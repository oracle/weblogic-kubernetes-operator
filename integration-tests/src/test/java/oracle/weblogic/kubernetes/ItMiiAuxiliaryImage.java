// Copyright (c) 2021, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.weblogic.kubernetes;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import io.kubernetes.client.custom.V1Patch;
import io.kubernetes.client.openapi.models.CoreV1Event;
import oracle.weblogic.domain.AuxiliaryImage;
import oracle.weblogic.domain.AuxiliaryImageVolume;
import oracle.weblogic.domain.Domain;
import oracle.weblogic.kubernetes.actions.impl.primitive.Command;
import oracle.weblogic.kubernetes.actions.impl.primitive.CommandParams;
import oracle.weblogic.kubernetes.annotations.IntegrationTest;
import oracle.weblogic.kubernetes.annotations.Namespaces;
import oracle.weblogic.kubernetes.logging.LoggingFacade;
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
import static oracle.weblogic.kubernetes.TestConstants.MII_AUXILIARY_IMAGE_NAME;
import static oracle.weblogic.kubernetes.TestConstants.MII_BASIC_APP_NAME;
import static oracle.weblogic.kubernetes.TestConstants.MII_BASIC_IMAGE_TAG;
import static oracle.weblogic.kubernetes.TestConstants.MII_BASIC_WDT_MODEL_FILE;
import static oracle.weblogic.kubernetes.TestConstants.OCIR_SECRET_NAME;
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
import static oracle.weblogic.kubernetes.actions.TestActions.getDomainCustomResource;
import static oracle.weblogic.kubernetes.actions.TestActions.getServiceNodePort;
import static oracle.weblogic.kubernetes.actions.TestActions.now;
import static oracle.weblogic.kubernetes.actions.TestActions.patchDomainCustomResource;
import static oracle.weblogic.kubernetes.assertions.TestAssertions.secretExists;
import static oracle.weblogic.kubernetes.assertions.TestAssertions.verifyRollingRestartOccurred;
import static oracle.weblogic.kubernetes.utils.CommonMiiTestUtils.createDomainResource;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.checkEvent;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.checkSystemResourceConfig;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.checkSystemResourceConfiguration;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.createDomainAndVerify;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.createOcirRepoSecret;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.createSecretWithUsernamePassword;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.deleteDomainResource;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.getExternalServicePodName;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.getPodsWithTimeStamps;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.installAndVerifyOperator;
import static oracle.weblogic.kubernetes.utils.FileUtils.replaceStringInFile;
import static oracle.weblogic.kubernetes.utils.FileUtils.unzipWDTInstallationFile;
import static oracle.weblogic.kubernetes.utils.K8sEvents.DOMAIN_PROCESSING_FAILED;
import static oracle.weblogic.kubernetes.utils.K8sEvents.getEvent;
import static oracle.weblogic.kubernetes.utils.ThreadSafeLogger.getLogger;
import static org.awaitility.Awaitility.with;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
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
    int adminServiceNodePort
        = getServiceNodePort(domainNamespace, getExternalServicePodName(adminServerPodName), "default");
    assertNotEquals(-1, adminServiceNodePort, "admin server default node port is not valid");
    assertTrue(checkSystemResourceConfiguration(adminServiceNodePort, "JMSSystemResources",
        "TestClusterJmsModule2", "200"), "JMSSystemResources not found");
    logger.info("Found the JMSSystemResource configuration");
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

    assertTrue(checkSystemResourceConfig(adminServiceNodePort,
        "JDBCSystemResources/TestDataSource/JDBCResource/JDBCDriverParams",
        "jdbc:oracle:thin:@\\/\\/localhost:7001\\/dbsvc"), "Can't find expected URL configuration for DataSource");

    logger.info("Found the DataResource configuration");
  }

  /**
   * Negative Test to create domain with mismatch mount path in auxiliary image and auxiliaryImageVolumes.
   * in auxiliaryImageVolumes, set mountPath to "/errorpath"
   * in auxiliary image, set AUXILIARY_IMAGE_PATH to "/auxiliary"
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

    // check the domain event contains the expected error msg
    checkEvent(opNamespace, errorpathDomainNamespace, domainUid, DOMAIN_PROCESSING_FAILED, "Warning", timestamp);
    CoreV1Event event =
        getEvent(opNamespace, errorpathDomainNamespace, domainUid, DOMAIN_PROCESSING_FAILED, "Warning", timestamp);
    String expectedErrorMsg = "Failed to complete processing domain resource domain1 due to: "
        + "Auxiliary Image: Dir '/errorpath' doesn't exist or is empty. Exiting.";
    assertTrue(event.getMessage().contains(expectedErrorMsg),
        String.format("The event message does not contain the expected error msg %s", expectedErrorMsg));

    // delete domain1
    deleteDomainResource(errorpathDomainNamespace, domainUid);
  }

  /**
   * Negative Test to create domain without WDT binary.
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

    // check the domain event contains the expected error msg
    checkEvent(opNamespace, errorpathDomainNamespace, domainUid, DOMAIN_PROCESSING_FAILED, "Warning", timestamp);
    CoreV1Event event =
        getEvent(opNamespace, errorpathDomainNamespace, domainUid, DOMAIN_PROCESSING_FAILED, "Warning", timestamp);
    String expectedErrorMsg = "The domain resource 'spec.domainHomeSourceType' is 'FromModel'  and "
        + "a WebLogic Deploy Tool (WDT) install is not located at  'spec.configuration.model.wdtInstallHome'  "
        + "which is currently set to '/auxiliary/weblogic-deploy'";
    assertTrue(event.getMessage().contains(expectedErrorMsg),
        String.format("The event message does not contain the expected error msg %s", expectedErrorMsg));

    // delete domain1
    deleteDomainResource(errorpathDomainNamespace, domainUid);
  }

  /**
   * Negative Test to create domain without domain model file, the auxiliary image contains only sparse JMS config.
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

    // check the domain event contains the expected error msg
    checkEvent(opNamespace, errorpathDomainNamespace, domainUid, DOMAIN_PROCESSING_FAILED, "Warning", timestamp);
    CoreV1Event event =
        getEvent(opNamespace, errorpathDomainNamespace, domainUid, DOMAIN_PROCESSING_FAILED, "Warning", timestamp);
    String expectedErrorMsg =
        "createDomain did not find the required domainInfo section in the model file /auxiliary/models/model.jms2.yaml";
    assertTrue(event.getMessage().contains(expectedErrorMsg),
        String.format("The event message does not contain the expected error msg %s", expectedErrorMsg));

    // delete domain1
    deleteDomainResource(errorpathDomainNamespace, domainUid);
  }

  /**
   * Negative Test to create domain using a custom mount command that's guaranteed to fail, for example:  exit 1.
   */
  @Test
  @Order(6)
  @DisplayName("Negative Test to create domain using a custom mount command that's guaranteed to fail")
  public void testErrorPathDomainWithFailCustomMountCommand() {

    OffsetDateTime timestamp = now();
    String errorPathAuxiliaryImage4 = MII_AUXILIARY_IMAGE_NAME + ":errorpathimage4";

    final String auxiliaryImageVolumeName = "auxiliaryImageVolume1";
    final String auxiliaryImagePath = "/auxiliary";

    createSecretsForDomain(adminSecretName, encryptionSecretName, errorpathDomainNamespace);
    
    // create stage dir for auxiliary image
    Path errorpathAIPath4 = Paths.get(RESULTS_ROOT, "errorpathauxiimage4");
    assertDoesNotThrow(() -> FileUtils.deleteDirectory(errorpathAIPath4.toFile()));
    assertDoesNotThrow(() -> Files.createDirectories(errorpathAIPath4));

    // create models dir and copy model for image
    Path modelsPath4 = Paths.get(errorpathAIPath4.toString(), "models");
    assertDoesNotThrow(() -> Files.createDirectories(modelsPath4));
    assertDoesNotThrow(() -> Files.copy(
        Paths.get(MODEL_DIR, MII_BASIC_WDT_MODEL_FILE),
        Paths.get(modelsPath4.toString(), MII_BASIC_WDT_MODEL_FILE),
        StandardCopyOption.REPLACE_EXISTING));

    // unzip WDT installation file into work dir
    unzipWDTInstallationFile(errorpathAIPath4.toString());

    // create image1 with model and wdt installation files
    createAuxiliaryImage(errorpathAIPath4.toString(),
        Paths.get(RESOURCE_DIR, "auxiliaryimage", "Dockerfile").toString(), errorPathAuxiliaryImage4);

    // push image to repo for multi node cluster
    if (!DOMAIN_IMAGES_REPO.isEmpty()) {
      logger.info("docker push image {0} to registry {1}", errorPathAuxiliaryImage4, DOMAIN_IMAGES_REPO);
      assertTrue(dockerPush(errorPathAuxiliaryImage4),
          String.format("docker push failed for image %s", errorPathAuxiliaryImage4));
    }

    // create domain custom resource using common mount and images
    logger.info("Creating domain custom resource with domainUid {0} and auxiliary image {1}",
        domainUid, errorPathAuxiliaryImage4);
    AuxiliaryImageVolume auxiliaryImageVolume = new AuxiliaryImageVolume()
        .name(auxiliaryImageVolumeName)
        .mountPath(auxiliaryImagePath);

    AuxiliaryImage auxiliaryImage = new AuxiliaryImage()
        .image(errorPathAuxiliaryImage4)
        .imagePullPolicy("IfNotPresent")
        .volume(auxiliaryImageVolumeName)
        .command("exit 1");

    Domain domainCR = createDomainResource(domainUid, errorpathDomainNamespace,
        WEBLOGIC_IMAGE_NAME + ":" + WEBLOGIC_IMAGE_TAG, adminSecretName, OCIR_SECRET_NAME,
        encryptionSecretName, replicaCount, "cluster-1", Arrays.asList(auxiliaryImageVolume),
        Arrays.asList(auxiliaryImage));

    // create domain and verify it is failed
    logger.info("Creating domain {0} with auxiliary image {1} in namespace {2}",
        domainUid, errorPathAuxiliaryImage4, errorpathDomainNamespace);

    assertDoesNotThrow(() -> createDomainCustomResource(domainCR),
        "createDomainCustomResource throws Exception");

    // check the domain event contains the expected error msg
    checkEvent(opNamespace, errorpathDomainNamespace, domainUid, DOMAIN_PROCESSING_FAILED, "Warning", timestamp);
    CoreV1Event event =
        getEvent(opNamespace, errorpathDomainNamespace, domainUid, DOMAIN_PROCESSING_FAILED, "Warning", timestamp);
    String expectedErrorMsg = "Failed to complete processing domain resource domain1 due to: "
        + "Auxiliary Image: Command 'exit 1' execution failed in container";
    assertTrue(event.getMessage().contains(expectedErrorMsg),
        String.format("The event message does not contain the expected error msg %s", expectedErrorMsg));

    // delete domain1
    deleteDomainResource(errorpathDomainNamespace, domainUid);
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
}
