// Copyright (c) 2020, Oracle Corporation and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.weblogic.kubernetes;

import java.io.IOException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import com.google.gson.JsonObject;
import io.kubernetes.client.custom.V1Patch;
import io.kubernetes.client.openapi.models.V1EnvVar;
import io.kubernetes.client.openapi.models.V1LocalObjectReference;
import io.kubernetes.client.openapi.models.V1ObjectMeta;
import io.kubernetes.client.openapi.models.V1Secret;
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
import oracle.weblogic.kubernetes.actions.impl.OperatorParams;
import oracle.weblogic.kubernetes.actions.impl.primitive.HelmParams;
import oracle.weblogic.kubernetes.annotations.IntegrationTest;
import oracle.weblogic.kubernetes.annotations.Namespaces;
import oracle.weblogic.kubernetes.annotations.tags.MustNotRunInParallel;
import oracle.weblogic.kubernetes.annotations.tags.Slow;
import oracle.weblogic.kubernetes.extensions.LoggedTest;
import org.awaitility.core.ConditionFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import static java.util.concurrent.TimeUnit.MINUTES;
import static java.util.concurrent.TimeUnit.SECONDS;
import static oracle.weblogic.kubernetes.TestConstants.OPERATOR_CHART_DIR;
import static oracle.weblogic.kubernetes.TestConstants.OPERATOR_RELEASE_NAME;
import static oracle.weblogic.kubernetes.TestConstants.REPO_DUMMY_VALUE;
import static oracle.weblogic.kubernetes.TestConstants.REPO_EMAIL;
import static oracle.weblogic.kubernetes.TestConstants.REPO_NAME;
import static oracle.weblogic.kubernetes.TestConstants.REPO_PASSWORD;
import static oracle.weblogic.kubernetes.TestConstants.REPO_REGISTRY;
import static oracle.weblogic.kubernetes.TestConstants.REPO_SECRET_NAME;
import static oracle.weblogic.kubernetes.TestConstants.REPO_USERNAME;
import static oracle.weblogic.kubernetes.actions.ActionConstants.ARCHIVE_DIR;
import static oracle.weblogic.kubernetes.actions.ActionConstants.DOWNLOAD_DIR;
import static oracle.weblogic.kubernetes.actions.ActionConstants.MODEL_DIR;
import static oracle.weblogic.kubernetes.actions.ActionConstants.WDT_VERSION;
import static oracle.weblogic.kubernetes.actions.ActionConstants.WIT_BUILD_DIR;
import static oracle.weblogic.kubernetes.actions.TestActions.buildAppArchive;
import static oracle.weblogic.kubernetes.actions.TestActions.createDockerConfigJson;
import static oracle.weblogic.kubernetes.actions.TestActions.createDomainCustomResource;
import static oracle.weblogic.kubernetes.actions.TestActions.createMiiImage;
import static oracle.weblogic.kubernetes.actions.TestActions.createSecret;
import static oracle.weblogic.kubernetes.actions.TestActions.createServiceAccount;
import static oracle.weblogic.kubernetes.actions.TestActions.defaultAppParams;
import static oracle.weblogic.kubernetes.actions.TestActions.defaultWitParams;
import static oracle.weblogic.kubernetes.actions.TestActions.deleteDomainCustomResource;
import static oracle.weblogic.kubernetes.actions.TestActions.deleteImage;
import static oracle.weblogic.kubernetes.actions.TestActions.deleteNamespace;
import static oracle.weblogic.kubernetes.actions.TestActions.deleteServiceAccount;
import static oracle.weblogic.kubernetes.actions.TestActions.dockerLogin;
import static oracle.weblogic.kubernetes.actions.TestActions.dockerPush;
import static oracle.weblogic.kubernetes.actions.TestActions.getOperatorImageName;
import static oracle.weblogic.kubernetes.actions.TestActions.installOperator;
import static oracle.weblogic.kubernetes.actions.TestActions.patchDomainCustomResource;
import static oracle.weblogic.kubernetes.actions.TestActions.uninstallOperator;
import static oracle.weblogic.kubernetes.assertions.TestAssertions.appAccessibleInPod;
import static oracle.weblogic.kubernetes.assertions.TestAssertions.appAccessibleInPodCallable;
import static oracle.weblogic.kubernetes.assertions.TestAssertions.doesImageExist;
import static oracle.weblogic.kubernetes.assertions.TestAssertions.domainExists;
import static oracle.weblogic.kubernetes.assertions.TestAssertions.isHelmReleaseDeployed;
import static oracle.weblogic.kubernetes.assertions.TestAssertions.operatorIsRunning;
import static oracle.weblogic.kubernetes.assertions.TestAssertions.podExists;
import static oracle.weblogic.kubernetes.assertions.TestAssertions.podReady;
import static oracle.weblogic.kubernetes.assertions.TestAssertions.serviceExists;
import static oracle.weblogic.kubernetes.utils.FileUtils.checkDirectory;
import static oracle.weblogic.kubernetes.utils.FileUtils.cleanupDirectory;
import static org.awaitility.Awaitility.with;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

// Test to create model in image domain and verify the domain started successfully
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DisplayName("Test to create model in image domain and start the domain")
@IntegrationTest
class ItMiiDomain implements LoggedTest {

  // mii constants
  private static final String WDT_MODEL_FILE = "model1-wls.yaml";
  private static final String MII_IMAGE_NAME = "mii-image";
  private static final String APP_NAME = "sample-app";

  // domain constants
  private static final String DOMAIN_VERSION = "v7";
  private static final String API_VERSION = "weblogic.oracle/" + DOMAIN_VERSION;
  
  // app constants
  private static final String APP_RESPONSE_V1 = "Hello World, you have reached server managed-server1";
  private static final String APP_RESPONSE_V2 = "Hello World AGAIN, you have reached server managed-server1";
  private static final String APP_RESPONSE_V3 = "How are you doing!";

  private static HelmParams opHelmParams = null;
  private static V1ServiceAccount serviceAccount = null;
  private static String opNamespace = null;
  private static String domainNamespace = null;
  private static ConditionFactory withStandardRetryPolicy = null;
  private static String dockerConfigJson = "";

  private String domainUID = "domain1";
  private String miiImagePatchAppV2 = null;
  private String miiImageAddSecondApp = null;
  private String miiImage = null;

  /**
   * Install Operator.
   * @param namespaces list of namespaces created by the IntegrationTestWatcher by the
   JUnit engine parameter resolution mechanism
   */
  @BeforeAll
  public static void initAll(@Namespaces(2) List<String> namespaces) {
    // create standard, reusable retry/backoff policy
    withStandardRetryPolicy = with().pollDelay(2, SECONDS)
        .and().with().pollInterval(10, SECONDS)
        .atMost(5, MINUTES).await();

    // get a new unique opNamespace
    logger.info("Creating unique namespace for Operator");
    assertNotNull(namespaces.get(0), "Namespace list is null");
    opNamespace = namespaces.get(0);

    logger.info("Creating unique namespace for Domain");
    assertNotNull(namespaces.get(1), "Namespace list is null");
    domainNamespace = namespaces.get(1);

    // Create a service account for the unique opNamespace
    logger.info("Creating service account");
    String serviceAccountName = opNamespace + "-sa";
    assertDoesNotThrow(() -> createServiceAccount(new V1ServiceAccount()
        .metadata(
            new V1ObjectMeta()
                .namespace(opNamespace)
                .name(serviceAccountName))));
    logger.info("Created service account: {0}", serviceAccountName);

    // get Operator image name
    String operatorImage = getOperatorImageName();
    assertFalse(operatorImage.isEmpty(), "Operator image name can not be empty");
    logger.info("Operator image name {0}", operatorImage);

    // Create docker registry secret in the operator namespace to pull the image from repository
    logger.info("Creating docker registry secret in namespace {0}", opNamespace);
    JsonObject dockerConfigJsonObject = createDockerConfigJson(
        REPO_USERNAME, REPO_PASSWORD, REPO_EMAIL, REPO_REGISTRY);
    dockerConfigJson = dockerConfigJsonObject.toString();

    // Create the V1Secret configuration
    V1Secret repoSecret = new V1Secret()
        .metadata(new V1ObjectMeta()
            .name(REPO_SECRET_NAME)
            .namespace(opNamespace))
        .type("kubernetes.io/dockerconfigjson")
        .putDataItem(".dockerconfigjson", dockerConfigJson.getBytes());

    boolean secretCreated = assertDoesNotThrow(() -> createSecret(repoSecret),
        String.format("createSecret failed for %s", REPO_SECRET_NAME));
    assertTrue(secretCreated, String.format("createSecret failed while creating secret %s in namespace",
                  REPO_SECRET_NAME, opNamespace));

    // map with secret
    Map<String, Object> secretNameMap = new HashMap<String, Object>();
    secretNameMap.put("name", REPO_SECRET_NAME);
    // helm install parameters
    opHelmParams = new HelmParams()
        .releaseName(OPERATOR_RELEASE_NAME)
        .namespace(opNamespace)
        .chartDir(OPERATOR_CHART_DIR);

    // Operator chart values to override
    OperatorParams opParams =
        new OperatorParams()
            .helmParams(opHelmParams)
            .image(operatorImage)
            .imagePullSecrets(secretNameMap)
            .domainNamespaces(Arrays.asList(domainNamespace))
            .serviceAccount(serviceAccountName);

    // install Operator
    logger.info("Installing Operator in namespace {0}", opNamespace);
    assertTrue(installOperator(opParams),
        String.format("Operator install failed in namespace %s", opNamespace));
    logger.info("Operator installed in namespace {0}", opNamespace);

    // list helm releases matching Operator release name in operator namespace
    logger.info("Checking Operator release {0} status in namespace {1}",
        OPERATOR_RELEASE_NAME, opNamespace);
    assertTrue(isHelmReleaseDeployed(OPERATOR_RELEASE_NAME, opNamespace),
        String.format("Operator release %s is not in deployed status in namespace %s",
            OPERATOR_RELEASE_NAME, opNamespace));
    logger.info("Operator release {0} status is deployed in namespace {1}",
        OPERATOR_RELEASE_NAME, opNamespace);

    // check operator is running
    logger.info("Check Operator pod is running in namespace {0}", opNamespace);
    withStandardRetryPolicy
        .conditionEvaluationListener(
            condition -> logger.info("Waiting for operator to be running in namespace {0} "
                    + "(elapsed time {1}ms, remaining time {2}ms)",
                opNamespace,
                condition.getElapsedTimeInMS(),
                condition.getRemainingTimeInMS()))
        .until(operatorIsRunning(opNamespace));

  }

  @Test
  @Order(1)
  @DisplayName("Create model in image domain")
  @Slow
  @MustNotRunInParallel
  public void testCreateMiiDomain() {
    // admin/managed server name here should match with model yaml in WDT_MODEL_FILE
    final String adminServerPodName = domainUID + "-admin-server";
    final String managedServerPrefix = domainUID + "-managed-server";
    final int replicaCount = 2;

    // create image with model files
    miiImage = createInitialDomainImage();

    // push the image to OCIR to make the test work in multi node cluster
    pushImageIfNeeded(miiImage);

    // Create the V1Secret configuration
    V1Secret repoSecret = new V1Secret()
        .metadata(new V1ObjectMeta()
            .name(REPO_SECRET_NAME)
            .namespace(domainNamespace))
        .type("kubernetes.io/dockerconfigjson")
        .putDataItem(".dockerconfigjson", dockerConfigJson.getBytes());

    boolean secretCreated = assertDoesNotThrow(() -> createSecret(repoSecret),
        String.format("createSecret failed for %s", REPO_SECRET_NAME));
    assertTrue(secretCreated, String.format("createSecret failed while creating secret %s", REPO_SECRET_NAME));

    // create secret for admin credentials
    logger.info("Create secret for admin credentials");
    String adminSecretName = "weblogic-credentials";
    Map<String, String> adminSecretMap = new HashMap();
    adminSecretMap.put("username", "weblogic");
    adminSecretMap.put("password", "welcome1");
    secretCreated = assertDoesNotThrow(() -> createSecret(new V1Secret()
        .metadata(new V1ObjectMeta()
            .name(adminSecretName)
            .namespace(domainNamespace))
        .stringData(adminSecretMap)), "Create secret failed with ApiException");
    assertTrue(secretCreated, String.format("create secret failed for %s", adminSecretName));

    // create encryption secret
    logger.info("Create encryption secret");
    String encryptionSecretName = "encryptionsecret";
    Map<String, String> encryptionSecretMap = new HashMap();
    encryptionSecretMap.put("username", "weblogicenc");
    encryptionSecretMap.put("password", "weblogicenc");
    secretCreated = assertDoesNotThrow(() -> createSecret(new V1Secret()
        .metadata(new V1ObjectMeta()
            .name(encryptionSecretName)
            .namespace(domainNamespace))
        .stringData(encryptionSecretMap)), "Create secret failed with ApiException");
    assertTrue(secretCreated, String.format("create secret failed for %s", encryptionSecretName));

    // create the domain CR
    Domain domain = new Domain()
        .apiVersion(API_VERSION)
        .kind("Domain")
        .metadata(new V1ObjectMeta()
            .name(domainUID)
            .namespace(domainNamespace))
        .spec(new DomainSpec()
            .domainUid(domainUID)
            .domainHomeSourceType("FromModel")
            .image(miiImage)
            .addImagePullSecretsItem(new V1LocalObjectReference()
                .name(REPO_SECRET_NAME))
            .webLogicCredentialsSecret(new V1SecretReference()
                .name(adminSecretName)
                .namespace(domainNamespace))
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
                    .runtimeEncryptionSecret(encryptionSecretName))));

    logger.info("Create domain custom resource for domainUID {0} in namespace {1}",
        domainUID, domainNamespace);
    assertTrue(assertDoesNotThrow(() -> createDomainCustomResource(domain),
        String.format("Create domain custom resource failed with ApiException for %s in namespace %s",
            domainUID, domainNamespace)),
        String.format("Create domain custom resource failed with ApiException for %s in namespace %s",
            domainUID, domainNamespace));


    // wait for the domain to exist
    logger.info("Check for domain custom resouce in namespace {0}", domainNamespace);
    withStandardRetryPolicy
        .conditionEvaluationListener(
            condition -> logger.info("Waiting for domain {0} to be created in namespace {1} "
                    + "(elapsed time {2}ms, remaining time {3}ms)",
                domainUID,
                domainNamespace,
                condition.getElapsedTimeInMS(),
                condition.getRemainingTimeInMS()))
        .until(domainExists(domainUID, DOMAIN_VERSION, domainNamespace));


    // check admin server pod exist
    logger.info("Check for admin server pod {0} existence in namespace {1}",
        adminServerPodName, domainNamespace);
    checkPodCreated(adminServerPodName);

    // check managed server pods exists
    for (int i = 1; i <= replicaCount; i++) {
      logger.info("Check for managed server pod {0} existence in namespace {1}",
          managedServerPrefix + i, domainNamespace);
      checkPodCreated(managedServerPrefix + i);
    }

    // check admin server pod is running
    logger.info("Wait for admin server pod {0} to be ready in namespace {1}",
        adminServerPodName, domainNamespace);
    checkPodRunning(adminServerPodName);

    // check managed server pods are running
    for (int i = 1; i <= replicaCount; i++) {
      logger.info("Wait for managed server pod {0} to be ready in namespace {1}",
          managedServerPrefix + i, domainNamespace);
      checkPodRunning(managedServerPrefix + i);
    }

    logger.info("Check admin service {0} is created in namespace {1}",
        adminServerPodName, domainNamespace);
    checkServiceCreated(adminServerPodName);

    // check managed server services created
    for (int i = 1; i <= replicaCount; i++) {
      logger.info("Check managed server service {0} is created in namespace {1}",
          managedServerPrefix + i, domainNamespace);
      checkServiceCreated(managedServerPrefix + i);
    }
    
    checkAppRunning(
        domainUID,
        domainNamespace,
        "8001",
        "sample-war/index.jsp",
        APP_RESPONSE_V1);
    
    logger.info(String.format("Domain %s is fully started - servers are running and application is deployed corretly.",
        domainUID));
  }
  
  @Test
  @Order(4)
  @DisplayName("Update the sample-app application to version 2")
  @Slow
  @MustNotRunInParallel
  public void testPatchAppV2() {
    
    // app here is what is in the original app dir plus the replacement in the second app dir
    final String appDir1 = "sample-app";
    final String appDir2 = "sample-app-2";
    
    // check and V1 app is running
    assertTrue(appAccessibleInPod(
            domainUID,
            domainNamespace,
            "8001",
            "sample-war/index.jsp",
            APP_RESPONSE_V1),
        "The expected app is not accessible inside the server pod");
    
    // check and make sure that the version 2 app is NOT running
    assertFalse(appAccessibleInPod(
            domainUID,
            domainNamespace,
            "8001",
            "sample-war/index.jsp",
            APP_RESPONSE_V2),
        "The second version of the app is not supposed to be running!!");   
   
    // create another image with app V2 
    miiImagePatchAppV2 = updateImageWithAppV2Patch(
        MII_IMAGE_NAME,
        "testPatchAppV2",
        Arrays.asList(appDir1, appDir2));

    // push the image to OCIR to make the test work in multi node cluster
    pushImageIfNeeded(miiImagePatchAppV2);

    // modify the domain resource to use the new image
    patchDomainResourceIamge(domainUID, domainNamespace, miiImagePatchAppV2);
    
    logger.info("Sleep for 2 minutes and wait for the servers to be rolling-restarted");
    
    // Ideally we want to verify that the server pods were rolling restarted.
    // But it is hard to time the pod state transitions.
    // Instead, sleep for 2 minutes and check the newer version of the application. 
    // The application check is sufficient to verify that the version2 application
    // is running, thus the servers have been patched.
    try {
      TimeUnit.MINUTES.sleep(2);
    } catch (InterruptedException ie) {
      // do nothing
    }
    
    // check and wait for the app to be ready
    checkAppRunning(
        domainUID,
        domainNamespace,
        "8001",
        "sample-war/index.jsp",
        APP_RESPONSE_V2);

    logger.info("The cluster has been rolling started, and the version 2 application has been deployed correctly.");
  }

  @Test
  @Order(5)
  @DisplayName("Update the domain with another application")
  @Slow
  @MustNotRunInParallel
  public void testAddSecondApp() {
    
    // app here is what is in the original app dir plus the delta in the second app dir
    final String appDir1 = "sample-app";
    final String appDir2 = "sample-app-2";
    final String appDir3 = "sample-app-3";

    // check and V2 app is still running
    assertTrue(appAccessibleInPod(
            domainUID,
            domainNamespace,
            "8001",
            "sample-war/index.jsp",
            APP_RESPONSE_V2),
        "The expected app is not accessible inside the server pod");
    logger.info("App version 2 is still running");
    
    // check and make sure that the new app is not already running
    assertFalse(appAccessibleInPod(
            domainUID,
            domainNamespace,
            "8001",
            "sample-war-3/index.jsp",
            APP_RESPONSE_V3),
        "The second app is not supposed to be running!!");
    
    logger.info("About to patch the domain with new image");
    
    // create another image with an additional app
    miiImageAddSecondApp = updateImageWithApp3(
        MII_IMAGE_NAME,
        "testAddSecondApp",
        Arrays.asList(appDir1, appDir2),
        Collections.singletonList(appDir3),
        "model2-wls.yaml");
    
    logger.info("Image is successfully created");  
  
    // push the image to OCIR to make the test work in multi node cluster
    pushImageIfNeeded(miiImageAddSecondApp);

    // modify the domain resource to use the new image
    patchDomainResourceIamge(domainUID, domainNamespace, miiImageAddSecondApp);
    
    logger.info("Sleep for 2 minutes and wait for the servers to be rolling-restarted");
    
    // Ideally we want to verify that the server pods were rolling restarted.
    // But it is hard to time the pod state transitions.
    // Instead, sleep for 2 minutes and check the newer version of the application. 
    // The application check is sufficient to verify that the version2 application
    // is running, thus the servers have been patched.
    try {
      TimeUnit.MINUTES.sleep(2);
    } catch (InterruptedException ie) {
      // do nothing
    }
    
    // check and wait for the original app to be ready
    checkAppRunning(
        domainUID,
        domainNamespace,
        "8001",
        "sample-war/index.jsp",
        APP_RESPONSE_V2);
    
    // check and wait for the new app to be ready
    checkAppRunning(
        domainUID,
        domainNamespace,
        "8001",
        "sample-war-3/index.jsp",
        APP_RESPONSE_V3);

    logger.info("The cluster has been rolling started, and the two applications are both running correctly.");
  }

  /**
   * Uninstall Operator, delete service account, domain namespace and
   * operator namespace.
   */
  @AfterAll
  public void tearDownAll() {
    // Delete domain custom resource
    logger.info("Delete domain custom resource in namespace {0}", domainNamespace);
    assertDoesNotThrow(() -> deleteDomainCustomResource(domainUID, domainNamespace),
        "deleteDomainCustomResource failed with ApiException");
    logger.info("Deleted Domain Custom Resource " + domainUID + " from " + domainNamespace);

    // uninstall operator release
    logger.info("Uninstall Operator in namespace {0}", opNamespace);
    if (opHelmParams != null) {
      uninstallOperator(opHelmParams);
    }
    // Delete service account from unique opNamespace
    logger.info("Delete service account in namespace {0}", opNamespace);
    if (serviceAccount != null) {
      assertDoesNotThrow(() -> deleteServiceAccount(serviceAccount.getMetadata().getName(),
              serviceAccount.getMetadata().getNamespace()),
              "deleteServiceAccount failed with ApiException");
    }
    // Delete domain namespaces
    logger.info("Deleting domain namespace {0}", domainNamespace);
    if (domainNamespace != null) {
      assertDoesNotThrow(() -> deleteNamespace(domainNamespace),
          "deleteNamespace failed with ApiException");
      logger.info("Deleted namespace: " + domainNamespace);
    }

    // Delete opNamespace
    logger.info("Deleting Operator namespace {0}", opNamespace);
    if (opNamespace != null) {
      assertDoesNotThrow(() -> deleteNamespace(opNamespace),
          "deleteNamespace failed with ApiException");
      logger.info("Deleted namespace: " + opNamespace);
    }

    // delete the domain images created in the test class
    if (miiImage != null) {
      deleteImage(miiImage);
    }
    if (miiImagePatchAppV2 != null) {
      deleteImage(miiImagePatchAppV2);
    }
    if (miiImageAddSecondApp != null) {
      deleteImage(miiImageAddSecondApp);
    }

    // clean up the download directory so that we always get the latest
    // versions of the tools
    try {
      cleanupDirectory(DOWNLOAD_DIR);
    } catch (IOException | RuntimeException e) {    
      logger.severe("Failed to cleanup the download directory " + DOWNLOAD_DIR + " ready", e);    
    }
  }

  private void pushImageIfNeeded(String image) {
    // push the image to OCIR to make the test work in multi node cluster
    if (!REPO_USERNAME.equals(REPO_DUMMY_VALUE)) {
      logger.info("docker login");
      assertTrue(dockerLogin(REPO_REGISTRY, REPO_USERNAME, REPO_PASSWORD), "docker login failed");

      logger.info("docker push image {0} to OCIR", image);
      assertTrue(dockerPush(miiImage), String.format("docker push failed for image %s", image));
    }
  }

  private String createInitialDomainImage() {
    // create unique image name with date
    DateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd");
    Date date = new Date();
    final String imageTag = dateFormat.format(date) + "-" + System.currentTimeMillis();
    // Add repository name in image name for Jenkins runs
    final String imageName = REPO_USERNAME.equals(REPO_DUMMY_VALUE) ? MII_IMAGE_NAME : REPO_NAME + MII_IMAGE_NAME;
    final String image = imageName + ":" + imageTag;

    // build the model file list
    final List<String> modelList = 
        Collections.singletonList(String.format("%s/%s", MODEL_DIR, WDT_MODEL_FILE));

    // build an application archive using what is in resources/apps/APP_NAME
    assertTrue(buildAppArchive(defaultAppParams()
        .srcDirList(Collections.singletonList(APP_NAME))), 
        String.format("Failed to create app archive for %s", APP_NAME));

    // build the archive list
    List<String> archiveList = 
        Collections.singletonList(String.format("%s/%s.zip", ARCHIVE_DIR, APP_NAME));

    createImageAndVerify(imageName, imageTag, modelList, archiveList);

    return image;
  }
  
  private String updateImageWithAppV2Patch(
      String imageName,
      List<String> appDirList
  ) {
    DateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd");
    Date date = new Date();
    final String imageTag = dateFormat.format(date) + "-" + System.currentTimeMillis();
    // Add repository name in image name for Jenkins runs
    final String imageNameReal = REPO_USERNAME.equals(REPO_DUMMY_VALUE) ? imageName : REPO_NAME + imageName;
    String image = String.format("%s:%s",  imageNameReal, imageTag);
    
    // build the model file list
    List<String> modelList = 
        Collections.singletonList(String.format("%s/%s", MODEL_DIR, WDT_MODEL_FILE));
   
    // build an application archive using what is in resources/apps/APP_NAME
    assertTrue(
        buildAppArchive(
            defaultAppParams()
                .srcDirList(appDirList)),
        String.format("Failed to create app archive for %s",
            APP_NAME));

    // build the archive list
    List<String> archiveList = 
        Collections.singletonList(
            String.format("%s/%s.zip", ARCHIVE_DIR, APP_NAME));
    
    createImageAndVerify(imageNameReal, imageTag, modelList, archiveList);
    
    return image;
  }

  private String updateImageWithApp3(
      String imageName,
      List<String> appDirList1,
      List<String> appDirList2,
      String modelFile
  ) {
    DateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd");
    Date date = new Date();
    final String imageTag = dateFormat.format(date) + "-" + System.currentTimeMillis();
    // Add repository name in image name for Jenkins runs
    final String imageNameReal = REPO_USERNAME.equals(REPO_DUMMY_VALUE) ? imageName : REPO_NAME + imageName;
    String image = String.format("%s:%s",  imageNameReal, imageTag);
    
    // build the model file list
    List<String> modelList = Collections.singletonList(MODEL_DIR + "/" + modelFile);
 
    String appName1 = appDirList1.get(0);
    String appName2 = appDirList2.get(0);
    
    // build an application archive using for the first app, which is the same after
    // patching the original app.
    assertTrue(
        buildAppArchive(
            defaultAppParams()
                .srcDirList(appDirList1)
                .appName(appName1)),
        String.format("Failed to create app archive for %s",
            appName1));
    
    logger.info("Successfully created app zip file: " + appName1);
        
    assertTrue(
        buildAppArchive(
            defaultAppParams()
                .srcDirList(appDirList2)
                .appName(appName2)),
        String.format("Failed to create app archive for %s",
            appName2));
    
    logger.info("Successfully cteated app zip file: " + appName2); 
    
    // build the archive list with two zip files
    List<String> archiveList = Arrays.asList(
        String.format("%s/%s.zip", ARCHIVE_DIR, appName1),
        String.format("%s/%s.zip", ARCHIVE_DIR, appName2));
    
    createImageAndVerify(imageNameReal, imageTag, modelList, archiveList);
    
    return image;
  }

  /**
   * Patch the domain resource with a new image that contains a newer version of the application.
   * Here is an example of the JSON patch string that is constructed in this method.
   * [
   *   {"op": "replace", "path": "/spec/image", "value": "mii-image:v2" }
   * ]
   * 
   * @param domainUID the unique identifier of the domain resource
   * @param namespace the Kubernetes namespace that the domain is hosted
   * @param image the name of the image that contains a newer version of the application
   */
  private void patchDomainResourceIamge(
      String domainUID,
      String namespace,
      String image
  ) {
    String patch = 
        String.format("[\n  {\"op\": \"replace\", \"path\": \"/spec/image\", \"value\": \"%s\"}\n]\n",
            image);
    logger.info("About to patch the domain resource with:\n" + patch);

    assertTrue(patchDomainCustomResource(
            domainUID,
            namespace,
            new V1Patch(patch),
            V1Patch.PATCH_FORMAT_JSON_PATCH),
        "Failed to patch the domain resource with a  a different image.");
  }

  private void createImageAndVerify(
      String imageName,
      String imageTag,
      List<String> modelList,
      List<String> archiveList
  ) {
    final String image = imageName + ":" + imageTag;

    // Set additional environment variables for WIT
    checkDirectory(WIT_BUILD_DIR);
    Map<String, String> env = new HashMap<>();
    env.put("WLSIMG_BLDDIR", WIT_BUILD_DIR);

    // build an image using WebLogic Image Tool
    logger.info("Create image {0}:{1} using model directory {2}",
        imageName, imageTag, MODEL_DIR);
    boolean result = createMiiImage(
        defaultWitParams()
            .modelImageName(imageName)
            .modelImageTag(imageTag)
            .modelFiles(modelList)
            .modelArchiveFiles(archiveList)
            .wdtVersion(WDT_VERSION)
            .env(env)
            .redirect(true));

    assertTrue(result, String.format("Failed to create the image %s using WebLogic Image Tool",  image));

    /* Check image exists using docker images | grep image tag.
     * Tag name is unique as it contains date and timestamp.
     * This is a workaround for the issue on Jenkins machine
     * as docker images imagename:imagetag is not working and
     * the test fails even though the image exists.
     */
    assertTrue(doesImageExist(imageTag),
        String.format("Image %s doesn't exist", image));
  }

  private void checkPodCreated(String podName) {
    withStandardRetryPolicy
        .conditionEvaluationListener(
            condition -> logger.info("Waiting for pod {0} to be created in namespace {1} "
                    + "(elapsed time {2}ms, remaining time {3}ms)",
                podName,
                domainNamespace,
                condition.getElapsedTimeInMS(),
                condition.getRemainingTimeInMS()))
        .until(assertDoesNotThrow(() -> podExists(podName, domainUID, domainNamespace),
            String.format("podExists failed with ApiException for %s in namespace in %s",
                podName, domainNamespace)));

  }

  private void checkPodRunning(String podName) {
    withStandardRetryPolicy
        .conditionEvaluationListener(
            condition -> logger.info("Waiting for pod {0} to be ready in namespace {1} "
                    + "(elapsed time {2}ms, remaining time {3}ms)",
                podName,
                domainNamespace,
                condition.getElapsedTimeInMS(),
                condition.getRemainingTimeInMS()))
        .until(assertDoesNotThrow(() -> podReady(podName, domainUID, domainNamespace),
            String.format(
                "pod %s is not ready in namespace %s", podName, domainNamespace)));

  }

  private void checkServiceCreated(String serviceName) {
    withStandardRetryPolicy
        .conditionEvaluationListener(
            condition -> logger.info("Waiting for service {0} to be created in namespace {1} "
                    + "(elapsed time {2}ms, remaining time {3}ms)",
                serviceName,
                domainNamespace,
                condition.getElapsedTimeInMS(),
                condition.getRemainingTimeInMS()))
        .until(assertDoesNotThrow(() -> serviceExists(serviceName, null, domainNamespace),
            String.format(
                "Service %s is not ready in namespace %s", serviceName, domainNamespace)));

  }

  private void checkAppRunning(
      String domainUID,
      String ns,
      String internalPort,
      String appPath, 
      String expectedStr
  ) {
   
    // check if the app is accessible inside of a server pod
    withStandardRetryPolicy
        .conditionEvaluationListener(
            condition -> logger.info("Waiting for application {0} to be ready in namespace {1} "
            + "(elapsed time {2}ms, remaining time {3}ms)",
            appPath,
            domainNamespace,
            condition.getElapsedTimeInMS(),
            condition.getRemainingTimeInMS()))
        .until(assertDoesNotThrow(() -> appAccessibleInPodCallable(domainUID, ns, internalPort, appPath, expectedStr),
            String.format(
               "App %s is not ready in namespace %s", appPath, domainNamespace)));

  }

}
