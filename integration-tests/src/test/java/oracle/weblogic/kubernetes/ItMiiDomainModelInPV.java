// Copyright (c) 2020, 2024, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.weblogic.kubernetes;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import io.kubernetes.client.openapi.models.V1Container;
import io.kubernetes.client.openapi.models.V1LocalObjectReference;
import io.kubernetes.client.openapi.models.V1ObjectMeta;
import io.kubernetes.client.openapi.models.V1PersistentVolumeClaimVolumeSource;
import io.kubernetes.client.openapi.models.V1Pod;
import io.kubernetes.client.openapi.models.V1PodSpec;
import io.kubernetes.client.openapi.models.V1Volume;
import io.kubernetes.client.openapi.models.V1VolumeMount;
import oracle.weblogic.domain.DomainResource;
import oracle.weblogic.kubernetes.actions.impl.primitive.Kubernetes;
import oracle.weblogic.kubernetes.actions.impl.primitive.WitParams;
import oracle.weblogic.kubernetes.annotations.IntegrationTest;
import oracle.weblogic.kubernetes.annotations.Namespaces;
import oracle.weblogic.kubernetes.logging.LoggingFacade;
import oracle.weblogic.kubernetes.utils.CommonMiiTestUtils;
import oracle.weblogic.kubernetes.utils.ExecCommand;
import oracle.weblogic.kubernetes.utils.ExecResult;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import static oracle.weblogic.kubernetes.TestConstants.ADMIN_PASSWORD_DEFAULT;
import static oracle.weblogic.kubernetes.TestConstants.ADMIN_SERVER_NAME_BASE;
import static oracle.weblogic.kubernetes.TestConstants.ADMIN_USERNAME_DEFAULT;
import static oracle.weblogic.kubernetes.TestConstants.BASE_IMAGES_REPO;
import static oracle.weblogic.kubernetes.TestConstants.BASE_IMAGES_REPO_PASSWORD;
import static oracle.weblogic.kubernetes.TestConstants.BASE_IMAGES_REPO_SECRET_NAME;
import static oracle.weblogic.kubernetes.TestConstants.BASE_IMAGES_REPO_USERNAME;
import static oracle.weblogic.kubernetes.TestConstants.DOMAIN_IMAGES_REPO;
import static oracle.weblogic.kubernetes.TestConstants.IMAGE_PULL_POLICY;
import static oracle.weblogic.kubernetes.TestConstants.KUBERNETES_CLI;
import static oracle.weblogic.kubernetes.TestConstants.MANAGED_SERVER_NAME_BASE;
import static oracle.weblogic.kubernetes.TestConstants.MII_BASIC_IMAGE_NAME;
import static oracle.weblogic.kubernetes.TestConstants.OKD;
import static oracle.weblogic.kubernetes.TestConstants.OKE_CLUSTER;
import static oracle.weblogic.kubernetes.TestConstants.TRAEFIK_INGRESS_HTTP_HOSTPORT;
import static oracle.weblogic.kubernetes.TestConstants.WEBLOGIC_IMAGE_TO_USE_IN_SPEC;
import static oracle.weblogic.kubernetes.TestConstants.WLSIMG_BUILDER;
import static oracle.weblogic.kubernetes.actions.ActionConstants.APP_DIR;
import static oracle.weblogic.kubernetes.actions.ActionConstants.MODEL_DIR;
import static oracle.weblogic.kubernetes.actions.ActionConstants.WDT_VERSION;
import static oracle.weblogic.kubernetes.actions.ActionConstants.WIT_BUILD_DIR;
import static oracle.weblogic.kubernetes.actions.TestActions.createImage;
import static oracle.weblogic.kubernetes.actions.TestActions.defaultWitParams;
import static oracle.weblogic.kubernetes.actions.TestActions.getServiceNodePort;
import static oracle.weblogic.kubernetes.actions.TestActions.imagePush;
import static oracle.weblogic.kubernetes.actions.TestActions.imageRepoLogin;
import static oracle.weblogic.kubernetes.assertions.TestAssertions.doesImageExist;
import static oracle.weblogic.kubernetes.assertions.TestAssertions.podReady;
import static oracle.weblogic.kubernetes.utils.ApplicationUtils.verifyAdminServerRESTAccess;
import static oracle.weblogic.kubernetes.utils.BuildApplication.buildApplication;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.checkPodReadyAndServiceExists;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.createIngressHostRouting;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.getDateAndTimeStamp;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.getHostAndPort;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.getUniqueName;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.testUntil;
import static oracle.weblogic.kubernetes.utils.DomainUtils.createDomainAndVerify;
import static oracle.weblogic.kubernetes.utils.FileUtils.checkDirectory;
import static oracle.weblogic.kubernetes.utils.FileUtils.copyFileToPod;
import static oracle.weblogic.kubernetes.utils.ImageUtils.createBaseRepoSecret;
import static oracle.weblogic.kubernetes.utils.OKDUtils.createRouteForOKD;
import static oracle.weblogic.kubernetes.utils.OperatorUtils.installAndVerifyOperator;
import static oracle.weblogic.kubernetes.utils.PersistentVolumeUtils.createPV;
import static oracle.weblogic.kubernetes.utils.PersistentVolumeUtils.createPVC;
import static oracle.weblogic.kubernetes.utils.PersistentVolumeUtils.createfixPVCOwnerContainer;
import static oracle.weblogic.kubernetes.utils.PodUtils.execInPod;
import static oracle.weblogic.kubernetes.utils.PodUtils.getExternalServicePodName;
import static oracle.weblogic.kubernetes.utils.SecretUtils.createSecretWithUsernamePassword;
import static oracle.weblogic.kubernetes.utils.SecretUtils.createSecretsForImageRepos;
import static oracle.weblogic.kubernetes.utils.ThreadSafeLogger.getLogger;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * This test class verify creating a domain from model and application archive files stored in the persistent
 * volume.
 */
@DisplayName("Verify MII domain can be created from model file in PV location and custom wdtModelHome")
@IntegrationTest
@Tag("olcne-srg")
@Tag("kind-parallel")
@Tag("okd-wls-srg")
@Tag("oke-arm")
@Tag("oke-parallel")
public class ItMiiDomainModelInPV {

  private static String domainNamespace = null;

  // domain constants
  private static List<Parameters> params = new ArrayList<>();
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

  private static final String pvName = getUniqueName(domainUid1 + "-wdtmodel-pv-");
  private static final String pvcName = getUniqueName(domainUid1 + "-wdtmodel-pvc-");

  private static Path clusterViewAppPath;
  private static String modelFile = "modelinpv-with-war.yaml";
  private static String modelMountPath = "/u01/modelHome";

  private static LoggingFacade logger = null;

  private static String wlsImage;
  private static boolean isUseSecret;

  private static String adminSvcExtHost = null;
  private static String hostHeader;

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
    miiImageTagPV = getDateAndTimeStamp();
    miiImagePV = MII_BASIC_IMAGE_NAME + ":" + miiImageTagPV;

    // build a new MII image with no domain
    buildMIIandPushToRepo(MII_BASIC_IMAGE_NAME, miiImageTagPV, null);

    logger.info("Building image with custom wdt model home location");
    miiImageTagCustom = getDateAndTimeStamp();
    miiImageCustom = MII_BASIC_IMAGE_NAME + ":" + miiImageTagCustom;

    // build a new MII image with custom wdtHome
    buildMIIandPushToRepo(MII_BASIC_IMAGE_NAME, miiImageTagCustom, modelMountPath + "/model");

    params.add(new Parameters("domain2", miiImageCustom, 30500));
    params.add(new Parameters("domain1", miiImagePV, 30501));

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
    assertNotNull(pvPod, "pvPod is null");
    assertNotNull(pvPod.getMetadata(), "pvPod metadata is null");

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
    //Calls execInPod to change the ownership of files in PV - not valid in OKD
    if (!OKD) {
      execInPod(pvPod, null, true, argCommand);
    }
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
  @DisplayName("Create MII domain with model and application file from PV and custom wdtModelHome")
  @Tag("gate")
  @Tag("crio")
  void testMiiDomainWithModelAndApplicationInPV(Parameters params) {

    String domainUid = params.domainUid;
    String image = params.image;

    // create domain custom resource and verify all the pods came up
    logger.info("Creating domain custom resource with domainUid {0} and image {1}",
        domainUid, image);

    // HERE -- looking for where nodePort value is set

    DomainResource domainCR = CommonMiiTestUtils.createDomainResource(domainUid, domainNamespace,
        image, adminSecretName, createSecretsForImageRepos(domainNamespace), encryptionSecretName,
        2, List.of(clusterName), true, params.nodePort);
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
    if (TestConstants.KIND_CLUSTER
        && !TestConstants.WLSIMG_BUILDER.equals(TestConstants.WLSIMG_BUILDER_DEFAULT)) {
      hostHeader = createIngressHostRouting(domainNamespace, domainUid, adminServerName, 7001);
      assertDoesNotThrow(() -> verifyAdminServerRESTAccess("localhost", 
          TRAEFIK_INGRESS_HTTP_HOSTPORT, false, hostHeader));
    }
    //verify admin server accessibility and the health of cluster members
    verifyMemberHealth(adminServerPodName, managedServerNames, ADMIN_USERNAME_DEFAULT, ADMIN_PASSWORD_DEFAULT);
  }

  private record Parameters(String domainUid, String image, int nodePort) {}

  // generates the stream of objects used by parametrized test.
  private static Stream<Parameters> paramProvider() {
    return params.stream();
  }

  // create domain resource and verify all the server pods are ready
  private void createVerifyDomain(String domainUid, DomainResource domain,
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
      String user, String code) {

    logger.info("Checking the health of servers in cluster");

    testUntil(() -> {
      if (OKE_CLUSTER) {
        // In internal OKE env, verifyMemberHealth in admin server pod
        int adminPort = 7001;
        final String command = KUBERNETES_CLI + " exec -n "
            + domainNamespace + "  " + adminServerPodName + " -- curl http://"
            + adminServerPodName + ":"
            + adminPort + "/clusterview/ClusterViewServlet"
            + "\"?user=" + user
            + "&password=" + code + "\"";

        ExecResult result = null;
        try {
          result = ExecCommand.exec(command, true);
        } catch (IOException | InterruptedException ex) {
          logger.severe(ex.getMessage());
        }
        assertNotNull(result, "execResult is null");
        String response = result.stdout().trim();
        logger.info(response);
        boolean health = true;
        for (String managedServer : managedServerNames) {
          health = health && response.contains(managedServer + ":HEALTH_OK");
          if (health) {
            logger.info(managedServer + " is healthy");
          } else {
            logger.info(managedServer + " health is not OK or server not found");
          }
        }
        return health;
      } else {
        // In non-internal OKE env, verifyMemberHealth using adminSvcExtHost by sending HTTP request from local VM

        // TEST, HERE
        String extSvcPodName = getExternalServicePodName(adminServerPodName);
        logger.info("**** adminServerPodName={0}", adminServerPodName);
        logger.info("**** extSvcPodName={0}", extSvcPodName);

        adminSvcExtHost = createRouteForOKD(extSvcPodName, domainNamespace);
        logger.info("**** adminSvcExtHost={0}", adminSvcExtHost);
        logger.info("admin svc host = {0}", adminSvcExtHost);

        logger.info("Getting node port for default channel");
        int serviceNodePort = assertDoesNotThrow(()
            -> getServiceNodePort(domainNamespace, getExternalServicePodName(adminServerPodName), "default"),
            "Getting admin server node port failed");
        String hostAndPort = getHostAndPort(adminSvcExtHost, serviceNodePort);

        StringBuffer curlCmd = new StringBuffer("curl -vkg --noproxy '*' ");
        if (TestConstants.KIND_CLUSTER
            && !TestConstants.WLSIMG_BUILDER.equals(TestConstants.WLSIMG_BUILDER_DEFAULT)) {
          hostAndPort = "localhost:" + TRAEFIK_INGRESS_HTTP_HOSTPORT;
          curlCmd.append(" -H 'host: " + hostHeader + "' ");
        }
        logger.info("**** hostAndPort={0}", hostAndPort);
        String url = "\"http://" + hostAndPort
            + "/clusterview/ClusterViewServlet?user=" + user + "&password=" + code + "\"";
        curlCmd.append(url);
        logger.info("**** url={0}", curlCmd);

        ExecResult result = null;
        try {
          result = ExecCommand.exec(new String(curlCmd), true);
          getLogger().info("exitCode: {0}, \nstdout:\n{1}, \nstderr:\n{2}",
              result.exitValue(), result.stdout(), result.stderr());
        } catch (IOException | InterruptedException ex) {
          getLogger().info("Exception in curl request {0}", ex);
        }

        boolean health = true;
        assertNotNull(result, "result is null");
        for (String managedServer : managedServerNames) {
          health = health && result.stdout().contains(managedServer + ":HEALTH_OK");
          if (health) {
            logger.info(managedServer + " is healthy");
          } else {
            logger.info(managedServer + " health is not OK or server not found");
          }
        }
        return health;
      }
    },
        logger,
        "Verifying the health of all cluster members");
  }

  private static V1Pod setupWebLogicPod(String namespace) {
    // this secret is used only for non-kind cluster
    createBaseRepoSecret(namespace);

    final String podName = "weblogic-pv-pod-" + namespace;
    V1PodSpec podSpec = new V1PodSpec()
            .containers(Arrays.asList(
                new V1Container()
                    .name("weblogic-container")
                    .image(WEBLOGIC_IMAGE_TO_USE_IN_SPEC)
                    .imagePullPolicy(IMAGE_PULL_POLICY)
                    .addCommandItem("sleep")
                    .addArgsItem("600")
                    .volumeMounts(Arrays.asList(
                        new V1VolumeMount()
                            .name(pvName) // mount the persistent volume to /shared inside the pod
                            .mountPath(modelMountPath)))))
            .imagePullSecrets(Arrays.asList(new V1LocalObjectReference()
                .name(BASE_IMAGES_REPO_SECRET_NAME)))
            // the persistent volume claim used by the test
            .volumes(Arrays.asList(
                new V1Volume()
                    .name(pvName) // the persistent volume that needs to be archived
                    .persistentVolumeClaim(
                        new V1PersistentVolumeClaimVolumeSource()
                            .claimName(pvcName))));
    if (!OKD) {
      podSpec.initContainers(Arrays.asList(createfixPVCOwnerContainer(pvName, modelMountPath)));
    }

    V1Pod podBody = new V1Pod()
        .spec(podSpec)
        .metadata(new V1ObjectMeta().name(podName))
        .apiVersion("v1")
        .kind("Pod");

    V1Pod wlsPod = assertDoesNotThrow(() -> Kubernetes.createPod(namespace, podBody));

    testUntil(
        podReady(podName, null, namespace),
        logger,
        "{0} to be ready in namespace {1}",
        podName,
        namespace);

    return wlsPod;
  }

  /**
   * create a model in image with no domain and custom wdtModelHome push the image to repo.
   *
   * @param imageName name of the image
   * @param imageTag tag of the image
   * @param customWDTHome WDT home location to put the model file
   */
  public static void buildMIIandPushToRepo(String imageName, String imageTag, String customWDTHome) {
    logger = getLogger();
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
    String witTarget = ((OKD) ? "OpenShift" : "Default");
    createImage(defaultWitParams
        .modelImageName(imageName)
        .modelImageTag(imageTag)
        .modelFiles(modelList)
        .wdtModelOnly(true)
        .wdtVersion(WDT_VERSION)
        .target(witTarget)
        .env(env)
        .redirect(true));
    assertTrue(doesImageExist(imageTag),
        String.format("Image %s doesn't exist", imageName));
    imageRepoLoginAndPushImage(image);
  }

  /**
   * Login to repo and push image.
   *
   * @param image image to push to repo
   */
  public static void imageRepoLoginAndPushImage(String image) {
    logger = getLogger();
    // login to repo
    logger.info(WLSIMG_BUILDER + " login");
    testUntil(() -> imageRepoLogin(BASE_IMAGES_REPO, BASE_IMAGES_REPO_USERNAME, BASE_IMAGES_REPO_PASSWORD),
          logger,
          WLSIMG_BUILDER + " login to be successful");

    // push the image to repo
    if (!DOMAIN_IMAGES_REPO.isEmpty()) {
      logger.info(WLSIMG_BUILDER + " push image {0} to {1}", image, DOMAIN_IMAGES_REPO);
      testUntil(
          () -> imagePush(image),
          logger,
          WLSIMG_BUILDER + " push for image {0} to be successful",
          image);
    }
  }

}
