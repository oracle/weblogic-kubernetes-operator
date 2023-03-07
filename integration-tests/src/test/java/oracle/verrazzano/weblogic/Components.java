// Copyright (c) 2023, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.verrazzano.weblogic;

import java.util.ArrayList;
import java.util.List;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;

@ApiModel(
    description
    = "Components represents a Verrazzano component and how it will be realized in the Kubernetes cluster.")
public class Components {
  @ApiModelProperty("List of ingress traits.")
  public List<IngressTraits> traits = new ArrayList<>();
  
  @ApiModelProperty("Component name to deploy.")
  private String componentName;

  public Components componentName(String componentName) {
    this.componentName = componentName;
    return this;
  }

  public String componentName() {
    return componentName;
  }

  public String getComponentName() {
    return componentName;
  }

  public void setComponentName(String componentName) {
    this.componentName = componentName;
  }
  
  public Components traits(List<IngressTraits> traits) {
    this.traits = traits;
    return this;
  }

  public List<IngressTraits> traits() {
    return traits;
  }

  public List<IngressTraits> getTraits() {
    return traits;
  }

  public void setTraits(List<IngressTraits> traits) {
    this.traits = traits;
  }
}
