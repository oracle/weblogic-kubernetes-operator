// Copyright (c) 2020, 2024, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.weblogic.kubernetes;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.Callable;

import io.kubernetes.client.openapi.models.V1Container;
import io.kubernetes.client.openapi.models.V1EnvVar;
import io.kubernetes.client.openapi.models.V1LocalObjectReference;
import io.kubernetes.client.openapi.models.V1ObjectMeta;
import io.kubernetes.client.openapi.models.V1PersistentVolumeClaimVolumeSource;
import io.kubernetes.client.openapi.models.V1Volume;
import io.kubernetes.client.openapi.models.V1VolumeMount;
import oracle.weblogic.domain.AdminServer;
import oracle.weblogic.domain.AdminService;
import oracle.weblogic.domain.Channel;
import oracle.weblogic.domain.DomainResource;
import oracle.weblogic.domain.DomainSpec;
import oracle.weblogic.domain.ServerPod;
import oracle.weblogic.kubernetes.actions.impl.OperatorParams;
import oracle.weblogic.kubernetes.actions.impl.primitive.HelmParams;
import oracle.weblogic.kubernetes.annotations.IntegrationTest;
import oracle.weblogic.kubernetes.annotations.Namespaces;
import oracle.weblogic.kubernetes.logging.LoggingFacade;
import oracle.weblogic.kubernetes.utils.CleanupUtil;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static oracle.weblogic.kubernetes.TestConstants.ADMIN_PASSWORD_DEFAULT;
import static oracle.weblogic.kubernetes.TestConstants.ADMIN_USERNAME_DEFAULT;
import static oracle.weblogic.kubernetes.TestConstants.BASE_IMAGES_REPO_SECRET_NAME;
import static oracle.weblogic.kubernetes.TestConstants.DB_IMAGE_TO_USE_IN_SPEC;
import static oracle.weblogic.kubernetes.TestConstants.DEFAULT_EXTERNAL_SERVICE_NAME_SUFFIX;
import static oracle.weblogic.kubernetes.TestConstants.FMWINFRA_IMAGE_TO_USE_IN_SPEC;
import static oracle.weblogic.kubernetes.TestConstants.IMAGE_PULL_POLICY;
import static oracle.weblogic.kubernetes.TestConstants.K8S_NODEPORT_HOST;
import static oracle.weblogic.kubernetes.TestConstants.OPERATOR_CHART_DIR;
import static oracle.weblogic.kubernetes.TestConstants.OPERATOR_GITHUB_CHART_REPO_URL;
import static oracle.weblogic.kubernetes.TestConstants.OPERATOR_RELEASE_NAME;
import static oracle.weblogic.kubernetes.TestConstants.RESULTS_TEMPFILE;
import static oracle.weblogic.kubernetes.TestConstants.SKIP_CLEANUP;
import static oracle.weblogic.kubernetes.actions.ActionConstants.RESOURCE_DIR;
import static oracle.weblogic.kubernetes.actions.TestActions.getOperatorContainerImageName;
import static oracle.weblogic.kubernetes.actions.TestActions.getOperatorImageName;
import static oracle.weblogic.kubernetes.actions.impl.primitive.Image.getImageEnvVar;
import static oracle.weblogic.kubernetes.utils.ApplicationUtils.collectAppAvailability;
import static oracle.weblogic.kubernetes.utils.ApplicationUtils.deployAndAccessApplication;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.checkServiceExists;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.getNextFreePort;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.getUniqueName;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.testUntil;
import static oracle.weblogic.kubernetes.utils.ConfigMapUtils.createConfigMapForDomainCreation;
import static oracle.weblogic.kubernetes.utils.DbUtils.createRcuSecretWithUsernamePassword;
import static oracle.weblogic.kubernetes.utils.DbUtils.deleteDb;
import static oracle.weblogic.kubernetes.utils.DbUtils.setupDBandRCUschema;
import static oracle.weblogic.kubernetes.utils.DomainUtils.createDomainAndVerify;
import static oracle.weblogic.kubernetes.utils.ImageUtils.createBaseRepoSecret;
import static oracle.weblogic.kubernetes.utils.JobUtils.createDomainJob;
import static oracle.weblogic.kubernetes.utils.OperatorUtils.installAndVerifyOperator;
import static oracle.weblogic.kubernetes.utils.OperatorUtils.upgradeAndVerifyOperator;
import static oracle.weblogic.kubernetes.utils.PersistentVolumeUtils.createPV;
import static oracle.weblogic.kubernetes.utils.PersistentVolumeUtils.createPVC;
import static oracle.weblogic.kubernetes.utils.PodUtils.checkPodDoesNotExist;
import static oracle.weblogic.kubernetes.utils.PodUtils.checkPodReady;
import static oracle.weblogic.kubernetes.utils.PodUtils.setPodAntiAffinity;
import static oracle.weblogic.kubernetes.utils.SecretUtils.createSecretWithUsernamePassword;
import static oracle.weblogic.kubernetes.utils.ThreadSafeLogger.getLogger;
import static oracle.weblogic.kubernetes.utils.UpgradeUtils.cleanUpCRD;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests to upgrade Operator with FMW domain in persistent volume using WLST.
 * Install a released version of Operator from GitHub chart repository.
 * Create a domain using Domain-In-PV model with a dynamic cluster.
 * Upgrade operator with current Operator image build from current branch.
 * Verify Domain resource version and image are updated.
 */
@DisplayName("Tests to upgrade Operator with FMW domain in PV using WLST")
@IntegrationTest
@Tag("kind-upgrade")
class ItOperatorFmwUpgrade {

  private static String opNamespace = null;
  private static String domainNamespace = null;
  private static String dbNamespace = null;
  private static String oracle_home = null;
  private static String java_home = null;
  private List<String> namespaces;

  private static final String RCUSCHEMAPREFIX = "fmwdomainpv";
  private static final String ORACLEDBURLPREFIX = "oracledb.";
  private static String ORACLEDBSUFFIX = null;
  private static final String RCUSYSUSERNAME = "sys";
  private static final String RCUSYSPASSWORD = "Oradoc_db1";
  private static final String RCUSCHEMAUSERNAME = "myrcuuser";
  private static final String RCUSCHEMAPASSWORD = "Oradoc_db1";

  private static String dbUrl = null;
  private static LoggingFacade logger = null;

  private static final String domainUid = "fmwdomain-inpv";
  private static final String clusterName = "cluster-fmwdomain-inpv";
  private static final String adminServerName = "admin-server";
  private static final String managedServerNameBase = "managed-server";
  private static final String adminServerPodName = domainUid + "-" + adminServerName;
  private static final String managedServerPodNamePrefix = domainUid + "-" + managedServerNameBase;
  private final int managedServerPort = 8001;
  private final String wlSecretName = domainUid + "-weblogic-credentials";
  private final String rcuSecretName = domainUid + "-rcu-credentials";
  private static final int replicaCount = 1;

  private static String latestOperatorImageName;

  /**
   * Initialization of logger, conditionfactory and current Operator image to all test methods.
   */
  @BeforeAll
  public static void initAll() {
    logger = getLogger();

    latestOperatorImageName = getOperatorImageName();
  }

  /**
   * For each test:
   * Assigns unique namespaces for DB, operator and domains.
   * Start DB service and create RCU schema.
   * Pull FMW image and Oracle DB image if running tests in Kind cluster.
   *
   * @param namespaces injected by JUnit
   */
  @BeforeEach
  public void beforeEach(@Namespaces(4) List<String> namespaces) {
    this.namespaces = namespaces;

    logger.info("Assign a unique namespace for DB and RCU");
    assertNotNull(namespaces.get(0), "Namespace is null");
    dbNamespace = namespaces.get(0);
    final int dbListenerPort = getNextFreePort();
    ORACLEDBSUFFIX = ".svc.cluster.local:" + dbListenerPort + "/devpdb.k8s";
    dbUrl = ORACLEDBURLPREFIX + dbNamespace + ORACLEDBSUFFIX;

    logger.info("Assign a unique namespace for operator1");
    assertNotNull(namespaces.get(1), "Namespace is null");
    opNamespace = namespaces.get(1);

    logger.info("Assign a unique namespace for FMW domain");
    assertNotNull(namespaces.get(2), "Namespace is null");
    domainNamespace = namespaces.get(2);

    logger.info("Start DB and create RCU schema for namespace: {0}, dbListenerPort: {1}, RCU prefix: {2}, "
         + "dbUrl: {3}, dbImage: {4},  fmwImage: {5} ", dbNamespace, dbListenerPort, RCUSCHEMAPREFIX, dbUrl,
        DB_IMAGE_TO_USE_IN_SPEC, FMWINFRA_IMAGE_TO_USE_IN_SPEC);
    assertDoesNotThrow(() -> setupDBandRCUschema(DB_IMAGE_TO_USE_IN_SPEC, FMWINFRA_IMAGE_TO_USE_IN_SPEC,
        RCUSCHEMAPREFIX, dbNamespace, getNextFreePort(), dbUrl, dbListenerPort),
        String.format("Failed to create RCU schema for prefix %s in the namespace %s with "
        + "dbUrl %s, dbListenerPost %s", RCUSCHEMAPREFIX, dbNamespace, dbUrl, dbListenerPort));

    logger.info("DB image: {0}, FMW image {1} used in the test",
        DB_IMAGE_TO_USE_IN_SPEC, FMWINFRA_IMAGE_TO_USE_IN_SPEC);
  }

  /**
   * Cleanup Kubernetes artifacts in the namespaces used by the test and delete CRD.
   */
  @AfterEach
  public void tearDown() {
    //assertDoesNotThrow(() -> deleteDb(dbNamespace), String.format("Failed to delete DB %s", dbNamespace));

    if (!SKIP_CLEANUP) {

      assertDoesNotThrow(() -> deleteDb(dbNamespace), String.format("Failed to delete DB %s", dbNamespace));

      CleanupUtil.cleanup(namespaces);
      cleanUpCRD();
    }
  }

  /**
   * Operator upgrade from 3.3.8 to current with a FMW Domain.
   */
  @Disabled
  @DisplayName("Upgrade Operator from 3.3.8 to current")
  void testOperatorFmwUpgradeFrom338ToCurrent() {
    installAndUpgradeOperator("3.3.8", "v8", DEFAULT_EXTERNAL_SERVICE_NAME_SUFFIX);
  }

  /**
   * Operator upgrade from 3.4.5 to current with a FMW Domain.
   */
  @Test
  @DisplayName("Upgrade Operator from 3.4.5 to current")
  void testOperatorFmwUpgradeFrom345ToCurrent() {
    installAndUpgradeOperator("3.4.5", "v8", DEFAULT_EXTERNAL_SERVICE_NAME_SUFFIX);
  }

  /**
   * Operator upgrade from 3.4.6 to current with a FMW Domain.
   */
  @Test
  @DisplayName("Upgrade Operator from 3.4.6 to current")
  void testOperatorFmwUpgradeFrom346ToCurrent() {
    installAndUpgradeOperator("3.4.6", "v8", DEFAULT_EXTERNAL_SERVICE_NAME_SUFFIX);
  }

  /**
   * Operator upgrade from 4.0.7 to current with a FMW Domain.
   */
  @Test
  @DisplayName("Upgrade Operator from 4.0.7 to current")
  void testOperatorFmwUpgradeFrom407ToCurrent() {
    installAndUpgradeOperator("4.0.7", "v8", DEFAULT_EXTERNAL_SERVICE_NAME_SUFFIX);
  }

  /**
   * Operator upgrade from 4.0.8 to current with a FMW Domain.
   */
  @Test
  @DisplayName("Upgrade Operator from 4.0.8 to current")
  void testOperatorFmwUpgradeFrom408ToCurrent() {
    installAndUpgradeOperator("4.0.8", "v8", DEFAULT_EXTERNAL_SERVICE_NAME_SUFFIX);
  }

  /**
   * Operator upgrade from 4.1.0 to current with a FMW Domain.
   */
  @Test
  @DisplayName("Upgrade Operator from 4.1.0 to current")
  void testOperatorFmwUpgradeFrom410ToCurrent() {
    installAndUpgradeOperator("4.1.0", "v8", DEFAULT_EXTERNAL_SERVICE_NAME_SUFFIX);
  }

  /**
   * Operator upgrade from 4.1.1 to current with a FMW Domain.
   */
  @Test
  @DisplayName("Upgrade Operator from 4.1.1 to current")
  void testOperatorFmwUpgradeFrom411ToCurrent() {
    installAndUpgradeOperator("4.1.1", "v8", DEFAULT_EXTERNAL_SERVICE_NAME_SUFFIX);
  }

  private void installAndUpgradeOperator(
         String operatorVersion, String domainVersion,
         String externalServiceNameSuffix) {

    // install operator with older release
    HelmParams opHelmParams = installOperator(operatorVersion);

    // create FMW domain and verify
    createFmwDomainAndVerify(domainVersion);

    // upgrade to current operator
    upgradeOperatorAndVerify(externalServiceNameSuffix);
  }

  private HelmParams installOperator(String operatorVersion) {
    // delete existing CRD if any
    cleanUpCRD();

    // build Helm params to install the Operator
    HelmParams opHelmParams =
        new HelmParams().releaseName("weblogic-operator")
            .namespace(opNamespace)
            .repoUrl(OPERATOR_GITHUB_CHART_REPO_URL)
            .repoName("weblogic-operator")
            .chartName("weblogic-operator")
            .chartVersion(operatorVersion);

    // install operator with passed version
    String opServiceAccount = opNamespace + "-sa";
    installAndVerifyOperator(opNamespace, opServiceAccount, true,
        0, opHelmParams, domainNamespace);

    return opHelmParams;
  }

  private void upgradeOperatorAndVerify(String externalServiceNameSuffix) {
    String opServiceAccount = opNamespace + "-sa";
    String appName = "testwebapp.war";

    // deploy application and access the application once
    // to make sure the app is accessible
    deployAndAccessApplication(domainNamespace,
                                 domainUid,
                                 clusterName,
                                 adminServerName,
                                 adminServerPodName,
                                 managedServerPodNamePrefix,
                                 replicaCount,
                                 "7001",
                                 "8001");

    // start a new thread to collect the availability data of
    // the application while the main thread performs operator upgrade
    List<Integer> appAvailability = new ArrayList<Integer>();
    logger.info("Start a thread to keep track of the application's availability");
    Thread accountingThread =
          new Thread(
              () -> {
                collectAppAvailability(
                    domainNamespace,
                    opNamespace,
                    appAvailability,
                    adminServerPodName,
                    managedServerPodNamePrefix,
                    replicaCount,
                    "7001",
                    "8001",
                    "testwebapp/index.jsp");
              });
    accountingThread.start();

    try {
      // upgrade to current operator
      HelmParams upgradeHelmParams = new HelmParams()
            .releaseName(OPERATOR_RELEASE_NAME)
            .namespace(opNamespace)
            .chartDir(OPERATOR_CHART_DIR)
            .repoUrl(null)
            .chartVersion(null)
            .chartName(null);

      // build operator chart values
      OperatorParams opParams = new OperatorParams()
            .helmParams(upgradeHelmParams)
            .image(latestOperatorImageName)
            .externalRestEnabled(true);

      assertTrue(upgradeAndVerifyOperator(opNamespace, opParams),
            String.format("Failed to upgrade operator in namespace %s", opNamespace));

      // check operator image name after upgrade
      logger.info("Checking image name in operator container ");
      testUntil(
            assertDoesNotThrow(() -> getOpContainerImageName(),
              "Exception while getting the operator image name"),
            logger,
            "Checking operator image name in namespace {0} after upgrade",
            opNamespace);
    } finally {
      if (accountingThread != null) {
        try {
          accountingThread.join();
        } catch (InterruptedException ie) {
          // do nothing
        }
        // check the application availability data that we have collected,
        // and see if the application has been available all the time
        // during the upgrade
        logger.info("Verify that the application was available when the operator was being upgraded");
        assertTrue(appAlwaysAvailable(appAvailability),
              "Application was not always available when the operator was getting upgraded");
      }
    }
  }

  private void createFmwDomainAndVerify(String domainVersion) {
    final String pvName = getUniqueName(domainUid + "-pv-");
    final String pvcName = getUniqueName(domainUid + "-pvc-");
    final int t3ChannelPort = getNextFreePort();

    // create pull secrets for domainNamespace when running in non-kind
    // Kubernetes cluster this secret is used only for non-kind cluster
    createBaseRepoSecret(domainNamespace);

    // create FMW domain credential secret
    createSecretWithUsernamePassword(wlSecretName, domainNamespace,
        ADMIN_USERNAME_DEFAULT, ADMIN_PASSWORD_DEFAULT);

    // create RCU credential secret
    createRcuSecretWithUsernamePassword(rcuSecretName, domainNamespace,
        RCUSCHEMAUSERNAME, RCUSCHEMAPASSWORD, RCUSYSUSERNAME, RCUSYSPASSWORD);

    // create persistent volume and persistent volume claim for domain
    createPV(pvName, domainUid, this.getClass().getSimpleName());
    createPVC(pvName, pvcName, domainUid, domainNamespace);

    File domainPropertiesFile = createWlstPropertyFile(t3ChannelPort);

    // WLST script for creating domain
    Path wlstScript = Paths.get(RESOURCE_DIR, "python-scripts", "jrf-wlst-create-domain-onpv.py");

    // create configmap and domain on persistent volume using the WLST script and property file
    createDomainOnPvUsingWlst(wlstScript, domainPropertiesFile.toPath(), pvName, pvcName);

    // create domain and verify
    createDomainCrAndVerify(domainVersion, pvName, pvcName, t3ChannelPort);

    // verify the admin server service created
    checkServiceExists(adminServerPodName, domainNamespace);

    // verify admin server pod is ready
    checkPodReady(adminServerPodName, domainUid, domainNamespace);

    // verify managed server services created
    for (int i = 1; i <= replicaCount; i++) {
      logger.info("Checking managed server service {0} is created in namespace {1}",
          managedServerPodNamePrefix + i, domainNamespace);
      checkServiceExists(managedServerPodNamePrefix + i, domainNamespace);

      logger.info("Waiting for managed server pod {0} to be ready in namespace {1}",
          managedServerPodNamePrefix + i, domainNamespace);
      checkPodReady(managedServerPodNamePrefix + i, domainUid, domainNamespace);
    }
  }

  private void createDomainCrAndVerify(String domainVersion,
                                       String pvName,
                                       String pvcName,
                                       int t3ChannelPort) {

    // create a domain custom resource configuration object
    logger.info("Creating domain custom resource");
    DomainResource domain = new DomainResource()
        .apiVersion("weblogic.oracle/" + domainVersion)
        .kind("Domain")
        .metadata(new V1ObjectMeta()
            .name(domainUid)
            .namespace(domainNamespace))
        .spec(new DomainSpec()
            .domainUid(domainUid)
            .domainHome("/shared/" + domainNamespace + "/domains/" + domainUid)
            .domainHomeSourceType("PersistentVolume")
            .image(FMWINFRA_IMAGE_TO_USE_IN_SPEC)
            .imagePullPolicy(IMAGE_PULL_POLICY)
            .imagePullSecrets(Arrays.asList(
                new V1LocalObjectReference()
                    .name(BASE_IMAGES_REPO_SECRET_NAME)))
            .webLogicCredentialsSecret(new V1LocalObjectReference()
                .name(wlSecretName))
            .includeServerOutInPodLog(true)
            .logHomeEnabled(Boolean.TRUE)
            .logHome("/shared/" + domainNamespace + "/logs/" + domainUid)
            .dataHome("")
            .serverStartPolicy("v8".equals(domainVersion) ? "IF_NEEDED" : "IfNeeded")
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
            .adminServer(new AdminServer() //admin server
                .adminService(new AdminService()
                    .addChannelsItem(new Channel()
                        .channelName("default")
                        .nodePort(getNextFreePort()))
                    .addChannelsItem(new Channel()
                        .channelName("T3Channel")
                        .nodePort(t3ChannelPort))))
            .replicas(1));
    setPodAntiAffinity(domain);

    // verify the domain custom resource is created
    createDomainAndVerify(domain, domainNamespace, domainVersion);
  }

  private void createDomainOnPvUsingWlst(Path wlstScriptFile,
                                         Path domainPropertiesFile,
                                         String pvName,
                                         String pvcName) {

    logger.info("Preparing to run create domain job using WLST");

    List<Path> domainScriptFiles = new ArrayList<>();
    domainScriptFiles.add(wlstScriptFile);
    domainScriptFiles.add(domainPropertiesFile);

    logger.info("Creating a config map to hold domain creation scripts");
    String domainScriptConfigMapName = "create-domain-scripts-cm";
    assertDoesNotThrow(
        () -> createConfigMapForDomainCreation(domainScriptConfigMapName, domainScriptFiles,
            domainNamespace, this.getClass().getSimpleName()),
        "Create configmap for domain creation failed");

    // create a V1Container with specific scripts and properties for creating domain
    V1Container jobCreationContainer = new V1Container()
        .addCommandItem("/bin/sh")
        .addArgsItem("/u01/oracle/oracle_common/common/bin/wlst.sh")
        .addArgsItem("/u01/weblogic/" + wlstScriptFile.getFileName()) //wlst.sh script
        .addArgsItem("-skipWLSModuleScanning")
        .addArgsItem("-loadProperties")
        .addArgsItem("/u01/weblogic/" + domainPropertiesFile.getFileName()); //domain property file

    logger.info("Running a Kubernetes job to create the domain");
    createDomainJob(FMWINFRA_IMAGE_TO_USE_IN_SPEC, pvName, pvcName, domainScriptConfigMapName,
        domainNamespace, jobCreationContainer);
  }

  private File createWlstPropertyFile(int t3ChannelPort) {
    //get ENV variable from the image
    assertNotNull(getImageEnvVar(FMWINFRA_IMAGE_TO_USE_IN_SPEC, "ORACLE_HOME"),
        "envVar ORACLE_HOME from image is null");
    oracle_home = getImageEnvVar(FMWINFRA_IMAGE_TO_USE_IN_SPEC, "ORACLE_HOME");
    logger.info("ORACLE_HOME in image {0} is: {1}", FMWINFRA_IMAGE_TO_USE_IN_SPEC, oracle_home);
    assertNotNull(getImageEnvVar(FMWINFRA_IMAGE_TO_USE_IN_SPEC, "JAVA_HOME"),
        "envVar JAVA_HOME from image is null");
    java_home = getImageEnvVar(FMWINFRA_IMAGE_TO_USE_IN_SPEC, "JAVA_HOME");
    logger.info("JAVA_HOME in image {0} is: {1}", FMWINFRA_IMAGE_TO_USE_IN_SPEC, java_home);

    // create wlst property file object
    Properties p = new Properties();
    p.setProperty("oracleHome", oracle_home); //default $ORACLE_HOME
    p.setProperty("javaHome", java_home); //default $JAVA_HOME
    p.setProperty("domainParentDir", "/shared/" + domainNamespace + "/domains/");
    p.setProperty("domainName", domainUid);
    p.setProperty("domainUser", ADMIN_USERNAME_DEFAULT);
    p.setProperty("domainPassword", ADMIN_PASSWORD_DEFAULT);
    p.setProperty("rcuDb", dbUrl);
    p.setProperty("rcuSchemaPrefix", RCUSCHEMAPREFIX);
    p.setProperty("rcuSchemaPassword", RCUSCHEMAPASSWORD);
    p.setProperty("adminListenPort", "7001");
    p.setProperty("adminName", adminServerName);
    p.setProperty("managedNameBase", managedServerNameBase);
    p.setProperty("managedServerPort", Integer.toString(managedServerPort));
    p.setProperty("prodMode", "true");
    p.setProperty("managedCount", "4");
    p.setProperty("clusterName", clusterName);
    p.setProperty("t3ChannelPublicAddress", K8S_NODEPORT_HOST);
    p.setProperty("t3ChannelPort", Integer.toString(t3ChannelPort));
    p.setProperty("exposeAdminT3Channel", "true");

    // create a temporary WebLogic domain property file
    File domainPropertiesFile = assertDoesNotThrow(() ->
        File.createTempFile("domain", ".properties", new File(RESULTS_TEMPFILE)),
        "Failed to create domain properties file");

    // create the property file
    assertDoesNotThrow(() ->
        p.store(new FileOutputStream(domainPropertiesFile), "FMW wlst properties file"),
        "Failed to write domain properties file");

    return domainPropertiesFile;
  }

  private Callable<Boolean> getOpContainerImageName() {
    return () -> {
      String imageName = getOperatorContainerImageName(opNamespace);
      if (imageName != null) {
        if (!imageName.equals(latestOperatorImageName)) {
          logger.info("Operator image name {0} doesn't match with latest image {1}",
              imageName, latestOperatorImageName);
          return false;
        } else {
          logger.info("Operator image name {0}", imageName);
          return true;
        }
      }
      return false;
    };
  }

  private static boolean appAlwaysAvailable(List<Integer> appAvailability) {
    for (Integer count : appAvailability) {
      if (count == 0) {
        logger.warning("Application was not available during operator upgrade.");
        return false;
      }
    }
    return true;
  }

  private static void verifyPodNotRunning(String domainNamespace) {
    // check that admin server pod doesn't exists in the domain namespace
    logger.info("Checking that admin server pod {0} doesn't exists in namespace {1}",
        adminServerPodName, domainNamespace);
    checkPodDoesNotExist(adminServerPodName, domainUid, domainNamespace);

    // check for managed server pods existence in the domain namespace
    for (int i = 1; i <= replicaCount; i++) {
      String managedServerPodName = managedServerPodNamePrefix + i;

      // check that managed server pod doesn't exists in the domain namespace
      logger.info("Checking that managed server pod {0} doesn't exists in namespace {1}",
          managedServerPodName, domainNamespace);
      checkPodDoesNotExist(managedServerPodName, domainUid, domainNamespace);
    }
  }

}

