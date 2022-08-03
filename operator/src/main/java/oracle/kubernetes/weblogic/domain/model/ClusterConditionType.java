// Copyright (c) 2022, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.weblogic.domain.model;

import com.google.gson.annotations.SerializedName;
import oracle.kubernetes.json.Obsoleteable;

public enum ClusterConditionType implements Obsoleteable {
  @SerializedName("Available")
  AVAILABLE("Available"),
  @SerializedName("Completed")
  COMPLETED("Completed");

  private final String value;

  ClusterConditionType(String value) {
    this.value = value;
  }

  /**
   * Compares two conditions for precedence. Returns a negative number if thisCondition is to be sorted
   * before thatCondition.
   * @param thisCondition the first of two conditions to compare
   * @param thatCondition the second of two conditions to compare
   */
  int compare(ClusterCondition thisCondition, ClusterCondition thatCondition) {
    return thisCondition.compareTransitionTime(thatCondition);
  }

  @Override
  public String toString() {
    return String.valueOf(this.value);
  }
}
