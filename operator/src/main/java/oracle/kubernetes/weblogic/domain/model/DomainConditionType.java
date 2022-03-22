// Copyright (c) 2019, 2022, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.weblogic.domain.model;

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
  Failed(null, DOMAIN_FAILURE_RESOLVED) {
    @Override
    boolean allowMultipleConditionsWithThisType() {
      return true;
    }

    @Override
    boolean statusMayBeFalse() {
      return false;
    }
  },
  Available(DOMAIN_AVAILABLE, DOMAIN_UNAVAILABLE),
  Completed(DOMAIN_COMPLETE, DOMAIN_INCOMPLETE),
  ConfigChangesPendingRestart,
  Rolling(DOMAIN_ROLL_STARTING,DOMAIN_ROLL_COMPLETED) {
    @Override
    boolean statusMayBeFalse() {
      return false;
    }
  },
  Progressing {
    @Override
    public boolean isObsolete() {
      return true;
    }
  };

  private final EventHelper.EventItem addedEvent;
  private final EventHelper.EventItem removedEvent;

  DomainConditionType(EventHelper.EventItem addedEvent, EventHelper.EventItem removedEvent) {
    this.addedEvent = addedEvent;
    this.removedEvent = removedEvent;
  }

  DomainConditionType() {
    this(null, null);
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

}
