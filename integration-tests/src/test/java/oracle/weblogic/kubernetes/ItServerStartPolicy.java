// Copyright (c) 2020, Oracle Corporation and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.weblogic.kubernetes;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import io.kubernetes.client.custom.V1Patch;
import io.kubernetes.client.openapi.ApiException;
import io.kubernetes.client.openapi.models.V1EnvVar;
import io.kubernetes.client.openapi.models.V1LocalObjectReference;
import io.kubernetes.client.openapi.models.V1ObjectMeta;
import io.kubernetes.client.openapi.models.V1Secret;
import io.kubernetes.client.openapi.models.V1SecretReference;
import oracle.weblogic.domain.AdminServer;
import oracle.weblogic.domain.AdminService;
import oracle.weblogic.domain.Channel;
import oracle.weblogic.domain.Cluster;
import oracle.weblogic.domain.Configuration;
import oracle.weblogic.domain.Domain;
import oracle.weblogic.domain.DomainSpec;
import oracle.weblogic.domain.ManagedServer;
import oracle.weblogic.domain.Model;
import oracle.weblogic.domain.ServerPod;
import oracle.weblogic.kubernetes.actions.impl.primitive.Command;
import oracle.weblogic.kubernetes.actions.impl.primitive.CommandParams;
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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import static java.util.concurrent.TimeUnit.MINUTES;
import static java.util.concurrent.TimeUnit.SECONDS;
import static oracle.weblogic.kubernetes.TestConstants.DOMAIN_API_VERSION;
import static oracle.weblogic.kubernetes.TestConstants.DOMAIN_VERSION;
import static oracle.weblogic.kubernetes.TestConstants.K8S_NODEPORT_HOST;
import static oracle.weblogic.kubernetes.TestConstants.MII_BASIC_IMAGE_NAME;
import static oracle.weblogic.kubernetes.TestConstants.MII_BASIC_IMAGE_TAG;
import static oracle.weblogic.kubernetes.TestConstants.OCIR_SECRET_NAME;
import static oracle.weblogic.kubernetes.actions.ActionConstants.ITTESTS_DIR;
import static oracle.weblogic.kubernetes.actions.ActionConstants.WORK_DIR;
import static oracle.weblogic.kubernetes.actions.TestActions.createDomainCustomResource;
import static oracle.weblogic.kubernetes.actions.TestActions.createSecret;
import static oracle.weblogic.kubernetes.actions.TestActions.getServiceNodePort;
import static oracle.weblogic.kubernetes.actions.TestActions.patchDomainCustomResource;
import static oracle.weblogic.kubernetes.assertions.TestAssertions.domainExists;
import static oracle.weblogic.kubernetes.assertions.TestAssertions.isPodRestarted;
import static oracle.weblogic.kubernetes.assertions.TestAssertions.podDoesNotExist;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.checkPodReadyAndServiceExists;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.createConfigMapAndVerify;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.createOcirRepoSecret;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.getExternalServicePodName;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.getPodCreationTime;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.installAndVerifyOperator;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.setPodAntiAffinity;
import static oracle.weblogic.kubernetes.utils.ExecCommand.exec;
import static oracle.weblogic.kubernetes.utils.ThreadSafeLogger.getLogger;
import static org.apache.commons.io.FileUtils.copyDirectory;
import static org.apache.commons.io.FileUtils.deleteDirectory;
import static org.awaitility.Awaitility.with;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;


/**
 * Create a WebLogic domain with one dynamic cluster (with two managed servers)
 * one configured cluster (with two managed servers) and a standalone manged 
 * server. The replica count is set to 1 and serverStartPolicy is set to 
 * IF_NEEDED at managed server level. 
 * This test class verifies the following scenarios.
 *
 * <p>testAdminServerRestart
 *  Restart the Administration Server by changing serverStartPolicy 
 *   IF_NEEDED->NEVER->IF_NEEDED
 *  Verify that the sample script can not start admin server
 *
 * <p>testDomainRestart
 *  Restart the entire domain by using the sample script stopDomain.sh and startDomain.sh
 *
 * <p>testConfigClusterRestart
 *  Restart all servers in configured cluster by
 *    using the sample script stopCluster.sh and startCluster.sh
 *
 * <p>testDynamicClusterRestart
 *  Restart all servers in dynamic cluster by
 *    using the sample script stopCluster.sh and startCluster.sh
 *
 * <p>testConfigClusterStartServerUsingAlways
 *  Restart a server in configured cluster (beyond replica count) 
 *   IF_NEEDED->ALWAYS->IF_NEEDED
 *  Verify that if server start policy is ALWAYS and the server is selected
 *   to start based on the replica count, the sample script exits without making any changes
 *
 * <p>testDynamicClusterStartServerUsingAlways
 *  Restart a server in dynamic cluster (beyond replica count) 
 *   IF_NEEDED->ALWAYS->IF_NEEDED
 *  Verify that if server start policy is ALWAYS and the server is selected
 *   to start based on the replica count, the sample script exits without making any changes
 *
 * <p>testConfigClusterReplicaCountIsMaintained
 *  Shutdown a running managed server (say ms1) in a config
 *    cluster using the sample script stopServer.sh
 *  Make sure next managed server (say ms2) is scheduled to run to maintain the 
 *    replica count while the running managed server ms1 goes down.
 *  Start server ms1 in a config cluster using the sample script startServer.sh
 *  Make sure server ms2 goes down and server ms1 is re-scheduled to maintain 
 *    the replica count
 *
 * <p>testDynamicClusterReplicaCountIsMaintained
 *  Shutdown a running managed server (say ms1) in a dynamic
 *    cluster using the sample script stopServer.sh
 *  Make sure next managed server (say ms2) is scheduled to run to maintain the 
 *    replica count while the running managed server ms1 goes down.
 *  Start server ms1 in a dynamic cluster using the sample script startServer.sh.
 *  Make sure server ms2 goes down and server ms1 is re-scheduled to maintain 
 *    the replica count
 *
 * <p>testStandaloneManagedRestartIfNeeded
 *  Restart standalone server by changing serverStartPolicy 
 *   IF_NEEDED->NEVER->IF_NEEDED
 *  Verify that if server start policy is IF_NEEDED and the server is selected
 *   to start based on the replica count, the sample script exits without making any changes
 *
 * <p>testStandaloneManagedRestartAlways
 *  Restart standalone server by changing serverStartPolicy 
 *   IF_NEEDED->NEVER->ALWAYS
 *  Verify that if server start policy is ALWAYS and the server is selected
 *   to start based on the replica count, the sample script exits without making any changes
 *
 * <p>testStartDynamicClusterServerRandomlyPicked
 *   Start a dynamic cluster managed server picked randomly within the max cluster size
 *
 * <p>testRestartNonExistingComponent
 *   Verify that the sample script can not stop or start non-existing domain, cluster or server
 *
 * <p>testStartManagedServerBeyondMaxClusterLimit
 *   Verify that the sample script can not start a server that exceeds the max cluster size
 *
 * <p>testServerRestartManagedServerWithoutAdmin
 *   In the absence of Administration Server, sample script can start/stop a managed server
 */

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DisplayName("ServerStartPolicy attribute in different levels in a MII domain")
@IntegrationTest
class ItServerStartPolicy {

  public static final String SERVER_LIFECYCLE = "Server";
  public static final String CLUSTER_LIFECYCLE = "Cluster";
  public static final String DOMAIN = "DOMAIN";
  public static final String STOP_SERVER_SCRIPT = "stopServer.sh";
  public static final String START_SERVER_SCRIPT = "startServer.sh";
  public static final String STOP_CLUSTER_SCRIPT = "stopCluster.sh";
  public static final String START_CLUSTER_SCRIPT = "startCluster.sh";
  public static final String STOP_DOMAIN_SCRIPT = "stopDomain.sh";
  public static final String START_DOMAIN_SCRIPT = "startDomain.sh";

  private static String opNamespace = null;
  private static String domainNamespace = null;
  private static ConditionFactory withStandardRetryPolicy = null;

  private static int replicaCount = 1;
  private static String domainUid = "mii-start-policy";
  private StringBuffer checkCluster = null;
  private V1Patch patch = null;

  private final String adminServerPodName = domainUid + "-admin-server";
  private final String managedServerPrefix = domainUid + "-managed-server";
  private static LoggingFacade logger = null;

  private static final Path samplePath = Paths.get(ITTESTS_DIR, "../kubernetes/samples");
  private static final Path tempSamplePath = Paths.get(WORK_DIR, "sample-testing");
  private static final Path domainLifecycleSamplePath = Paths.get(samplePath + "/scripts/domain-lifecycle");


  /**
   * Install Operator.
   * Create a domain resource definition.
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

    String configMapName = "wls-ext-configmap";
    createConfigMapAndVerify(
        configMapName, domainUid, domainNamespace,
        Arrays.asList("model.wls.ext.config.yaml"));

    // create the domain CR with a pre-defined configmap
    createDomainResource(domainUid, domainNamespace, adminSecretName,
        OCIR_SECRET_NAME, encryptionSecretName,
        replicaCount, configMapName);

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

    //copy the samples directory to a temporary location
    setupSample();
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
         checkManagedServerConfiguration("config-cluster-server1");
    assertTrue(isServerConfigured, 
        "Could not find managed server from configured cluster");
    logger.info("Found managed server from configured cluster");

    // Check standalone server configuration is available 
    boolean isStandaloneServerConfigured = 
         checkManagedServerConfiguration("standalone-managed");
    assertTrue(isStandaloneServerConfigured, 
        "Could not find standalone managed server from configured cluster");
    logger.info("Found standalone managed server configuration");
  }

  /**
   * Stop the Administration server by patching the resource definition with 
   *  spec/adminServer/serverStartPolicy set to NEVER.
   * Make sure that Only the Administration server is stopped. 
   * Restart the Administration server by patching the resource definition with 
   *  spec/adminServer/serverStartPolicy set to IF_NEEDED.
   * Make sure that the Administration server is in RUNNING state.
   * Verify that the sample script can not start or shutdown admin server
   */
  @Test
  @DisplayName("Restart the Administration server with serverStartPolicy")
  public void testAdminServerRestart() {

    String configServerPodName = domainUid + "-config-cluster-server1";
    String dynamicServerPodName = domainUid + "-managed-server1";

    DateTime dynTs = getPodCreationTime(domainNamespace, dynamicServerPodName);
    DateTime cfgTs = getPodCreationTime(domainNamespace, configServerPodName);

    patchServerStartPolicy("/spec/adminServer/serverStartPolicy", "NEVER");
    logger.info("Domain is patched to shutdown administration server");

    checkPodDeleted(adminServerPodName, domainUid, domainNamespace);
    logger.info("Administration server shutdown success");

    logger.info("Check managed server pods are not affected");
    Callable<Boolean> isDynRestarted = 
         assertDoesNotThrow(() -> isPodRestarted(dynamicServerPodName, 
         domainNamespace, dynTs));
    assertFalse(assertDoesNotThrow(() -> isDynRestarted.call().booleanValue()),
         "Dynamic managed server pod must not be restated");

    Callable<Boolean> isCfgRestarted = 
         assertDoesNotThrow(() -> isPodRestarted(configServerPodName, 
         domainNamespace, cfgTs));
    assertFalse(assertDoesNotThrow(() -> isCfgRestarted.call().booleanValue()),
         "Configured managed server pod must not be restated");

    // verify that the sample script can not start admin server
    String result =  assertDoesNotThrow(() ->
        executeLifecycleScript(START_SERVER_SCRIPT, SERVER_LIFECYCLE, "admin-server", "", false),
        String.format("Failed to run %s", START_SERVER_SCRIPT));
    assertTrue(result.contains("script doesn't support starting or stopping administration server"),
        "The script shouldn't start the admin server");

    patchServerStartPolicy("/spec/adminServer/serverStartPolicy", "IF_NEEDED");
    logger.info("Domain is patched to start administration server");

    logger.info("Check admin service/pod {0} is created in namespace {1}",
        adminServerPodName, domainNamespace);
    checkPodReadyAndServiceExists(adminServerPodName, 
            domainUid, domainNamespace);

    // verify that the sample script can not shutdown admin server
    result =  assertDoesNotThrow(() ->
          executeLifecycleScript(STOP_SERVER_SCRIPT, SERVER_LIFECYCLE, "admin-server", "", false),
          String.format("Failed to run %s", STOP_SERVER_SCRIPT));
    assertTrue(result.contains("script doesn't support starting or stopping administration server"),
        "The script shouldn't stop the admin server");
  }

  /**
   * Stop a configured cluster using the sample script stopCluster.sh
   * Make sure that only server(s) in the configured cluster are stopped. 
   * Make sure that server(s) in the dynamic cluster are in RUNNING state. 
   * Restart the cluster using the sample script startCluster.sh
   * Make sure that servers in the configured cluster are in RUNNING state. 
   */
  @Test
  @DisplayName("Restart the configured cluster with serverStartPolicy")
  public void testConfigClusterRestart() {

    String configServerPodName = domainUid + "-config-cluster-server1";
    String dynamicServerPodName = domainUid + "-managed-server1";

    DateTime dynTs = getPodCreationTime(domainNamespace, dynamicServerPodName);

    checkPodReadyAndServiceExists(configServerPodName, 
              domainUid, domainNamespace);
    logger.info("(BeforePatch) configured cluster managed server is RUNNING");

    // Verify all clustered server pods are shutdown after stopCluster script execution
    logger.info("Stop configured cluster using the script");
    executeLifecycleScript(STOP_CLUSTER_SCRIPT, CLUSTER_LIFECYCLE, "cluster-2");

    checkPodDeleted(configServerPodName, domainUid, domainNamespace);
    logger.info("Config cluster shutdown success");

    // check managed server from other cluster are not affected
    logger.info("Check dynamic managed server pods are not affected");
    
    Callable<Boolean> isDynRestarted = 
         assertDoesNotThrow(() -> isPodRestarted(dynamicServerPodName, 
         domainNamespace, dynTs));
    assertFalse(assertDoesNotThrow(() -> isDynRestarted.call().booleanValue()),
         "Dynamic managed server pod must not be restated");

    // Verify all clustered server pods are started after startCluster script execution
    logger.info("Start configured cluster using the script");
    executeLifecycleScript(START_CLUSTER_SCRIPT, CLUSTER_LIFECYCLE, "cluster-2");

    checkPodReadyAndServiceExists(configServerPodName, 
              domainUid, domainNamespace);
    logger.info("Configured cluster restart success");
  }

  /**
   * Stop a dynamic cluster using the sample script stopCluster.sh.
   * Make sure that only servers in the dynamic cluster are stopped. 
   * Make sure that only servers in the configured cluster are in the 
   * RUNNING state. 
   * Restart the dynamic cluster using the sample script startCluster.sh
   * Make sure that servers in the dynamic cluster are in RUNNING state again. 
   */
  @Test
  @DisplayName("Restart the dynamic cluster with serverStartPolicy")
  public void testDynamicClusterRestart() {

    String dynamicServerPodName = domainUid + "-managed-server1";
    String configServerPodName = domainUid + "-config-cluster-server1";

    DateTime cfgTs = getPodCreationTime(domainNamespace, configServerPodName);

    checkPodReadyAndServiceExists(dynamicServerPodName, domainUid, domainNamespace);

    // Verify all clustered server pods are shut down after stopCluster script execution
    logger.info("Stop dynamic cluster using the script");
    executeLifecycleScript(STOP_CLUSTER_SCRIPT, CLUSTER_LIFECYCLE, "cluster-1");

    checkPodDeleted(dynamicServerPodName, domainUid, domainNamespace);
    logger.info("Dynamic cluster shutdown success");

    // check managed server from other cluster are not affected
    Callable<Boolean> isCfgRestarted = 
         assertDoesNotThrow(() -> isPodRestarted(configServerPodName, 
         domainNamespace, cfgTs));
    assertFalse(assertDoesNotThrow(() -> isCfgRestarted.call().booleanValue()),
         "Configured managed server pod must not be restated");

    // Verify all clustered server pods are started after startCluster script execution
    logger.info("Start dynamic cluster using the script");
    executeLifecycleScript(START_CLUSTER_SCRIPT, CLUSTER_LIFECYCLE, "cluster-1");

    checkPodReadyAndServiceExists(dynamicServerPodName, 
              domainUid, domainNamespace);
    logger.info("Dynamic cluster restart success");
  }

  /**
   * Stop the entire domain using the sample script stopDomain.sh
   * Make sure that all servers in the domain are stopped. 
   * Restart the domain by patching the resource definition with 
   *  spec/serverStartPolicy set to ADMIN_ONLY.
   * Make sure that ONLY Admin Server is in RUNNING state. 
   * Restart the domain using the sample script startDomain.sh
   * Make sure that all servers in the domain are in RUNNING state. 
   */
  @Test
  @DisplayName("Restart the Domain with serverStartPolicy")
  public void testDomainRestart() {

    String configServerPodName = domainUid + "-config-cluster-server1";
    String standaloneServerPodName = domainUid + "-standalone-managed";

    // Verify all WebLogic server instance pods are shut down after stopDomain script execution
    logger.info("Stop entire WebLogic domain using the script");
    executeLifecycleScript(STOP_DOMAIN_SCRIPT, DOMAIN, null);
   
    // make sure all the server pods are removed after patch
    checkPodDeleted(adminServerPodName, domainUid, domainNamespace);
    for (int i = 1; i <= replicaCount; i++) {
      checkPodDeleted(managedServerPrefix + i, domainUid, domainNamespace);
    }
    checkPodDeleted(configServerPodName, domainUid, domainNamespace);
    checkPodDeleted(standaloneServerPodName, domainUid, domainNamespace);

    // Patch the Domain with serverStartPolicy set to ADMIN_ONLY
    // Here only Admin server pod should come up
    patchServerStartPolicy("/spec/serverStartPolicy", "ADMIN_ONLY");
    logger.info("Domain is patched to start only administrative server");

    checkPodReadyAndServiceExists(adminServerPodName, 
             domainUid, domainNamespace);
    // make sure all other managed server pods are not provisioned 
    for (int i = 1; i <= replicaCount; i++) {
      checkPodDeleted(managedServerPrefix + i, domainUid, domainNamespace);
    }
    checkPodDeleted(configServerPodName, domainUid, domainNamespace);
    checkPodDeleted(standaloneServerPodName, domainUid, domainNamespace);

    // Verify all WebLogic server instance pods are started after startDomain script execution
    logger.info("Start entire WebLogic domain using the script");
    executeLifecycleScript(START_DOMAIN_SCRIPT, DOMAIN, null);

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
   * The domain custom resource has a second configured manged server with serverStartPolicy IF_NEEDED
   * Initially, the server will not come up since the replica count is set to 1
   * Update the serverStartPolicy for config-cluster-server2 to ALWAYS
   * by patching the resource definition with 
   *  spec/managedServers/1/serverStartPolicy set to ALWAYS
   * Make sure that managed server config-cluster-server2 is up and running
   * Verify that if server start policy is ALWAYS and the server is selected
   * to start based on the replica count, it means that server is already started or is
   * in the process of starting. In this case, script exits without making any changes
   * Stop the managed server by patching the resource definition 
   *   with spec/managedServers/1/serverStartPolicy set to IF_NEEDED.
   * Make sure the specified managed server is stopped as per replica count.
   */
  @Test
  @DisplayName("Start/stop config cluster managed server by updating serverStartPolicy to ALWAYS/IF_NEEDED")
  public void testConfigClusterStartServerUsingAlways() {
    String serverName = "config-cluster-server2";
    String serverPodName = domainUid + "-" + serverName;

    // Make sure that managed server is not running 
    checkPodDeleted(serverPodName, domainUid, domainNamespace);

    patchServerStartPolicy("/spec/managedServers/1/serverStartPolicy", "ALWAYS");
    logger.info("Domain is patched to start configured cluster managed server");

    checkPodReadyAndServiceExists(serverPodName, 
          domainUid, domainNamespace);
    logger.info("Configured cluster managed server is RUNNING");

    // Verify that if server start policy is ALWAYS and the server is selected
    // to start based on the replica count, it means that server is already started or is
    // in the process of starting. In this case, script exits without making any changes.
    String result = executeLifecycleScript(START_SERVER_SCRIPT, SERVER_LIFECYCLE, serverName);
    assertTrue(result.contains("No changes needed"), "startServer.sh shouldn't make changes");

    patchServerStartPolicy("/spec/managedServers/1/serverStartPolicy", "IF_NEEDED");
    logger.info("Domain is patched to stop configured cluster managed server");

    logger.info("Wait for managed server ${0} to be shutdown", serverPodName);
    checkPodDeleted(serverPodName, domainUid, domainNamespace);
    logger.info("Config cluster managed server shutdown success");
  }

  /**
   * Add managed server configuration (managed-server2) to CRD in a dynamic 
   * cluster with ServerStartPolicy IF_NEEDED. 
   * So initially, the server will not come up since replica count is set to 1.
   * Update the ServerStartPolicy for managed-server2 to ALWAYS
   * by patching the resource definition with 
   *  spec/managedServers/2/serverStartPolicy set to ALWAYS.
   * Make sure that managed server managed-server2 is up and running
   * Verify that if server start policy is ALWAYS and the server is selected
   * to start based on the replica count, it means that server is already started or is
   * in the process of starting. In this case, script exits without making any changes.
   * Stop the managed server by patching the resource definition 
   *   with spec/managedServers/2/serverStartPolicy set to IF_NEEDED.
   * Make sure the specified managed server is stopped as per replica count.
   */
  @Test
  @DisplayName("Start/stop dynamic cluster managed server by updating serverStartPolicy to ALWAYS/IF_NEEDED")
  public void testDynamicClusterStartServerUsingAlways() {
    String serverName = "managed-server2";
    String serverPodName = domainUid + "-" + serverName;

    // Make sure that managed server is not running 
    checkPodDeleted(serverPodName, domainUid, domainNamespace);
    
    patchServerStartPolicy("/spec/managedServers/2/serverStartPolicy","ALWAYS");
    logger.info("Domain resource patched to start the second managed server in dynamic cluster");
    checkPodReadyAndServiceExists(serverPodName, 
          domainUid, domainNamespace);
    logger.info("Second managed server in dynamic cluster is RUNNING");

    // Verify that if server start policy is ALWAYS and the server is selected
    // to start based on the replica count, it means that server is already started or is
    // in the process of starting. In this case, script exits without making any changes.
    String result = executeLifecycleScript(START_SERVER_SCRIPT, SERVER_LIFECYCLE, serverName);
    assertTrue(result.contains("No changes needed"), "startServer.sh shouldn't make changes");

    patchServerStartPolicy("/spec/managedServers/2/serverStartPolicy","IF_NEEDED");
    logger.info("Domain resource patched to shutdown the second managed server in dynamic cluster");

    logger.info("Wait for managed server ${0} to be shutdown", serverPodName);
    checkPodDeleted(serverPodName, domainUid, domainNamespace);
    logger.info("Dynamic cluster second managed server shutdown success");
  }

  /**
   * Add the first managed server (config-cluster-server1) in a configured 
   * cluster with serverStartPolicy IF_NEEDED. 
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
  @Test
  @DisplayName("Stop a running config cluster managed server and verify the replica count is maintained")
  public void testConfigClusterReplicaCountIsMaintained() {
    String serverName = "config-cluster-server1";
    String serverPodName = domainUid + "-" + serverName;
    String serverPodName2 = domainUid + "-config-cluster-server2";
    String keepReplicaCountConstantParameter = "-k";

    // Make sure that managed server(2) is not running 
    checkPodDeleted(serverPodName2, domainUid, domainNamespace);

    // shutdown config-cluster-server1 with keep_replica_constant option
    executeLifecycleScript(STOP_SERVER_SCRIPT, SERVER_LIFECYCLE, serverName, keepReplicaCountConstantParameter);

    // Make sure config-cluster-server1 is deleted 
    checkPodDeleted(serverPodName, domainUid, domainNamespace);
    // Make sure  config-cluster-server2 is started 
    checkPodReadyAndServiceExists(serverPodName2, domainUid, domainNamespace);
    logger.info("Configured cluster managed Server(2) is RUNNING");

    // start config-cluster-server1 with keep_replica_constant option
    executeLifecycleScript(START_SERVER_SCRIPT, SERVER_LIFECYCLE, serverName, keepReplicaCountConstantParameter);

    // Make sure config-cluster-server2 is deleted 
    checkPodDeleted(serverPodName2, domainUid, domainNamespace);

    // Make sure config-cluster-server1 is re-started
    checkPodReadyAndServiceExists(serverPodName, domainUid, domainNamespace);
  }

  /**
   * Add the first managed server (managed-server1) in a dynamic 
   * cluster with serverStartPolicy IF_NEEDED. 
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
  @Test
  @DisplayName("Stop a running dynamic cluster managed server and verify the replica count ")
  public void testDynamicClusterReplicaCountIsMaintained() {
    String serverPodName = domainUid + "-managed-server1";
    String serverPodName2 = domainUid + "-managed-server2";
    String keepReplicaCountConstantParameter = "-k";

    // Make sure that managed server(2) is not running 
    checkPodDeleted(serverPodName2, domainUid, domainNamespace);

    // shutdown managed-server1 with keep_replica_constant option
    executeLifecycleScript(STOP_SERVER_SCRIPT, SERVER_LIFECYCLE, "managed-server1", keepReplicaCountConstantParameter);

    // Make sure maanged-server1 is deleted 
    checkPodDeleted(serverPodName, domainUid, domainNamespace);
    checkPodReadyAndServiceExists(serverPodName2, domainUid, domainNamespace);
    logger.info("Dynamic cluster managed server(2) is RUNNING");

    // start managed-server1 with keep_replica_constant option
    executeLifecycleScript(START_SERVER_SCRIPT, SERVER_LIFECYCLE, "managed-server1", keepReplicaCountConstantParameter);

    // Make sure managed-server2 is deleted 
    checkPodDeleted(serverPodName2, domainUid, domainNamespace);

    // Make sure managed-server1 is re-started
    checkPodReadyAndServiceExists(serverPodName, domainUid, domainNamespace);
  }

  /**
   * Start independent managed server by setting serverStartPolicy to IF_NEEDED.
   * Stop an independent managed server by patching the resource definition with 
   *  spec/managedServers/0/serverStartPolicy set to NEVER.
   * Make sure that ONLY the specified managed server is stopped. 
   * Restart the independent managed server by patching the resource definition 
   * with spec/managedServers/0/serverStartPolicy set to ALWAYS.
   * Make sure that the specified managed server is in RUNNING state
   * Verify that if server start policy is ALWAYS and the server is selected
   * to start based on the replica count, it means that server is already started or is
   * in the process of starting. In this case, script exits without making any changes.
   */
  @Test
  @DisplayName("Restart the standalone managed server with serverStartPolicy")
  public void testStandaloneManagedRestartAlways() {
    String serverName = "standalone-managed";
    String serverPodName = domainUid + "-" + serverName;

    // Make sure that configured managed server is ready 
    checkPodReadyAndServiceExists(serverPodName,
            domainUid, domainNamespace);
    logger.info("Configured managed server is RUNNING");

    patchServerStartPolicy("/spec/managedServers/0/serverStartPolicy", "NEVER");
    logger.info("Domain is patched to shutdown standalone managed server");

    checkPodDeleted(serverPodName, domainUid, domainNamespace);
    logger.info("Configured managed server shutdown success");

    patchServerStartPolicy("/spec/managedServers/0/serverStartPolicy", "ALWAYS");
    logger.info("Domain is patched to start standalone managed server");

    checkPodReadyAndServiceExists(serverPodName,
            domainUid, domainNamespace);
    logger.info("Configured managed server restart success");

    // Verify that if server start policy is ALWAYS and the server is selected
    // to start based on the replica count, it means that server is already started or is
    // in the process of starting. In this case, script exits without making any changes.
    String result = executeLifecycleScript(START_SERVER_SCRIPT, SERVER_LIFECYCLE, serverName);
    assertTrue(result.contains("No changes needed"), "startServer.sh shouldn't make changes");
  }

  /**
   * Start independent managed server by setting serverStartPolicy to IF_NEEDED.
   * Stop an independent managed server by patching the resource definition with 
   *  spec/managedServers/0/serverStartPolicy set to NEVER.
   * Make sure that ONLY the specified managed server is stopped. 
   * Restart the independent managed server by patching the resource definition 
   * with spec/managedServers/0/serverStartPolicy set to IF_NEEDED.
   * Make sure that the specified managed server is in RUNNING state
   * Verify that if server start policy is IF_NEEDED and the server is selected
   * to start based on the replica count, it means that server is already started or is
   * in the process of starting. In this case, script exits without making any changes.
   */
  @Test
  @DisplayName("Restart the standalone managed server with serverStartPolicy")
  public void testStandaloneManagedRestartIfNeeded() {
    String serverName = "standalone-managed";
    String serverPodName = domainUid + "-" + serverName;

    // Make sure that configured managed server is ready 
    checkPodReadyAndServiceExists(serverPodName,
        domainUid, domainNamespace);
    logger.info("Standalone managed server is RUNNING");

    patchServerStartPolicy("/spec/managedServers/0/serverStartPolicy", "NEVER");
    logger.info("Domain is patched to shutdown standalone managed server");

    checkPodDeleted(serverPodName, domainUid, domainNamespace);
    logger.info("Standalone managed server shutdown success");

    patchServerStartPolicy("/spec/managedServers/0/serverStartPolicy", "IF_NEEDED");
    logger.info("Domain is patched to start standalone managed server");

    checkPodReadyAndServiceExists(serverPodName,
        domainUid, domainNamespace);
    logger.info("Standalone managed server restart success");

    // Verify that if server start policy is IF_NEEDED and the server is selected
    // to start based on the replica count, it means that server is already started or is
    // in the process of starting. In this case, script exits without making any changes.
    String result = executeLifecycleScript(START_SERVER_SCRIPT, SERVER_LIFECYCLE, serverName);
    assertTrue(result.contains("No changes needed"), "startServer.sh shouldn't make changes");
  }

  /**
   * Start a dynamic cluster managed server within the max cluster size
   * and the managed server name is not next in line to be started by the Operator but one a customer picks .
   */
  @Test
  @DisplayName("Pick a dynamic cluster managed server randomly within the max cluster size and verify it starts")
  public void testStartDynamicClusterServerRandomlyPicked() {
    String serverName = "managed-server3";
    String serverPodName3 = domainUid + "-" + serverName;
    String serverPodName1 = domainUid + "-managed-server1";
    String serverPodName2 = domainUid + "-managed-server2";

    // Make sure that managed server(2) is not running
    checkPodDeleted(serverPodName2, domainUid, domainNamespace);

    // Verify that starting a dynamic cluster managed server within the max cluster size succeeds
    executeLifecycleScript(START_SERVER_SCRIPT, SERVER_LIFECYCLE, serverName);

    // Make sure that managed server(1) is still running
    checkPodReadyAndServiceExists(serverPodName1, domainUid, domainNamespace);
    logger.info("Dynamic cluster managed server {0} is still RUNNING", serverPodName1);

    // Verify that a randomly picked dynamic cluster managed server within the max cluster size starts successfully
    checkPodReadyAndServiceExists(serverPodName3, domainUid, domainNamespace);
    logger.info("Replica Count Increased and a new Dynamic cluster managed server {0} is RUNNING", serverPodName3);
  }

  /**
   * Negative test to verify:
   * (a) the sample script can not stop or start a non-existing server
   * (b) the sample script can not stop or start a non-existing cluster
   * (c) the sample script can not stop or start a non-existing domain.
   */
  @Test
  @DisplayName("Verify that the sample script can not stop or start non-existing components")
  public void testRestartNonExistingComponent() {
    String wrongServerName = "ms1";
    String regex = ".*" + wrongServerName + ".*\\s*is not part";

    // verify that the script can not stop a non-existing server
    String result =  assertDoesNotThrow(() ->
        executeLifecycleScript(STOP_SERVER_SCRIPT, SERVER_LIFECYCLE, wrongServerName, "", false),
        String.format("Failed to run %s", STOP_CLUSTER_SCRIPT));
    assertTrue(verifyExecuteResult(result, regex),"The script shouldn't stop a server that doesn't exist");

    // verify that the script can not start a non-existing server
    result =  assertDoesNotThrow(() ->
        executeLifecycleScript(START_SERVER_SCRIPT, SERVER_LIFECYCLE, wrongServerName, "", false),
      String.format("Failed to run %s", START_SERVER_SCRIPT));
    assertTrue(verifyExecuteResult(result, regex),"The script shouldn't start a server that doesn't exist");

    // verify that the script can not stop a non-existing cluster
    String wrongClusterName = "cluster-3";
    regex = ".*" + wrongClusterName + ".*\\s*not part of domain";
    result =  assertDoesNotThrow(() ->
        executeLifecycleScript(STOP_CLUSTER_SCRIPT, CLUSTER_LIFECYCLE, wrongClusterName, "", false),
        String.format("Failed to run %s", STOP_CLUSTER_SCRIPT));
    assertTrue(result.contains("cluster cluster-3 is not part of domain"),
        "The script shouldn't stop a cluster that doesn't exist");

    // verify that the script can not start a non-existing cluster
    result =  assertDoesNotThrow(() ->
        executeLifecycleScript(START_CLUSTER_SCRIPT, CLUSTER_LIFECYCLE, wrongClusterName, "", false),
        String.format("Failed to run %s", STOP_CLUSTER_SCRIPT));
    assertTrue(result.contains("cluster cluster-3 is not part of domain"),
        "The script shouldn't start a cluster that doesn't exist");

    // verify that the script can not stop a non-existing domain
    String domainName = "mii-start-policy" + "-123";
    regex = ".*" + domainName + ".*\\s*not found";
    result = assertDoesNotThrow(() ->
        executeLifecycleScript(STOP_DOMAIN_SCRIPT, DOMAIN, null, "", false, domainName),
        String.format("Failed to run %s", STOP_DOMAIN_SCRIPT));
    assertTrue(verifyExecuteResult(result, regex),"The script shouldn't stop a domain that doesn't exist");

    // verify that the script can not start a non-existing domain
    result = assertDoesNotThrow(() ->
        executeLifecycleScript(START_DOMAIN_SCRIPT, DOMAIN, null, "", false, domainName),
        String.format("Failed to run %s", START_DOMAIN_SCRIPT));
    assertTrue(verifyExecuteResult(result, regex),"The script shouldn't start a domain that doesn't exist");
  }

  /**
   * Negative test to verify that the sample script can not start a server that exceeds the max cluster size
   * Currently, the domain resource has a configured cluster with two managed servers
   * and a dynamic cluster with MaxClusterSize set to 5. The sample script shouldn't start server 3
   * in configured cluster and server 6 in dynamic cluster.
   */
  @Test
  @DisplayName("Verify that the sample script can not start a server that exceeds the max cluster size")
  public void testStartManagedServerBeyondMaxClusterLimit() {
    String configServerName = "config-cluster-server3";
    String dynServerName = "managed-server6";

    // verify that the script can not start a server in config cluster that exceeds the max cluster size
    String regex = ".*" + configServerName + ".*\\s*is not part";
    String result =  assertDoesNotThrow(() ->
        executeLifecycleScript(START_SERVER_SCRIPT, SERVER_LIFECYCLE, configServerName, "", false),
        String.format("Failed to run %s", START_SERVER_SCRIPT));
    assertTrue(verifyExecuteResult(result, regex),"The script shouldn't stop a server that is beyong the limit");

    // verify that the script can not start a server in dynamic cluster that exceeds the max cluster size
    regex = ".*outside the range of allowed servers";
    result =  assertDoesNotThrow(() ->
        executeLifecycleScript(START_SERVER_SCRIPT, SERVER_LIFECYCLE, dynServerName, "", false),
        String.format("Failed to run %s", START_SERVER_SCRIPT));
    assertTrue(verifyExecuteResult(result, regex),"The script shouldn't stop a server that is beyong the limit");
  }

  /**
   * Verify that after the admin server is stopped, the sample script can start or stop a managed server.
   */
  @Disabled("Due to the bug OWLS-86251")
  @Test
  @DisplayName("In the absence of Administration Server, sample script can start/stop a managed server")
  public void testServerRestartManagedServerWithoutAdmin() {
    String serverName = "config-cluster-server1";

    try {
      // shutdown the admin server
      patchServerStartPolicy("/spec/adminServer/serverStartPolicy", "NEVER");
      logger.info("Domain is patched to shutdown administration server");

      // verify that the script can stop a server in absent of admin server
      assertDoesNotThrow(() ->
          executeLifecycleScript(STOP_SERVER_SCRIPT, SERVER_LIFECYCLE, serverName, "", true),
          String.format("Failed to run %s", STOP_SERVER_SCRIPT));
      checkPodDeleted(serverName, domainUid, domainNamespace);
      logger.info("Shutdown " + serverName + " without admin server success");

      // verify that the script can start a server in absent of admin server
      assertDoesNotThrow(() ->
          executeLifecycleScript(START_SERVER_SCRIPT, SERVER_LIFECYCLE, serverName, "", true),
          String.format("Failed to run %s", START_SERVER_SCRIPT));
      checkPodReadyAndServiceExists(serverName, domainUid, domainNamespace);
      logger.info("Start " + serverName + " without admin server success");

      // verify that in absent of admin server, when server is part of a cluster and
      // keep_replica_constant option is false (the default)
      // and the effective start policy of the server is IF_NEEDED and increasing replica count
      // will naturally start the server, the script increases the replica count
      String serverName2 = "managed-server2";
      assertDoesNotThrow(() ->
          executeLifecycleScript(START_SERVER_SCRIPT, SERVER_LIFECYCLE, serverName2, "", true),
          String.format("Failed to run %s", START_SERVER_SCRIPT));
      checkPodReadyAndServiceExists(serverName, domainUid, domainNamespace);
      logger.info("Start " + serverName2 + " without admin server success");
    } finally {
      // restart admin server
      patchServerStartPolicy("/spec/adminServer/serverStartPolicy", "IF_NEEDED");
      logger.info("Domain is patched to start administration server");

      logger.info("Check admin service/pod {0} is created in namespace {1}",
          adminServerPodName, domainNamespace);
      checkPodReadyAndServiceExists(adminServerPodName, domainUid, domainNamespace);
      logger.info("AdminServer restart success");
    }
  }

  private static void createDomainSecret(String secretName, String username, String password, String domNamespace)
          throws ApiException {
    Map<String, String> secretMap = new HashMap();
    secretMap.put("username", username);
    secretMap.put("password", password);
    boolean secretCreated = assertDoesNotThrow(() -> createSecret(new V1Secret()
            .metadata(new V1ObjectMeta()
                    .name(secretName)
                    .namespace(domNamespace))
            .stringData(secretMap)), "Create secret failed with ApiException");
    assertTrue(secretCreated, String.format("create secret failed for %s in namespace %s", secretName, domNamespace));
  }

  private static void createDomainResource(
      String domainUid, String domNamespace, String adminSecretName,
      String repoSecretName, String encryptionSecretName, 
      int replicaCount, String configmapName) {
    List<String> securityList = new ArrayList<>();
    // create the domain CR
    Domain domain = new Domain()
            .apiVersion(DOMAIN_API_VERSION)
            .kind("Domain")
            .metadata(new V1ObjectMeta()
                    .name(domainUid)
                    .namespace(domNamespace))
            .spec(new DomainSpec()
                    .domainUid(domainUid)
                    .domainHomeSourceType("FromModel")
                    .image(MII_BASIC_IMAGE_NAME + ":" + MII_BASIC_IMAGE_TAG)
                    .addImagePullSecretsItem(new V1LocalObjectReference()
                            .name(repoSecretName))
                    .webLogicCredentialsSecret(new V1SecretReference()
                            .name(adminSecretName)
                            .namespace(domNamespace))
                    .includeServerOutInPodLog(true)
                    .serverStartPolicy("IF_NEEDED")
                    .serverPod(new ServerPod()
                            .addEnvItem(new V1EnvVar()
                                    .name("JAVA_OPTIONS")
                                    .value("-Dweblogic.StdoutDebugEnabled=false"))
                            .addEnvItem(new V1EnvVar()
                                    .name("USER_MEM_ARGS")
                                    .value("-Djava.security.egd=file:/dev/./urandom ")))
                    .adminServer(new AdminServer()
                            .serverStartState("RUNNING")
                            .adminService(new AdminService()
                                    .addChannelsItem(new Channel()
                                            .channelName("default")
                                            .nodePort(0))))
                    .addClustersItem(new Cluster()
                            .clusterName("cluster-1")
                            .replicas(replicaCount)
                            .serverStartPolicy("IF_NEEDED")
                            .serverStartState("RUNNING"))
                    .addClustersItem(new Cluster()
                            .clusterName("cluster-2")
                            .replicas(replicaCount)
                            .serverStartPolicy("IF_NEEDED")
                            .serverStartState("RUNNING"))
                    .addManagedServersItem(new ManagedServer()
                            .serverName("standalone-managed")
                            .serverStartPolicy("IF_NEEDED")
                            .serverStartState("RUNNING"))
                    .addManagedServersItem(new ManagedServer()
                            .serverName("config-cluster-server2")
                            .serverStartPolicy("IF_NEEDED")
                            .serverStartState("RUNNING"))
                    .addManagedServersItem(new ManagedServer()
                            .serverName("managed-server2")
                            .serverStartPolicy("IF_NEEDED")
                            .serverStartState("RUNNING"))
                    .addManagedServersItem(new ManagedServer()
                            .serverName("config-cluster-server1")
                            .serverStartPolicy("IF_NEEDED")
                            .serverStartState("RUNNING"))
                    .addManagedServersItem(new ManagedServer()
                            .serverName("managed-server1")
                            .serverStartPolicy("IF_NEEDED")
                            .serverStartState("RUNNING"))
                    .configuration(new Configuration()
                            .secrets(securityList)
                            .model(new Model()
                                    .domainType("WLS")
                                    .configMap(configmapName)
                                    .runtimeEncryptionSecret(encryptionSecretName))
                        .introspectorJobActiveDeadlineSeconds(300L)));
    setPodAntiAffinity(domain);
    logger.info("Create domain custom resource for domainUid {0} in namespace {1}",
            domainUid, domNamespace);
    boolean domCreated = assertDoesNotThrow(() -> createDomainCustomResource(domain),
            String.format("Create domain custom resource failed with ApiException for %s in namespace %s",
                    domainUid, domNamespace));
    assertTrue(domCreated, String.format("Create domain custom resource failed with ApiException "
                    + "for %s in namespace %s", domainUid, domNamespace));
  }

  private void checkPodDeleted(String podName, String domainUid, String domNamespace) {
    withStandardRetryPolicy
        .conditionEvaluationListener(
            condition -> logger.info("Waiting for pod {0} to be deleted in namespace {1} "
                    + "(elapsed time {2}ms, remaining time {3}ms)",
                podName,
                domNamespace,
                condition.getElapsedTimeInMS(),
                condition.getRemainingTimeInMS()))
        .until(assertDoesNotThrow(() -> podDoesNotExist(podName, domainUid, domNamespace),
            String.format("podDoesNotExist failed with ApiException for %s in namespace in %s",
                podName, domNamespace)));
  }

  /*
   * Verify the server MBEAN configuration through rest API.
   * @param managedServer name of the managed server
   * @returns true if MBEAN is found otherwise false
   **/
  private boolean checkManagedServerConfiguration(String managedServer) {
    ExecResult result = null;
    int adminServiceNodePort
        = getServiceNodePort(domainNamespace, getExternalServicePodName(adminServerPodName), "default");
    checkCluster = new StringBuffer("status=$(curl --user weblogic:welcome1 ");
    checkCluster.append("http://" + K8S_NODEPORT_HOST + ":" + adminServiceNodePort)
          .append("/management/tenant-monitoring/servers/")
          .append(managedServer)
          .append(" --silent --show-error ")
          .append(" -o /dev/null")
          .append(" -w %{http_code});")
          .append("echo ${status}");
    logger.info("checkManagedServerConfiguration: curl command {0}", new String(checkCluster));
    try {
      result = exec(new String(checkCluster), true);
    } catch (Exception ex) {
      logger.info("Exception in checkManagedServerConfiguration() {0}", ex);
      return false;
    }
    logger.info("checkManagedServerConfiguration: curl command returned {0}", result.toString());
    if (result.stdout().equals("200")) {
      return true;
    } else {
      return false;
    }
  }

  public void patchServerStartPolicy(String patchPath, String policy) {

    StringBuffer patchStr = null;
    patchStr = new StringBuffer("[{");
    patchStr.append("\"op\": \"replace\",")
        .append(" \"path\": \"")
        .append(patchPath)
        .append("\",")
        .append(" \"value\":  \"")
        .append(policy)
        .append("\"")
        .append(" }]");

    logger.info("The domain resource patch string: {0}", patchStr);
    patch = new V1Patch(new String(patchStr));
    boolean crdPatched = assertDoesNotThrow(() ->
            patchDomainCustomResource(domainUid, domainNamespace, patch, "application/json-patch+json"),
        "patchDomainCustomResource(managedShutdown) failed");
    assertTrue(crdPatched, "patchDomainCustomResource failed");
  }

  // copy samples directory to a temporary location
  private static void setupSample() {
    assertDoesNotThrow(() -> {
      // copy ITTESTS_DIR + "../kubernates/samples" to WORK_DIR + "/sample-testing"
      logger.info("Deleting and recreating {0}", tempSamplePath);
      Files.createDirectories(tempSamplePath);
      deleteDirectory(tempSamplePath.toFile());
      Files.createDirectories(tempSamplePath);

      logger.info("Copying {0} to {1}", samplePath, tempSamplePath);
      copyDirectory(samplePath.toFile(), tempSamplePath.toFile());
    });
  }

  // Function to execute domain lifecyle scripts
  private String executeLifecycleScript(String script, String scriptType, String entityName) {
    return executeLifecycleScript(script, scriptType, entityName, "");
  }

  // Function to execute domain lifecyle scripts
  private String executeLifecycleScript(String script, String scriptType, String entityName, String extraParams) {
    return executeLifecycleScript(script, scriptType, entityName, extraParams, true);
  }

  // Function to execute domain lifecyle scripts
  private String executeLifecycleScript(String script,
                                        String scriptType,
                                        String entityName,
                                        String extraParams,
                                        boolean checkResult,
                                        String... args) {
    String domainName = (args.length == 0) ? domainUid : args[0];
    CommandParams params;
    //boolean result;
    //String commonParameters = " -d " + domainUid + " -n " + domainNamespace;
    String commonParameters = " -d " + domainName + " -n " + domainNamespace;
    params = new CommandParams().defaults();
    if (scriptType.equals(SERVER_LIFECYCLE)) {
      params.command("sh "
          + Paths.get(domainLifecycleSamplePath.toString(), "/" + script).toString()
          + commonParameters + " -s " + entityName + " " + extraParams);
    } else if (scriptType.equals(CLUSTER_LIFECYCLE)) {
      params.command("sh "
          + Paths.get(domainLifecycleSamplePath.toString(), "/" + script).toString()
          + commonParameters + " -c " + entityName);
    } else {
      params.command("sh "
          + Paths.get(domainLifecycleSamplePath.toString(), "/" + script).toString()
          + commonParameters);
    }

    ExecResult execResult = Command.withParams(params).executeAndReturnResult();
    if (checkResult) {
      assertEquals(0, execResult.exitValue(),
          String.format("Failed to execute script  %s ", script));
    }

    return execResult.toString();
  }

  private boolean verifyExecuteResult(String result, String regex) {
    Pattern pattern = Pattern.compile(regex);
    Matcher matcher = pattern.matcher(result);

    return matcher.find();
  }
}
