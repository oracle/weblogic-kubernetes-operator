// Copyright (c) 2022, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.weblogic.kubernetes;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import oracle.weblogic.domain.AuxiliaryImage;
import oracle.weblogic.domain.Domain;
import oracle.weblogic.kubernetes.actions.impl.primitive.Command;
import oracle.weblogic.kubernetes.actions.impl.primitive.CommandParams;
import oracle.weblogic.kubernetes.annotations.IntegrationTest;
import oracle.weblogic.kubernetes.annotations.Namespaces;
import oracle.weblogic.kubernetes.logging.LoggingFacade;
import oracle.weblogic.kubernetes.utils.CommonMiiTestUtils;
import oracle.weblogic.kubernetes.utils.ExecResult;
import org.apache.commons.io.FileUtils;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import static oracle.weblogic.kubernetes.TestConstants.ADMIN_PASSWORD_DEFAULT;
import static oracle.weblogic.kubernetes.TestConstants.ADMIN_USERNAME_DEFAULT;
import static oracle.weblogic.kubernetes.TestConstants.DOMAIN_IMAGES_REPO;
import static oracle.weblogic.kubernetes.TestConstants.KIND_REPO;
import static oracle.weblogic.kubernetes.TestConstants.MII_AUXILIARY_IMAGE_NAME;
import static oracle.weblogic.kubernetes.TestConstants.MII_BASIC_APP_NAME;
import static oracle.weblogic.kubernetes.TestConstants.MII_BASIC_IMAGE_TAG;
import static oracle.weblogic.kubernetes.TestConstants.MII_BASIC_WDT_MODEL_FILE;
import static oracle.weblogic.kubernetes.TestConstants.OCIR_SECRET_NAME;
import static oracle.weblogic.kubernetes.TestConstants.OPERATOR_RELEASE_NAME;
import static oracle.weblogic.kubernetes.TestConstants.RESULTS_ROOT;
import static oracle.weblogic.kubernetes.TestConstants.WEBLOGIC_IMAGE_NAME;
import static oracle.weblogic.kubernetes.TestConstants.WEBLOGIC_IMAGE_TO_USE_IN_SPEC;
import static oracle.weblogic.kubernetes.actions.ActionConstants.ARCHIVE_DIR;
import static oracle.weblogic.kubernetes.actions.ActionConstants.DOWNLOAD_DIR;
import static oracle.weblogic.kubernetes.actions.ActionConstants.MODEL_DIR;
import static oracle.weblogic.kubernetes.actions.ActionConstants.RESOURCE_DIR;
import static oracle.weblogic.kubernetes.actions.TestActions.buildAppArchive;
import static oracle.weblogic.kubernetes.actions.TestActions.createDomainCustomResource;
import static oracle.weblogic.kubernetes.actions.TestActions.defaultAppParams;
import static oracle.weblogic.kubernetes.actions.TestActions.deleteImage;
import static oracle.weblogic.kubernetes.actions.TestActions.dockerTag;
import static oracle.weblogic.kubernetes.actions.TestActions.getDomainCustomResource;
import static oracle.weblogic.kubernetes.actions.TestActions.getOperatorPodName;
import static oracle.weblogic.kubernetes.actions.TestActions.getServiceNodePort;
import static oracle.weblogic.kubernetes.assertions.TestAssertions.verifyRollingRestartOccurred;
import static oracle.weblogic.kubernetes.utils.CommonMiiTestUtils.createDomainResource40;
import static oracle.weblogic.kubernetes.utils.CommonMiiTestUtils.readFilesInPod;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.checkPodReadyAndServiceExists;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.checkSystemResourceConfig;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.checkSystemResourceConfiguration;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.getDateAndTimeStamp;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.testUntil;
import static oracle.weblogic.kubernetes.utils.DomainUtils.createDomainAndVerify;
import static oracle.weblogic.kubernetes.utils.DomainUtils.patchDomainWithAuxiliaryImageAndVerify;
import static oracle.weblogic.kubernetes.utils.FileUtils.replaceStringInFile;
import static oracle.weblogic.kubernetes.utils.FileUtils.unzipWDTInstallationFile;
import static oracle.weblogic.kubernetes.utils.ImageUtils.createOcirRepoSecret;
import static oracle.weblogic.kubernetes.utils.ImageUtils.dockerLoginAndPushImageToRegistry;
import static oracle.weblogic.kubernetes.utils.LoggingUtil.checkPodLogContainsString;
import static oracle.weblogic.kubernetes.utils.OKDUtils.createRouteForOKD;
import static oracle.weblogic.kubernetes.utils.OperatorUtils.installAndVerifyOperator;
import static oracle.weblogic.kubernetes.utils.PatchDomainUtils.patchDomainResource;
import static oracle.weblogic.kubernetes.utils.PodUtils.getExternalServicePodName;
import static oracle.weblogic.kubernetes.utils.PodUtils.getPodsWithTimeStamps;
import static oracle.weblogic.kubernetes.utils.PodUtils.verifyIntrospectorPodLogContainsExpectedErrorMsg;
import static oracle.weblogic.kubernetes.utils.SecretUtils.createSecretWithUsernamePassword;
import static oracle.weblogic.kubernetes.utils.ThreadSafeLogger.getLogger;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("Test to create model in image domain using auxiliary image. "
    + "Multiple domains are created in the same namespace in this class.")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@IntegrationTest
class ItMiiAuxiliaryImage40 {

  private static String domainNamespace = null;
  private static LoggingFacade logger = null;
  private static final String domainUid1 = "domain1";
  private final String domainUid = "";
  private static final String miiAuxiliaryImage1 = MII_AUXILIARY_IMAGE_NAME + "-domain:" + MII_BASIC_IMAGE_TAG + "1";
  private static final String miiAuxiliaryImage2 = MII_AUXILIARY_IMAGE_NAME + "-domain:" + MII_BASIC_IMAGE_TAG + "2";
  private static final String miiAuxiliaryImage3 = MII_AUXILIARY_IMAGE_NAME + "-domain:" + MII_BASIC_IMAGE_TAG + "3";
  private static final String miiAuxiliaryImage4 = MII_AUXILIARY_IMAGE_NAME + "-domain:" + MII_BASIC_IMAGE_TAG + "4";
  private static final String miiAuxiliaryImage5 = MII_AUXILIARY_IMAGE_NAME + "-domain:" + MII_BASIC_IMAGE_TAG + "5";
  private static final String miiAuxiliaryImage6 = MII_AUXILIARY_IMAGE_NAME + "-domain:" + MII_BASIC_IMAGE_TAG + "6";
  private static final String miiAuxiliaryImage7 = MII_AUXILIARY_IMAGE_NAME + "-domain:" + MII_BASIC_IMAGE_TAG + "7";
  private static final String miiAuxiliaryImage8 = MII_AUXILIARY_IMAGE_NAME + "-domain:" + MII_BASIC_IMAGE_TAG + "8";
  private final String adminServerPodName = domainUid + "-admin-server";
  private final String managedServerPrefix = domainUid + "-managed-server";
  private static final String adminServerPodNameDomain1 = domainUid1 + "-admin-server";
  private static final String managedServerPrefixDomain1 = domainUid1 + "-managed-server";
  private static final int replicaCount = 2;
  private String adminSvcExtHost = null;
  private static String adminSvcExtHostDomain1 = null;
  private static String adminSecretName = "weblogic-credentials";
  private static String encryptionSecretName = "encryptionsecret";
  private static String opNamespace = null;

  /**
   * Install Operator. Create a domain using multiple auxiliary images.
   * One auxiliary image containing the domain configuration and another auxiliary image with
   * JMS system resource, verify the domain is running and JMS resource is added.
   *
   * @param namespaces list of namespaces created by the IntegrationTestWatcher by the
   *                   JUnit engine parameter resolution mechanism
   */
  @BeforeAll
  public static void initAll(@Namespaces(4) List<String> namespaces) {
    logger = getLogger();
    // get a new unique opNamespace
    logger.info("Creating unique namespace for Operator");
    assertNotNull(namespaces.get(0), "Namespace list is null");
    opNamespace = namespaces.get(0);

    logger.info("Creating unique namespace for Domain1");
    assertNotNull(namespaces.get(1), "Namespace list is null");
    domainNamespace = namespaces.get(1);

    logger.info("Creating unique namespace for errorpathDomain");
    assertNotNull(namespaces.get(2), "Namespace list is null");
    String errorpathDomainNamespace = namespaces.get(2);

    logger.info("Creating unique namespace for wdtDomainNamespace");
    assertNotNull(namespaces.get(3), "Namespace list is null");
    String wdtDomainNamespace = namespaces.get(3);

    // install and verify operator
    installAndVerifyOperator(opNamespace, domainNamespace, errorpathDomainNamespace, wdtDomainNamespace);

    // Create the repo secret to pull the image
    // this secret is used only for non-kind cluster
    createOcirRepoSecret(domainNamespace);

    // create secret for admin credentials
    logger.info("Create secret for admin credentials");
    createSecretWithUsernamePassword(adminSecretName, domainNamespace,
        ADMIN_USERNAME_DEFAULT, ADMIN_PASSWORD_DEFAULT);

    // create encryption secret
    logger.info("Create encryption secret");
    createSecretWithUsernamePassword(encryptionSecretName, domainNamespace,
        "weblogicenc", "weblogicenc");

    // image1 with model files for domain config, ds, app and wdt install files
    createAuxiliaryImageWithDomainConfig(miiAuxiliaryImage1, "/auxiliary");
    // image2 with model files for jms config
    createAuxiliaryImageWithJmsConfig(miiAuxiliaryImage2, "/auxiliary");

    // admin/managed server name here should match with model yaml
    final String auxiliaryImagePath = "/auxiliary";

    // create domain custom resource using 2 auxiliary images
    logger.info("Creating domain custom resource with domainUid {0} and auxiliary images {1} {2}",
        domainUid1, miiAuxiliaryImage1, miiAuxiliaryImage2);
    Domain domainCR = createDomainResource40(domainUid1, domainNamespace,
        WEBLOGIC_IMAGE_TO_USE_IN_SPEC, adminSecretName, OCIR_SECRET_NAME,
        encryptionSecretName, replicaCount, "cluster-1", auxiliaryImagePath,
        miiAuxiliaryImage1, miiAuxiliaryImage2);

    // create domain and verify its running
    logger.info("Creating domain {0} with auxiliary images {1} {2} in namespace {3}",
        domainUid1, miiAuxiliaryImage1, miiAuxiliaryImage2, domainNamespace);
    createDomainAndVerify(domainUid1, domainCR, domainNamespace,
        adminServerPodNameDomain1, managedServerPrefixDomain1, replicaCount);

    //create router for admin service on OKD
    if (adminSvcExtHostDomain1 == null) {
      adminSvcExtHostDomain1 = createRouteForOKD(getExternalServicePodName(adminServerPodNameDomain1), domainNamespace);
      logger.info("admin svc host = {0}", adminSvcExtHostDomain1);
    }

    // check configuration for JMS
    checkConfiguredJMSresouce(domainNamespace, adminSvcExtHostDomain1);
  }

  /**
   * Reuse created a domain with datasource using auxiliary image containing the DataSource,
   * verify the domain is running and JDBC DataSource resource is added.
   * Patch domain with updated JDBC URL info and verify the update.
   */
  @Test
  @DisplayName("Test to update data source url in the  domain using auxiliary image")
  void testUpdateDataSourceInDomainUsingAuxiliaryImage() {
    Path multipleAIPath1 = Paths.get(RESULTS_ROOT, this.getClass().getSimpleName(),
        "ai" + miiAuxiliaryImage1.substring(miiAuxiliaryImage1.length() - 1));
    Path modelsPath1 = Paths.get(multipleAIPath1.toString(), "models");

    // create stage dir for auxiliary image with image3
    // replace DataSource URL info in the  model file
    assertDoesNotThrow(() -> replaceStringInFile(Paths.get(modelsPath1.toString(),
        "/multi-model-one-ds.20.yaml").toString(), "xxx.xxx.x.xxx:1521",
        "localhost:7001"), "Can't replace datasource url in the model file");
    assertDoesNotThrow(() -> replaceStringInFile(Paths.get(modelsPath1.toString(),
        "/multi-model-one-ds.20.yaml").toString(), "ORCLCDB",
        "dbsvc"), "Can't replace datasource url in the model file");

    // create image3 with model and wdt installation files
    createAuxiliaryImage(multipleAIPath1.toString(),
        Paths.get(RESOURCE_DIR, "auxiliaryimage", "Dockerfile").toString(), miiAuxiliaryImage3);

    // push image3 to repo for multi node cluster
    if (!DOMAIN_IMAGES_REPO.isEmpty()) {
      logger.info("docker push image {0} to registry {1}", miiAuxiliaryImage3, DOMAIN_IMAGES_REPO);
      dockerLoginAndPushImageToRegistry(miiAuxiliaryImage3);
    }

    //create router for admin service on OKD
    if (adminSvcExtHostDomain1 == null) {
      adminSvcExtHostDomain1 = createRouteForOKD(getExternalServicePodName(adminServerPodNameDomain1), domainNamespace);
      logger.info("admin svc host = {0}", adminSvcExtHostDomain1);
    }

    // check configuration for DataSource in the running domain
    int adminServiceNodePort
        = getServiceNodePort(domainNamespace, getExternalServicePodName(adminServerPodNameDomain1), "default");
    assertNotEquals(-1, adminServiceNodePort, "admin server default node port is not valid");
    assertTrue(checkSystemResourceConfig(adminSvcExtHostDomain1, adminServiceNodePort,
        "JDBCSystemResources/TestDataSource/JDBCResource/JDBCDriverParams",
        "jdbc:oracle:thin:@\\/\\/xxx.xxx.x.xxx:1521\\/ORCLCDB"),
        "Can't find expected URL configuration for DataSource");

    logger.info("Found the DataResource configuration");

    patchDomainWithAuxiliaryImageAndVerify(miiAuxiliaryImage1, miiAuxiliaryImage3, domainUid1, domainNamespace);

    checkConfiguredJDBCresouce(domainNamespace, adminServerPodNameDomain1, adminSvcExtHostDomain1);
  }

  /**
   * Patch the domain with the different base image name.
   * Verify all the pods are restarted and back to ready state.
   * Verify configured JMS and JDBC resources.
   */
  @Test
  @DisplayName("Test to update Base Weblogic Image Name")
  void testUpdateBaseImageName() {
    // get the original domain resource before update
    Domain domain1 = assertDoesNotThrow(() -> getDomainCustomResource(domainUid1, domainNamespace),
        String.format("getDomainCustomResource failed with ApiException when tried to get domain %s in namespace %s",
            domainUid1, domainNamespace));
    assertNotNull(domain1, "Got null domain resource");
    assertNotNull(domain1.getSpec(), domain1 + "/spec is null");

    // get the map with server pods and their original creation timestamps
    Map podsWithTimeStamps = getPodsWithTimeStamps(domainNamespace, adminServerPodNameDomain1,
        managedServerPrefixDomain1, replicaCount);

    //print out the original image name
    String imageName = domain1.getSpec().getImage();
    logger.info("Currently the image name used for the domain is: {0}", imageName);

    //change image name to imageUpdate
    String imageTag = getDateAndTimeStamp();
    String imageUpdate = KIND_REPO != null ? KIND_REPO
        + (WEBLOGIC_IMAGE_NAME + ":" + imageTag).substring(TestConstants.BASE_IMAGES_REPO.length() + 1)
        : WEBLOGIC_IMAGE_NAME + ":" + imageTag;
    dockerTag(imageName, imageUpdate);
    dockerLoginAndPushImageToRegistry(imageUpdate);

    StringBuffer patchStr;
    patchStr = new StringBuffer("[{");
    patchStr.append("\"op\": \"replace\",")
        .append(" \"path\": \"/spec/image\",")
        .append("\"value\": \"")
        .append(imageUpdate)
        .append("\"}]");
    logger.info("PatchStr for imageUpdate: {0}", patchStr.toString());

    assertTrue(patchDomainResource(domainUid1, domainNamespace, patchStr),
        "patchDomainCustomResource(imageUpdate) failed");

    domain1 = assertDoesNotThrow(() -> getDomainCustomResource(domainUid1, domainNamespace),
        String.format("getDomainCustomResource failed with ApiException when tried to get domain %s in namespace %s",
            domainUid1, domainNamespace));
    assertNotNull(domain1, "Got null domain resource after patching");
    assertNotNull(domain1.getSpec(), domain1 + " /spec is null");

    //print out image name in the new patched domain
    logger.info("In the new patched domain image name is: {0}", domain1.getSpec().getImage());

    // verify the server pods are rolling restarted and back to ready state
    logger.info("Verifying rolling restart occurred for domain {0} in namespace {1}",
        domainUid1, domainNamespace);
    assertTrue(verifyRollingRestartOccurred(podsWithTimeStamps, 1, domainNamespace),
        String.format("Rolling restart failed for domain %s in namespace %s", domainUid1, domainNamespace));

    checkPodReadyAndServiceExists(adminServerPodNameDomain1, domainUid1, domainNamespace);

    //create router for admin service on OKD
    if (adminSvcExtHostDomain1 == null) {
      adminSvcExtHostDomain1 = createRouteForOKD(getExternalServicePodName(adminServerPodNameDomain1), domainNamespace);
      logger.info("admin svc host = {0}", adminSvcExtHostDomain1);
    }

    // check configuration for JMS
    checkConfiguredJMSresouce(domainNamespace, adminSvcExtHostDomain1);
    //check configuration for JDBC
    checkConfiguredJDBCresouce(domainNamespace, adminServerPodNameDomain1, adminSvcExtHostDomain1);

  }

  /**
   * Create a domain using multiple auxiliary images using different WDT versions.
   * Use Case 1: Both the AI's have WDT install files but different versions.
   * One auxiliary image sourceWDTInstallHome set to default and the other auxiliary image
   * sourceWDTInstallHome set to None. The WDT install files from the second AI should be ignored.
   * Default model home location have no files, should be ignored.
   * Use Case 2: Both the auxiliary images sourceWDTInstallHome set to default.
   * Introspector should log an error message.
   */
  @Test
  @DisplayName("Test to create domain using multiple auxiliary images and different WDT installations")
  void testWithMultipleAIsHavingWDTInstallers() {

    // admin/managed server name here should match with model yaml
    final String auxiliaryImagePath = "/auxiliary";
    final String domainUid = "domain2";
    final String adminServerPodName = domainUid + "-admin-server";
    final String managedServerPrefix = domainUid + "-managed-server";
    // using the first image created in initAll, creating second image with different WDT version here

    // create stage dir for second auxiliary image with image2
    Path multipleAIPath2 = Paths.get(RESULTS_ROOT, this.getClass().getSimpleName(), "multipleauxiliaryimage2");
    assertDoesNotThrow(() -> FileUtils.deleteDirectory(multipleAIPath2.toFile()),
        "Delete directory failed");
    assertDoesNotThrow(() -> Files.createDirectories(multipleAIPath2),
        "Create directory failed");

    // create models dir with no files at default locaiton
    Path modelsPath2 = Paths.get(multipleAIPath2.toString(), "models");
    assertDoesNotThrow(() -> Files.createDirectories(modelsPath2));

    // unzip older release WDT installation file into work dir
    unzipWDTInstallationFile(multipleAIPath2.toString(),
        "https://github.com/oracle/weblogic-deploy-tooling/releases/download/release-1.9.19/weblogic-deploy.zip",
        DOWNLOAD_DIR + "/image2");

    // create image2 with model and wdt installation files
    createAuxiliaryImage(multipleAIPath2.toString(),
        Paths.get(RESOURCE_DIR, "auxiliaryimage", "Dockerfile").toString(), miiAuxiliaryImage4);

    // push image2 to repo for multi node cluster
    if (!DOMAIN_IMAGES_REPO.isEmpty()) {
      logger.info("docker push image {0} to registry {1}", miiAuxiliaryImage4, DOMAIN_IMAGES_REPO);
      dockerLoginAndPushImageToRegistry(miiAuxiliaryImage4);
    }

    // create domain custom resource using 2 auxiliary images, one with default sourceWDTInstallHome
    // and other with sourceWDTInstallHome set to none
    logger.info("Creating domain custom resource with domainUid {0} and auxiliary images {1} {2}",
        domainUid, miiAuxiliaryImage1, miiAuxiliaryImage4);
    Domain domainCR1 = createDomainResourceWithAuxiliaryImage40(domainUid, domainNamespace,
        WEBLOGIC_IMAGE_TO_USE_IN_SPEC, adminSecretName, OCIR_SECRET_NAME,
        encryptionSecretName, replicaCount, List.of("cluster-1"), auxiliaryImagePath,
        miiAuxiliaryImage1, miiAuxiliaryImage4);

    // create domain and verify its running
    logger.info("Creating domain {0} with auxiliary images {1} {2} in namespace {3}",
        domainUid, miiAuxiliaryImage1, miiAuxiliaryImage4, domainNamespace);
    createDomainAndVerify(domainUid, domainCR1, domainNamespace,
        adminServerPodName, managedServerPrefix, replicaCount);

    // check WDT version in main container in admin pod
    String wdtVersionFileInsidePod = "/aux/weblogic-deploy/VERSION.txt";
    ExecResult result = readFilesInPod(domainNamespace, adminServerPodName, wdtVersionFileInsidePod);

    logger.info("readFilesInPod returned: {0}", result.toString());
    assertFalse(result.exitValue() != 0, String.format("Failed to read file %s. Error is: %s",
        wdtVersionFileInsidePod, result.stderr()));
    assertFalse(result.toString().contains("1.9.19"),
        "Old version of WDT is copied");

    // create domain custom resource using 2 auxiliary images with default sourceWDTInstallHome for both images
    logger.info("Creating domain custom resource with domainUid {0} and auxiliary images {1} {2}",
        domainUid, miiAuxiliaryImage1, miiAuxiliaryImage4);
    Domain domainCR2 = createDomainResource40(domainUid + "1", domainNamespace,
        WEBLOGIC_IMAGE_TO_USE_IN_SPEC, adminSecretName, OCIR_SECRET_NAME,
        encryptionSecretName, replicaCount, "cluster-1", auxiliaryImagePath,
        miiAuxiliaryImage1, miiAuxiliaryImage4);

    logger.info("Creating domain custom resource for domainUid {0} in namespace {1}",
        domainUid + "1", domainNamespace);
    assertTrue(assertDoesNotThrow(() -> createDomainCustomResource(domainCR2),
            String.format("Create domain custom resource failed with ApiException for %s in namespace %s",
                domainUid + "1", domainNamespace)),
        String.format("Create domain custom resource failed with ApiException for %s in namespace %s",
            domainUid + "1", domainNamespace));

    String errorMessage =
        "[SEVERE] The target directory for WDT installation files '/tmpAuxiliaryImage/weblogic-deploy' "
        + "is not empty.  This is usually because multiple auxiliary images are specified, and more than one "
        + "specified a WDT install,  which is not allowed; if this is the problem, then you can correct the "
        + "problem by setting the  'domain.spec.configuration.model.auxiliaryImages.sourceWDTInstallHome' to "
        + "'None' on images that you  don't want to have an install copy";
    verifyIntrospectorPodLogContainsExpectedErrorMsg(domainUid + "1", domainNamespace, errorMessage);

  }

  /**
   * Negative test. Create a domain using auxiliary image with no installation files at specified sourceWdtInstallHome
   * location. Verify introspector log contains the expected error message.
   */
  @Test
  @DisplayName("Test to create domain using auxiliary image with no files at specified sourceWdtInstallHome")
  void testCreateDomainNoFilesAtSourceWDTInstallHome() {

    final String auxiliaryImagePathCustom = "/customauxiliary";
    final String domainUid = "domain3";

    // creating image with no WDT install files
    // create stage dir for auxiliary image
    Path multipleAIPath1 = Paths.get(RESULTS_ROOT, this.getClass().getSimpleName(), "multipleauxiliaryimage9");
    assertDoesNotThrow(() -> FileUtils.deleteDirectory(multipleAIPath1.toFile()),
        "Delete directory failed");
    assertDoesNotThrow(() -> Files.createDirectories(multipleAIPath1),
        "Create directory failed");
    Path multipleAIPathToFile1 =
        Paths.get(RESULTS_ROOT, this.getClass().getSimpleName(), "multipleauxiliaryimage9/test.txt");
    String content = "1";
    assertDoesNotThrow(() -> Files.write(multipleAIPathToFile1, content.getBytes()),
        "Can't write to file " + multipleAIPathToFile1);

    // create models dir and copy model, archive files if any for image1
    Path modelsPath1 = Paths.get(multipleAIPath1.toString(), "models");
    assertDoesNotThrow(() -> Files.createDirectories(modelsPath1));
    assertDoesNotThrow(() -> Files.copy(
        Paths.get(MODEL_DIR, MII_BASIC_WDT_MODEL_FILE),
        Paths.get(modelsPath1.toString(), MII_BASIC_WDT_MODEL_FILE),
        StandardCopyOption.REPLACE_EXISTING));

    // create image1 with model and wdt installation files
    createAuxiliaryImage(multipleAIPath1.toString(),
        Paths.get(RESOURCE_DIR, "auxiliaryimage", "Dockerfile").toString(), miiAuxiliaryImage5);

    // push image1 to repo for multi node cluster
    if (!DOMAIN_IMAGES_REPO.isEmpty()) {
      logger.info("docker push image {0} to registry {1}", miiAuxiliaryImage5, DOMAIN_IMAGES_REPO);
      dockerLoginAndPushImageToRegistry(miiAuxiliaryImage5);
    }

    // create domain custom resource using auxiliary image
    logger.info("Creating domain custom resource with domainUid {0} and auxiliary image {1}",
        domainUid, miiAuxiliaryImage5);
    Domain domainCR = createDomainResourceWithAuxiliaryImage40(domainUid, domainNamespace,
        WEBLOGIC_IMAGE_TO_USE_IN_SPEC, adminSecretName, OCIR_SECRET_NAME,
        encryptionSecretName, replicaCount, List.of("cluster-1"), auxiliaryImagePathCustom,
        miiAuxiliaryImage5);

    logger.info("Creating domain custom resource for domainUid {0} in namespace {1}",
        domainUid, domainNamespace);
    assertTrue(assertDoesNotThrow(() -> createDomainCustomResource(domainCR),
            String.format("Create domain custom resource failed with ApiException for %s in namespace %s",
                domainUid, domainNamespace)),
        String.format("Create domain custom resource failed with ApiException for %s in namespace %s",
            domainUid, domainNamespace));

    String errorMessage = "Make sure the 'sourceWDTInstallHome' is correctly specified and the WDT installation "
              + "files are available in this directory  or set 'sourceWDTInstallHome' to 'None' for this image.";
    verifyIntrospectorPodLogContainsExpectedErrorMsg(domainUid, domainNamespace, errorMessage);

  }

  /**
   * Negative test. Create a domain using multiple auxiliary images with specified(custom) sourceWdtInstallHome.
   * Verify operator log contains the expected error message and domain status and condition. This is a validation
   * check.
   */
  @Test
  @DisplayName("Test to create domain using multiple auxiliary images with specified sourceWdtInstallHome")
  void testSourceWDTInstallHomeSetAtMultipleAIs() {

    final String auxiliaryImagePathCustom = "/customauxiliary";
    final String domainUid = "domain4";

    // image1 with model files for domain config, ds, app and wdt install files
    createAuxiliaryImageWithDomainConfig(miiAuxiliaryImage6, auxiliaryImagePathCustom);
    // image2 with model files for jms config
    createAuxiliaryImageWithJmsConfig(miiAuxiliaryImage7, auxiliaryImagePathCustom);

    // create domain custom resource using auxiliary images
    String[] images = {miiAuxiliaryImage6, miiAuxiliaryImage7};
    Domain domainCR = CommonMiiTestUtils.createDomainResource(domainUid, domainNamespace,
        WEBLOGIC_IMAGE_TO_USE_IN_SPEC, adminSecretName, OCIR_SECRET_NAME,
        encryptionSecretName, replicaCount, "cluster-1",
        auxiliaryImagePathCustom, miiAuxiliaryImage6, miiAuxiliaryImage7);

    // add the sourceWDTInstallHome and sourceModelHome for both aux images.
    for (String cmImageName : images) {
      AuxiliaryImage auxImage = new AuxiliaryImage().image(cmImageName).imagePullPolicy("IfNotPresent");
      auxImage.sourceWDTInstallHome(auxiliaryImagePathCustom + "/weblogic-deploy")
          .sourceModelHome(auxiliaryImagePathCustom + "/models");
      domainCR.spec().configuration().model().withAuxiliaryImage(auxImage);
    }

    logger.info("Creating domain custom resource for domainUid {0} in namespace {1}",
        domainUid, domainNamespace);
    assertTrue(assertDoesNotThrow(() -> createDomainCustomResource(domainCR),
            String.format("Create domain custom resource failed with ApiException for %s in namespace %s",
                domainUid, domainNamespace)),
        String.format("Create domain custom resource failed with ApiException for %s in namespace %s",
            domainUid, domainNamespace));

    String errorMessage = "More than one auxiliary image under 'spec.configuration.model.auxiliaryImages' sets a "
            + "'sourceWDTInstallHome' value. The sourceWDTInstallHome value must be set for only one auxiliary image.";
    String operatorPodName = assertDoesNotThrow(() -> getOperatorPodName(OPERATOR_RELEASE_NAME, opNamespace));
    checkPodLogContainsString(opNamespace, operatorPodName, errorMessage);

  }

  /**
   * Negative test. Create a domain using auxiliary image with no model files at specified sourceModelHome
   * location. Verify introspector log contains the expected error message.
   */
  @Test
  @DisplayName("Test to create domain using auxiliary image with no files at specified sourceModelHome")
  void testCreateDomainNoFilesAtSourceModelHome() {

    final String auxiliaryImagePathCustom = "/customauxiliary";
    final String domainUid = "domain5";

    // creating image with no model files
    // create stage dir for auxiliary image
    Path aiWithNoModel = Paths.get(RESULTS_ROOT, this.getClass().getSimpleName(), "aiwithnomodel");
    assertDoesNotThrow(() -> FileUtils.deleteDirectory(aiWithNoModel.toFile()),
        "Delete directory failed");
    assertDoesNotThrow(() -> Files.createDirectories(aiWithNoModel),
        "Create directory failed");
    Path pathToFile =
        Paths.get(RESULTS_ROOT, this.getClass().getSimpleName(), "aiwithnomodel/test.txt");
    String content = "1";
    assertDoesNotThrow(() -> Files.write(pathToFile, content.getBytes()),
        "Can't write to file " + pathToFile);

    // create models dir and copy model, archive files if any for image1
    Path modelsPath1 = Paths.get(aiWithNoModel.toString(), "models");
    assertDoesNotThrow(() -> Files.createDirectories(modelsPath1));

    // unzip WDT installation file into work dir
    unzipWDTInstallationFile(aiWithNoModel.toString());

    // create image1 with model and wdt installation files
    createAuxiliaryImage(aiWithNoModel.toString(),
        Paths.get(RESOURCE_DIR, "auxiliaryimage", "Dockerfile").toString(),
        miiAuxiliaryImage8, auxiliaryImagePathCustom);

    // push image1 to repo for multi node cluster
    if (!DOMAIN_IMAGES_REPO.isEmpty()) {
      logger.info("docker push image {0} to registry {1}", miiAuxiliaryImage8, DOMAIN_IMAGES_REPO);
      dockerLoginAndPushImageToRegistry(miiAuxiliaryImage8);
    }

    // create domain custom resource using auxiliary image
    logger.info("Creating domain custom resource with domainUid {0} and auxiliary image {1}",
        domainUid, miiAuxiliaryImage8);
    Domain domainCR = createDomainResourceWithAuxiliaryImage40(domainUid, domainNamespace,
        WEBLOGIC_IMAGE_TO_USE_IN_SPEC, adminSecretName, OCIR_SECRET_NAME,
        encryptionSecretName, replicaCount, List.of("cluster-1"), auxiliaryImagePathCustom,
        miiAuxiliaryImage8);

    logger.info("Creating domain custom resource for domainUid {0} in namespace {1}",
        domainUid, domainNamespace);
    assertTrue(assertDoesNotThrow(() -> createDomainCustomResource(domainCR),
            String.format("Create domain custom resource failed with ApiException for %s in namespace %s",
                domainUid, domainNamespace)),
        String.format("Create domain custom resource failed with ApiException for %s in namespace %s",
            domainUid, domainNamespace));

    String errorMessage = "Make sure the 'sourceModelHome' is correctly specified and the WDT model "
        + "files are available in this directory  or set 'sourceModelHome' to 'None' for this image.";
    verifyIntrospectorPodLogContainsExpectedErrorMsg(domainUid, domainNamespace, errorMessage);

  }

  /**
   * Create a domain using multiple auxiliary images. One auxiliary image containing the domain configuration and
   * another auxiliary image with JMS system resource but with sourceModelHome set to none,
   * verify the domain is running and JMS resource is not added.
   */
  @Test
  @DisplayName("Test to create domain using multiple auxiliary images with model files and one AI having "
      + "sourceModelHome set to none")
  void testWithAISourceModelHomeSetToNone() {

    // admin/managed server name here should match with model yaml
    final String auxiliaryImagePath = "/auxiliary";
    final String domainUid = "domain6";
    final String adminServerPodName = domainUid + "-admin-server";
    final String managedServerPrefix = domainUid + "-managed-server";
    // using the images created in initAll
    // create domain custom resource using 2 auxiliary images, one with default sourceWDTInstallHome
    // and other with sourceWDTInstallHome set to none
    logger.info("Creating domain custom resource with domainUid {0} and auxiliary images {1} {2}",
        domainUid, miiAuxiliaryImage1, miiAuxiliaryImage4);
    Domain domainCR1 = createDomainResourceWithAuxiliaryImage40(domainUid, domainNamespace,
        WEBLOGIC_IMAGE_TO_USE_IN_SPEC, adminSecretName, OCIR_SECRET_NAME,
        encryptionSecretName, replicaCount, List.of("cluster-1"), auxiliaryImagePath,
        miiAuxiliaryImage1, miiAuxiliaryImage2);

    // create domain and verify its running
    logger.info("Creating domain {0} with auxiliary images {1} {2} in namespace {3}",
        domainUid, miiAuxiliaryImage1, miiAuxiliaryImage4, domainNamespace);
    createDomainAndVerify(domainUid, domainCR1, domainNamespace,
        adminServerPodName, managedServerPrefix, replicaCount);

    int adminServiceNodePort
        = getServiceNodePort(domainNamespace, getExternalServicePodName(adminServerPodName), "default");
    assertNotEquals(-1, adminServiceNodePort, "admin server default node port is not valid");

    assertFalse(checkSystemResourceConfiguration(adminSvcExtHost, adminServiceNodePort, "JMSSystemResources",
        "TestClusterJmsModule2", "200"), "Model files from second AI are not ignored");
  }


  /**
   * Cleanup images.
   */
  public void tearDownAll() {
    // delete images
    deleteImage(miiAuxiliaryImage1);
    deleteImage(miiAuxiliaryImage2);
    deleteImage(miiAuxiliaryImage3);
    deleteImage(miiAuxiliaryImage4);
    deleteImage(miiAuxiliaryImage5);
    deleteImage(miiAuxiliaryImage6);
    deleteImage(miiAuxiliaryImage7);
    deleteImage(miiAuxiliaryImage8);
  }

  private static void createAuxiliaryImage(String stageDirPath, String dockerFileLocation, String auxiliaryImage) {
    createAuxiliaryImage(stageDirPath, dockerFileLocation, auxiliaryImage, "/auxiliary");
  }

  private static void createAuxiliaryImage(String stageDirPath, String dockerFileLocation,
                                           String auxiliaryImage, String auxiliaryImagePath) {
    String cmdToExecute = String.format("cd %s && docker build -f %s %s -t %s .",
        stageDirPath, dockerFileLocation,
        "--build-arg AUXILIARY_IMAGE_PATH=" + auxiliaryImagePath, auxiliaryImage);
    assertTrue(new Command()
        .withParams(new CommandParams()
            .command(cmdToExecute))
        .execute(), String.format("Failed to execute", cmdToExecute));
  }

  private static void checkConfiguredJMSresouce(String domainNamespace, String adminSvcExtHost) {
    int adminServiceNodePort
        = getServiceNodePort(domainNamespace, getExternalServicePodName("domain1-admin-server"), "default");
    assertNotEquals(-1, adminServiceNodePort, "admin server default node port is not valid");

    testUntil(
        () -> checkSystemResourceConfiguration(adminSvcExtHost, adminServiceNodePort, "JMSSystemResources",
        "TestClusterJmsModule2", "200"),
        logger,
          "Checking for adminSvcExtHost: {0} or adminServiceNodePort: {1} if resourceName: {2} exists",
        adminSvcExtHost,
        adminServiceNodePort,
        "TestClusterJmsModule2");
    logger.info("Found the JMSSystemResource configuration");
  }

  private void checkConfiguredJDBCresouce(String domainNamespace, String adminServerPodName, String adminSvcExtHost) {
    int adminServiceNodePort
        = getServiceNodePort(domainNamespace, getExternalServicePodName(adminServerPodName), "default");
    assertNotEquals(-1, adminServiceNodePort, "admin server default node port is not valid");

    testUntil(
        () -> checkSystemResourceConfig(adminSvcExtHost, adminServiceNodePort,
        "JDBCSystemResources/TestDataSource/JDBCResource/JDBCDriverParams",
            "jdbc:oracle:thin:@\\/\\/localhost:7001\\/dbsvc"),
        logger,
          "Checking for adminSvcExtHost: {0} or adminServiceNodePort: {1} if resourceName: {2} has the right value",
        adminSvcExtHost,
        adminServiceNodePort,
        "JDBCSystemResources/TestDataSource/JDBCResource/JDBCDriverParams");
    logger.info("Found the DataResource configuration");
  }

  private Domain createDomainResourceWithAuxiliaryImage40(
      String domainResourceName,
      String domNamespace,
      String baseImageName,
      String adminSecretName,
      String repoSecretName,
      String encryptionSecretName,
      int replicaCount,
      List<String> clusterNames,
      String auxiliaryImagePath,
      String... auxiliaryImageName) {

    Domain domainCR = CommonMiiTestUtils.createDomainResource(domainResourceName, domNamespace,
        baseImageName, adminSecretName, repoSecretName,
        encryptionSecretName, replicaCount, clusterNames);
    int index = 0;
    for (String cmImageName: auxiliaryImageName) {
      AuxiliaryImage auxImage = new AuxiliaryImage().image(cmImageName).imagePullPolicy("IfNotPresent");
      //Only add the sourceWDTInstallHome and sourceModelHome for the first aux image.
      if (index == 0) {
        auxImage.sourceWDTInstallHome(auxiliaryImagePath + "/weblogic-deploy")
            .sourceModelHome(auxiliaryImagePath + "/models");
      } else {
        auxImage.sourceWDTInstallHome("none")
            .sourceModelHome("none");
      }
      domainCR.spec().configuration().model().withAuxiliaryImage(auxImage);
      index++;
    }
    return domainCR;
  }

  private static void createAuxiliaryImageWithDomainConfig(String imageName, String auxiliaryImagePath) {
    // create stage dir for auxiliary image
    Path aiPath = Paths.get(RESULTS_ROOT,
        ItMiiAuxiliaryImage40.class.getSimpleName(), "ai"
            + imageName.substring(imageName.length() - 1));
    assertDoesNotThrow(() -> FileUtils.deleteDirectory(aiPath.toFile()),
        "Delete directory failed");
    assertDoesNotThrow(() -> Files.createDirectories(aiPath),
        "Create directory failed");
    Path aiPathToFile =
        Paths.get(RESULTS_ROOT, ItMiiAuxiliaryImage40.class.getSimpleName(),
            "ai" + imageName.substring(imageName.length() - 1) + "/test.txt");
    String content = "1";
    assertDoesNotThrow(() -> Files.write(aiPathToFile, content.getBytes()),
        "Can't write to file " + aiPathToFile);

    // create models dir and copy model, archive files if any for image1
    Path modelsPath = Paths.get(aiPath.toString(), "models");
    assertDoesNotThrow(() -> Files.createDirectories(modelsPath));
    assertDoesNotThrow(() -> Files.copy(
        Paths.get(MODEL_DIR, MII_BASIC_WDT_MODEL_FILE),
        Paths.get(modelsPath.toString(), MII_BASIC_WDT_MODEL_FILE),
        StandardCopyOption.REPLACE_EXISTING));
    assertDoesNotThrow(() -> Files.copy(
        Paths.get(MODEL_DIR, "multi-model-one-ds.20.yaml"),
        Paths.get(modelsPath.toString(), "multi-model-one-ds.20.yaml"),
        StandardCopyOption.REPLACE_EXISTING));

    // build app
    assertTrue(buildAppArchive(defaultAppParams()
            .srcDirList(Collections.singletonList(MII_BASIC_APP_NAME))
            .appName(MII_BASIC_APP_NAME)),
        String.format("Failed to create app archive for %s", MII_BASIC_APP_NAME));

    // copy app archive to models
    assertDoesNotThrow(() -> Files.copy(
        Paths.get(ARCHIVE_DIR, MII_BASIC_APP_NAME + ".zip"),
        Paths.get(modelsPath.toString(), MII_BASIC_APP_NAME + ".zip"),
        StandardCopyOption.REPLACE_EXISTING));

    // unzip WDT installation file into work dir
    unzipWDTInstallationFile(aiPath.toString());

    // create image1 with model and wdt installation files
    createAuxiliaryImage(aiPath.toString(),
        Paths.get(RESOURCE_DIR, "auxiliaryimage", "Dockerfile").toString(), imageName, auxiliaryImagePath);

    // push image1 to repo for multi node cluster
    if (!DOMAIN_IMAGES_REPO.isEmpty()) {
      logger.info("docker push image {0} to registry {1}", imageName, DOMAIN_IMAGES_REPO);
      dockerLoginAndPushImageToRegistry(imageName);
    }
  }

  private static void createAuxiliaryImageWithJmsConfig(String imageName, String auxiliaryImagePath) {

    // create stage dir for second auxiliary image
    Path aiPath = Paths.get(RESULTS_ROOT, ItMiiAuxiliaryImage40.class.getSimpleName(),
        "ai" + imageName.substring(imageName.length() - 1));
    assertDoesNotThrow(() -> FileUtils.deleteDirectory(aiPath.toFile()),
        "Delete directory failed");
    assertDoesNotThrow(() -> Files.createDirectories(aiPath),
        "Create directory failed");

    // create models dir and copy model, archive files if any
    Path modelsPath = Paths.get(aiPath.toString(), "models");
    assertDoesNotThrow(() -> Files.createDirectories(modelsPath));
    Path aiPathToFile =
        Paths.get(RESULTS_ROOT, ItMiiAuxiliaryImage40.class.getSimpleName(),
            "ai" + imageName.substring(imageName.length() - 1) + "/test.txt");
    String content2 = "2";
    assertDoesNotThrow(() -> Files.write(aiPathToFile, content2.getBytes()),
        "Can't write to file " + aiPathToFile);
    assertDoesNotThrow(() -> Files.copy(
        Paths.get(MODEL_DIR, "/model.jms2.yaml"),
        Paths.get(modelsPath.toString(), "/model.jms2.yaml"),
        StandardCopyOption.REPLACE_EXISTING));

    // create image2 with model and wdt installation files
    createAuxiliaryImage(aiPath.toString(),
        Paths.get(RESOURCE_DIR, "auxiliaryimage", "Dockerfile").toString(), imageName, auxiliaryImagePath);

    // push image2 to repo for multi node cluster
    if (!DOMAIN_IMAGES_REPO.isEmpty()) {
      logger.info("docker push image {0} to registry {1}", imageName, DOMAIN_IMAGES_REPO);
      dockerLoginAndPushImageToRegistry(imageName);
    }

  }
}
