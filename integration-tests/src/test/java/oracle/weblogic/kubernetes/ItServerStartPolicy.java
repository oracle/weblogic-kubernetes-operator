// Copyright (c) 2020, 2021, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.weblogic.kubernetes;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import static java.util.concurrent.TimeUnit.MINUTES;
import static java.util.concurrent.TimeUnit.SECONDS;
import static oracle.weblogic.kubernetes.TestConstants.DOMAIN_API_VERSION;
import static oracle.weblogic.kubernetes.TestConstants.DOMAIN_VERSION;
import static oracle.weblogic.kubernetes.TestConstants.K8S_NODEPORT_HOST;
import static oracle.weblogic.kubernetes.TestConstants.MII_BASIC_IMAGE_NAME;
import static oracle.weblogic.kubernetes.TestConstants.MII_BASIC_IMAGE_TAG;
import static oracle.weblogic.kubernetes.actions.ActionConstants.ITTESTS_DIR;
import static oracle.weblogic.kubernetes.actions.ActionConstants.MODEL_DIR;
import static oracle.weblogic.kubernetes.actions.ActionConstants.WORK_DIR;
import static oracle.weblogic.kubernetes.actions.TestActions.createDomainCustomResource;
import static oracle.weblogic.kubernetes.actions.TestActions.createSecret;
import static oracle.weblogic.kubernetes.actions.TestActions.getServiceNodePort;
import static oracle.weblogic.kubernetes.assertions.TestAssertions.domainExists;
import static oracle.weblogic.kubernetes.assertions.TestAssertions.isPodRestarted;
import static oracle.weblogic.kubernetes.utils.CommonPatchTestUtils.patchServerStartPolicy;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.checkClusterReplicaCountMatches;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.checkPodDeleted;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.checkPodInitializing;
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
 * Create a (MII) WebLogic domain with a dynamic cluster with two managed 
 * servers, a configured cluster with two managed servers and a standalone 
 * managed server. The replica count is set to 1 and serverStartPolicy is set 
 * to IF_NEEDED at managed server level. 
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
  public static final String managedServerNamePrefix = "managed-server";
  public static final String CLUSTER_1 = "cluster-1";
  public static final String CLUSTER_2 = "cluster-2";

  private static String domainNamespace = null;

  private static final int replicaCount = 1;
  private static final String domainUid = "mii-start-policy";

  private final String adminServerPodName = domainUid + "-admin-server";
  private final String managedServerPrefix = domainUid + "-" + managedServerNamePrefix;
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
    ConditionFactory withStandardRetryPolicy = with().pollDelay(2, SECONDS)
            .and().with().pollInterval(10, SECONDS)
            .atMost(5, MINUTES).await();

    // get a new unique opNamespace
    logger.info("Creating unique namespace for Operator");
    assertNotNull(namespaces.get(0), "Namespace list is null");
    String opNamespace = namespaces.get(0);

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
            Collections.singletonList(MODEL_DIR + "/model.wls.ext.config.yaml"));

    // create the domain CR with a pre-defined configmap
    createDomainResource(domainNamespace, adminSecretName,
            encryptionSecretName,
            configMapName);

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
  public void testStopManagedServerBeyondMinClusterLimit() {
    String serverPodName = domainUid + "-managed-server1";
    String serverPodName2 = domainUid + "-managed-server2";

    // shutdown managed-server1 with keep_replica_constant option not set
    // This operator MUST fail as the MinDynamicCluster size is 1 
    // and allowReplicasBelowMinDynClusterSize is false

    String regex = "it is at its minimum";
    String result =  assertDoesNotThrow(() ->
        executeLifecycleScript(STOP_SERVER_SCRIPT, SERVER_LIFECYCLE, "managed-server1", "", false),
        String.format("Failed to run %s", STOP_CLUSTER_SCRIPT));
    assertTrue(verifyExecuteResult(result, regex),"The script shouldn't stop a server to go below Minimum");

    // Make sure managed-server1 is deleted 
    checkPodDeleted(serverPodName, domainUid, domainNamespace);
    // Make sure managed-server2 is provisioned to mantain the replica count
    checkPodReadyAndServiceExists(serverPodName2, domainUid, domainNamespace);

    // start managed-server1 with keep_replica_constant option
    // to bring the domain to original configuation with only managed-server1
    executeLifecycleScript(START_SERVER_SCRIPT, SERVER_LIFECYCLE, "managed-server1", "-k");
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
  public void testAdminServerRestart() {

    String configServerPodName = domainUid + "-config-cluster-server1";
    String dynamicServerPodName = domainUid + "-managed-server1";

    OffsetDateTime dynTs = getPodCreationTime(domainNamespace, dynamicServerPodName);
    OffsetDateTime cfgTs = getPodCreationTime(domainNamespace, configServerPodName);

    // verify that the sample script can shutdown admin server
    executeLifecycleScript(STOP_SERVER_SCRIPT, SERVER_LIFECYCLE, "admin-server", "", true);
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
    executeLifecycleScript(START_SERVER_SCRIPT, SERVER_LIFECYCLE, "admin-server", "", true);
    logger.info("Check admin service/pod {0} is created in namespace {1}",
        adminServerPodName, domainNamespace);
    checkPodReadyAndServiceExists(adminServerPodName, 
            domainUid, domainNamespace);
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
  @Order(2)
  @Test
  @DisplayName("Restart the configured cluster with serverStartPolicy")
  public void testConfigClusterRestart() {

    String configServerPodName = domainUid + "-config-cluster-server1";
    String dynamicServerPodName = domainUid + "-managed-server1";

    OffsetDateTime dynTs = getPodCreationTime(domainNamespace, dynamicServerPodName);

    checkPodReadyAndServiceExists(configServerPodName, 
              domainUid, domainNamespace);
    // startCluster.sh does not take any action on a running cluster
    String result = executeLifecycleScript(START_CLUSTER_SCRIPT, CLUSTER_LIFECYCLE, CLUSTER_2);
    assertTrue(result.contains("No changes needed"), "startCluster.sh shouldn't make changes");

    // Verify dynamic server are shutdown after stopCluster script execution
    logger.info("Stop configured cluster using the script");
    executeLifecycleScript(STOP_CLUSTER_SCRIPT, CLUSTER_LIFECYCLE, CLUSTER_2);

    checkPodDeleted(configServerPodName, domainUid, domainNamespace);
    logger.info("Config cluster shutdown success");

    // check managed server from other cluster are not affected
    logger.info("Check dynamic managed server pods are not affected");
    
    Callable<Boolean> isDynRestarted = 
         assertDoesNotThrow(() -> isPodRestarted(dynamicServerPodName, 
         domainNamespace, dynTs));
    assertFalse(assertDoesNotThrow(isDynRestarted::call),
         "Dynamic managed server pod must not be restated");

    // stopCluster.sh does not take any action on a stopped cluster
    result = executeLifecycleScript(STOP_CLUSTER_SCRIPT, CLUSTER_LIFECYCLE, CLUSTER_2);
    assertTrue(result.contains("No changes needed"), "stopCluster.sh shouldn't make changes");
    // Verify dynamic server are started after startCluster script execution
    logger.info("Start configured cluster using the script");
    executeLifecycleScript(START_CLUSTER_SCRIPT, CLUSTER_LIFECYCLE, CLUSTER_2);
    checkPodReadyAndServiceExists(configServerPodName, 
              domainUid, domainNamespace);
    logger.info("Configured cluster restart success");
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
  @Order(3)
  @Test
  @DisplayName("Restart the dynamic cluster with serverStartPolicy")
  public void testDynamicClusterRestart() {

    String dynamicServerPodName = domainUid + "-managed-server1";
    String configServerPodName = domainUid + "-config-cluster-server1";

    OffsetDateTime cfgTs = getPodCreationTime(domainNamespace, configServerPodName);
    checkPodReadyAndServiceExists(dynamicServerPodName, domainUid, domainNamespace);
    // startCluster.sh does not take any action on a running cluster
    String result = executeLifecycleScript(START_CLUSTER_SCRIPT, CLUSTER_LIFECYCLE, CLUSTER_1);
    assertTrue(result.contains("No changes needed"), "startCluster.sh shouldn't make changes");

    // Verify dynamic server are shut down after stopCluster script execution
    logger.info("Stop dynamic cluster using the script");
    executeLifecycleScript(STOP_CLUSTER_SCRIPT, CLUSTER_LIFECYCLE, CLUSTER_1);

    checkPodDeleted(dynamicServerPodName, domainUid, domainNamespace);
    logger.info("Dynamic cluster shutdown success");

    // stopCluster.sh does not take any action on a stopped cluster
    result = executeLifecycleScript(STOP_CLUSTER_SCRIPT, CLUSTER_LIFECYCLE, CLUSTER_1);
    assertTrue(result.contains("No changes needed"), "stopCluster.sh shouldn't make changes");

    // check managed server from other cluster are not affected
    Callable<Boolean> isCfgRestarted = 
         assertDoesNotThrow(() -> isPodRestarted(configServerPodName, 
         domainNamespace, cfgTs));
    assertFalse(assertDoesNotThrow(isCfgRestarted::call),
         "Configured managed server pod must not be restated");

    // Verify clustered server are started after startCluster script execution
    logger.info("Start dynamic cluster using the script");
    executeLifecycleScript(START_CLUSTER_SCRIPT, CLUSTER_LIFECYCLE, CLUSTER_1);
    checkPodReadyAndServiceExists(dynamicServerPodName, 
              domainUid, domainNamespace);
    logger.info("Dynamic cluster restart success");
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
  @Order(4)
  @Test
  @DisplayName("Restart the Domain with serverStartPolicy")
  public void testDomainRestart() {

    String configServerPodName = domainUid + "-config-cluster-server1";
    String standaloneServerPodName = domainUid + "-standalone-managed";

    // startDomain.sh does not take any action on a running domain
    String result = executeLifecycleScript(START_DOMAIN_SCRIPT, DOMAIN, null);
    assertTrue(result.contains("No changes needed"), "startDomain.sh shouldn't make changes");

    // Verify server instance(s) are shut down after stopDomain script execution
    logger.info("Stop entire WebLogic domain using the script");
    executeLifecycleScript(STOP_DOMAIN_SCRIPT, DOMAIN, null);
   
    // make sure all the server pods are removed after patch
    checkPodDeleted(adminServerPodName, domainUid, domainNamespace);
    for (int i = 1; i <= replicaCount; i++) {
      checkPodDeleted(managedServerPrefix + i, domainUid, domainNamespace);
    }
    checkPodDeleted(configServerPodName, domainUid, domainNamespace);
    checkPodDeleted(standaloneServerPodName, domainUid, domainNamespace);

    // stopDomain.sh does not take any action on a stopped domain
    result = executeLifecycleScript(STOP_DOMAIN_SCRIPT, DOMAIN, null);
    assertTrue(result.contains("No changes needed"), "stopDomain.sh shouldn't make changes");

    // managed server instances can't be started while domain is stopped
    result =  assertDoesNotThrow(() ->
       executeLifecycleScript(START_SERVER_SCRIPT, SERVER_LIFECYCLE, "managed-server1", "", false),
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
       executeLifecycleScript(START_SERVER_SCRIPT, SERVER_LIFECYCLE, "managed-server1", "", false),
       String.format("Failed to run %s", START_SERVER_SCRIPT));
    assertTrue(result.contains("Cannot start server"),
        "The script shouldn't start the managed server");
    logger.info("Managed server instances can not be started while spec.serverStartPolicy is ADMIN_ONLY");
    
    // Verify server instances are started after startDomain script execution
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
   * Verify ALWAYS serverStartPolicy (config cluster) overrides replica count.
   * The configured cluster has a second managed server(config-cluster-server2)
   * with serverStartPolicy set to IF_NEEDED. Initially, the server will not 
   * come up since the replica count for the cluster is set to 1. 
   * Update the serverStartPolicy for the server config-cluster-server2 to 
   * ALWAYS by patching the resource definition with 
   *  spec/managedServers/1/serverStartPolicy set to ALWAYS
   * Make sure that managed server config-cluster-server2 is up and running
   * Stop the managed server by patching the resource definition 
   *   with spec/managedServers/1/serverStartPolicy set to IF_NEEDED.
   * Make sure the specified managed server is stopped as per replica count.
   */
  @Order(5)
  @Test
  @DisplayName("Start/stop config cluster managed server by updating serverStartPolicy to ALWAYS/IF_NEEDED")
  public void testConfigClusterStartServerAlways() {
    String serverName = "config-cluster-server2";
    String serverPodName = domainUid + "-" + serverName;

    // Make sure that managed server is not running 
    checkPodDeleted(serverPodName, domainUid, domainNamespace);
    assertTrue(patchServerStartPolicy(domainUid, domainNamespace,
         "/spec/managedServers/1/serverStartPolicy", "ALWAYS"),
         "Failed to patch config managedServers's serverStartPolicy to ALWAYS");
    logger.info("Configured managed server is patched to set the serverStartPolicy to ALWAYS");
    checkPodReadyAndServiceExists(serverPodName, 
          domainUid, domainNamespace);
    logger.info("Configured cluster managed server is RUNNING");

    // Stop the server by changing the serverStartPolicy to IF_NEEDED
    assertTrue(patchServerStartPolicy(domainUid, domainNamespace,
         "/spec/managedServers/1/serverStartPolicy", "IF_NEEDED"),
         "Failed to patch config managedServers's serverStartPolicy to IF_NEEDED");
    logger.info("Domain resource patched to shutdown the second managed server in configured cluster");
    logger.info("Wait for managed server ${0} to be shutdown", serverPodName);
    checkPodDeleted(serverPodName, domainUid, domainNamespace);
    logger.info("Config cluster managed server shutdown success");
  }

  /**
   * Verify ALWAYS serverStartPolicy (dynamic cluster) overrides replica count.
   * The dynamic cluster has a second managed server(managed-server2)
   * with serverStartPolicy set to IF_NEEDED. Initially, the server will not 
   * come up since the replica count for the cluster is set to 1. 
   * Update the ServerStartPolicy for managed-server2 to ALWAYS
   * by patching the resource definition with 
   *  spec/managedServers/2/serverStartPolicy set to ALWAYS.
   * Make sure that managed server managed-server2 is up and running
   * Stop the managed server by patching the resource definition 
   *   with spec/managedServers/2/serverStartPolicy set to IF_NEEDED.
   * Make sure the specified managed server is stopped as per replica count.
   */
  @Order(6)
  @Test
  @DisplayName("Start/stop dynamic cluster managed server by updating serverStartPolicy to ALWAYS/IF_NEEDED")
  public void testDynamicClusterStartServerAlways() {
    String serverName = "managed-server2";
    String serverPodName = domainUid + "-" + serverName;

    // Make sure that managed server is not running 
    checkPodDeleted(serverPodName, domainUid, domainNamespace);
    
    assertTrue(patchServerStartPolicy(domainUid, domainNamespace,
         "/spec/managedServers/2/serverStartPolicy", "ALWAYS"),
         "Failed to patch dynamic managedServers's serverStartPolicy to ALWAYS");
    logger.info("Dynamic managed server is patched to set the serverStartPolicy to ALWAYS");
    checkPodReadyAndServiceExists(serverPodName, 
          domainUid, domainNamespace);
    logger.info("Second managed server in dynamic cluster is RUNNING");

    // Stop the server by changing the serverStartPolicy to IF_NEEDED
    assertTrue(patchServerStartPolicy(domainUid, domainNamespace,
         "/spec/managedServers/2/serverStartPolicy", "IF_NEEDED"),
         "Failed to patch dynamic managedServers's serverStartPolicy to IF_NEEDED");
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
  @Order(7)
  @Test
  @DisplayName("Stop/Start a running config cluster managed server and verify the replica count is maintained")
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
  @Order(8)
  @Test
  @DisplayName("Stop/Start a running dynamic cluster managed server and verify the replica count ")
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
   * Start an independent managed server with serverStartPolicy to IF_NEEDED.
   * The serverStartPolicy transition is IF_NEEDED-->NEVER-->ALWAYS
   * Stop an independent managed server by patching the domain resource with 
   *  spec/managedServers/0/serverStartPolicy set to NEVER.
   * Make sure that ONLY the specified managed server is stopped. 
   * Restart the independent managed server by patching the resource definition 
   * with spec/managedServers/0/serverStartPolicy set to ALWAYS.
   * Make sure that the specified managed server is in RUNNING state
   */
  @Order(9)
  @Test
  @DisplayName("Restart the standalone managed server with serverStartPolicy ALWAYS")
  public void testStandaloneManagedRestartAlways() {
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
  @Order(10)
  @Test
  @DisplayName("Restart the standalone managed server with serverStartPolicy IF_NEEDED")
  public void testStandaloneManagedRestartIfNeeded() {
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
  @Order(11)
  @Test
  @DisplayName("Restart the standalone managed server with sample script")
  public void testStandaloneManagedRestart() {
    String serverName = "standalone-managed";
    String serverPodName = domainUid + "-" + serverName;
    String keepReplicaCountConstantParameter = "-k";

    // Make sure that configured managed server is ready 
    checkPodReadyAndServiceExists(serverPodName,
            domainUid, domainNamespace);
    logger.info("Configured managed server is RUNNING");
    // startServer.sh does not take any action on a running server
    String result = executeLifecycleScript(START_SERVER_SCRIPT, 
          SERVER_LIFECYCLE, "standalone-managed", 
          keepReplicaCountConstantParameter);
    assertTrue(result.contains("No changes needed"), "startServer.sh shouldn't make changes");

    // shutdown standalone-managed using the script stopServer.sh
    executeLifecycleScript(STOP_SERVER_SCRIPT, SERVER_LIFECYCLE, 
         "standalone-managed", keepReplicaCountConstantParameter);
    logger.info("Script executed to shutdown standalone managed server");

    checkPodDeleted(serverPodName, domainUid, domainNamespace);
    logger.info("Standalone managed server shutdown success");

    // stopServer.sh does not take any action on a stopped server
    result = executeLifecycleScript(STOP_SERVER_SCRIPT, 
          SERVER_LIFECYCLE, "standalone-managed", 
          keepReplicaCountConstantParameter);
    assertTrue(result.contains("No changes needed"), "stopServer.sh shouldn't make changes");

    executeLifecycleScript(START_SERVER_SCRIPT, SERVER_LIFECYCLE, 
            "standalone-managed", keepReplicaCountConstantParameter);
    logger.info("Script executed to start standalone managed server");

    checkPodReadyAndServiceExists(serverPodName,
            domainUid, domainNamespace);
    logger.info("Standalone managed server restart success");
  }

  /**
   * Make sure the startServer script can start any server (not in order)
   * in a dynamic cluster within the max cluster size limit. 
   * Say the max cluster size is 3 and managed-server1 is running.
   * startServer script can start managed-server3 explicitly by skipping 
   * managed-server2. 
   */
  @Order(12)
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
    logger.info("Randomly picked dynamic cluster managed server {0} is RUNNING", serverPodName3);
  }

  /**
   * Negative tests to verify:
   * (a) the sample script can not stop or start a non-existing server
   * (b) the sample script can not stop or start a non-existing cluster
   * (c) the sample script can not stop or start a non-existing domain.
   */
  @Order(13)
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
   * Negative test to verify that the sample script can not start a server that
   * exceeds the max cluster size
   * Currently, the domain resource has a configured cluster with two managed 
   * servers and a dynamic cluster with MaxClusterSize set to 5. 
   * The sample script shouldn't start configured managed server 
   * config-cluster-server3 in configured cluster and managed-server-6 
   * in dynamic cluster.
   */
  @Order(14)
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
    assertTrue(verifyExecuteResult(result, regex),"The script shouldn't start a server that is beyond the limit");

    // verify that the script can not start a server in dynamic cluster that exceeds the max cluster size
    regex = ".*is outside the allowed range of";
    result =  assertDoesNotThrow(() ->
        executeLifecycleScript(START_SERVER_SCRIPT, SERVER_LIFECYCLE, dynServerName, "", false),
        String.format("Failed to run %s", START_SERVER_SCRIPT));
    assertTrue(verifyExecuteResult(result, regex),"The script shouldn't start a server that is beyond the limit");
  }

  /**
   * Refer JIRA OWLS-86251
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
  @Order(15)
  @Test
  @DisplayName("Manage dynamic cluster server in absence of Administration Server")
  public void testDynamicServerLifeCycleWithoutAdmin() {
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
           "/spec/adminServer/serverStartPolicy", "NEVER"),
           "Failed to patch adminServer's serverStartPolicy to NEVER");
      logger.info("Domain is patched to shutdown administration server");
      checkPodDeleted(adminServerPodName, domainUid, domainNamespace);
      logger.info("Administration server shutdown success");

      // verify the script can stop the server by reducing replica count
      assertDoesNotThrow(() ->
          executeLifecycleScript(STOP_SERVER_SCRIPT, 
                                 SERVER_LIFECYCLE, serverName, "", true),
          String.format("Failed to run %s", STOP_SERVER_SCRIPT));
      checkPodDeleted(serverPodName, domainUid, domainNamespace);
      logger.info("Shutdown [" + serverName + "] without admin server success");

      // Here the script increase the replica count by 1, but operator cannot 
      // start server in MSI mode as the server state (configuration) is 
      // lost while stopping the server in mii model.
      
      assertDoesNotThrow(() ->
          executeLifecycleScript(START_SERVER_SCRIPT, SERVER_LIFECYCLE, serverName, "", true),
          String.format("Failed to run %s", START_SERVER_SCRIPT));
      logger.info("Replica count increased without admin server");

      // Check if pod in init state
      // Here the server pd is created but does not goes into 1/1 state
      checkPodInitializing(serverPodName, domainUid, domainNamespace);
      logger.info("Server[" + serverName + "] pod is initialized");

      // (re)Start Start the admin
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

  /**
   * Refer JIRA OWLS-86251
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
  @Order(16)
  @Test
  @DisplayName("Manage configured cluster server in absence of Administration Server")
  public void testConfiguredServerLifeCycleWithoutAdmin() {
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
          executeLifecycleScript(STOP_SERVER_SCRIPT, 
                                 SERVER_LIFECYCLE, serverName, "", true),
          String.format("Failed to run %s", STOP_SERVER_SCRIPT));
      checkPodDeleted(serverPodName, domainUid, domainNamespace);
      logger.info("Shutdown [" + serverName + "] without admin server success");

      // Here the script increase the replica count by 1, but operator cannot 
      // start server in MSI mode as the server state (configuration) is 
      // lost while stopping the server in mii model.
      
      assertDoesNotThrow(() ->
          executeLifecycleScript(START_SERVER_SCRIPT, SERVER_LIFECYCLE, serverName, "", true),
          String.format("Failed to run %s", START_SERVER_SCRIPT));
      logger.info("Replica count increased without admin server");

      // Check if pod in init state
      // Here the server pd is created but does not goes into 1/1 state
      checkPodInitializing(serverPodName, domainUid, domainNamespace);
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

  /**
   * Restart the clustered managed server that is part of the dynamic cluster using the sample scripts
   * stopServer.sh and startServer.sh while keeping the replica count constant.
   * The test case verifies the fix for OWLS-87209 where scripts don't work when serverStartState
   * is set at the managed server level but serverStartPolicy is not set at the managed server level.
   */
  @Order(17)
  @Test
  @DisplayName("Restart the dynamic cluster managed server using sample scripts with constant replica count")
  public void testRestartingMSWithExplicitServerStartStateWhileKeepingReplicaConstant() {
    String serverName = managedServerNamePrefix + 1;
    String serverPodName = managedServerPrefix + 1;
    String keepReplicasConstant = "-k";

    // shut down the dynamic cluster managed server using the script stopServer.sh and keep replicas constant
    executeLifecycleScript(STOP_SERVER_SCRIPT, SERVER_LIFECYCLE, serverName, keepReplicasConstant);
    checkPodDeleted(serverPodName, domainUid, domainNamespace);
    logger.info("managed server " + serverName + " stopped successfully.");

    // start the dynamic cluster managed server using the script startServer.sh and keep replicas constant
    executeLifecycleScript(START_SERVER_SCRIPT, SERVER_LIFECYCLE, serverName, keepReplicasConstant);
    checkPodReadyAndServiceExists(serverPodName, domainUid, domainNamespace);
    logger.info("managed server " + serverName + " restarted successfully.");
  }

  /**
   * Restart the clustered managed server that is part of the dynamic cluster using the sample scripts
   * stopServer.sh and startServer.sh along with changing the replica count.
   * The test case verifies the fix for OWLS-87209 where scripts don't work when serverStartState
   * is set at the managed server level but serverStartPolicy is not set at the managed server level.
   */
  @Order(18)
  @Test
  @DisplayName("Restart the dynamic cluster managed server using sample scripts with varying replica count")
  public void testRestartingMSWithExplicitServerStartStateWhileVaryingReplicaCount() {
    String serverName = managedServerNamePrefix + 1;
    String serverPodName = managedServerPrefix + 1;
    String keepReplicasConstant = "-k";

    // shut down managed server3 using the script stopServer.sh and keep replicas constant
    executeLifecycleScript(STOP_SERVER_SCRIPT, SERVER_LIFECYCLE,
            managedServerNamePrefix + 3, keepReplicasConstant);
    checkPodDeleted(managedServerPrefix + 3, domainUid, domainNamespace);

    // shut down the dynamic cluster managed server using the script stopServer.sh and let replicas decrease
    executeLifecycleScript(STOP_SERVER_SCRIPT, SERVER_LIFECYCLE, serverName);
    checkPodDeleted(serverPodName, domainUid, domainNamespace);
    assertDoesNotThrow(() -> assertTrue(checkClusterReplicaCountMatches(CLUSTER_1, domainUid, domainNamespace, 1)));
    logger.info("managed server " + serverName + " stopped successfully.");

    // start the dynamic cluster managed server using the script startServer.sh and let replicas increase
    executeLifecycleScript(START_SERVER_SCRIPT, SERVER_LIFECYCLE, serverName);
    checkPodReadyAndServiceExists(serverPodName, domainUid, domainNamespace);
    assertDoesNotThrow(() -> assertTrue(checkClusterReplicaCountMatches(CLUSTER_1, domainUid, domainNamespace, 2)));
    logger.info("managed server " + serverName + " restarted successfully.");
  }

  private static void createDomainSecret(String secretName, String username, String password, String domNamespace) {
    Map<String, String> secretMap = new HashMap<>();
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
          String domNamespace, String adminSecretName,
          String encryptionSecretName,
          String configmapName) {
    List<String> securityList = new ArrayList<>();
    // create the domain CR
    Domain domain = new Domain()
            .apiVersion(DOMAIN_API_VERSION)
            .kind("Domain")
            .metadata(new V1ObjectMeta()
                    .name(ItServerStartPolicy.domainUid)
                    .namespace(domNamespace))
            .spec(new DomainSpec()
                    .allowReplicasBelowMinDynClusterSize(false)
                    .domainUid(ItServerStartPolicy.domainUid)
                    .domainHomeSourceType("FromModel")
                    .image(MII_BASIC_IMAGE_NAME + ":" + MII_BASIC_IMAGE_TAG)
                    .addImagePullSecretsItem(new V1LocalObjectReference()
                            .name(TestConstants.OCIR_SECRET_NAME))
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
                            .clusterName(CLUSTER_1)
                            .replicas(ItServerStartPolicy.replicaCount)
                            .serverStartPolicy("IF_NEEDED")
                            .serverStartState("RUNNING"))
                    .addClustersItem(new Cluster()
                            .clusterName(CLUSTER_2)
                            .replicas(ItServerStartPolicy.replicaCount)
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
            ItServerStartPolicy.domainUid, domNamespace);
    boolean domCreated = assertDoesNotThrow(() -> createDomainCustomResource(domain),
            String.format("Create domain custom resource failed with ApiException for %s in namespace %s",
                    ItServerStartPolicy.domainUid, domNamespace));
    assertTrue(domCreated, String.format("Create domain custom resource failed with ApiException "
                    + "for %s in namespace %s", ItServerStartPolicy.domainUid, domNamespace));
  }

  /*
   * Verify the server MBEAN configuration through rest API.
   * @param managedServer name of the managed server
   * @returns true if MBEAN is found otherwise false
   **/
  private boolean checkManagedServerConfiguration(String managedServer) {
    ExecResult result;
    int adminServiceNodePort
        = getServiceNodePort(domainNamespace, getExternalServicePodName(adminServerPodName), "default");
    StringBuffer checkCluster = new StringBuffer("status=$(curl --user weblogic:welcome1 ");
    checkCluster.append("http://").append(K8S_NODEPORT_HOST).append(":").append(adminServiceNodePort)
          .append("/management/tenant-monitoring/servers/")
          .append(managedServer)
          .append(" --silent --show-error ")
          .append(" -o /dev/null")
          .append(" -w %{http_code});")
          .append("echo ${status}");
    logger.info("checkManagedServerConfiguration: curl command {0}",
            new String(checkCluster));
    try {
      result = exec(new String(checkCluster), true);
    } catch (Exception ex) {
      logger.info("Exception in checkManagedServerConfiguration() {0}", ex);
      return false;
    }
    logger.info("checkManagedServerConfiguration: curl command returned {0}", result.toString());
    return result.stdout().equals("200");
  }

  // copy samples directory to a temporary location
  private static void setupSample() {
    assertDoesNotThrow(() -> {
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
