// Copyright (c) 2020, 2022, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator;

import java.io.IOException;

import com.google.gson.TypeAdapter;
import com.google.gson.annotations.JsonAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;

@JsonAdapter(MIINonDynamicChangesMethod.Adapter.class)
public enum MIINonDynamicChangesMethod {
  COMMIT_UPDATE_AND_ROLL("CommitUpdateAndRoll"),
  COMMIT_UPDATE_ONLY("CommitUpdateOnly");

  private final String value;

  MIINonDynamicChangesMethod(String value) {
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
   * @return MII non-dynamic changes method type
   */
  public static MIINonDynamicChangesMethod fromValue(String value) {
    for (MIINonDynamicChangesMethod testValue : values()) {
      if (testValue.value.equals(value)) {
        return testValue;
      }
    }

    throw new IllegalArgumentException("Unexpected value '" + value + "'");
  }

  public static class Adapter extends TypeAdapter<MIINonDynamicChangesMethod> {
    public void write(JsonWriter jsonWriter, MIINonDynamicChangesMethod enumeration) throws IOException {
      jsonWriter.value(enumeration.getValue());
    }

    public MIINonDynamicChangesMethod read(JsonReader jsonReader) throws IOException {
      String value = jsonReader.nextString();
      return MIINonDynamicChangesMethod.fromValue(value);
    }
  }
}
