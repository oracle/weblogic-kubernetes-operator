// Copyright (c) 2018, 2020, Oracle Corporation and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.steps;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.LogRecord;

import com.meterware.simplestub.Memento;
import com.meterware.simplestub.Stub;
import io.kubernetes.client.openapi.models.V1ObjectMeta;
import io.kubernetes.client.openapi.models.V1Service;
import io.kubernetes.client.openapi.models.V1ServicePort;
import io.kubernetes.client.openapi.models.V1ServiceSpec;
import oracle.kubernetes.operator.ProcessingConstants;
import oracle.kubernetes.operator.helpers.DomainPresenceInfo;
import oracle.kubernetes.operator.helpers.KubernetesVersion;
import oracle.kubernetes.operator.http.Result;
import oracle.kubernetes.operator.http.ResultStub;
import oracle.kubernetes.operator.steps.ReadHealthStep.ProcessResponseFromHttpClientStep;
import oracle.kubernetes.operator.steps.ReadHealthStep.ReadHealthWithHttpClientStep;
import oracle.kubernetes.operator.utils.WlsDomainConfigSupport;
import oracle.kubernetes.operator.work.Component;
import oracle.kubernetes.operator.work.NextAction;
import oracle.kubernetes.operator.work.Packet;
import oracle.kubernetes.operator.work.Step;
import oracle.kubernetes.utils.TestUtils;
import oracle.kubernetes.weblogic.domain.model.ServerHealth;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import static oracle.kubernetes.operator.LabelConstants.CLUSTERNAME_LABEL;
import static oracle.kubernetes.operator.ProcessingConstants.SERVER_STATE_MAP;
import static oracle.kubernetes.operator.logging.MessageKeys.WLS_HEALTH_READ_FAILED;
import static oracle.kubernetes.operator.logging.MessageKeys.WLS_HEALTH_READ_FAILED_NO_HTTPCLIENT;
import static oracle.kubernetes.utils.LogMatcher.containsInfo;
import static org.hamcrest.MatcherAssert.assertThat;
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
  private static final String NAMESPACE = "testnamespace";
  private static final String DOMAIN_UID = "domain-uid";
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
  V1Service service;
  Step next;
  String encodedCredentialsStub = "test";
  ResultStub resultStub;
  ReadHealthWithHttpClientStep withHttpClientStep;
  ProcessResponseFromHttpClientStep processResponseFromJavaHttpClientStep;
  private List<LogRecord> logRecords = new ArrayList<>();
  private Memento consoleControl;

  /** Setup test. */
  @Before
  public void setup() {
    consoleControl =
        TestUtils.silenceOperatorLogger()
            .collectLogMessages(logRecords, LOG_KEYS)
            .ignoringLoggedExceptions(CLASSCAST_EXCEPTION)
            .withLogLevel(Level.FINE);
    service = Stub.createStub(V1ServiceStub.class);
    next = new MockStep(null);
    resultStub = Stub.createStub(ResultStub.class);
    withHttpClientStep = new ReadHealthWithHttpClientStep(service, null, next);
    processResponseFromJavaHttpClientStep = new ProcessResponseFromHttpClientStep(null);
  }

  @After
  public void tearDown() {
    consoleControl.revert();
  }

  @Test
  public void withHttpClientStep_Health_logIfFailed() {
    Packet packet =
        Stub.createStub(PacketStub.class)
            .withServerName(ADMIN_NAME)
            .withGetKeyThrowsException(true)
            .withEncodedCredentials(encodedCredentialsStub)
            .withGetKeyReturnValue(resultStub);

    withHttpClientStep.apply(packet);
    processResponseFromJavaHttpClientStep.apply(packet);

    assertThat(logRecords, containsInfo(WLS_HEALTH_READ_FAILED, ADMIN_NAME));
  }

  @Test
  public void withHttpClientStep_logIfMissingHttpClient() {
    Packet packet =
        Stub.createStub(PacketStub.class).withServerName(ADMIN_NAME).withEncodedCredentials(null);
    packet.put(ProcessingConstants.REMAINING_SERVERS_HEALTH_TO_READ, new AtomicInteger(1));

    withHttpClientStep.apply(packet);

    assertThat(logRecords, containsInfo(WLS_HEALTH_READ_FAILED_NO_HTTPCLIENT, ADMIN_NAME));
    assertThat(
        ((AtomicInteger) packet.get(ProcessingConstants.REMAINING_SERVERS_HEALTH_TO_READ)).get(),
        is(1));
  }

  @Test
  public void withHttpClientStep_decrementRemainingServerHealthReadInPacketIfSucceeded() {
    Packet packet =
        Stub.createStub(PacketStub.class)
            .withServerName(ADMIN_NAME)
            .withEncodedCredentials(encodedCredentialsStub)
            .withGetKeyReturnValue(resultStub);

    packet.put(
        ProcessingConstants.SERVER_HEALTH_MAP, new ConcurrentHashMap<String, ServerHealth>());
    packet.put(ProcessingConstants.REMAINING_SERVERS_HEALTH_TO_READ, new AtomicInteger(1));

    withHttpClientStep.apply(packet);
    processResponseFromJavaHttpClientStep.apply(packet);

    assertThat(
        ((AtomicInteger) packet.get(ProcessingConstants.REMAINING_SERVERS_HEALTH_TO_READ)).get(),
        is(0));
  }

  @Test
  public void withHttpClientStep_forConfiguredServer_decrementRemainingServerHealthRead() {
    Packet packet =
        Stub.createStub(PacketStub.class)
            .withServerName(CONFIGURED_MANAGED_SERVER1)
            .withEncodedCredentials(encodedCredentialsStub)
            .withGetKeyReturnValue(resultStub);

    packet.put(
        ProcessingConstants.SERVER_HEALTH_MAP, new ConcurrentHashMap<String, ServerHealth>());
    packet.put(ProcessingConstants.REMAINING_SERVERS_HEALTH_TO_READ, new AtomicInteger(1));
    configureServiceWithClusterName(CONFIGURED_CLUSTER_NAME);

    withHttpClientStep.apply(packet);
    processResponseFromJavaHttpClientStep.apply(packet);

    assertThat(
        ((AtomicInteger) packet.get(ProcessingConstants.REMAINING_SERVERS_HEALTH_TO_READ)).get(),
        is(0));
  }

  @Test
  public void withHttpClientStep_forDynamicServer_decrementRemainingServerHealthRead() {
    Packet packet =
        Stub.createStub(PacketStub.class)
            .withServerName(DYNAMIC_MANAGED_SERVER1)
            .withEncodedCredentials(encodedCredentialsStub)
            .withGetKeyReturnValue(resultStub);

    packet.put(
        ProcessingConstants.SERVER_HEALTH_MAP, new ConcurrentHashMap<String, ServerHealth>());
    packet.put(ProcessingConstants.REMAINING_SERVERS_HEALTH_TO_READ, new AtomicInteger(1));
    configureServiceWithClusterName(DYNAMIC_CLUSTER_NAME);

    withHttpClientStep.apply(packet);
    processResponseFromJavaHttpClientStep.apply(packet);

    assertThat(
        ((AtomicInteger) packet.get(ProcessingConstants.REMAINING_SERVERS_HEALTH_TO_READ)).get(),
        is(0));
  }

  @Test
  public void withHttpClientStep_decrementRemainingServerHealthReadInMultipleClonedPackets() {
    Packet packet = new Packet();
    packet.put(ProcessingConstants.DOMAIN_TOPOLOGY, configSupport.createDomainConfig());
    packet.put(ProcessingConstants.KEY, encodedCredentialsStub);
    packet.put(
        ProcessingConstants.SERVER_HEALTH_MAP, new ConcurrentHashMap<String, ServerHealth>());
    packet.put(ProcessingConstants.REMAINING_SERVERS_HEALTH_TO_READ, new AtomicInteger(2));

    final ReadHealthWithHttpClientStep withHttpClientStep1 =
        new ReadHealthWithHttpClientStep(service, null, next);
    final ReadHealthWithHttpClientStep withHttpClientStep2 =
        new ReadHealthWithHttpClientStep(service, null, next);

    Packet packet1 = packet.clone();
    packet1.put(ProcessingConstants.SERVER_NAME, ADMIN_NAME);
    withHttpClientStep1.apply(packet1);
    packet1.put(ProcessingConstants.RESULT, new Result("", 200, true));
    processResponseFromJavaHttpClientStep.apply(packet1);

    Packet packet2 = packet.clone();
    packet2.put(ProcessingConstants.SERVER_NAME, MANAGED_SERVER1);
    packet2.put(ProcessingConstants.KEY, encodedCredentialsStub);
    withHttpClientStep2.apply(packet2);
    packet2.put(ProcessingConstants.RESULT, new Result("", 200, true));
    processResponseFromJavaHttpClientStep.apply(packet2);

    assertThat(
        ((AtomicInteger) packet.get(ProcessingConstants.REMAINING_SERVERS_HEALTH_TO_READ)).get(),
        is(0));
  }

  @Test
  public void withHttpClientStep_verifyOkServerHealthAddedToPacket() {
    Packet packet = createPacketForTest(OK_RESPONSE, 200, true);
    withHttpClientStep.apply(packet);
    processResponseFromJavaHttpClientStep.apply(packet);

    Map<String, ServerHealth> serverHealthMap =
        packet.getValue(ProcessingConstants.SERVER_HEALTH_MAP);
    ServerHealth serverHealth = serverHealthMap.get(MANAGED_SERVER1);
    assertThat(serverHealth.getOverallHealth(), is("ok"));
    Map<String, String> serverStateMap = packet.getValue(SERVER_STATE_MAP);
    assertThat(serverStateMap.get(MANAGED_SERVER1), is("RUNNING"));
  }

  @Test
  public void withHttpClientStep_verifyServerHealthForServerOverloadedAddedToPacket() {
    Packet packet = createPacketForTest("", 500, false);

    withHttpClientStep.apply(packet);
    processResponseFromJavaHttpClientStep.apply(packet);

    Map<String, ServerHealth> serverHealthMap =
        packet.getValue(ProcessingConstants.SERVER_HEALTH_MAP);
    ServerHealth serverHealth = serverHealthMap.get(MANAGED_SERVER1);
    assertThat(
        serverHealth.getOverallHealth(), is(ReadHealthStep.OVERALL_HEALTH_FOR_SERVER_OVERLOADED));
    Map<String, String> serverStateMap = packet.getValue(SERVER_STATE_MAP);
    assertThat(serverStateMap.get(MANAGED_SERVER1), is("UNKNOWN"));
  }

  @Test
  public void withHttpClientStep_verifyServerHealthForOtherErrorAddedToPacket() {
    Packet packet = createPacketForTest("", 404, false);

    withHttpClientStep.apply(packet);
    processResponseFromJavaHttpClientStep.apply(packet);

    Map<String, ServerHealth> serverHealthMap =
        packet.getValue(ProcessingConstants.SERVER_HEALTH_MAP);
    ServerHealth serverHealth = serverHealthMap.get(MANAGED_SERVER1);
    assertThat(serverHealth.getOverallHealth(), is(ReadHealthStep.OVERALL_HEALTH_NOT_AVAILABLE));
    Map<String, String> serverStateMap = packet.getValue(SERVER_STATE_MAP);
    assertThat(serverStateMap.get(MANAGED_SERVER1), is("UNKNOWN"));
  }

  Packet createPacketForTest() {
    return createPacketForTest("", 200, true);
  }

  Packet createPacketForTest(String response, int status, boolean successful) {
    Result result = new Result(response, status, successful);
    Packet packet =
        Stub.createStub(PacketStub.class)
            .withServerName(MANAGED_SERVER1)
            .withGetKeyReturnValue(result)
            .withEncodedCredentials(encodedCredentialsStub);
    packet.put(
        ProcessingConstants.SERVER_HEALTH_MAP, new ConcurrentHashMap<String, ServerHealth>());
    packet.put(ProcessingConstants.REMAINING_SERVERS_HEALTH_TO_READ, new AtomicInteger(1));
    packet.put(SERVER_STATE_MAP, new ConcurrentHashMap<String, String>());
    packet
        .getComponents()
        .put(
            ProcessingConstants.DOMAIN_COMPONENT_NAME,
            Component.createFor(
                new DomainPresenceInfo(NAMESPACE, DOMAIN_UID), KubernetesVersion.TEST_VERSION));

    return packet;
  }

  abstract static class PacketStub extends Packet {

    String serverName;
    String encodedCredentials;
    boolean getKeyThrowsException;
    Result getKeyReturnValue;

    PacketStub withGetKeyThrowsException(boolean getKeyThrowsException) {
      this.getKeyThrowsException = getKeyThrowsException;
      return this;
    }

    PacketStub withGetKeyReturnValue(Result getKeyReturnValue) {
      this.getKeyReturnValue = getKeyReturnValue;
      return this;
    }

    PacketStub withEncodedCredentials(String encodedCredentials) {
      this.encodedCredentials = encodedCredentials;
      return this;
    }

    PacketStub withServerName(String serverName) {
      this.serverName = serverName;
      return this;
    }

    @Override
    public Object get(Object key) {
      if (ProcessingConstants.RESULT.equals(key)) {
        if (getKeyThrowsException) {
          throw CLASSCAST_EXCEPTION; // to go to catch clause in WithHttpClientStep.apply() method
        }
        return getKeyReturnValue;
      } else if (ProcessingConstants.KEY.equals(key)) {
        return encodedCredentials;
      } else if (ProcessingConstants.SERVER_NAME.equals(key)) {
        return serverName;
      } else if (ProcessingConstants.DOMAIN_TOPOLOGY.equals(key)) {
        return configSupport.createDomainConfig();
      }
      return super.get(key);
    }
  }

  public abstract static class V1ServiceStub extends V1Service {

    @Override
    public V1ServiceSpec getSpec() {
      List<V1ServicePort> ports = new ArrayList<>();
      ports.add(new V1ServicePort().port(7001).name("default"));
      V1ServiceSpec v1ServiceSpec = new V1ServiceSpec().clusterIP("127.0.0.1").ports(ports);
      return v1ServiceSpec;
    }
  }

  private void configureServiceWithClusterName(String clusterName) {
    service.setMetadata(new V1ObjectMeta().putLabelsItem(CLUSTERNAME_LABEL, clusterName));
  }

  static class MockStep extends Step {
    public MockStep(Step next) {
      super(next);
    }

    @Override
    public NextAction apply(Packet packet) {
      return null;
    }
  }
}
