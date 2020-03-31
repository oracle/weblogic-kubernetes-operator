// Copyright (c) 2020, Oracle Corporation and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.weblogic.domain.model;

import oracle.kubernetes.json.Description;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.apache.commons.lang3.builder.ToStringBuilder;

public class Istio {

  @Description(
      "True, if this domain is deployed under an Istio service mesh. "
          + "Defaults to true when the 'istio' element is included. Not required.")
  private Boolean enabled;

  @Description("The WebLogic readiness port for Istio. Defaults to 8888. Not required.")
  private Integer readinessPort;

  public Istio enabled(Boolean enabled) {
    this.enabled = enabled;
    return this;
  }

  public Boolean getEnabled() {
    return this.enabled;
  }

  public void setEnabled(Boolean enabled) {
    this.enabled = enabled;
  }

  public Istio readinessPort(Integer readinessPort) {
    this.readinessPort = readinessPort;
    return this;
  }

  public Integer getReadinessPort() {
    return this.readinessPort;
  }

  public void setReadinessPort(Integer readinessPort) {
    this.readinessPort = readinessPort;
  }

  @Override
  public String toString() {
    ToStringBuilder builder =
        new ToStringBuilder(this)
            .append("enabled", enabled)
            .append("readinessPort", readinessPort);

    return builder.toString();
  }

  @Override
  public int hashCode() {
    HashCodeBuilder builder = new HashCodeBuilder().append(enabled).append(readinessPort);

    return builder.toHashCode();
  }

  @Override
  public boolean equals(Object other) {
    if (other == this) {
      return true;
    }
    if (!(other instanceof Istio)) {
      return false;
    }

    Istio rhs = ((Istio) other);
    EqualsBuilder builder =
        new EqualsBuilder()
            .append(enabled, rhs.enabled)
            .append(readinessPort, rhs.readinessPort);

    return builder.isEquals();
  }

}
