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

import com.google.gson.JsonObject;
import io.kubernetes.client.custom.V1Patch;
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
import static oracle.weblogic.kubernetes.actions.TestActions.upgradeOperator;
import static oracle.weblogic.kubernetes.assertions.TestAssertions.appAccessibleInPod;
import static oracle.weblogic.kubernetes.assertions.TestAssertions.appNotAccessibleInPod;
import static oracle.weblogic.kubernetes.assertions.TestAssertions.doesImageExist;
import static oracle.weblogic.kubernetes.assertions.TestAssertions.domainExists;
import static oracle.weblogic.kubernetes.assertions.TestAssertions.domainResourceImagePatched;
import static oracle.weblogic.kubernetes.assertions.TestAssertions.isHelmReleaseDeployed;
import static oracle.weblogic.kubernetes.assertions.TestAssertions.operatorIsRunning;
import static oracle.weblogic.kubernetes.assertions.TestAssertions.podExists;
import static oracle.weblogic.kubernetes.assertions.TestAssertions.podImagePatched;
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
  private static final String APP_RESPONSE_V1 = "Hello World, you have reached server managed-server";
  private static final String APP_RESPONSE_V2 = "Hello World AGAIN, you have reached server managed-server";
  private static final String APP_RESPONSE_V3 = "How are you doing!";

  private static HelmParams opHelmParams = null;
  private static V1ServiceAccount serviceAccount = null;
  private String serviceAccountName = null;
  private static String opNamespace = null;
  private static String operatorImage = null;
  private static String domainNamespace = null;
  private static String domainNamespace1 = null;
  private static String domainNamespace2 = null;
  private static ConditionFactory withStandardRetryPolicy = null;
  private static ConditionFactory withQuickRetryPolicy = null;
  private static String dockerConfigJson = "";

  private String domainUid = "domain1";
  private String domainUid1 = "domain2";
  private String miiImagePatchAppV2 = null;
  private String miiImageAddSecondApp = null;
  private String miiImage = null;

  /**
   * Install Operator.
   * @param namespaces list of namespaces created by the IntegrationTestWatcher by the
   JUnit engine parameter resolution mechanism
   */
  @BeforeAll
  public static void initAll(@Namespaces(3) List<String> namespaces) {
    // create standard, reusable retry/backoff policy
    withStandardRetryPolicy = with().pollDelay(2, SECONDS)
        .and().with().pollInterval(10, SECONDS)
        .atMost(6, MINUTES).await();

    withQuickRetryPolicy = with().pollDelay(1, SECONDS)
        .and().with().pollInterval(2, SECONDS)
        .atMost(5, SECONDS).await();

    // get a new unique opNamespace
    logger.info("Creating unique namespace for Operator");
    assertNotNull(namespaces.get(0), "Namespace list is null");
    opNamespace = namespaces.get(0);

    logger.info("Creating unique namespace for Domain");
    assertNotNull(namespaces.get(1), "Namespace list is null");
    domainNamespace = namespaces.get(1);
    assertNotNull(namespaces.get(2), "Namespace list is null");
    domainNamespace1 = namespaces.get(2);

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
    operatorImage = getOperatorImageName();
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
    final String adminServerPodName = domainUid + "-admin-server";
    final String managedServerPrefix = domainUid + "-managed-server";
    final int replicaCount = 2;

    // create image with model files
    miiImage = createInitialDomainImage();

    // push the image to OCIR to make the test work in multi node cluster
    pushImageIfNeeded(miiImage);

    // Create the repo secret to pull the image
    assertDoesNotThrow(() -> createRepoSecret(domainNamespace),
            String.format("createSecret failed for %s", REPO_SECRET_NAME));

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
    createDomainResource(domainUid, domainNamespace, adminSecretName, REPO_SECRET_NAME,
              encryptionSecretName, replicaCount);

    // wait for the domain to exist
    logger.info("Check for domain custom resource in namespace {0}", domainNamespace);
    withStandardRetryPolicy
        .conditionEvaluationListener(
            condition -> logger.info("Waiting for domain {0} to be created in namespace {1} "
                    + "(elapsed time {2}ms, remaining time {3}ms)",
                domainUid,
                domainNamespace,
                condition.getElapsedTimeInMS(),
                condition.getRemainingTimeInMS()))
        .until(domainExists(domainUid, DOMAIN_VERSION, domainNamespace));


    // check admin server pod exists
    logger.info("Check for admin server pod {0} existence in namespace {1}",
        adminServerPodName, domainNamespace);
    checkPodCreated(adminServerPodName, domainUid, domainNamespace);

    // check managed server pods exist
    for (int i = 1; i <= replicaCount; i++) {
      logger.info("Check for managed server pod {0} existence in namespace {1}",
          managedServerPrefix + i, domainNamespace);
      checkPodCreated(managedServerPrefix + i, domainUid, domainNamespace);
    }

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
    checkServiceCreated(adminServerPodName, domainNamespace);

    // check managed server services created
    for (int i = 1; i <= replicaCount; i++) {
      logger.info("Check managed server service {0} is created in namespace {1}",
          managedServerPrefix + i, domainNamespace);
      checkServiceCreated(managedServerPrefix + i, domainNamespace);
    }
    
    for (int i = 1; i <= replicaCount; i++) {
      checkAppRunning(
          domainUid,
          domainNamespace,
          managedServerPrefix + i,
          "8001",
          "sample-war/index.jsp",
          APP_RESPONSE_V1 + i);
    }
 
    logger.info(String.format("Domain %s is fully started - servers are running and application is deployed corretly.",
        domainUid));
  }
  
  @Test
  @Order(2)
  @DisplayName("Create a second domain with the image from the the first test")
  @Slow
  @MustNotRunInParallel
  public void testCreateMiiSecondDomainDiffNSSameImage() {
    // admin/managed server name here should match with model yaml in WDT_MODEL_FILE
    final String adminServerPodName = domainUid1 + "-admin-server";
    final String managedServerPrefix = domainUid1 + "-managed-server";
    final int replicaCount = 2;

    OperatorParams opParams =
            new OperatorParams()
                    .helmParams(opHelmParams)
                    .image(operatorImage)
                    .domainNamespaces(Arrays.asList(domainNamespace,domainNamespace1))
                    .serviceAccount(serviceAccountName);

    // upgrade Operator
    logger.info("Upgrading Operator in namespace {0}", opNamespace);
    assertTrue(upgradeOperator(opParams),
            String.format("Operator upgrade failed in namespace %s", opNamespace));
    logger.info("Operator upgraded in namespace {0}", opNamespace);

    // Create the repo secret to pull the image
    assertDoesNotThrow(() -> createRepoSecret(domainNamespace1),
              String.format("createSecret failed for %s", REPO_SECRET_NAME));

    // create secret for admin credentials
    logger.info("Create secret for admin credentials");
    String adminSecretName = domainUid1 + "-weblogic-credentials";
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
    createDomainResource(domainUid1, domainNamespace1, adminSecretName, REPO_SECRET_NAME,
              encryptionSecretName, replicaCount);

    // wait for the domain to exist
    logger.info("Check for domain custom resource in namespace {0}", domainNamespace1);
    withStandardRetryPolicy
        .conditionEvaluationListener(
            condition -> logger.info("Waiting for domain {0} to be created in namespace {1} "
                    + "(elapsed time {2}ms, remaining time {3}ms)",
                domainUid1,
                domainNamespace1,
                condition.getElapsedTimeInMS(),
                condition.getRemainingTimeInMS()))
        .until(domainExists(domainUid1, DOMAIN_VERSION, domainNamespace1));


    // check admin server pod exists
    logger.info("Check for admin server pod {0} existence in namespace {1}",
            adminServerPodName, domainNamespace1);
    checkPodCreated(adminServerPodName, domainUid1, domainNamespace1);

    // check managed server pods exist
    for (int i = 1; i <= replicaCount; i++) {
      logger.info("Check for managed server pod {0} existence in namespace {1}",
              managedServerPrefix + i, domainNamespace1);
      checkPodCreated(managedServerPrefix + i, domainUid1, domainNamespace1);
    }

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
  @DisplayName("Create a domain with same domainUid as first domain but in a new namespace")
  @Slow
  @MustNotRunInParallel
  public void testCreateMiiDomainSameDomainUidDiffNS() {
    // admin/managed server name here should match with model yaml in WDT_MODEL_FILE
    final String adminServerPodName = domainUid + "-admin-server";
    final String managedServerPrefix = domainUid + "-managed-server";
    final int replicaCount = 2;

    // Create the repo secret to pull the image
    assertDoesNotThrow(() -> createRepoSecret(domainNamespace1),
            String.format("createSecret failed for %s", REPO_SECRET_NAME));

    // create secret for admin credentials
    logger.info("Create secret for admin credentials");
    String adminSecretName = domainUid + "-weblogic-credentials";
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
    createDomainResource(domainUid, domainNamespace1, adminSecretName, REPO_SECRET_NAME,
              encryptionSecretName, replicaCount);

    // wait for the domain to exist
    logger.info("Check for domain custom resource in namespace {0}", domainNamespace1);
    withStandardRetryPolicy
        .conditionEvaluationListener(
            condition -> logger.info("Waiting for domain {0} to be created in namespace {1} "
                   + "(elapsed time {2}ms, remaining time {3}ms)",
                domainUid,
                domainNamespace1,
                condition.getElapsedTimeInMS(),
                condition.getRemainingTimeInMS()))
        .until(domainExists(domainUid, DOMAIN_VERSION, domainNamespace1));


    // check admin server pod exists
    logger.info("Check for admin server pod {0} existence in namespace {1}",
            adminServerPodName, domainNamespace1);
    checkPodCreated(adminServerPodName, domainUid, domainNamespace1);

    // check managed server pods exist
    for (int i = 1; i <= replicaCount; i++) {
      logger.info("Check for managed server pod {0} existence in namespace {1}",
              managedServerPrefix + i, domainNamespace1);
      checkPodCreated(managedServerPrefix + i, domainUid, domainNamespace1);
    }

    // check admin server pod is ready
    logger.info("Wait for admin server pod {0} to be ready in namespace {1}",
            adminServerPodName, domainNamespace1);
    checkPodReady(adminServerPodName, domainUid, domainNamespace1);

    // check managed server pods are ready
    for (int i = 1; i <= replicaCount; i++) {
      logger.info("Wait for managed server pod {0} to be ready in namespace {1}",
              managedServerPrefix + i, domainNamespace1);
      checkPodReady(managedServerPrefix + i, domainUid, domainNamespace1);
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
  @Order(4)
  @DisplayName("Update the sample-app application to version 2")
  @Slow
  @MustNotRunInParallel
  public void testPatchAppV2() {
    
    // app here is what is in the original app dir plus the replacement in the second app dir
    final String appDir1 = "sample-app";
    final String appDir2 = "sample-app-2";
    final String adminServerPodName = domainUid1 + "-admin-server";
    final String managedServerPrefix = domainUid + "-managed-server";
    final int replicaCount = 2;
    
    // check and make sure that V1 app is running
    for (int i = 1; i <= replicaCount; i++) {
      quickCheckAppRunning(
            domainUid,
            domainNamespace,
            managedServerPrefix + i,
            "8001",
            "sample-war/index.jsp",
            APP_RESPONSE_V1 + i,
            String.format("App sample-war/index.jsp is not running in namespace %s as expected.", 
                domainNamespace));
    }
 
    // check and make sure that the version 2 app is NOT running
    for (int i = 1; i <= replicaCount; i++) {
      quickCheckAppNotRunning(
            domainUid,
            domainNamespace,
            managedServerPrefix + i,
            "8001",
            "sample-war/index.jsp",
            APP_RESPONSE_V2 + i,
            "The second version of the app is not supposed to be running.");   
    }
 
    // create another image with app V2 
    miiImagePatchAppV2 = updateImageWithAppV2Patch(
        String.format("%s-%s", MII_IMAGE_NAME, "test-patch-app-v2"),
        Arrays.asList(appDir1, appDir2));

    // push the image to OCIR to make the test work in multi node cluster
    pushImageIfNeeded(miiImagePatchAppV2);

    // modify the domain resource to use the new image
    patchDomainResourceIamge(domainUid, domainNamespace, miiImagePatchAppV2);
    
    // check if domain resource has been patched with the new image    
    checkDomainPatched(domainUid, domainNamespace, miiImagePatchAppV2);

    // check and wait for the admin server pod to be patched with the new image
    checkPodImagePatched(
        domainUid,
        domainNamespace,
        adminServerPodName,
        miiImagePatchAppV2);

    // check and wait for the managed server pods to be patched with the new image
    for (int i = 1; i <= replicaCount; i++) {
      checkPodImagePatched(
          domainUid,
          domainNamespace,
          managedServerPrefix + i,
          miiImagePatchAppV2);
    }

    // check and wait for the app to be ready
    for (int i = 1; i <= replicaCount; i++) {
      checkAppRunning(
          domainUid,
          domainNamespace,
          managedServerPrefix + i,
          "8001",
          "sample-war/index.jsp",
          APP_RESPONSE_V2 + i);
    }

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
    final String adminServerPodName = domainUid1 + "-admin-server";
    final String managedServerPrefix = domainUid + "-managed-server";
    final int replicaCount = 2;

    // check and V2 app is still running
    for (int i = 1; i <= replicaCount; i++) {
      quickCheckAppRunning(
          domainUid,
          domainNamespace,
          managedServerPrefix + i,
          "8001",
          "sample-war/index.jsp",
          APP_RESPONSE_V2 + i,
          "The expected app is not accessible inside the server pods");
    }

    logger.info("App version 2 is still running");
    
    // check and make sure that the new app is not already running
    for (int i = 1; i <= replicaCount; i++) {
      quickCheckAppNotRunning(
          domainUid,
          domainNamespace,
          managedServerPrefix + i,
          "8001",
          "sample-war-3/index.jsp",
          APP_RESPONSE_V3,
          "The second app is not supposed to be running!!");
    }
   
    logger.info("About to patch the domain with new image");
    
    // create another image with an additional app
    miiImageAddSecondApp = updateImageWithApp3(
        String.format("%s-%s", MII_IMAGE_NAME, "test-add-second-app"),
        Arrays.asList(appDir1, appDir2),
        Collections.singletonList(appDir3),
        "model2-wls.yaml");
    
    logger.info("Image is successfully created");  
  
    // push the image to OCIR to make the test work in multi node cluster
    pushImageIfNeeded(miiImageAddSecondApp);

    // modify the domain resource to use the new image
    patchDomainResourceIamge(domainUid, domainNamespace, miiImageAddSecondApp);
    
    // check if the domain resource has been patched with the new image   
    checkDomainPatched(domainUid, domainNamespace, miiImageAddSecondApp);

    // check and wait for the admin server pod to be patched with the new image
    checkPodImagePatched(
        domainUid,
        domainNamespace,
        adminServerPodName,
        miiImagePatchAppV2);

    // check and wait for the managed server pods to be patched with the new image
    for (int i = 1; i <= replicaCount; i++) {
      checkPodImagePatched(
          domainUid,
          domainNamespace,
          managedServerPrefix + i,
          miiImageAddSecondApp);
    }
    
    // check and wait for the new app to be ready
    for (int i = 1; i <= replicaCount; i++) {
      checkAppRunning(
          domainUid,
          domainNamespace,
          managedServerPrefix + i,
          "8001",
          "sample-war-3/index.jsp",
          APP_RESPONSE_V3);
    }
 
    // check and wait for the original app to be ready
    for (int i = 1; i <= replicaCount; i++) {
      checkAppRunning(
          domainUid,
          domainNamespace,
          managedServerPrefix + i,
          "8001",
          "sample-war/index.jsp",
          APP_RESPONSE_V2 + i);
    }

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
    assertDoesNotThrow(() -> deleteDomainCustomResource(domainUid, domainNamespace),
        "deleteDomainCustomResource failed with ApiException");
    logger.info("Deleted Domain Custom Resource " + domainUid + " from " + domainNamespace);

    assertDoesNotThrow(() -> deleteDomainCustomResource(domainUid1, domainNamespace1),
            "deleteDomainCustomResource failed with ApiException");
    logger.info("Deleted Domain Custom Resource " + domainUid1 + " from " + domainNamespace1);

    logger.info("Delete domain custom resource in namespace {0}", domainNamespace1);
    assertDoesNotThrow(() -> deleteDomainCustomResource(domainUid, domainNamespace1),
            "deleteDomainCustomResource failed with ApiException");
    logger.info("Deleted Domain Custom Resource " + domainUid + " from " + domainNamespace1);

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
      assertTrue(dockerPush(image), String.format("docker push failed for image %s", image));
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
   * @param domainUid the unique identifier of the domain resource
   * @param namespace the Kubernetes namespace that the domain is hosted
   * @param image the name of the image that contains a newer version of the application
   */
  private void patchDomainResourceIamge(
      String domainUid,
      String namespace,
      String image
  ) {
    String patch = 
        String.format("[\n  {\"op\": \"replace\", \"path\": \"/spec/image\", \"value\": \"%s\"}\n]\n",
            image);
    logger.info("About to patch the domain resource with:\n" + patch);

    assertTrue(patchDomainCustomResource(
            domainUid,
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

  private void createRepoSecret(String domNamespace) throws ApiException {
    V1Secret repoSecret = new V1Secret()
            .metadata(new V1ObjectMeta()
                    .name(REPO_SECRET_NAME)
                    .namespace(domNamespace))
            .type("kubernetes.io/dockerconfigjson")
            .putDataItem(".dockerconfigjson", dockerConfigJson.getBytes());

    boolean secretCreated = false;
    try {
      secretCreated = createSecret(repoSecret);
    } catch (ApiException e) {
      logger.info("Exception when calling CoreV1Api#createNamespacedSecret");
      logger.info("Status code: " + e.getCode());
      logger.info("Reason: " + e.getResponseBody());
      logger.info("Response headers: " + e.getResponseHeaders());
      //409 means that the secret already exists - it is not an error, so can proceed
      if (e.getCode() != 409) {
        throw e;
      } else {
        secretCreated = true;
      }

    }
    assertTrue(secretCreated, String.format("create secret failed for %s in namespace %s",
            REPO_SECRET_NAME, domNamespace));
  }

  private void createDomainSecret(String secretName, String username, String password, String domNamespace)
          throws ApiException {
    Map<String, String> secretMap = new HashMap();
    secretMap.put("username", username);
    secretMap.put("password", password);
    boolean secretCreated = assertDoesNotThrow(() -> createSecret(new V1Secret()
            .metadata(new V1ObjectMeta()
                    .name(secretName)
                    .namespace(domNamespace))
            .stringData(secretMap)), "Create secret failed with ApiException");
    assertTrue(secretCreated, String.format("create secret failed for %s in namespace %s", secretName, domNamespace));

  }

  private void createDomainResource(String domainUid, String domNamespace, String adminSecretName,
                                    String repoSecretName, String encryptionSecretName, int replicaCount) {
    // create the domain CR
    Domain domain = new Domain()
            .apiVersion(API_VERSION)
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
                                    .runtimeEncryptionSecret(encryptionSecretName))));

    logger.info("Create domain custom resource for domainUid {0} in namespace {1}",
            domainUid, domNamespace);
    boolean domCreated = assertDoesNotThrow(() -> createDomainCustomResource(domain),
            String.format("Create domain custom resource failed with ApiException for %s in namespace %s",
                    domainUid, domNamespace));
    assertTrue(domCreated, String.format("Create domain custom resource failed with ApiException "
                    + "for %s in namespace %s", domainUid, domNamespace));
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

  private void checkPodReady(String podName, String domainUid, String domNamespace) {
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

  private void checkAppRunning(
      String domainUid,
      String ns,
      String podName,
      String internalPort,
      String appPath, 
      String expectedStr
  ) {
   
    // check if the app is accessible inside of a server pod
    withStandardRetryPolicy
        .conditionEvaluationListener(
            condition -> logger.info("Waiting for application {0} to be ready on {1} in namespace {2} "
            + "(elapsed time {3}ms, remaining time {4}ms)",
            appPath,
            podName,
            domainNamespace,
            condition.getElapsedTimeInMS(),
            condition.getRemainingTimeInMS()))
        .until(assertDoesNotThrow(() -> appAccessibleInPod(
                domainUid, 
                ns, 
                podName, 
                internalPort, 
                appPath, 
                expectedStr),
            String.format(
                "App %s is not ready on pod %s in namespace %s", appPath, podName, domainNamespace)));

  }
  
  private void quickCheckAppRunning(
      String domainUid,
      String ns,
      String podName,
      String internalPort,
      String appPath, 
      String expectedStr,
      String failMessage
  ) {
   
    // check if the app is accessible inside of a server pod
    withQuickRetryPolicy
        .conditionEvaluationListener(
            condition -> logger.info("Checking if application {0} is running on pod {1} in namespace {2} "
            + "(elapsed time {3}ms, remaining time {4}ms)",
            appPath,
            podName,
            domainNamespace,
            condition.getElapsedTimeInMS(),
            condition.getRemainingTimeInMS()))
        .until(assertDoesNotThrow(() -> appAccessibleInPod(
                domainUid, 
                ns,
                podName, 
                internalPort, 
                appPath, 
                expectedStr),
            failMessage));

  }
  
  private void quickCheckAppNotRunning(
      String domainUid,
      String ns,
      String podName,
      String internalPort,
      String appPath, 
      String expectedStr,
      String failMessage
  ) {
   
    // check if the app is not running inside of a server pod
    withQuickRetryPolicy
        .conditionEvaluationListener(
            condition -> logger.info("Checking if application {0} is not running on pod {1} in namespace {2} "
            + "(elapsed time {3}ms, remaining time {4}ms)",
            appPath,
            podName,
            domainNamespace,
            condition.getElapsedTimeInMS(),
            condition.getRemainingTimeInMS()))
        .until(assertDoesNotThrow(() -> appNotAccessibleInPod(
                domainUid, 
                ns, 
                podName,
                internalPort, 
                appPath, 
                expectedStr),
            failMessage));
  }
   
  private void checkDomainPatched(
      String domainUid,
      String ns,
      String image 
  ) {
   
    // check if the app is accessible inside of a server pod
    withStandardRetryPolicy
        .conditionEvaluationListener(
            condition -> logger.info("Waiting for domain {0} to be patched in namespace {1} "
            + "(elapsed time {2}ms, remaining time {3}ms)",
            domainUid,
            ns,
            condition.getElapsedTimeInMS(),
            condition.getRemainingTimeInMS()))
        .until(assertDoesNotThrow(() -> domainResourceImagePatched(domainUid, ns, image),
            String.format(
               "Domain %s is not patched in namespace %s with image %s", domainUid, domainNamespace, image)));

  }
  
  private void checkPodImagePatched(
      String domainUid,
      String ns,
      String podName,
      String image
  ) {
   
    // check if the app is accessible inside of a server pod
    withStandardRetryPolicy
        .conditionEvaluationListener(
            condition -> logger.info("Waiting for pod {0} to be patched in namespace {1} "
            + "(elapsed time {2}ms, remaining time {3}ms)",
            podName,
            ns,
            condition.getElapsedTimeInMS(),
            condition.getRemainingTimeInMS()))
        .until(assertDoesNotThrow(() -> podImagePatched(domainUid, ns, podName, image),
            String.format(
               "Domain %s pod %s is not patched with image %s in namespace %s.",
               domainUid,
               podName,
               domainNamespace,
               image)));
  }
}
