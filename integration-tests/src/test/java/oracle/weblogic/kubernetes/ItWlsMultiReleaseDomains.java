// Copyright (c) 2025, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.weblogic.kubernetes;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.stream.Stream;

import io.kubernetes.client.openapi.models.V1EnvVar;
import io.kubernetes.client.openapi.models.V1LocalObjectReference;
import io.kubernetes.client.openapi.models.V1ObjectMeta;
import io.kubernetes.client.openapi.models.V1PodSecurityContext;
import io.kubernetes.client.openapi.models.V1ResourceRequirements;
import oracle.weblogic.domain.Configuration;
import oracle.weblogic.domain.DomainResource;
import oracle.weblogic.domain.DomainSpec;
import oracle.weblogic.domain.Model;
import oracle.weblogic.domain.ServerPod;
import oracle.weblogic.kubernetes.annotations.IntegrationTest;
import oracle.weblogic.kubernetes.annotations.Namespaces;
import oracle.weblogic.kubernetes.logging.LoggingFacade;
import org.awaitility.core.ConditionFactory;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import static java.util.concurrent.TimeUnit.MINUTES;
import static java.util.concurrent.TimeUnit.SECONDS;
import static oracle.weblogic.kubernetes.TestConstants.ADMIN_PASSWORD_DEFAULT;
import static oracle.weblogic.kubernetes.TestConstants.ADMIN_USERNAME_DEFAULT;
import static oracle.weblogic.kubernetes.TestConstants.DEFAULT_WEBLOGIC_IMAGE_TAGS;
import static oracle.weblogic.kubernetes.TestConstants.DOMAIN_API_VERSION;
import static oracle.weblogic.kubernetes.TestConstants.DOMAIN_IMAGES_PREFIX;
import static oracle.weblogic.kubernetes.TestConstants.DOMAIN_IMAGES_REPO;
import static oracle.weblogic.kubernetes.TestConstants.IMAGE_PULL_POLICY;
import static oracle.weblogic.kubernetes.TestConstants.KIND_REPO;
import static oracle.weblogic.kubernetes.TestConstants.MII_BASIC_APP_NAME;
import static oracle.weblogic.kubernetes.TestConstants.MII_BASIC_WDT_MODEL_FILE;
import static oracle.weblogic.kubernetes.TestConstants.OKD;
import static oracle.weblogic.kubernetes.TestConstants.TEST_IMAGES_REPO_SECRET_NAME;
import static oracle.weblogic.kubernetes.TestConstants.WLSIMG_BUILDER;
import static oracle.weblogic.kubernetes.TestConstants.WLS_DOMAIN_TYPE;
import static oracle.weblogic.kubernetes.actions.ActionConstants.ARCHIVE_DIR;
import static oracle.weblogic.kubernetes.actions.ActionConstants.MODEL_DIR;
import static oracle.weblogic.kubernetes.actions.ActionConstants.WDT_VERSION;
import static oracle.weblogic.kubernetes.actions.ActionConstants.WIT_BUILD_DIR;
import static oracle.weblogic.kubernetes.actions.TestActions.buildAppArchive;
import static oracle.weblogic.kubernetes.actions.TestActions.createImage;
import static oracle.weblogic.kubernetes.actions.TestActions.defaultAppParams;
import static oracle.weblogic.kubernetes.actions.TestActions.defaultWitParams;
import static oracle.weblogic.kubernetes.actions.TestActions.deleteImage;
import static oracle.weblogic.kubernetes.actions.TestActions.imagePush;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.checkServiceExists;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.testUntil;
import static oracle.weblogic.kubernetes.utils.DomainUtils.createDomainAndVerify;
import static oracle.weblogic.kubernetes.utils.FileUtils.checkDirectory;
import static oracle.weblogic.kubernetes.utils.ImageUtils.createTestRepoSecret;
import static oracle.weblogic.kubernetes.utils.OperatorUtils.installAndVerifyOperator;
import static oracle.weblogic.kubernetes.utils.PodUtils.checkPodExists;
import static oracle.weblogic.kubernetes.utils.PodUtils.checkPodReady;
import static oracle.weblogic.kubernetes.utils.PodUtils.setPodAntiAffinity;
import static oracle.weblogic.kubernetes.utils.SecretUtils.createSecretWithUsernamePassword;
import static oracle.weblogic.kubernetes.utils.ThreadSafeLogger.getLogger;
import static org.awaitility.Awaitility.with;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("Test to create model in image domain using auxiliary image. "
    + "Multiple domains are created in the same namespace in this class "
    + "using multiple releases of WebLogic Server.")
@IntegrationTest
@Tag("kind-parallel")
class ItWlsMultiReleaseDomains {
  
  private static String domainNamespace = null;
  private static LoggingFacade logger = null;
  private static String domainUid;
  private static final int replicaCount = 1;
  private static String adminSecretName = "weblogic-credentials";
  private static String encryptionSecretName = "encryptionsecret";
  private static String opNamespace = null;
  private static List<String> images = new ArrayList<>();
  
  ConditionFactory withVeryLongRetryPolicy
      = with().pollDelay(0, SECONDS)
          .and().with().pollInterval(10, SECONDS)
          .atMost(30, MINUTES).await();

  /**
   * Install Operator.
   * @param namespaces list of namespaces. 
   */
  @BeforeAll
  public static void initAll(@Namespaces(2) List<String> namespaces) {
    logger = getLogger();
    // get a new unique opNamespace
    logger.info("Creating unique namespace for Operator");
    assertNotNull(namespaces.get(0), "Namespace list is null");
    opNamespace = namespaces.get(0);

    domainNamespace = namespaces.get(1);

    // install and verify operator
    installAndVerifyOperator(opNamespace, domainNamespace);
    // create secret for admin credentials
    logger.info("Create secret for admin credentials");
    createSecretWithUsernamePassword(adminSecretName, domainNamespace,
        ADMIN_USERNAME_DEFAULT, ADMIN_PASSWORD_DEFAULT);

    // create encryption secret
    logger.info("Create encryption secret");
    createSecretWithUsernamePassword(encryptionSecretName, domainNamespace,
        "weblogicenc", "weblogicenc");
  }

  //@ValueSource(strings = {"12.2.1.4-ol8", "14.1.2.0-generic-jdk17-ol8", "15.1.1.0.0-jdk17"})
  
  /**
   * Create domains using multiple releases of WebLogic.
   * Verify all the pods are started and in ready state.
   */
  @ParameterizedTest
  @MethodSource("tagProvider")
  @DisplayName("Test to create domains with different WLS releases")
  void testCreateDomainWithMultipleWLSReleases(String wlsRelease) {
    String myMiiImageName = DOMAIN_IMAGES_PREFIX + "mii-multirelease-image";
    String myMiiImage = myMiiImageName + ":" + wlsRelease;

    testUntil(withVeryLongRetryPolicy,
        createBasicImage(myMiiImageName, wlsRelease, MII_BASIC_WDT_MODEL_FILE,
            null, MII_BASIC_APP_NAME),
        logger,
        "createBasicImage to be successful");
    if (KIND_REPO != null) {
      logger.info("kind load docker-image {0} --name kind", myMiiImage);
    } else {
      logger.info(WLSIMG_BUILDER + " push image {0} to {1}", myMiiImage, DOMAIN_IMAGES_REPO);
    }
    testUntil(
        withVeryLongRetryPolicy,
        () -> imagePush(myMiiImage),
        logger,
        WLSIMG_BUILDER + " push to TEST_IMAGES_REPO/kind for image {0} to be successful",
        myMiiImage);
    domainUid = "domain" + wlsRelease.substring(0, 8).replace(".", "");
    createAndVerifyMiiDomain(myMiiImage, domainUid, domainUid + "-admin-server",
        domainUid + "-managed-server");
  }
  
  private static Stream<Arguments> tagProvider() {
    return Arrays.stream(DEFAULT_WEBLOGIC_IMAGE_TAGS.split(","))
        .map(String::trim)
        .map(Arguments::of);
  }
  
  /**
   * Cleanup images.
   */
  public void tearDownAll() {
    // delete images
    for (String image : images) {
      deleteImage(image);
    }
  }
  
  /**
   * Create a model in image domain and verify the server pods are ready.
   */
  private static void createAndVerifyMiiDomain(String miiImage, String domainUid, 
      String adminServerPodName, String managedServerPrefix) {

    // create registry secret to pull the image from registry
    // this secret is used only for non-kind cluster
    logger.info("Creating registry secret in namespace {0}", domainNamespace);
    createTestRepoSecret(domainNamespace);

    // create secret for admin credentials
    logger.info("Creating secret for admin credentials");
    String adminSecretName = "weblogic-credentials";
    createSecretWithUsernamePassword(adminSecretName, domainNamespace, ADMIN_USERNAME_DEFAULT, ADMIN_PASSWORD_DEFAULT);

    // create encryption secret
    logger.info("Creating encryption secret");
    String encryptionSecretName = "encryptionsecret";
    createSecretWithUsernamePassword(encryptionSecretName, domainNamespace, "weblogicenc", "weblogicenc");

    ServerPod srvrPod = new ServerPod()
        .addEnvItem(new V1EnvVar()
            .name("JAVA_OPTIONS")
            .value("-Dweblogic.StdoutDebugEnabled=false"))
        .addEnvItem(new V1EnvVar()
            .name("USER_MEM_ARGS")
            .value("-Djava.security.egd=file:/dev/./urandom "))
        .resources(new V1ResourceRequirements()
            .limits(new HashMap<>())
            .requests(new HashMap<>()));

    if (!OKD) {
      V1PodSecurityContext podSecCtxt = new V1PodSecurityContext()
                 .runAsUser(0L);
      srvrPod.podSecurityContext(podSecCtxt);
    }

    // create the domain CR
    DomainResource domain = new DomainResource()
        .apiVersion(DOMAIN_API_VERSION)
        .kind("Domain")
        .metadata(new V1ObjectMeta()
            .name(domainUid)
            .namespace(domainNamespace))
        .spec(new DomainSpec()
            .domainUid(domainUid)
            .domainHomeSourceType("FromModel")
            .image(miiImage)
            .imagePullPolicy(IMAGE_PULL_POLICY)
            .addImagePullSecretsItem(new V1LocalObjectReference()
                .name(TEST_IMAGES_REPO_SECRET_NAME))
            .webLogicCredentialsSecret(new V1LocalObjectReference()
                .name(adminSecretName))
            .includeServerOutInPodLog(true)
            .serverStartPolicy("IfNeeded")
            .serverPod(srvrPod)
            .configuration(new Configuration()
                .introspectorJobActiveDeadlineSeconds(3000L)
                .model(new Model()
                    .domainType(WLS_DOMAIN_TYPE)
                    .runtimeEncryptionSecret(encryptionSecretName))));
    setPodAntiAffinity(domain);
    // create model in image domain
    logger.info("Creating model in image domain {0} in namespace {1} using image {2}",
        domainUid, domainNamespace, miiImage);
    createDomainAndVerify(domain, domainNamespace);

    // check that admin server pod exists in the domain namespace
    logger.info("Checking that admin server pod {0} exists in namespace {1}",
        adminServerPodName, domainNamespace);
    checkPodExists(adminServerPodName, domainUid, domainNamespace);

    logger.info("Checking that admin service {0} exists in namespace {1}",
        adminServerPodName, domainNamespace);
    checkServiceExists(adminServerPodName, domainNamespace);

    // check that admin server pod is ready
    logger.info("Checking that admin server pod {0} is ready in namespace {1}",
        adminServerPodName, domainNamespace);
    checkPodReady(adminServerPodName, domainUid, domainNamespace);

    // check for managed server pods existence in the domain namespace
    for (int i = 1; i <= replicaCount; i++) {
      String managedServerPodName = managedServerPrefix + i;

      // check that the managed server pod exists in the domain namespace
      logger.info("Checking that managed server pod {0} exists in namespace {1}",
          managedServerPodName, domainNamespace);
      checkPodExists(managedServerPodName, domainUid, domainNamespace);

      // check that the managed server service exists in the domain namespace
      logger.info("Checking that managed server service {0} exists in namespace {1}",
          managedServerPodName, domainNamespace);
      checkServiceExists(managedServerPodName, domainNamespace);

      // check that the managed server pod is ready
      logger.info("Checking that managed server pod {0} is ready in namespace {1}",
          managedServerPodName, domainNamespace);
      checkPodReady(managedServerPodName, domainUid, domainNamespace);
    }
  }

  /**
   * Create image with basic domain model yaml, variable file and sample application.
   *
   * @param imageName  name of the image
   * @param imageTag   tag of the image
   * @param modelFile  model file to build the image
   * @param varFile    variable file to build the image
   * @param appName    name of the application to build the image
   * @param domainType domain type to be built
   * @return true if image is created successfully
   */

  public Callable<Boolean> createBasicImage(String imageName, String imageTag, String modelFile, String varFile,
                                            String appName) {
    return (() -> {
      LoggingFacade logger = getLogger();
      final String image = imageName + ":" + imageTag;

      // build the model file list
      final List<String> modelList = Collections.singletonList(MODEL_DIR + "/" + modelFile);

      // build an application archive using what is in resources/apps/APP_NAME
      logger.info("Build an application archive using resources/apps/{0}", appName);
      assertTrue(buildAppArchive(defaultAppParams()
              .srcDirList(Collections.singletonList(appName))),
          String.format("Failed to create app archive for %s", appName));

      // build the archive list
      String zipFile = String.format("%s/%s.zip", ARCHIVE_DIR, appName);
      final List<String> archiveList = Collections.singletonList(zipFile);

      // Set additional environment variables for WIT
      checkDirectory(WIT_BUILD_DIR);
      Map<String, String> env = new HashMap<>();
      env.put("WLSIMG_BLDDIR", WIT_BUILD_DIR);

      String witTarget = ((OKD) ? "OpenShift" : "Default");

      // build an image using WebLogic Image Tool
      boolean imageCreation = false;
      logger.info("Create image {0} using model directory {1}", image, MODEL_DIR);

      imageCreation = createImage(
          defaultWitParams()
              .baseImageTag(imageTag)
              .modelImageName(imageName)
              .modelImageTag(imageTag)
              .modelFiles(modelList)
              .modelArchiveFiles(archiveList)
              .wdtModelOnly(true)
              .wdtVersion(WDT_VERSION)
              .target(witTarget)
              .env(env)
              .redirect(true));
      images.add(imageName + ":" + imageTag);
      
      return imageCreation;
    });
  }
  
}
