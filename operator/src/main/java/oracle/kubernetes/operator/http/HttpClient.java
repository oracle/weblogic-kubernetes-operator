// Copyright (c) 2017, 2020, Oracle Corporation and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.http;

import java.util.Arrays;
import java.util.Map;
import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.Entity;
import javax.ws.rs.client.Invocation;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.Response;

import io.kubernetes.client.openapi.models.V1Pod;
import io.kubernetes.client.openapi.models.V1Service;
import io.kubernetes.client.openapi.models.V1ServicePort;
import io.kubernetes.client.openapi.models.V1ServiceSpec;
import oracle.kubernetes.operator.helpers.SecretHelper;
import oracle.kubernetes.operator.helpers.SecretType;
import oracle.kubernetes.operator.logging.LoggingFacade;
import oracle.kubernetes.operator.logging.LoggingFactory;
import oracle.kubernetes.operator.logging.MessageKeys;
import oracle.kubernetes.operator.work.NextAction;
import oracle.kubernetes.operator.work.Packet;
import oracle.kubernetes.operator.work.Step;

/** HTTP Client. */
public class HttpClient {
  public static final String KEY = "httpClient";

  private static final LoggingFacade LOGGER = LoggingFactory.getLogger("Operator", "Operator");
  private static final String HTTP_PROTOCOL = "http://";
  private static final String HTTPS_PROTOCOL = "https://";
  private final Client httpClient;
  private final String encodedCredentials;

  // Please use one of the factory methods to get an instance of HttpClient.
  // Constructor is package access for unit testing
  HttpClient(Client httpClient, String encodedCredentials) {
    this.httpClient = httpClient;
    this.encodedCredentials = encodedCredentials;
  }

  /**
   * Asynchronous {@link Step} for creating an authenticated HTTP client targeted at a server
   * instance.
   *
   * @param namespace Namespace
   * @param adminSecretName Admin secret name
   * @param next Next processing step
   * @return step to create client
   */
  public static Step createAuthenticatedClientForServer(
      String namespace, String adminSecretName, Step next) {
    return new AuthenticatedClientForServerStep(
        namespace, adminSecretName, new WithSecretDataStep(next));
  }

  /**
   * Erase authentication credential so that it is not sitting in memory where a rogue program can
   * find it.
   */
  private static void clearCredential(byte[] credential) {
    if (credential != null) {
      Arrays.fill(credential, (byte) 0);
    }
  }

  /**
   * Create authenticated HTTP client.
   *
   * @param username Username
   * @param password Password
   * @return authenticated client
   */
  private static HttpClient createAuthenticatedClient(
      final byte[] username, final byte[] password) {
    // build client with authentication information.
    Client client = ClientBuilder.newClient();
    String encodedCredentials = null;
    if (username != null && password != null) {
      byte[] usernameAndPassword = new byte[username.length + password.length + 1];
      System.arraycopy(username, 0, usernameAndPassword, 0, username.length);
      usernameAndPassword[username.length] = (byte) ':';
      System.arraycopy(password, 0, usernameAndPassword, username.length + 1, password.length);
      encodedCredentials = java.util.Base64.getEncoder().encodeToString(usernameAndPassword);
    }
    return new HttpClient(client, encodedCredentials);
  }

  /**
   * Returns the URL to access the Service; using the Service clusterIP and port. If the service is
   * headless, then the pod's IP is returned, if available.
   *
   * @param service The name of the Service that you want the URL for.
   * @param pod The pod for headless services
   * @param adminChannel administration channel name
   * @param defaultPort default port, if enabled. Other ports will use SSL.
   * @return The URL of the Service or null if the URL cannot be found.
   */
  public static String getServiceUrl(
      V1Service service, V1Pod pod, String adminChannel, Integer defaultPort) {
    if (service != null) {
      V1ServiceSpec spec = service.getSpec();
      if (spec != null) {
        String portalIP = spec.getClusterIP();
        if ("None".equalsIgnoreCase(spec.getClusterIP())) {
          if (pod != null && pod.getStatus().getPodIP() != null) {
            portalIP = pod.getStatus().getPodIP();
          } else {
            portalIP =
                service.getMetadata().getName()
                    + "."
                    + service.getMetadata().getNamespace()
                    + ".pod.cluster.local";
          }
        }
        Integer port = -1; // uninitialized
        if (adminChannel != null) {
          for (V1ServicePort sp : spec.getPorts()) {
            if (adminChannel.equals(sp.getName())) {
              port = sp.getPort();
              break;
            }
          }
          if (port == -1) {
            return null;
          }
        } else {
          port = spec.getPorts().iterator().next().getPort();
        }
        portalIP += ":" + port;
        String serviceUrl = (port.equals(defaultPort) ? HTTP_PROTOCOL : HTTPS_PROTOCOL) + portalIP;
        LOGGER.fine(MessageKeys.SERVICE_URL, serviceUrl);
        return serviceUrl;
      }
    }
    return null;
  }

  /**
   * Constructs a URL using the provided service URL and request URL, and use the resulting URL and
   * the payload provided to issue a HTTP POST request. This method does not throw HttpException if
   * the HTTP request returns failure status code
   *
   * @param requestUrl The request URL containing the request of the REST call
   * @param serviceUrl The service URL containing the host and port of the server where the HTTP
   *     request is to be sent to
   * @param payload The payload to be used in the HTTP POST request
   * @return A Result object containing the respond from the REST call
   */
  public Result executePostUrlOnServiceClusterIP(
      String requestUrl, String serviceUrl, String payload) {
    Result result = null;
    try {
      result = executePostUrlOnServiceClusterIP(requestUrl, serviceUrl, payload, false);
    } catch (HttpException httpException) {
      // ignore as executePostUrlOnServiceClusterIP only throw HttpException if throwOnFailure is
      // true
    }
    return result;
  }

  /**
   * Constructs a URL using the provided service URL and request URL, and use the resulting URL and
   * the payload provided to issue a HTTP POST request.
   *
   * @param requestUrl The request URL containing the request of the REST call
   * @param serviceUrl The service URL containing the host and port of the server where the HTTP
   *     request is to be sent to
   * @param payload The payload to be used in the HTTP POST request
   * @param throwOnFailure Throws HttpException if the status code in the HTTP response indicates
   *     any error
   * @return A Result object containing the respond from the REST call
   * @throws HttpException if throwOnFailure is true and the status of the HTTP response indicates
   *     the request was not successful
   */
  public Result executePostUrlOnServiceClusterIP(
      String requestUrl, String serviceUrl, String payload, boolean throwOnFailure)
      throws HttpException {
    String url = serviceUrl + requestUrl;
    WebTarget target = httpClient.target(url);
    Invocation.Builder invocationBuilder =
        target
            .request()
            .accept("application/json")
            .header("Authorization", "Basic " + encodedCredentials)
            .header("X-Requested-By", "Weblogic Operator");
    Response response = invocationBuilder.post(Entity.json(payload));
    LOGGER.finer("Response is  " + response.getStatusInfo());
    String responseString = null;
    int status = response.getStatus();
    boolean successful = false;
    if (response.getStatusInfo().getFamily() == Response.Status.Family.SUCCESSFUL) {
      successful = true;
      if (response.hasEntity()) {
        responseString = String.valueOf(response.readEntity(String.class));
      }
    } else {
      LOGGER.fine(MessageKeys.HTTP_METHOD_FAILED, "POST", url, response.getStatus());
      if (throwOnFailure) {
        throw new HttpException(status);
      }
    }
    return new Result(responseString, status, successful);
  }

  private static class AuthenticatedClientForServerStep extends Step {
    private final String namespace;
    private final String adminSecretName;

    AuthenticatedClientForServerStep(String namespace, String adminSecretName, Step next) {
      super(next);
      this.namespace = namespace;
      this.adminSecretName = adminSecretName;
    }

    @Override
    public NextAction apply(Packet packet) {
      Step readSecret =
          SecretHelper.getSecretData(
              SecretType.WebLogicCredentials, adminSecretName, namespace, getNext());
      return doNext(readSecret, packet);
    }
  }

  private static class WithSecretDataStep extends Step {

    WithSecretDataStep(Step next) {
      super(next);
    }

    @Override
    public NextAction apply(Packet packet) {
      @SuppressWarnings("unchecked")
      Map<String, byte[]> secretData =
          (Map<String, byte[]>) packet.get(SecretHelper.SECRET_DATA_KEY);
      if (secretData != null) {
        byte[] username = secretData.get(SecretHelper.ADMIN_SERVER_CREDENTIALS_USERNAME);
        byte[] password = secretData.get(SecretHelper.ADMIN_SERVER_CREDENTIALS_PASSWORD);
        packet.put(KEY, createAuthenticatedClient(username, password));

        clearCredential(username);
        clearCredential(password);
      }
      return doNext(packet);
    }
  }
}
