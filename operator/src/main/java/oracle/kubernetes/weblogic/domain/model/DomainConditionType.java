// Copyright (c) 2019, 2022, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.weblogic.domain.model;

import java.io.IOException;

import com.google.gson.TypeAdapter;
import com.google.gson.annotations.JsonAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;
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

@JsonAdapter(DomainConditionType.Adapter.class)
public enum DomainConditionType implements Obsoleteable, Labeled {
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
  AVAILABLE("Available", DOMAIN_AVAILABLE, DOMAIN_UNAVAILABLE),
  COMPLETED("Completed", DOMAIN_COMPLETE, DOMAIN_INCOMPLETE),
  CONFIG_CHANGES_PENDING_RESTART("ConfigChangesPendingRestart"),
  ROLLING("Rolling", DOMAIN_ROLL_STARTING, DOMAIN_ROLL_COMPLETED) {
    @Override
    boolean statusMayBeFalse() {
      return false;
    }
  },
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

  /**
   * Locate enum type from value.
   * @param value Value
   * @return Domain condition type
   */
  public static DomainConditionType fromValue(String value) {
    for (DomainConditionType testValue : values()) {
      if (testValue.label.equals(value)) {
        return testValue;
      }
    }

    throw new IllegalArgumentException("Unexpected value '" + value + "'");
  }

  public static class Adapter extends TypeAdapter<DomainConditionType> {
    public void write(JsonWriter jsonWriter, DomainConditionType enumeration) throws IOException {
      jsonWriter.value(enumeration.label());
    }

    public DomainConditionType read(JsonReader jsonReader) throws IOException {
      String value = jsonReader.nextString();
      return DomainConditionType.fromValue(value);
    }
  }
}
