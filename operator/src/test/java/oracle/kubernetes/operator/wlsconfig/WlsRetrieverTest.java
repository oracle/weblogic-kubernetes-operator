// Copyright 2018, Oracle Corporation and/or its affiliates.  All rights reserved.
// Licensed under the Universal Permissive License v 1.0 as shown at
// http://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.wlsconfig;

import static oracle.kubernetes.LogMatcher.containsFine;
import static oracle.kubernetes.LogMatcher.containsWarning;
import static oracle.kubernetes.operator.logging.MessageKeys.WLS_CONFIGURATION_READ_FAILED;
import static oracle.kubernetes.operator.logging.MessageKeys.WLS_HEALTH_READ_FAILED;
import static org.hamcrest.MatcherAssert.assertThat;

import com.meterware.simplestub.Memento;
import com.meterware.simplestub.Stub;
import io.kubernetes.client.models.V1Service;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import oracle.kubernetes.TestUtils;
import oracle.kubernetes.operator.ProcessingConstants;
import oracle.kubernetes.operator.http.HttpClient;
import oracle.kubernetes.operator.wlsconfig.WlsRetriever.RequestType;
import oracle.kubernetes.operator.wlsconfig.WlsRetriever.WithHttpClientStep;
import oracle.kubernetes.operator.work.NextAction;
import oracle.kubernetes.operator.work.Packet;
import oracle.kubernetes.operator.work.Step;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class WlsRetrieverTest {

  // The log messages to be checked during this test
  private static final String[] LOG_KEYS = {WLS_CONFIGURATION_READ_FAILED, WLS_HEALTH_READ_FAILED};

  private List<LogRecord> logRecords = new ArrayList<>();
  private Memento consoleControl;
  private static final ClassCastException CLASSCAST_EXCEPTION = new ClassCastException("");

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
  public void withHttpClientStep_Config_logIfFailed() {
    V1Service service = Stub.createStub(V1ServiceStub.class);
    Step next = new MockStep(null);
    Packet packet = Stub.createStub(PacketStub.class);

    WithHttpClientStep withHttpClientStep =
        new WithHttpClientStep(RequestType.CONFIG, service, next);
    withHttpClientStep.apply(packet);

    assertThat(logRecords, containsWarning(WLS_CONFIGURATION_READ_FAILED));
  }

  @Test
  public void withHttpClientStep_Config_nologIfFailedOnRetry() {
    V1Service service = Stub.createStub(V1ServiceStub.class);
    Step next = new MockStep(null);
    Packet packet = Stub.createStub(PacketStub.class).withRetryCount(1);

    WithHttpClientStep withHttpClientStep =
        new WithHttpClientStep(RequestType.CONFIG, service, next);
    withHttpClientStep.apply(packet);

    assert (logRecords.isEmpty());
  }

  @Test
  public void withHttpClientStep_Health_logIfFailed() {
    V1Service service = Stub.createStub(V1ServiceStub.class);
    Step next = new MockStep(null);
    final String SERVER_NAME = "admin-server";
    Packet packet = Stub.createStub(PacketStub.class).withServerName(SERVER_NAME);

    WithHttpClientStep withHttpClientStep =
        new WithHttpClientStep(RequestType.HEALTH, service, next);
    withHttpClientStep.apply(packet);

    assertThat(logRecords, containsFine(WLS_HEALTH_READ_FAILED, SERVER_NAME));
  }

  @Test
  public void withHttpClientStep_Health_nologIfFailedOnRetry() {
    V1Service service = Stub.createStub(V1ServiceStub.class);
    Step next = new MockStep(null);
    Packet packet = Stub.createStub(PacketStub.class).withRetryCount(1);

    WithHttpClientStep withHttpClientStep =
        new WithHttpClientStep(RequestType.HEALTH, service, next);
    withHttpClientStep.apply(packet);

    assert (logRecords.isEmpty());
  }

  abstract static class PacketStub extends Packet {

    Integer retryCount;
    String serverName;

    PacketStub withRetryCount(int retryCount) {
      this.retryCount = retryCount;
      return this;
    }

    PacketStub withServerName(String serverName) {
      this.serverName = serverName;
      return this;
    }

    @Override
    public Object get(Object key) {
      if (HttpClient.KEY.equals(key)) {
        throw WlsRetrieverTest
            .CLASSCAST_EXCEPTION; // to go to catch clause in WithHttpClientStep.apply() method
      } else if (WlsRetriever.RETRY_COUNT.equals(key)) {
        return retryCount;
      } else if (ProcessingConstants.SERVER_NAME.equals(key)) {
        return serverName;
      }
      return super.get(key);
    }
  }

  public abstract static class V1ServiceStub extends V1Service {}

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
