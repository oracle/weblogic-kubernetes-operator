// Copyright (c) 2025, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.weblogic.domain.model;

import oracle.kubernetes.json.Description;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.apache.commons.lang3.builder.ToStringBuilder;

public class ExternalSecrets {

  @Description("HashiCorp vault for operator managed secrets.")
  private HashiCorpVault hashiCorpVault;

  public HashiCorpVault getHashiCorpVault() {
    return hashiCorpVault;
  }

  public void setHashiCorpVault(HashiCorpVault hashiCorpVault) {
    this.hashiCorpVault = hashiCorpVault;
  }

  @Override
  public String toString() {
    ToStringBuilder builder =
            new ToStringBuilder(this)
                    .append("hashiCorpVault", hashiCorpVault);

    return builder.toString();
  }

  @Override
  public int hashCode() {
    HashCodeBuilder builder = new HashCodeBuilder()
            .append(hashiCorpVault);

    return builder.toHashCode();
  }

  @Override
  public boolean equals(Object other) {
    if (other == this) {
      return true;
    } else if (!(other instanceof ExternalSecrets)) {
      return false;
    }

    ExternalSecrets rhs = ((ExternalSecrets) other);
    EqualsBuilder builder =
            new EqualsBuilder()
                    .append(hashiCorpVault, rhs.hashiCorpVault);

    return builder.isEquals();
  }

}
