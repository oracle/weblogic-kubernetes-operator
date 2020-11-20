// Copyright (c) 2020, Oracle Corporation and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.weblogic.kubernetes;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import oracle.weblogic.kubernetes.annotations.IntegrationTest;
import oracle.weblogic.kubernetes.annotations.Namespaces;
import oracle.weblogic.kubernetes.logging.LoggingFacade;
import oracle.weblogic.kubernetes.utils.ExecResult;
import org.awaitility.core.ConditionFactory;
import org.joda.time.DateTime;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import static java.util.concurrent.TimeUnit.MINUTES;
import static java.util.concurrent.TimeUnit.SECONDS;
import static oracle.weblogic.kubernetes.TestConstants.DOMAIN_VERSION;
import static oracle.weblogic.kubernetes.TestConstants.MANAGED_SERVER_NAME_BASE;
import static oracle.weblogic.kubernetes.TestConstants.MII_BASIC_IMAGE_NAME;
import static oracle.weblogic.kubernetes.TestConstants.MII_BASIC_IMAGE_TAG;
import static oracle.weblogic.kubernetes.TestConstants.OCIR_SECRET_NAME;
import static oracle.weblogic.kubernetes.actions.TestActions.patchDomainResourceWithNewIntrospectVersion;
import static oracle.weblogic.kubernetes.assertions.TestAssertions.domainExists;
import static oracle.weblogic.kubernetes.utils.CommonMiiTestUtils.checkWorkManagerRuntime;
import static oracle.weblogic.kubernetes.utils.CommonMiiTestUtils.createDatabaseSecret;
import static oracle.weblogic.kubernetes.utils.CommonMiiTestUtils.createDomainResourceWithLogHome;
import static oracle.weblogic.kubernetes.utils.CommonMiiTestUtils.createDomainSecret;
import static oracle.weblogic.kubernetes.utils.CommonMiiTestUtils.createJobToChangePermissionsOnPvHostPath;
import static oracle.weblogic.kubernetes.utils.CommonMiiTestUtils.readMaxThreadsConstraintRuntimeForWorkManager;
import static oracle.weblogic.kubernetes.utils.CommonMiiTestUtils.readMinThreadsConstraintRuntimeForWorkManager;
import static oracle.weblogic.kubernetes.utils.CommonMiiTestUtils.replaceConfigMapWithModelFiles;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.checkPodDoesNotExist;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.checkPodExists;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.checkPodReadyAndServiceExists;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.createConfigMapAndVerify;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.createOcirRepoSecret;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.createPV;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.createPVC;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.createSecretForBaseImages;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.getIntrospectJobName;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.getPodCreationTime;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.installAndVerifyOperator;
import static oracle.weblogic.kubernetes.utils.ThreadSafeLogger.getLogger;
import static org.awaitility.Awaitility.with;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * This test class verifies the following scenarios
 *
 * <p>testServerLogsAreOnPV
 * domain logHome is on PV, check server logs are on PV
 *
 * <p>testMiiCheckSystemResources
 *  Check the System Resources in a pre-configured ConfigMap
 *
 * <p>testAddWorkManager
 *  Add a new work manager to a running WebLogic domain
 *
 * <p>testUpdateWorkManager
 *  Update dynamic work manager configurations in a running WebLogic domain.
 *
 */

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DisplayName("Test dynamic updates to a model in image domain")
@IntegrationTest
class ItMiiDynamicUpdate {

  private static String opNamespace = null;
  private static String domainNamespace = null;
  private static ConditionFactory withStandardRetryPolicy = null;
  private static int replicaCount = 1;
  private static final String domainUid = "mii-dynamic-update";
  private static String pvName = domainUid + "-pv"; // name of the persistent volume
  private static String pvcName = domainUid + "-pvc"; // name of the persistent volume claim
  private static final String configMapName = "dynamicupdate-test-configmap";//"wmconfigmap";
  private final String adminServerPodName = domainUid + "-admin-server";
  private final String managedServerPrefix = domainUid + "-managed-server";
  private final String adminServerName = "admin-server";
  private final String workManagerName = "newWM";

  private static LoggingFacade logger = null;

  /**
   * Install Operator.
   * Create domain resource defintion.
   * @param namespaces list of namespaces created by the IntegrationTestWatcher by the
   JUnit engine parameter resolution mechanism
   */
  @BeforeAll
  public static void initAll(@Namespaces(2) List<String> namespaces) {
    logger = getLogger();
    // create standard, reusable retry/backoff policy
    withStandardRetryPolicy = with().pollDelay(2, SECONDS)
        .and().with().pollInterval(10, SECONDS)
        .atMost(5, MINUTES).await();

    // get a new unique opNamespace
    logger.info("Creating unique namespace for Operator");
    assertNotNull(namespaces.get(0), "Namespace list is null");
    opNamespace = namespaces.get(0);

    logger.info("Creating unique namespace for Domain");
    assertNotNull(namespaces.get(1), "Namespace list is null");
    domainNamespace = namespaces.get(1);

    // install and verify operator
    installAndVerifyOperator(opNamespace, domainNamespace);

    // Create the repo secret to pull the image
    // this secret is used only for non-kind cluster
    createOcirRepoSecret(domainNamespace);

    // create secret for admin credentials
    logger.info("Create secret for admin credentials");
    String adminSecretName = "weblogic-credentials";
    assertDoesNotThrow(() -> createDomainSecret(adminSecretName,"weblogic",
            "welcome1", domainNamespace),
            String.format("createSecret failed for %s", adminSecretName));

    // create encryption secret
    logger.info("Create encryption secret");
    String encryptionSecretName = "encryptionsecret";
    assertDoesNotThrow(() -> createDomainSecret(encryptionSecretName, "weblogicenc",
            "weblogicenc", domainNamespace),
             String.format("createSecret failed for %s", encryptionSecretName));

    logger.info("Create database secret");
    final String dbSecretName = domainUid  + "-db-secret";
    assertDoesNotThrow(() -> createDatabaseSecret(dbSecretName, "scott",
            "tiger", "jdbc:oracle:thin:localhost:/ORCLCDB", domainNamespace),
             String.format("createSecret failed for %s", dbSecretName));

    // create WDT config map without any files
    createConfigMapAndVerify(configMapName, domainUid, domainNamespace, Collections.EMPTY_LIST);

    // create pull secrets for WebLogic image when running in non Kind Kubernetes cluster
    // this secret is used only for non-kind cluster
    createSecretForBaseImages(domainNamespace);

    // create PV, PVC for logs
    createPV(pvName, domainUid, ItMiiDynamicUpdate.class.getSimpleName());
    createPVC(pvName, pvcName, domainUid, domainNamespace);

    // create job to change permissions on PV hostPath
    createJobToChangePermissionsOnPvHostPath(pvName, pvcName, domainNamespace);

    // create the domain CR with a pre-defined configmap
    createDomainResourceWithLogHome(domainUid, domainNamespace,
        MII_BASIC_IMAGE_NAME + ":" + MII_BASIC_IMAGE_TAG,
        adminSecretName, OCIR_SECRET_NAME, encryptionSecretName,
        replicaCount, pvName, pvcName, "cluster-1", configMapName, dbSecretName, true);

    // wait for the domain to exist
    logger.info("Check for domain custom resource in namespace {0}", domainNamespace);
    withStandardRetryPolicy
        .conditionEvaluationListener(
            condition -> logger.info("Waiting for domain {0} to be created in namespace {1} "
                    + "(elapsed time {2}ms, remaining time {3}ms)",
                domainUid,
                domainNamespace,
                condition.getElapsedTimeInMS(),
                condition.getRemainingTimeInMS()))
        .until(domainExists(domainUid, DOMAIN_VERSION, domainNamespace));
  }

  /**
   * Verify all server pods are running.
   * Verify all k8s services for all servers are created.
   */
  @BeforeEach
  public void beforeEach() {

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
   * Create a configmap containing both the model yaml, and a sparse model file to add
   * a new work manager, a min threads constraint, and a max threads constraint
   * Patch the domain resource with the configmap.
   * Update the introspect version of the domain resource.
   * Verify rolling restart of the domain by comparing PodCreationTimestamp
   * before and after rolling restart.
   * Verify new work manager is configured.
   */
  @Test
  @Order(1)
  @DisplayName("Add a work manager to a model-in-image domain using dynamic update")
  public void testMiiAddWorkManager() {

    // This test uses the WebLogic domain created in BeforeAll method
    // BeforeEach method ensures that the server pods are running

    // get the creation time of the admin server pod before patching
    DateTime adminPodCreationTime = getPodCreationTime(domainNamespace, adminServerPodName);

    replaceConfigMapWithModelFiles(configMapName, domainUid, domainNamespace,
        Arrays.asList("model.config.wm.yaml"), withStandardRetryPolicy);

    assertTrue(assertDoesNotThrow(() ->
            patchDomainResourceWithNewIntrospectVersion(domainUid, domainNamespace),
        "Patch domain with new IntrospectVersion threw ApiException"),
        "Failed to patch domain with new IntrospectVersion");

    verifyIntrospectorRuns();

    withStandardRetryPolicy.conditionEvaluationListener(
        condition ->
            logger.info("Waiting for work manager configuration to be updated. Elapsed time{1}, remaining time {2}",
                condition.getElapsedTimeInMS(), condition.getRemainingTimeInMS())).until(
                    () -> checkWorkManagerRuntime(domainNamespace, adminServerPodName,
                        MANAGED_SERVER_NAME_BASE + "1",
                        workManagerName, "200"));
    logger.info("Found new work manager configuration");

    assertEquals(adminPodCreationTime, getPodCreationTime(domainNamespace, adminServerPodName),
        "servers should not roll");
  }

  /**
   * Recreate configmap containing both the model yaml, and a sparse model file with
   * updated min and max threads constraints that was added in {@link #testMiiAddWorkManager()}
   * test.
   * Patch the domain resource with the configmap.
   * Update the introspect version of the domain resource.
   * Wait for introspector to complete
   * Verify work manager configuration is updated.
   */
  @Test
  @Order(2)
  @DisplayName("Update work manager min/max threads constraints config to a model-in-image domain using dynamic update")
  public void testMiiUpdateWorkManager() {

    // This test uses the WebLogic domain created in BeforeAll method
    // BeforeEach method ensures that the server pods are running

    // get the creation time of the admin server pod before patching
    DateTime adminPodCreationTime = getPodCreationTime(domainNamespace, adminServerPodName);

    replaceConfigMapWithModelFiles(configMapName, domainUid, domainNamespace,
        Arrays.asList("model.update.wm.yaml"),
        withStandardRetryPolicy);

    assertTrue(assertDoesNotThrow(() ->
            patchDomainResourceWithNewIntrospectVersion(domainUid, domainNamespace),
        "Patch domain with new IntrospectVersion threw ApiException"),
        "Failed to patch domain with new IntrospectVersion");

    verifyIntrospectorRuns();

    withStandardRetryPolicy.conditionEvaluationListener(
        condition ->
            logger.info("Waiting for min threads constraint configuration to be updated. "
                    + "Elapsed time{1}, remaining time {2}",
                condition.getElapsedTimeInMS(), condition.getRemainingTimeInMS())).until(
                    () -> checkMinThreadsConstraintRuntime(2));

    withStandardRetryPolicy.conditionEvaluationListener(
        condition ->
            logger.info("Waiting for max threads constraint configuration to be updated. "
                    + "Elapsed time{1}, remaining time {2}",
                condition.getElapsedTimeInMS(), condition.getRemainingTimeInMS())).until(
                    () -> checkMaxThreadsConstraintRuntime(20));

    logger.info("Found updated work manager configuration");

    assertEquals(adminPodCreationTime, getPodCreationTime(domainNamespace, adminServerPodName),
        "servers should not roll");
  }

  private void verifyIntrospectorRuns() {
    //verify the introspector pod is created and runs
    logger.info("Verifying introspector pod is created, runs and deleted");
    String introspectPodName = getIntrospectJobName(domainUid);
    checkPodExists(introspectPodName, domainUid, domainNamespace);
    checkPodDoesNotExist(introspectPodName, domainUid, domainNamespace);
  }

  /*
   * Verify the min threads constraint runtime configuration through rest API.
   * @param count expected value for min threads constraint count
   * @returns true if the min threads constraint runtime is read successfully and is configured
   *          with the provided count value.
   **/
  private boolean checkMinThreadsConstraintRuntime(int count) {
    ExecResult result = readMinThreadsConstraintRuntimeForWorkManager(domainNamespace,
        adminServerPodName, MANAGED_SERVER_NAME_BASE + "1", workManagerName);
    if (result != null) {
      logger.info("readMinThreadsConstraintRuntime read " + result.toString());
      return (result.stdout() != null && result.stdout().contains("\"count\": " + count));
    }
    logger.info("readMinThreadsConstraintRuntime failed to read from WebLogic server ");
    return false;
  }

  /*
   * Verify the max threads constraint runtime configuration through rest API.
   * @param count expected value for max threads constraint count
   * @returns true if the max threads constraint runtime is read successfully and is configured
   *          with the provided count value.
   **/
  private boolean checkMaxThreadsConstraintRuntime(int count) {
    ExecResult result = readMaxThreadsConstraintRuntimeForWorkManager(domainNamespace,
        adminServerPodName, MANAGED_SERVER_NAME_BASE + "1", workManagerName);
    if (result != null) {
      logger.info("readMaxThreadsConstraintRuntime read " + result.toString());
      return (result.stdout() != null && result.stdout().contains("\"count\": " + count));
    }
    logger.info("readMaxThreadsConstraintRuntime failed to read from WebLogic server ");
    return false;
  }
}
