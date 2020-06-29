// Copyright (c) 2020, Oracle Corporation and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.weblogic.domain.model;

import javax.validation.Valid;

import oracle.kubernetes.json.Description;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.apache.commons.lang3.builder.ToStringBuilder;

public class Opss {

  @Description("Name of a Secret containing the OPSS key wallet file.")
  private String walletFileSecret;

  @Description(
      "Name of a Secret containing the OPSS key passphrase.")
  @Valid
  private String walletPasswordSecret;

  public String getWalletFileSecret() {
    return this.walletFileSecret;
  }

  public void setWalletFileSecret(String walletFileSecret) {
    this.walletFileSecret = walletFileSecret;
  }

  public Opss withWalletFileSecret(String walletFileSecret) {
    this.walletFileSecret = walletFileSecret;
    return this;
  }

  public String getWalletPasswordSecret() {
    return this.walletPasswordSecret;
  }

  public void setWalletPasswordSecret(String walletPasswordSecret) {
    this.walletPasswordSecret = walletPasswordSecret;
  }

  public Opss withWalletPasswordSecret(String walletPasswordSecret) {
    this.walletPasswordSecret = walletPasswordSecret;
    return this;
  }

  @Override
  public String toString() {
    ToStringBuilder builder =
        new ToStringBuilder(this)
            .append("walletFileSecret", walletFileSecret)
            .append("walletPasswordSecret", walletPasswordSecret);

    return builder.toString();
  }

  @Override
  public int hashCode() {
    HashCodeBuilder builder = new HashCodeBuilder()
        .append(walletFileSecret)
        .append(walletPasswordSecret);

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
            .append(walletFileSecret, rhs.walletFileSecret)
            .append(walletPasswordSecret, rhs.walletPasswordSecret);

    return builder.isEquals();
  }
}
