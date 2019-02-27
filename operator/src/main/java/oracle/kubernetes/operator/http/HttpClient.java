// Copyright 2017, 2018, Oracle Corporation and/or its affiliates.  All rights reserved.
// Licensed under the Universal Permissive License v 1.0 as shown at
// http://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.http;

import io.kubernetes.client.ApiException;
import io.kubernetes.client.models.V1Service;
import io.kubernetes.client.models.V1ServiceSpec;
import java.util.Arrays;
import java.util.Map;
import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.Entity;
import javax.ws.rs.client.Invocation;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.Response;
import oracle.kubernetes.operator.helpers.CallBuilderFactory;
import oracle.kubernetes.operator.helpers.SecretHelper;
import oracle.kubernetes.operator.logging.LoggingFacade;
import oracle.kubernetes.operator.logging.LoggingFactory;
import oracle.kubernetes.operator.logging.MessageKeys;
import oracle.kubernetes.operator.work.ContainerResolver;
import oracle.kubernetes.operator.work.NextAction;
import oracle.kubernetes.operator.work.Packet;
import oracle.kubernetes.operator.work.Step;

/** HTTP Client. */
public class HttpClient {
  public static final String KEY = "httpClient";

  private static final LoggingFacade LOGGER = LoggingFactory.getLogger("Operator", "Operator");

  private Client httpClient;
  private String encodedCredentials;

  private static final String HTTP_PROTOCOL = "http://";

  // for debugging
  private static final String SERVICE_URL =
      System.getProperty("oracle.kubernetes.operator.http.HttpClient.SERVICE_URL");

  // Please use one of the factory methods to get an instance of HttpClient.
  // Constructor is package access for unit testing
  HttpClient(Client httpClient, String encodedCredentials) {
    this.httpClient = httpClient;
    this.encodedCredentials = encodedCredentials;
  }

  /**
   * Constructs a URL using the provided service URL and request URL, and use the resulting URL to
   * issue a HTTP GET request.
   *
   * @param requestUrl The request URL containing the request of the REST call
   * @param serviceURL The service URL containing the host and port of the server where the HTTP
   *     request is to be sent to
   * @return A Result object containing the respond from the REST call
   */
  public Result executeGetOnServiceClusterIP(String requestUrl, String serviceURL) {
    String url = serviceURL + requestUrl;
    WebTarget target = httpClient.target(url);
    Invocation.Builder invocationBuilder =
        target
            .request()
            .accept("application/json")
            .header("Authorization", "Basic " + encodedCredentials);
    Response response = invocationBuilder.get();
    String responseString = null;
    int status = response.getStatus();
    boolean successful = false;
    if (response.getStatusInfo().getFamily() == Response.Status.Family.SUCCESSFUL) {
      successful = true;
      if (response.hasEntity()) {
        responseString = String.valueOf(response.readEntity(String.class));
      }
    } else {
      LOGGER.warning(MessageKeys.HTTP_METHOD_FAILED, "GET", url, response.getStatus());
    }
    return new Result(responseString, status, successful);
  }

  /**
   * Constructs a URL using the provided service URL and request URL, and use the resulting URL and
   * the payload provided to issue a HTTP POST request. This method does not throw HTTPException if
   * the HTTP request returns failure status code
   *
   * @param requestUrl The request URL containing the request of the REST call
   * @param serviceURL The service URL containing the host and port of the server where the HTTP
   *     request is to be sent to
   * @param payload The payload to be used in the HTTP POST request
   * @return A Result object containing the respond from the REST call
   */
  public Result executePostUrlOnServiceClusterIP(
      String requestUrl, String serviceURL, String payload) {
    Result result = null;
    try {
      result = executePostUrlOnServiceClusterIP(requestUrl, serviceURL, payload, false);
    } catch (HTTPException httpException) {
      // ignore as executePostUrlOnServiceClusterIP only throw HTTPException if throwOnFailure is
      // true
    }
    return result;
  }

  /**
   * Constructs a URL using the provided service URL and request URL, and use the resulting URL and
   * the payload provided to issue a HTTP POST request.
   *
   * @param requestUrl The request URL containing the request of the REST call
   * @param serviceURL The service URL containing the host and port of the server where the HTTP
   *     request is to be sent to
   * @param payload The payload to be used in the HTTP POST request
   * @param throwOnFailure Throws HTTPException if the status code in the HTTP response indicates
   *     any error
   * @return A Result object containing the respond from the REST call
   * @throws HTTPException if throwOnFailure is true and the status of the HTTP response indicates
   *     the request was not successful
   */
  public Result executePostUrlOnServiceClusterIP(
      String requestUrl, String serviceURL, String payload, boolean throwOnFailure)
      throws HTTPException {
    String url = serviceURL + requestUrl;
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
        throw new HTTPException(status);
      }
    }
    return new Result(responseString, status, successful);
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

  private static class AuthenticatedClientForServerStep extends Step {
    private final String namespace;
    private final String adminSecretName;

    public AuthenticatedClientForServerStep(String namespace, String adminSecretName, Step next) {
      super(next);
      this.namespace = namespace;
      this.adminSecretName = adminSecretName;
    }

    @Override
    public NextAction apply(Packet packet) {
      Step readSecret =
          SecretHelper.getSecretData(
              SecretHelper.SecretType.AdminCredentials, adminSecretName, namespace, getNext());
      return doNext(readSecret, packet);
    }
  }

  private static class WithSecretDataStep extends Step {

    public WithSecretDataStep(Step next) {
      super(next);
    }

    @Override
    public NextAction apply(Packet packet) {
      @SuppressWarnings("unchecked")
      Map<String, byte[]> secretData =
          (Map<String, byte[]>) packet.get(SecretHelper.SECRET_DATA_KEY);
      byte[] username = null;
      byte[] password = null;
      if (secretData != null) {
        username = secretData.get(SecretHelper.ADMIN_SERVER_CREDENTIALS_USERNAME);
        password = secretData.get(SecretHelper.ADMIN_SERVER_CREDENTIALS_PASSWORD);
      }
      packet.put(KEY, createAuthenticatedClient(username, password));

      Arrays.fill(username, (byte) 0);
      Arrays.fill(password, (byte) 0);
      return doNext(packet);
    }
  }

  /**
   * Create authenticated client specifically targeted at an admin server.
   *
   * @param namespace Namespace
   * @param adminSecretName Admin secret name
   * @return authenticated client
   */
  public static HttpClient createAuthenticatedClientForServer(
      String namespace, String adminSecretName) {
    SecretHelper secretHelper = new SecretHelper(namespace);
    Map<String, byte[]> secretData =
        secretHelper.getSecretData(SecretHelper.SecretType.AdminCredentials, adminSecretName);

    byte[] username = null;
    byte[] password = null;
    if (secretData != null) {
      username = secretData.get(SecretHelper.ADMIN_SERVER_CREDENTIALS_USERNAME);
      password = secretData.get(SecretHelper.ADMIN_SERVER_CREDENTIALS_PASSWORD);
    }
    return createAuthenticatedClient(username, password);
  }

  /**
   * Create authenticated HTTP client.
   *
   * @param username Username
   * @param password Password
   * @return authenticated client
   */
  public static HttpClient createAuthenticatedClient(final byte[] username, final byte[] password) {
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
   * Returns the URL to access the service; using the service clusterIP and port.
   *
   * @param name The name of the Service that you want the URL for.
   * @param namespace The Namespace in which the Service you want the URL for is defined.
   * @return The URL of the Service, or null if it is not found
   */
  public static String getServiceURL(String name, String namespace) {
    if (SERVICE_URL != null) {
      return SERVICE_URL;
    }
    try {
      CallBuilderFactory factory =
          ContainerResolver.getInstance().getContainer().getSPI(CallBuilderFactory.class);
      return getServiceURL(factory.create().readService(name, namespace));
    } catch (ApiException e) {
      LOGGER.warning(MessageKeys.EXCEPTION, e);
    }
    return null;
  }

  /**
   * Returns the URL to access the Service; using the Service clusterIP and port.
   *
   * @param service The name of the Service that you want the URL for.
   * @return The URL of the Service or null if the URL cannot be found.
   */
  public static String getServiceURL(V1Service service) {
    if (service != null) {
      V1ServiceSpec spec = service.getSpec();
      if (spec != null) {
        String portalIP =
            "None".equalsIgnoreCase(spec.getClusterIP())
                ? service.getMetadata().getName()
                    + "."
                    + service.getMetadata().getNamespace()
                    + ".svc.cluster.local"
                : spec.getClusterIP();
        int port = spec.getPorts().iterator().next().getPort();
        portalIP += ":" + port;
        String serviceURL = HTTP_PROTOCOL + portalIP;
        LOGGER.fine(MessageKeys.SERVICE_URL, serviceURL);
        return serviceURL;
      }
    }
    return null;
  }
}
