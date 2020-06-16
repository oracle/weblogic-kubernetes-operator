// Copyright (c) 2020, Oracle Corporation and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.weblogic.kubernetes.extensions;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;

import oracle.weblogic.kubernetes.TestConstants;
import oracle.weblogic.kubernetes.actions.impl.Operator;
import oracle.weblogic.kubernetes.utils.ExecCommand;
import oracle.weblogic.kubernetes.utils.ExecResult;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.ExtensionContext;

import static oracle.weblogic.kubernetes.TestConstants.DB_IMAGE_NAME;
import static oracle.weblogic.kubernetes.TestConstants.DB_IMAGE_TAG;
import static oracle.weblogic.kubernetes.TestConstants.JRF_BASE_IMAGE_NAME;
import static oracle.weblogic.kubernetes.TestConstants.JRF_BASE_IMAGE_TAG;
import static oracle.weblogic.kubernetes.TestConstants.KIND_REPO;
import static oracle.weblogic.kubernetes.TestConstants.MII_BASIC_APP_NAME;
import static oracle.weblogic.kubernetes.TestConstants.MII_BASIC_IMAGE_DOMAINTYPE;
import static oracle.weblogic.kubernetes.TestConstants.MII_BASIC_IMAGE_NAME;
import static oracle.weblogic.kubernetes.TestConstants.MII_BASIC_IMAGE_TAG;
import static oracle.weblogic.kubernetes.TestConstants.MII_BASIC_WDT_MODEL_FILE;
import static oracle.weblogic.kubernetes.TestConstants.OCR_PASSWORD;
import static oracle.weblogic.kubernetes.TestConstants.OCR_REGISTRY;
import static oracle.weblogic.kubernetes.TestConstants.OCR_USERNAME;
import static oracle.weblogic.kubernetes.TestConstants.REPO_DUMMY_VALUE;
import static oracle.weblogic.kubernetes.TestConstants.REPO_NAME;
import static oracle.weblogic.kubernetes.TestConstants.REPO_PASSWORD;
import static oracle.weblogic.kubernetes.TestConstants.REPO_REGISTRY;
import static oracle.weblogic.kubernetes.TestConstants.REPO_USERNAME;
import static oracle.weblogic.kubernetes.TestConstants.WDT_BASIC_APP_NAME;
import static oracle.weblogic.kubernetes.TestConstants.WDT_BASIC_IMAGE_DOMAINHOME;
import static oracle.weblogic.kubernetes.TestConstants.WDT_BASIC_IMAGE_DOMAINTYPE;
import static oracle.weblogic.kubernetes.TestConstants.WDT_BASIC_IMAGE_NAME;
import static oracle.weblogic.kubernetes.TestConstants.WDT_BASIC_IMAGE_TAG;
import static oracle.weblogic.kubernetes.TestConstants.WDT_BASIC_MODEL_FILE;
import static oracle.weblogic.kubernetes.TestConstants.WDT_BASIC_MODEL_PROPERTIES_FILE;
import static oracle.weblogic.kubernetes.actions.ActionConstants.ARCHIVE_DIR;
import static oracle.weblogic.kubernetes.actions.ActionConstants.DOWNLOAD_DIR;
import static oracle.weblogic.kubernetes.actions.ActionConstants.MODEL_DIR;
import static oracle.weblogic.kubernetes.actions.ActionConstants.RESOURCE_DIR;
import static oracle.weblogic.kubernetes.actions.ActionConstants.WDT_VERSION;
import static oracle.weblogic.kubernetes.actions.ActionConstants.WIT_BUILD_DIR;
import static oracle.weblogic.kubernetes.actions.ActionConstants.WLS_BASE_IMAGE_NAME;
import static oracle.weblogic.kubernetes.actions.ActionConstants.WLS_BASE_IMAGE_TAG;
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
import static oracle.weblogic.kubernetes.extensions.LoggedTest.logger;
import static oracle.weblogic.kubernetes.utils.FileUtils.checkDirectory;
import static oracle.weblogic.kubernetes.utils.FileUtils.cleanupDirectory;
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

  @Override
  public void beforeAll(ExtensionContext context) {
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
        assertTrue(Operator.buildImage(operatorImage));

        if (System.getenv("SKIP_BASIC_IMAGE_BUILD") == null) {
          // build MII basic image
          miiBasicImage = MII_BASIC_IMAGE_NAME + ":" + MII_BASIC_IMAGE_TAG;
          assertTrue(createBasicImage(MII_BASIC_IMAGE_NAME, MII_BASIC_IMAGE_TAG, MII_BASIC_WDT_MODEL_FILE,
              null, MII_BASIC_APP_NAME, MII_BASIC_IMAGE_DOMAINTYPE),
              String.format("Failed to create the image %s using WebLogic Image Tool",
                  miiBasicImage));

          // build basic wdt-domain-in-image image
          wdtBasicImage = WDT_BASIC_IMAGE_NAME + ":" + WDT_BASIC_IMAGE_TAG;
          assertTrue(createBasicImage(WDT_BASIC_IMAGE_NAME, WDT_BASIC_IMAGE_TAG, WDT_BASIC_MODEL_FILE,
              WDT_BASIC_MODEL_PROPERTIES_FILE, WDT_BASIC_APP_NAME, WDT_BASIC_IMAGE_DOMAINTYPE),
              String.format("Failed to create the image %s using WebLogic Image Tool",
                  wdtBasicImage));


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

          if (!REPO_USERNAME.equals(REPO_DUMMY_VALUE)) {
            logger.info("docker login");
            assertTrue(dockerLogin(REPO_REGISTRY, REPO_USERNAME, REPO_PASSWORD), "docker login failed");
          }
        }
        // push the image
        if (!REPO_NAME.isEmpty()) {
          logger.info("docker push image {0} to {1}", operatorImage, REPO_NAME);
          assertTrue(dockerPush(operatorImage), String.format("docker push failed for image %s", operatorImage));

          if (System.getenv("SKIP_BASIC_IMAGE_BUILD") == null) {
            logger.info("docker push mii basic image {0} to registry", miiBasicImage);
            assertTrue(dockerPush(miiBasicImage), String.format("docker push failed for image %s", miiBasicImage));

            logger.info("docker push wdt basic domain in image {0} to registry", wdtBasicImage);
            assertTrue(dockerPush(wdtBasicImage), String.format("docker push failed for image %s", wdtBasicImage));
          }
        }

        // The following code is for pulling WLS images if running tests in Kind cluster
        if (KIND_REPO != null) {
          // We can't figure out why the kind clusters can't pull images from OCR using the image pull secret. There
          // is some evidence it may be a containerd bug. Therefore, we are going to "give up" and workaround the issue.
          // The workaround will be to:
          //   1. docker login
          //   2. docker pull
          //   3. docker tag with the KIND_REPO value
          //   4. docker push this new image name
          //   5. use this image name to create the domain resource
          Collection<String> images = new ArrayList<>();
          images.add(WLS_BASE_IMAGE_NAME + ":" + WLS_BASE_IMAGE_TAG);
          images.add(JRF_BASE_IMAGE_NAME + ":" + JRF_BASE_IMAGE_TAG);
          images.add(DB_IMAGE_NAME + ":" + DB_IMAGE_TAG);

          assertTrue(dockerLogin(OCR_REGISTRY, OCR_USERNAME, OCR_PASSWORD), "docker login failed");
          pullImageFromOcrAndPushToKind(images);
        }
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
  }

  /**
   * Called when images are pushed to Docker allowing conditional cleanup of images that are pushed
   * to a remote registry.
   * @param imageName Image name
   */
  public static void registerPushedImage(String imageName) {
    pushedImages.add(imageName);
  }

  @Override
  public void close() {
    logger.info("Cleanup images after all test suites are run");

    // delete all the images from local repo
    for (String image : pushedImages) {
      deleteImage(image);
    }

    // delete images from OCIR, if necessary
    if (REPO_NAME.contains("ocir.io")) {
      String token = getOcirToken();
      if (token != null) {
        for (String image : pushedImages) {
          deleteImageOcir(token, image);
        }
      }
    }
  }

  private String getOcirToken() {
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
      logger.info("result.stderr: \n{0}", result.stderr());
    }
  }

  /**
   * Create image with basic domain model yaml, variable file and sample application.
   * @param imageName name of the image
   * @param imageTag tag of the image
   * @param modelFile model file to build the image
   * @param varFile variable file to build the image
   * @param appName name of the application to build the image
   * @param domainType domain type to be built
   * @return true if image is created successfully
   */
  private boolean createBasicImage(String imageName, String imageTag, String modelFile, String varFile,
                                   String appName, String domainType) {

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
  }

  private void pullImageFromOcrAndPushToKind(Collection<String> imagesList) {
    for (String image : imagesList) {
      assertTrue(dockerPull(image), String.format("docker pull failed for image %s", image));
      String kindRepoImage = KIND_REPO + image.substring(TestConstants.OCR_REGISTRY.length() + 1);
      assertTrue(dockerTag(image, kindRepoImage),
          String.format("docker tag failed for images %s, %s", image, kindRepoImage));
      assertTrue(dockerPush(kindRepoImage), String.format("docker push failed for image %s", kindRepoImage));
    }
  }

}
