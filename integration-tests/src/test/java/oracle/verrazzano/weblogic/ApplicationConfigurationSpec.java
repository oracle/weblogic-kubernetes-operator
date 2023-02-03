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
    = "ApplicationConfigurationSpec represents a verrazzano application configuration Spec and how it will "
        + "be realized in the Kubernetes cluster.")
public class ApplicationConfigurationSpec {

  @ApiModelProperty("List of verrazzano components.")
  private List<Components> components = new ArrayList<>();


  public ApplicationConfigurationSpec components(List<Components> components) {
    this.components = components;
    return this;
  }

  public List<Components> components() {
    return components;
  }

  public List<Components> getComponents() {
    return components;
  }

  public void setComponents(List<Components> components) {
    this.components = components;
  }

  @Override
  public String toString() {
    return new ToStringBuilder(this)
        .append("components", components)
        .toString();
  }

  @Override
  public int hashCode() {
    return new HashCodeBuilder()
        .append(components)
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
    ApplicationConfigurationSpec rhs = (ApplicationConfigurationSpec) other;
    return new EqualsBuilder()
        .append(components, rhs.components)
        .isEquals();
  }
}
