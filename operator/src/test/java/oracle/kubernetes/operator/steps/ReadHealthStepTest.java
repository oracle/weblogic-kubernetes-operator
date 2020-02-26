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
import io.kubernetes.client.openapi.models.V1Service;
import io.kubernetes.client.openapi.models.V1ServicePort;
import io.kubernetes.client.openapi.models.V1ServiceSpec;
import oracle.kubernetes.operator.ProcessingConstants;
import oracle.kubernetes.operator.helpers.DomainPresenceInfo;
import oracle.kubernetes.operator.helpers.KubernetesVersion;
import oracle.kubernetes.operator.http.HttpClient;
import oracle.kubernetes.operator.http.HttpClientStub;
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
  private static final ClassCastException CLASSCAST_EXCEPTION = new ClassCastException("");
  private static final WlsDomainConfigSupport configSupport =
      new WlsDomainConfigSupport(DOMAIN_NAME)
          .withWlsServer(ADMIN_NAME, ADMIN_PORT_NUM)
          .withWlsServer(MANAGED_SERVER1, MANAGED_SERVER1_PORT_NUM)
          .withAdminServerName(ADMIN_NAME);
  V1Service service;
  Step next;
  HttpClientStub httpClientStub;
  ReadHealthWithHttpClientStep withHttpClientStep;
  private List<LogRecord> logRecords = new ArrayList<>();
  private Memento consoleControl;

  /**
   * Setup test.
   */
  @Before
  public void setup() {
    consoleControl =
        TestUtils.silenceOperatorLogger()
            .collectLogMessages(logRecords, LOG_KEYS)
            .ignoringLoggedExceptions(CLASSCAST_EXCEPTION)
            .withLogLevel(Level.FINE);
    service = Stub.createStub(V1ServiceStub.class);
    next = new MockStep(null);
    httpClientStub = Stub.createStub(HttpClientStub.class);
    withHttpClientStep = new ReadHealthWithHttpClientStep(service, null, next);
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
            .withGetKeyThrowsException(true);

    withHttpClientStep.apply(packet);

    assertThat(logRecords, containsInfo(WLS_HEALTH_READ_FAILED, ADMIN_NAME));
  }

  @Test
  public void withHttpClientStep_logIfMissingHttpClient() {
    Packet packet =
        Stub.createStub(PacketStub.class).withServerName(ADMIN_NAME).withGetKeyReturnValue(null);
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
            .withGetKeyReturnValue(httpClientStub);
    packet.put(
        ProcessingConstants.SERVER_HEALTH_MAP, new ConcurrentHashMap<String, ServerHealth>());
    packet.put(ProcessingConstants.REMAINING_SERVERS_HEALTH_TO_READ, new AtomicInteger(1));

    withHttpClientStep.apply(packet);

    assertThat(
        ((AtomicInteger) packet.get(ProcessingConstants.REMAINING_SERVERS_HEALTH_TO_READ)).get(),
        is(0));
  }

  @Test
  public void withHttpClientStep_decrementRemainingServerHealthReadInMultipleClonedPackets() {
    Packet packet = new Packet();
    packet.put(ProcessingConstants.DOMAIN_TOPOLOGY, configSupport.createDomainConfig());
    packet.put(HttpClient.KEY, httpClientStub);
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

    Packet packet2 = packet.clone();
    packet2.put(ProcessingConstants.SERVER_NAME, MANAGED_SERVER1);
    withHttpClientStep2.apply(packet2);

    assertThat(
        ((AtomicInteger) packet.get(ProcessingConstants.REMAINING_SERVERS_HEALTH_TO_READ)).get(),
        is(0));
  }

  @Test
  public void withHttpClientStep_verifyOkServerHealthAddedToPacket() {
    httpClientStub.withResponse(OK_RESPONSE);

    Packet packet = createPacketForTest();

    withHttpClientStep.apply(packet);

    Map<String, ServerHealth> serverHealthMap =
        packet.getValue(ProcessingConstants.SERVER_HEALTH_MAP);
    ServerHealth serverHealth = serverHealthMap.get(MANAGED_SERVER1);
    assertThat(serverHealth.getOverallHealth(), is("ok"));
    Map<String, String> serverStateMap = packet.getValue(SERVER_STATE_MAP);
    assertThat(serverStateMap.get(MANAGED_SERVER1), is("RUNNING"));
  }

  @Test
  public void withHttpClientStep_verifyServerHealthForServerOverloadedAddedToPacket() {
    httpClientStub.withStatus(500).withSuccessful(false);

    Packet packet = createPacketForTest();

    withHttpClientStep.apply(packet);

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
    httpClientStub.withStatus(404).withSuccessful(false);

    Packet packet = createPacketForTest();

    withHttpClientStep.apply(packet);

    Map<String, ServerHealth> serverHealthMap =
        packet.getValue(ProcessingConstants.SERVER_HEALTH_MAP);
    ServerHealth serverHealth = serverHealthMap.get(MANAGED_SERVER1);
    assertThat(serverHealth.getOverallHealth(), is(ReadHealthStep.OVERALL_HEALTH_NOT_AVAILABLE));
    Map<String, String> serverStateMap = packet.getValue(SERVER_STATE_MAP);
    assertThat(serverStateMap.get(MANAGED_SERVER1), is("UNKNOWN"));
  }

  Packet createPacketForTest() {
    Packet packet =
        Stub.createStub(PacketStub.class)
            .withServerName(MANAGED_SERVER1)
            .withGetKeyReturnValue(httpClientStub);
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
    boolean getKeyThrowsException;
    HttpClient getKeyReturnValue;

    PacketStub withGetKeyThrowsException(boolean getKeyThrowsException) {
      this.getKeyThrowsException = getKeyThrowsException;
      return this;
    }

    PacketStub withGetKeyReturnValue(HttpClient getKeyReturnValue) {
      this.getKeyReturnValue = getKeyReturnValue;
      return this;
    }

    PacketStub withServerName(String serverName) {
      this.serverName = serverName;
      return this;
    }

    @Override
    public Object get(Object key) {
      if (HttpClient.KEY.equals(key)) {
        if (getKeyThrowsException) {
          throw CLASSCAST_EXCEPTION; // to go to catch clause in WithHttpClientStep.apply() method
        }
        return getKeyReturnValue;
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
