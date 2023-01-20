// Copyright (c) 2017, 2022, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.weblogic.domain.model;

import java.util.Map;

import io.kubernetes.client.common.KubernetesObject;
import io.kubernetes.client.openapi.models.V1ObjectMeta;
import io.swagger.annotations.ApiModelProperty;

public class PartialObjectMetadata implements KubernetesObject {

  @ApiModelProperty("The API version for the Domain.")
  private String apiVersion;

  @ApiModelProperty("The type of resource. Must be 'DomainList'.")
  private String kind;

  @ApiModelProperty(
      "Standard list metadata. "
          + "More info: https://git.k8s.io/community/contributors/devel/api-conventions.md#types-kinds.")
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

  public void setMetadata(V1ObjectMeta metadata) {
    this.metadata = metadata;
  }

  public String getName() {
    return metadata.getName();
  }

  public void setName(String name) {
    metadata.setName(name);
  }

  public String getNamespace() {
    return metadata.getNamespace();
  }

  public void setNamespace(String namespace) {
    metadata.setNamespace(namespace);
  }

  public Map<String, String> getLabels() {
    return metadata.getLabels();
  }

  public void setLabels(Map<String, String> labels) {
    metadata.setLabels(labels);
  }

  public Map<String, String> getAnnotations() {
    return metadata.getAnnotations();
  }

  public void setAnnotations(Map<String, String> annotations) {
    metadata.setAnnotations(annotations);
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
