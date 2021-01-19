// Copyright (c) 2019, 2021, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.helpers;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.stream.Collectors;
import javax.annotation.Nonnull;

import io.kubernetes.client.openapi.ApiException;
import io.kubernetes.client.openapi.models.V1OwnerReference;
import io.kubernetes.client.openapi.models.V1Service;
import io.kubernetes.client.openapi.models.V1ServicePort;
import oracle.kubernetes.operator.KubernetesConstants;
import oracle.kubernetes.operator.LabelConstants;
import oracle.kubernetes.operator.calls.FailureStatusSourceException;
import oracle.kubernetes.operator.calls.unprocessable.UnrecoverableErrorBuilderImpl;
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
import oracle.kubernetes.weblogic.domain.model.Domain;
import org.hamcrest.Description;
import org.hamcrest.Matcher;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static com.meterware.simplestub.Stub.createStrictStub;
import static oracle.kubernetes.operator.ProcessingConstants.CLUSTER_NAME;
import static oracle.kubernetes.operator.ProcessingConstants.DOMAIN_TOPOLOGY;
import static oracle.kubernetes.operator.ProcessingConstants.SERVER_NAME;
import static oracle.kubernetes.operator.ProcessingConstants.SERVER_SCAN;
import static oracle.kubernetes.operator.helpers.DomainStatusMatcher.hasStatus;
import static oracle.kubernetes.operator.helpers.KubernetesTestSupport.SERVICE;
import static oracle.kubernetes.operator.helpers.ServiceHelperTest.NodePortMatcher.nodePort;
import static oracle.kubernetes.operator.helpers.ServiceHelperTest.PortMatcher.containsPort;
import static oracle.kubernetes.operator.helpers.ServiceHelperTest.ServiceNameMatcher.serviceWithName;
import static oracle.kubernetes.operator.helpers.ServiceHelperTest.UniquePortsMatcher.hasOnlyUniquePortNames;
import static oracle.kubernetes.operator.logging.MessageKeys.ADMIN_SERVICE_CREATED;
import static oracle.kubernetes.operator.logging.MessageKeys.ADMIN_SERVICE_EXISTS;
import static oracle.kubernetes.operator.logging.MessageKeys.ADMIN_SERVICE_REPLACED;
import static oracle.kubernetes.operator.logging.MessageKeys.CLUSTER_SERVICE_CREATED;
import static oracle.kubernetes.operator.logging.MessageKeys.CLUSTER_SERVICE_EXISTS;
import static oracle.kubernetes.operator.logging.MessageKeys.CLUSTER_SERVICE_REPLACED;
import static oracle.kubernetes.operator.logging.MessageKeys.EXTERNAL_CHANNEL_SERVICE_CREATED;
import static oracle.kubernetes.operator.logging.MessageKeys.EXTERNAL_CHANNEL_SERVICE_EXISTS;
import static oracle.kubernetes.operator.logging.MessageKeys.EXTERNAL_CHANNEL_SERVICE_REPLACED;
import static oracle.kubernetes.operator.logging.MessageKeys.MANAGED_SERVICE_CREATED;
import static oracle.kubernetes.operator.logging.MessageKeys.MANAGED_SERVICE_EXISTS;
import static oracle.kubernetes.operator.logging.MessageKeys.MANAGED_SERVICE_REPLACED;
import static oracle.kubernetes.utils.LogMatcher.containsFine;
import static oracle.kubernetes.utils.LogMatcher.containsInfo;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasEntry;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.junit.MatcherAssert.assertThat;

@SuppressWarnings("ConstantConditions")
abstract class ServiceHelperTest extends ServiceHelperTestBase {

  private static final String TEST_CLUSTER = "cluster-1";
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
    testFacade.configureService(configureDomain()).withServiceLabel(OLD_LABEL, "value");
    testFacade.configureService(configureDomain()).withServiceAnnotation(OLD_ANNOTATION, "value");
  }

  @AfterEach
  public void tearDown() throws Exception {
    super.tearDown();

    testSupport.throwOnCompletionFailure();
  }

  private AdminServerConfigurator configureAdminServer() {
    return configureDomain().configureAdminServer();
  }

  private DomainConfigurator configureDomain() {
    return DomainConfiguratorFactory.forDomain(domainPresenceInfo.getDomain());
  }

  @Test
  public void whenCreated_createWithOwnerReference() {
    V1OwnerReference expectedReference = new V1OwnerReference()
        .apiVersion(KubernetesConstants.DOMAIN_GROUP + "/" + KubernetesConstants.DOMAIN_VERSION)
        .kind(KubernetesConstants.DOMAIN)
        .name(DOMAIN_NAME)
        .uid(KUBERNETES_UID)
        .controller(true);

    V1Service model = testFacade.createServiceModel(testSupport.getPacket());
    assertThat(model.getMetadata().getOwnerReferences(), contains(expectedReference));
  }

  @Test
  public void whenCreated_modelHasServiceType() {
    V1Service model = testFacade.createServiceModel(testSupport.getPacket());

    assertThat(getServiceType(model), equalTo(testFacade.getExpectedServiceType().toString()));
  }

  private String getServiceType(V1Service service) {
    return service.getSpec().getType();
  }

  @Test
  public void whenCreated_modelKubernetesTypeIsCorrect() {
    V1Service model = testFacade.createServiceModel(testSupport.getPacket());

    assertThat(OperatorServiceType.getType(model), equalTo(testFacade.getType()));
  }

  @Test
  public void whenCreated_modelHasExpectedSelectors() {
    V1Service model = testFacade.createServiceModel(testSupport.getPacket());

    assertThat(
        model.getSpec().getSelector(),
        allOf(
            hasEntry(LabelConstants.CREATEDBYOPERATOR_LABEL, "true"),
            hasEntry(LabelConstants.DOMAINUID_LABEL, UID),
            hasEntry(testFacade.getExpectedSelectorKey(), testFacade.getExpectedSelectorValue())));
  }

  @Test
  public void whenCreated_modelIncludesExpectedNapPorts() {
    V1Service model = testFacade.createServiceModel(testSupport.getPacket());

    for (Map.Entry<String, Integer> entry : testFacade.getExpectedNapPorts().entrySet()) {
      assertThat(model, containsPort(entry.getKey(), entry.getValue()));
    }
  }

  @Test
  public void whenCreated_modelIncludesStandardListenPorts() {
    V1Service model = testFacade.createServiceModel(testSupport.getPacket());

    assertThat(model, containsPort("default", testFacade.getExpectedListenPort()));
    assertThat(model, containsPort("default-secure", testFacade.getExpectedSslListenPort()));
    assertThat(model, containsPort("default-admin", testFacade.getExpectedAdminPort()));
  }

  @Test
  public void whenCreated_modelIncludesExpectedNodePorts() {
    V1Service model = testFacade.createServiceModel(testSupport.getPacket());

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
  public void onRunWithNoService_logCreatedMessage() {
    runServiceHelper();

    assertThat(logRecords, containsInfo(testFacade.getServiceCreateLogMessage()));
  }

  private void runServiceHelper() {
    testSupport.runSteps(testFacade.createSteps(terminalStep));
  }

  @Test
  public void onRunWithNoService_createIt() {
    consoleHandlerMemento.ignoreMessage(testFacade.getServiceCreateLogMessage());

    runServiceHelper();

    assertThat(
        testFacade.getRecordedService(domainPresenceInfo),
        is(serviceWithName(testFacade.getServiceName())));
  }

  @Test
  public void afterRun_createdServiceHasNoDuplicatePorts() {
    consoleHandlerMemento.ignoreMessage(testFacade.getServiceCreateLogMessage());

    runServiceHelper();

    assertThat(testFacade.getRecordedService(domainPresenceInfo), hasOnlyUniquePortNames());
  }

  @Test
  public void onFailedRun_reportFailure() {
    testSupport.addRetryStrategy(retryStrategy);
    testSupport.failOnResource(SERVICE, testFacade.getServiceName(), NS, 500);

    runServiceHelper();

    testSupport.verifyCompletionThrowable(FailureStatusSourceException.class);
  }

  @Test
  public void whenServiceCreationFailsDueToUnprocessableEntityFailure_reportInDomainStatus() {
    testSupport.defineResources(domainPresenceInfo.getDomain());
    testSupport.failOnResource(SERVICE, testFacade.getServiceName(), NS, new UnrecoverableErrorBuilderImpl()
        .withReason("FieldValueNotFound")
        .withMessage("Test this failure")
        .build());

    runServiceHelper();

    assertThat(getDomain(), hasStatus("FieldValueNotFound",
        "testcall in namespace junit, for testName: Test this failure"));
  }

  @Test
  public void whenServiceCreationFailsDueToUnprocessableEntityFailure_abortFiber() {
    testSupport.defineResources(domainPresenceInfo.getDomain());
    testSupport.failOnResource(SERVICE, testFacade.getServiceName(), NS, new UnrecoverableErrorBuilderImpl()
        .withReason("FieldValueNotFound")
        .withMessage("Test this failure")
        .build());

    runServiceHelper();

    assertThat(terminalStep.wasRun(), is(false));
  }

  private Domain getDomain() {
    return (Domain) testSupport.getResources(KubernetesTestSupport.DOMAIN).get(0);
  }

  @Test
  public void whenMatchingServiceRecordedInDomainPresence_logServiceExists() {
    V1Service originalService = testFacade.createServiceModel(testSupport.getPacket());
    testFacade.recordService(domainPresenceInfo, originalService);

    runServiceHelper();

    assertThat(logRecords, containsFine(testFacade.getServiceExistsLogMessage()));
  }

  @Test
  public void whenConfiguredLabelAdded_replaceService() {
    verifyServiceReplaced(this::configureNewLabel);
  }

  @Test
  public void whenConfiguredLabelChanged_replaceService() {
    verifyServiceReplaced(this::changeConfiguredLabel);
  }

  @Test
  public void whenConfiguredAnnotationAdded_replaceService() {
    verifyServiceReplaced(this::configureNewAnnotation);
  }

  @Test
  public void whenConfiguredAnnotationChanged_replaceService() {
    verifyServiceReplaced(this::changeConfiguredAnnotation);
  }

  @Test
  public void whenConfiguredListenPortChanged_replaceService() {
    verifyServiceReplaced(this::changeConfiguredListenPort);
  }

  @Test
  public void whenConfiguredSslListenPortChanged_replaceService() {
    verifyServiceReplaced(this::changeConfiguredSslListenPort);
  }

  private void verifyServiceReplaced(Runnable configurationMutator) {
    recordInitialService();
    configurationMutator.run();

    runServiceHelper();

    assertThat(logRecords, containsInfo(testFacade.getServiceReplacedLogMessage()));
  }

  private void configureNewLabel() {
    testFacade.configureService(configureDomain()).withServiceLabel("newLabel", "value");
  }

  private void changeConfiguredLabel() {
    testFacade.configureService(configureDomain()).withServiceLabel(OLD_LABEL, "newValue");
  }

  private void configureNewAnnotation() {
    testFacade.configureService(configureDomain()).withServiceAnnotation("newAnnotation", "value");
  }

  private void changeConfiguredAnnotation() {
    testFacade.configureService(configureDomain()).withServiceLabel(OLD_ANNOTATION, "newValue");
  }

  private void changeConfiguredListenPort() {
    serverConfig.setListenPort(9900);
  }

  private void changeConfiguredSslListenPort() {
    serverConfig.setSslListenPort(9901);
  }

  private void recordInitialService() {
    V1Service originalService = testFacade.createServiceModel(testSupport.getPacket());
    testSupport.defineResources(originalService);
    testFacade.recordService(domainPresenceInfo, originalService);
  }

  @Test
  public void whenServiceLabelAdded_dontReplaceService() {
    verifyServiceNotReplaced(this::addNewLabel);
  }

  @Test
  public void whenServiceLabelChanged_dontReplaceService() {
    verifyServiceNotReplaced(this::changeLabel);
  }

  @Test
  public void whenServiceAnnotationAdded_dontReplaceService() {
    verifyServiceNotReplaced(this::addNewAnnotation);
  }

  @Test
  public void whenServiceAnnotationChanged_dontReplaceService() {
    verifyServiceNotReplaced(this::changeAnnotation);
  }

  @Test
  public void whenServiceListenPortChanged_dontReplaceService() {
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

  enum ServiceType {
    ClusterIP,
    NodePort
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

    ServiceType getExpectedServiceType() {
      return ServiceType.ClusterIP;
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

    abstract ServiceConfigurator configureService(DomainConfigurator configurator);

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
    public ServiceConfigurator configureService(DomainConfigurator configurator) {
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
    private final Integer expectedValue;

    private PortMatcher(@Nonnull String expectedName, Integer expectedValue) {
      this.expectedName = expectedName;
      this.expectedValue = expectedValue;
    }

    static PortMatcher containsPort(@Nonnull String expectedName, Integer expectedValue) {
      return new PortMatcher(expectedName, expectedValue);
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
        if (matchSelectedPort(matchingPort)) {
          return true;
        }
        mismatchDescription.appendText("contains port ").appendValue(matchingPort);
      }
      return false;
    }

    private boolean matchSelectedPort(V1ServicePort matchingPort) {
      return "TCP".equals(matchingPort.getProtocol())
          && Objects.equals(expectedValue, matchingPort.getPort());
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
