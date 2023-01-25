// Copyright (c) 2023, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.verrazzano.weblogic;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.apache.commons.lang3.builder.ToStringBuilder;

@ApiModel(description = "ComponentSpec is a description of a component.")
public class ComponentSpec {

  @ApiModelProperty("Specification for the Workload.")
  private Workload workLoad;

  public ComponentSpec workLoad(Workload workLoad) {
    this.workLoad = workLoad;
    return this;
  }

  public Workload workLoad() {
    return workLoad;
  }

  public Workload getWorkLoad() {
    return workLoad;
  }

  public void setWorkLoad(Workload workLoad) {
    this.workLoad = workLoad;
  }

  @Override
  public String toString() {
    return new ToStringBuilder(this)
        .append("workload", workLoad)
        .toString();
  }

  @Override
  public int hashCode() {
    return new HashCodeBuilder()
        .append(workLoad)
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
    ComponentSpec rhs = (ComponentSpec) other;
    return new EqualsBuilder()
        .append(workLoad, rhs.workLoad)
        .isEquals();
  }
}
