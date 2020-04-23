// Copyright (c) 2020, Oracle Corporation and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.weblogic.kubernetes.extensions;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import static oracle.weblogic.kubernetes.extensions.LoggedTest.logger;
import static org.junit.jupiter.api.extension.ExtensionContext.Namespace.GLOBAL;

// class to build the required images for the tests
public class ImageBuilders implements BeforeAllCallback, ExtensionContext.Store.CloseableResource {

  private static boolean started = false;
  @Override
  public void beforeAll(ExtensionContext context) {
    if (!started) {
      started = true;
      logger.info("Building docker Images before any integration test classes are run");
      context.getRoot().getStore(GLOBAL).put("BuildSetup", this);
    }
  }
  @Override
  public void close() {
    logger.info("Cleanup images after all test suites are run");
  }
}