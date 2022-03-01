// Copyright (c) 2022, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.weblogic.kubernetes;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.OffsetDateTime;
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
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import static oracle.weblogic.kubernetes.TestConstants.ADMIN_PASSWORD_DEFAULT;
import static oracle.weblogic.kubernetes.TestConstants.ADMIN_USERNAME_DEFAULT;
import static oracle.weblogic.kubernetes.TestConstants.DOMAIN_IMAGES_REPO;
import static oracle.weblogic.kubernetes.TestConstants.DOMAIN_STATUS_CONDITION_FAILED_TYPE;
import static oracle.weblogic.kubernetes.TestConstants.DOMAIN_VERSION;
import static oracle.weblogic.kubernetes.TestConstants.KIND_REPO;
import static oracle.weblogic.kubernetes.TestConstants.MII_AUXILIARY_IMAGE_NAME;
import static oracle.weblogic.kubernetes.TestConstants.MII_BASIC_APP_NAME;
import static oracle.weblogic.kubernetes.TestConstants.MII_BASIC_IMAGE_TAG;
import static oracle.weblogic.kubernetes.TestConstants.MII_BASIC_WDT_MODEL_FILE;
import static oracle.weblogic.kubernetes.TestConstants.OCIR_SECRET_NAME;
import static oracle.weblogic.kubernetes.TestConstants.OPERATOR_RELEASE_NAME;
import static oracle.weblogic.kubernetes.TestConstants.RESULTS_ROOT;
import static oracle.weblogic.kubernetes.TestConstants.WDT_TEST_VERSION;
import static oracle.weblogic.kubernetes.TestConstants.WEBLOGIC_IMAGE_NAME;
import static oracle.weblogic.kubernetes.TestConstants.WEBLOGIC_IMAGE_TO_USE_IN_SPEC;
import static oracle.weblogic.kubernetes.actions.ActionConstants.ARCHIVE_DIR;
import static oracle.weblogic.kubernetes.actions.ActionConstants.DOWNLOAD_DIR;
import static oracle.weblogic.kubernetes.actions.ActionConstants.MODEL_DIR;
import static oracle.weblogic.kubernetes.actions.ActionConstants.RESOURCE_DIR;
import static oracle.weblogic.kubernetes.actions.ActionConstants.WDT_DOWNLOAD_FILENAME_DEFAULT;
import static oracle.weblogic.kubernetes.actions.TestActions.buildAppArchive;
import static oracle.weblogic.kubernetes.actions.TestActions.createDomainCustomResource;
import static oracle.weblogic.kubernetes.actions.TestActions.defaultAppParams;
import static oracle.weblogic.kubernetes.actions.TestActions.deleteImage;
import static oracle.weblogic.kubernetes.actions.TestActions.dockerTag;
import static oracle.weblogic.kubernetes.actions.TestActions.getDomainCustomResource;
import static oracle.weblogic.kubernetes.actions.TestActions.getOperatorPodName;
import static oracle.weblogic.kubernetes.actions.TestActions.getServiceNodePort;
import static oracle.weblogic.kubernetes.actions.TestActions.now;
import static oracle.weblogic.kubernetes.assertions.TestAssertions.doesDomainExist;
import static oracle.weblogic.kubernetes.assertions.TestAssertions.verifyRollingRestartOccurred;
import static oracle.weblogic.kubernetes.utils.CommonMiiTestUtils.createDomainResource40;
import static oracle.weblogic.kubernetes.utils.CommonMiiTestUtils.readFilesInPod;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.checkPodReadyAndServiceExists;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.checkServiceDoesNotExist;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.checkSystemResourceConfig;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.checkSystemResourceConfiguration;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.getDateAndTimeStamp;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.testUntil;
import static oracle.weblogic.kubernetes.utils.DomainUtils.checkDomainStatusConditionTypeExists;
import static oracle.weblogic.kubernetes.utils.DomainUtils.checkDomainStatusConditionTypeHasExpectedStatus;
import static oracle.weblogic.kubernetes.utils.DomainUtils.createDomainAndVerify;
import static oracle.weblogic.kubernetes.utils.DomainUtils.deleteDomainResource;
import static oracle.weblogic.kubernetes.utils.DomainUtils.patchDomainWithAuxiliaryImageAndVerify;
import static oracle.weblogic.kubernetes.utils.DomainUtils.verifyDomainStatusConditionTypeDoesNotExist;
import static oracle.weblogic.kubernetes.utils.FileUtils.copyFileFromPod;
import static oracle.weblogic.kubernetes.utils.FileUtils.replaceStringInFile;
import static oracle.weblogic.kubernetes.utils.FileUtils.unzipWDTInstallationFile;
import static oracle.weblogic.kubernetes.utils.ImageUtils.createOcirRepoSecret;
import static oracle.weblogic.kubernetes.utils.ImageUtils.dockerLoginAndPushImageToRegistry;
import static oracle.weblogic.kubernetes.utils.JobUtils.getIntrospectJobName;
import static oracle.weblogic.kubernetes.utils.K8sEvents.DOMAIN_FAILED;
import static oracle.weblogic.kubernetes.utils.K8sEvents.checkDomainEventContainsExpectedMsg;
import static oracle.weblogic.kubernetes.utils.LoggingUtil.checkPodLogContainsString;
import static oracle.weblogic.kubernetes.utils.OKDUtils.createRouteForOKD;
import static oracle.weblogic.kubernetes.utils.OperatorUtils.installAndVerifyOperator;
import static oracle.weblogic.kubernetes.utils.PatchDomainUtils.patchDomainResource;
import static oracle.weblogic.kubernetes.utils.PodUtils.checkPodDoesNotExist;
import static oracle.weblogic.kubernetes.utils.PodUtils.checkPodExists;
import static oracle.weblogic.kubernetes.utils.PodUtils.getExternalServicePodName;
import static oracle.weblogic.kubernetes.utils.PodUtils.getPodsWithTimeStamps;
import static oracle.weblogic.kubernetes.utils.PodUtils.verifyIntrospectorPodLogContainsExpectedErrorMsg;
import static oracle.weblogic.kubernetes.utils.SecretUtils.createSecretWithUsernamePassword;
import static oracle.weblogic.kubernetes.utils.ThreadSafeLogger.getLogger;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
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
  private static String wdtDomainNamespace = null;
  private static String errorpathDomainNamespace = null;
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
  private static final String miiAuxiliaryImage9 = MII_AUXILIARY_IMAGE_NAME + "-domain:" + MII_BASIC_IMAGE_TAG + "9";
  private static final String miiAuxiliaryImage10 = MII_AUXILIARY_IMAGE_NAME + "-domain:" + MII_BASIC_IMAGE_TAG + "10";
  private static final String miiAuxiliaryImage11 = MII_AUXILIARY_IMAGE_NAME + "-domain:" + MII_BASIC_IMAGE_TAG + "11";
  private static String errorPathAuxiliaryImage2 = MII_AUXILIARY_IMAGE_NAME + "-domain:" + MII_BASIC_IMAGE_TAG + "12";
  private static String errorPathAuxiliaryImage3 = MII_AUXILIARY_IMAGE_NAME + "-domain:" + MII_BASIC_IMAGE_TAG + "13";
  private static String errorPathAuxiliaryImage4 = MII_AUXILIARY_IMAGE_NAME + "-domain:" + MII_BASIC_IMAGE_TAG + "14";
  private static String errorPathAuxiliaryImage5 = MII_AUXILIARY_IMAGE_NAME + "-domain:" + MII_BASIC_IMAGE_TAG + "15";
  private static final String adminServerPodNameDomain1 = domainUid1 + "-admin-server";
  private static final String managedServerPrefixDomain1 = domainUid1 + "-managed-server";
  private static final int replicaCount = 2;
  private String adminSvcExtHost = null;
  private static String adminSvcExtHostDomain1 = null;
  private static String adminSecretName = "weblogic-credentials";
  private static String encryptionSecretName = "encryptionsecret";
  private static String opNamespace = null;
  private static String operatorPodName = null;

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
    errorpathDomainNamespace = namespaces.get(2);

    logger.info("Creating unique namespace for wdtDomainNamespace");
    assertNotNull(namespaces.get(3), "Namespace list is null");
    wdtDomainNamespace = namespaces.get(3);

    // install and verify operator
    installAndVerifyOperator(opNamespace, domainNamespace, errorpathDomainNamespace, wdtDomainNamespace);

    operatorPodName =
        assertDoesNotThrow(() -> getOperatorPodName(OPERATOR_RELEASE_NAME, opNamespace),
            "Can't get operator's pod name");

    // Create the repo secret to pull the image
    // this secret is used only for non-kind cluster
    createOcirRepoSecret(domainNamespace);
    createOcirRepoSecret(errorpathDomainNamespace);

    // create secret for admin credentials
    logger.info("Create secret for admin credentials");
    createSecretWithUsernamePassword(adminSecretName, domainNamespace,
        ADMIN_USERNAME_DEFAULT, ADMIN_PASSWORD_DEFAULT);

    // create encryption secret
    logger.info("Create encryption secret");
    createSecretWithUsernamePassword(encryptionSecretName, domainNamespace,
        "weblogicenc", "weblogicenc");

    createSecretWithUsernamePassword(adminSecretName, errorpathDomainNamespace,
        ADMIN_USERNAME_DEFAULT, ADMIN_PASSWORD_DEFAULT);

    // create encryption secret
    logger.info("Create encryption secret");
    createSecretWithUsernamePassword(encryptionSecretName, errorpathDomainNamespace,
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
   * Verify domain is rolling restarted.
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

    // get the map with server pods and their original creation timestamps
    Map podsWithTimeStamps = getPodsWithTimeStamps(domainNamespace, adminServerPodNameDomain1,
        managedServerPrefixDomain1, replicaCount);

    patchDomainWithAuxiliaryImageAndVerify(miiAuxiliaryImage1, miiAuxiliaryImage3, domainUid1, domainNamespace);

    // verify the server pods are rolling restarted and back to ready state
    logger.info("Verifying rolling restart occurred for domain {0} in namespace {1}",
        domainUid1, domainNamespace);
    assertTrue(verifyRollingRestartOccurred(podsWithTimeStamps, 1, domainNamespace),
        String.format("Rolling restart failed for domain %s in namespace %s", domainUid1, domainNamespace));

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
   * location. Verify domain events and operator log contains the expected error message.
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

    OffsetDateTime timestamp = now();

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
    checkPodLogContainsString(opNamespace, operatorPodName, errorMessage);

    // check the domain event contains the expected error message
    checkDomainEventContainsExpectedMsg(opNamespace, domainNamespace, domainUid, DOMAIN_FAILED,
        "Warning", timestamp, errorMessage);
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
    checkPodLogContainsString(opNamespace, operatorPodName, errorMessage);

  }

  /**
   * Negative test. Create a domain using auxiliary image with no model files at specified sourceModelHome
   * location. Verify domain events and operator log contains the expected error message.
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

    OffsetDateTime timestamp = now();

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

    // check the operator pod log contains the expected error message
    checkPodLogContainsString(opNamespace, operatorPodName, errorMessage);

    // check the domain event contains the expected error message
    checkDomainEventContainsExpectedMsg(opNamespace, domainNamespace, domainUid, DOMAIN_FAILED,
        "Warning", timestamp, errorMessage);

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
   * Negative Test to create domain without WDT binary.
   * Check the error message is in domain events and operator pod log.
   */
  @Test
  @DisplayName("Negative Test to create domain without WDT binary")
  void testErrorPathDomainMissingWDTBinary() {

    final String auxiliaryImagePath = "/auxiliary";
    final String domainUid2 = "domain7";
    final String adminServerPodName = domainUid2 + "-admin-server";
    final String managedServerPrefix = domainUid2 + "-managed-server";

    //In case the previous test failed, ensure the created domain in the same namespace is deleted.
    if (doesDomainExist(domainUid2, DOMAIN_VERSION, errorpathDomainNamespace)) {
      deleteDomainResource(errorpathDomainNamespace, domainUid2);
    }

    OffsetDateTime timestamp = now();

    // create stage dir for auxiliary image
    Path errorpathAIPath2 = Paths.get(RESULTS_ROOT, this.getClass().getSimpleName(), "errorpathauxiimage2");
    assertDoesNotThrow(() -> FileUtils.deleteDirectory(errorpathAIPath2.toFile()),
        "Delete directory failed");
    assertDoesNotThrow(() -> Files.createDirectories(errorpathAIPath2),
        "Create directory failed");

    // create models dir and copy model for image
    Path modelsPath2 = Paths.get(errorpathAIPath2.toString(), "models");
    assertDoesNotThrow(() -> Files.createDirectories(modelsPath2),
        "Create directory failed");
    assertDoesNotThrow(() -> Files.copy(
        Paths.get(MODEL_DIR, MII_BASIC_WDT_MODEL_FILE),
        Paths.get(modelsPath2.toString(), MII_BASIC_WDT_MODEL_FILE),
        StandardCopyOption.REPLACE_EXISTING), "Copy files failed");

    // create image with model and no wdt installation files
    // create image1 with model and wdt installation files
    createAuxiliaryImage(errorpathAIPath2.toString(),
        Paths.get(RESOURCE_DIR, "auxiliaryimage", "Dockerfile").toString(),
        errorPathAuxiliaryImage2);

    // push image to repo for multi node cluster
    if (!DOMAIN_IMAGES_REPO.isEmpty()) {
      logger.info("docker push image {0} to registry {1}", errorPathAuxiliaryImage2, DOMAIN_IMAGES_REPO);
      dockerLoginAndPushImageToRegistry(errorPathAuxiliaryImage2);
    }

    // create domain custom resource using auxiliary images
    logger.info("Creating domain custom resource with domainUid {0} and auxiliary image {1}",
        domainUid2, errorPathAuxiliaryImage2);

    Domain domainCR = createDomainResource40(domainUid2, errorpathDomainNamespace,
        WEBLOGIC_IMAGE_TO_USE_IN_SPEC, adminSecretName, OCIR_SECRET_NAME,
        encryptionSecretName, replicaCount, "cluster-1", auxiliaryImagePath,
        errorPathAuxiliaryImage2);

    // create domain and verify it is failed
    logger.info("Creating domain {0} with auxiliary image {1} in namespace {2}",
        domainUid2, errorPathAuxiliaryImage2, errorpathDomainNamespace);
    assertDoesNotThrow(() -> createDomainCustomResource(domainCR), "createDomainCustomResource throws Exception");

    // check the introspector pod log contains the expected error message
    String expectedErrorMsg = "The domain resource 'spec.domainHomeSourceType' is 'FromModel'  and "
        + "a WebLogic Deploy Tool (WDT) install is not located at  'spec.configuration.model.wdtInstallHome'  "
        + "which is currently set to '/aux/weblogic-deploy'";

    // check the domain event contains the expected error message
    checkDomainEventContainsExpectedMsg(opNamespace, errorpathDomainNamespace, domainUid2, DOMAIN_FAILED,
        "Warning", timestamp, expectedErrorMsg);

    // check there are no admin server and managed server pods and services created
    checkPodDoesNotExist(adminServerPodName, domainUid2, errorpathDomainNamespace);
    checkServiceDoesNotExist(adminServerPodName, errorpathDomainNamespace);
    for (int i = 1; i <= replicaCount; i++) {
      checkPodDoesNotExist(managedServerPrefix + i, domainUid2, domainNamespace);
      checkServiceDoesNotExist(managedServerPrefix + i, domainNamespace);
    }

    // check the operator pod log contains the expected error message
    checkPodLogContainsString(opNamespace, operatorPodName, expectedErrorMsg);
    // delete domain1
    deleteDomainResource(errorpathDomainNamespace, domainUid2);
  }

  /**
   * Negative Test to create domain without domain model file, the auxiliary image contains only sparse JMS config.
   * Check the error message is in domain events and operator pod log
   */
  @Test
  @DisplayName("Negative Test to create domain without domain model file, only having sparse JMS config")
  void testErrorPathDomainMissingDomainConfig() {
    final String domainUid2 = "domain7";
    final String adminServerPodName = domainUid2 + "-admin-server";
    final String managedServerPrefix = domainUid2 + "-managed-server";

    //In case the previous test failed, ensure the created domain in the same namespace is deleted.
    if (doesDomainExist(domainUid2, DOMAIN_VERSION, errorpathDomainNamespace)) {
      deleteDomainResource(errorpathDomainNamespace, domainUid2);
    }
    OffsetDateTime timestamp = now();

    final String auxiliaryImagePath = "/auxiliary";

    // create stage dir for auxiliary image
    Path errorpathAIPath3 = Paths.get(RESULTS_ROOT, this.getClass().getSimpleName(), "errorpathauxiimage3");
    assertDoesNotThrow(() -> FileUtils.deleteDirectory(errorpathAIPath3.toFile()),
        "Delete directory failed");
    assertDoesNotThrow(() -> Files.createDirectories(errorpathAIPath3),
        "Create directory failed");

    // create models dir and copy model for image
    Path modelsPath3 = Paths.get(errorpathAIPath3.toString(), "models");
    assertDoesNotThrow(() -> Files.createDirectories(modelsPath3),
        "Create directory failed");
    assertDoesNotThrow(() -> Files.copy(
        Paths.get(MODEL_DIR, "model.jms2.yaml"),
        Paths.get(modelsPath3.toString(), "model.jms2.yaml"),
        StandardCopyOption.REPLACE_EXISTING),
        "Copy files failed");

    // unzip WDT installation file into work dir
    unzipWDTInstallationFile(errorpathAIPath3.toString());

    // create image1 with model and wdt installation files
    createAuxiliaryImage(errorpathAIPath3.toString(),
        Paths.get(RESOURCE_DIR, "auxiliaryimage", "Dockerfile").toString(), errorPathAuxiliaryImage3);

    // push image to repo for multi node cluster
    if (!DOMAIN_IMAGES_REPO.isEmpty()) {
      logger.info("docker push image {0} to registry {1}", errorPathAuxiliaryImage3, DOMAIN_IMAGES_REPO);
      dockerLoginAndPushImageToRegistry(errorPathAuxiliaryImage3);
    }

    // create domain custom resource using auxiliary images
    logger.info("Creating domain custom resource with domainUid {0} and auxiliary image {1}",
        domainUid2, errorPathAuxiliaryImage3);

    Domain domainCR = createDomainResource40(domainUid2, errorpathDomainNamespace,
        WEBLOGIC_IMAGE_TO_USE_IN_SPEC, adminSecretName, OCIR_SECRET_NAME,
        encryptionSecretName, replicaCount, "cluster-1", auxiliaryImagePath,
        errorPathAuxiliaryImage3);

    // create domain and verify it is failed
    logger.info("Creating domain {0} with auxiliary image {1} in namespace {2}",
        domainUid2, errorPathAuxiliaryImage3, errorpathDomainNamespace);
    assertDoesNotThrow(() -> createDomainCustomResource(domainCR), "createDomainCustomResource throws Exception");

    // check the introspector pod log contains the expected error message
    String expectedErrorMsg =
        "createDomain did not find the required domainInfo section in the model file /aux/models/model.jms2.yaml";
    // check the domain event contains the expected error message
    checkDomainEventContainsExpectedMsg(opNamespace, errorpathDomainNamespace, domainUid2, DOMAIN_FAILED,
        "Warning", timestamp, expectedErrorMsg);

    // check the operator pod log contains the expected error message
    checkPodLogContainsString(opNamespace, operatorPodName, expectedErrorMsg);

    // check there are no admin server and managed server pods and services created
    checkPodDoesNotExist(adminServerPodName, domainUid2, errorpathDomainNamespace);
    checkServiceDoesNotExist(adminServerPodName, errorpathDomainNamespace);
    for (int i = 1; i <= replicaCount; i++) {
      checkPodDoesNotExist(managedServerPrefix + i, domainUid2, errorpathDomainNamespace);
      checkServiceDoesNotExist(managedServerPrefix + i, errorpathDomainNamespace);
    }

    // delete domain
    deleteDomainResource(errorpathDomainNamespace, domainUid2);
  }

  /**
   * Negative Test to create domain with file , created by user tester with permission read only
   * and not accessible by oracle user in auxiliary image
   * via provided Dockerfile.
   * Check the error message is in introspector pod log, domain events and operator pod log.
   */
  @Test
  @Disabled("Regression bug, test fails")
  @DisplayName("Negative Test to create domain with file in auxiliary image not accessible by oracle user")
  void testErrorPathFilePermission() {
    final String domainUid2 = "domain8";
    final String adminServerPodName = domainUid2 + "-admin-server";
    final String managedServerPrefix = domainUid2 + "-managed-server";
    final String auxiliaryImagePath = "/auxiliary";

    //In case the previous test failed, ensure the created domain in the same namespace is deleted.
    if (doesDomainExist(domainUid2, DOMAIN_VERSION, errorpathDomainNamespace)) {
      deleteDomainResource(errorpathDomainNamespace, domainUid2);
    }
    OffsetDateTime timestamp = now();

    // create stage dir for auxiliary image
    Path errorpathAIPath1 = Paths.get(RESULTS_ROOT, this.getClass().getSimpleName(), "errorpathauxiimage4");
    assertDoesNotThrow(() -> FileUtils.deleteDirectory(errorpathAIPath1.toFile()),
        "Can't delete directory");
    assertDoesNotThrow(() -> Files.createDirectories(errorpathAIPath1),
        "Can't create directory");

    // create models dir and copy model for image
    Path modelsPath1 = Paths.get(errorpathAIPath1.toString(), "models");
    assertDoesNotThrow(() -> Files.createDirectories(modelsPath1),
        "Can't create directory");
    assertDoesNotThrow(() -> Files.copy(
        Paths.get(MODEL_DIR, MII_BASIC_WDT_MODEL_FILE),
        Paths.get(modelsPath1.toString(), MII_BASIC_WDT_MODEL_FILE),
        StandardCopyOption.REPLACE_EXISTING),
        "Can't copy files");

    Path errorpathAIPathToFile =
        Paths.get(RESULTS_ROOT, this.getClass().getSimpleName(), "errorpathauxiimage4/models/test1.properties");
    String content = "some=text ";
    assertDoesNotThrow(() -> Files.write(errorpathAIPathToFile, content.getBytes()),
        "Can't write to file " + errorpathAIPathToFile);

    // build app
    assertTrue(buildAppArchive(defaultAppParams()
            .srcDirList(Collections.singletonList(MII_BASIC_APP_NAME))
            .appName(MII_BASIC_APP_NAME)),
        String.format("Failed to create app archive for %s", MII_BASIC_APP_NAME));

    // copy app archive to models
    assertDoesNotThrow(() -> Files.copy(
        Paths.get(ARCHIVE_DIR, MII_BASIC_APP_NAME + ".zip"),
        Paths.get(modelsPath1.toString(), MII_BASIC_APP_NAME + ".zip"),
        StandardCopyOption.REPLACE_EXISTING), "Can't copy files");

    // unzip WDT installation file into work dir
    unzipWDTInstallationFile(errorpathAIPath1.toString());

    // create image with model and wdt installation files
    createAuxiliaryImage(errorpathAIPath1.toString(),
        Paths.get(RESOURCE_DIR, "auxiliaryimage", "/negative/Dockerfile").toString(), errorPathAuxiliaryImage4);

    // push image to repo for multi node cluster
    if (!DOMAIN_IMAGES_REPO.isEmpty()) {
      logger.info("docker push image {0} to registry {1}", errorPathAuxiliaryImage4, DOMAIN_IMAGES_REPO);
      dockerLoginAndPushImageToRegistry(errorPathAuxiliaryImage4);
    }

    // create domain custom resource using auxiliary images
    logger.info("Creating domain custom resource with domainUid {0} and auxiliary image {1}",
        domainUid2, errorPathAuxiliaryImage4);
    Domain domainCR = createDomainResource40(domainUid2, errorpathDomainNamespace,
        WEBLOGIC_IMAGE_TO_USE_IN_SPEC, adminSecretName, OCIR_SECRET_NAME,
        encryptionSecretName, replicaCount, "cluster-1", auxiliaryImagePath,
        errorPathAuxiliaryImage4);

    // create domain and verify it is failed
    logger.info("Creating domain {0} with auxiliary image {1} in namespace {2}",
        domainUid2, errorPathAuxiliaryImage4, errorpathDomainNamespace);
    assertDoesNotThrow(() -> createDomainCustomResource(domainCR), "createDomainCustomResource throws Exception");

    // check the introspector pod log contains the expected error message
    String expectedErrorMsg = "cp: can't open '/aux/models/test1.properties': Permission denied";

    // check the domain event contains the expected error message
    checkDomainEventContainsExpectedMsg(opNamespace, errorpathDomainNamespace, domainUid2, DOMAIN_FAILED,
        "Warning", timestamp, expectedErrorMsg);

    // check the operator pod log contains the expected error message
    checkPodLogContainsString(opNamespace, operatorPodName, expectedErrorMsg);

    // check there are no admin server and managed server pods and services not created
    checkPodDoesNotExist(adminServerPodName, domainUid2, errorpathDomainNamespace);
    checkServiceDoesNotExist(adminServerPodName, errorpathDomainNamespace);
    for (int i = 1; i <= replicaCount; i++) {
      checkPodDoesNotExist(managedServerPrefix + i, domainUid2, errorpathDomainNamespace);
      checkServiceDoesNotExist(managedServerPrefix + i, errorpathDomainNamespace);
    }

    // delete domain1
    deleteDomainResource(errorpathDomainNamespace, domainUid2);
  }

  /**
   * Create a domain using multiple auxiliary images.
   * One auxiliary image (image1) contains the domain configuration and
   * another auxiliary image (image2) with WDT only,
   * update WDT version by patching with another auxiliary image (image3)
   * and verify the domain is running.
   */
  @Test
  @DisplayName("Test to update WDT version using  auxiliary images")
  void testUpdateWDTVersionUsingMultipleAuxiliaryImages() {

    // admin/managed server name here should match with model yaml
    final String auxiliaryImagePath = "/auxiliary";
    final String domainUid = "domain7";
    final String adminServerPodName = domainUid + "-admin-server";
    final String managedServerPrefix = domainUid + "-managed-server";

    // Create the repo secret to pull the image
    // this secret is used only for non-kind cluster
    createOcirRepoSecret(wdtDomainNamespace);

    // create secret for admin credentials
    logger.info("Create secret for admin credentials");
    String adminSecretName = "weblogic-credentials";
    createSecretWithUsernamePassword(adminSecretName, wdtDomainNamespace,
        ADMIN_USERNAME_DEFAULT, ADMIN_PASSWORD_DEFAULT);

    // create encryption secret
    logger.info("Create encryption secret");
    String encryptionSecretName = "encryptionsecret";
    createSecretWithUsernamePassword(encryptionSecretName, wdtDomainNamespace,
        "weblogicenc", "weblogicenc");

    // create stage dir for first auxiliary image containing domain configuration
    Path multipleAIPath1 = Paths.get(RESULTS_ROOT, this.getClass().getSimpleName(), "multipleauxiliaryimage1");
    assertDoesNotThrow(() -> FileUtils.deleteDirectory(multipleAIPath1.toFile()),
        "Delete directory failed");
    assertDoesNotThrow(() -> Files.createDirectories(multipleAIPath1),
        "Failed to create directory");

    // create models dir and copy model, archive files if any for image1
    Path modelsPath1 = Paths.get(multipleAIPath1.toString(), "models");
    assertDoesNotThrow(() -> Files.createDirectories(modelsPath1));
    assertDoesNotThrow(() -> Files.copy(
        Paths.get(MODEL_DIR, MII_BASIC_WDT_MODEL_FILE),
        Paths.get(modelsPath1.toString(), MII_BASIC_WDT_MODEL_FILE),
        StandardCopyOption.REPLACE_EXISTING), "Failed to copy file");
    assertDoesNotThrow(() -> Files.copy(
        Paths.get(MODEL_DIR, "multi-model-one-ds.20.yaml"),
        Paths.get(modelsPath1.toString(), "multi-model-one-ds.20.yaml"),
        StandardCopyOption.REPLACE_EXISTING), "Failed to copy file");

    // build app
    assertTrue(buildAppArchive(defaultAppParams()
            .srcDirList(Collections.singletonList(MII_BASIC_APP_NAME))
            .appName(MII_BASIC_APP_NAME)),
        String.format("Failed to create app archive for %s", MII_BASIC_APP_NAME));

    // copy app archive to models
    assertDoesNotThrow(() -> Files.copy(
        Paths.get(ARCHIVE_DIR, MII_BASIC_APP_NAME + ".zip"),
        Paths.get(modelsPath1.toString(), MII_BASIC_APP_NAME + ".zip"),
        StandardCopyOption.REPLACE_EXISTING), "Failed to copy file");

    // create image1 with domain configuration only
    createAuxiliaryImage(multipleAIPath1.toString(),
        Paths.get(RESOURCE_DIR, "auxiliaryimage", "Dockerfile").toString(), miiAuxiliaryImage9);

    // push image1 to repo for multi node cluster
    if (!DOMAIN_IMAGES_REPO.isEmpty()) {
      logger.info("docker push image {0} to registry {1}", miiAuxiliaryImage9, DOMAIN_IMAGES_REPO);
      dockerLoginAndPushImageToRegistry(miiAuxiliaryImage9);
    }

    // create stage dir for second auxiliary image
    Path multipleAIPath2 = Paths.get(RESULTS_ROOT, this.getClass().getSimpleName(), "multipleauxiliaryimage2");
    assertDoesNotThrow(() -> FileUtils.deleteDirectory(multipleAIPath2.toFile()),
        "Delete directory failed");
    assertDoesNotThrow(() -> Files.createDirectories(multipleAIPath2), "Create directory failed");

    Path modelsPath2 = Paths.get(multipleAIPath2.toString(), "models");
    assertDoesNotThrow(() -> Files.createDirectories(modelsPath2), "Create directory failed");

    // unzip older version WDT installation file into work dir
    String wdtURL = "https://github.com/oracle/weblogic-deploy-tooling/releases/download/release-"
        + WDT_TEST_VERSION + "/"
        + WDT_DOWNLOAD_FILENAME_DEFAULT;
    unzipWDTInstallationFile(multipleAIPath2.toString(), wdtURL,
        DOWNLOAD_DIR + "/ver" + WDT_TEST_VERSION);

    // create image2 with wdt installation files only
    createAuxiliaryImage(multipleAIPath2.toString(),
        Paths.get(RESOURCE_DIR, "auxiliaryimage", "Dockerfile").toString(), miiAuxiliaryImage10);

    // push image2 to repo for multi node cluster
    if (!DOMAIN_IMAGES_REPO.isEmpty()) {
      logger.info("docker push image {0} to registry {1}", miiAuxiliaryImage10, DOMAIN_IMAGES_REPO);
      dockerLoginAndPushImageToRegistry(miiAuxiliaryImage10);
    }

    // create stage dir for third auxiliary image with latest wdt installation files only
    Path multipleAIPath3 = Paths.get(RESULTS_ROOT, this.getClass().getSimpleName(), "multipleauxiliaryimage3");
    assertDoesNotThrow(() -> FileUtils.deleteDirectory(multipleAIPath3.toFile()),
        "Delete directory failed");
    assertDoesNotThrow(() -> Files.createDirectories(multipleAIPath3),
        "Create directory failed");

    Path modelsPath3 = Paths.get(multipleAIPath3.toString(), "models");
    assertDoesNotThrow(() -> Files.createDirectories(modelsPath3),
        "Create directory failed");

    // unzip WDT installation file into work dir
    unzipWDTInstallationFile(multipleAIPath3.toString());

    // create image3 with newest version of wdt installation files
    createAuxiliaryImage(multipleAIPath3.toString(),
        Paths.get(RESOURCE_DIR, "auxiliaryimage", "Dockerfile").toString(), miiAuxiliaryImage11);

    // push image3 to repo for multi node cluster
    if (!DOMAIN_IMAGES_REPO.isEmpty()) {
      logger.info("docker push image {0} to registry {1}", miiAuxiliaryImage6, DOMAIN_IMAGES_REPO);
      dockerLoginAndPushImageToRegistry(miiAuxiliaryImage11);
    }

    // create domain custom resource using 2 auxiliary images ( image1, image2)
    logger.info("Creating domain custom resource with domainUid {0} and auxiliary images {1} {2}",
        domainUid, miiAuxiliaryImage9, miiAuxiliaryImage10);
    Domain domainCR = createDomainResource40(domainUid, wdtDomainNamespace,
        WEBLOGIC_IMAGE_TO_USE_IN_SPEC, adminSecretName, OCIR_SECRET_NAME,
        encryptionSecretName, replicaCount, "cluster-1", auxiliaryImagePath,
        miiAuxiliaryImage9, miiAuxiliaryImage10);

    // create domain and verify its running
    logger.info("Creating domain {0} with auxiliary images {1} {2} in namespace {3}",
        domainUid, miiAuxiliaryImage9, miiAuxiliaryImage10, wdtDomainNamespace);
    createDomainAndVerify(domainUid, domainCR, wdtDomainNamespace,
        adminServerPodName, managedServerPrefix, replicaCount);

    //create router for admin service on OKD in wdtDomainNamespace
    adminSvcExtHost = createRouteForOKD(getExternalServicePodName(adminServerPodName), wdtDomainNamespace);
    logger.info("admin svc host = {0}", adminSvcExtHost);

    // check configuration for DataSource in the running domain
    int adminServiceNodePort
        = getServiceNodePort(wdtDomainNamespace, getExternalServicePodName(adminServerPodName), "default");
    assertNotEquals(-1, adminServiceNodePort, "admin server default node port is not valid");
    testUntil(
        () -> checkSystemResourceConfig(adminSvcExtHost, adminServiceNodePort,
            "JDBCSystemResources/TestDataSource/JDBCResource/JDBCDriverParams",
            "jdbc:oracle:thin:@\\/\\/xxx.xxx.x.xxx:1521\\/ORCLCDB"),
        logger,
        "Checking for adminSvcExtHost: {0} or adminServiceNodePort: {1} if resourceName: {2} has the right value",
        adminSvcExtHost,
        adminServiceNodePort,
        "JDBCSystemResources/TestDataSource/JDBCResource/JDBCDriverParams");
    logger.info("Found the DataResource configuration");
    //check WDT version in the image equals the  provided WDT_TEST_VERSION
    assertDoesNotThrow(() -> {
      String wdtVersion = checkWDTVersion(wdtDomainNamespace, "/aux", adminServerPodName);
      assertEquals("WebLogic Deploy Tooling " + WDT_TEST_VERSION, wdtVersion,
          " Used WDT in the auxiliary image does not match the expected");
    }, "Can't retrieve wdt version file or version does match the expected");

    //updating wdt to latest version by patching the domain with image3
    patchDomainWithAuxiliaryImageAndVerify(miiAuxiliaryImage10, miiAuxiliaryImage11, domainUid, wdtDomainNamespace);

    //check that WDT version is changed
    assertDoesNotThrow(() -> {
      String wdtVersion = checkWDTVersion(wdtDomainNamespace, "/aux", adminServerPodName);
      assertNotEquals("WebLogic Deploy Tooling " + WDT_TEST_VERSION,wdtVersion,
          " Used WDT in the auxiliary image was not updated");
    }, "Can't retrieve wdt version file "
        + "or wdt was not updated after patching with auxiliary image");

    // check configuration for DataSource in the running domain
    assertNotEquals(-1, adminServiceNodePort, "admin server default node port is not valid");
    testUntil(
        () -> checkSystemResourceConfig(adminSvcExtHost, adminServiceNodePort,
            "JDBCSystemResources/TestDataSource/JDBCResource/JDBCDriverParams",
            "jdbc:oracle:thin:@\\/\\/xxx.xxx.x.xxx:1521\\/ORCLCDB"),
        logger,
        "Checking for adminSvcExtHost: {0} or adminServiceNodePort: {1} if resourceName: {2} has the right value",
        adminSvcExtHost,
        adminServiceNodePort,
        "JDBCSystemResources/TestDataSource/JDBCResource/JDBCDriverParams");
    logger.info("Found the DataResource configuration");

  }

  /**
   * Create a domain using auxiliary image that doesn't exist or have issues to pull and verify the domain status
   * reports the error. Patch the domain with correct image and verify the introspector job completes successfully.
   */
  @Test
  @DisplayName("Test to create domain using an auxiliary image that doesn't exist and check domain status")
  void testDomainStatusErrorPullingAI() {
    // admin/managed server name here should match with model yaml
    final String auxiliaryImagePath = "/auxiliary";
    final String domainUid = "domain9";
    final String adminServerPodName = domainUid + "-admin-server";
    final String managedServerPrefix = domainUid + "-managed-server";
    final String aiThatDoesntExist = miiAuxiliaryImage1 + "100";

    // create domain custom resource using the auxiliary image that doesn't exist
    logger.info("Creating domain custom resource with domainUid {0} and auxiliary images {1}",
        domainUid, aiThatDoesntExist);
    Domain domainCR = createDomainResourceWithAuxiliaryImage40(domainUid, domainNamespace,
        WEBLOGIC_IMAGE_TO_USE_IN_SPEC, adminSecretName, OCIR_SECRET_NAME,
        encryptionSecretName, replicaCount, List.of("cluster-1"), auxiliaryImagePath,
        aiThatDoesntExist);
    // createDomainResource util method sets 600 for activeDeadlineSeconds which is too long to verify
    // introspector re-run in this use case
    domainCR.getSpec().configuration().introspectorJobActiveDeadlineSeconds(120L);

    logger.info("Creating domain custom resource for domainUid {0} in namespace {1}",
        domainUid, domainNamespace);
    assertTrue(assertDoesNotThrow(() -> createDomainCustomResource(domainCR),
            String.format("Create domain custom resource failed with ApiException for %s in namespace %s",
                domainUid, domainNamespace)),
        String.format("Create domain custom resource failed with ApiException for %s in namespace %s",
            domainUid, domainNamespace));

    // verify the condition type Failed exists
    checkDomainStatusConditionTypeExists(domainUid, domainNamespace, DOMAIN_STATUS_CONDITION_FAILED_TYPE);
    // verify the condition Failed type has status True
    checkDomainStatusConditionTypeHasExpectedStatus(domainUid, domainNamespace,
        DOMAIN_STATUS_CONDITION_FAILED_TYPE, "True");

    // patch the domain with correct image which exists
    patchDomainWithAuxiliaryImageAndVerify(aiThatDoesntExist, miiAuxiliaryImage1, domainUid,
        domainNamespace, false);

    // verify there is no status condition type Failed
    verifyDomainStatusConditionTypeDoesNotExist(domainUid, domainNamespace, DOMAIN_STATUS_CONDITION_FAILED_TYPE);

    //verify the introspector pod is created and runs
    String introspectPodNameBase = getIntrospectJobName(domainUid);

    checkPodExists(introspectPodNameBase, domainUid, domainNamespace);
    checkPodDoesNotExist(introspectPodNameBase, domainUid, domainNamespace);

    // check that admin service/pod exists in the domain namespace
    logger.info("Checking that admin service/pod {0} exists in namespace {1}",
        adminServerPodName, domainNamespace);
    checkPodReadyAndServiceExists(adminServerPodName, domainUid, domainNamespace);

    for (int i = 1; i <= replicaCount; i++) {
      String managedServerPodName = managedServerPrefix + i;

      // check that ms service/pod exists in the domain namespace
      logger.info("Checking that clustered ms service/pod {0} exists in namespace {1}",
          managedServerPodName, domainNamespace);
      checkPodReadyAndServiceExists(managedServerPodName, domainUid, domainNamespace);
    }
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
    deleteImage(errorPathAuxiliaryImage2);
    deleteImage(errorPathAuxiliaryImage3);
    deleteImage(errorPathAuxiliaryImage4);
    deleteImage(errorPathAuxiliaryImage5);
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

  private String checkWDTVersion(String domainNamespace, String auxiliaryImagePath,
                                 String adminServerPodName) throws Exception {
    assertDoesNotThrow(() ->
        FileUtils.deleteQuietly(Paths.get(RESULTS_ROOT, this.getClass().getSimpleName(), "/WDTversion.txt").toFile()));
    assertDoesNotThrow(() -> copyFileFromPod(domainNamespace,
        adminServerPodName, "weblogic-server",
        auxiliaryImagePath + "/weblogic-deploy/VERSION.txt",
        Paths.get(RESULTS_ROOT, this.getClass().getSimpleName(), "/WDTversion.txt")),
        " Can't find file in the pod, or failed to copy");


    return Files.readAllLines(Paths.get(RESULTS_ROOT, this.getClass().getSimpleName(), "/WDTversion.txt")).get(0);
  }

}
