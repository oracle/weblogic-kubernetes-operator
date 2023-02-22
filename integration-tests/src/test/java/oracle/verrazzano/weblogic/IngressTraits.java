// Copyright (c) 2023, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.verrazzano.weblogic;

import java.util.ArrayList;
import java.util.List;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.apache.commons.lang3.builder.ToStringBuilder;

@ApiModel(
    description
    = "Traits represents a Verrazzano IngressTrait and how it will be realized in the Kubernetes cluster.")
public class IngressTraits {

  @ApiModelProperty("List of ingress traits.")
  private List<IngressTrait> traits = new ArrayList<>();

  public IngressTraits trait(List<IngressTrait> traits) {
    this.traits = traits;
    return this;
  }

  public List<IngressTrait> traits() {
    return traits;
  }

  public List<IngressTrait> getTraits() {
    return traits;
  }

  public void setTrait(List<IngressTrait> traits) {
    this.traits = traits;
  }

  @Override
  public String toString() {
    return new ToStringBuilder(this)
        .append("traits", traits)
        .toString();
  }

  @Override
  public int hashCode() {
    return new HashCodeBuilder()
        .append(traits)
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
        .append(traits, rhs.traits)
        .isEquals();
  }
}
