// Copyright (c) 2023, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.steps;

import java.net.URI;
import java.net.http.HttpRequest;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import javax.annotation.Nonnull;

import com.meterware.simplestub.Memento;
import io.kubernetes.client.openapi.models.V1Container;
import io.kubernetes.client.openapi.models.V1EnvVar;
import io.kubernetes.client.openapi.models.V1ObjectMeta;
import io.kubernetes.client.openapi.models.V1Pod;
import io.kubernetes.client.openapi.models.V1PodCondition;
import io.kubernetes.client.openapi.models.V1PodSpec;
import io.kubernetes.client.openapi.models.V1PodStatus;
import io.kubernetes.client.openapi.models.V1Service;
import oracle.kubernetes.operator.DomainProcessorTestSetup;
import oracle.kubernetes.operator.KubernetesConstants;
import oracle.kubernetes.operator.LabelConstants;
import oracle.kubernetes.operator.ShutdownType;
import oracle.kubernetes.operator.helpers.AnnotationHelper;
import oracle.kubernetes.operator.helpers.DomainPresenceInfo;
import oracle.kubernetes.operator.helpers.KubernetesTestSupport;
import oracle.kubernetes.operator.helpers.KubernetesUtils;
import oracle.kubernetes.operator.helpers.LegalNames;
import oracle.kubernetes.operator.helpers.PodHelper;
import oracle.kubernetes.operator.helpers.UnitTestHash;
import oracle.kubernetes.operator.http.client.HttpAsyncRequestStep;
import oracle.kubernetes.operator.http.client.HttpAsyncTestSupport;
import oracle.kubernetes.operator.http.client.HttpResponseStub;
import oracle.kubernetes.operator.steps.ShutdownManagedServerStep.ShutdownManagedServerProcessing;
import oracle.kubernetes.operator.steps.ShutdownManagedServerStep.ShutdownManagedServerResponseStep;
import oracle.kubernetes.operator.tuning.TuningParametersStub;
import oracle.kubernetes.operator.utils.WlsDomainConfigSupport;
import oracle.kubernetes.operator.work.Component;
import oracle.kubernetes.operator.work.Packet;
import oracle.kubernetes.operator.work.Step;
import oracle.kubernetes.operator.work.TerminalStep;
import oracle.kubernetes.utils.SystemClockTestSupport;
import oracle.kubernetes.utils.TestUtils;
import oracle.kubernetes.weblogic.domain.model.DomainResource;
import oracle.kubernetes.weblogic.domain.model.DomainStatus;
import oracle.kubernetes.weblogic.domain.model.ServerStatus;
import oracle.kubernetes.weblogic.domain.model.Shutdown;
import org.hamcrest.junit.MatcherAssert;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static com.meterware.simplestub.Stub.createStub;
import static java.net.HttpURLConnection.HTTP_NOT_FOUND;
import static oracle.kubernetes.common.logging.MessageKeys.SERVER_SHUTDOWN_REST_FAILURE;
import static oracle.kubernetes.common.logging.MessageKeys.SERVER_SHUTDOWN_REST_RETRY;
import static oracle.kubernetes.common.logging.MessageKeys.SERVER_SHUTDOWN_REST_SUCCESS;
import static oracle.kubernetes.common.logging.MessageKeys.SERVER_SHUTDOWN_REST_THROWABLE;
import static oracle.kubernetes.common.utils.LogMatcher.containsFine;
import static oracle.kubernetes.common.utils.LogMatcher.containsInfo;
import static oracle.kubernetes.operator.LabelConstants.CLUSTERNAME_LABEL;
import static oracle.kubernetes.operator.LabelConstants.SERVERNAME_LABEL;
import static oracle.kubernetes.operator.ProcessingConstants.DOMAIN_TOPOLOGY;
import static oracle.kubernetes.operator.ProcessingConstants.SERVER_NAME;
import static oracle.kubernetes.operator.WebLogicConstants.ADMIN_STATE;
import static oracle.kubernetes.operator.WebLogicConstants.RUNNING_STATE;
import static oracle.kubernetes.operator.WebLogicConstants.SHUTDOWN_STATE;
import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;

class ShutdownManagedServerStepTest {
  // The log messages to be checked during this test
  private static final String[] LOG_KEYS = {
      SERVER_SHUTDOWN_REST_SUCCESS, SERVER_SHUTDOWN_REST_FAILURE,
      SERVER_SHUTDOWN_REST_RETRY,
      SERVER_SHUTDOWN_REST_THROWABLE
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
  private static final String RESPONSE = "httpResponse";
  private static final String SHUTDOWN_REQUEST_RETRY_COUNT = "shutdownRequestRetryCount";

  private final V1Pod configuredManagedServer1 = defineManagedPod(CONFIGURED_MANAGED_SERVER1);
  private final V1Pod standaloneManagedServer1 = defineManagedPod(MANAGED_SERVER1);
  private final V1Pod dynamicManagedServer1 = defineManagedPod(DYNAMIC_MANAGED_SERVER1);
  private V1Service configuredServerService;
  private V1Service standaloneServerService;
  private V1Service dynamicServerService;
  private final List<LogRecord> logRecords = new ArrayList<>();
  private final List<Memento> mementos = new ArrayList<>();
  private final KubernetesTestSupport testSupport = new KubernetesTestSupport();
  private final HttpAsyncTestSupport httpSupport = new HttpAsyncTestSupport();
  private final TerminalStep terminalStep = new TerminalStep();
  private final Step shutdownConfiguredManagedServer = ShutdownManagedServerStep
      .createShutdownManagedServerStep(terminalStep, CONFIGURED_MANAGED_SERVER1, configuredManagedServer1);
  private final Step shutdownStandaloneManagedServer = ShutdownManagedServerStep
      .createShutdownManagedServerStep(terminalStep, MANAGED_SERVER1, standaloneManagedServer1);
  private final Step shutdownDynamicManagedServer = ShutdownManagedServerStep
      .createShutdownManagedServerStep(terminalStep, DYNAMIC_MANAGED_SERVER1, dynamicManagedServer1);
  private final DomainResource domain = DomainProcessorTestSetup.createTestDomain();
  private final DomainPresenceInfo info = new DomainPresenceInfo(domain);

  @BeforeEach
  void setup() throws NoSuchFieldException {
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
    mementos.add(UnitTestHash.install());

    testSupport.addDomainPresenceInfo(info);
    testSupport.addToPacket(DOMAIN_TOPOLOGY, configSupport.createDomainConfig());
    testSupport.defineResources(domain);

    configuredServerService = createServerService(CONFIGURED_MANAGED_SERVER1, CONFIGURED_CLUSTER_NAME);
    standaloneServerService = createServerService(MANAGED_SERVER1, null);
    dynamicServerService = createServerService(DYNAMIC_MANAGED_SERVER1, DYNAMIC_CLUSTER_NAME);
    DomainProcessorTestSetup.defineSecretData(testSupport);
    defineServerPodsInDomainPresenceInfo();
  }

  private void defineServerPodsInDomainPresenceInfo() {
    info.setServerPod(CONFIGURED_MANAGED_SERVER1, configuredManagedServer1);
    info.setServerPod(MANAGED_SERVER1, standaloneManagedServer1);
    info.setServerPod(DYNAMIC_MANAGED_SERVER1, dynamicManagedServer1);
  }

  private void selectServer(String serverName, V1Service service) {
    testSupport.addToPacket(SERVER_NAME, serverName);
    info.setServerService(serverName, service);
  }

  @AfterEach
  void tearDown() {
    mementos.forEach(Memento::revert);
  }

  private void defineResponse(int status, String url) {
    defineResponse(true, status, url);
  }

  private void defineResponse(boolean gracefulShutdown, int status, String url) {
    HttpRequest request = gracefulShutdown
        ? createExpectedRequest(Objects.requireNonNullElse(
            url, "http://127.0.0.1:7001"))
        : createExpectedRequestForcedShutdown(Objects.requireNonNullElse(
            url, "http://127.0.0.1:7001"));
    httpSupport.defineResponse(request, createStub(HttpResponseStub.class, status, ""));
  }

  private HttpRequest createExpectedRequest(String url) {
    return HttpRequest.newBuilder()
        .uri(URI.create(url + "/management/weblogic/latest/serverRuntime/shutdown"))
        .POST(HttpRequest.BodyPublishers.noBody())
        .build();
  }

  private HttpRequest createExpectedRequestForcedShutdown(String url) {
    return HttpRequest.newBuilder()
        .uri(URI.create(url + "/management/weblogic/latest/serverRuntime/forceShutdown"))
        .POST(HttpRequest.BodyPublishers.noBody())
        .build();
  }

  private V1Pod createPod(String serverName) {
    List<V1EnvVar> env = addShutdownEnvVars();
    List<V1Container> containers = addEnvToWLSContainer(env);
    V1PodSpec podSpec = new V1PodSpec().containers(containers);
    return new V1Pod().metadata(createManagedPodMetadata(serverName)).spec(podSpec).status(createPodReadyStatus());
  }

  private V1PodStatus createPodReadyStatus() {
    return new V1PodStatus()
        .phase("Running")
        .addConditionsItem(new V1PodCondition().status("True").type("Ready"));
  }

  @Nonnull
  private List<V1Container> addEnvToWLSContainer(List<V1EnvVar> env) {
    List<V1Container> containers = new ArrayList<>();
    V1Container container = new V1Container().name(KubernetesConstants.WLS_CONTAINER_NAME).env(env);
    containers.add(container);
    return containers;
  }

  @Nonnull
  private List<V1EnvVar> addShutdownEnvVars() {
    List<V1EnvVar> env = new ArrayList<>();
    addEnvVar(env, "SHUTDOWN_TYPE", ShutdownType.GRACEFUL.toString());
    addEnvVar(env, "SHUTDOWN_TIMEOUT", String.valueOf(Shutdown.DEFAULT_TIMEOUT));
    addEnvVar(env, "SHUTDOWN_IGNORE_SESSIONS", String.valueOf(Shutdown.DEFAULT_IGNORESESSIONS));
    addEnvVar(env, "SHUTDOWN_WAIT_FOR_ALL_SESSIONS", String.valueOf(Shutdown.DEFAULT_WAIT_FOR_ALL_SESSIONS));
    return env;
  }

  private void addEnvVar(List<V1EnvVar> vars, String name, String value) {
    vars.add(new V1EnvVar().name(name).value(value));
  }

  @Test
  void whenAuthorizedToInvokeShutdown_verifySecretSet() {
    selectServer(CONFIGURED_MANAGED_SERVER1, configuredServerService);

    defineResponse(200, "http://test-domain-conf-managed-server1.namespace.svc:7001");

    // Validate not set before running steps
    assertThat(info.getWebLogicCredentialsSecret(), is(nullValue()));

    testSupport.runSteps(shutdownConfiguredManagedServer);

    // Validate is set after running steps
    assertThat(info.getWebLogicCredentialsSecret(), is(notNullValue()));
    assertThat(logRecords, containsFine(SERVER_SHUTDOWN_REST_SUCCESS));
  }

  @Test
  void whenInvokeShutdown_configuredClusterServer_verifySuccess() {
    selectServer(CONFIGURED_MANAGED_SERVER1, configuredServerService);

    defineResponse(200, "http://test-domain-conf-managed-server1.namespace.svc:7001");

    testSupport.runSteps(shutdownConfiguredManagedServer);

    assertThat(logRecords, containsFine(SERVER_SHUTDOWN_REST_SUCCESS));
  }

  @Test
  void whenInvokeShutdown_configuredClusterServer_verifyFailure() {
    selectServer(CONFIGURED_MANAGED_SERVER1, configuredServerService);

    defineResponse(404, "http://test-domain-conf-managed-server1.namespace.svc:7001");

    testSupport.runSteps(shutdownConfiguredManagedServer);

    assertThat(logRecords, containsInfo(SERVER_SHUTDOWN_REST_FAILURE));
  }

  @Test
  void whenInvokeShutdown_standaloneServer_verifySuccess() {
    selectServer(MANAGED_SERVER1, standaloneServerService);

    defineResponse(200, "http://test-domain-managed-server1.namespace.svc:8001");

    testSupport.runSteps(shutdownStandaloneManagedServer);

    assertThat(logRecords, containsFine(SERVER_SHUTDOWN_REST_SUCCESS));
  }

  @Test
  void whenInvokeShutdown_standaloneServer_verifyFailure() {
    selectServer(MANAGED_SERVER1, standaloneServerService);

    defineResponse(404, "http://test-domain-managed-server1.namespace.svc:7001");

    testSupport.runSteps(shutdownStandaloneManagedServer);

    assertThat(logRecords, containsInfo(SERVER_SHUTDOWN_REST_FAILURE));
  }

  @Test
  void whenInvokeShutdown_standaloneServer_DomainNotFound_verifyFailureAndRunNextStep() {
    selectServer(MANAGED_SERVER1, standaloneServerService);

    defineResponse(404, "http://test-domain-managed-server1.namespace.svc:7001");
    testSupport.failOnResource(KubernetesTestSupport.DOMAIN, UID, NS, HTTP_NOT_FOUND);

    testSupport.runSteps(shutdownStandaloneManagedServer);

    MatcherAssert.assertThat(terminalStep.wasRun(), is(true));
    assertThat(logRecords, containsInfo(SERVER_SHUTDOWN_REST_FAILURE));
  }

  @Test
  void whenInvokeShutdown_dynamicServer_verifySuccess() {
    selectServer(DYNAMIC_MANAGED_SERVER1, dynamicServerService);

    defineResponse(200, "http://test-domain-dyn-managed-server1.namespace.svc:7001");

    testSupport.runSteps(shutdownDynamicManagedServer);

    assertThat(logRecords, containsFine(SERVER_SHUTDOWN_REST_SUCCESS));
  }

  @Test
  void whenInvokeShutdown_dynamicServer_verifyFailure() {
    selectServer(DYNAMIC_MANAGED_SERVER1, dynamicServerService);

    defineResponse(404, "http://test-domain-dyn-managed-server1.namespace.svc:8001");

    testSupport.runSteps(shutdownDynamicManagedServer);

    assertThat(logRecords, containsInfo(SERVER_SHUTDOWN_REST_FAILURE));
  }

  @Test
  void whenInvokeForceShutdown_standaloneServer_verifySuccess() {
    selectServer(MANAGED_SERVER1, standaloneServerService);
    setForcedShutdownType(MANAGED_SERVER1);

    defineResponse(false, 200, "http://test-domain-managed-server1.namespace.svc:8001");

    testSupport.runSteps(shutdownStandaloneManagedServer);

    assertThat(httpSupport.getLastRequest().toString(), containsString("forceShutdown"));
    assertThat(logRecords, containsFine(SERVER_SHUTDOWN_REST_SUCCESS));
  }

  @Test
  void verifyShutdownManagedServerProcessing_requestTimeout() {
    ShutdownManagedServerProcessing processing = new ShutdownManagedServerProcessing(testSupport.getPacket(),
        configuredServerService, configuredManagedServer1);
    assertThat(
        processing.getRequestTimeoutSeconds(),
        equalTo(Shutdown.DEFAULT_TIMEOUT + PodHelper.DEFAULT_ADDITIONAL_DELETE_TIME));
  }

  @ParameterizedTest
  @ValueSource(strings = {RUNNING_STATE, ADMIN_STATE, SHUTDOWN_STATE})
  void whenShutdownResponseThrowsExceptionAndServerIsRunning_logRetry(String state) {
    ShutdownManagedServerResponseStep responseStep =
        new ShutdownManagedServerResponseStep(MANAGED_SERVER1, terminalStep);
    Packet p = testSupport.getPacket();

    // Setup Throwable exception
    p.getComponents().put(RESPONSE, Component
        .createFor(Throwable.class, new Throwable()));
    ShutdownManagedServerProcessing processing = new ShutdownManagedServerProcessing(p,
        standaloneServerService, standaloneManagedServer1);
    HttpAsyncRequestStep httpAsyncRequestStep = processing.createRequestStep(responseStep);
    responseStep.setHttpAsyncRequestStep(httpAsyncRequestStep);
    setServerState(state);

    responseStep.onFailure(p, null);
    if (!SHUTDOWN_STATE.equals(state)) {
      // Assert that we will retry due to exception and server not shutdown
      assertRetry(p);
    } else {
      // Assert that we will not retry as server is shutdown
      assertNoRetry(p);
    }

    // Assert we only retry once
    responseStep.onFailure(p, null);
    assertThat(p.get(SHUTDOWN_REQUEST_RETRY_COUNT), is(nullValue()));
    if (!SHUTDOWN_STATE.equals(state)) {
      // Log throwable message only if the server is not shutdown.
      assertThat(logRecords, containsInfo(SERVER_SHUTDOWN_REST_THROWABLE));
    }
  }

  private void assertRetry(Packet p) {
    assertThat(p.get(SHUTDOWN_REQUEST_RETRY_COUNT), equalTo(1));
    assertThat(logRecords, containsInfo(SERVER_SHUTDOWN_REST_RETRY));
  }

  private void assertNoRetry(Packet p) {
    assertThat(p.get(SHUTDOWN_REQUEST_RETRY_COUNT), equalTo(null));
    assertThat(logRecords, not(containsInfo(SERVER_SHUTDOWN_REST_RETRY)));
  }

  private void setServerState(String state) {
    DomainStatus domainStatus = info.getDomain().getStatus();
    domainStatus.withServers(Collections.singletonList(new ServerStatus()
        .withServerName(MANAGED_SERVER1).withState(state)));
  }

  private void setForcedShutdownType(String serverName) {
    V1Pod serverPod = info.getServerPod(serverName);
    List<V1EnvVar> vars = Objects.requireNonNull(serverPod.getSpec()).getContainers().stream()
        .filter(this::isK8sContainer).findFirst().map(V1Container::getEnv).get();
    for (V1EnvVar var : vars) {
      if (var.getName().equals("SHUTDOWN_TYPE")) {
        var.setValue(ShutdownType.FORCED.toString());
      }
    }
  }

  private boolean isK8sContainer(V1Container c) {
    return KubernetesConstants.WLS_CONTAINER_NAME.equals(c.getName());
  }

  private V1Pod defineManagedPod(String name) {
    return createPod(name);
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
      Objects.requireNonNull(service.getMetadata()).putLabelsItem(CLUSTERNAME_LABEL, clusterName);
    }

    return AnnotationHelper.withSha256Hash(service);
  }

  private static V1ObjectMeta withServerLabels(V1ObjectMeta meta, String serverName) {
    return KubernetesUtils.withOperatorLabels(UID, meta)
        .putLabelsItem(SERVERNAME_LABEL, serverName);
  }
}
