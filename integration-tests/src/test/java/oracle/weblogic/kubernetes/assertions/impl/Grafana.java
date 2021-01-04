// Copyright (c) 2020, 2021, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.weblogic.kubernetes.assertions.impl;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Callable;

public class Grafana {

  /**
   * Check if the grafana pod is running in a given namespace.
   * @param namespace in which to check for the grafana pod
   * @return true if found and running otherwise false
   */
  public static Callable<Boolean> isReady(String namespace) {
    return () -> {
      Map<String,String> labelMap = new HashMap<>();
      labelMap.put("app.kubernetes.io/name", "grafana");
      return Kubernetes.isPodReady(namespace, labelMap, "grafana");
    };
  }
}
