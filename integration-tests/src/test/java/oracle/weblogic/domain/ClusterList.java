// Copyright (c) 2022, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.weblogic.domain;

import java.util.ArrayList;
import java.util.List;

import io.kubernetes.client.common.KubernetesListObject;
import io.kubernetes.client.openapi.models.V1ListMeta;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.apache.commons.lang3.builder.ToStringBuilder;

@ApiModel(description = "ClusterList is a list of Clusters.")
public class ClusterList implements KubernetesListObject {

  @ApiModelProperty("The API version for the Cluster.")
  private String apiVersion;

  @ApiModelProperty("The type of resource. Must be 'ClusterList'.")
  private String kind;

  @ApiModelProperty(
      "Standard list metadata. "
          + "More info: https://git.k8s.io/community/contributors/devel/api-conventions.md#types-kinds.")
  private V1ListMeta metadata;
  
  @ApiModelProperty(
      "List of clusters. More info: https://git.k8s.io/community/contributors/devel/api-conventions.md. Required.")
  private List<ClusterResource> items = new ArrayList<>();

  public ClusterList apiVersion(String apiVersion) {
    this.apiVersion = apiVersion;
    return this;
  }

  public String apiVersion() {
    return apiVersion;
  }

  @Override
  public String getApiVersion() {
    return apiVersion;
  }

  public void setApiVersion(String apiVersion) {
    this.apiVersion = apiVersion;
  }

  public ClusterList kind(String kind) {
    this.kind = kind;
    return this;
  }

  public String kind() {
    return kind;
  }

  @Override
  public String getKind() {
    return kind;
  }

  public void setKind(String kind) {
    this.kind = kind;
  }

  public ClusterList metadata(V1ListMeta metadata) {
    this.metadata = metadata;
    return this;
  }

  public V1ListMeta metadata() {
    return metadata;
  }

  public V1ListMeta getMetadata() {
    return metadata;
  }

  public void setMetadata(V1ListMeta metadata) {
    this.metadata = metadata;
  }

  public ClusterList items(List<ClusterResource> items) {
    this.items = items;
    return this;
  }

  public List<ClusterResource> items() {
    return items;
  }

  public List<ClusterResource> getItems() {
    return items;
  }

  public void setItems(List<ClusterResource> items) {
    this.items = items;
  }


  @Override
  public int hashCode() {
    return new HashCodeBuilder()
        .append(metadata)
        .append(kind)
        .append(apiVersion)
        .append(items)
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
    ClusterList rhs = (ClusterList) other;
    return new EqualsBuilder()
        .append(apiVersion, rhs.apiVersion)
        .append(kind, rhs.kind)
        .append(metadata, rhs.metadata)
        .append(items, rhs.items)
        .isEquals();
  }

  @Override
  public String toString() {
    return new ToStringBuilder(this)
        .append("apiVersion", apiVersion)
        .append("items", items)
        .append("kind", kind)
        .append("metadata", metadata)
        .toString();
  }

  /**
   * Standard list metadata. More info:
   * https://git.k8s.io/community/contributors/devel/api-conventions.md#types-kinds
   *
   * @param metadata metadata
   * @return this
   */
  public ClusterList withMetadata(V1ListMeta metadata) {
    this.metadata = metadata;
    return this;
  }

}
