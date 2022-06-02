// Copyright (c) 2018, 2021, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.weblogic.domain.model;

import java.util.Optional;
import javax.annotation.Nonnull;

import com.google.gson.annotations.Expose;
import com.google.gson.annotations.SerializedName;
import io.kubernetes.client.common.KubernetesObject;
import io.kubernetes.client.openapi.models.V1ObjectMeta;
import jakarta.validation.Valid;
import oracle.kubernetes.json.Description;
import oracle.kubernetes.operator.KubernetesConstants;
import oracle.kubernetes.operator.helpers.KubernetesUtils;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.jetbrains.annotations.NotNull;

/**
 * An element representing a cluster in the domain configuration.
 *
 * @since 4.0
 */
public class ClusterResource implements KubernetesObject {
  /**
   * APIVersion defines the versioned schema of this representation of an object. Servers should
   * convert recognized schemas to the latest internal value, and may reject unrecognized values.
   * More info: https://git.k8s.io/community/contributors/devel/api-conventions.md#resources
   */
  @SuppressWarnings("common-java:DuplicatedBlocks")
  @SerializedName("apiVersion")
  @Description("The API version defines the versioned schema of this cluster.")
  private String apiVersion = KubernetesConstants.CLUSTER_VERSION;

  /**
   * Kind is a string value representing the REST resource this object represents. Servers may infer
   * this from the endpoint the client submits requests to. Cannot be updated. In CamelCase. More
   * info: https://git.k8s.io/community/contributors/devel/api-conventions.md#types-kinds
   */
  @SuppressWarnings("common-java:DuplicatedBlocks")
  @SerializedName("kind")
  @Description("The type of the REST resource. Must be \"Cluster\".")
  private String kind = KubernetesConstants.CLUSTER;

  /**
   * Standard object's metadata. More info:
   * https://git.k8s.io/community/contributors/devel/api-conventions.md#metadata
   */
  @SuppressWarnings("common-java:DuplicatedBlocks")
  @SerializedName("metadata")
  @Description("The resource metadata. Must include the `name` and `namespace.")
  private V1ObjectMeta metadata = new V1ObjectMeta();

  /**
   * ClusterSpec is a description of a cluster.
   */
  @SerializedName("spec")
  @Expose
  @Valid
  @Description("The specification of the operation of the WebLogic cluster. Required.")
  @Nonnull
  private Cluster spec = new Cluster();

  /**
   * ClusterStatus represents information about the status of a cluster. Status may trail the actual
   * state of a system.
   */
  @SerializedName("status")
  @Expose
  @Valid
  @Description("The current status of the operation of the WebLogic cluster. Updated automatically by the operator.")
  private ClusterStatus status;

  String getClusterName() {
    return Optional.ofNullable(spec.getClusterName()).orElse(metadata.getName());
  }

  Integer getReplicas() {
    return spec.getReplicas();
  }

  void setReplicas(Integer replicas) {
    spec.setReplicas(replicas);
  }

  ClusterResource spec(Cluster spec) {
    this.spec = spec;
    return this;
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
        .appendSuper(super.equals(o))
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
   * APIVersion defines the versioned schema of this representation of an object. Servers should
   * convert recognized schemas to the latest internal value, and may reject unrecognized values.
   * More info: https://git.k8s.io/community/contributors/devel/api-conventions.md#resources
   *
   * @param apiVersion API version
   * @return this
   */
  public ClusterResource withApiVersion(String apiVersion) {
    this.apiVersion = apiVersion;
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
   * @param kind Kind
   */
  public void setKind(String kind) {
    this.kind = kind;
  }

  /**
   * Kind is a string value representing the REST resource this object represents. Servers may infer
   * this from the endpoint the client submits requests to. Cannot be updated. In CamelCase. More
   * info: https://git.k8s.io/community/contributors/devel/api-conventions.md#types-kinds
   *
   * @param kind Kind
   * @return this
   */
  public ClusterResource withKind(String kind) {
    this.kind = kind;
    return this;
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
   * Returns the domain unique identifier.
   *
   * @return domain UID
   */
  public String getDomainUid() {
    return Optional.ofNullable(spec.getDomainUid()).orElse(KubernetesUtils.getDomainUidLabel(metadata));
  }

  @NotNull
  public Cluster getSpec() {
    return spec;
  }
}
