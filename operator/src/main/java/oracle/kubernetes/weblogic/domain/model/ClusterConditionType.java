// Copyright (c) 2022, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.weblogic.domain.model;

import com.google.gson.annotations.SerializedName;
import oracle.kubernetes.json.Obsoleteable;
import oracle.kubernetes.operator.helpers.EventHelper;

import static oracle.kubernetes.operator.helpers.EventHelper.EventItem.CLUSTER_AVAILABLE;
import static oracle.kubernetes.operator.helpers.EventHelper.EventItem.CLUSTER_COMPLETE;
import static oracle.kubernetes.operator.helpers.EventHelper.EventItem.CLUSTER_INCOMPLETE;
import static oracle.kubernetes.operator.helpers.EventHelper.EventItem.CLUSTER_UNAVAILABLE;

public enum ClusterConditionType implements Obsoleteable {
  @SerializedName("Available")
  AVAILABLE("Available", CLUSTER_AVAILABLE, CLUSTER_UNAVAILABLE),
  @SerializedName("Completed")
  COMPLETED("Completed", CLUSTER_COMPLETE, CLUSTER_INCOMPLETE);

  private final String value;
  private final EventHelper.EventItem addedEvent;
  private final EventHelper.EventItem removedEvent;

  ClusterConditionType(String value, EventHelper.EventItem addedEvent, EventHelper.EventItem removedEvent) {
    this.value = value;
    this.addedEvent = addedEvent;
    this.removedEvent = removedEvent;
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

  public EventHelper.EventItem getAddedEvent() {
    return addedEvent;
  }

  public EventHelper.EventItem getRemovedEvent() {
    return removedEvent;
  }

  @Override
  public String toString() {
    return String.valueOf(this.value);
  }
}
