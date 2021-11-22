// Copyright (c) 2018, 2021, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.steps;

import java.net.URI;
import java.net.http.HttpRequest;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.LogRecord;

import com.meterware.httpunit.Base64;
import com.meterware.simplestub.Memento;
import io.kubernetes.client.openapi.models.V1ObjectMeta;
import io.kubernetes.client.openapi.models.V1Pod;
import io.kubernetes.client.openapi.models.V1Service;
import io.kubernetes.client.openapi.models.V1ServiceSpec;
import oracle.kubernetes.operator.DomainProcessorTestSetup;
import oracle.kubernetes.operator.LabelConstants;
import oracle.kubernetes.operator.helpers.AnnotationHelper;
import oracle.kubernetes.operator.helpers.DomainPresenceInfo;
import oracle.kubernetes.operator.helpers.KubernetesTestSupport;
import oracle.kubernetes.operator.helpers.KubernetesUtils;
import oracle.kubernetes.operator.helpers.LegalNames;
import oracle.kubernetes.operator.helpers.TuningParametersStub;
import oracle.kubernetes.operator.http.HttpAsyncTestSupport;
import oracle.kubernetes.operator.http.HttpResponseStub;
import oracle.kubernetes.operator.utils.WlsDomainConfigSupport;
import oracle.kubernetes.operator.work.Step;
import oracle.kubernetes.operator.work.TerminalStep;
import oracle.kubernetes.utils.SystemClockTestSupport;
import oracle.kubernetes.utils.TestUtils;
import oracle.kubernetes.weblogic.domain.model.Domain;
import org.hamcrest.junit.MatcherAssert;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static com.meterware.simplestub.Stub.createStub;
import static oracle.kubernetes.operator.LabelConstants.CLUSTERNAME_LABEL;
import static oracle.kubernetes.operator.LabelConstants.SERVERNAME_LABEL;
import static oracle.kubernetes.operator.ProcessingConstants.DOMAIN_TOPOLOGY;
import static oracle.kubernetes.operator.ProcessingConstants.SERVER_NAME;
import static oracle.kubernetes.operator.helpers.KubernetesTestSupport.POD;
import static oracle.kubernetes.operator.logging.MessageKeys.SERVER_SHUTDOWN_REST_FAILURE;
import static oracle.kubernetes.operator.logging.MessageKeys.SERVER_SHUTDOWN_REST_SUCCESS;
import static oracle.kubernetes.utils.LogMatcher.containsFine;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

class ShutdownManagedServerStepTest {
  // The log messages to be checked during this test
  private static final String[] LOG_KEYS = {
      SERVER_SHUTDOWN_REST_SUCCESS, SERVER_SHUTDOWN_REST_FAILURE
  };
  private static final String UID = "test-domain";
  private static final String NS = "namespace";
  private static final String DOMAIN_NAME = "domain";
  private static final String ADMIN_NAME = "admin-server";
  private static final int ADMIN_PORT_NUM = 3456;
  private static final String MANAGED_SERVER1 = "managed-server1";
  private static final int MANAGED_SERVER1_PORT_NUM = 8001;
  private static final String CONFIGURED_CLUSTER_NAME = "conf-cluster-1";
  private static final String CONFIGURED_MANAGED_SERVER1 = "conf-managed-server1";
  private static final String DYNAMIC_CLUSTER_NAME = "dyn-cluster-1";
  private static final String DYNAMIC_MANAGED_SERVER1 = "dyn-managed-server1";
  private static final String DYNAMIC_MANAGED_SERVER2 = "dyn-managed-server2";

  private final V1Pod configuredManagedServer1 = defineManagedPod(CONFIGURED_MANAGED_SERVER1);
  private final V1Pod standaloneManagedServer1 = defineManagedPod(MANAGED_SERVER1);
  private final V1Pod dynamicManagedServer1 = defineManagedPod(DYNAMIC_MANAGED_SERVER1);
  private final V1Service configuredServerService = createServerService(CONFIGURED_MANAGED_SERVER1,
      CONFIGURED_CLUSTER_NAME);
  private final V1Service standaloneServerService = createServerService(MANAGED_SERVER1, null);
  private final V1Service dynamicServerService = createServerService(DYNAMIC_MANAGED_SERVER1,
      DYNAMIC_CLUSTER_NAME);
  private final V1Service headlessService = createStub(V1HeadlessServiceStub.class);
  private final List<LogRecord> logRecords = new ArrayList<>();
  private final List<Memento> mementos = new ArrayList<>();
  private final KubernetesTestSupport testSupport = new KubernetesTestSupport();
  private final HttpAsyncTestSupport httpSupport = new HttpAsyncTestSupport();
  private final TerminalStep terminalStep = new TerminalStep();
  private Step shutdownConfiguredManagedServer = ShutdownManagedServerStep
      .createShutdownManagedServerStep(terminalStep, CONFIGURED_MANAGED_SERVER1, configuredManagedServer1);
  private Step shutdownStandaloneManagedServer = ShutdownManagedServerStep
      .createShutdownManagedServerStep(terminalStep, MANAGED_SERVER1, standaloneManagedServer1);
  private Step shutdownDynamicManagedServer = ShutdownManagedServerStep
      .createShutdownManagedServerStep(terminalStep, DYNAMIC_MANAGED_SERVER1, dynamicManagedServer1);
  private final Domain domain = DomainProcessorTestSetup.createTestDomain();
  private final DomainPresenceInfo info = new DomainPresenceInfo(domain);

  @BeforeEach
  public void setup() throws NoSuchFieldException {
    WlsDomainConfigSupport configSupport =
        new WlsDomainConfigSupport(DOMAIN_NAME)
            .withWlsServer(ADMIN_NAME, ADMIN_PORT_NUM)
            .withWlsServer(MANAGED_SERVER1, MANAGED_SERVER1_PORT_NUM)
            .withWlsCluster(CONFIGURED_CLUSTER_NAME, CONFIGURED_MANAGED_SERVER1)
            .withDynamicWlsCluster(DYNAMIC_CLUSTER_NAME, DYNAMIC_MANAGED_SERVER1, DYNAMIC_MANAGED_SERVER2)
            .withAdminServerName(ADMIN_NAME);

    mementos.add(TestUtils.silenceOperatorLogger()
        .collectLogMessages(logRecords, LOG_KEYS)
        .withLogLevel(Level.FINE));

    mementos.add(testSupport.install());
    mementos.add(httpSupport.install());
    mementos.add(SystemClockTestSupport.installClock());
    mementos.add(TuningParametersStub.install());

    testSupport.addDomainPresenceInfo(info);
    testSupport.addToPacket(DOMAIN_TOPOLOGY, configSupport.createDomainConfig());
    testSupport.defineResources(domain);

    DomainProcessorTestSetup.defineSecretData(testSupport);
  }

  private void selectServer(String serverName) {
    selectServer(serverName, false);
  }

  private void selectServer(String serverName, V1Service service) {
    testSupport.addToPacket(SERVER_NAME, serverName);
    info.setServerService(serverName, service);
  }

  private void selectServer(String serverName, boolean headless) {
    testSupport.addToPacket(SERVER_NAME, serverName);
    if (headless) {
      info.setServerService(serverName, headlessService);
    } else {
      info.setServerService(serverName, configuredServerService);
    }
  }

  @AfterEach
  public void tearDown() {
    mementos.forEach(Memento::revert);
  }

  private void defineResponse(int status, String body) {
    defineResponse(status, body, null);
  }

  private void defineResponse(int status, String body, String url) {
    httpSupport.defineResponse(
        createExpectedRequest(Objects.requireNonNullElse(url, "http://127.0.0.1:7001")),
        createStub(HttpResponseStub.class, status, body));
  }

  private HttpRequest createExpectedRequest(String url) {
    return HttpRequest.newBuilder()
        .uri(URI.create(url + "/management/weblogic/latest/serverRuntime/shutdown"))
        .POST(HttpRequest.BodyPublishers.noBody())
        .build();
  }


  private boolean hasAuthenticationCredentials(HttpRequest request) {
    return Objects.equals(getAuthorizationHeader(request), expectedAuthorizationHeader());
  }

  private String getAuthorizationHeader(HttpRequest request) {
    return request.headers().firstValue("Authorization").orElse(null);
  }

  private String expectedAuthorizationHeader() {
    return "Basic " + Base64.encode("user:password");
  }

  @Test
  void whenAuthorizedToInvokeShutdown_verifySecretSet() {
    selectServer(CONFIGURED_MANAGED_SERVER1, configuredServerService);

    defineResponse(200, "", "http://test-domain-conf-managed-server1.namespace:7001");

    // Validate not set before running steps
    assertThat(info.getWebLogicCredentialsSecret(), is(nullValue()));

    testSupport.runSteps(shutdownConfiguredManagedServer);

    // Validate is set after running steps
    assertThat(info.getWebLogicCredentialsSecret(), is(notNullValue()));
    MatcherAssert.assertThat(logRecords, containsFine(SERVER_SHUTDOWN_REST_SUCCESS));
  }

  @Test
  void whenInvokeShutdown_configuredClusterServer_verifySuccess() {
    selectServer(CONFIGURED_MANAGED_SERVER1, configuredServerService);

    defineResponse(200, "", "http://test-domain-conf-managed-server1.namespace:7001");

    testSupport.runSteps(shutdownConfiguredManagedServer);

    MatcherAssert.assertThat(logRecords, containsFine(SERVER_SHUTDOWN_REST_SUCCESS));
  }

  @Test
  void whenInvokeShutdown_configuredClusterServer_verifyFailure() {
    selectServer(CONFIGURED_MANAGED_SERVER1, configuredServerService);

    defineResponse(404, "", "http://test-domain-conf-managed-server1.namespace:7001");

    testSupport.runSteps(shutdownConfiguredManagedServer);

    MatcherAssert.assertThat(logRecords, containsFine(SERVER_SHUTDOWN_REST_FAILURE));
  }

  @Test
  void whenInvokeShutdown_standaloneServer_verifySuccess() {
    selectServer(MANAGED_SERVER1, standaloneServerService);

    defineResponse(200, "", "http://test-domain-managed-server1.namespace:8001");

    testSupport.runSteps(shutdownStandaloneManagedServer);

    MatcherAssert.assertThat(logRecords, containsFine(SERVER_SHUTDOWN_REST_SUCCESS));
  }

  @Test
  void whenInvokeShutdown_standaloneServer_verifyFailure() {
    selectServer(MANAGED_SERVER1, standaloneServerService);

    defineResponse(404, "", "http://test-domain-managed-server1.namespace:7001");

    testSupport.runSteps(shutdownStandaloneManagedServer);

    MatcherAssert.assertThat(logRecords, containsFine(SERVER_SHUTDOWN_REST_FAILURE));
  }

  @Test
  void whenInvokeShutdown_dynamicServer_verifySuccess() {
    selectServer(DYNAMIC_MANAGED_SERVER1, dynamicServerService);

    defineResponse(200, "", "http://test-domain-dyn-managed-server1.namespace:7001");

    testSupport.runSteps(shutdownDynamicManagedServer);

    MatcherAssert.assertThat(logRecords, containsFine(SERVER_SHUTDOWN_REST_SUCCESS));
  }

  @Test
  void whenInvokeShutdown_dynamicServer_verifyFailure() {
    selectServer(DYNAMIC_MANAGED_SERVER1, dynamicServerService);

    defineResponse(404, "", "http://test-domain-dyn-managed-server1.namespace:8001");

    testSupport.runSteps(shutdownDynamicManagedServer);

    MatcherAssert.assertThat(logRecords, containsFine(SERVER_SHUTDOWN_REST_FAILURE));
  }


  public abstract static class V1ServiceStub extends V1Service {

    @Override
    public V1ServiceSpec getSpec() {
      return new V1ServiceSpec().clusterIP("127.0.0.1");
    }
  }

  public abstract static class V1HeadlessServiceStub extends V1Service {

    @Override
    public V1ObjectMeta getMetadata() {
      return new V1ObjectMeta().name(ADMIN_NAME).namespace(NS);
    }

    @Override
    public V1ServiceSpec getSpec() {
      return new V1ServiceSpec().clusterIP("None");
    }
  }

  public abstract static class V1HeadlessMSServiceStub extends V1Service {
    @Override
    public V1ObjectMeta getMetadata() {
      return new V1ObjectMeta().name(DYNAMIC_MANAGED_SERVER2).namespace(NS)
          .putLabelsItem(CLUSTERNAME_LABEL, DYNAMIC_CLUSTER_NAME);
    }
  }

  private void configureServiceWithClusterName(String clusterName) {
    configuredServerService
        .setMetadata(new V1ObjectMeta().putLabelsItem(CLUSTERNAME_LABEL, clusterName));
  }

  private V1Pod getSelectedPod(String name) {
    return testSupport.getResourceWithName(POD, name);
  }

  private V1Pod defineManagedPod(String name) {
    return new V1Pod().metadata(createManagedPodMetadata(name));
  }

  private V1ObjectMeta createManagedPodMetadata(String name) {
    return createPodMetadata(name)
        .putLabelsItem(LabelConstants.CREATEDBYOPERATOR_LABEL,"true")
        .putLabelsItem(LabelConstants.DOMAINNAME_LABEL, UID)
        .putLabelsItem(LabelConstants.SERVERNAME_LABEL, name);
  }

  private V1ObjectMeta createPodMetadata(String name) {
    return new V1ObjectMeta()
        .name(name)
        .namespace(NS);
  }

  private static V1Service createServerService(String serverName, String clusterName) {
    V1Service service = new V1Service()
        .metadata(
            withServerLabels(
                new V1ObjectMeta()
                    .name(
                        LegalNames.toServerServiceName(UID, serverName))
                    .namespace(NS),
                serverName));

    if (clusterName != null && !clusterName.isEmpty()) {
      service.getMetadata().putLabelsItem(CLUSTERNAME_LABEL, clusterName);
    }

    return AnnotationHelper.withSha256Hash(service);
  }

  private static V1ObjectMeta withServerLabels(V1ObjectMeta meta, String serverName) {
    return KubernetesUtils.withOperatorLabels(UID, meta)
        .putLabelsItem(SERVERNAME_LABEL, serverName);
  }
}
