// Copyright (c) 2023, 2024, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.weblogic.kubernetes;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import io.kubernetes.client.custom.V1Patch;
import io.kubernetes.client.openapi.ApiException;
import io.kubernetes.client.openapi.models.V1ConfigMap;
import io.kubernetes.client.openapi.models.V1ObjectMeta;
import oracle.weblogic.domain.ClusterResource;
import oracle.weblogic.domain.DomainCreationImage;
import oracle.weblogic.domain.DomainResource;
import oracle.weblogic.kubernetes.actions.impl.primitive.HelmParams;
import oracle.weblogic.kubernetes.actions.impl.primitive.WitParams;
import oracle.weblogic.kubernetes.annotations.IntegrationTest;
import oracle.weblogic.kubernetes.annotations.Namespaces;
import oracle.weblogic.kubernetes.logging.LoggingFacade;
import org.awaitility.core.ConditionFactory;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import static oracle.weblogic.kubernetes.TestConstants.ADMIN_PASSWORD_DEFAULT;
import static oracle.weblogic.kubernetes.TestConstants.ADMIN_USERNAME_DEFAULT;
import static oracle.weblogic.kubernetes.TestConstants.ELASTICSEARCH_HOST;
import static oracle.weblogic.kubernetes.TestConstants.FMWINFRA_IMAGE_TO_USE_IN_SPEC;
import static oracle.weblogic.kubernetes.TestConstants.MII_AUXILIARY_IMAGE_NAME;
import static oracle.weblogic.kubernetes.TestConstants.MII_BASIC_IMAGE_TAG;
import static oracle.weblogic.kubernetes.TestConstants.OKD;
import static oracle.weblogic.kubernetes.TestConstants.OPERATOR_CHART_DIR;
import static oracle.weblogic.kubernetes.TestConstants.OPERATOR_RELEASE_NAME;
import static oracle.weblogic.kubernetes.TestConstants.RESULTS_TEMPFILE;
import static oracle.weblogic.kubernetes.TestConstants.TEST_IMAGES_REPO_SECRET_NAME;
import static oracle.weblogic.kubernetes.actions.ActionConstants.MODEL_DIR;
import static oracle.weblogic.kubernetes.actions.TestActions.createConfigMap;
import static oracle.weblogic.kubernetes.actions.TestActions.deletePod;
import static oracle.weblogic.kubernetes.actions.TestActions.getOperatorPodName;
import static oracle.weblogic.kubernetes.actions.TestActions.patchDomainCustomResource;
import static oracle.weblogic.kubernetes.actions.TestActions.patchDomainResourceWithNewIntrospectVersion;
import static oracle.weblogic.kubernetes.utils.AuxiliaryImageUtils.createAndPushAuxiliaryImage;
import static oracle.weblogic.kubernetes.utils.ClusterUtils.createClusterAndVerify;
import static oracle.weblogic.kubernetes.utils.ClusterUtils.createClusterResource;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.addSccToDBSvcAccount;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.checkPodReadyAndServiceExists;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.createCustomConditionFactory;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.getUniqueName;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.testUntil;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.verifyConfiguredSystemResource;
import static oracle.weblogic.kubernetes.utils.ConfigMapUtils.createConfigMapAndVerify;
import static oracle.weblogic.kubernetes.utils.DbUtils.createOracleDBUsingOperator;
import static oracle.weblogic.kubernetes.utils.DbUtils.createRcuAccessSecret;
import static oracle.weblogic.kubernetes.utils.DbUtils.createRcuSchema;
import static oracle.weblogic.kubernetes.utils.DbUtils.startOracleDB;
import static oracle.weblogic.kubernetes.utils.DomainUtils.createDomainAndVerify;
import static oracle.weblogic.kubernetes.utils.DomainUtils.deleteDomainResource;
import static oracle.weblogic.kubernetes.utils.FmwUtils.createDomainResourceSimplifyJrfPv;
import static oracle.weblogic.kubernetes.utils.FmwUtils.saveAndRestoreOpssWalletfileSecret;
import static oracle.weblogic.kubernetes.utils.FmwUtils.verifyDomainReady;
import static oracle.weblogic.kubernetes.utils.ImageUtils.createBaseRepoSecret;
import static oracle.weblogic.kubernetes.utils.ImageUtils.createTestRepoSecret;
import static oracle.weblogic.kubernetes.utils.JobUtils.getIntrospectJobName;
import static oracle.weblogic.kubernetes.utils.OKDUtils.createRouteForOKD;
import static oracle.weblogic.kubernetes.utils.OperatorUtils.installAndVerifyOperator;
import static oracle.weblogic.kubernetes.utils.PatchDomainUtils.patchDomainResourceServerStartPolicy;
import static oracle.weblogic.kubernetes.utils.PodUtils.checkPodDeleted;
import static oracle.weblogic.kubernetes.utils.PodUtils.checkPodDoesNotExist;
import static oracle.weblogic.kubernetes.utils.PodUtils.checkPodExists;
import static oracle.weblogic.kubernetes.utils.PodUtils.checkPodLogContains;
import static oracle.weblogic.kubernetes.utils.PodUtils.getExternalServicePodName;
import static oracle.weblogic.kubernetes.utils.SecretUtils.createOpsswalletpasswordSecret;
import static oracle.weblogic.kubernetes.utils.SecretUtils.createSecretWithUsernamePassword;
import static oracle.weblogic.kubernetes.utils.ThreadSafeLogger.getLogger;
import static org.apache.commons.io.FileUtils.delete;
import static org.apache.commons.io.FileUtils.deleteDirectory;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Test to create a FMW domain on PV with DomainOnPvSimplification feature when user pre-creates RCU.
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DisplayName("Test for initializeDomainOnPV when user per-creates RCU")
@IntegrationTest
@Tag("kind-sequential")
@Tag("oke-weekly-sequential")
public class ItFmwDomainInPvUserCreateRcu {

  private static String opNamespace = null;
  private static String domainNamespace = null;
  private static String dbNamespace = null;

  private static final String RCUSCHEMAPREFIX = "jrfdomainpv";
  private static final String ORACLEDBURLPREFIX = "oracledb.";
  private static String ORACLEDBSUFFIX = null;
  private static final String RCUSCHEMAPASSWORD = "Oradoc_db1";

  private static String dbUrl = null;
  private static LoggingFacade logger = null;
  private static String DOMAINHOMEPREFIX = null;
  private static final String domainUid1 = "jrfdomainonpv-userrcu1";
  private static final String domainUid3 = "jrfdomainonpv-userrcu3";
  private static final String domainUid4 = "jrfdomainonpv-userrcu4";

  private static final String miiAuxiliaryImage1Tag = "jrf1" + MII_BASIC_IMAGE_TAG;
  private final String adminSecretName1 = domainUid1 + "-weblogic-credentials";
  private final String adminSecretName3 = domainUid3 + "-weblogic-credentials";
  private final String adminSecretName4 = domainUid4 + "-weblogic-credentials";
  private final String rcuaccessSecretName1 = domainUid1 + "-rcu-credentials";
  private final String rcuaccessSecretName3 = domainUid3 + "-rcu-credentials";
  private final String rcuaccessSecretName4 = domainUid4 + "-rcu-credentials";
  private final String opsswalletpassSecretName1 = domainUid1 + "-opss-wallet-password-secret";
  private final String opsswalletpassSecretName3 = domainUid3 + "-opss-wallet-password-secret";
  private final String opsswalletpassSecretName4 = domainUid4 + "-opss-wallet-password-secret";
  private final String opsswalletfileSecretName1 = domainUid1 + "-opss-wallet-file-secret";
  private final String opsswalletfileSecretName3 = domainUid3 + "-opss-wallet-file-secret";
  private final String opsswalletfileSecretName4 = domainUid4 + "-opss-wallet-file-secret";
  private static String dbName = "my-orcl-sidb";
  private static final String RCUSYSPASSWORD = "Oradoc_db1";
  private static final int replicaCount = 1;

  private final String fmwModelFilePrefix = "model-fmwdomainonpv-rcu-wdt";
  private final String fmwModelFile = fmwModelFilePrefix + ".yaml";
  private static DomainCreationImage domainCreationImage1 = null;
  private static List<DomainCreationImage> domainCreationImages3 = new ArrayList<>();
  private static List<DomainCreationImage> domainCreationImages4 = new ArrayList<>();
  private static String configMapName = null;

  /**
   * Assigns unique namespaces for DB, operator and domain.
   * Start DB service and create RCU schema.
   * Pull FMW image and Oracle DB image if running tests in Kind cluster.
   */
  @BeforeAll
  public static void initAll(@Namespaces(3) List<String> namespaces) {
    logger = getLogger();

    // get a new unique dbNamespace
    logger.info("Assign a unique namespace for DB and RCU");
    assertNotNull(namespaces.get(0), "Namespace is null");
    dbNamespace = namespaces.get(0);

    // get a new unique opNamespace
    logger.info("Assign a unique namespace for operator1");
    assertNotNull(namespaces.get(1), "Namespace is null");
    opNamespace = namespaces.get(1);

    // get a new unique domainNamespace
    logger.info("Assign a unique namespace for FMW domain");
    assertNotNull(namespaces.get(2), "Namespace is null");
    domainNamespace = namespaces.get(2);

    // start DB
    dbUrl = assertDoesNotThrow(() -> createOracleDBUsingOperator(dbName, RCUSYSPASSWORD, dbNamespace));

    // install operator with DomainOnPvSimplification=true"
    HelmParams opHelmParams =
        new HelmParams().releaseName(OPERATOR_RELEASE_NAME)
            .namespace(opNamespace)
            .chartDir(OPERATOR_CHART_DIR);
    installAndVerifyOperator(opNamespace, opNamespace + "-sa", false,
        0, opHelmParams, ELASTICSEARCH_HOST, false, true, null,
        null, false, "INFO", "DomainOnPvSimplification=true", false, domainNamespace);

    // create pull secrets for domainNamespace when running in non Kind Kubernetes cluster
    // this secret is used only for non-kind cluster
    createBaseRepoSecret(domainNamespace);
  }

  /**
   * User creates RCU, Operate creates PV/PVC and FMW domain
   * Verify Pod is ready and service exists for both admin server and managed servers.
   */
  @Test
  @Order(1)
  @DisplayName("Create a FMW domain on PV when user per-creates RCU")
  @Tag("gate")
  void testFmwDomainOnPvUserCreatesRCU() {

    final String pvName = getUniqueName(domainUid1 + "-pv-");
    final String pvcName = getUniqueName(domainUid1 + "-pvc-");

    //create RCU schema
    assertDoesNotThrow(() -> createRcuSchema(FMWINFRA_IMAGE_TO_USE_IN_SPEC, RCUSCHEMAPREFIX + "1",
        dbUrl, dbNamespace),"create RCU schema failed");

    // create a model property file
    File fmwModelPropFile = createWdtPropertyFile(domainUid1, RCUSCHEMAPREFIX + "1");

    // Create the repo secret to pull the image
    // this secret is used only for non-kind cluster
    createTestRepoSecret(domainNamespace);

    // create secret for admin credentials
    logger.info("Create secret for admin credentials");
    assertDoesNotThrow(() -> createSecretWithUsernamePassword(
        adminSecretName1,
        domainNamespace,
        ADMIN_USERNAME_DEFAULT,
        ADMIN_PASSWORD_DEFAULT),
        String.format("createSecret failed for %s", adminSecretName1));

    // create RCU access secret
    logger.info("Creating RCU access secret: {0}, with prefix: {1}, dbUrl: {2}, schemapassword: {3})",
        rcuaccessSecretName1, RCUSCHEMAPREFIX + "1", RCUSCHEMAPASSWORD, dbUrl);
    assertDoesNotThrow(() -> createRcuAccessSecret(
        rcuaccessSecretName1,
        domainNamespace,
        RCUSCHEMAPREFIX + "1",
        RCUSCHEMAPASSWORD,
        dbUrl),
        String.format("createSecret failed for %s", rcuaccessSecretName1));

    logger.info("Create OPSS wallet password secret");
    assertDoesNotThrow(() -> createOpsswalletpasswordSecret(
        opsswalletpassSecretName1,
        domainNamespace,
        ADMIN_PASSWORD_DEFAULT),
        String.format("createSecret failed for %s", opsswalletpassSecretName1));

    List<String> modelList = new ArrayList<>();
    modelList.add(MODEL_DIR + "/" + fmwModelFile);
    List<String> modelProList = new ArrayList<>();
    modelProList.add(fmwModelPropFile.toPath().toString());
    String miiAuxiliaryImage1Tag = "jrf1" + MII_BASIC_IMAGE_TAG;
    WitParams witParams =
        new WitParams()
            .modelImageName(MII_AUXILIARY_IMAGE_NAME)
            .modelImageTag(miiAuxiliaryImage1Tag)
            .modelFiles(modelList)
            .modelVariableFiles(modelProList);
    createAndPushAuxiliaryImage(MII_AUXILIARY_IMAGE_NAME, miiAuxiliaryImage1Tag, witParams);
    domainCreationImage1 =
        new DomainCreationImage().image(MII_AUXILIARY_IMAGE_NAME + ":" + miiAuxiliaryImage1Tag);

    // create a domain custom resource configuration object
    logger.info("Creating domain custom resource with pvName: {0}", pvName);
    DomainResource domain = createDomainResourceSimplifyJrfPv(
        domainUid1, domainNamespace, adminSecretName1,
        TEST_IMAGES_REPO_SECRET_NAME,
        rcuaccessSecretName1,
        opsswalletpassSecretName1, null,
        pvName, pvcName, Collections.singletonList(domainCreationImage1), null);

    createDomainAndVerify(domain, domainNamespace);

    // verify that all servers are ready
    verifyDomainReady(domainNamespace, domainUid1, replicaCount, "nosuffix");
  }

  /**
   * Export the OPSS wallet file secret of Fmw domain from the previous run
   * Use this OPSS wallet file secret to create Fmw domain on PV to connect to the same database
   * Verify Pod is ready and service exists for both admin server and managed servers.
   */
  @Test
  @Order(2)
  @DisplayName("Create a FMW domain on PV when user provide OPSS wallet file secret")
  void testFmwDomainOnPvUserProvideOpss() {

    final String pvName = getUniqueName(domainUid1 + "-pv-");
    final String pvcName = getUniqueName(domainUid1 + "-pvc-");

    saveAndRestoreOpssWalletfileSecret(domainNamespace, domainUid1, opsswalletfileSecretName1);
    logger.info("Deleting domain custom resource with namespace: {0}, domainUid {1}", domainNamespace, domainUid1);
    deleteDomainResource(domainNamespace, domainUid1);
    try {
      deleteDirectory(Paths.get("/shared").toFile());
    } catch (IOException ioe) {
      logger.severe("Failed to cleanup directory /shared", ioe);
    }
    logger.info("Creating domain custom resource with pvName: {0}", pvName);
    DomainResource domain = createDomainResourceSimplifyJrfPv(
        domainUid1, domainNamespace, adminSecretName1,
        TEST_IMAGES_REPO_SECRET_NAME,
        rcuaccessSecretName1,
        opsswalletpassSecretName1, opsswalletfileSecretName1,
        pvName, pvcName, Collections.singletonList(domainCreationImage1), null);

    createDomainAndVerify(domain, domainNamespace);

    // verify that all servers are ready and EM console is accessible
    verifyDomainReady(domainNamespace, domainUid1, replicaCount, "nosuffix");

    // delete the domain
    deleteDomainResource(domainNamespace, domainUid1);
    //delete the rcu pod
    assertDoesNotThrow(() -> deletePod("rcu", dbNamespace),
              "Got exception while deleting server " + "rcu");
    checkPodDoesNotExist("rcu", null, dbNamespace);
    //delete the wallet file ewallet.p12
    try {
      delete(new File("./ewallet.p12"));
      logger.info("Wallet file ewallet.p12 is deleted");
    } catch (IOException ioe) {
      logger.severe("Failed to delete file ewallet.p12", ioe);
    }

  }

  /**
   * The user provides opss.walletFileSecret that does not exist.
   * In this case the domain will not be created and operator will log message like
   * "Domain xxx is not valid: OpssWalletFile secret 'xxx' not found in namespace xxx"
   */
  @Test
  @Order(3)
  @DisplayName("Create a FMW domain on PV when user provide OPSS wallet file secret that does not exist")
  void testFmwDomainOnPvUserProvideOpssNotexist() {
    String domainUid = "jrfdomainonpv-userrcu2";
    String adminSecretName = domainUid + "-weblogic-credentials";
    String rcuaccessSecretName = domainUid + "-rcu-credentials";
    String opsswalletpassSecretName = domainUid + "-opss-wallet-password-secret";
    String opsswalletfileSecretName = domainUid + "-opss-wallet-file-secret";
    final String pvName = getUniqueName(domainUid1 + "-pv-");
    final String pvcName = getUniqueName(domainUid1 + "-pvc-");

    //create RCU schema
    assertDoesNotThrow(() -> createRcuSchema(FMWINFRA_IMAGE_TO_USE_IN_SPEC, RCUSCHEMAPREFIX + "2",
        dbUrl, dbNamespace),"create RCU schema failed");

    // create a model property file
    File fmwModelPropFile = createWdtPropertyFile(domainUid, RCUSCHEMAPREFIX + "2");

    // Create the repo secret to pull the image
    // this secret is used only for non-kind cluster
    createTestRepoSecret(domainNamespace);

    // create secret for admin credentials
    logger.info("Create secret for admin credentials");
    assertDoesNotThrow(() -> createSecretWithUsernamePassword(
        adminSecretName,
        domainNamespace,
        ADMIN_USERNAME_DEFAULT,
        ADMIN_PASSWORD_DEFAULT),
        String.format("createSecret failed for %s", adminSecretName));

    // create RCU access secret
    logger.info("Creating RCU access secret: {0}, with prefix: {1}, dbUrl: {2}, schemapassword: {3})",
        rcuaccessSecretName, RCUSCHEMAPREFIX + "2", RCUSCHEMAPASSWORD, dbUrl);
    assertDoesNotThrow(() -> createRcuAccessSecret(
        rcuaccessSecretName,
        domainNamespace,
        RCUSCHEMAPREFIX + "2",
        RCUSCHEMAPASSWORD,
        dbUrl),
        String.format("createSecret failed for %s", rcuaccessSecretName));

    logger.info("Create OPSS wallet password secret");
    assertDoesNotThrow(() -> createOpsswalletpasswordSecret(
        opsswalletpassSecretName,
        domainNamespace,
        ADMIN_PASSWORD_DEFAULT),
        String.format("createSecret failed for %s", opsswalletpassSecretName));

    List<String> modelList = new ArrayList<>();
    modelList.add(MODEL_DIR + "/" + fmwModelFile);
    List<String> modelProList = new ArrayList<>();
    modelProList.add(fmwModelPropFile.toPath().toString());
    String miiAuxiliaryImageTag = "jrf2" + MII_BASIC_IMAGE_TAG;
    WitParams witParams =
        new WitParams()
            .modelImageName(MII_AUXILIARY_IMAGE_NAME)
            .modelImageTag(miiAuxiliaryImageTag)
            .modelFiles(modelList)
            .modelVariableFiles(modelProList);
    createAndPushAuxiliaryImage(MII_AUXILIARY_IMAGE_NAME, miiAuxiliaryImageTag, witParams);
    DomainCreationImage domainCreationImage =
        new DomainCreationImage().image(MII_AUXILIARY_IMAGE_NAME + ":" + miiAuxiliaryImageTag);

    // create a domain custom resource configuration object
    logger.info("Creating domain custom resource with pvName: {0}", pvName);
    DomainResource domain = createDomainResourceSimplifyJrfPv(
        domainUid, domainNamespace, adminSecretName,
        TEST_IMAGES_REPO_SECRET_NAME,
        rcuaccessSecretName,
        opsswalletpassSecretName, opsswalletfileSecretName,
        pvName, pvcName, Collections.singletonList(domainCreationImage), null);

    createDomainAndVerify(domain, domainNamespace);

    String expectedErrorMsg = String.format("Domain %s is not valid: OpssWalletFile secret '%s'"
        + " not found in namespace '%s'", domainUid, opsswalletfileSecretName, domainNamespace);
    String operatorPodName =
        assertDoesNotThrow(() -> getOperatorPodName(OPERATOR_RELEASE_NAME, opNamespace));
    verifyPodWithExpectedErrorMsg(expectedErrorMsg, operatorPodName, opNamespace);
    
    // delete the domain
    deleteDomainResource(domainNamespace, domainUid);
    //delete the rcu pod
    assertDoesNotThrow(() -> deletePod("rcu", dbNamespace),
              "Got exception while deleting server " + "rcu");
    checkPodDoesNotExist("rcu", null, dbNamespace);

  }
  
  /**
   * User creates RCU, Operate creates PV/PVC and FMW domain with additional WDT config map.
   * Verify Pod is ready and service exists for both admin server and managed servers.
   */
  @Test
  @Order(4)
  @DisplayName("Create a FMW domain on PV with additional WDT config map when user per-creates RCU")
  void testFmwDomainOnPvUserCreatesRCUwdtConfigMap() {

    final String pvName = getUniqueName(domainUid4 + "-pv-");
    final String pvcName = getUniqueName(domainUid4 + "-pvc-");

    //create RCU schema
    assertDoesNotThrow(() -> createRcuSchema(FMWINFRA_IMAGE_TO_USE_IN_SPEC, RCUSCHEMAPREFIX + "4",
        dbUrl, dbNamespace), "create RCU schema failed");

    // create a model property file
    File fmwModelPropFile = createWdtPropertyFile(domainUid4, RCUSCHEMAPREFIX + "4");

    // Create the repo secret to pull the image
    // this secret is used only for non-kind cluster
    createTestRepoSecret(domainNamespace);

    // create secret for admin credentials
    logger.info("Create secret for admin credentials");
    assertDoesNotThrow(() -> createSecretWithUsernamePassword(
        adminSecretName4,
        domainNamespace,
        ADMIN_USERNAME_DEFAULT,
        ADMIN_PASSWORD_DEFAULT),
        String.format("createSecret failed for %s", adminSecretName4));

    // create RCU access secret
    logger.info("Creating RCU access secret: {0}, with prefix: {1}, dbUrl: {2}, schemapassword: {3})",
        rcuaccessSecretName4, RCUSCHEMAPREFIX + "4", RCUSCHEMAPASSWORD, dbUrl);
    assertDoesNotThrow(() -> createRcuAccessSecret(
        rcuaccessSecretName4,
        domainNamespace,
        RCUSCHEMAPREFIX + "4",
        RCUSCHEMAPASSWORD,
        dbUrl),
        String.format("createSecret failed for %s", rcuaccessSecretName4));

    logger.info("Create OPSS wallet password secret");
    assertDoesNotThrow(() -> createOpsswalletpasswordSecret(
        opsswalletpassSecretName4,
        domainNamespace,
        ADMIN_PASSWORD_DEFAULT),
        String.format("createSecret failed for %s", opsswalletpassSecretName4));

    DomainCreationImage domainCreationImage = createImage(fmwModelFile,fmwModelPropFile,"jrf4");
    domainCreationImages4.add(domainCreationImage);

    logger.info("create WDT configMap with jms model");
    configMapName = "jmsconfigmap";
    createConfigMapAndVerify(
        configMapName, domainUid4, domainNamespace,
        Arrays.asList(MODEL_DIR + "/model.jms2.yaml"));

    // create a domain custom resource configuration object
    logger.info("Creating domain custom resource with pvName: {0}", pvName);
    DomainResource domain = createDomainResourceSimplifyJrfPv(
        domainUid4, domainNamespace, adminSecretName4,
        TEST_IMAGES_REPO_SECRET_NAME,
        rcuaccessSecretName4,
        opsswalletpassSecretName4, null,
        pvName, pvcName, domainCreationImages4, configMapName);

    createDomainAndVerify(domain, domainNamespace);

    // verify that all servers are ready
    verifyDomainReady(domainNamespace, domainUid4, replicaCount, "nosuffix");

    //create router for admin service on OKD
    String adminServerPodName = domainUid4 + "-admin-server";
    String adminSvcExtHost = createRouteForOKD(getExternalServicePodName(adminServerPodName), domainNamespace);
    logger.info("admin svc host = {0}", adminSvcExtHost);

    // check configuration for JMS
    checkConfiguredJMSresouce(domainNamespace, adminServerPodName, adminSvcExtHost);

  }


  /**
   * User creates RCU, Operate creates PV/PVC and FMW domain with multiple images
   * Verify Pod is ready and service exists for both admin server and managed servers.
   */
  @Test
  @Order(5)
  @DisplayName("Create a FMW domain on PV with multiple images when user per-creates RCU")
  void testFmwDomainOnPvUserCreatesRCUMultiImages() {

    final String pvName = getUniqueName(domainUid3 + "-pv-");
    final String pvcName = getUniqueName(domainUid3 + "-pvc-");

    //create RCU schema
    assertDoesNotThrow(() -> createRcuSchema(FMWINFRA_IMAGE_TO_USE_IN_SPEC, RCUSCHEMAPREFIX + "3",
        dbUrl, dbNamespace), "create RCU schema failed");

    // create a model property file
    File fmwModelPropFile = createWdtPropertyFile(domainUid3, RCUSCHEMAPREFIX + "3");

    // Create the repo secret to pull the image
    // this secret is used only for non-kind cluster
    createTestRepoSecret(domainNamespace);

    // create secret for admin credentials
    logger.info("Create secret for admin credentials");
    assertDoesNotThrow(() -> createSecretWithUsernamePassword(
        adminSecretName3,
        domainNamespace,
        ADMIN_USERNAME_DEFAULT,
        ADMIN_PASSWORD_DEFAULT),
        String.format("createSecret failed for %s", adminSecretName3));

    // create RCU access secret
    logger.info("Creating RCU access secret: {0}, with prefix: {1}, dbUrl: {2}, schemapassword: {3})",
        rcuaccessSecretName3, RCUSCHEMAPREFIX + "3", RCUSCHEMAPASSWORD, dbUrl);
    assertDoesNotThrow(() -> createRcuAccessSecret(
        rcuaccessSecretName3,
        domainNamespace,
        RCUSCHEMAPREFIX + "3",
        RCUSCHEMAPASSWORD,
        dbUrl),
        String.format("createSecret failed for %s", rcuaccessSecretName3));

    logger.info("Create OPSS wallet password secret");
    assertDoesNotThrow(() -> createOpsswalletpasswordSecret(
        opsswalletpassSecretName3,
        domainNamespace,
        ADMIN_PASSWORD_DEFAULT),
        String.format("createSecret failed for %s", opsswalletpassSecretName3));

    DomainCreationImage domainCreationImage1 = createImage(fmwModelFile,fmwModelPropFile,"jrf3");

    // image2 with model files for jms config
    List<String> modelList = new ArrayList<>();
    modelList.add(MODEL_DIR + "/model.jms2.yaml");
    String miiAuxiliaryImageTag = "jrf3jms" + MII_BASIC_IMAGE_TAG;
    WitParams witParams =
        new WitParams()
            .modelImageName(MII_AUXILIARY_IMAGE_NAME)
            .modelImageTag(miiAuxiliaryImageTag)
            .wdtModelOnly(true)
            .modelFiles(modelList)
            .wdtVersion("NONE");
    createAndPushAuxiliaryImage(MII_AUXILIARY_IMAGE_NAME, miiAuxiliaryImageTag, witParams);
    DomainCreationImage domainCreationImage2 = new DomainCreationImage().image(MII_AUXILIARY_IMAGE_NAME
        + ":" + miiAuxiliaryImageTag);
    domainCreationImages3.add(domainCreationImage1);
    domainCreationImages3.add(domainCreationImage2);

    // create a domain custom resource configuration object
    logger.info("Creating domain custom resource with pvName: {0}", pvName);
    DomainResource domain = createDomainResourceSimplifyJrfPv(
        domainUid3, domainNamespace, adminSecretName3,
        TEST_IMAGES_REPO_SECRET_NAME,
        rcuaccessSecretName3,
        opsswalletpassSecretName3, null,
        pvName, pvcName, domainCreationImages3, null);

    createDomainAndVerify(domain, domainNamespace);

    // verify that all servers are ready
    verifyDomainReady(domainNamespace, domainUid3, replicaCount, "nosuffix");

    //create router for admin service on OKD
    String adminServerPodName = domainUid3 + "-admin-server";
    String adminSvcExtHost = createRouteForOKD(getExternalServicePodName(adminServerPodName), domainNamespace);
    logger.info("admin svc host = {0}", adminSvcExtHost);

    // check configuration for JMS
    checkConfiguredJMSresouce(domainNamespace, adminServerPodName, adminSvcExtHost);

  }

  /**
   * Export the OPSS wallet file secret of Fmw domain from the previous run
   * CrateIfNotExists set to DomainAndRCU
   * Use this OPSS wallet file secret to restart Fmw domain on PV to connect to the same database
   * Verify Pod is ready and service exists for both admin server and managed servers.
   */
  @Test
  @Order(6)
  @DisplayName("Restart a FMW domain on PV with provided OPSS wallet file secret")
  void testReuseRCUschemaToRestartFmwDomain() {

    saveAndRestoreOpssWalletfileSecret(domainNamespace, domainUid3, opsswalletfileSecretName3);
    shutdownDomain(domainUid3);
    patchDomainWithWalletFileSecret(opsswalletfileSecretName3, domainUid3);
    startupDomain(domainUid3);

    // verify that all servers are ready
    verifyDomainReady(domainNamespace, domainUid3, replicaCount, "nosuffix");

    // delete the domain
    deleteDomainResource(domainNamespace, domainUid3);
    //delete the rcu pod
    assertDoesNotThrow(() -> deletePod("rcu", dbNamespace),
        "Got exception while deleting server " + "rcu");
    checkPodDoesNotExist("rcu", null, dbNamespace);
    //delete the wallet file ewallet.p12
    try {
      delete(new File("./ewallet.p12"));
      logger.info("Wallet file ewallet.p12 is deleted");
    } catch (IOException ioe) {
      logger.severe("Failed to delete file ewallet.p12", ioe);
    }
  }

  /**
   * User creates RCU, Operate creates PV/PVC and FMW domain.
   * Add a cluster to the domain and update the introspectVersion to trigger the introspection
   * Verify Operator starts the servers in the new cluster.
   */
  @Test
  @Order(7)
  @DisplayName("Create a FMW domain on PV with adding new cluster")
  void testFmwDomainOnPvUserWithAddedCluster() {
    String domainUid = "jrfdomainonpv-userrcu7";
    String adminSecretName = domainUid + "-weblogic-credentials";
    String rcuaccessSecretName = domainUid + "-rcu-credentials";
    String opsswalletpassSecretName = domainUid + "-opss-wallet-password-secret";
    String configMapName = domainUid + "-configmap";
    String cluster2Res   = domainUid + "-cluster-2";
    String cluster2Name  = "cluster-2";
    int replicaCluster2 = 2;
    String managedServer2Prefix = domainUid + "-c2-managed-server";
    final String pvName = getUniqueName(domainUid + "-pv-");
    final String pvcName = getUniqueName(domainUid + "-pvc-");

    //create RCU schema
    assertDoesNotThrow(() -> createRcuSchema(FMWINFRA_IMAGE_TO_USE_IN_SPEC, RCUSCHEMAPREFIX + "7",
        dbUrl, dbNamespace), "create RCU schema failed");

    // create a model property file
    File fmwModelPropFile = createWdtPropertyFile(domainUid, RCUSCHEMAPREFIX + "7");

    // Create the repo secret to pull the image
    // this secret is used only for non-kind cluster
    createTestRepoSecret(domainNamespace);

    // create secret for admin credentials
    logger.info("Create secret for admin credentials");
    assertDoesNotThrow(() -> createSecretWithUsernamePassword(
        adminSecretName,
        domainNamespace,
        ADMIN_USERNAME_DEFAULT,
        ADMIN_PASSWORD_DEFAULT),
        String.format("createSecret failed for %s", adminSecretName));

    // create RCU access secret
    logger.info("Creating RCU access secret: {0}, with prefix: {1}, dbUrl: {2}, schemapassword: {3})",
        rcuaccessSecretName, RCUSCHEMAPREFIX + "7", RCUSCHEMAPASSWORD, dbUrl);
    assertDoesNotThrow(() -> createRcuAccessSecret(
        rcuaccessSecretName,
        domainNamespace,
        RCUSCHEMAPREFIX + "7",
        RCUSCHEMAPASSWORD,
        dbUrl),
        String.format("createSecret failed for %s", rcuaccessSecretName));

    logger.info("Create OPSS wallet password secret");
    assertDoesNotThrow(() -> createOpsswalletpasswordSecret(
        opsswalletpassSecretName,
        domainNamespace,
        ADMIN_PASSWORD_DEFAULT),
        String.format("createSecret failed for %s", opsswalletpassSecretName));

    DomainCreationImage domainCreationImage = createImage(fmwModelFile,fmwModelPropFile,"jrf7");
    List<DomainCreationImage> domainCreationImages7 = new ArrayList<>();
    domainCreationImages7.add(domainCreationImage);

    logger.info("create WDT configMap with extra cluster");
    createModelConfigMap(domainUid,configMapName);

    // create a domain custom resource configuration object
    logger.info("Creating domain custom resource with pvName: {0}", pvName);
    DomainResource domain = createDomainResourceSimplifyJrfPv(
        domainUid, domainNamespace, adminSecretName,
        TEST_IMAGES_REPO_SECRET_NAME,
        rcuaccessSecretName,
        opsswalletpassSecretName, null,
        pvName, pvcName, domainCreationImages7, configMapName);

    createDomainAndVerify(domain, domainNamespace);

    // verify that all servers in the original cluster-1 are ready
    verifyDomainReady(domainNamespace, domainUid, replicaCount, "nosuffix");

    // create and deploy cluster resource
    ClusterResource cluster2 = createClusterResource(
        cluster2Res, cluster2Name, domainNamespace, replicaCluster2);
    logger.info("Creating ClusterResource {0} in namespace {1}",cluster2Res, domainNamespace);
    createClusterAndVerify(cluster2);

    logger.info("Patch domain resource by adding cluster cluster-2");
    String patchStr
        = "["
        + "{\"op\": \"add\",\"path\": \"/spec/clusters/-\", \"value\": {\"name\" : \"" + cluster2Res + "\"}"
        + "}]";
    logger.info("Updating domain configuration using patch string: {0}\n", patchStr);
    V1Patch patch2 = new V1Patch(patchStr);
    assertTrue(patchDomainCustomResource(domainUid, domainNamespace, patch2, V1Patch.PATCH_FORMAT_JSON_PATCH),
        "Failed to patch domain");

    patchDomainResourceWithNewIntrospectVersion(domainUid, domainNamespace);

    ConditionFactory customConditionFactory = createCustomConditionFactory(0, 1, 5);

    //verify the introspector pod is created and runs
    String introspectPodNameBase2 = getIntrospectJobName(domainUid);
    checkPodExists(customConditionFactory, introspectPodNameBase2, domainUid, domainNamespace);
    checkPodDoesNotExist(introspectPodNameBase2, domainUid, domainNamespace);

    // verify managed server pods from cluster-2 are created
    for (int i = 1; i <= replicaCluster2; i++) {
      logger.info("Wait for managed pod {0} to be ready in namespace {1}",
          managedServer2Prefix + i, domainNamespace);
      checkPodReadyAndServiceExists(managedServer2Prefix + i, domainUid, domainNamespace);
    }

    // delete the domain
    deleteDomainResource(domainNamespace, domainUid);
    //delete the rcu pod
    assertDoesNotThrow(() -> deletePod("rcu", dbNamespace),
              "Got exception while deleting server " + "rcu");
    checkPodDoesNotExist("rcu", null, dbNamespace);

  }

  private DomainCreationImage createImage(String fmwModelFile,  File fmwModelPropFile, String imageTagPrefix) {

    List<String> modelList = new ArrayList<>();
    modelList.add(MODEL_DIR + "/" + fmwModelFile);
    List<String> modelProList = new ArrayList<>();
    modelProList.add(fmwModelPropFile.toPath().toString());
    String miiAuxiliaryImageTag = imageTagPrefix + MII_BASIC_IMAGE_TAG;
    WitParams witParams =
        new WitParams()
            .modelImageName(MII_AUXILIARY_IMAGE_NAME)
            .modelImageTag(miiAuxiliaryImageTag)
            .modelFiles(modelList)
            .modelVariableFiles(modelProList);
    createAndPushAuxiliaryImage(MII_AUXILIARY_IMAGE_NAME, miiAuxiliaryImageTag, witParams);
    return new DomainCreationImage().image(MII_AUXILIARY_IMAGE_NAME + ":" + miiAuxiliaryImageTag);

  }

  private File createWdtPropertyFile(String domainUid, String rcuSchemaPrefix) {

    Properties p = new Properties();
    p.setProperty("rcuDb", dbUrl);
    p.setProperty("rcuSchemaPrefix", rcuSchemaPrefix);
    p.setProperty("rcuSchemaPassword", RCUSCHEMAPASSWORD);
    p.setProperty("adminUsername", ADMIN_USERNAME_DEFAULT);
    p.setProperty("adminPassword", ADMIN_PASSWORD_DEFAULT);
    p.setProperty("domainName", domainUid);

    // create a model property file
    File domainPropertiesFile = assertDoesNotThrow(() ->
        File.createTempFile(fmwModelFilePrefix, ".properties", new File(RESULTS_TEMPFILE)),
        "Failed to create FMW model properties file");

    // create the property file
    assertDoesNotThrow(() ->
        p.store(new FileOutputStream(domainPropertiesFile), "FMW properties file"),
        "Failed to write FMW properties file");

    return domainPropertiesFile;
  }

  /**
   * Start Oracle DB instance in the specified namespace.
   *
   * @param dbImage image name of database
   * @param dbNamespace namespace where DB and RCU schema are going to start
   * @param dbPort NodePort of DB
   * @param dbListenerPort TCP listener port of DB
   * @throws ApiException if any error occurs when setting up database
   */
  private static synchronized void setupDB(String dbImage, String dbNamespace, int dbPort, int dbListenerPort)
      throws ApiException {
    LoggingFacade logger = getLogger();
    // create pull secrets when running in non Kind Kubernetes cluster
    // this secret is used only for non-kind cluster
    createBaseRepoSecret(dbNamespace);

    if (OKD) {
      addSccToDBSvcAccount("default", dbNamespace);
    }

    logger.info("Start Oracle DB with dbImage: {0}, dbPort: {1}, dbNamespace: {2}, dbListenerPort:{3}",
        dbImage, dbPort, dbNamespace, dbListenerPort);
    startOracleDB(dbImage, dbPort, dbNamespace, dbListenerPort);
  }

  /**
   * Check Configured JMS Resource.
   *
   * @param domainNamespace domain namespace
   * @param adminServerPodName  admin server pod name
   * @param adminSvcExtHost admin server external host
   */
  private static void checkConfiguredJMSresouce(String domainNamespace, String adminServerPodName,
                                               String adminSvcExtHost) {
    verifyConfiguredSystemResource(domainNamespace, adminServerPodName, adminSvcExtHost,
        "JMSSystemResources", "TestClusterJmsModule2", "200");
  }

  private void verifyPodWithExpectedErrorMsg(String expectedErrorMsg, String podName, String nameSpace) {

    logger.info("Verifying operator pod log for error messages");
    testUntil(
        () -> assertDoesNotThrow(() -> checkPodLogContains(expectedErrorMsg, podName, nameSpace),
            String.format("Checking operator pod %s log failed for namespace %s, expectErrorMsg %s", podName,
               nameSpace, expectedErrorMsg)),
        logger,
        "Checking operator log containing the expected error msg {0}:",
        expectedErrorMsg);
  }

  // create a ConfigMap with a model that adds a cluster
  private static void createModelConfigMap(String domainid, String cfgMapName) {
    String yamlString = "topology:\n"
        + "  Cluster:\n"
        + "    'cluster-2':\n"
        + "  Server:\n"
        + "    'c2-managed-server1':\n"
        + "       Cluster: 'cluster-2' \n"
        + "       ListenPort : 8001 \n"
        + "    'c2-managed-server2':\n"
        + "       Cluster: 'cluster-2' \n"
        + "       ListenPort : 8001 \n"
        + "    'c2-managed-server3':\n"
        + "       Cluster: 'cluster-2' \n"
        + "       ListenPort : 8001 \n"
        + "    'c2-managed-server4':\n"
        + "       Cluster: 'cluster-2' \n"
        + "       ListenPort : 8001 \n"
        + "    'c2-managed-server5':\n"
        + "       Cluster: 'cluster-2' \n"
        + "       ListenPort : 8001 \n";

    Map<String, String> labels = new HashMap<>();
    labels.put("weblogic.domainUid", domainid);
    Map<String, String> data = new HashMap<>();
    data.put("model.cluster.yaml", yamlString);

    V1ConfigMap configMap = new V1ConfigMap()
        .data(data)
        .metadata(new V1ObjectMeta()
            .labels(labels)
            .name(cfgMapName)
            .namespace(domainNamespace));
    boolean cmCreated = assertDoesNotThrow(() -> createConfigMap(configMap),
        String.format("Can't create ConfigMap %s", cfgMapName));
    assertTrue(cmCreated, String.format("createConfigMap failed %s", cfgMapName));
  }

  /**
   * Shutdown the domain by setting serverStartPolicy as "Never".
   */
  private void shutdownDomain(String domainUid) {
    patchDomainResourceServerStartPolicy("/spec/serverStartPolicy", "Never", domainNamespace, domainUid);
    logger.info("Domain is patched to stop entire WebLogic domain");

    String adminServerPodName = domainUid + "-admin-server";
    String managedServerPrefix = domainUid + "-managed-server";

    // make sure all the server pods are removed after patch
    checkPodDeleted(adminServerPodName, domainUid, domainNamespace);
    for (int i = 1; i <= replicaCount; i++) {
      checkPodDeleted(managedServerPrefix + i, domainUid, domainNamespace);
    }

    logger.info("Domain shutdown success");

  }

  /**
   * Startup the domain by setting serverStartPolicy as "IfNeeded".
   */
  private void startupDomain(String domainUid) {
    patchDomainResourceServerStartPolicy("/spec/serverStartPolicy", "IfNeeded", domainNamespace,
        domainUid);
    logger.info("Domain is patched to start all servers in the domain");
  }

  /**
   * Patch the domain with opss wallet file secret.
   * @param opssWalletFileSecretName the name of opps wallet file secret
   * @return true if patching succeeds, false otherwise
   */
  private boolean patchDomainWithWalletFileSecret(String opssWalletFileSecretName, String domainUid) {
    // construct the patch string for adding server pod resources
    StringBuffer patchStr = new StringBuffer("[{")
        .append("\"op\": \"add\", ")
        .append("\"path\": \"/spec/configuration/opss/walletFileSecret\", ")
        .append("\"value\": \"")
        .append(opssWalletFileSecretName)
        .append("\"}]");

    logger.info("Adding opssWalletPasswordSecretName for domain {0} in namespace {1} using patch string: {2}",
        domainUid, domainNamespace, patchStr.toString());

    V1Patch patch = new V1Patch(new String(patchStr));

    return patchDomainCustomResource(domainUid, domainNamespace, patch, V1Patch.PATCH_FORMAT_JSON_PATCH);
  }

  
}