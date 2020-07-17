// Copyright (c) 2020, Oracle Corporation and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.weblogic.kubernetes;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;

import oracle.weblogic.kubernetes.annotations.IntegrationTest;
import oracle.weblogic.kubernetes.annotations.Namespaces;
import oracle.weblogic.kubernetes.annotations.tags.MustNotRunInParallel;
import oracle.weblogic.kubernetes.annotations.tags.Slow;
import oracle.weblogic.kubernetes.logging.LoggingFacade;
import org.awaitility.core.ConditionFactory;
import org.joda.time.DateTime;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import static java.util.concurrent.TimeUnit.MINUTES;
import static java.util.concurrent.TimeUnit.SECONDS;
import static oracle.weblogic.kubernetes.TestConstants.ADMIN_PASSWORD_DEFAULT;
import static oracle.weblogic.kubernetes.TestConstants.ADMIN_PASSWORD_PATCH;
import static oracle.weblogic.kubernetes.TestConstants.ADMIN_SERVER_NAME_BASE;
import static oracle.weblogic.kubernetes.TestConstants.ADMIN_USERNAME_DEFAULT;
import static oracle.weblogic.kubernetes.TestConstants.ADMIN_USERNAME_PATCH;
import static oracle.weblogic.kubernetes.TestConstants.MANAGED_SERVER_NAME_BASE;
import static oracle.weblogic.kubernetes.TestConstants.MII_BASIC_IMAGE_NAME;
import static oracle.weblogic.kubernetes.TestConstants.MII_BASIC_IMAGE_TAG;
import static oracle.weblogic.kubernetes.actions.TestActions.getPodCreationTimestamp;
import static oracle.weblogic.kubernetes.assertions.TestAssertions.verifyRollingRestartOccurred;
import static oracle.weblogic.kubernetes.utils.CommonMiiTestUtils.createMiiDomainAndVerify;
import static oracle.weblogic.kubernetes.utils.CommonPatchTestUtils.checkPodRestartVersionUpdated;
import static oracle.weblogic.kubernetes.utils.CommonPatchTestUtils.patchDomainWithNewSecretAndVerify;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.createSecretWithUsernamePassword;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.installAndVerifyOperator;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.verifyCredentials;
import static oracle.weblogic.kubernetes.utils.ThreadSafeLogger.getLogger;
import static org.awaitility.Awaitility.with;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

// Test to change the WebLogic credentials secret of a domain custom resource that uses model-in-image.
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DisplayName("Test to patch the model-in-image image to change WebLogic admin credentials secret")
@IntegrationTest
class ItMiiChangeAdminCredentials {

  private static String domainNamespace = null;
  private static String domainUid = "domain1";
  private static ConditionFactory withStandardRetryPolicy = null;
  private static ConditionFactory withQuickRetryPolicy = null;

  private static String adminServerPodName = String.format("%s-%s", domainUid, ADMIN_SERVER_NAME_BASE);
  private static String managedServerPrefix = String.format("%s-%s", domainUid, MANAGED_SERVER_NAME_BASE);
  private static int replicaCount = 2;
  private static LoggingFacade logger = null;

  /**
   * Perform initialization for all the tests in this class.
   * Set up the necessary namespaces, install the operator in the first namespace, and
   * create a domain in the second namespace using the pre-created basic MII image.
   * @param namespaces list of namespaces created by the IntegrationTestWatcher by the
   *                   JUnit engine parameter resolution mechanism
   */
  @BeforeAll
  public static void initAll(@Namespaces(2) List<String> namespaces) {
    logger = getLogger();
    // create standard, reusable retry/backoff policy
    withStandardRetryPolicy = with().pollDelay(2, SECONDS)
        .and().with().pollInterval(10, SECONDS)
        .atMost(6, MINUTES).await();

    // create quick, reusable retry/backoff policy
    withQuickRetryPolicy = with().pollDelay(0, SECONDS)
        .and().with().pollInterval(3, SECONDS)
        .atMost(12, SECONDS).await();

    // get namespaces
    assertNotNull(namespaces.get(0), String.format("Namespace namespaces.get(0) is null"));
    String opNamespace = namespaces.get(0);

    assertNotNull(namespaces.get(1), String.format("Namespace namespaces.get(1) is null"));
    domainNamespace = namespaces.get(1);

    // install the operator
    logger.info("Install an operator in namespace {0}, managing namespace {1}",
        opNamespace, domainNamespace);
    installAndVerifyOperator(opNamespace, domainNamespace);

    // create a domain resource
    logger.info("Create model-in-image domain {0} in namespace {1}, and wait until it comes up",
        domainUid, domainNamespace);
    createMiiDomainAndVerify(
        domainNamespace,
        domainUid,
        MII_BASIC_IMAGE_NAME + ":" + MII_BASIC_IMAGE_TAG,
        adminServerPodName,
        managedServerPrefix,
        replicaCount);
  }

  /**
   * Test patching a running model-in-image domain with a new WebLogic credentials secret.
   * Perform two patching operations to the domain spec. First, change the webLogicCredentialsSecret to
   * a new secret, and then change the domainRestartVersion to trigger a rolling restart of the server pods.
   * Verify that the domain spec's webLogicCredentialsSecret and restartVersion are updated,
   * the server pods are recreated by checking each pod's creationTimestamp before and after patching,
   * the server pods' weblogic.domainRestartVersion label is updated, and
   * the new credentials are valid and can be used to access WebLogic RESTful Management Services.
   */
  @Test
  @DisplayName("Change the WebLogic credentials")
  @Slow
  @MustNotRunInParallel
  public void testChangeWebLogicCredentials() {
    final boolean VALID = true;
    final boolean INVALID = false;

    LinkedHashMap<String, DateTime> pods = new LinkedHashMap<>();
    // get the creation time of the admin server pod before patching
    DateTime adminPodCreationTime =
        assertDoesNotThrow(() -> getPodCreationTimestamp(domainNamespace,"",adminServerPodName),
        String.format("Failed to get creationTimestamp for pod %s", adminServerPodName));
    assertNotNull(adminPodCreationTime, "creationTimestamp of the admin server pod is null");

    logger.info("Domain {0} in namespace {1}, admin server pod {2} creationTimestamp before patching is {3}",
        domainUid,
        domainNamespace,
        adminServerPodName,
        adminPodCreationTime);

    pods.put(adminServerPodName, adminPodCreationTime);

    List<DateTime> msLastCreationTime = new ArrayList<DateTime>();
    // get the creation time of the managed server pods before patching
    assertDoesNotThrow(
        () -> {
          for (int i = 1; i <= replicaCount; i++) {
            String managedServerPodName = managedServerPrefix + i;
            DateTime creationTime = getPodCreationTimestamp(domainNamespace,"", managedServerPodName);
            msLastCreationTime.add(creationTime);
            pods.put(managedServerPodName, creationTime);

            logger.info("Domain {0} in namespace {1}, managed server pod {2} creationTimestamp before patching is {3}",
                domainUid,
                domainNamespace,
                managedServerPodName,
                creationTime);
          }
        },
        String.format("Failed to get creationTimestamp for managed server pods"));

    logger.info("Check that before patching current credentials are valid and new credentials are not");
    verifyCredentials(adminServerPodName, domainNamespace, ADMIN_USERNAME_DEFAULT, ADMIN_PASSWORD_DEFAULT, VALID);
    verifyCredentials(adminServerPodName, domainNamespace, ADMIN_USERNAME_PATCH, ADMIN_PASSWORD_PATCH, INVALID);

    // create a new secret for admin credentials
    logger.info("Create a new secret that contains new WebLogic admin credentials");
    String adminSecretName = "weblogic-credentials-new";
    assertDoesNotThrow(() -> createSecretWithUsernamePassword(
        adminSecretName,
        domainNamespace,
        ADMIN_USERNAME_PATCH,
        ADMIN_PASSWORD_PATCH),
        String.format("createSecret failed for %s", adminSecretName));

    // patch the domain resource with the new secret and verify that the domain resource is patched.
    logger.info("Patch domain {0} in namespace {1} with the secret {2}, and verify the result",
        domainUid, domainNamespace, adminSecretName);

    String restartVersion = patchDomainWithNewSecretAndVerify(
        domainUid,
        domainNamespace,
        adminServerPodName,
        managedServerPrefix,
        replicaCount,
        adminSecretName);

    logger.info("Wait for domain {0} admin server pod {1} in namespace {2} to be restarted",
        domainUid, adminServerPodName, domainNamespace);

    assertTrue(assertDoesNotThrow(
        () -> (verifyRollingRestartOccurred(pods, 1, domainNamespace)),
         "More than one pod was restarted at same time"),
        "Rolling restart failed");

    for (int i = 1; i <= replicaCount; i++) {
      final String podName = managedServerPrefix + i;
      final DateTime lastCreationTime = msLastCreationTime.get(i - 1);
      // check that the managed server pod's label has been updated with the new restartVersion
      checkPodRestartVersionUpdated(podName, domainUid, domainNamespace, restartVersion);
    }

    // check if the new credentials are valid and the old credentials are not valid any more
    logger.info("Check that after patching current credentials are not valid and new credentials are");
    verifyCredentials(adminServerPodName, domainNamespace, ADMIN_USERNAME_DEFAULT, ADMIN_PASSWORD_DEFAULT, INVALID);
    verifyCredentials(adminServerPodName, domainNamespace, ADMIN_USERNAME_PATCH, ADMIN_PASSWORD_PATCH, VALID);

    logger.info("Domain {0} in namespace {1} is fully started after changing WebLogic credentials secret",
        domainUid, domainNamespace);
  }

}
