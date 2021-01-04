// Copyright (c) 2020, 2021, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.weblogic.kubernetes.extensions;


import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Handler;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import oracle.weblogic.kubernetes.actions.impl.Operator;
import oracle.weblogic.kubernetes.logging.LoggingFacade;
import oracle.weblogic.kubernetes.utils.ExecCommand;
import oracle.weblogic.kubernetes.utils.ExecResult;
import org.awaitility.core.ConditionFactory;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.ExtensionContext;

import static java.util.concurrent.TimeUnit.MINUTES;
import static java.util.concurrent.TimeUnit.SECONDS;
import static oracle.weblogic.kubernetes.TestConstants.BASE_IMAGES_REPO;
import static oracle.weblogic.kubernetes.TestConstants.DB_IMAGE_NAME;
import static oracle.weblogic.kubernetes.TestConstants.DB_IMAGE_TAG;
import static oracle.weblogic.kubernetes.TestConstants.DOMAIN_IMAGES_REPO;
import static oracle.weblogic.kubernetes.TestConstants.FMWINFRA_IMAGE_NAME;
import static oracle.weblogic.kubernetes.TestConstants.FMWINFRA_IMAGE_TAG;
import static oracle.weblogic.kubernetes.TestConstants.KIND_REPO;
import static oracle.weblogic.kubernetes.TestConstants.MII_BASIC_APP_NAME;
import static oracle.weblogic.kubernetes.TestConstants.MII_BASIC_IMAGE_DOMAINTYPE;
import static oracle.weblogic.kubernetes.TestConstants.MII_BASIC_IMAGE_NAME;
import static oracle.weblogic.kubernetes.TestConstants.MII_BASIC_IMAGE_TAG;
import static oracle.weblogic.kubernetes.TestConstants.MII_BASIC_WDT_MODEL_FILE;
import static oracle.weblogic.kubernetes.TestConstants.OCIR_PASSWORD;
import static oracle.weblogic.kubernetes.TestConstants.OCIR_REGISTRY;
import static oracle.weblogic.kubernetes.TestConstants.OCIR_USERNAME;
import static oracle.weblogic.kubernetes.TestConstants.OCR_PASSWORD;
import static oracle.weblogic.kubernetes.TestConstants.OCR_REGISTRY;
import static oracle.weblogic.kubernetes.TestConstants.OCR_USERNAME;
import static oracle.weblogic.kubernetes.TestConstants.REPO_DUMMY_VALUE;
import static oracle.weblogic.kubernetes.TestConstants.RESULTS_ROOT;
import static oracle.weblogic.kubernetes.TestConstants.WDT_BASIC_APP_NAME;
import static oracle.weblogic.kubernetes.TestConstants.WDT_BASIC_IMAGE_DOMAINHOME;
import static oracle.weblogic.kubernetes.TestConstants.WDT_BASIC_IMAGE_DOMAINTYPE;
import static oracle.weblogic.kubernetes.TestConstants.WDT_BASIC_IMAGE_NAME;
import static oracle.weblogic.kubernetes.TestConstants.WDT_BASIC_IMAGE_TAG;
import static oracle.weblogic.kubernetes.TestConstants.WDT_BASIC_MODEL_FILE;
import static oracle.weblogic.kubernetes.TestConstants.WDT_BASIC_MODEL_PROPERTIES_FILE;
import static oracle.weblogic.kubernetes.TestConstants.WEBLOGIC_IMAGE_NAME;
import static oracle.weblogic.kubernetes.TestConstants.WEBLOGIC_IMAGE_TAG;
import static oracle.weblogic.kubernetes.TestConstants.WLS_UPDATE_IMAGE_TAG;
import static oracle.weblogic.kubernetes.actions.ActionConstants.ARCHIVE_DIR;
import static oracle.weblogic.kubernetes.actions.ActionConstants.DOWNLOAD_DIR;
import static oracle.weblogic.kubernetes.actions.ActionConstants.MODEL_DIR;
import static oracle.weblogic.kubernetes.actions.ActionConstants.RESOURCE_DIR;
import static oracle.weblogic.kubernetes.actions.ActionConstants.STAGE_DIR;
import static oracle.weblogic.kubernetes.actions.ActionConstants.WDT_VERSION;
import static oracle.weblogic.kubernetes.actions.ActionConstants.WIT_BUILD_DIR;
import static oracle.weblogic.kubernetes.actions.ActionConstants.WORK_DIR;
import static oracle.weblogic.kubernetes.actions.TestActions.buildAppArchive;
import static oracle.weblogic.kubernetes.actions.TestActions.createImage;
import static oracle.weblogic.kubernetes.actions.TestActions.defaultAppParams;
import static oracle.weblogic.kubernetes.actions.TestActions.defaultWitParams;
import static oracle.weblogic.kubernetes.actions.TestActions.deleteImage;
import static oracle.weblogic.kubernetes.actions.TestActions.dockerLogin;
import static oracle.weblogic.kubernetes.actions.TestActions.dockerPull;
import static oracle.weblogic.kubernetes.actions.TestActions.dockerPush;
import static oracle.weblogic.kubernetes.actions.TestActions.dockerTag;
import static oracle.weblogic.kubernetes.assertions.TestAssertions.doesImageExist;
import static oracle.weblogic.kubernetes.utils.FileUtils.checkDirectory;
import static oracle.weblogic.kubernetes.utils.FileUtils.cleanupDirectory;
import static oracle.weblogic.kubernetes.utils.IstioUtils.installIstio;
import static oracle.weblogic.kubernetes.utils.IstioUtils.uninstallIstio;
import static oracle.weblogic.kubernetes.utils.ThreadSafeLogger.getLogger;
import static org.awaitility.Awaitility.with;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.extension.ExtensionContext.Namespace.GLOBAL;

/**
 * Class to build the required images for the tests.
 */
public class ImageBuilders implements BeforeAllCallback, ExtensionContext.Store.CloseableResource {
  private static final AtomicBoolean started = new AtomicBoolean(false);
  private static final CountDownLatch initializationLatch = new CountDownLatch(1);
  private static String operatorImage;
  private static String miiBasicImage;
  private static String wdtBasicImage;

  private static Collection<String> pushedImages = new ArrayList<>();
  private static boolean isInitializationSuccessful = false;

  ConditionFactory withStandardRetryPolicy
      = with().pollDelay(0, SECONDS)
      .and().with().pollInterval(10, SECONDS)
      .atMost(30, MINUTES).await();

  @Override
  public void beforeAll(ExtensionContext context) {
    LoggingFacade logger = getLogger();
    /* The pattern is that we have initialization code that we want to run once to completion
     * before any tests are executed. This method will be called before every test method. Therefore, the
     * very first time this method is called we will do the initialization. Since we assume that the tests
     * will be run concurrently in many threads, we need a guard to ensure that only the first thread arriving
     * attempts to do the initialization *and* that any other threads *wait* for that initialization to complete
     * before running their tests.
     */
    if (!started.getAndSet(true)) {
      try {
        // clean up the download directory so that we always get the latest
        // versions of the WDT and WIT tools in every run of the test suite.
        try {
          cleanupDirectory(DOWNLOAD_DIR);
        } catch (IOException ioe) {
          logger.severe("Failed to cleanup the download directory " + DOWNLOAD_DIR, ioe);
        }

        // Only the first thread will enter this block.

        logger.info("Building docker Images before any integration test classes are run");
        context.getRoot().getStore(GLOBAL).put("BuildSetup", this);

        // build operator image
        operatorImage = Operator.getImageName();
        logger.info("Operator image name {0}", operatorImage);
        assertFalse(operatorImage.isEmpty(), "Image name can not be empty");
        assertTrue(Operator.buildImage(operatorImage), "docker build failed for Operator");

        // docker login to OCR or OCIR if OCR_USERNAME and OCR_PASSWORD is provided in env var
        if (BASE_IMAGES_REPO.equals(OCR_REGISTRY)) {
          if (!OCR_USERNAME.equals(REPO_DUMMY_VALUE)) {
            withStandardRetryPolicy
                .conditionEvaluationListener(
                    condition -> logger.info("Waiting for docker login to OCR to be successful"
                            + "(elapsed time {0} ms, remaining time {1} ms)",
                        condition.getElapsedTimeInMS(),
                        condition.getRemainingTimeInMS()))
                .until(() -> dockerLogin(OCR_REGISTRY, OCR_USERNAME, OCR_PASSWORD));
          }
        } else if (BASE_IMAGES_REPO.equals(OCIR_REGISTRY)) {
          if (!OCIR_USERNAME.equals(REPO_DUMMY_VALUE)) {
            withStandardRetryPolicy
                .conditionEvaluationListener(
                    condition -> logger.info("Waiting for docker login to OCIR to be successful"
                            + "(elapsed time {0} ms, remaining time {1} ms)",
                        condition.getElapsedTimeInMS(),
                        condition.getRemainingTimeInMS()))
                .until(() -> dockerLogin(OCIR_REGISTRY, OCIR_USERNAME, OCIR_PASSWORD));
          }
        }
        // The following code is for pulling WLS images if running tests in Kind cluster
        if (KIND_REPO != null) {
          // The kind clusters can't pull images from OCR using the image pull secret.
          // It may be a containerd bug. We are going to workaround this issue.
          // The workaround will be to:
          //   1. docker login
          //   2. docker pull
          //   3. docker tag with the KIND_REPO value
          //   4. docker push this new image name
          //   5. use this image name to create the domain resource
          Collection<String> images = new ArrayList<>();

          images.add(WEBLOGIC_IMAGE_NAME + ":" + WEBLOGIC_IMAGE_TAG);
          images.add(WEBLOGIC_IMAGE_NAME + ":" + WLS_UPDATE_IMAGE_TAG);
          images.add(FMWINFRA_IMAGE_NAME + ":" + FMWINFRA_IMAGE_TAG);
          images.add(DB_IMAGE_NAME + ":" + DB_IMAGE_TAG);

          for (String image : images) {
            withStandardRetryPolicy
                .conditionEvaluationListener(
                    condition -> logger.info("Waiting for pullImageFromOcrOrOcirAndPushToKind for image {0} to be "
                            + "successful (elapsed time {1} ms, remaining time {2} ms)", image,
                        condition.getElapsedTimeInMS(),
                        condition.getRemainingTimeInMS()))
                .until(pullImageFromOcrOrOcirAndPushToKind(image)
                );
          }
        }

        if (System.getenv("SKIP_BASIC_IMAGE_BUILD") == null) {
          // build MII basic image
          miiBasicImage = MII_BASIC_IMAGE_NAME + ":" + MII_BASIC_IMAGE_TAG;
          withStandardRetryPolicy
              .conditionEvaluationListener(
                  condition -> logger.info("Waiting for createBasicImage to be successful"
                          + "(elapsed time {0} ms, remaining time {1} ms)",
                      condition.getElapsedTimeInMS(),
                      condition.getRemainingTimeInMS()))
              .until(createBasicImage(MII_BASIC_IMAGE_NAME, MII_BASIC_IMAGE_TAG, MII_BASIC_WDT_MODEL_FILE,
                  null, MII_BASIC_APP_NAME, MII_BASIC_IMAGE_DOMAINTYPE)
              );

          // build basic wdt-domain-in-image image
          wdtBasicImage = WDT_BASIC_IMAGE_NAME + ":" + WDT_BASIC_IMAGE_TAG;
          withStandardRetryPolicy
              .conditionEvaluationListener(
                  condition -> logger.info("Waiting for createBasicImage to be successful"
                          + "(elapsed time {0} ms, remaining time {1} ms)",
                      condition.getElapsedTimeInMS(),
                      condition.getRemainingTimeInMS()))
              .until(createBasicImage(WDT_BASIC_IMAGE_NAME, WDT_BASIC_IMAGE_TAG, WDT_BASIC_MODEL_FILE,
                  WDT_BASIC_MODEL_PROPERTIES_FILE, WDT_BASIC_APP_NAME, WDT_BASIC_IMAGE_DOMAINTYPE)
              );

          /* Check image exists using docker images | grep image tag.
           * Tag name is unique as it contains date and timestamp.
           * This is a workaround for the issue on Jenkins machine
           * as docker images imagename:imagetag is not working and
           * the test fails even though the image exists.
           */
          assertTrue(doesImageExist(MII_BASIC_IMAGE_TAG),
              String.format("Image %s doesn't exist", miiBasicImage));

          assertTrue(doesImageExist(WDT_BASIC_IMAGE_TAG),
              String.format("Image %s doesn't exist", wdtBasicImage));

        }

        if (!OCIR_USERNAME.equals(REPO_DUMMY_VALUE)) {
          logger.info("docker login");
          withStandardRetryPolicy
              .conditionEvaluationListener(
                  condition -> logger.info("Waiting for docker login to OCIR to be successful"
                          + "(elapsed time {0} ms, remaining time {1} ms)",
                      condition.getElapsedTimeInMS(),
                      condition.getRemainingTimeInMS()))
              .until(() -> dockerLogin(OCIR_REGISTRY, OCIR_USERNAME, OCIR_PASSWORD));
        }

        // push the images to repo
        if (!DOMAIN_IMAGES_REPO.isEmpty()) {

          List<String> images = new ArrayList<>();
          images.add(operatorImage);
          // add images only if SKIP_BASIC_IMAGE_BUILD is not set
          if (System.getenv("SKIP_BASIC_IMAGE_BUILD") == null) {
            images.add(miiBasicImage);
            images.add(wdtBasicImage);
          }

          for (String image : images) {
            logger.info("docker push image {0} to {1}", image, DOMAIN_IMAGES_REPO);
            withStandardRetryPolicy
                .conditionEvaluationListener(
                    condition -> logger.info("Waiting for docker push to OCIR for image {0} to be successful"
                            + "(elapsed time {1} ms, remaining time {2} ms)",
                        image,
                        condition.getElapsedTimeInMS(),
                        condition.getRemainingTimeInMS()))
                .until(() -> dockerPush(image));
          }
        }

        // set initialization success to true, not counting the istio installation as not all tests use istio
        isInitializationSuccessful = true;
        logger.info("Installing istio before any test suites are run");
        installIstio();
      } finally {
        // Initialization is done. Release all waiting other threads. The latch is now disabled so
        // other threads
        // arriving later will immediately proceed.
        initializationLatch.countDown();
      }
    } else {
      // Other threads will enter here and wait on the latch. Once the latch is released, any threads arriving
      // later will immediately proceed.
      try {
        initializationLatch.await();
      } catch (InterruptedException e) {
        throw new IllegalStateException(e);
      }
    }

    // check initialization is already done and is not successful
    assertTrue(started.get() && isInitializationSuccessful,
        "Initialization(pull images from OCR or login/push to OCIR) failed, "
            + "check the actual error or stack trace in the first test that failed in the test suite");

  }

  /**
   * Called when images are pushed to Docker allowing conditional cleanup of images that are pushed
   * to a remote registry.
   *
   * @param imageName Image name
   */
  public static void registerPushedImage(String imageName) {
    pushedImages.add(imageName);
  }

  @Override
  public void close() {
    LoggingFacade logger = getLogger();
    // check SKIP_CLEANUP environment variable to skip cleanup
    if (System.getenv("SKIP_CLEANUP") != null
        && System.getenv("SKIP_CLEANUP").toLowerCase().equals("true")) {
      logger.info("Skipping RESULTS_ROOT clean up after test execution");
    } else {
      logger.info("Uninstall istio after all test suites are run");
      uninstallIstio();
      logger.info("Cleanup WIT/WDT binary form {0}", RESULTS_ROOT);
      try {
        Files.deleteIfExists(Paths.get(RESULTS_ROOT, "wlthint3client.jar"));
        cleanupDirectory(DOWNLOAD_DIR);
        cleanupDirectory(WIT_BUILD_DIR);
        cleanupDirectory(STAGE_DIR);
        cleanupDirectory((Paths.get(WORK_DIR, "imagetool")).toString());
        // remove empty directory
        Files.deleteIfExists(Paths.get(WORK_DIR, "imagetool"));
        Files.deleteIfExists(Paths.get(STAGE_DIR));
        Files.deleteIfExists(Paths.get(WIT_BUILD_DIR));
        Files.deleteIfExists(Paths.get(DOWNLOAD_DIR));
      } catch (IOException ioe) {
        logger.severe("Failed to cleanup files @ " + RESULTS_ROOT, ioe);
      }

      logger.info("Cleanup images after all test suites are run");
      // delete all the images from local repo
      for (String image : pushedImages) {
        deleteImage(image);
      }
    }

    // delete images from OCIR, if necessary
    if (DOMAIN_IMAGES_REPO.contains("ocir.io")) {
      String token = getOcirToken();
      if (token != null) {
        for (String image : pushedImages) {
          deleteImageOcir(token, image);
        }
      }
    }

    for (Handler handler : logger.getUnderlyingLogger().getHandlers()) {
      handler.close();
    }
  }

  private String getOcirToken() {
    LoggingFacade logger = getLogger();
    Path scriptPath = Paths.get(RESOURCE_DIR, "bash-scripts", "ocirtoken.sh");
    String cmd = scriptPath.toFile().getAbsolutePath();
    ExecResult result = null;
    try {
      result = ExecCommand.exec(cmd, true);
    } catch (Exception e) {
      logger.info("Got exception while running command: {0}", cmd);
      logger.info(e.toString());
    }
    if (result != null) {
      logger.info("result.stdout: \n{0}", result.stdout());
      logger.info("result.stderr: \n{0}", result.stderr());
    }

    return result != null ? result.stdout().trim() : null;
  }

  private void deleteImageOcir(String token, String imageName) {
    LoggingFacade logger = getLogger();
    int firstSlashIdx = imageName.indexOf('/');
    String registry = imageName.substring(0, firstSlashIdx);
    int secondSlashIdx = imageName.indexOf('/', firstSlashIdx + 1);
    String tenancy = imageName.substring(firstSlashIdx + 1, secondSlashIdx);
    String imageAndTag = imageName.substring(secondSlashIdx + 1);
    String curlCmd = "curl -skL -X \"DELETE\" -H \"Authorization: Bearer " + token
        + "\" \"https://" + registry + "/20180419/docker/images/"
        + tenancy + "/" + imageAndTag.replace(':', '/') + "\"";
    logger.info("About to invoke: " + curlCmd);
    ExecResult result = null;
    try {
      result = ExecCommand.exec(curlCmd, true);
    } catch (Exception e) {
      logger.info("Got exception while running command: {0}", curlCmd);
      logger.info(e.toString());
    }
    if (result != null) {
      logger.info("result.stdout: \n{0}", result.stdout());
      String stdout = result.stdout();
      logger.info("result.stdout: \n{0}", stdout);
      logger.info("result.stderr: \n{0}", result.stderr());

      // check if delete was successful and respond if tag couldn't be deleted because there is only one image
      if (!stdout.isEmpty()) {
        ObjectMapper mapper = new ObjectMapper();
        try {
          JsonNode root = mapper.readTree(stdout);
          JsonNode errors = root.path("errors");
          if (errors != null) {
            Iterator<JsonNode> it = errors.elements();
            while (it.hasNext()) {
              JsonNode entry = it.next();
              if (entry != null) {
                JsonNode code = entry.path("code");
                if (code != null) {
                  if ("SEMANTIC_VALIDATION_ERROR".equals(code.asText())) {
                    // The delete of the tag failed because there is only one tag remaining in the
                    // repository
                    // Note: there are probably other semantic validation errors, but I don't think
                    // it's worth
                    // checking now because our use cases are fairly simple

                    int colonIdx = imageAndTag.indexOf(':');
                    String repo = imageAndTag.substring(0, colonIdx);

                    // Delete the repository
                    curlCmd =
                        "curl -skL -X \"DELETE\" -H \"Authorization: Bearer "
                            + token
                            + "\" \"https://"
                            + registry
                            + "/20180419/docker/repos/"
                            + tenancy
                            + "/"
                            + repo
                            + "\"";
                    logger.info("About to invoke: " + curlCmd);
                    result = null;
                    try {
                      result = ExecCommand.exec(curlCmd, true);
                    } catch (Exception e) {
                      logger.info("Got exception while running command: {0}", curlCmd);
                      logger.info(e.toString());
                    }
                    if (result != null) {
                      logger.info("result.stdout: \n{0}", result.stdout());
                      logger.info("result.stderr: \n{0}", result.stderr());
                    }
                  }
                }
              }
            }
          }
        } catch (JsonProcessingException e) {
          logger.info("Got exception, parsing failed with errors " + e.getMessage());
        }
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

  public Callable<Boolean> createBasicImage(String imageName, String imageTag, String modelFile, String varFile,
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

      // For k8s 1.16 support and as of May 6, 2020, we presently need a different JDK for these
      // tests and for image tool. This is expected to no longer be necessary once JDK 11.0.8 or
      // the next JDK 14 versions are released.
      String witJavaHome = System.getenv("WIT_JAVA_HOME");
      if (witJavaHome != null) {
        env.put("JAVA_HOME", witJavaHome);
      }

      // build an image using WebLogic Image Tool
      boolean imageCreation = false;
      logger.info("Create image {0} using model directory {1}", image, MODEL_DIR);
      if (domainType.equalsIgnoreCase("wdt")) {
        final List<String> modelVarList = Collections.singletonList(MODEL_DIR + "/" + varFile);
        imageCreation = createImage(
            defaultWitParams()
                .modelImageName(imageName)
                .modelImageTag(WDT_BASIC_IMAGE_TAG)
                .modelFiles(modelList)
                .modelArchiveFiles(archiveList)
                .modelVariableFiles(modelVarList)
                .domainHome(WDT_BASIC_IMAGE_DOMAINHOME)
                .wdtOperation("CREATE")
                .wdtVersion(WDT_VERSION)
                .env(env)
                .redirect(true));
      } else if (domainType.equalsIgnoreCase("mii")) {
        imageCreation = createImage(
            defaultWitParams()
                .modelImageName(imageName)
                .modelImageTag(MII_BASIC_IMAGE_TAG)
                .modelFiles(modelList)
                .modelArchiveFiles(archiveList)
                .wdtModelOnly(true)
                .wdtVersion(WDT_VERSION)
                .env(env)
                .redirect(true));
      }
      return imageCreation;
    });
  }

  private Callable<Boolean> pullImageFromOcrOrOcirAndPushToKind(String image) {
    return (() -> {
      String kindRepoImage = KIND_REPO + image.substring(BASE_IMAGES_REPO.length() + 1);
      return dockerPull(image) && dockerTag(image, kindRepoImage) && dockerPush(kindRepoImage);
    });
  }

}
