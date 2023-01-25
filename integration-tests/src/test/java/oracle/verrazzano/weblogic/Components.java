// Copyright (c) 2023, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.verrazzano.weblogic;

import io.kubernetes.client.openapi.models.V1LocalObjectReference;
import java.util.ArrayList;
import java.util.List;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;

@ApiModel(
    description
    = "Component represents a Verrazzano component and how it will be realized in the Kubernetes cluster.")
public class Components {

  @ApiModelProperty("A list of component names to deploy.")
  private List<V1LocalObjectReference> componentName = new ArrayList<>();

  public Components componentName(List<V1LocalObjectReference> componentName) {
    this.componentName = componentName;
    return this;
  }

  public List<V1LocalObjectReference> componentName() {
    return componentName;
  }

  /**
   * Adds componentName item.
   *
   * @param componentNameItem component item
   * @return this
   */
  public Components addComponentNameItem(V1LocalObjectReference componentNameItem) {
    if (componentName == null) {
      componentName = new ArrayList<>();
    }
    componentName.add(componentNameItem);
    return this;
  }

  public List<V1LocalObjectReference> getComponentName() {
    return componentName;
  }

  public void setComponentName(List<V1LocalObjectReference> componentName) {
    this.componentName = componentName;
  }

}
