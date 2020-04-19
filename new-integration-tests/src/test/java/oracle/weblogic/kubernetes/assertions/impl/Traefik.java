// Copyright (c) 2020, Oracle Corporation and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.weblogic.kubernetes.assertions.impl;

import java.util.concurrent.Callable;


public class Traefik {

  /**
   * Check if the traefik pod is running in a given namespace.
   *
   * @param namespace in which to check for the operator pod
   * @return true if found and running, false otherwise
   */
  public static Callable<Boolean> isRunning(String namespace) {
    return () -> {
      return Kubernetes.isTraefikPodRunning(namespace);
    };
  }

  /**
   * Check if the traefik is ready in a given namespace.
   *
   * @param namespace in which the traefik is running
   * @return true if the traefik is ready, false otherwise
   */
  public static Callable<Boolean> isReady(String namespace) {
    return () -> {
      return Kubernetes.isTraefikPodReady(namespace);
    };
  }

}
