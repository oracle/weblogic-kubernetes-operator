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
import oracle.weblogic.domain.CommonMount;
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
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;

import static java.util.concurrent.TimeUnit.MINUTES;
import static java.util.concurrent.TimeUnit.SECONDS;
import static oracle.weblogic.kubernetes.TestConstants.ADMIN_PASSWORD_DEFAULT;
import static oracle.weblogic.kubernetes.TestConstants.ADMIN_USERNAME_DEFAULT;
import static oracle.weblogic.kubernetes.TestConstants.DOMAIN_IMAGES_REPO;
import static oracle.weblogic.kubernetes.TestConstants.K8S_NODEPORT_HOST;
import static oracle.weblogic.kubernetes.TestConstants.MII_BASIC_APP_NAME;
import static oracle.weblogic.kubernetes.TestConstants.MII_BASIC_IMAGE_TAG;
import static oracle.weblogic.kubernetes.TestConstants.MII_BASIC_WDT_MODEL_FILE;
import static oracle.weblogic.kubernetes.TestConstants.MII_COMMONMOUNT_IMAGE_NAME;
import static oracle.weblogic.kubernetes.TestConstants.OCIR_SECRET_NAME;
import static oracle.weblogic.kubernetes.TestConstants.RESULTS_ROOT;
import static oracle.weblogic.kubernetes.TestConstants.WEBLOGIC_IMAGE_NAME;
import static oracle.weblogic.kubernetes.TestConstants.WEBLOGIC_IMAGE_TAG;
import static oracle.weblogic.kubernetes.TestConstants.WLS_DEFAULT_CHANNEL_NAME;
import static oracle.weblogic.kubernetes.actions.ActionConstants.ARCHIVE_DIR;
import static oracle.weblogic.kubernetes.actions.ActionConstants.MODEL_DIR;
import static oracle.weblogic.kubernetes.actions.ActionConstants.RESOURCE_DIR;
import static oracle.weblogic.kubernetes.actions.TestActions.buildAppArchive;
import static oracle.weblogic.kubernetes.actions.TestActions.defaultAppParams;
import static oracle.weblogic.kubernetes.actions.TestActions.deleteImage;
import static oracle.weblogic.kubernetes.actions.TestActions.dockerPush;
import static oracle.weblogic.kubernetes.actions.TestActions.getDomainCustomResource;
import static oracle.weblogic.kubernetes.actions.TestActions.getPodCreationTimestamp;
import static oracle.weblogic.kubernetes.actions.TestActions.getServiceNodePort;
import static oracle.weblogic.kubernetes.actions.TestActions.patchDomainCustomResource;
import static oracle.weblogic.kubernetes.actions.TestActions.now;
import static oracle.weblogic.kubernetes.assertions.TestAssertions.verifyRollingRestartOccurred;
import static oracle.weblogic.kubernetes.utils.CommonMiiTestUtils.createDomainResource;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.checkPodReadyAndServiceExists;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.checkSystemResourceConfiguration;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.createDomainAndVerify;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.createOcirRepoSecret;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.createSecretWithUsernamePassword;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.getExternalServicePodName;
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

@DisplayName("Test to create model in image domain using common mount")
@IntegrationTest
public class ItMiiCommonMount {

  private static String opNamespace = null;
  private static String domain1Namespace = null;
  private static String domain2Namespace = null;
  private static LoggingFacade logger = null;
  private String domain1Uid = "domain1";
  private String domain2Uid = "domain2";
  private static String miiCMImage1 = MII_COMMONMOUNT_IMAGE_NAME + ":" + MII_BASIC_IMAGE_TAG + "1";
  private static String miiCMImage2 = MII_COMMONMOUNT_IMAGE_NAME + ":" + MII_BASIC_IMAGE_TAG + "2";
  private static String miiCMImage3 = MII_COMMONMOUNT_IMAGE_NAME + ":" + MII_BASIC_IMAGE_TAG + "3";
  private static String miiCMImage4 = MII_COMMONMOUNT_IMAGE_NAME + ":" + MII_BASIC_IMAGE_TAG + "4";
  private static Map<String, OffsetDateTime> podsWithTimeStamps = null;

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
    domain1Namespace = namespaces.get(1);

    logger.info("Creating unique namespace for Domain2");
    assertNotNull(namespaces.get(2), "Namespace list is null");
    domain2Namespace = namespaces.get(2);

    // install and verify operator
    installAndVerifyOperator(opNamespace, domain1Namespace, domain2Namespace);
  }


  /**
   * Create a domain using multiple common mounts. One common mount containing the domain configuration and
   * another common mount with JMS system resource, verify the domain is running and JMS resource is added.
   */
  @Test
  @Order(1)
  @DisplayName("Test to create domain using multiple common mounts")
  public void testCreateDomainUsingMultipleCommonMounts() {

    // admin/managed server name here should match with model yaml
    final String adminServerPodName = domain1Uid + "-admin-server";
    final String managedServerPrefix = domain1Uid + "-managed-server";
    final int replicaCount = 2;
    final String commonMountVolumeName = "commonMountsVolume1";
    final String commonMountPath = "/common";

    // Create the repo secret to pull the image
    // this secret is used only for non-kind cluster
    createOcirRepoSecret(domain1Namespace);

    // create secret for admin credentials
    logger.info("Create secret for admin credentials");
    String adminSecretName = "weblogic-credentials";
    createSecretWithUsernamePassword(adminSecretName, domain1Namespace,
        ADMIN_USERNAME_DEFAULT, ADMIN_PASSWORD_DEFAULT);

    // create encryption secret
    logger.info("Create encryption secret");
    String encryptionSecretName = "encryptionsecret";
    createSecretWithUsernamePassword(encryptionSecretName, domain1Namespace,
        "weblogicenc", "weblogicenc");

    // create stage dir for first common mount with image1
    Path multipleCMPath1 = Paths.get(RESULTS_ROOT, "multiplecmimage1");
    assertDoesNotThrow(() -> FileUtils.deleteDirectory(multipleCMPath1.toFile()));
    assertDoesNotThrow(() -> Files.createDirectories(multipleCMPath1));

    // create models dir and copy model, archive files if any for image1
    Path modelsPath1 = Paths.get(multipleCMPath1.toString(), "models");
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
        Paths.get(ARCHIVE_DIR,  MII_BASIC_APP_NAME + ".zip"),
        Paths.get(modelsPath1.toString(), MII_BASIC_APP_NAME + ".zip"),
        StandardCopyOption.REPLACE_EXISTING));

    // unzip WDT installation file into work dir
    unzipWDTInstallationFile(multipleCMPath1.toString());

    // create image1 with model and wdt installation files
    createCommonMountImage(multipleCMPath1.toString(),
        Paths.get(RESOURCE_DIR, "commonmount", "Dockerfile").toString(), miiCMImage1);

    // push image1 to repo for multi node cluster
    if (!DOMAIN_IMAGES_REPO.isEmpty()) {
      logger.info("docker push image {0} to registry {1}", miiCMImage1, DOMAIN_IMAGES_REPO);
      assertTrue(dockerPush(miiCMImage1), String.format("docker push failed for image %s", miiCMImage1));
    }

    // create stage dir for second common mount with image2
    Path multipleCMPath2 = Paths.get(RESULTS_ROOT, "multiplecmimage2");
    assertDoesNotThrow(() -> FileUtils.deleteDirectory(multipleCMPath2.toFile()));
    assertDoesNotThrow(() -> Files.createDirectories(multipleCMPath2));

    // create models dir and copy model, archive files if any
    Path modelsPath2 = Paths.get(multipleCMPath2.toString(), "models");
    assertDoesNotThrow(() -> Files.createDirectories(modelsPath2));
    assertDoesNotThrow(() -> Files.copy(
        Paths.get(MODEL_DIR, "/model.jms2.yaml"),
        Paths.get(modelsPath2.toString(), "/model.jms2.yaml"),
        StandardCopyOption.REPLACE_EXISTING));

    // create image2 with model and wdt installation files
    createCommonMountImage(multipleCMPath2.toString(),
        Paths.get(RESOURCE_DIR, "commonmount", "Dockerfile").toString(), miiCMImage2);

    // push image2 to repo for multi node cluster
    if (!DOMAIN_IMAGES_REPO.isEmpty()) {
      logger.info("docker push image {0} to registry {1}", miiCMImage2, DOMAIN_IMAGES_REPO);
      assertTrue(dockerPush(miiCMImage2), String.format("docker push failed for image %s", miiCMImage2));
    }

    // create domain custom resource using 2 common mounts and images
    logger.info("Creating domain custom resource with domainUid {0} and common mount images {1} {2}",
        domain1Uid, miiCMImage1, miiCMImage2);
    Domain domainCR = createDomainResource(domain1Uid, domain1Namespace,
        WEBLOGIC_IMAGE_NAME + ":" + WEBLOGIC_IMAGE_TAG, adminSecretName, OCIR_SECRET_NAME,
                    encryptionSecretName, replicaCount, "cluster-1", commonMountPath,
                    commonMountVolumeName, miiCMImage1, miiCMImage2);

    // create domain and verify its running
    logger.info("Creating domain {0} with common mount images {1} {2} in namespace {3}",
        domain1Uid, miiCMImage1, miiCMImage2, domain1Namespace);
    createDomainAndVerify(domain1Uid, domainCR, domain1Namespace, adminServerPodName, managedServerPrefix, replicaCount);

    // check configuration for JMS
    int adminServiceNodePort
        = getServiceNodePort(domain1Namespace, getExternalServicePodName(adminServerPodName), "default");
    assertNotEquals(-1, adminServiceNodePort, "admin server default node port is not valid");
    assertTrue(checkSystemResourceConfiguration(adminServiceNodePort, "JMSSystemResources",
        "TestClusterJmsModule2", "200"), "JMSSystemResources not found");
    logger.info("Found the JMSSystemResource configuration");
  }

  /**
   * Create a domain with datasource using common mount containing the DataSource, verify the domain is running and JDBC DataSource resource is added.
   * Patch domain with updated JDBC URL info and verify the update.
   */
  @Test
  @Order(2)
  @DisplayName("Test to update data source url in the  domain using common mount")
  public void testUpdateDataSourceInDomainUsingCommonMount() {

    // admin/managed server name here should match with model yaml
    final String adminServerPodName = domain2Uid + "-admin-server";
    final String managedServerPrefix = domain2Uid + "-managed-server";
    final int replicaCount = 2;
    final String commonMountVolumeName = "commonMountsVolume1";
    final String commonMountPath = "/common";

    // Create the repo secret to pull the image
    // this secret is used only for non-kind cluster
    createOcirRepoSecret(domain2Namespace);

    // create secret for admin credentials
    logger.info("Create secret for admin credentials");
    String adminSecretName = "weblogic-credentials";
    createSecretWithUsernamePassword(adminSecretName, domain2Namespace,
        ADMIN_USERNAME_DEFAULT, ADMIN_PASSWORD_DEFAULT);

    // create encryption secret
    logger.info("Create encryption secret");
    String encryptionSecretName = "encryptionsecret";
    createSecretWithUsernamePassword(encryptionSecretName, domain2Namespace,
        "weblogicenc", "weblogicenc");

    // create stage dir for first common mount with image1
    Path dsCMPath1 = Paths.get(RESULTS_ROOT, "dscmimage1");
    assertDoesNotThrow(() -> FileUtils.deleteDirectory(dsCMPath1.toFile()));
    assertDoesNotThrow(() -> Files.createDirectories(dsCMPath1));

    // create models dir and copy model, archive files if any for image3
    Path modelsPath1 = Paths.get(dsCMPath1.toString(), "models");
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
    unzipWDTInstallationFile(dsCMPath1.toString());

    // create image3 with model and wdt installation files
    createCommonMountImage(dsCMPath1.toString(),
        Paths.get(RESOURCE_DIR, "commonmount", "Dockerfile").toString(), miiCMImage3);

    // push image3 to repo for multi node cluster
    if (!DOMAIN_IMAGES_REPO.isEmpty()) {
      logger.info("docker push image {0} to registry {1}", miiCMImage3, DOMAIN_IMAGES_REPO);
      assertTrue(dockerPush(miiCMImage3), String.format("docker push failed for image %s", miiCMImage3));
    }

    // create stage dir for common mount with image4
    // replace DataSource URL info in the  model file
    assertDoesNotThrow(() -> replaceStringInFile(Paths.get(modelsPath1.toString(),"/multi-model-one-ds.20.yaml").toString(), "xxx.xxx.x.xxx:1521",
        "localhost:7001"),"Can't replace datasource url in the model file");
    assertDoesNotThrow(() -> replaceStringInFile(Paths.get(modelsPath1.toString(),"/multi-model-one-ds.20.yaml").toString(), "ORCLCDB",
        "dbsvc"),"Can't replace datasource url in the model file");

    // create image2 with model and wdt installation files
    createCommonMountImage(dsCMPath1.toString(),
        Paths.get(RESOURCE_DIR, "commonmount", "Dockerfile").toString(), miiCMImage4);

    // push image4 to repo for multi node cluster
    if (!DOMAIN_IMAGES_REPO.isEmpty()) {
      logger.info("docker push image {0} to registry {1}", miiCMImage4, DOMAIN_IMAGES_REPO);
      assertTrue(dockerPush(miiCMImage4), String.format("docker push failed for image %s", miiCMImage4));
    }

    // create domain custom resource using common mount with original datasource info
    logger.info("Creating domain custom resource with domain2Uid {0} and common mount image {1} ",
        domain2Uid, miiCMImage3);
    Domain domainCR = createDomainResource(domain2Uid, domain2Namespace,
        WEBLOGIC_IMAGE_NAME + ":" + WEBLOGIC_IMAGE_TAG, adminSecretName, OCIR_SECRET_NAME,
        encryptionSecretName, replicaCount, "cluster-1", commonMountPath,
        commonMountVolumeName, miiCMImage3);

    // create domain and verify its running
    logger.info("Creating domain {0} with common mount image {1} in namespace {2}",
        domain2Uid, miiCMImage3, domain2Namespace);
    createDomainAndVerify(domain2Uid, domainCR, domain2Namespace, adminServerPodName, managedServerPrefix, replicaCount);
    podsWithTimeStamps = getPodsWithTimeStamps(adminServerPodName, managedServerPrefix, domain2Namespace, 2);
    // check configuration for DataSource
    int adminServiceNodePort
        = getServiceNodePort(domain2Namespace, getExternalServicePodName(adminServerPodName), "default");
    assertNotEquals(-1, adminServiceNodePort, "admin server default node port is not valid");
    assertTrue(checkDS(adminServerPodName,domain2Namespace,"TestDataSource","jdbc:oracle:thin:@\\/\\/xxx.xxx.x.xxx:1521\\/ORCLCDB"),
        "Can't find expected URL configuration for DataSource ");
    logger.info("Found the DataResource configuration");

    patchDomainWithCMImageAndVerify(miiCMImage3, miiCMImage4, domain2Uid, domain2Namespace);

    assertTrue(checkDS(adminServerPodName,domain2Namespace,"TestDataSource","jdbc:oracle:thin:@\\/\\/localhost:7001\\/dbsvc"),
        "Can't find expected URL configuration for DataSource ");
    logger.info("Found the DataResource configuration");
  }

  private static void patchDomainWithCMImageAndVerify(String oldImageName, String newImageName, String domainUid, String domainNamespace) {
    Domain domain1 = assertDoesNotThrow(() -> getDomainCustomResource(domainUid, domainNamespace),
        String.format("getDomainCustomResource failed with ApiException when tried to get domain %s in namespace %s",
            domainUid, domainNamespace));
    assertNotNull(domain1, "Got null domain resource ");
    assertNotNull(domain1.getSpec().getServerPod().getCommonMounts(), domain1 + "/spec/serverPod/commonMounts is null");
    List<CommonMount> commonMountList = domain1.getSpec().getServerPod().getCommonMounts();
    assertFalse(commonMountList.isEmpty(), "CommonMount list is empty");
    String searchString;
    int index = 0;

    CommonMount cmMount = commonMountList.stream()
          .filter(commonMount -> oldImageName.equals(commonMount.getImage()))
          .findAny()
          .orElse(null);
    assertNotNull(cmMount, "Can't find common Mount with Image name " + oldImageName
    + "can't patch domain " + domainUid);

    index = commonMountList.indexOf(cmMount);
    searchString = "\"/spec/serverPod/commonMounts/" + index + "/image\"";
    StringBuffer patchStr = new StringBuffer("[{");
    patchStr.append("\"op\": \"replace\",")
        .append(" \"path\": " + searchString + ",")
        .append(" \"value\":  \"" + newImageName + "\"")
        .append(" }]");
    logger.info("Common Mount patch string: " +  patchStr);

    V1Patch patch = new V1Patch((patchStr).toString());

    boolean cmPatched = assertDoesNotThrow(() ->
            patchDomainCustomResource(domainUid, domainNamespace, patch, "application/json-patch+json"),
        "patchDomainCustomResource(Common Mount)  failed ");
    assertTrue(cmPatched, "patchDomainCustomResource(common Mount) failed");

    domain1 = assertDoesNotThrow(() -> getDomainCustomResource(domainUid, domainNamespace),
        String.format("getDomainCustomResource failed with ApiException when tried to get domain %s in namespace %s",
            domainUid, domainNamespace));
    assertNotNull(domain1, "Got null domain resource after patching");
    assertNotNull(domain1.getSpec(), domain1 + " /spec/serverPod is null");
    assertNotNull(domain1.getSpec().getServerPod(), domain1 + " /spec/serverPod is null");
    assertNotNull(domain1.getSpec().getServerPod().getCommonMounts(), domain1 + "/spec/serverPod/commonMounts is null");

    //verify the new CommonMount image in the new patched domain
    commonMountList = domain1.getSpec().getServerPod().getCommonMounts();
    ;
    String cmImage = commonMountList.get(index).getImage();
    logger.info("In the new patched domain imageValue is: {0}", cmImage);
    assertTrue(cmImage.equalsIgnoreCase(newImageName), "common mount image was not updated"
        + " in the new patched domain");

    //get current timestamp before domain rolling restart to verify domain roll events
    OffsetDateTime timestamp = now();

    // verify the server pods are rolling restarted and back to ready state
    logger.info("Verifying rolling restart occurred for domain {0} in namespace {1}",
        domainUid, domainNamespace);

    assertTrue(verifyRollingRestartOccurred(podsWithTimeStamps, 1, domainNamespace),
        String.format("Rolling restart failed for domain %s in namespace %s", domainUid, domainNamespace));
  }

  private Map getPodsWithTimeStamps(String adminServerPodName, String managedServerPrefix,
                                    String domainNamespace,
                                    int replicaCount) {

    // create the map with server pods and their original creation timestamps
    podsWithTimeStamps = new LinkedHashMap<>();

    podsWithTimeStamps.put(adminServerPodName,
        assertDoesNotThrow(() -> getPodCreationTimestamp(domainNamespace, "", adminServerPodName),
            String.format("getPodCreationTimestamp failed with ApiException for pod %s in namespace %s",
                adminServerPodName, domainNamespace)));

    for (int i = 1; i <= replicaCount; i++) {
      String managedServerPodName = managedServerPrefix + i;
      podsWithTimeStamps.put(managedServerPodName,
          assertDoesNotThrow(() -> getPodCreationTimestamp(domainNamespace, "", managedServerPodName),
              String.format("getPodCreationTimestamp failed with ApiException for pod %s in namespace %s",
                  managedServerPodName, domainNamespace)));
    }
    return podsWithTimeStamps;
  }

  /**
   * Cleanup images.
   */
  public void tearDownAll() {
    // delete images
    if (miiCMImage1 != null) {
      deleteImage(miiCMImage1);
    }

    if (miiCMImage2 != null) {
      deleteImage(miiCMImage2);
    }

    if (miiCMImage3 != null) {
      deleteImage(miiCMImage3);
    }

    if (miiCMImage4 != null) {
      deleteImage(miiCMImage4);
    }
  }

  private void createCommonMountImage(String stageDirPath, String dockerFileLocation, String cmImage) {
    String cmdToExecute = String.format("cd %s && docker build -f %s %s -t %s .",
        stageDirPath, dockerFileLocation,
        "--build-arg COMMON_MOUNT_PATH=/common", cmImage);
    assertTrue(new Command()
        .withParams(new CommandParams()
            .command(cmdToExecute))
        .execute(), String.format("Failed to execute", cmdToExecute));
  }

  /**
   * Check a DataSource info.
   */
  private static boolean checkDS(
      String adminServerPodName,
      String namespace,
      String dsName,
      String dsInfo) {
    int adminServiceNodePort = getServiceNodePort(
        namespace, getExternalServicePodName(adminServerPodName), WLS_DEFAULT_CHANNEL_NAME);

    String command = new StringBuffer()
        .append("curl --user " + ADMIN_USERNAME_DEFAULT + ":" + ADMIN_PASSWORD_DEFAULT)
        .append(" http://" + K8S_NODEPORT_HOST + ":" + adminServiceNodePort)
        .append("/management/wls/latest/datasources")
        .append("/id/" + dsName)
        .append(" --noproxy '*'")
        .append(" --silent --show-error ").toString();
        //.append("| grep url | tr -d -c 0-9 ").toString();

    CommandParams params = Command
        .defaultCommandParams()
        .command(command)
        .saveResults(true)
        .redirect(true);
    Command.withParams(params).execute();
    return params.stdout().contains(dsInfo);


    //return Command.withParams(params).executeAndVerify(expectedStr);
  }

}
