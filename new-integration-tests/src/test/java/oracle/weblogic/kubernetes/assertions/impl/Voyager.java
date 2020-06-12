// Copyright (c) 2020, Oracle Corporation and/or its affiliates.
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
   * @return true if the Voyager pod is running, false otherwise
   */
  public static Callable<Boolean> isRunning(String namespace) {
    return () -> Kubernetes.isVoyagerPodRunning(namespace);
  }

  /**
   * Check if the Voyager pod is ready in a given namespace.
   *
   * @param namespace in which to check the Voyager pod is ready
   * @return true if the Voyager pod is in the ready state, false otherwise
   */
  public static Callable<Boolean> isReady(String namespace) {
    return () -> Kubernetes.isVoyagerPodReady(namespace);
  }

  /**
   * Check if the Voyager ingress pod is running in a given namespace and pod name.
   *
   * @param namespace in which to check if the Voyager ingress pod is running
   * @param podName name of Voyager ingress pod to check
   * @return true if the Voyager ingress pod is running, false otherwise
   */
  public static Callable<Boolean> isIngressRunning(String namespace, String podName) {
    return () -> Kubernetes.isVoyagerIngressRunning(namespace, podName);
  }

  /**
   * Check if the Voyager pod is ready in a given namespace and pod name.
   *
   * @param namespace in which to check the Voyager ingress pod is ready
   * @param podName name of Voyager ingress pod to check
   * @return true if the Voyager ingress pod is in the ready state, false otherwise
   */
  public static Callable<Boolean> isIngressReady(String namespace, String podName) {
    return () -> Kubernetes.isVoyagerIngressReady(namespace, podName);
  }

}
