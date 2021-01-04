// Copyright (c) 2020, 2021, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.weblogic.kubernetes.logging;

import java.util.HashMap;
import java.util.Map;
import java.util.logging.Handler;
import java.util.logging.Logger;

import io.kubernetes.client.openapi.JSON;

/**
 * A factory to create a Logger based on the LoggingFacade.
 * */
public class LoggingFactory {

  // map from resourceBundleName to facade
  private static final Map<String, LoggingFacade> facade = new HashMap<String, LoggingFacade>();

  private static JSON json = new JSON();

  private LoggingFactory() {
    // hide implicit public constructor
  }

  /**
   * Obtains a Logger from the underlying logging implementation and wraps it in a LoggingFacade.
   * Infers resource bundle name as OperatorIntegrationTests.
   *
   * @param clazz use class name as logger name
   * @return a logger for the caller to use
   */
  public static LoggingFacade getLogger(Class clazz) {
    return getLogger(clazz.getName(), "OperatorIntegrationTests");
  }

  /**
   * Obtains a Logger from the underlying logging implementation and wraps it in a LoggingFacade.
   *
   * @param name the name of the logger to use
   * @param resourceBundleName the resource bundle to use with this logger
   * @return a logger for the caller to use
   */
  public static synchronized LoggingFacade getLogger(String name, String resourceBundleName) {

    LoggingFacade lf = facade.get(name);
    if (lf == null) {
      Logger logger = Logger.getLogger(name, resourceBundleName);
      lf = new LoggingFacade(logger);
      facade.put(name, lf);
    }

    return lf;
  }

  /**
   * Obtains a Logger from the underlying logging implementation and wraps it in a LoggingFacade.
   *
   * @param name the name of the logger to use
   * @param resourceBundleName the resource bundle to use with this logger
   * @param handler handler for the logger
   * @return a logger for the caller to use
   */
  public static synchronized LoggingFacade getLogger(
      String name, String resourceBundleName, Handler handler) {

    LoggingFacade lf = facade.get(name);
    if (lf == null) {
      Logger logger = Logger.getLogger(name, resourceBundleName);
      lf = new LoggingFacade(logger);
      logger.addHandler(handler);
      facade.put(name, lf);
    }

    return lf;
  }
}
