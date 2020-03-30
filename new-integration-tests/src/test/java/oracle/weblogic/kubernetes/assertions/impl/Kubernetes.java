// Copyright (c) 2020, Oracle Corporation and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.weblogic.kubernetes.assertions.impl;

import java.util.concurrent.Callable;

public class Kubernetes {

  public static Callable<Boolean> podExists(String podName, String domainUID, String namespace) {
    return () -> {
      return true;
    };
  }

  public static Callable<Boolean> podRunning(String podName, String domainUID, String namespace) {
    return () -> {
      return true;
    };
  }

  public static Callable<Boolean> podTerminating(String podName, String domainUID, String namespace) {
    return () -> {
      return true;
    };
  }

  public static boolean serviceCreated(String domainUID, String namespace) {
    return true;
  }

  public static boolean loadBalancerReady(String domainUID) {
    return true;
  }

  public static boolean adminServerReady(String domainUID, String namespace) {
    return true;
  }
}
