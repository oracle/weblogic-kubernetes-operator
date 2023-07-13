// Copyright (c) 2018, 2023, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator;

import java.io.File;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.security.KeyManagementException;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.UnrecoverableKeyException;
import java.security.cert.CertificateException;
import java.security.spec.InvalidKeySpecException;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import javax.annotation.Nonnull;

import com.meterware.simplestub.Memento;
import com.meterware.simplestub.StaticStubSupport;
import io.kubernetes.client.openapi.models.CoreV1Event;
import io.kubernetes.client.openapi.models.V1ConfigMap;
import io.kubernetes.client.openapi.models.V1CustomResourceDefinition;
import io.kubernetes.client.openapi.models.V1Namespace;
import io.kubernetes.client.openapi.models.V1ObjectMeta;
import io.kubernetes.client.openapi.models.V1ObjectReference;
import io.kubernetes.client.openapi.models.VersionInfo;
import oracle.kubernetes.common.logging.MessageKeys;
import oracle.kubernetes.operator.Namespaces.SelectionStrategy;
import oracle.kubernetes.operator.builders.StubWatchFactory;
import oracle.kubernetes.operator.builders.WatchEvent;
import oracle.kubernetes.operator.helpers.HelmAccess;
import oracle.kubernetes.operator.helpers.HelmAccessStub;
import oracle.kubernetes.operator.helpers.KubernetesEventObjects;
import oracle.kubernetes.operator.helpers.KubernetesTestSupport;
import oracle.kubernetes.operator.helpers.KubernetesUtils;
import oracle.kubernetes.operator.helpers.KubernetesVersion;
import oracle.kubernetes.operator.helpers.SemanticVersion;
import oracle.kubernetes.operator.http.BaseServer;
import oracle.kubernetes.operator.http.metrics.MetricsServer;
import oracle.kubernetes.operator.tuning.TuningParametersStub;
import oracle.kubernetes.operator.utils.InMemoryFileSystem;
import oracle.kubernetes.operator.work.Component;
import oracle.kubernetes.operator.work.Container;
import oracle.kubernetes.operator.work.FiberTestSupport;
import oracle.kubernetes.operator.work.Packet;
import oracle.kubernetes.operator.work.Step;
import oracle.kubernetes.operator.work.ThreadFactorySingleton;
import oracle.kubernetes.utils.SystemClock;
import oracle.kubernetes.utils.TestUtils;
import org.glassfish.grizzly.http.server.HttpHandlerRegistration;
import org.glassfish.grizzly.http.server.HttpServer;
import org.glassfish.jersey.server.ResourceConfig;
import org.hamcrest.Description;
import org.hamcrest.TypeSafeDiagnosingMatcher;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.ResourceLock;

import static com.meterware.simplestub.Stub.createNiceStub;
import static com.meterware.simplestub.Stub.createStrictStub;
import static oracle.kubernetes.common.logging.MessageKeys.CRD_NOT_INSTALLED;
import static oracle.kubernetes.common.logging.MessageKeys.OPERATOR_STARTED;
import static oracle.kubernetes.common.logging.MessageKeys.OP_CONFIG_DOMAIN_NAMESPACES;
import static oracle.kubernetes.common.logging.MessageKeys.OP_CONFIG_NAMESPACE;
import static oracle.kubernetes.common.logging.MessageKeys.OP_CONFIG_SERVICE_ACCOUNT;
import static oracle.kubernetes.common.logging.MessageKeys.START_MANAGING_NAMESPACE_EVENT_PATTERN;
import static oracle.kubernetes.common.logging.MessageKeys.WAIT_FOR_CRD_INSTALLATION;
import static oracle.kubernetes.common.utils.LogMatcher.containsInfo;
import static oracle.kubernetes.common.utils.LogMatcher.containsSevere;
import static oracle.kubernetes.common.utils.LogMatcher.containsWarning;
import static oracle.kubernetes.operator.BaseMain.container;
import static oracle.kubernetes.operator.EventConstants.DOMAIN_CHANGED_EVENT;
import static oracle.kubernetes.operator.EventConstants.DOMAIN_CREATED_EVENT;
import static oracle.kubernetes.operator.EventConstants.NAMESPACE_WATCHING_STARTED_EVENT;
import static oracle.kubernetes.operator.EventConstants.START_MANAGING_NAMESPACE_EVENT;
import static oracle.kubernetes.operator.EventConstants.STOP_MANAGING_NAMESPACE_EVENT;
import static oracle.kubernetes.operator.EventTestUtils.containsEvent;
import static oracle.kubernetes.operator.EventTestUtils.containsEventWithMessage;
import static oracle.kubernetes.operator.EventTestUtils.containsEventWithMessageForNamespaces;
import static oracle.kubernetes.operator.EventTestUtils.getEvents;
import static oracle.kubernetes.operator.EventTestUtils.getFormattedMessage;
import static oracle.kubernetes.operator.KubernetesConstants.OPERATOR_ENABLE_REST_ENDPOINT_ENV;
import static oracle.kubernetes.operator.KubernetesConstants.OPERATOR_NAMESPACE_ENV;
import static oracle.kubernetes.operator.KubernetesConstants.OPERATOR_POD_NAME_ENV;
import static oracle.kubernetes.operator.KubernetesConstants.SCRIPT_CONFIG_MAP_NAME;
import static oracle.kubernetes.operator.LabelConstants.CREATEDBYOPERATOR_LABEL;
import static oracle.kubernetes.operator.LabelConstants.DOMAINUID_LABEL;
import static oracle.kubernetes.operator.Namespaces.SELECTION_STRATEGY_KEY;
import static oracle.kubernetes.operator.OperatorMain.GIT_BRANCH_KEY;
import static oracle.kubernetes.operator.OperatorMain.GIT_BUILD_TIME_KEY;
import static oracle.kubernetes.operator.OperatorMain.GIT_BUILD_VERSION_KEY;
import static oracle.kubernetes.operator.OperatorMain.GIT_COMMIT_KEY;
import static oracle.kubernetes.operator.ProcessingConstants.DELEGATE_COMPONENT_NAME;
import static oracle.kubernetes.operator.helpers.EventHelper.EventItem.NAMESPACE_WATCHING_STARTED;
import static oracle.kubernetes.operator.helpers.EventHelper.EventItem.NAMESPACE_WATCHING_STOPPED;
import static oracle.kubernetes.operator.helpers.EventHelper.EventItem.START_MANAGING_NAMESPACE;
import static oracle.kubernetes.operator.helpers.EventHelper.EventItem.STOP_MANAGING_NAMESPACE;
import static oracle.kubernetes.operator.helpers.KubernetesTestSupport.DOMAIN;
import static oracle.kubernetes.operator.helpers.NamespaceHelper.getOperatorNamespace;
import static oracle.kubernetes.operator.tuning.TuningParameters.DEFAULT_CALL_LIMIT;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.Matchers.hasValue;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.junit.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OperatorMainTest extends ThreadFactoryTestBase {
  public static final VersionInfo TEST_VERSION_INFO = new VersionInfo().major("1").minor("18").gitVersion("0");
  public static final KubernetesVersion TEST_VERSION = new KubernetesVersion(TEST_VERSION_INFO);

  private static final String OPERATOR_POD_NAME = "my-weblogic-operator-1234";
  private static final String OP_NS = "operator-namespace";

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
  public static final String CRD = "CRD";

  private final KubernetesTestSupport testSupport = new KubernetesTestSupport();
  private final List<Memento> mementos = new ArrayList<>();
  private final TestUtils.ConsoleHandlerMemento loggerControl = TestUtils.silenceOperatorLogger();
  private final Collection<LogRecord> logRecords = new ArrayList<>();
  private final String ns = "nsrand" + (((int) (Math.random() * 9999)) + 1);
  private final DomainNamespaces domainNamespaces = new DomainNamespaces(null);
  private final MainDelegateStub delegate = createStrictStub(MainDelegateStub.class, testSupport, domainNamespaces);
  private final OperatorMain operatorMain = new OperatorMain(delegate);
  private static final InMemoryFileSystem inMemoryFileSystem = InMemoryFileSystem.createInstance();

  private final Map<String, Map<String, KubernetesEventObjects>> domainEventObjects = new ConcurrentHashMap<>();
  private final Map<String, KubernetesEventObjects> nsEventObjects = new ConcurrentHashMap<>();

  static {
    buildProperties = new PropertiesBuilder()
              .withProperty(GIT_BUILD_VERSION_KEY, GIT_BUILD_VERSION)
              .withProperty(GIT_BRANCH_KEY, GIT_BRANCH)
              .withProperty(GIT_COMMIT_KEY, GIT_COMMIT)
              .withProperty(GIT_BUILD_TIME_KEY, GIT_BUILD_TIME)
              .build();
  }

  private static class PropertiesBuilder {
    private final Properties properties = new Properties();

    private PropertiesBuilder withProperty(String name, String value) {
      properties.put(name, value);
      return this;
    }

    private Properties build() {
      return properties;
    }
  }

  @BeforeEach
  public void setUp() throws Exception {
    mementos.add(loggerControl);
    mementos.add(testSupport.install());
    mementos.add(TestStepFactory.install());
    mementos.add(HelmAccessStub.install());
    mementos.add(TuningParametersStub.install());
    mementos.add(StubWatchFactory.install());
    mementos.add(StaticStubSupport.install(ThreadFactorySingleton.class, "instance", this));
    mementos.add(NoopWatcherStarter.install());
    mementos.add(StaticStubSupport.install(DomainProcessorImpl.class, "domainEventK8SObjects", domainEventObjects));
    mementos.add(StaticStubSupport.install(DomainProcessorImpl.class, "namespaceEventK8SObjects", nsEventObjects));
    mementos.add(inMemoryFileSystem.install());

    HelmAccessStub.defineVariable(OPERATOR_NAMESPACE_ENV, OP_NS);
    HelmAccessStub.defineVariable(OPERATOR_POD_NAME_ENV, OPERATOR_POD_NAME);
    HelmAccessStub.defineVariable(OPERATOR_ENABLE_REST_ENDPOINT_ENV, "true");
  }

  @AfterEach
  public void tearDown() throws Exception {
    testSupport.throwOnCompletionFailure();

    mementos.forEach(Memento::revert);
  }

  @Test
  void whenOperatorCreated_logStartupMessage() {
    loggerControl.withLogLevel(Level.INFO).collectLogMessages(logRecords, OPERATOR_STARTED);

    OperatorMain.createMain(buildProperties);

    assertThat(logRecords, containsInfo(OPERATOR_STARTED).withParams(GIT_BUILD_VERSION, IMPL, GIT_BUILD_TIME));
  }

  @Test
  void whenOperatorCreated_logOperatorNamespace() {
    loggerControl.withLogLevel(Level.INFO).collectLogMessages(logRecords, OP_CONFIG_NAMESPACE);

    OperatorMain.createMain(buildProperties);

    assertThat(logRecords, containsInfo(OP_CONFIG_NAMESPACE).withParams(getOperatorNamespace()));
  }

  @Test
  void whenOperatorCreated_logServiceAccountName() {
    loggerControl.withLogLevel(Level.INFO).collectLogMessages(logRecords, OP_CONFIG_SERVICE_ACCOUNT);

    OperatorMain.createMain(buildProperties);

    assertThat(logRecords, containsInfo(OP_CONFIG_SERVICE_ACCOUNT).withParams("default"));
  }

  @Test
  void whenOperatorCreatedWithListNamespaceStrategy_logConfiguredNamespaces() {
    loggerControl.withLogLevel(Level.INFO).collectLogMessages(logRecords, OP_CONFIG_DOMAIN_NAMESPACES);
    defineSelectionStrategy(SelectionStrategy.LIST);
    HelmAccessStub.defineVariable(HelmAccess.OPERATOR_DOMAIN_NAMESPACES,
          String.join(",", NS_WEBLOGIC1, NS_WEBLOGIC2, NS_WEBLOGIC3));

    OperatorMain.createMain(buildProperties);

    assertThat(logRecords, containsInfo(OP_CONFIG_DOMAIN_NAMESPACES)
          .withParams(String.join(", ", NS_WEBLOGIC1, NS_WEBLOGIC2, NS_WEBLOGIC3)));
  }

  @Test
  void whenOperatorCreatedWithDedicatedNamespaceStrategy_logConfiguredNamespaces() {
    loggerControl.withLogLevel(Level.INFO).collectLogMessages(logRecords, OP_CONFIG_DOMAIN_NAMESPACES);
    defineSelectionStrategy(SelectionStrategy.DEDICATED);

    OperatorMain.createMain(buildProperties);

    assertThat(logRecords, containsInfo(OP_CONFIG_DOMAIN_NAMESPACES).withParams(getOperatorNamespace()));
  }

  @Test
  void whenOperatorCreatedWithRegExpNamespaceStrategy_dontLogConfiguredNamespaces() {
    loggerControl.withLogLevel(Level.INFO).collectLogMessages(logRecords, OP_CONFIG_DOMAIN_NAMESPACES);
    defineSelectionStrategy(SelectionStrategy.REG_EXP);

    OperatorMain.createMain(buildProperties);

    assertThat(logRecords, not(containsInfo(OP_CONFIG_DOMAIN_NAMESPACES)));
  }

  @Test
  void whenOperatorCreatedWithLabelSelectorNamespaceStrategy_dontLogConfiguredNamespaces() {
    loggerControl.withLogLevel(Level.INFO).collectLogMessages(logRecords, OP_CONFIG_DOMAIN_NAMESPACES);
    defineSelectionStrategy(SelectionStrategy.LABEL_SELECTOR);

    OperatorMain.createMain(buildProperties);

    assertThat(logRecords, not(containsInfo(OP_CONFIG_DOMAIN_NAMESPACES)));
  }

  @Test
  void whenOperatorStarted_namespaceWatcherIsCreated() {
    operatorMain.startDeployment(null);

    assertThat(operatorMain.getNamespaceWatcher(), notNullValue());
  }

  @Test
  void whenOperatorStarted_operatorNamespaceEventWatcherIsCreated() {
    operatorMain.startDeployment(null);

    assertThat(operatorMain.getOperatorNamespaceEventWatcher(), notNullValue());
  }

  @Test
  void whenOperatorStarted_withoutExistingEvent_nsEventK8SObjectsEmpty() {
    operatorMain.startDeployment(null);
    assertThat(getNSEventMapSize(), equalTo(0));
  }

  @Test
  void whenOperatorStarted_withExistingEvents_nsEventK8SObjectsPopulated() {
    CoreV1Event event1 = createNSEvent("event1").reason(START_MANAGING_NAMESPACE_EVENT);
    CoreV1Event event2 = createNSEvent("event2").reason(STOP_MANAGING_NAMESPACE_EVENT);

    testSupport.defineResources(event1, event2);
    operatorMain.startDeployment(null);
    assertThat(getNSEventMapSize(), equalTo(2));
  }

  @Test
  void whenOperatorShutdown_completionCallbackOccursBeforeFollowingLogic() {
    final List<String> callOrder = Collections.synchronizedList(new ArrayList<>());
    operatorMain.stopDeployment(() -> callOrder.add("completionCallback"));
    callOrder.add("afterStoppedDeployment");

    assertThat(callOrder, hasItems("completionCallback", "afterStoppedDeployment"));
  }

  private int getNSEventMapSize() {
    return Optional.ofNullable(nsEventObjects.get(OP_NS)).map(KubernetesEventObjects::size).orElse(0);
  }

  private CoreV1Event createNSEvent(String name) {
    return new CoreV1Event()
        .metadata(new V1ObjectMeta()
            .name(name)
            .namespace(OP_NS)
            .putLabelsItem(CREATEDBYOPERATOR_LABEL, "true"))
        .involvedObject(new V1ObjectReference().name("Op1").namespace(OP_NS).uid("1234"));
  }

  @Test
  void whenOperatorStartedInDedicatedMode_namespaceWatcherIsNotCreated() {
    defineSelectionStrategy(SelectionStrategy.DEDICATED);

    operatorMain.startDeployment(null);

    assertThat(operatorMain.getNamespaceWatcher(), nullValue());
  }

  @Test
  void whenOperatorStartedInDedicatedMode_operatorNamespaceEventWatcherIsNotCreated() {
    defineSelectionStrategy(SelectionStrategy.DEDICATED);

    operatorMain.startDeployment(null);

    assertThat(operatorMain.getOperatorNamespaceEventWatcher(), nullValue());
  }

  @Test
  void whenUnableToCreateCRD_dontTryToStartWatchers() {
    simulateMissingCRD();

    recheckDomains();

    verifyWatchersNotDefined(operatorMain.getDomainNamespaces(), getOperatorNamespace());
  }

  private void simulateMissingCRD() {
    testSupport.failOnResource(DOMAIN, null, getOperatorNamespace(), HttpURLConnection.HTTP_NOT_FOUND);
  }

  private void simulateMissingCRDPermissions() {
    testSupport.failOnResource(CRD, null, null, HttpURLConnection.HTTP_FORBIDDEN);
  }

  private void recheckDomains() {
    testSupport.runSteps(operatorMain.createDomainRecheckSteps());
  }

  private void runCreateReadNamespacesStep() {
    testSupport.runSteps(new DomainRecheck(delegate, false).createReadNamespacesStep());
  }

  @Test
  void whenNoCRD_logReasonForFailure() {
    loggerControl.withLogLevel(Level.INFO).collectLogMessages(logRecords, WAIT_FOR_CRD_INSTALLATION);
    simulateMissingCRD();
    delegate.hideCRD();

    recheckDomains();

    assertThat(logRecords, containsInfo(WAIT_FOR_CRD_INSTALLATION));
  }

  @Test
  void whenCRDCreated_dontLogFailure() {
    loggerControl.withLogLevel(Level.SEVERE).collectLogMessages(logRecords, CRD_NOT_INSTALLED);
    simulateMissingCRD();

    recheckDomains();

    assertThat(logRecords, not(containsSevere(CRD_NOT_INSTALLED)));
  }

  @Test
  void afterLoggedCRDMissing_dontDoItASecondTime() {
    loggerControl.withLogLevel(Level.SEVERE).collectLogMessages(logRecords, CRD_NOT_INSTALLED);
    simulateMissingCRD();
    recheckDomains();
    logRecords.clear();

    recheckDomains();

    assertThat(logRecords, not(containsSevere(CRD_NOT_INSTALLED)));
  }

  @Test
  void afterMissingCRDdetected_correctionOfTheConditionAllowsProcessingToOccur() {
    defineSelectionStrategy(SelectionStrategy.DEDICATED);
    simulateMissingCRD();
    delegate.hideCRD();
    recheckDomains();

    testSupport.cancelFailures();
    simulateMissingCRDPermissions();
    recheckDomains();

    verifyWatchersDefined(operatorMain.getDomainNamespaces(), getOperatorNamespace());
  }

  @Test
  void afterMissingCRDcorrected_subsequentFailureLogsReasonForFailure() {
    simulateMissingCRD();
    recheckDomains();
    testSupport.cancelFailures();
    recheckDomains();

    loggerControl.withLogLevel(Level.INFO).collectLogMessages(logRecords, WAIT_FOR_CRD_INSTALLATION);
    simulateMissingCRD();
    delegate.hideCRD();
    recheckDomains();

    assertThat(logRecords, containsInfo(WAIT_FOR_CRD_INSTALLATION));
  }

  @Test
  void withNamespaceList_onReadNamespaces_startsNamespaces() {
    defineSelectionStrategy(SelectionStrategy.LIST);
    HelmAccessStub.defineVariable(HelmAccess.OPERATOR_DOMAIN_NAMESPACES,
          String.join(",", NS_WEBLOGIC1, NS_WEBLOGIC2, NS_WEBLOGIC3));
    testSupport.defineResources(NAMESPACE_WEBLOGIC1, NAMESPACE_WEBLOGIC2, NAMESPACE_WEBLOGIC3,
                                NAMESPACE_WEBLOGIC4, NAMESPACE_WEBLOGIC5);

    runCreateReadNamespacesStep();

    assertThat(getStartingNamespaces(), contains(NS_WEBLOGIC1, NS_WEBLOGIC2, NS_WEBLOGIC3));
  }

  private List<String> getStartingNamespaces() {
    return Arrays.stream(NAMESPACES).filter(domainNamespaces::isStarting).collect(Collectors.toList());
  }

  private List<String> getStartingNamespaces(String...namespaces) {
    return Arrays.stream(namespaces).filter(domainNamespaces::isStarting).collect(Collectors.toList());
  }

  @Nonnull
  DomainRecheck createDomainRecheck() {
    return new DomainRecheck(delegate);
  }

  @SuppressWarnings("unused")
  static class NamespaceStatusMatcher extends TypeSafeDiagnosingMatcher<NamespaceStatus> {

    private static NamespaceStatusMatcher isNamespaceStarting() {
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
  void withRegExp_onReadNamespaces_startsNamespaces() {
    defineSelectionStrategy(SelectionStrategy.REG_EXP);
    testSupport.defineResources(NAMESPACE_WEBLOGIC1, NAMESPACE_WEBLOGIC2, NAMESPACE_WEBLOGIC3,
          NAMESPACE_WEBLOGIC4, NAMESPACE_WEBLOGIC5);

    TuningParametersStub.setParameter("domainNamespaceRegExp", REGEXP);
    runCreateReadNamespacesStep();

    assertThat(getStartingNamespaces(), contains(NS_WEBLOGIC2, NS_WEBLOGIC4));
  }

  @Test
  void withLabelSelector_onReadNamespaces_startsNamespaces() {
    defineSelectionStrategy(SelectionStrategy.LABEL_SELECTOR);
    testSupport.defineResources(NAMESPACE_WEBLOGIC1, NAMESPACE_WEBLOGIC2, NAMESPACE_WEBLOGIC3,
          NAMESPACE_WEBLOGIC4, NAMESPACE_WEBLOGIC5);

    TuningParametersStub.setParameter("domainNamespaceLabelSelector", LABEL + "=" + VALUE);
    runCreateReadNamespacesStep();

    assertThat(getStartingNamespaces(), contains(NS_WEBLOGIC1, NS_WEBLOGIC3, NS_WEBLOGIC5));
  }

  private V1ObjectMeta createMetadata(OffsetDateTime creationTimestamp) {
    return new V1ObjectMeta()
        .name(DOMAIN_UID)
        .namespace(NS)
        .creationTimestamp(creationTimestamp)
        .resourceVersion("1");
  }

  @Test
  void whenConfiguredDomainNamespaceMissing_logWarning() {
    loggerControl.withLogLevel(Level.WARNING).collectLogMessages(logRecords, MessageKeys.NAMESPACE_IS_MISSING);

    defineSelectionStrategy(SelectionStrategy.LIST);
    String namespaceString = "NS1,NS" + LAST_NAMESPACE_NUM;
    HelmAccessStub.defineVariable(HelmAccess.OPERATOR_DOMAIN_NAMESPACES, namespaceString);
    createNamespaces(LAST_NAMESPACE_NUM - 1);

    testSupport.runSteps(createDomainRecheck().readExistingNamespaces());

    assertThat(logRecords, containsWarning(MessageKeys.NAMESPACE_IS_MISSING));
  }

  @Test
  void whenNamespacesListedInOneChunk_dontDeclarePresentNamespacesAsMissing() {
    loggerControl.withLogLevel(Level.WARNING).collectLogMessages(logRecords, MessageKeys.NAMESPACE_IS_MISSING);

    defineSelectionStrategy(SelectionStrategy.LIST);
    String namespaceString = "NS1,NS" + LAST_NAMESPACE_NUM;
    HelmAccessStub.defineVariable(HelmAccess.OPERATOR_DOMAIN_NAMESPACES, namespaceString);
    createNamespaces(LAST_NAMESPACE_NUM);

    testSupport.runSteps(createDomainRecheck().readExistingNamespaces());
  }

  private void defineSelectionStrategy(SelectionStrategy selectionStrategy) {
    TuningParametersStub.setParameter(Namespaces.SELECTION_STRATEGY_KEY, selectionStrategy.toString());
  }

  @Test
  void whenNamespacesListedInMultipleChunks_allNamespacesStarted() {
    loggerControl.withLogLevel(Level.WARNING).collectLogMessages(logRecords, MessageKeys.NAMESPACE_IS_MISSING);

    defineSelectionStrategy(SelectionStrategy.LIST);
    String namespaceString = "NS1,NS" + MULTICHUNK_LAST_NAMESPACE_NUM;
    HelmAccessStub.defineVariable(HelmAccess.OPERATOR_DOMAIN_NAMESPACES, namespaceString);
    createNamespaces(MULTICHUNK_LAST_NAMESPACE_NUM);

    testSupport.runSteps(createDomainRecheck().readExistingNamespaces());

    assertThat(getStartingNamespaces("NS1", "NS" + MULTICHUNK_LAST_NAMESPACE_NUM),
        contains("NS1", "NS" + MULTICHUNK_LAST_NAMESPACE_NUM));
  }

  @Test
  void whenNamespacesListedInMoreThanTwoChunks_allNamespacesStarted() {
    loggerControl.withLogLevel(Level.WARNING).collectLogMessages(logRecords, MessageKeys.NAMESPACE_IS_MISSING);
    int lastNSNumber = DEFAULT_CALL_LIMIT * 3 + 1;
    defineSelectionStrategy(SelectionStrategy.LIST);
    String namespaceString = "NS1,NS" + lastNSNumber;
    HelmAccessStub.defineVariable(HelmAccess.OPERATOR_DOMAIN_NAMESPACES, namespaceString);
    createNamespaces(lastNSNumber);

    testSupport.runSteps(createDomainRecheck().readExistingNamespaces());

    assertThat(getStartingNamespaces("NS1", "NS" + lastNSNumber), contains("NS1", "NS" + lastNSNumber));
  }

  @Test
  void whenNamespacesListedInMultipleChunks_dontDeclarePresentNamespacesAsMissing() {
    loggerControl.withLogLevel(Level.WARNING).collectLogMessages(logRecords, MessageKeys.NAMESPACE_IS_MISSING);

    defineSelectionStrategy(SelectionStrategy.LIST);
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
  void deleteDomainPresenceWithTimeCheck_delete_with_same_DateTime() {
    OffsetDateTime creationDatetime = SystemClock.now();
    V1ObjectMeta domainMeta = createMetadata(creationDatetime);

    V1ObjectMeta domain2Meta = createMetadata(creationDatetime);

    assertFalse(KubernetesUtils.isFirstNewer(domainMeta, domain2Meta));
  }

  @Test
  void deleteDomainPresenceWithTimeCheck_delete_with_newer_DateTime() {
    OffsetDateTime creationDatetime = SystemClock.now();
    V1ObjectMeta domainMeta = createMetadata(creationDatetime);

    OffsetDateTime deleteDatetime = creationDatetime.plusMinutes(1);
    V1ObjectMeta domain2Meta = createMetadata(deleteDatetime);

    assertFalse(KubernetesUtils.isFirstNewer(domainMeta, domain2Meta));
  }

  @Test
  void deleteDomainPresenceWithTimeCheck_doNotDelete_with_older_DateTime() {
    OffsetDateTime creationDatetime = SystemClock.now();
    V1ObjectMeta domainMeta = createMetadata(creationDatetime);

    OffsetDateTime deleteDatetime = creationDatetime.minusMinutes(1);
    V1ObjectMeta domain2Meta = createMetadata(deleteDatetime);

    assertTrue(KubernetesUtils.isFirstNewer(domainMeta, domain2Meta));
  }

  @Test
  void afterReadingExistingResourcesForNamespace_WatchersAreDefined() {
    testSupport.runSteps(domainNamespaces.readExistingResources(NS, createStrictStub(DomainProcessor.class)));

    assertThat(domainNamespaces.getClusterWatcher(NS), notNullValue());
    assertThat(domainNamespaces.getConfigMapWatcher(NS), notNullValue());
    assertThat(domainNamespaces.getDomainWatcher(NS), notNullValue());
    assertThat(domainNamespaces.getEventWatcher(NS), notNullValue());
    assertThat(domainNamespaces.getDomainEventWatcher(NS), notNullValue());
    assertThat(domainNamespaces.getPodWatcher(NS), notNullValue());
    assertThat(domainNamespaces.getServiceWatcher(NS), notNullValue());
    assertThat(domainNamespaces.getPodDisruptionBudgetWatcher(NS), notNullValue());
  }

  @Test
  void afterReadingExistingResourcesForNamespace_ScriptConfigMapIsDefined() {
    testSupport.runSteps(domainNamespaces.readExistingResources(NS, createStrictStub(DomainProcessor.class)));

    assertThat(getScriptMap(NS), notNullValue());
  }

  @Test
  void afterReadingExistingResourcesForNamespace_withoutExistingEvent_domainEventK8SObjectsEmpty() {
    testSupport.runSteps(domainNamespaces.readExistingResources(NS, createStrictStub(DomainProcessor.class)));
    assertThat(getDomainEventMapSize(), equalTo(0));
  }

  @Test
  void afterReadingExistingResourcesForNamespace_withExistingEvents_domainEventK8SObjectsPopulated() {
    CoreV1Event event1 = createDomainEvent("event1").reason(DOMAIN_CREATED_EVENT);
    CoreV1Event event2 = createDomainEvent("event2").reason(DOMAIN_CHANGED_EVENT);

    testSupport.defineResources(event1, event2);
    testSupport.runSteps(domainNamespaces.readExistingResources(NS, createStrictStub(DomainProcessor.class)));

    assertThat(getDomainEventMapSize(), equalTo(2));
  }

  private int getDomainEventMapSize() {
    return Optional.ofNullable(domainEventObjects.get(NS))
        .map(m -> this.getEventMapForDomain(m, DOMAIN_UID))
        .map(KubernetesEventObjects::size)
        .orElse(0);
  }

  @SuppressWarnings("SameParameterValue")
  private KubernetesEventObjects getEventMapForDomain(Map<String, KubernetesEventObjects> map, String domainUid) {
    return map.get(domainUid);
  }

  private CoreV1Event createDomainEvent(String name) {
    return new CoreV1Event()
        .metadata(new V1ObjectMeta()
            .name(name)
            .namespace(NS)
            .putLabelsItem(CREATEDBYOPERATOR_LABEL, "true")
            .putLabelsItem(DOMAINUID_LABEL, DOMAIN_UID))
        .involvedObject(new V1ObjectReference().name(DOMAIN_UID).namespace(NS).uid("abcd"));
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
  void beforeNamespaceAdded_watchersAreNotDefined() {
    HelmAccessStub.defineVariable(HelmAccess.OPERATOR_DOMAIN_NAMESPACES, ns);

    verifyWatchersNotDefined(operatorMain.getDomainNamespaces(), ns);
  }

  private void verifyWatchersNotDefined(DomainNamespaces domainNamespaces, String ns) {
    assertThat(domainNamespaces.getClusterWatcher(ns), nullValue());
    assertThat(domainNamespaces.getConfigMapWatcher(ns), nullValue());
    assertThat(domainNamespaces.getDomainWatcher(ns), nullValue());
    assertThat(domainNamespaces.getEventWatcher(ns), nullValue());
    assertThat(domainNamespaces.getDomainEventWatcher(ns), nullValue());
    assertThat(domainNamespaces.getJobWatcher(ns), nullValue());
    assertThat(domainNamespaces.getPodWatcher(ns), nullValue());
    assertThat(domainNamespaces.getServiceWatcher(ns), nullValue());
  }

  @Test
  void afterNullNamespaceAdded_WatchersAreNotDefined() {
    operatorMain.dispatchNamespaceWatch(WatchEvent.createAddedEvent((V1Namespace) null).toWatchResponse());

    verifyWatchersNotDefined(operatorMain.getDomainNamespaces(), ns);
  }

  @Test
  void afterNonDomainNamespaceAdded_WatchersAreNotDefined() {
    HelmAccessStub.defineVariable(HelmAccess.OPERATOR_DOMAIN_NAMESPACES, NS_WEBLOGIC1);
    HelmAccessStub.defineVariable(SELECTION_STRATEGY_KEY, Namespaces.SelectionStrategy.LIST.toString());
    V1Namespace namespace = new V1Namespace().metadata(new V1ObjectMeta().name(ns));
    operatorMain.dispatchNamespaceWatch(WatchEvent.createAddedEvent(namespace).toWatchResponse());

    verifyWatchersNotDefined(operatorMain.getDomainNamespaces(), ns);
  }

  @Test
  void afterNamespaceAdded_WatchersAreDefined() {
    defineSelectionStrategy(SelectionStrategy.LIST);
    HelmAccessStub.defineVariable(HelmAccess.OPERATOR_DOMAIN_NAMESPACES, ns);
    V1Namespace namespace = new V1Namespace().metadata(new V1ObjectMeta().name(ns));

    operatorMain.dispatchNamespaceWatch(WatchEvent.createAddedEvent(namespace).toWatchResponse());

    verifyWatchersDefined(operatorMain.getDomainNamespaces(), ns);
  }

  @Test
  void afterNamespaceDeleted_isDeletingSetToTrue() {
    operatorMain.getDomainNamespaces().isStopping(ns);
    defineSelectionStrategy(SelectionStrategy.LIST);
    HelmAccessStub.defineVariable(HelmAccess.OPERATOR_DOMAIN_NAMESPACES, ns);
    V1Namespace namespace = new V1Namespace().metadata(new V1ObjectMeta().name(ns));

    operatorMain.dispatchNamespaceWatch(WatchEvent.createDeletedEvent(namespace).toWatchResponse());

    assertThat(operatorMain.getDomainNamespaces().isStopping(ns).get(), is(true));
  }

  @Test
  void afterNonManagedNamespaceDeleted_whenUsedToBeManaged_isDeletingIsTrue() {
    String ns2 = "non-managed-ns";
    operatorMain.getDomainNamespaces().isStopping(ns2);
    defineSelectionStrategy(SelectionStrategy.LIST);
    HelmAccessStub.defineVariable(HelmAccess.OPERATOR_DOMAIN_NAMESPACES, ns);
    V1Namespace namespace = new V1Namespace().metadata(new V1ObjectMeta().name(ns2));

    operatorMain.dispatchNamespaceWatch(WatchEvent.createDeletedEvent(namespace).toWatchResponse());

    assertThat(operatorMain.getDomainNamespaces().isStopping(ns2).get(), is(true));
  }

  @Test
  void afterNonManagedNamespaceDeleted_isDeletingIsFalse() {
    String ns2 = "non-managed-ns";
    defineSelectionStrategy(SelectionStrategy.LIST);
    HelmAccessStub.defineVariable(HelmAccess.OPERATOR_DOMAIN_NAMESPACES, ns);
    V1Namespace namespace = new V1Namespace().metadata(new V1ObjectMeta().name(ns2));

    operatorMain.dispatchNamespaceWatch(WatchEvent.createDeletedEvent(namespace).toWatchResponse());

    assertThat(operatorMain.getDomainNamespaces().isStopping(ns2).get(), is(false));
  }

  private void verifyWatchersDefined(DomainNamespaces domainNamespaces, String ns) {
    assertThat(domainNamespaces.getClusterWatcher(ns), notNullValue());
    assertThat(domainNamespaces.getConfigMapWatcher(ns), notNullValue());
    assertThat(domainNamespaces.getDomainWatcher(ns), notNullValue());
    assertThat(domainNamespaces.getEventWatcher(ns), notNullValue());
    assertThat(domainNamespaces.getDomainEventWatcher(ns), notNullValue());
    assertThat(domainNamespaces.getJobWatcher(ns), notNullValue());
    assertThat(domainNamespaces.getPodWatcher(ns), notNullValue());
    assertThat(domainNamespaces.getServiceWatcher(ns), notNullValue());
  }

  @Test
  void afterNamespaceAdded_scriptConfigMapIsDefined() {
    defineSelectionStrategy(SelectionStrategy.LIST);
    HelmAccessStub.defineVariable(HelmAccess.OPERATOR_DOMAIN_NAMESPACES, ns);
    V1Namespace namespace = new V1Namespace().metadata(new V1ObjectMeta().name(ns));
    operatorMain.dispatchNamespaceWatch(WatchEvent.createAddedEvent(namespace).toWatchResponse());

    assertThat(getScriptMap(ns), notNullValue());
  }

  @Test
  void withNamespaceList_onStartNamespaces_nsWatchStartedEventCreatedWithExpectedMessage() {
    defineSelectionStrategy(SelectionStrategy.LIST);
    HelmAccessStub.defineVariable(HelmAccess.OPERATOR_DOMAIN_NAMESPACES,
        String.join(",", NS_WEBLOGIC1, NS_WEBLOGIC2, NS_WEBLOGIC3));
    testSupport.defineResources(NAMESPACE_WEBLOGIC1, NAMESPACE_WEBLOGIC2, NAMESPACE_WEBLOGIC3,
        NAMESPACE_WEBLOGIC4, NAMESPACE_WEBLOGIC5);

    List<String> namespaces = Arrays.asList(NS_WEBLOGIC1, NS_WEBLOGIC2, NS_WEBLOGIC3);
    testSupport.runSteps(
        createDomainRecheck().createStartNamespacesStep(namespaces));

    assertThat("Found NAMESPACE_WATCHING_STARTED event with expected message for all namespaces",
        containsEventWithMessageForNamespaces(getEvents(testSupport),
            NAMESPACE_WATCHING_STARTED, namespaces), is(true));
  }

  @Test
  void withNamespaceList_onStartNamespaces_startManagingNSEventCreatedWithExpectedMessage() {
    defineSelectionStrategy(SelectionStrategy.LIST);
    HelmAccessStub.defineVariable(HelmAccess.OPERATOR_DOMAIN_NAMESPACES, NS_WEBLOGIC1);

    testSupport.defineResources(NAMESPACE_WEBLOGIC1, NAMESPACE_WEBLOGIC2);

    List<String> namespaces = Collections.singletonList(NS_WEBLOGIC1);
    testSupport.runSteps(
        createDomainRecheck().createStartNamespacesStep(namespaces));

    assertThat("Found START_MANAGING_NAMESPACE event with expected message",
        containsEventWithMessage(getEvents(testSupport),
            START_MANAGING_NAMESPACE_EVENT,
            getFormattedMessage(START_MANAGING_NAMESPACE_EVENT_PATTERN, NS_WEBLOGIC1)),
        is(true));
  }

  @Test
  void withNamespaceList_onStartNamespaces_foundExpectedLogMessage() {
    logRecords.clear();
    loggerControl.withLogLevel(Level.INFO).collectLogMessages(logRecords, MessageKeys.BEGIN_MANAGING_NAMESPACE);
    defineSelectionStrategy(SelectionStrategy.LIST);
    HelmAccessStub.defineVariable(HelmAccess.OPERATOR_DOMAIN_NAMESPACES,
        String.join(",", NS_WEBLOGIC1, NS_WEBLOGIC2, NS_WEBLOGIC3));
    testSupport.defineResources(NAMESPACE_WEBLOGIC1, NAMESPACE_WEBLOGIC2, NAMESPACE_WEBLOGIC3,
        NAMESPACE_WEBLOGIC4, NAMESPACE_WEBLOGIC5);

    List<String> namespaces = Arrays.asList(NS_WEBLOGIC1, NS_WEBLOGIC2, NS_WEBLOGIC3);
    testSupport.runSteps(
        createDomainRecheck().createStartNamespacesStep(namespaces));

    assertThat(logRecords, containsInfo(MessageKeys.BEGIN_MANAGING_NAMESPACE).withParams(NS_WEBLOGIC1));
    assertThat(logRecords, containsInfo(MessageKeys.BEGIN_MANAGING_NAMESPACE).withParams(NS_WEBLOGIC2));
    assertThat(logRecords, containsInfo(MessageKeys.BEGIN_MANAGING_NAMESPACE).withParams(NS_WEBLOGIC3));
  }

  @Test
  void withNamespaceList_onReadNamespaces_whenConfiguredDomainNamespaceMissing_noEventCreated() {
    defineSelectionStrategy(SelectionStrategy.LIST);
    String namespaceString = "NS" + LAST_NAMESPACE_NUM + ",NS" + DEFAULT_CALL_LIMIT;
    HelmAccessStub.defineVariable(HelmAccess.OPERATOR_DOMAIN_NAMESPACES, namespaceString);
    createNamespaces(LAST_NAMESPACE_NUM - 1);

    runCreateReadNamespacesStep();

    assertThat("Found no event",
        containsEvent(getEvents(testSupport), NAMESPACE_WATCHING_STARTED_EVENT), is(false));
  }

  @Test
  void withNamespaceList_onReadNamespaces_whenDomainNamespaceRemoved_nsWatchStoppedEventCreated() {
    domainNamespaces.isStopping("NS3");
    defineSelectionStrategy(SelectionStrategy.LIST);
    String namespaceString = "NS1,NS2";
    HelmAccessStub.defineVariable(HelmAccess.OPERATOR_DOMAIN_NAMESPACES, namespaceString);
    createNamespaces(4);

    runCreateReadNamespacesStep();
    assertThat("Found NAMESPACE_WATCHING_STOPPED event with expected message",
        containsEventWithMessageForNamespaces(getEvents(testSupport),
            NAMESPACE_WATCHING_STOPPED, Collections.singletonList("NS3")), is(true));
  }

  @Test
  void withNamespaceList_onReadNamespaces_whenDomainNamespaceRemovedAndIsStoppingTrue_nsWatchStoppedEventNotCreated() {
    domainNamespaces.isStopping("NS3").set(true);
    defineSelectionStrategy(SelectionStrategy.LIST);
    String namespaceString = "NS1,NS2";
    HelmAccessStub.defineVariable(HelmAccess.OPERATOR_DOMAIN_NAMESPACES, namespaceString);
    createNamespaces(4);

    runCreateReadNamespacesStep();
    assertThat("Found NAMESPACE_WATCHING_STOPPED event with expected message",
        containsEventWithMessageForNamespaces(getEvents(testSupport),
            NAMESPACE_WATCHING_STOPPED, Collections.singletonList("NS3")), is(false));
  }

  @Test
  void withNamespaceList_onReadNamespaces_whenDomainNamespaceRemoved_stopManagingNSEventCreated() {
    domainNamespaces.isStopping("NS3");
    defineSelectionStrategy(SelectionStrategy.LIST);
    String namespaceString = "NS1,NS2";
    HelmAccessStub.defineVariable(HelmAccess.OPERATOR_DOMAIN_NAMESPACES, namespaceString);
    createNamespaces(4);

    runCreateReadNamespacesStep();

    assertThat("Found NAMESPACE_WATCHING_STOPPED event with expected message",
        containsEventWithMessageForNamespaces(getEvents(testSupport),
            STOP_MANAGING_NAMESPACE, Collections.singletonList("NS3")), is(true));
  }

  @Test
  void withNamespaceList_onReadNamespaces_whenDomainNamespaceRemovedAndIsStoppingTrue_stopManagingNSEventCreated() {
    domainNamespaces.isStopping("NS3").set(true);
    defineSelectionStrategy(SelectionStrategy.LIST);
    String namespaceString = "NS1,NS2";
    HelmAccessStub.defineVariable(HelmAccess.OPERATOR_DOMAIN_NAMESPACES, namespaceString);
    createNamespaces(4);

    runCreateReadNamespacesStep();

    assertThat("Found NAMESPACE_WATCHING_STOPPED event with expected message",
        containsEventWithMessageForNamespaces(getEvents(testSupport),
            STOP_MANAGING_NAMESPACE, Collections.singletonList("NS3")), is(true));
  }

  @Test
  void withNamespaceList_onReadNamespaces_whenDomainNamespaceRemoved_foundExpectedLogMessage() {
    logRecords.clear();
    loggerControl.withLogLevel(Level.INFO).collectLogMessages(logRecords, MessageKeys.END_MANAGING_NAMESPACE);
    domainNamespaces.isStopping("NS3");
    defineSelectionStrategy(SelectionStrategy.LIST);
    String namespaceString = "NS1,NS2";
    HelmAccessStub.defineVariable(HelmAccess.OPERATOR_DOMAIN_NAMESPACES, namespaceString);
    createNamespaces(4);

    runCreateReadNamespacesStep();
    assertThat(logRecords, containsInfo(MessageKeys.END_MANAGING_NAMESPACE).withParams("NS3"));
  }

  @Test
  void withNamespaceList_changeToDedicated_onReadNamespaces_nsWatchStoppedEventCreated() {
    defineNamespaceListStrategy("NS1");
    runCreateReadNamespacesStep();

    defineSelectionStrategy(SelectionStrategy.DEDICATED);
    runCreateReadNamespacesStep();

    assertThat("Found NAMESPACE_WATCHING_STOPPED event with expected message",
        containsEventWithMessageForNamespaces(getEvents(testSupport),
            NAMESPACE_WATCHING_STOPPED, Collections.singletonList("NS1")), is(true));
  }

  @Test
  void withNamespaceList_changeToDedicated_onReadNamespaces_nsWatchStartedEventCreatedInOpNS() {
    defineNamespaceListStrategy("NS1");
    runCreateReadNamespacesStep();

    defineSelectionStrategy(SelectionStrategy.DEDICATED);
    runCreateReadNamespacesStep();

    assertThat("Found NAMESPACE_WATCHING_STARTED event with expected message",
        containsEventWithMessageForNamespaces(getEvents(testSupport),
            NAMESPACE_WATCHING_STARTED, Collections.singletonList(OP_NS)), is(true));
  }

  @SuppressWarnings("SameParameterValue")
  private void defineNamespaceListStrategy(String namespaceString) {
    defineSelectionStrategy(SelectionStrategy.LIST);
    HelmAccessStub.defineVariable(HelmAccess.OPERATOR_DOMAIN_NAMESPACES, namespaceString);
    createNamespaces(4);
  }

  @Test
  void withNamespaceList_changeToDedicated_onReadNamespaces_StartManagingEventCreatedInOpNS() {
    defineNamespaceListStrategy("NS1");
    runCreateReadNamespacesStep();

    defineSelectionStrategy(SelectionStrategy.DEDICATED);
    runCreateReadNamespacesStep();

    assertThat("Found START_MANAGING_NAMESPACE event with expected message",
        containsEventWithMessageForNamespaces(getEvents(testSupport),
            START_MANAGING_NAMESPACE, Collections.singletonList(OP_NS)), is(true));
  }

  @Test
  void withNamespaceList_changeToDedicated_onReadNamespaces_nsEventMapIsEmpty() {
    defineNamespaceListStrategy("NS1");
    runCreateReadNamespacesStep();

    assertThat("Confirmed that the event maps for the namespace are empty before changing strategy",
        eventMapsEmpty("NS1"), is(false));

    defineSelectionStrategy(SelectionStrategy.DEDICATED);
    runCreateReadNamespacesStep();

    assertThat("Confirmed that the event maps for the namespace are empty", eventMapsEmpty("NS1"), is(true));
  }

  @Test
  void withNamespaceLabelSelector_onStartNamespaces_nsWatchStartedEventCreatedWithExpectedMessage() {
    defineSelectionStrategy(SelectionStrategy.LABEL_SELECTOR);
    testSupport.defineResources(NAMESPACE_WEBLOGIC1, NAMESPACE_WEBLOGIC2, NAMESPACE_WEBLOGIC3,
        NAMESPACE_WEBLOGIC4, NAMESPACE_WEBLOGIC5);

    TuningParametersStub.setParameter("domainNamespaceLabelSelector", LABEL + "=" + VALUE);

    List<String> namespaces = Arrays.asList(NS_WEBLOGIC1, NS_WEBLOGIC2, NS_WEBLOGIC3);
    testSupport.runSteps(
        createDomainRecheck().createStartNamespacesStep(namespaces));

    assertThat("Found NAMESPACE_WATCHING_STARTED event with expected message for all namespaces",
        containsEventWithMessageForNamespaces(getEvents(testSupport),
            NAMESPACE_WATCHING_STARTED, namespaces), is(true));
  }

  @Test
  void withNamespaceLabelSelector_onStartNamespaces_startManagingNSEventCreatedWithExpectedMessage() {
    defineSelectionStrategy(SelectionStrategy.LABEL_SELECTOR);
    testSupport.defineResources(NAMESPACE_WEBLOGIC1, NAMESPACE_WEBLOGIC2, NAMESPACE_WEBLOGIC3,
        NAMESPACE_WEBLOGIC4, NAMESPACE_WEBLOGIC5);

    TuningParametersStub.setParameter("domainNamespaceLabelSelector", LABEL + "=" + VALUE);

    List<String> namespaces = Arrays.asList(NS_WEBLOGIC1, NS_WEBLOGIC2, NS_WEBLOGIC3);
    testSupport.runSteps(
        createDomainRecheck().createStartNamespacesStep(namespaces));

    assertThat("Found START_MANAGING_NAMESPACE event with expected message for all namespaces",
        containsEventWithMessageForNamespaces(getEvents(testSupport),
            START_MANAGING_NAMESPACE, namespaces), is(true));
  }

  @Test
  void withNamespaceLabelSelector_whenLabelRemoved_nsWatchStoppedEventCreated() {
    domainNamespaces.isStopping("NS3");
    testSupport.defineResources(NAMESPACE_WEBLOGIC1, NAMESPACE_WEBLOGIC2, NAMESPACE_WEBLOGIC3,
        NAMESPACE_WEBLOGIC4, NAMESPACE_WEBLOGIC5);

    defineSelectionStrategy(SelectionStrategy.LABEL_SELECTOR);
    TuningParametersStub.setParameter("domainNamespaceLabelSelector", LABEL + "=" + VALUE);

    runCreateReadNamespacesStep();

    assertThat("Found NAMESPACE_WATCHING_STOPPED event with expected message",
            containsEventWithMessageForNamespaces(getEvents(testSupport),
                NAMESPACE_WATCHING_STOPPED, Collections.singletonList("NS3")), is(true));
  }

  @Test
  void withNamespaceLabelSelector_whenLabelRemovedAndIsDeletingTrue_nsWatchStoppedEventNotCreated() {
    domainNamespaces.isStopping("NS3").set(true);
    testSupport.defineResources(NAMESPACE_WEBLOGIC1, NAMESPACE_WEBLOGIC2, NAMESPACE_WEBLOGIC3,
        NAMESPACE_WEBLOGIC4, NAMESPACE_WEBLOGIC5);

    defineSelectionStrategy(SelectionStrategy.LABEL_SELECTOR);
    TuningParametersStub.setParameter("domainNamespaceLabelSelector", LABEL + "=" + VALUE);

    runCreateReadNamespacesStep();

    assertThat("Found NAMESPACE_WATCHING_STOPPED event with expected message",
        containsEventWithMessageForNamespaces(getEvents(testSupport),
            NAMESPACE_WATCHING_STOPPED, Collections.singletonList("NS3")), is(false));
  }

  @Test
  void withNamespaceLabelSelector_onReadNamespaces_whenLabelRemoved_stopManagingNSEventCreated() {
    domainNamespaces.isStopping("NS3");
    testSupport.defineResources(NAMESPACE_WEBLOGIC1, NAMESPACE_WEBLOGIC2, NAMESPACE_WEBLOGIC3,
        NAMESPACE_WEBLOGIC4, NAMESPACE_WEBLOGIC5);
    defineSelectionStrategy(SelectionStrategy.LABEL_SELECTOR);
    TuningParametersStub.setParameter("domainNamespaceLabelSelector", LABEL + "=" + VALUE);

    runCreateReadNamespacesStep();

    assertThat("Found NAMESPACE_WATCHING_STOPPED event with expected message",
        containsEventWithMessageForNamespaces(getEvents(testSupport),
            STOP_MANAGING_NAMESPACE, Collections.singletonList("NS3")), is(true));
  }

  @Test
  void withNamespaceLabelSelector_onReadNamespaces_whenNamespaceLabelRemoved_foundExpectedLogMessage() {
    logRecords.clear();
    loggerControl.withLogLevel(Level.INFO).collectLogMessages(logRecords, MessageKeys.END_MANAGING_NAMESPACE);
    domainNamespaces.isStopping("NS3");
    testSupport.defineResources(NAMESPACE_WEBLOGIC1, NAMESPACE_WEBLOGIC2, NAMESPACE_WEBLOGIC3,
        NAMESPACE_WEBLOGIC4, NAMESPACE_WEBLOGIC5);
    defineSelectionStrategy(SelectionStrategy.LABEL_SELECTOR);
    TuningParametersStub.setParameter("domainNamespaceLabelSelector", LABEL + "=" + VALUE);

    runCreateReadNamespacesStep();

    assertThat(logRecords, containsInfo(MessageKeys.END_MANAGING_NAMESPACE).withParams("NS3"));
  }

  @Test
  void withNamespaceRegExp_onStartNamespaces_nsWatchStartedEventCreatedWithExpectedMessage() {
    defineSelectionStrategy(SelectionStrategy.REG_EXP);
    testSupport.defineResources(NAMESPACE_WEBLOGIC1, NAMESPACE_WEBLOGIC2, NAMESPACE_WEBLOGIC3,
        NAMESPACE_WEBLOGIC4, NAMESPACE_WEBLOGIC5);
    TuningParametersStub.setParameter("domainNamespaceRegExp", REGEXP);
    List<String> namespaces = Arrays.asList(NS_WEBLOGIC1, NS_WEBLOGIC2, NS_WEBLOGIC3);

    testSupport.runSteps(
        createDomainRecheck().createStartNamespacesStep(namespaces));

    assertThat("Found NAMESPACE_WATCHING_STARTED event with expected message for all namespaces",
        containsEventWithMessageForNamespaces(getEvents(testSupport),
            NAMESPACE_WATCHING_STARTED, namespaces), is(true));
  }

  @Test
  void withNamespaceRegExp_onStartNamespaces_startManagingNSEventCreatedWithExpectedMessage() {
    defineSelectionStrategy(SelectionStrategy.REG_EXP);
    testSupport.defineResources(NAMESPACE_WEBLOGIC1, NAMESPACE_WEBLOGIC2, NAMESPACE_WEBLOGIC3,
        NAMESPACE_WEBLOGIC4, NAMESPACE_WEBLOGIC5);

    TuningParametersStub.setParameter("domainNamespaceRegExp", REGEXP);

    List<String> namespaces = Arrays.asList(NS_WEBLOGIC1, NS_WEBLOGIC2, NS_WEBLOGIC3);
    testSupport.runSteps(
        createDomainRecheck().createStartNamespacesStep(namespaces));

    assertThat("Found START_MANAGING_NAMESPACE event with expected message for all namespaces",
        containsEventWithMessageForNamespaces(getEvents(testSupport),
            START_MANAGING_NAMESPACE, namespaces), is(true));
  }

  @Test
  void withNamespaceRegExp_onReadNamespaces_whenNamespaceLabelRemoved_nsWatchStoppedEventCreated() {
    domainNamespaces.isStopping("NS3");
    testSupport.defineResources(NAMESPACE_WEBLOGIC1, NAMESPACE_WEBLOGIC2, NAMESPACE_WEBLOGIC3,
        NAMESPACE_WEBLOGIC4, NAMESPACE_WEBLOGIC5);

    defineSelectionStrategy(SelectionStrategy.REG_EXP);
    TuningParametersStub.setParameter("domainNamespaceRegExp", REGEXP);
    runCreateReadNamespacesStep();

    assertThat("Found NAMESPACE_WATCHING_STOPPED event with expected message",
        containsEventWithMessageForNamespaces(getEvents(testSupport),
            NAMESPACE_WATCHING_STOPPED, Collections.singletonList("NS3")), is(true));
  }

  @Test
  void withNamespaceRegExp_onReadNamespaces_whenNamespaceLabelRemovedAndIsDeletingTrue_nsWatchStoppedEventNotCreated() {
    domainNamespaces.isStopping("NS3").set(true);
    testSupport.defineResources(NAMESPACE_WEBLOGIC1, NAMESPACE_WEBLOGIC2, NAMESPACE_WEBLOGIC3,
        NAMESPACE_WEBLOGIC4, NAMESPACE_WEBLOGIC5);

    defineSelectionStrategy(SelectionStrategy.REG_EXP);
    TuningParametersStub.setParameter("domainNamespaceRegExp", REGEXP);
    runCreateReadNamespacesStep();

    assertThat("Found NAMESPACE_WATCHING_STOPPED event with expected message",
        containsEventWithMessageForNamespaces(getEvents(testSupport),
            NAMESPACE_WATCHING_STOPPED, Collections.singletonList("NS3")), is(false));
  }

  @Test
  void withNamespaceRegExp_onCreateReadNamespaces_whenNamespaceLabelRemoved_stopManagingNSEventCreated() {
    domainNamespaces.isStopping("NS3");
    testSupport.defineResources(NAMESPACE_WEBLOGIC1, NAMESPACE_WEBLOGIC2, NAMESPACE_WEBLOGIC3,
        NAMESPACE_WEBLOGIC4, NAMESPACE_WEBLOGIC5);

    defineSelectionStrategy(SelectionStrategy.REG_EXP);
    TuningParametersStub.setParameter("domainNamespaceRegExp", REGEXP);
    runCreateReadNamespacesStep();

    assertThat("Found NAMESPACE_WATCHING_STOPPED event with expected message",
        containsEventWithMessageForNamespaces(getEvents(testSupport),
            STOP_MANAGING_NAMESPACE, Collections.singletonList("NS3")), is(true));
  }

  @Test
  void withNamespaceRegExp_onReadNamespaces_whenNamespaceLabelRemoved_foundExpectedLogMessage() {
    logRecords.clear();
    loggerControl.withLogLevel(Level.INFO).collectLogMessages(logRecords, MessageKeys.END_MANAGING_NAMESPACE);
    domainNamespaces.isStopping("NS3");
    testSupport.defineResources(NAMESPACE_WEBLOGIC1, NAMESPACE_WEBLOGIC2, NAMESPACE_WEBLOGIC3,
        NAMESPACE_WEBLOGIC4, NAMESPACE_WEBLOGIC5);

    defineSelectionStrategy(SelectionStrategy.REG_EXP);
    TuningParametersStub.setParameter("domainNamespaceRegExp", REGEXP);
    runCreateReadNamespacesStep();

    assertThat(logRecords, containsInfo(MessageKeys.END_MANAGING_NAMESPACE).withParams("NS3"));
  }

  @Test
  void withNamespaceDedicated_onReadNamespaces_nsWatchStartedEventCreatedWithExpectedMessage() {
    defineSelectionStrategy(SelectionStrategy.DEDICATED);
    runCreateReadNamespacesStep();

    assertThat("Found NAMESPACE_WATCHING_STARTED event with expected message for all namespaces",
        containsEventWithMessageForNamespaces(getEvents(testSupport),
            NAMESPACE_WATCHING_STARTED, Collections.singletonList(OP_NS)), is(true));
    assertThat("Found NAMESPACE_WATCHING_STARTED event with expected message for all namespaces",
        containsEventWithMessageForNamespaces(getEvents(testSupport),
            START_MANAGING_NAMESPACE, Collections.singletonList(OP_NS)), is(true));
  }

  @Test
  void withNamespaceDedicated_onReadNamespaces_startManagingNSEventCreatedWithExpectedMessage() {
    defineSelectionStrategy(SelectionStrategy.DEDICATED);
    runCreateReadNamespacesStep();

    assertThat("Found START_MANAGING_NAMESPACE event with expected message for all namespaces",
        containsEventWithMessageForNamespaces(getEvents(testSupport),
            START_MANAGING_NAMESPACE, Collections.singletonList(OP_NS)), is(true));
  }

  @Test
  void withNamespaceDedicated_changeToList_onReadNamespaces_nsWatchStoppedEventCreatedInOpNS() {
    defineSelectionStrategy(SelectionStrategy.DEDICATED);
    runCreateReadNamespacesStep();

    defineNamespaceListStrategy("NS1");
    runCreateReadNamespacesStep();

    assertThat("Found NAMESPACE_WATCHING_STOPPED event with expected message",
        containsEventWithMessageForNamespaces(getEvents(testSupport),
            NAMESPACE_WATCHING_STOPPED, Collections.singletonList(OP_NS)), is(true));
  }

  @Test
  void withNamespaceDedicated_changeToList_onReadNamespaces_opNSEventMapIsEmpty() {
    defineSelectionStrategy(SelectionStrategy.DEDICATED);
    runCreateReadNamespacesStep();

    assertThat("Confirm that the event maps for the namespace are not empty before change the strategy",
        eventMapsEmpty(OP_NS), is(false));

    defineNamespaceListStrategy("NS1");
    runCreateReadNamespacesStep();

    assertThat("Confirm that the event maps for the namespace are empty",
        eventMapsEmpty(OP_NS), is(true));
  }

  @Test
  void whenOperatorStarted_startMetricsServer() throws UnrecoverableKeyException, CertificateException, IOException,
      NoSuchAlgorithmException, KeyStoreException, InvalidKeySpecException, KeyManagementException {
    operatorMain.startMetricsServer(container, 9001);
    assertNotNull(operatorMain.getMetricsServer());
    assertInstanceOf(MetricsServer.class, operatorMain.getMetricsServer());
    HttpServer httpServer = ((MetricsServer) operatorMain.getMetricsServer()).getMetricsHttpServer();
    assertNotNull(httpServer);
    assertThat(httpServer.getServerConfiguration().getHttpHandlersWithMapping(),
        hasValue(new HttpHandlerRegistration[] {
            new HttpHandlerRegistration.Builder().contextPath("").urlPattern("/metrics").build() }));
    assertTrue(httpServer.isStarted());
  }

  @Test
  void whenOperatorStopping_stopMetricsServer() throws UnrecoverableKeyException, CertificateException, IOException,
      NoSuchAlgorithmException, KeyStoreException, InvalidKeySpecException, KeyManagementException {
    operatorMain.startMetricsServer(container, 9000);
    HttpServer httpServer = ((MetricsServer) operatorMain.getMetricsServer()).getMetricsHttpServer();
    assertNotNull(httpServer);
    assertTrue(httpServer.isStarted());

    operatorMain.stopMetricsServer();
    assertNull(operatorMain.getMetricsServer());
    assertFalse(httpServer.isStarted());
  }

  private boolean eventMapsEmpty(String ns) {
    return isNSEventMapEmpty(ns) && isDomainEventMapEmpty(ns);
  }

  private boolean isNSEventMapEmpty(String ns) {
    return nsEventObjects.get(ns) == null || nsEventObjects.get(ns).size() == 0;
  }

  private boolean isDomainEventMapEmpty(String ns) {
    return domainEventObjects.get(ns) == null || domainEventObjects.get(ns).size() == 0;
  }

  @Test
  void withNamespaceDedicated_changeToList_onReadNamespaces_nsWatchStartedEventCreated() {
    defineSelectionStrategy(SelectionStrategy.DEDICATED);
    runCreateReadNamespacesStep();

    defineNamespaceListStrategy("NS1");
    runCreateReadNamespacesStep();

    assertThat("Found NAMESPACE_WATCHING_STARTED event with expected message",
        containsEventWithMessageForNamespaces(getEvents(testSupport),
            NAMESPACE_WATCHING_STARTED, Collections.singletonList("NS1")), is(true));
  }

  @Test
  void withNamespaceDedicated_changeToList_onReadNamespaces_StartManagingNSEventCreated() {
    defineSelectionStrategy(SelectionStrategy.DEDICATED);
    runCreateReadNamespacesStep();

    defineNamespaceListStrategy("NS1");
    runCreateReadNamespacesStep();

    assertThat("Found START_MANAGING_NAMESPACE event with expected message",
        containsEventWithMessageForNamespaces(getEvents(testSupport),
            START_MANAGING_NAMESPACE, Collections.singletonList("NS1")), is(true));
  }

  @Test
  @ResourceLock(value = "operatorMain")
  void whenShutdownMarkerIsCreated_stopOperator() throws NoSuchFieldException {
    mementos.add(StaticStubSupport.install(
            BaseMain.class, "wrappedExecutorService", testSupport.getScheduledExecutorService()));
    inMemoryFileSystem.defineFile(delegate.getShutdownMarker(), "shutdown");
    testSupport.presetFixedDelay();

    operatorMain.doMain();

    assertThat(operatorMain.getShutdownSignalAvailablePermits(), equalTo(0));
  }

  @Test
  void whenOperatorStopped_restServerShutdown() {
    OperatorMain m = OperatorMain.createMain(buildProperties);
    BaseServerStub restServer = new BaseServerStub();
    m.getRestServer().set(restServer);
    m.completeStop();
    assertThat(restServer.isStopCalled, is(true));
    assertThat(m.getRestServer().get(), nullValue());
  }

  @Test
  @ResourceLock(value = "operatorMain")
  void startAndStopOperator() {
    assertDoesNotThrow(() -> {
      operatorMain.completeBegin();
      operatorMain.completeStop();
    });
  }

  private static class BaseServerStub extends BaseServer {
    private boolean isStopCalled = false;

    @Override
    public void start(Container container) throws UnrecoverableKeyException, CertificateException, IOException,
        NoSuchAlgorithmException, KeyStoreException, InvalidKeySpecException, KeyManagementException {
      // no-op
    }

    @Override
    public void stop() {
      isStopCalled = true;
    }

    @Override
    protected ResourceConfig createResourceConfig() {
      throw new IllegalStateException();
    }
  }

  abstract static class MainDelegateStub implements MainDelegate {
    private final FiberTestSupport testSupport;
    private final DomainNamespaces domainNamespaces;
    private final AtomicReference<V1CustomResourceDefinition> crdReference = new AtomicReference<>();
    private boolean hideCRD = false;

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
    public void addToPacket(Packet packet) {
      packet.getComponents().put(DELEGATE_COMPONENT_NAME, Component.createFor(CoreDelegate.class, this));
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
      return TEST_VERSION;
    }

    @Override
    public SemanticVersion getProductVersion() {
      return SemanticVersion.TEST_VERSION;
    }

    public void hideCRD() {
      this.hideCRD = true;
    }

    @Override
    public AtomicReference<V1CustomResourceDefinition> getCrdReference() {
      return hideCRD ? new AtomicReference<>() : crdReference;
    }

    @Override
    public File getDeploymentHome() {
      return new File("/deployment");
    }

    @Override
    public File getProbesHome() {
      return new File("/probes");
    }

    public boolean createNewFile(File file) throws IOException {
      // skip creating ready probe file
      if ("/probes/.ready".equals(file.getPath())) {
        return true;
      }
      return file.createNewFile();
    }

    @Override
    public String getPrincipal() {
      return null;
    }

    @Override
    public int getMetricsPort() {
      return 8090;
    }

    @Override
    public ScheduledFuture<?> scheduleWithFixedDelay(Runnable command, long initialDelay, long delay, TimeUnit unit) {
      return testSupport.scheduleWithFixedDelay(command, initialDelay, delay, unit);
    }
  }

  static class TestStepFactory implements OperatorMain.NextStepFactory {
    @SuppressWarnings("FieldCanBeLocal")
    private static TestStepFactory factory = new TestStepFactory();

    private static Memento install() throws NoSuchFieldException {
      factory = new TestStepFactory();
      return StaticStubSupport.install(OperatorMain.class, "nextStepFactory", factory);
    }

    @Override
    public Step createInternalInitializationStep(MainDelegate delegate, Step next) {
      return next;
    }
  }
}
