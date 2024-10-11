// Copyright (c) 2020, 2024, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.weblogic.kubernetes;

import java.net.InetAddress;
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

import oracle.weblogic.kubernetes.actions.impl.primitive.Command;
import oracle.weblogic.kubernetes.actions.impl.primitive.CommandParams;
import oracle.weblogic.kubernetes.annotations.IntegrationTest;
import oracle.weblogic.kubernetes.annotations.Namespaces;
import oracle.weblogic.kubernetes.logging.LoggingFacade;
import oracle.weblogic.kubernetes.utils.CleanupUtil;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import static java.nio.file.StandardCopyOption.REPLACE_EXISTING;
import static oracle.weblogic.kubernetes.TestConstants.ADMIN_PASSWORD_DEFAULT;
import static oracle.weblogic.kubernetes.TestConstants.ADMIN_USERNAME_DEFAULT;
import static oracle.weblogic.kubernetes.TestConstants.CLUSTER_VERSION;
import static oracle.weblogic.kubernetes.TestConstants.DEFAULT_EXTERNAL_SERVICE_NAME_SUFFIX;
import static oracle.weblogic.kubernetes.TestConstants.DOMAIN_API_VERSION;
import static oracle.weblogic.kubernetes.TestConstants.DOMAIN_STATUS_CONDITION_COMPLETED_TYPE;
import static oracle.weblogic.kubernetes.TestConstants.DOMAIN_VERSION;
import static oracle.weblogic.kubernetes.TestConstants.ENCRYPION_PASSWORD_DEFAULT;
import static oracle.weblogic.kubernetes.TestConstants.ENCRYPION_USERNAME_DEFAULT;
import static oracle.weblogic.kubernetes.TestConstants.K8S_NODEPORT_HOST;
import static oracle.weblogic.kubernetes.TestConstants.KIND_CLUSTER;
import static oracle.weblogic.kubernetes.TestConstants.KUBERNETES_CLI;
import static oracle.weblogic.kubernetes.TestConstants.MII_AUXILIARY_IMAGE_NAME;
import static oracle.weblogic.kubernetes.TestConstants.MII_BASIC_APP_NAME;
import static oracle.weblogic.kubernetes.TestConstants.MII_BASIC_IMAGE_NAME;
import static oracle.weblogic.kubernetes.TestConstants.MII_BASIC_IMAGE_TAG;
import static oracle.weblogic.kubernetes.TestConstants.MII_BASIC_WDT_MODEL_FILE;
import static oracle.weblogic.kubernetes.TestConstants.OLD_DOMAIN_VERSION;
import static oracle.weblogic.kubernetes.TestConstants.RESULTS_ROOT;
import static oracle.weblogic.kubernetes.TestConstants.SKIP_CLEANUP;
import static oracle.weblogic.kubernetes.TestConstants.TRAEFIK_INGRESS_HTTP_HOSTPORT;
import static oracle.weblogic.kubernetes.TestConstants.WEBLOGIC_IMAGE_TO_USE_IN_SPEC;
import static oracle.weblogic.kubernetes.TestConstants.WLSIMG_BUILDER;
import static oracle.weblogic.kubernetes.TestConstants.WLSIMG_BUILDER_DEFAULT;
import static oracle.weblogic.kubernetes.actions.ActionConstants.ARCHIVE_DIR;
import static oracle.weblogic.kubernetes.actions.ActionConstants.MODEL_DIR;
import static oracle.weblogic.kubernetes.actions.ActionConstants.RESOURCE_DIR;
import static oracle.weblogic.kubernetes.actions.ActionConstants.WORK_DIR;
import static oracle.weblogic.kubernetes.actions.TestActions.getServiceNodePort;
import static oracle.weblogic.kubernetes.actions.TestActions.scaleCluster;
import static oracle.weblogic.kubernetes.assertions.TestAssertions.domainExists;
import static oracle.weblogic.kubernetes.utils.ApplicationUtils.collectAppAvailability;
import static oracle.weblogic.kubernetes.utils.ApplicationUtils.deployAndAccessApplication;
import static oracle.weblogic.kubernetes.utils.ApplicationUtils.verifyAdminConsoleAccessible;
import static oracle.weblogic.kubernetes.utils.ApplicationUtils.verifyAdminServerRESTAccess;
import static oracle.weblogic.kubernetes.utils.AuxiliaryImageUtils.createPushAuxiliaryImageWithDomainConfig;
import static oracle.weblogic.kubernetes.utils.CommonMiiTestUtils.verifyPodsNotRolled;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.checkServiceExists;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.createIngressHostRouting;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.formatIPv6Host;
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
@Tag("kind-upgrade")
class ItOperatorWlsUpgrade {

  private static LoggingFacade logger = null;
  private String domainUid = "domain1";
  private String adminServerName = "admin-server";
  private int adminPort = 7001;
  private String adminServerPodName = domainUid + "-" + adminServerName;
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
  private static String hostHeader;

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
   * Operator upgrade from 4.0.9 to current with Mii domain in V8 schema.
   */
  @Test
  @DisplayName("Upgrade Operator from 4.0.9 to current")
  void testOperatorUpgradeMiiDomainV8From409ToCurrent() {
    logger.info("Starting test testOperatorUpgradeMiiDomainV8From409ToCurrent with Mii domain v8 schema");
    installOperatorCreateMiiDomainAndUpgrade("4.0.9", OLD_DOMAIN_VERSION, DEFAULT_EXTERNAL_SERVICE_NAME_SUFFIX);
  }
  
  /**
   * Upgrade Operator from 4.0.10 to current with Auxiliary image domain, V9 schema.
   */
  @Test
  @DisplayName("Upgrade Operator from 4.0.10 to current")
  void testOperatorUpgradeAuxDomainV9From4010ToCurrent() {
    logger.info("Starting test testOperatorUpgradeAuxDomainV9From4010ToCurrent with Auxiliary domain v9 schema");
    installOperatorCreateAuxDomainAndUpgrade("4.0.10", DOMAIN_VERSION);
  }


  /**
   * Upgrade Operator from 4.1.7 to current with Mii domain in V8 schema.
   */
  @Test
  @DisplayName("Upgrade Operator from 4.1.7 to current")
  void testOperatorUpgradeMiiDomainV8From417ToCurrent() {
    logger.info("Starting test testOperatorUpgradeMiiDomainV8From417ToCurrent with Mii domain v8 schema");
    installOperatorCreateMiiDomainAndUpgrade("4.1.7", OLD_DOMAIN_VERSION, DEFAULT_EXTERNAL_SERVICE_NAME_SUFFIX);
  }

  /**
   * Operator upgrade from 4.1.8 to current with Auxiliary Image Domain, V9 schema.
   */
  @Test
  @DisplayName("Upgrade Operator from 4.1.8 to current")
  void testOperatorUpgradeAuxDomainV9From418ToCurrent() {
    logger.info("Starting test testOperatorUpgradeAuxDomainV9From418ToCurrent to upgrade Auxiliary Domain "
        + "with V9 schema to current");
    installOperatorCreateAuxDomainAndUpgrade("4.1.8", DOMAIN_VERSION);
  }

  /**
   * Operator upgrade from 4.2.7 to current with Auxiliary Image Domain, V9 schema.
   */
  @Test
  @DisplayName("Upgrade Operator from 4.2.7 to current")
  void testOperatorUpgradeAuxDomainV9From427ToCurrent() {
    logger.info("Starting test testOperatorUpgradeAuxDomainV9From427ToCurrent to upgrade Domain with "
        + "Auxiliary Image with v9 schema to current");
    installOperatorCreateAuxDomainAndUpgrade("4.2.7", DOMAIN_VERSION);
  }

  /**
   * Operator upgrade from 4.2.6 to current with Model in Image Domain, V8 schema.
   */
  @Test
  @DisplayName("Upgrade Operator from 4.2.6 to current")
  void testOperatorUpgradeMiiDomainV8From426ToCurrent() {
    logger.info("Starting test testOperatorUpgradeMiiDomainV8From426ToCurrent to upgrade Domain with "
        + "Auxiliary Image with v8 schema to current");
    installOperatorCreateMiiDomainAndUpgrade("4.2.6", OLD_DOMAIN_VERSION, DEFAULT_EXTERNAL_SERVICE_NAME_SUFFIX);
  }

  /**
   * Operator upgrade from 3.4.13 to current with Auxiliary Image Domain, V8 schema.
   * V9 schema is from Operator 4.0.
   */
  @Test
  @DisplayName("Upgrade 3.4.13 Auxiliary Domain(v8 schema) Image to current")
  void testOperatorUpgradeAuxDomainV8From3413ToCurrent() {
    logger.info("Starting testOperatorUpgradeAuxDomainV8From3413ToCurrent "
         + "to upgrade Domain with Auxiliary Image with v8 schema to current");
    installOperatorCreateAuxDomainAndUpgrade("3.4.13", OLD_DOMAIN_VERSION);
  }

  /**
   * Upgrade Operator from 3.4.12 to current with Mii domain in V8 schema.
   */
  @Test
  @DisplayName("Upgrade 3.4.12 Mii Domain(v8 schema) Image to current")
  void testOperatorUpgradeMiiDomainV8From3412ToCurrent() {
    logger.info("Starting testOperatorWlsAuxDomainV8UpgradeFrom3412ToCurrent "
        + "to upgrade MII Domain with v8 schema to current");
    installOperatorCreateMiiDomainAndUpgrade("3.4.12", OLD_DOMAIN_VERSION, DEFAULT_EXTERNAL_SERVICE_NAME_SUFFIX);
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

  void installOperatorCreateAuxDomainAndUpgrade(String operatorVersion, String domainApiVersion) {

    logger.info("Upgrade version/{0} Auxiliary Domain(v8) to current", operatorVersion);
    installOldOperator(operatorVersion, opNamespace, domainNamespace);
    createSecrets();

    // Create the repo secret to pull base WebLogic image
    createBaseRepoSecret(domainNamespace);

    // Creating an aux image domain with v8/v9 version
    final String auxiliaryImagePath = "/auxiliary";
    List<String> archiveList = Collections.singletonList(ARCHIVE_DIR + "/" + MII_BASIC_APP_NAME + ".zip");
    List<String> modelList = new ArrayList<>();
    modelList.add(MODEL_DIR + "/" + MII_BASIC_WDT_MODEL_FILE);
    // modelList.add(MODEL_DIR + "/model.jms2.yaml");
    logger.info("creating auxiliary image {0}:{1} using imagetool.sh ", miiAuxiliaryImage, MII_BASIC_IMAGE_TAG);
    createPushAuxiliaryImageWithDomainConfig(MII_AUXILIARY_IMAGE_NAME, miiAuxiliaryImageTag, archiveList, modelList);

    // Generate a v8/v9 version of domain.yaml file from a template file
    // by replacing domain namespace, domain uid, base image and aux image
    String auxImage = MII_AUXILIARY_IMAGE_NAME + ":" + miiAuxiliaryImageTag;
    String auxImageDomainYaml = "auxilary.single.image.template.yaml";
    Map<String, String> templateMap  = new HashMap<>();
    templateMap.put("DOMAIN_NS", domainNamespace);
    templateMap.put("DOMAIN_UID", domainUid);
    templateMap.put("AUX_IMAGE", auxImage);
    templateMap.put("BASE_IMAGE", WEBLOGIC_IMAGE_TO_USE_IN_SPEC);
    templateMap.put("API_VERSION", domainApiVersion);
    templateMap.put("CLUSTER_VERSION", CLUSTER_VERSION);
    if (domainApiVersion.equals(DOMAIN_VERSION)) {
      auxImageDomainYaml =  "auxiliary.single.image.v9.template.yaml";
    }
    Path srcDomainFile = Paths.get(RESOURCE_DIR,
        "upgrade", auxImageDomainYaml);
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
        domainExists(domainUid, domainApiVersion, domainNamespace),
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
        DOMAIN_STATUS_CONDITION_COMPLETED_TYPE, domainApiVersion);
    upgradeOperatorToCurrent(opNamespace);
    checkDomainStatus(domainNamespace,domainUid);
    verifyPodsNotRolled(domainNamespace, pods);
    scaleClusterUpAndDown(domainApiVersion);
  }

  // After upgrade scale up/down the cluster
  private void scaleClusterUpAndDown(String domainApiVersion) {

    String clusterName = domainUid + "-" + "cluster-1";
    if (domainApiVersion.equals(DOMAIN_VERSION)) {
      clusterName = "cluster-1";
    }
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

  private void installMiiDomainResource(
      String domainVersion,
      String externalServiceNameSuffix) {

    logger.info("Default Domain API version {0}", DOMAIN_API_VERSION);
    logger.info("Domain API version selected {0}", domainVersion);
    logger.info("Install domain resource for domainUid {0} in namespace {1}",
            domainUid, domainNamespace);

    // create WLS domain and verify
    createWlsMiiDomainByDomainYamlAndVerify(domainNamespace, externalServiceNameSuffix, domainVersion);

  }

  // Since Operator version 3.1.0 the service pod prefix has been changed
  // from -external to -ext e.g.
  // domain1-adminserver-ext  NodePort    10.96.46.242   30001:30001/TCP
  private void installOperatorCreateMiiDomainAndUpgrade(String operatorVersion, String domainVersion,
                                                        String externalServiceNameSuffix) {

    installOldOperator(operatorVersion,opNamespace,domainNamespace);
    // create WLS domain and verify
    installMiiDomainResource(domainVersion, externalServiceNameSuffix);

    // upgrade to current operator
    upgradeOperatorAndVerify(opNamespace, domainNamespace, domainVersion);
  }

  private void upgradeOperatorAndVerify(String opNamespace, String domainNamespace, String domainVersion) {
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
      upgradeOperatorToCurrent(opNamespace);
      checkDomainStatus(domainNamespace,domainUid);
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
    scaleClusterUpAndDown(domainVersion);

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

  /**
   * Replace the fields in domain yaml file with testing attributes.
   * For example, namespace, domainUid,  and image. Then create domain using
   * KUBERNETES_CLI and verify the domain is created
   *
   * @param domainNamespace           namespace in which to create domain
   * @param externalServiceNameSuffix suffix of externalServiceName
   * @param domainVersion             version of the domain
   */
  private void createWlsMiiDomainByDomainYamlAndVerify(String domainNamespace,
                                                       String externalServiceNameSuffix, String domainVersion) {

    // Create the repo secret to pull the image
    // this secret is used only for non-kind cluster
    createSecrets();

    // use the checked in domain.yaml to create domain for old releases
    // copy domain.yaml to results dir
    assertDoesNotThrow(() -> Files.createDirectories(
        Paths.get(RESULTS_ROOT + "/" + this.getClass().getSimpleName())),
        String.format("Could not create directory under %s", RESULTS_ROOT));

    if (domainVersion.equals(DOMAIN_VERSION)) {
      logger.info("Model in image domain using " + DOMAIN_VERSION + " will be created ");
      srcDomainYaml = Paths.get(RESOURCE_DIR, "domain", "mii-cluster-domain.yaml");
      destDomainYaml =
        Paths.get(RESULTS_ROOT + "/" + this.getClass().getSimpleName() + "/" + "domain.yaml");
      assertDoesNotThrow(() -> Files.copy(srcDomainYaml, destDomainYaml, REPLACE_EXISTING),
          "File copy failed for mii-cluster-domain.yaml");
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
    assertDoesNotThrow(() -> replaceStringInFile(
        destDomainYaml.toString(), "domain1-runtime-encryption-secret", encryptionSecretName),
        "Could not modify runtimeEncryptionSecret in the domain.yaml file");
    assertDoesNotThrow(() -> replaceStringInFile(
        destDomainYaml.toString(), "model-in-image:WLS-v1",
        MII_BASIC_IMAGE_NAME + ":" + MII_BASIC_IMAGE_TAG),
        "Could not modify image name in the domain.yaml file");

    boolean result = Command
        .withParams(new CommandParams()
            .command(KUBERNETES_CLI + " create -f " + destDomainYaml))
        .execute();
    assertTrue(result, KUBERNETES_CLI + " create failed");

    verifyDomain(domainUid, domainNamespace, externalServiceNameSuffix);

  }

  private void verifyDomain(String domainUidString, String domainNamespace, String externalServiceNameSuffix) {

    checkDomainStarted(domainUid, domainNamespace);
    
    if (KIND_CLUSTER && !WLSIMG_BUILDER.equals(WLSIMG_BUILDER_DEFAULT)) {
      hostHeader = createIngressHostRouting(domainNamespace, domainUidString, adminServerName, adminPort);
      assertDoesNotThrow(() -> verifyAdminServerRESTAccess(formatIPv6Host(InetAddress.getLocalHost().getHostAddress()),
          TRAEFIK_INGRESS_HTTP_HOSTPORT, false, hostHeader));
    }

    if (WLSIMG_BUILDER.equals(WLSIMG_BUILDER_DEFAULT)) {
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

}
