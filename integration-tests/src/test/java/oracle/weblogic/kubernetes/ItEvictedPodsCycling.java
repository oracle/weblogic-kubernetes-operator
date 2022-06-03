// Copyright (c) 2022, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.weblogic.kubernetes;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import io.kubernetes.client.custom.Quantity;
import io.kubernetes.client.openapi.models.V1ResourceRequirements;
import oracle.weblogic.domain.Domain;
import oracle.weblogic.kubernetes.annotations.IntegrationTest;
import oracle.weblogic.kubernetes.annotations.Namespaces;
import oracle.weblogic.kubernetes.logging.LoggingFacade;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static oracle.weblogic.kubernetes.TestConstants.ADMIN_PASSWORD_DEFAULT;
import static oracle.weblogic.kubernetes.TestConstants.ADMIN_SERVER_NAME_BASE;
import static oracle.weblogic.kubernetes.TestConstants.ADMIN_USERNAME_DEFAULT;
import static oracle.weblogic.kubernetes.TestConstants.MANAGED_SERVER_NAME_BASE;
import static oracle.weblogic.kubernetes.TestConstants.MII_BASIC_IMAGE_NAME;
import static oracle.weblogic.kubernetes.TestConstants.MII_BASIC_IMAGE_TAG;
import static oracle.weblogic.kubernetes.TestConstants.TEST_IMAGES_REPO_SECRET_NAME;
import static oracle.weblogic.kubernetes.utils.CommonMiiTestUtils.createDomainResource;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.checkPodEvictedStatus;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.checkPodReadyAndServiceExists;
import static oracle.weblogic.kubernetes.utils.DomainUtils.createDomainAndVerify;
import static oracle.weblogic.kubernetes.utils.ImageUtils.createTestRepoSecret;
import static oracle.weblogic.kubernetes.utils.OperatorUtils.installAndVerifyOperator;
import static oracle.weblogic.kubernetes.utils.SecretUtils.createSecretWithUsernamePassword;
import static oracle.weblogic.kubernetes.utils.ThreadSafeLogger.getLogger;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Use Operator log to test WLS server pods were evicted due to Pod ephemeral local
 * storage usage exceeds the total limit of containers 5M and replaced.
 * 1. Install and start Operators
 * 2. Create and start the WebLogic domain with configuration of resource limit "ephemeral-storage=5M"
 * 3. Verify that WLS server pods were evicted due to Pod ephemeral local
 *    storage usage exceeds the total limit of containers 5M and replaced.
 */
@DisplayName("Test WLS server pods were evicted due to Pod ephemeral storage usage exceeds the total limit")
@IntegrationTest
class ItEvictedPodsCycling {

  // constants for Domain
  private static String domainUid = "domain1";
  private static String adminServerPodName = String.format("%s-%s", domainUid, ADMIN_SERVER_NAME_BASE);
  private static String managedServerPodPrefix = String.format("%s-%s", domainUid, MANAGED_SERVER_NAME_BASE);
  private static String clusterName = "cluster-1";
  private static int replicaCount = 2;

  private static String opNamespace = null;
  private static String domainNamespace = null;

  private static Map<String, Quantity> resourceRequest = new HashMap<>();
  private static Map<String, Quantity> resourceLimit = new HashMap<>();
  private static final String ephemeralStorage = "5M";

  private static LoggingFacade logger = null;

  /**
   * Install Operator.
   * Config resource limit and set ephemeral-storage=5M
   * Create domain.
   *
   * @param namespaces list of namespaces created by the IntegrationTestWatcher by the
   *                   JUnit engine parameter resolution mechanism.
   */
  @BeforeAll
  public static void init(@Namespaces(2) List<String> namespaces) {
    logger = getLogger();

    // get a new unique opNamespace
    logger.info("Assigning a unique namespace for Operator");
    assertNotNull(namespaces.get(0), "Namespace list is null");
    opNamespace = namespaces.get(0);

    // get a new unique domainNamespace
    logger.info("Assigning a unique namespace for Domain");
    assertNotNull(namespaces.get(1), "Namespace list is null");
    domainNamespace = namespaces.get(1);

    // install and verify Operator
    installAndVerifyOperator(opNamespace, domainNamespace);

    // create a domain resource
    logger.info("Create model-in-image domain {0} in namespace {1}, and wait until it comes up",
        domainUid, domainNamespace);
    createAndVerifyDomain();
  }

  /**
   * Use Operator log to verify that WLS server pods were evicted due to Pod ephemeral local
   * storage usage exceeds the total limit of containers 5M and replaced.
   */
  @Test
  @DisplayName("Use Operator log to verify that WLS server pods were evicted and replaced")
  void testEvictedPodReplaced() {
    // verify that admin server pod evicted status exists in Operator log
    checkPodEvictedStatus(opNamespace, adminServerPodName, ephemeralStorage);

    // verify that managed server pod evicted status exists in Operator log
    for (int i = 1; i <= replicaCount; i++) {
      checkPodEvictedStatus(opNamespace, managedServerPodPrefix + i, ephemeralStorage);
    }

    // verify admin server and managed server pods are replaced and started again
    checkServerPodsAndServiceReady();
  }

  private static Domain createAndVerifyDomain() {
    LoggingFacade logger = getLogger();
    // this secret is used only for non-kind cluster
    logger.info("Create the repo secret {0} to pull the image", TEST_IMAGES_REPO_SECRET_NAME);
    assertDoesNotThrow(() -> createTestRepoSecret(domainNamespace),
        String.format("createSecret failed for %s", TEST_IMAGES_REPO_SECRET_NAME));

    // create secret for admin credentials
    logger.info("Create secret for admin credentials");
    String adminSecretName = "weblogic-credentials";
    assertDoesNotThrow(() -> createSecretWithUsernamePassword(
        adminSecretName,
        domainNamespace,
        ADMIN_USERNAME_DEFAULT,
        ADMIN_PASSWORD_DEFAULT),
        String.format("createSecret failed for %s", adminSecretName));

    // create encryption secret
    logger.info("Create encryption secret");
    String encryptionSecretName = "encryptionsecret";
    assertDoesNotThrow(() -> createSecretWithUsernamePassword(
        encryptionSecretName,
        domainNamespace,
        "weblogicenc",
        "weblogicenc"),
        String.format("createSecret failed for %s", encryptionSecretName));

    resourceRequest.put("cpu", new Quantity("250m"));
    resourceRequest.put("memory", new Quantity("768Mi"));
    resourceLimit.put("ephemeral-storage", new Quantity(ephemeralStorage));

    // create the domain custom resource
    logger.info("Create domain resource {0} object in namespace {1} and verify that it is created",
        domainUid, domainNamespace);
    Domain domain = createDomainResource(
        domainUid,
        domainNamespace,
        MII_BASIC_IMAGE_NAME + ":" + MII_BASIC_IMAGE_TAG,
        adminSecretName,
        new String[]{TEST_IMAGES_REPO_SECRET_NAME},
        encryptionSecretName,
        replicaCount,
        clusterName);

    domain.spec()
        .serverPod()
            .resources(new V1ResourceRequirements()
                .requests(resourceRequest)
                .limits(resourceLimit));

    createDomainAndVerify(domain, domainNamespace);

    checkServerPodsAndServiceReady();

    return domain;
  }

  private static void checkServerPodsAndServiceReady() {
    // check admin server pod is ready
    logger.info("Wait for admin server pod {0} to be ready in namespace {1}",
        adminServerPodName, domainNamespace);
    checkPodReadyAndServiceExists(adminServerPodName, domainUid, domainNamespace);

    // check managed server pods are ready
    for (int i = 1; i <= replicaCount; i++) {
      logger.info("Wait for managed server pod {0} to be ready in namespace {1}",
          managedServerPodPrefix + i, domainNamespace);
      checkPodReadyAndServiceExists(managedServerPodPrefix + i, domainUid, domainNamespace);
    }
  }
}
