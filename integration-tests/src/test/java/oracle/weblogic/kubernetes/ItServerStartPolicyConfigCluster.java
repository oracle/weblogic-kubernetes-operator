// Copyright (c) 2021, 2024, Oracle and/or its affiliates.
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

import static oracle.weblogic.kubernetes.actions.TestActions.getPodCreationTimestamp;
import static oracle.weblogic.kubernetes.assertions.TestAssertions.isPodRestarted;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.checkClusterReplicaCountMatches;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.checkPodReadyAndServiceExists;
import static oracle.weblogic.kubernetes.utils.PatchDomainUtils.patchServerStartPolicy;
import static oracle.weblogic.kubernetes.utils.PodUtils.checkIsPodRestarted;
import static oracle.weblogic.kubernetes.utils.PodUtils.checkPodDeleted;
import static oracle.weblogic.kubernetes.utils.PodUtils.checkPodDoesNotExist;
import static oracle.weblogic.kubernetes.utils.PodUtils.checkPodRestarted;
import static oracle.weblogic.kubernetes.utils.PodUtils.getPodCreationTime;
import static oracle.weblogic.kubernetes.utils.ServerStartPolicyUtils.CLUSTER_LIFECYCLE;
import static oracle.weblogic.kubernetes.utils.ServerStartPolicyUtils.CONFIG_CLUSTER;
import static oracle.weblogic.kubernetes.utils.ServerStartPolicyUtils.DOMAIN;
import static oracle.weblogic.kubernetes.utils.ServerStartPolicyUtils.DYNAMIC_CLUSTER;
import static oracle.weblogic.kubernetes.utils.ServerStartPolicyUtils.ROLLING_CLUSTER_SCRIPT;
import static oracle.weblogic.kubernetes.utils.ServerStartPolicyUtils.ROLLING_DOMAIN_SCRIPT;
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
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests to verify that life cycle operation of configured cluster does not impact the state of dynamic cluster.
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DisplayName("ServerStartPolicy attribute in different levels in a MII domain")
@IntegrationTest
@Tag("olcne-mrg")
@Tag("kind-parallel")
@Tag("okd-wls-mrg")
@Tag("oke-arm")
@Tag("oke-parallel")
class ItServerStartPolicyConfigCluster {

  private static String domainNamespace = null;
  private static String opNamespace = null;

  private static final int replicaCount = 1;
  private static final String domainUid = "mii-start-policy";

  private static final String adminServerPodName = domainUid + "-admin-server";
  private static final String managedServerPrefix = domainUid + "-" + managedServerNamePrefix;
  private static final String clusterResourceName = DYNAMIC_CLUSTER;
  private static LoggingFacade logger = null;
  private static final String samplePath = "sample-testing-config-cluster";

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
   * Stop the configured cluster using the sample script stopCluster.sh
   * Verify that server(s) in the configured cluster are stopped. 
   * Verify that server(s) in the dynamic cluster are in RUNNING state. 
   * Restart the cluster using the sample script startCluster.sh
   * Make sure that servers in the configured cluster are in RUNNING state. 
   * The usecase also verify the scripts startCluster.sh/stopCluster.sh make 
   * no changes in a running/stopped cluster respectively.
   */
  @Order(1)
  @Test
  @DisplayName("Restart the configured cluster with serverStartPolicy")
  void testConfigClusterRestart() {

    String configServerPodName = domainUid + "-config-cluster-server1";
    String dynamicServerPodName = domainUid + "-managed-server1";

    OffsetDateTime dynTs = getPodCreationTime(domainNamespace, dynamicServerPodName);

    checkPodReadyAndServiceExists(configServerPodName, 
              domainUid, domainNamespace);
    // startCluster.sh does not take any action on a running cluster
    String result = executeLifecycleScript(domainUid, domainNamespace,
        samplePath,START_CLUSTER_SCRIPT, CLUSTER_LIFECYCLE, CONFIG_CLUSTER);
    assertTrue(result.contains("No changes needed"), "startCluster.sh shouldn't make changes");

    // Verify config servers are shutdown after stopCluster script execution
    logger.info("Stop configured cluster using the script");
    executeLifecycleScript(domainUid, domainNamespace, samplePath,
        STOP_CLUSTER_SCRIPT, CLUSTER_LIFECYCLE, CONFIG_CLUSTER);

    checkPodDeleted(configServerPodName, domainUid, domainNamespace);
    logger.info("configured cluster shutdown success");

    // check managed server from dynamic cluster are not affected
    logger.info("Check dynamic managed server pods are not affected");
    
    Callable<Boolean> isDynRestarted = 
         assertDoesNotThrow(() -> isPodRestarted(dynamicServerPodName, 
         domainNamespace, dynTs));
    assertFalse(assertDoesNotThrow(isDynRestarted::call),
         "Dynamic managed server pod must not be restated");

    // stopCluster.sh does not take any action on a stopped cluster
    result = executeLifecycleScript(domainUid, domainNamespace,
        samplePath,STOP_CLUSTER_SCRIPT, CLUSTER_LIFECYCLE, CONFIG_CLUSTER);
    assertTrue(result.contains("No changes needed"), "stopCluster.sh shouldn't make changes");
    // Verify dynamic server are started after startCluster script execution
    logger.info("Start configured cluster using the script");
    executeLifecycleScript(domainUid, domainNamespace,
        samplePath,START_CLUSTER_SCRIPT, CLUSTER_LIFECYCLE, CONFIG_CLUSTER);
    checkPodReadyAndServiceExists(configServerPodName, 
              domainUid, domainNamespace);
    logger.info("Configured cluster restart success");
  }

  /**
   * Verify Always serverStartPolicy (configured cluster) overrides replica count.
   * The configured cluster has a second managed server(config-cluster-server2)
   * with serverStartPolicy set to IfNeeded. Initially, the server will not
   * come up since the replica count for the cluster is set to 1. 
   * Update the serverStartPolicy for the server config-cluster-server2 to 
   * Always by patching the resource definition with
   *  spec/managedServers/1/serverStartPolicy set to Always
   * Make sure that managed server config-cluster-server2 is up and running
   * Stop the managed server by patching the resource definition 
   *   with spec/managedServers/1/serverStartPolicy set to IfNeeded.
   * Make sure the specified managed server is stopped as per replica count.
   */
  @Order(2)
  @Test
  @DisplayName("Start/stop configured cluster managed server by updating serverStartPolicy to Always/IfNeeded")
  void testConfigClusterStartServerAlways() {
    String serverName = "config-cluster-server2";
    String serverPodName = domainUid + "-" + serverName;

    // Make sure that managed server is not running 
    checkPodDeleted(serverPodName, domainUid, domainNamespace);
    assertTrue(patchServerStartPolicy(domainUid, domainNamespace,
         "/spec/managedServers/1/serverStartPolicy", "Always"),
         "Failed to patch config managedServers's serverStartPolicy to Always");
    logger.info("Configured managed server is patched to set the serverStartPolicy to Always");
    checkPodReadyAndServiceExists(serverPodName, 
          domainUid, domainNamespace);
    logger.info("Configured cluster managed server is RUNNING");

    // Stop the server by changing the serverStartPolicy to IfNeeded
    assertTrue(patchServerStartPolicy(domainUid, domainNamespace,
         "/spec/managedServers/1/serverStartPolicy", "IfNeeded"),
         "Failed to patch config managedServers's serverStartPolicy to IfNeeded");
    logger.info("Domain resource patched to shutdown the second managed server in configured cluster");
    logger.info("Wait for managed server ${0} to be shutdown", serverPodName);
    checkPodDeleted(serverPodName, domainUid, domainNamespace);
    logger.info("configured cluster managed server shutdown success");
  }


  /**
   * Add the first managed server (config-cluster-server1) in a configured 
   * cluster with serverStartPolicy IfNeeded.
   * Initially, the server will come up since the replica count is set to 1.
   * (a) Shutdown config-cluster-server1 using the sample script stopServer.sh
   *     with keep_replica_constant option set to true
   *     Make sure that managed server config-cluster-server1 is shutdown.
   *     Make sure that managed server config-cluster-server2 comes up
   *       to maintain the replica count of 1.
   * (b) Restart config-cluster-server1 using the sample script startServer.sh
   *     with keep_replica_constant option set to true
   *     Make sure that managed server config-cluster-server2 is shutdown.
   *     Make sure that managed server config-cluster-server1 comes up
   *       to maintain the replica count of 1.
   */
  @Order(3)
  @Test
  @DisplayName("Stop/Start a running configured cluster managed server and verify the replica count is maintained")
  void testConfigClusterReplicaCountIsMaintained() {
    String serverName = "config-cluster-server1";
    String serverPodName = domainUid + "-" + serverName;
    String serverPodName2 = domainUid + "-config-cluster-server2";
    String keepReplicaCountConstantParameter = "-k";

    // Make sure that managed server(2) is not running 
    checkPodDeleted(serverPodName2, domainUid, domainNamespace);

    // shutdown config-cluster-server1 with keep_replica_constant option
    executeLifecycleScript(domainUid, domainNamespace, samplePath,
        STOP_SERVER_SCRIPT, SERVER_LIFECYCLE, serverName, keepReplicaCountConstantParameter);

    // Make sure config-cluster-server1 is deleted 
    checkPodDeleted(serverPodName, domainUid, domainNamespace);
    // Make sure  config-cluster-server2 is started 
    checkPodReadyAndServiceExists(serverPodName2, domainUid, domainNamespace);
    logger.info("Configured cluster managed Server(2) is RUNNING");

    // start config-cluster-server1 with keep_replica_constant option
    executeLifecycleScript(domainUid, domainNamespace, samplePath,
        START_SERVER_SCRIPT, SERVER_LIFECYCLE, serverName, keepReplicaCountConstantParameter);

    // Make sure config-cluster-server2 is deleted 
    checkPodDeleted(serverPodName2, domainUid, domainNamespace);

    // Make sure config-cluster-server1 is re-started
    checkPodReadyAndServiceExists(serverPodName, domainUid, domainNamespace);
  }

  /**
   * Rolling restart the configured cluster using the sample script rollCluster.sh script
   * Verify that server(s) in the configured cluster are restarted and in RUNNING state.
   * Verify that server(s) in the dynamic cluster are not affected.
   */
  @Order(4)
  @Test
  @DisplayName("Rolling restart the configured cluster with rollCluster.sh script")
  void testConfigClusterRollingRestart() {
    String configServerName = "config-cluster-server1";
    String configServerPodName = domainUid + "-" + configServerName;
    String dynamicServerPodName = domainUid + "-managed-server1";

    // restore the env
    restoreEnv(domainUid, domainNamespace, samplePath);

    // get the creation time of the configured and dynamic server pod before patching
    OffsetDateTime configServerPodCreationTime =
        assertDoesNotThrow(() -> getPodCreationTimestamp(domainNamespace, "", configServerPodName),
        String.format("Failed to get creationTimestamp for pod %s", configServerPodName));
    OffsetDateTime dynServerPodCreationTime =
        assertDoesNotThrow(() -> getPodCreationTimestamp(domainNamespace, "", dynamicServerPodName),
        String.format("Failed to get creationTimestamp for pod %s", dynamicServerPodName));

    // use rollCluster.sh to rolling-restart a configured cluster
    logger.info("Rolling restart the configured cluster with rollCluster.sh script");
    String result =  assertDoesNotThrow(() ->
        executeLifecycleScript(domainUid, domainNamespace, samplePath,
            ROLLING_CLUSTER_SCRIPT, CLUSTER_LIFECYCLE, CONFIG_CLUSTER),
        String.format("Failed to run %s", ROLLING_CLUSTER_SCRIPT));

    // wait till rolling restart has started by checking managed server pods have restarted
    logger.info("Waiting for rolling restart to start by checking {0} pod is restarted in namespace {0}",
        configServerPodName, domainNamespace);
    checkPodRestarted(domainUid, domainNamespace, configServerPodName, configServerPodCreationTime);

    // check managed server from dynamic cluster are not affected
    logger.info("Check dynamic managed server pods are not affected");
    assertDoesNotThrow(() -> assertTrue(checkClusterReplicaCountMatches(clusterResourceName,
        domainNamespace, replicaCount)));

    boolean isPodRestarted =
        assertDoesNotThrow(() -> checkIsPodRestarted(domainNamespace,
        dynamicServerPodName, dynServerPodCreationTime).call().booleanValue(),
        String.format("pod %s should not been restarted in namespace %s",
            dynamicServerPodName, domainNamespace));

    assertFalse(isPodRestarted,
        String.format("dynamic server %s shouldn't be rolling-restarted", dynamicServerPodName));
  }

  /**
   * Rolling restart the domain using the sample script rollDomain.sh script
   * Verify that server(s) in the domain is restarted and all servers are in RUNNING state.
   */
  @Order(5)
  @Test
  @DisplayName("Rolling restart the domain with rollDomain.shscript")
  void testConfigDomainRollingRestart() {
    String configServerName = "config-cluster-server1";
    String dynamicServerName = "managed-server1";
    String configServerPodName = domainUid + "-" + configServerName;
    String dynamicServerPodName = domainUid + "-" + dynamicServerName;

    // restore the env
    restoreEnv(domainUid, domainNamespace, samplePath);

    // get the creation time of the configured and dynamic server pod before patching
    OffsetDateTime configServerPodCreationTime =
        assertDoesNotThrow(() -> getPodCreationTimestamp(domainNamespace, "", configServerPodName),
        String.format("Failed to get creationTimestamp for pod %s", configServerPodName));
    OffsetDateTime dynServerPodCreationTime =
        assertDoesNotThrow(() -> getPodCreationTimestamp(domainNamespace, "", dynamicServerPodName),
        String.format("Failed to get creationTimestamp for pod %s", dynamicServerPodName));

    // use rollDomain.sh to rolling-restart a configured cluster
    logger.info("Rolling restart the domain with rollDomain.sh script");
    String result =  assertDoesNotThrow(() ->
        executeLifecycleScript(domainUid, domainNamespace, samplePath,ROLLING_DOMAIN_SCRIPT, DOMAIN, ""),
        String.format("Failed to run %s", ROLLING_DOMAIN_SCRIPT));

    // wait till rolling restart has started by checking managed server pods have restarted
    logger.info("Waiting for rolling restart to start by checking {0} pod is restarted in namespace {0}",
        configServerPodName, domainNamespace);
    checkPodRestarted(domainUid, domainNamespace, configServerPodName, configServerPodCreationTime);

    logger.info("Waiting for rolling restart to start by checking {0} pod is restarted in namespace {0}",
        dynamicServerPodName, domainNamespace);
    checkPodRestarted(domainUid, domainNamespace, dynamicServerPodName, dynServerPodCreationTime);
  }

  /**
   * Scale the configured cluster using the sample script scaleCluster.sh script
   * Verify that server(s) in the configured cluster are scaled up and in RUNNING state.
   * Verify that server(s) in the dynamic cluster are not affected.
   * Restore the env using the sample script stopServer.sh.
   */
  @Order(6)
  @Test
  @DisplayName("Scale the configured cluster with scaleCluster.sh script")
  void testConfigClusterScale() {
    int newReplicaCount = 2;
    String configServerName = "config-cluster-server" + newReplicaCount;
    String configServerPodName = domainUid + "-" + configServerName;
    String dynamicServerPodName = domainUid + "-managed-server" + newReplicaCount;

    // use clusterStatus.sh to make sure the server-to-be-test doesn't exist
    // String regex matches below
    // cluster        status      available  min  max  goal  current  ready
    // clusterName    Completed   True       0    5    1      1       1
    String regex =
        ".*" + DYNAMIC_CLUSTER + "(\\s+)Completed(\\s+)True(\\s+)0(\\s+)5(\\s+)1(\\s+)1(\\s+)1";
    scalingClusters(domainUid, domainNamespace, DYNAMIC_CLUSTER, dynamicServerPodName,
        replicaCount, regex, false, samplePath);
    // String regex matches below
    // cluster        status      available  min  max  goal  current  ready
    // clusterName    Completed   True       0    2    1      1       1
    regex = ".*" + CONFIG_CLUSTER + "(\\s+)Completed(\\s+)True(\\s+)0(\\s+)2(\\s+)1(\\s+)1(\\s+)1";
    scalingClusters(domainUid, domainNamespace, CONFIG_CLUSTER, configServerPodName,
        replicaCount, regex, false, samplePath);

    // use scaleCluster.sh to scale a dynamic cluster and
    // use clusterStatus.sh to verify scaling results
    // String regex matches below
    // cluster        status      available  min  max  goal  current  ready
    // clusterName    Completed   True       0    2    2       2      2
    regex = ".*" + CONFIG_CLUSTER + "(\\s+)Completed(\\s+)True(\\s+)0(\\s+)2(\\s+)2(\\s+)2(\\s+)2";
    scalingClusters(domainUid, domainNamespace, CONFIG_CLUSTER,
        configServerPodName, newReplicaCount, regex, true, samplePath);

    // check managed server from dynamic cluster are not affected
    logger.info("Check dynamic managed server pods are not affected");
    assertDoesNotThrow(() -> assertTrue(checkClusterReplicaCountMatches(clusterResourceName,
        domainNamespace, replicaCount)));
    checkPodDoesNotExist(dynamicServerPodName, domainUid, domainNamespace);

    // use clusterStatus.sh to restore test env
    // String regex matches below
    // cluster        status      available  min  max  goal  current  ready
    // clusterName    Completed   True       0    2    1      1       1
    regex = ".*" + CONFIG_CLUSTER + "(\\s+)Completed(\\s+)True(\\s+)0(\\s+)2(\\s+)1(\\s+)1(\\s+)1";
    scalingClusters(domainUid, domainNamespace, CONFIG_CLUSTER, configServerPodName,
        replicaCount, regex, false, samplePath);
  }
}
