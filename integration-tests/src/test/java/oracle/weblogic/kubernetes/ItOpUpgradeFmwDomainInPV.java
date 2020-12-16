// Copyright (c) 2020, Oracle Corporation and/or its affiliates.
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
import io.kubernetes.client.openapi.models.V1SecretReference;
import io.kubernetes.client.openapi.models.V1Volume;
import io.kubernetes.client.openapi.models.V1VolumeMount;
import oracle.weblogic.domain.AdminServer;
import oracle.weblogic.domain.AdminService;
import oracle.weblogic.domain.Channel;
import oracle.weblogic.domain.Cluster;
import oracle.weblogic.domain.Domain;
import oracle.weblogic.domain.DomainSpec;
import oracle.weblogic.domain.ServerPod;
import oracle.weblogic.kubernetes.actions.impl.OperatorParams;
import oracle.weblogic.kubernetes.actions.impl.primitive.Command;
import oracle.weblogic.kubernetes.actions.impl.primitive.CommandParams;
import oracle.weblogic.kubernetes.actions.impl.primitive.HelmParams;
import oracle.weblogic.kubernetes.annotations.IntegrationTest;
import oracle.weblogic.kubernetes.annotations.Namespaces;
import oracle.weblogic.kubernetes.logging.LoggingFacade;
import oracle.weblogic.kubernetes.utils.CleanupUtil;
import oracle.weblogic.kubernetes.utils.CommonTestUtils;
import oracle.weblogic.kubernetes.utils.DeployUtil;
import oracle.weblogic.kubernetes.utils.ExecResult;
import org.awaitility.core.ConditionFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static java.util.concurrent.TimeUnit.MINUTES;
import static java.util.concurrent.TimeUnit.SECONDS;
import static oracle.weblogic.kubernetes.TestConstants.ADMIN_PASSWORD_DEFAULT;
import static oracle.weblogic.kubernetes.TestConstants.ADMIN_USERNAME_DEFAULT;
import static oracle.weblogic.kubernetes.TestConstants.BASE_IMAGES_REPO_SECRET;
import static oracle.weblogic.kubernetes.TestConstants.DB_IMAGE_TO_USE_IN_SPEC;
import static oracle.weblogic.kubernetes.TestConstants.DEFAULT_EXTERNAL_SERVICE_NAME_SUFFIX;
import static oracle.weblogic.kubernetes.TestConstants.FMWINFRA_IMAGE_TO_USE_IN_SPEC;
import static oracle.weblogic.kubernetes.TestConstants.K8S_NODEPORT_HOST;
import static oracle.weblogic.kubernetes.TestConstants.OLD_DEFAULT_EXTERNAL_SERVICE_NAME_SUFFIX;
import static oracle.weblogic.kubernetes.TestConstants.OPERATOR_CHART_DIR;
import static oracle.weblogic.kubernetes.TestConstants.OPERATOR_GITHUB_CHART_REPO_URL;
import static oracle.weblogic.kubernetes.TestConstants.OPERATOR_RELEASE_NAME;
import static oracle.weblogic.kubernetes.actions.ActionConstants.ITTESTS_DIR;
import static oracle.weblogic.kubernetes.actions.ActionConstants.RESOURCE_DIR;
import static oracle.weblogic.kubernetes.actions.TestActions.getOperatorContainerImageName;
import static oracle.weblogic.kubernetes.actions.TestActions.getOperatorImageName;
import static oracle.weblogic.kubernetes.actions.TestActions.getServiceNodePort;
import static oracle.weblogic.kubernetes.actions.TestActions.uninstallOperator;
import static oracle.weblogic.kubernetes.actions.impl.primitive.Docker.getImageEnvVar;
import static oracle.weblogic.kubernetes.assertions.TestAssertions.appAccessibleInPod;
import static oracle.weblogic.kubernetes.assertions.TestAssertions.checkHelmReleaseRevision;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.checkAppIsRunning;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.checkPodReady;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.checkServiceExists;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.createDomainAndVerify;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.createSecretForBaseImages;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.createSecretWithUsernamePassword;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.getExternalServicePodName;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.installAndVerifyOperator;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.setPodAntiAffinity;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.upgradeAndVerifyOperator;
import static oracle.weblogic.kubernetes.utils.DbUtils.setupDBandRCUschema;
import static oracle.weblogic.kubernetes.utils.TestUtils.getNextFreePort;
import static oracle.weblogic.kubernetes.utils.ThreadSafeLogger.getLogger;
import static org.awaitility.Awaitility.with;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests to upgrade Operator with FMW domain in persistent volume using WLST.
 */
@DisplayName("Tests to upgrade Operator with FMW domain in persistent volume using WLST")
@IntegrationTest
public class ItOpUpgradeFmwDomainInPV {

  private static ConditionFactory withStandardRetryPolicy;
  private static ConditionFactory withQuickRetryPolicy;

  private static String opNamespace1 = null;
  private static String opNamespace2 = null;
  private static String domainNamespace = null;
  private static String oracle_home = null;
  private static String java_home = null;
  private List<String> namespaces;

  private static final String RCUSCHEMAPREFIX = "fmwdomainpv";
  private static final String ORACLEDBURLPREFIX = "oracledb.";
  private static final String ORACLEDBSUFFIX = ".svc.cluster.local:1521/devpdb.k8s";
  private static final String RCUSYSUSERNAME = "sys";
  private static final String RCUSYSPASSWORD = "Oradoc_db1";
  private static final String RCUSCHEMAUSERNAME = "myrcuuser";
  private static final String RCUSCHEMAPASSWORD = "Oradoc_db1";

  private static String dbUrl = null;
  private static LoggingFacade logger = null;

  private final String domainUid = "fmwdomain-inpv";
  private final String clusterName = "cluster-fmwdomain-inpv";
  private final String adminServerName = "wlst-admin-server";
  private final String managedServerNameBase = "wlst-ms-";
  private final String adminServerPodName = domainUid + "-" + adminServerName;
  private final String managedServerPodNamePrefix = domainUid + "-" + managedServerNameBase;
  private final int managedServerPort = 8001;
  private final String wlSecretName = domainUid + "-weblogic-credentials";
  private final String rcuSecretName = domainUid + "-rcu-credentials";
  private final int replicaCount = 2;

  private static String latestOperatorImageName;

  /**
   * Initialization of logger, conditionfactory and latest Operator image to all test methods.
   */
  @BeforeAll
  public static void initAll() {
    logger = getLogger();
    // create standard, reusable retry/backoff policy
    withStandardRetryPolicy = with().pollDelay(10, SECONDS)
        .and().with().pollInterval(10, SECONDS)
        .atMost(5, MINUTES).await();

    // create a reusable quick retry policy
    withQuickRetryPolicy = with().pollDelay(0, SECONDS)
        .and().with().pollInterval(4, SECONDS)
        .atMost(10, SECONDS).await();

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
    final String dbNamespace = namespaces.get(0);
    dbUrl = ORACLEDBURLPREFIX + dbNamespace + ORACLEDBSUFFIX;

    logger.info("Assign a unique namespace for operator1");
    assertNotNull(namespaces.get(1), "Namespace is null");
    opNamespace1 = namespaces.get(1);

    logger.info("Assign a unique namespace for operator2");
    assertNotNull(namespaces.get(2), "Namespace is null");
    opNamespace2 = namespaces.get(2);

    logger.info("Assign a unique namespace for FMW domain");
    assertNotNull(namespaces.get(3), "Namespace is null");
    domainNamespace = namespaces.get(3);

    logger.info("Start DB and create RCU schema for namespace: {0}, RCU prefix: {1}, "
        + "dbUrl: {2}, dbImage: {3},  fmwImage: {4} ", dbNamespace, RCUSCHEMAPREFIX, dbUrl,
        DB_IMAGE_TO_USE_IN_SPEC, FMWINFRA_IMAGE_TO_USE_IN_SPEC);
    assertDoesNotThrow(() -> setupDBandRCUschema(DB_IMAGE_TO_USE_IN_SPEC, FMWINFRA_IMAGE_TO_USE_IN_SPEC,
        RCUSCHEMAPREFIX, dbNamespace, 0, dbUrl),
        String.format("Failed to create RCU schema for prefix %s in the namespace %s with "
            + "dbUrl %s", RCUSCHEMAPREFIX, dbNamespace, dbUrl));

    logger.info("DB image: {0}, FMW image {1} used in the test",
        DB_IMAGE_TO_USE_IN_SPEC, FMWINFRA_IMAGE_TO_USE_IN_SPEC);
  }

  /**
   * Cleanup Kubernetes artifacts in the namespaces used by the test and delete CRD.
   */
  @AfterEach
  public void tearDown() {
    if (System.getenv("SKIP_CLEANUP") == null
        || (System.getenv("SKIP_CLEANUP") != null
        && System.getenv("SKIP_CLEANUP").equalsIgnoreCase("false"))) {
      CleanupUtil.cleanup(namespaces);
      new Command()
          .withParams(new CommandParams()
              .command("kubectl delete crd domains.weblogic.oracle --ignore-not-found"))
          .execute();
    }
  }

  /**
   * Operator upgrade from 2.6.0 to latest.
   * Install 2.6.0 release Operator from GitHub chart repository and create a FMW domain.
   * Delete Operator and install latest Operator and verify CRD version is updated.
   */
  @Test
  @DisplayName("Upgrade Operator from 2.6.0 to latest with FMW domain in PV")
  public void testOperatorUpgradeFrom260FmwDomainInPv() {
    installAndUpgradeOperator("2.6.0", OLD_DEFAULT_EXTERNAL_SERVICE_NAME_SUFFIX,  false);
  }

  /**
   * Operator upgrade from 3.0.0 to latest.
   * Install 3.0.0 Operator from GitHub chart repository and create a domain.
   * Deploy an application to the cluster in domain and verify the application can be
   * accessed while the operator is upgraded and after the upgrade.
   * Upgrade operator with latest Operator image and verify CRD version and image are updated.
   */
  @Test
  @DisplayName("Upgrade Operator from 3.0.0 to latest with FMW domain in PV")
  public void testOperatorUpgradeFrom300FmwDomainInPv() {
    installAndUpgradeOperator("3.0.0", OLD_DEFAULT_EXTERNAL_SERVICE_NAME_SUFFIX, true);
  }

  /**
   * Operator upgrade from 3.1.0 to latest.
   * Install 3.1.0 Operator from GitHub chart repository and create a domain.
   * Deploy an application to the cluster in domain and verify the application can be
   * accessed while the operator is upgraded and after the upgrade.
   * Upgrade operator with latest Operator image and verify CRD version and image are updated.
   */
  @Test
  @DisplayName("Upgrade Operator from 3.1.0 to latest with FMW domain in PV")
  public void testOperatorUpgradeFrom310FmwDomainInPv() {
    installAndUpgradeOperator("3.1.0", DEFAULT_EXTERNAL_SERVICE_NAME_SUFFIX, true);
  }

  private void installAndUpgradeOperator(String operatorVersion,
                                         String externalServiceNameSuffix,
                                         boolean useHelmUpgrade) {
    String domainVersion = getApiVersion(operatorVersion);

    // install operator with passed version and verify its running in ready state
    HelmParams opHelmParams =
        installAndVerifyOperaotByVersion(operatorVersion);

    // create FMW domain and verify
    createFmwDomainAndVerify(domainVersion);

    // upgrade to latest operator
    upgradeOperatorAndVerify(externalServiceNameSuffix, opHelmParams, useHelmUpgrade);
  }

  private HelmParams installAndVerifyOperaotByVersion(String operatorVersion) {
    // delete existing CRD
    new Command()
        .withParams(new CommandParams()
            .command("kubectl delete crd domains.weblogic.oracle --ignore-not-found"))
        .execute();

    // build Helm params to install the Operator
    HelmParams opHelmParams =
        new HelmParams().releaseName("weblogic-operator")
            .namespace(opNamespace1)
            .repoUrl(OPERATOR_GITHUB_CHART_REPO_URL)
            .repoName("weblogic-operator")
            .chartName("weblogic-operator")
            .chartVersion(operatorVersion);

    // install operator with passed version
    String opServiceAccount = opNamespace1 + "-sa";
    installAndVerifyOperator(opNamespace1, opServiceAccount, true,
        0, opHelmParams, domainNamespace);

    return opHelmParams;
  }

  private void upgradeOperatorAndVerify(String externalServiceNameSuffix,
                                        HelmParams opHelmParams,
                                        boolean useHelmUpgrade) {
    String opServiceAccount = opNamespace1 + "-sa";

    if (useHelmUpgrade) {
      // deploy application and access the application once to make sure the app is accessible
      deployAndAccessApplication(externalServiceNameSuffix);

      // start a new thread to collect the availability data of the application while the
      // main thread performs operator upgrade
      List<Integer> appAvailability = new ArrayList<Integer>();
      logger.info("Start a thread to keep track of the application's availability");
      Thread accountingThread =
          new Thread(
              () -> {
                collectAppAvailability(
                    opNamespace1,
                    appAvailability,
                    managedServerPodNamePrefix,
                    replicaCount,
                    "8001",
                    "testwebapp/index.jsp");
              });
      accountingThread.start();

      try {
        // upgrade to latest operator
        HelmParams upgradeHelmParams = new HelmParams()
            .releaseName(OPERATOR_RELEASE_NAME)
            .namespace(opNamespace1)
            .chartDir(OPERATOR_CHART_DIR)
            .repoUrl(null)
            .chartVersion(null)
            .chartName(null);

        // build operator chart values
        OperatorParams opParams = new OperatorParams()
            .helmParams(upgradeHelmParams)
            .image(latestOperatorImageName)
            .externalRestEnabled(true);

        assertTrue(upgradeAndVerifyOperator(opNamespace1, opParams),
            String.format("Failed to upgrade operator in namespace %s", opNamespace1));

        // check operator image name after upgrade
        logger.info("Checking image name in operator container ");
        withStandardRetryPolicy
            .conditionEvaluationListener(
                condition -> logger.info("Checking operator image name in namespace {0} after upgrade "
                        + "(elapsed time {1}ms, remaining time {2}ms)",
                    opNamespace1,
                    condition.getElapsedTimeInMS(),
                    condition.getRemainingTimeInMS()))
            .until(assertDoesNotThrow(() -> getOpContainerImageName(),
                "Exception while getting the operator image name"));
      } finally {
        if (accountingThread != null) {
          try {
            accountingThread.join();
          } catch (InterruptedException ie) {
            // do nothing
          }
          // check the application availability data that we have collected, and see if
          // the application has been available all the time during the upgrade
          logger.info("Verify that the application was available when the operator was being upgraded");
          assertTrue(appAlwaysAvailable(appAvailability),
              "Application was not always available when the operator was getting upgraded");
        }
      }
    } else {
      opServiceAccount = opNamespace2 + "-sa";

      // uninstall operator 2.6.0
      assertTrue(uninstallOperator(opHelmParams),
          String.format("Uninstall operator failed in namespace %s", opNamespace1));

      // install latest operator
      installAndVerifyOperator(opNamespace2, opServiceAccount, true, 0);
    }
  }

  private void deployAndAccessApplication(String externalServiceNameSuffix) {
    logger.info("Getting node port for admin server default channel");
    int serviceNodePort = assertDoesNotThrow(() ->
        getServiceNodePort(domainNamespace, getExternalServicePodName(adminServerPodName,
            externalServiceNameSuffix), "default"),
            "Getting admin server node port failed");
    assertNotEquals(-1, serviceNodePort, "admin server default node port is not valid");

    // deploy the app
    Path archivePath = Paths.get(ITTESTS_DIR, "../src/integration-tests/apps/testwebapp.war");
    logger.info("Deploying application {0} to domain {1} cluster target {2} in namespace {3}",
        archivePath, domainUid, clusterName, domainNamespace);
    ExecResult result = DeployUtil.deployUsingRest(K8S_NODEPORT_HOST,
        String.valueOf(serviceNodePort),
        ADMIN_USERNAME_DEFAULT, ADMIN_PASSWORD_DEFAULT,
        clusterName, archivePath, null, "testwebapp");
    assertNotNull(result, "Application deployment failed");
    logger.info("Application deployment returned {0}", result.toString());
    assertEquals("202", result.stdout(), "Deployment didn't return HTTP status code 202");

    // check if the application is accessible inside of a server pod using quick retry policy
    logger.info("Check and wait for the application to become ready");
    for (int i = 1; i <= replicaCount; i++) {
      checkAppIsRunning(withQuickRetryPolicy, domainNamespace, managedServerPodNamePrefix + i,
          "8001", "testwebapp/index.jsp", managedServerPodNamePrefix + i);
    }
  }

  private Callable<Boolean> getOpContainerImageName() {
    return () -> {
      String imageName = getOperatorContainerImageName(opNamespace1);
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

  private void createFmwDomainAndVerify(String domainVersion) {
    final String pvName = domainUid + "-pv";
    final String pvcName = domainUid + "-pvc";
    final int t3ChannelPort = getNextFreePort(30000, 32767);

    // create pull secrets for domainNamespace when running in non Kind Kubernetes cluster
    // this secret is used only for non-kind cluster
    createSecretForBaseImages(domainNamespace);

    // create FMW domain credential secret
    createSecretWithUsernamePassword(wlSecretName, domainNamespace,
        ADMIN_USERNAME_DEFAULT, ADMIN_PASSWORD_DEFAULT);

    // create RCU credential secret
    CommonTestUtils.createRcuSecretWithUsernamePassword(rcuSecretName, domainNamespace,
        RCUSCHEMAUSERNAME, RCUSCHEMAPASSWORD, RCUSYSUSERNAME, RCUSYSPASSWORD);

    // create persistent volume and persistent volume claim for domain
    CommonTestUtils.createPV(pvName, domainUid, this.getClass().getSimpleName());
    CommonTestUtils.createPVC(pvName, pvcName, domainUid, domainNamespace);

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
    p.setProperty("domainParentDir", "/shared/domains/");
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
        File.createTempFile("domain", "properties"),
        "Failed to create domain properties file");

    // create the property file
    assertDoesNotThrow(() ->
        p.store(new FileOutputStream(domainPropertiesFile), "FMW wlst properties file"),
        "Failed to write domain properties file");

    return domainPropertiesFile;
  }

  private void createDomainCrAndVerify(String domainVersion,
                                       String pvName,
                                       String pvcName,
                                       int t3ChannelPort) {
    // create a domain custom resource configuration object
    logger.info("Creating domain custom resource");
    Domain domain = new Domain()
        .apiVersion("weblogic.oracle/" + domainVersion)
        .kind("Domain")
        .metadata(new V1ObjectMeta()
            .name(domainUid)
            .namespace(domainNamespace))
        .spec(new DomainSpec()
            .domainUid(domainUid)
            .domainHome("/shared/domains/" + domainUid)  // point to domain home in pv
            .domainHomeSourceType("PersistentVolume") // set the domain home source type as pv
            .image(FMWINFRA_IMAGE_TO_USE_IN_SPEC)
            .imagePullPolicy("IfNotPresent")
            .imagePullSecrets(Arrays.asList(
                new V1LocalObjectReference()
                    .name(BASE_IMAGES_REPO_SECRET)))
            .webLogicCredentialsSecret(new V1SecretReference()
                .name(wlSecretName)
                .namespace(domainNamespace))
            .includeServerOutInPodLog(true)
            .logHomeEnabled(Boolean.TRUE)
            .logHome("/shared/logs/" + domainUid)
            .dataHome("")
            .serverStartPolicy("IF_NEEDED")
            .serverPod(new ServerPod() //serverpod
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
                .serverStartState("RUNNING")
                .adminService(new AdminService()
                    .addChannelsItem(new Channel()
                        .channelName("default")
                        .nodePort(0))
                    .addChannelsItem(new Channel()
                        .channelName("T3Channel")
                        .nodePort(t3ChannelPort))))
            .addClustersItem(new Cluster() //cluster
                .clusterName(clusterName)
                .replicas(replicaCount)
                .serverStartState("RUNNING")
                ));
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
        () -> CommonTestUtils.createConfigMapForDomainCreation(domainScriptConfigMapName, domainScriptFiles,
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
    CommonTestUtils.createDomainJob(FMWINFRA_IMAGE_TO_USE_IN_SPEC, pvName, pvcName, domainScriptConfigMapName,
        domainNamespace, jobCreationContainer);
  }

  private static void collectAppAvailability(String operatorNamespace,
                                             List<Integer> appAvailability,
                                             String managedServerPrefix,
                                             int replicaCount,
                                             String internalPort,
                                             String appPath) {
    // Access the pod periodically to check application's availability while upgrade is happening
    // and after upgrade is complete.
    // appAccessedAfterUpgrade is used to access the app once after upgrade is complete
    boolean appAccessedAfterUpgrade = false;
    while (!appAccessedAfterUpgrade) {
      boolean isUpgradeComplete = checkHelmReleaseRevision(OPERATOR_RELEASE_NAME, operatorNamespace, "2");
      // upgrade is not complete or app is not accessed after upgrade
      if (!isUpgradeComplete || !appAccessedAfterUpgrade) {
        for (int i = 1; i <= replicaCount; i++) {
          if (appAccessibleInPod(domainNamespace,
              managedServerPrefix + i,
              internalPort,
              appPath,
              managedServerPrefix + i)) {
            appAvailability.add(1);
            logger.fine("application is accessible in pod " + managedServerPrefix + i);
          } else {
            appAvailability.add(0);
            logger.fine("application is not accessible in pod " + managedServerPrefix + i);
          }
        }
      }
      if (isUpgradeComplete) {
        logger.info("Upgrade is complete and app is accessed after upgrade");
        appAccessedAfterUpgrade = true;
      }
    }
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

  private String getApiVersion(String operatorVersion) {
    String apiVersion = null;
    switch (operatorVersion) {
      case "2.6.0":
        apiVersion = "v7";
        break;
      default:
        apiVersion = TestConstants.DOMAIN_VERSION;
    }

    return apiVersion;
  }
}

