// Copyright (c) 2019, 2022, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.helpers;

import com.google.gson.annotations.SerializedName;

/**
 * Types of secrets which can be configured on a domain.
 */
public enum SecretType {
  @SerializedName("WebLogicCredentials")
  WEBLOGIC_CREDENTIALS("WebLogicCredentials"),
  @SerializedName("ImagePull")
  IMAGE_PULL("ImagePull"),
  @SerializedName("ConfigOverride")
  CONFIG_OVERRIDE("ConfigOverride"),
  @SerializedName("RuntimeEncryption")
  RUNTIME_ENCRYPTION("RuntimeEncryption"),
  @SerializedName("OpssWalletPassword")
  OPSS_WALLET_PASSWORD("OpssWalletPassword"),
  @SerializedName("OpssWalletFile")
  OPSS_WALLET_FILE("OpssWalletFile");

  private final String value;

  SecretType(String value) {
    this.value = value;
  }

  @Override
  public String toString() {
    return String.valueOf(this.value);
  }
}
