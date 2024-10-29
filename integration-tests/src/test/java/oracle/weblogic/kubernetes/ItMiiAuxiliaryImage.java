// Copyright (c) 2022, 2024, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.weblogic.kubernetes;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import io.kubernetes.client.openapi.ApiException;
import oracle.weblogic.domain.AuxiliaryImage;
import oracle.weblogic.domain.DomainResource;
import oracle.weblogic.kubernetes.actions.impl.AppParams;
import oracle.weblogic.kubernetes.actions.impl.primitive.WitParams;
import oracle.weblogic.kubernetes.annotations.IntegrationTest;
import oracle.weblogic.kubernetes.annotations.Namespaces;
import oracle.weblogic.kubernetes.logging.LoggingFacade;
import oracle.weblogic.kubernetes.utils.CommonMiiTestUtils;
import org.apache.commons.io.FileUtils;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import static oracle.weblogic.kubernetes.TestConstants.ADMIN_PASSWORD_DEFAULT;
import static oracle.weblogic.kubernetes.TestConstants.ADMIN_USERNAME_DEFAULT;
import static oracle.weblogic.kubernetes.TestConstants.DOMAIN_IMAGES_PREFIX;
import static oracle.weblogic.kubernetes.TestConstants.DOMAIN_STATUS_CONDITION_FAILED_TYPE;
import static oracle.weblogic.kubernetes.TestConstants.DOMAIN_VERSION;
import static oracle.weblogic.kubernetes.TestConstants.IMAGE_PULL_POLICY;
import static oracle.weblogic.kubernetes.TestConstants.KIND_REPO;
import static oracle.weblogic.kubernetes.TestConstants.MII_APP_RESPONSE_V1;
import static oracle.weblogic.kubernetes.TestConstants.MII_APP_RESPONSE_V2;
import static oracle.weblogic.kubernetes.TestConstants.MII_AUXILIARY_IMAGE_NAME;
import static oracle.weblogic.kubernetes.TestConstants.MII_BASIC_APP_NAME;
import static oracle.weblogic.kubernetes.TestConstants.MII_BASIC_IMAGE_TAG;
import static oracle.weblogic.kubernetes.TestConstants.MII_BASIC_WDT_MODEL_FILE;
import static oracle.weblogic.kubernetes.TestConstants.OPERATOR_RELEASE_NAME;
import static oracle.weblogic.kubernetes.TestConstants.RESULTS_ROOT;
import static oracle.weblogic.kubernetes.TestConstants.WDT_TEST_VERSION;
import static oracle.weblogic.kubernetes.TestConstants.WEBLOGIC_IMAGE_NAME_DEFAULT;
import static oracle.weblogic.kubernetes.TestConstants.WEBLOGIC_IMAGE_TO_USE_IN_SPEC;
import static oracle.weblogic.kubernetes.actions.ActionConstants.ARCHIVE_DIR;
import static oracle.weblogic.kubernetes.actions.ActionConstants.MODEL_DIR;
import static oracle.weblogic.kubernetes.actions.ActionConstants.RESOURCE_DIR;
import static oracle.weblogic.kubernetes.actions.TestActions.buildAppArchive;
import static oracle.weblogic.kubernetes.actions.TestActions.createDomainCustomResource;
import static oracle.weblogic.kubernetes.actions.TestActions.defaultAppParams;
import static oracle.weblogic.kubernetes.actions.TestActions.deleteConfigMap;
import static oracle.weblogic.kubernetes.actions.TestActions.deleteImage;
import static oracle.weblogic.kubernetes.actions.TestActions.getDomainCustomResource;
import static oracle.weblogic.kubernetes.actions.TestActions.getOperatorPodName;
import static oracle.weblogic.kubernetes.actions.TestActions.imageTag;
import static oracle.weblogic.kubernetes.actions.TestActions.now;
import static oracle.weblogic.kubernetes.actions.impl.primitive.Kubernetes.listConfigMaps;
import static oracle.weblogic.kubernetes.assertions.TestAssertions.appAccessibleInPod;
import static oracle.weblogic.kubernetes.assertions.TestAssertions.doesDomainExist;
import static oracle.weblogic.kubernetes.assertions.TestAssertions.verifyRollingRestartOccurred;
import static oracle.weblogic.kubernetes.utils.AuxiliaryImageUtils.checkWDTVersion;
import static oracle.weblogic.kubernetes.utils.AuxiliaryImageUtils.createAndPushAuxiliaryImage;
import static oracle.weblogic.kubernetes.utils.AuxiliaryImageUtils.createPushAuxiliaryImageWithDomainConfig;
import static oracle.weblogic.kubernetes.utils.AuxiliaryImageUtils.createPushAuxiliaryImageWithWDTInstallOnly;
import static oracle.weblogic.kubernetes.utils.ClusterUtils.createClusterResourceAndAddReferenceToDomain;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.checkPodReadyAndServiceExists;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.checkServiceDoesNotExist;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.checkSystemResourceConfigViaAdminPod;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.checkSystemResourceConfiguration;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.getDateAndTimeStamp;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.testUntil;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.verifyConfiguredSystemResouceByPath;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.verifyConfiguredSystemResource;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.withLongRetryPolicy;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.withStandardRetryPolicy;
import static oracle.weblogic.kubernetes.utils.ConfigMapUtils.createConfigMapForDomainCreation;
import static oracle.weblogic.kubernetes.utils.DomainUtils.checkDomainStatusConditionTypeExists;
import static oracle.weblogic.kubernetes.utils.DomainUtils.checkDomainStatusConditionTypeHasExpectedStatus;
import static oracle.weblogic.kubernetes.utils.DomainUtils.createDomainAndVerify;
import static oracle.weblogic.kubernetes.utils.DomainUtils.deleteDomainResource;
import static oracle.weblogic.kubernetes.utils.DomainUtils.patchDomainWithAuxiliaryImageAndVerify;
import static oracle.weblogic.kubernetes.utils.DomainUtils.verifyDomainStatusConditionTypeDoesNotExist;
import static oracle.weblogic.kubernetes.utils.FileUtils.replaceStringInFile;
import static oracle.weblogic.kubernetes.utils.ImageUtils.createTestRepoSecret;
import static oracle.weblogic.kubernetes.utils.ImageUtils.imageRepoLoginAndPushImageToRegistry;
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
import static oracle.weblogic.kubernetes.utils.SecretUtils.createSecretsForImageRepos;
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
@Tag("olcne-mrg")
@Tag("kind-parallel")
@Tag("toolkits-srg")
@Tag("okd-wls-srg")
@Tag("oke-arm")
@Tag("oke-parallel")
class ItMiiAuxiliaryImage {

  private static String domainNamespace = null;
  private static String wdtDomainNamespace = null;
  private static String errorpathDomainNamespace = null;
  private static LoggingFacade logger = null;
  private static final String domainUid1 = "domain1";
  private final String domainUid = "";
  private static final String miiAuxiliaryImage1Tag = "image1" + MII_BASIC_IMAGE_TAG;
  private static final String miiAuxiliaryImage1 = MII_AUXILIARY_IMAGE_NAME + ":" + miiAuxiliaryImage1Tag;
  private static final String miiAuxiliaryImage2Tag = "image2" + MII_BASIC_IMAGE_TAG;
  private static final String miiAuxiliaryImage2 = MII_AUXILIARY_IMAGE_NAME + ":" + miiAuxiliaryImage2Tag;
  private static final String miiAuxiliaryImage3Tag = "image3" + MII_BASIC_IMAGE_TAG;
  private static final String miiAuxiliaryImage3 = MII_AUXILIARY_IMAGE_NAME + ":" + miiAuxiliaryImage3Tag;
  private static final String miiAuxiliaryImage4Tag = "image4" + MII_BASIC_IMAGE_TAG;
  private static final String miiAuxiliaryImage4 = MII_AUXILIARY_IMAGE_NAME + ":" + miiAuxiliaryImage4Tag;
  private static final String miiAuxiliaryImage5Tag = "image5" + MII_BASIC_IMAGE_TAG;
  private static final String miiAuxiliaryImage5 = MII_AUXILIARY_IMAGE_NAME + ":" + miiAuxiliaryImage5Tag;
  private static final String miiAuxiliaryImage6Tag = "image6" + MII_BASIC_IMAGE_TAG;
  private static final String miiAuxiliaryImage6 = MII_AUXILIARY_IMAGE_NAME + ":" + miiAuxiliaryImage6Tag;
  private static final String miiAuxiliaryImage7Tag = "image7" + MII_BASIC_IMAGE_TAG;
  private static final String miiAuxiliaryImage7 = MII_AUXILIARY_IMAGE_NAME + ":" + miiAuxiliaryImage7Tag;
  private static final String miiAuxiliaryImage8Tag = "image8" + MII_BASIC_IMAGE_TAG;
  private static final String miiAuxiliaryImage8 = MII_AUXILIARY_IMAGE_NAME + ":" + miiAuxiliaryImage8Tag;
  private static final String miiAuxiliaryImage9Tag = "image9" + MII_BASIC_IMAGE_TAG;
  private static final String miiAuxiliaryImage9 = MII_AUXILIARY_IMAGE_NAME + ":" + miiAuxiliaryImage9Tag;
  private static final String miiAuxiliaryImage10Tag = "image10" + MII_BASIC_IMAGE_TAG;
  private static final String miiAuxiliaryImage10 = MII_AUXILIARY_IMAGE_NAME + ":" + miiAuxiliaryImage10Tag;
  private static final String miiAuxiliaryImage11Tag = "image11" + MII_BASIC_IMAGE_TAG;
  private static final String miiAuxiliaryImage11 = MII_AUXILIARY_IMAGE_NAME + ":" + miiAuxiliaryImage11Tag;
  private static final String miiAuxiliaryImage12Tag = "image12" + MII_BASIC_IMAGE_TAG;
  private static final String miiAuxiliaryImage12 = MII_AUXILIARY_IMAGE_NAME + ":" + miiAuxiliaryImage12Tag;
  private static final String miiAuxiliaryImage13Tag = "image13" + MII_BASIC_IMAGE_TAG;
  private static final String miiAuxiliaryImage13 = MII_AUXILIARY_IMAGE_NAME + ":" + miiAuxiliaryImage13Tag;
  private static final String miiAuxiliaryImage14Tag = "image14" + MII_BASIC_IMAGE_TAG;
  private static final String miiAuxiliaryImage14 = MII_AUXILIARY_IMAGE_NAME + ":" + miiAuxiliaryImage14Tag;
  private static final String miiAuxiliaryImage15Tag = "image15" + MII_BASIC_IMAGE_TAG;
  private static final String miiAuxiliaryImage15 = MII_AUXILIARY_IMAGE_NAME + ":" + miiAuxiliaryImage15Tag;

  private static final String miiAuxiliaryImage16Tag = "image16" + MII_BASIC_IMAGE_TAG;
  private static final String miiAuxiliaryImage16 = MII_AUXILIARY_IMAGE_NAME + ":" + miiAuxiliaryImage16Tag;

  private static final String miiAuxiliaryImage17Tag = "image17" + MII_BASIC_IMAGE_TAG;
  private static final String miiAuxiliaryImage17 = MII_AUXILIARY_IMAGE_NAME + ":" + miiAuxiliaryImage17Tag;

  private static final String errorPathAuxiliaryImage1Tag = "errorimage1" + MII_BASIC_IMAGE_TAG;
  private static final String errorPathAuxiliaryImage1 = MII_AUXILIARY_IMAGE_NAME + ":" + errorPathAuxiliaryImage1Tag;
  private static final String errorPathAuxiliaryImage2Tag = "errorimage2" + MII_BASIC_IMAGE_TAG;
  private static final String errorPathAuxiliaryImage2 = MII_AUXILIARY_IMAGE_NAME + ":" + errorPathAuxiliaryImage2Tag;
  private static final String errorPathAuxiliaryImage3Tag = "errorimage3" + MII_BASIC_IMAGE_TAG;
  private static final String errorPathAuxiliaryImage3 = MII_AUXILIARY_IMAGE_NAME + ":" + errorPathAuxiliaryImage3Tag;
  private static final String errorPathAuxiliaryImage4Tag = "errorimage4" + MII_BASIC_IMAGE_TAG;
  private static final String errorPathAuxiliaryImage4 = MII_AUXILIARY_IMAGE_NAME + ":" + errorPathAuxiliaryImage4Tag;
  private static final String adminServerPodNameDomain1 = domainUid1 + "-admin-server";
  private static final String managedServerPrefixDomain1 = domainUid1 + "-managed-server";
  private static final int replicaCount = 2;
  private String adminSvcExtHost = null;
  private static String adminSvcExtHostDomain1 = null;
  private static String adminSecretName = "weblogic-credentials";
  private static String encryptionSecretName = "encryptionsecret";
  private static String opNamespace = null;
  private static String operatorPodName = null;
  private static String oldMiiAuxImageNameInDomain1 = miiAuxiliaryImage1;
  private static AppParams appParams = defaultAppParams()
      .appArchiveDir(ARCHIVE_DIR + ItMiiAuxiliaryImage.class.getSimpleName());

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
    // build app
    assertTrue(buildAppArchive(appParams
            .srcDirList(Collections.singletonList(MII_BASIC_APP_NAME))
            .appName(MII_BASIC_APP_NAME)),
        String.format("Failed to create app archive for %s", MII_BASIC_APP_NAME));

    // image1 with model files for domain config, ds, app and wdt install files
    List<String> archiveList = Collections.singletonList(appParams.appArchiveDir() + "/" + MII_BASIC_APP_NAME + ".zip");

    List<String> modelList = new ArrayList<>();
    modelList.add(MODEL_DIR + "/" + MII_BASIC_WDT_MODEL_FILE);
    modelList.add(MODEL_DIR + "/multi-model-one-ds.20.yaml");
    createPushAuxiliaryImageWithDomainConfig(MII_AUXILIARY_IMAGE_NAME, miiAuxiliaryImage1Tag, archiveList, modelList);
    // image2 with model files for jms config
    modelList = new ArrayList<>();
    modelList.add(MODEL_DIR + "/model.jms2.yaml");
    WitParams witParams =
        new WitParams()
            .modelImageName(MII_AUXILIARY_IMAGE_NAME)
            .modelImageTag(miiAuxiliaryImage2Tag)
            .wdtModelOnly(true)
            .modelFiles(modelList)
            .wdtVersion("NONE");
    createAndPushAuxiliaryImage(MII_AUXILIARY_IMAGE_NAME, miiAuxiliaryImage2Tag, witParams);

    // admin/managed server name here should match with model yaml
    final String auxiliaryImagePath = "/auxiliary";
    String clusterName = "cluster-1";

    // create domain custom resource using 2 auxiliary images
    logger.info("Creating domain custom resource with domainUid {0} and auxiliary images {1} {2}",
        domainUid1, miiAuxiliaryImage1, miiAuxiliaryImage2);
    DomainResource domainCR = CommonMiiTestUtils.createDomainResourceWithAuxiliaryImage(domainUid1, domainNamespace,
        WEBLOGIC_IMAGE_TO_USE_IN_SPEC, adminSecretName, createSecretsForImageRepos(domainNamespace),
        encryptionSecretName, auxiliaryImagePath,
        miiAuxiliaryImage1,
        miiAuxiliaryImage2);

    domainCR = createClusterResourceAndAddReferenceToDomain(
        domainUid1 + "-" + clusterName, clusterName, domainNamespace, domainCR, replicaCount);

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
    checkConfiguredJMSresouce(domainNamespace, adminServerPodNameDomain1, adminSvcExtHostDomain1);
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

    // create stage dir for auxiliary image
    Path aiPath = Paths.get(RESULTS_ROOT,
        ItMiiAuxiliaryImage.class.getSimpleName(), "ai"
            + miiAuxiliaryImage1.substring(miiAuxiliaryImage1.length() - 1));
    assertDoesNotThrow(() -> FileUtils.deleteDirectory(aiPath.toFile()),
        "Delete directory failed");
    assertDoesNotThrow(() -> Files.createDirectories(aiPath),
        "Create directory failed");

    Path modelsPath = Paths.get(aiPath.toString(), "models");
    // create models dir and copy model for image

    assertDoesNotThrow(() -> Files.createDirectories(modelsPath),
        "Create directory failed");
    assertDoesNotThrow(() -> Files.copy(
        Paths.get(MODEL_DIR, "multi-model-one-ds.20.yaml"),
        Paths.get(modelsPath.toString(), "multi-model-one-ds.20.yaml"),
        StandardCopyOption.REPLACE_EXISTING), "Copy files failed");


    // create stage dir for auxiliary image with image3
    // replace DataSource URL info in the  model file
    assertDoesNotThrow(() -> replaceStringInFile(Paths.get(modelsPath.toString(),
        "/multi-model-one-ds.20.yaml").toString(), "xxx.xxx.x.xxx:1521",
        "localhost:7001"), "Can't replace datasource url in the model file");
    assertDoesNotThrow(() -> replaceStringInFile(Paths.get(modelsPath.toString(),
        "/multi-model-one-ds.20.yaml").toString(), "ORCLCDB",
        "dbsvc"), "Can't replace datasource url in the model file");

    List<String> archiveList = Collections.singletonList(appParams.appArchiveDir() + "/" + MII_BASIC_APP_NAME + ".zip");

    List<String> modelList = new ArrayList<>();
    modelList.add(MODEL_DIR + "/" + MII_BASIC_WDT_MODEL_FILE);
    modelList.add(modelsPath + "/multi-model-one-ds.20.yaml");

    // create image3 with model and wdt installation files
    WitParams witParams =
        new WitParams()
            .modelImageName(MII_AUXILIARY_IMAGE_NAME)
            .modelImageTag(miiAuxiliaryImage3Tag)
            .modelFiles(modelList)
            .modelArchiveFiles(archiveList);
    createAndPushAuxiliaryImage(MII_AUXILIARY_IMAGE_NAME, miiAuxiliaryImage3Tag, witParams);

    //create router for admin service on OKD
    if (adminSvcExtHostDomain1 == null) {
      adminSvcExtHostDomain1 = createRouteForOKD(getExternalServicePodName(adminServerPodNameDomain1), domainNamespace);
      logger.info("admin svc host = {0}", adminSvcExtHostDomain1);
    }

    // check configuration for DataSource in the running domain
    assertTrue(checkSystemResourceConfigViaAdminPod(adminServerPodNameDomain1, domainNamespace,
        "JDBCSystemResources/TestDataSource/JDBCResource/JDBCDriverParams",
        "jdbc:oracle:thin:@\\/\\/xxx.xxx.x.xxx:1521\\/ORCLCDB"),
        "Can't find expected URL configuration for DataSource");

    logger.info("Found the DataResource configuration");

    // get the map with server pods and their original creation timestamps
    Map<String, OffsetDateTime> podsWithTimeStamps = getPodsWithTimeStamps(domainNamespace, adminServerPodNameDomain1,
        managedServerPrefixDomain1, replicaCount);

    patchDomainWithAuxiliaryImageAndVerify(oldMiiAuxImageNameInDomain1,
        miiAuxiliaryImage3,
        domainUid1, domainNamespace, replicaCount);
    // set the old image name for other tests which patch the domain
    oldMiiAuxImageNameInDomain1 =  miiAuxiliaryImage3;
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
    DomainResource domain1 = assertDoesNotThrow(() -> getDomainCustomResource(domainUid1, domainNamespace),
        String.format("getDomainCustomResource failed with ApiException when tried to get domain %s in namespace %s",
            domainUid1, domainNamespace));
    assertNotNull(domain1, "Got null domain resource");
    assertNotNull(domain1.getSpec(), domain1 + "/spec is null");

    // get the map with server pods and their original creation timestamps
    Map<String, OffsetDateTime> podsWithTimeStamps = getPodsWithTimeStamps(domainNamespace, adminServerPodNameDomain1,
        managedServerPrefixDomain1, replicaCount);

    //print out the original image name
    String imageName = domain1.getSpec().getImage();
    logger.info("Currently the image name used for the domain is: {0}", imageName);

    //change image name to imageUpdate
    String imageTag = getDateAndTimeStamp();
    String imageUpdate = KIND_REPO != null ? KIND_REPO
        + (WEBLOGIC_IMAGE_NAME_DEFAULT + ":" + imageTag).substring(TestConstants.BASE_IMAGES_REPO.length() + 1)
        : DOMAIN_IMAGES_PREFIX + WEBLOGIC_IMAGE_NAME_DEFAULT + "-dev:" + imageTag;
    imageTag(imageName, imageUpdate);
    imageRepoLoginAndPushImageToRegistry(imageUpdate);

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
    checkConfiguredJMSresouce(domainNamespace, adminServerPodNameDomain1, adminSvcExtHostDomain1);
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

    createPushAuxiliaryImageWithWDTInstallOnly(MII_AUXILIARY_IMAGE_NAME,miiAuxiliaryImage4Tag, WDT_TEST_VERSION);

    // create domain custom resource using 2 auxiliary images, one with default sourceWDTInstallHome
    // and other with sourceWDTInstallHome set to none
    logger.info("Creating domain custom resource with domainUid {0} and auxiliary images {1} {2}",
        domainUid, miiAuxiliaryImage1, miiAuxiliaryImage4);
    DomainResource domainCR1 = createDomainResourceWithAuxiliaryImage(domainUid, domainNamespace,
        WEBLOGIC_IMAGE_TO_USE_IN_SPEC, adminSecretName, createSecretsForImageRepos(domainNamespace),
        encryptionSecretName, replicaCount, auxiliaryImagePath,
        miiAuxiliaryImage1, miiAuxiliaryImage4);

    // create domain and verify its running
    logger.info("Creating domain {0} with auxiliary images {1} {2} in namespace {3}",
        domainUid, miiAuxiliaryImage1, miiAuxiliaryImage4, domainNamespace);
    createDomainAndVerify(domainUid, domainCR1, domainNamespace,
        adminServerPodName, managedServerPrefix, replicaCount);

    // check WDT version in main container in admin pod
    String wdtVersion =
        assertDoesNotThrow(() -> checkWDTVersion(domainNamespace, adminServerPodName,
            "/aux", this.getClass().getSimpleName()));

    assertFalse(wdtVersion.contains(WDT_TEST_VERSION),
        "Old version of WDT is copied");

    // create domain custom resource using 2 auxiliary images with default sourceWDTInstallHome for both images
    logger.info("Creating domain custom resource with domainUid {0} and auxiliary images {1} {2}",
        domainUid, miiAuxiliaryImage1, miiAuxiliaryImage4);
    DomainResource domainCR2 = CommonMiiTestUtils.createDomainResourceWithAuxiliaryImage(
        domainUid + "1", domainNamespace,
        WEBLOGIC_IMAGE_TO_USE_IN_SPEC, adminSecretName, createSecretsForImageRepos(domainNamespace),
        encryptionSecretName, auxiliaryImagePath,
        miiAuxiliaryImage1,
        miiAuxiliaryImage4);

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

    List<String> archiveList = Collections.singletonList(appParams.appArchiveDir() + "/" + MII_BASIC_APP_NAME + ".zip");

    List<String> modelList = new ArrayList<>();
    modelList.add(MODEL_DIR + "/" + MII_BASIC_WDT_MODEL_FILE);

    // create image5 with model and no wdt installation files
    WitParams witParams =
        new WitParams()
            .modelImageName(MII_AUXILIARY_IMAGE_NAME)
            .modelImageTag(miiAuxiliaryImage5Tag)
            .modelFiles(modelList)
            .modelArchiveFiles(archiveList)
            .wdtVersion("NONE");
    createAndPushAuxiliaryImage(MII_AUXILIARY_IMAGE_NAME,miiAuxiliaryImage5Tag, witParams);
    OffsetDateTime timestamp = now();

    // create domain custom resource using auxiliary image
    logger.info("Creating domain custom resource with domainUid {0} and auxiliary image {1}",
        domainUid, miiAuxiliaryImage5);
    DomainResource domainCR = createDomainResourceWithAuxiliaryImage(domainUid, domainNamespace,
        WEBLOGIC_IMAGE_TO_USE_IN_SPEC, adminSecretName, createSecretsForImageRepos(domainNamespace),
        encryptionSecretName, replicaCount, auxiliaryImagePathCustom,
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
   * Verify the create domain call failed with expected error message This is a validation
   * check.
   */
  @Test
  @DisplayName("Test to create domain using multiple auxiliary images with specified sourceWdtInstallHome")
  void testSourceWDTInstallHomeSetAtMultipleAIs() {

    final String auxiliaryImagePathCustom = "/customauxiliary";
    final String domainUid = "domain4";

    // image1 with model files for domain config, ds, app and wdt install files
    //createAuxiliaryImageWithDomainConfig(miiAuxiliaryImage6, auxiliaryImagePathCustom);

    // admin/managed server name here should match with model yaml
    List<String> archiveList = Collections.singletonList(appParams.appArchiveDir() + "/" + MII_BASIC_APP_NAME + ".zip");

    List<String> modelList = new ArrayList<>();
    modelList.add(MODEL_DIR + "/" + MII_BASIC_WDT_MODEL_FILE);
    modelList.add(MODEL_DIR + "/multi-model-one-ds.20.yaml");

    WitParams witParams =
        new WitParams()
            .modelImageName(MII_AUXILIARY_IMAGE_NAME)
            .modelImageTag(miiAuxiliaryImage6Tag)
            .modelFiles(modelList)
            .modelArchiveFiles(archiveList)
            .wdtHome(auxiliaryImagePathCustom)
            .wdtModelHome(auxiliaryImagePathCustom + "/models");
    createAndPushAuxiliaryImage(MII_AUXILIARY_IMAGE_NAME, miiAuxiliaryImage6Tag, witParams);

    modelList = new ArrayList<>();
    modelList.add(MODEL_DIR + "/model.jms2.yaml");
    // image2 with model files for jms config

    witParams =
        new WitParams()
            .modelImageName(MII_AUXILIARY_IMAGE_NAME)
            .modelImageTag(miiAuxiliaryImage7Tag)
            .modelFiles(modelList)
            .wdtModelOnly(true)
            .wdtVersion("NONE")
            .wdtHome(auxiliaryImagePathCustom)
            .wdtModelHome(auxiliaryImagePathCustom + "/models");
    createAndPushAuxiliaryImage(MII_AUXILIARY_IMAGE_NAME, miiAuxiliaryImage7Tag, witParams);

    // create domain custom resource using auxiliary images
    String[] images = {miiAuxiliaryImage6, miiAuxiliaryImage7};
    DomainResource domainCR = CommonMiiTestUtils.createDomainResource(domainUid, domainNamespace,
        WEBLOGIC_IMAGE_TO_USE_IN_SPEC, adminSecretName, createSecretsForImageRepos(domainNamespace),
        encryptionSecretName,
        auxiliaryImagePathCustom, miiAuxiliaryImage6,
        miiAuxiliaryImage7);

    // add the sourceWDTInstallHome and sourceModelHome for both aux images.
    for (String cmImageName : images) {
      AuxiliaryImage auxImage = new AuxiliaryImage()
          .image(cmImageName).imagePullPolicy(IMAGE_PULL_POLICY);
      auxImage.sourceWDTInstallHome(auxiliaryImagePathCustom + "/weblogic-deploy")
          .sourceModelHome(auxiliaryImagePathCustom + "/models");
      domainCR.spec().configuration().model().withAuxiliaryImage(auxImage);
    }

    logger.info("Creating domain custom resource for domainUid {0} in namespace {1}",
        domainUid, domainNamespace);

    String errorMessage = "The sourceWDTInstallHome value must be set for only one auxiliary image";
    boolean succeeded = true;
    ApiException exception = null;
    try {
      succeeded = createDomainCustomResource(domainCR);
    } catch (ApiException e) {
      exception = e;
    }
    assertTrue(failedWithExpectedErrorMsg(succeeded, exception, errorMessage),
        String.format("Create domain custom resource unexpectedly succeeded for %s in namespace %s",
            domainUid, domainNamespace));

  }

  private boolean failedWithExpectedErrorMsg(boolean succeeded, ApiException exception, String expectedErrorMsg) {
    return !succeeded || hasExpectedException(exception, expectedErrorMsg);
  }

  private boolean hasExpectedException(ApiException exception, String expectedMsg) {
    return exception != null && exception.getResponseBody().contains(expectedMsg);
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

    WitParams witParams =
            new WitParams()
                    .modelImageName(MII_AUXILIARY_IMAGE_NAME)
                    .modelImageTag(miiAuxiliaryImage8Tag)
                    .wdtHome(auxiliaryImagePathCustom)
                    .wdtModelHome(auxiliaryImagePathCustom + "/models")
                    .wdtVersion("latest");
    createAndPushAuxiliaryImage(MII_AUXILIARY_IMAGE_NAME,miiAuxiliaryImage8Tag, witParams);

    OffsetDateTime timestamp = now();

    // create domain custom resource using auxiliary image
    logger.info("Creating domain custom resource with domainUid {0} and auxiliary image {1}",
            domainUid, miiAuxiliaryImage8);
    DomainResource domainCR = createDomainResourceWithAuxiliaryImage(domainUid, domainNamespace,
            WEBLOGIC_IMAGE_TO_USE_IN_SPEC, adminSecretName, createSecretsForImageRepos(domainNamespace),
            encryptionSecretName, replicaCount, auxiliaryImagePathCustom,
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
   * Create a domain using auxiliary image with configMap with model files.
   * Verify domain is created and running.
   */
  @Test
  @DisplayName("Test to create domain using auxiliary image with configMap containing model files"
      + " with empty model files dir in the auxiliary image")
  void testCreateDomainWithConfigMapAndEmptyModelFileDir() {

    final String auxiliaryImagePathCustom = "/customauxiliary";
    String domainUid = "testdomain8";
    String adminServerPodName = domainUid + "-admin-server";
    String managedServerPrefix = domainUid + "-managed-server";
    List<String> archiveList = Collections.singletonList(appParams.appArchiveDir() + "/" + MII_BASIC_APP_NAME + ".zip");

    WitParams witParams =
            new WitParams()
                    .modelImageName(MII_AUXILIARY_IMAGE_NAME)
                    .modelImageTag(miiAuxiliaryImage12Tag)
                    .wdtHome(auxiliaryImagePathCustom)
                    .modelArchiveFiles(archiveList)
                    .wdtModelHome(auxiliaryImagePathCustom + "/models");
    createAndPushAuxiliaryImage(MII_AUXILIARY_IMAGE_NAME,miiAuxiliaryImage12Tag, witParams);

    //create empty configMap with no models files and verify that domain creation failed.
    List<Path> cmFiles = new ArrayList<>();

    String configMapName1 = "modelfiles1-cm";

    //add model files to configmap and verify domain is running
    cmFiles.add(
            Paths.get(MODEL_DIR + "/" + MII_BASIC_WDT_MODEL_FILE));
    cmFiles.add(Paths.get(MODEL_DIR + "/multi-model-one-ds.20.yaml"));
    assertDoesNotThrow(
            () -> createConfigMapForDomainCreation(
                    configMapName1, cmFiles, domainNamespace, this.getClass().getSimpleName()),
            "Create configmap for domain creation failed");

    // create domain custom resource using auxiliary image
    logger.info("Creating domain custom resource with domainUid {0} and auxiliary image {1}",
            domainUid, miiAuxiliaryImage12);
    DomainResource domainCR1 = createDomainResourceWithAuxiliaryImage(domainUid, domainNamespace,
            WEBLOGIC_IMAGE_TO_USE_IN_SPEC, adminSecretName, createSecretsForImageRepos(domainNamespace),
            encryptionSecretName, replicaCount, auxiliaryImagePathCustom,
            miiAuxiliaryImage12);
    assertNotNull(domainCR1, "failed to create domain resource");
    domainCR1.spec().configuration().model().configMap(configMapName1);
    createDomainAndVerify(domainUid, domainCR1, domainNamespace,
            adminServerPodName, managedServerPrefix, replicaCount);
  }

  /**
   * Create a domain using auxiliary image with empty configMap and no model files.
   * Verify domain events and operator log contains the expected error message from WDT tool.
   * WDT Create Primordial Domain Failed.
   */
  @Test
  @DisplayName("Test to create domain using auxiliary image with "
      + "empty configMap with no models files and verify that domain creation failed")
  void testCreateDomainWithEmptyConfigMapWithNoModelFiles() {

    final String auxiliaryImagePathCustom = "/customauxiliary";
    String domainUid = "testdomain9";
    String adminServerPodName = domainUid + "-admin-server";
    String managedServerPrefix = domainUid + "-managed-server";
    List<String> archiveList = Collections.singletonList(appParams.appArchiveDir() + "/" + MII_BASIC_APP_NAME + ".zip");

    WitParams witParams =
        new WitParams()
            .modelImageName(MII_AUXILIARY_IMAGE_NAME)
            .modelImageTag(miiAuxiliaryImage16Tag)
            .wdtHome(auxiliaryImagePathCustom)
            .modelArchiveFiles(archiveList)
            .wdtModelHome(auxiliaryImagePathCustom + "/models");
    createAndPushAuxiliaryImage(MII_AUXILIARY_IMAGE_NAME,miiAuxiliaryImage16Tag, witParams);

    String configMapName = "modelfiles-cm";
    logger.info("Create ConfigMap {0} in namespace {1} with WDT models {3} and {4}",
        configMapName, domainNamespace, MII_BASIC_WDT_MODEL_FILE, "/multi-model-one-ds.20.yaml");

    //create empty configMap with no models files and verify that domain creation failed.
    List<Path> cmFiles = new ArrayList<>();

    assertDoesNotThrow(
        () -> createConfigMapForDomainCreation(
            configMapName, cmFiles, domainNamespace, this.getClass().getSimpleName()),
        "Create configmap for domain creation failed");
    OffsetDateTime timestamp = now();

    // create domain custom resource using auxiliary image
    logger.info("Creating domain custom resource with domainUid {0} and auxiliary image {1}",
        domainUid, miiAuxiliaryImage16);
    final DomainResource domainCR = createDomainResourceWithAuxiliaryImage(domainUid, domainNamespace,
        WEBLOGIC_IMAGE_TO_USE_IN_SPEC, adminSecretName, createSecretsForImageRepos(domainNamespace),
        encryptionSecretName, replicaCount, auxiliaryImagePathCustom,
        miiAuxiliaryImage16);
    assertNotNull(domainCR, "failed to create domain resource");
    domainCR.spec().configuration().model().configMap(configMapName);
    // create domain and verify it is failed
    logger.info("Creating domain custom resource for domainUid {0} in namespace {1}",
        domainUid, domainNamespace);
    assertDoesNotThrow(() -> createDomainCustomResource(domainCR),
        String.format("Create domain custom resource failed with ApiException for %s in namespace %s",
            domainUid, domainNamespace));
    // check the introspector pod log contains the expected error message
    String expectedErrorMsg = "Model in Image: WDT Create Primordial Domain Failed";

    // check the domain event contains the expected error message
    checkDomainEventContainsExpectedMsg(opNamespace, domainNamespace, domainUid, DOMAIN_FAILED,
        "Warning", timestamp, expectedErrorMsg);

    // check the operator pod log contains the expected error message
    checkPodLogContainsString(opNamespace, operatorPodName, expectedErrorMsg);

    // check there are no admin server and managed server pods and services not created
    checkPodDoesNotExist(adminServerPodName, domainUid, domainNamespace);
    checkServiceDoesNotExist(adminServerPodName, domainNamespace);
    for (int i = 1; i <= replicaCount; i++) {
      checkPodDoesNotExist(managedServerPrefix + i, domainUid, domainNamespace);
      checkServiceDoesNotExist(managedServerPrefix + i, domainNamespace);
    }

    // delete domain9
    deleteDomainResource(domainNamespace, domainUid);
    deleteConfigMap(configMapName, domainNamespace);
    testUntil(
        withLongRetryPolicy,
        () -> listConfigMaps(domainNamespace).getItems().stream().noneMatch(cm
            -> cm.getMetadata() != null && cm.getMetadata().getName() !=  null
            && cm.getMetadata().getName().equals(configMapName)),
        logger,
        "configmap {0} to be deleted.", configMapName);
  }

  /**
   * Create a domain using auxiliary image with custom wdtModelHome and wdtInstallHome
   * where the wdtModelHome ("/aux/y/models") is placed
   * under the wdtInstallHome (/aux") directory. Verify domain creation.
   */
  @Test
  @DisplayName("Test to create domain using auxiliary image with"
          + " custom wdtModelHome and wdtInstallHome"
          + " where the wdtModelHome is placed under wdtInstallHome directory")
  void testCreateDomainUseWdtModelHomeDirUnderWdtInstallHome() {

    String wdtInstallPath = "/aux";
    String wdtModelHomePath = "/aux/y";
    String domainUid = "testdomain13";

    // creating image13 with wdtModelHome dir located under wdtInstallHome dir, verify domain is started
    createDomainUsingAuxImageWithCustomWdtModelHomeInstallHome(wdtInstallPath,
            wdtModelHomePath,domainUid,miiAuxiliaryImage13Tag);
  }

  /**
   * Create a domain using auxiliary image with custom wdtModelHome and wdtInstallHome
   * where the wdtInstallHome dir located under wdtModelHome
   * Verify domain creation.
   */
  @Test
  @DisplayName("Test to create domain using auxiliary image with"
          + " custom wdtModelHome and wdtInstallHome"
          + " where the wdtInstallHome is placed under wdtModelHome directory or vice-versa")
  void testCreateDomainUseWdtInstallHomeDirUnderWdtModelHome() {

    // create image15 with wdtInstallHome under wdtModelHome dir,verify error message
    String wdtInstallPath = "/aux/y";
    String wdtModelHomePath = "/aux";
    String domainUid = "testdomain15";

    createDomainUsingAuxImageWithCustomWdtModelHomeInstallHome(wdtInstallPath,
            wdtModelHomePath,domainUid,miiAuxiliaryImage15Tag);
  }

  /**
   * Create a domain using auxiliary image with custom wdtModelHome and wdtInstallHome
   * where the wdtInstallHome dir and  wdtModelHome dir are the same
   * Verify domain creation.
   */
  @Test
  @DisplayName("Test to create domain using auxiliary image with"
          + " custom wdtModelHome and wdtInstallHome"
          + " where the wdtModelHome is same as wdtInstallHome directory")
  void testCreateDomainUseWdtInstallHomeDirSameAsWdtModelHome() {

    // create image14 with same wdtModelHome and wdtInstallHome dir, verify error message
    String wdtInstallPath = "/aux";
    String wdtModelHomePath = "/aux";
    String domainUid = "testdomain14";
    createDomainUsingAuxImageWithCustomWdtModelHomeInstallHome(wdtInstallPath,
            wdtModelHomePath,domainUid,miiAuxiliaryImage14Tag);
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
    DomainResource domainCR1 = createDomainResourceWithAuxiliaryImage(domainUid, domainNamespace,
        WEBLOGIC_IMAGE_TO_USE_IN_SPEC, adminSecretName, createSecretsForImageRepos(domainNamespace),
        encryptionSecretName, replicaCount, auxiliaryImagePath,
        miiAuxiliaryImage1, miiAuxiliaryImage2);

    // create domain and verify its running
    logger.info("Creating domain {0} with auxiliary images {1} {2} in namespace {3}",
        domainUid, miiAuxiliaryImage1, miiAuxiliaryImage4, domainNamespace);
    createDomainAndVerify(domainUid, domainCR1, domainNamespace,
        adminServerPodName, managedServerPrefix, replicaCount);

    assertFalse(checkSystemResourceConfiguration(adminServerPodName, domainNamespace, "JMSSystemResources",
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
    int replicaCount = 1;

    //In case the previous test failed, ensure the created domain in the same namespace is deleted.
    if (doesDomainExist(domainUid2, DOMAIN_VERSION, errorpathDomainNamespace)) {
      deleteDomainResource(errorpathDomainNamespace, domainUid2);
    }
    OffsetDateTime timestamp = now();

    List<String> archiveList = Collections.singletonList(appParams.appArchiveDir() + "/" + MII_BASIC_APP_NAME + ".zip");

    List<String> modelList = new ArrayList<>();
    modelList.add(MODEL_DIR + "/" + MII_BASIC_WDT_MODEL_FILE);
    modelList.add(MODEL_DIR + "/multi-model-one-ds.20.yaml");
    WitParams witParams =
        new WitParams()
            .modelImageName(MII_AUXILIARY_IMAGE_NAME)
            .modelImageTag(errorPathAuxiliaryImage1Tag)
            .modelFiles(modelList)
            .modelArchiveFiles(archiveList)
            .wdtVersion("NONE");
    createAndPushAuxiliaryImage(MII_AUXILIARY_IMAGE_NAME, errorPathAuxiliaryImage1Tag, witParams);

    // create domain custom resource using auxiliary images
    logger.info("Creating domain custom resource with domainUid {0} and auxiliary image {1}",
        domainUid2, errorPathAuxiliaryImage1);

    DomainResource domainCR = CommonMiiTestUtils.createDomainResourceWithAuxiliaryImage(
        domainUid2, errorpathDomainNamespace,
        WEBLOGIC_IMAGE_TO_USE_IN_SPEC, adminSecretName, createSecretsForImageRepos(errorpathDomainNamespace),
        encryptionSecretName, auxiliaryImagePath,
        errorPathAuxiliaryImage1);

    // create domain and verify it is failed
    logger.info("Creating domain {0} with auxiliary image {1} in namespace {2}",
        domainUid2, errorPathAuxiliaryImage1, errorpathDomainNamespace);
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
    int replicaCount = 1;

    //In case the previous test failed, ensure the created domain in the same namespace is deleted.
    if (doesDomainExist(domainUid2, DOMAIN_VERSION, errorpathDomainNamespace)) {
      deleteDomainResource(errorpathDomainNamespace, domainUid2);
    }
    final String auxiliaryImagePath = "/auxiliary";
    OffsetDateTime timestamp = now();

    List<String> archiveList = Collections.singletonList(appParams.appArchiveDir() + "/" + MII_BASIC_APP_NAME + ".zip");

    List<String> modelList = new ArrayList<>();
    modelList.add(MODEL_DIR + "/model.jms2.yaml");
    WitParams witParams =
        new WitParams()
            .modelImageName(MII_AUXILIARY_IMAGE_NAME)
            .modelImageTag(errorPathAuxiliaryImage2Tag)
            .modelFiles(modelList)
            .modelArchiveFiles(archiveList)
            .wdtVersion("latest");
    createAndPushAuxiliaryImage(MII_AUXILIARY_IMAGE_NAME, errorPathAuxiliaryImage2Tag, witParams);
    // create domain custom resource using auxiliary images
    logger.info("Creating domain custom resource with domainUid {0} and auxiliary image {1}",
        domainUid2, errorPathAuxiliaryImage2);

    DomainResource domainCR = CommonMiiTestUtils.createDomainResourceWithAuxiliaryImage(
        domainUid2, errorpathDomainNamespace,
        WEBLOGIC_IMAGE_TO_USE_IN_SPEC, adminSecretName, createSecretsForImageRepos(errorpathDomainNamespace),
        encryptionSecretName, auxiliaryImagePath,
        errorPathAuxiliaryImage2);

    // create domain and verify it is failed
    logger.info("Creating domain {0} with auxiliary image {1} in namespace {2}",
        domainUid2, errorPathAuxiliaryImage2, errorpathDomainNamespace);
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
  @DisplayName("Negative Test to create domain with file in auxiliary image not accessible by oracle user")
  void testErrorPathFilePermission() {
    final String domainUid2 = "domain8";
    final String adminServerPodName = domainUid2 + "-admin-server";
    final String managedServerPrefix = domainUid2 + "-managed-server";
    final String auxiliaryImagePath = "/auxiliary";
    int replicaCount = 1;

    //In case the previous test failed, ensure the created domain in the same namespace is deleted.
    if (doesDomainExist(domainUid2, DOMAIN_VERSION, errorpathDomainNamespace)) {
      deleteDomainResource(errorpathDomainNamespace, domainUid2);
    }

    OffsetDateTime timestamp = now();

    List<String> archiveList = Collections.singletonList(appParams.appArchiveDir() + "/" + MII_BASIC_APP_NAME + ".zip");

    List<String> modelList = new ArrayList<>();
    modelList.add(MODEL_DIR + "/" + MII_BASIC_WDT_MODEL_FILE);
    modelList.add(MODEL_DIR + "/multi-model-one-ds.20.yaml");

    WitParams witParams =
        new WitParams()
            .modelImageName(MII_AUXILIARY_IMAGE_NAME)
            .modelImageTag(errorPathAuxiliaryImage3Tag)
            .modelArchiveFiles(archiveList)
            .modelFiles(modelList)
            .additionalBuildCommands(RESOURCE_DIR + "/auxiliaryimage/addBuildCommand.txt");
    createAndPushAuxiliaryImage(MII_AUXILIARY_IMAGE_NAME, errorPathAuxiliaryImage3Tag, witParams);

    // create domain custom resource using 2 auxiliary images
    logger.info("Creating domain custom resource with domainUid {0} and auxiliary images {1} {2}",
        domainUid2, errorPathAuxiliaryImage3);
    DomainResource domainCR = CommonMiiTestUtils.createDomainResourceWithAuxiliaryImage(
        domainUid2, errorpathDomainNamespace,
        WEBLOGIC_IMAGE_TO_USE_IN_SPEC, adminSecretName, createSecretsForImageRepos(errorpathDomainNamespace),
        encryptionSecretName, auxiliaryImagePath,
        errorPathAuxiliaryImage3);


    // create domain and verify it is failed
    logger.info("Creating domain {0} with auxiliary image {1} in namespace {2}",
        domainUid2, errorPathAuxiliaryImage3, errorpathDomainNamespace);
    assertDoesNotThrow(() -> createDomainCustomResource(domainCR), "createDomainCustomResource throws Exception");

    // check the introspector pod log contains the expected error message
    String expectedErrorMsg = "cp: can't open '/auxiliary/models/multi-model-one-ds.20.yaml': "
        + "Permission denied";

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
   * and verify the WDT version is updated to the one bundled with image3
   */
  @Test
  @DisplayName("Test to update WDT version using  auxiliary images")
  void testUpdateWDTVersionUsingMultipleAuxiliaryImages() {

    // admin/managed server name here should match with model yaml
    final String auxiliaryImagePath = "/auxiliary";
    final String domainUid = "domain7";
    final String adminServerPodName = domainUid + "-admin-server";
    final String managedServerPrefix = domainUid + "-managed-server";
    int replicaCount = 1;

    // Create the repo secret to pull the image
    // this secret is used only for non-kind cluster
    createTestRepoSecret(wdtDomainNamespace);

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

    List<String> archiveList = Collections.singletonList(appParams.appArchiveDir() + "/" + MII_BASIC_APP_NAME + ".zip");

    List<String> modelList = new ArrayList<>();
    modelList.add(MODEL_DIR + "/multi-model-one-ds.20.yaml");
    modelList.add(MODEL_DIR + "/" + MII_BASIC_WDT_MODEL_FILE);
    WitParams witParams =
        new WitParams()
            .modelImageName(MII_AUXILIARY_IMAGE_NAME)
            .modelImageTag(miiAuxiliaryImage9Tag)
            .modelArchiveFiles(archiveList)
            .modelFiles(modelList)
            .wdtVersion("NONE");
    createAndPushAuxiliaryImage(MII_AUXILIARY_IMAGE_NAME,miiAuxiliaryImage9Tag, witParams);

    // create second auxiliary image with older wdt installation files only
    logger.info("Create Auxiliary image with older wdt installation {0}", WDT_TEST_VERSION);
    witParams =
        new WitParams()
            .modelImageName(MII_AUXILIARY_IMAGE_NAME)
            .modelImageTag(miiAuxiliaryImage10Tag)
            .wdtVersion(WDT_TEST_VERSION);
    createAndPushAuxiliaryImage(MII_AUXILIARY_IMAGE_NAME, miiAuxiliaryImage10Tag, witParams);

    // create third auxiliary image with newest wdt installation files only
    logger.info("Create AUX IMAGE with latest wdt installation");
    logger.info("Create Auxiliary image with latest wdt installation");
    witParams =
        new WitParams()
            .modelImageName(MII_AUXILIARY_IMAGE_NAME)
            .modelImageTag(miiAuxiliaryImage11Tag)
            .wdtVersion("latest");
    createAndPushAuxiliaryImage(MII_AUXILIARY_IMAGE_NAME,miiAuxiliaryImage11Tag, witParams);

    // create domain custom resource using 2 auxiliary images ( image1, image2)
    logger.info("Creating domain custom resource with domainUid {0} and auxiliary images {1} {2}",
        domainUid, miiAuxiliaryImage9, miiAuxiliaryImage10);
    DomainResource domainCR = CommonMiiTestUtils.createDomainResourceWithAuxiliaryImage(domainUid, wdtDomainNamespace,
        WEBLOGIC_IMAGE_TO_USE_IN_SPEC, adminSecretName, createSecretsForImageRepos(wdtDomainNamespace),
        encryptionSecretName, auxiliaryImagePath,
        miiAuxiliaryImage9,
        miiAuxiliaryImage10);

    // create domain and verify its running
    logger.info("Creating domain {0} with auxiliary images {1} {2} in namespace {3}",
        domainUid, miiAuxiliaryImage9, miiAuxiliaryImage10, wdtDomainNamespace);
    createDomainAndVerify(domainUid, domainCR, wdtDomainNamespace,
        adminServerPodName, managedServerPrefix, replicaCount);

    //create router for admin service on OKD in wdtDomainNamespace
    adminSvcExtHost = createRouteForOKD(getExternalServicePodName(adminServerPodName), wdtDomainNamespace);
    logger.info("admin svc host = {0}", adminSvcExtHost);

    // check configuration for DataSource in the running domain
    testUntil(
        () -> checkSystemResourceConfigViaAdminPod(adminServerPodName, wdtDomainNamespace,
            "JDBCSystemResources/TestDataSource/JDBCResource/JDBCDriverParams",
            "jdbc:oracle:thin:@\\/\\/xxx.xxx.x.xxx:1521\\/ORCLCDB"),
        logger,
        "Checking for adminServerPodName: {0} in namespace : {1} if resourceName: {2} has the right value",
        adminServerPodName,
        wdtDomainNamespace,
        "JDBCSystemResources/TestDataSource/JDBCResource/JDBCDriverParams");
    logger.info("Found the DataResource configuration");
    //check WDT version in the image equals the  provided WDT_TEST_VERSION
    assertDoesNotThrow(() -> {
      String wdtVersion = checkWDTVersion(wdtDomainNamespace,
          adminServerPodName, "/aux",
          this.getClass().getSimpleName());
      logger.info("(before patch) Returned WDT Version {0}", wdtVersion);
      assertEquals("WebLogic Deploy Tooling " + WDT_TEST_VERSION, wdtVersion,
          " Used WDT in the auxiliary image does not match the expected");
    }, "Can't retrieve wdt version file or version does match the expected");

    //updating wdt to latest version by patching the domain with image3
    patchDomainWithAuxiliaryImageAndVerify(miiAuxiliaryImage10,
        miiAuxiliaryImage11, domainUid, wdtDomainNamespace, replicaCount);

    //check that WDT version is updated to latest 
    assertDoesNotThrow(() -> {
      String wdtVersion = checkWDTVersion(wdtDomainNamespace, adminServerPodName,
          "/aux", this.getClass().getSimpleName());
      logger.info("(after patch) Returned WDT Version {0}", wdtVersion);
      assertNotEquals("WebLogic Deploy Tooling " + WDT_TEST_VERSION,wdtVersion,
          " Used WDT in the auxiliary image was not updated");
    }, "Can't retrieve wdt version file "
        + "or wdt was not updated after patching with auxiliary image");

    // check configuration for DataSource in the running domain
    testUntil(
        () -> checkSystemResourceConfigViaAdminPod(adminServerPodName, wdtDomainNamespace,
            "JDBCSystemResources/TestDataSource/JDBCResource/JDBCDriverParams",
            "jdbc:oracle:thin:@\\/\\/xxx.xxx.x.xxx:1521\\/ORCLCDB"),
        logger,
        "Checking for adminServerPodName: {0} in namespace: {1} if resourceName: {2} has the right value",
        adminServerPodName,
        wdtDomainNamespace,
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
    DomainResource domainCR = createDomainResourceWithAuxiliaryImage(domainUid, domainNamespace,
        WEBLOGIC_IMAGE_TO_USE_IN_SPEC, adminSecretName, createSecretsForImageRepos(domainNamespace),
        encryptionSecretName, replicaCount, auxiliaryImagePath,
        aiThatDoesntExist + ":" + MII_BASIC_IMAGE_TAG);
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
    patchDomainWithAuxiliaryImageAndVerify(aiThatDoesntExist + ":" + MII_BASIC_IMAGE_TAG,
        miiAuxiliaryImage1, domainUid,
        domainNamespace, false, replicaCount);

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
   * Update the existing application to a new version.
   * Create a new auxiliary image with the new application and model files for the
   * domain. Patch the domain by replacing the existing auxiliary image with the
   * new auxiliary image. Verify the domain is rolled and the new version of the
   * application is accessible.
   *
   */
  @Test
  @DisplayName("Update the sample-app application to version 2")
  void testUpdateApplicationUsingAI() {

    // check the sample app is accessible from managed servers
    checkApplicationIsAccessible(replicaCount, MII_APP_RESPONSE_V1);

    // application in the new image contains what is in the original application directory sample-app,
    // plus the replacements or/and additions in the second application directory sample-app-2.
    final String appDir1 = "sample-app";
    final String appDir2 = "sample-app-2";
    List<String> appDirList = Arrays.asList(appDir1, appDir2);

    logger.info("Build an application archive using what is in {0}", appDirList);
    assertTrue(
        buildAppArchive(appParams.srcDirList(appDirList)),
        String.format("Failed to create application archive for %s", MII_BASIC_APP_NAME));

    logger.info("Build the archive list that contains {0}",
        String.format("%s/%s.zip", appParams.appArchiveDir(), MII_BASIC_APP_NAME));
    List<String> archiveList =
        Collections.singletonList(
            String.format("%s/%s.zip", appParams.appArchiveDir(), MII_BASIC_APP_NAME));

    List<String> modelList = new ArrayList<>();
    modelList.add(MODEL_DIR + "/" + MII_BASIC_WDT_MODEL_FILE);
    modelList.add(MODEL_DIR + "/multi-model-one-ds.20.yaml");
    modelList.add(MODEL_DIR + "/model.jms2.yaml");
    logger.info("Create auxiliary image with model files and app");
    createPushAuxiliaryImageWithDomainConfig(MII_AUXILIARY_IMAGE_NAME, miiAuxiliaryImage17Tag, archiveList, modelList);

    // patch domain resource with new image by replacing old image
    // and verify rolling restart occurred
    patchDomainWithAuxiliaryImageAndVerify(oldMiiAuxImageNameInDomain1,
        miiAuxiliaryImage17,
        domainUid1, domainNamespace, replicaCount);
    // set the old image name for other tests which patch the domain
    oldMiiAuxImageNameInDomain1 = miiAuxiliaryImage17;
    // check updated sample app is accessible from managed servers
    checkApplicationIsAccessible(replicaCount, MII_APP_RESPONSE_V2);
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
    deleteImage(miiAuxiliaryImage9);
    deleteImage(miiAuxiliaryImage10);
    deleteImage(miiAuxiliaryImage11);
    deleteImage(miiAuxiliaryImage12);
    deleteImage(miiAuxiliaryImage13);
    deleteImage(miiAuxiliaryImage14);
    deleteImage(miiAuxiliaryImage15);
    deleteImage(miiAuxiliaryImage16);
    deleteImage(errorPathAuxiliaryImage1);
    deleteImage(errorPathAuxiliaryImage2);
    deleteImage(errorPathAuxiliaryImage3);
    deleteImage(errorPathAuxiliaryImage4);
  }

  private static void checkConfiguredJMSresouce(String domainNamespace, String adminServerPodName,
                                                String adminSvcExtHost) {
    verifyConfiguredSystemResource(domainNamespace, adminServerPodName, adminSvcExtHost,
        "JMSSystemResources", "TestClusterJmsModule2", "200");
  }

  private void checkConfiguredJDBCresouce(String domainNamespace, String adminServerPodName, String adminSvcExtHost) {
    verifyConfiguredSystemResouceByPath(domainNamespace, adminServerPodName, adminSvcExtHost,
        "JDBCSystemResources/TestDataSource/JDBCResource/JDBCDriverParams",
        "jdbc:oracle:thin:@\\/\\/localhost:7001\\/dbsvc");
  }

  private DomainResource createDomainResourceWithAuxiliaryImage(
      String domainResourceName,
      String domNamespace,
      String baseImageName,
      String adminSecretName,
      String[] repoSecretName,
      String encryptionSecretName,
      int replicaCount,
      String auxiliaryImagePath,
      String... auxiliaryImageName) {

    return createDomainResourceWithAuxiliaryImage(
            domainResourceName,
            domNamespace,
            baseImageName,
            adminSecretName,
            repoSecretName,
            encryptionSecretName,
        auxiliaryImagePath, auxiliaryImagePath, replicaCount,
        auxiliaryImageName);
  }

  private DomainResource createDomainResourceWithAuxiliaryImage(
      String domainResourceName,
      String domNamespace,
      String baseImageName,
      String adminSecretName,
      String[] repoSecretName,
      String encryptionSecretName,
      String sourceWDTInstallHome, String sourceWDTModelHome, int replicaCount,
      String... auxiliaryImageName) {

    DomainResource domainCR = CommonMiiTestUtils.createDomainResource(domainResourceName, domNamespace,
            baseImageName, adminSecretName, repoSecretName,
            encryptionSecretName);
    int index = 0;
    for (String cmImageName: auxiliaryImageName) {
      AuxiliaryImage auxImage = new AuxiliaryImage()
          .image(cmImageName).imagePullPolicy(IMAGE_PULL_POLICY);
      //Only add the sourceWDTInstallHome and sourceModelHome for the first aux image.
      if (index == 0) {
        auxImage.sourceWDTInstallHome(sourceWDTInstallHome + "/weblogic-deploy")
                .sourceModelHome(sourceWDTModelHome + "/models");
      } else {
        auxImage.sourceWDTInstallHome("none")
                .sourceModelHome("none");
      }
      domainCR.spec().configuration().model().withAuxiliaryImage(auxImage);
      index++;
    }
    domainCR.spec().replicas(replicaCount);
    return domainCR;
  }

  private void createDomainUsingAuxImageWithCustomWdtModelHomeInstallHome(String wdtInstallPath,
                                                                                               String wdtModelHomePath,
                                                                                               String domainUid,
                                                                                               String imageTag) {

    List<String> archiveList = Collections.singletonList(appParams.appArchiveDir() + "/" + MII_BASIC_APP_NAME + ".zip");

    List<String> modelList = new ArrayList<>();
    modelList.add(MODEL_DIR + "/" + MII_BASIC_WDT_MODEL_FILE);
    final String adminServerPodName = domainUid + "-admin-server";
    final String managedServerPrefix = domainUid + "-managed-server";

    WitParams witParams =
            new WitParams()
                    .modelImageName(MII_AUXILIARY_IMAGE_NAME)
                    .modelImageTag(imageTag)
                    .modelFiles(modelList)
                    .wdtModelHome(wdtModelHomePath + "/models")
                    .wdtHome(wdtInstallPath)
                    .modelArchiveFiles(archiveList);
    createAndPushAuxiliaryImage(MII_AUXILIARY_IMAGE_NAME,imageTag, witParams);
    String imageName = MII_AUXILIARY_IMAGE_NAME + ":" + imageTag;
    // create domain custom resource using auxiliary image13
    logger.info("Creating domain custom resource with domainUid {0} and auxiliary image {1}",
            domainUid, imageName);
    DomainResource domainCR = createDomainResourceWithAuxiliaryImage(domainUid, domainNamespace,
            WEBLOGIC_IMAGE_TO_USE_IN_SPEC, adminSecretName, createSecretsForImageRepos(domainNamespace),
            encryptionSecretName, wdtInstallPath, wdtModelHomePath, replicaCount,
        imageName);

    assertNotNull(domainCR,
            String.format("Create domain custom resource failed with ApiException for %s in namespace %s",
                    domainUid, domainNamespace));
    // create domain and verify its running
    logger.info("Creating domain {0} with auxiliary images {1} in namespace {3}",
            domainUid, imageName, domainNamespace);
    createDomainAndVerify(domainUid, domainCR, domainNamespace,
            adminServerPodName, managedServerPrefix, replicaCount);

    deleteDomainResource(domainNamespace, domainUid);
  }

  private void checkApplicationIsAccessible(
      int replicaCount, String expectedResponse) {
    for (int i = 1; i <= replicaCount; i++) {
      int index = i;
      testUntil(withStandardRetryPolicy,
          () -> appAccessibleInPod(domainNamespace, managedServerPrefixDomain1 + index, "8001",
              "sample-war/index.jsp", expectedResponse + index),
          logger,
          "application {0} is running on pod {1} in namespace {2}",
          "sample-war",
          managedServerPrefixDomain1 + index,
          domainNamespace);
    }
  }
}
