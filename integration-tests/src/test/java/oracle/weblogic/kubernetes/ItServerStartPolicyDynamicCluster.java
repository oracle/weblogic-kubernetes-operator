// Copyright (c) 2021, 2023, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.weblogic.kubernetes;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.concurrent.Callable;

import oracle.weblogic.kubernetes.annotations.IntegrationTest;
import oracle.weblogic.kubernetes.annotations.Namespaces;
import oracle.weblogic.kubernetes.logging.LoggingFacade;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import static java.util.concurrent.TimeUnit.MINUTES;
import static java.util.concurrent.TimeUnit.SECONDS;
import static oracle.weblogic.kubernetes.actions.TestActions.getPodCreationTimestamp;
import static oracle.weblogic.kubernetes.assertions.TestAssertions.isPodRestarted;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.checkClusterReplicaCountMatches;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.checkPodReadyAndServiceExists;
import static oracle.weblogic.kubernetes.utils.OKDUtils.createRouteForOKD;
import static oracle.weblogic.kubernetes.utils.PatchDomainUtils.patchServerStartPolicy;
import static oracle.weblogic.kubernetes.utils.PodUtils.checkIsPodRestarted;
import static oracle.weblogic.kubernetes.utils.PodUtils.checkPodDeleted;
import static oracle.weblogic.kubernetes.utils.PodUtils.checkPodDoesNotExist;
import static oracle.weblogic.kubernetes.utils.PodUtils.checkPodInitialized;
import static oracle.weblogic.kubernetes.utils.PodUtils.checkPodRestarted;
import static oracle.weblogic.kubernetes.utils.PodUtils.getPodCreationTime;
import static oracle.weblogic.kubernetes.utils.ServerStartPolicyUtils.CLUSTER_LIFECYCLE;
import static oracle.weblogic.kubernetes.utils.ServerStartPolicyUtils.CONFIG_CLUSTER;
import static oracle.weblogic.kubernetes.utils.ServerStartPolicyUtils.DYNAMIC_CLUSTER;
import static oracle.weblogic.kubernetes.utils.ServerStartPolicyUtils.ROLLING_CLUSTER_SCRIPT;
import static oracle.weblogic.kubernetes.utils.ServerStartPolicyUtils.SCALE_CLUSTER_SCRIPT;
import static oracle.weblogic.kubernetes.utils.ServerStartPolicyUtils.SERVER_LIFECYCLE;
import static oracle.weblogic.kubernetes.utils.ServerStartPolicyUtils.START_CLUSTER_SCRIPT;
import static oracle.weblogic.kubernetes.utils.ServerStartPolicyUtils.START_SERVER_SCRIPT;
import static oracle.weblogic.kubernetes.utils.ServerStartPolicyUtils.STOP_CLUSTER_SCRIPT;
import static oracle.weblogic.kubernetes.utils.ServerStartPolicyUtils.STOP_SERVER_SCRIPT;
import static oracle.weblogic.kubernetes.utils.ServerStartPolicyUtils.checkManagedServerConfiguration;
import static oracle.weblogic.kubernetes.utils.ServerStartPolicyUtils.executeLifecycleScript;
import static oracle.weblogic.kubernetes.utils.ServerStartPolicyUtils.managedServerNamePrefix;
import static oracle.weblogic.kubernetes.utils.ServerStartPolicyUtils.prepare;
import static oracle.weblogic.kubernetes.utils.ServerStartPolicyUtils.restoreEnv;
import static oracle.weblogic.kubernetes.utils.ServerStartPolicyUtils.scalingClusters;
import static oracle.weblogic.kubernetes.utils.ThreadSafeLogger.getLogger;
import static org.awaitility.Awaitility.with;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests to verify that life cycle operation of dynamic cluster does not impact the state of config cluster.
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DisplayName("ServerStartPolicy attribute in different levels in a MII domain dynamic cluster")
@IntegrationTest
@Tag("olcne")
@Tag("oke-parallel")
@Tag("kind-parallel")
@Tag("okd-wls-mrg")
class ItServerStartPolicyDynamicCluster {

  private static String domainNamespace = null;
  private static String opNamespace = null;

  private static final int replicaCount = 1;
  private static final String domainUid = "mii-start-policy";

  private static final String adminServerPodName = domainUid + "-admin-server";
  private final String managedServerPrefix = domainUid + "-" + managedServerNamePrefix;
  private static LoggingFacade logger = null;
  private static String ingressHost = null; //only used for OKD
  private static final String samplePath = "sample-testing-dynamic-cluster";
  private static final String dynamicClusterResourceName = DYNAMIC_CLUSTER;
  private static final String configuredClusterResourceName = CONFIG_CLUSTER;

  /**
   * Install Operator.
   * Create a domain resource.
   * @param namespaces list of namespaces created by the IntegrationTestWatcher by the
  JUnit engine parameter resolution mechanism
   */
  @BeforeAll
  public static void initAll(@Namespaces(2) List<String> namespaces) {
    logger = getLogger();

    // get a new unique opNamespace
    logger.info("Creating unique namespace for Operator");
    assertNotNull(namespaces.get(0), "Namespace list is null");
    opNamespace = namespaces.get(0);

    logger.info("Creating unique namespace for Domain");
    assertNotNull(namespaces.get(1), "Namespace list is null");
    domainNamespace = namespaces.get(1);

    prepare(domainNamespace, domainUid, opNamespace, samplePath);

    // In OKD environment, the node port cannot be accessed directly. Have to create an ingress
    ingressHost = createRouteForOKD(adminServerPodName + "-ext", domainNamespace);
  }

  /**
   * Verify all server pods are running.
   * Verify k8s services for all servers are created.
   */
  @BeforeEach
  public void beforeEach() {

    logger.info("Check admin service/pod {0} is created in namespace {1}",
        adminServerPodName, domainNamespace);
    checkPodReadyAndServiceExists(adminServerPodName,
        domainUid, domainNamespace);

    for (int i = 1; i <= replicaCount; i++) {
      checkPodReadyAndServiceExists(managedServerPrefix + i,
          domainUid, domainNamespace);
    }

    // Check configured cluster configuration is available
    boolean isServerConfigured =
        checkManagedServerConfiguration("config-cluster-server1", domainNamespace, adminServerPodName);
    assertTrue(isServerConfigured,
        "Could not find managed server from configured cluster");
    logger.info("Found managed server from configured cluster");

    // Check standalone server configuration is available
    boolean isStandaloneServerConfigured =
        checkManagedServerConfiguration("standalone-managed", domainNamespace, adminServerPodName);
    assertTrue(isStandaloneServerConfigured,
        "Could not find standalone managed server from configured cluster");
    logger.info("Found standalone managed server configuration");
  }

  /**
   * Stop the dynamic cluster using the sample script stopCluster.sh.
   * Verify that server(s) in the dynamic cluster are stopped.
   * Verify that server(s) in the configured cluster are in the RUNNING state.
   * Restart the dynamic cluster using the sample script startCluster.sh
   * Make sure that servers in the dynamic cluster are in RUNNING state again.
   * The usecase also verify the scripts startCluster.sh/stopCluster.sh make
   * no changes in a running/stopped cluster respectively.
   */
  @Order(1)
  @Test
  @DisplayName("Restart the dynamic cluster with serverStartPolicy")
  void testDynamicClusterRestart() {

    String dynamicServerPodName = domainUid + "-managed-server1";
    String configServerPodName = domainUid + "-config-cluster-server1";

    OffsetDateTime cfgTs = getPodCreationTime(domainNamespace, configServerPodName);
    checkPodReadyAndServiceExists(dynamicServerPodName, domainUid, domainNamespace);
    // startCluster.sh does not take any action on a running cluster
    String result = executeLifecycleScript(domainUid, domainNamespace,
        samplePath, START_CLUSTER_SCRIPT, CLUSTER_LIFECYCLE, DYNAMIC_CLUSTER);
    assertTrue(result.contains("No changes needed"), "startCluster.sh shouldn't make changes");

    // Verify dynamic server are shut down after stopCluster script execution
    logger.info("Stop dynamic cluster using the script");
    executeLifecycleScript(domainUid, domainNamespace, samplePath,
        STOP_CLUSTER_SCRIPT, CLUSTER_LIFECYCLE, DYNAMIC_CLUSTER);

    checkPodDeleted(dynamicServerPodName, domainUid, domainNamespace);
    logger.info("Dynamic cluster shutdown success");

    // stopCluster.sh does not take any action on a stopped cluster
    result = executeLifecycleScript(domainUid, domainNamespace,
        samplePath, STOP_CLUSTER_SCRIPT, CLUSTER_LIFECYCLE, DYNAMIC_CLUSTER);
    assertTrue(result.contains("No changes needed"), "stopCluster.sh shouldn't make changes");

    // check managed server from config cluster are not affected
    Callable<Boolean> isCfgRestarted =
        assertDoesNotThrow(() -> isPodRestarted(configServerPodName,
            domainNamespace, cfgTs));
    assertFalse(assertDoesNotThrow(isCfgRestarted::call),
        "Configured managed server pod must not be restated");

    // Verify clustered server are started after startCluster script execution
    logger.info("Start dynamic cluster using the script");
    executeLifecycleScript(domainUid, domainNamespace, samplePath,
        START_CLUSTER_SCRIPT, CLUSTER_LIFECYCLE, DYNAMIC_CLUSTER);
    checkPodReadyAndServiceExists(dynamicServerPodName,
        domainUid, domainNamespace);
    logger.info("Dynamic cluster restart success");
  }

  /**
   * Verify Always serverStartPolicy (dynamic cluster) overrides replica count.
   * The dynamic cluster has a second managed server(managed-server2)
   * with serverStartPolicy set to IfNeeded. Initially, the server will not
   * come up since the replica count for the cluster is set to 1.
   * Update the ServerStartPolicy for managed-server2 to Always
   * by patching the resource definition with
   *  spec/managedServers/2/serverStartPolicy set to Always.
   * Make sure that managed server managed-server2 is up and running
   * Stop the managed server by patching the resource definition
   *   with spec/managedServers/2/serverStartPolicy set to IfNeeded.
   * Make sure the specified managed server is stopped as per replica count.
   */
  @Order(2)
  @Test
  @DisplayName("Start/stop dynamic cluster managed server by updating serverStartPolicy to Always/IfNeeded")
  void testDynamicClusterStartServerAlways() {
    String serverName = "managed-server2";
    String serverPodName = domainUid + "-" + serverName;

    // Make sure that managed server is not running
    checkPodDeleted(serverPodName, domainUid, domainNamespace);

    assertTrue(patchServerStartPolicy(domainUid, domainNamespace,
        "/spec/managedServers/2/serverStartPolicy", "Always"),
        "Failed to patch dynamic managedServers's serverStartPolicy to Always");
    logger.info("Dynamic managed server is patched to set the serverStartPolicy to Always");
    checkPodReadyAndServiceExists(serverPodName,
        domainUid, domainNamespace);
    logger.info("Second managed server in dynamic cluster is RUNNING");

    // Stop the server by changing the serverStartPolicy to IfNeeded
    assertTrue(patchServerStartPolicy(domainUid, domainNamespace,
        "/spec/managedServers/2/serverStartPolicy", "IfNeeded"),
        "Failed to patch dynamic managedServers's serverStartPolicy to IfNeeded");
    logger.info("Domain resource patched to shutdown the second managed server in dynamic cluster");
    logger.info("Wait for managed server ${0} to be shutdown", serverPodName);
    checkPodDeleted(serverPodName, domainUid, domainNamespace);
    logger.info("Dynamic cluster second managed server shutdown success");
  }

  /**
   * Add the first managed server (managed-server1) in a dynamic
   * cluster with serverStartPolicy IfNeeded.
   * Initially, the server will come up since the replica count is set to 1.
   * (a) Shutdown config-cluster-server1 using the sample script stopServer.sh
   *     with keep_replica_constant option set to true
   *     Make sure that managed server managed-server1 is shutdown.
   *     Make sure that managed server managed-server2 comes up
   *       to maintain the replica count of 1.
   * (b) Restart config-cluster-server1 using the sample script startServer.sh
   *     with keep_replica_constant option set to true
   *     Make sure that managed server managed-server2 is shutdown.
   *     Make sure that managed server managed-server1 comes up
   *       to maintain the replica count of 1.
   */
  @Order(3)
  @Test
  @DisplayName("Stop/Start a running dynamic cluster managed server and verify the replica count ")
  void testDynamicClusterReplicaCountIsMaintained() {
    String serverPodName = domainUid + "-managed-server1";
    String serverPodName2 = domainUid + "-managed-server2";
    String keepReplicaCountConstantParameter = "-k";

    // Make sure that managed server(2) is not running
    checkPodDeleted(serverPodName2, domainUid, domainNamespace);

    // shutdown managed-server1 with keep_replica_constant option
    executeLifecycleScript(domainUid, domainNamespace, samplePath,
        STOP_SERVER_SCRIPT, SERVER_LIFECYCLE, "managed-server1", keepReplicaCountConstantParameter);

    // Make sure maanged-server1 is deleted
    checkPodDeleted(serverPodName, domainUid, domainNamespace);
    checkPodReadyAndServiceExists(serverPodName2, domainUid, domainNamespace);
    logger.info("Dynamic cluster managed server(2) is RUNNING");

    // start managed-server1 with keep_replica_constant option
    executeLifecycleScript(domainUid, domainNamespace, samplePath,
        START_SERVER_SCRIPT, SERVER_LIFECYCLE, "managed-server1", keepReplicaCountConstantParameter);

    // Make sure managed-server2 is deleted
    checkPodDeleted(serverPodName2, domainUid, domainNamespace);

    // Make sure managed-server1 is re-started
    checkPodReadyAndServiceExists(serverPodName, domainUid, domainNamespace);
  }

  /**
   * Make sure the startServer script can start any server (not in order)
   * in a dynamic cluster within the max cluster size limit.
   * Say the max cluster size is 3 and managed-server1 is running.
   * startServer script can start managed-server3 explicitly by skipping
   * managed-server2.
   */
  @Order(4)
  @Test
  @DisplayName("Pick a dynamic cluster managed server randomly within the max cluster size and verify it starts")
  void testStartDynamicClusterServerRandomlyPicked() {
    String serverName = "managed-server3";
    String serverPodName3 = domainUid + "-" + serverName;
    String serverPodName1 = domainUid + "-managed-server1";
    String serverPodName2 = domainUid + "-managed-server2";

    // Make sure that managed server(2) is not running
    checkPodDeleted(serverPodName2, domainUid, domainNamespace);

    // Verify that starting a dynamic cluster managed server within the max cluster size succeeds
    executeLifecycleScript(domainUid, domainNamespace, samplePath, START_SERVER_SCRIPT, SERVER_LIFECYCLE, serverName);

    // Make sure that managed server(1) is still running
    checkPodReadyAndServiceExists(serverPodName1, domainUid, domainNamespace);
    logger.info("Dynamic cluster managed server {0} is still RUNNING", serverPodName1);

    // Verify that a randomly picked dynamic cluster managed server within the max cluster size starts successfully
    checkPodReadyAndServiceExists(serverPodName3, domainUid, domainNamespace);
    logger.info("Randomly picked dynamic cluster managed server {0} is RUNNING", serverPodName3);
  }

  /**
   * Once the admin server is stopped, operator can not start a new managed
   * server from scratch if it has never been started earlier with
   * administration server. Once the administration server is stopped,
   * the managed server can only be started in MSI (managed server independence)
   * mode. To start a managed server in MSI mode, the pre-requisite is that
   * the managed server MUST be started once before administration server is
   * shutdown, so that the security configuration is replicated on the managed
   * server. In this case of MII and DomainInImage model, the server
   * state/configuration  is not saved once the server is shutdown unless we
   * use domain-on-pv model. So in MII case, startServer.sh script update the
   * replica count but the server startup is deferred till we re-start the
   * adminserver. Here the operator tries to start the managed server but it
   * will keep on failing  until administration server is available.
   */
  @Order(5)
  @Test
  @DisplayName("Manage dynamic cluster server in absence of Administration Server")
  void testDynamicServerLifeCycleWithoutAdmin() {
    String serverName = "managed-server1";
    // domainUid + "-" + serverName;
    String serverPodName = managedServerPrefix + "1";
    // Here managed server can be stopped without admin server
    // but can not be started to RUNNING state.

    try {
      // Make sure that managed-server-1 is RUNNING
      checkPodReadyAndServiceExists(serverPodName, domainUid, domainNamespace);
      logger.info("Server Pod [" + serverName + "] is in RUNNING state");

      // shutdown the admin server
      assertTrue(patchServerStartPolicy(domainUid, domainNamespace,
          "/spec/adminServer/serverStartPolicy", "Never"),
          "Failed to patch adminServer's serverStartPolicy to Never");
      logger.info("Domain is patched to shutdown administration server");
      checkPodDeleted(adminServerPodName, domainUid, domainNamespace);
      logger.info("Administration server shutdown success");

      // verify the script can stop the server by reducing replica count
      assertDoesNotThrow(() ->
              executeLifecycleScript(domainUid, domainNamespace, samplePath, STOP_SERVER_SCRIPT,
                  SERVER_LIFECYCLE, serverName, "", true),
          String.format("Failed to run %s", STOP_SERVER_SCRIPT));
      checkPodDeleted(serverPodName, domainUid, domainNamespace);
      logger.info("Shutdown [" + serverName + "] without admin server success");

      // Here the script increase the replica count by 1, but operator cannot
      // start server in MSI mode as the server state (configuration) is
      // lost while stopping the server in mii model.

      assertDoesNotThrow(() ->
              executeLifecycleScript(domainUid, domainNamespace, samplePath,
                  START_SERVER_SCRIPT, SERVER_LIFECYCLE, serverName, "", true),
          String.format("Failed to run %s", START_SERVER_SCRIPT));
      logger.info("Replica count increased without admin server");

      // Check if pod in init state
      // Here the server pod is created but does not goes into 1/1 state
      checkPodInitialized(serverPodName, domainUid, domainNamespace);
      logger.info("Server[" + serverName + "] pod is initialized");

      // (re)Start Start the admin
      assertTrue(patchServerStartPolicy(domainUid, domainNamespace,
          "/spec/adminServer/serverStartPolicy", "IfNeeded"),
          "Failed to patch adminServer's serverStartPolicy to IfNeeded");
      checkPodReadyAndServiceExists(with().pollDelay(2, SECONDS)
          .and().with().pollInterval(10, SECONDS)
          .atMost(10, MINUTES).await(),
          adminServerPodName, domainUid, domainNamespace);
      logger.info("administration server restart success");

      checkPodReadyAndServiceExists(with().pollDelay(2, SECONDS)
          .and().with().pollInterval(10, SECONDS)
          .atMost(10, MINUTES).await(), serverPodName, domainUid, domainNamespace);
      logger.info("(re)Started [" + serverName + "] on admin server restart");
    } finally {
      // restart admin server
      assertTrue(patchServerStartPolicy(domainUid, domainNamespace,
          "/spec/adminServer/serverStartPolicy", "IfNeeded"),
          "Failed to patch adminServer's serverStartPolicy to IfNeeded");
      logger.info("Check admin service/pod {0} is created in namespace {1}",
          adminServerPodName, domainNamespace);
      checkPodReadyAndServiceExists(with().pollDelay(2, SECONDS)
          .and().with().pollInterval(10, SECONDS)
          .atMost(10, MINUTES).await(), adminServerPodName, domainUid, domainNamespace);
    }
  }

  /**
   * Restart the clustered managed server that is part of the dynamic cluster using the sample scripts
   * stopServer.sh and startServer.sh while keeping the replica count constant.
   * The test case verifies that scripts work when serverStartPolicy is not set at the managed server level.
   */
  @Order(6)
  @Test
  @DisplayName("Restart the dynamic cluster managed server using sample scripts with constant replica count")
  void testRestartingMSWhileKeepingReplicaConstant() {
    String serverName = managedServerNamePrefix + 1;
    String serverPodName = managedServerPrefix + 1;
    String keepReplicasConstant = "-k";

    // shut down the dynamic cluster managed server using the script stopServer.sh and keep replicas constant
    executeLifecycleScript(domainUid, domainNamespace, samplePath,
        STOP_SERVER_SCRIPT, SERVER_LIFECYCLE, serverName, keepReplicasConstant);
    checkPodDeleted(serverPodName, domainUid, domainNamespace);
    logger.info("managed server " + serverName + " stopped successfully.");

    // start the dynamic cluster managed server using the script startServer.sh and keep replicas constant
    executeLifecycleScript(domainUid, domainNamespace, samplePath,
        START_SERVER_SCRIPT, SERVER_LIFECYCLE, serverName, keepReplicasConstant);
    checkPodReadyAndServiceExists(serverPodName, domainUid, domainNamespace);
    logger.info("managed server " + serverName + " restarted successfully.");
  }

  /**
   * Restart the clustered managed server that is part of the dynamic cluster using the sample scripts
   * stopServer.sh and startServer.sh along with changing the replica count.
   * The test case verifies that scripts work when serverStartPolicy is not set at the managed server level.
   */
  @Order(7)
  @Test
  @DisplayName("Restart the dynamic cluster managed server using sample scripts with varying replica count")
  void testRestartingMSWhileVaryingReplicaCount() {
    String serverName = managedServerNamePrefix + 1;
    String serverPodName = managedServerPrefix + 1;
    String keepReplicasConstant = "-k";

    // shut down managed server3 using the script stopServer.sh and keep replicas constant
    executeLifecycleScript(domainUid, domainNamespace, samplePath, STOP_SERVER_SCRIPT, SERVER_LIFECYCLE,
        managedServerNamePrefix + 3, keepReplicasConstant);
    checkPodDeleted(managedServerPrefix + 3, domainUid, domainNamespace);

    // shut down the dynamic cluster managed server using the script stopServer.sh and let replicas decrease
    executeLifecycleScript(domainUid, domainNamespace, samplePath,
        STOP_SERVER_SCRIPT, SERVER_LIFECYCLE, serverName);
    checkPodDeleted(serverPodName, domainUid, domainNamespace);
    assertDoesNotThrow(() -> assertTrue(checkClusterReplicaCountMatches(dynamicClusterResourceName,
        domainNamespace, 1)));
    logger.info("managed server " + serverName + " stopped successfully.");

    // start the dynamic cluster managed server using the script startServer.sh and let replicas increase
    executeLifecycleScript(domainUid, domainNamespace, samplePath,
        START_SERVER_SCRIPT, SERVER_LIFECYCLE, serverName);
    checkPodReadyAndServiceExists(serverPodName, domainUid, domainNamespace);
    assertDoesNotThrow(() -> assertTrue(checkClusterReplicaCountMatches(dynamicClusterResourceName,
        domainNamespace, 2)));
    logger.info("managed server " + serverName + " restarted successfully.");
  }

  /**
   * Rolling restart the dynamic cluster using the sample script rollCluster.sh script
   * Verify that server(s) in the dynamic cluster are restarted and in RUNNING state.
   * Verify that server(s) in the configured cluster are not affected.
   */
  @Order(8)
  @Test
  @DisplayName("Rolling restart the dynamic cluster with rollCluster.sh script")
  void testDynamicClusterRollingRestart() {
    String dynamicServerName = "managed-server1";
    String dynamicServerPodName = domainUid + "-" + dynamicServerName;
    String configServerPodName = domainUid + "-config-cluster-server1";

    // restore the env
    restoreEnv(domainUid, domainNamespace, samplePath);

    // get the creation time of the configured and dynamic server pod before patching
    OffsetDateTime dynServerPodCreationTime =
        assertDoesNotThrow(() -> getPodCreationTimestamp(domainNamespace, "", dynamicServerPodName),
            String.format("Failed to get creationTimestamp for pod %s", dynamicServerPodName));
    OffsetDateTime configServerPodCreationTime =
        assertDoesNotThrow(() -> getPodCreationTimestamp(domainNamespace, "", configServerPodName),
            String.format("Failed to get creationTimestamp for pod %s", configServerPodName));

    // use rollCluster.sh to rolling-restart a dynamic cluster
    logger.info("Rolling restart the dynamic cluster with rollCluster.sh script");
    assertDoesNotThrow(() ->
            executeLifecycleScript(domainUid, domainNamespace, samplePath,
                ROLLING_CLUSTER_SCRIPT, CLUSTER_LIFECYCLE, DYNAMIC_CLUSTER),
        String.format("Failed to run %s", ROLLING_CLUSTER_SCRIPT));

    // wait till rolling restart has started by checking managed server pods have restarted
    logger.info("Waiting for rolling restart to start by checking {0} pod is restarted in namespace {0}",
        dynamicServerPodName, domainNamespace);
    checkPodRestarted(domainUid, domainNamespace, dynamicServerPodName, dynServerPodCreationTime);

    // check managed server from config cluster are not affected
    logger.info("Check configured managed server pods are not affected");
    assertDoesNotThrow(() -> assertTrue(checkClusterReplicaCountMatches(configuredClusterResourceName,
        domainNamespace, replicaCount)));

    boolean isPodRestarted =
        assertDoesNotThrow(() -> checkIsPodRestarted(domainNamespace,
            configServerPodName, configServerPodCreationTime).call().booleanValue(),
            String.format("pod %s should not been restarted in namespace %s",
                configServerPodName, domainNamespace));

    assertFalse(isPodRestarted,
        String.format("configured server %s shouldn't be rolling-restarted", configServerPodName));
  }

  /**
   * Scale the dynamic cluster using the sample script scaleCluster.sh script
   * Verify that server(s) in the dynamic cluster are scaled up and in RUNNING state.
   * Verify that server(s) in the configured cluster are not affected.
   * Restore the env using the sample script stopServer.sh.
   */
  @Order(9)
  @Test
  @DisplayName("Scale the dynamic cluster with scaleCluster.sh script")
  void testDynamicClusterScale() {
    int newReplicaCount = 2;
    String dynamicServerName = "managed-server" + newReplicaCount;
    String dynamicServerPodName = domainUid + "-" + dynamicServerName;
    String configServerPodName = domainUid + "-config-cluster-server" + newReplicaCount;

    // use clusterStatus.sh to make sure the server-to-be-test doesn't exist
    // String regex matches below
    // cluster        status      available  min  max  goal  current  ready
    // clusterName    Completed   True       0    5    1     1      1
    String regex =
        ".*" + DYNAMIC_CLUSTER + "(\\s+)Completed(\\s+)True(\\s+)0(\\s+)5(\\s+)1(\\s+)1(\\s+)1";
    scalingClusters(domainUid, domainNamespace, DYNAMIC_CLUSTER,
        dynamicServerPodName, replicaCount, regex, false, samplePath);
    // String regex matches below
    // cluster        status      available  min  max  goal  current  ready
    // clusterName    Completed   True       0    2    1     1      1
    regex = ".*" + CONFIG_CLUSTER + "(\\s+)Completed(\\s+)True(\\s+)0(\\s+)2(\\s+)1(\\s+)1(\\s+)1";
    scalingClusters(domainUid, domainNamespace, configuredClusterResourceName, configServerPodName,
        replicaCount, regex, false, samplePath);

    // use scaleCluster.sh to scale a dynamic cluster and
    // use clusterStatus.sh to verify scaling results
    // String regex matches below
    // cluster        status      available  min  max  goal  current  ready
    // clusterName    Completed   True       0    5    2       2      2
    regex = ".*" + DYNAMIC_CLUSTER + "(\\s+)Completed(\\s+)True(\\s+)0(\\s+)5(\\s+)2(\\s+)2(\\s+)2";
    scalingClusters(domainUid, domainNamespace, DYNAMIC_CLUSTER,
        dynamicServerPodName, newReplicaCount, regex, true, samplePath);

    // check managed server from config cluster are not affected
    logger.info("Check configured managed server pods are not affected");
    assertDoesNotThrow(() -> assertTrue(checkClusterReplicaCountMatches(configuredClusterResourceName,
        domainNamespace, replicaCount)));
    checkPodDoesNotExist(configServerPodName, domainUid, domainNamespace);

    // use clusterStatus.sh to restore test env
    // String regex matches below
    // cluster        status      available  min  max  goal  current  ready
    // clusterName    Completed   True       0    5    1     1      1
    regex = ".*" + DYNAMIC_CLUSTER + "(\\s+)Completed(\\s+)True(\\s+)0(\\s+)5(\\s+)1(\\s+)1(\\s+)1";
    scalingClusters(domainUid, domainNamespace,DYNAMIC_CLUSTER, dynamicServerPodName,
        replicaCount, regex, false, samplePath);
  }

  /**
   * Verify the sample script reports proper error when a cluster is scaled beyond max cluster size.
   */
  @Order(10)
  @Test
  @DisplayName("verify the sample script fails when a cluster is scaled beyond max cluster size")
  void testScaleBeyondMaxClusterSize() {
    int newReplicaCount = 7;
    String expectedResult = "Replicas value is not in the allowed range";
    // use scaleCluster.sh to scale a given cluster
    logger.info("Scale cluster {0} using the script scaleCluster.sh", DYNAMIC_CLUSTER);
    String result =  assertDoesNotThrow(() ->
            executeLifecycleScript(domainUid, domainNamespace, samplePath,
                SCALE_CLUSTER_SCRIPT, CLUSTER_LIFECYCLE, DYNAMIC_CLUSTER, " -r " + newReplicaCount, false),
        String.format("Failed to run %s", SCALE_CLUSTER_SCRIPT));
    assertTrue(result.contains(expectedResult), "Expected result " + expectedResult + "not returned");
    // verify the replica did not change
    assertDoesNotThrow(() -> assertTrue(checkClusterReplicaCountMatches(dynamicClusterResourceName,
        domainNamespace, replicaCount)));
  }
}
