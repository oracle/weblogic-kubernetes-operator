// Copyright (c) 2020, 2021, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.weblogic.kubernetes.extensions;

import oracle.weblogic.kubernetes.utils.ThreadSafeLogger;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.TestInstancePostProcessor;

public class LoggingExtension implements TestInstancePostProcessor {

  @Override
  public void postProcessTestInstance(
      Object testInstance, ExtensionContext extensionContext) throws Exception {

    ThreadSafeLogger.globalLogger.info("Initializing logger in postProcessTestInstance for {0}",
        testInstance.getClass().getSimpleName());

    // initialize logger for each test
    ThreadSafeLogger.init(testInstance.getClass().getSimpleName());

  }

}
