// Copyright (c) 2019, 2020, Oracle Corporation and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.utils;

import java.util.logging.ConsoleHandler;
import java.util.logging.Logger;

public class LoggerHelper {
  private static final ThreadLocal<Logger> localLogger = new ThreadLocal<Logger>();
  private static final Logger globalLogger = Logger.getLogger("GLOBAL", "OperatorIT");

  static {
    globalLogger.addHandler(new ConsoleHandler());

  }

  public static void closeLocal() {
    // TODO flush the local logger, if it exists, then close it, then finally call initLocal(null)
    initLocal(null);
  }

  /**
   * initialize local logger.
   * @param cl logger
   */
  public static void initLocal(Logger cl) {
    //cl.setUseParentHandlers(false);
    //cl.addHandler(new ConsoleHandler());
    localLogger.set(cl);

  }

  /**
   * get local.
   * @return logger
   */
  public static Logger getLocal() {
    Logger cl = localLogger.get();
    if (cl == null) {
      return globalLogger;
    }
    return cl;
  }

  public static Logger getGlobal() {
    return globalLogger;
  }
}
