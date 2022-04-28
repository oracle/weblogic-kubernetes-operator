// Copyright (c) 2019, 2022, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.weblogic.domain.model;

import com.google.gson.annotations.SerializedName;
import oracle.kubernetes.json.Obsoleteable;
import oracle.kubernetes.operator.helpers.EventHelper;

import static oracle.kubernetes.operator.helpers.EventHelper.EventItem.DOMAIN_AVAILABLE;
import static oracle.kubernetes.operator.helpers.EventHelper.EventItem.DOMAIN_COMPLETE;
import static oracle.kubernetes.operator.helpers.EventHelper.EventItem.DOMAIN_FAILURE_RESOLVED;
import static oracle.kubernetes.operator.helpers.EventHelper.EventItem.DOMAIN_INCOMPLETE;
import static oracle.kubernetes.operator.helpers.EventHelper.EventItem.DOMAIN_ROLL_COMPLETED;
import static oracle.kubernetes.operator.helpers.EventHelper.EventItem.DOMAIN_ROLL_STARTING;
import static oracle.kubernetes.operator.helpers.EventHelper.EventItem.DOMAIN_UNAVAILABLE;

public enum DomainConditionType implements Obsoleteable {
  @SerializedName("Failed")
  FAILED("Failed",null, DOMAIN_FAILURE_RESOLVED) {
    @Override
    int compare(DomainCondition thisCondition, DomainCondition thatCondition) {
      if (compareUsingSeverities(thisCondition, thatCondition)) {
        return thisCondition.getSeverity().compareTo(thatCondition.getSeverity());
      } else {
        return super.compare(thisCondition, thatCondition);
      }
    }

    private boolean compareUsingSeverities(DomainCondition thisCondition, DomainCondition thatCondition) {
      return thatCondition.getType() == FAILED && thisCondition.getSeverity() != thatCondition.getSeverity();
    }

    @Override
    boolean allowMultipleConditionsWithThisType() {
      return true;
    }

    @Override
    boolean statusMayBeFalse() {
      return false;
    }
  },
  @SerializedName("Available")
  AVAILABLE("Available", DOMAIN_AVAILABLE, DOMAIN_UNAVAILABLE),
  @SerializedName("Completed")
  COMPLETED("Completed", DOMAIN_COMPLETE, DOMAIN_INCOMPLETE),
  @SerializedName("ConfigChangesPendingRestart")
  CONFIG_CHANGES_PENDING_RESTART("ConfigChangesPendingRestart"),
  @SerializedName("Rolling")
  ROLLING("Rolling", DOMAIN_ROLL_STARTING, DOMAIN_ROLL_COMPLETED) {
    @Override
    boolean statusMayBeFalse() {
      return false;
    }
  },
  @SerializedName("Progressing")
  PROGRESSING("Progressing") {
    @Override
    public boolean isObsolete() {
      return true;
    }
  };

  private final String value;
  private final EventHelper.EventItem addedEvent;
  private final EventHelper.EventItem removedEvent;

  DomainConditionType(String value, EventHelper.EventItem addedEvent, EventHelper.EventItem removedEvent) {
    this.value = value;
    this.addedEvent = addedEvent;
    this.removedEvent = removedEvent;
  }

  DomainConditionType(String label) {
    this(label, null, null);
  }

  /**
   * Compares two conditions for precedence. Returns a negative number if thisCondition is to be sorted
   * before thatCondition.
   * @param thisCondition the first of two conditions to compare
   * @param thatCondition the second of two conditions to compare
   */
  int compare(DomainCondition thisCondition, DomainCondition thatCondition) {
    return thisCondition.compareTransitionTime(thatCondition);
  }

  boolean allowMultipleConditionsWithThisType() {
    return false;
  }

  boolean statusMayBeFalse() {
    return true;
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
