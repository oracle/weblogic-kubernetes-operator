// Copyright (c) 2021, 2022, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.weblogic.kubernetes.utils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import io.kubernetes.client.openapi.models.V1Namespace;
import io.kubernetes.client.openapi.models.V1NamespaceStatus;
import io.kubernetes.client.openapi.models.V1ObjectMeta;
import io.kubernetes.client.openapi.models.V1Secret;
import oracle.weblogic.kubernetes.actions.impl.Secret;
import oracle.weblogic.kubernetes.actions.impl.primitive.Command;
import oracle.weblogic.kubernetes.actions.impl.primitive.CommandParams;
import oracle.weblogic.kubernetes.actions.impl.primitive.Kubernetes;
import oracle.weblogic.kubernetes.logging.LoggingFacade;

import static oracle.weblogic.kubernetes.TestConstants.BASE_IMAGES_REPO;
import static oracle.weblogic.kubernetes.TestConstants.BASE_IMAGES_REPO_EMAIL;
import static oracle.weblogic.kubernetes.TestConstants.BASE_IMAGES_REPO_PASSWORD;
import static oracle.weblogic.kubernetes.TestConstants.BASE_IMAGES_REPO_SECRET_NAME;
import static oracle.weblogic.kubernetes.TestConstants.BASE_IMAGES_REPO_USERNAME;
import static oracle.weblogic.kubernetes.TestConstants.DOMAIN_IMAGES_REPO;
import static oracle.weblogic.kubernetes.TestConstants.GEN_EXTERNAL_REST_IDENTITY_FILE;
import static oracle.weblogic.kubernetes.TestConstants.K8S_NODEPORT_HOST;
import static oracle.weblogic.kubernetes.TestConstants.TEST_IMAGES_REPO_EMAIL;
import static oracle.weblogic.kubernetes.TestConstants.TEST_IMAGES_REPO_PASSWORD;
import static oracle.weblogic.kubernetes.TestConstants.TEST_IMAGES_REPO_SECRET_NAME;
import static oracle.weblogic.kubernetes.TestConstants.TEST_IMAGES_REPO_USERNAME;
import static oracle.weblogic.kubernetes.actions.TestActions.createSecret;
import static oracle.weblogic.kubernetes.assertions.TestAssertions.secretExists;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.testUntil;
import static oracle.weblogic.kubernetes.utils.ImageUtils.createImageRegistrySecret;
import static oracle.weblogic.kubernetes.utils.ThreadSafeLogger.getLogger;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class SecretUtils {

  /**
   * Create a secret with TLS certificate and key in the specified namespace.
   *
   * @param secretName secret name to create
   * @param namespace namespace in which the secret will be created
   * @param keyFile key file containing key for the secret
   * @param certFile certificate file containing certificate for secret
   * @throws java.io.IOException when reading key/cert files fails
   */
  public static void createSecretWithTLSCertKey(
      String secretName, String namespace, Path keyFile, Path certFile) throws IOException {

    LoggingFacade logger = getLogger();
    logger.info("Creating TLS secret {0} in namespace {1} with certfile {2} and keyfile {3}",
        secretName, namespace, certFile, keyFile);

    Map<String, String> data = new HashMap<>();
    data.put("tls.crt", Base64.getEncoder().encodeToString(Files.readAllBytes(certFile)));
    data.put("tls.key", Base64.getEncoder().encodeToString(Files.readAllBytes(keyFile)));

    V1Secret secret = new V1Secret()
        .metadata(new V1ObjectMeta()
            .name(secretName)
            .namespace(namespace))
        .type("kubernetes.io/tls")
        .stringData(data);

    boolean secretCreated = assertDoesNotThrow(() -> createSecret(secret),
        "Create secret failed with ApiException");
    assertTrue(secretCreated, String.format("create secret failed for %s", secretName));
  }

  /**
   * Create a secret with username and password in the specified namespace.
   *
   * @param secretName secret name to create
   * @param namespace namespace in which the secret will be created
   * @param username username in the secret
   * @param password passowrd in the secret
   */
  public static void createSecretWithUsernamePassword(String secretName,
                                                      String namespace,
                                                      String username,
                                                      String password) {
    Map<String, String> secretMap = new HashMap<>();
    secretMap.put("username", username);
    secretMap.put("password", password);

    if (!secretExists(secretName, namespace)) {
      boolean secretCreated = assertDoesNotThrow(() -> createSecret(new V1Secret()
          .metadata(new V1ObjectMeta()
              .name(secretName)
              .namespace(namespace))
          .stringData(secretMap)), "Create secret failed with ApiException");

      assertTrue(secretCreated, String.format("create secret failed for %s", secretName));
    }
  }

  /**
   * Create a RcuAccess secret with RCU schema prefix, RCU schema password and RCU database connection string
   * in the specified namespace.
   *
   * @param secretName secret name to create
   * @param namespace namespace in which the secret will be created
   * @param opsswalletpassword  OPSS wallet password
   */
  public static void createOpsswalletpasswordSecret(String secretName, String namespace,
                                                    String opsswalletpassword) {
    Map<String, String> secretMap = new HashMap<>();
    secretMap.put("walletPassword", opsswalletpassword);

    boolean secretCreated = assertDoesNotThrow(() -> createSecret(new V1Secret()
        .metadata(new V1ObjectMeta()
            .name(secretName)
            .namespace(namespace))
        .stringData(secretMap)), "Create secret failed with ApiException");
    assertTrue(secretCreated, String.format("create secret failed for %s", secretName));
  }

  /**
   * Create a OPSS wallet file secret without file in the specified namespace.
   * This is for a negative test scenario
   * @param secretName secret name to create
   * @param namespace namespace in which the secret will be created
   */
  public static void createOpsswalletFileSecretWithoutFile(String secretName, String namespace) {

    boolean secretCreated = assertDoesNotThrow(() -> createSecret(new V1Secret()
        .metadata(new V1ObjectMeta()
            .name(secretName)
            .namespace(namespace))),
         "Create secret failed with ApiException");
    assertTrue(secretCreated, String.format("create secret failed for %s", secretName));
  }

  /**
   * Create a secret with username and password and Elasticsearch host and port in the specified namespace.
   *
   * @param secretName secret name to create
   * @param namespace namespace in which the secret will be created
   * @param username username in the secret
   * @param password passowrd in the secret
   * @param elasticsearchhost Elasticsearch host in the secret
   * @param elasticsearchport Elasticsearch port in the secret
   */
  public static void createSecretWithUsernamePasswordElk(String secretName,
                                                         String namespace,
                                                         String username,
                                                         String password,
                                                         String elasticsearchhost,
                                                         String elasticsearchport) {
    Map<String, String> secretMap = new HashMap<>();
    secretMap.put("username", username);
    secretMap.put("password", password);
    secretMap.put("elasticsearchhost", elasticsearchhost);
    secretMap.put("elasticsearchport", elasticsearchport);

    boolean secretCreated = assertDoesNotThrow(() -> createSecret(new V1Secret()
        .metadata(new V1ObjectMeta()
            .name(secretName)
            .namespace(namespace))
        .stringData(secretMap)), "Create secret failed with ApiException");
    assertTrue(secretCreated, String.format("create secret failed for %s", secretName));
  }

  /**
   * Create an external REST Identity secret in the specified namespace.
   *
   * @param namespace the namespace in which the secret to be created
   * @param secretName name of the secret to be created
   * @return true if the command to create secret succeeds, false otherwise
   */
  public static boolean createExternalRestIdentitySecret(String namespace, String secretName) {

    StringBuffer command = new StringBuffer()
        .append(GEN_EXTERNAL_REST_IDENTITY_FILE);
    if (K8S_NODEPORT_HOST != null && !K8S_NODEPORT_HOST.equals("<none>")) {
      if (Character.isDigit(K8S_NODEPORT_HOST.charAt(0))) {
        command.append(" -a \"IP:");
      } else {
        command.append(" -a \"DNS:");
      }
      command.append(K8S_NODEPORT_HOST);
    } else {
      command.append(" -a \"DNS:")
          .append("external-weblogic-operator-svc.")
          .append(namespace)
          .append(".svc.cluster.local");
    }
    command.append(",DNS:localhost,IP:127.0.0.1\"")
        .append(" -n ")
        .append(namespace)
        .append(" -s ")
        .append(secretName);

    CommandParams params = Command
        .defaultCommandParams()
        .command(command.toString())
        .saveResults(true)
        .redirect(true);

    return Command.withParams(params).execute();
  }

  /**
   * Verify that the namespace is active.
   */
  public static void verifyNamespaceActive() {
    final LoggingFacade logger = getLogger();

    testUntil(
        () -> {
          V1Namespace namespace = Kubernetes.getNamespace("default");
          return "Active".equals(
              Optional.ofNullable(namespace).map(V1Namespace::getStatus)
                  .map(V1NamespaceStatus::getPhase).orElse(null));
        },
        logger,
        "waiting for the default namespace to be active");
  }

  /**
   * Create multiple secrets if base images repository and domain images repository are different.
   *
   * @param namespace namespace in which to create image repository secrets
   * @return string array of secret names created
   */
  public static String[] createSecretsForImageRepos(String namespace) {
    List<String> secrets = new ArrayList<>();
    //create repo registry secret
    if (!secretExists(TEST_IMAGES_REPO_SECRET_NAME, namespace)) {
      createImageRegistrySecret(TEST_IMAGES_REPO_USERNAME, TEST_IMAGES_REPO_PASSWORD, TEST_IMAGES_REPO_EMAIL,
            DOMAIN_IMAGES_REPO, TEST_IMAGES_REPO_SECRET_NAME, namespace);
    }
    secrets.add(TEST_IMAGES_REPO_SECRET_NAME);

    if (!BASE_IMAGES_REPO.equals(DOMAIN_IMAGES_REPO)) {
      //create base images repo secret
      if (!secretExists(BASE_IMAGES_REPO_SECRET_NAME, namespace)) {
        createImageRegistrySecret(BASE_IMAGES_REPO_USERNAME, BASE_IMAGES_REPO_PASSWORD, BASE_IMAGES_REPO_EMAIL,
            BASE_IMAGES_REPO, BASE_IMAGES_REPO_SECRET_NAME, namespace);
      }
      secrets.add(BASE_IMAGES_REPO_SECRET_NAME);
    }
    return secrets.toArray(String[]::new);
  }

  /**
   * Retrieve service account token stored in secret .
   *
   * @param serviceAccount service account name
   * @param namespace namespace
   * @return string service account token stored in secret
   */
  public static String getServiceAccountToken(String serviceAccount, String namespace) {
    LoggingFacade logger = getLogger();
    logger.info("Getting the secret of service account {0} in namespace {1}", serviceAccount, namespace);
    String secretName = Secret.getSecretOfServiceAccount(namespace, serviceAccount);
    if (secretName.isEmpty()) {
      logger.info("Did not find secret of service account {0} in namespace {1}", serviceAccount, namespace);
      return null;
    }
    logger.info("Got secret {0} of service account {1} in namespace {2}",
        secretName, serviceAccount, namespace);

    logger.info("Getting service account token stored in secret {0} to authenticate as service account {1}"
        + " in namespace {2}", secretName, serviceAccount, namespace);
    String secretToken = Secret.getSecretEncodedToken(namespace, secretName);
    if (secretToken.isEmpty()) {
      logger.info("Did not get encoded token for secret {0} associated with service account {1} in namespace {2}",
          secretName, serviceAccount, namespace);
      return null;
    }
    logger.info("Got encoded token for secret {0} associated with service account {1} in namespace {2}: {3}",
        secretName, serviceAccount, namespace, secretToken);
    return secretToken;
  }
}
