// Copyright (c) 2020, 2022, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.weblogic.kubernetes.actions.impl;

import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import io.kubernetes.client.openapi.ApiException;
import io.kubernetes.client.openapi.models.V1ObjectMeta;
import io.kubernetes.client.openapi.models.V1Secret;
import io.kubernetes.client.openapi.models.V1SecretList;
import oracle.weblogic.kubernetes.actions.impl.primitive.Kubernetes;
import oracle.weblogic.kubernetes.logging.LoggingFacade;

import static oracle.weblogic.kubernetes.TestConstants.OKD;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.testUntil;
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

    logger.info("service account = {0}", serviceAccountName);
    logger.info("namespace = {0}", namespace);
    V1SecretList secretList = listSecrets(namespace);
    for (V1Secret v1Secret : secretList.getItems()) {
      V1ObjectMeta meta = Optional.ofNullable(v1Secret).map(V1Secret::getMetadata).orElse(new V1ObjectMeta());
      Map<String, String> annotations =
          Optional.of(meta).map(V1ObjectMeta::getAnnotations).orElse(Collections.emptyMap());
      String saName = annotations.get("kubernetes.io/service-account.name");
      if (serviceAccountName.equals(saName)) {
        logger.info("secret name = {0}", meta.getName());
        return meta.getName();
      }
    }

    logger.info("No secret found for service account; creating one");
    String saSecretName = serviceAccountName + "-" + "sa-token";
    V1Secret saSecret = new V1Secret()
        .type("kubernetes.io/service-account-token")
        .metadata(new V1ObjectMeta()
            .name(saSecretName)
            .namespace(namespace)
            .putAnnotationsItem("kubernetes.io/service-account.name", serviceAccountName));

    try {
      Kubernetes.createSecret(saSecret);
    } catch (ApiException e) {
      logger.severe("failed to create secret for service account", e);
      return "";
    }

    testUntil(
        () -> hasToken(saSecretName, namespace),
        logger,
        "Waiting for token to be populated in secret");

    return saSecretName;
  }

  private static boolean hasToken(String saSecretName, String namespace) throws ApiException {
    V1Secret secret = Kubernetes.getSecret(saSecretName, namespace);
    if (secret != null) {
      Map<String, byte[]> data = Optional.of(secret).map(V1Secret::getData).orElse(Collections.emptyMap());
      return data.containsKey("token");
    }
    return false;
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
          if (OKD) {
            for (Map.Entry<String, String> annotation : v1Secret.getMetadata().getAnnotations().entrySet()) {
              if (annotation.getKey().equals("openshift.io/token-secret.value")) {
                return annotation.getValue();
              }
            }
            return null;
          } else {
            if (v1Secret.getData() != null) {
              byte[] encodedToken = v1Secret.getData().get("token");
              return Base64.getEncoder().encodeToString(encodedToken);
            }
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
