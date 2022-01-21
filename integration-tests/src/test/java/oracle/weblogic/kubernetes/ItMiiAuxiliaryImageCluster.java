// Copyright (c) 2021, 2022, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.weblogic.kubernetes;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.Map;

import oracle.weblogic.kubernetes.actions.impl.primitive.Command;
import oracle.weblogic.kubernetes.actions.impl.primitive.CommandParams;
import oracle.weblogic.kubernetes.annotations.IntegrationTest;
import oracle.weblogic.kubernetes.annotations.Namespaces;
import oracle.weblogic.kubernetes.logging.LoggingFacade;
import oracle.weblogic.kubernetes.utils.ExecResult;
import org.apache.commons.io.FileUtils;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import static oracle.weblogic.kubernetes.TestConstants.ADMIN_PASSWORD_DEFAULT;
import static oracle.weblogic.kubernetes.TestConstants.ADMIN_USERNAME_DEFAULT;
import static oracle.weblogic.kubernetes.TestConstants.DOMAIN_IMAGES_REPO;
import static oracle.weblogic.kubernetes.TestConstants.MII_AUXILIARY_IMAGE_NAME;
import static oracle.weblogic.kubernetes.TestConstants.MII_BASIC_IMAGE_TAG;
import static oracle.weblogic.kubernetes.TestConstants.RESULTS_ROOT;
import static oracle.weblogic.kubernetes.actions.ActionConstants.MODEL_DIR;
import static oracle.weblogic.kubernetes.actions.ActionConstants.RESOURCE_DIR;
import static oracle.weblogic.kubernetes.actions.TestActions.dockerPush;
import static oracle.weblogic.kubernetes.actions.TestActions.getServiceNodePort;
import static oracle.weblogic.kubernetes.utils.CommonMiiTestUtils.patchDomainClusterWithAuxImageAndVerify;
import static oracle.weblogic.kubernetes.utils.CommonMiiTestUtils.readFilesInPod;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.checkSystemResourceConfiguration;
import static oracle.weblogic.kubernetes.utils.FileUtils.unzipWDTInstallationFile;
import static oracle.weblogic.kubernetes.utils.ImageUtils.createOcirRepoSecret;
import static oracle.weblogic.kubernetes.utils.OperatorUtils.installAndVerifyOperator;
import static oracle.weblogic.kubernetes.utils.PodUtils.getExternalServicePodName;
import static oracle.weblogic.kubernetes.utils.SecretUtils.createSecretWithUsernamePassword;
import static oracle.weblogic.kubernetes.utils.ThreadSafeLogger.getLogger;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("Test to create model in image domain using auxiliary image containing the cluster configuration")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@IntegrationTest
@Disabled("Temporarily disabled due to auxiliary image 4.0 changes.")
class ItMiiAuxiliaryImageCluster {

  private static String opNamespace = null;
  private static String domainNamespace = null;
  private static LoggingFacade logger = null;
  private static int auxiliaryImageNumberIndex = 1;
  private final String domainUid = "domain1";
  private final String adminServerPodName = domainUid + "-admin-server";
  private final String managedServerPrefix = domainUid + "-managed-server";
  private final String clusterName = "cluster-1";
  private final int replicaCount = 2;
  private final int clusterIndex = 0;
  private final String miiAuxiliaryImagePrefix = MII_AUXILIARY_IMAGE_NAME + ":" + MII_BASIC_IMAGE_TAG;
  private final String auxiliaryImageVolumeName = "auxiliaryImageVolumeCluster";
  private final String auxiliaryImagePath = "/auxiliary";
  private final String customDir = "customdir";

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
   * Create a domain using multiple auxiliary images. One auxiliary image containing the domain configuration
   * and other two auxiliary images contain the cluster configuration, verify the domain is running and
   * files in cluster scope image only copied to WLS server within the cluster.
   */
  @Test
  @Order(1)
  @DisplayName("Test to create domain using multiple auxiliary images containing the doamin and cluster configuration")
  void testCreateDomainUsingAuxiliaryImagesWClusterConfig() {
    final List<String> auxiliaryImageDomainScopeNames = List.of(miiAuxiliaryImagePrefix + "1");
    final List<String> auxiliaryImageClusterScopeNames =
        List.of(miiAuxiliaryImagePrefix + "2", miiAuxiliaryImagePrefix + "3");
    auxiliaryImageNumberIndex += 3;

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

    // create stage dir for domain scope auxiliary image with auxiliaryImageDomainScopeNames
    Path multipleAiPath1 = Paths.get(RESULTS_ROOT, "multipleauxiliaryimage1");
    // create models dir and copy model, archive files if any for auxiliaryImageDomainScopeNames
    Path modelsPath = Paths.get(multipleAiPath1.toString(), "models");
    logger.info("Create models dir {0} and copy model file {1}",
        multipleAiPath1.toString(), Paths.get(MODEL_DIR, "model-auxiliaryimage-cluster.yaml").toString());
    assertDoesNotThrow(() -> FileUtils.deleteDirectory(modelsPath.toFile()));
    assertDoesNotThrow(() -> Files.createDirectories(modelsPath));
    assertDoesNotThrow(() -> Files.copy(
        Paths.get(MODEL_DIR, "model-auxiliaryimage-cluster.yaml"),
        Paths.get(modelsPath.toString(), "model-auxiliaryimage-cluster.yaml"),
        StandardCopyOption.REPLACE_EXISTING));

    // unzip WDT installation file into work dir
    unzipWDTInstallationFile(multipleAiPath1.toString());

    // create auxiliaryImageDomainScopeNames with model and wdt installation files
    logger.info("Create auxiliary image: {0}", auxiliaryImageDomainScopeNames.get(0));
    createAuxiliaryImage(multipleAiPath1.toString(),
        Paths.get(RESOURCE_DIR, "auxiliaryimage", "Dockerfile").toString(),
        auxiliaryImageDomainScopeNames.get(0));

    // create stage and customdir dir for cluster scope auxiliary images with auxiliaryImageClusterScopeNames
    Path multipleAiPath2 = Paths.get(RESULTS_ROOT, "multipleauxiliaryimage2");
    Path customDirPath = Paths.get(multipleAiPath2.toString(), customDir);
    logger.info("Create a custom dir {0} and copy two text files {1} and {2}",
        customDirPath.toString(), "clusterai-1.txt", "clusterai-2.txt");
    assertDoesNotThrow(() -> FileUtils.deleteDirectory(customDirPath.toFile()));
    assertDoesNotThrow(() -> Files.createDirectories(customDirPath));

    // create and copy two plain text files
    Map<Path, String> fileNames =
        Map.of(Paths.get(customDirPath.toString(), "clusterai-1.txt"), "file 1 in customdir",
               Paths.get(customDirPath.toString(), "clusterai-2.txt"), "file 2 in customdir");
    fileNames.forEach((filePath, fileContent) -> {
      assertDoesNotThrow(() -> Files.write(filePath, fileContent.getBytes()),
          "Can't write to file " + filePath);
    });

    // create an auxiliary image containing each plain text file, respectively
    auxiliaryImageClusterScopeNames.stream().forEach(
        imageName -> {
          logger.info("Create auxiliary image: {0}", imageName);
          createAuxiliaryImage(multipleAiPath2.toString(),
              Paths.get(RESOURCE_DIR, "auxiliaryimage", "Dockerfile").toString(), imageName);
      }
    );

    // create domain custom resource using 3 auxiliary images
    logger.info("Creating domain custom resource with domainUid {0} and auxiliary images {1} {2}",
        domainUid, auxiliaryImageDomainScopeNames.toString(), auxiliaryImageClusterScopeNames.toString());
    /* Commented out due to auxiliary image 4.0 changes.
    Domain domainCR = createDomainResourceWithAuxiliaryImageClusterScope(domainUid, domainNamespace,
        WEBLOGIC_IMAGE_TO_USE_IN_SPEC, adminSecretName, OCIR_SECRET_NAME,
        encryptionSecretName, replicaCount, clusterName,
        Map.of(auxiliaryImagePath, List.of(auxiliaryImageVolumeName)),
        auxiliaryImageDomainScopeNames, auxiliaryImageClusterScopeNames);

    // create domain and verify its running
    logger.info("Creating domain {0} with auxiliary images {1} {2} in namespace {3}",
        domainUid, auxiliaryImageDomainScopeNames.toString(),
        auxiliaryImageClusterScopeNames.toString(), domainNamespace);
    createDomainAndVerify(domainUid, domainCR, domainNamespace,
        adminServerPodName, managedServerPrefix, replicaCount);
     */

    // verify that plain text files only copied to servers in the cluster but not copied to admin server
    fileNames.forEach((filePath, fileContent) -> {
      verifyFileInPod(Paths.get(auxiliaryImagePath, customDir, filePath.getFileName().toString()).toString(),
          fileContent);
    });
  }

  /**
   * Patch a domain using auxiliary image to add a new cluster configuration,
   * verify the domain is patched and rolling restarted and
   * file in cluster scope image only copied to WLS server within the cluster.
   */
  @Test
  @Order(2)
  @DisplayName("Patch a domain using auxiliary image to add a new cluster configuration")
  void testPatchDomainToAddClusterConfigUsingAuxiliaryImage() {
    final int addAuxImageLoc = 2;
    final String miiAuxiliaryImage = miiAuxiliaryImagePrefix + auxiliaryImageNumberIndex;
    auxiliaryImageNumberIndex += 1;

    // create stage dir for cluster scope auxiliary images with miiAuxiliaryImage
    Path customAiPath = Paths.get(RESULTS_ROOT, "multipleauxiliaryimage_add");
    Path customDirPath = Paths.get(customAiPath.toString(), customDir);
    assertDoesNotThrow(() -> FileUtils.deleteDirectory(customDirPath.toFile()));
    assertDoesNotThrow(() -> Files.createDirectories(customDirPath));
    logger.info("Create a custom dir {0} and copy text file {1}",
        customDirPath.toString(), "clusteraiAdd.txt");

    // create a plain text file
    Path fileInCustomDir = Paths.get(customDirPath.toString(), "clusteraiAdd.txt");
    String fileContent = "patch the domain to add";
    assertDoesNotThrow(() -> Files.write(fileInCustomDir, fileContent.getBytes()),
        "Can't write to file " + fileInCustomDir);

    // create image containing plain text file clusteraiAdd.txt
    logger.info("Create auxiliary image: {0}", miiAuxiliaryImage);
    createAuxiliaryImage(customAiPath.toString(),
        Paths.get(RESOURCE_DIR, "auxiliaryimage", "Dockerfile").toString(), miiAuxiliaryImage);

    // push image to repo for multi node cluster
    if (!DOMAIN_IMAGES_REPO.isEmpty()) {
      logger.info("docker push image {0} to registry {1}", miiAuxiliaryImage, DOMAIN_IMAGES_REPO);
      assertTrue(dockerPush(miiAuxiliaryImage), String.format("docker push failed for image %s", miiAuxiliaryImage));
    }

    // patch a domain using auxiliary image to add a new cluster configuration
    logger.info("Patch domain to add a new cluster config to cluster {0}", clusterName);
    patchDomainClusterWithAuxImageAndVerify(domainUid, domainNamespace, managedServerPrefix, replicaCount,
        clusterIndex, auxiliaryImageVolumeName, miiAuxiliaryImage, addAuxImageLoc, "add");

    // verify that plain text file only copied to servers in the cluster but not copied to admin server
    logger.info("Verify that plain text file only copied to servers in the cluster but not copied to admin server");
    verifyFileInPod(Paths.get(auxiliaryImagePath, customDir, fileInCustomDir.getFileName().toString()).toString(),
        fileContent);
  }

  /**
   * Patch a domain using auxiliary image to replace an existing cluster configuration,
   * verify the domain is patched and rolling restarted,
   * files in cluster scope image only copied to WLS server within the cluster.
   */
  @Test
  @Order(3)
  @DisplayName("Patch a domain using auxiliary image to replace an existing cluster configuration")
  void testPatchDomainToReplaceClusterConfigUsingAuxiliaryImage() {
    final int replaceAuxImageLoc = 2;
    final String miiAuxiliaryImage = miiAuxiliaryImagePrefix + auxiliaryImageNumberIndex;
    auxiliaryImageNumberIndex += 1;

    // create stage and customdir dir for cluster scope auxiliary images with miiAuxiliaryImage
    Path customAiPath = Paths.get(RESULTS_ROOT, "multipleauxiliaryimage_replace");
    Path customDirPath = Paths.get(customAiPath.toString(), customDir);
    assertDoesNotThrow(() -> FileUtils.deleteDirectory(customDirPath.toFile()));
    assertDoesNotThrow(() -> Files.createDirectories(customDirPath));
    logger.info("Create a custom dir {0} and copy text file {1}",
        customDirPath.toString(), "clusteraiReplace.txt");

    // create a plain text file
    Path fileInCustomDir = Paths.get(customDirPath.toString(), "clusteraiReplace.txt");
    String fileContent = "patch the domain to replace";
    assertDoesNotThrow(() -> Files.write(fileInCustomDir, fileContent.getBytes()),
        "Can't write to file " + fileInCustomDir);

    // create image with plain text file clusteraiReplace.txt
    logger.info("Create auxiliary image: {0}", miiAuxiliaryImage);
    createAuxiliaryImage(customAiPath.toString(),
        Paths.get(RESOURCE_DIR, "auxiliaryimage", "Dockerfile").toString(), miiAuxiliaryImage);

    // push image to repo for multi node cluster
    if (!DOMAIN_IMAGES_REPO.isEmpty()) {
      logger.info("docker push image {0} to registry {1}", miiAuxiliaryImage, DOMAIN_IMAGES_REPO);
      assertTrue(dockerPush(miiAuxiliaryImage), String.format("docker push failed for image %s", miiAuxiliaryImage));
    }

    // patch a domain using auxiliary image to replace an existing cluster configuration
    logger.info("Patch domain to replace an existing cluster config to cluster {0}", clusterName);
    patchDomainClusterWithAuxImageAndVerify(domainUid, domainNamespace, managedServerPrefix, replicaCount,
        clusterIndex, auxiliaryImageVolumeName, miiAuxiliaryImage, replaceAuxImageLoc, "replace");

    // verify that plain text file only copied to servers in the cluster but not copied to admin server
    logger.info("Verify that plain text file only copied to servers in the cluster but not copied to admin server");
    verifyFileInPod(Paths.get(auxiliaryImagePath, customDir, fileInCustomDir.getFileName().toString()).toString(),
        fileContent);
  }

  /**
   * A negative test to verify that model files in an auxiliary images at the cluster scope will be ignored.
   * The test creates an auxiliary image containing JMS model files and use it
   * to config the cluster scope configuration.
   * Verify that the JMS system resource config using adminServiceNodePort can not be found.
   */
  @Test
  @Order(4)
  @DisplayName("Verify that model files in an auxiliary images at the cluster scope are ignored")
  void testPatchDomainToAddModelsToClusterConfigIgnored() {
    final int addAuxImageLoc = 3;
    final String miiAuxiliaryImage = miiAuxiliaryImagePrefix + auxiliaryImageNumberIndex;
    auxiliaryImageNumberIndex += 1;

    // stage dir for auxiliary image containing the cluster config and copy JMS model file
    Path auxiliaryImagePath = Paths.get(RESULTS_ROOT, "auxiliaryimage_modelcluster");
    Path modelsPath = Paths.get(auxiliaryImagePath.toString(), "models");
    logger.info("Create models dir {0} and copy model file {1}",
        modelsPath.toString(), Paths.get(MODEL_DIR, "/model.jms2.yaml").toString());

    assertDoesNotThrow(() -> FileUtils.deleteDirectory(modelsPath.toFile()));
    assertDoesNotThrow(() -> Files.createDirectories(modelsPath));
    assertDoesNotThrow(() -> Files.copy(
        Paths.get(MODEL_DIR, "/model.jms2.yaml"),
        Paths.get(modelsPath.toString(), "/model.jms2.yaml"),
        StandardCopyOption.REPLACE_EXISTING));

    // create image with JMS model file
    logger.info("Create auxiliary image: {0}", miiAuxiliaryImage);
    createAuxiliaryImage(auxiliaryImagePath.toString(),
        Paths.get(RESOURCE_DIR, "auxiliaryimage", "Dockerfile").toString(), miiAuxiliaryImage);

    // push image to repo for multi node cluster
    if (!DOMAIN_IMAGES_REPO.isEmpty()) {
      logger.info("docker push image {0} to registry {1}", miiAuxiliaryImage, DOMAIN_IMAGES_REPO);
      assertTrue(dockerPush(miiAuxiliaryImage), String.format("docker push failed for image %s", miiAuxiliaryImage));
    }

    // patch a domain using the auxiliary image containing to config cluster scope configuration
    logger.info("Patch domain to add a new cluster config to cluster {0}", clusterName);
    patchDomainClusterWithAuxImageAndVerify(domainUid, domainNamespace, managedServerPrefix, replicaCount,
        clusterIndex, auxiliaryImageVolumeName, miiAuxiliaryImage, addAuxImageLoc, "add");

    // verify that the JMS system resource config using adminServiceNodePort can not be found
    logger.info("Verify that the JMSSystemResource configuration doesn't exist");
    int adminServiceNodePort =
        getServiceNodePort(domainNamespace, getExternalServicePodName(adminServerPodName), "default");
    assertNotEquals(-1, adminServiceNodePort, "admin server default node port is not valid");
    assertFalse(checkSystemResourceConfiguration(adminServiceNodePort, "JMSSystemResources",
        "TestClusterJmsModule2", "200"), "JMSSystemResources found");
    logger.info("The JMSSystemResource configuration is not found");
  }

  private void createAuxiliaryImage(String stageDirPath, String dockerFileLocation, String auxiliaryImage) {
    String cmdToExecute = String.format("cd %s && docker build -f %s %s -t %s .",
        stageDirPath, dockerFileLocation,
        "--build-arg AUXILIARY_IMAGE_PATH=" + auxiliaryImagePath, auxiliaryImage);
    assertTrue(new Command()
        .withParams(new CommandParams()
            .command(cmdToExecute))
        .execute(), String.format("Failed to execute", cmdToExecute));

    // push miiAuxiliaryImage to repo for multi node cluster
    if (!DOMAIN_IMAGES_REPO.isEmpty()) {
      logger.info("docker push image {0} to registry {1}", auxiliaryImage, DOMAIN_IMAGES_REPO);
      assertTrue(dockerPush(auxiliaryImage), String.format("docker push failed for image %s", auxiliaryImage));
    }
  }

  private void verifyFileInPod(String fileName, String fileContent) {
    // verify that the file is not copied to admin server that is not a server in the cluster
    ExecResult result = readFilesInPod(domainNamespace, adminServerPodName, fileName);

    logger.info("readFilesInPod returned: {0}", result.toString());
    assertFalse(result.exitValue() == 0, String.format("Failed to read file %s. Error is: %s",
        fileName, result.stderr()));
    assertTrue(result.toString().contains("No such file or directory"),
        String.format("File %s should not exists in the admin pod", fileName));

    // verify that the file is copied to managed server that is a server in the cluster
    for (int i = 1; i <= replicaCount; i++) {
      result = readFilesInPod(domainNamespace, managedServerPrefix + i, fileName);

      logger.info("readFilesInPod returned: {0}", result.toString());
      assertTrue(result.exitValue() == 0, String.format("Failed to read file %s. Error is: %s",
          fileName, result.stderr()));
      assertTrue(result.stdout().contains(fileContent),
          String.format("The content %s read from file %s is not same as given one %s in managed server pod %s",
              result.stdout(), fileName, fileContent, managedServerPrefix + i));
    }
  }
}
