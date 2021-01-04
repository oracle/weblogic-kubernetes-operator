// Copyright (c) 2020, 2021, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.weblogic.kubernetes.assertions.impl;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Callable;

public class Prometheus {

  /**
   * Check if the prometheus pods are running in a given namespace.
   * @param namespace in which to check for the prometheus pods
   * @return true if found and running otherwise false
   */
  public static Callable<Boolean> isReady(String namespace) {
    Map<String,String> labelMapPromSvc = new HashMap<>();
    labelMapPromSvc.put("component", "server");
    Map<String,String> labelMapAlertMgr = new HashMap<>();
    labelMapAlertMgr.put("component", "alertmanager");
    return () -> {
      return (Kubernetes.isPodReady(namespace, labelMapAlertMgr, "prometheus-alertmanager")
              && Kubernetes.isPodReady(namespace, labelMapPromSvc, "prometheus-server"));
    };
  }
}
