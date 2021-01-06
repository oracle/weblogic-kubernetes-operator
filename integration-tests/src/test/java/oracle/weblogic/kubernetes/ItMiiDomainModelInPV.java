// Copyright (c) 2020, 2021, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.weblogic.kubernetes;

import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.Callable;
import java.util.stream.Stream;

import io.kubernetes.client.openapi.models.V1Container;
import io.kubernetes.client.openapi.models.V1LocalObjectReference;
import io.kubernetes.client.openapi.models.V1ObjectMeta;
import io.kubernetes.client.openapi.models.V1PersistentVolumeClaimVolumeSource;
import io.kubernetes.client.openapi.models.V1Pod;
import io.kubernetes.client.openapi.models.V1PodSpec;
import io.kubernetes.client.openapi.models.V1Volume;
import io.kubernetes.client.openapi.models.V1VolumeMount;
import oracle.weblogic.domain.Domain;
import oracle.weblogic.kubernetes.actions.impl.primitive.Kubernetes;
import oracle.weblogic.kubernetes.actions.impl.primitive.WitParams;
import oracle.weblogic.kubernetes.annotations.IntegrationTest;
import oracle.weblogic.kubernetes.annotations.Namespaces;
import oracle.weblogic.kubernetes.logging.LoggingFacade;
import oracle.weblogic.kubernetes.utils.CommonMiiTestUtils;
import oracle.weblogic.kubernetes.utils.OracleHttpClient;
import oracle.weblogic.kubernetes.utils.TestUtils;
import org.awaitility.core.ConditionFactory;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import static java.util.concurrent.TimeUnit.MINUTES;
import static java.util.concurrent.TimeUnit.SECONDS;
import static oracle.weblogic.kubernetes.TestConstants.ADMIN_PASSWORD_DEFAULT;
import static oracle.weblogic.kubernetes.TestConstants.ADMIN_SERVER_NAME_BASE;
import static oracle.weblogic.kubernetes.TestConstants.ADMIN_USERNAME_DEFAULT;
import static oracle.weblogic.kubernetes.TestConstants.BASE_IMAGES_REPO_SECRET;
import static oracle.weblogic.kubernetes.TestConstants.DOMAIN_IMAGES_REPO;
import static oracle.weblogic.kubernetes.TestConstants.K8S_NODEPORT_HOST;
import static oracle.weblogic.kubernetes.TestConstants.MANAGED_SERVER_NAME_BASE;
import static oracle.weblogic.kubernetes.TestConstants.MII_BASIC_IMAGE_NAME;
import static oracle.weblogic.kubernetes.TestConstants.OCIR_PASSWORD;
import static oracle.weblogic.kubernetes.TestConstants.OCIR_REGISTRY;
import static oracle.weblogic.kubernetes.TestConstants.OCIR_SECRET_NAME;
import static oracle.weblogic.kubernetes.TestConstants.OCIR_USERNAME;
import static oracle.weblogic.kubernetes.TestConstants.OKE_CLUSTER;
import static oracle.weblogic.kubernetes.TestConstants.REPO_DUMMY_VALUE;
import static oracle.weblogic.kubernetes.TestConstants.WEBLOGIC_IMAGE_TO_USE_IN_SPEC;
import static oracle.weblogic.kubernetes.actions.ActionConstants.APP_DIR;
import static oracle.weblogic.kubernetes.actions.ActionConstants.MODEL_DIR;
import static oracle.weblogic.kubernetes.actions.ActionConstants.WDT_VERSION;
import static oracle.weblogic.kubernetes.actions.ActionConstants.WIT_BUILD_DIR;
import static oracle.weblogic.kubernetes.actions.TestActions.createImage;
import static oracle.weblogic.kubernetes.actions.TestActions.defaultWitParams;
import static oracle.weblogic.kubernetes.actions.TestActions.dockerLogin;
import static oracle.weblogic.kubernetes.actions.TestActions.dockerPush;
import static oracle.weblogic.kubernetes.actions.TestActions.getServiceNodePort;
import static oracle.weblogic.kubernetes.assertions.TestAssertions.doesImageExist;
import static oracle.weblogic.kubernetes.assertions.TestAssertions.podReady;
import static oracle.weblogic.kubernetes.utils.BuildApplication.buildApplication;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.checkPodReadyAndServiceExists;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.createDomainAndVerify;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.createOcirRepoSecret;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.createPV;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.createPVC;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.createSecretForBaseImages;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.createSecretWithUsernamePassword;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.createfixPVCOwnerContainer;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.execInPod;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.getExternalServicePodName;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.installAndVerifyOperator;
import static oracle.weblogic.kubernetes.utils.FileUtils.checkDirectory;
import static oracle.weblogic.kubernetes.utils.FileUtils.copyFileToPod;
import static oracle.weblogic.kubernetes.utils.ThreadSafeLogger.getLogger;
import static org.awaitility.Awaitility.with;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * This test class verify creating a domain from model and application archive files stored in the persistent
 * volume.
 */
@DisplayName("Verify MII domain can be created from model file in PV location and custom wdtModelHome")
@IntegrationTest
public class ItMiiDomainModelInPV {

  private static String domainNamespace = null;

  // domain constants
  private static Map<String, String> params = new HashMap<>();
  private static String domainUid1 = "domain1";
  private static String domainUid2 = "domain2";
  private static String adminServerName = "admin-server";
  private static String clusterName = "cluster-1";
  private static int replicaCount = 2;

  private static String miiImagePV;
  private static String miiImageTagPV;
  private static String miiImageCustom;
  private static String miiImageTagCustom;
  private static String adminSecretName;
  private static String encryptionSecretName;

  private static String pvName = domainUid1 + "-wdtmodel-pv"; // name of the persistent volume
  private static String pvcName = domainUid1 + "-wdtmodel-pvc"; // name of the persistent volume claim

  private static Path clusterViewAppPath;
  private static String modelFile = "modelinpv-with-war.yaml";
  private static String modelMountPath = "/u01/modelHome";

  // create standard, reusable retry/backoff policy
  private static final ConditionFactory withStandardRetryPolicy
      = with().pollDelay(2, SECONDS)
          .and().with().pollInterval(10, SECONDS)
          .atMost(15, MINUTES).await();

  private static LoggingFacade logger = null;

  private static String wlsImage;
  private static boolean isUseSecret;


  /**
   * 1. Get namespaces for operator and WebLogic domain.
   * 2. Create operator.
   * 3. Build a MII with no domain, MII with custom wdtModelHome and push it to repository.
   * 4. Create WebLogic credential and model encryption secrets
   * 5. Create PV and PVC to store model and application files.
   * 6. Copy the model file and application files to PV.
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

    // get a unique domain1 namespace
    logger.info("Getting a unique namespace for WebLogic domains");
    assertNotNull(namespaces.get(1), "Namespace list is null");
    domainNamespace = namespaces.get(1);

    // install and verify operator
    installAndVerifyOperator(opNamespace, domainNamespace);

    logger.info("Building image with empty model file");
    miiImageTagPV = TestUtils.getDateAndTimeStamp();
    miiImagePV = MII_BASIC_IMAGE_NAME + ":" + miiImageTagPV;

    // build a new MII image with no domain
    buildMIIandPushToRepo(MII_BASIC_IMAGE_NAME, miiImageTagPV, null);

    logger.info("Building image with custom wdt model home location");
    miiImageTagCustom = TestUtils.getDateAndTimeStamp();
    miiImageCustom = MII_BASIC_IMAGE_NAME + ":" + miiImageTagCustom;

    // build a new MII image with custom wdtHome
    buildMIIandPushToRepo(MII_BASIC_IMAGE_NAME, miiImageTagCustom, modelMountPath + "/model");

    params.put("domain2", miiImageCustom);
    params.put("domain1", miiImagePV);

    // create docker registry secret to pull the image from registry
    // this secret is used only for non-kind cluster
    logger.info("Creating docker registry secret in namespace {0}", domainNamespace);
    createOcirRepoSecret(domainNamespace);

    // create secret for admin credentials
    logger.info("Creating secret for admin credentials");
    adminSecretName = "weblogic-credentials";
    createSecretWithUsernamePassword(adminSecretName, domainNamespace, ADMIN_USERNAME_DEFAULT, ADMIN_PASSWORD_DEFAULT);

    // create model encryption secret
    logger.info("Creating encryption secret");
    encryptionSecretName = "encryptionsecret";
    createSecretWithUsernamePassword(encryptionSecretName, domainNamespace, "weblogicenc", "weblogicenc");

    // create the PV and PVC to store application and model files
    createPV(pvName, domainUid1, "ItMiiDomainModelInPV");
    createPVC(pvName, pvcName, domainUid1, domainNamespace);

    // build the clusterview application
    Path distDir = buildApplication(Paths.get(APP_DIR, "clusterview"),
        null, null, "dist", domainNamespace);
    clusterViewAppPath = Paths.get(distDir.toString(), "clusterview.war");
    assertTrue(clusterViewAppPath.toFile().exists(), "Application archive is not available");

    logger.info("Setting up WebLogic pod to access PV");
    V1Pod pvPod = setupWebLogicPod(domainNamespace);

    logger.info("Creating directory {0} in PV", modelMountPath + "/applications");
    execInPod(pvPod, null, true, "mkdir -p " + modelMountPath + "/applications");

    logger.info("Creating directory {0} in PV", modelMountPath + "/model");
    execInPod(pvPod, null, true, "mkdir -p " + modelMountPath + "/model");

    //copy the model file to PV using the temp pod - we don't have access to PVROOT in Jenkins env
    logger.info("Copying model file {0} to pv directory {1}",
        Paths.get(MODEL_DIR, modelFile).toString(), modelMountPath + "/model", modelFile);
    assertDoesNotThrow(() -> copyFileToPod(domainNamespace, pvPod.getMetadata().getName(), null,
        Paths.get(MODEL_DIR, modelFile), Paths.get(modelMountPath + "/model", modelFile)),
        "Copying file to pod failed");

    logger.info("Copying application file {0} to pv directory {1}",
        clusterViewAppPath.toString(), modelMountPath + "/applications", "clusterview.war");
    assertDoesNotThrow(() -> copyFileToPod(domainNamespace, pvPod.getMetadata().getName(), null,
        clusterViewAppPath, Paths.get(modelMountPath + "/applications", "clusterview.war")),
        "Copying file to pod failed");

    logger.info("Changing file ownership {0} to oracle:root in PV", modelMountPath);
    String argCommand = "chown -R 1000:root " + modelMountPath;
    if (OKE_CLUSTER) {
      argCommand = "chown 1000:root " + modelMountPath
          + "/. && find "
          + modelMountPath
          + "/. -maxdepth 1 ! -name '.snapshot' ! -name '.' -print0 | xargs -r -0  chown -R 1000:root";
    }
    execInPod(pvPod, null, true, argCommand);
  }

  /**
   * Test domain creation from model file stored in PV. https://oracle.github.io/weblogic-kubernetes-operator
       /userguide/managing-domains/domain-resource/#domain-spec-elements
    1.Create the domain custom resource using mii with no domain and specifying a PV location for modelHome
    2.Create the domain custom resource using mii with custom wdt model home in a pv location
    3. Verify the domain creation is successful and application is accessible.
    4. Repeat the test the above test using image created with custom wdtModelHome.
   * @param params domain name and image parameters
   */
  @ParameterizedTest
  @MethodSource("paramProvider")
  @DisplayName("Create MII domain with model and application file from PV and custon wdtModelHome")
  public void testMiiDomainWithModelAndApplicationInPV(Entry<String, String> params) {

    String domainUid = params.getKey();
    String image = params.getValue();

    // create domain custom resource and verify all the pods came up
    logger.info("Creating domain custom resource with domainUid {0} and image {1}",
        domainUid, image);
    Domain domainCR = CommonMiiTestUtils.createDomainResource(domainUid, domainNamespace,
        image, adminSecretName, OCIR_SECRET_NAME, encryptionSecretName, replicaCount, clusterName);
    domainCR.spec().configuration().model().withModelHome(modelMountPath + "/model");
    domainCR.spec().serverPod()
        .addVolumesItem(new V1Volume()
            .name(pvName)
            .persistentVolumeClaim(new V1PersistentVolumeClaimVolumeSource()
                .claimName(pvcName)))
        .addVolumeMountsItem(new V1VolumeMount()
            .mountPath(modelMountPath)
            .name(pvName));

    String adminServerPodName = domainUid + "-" + ADMIN_SERVER_NAME_BASE;
    String managedServerPodNamePrefix = domainUid + "-" + MANAGED_SERVER_NAME_BASE;

    logger.info("Creating domain {0} with model in image {1} in namespace {2}",
        domainUid, image, domainNamespace);
    createVerifyDomain(domainUid, domainCR, adminServerPodName, managedServerPodNamePrefix);

    List<String> managedServerNames = new ArrayList<String>();
    for (int i = 1; i <= replicaCount; i++) {
      managedServerNames.add(MANAGED_SERVER_NAME_BASE + i);
    }

    //verify admin server accessibility and the health of cluster members
    verifyMemberHealth(adminServerPodName, managedServerNames, ADMIN_USERNAME_DEFAULT, ADMIN_PASSWORD_DEFAULT);

  }

  // generates the stream of objects used by parametrized test.
  private static Stream<Entry<String, String>> paramProvider() {
    return params.entrySet().stream();
  }

  // create domain resource and verify all the server pods are ready
  private void createVerifyDomain(String domainUid, Domain domain,
      String adminServerPodName, String managedServerPodNamePrefix) {
    // create model in image domain
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

  private static void verifyMemberHealth(String adminServerPodName, List<String> managedServerNames,
      String user, String password) {

    logger.info("Getting node port for default channel");
    int serviceNodePort = assertDoesNotThrow(()
        -> getServiceNodePort(domainNamespace, getExternalServicePodName(adminServerPodName), "default"),
        "Getting admin server node port failed");

    logger.info("Checking the health of servers in cluster");
    String url = "http://" + K8S_NODEPORT_HOST + ":" + serviceNodePort
        + "/clusterview/ClusterViewServlet?user=" + user + "&password=" + password;

    withStandardRetryPolicy.conditionEvaluationListener(
        condition -> logger.info("Verifying the health of all cluster members"
            + "(elapsed time {0} ms, remaining time {1} ms)",
            condition.getElapsedTimeInMS(),
            condition.getRemainingTimeInMS()))
        .until((Callable<Boolean>) () -> {
          HttpResponse<String> response = assertDoesNotThrow(() -> OracleHttpClient.get(url, true));
          assertEquals(200, response.statusCode(), "Status code not equals to 200");
          boolean health = true;
          for (String managedServer : managedServerNames) {
            health = health && response.body().contains(managedServer + ":HEALTH_OK");
            if (health) {
              logger.info(managedServer + " is healthy");
            } else {
              logger.info(managedServer + " health is not OK or server not found");
            }
          }
          return health;
        });
  }

  private static V1Pod setupWebLogicPod(String namespace) {
    // this secret is used only for non-kind cluster
    createSecretForBaseImages(namespace);

    final String podName = "weblogic-pv-pod-" + namespace;
    V1Pod podBody = new V1Pod()
        .spec(new V1PodSpec()
            .initContainers(Arrays.asList(createfixPVCOwnerContainer(pvName, modelMountPath)))
            .containers(Arrays.asList(
                new V1Container()
                    .name("weblogic-container")
                    .image(WEBLOGIC_IMAGE_TO_USE_IN_SPEC)
                    .imagePullPolicy("IfNotPresent")
                    .addCommandItem("sleep")
                    .addArgsItem("600")
                    .volumeMounts(Arrays.asList(
                        new V1VolumeMount()
                            .name(pvName) // mount the persistent volume to /shared inside the pod
                            .mountPath(modelMountPath)))))
            .imagePullSecrets(Arrays.asList(new V1LocalObjectReference()
                .name(BASE_IMAGES_REPO_SECRET)))
            // the persistent volume claim used by the test
            .volumes(Arrays.asList(
                new V1Volume()
                    .name(pvName) // the persistent volume that needs to be archived
                    .persistentVolumeClaim(
                        new V1PersistentVolumeClaimVolumeSource()
                            .claimName(pvcName)))))
        .metadata(new V1ObjectMeta().name(podName))
        .apiVersion("v1")
        .kind("Pod");

    V1Pod wlsPod = assertDoesNotThrow(() -> Kubernetes.createPod(namespace, podBody));

    withStandardRetryPolicy
        .conditionEvaluationListener(
            condition -> logger.info("Waiting for {0} to be ready in namespace {1}, "
                + "(elapsed time {2} , remaining time {3}",
                podName,
                namespace,
                condition.getElapsedTimeInMS(),
                condition.getRemainingTimeInMS()))
        .until(podReady(podName, null, namespace));

    return wlsPod;
  }

  // create a model in image with no domain and custom wdtModelHome
  // push the image to repo
  private static void buildMIIandPushToRepo(String imageName, String imageTag, String customWDTHome) {
    final String image = imageName + ":" + imageTag;
    logger.info("Building image {0}", image);
    Path emptyModelFile = Paths.get(TestConstants.RESULTS_ROOT, "miitemp", "empty-wdt-model.yaml");
    assertDoesNotThrow(() -> Files.createDirectories(emptyModelFile.getParent()));
    emptyModelFile.toFile().delete();
    assertTrue(assertDoesNotThrow(() -> emptyModelFile.toFile().createNewFile()));
    final List<String> modelList = Collections.singletonList(emptyModelFile.toString());
    // Set additional environment variables for WIT
    checkDirectory(WIT_BUILD_DIR);
    Map<String, String> env = new HashMap<>();
    env.put("WLSIMG_BLDDIR", WIT_BUILD_DIR);
    WitParams defaultWitParams = defaultWitParams();
    if (customWDTHome != null) {
      defaultWitParams.wdtModelHome(customWDTHome);
    }
    createImage(defaultWitParams
        .modelImageName(imageName)
        .modelImageTag(imageTag)
        .modelFiles(modelList)
        .wdtModelOnly(true)
        .wdtVersion(WDT_VERSION)
        .env(env)
        .redirect(true));
    assertTrue(doesImageExist(imageTag),
        String.format("Image %s doesn't exist", imageName));
    dockerLoginAndPushImage(image);
  }

  private static void dockerLoginAndPushImage(String image) {
    // login to docker
    if (!OCIR_USERNAME.equals(REPO_DUMMY_VALUE)) {
      logger.info("docker login");
      withStandardRetryPolicy
          .conditionEvaluationListener(
              condition -> logger.info("Waiting for docker login to be successful"
                  + "(elapsed time {0} ms, remaining time {1} ms)",
                  condition.getElapsedTimeInMS(),
                  condition.getRemainingTimeInMS()))
          .until(() -> dockerLogin(OCIR_REGISTRY, OCIR_USERNAME, OCIR_PASSWORD));
    }

    // push the image to repo
    if (!DOMAIN_IMAGES_REPO.isEmpty()) {
      logger.info("docker push image {0} to {1}", image, DOMAIN_IMAGES_REPO);
      withStandardRetryPolicy
          .conditionEvaluationListener(condition -> logger.info("Waiting for docker push for image {0} to be successful"
          + "(elapsed time {1} ms, remaining time {2} ms)",
          image,
          condition.getElapsedTimeInMS(),
          condition.getRemainingTimeInMS()))
          .until(() -> dockerPush(image));
    }
  }
}
