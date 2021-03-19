// Copyright (c) 2020, 2021, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.weblogic.kubernetes;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.logging.Level;

import io.kubernetes.client.custom.V1Patch;
import io.kubernetes.client.openapi.ApiException;
import io.kubernetes.client.openapi.models.V1ConfigMap;
import io.kubernetes.client.openapi.models.V1EnvVar;
import io.kubernetes.client.openapi.models.V1Job;
import io.kubernetes.client.openapi.models.V1JobCondition;
import io.kubernetes.client.openapi.models.V1JobSpec;
import io.kubernetes.client.openapi.models.V1LocalObjectReference;
import io.kubernetes.client.openapi.models.V1ObjectMeta;
import io.kubernetes.client.openapi.models.V1PersistentVolumeClaimVolumeSource;
import io.kubernetes.client.openapi.models.V1Pod;
import io.kubernetes.client.openapi.models.V1PodSpec;
import io.kubernetes.client.openapi.models.V1PodTemplateSpec;
import io.kubernetes.client.openapi.models.V1Secret;
import io.kubernetes.client.openapi.models.V1SecretReference;
import io.kubernetes.client.openapi.models.V1Volume;
import io.kubernetes.client.openapi.models.V1VolumeMount;
import oracle.weblogic.domain.AdminServer;
import oracle.weblogic.domain.AdminService;
import oracle.weblogic.domain.Channel;
import oracle.weblogic.domain.Cluster;
import oracle.weblogic.domain.Configuration;
import oracle.weblogic.domain.Domain;
import oracle.weblogic.domain.DomainSpec;
import oracle.weblogic.domain.Model;
import oracle.weblogic.domain.ServerPod;
import oracle.weblogic.kubernetes.actions.impl.primitive.Kubernetes;
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
import static oracle.weblogic.kubernetes.TestConstants.ADMIN_PASSWORD_DEFAULT;
import static oracle.weblogic.kubernetes.TestConstants.ADMIN_PASSWORD_PATCH;
import static oracle.weblogic.kubernetes.TestConstants.ADMIN_USERNAME_DEFAULT;
import static oracle.weblogic.kubernetes.TestConstants.ADMIN_USERNAME_PATCH;
import static oracle.weblogic.kubernetes.TestConstants.BASE_IMAGES_REPO_SECRET;
import static oracle.weblogic.kubernetes.TestConstants.DOMAIN_API_VERSION;
import static oracle.weblogic.kubernetes.TestConstants.DOMAIN_VERSION;
import static oracle.weblogic.kubernetes.TestConstants.K8S_NODEPORT_HOST;
import static oracle.weblogic.kubernetes.TestConstants.MII_BASIC_IMAGE_NAME;
import static oracle.weblogic.kubernetes.TestConstants.MII_BASIC_IMAGE_TAG;
import static oracle.weblogic.kubernetes.TestConstants.OCIR_SECRET_NAME;
import static oracle.weblogic.kubernetes.actions.ActionConstants.MODEL_DIR;
import static oracle.weblogic.kubernetes.actions.ActionConstants.RESOURCE_DIR;
import static oracle.weblogic.kubernetes.actions.TestActions.createConfigMap;
import static oracle.weblogic.kubernetes.actions.TestActions.createDomainCustomResource;
import static oracle.weblogic.kubernetes.actions.TestActions.createSecret;
import static oracle.weblogic.kubernetes.actions.TestActions.getJob;
import static oracle.weblogic.kubernetes.actions.TestActions.getPodLog;
import static oracle.weblogic.kubernetes.actions.TestActions.getServiceNodePort;
import static oracle.weblogic.kubernetes.actions.TestActions.listPods;
import static oracle.weblogic.kubernetes.actions.TestActions.patchDomainCustomResource;
import static oracle.weblogic.kubernetes.actions.TestActions.patchDomainResourceWithNewRestartVersion;
import static oracle.weblogic.kubernetes.actions.TestActions.scaleCluster;
import static oracle.weblogic.kubernetes.assertions.TestAssertions.domainExists;
import static oracle.weblogic.kubernetes.assertions.TestAssertions.podDoesNotExist;
import static oracle.weblogic.kubernetes.assertions.TestAssertions.verifyRollingRestartOccurred;
import static oracle.weblogic.kubernetes.utils.CommonPatchTestUtils.patchDomainWithNewSecretAndVerify;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.checkPodDeleted;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.checkPodDoesNotExist;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.checkPodReady;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.checkPodReadyAndServiceExists;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.checkServiceExists;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.checkSystemResourceConfiguration;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.createConfigMapAndVerify;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.createJobAndWaitUntilComplete;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.createOcirRepoSecret;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.createPV;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.createPVC;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.createSecretForBaseImages;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.createSecretWithUsernamePassword;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.createfixPVCOwnerContainer;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.getExternalServicePodName;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.getPodCreationTime;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.installAndVerifyOperator;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.setPodAntiAffinity;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.verifyCredentials;
import static oracle.weblogic.kubernetes.utils.ExecCommand.exec;
import static oracle.weblogic.kubernetes.utils.FileUtils.copyFileToPod;
import static oracle.weblogic.kubernetes.utils.ThreadSafeLogger.getLogger;
import static org.awaitility.Awaitility.with;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * This test class verifies dynamic changes to domain resource and configuration
 * by modifying the associated configmap with a model-in-image domain.
 * After updating the configmap, the test updates the restartVersion of the
 * domain resource which triggers the rolling restart to verify the change.
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
  private final String clusterName = "cluster-1";

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
        Arrays.asList(MODEL_DIR + "/model.sysresources.yaml"));


    // create pull secrets for WebLogic image when running in non Kind Kubernetes cluster
    // this secret is used only for non-kind cluster
    createSecretForBaseImages(domainNamespace);

    // create PV, PVC for logs
    createPV(pvName, domainUid, ItMiiUpdateDomainConfig.class.getSimpleName());
    createPVC(pvName, pvcName, domainUid, domainNamespace);

    // create job to change permissions on PV hostPath
    createJobToChangePermissionsOnPvHostPath(pvName, pvcName, domainNamespace);

    // create the domain CR with a pre-defined configmap
    createDomainResource(domainUid, domainNamespace, adminSecretName,
        OCIR_SECRET_NAME, encryptionSecretName,
        replicaCount, configMapName, dbSecretName);

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
   * Check server logs are written on PersistentVolume(PV).
   * The test looks for the string RUNNING in server log
   */
  @Test
  @Order(1)
  @DisplayName("Check the server logs are written to PersistentVolume")
  public void testMiiServerLogsAreOnPV() {

    // check server logs are written on PV and look for string RUNNING in log
    checkLogsOnPV("grep RUNNING /shared/logs/" + adminServerName + ".log", adminServerPodName);
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
  @DisplayName("Verify the pre-configured SystemResources in the domain")
  public void testMiiCheckSystemResources() {

    int adminServiceNodePort
        = getServiceNodePort(domainNamespace, getExternalServicePodName(adminServerPodName), "default");
    assertNotEquals(-1, adminServiceNodePort, "admin server default node port is not valid");
    assertTrue(checkSystemResourceConfiguration(adminServiceNodePort, "JDBCSystemResources",
        "TestDataSource", "200"), "JDBCSystemResource not found");
    logger.info("Found the JDBCSystemResource configuration");

    assertTrue(checkSystemResourceConfiguration(adminServiceNodePort,"JMSSystemResources",
        "TestClusterJmsModule", "200"), "JMSSystemResources not found");
    logger.info("Found the JMSSystemResource configuration");

    assertTrue(checkSystemResourceConfiguration(adminServiceNodePort,"WLDFSystemResources",
        "TestWldfModule", "200"), "WLDFSystemResources not found");
    logger.info("Found the WLDFSystemResource configuration");

    ExecResult result = null;
    result = checkJdbcRuntime("TestDataSource");
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
  @DisplayName("Delete SystemResources from the domain")
  public void testMiiDeleteSystemResources() {

    String configMapName = "deletesysrescm";
    createConfigMapAndVerify(
        configMapName, domainUid, domainNamespace,
        Arrays.asList(MODEL_DIR + "/model.delete.sysresources.yaml"));

    LinkedHashMap<String, OffsetDateTime> pods = new LinkedHashMap<>();
    // get the creation time of the admin server pod before patching
    OffsetDateTime adminPodCreationTime = getPodCreationTime(domainNamespace,adminServerPodName);
    pods.put(adminServerPodName, adminPodCreationTime);
    // get the creation time of the managed server pods before patching
    for (int i = 1; i <= replicaCount; i++) {
      pods.put(managedServerPrefix + i, getPodCreationTime(domainNamespace, managedServerPrefix + i));
    }

    StringBuffer patchStr = null;
    patchStr = new StringBuffer("[{");
    patchStr.append("\"op\": \"replace\",")
        .append(" \"path\": \"/spec/configuration/model/configMap\",")
        .append(" \"value\":  \"" + configMapName + "\"")
        .append(" }]");
    logger.log(Level.INFO, "Configmap patch string: {0}", patchStr);

    patch = new V1Patch(new String(patchStr));
    boolean cmPatched = assertDoesNotThrow(() ->
            patchDomainCustomResource(domainUid, domainNamespace, patch, "application/json-patch+json"),
        "patchDomainCustomResource(configMap)  failed ");
    assertTrue(cmPatched, "patchDomainCustomResource(configMap) failed");

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

    int adminServiceNodePort
        = getServiceNodePort(domainNamespace, getExternalServicePodName(adminServerPodName), "default");
    assertNotEquals(-1, adminServiceNodePort, "admin server default node port is not valid");
    assertTrue(checkSystemResourceConfiguration(adminServiceNodePort, "JDBCSystemResources",
         "TestDataSource", "404"), "JDBCSystemResource should be deleted");
    assertTrue(checkSystemResourceConfiguration(adminServiceNodePort, "JMSSystemResources",
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
  @DisplayName("Add new JDBC/JMS SystemResources to the domain")
  public void testMiiAddSystemResources() {

    logger.info("Use same database secret created in befreAll() method");
    String configMapName = "dsjmsconfigmap";
    createConfigMapAndVerify(
        configMapName, domainUid, domainNamespace,
        Arrays.asList(MODEL_DIR + "/model.jdbc2.yaml", MODEL_DIR + "/model.jms2.yaml"));

    LinkedHashMap<String, OffsetDateTime> pods = new LinkedHashMap<>();
    // get the creation time of the admin server pod before patching
    OffsetDateTime adminPodCreationTime = getPodCreationTime(domainNamespace,adminServerPodName);
    pods.put(adminServerPodName, adminPodCreationTime);
    // get the creation time of the managed server pods before patching
    for (int i = 1; i <= replicaCount; i++) {
      pods.put(managedServerPrefix + i, getPodCreationTime(domainNamespace, managedServerPrefix + i));
    }

    StringBuffer patchStr = null;
    patchStr = new StringBuffer("[{");
    patchStr.append("\"op\": \"replace\",")
        .append(" \"path\": \"/spec/configuration/model/configMap\",")
        .append(" \"value\":  \"" + configMapName + "\"")
        .append(" }]");
    logger.log(Level.INFO, "Configmap patch string: {0}", patchStr);

    patch = new V1Patch(new String(patchStr));
    boolean cmPatched = assertDoesNotThrow(() ->
            patchDomainCustomResource(domainUid, domainNamespace, patch, "application/json-patch+json"),
        "patchDomainCustomResource(configMap)  failed ");
    assertTrue(cmPatched, "patchDomainCustomResource(configMap) failed");

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

    int adminServiceNodePort
        = getServiceNodePort(domainNamespace, getExternalServicePodName(adminServerPodName), "default");
    assertNotEquals(-1, adminServiceNodePort, "admin server default node port is not valid");
    assertTrue(checkSystemResourceConfiguration(adminServiceNodePort,"JDBCSystemResources",
          "TestDataSource2", "200"), "JDBCSystemResource not found");
    logger.info("Found the JDBCSystemResource configuration");

    assertTrue(checkSystemResourceConfiguration(adminServiceNodePort, "JMSSystemResources",
          "TestClusterJmsModule2", "200"), "JMSSystemResources not found");
    logger.info("Found the JMSSystemResource configuration");

    // check JMS logs are written on PV
    checkLogsOnPV("ls -ltr /shared/logs/*jms_messages.log", managedServerPrefix + "1");
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
  @DisplayName("Add a dynamic cluster to the domain with default replica count")
  public void testMiiAddDynmicClusteriWithNoReplica() {

    // This test uses the WebLogic domain created in BeforeAll method
    // BeforeEach method ensures that the server pods are running

    String configMapName = "noreplicaconfigmap";
    createClusterConfigMap(configMapName, "model.config.cluster.yaml");

    LinkedHashMap<String, OffsetDateTime> pods = new LinkedHashMap<>();

    // get the creation time of the server pods before patching
    OffsetDateTime adminPodCreationTime = getPodCreationTime(domainNamespace, adminServerPodName);
    pods.put(adminServerPodName, adminPodCreationTime);
    for (int i = 1; i <= replicaCount; i++) {
      pods.put(managedServerPrefix + i, getPodCreationTime(domainNamespace, managedServerPrefix + i));
    }

    StringBuffer patchStr = null;
    patchStr = new StringBuffer("[{");
    patchStr.append("\"op\": \"replace\",")
        .append(" \"path\": \"/spec/configuration/model/configMap\",")
        .append(" \"value\":  \"" + configMapName + "\"")
        .append(" }]");
    logger.log(Level.INFO, "Configmap patch string: {0}", patchStr);

    patch = new V1Patch(new String(patchStr));
    boolean cmPatched = assertDoesNotThrow(() ->
            patchDomainCustomResource(domainUid, domainNamespace, patch, "application/json-patch+json"),
        "patchDomainCustomResource(configMap)  failed ");
    assertTrue(cmPatched, "patchDomainCustomResource(configMap) failed");

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
  @DisplayName("Add a dynamic cluster to domain with non-zero replica count")
  public void testMiiAddDynamicCluster() {

    // This test uses the WebLogic domain created in BeforeAll method
    // BeforeEach method ensures that the server pods are running

    String configMapName = "dynamicclusterconfigmap";
    createClusterConfigMap(configMapName, "model.dynamic.cluster.yaml");

    LinkedHashMap<String, OffsetDateTime> pods = new LinkedHashMap<>();
    // get the creation time of the admin server pod before patching
    OffsetDateTime adminPodCreationTime = getPodCreationTime(domainNamespace, adminServerPodName);
    pods.put(adminServerPodName, adminPodCreationTime);
    // get the creation time of the managed server pods before patching
    for (int i = 1; i <= replicaCount; i++) {
      pods.put(managedServerPrefix + i, getPodCreationTime(domainNamespace, managedServerPrefix + i));
    }

    StringBuffer patchStr = null;
    patchStr = new StringBuffer("[{");
    patchStr.append("\"op\": \"replace\",")
        .append(" \"path\": \"/spec/configuration/model/configMap\",")
        .append(" \"value\":  \"" + configMapName + "\"")
        .append(" }]");
    logger.log(Level.INFO, "Configmap patch string: {0}", patchStr);

    patch = new V1Patch(new String(patchStr));
    boolean cmPatched = assertDoesNotThrow(() ->
            patchDomainCustomResource(domainUid, domainNamespace, patch, "application/json-patch+json"),
        "patchDomainCustomResource(configMap)  failed ");
    assertTrue(cmPatched, "patchDomainCustomResource(configMap) failed");

    patchStr = new StringBuffer("[{");
    patchStr.append(" \"op\": \"replace\",")
        .append(" \"path\": \"/spec/replicas\",")
        .append(" \"value\": 1")
        .append(" }]");
    logger.log(Level.INFO, "Replicas patch string: {0}", patchStr);

    patch = new V1Patch(new String(patchStr));
    boolean replicaPatched = assertDoesNotThrow(() ->
            patchDomainCustomResource(domainUid, domainNamespace, patch, "application/json-patch+json"),
        "patchDomainCustomResource(restartVersion)  failed ");
    assertTrue(replicaPatched, "patchDomainCustomResource(replicas) failed");

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
  @DisplayName("Add a configured cluster to the domain")
  public void testMiiAddConfiguredCluster() {

    // This test uses the WebLogic domain created in BeforeAll method
    // BeforeEach method ensures that the server pods are running

    String configMapName = "configclusterconfigmap";
    createClusterConfigMap(configMapName, "model.config.cluster.yaml");

    LinkedHashMap<String, OffsetDateTime> pods = new LinkedHashMap<>();

    // get the creation time of the admin server pod before patching
    OffsetDateTime adminPodCreationTime = getPodCreationTime(domainNamespace, adminServerPodName);
    // get the creation time of the managed server pods before patching
    pods.put(adminServerPodName, adminPodCreationTime);
    for (int i = 1; i <= replicaCount; i++) {
      pods.put(managedServerPrefix + i, getPodCreationTime(domainNamespace,   managedServerPrefix + i));
    }

    StringBuffer patchStr = null;
    patchStr = new StringBuffer("[{");
    patchStr.append("\"op\": \"replace\",")
        .append(" \"path\": \"/spec/configuration/model/configMap\",")
        .append(" \"value\":  \"" + configMapName + "\"")
        .append(" }]");
    logger.log(Level.INFO, "Configmap patch string: {0}", patchStr);

    patch = new V1Patch(new String(patchStr));
    boolean cmPatched = assertDoesNotThrow(() ->
            patchDomainCustomResource(domainUid, domainNamespace, patch, "application/json-patch+json"),
        "patchDomainCustomResource(configMap)  failed ");
    assertTrue(cmPatched, "patchDomainCustomResource(configMap) failed");

    patchStr = new StringBuffer("[{");
    patchStr.append(" \"op\": \"replace\",")
        .append(" \"path\": \"/spec/replicas\",")
        .append(" \"value\": 1")
        .append(" }]");
    logger.log(Level.INFO, "Replicas patch string: {0}", patchStr);

    patch = new V1Patch(new String(patchStr));
    boolean replicaPatched = assertDoesNotThrow(() ->
            patchDomainCustomResource(domainUid, domainNamespace, patch, "application/json-patch+json"),
        "patchDomainCustomResource(restartVersion)  failed ");
    assertTrue(replicaPatched, "patchDomainCustomResource(replicas) failed");

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
   * Make sure all the server pods are re-started in a rolling fashion. 
   * Check the validity of new credentials by accessing WebLogic RESTful Service
   */
  @Test
  @Order(8)
  @DisplayName("Change the WebLogic Admin credential of the domain")
  public void testMiiUpdateWebLogicCredential() {
    final boolean VALID = true;
    final boolean INVALID = false;

    LinkedHashMap<String, OffsetDateTime> pods = new LinkedHashMap<>();
    // get the creation time of the admin server pod before patching
    OffsetDateTime adminPodCreationTime = getPodCreationTime(domainNamespace,adminServerPodName);
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

  /**
   * Start a WebLogic domain with a dynamic cluster with the following 
   * attributes MaxDynamicClusterSize(5) and MinDynamicClusterSize(1)
   * Set allowReplicasBelowMinDynClusterSize to false.
   * Make sure that the cluster can be scaled up to 5 servers and
   * scaled down to 1 server. 
   * Create a configmap with a sparse model file with following attributes for 
   * Cluster/cluster-1/DynamicServers
   *   MaxDynamicClusterSize(4) and MinDynamicClusterSize(2)
   * Patch the domain resource with the configmap and update restartVersion.
   * Make sure a rolling restart is triggered.  
   * Now with the modified value
   * Make sure that the cluster can be scaled up to 4 servers.
   * Make sure JMS Connections and messages are distributed across 4 servers.
   * Make sure that the cluster can be scaled down below 2 servers.
   */
  @Test
  @Order(9)
  @DisplayName("Test modification to Dynamic cluster size parameters")
  public void testMiiUpdateDynamicClusterSize() {

    // Scale the cluster to replica count to 5
    logger.info("[Before Patching] updating the replica count to 5");
    boolean p1Success = assertDoesNotThrow(() ->
            scaleCluster(domainUid, domainNamespace, "cluster-1", 5),
        String.format("replica pacthing to 5 failed for domain %s in namespace %s", domainUid, domainNamespace));
    assertTrue(p1Success,
        String.format("replica patching to 5 failed for domain %s in namespace %s", domainUid, domainNamespace));

    // Make sure that we can scale upto replica count 5
    // since the MaxDynamicClusterSize is set to 5
    checkPodReadyAndServiceExists(managedServerPrefix + "3", domainUid, domainNamespace);
    checkPodReadyAndServiceExists(managedServerPrefix + "4", domainUid, domainNamespace);
    checkPodReadyAndServiceExists(managedServerPrefix + "5", domainUid, domainNamespace);

    // Make sure that we can scale down upto replica count 1
    // since the MinDynamicClusterSize is set to 1
    logger.info("[Before Patching] updating the replica count to 1");
    boolean p11Success = assertDoesNotThrow(() ->
            scaleCluster(domainUid, domainNamespace, "cluster-1", 1),
        String.format("replica pacthing to 1 failed for domain %s in namespace %s", domainUid, domainNamespace));
    assertTrue(p11Success,
        String.format("replica patching to 1 failed for domain %s in namespace %s", domainUid, domainNamespace));

    checkPodDeleted(managedServerPrefix + "2", domainUid, domainNamespace);
    checkPodDeleted(managedServerPrefix + "3", domainUid, domainNamespace);
    checkPodDeleted(managedServerPrefix + "4", domainUid, domainNamespace);
    checkPodDeleted(managedServerPrefix + "5", domainUid, domainNamespace);

    // Bring back the cluster to originally configured replica count
    logger.info("[Before Patching] updating the replica count to 2");
    boolean p2Success = assertDoesNotThrow(() ->
            scaleCluster(domainUid, domainNamespace, "cluster-1", replicaCount),
        String.format("replica pacthing to 2 failed for domain %s in namespace %s", domainUid, domainNamespace));
    assertTrue(p1Success,
        String.format("replica patching to 2 failed for domain %s in namespace %s", domainUid, domainNamespace));
    checkPodReadyAndServiceExists(managedServerPrefix + "2", domainUid, domainNamespace);

    // get the creation time of the server pods before patching
    LinkedHashMap<String, OffsetDateTime> pods = new LinkedHashMap<>();
    OffsetDateTime adminPodCreationTime = getPodCreationTime(domainNamespace, adminServerPodName);
    pods.put(adminServerPodName, adminPodCreationTime);
    for (int i = 1; i <= replicaCount; i++) {
      pods.put(managedServerPrefix + i, getPodCreationTime(domainNamespace, managedServerPrefix + i));
    }

    // Update the Dynamic ClusterSize and add distributed destination 
    // to verify JMS connection and message distribution after the 
    // WebLogic cluster is scaled.
    String configMapName = "dynamic-cluster-size-cm";
    createClusterConfigMap(configMapName, "model.cluster.size.yaml");

    StringBuffer patchStr = null;
    patchStr = new StringBuffer("[{");
    patchStr.append("\"op\": \"replace\",")
        .append(" \"path\": \"/spec/configuration/model/configMap\",")
        .append(" \"value\":  \"" + configMapName + "\"")
        .append(" }]");
    logger.log(Level.INFO, "Configmap patch string: {0}", patchStr);

    patch = new V1Patch(new String(patchStr));
    boolean cmPatched = assertDoesNotThrow(() ->
            patchDomainCustomResource(domainUid, domainNamespace, patch, "application/json-patch+json"),
        "patchDomainCustomResource(configMap)  failed ");
    assertTrue(cmPatched, "patchDomainCustomResource(configMap) failed");

    String newRestartVersion = patchDomainResourceWithNewRestartVersion(domainUid, domainNamespace);
    logger.log(Level.INFO, "New restart version : {0}", newRestartVersion);
    assertTrue(verifyRollingRestartOccurred(pods, 1, domainNamespace),
        "Rolling restart failed");

    // build the standalone JMS Client on Admin pod after rolling restart
    buildClientOnPod();

    // Scale the cluster to replica count 5
    // Here managed-server5 should not come up as new MaxClusterSize is 4
    logger.info("[After Patching] updating the replica count to 5");
    boolean p3Success = assertDoesNotThrow(() ->
            scaleCluster(domainUid, domainNamespace, "cluster-1", 5),
        String.format("Scaling the cluster cluster-1 of domain %s in namespace %s failed", domainUid, domainNamespace));
    assertTrue(p1Success,
        String.format("replica patching to 3 failed for domain %s in namespace %s", domainUid, domainNamespace));
    
    //  Make sure the 3rd Managed server comes up
    checkServiceExists(managedServerPrefix + "3", domainNamespace);
    checkServiceExists(managedServerPrefix + "4", domainNamespace);
    checkPodDeleted(managedServerPrefix + "5", domainUid, domainNamespace);

    // Make sure the JMS Connection LoadBalancing and message LoadBalancing
    // works inside pod before scaling the cluster
    String jarLocation = "/u01/oracle/wlserver/server/lib/weblogic.jar";
    StringBuffer javapCmd = new StringBuffer("kubectl exec -n ");
    javapCmd.append(domainNamespace);
    javapCmd.append(" -it ");
    javapCmd.append(adminServerPodName);
    javapCmd.append(" -- /bin/bash -c \"");
    javapCmd.append("java -cp ");
    javapCmd.append(jarLocation);
    javapCmd.append(":.");
    javapCmd.append(" JmsTestClient ");
    javapCmd.append(" t3://");
    javapCmd.append(domainUid);
    javapCmd.append("-cluster-");
    javapCmd.append(clusterName);
    javapCmd.append(":8001 4 true");
    javapCmd.append(" \"");
    logger.info("java command to be run {0}", javapCmd.toString());
    withStandardRetryPolicy
        .conditionEvaluationListener(
            condition -> logger.info("Wait for t3 JMS Client to access WLS "
                    + "(elapsed time {0}ms, remaining time {1}ms)",
                condition.getElapsedTimeInMS(),
                condition.getRemainingTimeInMS()))
        .until(runJmsClient(new String(javapCmd)));
    
    // Since the MinDynamicClusterSize is set to 2 in the configmap 
    // and allowReplicasBelowMinDynClusterSize is set false, the replica
    // count can not go below 2. So during the following scale down operation 
    // only managed-server3 and managed-server4 pod should be removed.  
    logger.info("[After Patching] updating the replica count to 1");
    boolean p4Success = assertDoesNotThrow(() ->
            scaleCluster(domainUid, domainNamespace, "cluster-1", 1),
        String.format("replica patching to 1 failed for domain %s in namespace %s", domainUid, domainNamespace));
    assertTrue(p4Success,
        String.format("Cluster replica patching failed for domain %s in namespace %s", domainUid, domainNamespace));

    checkPodDoesNotExist(managedServerPrefix + "3", domainUid, domainNamespace);
    checkPodDoesNotExist(managedServerPrefix + "4", domainUid, domainNamespace);
    for (int i = 1; i <= replicaCount; i++) {
      logger.info("Check managed server service {0} available in namespace {1}",
          managedServerPrefix + i, domainNamespace);
      checkServiceExists(managedServerPrefix + i, domainNamespace);
    }
    logger.info("New Dynamic Cluster Size attribute verified");
  }

  // Build JMS Client inside the Admin Server Pod
  private void buildClientOnPod() {

    String destLocation = "/u01/oracle/JmsTestClient.java";
    assertDoesNotThrow(() -> copyFileToPod(domainNamespace,
             adminServerPodName, "",
             Paths.get(RESOURCE_DIR, "tunneling", "JmsTestClient.java"),
             Paths.get(destLocation)));

    String jarLocation = "/u01/oracle/wlserver/server/lib/weblogic.jar";

    StringBuffer javacCmd = new StringBuffer("kubectl exec -n ");
    javacCmd.append(domainNamespace);
    javacCmd.append(" -it ");
    javacCmd.append(adminServerPodName);
    javacCmd.append(" -- /bin/bash -c \"");
    javacCmd.append("javac -cp ");
    javacCmd.append(jarLocation);
    javacCmd.append(" JmsTestClient.java ");
    javacCmd.append(" \"");
    logger.info("javac command {0}", javacCmd.toString());
    ExecResult result = assertDoesNotThrow(
        () -> exec(new String(javacCmd), true));
    logger.info("javac returned {0}", result.toString());
    logger.info("javac returned EXIT value {0}", result.exitValue());
    assertTrue(result.exitValue() == 0, "Client compilation fails");
  }

  // Run standalone JMS Client in the pod using wlthint3client.jar in classpath.
  // The client sends 300 messsage to a Uniform Distributed Queue.
  // Make sure that each destination get excatly 150 messages each.
  // and JMS connection is load balanced across all servers
  private static Callable<Boolean> runJmsClient(String javaCmd) {
    return (()  -> {
      ExecResult result = assertDoesNotThrow(() -> exec(new String(javaCmd), true));
      logger.info("java returned {0}", result.toString());
      logger.info("java returned EXIT value {0}", result.exitValue());
      return ((result.exitValue() == 0));
    });
  }


  private static void createDatabaseSecret(
        String secretName, String username, String password, 
        String dburl, String domNamespace) throws ApiException {
    Map<String, String> secretMap = new HashMap();
    secretMap.put("username", username);
    secretMap.put("password", password);
    secretMap.put("url", dburl);
    boolean secretCreated = assertDoesNotThrow(() -> createSecret(new V1Secret()
            .metadata(new V1ObjectMeta()
                    .name(secretName)
                    .namespace(domNamespace))
            .stringData(secretMap)), "Create secret failed with ApiException");
    assertTrue(secretCreated, String.format("create secret failed for %s in namespace %s", secretName, domNamespace));

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
      int replicaCount, String configmapName, String dbSecretName) {
    List<String> securityList = new ArrayList<>();
    securityList.add(dbSecretName);
    // create the domain CR
    Domain domain = new Domain()
            .apiVersion(DOMAIN_API_VERSION)
            .kind("Domain")
            .metadata(new V1ObjectMeta()
                    .name(domainUid)
                    .namespace(domNamespace))
            .spec(new DomainSpec()
                    .allowReplicasBelowMinDynClusterSize(false)
                    .domainUid(domainUid)
                    .domainHomeSourceType("FromModel")
                    .image(MII_BASIC_IMAGE_NAME + ":" + MII_BASIC_IMAGE_TAG)
                    .addImagePullSecretsItem(new V1LocalObjectReference()
                            .name(repoSecretName))
                    .webLogicCredentialsSecret(new V1SecretReference()
                            .name(adminSecretName)
                            .namespace(domNamespace))
                    .includeServerOutInPodLog(true)
                    .logHomeEnabled(Boolean.TRUE)
                    .logHome("/shared/logs")
                    .serverStartPolicy("IF_NEEDED")
                    .serverPod(new ServerPod()
                            .addEnvItem(new V1EnvVar()
                                    .name("JAVA_OPTIONS")
                                    .value("-Dweblogic.StdoutDebugEnabled=false"))
                            .addEnvItem(new V1EnvVar()
                                    .name("USER_MEM_ARGS")
                                    .value("-Djava.security.egd=file:/dev/./urandom "))
                            .addVolumesItem(new V1Volume()
                                    .name(pvName)
                                    .persistentVolumeClaim(new V1PersistentVolumeClaimVolumeSource()
                                        .claimName(pvcName)))
                            .addVolumeMountsItem(new V1VolumeMount()
                                .mountPath("/shared")
                                .name(pvName)))
                    .adminServer(new AdminServer()
                            .serverStartState("RUNNING")
                            .adminService(new AdminService()
                                    .addChannelsItem(new Channel()
                                            .channelName("default")
                                            .nodePort(0))))
                    .addClustersItem(new Cluster()
                            .clusterName("cluster-1")
                            .replicas(replicaCount)
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

  private ExecResult checkJdbcRuntime(String resourcesName) {
    int adminServiceNodePort
        = getServiceNodePort(domainNamespace, getExternalServicePodName(adminServerPodName), "default");
    ExecResult result = null;

    curlString = new StringBuffer("curl --user weblogic:welcome1 ");
    curlString.append("http://" + K8S_NODEPORT_HOST + ":" + adminServiceNodePort)
         .append("/management/wls/latest/datasources/id/")
         .append(resourcesName)
         .append("/")
         .append(" --silent --show-error ");
    logger.info("checkJdbcRuntime: curl command {0}", new String(curlString));
    try {
      result = exec(new String(curlString), true);
    } catch (Exception ex) {
      logger.info("checkJdbcRuntime: caught unexpected exception {0}", ex);
      return null;
    }
    return result;
  }

  private static void createJobToChangePermissionsOnPvHostPath(String pvName, String pvcName, String namespace) {
    logger.info("Running Kubernetes job to create domain");
    V1Job jobBody = new V1Job()
        .metadata(
            new V1ObjectMeta()
                .name("change-permissions-onpv-job-" + pvName) // name of the job
                .namespace(namespace))
        .spec(new V1JobSpec()
            .backoffLimit(0) // try only once
            .template(new V1PodTemplateSpec()
                .spec(new V1PodSpec()
                    .restartPolicy("Never")
                    .addContainersItem(
                        createfixPVCOwnerContainer(pvName,
                        "/shared")) // mounted under /shared inside pod
                    .volumes(Arrays.asList(
                        new V1Volume()
                            .name(pvName)
                            .persistentVolumeClaim(
                                new V1PersistentVolumeClaimVolumeSource()
                                    .claimName(pvcName))))
                    .imagePullSecrets(Arrays.asList(
                        new V1LocalObjectReference()
                            .name(BASE_IMAGES_REPO_SECRET)))))); // this secret is used only for non-kind cluster

    String jobName = createJobAndWaitUntilComplete(jobBody, namespace);

    // check job status and fail test if the job failed
    V1Job job = assertDoesNotThrow(() -> getJob(jobName, namespace),
        "Getting the job failed");
    if (job != null) {
      V1JobCondition jobCondition = job.getStatus().getConditions().stream().filter(
          v1JobCondition -> "Failed".equalsIgnoreCase(v1JobCondition.getType()))
          .findAny()
          .orElse(null);
      if (jobCondition != null) {
        logger.severe("Job {0} failed to change permissions on PV hostpath", jobName);
        List<V1Pod> pods = assertDoesNotThrow(() -> listPods(
            namespace, "job-name=" + jobName).getItems(),
            "Listing pods failed");
        if (!pods.isEmpty()) {
          String podLog = assertDoesNotThrow(() -> getPodLog(pods.get(0).getMetadata().getName(), namespace),
              "Failed to get pod log");
          logger.severe(podLog);
          fail("Change permissions on PV hostpath job failed");
        }
      }
    }
  }

  private void checkLogsOnPV(String commandToExecuteInsidePod, String podName) {
    logger.info("Checking logs are written on PV by running the command {0} on pod {1}, namespace {2}",
        commandToExecuteInsidePod, podName, domainNamespace);
    V1Pod serverPod = assertDoesNotThrow(() ->
            Kubernetes.getPod(domainNamespace, null, podName),
        String.format("Could not get the server Pod {0} in namespace {1}",
            podName, domainNamespace));

    ExecResult result = assertDoesNotThrow(() -> Kubernetes.exec(serverPod, null, true,
        "/bin/sh", "-c", commandToExecuteInsidePod),
        String.format("Could not execute the command %s in pod %s, namespace %s",
            commandToExecuteInsidePod, podName, domainNamespace));
    logger.info("Command {0} returned with exit value {1}, stderr {2}, stdout {3}",
        commandToExecuteInsidePod, result.exitValue(), result.stderr(), result.stdout());

    // checking for exitValue 0 for success fails sometimes as k8s exec api returns non-zero exit value even on success,
    // so checking for exitValue non-zero and stderr not empty for failure, otherwise its success
    assertFalse(result.exitValue() != 0 && result.stderr() != null && !result.stderr().isEmpty(),
        String.format("Command %s failed with exit value %s, stderr %s, stdout %s",
            commandToExecuteInsidePod, result.exitValue(), result.stderr(), result.stdout()));

  }

}
