// Copyright (c) 2020, Oracle Corporation and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.weblogic.kubernetes.assertions.impl;

import java.util.concurrent.Callable;


public class Traefik {

  /**
   * Check if the traefik pod is running in a given namespace.
   *
   * @param namespace in which the traefik pod is running
   * @return true if the traefik pod is running, false otherwise
   */
  public static Callable<Boolean> isRunning(String namespace) {
    return () -> {
      return Kubernetes.isTraefikPodRunning(namespace);
    };
  }

  /**
   * Check if the traefik is ready in a given namespace.
   *
   * @param namespace in which the traefik pod is running
   * @return true if the traefik pod is ready, false otherwise
   */
  public static Callable<Boolean> isReady(String namespace) {
    return () -> {
      return Kubernetes.isTraefikPodReady(namespace);
    };
  }

}
