// Copyright (c) 2020, 2021, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.weblogic.kubernetes.utils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.logging.FileHandler;
import java.util.logging.SimpleFormatter;

import oracle.weblogic.kubernetes.TestConstants;
import oracle.weblogic.kubernetes.logging.LoggingFacade;
import oracle.weblogic.kubernetes.logging.LoggingFactory;

public class ThreadSafeLogger {
  private static InheritableThreadLocal<LoggingFacade> localLogger = new InheritableThreadLocal<LoggingFacade>();
  public static final LoggingFacade globalLogger = LoggingFactory.getLogger("GLOBAL", "OperatorIntegrationTests");

  /**
   * Create a logger and set it in thread local.
   * @param loggerName name of the logger
   */
  public static void init(String loggerName) {
    try {
      // create file handler
      Path resultDir = Files.createDirectories(Paths.get(TestConstants.LOGS_DIR,
          loggerName));
      FileHandler fileHandler = new FileHandler(
          Paths.get(resultDir.toString(), loggerName + ".out").toString(), true);
      SimpleFormatter formatter = new SimpleFormatter();
      fileHandler.setFormatter(formatter);
      // create logger
      LoggingFacade logger = LoggingFactory.getLogger(
          loggerName, "OperatorIntegrationTests", fileHandler);
      localLogger.set(logger);
    } catch (IOException ioe) {
      globalLogger.severe("Logger initialization failed with Exception {0}", ioe);
    }
  }

  /**
   * Get logger from the current thread.
   * @return logging facade with logger
   */
  public static LoggingFacade getLogger() {
    if (localLogger.get() != null) {
      return localLogger.get();
    } else {
      return globalLogger;
    }
  }

}
