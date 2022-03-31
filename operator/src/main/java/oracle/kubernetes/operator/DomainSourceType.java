// Copyright (c) 2020, 2022, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator;

import com.google.gson.annotations.SerializedName;

public enum DomainSourceType {
  @SerializedName("Image")
  IMAGE("Image") {
    @Override
    public String getDefaultDomainHome(String uid) {
      return "/u01/oracle/user_projects/domains";
    }
  },
  @SerializedName("PersistentVolume")
  PERSISTENT_VOLUME("PersistentVolume") {
    @Override
    public boolean hasLogHomeByDefault() {
      return true;
    }

    @Override
    public String getDefaultDomainHome(String uid) {
      return "/shared/domains/" + uid;
    }
  },
  @SerializedName("FromModel")
  FROM_MODEL("FromModel") {
    @Override
    public String getDefaultDomainHome(String uid) {
      return "/u01/domains/" + uid;
    }

  };

  public boolean hasLogHomeByDefault() {
    return false;
  }

  public abstract String getDefaultDomainHome(String uid);

  private final String value;

  DomainSourceType(String value) {
    this.value = value;
  }

  @Override
  public String toString() {
    return String.valueOf(this.value);
  }
}
