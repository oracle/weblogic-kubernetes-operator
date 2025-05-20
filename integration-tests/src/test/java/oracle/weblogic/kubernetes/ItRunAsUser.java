// Copyright (c) 2025, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.weblogic.kubernetes;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;

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
import oracle.weblogic.kubernetes.actions.impl.primitive.Command;
import oracle.weblogic.kubernetes.actions.impl.primitive.CommandParams;
import oracle.weblogic.kubernetes.annotations.IntegrationTest;
import oracle.weblogic.kubernetes.annotations.Namespaces;
import oracle.weblogic.kubernetes.logging.LoggingFacade;
import org.awaitility.core.ConditionFactory;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIfEnvironmentVariable;

import static java.util.concurrent.TimeUnit.MINUTES;
import static java.util.concurrent.TimeUnit.SECONDS;
import static oracle.weblogic.kubernetes.TestConstants.ADMIN_PASSWORD_DEFAULT;
import static oracle.weblogic.kubernetes.TestConstants.ADMIN_SERVER_NAME_BASE;
import static oracle.weblogic.kubernetes.TestConstants.ADMIN_USERNAME_DEFAULT;
import static oracle.weblogic.kubernetes.TestConstants.BASE_IMAGES_REPO;
import static oracle.weblogic.kubernetes.TestConstants.BASE_IMAGES_REPO_PASSWORD;
import static oracle.weblogic.kubernetes.TestConstants.BASE_IMAGES_REPO_USERNAME;
import static oracle.weblogic.kubernetes.TestConstants.DOMAIN_API_VERSION;
import static oracle.weblogic.kubernetes.TestConstants.DOMAIN_IMAGES_PREFIX;
import static oracle.weblogic.kubernetes.TestConstants.DOMAIN_IMAGES_REPO;
import static oracle.weblogic.kubernetes.TestConstants.IMAGE_PULL_POLICY;
import static oracle.weblogic.kubernetes.TestConstants.KIND_REPO;
import static oracle.weblogic.kubernetes.TestConstants.MANAGED_SERVER_NAME_BASE;
import static oracle.weblogic.kubernetes.TestConstants.MII_BASIC_APP_NAME;
import static oracle.weblogic.kubernetes.TestConstants.MII_BASIC_IMAGE_DOMAINTYPE;
import static oracle.weblogic.kubernetes.TestConstants.MII_BASIC_IMAGE_TAG;
import static oracle.weblogic.kubernetes.TestConstants.MII_BASIC_WDT_MODEL_FILE;
import static oracle.weblogic.kubernetes.TestConstants.OKD;
import static oracle.weblogic.kubernetes.TestConstants.SKIP_BUILD_IMAGES_IF_EXISTS;
import static oracle.weblogic.kubernetes.TestConstants.TEST_IMAGES_REPO_SECRET_NAME;
import static oracle.weblogic.kubernetes.TestConstants.WDT_BASIC_IMAGE_DOMAINHOME;
import static oracle.weblogic.kubernetes.TestConstants.WDT_BASIC_IMAGE_TAG;
import static oracle.weblogic.kubernetes.TestConstants.WEBLOGIC_IMAGE_NAME;
import static oracle.weblogic.kubernetes.TestConstants.WEBLOGIC_IMAGE_WLSADM_TAG;
import static oracle.weblogic.kubernetes.TestConstants.WLSIMG_BUILDER;
import static oracle.weblogic.kubernetes.TestConstants.WLS_DOMAIN_TYPE;
import static oracle.weblogic.kubernetes.actions.ActionConstants.ARCHIVE_DIR;
import static oracle.weblogic.kubernetes.actions.ActionConstants.MODEL_DIR;
import static oracle.weblogic.kubernetes.actions.ActionConstants.WDT_VERSION;
import static oracle.weblogic.kubernetes.actions.ActionConstants.WIT_BUILD_DIR;
import static oracle.weblogic.kubernetes.actions.ActionConstants.WIT_JAVA_HOME;
import static oracle.weblogic.kubernetes.actions.TestActions.buildAppArchive;
import static oracle.weblogic.kubernetes.actions.TestActions.createImage;
import static oracle.weblogic.kubernetes.actions.TestActions.defaultAppParams;
import static oracle.weblogic.kubernetes.actions.TestActions.defaultWitParams;
import static oracle.weblogic.kubernetes.actions.TestActions.imagePush;
import static oracle.weblogic.kubernetes.actions.TestActions.imageRepoLogin;
import static oracle.weblogic.kubernetes.assertions.TestAssertions.doesImageExist;
import static oracle.weblogic.kubernetes.assertions.TestAssertions.imageExists;
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

/**
 * Create a custom 14.1.2.0 WebLogic base image with added new user, wlsadm:root (with uid 12345).
 * 1. Create a file called additionalBuildCommands
 * [initial-build-commands]
 * RUN useradd -u 12345 wlsadm -g root
 * RUN chown -R wlsadm:root /u01
 *
 * [final-build-commands]
 * USER wlsadm
 * 2. Create the custom image based on the default base Weblogic image
 * imagetool create \
 *   --fromImage phx.ocir.io/devweblogic/test-images/weblogic:14.1.2.0-generic-jdk17-ol8 \
 *   --tag phx.ocir.io/devweblogic/test-images/weblogic:14.1.2.0-with-wlsadm-jdk17-ol8 \
 *   --type WLS \
 *   --version 14.1.2.0 \
 *   --additionalBuildCommands pathto/additionalBuildCommands \
 *   --chown wlsadm:root
 *
 * Push this image to the ocir with tag: 14.1.2.0-with-wlsadm-jdk17-ol8-org
 * Using this custom WebLogic base image to create a mii domain with new user, wlsadm:root
 * Set runAsUser > 10000(12345) and verify mii domain is up and running
 */
@DisplayName("Verify using custom WebLogic base image with added user, wlsadm: root, wko supports runAsUser > 10000")
@IntegrationTest
@Tag("kind-sequential")
class ItRunAsUser {

  private static String opNamespace;
  private static String domainNamespace = null;

  // domain constants
  private static final String domainUid = "domain1";
  private static final int replicaCount = 1;
  private static final String adminServerPodName = domainUid + "-" + ADMIN_SERVER_NAME_BASE;
  private static final String managedServerPrefix = domainUid + "-" + MANAGED_SERVER_NAME_BASE;
  private static LoggingFacade logger = null;

  ConditionFactory withVeryLongRetryPolicy
      = with().pollDelay(0, SECONDS)
      .and().with().pollInterval(10, SECONDS)
      .atMost(30, MINUTES).await();


  /**
   * Get namespaces for operator and WebLogic domain.
   *
   * @param namespaces list of namespaces created by the IntegrationTestWatcher by the
   *                   JUnit engine parameter resolution mechanism
   */
  @BeforeAll
  static void initAll(@Namespaces(2) List<String> namespaces) {
    logger = getLogger();
    // get a unique operator namespace
    logger.info("Getting a unique namespace for operator");
    assertNotNull(namespaces.get(0), "Namespace list is null");
    opNamespace = namespaces.get(0);

    // get a unique domain namespace
    logger.info("Getting a unique namespace for WebLogic domain");
    assertNotNull(namespaces.get(1), "Namespace list is null");
    domainNamespace = namespaces.get(1);

    // install and verify operator
    installAndVerifyOperator(opNamespace, domainNamespace);

  }

  /**
   * Create a WLS mii domain with user, wlsadmin:root.
   * Set runAsUser as 12345.
   * Verify Pod is ready and service exists for both admin server and managed servers.
   */
  @Test
  @DisabledIfEnvironmentVariable(named = "OKD", matches = "true")
  @DisplayName("Verify a mii domain with runAsUser as 12345 is up and running")
  void testRunAsUserOver10k() {

    String miiImageWlsadmName = DOMAIN_IMAGES_PREFIX + "mii-image-wlsadm";
    String miiImageWlsadm = miiImageWlsadmName + ":" + MII_BASIC_IMAGE_TAG;

    // create mii Image for this test with user wlsadm: root
    createMiiImage(miiImageWlsadmName, miiImageWlsadm);

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
                 .runAsUser(12345L);
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
            .image(miiImageWlsadm)
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
        domainUid, domainNamespace, miiImageWlsadm);
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

  private void createMiiImage(String miiImageName, String miiImage) {

    // build MII basic image if does not exits
    logger.info("Build/Check mii-basic-wlsadm image with tag {0}", MII_BASIC_IMAGE_TAG);
    if (!imageExists(miiImageName, MII_BASIC_IMAGE_TAG)) {
      logger.info("Building mii image with name {0}, tag {1}, image {2}", miiImageName, MII_BASIC_IMAGE_TAG,
          miiImage);
      testUntil(
          withVeryLongRetryPolicy,
          createBasicImage(miiImageName, MII_BASIC_IMAGE_TAG, MII_BASIC_WDT_MODEL_FILE,
                null, MII_BASIC_APP_NAME, MII_BASIC_IMAGE_DOMAINTYPE),
          logger,
          "create  to be successful");
    } else {
      logger.info("!!!! domain image {0} exists !!!!", miiImage);
    }

    assertTrue(doesImageExist(MII_BASIC_IMAGE_TAG),
        String.format("Image %s doesn't exist", miiImage));

    logger.info(WLSIMG_BUILDER + " login");
    testUntil(withVeryLongRetryPolicy,
        () -> imageRepoLogin(BASE_IMAGES_REPO, BASE_IMAGES_REPO_USERNAME, BASE_IMAGES_REPO_PASSWORD),
        logger, WLSIMG_BUILDER + " login to BASE_IMAGES_REPO to be successful");

    // push the images to test images repository
    if (!DOMAIN_IMAGES_REPO.isEmpty()) {

      List<String> images = new ArrayList<>();

      // add images only if SKIP_BUILD_IMAGES_IF_EXISTS is not set
      if (!SKIP_BUILD_IMAGES_IF_EXISTS) {
        images.add(miiImage);

      }

      for (String image : images) {
        if (KIND_REPO != null) {
          logger.info("kind load docker-image {0} --name kind", image);
        } else {
          logger.info(WLSIMG_BUILDER + " push image {0} to {1}", image, DOMAIN_IMAGES_REPO);
        }
        testUntil(
            withVeryLongRetryPolicy,
                () -> imagePush(image),
                logger,
                WLSIMG_BUILDER + " push to TEST_IMAGES_REPO/kind for image {0} to be successful",
                image);
      }

      // list images for Kind cluster
      if (KIND_REPO != null) {
        Command
              .withParams(new CommandParams()
                  .command(WLSIMG_BUILDER + " exec kind-worker crictl images")
                  .verbose(true)
                  .saveResults(true))
              .execute();
      }
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

  private Callable<Boolean> createBasicImage(String imageName, String imageTag, String modelFile, String varFile,
                                            String appName, String domainType) {
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

      if (WIT_JAVA_HOME != null) {
        env.put("JAVA_HOME", WIT_JAVA_HOME);
      }

      String witTarget = ((OKD) ? "OpenShift" : "Default");

      // build an image using WebLogic Image Tool
      boolean imageCreation = false;
      logger.info("Create image {0} using model directory {1}", image, MODEL_DIR);
      if (domainType.equalsIgnoreCase("wdt")) {
        final List<String> modelVarList = Collections.singletonList(MODEL_DIR + "/" + varFile);
        imageCreation = createImage(
            defaultWitParams()
                .baseImageName(WEBLOGIC_IMAGE_NAME)
                .baseImageTag(WEBLOGIC_IMAGE_WLSADM_TAG)
                .modelImageName(imageName)
                .modelImageTag(WDT_BASIC_IMAGE_TAG)
                .modelFiles(modelList)
                .modelArchiveFiles(archiveList)
                .modelVariableFiles(modelVarList)
                .domainHome(WDT_BASIC_IMAGE_DOMAINHOME)
                .wdtOperation("CREATE")
                .wdtVersion(WDT_VERSION)
                .target(witTarget)
                .env(env)
                .useridGroupid("wlsadm:root")
                .redirect(true));
      } else if (domainType.equalsIgnoreCase("mii")) {
        imageCreation = createImage(
            defaultWitParams()
                .baseImageName(WEBLOGIC_IMAGE_NAME)
                .baseImageTag(WEBLOGIC_IMAGE_WLSADM_TAG)
                .modelImageName(imageName)
                .modelImageTag(MII_BASIC_IMAGE_TAG)
                .modelFiles(modelList)
                .modelArchiveFiles(archiveList)
                .wdtModelOnly(true)
                .wdtVersion(WDT_VERSION)
                .target(witTarget)
                .env(env)
                .useridGroupid("wlsadm:root")
                .redirect(true));
      }
      return imageCreation;
    });
  }

}

