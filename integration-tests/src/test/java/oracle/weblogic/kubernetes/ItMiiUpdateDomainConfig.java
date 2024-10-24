// Copyright (c) 2020, 2024, Oracle and/or its affiliates.
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
import io.kubernetes.client.openapi.models.V1ConfigMap;
import io.kubernetes.client.openapi.models.V1EnvVar;
import io.kubernetes.client.openapi.models.V1LocalObjectReference;
import io.kubernetes.client.openapi.models.V1ObjectMeta;
import io.kubernetes.client.openapi.models.V1PersistentVolumeClaimVolumeSource;
import io.kubernetes.client.openapi.models.V1Pod;
import io.kubernetes.client.openapi.models.V1Secret;
import io.kubernetes.client.openapi.models.V1Volume;
import io.kubernetes.client.openapi.models.V1VolumeMount;
import oracle.weblogic.domain.AdminServer;
import oracle.weblogic.domain.AdminService;
import oracle.weblogic.domain.Channel;
import oracle.weblogic.domain.Configuration;
import oracle.weblogic.domain.DomainResource;
import oracle.weblogic.domain.DomainSpec;
import oracle.weblogic.domain.Model;
import oracle.weblogic.domain.ServerPod;
import oracle.weblogic.kubernetes.actions.impl.primitive.Kubernetes;
import oracle.weblogic.kubernetes.annotations.IntegrationTest;
import oracle.weblogic.kubernetes.annotations.Namespaces;
import oracle.weblogic.kubernetes.logging.LoggingFacade;
import oracle.weblogic.kubernetes.utils.ExecResult;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import static oracle.weblogic.kubernetes.TestConstants.ADMIN_PASSWORD_DEFAULT;
import static oracle.weblogic.kubernetes.TestConstants.ADMIN_USERNAME_DEFAULT;
import static oracle.weblogic.kubernetes.TestConstants.DOMAIN_API_VERSION;
import static oracle.weblogic.kubernetes.TestConstants.IMAGE_PULL_POLICY;
import static oracle.weblogic.kubernetes.TestConstants.KUBERNETES_CLI;
import static oracle.weblogic.kubernetes.TestConstants.MII_BASIC_IMAGE_NAME;
import static oracle.weblogic.kubernetes.TestConstants.MII_BASIC_IMAGE_TAG;
import static oracle.weblogic.kubernetes.TestConstants.OCNE;
import static oracle.weblogic.kubernetes.TestConstants.OKE_CLUSTER;
import static oracle.weblogic.kubernetes.TestConstants.TEST_IMAGES_REPO_SECRET_NAME;
import static oracle.weblogic.kubernetes.TestConstants.TRAEFIK_INGRESS_HTTP_HOSTPORT;
import static oracle.weblogic.kubernetes.actions.ActionConstants.MODEL_DIR;
import static oracle.weblogic.kubernetes.actions.ActionConstants.RESOURCE_DIR;
import static oracle.weblogic.kubernetes.actions.TestActions.createConfigMap;
import static oracle.weblogic.kubernetes.actions.TestActions.createSecret;
import static oracle.weblogic.kubernetes.actions.TestActions.execCommand;
import static oracle.weblogic.kubernetes.actions.TestActions.getDomainCustomResource;
import static oracle.weblogic.kubernetes.actions.TestActions.getServiceNodePort;
import static oracle.weblogic.kubernetes.actions.TestActions.patchDomainCustomResource;
import static oracle.weblogic.kubernetes.actions.TestActions.patchDomainResourceWithNewRestartVersion;
import static oracle.weblogic.kubernetes.actions.TestActions.scaleCluster;
import static oracle.weblogic.kubernetes.assertions.TestAssertions.verifyRollingRestartOccurred;
import static oracle.weblogic.kubernetes.utils.ClusterUtils.createClusterResourceAndAddReferenceToDomain;
import static oracle.weblogic.kubernetes.utils.CommonMiiTestUtils.createJobToChangePermissionsOnPvHostPath;
import static oracle.weblogic.kubernetes.utils.CommonMiiTestUtils.verifyUpdateWebLogicCredential;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.checkPodReadyAndServiceExists;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.checkServiceExists;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.createIngressHostRouting;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.exeAppInServerPod;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.getHostAndPort;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.getNextFreePort;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.getUniqueName;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.testUntil;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.verifyCommandResultContainsMsg;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.verifySystemResourceConfiguration;
import static oracle.weblogic.kubernetes.utils.ConfigMapUtils.createConfigMapAndVerify;
import static oracle.weblogic.kubernetes.utils.DomainUtils.createDomainAndVerify;
import static oracle.weblogic.kubernetes.utils.ExecCommand.exec;
import static oracle.weblogic.kubernetes.utils.FileUtils.copyFileToPod;
import static oracle.weblogic.kubernetes.utils.ImageUtils.createBaseRepoSecret;
import static oracle.weblogic.kubernetes.utils.ImageUtils.createTestRepoSecret;
import static oracle.weblogic.kubernetes.utils.OKDUtils.createRouteForOKD;
import static oracle.weblogic.kubernetes.utils.OperatorUtils.installAndVerifyOperator;
import static oracle.weblogic.kubernetes.utils.PersistentVolumeUtils.createPV;
import static oracle.weblogic.kubernetes.utils.PersistentVolumeUtils.createPVC;
import static oracle.weblogic.kubernetes.utils.PodUtils.checkPodDeleted;
import static oracle.weblogic.kubernetes.utils.PodUtils.checkPodReady;
import static oracle.weblogic.kubernetes.utils.PodUtils.getExternalServicePodName;
import static oracle.weblogic.kubernetes.utils.PodUtils.getPodCreationTime;
import static oracle.weblogic.kubernetes.utils.PodUtils.setPodAntiAffinity;
import static oracle.weblogic.kubernetes.utils.ThreadSafeLogger.getLogger;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;


/**
 * This test class verifies dynamic changes to domain resource and configuration
 * by modifying the associated configmap with a model-in-image domain.
 * After updating the configmap, the test updates the restartVersion of the
 * domain resource which triggers the rolling restart to verify the change.
 */

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DisplayName("Test logHome on PV, add SystemResources, Clusters to model in image domain")
@IntegrationTest
@Tag("olcne-mrg")
@Tag("kind-parallel")
@Tag("toolkits-srg")
@Tag("okd-wls-srg")
@Tag("oke-weekly-sequential")
class ItMiiUpdateDomainConfig {

  private static String opNamespace = null;
  private static String domainNamespace = null;
  private static final int replicaCount = 2;
  private static final String domainUid = "mii-add-config";
  private static final String pvName = getUniqueName(domainUid + "-pv-");
  private static final String pvcName = getUniqueName(domainUid + "-pvc-");
  private StringBuffer curlString = null;
  private V1Patch patch = null;
  private static final String adminServerPodName = domainUid + "-admin-server";
  private final String managedServerPrefix = domainUid + "-managed-server";
  private static final String adminServerName = "admin-server";
  private final String clusterName = "cluster-1";
  private String adminSvcExtHost = null;
  private static String hostHeader = null;

  private static LoggingFacade logger = null;

  /**
   * Install Operator.
   * Create domain resource defintion.
   *
   * @param namespaces list of namespaces created by the IntegrationTestWatcher by the
   *                   JUnit engine parameter resolution mechanism
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

    // install and verify operator
    installAndVerifyOperator(opNamespace, domainNamespace);

    // Create the repo secret to pull the image
    // this secret is used only for non-kind cluster
    createTestRepoSecret(domainNamespace);

    // create secret for admin credentials
    logger.info("Create secret for admin credentials");
    String adminSecretName = "weblogic-credentials";
    assertDoesNotThrow(() -> createDomainSecret(adminSecretName,
            ADMIN_USERNAME_DEFAULT, ADMIN_PASSWORD_DEFAULT, domainNamespace),
        String.format("createSecret failed for %s", adminSecretName));

    // create encryption secret
    logger.info("Create encryption secret");
    String encryptionSecretName = "encryptionsecret";
    assertDoesNotThrow(() -> createDomainSecret(encryptionSecretName, "weblogicenc",
            "weblogicenc", domainNamespace),
        String.format("createSecret failed for %s", encryptionSecretName));

    logger.info("Create database secret");
    final String dbSecretName = domainUid + "-db-secret";
    assertDoesNotThrow(() -> createDatabaseSecret(dbSecretName, "scott",
            "##W%*}!\"'\"`']\\\\//1$$~x", "jdbc:oracle:thin:localhost:/ORCLCDB", domainNamespace),
        String.format("createSecret failed for %s", dbSecretName));
    String configMapName = "jdbc-jms-wldf-configmap";

    createConfigMapAndVerify(
        configMapName, domainUid, domainNamespace,
        Arrays.asList(MODEL_DIR + "/model.sysresources.yaml"));


    // create pull secrets for WebLogic image when running in non Kind Kubernetes cluster
    // this secret is used only for non-kind cluster
    createBaseRepoSecret(domainNamespace);

    // create PV, PVC for logs
    createPV(pvName, domainUid, ItMiiUpdateDomainConfig.class.getSimpleName());
    createPVC(pvName, pvcName, domainUid, domainNamespace);

    // create job to change permissions on PV hostPath
    createJobToChangePermissionsOnPvHostPath(pvName, pvcName, domainNamespace);

    // create cluster object
    String clusterName = "cluster-1";

    // create the domain CR with a pre-defined configmap
    DomainResource domain = createDomainResource(domainUid, domainNamespace, adminSecretName,
        TEST_IMAGES_REPO_SECRET_NAME, encryptionSecretName,
        replicaCount, configMapName, dbSecretName);

    // add cluster to domain
    domain = createClusterResourceAndAddReferenceToDomain(domainUid + "-" + clusterName,
        clusterName, domainNamespace, domain, replicaCount);

    createDomainAndVerify(domain, domainNamespace);
    // verify the admin server service and pod created
    checkPodReadyAndServiceExists(adminServerPodName, domainUid, domainNamespace);

    // create ingress for admin service
    if (TestConstants.KIND_CLUSTER
        && !TestConstants.WLSIMG_BUILDER.equals(TestConstants.WLSIMG_BUILDER_DEFAULT)) {
      hostHeader = createIngressHostRouting(domainNamespace, domainUid, adminServerName, 7001);
    }
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

    // In OKD env, adminServers' external service nodeport cannot be accessed directly.
    // We have to create a route for the admins server external service.
    if ((adminSvcExtHost == null)) {
      adminSvcExtHost = createRouteForOKD(getExternalServicePodName(adminServerPodName), domainNamespace);
    }
  }

  /**
   * Check the environment variable with special characters.
   */
  @Test
  @Order(0)
  @DisplayName("Check environment variable with special characters")
  void testMiiCustomEnv() {
    DomainResource domain1 = assertDoesNotThrow(() -> getDomainCustomResource(domainUid, domainNamespace),
        String.format("getDomainCustomResource failed with ApiException when tried to get domain %s in namespace %s",
            domainUid, domainNamespace));
    List<V1EnvVar> envList = domain1.getSpec().getServerPod().getEnv();

    boolean found = false;
    for (int i = 0; i < envList.size(); i++) {
      logger.info("The name is: {0}, value is: {1}", envList.get(i).getName(), envList.get(i).getValue());
      if (envList.get(i).getName().equalsIgnoreCase("CUSTOM_ENV")) {
        assertTrue(envList.get(i).getValue() != null
                && envList.get(i).getValue().equalsIgnoreCase("${DOMAIN_UID}~##!'%*$(ls)"),
            "Expected value for CUSTOM_ENV variable does not mtach");
        found = true;
      }
    }
    assertTrue(found, "Couldn't find CUSTOM_ENV variable in domain resource");

    int adminServiceNodePort
        = getServiceNodePort(domainNamespace, getExternalServicePodName(adminServerPodName), "default");

    String hostAndPort;
    if (OKE_CLUSTER || OCNE) {
      hostAndPort = adminServerPodName + ":7001";
    } else {
      hostAndPort = getHostAndPort(adminSvcExtHost, adminServiceNodePort);
    }

    // use traefik LB for kind cluster with ingress host header in url
    String headers = "";
    if (TestConstants.KIND_CLUSTER
        && !TestConstants.WLSIMG_BUILDER.equals(TestConstants.WLSIMG_BUILDER_DEFAULT)) {
      hostAndPort = "localhost:" + TRAEFIK_INGRESS_HTTP_HOSTPORT;
      headers = " -H 'host: " + hostHeader + "' ";
    }
    String curlString = new StringBuffer()
          .append("curl -g --user ")
          .append(ADMIN_USERNAME_DEFAULT)
          .append(":")
          .append(ADMIN_PASSWORD_DEFAULT)
          .append(" ")
          .append(headers)
          .append("\"http://" + hostAndPort)
          .append("/management/weblogic/latest/domainConfig")
          .append("/JMSServers/TestClusterJmsServer")
          .append("?fields=notes&links=none\"")
          .append(" --silent ").toString();

    if (OKE_CLUSTER || OCNE) {
      curlString = KUBERNETES_CLI + " exec -n " + domainNamespace + "  " + adminServerPodName + " -- " + curlString;
    }

    logger.info("checkJmsServerConfig: curl command {0}", curlString);
    verifyCommandResultContainsMsg(curlString, "${DOMAIN_UID}~##!'%*$(ls)");
  }

  /**
   * Check server logs are written on PersistentVolume(PV).
   * The test looks for the string RUNNING in the server log
   */
  @Test
  @Order(1)
  @DisplayName("Check the server logs are written to PersistentVolume")
  void testMiiServerLogsAreOnPV() {
    // check server logs are written on PV and look for string RUNNING in log
    checkLogsOnPV("grep RUNNING /shared/" + domainNamespace + "/logs/servers/" + adminServerName + "/logs/"
        + adminServerName + ".log", adminServerPodName);
  }

  /**
   * Check HTTP server logs are written on logHome.
   * The test looks for the string sample-war/index.jsp in HTTP access
   * logs
   */
  @Test
  @Order(2)
  @DisplayName("Check the HTTP server logs are written to PersistentVolume")
  void testMiiHttpServerLogsAreOnPV() {
    String[] podNames = {managedServerPrefix + "1", managedServerPrefix + "2"};
    for (String pod : podNames) {
      String curlCmd = "for i in {1..100}; "
          + "do "
          + "curl -v http://" + pod + ":8001/sample-war/index.jsp;"
          + "done";
      testUntil(
          () -> {
            ExecResult execResult = assertDoesNotThrow(() -> execCommand(domainNamespace, pod, null, true,
                "/bin/sh", "-c", curlCmd));
            return execResult.toString().contains("HTTP/1.1 200 OK");
          },
          logger,
          "Sending HTTP requests to populate the http access log");
    }
    String[] servers = {"managed-server1", "managed-server2"};
    for (String server : servers) {
      logger.info("Checking HTTP server logs are written on PV and look for string sample-war/index.jsp in log");
      checkLogsOnPV("grep sample-war/index.jsp /shared/" + domainNamespace + "/logs/servers/" + server + "/logs/"
          + server + "_access.log",  adminServerPodName);
    }
  }

  /**
   * Create a WebLogic domain with a defined configmap in the
   * configuration/model section of the domain resource.
   * The configmap has multiple sparse WDT model files that define
   * a JDBCSystemResource, a JMSSystemResource and a WLDFSystemResource.
   * Verify all the SystemResource configurations using the rest API call
   * using the public node port of the administration server.
   */
  @Test
  @Order(3)
  @DisplayName("Verify the pre-configured SystemResources in the domain")
  void testMiiCheckSystemResources() {

    int adminServiceNodePort
        = getServiceNodePort(domainNamespace, getExternalServicePodName(adminServerPodName), "default");
    assertNotEquals(-1, adminServiceNodePort, "admin server default node port is not valid");

    if (OKE_CLUSTER || OCNE) {
      String resourcePath = "/management/weblogic/latest/domainConfig/JDBCSystemResources/TestDataSource";
      ExecResult result = exeAppInServerPod(domainNamespace, adminServerPodName,7001, resourcePath);
      assertEquals(0, result.exitValue(), "Failed to find the JDBCSystemResource configuration");
      assertTrue(result.toString().contains("JDBCSystemResources"),
          "Failed to find the JDBCSystemResource configuration");
      logger.info("Found the JDBCSystemResource configuration");

      resourcePath = "/management/weblogic/latest/domainConfig/JMSSystemResources/TestClusterJmsModule";
      result = exeAppInServerPod(domainNamespace, adminServerPodName,7001, resourcePath);
      assertEquals(0, result.exitValue(), "Failed to find the JMSSystemResources configuration");
      assertTrue(result.toString().contains("JMSSystemResources"),
          "Failed to find the JMSSystemResources configuration");
      logger.info("Found the JMSSystemResource configuration");

      resourcePath = "/management/weblogic/latest/domainConfig/WLDFSystemResources/TestWldfModule";
      result = exeAppInServerPod(domainNamespace, adminServerPodName,7001, resourcePath);
      assertEquals(0, result.exitValue(), "Failed to find the WLDFSystemResources configuration");
      assertTrue(result.toString().contains("WLDFSystemResources"),
          "Failed to find the WLDFSystemResources configuration");
      logger.info("Found the WLDFSystemResources configuration");

      resourcePath = "/management/wls/latest/datasources/id/TestDataSource";
      result = exeAppInServerPod(domainNamespace, adminServerPodName,7001, resourcePath);
      assertEquals(0, result.exitValue(), "Failed to find the JDBCSystemResource configuration");
      assertTrue(result.toString().contains("scott"),
          "Failed to find the JDBCSystemResource configuration");
      logger.info("Found the JDBCSystemResource configuration");
    } else {
      verifySystemResourceConfiguration(adminSvcExtHost, adminServiceNodePort,
          "JDBCSystemResources", "TestDataSource", "200", hostHeader);
      logger.info("Found the JDBCSystemResource configuration");

      verifySystemResourceConfiguration(adminSvcExtHost, adminServiceNodePort,
          "JMSSystemResources", "TestClusterJmsModule", "200", hostHeader);
      logger.info("Found the JMSSystemResource configuration");

      verifySystemResourceConfiguration(adminSvcExtHost, adminServiceNodePort,
          "WLDFSystemResources", "TestWldfModule", "200", hostHeader);
      logger.info("Found the WLDFSystemResource configuration");

      verifyJdbcRuntime("TestDataSource", "jdbc:oracle:thin:localhost");
      verifyJdbcRuntime("TestDataSource", "scott");
      logger.info("Found the JDBCSystemResource configuration");
    }
  }

  /**
   * Start a WebLogic domain using model-in-image.
   * Create 1 configmap with 2 models files, one of them to add JMS/JDBC SystemResources
   * and another one to delete JMS/JDBC SystemResources
   * Patch the domain resource with the configmap.
   * Update the restart version of the domain resource.
   * Verify rolling restart of the domain by comparing PodCreationTimestamp
   * for all the server pods before and after rolling restart.
   * Verify SystemResources are deleted from the domain.
   */
  @Test
  @Order(5)
  @DisplayName("Delete SystemResources from the domain")
  void testMiiDeleteSystemResources() {

    String configMapName = "deletesysrescm";
    final String modelFileAdd =  "model.add.sysresources.yaml";
    final String modelFileDelete = "model.delete.sysresources.yaml";
    List<String> modelFiles = Arrays.asList(MODEL_DIR + "/" + modelFileAdd, MODEL_DIR + "/" + modelFileDelete);
    createConfigMapAndVerify(
        configMapName, domainUid, domainNamespace, modelFiles);

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

    if (OKE_CLUSTER || OCNE) {
      String resourcePath = "/management/weblogic/latest/domainConfig/JDBCSystemResources/TestDataSource";
      ExecResult result = exeAppInServerPod(domainNamespace, adminServerPodName, 7001, resourcePath);
      assertEquals(0, result.exitValue(), "Failed to delete the JDBCSystemResource configuration");
      assertTrue(result.toString().contains("404"), "Failed to delete the JDBCSystemResource configuration");
      logger.info("The JDBCSystemResource configuration is deleted");

      resourcePath = "/management/weblogic/latest/domainConfig/JMSSystemResources/TestClusterJmsModule";
      result = exeAppInServerPod(domainNamespace, adminServerPodName, 7001, resourcePath);
      assertEquals(0, result.exitValue(), "Failed to delete the JMSSystemResources configuration");
      assertTrue(result.toString().contains("404"), "Failed to delete the JMSSystemResources configuration");
      logger.info("The JMSSystemResource configuration is deleted");
    } else {
      int adminServiceNodePort
          = getServiceNodePort(domainNamespace, getExternalServicePodName(adminServerPodName), "default");
      assertNotEquals(-1, adminServiceNodePort, "admin server default node port is not valid");
      verifySystemResourceConfiguration(adminSvcExtHost, adminServiceNodePort,
          "JDBCSystemResources", "TestDataSource", "404", hostHeader);
      verifySystemResourceConfiguration(adminSvcExtHost, adminServiceNodePort,
          "JMSSystemResources", "TestClusterJmsModule", "404", hostHeader);
    }
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
  @Order(6)
  @DisplayName("Add new JDBC/JMS SystemResources to the domain")
  void testMiiAddSystemResources() {

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

    if (OKE_CLUSTER || OCNE) {
      String resourcePath = "/management/weblogic/latest/domainConfig/JDBCSystemResources/TestDataSource2";
      ExecResult result = exeAppInServerPod(domainNamespace, adminServerPodName, 7001, resourcePath);
      assertEquals(0, result.exitValue(), "Failed to find the JDBCSystemResource configuration");
      assertTrue(result.toString().contains("JDBCSystemResources"),
          "Failed to find the JDBCSystemResource configuration");
      logger.info("Found the JDBCSystemResource configuration");

      resourcePath = "/management/weblogic/latest/domainConfig/JMSSystemResources/TestClusterJmsModule2";
      result = exeAppInServerPod(domainNamespace, adminServerPodName, 7001, resourcePath);
      assertEquals(0, result.exitValue(), "Failed to find the JMSSystemResources configuration");
      assertTrue(result.toString().contains("JMSSystemResources"),
          "Failed to find the JMSSystemResources configuration");
      logger.info("Found the JMSSystemResource configuration");
    } else {
      int adminServiceNodePort
          = getServiceNodePort(domainNamespace, getExternalServicePodName(adminServerPodName), "default");
      assertNotEquals(-1, adminServiceNodePort, "admin server default node port is not valid");

      verifySystemResourceConfiguration(adminSvcExtHost, adminServiceNodePort,
          "JDBCSystemResources", "TestDataSource2", "200", hostHeader);
      logger.info("Found the JDBCSystemResource configuration");

      verifySystemResourceConfiguration(adminSvcExtHost, adminServiceNodePort,
          "JMSSystemResources", "TestClusterJmsModule2", "200", hostHeader);
      logger.info("Found the JMSSystemResource configuration");
    }

    // check JMS logs are written on PV
    checkLogsOnPV("ls -ltr /shared/" + domainNamespace + "/logs/*jms_messages.log", managedServerPrefix + "1");
  }

  /**
   * Create a configmap with a sparse model file to add a dynamic cluster.
   * Patch the domain resource with the configmap.
   * Patch the domain resource with the spec/replicas set to 1.
   * Update the restart version of the domain resource.
   * Verify rolling restart of the domain by comparing PodCreationTimestamp
   * before and after rolling restart.
   * Verify servers from the new cluster are running.
   */
  @Test
  @Order(7)
  @DisplayName("Add a dynamic cluster to domain with non-zero replica count")
  void testMiiAddDynamicCluster() {

    // This test uses the WebLogic domain created in the BeforeAll method
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

    verifyManagedServerConfiguration("dynamic-server1");
    logger.info("Found new managed server configuration");
  }

  /**
   * Start a WebLogic domain with model-in-image.
   * Patch the domain CRD with a new credentials secret.
   * Update domainRestartVersion to trigger a rolling restart of server pods.
   * Make sure all the server pods are re-started in a rolling fashion.
   * Check the validity of new credentials by accessing WebLogic RESTful Service
   */
  @Test
  @Order(8)
  @DisplayName("Change the WebLogic Admin credential of the domain")
  void testMiiUpdateWebLogicCredential() {
    verifyUpdateWebLogicCredential(7001, domainNamespace, domainUid, adminServerPodName,
        managedServerPrefix, replicaCount);
  }

  /**
   * Start a WebLogic domain with a dynamic cluster with the following
   * attributes MaxDynamicClusterSize(5) and MinDynamicClusterSize(1)
   * Make sure that the cluster can be scaled up to 5 servers and
   * scaled down to 1 server.
   * Create a configmap with a sparse model file with the following attributes
   * Cluster/cluster-1/DynamicServers
   *   MaxDynamicClusterSize(4) and MinDynamicClusterSize(2)
   * Patch the domain resource with the configmap and update the restartVersion.
   * Make sure a rolling restart is triggered.
   * Now with the modified value
   * Make sure that the cluster can be scaled up to 4 servers.
   * Make sure JMS Connections and messages are distributed across 4 servers.
   */
  @Test
  @Order(9)
  @DisplayName("Test modification to Dynamic cluster size parameters")
  void testMiiUpdateDynamicClusterSize() {

    // Scale the cluster to replica count to 5
    logger.info("[Before Patching] updating the replica count to 5");
    boolean p1Success = scaleCluster(domainUid + "-cluster-1", domainNamespace, 5);
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
    boolean p11Success = scaleCluster(domainUid + "-cluster-1", domainNamespace, 1);
    assertTrue(p11Success,
        String.format("replica patching to 1 failed for domain %s in namespace %s", domainUid, domainNamespace));

    checkPodDeleted(managedServerPrefix + "2", domainUid, domainNamespace);
    checkPodDeleted(managedServerPrefix + "3", domainUid, domainNamespace);
    checkPodDeleted(managedServerPrefix + "4", domainUid, domainNamespace);
    checkPodDeleted(managedServerPrefix + "5", domainUid, domainNamespace);

    // Bring back the cluster to originally configured replica count
    logger.info("[Before Patching] updating the replica count to 2");
    boolean p2Success = scaleCluster(domainUid + "-cluster-1", domainNamespace, replicaCount);
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

    // Attempt to scale the cluster to replica count 5
    // This scale request should be rejected and fail by admission webhook.
    logger.info("[After Patching] updating the replica count to 5");
    boolean p3Success = scaleCluster(domainUid + "-cluster-1", domainNamespace, 5);
    assertFalse(p3Success,
            String.format("replica count patching to 5 failed for domain %s in namespace %s",
                    domainUid, domainNamespace));

    // Scale the cluster to new maximum replica count of 4
    boolean p4Success = scaleCluster(domainUid + "-cluster-1", domainNamespace, 4);
    assertTrue(p4Success,
            String.format("replica count patching to 4 succeeded for domain %s in namespace %s",
                    domainUid, domainNamespace));

    //  Make sure the 3rd and 4th Managed servers come up
    checkServiceExists(managedServerPrefix + "3", domainNamespace);
    checkServiceExists(managedServerPrefix + "4", domainNamespace);

    // Make sure the JMS Connection LoadBalancing and message LoadBalancing
    // works inside pod before scaling the cluster
    String jarLocation = "/u01/oracle/wlserver/server/lib/weblogic.jar";
    StringBuffer javapCmd = new StringBuffer(KUBERNETES_CLI + " exec -n ");
    javapCmd.append(domainNamespace);
    javapCmd.append(" -it ");
    javapCmd.append(adminServerPodName);
    javapCmd.append(" -- /bin/bash -c \"");
    javapCmd.append("cd /u01; java -cp ");
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
    testUntil(
        runJmsClient(new String(javapCmd)),
        logger,
        "Wait for t3 JMS Client to access WLS");

    logger.info("New Dynamic Cluster Size attribute verified");
  }

  // Build JMS Client inside the Admin Server Pod
  private void buildClientOnPod() {

    String destLocation = "/u01/JmsTestClient.java";
    assertDoesNotThrow(() -> copyFileToPod(domainNamespace,
             adminServerPodName, "",
             Paths.get(RESOURCE_DIR, "tunneling", "JmsTestClient.java"),
             Paths.get(destLocation)));

    String jarLocation = "/u01/oracle/wlserver/server/lib/weblogic.jar";

    StringBuffer javacCmd = new StringBuffer(KUBERNETES_CLI + " exec -n ");
    javacCmd.append(domainNamespace);
    javacCmd.append(" -it ");
    javacCmd.append(adminServerPodName);
    javacCmd.append(" -- /bin/bash -c \"");
    javacCmd.append("javac -cp ");
    javacCmd.append(jarLocation);
    javacCmd.append(" /u01/JmsTestClient.java ");
    javacCmd.append(" \"");
    logger.info("javac command {0}", javacCmd.toString());
    ExecResult result = assertDoesNotThrow(
        () -> exec(new String(javacCmd), true));
    logger.info("javac returned {0}", result.toString());
    logger.info("javac returned EXIT value {0}", result.exitValue());
    assertEquals(0, result.exitValue(), "Client compilation fails");
  }

  /**
   * Start a WebLogic domain using model-in-image with JMS/JDBC SystemResources.
   * Create a empty configmap to delete JMS/JDBC SystemResources
   * Patch the domain resource with the configmap.
   * Update the restart version of the domain resource.
   * Verify rolling restart of the domain by comparing PodCreationTimestamp
   * for all the server pods before and after rolling restart.
   * Verify SystemResources are deleted from the domain.
   */
  @Test
  @Order(4)
  @DisplayName("Delete SystemResources from the domain")
  void testMiiDeleteSystemResourcesByEmptyConfigMap() {

    String configMapName = "deletesysrescm";
    createConfigMapAndVerify(
        configMapName, domainUid, domainNamespace,
        Arrays.asList(MODEL_DIR + "/model.delete.sysresourcesbyconfigmap.yaml"));

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

    if (OKE_CLUSTER || OCNE) {
      String resourcePath = "/management/weblogic/latest/domainConfig/JDBCSystemResources/TestDataSource";
      ExecResult result = exeAppInServerPod(domainNamespace, adminServerPodName, 7001, resourcePath);
      assertEquals(0, result.exitValue(), "Failed to delete the JDBCSystemResource configuration");
      assertTrue(result.toString().contains("404"), "Failed to delete the JDBCSystemResource configuration");
      logger.info("The JDBCSystemResource configuration is deleted");

      resourcePath = "/management/weblogic/latest/domainConfig/JMSSystemResources/TestClusterJmsModule";
      result = exeAppInServerPod(domainNamespace, adminServerPodName, 7001, resourcePath);
      assertEquals(0, result.exitValue(), "Failed to delete the JMSSystemResources configuration");
      assertTrue(result.toString().contains("404"), "Failed to delete the JMSSystemResources configuration");
      logger.info("The JMSSystemResource configuration is deleted");
    } else {
      int adminServiceNodePort
          = getServiceNodePort(domainNamespace, getExternalServicePodName(adminServerPodName), "default");
      assertNotEquals(-1, adminServiceNodePort, "admin server default node port is not valid");
      verifySystemResourceConfiguration(adminSvcExtHost, adminServiceNodePort,
          "JDBCSystemResources", "TestDataSource", "404", hostHeader);
      verifySystemResourceConfiguration(adminSvcExtHost, adminServiceNodePort,
          "JMSSystemResources", "TestClusterJmsModule", "404", hostHeader);
    }
  }

  // Run standalone JMS Client in the pod using wlthint3client.jar in classpath.
  // The client sends 300 messsage to a Uniform Distributed Queue.
  // Make sure that each destination get excatly 150 messages each.
  // and JMS connection is load balanced across all servers
  private static Callable<Boolean> runJmsClient(String javaCmd) {
    return (()  -> {
      ExecResult result = assertDoesNotThrow(() -> exec(javaCmd, true));
      logger.info("java returned {0}", result.toString());
      logger.info("java returned EXIT value {0}", result.exitValue());
      return ((result.exitValue() == 0));
    });
  }


  private static void createDatabaseSecret(
        String secretName, String username, String password,
        String dburl, String domNamespace) {
    Map<String, String> secretMap = new HashMap<>();
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

  // Add an environmental variable with special character
  // Make sure the variable is available in domain resource with right value
  private static DomainResource createDomainResource(
      String domainUid, String domNamespace, String adminSecretName,
      String repoSecretName, String encryptionSecretName,
      int replicaCount, String configmapName, String dbSecretName) {
    List<String> securityList = new ArrayList<>();
    securityList.add(dbSecretName);

    // create the domain CR
    DomainResource domain = new DomainResource()
            .apiVersion(DOMAIN_API_VERSION)
            .kind("Domain")
            .metadata(new V1ObjectMeta()
                    .name(domainUid)
                    .namespace(domNamespace))
            .spec(new DomainSpec()
                    .domainUid(domainUid)
                    .domainHomeSourceType("FromModel")
                    .image(MII_BASIC_IMAGE_NAME + ":" + MII_BASIC_IMAGE_TAG)
                    .imagePullPolicy(IMAGE_PULL_POLICY)
                    .addImagePullSecretsItem(new V1LocalObjectReference()
                            .name(repoSecretName))
                    .webLogicCredentialsSecret(new V1LocalObjectReference()
                            .name(adminSecretName))
                    .includeServerOutInPodLog(true)
                    .logHomeEnabled(Boolean.TRUE)
                    .logHome("/shared/" + domainNamespace + "/logs")
                    .serverStartPolicy("IfNeeded")
                    .serverPod(new ServerPod()
                            .addEnvItem(new V1EnvVar()
                                    .name("JAVA_OPTIONS")
                                    .value("-Dweblogic.StdoutDebugEnabled=false "
                                        + "-Dweblogic.security.remoteAnonymousRMIT3Enabled=true"))
                            .addEnvItem(new V1EnvVar()
                                    .name("USER_MEM_ARGS")
                                    .value("-Djava.security.egd=file:/dev/./urandom "))
                            .addEnvItem(new V1EnvVar()
                                    .name("CUSTOM_ENV")
                                    .value("${DOMAIN_UID}~##!'%*$(ls)"))
                            .addVolumesItem(new V1Volume()
                                    .name(pvName)
                                    .persistentVolumeClaim(new V1PersistentVolumeClaimVolumeSource()
                                        .claimName(pvcName)))
                            .addVolumeMountsItem(new V1VolumeMount()
                                .mountPath("/shared")
                                .name(pvName)))
                    .adminServer(new AdminServer()
                            .adminService(new AdminService()
                                    .addChannelsItem(new Channel()
                                            .channelName("default")
                                            .nodePort(getNextFreePort()))))
                    .configuration(new Configuration()
                            .secrets(securityList)
                            .model(new Model()
                                    .domainType("WLS")
                                    .configMap(configmapName)
                                    .runtimeEncryptionSecret(encryptionSecretName))
                        .introspectorJobActiveDeadlineSeconds(3000L))
                .replicas(replicaCount));
    setPodAntiAffinity(domain);
    return domain;
  }

  private void verifyManagedServerConfiguration(String managedServer) {
    int adminServiceNodePort
        = getServiceNodePort(domainNamespace, getExternalServicePodName(adminServerPodName), "default");
    String hostAndPort;
    if (OKE_CLUSTER || OCNE) {
      hostAndPort = adminServerPodName + ":7001";
    } else {
      hostAndPort = getHostAndPort(adminSvcExtHost, adminServiceNodePort);
    }

    // use traefik LB for kind cluster with ingress host header in url
    String headers = "";
    if (TestConstants.KIND_CLUSTER
        && !TestConstants.WLSIMG_BUILDER.equals(TestConstants.WLSIMG_BUILDER_DEFAULT)) {
      hostAndPort = "localhost:" + TRAEFIK_INGRESS_HTTP_HOSTPORT;
      headers = " -H 'host: " + hostHeader + "' ";
    }
    StringBuffer checkClusterBaseCmd = new StringBuffer("curl -g --user ")
        .append(ADMIN_USERNAME_DEFAULT)
        .append(":")
        .append(ADMIN_PASSWORD_DEFAULT)
        .append(" ")
        .append(headers)
        .append("http://" + hostAndPort)
        .append("/management/tenant-monitoring/servers/")
        .append(managedServer)
        .append(" --silent --show-error -o /dev/null -w %{http_code}");

    StringBuffer checkCluster = new StringBuffer();

    if (OKE_CLUSTER || OCNE) {
      checkCluster = new StringBuffer(KUBERNETES_CLI)
        .append(" exec -n ")
        .append(domainNamespace)
        .append(" ")
        .append(adminServerPodName)
        .append(" -- ")
        .append(checkClusterBaseCmd);
    } else {
      checkCluster = new StringBuffer("status=$(");
      checkCluster.append(checkClusterBaseCmd)
          .append(");")
          .append("echo ${status}");
    }

    logger.info("checkManagedServerConfiguration: curl command {0}", new String(checkCluster));
    verifyCommandResultContainsMsg(new String(checkCluster), "200");
    logger.info("Command to check managedServer configuration: {0} succeeded", new String(checkCluster));
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

  private void verifyJdbcRuntime(String resourcesName, String expectedOutput) {
    int adminServiceNodePort
        = getServiceNodePort(domainNamespace, getExternalServicePodName(adminServerPodName), "default");
    String hostAndPort = getHostAndPort(adminSvcExtHost, adminServiceNodePort);
    String headers = "";
    if (TestConstants.KIND_CLUSTER
        && !TestConstants.WLSIMG_BUILDER.equals(TestConstants.WLSIMG_BUILDER_DEFAULT)) {
      hostAndPort = "localhost:" + TRAEFIK_INGRESS_HTTP_HOSTPORT;
      headers = " -H 'host: " + hostHeader + "' ";
    }

    ExecResult result = null;
    curlString = new StringBuffer("curl -g --user ")
         .append(ADMIN_USERNAME_DEFAULT)
         .append(":")
         .append(ADMIN_PASSWORD_DEFAULT)
         .append(" ")
         .append(headers)
         .append("http://" + hostAndPort)
         .append("/management/wls/latest/datasources/id/")
         .append(resourcesName)
         .append("/")
         .append(" --silent --show-error ");
    logger.info("checkJdbcRuntime: curl command {0}", new String(curlString));

    verifyCommandResultContainsMsg(new String(curlString), expectedOutput);
  }

  private void checkLogsOnPV(String commandToExecuteInsidePod, String podName) {
    logger.info("Checking logs are written on PV by running the command {0} on pod {1}, namespace {2}",
        commandToExecuteInsidePod, podName, domainNamespace);
    V1Pod serverPod = assertDoesNotThrow(() ->
            Kubernetes.getPod(domainNamespace, null, podName),
        String.format("Could not get the server Pod %s in namespace %s",
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
