// Copyright 2018, Oracle Corporation and/or its affiliates.  All rights reserved.
// Licensed under the Universal Permissive License v 1.0 as shown at
// http://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator;

public enum ServerStartPolicy {
  NEVER,
  ALWAYS,
  IF_NEEDED,
  ADMIN_ONLY;

  public static ServerStartPolicy getDefaultPolicy() {
    return IF_NEEDED;
  }
}
