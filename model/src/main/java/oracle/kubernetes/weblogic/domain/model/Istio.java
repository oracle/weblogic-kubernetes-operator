// Copyright 2019, Oracle Corporation and/or its affiliates.  All rights reserved.
// Licensed under the Universal Permissive License v 1.0 as shown at
// http://oss.oracle.com/licenses/upl.

package oracle.kubernetes.weblogic.domain.model;

import oracle.kubernetes.json.Description;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.apache.commons.lang3.builder.ToStringBuilder;

public class Istio {

  @Description("True, if this domain is deployed under an Istio service mesh. Defaults to true. Not required.")
  private Boolean istioEnabled = true;

  /**
   * True, if this domain is deployed under an Istio service mesh.
   *
   * @return True, if this domain is deployed under an Istio service mesh.
   */
  public Boolean getIstioEnabled() {
    return this.istioEnabled;
  }

  /**
   * Sets the Istio enabled status.
   *
   * @param istioEnabled True, if this domain is deployed under an Istio service mesh.
   */
  public void setIstioEnabled(Boolean istioEnabled) {
    this.istioEnabled = istioEnabled;
  }

  /**
   * Sets the Istio enabled status.
   *
   * @param istioEnabled True, if this domain is deployed under an Istio service mesh.
   * @return this
   */
  public Istio withIstioEnabled(Boolean istioEnabled) {
    this.istioEnabled = istioEnabled;
    return this;
  }


  @Override
  public String toString() {
    ToStringBuilder builder =
        new ToStringBuilder(this)
            .append("istioEnabled", istioEnabled);

    return builder.toString();
  }

  @Override
  public int hashCode() {
    HashCodeBuilder builder =
        new HashCodeBuilder()
            .append(istioEnabled);

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
            .append(istioEnabled, rhs.istioEnabled);

    return builder.isEquals();
  }
}
