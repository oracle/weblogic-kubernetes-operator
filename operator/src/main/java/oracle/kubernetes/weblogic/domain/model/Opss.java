// Copyright (c) 2020, Oracle Corporation and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.weblogic.domain.model;

import javax.validation.Valid;

import io.kubernetes.client.openapi.models.V1SecretReference;
import oracle.kubernetes.json.Description;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.apache.commons.lang3.builder.ToStringBuilder;

public class Opss {

  @Description("The name of the config map containing the OPSS key wallet file")
  private String keyWalletConfigMap;

  @Description(
      "Secret containing OPSS key passphrase.")
  @Valid
  private V1SecretReference walletSecret;

  public String getKeyWalletConfigMap() {
    return this.keyWalletConfigMap;
  }

  public void setKeyWalletConfigMap(String keyWalletConfigMap) {
    this.keyWalletConfigMap = keyWalletConfigMap;
  }

  public Opss withKeyWalletConfigMap(String keyWalletConfigMap) {
    this.keyWalletConfigMap = keyWalletConfigMap;
    return this;
  }

  public V1SecretReference getWalletSecret() {
    return this.walletSecret;
  }

  public void setWalletSecret(V1SecretReference walletSecret) {
    this.walletSecret = walletSecret;
  }

  public Opss withWalletSecret(V1SecretReference walletSecret) {
    this.walletSecret = walletSecret;
    return this;
  }

  @Override
  public String toString() {
    ToStringBuilder builder =
        new ToStringBuilder(this)
            .append("keyWalletConfigMap", keyWalletConfigMap)
            .append("walletSecret", walletSecret);

    return builder.toString();
  }

  @Override
  public int hashCode() {
    HashCodeBuilder builder = new HashCodeBuilder()
        .append(keyWalletConfigMap)
        .append(walletSecret);

    return builder.toHashCode();
  }

  @Override
  public boolean equals(Object other) {
    if (other == this) {
      return true;
    }
    if (!(other instanceof Opss)) {
      return false;
    }

    Opss rhs = ((Opss) other);
    EqualsBuilder builder =
        new EqualsBuilder()
            .append(keyWalletConfigMap, rhs.keyWalletConfigMap)
            .append(walletSecret, rhs.walletSecret);

    return builder.isEquals();
  }
}
