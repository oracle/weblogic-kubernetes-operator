// Copyright (c) 2020, Oracle Corporation and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.weblogic.kubernetes;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;

import io.kubernetes.client.custom.V1Patch;
import io.kubernetes.client.openapi.models.V1ConfigMap;
import io.kubernetes.client.openapi.models.V1ObjectMeta;
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
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import static java.util.concurrent.TimeUnit.MINUTES;
import static java.util.concurrent.TimeUnit.SECONDS;
import static oracle.weblogic.kubernetes.TestConstants.ADMIN_PASSWORD_DEFAULT;
import static oracle.weblogic.kubernetes.TestConstants.ADMIN_PASSWORD_PATCH;
import static oracle.weblogic.kubernetes.TestConstants.ADMIN_USERNAME_DEFAULT;
import static oracle.weblogic.kubernetes.TestConstants.ADMIN_USERNAME_PATCH;
import static oracle.weblogic.kubernetes.TestConstants.DOMAIN_VERSION;
import static oracle.weblogic.kubernetes.TestConstants.K8S_NODEPORT_HOST;
import static oracle.weblogic.kubernetes.TestConstants.MII_BASIC_IMAGE_NAME;
import static oracle.weblogic.kubernetes.TestConstants.MII_BASIC_IMAGE_TAG;
import static oracle.weblogic.kubernetes.TestConstants.OCIR_SECRET_NAME;
import static oracle.weblogic.kubernetes.actions.ActionConstants.MODEL_DIR;
import static oracle.weblogic.kubernetes.actions.TestActions.createConfigMap;
import static oracle.weblogic.kubernetes.actions.TestActions.getServiceNodePort;
import static oracle.weblogic.kubernetes.actions.TestActions.patchDomainCustomResource;
import static oracle.weblogic.kubernetes.actions.TestActions.patchDomainResourceWithModelConfigMap;
import static oracle.weblogic.kubernetes.actions.TestActions.patchDomainResourceWithNewRestartVersion;
import static oracle.weblogic.kubernetes.assertions.TestAssertions.domainExists;
import static oracle.weblogic.kubernetes.assertions.TestAssertions.podDoesNotExist;
import static oracle.weblogic.kubernetes.assertions.TestAssertions.verifyRollingRestartOccurred;
import static oracle.weblogic.kubernetes.utils.CommonMiiTestUtils.checkLogsOnPV;
import static oracle.weblogic.kubernetes.utils.CommonMiiTestUtils.checkSystemResource;
import static oracle.weblogic.kubernetes.utils.CommonMiiTestUtils.createDatabaseSecret;
import static oracle.weblogic.kubernetes.utils.CommonMiiTestUtils.createDomainResourceWithLogHome;
import static oracle.weblogic.kubernetes.utils.CommonMiiTestUtils.createDomainSecret;
import static oracle.weblogic.kubernetes.utils.CommonMiiTestUtils.createJobToChangePermissionsOnPvHostPath;
import static oracle.weblogic.kubernetes.utils.CommonMiiTestUtils.readJdbcRuntime;
import static oracle.weblogic.kubernetes.utils.CommonPatchTestUtils.patchDomainWithNewSecretAndVerify;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.checkPodReady;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.checkPodReadyAndServiceExists;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.checkServiceExists;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.createConfigMapAndVerify;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.createOcirRepoSecret;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.createPV;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.createPVC;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.createSecretForBaseImages;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.createSecretWithUsernamePassword;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.getExternalServicePodName;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.getPodCreationTime;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.installAndVerifyOperator;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.verifyCredentials;
import static oracle.weblogic.kubernetes.utils.ThreadSafeLogger.getLogger;
import static org.awaitility.Awaitility.with;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
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
 * <p>testMiiDeleteSystemResources
 *  Delete System Resources defined in a WebLogic domain 
 *
 * <p>testMiiAddSystemResources
 *  Add new System Resources to a running WebLogic domain
 *
 * <p>testMiiAddDynmicClusteriWithNoReplica
 *  Add a new dynamic WebLogic cluster to a running domain with default Replica
 *  count(zero), so that no managed server on the new cluster is activated.
 *
 * <p>testMiiAddDynamicCluster
 *  Add a new dynamic WebLogic cluster to a running domain with non-zero Replica
 *  count so that required number of managed servers(s) on new cluster get  
 *  activated after rolling restart. 
 *
 * <p>testMiiAddConfiguredCluster
 *  Add a new configured WebLogic cluster to a running domain 
 *
 * <p>testMiiUpdateWebLogicCredential
 *  Update the administrative credential of a running domain by updating the 
 *  secret and activating a rolling restart.
 */

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DisplayName("Test logHome on PV, add SystemResources, Clusters to model in image domain")
@IntegrationTest
class ItMiiUpdateDomainConfig {

  private static String opNamespace = null;
  private static String domainNamespace = null;
  private static ConditionFactory withStandardRetryPolicy = null;
  private static int replicaCount = 2;
  private static final String domainUid = "mii-add-config";
  private static String pvName = domainUid + "-pv"; // name of the persistent volume
  private static String pvcName = domainUid + "-pvc"; // name of the persistent volume claim
  private StringBuffer curlString = null;
  private StringBuffer checkCluster = null;
  private V1Patch patch = null;
  private final String adminServerPodName = domainUid + "-admin-server";
  private final String managedServerPrefix = domainUid + "-managed-server";
  private final String adminServerName = "admin-server";

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
    String configMapName = "jdbc-jms-wldf-configmap";

    createConfigMapAndVerify(
        configMapName, domainUid, domainNamespace,
        Arrays.asList("model.sysresources.yaml"));


    // create pull secrets for WebLogic image when running in non Kind Kubernetes cluster
    // this secret is used only for non-kind cluster
    createSecretForBaseImages(domainNamespace);

    // create PV, PVC for logs
    createPV(pvName, domainUid, ItMiiUpdateDomainConfig.class.getSimpleName());
    createPVC(pvName, pvcName, domainUid, domainNamespace);

    // create job to change permissions on PV hostPath
    createJobToChangePermissionsOnPvHostPath(pvName, pvcName, domainNamespace);

    // create the domain CR with a pre-defined configmap
    createDomainResourceWithLogHome(domainUid, domainNamespace,
        MII_BASIC_IMAGE_NAME + ":" + MII_BASIC_IMAGE_TAG,
        adminSecretName, OCIR_SECRET_NAME, encryptionSecretName, replicaCount,
        pvName, pvcName,"cluster-1", configMapName,
        dbSecretName, false);

    //    createDomainResource(domainUid, domainNamespace, adminSecretName,
    //        OCIR_SECRET_NAME, encryptionSecretName,
    //        replicaCount, configMapName, dbSecretName);

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
   * Check server logs are written on PV.
   */
  @Test
  @Order(1)
  @DisplayName("Check the server logs are written on PV, look for string RUNNING in server log")
  public void testServerLogsAreOnPV() {

    // check server logs are written on PV and look for string RUNNING in log
    checkLogsOnPV(domainNamespace, "grep RUNNING /shared/logs/" + adminServerName + ".log", adminServerPodName);
  }

  /**
   * Create a WebLogic domain with a defined configmap in configuration/model 
   * section of the domain resource.
   * The configmap has multiple sparse WDT model files that define 
   * a JDBCSystemResource, a JMSSystemResource and a WLDFSystemResource.
   * Verify all the SystemResource configurations using the rest API call 
   * using the public nodeport of the administration server.
   */
  @Test
  @Order(2)
  @DisplayName("Verify the pre-configured SystemResources in a model-in-image domain")
  public void testMiiCheckSystemResources() {

    assertTrue(checkSystemResourceConfiguration("JDBCSystemResources", 
        "TestDataSource", "200"), "JDBCSystemResource not found");
    logger.info("Found the JDBCSystemResource configuration");

    assertTrue(checkSystemResourceConfiguration("JMSSystemResources", 
        "TestClusterJmsModule", "200"), "JMSSystemResources not found");
    logger.info("Found the JMSSystemResource configuration");

    assertTrue(checkSystemResourceConfiguration("WLDFSystemResources", 
        "TestWldfModule", "200"), "WLDFSystemResources not found");
    logger.info("Found the WLDFSystemResource configuration");

    ExecResult result = null;
    result = readJdbcRuntime(domainNamespace, adminServerPodName, "TestDataSource");
    logger.info("checkJdbcRuntime: returned {0}", result.toString());
    assertTrue(result.stdout().contains("jdbc:oracle:thin:localhost"),
         String.format("DB URL does not match with RuntimeMBean Info"));
    assertTrue(result.stdout().contains("scott"),
         String.format("DB user name does not match with RuntimeMBean Info"));
    logger.info("Found the JDBCSystemResource configuration");

  }

  /**
   * Start a WebLogic domain using model-in-image with JMS/JDBC SystemResources.
   * Create a configmap to delete JMS/JDBC SystemResources.
   * Patch the domain resource with the configmap.
   * Update the restart version of the domain resource.
   * Verify rolling restart of the domain by comparing PodCreationTimestamp
   * for all the server pods before and after rolling restart.
   * Verify SystemResources are deleted from the domain.
   */
  @Test
  @Order(3)
  @DisplayName("Delete SystemResources from a model-in-image domain")
  public void testMiiDeleteSystemResources() {

    String configMapName = "deletesysrescm";
    createConfigMapAndVerify(
        configMapName, domainUid, domainNamespace,
        Arrays.asList("model.delete.sysresources.yaml"));

    LinkedHashMap<String, DateTime> pods = new LinkedHashMap<>();
    // get the creation time of the admin server pod before patching
    DateTime adminPodCreationTime = getPodCreationTime(domainNamespace,adminServerPodName);
    pods.put(adminServerPodName, adminPodCreationTime);
    // get the creation time of the managed server pods before patching
    for (int i = 1; i <= replicaCount; i++) {
      pods.put(managedServerPrefix + i, getPodCreationTime(domainNamespace, managedServerPrefix + i));
    }

    patchDomainResourceWithModelConfigMap(domainUid, domainNamespace, configMapName);

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
   
    assertTrue(checkSystemResourceConfiguration("JDBCSystemResources", 
         "TestDataSource", "404"), "JDBCSystemResource should be deleted");
    assertTrue(checkSystemResourceConfiguration("JMSSystemResources", 
         "TestClusterJmsModule", "404"), "JMSSystemResources should be deleted");
  }

  /**
   * Start a WebLogic domain using model-in-image.
   * Create a configmap with sparse JDBC/JMS model files using LOG_HOME(which is on PV) ENV var for JMS Server log file.
   * Patch the domain resource with the configmap.
   * Update the restart version of the domain resource.
   * Verify rolling restart of the domain by comparing PodCreationTimestamp
   * for all the server pods before and after rolling restart.
   * Verify SystemResource configurations using Rest API call to admin server.
   * Verify JMS Server logs are written on PV.
   */
  @Test
  @Order(4)
  @DisplayName("Add New JDBC/JMS SystemResources to a model-in-image domain")
  public void testMiiAddSystemResources() {

    logger.info("Use same database secret created in befreAll() method");
    String configMapName = "dsjmsconfigmap";
    createConfigMapAndVerify(
        configMapName, domainUid, domainNamespace,
        Arrays.asList("model.jdbc2.yaml", "model.jms2.yaml"));

    LinkedHashMap<String, DateTime> pods = new LinkedHashMap<>();
    // get the creation time of the admin server pod before patching
    DateTime adminPodCreationTime = getPodCreationTime(domainNamespace,adminServerPodName);
    pods.put(adminServerPodName, adminPodCreationTime);
    // get the creation time of the managed server pods before patching
    for (int i = 1; i <= replicaCount; i++) {
      pods.put(managedServerPrefix + i, getPodCreationTime(domainNamespace, managedServerPrefix + i));
    }

    patchDomainResourceWithModelConfigMap(domainUid, domainNamespace, configMapName);

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

    assertTrue(checkSystemResourceConfiguration("JDBCSystemResources", 
          "TestDataSource2", "200"), "JDBCSystemResource not found");
    logger.info("Found the JDBCSystemResource configuration");

    assertTrue(checkSystemResourceConfiguration("JMSSystemResources", 
          "TestClusterJmsModule2", "200"), "JMSSystemResources not found");
    logger.info("Found the JMSSystemResource configuration");

    // check JMS logs are written on PV
    checkLogsOnPV(domainNamespace,"ls -ltr /shared/logs/*jms_messages.log", managedServerPrefix + "1");
  }

  /**
   * Patch the domain resource with the configmap to add a cluster.
   * Update the restart version of the domain resource.
   * Verify rolling restart of the domain by comparing PodCreationTimestamp
   * before and after rolling restart.
   * Verify servers from new cluster are not in running state, because 
   * the spec level replica count to zero(default).
   */
  @Test
  @Order(5)
  @DisplayName("Add a dynamic cluster to a model-in-image domain with default replica count")
  public void testMiiAddDynmicClusteriWithNoReplica() {

    // This test uses the WebLogic domain created in BeforeAll method
    // BeforeEach method ensures that the server pods are running

    String configMapName = "noreplicaconfigmap";
    createClusterConfigMap(configMapName, "model.config.cluster.yaml");

    LinkedHashMap<String, DateTime> pods = new LinkedHashMap<>();

    // get the creation time of the server pods before patching
    DateTime adminPodCreationTime = getPodCreationTime(domainNamespace, adminServerPodName);
    pods.put(adminServerPodName, adminPodCreationTime);
    for (int i = 1; i <= replicaCount; i++) {
      pods.put(managedServerPrefix + i, getPodCreationTime(domainNamespace, managedServerPrefix + i));
    }

    patchDomainResourceWithModelConfigMap(domainUid, domainNamespace, configMapName);

    String newRestartVersion = patchDomainResourceWithNewRestartVersion(domainUid, domainNamespace);
    logger.log(Level.INFO, "New restart version : {0}", newRestartVersion);

    assertTrue(verifyRollingRestartOccurred(pods, 1, domainNamespace),
        "Rolling restart failed");

    // The ServerNamePrefix for the new configured cluster is config-server
    // Make sure the managed server from new cluster is not running

    String newServerPodName = domainUid + "-config-server1";
    checkPodNotCreated(newServerPodName, domainUid, domainNamespace);

    boolean isServerConfigured = checkManagedServerConfiguration("config-server1");
    assertTrue(isServerConfigured, "Could not find new managed server configuration");
    logger.info("Found new managed server configuration");
  }

  /**
   * Create a configmap with a sparse model file to add a dynamic cluster.
   * Patch the domain resource with the configmap.
   * Patch the domain resource with the spec/replicas set to 1.
   * Update the restart version of the domain resource.
   * Verify rolling restart of the domain by comparing PodCreationTimestamp
   * before and after rolling restart.
   * Verify servers from new cluster are in running state.
   */
  @Test
  @Order(6)
  @DisplayName("Add a dynamic cluster to model-in-image domain with non-zero replica count")
  public void testMiiAddDynamicCluster() {

    // This test uses the WebLogic domain created in BeforeAll method
    // BeforeEach method ensures that the server pods are running

    String configMapName = "dynamicclusterconfigmap";
    createClusterConfigMap(configMapName, "model.dynamic.cluster.yaml");

    LinkedHashMap<String, DateTime> pods = new LinkedHashMap<>();
    // get the creation time of the admin server pod before patching
    DateTime adminPodCreationTime = getPodCreationTime(domainNamespace, adminServerPodName);
    pods.put(adminServerPodName, adminPodCreationTime);
    // get the creation time of the managed server pods before patching
    for (int i = 1; i <= replicaCount; i++) {
      pods.put(managedServerPrefix + i, getPodCreationTime(domainNamespace, managedServerPrefix + i));
    }

    patchDomainResourceWithModelConfigMap(domainUid, domainNamespace, configMapName);

    StringBuffer patchStr = new StringBuffer("[{");
    patchStr.append(" \"op\": \"replace\",")
        .append(" \"path\": \"/spec/replicas\",")
        .append(" \"value\": 1")
        .append(" }]");
    logger.log(Level.INFO, "Replicas patch string: {0}", patchStr);

    patch = new V1Patch(new String(patchStr));
    boolean repilcaPatched = assertDoesNotThrow(() ->
            patchDomainCustomResource(domainUid, domainNamespace, patch, "application/json-patch+json"),
        "patchDomainCustomResource(restartVersion)  failed ");
    assertTrue(repilcaPatched, "patchDomainCustomResource(repilcas) failed");

    String newRestartVersion = patchDomainResourceWithNewRestartVersion(domainUid, domainNamespace);
    logger.log(Level.INFO, "New restart version : {0}", newRestartVersion);

    // Check if the admin server pod has been restarted
    // by comparing the PodCreationTime before and after rolling restart

    assertTrue(verifyRollingRestartOccurred(pods, 1, domainNamespace),
        "Rolling restart failed");

    // The ServerNamePrefix for the new dynamic cluster is dynamic-server
    // Make sure the managed server from the new cluster is running

    String newServerPodName = domainUid + "-dynamic-server1";
    checkPodReady(newServerPodName, domainUid, domainNamespace);
    checkServiceExists(newServerPodName, domainNamespace);

    boolean isServerConfigured = checkManagedServerConfiguration("dynamic-server1");
    assertTrue(isServerConfigured, "Could not find new managed server configuration");
    logger.info("Found new managed server configuration");
  }

  /**
   * Create a configmap with a sparse model file to add a configured cluster.
   * Patch the domain resource with the configmap.
   * Patch the domain resource with the spec/replicas set to 1.
   * Update the restart version of the domain resource.
   * Verify rolling restart of the domain by comparing PodCreationTimestamp
   * before and after rolling restart.
   * Verify servers from new cluster are in running state.
   */
  @Test
  @Order(7)
  @DisplayName("Add a configured cluster to a model-in-image domain")
  public void testMiiAddConfiguredCluster() {

    // This test uses the WebLogic domain created in BeforeAll method
    // BeforeEach method ensures that the server pods are running

    String configMapName = "configclusterconfigmap";
    createClusterConfigMap(configMapName, "model.config.cluster.yaml");

    LinkedHashMap<String, DateTime> pods = new LinkedHashMap<>();

    // get the creation time of the admin server pod before patching
    DateTime adminPodCreationTime = getPodCreationTime(domainNamespace, adminServerPodName);
    // get the creation time of the managed server pods before patching
    pods.put(adminServerPodName, adminPodCreationTime);
    for (int i = 1; i <= replicaCount; i++) {
      pods.put(managedServerPrefix + i, getPodCreationTime(domainNamespace,   managedServerPrefix + i));
    }

    patchDomainResourceWithModelConfigMap(domainUid, domainNamespace, configMapName);

    StringBuffer patchStr = new StringBuffer("[{");
    patchStr.append(" \"op\": \"replace\",")
        .append(" \"path\": \"/spec/replicas\",")
        .append(" \"value\": 1")
        .append(" }]");
    logger.log(Level.INFO, "Replicas patch string: {0}", patchStr);

    patch = new V1Patch(new String(patchStr));
    boolean repilcaPatched = assertDoesNotThrow(() ->
            patchDomainCustomResource(domainUid, domainNamespace, patch, "application/json-patch+json"),
        "patchDomainCustomResource(restartVersion)  failed ");
    assertTrue(repilcaPatched, "patchDomainCustomResource(repilcas) failed");

    String newRestartVersion = patchDomainResourceWithNewRestartVersion(domainUid, domainNamespace);
    logger.log(Level.INFO, "New restart version : {0}", newRestartVersion);

    assertTrue(verifyRollingRestartOccurred(pods, 1, domainNamespace),
        "Rolling restart failed");

    // The ServerNamePrefix for the new configured cluster is config-server
    // Make sure the managed server from the new cluster is running
    String newServerPodName = domainUid + "-config-server1";
    checkPodReady(newServerPodName, domainUid, domainNamespace);
    checkServiceExists(newServerPodName, domainNamespace);

    boolean isServerConfigured = checkManagedServerConfiguration("config-server1");
    assertTrue(isServerConfigured, "Could not find new managed server configuration");
    logger.info("Found new managed server configuration");
  }

  /**
   * Start a WebLogic domain with model-in-imge.
   * Patch the domain CRD with a new credentials secret.
   * Update domainRestartVersion to trigger a rolling restart of server pods.
   * make sure all the server pods are re-started in a rolling fashion. 
   * Check the validity of new credentials by accessing 
   * WebLogic RESTful Management Services.
   */
  @Test
  @Order(8)
  @DisplayName("Change the WebLogic Admin credential in model-in-image domain")
  public void testMiiUpdateWebLogicCredential() {
    final boolean VALID = true;
    final boolean INVALID = false;

    LinkedHashMap<String, DateTime> pods = new LinkedHashMap<>();
    // get the creation time of the admin server pod before patching
    DateTime adminPodCreationTime = getPodCreationTime(domainNamespace,adminServerPodName);
    pods.put(adminServerPodName, adminPodCreationTime);
    // get the creation time of the managed server pods before patching
    for (int i = 1; i <= replicaCount; i++) {
      pods.put(managedServerPrefix + i, getPodCreationTime(domainNamespace, managedServerPrefix + i));
    }

    logger.info("Check that before patching current credentials are valid and new credentials are not");
    verifyCredentials(adminServerPodName, domainNamespace, ADMIN_USERNAME_DEFAULT, ADMIN_PASSWORD_DEFAULT, VALID);
    verifyCredentials(adminServerPodName, domainNamespace, ADMIN_USERNAME_PATCH, ADMIN_PASSWORD_PATCH, INVALID);

    // create a new secret for admin credentials
    logger.info("Create a new secret that contains new WebLogic admin credentials");
    String adminSecretName = "weblogic-credentials-new";
    assertDoesNotThrow(() -> createSecretWithUsernamePassword(
        adminSecretName,
        domainNamespace,
        ADMIN_USERNAME_PATCH,
        ADMIN_PASSWORD_PATCH),
        String.format("createSecret failed for %s", adminSecretName));

    // patch the domain resource with the new secret and verify that the domain resource is patched.
    logger.info("Patch domain {0} in namespace {1} with the secret {2}, and verify the result",
        domainUid, domainNamespace, adminSecretName);
    String restartVersion = patchDomainWithNewSecretAndVerify(
        domainUid,
        domainNamespace,
        adminServerPodName,
        managedServerPrefix,
        replicaCount,
        adminSecretName);

    logger.info("Wait for domain {0} admin server pod {1} in namespace {2} to be restarted",
        domainUid, adminServerPodName, domainNamespace);

    assertTrue(verifyRollingRestartOccurred(pods, 1, domainNamespace),
        "Rolling restart failed");

    // check if the new credentials are valid and the old credentials are not valid any more
    logger.info("Check that after patching current credentials are not valid and new credentials are");
    verifyCredentials(adminServerPodName, domainNamespace, ADMIN_USERNAME_DEFAULT, ADMIN_PASSWORD_DEFAULT, INVALID);
    verifyCredentials(adminServerPodName, domainNamespace, ADMIN_USERNAME_PATCH, ADMIN_PASSWORD_PATCH, VALID);

    logger.info("Domain {0} in namespace {1} is fully started after changing WebLogic credentials secret",
        domainUid, domainNamespace);
  }


  private void checkPodNotCreated(String podName, String domainUid, String domNamespace) {
    withStandardRetryPolicy
        .conditionEvaluationListener(
            condition -> logger.info("Waiting for pod {0} to be not created in namespace {1} "
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
    return checkSystemResource(domainNamespace, adminServerPodName,
        "/management/tenant-monitoring/servers/" + managedServer, "200");
    //    ExecResult result = null;
    //    int adminServiceNodePort
    //        = getServiceNodePort(domainNamespace, getExternalServicePodName(adminServerPodName), "default");
    //    checkCluster = new StringBuffer("status=$(curl --user weblogic:welcome1 ");
    //    checkCluster.append("http://" + K8S_NODEPORT_HOST + ":" + adminServiceNodePort)
    //          .append("/management/tenant-monitoring/servers/")
    //          .append(managedServer)
    //          .append(" --silent --show-error ")
    //          .append(" -o /dev/null")
    //          .append(" -w %{http_code});")
    //          .append("echo ${status}");
    //    logger.info("checkManagedServerConfiguration: curl command {0}", new String(checkCluster));
    //    try {
    //      result = exec(new String(checkCluster), true);
    //    } catch (Exception ex) {
    //      logger.info("Exception in checkManagedServerConfiguration() {0}", ex);
    //      return false;
    //    }
    //    logger.info("checkManagedServerConfiguration: curl command returned {0}", result.toString());
    //    if (result.stdout().equals("200")) {
    //      return true;
    //    } else {
    //      return false;
    //    }
  }

  // Crate a ConfigMap with a model file to add a new WebLogic cluster
  private void createClusterConfigMap(String configMapName, String modelFile) {
    Map<String, String> labels = new HashMap<>();
    labels.put("weblogic.domainUid", domainUid);
    String dsModelFile =  String.format("%s/%s", MODEL_DIR,modelFile);
    Map<String, String> data = new HashMap<>();
    String cmData = null;
    cmData = assertDoesNotThrow(() -> Files.readString(Paths.get(dsModelFile)),
        String.format("readString operation failed for %s", dsModelFile));
    assertNotNull(cmData, String.format("readString() operation failed while creating ConfigMap %s", configMapName));
    data.put(modelFile, cmData);

    V1ObjectMeta meta = new V1ObjectMeta()
        .labels(labels)
        .name(configMapName)
        .namespace(domainNamespace);
    V1ConfigMap configMap = new V1ConfigMap()
        .data(data)
        .metadata(meta);

    boolean cmCreated = assertDoesNotThrow(() -> createConfigMap(configMap),
        String.format("Can't create ConfigMap %s", configMapName));
    assertTrue(cmCreated, String.format("createConfigMap failed while creating ConfigMap %s", configMapName));
  }

  private boolean checkSystemResourceConfiguration(String resourcesType, 
         String resourcesName, String expectedStatusCode) {

    int adminServiceNodePort
        = getServiceNodePort(domainNamespace, getExternalServicePodName(adminServerPodName), "default");
    ExecResult result = null;
    curlString = new StringBuffer("status=$(curl --user weblogic:welcome1 ");
    curlString.append("http://" + K8S_NODEPORT_HOST + ":" + adminServiceNodePort)
         .append("/management/weblogic/latest/domainConfig")
         .append("/")
         .append(resourcesType)
         .append("/")
         .append(resourcesName)
         .append("/")
         .append(" --silent --show-error ")
         .append(" -o /dev/null ")
         .append(" -w %{http_code});")
         .append("echo ${status}");
    logger.info("checkSystemResource: curl command {0}", new String(curlString));
    return new Command()
          .withParams(new CommandParams()
              .command(curlString.toString()))
          .executeAndVerify(expectedStatusCode);
  }
}
