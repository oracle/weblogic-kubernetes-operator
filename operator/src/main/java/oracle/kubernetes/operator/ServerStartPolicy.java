// Copyright (c) 2018, 2022, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator;

import com.google.gson.annotations.SerializedName;

public enum ServerStartPolicy {
  @SerializedName("Always")
  ALWAYS("Always") {
    @Override
    public boolean forDomain() {
      return false;
    }

    @Override
    public boolean forCluster() {
      return false;
    }
  },
  @SerializedName("Never")
  NEVER("Never"),
  @SerializedName("IfNeeded")
  IF_NEEDED("IfNeeded"),
  @SerializedName("AdminOnly")
  ADMIN_ONLY("AdminOnly") {
    @Override
    public boolean forCluster() {
      return false;
    }

    @Override
    public boolean forServer() {
      return false;
    }
  };

  private final String value;

  ServerStartPolicy(String value) {
    this.value = value;
  }

  @Override
  public String toString() {
    return String.valueOf(this.value);
  }

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
