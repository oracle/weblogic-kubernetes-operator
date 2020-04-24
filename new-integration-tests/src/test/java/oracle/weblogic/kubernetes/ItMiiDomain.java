// Copyright (c) 2020, Oracle Corporation and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.weblogic.kubernetes;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Base64;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.google.gson.JsonObject;
import io.kubernetes.client.openapi.ApiException;
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
import static oracle.weblogic.kubernetes.actions.ActionConstants.ARCHIVE_DIR;
import static oracle.weblogic.kubernetes.actions.ActionConstants.MODEL_DIR;
import static oracle.weblogic.kubernetes.actions.ActionConstants.WDT_VERSION;
import static oracle.weblogic.kubernetes.actions.ActionConstants.WIT_BUILD_DIR;
import static oracle.weblogic.kubernetes.actions.TestActions.buildAppArchive;
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
import static oracle.weblogic.kubernetes.actions.TestActions.helmList;
import static oracle.weblogic.kubernetes.actions.TestActions.installOperator;
import static oracle.weblogic.kubernetes.actions.TestActions.uninstallOperator;
import static oracle.weblogic.kubernetes.actions.TestActions.upgradeOperator;
import static oracle.weblogic.kubernetes.assertions.TestAssertions.dockerImageExists;
import static oracle.weblogic.kubernetes.assertions.TestAssertions.domainExists;
import static oracle.weblogic.kubernetes.assertions.TestAssertions.operatorIsRunning;
import static oracle.weblogic.kubernetes.assertions.TestAssertions.podExists;
import static oracle.weblogic.kubernetes.assertions.TestAssertions.podReady;
import static oracle.weblogic.kubernetes.assertions.TestAssertions.serviceExists;
import static oracle.weblogic.kubernetes.utils.FileUtils.checkDirectory;
import static org.awaitility.Awaitility.with;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

// Test to create model in image domain and verify the domain started successfully
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DisplayName("Test to create model in image domain and start the domain")
@IntegrationTest
class ItMiiDomain implements LoggedTest {

  // operator constants
  private static final String OPERATOR_RELEASE_NAME = "weblogic-operator";
  private static final String OPERATOR_CHART_DIR =
      "../kubernetes/charts/weblogic-operator";
  private static final String OPERATOR_IMAGE =
      "oracle/weblogic-kubernetes-operator:3.0.0";
  //"phx.ocir.io/weblogick8s/weblogic-kubernetes-operator:develop";

  // mii constants
  private static final String WDT_MODEL_FILE = "model1-wls.yaml";
  private static final String MII_IMAGE_NAME = "mii-image";
  private static final String APP_NAME = "sample-app";

  // domain constants
  private static final String DOMAIN_VERSION = "v7";
  private static final String API_VERSION = "weblogic.oracle/" + DOMAIN_VERSION;

  private static HelmParams opHelmParams = null;
  private static V1ServiceAccount serviceAccount = null;
  private String serviceAccountName = null;
  private static String opNamespace = null;
  private static String domainNamespace = null;
  private static String domainNamespace1 = null;
  private static String domainNamespace2 = null;
  private static ConditionFactory withStandardRetryPolicy = null;

  private String domainUID = "domain1";
  private String domainUID1 = "domain2";
  private String repoSecretName = "reposecret";
  private String miiImage = null;
  private static String repoRegistry = "dummy";
  private static String repoUserName = "dummy";
  private static String repoPassword = "dummy";
  private static String repoEmail = "dummy";
  private static String dockerConfigJson = null;

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

    // helm install parameters
    opHelmParams = new HelmParams()
        .releaseName(OPERATOR_RELEASE_NAME)
        .namespace(opNamespace)
        .chartDir(OPERATOR_CHART_DIR);

    // Operator chart values to override
    OperatorParams opParams =
        new OperatorParams()
            .helmParams(opHelmParams)
            .image(OPERATOR_IMAGE)
            .domainNamespaces(Arrays.asList(domainNamespace))
            .serviceAccount(serviceAccountName);

    // install Operator
    logger.info("Installing Operator in namespace {0}", opNamespace);
    assertTrue(installOperator(opParams),
        String.format("Operator install failed in namespace %s", opNamespace));
    logger.info("Operator installed in namespace {0}", opNamespace);

    // list helm releases
    logger.info("List helm releases in namespace {0}", opNamespace);
    helmList(opHelmParams);

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

    JsonObject dockerConfigJsonObject = getDockerConfigJson(
            repoUserName, repoPassword, repoEmail, repoRegistry);
    dockerConfigJson = dockerConfigJsonObject.toString();

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
    miiImage = createImageAndVerify();

    // push the image to OCIR to make the test work in multi node cluster
    if (System.getenv("REPO_REGISTRY") != null && System.getenv("REPO_USERNAME") != null
        && System.getenv("REPO_PASSWORD") != null && System.getenv("REPO_EMAIL") != null) {
      repoRegistry = System.getenv("REPO_REGISTRY");
      repoUserName = System.getenv("REPO_USERNAME");
      repoPassword = System.getenv("REPO_PASSWORD");
      repoEmail = System.getenv("REPO_EMAIL");

      logger.info("docker login");
      assertTrue(dockerLogin(repoRegistry, repoUserName, repoPassword), "docker login failed");

      logger.info("docker push image {0} to OCIR", miiImage);
      assertTrue(dockerPush(miiImage), String.format("docker push failed for image %s", miiImage));
    }

    // Create the repo secret to pull the image
    assertDoesNotThrow(() -> createRepoSecret(domainNamespace),
            String.format("createSecret failed for %s", repoSecretName));

    // create secret for admin credentials
    logger.info("Create secret for admin credentials");
    String adminSecretName = "weblogic-credentials";
    assertDoesNotThrow(() -> createDomainSecret(adminSecretName,"weblogic",
            "welcome1", domainNamespace),
            String.format("createSecret failed for %s", adminSecretName));

    // create encryption secret
    logger.info("Create encryption secret");
    String encryptionSecretName = "encryptionsecret";
    assertDoesNotThrow(() -> createDomainSecret(encryptionSecretName, "weblogicenc",
            "weblogicenc", domainNamespace),
             String.format("createSecret failed for %s", encryptionSecretName));

    // create the domain CR
    int adminNodePort = 30711;
    createDomainResource(domainUID, domainNamespace, adminSecretName, repoSecretName,
            encryptionSecretName, adminNodePort, replicaCount);

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
    checkPodCreated(adminServerPodName, domainUID, domainNamespace);

    // check managed server pods exists
    for (int i = 1; i <= replicaCount; i++) {
      logger.info("Check for managed server pod {0} existence in namespace {1}",
          managedServerPrefix + i, domainNamespace);
      checkPodCreated(managedServerPrefix + i, domainUID, domainNamespace);
    }

    // check admin server pod is running
    logger.info("Wait for admin server pod {0} to be ready in namespace {1}",
        adminServerPodName, domainNamespace);
    checkPodRunning(adminServerPodName, domainUID, domainNamespace);

    // check managed server pods are running
    for (int i = 1; i <= replicaCount; i++) {
      logger.info("Wait for managed server pod {0} to be ready in namespace {1}",
          managedServerPrefix + i, domainNamespace);
      checkPodRunning(managedServerPrefix + i, domainUID, domainNamespace);
    }

    logger.info("Check admin service {0} is created in namespace {1}",
        adminServerPodName, domainNamespace);
    checkServiceCreated(adminServerPodName, domainNamespace);

    // check managed server services created
    for (int i = 1; i <= replicaCount; i++) {
      logger.info("Check managed server service {0} is created in namespace {1}",
          managedServerPrefix + i, domainNamespace);
      checkServiceCreated(managedServerPrefix + i, domainNamespace);
    }

  }

  @Test
  @Order(2)
  @DisplayName("Create a second domain with the image from the the first test")
  @Slow
  @MustNotRunInParallel
  public void testCreateMiiSecondDomain() {
    // admin/managed server name here should match with model yaml in WDT_MODEL_FILE
    final String adminServerPodName = domainUID1 + "-admin-server";
    final String managedServerPrefix = domainUID1 + "-managed-server";
    final int replicaCount = 2;

    logger.info("Creating unique namespace for Domain domain2");
    domainNamespace1 = assertDoesNotThrow(() -> createUniqueNamespace(),
            "Failed to create unique namespace due to ApiException");
    logger.info("Created a new namespace called {0}", domainNamespace1);

    OperatorParams opParams =
            new OperatorParams()
                    .helmParams(opHelmParams)
                    .image(OPERATOR_IMAGE)
                    .domainNamespaces(Arrays.asList(domainNamespace,domainNamespace1))
                    .serviceAccount(serviceAccountName);

    // install Operator
    logger.info("Upgrading Operator in namespace {0}", opNamespace);
    assertTrue(upgradeOperator(opParams),
            String.format("Operator upgrade failed in namespace %s", opNamespace));
    logger.info("Operator upgraded in namespace {0}", opNamespace);

    // Create the repo secret to pull the image
    assertDoesNotThrow(() -> createRepoSecret(domainNamespace1),
            String.format("createSecret failed for %s", repoSecretName));

    // create secret for admin credentials
    logger.info("Create secret for admin credentials");
    String adminSecretName = domainUID1 + "-weblogic-credentials";
    assertDoesNotThrow(() -> createDomainSecret(adminSecretName,"weblogic",
            "welcome2", domainNamespace1),
            String.format("createSecret failed for %s", adminSecretName));

    // create encryption secret
    logger.info("Create encryption secret");
    String encryptionSecretName = "encryptionsecretdomain2";
    assertDoesNotThrow(() -> createDomainSecret(encryptionSecretName, "weblogicencdomain2",
            "weblogicencdomain2", domainNamespace1),
             String.format("createSecret failed for %s", encryptionSecretName));

    // create the domain CR
    int adminNodePort = 30712;
    createDomainResource(domainUID1, domainNamespace1, adminSecretName, repoSecretName,
            encryptionSecretName, adminNodePort, replicaCount);

    // wait for the domain to exist
    logger.info("Check for domain custom resouce in namespace {0}", domainNamespace1);
    withStandardRetryPolicy
        .conditionEvaluationListener(
            condition -> logger.info("Waiting for domain {0} to be created in namespace {1} "
                    + "(elapsed time {2}ms, remaining time {3}ms)",
                domainUID1,
                domainNamespace1,
                condition.getElapsedTimeInMS(),
                condition.getRemainingTimeInMS()))
        .until(domainExists(domainUID1, DOMAIN_VERSION, domainNamespace1));


    // check admin server pod exist
    logger.info("Check for admin server pod {0} existence in namespace {1}",
            adminServerPodName, domainNamespace1);
    checkPodCreated(adminServerPodName, domainUID1, domainNamespace1);

    // check managed server pods exists
    for (int i = 1; i <= replicaCount; i++) {
      logger.info("Check for managed server pod {0} existence in namespace {1}",
              managedServerPrefix + i, domainNamespace1);
      checkPodCreated(managedServerPrefix + i, domainUID1, domainNamespace1);
    }

    // check admin server pod is running
    logger.info("Wait for admin server pod {0} to be ready in namespace {1}",
            adminServerPodName, domainNamespace1);
    checkPodRunning(adminServerPodName, domainUID1, domainNamespace1);

    // check managed server pods are running
    for (int i = 1; i <= replicaCount; i++) {
      logger.info("Wait for managed server pod {0} to be ready in namespace {1}",
              managedServerPrefix + i, domainNamespace1);
      checkPodRunning(managedServerPrefix + i, domainUID1, domainNamespace1);
    }

    logger.info("Check admin service {0} is created in namespace {1}",
            adminServerPodName, domainNamespace1);
    checkServiceCreated(adminServerPodName, domainNamespace1);

    // check managed server services created
    for (int i = 1; i <= replicaCount; i++) {
      logger.info("Check managed server service {0} is created in namespace {1}",
              managedServerPrefix + i, domainNamespace1);
      checkServiceCreated(managedServerPrefix + i, domainNamespace1);
    }
  }

  @Test
  @Order(3)
  @DisplayName("Create a domain with same domainUID as first domain but in a new namespace")
  @Slow
  @MustNotRunInParallel
  public void testCreateMiiDomainSameDomainUIDDiffNS() {
    // admin/managed server name here should match with model yaml in WDT_MODEL_FILE
    final String adminServerPodName = domainUID + "-admin-server";
    final String managedServerPrefix = domainUID + "-managed-server";
    final int replicaCount = 2;

    // Create the repo secret to pull the image
    assertDoesNotThrow(() -> createRepoSecret(domainNamespace1),
            String.format("createSecret failed for %s", repoSecretName));

    // create secret for admin credentials
    logger.info("Create secret for admin credentials");
    String adminSecretName = domainUID + "-weblogic-credentials";
    assertDoesNotThrow(() -> createDomainSecret(adminSecretName,"weblogic",
            "welcome3", domainNamespace1),
            String.format("createSecret failed for %s", adminSecretName));

    // create encryption secret
    logger.info("Create encryption secret");
    String encryptionSecretName = "encryptionsecretdomain3";
    assertDoesNotThrow(() -> createDomainSecret(encryptionSecretName, "weblogicencdomain3",
            "weblogicencdomain3", domainNamespace1),
             String.format("createSecret failed for %s", encryptionSecretName));

    // create the domain CR
    int adminNodePort = 30714;
    createDomainResource(domainUID, domainNamespace1, adminSecretName, repoSecretName,
            encryptionSecretName, adminNodePort, replicaCount);

    // wait for the domain to exist
    logger.info("Check for domain custom resouce in namespace {0}", domainNamespace1);
    withStandardRetryPolicy
        .conditionEvaluationListener(
            condition -> logger.info("Waiting for domain {0} to be created in namespace {1} "
                   + "(elapsed time {2}ms, remaining time {3}ms)",
                domainUID,
                domainNamespace1,
                condition.getElapsedTimeInMS(),
                condition.getRemainingTimeInMS()))
        .until(domainExists(domainUID, DOMAIN_VERSION, domainNamespace1));


    // check admin server pod exist
    logger.info("Check for admin server pod {0} existence in namespace {1}",
            adminServerPodName, domainNamespace1);
    checkPodCreated(adminServerPodName, domainUID, domainNamespace1);

    // check managed server pods exists
    for (int i = 1; i <= replicaCount; i++) {
      logger.info("Check for managed server pod {0} existence in namespace {1}",
              managedServerPrefix + i, domainNamespace1);
      checkPodCreated(managedServerPrefix + i, domainUID, domainNamespace1);
    }

    // check admin server pod is running
    logger.info("Wait for admin server pod {0} to be ready in namespace {1}",
            adminServerPodName, domainNamespace1);
    checkPodRunning(adminServerPodName, domainUID, domainNamespace1);

    // check managed server pods are running
    for (int i = 1; i <= replicaCount; i++) {
      logger.info("Wait for managed server pod {0} to be ready in namespace {1}",
              managedServerPrefix + i, domainNamespace1);
      checkPodRunning(managedServerPrefix + i, domainUID, domainNamespace1);
    }

    logger.info("Check admin service {0} is created in namespace {1}",
            adminServerPodName, domainNamespace1);
    checkServiceCreated(adminServerPodName, domainNamespace1);

    // check managed server services created
    for (int i = 1; i <= replicaCount; i++) {
      logger.info("Check managed server service {0} is created in namespace {1}",
              managedServerPrefix + i, domainNamespace1);
      checkServiceCreated(managedServerPrefix + i, domainNamespace1);
    }
  }


  public void tearDown() {

    // Delete domain custom resource
    logger.info("Delete domain custom resource in namespace {0}", domainNamespace);
    assertDoesNotThrow(() -> deleteDomainCustomResource(domainUID, domainNamespace),
        "deleteDomainCustomResource failed with ApiException");
    logger.info("Deleted Domain Custom Resource " + domainUID + " from " + domainNamespace);

    logger.info("Delete domain custom resource in namespace {0}", domainNamespace1);
    assertDoesNotThrow(() -> deleteDomainCustomResource(domainUID1, domainNamespace1),
            "deleteDomainCustomResource failed with ApiException");
    logger.info("Deleted Domain Custom Resource " + domainUID1 + " from " + domainNamespace1);

    logger.info("Delete domain custom resource in namespace {0}", domainNamespace1);
    assertDoesNotThrow(() -> deleteDomainCustomResource(domainUID, domainNamespace1),
            "deleteDomainCustomResource failed with ApiException");
    logger.info("Deleted Domain Custom Resource " + domainUID + " from " + domainNamespace1);

    // delete the domain image created for the test
    if (miiImage != null) {
      deleteImage(miiImage);
    }

  }

  /**
   * Uninstall Operator, delete service account, domain namespace and
   * operator namespace.
   */
  @AfterAll
  public void tearDownAll() {
    tearDown();
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

    // Delete domain namespaces
    logger.info("Deleting domain namespace {0}", domainNamespace1);
    if (domainNamespace1 != null) {
      assertDoesNotThrow(() -> deleteNamespace(domainNamespace1),
              "deleteNamespace failed with ApiException");
      logger.info("Deleted namespace: " + domainNamespace1);
    }

    // Delete opNamespace
    logger.info("Deleting Operator namespace {0}", opNamespace);
    if (opNamespace != null) {
      assertDoesNotThrow(() -> deleteNamespace(opNamespace),
          "deleteNamespace failed with ApiException");
      logger.info("Deleted namespace: " + opNamespace);
    }

  }

  private String createImageAndVerify() {
    // create unique image name with date
    DateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd");
    Date date = new Date();
    final String imageTag = dateFormat.format(date) + "-" + System.currentTimeMillis();

    // build the model file list
    final List<String> modelList = Collections.singletonList(MODEL_DIR + "/" + WDT_MODEL_FILE);

    // build an application archive using what is in resources/apps/APP_NAME
    assertTrue(buildAppArchive(defaultAppParams()
        .srcDir(APP_NAME)), String.format("Failed to create app archive for %s", APP_NAME));

    // build the archive list
    String zipFile = String.format("%s/%s.zip", ARCHIVE_DIR, APP_NAME);
    final List<String> archiveList = Collections.singletonList(zipFile);

    // Set additional environment variables for WIT
    checkDirectory(WIT_BUILD_DIR);
    Map<String, String> env = new HashMap<>();
    env.put("WLSIMG_BLDDIR", WIT_BUILD_DIR);

    // build an image using WebLogic Image Tool
    logger.info("Create image {0}:{1} using model directory {2}",
        MII_IMAGE_NAME, imageTag, MODEL_DIR);
    boolean result = createMiiImage(
        defaultWitParams()
            .modelImageName(MII_IMAGE_NAME)
            .modelImageTag(imageTag)
            .modelFiles(modelList)
            .modelArchiveFiles(archiveList)
            .wdtVersion(WDT_VERSION)
            .env(env)
            .redirect(true));

    assertTrue(result, String.format("Failed to create the image %s using WebLogic Image Tool", MII_IMAGE_NAME));

    // check image exists
    assertTrue(dockerImageExists(MII_IMAGE_NAME, imageTag),
        String.format("Image %s doesn't exist", MII_IMAGE_NAME + ":" + imageTag));

    return MII_IMAGE_NAME + ":" + imageTag;
  }

  public void createRepoSecret(String domNamespace) throws ApiException {
    V1Secret repoSecret = new V1Secret()
            .metadata(new V1ObjectMeta()
                    .name(repoSecretName)
                    .namespace(domNamespace))
            .type("kubernetes.io/dockerconfigjson")
            .putDataItem(".dockerconfigjson", dockerConfigJson.getBytes());

    boolean secretCreated = false;
    try {
      secretCreated = createSecret(repoSecret);
    } catch (ApiException e) {
      System.err.println("Exception when calling CoreV1Api#createNamespacedSecret");
      System.err.println("Status code: " + e.getCode());
      System.err.println("Reason: " + e.getResponseBody());
      System.err.println("Response headers: " + e.getResponseHeaders());
      //409 means that the secret already exists - it is not an error, so can proceed
      if (e.getCode() != 409) {
        throw e;
      } else {
        secretCreated = true;
      }

    }
    assertTrue(secretCreated, String.format("create secret failed for %s", repoSecretName));
  }

  public void createDomainSecret(String secretName, String username, String password, String domNamespace)
          throws ApiException {
    Map<String, String> secretMap = new HashMap();
    secretMap.put("username", username);
    secretMap.put("password", password);
    boolean secretCreated = assertDoesNotThrow(() -> createSecret(new V1Secret()
            .metadata(new V1ObjectMeta()
                    .name(secretName)
                    .namespace(domNamespace))
            .stringData(secretMap)), "Create secret failed with ApiException");
    assertTrue(secretCreated, String.format("create secret failed for %s", secretName));

  }

  public void createDomainResource(String domainUID, String domNamespace, String adminSecretName,
                                   String repoSecretName, String encryptionSecretName,
                                   int adminNodePort, int replicaCount) {
    // create the domain CR
    Domain domain = new Domain()
            .apiVersion(API_VERSION)
            .kind("Domain")
            .metadata(new V1ObjectMeta()
                    .name(domainUID)
                    .namespace(domNamespace))
            .spec(new DomainSpec()
                    .domainUid(domainUID)
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
                                            .nodePort(adminNodePort))))
                    .addClustersItem(new Cluster()
                            .clusterName("cluster-1")
                            .replicas(replicaCount)
                            .serverStartState("RUNNING"))
                    .configuration(new Configuration()
                            .model(new Model()
                                    .domainType("WLS")
                                    .runtimeEncryptionSecret(encryptionSecretName))));

    logger.info("Create domain custom resource for domainUID {0} in namespace {1}",
            domainUID, domNamespace);
    assertTrue(assertDoesNotThrow(() -> createDomainCustomResource(domain),
            String.format("Create domain custom resource failed with ApiException for %s in namespace %s",
                    domainUID, domNamespace)),
            String.format("Create domain custom resource failed with ApiException for %s in namespace %s",
                    domainUID, domNamespace));
  }

  private void checkPodCreated(String podName, String domainUid, String domNamespace) {
    withStandardRetryPolicy
        .conditionEvaluationListener(
            condition -> logger.info("Waiting for pod {0} to be created in namespace {1} "
                    + "(elapsed time {2}ms, remaining time {3}ms)",
                podName,
                domNamespace,
                condition.getElapsedTimeInMS(),
                condition.getRemainingTimeInMS()))
        .until(assertDoesNotThrow(() -> podExists(podName, domainUid, domNamespace),
            String.format("podExists failed with ApiException for %s in namespace in %s",
                podName, domNamespace)));

  }

  private void checkPodRunning(String podName, String domainUid, String domNamespace) {
    withStandardRetryPolicy
        .conditionEvaluationListener(
            condition -> logger.info("Waiting for pod {0} to be ready in namespace {1} "
                    + "(elapsed time {2}ms, remaining time {3}ms)",
                podName,
                domNamespace,
                condition.getElapsedTimeInMS(),
                condition.getRemainingTimeInMS()))
        .until(assertDoesNotThrow(() -> podReady(podName, domainUid, domNamespace),
            String.format(
                "pod %s is not ready in namespace %s", podName, domNamespace)));

  }

  private void checkServiceCreated(String serviceName, String domNamespace) {
    withStandardRetryPolicy
        .conditionEvaluationListener(
            condition -> logger.info("Waiting for service {0} to be created in namespace {1} "
                    + "(elapsed time {2}ms, remaining time {3}ms)",
                serviceName,
                domNamespace,
                condition.getElapsedTimeInMS(),
                condition.getRemainingTimeInMS()))
        .until(assertDoesNotThrow(() -> serviceExists(serviceName, null, domNamespace),
            String.format(
                "Service %s is not ready in namespace %s", serviceName, domainNamespace)));

  }

  private static JsonObject getDockerConfigJson(String username, String password, String email, String registry) {
    JsonObject authObject = new JsonObject();
    authObject.addProperty("username", username);
    authObject.addProperty("password", password);
    authObject.addProperty("email", email);
    String auth = username + ":" + password;
    String authEncoded = Base64.getEncoder().encodeToString(auth.getBytes());
    System.out.println("auth encoded: " + authEncoded);
    authObject.addProperty("auth", authEncoded);
    JsonObject registryObject = new JsonObject();
    registryObject.add(registry, authObject);
    JsonObject configJsonObject = new JsonObject();
    configJsonObject.add("auths", registryObject);
    return configJsonObject;
  }

}
