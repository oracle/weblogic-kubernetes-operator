// Copyright (c) 2020, Oracle Corporation and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.weblogic.kubernetes.actions.impl;

import oracle.weblogic.kubernetes.actions.impl.primitive.Helm;

public class Operator extends Helm {
  public static boolean scaleDomain(String domainUID, String clusterName, int numOfServers) {
    return true;
  }
}
