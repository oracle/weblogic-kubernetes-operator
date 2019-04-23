// Copyright 2018, 2019 Oracle Corporation and/or its affiliates.  All rights reserved.
// Licensed under the Universal Permissive License v 1.0 as shown at
// http://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.steps;

import static oracle.kubernetes.LogMatcher.containsInfo;
import static oracle.kubernetes.operator.logging.MessageKeys.WLS_HEALTH_READ_FAILED;
import static oracle.kubernetes.operator.logging.MessageKeys.WLS_HEALTH_READ_FAILED_NO_HTTPCLIENT;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

import com.meterware.simplestub.Memento;
import com.meterware.simplestub.Stub;
import io.kubernetes.client.models.V1Service;
import io.kubernetes.client.models.V1ServicePort;
import io.kubernetes.client.models.V1ServiceSpec;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import oracle.kubernetes.TestUtils;
import oracle.kubernetes.operator.ProcessingConstants;
import oracle.kubernetes.operator.http.HttpClient;
import oracle.kubernetes.operator.http.HttpClientStub;
import oracle.kubernetes.operator.steps.ReadHealthStep.ReadHealthWithHttpClientStep;
import oracle.kubernetes.operator.utils.WlsDomainConfigSupport;
import oracle.kubernetes.operator.work.NextAction;
import oracle.kubernetes.operator.work.Packet;
import oracle.kubernetes.operator.work.Step;
import oracle.kubernetes.weblogic.domain.model.ServerHealth;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class ReadHealthStepTest {
  // The log messages to be checked during this test
  private static final String[] LOG_KEYS = {
    WLS_HEALTH_READ_FAILED, WLS_HEALTH_READ_FAILED_NO_HTTPCLIENT
  };

  private static final String DOMAIN_NAME = "domain";
  private static final String ADMIN_NAME = "admin-server";
  private static final int ADMIN_PORT_NUM = 3456;

  private static final String MANAGED_SERVER1 = "managed-server1";
  private static final int MANAGED_SERVER1_PORT_NUM = 8001;

  private List<LogRecord> logRecords = new ArrayList<>();
  private Memento consoleControl;
  private static final ClassCastException CLASSCAST_EXCEPTION = new ClassCastException("");
  private static final WlsDomainConfigSupport configSupport =
      new WlsDomainConfigSupport(DOMAIN_NAME)
          .withWlsServer(ADMIN_NAME, ADMIN_PORT_NUM)
          .withWlsServer(MANAGED_SERVER1, MANAGED_SERVER1_PORT_NUM)
          .withAdminServerName(ADMIN_NAME);

  @Before
  public void setup() {
    consoleControl =
        TestUtils.silenceOperatorLogger()
            .collectLogMessages(logRecords, LOG_KEYS)
            .ignoringLoggedExceptions(CLASSCAST_EXCEPTION)
            .withLogLevel(Level.FINE);
  }

  @After
  public void tearDown() {
    consoleControl.revert();
  }

  @Test
  public void withHttpClientStep_Health_logIfFailed() {
    V1Service service = Stub.createStub(V1ServiceStub.class);
    Step next = new MockStep(null);
    final String SERVER_NAME = ADMIN_NAME;
    Packet packet =
        Stub.createStub(PacketStub.class)
            .withServerName(SERVER_NAME)
            .withGetKeyThrowsException(true);

    ReadHealthWithHttpClientStep withHttpClientStep =
        new ReadHealthWithHttpClientStep(service, null, next);
    withHttpClientStep.apply(packet);

    assertThat(logRecords, containsInfo(WLS_HEALTH_READ_FAILED, SERVER_NAME));
  }

  @Test
  public void withHttpClientStep_logIfMissingHTTPClient() {
    V1Service service = Stub.createStub(V1ServiceStub.class);
    Step next = new MockStep(null);
    final String SERVER_NAME = ADMIN_NAME;
    Packet packet =
        Stub.createStub(PacketStub.class).withServerName(SERVER_NAME).withGetKeyReturnValue(null);
    packet.put(ProcessingConstants.REMAINING_SERVERS_HEALTH_TO_READ, new AtomicInteger(1));

    ReadHealthWithHttpClientStep withHttpClientStep =
        new ReadHealthWithHttpClientStep(service, null, next);
    withHttpClientStep.apply(packet);

    assertThat(logRecords, containsInfo(WLS_HEALTH_READ_FAILED_NO_HTTPCLIENT, SERVER_NAME));
    assertThat(
        ((AtomicInteger) packet.get(ProcessingConstants.REMAINING_SERVERS_HEALTH_TO_READ)).get(),
        is(1));
  }

  @Test
  public void withHttpClientStep_decrementRemainingServerHealthReadInPacketIfSucceeded() {
    V1Service service = Stub.createStub(V1ServiceStub.class);
    Step next = new MockStep(null);
    final String SERVER_NAME = ADMIN_NAME;

    HttpClientStub httpClientStub = Stub.createStub(HttpClientStub.class);

    Packet packet =
        Stub.createStub(PacketStub.class)
            .withServerName(SERVER_NAME)
            .withGetKeyReturnValue(httpClientStub);
    packet.put(
        ProcessingConstants.SERVER_HEALTH_MAP, new ConcurrentHashMap<String, ServerHealth>());
    packet.put(ProcessingConstants.REMAINING_SERVERS_HEALTH_TO_READ, new AtomicInteger(1));

    ReadHealthWithHttpClientStep withHttpClientStep =
        new ReadHealthWithHttpClientStep(service, null, next);
    withHttpClientStep.apply(packet);

    assertThat(
        ((AtomicInteger) packet.get(ProcessingConstants.REMAINING_SERVERS_HEALTH_TO_READ)).get(),
        is(0));
  }

  @Test
  public void withHttpClientStep_decrementRemainingServerHealthReadInMultipleClonedPackets() {
    V1Service service = Stub.createStub(V1ServiceStub.class);
    Step next = new MockStep(null);

    HttpClientStub httpClientStub = Stub.createStub(HttpClientStub.class);

    Packet packet = new Packet();
    packet.put(ProcessingConstants.DOMAIN_TOPOLOGY, configSupport.createDomainConfig());
    packet.put(HttpClient.KEY, httpClientStub);
    packet.put(
        ProcessingConstants.SERVER_HEALTH_MAP, new ConcurrentHashMap<String, ServerHealth>());
    packet.put(ProcessingConstants.REMAINING_SERVERS_HEALTH_TO_READ, new AtomicInteger(2));

    ReadHealthWithHttpClientStep withHttpClientStep1 =
        new ReadHealthWithHttpClientStep(service, null, next);
    ReadHealthWithHttpClientStep withHttpClientStep2 =
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
