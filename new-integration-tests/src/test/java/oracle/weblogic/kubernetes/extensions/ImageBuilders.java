// Copyright (c) 2020, Oracle Corporation and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.weblogic.kubernetes.extensions;

import java.io.IOException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;

import oracle.weblogic.kubernetes.actions.impl.Operator;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.ExtensionContext;

import static oracle.weblogic.kubernetes.TestConstants.REPO_DUMMY_VALUE;
import static oracle.weblogic.kubernetes.TestConstants.REPO_NAME;
import static oracle.weblogic.kubernetes.TestConstants.REPO_PASSWORD;
import static oracle.weblogic.kubernetes.TestConstants.REPO_REGISTRY;
import static oracle.weblogic.kubernetes.TestConstants.REPO_USERNAME;
import static oracle.weblogic.kubernetes.actions.ActionConstants.DOWNLOAD_DIR;
import static oracle.weblogic.kubernetes.actions.TestActions.dockerLogin;
import static oracle.weblogic.kubernetes.actions.TestActions.dockerPush;
import static oracle.weblogic.kubernetes.extensions.LoggedTest.logger;
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
        String operatorImage = Operator.getImageName();
        logger.info("Operator image name {0}", operatorImage);
        // assertFalse(true);
        assertFalse(operatorImage.isEmpty(), "Image name can not be empty");

        assertTrue(Operator.buildImage(operatorImage));

        // docker login, if needed
        if (!REPO_USERNAME.equals(REPO_DUMMY_VALUE)) {
          logger.info("docker login");
          assertTrue(dockerLogin(REPO_REGISTRY, REPO_USERNAME, REPO_PASSWORD), "docker login failed");
        }

        // push the image
        if (!REPO_NAME.isEmpty()) {
          logger.info("docker push image {0} to {1}", operatorImage, REPO_NAME);
          assertTrue(dockerPush(operatorImage), String.format("docker push failed for image %s", operatorImage));
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
  }
}
