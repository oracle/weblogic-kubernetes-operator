// Copyright (c) 2017, 2022, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.weblogic.domain.model;

import java.util.ArrayList;
import java.util.List;

import com.google.gson.annotations.Expose;
import com.google.gson.annotations.SerializedName;
import io.kubernetes.client.common.KubernetesListObject;
import io.kubernetes.client.openapi.models.V1ListMeta;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.apache.commons.lang3.builder.ToStringBuilder;

/** ClusterList is a list of Clusters. */
public class ClusterList implements KubernetesListObject {

  /**
   * APIVersion defines the versioned schema of this representation of an object. Servers should
   * convert recognized schemas to the latest internal value, and may reject unrecognized values.
   * More info: https://git.k8s.io/community/contributors/devel/api-conventions.md#resources.
   */
  @SuppressWarnings("common-java:DuplicatedBlocks")
  @SerializedName("apiVersion")
  @Expose
  private String apiVersion;

  /**
   * List of clusters. More info: https://git.k8s.io/community/contributors/devel/api-conventions.md.
   * Required.
   */
  @SuppressWarnings("common-java:DuplicatedBlocks")
  @SerializedName("items")
  @Expose
  @Valid
  @NotNull
  private List<ClusterResource> items = new ArrayList<>();

  /**
   * Kind is a string value representing the REST resource this object represents. Servers may infer
   * this from the endpoint the client submits requests to. Cannot be updated. In CamelCase. More
   * info: https://git.k8s.io/community/contributors/devel/api-conventions.md#types-kinds.
   */
  @SuppressWarnings("common-java:DuplicatedBlocks")
  @SerializedName("kind")
  @Expose
  private String kind;

  /**
   * Standard list metadata. More info:
   * https://git.k8s.io/community/contributors/devel/api-conventions.md#types-kinds.
   */
  @SuppressWarnings("common-java:DuplicatedBlocks")
  @SerializedName("metadata")
  @Expose
  @Valid
  private V1ListMeta metadata;

  /**
   * APIVersion defines the versioned schema of this representation of an object. Servers should
   * convert recognized schemas to the latest internal value, and may reject unrecognized values.
   * More info: https://git.k8s.io/community/contributors/devel/api-conventions.md#resources.
   *
   * @return API version
   */
  public String getApiVersion() {
    return apiVersion;
  }

  /**
   * APIVersion defines the versioned schema of this representation of an object. Servers should
   * convert recognized schemas to the latest internal value, and may reject unrecognized values.
   * More info: https://git.k8s.io/community/contributors/devel/api-conventions.md#resources.
   *
   * @param apiVersion API version
   */
  public void setApiVersion(String apiVersion) {
    this.apiVersion = apiVersion;
  }

  /**
   * APIVersion defines the versioned schema of this representation of an object. Servers should
   * convert recognized schemas to the latest internal value, and may reject unrecognized values.
   * More info: https://git.k8s.io/community/contributors/devel/api-conventions.md#resources.
   *
   * @param apiVersion API version
   * @return this
   */
  public ClusterList withApiVersion(String apiVersion) {
    this.apiVersion = apiVersion;
    return this;
  }

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

  /**
   * Kind is a string value representing the REST resource this object represents. Servers may infer
   * this from the endpoint the client submits requests to. Cannot be updated. In CamelCase. More
   * info: https://git.k8s.io/community/contributors/devel/api-conventions.md#types-kinds
   *
   * @return kind
   */
  public String getKind() {
    return kind;
  }

  /**
   * Kind is a string value representing the REST resource this object represents. Servers may infer
   * this from the endpoint the client submits requests to. Cannot be updated. In CamelCase. More
   * info: https://git.k8s.io/community/contributors/devel/api-conventions.md#types-kinds
   *
   * @param kind kind
   */
  public void setKind(String kind) {
    this.kind = kind;
  }

  /**
   * Kind is a string value representing the REST resource this object represents. Servers may infer
   * this from the endpoint the client submits requests to. Cannot be updated. In CamelCase. More
   * info: https://git.k8s.io/community/contributors/devel/api-conventions.md#types-kinds
   *
   * @param kind kind
   * @return this
   */
  public ClusterList withKind(String kind) {
    this.kind = kind;
    return this;
  }

  /**
   * Standard list metadata. More info:
   * https://git.k8s.io/community/contributors/devel/api-conventions.md#types-kinds
   *
   * @return metadata
   */
  public V1ListMeta getMetadata() {
    return metadata;
  }

  /**
   * Standard list metadata. More info:
   * https://git.k8s.io/community/contributors/devel/api-conventions.md#types-kinds
   *
   * @param metadata metadata
   */
  public void setMetadata(V1ListMeta metadata) {
    this.metadata = metadata;
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

  @Override
  public String toString() {
    return new ToStringBuilder(this)
        .append("apiVersion", apiVersion)
        .append("items", items)
        .append("kind", kind)
        .append("metadata", metadata)
        .toString();
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

}
