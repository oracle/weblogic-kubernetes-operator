// Copyright (c) 2019, 2022, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.helpers;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.stream.Collectors;
import javax.annotation.Nonnull;

import io.kubernetes.client.openapi.ApiException;
import io.kubernetes.client.openapi.models.CoreV1Event;
import io.kubernetes.client.openapi.models.V1ObjectMeta;
import io.kubernetes.client.openapi.models.V1OwnerReference;
import io.kubernetes.client.openapi.models.V1Service;
import io.kubernetes.client.openapi.models.V1ServicePort;
import io.kubernetes.client.openapi.models.V1ServiceSpec;
import oracle.kubernetes.operator.KubernetesConstants;
import oracle.kubernetes.operator.LabelConstants;
import oracle.kubernetes.operator.calls.UnrecoverableCallException;
import oracle.kubernetes.operator.calls.unprocessable.UnrecoverableErrorBuilderImpl;
import oracle.kubernetes.operator.tuning.TuningParametersStub;
import oracle.kubernetes.operator.utils.WlsDomainConfigSupport;
import oracle.kubernetes.operator.wlsconfig.WlsDomainConfig;
import oracle.kubernetes.operator.wlsconfig.WlsServerConfig;
import oracle.kubernetes.operator.work.Packet;
import oracle.kubernetes.operator.work.Step;
import oracle.kubernetes.operator.work.TerminalStep;
import oracle.kubernetes.utils.TestUtils;
import oracle.kubernetes.weblogic.domain.AdminServerConfigurator;
import oracle.kubernetes.weblogic.domain.DomainConfigurator;
import oracle.kubernetes.weblogic.domain.DomainConfiguratorFactory;
import oracle.kubernetes.weblogic.domain.ServiceConfigurator;
import oracle.kubernetes.weblogic.domain.model.DomainResource;
import org.hamcrest.Description;
import org.hamcrest.Matcher;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static com.meterware.simplestub.Stub.createStrictStub;
import static oracle.kubernetes.common.logging.MessageKeys.ADMIN_SERVICE_CREATED;
import static oracle.kubernetes.common.logging.MessageKeys.ADMIN_SERVICE_EXISTS;
import static oracle.kubernetes.common.logging.MessageKeys.ADMIN_SERVICE_REPLACED;
import static oracle.kubernetes.common.logging.MessageKeys.CLUSTER_SERVICE_CREATED;
import static oracle.kubernetes.common.logging.MessageKeys.CLUSTER_SERVICE_EXISTS;
import static oracle.kubernetes.common.logging.MessageKeys.CLUSTER_SERVICE_REPLACED;
import static oracle.kubernetes.common.logging.MessageKeys.EXTERNAL_CHANNEL_SERVICE_CREATED;
import static oracle.kubernetes.common.logging.MessageKeys.EXTERNAL_CHANNEL_SERVICE_EXISTS;
import static oracle.kubernetes.common.logging.MessageKeys.EXTERNAL_CHANNEL_SERVICE_REPLACED;
import static oracle.kubernetes.common.logging.MessageKeys.KUBERNETES_EVENT_ERROR;
import static oracle.kubernetes.common.logging.MessageKeys.MANAGED_SERVICE_CREATED;
import static oracle.kubernetes.common.logging.MessageKeys.MANAGED_SERVICE_EXISTS;
import static oracle.kubernetes.common.logging.MessageKeys.MANAGED_SERVICE_REPLACED;
import static oracle.kubernetes.common.utils.LogMatcher.containsFine;
import static oracle.kubernetes.common.utils.LogMatcher.containsInfo;
import static oracle.kubernetes.operator.DomainStatusMatcher.hasStatus;
import static oracle.kubernetes.operator.EventTestUtils.getEventsWithReason;
import static oracle.kubernetes.operator.EventTestUtils.getLocalizedString;
import static oracle.kubernetes.operator.KubernetesConstants.HTTP_INTERNAL_ERROR;
import static oracle.kubernetes.operator.ProcessingConstants.CLUSTER_NAME;
import static oracle.kubernetes.operator.ProcessingConstants.DOMAIN_TOPOLOGY;
import static oracle.kubernetes.operator.ProcessingConstants.SERVER_NAME;
import static oracle.kubernetes.operator.ProcessingConstants.SERVER_SCAN;
import static oracle.kubernetes.operator.helpers.EventHelper.EventItem.DOMAIN_FAILED;
import static oracle.kubernetes.operator.helpers.KubernetesTestSupport.SERVICE;
import static oracle.kubernetes.operator.helpers.ServiceHelperTest.NodePortMatcher.nodePort;
import static oracle.kubernetes.operator.helpers.ServiceHelperTest.PortMatcher.containsPort;
import static oracle.kubernetes.operator.helpers.ServiceHelperTest.ServiceNameMatcher.serviceWithName;
import static oracle.kubernetes.operator.helpers.ServiceHelperTest.UniquePortsMatcher.hasOnlyUniquePortNames;
import static oracle.kubernetes.weblogic.domain.model.DomainFailureReason.KUBERNETES;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasEntry;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.stringContainsInOrder;
import static org.hamcrest.junit.MatcherAssert.assertThat;

@SuppressWarnings("ConstantConditions")
abstract class ServiceHelperTest extends ServiceHelperTestBase {

  private static final int TEST_NODE_PORT = 30001;
  private static final int TEST_NODE_SSL_PORT = 30002;
  private static final int NAP1_NODE_PORT = 30012;
  private static final int TEST_PORT = 7000;
  private static final int ADMIN_PORT = 8000;
  private static final String TEST_SERVER = "server1";
  private static final String ADMIN_SERVER = "ADMIN_SERVER";
  private static final String[] MESSAGE_KEYS = {
    CLUSTER_SERVICE_CREATED,
    CLUSTER_SERVICE_EXISTS,
    CLUSTER_SERVICE_REPLACED,
    ADMIN_SERVICE_CREATED,
    MANAGED_SERVICE_CREATED,
    ADMIN_SERVICE_EXISTS,
    MANAGED_SERVICE_EXISTS,
    ADMIN_SERVICE_REPLACED,
    MANAGED_SERVICE_REPLACED,
    EXTERNAL_CHANNEL_SERVICE_CREATED,
    EXTERNAL_CHANNEL_SERVICE_REPLACED,
    EXTERNAL_CHANNEL_SERVICE_EXISTS
  };
  private static final String OLD_LABEL = "oldLabel";
  private static final String OLD_ANNOTATION = "annotation";
  private static final String NAP_1 = "nap1";
  private static final String NAP_2 = "Nap2";
  private static final String NAP_3 = "NAP_3";
  private static final int NAP_PORT_1 = 7100;
  private static final int NAP_PORT_2 = 37100;
  private static final int NAP_PORT_3 = 37200;
  protected static final String SIP_CLEAR = "sip-clear";
  protected static final String SIP_SECURE = "sip-secure";
  private static final int SIP_CLEAR_NAP_PORT = 8003;
  private static final int SIP_SECURE_NAP_PORT = 8004;
  public static final String STRANDED = "Stranded";
  private static final String FAILURE_MESSAGE = "Test this failure";
  private final TerminalStep terminalStep = new TerminalStep();
  public TestFacade testFacade;
  private final KubernetesTestSupport testSupport = new KubernetesTestSupport();
  private final RetryStrategyStub retryStrategy = createStrictStub(RetryStrategyStub.class);
  private final List<LogRecord> logRecords = new ArrayList<>();
  private WlsServerConfig serverConfig;
  private TestUtils.ConsoleHandlerMemento consoleHandlerMemento;

  ServiceHelperTest(TestFacade testFacade) {
    this.testFacade = testFacade;
  }

  static String getTestCluster() {
    return TEST_CLUSTER;
  }

  static int getTestNodePort() {
    return TEST_NODE_PORT;
  }

  static int getNap1NodePort() {
    return NAP1_NODE_PORT;
  }

  static String getNap1() {
    return NAP_1;
  }

  static String getNap2() {
    return NAP_2;
  }

  static String getNap3() {
    return NAP_3;
  }

  static int getNapPort2() {
    return NAP_PORT_2;
  }

  static int getNapPort3() {
    return NAP_PORT_3;
  }

  static String getNapSipClear() {
    return SIP_CLEAR;
  }

  static String getNapSipSecure() {
    return SIP_SECURE;
  }

  static int getNapPortSipClear() {
    return SIP_CLEAR_NAP_PORT;
  }

  static int getNapPortSipSecure() {
    return SIP_SECURE_NAP_PORT;
  }

  @BeforeEach
  public void setUp() throws Exception {
    configureAdminServer()
        .configureAdminService()
        .withChannel("default", TEST_NODE_PORT)
        .withChannel("default-secure", TEST_NODE_SSL_PORT)
        .withChannel(NAP_1, NAP1_NODE_PORT)
        .withChannel(NAP_2);
    mementos.add(
        consoleHandlerMemento =
            TestUtils.silenceOperatorLogger()
                .collectLogMessages(logRecords, MESSAGE_KEYS)
                .withLogLevel(Level.FINE)
                .ignoringLoggedExceptions(ApiException.class));
    mementos.add(testSupport.install());
    mementos.add(UnitTestHash.install());
    mementos.add(TuningParametersStub.install());

    WlsDomainConfigSupport configSupport = new WlsDomainConfigSupport(DOMAIN_NAME);
    configSupport
        .addWlsServer(ADMIN_SERVER, ADMIN_PORT)
        .addNetworkAccessPoint(NAP_1, NAP_PORT_1)
        .addNetworkAccessPoint(NAP_2, NAP_PORT_2);

    configSupport
        .addWlsServer(TEST_SERVER, TEST_PORT)
        .setAdminPort(ADMIN_PORT)
        .addNetworkAccessPoint(NAP_3, NAP_PORT_3)
        .addNetworkAccessPoint(SIP_CLEAR, "sip", SIP_CLEAR_NAP_PORT)
        .addNetworkAccessPoint(SIP_SECURE, "sips", SIP_SECURE_NAP_PORT);
    configSupport.addWlsCluster(TEST_CLUSTER, TEST_SERVER);
    configSupport.setAdminServerName(ADMIN_SERVER);

    WlsDomainConfig domainConfig = configSupport.createDomainConfig();
    serverConfig = domainConfig.getServerConfig(testFacade.getServerName());
    testSupport
        .addToPacket(CLUSTER_NAME, TEST_CLUSTER)
        .addToPacket(SERVER_NAME, testFacade.getServerName())
        .addToPacket(DOMAIN_TOPOLOGY, domainConfig)
        .addToPacket(SERVER_SCAN, serverConfig)
        .addDomainPresenceInfo(domainPresenceInfo);
    testFacade.configureService(domainPresenceInfo, configureDomain()).withServiceLabel(OLD_LABEL, "value");
    testFacade.configureService(domainPresenceInfo, configureDomain()).withServiceAnnotation(OLD_ANNOTATION, "value");
  }

  @AfterEach
  public void tearDown() throws Exception {
    super.tearDown();

    testSupport.throwOnCompletionFailure();
  }


  private AdminServerConfigurator configureAdminServer() {
    return configureDomain().configureAdminServer();
  }

  DomainConfigurator configureDomain() {
    return DomainConfiguratorFactory.forDomain(domainPresenceInfo.getDomain());
  }

  protected WlsServerConfig getServerConfig() {
    return serverConfig;
  }

  public void setUpServicePortPatterns() {
    configureAdminServer()
        .configureAdminService();

    WlsDomainConfigSupport configSupport = new WlsDomainConfigSupport(DOMAIN_NAME);
    configSupport
        .addWlsServer(ADMIN_SERVER, ADMIN_PORT)
        .addNetworkAccessPoint(NAP_1, NAP_PORT_1)
        .addNetworkAccessPoint(NAP_2, NAP_PORT_2);

    configSupport
        .addWlsServer(TEST_SERVER, TEST_PORT)
        .setAdminPort(ADMIN_PORT)
        .addNetworkAccessPoint(NAP_3, NAP_PORT_3);
    configSupport.addWlsCluster(TEST_CLUSTER, TEST_SERVER);
    configSupport.setAdminServerName(ADMIN_SERVER);

    WlsDomainConfig domainConfig = configSupport.createDomainConfig();
    serverConfig = domainConfig.getServerConfig(testFacade.getServerName());
    testSupport
        .addToPacket(CLUSTER_NAME, TEST_CLUSTER)
        .addToPacket(SERVER_NAME, testFacade.getServerName())
        .addToPacket(DOMAIN_TOPOLOGY, domainConfig)
        .addToPacket(SERVER_SCAN, serverConfig)
        .addDomainPresenceInfo(domainPresenceInfo);
  }

  @Test
  void whenCreated_createWithOwnerReference() {
    V1OwnerReference expectedReference = new V1OwnerReference()
        .apiVersion(KubernetesConstants.DOMAIN_GROUP + "/" + KubernetesConstants.DOMAIN_VERSION)
        .kind(KubernetesConstants.DOMAIN)
        .name(DOMAIN_NAME)
        .uid(KUBERNETES_UID)
        .controller(true);

    V1Service model = createService();
    assertThat(model.getMetadata().getOwnerReferences(), contains(expectedReference));
  }

  V1Service createService() {
    return testFacade.createServiceModel(testSupport.getPacket());
  }

  @Test
  void whenCreated_modelHasServiceType() {
    V1Service model = createService();

    assertThat(getServiceType(model), equalTo(testFacade.getExpectedServiceType()));
  }

  private String getServiceType(V1Service service) {
    return service.getSpec().getType();
  }

  @Test
  void whenCreated_modelKubernetesTypeIsCorrect() {
    V1Service model = createService();

    assertThat(OperatorServiceType.getType(model), equalTo(testFacade.getType()));
  }

  @Test
  void whenCreated_modelHasExpectedSelectors() {
    V1Service model = createService();

    assertThat(
        model.getSpec().getSelector(),
        allOf(
            hasEntry(LabelConstants.CREATEDBYOPERATOR_LABEL, "true"),
            hasEntry(LabelConstants.DOMAINUID_LABEL, UID),
            hasEntry(testFacade.getExpectedSelectorKey(), testFacade.getExpectedSelectorValue())));
  }

  @Test
  void whenCreated_modelIncludesExpectedNapPorts() {
    V1Service model = createService();

    for (Map.Entry<String, Integer> entry : testFacade.getExpectedNapPorts().entrySet()) {
      assertThat(model, containsPort(entry.getKey(), getExpectedProtocol(entry.getKey()),
          getExpectedAppProtocol(entry.getKey()), entry.getValue()));
    }
  }

  private String getExpectedProtocol(String portName) {
    return portName.startsWith("udp-") ? "UDP" : "TCP";
  }

  private String getExpectedAppProtocol(String portName) {
    if (portName.equals("udp-sip-secure") || portName.equals("default-admin") || portName.equals("sip-secure")) {
      return "tls";
    } else {
      return "tcp";
    }
  }

  @Test
  void whenCreated_modelIncludesStandardListenPorts() {
    V1Service model = createService();

    assertThat(model, containsPort("default", "tcp",
        testFacade.getExpectedListenPort()));
    assertThat(model, containsPort("default-secure", "https",
        testFacade.getExpectedSslListenPort()));
    assertThat(model, containsPort("default-admin", "tls",
        testFacade.getExpectedAdminPort()));
  }

  @Test
  void whenCreated_modelIncludesExpectedNodePorts() {
    V1Service model = createService();

    assertThat(
        getExternalPorts(model), containsInAnyOrder(toMatchers(testFacade.getExpectedNodePorts())));
  }

  private List<V1ServicePort> getExternalPorts(V1Service model) {
    return model.getSpec().getPorts().stream()
        .filter(p -> p.getNodePort() != null)
        .collect(Collectors.toList());
  }

  private List<Matcher<? super V1ServicePort>> toMatchers(Map<String, Integer> nodePorts) {
    return nodePorts.entrySet().stream()
        .map(e -> nodePort(e.getKey(), e.getValue()))
        .collect(Collectors.toList());
  }

  @Test
  void onRunWithNoService_logCreatedMessage() {
    runServiceHelper();

    assertThat(logRecords, containsInfo(testFacade.getServiceCreateLogMessage()));
  }

  private void runServiceHelper() {
    testSupport.runSteps(testFacade.createSteps(terminalStep));
  }

  @Test
  void onRunWithNoService_createIt() {
    consoleHandlerMemento.ignoreMessage(testFacade.getServiceCreateLogMessage());

    runServiceHelper();

    assertThat(
        testFacade.getRecordedService(domainPresenceInfo),
        is(serviceWithName(testFacade.getServiceName())));
  }

  @Test
  void verifyPortNamesAreNormalizedWithAppProtocolSet() {
    consoleHandlerMemento.ignoreMessage(testFacade.getServiceCreateLogMessage());
    setUpServicePortPatterns();
    List<String> portNames = new ArrayList<>(Arrays.asList("nap-1", "nap-2", "nap-3", "default"));

    runServiceHelper();
    List<V1ServicePort> ports = testFacade.getRecordedService(domainPresenceInfo).getSpec().getPorts();
    for (V1ServicePort port: ports) {
      assertThat(port.getAppProtocol(), notNullValue());
      if (port.getName().equals("default-admin")) {
        assertThat(port.getAppProtocol(), equalTo("tls"));
      } else if (portNames.contains(port.getName())) {
        assertThat(port.getAppProtocol(), equalTo("tcp"));
      }
    }
  }

  @Test
  void testGetAppProtocol() {
    assertThat(ServiceHelper.getAppProtocol("unknown"), equalTo("tcp"));
    assertThat(ServiceHelper.getAppProtocol("http"), equalTo("http"));
    assertThat(ServiceHelper.getAppProtocol("https"), equalTo("https"));
    assertThat(ServiceHelper.getAppProtocol("t3s"), equalTo("tls"));
    assertThat(ServiceHelper.getAppProtocol("ldaps"), equalTo("tls"));
    assertThat(ServiceHelper.getAppProtocol("iiops"), equalTo("tls"));
    assertThat(ServiceHelper.getAppProtocol("cbts"), equalTo("tls"));
    assertThat(ServiceHelper.getAppProtocol("sips"), equalTo("tls"));
    assertThat(ServiceHelper.getAppProtocol("admin"), equalTo("tls"));
  }

  @Test
  void afterRun_createdServiceHasNoDuplicatePorts() {
    consoleHandlerMemento.ignoreMessage(testFacade.getServiceCreateLogMessage());

    runServiceHelper();

    assertThat(testFacade.getRecordedService(domainPresenceInfo), hasOnlyUniquePortNames());
  }

  @Test
  void onFailedRun_reportFailure() {
    testSupport.addRetryStrategy(retryStrategy);
    testSupport.failOnCreate(SERVICE, NS, HTTP_INTERNAL_ERROR);

    runServiceHelper();

    testSupport.verifyCompletionThrowable(UnrecoverableCallException.class);
  }

  @Test
  void whenServiceCreationFailsDueToUnprocessableEntityFailure_reportInDomainStatus() {
    testSupport.defineResources(domainPresenceInfo.getDomain());
    testSupport.failOnCreate(SERVICE, NS, new UnrecoverableErrorBuilderImpl()
        .withReason("FieldValueNotFound")
        .withMessage(FAILURE_MESSAGE)
        .build());

    runServiceHelper();

    assertThat(getDomain(), hasStatus().withReason(KUBERNETES)
        .withMessageContaining("create", "service", NS, FAILURE_MESSAGE));
  }

  @Test
  void whenServiceCreationFailsDueToUnprocessableEntityFailure_createFailedEventWithKubernetesReason() {
    testSupport.defineResources(domainPresenceInfo.getDomain());
    testSupport.failOnCreate(SERVICE, NS, new UnrecoverableErrorBuilderImpl()
        .withReason("FieldValueNotFound")
        .withMessage(FAILURE_MESSAGE)
        .build());

    runServiceHelper();

    assertThat(
        "Expected Event " + DOMAIN_FAILED + " expected with message not found",
        getExpectedEventMessage(DOMAIN_FAILED),
        stringContainsInOrder("Domain", UID, "failed due to",
           getLocalizedString(KUBERNETES_EVENT_ERROR)));
  }

  protected String getExpectedEventMessage(EventHelper.EventItem event) {
    List<CoreV1Event> events = getEventsWithReason(getEvents(), event.getReason());
    //System.out.println(events);
    return Optional.ofNullable(events)
        .filter(list -> list.size() != 0)
        .map(n -> n.get(0))
        .map(CoreV1Event::getMessage)
        .orElse("Event not found");
  }

  private List<CoreV1Event> getEvents() {
    return testSupport.getResources(KubernetesTestSupport.EVENT);
  }

  @Test
  void whenServiceCreationFailsDueToUnprocessableEntityFailure_abortFiber() {
    testSupport.defineResources(domainPresenceInfo.getDomain());
    testSupport.failOnCreate(SERVICE, NS, new UnrecoverableErrorBuilderImpl()
        .withReason("FieldValueNotFound")
        .withMessage(FAILURE_MESSAGE)
        .build());

    runServiceHelper();

    assertThat(terminalStep.wasRun(), is(false));
  }

  private DomainResource getDomain() {
    return (DomainResource) testSupport.getResources(KubernetesTestSupport.DOMAIN).get(0);
  }

  @Test
  void whenMatchingServiceRecordedInDomainPresence_logServiceExists() {
    V1Service originalService = createService();
    testFacade.recordService(domainPresenceInfo, originalService);

    runServiceHelper();

    assertThat(logRecords, containsFine(testFacade.getServiceExistsLogMessage()));
  }

  @Test
  void whenConfiguredLabelAdded_replaceService() {
    verifyServiceReplaced(this::configureNewLabel);
  }

  @Test
  void whenConfiguredLabelChanged_replaceService() {
    verifyServiceReplaced(this::changeConfiguredLabel);
  }

  @Test
  void whenConfiguredAnnotationAdded_replaceService() {
    verifyServiceReplaced(this::configureNewAnnotation);
  }

  @Test
  void whenConfiguredAnnotationChanged_replaceService() {
    verifyServiceReplaced(this::changeConfiguredAnnotation);
  }

  @Test
  void whenConfiguredListenPortChanged_replaceService() {
    verifyServiceReplaced(this::changeConfiguredListenPort);
  }

  @Test
  void whenConfiguredSslListenPortChanged_replaceService() {
    verifyServiceReplaced(this::changeConfiguredSslListenPort);
  }

  private void verifyServiceReplaced(Runnable configurationMutator) {
    recordInitialService();
    if (testFacade instanceof ExternalServiceHelperTest.ExternalServiceTestFacade) {
      recordStrandedService();
    }
    configurationMutator.run();

    runServiceHelper();

    assertThat(logRecords, containsInfo(testFacade.getServiceReplacedLogMessage()));
    assertThat(getStrandedService(), empty());
  }

  private List<Object> getStrandedService() {
    List<V1Service> svcList = testSupport.getResources(SERVICE);
    return svcList.stream().filter(s -> s.getMetadata().getName().equals(STRANDED)).collect(Collectors.toList());
  }

  private void configureNewLabel() {
    testFacade.configureService(domainPresenceInfo, configureDomain()).withServiceLabel("newLabel", "value");
  }

  private void changeConfiguredLabel() {
    testFacade.configureService(domainPresenceInfo, configureDomain()).withServiceLabel(OLD_LABEL, "newValue");
  }

  private void configureNewAnnotation() {
    testFacade.configureService(domainPresenceInfo, configureDomain()).withServiceAnnotation("newAnnotation", "value");
  }

  private void changeConfiguredAnnotation() {
    testFacade.configureService(domainPresenceInfo, configureDomain()).withServiceLabel(OLD_ANNOTATION, "newValue");
  }

  private void changeConfiguredListenPort() {
    serverConfig.setListenPort(9900);
  }

  private void changeConfiguredSslListenPort() {
    serverConfig.setSslListenPort(9901);
  }

  private void recordInitialService() {
    V1Service originalService = createService();
    testSupport.defineResources(originalService);
    testFacade.recordService(domainPresenceInfo, originalService);
  }

  private void recordStrandedService() {
    Map<String, String> labels = new HashMap<>();
    labels.put(LabelConstants.DOMAINUID_LABEL, UID);
    labels.put(LabelConstants.CREATEDBYOPERATOR_LABEL, "true");
    V1Service strandedService = new V1Service().metadata(new V1ObjectMeta().name(STRANDED).namespace(NS)
            .labels(labels)).spec(new V1ServiceSpec().type("NodePort"));
    testSupport.defineResources(strandedService);
    testFacade.recordService(domainPresenceInfo, strandedService);
  }

  @Test
  void whenServiceLabelAdded_dontReplaceService() {
    verifyServiceNotReplaced(this::addNewLabel);
  }

  @Test
  void whenServiceLabelChanged_dontReplaceService() {
    verifyServiceNotReplaced(this::changeLabel);
  }

  @Test
  void whenServiceAnnotationAdded_dontReplaceService() {
    verifyServiceNotReplaced(this::addNewAnnotation);
  }

  @Test
  void whenServiceAnnotationChanged_dontReplaceService() {
    verifyServiceNotReplaced(this::changeAnnotation);
  }

  @Test
  void whenServiceListenPortChanged_dontReplaceService() {
    verifyServiceNotReplaced(this::changeListenPort);
  }

  private void verifyServiceNotReplaced(Consumer<V1Service> serviceMutator) {
    runServiceHelper();
    logRecords.clear();
    serviceMutator.accept(getCreatedService());

    runServiceHelper();

    assertThat(logRecords, containsFine(testFacade.getServiceExistsLogMessage()));
  }

  private void addNewLabel(V1Service service) {
    service.getMetadata().putLabelsItem("newLabel", "value");
  }

  private void changeLabel(V1Service service) {
    service.getMetadata().putLabelsItem(OLD_LABEL, "newValue");
  }

  private void addNewAnnotation(V1Service service) {
    service.getMetadata().putAnnotationsItem("newAnnotation", "value");
  }

  private void changeAnnotation(V1Service service) {
    service.getMetadata().putLabelsItem(OLD_ANNOTATION, "newValue");
  }

  private void changeListenPort(V1Service service) {
    getListenPort(service).setPort(6666);
  }

  private V1ServicePort getListenPort(V1Service service) {
    return service.getSpec().getPorts().stream().filter(this::isListenPort).findAny().orElse(null);
  }

  private boolean isListenPort(V1ServicePort servicePort) {
    return servicePort.getName().equals("default");
  }

  private V1Service getCreatedService() {
    return getCreatedServices().get(0);
  }

  private List<V1Service> getCreatedServices() {
    return testSupport.getResources(SERVICE);
  }

  abstract static class TestFacade {
    private final Map<String, Integer> expectedNapPorts = new HashMap<>();
    private final Map<String, Integer> expectedNodePorts = new HashMap<>();

    abstract OperatorServiceType getType();

    abstract String getServiceCreateLogMessage();

    abstract String getServiceExistsLogMessage();

    abstract String getServiceReplacedLogMessage();

    abstract String getServerName();

    abstract String getServiceName();

    abstract Step createSteps(Step next);

    abstract V1Service createServiceModel(Packet packet);

    abstract V1Service getRecordedService(DomainPresenceInfo info);

    abstract void recordService(DomainPresenceInfo info, V1Service service);

    abstract Integer getExpectedListenPort();

    String getExpectedServiceType() {
      return "ClusterIP";
    }

    Integer getExpectedSslListenPort() {
      return null;
    }

    Integer getExpectedAdminPort() {
      return null;
    }

    final int getTestPort() {
      return TEST_PORT;
    }

    final int getAdminPort() {
      return ADMIN_PORT;
    }

    final String getAdminServerName() {
      return ADMIN_SERVER;
    }

    final String getManagedServerName() {
      return TEST_SERVER;
    }

    Map<String, Integer> getExpectedNodePorts() {
      return expectedNodePorts;
    }

    Map<String, Integer> getExpectedNapPorts() {
      return expectedNapPorts;
    }

    abstract ServiceConfigurator configureService(DomainPresenceInfo info, DomainConfigurator configurator);

    String getExpectedSelectorKey() {
      return LabelConstants.SERVERNAME_LABEL;
    }

    abstract String getExpectedSelectorValue();
  }


  abstract static class ServerTestFacade extends TestFacade {

    @Override
    OperatorServiceType getType() {
      return OperatorServiceType.SERVER;
    }

    @Override
    public String getServiceName() {
      return LegalNames.toServerServiceName(UID, getServerName());
    }

    @Override
    public Step createSteps(Step next) {
      return ServiceHelper.createForServerStep(next);
    }

    @Override
    public V1Service createServiceModel(Packet packet) {
      return ServiceHelper.createServerServiceModel(packet);
    }

    @Override
    public V1Service getRecordedService(DomainPresenceInfo info) {
      return info.getServerService(getServerName());
    }

    @Override
    public void recordService(DomainPresenceInfo info, V1Service service) {
      info.setServerService(getServerName(), service);
    }

    @Override
    public ServiceConfigurator configureService(DomainPresenceInfo info, DomainConfigurator configurator) {
      return configurator.configureServer(getServerName());
    }

    @Override
    String getExpectedSelectorValue() {
      return getServerName();
    }
  }


  @SuppressWarnings("unused")
  static class ServiceNameMatcher
      extends org.hamcrest.TypeSafeDiagnosingMatcher<io.kubernetes.client.openapi.models.V1Service> {
    private final String expectedName;

    private ServiceNameMatcher(String expectedName) {
      this.expectedName = expectedName;
    }

    static ServiceNameMatcher serviceWithName(String expectedName) {
      return new ServiceNameMatcher(expectedName);
    }

    @Override
    protected boolean matchesSafely(V1Service item, Description mismatchDescription) {
      if (expectedName.equals(getName(item))) {
        return true;
      }

      mismatchDescription.appendText("service with name ").appendValue(getName(item));
      return false;
    }

    private String getName(V1Service item) {
      return item.getMetadata().getName();
    }

    @Override
    public void describeTo(Description description) {
      description.appendText("service with name ").appendValue(expectedName);
    }
  }

  @SuppressWarnings("unused")
  static class PortMatcher
      extends org.hamcrest.TypeSafeDiagnosingMatcher<io.kubernetes.client.openapi.models.V1Service> {
    private final String expectedName;
    private final String expectedProtocol;
    private final String expectedAppProtocol;
    private final Integer expectedValue;

    private PortMatcher(@Nonnull String expectedName, Integer expectedValue) {
      this(expectedName, "TCP", expectedValue);
    }

    private PortMatcher(@Nonnull String expectedName, String expectedAppProtocol, Integer expectedValue) {
      this(expectedName, "TCP", expectedAppProtocol, expectedValue);
    }

    private PortMatcher(@Nonnull String expectedName, String expectedProtocol,
                        String expectedAppProtocol, Integer expectedValue) {
      this.expectedName = expectedName;
      this.expectedProtocol = expectedProtocol;
      this.expectedAppProtocol = expectedAppProtocol;
      this.expectedValue = expectedValue;
    }

    static PortMatcher containsPort(@Nonnull String expectedName, Integer expectedValue) {
      return new PortMatcher(expectedName, expectedValue);
    }

    static PortMatcher containsPort(@Nonnull String expectedName,
                                    @Nonnull String expectedProtocol,
                                    @Nonnull String expectedAppProtocol, Integer expectedValue) {
      return new PortMatcher(expectedName, expectedProtocol, expectedAppProtocol, expectedValue);
    }

    static PortMatcher containsPort(@Nonnull String expectedName,
                                    @Nonnull String expectedAppProtocol, Integer expectedValue) {
      return new PortMatcher(expectedName, expectedAppProtocol, expectedValue);
    }

    @Override
    protected boolean matchesSafely(V1Service item, Description mismatchDescription) {
      V1ServicePort matchingPort = getPortWithName(item);

      if (matchingPort == null) {
        if (expectedValue == null) {
          return true;
        }
        mismatchDescription.appendText("contains no port with name ").appendValue(expectedName);
      } else {
        if (matchesSelectedProtocol(matchingPort)
            && matchesSelectedPort(matchingPort)
            && matchesSelectedAppProtocol(matchingPort)) {
          return true;
        }
        mismatchDescription.appendText("contains port ").appendValue(matchingPort);
      }
      return false;
    }

    private boolean matchesSelectedProtocol(V1ServicePort matchingPort) {
      return Objects.equals(expectedProtocol, matchingPort.getProtocol());
    }

    private boolean matchesSelectedPort(V1ServicePort matchingPort) {
      return Objects.equals(expectedValue, matchingPort.getPort());
    }

    private boolean matchesSelectedAppProtocol(V1ServicePort matchingPort) {
      return Objects.equals(expectedAppProtocol, matchingPort.getAppProtocol());
    }

    private V1ServicePort getPortWithName(V1Service item) {
      return item.getSpec().getPorts().stream().filter(this::hasName).findFirst().orElse(null);
    }

    private boolean hasName(V1ServicePort p) {
      return expectedName.equals(p.getName());
    }

    @Override
    public void describeTo(Description description) {
      if (expectedValue == null) {
        description.appendText("service with no port named ").appendValue(expectedName);
      } else {
        description
            .appendText("service with TCP port with name ")
            .appendValue(expectedName)
            .appendText(", number ")
            .appendValue(expectedValue);
      }
    }
  }

  @SuppressWarnings("unused")
  static class NodePortMatcher
      extends org.hamcrest.TypeSafeDiagnosingMatcher<io.kubernetes.client.openapi.models.V1ServicePort> {
    private final String name;
    private final int nodePort;

    private NodePortMatcher(String name, int nodePort) {
      this.name = name;
      this.nodePort = nodePort;
    }

    static NodePortMatcher nodePort(String name, int nodePort) {
      return new NodePortMatcher(name, nodePort);
    }

    private static void describe(Description description, String name, Integer nodePort) {
      description
          .appendText("service port with name ")
          .appendValue(name)
          .appendText(" and node port ")
          .appendValue(nodePort);
    }

    @Override
    protected boolean matchesSafely(V1ServicePort item, Description mismatchDescription) {
      if (name.equals(item.getName()) && nodePort == item.getNodePort()) {
        return true;
      }

      describe(mismatchDescription, item.getName(), item.getNodePort());
      return false;
    }

    @Override
    public void describeTo(Description description) {
      describe(description, name, nodePort);
    }
  }

  @SuppressWarnings("unused")
  static class UniquePortsMatcher
      extends org.hamcrest.TypeSafeDiagnosingMatcher<io.kubernetes.client.openapi.models.V1Service> {
    static UniquePortsMatcher hasOnlyUniquePortNames() {
      return new UniquePortsMatcher();
    }

    @Override
    protected boolean matchesSafely(V1Service item, Description mismatchDescription) {
      Set<String> duplicates = getDuplicatePortNames(item);
      if (duplicates.isEmpty()) {
        return true;
      }

      mismatchDescription.appendValueList("found duplicate ports for names: ", ",", "", duplicates);
      return false;
    }

    private Set<String> getDuplicatePortNames(V1Service item) {
      Set<String> uniqueNames = new HashSet<>();
      Set<String> duplicates = new HashSet<>();
      for (V1ServicePort port : item.getSpec().getPorts()) {
        if (!uniqueNames.add(port.getName())) {
          duplicates.add(port.getName());
        }
      }
      return duplicates;
    }

    @Override
    public void describeTo(Description description) {
      description.appendText("ports with all unique names");
    }
  }
}

// todo: external with no admin server (avoid NPE)
// todo: external with empty naps   (avoid NPE)
// todo: external with no channels  (don't create service)
