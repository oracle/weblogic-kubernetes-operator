// Copyright (c) 2020, 2021, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.weblogic.kubernetes.actions.impl;

import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

import io.kubernetes.client.openapi.ApiException;
import io.kubernetes.client.openapi.models.V1ObjectReference;
import io.kubernetes.client.openapi.models.V1Secret;
import io.kubernetes.client.openapi.models.V1SecretList;
import io.kubernetes.client.openapi.models.V1ServiceAccount;
import io.kubernetes.client.openapi.models.V1ServiceAccountList;
import oracle.weblogic.kubernetes.actions.impl.primitive.Kubernetes;
import oracle.weblogic.kubernetes.logging.LoggingFacade;

import static oracle.weblogic.kubernetes.TestConstants.OKD;
import static oracle.weblogic.kubernetes.actions.impl.primitive.Kubernetes.listServiceAccounts;
import static oracle.weblogic.kubernetes.actions.impl.primitive.Kubernetes.readSecretByReference;
import static oracle.weblogic.kubernetes.utils.ThreadSafeLogger.getLogger;

public class Secret {

  /**
   * Create a Kubernetes Secret.
   *
   * @param secret V1Secret object containing Kubernetes secret configuration data
   * @return true if successful
   * @throws ApiException if Kubernetes client API call fails
   */
  public static boolean create(V1Secret secret) throws ApiException {
    return Kubernetes.createSecret(secret);
  }

  /**
   * Delete a Kubernetes Secret.
   *
   * @param name name of the Secret
   * @param namespace name of namespace
   * @return true if successful, false otherwise
   */
  public static boolean delete(String name, String namespace) {
    return Kubernetes.deleteSecret(name, namespace);
  }

  /**
   * Get a secret of a service account in the specified namespace.
   * @param namespace namespace in which to get the secret
   * @param serviceAccountName the service account name which the secret is associated with
   * @return secret name
   */
  public static String getSecretOfServiceAccount(String namespace, String serviceAccountName) {

    LoggingFacade logger = getLogger();
    List<V1Secret> v1Secrets = new ArrayList<>();
    List<V1ServiceAccount> v1ServiceAccounts = new ArrayList<>();
    List<V1Secret> v1SaSecrets = new ArrayList<>();
    String token = null;

    if (!OKD) {
      V1SecretList secretList = listSecrets(namespace);
      if (secretList != null) {
        v1Secrets = secretList.getItems();
      }

      for (V1Secret v1Secret : v1Secrets) {
        if (v1Secret.getMetadata() != null && v1Secret.getMetadata().getName() != null) {
          logger.info(" secret name = {0}", v1Secret.getMetadata().getName());
          if (v1Secret.getMetadata().getName().startsWith(serviceAccountName)) {
            return v1Secret.getMetadata().getName();
          }
        }
      }
    } else {
      logger.info("service account = {0}", serviceAccountName);
      logger.info("namespace = {0}", namespace);
      V1ServiceAccountList serviceAccountList = listServiceAccounts(namespace);
      if (serviceAccountList != null) {
        v1ServiceAccounts = serviceAccountList.getItems();
      }

      try {
        for (V1ServiceAccount v1ServiceAccount : v1ServiceAccounts) {
          if (v1ServiceAccount.getMetadata() != null && v1ServiceAccount.getMetadata().getName() != null) {
            if (v1ServiceAccount.getMetadata().getName().startsWith(serviceAccountName)) {
              List<V1ObjectReference> saSecretList = v1ServiceAccount.getSecrets();
              for (V1ObjectReference reference : saSecretList) {
                // Get the secret.
                V1Secret secret = readSecretByReference(reference, namespace);
                logger.info("secret token = {0}", secret.getMetadata().getName());
                return secret.getMetadata().getName();
              }
            }
          }
        }
      } catch (ApiException apie) {
        logger.info(" got ApiException");
      }
    }
    return "";
  }

  /**
   * Get a secret encoded token in the specified namespace.
   *
   * @param namespace namespace in which the secret exists
   * @param secretName secret name to get the encoded token
   * @return the encoded token of the secret
   */
  public static String getSecretEncodedToken(String namespace, String secretName) {

    List<V1Secret> v1Secrets = new ArrayList<>();

    V1SecretList secretList = listSecrets(namespace);
    if (secretList != null) {
      v1Secrets = secretList.getItems();
    }

    for (V1Secret v1Secret : v1Secrets) {
      if (v1Secret.getMetadata() != null && v1Secret.getMetadata().getName() != null) {
        if (v1Secret.getMetadata().getName().equals(secretName)) {
          if (v1Secret.getData() != null) {
            byte[] encodedToken = v1Secret.getData().get("token");
            return Base64.getEncoder().encodeToString(encodedToken);
          }
        }
      }
    }

    return "";
  }

  /**
   * List secrets in the Kubernetes cluster.
   *
   * @param namespace Namespace in which to query
   * @return V1SecretList of secrets in the Kubernetes cluster
   */
  public static V1SecretList listSecrets(String namespace) {
    return Kubernetes.listSecrets(namespace);
  }
}
