// Copyright (c) 2019, 2022, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.helpers;

import java.io.IOException;

import com.google.gson.TypeAdapter;
import com.google.gson.annotations.JsonAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;

/**
 * Types of secrets which can be configured on a domain.
 */
@JsonAdapter(SecretType.Adapter.class)
public enum SecretType {
  WEBLOGIC_CREDENTIALS("WebLogicCredentials"),
  IMAGE_PULL("ImagePull"),
  CONFIG_OVERRIDE("ConfigOverride"),
  RUNTIME_ENCRYPTION("RuntimeEncryption"),
  OPSS_WALLET_PASSWORD("OpssWalletPassword"),
  OPSS_WALLET_FILE("OpssWalletFile");

  private final String value;

  SecretType(String value) {
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
   * @return Secret type
   */
  public static SecretType fromValue(String value) {
    for (SecretType testValue : values()) {
      if (testValue.value.equals(value)) {
        return testValue;
      }
    }

    throw new IllegalArgumentException("Unexpected value '" + value + "'");
  }

  public static class Adapter extends TypeAdapter<SecretType> {
    public void write(JsonWriter jsonWriter, SecretType enumeration) throws IOException {
      jsonWriter.value(enumeration.getValue());
    }

    public SecretType read(JsonReader jsonReader) throws IOException {
      String value = jsonReader.nextString();
      return SecretType.fromValue(value);
    }
  }
}
