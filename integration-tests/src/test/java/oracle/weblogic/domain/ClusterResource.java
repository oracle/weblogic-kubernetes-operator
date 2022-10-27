// Copyright (c) 2022, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.weblogic.domain;

import java.util.Optional;
import javax.annotation.Nonnull;

import io.kubernetes.client.common.KubernetesObject;
import io.kubernetes.client.openapi.models.V1ObjectMeta;
import io.swagger.annotations.ApiModelProperty;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.apache.commons.lang3.builder.ToStringBuilder;

/**
 * An element representing a cluster in the domain configuration.
 *
 * @since 4.0
 */
public class ClusterResource implements KubernetesObject {

  @ApiModelProperty("The API version defines the versioned schema of this cluster.")
  private String apiVersion;

  @ApiModelProperty("The type of the REST resource. Must be \"Cluster\".")
  private String kind;

  @ApiModelProperty("The resource metadata. Must include the `name` and `namespace.")
  private V1ObjectMeta metadata = new V1ObjectMeta();


  @ApiModelProperty("The specification of the operation of the WebLogic cluster. Required.")
  private ClusterSpec spec = new ClusterSpec();

  @ApiModelProperty("The current status of the operation of the WebLogic cluster. "
      + "Updated automatically by the operator.")
  private ClusterStatus status;

  public String getClusterName() {
    return Optional.ofNullable(spec.getClusterName()).orElse(getMetadata().getName());
  }

  public String getClusterResourceName() {
    return metadata.getName();
  }

  public Integer getReplicas() {
    return spec.getReplicas();
  }

  public void setReplicas(Integer replicas) {
    spec.setReplicas(replicas);
  }

  public ClusterResource spec(ClusterSpec spec) {
    this.spec = spec;
    return this;
  }
  
  public ClusterSpec spec() {
    return spec;
  }

  @Override
  public String toString() {
    return new ToStringBuilder(this)
        .append("clusterName", getClusterName())
        .append("apiVersion", apiVersion)
        .append("kind", kind)
        .append("metadata", metadata)
        .append("spec", spec)
        .append("status", status)
        .toString();
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }

    if (o == null || getClass() != o.getClass()) {
      return false;
    }

    ClusterResource cluster = (ClusterResource) o;

    return new EqualsBuilder()
        .append(metadata, cluster.metadata)
        .append(apiVersion, cluster.apiVersion)
        .append(kind, cluster.kind)
        .append(spec, cluster.spec)
        .append(status, cluster.status)
        .isEquals();
  }

  @Override
  public int hashCode() {
    return new HashCodeBuilder(17, 37)
        .append(metadata)
        .append(apiVersion)
        .append(kind)
        .append(spec)
        .append(status)
        .toHashCode();
  }

  /**
   * APIVersion defines the versioned schema of this representation of an object. Servers should
   * convert recognized schemas to the latest internal value, and may reject unrecognized values.
   * More info: https://git.k8s.io/community/contributors/devel/api-conventions.md#resources
   *
   * @return API version
   */
  public String getApiVersion() {
    return apiVersion;
  }

  /**
   * APIVersion defines the versioned schema of this representation of an object. Servers should
   * convert recognized schemas to the latest internal value, and may reject unrecognized values.
   * More info: https://git.k8s.io/community/contributors/devel/api-conventions.md#resources
   *
   * @param apiVersion API version
   */
  public void setApiVersion(String apiVersion) {
    this.apiVersion = apiVersion;
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
   * @param kind Kind
   */
  public void setKind(String kind) {
    this.kind = kind;
  }

  /**
   * Standard object's metadata. More info:
   * https://git.k8s.io/community/contributors/devel/api-conventions.md#metadata
   *
   * @return Metadata
   */
  public V1ObjectMeta getMetadata() {
    return metadata;
  }

  /**
   * Standard object's metadata. More info:
   * https://git.k8s.io/community/contributors/devel/api-conventions.md#metadata
   *
   * @param metadata Metadata
   */
  public void setMetadata(V1ObjectMeta metadata) {
    this.metadata = metadata;
  }

  /**
   * Standard object's metadata. More info:
   * https://git.k8s.io/community/contributors/devel/api-conventions.md#metadata
   *
   * @param metadata Metadata
   * @return this
   */
  public ClusterResource withMetadata(V1ObjectMeta metadata) {
    this.metadata = metadata;
    return this;
  }

  /**
   * Returns the status for this resource.
   */
  public ClusterStatus getStatus() {
    return status;
  }

  /**
   * Sets the status for this resource.
   * @param status the new status
   */
  public void setStatus(ClusterStatus status) {
    this.status = status;
  }

  /**
   * Sets the status for this resource and returns the updated resource.
   * @param status the new status
   */
  public ClusterResource withStatus(ClusterStatus status) {
    setStatus(status);
    return this;
  }

  @Nonnull
  public ClusterSpec getSpec() {
    return spec;
  }

  public ClusterResource withApiVersion(String apiVersion) {
    setApiVersion(apiVersion);
    return this;
  }

  public ClusterResource withKind(String kind) {
    setKind(kind);
    return this;
  }

  public String getNamespace() {
    return getMetadata().getNamespace();
  }

  public ClusterResource withReplicas(int i) {
    setReplicas(i);
    return this;
  }
}
