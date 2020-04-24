// Copyright (c) 2020, Oracle Corporation and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.weblogic.kubernetes;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.google.gson.JsonObject;
import io.kubernetes.client.openapi.models.V1ObjectMeta;
import io.kubernetes.client.openapi.models.V1Secret;
import io.kubernetes.client.openapi.models.V1ServiceAccount;
import oracle.weblogic.kubernetes.actions.impl.Operator;
import oracle.weblogic.kubernetes.actions.impl.OperatorParams;
import oracle.weblogic.kubernetes.actions.impl.primitive.HelmParams;
import oracle.weblogic.kubernetes.annotations.IntegrationTest;
import oracle.weblogic.kubernetes.annotations.Namespaces;
import oracle.weblogic.kubernetes.annotations.tags.MustNotRunInParallel;
import oracle.weblogic.kubernetes.annotations.tags.Slow;
import oracle.weblogic.kubernetes.extensions.LoggedTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import static java.util.concurrent.TimeUnit.MINUTES;
import static java.util.concurrent.TimeUnit.SECONDS;
import static oracle.weblogic.kubernetes.TestConstants.OPERATOR_CHART_DIR;
import static oracle.weblogic.kubernetes.TestConstants.OPERATOR_RELEASE_NAME;
import static oracle.weblogic.kubernetes.actions.TestActions.createDockerConfigJson;
import static oracle.weblogic.kubernetes.actions.TestActions.createSecret;
import static oracle.weblogic.kubernetes.actions.TestActions.createServiceAccount;
import static oracle.weblogic.kubernetes.actions.TestActions.deleteNamespace;
import static oracle.weblogic.kubernetes.actions.TestActions.deleteServiceAccount;
import static oracle.weblogic.kubernetes.actions.TestActions.helmList;
import static oracle.weblogic.kubernetes.actions.TestActions.installOperator;
import static oracle.weblogic.kubernetes.actions.TestActions.uninstallOperator;
import static oracle.weblogic.kubernetes.assertions.TestAssertions.operatorIsRunning;
import static org.awaitility.Awaitility.with;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

// Test to install Operator and verify Operator is running
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DisplayName("Simple validation of basic operator functions")
@IntegrationTest
class ItSimpleOperatorValidation implements LoggedTest {

  private HelmParams opHelmParams = null;
  private V1ServiceAccount serviceAccount = null;
  private String opNamespace = null;
  private String domainNamespace1 = null;
  private String domainNamespace2 = null;


  /**
   * Install Operator and verify the Operator is running.
   */
  @Test
  @Order(1)
  @DisplayName("Install the operator")
  @Slow
  @MustNotRunInParallel
  public void testInstallingOperator(@Namespaces(3) List<String> namespaces) {
    // get unique namespaces for operator and domains
    namespaces.forEach((namespace) -> {
      assertNotNull(namespace, "Namespace " + namespace + "is null");
    });
    opNamespace = namespaces.get(0);
    domainNamespace1 = namespaces.get(1);
    domainNamespace2 = namespaces.get(2);

    // Create a service account for the unique opNamespace
    logger.info("Creating service account");
    String serviceAccountName = opNamespace + "-sa";
    assertDoesNotThrow(() -> createServiceAccount(new V1ServiceAccount()
        .metadata(
            new V1ObjectMeta()
                .namespace(opNamespace)
                .name(serviceAccountName))));
    logger.info("Created service account: {0}", serviceAccountName);

    String image = Operator.getImageName();
    assertFalse(image.isEmpty(), "Operator image name can not be empty");
    logger.info("Operator image name {0}", image);

    // Create docker registry secret in the operator namespace to pull the image from repository
    String repoRegistry = "dummy";
    String repoUserName = "dummy";
    String repoPassword = "dummy";
    String repoEmail = "dummy";
    String repoSecretName = "ocir-secret";
    if (System.getenv("REPO_REGISTRY") != null && System.getenv("REPO_USERNAME") != null
        && System.getenv("REPO_PASSWORD") != null && System.getenv("REPO_EMAIL") != null) {
      repoRegistry = System.getenv("REPO_REGISTRY");
      repoUserName = System.getenv("REPO_USERNAME");
      repoPassword = System.getenv("REPO_PASSWORD");
      repoEmail = System.getenv("REPO_EMAIL");
    }
    logger.info("Creating docker registry secret in namespace {0}", opNamespace);
    JsonObject dockerConfigJsonObject = createDockerConfigJson(
        repoUserName, repoPassword, repoEmail, repoRegistry);
    String dockerConfigJson = dockerConfigJsonObject.toString();

    // Create the V1Secret configuration
    V1Secret repoSecret = new V1Secret()
        .metadata(new V1ObjectMeta()
            .name(repoSecretName)
            .namespace(opNamespace))
        .type("kubernetes.io/dockerconfigjson")
        .putDataItem(".dockerconfigjson", dockerConfigJson.getBytes());

    boolean secretCreated = assertDoesNotThrow(() -> createSecret(repoSecret),
        String.format("createSecret failed for %s", repoSecretName));
    assertTrue(secretCreated, String.format("createSecret failed while creating secret %s", repoSecretName));

    // map with secret
    Map<String, Object> secretNameMap = new HashMap<String, Object>();
    secretNameMap.put("name", repoSecretName);

    // helm install parameters
    opHelmParams = new HelmParams()
        .releaseName(OPERATOR_RELEASE_NAME)
        .namespace(opNamespace)
        .chartDir(OPERATOR_CHART_DIR);

    // Operator chart values to override
    OperatorParams opParams =
        new OperatorParams()
            .helmParams(opHelmParams)
            .image(image)
            .imagePullSecrets(secretNameMap)
            .domainNamespaces(Arrays.asList(domainNamespace1, domainNamespace2))
            .serviceAccount(serviceAccountName);

    // install Operator
    logger.info("Installing Operator in namespace {0}", opNamespace);
    assertTrue(installOperator(opParams),
        String.format("Operator install failed in namespace %s", opNamespace));
    logger.info("Operator installed in namespace {0}", opNamespace);

    // list helm releases
    logger.info("List helm releases in namespace {0}", opNamespace);
    helmList(opHelmParams);

    // check operator is running
    logger.info("Check Operator pod is running in namespace {0}", opNamespace);
    with().pollDelay(2, SECONDS)
        .and().with().pollInterval(10, SECONDS)
        .atMost(5, MINUTES).await()
        .conditionEvaluationListener(
            condition -> logger.info("Waiting for operator to be running in namespace {0} "
                    + "(elapsed time {1}ms, remaining time {2}ms)",
                opNamespace,
                condition.getElapsedTimeInMS(),
                condition.getRemainingTimeInMS()))
        .until(operatorIsRunning(opNamespace));

  }

  @AfterEach
  public void tearDown() {
    // To Do: Remove this after we have common cleanup
    // uninstall operator release
    logger.info("Uninstall Operator in namespace {0}", opNamespace);
    if (opHelmParams != null) {
      uninstallOperator(opHelmParams);
    }
    // Delete service account from unique opNamespace
    logger.info("Delete service account in namespace {0}", opNamespace);
    if (serviceAccount != null) {
      assertDoesNotThrow(() -> deleteServiceAccount(serviceAccount.getMetadata().getName(),
          serviceAccount.getMetadata().getNamespace()),
          "deleteServiceAccount failed with ApiException");
    }
    // Delete domain namespaces
    logger.info("Deleting domain namespace {0}", domainNamespace1);
    if (domainNamespace1 != null) {
      assertDoesNotThrow(() -> deleteNamespace(domainNamespace1),
          "deleteNamespace failed with ApiException");
      logger.info("Deleted namespace: " + domainNamespace1);
    }

    logger.info("Deleting domain namespace {0}", domainNamespace2);
    if (domainNamespace2 != null) {
      assertDoesNotThrow(() -> deleteNamespace(domainNamespace2),
          "deleteNamespace failed with ApiException");
      logger.info("Deleted namespace: " + domainNamespace2);
    }

    // Delete opNamespace
    logger.info("Deleting Operator namespace {0}", opNamespace);
    if (opNamespace != null) {
      assertDoesNotThrow(() -> deleteNamespace(opNamespace),
          "deleteNamespace failed with ApiException");
      logger.info("Deleted namespace: " + opNamespace);
    }
  }

}
