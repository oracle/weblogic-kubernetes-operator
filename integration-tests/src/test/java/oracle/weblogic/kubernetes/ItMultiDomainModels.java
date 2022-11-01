// Copyright (c) 2022, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.weblogic.kubernetes;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import oracle.weblogic.domain.Domain;
import oracle.weblogic.kubernetes.actions.impl.primitive.Command;
import oracle.weblogic.kubernetes.actions.impl.primitive.CommandParams;
import oracle.weblogic.kubernetes.annotations.DisabledOnSlimImage;
import oracle.weblogic.kubernetes.annotations.IntegrationTest;
import oracle.weblogic.kubernetes.annotations.Namespaces;
import oracle.weblogic.kubernetes.logging.LoggingFacade;
import org.apache.commons.io.FileUtils;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static oracle.weblogic.kubernetes.TestConstants.ADMIN_PASSWORD_DEFAULT;
import static oracle.weblogic.kubernetes.TestConstants.ADMIN_SERVER_NAME_BASE;
import static oracle.weblogic.kubernetes.TestConstants.ADMIN_USERNAME_DEFAULT;
import static oracle.weblogic.kubernetes.TestConstants.BUSYBOX_IMAGE;
import static oracle.weblogic.kubernetes.TestConstants.BUSYBOX_TAG;
import static oracle.weblogic.kubernetes.TestConstants.DOMAIN_IMAGES_REPO;
import static oracle.weblogic.kubernetes.TestConstants.MANAGED_SERVER_NAME_BASE;
import static oracle.weblogic.kubernetes.TestConstants.MII_AUXILIARY_IMAGE_NAME;
import static oracle.weblogic.kubernetes.TestConstants.MII_BASIC_APP_NAME;
import static oracle.weblogic.kubernetes.TestConstants.MII_BASIC_IMAGE_NAME;
import static oracle.weblogic.kubernetes.TestConstants.MII_BASIC_IMAGE_TAG;
import static oracle.weblogic.kubernetes.TestConstants.MII_BASIC_WDT_MODEL_FILE;
import static oracle.weblogic.kubernetes.TestConstants.RESULTS_ROOT;
import static oracle.weblogic.kubernetes.TestConstants.WEBLOGIC_IMAGE_TO_USE_IN_SPEC;
import static oracle.weblogic.kubernetes.actions.ActionConstants.ARCHIVE_DIR;
import static oracle.weblogic.kubernetes.actions.ActionConstants.MODEL_DIR;
import static oracle.weblogic.kubernetes.actions.ActionConstants.RESOURCE_DIR;
import static oracle.weblogic.kubernetes.actions.ActionConstants.WORK_DIR;
import static oracle.weblogic.kubernetes.actions.TestActions.buildAppArchive;
import static oracle.weblogic.kubernetes.actions.TestActions.defaultAppParams;
import static oracle.weblogic.kubernetes.actions.TestActions.getServiceNodePort;
import static oracle.weblogic.kubernetes.assertions.TestAssertions.adminNodePortAccessible;
import static oracle.weblogic.kubernetes.utils.CommonMiiTestUtils.createDomainResource;
import static oracle.weblogic.kubernetes.utils.CommonMiiTestUtils.createMiiDomainAndVerify;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.scaleAndVerifyCluster;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.testUntil;
import static oracle.weblogic.kubernetes.utils.DomainUtils.createAndVerifyDomainInImageUsingWdt;
import static oracle.weblogic.kubernetes.utils.DomainUtils.createDomainAndVerify;
import static oracle.weblogic.kubernetes.utils.DomainUtils.createDomainOnPvUsingWdt;
import static oracle.weblogic.kubernetes.utils.DomainUtils.shutdownDomainAndVerify;
import static oracle.weblogic.kubernetes.utils.FileUtils.replaceStringInFile;
import static oracle.weblogic.kubernetes.utils.FileUtils.unzipWDTInstallationFile;
import static oracle.weblogic.kubernetes.utils.ImageUtils.dockerLoginAndPushImageToRegistry;
import static oracle.weblogic.kubernetes.utils.OKDUtils.createRouteForOKD;
import static oracle.weblogic.kubernetes.utils.OperatorUtils.installAndVerifyOperator;
import static oracle.weblogic.kubernetes.utils.PodUtils.getExternalServicePodName;
import static oracle.weblogic.kubernetes.utils.SecretUtils.createSecretWithUsernamePassword;
import static oracle.weblogic.kubernetes.utils.SecretUtils.createSecretsForImageRepos;
import static oracle.weblogic.kubernetes.utils.ThreadSafeLogger.getLogger;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The test class creates WebLogic domains with three models.
 * domain-on-pv ( using WDT )
 * domain-in-image ( using WDT )
 * model-in-image
 * Verify the basic lifecycle operations of the WebLogic server pods by scaling the domain.
 * Also verify admin console login using admin node port.
 */
@DisplayName("Verify the basic lifecycle operations of the WebLogic server pods by scaling the clusters in the domain"
    + " with different domain types and verify admin console login using admin node port.")
@IntegrationTest
class ItMultiDomainModels {

  // domain constants
  private static final String clusterName = "cluster-1";
  private static final int replicaCount = 2;
  private static final String wlSecretName = "weblogic-credentials";
  private static final String miiDomainUid = "miidomain";
  private static final String dimDomainUid = "domaininimage";
  private static final String dpvDomainUid = "domainonpv";
  private static final String wdtModelFileForDomainInImage = "wdt-singlecluster-sampleapp-usingprop-wls.yaml";

  private static LoggingFacade logger = null;
  private static String miiDomainNamespace = null;
  private static String domainOnPVNamespace = null;
  private static String domainInImageNamespace = null;
  private static String auxiliaryImageDomainNamespace = null;

  /**
   * Install operator.
   * Create four different type of domains: model in image, domain on PV, domain in image and
   * domain with auxiliary image.
   *
   * @param namespaces list of namespaces created by the IntegrationTestWatcher by the
   *                   JUnit engine parameter resolution mechanism
   */
  @BeforeAll
  public static void initAll(@Namespaces(5) List<String> namespaces) {
    logger = getLogger();

    // get a unique operator namespace
    logger.info("Getting a unique namespace for operator");
    assertNotNull(namespaces.get(0), "Namespace list is null");
    String opNamespace = namespaces.get(0);

    // get unique namespaces for four different type of domains
    logger.info("Getting unique namespaces for four different type of domains");
    assertNotNull(namespaces.get(1));
    miiDomainNamespace = namespaces.get(1);
    assertNotNull(namespaces.get(2));
    domainOnPVNamespace = namespaces.get(2);
    assertNotNull(namespaces.get(3));
    domainInImageNamespace = namespaces.get(3);
    assertNotNull(namespaces.get(4));
    auxiliaryImageDomainNamespace = namespaces.get(4);

    // set the service account name for the operator
    String opServiceAccount = opNamespace + "-sa";

    // install and verify operator
    installAndVerifyOperator(opNamespace, opServiceAccount, false, 0,
        miiDomainNamespace, domainOnPVNamespace, domainInImageNamespace, auxiliaryImageDomainNamespace);
  }

  /**
   * Scale the cluster by patching domain resource for four different
   * type of domains i.e. domain-on-pv, domain-in-image, model-in-image and domain-with-auxiliary-image
   * Also verify admin console login using admin node port.
   * @param domainType domain type, possible value: modelInImage, domainInImage, domainOnPV, auxiliaryImageDomain
   */
  @ParameterizedTest
  @DisplayName("scale cluster by patching domain resource with four different type of domains and "
      + "verify admin console login using admin node port.")
  @ValueSource(strings = {"modelInImage", "domainInImage", "domainOnPV", "auxiliaryImageDomain"})
  @Tag("gate")
  @Tag("crio")
  @DisabledOnSlimImage
  void testScaleClustersAndAdminConsoleLogin(String domainType) {
    Domain domain = createDomainBasedOnDomainType(domainType);

    // get the domain properties
    String domainUid = domain.getSpec().getDomainUid();
    String domainNamespace = domain.getMetadata().getNamespace();

    String managedServerPodNamePrefix = domainUid + "-" + MANAGED_SERVER_NAME_BASE;

    String adminServerPodName = domainUid + "-" + ADMIN_SERVER_NAME_BASE;
    logger.info("Getting node port for default channel");
    int serviceNodePort = assertDoesNotThrow(() -> getServiceNodePort(
        domainNamespace, getExternalServicePodName(adminServerPodName), "default"),
        "Getting admin server node port failed");

    // In OKD cluster, we need to get the routeHost for the external admin service
    String routeHost = createRouteForOKD(getExternalServicePodName(adminServerPodName), domainNamespace);

    int numberOfServers = 3;
    logger.info("Scaling cluster {0} of domain {1} in namespace {2} to {3} servers.",
        clusterName, domainUid, domainNamespace, numberOfServers);
    List<String> managedServersBeforeScale = listManagedServersBeforeScale(replicaCount);
    scaleAndVerifyCluster(clusterName, domainUid, domainNamespace, managedServerPodNamePrefix,
        replicaCount, numberOfServers, null, managedServersBeforeScale);

    // then scale cluster back to 2 servers
    logger.info("Scaling cluster {0} of domain {1} in namespace {2} from {3} servers to {4} servers.",
        clusterName, domainUid, domainNamespace, numberOfServers, replicaCount);
    managedServersBeforeScale = listManagedServersBeforeScale(numberOfServers);
    scaleAndVerifyCluster(clusterName, domainUid, domainNamespace, managedServerPodNamePrefix,
        numberOfServers, replicaCount, null, managedServersBeforeScale);

    logger.info("Validating WebLogic admin server access by login to console");
    testUntil(assertDoesNotThrow(
        () -> adminNodePortAccessible(serviceNodePort, ADMIN_USERNAME_DEFAULT, ADMIN_PASSWORD_DEFAULT, routeHost),
        "Access to admin server node port failed"),
        logger,
        "Console login validation");

    // shutdown domain and verify the domain is shutdown
    shutdownDomainAndVerify(domainNamespace, domainUid, replicaCount);
  }

  /**
   * Generate a server list which contains all managed servers in the cluster before scale.
   *
   * @param replicasBeforeScale the replicas of WebLogic cluster before scale
   * @return list of managed servers in the cluster before scale
   */
  private List<String> listManagedServersBeforeScale(int replicasBeforeScale) {

    List<String> managedServerNames = new ArrayList<>();
    for (int i = 1; i <= replicasBeforeScale; i++) {
      managedServerNames.add(MANAGED_SERVER_NAME_BASE + i);
    }

    return managedServerNames;
  }

  /**
   * Assert the specified domain and domain spec, metadata and clusters not null.
   * @param domain oracle.weblogic.domain.Domain object
   */
  private void assertDomainNotNull(Domain domain) {
    assertNotNull(domain, "domain is null");
    assertNotNull(domain.getSpec(), domain + " spec is null");
    assertNotNull(domain.getMetadata(), domain + " metadata is null");
    assertNotNull(domain.getSpec().getClusters(), domain.getSpec() + " getClusters() is null");
  }

  private Domain createDomainBasedOnDomainType(String domainType) {
    Domain domain;

    if (domainType.equalsIgnoreCase("modelInImage")) {
      domain = createMiiDomainAndVerify(miiDomainNamespace, miiDomainUid,
          MII_BASIC_IMAGE_NAME + ":" + MII_BASIC_IMAGE_TAG,
          miiDomainUid + "-" + ADMIN_SERVER_NAME_BASE,
          miiDomainUid + "-" + MANAGED_SERVER_NAME_BASE, replicaCount);
    } else if (domainType.equalsIgnoreCase("domainInImage")) {
      List<String> appSrcDirList = new ArrayList<>();
      appSrcDirList.add(MII_BASIC_APP_NAME);
      domain = createAndVerifyDomainInImageUsingWdt(dimDomainUid, domainInImageNamespace,
          wdtModelFileForDomainInImage, appSrcDirList, wlSecretName, clusterName, replicaCount);
    } else if (domainType.equalsIgnoreCase("domainOnPV")) {
      domain = createDomainOnPvUsingWdt(dpvDomainUid, domainOnPVNamespace, wlSecretName,
          clusterName, replicaCount, ItMultiDomainModels.class.getSimpleName());
    } else {
      domain = createDomainUsingAuxiliaryImage();
    }

    assertDomainNotNull(domain);
    return domain;
  }

  private Domain createDomainUsingAuxiliaryImage() {

    String domainUid = "auxiliaryimagedomain";
    String adminServerPodName = domainUid + "-admin-server";
    String managedServerPrefix = domainUid + "-managed-server";

    // admin/managed server name here should match with model yaml
    final String auxiliaryImageVolumeName = "auxiliaryImageVolume1";
    final String auxiliaryImagePath = "/auxiliary";

    // create secret for admin credentials
    logger.info("Create secret for admin credentials");
    String adminSecretName = "weblogic-credentials";
    createSecretWithUsernamePassword(adminSecretName, auxiliaryImageDomainNamespace,
        ADMIN_USERNAME_DEFAULT, ADMIN_PASSWORD_DEFAULT);

    // create encryption secret
    logger.info("Create encryption secret");
    String encryptionSecretName = "encryptionsecret";
    createSecretWithUsernamePassword(encryptionSecretName, auxiliaryImageDomainNamespace,
        "weblogicenc", "weblogicenc");

    // create stage dir for the auxiliary image
    Path auxiliaryImageDir = Paths.get(RESULTS_ROOT, ItMultiDomainModels.class.getSimpleName(), "auxiliaryimage");
    assertDoesNotThrow(() -> FileUtils.deleteDirectory(auxiliaryImageDir.toFile()),
        "Delete directory failed");
    assertDoesNotThrow(() -> Files.createDirectories(auxiliaryImageDir),
        "Create directory failed");

    // create models dir and copy model, archive files if any for image1
    Path modelsPath1 = Paths.get(auxiliaryImageDir.toString(), "models");
    assertDoesNotThrow(() -> Files.createDirectories(modelsPath1));
    assertDoesNotThrow(() -> Files.copy(
        Paths.get(MODEL_DIR, MII_BASIC_WDT_MODEL_FILE),
        Paths.get(modelsPath1.toString(), MII_BASIC_WDT_MODEL_FILE),
        StandardCopyOption.REPLACE_EXISTING));

    // build app
    assertTrue(buildAppArchive(defaultAppParams()
            .srcDirList(Collections.singletonList(MII_BASIC_APP_NAME))
            .appName(MII_BASIC_APP_NAME)),
        String.format("Failed to create app archive for %s", MII_BASIC_APP_NAME));

    // copy app archive to models
    assertDoesNotThrow(() -> Files.copy(
        Paths.get(ARCHIVE_DIR, MII_BASIC_APP_NAME + ".zip"),
        Paths.get(modelsPath1.toString(), MII_BASIC_APP_NAME + ".zip"),
        StandardCopyOption.REPLACE_EXISTING));

    // unzip WDT installation file into work dir
    unzipWDTInstallationFile(auxiliaryImageDir.toString());

    // create image1 with model and wdt installation files
    String miiAuxiliaryImage1 = MII_AUXILIARY_IMAGE_NAME + ":" + MII_BASIC_IMAGE_TAG + "mdmauxi";
    createAuxiliaryImage(auxiliaryImageDir.toString(),
        Paths.get(RESOURCE_DIR, "auxiliaryimage", "Dockerfile").toString(), miiAuxiliaryImage1);

    // push image1 to repo for multi node cluster
    if (!DOMAIN_IMAGES_REPO.isEmpty()) {
      logger.info("docker push image {0} to registry {1}", miiAuxiliaryImage1, DOMAIN_IMAGES_REPO);
      dockerLoginAndPushImageToRegistry(miiAuxiliaryImage1);
    }

    // create domain custom resource using the auxiliary image
    logger.info("Creating domain custom resource with domainUid {0} and auxiliary images {1} {2}",
        domainUid, miiAuxiliaryImage1);
    Domain domainCR = createDomainResource(domainUid, auxiliaryImageDomainNamespace,
        WEBLOGIC_IMAGE_TO_USE_IN_SPEC, adminSecretName, createSecretsForImageRepos(auxiliaryImageDomainNamespace),
        encryptionSecretName, replicaCount, "cluster-1", auxiliaryImagePath,
        auxiliaryImageVolumeName, miiAuxiliaryImage1);

    // create domain and verify its running
    logger.info("Creating domain {0} with auxiliary image {1} in namespace {2}",
        domainUid, miiAuxiliaryImage1, auxiliaryImageDomainNamespace);
    createDomainAndVerify(domainUid, domainCR, auxiliaryImageDomainNamespace,
        adminServerPodName, managedServerPrefix, replicaCount);

    return domainCR;
  }

  private void createAuxiliaryImage(String stageDirPath, String dockerFileLocation, String auxiliaryImage) {
    //replace the BUSYBOX_IMAGE and BUSYBOX_TAG in Dockerfile
    Path dockerDestFile = Paths.get(WORK_DIR, "auximages", "Dockerfile");
    assertDoesNotThrow(() -> {
      Files.createDirectories(dockerDestFile.getParent());
      Files.copy(Paths.get(dockerFileLocation),
          dockerDestFile, StandardCopyOption.REPLACE_EXISTING);
      replaceStringInFile(dockerDestFile.toString(), "BUSYBOX_IMAGE", BUSYBOX_IMAGE);
      replaceStringInFile(dockerDestFile.toString(), "BUSYBOX_TAG", BUSYBOX_TAG);
    });

    String cmdToExecute = String.format("cd %s && docker build -f %s %s -t %s .",
        stageDirPath, dockerDestFile.toString(),
        "--build-arg AUXILIARY_IMAGE_PATH=/auxiliary", auxiliaryImage);
    assertTrue(Command.withParams(new CommandParams().command(cmdToExecute)).execute(),
        String.format("Failed to execute command %s", cmdToExecute));
  }

}
