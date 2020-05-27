// Copyright (c) 2020, Oracle Corporation and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.weblogic.kubernetes.assertions.impl;

import java.util.HashMap;
import java.util.concurrent.Callable;

import io.kubernetes.client.openapi.ApiException;


public class Prometheus {

  /**
   * Check if the prometheus pods are running in a given namespace.
   * @param namespace in which to check for the prometheus pods
   * @return true if found and running otherwise false
   */
  public static Callable<Boolean> isReady(String namespace) {
    return () -> {
      return Kubernetes.arePrometheusPodsReady(namespace);
    };
  }
}
