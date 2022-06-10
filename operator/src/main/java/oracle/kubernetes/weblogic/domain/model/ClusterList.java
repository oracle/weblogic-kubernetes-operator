// Copyright (c) 2017, 2022, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.weblogic.domain.model;

import java.util.ArrayList;
import java.util.List;

import com.google.gson.annotations.Expose;
import com.google.gson.annotations.SerializedName;
import io.kubernetes.client.openapi.models.V1ListMeta;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.apache.commons.lang3.builder.ToStringBuilder;

/** ClusterList is a list of Clusters. */
public class ClusterList extends KubernetesListObjectImpl {

  /**
   * List of clusters. More info: https://git.k8s.io/community/contributors/devel/api-conventions.md.
   * Required.
   */
  @SerializedName("items")
  @Expose
  @Valid
  @NotNull
  private List<ClusterResource> items = new ArrayList<>();

  /**
   * List of clusters. More info: https://git.k8s.io/community/contributors/devel/api-conventions.md.
   * Required.
   *
   * @return items
   */
  public List<ClusterResource> getItems() {
    return items;
  }

  /**
   * List of clusters. More info: https://git.k8s.io/community/contributors/devel/api-conventions.md.
   * Required.
   *
   * @param items items
   */
  public void setItems(List<ClusterResource> items) {
    this.items = items;
  }

  /**
   * List of clusters. More info: https://git.k8s.io/community/contributors/devel/api-conventions.md.
   * Required.
   *
   * @param items items
   * @return this
   */
  public ClusterList withItems(List<ClusterResource> items) {
    this.items = items;
    return this;
  }

  @Override
  public int hashCode() {
    return new HashCodeBuilder()
        .append(metadata)
        .append(apiVersion)
        .append(items)
        .append(kind)
        .toHashCode();
  }

  @Override
  public boolean equals(Object other) {
    if (other == this) {
      return true;
    }
    if (!(other instanceof ClusterList)) {
      return false;
    }
    ClusterList rhs = ((ClusterList) other);
    return new EqualsBuilder()
        .append(metadata, rhs.metadata)
        .append(apiVersion, rhs.apiVersion)
        .append(items, rhs.items)
        .append(kind, rhs.kind)
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
