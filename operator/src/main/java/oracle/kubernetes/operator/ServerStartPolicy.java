// Copyright (c) 2018, 2020, Oracle Corporation and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

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

  public static ServerStartPolicy getDefaultPolicy() {
    return IF_NEEDED;
  }

  public boolean forDomain() {
    return true;
  }

  public boolean forCluster() {
    return true;
  }

  public boolean forServer() {
    return true;
  }
}
