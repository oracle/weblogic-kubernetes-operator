// Copyright (c) 2019, 2020, Oracle Corporation and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.weblogic.domain.model;

import oracle.kubernetes.json.Description;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.apache.commons.lang3.builder.ToStringBuilder;

public class Istio {

  @Description(
      "True, if this domain is deployed under an Istio service mesh. "
          + "Defaults to true when the `istio` field is specified.")
  private Boolean enabled = true;

  @Description("The operator will create a WebLogic network access point with this port for use by the "
      + "readiness probe. Defaults to 8888.")
  private Integer readinessPort = 8888;

  /**
   * True, if this domain is deployed under an Istio service mesh.
   *
   * @return True, if this domain is deployed under an Istio service mesh.
   */
  public Boolean getEnabled() {
    return this.enabled;
  }

  /**
   * Sets the Istio enabled status.
   *
   * @param enabled True, if this domain is deployed under an Istio service mesh.
   */
  public void setEnabled(Boolean enabled) {
    this.enabled = enabled;
  }

  /**
   * Get the readiness port.
   *
   * @return the readiness port.
   */
  public Integer getReadinessPort() {
    return this.readinessPort;
  }

  /**
   * Sets the Istio readiness port.
   *
   * @param readinessPort the Istio readiness port.
   */
  public void setReadinessPort(Integer readinessPort) {
    this.readinessPort = readinessPort;
  }

  /**
   * Sets the Istio enabled status.
   *
   * @param istioEnabled True, if this domain is deployed under an Istio service mesh.
   * @return this
   */
  public Istio withIstioEnabled(Boolean istioEnabled) {
    this.enabled = istioEnabled;
    return this;
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
