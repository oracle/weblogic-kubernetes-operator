// Copyright (c) 2022, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.weblogic.kubernetes;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import io.kubernetes.client.openapi.ApiException;
import io.kubernetes.client.openapi.models.V1EnvVar;
import io.kubernetes.client.openapi.models.V1LocalObjectReference;
import io.kubernetes.client.openapi.models.V1ObjectMeta;
import io.kubernetes.client.openapi.models.V1PersistentVolumeClaimVolumeSource;
import io.kubernetes.client.openapi.models.V1SecretReference;
import io.kubernetes.client.openapi.models.V1Volume;
import io.kubernetes.client.openapi.models.V1VolumeMount;
import oracle.weblogic.domain.AdminServer;
import oracle.weblogic.domain.Cluster;
import oracle.weblogic.domain.Configuration;
import oracle.weblogic.domain.Domain;
import oracle.weblogic.domain.DomainSpec;
import oracle.weblogic.domain.Istio;
import oracle.weblogic.domain.Model;
import oracle.weblogic.domain.OnlineUpdate;
import oracle.weblogic.domain.ServerPod;
import oracle.weblogic.kubernetes.actions.impl.primitive.Command;
import oracle.weblogic.kubernetes.actions.impl.primitive.CommandParams;
import oracle.weblogic.kubernetes.annotations.IntegrationTest;
import oracle.weblogic.kubernetes.annotations.Namespaces;
import oracle.weblogic.kubernetes.logging.LoggingFacade;
import oracle.weblogic.kubernetes.utils.ExecResult;
import oracle.weblogic.kubernetes.utils.FmwUtils;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import static oracle.weblogic.kubernetes.TestConstants.ADMIN_PASSWORD_DEFAULT;
import static oracle.weblogic.kubernetes.TestConstants.ADMIN_USERNAME_DEFAULT;
import static oracle.weblogic.kubernetes.TestConstants.DOMAIN_API_VERSION;
import static oracle.weblogic.kubernetes.TestConstants.DOMAIN_VERSION;
import static oracle.weblogic.kubernetes.TestConstants.ENCRYPION_PASSWORD_DEFAULT;
import static oracle.weblogic.kubernetes.TestConstants.ENCRYPION_USERNAME_DEFAULT;
import static oracle.weblogic.kubernetes.TestConstants.FMWINFRA_IMAGE_NAME;
import static oracle.weblogic.kubernetes.TestConstants.FMWINFRA_IMAGE_TAG;
import static oracle.weblogic.kubernetes.TestConstants.FMWINFRA_IMAGE_TO_USE_IN_SPEC;
import static oracle.weblogic.kubernetes.TestConstants.K8S_NODEPORT_HOST;
import static oracle.weblogic.kubernetes.TestConstants.MII_BASIC_APP_NAME;
import static oracle.weblogic.kubernetes.TestConstants.MII_BASIC_IMAGE_NAME;
import static oracle.weblogic.kubernetes.TestConstants.MII_BASIC_IMAGE_TAG;
import static oracle.weblogic.kubernetes.TestConstants.OCIR_SECRET_NAME;
import static oracle.weblogic.kubernetes.TestConstants.WEBLOGIC_IMAGE_TAG;
import static oracle.weblogic.kubernetes.TestConstants.WEBLOGIC_SLIM;
import static oracle.weblogic.kubernetes.actions.ActionConstants.ITTESTS_DIR;
import static oracle.weblogic.kubernetes.actions.ActionConstants.MODEL_DIR;
import static oracle.weblogic.kubernetes.actions.ActionConstants.RESOURCE_DIR;
import static oracle.weblogic.kubernetes.actions.ActionConstants.WORK_DIR;
import static oracle.weblogic.kubernetes.actions.TestActions.addLabelsToNamespace;
import static oracle.weblogic.kubernetes.actions.TestActions.createDomainCustomResource;
import static oracle.weblogic.kubernetes.actions.TestActions.execCommand;
import static oracle.weblogic.kubernetes.actions.TestActions.scaleCluster;
import static oracle.weblogic.kubernetes.assertions.TestAssertions.domainExists;
import static oracle.weblogic.kubernetes.utils.ApplicationUtils.checkAppUsingHostHeader;
import static oracle.weblogic.kubernetes.utils.CommonMiiTestUtils.createDatabaseSecret;
import static oracle.weblogic.kubernetes.utils.CommonMiiTestUtils.createDomainSecret;
import static oracle.weblogic.kubernetes.utils.CommonMiiTestUtils.createJobToChangePermissionsOnPvHostPath;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.checkPodReadyAndServiceExists;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.getHostAndPort;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.isWebLogicPsuPatchApplied;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.runClientInsidePod;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.runJavacInsidePod;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.startPortForwardProcess;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.stopPortForwardProcess;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.testUntil;
import static oracle.weblogic.kubernetes.utils.ConfigMapUtils.createConfigMapAndVerify;
import static oracle.weblogic.kubernetes.utils.DbUtils.createOracleDBUsingOperator;
import static oracle.weblogic.kubernetes.utils.DbUtils.createRcuAccessSecret;
import static oracle.weblogic.kubernetes.utils.DbUtils.createRcuSchema;
import static oracle.weblogic.kubernetes.utils.DbUtils.deleteHostPathProvisioner;
import static oracle.weblogic.kubernetes.utils.DbUtils.deleteOracleDB;
import static oracle.weblogic.kubernetes.utils.DbUtils.installDBOperator;
import static oracle.weblogic.kubernetes.utils.DbUtils.uninstallDBOperator;
import static oracle.weblogic.kubernetes.utils.DeployUtil.deployToClusterUsingRest;
import static oracle.weblogic.kubernetes.utils.DomainUtils.createDomainAndVerify;
import static oracle.weblogic.kubernetes.utils.ExecCommand.exec;
import static oracle.weblogic.kubernetes.utils.FileUtils.copyFileToPod;
import static oracle.weblogic.kubernetes.utils.FileUtils.generateFileFromTemplate;
import static oracle.weblogic.kubernetes.utils.FmwUtils.verifyDomainReady;
import static oracle.weblogic.kubernetes.utils.ImageUtils.createMiiImageAndVerify;
import static oracle.weblogic.kubernetes.utils.ImageUtils.createOcirRepoSecret;
import static oracle.weblogic.kubernetes.utils.ImageUtils.createSecretForBaseImages;
import static oracle.weblogic.kubernetes.utils.ImageUtils.dockerLoginAndPushImageToRegistry;
import static oracle.weblogic.kubernetes.utils.IstioUtils.deployHttpIstioGatewayAndVirtualservice;
import static oracle.weblogic.kubernetes.utils.IstioUtils.deployIstioDestinationRule;
import static oracle.weblogic.kubernetes.utils.IstioUtils.getIstioHttpIngressPort;
import static oracle.weblogic.kubernetes.utils.IstioUtils.isLocalHostBindingsEnabled;
import static oracle.weblogic.kubernetes.utils.OKDUtils.createRouteForOKD;
import static oracle.weblogic.kubernetes.utils.OperatorUtils.installAndVerifyOperator;
import static oracle.weblogic.kubernetes.utils.PersistentVolumeUtils.createPV;
import static oracle.weblogic.kubernetes.utils.PersistentVolumeUtils.createPVC;
import static oracle.weblogic.kubernetes.utils.PodUtils.checkPodDoesNotExist;
import static oracle.weblogic.kubernetes.utils.PodUtils.getExternalServicePodName;
import static oracle.weblogic.kubernetes.utils.PodUtils.setPodAntiAffinity;
import static oracle.weblogic.kubernetes.utils.SecretUtils.createOpsswalletpasswordSecret;
import static oracle.weblogic.kubernetes.utils.SecretUtils.createSecretWithUsernamePassword;
import static oracle.weblogic.kubernetes.utils.ThreadSafeLogger.getLogger;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DisplayName("Test to a create Istio enabled FMW model in image domain and WebLogic domain using Oracle "
    + "database created using Oracle Database Operator")
@IntegrationTest
class ItIstioDBOperator {

  private static String dbNamespace = null;
  private static String opNamespace = null;
  private static String fmwDomainNamespace = null;
  private static String wlsDomainNamespace = null;
  private static String fmwMiiImage = null;

  private static final String RCUSCHEMAPREFIX = "FMWDOMAINMII";
  private static final String RCUSYSPASSWORD = "Oradoc_db1";
  private static final String RCUSCHEMAPASSWORD = "Oradoc_db1";
  private static final String modelFile = "model-singleclusterdomain-sampleapp-jrf.yaml";

  private static String dbUrl = null;
  private static String dbName = "istio-oracle-sidb";
  private static LoggingFacade logger = null;

  private String fmwDomainUid = "jrf-istio-db";
  private String fmwAdminServerPodName = fmwDomainUid + "-admin-server";
  private String fmwManagedServerPrefix = fmwDomainUid + "-managed-server";
  private int replicaCount = 2;
  private String fmwAminSecretName = fmwDomainUid + "-weblogic-credentials";
  private String fmwEncryptionSecretName = fmwDomainUid + "-encryptionsecret";
  private String rcuaccessSecretName = fmwDomainUid + "-rcu-access";
  private String opsswalletpassSecretName = fmwDomainUid + "-opss-wallet-password-secret";
  private String opsswalletfileSecretName = fmwDomainUid + "opss-wallet-file-secret";
  private String adminSvcExtHost = null;

  private static final String wlsDomainUid = "mii-jms-istio-db";
  private static String pvName = wlsDomainUid + "-pv";
  private static String pvcName = wlsDomainUid + "-pvc";
  private static final String wlsAdminServerPodName = wlsDomainUid + "-admin-server";
  private static final String wlsManagedServerPrefix = wlsDomainUid + "-managed-server";
  private static int wlDomainIstioIngressPort;
  private String configMapName = "dynamicupdate-istio-configmap";
  private static String cpUrl;
  private static String adminSvcExtRouteHost = null;

  private final Path samplePath = Paths.get(ITTESTS_DIR, "../kubernetes/samples");
  private final Path domainLifecycleSamplePath = Paths.get(samplePath + "/scripts/domain-lifecycle");

  private static String hostHeader;

  /**
   * Start DB service and create RCU schema.
   * Assigns unique namespaces for operator and domains.
   * Installs operator.
   *
   * @param namespaces injected by JUnit
   */
  @BeforeAll
  public static void initAll(@Namespaces(4) List<String> namespaces) {

    logger = getLogger();
    logger.info("Assign a unique namespace for DB and RCU");
    assertNotNull(namespaces.get(0), "Namespace is null");
    dbNamespace = namespaces.get(0);

    logger.info("Assign a unique namespace for operator");
    assertNotNull(namespaces.get(1), "Namespace is null");
    opNamespace = namespaces.get(1);

    logger.info("Assign a unique namespace for FMW domain");
    assertNotNull(namespaces.get(2), "Namespace is null");
    fmwDomainNamespace = namespaces.get(2);

    logger.info("Assign a unique namespace for WLS domain");
    assertNotNull(namespaces.get(3), "Namespace is null");
    wlsDomainNamespace = namespaces.get(3);

    // Create the repo secret to pull the image
    // this secret is used only for non-kind cluster
    createSecretForBaseImages(fmwDomainNamespace);
    createSecretForBaseImages(wlsDomainNamespace);

    // create PV, PVC for logs/data
    createPV(pvName, wlsDomainUid, ItIstioDBOperator.class.getSimpleName());
    createPVC(pvName, pvcName, wlsDomainUid, wlsDomainNamespace);

    // create job to change permissions on PV hostPath
    createJobToChangePermissionsOnPvHostPath(pvName, pvcName, wlsDomainNamespace);

    // Label the domain/operator namespace with istio-injection=enabled
    Map<String, String> labelMap = new HashMap();
    labelMap.put("istio-injection", "enabled");
    assertDoesNotThrow(() -> addLabelsToNamespace(fmwDomainNamespace, labelMap));
    assertDoesNotThrow(() -> addLabelsToNamespace(wlsDomainNamespace, labelMap));
    assertDoesNotThrow(() -> addLabelsToNamespace(opNamespace, labelMap));

    //install Oracle Database Operator
    assertDoesNotThrow(() -> installDBOperator(dbNamespace), "Failed to install database operator");

    logger.info("Create Oracle DB in namespace: {0} ", dbNamespace);
    dbUrl = assertDoesNotThrow(() -> createOracleDBUsingOperator(dbName, RCUSYSPASSWORD, dbNamespace));

    logger.info("Create RCU schema with fmwImage: {0}, rcuSchemaPrefix: {1}, dbUrl: {2}, "
        + " dbNamespace: {3}", FMWINFRA_IMAGE_TO_USE_IN_SPEC, RCUSCHEMAPREFIX, dbUrl, dbNamespace);
    assertDoesNotThrow(() -> createRcuSchema(FMWINFRA_IMAGE_TO_USE_IN_SPEC, RCUSCHEMAPREFIX,
        dbUrl, dbNamespace));

    // install operator and verify its running in ready state
    installAndVerifyOperator(opNamespace, fmwDomainNamespace, wlsDomainNamespace);
  }

  /**
   * Create a basic istio enabled FMW model in image domain using the database created by DB Operator.
   * Verify Pod is ready and service exists for both admin server and managed servers.
   */
  @Test
  @DisplayName("Create Istio enabled FMW Domain model in image domain")
  void  testIstioEnabledFmwModelInImageWithDbOperator() {
    // Create the repo secret to pull the image
    // this secret is used only for non-kind cluster
    createOcirRepoSecret(fmwDomainNamespace);

    // create secret for admin credentials
    logger.info("Create secret for admin credentials");
    assertDoesNotThrow(() -> createSecretWithUsernamePassword(fmwAminSecretName,
        fmwDomainNamespace,
        ADMIN_USERNAME_DEFAULT,
        ADMIN_PASSWORD_DEFAULT),
        String.format("createSecret failed for %s", fmwAminSecretName));

    // create encryption secret
    logger.info("Create encryption secret");
    assertDoesNotThrow(() -> createSecretWithUsernamePassword(fmwEncryptionSecretName,
        fmwDomainNamespace,
        ENCRYPION_USERNAME_DEFAULT,
        ENCRYPION_PASSWORD_DEFAULT),
        String.format("createSecret failed for %s", fmwEncryptionSecretName));

    // create RCU access secret
    logger.info("Creating RCU access secret: {0}, with prefix: {1}, dbUrl: {2}, schemapassword: {3})",
        rcuaccessSecretName, RCUSCHEMAPREFIX, dbUrl, RCUSCHEMAPASSWORD);
    assertDoesNotThrow(() -> createRcuAccessSecret(
        rcuaccessSecretName,
        fmwDomainNamespace,
        RCUSCHEMAPREFIX,
        RCUSCHEMAPASSWORD,
        dbUrl),
        String.format("createSecret failed for %s", rcuaccessSecretName));

    logger.info("Create OPSS wallet password secret");
    assertDoesNotThrow(() -> createOpsswalletpasswordSecret(
        opsswalletpassSecretName,
        fmwDomainNamespace,
        ADMIN_PASSWORD_DEFAULT),
        String.format("createSecret failed for %s", opsswalletpassSecretName));

    logger.info("Create an image with jrf model file");
    final List<String> modelList = Collections.singletonList(MODEL_DIR + "/" + modelFile);
    fmwMiiImage = createMiiImageAndVerify(
        "jrf-mii-image",
        modelList,
        Collections.singletonList(MII_BASIC_APP_NAME),
        FMWINFRA_IMAGE_NAME,
        FMWINFRA_IMAGE_TAG,
        "JRF",
        false);

    // push the image to a registry to make it accessible in multi-node cluster
    dockerLoginAndPushImageToRegistry(fmwMiiImage);

    // create WDT config map without any files
    createConfigMapAndVerify(configMapName, fmwDomainUid, fmwDomainNamespace, Collections.EMPTY_LIST);

    // create the domain object
    Domain domain = FmwUtils.createIstioDomainResource(fmwDomainUid,
        fmwDomainNamespace,
        fmwAminSecretName,
        OCIR_SECRET_NAME,
        fmwEncryptionSecretName,
        rcuaccessSecretName,
        opsswalletpassSecretName,
        replicaCount,
        fmwMiiImage,
        configMapName
        );

    createDomainAndVerify(domain, fmwDomainNamespace);

    verifyDomainReady(fmwDomainNamespace, fmwDomainUid, replicaCount);

    String clusterName = "cluster-1";
    int istioIngressPort = enableIstio(clusterName, fmwDomainUid, fmwDomainNamespace, fmwAdminServerPodName);
    logger.info("Istio Ingress Port is {0}", istioIngressPort);

    // We can not verify Rest Management console thru Adminstration NodePort
    // in istio, as we can not enable Adminstration NodePort
    if (!WEBLOGIC_SLIM) {
      String consoleUrl = "http://" + K8S_NODEPORT_HOST + ":" + istioIngressPort + "/console/login/LoginForm.jsp";
      boolean checkConsole =
          checkAppUsingHostHeader(consoleUrl, fmwDomainNamespace + ".org");
      assertTrue(checkConsole, "Failed to access WebLogic console");
      logger.info("WebLogic console is accessible");
      String localhost = "localhost";
      String forwardPort =
           startPortForwardProcess(localhost, fmwDomainNamespace,
           fmwDomainUid, 7001);
      assertNotNull(forwardPort, "port-forward command fails to assign local port");
      logger.info("Forwarded local port is {0}", forwardPort);
      consoleUrl = "http://" + localhost + ":" + forwardPort + "/console/login/LoginForm.jsp";
      checkConsole =
          checkAppUsingHostHeader(consoleUrl, fmwDomainNamespace + ".org");
      assertTrue(checkConsole, "Failed to access WebLogic console thru port-forwarded port");
      logger.info("WebLogic console is accessible thru port forwarding");
      stopPortForwardProcess(fmwDomainNamespace);
    } else {
      logger.info("Skipping WebLogic console in WebLogic slim image");
    }

    if (isWebLogicPsuPatchApplied()) {
      String curlCmd2 = "curl -j -sk --show-error --noproxy '*' "
          + " -H 'Host: " + fmwDomainNamespace + ".org'"
          + " --user " + ADMIN_USERNAME_DEFAULT + ":" + ADMIN_PASSWORD_DEFAULT
          + " --url http://" + K8S_NODEPORT_HOST + ":" + istioIngressPort
          + "/management/weblogic/latest/domainRuntime/domainSecurityRuntime?"
          + "link=none";

      ExecResult result = null;
      logger.info("curl command {0}", curlCmd2);
      result = assertDoesNotThrow(
        () -> exec(curlCmd2, true));

      if (result.exitValue() == 0) {
        logger.info("curl command returned {0}", result.toString());
        assertTrue(result.stdout().contains("SecurityValidationWarnings"),
                "Could not access the Security Warning Tool page");
        assertTrue(!result.stdout().contains("minimum of umask 027"), "umask warning check failed");
        logger.info("No minimum umask warning reported");
      } else {
        assertTrue(false, "Curl command failed to get DomainSecurityRuntime");
      }
    } else {
      logger.info("Skipping Security warning check, since Security Warning tool "
            + " is not available in the WLS Release {0}", WEBLOGIC_IMAGE_TAG);
    }

    Path archivePath = Paths.get(ITTESTS_DIR, "../operator/integration-tests/apps/testwebapp.war");
    ExecResult result = null;
    result = deployToClusterUsingRest(K8S_NODEPORT_HOST,
        String.valueOf(istioIngressPort),
        ADMIN_USERNAME_DEFAULT, ADMIN_PASSWORD_DEFAULT,
        clusterName, archivePath, fmwDomainNamespace + ".org", "testwebapp");
    assertNotNull(result, "Application deployment failed");
    logger.info("Application deployment returned {0}", result.toString());
    assertEquals("202", result.stdout(), "Deployment didn't return HTTP status code 202");

    String url = "http://" + K8S_NODEPORT_HOST + ":" + istioIngressPort + "/testwebapp/index.jsp";
    logger.info("Application Access URL {0}", url);
    hostHeader = fmwDomainNamespace + ".org";
    boolean checkApp = checkAppUsingHostHeader(url, hostHeader);
    assertTrue(checkApp, "Failed to access WebLogic application");
  }

  /**
   * Create Istio enabled WebLogic domain using model in image and Oracle database used for JMS and JTA
   * migration and service logs.
   */
  @Test
  void  testIstioWlsModelInImageWithDbOperator() {

    // create secret for admin credentials
    logger.info("Create secret for admin credentials");
    String adminSecretName = "weblogic-credentials";
    assertDoesNotThrow(() -> createDomainSecret(adminSecretName,
        ADMIN_USERNAME_DEFAULT, ADMIN_PASSWORD_DEFAULT, wlsDomainNamespace),
        String.format("createSecret failed for %s", adminSecretName));

    // create encryption secret
    logger.info("Create encryption secret");
    String encryptionSecretName = "encryptionsecret";
    assertDoesNotThrow(() -> createDomainSecret(encryptionSecretName, ENCRYPION_USERNAME_DEFAULT,
        ENCRYPION_PASSWORD_DEFAULT, wlsDomainNamespace),
        String.format("createSecret failed for %s", encryptionSecretName));

    logger.info("Create database secret");
    final String dbSecretName = wlsDomainUid + "-db-secret";

    cpUrl = "jdbc:oracle:thin:@//" + dbUrl;
    logger.info("ConnectionPool URL = {0}", cpUrl);
    assertDoesNotThrow(() -> createDatabaseSecret(dbSecretName,
        "sys as sysdba", "Oradoc_db1", cpUrl, wlsDomainNamespace),
        String.format("createSecret failed for %s", dbSecretName));
    String configMapName = "jdbc-jms-recovery-configmap";

    createConfigMapAndVerify(configMapName, wlsDomainUid, wlsDomainNamespace,
        Arrays.asList(MODEL_DIR + "/jms.recovery.yaml"));

    // create the domain CR with a pre-defined configmap
    createDomainResourceWithLogHome(wlsDomainUid, wlsDomainNamespace,
        MII_BASIC_IMAGE_NAME + ":" + MII_BASIC_IMAGE_TAG,
        adminSecretName, OCIR_SECRET_NAME, encryptionSecretName,
        replicaCount, pvName, pvcName, "cluster-1", configMapName,
        dbSecretName, false, false, true);

    // wait for the domain to exist
    logger.info("Check for domain custom resource in namespace {0}", wlsDomainNamespace);
    testUntil(domainExists(wlsDomainUid, DOMAIN_VERSION, wlsDomainNamespace),
        logger,
        "domain {0} to be created in namespace {1}",
        wlsDomainUid,
        wlsDomainNamespace);

    logger.info("Check admin service and pod {0} is created in namespace {1}",
        wlsAdminServerPodName, wlsDomainNamespace);
    checkPodReadyAndServiceExists(wlsAdminServerPodName, wlsDomainUid, wlsDomainNamespace);

    adminSvcExtRouteHost = createRouteForOKD(getExternalServicePodName(wlsAdminServerPodName), wlsDomainNamespace);
    // create the required leasing table 'ACTIVE' before we start the cluster
    createLeasingTable(wlsAdminServerPodName, wlsDomainNamespace, dbUrl);
    // check managed server services and pods are ready
    for (int i = 1; i <= replicaCount; i++) {
      logger.info("Wait for managed server services and pods are created in namespace {0}",
          wlsDomainNamespace);
      checkPodReadyAndServiceExists(wlsManagedServerPrefix + i, wlsDomainUid, wlsDomainNamespace);
    }

    wlDomainIstioIngressPort = enableIstio("cluster-1", wlsDomainUid, wlsDomainNamespace, wlsAdminServerPodName);
    logger.info("Istio Ingress Port is {0}", wlDomainIstioIngressPort);

    hostHeader = wlsDomainNamespace + ".org";

    //Verify JMS/JTA Service migration with File(JDBC) Store
    testMiiJmsJtaServiceMigration();
  }

  /**
   * Verify JMS/JTA Service is migrated to an available active server.
   */
  private void testMiiJmsJtaServiceMigration() {

    // build the standalone JMS Client on Admin pod
    String destLocation = "/u01/JmsSendReceiveClient.java";
    assertDoesNotThrow(() -> copyFileToPod(wlsDomainNamespace,
        wlsAdminServerPodName, "",
        Paths.get(RESOURCE_DIR, "jms", "JmsSendReceiveClient.java"),
        Paths.get(destLocation)));
    runJavacInsidePod(wlsAdminServerPodName, wlsDomainNamespace, destLocation);

    assertTrue(checkJmsServerRuntime("ClusterJmsServer@managed-server2",
        "managed-server2"),
        "ClusterJmsServer@managed-server2 is not on managed-server2 before migration");

    assertTrue(checkJmsServerRuntime("JdbcJmsServer@managed-server2",
        "managed-server2"),
        "JdbcJmsServer@managed-server2 is not on managed-server2 before migration");

    assertTrue(checkJtaRecoveryServiceRuntime("managed-server2",
        "managed-server2", "true"),
        "JTARecoveryService@managed-server2 is not on managed-server2 before migration");

    // Send persistent messages to both FileStore and JDBCStore based
    // JMS Destination (Queue)
    runJmsClientOnAdminPod("send",
        "ClusterJmsServer@managed-server2@jms.testUniformQueue");
    runJmsClientOnAdminPod("send",
        "JdbcJmsServer@managed-server2@jms.jdbcUniformQueue");

    // Scale down the cluster to repilca count of 1, this will shutdown
    // the managed server managed-server2 in the cluster to trigger
    // JMS/JTA Service Migration.
    boolean psuccess = assertDoesNotThrow(()
        -> scaleCluster(wlsDomainUid, wlsDomainNamespace, "cluster-1", 1),
        String.format("replica patching to 1 failed for domain %s in namespace %s", wlsDomainUid, wlsDomainNamespace));
    assertTrue(psuccess,
        String.format("Cluster replica patching failed for domain %s in namespace %s",
            wlsDomainUid, wlsDomainNamespace));
    checkPodDoesNotExist(wlsManagedServerPrefix + "2", wlsDomainUid, wlsDomainNamespace);

    // Make sure the ClusterJmsServer@managed-server2 and
    // JdbcJmsServer@managed-server2 are migrated to managed-server1
    assertTrue(checkJmsServerRuntime("ClusterJmsServer@managed-server2",
        "managed-server1"),
        "ClusterJmsServer@managed-server2 is NOT migrated to managed-server1");
    logger.info("ClusterJmsServer@managed-server2 is migrated to managed-server1");

    assertTrue(checkJmsServerRuntime("JdbcJmsServer@managed-server2",
        "managed-server1"),
        "JdbcJmsServer@managed-server2 is NOT migrated to managed-server1");
    logger.info("JdbcJmsServer@managed-server2 is migrated to managed-server1");

    assertTrue(checkStoreRuntime("ClusterFileStore@managed-server2",
        "managed-server1"),
        "ClusterFileStore@managed-server2 is NOT migrated to managed-server1");
    logger.info("ClusterFileStore@managed-server2 is migrated to managed-server1");

    assertTrue(checkStoreRuntime("ClusterJdbcStore@managed-server2",
        "managed-server1"),
        "JdbcStore@managed-server2 is NOT migrated to managed-server1");
    logger.info("JdbcStore@managed-server2 is migrated to managed-server1");

    assertTrue(checkJtaRecoveryServiceRuntime("managed-server1",
        "managed-server2", "true"), "JTA RecoveryService@managed-server2 is not migrated to managed-server1");
    logger.info("JTA RecoveryService@managed-server2 is migrated to managed-server1");

    runJmsClientOnAdminPod("receive",
        "ClusterJmsServer@managed-server2@jms.testUniformQueue");
    runJmsClientOnAdminPod("receive",
        "JdbcJmsServer@managed-server2@jms.jdbcUniformQueue");

    // Restart the managed server(2) to make sure the JTA Recovery Service is
    // migrated back to original hosting server
    restartManagedServer("managed-server2");
    assertTrue(checkJtaRecoveryServiceRuntime("managed-server2",
        "managed-server2", "true"),
        "JTARecoveryService@managed-server2 is not on managed-server2 after restart");
    logger.info("JTA RecoveryService@managed-server2 is migrated back to managed-server1");
    assertTrue(checkJtaRecoveryServiceRuntime("managed-server1",
        "managed-server2", "false"),
        "JTARecoveryService@managed-server2 is not deactivated on managed-server1 after restart");
    logger.info("JTA RecoveryService@managed-server2 is deactivated on managed-server1 after restart");

    assertTrue(checkStoreRuntime("ClusterFileStore@managed-server2",
        "managed-server2"),
        "FileStore@managed-server2 is NOT migrated back to managed-server2");
    logger.info("FileStore@managed-server2 is migrated back to managed-server2");
    assertTrue(checkStoreRuntime("ClusterJdbcStore@managed-server2",
        "managed-server2"),
        "JdbcStore@managed-server2 is NOT migrated back to managed-server2");
    logger.info("JdbcStore@managed-server2 is migrated back to managed-server2");

    assertTrue(checkJmsServerRuntime("ClusterJmsServer@managed-server2",
        "managed-server2"),
        "ClusterJmsServer@managed-server2 is NOT migrated back to to managed-server2");
    logger.info("ClusterJmsServer@managed-server2 is migrated back to managed-server2");
    assertTrue(checkJmsServerRuntime("JdbcJmsServer@managed-server2",
        "managed-server2"),
        "JdbcJmsServer@managed-server2 is NOT migrated back to managed-server2");
    logger.info("JdbcJmsServer@managed-server2 is migrated back to managed-server2");
  }

  /**
   * Uninstall DB operator and delete DB instance.
   * The cleanup framework does not uninstall DB operator, delete DB instance and storageclass.
   * Deletes Oracle database instance, operator and storageclass.
   */
  @AfterAll
  public void tearDownAll() throws ApiException {
    if (System.getenv("SKIP_CLEANUP") == null
        || (System.getenv("SKIP_CLEANUP") != null
        && System.getenv("SKIP_CLEANUP").equalsIgnoreCase("false"))) {
      deleteOracleDB(dbNamespace, dbName);
      deleteHostPathProvisioner(dbNamespace);
      uninstallDBOperator(dbNamespace);
    }
  }


  // Restart the managed-server
  private void restartManagedServer(String serverName) {
    String commonParameters = " -d " + wlsDomainUid + " -n " + wlsDomainNamespace;
    boolean result;
    CommandParams params = new CommandParams().defaults();
    String script = "startServer.sh";
    params.command("sh "
        + Paths.get(domainLifecycleSamplePath.toString(), "/" + script).toString()
        + commonParameters + " -s " + serverName);
    result = Command.withParams(params).execute();
    assertTrue(result, "Failed to execute script " + script);
    checkPodReadyAndServiceExists(wlsManagedServerPrefix + "2", wlsDomainUid, wlsDomainNamespace);
  }

  // Run standalone JMS Client to send/receive message from
  // Distributed Destination Member
  private void runJmsClientOnAdminPod(String action, String queue) {
    testUntil(
        runClientInsidePod(wlsAdminServerPodName, wlsDomainNamespace,
            "/u01", "JmsSendReceiveClient",
            "t3://" + wlsDomainUid + "-cluster-cluster-1:8001", action, queue, "100"),
        logger,
        "Wait for JMS Client to send/recv msg");
  }

  /*
   * Verify the JMS Server Runtime through REST API.
   * Get the specific JMSServer Runtime on specified managed server.
   * @param jmsServer name of the JMSServerRuntime to look for
   * @param managedServer name of the managed server to look for JMSServerRuntime
   * @returns true if MBean is found otherwise false
   **/
  private boolean checkJmsServerRuntime(String jmsServer, String managedServer) {
    String hostAndPort = getHostAndPort(adminSvcExtRouteHost, wlDomainIstioIngressPort);
    StringBuffer curlString = new StringBuffer("status=$(curl --user "
        + ADMIN_USERNAME_DEFAULT + ":" + ADMIN_PASSWORD_DEFAULT
        + " -H 'host: " + hostHeader + " ' ");
    curlString.append("http://" + hostAndPort)
        .append("/management/weblogic/latest/domainRuntime/serverRuntimes/")
        .append(managedServer)
        .append("/JMSRuntime/JMSServers/")
        .append(jmsServer)
        .append(" --silent --show-error ")
        .append(" -o /dev/null")
        .append(" -w %{http_code});")
        .append("echo ${status}");
    logger.info("checkJmsServerRuntime: curl command {0}", new String(curlString));
    testUntil(
        assertDoesNotThrow(() -> () -> exec(curlString.toString(), true).stdout().contains("200")),
        logger,
        "JMS Server Service to migrate");
    return true;
  }

  /*
   * Verify the Persistent Store Runtimes through REST API.
   * Get the specific Persistent Store Runtime on specified managed server.
   * @param storeName name of the PersistentStore Runtime to look for
   * @param managedServer name of the managed server to look for StoreRuntime
   * @returns true if MBean is found otherwise false
   **/
  private boolean checkStoreRuntime(String storeName, String managedServer) {
    String hostAndPort = getHostAndPort(adminSvcExtRouteHost, wlDomainIstioIngressPort);
    StringBuffer curlString = new StringBuffer("status=$(curl --user "
        + ADMIN_USERNAME_DEFAULT + ":" + ADMIN_PASSWORD_DEFAULT + " "
        + " -H 'host: " + hostHeader + " ' ");
    curlString.append("http://" + hostAndPort)
        .append("/management/weblogic/latest/domainRuntime/serverRuntimes/")
        .append(managedServer)
        .append("/persistentStoreRuntimes/")
        .append(storeName)
        .append(" --silent --show-error ")
        .append(" -o /dev/null")
        .append(" -w %{http_code});")
        .append("echo ${status}");
    logger.info("checkStoreRuntime: curl command {0}", new String(curlString));
    testUntil(
        assertDoesNotThrow(() -> () -> exec(curlString.toString(), true).stdout().contains("200")),
        logger,
        "PersistentStoreRuntimes Service to migrate");
    return true;
  }

  /*
   * Verify the JTA Recovery Service Runtime through REST API.
   * Get the JTA Recovery Service Runtime for a server on a
   * specified managed server.
   * @param managedServer name of the server to look for RecoveyServerRuntime
   * @param recoveryService name of RecoveyServerRuntime (managed server)
   * @param active is the recovery active (true or false )
   * @returns true if MBean is found otherwise false
   **/
  private boolean checkJtaRecoveryServiceRuntime(String managedServer, String recoveryService, String active) {
    String hostAndPort = getHostAndPort(adminSvcExtRouteHost, wlDomainIstioIngressPort);
    StringBuffer curlString = new StringBuffer("curl --user "
        + ADMIN_USERNAME_DEFAULT + ":" + ADMIN_PASSWORD_DEFAULT
        + " -H 'host: " + hostHeader + " ' ");
    curlString.append("\"http://" + hostAndPort)
        .append("/management/weblogic/latest/domainRuntime/serverRuntimes/")
        .append(managedServer)
        .append("/JTARuntime/recoveryRuntimeMBeans/")
        .append(recoveryService)
        .append("?fields=active&links=none\"")
        .append(" --show-error ");
    logger.info("checkJtaRecoveryServiceRuntime: curl command {0}", new String(curlString));
    testUntil(
        assertDoesNotThrow(() -> () -> exec(curlString.toString(), true)
        .stdout().contains("{\"active\": " + active + "}")),
        logger,
        "JTA Recovery Service to migrate");
    return true;
  }

  /**
   * Create leasing Table (ACTIVE) on an Oracle DB Instance. Uses the WebLogic utility utils.Schema to add the table So
   * the command MUST be run inside a Weblogic Server pod.
   *
   * @param wlPodName the pod name
   * @param namespace the namespace in which WebLogic pod exists
   * @param dbUrl Oracle database url
   */
  public static void createLeasingTable(String wlPodName, String namespace, String dbUrl) {
    Path ddlFile = Paths.get(WORK_DIR + "/leasing.ddl");
    String ddlString = "DROP TABLE ACTIVE;\n"
        + "CREATE TABLE ACTIVE (\n"
        + "  SERVER VARCHAR2(255) NOT NULL,\n"
        + "  INSTANCE VARCHAR2(255) NOT NULL,\n"
        + "  DOMAINNAME VARCHAR2(255) NOT NULL,\n"
        + "  CLUSTERNAME VARCHAR2(255) NOT NULL,\n"
        + "  TIMEOUT DATE,\n"
        + "  PRIMARY KEY (SERVER, DOMAINNAME, CLUSTERNAME)\n"
        + ");\n";

    assertDoesNotThrow(() -> Files.write(ddlFile, ddlString.getBytes()));
    String destLocation = "/u01/leasing.ddl";
    assertDoesNotThrow(() -> copyFileToPod(namespace,
        wlPodName, "",
        Paths.get(WORK_DIR, "leasing.ddl"),
        Paths.get(destLocation)));

    //String cpUrl = "jdbc:oracle:thin:@//" + K8S_NODEPORT_HOST + ":"
    String cpUrl = "jdbc:oracle:thin:@//" + dbUrl;
    String jarLocation = "/u01/oracle/wlserver/server/lib/weblogic.jar";
    StringBuffer ecmd = new StringBuffer("java -cp ");
    ecmd.append(jarLocation);
    ecmd.append(" utils.Schema ");
    ecmd.append(cpUrl);
    ecmd.append(" oracle.jdbc.OracleDriver");
    ecmd.append(" -verbose ");
    ecmd.append(" -u \"sys as sysdba\"");
    ecmd.append(" -p Oradoc_db1");
    ecmd.append(" /u01/leasing.ddl");
    ExecResult execResult = assertDoesNotThrow(() -> execCommand(namespace, wlPodName,
        null, true, "/bin/sh", "-c", ecmd.toString()));
    assertTrue(execResult.exitValue() == 0, "Could not create the Leasing Table");
  }

  private int enableIstio(String clusterName, String domainUid, String namespace, String adminServerPodName) {

    String clusterService = domainUid + "-cluster-" + clusterName + "." + namespace + ".svc.cluster.local";

    Map<String, String> templateMap = new HashMap();
    templateMap.put("NAMESPACE", namespace);
    templateMap.put("DUID", domainUid);
    templateMap.put("ADMIN_SERVICE", adminServerPodName);
    templateMap.put("CLUSTER_SERVICE", clusterService);

    Path srcHttpFile = Paths.get(RESOURCE_DIR, "istio", "istio-http-template.yaml");
    Path targetHttpFile = assertDoesNotThrow(
        () -> generateFileFromTemplate(srcHttpFile.toString(), "istio-http.yaml", templateMap));
    logger.info("Generated Http VS/Gateway file path is {0}", targetHttpFile);

    boolean deployRes = assertDoesNotThrow(
        () -> deployHttpIstioGatewayAndVirtualservice(targetHttpFile));
    assertTrue(deployRes, "Failed to deploy Http Istio Gateway/VirtualService");

    Path srcDrFile = Paths.get(RESOURCE_DIR, "istio", "istio-dr-template.yaml");
    Path targetDrFile = assertDoesNotThrow(
        () -> generateFileFromTemplate(srcDrFile.toString(), "istio-dr.yaml", templateMap));
    logger.info("Generated DestinationRule file path is {0}", targetDrFile);

    deployRes = assertDoesNotThrow(
        () -> deployIstioDestinationRule(targetDrFile));
    assertTrue(deployRes, "Failed to deploy Istio DestinationRule");

    int istioIngressPort = getIstioHttpIngressPort();
    logger.info("Istio Ingress Port is {0}", istioIngressPort);
    return istioIngressPort;
  }

  private static Domain createDomainResourceWithLogHome(
      String domainResourceName,
      String domNamespace,
      String imageName,
      String adminSecretName,
      String repoSecretName,
      String encryptionSecretName,
      int replicaCount,
      String pvName,
      String pvcName,
      String clusterName,
      String configMapName,
      String dbSecretName,
      boolean allowReplicasBelowMinDynClusterSize,
      boolean onlineUpdateEnabled,
      boolean setDataHome) {

    List<String> securityList = new ArrayList<>();
    if (dbSecretName != null) {
      securityList.add(dbSecretName);
    }

    DomainSpec domainSpec = new DomainSpec()
        .domainUid(domainResourceName)
        .domainHomeSourceType("FromModel")
        .allowReplicasBelowMinDynClusterSize(allowReplicasBelowMinDynClusterSize)
        .image(imageName)
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
                .value("-Dweblogic.security.SSL.ignoreHostnameVerification=true"))
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
            .serverStartState("RUNNING"))
        .addClustersItem(new Cluster()
            .clusterName(clusterName)
            .replicas(replicaCount)
            .serverStartState("RUNNING"))
        .configuration(new Configuration()
            .istio(new Istio()
                .enabled(Boolean.TRUE)
                .readinessPort(8888)
                .localhostBindingsEnabled(isLocalHostBindingsEnabled()))
            .secrets(securityList)
            .model(new Model()
                .domainType("WLS")
                .configMap(configMapName)
                .runtimeEncryptionSecret(encryptionSecretName)
                .onlineUpdate(new OnlineUpdate()
                    .enabled(onlineUpdateEnabled)))
            .introspectorJobActiveDeadlineSeconds(300L));

    if (setDataHome) {
      domainSpec.dataHome("/shared/data");
    }
    // create the domain CR
    Domain domain = new Domain()
        .apiVersion(DOMAIN_API_VERSION)
        .kind("Domain")
        .metadata(new V1ObjectMeta()
            .name(domainResourceName)
            .namespace(domNamespace))
        .spec(domainSpec);

    logger.info("Create domain custom resource for domainUid {0} in namespace {1}",
        domainResourceName, domNamespace);
    boolean domCreated = assertDoesNotThrow(() -> createDomainCustomResource(domain),
        String.format("Create domain custom resource failed with ApiException for %s in namespace %s",
            domainResourceName, domNamespace));
    assertTrue(domCreated, String.format("Create domain custom resource failed with ApiException "
        + "for %s in namespace %s", domainResourceName, domNamespace));

    setPodAntiAffinity(domain);
    return domain;
  }

}
