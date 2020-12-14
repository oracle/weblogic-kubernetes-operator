// Copyright (c) 2018, 2020, Oracle Corporation and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator;

import java.net.HttpURLConnection;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Properties;
import java.util.Random;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import com.meterware.simplestub.Memento;
import com.meterware.simplestub.StaticStubSupport;
import io.kubernetes.client.openapi.models.V1ConfigMap;
import io.kubernetes.client.openapi.models.V1Namespace;
import io.kubernetes.client.openapi.models.V1ObjectMeta;
import oracle.kubernetes.operator.Namespaces.SelectionStrategy;
import oracle.kubernetes.operator.builders.StubWatchFactory;
import oracle.kubernetes.operator.builders.WatchEvent;
import oracle.kubernetes.operator.helpers.HelmAccess;
import oracle.kubernetes.operator.helpers.HelmAccessStub;
import oracle.kubernetes.operator.helpers.KubernetesTestSupport;
import oracle.kubernetes.operator.helpers.KubernetesUtils;
import oracle.kubernetes.operator.helpers.KubernetesVersion;
import oracle.kubernetes.operator.helpers.SemanticVersion;
import oracle.kubernetes.operator.helpers.TuningParametersStub;
import oracle.kubernetes.operator.logging.MessageKeys;
import oracle.kubernetes.operator.work.FiberTestSupport;
import oracle.kubernetes.operator.work.Packet;
import oracle.kubernetes.operator.work.Step;
import oracle.kubernetes.operator.work.ThreadFactorySingleton;
import oracle.kubernetes.utils.TestUtils;
import org.hamcrest.Description;
import org.hamcrest.TypeSafeDiagnosingMatcher;
import org.jetbrains.annotations.NotNull;
import org.joda.time.DateTime;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import static com.meterware.simplestub.Stub.createNiceStub;
import static com.meterware.simplestub.Stub.createStrictStub;
import static oracle.kubernetes.operator.KubernetesConstants.SCRIPT_CONFIG_MAP_NAME;
import static oracle.kubernetes.operator.Main.GIT_BRANCH_KEY;
import static oracle.kubernetes.operator.Main.GIT_BUILD_TIME_KEY;
import static oracle.kubernetes.operator.Main.GIT_BUILD_VERSION_KEY;
import static oracle.kubernetes.operator.Main.GIT_COMMIT_KEY;
import static oracle.kubernetes.operator.TuningParametersImpl.DEFAULT_CALL_LIMIT;
import static oracle.kubernetes.operator.helpers.KubernetesTestSupport.DOMAIN;
import static oracle.kubernetes.operator.helpers.NamespaceHelper.getOperatorNamespace;
import static oracle.kubernetes.operator.logging.MessageKeys.CRD_NOT_INSTALLED;
import static oracle.kubernetes.operator.logging.MessageKeys.OPERATOR_STARTED;
import static oracle.kubernetes.operator.logging.MessageKeys.OP_CONFIG_DOMAIN_NAMESPACES;
import static oracle.kubernetes.operator.logging.MessageKeys.OP_CONFIG_NAMESPACE;
import static oracle.kubernetes.operator.logging.MessageKeys.OP_CONFIG_SERVICE_ACCOUNT;
import static oracle.kubernetes.utils.LogMatcher.containsInfo;
import static oracle.kubernetes.utils.LogMatcher.containsSevere;
import static oracle.kubernetes.utils.LogMatcher.containsWarning;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.junit.MatcherAssert.assertThat;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class MainTest extends ThreadFactoryTestBase {

  /** More than one chunk's worth of namespaces. */
  private static final int MULTICHUNK_LAST_NAMESPACE_NUM = DEFAULT_CALL_LIMIT + 1;

  /** Less than one chunk's worth of namespaces. */
  private static final int LAST_NAMESPACE_NUM = DEFAULT_CALL_LIMIT - 1;

  private static final String NS = "default";
  private static final String DOMAIN_UID = "domain-uid-for-testing";

  private static final String REGEXP = "regexp";
  private static final String NS_WEBLOGIC1 = "weblogic-alpha";
  private static final String NS_WEBLOGIC2 = "weblogic-beta-" + REGEXP;
  private static final String NS_WEBLOGIC3 = "weblogic-gamma";
  private static final String NS_WEBLOGIC4 = "other-alpha-" + REGEXP;
  private static final String NS_WEBLOGIC5 = "other-beta";
  private static final String[] NAMESPACES = {NS_WEBLOGIC1, NS_WEBLOGIC2, NS_WEBLOGIC3, NS_WEBLOGIC4, NS_WEBLOGIC5};

  private static final String GIT_BUILD_VERSION = "3.1.0";
  private static final String GIT_BRANCH = "master";
  private static final String GIT_COMMIT = "a987654";
  private static final String GIT_BUILD_TIME = "Sep-10-2015";
  private static final String IMPL = GIT_BRANCH + "." + GIT_COMMIT;

  private static final String LABEL = "weblogic-operator";
  private static final String VALUE = "enabled";

  private static final Properties buildProperties;
  private static final V1Namespace NAMESPACE_WEBLOGIC1
      = new V1Namespace().metadata(new V1ObjectMeta().name(NS_WEBLOGIC1).putLabelsItem(LABEL, VALUE));
  private static final V1Namespace NAMESPACE_WEBLOGIC2
      = new V1Namespace().metadata(new V1ObjectMeta().name(NS_WEBLOGIC2));
  private static final V1Namespace NAMESPACE_WEBLOGIC3
      = new V1Namespace().metadata(new V1ObjectMeta().name(NS_WEBLOGIC3).putLabelsItem(LABEL, VALUE));
  private static final V1Namespace NAMESPACE_WEBLOGIC4
          = new V1Namespace().metadata(new V1ObjectMeta().name(NS_WEBLOGIC4));
  private static final V1Namespace NAMESPACE_WEBLOGIC5
          = new V1Namespace().metadata(new V1ObjectMeta().name(NS_WEBLOGIC5).putLabelsItem(LABEL, VALUE));

  private final KubernetesTestSupport testSupport = new KubernetesTestSupport();
  private final List<Memento> mementos = new ArrayList<>();
  private final TestUtils.ConsoleHandlerMemento loggerControl = TestUtils.silenceOperatorLogger();
  private final Collection<LogRecord> logRecords = new ArrayList<>();
  private final String ns = "nsrand" + new Random().nextInt(10000);
  private final DomainNamespaces domainNamespaces = new DomainNamespaces();
  private final MainDelegateStub delegate = createStrictStub(MainDelegateStub.class, testSupport, domainNamespaces);
  private final Main main = new Main(delegate);

  static {
    buildProperties = new PropertiesBuilder()
              .withProperty(GIT_BUILD_VERSION_KEY, GIT_BUILD_VERSION)
              .withProperty(GIT_BRANCH_KEY, GIT_BRANCH)
              .withProperty(GIT_COMMIT_KEY, GIT_COMMIT)
              .withProperty(GIT_BUILD_TIME_KEY, GIT_BUILD_TIME)
              .build();
  }

  static class PropertiesBuilder {
    private final Properties properties = new Properties();

    private PropertiesBuilder withProperty(String name, String value) {
      properties.put(name, value);
      return this;
    }

    private Properties build() {
      return properties;
    }
  }

  @Before
  public void setUp() throws Exception {
    mementos.add(loggerControl);
    mementos.add(testSupport.install());
    mementos.add(HelmAccessStub.install());
    mementos.add(TuningParametersStub.install());
    mementos.add(StubWatchFactory.install());
    mementos.add(StaticStubSupport.install(ThreadFactorySingleton.class, "INSTANCE", this));
    mementos.add(NoopWatcherStarter.install());
  }

  @After
  public void tearDown() throws Exception {
    testSupport.throwOnCompletionFailure();

    mementos.forEach(Memento::revert);
  }

  @Test
  public void whenOperatorCreated_logStartupMessage() {
    loggerControl.withLogLevel(Level.INFO).collectLogMessages(logRecords, OPERATOR_STARTED);

    Main.createMain(buildProperties);

    assertThat(logRecords, containsInfo(OPERATOR_STARTED, GIT_BUILD_VERSION, IMPL, GIT_BUILD_TIME));
  }

  @Test
  public void whenOperatorCreated_logOperatorNamespace() {
    loggerControl.withLogLevel(Level.INFO).collectLogMessages(logRecords, OP_CONFIG_NAMESPACE);

    Main.createMain(buildProperties);

    assertThat(logRecords, containsInfo(OP_CONFIG_NAMESPACE, getOperatorNamespace()));
  }

  @Test
  public void whenOperatorCreated_logServiceAccountName() {
    loggerControl.withLogLevel(Level.INFO).collectLogMessages(logRecords, OP_CONFIG_SERVICE_ACCOUNT);

    Main.createMain(buildProperties);

    assertThat(logRecords, containsInfo(OP_CONFIG_SERVICE_ACCOUNT, "default"));
  }

  @Test
  public void whenOperatorCreatedWithListNamespaceStrategy_logConfiguredNamespaces() {
    loggerControl.withLogLevel(Level.INFO).collectLogMessages(logRecords, OP_CONFIG_DOMAIN_NAMESPACES);
    defineSelectionStrategy(SelectionStrategy.List);
    HelmAccessStub.defineVariable(HelmAccess.OPERATOR_DOMAIN_NAMESPACES,
          String.join(",", NS_WEBLOGIC1, NS_WEBLOGIC2, NS_WEBLOGIC3));

    Main.createMain(buildProperties);

    assertThat(logRecords, containsInfo(OP_CONFIG_DOMAIN_NAMESPACES,
          String.join(", ", NS_WEBLOGIC1, NS_WEBLOGIC2, NS_WEBLOGIC3)));
  }

  @Test
  public void whenOperatorCreatedWithDedicatedNamespaceStrategy_logConfiguredNamespaces() {
    loggerControl.withLogLevel(Level.INFO).collectLogMessages(logRecords, OP_CONFIG_DOMAIN_NAMESPACES);
    defineSelectionStrategy(SelectionStrategy.Dedicated);

    Main.createMain(buildProperties);

    assertThat(logRecords, containsInfo(OP_CONFIG_DOMAIN_NAMESPACES, getOperatorNamespace()));
  }

  @Test
  public void whenOperatorCreatedWithRegExpNamespaceStrategy_dontLogConfiguredNamespaces() {
    loggerControl.withLogLevel(Level.INFO).collectLogMessages(logRecords, OP_CONFIG_DOMAIN_NAMESPACES);
    defineSelectionStrategy(SelectionStrategy.RegExp);

    Main.createMain(buildProperties);

    assertThat(logRecords, not(containsInfo(OP_CONFIG_DOMAIN_NAMESPACES)));
  }

  @Test
  public void whenOperatorCreatedWithLabelSelectorNamespaceStrategy_dontLogConfiguredNamespaces() {
    loggerControl.withLogLevel(Level.INFO).collectLogMessages(logRecords, OP_CONFIG_DOMAIN_NAMESPACES);
    defineSelectionStrategy(SelectionStrategy.LabelSelector);

    Main.createMain(buildProperties);

    assertThat(logRecords, not(containsInfo(OP_CONFIG_DOMAIN_NAMESPACES)));
  }

  @Test
  public void whenOperatorStarted_namespaceWatcherIsCreated() {
    main.startOperator(null);

    assertThat(main.getNamespaceWatcher(), notNullValue());
  }

  @Test
  public void whenOperatorStartedInDedicatedMode_namespaceWatcherIsNotCreated() {
    defineSelectionStrategy(SelectionStrategy.Dedicated);

    main.startOperator(null);

    assertThat(main.getNamespaceWatcher(), nullValue());
  }

  @Test
  public void whenUnableToCreateCRD_dontTryToStartWatchers() {
    simulateMissingCRD();

    recheckDomains();

    verifyWatchersNotDefined(main.getDomainNamespaces(), getOperatorNamespace());
  }

  void simulateMissingCRD() {
    testSupport.failOnResource(DOMAIN, null, getOperatorNamespace(), HttpURLConnection.HTTP_NOT_FOUND);
  }

  void recheckDomains() {
    testSupport.runSteps(main.createDomainRecheckSteps());
  }

  @Test
  public void whenNoCRD_logReasonForFailure() {
    loggerControl.withLogLevel(Level.SEVERE).collectLogMessages(logRecords, CRD_NOT_INSTALLED);
    simulateMissingCRD();

    recheckDomains();

    assertThat(logRecords, containsSevere(CRD_NOT_INSTALLED));
  }

  @Test
  public void afterLoggedCRDMissing_dontDoItASecondTime() {
    loggerControl.withLogLevel(Level.SEVERE).collectLogMessages(logRecords, CRD_NOT_INSTALLED);
    simulateMissingCRD();
    recheckDomains();
    logRecords.clear();

    recheckDomains();

    assertThat(logRecords, not(containsSevere(CRD_NOT_INSTALLED)));
  }

  @Test
  public void afterMissingCRDdetected_correctionOfTheConditionAllowsProcessingToOccur() {
    defineSelectionStrategy(SelectionStrategy.Dedicated);
    simulateMissingCRD();
    recheckDomains();

    testSupport.cancelFailures();
    recheckDomains();

    verifyWatchersDefined(main.getDomainNamespaces(), getOperatorNamespace());
  }

  @Test
  public void afterMissingCRDcorrected_subsequentFailureLogsReasonForFailure() {
    simulateMissingCRD();
    recheckDomains();
    testSupport.cancelFailures();
    recheckDomains();

    loggerControl.withLogLevel(Level.SEVERE).collectLogMessages(logRecords, CRD_NOT_INSTALLED);
    simulateMissingCRD();
    recheckDomains();

    assertThat(logRecords, containsSevere(CRD_NOT_INSTALLED));
  }


  @Test
  public void withNamespaceList_onReadExistingNamespaces_startsNamespaces() {
    defineSelectionStrategy(SelectionStrategy.List);
    HelmAccessStub.defineVariable(HelmAccess.OPERATOR_DOMAIN_NAMESPACES,
          String.join(",", NS_WEBLOGIC1, NS_WEBLOGIC2, NS_WEBLOGIC3));
    testSupport.defineResources(NAMESPACE_WEBLOGIC1, NAMESPACE_WEBLOGIC2, NAMESPACE_WEBLOGIC3,
                                NAMESPACE_WEBLOGIC4, NAMESPACE_WEBLOGIC5);

    testSupport.runSteps(createDomainRecheck().readExistingNamespaces());

    assertThat(getStartingNamespaces(), contains(NS_WEBLOGIC1, NS_WEBLOGIC2, NS_WEBLOGIC3));
  }

  private List<String> getStartingNamespaces() {
    return Arrays.stream(NAMESPACES).filter(domainNamespaces::isStarting).collect(Collectors.toList());
  }

  @NotNull
  DomainRecheck createDomainRecheck() {
    return new DomainRecheck(delegate);
  }

  @SuppressWarnings("unused")
  static class NamespaceStatusMatcher extends TypeSafeDiagnosingMatcher<NamespaceStatus> {

    static NamespaceStatusMatcher isNamespaceStarting() {
      return new NamespaceStatusMatcher();
    }

    @Override
    protected boolean matchesSafely(NamespaceStatus item, Description mismatchDescription) {
      if (item.isNamespaceStarting().get()) {
        return true;
      }

      mismatchDescription.appendText("isNamespaceStarting is false");
      return false;
    }

    @Override
    public void describeTo(Description description) {
      description.appendText("NamespaceStatus with isNamespaceStarting true");
    }
  }

  @Test
  public void withRegExp_onReadExistingNamespaces_startsNamespaces() {
    defineSelectionStrategy(SelectionStrategy.RegExp);
    testSupport.defineResources(NAMESPACE_WEBLOGIC1, NAMESPACE_WEBLOGIC2, NAMESPACE_WEBLOGIC3,
          NAMESPACE_WEBLOGIC4, NAMESPACE_WEBLOGIC5);

    TuningParameters.getInstance().put("domainNamespaceRegExp", REGEXP);
    testSupport.runSteps(createDomainRecheck().readExistingNamespaces());

    assertThat(getStartingNamespaces(), contains(NS_WEBLOGIC2, NS_WEBLOGIC4));
  }

  @Test
  public void withLabelSelector_onReadExistingNamespaces_startsNamespaces() {
    defineSelectionStrategy(SelectionStrategy.LabelSelector);
    testSupport.defineResources(NAMESPACE_WEBLOGIC1, NAMESPACE_WEBLOGIC2, NAMESPACE_WEBLOGIC3,
          NAMESPACE_WEBLOGIC4, NAMESPACE_WEBLOGIC5);

    TuningParameters.getInstance().put("domainNamespaceLabelSelector", LABEL + "=" + VALUE);
    testSupport.runSteps(createDomainRecheck().readExistingNamespaces());

    assertThat(getStartingNamespaces(), contains(NS_WEBLOGIC1, NS_WEBLOGIC3, NS_WEBLOGIC5));
  }

  private V1ObjectMeta createMetadata(DateTime creationTimestamp) {
    return new V1ObjectMeta()
        .name(DOMAIN_UID)
        .namespace(NS)
        .creationTimestamp(creationTimestamp)
        .resourceVersion("1");
  }

  @Test
  public void whenConfiguredDomainNamespaceMissing_logWarning() {
    loggerControl.withLogLevel(Level.WARNING).collectLogMessages(logRecords, MessageKeys.NAMESPACE_IS_MISSING);

    defineSelectionStrategy(SelectionStrategy.List);
    String namespaceString = "NS1,NS" + LAST_NAMESPACE_NUM;
    HelmAccessStub.defineVariable(HelmAccess.OPERATOR_DOMAIN_NAMESPACES, namespaceString);
    createNamespaces(LAST_NAMESPACE_NUM - 1);

    testSupport.runSteps(createDomainRecheck().readExistingNamespaces());

    assertThat(logRecords, containsWarning(MessageKeys.NAMESPACE_IS_MISSING));
  }

  @Test
  public void whenNamespacesListedInOneChunk_dontDeclarePresentNamespacesAsMissing() {
    loggerControl.withLogLevel(Level.WARNING).collectLogMessages(logRecords, MessageKeys.NAMESPACE_IS_MISSING);

    defineSelectionStrategy(SelectionStrategy.List);
    String namespaceString = "NS1,NS" + LAST_NAMESPACE_NUM;
    HelmAccessStub.defineVariable(HelmAccess.OPERATOR_DOMAIN_NAMESPACES, namespaceString);
    createNamespaces(LAST_NAMESPACE_NUM);

    testSupport.runSteps(createDomainRecheck().readExistingNamespaces());
  }

  private void defineSelectionStrategy(SelectionStrategy selectionStrategy) {
    TuningParameters.getInstance().put(Namespaces.SELECTION_STRATEGY_KEY, selectionStrategy.toString());
  }

  @Test
  public void whenNamespacesListedInMultipleChunks_dontDeclarePresentNamespacesAsMissing() {
    loggerControl.withLogLevel(Level.WARNING).collectLogMessages(logRecords, MessageKeys.NAMESPACE_IS_MISSING);

    defineSelectionStrategy(SelectionStrategy.List);
    String namespaceString = "NS1,NS" + MULTICHUNK_LAST_NAMESPACE_NUM;
    HelmAccessStub.defineVariable(HelmAccess.OPERATOR_DOMAIN_NAMESPACES, namespaceString);
    createNamespaces(MULTICHUNK_LAST_NAMESPACE_NUM);

    testSupport.runSteps(createDomainRecheck().readExistingNamespaces());
  }

  private void createNamespaces(int lastNamespaceNum) {
    IntStream.rangeClosed(1, lastNamespaceNum)
          .boxed()
          .map(i -> "NS" + i)
          .map(this::createNamespace)
          .forEach(testSupport::defineResources);
  }

  private V1Namespace createNamespace(String ns) {
    return new V1Namespace().metadata(new V1ObjectMeta().name(ns));
  }

  @Test
  public void deleteDomainPresenceWithTimeCheck_delete_with_same_DateTime() {
    DateTime creationDatetime = DateTime.now();
    V1ObjectMeta domainMeta = createMetadata(creationDatetime);

    V1ObjectMeta domain2Meta = createMetadata(creationDatetime);

    assertFalse(KubernetesUtils.isFirstNewer(domainMeta, domain2Meta));
  }

  @Test
  public void deleteDomainPresenceWithTimeCheck_delete_with_newer_DateTime() {
    DateTime creationDatetime = DateTime.now();
    V1ObjectMeta domainMeta = createMetadata(creationDatetime);

    DateTime deleteDatetime = creationDatetime.plusMinutes(1);
    V1ObjectMeta domain2Meta = createMetadata(deleteDatetime);

    assertFalse(KubernetesUtils.isFirstNewer(domainMeta, domain2Meta));
  }

  @Test
  public void deleteDomainPresenceWithTimeCheck_doNotDelete_with_older_DateTime() {
    DateTime creationDatetime = DateTime.now();
    V1ObjectMeta domainMeta = createMetadata(creationDatetime);

    DateTime deleteDatetime = creationDatetime.minusMinutes(1);
    V1ObjectMeta domain2Meta = createMetadata(deleteDatetime);

    assertTrue(KubernetesUtils.isFirstNewer(domainMeta, domain2Meta));
  }

  @Test
  public void afterReadingExistingResourcesForNamespace_WatchersAreDefined() {
    testSupport.runSteps(domainNamespaces.readExistingResources(NS, createStrictStub(DomainProcessor.class)));

    assertThat(domainNamespaces.getConfigMapWatcher(NS), notNullValue());
    assertThat(domainNamespaces.getDomainWatcher(NS), notNullValue());
    assertThat(domainNamespaces.getEventWatcher(NS), notNullValue());
    assertThat(domainNamespaces.getPodWatcher(NS), notNullValue());
    assertThat(domainNamespaces.getServiceWatcher(NS), notNullValue());
  }
  
  @Test
  public void afterReadingExistingResourcesForNamespace_ScriptConfigMapIsDefined() {
    testSupport.runSteps(domainNamespaces.readExistingResources(NS, createStrictStub(DomainProcessor.class)));

    assertThat(getScriptMap(NS), notNullValue());
  }

  @SuppressWarnings("SameParameterValue")
  private V1ConfigMap getScriptMap(String ns) {
    return testSupport.<V1ConfigMap>getResources(KubernetesTestSupport.CONFIG_MAP).stream()
          .filter(m -> isScriptConfigMap(m, ns))
          .findFirst()
          .orElse(null);
  }

  @SuppressWarnings("SameParameterValue")
  private boolean isScriptConfigMap(V1ConfigMap configMap, String ns) {
    return Optional.ofNullable(configMap.getMetadata()).filter(meta -> isScriptConfigMapMetadata(meta, ns)).isPresent();
  }

  private boolean isScriptConfigMapMetadata(V1ObjectMeta meta, String ns) {
    return SCRIPT_CONFIG_MAP_NAME.equals(meta.getName()) && ns.equals(meta.getNamespace());
  }

  @Test
  public void beforeNamespaceAdded_watchersAreNotDefined() {
    HelmAccessStub.defineVariable(HelmAccess.OPERATOR_DOMAIN_NAMESPACES, ns);

    verifyWatchersNotDefined(main.getDomainNamespaces(), ns);
  }

  private void verifyWatchersNotDefined(DomainNamespaces domainNamespaces, String ns) {
    assertThat(domainNamespaces.getConfigMapWatcher(ns), nullValue());
    assertThat(domainNamespaces.getDomainWatcher(ns), nullValue());
    assertThat(domainNamespaces.getEventWatcher(ns), nullValue());
    assertThat(domainNamespaces.getJobWatcher(ns), nullValue());
    assertThat(domainNamespaces.getPodWatcher(ns), nullValue());
    assertThat(domainNamespaces.getServiceWatcher(ns), nullValue());
  }

  @Test
  public void afterNullNamespaceAdded_WatchersAreNotDefined() {
    main.dispatchNamespaceWatch(WatchEvent.createAddedEvent((V1Namespace) null).toWatchResponse());

    verifyWatchersNotDefined(main.getDomainNamespaces(), ns);
  }
  
  @Test
  public void afterNonDomainNamespaceAdded_WatchersAreNotDefined() {
    HelmAccessStub.defineVariable(HelmAccess.OPERATOR_DOMAIN_NAMESPACES, NS_WEBLOGIC1);
    V1Namespace namespace = new V1Namespace().metadata(new V1ObjectMeta().name(ns));
    main.dispatchNamespaceWatch(WatchEvent.createAddedEvent(namespace).toWatchResponse());

    verifyWatchersNotDefined(main.getDomainNamespaces(), ns);
  }
  
  @Test
  public void afterNamespaceAdded_WatchersAreDefined() {
    HelmAccessStub.defineVariable(HelmAccess.OPERATOR_DOMAIN_NAMESPACES, ns);
    V1Namespace namespace = new V1Namespace().metadata(new V1ObjectMeta().name(ns));

    main.dispatchNamespaceWatch(WatchEvent.createAddedEvent(namespace).toWatchResponse());

    verifyWatchersDefined(main.getDomainNamespaces(), ns);
  }

  private void verifyWatchersDefined(DomainNamespaces domainNamespaces, String ns) {
    assertThat(domainNamespaces.getConfigMapWatcher(ns), notNullValue());
    assertThat(domainNamespaces.getDomainWatcher(ns), notNullValue());
    assertThat(domainNamespaces.getEventWatcher(ns), notNullValue());
    assertThat(domainNamespaces.getJobWatcher(ns), notNullValue());
    assertThat(domainNamespaces.getPodWatcher(ns), notNullValue());
    assertThat(domainNamespaces.getServiceWatcher(ns), notNullValue());
  }

  @Test
  public void afterNamespaceAdded_scriptConfigMapIsDefined() {
    HelmAccessStub.defineVariable(HelmAccess.OPERATOR_DOMAIN_NAMESPACES, ns);
    V1Namespace namespace = new V1Namespace().metadata(new V1ObjectMeta().name(ns));
    main.dispatchNamespaceWatch(WatchEvent.createAddedEvent(namespace).toWatchResponse());

    assertThat(getScriptMap(ns), notNullValue());
  }

  abstract static class MainDelegateStub implements MainDelegate {
    private final FiberTestSupport testSupport;
    private final DomainNamespaces domainNamespaces;

    public MainDelegateStub(FiberTestSupport testSupport, DomainNamespaces domainNamespaces) {
      this.testSupport = testSupport;
      this.domainNamespaces = domainNamespaces;
    }

    @Override
    public void runSteps(Packet packet, Step firstStep, Runnable completionAction) {
      testSupport.withPacket(packet)
                 .withCompletionAction(completionAction)
                 .runSteps(firstStep);
    }

    @Override
    public DomainProcessor getDomainProcessor() {
      return createNiceStub(DomainProcessor.class);
    }

    @Override
    public DomainNamespaces getDomainNamespaces() {
      return domainNamespaces;
    }

    @Override
    public KubernetesVersion getKubernetesVersion() {
      return KubernetesVersion.TEST_VERSION;
    }

    @Override
    public SemanticVersion getProductVersion() {
      return SemanticVersion.TEST_VERSION;
    }
  }
}
