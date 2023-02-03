// Copyright (c) 2023, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.verrazzano.weblogic;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import oracle.weblogic.domain.DomainResource;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.apache.commons.lang3.builder.ToStringBuilder;

@ApiModel(description = "WorkloadSpec is a description of a Workload.")
public class WorkloadSpec {

  @ApiModelProperty("Specication for the Workload.")
  public DomainResource template;

  public WorkloadSpec template(DomainResource template) {
    this.template = template;
    return this;
  }

  public DomainResource template() {
    return template;
  }

  public DomainResource getTemplate() {
    return template;
  }

  public void setTemplate(DomainResource template) {
    this.template = template;
  }

  @Override
  public String toString() {
    ToStringBuilder builder
        = new ToStringBuilder(this)
            .append("template", template);

    return builder.toString();
  }

  @Override
  public int hashCode() {
    HashCodeBuilder builder
        = new HashCodeBuilder()
            .append(template);

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
    WorkloadSpec rhs = (WorkloadSpec) other;
    EqualsBuilder builder
        = new EqualsBuilder()
            .append(template, rhs.template);
    return builder.isEquals();
  }
}
