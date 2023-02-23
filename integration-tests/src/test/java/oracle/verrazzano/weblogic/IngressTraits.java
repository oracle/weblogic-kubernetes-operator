// Copyright (c) 2023, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.verrazzano.weblogic;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.apache.commons.lang3.builder.ToStringBuilder;

@ApiModel(
    description
    = "IngressTraits represents a Verrazzano Ingress Traits and how it will be realized in the Kubernetes cluster.")
public class IngressTraits {

  @ApiModelProperty("ingress trait.")
  private IngressTrait trait = new IngressTrait();

  public IngressTraits trait(IngressTrait trait) {
    this.trait = trait;
    return this;
  }

  public IngressTrait trait() {
    return trait;
  }

  public IngressTrait getTrait() {
    return trait;
  }

  public void setTrait(IngressTrait trait) {
    this.trait = trait;
  }

  @Override
  public String toString() {
    return new ToStringBuilder(this)
        .append("trait", trait)
        .toString();
  }

  @Override
  public int hashCode() {
    return new HashCodeBuilder()
        .append(trait)
        .toHashCode();
  }

  @Override
  public boolean equals(Object other) {
    if (this == other) {
      return true;
    }
    if (other == null || getClass() != other.getClass()) {
      return false;
    }
    IngressTraits rhs = (IngressTraits) other;
    return new EqualsBuilder()
        .append(trait, rhs.trait)
        .isEquals();
  }
}
