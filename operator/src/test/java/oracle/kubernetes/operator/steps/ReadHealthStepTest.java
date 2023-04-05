// Copyright (c) 2018, 2023, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.steps;

import java.net.http.HttpRequest;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import javax.annotation.Nonnull;

import com.meterware.httpunit.Base64;
import com.meterware.simplestub.Memento;
import io.kubernetes.client.openapi.models.V1Service;
import io.kubernetes.client.openapi.models.V1ServiceBuilder;
import oracle.kubernetes.operator.DomainProcessorTestSetup;
import oracle.kubernetes.operator.helpers.DomainPresenceInfo;
import oracle.kubernetes.operator.helpers.KubernetesTestSupport;
import oracle.kubernetes.operator.http.client.HttpAsyncTestSupport;
import oracle.kubernetes.operator.http.client.HttpResponseStub;
import oracle.kubernetes.operator.tuning.TuningParametersStub;
import oracle.kubernetes.operator.utils.WlsDomainConfigSupport;
import oracle.kubernetes.operator.wlsconfig.NetworkAccessPoint;
import oracle.kubernetes.operator.wlsconfig.WlsDomainConfig;
import oracle.kubernetes.operator.wlsconfig.WlsServerConfig;
import oracle.kubernetes.operator.work.Packet;
import oracle.kubernetes.operator.work.Step;
import oracle.kubernetes.operator.work.TerminalStep;
import oracle.kubernetes.utils.SystemClockTestSupport;
import oracle.kubernetes.utils.TestUtils;
import oracle.kubernetes.weblogic.domain.model.DomainResource;
import oracle.kubernetes.weblogic.domain.model.ServerHealth;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static com.meterware.simplestub.Stub.createStub;
import static oracle.kubernetes.common.logging.MessageKeys.WLS_HEALTH_READ_FAILED;
import static oracle.kubernetes.common.logging.MessageKeys.WLS_HEALTH_READ_FAILED_NO_HTTPCLIENT;
import static oracle.kubernetes.operator.LabelConstants.CLUSTERNAME_LABEL;
import static oracle.kubernetes.operator.ProcessingConstants.DOMAIN_TOPOLOGY;
import static oracle.kubernetes.operator.ProcessingConstants.REMAINING_SERVERS_HEALTH_TO_READ;
import static oracle.kubernetes.operator.ProcessingConstants.SERVER_HEALTH_MAP;
import static oracle.kubernetes.operator.ProcessingConstants.SERVER_NAME;
import static oracle.kubernetes.operator.ProcessingConstants.SERVER_STATE_MAP;
import static oracle.kubernetes.operator.http.client.HttpAsyncTestSupport.OK_RESPONSE;
import static oracle.kubernetes.operator.http.client.HttpAsyncTestSupport.createExpectedRequest;
import static oracle.kubernetes.operator.steps.ReadHealthStep.OVERALL_HEALTH_FOR_SERVER_OVERLOADED;
import static oracle.kubernetes.operator.steps.ReadHealthStep.OVERALL_HEALTH_NOT_AVAILABLE;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;

class ReadHealthStepTest {
  // The log messages to be checked during this test
  private static final String[] LOG_KEYS = {
      WLS_HEALTH_READ_FAILED, WLS_HEALTH_READ_FAILED_NO_HTTPCLIENT
  };
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

  private static final ClassCastException CLASSCAST_EXCEPTION = new ClassCastException("");
  private final List<LogRecord> logRecords = new ArrayList<>();
  private final List<Memento> mementos = new ArrayList<>();
  private final KubernetesTestSupport testSupport = new KubernetesTestSupport();
  private final HttpAsyncTestSupport httpSupport = new HttpAsyncTestSupport();
  private final TerminalStep terminalStep = new TerminalStep();
  private final Step readHealthStep = ReadHealthStep.createReadHealthStep(terminalStep);
  private final Map<String, ServerHealth> serverHealthMap = new HashMap<>();
  private final Map<String, String> serverStateMap = new HashMap<>();
  private final DomainResource domain = DomainProcessorTestSetup.createTestDomain();
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
        .ignoringLoggedExceptions(CLASSCAST_EXCEPTION)
        .withLogLevel(Level.FINE));
    mementos.add(testSupport.install());
    mementos.add(httpSupport.install());
    mementos.add(SystemClockTestSupport.installClock());
    mementos.add(TuningParametersStub.install());

    testSupport.addDomainPresenceInfo(info);
    testSupport.addToPacket(SERVER_HEALTH_MAP, serverHealthMap);
    testSupport.addToPacket(SERVER_STATE_MAP, serverStateMap);
    testSupport.addToPacket(DOMAIN_TOPOLOGY, configSupport.createDomainConfig());
    testSupport.addToPacket(REMAINING_SERVERS_HEALTH_TO_READ, new AtomicInteger(1));

    DomainProcessorTestSetup.defineSecretData(testSupport);
  }

  private int getRemainingServersToRead(Packet packet) {
    return ((AtomicInteger) packet.get(REMAINING_SERVERS_HEALTH_TO_READ)).get();
  }

  private V1Service createService(String serverName) {
    return new V1ServiceBuilder().withNewMetadata().withName(serverName).withNamespace("Test").endMetadata().build();
  }

  private V1Service selectServer(String serverName) {
    V1Service service = createService(serverName);
    testSupport.addToPacket(SERVER_NAME, serverName);
    info.setServerService(serverName, service);
    return service;
  }

  @AfterEach
  public void tearDown() {
    mementos.forEach(Memento::revert);
  }

  @Test
  void whenReadAdminServerHealth_decrementRemainingServers() {
    selectServer(ADMIN_NAME);
    defineResponse(200, OK_RESPONSE, "http://" + ADMIN_NAME + ".Test.svc:3456");

    Packet packet = testSupport.runSteps(readHealthStep);

    assertThat(getRemainingServersToRead(packet), equalTo(0));
  }

  private void defineResponse(int status, String body, @Nonnull String url) {
    httpSupport.defineResponse(
        createExpectedRequest(url),
        createStub(HttpResponseStub.class, status, body));
  }


  @Test
  void whenReadConfiguredManagedServerHealth_decrementRemainingServers() {
    V1Service service = selectServer(CONFIGURED_MANAGED_SERVER1);
    configureServiceWithClusterName(CONFIGURED_CLUSTER_NAME, service);
    defineResponse(200, OK_RESPONSE, "http://" + CONFIGURED_MANAGED_SERVER1 + ".Test.svc:7001");

    Packet packet = testSupport.runSteps(readHealthStep);

    assertThat(getRemainingServersToRead(packet), equalTo(0));
  }

  // todo test that correct IP address used for each server
  // todo test failure to read credentials

  @Test
  void whenReadDynamicManagedServerHealth_decrementRemainingServers() {
    V1Service service = selectServer(DYNAMIC_MANAGED_SERVER1);
    configureServiceWithClusterName(DYNAMIC_CLUSTER_NAME, service);
    defineResponse(200, OK_RESPONSE, "http://" + DYNAMIC_MANAGED_SERVER1 + ".Test.svc:7001");

    Packet packet = testSupport.runSteps(readHealthStep);

    assertThat(getRemainingServersToRead(packet), equalTo(0));
  }

  @Test
  void whenServerRunning_verifyServerHealth() {
    selectServer(MANAGED_SERVER1);

    defineResponse(200, OK_RESPONSE, "http://" + MANAGED_SERVER1 + ".Test.svc:8001");

    Packet packet = testSupport.runSteps(readHealthStep);

    assertThat(getServerHealthMap(packet).get(MANAGED_SERVER1).getOverallHealth(), equalTo("ok"));
    assertThat(getServerStateMap(packet).get(MANAGED_SERVER1), is("RUNNING"));
  }

  private Map<String, ServerHealth> getServerHealthMap(Packet packet) {
    return packet.getValue(SERVER_HEALTH_MAP);
  }

  private Map<String, String> getServerStateMap(Packet packet) {
    return packet.getValue(SERVER_STATE_MAP);
  }

  @Test
  void whenAdminPodIPNull_verifyServerHealth() {
    selectServer(ADMIN_NAME);
    defineResponse(200, OK_RESPONSE, "http://admin-server.Test.svc:3456");

    Packet packet = testSupport.runSteps(readHealthStep);

    assertThat(getServerStateMap(packet).get(ADMIN_NAME), is("RUNNING"));
  }

  @Test
  void whenAdminPodIPNull_requestSendWithCredentials() {
    selectServer(ADMIN_NAME);
    defineResponse(200, OK_RESPONSE, "http://admin-server.Test.svc:3456");

    testSupport.runSteps(readHealthStep);

    assertThat(hasAuthenticationCredentials(httpSupport.getLastRequest()), is(true));
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
  void whenServerOverloaded_verifyServerHealth() {
    selectServer(MANAGED_SERVER1);

    defineResponse(500, "", "http://" + MANAGED_SERVER1 + ".Test.svc:8001");

    Packet packet = testSupport.runSteps(readHealthStep);

    assertThat(getServerHealthMap(packet).get(MANAGED_SERVER1).getOverallHealth(),
        equalTo(OVERALL_HEALTH_FOR_SERVER_OVERLOADED));
    assertThat(getServerStateMap(packet).get(MANAGED_SERVER1), is("UNKNOWN"));
  }

  @Test
  void whenUnableToReadHealth_verifyNotAvailable() {
    selectServer(MANAGED_SERVER1);

    defineResponse(404, "", "http://" + MANAGED_SERVER1 + ".Test.svc:8001");

    Packet packet = testSupport.runSteps(readHealthStep);

    assertThat(getServerHealthMap(packet).get(MANAGED_SERVER1).getOverallHealth(),
        equalTo(OVERALL_HEALTH_NOT_AVAILABLE));
    assertThat(getServerStateMap(packet).get(MANAGED_SERVER1), is("UNKNOWN"));
  }

  @Test
  void whenServerConfiguredWithServerListenPortOnly_readHealthUsingServerListenPort() {
    V1Service service = selectServer(DYNAMIC_MANAGED_SERVER2);
    configureServiceWithClusterName(DYNAMIC_CLUSTER_NAME, service);
    WlsServerConfig server = getDynamicClusterServer2();
    server.setListenPort(8001);
    defineExpectedURLInResponse("http", 8001);

    Packet packet = testSupport.runSteps(readHealthStep);

    assertThat(readServerHealthSucceeded(packet), equalTo(true));
  }

  @Test
  void whenServerConfiguredWithSSLPortOnly_readHealthUsingSSLPort() {
    V1Service service = selectServer(DYNAMIC_MANAGED_SERVER2);
    configureServiceWithClusterName(DYNAMIC_CLUSTER_NAME, service);
    WlsServerConfig server = getDynamicClusterServer2();
    server.setSslListenPort(7002);
    defineExpectedURLInResponse("https", 7002);

    Packet packet = testSupport.runSteps(readHealthStep);

    assertThat(readServerHealthSucceeded(packet), equalTo(true));
  }

  @Test
  void whenServerConfiguredWithServerListenPortAndSSLPort_readHealthUsingSSLPort() {
    V1Service service = selectServer(DYNAMIC_MANAGED_SERVER2);
    configureServiceWithClusterName(DYNAMIC_CLUSTER_NAME, service);
    WlsServerConfig server = getDynamicClusterServer2();
    server.setListenPort(8001);
    server.setSslListenPort(7002);
    defineExpectedURLInResponse("https", 7002);

    Packet packet = testSupport.runSteps(readHealthStep);

    assertThat(readServerHealthSucceeded(packet), equalTo(true));
  }

  @Test
  void whenServerConfiguredWithServerListenPortAndNonAdminNAP_readHealthUsingServerListenPort() {
    V1Service service = selectServer(DYNAMIC_MANAGED_SERVER2);
    configureServiceWithClusterName(DYNAMIC_CLUSTER_NAME, service);
    WlsServerConfig server = getDynamicClusterServer2();
    server.setListenPort(8001);
    server.addNetworkAccessPoint(new NetworkAccessPoint("nap1", "t3", 9001, 9001));
    defineExpectedURLInResponse("http", 8001);

    Packet packet = testSupport.runSteps(readHealthStep);

    assertThat(readServerHealthSucceeded(packet), equalTo(true));
  }

  @Test
  void whenServerConfiguredWithServerSSLPortAndNonAdminNAP_readHealthUsingSSLPort() {
    V1Service service = selectServer(DYNAMIC_MANAGED_SERVER2);
    configureServiceWithClusterName(DYNAMIC_CLUSTER_NAME, service);
    WlsServerConfig server = getDynamicClusterServer2();
    server.setSslListenPort(7002);
    server.addNetworkAccessPoint(new NetworkAccessPoint("nap1", "t3", 9001, 9001));
    defineExpectedURLInResponse("https", 7002);

    Packet packet = testSupport.runSteps(readHealthStep);

    assertThat(readServerHealthSucceeded(packet), equalTo(true));
  }

  @Test
  void whenServerConfiguredWithSSLPortAndAdminNAP_readHealthUsingAdminNAPPort() {
    V1Service service = selectServer(DYNAMIC_MANAGED_SERVER2);
    configureServiceWithClusterName(DYNAMIC_CLUSTER_NAME, service);
    WlsServerConfig server = getDynamicClusterServer2();
    server.setSslListenPort(7002);
    server.addNetworkAccessPoint(new NetworkAccessPoint("admin", "admin", 8888, 8888));
    defineExpectedURLInResponse("https", 8888);

    Packet packet = testSupport.runSteps(readHealthStep);

    assertThat(readServerHealthSucceeded(packet), equalTo(true));
  }

  @Test
  void whenServerConfiguredWithServerListenPortAndAdminNAP_readHealthUsingAdminNAPPort() {
    V1Service service = selectServer(DYNAMIC_MANAGED_SERVER2);
    configureServiceWithClusterName(DYNAMIC_CLUSTER_NAME, service);
    WlsServerConfig server = getDynamicClusterServer2();
    server.setListenPort(8001);
    server.setListenPort(null);
    server.addNetworkAccessPoint(new NetworkAccessPoint("nap1", "admin", 8888, 8888));
    defineExpectedURLInResponse("https", 8888);

    Packet packet = testSupport.runSteps(readHealthStep);

    assertThat(readServerHealthSucceeded(packet), equalTo(true));
  }

  @Test
  void whenServerConfiguredWithNonAdminNAPOnly_readHealthFailed() {
    V1Service service = selectServer(DYNAMIC_MANAGED_SERVER2);
    configureServiceWithClusterName(DYNAMIC_CLUSTER_NAME, service);
    WlsServerConfig server = getDynamicClusterServer2();
    server.addNetworkAccessPoint(new NetworkAccessPoint("nap1", "t3", 9001, 9001));
    defineExpectedURLInResponse("http", 9001);

    Packet packet = testSupport.runSteps(readHealthStep);

    assertThat(readServerHealthSucceeded(packet), equalTo(false));
  }

  @Test
  void whenAuthorizedToReadHealth_verifySecretSet() {
    selectServer(MANAGED_SERVER1);

    defineResponse(200, OK_RESPONSE, "http://127.0.0.1:8001");

    testSupport.runSteps(readHealthStep);

    assertThat(info.getWebLogicCredentialsSecret(), is(notNullValue()));
  }

  @Test
  void whenAuthorizedToReadHealthAndThenWait_verifySecretCleared() {
    selectServer(MANAGED_SERVER1);

    defineResponse(200, OK_RESPONSE, "http://127.0.0.1:8001");

    testSupport.runSteps(readHealthStep);

    assertThat(info.getWebLogicCredentialsSecret(), is(notNullValue()));

    SystemClockTestSupport.increment(180);

    assertThat(info.getWebLogicCredentialsSecret(), is(nullValue()));
  }

  @Test
  void whenNotAuthorizedToReadHealth_verifySecretCleared() {
    selectServer(MANAGED_SERVER1);

    defineResponse(403, "", "http://" + MANAGED_SERVER1 + ".Test.svc:8001");

    testSupport.runSteps(readHealthStep);

    assertThat(info.getWebLogicCredentialsSecret(), is(nullValue()));
  }

  private void defineExpectedURLInResponse(String protocol, int port) {
    defineResponse(200, OK_RESPONSE, protocol + "://dyn-managed-server2.Test.svc:" + port);
  }

  private boolean readServerHealthSucceeded(Packet packet) {
    return getRemainingServersToRead(packet) == 0;
  }

  private WlsServerConfig getDynamicClusterServer2() {
    WlsDomainConfig wlsDomainConfig = (WlsDomainConfig) testSupport.getPacket().get(DOMAIN_TOPOLOGY);
    return wlsDomainConfig.getClusterConfig(DYNAMIC_CLUSTER_NAME).getServerConfigs().get(1);
  }

  private void configureServiceWithClusterName(String clusterName, V1Service service) {
    service.getMetadata().putLabelsItem(CLUSTERNAME_LABEL, clusterName);
  }

}
