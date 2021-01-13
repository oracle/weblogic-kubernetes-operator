// Copyright (c) 2017, 2021, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.authentication;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import io.kubernetes.client.openapi.ApiClient;
import io.kubernetes.client.openapi.ApiException;
import io.kubernetes.client.openapi.models.V1ObjectReference;
import io.kubernetes.client.openapi.models.V1Secret;
import io.kubernetes.client.openapi.models.V1ServiceAccount;
import io.kubernetes.client.util.Config;
import oracle.kubernetes.operator.logging.LoggingFacade;
import oracle.kubernetes.operator.logging.LoggingFactory;

/**
 * This class contains methods to authenticate to the Kubernetes API Server in different ways and to
 * create API clients with the desired credentials.
 */
public class Authenticator {

  private static final String SERVICE_HOST = "KUBERNETES_SERVICE_HOST";
  private static final String SERVICE_PORT = "KUBERNETES_SERVICE_PORT";
  private static final LoggingFacade LOGGER = LoggingFactory.getLogger("Operator", "Operator");
  private final ApiClient apiClient;
  private final Helpers helper;

  /**
   * Create a new instance of the Authenticator class containing the default API client. The default
   * client will normally use <code>~/.kube/config</code> or the file pointed to by <code>
   * $KUBECONFIG</code> outside a Kubernetes cluster, or the <code>default</code> Service Account
   * inside a Kubernetes cluster (i.e. in a Container in a Pod).
   *
   * @throws IOException if there is an API error.
   */
  public Authenticator() throws IOException {
    this.apiClient = Config.defaultClient();
    this.helper = new Helpers(this);
  }

  /**
   * Create a new instance of Authenticator with the provided API Client. Called by KubernetesClient
   * class to setup client and config objects.
   *
   * @param apiClient The API Client to create the Authenticator with.
   */
  public Authenticator(ApiClient apiClient) {
    this.apiClient = apiClient;
    this.helper = new Helpers(this);
  }

  /**
   * Get the API client.
   *
   * @return the ApiClient object.
   */
  public ApiClient getApiClient() {
    return apiClient;
  }

  /**
   * Given a serviceAccountName (restricted by namespace) create the authenticated client from
   * secrets attached to that account.
   *
   * @param serviceAccountName The name of the Service Account.
   * @param namespace The name of the Namespace.
   * @return ApiClient that has been properly authenticated.
   * @throws ApiException if there is an API error.
   */
  public ApiClient createClientByServiceAccountName(String serviceAccountName, String namespace)
      throws ApiException {

    V1ServiceAccount serviceAccount = helper.findServiceAccount(serviceAccountName, namespace);
    return authenticateByServiceAccount(serviceAccount);
  }

  /**
   * Given a V1ServiceAccount object, pull the authentication secrets and initialize a new ApiClient
   * to authenticate with those credentials.
   *
   * @param serviceAccount The name of the Service Account to authenticate with.
   * @return ApiClient An ApiClient for the given Service Account.
   * @throws ApiException if there is an API error.
   */
  private ApiClient authenticateByServiceAccount(V1ServiceAccount serviceAccount)
      throws ApiException {

    LOGGER.entering();

    byte[] caCert = null;
    String token = null;

    List<V1ObjectReference> secretList = serviceAccount.getSecrets();
    for (V1ObjectReference reference : secretList) {
      // Get the secret.
      V1Secret secret =
          helper.readSecretByReference(reference, serviceAccount.getMetadata().getNamespace());
      Map<String, byte[]> secretMap = secret.getData();
      for (Entry<String, byte[]> entry : secretMap.entrySet()) {
        if (entry.getKey().equals("ca.crt")) {
          caCert = entry.getValue();
        }
        if (entry.getKey().equals("token")) {
          token = new String(entry.getValue());
        }
      }
    }
    String serviceToken = token;

    String serviceHost = System.getenv(SERVICE_HOST);
    String servicePort = System.getenv(SERVICE_PORT);
    String serviceUrl = "https://" + serviceHost + ":" + servicePort;

    ApiClient newClient = new ApiClient();
    newClient.setBasePath(serviceUrl);
    newClient.setApiKey("Bearer " + token);
    newClient.setSslCaCert(new ByteArrayInputStream(caCert));

    LOGGER.exiting(newClient);
    return newClient;
  }

}
