// Copyright 2018, Oracle Corporation and/or its affiliates.  All rights reserved.
// Licensed under the Universal Permissive License v 1.0 as shown at
// http://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator;

public enum ServerStartPolicy {
  ALWAYS {
    @Override
    public boolean forDomain() {
      return false;
    }

    @Override
    public boolean forCluster() {
      return false;
    }
  },
  NEVER,
  IF_NEEDED,
  ADMIN_ONLY {
    @Override
    public boolean forCluster() {
      return false;
    }

    @Override
    public boolean forServer() {
      return false;
    }
  };

  public boolean forDomain() {
    return true;
  }

  public boolean forCluster() {
    return true;
  }

  public boolean forServer() {
    return true;
  }

  public static ServerStartPolicy getDefaultPolicy() {
    return IF_NEEDED;
  }
}
