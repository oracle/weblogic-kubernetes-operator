// Copyright (c) 2020, 2022, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator;

import com.google.gson.annotations.SerializedName;

public enum MIINonDynamicChangesMethod {
  @SerializedName("CommitUpdateAndRoll")
  COMMIT_UPDATE_AND_ROLL("CommitUpdateAndRoll"),
  @SerializedName("CommitUpdateOnly")
  COMMIT_UPDATE_ONLY("CommitUpdateOnly");

  private final String value;

  MIINonDynamicChangesMethod(String value) {
    this.value = value;
  }

  public String getValue() {
    return this.value;
  }

  @Override
  public String toString() {
    return String.valueOf(this.value);
  }

  /**
   * Locate enum type from value.
   * @param value Value
   * @return MII non-dynamic changes method type
   */
  public static MIINonDynamicChangesMethod fromValue(String value) {
    for (MIINonDynamicChangesMethod testValue : values()) {
      if (testValue.value.equals(value)) {
        return testValue;
      }
    }

    throw new IllegalArgumentException("Unexpected value '" + value + "'");
  }
}
