// Copyright (c) 2020, 2023, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.weblogic.kubernetes;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;

import oracle.weblogic.kubernetes.annotations.IntegrationTest;
import oracle.weblogic.kubernetes.annotations.Namespaces;
import oracle.weblogic.kubernetes.logging.LoggingFacade;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static oracle.weblogic.kubernetes.TestConstants.ADMIN_PASSWORD_DEFAULT;
import static oracle.weblogic.kubernetes.TestConstants.ADMIN_PASSWORD_PATCH;
import static oracle.weblogic.kubernetes.TestConstants.ADMIN_SERVER_NAME_BASE;
import static oracle.weblogic.kubernetes.TestConstants.ADMIN_USERNAME_DEFAULT;
import static oracle.weblogic.kubernetes.TestConstants.ADMIN_USERNAME_PATCH;
import static oracle.weblogic.kubernetes.TestConstants.MANAGED_SERVER_NAME_BASE;
import static oracle.weblogic.kubernetes.TestConstants.MII_BASIC_IMAGE_NAME;
import static oracle.weblogic.kubernetes.TestConstants.MII_BASIC_IMAGE_TAG;
import static oracle.weblogic.kubernetes.TestConstants.OPERATOR_RELEASE_NAME;
import static oracle.weblogic.kubernetes.actions.TestActions.deletePod;
import static oracle.weblogic.kubernetes.actions.TestActions.getOperatorPodName;
import static oracle.weblogic.kubernetes.actions.TestActions.getPodCreationTimestamp;
import static oracle.weblogic.kubernetes.actions.TestActions.scaleCluster;
import static oracle.weblogic.kubernetes.actions.TestActions.startOperator;
import static oracle.weblogic.kubernetes.actions.TestActions.stopOperator;
import static oracle.weblogic.kubernetes.assertions.TestAssertions.isOperatorPodRestarted;
import static oracle.weblogic.kubernetes.assertions.TestAssertions.operatorIsReady;
import static oracle.weblogic.kubernetes.assertions.TestAssertions.verifyRollingRestartOccurred;
import static oracle.weblogic.kubernetes.utils.CommonMiiTestUtils.createMiiDomainAndVerify;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.checkPodReadyAndServiceExists;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.testUntil;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.verifyCredentials;
import static oracle.weblogic.kubernetes.utils.OKDUtils.createRouteForOKD;
import static oracle.weblogic.kubernetes.utils.OperatorUtils.installAndVerifyOperator;
import static oracle.weblogic.kubernetes.utils.PatchDomainUtils.checkPodRestartVersionUpdated;
import static oracle.weblogic.kubernetes.utils.PatchDomainUtils.patchDomainWithNewSecretAndVerify;
import static oracle.weblogic.kubernetes.utils.PodUtils.checkPodDoesNotExist;
import static oracle.weblogic.kubernetes.utils.PodUtils.checkPodRestarted;
import static oracle.weblogic.kubernetes.utils.SecretUtils.createSecretWithUsernamePassword;
import static oracle.weblogic.kubernetes.utils.ThreadSafeLogger.getLogger;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;


// Test to restart the operator when the server pods roll after changing the WebLogic credentials secret of a
// domain custom resource that uses model-in-image.
@DisplayName("Test to patch the model-in-image image to change WebLogic admin credentials secret")
@IntegrationTest
@Tag("olcne-mrg")
@Tag("kind-parallel")
@Tag("okd-wls-mrg")
@Tag("oke-weekly-sequential")
@Tag("oke-arm")
class ItOperatorRestart {
  private static String opNamespace = null;
  private static String domainNamespace = null;
  private static String domainUid = "domain1";

  private static String adminServerPodName = String.format("%s-%s", domainUid, ADMIN_SERVER_NAME_BASE);
  private static String managedServerPrefix = String.format("%s-%s", domainUid, MANAGED_SERVER_NAME_BASE);
  private static int replicaCount = 2;
  private static LoggingFacade logger = null;

  /**
   * Perform initialization for all the tests in this class.
   * Set up the necessary namespaces, install the operator in the first namespace, and
   * create a domain in the second namespace using the pre-created basic MII image.
   *
   * @param namespaces list of namespaces created by the IntegrationTestWatcher by the
   *           JUnit engine parameter resolution mechanism
   */
  @BeforeAll
  static void initAll(@Namespaces(2) List<String> namespaces) {
    logger = getLogger();

    // get namespaces
    assertNotNull(namespaces.get(0), "Namespace namespaces.get(0) is null");
    opNamespace = namespaces.get(0);

    assertNotNull(namespaces.get(1), "Namespace namespaces.get(1) is null");
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
   * Stop Operator and delete the admin and managed server pods.
   * Restart Operator and verify admin and managed servers are started.
   */
  @Test
  @DisplayName("Stop operator, delete all the server pods and restart operator, verify servers are started")
  void testRestartOperatorAndVerifyDomainUp() {

    // get operator pod name
    String operatorPodName = assertDoesNotThrow(
        () -> getOperatorPodName(OPERATOR_RELEASE_NAME, opNamespace));
    assertNotNull(operatorPodName, "Operator pod name returned is null");
    logger.info("Operator pod name {0}", operatorPodName);

    // stop operator by changing replica to 0 in operator deployment
    assertTrue(stopOperator(opNamespace), "Couldn't stop the Operator");

    // check operator pod is not running
    checkPodDoesNotExist(operatorPodName, null, opNamespace);

    // delete server pods
    for (int i = 1; i <= replicaCount; i++) {
      final String managedServerPodName = managedServerPrefix + i;
      logger.info("Deleting managed server {0} in namespace {1}", managedServerPodName, domainNamespace);
      assertDoesNotThrow(() -> deletePod(managedServerPodName, domainNamespace),
              "Got exception while deleting server " + managedServerPodName);
      checkPodDoesNotExist(managedServerPodName, domainUid, domainNamespace);
    }

    logger.info("deleting admin server pod");
    assertDoesNotThrow(() -> deletePod(adminServerPodName, domainNamespace),
            "Got exception while deleting admin server pod");
    checkPodDoesNotExist(adminServerPodName, domainUid, domainNamespace);

    // start operator by changing replica to 1 in operator deployment
    assertTrue(startOperator(opNamespace), "Couldn't start the Operator");

    // check operator is running
    logger.info("Check Operator pod is running in namespace {0}", opNamespace);
    testUntil(
        operatorIsReady(opNamespace),
        logger,
        "operator to be running in namespace {0}",
        opNamespace);

    logger.info("Check admin service and pod {0} is created in namespace {1}",
        adminServerPodName, domainNamespace);
    checkPodReadyAndServiceExists(adminServerPodName, domainUid, domainNamespace);

    // check managed server services and pods are ready
    for (int i = 1; i <= replicaCount; i++) {
      logger.info("Wait for managed server services and pods are created in namespace {0}",
           domainNamespace);
      checkPodReadyAndServiceExists(managedServerPrefix + i, domainUid, domainNamespace);
    }
  }

  /**
   * Stop Operator and increase the replica count for the domain.
   * Restart Operator and verify the cluster is scaled up.
   */
  @Test
  @DisplayName("Stop operator, increase replica count for the domain, restart operator and verify cluster is scaled up")
  void testRestartOperatorAndVerifyScaling() {

    // get operator pod name
    String operatorPodName = assertDoesNotThrow(
        () -> getOperatorPodName(OPERATOR_RELEASE_NAME, opNamespace));
    assertNotNull(operatorPodName, "Operator pod name returned is null");
    logger.info("Operator pod name {0}", operatorPodName);

    // stop operator by changing replica to 0 in operator deployment
    assertTrue(stopOperator(opNamespace), "Couldn't stop the Operator");

    // check operator pod is not running
    checkPodDoesNotExist(operatorPodName, null, opNamespace);

    // scale up the domain by increasing replica count
    replicaCount = 3;
    boolean scalingSuccess = scaleCluster("cluster-1", domainNamespace, replicaCount);
    assertTrue(scalingSuccess,
        String.format("Cluster scaling failed for domain %s in namespace %s", domainUid, domainNamespace));

    // start operator by changing replica to 1 in operator deployment
    assertTrue(startOperator(opNamespace), "Couldn't start the Operator");

    // check new server is started and existing servers are running
    logger.info("Check admin service and pod {0} is created in namespace {1}",
        adminServerPodName, domainNamespace);
    checkPodReadyAndServiceExists(adminServerPodName, domainUid, domainNamespace);

    // check managed server services and pods are ready
    for (int i = 1; i <= replicaCount; i++) {
      logger.info("Wait for managed server pod {0} to be ready in namespace {1}",
          managedServerPrefix + i, domainNamespace);
      checkPodReadyAndServiceExists(managedServerPrefix + i, domainUid, domainNamespace);
    }

  }

  /**
   * Test patching a running model-in-image domain with a new WebLogic credentials secret.
   * Perform two patching operations to the domain spec. First, change the webLogicCredentialsSecret to
   * a new secret, and then change the domainRestartVersion to trigger a rolling restart of the server pods.
   * While the rolling is on-going, restart the operator pod.
   * Verify that after the operator is restarted, the domain spec's webLogicCredentialsSecret and,
   * restartVersion are updated, and the server pods are recreated, the server pods' weblogic.domainRestartVersion
   * label is updated, and the new credentials are valid and can be used to access WebLogic RESTful
   * Management Services.
   */
  @Test
  @DisplayName("Restart operator when the domain is rolling after the admin credentials are changed")
  void testOperatorRestartWhenPodRoll() {
    final boolean VALID = true;
    final boolean INVALID = false;

    LinkedHashMap<String, OffsetDateTime> pods = new LinkedHashMap<>();

    // get the creation time of the admin server pod before patching
    OffsetDateTime adminPodCreationTime =
        assertDoesNotThrow(() -> getPodCreationTimestamp(domainNamespace, "", adminServerPodName),
        String.format("Failed to get creationTimestamp for pod %s", adminServerPodName));
    assertNotNull(adminPodCreationTime, "creationTimestamp of the admin server pod is null");

    logger.info("Domain {0} in namespace {1}, admin server pod {2} creationTimestamp before patching is {3}",
        domainUid,
        domainNamespace,
        adminServerPodName,
        adminPodCreationTime);

    pods.put(adminServerPodName, adminPodCreationTime);

    List<OffsetDateTime> msLastCreationTime = new ArrayList<OffsetDateTime>();
    // get the creation time of the managed server pods before patching
    assertDoesNotThrow(
        () -> {
            for (int i = 1; i <= replicaCount; i++) {
              String managedServerPodName = managedServerPrefix + i;
              OffsetDateTime creationTime = getPodCreationTimestamp(domainNamespace, "", managedServerPodName);
              msLastCreationTime.add(creationTime);
              pods.put(managedServerPodName, creationTime);

              logger.info("Domain {0} in namespace {1}, server pod {2} creationTimestamp before patching is {3}",
                  domainUid,
                  domainNamespace,
                  managedServerPodName,
                  creationTime);
            }
        },
        "Failed to get creationTimestamp for managed server pods");

    createRouteForOKD(adminServerPodName + "-ext", domainNamespace);

    logger.info("Check that before patching current credentials are valid and new credentials are not");
    verifyCredentials(7001, adminServerPodName, domainNamespace, ADMIN_USERNAME_DEFAULT,
        ADMIN_PASSWORD_DEFAULT, VALID);
    verifyCredentials(7001, adminServerPodName, domainNamespace, ADMIN_USERNAME_PATCH,
        ADMIN_PASSWORD_PATCH, INVALID);

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
        adminSecretName);

    // wait till rolling restart has started by checking admin server pod has restarted
    logger.info("Waiting for rolling restart to start by checking {0} pod is restarted in namespace {0}",
        adminServerPodName, domainNamespace);
    checkPodRestarted(domainUid, domainNamespace, adminServerPodName,
        adminPodCreationTime);

    logger.info("Delete the operator pod in namespace {0} and wait for it to be restarted", opNamespace);
    restartOperatorAndVerify();

    logger.info("Wait for domain {0} server pods in namespace {1} to be restarted",
        domainUid, domainNamespace);
    assertTrue(verifyRollingRestartOccurred(pods, 1, domainNamespace),
        "Rolling restart failed");

    for (int i = 1; i <= replicaCount; i++) {
      final String podName = managedServerPrefix + i;
      // check that the managed server pod's label has been updated with the new restartVersion
      checkPodRestartVersionUpdated(podName, domainUid, domainNamespace, restartVersion);
    }

    // check if the new credentials are valid and the old credentials are not valid any more
    logger.info("Check that after patching current credentials are not valid and new credentials are");
    verifyCredentials(7001, adminServerPodName, domainNamespace, ADMIN_USERNAME_DEFAULT,
        ADMIN_PASSWORD_DEFAULT, INVALID);
    verifyCredentials(7001, adminServerPodName, domainNamespace, ADMIN_USERNAME_PATCH,
        ADMIN_PASSWORD_PATCH, VALID);

    logger.info("Domain {0} in namespace {1} is fully started after changing WebLogic credentials secret",
        domainUid, domainNamespace);
  }

  private void restartOperatorAndVerify() {
    String opPodName = 
        assertDoesNotThrow(() -> getOperatorPodName(TestConstants.OPERATOR_RELEASE_NAME, opNamespace),
        "Failed to get the name of the operator pod");

    // get the creation time of the admin server pod before patching
    OffsetDateTime opPodCreationTime =
        assertDoesNotThrow(() -> getPodCreationTimestamp(opNamespace, "", opPodName),
            String.format("Failed to get creationTimestamp for pod %s", opPodName));
    assertNotNull(opPodCreationTime, "creationTimestamp of the operator pod is null");

    // stop operator by changing replica to 0 in operator deployment
    assertTrue(stopOperator(opNamespace), "Couldn't stop the Operator");

    // check operator pod is not running
    checkPodDoesNotExist(opPodName, null, opNamespace);

    // start operator by changing replica to 1 in operator deployment
    assertTrue(startOperator(opNamespace), "Couldn't start the Operator");

    // wait for the operator to be ready
    logger.info("Wait for the operator pod is ready in namespace {0}", opNamespace);
    testUntil(
        assertDoesNotThrow(() -> isOperatorPodRestarted(opNamespace, opPodCreationTime),
          "Failed to check if the operator is restarted with ApiException"),
        logger,
        "operator to be running in namespace {0}",
        opNamespace);

    String opPodNameNew =
        assertDoesNotThrow(() -> getOperatorPodName(TestConstants.OPERATOR_RELEASE_NAME, opNamespace),
            "Failed to get the name of the operator pod");

    assertNotEquals(opPodName, opPodNameNew,
        "The operator names before and after a restart should be different");
  }

}
