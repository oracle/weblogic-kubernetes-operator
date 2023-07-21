// Copyright (c) 2022, 2023, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.weblogic.kubernetes;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import oracle.weblogic.domain.AuxiliaryImage;
import oracle.weblogic.domain.DomainResource;
import oracle.weblogic.kubernetes.actions.impl.primitive.Command;
import oracle.weblogic.kubernetes.actions.impl.primitive.CommandParams;
import oracle.weblogic.kubernetes.actions.impl.primitive.WitParams;
import oracle.weblogic.kubernetes.annotations.IntegrationTest;
import oracle.weblogic.kubernetes.annotations.Namespaces;
import oracle.weblogic.kubernetes.logging.LoggingFacade;
import oracle.weblogic.kubernetes.utils.CommonMiiTestUtils;
import oracle.weblogic.kubernetes.utils.ExecResult;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static oracle.weblogic.kubernetes.TestConstants.ADMIN_PASSWORD_DEFAULT;
import static oracle.weblogic.kubernetes.TestConstants.ADMIN_USERNAME_DEFAULT;
import static oracle.weblogic.kubernetes.TestConstants.BUSYBOX_IMAGE;
import static oracle.weblogic.kubernetes.TestConstants.BUSYBOX_TAG;
import static oracle.weblogic.kubernetes.TestConstants.DOMAIN_IMAGES_REPO;
import static oracle.weblogic.kubernetes.TestConstants.ENCRYPION_PASSWORD_DEFAULT;
import static oracle.weblogic.kubernetes.TestConstants.ENCRYPION_USERNAME_DEFAULT;
import static oracle.weblogic.kubernetes.TestConstants.IMAGE_PULL_POLICY;
import static oracle.weblogic.kubernetes.TestConstants.MII_APP_RESPONSE_V1;
import static oracle.weblogic.kubernetes.TestConstants.MII_AUXILIARY_IMAGE_NAME;
import static oracle.weblogic.kubernetes.TestConstants.MII_BASIC_APP_NAME;
import static oracle.weblogic.kubernetes.TestConstants.MII_BASIC_IMAGE_TAG;
import static oracle.weblogic.kubernetes.TestConstants.MII_BASIC_WDT_MODEL_FILE;
import static oracle.weblogic.kubernetes.TestConstants.ORACLELINUX_TEST_VERSION;
import static oracle.weblogic.kubernetes.TestConstants.SKIP_CLEANUP;
import static oracle.weblogic.kubernetes.TestConstants.WDT_TEST_VERSION;
import static oracle.weblogic.kubernetes.TestConstants.WEBLOGIC_IMAGE_TO_USE_IN_SPEC;
import static oracle.weblogic.kubernetes.TestConstants.WLSIMG_BUILDER;
import static oracle.weblogic.kubernetes.actions.ActionConstants.ARCHIVE_DIR;
import static oracle.weblogic.kubernetes.actions.ActionConstants.MODEL_DIR;
import static oracle.weblogic.kubernetes.actions.TestActions.buildAppArchive;
import static oracle.weblogic.kubernetes.actions.TestActions.defaultAppParams;
import static oracle.weblogic.kubernetes.actions.TestActions.deleteImage;
import static oracle.weblogic.kubernetes.assertions.TestAssertions.appAccessibleInPod;
import static oracle.weblogic.kubernetes.assertions.TestAssertions.imageExists;
import static oracle.weblogic.kubernetes.utils.AuxiliaryImageUtils.checkWDTVersion;
import static oracle.weblogic.kubernetes.utils.AuxiliaryImageUtils.createAuxImageUsingWITAndReturnResult;
import static oracle.weblogic.kubernetes.utils.AuxiliaryImageUtils.createAuxiliaryImage;
import static oracle.weblogic.kubernetes.utils.ClusterUtils.createClusterResourceAndAddReferenceToDomain;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.testUntil;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.verifyConfiguredSystemResouceByPath;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.verifyConfiguredSystemResource;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.withStandardRetryPolicy;
import static oracle.weblogic.kubernetes.utils.DomainUtils.createDomainAndVerify;
import static oracle.weblogic.kubernetes.utils.ImageUtils.imageRepoLoginAndPushImageToRegistry;
import static oracle.weblogic.kubernetes.utils.OKDUtils.createRouteForOKD;
import static oracle.weblogic.kubernetes.utils.OperatorUtils.installAndVerifyOperator;
import static oracle.weblogic.kubernetes.utils.PodUtils.getExternalServicePodName;
import static oracle.weblogic.kubernetes.utils.SecretUtils.createSecretWithUsernamePassword;
import static oracle.weblogic.kubernetes.utils.SecretUtils.createSecretsForImageRepos;
import static oracle.weblogic.kubernetes.utils.ThreadSafeLogger.getLogger;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("Test to create model in image domain using auxiliary image with new createAuxImage command")
@IntegrationTest
@Tag("kind-parallel")
@Tag("toolkits-srg")
@Tag("okd-wls-srg")
@Tag("olcne")
@Tag("oke-gate")
class ItMiiCreateAuxImageWithImageTool {

  private static String opNamespace = null;
  private static String domainNamespace = null;

  private static LoggingFacade logger = null;
  private String domain1Uid = "domain1";
  private String domain2Uid = "domain2";
  private static String miiImageTag = "new" + MII_BASIC_IMAGE_TAG;
  private static String miiImage = MII_AUXILIARY_IMAGE_NAME + ":" + miiImageTag;
  private final int replicaCount = 1;
  private static String adminSecretName;
  private static String encryptionSecretName;

  /**
   * Install Operator.
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

    logger.info("Creating unique namespace for domain");
    assertNotNull(namespaces.get(1), "Namespace list is null");
    domainNamespace = namespaces.get(1);

    // install and verify operator
    installAndVerifyOperator(opNamespace, domainNamespace);

    // create secret for admin credentials
    logger.info("Create secret for admin credentials");
    adminSecretName = "weblogic-credentials";
    createSecretWithUsernamePassword(adminSecretName, domainNamespace, ADMIN_USERNAME_DEFAULT, ADMIN_PASSWORD_DEFAULT);

    // create encryption secret
    logger.info("Create encryption secret");
    encryptionSecretName = "encryptionsecret";
    createSecretWithUsernamePassword(encryptionSecretName, domainNamespace,
        ENCRYPION_USERNAME_DEFAULT, ENCRYPION_PASSWORD_DEFAULT);

    // build app
    assertTrue(buildAppArchive(defaultAppParams()
            .srcDirList(Collections.singletonList(MII_BASIC_APP_NAME))
            .appName(MII_BASIC_APP_NAME)),
        String.format("Failed to create app archive for %s", MII_BASIC_APP_NAME));
  }

  /**
   * Create a domain using auxiliary images. Create auxiliary image using default options.
   * Verify the domain is running and JMS resource is added.
   */
  @Test
  @DisplayName("Test to create domain using createAuxImage with default options")
  void testCreateDomainUsingAuxImageDefaultOptions() {

    // admin/managed server name here should match with model yaml
    final String auxiliaryImagePath = "/auxiliary";
    String miiAuxiliaryImage = miiImage + "1";
    String miiAuxiliaryImageTag = miiImageTag + "1";
    List<String> archiveList = Collections.singletonList(ARCHIVE_DIR + "/" + MII_BASIC_APP_NAME + ".zip");

    List<String> modelList = new ArrayList<>();
    modelList.add(MODEL_DIR + "/" + MII_BASIC_WDT_MODEL_FILE);
    modelList.add(MODEL_DIR + "/model.jms2.yaml");

    // create auxiliary image using imagetool command if does not exists
    if (imageExists(MII_AUXILIARY_IMAGE_NAME, miiAuxiliaryImageTag)) {
      deleteImage(miiAuxiliaryImage);
    }
    logger.info("creating auxiliary image {0} using imagetool.sh ", miiAuxiliaryImage);
    testUntil(
        withStandardRetryPolicy,
        createAuxiliaryImage(MII_AUXILIARY_IMAGE_NAME, miiAuxiliaryImageTag, modelList, archiveList),
        logger,
        "createAuxImage to be successful");


    // push auxiliary image to repo for multi node cluster
    logger.info(WLSIMG_BUILDER + " push image {0} to registry {2}", miiAuxiliaryImage,
        DOMAIN_IMAGES_REPO);
    imageRepoLoginAndPushImageToRegistry(miiAuxiliaryImage);

    // create domain custom resource using auxiliary image
    logger.info("Creating domain custom resource with domainUid {0} and auxiliary image {1}",
        domain1Uid, miiAuxiliaryImage);
    DomainResource domainCR = CommonMiiTestUtils.createDomainResourceWithAuxiliaryImage(domain1Uid, domainNamespace,
        WEBLOGIC_IMAGE_TO_USE_IN_SPEC, adminSecretName, createSecretsForImageRepos(domainNamespace),
        encryptionSecretName, auxiliaryImagePath,
        miiAuxiliaryImage);

    // create domain and verify its running
    logger.info("Creating domain {0} with auxiliary image {1} in namespace {2}",
        domain1Uid, miiAuxiliaryImage, domainNamespace);
    String adminServerPodName = domain1Uid + "-admin-server";
    String managedServerPrefix = domain1Uid + "-managed-server";

    createDomainAndVerify(domain1Uid, domainCR, domainNamespace, adminServerPodName, managedServerPrefix, replicaCount);

    //create router for admin service on OKD
    String adminSvcExtHost = createRouteForOKD(getExternalServicePodName(adminServerPodName), domainNamespace);
    logger.info("admin svc host = {0}", adminSvcExtHost);

    // check configuration for JMS
    checkConfiguredJMSresouce(domainNamespace, adminServerPodName, adminSvcExtHost);

    // check the sample app is accessible from managed servers
    logger.info("Check and wait for the sample application to become ready");
    for (int i = 1; i <= replicaCount; i++) {
      int index = i;
      testUntil(withStandardRetryPolicy,
          () -> appAccessibleInPod(domainNamespace, managedServerPrefix + index, "8001",
              "sample-war/index.jsp", MII_APP_RESPONSE_V1 + index),
          logger,
          "application {0} is running on pod {1} in namespace {2}",
          "sample-war",
          managedServerPrefix + index,
          domainNamespace);
    }
  }

  /**
   * Create a domain with auxiliary image. Create the auxiliary image using customized options.
   * Verify the domain is up and running. Also check JDBC resources and WDT version.
   */
  @Test
  @DisplayName("Test to create domain using auxiliary image with customized options")
  @Tag("gate")
  void testCreateDomainUsingAuxImageCustomizedOptions() {
    // admin/managed server name here should match with model yaml
    final String auxiliaryImagePath2 = "/auxiliary2";
    String miiAuxiliaryImage = miiImage + "2";
    String miiAuxiliaryImageTag = miiImageTag + "2";
    // create a new auxiliary image with oraclelinux base image
    List<String> archiveList = Collections.singletonList(ARCHIVE_DIR + "/" + MII_BASIC_APP_NAME + ".zip");

    List<String> modelList = new ArrayList<>();
    modelList.add(MODEL_DIR + "/" + MII_BASIC_WDT_MODEL_FILE);
    modelList.add(MODEL_DIR + "/multi-model-one-ds.20.yaml");

    WitParams witParams =
        new WitParams()
        .modelImageName(MII_AUXILIARY_IMAGE_NAME)
        .modelImageTag(miiAuxiliaryImageTag)
        .baseImageName("ghcr.io/oracle/oraclelinux")
        .baseImageTag(ORACLELINUX_TEST_VERSION)
        .wdtHome(auxiliaryImagePath2)
        .modelArchiveFiles(archiveList)
        .modelFiles(modelList)
        .wdtVersion(WDT_TEST_VERSION);

    if (imageExists(MII_AUXILIARY_IMAGE_NAME, miiAuxiliaryImageTag)) {
      deleteImage(miiAuxiliaryImage);
    }
    
    logger.info("creating auxiliary image {0}:{1} using imagetool.sh ",
        MII_AUXILIARY_IMAGE_NAME, miiAuxiliaryImageTag);
    testUntil(
        withStandardRetryPolicy,
        createAuxiliaryImage(witParams),
        logger,
        "createAuxImage to be successful");


    // push image1 to repo for multi node cluster
    logger.info(WLSIMG_BUILDER + " push image {0} to registry {1}", miiAuxiliaryImage,
        DOMAIN_IMAGES_REPO);
    imageRepoLoginAndPushImageToRegistry(miiAuxiliaryImage);

    String clusterName = "cluster-1";

    // create domain custom resource using auxiliary image
    logger.info("Creating domain custom resource with domainUid {0} and auxiliary image {1}",
        domain2Uid, miiAuxiliaryImage);
    DomainResource domainCR = CommonMiiTestUtils.createDomainResourceWithAuxiliaryImage(domain2Uid, domainNamespace,
        WEBLOGIC_IMAGE_TO_USE_IN_SPEC, adminSecretName, createSecretsForImageRepos(domainNamespace),
        encryptionSecretName, auxiliaryImagePath2,
        miiAuxiliaryImage);

    String adminServerPodName = domain2Uid + "-admin-server";
    String managedServerPrefix = domain2Uid + "-managed-server";

    domainCR = createClusterResourceAndAddReferenceToDomain(
        domain2Uid + "-" + clusterName, clusterName, domainNamespace, domainCR, replicaCount);

    // create domain and verify its running
    logger.info("Creating domain {0} with auxiliary images {1} in namespace {2}",
        domain2Uid, miiAuxiliaryImage, domainNamespace);
    createDomainAndVerify(domain2Uid, domainCR, domainNamespace, adminServerPodName, managedServerPrefix, replicaCount);

    //create router for admin service on OKD
    String adminSvcExtHost = createRouteForOKD(getExternalServicePodName(adminServerPodName), domainNamespace);
    logger.info("admin svc host = {0}", adminSvcExtHost);

    checkConfiguredJDBCresouce(domainNamespace, adminServerPodName, adminSvcExtHost);

    // verify the WDT version
    String wdtVersion =
        assertDoesNotThrow(() -> checkWDTVersion(domainNamespace, adminServerPodName,
                "/aux", this.getClass().getSimpleName()));
    assertEquals("WebLogic Deploy Tooling " + WDT_TEST_VERSION, wdtVersion,
          " Used WDT in the auxiliary image was not updated");
  }

  /**
   * Create a domain with auxiliary image. Create the auxilary image using customized wdtModelHome.
   * Verify the domain is up and running.
   */
  @Test
  @DisplayName("Test to create domain using auxiliary image with customized wdtModelHome")
  void testCreateDomainUsingAuxImageCustomizedWdtmodelhome() {
    // admin/managed server name here should match with model yaml

    List<String> archiveList = Collections.singletonList(ARCHIVE_DIR + "/" + MII_BASIC_APP_NAME + ".zip");

    List<String> modelList = new ArrayList<>();
    modelList.add(MODEL_DIR + "/" + MII_BASIC_WDT_MODEL_FILE);
    modelList.add(MODEL_DIR + "/multi-model-one-ds.20.yaml");

    String customWdtHome = "/customwdthome";
    String customWdtModelHome = "/customwdtmodelhome/models";
    String miiAuxiliaryImage = miiImage + "3";
    String miiAuxiliaryImageTag = miiImageTag + "3";
    WitParams witParams =
        new WitParams()
            .modelImageName(MII_AUXILIARY_IMAGE_NAME)
            .modelImageTag(miiAuxiliaryImageTag)
            .wdtHome(customWdtHome)
            .wdtModelHome(customWdtModelHome)
            .modelArchiveFiles(archiveList)
            .modelFiles(modelList);

    if (imageExists(MII_AUXILIARY_IMAGE_NAME, miiAuxiliaryImageTag)) {
      deleteImage(miiAuxiliaryImage);
    }
    logger.info("creating auxiliary image {0} using imagetool.sh ", miiAuxiliaryImage);
    testUntil(
        withStandardRetryPolicy,
        createAuxiliaryImage(witParams),
        logger,
        "createAuxImage to be successful");

    // push image1 to repo for multi node cluster
    logger.info(WLSIMG_BUILDER + " push image {0} to registry {1}", miiAuxiliaryImage,
        DOMAIN_IMAGES_REPO);
    imageRepoLoginAndPushImageToRegistry(miiAuxiliaryImage);

    // create domain custom resource using auxiliary image
    String domain3Uid = "domain3";
    logger.info("Creating domain custom resource with domainUid {0} and auxiliary image {1}",
            domain3Uid, miiAuxiliaryImage);
    DomainResource domainCR = CommonMiiTestUtils.createDomainResourceWithAuxiliaryImage(domain3Uid, domainNamespace,
        WEBLOGIC_IMAGE_TO_USE_IN_SPEC, adminSecretName, createSecretsForImageRepos(domainNamespace),
        encryptionSecretName);
    domainCR.spec().configuration().model()
        .withAuxiliaryImage(new AuxiliaryImage()
            .image(miiAuxiliaryImage)
            .imagePullPolicy(IMAGE_PULL_POLICY)
            .sourceWDTInstallHome(customWdtHome + "/weblogic-deploy")
            .sourceModelHome(customWdtModelHome));

    String adminServerPodName = domain3Uid + "-admin-server";
    String managedServerPrefix = domain3Uid + "-managed-server";

    // create domain and verify its running
    logger.info("Creating domain {0} with auxiliary images {1} in namespace {2}",
            domain3Uid, miiAuxiliaryImage, domainNamespace);
    createDomainAndVerify(domain3Uid, domainCR, domainNamespace, adminServerPodName, managedServerPrefix, replicaCount);

    //create router for admin service on OKD
    String adminSvcExtHost = createRouteForOKD(getExternalServicePodName(adminServerPodName), domainNamespace);
    logger.info("admin svc host = {0}", adminSvcExtHost);

    checkConfiguredJDBCresouce(domainNamespace, adminServerPodName, adminSvcExtHost);
  }

  /**
   * Test createAuxImage with --dryRun option.
   */
  @Test
  @DisplayName("Test create auxiliary image with dryRun options")
  void testCreateAuxImageDryrunOptions() {
    String auxImageName = "mydryrunimage";
    WitParams witParams = new WitParams()
        .dryRun(true)
        .modelImageName(auxImageName)
        .modelImageTag("1")
        .modelFiles(Collections.singletonList(MODEL_DIR + "/model.update.wm.yaml"));

    ExecResult result = createAuxImageUsingWITAndReturnResult(witParams);

    // check there is Dockerfile printed out
    assertTrue(result.exitValue() == 0 && result.stdout().contains("BEGIN DOCKERFILE"));

    // check there is no mydryrunimage created
    CommandParams params = Command
        .defaultCommandParams()
        .command(WLSIMG_BUILDER + " images")
        .saveResults(true)
        .redirect(true);

    result = Command.withParams(params).executeAndReturnResult();
    assertFalse(result.stdout().contains(auxImageName));
  }

  /**
   * Negative test with unsupported packageManager.
   */
  @Test
  @DisplayName("Negative test with unsupported packageManager")
  void testNegativeCreateAuxImageUnsupportedPM() {
    String auxImageName = "myauxiliaryimage";
    WitParams witParams = new WitParams()
        .packageManager("pkm")
        .modelImageName(auxImageName)
        .modelImageTag("1")
        .modelFiles(Collections.singletonList(MODEL_DIR + "/model.update.wm.yaml"));

    ExecResult result = createAuxImageUsingWITAndReturnResult(witParams);
    String exepectedErrorMsg = "Invalid value for option '--packageManager':";
    assertTrue(result.exitValue() != 0 && result.stderr().contains(exepectedErrorMsg));
  }

  /**
   * Test createAuxImage with --pull option.
   */
  @Test
  @DisplayName("Test createAuxImage with --pull option")
  void testCreateAuxImagePullOption() {
    // pull base image first
    String imageAndTag = BUSYBOX_IMAGE + ":" + BUSYBOX_TAG;
    CommandParams params = Command
        .defaultCommandParams()
        .command(WLSIMG_BUILDER + " pull " + imageAndTag)
        .saveResults(true)
        .redirect(true);

    assertTrue(Command.withParams(params).execute(), "failed to pull " + imageAndTag);

    String auxImageName = "auximagewithpulloption";
    WitParams witParams = new WitParams()
        .modelImageName(auxImageName)
        .modelImageTag(MII_BASIC_IMAGE_TAG)
        .pull(true)
        .modelFiles(Collections.singletonList(MODEL_DIR + "/model.update.wm.yaml"));

    if (imageExists(auxImageName, MII_BASIC_IMAGE_TAG)) {
      deleteImage(auxImageName + ":" + MII_BASIC_IMAGE_TAG);
    }
    // create auxiliary image
    ExecResult result = createAuxImageUsingWITAndReturnResult(witParams);
    assertEquals(0, result.exitValue());

    // verify that base and aux image have the same RootFS
    params = Command
            .defaultCommandParams()
            .command(WLSIMG_BUILDER + " inspect --format='{{index .RootFS.Layers 0}}' " + imageAndTag)
            .saveResults(true)
            .redirect(true);

    assertTrue(Command.withParams(params).execute(), "failed to inspect RootFS of " + imageAndTag);
    String rootFS = params.stdout();

    params = Command
            .defaultCommandParams()
            .command(WLSIMG_BUILDER
                + " inspect --format='{{index .RootFS.Layers 0}}' "
                + auxImageName + ":" + MII_BASIC_IMAGE_TAG)
            .saveResults(true)
            .redirect(true);

    assertTrue(Command.withParams(params).execute(),
            "failed to inspect RootFS of " + auxImageName + ":" + MII_BASIC_IMAGE_TAG);
    assertEquals(rootFS, params.stdout());
  }

  /**
   * Test createAuxImage with --skipcleanup option.
   */
  @Test
  @DisplayName("Test createAuxImage with --skipcleanup option")
  void testCreateAuxImageSkipCleanup() {
    // remove images containing <none>
    CommandParams params = Command
        .defaultCommandParams()
        .command(WLSIMG_BUILDER + " rmi $(" + WLSIMG_BUILDER + " images |grep none | awk '{print $3}')")
        .saveResults(true)
        .redirect(true);

    Command.withParams(params).execute();

    String auxImageName = "auximagewithskipcleanupoption";
    WitParams witParams = new WitParams()
        .modelImageName(auxImageName)
        .modelImageTag(MII_BASIC_IMAGE_TAG)
        .skipCleanup(true)
        .modelFiles(Collections.singletonList(MODEL_DIR + "/model.update.wm.yaml"));

    // create auxiliary image
    createAuxImageUsingWITAndReturnResult(witParams);

    // verify there is intermediate images created and kept
    params = Command
        .defaultCommandParams()
        .command(WLSIMG_BUILDER + " images")
        .saveResults(true)
        .redirect(true);

    ExecResult result = Command.withParams(params).executeAndReturnResult();
    assertTrue(result.exitValue() == 0 && result.stdout().contains("<none>"));
  }

  /**
   * Cleanup images.
   */
  public void tearDownAll() {
    if (!SKIP_CLEANUP) {
      // delete images
      for (int i = 1; i < 4; i++) {
        if (miiImage + i != null) {
          deleteImage(miiImage + i);
        }
      }
    }
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

  /**
   * Check Configured JDBC Resource.
   *
   * @param domainNamespace domain namespace
   * @param adminServerPodName  admin server pod name
   * @param adminSvcExtHost admin server external host
   */
  public static void checkConfiguredJDBCresouce(String domainNamespace, String adminServerPodName,
                                                String adminSvcExtHost) {

    verifyConfiguredSystemResouceByPath(domainNamespace, adminServerPodName, adminSvcExtHost,
        "JDBCSystemResources/TestDataSource/JDBCResource/JDBCDriverParams",
        "jdbc:oracle:thin:@\\/\\/xxx.xxx.x.xxx:1521\\/ORCLCDB");
  }
}
