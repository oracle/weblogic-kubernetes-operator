// Copyright (c) 2020, Oracle Corporation and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.weblogic.kubernetes;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import io.kubernetes.client.custom.V1Patch;
import io.kubernetes.client.openapi.models.V1EnvVar;
import io.kubernetes.client.openapi.models.V1LocalObjectReference;
import io.kubernetes.client.openapi.models.V1ObjectMeta;
import io.kubernetes.client.openapi.models.V1SecretReference;
import io.kubernetes.client.openapi.models.V1ServiceAccount;
import oracle.weblogic.domain.AdminServer;
import oracle.weblogic.domain.AdminService;
import oracle.weblogic.domain.Channel;
import oracle.weblogic.domain.Cluster;
import oracle.weblogic.domain.Configuration;
import oracle.weblogic.domain.Domain;
import oracle.weblogic.domain.DomainSpec;
import oracle.weblogic.domain.Model;
import oracle.weblogic.domain.ServerPod;
import oracle.weblogic.kubernetes.actions.impl.primitive.HelmParams;
import oracle.weblogic.kubernetes.annotations.IntegrationTest;
import oracle.weblogic.kubernetes.annotations.Namespaces;
import oracle.weblogic.kubernetes.annotations.tags.MustNotRunInParallel;
import oracle.weblogic.kubernetes.annotations.tags.Slow;
import oracle.weblogic.kubernetes.logging.LoggingFacade;
import org.awaitility.core.ConditionFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import static java.util.concurrent.TimeUnit.MINUTES;
import static java.util.concurrent.TimeUnit.SECONDS;
import static oracle.weblogic.kubernetes.TestConstants.ADMIN_SERVER_NAME_BASE;
import static oracle.weblogic.kubernetes.TestConstants.DOMAIN_API_VERSION;
import static oracle.weblogic.kubernetes.TestConstants.MII_APP_RESPONSE_V1;
import static oracle.weblogic.kubernetes.TestConstants.MII_APP_RESPONSE_V2;
import static oracle.weblogic.kubernetes.TestConstants.MII_APP_RESPONSE_V3;
import static oracle.weblogic.kubernetes.TestConstants.MII_BASIC_APP_NAME;
import static oracle.weblogic.kubernetes.TestConstants.MII_BASIC_IMAGE_NAME;
import static oracle.weblogic.kubernetes.TestConstants.MII_BASIC_IMAGE_TAG;
import static oracle.weblogic.kubernetes.TestConstants.MII_BASIC_WDT_MODEL_FILE;
import static oracle.weblogic.kubernetes.TestConstants.MII_TWO_APP_WDT_MODEL_FILE;
import static oracle.weblogic.kubernetes.TestConstants.REPO_DUMMY_VALUE;
import static oracle.weblogic.kubernetes.TestConstants.REPO_NAME;
import static oracle.weblogic.kubernetes.TestConstants.REPO_PASSWORD;
import static oracle.weblogic.kubernetes.TestConstants.REPO_REGISTRY;
import static oracle.weblogic.kubernetes.TestConstants.REPO_SECRET_NAME;
import static oracle.weblogic.kubernetes.TestConstants.REPO_USERNAME;
import static oracle.weblogic.kubernetes.actions.ActionConstants.ARCHIVE_DIR;
import static oracle.weblogic.kubernetes.actions.ActionConstants.MODEL_DIR;
import static oracle.weblogic.kubernetes.actions.ActionConstants.WDT_VERSION;
import static oracle.weblogic.kubernetes.actions.ActionConstants.WIT_BUILD_DIR;
import static oracle.weblogic.kubernetes.actions.ActionConstants.WLS;
import static oracle.weblogic.kubernetes.actions.ActionConstants.WLS_BASE_IMAGE_NAME;
import static oracle.weblogic.kubernetes.actions.TestActions.buildAppArchive;
import static oracle.weblogic.kubernetes.actions.TestActions.createImage;
import static oracle.weblogic.kubernetes.actions.TestActions.defaultAppParams;
import static oracle.weblogic.kubernetes.actions.TestActions.defaultWitParams;
import static oracle.weblogic.kubernetes.actions.TestActions.deleteDomainCustomResource;
import static oracle.weblogic.kubernetes.actions.TestActions.deleteImage;
import static oracle.weblogic.kubernetes.actions.TestActions.dockerLogin;
import static oracle.weblogic.kubernetes.actions.TestActions.dockerPush;
import static oracle.weblogic.kubernetes.actions.TestActions.patchDomainCustomResource;
import static oracle.weblogic.kubernetes.assertions.TestAssertions.appAccessibleInPod;
import static oracle.weblogic.kubernetes.assertions.TestAssertions.appNotAccessibleInPod;
import static oracle.weblogic.kubernetes.assertions.TestAssertions.doesImageExist;
import static oracle.weblogic.kubernetes.assertions.TestAssertions.domainResourceImagePatched;
import static oracle.weblogic.kubernetes.assertions.TestAssertions.podImagePatched;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.checkPodReady;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.checkServiceExists;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.createDockerRegistrySecret;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.createDomainAndVerify;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.createMiiImageAndVerify;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.createSecretWithUsernamePassword;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.dockerLoginAndPushImageToRegistry;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.installAndVerifyOperator;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.upgradeAndVerifyOperator;
import static oracle.weblogic.kubernetes.utils.FileUtils.checkDirectory;
import static oracle.weblogic.kubernetes.utils.ThreadSafeLogger.getLogger;
import static org.awaitility.Awaitility.with;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

// Test to create model in image domain and verify the domain started successfully
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DisplayName("Test to create model in image domain and start the domain")
@IntegrationTest
class ItMiiDomain {

  private static HelmParams opHelmParams = null;
  private static V1ServiceAccount serviceAccount = null;
  private String serviceAccountName = null;
  private static String opNamespace = null;
  private static String operatorImage = null;
  private static String domainNamespace = null;
  private static String domainNamespace1 = null;
  private static ConditionFactory withStandardRetryPolicy = null;
  private static ConditionFactory withQuickRetryPolicy = null;
  private static String dockerConfigJson = "";

  private String domainUid = "domain1";
  private String domainUid1 = "domain2";
  private String miiImagePatchAppV2 = null;
  private String miiImageAddSecondApp = null;
  private String miiImage = null;
  private static LoggingFacade logger = null;

  private static Map<String, Object> secretNameMap;

  /**
   * Install Operator.
   * @param namespaces list of namespaces created by the IntegrationTestWatcher by the
   JUnit engine parameter resolution mechanism
   */
  @BeforeAll
  public static void initAll(@Namespaces(3) List<String> namespaces) {
    logger = getLogger();
    // create standard, reusable retry/backoff policy
    withStandardRetryPolicy = with().pollDelay(2, SECONDS)
        .and().with().pollInterval(10, SECONDS)
        .atMost(6, MINUTES).await();

    // create a reusable quick retry policy
    withQuickRetryPolicy = with().pollDelay(0, SECONDS)
        .and().with().pollInterval(4, SECONDS)
        .atMost(10, SECONDS).await();

    // get a new unique opNamespace
    logger.info("Creating unique namespace for Operator");
    assertNotNull(namespaces.get(0), "Namespace list is null");
    opNamespace = namespaces.get(0);

    logger.info("Creating unique namespace for Domain");
    assertNotNull(namespaces.get(1), "Namespace list is null");
    domainNamespace = namespaces.get(1);
    assertNotNull(namespaces.get(2), "Namespace list is null");
    domainNamespace1 = namespaces.get(2);

    // install and verify operator
    installAndVerifyOperator(opNamespace, domainNamespace, domainNamespace1);
  }

  @Test
  @Order(1)
  @DisplayName("Create model in image domain")
  @Slow
  @MustNotRunInParallel
  public void testCreateMiiDomain() {
    // admin/managed server name here should match with model yaml in MII_BASIC_WDT_MODEL_FILE
    final String adminServerPodName = domainUid + "-admin-server";
    final String managedServerPrefix = domainUid + "-managed-server";
    final int replicaCount = 2;

    // Create the repo secret to pull the image
    assertDoesNotThrow(() -> createDockerRegistrySecret(domainNamespace),
        String.format("createSecret failed for %s", REPO_SECRET_NAME));

    // create secret for admin credentials
    logger.info("Create secret for admin credentials");
    String adminSecretName = "weblogic-credentials";
    assertDoesNotThrow(() -> createSecretWithUsernamePassword(
                                    adminSecretName,
                                    domainNamespace,
                                    "weblogic",
                                    "welcome1"),
        String.format("createSecret failed for %s", adminSecretName));

    // create encryption secret
    logger.info("Create encryption secret");
    String encryptionSecretName = "encryptionsecret";
    assertDoesNotThrow(() -> createSecretWithUsernamePassword(
                                      encryptionSecretName,
                                      domainNamespace,
                            "weblogicenc",
                            "weblogicenc"),
                    String.format("createSecret failed for %s", encryptionSecretName));

    // create the domain object
    Domain domain = createDomainResource(domainUid,
                                      domainNamespace,
                                      adminSecretName,
                                      REPO_SECRET_NAME,
                                      encryptionSecretName,
                                      replicaCount,
                              MII_BASIC_IMAGE_NAME + ":" + MII_BASIC_IMAGE_TAG);

    // create model in image domain
    logger.info("Creating model in image domain {0} in namespace {1} using docker image {2}",
        domainUid, domainNamespace, MII_BASIC_IMAGE_NAME + ":" + MII_BASIC_IMAGE_TAG);
    createDomainAndVerify(domain, domainNamespace);

    // check admin server pod is ready
    logger.info("Wait for admin server pod {0} to be ready in namespace {1}",
        adminServerPodName, domainNamespace);
    checkPodReady(adminServerPodName, domainUid, domainNamespace);

    // check managed server pods are ready
    for (int i = 1; i <= replicaCount; i++) {
      logger.info("Wait for managed server pod {0} to be ready in namespace {1}",
          managedServerPrefix + i, domainNamespace);
      checkPodReady(managedServerPrefix + i, domainUid, domainNamespace);
    }

    logger.info("Check admin service {0} is created in namespace {1}",
        adminServerPodName, domainNamespace);
    checkServiceExists(adminServerPodName, domainNamespace);

    // check managed server services created
    for (int i = 1; i <= replicaCount; i++) {
      logger.info("Check managed server service {0} is created in namespace {1}",
          managedServerPrefix + i, domainNamespace);
      checkServiceExists(managedServerPrefix + i, domainNamespace);
    }
    
    // check and wait for the application to be accessible in all server pods
    for (int i = 1; i <= replicaCount; i++) {
      checkAppRunning(
          domainNamespace,
          managedServerPrefix + i,
          "8001",
          "sample-war/index.jsp",
          MII_APP_RESPONSE_V1 + i);
    }
 
    logger.info("Domain {0} is fully started - servers are running and application is available",
        domainUid);
  }

  @Test
  @Order(2)
  @DisplayName("Create a second domain with the image from the the first test")
  @Slow
  @MustNotRunInParallel
  public void testCreateMiiSecondDomainDiffNSSameImage() {
    // admin/managed server name here should match with model yaml in MII_BASIC_WDT_MODEL_FILE
    final String adminServerPodName = domainUid1 + "-admin-server";
    final String managedServerPrefix = domainUid1 + "-managed-server";
    final int replicaCount = 2;

    // Create the repo secret to pull the image
    assertDoesNotThrow(() -> createDockerRegistrySecret(domainNamespace1),
        String.format("createSecret failed for %s", REPO_SECRET_NAME));

    // create secret for admin credentials
    logger.info("Create secret for admin credentials");
    String adminSecretName = "weblogic-credentials";
    assertDoesNotThrow(() -> createSecretWithUsernamePassword(
        adminSecretName,
        domainNamespace1,
        "weblogic",
        "welcome1"),
        String.format("createSecret failed for %s", adminSecretName));

    // create encryption secret
    logger.info("Create encryption secret");
    String encryptionSecretName = "encryptionsecret";
    assertDoesNotThrow(() -> createSecretWithUsernamePassword(
                            encryptionSecretName,
                            domainNamespace1,
                      "weblogicenc",
                      "weblogicenc"),
                    String.format("createSecret failed for %s", encryptionSecretName));

    // create the domain object
    Domain domain = createDomainResource(domainUid1,
                domainNamespace1,
                adminSecretName,
                REPO_SECRET_NAME,
                encryptionSecretName,
                replicaCount,
                MII_BASIC_IMAGE_NAME + ":" + MII_BASIC_IMAGE_TAG);

    // create model in image domain
    logger.info("Creating model in image domain {0} in namespace {1} using docker image {2}",
        domainUid1, domainNamespace1, MII_BASIC_IMAGE_NAME + ":" + MII_BASIC_IMAGE_TAG);
    createDomainAndVerify(domain, domainNamespace1);

    // check admin server pod is ready
    logger.info("Wait for admin server pod {0} to be ready in namespace {1}",
            adminServerPodName, domainNamespace1);
    checkPodReady(adminServerPodName, domainUid1, domainNamespace1);

    // check managed server pods are ready
    for (int i = 1; i <= replicaCount; i++) {
      logger.info("Wait for managed server pod {0} to be ready in namespace {1}",
              managedServerPrefix + i, domainNamespace1);
      checkPodReady(managedServerPrefix + i, domainUid1, domainNamespace1);
    }

    logger.info("Check admin service {0} is created in namespace {1}",
            adminServerPodName, domainNamespace1);
    checkServiceExists(adminServerPodName, domainNamespace1);

    // check managed server services created
    for (int i = 1; i <= replicaCount; i++) {
      logger.info("Check managed server service {0} is created in namespace {1}",
              managedServerPrefix + i, domainNamespace1);
      checkServiceExists(managedServerPrefix + i, domainNamespace1);
    }
  }

  @Test
  @Order(3)
  @DisplayName("Update the sample-app application to version 2")
  @Slow
  @MustNotRunInParallel
  public void testPatchAppV2() {
    
    // application in the new image contains what is in the original application directory sample-app, 
    // plus the replacements or/and additions in the second application directory sample-app-2.
    final String appDir1 = "sample-app";
    final String appDir2 = "sample-app-2";
    final String adminServerPodName = domainUid + "-admin-server";
    final String managedServerPrefix = domainUid + "-managed-server";
    final int replicaCount = 2;
    
    Thread accountingThread = null;
    List<Integer> appAvailability = new ArrayList<Integer>();
    
    logger.info("Start a thread to keep track of the application's availability");
    // start a new thread to collect the availability data of the application while the
    // main thread performs patching operation, and checking of the results.
    accountingThread =
        new Thread(
            () -> {
              collectAppAvaiability(
                  domainNamespace,
                  appAvailability,
                  managedServerPrefix,
                  replicaCount,
                  "8001",
                  "sample-war/index.jsp");
            });
    accountingThread.start();
   
    try {
      logger.info("Check that V1 application is still running");
      for (int i = 1; i <= replicaCount; i++) {
        quickCheckAppRunning(
            domainNamespace,
            managedServerPrefix + i,
            "8001",
            "sample-war/index.jsp",
            MII_APP_RESPONSE_V1 + i);
      }
 
      logger.info("Check that the version 2 application is NOT running");
      for (int i = 1; i <= replicaCount; i++) {
        quickCheckAppNotRunning(
            domainNamespace,
            managedServerPrefix + i,
            "8001",
            "sample-war/index.jsp",
            MII_APP_RESPONSE_V2 + i);   
      }
 
      logger.info("Create a new image with application V2");
      miiImagePatchAppV2 = updateImageWithAppV2Patch(
          String.format("%s-%s", MII_BASIC_IMAGE_NAME, "test-patch-app-v2"),
          Arrays.asList(appDir1, appDir2));

      // push the image to a registry to make the test work in multi node cluster
      pushImageIfNeeded(miiImagePatchAppV2);

      // patch the domain resource with the new image and verify that the domain resource is patched, 
      // and all server pods are patched as well.
      logger.info("Patch domain resource with image {0}, and verify the results", miiImagePatchAppV2);
      patchAndVerify(
          domainUid,
          domainNamespace,
          adminServerPodName,
          managedServerPrefix,
          replicaCount,
          miiImagePatchAppV2);

      logger.info("Check and wait for the V2 application to become available");
      for (int i = 1; i <= replicaCount; i++) {
        checkAppRunning(
            domainNamespace,
            managedServerPrefix + i,
            "8001",
            "sample-war/index.jsp",
            MII_APP_RESPONSE_V2 + i);
      } 
    } finally {
    
      if (accountingThread != null) {
        try {
          accountingThread.join();
        } catch (InterruptedException ie) {
          // do nothing
        }

        // check the application availability data that we have collected, and see if
        // the application has been available all the time since the beginning of this test method
        logger.info("Verify that V2 application was available when domain {0} was being patched with image {1}",
            domainUid, miiImagePatchAppV2); 
        assertTrue(appAlwaysAvailable(appAvailability),
            String.format("Application V2 was not always available when domain %s was being patched with image %s",
                domainUid, miiImagePatchAppV2));
      }
    }
    
    logger.info("The version 2 application has been deployed correctly on all server pods");
  }

  @Test
  @Order(4)
  @DisplayName("Update the domain with another application")
  @Slow
  @MustNotRunInParallel
  public void testAddSecondApp() {
    
    // the existing application is the combination of what are in appDir1 and appDir2 as in test case number 4,
    // the second application is in appDir3.
    final String appDir1 = "sample-app";
    final String appDir2 = "sample-app-2";
    final String appDir3 = "sample-app-3";
    final String adminServerPodName = domainUid + "-admin-server";
    final String managedServerPrefix = domainUid + "-managed-server";
    final int replicaCount = 2;

    logger.info("Check that V2 application is still running after the previous test");
    for (int i = 1; i <= replicaCount; i++) {
      quickCheckAppRunning(
          domainNamespace,
          managedServerPrefix + i,
          "8001",
          "sample-war/index.jsp",
          MII_APP_RESPONSE_V2 + i);
    }

    logger.info("Check that the new application is NOT already running");
    for (int i = 1; i <= replicaCount; i++) {
      quickCheckAppNotRunning(
          domainNamespace,
          managedServerPrefix + i,
          "8001",
          "sample-war-3/index.jsp",
          MII_APP_RESPONSE_V3 + i);
    }
   
    logger.info("Create a new image that contains the additional application");
    miiImageAddSecondApp = updateImageWithSampleApp3(
        String.format("%s-%s", MII_BASIC_IMAGE_NAME, "test-add-second-app"),
        Arrays.asList(appDir1, appDir2),
        Collections.singletonList(appDir3),
        MII_TWO_APP_WDT_MODEL_FILE);
    
    // push the image to a registry to make the test work in multi node cluster
    pushImageIfNeeded(miiImageAddSecondApp);
   
    // patch the domain resource with the new image and verify that the domain resource is patched, 
    // and all server pods are patched as well.
    logger.info("Patch the domain with image {0}, and verify the results", miiImageAddSecondApp); 
    patchAndVerify(
        domainUid,
        domainNamespace,
        adminServerPodName,
        managedServerPrefix,
        replicaCount,
        miiImageAddSecondApp);
    
    logger.info("Check and wait for the new application to become ready");
    for (int i = 1; i <= replicaCount; i++) {
      checkAppRunning(
          domainNamespace,
          managedServerPrefix + i,
          "8001",
          "sample-war-3/index.jsp",
          MII_APP_RESPONSE_V3 + i);
    }
 
    logger.info("Check and wait for the original application V2 to become ready");
    for (int i = 1; i <= replicaCount; i++) {
      checkAppRunning(
          domainNamespace,
          managedServerPrefix + i,
          "8001",
          "sample-war/index.jsp",
          MII_APP_RESPONSE_V2 + i);
    }

    logger.info("Both of the applications are running correctly after patching");
  }

  /**
   * Parameterized test to create model in image domain using different WebLogic version images
   * as parameters.
   *
   * @param imageTag WebLogic image tag
   * @param namespaces domain namespace
   */
  @ParameterizedTest
  @DisplayName("Create model in image domain using different WebLogic version images as parameters")
  @MethodSource("oracle.weblogic.kubernetes.utils.Params#webLogicImageTags")
  public void testParamsCreateMiiDomain(String imageTag, @Namespaces(1) List<String> namespaces) {
    imageTag = imageTag.trim();
    assertTrue(!imageTag.isEmpty(), "imageTag can not be empty string");
    logger.info("Using imageTag {0}", imageTag);

    logger.info("Getting unique namespace for Domain");
    assertNotNull(namespaces.get(0), "Namespace list is null");
    domainNamespace = namespaces.get(0);

    // upgrade Operator for the new domain namespace
    assertTrue(upgradeAndVerifyOperator(opNamespace, domainNamespace),
        String.format("Failed to upgrade operator in namespace %s", opNamespace));

    // admin/managed server name here should match with model yaml in MII_BASIC_WDT_MODEL_FILE
    final String adminServerPodName = domainUid + "-" + ADMIN_SERVER_NAME_BASE;
    final String managedServerPrefix = domainUid + "-managed-server";
    final int replicaCount = 2;

    // create image with model files
    logger.info("Creating image with model file and verify");
    miiImage = createMiiImageAndVerify(
        "mii-image",
        MII_BASIC_WDT_MODEL_FILE,
        MII_BASIC_APP_NAME,
        WLS_BASE_IMAGE_NAME,
        imageTag,
        WLS);

    // docker login and push image to docker registry if necessary
    dockerLoginAndPushImageToRegistry(miiImage);

    // Create the repo secret to pull the image
    assertDoesNotThrow(() -> createDockerRegistrySecret(domainNamespace),
        String.format("createSecret failed for %s", REPO_SECRET_NAME));

    // create secret for admin credentials
    logger.info("Create secret for admin credentials");
    String adminSecretName = "weblogic-credentials";
    assertDoesNotThrow(() -> createSecretWithUsernamePassword(adminSecretName, domainNamespace, "weblogic",
        "welcome1"),
        String.format("createSecret failed for %s", adminSecretName));

    // create encryption secret
    logger.info("Create encryption secret");
    String encryptionSecretName = "encryptionsecret";
    assertDoesNotThrow(() -> createSecretWithUsernamePassword(encryptionSecretName, domainNamespace, "weblogicenc",
        "weblogicenc"),
        String.format("createSecret failed for %s", encryptionSecretName));

    // create the domain object
    Domain domain = createDomainResource(domainUid, domainNamespace, adminSecretName, REPO_SECRET_NAME,
        encryptionSecretName, replicaCount, miiImage);

    // create model in image domain
    logger.info("Creating model in image domain {0} in namespace {1} using docker image {2}",
        domainUid, domainNamespace, miiImage);
    createDomainAndVerify(domain, domainNamespace);

    // check admin server pod is ready
    logger.info("Wait for admin server pod {0} to be ready in namespace {1}",
        adminServerPodName, domainNamespace);
    checkPodReady(adminServerPodName, domainUid, domainNamespace);

    // check managed server pods are ready
    for (int i = 1; i <= replicaCount; i++) {
      logger.info("Wait for managed server pod {0} to be ready in namespace {1}",
          managedServerPrefix + i, domainNamespace);
      checkPodReady(managedServerPrefix + i, domainUid, domainNamespace);
    }

    logger.info("Check admin service {0} is created in namespace {1}",
        adminServerPodName, domainNamespace);
    checkServiceExists(adminServerPodName, domainNamespace);

    // check managed server services created
    for (int i = 1; i <= replicaCount; i++) {
      logger.info("Check managed server service {0} is created in namespace {1}",
          managedServerPrefix + i, domainNamespace);
      checkServiceExists(managedServerPrefix + i, domainNamespace);
    }

    // check and wait for the application to be accessible in all server pods
    for (int i = 1; i <= replicaCount; i++) {
      checkAppRunning(
          domainNamespace,
          managedServerPrefix + i,
          "8001",
          "sample-war/index.jsp",
          MII_APP_RESPONSE_V1 + i);
    }

    logger.info("Domain {0} is fully started - servers are running and application is available",
        domainUid);

  }

  // This method is needed in this test class, since the cleanup util
  // won't cleanup the images.

  @AfterEach
  public void tearDown() {
    // delete mii domain images created for parameterized test
    if (miiImage != null) {
      deleteImage(miiImage);
    }
  }

  @AfterAll
  public void tearDownAll() {
    // Delete domain custom resource
    logger.info("Delete domain custom resource in namespace {0}", domainNamespace);
    assertDoesNotThrow(() -> deleteDomainCustomResource(domainUid, domainNamespace),
        "deleteDomainCustomResource failed with ApiException");
    logger.info("Deleted Domain Custom Resource " + domainUid + " from " + domainNamespace);

    logger.info("Delete domain custom resource in namespace {0}", domainNamespace1);
    assertDoesNotThrow(() -> deleteDomainCustomResource(domainUid1, domainNamespace1),
            "deleteDomainCustomResource failed with ApiException");
    logger.info("Deleted Domain Custom Resource " + domainUid1 + " from " + domainNamespace1);

    logger.info("Delete domain custom resource in namespace {0}", domainNamespace1);
    assertDoesNotThrow(() -> deleteDomainCustomResource(domainUid, domainNamespace1),
            "deleteDomainCustomResource failed with ApiException");
    logger.info("Deleted Domain Custom Resource " + domainUid + " from " + domainNamespace1);

    // delete the domain images created in the test class
    if (miiImagePatchAppV2 != null) {
      deleteImage(miiImagePatchAppV2);
    }
    if (miiImageAddSecondApp != null) {
      deleteImage(miiImageAddSecondApp);
    }
  }

  private void pushImageIfNeeded(String image) {
    // push the image to a registry to make the test work in multi node cluster
    if (!REPO_USERNAME.equals(REPO_DUMMY_VALUE)) {
      logger.info("docker login to registry {0}", REPO_REGISTRY);
      assertTrue(dockerLogin(REPO_REGISTRY, REPO_USERNAME, REPO_PASSWORD), "docker login failed");
    }

    // push image 
    if (!REPO_NAME.isEmpty()) {
      logger.info("docker push image {0} to registry", image);
      assertTrue(dockerPush(image), String.format("docker push failed for image %s", image));
    }
  }

  private String createUniqueImageTag() {
    // create unique image name with date
    DateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd");
    Date date = new Date();
    return dateFormat.format(date) + "-" + System.currentTimeMillis();
  }

  private String updateImageWithAppV2Patch(
      String imageName,
      List<String> appDirList
  ) {
    logger.info("Build the model file list that contains {0}", MII_BASIC_WDT_MODEL_FILE);
    List<String> modelList = 
        Collections.singletonList(String.format("%s/%s", MODEL_DIR, MII_BASIC_WDT_MODEL_FILE));
   
    logger.info("Build an application archive using what is in {0}", appDirList);
    assertTrue(
        buildAppArchive(
            defaultAppParams()
                .srcDirList(appDirList)),
        String.format("Failed to create application archive for %s",
            MII_BASIC_APP_NAME));

    logger.info("Build the archive list that contains {0}",
        String.format("%s/%s.zip", ARCHIVE_DIR, MII_BASIC_APP_NAME));
    List<String> archiveList = 
        Collections.singletonList(
            String.format("%s/%s.zip", ARCHIVE_DIR, MII_BASIC_APP_NAME));
    
    return createImageAndVerify(
      imageName,
      createUniqueImageTag(),
      modelList,
      archiveList);
  }

  private String updateImageWithSampleApp3(
      String imageName,
      List<String> appDirList1,
      List<String> appDirList2,
      String modelFile
  ) {
    logger.info("Build the model file list that contains {0}", modelFile);
    List<String> modelList = Collections.singletonList(MODEL_DIR + "/" + modelFile);
 
    String appName1 = appDirList1.get(0);
    String appName2 = appDirList2.get(0);
    
    logger.info("Build the first application archive using what is in {0}", appDirList1);
    assertTrue(
        buildAppArchive(
            defaultAppParams()
                .srcDirList(appDirList1)
                .appName(appName1)),
        String.format("Failed to create application archive for %s",
            appName1));
    
    logger.info("Build the second application archive usingt what is in {0}", appDirList2);
    assertTrue(
        buildAppArchive(
            defaultAppParams()
                .srcDirList(appDirList2)
                .appName(appName2)),
        String.format("Failed to create application archive for %s",
            appName2));
    
    logger.info("Build the archive list with two zip files: {0} and {1}",
        String.format("%s/%s.zip", ARCHIVE_DIR, appName1),
        String.format("%s/%s.zip", ARCHIVE_DIR, appName2));
    List<String> archiveList = Arrays.asList(
        String.format("%s/%s.zip", ARCHIVE_DIR, appName1),
        String.format("%s/%s.zip", ARCHIVE_DIR, appName2));
    
    return createImageAndVerify(
      imageName,
      createUniqueImageTag(),
      modelList,
      archiveList);
  }

  /**
   * Patch the domain resource with a new image.
   * Here is an example of the JSON patch string that is constructed in this method.
   * [
   *   {"op": "replace", "path": "/spec/image", "value": "mii-image:v2" }
   * ]
   * 
   * @param domainResourceName name of the domain resource
   * @param namespace Kubernetes namespace that the domain is hosted
   * @param image name of the new image
   */
  private void patchDomainResourceImage(
      String domainResourceName,
      String namespace,
      String image
  ) {
    String patch = 
        String.format("[\n  {\"op\": \"replace\", \"path\": \"/spec/image\", \"value\": \"%s\"}\n]\n",
            image);
    logger.info("About to patch the domain resource {0} in namespace {1} with:{2}\n",
        domainResourceName, namespace, patch);

    assertTrue(patchDomainCustomResource(
            domainResourceName,
            namespace,
            new V1Patch(patch),
            V1Patch.PATCH_FORMAT_JSON_PATCH),
        String.format("Failed to patch the domain resource {0} in namespace {1} with image {2}",
            domainResourceName, namespace, image));
  }

  private String createImageAndVerify(
      String imageName,
      String imageTag,
      List<String> modelList,
      List<String> archiveList
  ) {
    String image = String.format("%s:%s", imageName, imageTag);

    // Set additional environment variables for WIT
    checkDirectory(WIT_BUILD_DIR);
    Map<String, String> env = new HashMap<>();
    env.put("WLSIMG_BLDDIR", WIT_BUILD_DIR);

    // For k8s 1.16 support and as of May 6, 2020, we presently need a different JDK for these
    // tests and for image tool. This is expected to no longer be necessary once JDK 11.0.8 or
    // the next JDK 14 versions are released.
    String witJavaHome = System.getenv("WIT_JAVA_HOME");
    if (witJavaHome != null) {
      env.put("JAVA_HOME", witJavaHome);
    }
 
    // build an image using WebLogic Image Tool
    logger.info("Create image {0} using model list {1} and archive list {2}",
        image, modelList, archiveList);
    boolean result = createImage(
        defaultWitParams()
            .modelImageName(imageName)
            .modelImageTag(imageTag)
            .modelFiles(modelList)
            .modelArchiveFiles(archiveList)
            .wdtModelOnly(true)
            .wdtVersion(WDT_VERSION)
            .env(env)
            .redirect(true));

    assertTrue(result, String.format("Failed to create image %s using WebLogic Image Tool", image));

    /* Check image exists using docker images | grep image tag.
     * Tag name is unique as it contains date and timestamp.
     * This is a workaround for the issue on Jenkins machine
     * as docker images imagename:imagetag is not working and
     * the test fails even though the image exists.
     */
    assertTrue(doesImageExist(imageTag),
        String.format("Image %s doesn't exist", image));

    return image;
  }


  private Domain createDomainResource(String domainUid, String domNamespace, String adminSecretName,
                                    String repoSecretName, String encryptionSecretName, int replicaCount,
                                      String miiImage) {
    // create the domain CR
    return new Domain()
        .apiVersion(DOMAIN_API_VERSION)
        .kind("Domain")
        .metadata(new V1ObjectMeta()
            .name(domainUid)
            .namespace(domNamespace))
        .spec(new DomainSpec()
            .domainUid(domainUid)
            .domainHomeSourceType("FromModel")
            .image(miiImage)
            .addImagePullSecretsItem(new V1LocalObjectReference()
                .name(repoSecretName))
            .webLogicCredentialsSecret(new V1SecretReference()
                .name(adminSecretName)
                .namespace(domNamespace))
            .includeServerOutInPodLog(true)
            .serverStartPolicy("IF_NEEDED")
            .serverPod(new ServerPod()
                .addEnvItem(new V1EnvVar()
                    .name("JAVA_OPTIONS")
                    .value("-Dweblogic.StdoutDebugEnabled=false"))
                .addEnvItem(new V1EnvVar()
                    .name("USER_MEM_ARGS")
                    .value("-Djava.security.egd=file:/dev/./urandom ")))
            .adminServer(new AdminServer()
                .serverStartState("RUNNING")
                .adminService(new AdminService()
                    .addChannelsItem(new Channel()
                        .channelName("default")
                        .nodePort(0))))
            .addClustersItem(new Cluster()
                .clusterName("cluster-1")
                .replicas(replicaCount)
                .serverStartState("RUNNING"))
            .configuration(new Configuration()
                .model(new Model()
                    .domainType("WLS")
                    .runtimeEncryptionSecret(encryptionSecretName))
                .introspectorJobActiveDeadlineSeconds(300L)));


  }


  private void patchAndVerify(
      final String domainUid,
      final String namespace,
      final String adminServerPodName,
      final String managedServerPrefix,
      final int replicaCount,
      final String image
  ) {
    logger.info(
        "Patch the domain resource {0} in namespace {1} to use the new image {2}",
        domainUid, namespace, image);

    patchDomainResourceImage(domainUid, namespace, image);
    
    logger.info(
        "Check that domain resource {0} in namespace {1} has been patched with image {2}",
        domainUid, namespace, image);
    checkDomainPatched(domainUid, namespace, image);

    // check and wait for the admin server pod to be patched with the new image
    logger.info(
        "Check that admin server pod for domain resource {0} in namespace {1} has been patched with image {2}",
        domainUid, namespace, image);

    checkPodImagePatched(
        domainUid,
        namespace,
        adminServerPodName,
        image);

    // check and wait for the managed server pods to be patched with the new image
    logger.info(
        "Check that server pods for domain resource {0} in namespace {1} have been patched with image {2}",
        domainUid, namespace, image);
    for (int i = 1; i <= replicaCount; i++) {
      checkPodImagePatched(
          domainUid,
          namespace,
          managedServerPrefix + i,
          image);
    }
  }

  private void checkAppRunning(
      String namespace,
      String podName,
      String internalPort,
      String appPath,
      String expectedStr
  ) {
   
    // check if the application is accessible inside of a server pod using standard retry policy
    checkAppIsRunning(withStandardRetryPolicy, namespace, podName, internalPort, appPath, expectedStr);
  }
  
  private void quickCheckAppRunning(
      String namespace,
      String podName,
      String internalPort,
      String appPath,
      String expectedStr
  ) {
    // check if the application is accessible inside of a server pod using quick retry policy
    checkAppIsRunning(withQuickRetryPolicy, namespace, podName, internalPort, appPath, expectedStr);
  }

  private void checkAppIsRunning(
      ConditionFactory conditionFactory,
      String namespace,
      String podName,
      String internalPort,
      String appPath,
      String expectedStr
  ) {
   
    // check if the application is accessible inside of a server pod
    conditionFactory
        .conditionEvaluationListener(
            condition -> logger.info("Waiting for application {0} is running on pod {1} in namespace {2} "
            + "(elapsed time {3}ms, remaining time {4}ms)",
            appPath,
            podName,
            namespace,
            condition.getElapsedTimeInMS(),
            condition.getRemainingTimeInMS()))
        .until(() -> appAccessibleInPod(
                namespace,
                podName, 
                internalPort, 
                appPath, 
                expectedStr));

  }
  
  private void quickCheckAppNotRunning(
      String namespace,
      String podName,
      String internalPort,
      String appPath,
      String expectedStr
  ) {
   
    // check that the application is NOT running inside of a server pod
    withQuickRetryPolicy
        .conditionEvaluationListener(
            condition -> logger.info("Checking if application {0} is not running on pod {1} in namespace {2} "
            + "(elapsed time {3}ms, remaining time {4}ms)",
            appPath,
            podName,
            namespace,
            condition.getElapsedTimeInMS(),
            condition.getRemainingTimeInMS()))
        .until(() -> appNotAccessibleInPod(
                namespace, 
                podName,
                internalPort, 
                appPath, 
                expectedStr));
  }
   
  private void checkDomainPatched(
      String domainUid,
      String namespace,
      String image 
  ) {
   
    // check if the domain resource has been patched with the given image
    withStandardRetryPolicy
        .conditionEvaluationListener(
            condition -> logger.info("Waiting for domain {0} to be patched in namespace {1} "
            + "(elapsed time {2}ms, remaining time {3}ms)",
            domainUid,
            namespace,
            condition.getElapsedTimeInMS(),
            condition.getRemainingTimeInMS()))
        .until(assertDoesNotThrow(() -> domainResourceImagePatched(domainUid, namespace, image),
            String.format(
               "Domain %s is not patched in namespace %s with image %s", domainUid, namespace, image)));

  }
  
  private void checkPodImagePatched(
      String domainUid,
      String namespace,
      String podName,
      String image
  ) {
   
    // check if the server pod has been patched with the given image
    withStandardRetryPolicy
        .conditionEvaluationListener(
            condition -> logger.info("Waiting for pod {0} to be patched in namespace {1} "
            + "(elapsed time {2}ms, remaining time {3}ms)",
            podName,
            namespace,
            condition.getElapsedTimeInMS(),
            condition.getRemainingTimeInMS()))
        .until(assertDoesNotThrow(() -> podImagePatched(domainUid, namespace, podName, "weblogic-server", image),
            String.format(
               "Pod %s is not patched with image %s in namespace %s.",
               podName,
               image,
               namespace)));
  }
  
  private static void collectAppAvaiability(
      String namespace,
      List<Integer> appAvailability,
      String managedServerPrefix,
      int replicaCount,
      String internalPort,
      String appPath
  ) {
    boolean v2AppAvailable = false;
    boolean failed = false;

    // Access the pod periodically to check application's availability across the duration
    // of patching the domain with newer version of the application.
    while (!v2AppAvailable && !failed)  {
      v2AppAvailable = true;
      for (int i = 1; i <= replicaCount; i++) {
        v2AppAvailable = v2AppAvailable && appAccessibleInPod(
                            namespace,
                            managedServerPrefix + i, 
                            internalPort, 
                            appPath, 
                            MII_APP_RESPONSE_V2 + i);
      }

      int count = 0;
      for (int i = 1; i <= replicaCount; i++) {
        if (appAccessibleInPod(
            namespace,
            managedServerPrefix + i, 
            internalPort, 
            appPath, 
            "Hello World")) {  
          count++;
        }
      }
      appAvailability.add(count);
      
      if (count == 0) {
        logger.info("XXXXXXXXXXX: application not available XXXXXXXX");
        failed = true;
      } else {
        logger.fine("YYYYYYYYYYY: application available YYYYYYYY count = " + count);   
      }
      try {
        TimeUnit.MILLISECONDS.sleep(200);
      } catch (InterruptedException ie) {
        // do nothing
      }
    }
  }
  
  private static boolean appAlwaysAvailable(List<Integer> appAvailability) {
    for (Integer count: appAvailability) {
      if (count == 0) {
        logger.warning("Application was not available during patching.");
        return false;
      }
    }
    return true;
  }

}
