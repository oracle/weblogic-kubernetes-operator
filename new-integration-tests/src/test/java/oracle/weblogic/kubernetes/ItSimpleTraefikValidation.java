// Copyright (c) 2020, Oracle Corporation and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.weblogic.kubernetes;

import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.google.gson.JsonObject;
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
import oracle.weblogic.kubernetes.actions.impl.TraefikParams;
import oracle.weblogic.kubernetes.actions.impl.primitive.HelmParams;
import oracle.weblogic.kubernetes.annotations.IntegrationTest;
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
import static oracle.weblogic.kubernetes.actions.ActionConstants.WIT_BUILD_DIR;
import static oracle.weblogic.kubernetes.actions.TestActions.buildAppArchive;
import static oracle.weblogic.kubernetes.actions.TestActions.callWebAppAndCheckForServerNameInResponse;
import static oracle.weblogic.kubernetes.actions.TestActions.createDomainCustomResource;
import static oracle.weblogic.kubernetes.actions.TestActions.createIngress;
import static oracle.weblogic.kubernetes.actions.TestActions.createMiiImage;
import static oracle.weblogic.kubernetes.actions.TestActions.createSecret;
import static oracle.weblogic.kubernetes.actions.TestActions.createServiceAccount;
import static oracle.weblogic.kubernetes.actions.TestActions.createUniqueNamespace;
import static oracle.weblogic.kubernetes.actions.TestActions.defaultAppParams;
import static oracle.weblogic.kubernetes.actions.TestActions.defaultWitParams;
import static oracle.weblogic.kubernetes.actions.TestActions.deleteDomainCustomResource;
import static oracle.weblogic.kubernetes.actions.TestActions.deleteImage;
import static oracle.weblogic.kubernetes.actions.TestActions.deleteNamespace;
import static oracle.weblogic.kubernetes.actions.TestActions.deleteServiceAccount;
import static oracle.weblogic.kubernetes.actions.TestActions.dockerLogin;
import static oracle.weblogic.kubernetes.actions.TestActions.dockerPush;
import static oracle.weblogic.kubernetes.actions.TestActions.getExpectedResult;
import static oracle.weblogic.kubernetes.actions.TestActions.getIngress;
import static oracle.weblogic.kubernetes.actions.TestActions.installOperator;
import static oracle.weblogic.kubernetes.actions.TestActions.installTraefik;
import static oracle.weblogic.kubernetes.actions.TestActions.uninstallIngress;
import static oracle.weblogic.kubernetes.actions.TestActions.uninstallOperator;
import static oracle.weblogic.kubernetes.actions.TestActions.uninstallTraefik;
import static oracle.weblogic.kubernetes.assertions.TestAssertions.dockerImageExists;
import static oracle.weblogic.kubernetes.assertions.TestAssertions.domainExists;
import static oracle.weblogic.kubernetes.assertions.TestAssertions.isTraefikReady;
import static oracle.weblogic.kubernetes.assertions.TestAssertions.operatorIsRunning;
import static oracle.weblogic.kubernetes.assertions.TestAssertions.podExists;
import static oracle.weblogic.kubernetes.assertions.TestAssertions.podReady;
import static oracle.weblogic.kubernetes.assertions.TestAssertions.serviceExists;
import static oracle.weblogic.kubernetes.utils.FileUtils.checkDirectory;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.awaitility.Awaitility.with;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verify the sample application can be accessed via the ingress controller.
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DisplayName("Verify the sample application can be accessed via the ingress controller")
@IntegrationTest
class ItSimpleTraefikValidation implements LoggedTest {

  // operator constants
  private static final String OPERATOR_RELEASE_NAME = "weblogic-operator";
  private static final String OPERATOR_CHART_DIR =
      "../kubernetes/charts/weblogic-operator";
  private static final String OPERATOR_IMAGE =
      "oracle/weblogic-kubernetes-operator:3.0.0";

  // mii constants
  private static final String WDT_MODEL_FILE = "model1-wls.yaml";
  private static final String MII_IMAGE_NAME = "mii-image";
  private static final String APP_NAME = "sample-app";

  // domain constants
  private static final String DOMAIN_VERSION = "v7";
  private static final String API_VERSION = "weblogic.oracle/" + DOMAIN_VERSION;

  private static HelmParams opHelmParams = null;
  private static V1ServiceAccount serviceAccount = null;
  private static String opNamespace = null;
  private static String domainNamespace = null;
  private static ConditionFactory withStandardRetryPolicy = null;
  private static HelmParams tfHelmParams = null;
  private static HelmParams ingressParam = null;
  private static String tfNamespace = null;
  private static String projectRoot = System.getProperty("user.dir");

  private String domainUid = "domain1";
  private String miiImage = null;
  private final String managedServerNameBase = "managed-server";
  private final int replicaCount = 2;

  /**
   * Install Operator and Traefik.
   */
  @BeforeAll
  public static void initAll() {
    // create standard, reusable retry/backoff policy
    withStandardRetryPolicy = with().pollDelay(2, SECONDS)
        .and().with().pollInterval(10, SECONDS)
        .atMost(5, MINUTES).await();

    // install and verify operator
    installAndVerifyOperator();

    // install and verify traefik
    installAndVerifyTraefik();
  }

  @Test
  @Order(1)
  @DisplayName("Create model in image domain")
  @Slow
  @MustNotRunInParallel
  public void testCreateMiiDomain() {
    // admin/managed server name here should match with model yaml in WDT_MODEL_FILE
    final String adminServerPodName = domainUid + "-admin-server";
    String managedServerPrefix = domainUid + "-" + managedServerNameBase;
    String repoSecretName = "reposecret";

    // create image with model files
    miiImage = createImageAndVerify();

    String repoRegistry = "dummy";
    String repoUserName = "dummy";
    String repoPassword = "dummy";
    String repoEmail = "dummy";

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

    // create docker registry secret in the domain namespace to pull the image from OCIR
    JsonObject dockerConfigJsonObject = getDockerConfigJson(
        repoUserName, repoPassword, repoEmail, repoRegistry);
    String dockerConfigJson = dockerConfigJsonObject.toString();

    // Create the V1Secret configuration
    V1Secret repoSecret = new V1Secret()
        .metadata(new V1ObjectMeta()
            .name(repoSecretName)
            .namespace(domainNamespace))
        .type("kubernetes.io/dockerconfigjson")
        .putDataItem(".dockerconfigjson", dockerConfigJson.getBytes());

    boolean secretCreated = assertDoesNotThrow(() -> createSecret(repoSecret),
        String.format("createSecret failed for %s", repoSecretName));
    assertTrue(secretCreated, String.format("createSecret failed while creating secret %s", repoSecretName));

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
            .name(domainUid)
            .namespace(domainNamespace))
        .spec(new DomainSpec()
            .domainUid(domainUid)
            .domainHomeSourceType("FromModel")
            .image(miiImage)
            .addImagePullSecretsItem(new V1LocalObjectReference()
                .name(repoSecretName))
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
                        .nodePort(getNextFreePort(30711, 30811)))))
            .addClustersItem(new Cluster()
                .clusterName("cluster-1")
                .replicas(replicaCount)
                .serverStartState("RUNNING"))
            .configuration(new Configuration()
                .model(new Model()
                    .domainType("WLS")
                    .runtimeEncryptionSecret(encryptionSecretName))));

    logger.info("Create domain custom resource for domainUid {0} in namespace {1}",
        domainUid, domainNamespace);
    assertTrue(assertDoesNotThrow(() -> createDomainCustomResource(domain),
        String.format("Create domain custom resource failed with ApiException for %s in namespace %s",
            domainUid, domainNamespace)),
        String.format("Create domain custom resource failed with ApiException for %s in namespace %s",
            domainUid, domainNamespace));

    // wait for the domain to exist
    logger.info("Check for domain custom resouce in namespace {0}", domainNamespace);
    withStandardRetryPolicy
        .conditionEvaluationListener(
            condition -> logger.info("Waiting for domain {0} to be created in namespace {1} "
                    + "(elapsed time {2}ms, remaining time {3}ms)",
                domainUid,
                domainNamespace,
                condition.getElapsedTimeInMS(),
                condition.getRemainingTimeInMS()))
        .until(domainExists(domainUid, DOMAIN_VERSION, domainNamespace));

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
    checkPodReady(adminServerPodName);

    // check managed server pods are running
    for (int i = 1; i <= replicaCount; i++) {
      logger.info("Wait for managed server pod {0} to be ready in namespace {1}",
          managedServerPrefix + i, domainNamespace);
      checkPodReady(managedServerPrefix + i);
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
  }

  @Test
  @Order(2)
  @DisplayName("Create an ingress per domain")
  public void testCreateIngress() {
    // helm install parameters for ingress
    ingressParam = new HelmParams()
            .releaseName(domainUid + "-ingress")
            .namespace(domainNamespace)
            .chartDir(projectRoot + "/../kubernetes/samples/charts/ingress-per-domain");

    assertThat(createIngress(ingressParam, domainUid, domainUid + ".org"))
            .as("Test createIngress returns true")
            .withFailMessage("createIngress() did not return true")
            .isTrue();

    // check the ingress is created
    String ingressName = domainUid + "-traefik";
    assertThat(assertDoesNotThrow(() -> getIngress(domainNamespace)))
        .as(String.format("get the ingress %1s in namespace %2s", ingressName, domainNamespace))
        .withFailMessage(String.format("can not get ingress %1s in namespace %2s", ingressName, domainNamespace))
        .contains(ingressName);

    logger.info("ingress is created in namespace {0}", domainNamespace);
  }

  @Test
  @Order(3)
  @DisplayName("Verify the application can be accessed through the ingress controller ")
  public void testSampleAppThroughIngressController() throws Exception {
    String hostname = InetAddress.getLocalHost().getHostName();
    String curlCmd = String.format("curl --silent --noproxy '*' -H 'host: %1s' http://%2s:30305/sample-war/index.jsp",
        domainUid + ".org", hostname);
    verifyIngressController(curlCmd, 50);
  }

  /**
   * TODO: remove this after Sankar's PR is merged
   * Uninstall Operator, traefik and ingress, delete service account, domain namespace, traefik namespace and
   * operator namespace.
   */
  @AfterAll
  public void tearDownAll() {
    // Delete domain custom resource
    logger.info("Delete domain custom resource in namespace {0}", domainNamespace);
    assertDoesNotThrow(() -> deleteDomainCustomResource(domainUid, domainNamespace),
        "deleteDomainCustomResource failed with ApiException");
    logger.info("Deleted Domain Custom Resource " + domainUid + " from " + domainNamespace);

    // delete the domain image created for the test
    if (miiImage != null) {
      deleteImage(miiImage);
    }

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

    // uninstall ingress
    if (ingressParam != null) {
      assertThat(uninstallIngress(ingressParam))
          .as("Test uninstallIngress returns true")
          .withFailMessage("uninstallIngress() did not return true")
          .isTrue();
    }

    // uninstall traefik release
    if (tfHelmParams != null) {
      assertThat(uninstallTraefik(tfHelmParams))
          .as("Test uninstallTraefik returns true")
          .withFailMessage("uninstallTraefik() did not return true")
          .isTrue();
    }

    // Delete traefik Namespace
    if (tfNamespace != null) {
      assertThatCode(
          () -> deleteNamespace(tfNamespace))
          .as("Test that deleteNamespace does not throw an exception")
          .withFailMessage("deleteNamespace() threw an exception")
          .doesNotThrowAnyException();
      logger.info("Deleted namespace: {0}", tfNamespace);
    }
  }

  /**
   * Prepare and install weblogic operator.
   */
  private static void installAndVerifyOperator() {
    // get a new unique opNamespace
    logger.info("Creating unique namespace for Operator");
    opNamespace = assertDoesNotThrow(() -> createUniqueNamespace(),
        "Failed to create unique namespace due to ApiException");
    logger.info("Created a new namespace called {0}", opNamespace);

    logger.info("Creating unique namespace for Domain");
    domainNamespace = assertDoesNotThrow(() -> createUniqueNamespace(),
        "Failed to create unique namespace due to ApiException");
    logger.info("Created a new namespace called {0}", domainNamespace);

    // Create a service account for the opNamespace
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
    String cmd = String.format("helm list -n %s", opNamespace);
    assertTrue(getExpectedResult(cmd, OPERATOR_RELEASE_NAME),
        String.format("did not get %1s in namespace %2s", OPERATOR_RELEASE_NAME, opNamespace));

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

  /**
   * Install traefik and wait until the traefik pod is ready.
   */
  private static void installAndVerifyTraefik() {

    // create a new unique namespace for traefik
    logger.info("Creating an unique namespace for Traefil");
    tfNamespace = assertDoesNotThrow(() -> createUniqueNamespace(),
        "Fail to create an unique namespace due to ApiException");
    logger.info("Created a new namespace called {0}", tfNamespace);

    // helm install parameters
    tfHelmParams = new HelmParams()
        .releaseName("traefik-operator")
        .namespace(tfNamespace)
        .repoUrl("https://kubernetes-charts.storage.googleapis.com/")
        .repoName("stable")
        .chartName("traefik")
        .chartValuesFile(projectRoot + "/../kubernetes/samples/charts/traefik/values.yaml");

    TraefikParams tfParams = new TraefikParams()
        .helmParams(tfHelmParams)
        .namespaces("{" + tfNamespace + "," + domainNamespace + "}");

    // install Traefik
    assertThat(installTraefik(tfParams))
        .as("Test installTraefik returns true")
        .withFailMessage("installTraefik() did not return true")
        .isTrue();
    logger.info("Traefik is installed in namespace {0}", tfNamespace);

    // verify that the traefik is installed
    String cmd = "helm ls -n " + tfNamespace;
    assertThat(getExpectedResult(cmd, tfHelmParams.getReleaseName()))
        .as("traefik is installed")
        .withFailMessage("did not find traefik-operator in " + tfNamespace)
        .isTrue();

    // first wait 30 seconds, then check if the traefik pod is ready.
    with().pollDelay(30, SECONDS)
        .and().with().pollInterval(10, SECONDS)
        .conditionEvaluationListener(
            condition -> logger.info(
                "Waiting for traefik to be ready (elapsed time {0}ms, remaining time {1}ms)",
                condition.getElapsedTimeInMS(),
                condition.getRemainingTimeInMS()))
        .await().atMost(5, MINUTES)
        .until(isTraefikReady(tfNamespace));
  }

  /**
   * Verify the ingress controller can route the app to all managed servers.
   *
   * @param curlCmd curl command to ping apps through ingress controller
   * @param maxIterations max iterations to call curl command
   * @throws Exception if the ingress controller can not route the app to one or more servers
   */
  private void verifyIngressController(String curlCmd, int maxIterations) throws Exception {
    List<String> managedServerNames = new ArrayList<>();
    for (int i = 1; i <= replicaCount; i++) {
      managedServerNames.add(managedServerNameBase + i);
    }

    callWebAppAndCheckForServerNameInResponse(curlCmd, managedServerNames, maxIterations);
  }

  /**
   * Create a docker image for model in image.
   *
   * @return image name
   */
  private String createImageAndVerify() {
    // create unique image name with date
    DateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd");
    Date date = new Date();
    final String imageTag = dateFormat.format(date) + "-" + System.currentTimeMillis();

    // build the model file list
    List<String> modelList = Collections.singletonList(MODEL_DIR + "/" + WDT_MODEL_FILE);

    // build an application archive using what is in resources/apps/APP_NAME
    assertTrue(buildAppArchive(defaultAppParams()
        .srcDir(APP_NAME)), String.format("Failed to create app archive for %s", APP_NAME));

    // build the archive list
    String zipFile = String.format("%s/%s.zip", ARCHIVE_DIR, APP_NAME);
    List<String> archiveList = Collections.singletonList(zipFile);

    // Set additional environment variables for WIT
    checkDirectory(WIT_BUILD_DIR);
    Map<String, String> env = new HashMap();
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
            .wdtVersion("latest")
            .env(env)
            .redirect(true));

    assertTrue(result, String.format("Failed to create the image %s using WebLogic Image Tool", MII_IMAGE_NAME));

    // check image exists
    assertTrue(dockerImageExists(MII_IMAGE_NAME, imageTag),
        String.format("Image %s doesn't exist", MII_IMAGE_NAME + ":" + imageTag));

    return MII_IMAGE_NAME + ":" + imageTag;
  }

  /**
   * Check pod is created.
   *
   * @param podName pod name to check
   */
  private void checkPodCreated(String podName) {
    withStandardRetryPolicy
        .conditionEvaluationListener(
            condition -> logger.info("Waiting for pod {0} to be created in namespace {1} "
                    + "(elapsed time {2}ms, remaining time {3}ms)",
                podName,
                domainNamespace,
                condition.getElapsedTimeInMS(),
                condition.getRemainingTimeInMS()))
        .until(assertDoesNotThrow(() -> podExists(podName, domainUid, domainNamespace),
            String.format("podExists failed with ApiException for %s in namespace in %s",
                podName, domainNamespace)));

  }

  /**
   * Check pod is ready.
   *
   * @param podName pod name to check
   */
  private void checkPodReady(String podName) {
    withStandardRetryPolicy
        .conditionEvaluationListener(
            condition -> logger.info("Waiting for pod {0} to be ready in namespace {1} "
                    + "(elapsed time {2}ms, remaining time {3}ms)",
                podName,
                domainNamespace,
                condition.getElapsedTimeInMS(),
                condition.getRemainingTimeInMS()))
        .until(assertDoesNotThrow(() -> podReady(podName, domainUid, domainNamespace),
            String.format(
                "pod %s is not ready in namespace %s", podName, domainNamespace)));

  }

  /**
   * Check service is created.
   *
   * @param serviceName service name to check
   */
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

  /**
   * Get docker registry configuration json object.
   *
   * @param username username for the docker registry
   * @param password password for the docker registry
   * @param email email for the docker registry
   * @param registry docker registry name
   * @return json object for the docker registry configuration
   */
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

  /**
   * Get the next free port between range from to to.
   * @param from range starting point
   * @param to range ending port
   * @return free port
   */
  private int getNextFreePort(int from, int to) {
    int port;
    for (port = from; port < to; port++) {
      if (isLocalPortFree(port)) {
        logger.info("next free port is: {0}", port);
        return port;
      }
    }
    logger.info("Can not find free port between {0} and {1}", from, to);
    return port;
  }

  /**
   * Check if the given port number is free.
   *
   * @param port port number to check
   * @return true if the port is free, false otherwise
   */
  private boolean isLocalPortFree(int port) {
    try {
      new ServerSocket(port).close();
      return true;
    } catch (IOException e) {
      return false;
    }
  }
}
