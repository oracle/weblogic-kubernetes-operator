// Copyright (c) 2023, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.weblogic.domain;

import com.google.gson.annotations.SerializedName;

public enum CreateIfNotExists {
  @SerializedName("domain")
  DOMAIN("domain"),
  @SerializedName("domainAndRCU")
  DOMAIN_AND_RCU("domainAndRCU");

  private final String value;

  CreateIfNotExists(String value) {
    this.value = value;
  }

  @Override
  public String toString() {
    return String.valueOf(this.value);
  }
}
