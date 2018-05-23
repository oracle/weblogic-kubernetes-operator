// Copyright 2018, Oracle Corporation and/or its affiliates.  All rights reserved.
// Licensed under the Universal Permissive License v 1.0 as shown at
// http://oss.oracle.com/licenses/upl.
package oracle.kubernetes.operator.http;

import com.meterware.simplestub.Stub;
import java.util.logging.Level;
import javax.ws.rs.client.Client;
import javax.ws.rs.client.Entity;
import javax.ws.rs.client.Invocation;
import javax.ws.rs.client.Invocation.Builder;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;
import oracle.kubernetes.operator.logging.MessageKeys;
import oracle.kubernetes.operator.utils.LoggingFacadeStub;
import oracle.kubernetes.operator.wlsconfig.WlsDomainConfig;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class HttpClientTest {

  private static Response mockResponse;
  private LoggingFacadeStub loggingFacadeStub;

  @Before
  public void setup() throws Exception {
    loggingFacadeStub = LoggingFacadeStub.install(HttpClient.class);
  }

  @After
  public void tearDown() throws Exception {
    loggingFacadeStub.uninstall();
    mockResponse = null;
  }

  @Test
  public void messageLogged_when_executePostUrlOnServiceClusterIP_fails() throws HTTPException {
    ClientStub clientStub = Stub.createStub(ClientStub.class);
    HttpClient httpClient = new HttpClient(clientStub, "");
    mockResponse = Stub.createStub(ResponseStub.class, Status.NOT_FOUND, null);
    final String serviceURL = "fake/service/url";
    final String requestURL = "fake/request/url";

    httpClient.executePostUrlOnServiceClusterIP(
        requestURL, serviceURL, WlsDomainConfig.getRetrieveServersSearchPayload(), false);

    loggingFacadeStub.assertContains(
        Level.FINE,
        MessageKeys.HTTP_METHOD_FAILED,
        "POST",
        serviceURL + requestURL,
        Status.NOT_FOUND.getStatusCode());
  }

  abstract static class ClientStub implements Client {

    @Override
    public WebTarget target(String uri) {
      return Stub.createStub(WebTargetStub.class);
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
      return HttpClientTest.mockResponse;
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
