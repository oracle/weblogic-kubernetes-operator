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
public class Paths {

  @ApiModelProperty("List of ingress traits.")
  private List<Path> path = new ArrayList<>();

  public Paths path(List<Path> path) {
    this.path = path;
    return this;
  }

  public List<Path> path() {
    return path;
  }

  public List<Path> getPath() {
    return path;
  }

  public void setPath(List<Path> path) {
    this.path = path;
  }

  @Override
  public String toString() {
    return new ToStringBuilder(this)
        .append("path", path)
        .toString();
  }

  @Override
  public int hashCode() {
    return new HashCodeBuilder()
        .append(path)
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
    Paths rhs = (Paths) other;
    return new EqualsBuilder()
        .append(path, rhs.path)
        .isEquals();
  }

}
