// Copyright (c) 2019, 2022, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.helpers;

import java.io.IOException;

import com.google.gson.TypeAdapter;
import com.google.gson.annotations.JsonAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;
import oracle.kubernetes.common.Labeled;

/**
 * Types of secrets which can be configured on a domain.
 */
@JsonAdapter(SecretType.Adapter.class)
public enum SecretType implements Labeled {
  WEBLOGIC_CREDENTIALS("WebLogicCredentials"),
  IMAGE_PULL("ImagePull"),
  CONFIG_OVERRIDE("ConfigOverride"),
  RUNTIME_ENCRYPTION("RuntimeEncryption"),
  OPSS_WALLET_PASSWORD("OpssWalletPassword"),
  OPSS_WALLET_FILE("OpssWalletFile");

  private final String label;

  SecretType(String label) {
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
   * @return Secret type
   */
  public static SecretType fromValue(String value) {
    for (SecretType testValue : values()) {
      if (testValue.label.equals(value)) {
        return testValue;
      }
    }

    throw new IllegalArgumentException("Unexpected value '" + value + "'");
  }

  public static class Adapter extends TypeAdapter<SecretType> {
    public void write(JsonWriter jsonWriter, SecretType enumeration) throws IOException {
      jsonWriter.value(enumeration.label());
    }

    public SecretType read(JsonReader jsonReader) throws IOException {
      String value = jsonReader.nextString();
      return SecretType.fromValue(value);
    }
  }
}
