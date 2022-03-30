// Copyright (c) 2019, 2022, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator;

import java.io.IOException;

import com.google.gson.TypeAdapter;
import com.google.gson.annotations.JsonAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;

@JsonAdapter(ModelInImageDomainType.Adapter.class)
public enum ModelInImageDomainType {
  WLS("WLS"),
  RESTRICTED_JRF("RestrictedJRF"),
  JRF("JRF");

  private final String value;

  ModelInImageDomainType(String value) {
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
   * @return Model in image domain type
   */
  public static ModelInImageDomainType fromValue(String value) {
    for (ModelInImageDomainType testValue : values()) {
      if (testValue.value.equals(value)) {
        return testValue;
      }
    }

    throw new IllegalArgumentException("Unexpected value '" + value + "'");
  }

  public static class Adapter extends TypeAdapter<ModelInImageDomainType> {
    public void write(JsonWriter jsonWriter, ModelInImageDomainType enumeration) throws IOException {
      jsonWriter.value(enumeration.getValue());
    }

    public ModelInImageDomainType read(JsonReader jsonReader) throws IOException {
      String value = jsonReader.nextString();
      return ModelInImageDomainType.fromValue(value);
    }
  }
}
