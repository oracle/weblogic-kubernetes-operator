// Copyright (c) 2020, Oracle Corporation and/or its affiliates.  All rights reserved.
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
  private String walletConfigMap;

  @Description(
      "OPSS key passphrase.")
  @Valid
  private V1SecretReference keySecret;

  public String getWalletConfigMap() {
    return this.walletConfigMap;
  }

  public void setWalletConfigMap(String walletConfigMap) {
    this.walletConfigMap = walletConfigMap;
  }

  public Opss withWalletConfigMap(String walletConfigMap) {
    this.walletConfigMap = walletConfigMap;
    return this;
  }

  public V1SecretReference getKeySecret() {
    return this.keySecret;
  }

  public void setKeySecret(V1SecretReference keySecret) {
    this.keySecret = keySecret;
  }

  public Opss withKeySecret(V1SecretReference keySecret) {
    this.keySecret = keySecret;
    return this;
  }

  @Override
  public String toString() {
    ToStringBuilder builder =
        new ToStringBuilder(this)
            .append("walletConfigMap", walletConfigMap)
            .append("keySecret", keySecret);

    return builder.toString();
  }

  @Override
  public int hashCode() {
    HashCodeBuilder builder = new HashCodeBuilder()
        .append(walletConfigMap)
        .append(keySecret);

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
            .append(walletConfigMap, rhs.walletConfigMap)
            .append(keySecret, rhs.keySecret);

    return builder.isEquals();
  }
}
