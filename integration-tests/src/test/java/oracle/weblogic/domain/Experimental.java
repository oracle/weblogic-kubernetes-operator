// Copyright (c) 2020, 2021, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.weblogic.domain;

import io.swagger.annotations.ApiModelProperty;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.apache.commons.lang3.builder.ToStringBuilder;

public class Experimental {

  @ApiModelProperty("Istio service mesh integration configuration.")
  private Istio istio;

  public Experimental istio(Istio istio) {
    this.istio = istio;
    return this;
  }

  public Istio istio() {
    return this.istio;
  }

  public Istio getIstio() {
    return istio;
  }

  public void setIstio(Istio istio) {
    this.istio = istio;
  }

  @Override
  public String toString() {
    ToStringBuilder builder = new ToStringBuilder(this).append("istio", istio);

    return builder.toString();
  }

  @Override
  public int hashCode() {
    HashCodeBuilder builder = new HashCodeBuilder().append(istio);

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
    Experimental rhs = (Experimental) other;
    EqualsBuilder builder = new EqualsBuilder().append(istio, rhs.istio);

    return builder.isEquals();
  }
}
