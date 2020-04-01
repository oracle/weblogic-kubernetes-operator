// Copyright (c) 2020, Oracle Corporation and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.weblogic.kubernetes.assertions.impl;

import java.util.concurrent.Callable;

public class Domain {

  public static Callable<Boolean> exists(String domainUID, String namespace) {
    return () -> {
      String[] pods = {};
      for (String pod : pods) {
        if (!Kubernetes.podRunning(pod, domainUID, namespace)) {
          return false;
        }
      }
      return true;
    };
  }

  public static boolean adminT3ChannelAccessible(String domainUID, String namespace) {
    return true;
  }

  public static boolean adminNodePortAccessible(String domainUID, String namespace) {
    return true;
  }

}
