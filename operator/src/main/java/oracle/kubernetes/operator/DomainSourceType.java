// Copyright (c) 2020, 2022, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator;

import java.io.IOException;

import com.google.gson.TypeAdapter;
import com.google.gson.annotations.JsonAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;

@JsonAdapter(DomainSourceType.Adapter.class)
public enum DomainSourceType {
  IMAGE("Image") {
    @Override
    public String getDefaultDomainHome(String uid) {
      return "/u01/oracle/user_projects/domains";
    }
  },
  PERSISTENT_VOLUME("PersistentVolume") {
    @Override
    public boolean hasLogHomeByDefault() {
      return true;
    }

    @Override
    public String getDefaultDomainHome(String uid) {
      return "/shared/domains/" + uid;
    }
  },
  FROM_MODEL("FromModel") {
    @Override
    public String getDefaultDomainHome(String uid) {
      return "/u01/domains/" + uid;
    }

  };

  public boolean hasLogHomeByDefault() {
    return false;
  }

  public abstract String getDefaultDomainHome(String uid);

  private final String value;

  DomainSourceType(String value) {
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
   * @return Domain source type
   */
  public static DomainSourceType fromValue(String value) {
    for (DomainSourceType testValue : values()) {
      if (testValue.value.equals(value)) {
        return testValue;
      }
    }

    throw new IllegalArgumentException("Unexpected value '" + value + "'");
  }

  public static class Adapter extends TypeAdapter<DomainSourceType> {
    public void write(JsonWriter jsonWriter, DomainSourceType enumeration) throws IOException {
      jsonWriter.value(enumeration.getValue());
    }

    public DomainSourceType read(JsonReader jsonReader) throws IOException {
      String value = jsonReader.nextString();
      return DomainSourceType.fromValue(value);
    }
  }
}
