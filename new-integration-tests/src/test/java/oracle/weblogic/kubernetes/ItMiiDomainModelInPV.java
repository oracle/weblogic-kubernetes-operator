// Copyright (c) 2020, Oracle Corporation and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.weblogic.kubernetes;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import io.kubernetes.client.openapi.ApiException;
import io.kubernetes.client.openapi.models.V1Container;
import io.kubernetes.client.openapi.models.V1EnvVar;
import io.kubernetes.client.openapi.models.V1LocalObjectReference;
import io.kubernetes.client.openapi.models.V1ObjectMeta;
import io.kubernetes.client.openapi.models.V1PersistentVolumeClaimVolumeSource;
import io.kubernetes.client.openapi.models.V1Pod;
import io.kubernetes.client.openapi.models.V1PodSpec;
import io.kubernetes.client.openapi.models.V1SecretReference;
import io.kubernetes.client.openapi.models.V1Volume;
import io.kubernetes.client.openapi.models.V1VolumeMount;
import oracle.weblogic.domain.AdminServer;
import oracle.weblogic.domain.AdminService;
import oracle.weblogic.domain.Channel;
import oracle.weblogic.domain.Cluster;
import oracle.weblogic.domain.Configuration;
import oracle.weblogic.domain.Domain;
import oracle.weblogic.domain.DomainSpec;
import oracle.weblogic.domain.Model;
import oracle.weblogic.domain.ServerPod;
import oracle.weblogic.kubernetes.actions.impl.primitive.Kubernetes;
import oracle.weblogic.kubernetes.annotations.IntegrationTest;
import oracle.weblogic.kubernetes.annotations.Namespaces;
import oracle.weblogic.kubernetes.logging.LoggingFacade;
import oracle.weblogic.kubernetes.utils.TestUtils;
import org.awaitility.core.ConditionFactory;
import org.awaitility.core.ConditionTimeoutException;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static java.util.concurrent.TimeUnit.MINUTES;
import static java.util.concurrent.TimeUnit.SECONDS;
import static oracle.weblogic.kubernetes.TestConstants.ADMIN_SERVER_NAME_BASE;
import static oracle.weblogic.kubernetes.TestConstants.DOMAIN_API_VERSION;
import static oracle.weblogic.kubernetes.TestConstants.MANAGED_SERVER_NAME_BASE;
import static oracle.weblogic.kubernetes.TestConstants.MII_BASIC_IMAGE_NAME;
import static oracle.weblogic.kubernetes.TestConstants.REPO_DUMMY_VALUE;
import static oracle.weblogic.kubernetes.TestConstants.REPO_NAME;
import static oracle.weblogic.kubernetes.TestConstants.REPO_PASSWORD;
import static oracle.weblogic.kubernetes.TestConstants.REPO_REGISTRY;
import static oracle.weblogic.kubernetes.TestConstants.REPO_SECRET_NAME;
import static oracle.weblogic.kubernetes.TestConstants.REPO_USERNAME;
import static oracle.weblogic.kubernetes.TestConstants.WLS_DOMAIN_TYPE;
import static oracle.weblogic.kubernetes.actions.ActionConstants.APP_DIR;
import static oracle.weblogic.kubernetes.actions.ActionConstants.MODEL_DIR;
import static oracle.weblogic.kubernetes.actions.ActionConstants.WDT_VERSION;
import static oracle.weblogic.kubernetes.actions.ActionConstants.WIT_BUILD_DIR;
import static oracle.weblogic.kubernetes.actions.TestActions.createImage;
import static oracle.weblogic.kubernetes.actions.TestActions.defaultWitParams;
import static oracle.weblogic.kubernetes.actions.TestActions.dockerLogin;
import static oracle.weblogic.kubernetes.actions.TestActions.dockerPush;
import static oracle.weblogic.kubernetes.assertions.TestAssertions.podReady;
import static oracle.weblogic.kubernetes.utils.BuildApplication.buildApplication;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.checkPodReadyAndServiceExists;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.createDockerRegistrySecret;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.createDomainAndVerify;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.createPV;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.createPVC;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.createSecretWithUsernamePassword;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.installAndVerifyOperator;
import static oracle.weblogic.kubernetes.utils.FileUtils.checkDirectory;
import static oracle.weblogic.kubernetes.utils.ThreadSafeLogger.getLogger;
import static org.awaitility.Awaitility.with;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * This test is to verify shutdown rules when shutdown properties are defined at different levels (domain, cluster,
 * adminServer and managedServer level).
 */
@DisplayName("Verify shutdown rules when shutdown properties are defined at different levels")
@IntegrationTest
public class ItMiiDomainModelInPV {

  private static String domainNamespace = null;

  // domain constants
  private static String domainUid = "domain1";
  private static String adminServerName = "admin-server";
  private static String clusterName = "cluster-1";
  private static int replicaCount = 2;
  private static String adminServerPodName = domainUid + "-" + ADMIN_SERVER_NAME_BASE;
  private static String managedServerPodNamePrefix = domainUid + "-" + MANAGED_SERVER_NAME_BASE;

  private static String miiImage;
  private static String miiImageTag;
  private static String adminSecretName;
  private static String encryptionSecretName;

  private static String pvName = domainUid + "-pv"; // name of the persistent volume
  private static String pvcName = domainUid + "-pvc"; // name of the persistent volume claim

  private static Path clusterViewAppPath;
  private static String modelFile = "modelinpv-with-war.yaml";

  // create standard, reusable retry/backoff policy
  private static final ConditionFactory withStandardRetryPolicy
      = with().pollDelay(2, SECONDS)
          .and().with().pollInterval(10, SECONDS)
          .atMost(15, MINUTES).await();

  private static LoggingFacade logger = null;

  /**
   * 1. Get namespaces for operator and WebLogic domain. 2. Push MII image to registry. 3. Create WebLogic credential
   * secret and encryption secret. 4. Create configmap for independent managed server additions.
   *
   * @param namespaces list of namespaces injected by JUnit
   */
  @BeforeAll
  public static void initAll(@Namespaces(2) List<String> namespaces) {
    logger = getLogger();
    // get a unique operator namespace
    logger.info("Getting a unique namespace for operator");
    assertNotNull(namespaces.get(0), "Namespace list is null");
    String opNamespace = namespaces.get(0);

    // get a unique domain namespace
    logger.info("Getting a unique namespace for WebLogic domain");
    assertNotNull(namespaces.get(1), "Namespace list is null");
    domainNamespace = namespaces.get(1);

    // install and verify operator
    installAndVerifyOperator(opNamespace, domainNamespace);

    // build a new MII image with no domain
    miiImageTag = TestUtils.getDateAndTimeStamp();
    miiImage = MII_BASIC_IMAGE_NAME + ":" + miiImageTag;

    buildMIIWithEmptyDomain();

    if (!REPO_USERNAME.equals(REPO_DUMMY_VALUE)) {
      logger.info("docker login");
      withStandardRetryPolicy
          .conditionEvaluationListener(
              condition -> logger.info("Waiting for docker login to be successful"
                  + "(elapsed time {0} ms, remaining time {1} ms)",
                  condition.getElapsedTimeInMS(),
                  condition.getRemainingTimeInMS()))
          .until(() -> dockerLogin(REPO_REGISTRY, REPO_USERNAME, REPO_PASSWORD));
    }

    // push the images to repo
    logger.info("docker push image {0} to {1}", miiImage, REPO_NAME);
    if (!REPO_NAME.isEmpty()) {
      withStandardRetryPolicy
          .conditionEvaluationListener(condition -> logger.info("Waiting for docker push for image {0} to be successful"
          + "(elapsed time {1} ms, remaining time {2} ms)",
          miiImage,
          condition.getElapsedTimeInMS(),
          condition.getRemainingTimeInMS()))
          .until(() -> dockerPush(miiImage));
    }

    // create docker registry secret to pull the image from registry
    logger.info("Creating docker registry secret in namespace {0}", domainNamespace);
    createDockerRegistrySecret(domainNamespace);

    // create secret for admin credentials
    logger.info("Creating secret for admin credentials");
    adminSecretName = "weblogic-credentials";
    createSecretWithUsernamePassword(adminSecretName, domainNamespace, "weblogic", "welcome1");

    // create encryption secret
    logger.info("Creating encryption secret");
    encryptionSecretName = "encryptionsecret";
    createSecretWithUsernamePassword(encryptionSecretName, domainNamespace, "weblogicenc", "weblogicenc");

    createPV(pvName, domainUid, "ItMiiDomainModelInPV");
    createPVC(pvName, pvcName, domainUid, domainNamespace);

    // build the clusterview application
    Path distDir = buildApplication(Paths.get(APP_DIR, "clusterview"),
        null, null, "dist", domainNamespace);
    clusterViewAppPath = Paths.get(distDir.toString(), "clusterview.war");
    assertTrue(clusterViewAppPath.toFile().exists(), "Application archive is not available");

    V1Pod webLogicPod = setupPVPod(domainNamespace);
    try {
      //copy the model file to PV using the temp pod - we don't have access to PVROOT in Jenkins env
      Kubernetes.copyFileToPod(domainNamespace, webLogicPod.getMetadata().getName(), null,
          Paths.get(MODEL_DIR, modelFile),
          Paths.get("shared", modelFile));
      Kubernetes.copyFileToPod(domainNamespace, webLogicPod.getMetadata().getName(), null,
          clusterViewAppPath,
          Paths.get("shared", "clusterview.war"));
    } catch (ApiException | IOException ioex) {
      logger.info("Exception while copying file model file or application archive");
      fail("Failed to add model file or application archive to PV");
    }

  }

  private static void buildMIIWithEmptyDomain() {
    Path emptyModelFile = Paths.get(TestConstants.RESULTS_ROOT, "miitemp", "empty-wdt-model.yaml");
    assertDoesNotThrow(() -> Files.createDirectories(emptyModelFile.getParent()));
    emptyModelFile.toFile().delete();
    assertTrue(assertDoesNotThrow(() -> emptyModelFile.toFile().createNewFile()));
    final List<String> modelList = Collections.singletonList(emptyModelFile.toString());
    // Set additional environment variables for WIT
    checkDirectory(WIT_BUILD_DIR);
    Map<String, String> env = new HashMap<>();
    env.put("WLSIMG_BLDDIR", WIT_BUILD_DIR);
    createImage(defaultWitParams()
        .modelImageName(MII_BASIC_IMAGE_NAME)
        .modelImageTag(miiImageTag)
        .modelFiles(modelList)
        .wdtModelOnly(true)
        .wdtVersion(WDT_VERSION)
        .env(env)
        .redirect(true));
  }

  /**
   * Add shutdown options for servers at all levels: domain, admin server, cluster and managed server levels. Verify
   * individual specific level options takes precedence.
   */
  @Test
  @DisplayName("Verify shutdown rules when shutdown properties are defined at different levels ")
  public void testShutdownPropsAllLevels() {

    // create domain custom resource and verify all the pods came up
    Domain domain = buildDomainResource();
    createVerifyDomain(domain);
  }

  // create custom domain resource with different shutdownobject values for adminserver/cluster/independent ms
  private Domain buildDomainResource() {
    logger.info("Creating domain custom resource");
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
                    .value("-Djava.security.egd=file:/dev/./urandom "))
                .addVolumesItem(new V1Volume()
                    .name(pvName)
                    .persistentVolumeClaim(new V1PersistentVolumeClaimVolumeSource()
                        .claimName(pvcName)))
                .addVolumeMountsItem(new V1VolumeMount()
                    .mountPath("/shared")
                    .name(pvName)))
            .adminServer(new AdminServer()
                .serverStartState("RUNNING")
                .adminService(new AdminService()
                    .addChannelsItem(new Channel()
                        .channelName("default")
                        .nodePort(0))))
            .addClustersItem(new Cluster()
                .clusterName(clusterName)
                .replicas(replicaCount)
                .serverStartState("RUNNING"))
            .configuration(new Configuration()
                .model(new Model()
                    .withModelHome("/shared")
                    .domainType(WLS_DOMAIN_TYPE)
                    .runtimeEncryptionSecret(encryptionSecretName))));
    return domain;
  }

  // create domain resource and verify all the server pods are ready
  private void createVerifyDomain(Domain domain) {
    // create model in image domain
    logger.info("Creating model in image domain {0} in namespace {1} using docker image {2}",
        domainUid, domainNamespace, miiImage);
    createDomainAndVerify(domain, domainNamespace);

    // check that admin service/pod exists in the domain namespace
    logger.info("Checking that admin service/pod {0} exists in namespace {1}",
        adminServerPodName, domainNamespace);
    checkPodReadyAndServiceExists(adminServerPodName, domainUid, domainNamespace);

    for (int i = 1; i <= replicaCount; i++) {
      String managedServerPodName = managedServerPodNamePrefix + i;

      // check that ms service/pod exists in the domain namespace
      logger.info("Checking that clustered ms service/pod {0} exists in namespace {1}",
          managedServerPodName, domainNamespace);
      checkPodReadyAndServiceExists(managedServerPodName, domainUid, domainNamespace);
    }

  }

  private static V1Pod setupPVPod(String namespace) {

    // Create the temporary pod with oraclelinux image
    // oraclelinux:7-slim is not useful, missing tar utility
    final String podName = "pv-pod-" + namespace;
    V1Pod podBody = new V1Pod()
        .spec(new V1PodSpec()
            .containers(Arrays.asList(
                new V1Container()
                    .name("pv-container")
                    .image("oraclelinux:7")
                    .imagePullPolicy("IfNotPresent")
                    .volumeMounts(Arrays.asList(
                        new V1VolumeMount()
                            .name(pvName) // mount the persistent volume to /shared inside the pod
                            .mountPath("/shared")))
                    .addCommandItem("tailf")
                    .addArgsItem("/dev/null")))
            .volumes(Arrays.asList(
                new V1Volume()
                    .name(pvName) // the persistent volume that needs to be archived
                    .persistentVolumeClaim(
                        new V1PersistentVolumeClaimVolumeSource()
                            .claimName(pvcName))))) // the persistent volume claim used by the test
        .metadata(new V1ObjectMeta().name(podName))
        .apiVersion("v1")
        .kind("Pod");
    V1Pod pvPod = assertDoesNotThrow(() -> Kubernetes.createPod(namespace, podBody));

    try {
      withStandardRetryPolicy
          .conditionEvaluationListener(
              condition -> logger.info("Waiting for {0} to be ready in namespace {1}, "
                  + "(elapsed time {2} , remaining time {3}",
                  podName,
                  namespace,
                  condition.getElapsedTimeInMS(),
                  condition.getRemainingTimeInMS()))
          .until(podReady(podName, null, namespace));
    } catch (ConditionTimeoutException ex) {
      logger.warning("Condition not met", ex);
    }

    return pvPod;
  }
}
