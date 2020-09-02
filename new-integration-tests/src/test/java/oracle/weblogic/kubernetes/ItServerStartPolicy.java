// Copyright (c) 2020, Oracle Corporation and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.weblogic.kubernetes;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import io.kubernetes.client.custom.V1Patch;
import io.kubernetes.client.openapi.ApiException;
import io.kubernetes.client.openapi.models.V1EnvVar;
import io.kubernetes.client.openapi.models.V1LocalObjectReference;
import io.kubernetes.client.openapi.models.V1ObjectMeta;
import io.kubernetes.client.openapi.models.V1Secret;
import io.kubernetes.client.openapi.models.V1SecretReference;
import io.kubernetes.client.openapi.models.V1ServiceAccount;
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
import oracle.weblogic.kubernetes.actions.impl.primitive.HelmParams;
import oracle.weblogic.kubernetes.annotations.IntegrationTest;
import oracle.weblogic.kubernetes.annotations.Namespaces;
import oracle.weblogic.kubernetes.logging.LoggingFacade;
import oracle.weblogic.kubernetes.utils.ExecResult;
import org.awaitility.core.ConditionFactory;
import org.joda.time.DateTime;
import org.junit.jupiter.api.AfterAll;
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
import static oracle.weblogic.kubernetes.TestConstants.REPO_SECRET_NAME;
import static oracle.weblogic.kubernetes.actions.TestActions.createDomainCustomResource;
import static oracle.weblogic.kubernetes.actions.TestActions.createSecret;
import static oracle.weblogic.kubernetes.actions.TestActions.deleteDomainCustomResource;
import static oracle.weblogic.kubernetes.actions.TestActions.getServiceNodePort;
import static oracle.weblogic.kubernetes.actions.TestActions.patchDomainCustomResource;
import static oracle.weblogic.kubernetes.assertions.TestAssertions.domainExists;
import static oracle.weblogic.kubernetes.assertions.TestAssertions.podDoesNotExist;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.checkPodReady;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.checkServiceExists;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.createConfigMapAndVerify;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.createDockerRegistrySecret;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.getPodCreationTime;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.installAndVerifyOperator;
import static oracle.weblogic.kubernetes.utils.ExecCommand.exec;
import static oracle.weblogic.kubernetes.utils.ThreadSafeLogger.getLogger;
import static org.awaitility.Awaitility.with;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
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
 *
 * <p>testDomainRestart
 *  Restart the entire domain by changing serverStartPolicy 
 *   IF_NEEDED->NEVER->ADMIN_ONLY->IF_NEEDED
 *
 * <p>testConfigClusterRestart
 *  Restart all servers in configured cluster by changing serverStartPolicy 
 *   IF_NEEDED->NEVER->IF_NEEDED
 *
 * <p>testDynamicClusterRestart
 *  Restart all servers in dynamic cluster by changing serverStartPolicy 
 *   IF_NEEDED->NEVER->IF_NEEDED
 *
 * <p>testStartAlwaysConfigClusterManaged
 *  Restart a server in configured cluster (beyond replica count) 
 *   IF_NEEDED->ALWAYS->IF_NEEDED
 *
 * <p>testStartAlwaysDynamicClusterManaged
 *  Restart a server in dynamic cluster (beyond replica count) 
 *   IF_NEEDED->ALWAYS->IF_NEEDED
 *
 * <p>testStopNeverConfigClusterManaged
 *  Stop a server in configured cluster by changing serverStartPolicy
 *   IF_NEEDED->ALWAYS->NEVER
 *
 * <p>testStopNeverDynamicClusterManaged
 *  Stop a server in dynamic cluster by changing serverStartPolicy
 *   IF_NEEDED->ALWAYS->NEVER
 *
 * <p>testStopConfigClusterReplicaManaged
 *  Change the serverStartPolicy of a running managed server (say ms1) in config
 *  cluster to NEVER. 
 *  Make sure next managed server (say ms2) is scheduled to run to maintain the 
 *  replica count while the running managed server ms1 goes down.
 *  Change the serverStartPolicy of server ms1 to IF_NEEDED.
 *  Make sure server ms2 goes down and server ms1 is re-scheduled to maintain 
 *  the replica count
 *
 * <p>testStopDynamicClusterReplicaManaged
 *  Change the serverStartPolicy of a running managed server (say ms1) in a 
 *  dynamic cluster to NEVER. 
 *  Make sure next managed server (say ms2) is scheduled to run to maintain the 
 *  replica count while the running managed server ms1 goes down.
 *  Change the serverStartPolicy of server ms1 to IF_NEEDED.
 *  Make sure server ms2 goes down and server ms1 is re-scheduled to maintain 
 *  the replica count
 *
 * <p>testStandaloneManagedRestartIfNeeded
 *  Restart standalone server changing serverStartPolicy 
 *   IF_NEEDED->NEVER->IF_NEEDED
 *
 * <p>testStandaloneManagedRestartAlways
 *  Restart standalone server changing serverStartPolicy 
 *   IF_NEEDED->NEVER->ALWAYS
 */

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DisplayName("ServerStartPolicy attribute in different scope in a mii domain")
@IntegrationTest
class ItServerStartPolicy {

  private static HelmParams opHelmParams = null;
  private static V1ServiceAccount serviceAccount = null;
  private String serviceAccountName = null;
  private static String opNamespace = null;
  private static String operatorImage = null;
  private static String domainNamespace = null;
  private static ConditionFactory withStandardRetryPolicy = null;
  private static String dockerConfigJson = "";

  private static int replicaCount = 1;
  private static final String domainUid = "mii-start-policy";
  private StringBuffer curlString = null;

  private StringBuffer checkCluster = null;
  private V1Patch patch = null;

  private static Map<String, Object> secretNameMap;

  private final String adminServerPodName = domainUid + "-admin-server";
  private final String managedServerPrefix = domainUid + "-managed-server";
  private static LoggingFacade logger = null;

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
    assertDoesNotThrow(() -> createDockerRegistrySecret(domainNamespace),
          String.format("createSecret failed for %s", REPO_SECRET_NAME));

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
        REPO_SECRET_NAME, encryptionSecretName,
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
  }

  /**
   * Verify all server pods are running.
   * Verify k8s services for all servers are created.
   */
  @BeforeEach
  public void beforeEach() {

    logger.info("Check admin service {0} is created in namespace {1}",
        adminServerPodName, domainNamespace);
    checkServiceExists(adminServerPodName, domainNamespace);

    logger.info("Wait for admin server pod {0} to be ready in namespace {1}",
        adminServerPodName, domainNamespace);
    checkPodReady(adminServerPodName, domainUid, domainNamespace);

    for (int i = 1; i <= replicaCount; i++) {
      logger.info("Check managed server service {0} is created in namespace {1}",
          managedServerPrefix + i, domainNamespace);
      checkServiceExists(managedServerPrefix + i, domainNamespace);
    }

    // Check managed server pods are ready
    for (int i = 1; i <= replicaCount; i++) {
      logger.info("Wait for managed server pod {0} to be ready in namespace {1}",
          managedServerPrefix + i, domainNamespace);
      checkPodReady(managedServerPrefix + i, domainUid, domainNamespace);
    }

    // Check configured cluster configuration is available 
    boolean isServerConfigured = 
        checkManagedServerConfiguration("config-cluster-server1");
    assertTrue(isServerConfigured, 
        "Could not find managed server from configured cluster");
    logger.info("Found managed server from configured cluster");

    // Check standalone server configuration is available 
    isServerConfigured = 
       checkManagedServerConfiguration("standalone-managed");
    logger.info("Found non-cluster managed server configuration");
  }

  /**
   * Stop the Administration server by patching the resource definition with 
   *  spec/adminServer/serverStartPolicy set to NEVER.
   * Make sure that Only the Administration server is stopped. 
   * Restart the Administration server by patching the resource definition with 
   *  spec/adminServer/serverStartPolicy set to IF_NEEDED.
   * Make sure that the Administration server is in RUNNING state. 
   */
  @Test
  @DisplayName("Restart the Administration server with serverStartPolicy")
  public void testAdminServerRestart() {

    String configServerPodName = domainUid + "-config-cluster-server1";
    String dynamicServerPodName = domainUid + "-managed-server1";
    DateTime dynTs = getPodCreationTime(domainNamespace, dynamicServerPodName);
    DateTime cfgTs = getPodCreationTime(domainNamespace, configServerPodName);
    StringBuffer patchStr = null;
    patchStr = new StringBuffer("[{");
    patchStr.append("\"op\": \"replace\",")
        .append(" \"path\": \"/spec/adminServer/serverStartPolicy\",")
        .append(" \"value\":  \"NEVER\"")
        .append(" }]");
    logger.info("AdminServer shutdown patch string: {0}", patchStr);

    patch = new V1Patch(new String(patchStr));
    boolean crdPatched = assertDoesNotThrow(() ->
            patchDomainCustomResource(domainUid, domainNamespace, patch, "application/json-patch+json"),
        "patchDomainCustomResource(adminShutdown) failed");

    assertTrue(crdPatched, "patchDomainCustomResource(adminShutdown) failed");
   
    checkPodDeleted(adminServerPodName, domainUid, domainNamespace);
    logger.info("AdminServer shutdown success");

    logger.info("Check managed server pods are not affected");
    DateTime dynTs2 = getPodCreationTime(domainNamespace, dynamicServerPodName);
    DateTime cfgTs2 = getPodCreationTime(domainNamespace, configServerPodName);

    assertTrue(
        dynTs2.withTimeAtStartOfDay().isEqual(dynTs.withTimeAtStartOfDay()), 
        "Dynamic managed server pod creation time must be same");
    assertTrue(
        cfgTs2.withTimeAtStartOfDay().isEqual(cfgTs.withTimeAtStartOfDay()), 
        "Configured managed server pod creation time must be same");

    patchStr = null;
    patchStr = new StringBuffer("[{");
    patchStr.append("\"op\": \"replace\",")
        .append(" \"path\": \"/spec/adminServer/serverStartPolicy\",")
        .append(" \"value\":  \"IF_NEEDED\"")
        .append(" }]");
    logger.info("AdminServer restart patch string: {0}", patchStr);

    patch = new V1Patch(new String(patchStr));
    crdPatched = false;
    crdPatched = assertDoesNotThrow(() ->
            patchDomainCustomResource(domainUid, domainNamespace, patch, "application/json-patch+json"),
        "patchDomainCustomResource(restart) failed");

    assertTrue(crdPatched, "patchDomainCustomResource(restart) failed");
    logger.info("Check admin service {0} is created in namespace {1}",
        adminServerPodName, domainNamespace);
    checkServiceExists(adminServerPodName, domainNamespace);
    logger.info("Wait for admin server pod {0} to be ready in namespace {1}",
        adminServerPodName, domainNamespace);
    checkPodReady(adminServerPodName, domainUid, domainNamespace);
    logger.info("AdminServer restart success");
  }

  /**
   * Stop a configured cluster by patching the resource definition with 
   *  spec/clusters/1/serverStartPolicy set to NEVER.
   * Make sure that only server(s) in the configured cluster are stopped. 
   * Make sure that server(s) in the dynamic cluster are in RUNNING state. 
   * Restart the cluster by patching the resource definition with 
   *  spec/clusters/1/serverStartPolicy set to IF_NEEDED.
   * Make sure that servers in the configured cluster are in RUNNING state. 
   */
  @Test
  @DisplayName("Restart the configured cluster with serverStartPolicy")
  public void testConfigClusterRestart() {

    String configServerPodName = domainUid + "-config-cluster-server1";
    String dynamicServerPodName = domainUid + "-managed-server1";

    DateTime dynTs = getPodCreationTime(domainNamespace, dynamicServerPodName);

    checkServiceExists(configServerPodName, domainNamespace);
    checkPodReady(configServerPodName, domainUid, domainNamespace);
    logger.info("(BeforePatch) configured cluster managed server is RUNNING");

    StringBuffer patchStr = null;
    patchStr = new StringBuffer("[{");
    patchStr.append("\"op\": \"replace\",")
        .append(" \"path\": \"/spec/clusters/1/serverStartPolicy\",")
        .append(" \"value\":  \"NEVER\"")
        .append(" }]");
    logger.info("Configured cluster shutdown patch string: {0}", patchStr);

    patch = new V1Patch(new String(patchStr));
    boolean crdPatched = assertDoesNotThrow(() ->
            patchDomainCustomResource(domainUid, domainNamespace, patch, "application/json-patch+json"),
        "patchDomainCustomResource(clusterShutdown) failed");

    assertTrue(crdPatched, "patchDomainCustomResource(configclusterShutdown) failed");
   
    checkPodDeleted(configServerPodName, domainUid, domainNamespace);
    logger.info("Config cluster shutdown success");

    // check managed server from other cluster are not affected
    logger.info("Check dynamic managed server pods are not affected");
    DateTime dynTs2 = getPodCreationTime(domainNamespace, dynamicServerPodName);
    assertTrue(
        dynTs2.withTimeAtStartOfDay().isEqual(dynTs.withTimeAtStartOfDay()),
        "Dynamic managed server pod creation time must be same");

    patchStr = null;
    patchStr = new StringBuffer("[{");
    patchStr.append("\"op\": \"replace\",")
        .append(" \"path\": \"/spec/clusters/1/serverStartPolicy\",")
        .append(" \"value\":  \"IF_NEEDED\"")
        .append(" }]");
    logger.info("Cluster restart patch string: {0}", patchStr);

    patch = new V1Patch(new String(patchStr));
    crdPatched = false;
    crdPatched = assertDoesNotThrow(() ->
            patchDomainCustomResource(domainUid, domainNamespace, patch, "application/json-patch+json"),
        "patchDomainCustomResource(clusterRestart) failed");

    assertTrue(crdPatched, "patchDomainCustomResource(clusterRestart) failed");
    logger.info("Check configured cluster managed service {0} is created in namespace {1}",
        configServerPodName, domainNamespace);
    checkServiceExists(configServerPodName, domainNamespace);
    logger.info("Wait for cluster server pod {0} to be ready in namespace {1}",
        configServerPodName, domainNamespace);
    checkPodReady(configServerPodName, domainUid, domainNamespace);
    logger.info("Configured cluster restart success");
  }

  /**
   * Stop a dynamic cluster by patching the resource definition with 
   *  spec/clusters/1/serverStartPolicy set to NEVER.
   * Make sure that only servers in the dynamic cluster are stopped. 
   * Make sure that only servers in the configured cluster are in the 
   * RUNNING state. 
   * Restart the dynamic cluster by patching the resource definition with 
   *  spec/clusters/1/serverStartPolicy set to IF_NEEDED.
   * Make sure that servers in the dynamic cluster are in RUNNING state again. 
   */
  @Test
  @DisplayName("Restart the dynamic cluster with serverStartPolicy")
  public void testDynamicClusterRestart() {

    String dynamicServerPodName = domainUid + "-managed-server1";
    String configServerPodName = domainUid + "-config-cluster-server1";

    DateTime cfgTs = getPodCreationTime(domainNamespace, configServerPodName);

    checkServiceExists(dynamicServerPodName, domainNamespace);
    checkPodReady(dynamicServerPodName, domainUid, domainNamespace);
    logger.info("(BeforePatch) dynamic cluster Managed Server is RUNNING");

    StringBuffer patchStr = null;
    patchStr = new StringBuffer("[{");
    patchStr.append("\"op\": \"replace\",")
        .append(" \"path\": \"/spec/clusters/0/serverStartPolicy\",")
        .append(" \"value\":  \"NEVER\"")
        .append(" }]");
    logger.info("Dynamic cluster shutdown patch string: {0}", patchStr);

    patch = new V1Patch(new String(patchStr));
    boolean crdPatched = assertDoesNotThrow(() ->
            patchDomainCustomResource(domainUid, domainNamespace, patch, "application/json-patch+json"),
        "patchDomainCustomResource(dynamicClusterShutdown) failed");
    assertTrue(crdPatched, "patchDomainCustomResource(dynamicClusterShutdown) failed");
   
    checkPodDeleted(dynamicServerPodName, domainUid, domainNamespace);
    logger.info("Dynamic cluster shutdown success");

    // check managed server from other cluster are not affected
    logger.info("Check configured managed server pods are not affected");
    DateTime cfgTs2 = getPodCreationTime(domainNamespace, configServerPodName);
    assertTrue(
        cfgTs2.withTimeAtStartOfDay().isEqual(cfgTs.withTimeAtStartOfDay()),
        "Configured managed server pod creation time must be same");

    patchStr = null;
    patchStr = new StringBuffer("[{");
    patchStr.append("\"op\": \"replace\",")
        .append(" \"path\": \"/spec/clusters/0/serverStartPolicy\",")
        .append(" \"value\":  \"IF_NEEDED\"")
        .append(" }]");
    logger.info("Dynamic cluster restart patch string: {0}", patchStr);

    patch = new V1Patch(new String(patchStr));
    crdPatched = false;
    crdPatched = assertDoesNotThrow(() ->
            patchDomainCustomResource(domainUid, domainNamespace, patch, "application/json-patch+json"),
        "patchDomainCustomResource(dynamicClusterRestart) failed");
    assertTrue(crdPatched, "patchDomainCustomResource(dynamicClusterRestart) failed");
    logger.info("Wait for dynamic cluster server pod {0} to be ready in namespace {1}", domainNamespace);
    checkServiceExists(dynamicServerPodName, domainNamespace);
    checkPodReady(dynamicServerPodName, domainUid, domainNamespace);

    logger.info("Dynamic cluster restart success");
  }

  /**
   * Stop the entire domain by patching the resource definition with 
   *  spec/serverStartPolicy set to NEVER.
   * Make sure that all servers in the domain are stopped. 
   * Restart the domain by patching the resource definition with 
   *  spec/serverStartPolicy set to ADMIN_ONLY.
   * Make sure that ONLY Admin Server is in RUNNING state. 
   * Restart the domain by patching the resource definition with 
   *  spec/serverStartPolicy set to IF_NEEDED.
   * Make sure that all servers in the domain are in RUNNING state. 
   */
  @Test
  @DisplayName("Restart the Domain with serverStartPolicy")
  public void testDomainRestart() {

    String configServerPodName = domainUid + "-config-cluster-server1";
    String standaloneServerPodName = domainUid + "-standalone-managed";

    StringBuffer patchStr = null;
    patchStr = new StringBuffer("[{");
    patchStr.append("\"op\": \"replace\",")
        .append(" \"path\": \"/spec/serverStartPolicy\",")
        .append(" \"value\":  \"NEVER\"")
        .append(" }]");
    logger.info("Domain shutdown patch string: {0}", patchStr);

    patch = new V1Patch(new String(patchStr));
    boolean crdPatched = assertDoesNotThrow(() ->
            patchDomainCustomResource(domainUid, domainNamespace, patch, "application/json-patch+json"),
        "patchDomainCustomResource(domainShutdown) failed");
    assertTrue(crdPatched, "patchDomainCustomResource(domainShutdown) failed");
    logger.info("!!! Domain Resource patched for shutdown (NEVER) !!!");
   
    // make sure all the server pods are removed after patch
    checkPodDeleted(adminServerPodName, domainUid, domainNamespace);
    for (int i = 1; i <= replicaCount; i++) {
      checkPodDeleted(managedServerPrefix + i, domainUid, domainNamespace);
    }
    checkPodDeleted(configServerPodName, domainUid, domainNamespace);
    checkPodDeleted(standaloneServerPodName, domainUid, domainNamespace);
    logger.info("!!! Domain shutdown (NEVER) success !!!");

    // Patch the Domain with serverStartPolicy set to ADMIN_ONLY
    // Here only Admin server pod should come up
    patchStr = null;
    patchStr = new StringBuffer("[{");
    patchStr.append("\"op\": \"replace\",")
        .append(" \"path\": \"/spec/serverStartPolicy\",")
        .append(" \"value\":  \"ADMIN_ONLY\"")
        .append(" }]");
    logger.info("Domain restart(adminonly) patch string: {0}", patchStr);

    patch = new V1Patch(new String(patchStr));
    crdPatched = false;
    crdPatched = assertDoesNotThrow(() ->
            patchDomainCustomResource(domainUid, domainNamespace, patch, "application/json-patch+json"),
        "patchDomainCustomResource(adminonly) failed");

    assertTrue(crdPatched, "patchDomainCustomResource(adminonly) failed");
    logger.info("!!! Domain Resource patched for shutdown (ADMIN_ONLY) !!!");

    checkServiceExists(adminServerPodName, domainNamespace);
    checkPodReady(adminServerPodName, domainUid, domainNamespace);

    // make sure all other managed server pods are not provisioned 
    for (int i = 1; i <= replicaCount; i++) {
      checkPodDeleted(managedServerPrefix + i, domainUid, domainNamespace);
    }
    checkPodDeleted(configServerPodName, domainUid, domainNamespace);
    checkPodDeleted(standaloneServerPodName, domainUid, domainNamespace);

    logger.info("!!! Domain restart (ADMIN_ONLY) success !!!");

    // Patch the Domain with serverStartPolicy set to IF_NEEDED
    // Here all the Servers should come up
    patchStr = null;
    patchStr = new StringBuffer("[{");
    patchStr.append("\"op\": \"replace\",")
        .append(" \"path\": \"/spec/serverStartPolicy\",")
        .append(" \"value\":  \"IF_NEEDED\"")
        .append(" }]");
    logger.info("Domain restart(if_needed) patch string: {0}", patchStr);

    patch = new V1Patch(new String(patchStr));
    crdPatched = false;
    crdPatched = assertDoesNotThrow(() ->
            patchDomainCustomResource(domainUid, domainNamespace, patch, "application/json-patch+json"),
        "patchDomainCustomResource(if_needed) failed");
    assertTrue(crdPatched, "patchDomainCustomResource(if_needed) failed");
    logger.info("!!! Domain Resource patched for shutdown (IF_NEEDED) !!!");
    
    // check dynamic managed server pods are ready
    for (int i = 1; i <= replicaCount; i++) {
      checkServiceExists(managedServerPrefix + i, domainNamespace);
      checkPodReady(managedServerPrefix + i, domainUid, domainNamespace);
    }
    checkPodReady(configServerPodName, domainUid, domainNamespace);
    checkPodReady(standaloneServerPodName, domainUid, domainNamespace);
    logger.info("!!! Domain restart (IF_NEEDED) success !!!");
  }

  /**
   * Add a second managed server (config-cluster-server2) in a configured 
   * cluster with serverStartPolicy IF_NEEDED. 
   * Initially, the server will not come up since the replica count is set to 1.
   * Update the serverStartPolicy for config-cluster-server2 to ALWAYS
   * by patching the resource definition with 
   *  spec/managedServers/1/serverStartPolicy set to ALWAYS.
   * Make sure that managed server config-cluster-server2 is up and running
   * Stop the managed server by patching the resource definition 
   *   with spec/managedServers/1/serverStartPolicy set to IF_NEEDED.
   * Make sure the specified managed server is stopped as per replica count.
   */

  @Test
  @DisplayName("Start/stop config cluster managed server by updating serverStartPolicy to ALWAYS/IF_NEEDED")
  public void testStartAlwaysConfigClusterManaged() {
    String serverPodName = domainUid + "-config-cluster-server2";

    // Make sure that managed server is not running 
    checkPodDeleted(serverPodName, domainUid, domainNamespace);

    StringBuffer patchStr = null;
    patchStr = new StringBuffer("[{");
    patchStr.append("\"op\": \"replace\",")
        .append(" \"path\": \"/spec/managedServers/1/serverStartPolicy\",")
        .append(" \"value\":  \"ALWAYS\"")
        .append(" }]");
    logger.info("Config cluster managed server patch string: {0}", patchStr);

    patch = new V1Patch(new String(patchStr));
    boolean crdPatched = assertDoesNotThrow(() ->
            patchDomainCustomResource(domainUid, domainNamespace, patch, "application/json-patch+json"),
        "patchDomainCustomResource(managedShutdown) failed");
    assertTrue(crdPatched, "patchDomainCustomResource(managedShutdown) failed");
    logger.info("!!! Domain Resource patched for config cluster managed server !!!");
    checkServiceExists(serverPodName, domainNamespace);
    checkPodReady(serverPodName, domainUid, domainNamespace);
    logger.info("Configured cluster managed Server is RUNNING");

    patchStr = null;
    patchStr = new StringBuffer("[{");
    patchStr.append("\"op\": \"replace\",")
        .append(" \"path\": \"/spec/managedServers/1/serverStartPolicy\",")
        .append(" \"value\":  \"IF_NEEDED\"")
        .append(" }]");
    logger.info("Managed restart patch string: {0}", patchStr);

    patch = new V1Patch(new String(patchStr));
    crdPatched = false;
    crdPatched = assertDoesNotThrow(() ->
            patchDomainCustomResource(domainUid, domainNamespace, patch, "application/json-patch+json"),
        "patchDomainCustomResource(managedRestart) failed");

    assertTrue(crdPatched, "patchDomainCustomResource(managedRestart) failed");
    logger.info("!!! Domain resource patched for config cluster managed server shutdown !!!");

    logger.info("Wait for managed server ${0} to be shutdown", serverPodName);
    checkPodDeleted(serverPodName, domainUid, domainNamespace);
    logger.info("Config cluster managed server shutdown success");
  }

  /**
   * Add a second managed server (config-cluster-server2) in a configured 
   * cluster with serverStartPolicy IF_NEEDED. 
   * Initially, the server will not come up since the replica count is set to 1.
   * Update the serverStartPolicy for config-cluster-server2 to ALWAYS
   * by patching the resource definition with 
   *  spec/managedServers/1/serverStartPolicy set to ALWAYS.
   * Make sure that managed server config-cluster-server2 is up and running
   * Stop the managed server by patching the resource definition 
   *   with spec/managedServers/1/serverStartPolicy set to NEVER.
   */

  @Test
  @DisplayName("Start/stop config cluster managed server by updating serverStartPolicy to ALWAYS/NEVER")
  public void testStopNeverConfigClusterManaged() {
    String serverPodName = domainUid + "-config-cluster-server2";

    // Make sure that managed server is not running 
    checkPodDeleted(serverPodName, domainUid, domainNamespace);

    StringBuffer patchStr = null;
    patchStr = new StringBuffer("[{");
    patchStr.append("\"op\": \"replace\",")
        .append(" \"path\": \"/spec/managedServers/1/serverStartPolicy\",")
        .append(" \"value\":  \"ALWAYS\"")
        .append(" }]");
    logger.info("Config cluster managed server patch string: {0}", patchStr);

    patch = new V1Patch(new String(patchStr));
    boolean crdPatched = assertDoesNotThrow(() ->
            patchDomainCustomResource(domainUid, domainNamespace, patch, "application/json-patch+json"),
        "patchDomainCustomResource(managedShutdown) failed");
    assertTrue(crdPatched, "patchDomainCustomResource(managedShutdown) failed");
    logger.info("!!! Domain Resource patched for config cluster managed server !!!");
    checkServiceExists(serverPodName, domainNamespace);
    checkPodReady(serverPodName, domainUid, domainNamespace);
    logger.info("Configured cluster managed Server is RUNNING");

    patchStr = null;
    patchStr = new StringBuffer("[{");
    patchStr.append("\"op\": \"replace\",")
        .append(" \"path\": \"/spec/managedServers/1/serverStartPolicy\",")
        .append(" \"value\":  \"NEVER\"")
        .append(" }]");
    logger.info("Managed restart patch string: {0}", patchStr);

    patch = new V1Patch(new String(patchStr));
    crdPatched = false;
    crdPatched = assertDoesNotThrow(() ->
            patchDomainCustomResource(domainUid, domainNamespace, patch, "application/json-patch+json"),
        "patchDomainCustomResource(managedRestart) failed");

    assertTrue(crdPatched, "patchDomainCustomResource(managedRestart) failed");
    logger.info("!!! Domain resource patched for config cluster managed server shutdown !!!");

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
   * Stop the managed server by patching the resource definition 
   *   with spec/managedServers/2/serverStartPolicy set to IF_NEEDED.
   * Make sure the specified managed server is stopped as per replica count.
   */

  @Test
  @DisplayName("Start/stop dynamic cluster managed server by updating serverStartPolicy to ALWAYS/IF_NEEDED")
  public void testStartAlwaysDynamicClusterManaged() {
    String serverPodName = domainUid + "-managed-server2";

    // Make sure that managed server is not running 
    checkPodDeleted(serverPodName, domainUid, domainNamespace);

    StringBuffer patchStr = null;
    patchStr = new StringBuffer("[{");
    patchStr.append("\"op\": \"replace\",")
        .append(" \"path\": \"/spec/managedServers/2/serverStartPolicy\",")
        .append(" \"value\":  \"ALWAYS\"")
        .append(" }]");
    logger.info("Config cluster managed server patch string: {0}", patchStr);

    patch = new V1Patch(new String(patchStr));
    boolean crdPatched = assertDoesNotThrow(() ->
            patchDomainCustomResource(domainUid, domainNamespace, patch, "application/json-patch+json"),
        "patchDomainCustomResource(managedShutdown) failed");
    assertTrue(crdPatched, "patchDomainCustomResource(managedShutdown) failed");
    logger.info("!!! Domain Resource patched for dynamic cluster managed server !!!");
    checkServiceExists(serverPodName, domainNamespace);
    checkPodReady(serverPodName, domainUid, domainNamespace);
    logger.info("Config cluster Managed Server is RUNNING");

    patchStr = null;
    patchStr = new StringBuffer("[{");
    patchStr.append("\"op\": \"replace\",")
        .append(" \"path\": \"/spec/managedServers/2/serverStartPolicy\",")
        .append(" \"value\":  \"IF_NEEDED\"")
        .append(" }]");
    logger.info("Managed restart patch string: {0}", patchStr);

    patch = new V1Patch(new String(patchStr));
    crdPatched = false;
    crdPatched = assertDoesNotThrow(() ->
            patchDomainCustomResource(domainUid, domainNamespace, patch, "application/json-patch+json"),
        "patchDomainCustomResource(managedRestart) failed");

    assertTrue(crdPatched, "patchDomainCustomResource(managedRestart) failed");
    logger.info("!!! Domain resource patched for dynamic cluster managed server shutdown !!!");

    logger.info("Wait for managed server ${0} to be shutdown", serverPodName);
    checkPodDeleted(serverPodName, domainUid, domainNamespace);
    logger.info("Dynamic cluster managed server shutdown success");
  }

  /**
   * Add managed server configuration (managed-server2) to CRD in a dynamic 
   * cluster with ServerStartPolicy IF_NEEDED. 
   * So initially, the server will not come up since replica count is set to 1.
   * Update the ServerStartPolicy for managed-server2 to ALWAYS
   * by patching the resource definition with 
   *  spec/managedServers/2/serverStartPolicy set to ALWAYS.
   * Make sure that managed server managed-server2 is up and running
   * Stop the managed server by patching the resource definition 
   *   with spec/managedServers/2/serverStartPolicy set to NEVER
   */

  @Test
  @DisplayName("Start/stop dynamic cluster managed server by updating serverStartPolicy to ALWAYS/NEVER")
  public void testStopNeverDynamicClusterManaged() {
    String serverPodName = domainUid + "-managed-server2";

    // Make sure that managed server is not running 
    checkPodDeleted(serverPodName, domainUid, domainNamespace);

    StringBuffer patchStr = null;
    patchStr = new StringBuffer("[{");
    patchStr.append("\"op\": \"replace\",")
        .append(" \"path\": \"/spec/managedServers/2/serverStartPolicy\",")
        .append(" \"value\":  \"ALWAYS\"")
        .append(" }]");
    logger.info("Config cluster managed server patch string: {0}", patchStr);

    patch = new V1Patch(new String(patchStr));
    boolean crdPatched = assertDoesNotThrow(() ->
            patchDomainCustomResource(domainUid, domainNamespace, patch, "application/json-patch+json"),
        "patchDomainCustomResource(managedShutdown) failed");
    assertTrue(crdPatched, "patchDomainCustomResource(managedShutdown) failed");
    logger.info("!!! Domain Resource patched for dynamic cluster managed server !!!");
    checkServiceExists(serverPodName, domainNamespace);
    checkPodReady(serverPodName, domainUid, domainNamespace);
    logger.info("Config cluster Managed Server is RUNNING");

    patchStr = null;
    patchStr = new StringBuffer("[{");
    patchStr.append("\"op\": \"replace\",")
        .append(" \"path\": \"/spec/managedServers/2/serverStartPolicy\",")
        .append(" \"value\":  \"NEVER\"")
        .append(" }]");
    logger.info("Managed restart patch string: {0}", patchStr);

    patch = new V1Patch(new String(patchStr));
    crdPatched = false;
    crdPatched = assertDoesNotThrow(() ->
            patchDomainCustomResource(domainUid, domainNamespace, patch, "application/json-patch+json"),
        "patchDomainCustomResource(managedRestart) failed");

    assertTrue(crdPatched, "patchDomainCustomResource(managedRestart) failed");
    logger.info("!!! Domain resource patched for dynamic cluster managed server shutdown !!!");

    logger.info("Wait for managed server ${0} to be shutdown", serverPodName);
    checkPodDeleted(serverPodName, domainUid, domainNamespace);
    logger.info("Dynamic cluster managed server shutdown success");
  }

  /**
   * Add the first managed server (config-cluster-server1) in a configured 
   * cluster with serverStartPolicy IF_NEEDED. 
   * Initially, the server will come up since the replica count is set to 1.
   * (a) Update the serverStartPolicy for config-cluster-server1 to NEVER
   *      by patching the resource definition with 
   *        spec/managedServers/3/serverStartPolicy set to NEVER.
   *     Make sure that managed server config-cluster-server1 is shutdown.
   *     Make sure that managed server config-cluster-server2 comes up
   *       to maintain the replica count of 1.
   * (b) Update the serverStartPolicy for config-cluster-server1 to IF_NEEDED
   *       by patching the resource definition with 
   *       spec/managedServers/3/serverStartPolicy set to IF_NEEDED.
   *     Make sure that managed server config-cluster-server2 is shutdown.
   *     Make sure that managed server config-cluster-server1 comes up
   *       to maintain the replica count of 1.
   */
  @Test
  @DisplayName("Stop a running config cluster managed server and verify the replica count is maintained")
  public void testStopConfigClusterReplicaManaged() {
    String serverPodName = domainUid + "-config-cluster-server1";
    String serverPodName2 = domainUid + "-config-cluster-server2";

    // Make sure that managed server(2) is not running 
    checkPodDeleted(serverPodName2, domainUid, domainNamespace);

    // Patch(Shutdown) the config-cluster-server1 
    StringBuffer patchStr = null;
    patchStr = new StringBuffer("[{");
    patchStr.append("\"op\": \"replace\",")
        .append(" \"path\": \"/spec/managedServers/3/serverStartPolicy\",")
        .append(" \"value\":  \"NEVER\"")
        .append(" }]");
    logger.info("Config cluster managed server patch string: {0}", patchStr);

    patch = new V1Patch(new String(patchStr));
    boolean crdPatched = assertDoesNotThrow(() ->
            patchDomainCustomResource(domainUid, domainNamespace, patch, "application/json-patch+json"),
        "patchDomainCustomResource(managedShutdown) failed");
    assertTrue(crdPatched, "patchDomainCustomResource(managedShutdown) failed");
    logger.info("!!! Domain Resource patched for config cluster managed server !!!");

    // Make sure config-cluster-server1 is deleted 
    checkPodDeleted(serverPodName, domainUid, domainNamespace);
    // Make sure  config-cluster-server2 is started 
    checkServiceExists(serverPodName2, domainNamespace);
    checkPodReady(serverPodName2, domainUid, domainNamespace);
    logger.info("Configured cluster managed Server(2) is RUNNING");

    // Patch(start) the config-cluster-server1 
    patchStr = null;
    patchStr = new StringBuffer("[{");
    patchStr.append("\"op\": \"replace\",")
        .append(" \"path\": \"/spec/managedServers/3/serverStartPolicy\",")
        .append(" \"value\":  \"IF_NEEDED\"")
        .append(" }]");
    logger.info("Managed restart patch string: {0}", patchStr);

    patch = new V1Patch(new String(patchStr));
    crdPatched = false;
    crdPatched = assertDoesNotThrow(() ->
            patchDomainCustomResource(domainUid, domainNamespace, patch, "application/json-patch+json"),
        "patchDomainCustomResource(managedRestart) failed");

    assertTrue(crdPatched, "patchDomainCustomResource(managedRestart) failed");
    logger.info("!!! Domain resource patched for config cluster managed server shutdown !!!");

    // Make sure config-cluster-server2 is deleted 
    checkPodDeleted(serverPodName2, domainUid, domainNamespace);

    // Make sure config-cluster-server1 is re-started
    checkServiceExists(serverPodName, domainNamespace);
    checkPodReady(serverPodName, domainUid, domainNamespace);
  }

  /**
   * Add the first managed server (managed-server1) in a dynamic 
   * cluster with serverStartPolicy IF_NEEDED. 
   * Initially, the server will come up since the replica count is set to 1.
   * (a) Update the serverStartPolicy for managed-server1 to NEVER
   *      by patching the resource definition with 
   *        spec/managedServers/4/serverStartPolicy set to NEVER.
   *     Make sure that managed server managed-server1 is shutdown.
   *     Make sure that managed server managed-server2 comes up
   *       to maintain the replica count of 1.
   * (b) Update the serverStartPolicy for managed-server1 to IF_NEEDED
   *       by patching the resource definition with 
   *       spec/managedServers/4/serverStartPolicy set to IF_NEEDED.
   *     Make sure that managed server managed-server2 is shutdown.
   *     Make sure that managed server managed-server1 comes up
   *       to maintain the replica count of 1.
   */
  @Test
  @DisplayName("Stop a running dynamic cluster managed server and verify the replica count ")
  public void testStopDynamicClusterReplicaManaged() {
    String serverPodName = domainUid + "-managed-server1";
    String serverPodName2 = domainUid + "-managed-server2";

    // Make sure that managed server(2) is not running 
    checkPodDeleted(serverPodName2, domainUid, domainNamespace);

    // Patch(Shutdown) the managed-server1 
    StringBuffer patchStr = null;
    patchStr = new StringBuffer("[{");
    patchStr.append("\"op\": \"replace\",")
        .append(" \"path\": \"/spec/managedServers/4/serverStartPolicy\",")
        .append(" \"value\":  \"NEVER\"")
        .append(" }]");
    logger.info("Dynamic cluster managed server patch string: {0}", patchStr);

    patch = new V1Patch(new String(patchStr));
    boolean crdPatched = assertDoesNotThrow(() ->
            patchDomainCustomResource(domainUid, domainNamespace, patch, "application/json-patch+json"),
        "patchDomainCustomResource(managedShutdown) failed");
    assertTrue(crdPatched, "patchDomainCustomResource(managedShutdown) failed");
    logger.info("!!! Domain Resource patched for dynamic cluster managed server !!!");

    // Make sure maanged-server1 is deleted 
    checkPodDeleted(serverPodName, domainUid, domainNamespace);
    checkServiceExists(serverPodName2, domainNamespace);
    checkPodReady(serverPodName2, domainUid, domainNamespace);
    logger.info("Dynamic cluster managed Server(2) is RUNNING");

    // Patch(start) the managed-server1 
    patchStr = null;
    patchStr = new StringBuffer("[{");
    patchStr.append("\"op\": \"replace\",")
        .append(" \"path\": \"/spec/managedServers/4/serverStartPolicy\",")
        .append(" \"value\":  \"IF_NEEDED\"")
        .append(" }]");
    logger.info("Managed restart patch string: {0}", patchStr);

    patch = new V1Patch(new String(patchStr));
    crdPatched = false;
    crdPatched = assertDoesNotThrow(() ->
            patchDomainCustomResource(domainUid, domainNamespace, patch, "application/json-patch+json"),
        "patchDomainCustomResource(managedRestart) failed");

    assertTrue(crdPatched, "patchDomainCustomResource(managedRestart) failed");
    logger.info("!!! Domain resource patched for dynamic cluster managed server shutdown !!!");

    // Make sure managed-server2 is deleted 
    checkPodDeleted(serverPodName2, domainUid, domainNamespace);

    // Make sure managed-server1 is re-started
    checkServiceExists(serverPodName, domainNamespace);
    checkPodReady(serverPodName, domainUid, domainNamespace);
  }

  /**
   * Start independent managed server by setting serverStartPolicy to IF_NEEDED.
   * Stop an independent managed server by patching the resource definition with 
   *  spec/managedServers/0/serverStartPolicy set to NEVER.
   * Make sure that ONLY the specified managed server is stopped. 
   * Restart the independent managed server by patching the resource definition 
   * with spec/managedServers/0/serverStartPolicy set to ALWAYS.
   * Make sure that the specified managed server is in RUNNING state.
   */

  // The usecase fails NEVER->ALWAYS
  // https://bug.oraclecorp.com/pls/bug/webbug_print.show?c_rptno=31833260
  @Disabled
  @Test
  @DisplayName("Restart the standalone managed server with serverStartPolicy")
  public void testStandaloneManagedRestartAlways() {

    String configServerPodName = domainUid + "-standalone-managed";

    // Make sure that configured managed server is ready 
    checkServiceExists(configServerPodName, domainNamespace);
    checkPodReady(configServerPodName, domainUid, domainNamespace);
    logger.info("Configured Managed Server is RUNNING");

    StringBuffer patchStr = null;
    patchStr = new StringBuffer("[{");
    patchStr.append("\"op\": \"replace\",")
        .append(" \"path\": \"/spec/managedServers/0/serverStartPolicy\",")
        .append(" \"value\":  \"NEVER\"")
        .append(" }]");
    logger.info("Managed Server shutdown patch string: {0}", patchStr);

    patch = new V1Patch(new String(patchStr));
    boolean crdPatched = assertDoesNotThrow(() ->
            patchDomainCustomResource(domainUid, domainNamespace, patch, "application/json-patch+json"),
        "patchDomainCustomResource(managedShutdown) failed");
    assertTrue(crdPatched, "patchDomainCustomResource(managedShutdown) failed");
    logger.info("!!! Domain Resource patched for managedShutdown !!!");

    checkPodDeleted(configServerPodName, domainUid, domainNamespace);
    logger.info("Managed Server shutdown success");

    patchStr = null;
    patchStr = new StringBuffer("[{");
    patchStr.append("\"op\": \"replace\",")
        .append(" \"path\": \"/spec/managedServers/0/serverStartPolicy\",")
        .append(" \"value\":  \"ALWAYS\"")
        .append(" }]");
    logger.info("Managed restart patch string: {0}", patchStr);

    patch = new V1Patch(new String(patchStr));
    crdPatched = false;
    crdPatched = assertDoesNotThrow(() ->
            patchDomainCustomResource(domainUid, domainNamespace, patch, "application/json-patch+json"),
        "patchDomainCustomResource(managedRestart) failed");

    assertTrue(crdPatched, "patchDomainCustomResource(managedRestart) failed");
    logger.info("!!! Domain Resource patched for managedRestart !!!");

    logger.info("Wait for standalone managed server ${0} to be restarted", configServerPodName);
    checkServiceExists(configServerPodName, domainNamespace);
    checkPodReady(configServerPodName, domainUid, domainNamespace);
    logger.info("Managed Server restart success");
  }

  /**
   * Start independent managed server by setting serverStartPolicy to IF_NEEDED.
   * Stop an independent managed server by patching the resource definition with 
   *  spec/managedServers/0/serverStartPolicy set to NEVER.
   * Make sure that ONLY the specified managed server is stopped. 
   * Restart the independent managed server by patching the resource definition 
   * with spec/managedServers/0/serverStartPolicy set to IF_NEEDED.
   * Make sure that the specified managed server is in RUNNING state.
   */

  // The usecase fails NEVER->IF_NEEDED
  // https://bug.oraclecorp.com/pls/bug/webbug_print.show?c_rptno=31833260
  @Disabled
  @Test
  @DisplayName("Restart the standalone managed server with serverStartPolicy")
  public void testStandaloneManagedRestartIfNeeded() {

    String configServerPodName = domainUid + "-standalone-managed";

    // Make sure that configured managed server is ready 
    checkServiceExists(configServerPodName, domainNamespace);
    checkPodReady(configServerPodName, domainUid, domainNamespace);
    logger.info("Configured Managed Server is RUNNING");

    StringBuffer patchStr = null;
    patchStr = new StringBuffer("[{");
    patchStr.append("\"op\": \"replace\",")
        .append(" \"path\": \"/spec/managedServers/0/serverStartPolicy\",")
        .append(" \"value\":  \"NEVER\"")
        .append(" }]");
    logger.info("Managed Server shutdown patch string: {0}", patchStr);

    patch = new V1Patch(new String(patchStr));
    boolean crdPatched = assertDoesNotThrow(() ->
            patchDomainCustomResource(domainUid, domainNamespace, patch, "application/json-patch+json"),
        "patchDomainCustomResource(managedShutdown) failed");
    assertTrue(crdPatched, "patchDomainCustomResource(managedShutdown) failed");
    logger.info("!!! Domain Resource patched for managedShutdown !!!");

    checkPodDeleted(configServerPodName, domainUid, domainNamespace);
    logger.info("Managed Server shutdown success");

    patchStr = null;
    patchStr = new StringBuffer("[{");
    patchStr.append("\"op\": \"replace\",")
        .append(" \"path\": \"/spec/managedServers/0/serverStartPolicy\",")
        .append(" \"value\":  \"IF_NEEDED\"")
        .append(" }]");
    logger.info("Managed restart patch string: {0}", patchStr);

    patch = new V1Patch(new String(patchStr));
    crdPatched = false;
    crdPatched = assertDoesNotThrow(() ->
            patchDomainCustomResource(domainUid, domainNamespace, patch, "application/json-patch+json"),
        "patchDomainCustomResource(managedRestart) failed");

    assertTrue(crdPatched, "patchDomainCustomResource(managedRestart) failed");
    logger.info("!!! Domain Resource patched for managedRestart !!!");

    logger.info("Wait for standalone managed server ${0} to be restarted", configServerPodName);
    checkServiceExists(configServerPodName, domainNamespace);
    checkPodReady(configServerPodName, domainUid, domainNamespace);
    logger.info("Managed Server restart success");
  }

  // This method is needed in this test class, since the cleanup util
  // won't clean up the images.
  @AfterAll
  void tearDown() {
    // Delete domain custom resource
    logger.info("Delete domain custom resource in namespace {0}", domainNamespace);
    assertDoesNotThrow(() -> deleteDomainCustomResource(domainUid, domainNamespace),
        "deleteDomainCustomResource failed with ApiException");
    logger.info("Deleted Domain Custom Resource " + domainUid + " from " + domainNamespace);
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
    int adminServiceNodePort = getServiceNodePort(domainNamespace, adminServerPodName + "-external", "default");
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

}
