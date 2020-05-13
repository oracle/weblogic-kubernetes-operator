// Copyright (c) 2020, Oracle Corporation and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.weblogic.kubernetes.utils;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
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
import org.awaitility.core.ConditionFactory;

import static java.util.concurrent.TimeUnit.MINUTES;
import static java.util.concurrent.TimeUnit.SECONDS;
import static oracle.weblogic.kubernetes.TestConstants.DOMAIN_API_VERSION;
import static oracle.weblogic.kubernetes.TestConstants.DOMAIN_VERSION;
import static oracle.weblogic.kubernetes.TestConstants.GOOGLE_REPO_URL;
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
import static oracle.weblogic.kubernetes.actions.TestActions.createMiiImage;
import static oracle.weblogic.kubernetes.actions.TestActions.createSecret;
import static oracle.weblogic.kubernetes.actions.TestActions.createServiceAccount;
import static oracle.weblogic.kubernetes.actions.TestActions.defaultAppParams;
import static oracle.weblogic.kubernetes.actions.TestActions.defaultWitParams;
import static oracle.weblogic.kubernetes.actions.TestActions.dockerLogin;
import static oracle.weblogic.kubernetes.actions.TestActions.dockerPush;
import static oracle.weblogic.kubernetes.actions.TestActions.getOperatorImageName;
import static oracle.weblogic.kubernetes.actions.TestActions.installNginx;
import static oracle.weblogic.kubernetes.actions.TestActions.installOperator;
import static oracle.weblogic.kubernetes.assertions.TestAssertions.doesImageExist;
import static oracle.weblogic.kubernetes.assertions.TestAssertions.domainExists;
import static oracle.weblogic.kubernetes.assertions.TestAssertions.isHelmReleaseDeployed;
import static oracle.weblogic.kubernetes.assertions.TestAssertions.isNginxReady;
import static oracle.weblogic.kubernetes.assertions.TestAssertions.operatorIsReady;
import static oracle.weblogic.kubernetes.assertions.TestAssertions.podDoesNotExist;
import static oracle.weblogic.kubernetes.assertions.TestAssertions.podExists;
import static oracle.weblogic.kubernetes.assertions.TestAssertions.podReady;
import static oracle.weblogic.kubernetes.assertions.TestAssertions.serviceDoesNotExist;
import static oracle.weblogic.kubernetes.assertions.TestAssertions.serviceExists;
import static oracle.weblogic.kubernetes.extensions.LoggedTest.logger;
import static oracle.weblogic.kubernetes.utils.FileUtils.checkDirectory;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.with;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The common utility class for tests.
 */
public class CommonUtils {

  private static ConditionFactory withStandardRetryPolicy =
      with().pollDelay(2, SECONDS)
          .and().with().pollInterval(10, SECONDS)
          .atMost(5, MINUTES).await();

  /**
   * Install WebLogic operator and wait up to 5 minutes until the operator pod is ready.
   *
   * @param opNamespace the operator namespace in which the operator will be installed
   * @param domainNamespace the list of the domain namespaces which will be managed by the operator
   * @return the operator Helm installation parameters
   */
  public static HelmParams installAndVerifyOperator(String opNamespace,
                                                    String... domainNamespace) {

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
    logger.info("operator image name {0}", operatorImage);

    // Create docker registry secret in the operator namespace to pull the image from repository
    logger.info("Creating docker registry secret in namespace {0}", opNamespace);
    createDockerRegistrySecret(opNamespace);

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

    // wait for the operator is ready
    logger.info("Wait for the operator pod is ready in namespace {0}", opNamespace);
    withStandardRetryPolicy
        .conditionEvaluationListener(
            condition -> logger.info("Waiting for operator to be running in namespace {0} "
                    + "(elapsed time {1}ms, remaining time {2}ms)",
                opNamespace,
                condition.getElapsedTimeInMS(),
                condition.getRemainingTimeInMS()))
        .until(operatorIsReady(opNamespace));

    return opHelmParams;
  }

  /**
   * Install NGINX and wait up to 5 minutes until the NGINX pod is ready.
   *
   * @param nginxNamespace the namespace in which the NGINX will be installed
   * @param nodeportshttp the http nodeport of NGINX
   * @param nodeportshttps the https nodeport of NGINX
   * @return the NGINX Helm installation parameters
   */
  public static HelmParams installAndVerifyNginx(String nginxNamespace,
                                           int nodeportshttp,
                                           int nodeportshttps) {

    // Helm install parameters
    HelmParams nginxHelmParams = new HelmParams()
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
        .as("Test NGINX installation succeeds")
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

    return nginxHelmParams;
  }

  /**
   * Create a model in image domain in the specified namespace and wait up to 5 minutes until the domain exists
   * in the namespace.
   *
   * @param miiImage the docker image of the model-in-image domain
   * @param domainUid the domain uid for the model-in-image domain
   * @param domainNamespace the namespace in which the domain will be created
   * @param clusters list of the oracle.weblogic.domain.Cluster objects used to create the domain
   * @param domainType the domain type for model-in-image domain, accepted value: WLS, JRF and RestrictedJRF
   */
  public static void createMiiDomain(String miiImage,
                                     String domainUid,
                                     String domainNamespace,
                                     List<Cluster> clusters,
                                     String domainType) {

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

    // create docker registry secret to pull the image from repository
    logger.info("Creating docker registry secret in namespace {0}", domainNamespace);
    createDockerRegistrySecret(domainNamespace);

    // create secret for admin credentials
    logger.info("Creating secret for admin credentials");
    String adminSecretName = "weblogic-credentials";
    Map<String, String> adminSecretMap = new HashMap<>();
    adminSecretMap.put("username", "weblogic");
    adminSecretMap.put("password", "welcome1");
    boolean secretCreated = assertDoesNotThrow(() -> createSecret(new V1Secret()
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

    // create the domain CR
    Domain domain = new Domain()
        .apiVersion(DOMAIN_API_VERSION)
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
            .clusters(clusters)
            .configuration(new Configuration()
                .model(new Model()
                    .domainType(domainType)
                    .runtimeEncryptionSecret(encryptionSecretName))));

    logger.info("Creating domain custom resource for domainUid {0} in namespace {1}",
        domainUid, domainNamespace);
    assertTrue(assertDoesNotThrow(() -> createDomainCustomResource(domain),
        String.format("Create domain custom resource failed with ApiException for %s in namespace %s",
            domainUid, domainNamespace)),
        String.format("Create domain custom resource failed with ApiException for %s in namespace %s",
            domainUid, domainNamespace));

    // wait for the domain to exist
    logger.info("Checking for domain custom resource in namespace {0}", domainNamespace);
    withStandardRetryPolicy
        .conditionEvaluationListener(
            condition -> logger.info("Waiting for domain {0} to be created in namespace {1} "
                    + "(elapsed time {2}ms, remaining time {3}ms)",
                domainUid,
                domainNamespace,
                condition.getElapsedTimeInMS(),
                condition.getRemainingTimeInMS()))
        .until(domainExists(domainUid, DOMAIN_VERSION, domainNamespace));
  }

  /**
   * Check pod is created.
   *
   * @param podName pod name to check
   * @param domainUid the domain in which the pod exists
   * @param domainNamespace the domain namespace in which the domain exists
   */
  public static void checkPodCreated(String podName, String domainUid, String domainNamespace) {
    withStandardRetryPolicy
        .conditionEvaluationListener(
            condition -> logger.info("Waiting for pod {0} to be created in namespace {1} "
                    + "(elapsed time {2}ms, remaining time {3}ms)",
                podName,
                domainNamespace,
                condition.getElapsedTimeInMS(),
                condition.getRemainingTimeInMS()))
        .until(assertDoesNotThrow(() -> podExists(podName, domainUid, domainNamespace),
            String.format("podExists failed with ApiException for pod %s in namespace in %s",
                podName, domainNamespace)));
  }

  /**
   * Check pod is ready.
   *
   * @param podName pod name to check
   * @param domainUid the domain in which the pod exists
   * @param domainNamespace the domain namespace in which the domain exists
   */
  public static void checkPodReady(String podName, String domainUid, String domainNamespace) {
    withStandardRetryPolicy
        .conditionEvaluationListener(
            condition -> logger.info("Waiting for pod {0} to be ready in namespace {1} "
                    + "(elapsed time {2}ms, remaining time {3}ms)",
                podName,
                domainNamespace,
                condition.getElapsedTimeInMS(),
                condition.getRemainingTimeInMS()))
        .until(assertDoesNotThrow(() -> podReady(podName, domainUid, domainNamespace),
            String.format("podReady failed with ApiException for pod %s in namespace in %s",
               podName, domainNamespace)));
  }

  /**
   * Check service is created.
   *
   * @param serviceName service name to check
   * @param domainNamespace the domain namespace in which the service exists
   */
  public static void checkServiceCreated(String serviceName, String domainNamespace) {
    withStandardRetryPolicy
        .conditionEvaluationListener(
            condition -> logger.info("Waiting for service {0} to be created in namespace {1} "
                    + "(elapsed time {2}ms, remaining time {3}ms)",
                serviceName,
                domainNamespace,
                condition.getElapsedTimeInMS(),
                condition.getRemainingTimeInMS()))
        .until(assertDoesNotThrow(() -> serviceExists(serviceName, null, domainNamespace),
            String.format("serviceExists failed with ApiException for service %s in namespace in %s",
                serviceName, domainNamespace)));
  }

  /**
   * Check pod was deleted.
   *
   * @param podName pod name to check
   * @param domainUid the domain uid in which the pod exists
   * @param domainNamespace the domain namespace in which the domain exists
   */
  public static void checkPodDeleted(String podName, String domainUid, String domainNamespace) {
    withStandardRetryPolicy
        .conditionEvaluationListener(
            condition -> logger.info("Waiting for pod {0} to be deleted in namespace {1} "
                    + "(elapsed time {2}ms, remaining time {3}ms)",
                podName,
                domainNamespace,
                condition.getElapsedTimeInMS(),
                condition.getRemainingTimeInMS()))
        .until(assertDoesNotThrow(() -> podDoesNotExist(podName, domainUid, domainNamespace),
            String.format("podDoesNotExist failed with ApiException for pod %s in namespace in %s",
                podName, domainNamespace)));
  }

  /**
   * Check service was deleted.
   *
   * @param serviceName service name to check
   * @param domainNamespace the namespace in which to check whether the service was deleted
   */
  public static void checkServiceDeleted(String serviceName, String domainNamespace) {
    withStandardRetryPolicy
        .conditionEvaluationListener(
            condition -> logger.info("Waiting for pod {0} to be deleted in namespace {1} "
                    + "(elapsed time {2}ms, remaining time {3}ms)",
                serviceName,
                domainNamespace,
                condition.getElapsedTimeInMS(),
                condition.getRemainingTimeInMS()))
        .until(serviceDoesNotExist(serviceName, null, domainNamespace));
  }

  /**
   * Create a Docker image for model in image domain.
   *
   * @param imageNameBase the base image name used in local or to construct the image name in repository
   * @param wdtModelFile the WDT model file used to build the Docker image
   * @param appName the sample application name used to build sample app ear file in WDT model file
   * @return image name with tag
   */
  public static  String createImageAndVerify(String imageNameBase,
                                             String wdtModelFile,
                                             String appName) {

    // create unique image name with date
    DateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd");
    Date date = new Date();
    final String imageTag = dateFormat.format(date) + "-" + System.currentTimeMillis();
    // Add repository name in image name for Jenkins runs
    final String imageName = REPO_NAME + imageNameBase;
    final String image = imageName + ":" + imageTag;

    // build the model file list
    final List<String> modelList = Collections.singletonList(MODEL_DIR + "/" + wdtModelFile);

    // build an application archive using what is in resources/apps/APP_NAME
    assertTrue(buildAppArchive(defaultAppParams()
        .srcDir(appName)), String.format("Failed to create app archive for %s", appName));

    // build the archive list
    String zipFile = String.format("%s/%s.zip", ARCHIVE_DIR, appName);
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
   * Create a docker registry secret in the specified namespace.
   *
   * @param namespace the namespace in which the secret will be created
   */
  private static void createDockerRegistrySecret(String namespace) {

    // Create docker registry secret in the namespace to pull the image from repository
    JsonObject dockerConfigJsonObject = createDockerConfigJson(
        REPO_USERNAME, REPO_PASSWORD, REPO_EMAIL, REPO_REGISTRY);
    String dockerConfigJson = dockerConfigJsonObject.toString();

    // Create the V1Secret configuration
    V1Secret repoSecret = new V1Secret()
        .metadata(new V1ObjectMeta()
            .name(REPO_SECRET_NAME)
            .namespace(namespace))
        .type("kubernetes.io/dockerconfigjson")
        .putDataItem(".dockerconfigjson", dockerConfigJson.getBytes());

    boolean secretCreated = assertDoesNotThrow(() -> createSecret(repoSecret),
        String.format("createSecret failed for %s", REPO_SECRET_NAME));
    assertTrue(secretCreated, String.format("createSecret failed while creating secret %s in namespace %s",
        REPO_SECRET_NAME, namespace));
  }
}
