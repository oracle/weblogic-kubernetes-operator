// Copyright (c) 2020, Oracle Corporation and/or its affiliates.  All rights reserved.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.weblogic.domain.model;

import java.util.List;

import oracle.kubernetes.json.Description;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.apache.commons.lang3.builder.ToStringBuilder;

public class Overrides {
  @Description("The name of the config map for WebLogic configuration overrides.")
  private String configMapName;

  @Description("A list of names of the secrets for WebLogic configuration overrides.")
  private List<String> secrets;

  public String getConfigMapName() {
    return this.configMapName;
  }

  public void setConfigMapName(String configMapName) {
    this.configMapName = configMapName;
  }

  public Overrides withConfigMapName(String configMapName) {
    this.configMapName = configMapName;
    return this;
  }

  public List<String> getSecrets() {
    return secrets;
  }

  public void setSecrets(List<String> secrets) {
    this.secrets = secrets;
  }

  public Overrides withSecrets(List<String> secrets) {
    this.secrets = secrets;
    return this;
  }

  @Override
  public String toString() {
    ToStringBuilder builder =
        new ToStringBuilder(this)
            .append("configMapName", configMapName)
            .append("secrets", secrets);

    return builder.toString();
  }

  @Override
  public int hashCode() {
    HashCodeBuilder builder = new HashCodeBuilder().append(configMapName).append(secrets);

    return builder.toHashCode();
  }

  @Override
  public boolean equals(Object other) {
    if (other == this) {
      return true;
    }
    if (!(other instanceof Overrides)) {
      return false;
    }

    Overrides rhs = ((Overrides) other);
    EqualsBuilder builder =
        new EqualsBuilder()
            .append(configMapName, rhs.configMapName)
            .append(secrets, rhs.secrets);

    return builder.isEquals();
  }
}
