// Copyright (c) 2020, 2022, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator;

import com.google.gson.annotations.SerializedName;

public enum MIINonDynamicChangesMethod implements Labeled {
  @SerializedName("CommitUpdateAndRoll")
  COMMIT_UPDATE_AND_ROLL("CommitUpdateAndRoll"),
  @SerializedName("CommitUpdateOnly")
  COMMIT_UPDATE_ONLY("CommitUpdateOnly");

  private final String label;

  MIINonDynamicChangesMethod(String label) {
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
