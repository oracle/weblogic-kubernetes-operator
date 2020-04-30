// Copyright (c) 2020, Oracle Corporation and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.weblogic.kubernetes.assertions.impl;

import java.util.concurrent.Callable;

/**
 * Assertions for Nginx ingress controller.
 */
public class Nginx {

  /**
   * Check if the Nginx pod is running in the specified namespace.
   *
   * @param namespace in which to check if the Nginx pod is running
   * @return true if the Nginx pod is running, false otherwise
   */
  public static Callable<Boolean> isRunning(String namespace) {
    return () -> {
      return Kubernetes.isNginxPodRunning(namespace);
    };
  }

  /**
   * Check if the Nginx pod is ready in the specified namespace.
   *
   * @param namespace in which to check the Nginx pod is ready
   * @return true if the Nginx pod is in the ready state, false otherwise
   */
  public static Callable<Boolean> isReady(String namespace) {
    return () -> {
      return Kubernetes.isNginxPodReady(namespace);
    };
  }

}
