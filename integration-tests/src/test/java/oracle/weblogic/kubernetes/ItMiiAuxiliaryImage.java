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
import oracle.weblogic.domain.AuxiliaryImage;
import oracle.weblogic.domain.Domain;
import oracle.weblogic.kubernetes.actions.impl.primitive.Command;
import oracle.weblogic.kubernetes.actions.impl.primitive.CommandParams;
import oracle.weblogic.kubernetes.annotations.IntegrationTest;
import oracle.weblogic.kubernetes.annotations.Namespaces;
import oracle.weblogic.kubernetes.logging.LoggingFacade;
import oracle.weblogic.kubernetes.utils.TestUtils;
import org.apache.commons.io.FileUtils;
import org.awaitility.core.ConditionFactory;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import static java.util.concurrent.TimeUnit.MINUTES;
import static java.util.concurrent.TimeUnit.SECONDS;
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
import static oracle.weblogic.kubernetes.TestConstants.WEBLOGIC_IMAGE_TAG;
import static oracle.weblogic.kubernetes.actions.ActionConstants.ARCHIVE_DIR;
import static oracle.weblogic.kubernetes.actions.ActionConstants.MODEL_DIR;
import static oracle.weblogic.kubernetes.actions.ActionConstants.RESOURCE_DIR;
import static oracle.weblogic.kubernetes.actions.TestActions.buildAppArchive;
import static oracle.weblogic.kubernetes.actions.TestActions.createDomainCustomResource;
import static oracle.weblogic.kubernetes.actions.TestActions.defaultAppParams;
import static oracle.weblogic.kubernetes.actions.TestActions.deleteImage;
import static oracle.weblogic.kubernetes.actions.TestActions.dockerPush;
import static oracle.weblogic.kubernetes.actions.TestActions.dockerTag;
import static oracle.weblogic.kubernetes.actions.TestActions.getDomainCustomResource;
import static oracle.weblogic.kubernetes.actions.TestActions.getOperatorPodName;
import static oracle.weblogic.kubernetes.actions.TestActions.getServiceNodePort;
import static oracle.weblogic.kubernetes.actions.TestActions.now;
import static oracle.weblogic.kubernetes.actions.TestActions.patchDomainCustomResource;
import static oracle.weblogic.kubernetes.assertions.TestAssertions.secretExists;
import static oracle.weblogic.kubernetes.assertions.TestAssertions.verifyRollingRestartOccurred;
import static oracle.weblogic.kubernetes.utils.CommonMiiTestUtils.createDomainResource;
import static oracle.weblogic.kubernetes.utils.CommonMiiTestUtils.verifyPodsNotRolled;
import static oracle.weblogic.kubernetes.utils.CommonPatchTestUtils.patchDomainResource;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.checkDomainEventContainsExpectedMsg;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.checkPodDoesNotExist;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.checkPodLogContainsString;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.checkPodReady;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.checkPodReadyAndServiceExists;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.checkServiceDoesNotExist;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.checkSystemResourceConfig;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.checkSystemResourceConfiguration;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.createDomainAndVerify;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.createOcirRepoSecret;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.createSecretWithUsernamePassword;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.deleteDomainResource;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.dockerLoginAndPushImageToRegistry;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.getExternalServicePodName;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.getIntrospectorPodName;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.getPodCreationTime;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.getPodsWithTimeStamps;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.installAndVerifyOperator;
import static oracle.weblogic.kubernetes.utils.FileUtils.copyFileFromPod;
import static oracle.weblogic.kubernetes.utils.FileUtils.replaceStringInFile;
import static oracle.weblogic.kubernetes.utils.FileUtils.unzipWDTInstallationFile;
import static oracle.weblogic.kubernetes.utils.K8sEvents.DOMAIN_PROCESSING_FAILED;
import static oracle.weblogic.kubernetes.utils.ThreadSafeLogger.getLogger;
import static org.awaitility.Awaitility.with;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("Test to create model in image domain using auxiliary image")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@IntegrationTest
public class ItMiiAuxiliaryImage {

  private static String opNamespace = null;
  private static String domainNamespace = null;
  private static String errorpathDomainNamespace = null;
  private static LoggingFacade logger = null;
  private String domainUid = "domain1";
  private static String miiAuxiliaryImage1 = MII_AUXILIARY_IMAGE_NAME + ":" + MII_BASIC_IMAGE_TAG + "1";
  private static String miiAuxiliaryImage2 = MII_AUXILIARY_IMAGE_NAME + ":" + MII_BASIC_IMAGE_TAG + "2";
  private static String miiAuxiliaryImage3 = MII_AUXILIARY_IMAGE_NAME + ":" + MII_BASIC_IMAGE_TAG + "3";
  private static Map<String, OffsetDateTime> podsWithTimeStamps = null;
  private final String adminServerPodName = domainUid + "-admin-server";
  private final String managedServerPrefix = domainUid + "-managed-server";
  private final int replicaCount = 2;
  private String adminSecretName = "weblogic-credentials";
  private String encryptionSecretName = "encryptionsecret";

  ConditionFactory withStandardRetryPolicy
      = with().pollDelay(0, SECONDS)
      .and().with().pollInterval(10, SECONDS)
      .atMost(30, MINUTES).await();

  /**
   * Install Operator.
   * @param namespaces list of namespaces created by the IntegrationTestWatcher by the
   *        JUnit engine parameter resolution mechanism
   */
  @BeforeAll
  public static void initAll(@Namespaces(3) List<String> namespaces) {
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

    // install and verify operator
    installAndVerifyOperator(opNamespace, domainNamespace, errorpathDomainNamespace);
  }


  /**
   * Create a domain using multiple auxiliary images. One auxiliary image containing the domain configuration and
   * another auxiliary image with JMS system resource, verify the domain is running and JMS resource is added.
   */
  @Test
  @Order(1)
  @DisplayName("Test to create domain using multiple auxiliary images")
  public void testCreateDomainUsingMultipleAuxiliaryImages() {

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
    assertDoesNotThrow(() -> FileUtils.deleteDirectory(multipleAIPath1.toFile()));
    assertDoesNotThrow(() -> Files.createDirectories(multipleAIPath1));
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
        Paths.get(ARCHIVE_DIR,  MII_BASIC_APP_NAME + ".zip"),
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
      assertTrue(dockerPush(miiAuxiliaryImage1), String.format("docker push failed for image %s", miiAuxiliaryImage1));
    }

    // create stage dir for second auxiliary image with image2
    Path multipleAIPath2 = Paths.get(RESULTS_ROOT, "multipleauxiliaryimage2");
    assertDoesNotThrow(() -> FileUtils.deleteDirectory(multipleAIPath2.toFile()));
    assertDoesNotThrow(() -> Files.createDirectories(multipleAIPath2));

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
      assertTrue(dockerPush(miiAuxiliaryImage2), String.format("docker push failed for image %s", miiAuxiliaryImage2));
    }

    // create domain custom resource using 2 auxiliary images
    logger.info("Creating domain custom resource with domainUid {0} and auxiliary images {1} {2}",
        domainUid, miiAuxiliaryImage1, miiAuxiliaryImage2);
    Domain domainCR = createDomainResource(domainUid, domainNamespace,
        WEBLOGIC_IMAGE_NAME + ":" + WEBLOGIC_IMAGE_TAG, adminSecretName, OCIR_SECRET_NAME,
        encryptionSecretName, replicaCount, "cluster-1", auxiliaryImagePath,
        auxiliaryImageVolumeName, miiAuxiliaryImage1, miiAuxiliaryImage2);

    // create domain and verify its running
    logger.info("Creating domain {0} with auxiliary images {1} {2} in namespace {3}",
        domainUid, miiAuxiliaryImage1, miiAuxiliaryImage2, domainNamespace);
    createDomainAndVerify(domainUid, domainCR, domainNamespace,
        adminServerPodName, managedServerPrefix, replicaCount);

    // check configuration for JMS
    checkConfiguredJMSresouce(domainNamespace, adminServerPodName);

    //checking the order of loading for the common mount images, expecting file with content =2
    assertDoesNotThrow(() -> FileUtils.deleteQuietly(Paths.get(RESULTS_ROOT, "/test.txt").toFile()));
    assertDoesNotThrow(() -> copyFileFromPod(domainNamespace,
        adminServerPodName, "weblogic-server",
        auxiliaryImagePath + "/test.txt",
        Paths.get(RESULTS_ROOT, "/test.txt")), " Can't find file in the pod, or failed to copy");

    assertDoesNotThrow(() ->  {
      String fileContent = Files.readAllLines(Paths.get(RESULTS_ROOT, "/test.txt")).get(0);
      assertEquals("2", fileContent, "The content of the file from common mount path "
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
  public void testUpdateDataSourceInDomainUsingAuxiliaryImage() {
    Path multipleAIPath1 = Paths.get(RESULTS_ROOT, "multipleauxiliaryimage1");
    Path modelsPath1 = Paths.get(multipleAIPath1.toString(), "models");


    // create stage dir for auxiliary image with image3
    // replace DataSource URL info in the  model file
    assertDoesNotThrow(() -> replaceStringInFile(Paths.get(modelsPath1.toString(),
        "/multi-model-one-ds.20.yaml").toString(), "xxx.xxx.x.xxx:1521",
        "localhost:7001"),"Can't replace datasource url in the model file");
    assertDoesNotThrow(() -> replaceStringInFile(Paths.get(modelsPath1.toString(),
        "/multi-model-one-ds.20.yaml").toString(), "ORCLCDB",
        "dbsvc"),"Can't replace datasource url in the model file");

    // create image3 with model and wdt installation files
    createAuxiliaryImage(multipleAIPath1.toString(),
        Paths.get(RESOURCE_DIR, "auxiliaryimage", "Dockerfile").toString(), miiAuxiliaryImage3);

    // push image3 to repo for multi node cluster
    if (!DOMAIN_IMAGES_REPO.isEmpty()) {
      logger.info("docker push image {0} to registry {1}", miiAuxiliaryImage3, DOMAIN_IMAGES_REPO);
      assertTrue(dockerPush(miiAuxiliaryImage3), String.format("docker push failed for image %s", miiAuxiliaryImage3));
    }

    // check configuration for DataSource in the running domain
    int adminServiceNodePort
        = getServiceNodePort(domainNamespace, getExternalServicePodName(adminServerPodName), "default");
    assertNotEquals(-1, adminServiceNodePort, "admin server default node port is not valid");
    assertTrue(checkSystemResourceConfig(adminServiceNodePort,
        "JDBCSystemResources/TestDataSource/JDBCResource/JDBCDriverParams",
        "jdbc:oracle:thin:@\\/\\/xxx.xxx.x.xxx:1521\\/ORCLCDB"),"Can't find expected URL configuration for DataSource");

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
  public void testUpdateBaseImageName() {
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
    String imageTag = TestUtils.getDateAndTimeStamp();
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
   * Check the error msg is in introspector pod log, domain events and operator pod log.
   */
  @Test
  @Order(3)
  @DisplayName("Negative Test to create domain with mismatch mount path in auxiliary image and auxiliaryImageVolumes")
  public void testErrorPathDomainMismatchMountPath() {

    OffsetDateTime timestamp = now();
    String errorPathAuxiliaryImage1 = MII_AUXILIARY_IMAGE_NAME + ":errorpathimage1";

    final String auxiliaryImageVolumeName = "auxiliaryImageVolume1";
    final String auxiliaryImagePath = "/errorpath";

    createSecretsForDomain(adminSecretName, encryptionSecretName, errorpathDomainNamespace);

    // create stage dir for auxiliary image
    Path errorpathAIPath1 = Paths.get(RESULTS_ROOT, "errorpathauxiimage1");
    assertDoesNotThrow(() -> FileUtils.deleteDirectory(errorpathAIPath1.toFile()));
    assertDoesNotThrow(() -> Files.createDirectories(errorpathAIPath1));

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
      assertTrue(dockerPush(errorPathAuxiliaryImage1),
          String.format("docker push failed for image %s", errorPathAuxiliaryImage1));
    }

    // create domain custom resource using auxiliary images
    logger.info("Creating domain custom resource with domainUid {0} and auxiliary image {1}",
        domainUid, errorPathAuxiliaryImage1);
    Domain domainCR = createDomainResource(domainUid, errorpathDomainNamespace,
        WEBLOGIC_IMAGE_NAME + ":" + WEBLOGIC_IMAGE_TAG, adminSecretName, OCIR_SECRET_NAME,
        encryptionSecretName, replicaCount, "cluster-1", auxiliaryImagePath,
        auxiliaryImageVolumeName, errorPathAuxiliaryImage1);

    // create domain and verify it is failed
    logger.info("Creating domain {0} with auxiliary image {1} in namespace {2}",
        domainUid, errorPathAuxiliaryImage1, errorpathDomainNamespace);
    assertDoesNotThrow(() -> createDomainCustomResource(domainCR), "createDomainCustomResource throws Exception");

    // check the introspector pod log contains the expected error msg
    String expectedErrorMsg = "Auxiliary Image: Dir '/errorpath' doesn't exist or is empty. Exiting.";
    String introspectorPodName = assertDoesNotThrow(() -> getIntrospectorPodName(domainUid, errorpathDomainNamespace));
    checkPodLogContainsString(errorpathDomainNamespace, introspectorPodName, expectedErrorMsg);

    // check the domain event contains the expected error msg
    checkDomainEventContainsExpectedMsg(opNamespace, errorpathDomainNamespace, domainUid, DOMAIN_PROCESSING_FAILED,
        "Warning", timestamp, expectedErrorMsg);

    // check the operator pod log contains the expected error msg
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
   * Check the error msg is in introspector pod log, domain events and operator pod log.
   */
  @Test
  @Order(4)
  @DisplayName("Negative Test to create domain without WDT binary")
  public void testErrorPathDomainMissingWDTBinary() {

    OffsetDateTime timestamp = now();
    String errorPathAuxiliaryImage2 = MII_AUXILIARY_IMAGE_NAME + ":errorpathimage2";

    final String auxiliaryImageVolumeName = "auxiliaryImageVolume1";
    final String auxiliaryImagePath = "/auxiliary";

    createSecretsForDomain(adminSecretName, encryptionSecretName, errorpathDomainNamespace);

    // create stage dir for common mount image
    Path errorpathAIPath2 = Paths.get(RESULTS_ROOT, "errorpathauxiimage2");
    assertDoesNotThrow(() -> FileUtils.deleteDirectory(errorpathAIPath2.toFile()));
    assertDoesNotThrow(() -> Files.createDirectories(errorpathAIPath2));

    // create models dir and copy model for image
    Path modelsPath2 = Paths.get(errorpathAIPath2.toString(), "models");
    assertDoesNotThrow(() -> Files.createDirectories(modelsPath2));
    assertDoesNotThrow(() -> Files.copy(
        Paths.get(MODEL_DIR, MII_BASIC_WDT_MODEL_FILE),
        Paths.get(modelsPath2.toString(), MII_BASIC_WDT_MODEL_FILE),
        StandardCopyOption.REPLACE_EXISTING));

    // create image with model and no wdt installation files
    createAuxiliaryImage(errorpathAIPath2.toString(),
        Paths.get(RESOURCE_DIR, "auxiliaryimage", "Dockerfile").toString(), errorPathAuxiliaryImage2);

    // push image to repo for multi node cluster
    if (!DOMAIN_IMAGES_REPO.isEmpty()) {
      logger.info("docker push image {0} to registry {1}", errorPathAuxiliaryImage2, DOMAIN_IMAGES_REPO);
      assertTrue(dockerPush(errorPathAuxiliaryImage2),
          String.format("docker push failed for image %s", errorPathAuxiliaryImage2));
    }

    // create domain custom resource using auxiliary images
    logger.info("Creating domain custom resource with domainUid {0} and auxiliary image {1}",
        domainUid, errorPathAuxiliaryImage2);
    Domain domainCR = createDomainResource(domainUid, errorpathDomainNamespace,
        WEBLOGIC_IMAGE_NAME + ":" + WEBLOGIC_IMAGE_TAG, adminSecretName, OCIR_SECRET_NAME,
        encryptionSecretName, replicaCount, "cluster-1", auxiliaryImagePath,
        auxiliaryImageVolumeName, errorPathAuxiliaryImage2);

    // create domain and verify it is failed
    logger.info("Creating domain {0} with common mount image {1} in namespace {2}",
        domainUid, errorPathAuxiliaryImage2, errorpathDomainNamespace);
    assertDoesNotThrow(() -> createDomainCustomResource(domainCR), "createDomainCustomResource throws Exception");

    // check the introspector pod log contains the expected error msg
    String expectedErrorMsg = "The domain resource 'spec.domainHomeSourceType' is 'FromModel'  and "
        + "a WebLogic Deploy Tool (WDT) install is not located at  'spec.configuration.model.wdtInstallHome'  "
        + "which is currently set to '/auxiliary/weblogic-deploy'";
    String introspectorPodName = assertDoesNotThrow(() -> getIntrospectorPodName(domainUid, errorpathDomainNamespace));
    checkPodLogContainsString(errorpathDomainNamespace, introspectorPodName, expectedErrorMsg);

    // check the domain event contains the expected error msg
    checkDomainEventContainsExpectedMsg(opNamespace, errorpathDomainNamespace, domainUid, DOMAIN_PROCESSING_FAILED,
        "Warning", timestamp, expectedErrorMsg);

    // check the operator pod log contains the expected error msg
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
   * Negative Test to create domain without domain model file, the auxiliary image contains only sparse JMS config.
   * Check the error message is in introspector pod log, domain events and operator pod log
   */
  @Test
  @Order(5)
  @DisplayName("Negative Test to create domain without domain model file, only having sparse JMS config")
  public void testErrorPathDomainMissingDomainConfig() {

    OffsetDateTime timestamp = now();
    String errorPathAuxiliaryImage3 = MII_AUXILIARY_IMAGE_NAME + ":errorpathimage3";

    final String auxiliaryImageVolumeName = "auxiliaryImageVolume1";
    final String auxiliaryImagePath = "/auxiliary";

    createSecretsForDomain(adminSecretName, encryptionSecretName, errorpathDomainNamespace);

    // create stage dir for auxiliary image
    Path errorpathAIPath3 = Paths.get(RESULTS_ROOT, "errorpathauxiimage3");
    assertDoesNotThrow(() -> FileUtils.deleteDirectory(errorpathAIPath3.toFile()));
    assertDoesNotThrow(() -> Files.createDirectories(errorpathAIPath3));

    // create models dir and copy model for image
    Path modelsPath3 = Paths.get(errorpathAIPath3.toString(), "models");
    assertDoesNotThrow(() -> Files.createDirectories(modelsPath3));
    assertDoesNotThrow(() -> Files.copy(
        Paths.get(MODEL_DIR, "model.jms2.yaml"),
        Paths.get(modelsPath3.toString(), "model.jms2.yaml"),
        StandardCopyOption.REPLACE_EXISTING));

    // unzip WDT installation file into work dir
    unzipWDTInstallationFile(errorpathAIPath3.toString());

    // create image1 with model and wdt installation files
    createAuxiliaryImage(errorpathAIPath3.toString(),
        Paths.get(RESOURCE_DIR, "auxiliaryimage", "Dockerfile").toString(), errorPathAuxiliaryImage3);

    // push image to repo for multi node cluster
    if (!DOMAIN_IMAGES_REPO.isEmpty()) {
      logger.info("docker push image {0} to registry {1}", errorPathAuxiliaryImage3, DOMAIN_IMAGES_REPO);
      assertTrue(dockerPush(errorPathAuxiliaryImage3),
          String.format("docker push failed for image %s", errorPathAuxiliaryImage3));
    }

    // create domain custom resource using auxiliary images
    logger.info("Creating domain custom resource with domainUid {0} and auxiliary image {1}",
        domainUid, errorPathAuxiliaryImage3);
    Domain domainCR = createDomainResource(domainUid, errorpathDomainNamespace,
        WEBLOGIC_IMAGE_NAME + ":" + WEBLOGIC_IMAGE_TAG, adminSecretName, OCIR_SECRET_NAME,
        encryptionSecretName, replicaCount, "cluster-1", auxiliaryImagePath,
        auxiliaryImageVolumeName, errorPathAuxiliaryImage3);

    // create domain and verify it is failed
    logger.info("Creating domain {0} with auxiliary image {1} in namespace {2}",
        domainUid, errorPathAuxiliaryImage3, errorpathDomainNamespace);
    assertDoesNotThrow(() -> createDomainCustomResource(domainCR), "createDomainCustomResource throws Exception");

    // check the introspector pod log contains the expected error msg
    String expectedErrorMsg =
        "createDomain did not find the required domainInfo section in the model file /auxiliary/models/model.jms2.yaml";
    String introspectorPodName = assertDoesNotThrow(() -> getIntrospectorPodName(domainUid, errorpathDomainNamespace));
    checkPodLogContainsString(errorpathDomainNamespace, introspectorPodName, expectedErrorMsg);

    // check the domain event contains the expected error msg
    checkDomainEventContainsExpectedMsg(opNamespace, errorpathDomainNamespace, domainUid, DOMAIN_PROCESSING_FAILED,
        "Warning", timestamp, expectedErrorMsg);

    // check the operator pod log contains the expected error msg
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
   * Negative Test to patch the existing domain using a custom mount command that's guaranteed to fail.
   * Specify domain.spec.serverPod.auxiliaryImages.command to a custom mount command instead of the default one, which
   * defaults to "cp -R $AUXILIARY_IMAGE_PATH/* $TARGET_MOUNT_PATH"
   * Check the error msg in introspector pod log, domain events and operator pod log.
   * Restore the domain by removing the custom mount command.
   */
  @Test
  @Order(6)
  @DisplayName("Negative Test to patch domain using a custom mount command that's guaranteed to fail")
  public void testErrorPathDomainWithFailCustomMountCommand() {

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
    logger.info("Auxiliary Image patch string: " +  patchStr);

    V1Patch patch = new V1Patch((patchStr).toString());

    boolean aiPatched = assertDoesNotThrow(() ->
            patchDomainCustomResource(domainUid, domainNamespace, patch, "application/json-patch+json"),
        "patchDomainCustomResource(Auxiliary Image)  failed ");
    assertTrue(aiPatched, "patchDomainCustomResource(auxiliary image) failed");

    // check the introspector pod log contains the expected error msg
    String expectedErrorMsg = "Auxiliary Image: Command 'exit 1' execution failed in container";
    String introspectorPodName = assertDoesNotThrow(() -> getIntrospectorPodName(domainUid, domainNamespace));
    checkPodLogContainsString(domainNamespace, introspectorPodName, expectedErrorMsg);

    // check the domain event contains the expected error msg
    checkDomainEventContainsExpectedMsg(opNamespace, domainNamespace, domainUid, DOMAIN_PROCESSING_FAILED,
        "Warning", timestamp, expectedErrorMsg);

    // check the operator pod log contains the expected error msg
    String operatorPodName =
        assertDoesNotThrow(() -> getOperatorPodName(OPERATOR_RELEASE_NAME, opNamespace));
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
    logger.info("Auxiliary Image patch string: " +  patchStr);

    V1Patch patch1 = new V1Patch((patchStr).toString());

    aiPatched = assertDoesNotThrow(() ->
            patchDomainCustomResource(domainUid, domainNamespace, patch1, "application/json-patch+json"),
        "patchDomainCustomResource(Auxiliary Image)  failed ");
    assertTrue(aiPatched, "patchDomainCustomResource(auxiliary image) failed");

    // check the admin server is up and running
    checkPodReady(adminServerPodName, domainUid, domainNamespace);
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
    logger.info("Auxiliary Image patch string: " +  patchStr);

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
    assertTrue(checkSystemResourceConfiguration(adminServiceNodePort, "JMSSystemResources",
        "TestClusterJmsModule2", "200"), "JMSSystemResources not found");
    logger.info("Found the JMSSystemResource configuration");
  }

  private void checkConfiguredJDBCresouce(String domainNamespace, String adminServerPodName) {
    int adminServiceNodePort
        = getServiceNodePort(domainNamespace, getExternalServicePodName(adminServerPodName), "default");
    assertNotEquals(-1, adminServiceNodePort, "admin server default node port is not valid");
    assertTrue(checkSystemResourceConfig(adminServiceNodePort,
        "JDBCSystemResources/TestDataSource/JDBCResource/JDBCDriverParams",
        "jdbc:oracle:thin:@\\/\\/localhost:7001\\/dbsvc"), "Can't find expected URL configuration for DataSource");
    logger.info("Found the DataResource configuration");
  }

}
