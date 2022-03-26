// Copyright (c) 2021, 2022, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.weblogic.domain.model;

import com.google.gson.annotations.SerializedName;
import oracle.kubernetes.operator.Labeled;

public enum SessionAffinity implements Labeled {
  @SerializedName("ClientIP")
  CLIENT_IP("ClientIP"),
  @SerializedName("None")
  NONE("None");

  private final String label;

  SessionAffinity(String label) {
    this.label = label;
  }

  @Override
  public String label() {
    return label;
  }

  @Override
  public String toString() {
    return label();
  }
}
