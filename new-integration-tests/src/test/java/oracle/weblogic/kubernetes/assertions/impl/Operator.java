// Copyright (c) 2020, Oracle Corporation and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.weblogic.kubernetes.assertions.impl;

import java.util.concurrent.Callable;

import oracle.weblogic.kubernetes.extensions.LoggedTest;


public class Operator implements LoggedTest {



  public static Callable<Boolean> isRunning(String namespace) {
    return () -> {
      return Kubernetes.isOperatorPodRunning(namespace);
    };
  }

  public static boolean isRestServiceCreated(String namespace) {
    return true;
  }

}
