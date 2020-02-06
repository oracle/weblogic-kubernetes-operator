// Copyright (c) 2018, 2020, Oracle Corporation and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.http;

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

import com.meterware.simplestub.Stub;
import io.kubernetes.client.openapi.models.V1ObjectMeta;
import io.kubernetes.client.openapi.models.V1Service;
import io.kubernetes.client.openapi.models.V1ServicePort;
import io.kubernetes.client.openapi.models.V1ServiceSpec;
import oracle.kubernetes.operator.wlsconfig.WlsDomainConfig;
import oracle.kubernetes.utils.TestUtils;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import static oracle.kubernetes.operator.logging.MessageKeys.HTTP_METHOD_FAILED;
import static oracle.kubernetes.utils.LogMatcher.containsFine;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;

public class HttpClientTest {

  static final String FAKE_URL = "fake/url";
  private static final String CHANNEL_NAME = "default";
  private List<LogRecord> logRecords = new ArrayList<>();
  private TestUtils.ConsoleHandlerMemento consoleControl;

  /**
   * Setup test.
   */
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
  public void messageLogged_when_executePostUrlOnServiceClusterIP_fails() throws HttpException {
    executePostUrl(false);
    assertThat(logRecords, containsFine(HTTP_METHOD_FAILED, Status.NOT_FOUND.getStatusCode()));
  }

  @Test(expected = HttpException.class)
  public void throws_when_executePostUrlOnServiceClusterIP_withThrowOnFailure_fails()
      throws HttpException {
    ignoreMessage(HTTP_METHOD_FAILED);
    executePostUrl(true);
  }

  private void executePostUrl(boolean throwOnFailure) throws HttpException {
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
  public void getServiceUrl_returnsUrlUsingClusterIP() {
    final String clusterIp = "123.123.123.133";
    final int port = 7101;

    List<V1ServicePort> ports = new ArrayList<>();
    ports.add(new V1ServicePort().port(port).name(CHANNEL_NAME));
    V1Service service =
        new V1Service().spec(new V1ServiceSpec().clusterIP(clusterIp).ports(ports));
    String url = HttpClient.getServiceUrl(service, null, CHANNEL_NAME, port);
    assertEquals("http://" + clusterIp + ":" + port, url);
  }

  @Test
  public void getServiceUrl_returnsUrlUsingDns_ifNoneClusterIP() {
    final String clusterIp = "None";
    final int port = 7101;
    final String namespace = "domain1";
    final String serviceName = "admin-server";

    List<V1ServicePort> ports = new ArrayList<>();
    ports.add(new V1ServicePort().port(port).name(CHANNEL_NAME));

    V1Service service =
        new V1Service()
            .spec(new V1ServiceSpec().clusterIP(clusterIp).ports(ports))
            .metadata(new V1ObjectMeta().namespace(namespace).name(serviceName));

    String url = HttpClient.getServiceUrl(service, null, CHANNEL_NAME, port);
    assertEquals("http://" + serviceName + "." + namespace + ".pod.cluster.local:" + port, url);
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
