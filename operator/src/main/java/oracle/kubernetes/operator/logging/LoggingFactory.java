// Copyright (c) 2017, 2021, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.logging;

import java.util.HashMap;
import java.util.Map;
import java.util.logging.Logger;

import io.kubernetes.client.openapi.JSON;

/** A factory to create Loggers. */
public class LoggingFactory {

  // map from resourceBundleName to facade
  private static final Map<String, LoggingFacade> facade = new HashMap<>();

  private static final JSON json = new JSON();

  private LoggingFactory() {
    // hide implicit public constructor
  }

  public static JSON getJson() {
    return json;
  }

  /**
   * Obtains a Logger from the underlying logging implementation and wraps it in a LoggingFacade.
   *
   * @param name the name of the logger to use
   * @param resourceBundleName the resource bundle to use with this logger
   * @return a PlatformLogger object for the caller to use
   */
  public static synchronized LoggingFacade getLogger(String name, String resourceBundleName) {
    return facade.computeIfAbsent(resourceBundleName, lf -> getLoggingFacade(name, resourceBundleName));
  }

  private static LoggingFacade getLoggingFacade(String name, String resourceBundleName) {
    return new LoggingFacade(Logger.getLogger(name, resourceBundleName));
  }

}
