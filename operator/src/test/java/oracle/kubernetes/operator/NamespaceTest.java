// Copyright (c) 2019, 2022, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import javax.annotation.Nonnull;

import com.meterware.simplestub.Memento;
import com.meterware.simplestub.Stub;
import io.kubernetes.client.openapi.models.V1Namespace;
import io.kubernetes.client.openapi.models.V1ObjectMeta;
import io.kubernetes.client.openapi.models.VersionInfo;
import oracle.kubernetes.operator.builders.StubWatchFactory;
import oracle.kubernetes.operator.helpers.EventHelper;
import oracle.kubernetes.operator.helpers.HelmAccessStub;
import oracle.kubernetes.operator.helpers.KubernetesTestSupport;
import oracle.kubernetes.operator.helpers.KubernetesVersion;
import oracle.kubernetes.operator.helpers.OnConflictRetryStrategyStub;
import oracle.kubernetes.operator.helpers.SemanticVersion;
import oracle.kubernetes.operator.tuning.TuningParametersStub;
import oracle.kubernetes.utils.TestUtils;
import oracle.kubernetes.weblogic.domain.model.DomainResource;
import org.hamcrest.MatcherAssert;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static com.meterware.simplestub.Stub.createStrictStub;
import static java.net.HttpURLConnection.HTTP_FORBIDDEN;
import static oracle.kubernetes.common.logging.MessageKeys.CREATING_EVENT_FORBIDDEN;
import static oracle.kubernetes.common.utils.LogMatcher.containsWarning;
import static oracle.kubernetes.operator.DomainProcessorTestSetup.NS;
import static oracle.kubernetes.operator.EventConstants.NAMESPACE_WATCHING_STARTED_EVENT;
import static oracle.kubernetes.operator.Namespaces.SELECTION_STRATEGY_KEY;
import static oracle.kubernetes.operator.helpers.EventHelper.EventItem.NAMESPACE_WATCHING_STARTED;
import static oracle.kubernetes.operator.helpers.EventHelper.createEventStep;
import static oracle.kubernetes.operator.helpers.HelmAccess.OPERATOR_DOMAIN_NAMESPACES;
import static oracle.kubernetes.operator.helpers.KubernetesTestSupport.DOMAIN;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.sameInstance;
import static org.hamcrest.junit.MatcherAssert.assertThat;

public class NamespaceTest {
  public static final VersionInfo TEST_VERSION_INFO = new VersionInfo().major("1").minor("18").gitVersion("0");
  public static final KubernetesVersion TEST_VERSION = new KubernetesVersion(TEST_VERSION_INFO);

  private static final String ADDITIONAL_NS1 = "EXTRA_NS1";
  private static final String ADDITIONAL_NS2 = "EXTRA_NS2";

  private final KubernetesTestSupport testSupport = new KubernetesTestSupport();
  private final List<Memento> mementos = new ArrayList<>();
  private final Set<String> currentNamespaces = new HashSet<>();
  private final DomainNamespaces domainNamespaces = createDomainNamespaces();

  @Nonnull
  public static DomainNamespaces createDomainNamespaces() {
    return new DomainNamespaces(null);
  }

  private final DomainProcessorStub dp = Stub.createNiceStub(DomainProcessorStub.class);
  private final MainDelegateStub delegate = createStrictStub(MainDelegateStub.class, dp, domainNamespaces);
  private final Collection<LogRecord> logRecords = new ArrayList<>();
  private final OnConflictRetryStrategyStub retryStrategy = createStrictStub(OnConflictRetryStrategyStub.class);

  private TestUtils.ConsoleHandlerMemento loggerControl;

  @BeforeEach
  public void setUp() throws Exception {
    mementos.add(loggerControl = TestUtils.silenceOperatorLogger());
    mementos.add(StubWatchFactory.install());
    mementos.add(NoopWatcherStarter.install());
    mementos.add(HelmAccessStub.install());
    mementos.add(TuningParametersStub.install());
    mementos.add(testSupport.install());
  }

  @AfterEach
  public void tearDown() {
    mementos.forEach(Memento::revert);
  }

  @Test
  void givenJobWatcherForNamespace_afterNamespaceDeletedAndRecreatedHaveDifferentWatcher() {
    initializeNamespaces();
    JobWatcher oldWatcher = domainNamespaces.getJobWatcher(NS);

    deleteNamespace(NS);
    processNamespaces();
    defineNamespaces(NS);

    testSupport.runSteps(new OperatorMain(delegate).createDomainRecheckSteps());
    assertThat(domainNamespaces.getJobWatcher(NS), not(sameInstance(oldWatcher)));
  }

  @Test
  void whenDomainNamespaceRemovedFromDomainNamespaces_stopDomainWatchers() {
    initializeNamespaces();
    AtomicBoolean stopping = domainNamespaces.isStopping(NS);

    unspecifyDomainNamespace(NS);
    processNamespaces();

    assertThat(stopping.get(), is(true));
  }

  private void initializeNamespaces() {
    HelmAccessStub.defineVariable(SELECTION_STRATEGY_KEY, Namespaces.SelectionStrategy.LIST.toString());
    defineNamespaces(NS, ADDITIONAL_NS1, ADDITIONAL_NS2);
    specifyDomainNamespaces(NS, ADDITIONAL_NS2);
    processNamespaces();
  }

  private void defineNamespaces(String... namespaces) {
    Arrays.stream(namespaces).forEach(ns -> testSupport.defineResources(createNamespace(ns), createDomain(ns)));
  }

  private V1Namespace createNamespace(String n) {
    return new V1Namespace().metadata(new V1ObjectMeta().name(n));
  }

  private DomainResource createDomain(String ns) {
    return new DomainResource().withMetadata(new V1ObjectMeta().namespace(ns).name(createUid(ns)));
  }

  @Nonnull
  private String createUid(String ns) {
    return "uid-" + ns;
  }

  private void specifyDomainNamespaces(String... namespaces) {
    Arrays.stream(namespaces).forEach(this::addDomainNamespace);
  }

  @SuppressWarnings("SameParameterValue")
  private void deleteNamespace(String namespaceName) {
    testSupport.deleteNamespace(namespaceName);
  }

  private void processNamespaces() {
    testSupport.withClearPacket().runSteps(new DomainRecheck(dp, domainNamespaces).readExistingNamespaces());
  }

  @Test
  void whenDomainNamespaceRemovedFromDomainNamespaces_isNoLongerInManagedNamespaces() {
    initializeNamespaces();

    unspecifyDomainNamespace(NS);
    processNamespaces();

    assertThat(domainNamespaces.getNamespaces(), not(contains(NS)));
  }

  @Test
  void whenDomainNamespaceRemovedFromDomainNamespaces_doNotShutdownDomain() {
    initializeNamespaces();

    unspecifyDomainNamespace(NS);
    processNamespaces();

    assertThat(getDomainsInNamespace(NS), notNullValue());
  }

  @SuppressWarnings("SameParameterValue")
  private DomainResource getDomainsInNamespace(String namespace) {
    return testSupport.<DomainResource>getResources(DOMAIN).stream()
          .filter(d -> d.getDomainUid().equals(createUid(namespace)))
          .findFirst()
          .orElse(null);
  }

  @Test
  void whenDomainNamespaceDeleted_stopDomainWatchers() {
    initializeNamespaces();
    AtomicBoolean stopping = domainNamespaces.isStopping(NS);

    deleteNamespace(NS);
    processNamespaces();

    assertThat(stopping.get(), is(true));
  }

  @Test
  void whenDomainNamespaceDeleted_isNoLongerInManagedNamespaces() {
    initializeNamespaces();

    deleteNamespace(NS);
    processNamespaces();

    assertThat(domainNamespaces.getNamespaces(), not(contains(NS)));
  }

  @Test
  void whenStartNamespaceBeforeStepRunHit403OnEventCreation_namespaceStartingFlagCleared() {
    String namespace = "TEST_NAMESPACE_1";
    defineNamespaces(namespace);
    specifyDomainNamespaces(namespace);

    loggerControl.withLogLevel(Level.INFO).collectLogMessages(logRecords, CREATING_EVENT_FORBIDDEN);
    testSupport.failOnCreate(KubernetesTestSupport.EVENT, namespace, HTTP_FORBIDDEN);
    testSupport.runSteps(new DomainRecheck(delegate).createStartNamespaceBeforeStep(namespace));

    MatcherAssert.assertThat(logRecords,
        containsWarning(getMessage(CREATING_EVENT_FORBIDDEN, NAMESPACE_WATCHING_STARTED_EVENT, namespace)));
    assertThat(domainNamespaces.isStarting(namespace), is(false));
  }

  @Test
  void whenStartNamespaceBeforeStepRunSucceeds_namespaceStartingFlagIsNotCleared() {
    String namespace = "TEST_NAMESPACE_2";
    defineNamespaces(namespace);
    specifyDomainNamespaces(namespace);

    loggerControl.withLogLevel(Level.INFO).collectLogMessages(logRecords, CREATING_EVENT_FORBIDDEN);
    testSupport.runSteps(new DomainRecheck(delegate).createStartNamespaceBeforeStep(namespace));
    assertThat(logRecords.isEmpty(), is(true));
    assertThat(domainNamespaces.isStarting(namespace), is(true));
  }

  @Test
  void whenStartNamespaceBeforeStepRun403OnEventCreation_thenSucceed_namespaceStartingFlagSet() {
    String namespace = "TEST_NAMESPACE_3";
    testSupport.addRetryStrategy(retryStrategy);
    defineNamespaces(namespace);
    specifyDomainNamespaces(namespace);

    loggerControl.collectLogMessages(logRecords, CREATING_EVENT_FORBIDDEN);
    testSupport.failOnCreate(KubernetesTestSupport.EVENT, namespace, HTTP_FORBIDDEN);
    testSupport.runSteps(new DomainRecheck(delegate).createStartNamespaceBeforeStep(namespace));
    testSupport.cancelFailures();
    testSupport.runSteps(createEventStep(delegate.domainNamespaces,
        new EventHelper.EventData(NAMESPACE_WATCHING_STARTED)
            .namespace(namespace)
            .resourceName(namespace), null));
    MatcherAssert.assertThat(logRecords,
        containsWarning(getMessage(CREATING_EVENT_FORBIDDEN, NAMESPACE_WATCHING_STARTED_EVENT, namespace)));
    assertThat(domainNamespaces.isStarting(namespace), is(true));
  }

  @SuppressWarnings("SameParameterValue")
  private String getMessage(String pattern, String event, String ns) {
    return String.format(pattern, event, ns);
  }

  private void addDomainNamespace(String namespace) {
    currentNamespaces.add(namespace);
    HelmAccessStub.defineVariable(OPERATOR_DOMAIN_NAMESPACES, String.join(",", currentNamespaces));
  }

  @SuppressWarnings("SameParameterValue")
  private void unspecifyDomainNamespace(String namespace) {
    currentNamespaces.remove(namespace);
    HelmAccessStub.defineVariable(OPERATOR_DOMAIN_NAMESPACES, String.join(",", currentNamespaces));
  }

  abstract static class DomainProcessorStub implements DomainProcessor {
  }

  abstract static class MainDelegateStub implements MainDelegate {
    private final DomainProcessor domainProcessor;
    private final DomainNamespaces domainNamespaces;

    MainDelegateStub(DomainProcessor domainProcessor, DomainNamespaces domainNamespaces) {
      this.domainProcessor = domainProcessor;
      this.domainNamespaces = domainNamespaces;
    }

    @Override
    public DomainProcessor getDomainProcessor() {
      return domainProcessor;
    }

    @Override
    public DomainNamespaces getDomainNamespaces() {
      return domainNamespaces;
    }

    @Override
    public KubernetesVersion getKubernetesVersion() {
      return TEST_VERSION;
    }

    @Override
    public SemanticVersion getProductVersion() {
      return SemanticVersion.TEST_VERSION;
    }
  }

}
