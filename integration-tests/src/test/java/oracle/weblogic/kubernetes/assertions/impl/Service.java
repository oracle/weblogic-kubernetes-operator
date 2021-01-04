// Copyright (c) 2020, 2021, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.weblogic.kubernetes.assertions.impl;

import java.util.Map;
import java.util.concurrent.Callable;

public class Service {

  /**
   * Check is a service exists in given namespace.
   *
   * @param serviceName the name of the service to check for
   * @param label a Map of key value pairs the service is decorated with
   * @param namespace in which the service is running
   * @return true if the service exists otherwise false
   */
  public static Callable<Boolean> serviceExists(String serviceName, Map<String, String> label,
      String namespace) {
    return () -> {
      return Kubernetes.doesServiceExist(serviceName, label, namespace);
    };
  }
}
