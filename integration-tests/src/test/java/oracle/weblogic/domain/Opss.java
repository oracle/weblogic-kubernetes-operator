// Copyright (c) 2020, 2021, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.weblogic.domain;

import io.swagger.annotations.ApiModelProperty;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.apache.commons.lang3.builder.ToStringBuilder;

public class Opss {

  @ApiModelProperty("Secret containing the OPSS key wallet file.")
  private String walletFileSecret;

  @ApiModelProperty("Secret containing OPSS key passphrase.")
  private String walletPasswordSecret;

  public Opss walletFileSecret(String walletFileSecret) {
    this.walletFileSecret = walletFileSecret;
    return this;
  }

  public String walletFileSecret() {
    return this.walletFileSecret;
  }

  public String getWalletFileSecret() {
    return walletFileSecret;
  }

  public void setWalletFileSecret(String walletFileSecret) {
    this.walletFileSecret = walletFileSecret;
  }

  public Opss walletPasswordSecret(String walletPasswordSecret) {
    this.walletPasswordSecret = walletPasswordSecret;
    return this;
  }

  public String walletPasswordSecret() {
    return this.walletPasswordSecret;
  }

  public String getWalletPasswordSecret() {
    return walletPasswordSecret;
  }

  public void setWalletPasswordSecret(String walletPasswordSecret) {
    this.walletPasswordSecret = walletPasswordSecret;
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
    HashCodeBuilder builder =
        new HashCodeBuilder().append(walletFileSecret).append(walletPasswordSecret);

    return builder.toHashCode();
  }

  @Override
  public boolean equals(Object other) {
    if (this == other) {
      return true;
    }

    if (other == null || getClass() != other.getClass()) {
      return false;
    }
    Opss rhs = (Opss) other;
    EqualsBuilder builder =
        new EqualsBuilder()
            .append(walletFileSecret, rhs.walletFileSecret)
            .append(walletPasswordSecret, rhs.walletPasswordSecret);

    return builder.isEquals();
  }
}
