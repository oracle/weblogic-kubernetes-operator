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
public class IngressTraitSpec {

  @ApiModelProperty("List of ingress traits.")
  private List<IngressRule> ingressRules = new ArrayList<>();

  public IngressTraitSpec ingressRules(List<IngressRule> ingressRules) {
    this.ingressRules = ingressRules;
    return this;
  }

  public List<IngressRule> ingressRules() {
    return ingressRules;
  }

  public List<IngressRule> getIngressRules() {
    return ingressRules;
  }

  public void setIngressRule(List<IngressRule> ingressRules) {
    this.ingressRules = ingressRules;
  }

  @Override
  public String toString() {
    return new ToStringBuilder(this)
        .append("spec", ingressRules)
        .toString();
  }

  @Override
  public int hashCode() {
    return new HashCodeBuilder()
        .append(ingressRules)
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
    IngressTraitSpec rhs = (IngressTraitSpec) other;
    return new EqualsBuilder()
        .append(ingressRules, rhs.ingressRules)
        .isEquals();
  }

}
