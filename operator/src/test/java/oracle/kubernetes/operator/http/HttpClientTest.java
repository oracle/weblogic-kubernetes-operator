// Copyright 2018, Oracle Corporation and/or its affiliates.  All rights reserved.
// Licensed under the Universal Permissive License v 1.0 as shown at
// http://oss.oracle.com/licenses/upl.
package oracle.kubernetes.operator.http;

import static oracle.kubernetes.LogMatcher.containsFine;
import static oracle.kubernetes.operator.logging.MessageKeys.HTTP_METHOD_FAILED;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;

import com.meterware.simplestub.Stub;
import io.kubernetes.client.models.V1ObjectMeta;
import io.kubernetes.client.models.V1Service;
import io.kubernetes.client.models.V1ServicePort;
import io.kubernetes.client.models.V1ServiceSpec;
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

  @Test
  public void getServiceURL_returnsUrlUsingClusterIP() {
    final String CLUSTER_IP = "123.123.123.133";
    final int PORT = 7101;

    List<V1ServicePort> ports = new ArrayList<>();
    ports.add(new V1ServicePort().port(PORT));
    V1Service service =
        new V1Service().spec(new V1ServiceSpec().clusterIP(CLUSTER_IP).ports(ports));
    String url = HttpClient.getServiceURL(service);
    assertEquals("http://" + CLUSTER_IP + ":" + PORT, url);
  }

  @Test
  public void getServiceURL_returnsUrlUsingDNS_ifNoneClusterIP() {
    final String CLUSTER_IP = "None";
    final int PORT = 7101;
    final String NAMESPACE = "domain1";
    final String SERVICE_NAME = "admin-server";

    List<V1ServicePort> ports = new ArrayList<>();
    ports.add(new V1ServicePort().port(PORT));

    V1Service service =
        new V1Service()
            .spec(new V1ServiceSpec().clusterIP(CLUSTER_IP).ports(ports))
            .metadata(new V1ObjectMeta().namespace(NAMESPACE).name(SERVICE_NAME));

    String url = HttpClient.getServiceURL(service);
    assertEquals("http://" + SERVICE_NAME + "." + NAMESPACE + ".svc.cluster.local:" + PORT, url);
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
