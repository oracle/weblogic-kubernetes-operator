// Copyright 2018, Oracle Corporation and/or its affiliates.  All rights reserved.
// Licensed under the Universal Permissive License v 1.0 as shown at
// http://oss.oracle.com/licenses/upl.
package oracle.kubernetes.operator.http;

import static oracle.kubernetes.LogMatcher.containsFine;
import static oracle.kubernetes.operator.logging.MessageKeys.HTTP_METHOD_FAILED;
import static org.hamcrest.MatcherAssert.assertThat;

import com.meterware.simplestub.Stub;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import javax.ws.rs.client.Client;
import javax.ws.rs.client.Entity;
import javax.ws.rs.client.Invocation;
import javax.ws.rs.client.Invocation.Builder;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;
import oracle.kubernetes.TestUtils;
import oracle.kubernetes.operator.wlsconfig.WlsDomainConfig;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class HttpClientTest {

  private List<LogRecord> logRecords = new ArrayList<>();
  private TestUtils.ConsoleHandlerMemento consoleControl;
  static final String FAKE_URL = "fake/url";

  @Before
  public void setup() {
    consoleControl =
        TestUtils.silenceOperatorLogger()
            .collectLogMessages(logRecords, HTTP_METHOD_FAILED)
            .withLogLevel(Level.FINE);
  }

  @After
  public void tearDown() throws Exception {
    consoleControl.revert();
  }

  @Test
  public void messageLogged_when_executePostUrlOnServiceClusterIP_fails() throws HTTPException {
    executePostUrl(false);
    assertThat(logRecords, containsFine(HTTP_METHOD_FAILED, Status.NOT_FOUND.getStatusCode()));
  }

  @Test(expected = HTTPException.class)
  public void throws_when_executePostUrlOnServiceClusterIP_withThrowOnFailure_fails()
      throws HTTPException {
    ignoreMessage(HTTP_METHOD_FAILED);
    executePostUrl(true);
  }

  private void executePostUrl(boolean throwOnFailure) throws HTTPException {
    ClientStub clientStub =
        Stub.createStub(ClientStub.class)
            .withResponse(Stub.createStub(ResponseStub.class, Status.NOT_FOUND, null));
    HttpClient httpClient = new HttpClient(clientStub, "");

    httpClient.executePostUrlOnServiceClusterIP(
        FAKE_URL, FAKE_URL, WlsDomainConfig.getRetrieveServersSearchPayload(), throwOnFailure);
  }

  private void ignoreMessage(String message) {
    consoleControl.ignoreMessage(message);
  }

  abstract static class ClientStub implements Client {

    private static Response mockResponse;

    @Override
    public WebTarget target(String uri) {
      return Stub.createStub(WebTargetStub.class);
    }

    public ClientStub withResponse(Response mockResponse) {
      ClientStub.mockResponse = mockResponse;
      return this;
    }
  }

  abstract static class WebTargetStub implements WebTarget {

    @Override
    public Builder request() {
      return Stub.createStub(InvocationBuilderStub.class);
    }
  }

  abstract static class InvocationBuilderStub implements Invocation.Builder {

    @Override
    public Builder accept(String... mediaTypes) {
      return this;
    }

    @Override
    public Builder header(String name, Object value) {
      return this;
    }

    @Override
    public Response post(Entity<?> entity) {
      return ClientStub.mockResponse;
    }
  }

  abstract static class ResponseStub extends Response {

    final StatusType statusInfo;
    final Object entity;

    public ResponseStub(StatusType statusInfo, Object entity) {
      this.statusInfo = statusInfo;
      this.entity = entity;
    }

    @Override
    public int getStatus() {
      return statusInfo.getStatusCode();
    }

    @Override
    public StatusType getStatusInfo() {
      return statusInfo;
    }

    @Override
    public <T> T readEntity(Class<T> entityType) {
      return (T) entity;
    }

    @Override
    public boolean hasEntity() {
      return entity != null;
    }
  }
}
