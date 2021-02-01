// Copyright (c) 2020, 2021, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.weblogic.kubernetes;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;

import io.kubernetes.client.openapi.ApiException;
import io.kubernetes.client.openapi.models.V1Pod;
import oracle.weblogic.domain.Domain;
import oracle.weblogic.domain.DomainCondition;
import oracle.weblogic.kubernetes.annotations.IntegrationTest;
import oracle.weblogic.kubernetes.annotations.Namespaces;
import oracle.weblogic.kubernetes.logging.LoggingFacade;
import oracle.weblogic.kubernetes.utils.ExecResult;
import org.awaitility.core.ConditionFactory;
import org.joda.time.DateTime;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import static java.util.concurrent.TimeUnit.MINUTES;
import static java.util.concurrent.TimeUnit.SECONDS;
import static oracle.weblogic.kubernetes.TestConstants.ADMIN_PASSWORD_DEFAULT;
import static oracle.weblogic.kubernetes.TestConstants.ADMIN_USERNAME_DEFAULT;
import static oracle.weblogic.kubernetes.TestConstants.DOMAIN_VERSION;
import static oracle.weblogic.kubernetes.TestConstants.MANAGED_SERVER_NAME_BASE;
import static oracle.weblogic.kubernetes.TestConstants.MII_APP_RESPONSE_V1;
import static oracle.weblogic.kubernetes.TestConstants.MII_BASIC_IMAGE_NAME;
import static oracle.weblogic.kubernetes.TestConstants.MII_BASIC_IMAGE_TAG;
import static oracle.weblogic.kubernetes.TestConstants.MII_DYNAMIC_UPDATE_EXPECTED_ERROR_MSG;
import static oracle.weblogic.kubernetes.TestConstants.MII_UPDATED_RESTART_REQUIRED_LABEL;
import static oracle.weblogic.kubernetes.TestConstants.OCIR_SECRET_NAME;
import static oracle.weblogic.kubernetes.actions.ActionConstants.MODEL_DIR;
import static oracle.weblogic.kubernetes.actions.ActionConstants.WORK_DIR;
import static oracle.weblogic.kubernetes.actions.TestActions.getDomainCustomResource;
import static oracle.weblogic.kubernetes.actions.TestActions.getPod;
import static oracle.weblogic.kubernetes.actions.TestActions.getPodLog;
import static oracle.weblogic.kubernetes.actions.TestActions.getPodStatusPhase;
import static oracle.weblogic.kubernetes.actions.TestActions.getServiceNodePort;
import static oracle.weblogic.kubernetes.actions.TestActions.patchDomainResourceWithNewIntrospectVersion;
import static oracle.weblogic.kubernetes.actions.TestActions.patchDomainResourceWithNewRestartVersion;
import static oracle.weblogic.kubernetes.actions.TestActions.patchDomainResourceWithOnNonDynamicChanges;
import static oracle.weblogic.kubernetes.assertions.TestAssertions.domainExists;
import static oracle.weblogic.kubernetes.assertions.TestAssertions.podIntrospectVersionUpdated;
import static oracle.weblogic.kubernetes.assertions.TestAssertions.verifyRollingRestartOccurred;
import static oracle.weblogic.kubernetes.utils.CommonMiiTestUtils.checkApplicationRuntime;
import static oracle.weblogic.kubernetes.utils.CommonMiiTestUtils.checkWorkManagerRuntime;
import static oracle.weblogic.kubernetes.utils.CommonMiiTestUtils.createDatabaseSecret;
import static oracle.weblogic.kubernetes.utils.CommonMiiTestUtils.createDomainResourceWithLogHome;
import static oracle.weblogic.kubernetes.utils.CommonMiiTestUtils.createDomainSecret;
import static oracle.weblogic.kubernetes.utils.CommonMiiTestUtils.createJobToChangePermissionsOnPvHostPath;
import static oracle.weblogic.kubernetes.utils.CommonMiiTestUtils.readMaxThreadsConstraintRuntimeForWorkManager;
import static oracle.weblogic.kubernetes.utils.CommonMiiTestUtils.readMinThreadsConstraintRuntimeForWorkManager;
import static oracle.weblogic.kubernetes.utils.CommonMiiTestUtils.replaceConfigMapWithModelFiles;
import static oracle.weblogic.kubernetes.utils.CommonPatchTestUtils.patchDomainResourceWithNewReplicaCountAtSpecLevel;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.checkAppIsRunning;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.checkPodDoesNotExist;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.checkPodExists;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.checkPodReadyAndServiceExists;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.checkServiceExists;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.checkSystemResourceConfig;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.checkSystemResourceConfiguration;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.checkSystemResourceRuntime;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.createConfigMapAndVerify;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.createOcirRepoSecret;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.createPV;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.createPVC;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.createSecretForBaseImages;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.getExternalServicePodName;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.getIntrospectJobName;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.getPodCreationTime;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.installAndVerifyOperator;
import static oracle.weblogic.kubernetes.utils.ThreadSafeLogger.getLogger;
import static org.awaitility.Awaitility.with;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * This test class verifies the following scenarios
 *
 * <p>testMiiAddWorkManager
 * Add a new work manager to a running WebLogic domain
 *
 * <p>testMiiUpdateWorkManager
 * Update dynamic work manager configurations in a running WebLogic domain.
 */

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DisplayName("Test dynamic updates to a model in image domain")
@IntegrationTest
class ItMiiDynamicUpdate {

  private static String opNamespace = null;
  private static String domainNamespace = null;
  private static ConditionFactory withStandardRetryPolicy = null;
  private static ConditionFactory withQuickRetryPolicy;
  private static int replicaCount = 1;
  private static final String domainUid = "mii-dynamic-update";
  private static String pvName = domainUid + "-pv"; // name of the persistent volume
  private static String pvcName = domainUid + "-pvc"; // name of the persistent volume claim
  private static final String configMapName = "dynamicupdate-test-configmap";//"wmconfigmap";
  private final String adminServerPodName = domainUid + "-admin-server";
  private final String managedServerPrefix = domainUid + "-managed-server";
  private final String adminServerName = "admin-server";
  private final String workManagerName = "newWM";
  private static Path pathToChangeTargetYaml = null;
  private static Path pathToAddClusterYaml = null;
  private static LoggingFacade logger = null;

  /**
   * Install Operator.
   * Create domain resource defintion.
   *
   * @param namespaces list of namespaces created by the IntegrationTestWatcher by the
   *                   JUnit engine parameter resolution mechanism
   */
  @BeforeAll
  public static void initAll(@Namespaces(2) List<String> namespaces) {
    logger = getLogger();
    // create standard, reusable retry/backoff policy
    withStandardRetryPolicy = with().pollDelay(2, SECONDS)
        .and().with().pollInterval(10, SECONDS)
        .atMost(5, MINUTES).await();

    // create a reusable quick retry policy
    withQuickRetryPolicy = with().pollDelay(0, SECONDS)
        .and().with().pollInterval(4, SECONDS)
        .atMost(10, SECONDS).await();

    // get a new unique opNamespace
    logger.info("Creating unique namespace for Operator");
    assertNotNull(namespaces.get(0), "Namespace list is null");
    opNamespace = namespaces.get(0);

    logger.info("Creating unique namespace for Domain");
    assertNotNull(namespaces.get(1), "Namespace list is null");
    domainNamespace = namespaces.get(1);

    // install and verify operator
    installAndVerifyOperator(opNamespace, 0, 0, domainNamespace);

    // Create the repo secret to pull the image
    // this secret is used only for non-kind cluster
    createOcirRepoSecret(domainNamespace);

    // create secret for admin credentials
    logger.info("Create secret for admin credentials");
    String adminSecretName = "weblogic-credentials";
    assertDoesNotThrow(() -> createDomainSecret(adminSecretName, ADMIN_USERNAME_DEFAULT,
        ADMIN_PASSWORD_DEFAULT, domainNamespace),
        String.format("createSecret failed for %s", adminSecretName));

    // create encryption secret
    logger.info("Create encryption secret");
    String encryptionSecretName = "encryptionsecret";
    assertDoesNotThrow(() -> createDomainSecret(encryptionSecretName, "weblogicenc",
        "weblogicenc", domainNamespace),
        String.format("createSecret failed for %s", encryptionSecretName));

    logger.info("Create database secret");
    final String dbSecretName = domainUid + "-db-secret";
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

    // write sparse yaml to change target to file
    pathToChangeTargetYaml = Paths.get(WORK_DIR + "/changetarget.yaml");
    String yamlToChangeTarget = "appDeployments:\n"
        + "  Application:\n"
        + "    myear:\n"
        + "      Target: 'cluster-1,admin-server'";

    assertDoesNotThrow(() -> Files.write(pathToChangeTargetYaml, yamlToChangeTarget.getBytes()));

    // write sparse yaml to file
    pathToAddClusterYaml = Paths.get(WORK_DIR + "/addcluster.yaml");
    String yamlToAddCluster = "topology:\n"
        + "    Cluster:\n"
        + "        \"cluster-2\":\n"
        + "            DynamicServers:\n"
        + "                ServerTemplate:  \"cluster-2-template\"\n"
        + "                ServerNamePrefix: \"dynamic-server\"\n"
        + "                DynamicClusterSize: 4\n"
        + "                MinDynamicClusterSize: 2\n"
        + "                MaxDynamicClusterSize: 4\n"
        + "                CalculatedListenPorts: false\n"
        + "    ServerTemplate:\n"
        + "        \"cluster-2-template\":\n"
        + "            Cluster: \"cluster-2\"\n"
        + "            ListenPort : 8001";

    assertDoesNotThrow(() -> Files.write(pathToAddClusterYaml, yamlToAddCluster.getBytes()));
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

    LinkedHashMap<String, DateTime> pods = new LinkedHashMap<>();

    // get the creation time of the admin server pod before patching
    DateTime adminPodCreationTime = getPodCreationTime(domainNamespace, adminServerPodName);
    pods.put(adminServerPodName, getPodCreationTime(domainNamespace, adminServerPodName));
    // get the creation time of the managed server pods before patching
    for (int i = 1; i <= replicaCount; i++) {
      pods.put(managedServerPrefix + i, getPodCreationTime(domainNamespace, managedServerPrefix + i));
    }
    for (int i = 1; i <= replicaCount; i++) {
      pods.put(managedServerPrefix + i, getPodCreationTime(domainNamespace, managedServerPrefix + i));
    }

    replaceConfigMapWithModelFiles(configMapName, domainUid, domainNamespace,
        Arrays.asList(MODEL_DIR + "/model.config.wm.yaml"), withStandardRetryPolicy);

    String introspectVersion = patchDomainResourceWithNewIntrospectVersion(domainUid, domainNamespace);

    verifyIntrospectorRuns();

    withStandardRetryPolicy.conditionEvaluationListener(
        condition ->
            logger.info("Waiting for work manager configuration to be updated. "
                    + "Elapsed time {0}ms, remaining time {1}ms",
                condition.getElapsedTimeInMS(), condition.getRemainingTimeInMS())).until(
                  () -> checkWorkManagerRuntime(domainNamespace, adminServerPodName,
            MANAGED_SERVER_NAME_BASE + "1",
            workManagerName, "200"));
    logger.info("Found new work manager configuration");

    verifyPodsNotRolled(pods);

    verifyPodIntrospectVersionUpdated(pods.keySet(), introspectVersion);
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

    LinkedHashMap<String, DateTime> pods = new LinkedHashMap<>();

    // get the creation time of the admin server pod before patching
    DateTime adminPodCreationTime = getPodCreationTime(domainNamespace, adminServerPodName);
    pods.put(adminServerPodName, getPodCreationTime(domainNamespace, adminServerPodName));
    // get the creation time of the managed server pods before patching
    for (int i = 1; i <= replicaCount; i++) {
      pods.put(managedServerPrefix + i, getPodCreationTime(domainNamespace, managedServerPrefix + i));
    }

    replaceConfigMapWithModelFiles(configMapName, domainUid, domainNamespace,
        Arrays.asList(MODEL_DIR + "/model.update.wm.yaml"),
        withStandardRetryPolicy);

    String introspectVersion = patchDomainResourceWithNewIntrospectVersion(domainUid, domainNamespace);

    verifyIntrospectorRuns();

    verifyMinThreadsConstraintRuntime(2);

    verifyMaxThredsConstraintRuntime(20);

    logger.info("Found updated work manager configuration");

    verifyPodsNotRolled(pods);

    verifyPodIntrospectVersionUpdated(pods.keySet(), introspectVersion);

  }

  /**
   * Recreate configmap containing previous test model and application config target to both admin and cluster.
   * Patch the domain resource with the configmap.
   * Update the introspect version of the domain resource.
   * Wait for introspector to complete
   * Verify application target is changed by accessing the application runtime using REST API.
   * Verify the application can be accessed on both admin server and from all servers in cluster.
   */
  @Test
  @Order(3)
  @DisplayName("Change target for the application deployment using mii dynamic update")
  public void testMiiChangeTarget() {

    // This test uses the WebLogic domain created in BeforeAll method
    // BeforeEach method ensures that the server pods are running

    LinkedHashMap<String, DateTime> pods = new LinkedHashMap<>();

    // get the creation time of the admin server pod before patching
    DateTime adminPodCreationTime = getPodCreationTime(domainNamespace, adminServerPodName);
    pods.put(adminServerPodName, getPodCreationTime(domainNamespace, adminServerPodName));
    // get the creation time of the managed server pods before patching
    for (int i = 1; i <= replicaCount; i++) {
      pods.put(managedServerPrefix + i, getPodCreationTime(domainNamespace, managedServerPrefix + i));
    }

    // make sure the application is not deployed on admin server
    assertFalse(checkApplicationRuntime(domainNamespace, adminServerPodName,
        adminServerName, "200"),
        "Application deployed on " + adminServerName + " before the dynamic update");

    // check and wait for the application to be accessible in all server pods
    verifyApplicationAccessOnCluster();

    // Replace contents of an existing configMap
    replaceConfigMapWithModelFiles(configMapName, domainUid, domainNamespace,
        Arrays.asList(MODEL_DIR + "/model.config.wm.yaml", pathToChangeTargetYaml.toString()), withStandardRetryPolicy);

    // Patch a running domain with introspectVersion.
    String introspectVersion = patchDomainResourceWithNewIntrospectVersion(domainUid, domainNamespace);

    // Verifying introspector pod is created, runs and deleted
    verifyIntrospectorRuns();

    // check and wait for the application to be accessible in all server pods
    verifyApplicationAccessOnCluster();

    // check and wait for the application to be accessible in admin pod
    checkAppIsRunning(
        withQuickRetryPolicy,
        domainNamespace,
        adminServerPodName,
        "7001",
        "sample-war/index.jsp",
        "Hello World");

    verifyPodsNotRolled(pods);

    verifyPodIntrospectVersionUpdated(pods.keySet(), introspectVersion);
  }

  /**
   * Recreate configmap containing new cluster config.
   * Patch the domain resource with the configmap.
   * Update the introspect version of the domain resource.
   * Wait for introspector to complete
   * Verify servers in the newly added cluster are started and other servers are not rolled.
   */
  @Test
  @Order(4)
  @DisplayName("Add cluster in MII domain using mii dynamic update")
  public void testMiiAddCluster() {
    // This test uses the WebLogic domain created in BeforeAll method
    // BeforeEach method ensures that the server pods are running

    LinkedHashMap<String, DateTime> pods = new LinkedHashMap<>();

    // get the creation time of the admin server pod before patching
    DateTime adminPodCreationTime = getPodCreationTime(domainNamespace, adminServerPodName);
    pods.put(adminServerPodName, getPodCreationTime(domainNamespace, adminServerPodName));
    // get the creation time of the managed server pods before patching
    for (int i = 1; i <= replicaCount; i++) {
      pods.put(managedServerPrefix + i, getPodCreationTime(domainNamespace, managedServerPrefix + i));
    }

    // Replace contents of an existing configMap with cm config and application target as
    // there are issues with removing them, https://jira.oraclecorp.com/jira/browse/WDT-535
    replaceConfigMapWithModelFiles(configMapName, domainUid, domainNamespace,
        Arrays.asList(MODEL_DIR + "/model.config.wm.yaml",
            pathToAddClusterYaml.toString()), withStandardRetryPolicy);

    // change replica to have the servers running in the newly added cluster
    assertTrue(patchDomainResourceWithNewReplicaCountAtSpecLevel(domainUid, domainNamespace, replicaCount),
        "failed to patch the replicas at spec level");

    // Patch a running domain with introspectVersion.
    String introspectVersion = patchDomainResourceWithNewIntrospectVersion(domainUid, domainNamespace);

    // Verifying introspector pod is created, runs and deleted
    verifyIntrospectorRuns();

    // check the servers are started in newly added cluster and the server services and pods are ready
    for (int i = 1; i <= replicaCount; i++) {
      logger.info("Wait for managed server services and pods are created in namespace {0}",
          domainNamespace);
      checkPodReadyAndServiceExists(domainUid + "-dynamic-server" + i, domainUid, domainNamespace);
    }

    verifyPodsNotRolled(pods);

    verifyPodIntrospectVersionUpdated(pods.keySet(), introspectVersion);

  }

  /**
   * Recreate configmap containing datasource config.
   * Patch the domain resource with the configmap.
   * Update the introspect version of the domain resource.
   * Wait for introspector to complete
   * Verify the datasource is added by checking the MBean using REST api.
   */
  @Test
  @Order(5)
  @DisplayName("Add datasource in MII domain using mii dynamic update")
  public void testMiiAddDataSource() {
    // This test uses the WebLogic domain created in BeforeAll method
    // BeforeEach method ensures that the server pods are running
    addDataSourceAndVerify(true);
  }

  /**
   * Non-dynamic change using dynamic update by changing datasource parameters.
   * Set onNonDynamicChanges to CommitUpdateAndRoll.
   * Verify domain will rolling restart.
   * Verify introspectVersion is updated.
   * Verify the datasource parameter is updated by checking the MBean using REST api.
   * Verify domain status should have a condition type as "Available" and condition reason as "ServersReady".
   */
  @Test
  @Order(6)
  @DisplayName("Changing datasource parameters with CommitUpdateAndRoll using mii dynamic update")
  public void testMiiChangeDataSourceParameterWithCommitUpdateAndRoll() {

    // This test uses the WebLogic domain created in BeforeAll method
    // BeforeEach method ensures that the server pods are running
    LinkedHashMap<String, DateTime> pods = addDataSourceAndVerify(false);

    // Replace contents of an existing configMap with cm config and application target as
    // there are issues with removing them, https://jira.oraclecorp.com/jira/browse/WDT-535
    replaceConfigMapWithModelFiles(configMapName, domainUid, domainNamespace,
        Arrays.asList(MODEL_DIR + "/model.config.wm.yaml", pathToAddClusterYaml.toString(),
            MODEL_DIR + "/model.update.jdbc2.yaml"), withStandardRetryPolicy);

    // Patch a running domain with onNonDynamicChanges
    patchDomainResourceWithOnNonDynamicChanges(domainUid, domainNamespace, "CommitUpdateAndRoll");

    // Patch a running domain with introspectVersion.
    String introspectVersion = patchDomainResourceWithNewIntrospectVersion(domainUid, domainNamespace);

    // Verifying introspector pod is created, runs and deleted
    verifyIntrospectorRuns();

    // Verifying the domain is rolling restarted
    assertTrue(verifyRollingRestartOccurred(pods, 1, domainNamespace),
        "Rolling restart failed");

    verifyPodIntrospectVersionUpdated(pods.keySet(), introspectVersion);

    // check datasource configuration using REST api
    int adminServiceNodePort
        = getServiceNodePort(domainNamespace, getExternalServicePodName(adminServerPodName), "default");
    assertNotEquals(-1, adminServiceNodePort, "admin server default node port is not valid");
    assertTrue(checkSystemResourceConfig(adminServiceNodePort,
        "JDBCSystemResources/TestDataSource2/JDBCResource/JDBCDataSourceParams",
        "jdbc\\/TestDataSource2-2"), "JDBCSystemResource JNDIName not found");
    logger.info("JDBCSystemResource configuration found");

    // check that the domain status condition contains the correct type and expected reason
    logger.info("verifying the domain status condition contains the correct type and expected reason");
    verifyDomainStatusConditionNoErrorMsg("Available", "ServersReady");

    // change the datasource jndi name back to original in order to create a clean environment for the next test
    replaceConfigMapWithModelFiles(configMapName, domainUid, domainNamespace,
        Arrays.asList(MODEL_DIR + "/model.config.wm.yaml", pathToAddClusterYaml.toString(),
            MODEL_DIR + "/model.jdbc2.yaml"), withStandardRetryPolicy);

    // Patch a running domain with onNonDynamicChanges
    patchDomainResourceWithOnNonDynamicChanges(domainUid, domainNamespace, "CommitUpdateAndRoll");

    // Patch a running domain with introspectVersion.
    introspectVersion = patchDomainResourceWithNewIntrospectVersion(domainUid, domainNamespace);

    // Verifying introspector pod is created, runs and deleted
    verifyIntrospectorRuns();

    // Verifying the domain is rolling restarted
    assertTrue(verifyRollingRestartOccurred(pods, 1, domainNamespace),
        "Rolling restart failed");

    verifyPodIntrospectVersionUpdated(pods.keySet(), introspectVersion);
  }

  /**
   * Mixed update by changing the DataSource URL (non-dynamic) and undeploying an application (dynamic).
   * Patched the domain resource and set onNonDynamicChanges to CommitUpdateAndRoll.
   * Verify domain will rolling restart.
   * Verify introspectVersion is updated.
   * Verify the datasource URL is updated by checking the MBean using REST api.
   * Verify the application is undeployed.
   * Verify domain status should have a condition type as "Available" and condition reason as "ServersReady".
   */
  @Test
  @Order(7)
  @DisplayName("Changing Weblogic datasource URL and deleting application with CommitUpdateAndRoll "
      + "using mii dynamic update")
  public void testMiiDeleteAppChangeDBUrlWithCommitUpdateAndRoll() {

    // This test uses the WebLogic domain created in BeforeAll method
    // BeforeEach method ensures that the server pods are running
    LinkedHashMap<String, DateTime> pods = addDataSourceAndVerify(false);

    // check the application myear is deployed using REST API
    int adminServiceNodePort
        = getServiceNodePort(domainNamespace, getExternalServicePodName(adminServerPodName), "default");
    assertNotEquals(-1, adminServiceNodePort, "admin server default node port is not valid");
    assertTrue(checkSystemResourceConfig(adminServiceNodePort,
        "appDeployments",
        "myear"), "Application myear is not found");
    logger.info("Application myear is found");

    // write sparse yaml to undeploy application to file
    Path pathToUndeployAppYaml = Paths.get(WORK_DIR + "/undeployapp.yaml");
    String yamlToUndeployApp = "appDeployments:\n"
        + "  Application:\n"
        + "    !myear:";

    assertDoesNotThrow(() -> Files.write(pathToUndeployAppYaml, yamlToUndeployApp.getBytes()));

    // Replace contents of an existing configMap with cm config and application target as
    // there are issues with removing them, https://jira.oraclecorp.com/jira/browse/WDT-535
    replaceConfigMapWithModelFiles(configMapName, domainUid, domainNamespace,
        Arrays.asList(MODEL_DIR + "/model.config.wm.yaml", pathToAddClusterYaml.toString(),
            MODEL_DIR + "/model.jdbc2.update2.yaml", pathToUndeployAppYaml.toString()), withStandardRetryPolicy);

    // Patch a running domain with onNonDynamicChanges
    patchDomainResourceWithOnNonDynamicChanges(domainUid, domainNamespace, "CommitUpdateAndRoll");

    // Patch a running domain with introspectVersion.
    String introspectVersion = patchDomainResourceWithNewIntrospectVersion(domainUid, domainNamespace);

    // Verifying introspector pod is created, runs and deleted
    verifyIntrospectorRuns();

    // Verifying the domain is rolling restarted
    assertTrue(verifyRollingRestartOccurred(pods, 1, domainNamespace),
        "Rolling restart failed");

    verifyPodIntrospectVersionUpdated(pods.keySet(), introspectVersion);

    // check datasource configuration using REST api
    adminServiceNodePort
        = getServiceNodePort(domainNamespace, getExternalServicePodName(adminServerPodName), "default");
    assertNotEquals(-1, adminServiceNodePort, "admin server default node port is not valid");
    assertTrue(checkSystemResourceConfig(adminServiceNodePort,
        "JDBCSystemResources/TestDataSource2/JDBCResource/JDBCDriverParams",
        "newdburl"), "JDBCSystemResource DB URL not found");
    logger.info("JDBCSystemResource DB URL found");

    // verify the application is undeployed
    assertFalse(checkSystemResourceConfig(adminServiceNodePort,
        "appDeployments",
        "myear"), "Application myear found, should be undeployed");
    logger.info("Application myear is undeployed");

    // check that the domain status condition contains the correct type and expected reason
    logger.info("verifying the domain status condition contains the correct type and expected reason");
    verifyDomainStatusConditionNoErrorMsg("Available", "ServersReady");
  }

  /**
   * Recreate configmap by deleting datasource.
   * Patch the domain resource with the configmap.
   * Update the introspect version of the domain resource.
   * Wait for introspector to complete.
   * Verify the domain is not restarted.
   * Verify the introspector version is updated.
   * Verify the datasource is deleted.
   * Verify the domain status condition contains the correct type and expected reason.
   */
  @Test
  @Order(8)
  @DisplayName("Deleting Datasource")
  public void testMiiDeleteDatasource() {

    // This test uses the WebLogic domain created in BeforeAll method
    // BeforeEach method ensures that the server pods are running
    LinkedHashMap<String, DateTime> pods = addDataSourceAndVerify(false);

    // write sparse yaml to delete datasource to file
    Path pathToDeleteDSYaml = Paths.get(WORK_DIR + "/deleteds.yaml");
    String yamlToDeleteDS = "resources:\n"
        + "  JDBCSystemResource:\n"
        + "    !TestDataSource2:";

    assertDoesNotThrow(() -> Files.write(pathToDeleteDSYaml, yamlToDeleteDS.getBytes()));

    // Replace contents of an existing configMap with cm config
    replaceConfigMapWithModelFiles(configMapName, domainUid, domainNamespace,
        Arrays.asList(MODEL_DIR + "/model.config.wm.yaml", pathToAddClusterYaml.toString(),
            pathToDeleteDSYaml.toString()), withStandardRetryPolicy);

    // Patch a running domain with introspectVersion.
    String introspectVersion = patchDomainResourceWithNewIntrospectVersion(domainUid, domainNamespace);

    // Verifying introspector pod is created, runs and deleted
    verifyIntrospectorRuns();

    // Verifying the domain is not restarted
    verifyPodsNotRolled(pods);

    verifyPodIntrospectVersionUpdated(pods.keySet(), introspectVersion);

    // check datasource configuration is deleted using REST api
    int adminServiceNodePort
        = getServiceNodePort(domainNamespace, getExternalServicePodName(adminServerPodName), "default");
    assertNotEquals(-1, adminServiceNodePort, "admin server default node port is not valid");
    assertFalse(checkSystemResourceConfig(adminServiceNodePort, "JDBCSystemResources",
        "TestDataSource2"), "Found JDBCSystemResource datasource, should be deleted");
    logger.info("JDBCSystemResource Datasource is deleted");

    // check that the domain status condition contains the correct type and expected reason
    logger.info("verifying the domain status condition contains the correct type and expected reason");
    verifyDomainStatusConditionNoErrorMsg("Available", "ServersReady");
  }

  /**
   * Negative test: Changing the domain name using mii dynamic update.
   * Check the introspector will fail with error message showed in the introspector pod log.
   * Check the status phase of the introspector pod is failed
   * Check the domain status message contains the expected error msg
   * Check the domain status condition type is "Failed" and message contains the expected error msg
   */
  @Test
  @Order(9)
  @DisplayName("Negative test changing domain name using mii dynamic update")
  public void testMiiChangeDomainName() {
    // write sparse yaml to file
    Path pathToChangeDomainNameYaml = Paths.get(WORK_DIR + "/changedomainname.yaml");
    String yamlToChangeDomainName = "topology:\n"
        + "  Name: newdomainname\n"
        + "  AdminServerName: 'admin-server'";

    assertDoesNotThrow(() -> Files.write(pathToChangeDomainNameYaml, yamlToChangeDomainName.getBytes()));

    // Replace contents of an existing configMap
    replaceConfigMapWithModelFiles(configMapName, domainUid, domainNamespace,
        Arrays.asList(pathToChangeDomainNameYaml.toString()), withStandardRetryPolicy);

    // Patch a running domain with introspectVersion.
    patchDomainResourceWithNewIntrospectVersion(domainUid, domainNamespace);

    // Verifying introspector pod is created and failed
    logger.info("verifying the introspector failed with the expected error msg");
    verifyIntrospectorFailsWithExpectedErrorMsg(MII_DYNAMIC_UPDATE_EXPECTED_ERROR_MSG);

    // clean failed introspector pods
    // replace WDT config map with config that's added in previous tests,
    // otherwise it will try to delete them, we are not testing deletion here
    replaceConfigMapWithModelFiles(configMapName, domainUid, domainNamespace,
        Arrays.asList(MODEL_DIR + "/model.config.wm.yaml",
            pathToAddClusterYaml.toString(), MODEL_DIR + "/model.jdbc2.yaml"),
        withStandardRetryPolicy);

    // Patch a running domain with introspectVersion.
    patchDomainResourceWithNewIntrospectVersion(domainUid, domainNamespace);

    // Verifying introspector pod is created, runs and deleted
    verifyIntrospectorRuns();
  }

  /**
   * Negative test: Changing the listen port of a server using mii dynamic update.
   * Check the introspector will fail with error message showed in the introspector pod log.
   * Check the status phase of the introspector pod is failed
   * Check the domain status message contains the expected error msg
   * Check the domain status condition type is "Failed" and message contains the expected error msg
   */
  @Test
  @Order(10)
  @DisplayName("Negative test changing listen port of a server using mii dynamic update")
  public void testMiiChangeListenPort() {

    // This test uses the WebLogic domain created in BeforeAll method
    // BeforeEach method ensures that the server pods are running

    // write sparse yaml to file
    Path pathToChangeListenPortYaml = Paths.get(WORK_DIR + "/changelistenport.yaml");
    String yamlToChangeListenPort = "topology:\n"
        + "  Server:\n"
        + "    'admin-server':\n"
        + "      ListenPort: 7003";
    assertDoesNotThrow(() -> Files.write(pathToChangeListenPortYaml, yamlToChangeListenPort.getBytes()));

    // Replace contents of an existing configMap
    replaceConfigMapWithModelFiles(configMapName, domainUid, domainNamespace,
        Arrays.asList(pathToChangeListenPortYaml.toString()), withStandardRetryPolicy);

    // Patch a running domain with introspectVersion.
    patchDomainResourceWithNewIntrospectVersion(domainUid, domainNamespace);

    // Verifying introspector pod is created and failed
    logger.info("verifying the introspector failed and the pod log contains the expected error msg");
    verifyIntrospectorFailsWithExpectedErrorMsg(MII_DYNAMIC_UPDATE_EXPECTED_ERROR_MSG);

    // clean failed introspector
    // replace WDT config map with config that's added in previous tests,
    // otherwise it will try to delete them, we are not testing deletion here
    replaceConfigMapWithModelFiles(configMapName, domainUid, domainNamespace,
        Arrays.asList(MODEL_DIR + "/model.config.wm.yaml", pathToAddClusterYaml.toString(),
            MODEL_DIR + "/model.jdbc2.yaml"), withStandardRetryPolicy);

    // Patch a running domain with introspectVersion.
    patchDomainResourceWithNewIntrospectVersion(domainUid, domainNamespace);

    // Verifying introspector pod is created, runs and deleted
    verifyIntrospectorRuns();
  }

  /**
   * Negative test: Changing the listen address of a server using mii dynamic update.
   * Check the introspector will fail with error message showed in the introspector pod log
   * Check the status phase of the introspector pod is failed
   * Check the domain status message contains the expected error msg
   * Check the domain status condition type is "Failed" and message contains the expected error msg
   */
  @Test
  @Order(11)
  @DisplayName("Negative test changing listen address of a server using mii dynamic update")
  public void testMiiChangeListenAddress() {
    // write sparse yaml to file
    Path pathToChangeListenAddressYaml = Paths.get(WORK_DIR + "/changelistenAddress.yaml");
    String yamlToChangeListenAddress = "topology:\n"
        + "  ServerTemplate:\n"
        + "    'cluster-1-template':\n"
        + "       ListenAddress: myAddress";
    assertDoesNotThrow(() -> Files.write(pathToChangeListenAddressYaml, yamlToChangeListenAddress.getBytes()));

    // Replace contents of an existing configMap
    replaceConfigMapWithModelFiles(configMapName, domainUid, domainNamespace,
        Arrays.asList(pathToChangeListenAddressYaml.toString()), withStandardRetryPolicy);

    // Patch a running domain with introspectVersion.
    patchDomainResourceWithNewIntrospectVersion(domainUid, domainNamespace);

    // Verifying introspector pod is created and failed
    logger.info("verifying the introspector failed and the pod log contains the expected error msg");
    verifyIntrospectorFailsWithExpectedErrorMsg(MII_DYNAMIC_UPDATE_EXPECTED_ERROR_MSG);

    // clean failed introspector
    // replace with config that's added in previous tests,
    // otherwise it will try to delete them, we are not testing deletion here
    replaceConfigMapWithModelFiles(configMapName, domainUid, domainNamespace,
        Arrays.asList(MODEL_DIR + "/model.config.wm.yaml", pathToAddClusterYaml.toString(),
            MODEL_DIR + "/model.jdbc2.yaml"), withStandardRetryPolicy);

    // Patch a running domain with introspectVersion.
    patchDomainResourceWithNewIntrospectVersion(domainUid, domainNamespace);

    // Verifying introspector pod is created, runs and deleted
    verifyIntrospectorRuns();
  }

  /**
   * Negative test: Changing SSL setting of a server using mii dynamic update.
   * Check the introspector will fail with error message showed in the introspector pod log.
   * Check the status phase of the introspector pod is failed
   * Check the domain status message contains the expected error msg
   * Check the domain status condition type is "Failed" and message contains the expected error msg
   */
  @Test
  @Order(12)
  @DisplayName("Negative test changing SSL setting of a server using mii dynamic update")
  public void testMiiChangeSSL() {

    // This test uses the WebLogic domain created in BeforeAll method
    // BeforeEach method ensures that the server pods are running

    // write sparse yaml to file
    Path pathToChangeSSLYaml = Paths.get(WORK_DIR + "/changessl.yaml");
    String yamlToChangeSSL = "topology:\n"
        + "  ServerTemplate:\n"
        + "    'cluster-1-template':\n"
        + "      SSL:\n"
        + "         ListenPort: 8103";
    assertDoesNotThrow(() -> Files.write(pathToChangeSSLYaml, yamlToChangeSSL.getBytes()));

    // Replace contents of an existing configMap
    replaceConfigMapWithModelFiles(configMapName, domainUid, domainNamespace,
        Arrays.asList(pathToChangeSSLYaml.toString()), withStandardRetryPolicy);

    // Patch a running domain with introspectVersion.
    patchDomainResourceWithNewIntrospectVersion(domainUid, domainNamespace);

    // Verifying introspector pod is created and failed
    logger.info("verifying the introspector failed and the pod log contains the expected error msg");
    verifyIntrospectorFailsWithExpectedErrorMsg(MII_DYNAMIC_UPDATE_EXPECTED_ERROR_MSG);

    // clean failed introspector
    // replace WDT config map with config that's added in previous tests,
    // otherwise it will try to delete them, we are not testing deletion here
    replaceConfigMapWithModelFiles(configMapName, domainUid, domainNamespace,
        Arrays.asList(MODEL_DIR + "/model.config.wm.yaml", pathToAddClusterYaml.toString(),
            MODEL_DIR + "/model.jdbc2.yaml"), withStandardRetryPolicy);

    // Patch a running domain with introspectVersion.
    patchDomainResourceWithNewIntrospectVersion(domainUid, domainNamespace);

    // Verifying introspector pod is created, runs and deleted
    verifyIntrospectorRuns();
  }

  /**
   * Recreate configmap containing non-dynamic change, changing DS attribute.
   * Patch the domain resource with the configmap.
   * Patch the domain with onNonDynamicChanges value as CancelUpdate.
   * Update the introspect version of the domain resource.
   * Wait for introspector to complete
   * Verify the domain status is updated and domain is not restarted.
   */
  // with latest dynamicupdate branch, the CancelUpdate behavior got changed. Disable this test now.
  @Disabled("CancelUpdate is removed from dynamic update")
  @Test
  @Order(13)
  @DisplayName("Test onNonDynamicChanges value CancelUpdate")
  public void testOnNonDynamicChangesCancelUpdate() {

    // This test uses the WebLogic domain created in BeforeAll method
    // BeforeEach method ensures that the server pods are running
    final LinkedHashMap<String, DateTime> pods = addDataSourceAndVerify(false);

    // make non-dynamic change, update datasource JDBCDriver params
    replaceConfigMapWithModelFiles(configMapName, domainUid, domainNamespace,
        Arrays.asList(MODEL_DIR + "/model.config.wm.yaml", pathToAddClusterYaml.toString(),
            MODEL_DIR + "/model.jdbc2.updatejdbcdriverparams.yaml"),
              withStandardRetryPolicy);

    // Patch a running domain with onNonDynamicChanges=CancelUpdate.
    patchDomainResourceWithOnNonDynamicChanges(domainUid, domainNamespace, "CancelUpdate");

    // Patch a running domain with introspectVersion.
    String introspectVersion = patchDomainResourceWithNewIntrospectVersion(domainUid, domainNamespace);

    // Verifying introspector pod is created, runs and deleted
    verifyIntrospectorRuns();

    // Verify domain is not restarted when non-dynamic change is made and CancelUpdate is used
    verifyPodsNotRolled(pods);

    verifyPodIntrospectVersionUpdated(pods.keySet(), introspectVersion);

    // check that the domain status condition type is "OnlineUpdateCanceled" and message contains the expected msg
    String expectedMsgForCancelUpdate = "Online update completed successfully, but the changes require restart and "
          + "the domain resource specified 'spec.configuration.model.onlineUpdate.onNonDynamicChanges=CancelUpdate' "
          + "option to cancel all changes if restart require.";
    logger.info("Verifying the domain status condition message contains the expected msg");
    verifyDomainStatusCondition("OnlineUpdateCanceled", expectedMsgForCancelUpdate);

  }

  /**
   * Two non-dynamic changes with default CommitUpdateOnly for onNonDynamicChanges.
   * Create a configmap containing two non-dynamic changes, modified DataSource attribute
   * and Adminstration Sever ScatteredReadsEnabled attribute.
   * Patch the domain resource with the configmap, using default value CommitUpdateOnly for onNonDynamicChanges.
   * Update the introspect version of the domain resource.
   * Wait for introspector to complete.
   * Verify the domain status is updated, domain is not restarted and the change is commited.
   * Restart the domain and verify both the changes are effective using REST Api.
   */
  @Test
  @Order(14)
  @DisplayName("Test non-dynamic changes with onNonDynamicChanges default value CommitUpdateOnly")
  public void testOnNonDynamicChangesCommitUpdateOnly() {

    String expectedMsgForCommitUpdateOnly = "Online update completed successfully, but the changes require restart and"
        + " the domain resource specified 'spec.configuration.model.onlineUpdate.onNonDynamicChanges=CommitUpdateOnly'"
        + " or not set. The changes are committed but the domain require manually restart to "
        + " make the changes effective.";

    // This test uses the WebLogic domain created in BeforeAll method
    // BeforeEach method ensures that the server pods are running
    LinkedHashMap<String, DateTime> pods = addDataSourceAndVerify(false);

    // write sparse yaml to change ScatteredReadsEnabled for adminserver
    Path pathToChangReadsYaml = Paths.get(WORK_DIR + "/changereads.yaml");
    String yamlToChangeReads = "topology:\n"
        + "    Server:\n"
        + "        \"admin-server\":\n"
        + "            ScatteredReadsEnabled: true";
    assertDoesNotThrow(() -> Files.write(pathToChangReadsYaml, yamlToChangeReads.getBytes()));

    // make two non-dynamic changes, add  datasource JDBC driver params and change scatteredreadenabled
    replaceConfigMapWithModelFiles(configMapName, domainUid, domainNamespace,
        Arrays.asList(MODEL_DIR + "/model.config.wm.yaml", pathToAddClusterYaml.toString(),
            MODEL_DIR + "/model.jdbc2.updatejdbcdriverparams.yaml", pathToChangReadsYaml.toString()),
              withStandardRetryPolicy);

    // Patch a running domain with onNonDynamicChanges - update with CommitUpdateOnly so that even if previous test
    // updates onNonDynamicChanges, this test will work
    patchDomainResourceWithOnNonDynamicChanges(domainUid, domainNamespace, "CommitUpdateOnly");

    // Patch a running domain with introspectVersion, uses default value for onNonDynamicChanges
    String introspectVersion = patchDomainResourceWithNewIntrospectVersion(domainUid, domainNamespace);

    // Verifying introspector pod is created, runs and deleted
    verifyIntrospectorRuns();

    // Verify domain is not restarted when non-dynamic change is made using default CommitUpdateOnly
    verifyPodsNotRolled(pods);

    verifyPodIntrospectVersionUpdated(pods.keySet(), introspectVersion);

    // check pod label for MII_UPDATED_RESTART_REQUIRED_LABEL
    assertDoesNotThrow(() -> verifyPodLabelUpdated(pods.keySet(), MII_UPDATED_RESTART_REQUIRED_LABEL + "=true"),
        "Couldn't check pod label");
    logger.info("Verified pod label");

    // check the change is committed
    int adminServiceNodePort
        = getServiceNodePort(domainNamespace, getExternalServicePodName(adminServerPodName), "default");
    assertNotEquals(-1, adminServiceNodePort, "admin server default node port is not valid");

    // check server config for ScatteredReadsEnabled is updated
    assertTrue(checkSystemResourceConfig(adminServiceNodePort,
        "servers/" + adminServerName,
        "\"scatteredReadsEnabled\": true"), "ScatteredReadsEnabled is not changed to true");
    logger.info("ScatteredReadsEnabled is changed to true");

    // check datasource configuration using REST api
    assertTrue(checkSystemResourceConfig(adminServiceNodePort,
        "JDBCSystemResources/TestDataSource2/JDBCResource/JDBCDriverParams/properties/properties",
        "\"name\": \"testattrib\""), "JDBCSystemResource new property not found");
    logger.info("JDBCSystemResource new property found");

    // check that the domain status condition type is "ConfigChangesPendingRestart"
    // and message contains the expected msg
    logger.info("Verifying the domain status condition message contains the expected msg");
    verifyDomainStatusCondition("ConfigChangesPendingRestart", expectedMsgForCommitUpdateOnly);

    // restart domain and verify the changes are effective
    String newRestartVersion = patchDomainResourceWithNewRestartVersion(domainUid, domainNamespace);
    logger.log(Level.INFO, "New restart version is {0}", newRestartVersion);
    assertTrue(verifyRollingRestartOccurred(pods, 1, domainNamespace),
        "Rolling restart failed");

    // Even if pods are created, need the service to created
    for (int i = 1; i <= replicaCount; i++) {
      logger.info("Check managed server service {0} created in namespace {1}",
          managedServerPrefix + i, domainNamespace);
      checkServiceExists(managedServerPrefix + i, domainNamespace);
    }

    // check datasource runtime after restart
    assertTrue(checkSystemResourceRuntime(adminServiceNodePort,
        "serverRuntimes/" + MANAGED_SERVER_NAME_BASE + "1/JDBCServiceRuntime/"
        + "JDBCDataSourceRuntimeMBeans/TestDataSource2",
        "\"testattrib\": \"dummy\""), "JDBCSystemResource new property not found");
    logger.info("JDBCSystemResource new property found");

    // check pod label MII_UPDATED_RESTART_REQUIRED_LABEL should have been removed
    assertDoesNotThrow(() -> verifyPodLabelRemoved(pods.keySet(), MII_UPDATED_RESTART_REQUIRED_LABEL + "=true"),
        "Couldn't check pod label");
    logger.info("Verified pod label");

  }

  /**
   * Recreate configmap containing application config target to none.
   * Patch the domain resource with the configmap.
   * Update the introspect version of the domain resource.
   * Wait for introspector to complete
   * Verify application target is changed by accessing the application runtime using REST API.
   */
  @Test
  @Order(15)
  @DisplayName("Remove all targets for the application deployment in MII domain using mii dynamic update")
  public void testMiiRemoveTarget() {

    // This test uses the WebLogic domain created in BeforeAll method
    // BeforeEach method ensures that the server pods are running

    LinkedHashMap<String, DateTime> pods = new LinkedHashMap<>();

    // get the creation time of the admin server pod before patching
    DateTime adminPodCreationTime = getPodCreationTime(domainNamespace, adminServerPodName);
    pods.put(adminServerPodName, getPodCreationTime(domainNamespace, adminServerPodName));
    // get the creation time of the managed server pods before patching
    for (int i = 1; i <= replicaCount; i++) {
      pods.put(managedServerPrefix + i, getPodCreationTime(domainNamespace, managedServerPrefix + i));
    }

    // check and wait for the application to be accessible in all server pods
    verifyApplicationAccessOnCluster();

    // write sparse yaml to file
    Path pathToRemoveTargetYaml = Paths.get(WORK_DIR + "/removetarget.yaml");
    String yamlToRemoveTarget = "appDeployments:\n"
        + "  Application:\n"
        + "    myear:\n"
        + "      Target: ''";

    assertDoesNotThrow(() -> Files.write(pathToRemoveTargetYaml, yamlToRemoveTarget.getBytes()));

    // Replace contents of an existing configMap
    replaceConfigMapWithModelFiles(configMapName, domainUid, domainNamespace,
        Arrays.asList(MODEL_DIR + "/model.config.wm.yaml", pathToAddClusterYaml.toString(),
            MODEL_DIR + "/model.jdbc2.yaml", pathToRemoveTargetYaml.toString()), withStandardRetryPolicy);

    // Patch a running domain with introspectVersion.
    String introspectVersion = patchDomainResourceWithNewIntrospectVersion(domainUid, domainNamespace);

    // Verifying introspector pod is created, runs and deleted
    verifyIntrospectorRuns();

    verifyPodIntrospectVersionUpdated(pods.keySet(), introspectVersion);

    // make sure the application is not deployed on cluster
    verifyApplicationRuntimeOnCluster("404");

    // make sure the application is not deployed on admin
    withStandardRetryPolicy.conditionEvaluationListener(
        condition ->
            logger.info("Waiting for application target to be updated. "
                    + "Elapsed time {0}ms, remaining time {1}ms",
                condition.getElapsedTimeInMS(), condition.getRemainingTimeInMS())).until(
                  () -> checkApplicationRuntime(domainNamespace, adminServerPodName,
            adminServerName, "404"));

    verifyPodsNotRolled(pods);
  }

  private void verifyIntrospectorRuns() {
    //verify the introspector pod is created and runs
    logger.info("Verifying introspector pod is created, runs and deleted");
    String introspectJobName = getIntrospectJobName(domainUid);
    checkPodExists(introspectJobName, domainUid, domainNamespace);
    checkPodDoesNotExist(introspectJobName, domainUid, domainNamespace);
  }

  private void verifyIntrospectorFailsWithExpectedErrorMsg(String expectedErrorMsg) {
    // verify the introspector pod is created
    logger.info("Verifying introspector pod is created");
    String introspectJobName = getIntrospectJobName(domainUid);

    // check whether the introspector log contains the expected error message
    logger.info("verifying that the introspector log contains the expected error message");
    withStandardRetryPolicy
        .conditionEvaluationListener(
            condition ->
                logger.info(
                    "Checking for the log of introspector pod contains the expected error msg {0}. "
                        + "Elapsed time {1}ms, remaining time {2}ms",
                    expectedErrorMsg,
                    condition.getElapsedTimeInMS(),
                    condition.getRemainingTimeInMS()))
        .until(() ->
            podLogContainsExpectedErrorMsg(introspectJobName, domainNamespace, expectedErrorMsg));

    // check the status phase of the introspector pod is failed
    logger.info("verifying the status phase of the introspector pod is failed");
    withStandardRetryPolicy
        .conditionEvaluationListener(
            condition ->
                logger.info(
                    "Checking for status phase of introspector pod is failed. "
                        + "Elapsed time {0}ms, remaining time {1}ms",
                    condition.getElapsedTimeInMS(), condition.getRemainingTimeInMS()))
        .until(() ->
            podStatusPhaseContainsString(domainNamespace, introspectJobName, "Failed"));

    // check that the domain status message contains the expected error msg
    logger.info("verifying the domain status message contains the expected error msg");
    withStandardRetryPolicy
        .conditionEvaluationListener(
            condition -> logger.info("Waiting for domain status message contains the expected error msg \"{0}\" "
                    + "(elapsed time {1}ms, remaining time {2}ms)",
                expectedErrorMsg,
                condition.getElapsedTimeInMS(),
                condition.getRemainingTimeInMS()))
        .until(() -> {
          Domain miidomain = getDomainCustomResource(domainUid, domainNamespace);
          return (miidomain != null) && (miidomain.getStatus() != null) && (miidomain.getStatus().getMessage() != null)
              && miidomain.getStatus().getMessage().contains(expectedErrorMsg);
        });

    // check that the domain status condition type is "Failed" and message contains the expected error msg
    logger.info("verifying the domain status condition message contains the expected error msg");
    withStandardRetryPolicy
        .conditionEvaluationListener(
            condition -> logger.info("Waiting for domain status condition message contains the expected error msg "
                    + "\"{0}\", (elapsed time {1}ms, remaining time {2}ms)",
                expectedErrorMsg,
                condition.getElapsedTimeInMS(),
                condition.getRemainingTimeInMS()))
        .until(() -> {
          Domain miidomain = getDomainCustomResource(domainUid, domainNamespace);
          if ((miidomain != null) && (miidomain.getStatus() != null)) {
            for (DomainCondition domainCondition : miidomain.getStatus().getConditions()) {
              if ((domainCondition.getType() != null && domainCondition.getType().equalsIgnoreCase("Failed"))
                  && (domainCondition.getMessage() != null
                  && domainCondition.getMessage().contains(expectedErrorMsg))) {
                return true;
              }
            }
          }
          return false;
        });
  }

  private boolean podLogContainsExpectedErrorMsg(String introspectJobName, String namespace, String errormsg) {
    String introspectPodName;
    V1Pod introspectorPod;

    String labelSelector = String.format("weblogic.domainUID in (%s)", domainUid);

    try {
      introspectorPod = getPod(namespace, labelSelector, introspectJobName);
    } catch (ApiException apiEx) {
      logger.severe("got ApiException while getting pod: {0}", apiEx);
      return false;
    }

    if (introspectorPod != null && introspectorPod.getMetadata() != null) {
      introspectPodName = introspectorPod.getMetadata().getName();
    } else {
      return false;
    }

    String introspectorLog;
    try {
      introspectorLog = getPodLog(introspectPodName, namespace);
      logger.info("introspector log: {0}", introspectorLog);
    } catch (ApiException apiEx) {
      logger.severe("got ApiException while getting pod log: {0}", apiEx);
      return false;
    }

    return introspectorLog.contains(errormsg);
  }

  private boolean podStatusPhaseContainsString(String namespace, String jobName, String expectedPhase) {
    //String introspectPodName = getPodNameFromJobName(namespace, jobName);
    String introspectPodName;
    V1Pod introspectorPod;

    String labelSelector = String.format("weblogic.domainUID in (%s)", domainUid);
    try {
      introspectorPod = getPod(namespace, labelSelector, jobName);
    } catch (ApiException apiEx) {
      logger.severe("Got ApiException while getting pod: {0}", apiEx);
      return false;
    }

    if (introspectorPod != null && introspectorPod.getMetadata() != null) {
      introspectPodName = introspectorPod.getMetadata().getName();
    } else {
      return false;
    }

    try {
      return getPodStatusPhase(namespace, labelSelector, introspectPodName).equalsIgnoreCase(expectedPhase);
    } catch (ApiException apiEx) {
      logger.severe("Got ApiException while getting pod status phase: {0}", apiEx);
      return false;
    }

  }

  private void verifyPodsNotRolled(Map<String, DateTime> podsCreationTimes) {
    // wait for 2 minutes before checking the pods, make right decision logic
    // that runs every two minutes in the  Operator
    try {
      logger.info("Sleep 2 minutes for operator make right decision logic");
      Thread.sleep(120 * 1000);
    } catch (InterruptedException ie) {
      logger.info("InterruptedException while sleeping for 2 minutes");
    }
    for (Map.Entry<String, DateTime> entry : podsCreationTimes.entrySet()) {
      assertEquals(
          entry.getValue(),
          getPodCreationTime(domainNamespace, entry.getKey()),
          "pod '" + entry.getKey() + "' should not roll");
    }
  }

  private void verifyPodIntrospectVersionUpdated(Set<String> podNames, String expectedIntrospectVersion) {
    for (String podName : podNames) {
      withStandardRetryPolicy
          .conditionEvaluationListener(
              condition ->
                  logger.info(
                      "Checking for updated introspectVersion for pod {0}. "
                          + "Elapsed time {1}ms, remaining time {2}ms",
                      podName, condition.getElapsedTimeInMS(), condition.getRemainingTimeInMS()))
          .until(
              () ->
                  podIntrospectVersionUpdated(podName, domainNamespace, expectedIntrospectVersion));
    }
  }

  private void verifyPodLabelUpdated(Set<String> podNames, String label) throws ApiException {
    for (String podName : podNames) {
      assertNotNull(getPod(domainNamespace, label, podName),
          "Pod " + podName + " doesn't have label " + label);
    }
  }

  private void verifyPodLabelRemoved(Set<String> podNames, String label) throws ApiException {
    for (String podName : podNames) {
      assertNull(getPod(domainNamespace, label, podName),
          "Pod " + podName + " still have the label " + label);
    }
  }

  private void verifyMinThreadsConstraintRuntime(int count) {
    withStandardRetryPolicy.conditionEvaluationListener(
        condition ->
            logger.info("Waiting for min threads constraint configuration to be updated. "
                    + "Elapsed time {0}ms, remaining time {1}ms",
                condition.getElapsedTimeInMS(), condition.getRemainingTimeInMS())).until(
                  () -> checkMinThreadsConstraintRuntime(count));
  }

  private void verifyMaxThredsConstraintRuntime(int count) {
    withStandardRetryPolicy.conditionEvaluationListener(
        condition ->
            logger.info("Waiting for max threads constraint configuration to be updated. "
                    + "Elapsed time {0}ms, remaining time {1}ms",
                condition.getElapsedTimeInMS(), condition.getRemainingTimeInMS())).until(
                  () -> checkMaxThreadsConstraintRuntime(count));
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

  /**
   * Check application runtime using REST Api.
   *
   * @param expectedStatusCode expected status code
   */
  private void verifyApplicationRuntimeOnCluster(String expectedStatusCode) {
    // make sure the application is deployed on cluster
    for (int i = 1; i <= replicaCount; i++) {
      final int j = i;
      withStandardRetryPolicy.conditionEvaluationListener(
          condition ->
              logger.info("Waiting for application target to be updated. "
                      + "Elapsed time {0}ms, remaining time {1}ms",
                  condition.getElapsedTimeInMS(), condition.getRemainingTimeInMS())).until(
                    () -> checkApplicationRuntime(domainNamespace, adminServerPodName,
              MANAGED_SERVER_NAME_BASE + j, expectedStatusCode));

    }
  }

  /**
   * Verify the application access on all the servers pods in the cluster.
   */
  private void verifyApplicationAccessOnCluster() {
    // check and wait for the application to be accessible in all server pods
    for (int i = 1; i <= replicaCount; i++) {
      checkAppIsRunning(
          withQuickRetryPolicy,
          domainNamespace,
          managedServerPrefix + i,
          "8001",
          "sample-war/index.jsp",
          MII_APP_RESPONSE_V1 + i);
    }
  }

  /**
   * Verify domain status conditions contains the given condition type and message.
   * @param conditionType condition type
   * @param conditionMsg messsage in condition
   * @return true if the condition matches
   */
  private boolean verifyDomainStatusCondition(String conditionType, String conditionMsg) {
    withStandardRetryPolicy
        .conditionEvaluationListener(
            condition -> logger.info("Waiting for domain status condition message contains the expected msg "
                    + "\"{0}\", (elapsed time {1}ms, remaining time {2}ms)",
                conditionMsg,
                condition.getElapsedTimeInMS(),
                condition.getRemainingTimeInMS()))
        .until(() -> {
          Domain miidomain = getDomainCustomResource(domainUid, domainNamespace);
          if ((miidomain != null) && (miidomain.getStatus() != null)) {
            for (DomainCondition domainCondition : miidomain.getStatus().getConditions()) {
              logger.info("Condition Type =" + domainCondition.getType()
                  + " Condition Msg =" + domainCondition.getMessage());
              if (domainCondition.getType() != null && domainCondition.getMessage() != null) {
                logger.info("condition " + domainCondition.getType().equalsIgnoreCase(conditionType)
                    + " msg " + domainCondition.getMessage().contains(conditionMsg));
              }
              if ((domainCondition.getType() != null && domainCondition.getType().equalsIgnoreCase(conditionType))
                  && (domainCondition.getMessage() != null && domainCondition.getMessage().contains(conditionMsg))) {
                return true;
              }
            }
          }
          return false;
        });
    return false;
  }

  /**
   * Verify domain status conditions contains the given condition type and reason.
   * @param conditionType condition type
   * @param conditionReason reason in condition
   * @return true if the condition matches
   */
  private boolean verifyDomainStatusConditionNoErrorMsg(String conditionType, String conditionReason) {
    withStandardRetryPolicy
        .conditionEvaluationListener(
            condition -> logger.info("Waiting for domain status condition message contains the expected msg "
                    + "\"{0}\", (elapsed time {1}ms, remaining time {2}ms)",
                conditionReason,
                condition.getElapsedTimeInMS(),
                condition.getRemainingTimeInMS()))
        .until(() -> {
          Domain miidomain = getDomainCustomResource(domainUid, domainNamespace);
          if ((miidomain != null) && (miidomain.getStatus() != null)) {
            for (DomainCondition domainCondition : miidomain.getStatus().getConditions()) {
              logger.info("Condition Type =" + domainCondition.getType()
                  + " Condition Reason =" + domainCondition.getReason());
              if ((domainCondition.getType() != null && domainCondition.getType().equalsIgnoreCase(conditionType))
                  && (domainCondition.getReason() != null && domainCondition.getReason().contains(conditionReason))) {
                return true;
              }
            }
          }
          return false;
        });
    return false;
  }

  private LinkedHashMap<String, DateTime> addDataSourceAndVerify(boolean introspectorRuns) {

    LinkedHashMap<String, DateTime> pods = new LinkedHashMap<>();

    // get the creation time of the admin server pod before patching
    DateTime adminPodCreationTime = getPodCreationTime(domainNamespace, adminServerPodName);
    pods.put(adminServerPodName, getPodCreationTime(domainNamespace, adminServerPodName));
    // get the creation time of the managed server pods before patching
    for (int i = 1; i <= replicaCount; i++) {
      pods.put(managedServerPrefix + i, getPodCreationTime(domainNamespace, managedServerPrefix + i));
    }

    // Replace contents of an existing configMap with cm config and application target as
    // there are issues with removing them, https://jira.oraclecorp.com/jira/browse/WDT-535
    replaceConfigMapWithModelFiles(configMapName, domainUid, domainNamespace,
        Arrays.asList(MODEL_DIR + "/model.config.wm.yaml", pathToAddClusterYaml.toString(),
            MODEL_DIR + "/model.jdbc2.yaml"), withStandardRetryPolicy);

    // Patch a running domain with introspectVersion.
    String introspectVersion = patchDomainResourceWithNewIntrospectVersion(domainUid, domainNamespace);

    // Verifying introspector pod is created, runs and deleted
    // if the config map content is not changed, its possible to miss the introspector pod creation/deletion as
    // it will be very quick, skip the check in those cases
    if (introspectorRuns) {
      verifyIntrospectorRuns();
    }

    verifyPodsNotRolled(pods);

    verifyPodIntrospectVersionUpdated(pods.keySet(), introspectVersion);

    // check datasource configuration using REST api
    int adminServiceNodePort
        = getServiceNodePort(domainNamespace, getExternalServicePodName(adminServerPodName), "default");
    assertNotEquals(-1, adminServiceNodePort, "admin server default node port is not valid");
    assertTrue(checkSystemResourceConfiguration(adminServiceNodePort, "JDBCSystemResources",
        "TestDataSource2", "200"), "JDBCSystemResource not found");
    logger.info("JDBCSystemResource configuration found");
    return pods;

  }
}
