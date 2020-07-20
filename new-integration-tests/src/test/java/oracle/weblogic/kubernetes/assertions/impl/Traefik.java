// Copyright (c) 2020, Oracle Corporation and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.weblogic.kubernetes.assertions.impl;

import java.util.concurrent.Callable;

/**
 * Assertions for Traefik ingress controller.
 */
public class Traefik {

  /**
   * Check if the Traefik pod is running in the specified namespace.
   *
   * @param namespace in which to check if the Traefik pod is running
   * @return true if the Traefik pod is running, false otherwise
   */
  public static Callable<Boolean> isRunning(String namespace) {
    return () -> Kubernetes.isTraefikPodRunning(namespace);
  }

  /**
   * Check if the Traefik pod is ready in the specified namespace.
   *
   * @param namespace in which to check the Traefik pod is ready
   * @return true if the Traefik pod is in the ready state, false otherwise
   */
  public static Callable<Boolean> isReady(String namespace) {
    return () -> Kubernetes.isTraefikPodReady(namespace);
  }
}
