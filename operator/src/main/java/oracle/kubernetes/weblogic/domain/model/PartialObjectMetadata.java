// Copyright (c) 2023, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.weblogic.domain.model;

import io.kubernetes.client.common.KubernetesObject;
import io.kubernetes.client.openapi.models.V1ObjectMeta;
import io.swagger.annotations.ApiModelProperty;

public class PartialObjectMetadata implements KubernetesObject {

  @ApiModelProperty("The API version for the object.")
  private String apiVersion;

  @ApiModelProperty("The type of resource.")
  private String kind;

  @ApiModelProperty("Standard object's metadata.")
  private V1ObjectMeta metadata;

  public PartialObjectMetadata() {
    this.metadata = new V1ObjectMeta();
  }

  public PartialObjectMetadata(V1ObjectMeta metadata) {
    this.metadata = metadata;
  }

  public V1ObjectMeta getMetadata() {
    return metadata;
  }

  @Override
  public String getApiVersion() {
    return apiVersion;
  }

  @Override
  public String getKind() {
    return kind;
  }
}