// Copyright (c) 2020, 2022, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator;

import com.google.gson.annotations.SerializedName;

public enum OverrideDistributionStrategy {
  @SerializedName("Dynamic")
  DYNAMIC("Dynamic"),

  @SerializedName("OnRestart")
  ON_RESTART("OnRestart");

  public static final OverrideDistributionStrategy DEFAULT = DYNAMIC;

  private final String value;

  OverrideDistributionStrategy(String value) {
    this.value = value;
  }

  @Override
  public String toString() {
    return String.valueOf(this.value);
  }
}