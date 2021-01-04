// Copyright (c) 2020, 2021, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.weblogic.kubernetes.actions.impl;

import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

import io.kubernetes.client.openapi.ApiException;
import io.kubernetes.client.openapi.models.V1Secret;
import io.kubernetes.client.openapi.models.V1SecretList;
import oracle.weblogic.kubernetes.actions.impl.primitive.Kubernetes;

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

    List<V1Secret> v1Secrets = new ArrayList<>();

    V1SecretList secretList = listSecrets(namespace);
    if (secretList != null) {
      v1Secrets = secretList.getItems();
    }

    for (V1Secret v1Secret : v1Secrets) {
      if (v1Secret.getMetadata() != null && v1Secret.getMetadata().getName() != null) {
        if (v1Secret.getMetadata().getName().startsWith(serviceAccountName)) {
          return v1Secret.getMetadata().getName();
        }
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
