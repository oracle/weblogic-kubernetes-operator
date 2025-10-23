// Copyright (c) 2020, 2022, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.weblogic.kubernetes.assertions.impl;

import java.util.concurrent.Callable;

public class Operator {

  /**
   * Check if the operator pod is running in a given namespace.
   * @param namespace in which to check for the operator pod
   * @return true if found and running otherwise false
   */
  public static Callable<Boolean> isReady(String namespace) {
    return () -> {
      return Kubernetes.isOperatorPodReady(namespace);
    };
  }
  
  /**
   * Check if the operator webhook pod is running in a given namespace.
   * @param namespace in which to check for the operator webhook pod
   * @return true if found running and ready otherwise false
   */
  public static Callable<Boolean> isWebhookReady(String namespace) {
    return () -> {
      return Kubernetes.isWebhookPodReady(namespace);
    };
  }  

}
