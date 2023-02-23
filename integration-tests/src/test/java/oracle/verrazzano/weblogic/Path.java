// Copyright (c) 2023, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.verrazzano.weblogic;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;

@ApiModel(
    description
    = "Path represents a Verrazzano application path.")
public class Path {

  @ApiModelProperty("application path name.")
  public String path;

  @ApiModelProperty("application path type 'prefix' or 'Exact'.")
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
    this.path = path;
  }

}
