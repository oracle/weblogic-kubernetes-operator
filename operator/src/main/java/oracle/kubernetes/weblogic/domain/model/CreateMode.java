// Copyright (c) 2023, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.weblogic.domain.model;

import com.google.gson.annotations.SerializedName;

public enum CreateMode {
  @SerializedName("CreateDomainIfNotExists")
  CREATE_DOMAIN_IF_NOT_EXISTS("CreateDomainIfNotExists"),
  @SerializedName("CreateDomainWithRcuIfNoExists")
  CREATE_DOMAIN_WITH_RCU_IF_NOT_EXISTS("CreateDomainWithRcuIfNoExists");

  private final String value;

  CreateMode(String value) {
    this.value = value;
  }

  @Override
  public String toString() {
    return String.valueOf(this.value);
  }
}
