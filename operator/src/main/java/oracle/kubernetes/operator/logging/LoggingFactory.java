// Copyright 2017, Oracle Corporation and/or its affiliates.  All rights reserved.
// Licensed under the Universal Permissive License v 1.0 as shown at http://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.logging;

import java.util.HashMap;
import java.util.Map;
import java.util.logging.Logger;

import io.kubernetes.client.JSON;

/**
 * A factory to create Loggers.
 */
public class LoggingFactory {

  // map from resourceBundleName to facade
  private static final Map<String, LoggingFacade> facade = new HashMap<String, LoggingFacade>();
  
  private static JSON json = null;
  
  public static void setJSON(JSON json) {
    LoggingFactory.json = json;
  }
  
  static JSON getJSON() {
    return json;
  }

  private LoggingFactory() {
    // hide implicit public constructor
  }

  /**
   * Obtains a Logger from the underlying logging implementation and wraps it in a LoggingFacade.
   *
   * @param name               the name of the logger to use
   * @param resourceBundleName the resource bundle to use with this logger
   * @return a PlatformLogger object for the caller to use
   */
  public synchronized static LoggingFacade getLogger(String name, String resourceBundleName) {

    LoggingFacade lf = facade.get(resourceBundleName);
    if (lf == null) {
      Logger logger = Logger.getLogger(name, resourceBundleName);
      lf = new LoggingFacade(logger);
      facade.put(resourceBundleName, lf);
    }

    return lf;
  }
}
