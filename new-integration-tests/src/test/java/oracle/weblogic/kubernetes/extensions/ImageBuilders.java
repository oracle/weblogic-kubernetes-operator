// Copyright (c) 2020, Oracle Corporation and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.weblogic.kubernetes.extensions;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;

import oracle.weblogic.kubernetes.actions.impl.Operator;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.ExtensionContext;

import static oracle.weblogic.kubernetes.extensions.LoggedTest.logger;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.extension.ExtensionContext.Namespace.GLOBAL;

//import static org.junit.jupiter.api.Assertions.assertTrue;

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
      // Only the first thread will enter this block.

      logger.info("Building docker Images before any integration test classes are run");
      context.getRoot().getStore(GLOBAL).put("BuildSetup", this);

      // build operator image
      String operatorImage = Operator.getImageName();
      logger.info("Operator image name {0}", operatorImage);
      assertFalse(true);
      /* assertFalse(operatorImage.isEmpty(), "Image name can not be empty");
      assertTrue(Operator.buildImage(operatorImage)); */

      // Initialization is done. Release all waiting other threads. The latch is now disabled so other threads
      // arriving later will immediately proceed.
      initializationLatch.countDown();
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