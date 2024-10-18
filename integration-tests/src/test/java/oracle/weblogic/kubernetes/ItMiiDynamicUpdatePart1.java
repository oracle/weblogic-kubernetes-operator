// Copyright (c) 2021, 2024, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.weblogic.kubernetes;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;

import oracle.weblogic.kubernetes.annotations.IntegrationTest;
import oracle.weblogic.kubernetes.annotations.Namespaces;
import oracle.weblogic.kubernetes.logging.LoggingFacade;
import oracle.weblogic.kubernetes.utils.MiiDynamicUpdateHelper;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import static oracle.weblogic.kubernetes.TestConstants.MANAGED_SERVER_NAME_BASE;
import static oracle.weblogic.kubernetes.TestConstants.MII_APP_RESPONSE_V1;
import static oracle.weblogic.kubernetes.actions.ActionConstants.MODEL_DIR;
import static oracle.weblogic.kubernetes.actions.ActionConstants.RESOURCE_DIR;
import static oracle.weblogic.kubernetes.actions.ActionConstants.WORK_DIR;
import static oracle.weblogic.kubernetes.actions.TestActions.patchDomainResourceWithNewIntrospectVersion;
import static oracle.weblogic.kubernetes.actions.TestActions.scaleCluster;
import static oracle.weblogic.kubernetes.utils.ApplicationUtils.checkAppIsRunning;
import static oracle.weblogic.kubernetes.utils.CommonMiiTestUtils.checkApplicationRuntime;
import static oracle.weblogic.kubernetes.utils.CommonMiiTestUtils.checkWorkManagerRuntime;
import static oracle.weblogic.kubernetes.utils.CommonMiiTestUtils.readMaxThreadsConstraintRuntimeForWorkManager;
import static oracle.weblogic.kubernetes.utils.CommonMiiTestUtils.readMinThreadsConstraintRuntimeForWorkManager;
import static oracle.weblogic.kubernetes.utils.CommonMiiTestUtils.replaceConfigMapWithModelFiles;
import static oracle.weblogic.kubernetes.utils.CommonMiiTestUtils.verifyIntrospectorRuns;
import static oracle.weblogic.kubernetes.utils.CommonMiiTestUtils.verifyPodIntrospectVersionUpdated;
import static oracle.weblogic.kubernetes.utils.CommonMiiTestUtils.verifyPodsNotRolled;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.checkPodReadyAndServiceExists;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.runClientInsidePod;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.runJavacInsidePod;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.testUntil;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.withLongRetryPolicy;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.withQuickRetryPolicy;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.withStandardRetryPolicy;
import static oracle.weblogic.kubernetes.utils.FileUtils.copyFileToPod;
import static oracle.weblogic.kubernetes.utils.PatchDomainUtils.patchDomainResourceWithNewReplicaCountAtSpecLevel;
import static oracle.weblogic.kubernetes.utils.PodUtils.checkPodDeleted;
import static oracle.weblogic.kubernetes.utils.PodUtils.getPodCreationTime;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * This test class verifies the adding and updating work manager, adding cluster,
 * changing application target, changing dynamic cluster size and removing application target
 * in a running WebLogic domain.
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DisplayName("Test dynamic updates to a model in image domain, part1")
@IntegrationTest
@Tag("kind-parallel")
@Tag("toolkits-srg")
@Tag("okd-wls-mrg")
@Tag("oke-sequential")
@Tag("oke-arm")
@Tag("olcne-srg")
class ItMiiDynamicUpdatePart1 {

  static MiiDynamicUpdateHelper helper = new MiiDynamicUpdateHelper();
  private static final String domainUid = "mii-dynamic-update1";
  static String workManagerName = "newWM";
  public static Path pathToChangeTargetYaml = null;
  public static Path pathToAddClusterYaml = null;
  static LoggingFacade logger = null;

  /**
   * Install Operator.
   * Create domain resource defintion.
   *
   * @param namespaces list of namespaces created by the IntegrationTestWatcher by the
   *                   JUnit engine parameter resolution mechanism
   */
  @BeforeAll
  public static void initAll(@Namespaces(2) List<String> namespaces) {
    helper.initAll(namespaces, domainUid);
    logger = helper.logger;

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
    helper.beforeEach();
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
  @Tag("crio")
  void testMiiAddWorkManager() {

    // This test uses the WebLogic domain created in BeforeAll method
    // BeforeEach method ensures that the server pods are running

    LinkedHashMap<String, OffsetDateTime> pods = new LinkedHashMap<>();

    // get the creation time of the admin server pod before patching
    pods.put(helper.adminServerPodName, 
        getPodCreationTime(helper.domainNamespace, helper.adminServerPodName));
    // get the creation time of the managed server pods before patching
    for (int i = 1; i <= helper.replicaCount; i++) {
      pods.put(helper.managedServerPrefix + i, 
          getPodCreationTime(helper.domainNamespace, helper.managedServerPrefix + i));
    }
    replaceConfigMapWithModelFiles(MiiDynamicUpdateHelper.configMapName, domainUid, helper.domainNamespace,
        Arrays.asList(MODEL_DIR + "/model.config.wm.yaml"), withStandardRetryPolicy);

    String introspectVersion = patchDomainResourceWithNewIntrospectVersion(domainUid, helper.domainNamespace);

    verifyIntrospectorRuns(domainUid, helper.domainNamespace);

    testUntil(
        () -> checkWorkManagerRuntime(helper.adminSvcExtHost, helper.domainNamespace,
            helper.adminServerPodName,MANAGED_SERVER_NAME_BASE + "1",
          workManagerName, "200"),
        logger,
        "work manager configuration to be updated.");
    logger.info("Found new work manager configuration");

    verifyPodsNotRolled(helper.domainNamespace, pods);

    verifyPodIntrospectVersionUpdated(pods.keySet(), introspectVersion, helper.domainNamespace);
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
  void testMiiUpdateWorkManager() {

    // This test uses the WebLogic domain created in BeforeAll method
    // BeforeEach method ensures that the server pods are running

    LinkedHashMap<String, OffsetDateTime> pods = new LinkedHashMap<>();

    // get the creation time of the admin server pod before patching
    pods.put(helper.adminServerPodName, 
        getPodCreationTime(helper.domainNamespace, helper.adminServerPodName));
    // get the creation time of the managed server pods before patching
    for (int i = 1; i <= helper.replicaCount; i++) {
      pods.put(helper.managedServerPrefix + i, 
          getPodCreationTime(helper.domainNamespace, helper.managedServerPrefix + i));
    }

    replaceConfigMapWithModelFiles(MiiDynamicUpdateHelper.configMapName, domainUid, helper.domainNamespace,
        Arrays.asList(MODEL_DIR + "/model.update.wm.yaml"),
        withStandardRetryPolicy);

    String introspectVersion = 
        patchDomainResourceWithNewIntrospectVersion(domainUid, helper.domainNamespace);

    verifyIntrospectorRuns(domainUid, helper.domainNamespace);

    verifyMinThreadsConstraintRuntime(2);

    verifyMaxThredsConstraintRuntime(20);

    logger.info("Found updated work manager configuration");

    verifyPodsNotRolled(helper.domainNamespace, pods);

    verifyPodIntrospectVersionUpdated(pods.keySet(), introspectVersion, helper.domainNamespace);

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
  void testMiiChangeTarget() {

    // This test uses the WebLogic domain created in BeforeAll method
    // BeforeEach method ensures that the server pods are running

    LinkedHashMap<String, OffsetDateTime> pods = new LinkedHashMap<>();

    // get the creation time of the admin server pod before patching
    pods.put(helper.adminServerPodName,
        getPodCreationTime(helper.domainNamespace, helper.adminServerPodName));
    // get the creation time of the managed server pods before patching
    for (int i = 1; i <= helper.replicaCount; i++) {
      pods.put(helper.managedServerPrefix + i,
          getPodCreationTime(helper.domainNamespace, helper.managedServerPrefix + i));
    }

    // make sure the application is not deployed on admin server
    assertFalse(checkApplicationRuntime(helper.adminSvcExtHost, helper.domainNamespace,
            helper.adminServerPodName, helper.adminServerName, "200"),
        "Application deployed on " + helper.adminServerName + " before the dynamic update");

    // check and wait for the application to be accessible in all server pods
    verifyApplicationAccessOnCluster();

    // Replace contents of an existing configMap
    replaceConfigMapWithModelFiles(MiiDynamicUpdateHelper.configMapName, domainUid, helper.domainNamespace,
        Arrays.asList(MODEL_DIR + "/model.config.wm.yaml", pathToChangeTargetYaml.toString()), withStandardRetryPolicy);

    // Patch a running domain with introspectVersion.
    String introspectVersion = patchDomainResourceWithNewIntrospectVersion(domainUid, helper.domainNamespace);

    // Verifying introspector pod is created, runs and deleted
    verifyIntrospectorRuns(domainUid, helper.domainNamespace);

    // check and wait for the application to be accessible in all server pods
    verifyApplicationAccessOnCluster();

    // check and wait for the application to be accessible in admin pod
    checkAppIsRunning(
        withQuickRetryPolicy,
        helper.domainNamespace,
        helper.adminServerPodName,
        "7001",
        "sample-war/index.jsp",
        "Hello World");

    verifyPodsNotRolled(helper.domainNamespace, pods);

    verifyPodIntrospectVersionUpdated(pods.keySet(), introspectVersion, helper.domainNamespace);
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
  void testMiiAddCluster() {
    // This test uses the WebLogic domain created in BeforeAll method
    // BeforeEach method ensures that the server pods are running

    LinkedHashMap<String, OffsetDateTime> pods = new LinkedHashMap<>();

    // get the creation time of the admin server pod before patching
    OffsetDateTime adminPodCreationTime = getPodCreationTime(helper.domainNamespace, helper.adminServerPodName);
    pods.put(helper.adminServerPodName, getPodCreationTime(helper.domainNamespace, helper.adminServerPodName));
    // get the creation time of the managed server pods before patching
    for (int i = 1; i <= helper.replicaCount; i++) {
      pods.put(helper.managedServerPrefix + i,
          getPodCreationTime(helper.domainNamespace, helper.managedServerPrefix + i));
    }

    // Replace contents of an existing configMap with cm config and application target as
    // there are issues with removing them, WDT-535
    replaceConfigMapWithModelFiles(MiiDynamicUpdateHelper.configMapName, domainUid, helper.domainNamespace,
        Arrays.asList(MODEL_DIR + "/model.config.wm.yaml",
            pathToAddClusterYaml.toString()), withStandardRetryPolicy);

    // change replica to have the servers running in the newly added cluster
    assertTrue(patchDomainResourceWithNewReplicaCountAtSpecLevel(
        domainUid, helper.domainNamespace, helper.replicaCount),
        "failed to patch the replicas at spec level");

    // Patch a running domain with introspectVersion.
    String introspectVersion = patchDomainResourceWithNewIntrospectVersion(domainUid, helper.domainNamespace);

    // Verifying introspector pod is created, runs and deleted
    verifyIntrospectorRuns(domainUid, helper.domainNamespace);

    // check the servers are started in newly added cluster and the server services and pods are ready
    for (int i = 1; i <= helper.replicaCount; i++) {
      logger.info("Wait for managed server services and pods are created in namespace {0}",
          helper.domainNamespace);
      checkPodReadyAndServiceExists(domainUid + "-dynamic-server" + i, domainUid, helper.domainNamespace);
    }

    verifyPodsNotRolled(helper.domainNamespace, pods);

    verifyPodIntrospectVersionUpdated(pods.keySet(), introspectVersion, helper.domainNamespace);

  }

  /**
   * Modify MaxDynamicClusterSize and MinDynamicClusterSize using dynamic update.
   * Verify the cluster cannot be scaled beyond the modified MaxDynamicClusterSize value.
   * Verify JMS message and connection distribution/load balance after scaling the cluster.
   */
  @Test
  @Order(5)
  @DisplayName("Test modification to Dynamic cluster size parameters")
  void testMiiUpdateDynamicClusterSize() {
    String clusterName = "cluster-1";
    String clusterResName = domainUid + "-" + clusterName;
    // Scale the cluster by updating the replica count to 5
    logger.info("[Before Patching] updating the replica count to 5");
    boolean p1Success = scaleCluster(clusterResName, helper.domainNamespace,5);
    assertTrue(p1Success,
        String.format("Patching replica to 5 failed for cluster %s in namespace %s",
            clusterName, helper.domainNamespace));

    // Make sure the cluster can be scaled to replica count 5 as MaxDynamicClusterSize is set to 5
    checkPodReadyAndServiceExists(helper.managedServerPrefix + "2", domainUid, helper.domainNamespace);
    checkPodReadyAndServiceExists(helper.managedServerPrefix + "3", domainUid, helper.domainNamespace);
    checkPodReadyAndServiceExists(helper.managedServerPrefix + "4", domainUid, helper.domainNamespace);
    checkPodReadyAndServiceExists(helper.managedServerPrefix + "5", domainUid, helper.domainNamespace);

    // Make sure the cluster can be scaled to replica count 1 as MinDynamicClusterSize is set to 1
    logger.info("[Before Patching] updating the replica count to 1");
    boolean p11Success = scaleCluster(clusterResName, helper.domainNamespace, 1);
    assertTrue(p11Success,
        String.format("replica patching to 1 failed for domain %s in namespace %s", domainUid, helper.domainNamespace));

    checkPodDeleted(helper.managedServerPrefix + "2", domainUid, helper.domainNamespace);
    checkPodDeleted(helper.managedServerPrefix + "3", domainUid, helper.domainNamespace);
    checkPodDeleted(helper.managedServerPrefix + "4", domainUid, helper.domainNamespace);
    checkPodDeleted(helper.managedServerPrefix + "5", domainUid, helper.domainNamespace);

    // Bring back the cluster to originally configured replica count
    logger.info("[Before Patching] updating the replica count to 1");
    boolean p2Success = scaleCluster(clusterResName, helper.domainNamespace, helper.replicaCount);
    assertTrue(p2Success,
        String.format("replica patching to 1 failed for cluster %s in namespace %s",
            clusterName, helper.domainNamespace));
    for (int i = 1; i <= helper.replicaCount; i++) {
      checkPodReadyAndServiceExists(helper.managedServerPrefix + i, domainUid, helper.domainNamespace);
    }

    // get the creation time of the server pods before patching
    LinkedHashMap<String, OffsetDateTime> pods = new LinkedHashMap<>();
    OffsetDateTime adminPodCreationTime = getPodCreationTime(helper.domainNamespace, helper.adminServerPodName);
    pods.put(helper.adminServerPodName, adminPodCreationTime);
    for (int i = 1; i <= helper.replicaCount; i++) {
      pods.put(helper.managedServerPrefix + i,
          getPodCreationTime(helper.domainNamespace, helper.managedServerPrefix + i));
    }

    // Update the Dynamic ClusterSize and add distributed destination to verify JMS connection and message distribution
    // after the cluster is scaled.
    replaceConfigMapWithModelFiles(MiiDynamicUpdateHelper.configMapName, domainUid, helper.domainNamespace,
        Arrays.asList(MODEL_DIR + "/model.config.wm.yaml", pathToAddClusterYaml.toString(),
            MODEL_DIR + "/model.cluster.size.yaml"), withStandardRetryPolicy);

    // Patch a running domain with introspectVersion.
    String introspectVersion = patchDomainResourceWithNewIntrospectVersion(domainUid, helper.domainNamespace);

    // Verifying introspector pod is created, runs and deleted
    verifyIntrospectorRuns(domainUid, helper.domainNamespace);

    verifyPodIntrospectVersionUpdated(pods.keySet(), introspectVersion, helper.domainNamespace);

    verifyPodsNotRolled(helper.domainNamespace, pods);

    // build the standalone JMS Client on Admin pod after rolling restart
    String destLocation = "/u01/JmsTestClient.java";
    assertDoesNotThrow(() -> copyFileToPod(helper.domainNamespace,
        helper.adminServerPodName, "",
        Paths.get(RESOURCE_DIR, "tunneling", "JmsTestClient.java"),
        Paths.get(destLocation)));
    runJavacInsidePod(helper.adminServerPodName, helper.domainNamespace, destLocation);

    // Scale the cluster using replica count 5, patch cluster should fail as max size is 4
    logger.info("[After Patching] updating the replica count to 5");
    boolean p3Success = scaleCluster(clusterResName, helper.domainNamespace, 5);
    assertFalse(p3Success,
        String.format("replica patching to 5 should fail for domain %s in namespace %s",
            domainUid, helper.domainNamespace));

    // Run standalone JMS Client inside the pod using weblogic.jar in classpath.
    // The client sends 300 messsage to a Uniform Distributed Queue.
    // Make sure the messages are distributed across the members evenly
    // and JMS connection is load balanced across all servers
    testUntil(withLongRetryPolicy,
        runClientInsidePod(helper.adminServerPodName, helper.domainNamespace,
          "/u01", "JmsTestClient", "t3://" + domainUid + "-cluster-cluster-1:8001", "2", "true"),
        logger,
        "Wait for t3 JMS Client to access WLS");
  }

  /**
   * Recreate configmap containing application config target to none.
   * Patch the domain resource with the configmap.
   * Update the introspect version of the domain resource.
   * Wait for introspector to complete
   * Verify application target is changed by accessing the application runtime using REST API.
   */
  @Test
  @Order(6)
  @DisplayName("Remove all targets for the application deployment in MII domain using mii dynamic update")
  void testMiiRemoveTarget() {

    // This test uses the WebLogic domain created in BeforeAll method
    // BeforeEach method ensures that the server pods are running

    LinkedHashMap<String, OffsetDateTime> pods = new LinkedHashMap<>();

    // get the creation time of the admin server pod before patching
    OffsetDateTime adminPodCreationTime = getPodCreationTime(helper.domainNamespace, helper.adminServerPodName);
    pods.put(helper.adminServerPodName, getPodCreationTime(helper.domainNamespace, helper.adminServerPodName));
    // get the creation time of the managed server pods before patching
    for (int i = 1; i <= helper.replicaCount; i++) {
      pods.put(helper.managedServerPrefix + i,
          getPodCreationTime(helper.domainNamespace, helper.managedServerPrefix + i));
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
    replaceConfigMapWithModelFiles(MiiDynamicUpdateHelper.configMapName, domainUid, helper.domainNamespace,
        Arrays.asList(MODEL_DIR + "/model.config.wm.yaml", pathToAddClusterYaml.toString(),
            MODEL_DIR + "/model.cluster.size.yaml",
            pathToRemoveTargetYaml.toString()), withStandardRetryPolicy);

    // Patch a running domain with introspectVersion.
    String introspectVersion = patchDomainResourceWithNewIntrospectVersion(domainUid, helper.domainNamespace);

    // Verifying introspector pod is created, runs and deleted
    verifyIntrospectorRuns(domainUid, helper.domainNamespace);

    verifyPodIntrospectVersionUpdated(pods.keySet(), introspectVersion, helper.domainNamespace);

    // make sure the application is not deployed on cluster
    verifyApplicationRuntimeOnCluster("404");

    // make sure the application is not deployed on admin
    testUntil(
        () -> checkApplicationRuntime(helper.adminSvcExtHost, helper.domainNamespace,
            helper.adminServerPodName, helper.adminServerName, "404"),
        logger,
        "application target to be updated.");

    verifyPodsNotRolled(helper.domainNamespace, pods);
  }

  void verifyMinThreadsConstraintRuntime(int count) {
    testUntil(
        () -> checkMinThreadsConstraintRuntime(count),
        logger,
        "min threads constraint configuration to be updated");
  }

  void verifyMaxThredsConstraintRuntime(int count) {
    testUntil(
        () -> checkMaxThreadsConstraintRuntime(count),
        logger,
        "max threads constraint configuration to be updated");
  }

  /*
   * Verify the min threads constraint runtime configuration through rest API.
   * @param count expected value for min threads constraint count
   * @returns true if the min threads constraint runtime is read successfully and is configured
   *          with the provided count value.
   **/
  boolean checkMinThreadsConstraintRuntime(int count) {
    String result = readMinThreadsConstraintRuntimeForWorkManager(helper.adminSvcExtHost,
        helper.domainNamespace, helper.adminServerPodName,
        MANAGED_SERVER_NAME_BASE + "1", workManagerName);
    if (result != null) {
      logger.info("readMinThreadsConstraintRuntime read " + result.toString());
      return (result != null && result.contains("\"count\": " + count));
    }
    logger.info("readMinThreadsConstraintRuntime failed to read from WebLogic server ");
    return false;
  }


  /**
   * Check application runtime using REST Api.
   *
   * @param expectedStatusCode expected status code
   */
  private void verifyApplicationRuntimeOnCluster(String expectedStatusCode) {
    // make sure the application is deployed on cluster
    for (int i = 1; i <= helper.replicaCount; i++) {
      final int j = i;
      testUntil(
          () -> checkApplicationRuntime(helper.adminSvcExtHost, helper.domainNamespace,
              helper.adminServerPodName,MANAGED_SERVER_NAME_BASE + j, expectedStatusCode),
          logger,
          "application target to be updated");
    }
  }

  /**
   * Verify the application access on all the servers pods in the cluster.
   */
  private void verifyApplicationAccessOnCluster() {
    // check and wait for the application to be accessible in all server pods
    for (int i = 1; i <= helper.replicaCount; i++) {
      checkAppIsRunning(
          withQuickRetryPolicy,
          helper.domainNamespace,
          helper.managedServerPrefix + i,
          "8001",
          "sample-war/index.jsp",
          MII_APP_RESPONSE_V1 + i);
    }
  }

  /*
   * Verify the max threads constraint runtime configuration through rest API.
   * @param count expected value for max threads constraint count
   * @returns true if the max threads constraint runtime is read successfully and is configured
   *          with the provided count value.
   **/
  boolean checkMaxThreadsConstraintRuntime(int count) {
    String result = readMaxThreadsConstraintRuntimeForWorkManager(helper.adminSvcExtHost,
        helper.domainNamespace, helper.adminServerPodName,
        MANAGED_SERVER_NAME_BASE + "1", workManagerName);
    if (result != null) {
      logger.info("readMaxThreadsConstraintRuntime read " + result.toString());
      return (result != null && result.contains("\"count\": " + count));
    }
    logger.info("readMaxThreadsConstraintRuntime failed to read from WebLogic server ");
    return false;
  }


}
