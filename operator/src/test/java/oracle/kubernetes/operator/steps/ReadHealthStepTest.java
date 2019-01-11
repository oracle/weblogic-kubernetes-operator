// Copyright 2018, Oracle Corporation and/or its affiliates.  All rights reserved.
// Licensed under the Universal Permissive License v 1.0 as shown at
// http://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.steps;

import static oracle.kubernetes.LogMatcher.containsFine;
import static oracle.kubernetes.operator.logging.MessageKeys.WLS_HEALTH_READ_FAILED;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.*;

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
import oracle.kubernetes.operator.steps.ReadHealthStep.ReadHealthWithHttpClientStep;
import oracle.kubernetes.operator.work.Component;
import oracle.kubernetes.operator.work.NextAction;
import oracle.kubernetes.operator.work.Packet;
import oracle.kubernetes.operator.work.Step;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class ReadHealthStepTest {
  // The log messages to be checked during this test
  private static final String[] LOG_KEYS = {WLS_HEALTH_READ_FAILED};

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
  public void withHttpClientStep_Health_logIfFailed() {
    V1Service service = Stub.createStub(V1ServiceStub.class);
    Step next = new MockStep(null);
    final String SERVER_NAME = "admin-server";
    Packet packet = Stub.createStub(PacketStub.class).withServerName(SERVER_NAME);

    ReadHealthWithHttpClientStep withHttpClientStep =
        new ReadHealthWithHttpClientStep(service, next);
    withHttpClientStep.apply(packet);

    assertThat(logRecords, containsFine(WLS_HEALTH_READ_FAILED, SERVER_NAME));
  }

  abstract static class PacketStub extends Packet {

    Integer retryCount;
    String serverName;

    PacketStub withServerName(String serverName) {
      this.serverName = serverName;
      return this;
    }

    PacketStub addSpi(Class clazz, Object spiObject) {
      Component component = Component.createFor(spiObject);
      this.getComponents().put(clazz.getName(), component);
      return this;
    }

    @Override
    public Object get(Object key) {
      if (HttpClient.KEY.equals(key)) {
        throw CLASSCAST_EXCEPTION; // to go to catch clause in WithHttpClientStep.apply() method
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
