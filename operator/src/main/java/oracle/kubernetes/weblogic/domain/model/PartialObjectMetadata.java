// Copyright (c) 2023, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.weblogic.domain.model;

import java.util.Map;

import io.kubernetes.client.common.KubernetesObject;
import io.kubernetes.client.openapi.models.V1ObjectMeta;
import io.swagger.annotations.ApiModelProperty;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.apache.commons.lang3.builder.ToStringBuilder;

public class PartialObjectMetadata implements KubernetesObject {

  @ApiModelProperty("The API version for the object.")
  private String apiVersion;

  @ApiModelProperty("The type of resource.")
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

  public PartialObjectMetadata metadata(V1ObjectMeta metadata) {
    this.metadata = metadata;
    return this;
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

  @Override
  public String toString() {
    return new ToStringBuilder(this)
        .append("apiVersion", apiVersion)
        .append("kind", kind)
        .append("metadata", metadata)
        .toString();
  }

  @Override
  public int hashCode() {
    return new HashCodeBuilder()
        .append(metadata)
        .append(apiVersion)
        .append(kind)
        .toHashCode();
  }

  @Override
  public boolean equals(Object other) {
    if (other == this) {
      return true;
    }
    if (!(other instanceof PartialObjectMetadataList)) {
      return false;
    }
    PartialObjectMetadataList rhs = ((PartialObjectMetadataList) other);
    return new EqualsBuilder()
        .append(metadata, rhs.metadata)
        .append(apiVersion, rhs.apiVersion)
        .append(kind, rhs.kind)
        .isEquals();
  }
}