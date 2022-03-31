// Copyright (c) 2019, 2022, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator;

import com.google.gson.annotations.SerializedName;

public enum ShutdownType {
  @SerializedName("Graceful")
  GRACEFUL("Graceful"),
  @SerializedName("Forced")
  FORCED("Forced");

  private final String value;

  ShutdownType(String value) {
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
   * @return Shutdown type
   */
  public static ShutdownType fromValue(String value) {
    for (ShutdownType testValue : values()) {
      if (testValue.value.equals(value)) {
        return testValue;
      }
    }

    throw new IllegalArgumentException("Unexpected value '" + value + "'");
  }
}
