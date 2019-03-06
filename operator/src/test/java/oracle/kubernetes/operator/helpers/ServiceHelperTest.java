// Copyright 2019 Oracle Corporation and/or its affiliates.  All rights reserved.
// Licensed under the Universal Permissive License v 1.0 as shown at
// http://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.helpers;

import static com.meterware.simplestub.Stub.createStrictStub;
import static oracle.kubernetes.LogMatcher.containsFine;
import static oracle.kubernetes.LogMatcher.containsInfo;
import static oracle.kubernetes.operator.ProcessingConstants.CLUSTER_NAME;
import static oracle.kubernetes.operator.ProcessingConstants.DOMAIN_TOPOLOGY;
import static oracle.kubernetes.operator.ProcessingConstants.SERVER_NAME;
import static oracle.kubernetes.operator.ProcessingConstants.SERVER_SCAN;
import static oracle.kubernetes.operator.helpers.ServiceHelperTest.PortMatcher.containsPort;
import static oracle.kubernetes.operator.helpers.ServiceHelperTest.ServiceNameMatcher.serviceWithName;
import static oracle.kubernetes.operator.logging.MessageKeys.ADMIN_SERVICE_CREATED;
import static oracle.kubernetes.operator.logging.MessageKeys.ADMIN_SERVICE_EXISTS;
import static oracle.kubernetes.operator.logging.MessageKeys.ADMIN_SERVICE_REPLACED;
import static oracle.kubernetes.operator.logging.MessageKeys.CLUSTER_SERVICE_CREATED;
import static oracle.kubernetes.operator.logging.MessageKeys.CLUSTER_SERVICE_EXISTS;
import static oracle.kubernetes.operator.logging.MessageKeys.CLUSTER_SERVICE_REPLACED;
import static oracle.kubernetes.operator.logging.MessageKeys.MANAGED_SERVICE_CREATED;
import static oracle.kubernetes.operator.logging.MessageKeys.MANAGED_SERVICE_EXISTS;
import static oracle.kubernetes.operator.logging.MessageKeys.MANAGED_SERVICE_REPLACED;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.junit.MatcherAssert.assertThat;

import com.meterware.simplestub.Stub;
import io.kubernetes.client.ApiException;
import io.kubernetes.client.models.V1Service;
import io.kubernetes.client.models.V1ServicePort;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import javax.annotation.Nonnull;
import oracle.kubernetes.TestUtils;
import oracle.kubernetes.operator.utils.WlsDomainConfigSupport;
import oracle.kubernetes.operator.wlsconfig.WlsDomainConfig;
import oracle.kubernetes.operator.wlsconfig.WlsServerConfig;
import oracle.kubernetes.operator.work.Packet;
import oracle.kubernetes.operator.work.Step;
import oracle.kubernetes.operator.work.TerminalStep;
import oracle.kubernetes.weblogic.domain.AdminServerConfigurator;
import oracle.kubernetes.weblogic.domain.DomainConfigurator;
import oracle.kubernetes.weblogic.domain.DomainConfiguratorFactory;
import oracle.kubernetes.weblogic.domain.ServiceConfigurator;
import org.hamcrest.Description;
import org.junit.After;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class ServiceHelperTest extends ServiceHelperTestBase {

  private static final String TEST_CLUSTER = "cluster-1";
  private static final int TEST_NODE_PORT = 7002;
  private static final int TEST_PORT = 7000;
  private static final int ADMIN_PORT = 8000;
  private static final String DOMAIN_NAME = "domain1";
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
    MANAGED_SERVICE_REPLACED
  };
  private static final String OLD_LABEL = "oldLabel";
  private static final String OLD_ANNOTATION = "annotation";
  private static final ClusterServiceTestFacade CLUSTER_SERVICE_TEST_FACADE =
      new ClusterServiceTestFacade();
  private static final ManagedServerTestFacade MANAGED_SERVER_TEST_FACADE =
      new ManagedServerTestFacade();
  private static final AdminServerTestFacade ADMIN_SERVER_TEST_FACADE = new AdminServerTestFacade();
  private static final ExternalServiceTestFacade EXTERNAL_SERVICE_TEST_FACADE =
      new ExternalServiceTestFacade();

  private KubernetesTestSupport testSupport = new KubernetesTestSupport();
  private final TerminalStep terminalStep = new TerminalStep();
  private RetryStrategyStub retryStrategy = createStrictStub(RetryStrategyStub.class);
  private List<LogRecord> logRecords = new ArrayList<>();
  private WlsDomainConfig domainConfig;
  private WlsServerConfig serverConfig;

  @Before
  public void setUp() throws Exception {
    configureAdminServer().configureAdminService().withChannel("default", TEST_NODE_PORT);
    mementos.add(
        TestUtils.silenceOperatorLogger()
            .collectLogMessages(logRecords, MESSAGE_KEYS)
            .withLogLevel(Level.FINE));
    mementos.add(testSupport.install());

    WlsDomainConfigSupport configSupport = new WlsDomainConfigSupport(DOMAIN_NAME);
    configSupport.addWlsServer(ADMIN_SERVER, ADMIN_PORT);
    configSupport.addWlsServer(TEST_SERVER, TEST_PORT, ADMIN_PORT);
    configSupport.addWlsCluster(TEST_CLUSTER, TEST_SERVER);
    configSupport.setAdminServerName(ADMIN_SERVER);

    domainConfig = configSupport.createDomainConfig();
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

  @After
  public void tearDownServiceHelperTest() throws Exception {
    testSupport.throwOnCompletionFailure();
  }

  private AdminServerConfigurator configureAdminServer() {
    return configureDomain().configureAdminServer();
  }

  private DomainConfigurator configureDomain() {
    return DomainConfiguratorFactory.forDomain(domainPresenceInfo.getDomain());
  }

  @Parameters(name = "{index} : {0} service test")
  public static Collection<Object[]> data() {
    return Arrays.asList(
        new Object[][] {
          {"cluster", CLUSTER_SERVICE_TEST_FACADE},
          {"managed server", MANAGED_SERVER_TEST_FACADE},
          {"admin server", ADMIN_SERVER_TEST_FACADE},
          {"external", EXTERNAL_SERVICE_TEST_FACADE}
        });
  }

  @Parameter public String testType;

  @Parameter(1)
  public TestFacade testFacade;

  @Test
  public void whenCreated_modelIncludesListenPorts() {
    Assume.assumeFalse(testType.equals("external"));

    V1Service model = testFacade.createServiceModel(testSupport.getPacket());

    assertThat(model, containsPort("default", testFacade.getExpectedListenPort()));
    assertThat(model, containsPort("default-secure", testFacade.getExpectedSslListenPort()));
    assertThat(model, containsPort("default-admin", testFacade.getExpectedAdminPort()));
  }

  @Test
  public void onRunWithNoService_createIt() {
    runServiceHelper();

    assertThat(getCreatedResources(), contains(serviceWithName(testFacade.getServiceName())));
    assertThat(testFacade.getRecordedService(domainPresenceInfo), notNullValue());
    assertThat(logRecords, containsInfo(testFacade.getServiceCreateLogMessage()));
  }

  private void runServiceHelper() {
    testSupport.runSteps(testFacade.createSteps(terminalStep));
  }

  @Test
  public void onFailedRun_reportFailure() {
    testSupport.addRetryStrategy(retryStrategy);
    testSupport.failOnResource(null, NS, 401);

    runServiceHelper();

    testSupport.verifyCompletionThrowable(ApiException.class);
  }

  @Test
  public void whenMatchingServiceRecordedInDomainPresence_logServiceExists() {
    V1Service originalService = testFacade.createServiceModel(testSupport.getPacket());
    testFacade.recordService(domainPresenceInfo, originalService);

    runServiceHelper();

    assertThat(logRecords, containsFine(testFacade.getServiceExistsLogMessage()));
  }

  @Test
  public void whenLabelAdded_replaceService() {
    verifyServiceReplaced(this::addNewLabel);
  }

  @Test
  public void whenLabelChanged_replaceService() {
    verifyServiceReplaced(this::changeLabel);
  }

  @Test
  public void whenAnnotationAdded_replaceService() {
    verifyServiceReplaced(this::addNewAnnotation);
  }

  @Test
  public void whenAnnotationChanged_replaceService() {
    verifyServiceReplaced(this::changeAnnotation);
  }

  @Test
  public void whenListenPortChanged_replaceService() {
    verifyServiceReplaced(this::changeListenPort);
  }

  @Test
  public void whenSslListenPortChanged_replaceService() {
    verifyServiceReplaced(this::changeSslListenPort);
  }

  private void verifyServiceReplaced(Runnable serviceMutator) {
    Assume.assumeFalse(testType.equals("external"));

    recordInitialService();
    serviceMutator.run();

    runServiceHelper();

    assertThat(logRecords, containsInfo(testFacade.getServiceReplacedLogMessage()));
  }

  private void addNewLabel() {
    testFacade.configureService(configureDomain()).withServiceLabel("newLabel", "value");
  }

  private void changeLabel() {
    testFacade.configureService(configureDomain()).withServiceLabel(OLD_LABEL, "newValue");
  }

  private void addNewAnnotation() {
    testFacade.configureService(configureDomain()).withServiceAnnotation("newAnnotation", "value");
  }

  private void changeAnnotation() {
    testFacade.configureService(configureDomain()).withServiceLabel(OLD_ANNOTATION, "newValue");
  }

  private void changeListenPort() {
    serverConfig.setListenPort(9900);
  }

  private void changeSslListenPort() {
    serverConfig.setSslListenPort(9901);
  }

  private void recordInitialService() {
    V1Service originalService = testFacade.createServiceModel(testSupport.getPacket());
    testSupport.defineResources(originalService);
    testFacade.recordService(domainPresenceInfo, originalService);
  }

  private List<V1Service> getCreatedResources() {
    return testSupport.getResources(KubernetesTestSupport.SERVICE);
  }

  interface TestFacade {
    String getServiceCreateLogMessage();

    String getServiceExistsLogMessage();

    String getServiceReplacedLogMessage();

    String getServerName();

    String getServiceName();

    Step createSteps(Step next);

    V1Service createServiceModel(Packet packet);

    V1Service getRecordedService(DomainPresenceInfo info);

    void recordService(DomainPresenceInfo info, V1Service service);

    Integer getExpectedListenPort();

    default Integer getExpectedSslListenPort() {
      return null;
    }

    default Integer getExpectedAdminPort() {
      return null;
    }

    default ServiceConfigurator configureService(DomainConfigurator configurator) {
      return Stub.createNiceStub(ServiceConfigurator.class);
    }
  }

  static class ClusterServiceTestFacade implements TestFacade {
    @Override
    public String getServiceCreateLogMessage() {
      return CLUSTER_SERVICE_CREATED;
    }

    @Override
    public String getServiceExistsLogMessage() {
      return CLUSTER_SERVICE_EXISTS;
    }

    @Override
    public String getServiceReplacedLogMessage() {
      return CLUSTER_SERVICE_REPLACED;
    }

    @Override
    public String getServerName() {
      return TEST_SERVER;
    }

    @Override
    public String getServiceName() {
      return LegalNames.toClusterServiceName(UID, TEST_CLUSTER);
    }

    @Override
    public Step createSteps(Step next) {
      return ServiceHelper.createForClusterStep(next);
    }

    @Override
    public V1Service createServiceModel(Packet packet) {
      return ServiceHelper.createClusterServiceModel(packet);
    }

    @Override
    public V1Service getRecordedService(DomainPresenceInfo info) {
      return info.getClusters().get(TEST_CLUSTER);
    }

    @Override
    public void recordService(DomainPresenceInfo info, V1Service service) {
      info.getClusters().put(TEST_CLUSTER, service);
    }

    @Override
    public Integer getExpectedListenPort() {
      return TEST_PORT;
    }

    @Override
    public Integer getExpectedAdminPort() {
      return ADMIN_PORT;
    }

    @Override
    public ServiceConfigurator configureService(DomainConfigurator configurator) {
      return configurator.configureCluster(TEST_CLUSTER);
    }
  }

  abstract static class ServerTestFacade implements TestFacade {

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
      return info.getServers().get(getServerName()).getService().get();
    }

    @Override
    public void recordService(DomainPresenceInfo info, V1Service service) {
      info.getServers().put(getServerName(), createSko(service));
    }

    @Override
    public ServiceConfigurator configureService(DomainConfigurator configurator) {
      return configurator.configureServer(getServerName());
    }
  }

  static class ManagedServerTestFacade extends ServerTestFacade {
    @Override
    public String getServiceCreateLogMessage() {
      return MANAGED_SERVICE_CREATED;
    }

    @Override
    public String getServiceExistsLogMessage() {
      return MANAGED_SERVICE_EXISTS;
    }

    @Override
    public String getServiceReplacedLogMessage() {
      return MANAGED_SERVICE_REPLACED;
    }

    @Override
    public Integer getExpectedListenPort() {
      return TEST_PORT;
    }

    @Override
    public Integer getExpectedAdminPort() {
      return ADMIN_PORT;
    }

    @Override
    public String getServerName() {
      return TEST_SERVER;
    }
  }

  static class AdminServerTestFacade extends ServerTestFacade {
    @Override
    public String getServiceCreateLogMessage() {
      return ADMIN_SERVICE_CREATED;
    }

    @Override
    public String getServiceExistsLogMessage() {
      return ADMIN_SERVICE_EXISTS;
    }

    @Override
    public String getServiceReplacedLogMessage() {
      return ADMIN_SERVICE_REPLACED;
    }

    @Override
    public Integer getExpectedListenPort() {
      return ADMIN_PORT;
    }

    @Override
    public String getServerName() {
      return ADMIN_SERVER;
    }
  }

  static class ExternalServiceTestFacade implements TestFacade {
    @Override
    public String getServiceCreateLogMessage() {
      return ADMIN_SERVICE_CREATED;
    }

    @Override
    public String getServiceExistsLogMessage() {
      return ADMIN_SERVICE_EXISTS;
    }

    @Override
    public String getServiceReplacedLogMessage() {
      return ADMIN_SERVICE_REPLACED;
    }

    @Override
    public String getServerName() {
      return ADMIN_SERVER;
    }

    @Override
    public String getServiceName() {
      return LegalNames.toExternalServiceName(UID, ADMIN_SERVER);
    }

    @Override
    public Step createSteps(Step next) {
      return ServiceHelper.createForExternalServiceStep(next);
    }

    @Override
    public V1Service createServiceModel(Packet packet) {
      return ServiceHelper.createExternalServiceModel(packet);
    }

    @Override
    public V1Service getRecordedService(DomainPresenceInfo info) {
      return info.getServers().get(ADMIN_SERVER).getService().get();
    }

    @Override
    public void recordService(DomainPresenceInfo info, V1Service service) {
      info.getServers().put(ADMIN_SERVER, createSko(service));
    }

    @Override
    public Integer getExpectedListenPort() {
      return null;
    }
  }

  @SuppressWarnings("unused")
  static class ServiceNameMatcher
      extends org.hamcrest.TypeSafeDiagnosingMatcher<io.kubernetes.client.models.V1Service> {
    private String expectedName;

    private ServiceNameMatcher(String expectedName) {
      this.expectedName = expectedName;
    }

    static ServiceNameMatcher serviceWithName(String expectedName) {
      return new ServiceNameMatcher(expectedName);
    }

    @Override
    protected boolean matchesSafely(V1Service item, Description mismatchDescription) {
      if (expectedName.equals(getName(item))) return true;

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
      extends org.hamcrest.TypeSafeDiagnosingMatcher<io.kubernetes.client.models.V1Service> {
    @Nonnull private final String expectedName;
    private final Integer expectedValue;
    private final Integer expectedNodePort;

    private PortMatcher(@Nonnull String expectedName, Integer expectedValue, Integer nodePort) {
      this.expectedName = expectedName;
      this.expectedValue = expectedValue;
      this.expectedNodePort = nodePort;
    }

    static PortMatcher containsPort(@Nonnull String expectedName, Integer expectedValue) {
      return new PortMatcher(expectedName, expectedValue, null);
    }

    @Override
    protected boolean matchesSafely(V1Service item, Description mismatchDescription) {
      V1ServicePort matchingPort = getPortWithName(item);

      if (matchingPort == null) {
        if (expectedValue == null) return true;
        mismatchDescription.appendText("contains no port with name ").appendValue(expectedName);
        return false;
      } else {
        if (matchSelectedPort(matchingPort)) return true;
        mismatchDescription.appendText("contains port ").appendValue(matchingPort);
        return false;
      }
    }

    private boolean matchSelectedPort(V1ServicePort matchingPort) {
      return "TCP".equals(matchingPort.getProtocol())
          && Objects.equals(expectedValue, matchingPort.getPort())
          && Objects.equals(expectedNodePort, matchingPort.getNodePort());
    }

    private V1ServicePort getPortWithName(V1Service item) {
      return item.getSpec().getPorts().stream().filter(this::hasName).findFirst().orElse(null);
    }

    private boolean hasName(V1ServicePort p) {
      return expectedName.equals(p.getName());
    }

    @Override
    public void describeTo(Description description) {
      if (expectedValue == null)
        description.appendText("service with no port named ").appendValue(expectedName);
      else {
        description
            .appendText("service with TCP port with name ")
            .appendValue(expectedName)
            .appendText(", number ")
            .appendValue(expectedValue);
        if (expectedNodePort != null)
          description.appendText(" and node port ").appendValue(expectedNodePort);
      }
    }
  }
}
