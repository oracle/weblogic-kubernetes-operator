// Copyright (c) 2019, 2022, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator;

import java.io.IOException;

import com.google.gson.TypeAdapter;
import com.google.gson.annotations.JsonAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;
import oracle.kubernetes.common.Labeled;

@JsonAdapter(ShutdownType.Adapter.class)
public enum ShutdownType implements Labeled {
  GRACEFUL("Graceful"),
  FORCED("Forced");

  private final String label;

  ShutdownType(String label) {
    this.label = label;
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
   * @return Shutdown type
   */
  public static ShutdownType fromValue(String value) {
    for (ShutdownType testValue : values()) {
      if (testValue.label.equals(value)) {
        return testValue;
      }
    }

    throw new IllegalArgumentException("Unexpected value '" + value + "'");
  }

  public static class Adapter extends TypeAdapter<ShutdownType> {
    public void write(JsonWriter jsonWriter, ShutdownType enumeration) throws IOException {
      jsonWriter.value(enumeration.label());
    }

    public ShutdownType read(JsonReader jsonReader) throws IOException {
      String value = jsonReader.nextString();
      return ShutdownType.fromValue(value);
    }
  }
}
