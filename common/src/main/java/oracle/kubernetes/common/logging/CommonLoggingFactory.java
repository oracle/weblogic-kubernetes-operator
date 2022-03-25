// Copyright (c) 2022, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.common.logging;

import java.util.HashMap;
import java.util.Map;
import java.util.logging.Logger;

/** A factory to create Loggers. */
public class CommonLoggingFactory {

  // map from resourceBundleName to facade
  private static final Map<String, CommonLoggingFacade> facade = new HashMap<>();

  private CommonLoggingFactory() {
    // hide implicit public constructor
  }

  /**
   * Obtains a Logger from the underlying logging implementation and wraps it in a LoggingFacade.
   *
   * @param name the name of the logger to use
   * @param resourceBundleName the resource bundle to use with this logger
   * @return a PlatformLogger object for the caller to use
   */
  public static synchronized CommonLoggingFacade getLogger(String name, String resourceBundleName) {
    return facade.computeIfAbsent(resourceBundleName, clf -> getCommonLoggingFacade(name, resourceBundleName));
  }

  private static CommonLoggingFacade getCommonLoggingFacade(String name, String resourceBundleName) {
    return new CommonLoggingFacade(Logger.getLogger(name, resourceBundleName));
  }
}
