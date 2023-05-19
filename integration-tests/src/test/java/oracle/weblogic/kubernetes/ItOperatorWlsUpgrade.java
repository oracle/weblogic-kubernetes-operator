// Copyright (c) 2020, 2023, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.weblogic.kubernetes;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import io.kubernetes.client.openapi.models.V1EnvVar;
import io.kubernetes.client.openapi.models.V1LocalObjectReference;
import io.kubernetes.client.openapi.models.V1ObjectMeta;
import oracle.weblogic.domain.AdminServer;
import oracle.weblogic.domain.AdminService;
import oracle.weblogic.domain.Channel;
import oracle.weblogic.domain.Configuration;
import oracle.weblogic.domain.DomainResource;
import oracle.weblogic.domain.DomainSpec;
import oracle.weblogic.domain.Model;
import oracle.weblogic.domain.ServerPod;
import oracle.weblogic.kubernetes.actions.impl.primitive.Command;
import oracle.weblogic.kubernetes.actions.impl.primitive.CommandParams;
import oracle.weblogic.kubernetes.annotations.IntegrationTest;
import oracle.weblogic.kubernetes.annotations.Namespaces;
import oracle.weblogic.kubernetes.logging.LoggingFacade;
import oracle.weblogic.kubernetes.utils.CleanupUtil;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static java.nio.file.StandardCopyOption.REPLACE_EXISTING;
import static oracle.weblogic.kubernetes.TestConstants.ADMIN_PASSWORD_DEFAULT;
import static oracle.weblogic.kubernetes.TestConstants.ADMIN_USERNAME_DEFAULT;
import static oracle.weblogic.kubernetes.TestConstants.DEFAULT_EXTERNAL_SERVICE_NAME_SUFFIX;
import static oracle.weblogic.kubernetes.TestConstants.DOMAIN_API_VERSION;
import static oracle.weblogic.kubernetes.TestConstants.DOMAIN_STATUS_CONDITION_COMPLETED_TYPE;
import static oracle.weblogic.kubernetes.TestConstants.ENCRYPION_PASSWORD_DEFAULT;
import static oracle.weblogic.kubernetes.TestConstants.ENCRYPION_USERNAME_DEFAULT;
import static oracle.weblogic.kubernetes.TestConstants.K8S_NODEPORT_HOST;
import static oracle.weblogic.kubernetes.TestConstants.KUBERNETES_CLI;
import static oracle.weblogic.kubernetes.TestConstants.MII_AUXILIARY_IMAGE_NAME;
import static oracle.weblogic.kubernetes.TestConstants.MII_BASIC_APP_NAME;
import static oracle.weblogic.kubernetes.TestConstants.MII_BASIC_IMAGE_NAME;
import static oracle.weblogic.kubernetes.TestConstants.MII_BASIC_IMAGE_TAG;
import static oracle.weblogic.kubernetes.TestConstants.MII_BASIC_WDT_MODEL_FILE;
import static oracle.weblogic.kubernetes.TestConstants.OLD_DOMAIN_VERSION;
import static oracle.weblogic.kubernetes.TestConstants.RESULTS_ROOT;
import static oracle.weblogic.kubernetes.TestConstants.SKIP_CLEANUP;
import static oracle.weblogic.kubernetes.TestConstants.SSL_PROPERTIES;
import static oracle.weblogic.kubernetes.TestConstants.TEST_IMAGES_REPO_SECRET_NAME;
import static oracle.weblogic.kubernetes.TestConstants.WDT_BASIC_IMAGE_NAME;
import static oracle.weblogic.kubernetes.TestConstants.WDT_BASIC_IMAGE_TAG;
import static oracle.weblogic.kubernetes.TestConstants.WEBLOGIC_IMAGE_TO_USE_IN_SPEC;
import static oracle.weblogic.kubernetes.actions.ActionConstants.ARCHIVE_DIR;
import static oracle.weblogic.kubernetes.actions.ActionConstants.MODEL_DIR;
import static oracle.weblogic.kubernetes.actions.ActionConstants.RESOURCE_DIR;
import static oracle.weblogic.kubernetes.actions.ActionConstants.WORK_DIR;
import static oracle.weblogic.kubernetes.actions.TestActions.createDomainCustomResource;
import static oracle.weblogic.kubernetes.actions.TestActions.getServiceNodePort;
import static oracle.weblogic.kubernetes.actions.TestActions.scaleCluster;
import static oracle.weblogic.kubernetes.assertions.TestAssertions.domainExists;
import static oracle.weblogic.kubernetes.utils.ApplicationUtils.collectAppAvailability;
import static oracle.weblogic.kubernetes.utils.ApplicationUtils.deployAndAccessApplication;
import static oracle.weblogic.kubernetes.utils.ApplicationUtils.verifyAdminConsoleAccessible;
import static oracle.weblogic.kubernetes.utils.AuxiliaryImageUtils.createPushAuxiliaryImageWithDomainConfig;
import static oracle.weblogic.kubernetes.utils.CommonMiiTestUtils.verifyPodsNotRolled;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.checkServiceExists;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.getNextFreePort;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.startPortForwardProcess;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.stopPortForwardProcess;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.testUntil;
import static oracle.weblogic.kubernetes.utils.DomainUtils.verifyDomainStatusConditionTypeDoesNotExist;
import static oracle.weblogic.kubernetes.utils.FileUtils.generateFileFromTemplate;
import static oracle.weblogic.kubernetes.utils.FileUtils.replaceStringInFile;
import static oracle.weblogic.kubernetes.utils.ImageUtils.createBaseRepoSecret;
import static oracle.weblogic.kubernetes.utils.ImageUtils.createTestRepoSecret;
import static oracle.weblogic.kubernetes.utils.PatchDomainUtils.patchServerStartPolicy;
import static oracle.weblogic.kubernetes.utils.PodUtils.checkPodDeleted;
import static oracle.weblogic.kubernetes.utils.PodUtils.checkPodReady;
import static oracle.weblogic.kubernetes.utils.PodUtils.getExternalServicePodName;
import static oracle.weblogic.kubernetes.utils.PodUtils.getPodCreationTime;
import static oracle.weblogic.kubernetes.utils.PodUtils.setPodAntiAffinity;
import static oracle.weblogic.kubernetes.utils.SecretUtils.createSecretWithUsernamePassword;
import static oracle.weblogic.kubernetes.utils.ThreadSafeLogger.getLogger;
import static oracle.weblogic.kubernetes.utils.UpgradeUtils.checkCrdVersion;
import static oracle.weblogic.kubernetes.utils.UpgradeUtils.checkDomainStatus;
import static oracle.weblogic.kubernetes.utils.UpgradeUtils.cleanUpCRD;
import static oracle.weblogic.kubernetes.utils.UpgradeUtils.installOldOperator;
import static oracle.weblogic.kubernetes.utils.UpgradeUtils.upgradeOperatorToCurrent;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Install a released version of Operator from GitHub chart repository.
 * Create a domain using Domain-In-Image or Model-In-Image model with a dynamic cluster.
 * Deploy an application to the cluster in domain and verify the application
 * can be accessed while the operator is upgraded and after the upgrade.
 * Upgrade operator with current Operator image build from current branch.
 * Verify Domain resource version and image are updated.
 * Scale the cluster in upgraded environment.
 * Restart the entire domain in upgraded environment.
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DisplayName("Operator upgrade tests")
@IntegrationTest
@Tag("kind-sequential")
class ItOperatorWlsUpgrade {

  private static LoggingFacade logger = null;
  private String domainUid = "domain1";
  private String adminServerPodName = domainUid + "-admin-server";
  private String managedServerPodNamePrefix = domainUid + "-managed-server";
  private int replicaCount = 2;
  private List<String> namespaces;
  private String latestOperatorImageName;
  private String adminSecretName = "weblogic-credentials";
  private String encryptionSecretName = "encryptionsecret";
  private String opNamespace;
  private String domainNamespace;
  private Path srcDomainYaml = null;
  private Path destDomainYaml = null;
  private static String miiAuxiliaryImageTag = "aux-explict-upgrade";
  private static final String miiAuxiliaryImage = MII_AUXILIARY_IMAGE_NAME + ":" + miiAuxiliaryImageTag;

  /**
   * For each test:
   * Assigns unique namespaces for operator and domain.
   * @param namespaces injected by JUnit
   */
  @BeforeEach
  public void beforeEach(@Namespaces(2) List<String> namespaces) {
    this.namespaces = namespaces;
    assertNotNull(namespaces.get(0), "Namespace is null");
    opNamespace = namespaces.get(0);
    logger.info("Assign a unique namespace for domain");
    assertNotNull(namespaces.get(1), "Namespace is null");
    domainNamespace = namespaces.get(1);
  }

  /**
   * Does some initialization of logger, conditionfactory, etc common
   * to all test methods.
   */
  @BeforeAll
  public static void init() {
    logger = getLogger();
  }

  /**
   * Operator upgrade from 3.3.8 to current.
   */
  @ParameterizedTest
  @DisplayName("Upgrade Operator from 3.3.8 to current")
  @ValueSource(strings = { "Image", "FromModel" })
  void testOperatorWlsUpgradeFrom338ToCurrent(String domainType) {
    logger.info("Starting test testOperatorWlsUpgradeFrom338ToCurrent with domain type {0}", domainType);
    installAndUpgradeOperator(domainType, "3.3.8", OLD_DOMAIN_VERSION, DEFAULT_EXTERNAL_SERVICE_NAME_SUFFIX);
  }

  /**
   * Operator upgrade from 3.4.5 to current.
   */
  @ParameterizedTest
  @DisplayName("Upgrade Operator from 3.4.5 to current")
  @ValueSource(strings = { "Image", "FromModel" })
  void testOperatorWlsUpgradeFrom345ToCurrent(String domainType) {
    logger.info("Starting test testOperatorWlsUpgradeFrom345ToCurrent with domain type {0}", domainType);
    installAndUpgradeOperator(domainType, "3.4.5", OLD_DOMAIN_VERSION, DEFAULT_EXTERNAL_SERVICE_NAME_SUFFIX);
  }

  /**
   * Operator upgrade from 3.4.6 to current.
   */
  @ParameterizedTest
  @DisplayName("Upgrade Operator from 3.4.6 to current")
  @ValueSource(strings = { "Image", "FromModel" })
  void testOperatorWlsUpgradeFrom346ToCurrent(String domainType) {
    logger.info("Starting test testOperatorWlsUpgradeFrom346ToCurrent with domain type {0}", domainType);
    installAndUpgradeOperator(domainType, "3.4.6", OLD_DOMAIN_VERSION, DEFAULT_EXTERNAL_SERVICE_NAME_SUFFIX);
  }

  /**
   * Operator upgrade from 4.0.6 to current.
   */
  @Tag("oke-sequential")
  @ParameterizedTest
  @DisplayName("Upgrade Operator from 4.0.6 to current")
  @ValueSource(strings = { "Image", "FromModel" })
  void testOperatorWlsUpgradeFrom406ToCurrent(String domainType) {
    logger.info("Starting test testOperatorWlsUpgradeFrom406ToCurrent with domain type {0}", domainType);
    installAndUpgradeOperator(domainType, "4.0.6", OLD_DOMAIN_VERSION, DEFAULT_EXTERNAL_SERVICE_NAME_SUFFIX);
  }

  /**
   * Operator upgrade from 4.0.5 to current.
   */
  @Tag("oke-sequential")
  @ParameterizedTest
  @DisplayName("Upgrade Operator from 4.0.5 to current")
  @ValueSource(strings = { "Image", "FromModel" })
  void testOperatorWlsUpgradeFrom405ToCurrent(String domainType) {
    logger.info("Starting test testOperatorWlsUpgradeFrom405ToCurrent with domain type {0}", domainType);
    installAndUpgradeOperator(domainType, "4.0.5", OLD_DOMAIN_VERSION, DEFAULT_EXTERNAL_SERVICE_NAME_SUFFIX);
  }

  /**
   * Auxiliary Image Domain upgrade from Operator 3.4.1 to current.
   */
  @Test
  @DisplayName("Upgrade 3.4.1 Auxiliary Domain(v8 schema) Image to current")
  void testOperatorWlsAuxDomainUpgradeFrom341ToCurrent() {
    logger.info("Starting testOperatorWlsAuxDomainUpgradeFrom341ToCurrent " 
         + "to upgrade Domain with Auxiliary Image with v8 schema to current");
    upgradeWlsAuxDomain("3.4.1");
  }

  /**
   * Auxiliary Image Domain upgrade from Operator 3.4.4 to current.
   */
  @Test
  @DisplayName("Upgrade 3.4.4 Auxiliary Domain(v8 schema) Image to current")
  void testOperatorWlsAuxDomainUpgradeFrom344ToCurrent() {
    logger.info("Starting testOperatorWlsAuxDomainUpgradeFrom344ToCurrent " 
        + "to upgrade Domain with Auxiliary Image with v8 schema to current");
    upgradeWlsAuxDomain("3.4.4");
  }

  /**
   * Auxiliary Image Domain upgrade from Operator v3.3.8 to current.
   * Currently we do not support AuxDomain upgrade 3.3.8 to Latest with 
   * independent webhook only WebLogic Operator in Latest branch.
   * Temporarily disabled, re-enable after webhook not pre-created in 
   * InitializationTasks or the test is moved to a different test suite.
   */
  @Disabled
  @DisplayName("Upgrade 3.3.8 Auxiliary Domain(v8 schema) Image to current")
  void testOperatorWlsAuxDomainUpgradeFrom338ToCurrent() {
    logger.info("Starting test to upgrade Domain with Auxiliary Image with v8 schema to current");
    upgradeWlsAuxDomain("3.3.8");
  }

  /**
   * Cleanup Kubernetes artifacts in the namespaces used by the test and
   * delete CRD.
   */
  @AfterEach
  public void tearDown() {
    if (!SKIP_CLEANUP) {
      CleanupUtil.cleanup(namespaces);
      cleanUpCRD();
    }
  }

  void upgradeWlsAuxDomain(String oldVersion) {
    logger.info("Upgrade version/{0} Auxiliary Domain(v8) to current", oldVersion);
    installOldOperator(oldVersion,opNamespace,domainNamespace);
    createSecrets();

    // Create the repo secret to pull base WebLogic image
    createBaseRepoSecret(domainNamespace);

    // Creating an aux image domain with v8 version
    final String auxiliaryImagePath = "/auxiliary";
    List<String> archiveList = Collections.singletonList(ARCHIVE_DIR + "/" + MII_BASIC_APP_NAME + ".zip");
    List<String> modelList = new ArrayList<>();
    modelList.add(MODEL_DIR + "/" + MII_BASIC_WDT_MODEL_FILE);
    modelList.add(MODEL_DIR + "/model.jms2.yaml");
    logger.info("creating auxiliary image {0}:{1} using imagetool.sh ", miiAuxiliaryImage, MII_BASIC_IMAGE_TAG);
    createPushAuxiliaryImageWithDomainConfig(MII_AUXILIARY_IMAGE_NAME, miiAuxiliaryImageTag, archiveList, modelList);

    // Generate a v8 version of domain.yaml file from a template file
    // by replacing domain namespace, domain uid, base image and aux image
    String auxImage = MII_AUXILIARY_IMAGE_NAME + ":" + miiAuxiliaryImageTag;
    Map<String, String> templateMap  = new HashMap<>();
    templateMap.put("DOMAIN_NS", domainNamespace);
    templateMap.put("DOMAIN_UID", domainUid);
    templateMap.put("AUX_IMAGE", auxImage);
    templateMap.put("BASE_IMAGE", WEBLOGIC_IMAGE_TO_USE_IN_SPEC);
    templateMap.put("API_VERSION", "v8");
    Path srcDomainFile = Paths.get(RESOURCE_DIR,
        "upgrade", "auxilary.single.image.template.yaml");
    Path targetDomainFile = assertDoesNotThrow(
        () -> generateFileFromTemplate(srcDomainFile.toString(),
        "domain.yaml", templateMap));
    logger.info("Generated Domain Resource file {0}", targetDomainFile);

    // run KUBERNETES_CLI to create the domain
    logger.info("Run " + KUBERNETES_CLI + " to create the domain");
    CommandParams params = new CommandParams().defaults();
    params.command(KUBERNETES_CLI + " apply -f "
            + Paths.get(WORK_DIR + "/domain.yaml").toString());
    boolean result = Command.withParams(params).execute();
    assertTrue(result, "Failed to create domain custom resource");

    // wait for the domain to exist
    logger.info("Checking for domain custom resource in namespace {0}", domainNamespace);
    testUntil(
        domainExists(domainUid, "v8", domainNamespace),
        logger,
        "domain {0} to be created in namespace {1}",
        domainUid,
        domainNamespace);
    checkDomainStarted(domainUid, domainNamespace);
    LinkedHashMap<String, OffsetDateTime> pods = new LinkedHashMap<>();
    pods.put(adminServerPodName, getPodCreationTime(domainNamespace, adminServerPodName));
    // get the creation time of the managed server pods before upgrading the operator
    for (int i = 1; i <= replicaCount; i++) {
      pods.put(managedServerPodNamePrefix + i, getPodCreationTime(domainNamespace, managedServerPodNamePrefix + i));
    }
    // verify there is no status condition type Completed
    // before upgrading to Latest
    verifyDomainStatusConditionTypeDoesNotExist(domainUid, domainNamespace,
        DOMAIN_STATUS_CONDITION_COMPLETED_TYPE, OLD_DOMAIN_VERSION);
    upgradeOperatorToCurrent(opNamespace,domainNamespace,domainUid);
    verifyPodsNotRolled(domainNamespace, pods);
    scaleClusterUpAndDown();
  }

  // After upgrade scale up/down the cluster
  private void scaleClusterUpAndDown() {

    String clusterName = domainUid + "-" + "cluster-1";
    logger.info("Updating the cluster {0} replica count to 3", clusterName);
    boolean p1Success = scaleCluster(clusterName, domainNamespace,3);
    assertTrue(p1Success,
        String.format("Patching replica to 3 failed for cluster %s in namespace %s",
            clusterName, domainNamespace));

    logger.info("Updating the cluster {0} replica count to 2");
    p1Success = scaleCluster(clusterName, domainNamespace,2);
    assertTrue(p1Success,
        String.format("Patching replica to 2 failed for cluster %s in namespace %s",
            clusterName, domainNamespace));
  }

  private void installDomainResource(
      String domainType,
      String domainVersion,
      String externalServiceNameSuffix) {

    logger.info("Default Domain API version {0}", DOMAIN_API_VERSION);
    logger.info("Domain API version selected {0}", domainVersion);
    logger.info("Install domain resource for domainUid {0} in namespace {1}",
            domainUid, domainNamespace);

    // create WLS domain and verify
    createWlsDomainAndVerifyByDomainYaml(domainType, domainNamespace, externalServiceNameSuffix);

  }

  // Since Operator version 3.1.0 the service pod prefix has been changed
  // from -external to -ext e.g.
  // domain1-adminserver-ext  NodePort    10.96.46.242   30001:30001/TCP
  private void installAndUpgradeOperator(String domainType,
      String operatorVersion, String domainVersion,
      String externalServiceNameSuffix) {

    installOldOperator(operatorVersion,opNamespace,domainNamespace);

    // create WLS domain and verify
    installDomainResource(domainType, domainVersion, externalServiceNameSuffix);

    // upgrade to current operator
    upgradeOperatorAndVerify(opNamespace, domainNamespace);
  }

  private void upgradeOperatorAndVerify(String opNamespace, String domainNamespace) {
    String opServiceAccount = opNamespace + "-sa";
    String appName = "testwebapp.war";

    // deploy application and access the application once
    // to make sure the app is accessible
    deployAndAccessApplication(domainNamespace,
          domainUid, "cluster-1", "admin-server",
          adminServerPodName, managedServerPodNamePrefix,
          replicaCount, "7001", "8001");

    LinkedHashMap<String, OffsetDateTime> pods = new LinkedHashMap<>();
    pods.put(adminServerPodName, getPodCreationTime(domainNamespace, adminServerPodName));
    // get the creation time of the managed server pods before patching
    for (int i = 1; i <= replicaCount; i++) {
      pods.put(managedServerPodNamePrefix + i, getPodCreationTime(domainNamespace, managedServerPodNamePrefix + i));
    }

    // verify there is no status condition type Completed before upgrading to Latest
    verifyDomainStatusConditionTypeDoesNotExist(domainUid, domainNamespace,
        DOMAIN_STATUS_CONDITION_COMPLETED_TYPE, OLD_DOMAIN_VERSION);

    // start a new thread to collect the availability data of
    // the application while the main thread performs operator upgrade
    List<Integer> appAvailability = new ArrayList<>();
    logger.info("Start a thread to keep track of application availability");
    Thread accountingThread =
          new Thread(
              () -> {
                collectAppAvailability(
                    domainNamespace, opNamespace, appAvailability,
                    adminServerPodName, managedServerPodNamePrefix,
                    replicaCount, "7001", "8001", "testwebapp/index.jsp");
              });
    accountingThread.start();
    try {
      // upgrade to current operator
      upgradeOperatorToCurrent(opNamespace,domainNamespace,domainUid);
      verifyPodsNotRolled(domainNamespace, pods);
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
    scaleClusterUpAndDown();

    // check CRD version is updated
    logger.info("Checking CRD version");
    testUntil(
        checkCrdVersion(),
        logger,
        "the CRD version to be updated to current");

    // check domain status conditions
    checkDomainStatus(domainNamespace,domainUid);

    restartDomain(domainUid, domainNamespace);
  }

  private void createSecrets() {
    // Create the repo secret to pull the domain image
    // this secret is used only for non-kind cluster
    createTestRepoSecret(domainNamespace);

    // create secret for admin credentials
    logger.info("Create secret for admin credentials");
    createSecretWithUsernamePassword(adminSecretName, domainNamespace,
         ADMIN_USERNAME_DEFAULT, ADMIN_PASSWORD_DEFAULT);

    logger.info("Create encryption secret");
    createSecretWithUsernamePassword(encryptionSecretName, domainNamespace,
        ENCRYPION_USERNAME_DEFAULT, ENCRYPION_PASSWORD_DEFAULT);
  }

  private void createWlsDomainAndVerify(String domainType,
        String domainNamespace, String domainVersion,
        String externalServiceNameSuffix) {

    createSecrets();

    String domainImage = "";
    if (domainType.equalsIgnoreCase("Image")) {
      domainImage = WDT_BASIC_IMAGE_NAME + ":" + WDT_BASIC_IMAGE_TAG;
    } else {
      domainImage = MII_BASIC_IMAGE_NAME + ":" + MII_BASIC_IMAGE_TAG;
    }

    // create domain
    createDomainResource(domainNamespace, domainVersion,
                         domainType, domainImage);
    checkDomainStarted(domainUid, domainNamespace);
    logger.info("Getting node port for default channel");
    int serviceNodePort = assertDoesNotThrow(() -> getServiceNodePort(
        domainNamespace, getExternalServicePodName(adminServerPodName, externalServiceNameSuffix), "default"),
        "Getting admin server node port failed");
    logger.info("Validating WebLogic admin server access by login to console");
    verifyAdminConsoleAccessible(domainNamespace, K8S_NODEPORT_HOST,
           String.valueOf(serviceNodePort), false);
  }

  private void checkDomainStarted(String domainUid, String domainNamespace) {
    // verify admin server pod is ready
    checkPodReady(adminServerPodName, domainUid, domainNamespace);

    // verify the admin server service created
    checkServiceExists(adminServerPodName, domainNamespace);

    // verify managed server pods are ready
    for (int i = 1; i <= replicaCount; i++) {
      logger.info("Waiting for managed server pod {0} to be ready in namespace {1}",
          managedServerPodNamePrefix + i, domainNamespace);
      checkPodReady(managedServerPodNamePrefix + i, domainUid, domainNamespace);
    }

    // verify managed server services created
    for (int i = 1; i <= replicaCount; i++) {
      logger.info("Checking managed server service {0} is created in namespace {1}",
          managedServerPodNamePrefix + i, domainNamespace);
      checkServiceExists(managedServerPodNamePrefix + i, domainNamespace);
    }
  }

  private void checkDomainStopped(String domainUid, String domainNamespace) {
    // verify admin server pod is deleted
    checkPodDeleted(adminServerPodName, domainUid, domainNamespace);
    // verify managed server pods are deleted
    for (int i = 1; i <= replicaCount; i++) {
      logger.info("Waiting for managed server pod {0} to be deleted in namespace {1}",
          managedServerPodNamePrefix + i, domainNamespace);
      checkPodDeleted(managedServerPodNamePrefix + i, domainUid, domainNamespace);
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

  /**
   * Restart the domain after upgrade by changing serverStartPolicy.
   */
  private void restartDomain(String domainUid, String domainNamespace) {
    assertTrue(patchServerStartPolicy(domainUid, domainNamespace,
         "/spec/serverStartPolicy", "Never"),
         "Failed to patch Domain's serverStartPolicy to Never");
    logger.info("Domain is patched to shutdown");
    checkDomainStopped(domainUid, domainNamespace);

    assertTrue(patchServerStartPolicy(domainUid, domainNamespace,
         "/spec/serverStartPolicy", "IfNeeded"),
         "Failed to patch Domain's serverStartPolicy to IfNeeded");
    logger.info("Domain is patched to re start");
    checkDomainStarted(domainUid, domainNamespace);
  }

  private void createDomainResource(
      String domainNamespace,
      String domVersion,
      String domainHomeSourceType,
      String domainImage) {

    String domApiVersion = "weblogic.oracle/" + domVersion;
    logger.info("Default Domain API version {0}", DOMAIN_API_VERSION);
    logger.info("Domain API version selected {0}", domApiVersion);
    logger.info("Domain Image name selected {0}", domainImage);
    logger.info("Create domain resource for domainUid {0} in namespace {1}",
            domainUid, domainNamespace);

    // create encryption secret
    logger.info("Create encryption secret");
    String encryptionSecretName = "encryptionsecret";
    createSecretWithUsernamePassword(encryptionSecretName, domainNamespace,
                      "weblogicenc", "weblogicenc");
    DomainResource domain = new DomainResource()
            .apiVersion(domApiVersion)
            .kind("Domain")
            .metadata(new V1ObjectMeta()
                    .name(domainUid)
                    .namespace(domainNamespace))
            .spec(new DomainSpec()
                    .domainUid(domainUid)
                    .domainHomeSourceType(domainHomeSourceType)
                    .image(domainImage)
                    .addImagePullSecretsItem(new V1LocalObjectReference()
                            .name(TEST_IMAGES_REPO_SECRET_NAME))
                    .webLogicCredentialsSecret(new V1LocalObjectReference()
                            .name(adminSecretName))
                    .includeServerOutInPodLog(true)
                    .serverStartPolicy("weblogic.oracle/v8".equals(domApiVersion) ? "IF_NEEDED" : "IfNeeded")
                    .serverPod(new ServerPod()
                            .addEnvItem(new V1EnvVar()
                                    .name("JAVA_OPTIONS")
                                    .value(SSL_PROPERTIES))
                            .addEnvItem(new V1EnvVar()
                                    .name("USER_MEM_ARGS")
                                    .value("-Djava.security.egd=file:/dev/./urandom ")))
                    .adminServer(new AdminServer()
                        .adminService(new AdminService()
                        .addChannelsItem(new Channel()
                        .channelName("default")
                        .nodePort(getNextFreePort()))))
                    .configuration(new Configuration()
                            .model(new Model()
                                .runtimeEncryptionSecret(encryptionSecretName)
                                .domainType("WLS"))
                            .introspectorJobActiveDeadlineSeconds(300L)));
    boolean domCreated = assertDoesNotThrow(() -> createDomainCustomResource(domain, domVersion),
          String.format("Create domain custom resource failed with ApiException for %s in namespace %s",
          domainUid, domainNamespace));
    assertTrue(domCreated,
         String.format("Create domain custom resource failed with ApiException "
             + "for %s in namespace %s", domainUid, domainNamespace));
    setPodAntiAffinity(domain);
    removePortForwardingAttribute(domainNamespace,domainUid);
  }

  // Remove the artifact adminChannelPortForwardingEnabled from domain resource
  // if exist, so that the Operator release default will be effective.
  // e.g. in Release 3.3.x the default is false, but 4.x.x onward it is true
  // However in release(s) lower to 3.3.x, the CRD does not contain this attribute
  // so the patch command to remove this attribute fails. So we do not assert
  // the result of patch command
  // assertTrue(result, "Failed to remove PortForwardingAttribute");
  private void removePortForwardingAttribute(
      String domainNamespace, String  domainUid) {

    StringBuffer patchStr = new StringBuffer("[{");
    patchStr.append("\"op\": \"remove\",")
        .append(" \"path\": \"/spec/adminServer/adminChannelPortForwardingEnabled\"")
        .append("}]");
    logger.info("The patch String {0}", patchStr);
    StringBuffer commandStr = new StringBuffer(KUBERNETES_CLI + " patch domain ");
    commandStr.append(domainUid)
              .append(" -n " + domainNamespace)
              .append(" --type 'json' -p='")
              .append(patchStr)
              .append("'");
    logger.info("The Command String: {0}", commandStr);
    CommandParams params = new CommandParams().defaults();

    params.command(new String(commandStr));
    boolean result = Command.withParams(params).execute();
  }

  private void checkAdminPortForwarding(String domainNamespace, boolean successExpected) {

    logger.info("Checking port forwarding [{0}]", successExpected);
    String forwardPort =
           startPortForwardProcess("localhost", domainNamespace,
           domainUid, 7001);
    assertNotNull(forwardPort, "port-forward fails to assign local port");
    logger.info("Forwarded admin-port is {0}", forwardPort);
    if (successExpected) {
      verifyAdminConsoleAccessible(domainNamespace, "localhost",
           forwardPort, false);
      logger.info("WebLogic console is accessible thru port forwarding");
    } else {
      verifyAdminConsoleAccessible(domainNamespace, "localhost",
           forwardPort, false, false);
      logger.info("WebLogic console shouldn't accessible thru port forwarding");
    }
    stopPortForwardProcess(domainNamespace);
  }

  /**
   * Replace the fields in domain yaml file with testing attributes.
   * For example, namespace, domainUid,  and image. Then create domain using
   * KUBERNETES_CLI and verify the domain is created
   * @param domainType either domain in image(Image) or model in image (FromModel)
   * @param domainNamespace namespace in which to create domain
   * @param externalServiceNameSuffix suffix of externalServiceName
   */
  private void createWlsDomainAndVerifyByDomainYaml(String domainType,
      String domainNamespace, String externalServiceNameSuffix) {

    // Create the repo secret to pull the image
    // this secret is used only for non-kind cluster
    createSecrets();

    // use the checked in domain.yaml to create domain for old releases
    // copy domain.yaml to results dir
    assertDoesNotThrow(() -> Files.createDirectories(
        Paths.get(RESULTS_ROOT + "/" + this.getClass().getSimpleName())),
        String.format("Could not create directory under %s", RESULTS_ROOT));

    if (domainType.equalsIgnoreCase("Image")) {
      logger.info("Domain home in image domain will be created ");
      srcDomainYaml = Paths.get(RESOURCE_DIR, "domain", "domain-v8.yaml");
      destDomainYaml =
        Paths.get(RESULTS_ROOT + "/" + this.getClass().getSimpleName() + "/" + "domain.yaml");
      assertDoesNotThrow(() -> Files.copy(srcDomainYaml, destDomainYaml, REPLACE_EXISTING),
          "File copy failed for domain-v8.yaml");
    } else {
      logger.info("Model in image domain will be created ");
      srcDomainYaml = Paths.get(RESOURCE_DIR, "domain", "mii-domain-v8.yaml");
      destDomainYaml =
        Paths.get(RESULTS_ROOT + "/" + this.getClass().getSimpleName() + "/" + "mii-domain.yaml");
      assertDoesNotThrow(() -> Files.copy(srcDomainYaml, destDomainYaml, REPLACE_EXISTING),
          "File copy failed for mii-domain-v8.yaml");
    }

    // replace namespace, domainUid,  and image in domain.yaml
    assertDoesNotThrow(() -> replaceStringInFile(
        destDomainYaml.toString(), "domain1-ns", domainNamespace),
        "Could not modify the namespace in the domain.yaml file");
    assertDoesNotThrow(() -> replaceStringInFile(
        destDomainYaml.toString(), "domain1", domainUid),
        "Could not modify the domainUid in the domain.yaml file");
    assertDoesNotThrow(() -> replaceStringInFile(
        destDomainYaml.toString(), "domain1-weblogic-credentials", adminSecretName),
        "Could not modify the webLogicCredentialsSecret in the domain.yaml file");
    if (domainType.equalsIgnoreCase("Image")) {
      assertDoesNotThrow(() -> replaceStringInFile(
          destDomainYaml.toString(), "domain-home-in-image:14.1.1.0",
          WDT_BASIC_IMAGE_NAME + ":" + WDT_BASIC_IMAGE_TAG),
          "Could not modify image name in the domain.yaml file");
    } else {
      assertDoesNotThrow(() -> replaceStringInFile(
          destDomainYaml.toString(), "domain1-runtime-encryption-secret", encryptionSecretName),
          "Could not modify runtimeEncryptionSecret in the domain-v8.yaml file");
      assertDoesNotThrow(() -> replaceStringInFile(
          destDomainYaml.toString(), "model-in-image:WLS-v1",
          MII_BASIC_IMAGE_NAME + ":" + MII_BASIC_IMAGE_TAG),
          "Could not modify image name in the mii-domain-v8.yaml file");
    }
    assertTrue(new Command()
        .withParams(new CommandParams()
            .command(KUBERNETES_CLI + " create -f " + destDomainYaml))
        .execute(), KUBERNETES_CLI + " create failed");

    verifyDomain(domainUid, domainNamespace, externalServiceNameSuffix);

  }

  private void verifyDomain(String domainUidString, String domainNamespace, String externalServiceNameSuffix) {

    checkDomainStarted(domainUid, domainNamespace);

    logger.info("Getting node port for default channel");
    int serviceNodePort = assertDoesNotThrow(() -> getServiceNodePort(
        domainNamespace, getExternalServicePodName(adminServerPodName, externalServiceNameSuffix), "default"),
        "Getting admin server node port failed");
    logger.info("Got node port {0} for default channel for domainNameSpace {1}", serviceNodePort, domainNamespace);

    logger.info("Validating WebLogic admin server access by login to console");
    verifyAdminConsoleAccessible(domainNamespace, K8S_NODEPORT_HOST,
           String.valueOf(serviceNodePort), false);

  }

}
