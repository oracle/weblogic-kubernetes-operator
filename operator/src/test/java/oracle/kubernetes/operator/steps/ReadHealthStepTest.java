// Copyright (c) 2018, 2020, Oracle Corporation and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.steps;

import java.net.URI;
import java.net.http.HttpRequest;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.LogRecord;

import com.meterware.simplestub.Memento;
import io.kubernetes.client.openapi.models.V1ObjectMeta;
import io.kubernetes.client.openapi.models.V1Secret;
import io.kubernetes.client.openapi.models.V1Service;
import io.kubernetes.client.openapi.models.V1ServicePort;
import io.kubernetes.client.openapi.models.V1ServiceSpec;
import oracle.kubernetes.operator.DomainProcessorTestSetup;
import oracle.kubernetes.operator.helpers.DomainPresenceInfo;
import oracle.kubernetes.operator.helpers.KubernetesTestSupport;
import oracle.kubernetes.operator.http.HttpAsyncTestSupport;
import oracle.kubernetes.operator.http.HttpResponseStub;
import oracle.kubernetes.operator.utils.WlsDomainConfigSupport;
import oracle.kubernetes.operator.work.Packet;
import oracle.kubernetes.operator.work.Step;
import oracle.kubernetes.operator.work.TerminalStep;
import oracle.kubernetes.utils.TestUtils;
import oracle.kubernetes.weblogic.domain.model.Domain;
import oracle.kubernetes.weblogic.domain.model.ServerHealth;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import static com.meterware.simplestub.Stub.createStub;
import static oracle.kubernetes.operator.DomainProcessorTestSetup.NS;
import static oracle.kubernetes.operator.DomainProcessorTestSetup.SECRET_NAME;
import static oracle.kubernetes.operator.LabelConstants.CLUSTERNAME_LABEL;
import static oracle.kubernetes.operator.ProcessingConstants.DOMAIN_TOPOLOGY;
import static oracle.kubernetes.operator.ProcessingConstants.REMAINING_SERVERS_HEALTH_TO_READ;
import static oracle.kubernetes.operator.ProcessingConstants.SERVER_HEALTH_MAP;
import static oracle.kubernetes.operator.ProcessingConstants.SERVER_NAME;
import static oracle.kubernetes.operator.ProcessingConstants.SERVER_STATE_MAP;
import static oracle.kubernetes.operator.helpers.SecretHelper.ADMIN_SERVER_CREDENTIALS_PASSWORD;
import static oracle.kubernetes.operator.helpers.SecretHelper.ADMIN_SERVER_CREDENTIALS_USERNAME;
import static oracle.kubernetes.operator.logging.MessageKeys.WLS_HEALTH_READ_FAILED;
import static oracle.kubernetes.operator.logging.MessageKeys.WLS_HEALTH_READ_FAILED_NO_HTTPCLIENT;
import static oracle.kubernetes.operator.steps.ReadHealthStep.OVERALL_HEALTH_FOR_SERVER_OVERLOADED;
import static oracle.kubernetes.operator.steps.ReadHealthStep.OVERALL_HEALTH_NOT_AVAILABLE;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;

public class ReadHealthStepTest {
  static final String OK_RESPONSE =
      "{\n"
          + "    \"overallHealthState\": {\n"
          + "        \"state\": \"ok\",\n"
          + "        \"subsystemName\": null,\n"
          + "        \"partitionName\": null,\n"
          + "        \"symptoms\": []\n"
          + "    },\n"
          + "    \"state\": \"RUNNING\",\n"
          + "    \"activationTime\": 1556759105378\n"
          + "}";
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
  private static final ClassCastException CLASSCAST_EXCEPTION = new ClassCastException("");
  private static final WlsDomainConfigSupport configSupport =
      new WlsDomainConfigSupport(DOMAIN_NAME)
          .withWlsServer(ADMIN_NAME, ADMIN_PORT_NUM)
          .withWlsServer(MANAGED_SERVER1, MANAGED_SERVER1_PORT_NUM)
          .withWlsCluster(CONFIGURED_CLUSTER_NAME, CONFIGURED_MANAGED_SERVER1)
          .withDynamicWlsCluster(DYNAMIC_CLUSTER_NAME, DYNAMIC_MANAGED_SERVER1)
          .withAdminServerName(ADMIN_NAME);
  private V1Service service = createStub(V1ServiceStub.class);
  private List<LogRecord> logRecords = new ArrayList<>();
  private List<Memento> mementos = new ArrayList<>();
  private KubernetesTestSupport testSupport = new KubernetesTestSupport();
  private HttpAsyncTestSupport httpSupport = new HttpAsyncTestSupport();
  private TerminalStep terminalStep = new TerminalStep();
  private Step readHealthStep = ReadHealthStep.createReadHealthStep(terminalStep);
  private Map<String, ServerHealth> serverHealthMap = new HashMap<>();
  private Map<String, String> serverStateMap = new HashMap<>();
  private Domain domain = DomainProcessorTestSetup.createTestDomain();
  private final DomainPresenceInfo info = new DomainPresenceInfo(domain);

  /**
   * Javadoc to make the stupid style checker happy, probably because it throws an exception.
   * @throws NoSuchFieldException it's not gonna happen, OK?
   */
  @Before
  public void setup() throws NoSuchFieldException {
    mementos.add(TestUtils.silenceOperatorLogger()
            .collectLogMessages(logRecords, LOG_KEYS)
            .ignoringLoggedExceptions(CLASSCAST_EXCEPTION)
            .withLogLevel(Level.FINE));
    mementos.add(testSupport.install());
    mementos.add(httpSupport.install());

    testSupport.addDomainPresenceInfo(info);
    testSupport.addToPacket(SERVER_HEALTH_MAP, serverHealthMap);
    testSupport.addToPacket(SERVER_STATE_MAP, serverStateMap);
    testSupport.addToPacket(DOMAIN_TOPOLOGY, configSupport.createDomainConfig());
    testSupport.addToPacket(REMAINING_SERVERS_HEALTH_TO_READ, new AtomicInteger(1));

    defineSecretData();
  }

  private int getRemainingServersToRead(Packet packet) {
    return ((AtomicInteger) packet.get(REMAINING_SERVERS_HEALTH_TO_READ)).get();
  }

  private void selectServer(String serverName) {
    testSupport.addToPacket(SERVER_NAME, serverName);
    info.setServerService(serverName, service);
  }

  private void defineSecretData() {
    testSupport.defineResources(
                new V1Secret()
                      .metadata(new V1ObjectMeta().namespace(NS).name(SECRET_NAME))
                      .data(Map.of(ADMIN_SERVER_CREDENTIALS_USERNAME, "user".getBytes(),
                                   ADMIN_SERVER_CREDENTIALS_PASSWORD, "password".getBytes())));
  }

  @After
  public void tearDown() {
    mementos.forEach(Memento::revert);
  }
  
  @Test
  public void whenReadAdminServerHealth_decrementRemainingServers() {
    selectServer(ADMIN_NAME);
    defineResponse(200, "");

    Packet packet = testSupport.runSteps(readHealthStep);

    assertThat(getRemainingServersToRead(packet), equalTo(0));
  }

  private void defineResponse(int status, String body) {
    httpSupport.defineResponse(createExpectedRequest(), createStub(HttpResponseStub.class, status, body));
  }

  private HttpRequest createExpectedRequest() {
    return HttpRequest.newBuilder()
          .uri(URI.create("https://127.0.0.1:7001/management/weblogic/latest/serverRuntime/search"))
          .POST(HttpRequest.BodyPublishers.noBody())
          .build();
  }

  @Test
  public void whenReadConfiguredManagedServerHealth_decrementRemainingServers() {
    selectServer(CONFIGURED_MANAGED_SERVER1);
    configureServiceWithClusterName(CONFIGURED_CLUSTER_NAME);
    defineResponse(200, "");

    Packet packet = testSupport.runSteps(readHealthStep);

    assertThat(getRemainingServersToRead(packet), equalTo(0));
  }

  // todo test that correct IP address used for each server
  // todo test failure to read credentials

  @Test
  public void whenReadDynamicManagedServerHealth_decrementRemainingServers() {
    selectServer(DYNAMIC_MANAGED_SERVER1);
    configureServiceWithClusterName(DYNAMIC_CLUSTER_NAME);
    defineResponse(200, "");

    Packet packet = testSupport.runSteps(readHealthStep);

    assertThat(getRemainingServersToRead(packet), equalTo(0));
  }

  @Test
  public void whenServerRunning_verifyServerHealth() {
    selectServer(MANAGED_SERVER1);

    defineResponse(200, OK_RESPONSE);

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
  public void whenServerOverloaded_verifyServerHealth() {
    selectServer(MANAGED_SERVER1);

    defineResponse(500, "");

    Packet packet = testSupport.runSteps(readHealthStep);

    assertThat(getServerHealthMap(packet).get(MANAGED_SERVER1).getOverallHealth(),
               equalTo(OVERALL_HEALTH_FOR_SERVER_OVERLOADED));
    assertThat(getServerStateMap(packet).get(MANAGED_SERVER1), is("UNKNOWN"));
  }

  @Test
  public void whenUnableToReadHealth_verifyNotAvailable() {
    selectServer(MANAGED_SERVER1);

    defineResponse(404, "");

    Packet packet = testSupport.runSteps(readHealthStep);

    assertThat(getServerHealthMap(packet).get(MANAGED_SERVER1).getOverallHealth(),
               equalTo(OVERALL_HEALTH_NOT_AVAILABLE));
    assertThat(getServerStateMap(packet).get(MANAGED_SERVER1), is("UNKNOWN"));
  }

  public abstract static class V1ServiceStub extends V1Service {

    @Override
    public V1ServiceSpec getSpec() {
      List<V1ServicePort> ports = new ArrayList<>();
      ports.add(new V1ServicePort().port(7001).name("default"));
      return new V1ServiceSpec().clusterIP("127.0.0.1").ports(ports);
    }
  }

  private void configureServiceWithClusterName(String clusterName) {
    service.setMetadata(new V1ObjectMeta().putLabelsItem(CLUSTERNAME_LABEL, clusterName));
  }

}
