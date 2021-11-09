// Copyright (c) 2021, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.weblogic.kubernetes;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.OffsetDateTime;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import io.kubernetes.client.custom.V1Patch;
import io.kubernetes.client.openapi.ApiException;
import io.kubernetes.client.openapi.models.V1Pod;
import oracle.weblogic.domain.AuxiliaryImage;
import oracle.weblogic.domain.Domain;
import oracle.weblogic.kubernetes.actions.impl.primitive.Command;
import oracle.weblogic.kubernetes.actions.impl.primitive.CommandParams;
import oracle.weblogic.kubernetes.annotations.IntegrationTest;
import oracle.weblogic.kubernetes.annotations.Namespaces;
import oracle.weblogic.kubernetes.logging.LoggingFacade;
import org.apache.commons.io.FileUtils;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import static oracle.weblogic.kubernetes.TestConstants.ADMIN_PASSWORD_DEFAULT;
import static oracle.weblogic.kubernetes.TestConstants.ADMIN_USERNAME_DEFAULT;
import static oracle.weblogic.kubernetes.TestConstants.DOMAIN_IMAGES_REPO;
import static oracle.weblogic.kubernetes.TestConstants.K8S_NODEPORT_HOST;
import static oracle.weblogic.kubernetes.TestConstants.KIND_REPO;
import static oracle.weblogic.kubernetes.TestConstants.MII_AUXILIARY_IMAGE_NAME;
import static oracle.weblogic.kubernetes.TestConstants.MII_BASIC_APP_NAME;
import static oracle.weblogic.kubernetes.TestConstants.MII_BASIC_IMAGE_TAG;
import static oracle.weblogic.kubernetes.TestConstants.MII_BASIC_WDT_MODEL_FILE;
import static oracle.weblogic.kubernetes.TestConstants.OCIR_SECRET_NAME;
import static oracle.weblogic.kubernetes.TestConstants.OKD;
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
import static oracle.weblogic.kubernetes.actions.TestActions.getPod;
import static oracle.weblogic.kubernetes.actions.TestActions.getPodLog;
import static oracle.weblogic.kubernetes.actions.TestActions.getServiceNodePort;
import static oracle.weblogic.kubernetes.actions.TestActions.now;
import static oracle.weblogic.kubernetes.actions.TestActions.patchDomainCustomResource;
import static oracle.weblogic.kubernetes.assertions.TestAssertions.secretExists;
import static oracle.weblogic.kubernetes.assertions.TestAssertions.verifyRollingRestartOccurred;
import static oracle.weblogic.kubernetes.utils.CommonMiiTestUtils.createDomainResource;
import static oracle.weblogic.kubernetes.utils.CommonMiiTestUtils.verifyPodsNotRolled;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.checkPodReadyAndServiceExists;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.checkServiceDoesNotExist;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.checkSystemResourceConfig;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.getDateAndTimeStamp;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.testUntil;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.verifyCommandResultContainsMsg;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.verifySystemResourceConfiguration;
import static oracle.weblogic.kubernetes.utils.DomainUtils.createDomainAndVerify;
import static oracle.weblogic.kubernetes.utils.DomainUtils.deleteDomainResource;
import static oracle.weblogic.kubernetes.utils.FileUtils.copyFileFromPod;
import static oracle.weblogic.kubernetes.utils.FileUtils.replaceStringInFile;
import static oracle.weblogic.kubernetes.utils.FileUtils.unzipWDTInstallationFile;
import static oracle.weblogic.kubernetes.utils.ImageUtils.createOcirRepoSecret;
import static oracle.weblogic.kubernetes.utils.ImageUtils.dockerLoginAndPushImageToRegistry;
import static oracle.weblogic.kubernetes.utils.JobUtils.getIntrospectJobName;
import static oracle.weblogic.kubernetes.utils.K8sEvents.DOMAIN_PROCESSING_FAILED;
import static oracle.weblogic.kubernetes.utils.K8sEvents.checkDomainEventContainsExpectedMsg;
import static oracle.weblogic.kubernetes.utils.LoggingUtil.checkPodLogContainsString;
import static oracle.weblogic.kubernetes.utils.OKDUtils.createRouteForOKD;
import static oracle.weblogic.kubernetes.utils.OperatorUtils.installAndVerifyOperator;
import static oracle.weblogic.kubernetes.utils.PatchDomainUtils.patchDomainResource;
import static oracle.weblogic.kubernetes.utils.PodUtils.checkPodDoesNotExist;
import static oracle.weblogic.kubernetes.utils.PodUtils.checkPodReady;
import static oracle.weblogic.kubernetes.utils.PodUtils.getExternalServicePodName;
import static oracle.weblogic.kubernetes.utils.PodUtils.getPodCreationTime;
import static oracle.weblogic.kubernetes.utils.PodUtils.getPodsWithTimeStamps;
import static oracle.weblogic.kubernetes.utils.SecretUtils.createSecretWithUsernamePassword;
import static oracle.weblogic.kubernetes.utils.ThreadSafeLogger.getLogger;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("Test to create model in image domain using auxiliary image")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@IntegrationTest
class ItMiiAuxiliaryImage {

  private static String opNamespace = null;
  private static String domainNamespace = null;
  private static String errorpathDomainNamespace = null;
  private static String wdtDomainNamespace = null;
  private static LoggingFacade logger = null;
  private String domainUid = "domain1";
  private static String miiAuxiliaryImage1 = MII_AUXILIARY_IMAGE_NAME + ":" + MII_BASIC_IMAGE_TAG + "1";
  private static String miiAuxiliaryImage2 = MII_AUXILIARY_IMAGE_NAME + ":" + MII_BASIC_IMAGE_TAG + "2";
  private static String miiAuxiliaryImage3 = MII_AUXILIARY_IMAGE_NAME + ":" + MII_BASIC_IMAGE_TAG + "3";
  private static String miiAuxiliaryImage4 = MII_AUXILIARY_IMAGE_NAME + ":" + MII_BASIC_IMAGE_TAG + "4";
  private static String miiAuxiliaryImage5 = MII_AUXILIARY_IMAGE_NAME + ":" + MII_BASIC_IMAGE_TAG + "5";
  private static String miiAuxiliaryImage6 = MII_AUXILIARY_IMAGE_NAME + ":" + MII_BASIC_IMAGE_TAG + "6";
  private static String errorPathAuxiliaryImage1 = MII_AUXILIARY_IMAGE_NAME + ":errorpathimage1";
  private static String errorPathAuxiliaryImage2 = MII_AUXILIARY_IMAGE_NAME + ":errorpathimage2";
  private static String errorPathAuxiliaryImage3 = MII_AUXILIARY_IMAGE_NAME + ":errorpathimage3";
  private static String errorPathAuxiliaryImage4 = MII_AUXILIARY_IMAGE_NAME + ":errorpathimage4";
  private static Map<String, OffsetDateTime> podsWithTimeStamps = null;
  private final String adminServerPodName = domainUid + "-admin-server";
  private final String managedServerPrefix = domainUid + "-managed-server";
  private final int replicaCount = 2;
  private String adminSecretName = "weblogic-credentials";
  private String encryptionSecretName = "encryptionsecret";
  private String adminSvcExtHost = null;

  /**
   * Install Operator.
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
  }


  /**
   * Create a domain using multiple auxiliary images. One auxiliary image containing the domain configuration and
   * another auxiliary image with JMS system resource, verify the domain is running and JMS resource is added.
   */
  @Test
  @Order(1)
  @DisplayName("Test to create domain using multiple auxiliary images")
  void testCreateDomainUsingMultipleAuxiliaryImages() {

    // admin/managed server name here should match with model yaml
    final String auxiliaryImageVolumeName = "auxiliaryImageVolume1";
    final String auxiliaryImagePath = "/auxiliary";

    // Create the repo secret to pull the image
    // this secret is used only for non-kind cluster
    createOcirRepoSecret(domainNamespace);

    // create secret for admin credentials
    logger.info("Create secret for admin credentials");
    String adminSecretName = "weblogic-credentials";
    createSecretWithUsernamePassword(adminSecretName, domainNamespace,
        ADMIN_USERNAME_DEFAULT, ADMIN_PASSWORD_DEFAULT);

    // create encryption secret
    logger.info("Create encryption secret");
    String encryptionSecretName = "encryptionsecret";
    createSecretWithUsernamePassword(encryptionSecretName, domainNamespace,
        "weblogicenc", "weblogicenc");

    // create stage dir for first auxiliary image with image1
    Path multipleAIPath1 = Paths.get(RESULTS_ROOT, "multipleauxiliaryimage1");
    assertDoesNotThrow(() -> FileUtils.deleteDirectory(multipleAIPath1.toFile()),
        "Delete directory failed");
    assertDoesNotThrow(() -> Files.createDirectories(multipleAIPath1),
        "Create directory failed");
    Path multipleAIPathToFile1 = Paths.get(RESULTS_ROOT, "multipleauxiliaryimage1/test.txt");
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
    assertDoesNotThrow(() -> Files.copy(
        Paths.get(MODEL_DIR, "multi-model-one-ds.20.yaml"),
        Paths.get(modelsPath1.toString(), "multi-model-one-ds.20.yaml"),
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
    unzipWDTInstallationFile(multipleAIPath1.toString());

    // create image1 with model and wdt installation files
    createAuxiliaryImage(multipleAIPath1.toString(),
        Paths.get(RESOURCE_DIR, "auxiliaryimage", "Dockerfile").toString(), miiAuxiliaryImage1);

    // push image1 to repo for multi node cluster
    if (!DOMAIN_IMAGES_REPO.isEmpty()) {
      logger.info("docker push image {0} to registry {1}", miiAuxiliaryImage1, DOMAIN_IMAGES_REPO);
      dockerLoginAndPushImageToRegistry(miiAuxiliaryImage1);
    }

    // create stage dir for second auxiliary image with image2
    Path multipleAIPath2 = Paths.get(RESULTS_ROOT, "multipleauxiliaryimage2");
    assertDoesNotThrow(() -> FileUtils.deleteDirectory(multipleAIPath2.toFile()),
        "Delete directory failed");
    assertDoesNotThrow(() -> Files.createDirectories(multipleAIPath2),
        "Create directory failed");

    // create models dir and copy model, archive files if any
    Path modelsPath2 = Paths.get(multipleAIPath2.toString(), "models");
    assertDoesNotThrow(() -> Files.createDirectories(modelsPath2));
    Path multipleAIPathToFile2 = Paths.get(RESULTS_ROOT, "multipleauxiliaryimage2/test.txt");
    String content2 = "2";
    assertDoesNotThrow(() -> Files.write(multipleAIPathToFile2, content2.getBytes()),
        "Can't write to file " + multipleAIPathToFile2);
    assertDoesNotThrow(() -> Files.copy(
        Paths.get(MODEL_DIR, "/model.jms2.yaml"),
        Paths.get(modelsPath2.toString(), "/model.jms2.yaml"),
        StandardCopyOption.REPLACE_EXISTING));

    // create image2 with model and wdt installation files
    createAuxiliaryImage(multipleAIPath2.toString(),
        Paths.get(RESOURCE_DIR, "auxiliaryimage", "Dockerfile").toString(), miiAuxiliaryImage2);

    // push image2 to repo for multi node cluster
    if (!DOMAIN_IMAGES_REPO.isEmpty()) {
      logger.info("docker push image {0} to registry {1}", miiAuxiliaryImage2, DOMAIN_IMAGES_REPO);
      dockerLoginAndPushImageToRegistry(miiAuxiliaryImage2);
    }

    // create domain custom resource using 2 auxiliary images
    logger.info("Creating domain custom resource with domainUid {0} and auxiliary images {1} {2}",
        domainUid, miiAuxiliaryImage1, miiAuxiliaryImage2);
    Domain domainCR = createDomainResource(domainUid, domainNamespace,
        WEBLOGIC_IMAGE_TO_USE_IN_SPEC, adminSecretName, OCIR_SECRET_NAME,
        encryptionSecretName, replicaCount, "cluster-1", auxiliaryImagePath,
        auxiliaryImageVolumeName, miiAuxiliaryImage1, miiAuxiliaryImage2);

    // create domain and verify its running
    logger.info("Creating domain {0} with auxiliary images {1} {2} in namespace {3}",
        domainUid, miiAuxiliaryImage1, miiAuxiliaryImage2, domainNamespace);
    createDomainAndVerify(domainUid, domainCR, domainNamespace,
        adminServerPodName, managedServerPrefix, replicaCount);

    // check configuration for JMS
    checkConfiguredJMSresouce(domainNamespace, adminServerPodName);

    //checking the order of loading for the auxiliary images, expecting file with content =2
    assertDoesNotThrow(() -> FileUtils.deleteQuietly(Paths.get(RESULTS_ROOT, "/test.txt").toFile()));
    assertDoesNotThrow(() -> copyFileFromPod(domainNamespace,
        adminServerPodName, "weblogic-server",
        auxiliaryImagePath + "/test.txt",
        Paths.get(RESULTS_ROOT, "/test.txt")), " Can't find file in the pod, or failed to copy");

    assertDoesNotThrow(() -> {
      String fileContent = Files.readAllLines(Paths.get(RESULTS_ROOT, "/test.txt")).get(0);
      assertEquals("2", fileContent, "The content of the file from auxiliary image path "
          + fileContent + "does not match the expected 2");
    }, "File from image2 was not loaded in the expected order");
  }

  /**
   * Reuse created a domain with datasource using auxiliary image containing the DataSource,
   * verify the domain is running and JDBC DataSource resource is added.
   * Patch domain with updated JDBC URL info and verify the update.
   */
  @Test
  @Order(2)
  @DisplayName("Test to update data source url in the  domain using auxiliary image")
  void testUpdateDataSourceInDomainUsingAuxiliaryImage() {
    Path multipleAIPath1 = Paths.get(RESULTS_ROOT, "multipleauxiliaryimage1");
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

    // check configuration for DataSource in the running domain
    int adminServiceNodePort
        = getServiceNodePort(domainNamespace, getExternalServicePodName(adminServerPodName), "default");
    assertNotEquals(-1, adminServiceNodePort, "admin server default node port is not valid");
    assertTrue(checkSystemResourceConfig(adminServiceNodePort,
        "JDBCSystemResources/TestDataSource/JDBCResource/JDBCDriverParams",
        "jdbc:oracle:thin:@\\/\\/xxx.xxx.x.xxx:1521\\/ORCLCDB"),
        "Can't find expected URL configuration for DataSource");

    logger.info("Found the DataResource configuration");

    patchDomainWithAuxiliaryImageAndVerify(miiAuxiliaryImage1, miiAuxiliaryImage3, domainUid, domainNamespace);

    checkConfiguredJDBCresouce(domainNamespace, adminServerPodName);
  }

  /**
   * Patch the domain with the different base image name.
   * Verify all the pods are restarted and back to ready state.
   * Verify configured JMS and JDBC resources.
   */
  @Test
  @Order(3)
  @DisplayName("Test to update Base Weblogic Image Name")
  void testUpdateBaseImageName() {
    // get the original domain resource before update
    Domain domain1 = assertDoesNotThrow(() -> getDomainCustomResource(domainUid, domainNamespace),
        String.format("getDomainCustomResource failed with ApiException when tried to get domain %s in namespace %s",
            domainUid, domainNamespace));
    assertNotNull(domain1, "Got null domain resource");
    assertNotNull(domain1.getSpec(), domain1 + "/spec is null");

    // get the map with server pods and their original creation timestamps
    podsWithTimeStamps = getPodsWithTimeStamps(domainNamespace, adminServerPodName, managedServerPrefix,
        replicaCount);

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

    StringBuffer patchStr = null;
    patchStr = new StringBuffer("[{");
    patchStr.append("\"op\": \"replace\",")
        .append(" \"path\": \"/spec/image\",")
        .append("\"value\": \"")
        .append(imageUpdate)
        .append("\"}]");
    logger.info("PatchStr for imageUpdate: {0}", patchStr.toString());

    assertTrue(patchDomainResource(domainUid, domainNamespace, patchStr),
        "patchDomainCustomResource(imageUpdate) failed");

    domain1 = assertDoesNotThrow(() -> getDomainCustomResource(domainUid, domainNamespace),
        String.format("getDomainCustomResource failed with ApiException when tried to get domain %s in namespace %s",
            domainUid, domainNamespace));
    assertNotNull(domain1, "Got null domain resource after patching");
    assertNotNull(domain1.getSpec(), domain1 + " /spec is null");

    //print out image name in the new patched domain
    logger.info("In the new patched domain image name is: {0}", domain1.getSpec().getImage());

    // verify the server pods are rolling restarted and back to ready state
    logger.info("Verifying rolling restart occurred for domain {0} in namespace {1}",
        domainUid, domainNamespace);
    assertTrue(verifyRollingRestartOccurred(podsWithTimeStamps, 1, domainNamespace),
        String.format("Rolling restart failed for domain %s in namespace %s", domainUid, domainNamespace));

    checkPodReadyAndServiceExists(adminServerPodName, domainUid, domainNamespace);

    // check configuration for JMS
    checkConfiguredJMSresouce(domainNamespace, adminServerPodName);
    //check configuration for JDBC
    checkConfiguredJDBCresouce(domainNamespace, adminServerPodName);

  }

  /**
   * Negative Test to create domain with mismatch mount path in auxiliary image and auxiliaryImageVolumes.
   * in auxiliaryImageVolumes, set mountPath to "/errorpath"
   * in auxiliary image, set AUXILIARY_IMAGE_PATH to "/auxiliary"
   * Check the error message is in introspector pod log, domain events and operator pod log.
   */
  @Test
  @Order(3)
  @DisplayName("Negative Test to create domain with mismatch mount path in auxiliary image and auxiliaryImageVolumes")
  void testErrorPathDomainMismatchMountPath() {

    OffsetDateTime timestamp = now();

    final String auxiliaryImageVolumeName = "auxiliaryImageVolume1";
    final String auxiliaryImagePath = "/errorpath";

    createSecretsForDomain(adminSecretName, encryptionSecretName, errorpathDomainNamespace);

    // create stage dir for auxiliary image
    Path errorpathAIPath1 = Paths.get(RESULTS_ROOT, "errorpathauxiimage1");
    assertDoesNotThrow(() -> FileUtils.deleteDirectory(errorpathAIPath1.toFile()),
        "Delete directory failed");
    assertDoesNotThrow(() -> Files.createDirectories(errorpathAIPath1),
        "Create directory failed");

    // create models dir and copy model for image
    Path modelsPath1 = Paths.get(errorpathAIPath1.toString(), "models");
    assertDoesNotThrow(() -> Files.createDirectories(modelsPath1));
    assertDoesNotThrow(() -> Files.copy(
        Paths.get(MODEL_DIR, MII_BASIC_WDT_MODEL_FILE),
        Paths.get(modelsPath1.toString(), MII_BASIC_WDT_MODEL_FILE),
        StandardCopyOption.REPLACE_EXISTING));

    // unzip WDT installation file into work dir
    unzipWDTInstallationFile(errorpathAIPath1.toString());

    // create image with model and wdt installation files
    createAuxiliaryImage(errorpathAIPath1.toString(),
        Paths.get(RESOURCE_DIR, "auxiliaryimage", "Dockerfile").toString(), errorPathAuxiliaryImage1);

    // push image to repo for multi node cluster
    if (!DOMAIN_IMAGES_REPO.isEmpty()) {
      logger.info("docker push image {0} to registry {1}", errorPathAuxiliaryImage1, DOMAIN_IMAGES_REPO);
      dockerLoginAndPushImageToRegistry(errorPathAuxiliaryImage1);
    }

    // create domain custom resource using auxiliary images
    logger.info("Creating domain custom resource with domainUid {0} and auxiliary image {1}",
        domainUid, errorPathAuxiliaryImage1);
    Domain domainCR = createDomainResource(domainUid, errorpathDomainNamespace,
        WEBLOGIC_IMAGE_TO_USE_IN_SPEC, adminSecretName, OCIR_SECRET_NAME,
        encryptionSecretName, replicaCount, "cluster-1", auxiliaryImagePath,
        auxiliaryImageVolumeName, errorPathAuxiliaryImage1);

    // create domain and verify it is failed
    logger.info("Creating domain {0} with auxiliary image {1} in namespace {2}",
        domainUid, errorPathAuxiliaryImage1, errorpathDomainNamespace);
    assertDoesNotThrow(() -> createDomainCustomResource(domainCR), "createDomainCustomResource throws Exception");

    // check the introspector pod log contains the expected error message
    String expectedErrorMsg = "Auxiliary Image: Dir '/errorpath' doesn't exist or is empty. Exiting.";
    verifyIntrospectorPodLogContainsExpectedErrorMsg(domainUid, errorpathDomainNamespace, expectedErrorMsg);

    // check the domain event contains the expected error message
    checkDomainEventContainsExpectedMsg(opNamespace, errorpathDomainNamespace, domainUid, DOMAIN_PROCESSING_FAILED,
        "Warning", timestamp, expectedErrorMsg);

    // check the operator pod log contains the expected error message
    String operatorPodName =
        assertDoesNotThrow(() -> getOperatorPodName(OPERATOR_RELEASE_NAME, opNamespace));
    checkPodLogContainsString(opNamespace, operatorPodName, expectedErrorMsg);

    // check there are no admin server and managed server pods and services created
    checkPodDoesNotExist(adminServerPodName, domainUid, errorpathDomainNamespace);
    checkServiceDoesNotExist(adminServerPodName, errorpathDomainNamespace);
    for (int i = 1; i <= replicaCount; i++) {
      checkPodDoesNotExist(managedServerPrefix + i, domainUid, errorpathDomainNamespace);
      checkServiceDoesNotExist(managedServerPrefix + i, errorpathDomainNamespace);
    }

    // delete domain1
    deleteDomainResource(errorpathDomainNamespace, domainUid);
  }

  /**
   * Negative Test to create domain without WDT binary.
   * Check the error message is in introspector pod log, domain events and operator pod log.
   */
  @Test
  @Order(4)
  @DisplayName("Negative Test to create domain without WDT binary")
  void testErrorPathDomainMissingWDTBinary() {

    OffsetDateTime timestamp = now();

    final String auxiliaryImageVolumeName = "auxiliaryImageVolume1";
    final String auxiliaryImagePath = "/auxiliary";

    createSecretsForDomain(adminSecretName, encryptionSecretName, errorpathDomainNamespace);

    // create stage dir for auxiliary image
    Path errorpathAIPath2 = Paths.get(RESULTS_ROOT, "errorpathauxiimage2");
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
    createAuxiliaryImage(errorpathAIPath2.toString(),
        Paths.get(RESOURCE_DIR, "auxiliaryimage", "Dockerfile").toString(), errorPathAuxiliaryImage2);

    // push image to repo for multi node cluster
    if (!DOMAIN_IMAGES_REPO.isEmpty()) {
      logger.info("docker push image {0} to registry {1}", errorPathAuxiliaryImage2, DOMAIN_IMAGES_REPO);
      dockerLoginAndPushImageToRegistry(errorPathAuxiliaryImage2);
    }

    // create domain custom resource using auxiliary images
    logger.info("Creating domain custom resource with domainUid {0} and auxiliary image {1}",
        domainUid, errorPathAuxiliaryImage2);
    Domain domainCR = createDomainResource(domainUid, errorpathDomainNamespace,
        WEBLOGIC_IMAGE_TO_USE_IN_SPEC, adminSecretName, OCIR_SECRET_NAME,
        encryptionSecretName, replicaCount, "cluster-1", auxiliaryImagePath,
        auxiliaryImageVolumeName, errorPathAuxiliaryImage2);

    // create domain and verify it is failed
    logger.info("Creating domain {0} with auxiliary image {1} in namespace {2}",
        domainUid, errorPathAuxiliaryImage2, errorpathDomainNamespace);
    assertDoesNotThrow(() -> createDomainCustomResource(domainCR), "createDomainCustomResource throws Exception");

    // check the introspector pod log contains the expected error message
    String expectedErrorMsg = "The domain resource 'spec.domainHomeSourceType' is 'FromModel'  and "
        + "a WebLogic Deploy Tool (WDT) install is not located at  'spec.configuration.model.wdtInstallHome'  "
        + "which is currently set to '/auxiliary/weblogic-deploy'";
    verifyIntrospectorPodLogContainsExpectedErrorMsg(domainUid, errorpathDomainNamespace, expectedErrorMsg);

    // check the domain event contains the expected error message
    checkDomainEventContainsExpectedMsg(opNamespace, errorpathDomainNamespace, domainUid, DOMAIN_PROCESSING_FAILED,
        "Warning", timestamp, expectedErrorMsg);

    // check the operator pod log contains the expected error message
    String operatorPodName =
        assertDoesNotThrow(() -> getOperatorPodName(OPERATOR_RELEASE_NAME, opNamespace),
            "Can't get operator pod's name");
    checkPodLogContainsString(opNamespace, operatorPodName, expectedErrorMsg);

    // check there are no admin server and managed server pods and services created
    checkPodDoesNotExist(adminServerPodName, domainUid, errorpathDomainNamespace);
    checkServiceDoesNotExist(adminServerPodName, errorpathDomainNamespace);
    for (int i = 1; i <= replicaCount; i++) {
      checkPodDoesNotExist(managedServerPrefix + i, domainUid, errorpathDomainNamespace);
      checkServiceDoesNotExist(managedServerPrefix + i, errorpathDomainNamespace);
    }

    // delete domain1
    deleteDomainResource(errorpathDomainNamespace, domainUid);
  }

  /**
   * Negative Test to create domain without domain model file, the auxiliary image contains only sparse JMS config.
   * Check the error message is in introspector pod log, domain events and operator pod log
   */
  @Test
  @Order(5)
  @DisplayName("Negative Test to create domain without domain model file, only having sparse JMS config")
  void testErrorPathDomainMissingDomainConfig() {

    OffsetDateTime timestamp = now();

    final String auxiliaryImageVolumeName = "auxiliaryImageVolume1";
    final String auxiliaryImagePath = "/auxiliary";

    createSecretsForDomain(adminSecretName, encryptionSecretName, errorpathDomainNamespace);

    // create stage dir for auxiliary image
    Path errorpathAIPath3 = Paths.get(RESULTS_ROOT, "errorpathauxiimage3");
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
        domainUid, errorPathAuxiliaryImage3);
    Domain domainCR = createDomainResource(domainUid, errorpathDomainNamespace,
        WEBLOGIC_IMAGE_TO_USE_IN_SPEC, adminSecretName, OCIR_SECRET_NAME,
        encryptionSecretName, replicaCount, "cluster-1", auxiliaryImagePath,
        auxiliaryImageVolumeName, errorPathAuxiliaryImage3);

    // create domain and verify it is failed
    logger.info("Creating domain {0} with auxiliary image {1} in namespace {2}",
        domainUid, errorPathAuxiliaryImage3, errorpathDomainNamespace);
    assertDoesNotThrow(() -> createDomainCustomResource(domainCR), "createDomainCustomResource throws Exception");

    // check the introspector pod log contains the expected error message
    String expectedErrorMsg =
        "createDomain did not find the required domainInfo section in the model file /auxiliary/models/model.jms2.yaml";
    verifyIntrospectorPodLogContainsExpectedErrorMsg(domainUid, errorpathDomainNamespace, expectedErrorMsg);

    // check the domain event contains the expected error message
    checkDomainEventContainsExpectedMsg(opNamespace, errorpathDomainNamespace, domainUid, DOMAIN_PROCESSING_FAILED,
        "Warning", timestamp, expectedErrorMsg);

    // check the operator pod log contains the expected error message
    String operatorPodName =
        assertDoesNotThrow(() -> getOperatorPodName(OPERATOR_RELEASE_NAME, opNamespace),
            "Get operator's pod name failed");
    checkPodLogContainsString(opNamespace, operatorPodName, expectedErrorMsg);

    // check there are no admin server and managed server pods and services created
    checkPodDoesNotExist(adminServerPodName, domainUid, errorpathDomainNamespace);
    checkServiceDoesNotExist(adminServerPodName, errorpathDomainNamespace);
    for (int i = 1; i <= replicaCount; i++) {
      checkPodDoesNotExist(managedServerPrefix + i, domainUid, errorpathDomainNamespace);
      checkServiceDoesNotExist(managedServerPrefix + i, errorpathDomainNamespace);
    }

    // delete domain1
    deleteDomainResource(errorpathDomainNamespace, domainUid);
  }

  /**
   * Negative Test to patch the existing domain using a custom mount command that's guaranteed to fail.
   * Specify domain.spec.serverPod.auxiliaryImages.command to a custom mount command instead of the default one, which
   * defaults to "cp -R $AUXILIARY_IMAGE_PATH/* $TARGET_MOUNT_PATH"
   * Check the error message in introspector pod log, domain events and operator pod log.
   * Restore the domain by removing the custom mount command.
   */
  @Test
  @Order(6)
  @DisplayName("Negative Test to patch domain using a custom mount command that's guaranteed to fail")
  void testErrorPathDomainWithFailCustomMountCommand() {

    OffsetDateTime timestamp = now();

    // get the creation time of the admin server pod before patching
    LinkedHashMap<String, OffsetDateTime> pods = new LinkedHashMap<>();
    pods.put(adminServerPodName, getPodCreationTime(domainNamespace, adminServerPodName));
    // get the creation time of the managed server pods before patching
    for (int i = 1; i <= replicaCount; i++) {
      pods.put(managedServerPrefix + i, getPodCreationTime(domainNamespace, managedServerPrefix + i));
    }

    Domain domain1 = assertDoesNotThrow(() -> getDomainCustomResource(domainUid, domainNamespace),
        String.format("getDomainCustomResource failed with ApiException when tried to get domain %s in namespace %s",
            domainUid, domainNamespace));
    assertNotNull(domain1, "Got null domain resource ");
    assertNotNull(domain1.getSpec().getServerPod().getAuxiliaryImages(),
        domain1 + "/spec/serverPod/auxiliaryImages is null");

    List<AuxiliaryImage> auxiliaryImageList = domain1.getSpec().getServerPod().getAuxiliaryImages();
    assertFalse(auxiliaryImageList.isEmpty(), "AuxiliaryImage list is empty");

    // patch the first auxiliary image
    String searchString = "\"/spec/serverPod/auxiliaryImages/0/command\"";
    StringBuffer patchStr = new StringBuffer("[{");
    patchStr.append("\"op\": \"add\",")
        .append(" \"path\": " + searchString + ",")
        .append(" \"value\":  \"exit 1\"")
        .append(" }]");
    logger.info("Auxiliary Image patch string: " + patchStr);

    V1Patch patch = new V1Patch((patchStr).toString());

    boolean aiPatched = assertDoesNotThrow(() ->
            patchDomainCustomResource(domainUid, domainNamespace, patch, "application/json-patch+json"),
        "patchDomainCustomResource(Auxiliary Image)  failed ");
    assertTrue(aiPatched, "patchDomainCustomResource(auxiliary image) failed");

    // check the introspector pod log contains the expected error message
    String expectedErrorMsg = "Auxiliary Image: Command 'exit 1' execution failed in container";
    verifyIntrospectorPodLogContainsExpectedErrorMsg(domainUid, domainNamespace, expectedErrorMsg);

    // check the domain event contains the expected error message
    checkDomainEventContainsExpectedMsg(opNamespace, domainNamespace, domainUid, DOMAIN_PROCESSING_FAILED,
        "Warning", timestamp, expectedErrorMsg);

    // check the operator pod log contains the expected error message
    String operatorPodName =
        assertDoesNotThrow(() -> getOperatorPodName(OPERATOR_RELEASE_NAME, opNamespace),
            "Get operator's pod name failed");
    checkPodLogContainsString(opNamespace, operatorPodName, expectedErrorMsg);

    // verify the domain is not rolled
    logger.info("sleep 2 minutes to make sure the domain is not restarted");
    try {
      Thread.sleep(120000);
    } catch (InterruptedException ie) {
      // ignore
    }
    verifyPodsNotRolled(domainNamespace, pods);

    // restore domain1
    // patch the first auxiliary image to remove the domain.spec.serverPod.auxiliaryImages.command
    patchStr = new StringBuffer("[{");
    patchStr.append("\"op\": \"remove\",")
        .append(" \"path\": " + searchString)
        .append(" }]");
    logger.info("Auxiliary Image patch string: " + patchStr);

    V1Patch patch1 = new V1Patch((patchStr).toString());

    aiPatched = assertDoesNotThrow(() ->
            patchDomainCustomResource(domainUid, domainNamespace, patch1, "application/json-patch+json"),
        "patchDomainCustomResource(Auxiliary Image)  failed ");
    assertTrue(aiPatched, "patchDomainCustomResource(auxiliary image) failed");

    // check the admin server is up and running
    checkPodReady(adminServerPodName, domainUid, domainNamespace);
  }

  /**
   * Negative Test to create domain with file , created by user tester with permission read only
   * and not accessible by oracle user in auxiliary image
   * via provided Dockerfile.
   * Check the error message is in introspector pod log, domain events and operator pod log.
   */
  @Test
  @Order(7)
  @DisplayName("Negative Test to create domain with file in auxiliary image not accessible by oracle user")
  void testErrorPathFilePermission() {

    OffsetDateTime timestamp = now();

    final String auxiliaryImageVolumeName = "auxiliaryImageVolume1";
    final String auxiliaryImagePath = "/auxiliary";

    createSecretsForDomain(adminSecretName, encryptionSecretName, errorpathDomainNamespace);

    // create stage dir for auxiliary image
    Path errorpathAIPath1 = Paths.get(RESULTS_ROOT, "errorpathauxiimage4");
    assertDoesNotThrow(() -> FileUtils.deleteDirectory(errorpathAIPath1.toFile()),
        "Can't delete directory");
    assertDoesNotThrow(() -> Files.createDirectories(errorpathAIPath1),
        "Can't create directory");

    Path errorpathAIPathToFile = Paths.get(RESULTS_ROOT, "errorpathauxiimage4/test1.txt");
    String content = "some text ";
    assertDoesNotThrow(() -> Files.write(errorpathAIPathToFile, content.getBytes()),
        "Can't write to file " + errorpathAIPathToFile);

    // create models dir and copy model for image
    Path modelsPath1 = Paths.get(errorpathAIPath1.toString(), "models");
    assertDoesNotThrow(() -> Files.createDirectories(modelsPath1),
        "Can't create directory");
    assertDoesNotThrow(() -> Files.copy(
        Paths.get(MODEL_DIR, MII_BASIC_WDT_MODEL_FILE),
        Paths.get(modelsPath1.toString(), MII_BASIC_WDT_MODEL_FILE),
        StandardCopyOption.REPLACE_EXISTING),
        "Can't copy files");

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
        domainUid, errorPathAuxiliaryImage4);
    Domain domainCR = createDomainResource(domainUid, errorpathDomainNamespace,
        WEBLOGIC_IMAGE_TO_USE_IN_SPEC, adminSecretName, OCIR_SECRET_NAME,
        encryptionSecretName, replicaCount, "cluster-1", auxiliaryImagePath,
        auxiliaryImageVolumeName, errorPathAuxiliaryImage4);

    // create domain and verify it is failed
    logger.info("Creating domain {0} with auxiliary image {1} in namespace {2}",
        domainUid, errorPathAuxiliaryImage4, errorpathDomainNamespace);
    assertDoesNotThrow(() -> createDomainCustomResource(domainCR), "createDomainCustomResource throws Exception");

    // check the introspector pod log contains the expected error message
    String expectedErrorMsg = "cp: can't open '/auxiliary/test1.txt': Permission denied";
    verifyIntrospectorPodLogContainsExpectedErrorMsg(domainUid, errorpathDomainNamespace, expectedErrorMsg);

    // check the domain event contains the expected error message
    checkDomainEventContainsExpectedMsg(opNamespace, errorpathDomainNamespace, domainUid, DOMAIN_PROCESSING_FAILED,
        "Warning", timestamp, expectedErrorMsg);

    // check the operator pod log contains the expected error message
    String operatorPodName =
        assertDoesNotThrow(() -> getOperatorPodName(OPERATOR_RELEASE_NAME, opNamespace),
            "Can't get operator's pod name");
    checkPodLogContainsString(opNamespace, operatorPodName, expectedErrorMsg);

    // check there are no admin server and managed server pods and services not created
    checkPodDoesNotExist(adminServerPodName, domainUid, errorpathDomainNamespace);
    checkServiceDoesNotExist(adminServerPodName, errorpathDomainNamespace);
    for (int i = 1; i <= replicaCount; i++) {
      checkPodDoesNotExist(managedServerPrefix + i, domainUid, errorpathDomainNamespace);
      checkServiceDoesNotExist(managedServerPrefix + i, errorpathDomainNamespace);
    }

    // delete domain1
    deleteDomainResource(errorpathDomainNamespace, domainUid);
  }

  /**
   * Create a domain using multiple auxiliary images.
   * One auxiliary image (image1) contains the domain configuration and
   * another auxiliary image (image2) with WDT only,
   * update WDT version by patching with another auxiliary image (image3)
   * and verify the domain is running.
   */
  @Test
  @Order(8)
  @DisplayName("Test to update WDT version using  auxiliary images")
  void testUpdateWDTVersionUsingMultipleAuxiliaryImages() {

    // admin/managed server name here should match with model yaml
    final String auxiliaryImageVolumeName = "auxiliaryImageVolume1";
    final String auxiliaryImagePath = "/auxiliary";

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
    Path multipleAIPath1 = Paths.get(RESULTS_ROOT, "multipleauxiliaryimage1");
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
        Paths.get(RESOURCE_DIR, "auxiliaryimage", "Dockerfile").toString(), miiAuxiliaryImage4);

    // push image1 to repo for multi node cluster
    if (!DOMAIN_IMAGES_REPO.isEmpty()) {
      logger.info("docker push image {0} to registry {1}", miiAuxiliaryImage4, DOMAIN_IMAGES_REPO);
      dockerLoginAndPushImageToRegistry(miiAuxiliaryImage4);
    }

    // create stage dir for second auxiliary image
    Path multipleAIPath2 = Paths.get(RESULTS_ROOT, "multipleauxiliaryimage2");
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
        Paths.get(RESOURCE_DIR, "auxiliaryimage", "Dockerfile").toString(), miiAuxiliaryImage5);

    // push image2 to repo for multi node cluster
    if (!DOMAIN_IMAGES_REPO.isEmpty()) {
      logger.info("docker push image {0} to registry {1}", miiAuxiliaryImage5, DOMAIN_IMAGES_REPO);
      dockerLoginAndPushImageToRegistry(miiAuxiliaryImage5);
    }

    // create stage dir for third auxiliary image with latest wdt installation files only
    Path multipleAIPath3 = Paths.get(RESULTS_ROOT, "multipleauxiliaryimage3");
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
        Paths.get(RESOURCE_DIR, "auxiliaryimage", "Dockerfile").toString(), miiAuxiliaryImage6);

    // push image3 to repo for multi node cluster
    if (!DOMAIN_IMAGES_REPO.isEmpty()) {
      logger.info("docker push image {0} to registry {1}", miiAuxiliaryImage6, DOMAIN_IMAGES_REPO);
      dockerLoginAndPushImageToRegistry(miiAuxiliaryImage6);
    }

    // create domain custom resource using 2 auxiliary images ( image1, image2)
    logger.info("Creating domain custom resource with domainUid {0} and auxiliary images {1} {2}",
        domainUid, miiAuxiliaryImage4, miiAuxiliaryImage5);
    Domain domainCR = createDomainResource(domainUid, wdtDomainNamespace,
        WEBLOGIC_IMAGE_TO_USE_IN_SPEC, adminSecretName, OCIR_SECRET_NAME,
        encryptionSecretName, replicaCount, "cluster-1", auxiliaryImagePath,
        auxiliaryImageVolumeName, miiAuxiliaryImage4, miiAuxiliaryImage5);

    // create domain and verify its running
    logger.info("Creating domain {0} with auxiliary images {1} {2} in namespace {3}",
        domainUid, miiAuxiliaryImage4, miiAuxiliaryImage5, wdtDomainNamespace);
    createDomainAndVerify(domainUid, domainCR, wdtDomainNamespace,
        adminServerPodName, managedServerPrefix, replicaCount);

    // check configuration for DataSource in the running domain
    int adminServiceNodePort
        = getServiceNodePort(wdtDomainNamespace, getExternalServicePodName(adminServerPodName), "default");
    assertNotEquals(-1, adminServiceNodePort, "admin server default node port is not valid");
    assertTrue(checkSystemResourceConfig(adminServiceNodePort,
        "JDBCSystemResources/TestDataSource/JDBCResource/JDBCDriverParams",
        "jdbc:oracle:thin:@\\/\\/xxx.xxx.x.xxx:1521\\/ORCLCDB"),
        "Can't find expected URL configuration for DataSource");

    logger.info("Found the DataResource configuration");
    //check WDT version in the image equals the  provided WDT_TEST_VERSION

    assertDoesNotThrow(() -> {
      String wdtVersion = checkWDTVersion(wdtDomainNamespace, auxiliaryImagePath);
      assertEquals("WebLogic Deploy Tooling " + WDT_TEST_VERSION, wdtVersion,
          " Used WDT in the auxiliary image does not match the expected");
    }, "Can't retrieve wdt version file or version does match the expected");

    //updating wdt to latest version by patching the domain with image3
    patchDomainWithAuxiliaryImageAndVerify(miiAuxiliaryImage5, miiAuxiliaryImage6, domainUid, wdtDomainNamespace);

    //check that WDT version is changed
    assertDoesNotThrow(() -> {
      String wdtVersion = checkWDTVersion(wdtDomainNamespace, auxiliaryImagePath);
      assertNotEquals("WebLogic Deploy Tooling " + WDT_TEST_VERSION,wdtVersion,
          " Used WDT in the auxiliary image was not updated");
    }, "Can't retrieve wdt version file "
        + "or wdt was not updated after patching with auxiliary image");

    // check configuration for DataSource in the running domain
    adminServiceNodePort
        = getServiceNodePort(wdtDomainNamespace, getExternalServicePodName(adminServerPodName), "default");
    assertNotEquals(-1, adminServiceNodePort, "admin server default node port is not valid");
    assertTrue(checkSystemResourceConfig(adminServiceNodePort,
        "JDBCSystemResources/TestDataSource/JDBCResource/JDBCDriverParams",
        "jdbc:oracle:thin:@\\/\\/xxx.xxx.x.xxx:1521\\/ORCLCDB"),
        "Can't find expected URL configuration for DataSource");

  }

  private static void patchDomainWithAuxiliaryImageAndVerify(String oldImageName, String newImageName,
                                                             String domainUid, String domainNamespace) {
    String adminServerPodName = domainUid + "-admin-server";
    String managedServerPrefix = domainUid + "-managed-server";
    Domain domain1 = assertDoesNotThrow(() -> getDomainCustomResource(domainUid, domainNamespace),
        String.format("getDomainCustomResource failed with ApiException when tried to get domain %s in namespace %s",
            domainUid, domainNamespace));
    assertNotNull(domain1, "Got null domain resource ");
    assertNotNull(domain1.getSpec().getServerPod().getAuxiliaryImages(),
        domain1 + "/spec/serverPod/auxiliaryImages is null");
    List<AuxiliaryImage> auxiliaryImageList = domain1.getSpec().getServerPod().getAuxiliaryImages();
    assertFalse(auxiliaryImageList.isEmpty(), "AuxiliaryImage list is empty");
    String searchString;
    int index = 0;

    AuxiliaryImage ai = auxiliaryImageList.stream()
        .filter(auxiliaryImage -> oldImageName.equals(auxiliaryImage.getImage()))
        .findAny()
        .orElse(null);
    assertNotNull(ai, "Can't find auxiliary image with Image name " + oldImageName
        + "can't patch domain " + domainUid);

    index = auxiliaryImageList.indexOf(ai);
    searchString = "\"/spec/serverPod/auxiliaryImages/" + index + "/image\"";
    StringBuffer patchStr = new StringBuffer("[{");
    patchStr.append("\"op\": \"replace\",")
        .append(" \"path\": " + searchString + ",")
        .append(" \"value\":  \"" + newImageName + "\"")
        .append(" }]");
    logger.info("Auxiliary Image patch string: " + patchStr);

    //get current timestamp before domain rolling restart to verify domain roll events
    podsWithTimeStamps = getPodsWithTimeStamps(domainNamespace, adminServerPodName,
        managedServerPrefix, 2);
    V1Patch patch = new V1Patch((patchStr).toString());

    boolean aiPatched = assertDoesNotThrow(() ->
            patchDomainCustomResource(domainUid, domainNamespace, patch, "application/json-patch+json"),
        "patchDomainCustomResource(Auxiliary Image)  failed ");
    assertTrue(aiPatched, "patchDomainCustomResource(auxiliary image) failed");

    domain1 = assertDoesNotThrow(() -> getDomainCustomResource(domainUid, domainNamespace),
        String.format("getDomainCustomResource failed with ApiException when tried to get domain %s in namespace %s",
            domainUid, domainNamespace));
    assertNotNull(domain1, "Got null domain resource after patching");
    assertNotNull(domain1.getSpec(), domain1 + " /spec/serverPod is null");
    assertNotNull(domain1.getSpec().getServerPod(), domain1 + " /spec/serverPod is null");
    assertNotNull(domain1.getSpec().getServerPod().getAuxiliaryImages(),
        domain1 + "/spec/serverPod/auxiliaryImages is null");

    //verify the new auxiliary image in the new patched domain
    auxiliaryImageList = domain1.getSpec().getServerPod().getAuxiliaryImages();

    String auxiliaryImage = auxiliaryImageList.get(index).getImage();
    logger.info("In the new patched domain imageValue is: {0}", auxiliaryImage);
    assertTrue(auxiliaryImage.equalsIgnoreCase(newImageName), "auxiliary image was not updated"
        + " in the new patched domain");

    // verify the server pods are rolling restarted and back to ready state
    logger.info("Verifying rolling restart occurred for domain {0} in namespace {1}",
        domainUid, domainNamespace);

    assertTrue(verifyRollingRestartOccurred(podsWithTimeStamps, 1, domainNamespace),
        String.format("Rolling restart failed for domain %s in namespace %s", domainUid, domainNamespace));
  }

  /**
   * Cleanup images.
   */
  public void tearDownAll() {
    // delete images
    if (miiAuxiliaryImage1 != null) {
      deleteImage(miiAuxiliaryImage1);
    }

    if (miiAuxiliaryImage2 != null) {
      deleteImage(miiAuxiliaryImage2);
    }

    if (miiAuxiliaryImage3 != null) {
      deleteImage(miiAuxiliaryImage3);
    }

    if (miiAuxiliaryImage4 != null) {
      deleteImage(miiAuxiliaryImage4);
    }

    if (miiAuxiliaryImage5 != null) {
      deleteImage(miiAuxiliaryImage5);
    }

    if (miiAuxiliaryImage6 != null) {
      deleteImage(miiAuxiliaryImage6);
    }

    if (errorPathAuxiliaryImage1 != null) {
      deleteImage(errorPathAuxiliaryImage1);
    }

    if (errorPathAuxiliaryImage2 != null) {
      deleteImage(errorPathAuxiliaryImage2);
    }

    if (errorPathAuxiliaryImage3 != null) {
      deleteImage(errorPathAuxiliaryImage3);
    }

    if (errorPathAuxiliaryImage4 != null) {
      deleteImage(errorPathAuxiliaryImage4);
    }
  }

  private void createAuxiliaryImage(String stageDirPath, String dockerFileLocation, String auxiliaryImage) {
    String cmdToExecute = String.format("cd %s && docker build -f %s %s -t %s .",
        stageDirPath, dockerFileLocation,
        "--build-arg AUXILIARY_IMAGE_PATH=/auxiliary", auxiliaryImage);
    assertTrue(new Command()
        .withParams(new CommandParams()
            .command(cmdToExecute))
        .execute(), String.format("Failed to execute", cmdToExecute));
  }

  private void createSecretsForDomain(String adminSecretName, String encryptionSecretName, String domainNamespace) {
    if (!secretExists(OCIR_SECRET_NAME, domainNamespace)) {
      createOcirRepoSecret(domainNamespace);
    }

    // create secret for admin credentials
    logger.info("Create secret for admin credentials");
    if (!secretExists(adminSecretName, domainNamespace)) {
      createSecretWithUsernamePassword(adminSecretName, domainNamespace,
          ADMIN_USERNAME_DEFAULT, ADMIN_PASSWORD_DEFAULT);
    }

    // create encryption secret
    logger.info("Create encryption secret");
    if (!secretExists(encryptionSecretName, domainNamespace)) {
      createSecretWithUsernamePassword(encryptionSecretName, domainNamespace, "weblogicenc", "weblogicenc");
    }
  }

  private void checkConfiguredJMSresouce(String domainNamespace, String adminServerPodName) {
    int adminServiceNodePort
        = getServiceNodePort(domainNamespace, getExternalServicePodName(adminServerPodName), "default");
    assertNotEquals(-1, adminServiceNodePort, "admin server default node port is not valid");

    // In OKD env, adminServers' external service nodeport cannot be accessed directly.
    // We have to create a route for the admins server external service.
    if ((adminSvcExtHost == null)) {
      adminSvcExtHost = createRouteForOKD(getExternalServicePodName(adminServerPodName), domainNamespace);
    }

    verifySystemResourceConfiguration(adminSvcExtHost, adminServiceNodePort, "JMSSystemResources",
        "TestClusterJmsModule2", "200");
    logger.info("Found the JMSSystemResource configuration");
  }

  private void checkConfiguredJDBCresouce(String domainNamespace, String adminServerPodName) {
    int adminServiceNodePort
        = getServiceNodePort(domainNamespace, getExternalServicePodName(adminServerPodName), "default");
    assertNotEquals(-1, adminServiceNodePort, "admin server default node port is not valid");

    // In OKD env, adminServers' external service nodeport cannot be accessed directly.
    // We have to create a route for the admins server external service.
    if ((adminSvcExtHost == null)) {
      adminSvcExtHost = createRouteForOKD(getExternalServicePodName(adminServerPodName), domainNamespace);
    }

    String hostAndPort = (OKD) ? adminSvcExtHost : K8S_NODEPORT_HOST + ":" + adminServiceNodePort;
    logger.info("hostAndPort = {0} ", hostAndPort);
    StringBuffer curlString = new StringBuffer("curl --user ");
    curlString.append(ADMIN_USERNAME_DEFAULT + ":" + ADMIN_PASSWORD_DEFAULT)
        .append(" http://" + hostAndPort + "/management/weblogic/latest/domainConfig")
        .append("/JDBCSystemResources/TestDataSource/JDBCResource/JDBCDriverParams/");

    verifyCommandResultContainsMsg(new String(curlString), "jdbc:oracle:thin:@\\/\\/localhost:7001\\/dbsvc");
    logger.info("Found the DataResource configuration");
  }

  private String checkWDTVersion(String domainNamespace, String auxiliaryImagePath) throws Exception {
    assertDoesNotThrow(() -> FileUtils.deleteQuietly(Paths.get(RESULTS_ROOT, "/WDTversion.txt").toFile()));
    assertDoesNotThrow(() -> copyFileFromPod(domainNamespace,
        adminServerPodName, "weblogic-server",
        auxiliaryImagePath + "/weblogic-deploy/VERSION.txt",
        Paths.get(RESULTS_ROOT, "/WDTversion.txt")), " Can't find file in the pod, or failed to copy");


    return Files.readAllLines(Paths.get(RESULTS_ROOT, "/WDTversion.txt")).get(0);
  }

  private boolean introspectorPodLogContainsExpectedErrorMsg(String domainUid,
                                                             String namespace,
                                                             String errormsg) {
    String introspectPodName;
    V1Pod introspectorPod;

    String introspectJobName = getIntrospectJobName(domainUid);
    String labelSelector = String.format("weblogic.domainUID in (%s)", domainUid);

    try {
      introspectorPod = getPod(namespace, labelSelector, introspectJobName);
    } catch (ApiException apiEx) {
      logger.severe("got ApiException while getting pod: {0}", apiEx);
      return false;
    }

    if (introspectorPod != null && introspectorPod.getMetadata() != null) {
      introspectPodName = introspectorPod.getMetadata().getName();
      logger.info("found introspectore pod {0} in namespace {1}", introspectPodName, namespace);
    } else {
      return false;
    }

    String introspectorLog;
    try {
      introspectorLog = getPodLog(introspectPodName, namespace);
      logger.info("introspector log: {0}", introspectorLog);
    } catch (ApiException apiEx) {
      logger.severe("got ApiException while getting pod log: {0}", apiEx);
      return false;
    }

    return introspectorLog.contains(errormsg);
  }

  private void verifyIntrospectorPodLogContainsExpectedErrorMsg(String domainUid,
                                                                String namespace,
                                                                String expectedErrorMsg) {

    // wait and check whether the introspector log contains the expected error message
    logger.info("verifying that the introspector log contains the expected error message");
    testUntil(
        () -> introspectorPodLogContainsExpectedErrorMsg(domainUid, namespace, expectedErrorMsg),
        logger,
        "Checking for the log of introspector pod contains the expected error msg {0}",
        expectedErrorMsg);
  }
}
