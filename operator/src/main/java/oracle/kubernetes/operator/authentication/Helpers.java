// Copyright 2017, 2018, Oracle Corporation and/or its affiliates.  All rights reserved.
// Licensed under the Universal Permissive License v 1.0 as shown at
// http://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.authentication;

import io.kubernetes.client.ApiClient;
import io.kubernetes.client.ApiException;
import io.kubernetes.client.apis.CoreV1Api;
import io.kubernetes.client.models.V1ObjectReference;
import io.kubernetes.client.models.V1Secret;
import io.kubernetes.client.models.V1ServiceAccount;
import io.kubernetes.client.models.V1ServiceAccountList;
import java.util.ArrayList;
import java.util.Map;
import java.util.Map.Entry;
import oracle.kubernetes.operator.logging.LoggingFacade;
import oracle.kubernetes.operator.logging.LoggingFactory;
import org.apache.commons.codec.binary.Base64;

/**
 * This class provides helper methods for getting Service Accounts and Secrets for authentication
 * purposes.
 */
public class Helpers {

  @SuppressWarnings("unused")
  private final Authenticator authenticator;

  private final ApiClient apiClient;
  private final CoreV1Api coreApi;
  private static final LoggingFacade LOGGER = LoggingFactory.getLogger("Operator", "Operator");

  public Helpers(Authenticator authenticator) {
    this.authenticator = authenticator;
    apiClient = authenticator.getApiClient();
    coreApi = new CoreV1Api(apiClient);
  }

  /**
   * Find the servivce account by name.
   *
   * @param serviceAccountName The name of the Service Account.
   * @param namespace The Namespace the Service Account is defined in.
   * @return V1ServiceAccount object that matches the requested Service Account name and Namespace
   *     (if found).
   * @throws ApiException if an API error occurs.
   */
  protected V1ServiceAccount findServiceAccount(String serviceAccountName, String namespace)
      throws ApiException {

    LOGGER.entering();

    // list all service accounts and look for the one we want.
    // But make sure there are no duplicates spread across
    // multiple namespaces if a specific name space is not specified
    V1ServiceAccountList serviceAccountList = getAllServiceAccounts();
    ArrayList<V1ServiceAccount> sas = new ArrayList<>();
    if (serviceAccountList != null) {
      for (V1ServiceAccount sa : serviceAccountList.getItems()) {
        String name = sa.getMetadata().getName();
        if (name.equals(serviceAccountName)) {
          if (namespace != null) {
            String ns = sa.getMetadata().getNamespace();
            if (ns.equals(namespace)) {
              LOGGER.exiting(sa);
              return sa;
            }
          }
          sas.add(sa);
        }
      }
    }
    if (sas.isEmpty()) {
      ApiException e = new ApiException("serviceAccount " + serviceAccountName + " not found");
      LOGGER.throwing(e);
      throw e;
    }

    if (sas.size() > 1) {
      ApiException e =
          new ApiException(
              "serviceAccount " + serviceAccountName + " appears in more than one namespace");
      LOGGER.throwing(e);
      throw e;
    }

    V1ServiceAccount result = sas.get(0);
    LOGGER.exiting(result);
    return result;
  }

  /**
   * Get a list of all Service Accounts on this cluster. Only looking at the first 4K accounts.
   *
   * @return A list of Service Accounts.
   * @throws ApiException on API Exception
   */
  protected V1ServiceAccountList getAllServiceAccounts() throws ApiException {

    V1ServiceAccountList serviceAccountList = null;

    String cont = "";

    serviceAccountList =
        coreApi.listServiceAccountForAllNamespaces(
            cont, // continue option
            "", // field selector
            Boolean.FALSE, // includeUninitialized
            "", // labelSelector
            4096, // limit size for list
            "false", // pretty
            "", // resourceVersion
            0, // timeout (seconds)
            Boolean.FALSE // watch indicator
            );

    return serviceAccountList;
  }

  /**
   * Find the service account by supplied token.
   *
   * @param token authentication token to search for
   * @return V1ServiceAccount where token is secreted
   * @throws ApiException if there is an API error
   */
  protected V1ServiceAccount findServiceAccountByToken(String token) throws ApiException {

    LOGGER.entering();

    V1ServiceAccountList serviceAccounts = getAllServiceAccounts();

    for (V1ServiceAccount serviceAccount : serviceAccounts.getItems()) {
      for (V1ObjectReference reference : serviceAccount.getSecrets()) {
        V1Secret secret =
            readSecretByReference(reference, serviceAccount.getMetadata().getNamespace());
        Map<String, byte[]> secretMap = secret.getData();
        for (Entry<String, byte[]> entry : secretMap.entrySet()) {
          String secretToken = new String(entry.getValue());
          if (entry.getKey().equals("token") && token.equals(secretToken)) {
            LOGGER.exiting(serviceAccount);
            return serviceAccount;
          }
        }
      }
    }
    ApiException e = new ApiException("token does not match any secret");
    LOGGER.throwing(e);
    throw e;
  }

  /**
   * Read a secret by its object reference.
   *
   * @param reference V1ObjectReference An object reference to the Secret you want to read.
   * @param namespace The Namespace where Secret is defined.
   * @return V1Secret The requested Secret.
   * @throws ApiException if there is an API error.
   */
  protected V1Secret readSecretByReference(V1ObjectReference reference, String namespace)
      throws ApiException {

    LOGGER.entering();

    if (reference.getNamespace() != null) {
      namespace = reference.getNamespace();
    }

    V1Secret secret =
        coreApi.readNamespacedSecret(
            reference.getName(), namespace, "false", Boolean.TRUE, Boolean.TRUE);

    LOGGER.exiting(secret);
    return secret;
  }

  // decode base64
  protected byte[] decodeSecret(byte[] encoded) {
    return Base64.decodeBase64(encoded);
  }

  // encode base64
  protected byte[] encodeSecret(byte[] decoded) {
    return Base64.encodeBase64(decoded);
  }
}
