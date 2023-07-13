// Copyright (c) 2020, 2022, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.weblogic.kubernetes;

import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
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
import static oracle.weblogic.kubernetes.TestConstants.OPERATOR_RELEASE_NAME;
import static oracle.weblogic.kubernetes.actions.TestActions.getOperatorPodName;
import static oracle.weblogic.kubernetes.assertions.TestAssertions.isPodRestarted;
import static oracle.weblogic.kubernetes.assertions.impl.Kubernetes.doesPodNotExist;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.checkPodReadyAndServiceExists;
import static oracle.weblogic.kubernetes.utils.LoggingUtil.doesPodLogContainStringInTimeRange;
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
import static org.awaitility.Awaitility.with;
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
@Tag("olcne")
@Tag("oke-parallel")
@Tag("kind-parallel")
@Tag("okd-wls-mrg")
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
    if (assertDoesNotThrow(() -> doesPodNotExist(domainNamespace, domainUid, adminServerPodName))) {
      executeLifecycleScript(domainUid, domainNamespace, samplePath,
              START_SERVER_SCRIPT, SERVER_LIFECYCLE, "admin-server", "", true);
    }
    logger.info("Check admin service/pod {0} is created in namespace {1}",
        adminServerPodName, domainNamespace);
    checkPodReadyAndServiceExists(adminServerPodName,
        domainUid, domainNamespace);

    for (int i = 1; i <= replicaCount; i++) {
      String podName = managedServerPrefix + i;
      if (assertDoesNotThrow(() -> doesPodNotExist(domainNamespace, domainUid, podName))) {
        executeLifecycleScript(domainUid, domainNamespace, samplePath,
                START_SERVER_SCRIPT, SERVER_LIFECYCLE, "managed-server" + i, "", true);

      }
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
   * Stop the Administration server by using stopServer.sh sample script.
   * Make sure that Only the Administration server is stopped.
   * Restart the Administration server by using startServer.sh sample script.
   * Make sure that the Administration server is in RUNNING state.
   */
  @Order(0)
  @Test
  @DisplayName("Restart the Administration server with serverStartPolicy")
  void testAdminServerRestart() {

    String configServerPodName = domainUid + "-config-cluster-server1";
    String dynamicServerPodName = domainUid + "-managed-server1";
    String operatorPodName =
        assertDoesNotThrow(() -> getOperatorPodName(OPERATOR_RELEASE_NAME, opNamespace));

    OffsetDateTime dynTs = getPodCreationTime(domainNamespace, dynamicServerPodName);
    OffsetDateTime cfgTs = getPodCreationTime(domainNamespace, configServerPodName);

    // verify that the sample script can shutdown admin server
    executeLifecycleScript(domainUid, domainNamespace, samplePath,STOP_SERVER_SCRIPT,
        SERVER_LIFECYCLE, "admin-server", "", true);
    checkPodDeleted(adminServerPodName, domainUid, domainNamespace);
    logger.info("Administration server shutdown success");

    OffsetDateTime startTime = OffsetDateTime.now(ZoneId.systemDefault()).truncatedTo(ChronoUnit.SECONDS);
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

    // verify warning message is not logged in operator pod when admin server is shutdown
    logger.info("operator pod name: {0}", operatorPodName);
    assertFalse(doesPodLogContainStringInTimeRange(opNamespace, operatorPodName,
            "WARNING", startTime));
    assertFalse(doesPodLogContainStringInTimeRange(opNamespace, operatorPodName,
        "management/weblogic/latest/serverRuntime/search failed with exception java.net.ConnectException",
            startTime));

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
   *  spec/serverStartPolicy set to AdminOnly.
   * Make sure that ONLY administration server is in RUNNING state.
   * Make sure that no managed server can be started with AdminOnly policy.
   * Restart the domain using the sample script startDomain.sh
   * Make sure that all servers in the domain are in RUNNING state.
   * The use case also verify the scripts startDomain.sh/stopDomain.sh make
   * no changes in a running/stopped domain respectively.
   */
  @Order(1)
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
    logger.info("Managed server instances can not be started while spec.serverStartPolicy is Never");

    // Patch the Domain with serverStartPolicy set to AdminOnly
    // Here only Administration server pod should come up
    assertTrue(patchServerStartPolicy(domainUid, domainNamespace,
        "/spec/serverStartPolicy", "AdminOnly"),
        "Failed to patch domain's serverStartPolicy to AdminOnly");
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
    // spec.serverStartPolicy is AdminOnly
    result =  assertDoesNotThrow(() ->
            executeLifecycleScript(domainUid, domainNamespace, samplePath,
                START_SERVER_SCRIPT, SERVER_LIFECYCLE, "managed-server1", "", false),
        String.format("Failed to run %s", START_SERVER_SCRIPT));
    assertTrue(result.contains("Cannot start server"),
        "The script shouldn't start the managed server");
    logger.info("Managed server instances can not be started while spec.serverStartPolicy is AdminOnly");

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
   * Start an independent managed server with serverStartPolicy to IfNeeded.
   * The serverStartPolicy transition is IfNeeded-->Never-->Always
   * Stop an independent managed server by patching the domain resource with
   *  spec/managedServers/0/serverStartPolicy set to Never.
   * Make sure that ONLY the specified managed server is stopped.
   * Restart the independent managed server by patching the resource definition
   * with spec/managedServers/0/serverStartPolicy set to Always.
   * Make sure that the specified managed server is in RUNNING state
   */
  @Order(2)
  @Test
  @DisplayName("Restart the standalone managed server with serverStartPolicy Always")
  void testStandaloneManagedRestartAlways() {
    String serverName = "standalone-managed";
    String serverPodName = domainUid + "-" + serverName;

    // Make sure that configured managed server is ready
    checkPodReadyAndServiceExists(serverPodName,
        domainUid, domainNamespace);
    logger.info("Standalone managed server is RUNNING");

    assertTrue(patchServerStartPolicy(domainUid, domainNamespace,
        "/spec/managedServers/0/serverStartPolicy", "Never"),
        "Failed to patch Standalone managedServers's serverStartPolicy to Never");
    logger.info("Domain is patched to shutdown standalone managed server");

    checkPodDeleted(serverPodName, domainUid, domainNamespace);
    logger.info("Configured managed server shutdown success");

    assertTrue(patchServerStartPolicy(domainUid, domainNamespace,
        "/spec/managedServers/0/serverStartPolicy", "Always"),
        "Failed to patch Standalone managedServers's serverStartPolicy to Always");
    logger.info("Domain is patched to start standalone managed server");

    checkPodReadyAndServiceExists(serverPodName,
        domainUid, domainNamespace);
    logger.info("Standalone managed server restart (Always) success");
  }

  /**
   * Start an independent managed server with serverStartPolicy to IfNeeded.
   * The serverStartPolicy transition is IfNeeded-->Never-->IfNeeded
   * Stop an independent managed server by patching the domain resource with
   *  spec/managedServers/0/serverStartPolicy set to Never.
   * Make sure that ONLY the specified managed server is stopped.
   * Restart the independent managed server by patching the resource definition
   * with spec/managedServers/0/serverStartPolicy set to IfNeeded.
   * Make sure that the specified managed server is in RUNNING state
   */
  @Order(3)
  @Test
  @DisplayName("Restart the standalone managed server with serverStartPolicy IfNeeded")
  void testStandaloneManagedRestartIfNeeded() {
    String serverName = "standalone-managed";
    String serverPodName = domainUid + "-" + serverName;

    // Make sure that configured managed server is ready
    checkPodReadyAndServiceExists(serverPodName,
        domainUid, domainNamespace);
    logger.info("Standalone managed server is RUNNING");

    assertTrue(patchServerStartPolicy(domainUid, domainNamespace,
        "/spec/managedServers/0/serverStartPolicy", "Never"),
        "Failed to patch Standalone managedServers's serverStartPolicy to Never");
    logger.info("Domain is patched to shutdown standalone managed server");

    checkPodDeleted(serverPodName, domainUid, domainNamespace);
    logger.info("Standalone managed server shutdown success");

    assertTrue(patchServerStartPolicy(domainUid, domainNamespace,
        "/spec/managedServers/0/serverStartPolicy", "IfNeeded"),
        "Failed to patch Standalone managedServers's serverStartPolicy to IfNeeded");
    logger.info("Domain is patched to start standalone managed server");
    checkPodReadyAndServiceExists(serverPodName,
        domainUid, domainNamespace);
    logger.info("Standalone managed server restart (IfNeeded) success");
  }

  /**
   * Stop the independent managed server using the sample script stopServer.sh
   * Start the independent managed server using the sample script startServer.sh
   * The usecase also verify the scripts startServer.sh/stopServer.sh make
   * no changes in a running/stopped server respectively.
   */
  @Order(4)
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
  @Order(5)
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
  @Order(6)
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
          "/spec/adminServer/serverStartPolicy", "Never"),
          "Failed to patch adminServer's serverStartPolicy to Never");
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
}
