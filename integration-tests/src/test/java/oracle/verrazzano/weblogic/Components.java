// Copyright (c) 2023, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.verrazzano.weblogic;

import java.util.ArrayList;
import java.util.List;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;

@ApiModel(
    description
    = "Component represents a Verrazzano component and how it will be realized in the Kubernetes cluster.")
public class Components {

  @ApiModelProperty("A list of component names to deploy.")
  private List<String> componentName = new ArrayList<>();

  public Components componentName(List<String> componentName) {
    this.componentName = componentName;
    return this;
  }

  public List<String> componentName() {
    return componentName;
  }

  /**
   * Adds componentName item.
   *
   * @param componentNameItem component item
   * @return this
   */
  public Components addComponentNameItem(String componentNameItem) {
    if (componentName == null) {
      componentName = new ArrayList<>();
    }
    componentName.add(componentNameItem);
    return this;
  }

  public List<String> getComponentName() {
    return componentName;
  }

  public void setComponentName(List<String> componentName) {
    this.componentName = componentName;
  }

}
