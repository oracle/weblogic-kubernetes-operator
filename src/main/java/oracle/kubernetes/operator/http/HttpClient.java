// Copyright 2017, 2018, Oracle Corporation and/or its affiliates.  All rights reserved.
// Licensed under the Universal Permissive License v 1.0 as shown at http://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.http;

import io.kubernetes.client.ApiException;
import io.kubernetes.client.models.V1Service;
import io.kubernetes.client.models.V1ServiceSpec;
import oracle.kubernetes.operator.helpers.CallBuilder;
import oracle.kubernetes.operator.helpers.CallBuilderFactory;
import oracle.kubernetes.operator.helpers.SecretHelper;
import oracle.kubernetes.operator.logging.LoggingFacade;
import oracle.kubernetes.operator.logging.LoggingFactory;
import oracle.kubernetes.operator.logging.MessageKeys;
import oracle.kubernetes.operator.work.ContainerResolver;
import oracle.kubernetes.operator.work.NextAction;
import oracle.kubernetes.operator.work.Packet;
import oracle.kubernetes.operator.work.Step;

import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.Entity;
import javax.ws.rs.client.Invocation;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.Response;

import java.util.Arrays;
import java.util.Map;

/**
 * HTTP Client
 */
public class HttpClient {
  public static final String KEY = "httpClient";

  private static final LoggingFacade LOGGER = LoggingFactory.getLogger("Operator", "Operator");

  private Client httpClient;
  private String principal;
  private String encodedCredentials;

  private static final String HTTP_PROTOCOL = "http://";

  // for debugging
  private static final String SERVICE_URL = System.getProperty("oracle.kubernetes.operator.http.HttpClient.SERVICE_URL");

  private HttpClient(Client httpClient, String principal, String encodedCredentials) {
    this.httpClient = httpClient;
    this.principal = principal;
    this.encodedCredentials = encodedCredentials;
  }

  public String executeGetOnServiceClusterIP(String requestUrl, String serviceName, String namespace) {
    String serviceURL = SERVICE_URL == null ? getServiceURL(principal, serviceName, namespace) : SERVICE_URL;
    String url = serviceURL + requestUrl;
    WebTarget target = httpClient.target(url);
    Invocation.Builder invocationBuilder = target.request().accept("application/json")
        .header("Authorization", "Basic " + encodedCredentials);
    Response response = invocationBuilder.get();
    if (response.getStatusInfo().getFamily() == Response.Status.Family.SUCCESSFUL) {
      if (response.hasEntity()) {
        return String.valueOf(response.readEntity(String.class));
      }
    } else {
      LOGGER.warning(MessageKeys.HTTP_METHOD_FAILED, "GET", url, response.getStatus());
    }
    return null;
  }

  public String executePostUrlOnServiceClusterIP(String requestUrl, String serviceURL, String namespace, String payload) {
    String url = serviceURL + requestUrl;
    WebTarget target = httpClient.target(url);
    Invocation.Builder invocationBuilder = target.request().accept("application/json")
        .header("Authorization", "Basic " + encodedCredentials)
        .header("X-Requested-By", "WebLogicOperator");
    Response response = invocationBuilder.post(Entity.json(payload));
    LOGGER.finer("Response is  " + response.getStatusInfo());
    if (response.getStatusInfo().getFamily() == Response.Status.Family.SUCCESSFUL) {
      if (response.hasEntity()) {
        return String.valueOf(response.readEntity(String.class));
      }
    } else {
      LOGGER.warning(MessageKeys.HTTP_METHOD_FAILED, "POST", url, response.getStatus());
    }
    return null;
  }

  /**
   * Asynchronous {@link Step} for creating an authenticated HTTP client targeted at a server instance
   * @param principal Principal
   * @param namespace Namespace
   * @param adminSecretName Admin secret name
   * @param next Next processing step
   * @return step to create client
   */
  public static Step createAuthenticatedClientForServer(String principal, String namespace, String adminSecretName, Step next) {
    return new AuthenticatedClientForServerStep(namespace, adminSecretName, new WithSecretDataStep(principal, next));
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
      Step readSecret = SecretHelper.getSecretData(SecretHelper.SecretType.AdminCredentials, adminSecretName, namespace, next);
      return doNext(readSecret, packet);
    }
  }
  
  private static class WithSecretDataStep extends Step {
    private final String principal;

    public WithSecretDataStep(String principal, Step next) {
      super(next);
      this.principal = principal;
    }

    @Override
    public NextAction apply(Packet packet) {
      @SuppressWarnings("unchecked")
      Map<String, byte[]> secretData = (Map<String, byte[]>) packet.get(SecretHelper.SECRET_DATA_KEY);
      byte[] username = null;
      byte[] password = null;
      if (secretData != null) {
        username = secretData.get(SecretHelper.ADMIN_SERVER_CREDENTIALS_USERNAME);
        password = secretData.get(SecretHelper.ADMIN_SERVER_CREDENTIALS_PASSWORD);
      }
      packet.put(KEY, createAuthenticatedClient(principal, username, password));
      
      Arrays.fill(username, (byte) 0); 
      Arrays.fill(password, (byte) 0); 
      return doNext(packet);
    }
  }
  
  /**
   * Create authenticated client specifically targeted at an admin server
   * @param principal Principal
   * @param namespace Namespace
   * @param adminSecretName Admin secret name
   * @return authenticated client
   */
  public static HttpClient createAuthenticatedClientForServer(String principal, String namespace, String adminSecretName) {
    SecretHelper secretHelper = new SecretHelper(namespace);
    Map<String, byte[]> secretData =
        secretHelper.getSecretData(SecretHelper.SecretType.AdminCredentials, adminSecretName);

    byte[] username = null;
    byte[] password = null;
    if (secretData != null) {
      username = secretData.get(SecretHelper.ADMIN_SERVER_CREDENTIALS_USERNAME);
      password = secretData.get(SecretHelper.ADMIN_SERVER_CREDENTIALS_PASSWORD);
    }
    return createAuthenticatedClient(principal, username, password);
  }

  /**
   * Create authenticated HTTP client
   * @param principal Principal
   * @param username Username
   * @param password Password
   * @return authenticated client
   */
  public static HttpClient createAuthenticatedClient(String principal,
                                                     final byte[] username,
                                                     final byte[] password) {
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
    return new HttpClient(client, principal, encodedCredentials);
  }

  /**
   * Returns the URL to access the service; using the service clusterIP and port.
   *
   * @param principal The principal that will be used to call the Kubernetes API.
   * @param name The name of the Service that you want the URL for.
   * @param namespace The Namespace in which the Service you want the URL for is defined.
   * @return The URL of the Service, or null if it is not found or principal does not have sufficient permissions.
   */
  public static String getServiceURL(String principal, String name, String namespace) {
    try {
      CallBuilderFactory factory = ContainerResolver.getInstance().getContainer().getSPI(CallBuilderFactory.class);
      return getServiceURL(factory.create().readService(name, namespace));
    } catch (ApiException e) {
      LOGGER.warning(MessageKeys.EXCEPTION, e);
    }
    return null;
  }

  /**
   * Returns the URL to access the Service; using the Service clusterIP and port
   *
   * @param service The name of the Service that you want the URL for.
   * @return The URL of the Service or null if the URL cannot be found.
   */
  public static String getServiceURL(V1Service service) {
    if (service != null) {
      V1ServiceSpec spec = service.getSpec();
      if (spec != null) {
        String portalIP = spec.getClusterIP();
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
