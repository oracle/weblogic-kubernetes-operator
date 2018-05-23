// Copyright 2018, Oracle Corporation and/or its affiliates.  All rights reserved.
// Licensed under the Universal Permissive License v 1.0 as shown at
// http://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.wlsconfig;

import com.meterware.simplestub.Stub;
import io.kubernetes.client.models.V1Service;
import java.util.logging.Level;
import oracle.kubernetes.operator.ProcessingConstants;
import oracle.kubernetes.operator.http.HttpClient;
import oracle.kubernetes.operator.logging.MessageKeys;
import oracle.kubernetes.operator.utils.LoggingFacadeStub;
import oracle.kubernetes.operator.wlsconfig.WlsRetriever.RequestType;
import oracle.kubernetes.operator.wlsconfig.WlsRetriever.WithHttpClientStep;
import oracle.kubernetes.operator.work.NextAction;
import oracle.kubernetes.operator.work.Packet;
import oracle.kubernetes.operator.work.Step;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class WlsRetrieverTest {

  LoggingFacadeStub loggingFacadeStub;

  @Before
  public void setup() throws Exception {
    loggingFacadeStub = LoggingFacadeStub.install(WlsRetriever.class);
  }

  @After
  public void tearDown() throws Exception {
    loggingFacadeStub.uninstall();
  }

  @Test
  public void withHttpClientStep_Config_logIfFailed() {
    V1Service service = Stub.createStub(V1ServiceStub.class);
    Step next = new MockStep(null);
    Packet packet = new Packet();
    packet.put(HttpClient.KEY, "Not HttpClient to cause ClassCastException");

    WithHttpClientStep withHttpClientStep =
        new WithHttpClientStep(RequestType.CONFIG, service, next);
    withHttpClientStep.apply(packet);

    loggingFacadeStub.assertNumMessageLogged(1);
    loggingFacadeStub.assertContains(
        Level.WARNING, MessageKeys.WLS_CONFIGURATION_READ_FAILED, getClassCastException(packet));
  }

  @Test
  public void withHttpClientStep_Config_nologIfFailedOnRetry() {
    V1Service service = Stub.createStub(V1ServiceStub.class);
    Step next = new MockStep(null);
    Packet packet = new Packet();
    packet.put(HttpClient.KEY, "Not HttpClient to cause ClassCastException");
    packet.put(WlsRetriever.RETRY_COUNT, 1);

    WithHttpClientStep withHttpClientStep =
        new WithHttpClientStep(RequestType.CONFIG, service, next);
    withHttpClientStep.apply(packet);

    loggingFacadeStub.assertNoMessagesLogged();
  }

  @Test
  public void withHttpClientStep_Health_logIfFailed() {
    V1Service service = Stub.createStub(V1ServiceStub.class);
    Step next = new MockStep(null);
    final String SERVER_NAME = "admin-server";
    Packet packet = new Packet();
    packet.put(HttpClient.KEY, "Not HttpClient to cause ClassCastException");
    packet.put(ProcessingConstants.SERVER_NAME, SERVER_NAME);

    WithHttpClientStep withHttpClientStep =
        new WithHttpClientStep(RequestType.HEALTH, service, next);
    withHttpClientStep.apply(packet);

    loggingFacadeStub.assertNumMessageLogged(1);
    loggingFacadeStub.assertContains(
        Level.FINE,
        MessageKeys.WLS_HEALTH_READ_FAILED,
        SERVER_NAME,
        getClassCastException(packet).toString());
  }

  @Test
  public void withHttpClientStep_Health_nologIfFailedOnRetry() {
    V1Service service = Stub.createStub(V1ServiceStub.class);
    Step next = new MockStep(null);
    Packet packet = new Packet();
    packet.put(HttpClient.KEY, "Not HttpClient to cause ClassCastException");
    packet.put(WlsRetriever.RETRY_COUNT, 1);

    WithHttpClientStep withHttpClientStep =
        new WithHttpClientStep(RequestType.HEALTH, service, next);
    withHttpClientStep.apply(packet);

    loggingFacadeStub.assertNoMessagesLogged();
  }

  private ClassCastException getClassCastException(Packet packet) {
    try {
      HttpClient httpClient = (HttpClient) packet.get(HttpClient.KEY);
    } catch (ClassCastException e) {
      return e;
    }
    return null;
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
