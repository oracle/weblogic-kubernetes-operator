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
    = "IngressTraitSpec represents a Verrazzano IngressTrait specifction"
    + " and how it will be realized in the Kubernetes cluster.")
public class IngressTraitSpec {

  @ApiModelProperty("List of ingress rules.")
  public List<IngressRule> rules = new ArrayList<>();

  public IngressTraitSpec ingressRules(List<IngressRule> ingressRules) {
    this.rules = ingressRules;
    return this;
  }

  public List<IngressRule> ingressRules() {
    return rules;
  }

  public List<IngressRule> getRules() {
    return rules;
  }

  public void setIngressRule(List<IngressRule> ingressRules) {
    this.rules = ingressRules;
  }

  @Override
  public String toString() {
    return new ToStringBuilder(this)
        .append("spec", rules)
        .toString();
  }

  @Override
  public int hashCode() {
    return new HashCodeBuilder()
        .append(rules)
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
        .append(rules, rhs.rules)
        .isEquals();
  }

}
