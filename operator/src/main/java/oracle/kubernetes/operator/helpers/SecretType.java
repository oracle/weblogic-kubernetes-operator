// Copyright (c) 2019, 2022, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.helpers;

import oracle.kubernetes.operator.Labeled;

/**
 * Types of secrets which can be configured on a domain.
 */
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
}
