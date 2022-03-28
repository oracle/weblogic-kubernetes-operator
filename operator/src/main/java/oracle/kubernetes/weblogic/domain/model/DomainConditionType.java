// Copyright (c) 2019, 2022, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.weblogic.domain.model;

import com.google.gson.annotations.SerializedName;
import oracle.kubernetes.common.Labeled;
import oracle.kubernetes.json.Obsoleteable;
import oracle.kubernetes.operator.helpers.EventHelper;

import static oracle.kubernetes.operator.helpers.EventHelper.EventItem.DOMAIN_AVAILABLE;
import static oracle.kubernetes.operator.helpers.EventHelper.EventItem.DOMAIN_COMPLETE;
import static oracle.kubernetes.operator.helpers.EventHelper.EventItem.DOMAIN_FAILURE_RESOLVED;
import static oracle.kubernetes.operator.helpers.EventHelper.EventItem.DOMAIN_INCOMPLETE;
import static oracle.kubernetes.operator.helpers.EventHelper.EventItem.DOMAIN_ROLL_COMPLETED;
import static oracle.kubernetes.operator.helpers.EventHelper.EventItem.DOMAIN_ROLL_STARTING;
import static oracle.kubernetes.operator.helpers.EventHelper.EventItem.DOMAIN_UNAVAILABLE;

public enum DomainConditionType implements Obsoleteable, Labeled {
  @SerializedName("Failed")
  FAILED("Failed",null, DOMAIN_FAILURE_RESOLVED) {
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

  private final String label;
  private final EventHelper.EventItem addedEvent;
  private final EventHelper.EventItem removedEvent;

  DomainConditionType(String label, EventHelper.EventItem addedEvent, EventHelper.EventItem removedEvent) {
    this.label = label;
    this.addedEvent = addedEvent;
    this.removedEvent = removedEvent;
  }

  DomainConditionType(String label) {
    this(label, null, null);
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
  public String label() {
    return label;
  }

  @Override
  public String toString() {
    return label();
  }
}
