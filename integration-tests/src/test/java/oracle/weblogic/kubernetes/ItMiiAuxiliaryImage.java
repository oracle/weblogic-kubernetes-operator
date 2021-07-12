// Copyright (c) 2021, Oracle and/or its affiliates.
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

import io.kubernetes.client.custom.V1Patch;
import oracle.weblogic.domain.AuxiliaryImage;
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
import static oracle.weblogic.kubernetes.actions.TestActions.defaultAppParams;
import static oracle.weblogic.kubernetes.actions.TestActions.deleteImage;
import static oracle.weblogic.kubernetes.actions.TestActions.dockerPush;
import static oracle.weblogic.kubernetes.actions.TestActions.getDomainCustomResource;
import static oracle.weblogic.kubernetes.actions.TestActions.getServiceNodePort;
import static oracle.weblogic.kubernetes.actions.TestActions.patchDomainCustomResource;
import static oracle.weblogic.kubernetes.assertions.TestAssertions.verifyRollingRestartOccurred;
import static oracle.weblogic.kubernetes.utils.CommonMiiTestUtils.createDomainResource;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.checkSystemResourceConfig;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.checkSystemResourceConfiguration;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.createDomainAndVerify;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.createOcirRepoSecret;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.createSecretWithUsernamePassword;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.getExternalServicePodName;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.getPodsWithTimeStamps;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.installAndVerifyOperator;
import static oracle.weblogic.kubernetes.utils.FileUtils.replaceStringInFile;
import static oracle.weblogic.kubernetes.utils.FileUtils.unzipWDTInstallationFile;
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
  private static LoggingFacade logger = null;
  private String domainUid = "domain1";
  private static String miiAuxiliaryImage1 = MII_AUXILIARY_IMAGE_NAME + ":" + MII_BASIC_IMAGE_TAG + "1";
  private static String miiAuxiliaryImage2 = MII_AUXILIARY_IMAGE_NAME + ":" + MII_BASIC_IMAGE_TAG + "2";
  private static String miiAuxiliaryImage3 = MII_AUXILIARY_IMAGE_NAME + ":" + MII_BASIC_IMAGE_TAG + "3";
  private static Map<String, OffsetDateTime> podsWithTimeStamps = null;
  private final String adminServerPodName = domainUid + "-admin-server";
  private final String managedServerPrefix = domainUid + "-managed-server";
  private final int replicaCount = 2;

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
  public static void initAll(@Namespaces(2) List<String> namespaces) {
    logger = getLogger();
    // get a new unique opNamespace
    logger.info("Creating unique namespace for Operator");
    assertNotNull(namespaces.get(0), "Namespace list is null");
    opNamespace = namespaces.get(0);

    logger.info("Creating unique namespace for Domain1");
    assertNotNull(namespaces.get(1), "Namespace list is null");
    domainNamespace = namespaces.get(1);

    // install and verify operator
    installAndVerifyOperator(opNamespace, domainNamespace);
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
}
