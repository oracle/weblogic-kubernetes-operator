// Copyright (c) 2021, 2024, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.weblogic.kubernetes;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import io.kubernetes.client.custom.V1Patch;
import io.kubernetes.client.openapi.ApiException;
import io.kubernetes.client.openapi.models.V1LocalObjectReference;
import oracle.weblogic.domain.ClusterResource;
import oracle.weblogic.domain.DomainResource;
import oracle.weblogic.kubernetes.actions.impl.TraefikParams;
import oracle.weblogic.kubernetes.actions.impl.primitive.Command;
import oracle.weblogic.kubernetes.actions.impl.primitive.CommandParams;
import oracle.weblogic.kubernetes.actions.impl.primitive.HelmParams;
import oracle.weblogic.kubernetes.annotations.IntegrationTest;
import oracle.weblogic.kubernetes.annotations.Namespaces;
import oracle.weblogic.kubernetes.logging.LoggingFacade;
import oracle.weblogic.kubernetes.utils.ExecResult;
import oracle.weblogic.kubernetes.utils.FmwUtils;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import static oracle.weblogic.kubernetes.TestConstants.ADMIN_PASSWORD_DEFAULT;
import static oracle.weblogic.kubernetes.TestConstants.ADMIN_USERNAME_DEFAULT;
import static oracle.weblogic.kubernetes.TestConstants.DOMAIN_VERSION;
import static oracle.weblogic.kubernetes.TestConstants.ENCRYPION_PASSWORD_DEFAULT;
import static oracle.weblogic.kubernetes.TestConstants.ENCRYPION_USERNAME_DEFAULT;
import static oracle.weblogic.kubernetes.TestConstants.FMWINFRA_IMAGE_NAME;
import static oracle.weblogic.kubernetes.TestConstants.FMWINFRA_IMAGE_TAG;
import static oracle.weblogic.kubernetes.TestConstants.FMWINFRA_IMAGE_TO_USE_IN_SPEC;
import static oracle.weblogic.kubernetes.TestConstants.MII_BASIC_APP_NAME;
import static oracle.weblogic.kubernetes.TestConstants.MII_BASIC_IMAGE_NAME;
import static oracle.weblogic.kubernetes.TestConstants.MII_BASIC_IMAGE_TAG;
import static oracle.weblogic.kubernetes.TestConstants.OKE_CLUSTER_PRIVATEIP;
import static oracle.weblogic.kubernetes.TestConstants.SKIP_CLEANUP;
import static oracle.weblogic.kubernetes.TestConstants.TEST_IMAGES_REPO_SECRET_NAME;
import static oracle.weblogic.kubernetes.actions.ActionConstants.ITTESTS_DIR;
import static oracle.weblogic.kubernetes.actions.ActionConstants.MODEL_DIR;
import static oracle.weblogic.kubernetes.actions.ActionConstants.RESOURCE_DIR;
import static oracle.weblogic.kubernetes.actions.ActionConstants.WORK_DIR;
import static oracle.weblogic.kubernetes.actions.TestActions.createDomainCustomResource;
import static oracle.weblogic.kubernetes.actions.TestActions.execCommand;
import static oracle.weblogic.kubernetes.actions.TestActions.patchDomainCustomResource;
import static oracle.weblogic.kubernetes.actions.TestActions.scaleCluster;
import static oracle.weblogic.kubernetes.actions.TestActions.uninstallTraefik;
import static oracle.weblogic.kubernetes.actions.impl.primitive.Command.defaultCommandParams;
import static oracle.weblogic.kubernetes.assertions.TestAssertions.domainExists;
import static oracle.weblogic.kubernetes.utils.ClusterUtils.createClusterAndVerify;
import static oracle.weblogic.kubernetes.utils.ClusterUtils.createClusterResource;
import static oracle.weblogic.kubernetes.utils.CommonMiiTestUtils.createDatabaseSecret;
import static oracle.weblogic.kubernetes.utils.CommonMiiTestUtils.createDomainResourceWithLogHome;
import static oracle.weblogic.kubernetes.utils.CommonMiiTestUtils.createDomainSecret;
import static oracle.weblogic.kubernetes.utils.CommonMiiTestUtils.createJobToChangePermissionsOnPvHostPath;
import static oracle.weblogic.kubernetes.utils.CommonMiiTestUtils.readRuntimeResource;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.checkPodReadyAndServiceExists;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.createIngressHostRouting;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.getUniqueName;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.runClientInsidePod;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.runJavacInsidePod;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.testUntil;
import static oracle.weblogic.kubernetes.utils.ConfigMapUtils.createConfigMapAndVerify;
import static oracle.weblogic.kubernetes.utils.DbUtils.createOracleDBUsingOperator;
import static oracle.weblogic.kubernetes.utils.DbUtils.createRcuAccessSecret;
import static oracle.weblogic.kubernetes.utils.DbUtils.createRcuSchema;
import static oracle.weblogic.kubernetes.utils.DbUtils.deleteOracleDB;
import static oracle.weblogic.kubernetes.utils.DomainUtils.createDomainAndVerify;
import static oracle.weblogic.kubernetes.utils.FileUtils.copyFileToPod;
import static oracle.weblogic.kubernetes.utils.FmwUtils.verifyDomainReady;
import static oracle.weblogic.kubernetes.utils.FmwUtils.verifyEMconsoleAccess;
import static oracle.weblogic.kubernetes.utils.ImageUtils.createBaseRepoSecret;
import static oracle.weblogic.kubernetes.utils.ImageUtils.createMiiImageAndVerify;
import static oracle.weblogic.kubernetes.utils.ImageUtils.createTestRepoSecret;
import static oracle.weblogic.kubernetes.utils.ImageUtils.imageRepoLoginAndPushImageToRegistry;
import static oracle.weblogic.kubernetes.utils.LoadBalancerUtils.installAndVerifyTraefik;
import static oracle.weblogic.kubernetes.utils.OKDUtils.createRouteForOKD;
import static oracle.weblogic.kubernetes.utils.OperatorUtils.installAndVerifyOperator;
import static oracle.weblogic.kubernetes.utils.PatchDomainUtils.patchDomainResourceServerStartPolicy;
import static oracle.weblogic.kubernetes.utils.PersistentVolumeUtils.createPV;
import static oracle.weblogic.kubernetes.utils.PersistentVolumeUtils.createPVC;
import static oracle.weblogic.kubernetes.utils.PodUtils.checkPodDeleted;
import static oracle.weblogic.kubernetes.utils.PodUtils.checkPodDoesNotExist;
import static oracle.weblogic.kubernetes.utils.PodUtils.getExternalServicePodName;
import static oracle.weblogic.kubernetes.utils.SecretUtils.createOpsswalletpasswordSecret;
import static oracle.weblogic.kubernetes.utils.SecretUtils.createSecretWithUsernamePassword;
import static oracle.weblogic.kubernetes.utils.ThreadSafeLogger.getLogger;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests to create FMW model in image domain and WebLogic domain using Oracle
 * database created using Oracle Database Operator.
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DisplayName("Test to create FMW model in image domain and WebLogic domain using Oracle "
    + "database created using Oracle Database Operator")
@IntegrationTest
@Tag("oke-weekly-sequential")
@Tag("kind-parallel")
class ItDBOperator {

  private static String dbNamespace = null;
  private static String opNamespace = null;
  private static String fmwDomainNamespace = null;
  private static String wlsDomainNamespace = null;
  private static String traefikNamespace = null;
  private static String fmwMiiImage = null;

  private static String hostAndPort = null;

  private static final String RCUSCHEMAPREFIX = "FMWDOMAINMII";
  private static final String RCUSYSPASSWORD = "Oradoc_db1";
  private static final String RCUSCHEMAPASSWORD = "Oradoc_db1";
  private static final String modelFile = "model-singleclusterdomain-sampleapp-jrf.yaml";

  private static String dbUrl = null;
  private static String dbName = "my-oracle-sidb";
  private static LoggingFacade logger = null;
  private static HelmParams traefikHelmParams;

  private String fmwDomainUid = "fmwdomain-mii-db";
  private String adminServerName = "admin-server";
  private static int adminPort = 7001;  
  private String fmwAdminServerPodName = fmwDomainUid + "-admin-server";
  private String fmwManagedServerPrefix = fmwDomainUid + "-managed-server";
  private int replicaCount = 2;
  private String clusterName = "cluster-1";
  private String fmwAminSecretName = fmwDomainUid + "-weblogic-credentials";
  private String fmwEncryptionSecretName = fmwDomainUid + "-encryptionsecret";
  private String rcuaccessSecretName = fmwDomainUid + "-rcu-access";
  private String opsswalletpassSecretName = fmwDomainUid + "-opss-wallet-password-secret";
  private String opsswalletfileSecretName = fmwDomainUid + "opss-wallet-file-secret";
  private String adminSvcExtHost = null;

  private static final String wlsDomainUid = "mii-jms-recovery-db";
  private static final String pvName = getUniqueName(wlsDomainUid + "-pv-");
  private static final String pvcName = getUniqueName(wlsDomainUid + "-pvc-");
  private static final String wlsAdminServerPodName = wlsDomainUid + "-admin-server";
  private static final String wlsManagedServerPrefix = wlsDomainUid + "-managed-server";
  private static String cpUrl;
  private static String adminSvcExtRouteHost = null;

  private final Path samplePath = Paths.get(ITTESTS_DIR, "../kubernetes/samples");
  private final Path domainLifecycleSamplePath = Paths.get(samplePath + "/scripts/domain-lifecycle");
  private final String fmwClusterResName = fmwDomainUid + "-" + clusterName;
  private final String wlsClusterResName = wlsDomainUid + "-" + clusterName;
  
  private static String hostHeader;
  private String ingressIP;
  private static TraefikParams traefikParams;
  private String ingressClassName;

  /**
   * Start DB service and create RCU schema.
   * Assigns unique namespaces for operator and domains.
   * Pull FMW image and Oracle DB image if running tests in Kind cluster. Installs operator.
   *
   * @param namespaces injected by JUnit
   */
  @BeforeAll
  public static void initAll(@Namespaces(5) List<String> namespaces) {

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

    // get a unique Traefik namespace
    logger.info("Get a unique namespace for Traefik");
    assertNotNull(namespaces.get(4), "Namespace list is null");
    traefikNamespace = namespaces.get(4);

    // install and verify Traefik
    if (OKE_CLUSTER_PRIVATEIP) {
      traefikParams =
          installAndVerifyTraefik(traefikNamespace, 0, 0);
      traefikHelmParams = traefikParams.getHelmParams();
    }

    // Create the repo secret to pull the image
    // this secret is used only for non-kind cluster
    createBaseRepoSecret(fmwDomainNamespace);
    createBaseRepoSecret(wlsDomainNamespace);
    createBaseRepoSecret(dbNamespace);

    logger.info("Create Oracle DB in namespace: {0} ", dbNamespace);
    dbUrl = assertDoesNotThrow(() -> createOracleDBUsingOperator(dbName, RCUSYSPASSWORD, dbNamespace));

    logger.info("Create RCU schema with fmwImage: {0}, rcuSchemaPrefix: {1}, dbUrl: {2}, "
        + " dbNamespace: {3}", FMWINFRA_IMAGE_TO_USE_IN_SPEC, RCUSCHEMAPREFIX, dbUrl, dbNamespace);
    assertDoesNotThrow(() -> createRcuSchema(FMWINFRA_IMAGE_TO_USE_IN_SPEC, RCUSCHEMAPREFIX,
        dbUrl, dbNamespace));

    // install operator and verify its running in ready state
    installAndVerifyOperator(opNamespace, fmwDomainNamespace, wlsDomainNamespace);
  }

  @AfterAll
  void tearDown() {
    // uninstall Traefik
    if (traefikHelmParams != null) {
      assertThat(uninstallTraefik(traefikHelmParams))
          .as("Test uninstallTraefik returns true")
          .withFailMessage("uninstallTraefik() did not return true")
          .isTrue();
    }
  }

  /**
   * Create a basic FMW model in image domain using the database created by DB Operator. Verify Pod is ready and service
   * exists for both admin server and managed servers. Verify EM console is accessible.
   */
  //@Test - FMW mii domain has been deprecated since WKO 4.1
  @DisplayName("Create FMW Domain model in image")
  void  testFmwModelInImageWithDbOperator() {

    // Create the repo secret to pull the image
    // this secret is used only for non-kind cluster
    createTestRepoSecret(fmwDomainNamespace);

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
    imageRepoLoginAndPushImageToRegistry(fmwMiiImage);

    // create the domain object
    DomainResource domain = FmwUtils.createDomainResource(fmwDomainUid,
        fmwDomainNamespace,
        fmwAminSecretName,
        TEST_IMAGES_REPO_SECRET_NAME,
        fmwEncryptionSecretName,
        rcuaccessSecretName,
        opsswalletpassSecretName,
        fmwMiiImage);
    
    // create cluster object
    ClusterResource cluster = createClusterResource(fmwClusterResName,
        clusterName, fmwDomainNamespace, replicaCount);
    logger.info("Creating cluster resource {0} in namespace {1}", fmwClusterResName, fmwDomainNamespace);
    createClusterAndVerify(cluster);
    // set cluster references
    domain.getSpec().withCluster(new V1LocalObjectReference().name(fmwClusterResName));

    createDomainAndVerify(domain, fmwDomainNamespace);

    verifyDomainReady(fmwDomainNamespace, fmwDomainUid, replicaCount);
    // Expose the admin service external node port as  a route for OKD
    adminSvcExtHost = createRouteForOKD(getExternalServicePodName(fmwAdminServerPodName), fmwDomainNamespace);

    if (TestConstants.KIND_CLUSTER
        && !TestConstants.WLSIMG_BUILDER.equals(TestConstants.WLSIMG_BUILDER_DEFAULT)) {
      hostHeader = createIngressHostRouting(fmwDomainNamespace, fmwDomainUid, adminServerName, adminPort);
      verifyEMconsoleAccess(fmwDomainNamespace, fmwDomainUid, adminSvcExtHost, hostHeader);
    } else {
      verifyEMconsoleAccess(fmwDomainNamespace, fmwDomainUid, adminSvcExtHost);
    }

    //Reuse the same RCU schema to restart JRF domain
    testReuseRCUschemaToRestartDomain();

  }

  /**
   * Create WebLogic domain using model in image and Oracle database used for JMS and JTA migration and service logs.
   */
  @Test
  void  testWlsModelInImageWithDbOperator() {

    // Create the repo secret to pull the image
    // this secret is used only for non-kind cluster
    createTestRepoSecret(wlsDomainNamespace);

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

    // create PV, PVC for logs/data
    createPV(pvName, wlsDomainUid, ItDBOperator.class.getSimpleName());
    createPVC(pvName, pvcName, wlsDomainUid, wlsDomainNamespace);

    // create job to change permissions on PV hostPath
    createJobToChangePermissionsOnPvHostPath(pvName, pvcName, wlsDomainNamespace);

    // create the domain CR with a pre-defined configmap
    DomainResource domainCR = createDomainResourceWithLogHome(wlsDomainUid, wlsDomainNamespace,
        MII_BASIC_IMAGE_NAME + ":" + MII_BASIC_IMAGE_TAG,
        adminSecretName, TEST_IMAGES_REPO_SECRET_NAME, encryptionSecretName, replicaCount,
        pvName, pvcName, configMapName,
        dbSecretName, false, true);
    
    // create cluster object
    ClusterResource cluster = createClusterResource(wlsClusterResName, clusterName, wlsDomainNamespace, replicaCount);
    logger.info("Creating cluster resource {0} in namespace {1}", wlsClusterResName, wlsDomainNamespace);
    createClusterAndVerify(cluster);
    // set cluster references
    domainCR.getSpec().withCluster(new V1LocalObjectReference().name(wlsClusterResName));
    
    logger.info("Create domain custom resource for domainUid {0} in namespace {1}",
        wlsDomainUid, wlsDomainNamespace);
    boolean domCreated = assertDoesNotThrow(() -> createDomainCustomResource(domainCR, DOMAIN_VERSION),
        String.format("Create domain custom resource failed with ApiException for %s in namespace %s",
            wlsDomainUid, wlsDomainNamespace));
    assertTrue(domCreated, String.format("Create domain custom resource failed "
        + "for %s in namespace %s", wlsDomainUid, wlsDomainNamespace));

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

    if (TestConstants.KIND_CLUSTER
        && !TestConstants.WLSIMG_BUILDER.equals(TestConstants.WLSIMG_BUILDER_DEFAULT)) {
      hostHeader = createIngressHostRouting(wlsDomainNamespace, wlsDomainUid, adminServerName, adminPort);
    }
    //Verify JMS/JTA Service migration with File(JDBC) Store
    testMiiJmsJtaServiceMigration();
  }

  /**
   * Save the OPSS key wallet from a running JRF domain's introspector configmap to a file. Restore the OPSS key wallet
   * file to a Kubernetes secret. Shutdown the domain. Using the same RCU schema to restart the same JRF domain with
   * restored OPSS key wallet file secret. Verify Pod is ready and service exists for both admin server and managed
   * servers. Verify EM console is accessible.
   */
  private void testReuseRCUschemaToRestartDomain() {
    saveAndRestoreOpssWalletfileSecret(fmwDomainNamespace, fmwDomainUid, opsswalletfileSecretName);
    shutdownDomain();
    patchDomainWithWalletFileSecret(opsswalletfileSecretName);
    startupDomain();
    verifyDomainReady(fmwDomainNamespace, fmwDomainUid, replicaCount);
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
    boolean psuccess = scaleCluster(wlsClusterResName, wlsDomainNamespace, 1);
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
    if (!SKIP_CLEANUP) {
      deleteOracleDB(dbNamespace, dbName);
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
    testUntil(
        assertDoesNotThrow(() -> () -> getJMSRunTimeOutput(jmsServer,
            managedServer).contains("destinationsCurrentCount")),
        logger,
        "JMS Server Service to migrate");
    return true;
  }

  private String getJMSRunTimeOutput(String jmsServer, String managedServer) {
    String output = readRuntimeResource(
        adminSvcExtHost,
        wlsDomainNamespace,
        wlsAdminServerPodName,
        "/management/weblogic/latest/domainRuntime/serverRuntimes/"
            + managedServer
            + "/JMSRuntime/JMSServers/"
            + jmsServer,
        "checkJmsServerRuntime");
    logger.info("Got output " + output);
    return output;
  }

  /*
   * Verify the Persistent Store Runtimes through REST API.
   * Get the specific Persistent Store Runtime on specified managed server.
   * @param storeName name of the PersistentStore Runtime to look for
   * @param managedServer name of the managed server to look for StoreRuntime
   * @returns true if MBean is found otherwise false
   **/
  private boolean checkStoreRuntime(String storeName, String managedServer) {
    testUntil(
        assertDoesNotThrow(() -> () -> readRuntimeResource(
            adminSvcExtHost,
            wlsDomainNamespace,
            wlsAdminServerPodName,
            "/management/weblogic/latest/domainRuntime/serverRuntimes/"
                + managedServer
                + "/persistentStoreRuntimes/"
                + storeName,
            "checkPersistentStoreRuntime").contains("PersistentStoreRuntime")),
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
    testUntil(
        assertDoesNotThrow(() -> () -> readRuntimeResource(
            adminSvcExtHost,
            wlsDomainNamespace,
            wlsAdminServerPodName,
            "/management/weblogic/latest/domainRuntime/serverRuntimes/"
                + managedServer
                + "/JTARuntime/recoveryRuntimeMBeans/"
                + recoveryService,
            "checkRecoveryServiceRuntime").contains("\"active\": " + active)),
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
    assertEquals(0, execResult.exitValue(), "Could not create the Leasing Table");
  }

  /**
   * Save the OPSS key wallet from a running JRF domain's introspector configmap to a file.
   *
   * @param namespace namespace where JRF domain exists
   * @param domainUid unique domain Uid
   * @param walletfileSecretName name of wallet file secret
   */
  private void saveAndRestoreOpssWalletfileSecret(String namespace, String domainUid,
      String walletfileSecretName) {
    Path saveAndRestoreOpssPath
        = Paths.get(RESOURCE_DIR, "bash-scripts", "opss-wallet.sh");
    String script = saveAndRestoreOpssPath.toString();
    logger.info("Script for saveAndRestoreOpss is {0)", script);

    //save opss wallet file
    String command1 = script + " -d " + domainUid + " -n " + namespace + " -s";
    logger.info("Save wallet file command: {0}", command1);
    assertTrue(() -> Command.withParams(
        defaultCommandParams()
            .command(command1)
            .saveResults(true)
            .redirect(true))
        .execute());

    //restore opss wallet password secret
    String command2 = script + " -d " + domainUid + " -n " + namespace + " -r" + " -ws " + walletfileSecretName;
    logger.info("Restore wallet file command: {0}", command2);
    assertTrue(() -> Command.withParams(
        defaultCommandParams()
            .command(command2)
            .saveResults(true)
            .redirect(true))
        .execute());

  }

  /**
   * Shutdown the domain by setting serverStartPolicy as "Never".
   */
  private void shutdownDomain() {
    patchDomainResourceServerStartPolicy("/spec/serverStartPolicy", "Never", fmwDomainNamespace, fmwDomainUid);
    logger.info("Domain is patched to stop entire WebLogic domain");

    // make sure all the server pods are removed after patch
    checkPodDeleted(fmwAdminServerPodName, fmwDomainUid, fmwDomainNamespace);
    for (int i = 1; i <= replicaCount; i++) {
      checkPodDeleted(fmwManagedServerPrefix + i, fmwDomainUid, fmwDomainNamespace);
    }

    logger.info("Domain shutdown success");

  }

  /**
   * Startup the domain by setting serverStartPolicy as "IfNeeded".
   */
  private void startupDomain() {
    patchDomainResourceServerStartPolicy("/spec/serverStartPolicy", "IfNeeded", fmwDomainNamespace, fmwDomainUid);
    logger.info("Domain is patched to start all servers in the domain");
  }

  /**
   * Patch the domain with opss wallet file secret.
   *
   * @param opssWalletFileSecretName the name of opps wallet file secret
   * @return true if patching succeeds, false otherwise
   */
  private boolean patchDomainWithWalletFileSecret(String opssWalletFileSecretName) {
    // construct the patch string for adding server pod resources
    StringBuffer patchStr = new StringBuffer("[{")
        .append("\"op\": \"add\", ")
        .append("\"path\": \"/spec/configuration/opss/walletFileSecret\", ")
        .append("\"value\": \"")
        .append(opssWalletFileSecretName)
        .append("\"}]");

    logger.info("Adding opssWalletPasswordSecretName for domain {0} in namespace {1} using patch string: {2}",
        fmwDomainUid, fmwDomainNamespace, patchStr.toString());

    V1Patch patch = new V1Patch(new String(patchStr));

    return patchDomainCustomResource(fmwDomainUid, fmwDomainNamespace, patch, V1Patch.PATCH_FORMAT_JSON_PATCH);
  }

}
