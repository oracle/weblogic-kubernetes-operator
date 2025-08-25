// Copyright (c) 2025, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.weblogic.domain.model;

import oracle.kubernetes.json.Description;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.apache.commons.lang3.builder.ToStringBuilder;

public class HashiCorpVault {

  @Description("Use HashiCorp vault for operator managed secrets.")
  private boolean enabled;

  @Description("HashiCorp vault base URL")
  private String url;

  @Description("HashiCorp vault Kubernetes Role")
  private String role;

  @Description("HashiCorp vault secret path")
  private String secretPath;

  public boolean isEnabled() {
    return enabled;
  }

  public void setEnabled(boolean enabled) {
    this.enabled = enabled;
  }

  public String getUrl() {
    return url;
  }

  public void setUrl(String url) {
    this.url = url;
  }

  public String getRole() {
    return role;
  }

  public String getSecretPath() {
    return secretPath;
  }

  public void setSecretPath(String path) {
    this.secretPath = path;
  }

  @Override
  public String toString() {
    ToStringBuilder builder =
            new ToStringBuilder(this)
                    .append("enabled", enabled)
                    .append("url", url)
                    .append("role", role)
                    .append("secretPath", secretPath);

    return builder.toString();
  }

  @Override
  public int hashCode() {
    HashCodeBuilder builder = new HashCodeBuilder()
            .append(enabled)
            .append(url)
            .append(role)
            .append(secretPath);

    return builder.toHashCode();
  }

  @Override
  public boolean equals(Object other) {
    if (other == this) {
      return true;
    } else if (!(other instanceof HashiCorpVault)) {
      return false;
    }

    HashiCorpVault rhs = ((HashiCorpVault) other);
    EqualsBuilder builder =
            new EqualsBuilder()
                    .append(enabled, rhs.enabled)
                    .append(url, rhs.url)
                    .append(role, rhs.role)
                    .append(secretPath, rhs.secretPath);

    return builder.isEquals();
  }

}
