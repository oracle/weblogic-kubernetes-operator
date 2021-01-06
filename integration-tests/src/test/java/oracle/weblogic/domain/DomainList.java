// Copyright (c) 2020, 2021, Oracle and/or its affiliates.
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

@ApiModel(description = "DomainList is a list of Domains.")
public class DomainList implements KubernetesListObject {

  @ApiModelProperty("The API version for the Domain.")
  private String apiVersion;

  @ApiModelProperty("The type of resource. Must be 'DomainList'.")
  private String kind;

  @ApiModelProperty(
      "Standard list metadata. "
          + "More info: https://git.k8s.io/community/contributors/devel/api-conventions.md#types-kinds.")
  private V1ListMeta metadata;

  @ApiModelProperty(
      "List of domains. More info: https://git.k8s.io/community/contributors/devel/api-conventions.md. Required.")
  private List<Domain> items = new ArrayList<Domain>();

  public DomainList apiVersion(String apiVersion) {
    this.apiVersion = apiVersion;
    return this;
  }

  public String apiVersion() {
    return apiVersion;
  }

  public String getApiVersion() {
    return apiVersion;
  }

  public void setApiVersion(String apiVersion) {
    this.apiVersion = apiVersion;
  }

  public DomainList kind(String kind) {
    this.kind = kind;
    return this;
  }

  public String kind() {
    return kind;
  }

  public String getKind() {
    return kind;
  }

  public void setKind(String kind) {
    this.kind = kind;
  }

  public DomainList metadata(V1ListMeta metadata) {
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

  public DomainList items(List<Domain> items) {
    this.items = items;
    return this;
  }

  public List<Domain> items() {
    return items;
  }

  public List<Domain> getItems() {
    return items;
  }

  public void setItems(List<Domain> items) {
    this.items = items;
  }

  @Override
  public String toString() {
    return new ToStringBuilder(this)
        .append("apiVersion", apiVersion)
        .append("kind", kind)
        .append("metadata", metadata)
        .append("items", items)
        .toString();
  }

  @Override
  public int hashCode() {
    return new HashCodeBuilder()
        .append(apiVersion)
        .append(kind)
        .append(metadata)
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
    DomainList rhs = (DomainList) other;
    return new EqualsBuilder()
        .append(apiVersion, rhs.apiVersion)
        .append(kind, rhs.kind)
        .append(metadata, rhs.metadata)
        .append(items, rhs.items)
        .isEquals();
  }
}
