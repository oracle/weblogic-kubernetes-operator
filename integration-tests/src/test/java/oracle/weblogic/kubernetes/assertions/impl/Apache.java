// Copyright (c) 2020, 2021, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.weblogic.kubernetes.assertions.impl;

import java.util.concurrent.Callable;

/**
 * Assertions for Apache load balancer.
 */
public class Apache {

  /**
   * Check if the Apache pod is running in the specified namespace.
   *
   * @param namespace in which to check if the Apache pod is running
   * @return true if the Apache pod is running, false otherwise
   */
  public static Callable<Boolean> isRunning(String namespace) {
    return () -> Kubernetes.isApachePodRunning(namespace);
  }

  /**
   * Check if the Apache pod is ready in the specified namespace.
   *
   * @param namespace in which to check the Apache pod is ready
   * @return true if the Apache pod is in the ready state, false otherwise
   */
  public static Callable<Boolean> isReady(String namespace) {
    return () -> Kubernetes.isApachePodReady(namespace);
  }
}
