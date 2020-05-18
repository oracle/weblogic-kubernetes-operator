// Copyright (c) 2020, Oracle Corporation and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.weblogic.kubernetes.extensions;

import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;

import oracle.weblogic.kubernetes.actions.impl.Operator;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.ExtensionContext;

import static oracle.weblogic.kubernetes.TestConstants.MII_BASIC_APP_NAME;
import static oracle.weblogic.kubernetes.TestConstants.MII_BASIC_IMAGE_NAME;
import static oracle.weblogic.kubernetes.TestConstants.MII_BASIC_IMAGE_TAG;
import static oracle.weblogic.kubernetes.TestConstants.MII_BASIC_WDT_MODEL_FILE;
import static oracle.weblogic.kubernetes.TestConstants.REPO_DUMMY_VALUE;
import static oracle.weblogic.kubernetes.TestConstants.REPO_NAME;
import static oracle.weblogic.kubernetes.TestConstants.REPO_PASSWORD;
import static oracle.weblogic.kubernetes.TestConstants.REPO_REGISTRY;
import static oracle.weblogic.kubernetes.TestConstants.REPO_USERNAME;
import static oracle.weblogic.kubernetes.actions.ActionConstants.ARCHIVE_DIR;
import static oracle.weblogic.kubernetes.actions.ActionConstants.DOWNLOAD_DIR;
import static oracle.weblogic.kubernetes.actions.ActionConstants.MODEL_DIR;
import static oracle.weblogic.kubernetes.actions.ActionConstants.WDT_VERSION;
import static oracle.weblogic.kubernetes.actions.ActionConstants.WIT_BUILD_DIR;
import static oracle.weblogic.kubernetes.actions.TestActions.buildAppArchive;
import static oracle.weblogic.kubernetes.actions.TestActions.createMiiImage;
import static oracle.weblogic.kubernetes.actions.TestActions.defaultAppParams;
import static oracle.weblogic.kubernetes.actions.TestActions.defaultWitParams;
import static oracle.weblogic.kubernetes.actions.TestActions.deleteImage;
import static oracle.weblogic.kubernetes.actions.TestActions.dockerLogin;
import static oracle.weblogic.kubernetes.actions.TestActions.dockerPush;
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

        // build MII basic image
        miiBasicImage = MII_BASIC_IMAGE_NAME + ":" + MII_BASIC_IMAGE_TAG;
        assertTrue(createMiiBasicImage(MII_BASIC_IMAGE_NAME, MII_BASIC_IMAGE_TAG),
            String.format("Failed to create the image %s using WebLogic Image Tool",
                miiBasicImage));

        /* Check image exists using docker images | grep image tag.
         * Tag name is unique as it contains date and timestamp.
         * This is a workaround for the issue on Jenkins machine
         * as docker images imagename:imagetag is not working and
         * the test fails even though the image exists.
         */
        assertTrue(doesImageExist(MII_BASIC_IMAGE_TAG),
            String.format("Image %s doesn't exist", miiBasicImage));

        if (!REPO_USERNAME.equals(REPO_DUMMY_VALUE)) {
          logger.info("docker login");
          assertTrue(dockerLogin(REPO_REGISTRY, REPO_USERNAME, REPO_PASSWORD), "docker login failed");
        }

        // push the image
        if (!REPO_NAME.isEmpty()) {
          logger.info("docker push image {0} to {1}", operatorImage, REPO_NAME);
          assertTrue(dockerPush(operatorImage), String.format("docker push failed for image %s", operatorImage));

          logger.info("docker push mii basic image {0} to registry", miiBasicImage);
          assertTrue(dockerPush(miiBasicImage), String.format("docker push failed for image %s", miiBasicImage));
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

  @Override
  public void close() {
    logger.info("Cleanup images after all test suites are run");

    // delete mii basic image
    if (miiBasicImage != null) {
      deleteImage(miiBasicImage);
    }

    // delete operator image
    if (operatorImage != null) {
      deleteImage(operatorImage);
    }

  }

  /**
   * Create image with basic domain model yaml and sample app.
   * @param imageName name of the image
   * @param imageTag tag of the image
   * @return true if image is created successfully
   */
  private boolean createMiiBasicImage(String imageName, String imageTag) {

    final String image = imageName + ":" + imageTag;

    // build the model file list
    final List<String> modelList = Collections.singletonList(MODEL_DIR + "/" + MII_BASIC_WDT_MODEL_FILE);

    // build an application archive using what is in resources/apps/APP_NAME
    logger.info("Build an application archive using resources/apps/{0}", MII_BASIC_APP_NAME);
    assertTrue(buildAppArchive(defaultAppParams()
        .srcDirList(Collections.singletonList(MII_BASIC_APP_NAME))),
        String.format("Failed to create app archive for %s", MII_BASIC_APP_NAME));

    // build the archive list
    String zipFile = String.format("%s/%s.zip", ARCHIVE_DIR, MII_BASIC_APP_NAME);
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
    logger.info("Create image {0} using model directory {1}", image, MODEL_DIR);
    return createMiiImage(
        defaultWitParams()
            .modelImageName(imageName)
            .modelImageTag(MII_BASIC_IMAGE_TAG)
            .modelFiles(modelList)
            .modelArchiveFiles(archiveList)
            .wdtVersion(WDT_VERSION)
            .env(env)
            .redirect(true));

  }
}
