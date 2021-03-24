// Copyright (c) 2021, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.steps;

import java.net.URI;
import java.net.http.HttpRequest;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import javax.annotation.Nonnull;

import com.meterware.simplestub.Memento;
import io.kubernetes.client.openapi.models.V1Container;
import io.kubernetes.client.openapi.models.V1ContainerPort;
import io.kubernetes.client.openapi.models.V1ObjectMeta;
import io.kubernetes.client.openapi.models.V1Pod;
import io.kubernetes.client.openapi.models.V1PodCondition;
import io.kubernetes.client.openapi.models.V1PodSpec;
import io.kubernetes.client.openapi.models.V1PodStatus;
import io.kubernetes.client.openapi.models.V1Service;
import io.kubernetes.client.openapi.models.V1ServicePort;
import io.kubernetes.client.openapi.models.V1ServiceSpec;
import oracle.kubernetes.operator.DomainProcessorTestSetup;
import oracle.kubernetes.operator.helpers.DomainPresenceInfo;
import oracle.kubernetes.operator.helpers.KubernetesTestSupport;
import oracle.kubernetes.operator.helpers.SecretHelper;
import oracle.kubernetes.operator.http.HttpAsyncTestSupport;
import oracle.kubernetes.operator.http.HttpResponseStub;
import oracle.kubernetes.operator.utils.WlsDomainConfigSupport;
import oracle.kubernetes.operator.wlsconfig.WlsDomainConfig;
import oracle.kubernetes.operator.work.Step;
import oracle.kubernetes.utils.TestUtils;
import oracle.kubernetes.weblogic.domain.DomainConfigurator;
import oracle.kubernetes.weblogic.domain.DomainConfiguratorFactory;
import oracle.kubernetes.weblogic.domain.model.Domain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static com.meterware.simplestub.Stub.createStub;
import static oracle.kubernetes.operator.DomainProcessorTestSetup.NS;
import static oracle.kubernetes.operator.DomainProcessorTestSetup.defineSecretData;
import static oracle.kubernetes.operator.KubernetesConstants.EXPORTER_CONTAINER_NAME;
import static oracle.kubernetes.operator.ProcessingConstants.DOMAIN_TOPOLOGY;
import static oracle.kubernetes.operator.ProcessingConstants.SERVER_NAME;
import static oracle.kubernetes.operator.helpers.LegalNames.toPodName;
import static oracle.kubernetes.operator.helpers.LegalNames.toServerServiceName;
import static oracle.kubernetes.weblogic.domain.model.MonitoringExporterSpecification.EXPORTER_PORT_NAME;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.junit.MatcherAssert.assertThat;

public class MonitoringExporterStepsTest {

  private static final int EXPORTER_PORT = 8000;
  private static final String ADMIN_SERVER = "admin-server";
  private static final String MANAGED_SERVER1 = "managed-server1";
  private static final String MANAGED_SERVER2 = "managed-server2";
  private static final String MANAGED_SERVER3 = "managed-server3";
  private static final String MANAGED_SERVER4 = "managed-server4";
  private static final int ADMIN_SERVER_PORT_NUM = 7001;
  private static final int MANAGED_SERVER1_PORT_NUM = 8001;
  private static final int MANAGED_SERVER2_PORT_NUM = 8002;
  private static final int MANAGED_SERVER3_PORT_NUM = 8003;
  private static final int MANAGED_SERVER4_PORT_NUM = 8004;
  private static final String POD_NODE1 = "podNode1";
  private static final String POD_NODE2 = "podNode2";
  private static final String POD_NODE3 = "podNode3";
  private static final String DOMAIN_NAME = "domain";
  private static final Map<String,String> SERVER_NODES
        = Map.of(MANAGED_SERVER1, POD_NODE1, MANAGED_SERVER2, POD_NODE2, MANAGED_SERVER3, POD_NODE3);
  private static final String OLD_CONFIGURATION = "queries:\n";
  private static final String NEW_CONFIGURATION = "queries:\n  - key:\n";
  private static final String NEW_CONFIGURATION_REFORMATTED = "queries:\n    - key:\n";

  private final List<Memento> mementos = new ArrayList<>();
  private final KubernetesTestSupport testSupport = new KubernetesTestSupport();
  private final HttpAsyncTestSupport httpSupport = new HttpAsyncTestSupport();
  private final Domain domain = DomainProcessorTestSetup.createTestDomain();
  private final DomainPresenceInfo info = new DomainPresenceInfo(domain);
  private final WlsDomainConfig domainConfig =
        new WlsDomainConfigSupport(DOMAIN_NAME)
              .withAdminServerName(ADMIN_SERVER)
              .withWlsServer(ADMIN_SERVER, ADMIN_SERVER_PORT_NUM)
              .withWlsServer(MANAGED_SERVER1, MANAGED_SERVER1_PORT_NUM)
              .withWlsServer(MANAGED_SERVER2, MANAGED_SERVER2_PORT_NUM)
              .withWlsServer(MANAGED_SERVER3, MANAGED_SERVER3_PORT_NUM)
              .withWlsServer(MANAGED_SERVER4, MANAGED_SERVER4_PORT_NUM)
              .createDomainConfig();

  @BeforeEach
  void setUp() throws NoSuchFieldException {
    mementos.add(TestUtils.silenceOperatorLogger());
    mementos.add(testSupport.install());
    mementos.add(httpSupport.install());

    configureDomain(domain).withMonitoringExporterConfiguration(NEW_CONFIGURATION);
    domainConfig.getServerConfigs().keySet().forEach(this::defineServerPresence);
    forEachServer(this::defineServer);

    testSupport.addDomainPresenceInfo(info);
    testSupport.addToPacket(DOMAIN_TOPOLOGY, domainConfig);
    defineSecretData(testSupport);
  }

  private void defineServerPresence(String serverName) {
    info.setServerPod(serverName, null);
  }

  private void forEachServer(Consumer<String> serverNameConsumer) {
    SERVER_NODES.keySet().forEach(serverNameConsumer);
  }

  private void defineServer(String serverName) {
    final V1Pod pod = createPod(serverName);
    final V1Service service = createServerService(serverName);
    info.setServerPod(serverName, pod);
    info.setServerService(serverName, service);
    testSupport.defineResources(pod, service);
  }

  private DomainConfigurator configureDomain(Domain domain) {
    return DomainConfiguratorFactory.forDomain(domain);
  }

  private V1Pod createPod(String serverName) {
    return new V1Pod()
          .metadata(new V1ObjectMeta().namespace(NS).name(toPodName(DOMAIN_NAME, serverName)))
          .spec(new V1PodSpec().addContainersItem(createExporterSidecar()))
          .status(createReadyStatus().podIP(SERVER_NODES.get(serverName)));
  }

  @Nonnull
  private V1PodStatus createReadyStatus() {
    return setReady(new V1PodStatus());
  }

  private V1PodStatus setReady(V1PodStatus status) {
    status.phase("Running").setConditions(List.of(new V1PodCondition().type("Ready").status("True")));
    return status;
  }

  private V1Container createExporterSidecar() {
    return new V1Container()
          .name(EXPORTER_CONTAINER_NAME)
          .addPortsItem(new V1ContainerPort().name(EXPORTER_PORT_NAME).containerPort(EXPORTER_PORT));
  }

  private V1Service createServerService(String serverName) {
    return new V1Service()
          .metadata(new V1ObjectMeta().namespace(NS).name(toServerServiceName(DOMAIN_NAME, serverName)))
          .spec(new V1ServiceSpec().addPortsItem(new V1ServicePort().name(EXPORTER_PORT_NAME).port(EXPORTER_PORT)));
  }

  @AfterEach
  void tearDown() throws Exception {
    mementos.forEach(Memento::revert);

    testSupport.throwOnCompletionFailure();
  }

  @Test
  void whenConfigurationUpdateStepInvoked_sendConfigurationInPut() {
    testSupport.addToPacket(SERVER_NAME, MANAGED_SERVER1);
    expectConfigurationUpdate(MANAGED_SERVER1);

    testSupport.runSteps(
          Step.chain(
                SecretHelper.createAuthorizationHeaderFactoryStep(),
                MonitorExporterSteps.createConfigurationUpdateStep()));

    assertThat(httpSupport.getLastRequestContents(),
          equalTo(domain.getMonitoringExporterConfiguration().asJsonString()));
  }

  private void expectConfigurationUpdate(String serverName) {
    httpSupport.defineResponse(
          createPutRequest(getExporterUrl(serverName) + "/configuration", "ignored"),
          createStub(HttpResponseStub.class, 200, "OK"));
  }

  @Nonnull
  private String getExporterUrl(String serverName) {
    return "http://" + SERVER_NODES.get(serverName) + ":" + EXPORTER_PORT;
  }

  @SuppressWarnings("SameParameterValue")
  private HttpRequest createPutRequest(String urlString, String body) {
    return HttpRequest.newBuilder().PUT(HttpRequest.BodyPublishers.ofString(body)).uri(URI.create(urlString)).build();
  }

  @Test
  void whenSidecarHasDifferentConfiguration_sendNewConfiguration() {
    testSupport.addToPacket(SERVER_NAME, MANAGED_SERVER1);
    expectConfigurationQueryAndReturn(MANAGED_SERVER1, OLD_CONFIGURATION);
    expectConfigurationUpdate(MANAGED_SERVER1);

    testSupport.runSteps(
          Step.chain(
                SecretHelper.createAuthorizationHeaderFactoryStep(),
                MonitorExporterSteps.createConfigurationTestAndUpdateSteps()));


    assertThat(httpSupport.getLastRequestContents(),
          equalTo(domain.getMonitoringExporterConfiguration().asJsonString()));
  }

  private void expectConfigurationQueryAndReturn(String serverName, String configuration) {
    httpSupport.defineResponse(
          createGetRequest(getExporterUrl(serverName) + "/"),
          createStub(HttpResponseStub.class, 200, toExporterWebPage(configuration)));
  }

  private String toExporterWebPage(String configuration) {
    return "<p>Current Configuration</p>\n"
          + "<p><code><pre>\n"
          + "host: localhost\n"
          + "port: 7001\n"
          + configuration
          + "</pre></code></p>\n";
  }

  @SuppressWarnings("SameParameterValue")
  private HttpRequest createGetRequest(String urlString) {
    return HttpRequest.newBuilder().GET().uri(URI.create(urlString)).build();
  }

  @Test
  void whenSidecarHasSameConfiguration_dontSendNewConfiguration() {
    testSupport.addToPacket(SERVER_NAME, MANAGED_SERVER1);
    expectConfigurationQueryAndReturn(MANAGED_SERVER1, NEW_CONFIGURATION_REFORMATTED);
    expectConfigurationUpdate(MANAGED_SERVER1);

    testSupport.runSteps(
          Step.chain(
                SecretHelper.createAuthorizationHeaderFactoryStep(),
                MonitorExporterSteps.createConfigurationTestAndUpdateSteps()));

    assertThat(httpSupport.getLastRequest().method(), equalTo("GET"));
  }

  @Test
  void whenParallelStepsRun_sendNewConfigurationOnlyIfNeeded() {
    expectConfigurationQueryAndReturn(MANAGED_SERVER1, OLD_CONFIGURATION);
    expectConfigurationQueryAndReturn(MANAGED_SERVER2, NEW_CONFIGURATION);
    expectConfigurationQueryAndReturn(MANAGED_SERVER3, OLD_CONFIGURATION);
    forEachServer(this::expectConfigurationUpdate);

    testSupport.runSteps(MonitorExporterSteps.updateExporterSidecars());

    assertThat(getServersUpdated(), containsInAnyOrder(POD_NODE1, POD_NODE3));
  }

  @Nonnull
  private List<String> getServersUpdated() {
    return httpSupport.getHandledRequests().stream()
          .filter(this::isPutRequest)
          .map(HttpRequest::uri)
          .map(URI::getHost)
          .collect(Collectors.toList());
  }

  private boolean isPutRequest(HttpRequest request) {
    return request.method().equals("PUT");
  }

  @Test
  void skipPodBeingDeleted() {
    forEachServer(this::expectQueryAndReturnOldConfiguration);
    forEachServer(this::expectConfigurationUpdate);
    setDeletingState(MANAGED_SERVER1);

    testSupport.runSteps(MonitorExporterSteps.updateExporterSidecars());

    assertThat(getServersUpdated(), containsInAnyOrder(POD_NODE2, POD_NODE3));
  }

  private void expectQueryAndReturnOldConfiguration(String serverName) {
    expectConfigurationQueryAndReturn(serverName, OLD_CONFIGURATION);
  }

  @SuppressWarnings("SameParameterValue")
  private void setDeletingState(String serverName) {
    Optional.ofNullable(info.getServerPod(serverName)).map(V1Pod::getMetadata).ifPresent(this::setDeletingState);
  }

  private void setDeletingState(V1ObjectMeta meta) {
    meta.setDeletionTimestamp(OffsetDateTime.now());
  }

  @Test
  void dontUpdatePodWhileNotReady() {
    forEachServer(this::expectQueryAndReturnOldConfiguration);
    forEachServer(this::expectConfigurationUpdate);
    setNotReadyState(MANAGED_SERVER1);
    setNotReadyState(MANAGED_SERVER3);

    testSupport.runSteps(MonitorExporterSteps.updateExporterSidecars());

    assertThat(getServersUpdated(), containsInAnyOrder(POD_NODE2));
  }

  @SuppressWarnings("SameParameterValue")
  private void setNotReadyState(String serverName) {
    Optional.ofNullable(info.getServerPod(serverName)).map(V1Pod::getStatus).ifPresent(this::setNotReadyState);
  }

  private void setNotReadyState(V1PodStatus status) {
    status.phase("Idle");
  }

  @Test
  void updateOncePodsAreReady() {
    forEachServer(this::expectQueryAndReturnOldConfiguration);
    forEachServer(this::expectConfigurationUpdate);
    setNotReadyState(MANAGED_SERVER1);
    setNotReadyState(MANAGED_SERVER3);

    testSupport.schedule(() -> setReadyState(MANAGED_SERVER1), 1, TimeUnit.SECONDS);
    testSupport.schedule(() -> setReadyState(MANAGED_SERVER3), 1, TimeUnit.SECONDS);
    testSupport.runSteps(MonitorExporterSteps.updateExporterSidecars());
    testSupport.setTime(3, TimeUnit.SECONDS);

    assertThat(getServersUpdated(), containsInAnyOrder(POD_NODE1, POD_NODE2, POD_NODE3));
  }

  @SuppressWarnings("SameParameterValue")
  private void setReadyState(String serverName) {
    Optional.ofNullable(info.getServerPod(serverName)).map(V1Pod::getStatus).ifPresent(this::setReady);
  }

  @Test
  void whenNoExporterConfiguration_skipProcessing() {
    configureDomain(domain).withMonitoringExporterConfiguration(null);
    forEachServer(this::expectQueryAndReturnOldConfiguration);
    forEachServer(this::expectConfigurationUpdate);

    testSupport.runSteps(MonitorExporterSteps.updateExporterSidecars());

    assertThat(getServersUpdated(), empty());
  }

}
