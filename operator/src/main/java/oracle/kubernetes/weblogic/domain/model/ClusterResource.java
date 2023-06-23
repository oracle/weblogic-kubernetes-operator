// Copyright (c) 2022, 2023, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.weblogic.domain.model;

import java.util.List;
import java.util.Optional;
import javax.annotation.Nonnull;

import com.google.gson.annotations.Expose;
import com.google.gson.annotations.SerializedName;
import io.kubernetes.client.common.KubernetesObject;
import io.kubernetes.client.openapi.models.V1ContainerPort;
import io.kubernetes.client.openapi.models.V1ObjectMeta;
import jakarta.validation.Valid;
import oracle.kubernetes.json.Description;
import oracle.kubernetes.operator.KubernetesConstants;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * An element representing a cluster in the domain configuration.
 *
 * @since 4.0
 */
@SuppressWarnings("JavadocLinkAsPlainText")
@Description(
    "A Cluster resource describes the lifecycle options for all "
    + "of the Managed Server members of a WebLogic cluster, including Java "
    + "options, environment variables, additional Pod content, and the ability to "
    + "explicitly start, stop, or restart cluster members. "
    + "It must describe a cluster that already exists in the WebLogic domain "
    + "configuration. See also `domain.spec.clusters`."
)
public class ClusterResource implements KubernetesObject {
  /**
   * APIVersion defines the versioned schema of this representation of an object. Servers should
   * convert recognized schemas to the latest internal value, and may reject unrecognized values.
   * More info: https://git.k8s.io/community/contributors/devel/api-conventions.md#resources
   */
  @SerializedName("apiVersion")
  @Description("The API version defines the versioned schema of this cluster.")
  private String apiVersion = KubernetesConstants.API_VERSION_CLUSTER_WEBLOGIC_ORACLE;

  /**
   * Kind is a string value representing the REST resource this object represents. Servers may infer
   * this from the endpoint the client submits requests to. Cannot be updated. In CamelCase. More
   * info: https://git.k8s.io/community/contributors/devel/api-conventions.md#types-kinds
   */
  @SerializedName("kind")
  @Description("The type of the REST resource. Must be \"Cluster\".")
  private String kind = KubernetesConstants.CLUSTER;

  /**
   * Standard object's metadata. More info:
   * https://git.k8s.io/community/contributors/devel/api-conventions.md#metadata
   */
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
  private ClusterSpec spec = new ClusterSpec();

  /**
   * ClusterStatus represents information about the status of a cluster. Status may trail the actual
   * state of a system.
   */
  @SerializedName("status")
  @Expose
  @Valid
  @Description("The current status of the operation of the WebLogic cluster. Updated automatically by the operator.")
  private ClusterStatus status;

  public ClusterResource() {
  }

  /**
   * Returns the Kubernetes name of this cluster resource. This must be unique within a namespace and should not be
   * confused with {@link #getClusterName()}.
   */
  @Nullable
  public String getClusterResourceName() {
    return getMetadata().getName();
  }

  /**
   * Returns the WebLogic name of the cluster represented by this resource. It should not be confused with
   * {@link #getClusterResourceName()}.
   */
  @Nonnull
  public String getClusterName() {
    return spec.getClusterName();
  }

  ClusterResource withClusterName(String clusterName) {
    spec.withClusterName(clusterName);
    return this;
  }

  Integer getReplicas() {
    return spec.getReplicas();
  }

  void setReplicas(Integer replicas) {
    spec.setReplicas(replicas);
  }

  public ClusterResource spec(ClusterSpec spec) {
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

  /**
   * Returns true if the cluster resource has a later generation than the passed-in cached cluster resource.
   * @param cachedResource another presence info against which to compare this one.
   */
  public boolean isGenerationChanged(ClusterResource cachedResource) {
    return getGeneration()
        .map(gen -> gen.compareTo(getOrElse(cachedResource)) > 0)
        .orElse(getOrElse(cachedResource) != 0);
  }

  @NotNull
  private Long getOrElse(ClusterResource cachedResource) {
    return cachedResource.getGeneration().orElse(0L);
  }

  private Optional<Long> getGeneration() {
    return Optional.ofNullable(getMetadata().getGeneration());
  }

  // used by the validating webhook
  public List<String> getFatalValidationFailures() {
    return new ClusterValidator().getFatalValidationFailures();
  }

  class ClusterValidator extends Validator {
    // for validating webhook
    private List<String> getFatalValidationFailures() {
      addClusterInvalidMountPaths(ClusterResource.this);
      addClusterReservedEnvironmentVariables(ClusterResource.this, getMetadata().getName());
      verifyClusterLivenessProbeSuccessThreshold(ClusterResource.this, getMetadata().getName());
      verifyClusterContainerNameValid(ClusterResource.this, getMetadata().getName());
      verifyClusterContainerPortNameValidInPodSpec(ClusterResource.this, getMetadata().getName());
      return failures;
    }

    @Override
    void checkPortNameLength(V1ContainerPort port, String name, String prefix) {
      if (isPortNameTooLong(port)) {
        failures.add(DomainValidationMessages.exceedMaxContainerPortName(
            getMetadata().getName(),
            prefix + "." + name,
            port.getName()));
      }
    }
  }
}
