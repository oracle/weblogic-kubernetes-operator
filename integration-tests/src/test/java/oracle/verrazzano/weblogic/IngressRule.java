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
    = "IngressRule represents a Verrazzano Ingress rule and how it will be realized in the Kubernetes cluster.")
public class IngressRule {

  @ApiModelProperty("destination name.")
  public Destination destination = new Destination();

  @ApiModelProperty("List of application path.")
  public List<Path> paths = new ArrayList<>();

  public IngressRule paths(List<Path> paths) {
    this.paths = paths;
    return this;
  }

  public List<Path> paths() {
    return paths;
  }

  public List<Path> getPaths() {
    return paths;
  }

  public void setPaths(List<Path> paths) {
    this.paths = paths;
  }

  public IngressRule destination(Destination destination) {
    this.destination = destination;
    return this;
  }

  public Destination destination() {
    return destination;
  }

  public Destination getdestination() {
    return destination;
  }

  public void setdestination(Destination destination) {
    this.destination = destination;
  }

  @Override
  public String toString() {
    return new ToStringBuilder(this)
        .append("paths", paths)
        .append("destination", destination)
        .toString();
  }

  @Override
  public int hashCode() {
    return new HashCodeBuilder()
        .append(paths)
        .append(destination)
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
    IngressRule rhs = (IngressRule) other;
    return new EqualsBuilder()
        .append(paths, rhs.paths)
        .append(destination, rhs.destination)
        .isEquals();
  }

}
