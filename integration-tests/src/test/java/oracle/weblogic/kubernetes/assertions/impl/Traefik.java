// Copyright (c) 2020, 2021, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.weblogic.kubernetes.assertions.impl;

import java.util.concurrent.Callable;

/**
 * Assertions for traefik ingress controller.
 */
public class Traefik {

  /**
   * Check if the traefik pod is running in the specified namespace.
   *
   * @param namespace in which to check for the running traefik pod
   * @return true if the traefik pod is running, false otherwise
   */
  public static Callable<Boolean> isRunning(String namespace) {
    return () -> Kubernetes.isTraefikPodRunning(namespace);
  }

  /**
   * Check if the traefik pod is ready in the specified namespace.
   *
   * @param namespace in which to check for the traefik pod readiness
   * @return true if the traefik pod is in ready state, false otherwise
   */
  public static Callable<Boolean> isReady(String namespace) {
    return () -> Kubernetes.isTraefikPodReady(namespace);
  }
}
