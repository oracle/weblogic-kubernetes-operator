// Copyright (c) 2020, 2021, Oracle and/or its affiliates.
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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import static oracle.weblogic.kubernetes.TestConstants.OPERATOR_RELEASE_NAME;
import static oracle.weblogic.kubernetes.actions.TestActions.getOperatorPodName;
import static oracle.weblogic.kubernetes.actions.TestActions.getPodLog;
import static oracle.weblogic.kubernetes.assertions.TestAssertions.isPodRestarted;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.checkPodReadyAndServiceExists;
import static oracle.weblogic.kubernetes.utils.OKDUtils.createRouteForOKD;
import static oracle.weblogic.kubernetes.utils.PatchDomainUtils.patchServerStartPolicy;
import static oracle.weblogic.kubernetes.utils.PodUtils.checkPodDeleted;
import static oracle.weblogic.kubernetes.utils.PodUtils.checkPodInitialized;
import static oracle.weblogic.kubernetes.utils.PodUtils.getPodCreationTime;
import static oracle.weblogic.kubernetes.utils.ServerStartPolicyUtils.CLUSTER_LIFECYCLE;
import static oracle.weblogic.kubernetes.utils.ServerStartPolicyUtils.DOMAIN;
import static oracle.weblogic.kubernetes.utils.ServerStartPolicyUtils.SERVER_LIFECYCLE;
import static oracle.weblogic.kubernetes.utils.ServerStartPolicyUtils.START_CLUSTER_SCRIPT;
import static oracle.weblogic.kubernetes.utils.ServerStartPolicyUtils.START_DOMAIN_SCRIPT;
import static oracle.weblogic.kubernetes.utils.ServerStartPolicyUtils.START_SERVER_SCRIPT;
import static oracle.weblogic.kubernetes.utils.ServerStartPolicyUtils.STOP_CLUSTER_SCRIPT;
import static oracle.weblogic.kubernetes.utils.ServerStartPolicyUtils.STOP_DOMAIN_SCRIPT;
import static oracle.weblogic.kubernetes.utils.ServerStartPolicyUtils.STOP_SERVER_SCRIPT;
import static oracle.weblogic.kubernetes.utils.ServerStartPolicyUtils.checkManagedServerConfiguration;
import static oracle.weblogic.kubernetes.utils.ServerStartPolicyUtils.executeLifecycleScript;
import static oracle.weblogic.kubernetes.utils.ServerStartPolicyUtils.managedServerNamePrefix;
import static oracle.weblogic.kubernetes.utils.ServerStartPolicyUtils.prepare;
import static oracle.weblogic.kubernetes.utils.ServerStartPolicyUtils.verifyExecuteResult;
import static oracle.weblogic.kubernetes.utils.ThreadSafeLogger.getLogger;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests to verify that life cycle operation for standalone servers and domain.
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DisplayName("ServerStartPolicy attribute in different levels in a MII domain")
@IntegrationTest
class ItServerStartPolicy {

  private static String domainNamespace = null;
  private static String opNamespace = null;

  private static final int replicaCount = 1;
  private static final String domainUid = "mii-start-policy";

  private static final String adminServerPodName = domainUid + "-admin-server";
  private final String managedServerPrefix = domainUid + "-" + managedServerNamePrefix;
  private static LoggingFacade logger = null;
  private static String samplePath = "sample-testing";
  private static String ingressHost = null; //only used for OKD

  /**
   * Install Operator.
   * Create a domain resource definition.
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
        checkManagedServerConfiguration(ingressHost, "config-cluster-server1", domainNamespace, adminServerPodName);
    assertTrue(isServerConfigured,
        "Could not find managed server from configured cluster");
    logger.info("Found managed server from configured cluster");

    // Check standalone server configuration is available
    boolean isStandaloneServerConfigured =
        checkManagedServerConfiguration(ingressHost, "standalone-managed", domainNamespace, adminServerPodName);
    assertTrue(isStandaloneServerConfigured,
        "Could not find standalone managed server from configured cluster");
    logger.info("Found standalone managed server configuration");
  }

  /**
   * Verify the script stopServer.sh can not stop a server below the minimum
   * DynamicServer count when allowReplicasBelowMinDynClusterSize is false.
   * In the current domain configuration the minimum replica count is 1.
   * The managed-server1 is up and running.
   * Shutdown the managed-server1 using the script stopServer.sh.
   * managed-server1 is shutdown and managed-server2 comes up to mantain the
   * minimum replica count.
   */
  @Order(0)
  @Test
  @DisplayName("Stop a server below Limit")
  void testStopManagedServerBeyondMinClusterLimit() {
    String serverPodName = domainUid + "-managed-server1";
    String serverPodName2 = domainUid + "-managed-server2";

    // shutdown managed-server1 with keep_replica_constant option not set
    // This operator MUST fail as the MinDynamicCluster size is 1
    // and allowReplicasBelowMinDynClusterSize is false

    String regex = "it is at its minimum";
    String result =  assertDoesNotThrow(() ->
            executeLifecycleScript(domainUid, domainNamespace, samplePath,
                STOP_SERVER_SCRIPT, SERVER_LIFECYCLE, "managed-server1", "", false),
        String.format("Failed to run %s", STOP_CLUSTER_SCRIPT));
    assertTrue(verifyExecuteResult(result, regex),"The script shouldn't stop a server to go below Minimum");

    // Make sure managed-server1 is deleted
    checkPodDeleted(serverPodName, domainUid, domainNamespace);
    // Make sure managed-server2 is provisioned to mantain the replica count
    checkPodReadyAndServiceExists(serverPodName2, domainUid, domainNamespace);

    // start managed-server1 with keep_replica_constant option
    // to bring the domain to original configuation with only managed-server1
    executeLifecycleScript(domainUid, domainNamespace, samplePath,
        START_SERVER_SCRIPT, SERVER_LIFECYCLE, "managed-server1", "-k");
    checkPodDeleted(serverPodName2, domainUid, domainNamespace);
    checkPodReadyAndServiceExists(serverPodName, domainUid, domainNamespace);

  }

  /**
   * Stop the Administration server by using stopServer.sh sample script.
   * Make sure that Only the Administration server is stopped.
   * Restart the Administration server by using startServer.sh sample script.
   * Make sure that the Administration server is in RUNNING state.
   */
  @Order(1)
  @Test
  @DisplayName("Restart the Administration server with serverStartPolicy")
  void testAdminServerRestart() {

    String configServerPodName = domainUid + "-config-cluster-server1";
    String dynamicServerPodName = domainUid + "-managed-server1";

    OffsetDateTime dynTs = getPodCreationTime(domainNamespace, dynamicServerPodName);
    OffsetDateTime cfgTs = getPodCreationTime(domainNamespace, configServerPodName);

    // verify that the sample script can shutdown admin server
    executeLifecycleScript(domainUid, domainNamespace, samplePath,STOP_SERVER_SCRIPT,
        SERVER_LIFECYCLE, "admin-server", "", true);
    checkPodDeleted(adminServerPodName, domainUid, domainNamespace);
    logger.info("Administration server shutdown success");

    logger.info("Check managed server pods are not affected");
    Callable<Boolean> isDynRestarted =
        assertDoesNotThrow(() -> isPodRestarted(dynamicServerPodName,
            domainNamespace, dynTs));
    assertFalse(assertDoesNotThrow(isDynRestarted::call),
        "Dynamic managed server pod must not be restated");

    Callable<Boolean> isCfgRestarted =
        assertDoesNotThrow(() -> isPodRestarted(configServerPodName,
            domainNamespace, cfgTs));
    assertFalse(assertDoesNotThrow(isCfgRestarted::call),
        "Configured managed server pod must not be restated");

    // verify that the sample script can start admin server
    executeLifecycleScript(domainUid, domainNamespace, samplePath,
        START_SERVER_SCRIPT, SERVER_LIFECYCLE, "admin-server", "", true);
    logger.info("Check admin service/pod {0} is created in namespace {1}",
        adminServerPodName, domainNamespace);
    checkPodReadyAndServiceExists(adminServerPodName,
        domainUid, domainNamespace);
  }

  /**
   * Stop the entire domain using the sample script stopDomain.sh
   * Make sure that all servers in the domain are stopped.
   * Restart the domain by patching the resource definition with
   *  spec/serverStartPolicy set to ADMIN_ONLY.
   * Make sure that ONLY administration server is in RUNNING state.
   * Make sure that no managed server can be started with ADMIN_ONLY policy.
   * Restart the domain using the sample script startDomain.sh
   * Make sure that all servers in the domain are in RUNNING state.
   * The usecase also verify the scripts startDomain.sh/stopDomain.sh make
   * no changes in a running/stopped domain respectively.
   */
  @Order(2)
  @Test
  @DisplayName("Restart the Domain with serverStartPolicy")
  void testDomainRestart() {

    String configServerPodName = domainUid + "-config-cluster-server1";
    String standaloneServerPodName = domainUid + "-standalone-managed";

    // startDomain.sh does not take any action on a running domain
    String result = executeLifecycleScript(domainUid, domainNamespace, samplePath, START_DOMAIN_SCRIPT, DOMAIN, null);
    assertTrue(result.contains("No changes needed"), "startDomain.sh shouldn't make changes");

    // Verify server instance(s) are shut down after stopDomain script execution
    logger.info("Stop entire WebLogic domain using the script");
    executeLifecycleScript(domainUid, domainNamespace, samplePath,STOP_DOMAIN_SCRIPT, DOMAIN, null);

    // make sure all the server pods are removed after patch
    checkPodDeleted(adminServerPodName, domainUid, domainNamespace);
    for (int i = 1; i <= replicaCount; i++) {
      checkPodDeleted(managedServerPrefix + i, domainUid, domainNamespace);
    }
    checkPodDeleted(configServerPodName, domainUid, domainNamespace);
    checkPodDeleted(standaloneServerPodName, domainUid, domainNamespace);

    // stopDomain.sh does not take any action on a stopped domain
    result = executeLifecycleScript(domainUid, domainNamespace, samplePath, STOP_DOMAIN_SCRIPT, DOMAIN, null);
    assertTrue(result.contains("No changes needed"), "stopDomain.sh shouldn't make changes");

    // managed server instances can't be started while domain is stopped
    result =  assertDoesNotThrow(() ->
            executeLifecycleScript(domainUid, domainNamespace, samplePath,
                START_SERVER_SCRIPT, SERVER_LIFECYCLE, "managed-server1", "", false),
        String.format("Failed to run %s", START_SERVER_SCRIPT));
    assertTrue(result.contains("Cannot start server"),
        "The script shouldn't start the managed server");
    logger.info("Managed server instances can not be started while spec.serverStartPolicy is NEVER");

    // Patch the Domain with serverStartPolicy set to ADMIN_ONLY
    // Here only Administration server pod should come up
    assertTrue(patchServerStartPolicy(domainUid, domainNamespace,
        "/spec/serverStartPolicy", "ADMIN_ONLY"),
        "Failed to patch domain's serverStartPolicy to ADMIN_ONLY");
    logger.info("Domain is patched to start only administrative server");

    checkPodReadyAndServiceExists(adminServerPodName,
        domainUid, domainNamespace);
    // make sure all other managed server pods are not provisioned
    for (int i = 1; i <= replicaCount; i++) {
      checkPodDeleted(managedServerPrefix + i, domainUid, domainNamespace);
    }
    checkPodDeleted(configServerPodName, domainUid, domainNamespace);
    checkPodDeleted(standaloneServerPodName, domainUid, domainNamespace);

    // verify managed server instances can not be started while
    // spec.serverStartPolicy is ADMIN_ONLY
    result =  assertDoesNotThrow(() ->
            executeLifecycleScript(domainUid, domainNamespace, samplePath,
                START_SERVER_SCRIPT, SERVER_LIFECYCLE, "managed-server1", "", false),
        String.format("Failed to run %s", START_SERVER_SCRIPT));
    assertTrue(result.contains("Cannot start server"),
        "The script shouldn't start the managed server");
    logger.info("Managed server instances can not be started while spec.serverStartPolicy is ADMIN_ONLY");

    // Verify server instances are started after startDomain script execution
    logger.info("Start entire WebLogic domain using the script");
    executeLifecycleScript(domainUid, domainNamespace, samplePath,START_DOMAIN_SCRIPT, DOMAIN, null);

    // check dynamic managed server pods are ready
    for (int i = 1; i <= replicaCount; i++) {
      checkPodReadyAndServiceExists(managedServerPrefix + i,
          domainUid, domainNamespace);
    }
    checkPodReadyAndServiceExists(configServerPodName,
        domainUid, domainNamespace);
    checkPodReadyAndServiceExists(standaloneServerPodName,
        domainUid, domainNamespace);
    logger.info("startDomain.sh successfully started the domain");
  }

  /**
   * Start an independent managed server with serverStartPolicy to IF_NEEDED.
   * The serverStartPolicy transition is IF_NEEDED-->NEVER-->ALWAYS
   * Stop an independent managed server by patching the domain resource with
   *  spec/managedServers/0/serverStartPolicy set to NEVER.
   * Make sure that ONLY the specified managed server is stopped.
   * Restart the independent managed server by patching the resource definition
   * with spec/managedServers/0/serverStartPolicy set to ALWAYS.
   * Make sure that the specified managed server is in RUNNING state
   */
  @Order(3)
  @Test
  @DisplayName("Restart the standalone managed server with serverStartPolicy ALWAYS")
  void testStandaloneManagedRestartAlways() {
    String serverName = "standalone-managed";
    String serverPodName = domainUid + "-" + serverName;

    // Make sure that configured managed server is ready
    checkPodReadyAndServiceExists(serverPodName,
        domainUid, domainNamespace);
    logger.info("Standalone managed server is RUNNING");

    assertTrue(patchServerStartPolicy(domainUid, domainNamespace,
        "/spec/managedServers/0/serverStartPolicy", "NEVER"),
        "Failed to patch Standalone managedServers's serverStartPolicy to NEVER");
    logger.info("Domain is patched to shutdown standalone managed server");

    checkPodDeleted(serverPodName, domainUid, domainNamespace);
    logger.info("Configured managed server shutdown success");

    assertTrue(patchServerStartPolicy(domainUid, domainNamespace,
        "/spec/managedServers/0/serverStartPolicy", "ALWAYS"),
        "Failed to patch Standalone managedServers's serverStartPolicy to ALWAYS");
    logger.info("Domain is patched to start standalone managed server");

    checkPodReadyAndServiceExists(serverPodName,
        domainUid, domainNamespace);
    logger.info("Standalone managed server restart (ALWAYS) success");
  }

  /**
   * Start an independent managed server with serverStartPolicy to IF_NEEDED.
   * The serverStartPolicy transition is IF_NEEDED-->NEVER-->IF_NEEDED
   * Stop an independent managed server by patching the domain resource with
   *  spec/managedServers/0/serverStartPolicy set to NEVER.
   * Make sure that ONLY the specified managed server is stopped.
   * Restart the independent managed server by patching the resource definition
   * with spec/managedServers/0/serverStartPolicy set to IF_NEEDED.
   * Make sure that the specified managed server is in RUNNING state
   */
  @Order(4)
  @Test
  @DisplayName("Restart the standalone managed server with serverStartPolicy IF_NEEDED")
  void testStandaloneManagedRestartIfNeeded() {
    String serverName = "standalone-managed";
    String serverPodName = domainUid + "-" + serverName;

    // Make sure that configured managed server is ready
    checkPodReadyAndServiceExists(serverPodName,
        domainUid, domainNamespace);
    logger.info("Standalone managed server is RUNNING");

    assertTrue(patchServerStartPolicy(domainUid, domainNamespace,
        "/spec/managedServers/0/serverStartPolicy", "NEVER"),
        "Failed to patch Standalone managedServers's serverStartPolicy to NEVER");
    logger.info("Domain is patched to shutdown standalone managed server");

    checkPodDeleted(serverPodName, domainUid, domainNamespace);
    logger.info("Standalone managed server shutdown success");

    assertTrue(patchServerStartPolicy(domainUid, domainNamespace,
        "/spec/managedServers/0/serverStartPolicy", "IF_NEEDED"),
        "Failed to patch Standalone managedServers's serverStartPolicy to IF_NEEDED");
    logger.info("Domain is patched to start standalone managed server");
    checkPodReadyAndServiceExists(serverPodName,
        domainUid, domainNamespace);
    logger.info("Standalone managed server restart (IF_NEEDED) success");
  }

  /**
   * Stop the independent managed server using the sample script stopServer.sh
   * Start the independent managed server using the sample script startServer.sh
   * The usecase also verify the scripts startServer.sh/stopServer.sh make
   * no changes in a running/stopped server respectively.
   */
  @Order(5)
  @Test
  @DisplayName("Restart the standalone managed server with sample script")
  void testStandaloneManagedRestart() {
    String serverName = "standalone-managed";
    String serverPodName = domainUid + "-" + serverName;
    String keepReplicaCountConstantParameter = "-k";

    // Make sure that configured managed server is ready
    checkPodReadyAndServiceExists(serverPodName,
        domainUid, domainNamespace);
    logger.info("Configured managed server is RUNNING");
    // startServer.sh does not take any action on a running server
    String result = executeLifecycleScript(domainUid, domainNamespace, samplePath,START_SERVER_SCRIPT,
        SERVER_LIFECYCLE, "standalone-managed",
        keepReplicaCountConstantParameter);
    assertTrue(result.contains("No changes needed"), "startServer.sh shouldn't make changes");

    // shutdown standalone-managed using the script stopServer.sh
    executeLifecycleScript(domainUid, domainNamespace, samplePath,STOP_SERVER_SCRIPT, SERVER_LIFECYCLE,
        "standalone-managed", keepReplicaCountConstantParameter);
    logger.info("Script executed to shutdown standalone managed server");

    checkPodDeleted(serverPodName, domainUid, domainNamespace);
    logger.info("Standalone managed server shutdown success");

    // stopServer.sh does not take any action on a stopped server
    result = executeLifecycleScript(domainUid, domainNamespace, samplePath,STOP_SERVER_SCRIPT,
        SERVER_LIFECYCLE, "standalone-managed",
        keepReplicaCountConstantParameter);
    assertTrue(result.contains("No changes needed"), "stopServer.sh shouldn't make changes");

    executeLifecycleScript(domainUid, domainNamespace, samplePath,START_SERVER_SCRIPT, SERVER_LIFECYCLE,
        "standalone-managed", keepReplicaCountConstantParameter);
    logger.info("Script executed to start standalone managed server");

    checkPodReadyAndServiceExists(serverPodName,
        domainUid, domainNamespace);
    logger.info("Standalone managed server restart success");
  }

  /**
   * Negative tests to verify:
   * (a) the sample script can not stop or start a non-existing server
   * (b) the sample script can not stop or start a non-existing cluster
   * (c) the sample script can not stop or start a non-existing domain.
   */
  @Order(6)
  @Test
  @DisplayName("Verify that the sample script can not stop or start non-existing components")
  void testRestartNonExistingComponent() {
    String wrongServerName = "ms1";
    String regex = ".*" + wrongServerName + ".*\\s*is not part";

    // verify that the script can not stop a non-existing server
    String result =  assertDoesNotThrow(() ->
            executeLifecycleScript(domainUid, domainNamespace, samplePath,
                STOP_SERVER_SCRIPT, SERVER_LIFECYCLE, wrongServerName, "", false),
        String.format("Failed to run %s", STOP_CLUSTER_SCRIPT));
    assertTrue(verifyExecuteResult(result, regex),"The script shouldn't stop a server that doesn't exist");

    // verify that the script can not start a non-existing server
    result =  assertDoesNotThrow(() ->
            executeLifecycleScript(domainUid, domainNamespace, samplePath,
                START_SERVER_SCRIPT, SERVER_LIFECYCLE, wrongServerName, "", false),
        String.format("Failed to run %s", START_SERVER_SCRIPT));
    assertTrue(verifyExecuteResult(result, regex),"The script shouldn't start a server that doesn't exist");

    // verify that the script can not stop a non-existing cluster
    String wrongClusterName = "cluster-3";
    result =  assertDoesNotThrow(() ->
            executeLifecycleScript(domainUid, domainNamespace, samplePath,
                STOP_CLUSTER_SCRIPT, CLUSTER_LIFECYCLE, wrongClusterName, "", false),
        String.format("Failed to run %s", STOP_CLUSTER_SCRIPT));
    assertTrue(result.contains("cluster cluster-3 is not part of domain"),
        "The script shouldn't stop a cluster that doesn't exist");

    // verify that the script can not start a non-existing cluster
    result =  assertDoesNotThrow(() ->
            executeLifecycleScript(domainUid, domainNamespace, samplePath,
                START_CLUSTER_SCRIPT, CLUSTER_LIFECYCLE, wrongClusterName, "", false),
        String.format("Failed to run %s", STOP_CLUSTER_SCRIPT));
    assertTrue(result.contains("cluster cluster-3 is not part of domain"),
        "The script shouldn't start a cluster that doesn't exist");

    // verify that the script can not stop a non-existing domain
    String domainName = "mii-start-policy" + "-123";
    regex = ".*" + domainName + ".*\\s*not found";
    result = assertDoesNotThrow(() ->
            executeLifecycleScript(domainUid, domainNamespace, samplePath,
                STOP_DOMAIN_SCRIPT, DOMAIN, null, "", false, domainName),
        String.format("Failed to run %s", STOP_DOMAIN_SCRIPT));
    assertTrue(verifyExecuteResult(result, regex),"The script shouldn't stop a domain that doesn't exist");

    // verify that the script can not start a non-existing domain
    result = assertDoesNotThrow(() ->
            executeLifecycleScript(domainUid, domainNamespace, samplePath,
                START_DOMAIN_SCRIPT, DOMAIN, null, "", false, domainName),
        String.format("Failed to run %s", START_DOMAIN_SCRIPT));
    assertTrue(verifyExecuteResult(result, regex),"The script shouldn't start a domain that doesn't exist");
  }

  /**
   * Verify Operator infrastructure log warning message, when the sample script
   * tries to start a server that exceeds the max cluster size.
   */
  @Order(7)
  @Test
  @DisplayName("verify the operator logs warning message when starting a server that exceeds max cluster size")
  void testOperatorLogWarningMsg() {
    String operatorPodName =
        assertDoesNotThrow(() -> getOperatorPodName(OPERATOR_RELEASE_NAME, opNamespace));
    logger.info("operator pod name: {0}", operatorPodName);
    String operatorPodLog = assertDoesNotThrow(() -> getPodLog(operatorPodName, opNamespace));
    logger.info("operator pod log: {0}", operatorPodLog);
    assertTrue(operatorPodLog.contains("WARNING"));
    assertTrue(operatorPodLog.contains(
        "management/weblogic/latest/serverRuntime/search failed with exception java.net.ConnectException"));
  }

  /**
   * Once the admin server is stopped, operator can not start a new managed
   * server from scratch if it has never been started earlier with
   * administration Server. Once the administration server is stopped, the
   * managed server can only be started in MSI (managed server independence)
   * mode. To start a managed server in MSI mode, the pre-requisite is that the
   * managed server MUST be started once before administration server is
   * shutdown, so that the security configuration is replicated to the managed
   * server. In this case of MII and DomainInImage model, the server
   * state/configuration is lost once the server is shutdown unless we use
   * domain-on-pv model. So in MII case, startServer.sh script update the
   * replica count but the server startup is deferred till we re-start the
   * administration server. Here the operator tries to start the managed
   * server but it will keep on failing  until administration server is
   * available.
   */
  @Order(8)
  @Test
  @DisplayName("Manage configured cluster server in absence of Administration Server")
  void testConfiguredServerLifeCycleWithoutAdmin() {
    String serverName = "config-cluster-server1";
    String serverPodName = domainUid + "-" + serverName;

    // Here managed server can be stopped without admin server
    // but can not be started to RUNNING state.

    try {
      // Make sure that config-cluster-server1 is RUNNING
      checkPodReadyAndServiceExists(serverPodName, domainUid, domainNamespace);
      logger.info("Server Pod [" + serverName + "] is in RUNNING state");

      // shutdown the admin server
      assertTrue(patchServerStartPolicy(domainUid, domainNamespace,
          "/spec/adminServer/serverStartPolicy", "NEVER"),
          "Failed to patch adminServer's serverStartPolicy to NEVER");
      logger.info("Domain is patched to shutdown administration server");
      checkPodDeleted(adminServerPodName, domainUid, domainNamespace);
      logger.info("Administration server shutdown success");

      // verify the script can stop the server by reducing replica count
      assertDoesNotThrow(() ->
              executeLifecycleScript(domainUid, domainNamespace, samplePath,STOP_SERVER_SCRIPT,
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

      // (re)Start the admin
      assertTrue(patchServerStartPolicy(domainUid, domainNamespace,
          "/spec/adminServer/serverStartPolicy", "IF_NEEDED"),
          "Failed to patch adminServer's serverStartPolicy to IF_NEEDED");
      checkPodReadyAndServiceExists(
          adminServerPodName, domainUid, domainNamespace);
      logger.info("administration server restart success");

      checkPodReadyAndServiceExists(serverPodName, domainUid, domainNamespace);
      logger.info("(re)Started [" + serverName + "] on admin server restart");
    } finally {
      // restart admin server
      assertTrue(patchServerStartPolicy(domainUid, domainNamespace,
          "/spec/adminServer/serverStartPolicy", "IF_NEEDED"),
          "Failed to patch adminServer's serverStartPolicy to IF_NEEDED");
      logger.info("Check admin service/pod {0} is created in namespace {1}",
          adminServerPodName, domainNamespace);
      checkPodReadyAndServiceExists(adminServerPodName, domainUid, domainNamespace);
    }
  }
}
