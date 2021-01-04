// Copyright (c) 2020, 2021, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.weblogic.kubernetes.assertions.impl;

import java.util.concurrent.Callable;

/**
 * Assertions for Voyager ingress controller.
 */
public class Voyager {

  /**
   * Check if the Voyager pod is running in a given namespace.
   *
   * @param namespace in which to check if the Voyager pod is running
   * @param podName name of Voyager ingress controller pod or ingress resource pod
   * @return true if the Voyager pod is running, false otherwise
   */
  public static Callable<Boolean> isRunning(String namespace, String podName) {
    String labelSelector = null;
    return () -> Kubernetes.isPodRunning(namespace, labelSelector, podName);
  }

  /**
   * Check if the Voyager pod is ready in a given namespace.
   *
   * @param namespace in which to check the Voyager pod is ready
   * @param podName name of Voyager ingress controller pod or ingress resource pod
   * @return true if the Voyager pod is in the ready state, false otherwise
   */
  public static Callable<Boolean> isReady(String namespace, String podName) {
    String labelSelector = null;
    return () -> Kubernetes.isPodReady(namespace, labelSelector, podName);
  }
}
