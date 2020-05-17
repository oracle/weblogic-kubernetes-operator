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

import com.google.gson.JsonObject;
import io.kubernetes.client.openapi.models.V1EnvVar;
import io.kubernetes.client.openapi.models.V1LocalObjectReference;
import io.kubernetes.client.openapi.models.V1ObjectMeta;
import io.kubernetes.client.openapi.models.V1Secret;
import io.kubernetes.client.openapi.models.V1SecretReference;
import io.kubernetes.client.openapi.models.V1ServiceAccount;
import oracle.weblogic.domain.AdminServer;
import oracle.weblogic.domain.Cluster;
import oracle.weblogic.domain.Configuration;
import oracle.weblogic.domain.Domain;
import oracle.weblogic.domain.DomainSpec;
import oracle.weblogic.domain.Model;
import oracle.weblogic.domain.ServerPod;
import oracle.weblogic.kubernetes.actions.impl.NginxParams;
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
import static oracle.weblogic.kubernetes.TestConstants.GOOGLE_REPO_URL;
import static oracle.weblogic.kubernetes.TestConstants.K8S_NODEPORT_HOST;
import static oracle.weblogic.kubernetes.TestConstants.NGINX_CHART_NAME;
import static oracle.weblogic.kubernetes.TestConstants.NGINX_RELEASE_NAME;
import static oracle.weblogic.kubernetes.TestConstants.OPERATOR_CHART_DIR;
import static oracle.weblogic.kubernetes.TestConstants.OPERATOR_RELEASE_NAME;
import static oracle.weblogic.kubernetes.TestConstants.REPO_DUMMY_VALUE;
import static oracle.weblogic.kubernetes.TestConstants.REPO_EMAIL;
import static oracle.weblogic.kubernetes.TestConstants.REPO_NAME;
import static oracle.weblogic.kubernetes.TestConstants.REPO_PASSWORD;
import static oracle.weblogic.kubernetes.TestConstants.REPO_REGISTRY;
import static oracle.weblogic.kubernetes.TestConstants.REPO_SECRET_NAME;
import static oracle.weblogic.kubernetes.TestConstants.REPO_USERNAME;
import static oracle.weblogic.kubernetes.TestConstants.STABLE_REPO_NAME;
import static oracle.weblogic.kubernetes.actions.ActionConstants.ARCHIVE_DIR;
import static oracle.weblogic.kubernetes.actions.ActionConstants.MODEL_DIR;
import static oracle.weblogic.kubernetes.actions.ActionConstants.WDT_VERSION;
import static oracle.weblogic.kubernetes.actions.ActionConstants.WIT_BUILD_DIR;
import static oracle.weblogic.kubernetes.actions.TestActions.buildAppArchive;
import static oracle.weblogic.kubernetes.actions.TestActions.createDockerConfigJson;
import static oracle.weblogic.kubernetes.actions.TestActions.createDomainCustomResource;
import static oracle.weblogic.kubernetes.actions.TestActions.createIngress;
import static oracle.weblogic.kubernetes.actions.TestActions.createMiiImage;
import static oracle.weblogic.kubernetes.actions.TestActions.createSecret;
import static oracle.weblogic.kubernetes.actions.TestActions.createServiceAccount;
import static oracle.weblogic.kubernetes.actions.TestActions.defaultAppParams;
import static oracle.weblogic.kubernetes.actions.TestActions.defaultWitParams;
import static oracle.weblogic.kubernetes.actions.TestActions.dockerLogin;
import static oracle.weblogic.kubernetes.actions.TestActions.dockerPush;
import static oracle.weblogic.kubernetes.actions.TestActions.getOperatorImageName;
import static oracle.weblogic.kubernetes.actions.TestActions.getPodCreationTimestamp;
import static oracle.weblogic.kubernetes.actions.TestActions.installNginx;
import static oracle.weblogic.kubernetes.actions.TestActions.installOperator;
import static oracle.weblogic.kubernetes.actions.TestActions.listIngresses;
import static oracle.weblogic.kubernetes.actions.TestActions.scaleCluster;
import static oracle.weblogic.kubernetes.actions.TestActions.uninstallNginx;
import static oracle.weblogic.kubernetes.assertions.TestAssertions.doesImageExist;
import static oracle.weblogic.kubernetes.assertions.TestAssertions.domainExists;
import static oracle.weblogic.kubernetes.assertions.TestAssertions.isHelmReleaseDeployed;
import static oracle.weblogic.kubernetes.assertions.TestAssertions.isNginxReady;
import static oracle.weblogic.kubernetes.assertions.TestAssertions.operatorIsReady;
import static oracle.weblogic.kubernetes.assertions.TestAssertions.podDoesNotExist;
import static oracle.weblogic.kubernetes.assertions.TestAssertions.podExists;
import static oracle.weblogic.kubernetes.assertions.TestAssertions.podReady;
import static oracle.weblogic.kubernetes.assertions.TestAssertions.podStateNotChangedDuringScalingCluster;
import static oracle.weblogic.kubernetes.assertions.TestAssertions.serviceExists;
import static oracle.weblogic.kubernetes.utils.FileUtils.checkDirectory;
import static oracle.weblogic.kubernetes.utils.TestUtils.callWebAppAndCheckForServerNameInResponse;
import static oracle.weblogic.kubernetes.utils.TestUtils.getNextFreePort;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.with;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verify the model in image domain with multiple clusters can be scaled up and down.
 * Also verify the sample application can be accessed via NGINX.
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DisplayName("Verify scaling multiple clusters domain and the sample application can be accessed via NGINX")
@IntegrationTest
class ItScaleMiiDomainNginx implements LoggedTest {

  // mii constants
  private static final String WDT_MODEL_FILE = "model-multiclusterdomain-sampleapp-wls.yaml";
  private static final String MII_IMAGE_NAME = "mii-image";
  private static final String APP_NAME = "sample-app";

  // domain constants
  private static final String DOMAIN_VERSION = "v7";
  private static final String API_VERSION = "weblogic.oracle/" + DOMAIN_VERSION;
  private static final int NUMBER_OF_CLUSTERS = 2;
  private static final String CLUSTER_NAME_PREFIX = "cluster-";
  private static final int MANAGED_SERVER_PORT = 8001;

  private static String opNamespace = null;
  private static String domainNamespace = null;
  private static ConditionFactory withStandardRetryPolicy = null;
  private static String dockerConfigJson = "";
  private static HelmParams nginxHelmParams = null;
  private static String nginxNamespace = null;
  private static int nodeportshttp = 0;
  private static int nodeportshttps = 0;

  private final int replicaCount = 2;
  private final String managedServerNameBase = "-managed-server";
  private final String domainUid = "domain1";
  private String curlCmd = null;

  /**
   * Install operator and NGINX.
   *
   * @param namespaces list of namespaces created by the IntegrationTestWatcher by the
   *                   JUnit engine parameter resolution mechanism
   */
  @BeforeAll
  public static void initAll(@Namespaces(3) List<String> namespaces) {
    // create standard, reusable retry/backoff policy
    withStandardRetryPolicy = with().pollDelay(2, SECONDS)
        .and().with().pollInterval(10, SECONDS)
        .atMost(5, MINUTES).await();

    // get a unique operator namespace
    logger.info("Get a unique namespace for operator");
    assertNotNull(namespaces.get(0), "Namespace list is null");
    opNamespace = namespaces.get(0);

    // get a unique domain namespace
    logger.info("Get a unique namespace for WebLogic domain");
    assertNotNull(namespaces.get(1), "Namespace list is null");
    domainNamespace = namespaces.get(1);

    // get a unique NGINX namespace
    logger.info("Get a unique namespace for NGINX");
    assertNotNull(namespaces.get(2), "Namespace list is null");
    nginxNamespace = namespaces.get(2);

    // install and verify operator
    installAndVerifyOperator();

    // get a free node port for NGINX
    nodeportshttp = getNextFreePort(30305, 30405);
    nodeportshttps = getNextFreePort(30443, 30543);

    // install and verify NGINX
    installAndVerifyNginx();
  }

  @Test
  @Order(1)
  @DisplayName("Create model in image domain with multiple clusters")
  @Slow
  @MustNotRunInParallel
  public void testCreateMiiDomainWithMultiClusters() {

    // admin/managed server name here should match with model yaml in WDT_MODEL_FILE
    final String adminServerPodName = domainUid + "-admin-server";

    // create image with model files
    logger.info("Creating image with model file and verify");
    String miiImage = createImageAndVerify();

    // docker login, if necessary
    if (!REPO_USERNAME.equals(REPO_DUMMY_VALUE)) {
      logger.info("docker login");
      assertTrue(dockerLogin(REPO_REGISTRY, REPO_USERNAME, REPO_PASSWORD), "docker login failed");
    }

    // push image, if necessary
    if (!REPO_NAME.isEmpty()) {
      logger.info("docker push image {0} to {1}", miiImage, REPO_NAME);
      assertTrue(dockerPush(miiImage), String.format("docker push failed for image %s", miiImage));
    }

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
    logger.info("Creating secret for admin credentials");
    String adminSecretName = "weblogic-credentials";
    Map<String, String> adminSecretMap = new HashMap<>();
    adminSecretMap.put("username", "weblogic");
    adminSecretMap.put("password", "welcome1");
    secretCreated = assertDoesNotThrow(() -> createSecret(new V1Secret()
        .metadata(new V1ObjectMeta()
            .name(adminSecretName)
            .namespace(domainNamespace))
        .stringData(adminSecretMap)), "Create secret failed with ApiException");
    assertTrue(secretCreated, String.format("create secret failed for %s", adminSecretName));

    // create encryption secret
    logger.info("Creating encryption secret");
    String encryptionSecretName = "encryptionsecret";
    Map<String, String> encryptionSecretMap = new HashMap<>();
    encryptionSecretMap.put("username", "weblogicenc");
    encryptionSecretMap.put("password", "weblogicenc");
    secretCreated = assertDoesNotThrow(() -> createSecret(new V1Secret()
        .metadata(new V1ObjectMeta()
            .name(encryptionSecretName)
            .namespace(domainNamespace))
        .stringData(encryptionSecretMap)), "Create secret failed with ApiException");
    assertTrue(secretCreated, String.format("create secret failed for %s", encryptionSecretName));

    // construct the cluster list used for domain custom resource
    List<Cluster> clusterList = new ArrayList<>();
    for (int i = NUMBER_OF_CLUSTERS; i >= 1; i--) {
      clusterList.add(new Cluster()
          .clusterName(CLUSTER_NAME_PREFIX + i)
          .replicas(replicaCount)
          .serverStartState("RUNNING"));
    }

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
                .serverStartState("RUNNING"))
            .clusters(clusterList)
            .configuration(new Configuration()
                .model(new Model()
                    .domainType("WLS")
                    .runtimeEncryptionSecret(encryptionSecretName))));

    logger.info("Creating domain custom resource for domainUid {0} in namespace {1}",
        domainUid, domainNamespace);
    assertTrue(assertDoesNotThrow(() -> createDomainCustomResource(domain),
        String.format("Create domain custom resource failed with ApiException for %s in namespace %s",
            domainUid, domainNamespace)),
        String.format("Create domain custom resource failed with ApiException for %s in namespace %s",
            domainUid, domainNamespace));

    // wait for the domain to exist
    logger.info("Checking for domain custom resource existence in namespace {0}", domainNamespace);
    withStandardRetryPolicy
        .conditionEvaluationListener(
            condition -> logger.info("Waiting for domain {0} to be created in namespace {1} "
                    + "(elapsed time {2}ms, remaining time {3}ms)",
                domainUid,
                domainNamespace,
                condition.getElapsedTimeInMS(),
                condition.getRemainingTimeInMS()))
        .until(domainExists(domainUid, DOMAIN_VERSION, domainNamespace));

    // check admin server pod was created
    logger.info("Checking that admin server pod {0} was created in namespace {1}",
        adminServerPodName, domainNamespace);
    checkPodCreated(adminServerPodName);

    // check admin server pod is ready
    logger.info("Checking that admin server pod {0} is ready in namespace {1}",
        adminServerPodName, domainNamespace);
    checkPodReady(adminServerPodName);

    // check admin service is created
    logger.info("Checking that admin service {0} was created in namespace {1}",
        adminServerPodName, domainNamespace);
    checkServiceCreated(adminServerPodName);

    // check the readiness for the managed servers in each cluster
    for (int i = 1; i <= NUMBER_OF_CLUSTERS; i++) {
      for (int j = 1; j <= replicaCount; j++) {
        String managedServerPodName =
            domainUid + "-" + CLUSTER_NAME_PREFIX + i + managedServerNameBase + j;

        // check managed server pod was created
        logger.info("Checking that managed server pod {0} was created in namespace {1}",
            managedServerPodName, domainNamespace);
        checkPodCreated(managedServerPodName);

        // check managed server pod is ready
        logger.info("Checking that managed server pod {0} is ready in namespace {1}",
            managedServerPodName, domainNamespace);
        checkPodReady(managedServerPodName);

        // check managed server service was created
        logger.info("Checking that managed server service {0} was created in namespace {1}",
            managedServerPodName, domainNamespace);
        checkServiceCreated(managedServerPodName);
      }
    }
  }

  @Test
  @Order(2)
  @DisplayName("Create an ingress for each cluster of the WebLogic domain in the specified domain namespace")
  public void testCreateIngress() {

    // create an ingress for each cluster of the domain in the domain namespace
    for (int i = 1; i <= NUMBER_OF_CLUSTERS; i++) {

      String clusterName = CLUSTER_NAME_PREFIX + i;
      String ingressName = domainUid + "-" + clusterName + "-nginx";

      logger.info("Creating ingress {0} for cluster {1} of domain {2} in namespace {3}",
          ingressName, clusterName, domainUid, domainNamespace);
      assertThat(createIngress(ingressName, domainNamespace, domainUid, clusterName,
          MANAGED_SERVER_PORT, domainUid + "." + clusterName + ".test"))
          .as("Test ingress creation succeeds", ingressName)
          .withFailMessage("Ingress creation failed for cluster {0} of domain {1} in namespace {2}",
              clusterName, domainUid, domainNamespace)
          .isTrue();

      // check that the ingress was found in the domain namespace
      assertThat(assertDoesNotThrow(() -> listIngresses(domainNamespace)))
          .as("Test ingress {0} was found in namespace {1}", ingressName, domainNamespace)
          .withFailMessage("Ingress {0} was not found in namespace {1}", ingressName, domainNamespace)
          .contains(ingressName);

      logger.info("Ingress {0} for cluster {1} of domain {2} was found in namespace {3}",
          ingressName, clusterName, domainUid, domainNamespace);
    }
  }

  @Test
  @Order(3)
  @DisplayName("Verify the application can be accessed through the ingress controller for each cluster in the domain")
  public void testAppAccessThroughIngressController() {

    for (int i = 1; i <= NUMBER_OF_CLUSTERS; i++) {
      String clusterName = CLUSTER_NAME_PREFIX + i;

      List<String> managedServerListBeforeScale =
          listManagedServersBeforeScale(clusterName, replicaCount);

      // check that NGINX can access the sample apps from all managed servers in the cluster of the domain
      curlCmd = generateCurlCmd(clusterName);
      assertThat(callWebAppAndCheckForServerNameInResponse(curlCmd, managedServerListBeforeScale, 50))
          .as("Verify NGINX can access the sample app from all managed servers in the domain")
          .withFailMessage("NGINX can not access the sample app from one or more of the managed servers")
          .isTrue();
    }
  }

  @Test
  @Order(4)
  @DisplayName("Verify scale each cluster of the domain in domain namespace")
  public void testScaleClusters() {

    for (int i = 1; i <= NUMBER_OF_CLUSTERS; i++) {

      String clusterName = CLUSTER_NAME_PREFIX + i;
      int numberOfServers = 2 * i - 1;

      // scale cluster-1 to 1 server and cluster-2 to 3 servers
      logger.info("Scaling cluster {0} of domain {1} in namespace {2} to {3} servers.",
          clusterName, domainUid, domainNamespace, numberOfServers);
      scaleAndVerifyCluster(clusterName, replicaCount, numberOfServers);

      // then scale cluster-1 and cluster-2 to 0 server
      scaleAndVerifyCluster(clusterName, numberOfServers, 0);
    }
  }

  /**
   * TODO: remove this after Sankar's PR is merged
   * The cleanup framework does not uninstall NGINX release. Do it here for now.
   */
  @AfterAll
  public void tearDownAll() {
    // uninstall NGINX release
    if (nginxHelmParams != null) {
      assertThat(uninstallNginx(nginxHelmParams))
          .as("Test uninstallNginx returns true")
          .withFailMessage("uninstallNginx() did not return true")
          .isTrue();
    }
  }

  /**
   * Install WebLogic operator and wait until the operator pod is ready.
   */
  private static void installAndVerifyOperator() {

    // Create a service account for the unique opNamespace
    logger.info("Creating service account");
    String serviceAccountName = opNamespace + "-sa";
    assertDoesNotThrow(() -> createServiceAccount(new V1ServiceAccount()
        .metadata(new V1ObjectMeta()
                .namespace(opNamespace)
                .name(serviceAccountName))));
    logger.info("Created service account: {0}", serviceAccountName);

    // get operator image name
    String operatorImage = getOperatorImageName();
    assertFalse(operatorImage.isEmpty(), "operator image name can not be empty");
    logger.info("Got operator image name {0}", operatorImage);

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
    assertTrue(secretCreated, String.format("createSecret failed while creating secret %s in namespace %s",
        REPO_SECRET_NAME, opNamespace));

    // map with secret
    Map<String, Object> secretNameMap = new HashMap<>();
    secretNameMap.put("name", REPO_SECRET_NAME);

    // Helm install parameters
    HelmParams opHelmParams = new HelmParams()
        .releaseName(OPERATOR_RELEASE_NAME)
        .namespace(opNamespace)
        .chartDir(OPERATOR_CHART_DIR);

    // operator chart values to override
    OperatorParams opParams = new OperatorParams()
        .helmParams(opHelmParams)
        .image(operatorImage)
        .imagePullSecrets(secretNameMap)
        .domainNamespaces(Arrays.asList(domainNamespace))
        .serviceAccount(serviceAccountName);

    // install operator
    logger.info("Installing operator in namespace {0}", opNamespace);
    assertTrue(installOperator(opParams),
        String.format("Failed to install operator in namespace %s", opNamespace));
    logger.info("Operator installed in namespace {0}", opNamespace);

    // list Helm releases matching operator release name in operator namespace
    logger.info("Checking operator release {0} status in namespace {1}",
        OPERATOR_RELEASE_NAME, opNamespace);
    assertTrue(isHelmReleaseDeployed(OPERATOR_RELEASE_NAME, opNamespace),
        String.format("Operator release %s is not in deployed status in namespace %s",
            OPERATOR_RELEASE_NAME, opNamespace));
    logger.info("Operator release {0} status is deployed in namespace {1}",
        OPERATOR_RELEASE_NAME, opNamespace);

    // check operator is running
    logger.info("Checking that operator pod is running in namespace {0}", opNamespace);
    withStandardRetryPolicy
        .conditionEvaluationListener(
            condition -> logger.info("Waiting for operator to be running in namespace {0} "
                    + "(elapsed time {1}ms, remaining time {2}ms)",
                opNamespace,
                condition.getElapsedTimeInMS(),
                condition.getRemainingTimeInMS()))
        .until(operatorIsReady(opNamespace));
  }

  /**
   * Install NGINX and wait until the NGINX pod is ready.
   */
  private static void installAndVerifyNginx() {

    // Helm install parameters
    nginxHelmParams = new HelmParams()
        .releaseName(NGINX_RELEASE_NAME)
        .namespace(nginxNamespace)
        .repoUrl(GOOGLE_REPO_URL)
        .repoName(STABLE_REPO_NAME)
        .chartName(NGINX_CHART_NAME);

    // NGINX chart values to override
    NginxParams nginxParams = new NginxParams()
        .helmParams(nginxHelmParams)
        .nodePortsHttp(nodeportshttp)
        .nodePortsHttps(nodeportshttps);

    // install NGINX
    assertThat(installNginx(nginxParams))
        .as("Test installing NGINX succeeds")
        .withFailMessage("NGINX installation is failed")
        .isTrue();

    // verify that NGINX is installed
    logger.info("Checking NGINX release {0} status in namespace {1}",
        NGINX_RELEASE_NAME, nginxNamespace);
    assertTrue(isHelmReleaseDeployed(NGINX_RELEASE_NAME, nginxNamespace),
        String.format("NGINX release %s is not in deployed status in namespace %s",
            NGINX_RELEASE_NAME, nginxNamespace));
    logger.info("NGINX release {0} status is deployed in namespace {1}",
        NGINX_RELEASE_NAME, nginxNamespace);

    // wait until the NGINX pod is ready.
    withStandardRetryPolicy
        .conditionEvaluationListener(
            condition -> logger.info(
                "Waiting for NGINX to be ready in namespace {0} (elapsed time {1}ms, remaining time {2}ms)",
                nginxNamespace,
                condition.getElapsedTimeInMS(),
                condition.getRemainingTimeInMS()))
        .until(isNginxReady(nginxNamespace));
  }

  /**
   * Create a Docker image for model in image.
   *
   * @return image name with tag
   */
  private String createImageAndVerify() {

    // create unique image name with date
    DateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd");
    Date date = new Date();
    final String imageTag = dateFormat.format(date) + "-" + System.currentTimeMillis();
    // Add repository name in image name for Jenkins runs
    final String imageName = REPO_NAME + MII_IMAGE_NAME;
    final String image = imageName + ":" + imageTag;

    // build the model file list
    final List<String> modelList = Collections.singletonList(MODEL_DIR + "/" + WDT_MODEL_FILE);

    // build an application archive using what is in resources/apps/APP_NAME
    assertTrue(buildAppArchive(defaultAppParams()
        .srcDirList(Collections.singletonList(APP_NAME))), 
            String.format("Failed to create app archive for %s", APP_NAME));

    // build the archive list
    String zipFile = String.format("%s/%s.zip", ARCHIVE_DIR, APP_NAME);
    final List<String> archiveList = Collections.singletonList(zipFile);

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
    logger.info("Creating image {0} using model directory {1}", image, MODEL_DIR);
    boolean result = createMiiImage(
        defaultWitParams()
            .modelImageName(imageName)
            .modelImageTag(imageTag)
            .modelFiles(modelList)
            .modelArchiveFiles(archiveList)
            .wdtVersion(WDT_VERSION)
            .env(env)
            .redirect(true));

    assertTrue(result, String.format("Failed to create the image %s using WebLogic Image Tool", image));

    // Check image exists using docker images | grep image tag.
    assertTrue(doesImageExist(imageTag),
        String.format("Image %s does not exist", image));

    return image;
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
   * Check pod was deleted.
   *
   * @param podName pod name to check
   */
  private void checkPodDeleted(String podName) {
    withStandardRetryPolicy
        .conditionEvaluationListener(
            condition -> logger.info("Waiting for pod {0} to be deleted in namespace {1} "
                    + "(elapsed time {2}ms, remaining time {3}ms)",
                podName,
                domainNamespace,
                condition.getElapsedTimeInMS(),
                condition.getRemainingTimeInMS()))
        .until(assertDoesNotThrow(() -> podDoesNotExist(podName, null, domainNamespace),
            String.format("Pod %s still exists in namespace %s", podName, domainNamespace)));
  }

  /** Scale the WebLogic cluster to specified number of servers.
   *  And verify the sample app can be accessed through NGINX.
   *
   * @param clusterName the WebLogic cluster name in the domain to be scaled
   * @param replicasBeforeScale the replicas of the WebLogic cluster before the scale
   * @param replicasAfterScale the replicas of the WebLogic cluster after the scale
   */
  private void scaleAndVerifyCluster(String clusterName,
                                     int replicasBeforeScale,
                                     int replicasAfterScale) {

    String manageServerPodNamePrefix = domainUid + "-" + clusterName + managedServerNameBase;

    // get the original managed server pod creation timestamp before scale
    List<String> listOfPodCreationTimestamp = new ArrayList<>();
    for (int i = 1; i <= replicasBeforeScale; i++) {
      String managedServerPodName = manageServerPodNamePrefix + i;
      String originalCreationTimestamp =
          assertDoesNotThrow(() -> getPodCreationTimestamp(domainNamespace, "", managedServerPodName),
              String.format("getPodCreationTimestamp failed with ApiException for pod {0} in namespace {1}",
                  managedServerPodName, domainNamespace));
      listOfPodCreationTimestamp.add(originalCreationTimestamp);
    }

    // scale the cluster in the domain
    logger.info("Scaling cluster {0} of domain {1} in namespace {2} to {3} servers",
        clusterName, domainUid, domainNamespace, replicasAfterScale);
    assertThat(assertDoesNotThrow(() -> scaleCluster(domainUid, domainNamespace, clusterName, replicasAfterScale)))
        .as("Verify scaling cluster {0} of domain {1} in namespace {2} succeeds",
            clusterName, domainUid, domainNamespace)
        .withFailMessage("Scaling cluster failed")
        .isTrue();

    // generate a curl command to access the sample app through the ingress controller
    curlCmd = generateCurlCmd(clusterName);

    // generate the expected server list which should be in the sample app response string
    List<String> expectedServerNames =
        listManagedServersBeforeScale(clusterName, replicasBeforeScale);
    logger.info("expected server name list which should be in the sample app response: {0} before scale",
        expectedServerNames);

    if (replicasBeforeScale <= replicasAfterScale) {

      // scale up
      // check that the original managed server pod state is not changed during scaling the cluster
      for (int i = 1; i <= replicasBeforeScale; i++) {
        String manageServerPodName = manageServerPodNamePrefix + i;

        // check the original managed server pod state is not changed
        logger.info("Checking that the state of manged server pod {0} is not changed in namespace {1}",
            manageServerPodName, domainNamespace);
        podStateNotChangedDuringScalingCluster(manageServerPodName, domainUid, domainNamespace,
            listOfPodCreationTimestamp.get(i - 1));
      }

      // check that NGINX can access the sample apps from the original managed servers in the domain
      logger.info("Checking that NGINX can access the sample app from the original managed servers in the domain "
                  + "while the domain is scaling up.");
      assertThat(callWebAppAndCheckForServerNameInResponse(curlCmd, expectedServerNames, 50))
          .as("Verify NGINX can access the sample app from the original managed servers in the domain")
          .withFailMessage("NGINX can not access the sample app from one or more of the managed servers")
          .isTrue();

      // check that new managed server pods were created and wait for them to be ready
      for (int i = replicasBeforeScale + 1; i <= replicasAfterScale; i++) {
        String manageServerPodName = manageServerPodNamePrefix + i;

        // check new managed server pod was created
        logger.info("Checking that the new managed server pod {0} was created in namespace {1}",
            manageServerPodName, domainNamespace);
        checkPodCreated(manageServerPodName);

        // check new managed server pod is ready
        logger.info("Checking that the new managed server pod {0} is ready in namespace {1}",
            manageServerPodName, domainNamespace);
        checkPodReady(manageServerPodName);

        // check new managed server service was created
        logger.info("Checking that the new managed server service {0} was created in namespace {1}",
            manageServerPodName, domainNamespace);
        checkServiceCreated(manageServerPodName);

        // add the new managed server to the list
        expectedServerNames.add(clusterName + managedServerNameBase + i);
      }

      // check that NGINX can access the sample apps from new and original managed servers
      logger.info("Checking that NGINX can access the sample app from the new and original managed servers "
          + "in the domain after the cluster is scaled up.");
      assertThat(callWebAppAndCheckForServerNameInResponse(curlCmd, expectedServerNames, 50))
          .as("Verify NGINX can access the sample app from all managed servers in the domain")
          .withFailMessage("NGINX can not access the sample app from one or more of the managed servers")
          .isTrue();
    } else {
      // scale down
      // wait and check the pods are deleted
      for (int i = replicasBeforeScale; i > replicasAfterScale; i--) {
        logger.info("Checking that managed server pod {0} was deleted from namespace {1}",
            manageServerPodNamePrefix + i, domainNamespace);
        checkPodDeleted(manageServerPodNamePrefix + i);
        expectedServerNames.remove(clusterName + managedServerNameBase + i);
      }

      // check that NGINX can access the app from the remaining managed servers in the domain
      logger.info("Checking that NGINX can access the sample app from the remaining managed servers in the domain "
          + "after the cluster is scaled down.");
      assertThat(callWebAppAndCheckForServerNameInResponse(curlCmd, expectedServerNames, 50))
          .as("Verify NGINX can access the sample app from the remaining managed server in the domain")
          .withFailMessage("NGINX can not access the sample app from the remaining managed server")
          .isTrue();
    }
  }

  /**
   * Generate the curl command to access the sample app from the ingress controller.
   *
   * @param clusterName WebLogic cluster name which is the backend of the ingress
   * @return curl command string
   */
  private String generateCurlCmd(String clusterName) {
    return String.format("curl --silent --show-error --noproxy '*' -H 'host: %s' http://%s:%s/sample-war/index.jsp",
        domainUid + "." + clusterName + ".test", K8S_NODEPORT_HOST, nodeportshttp);
  }

  /**
   * Generate a server list which contains all managed servers in the cluster before scale.
   *
   * @param clusterName the name of the WebLogic cluster
   * @param replicasBeforeScale the replicas of WebLogic cluster before scale
   * @return list of managed servers in the cluster before scale
   */
  private List<String> listManagedServersBeforeScale(String clusterName, int replicasBeforeScale) {
    List<String> managedServerNames = new ArrayList<>();
    for (int i = 1; i <= replicasBeforeScale; i++) {
      managedServerNames.add(clusterName + managedServerNameBase + i);
    }

    return managedServerNames;
  }
}
