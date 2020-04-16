// Copyright (c) 2020, Oracle Corporation and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.weblogic.kubernetes.assertions.impl;

import java.util.concurrent.Callable;


public class Traefik {

  /**
   * Check if the traefik pod is running in a given namespace.
   * @param namespace in which to check for the operator pod
   * @return true if found and running otherwise false
   */
  public static Callable<Boolean> isRunning(String namespace) {
    return () -> {
      return Kubernetes.isTraefikPodRunning(namespace);
    };
  }

}
