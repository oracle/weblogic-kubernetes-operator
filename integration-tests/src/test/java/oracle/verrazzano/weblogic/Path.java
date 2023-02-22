// Copyright (c) 2023, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.verrazzano.weblogic;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;

@ApiModel(
    description
    = "Traits represents a Verrazzano IngressTrait and how it will be realized in the Kubernetes cluster.")
public class Path {

  @ApiModelProperty("Component name to deploy.")
  public String path;

  @ApiModelProperty("Component name to deploy.")
  public String pathType;

  public Path pathType(String pathType) {
    this.pathType = pathType;
    return this;
  }

  public String pathType() {
    return pathType;
  }

  public String getpathType() {
    return pathType;
  }

  public void setpathType(String pathType) {
    this.pathType = pathType;
  }

  public Path path(String path) {
    this.path = path;
    return this;
  }

  public String path() {
    return path;
  }

  public String getpath() {
    return pathType;
  }

  public void setpath(String path) {
    this.pathType = pathType;
  }

}
