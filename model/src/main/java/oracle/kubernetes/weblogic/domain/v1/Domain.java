// Copyright 2017, 2018, Oracle Corporation and/or its affiliates.  All rights reserved.
// Licensed under the Universal Permissive License v 1.0 as shown at
// http://oss.oracle.com/licenses/upl.

package oracle.kubernetes.weblogic.domain.v1;

import com.google.gson.annotations.Expose;
import com.google.gson.annotations.SerializedName;
import io.kubernetes.client.models.V1ObjectMeta;
import io.kubernetes.client.models.V1PersistentVolume;
import io.kubernetes.client.models.V1PersistentVolumeClaim;
import io.kubernetes.client.models.V1SecretReference;
import javax.validation.Valid;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.apache.commons.lang3.builder.ToStringBuilder;

/** Domain represents a WebLogic domain and how it will be realized in the Kubernetes cluster. */
@SuppressWarnings("deprecation")
public class Domain {

  /** The default number of replicas for a cluster. */
  public static final int DEFAULT_REPLICA_LIMIT = 2;

  /**
   * APIVersion defines the versioned schema of this representation of an object. Servers should
   * convert recognized schemas to the latest internal value, and may reject unrecognized values.
   * More info: https://git.k8s.io/community/contributors/devel/api-conventions.md#resources
   */
  @SerializedName("apiVersion")
  @Expose
  private String apiVersion;
  /**
   * Kind is a string value representing the REST resource this object represents. Servers may infer
   * this from the endpoint the client submits requests to. Cannot be updated. In CamelCase. More
   * info: https://git.k8s.io/community/contributors/devel/api-conventions.md#types-kinds
   */
  @SerializedName("kind")
  @Expose
  private String kind;
  /**
   * Standard object's metadata. More info:
   * https://git.k8s.io/community/contributors/devel/api-conventions.md#metadata
   */
  @SerializedName("metadata")
  @Expose
  @Valid
  private V1ObjectMeta metadata;
  /** DomainSpec is a description of a domain. */
  @SerializedName("spec")
  @Expose
  @Valid
  private DomainSpec spec;
  /**
   * DomainStatus represents information about the status of a domain. Status may trail the actual
   * state of a system.
   */
  @SerializedName("status")
  @Expose
  @Valid
  private DomainStatus status;

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
  public Domain withApiVersion(String apiVersion) {
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
  public Domain withKind(String kind) {
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
  public Domain withMetadata(V1ObjectMeta metadata) {
    this.metadata = metadata;
    return this;
  }

  public ServerSpec getAdminServerSpec() {
    return spec.getEffectiveConfigurationFactory(apiVersion).getAdminServerSpec();
  }

  public ServerSpec getServer(String serverName, String clusterName) {
    return spec.getEffectiveConfigurationFactory(apiVersion).getServerSpec(serverName, clusterName);
  }

  /**
   * Returns the number of replicas to start for the specified cluster.
   *
   * @param clusterName the name of the cluster
   * @return the result of applying any configurations for this value
   */
  public int getReplicaCount(String clusterName) {
    return spec.getEffectiveConfigurationFactory(apiVersion).getReplicaCount(clusterName);
  }

  public void setReplicaCount(String clusterName, int replicaLimit) {
    spec.getEffectiveConfigurationFactory(apiVersion).setReplicaCount(clusterName, replicaLimit);
  }

  String getEffectiveStartupControl() {
    return spec.getEffectiveStartupControl();
  }

  /**
   * DomainSpec is a description of a domain.
   *
   * @return Specification
   */
  public DomainSpec getSpec() {
    return spec;
  }

  /**
   * DomainSpec is a description of a domain.
   *
   * @param spec Specification
   */
  public void setSpec(DomainSpec spec) {
    this.spec = spec;
  }

  /**
   * DomainSpec is a description of a domain.
   *
   * @param spec Specification
   * @return this
   */
  public Domain withSpec(DomainSpec spec) {
    this.spec = spec;
    return this;
  }

  /**
   * DomainStatus represents information about the status of a domain. Status may trail the actual
   * state of a system.
   *
   * @return Status
   */
  public DomainStatus getStatus() {
    return status;
  }

  /**
   * DomainStatus represents information about the status of a domain. Status may trail the actual
   * state of a system.
   *
   * @param status Status
   */
  public void setStatus(DomainStatus status) {
    this.status = status;
  }

  /**
   * Reference to secret containing domain administrator username and password.
   *
   * @return admin secret
   */
  public V1SecretReference getAdminSecret() {
    return spec.getAdminSecret();
  }

  /**
   * Returns the name of the admin server.
   *
   * @return admin server name
   */
  public String getAsName() {
    return spec.getAsName();
  }

  /**
   * Returns the port used by the admin server.
   *
   * @return admin server port
   */
  public Integer getAsPort() {
    return spec.getAsPort();
  }

  /**
   * Returns the domain unique identifier.
   *
   * @return domain UID
   */
  public String getDomainUID() {
    return spec.getDomainUID();
  }

  /**
   * Returns the domain name
   *
   * @return domain name
   */
  public String getDomainName() {
    return spec.getDomainName();
  }

  public boolean isShuttingDown() {
    return spec.getEffectiveConfigurationFactory(apiVersion).isShuttingDown();
  }

  /**
   * Returns the name of the persistent volume claim for the logs and PV-based domain.
   *
   * @return volume claim
   */
  public String getPersistentVolumeClaimName() {
    return spec.getPersistentVolumeClaimName();
  }

  /**
   * Returns the persistent volume that must be created for domain storage. May be null.
   *
   * @return a definition of the kubernetes resource to create
   */
  public V1PersistentVolume getRequiredPersistentVolume() {
    return spec.getStorage() == null
        ? null
        : spec.getStorage().getRequiredPersistentVolume(getDomainUID());
  }

  /**
   * Returns the persistent volume claim that must be created for domain storage. May be null.
   *
   * @return a definition of the kubernetes resource to create
   */
  public V1PersistentVolumeClaim getRequiredPersistentVolumeClaim() {
    return spec.getStorage() == null
        ? null
        : spec.getStorage().getRequiredPersistentVolumeClaim(getDomainUID(), getNamespace());
  }

  private String getNamespace() {
    return getMetadata().getNamespace();
  }

  @Override
  public String toString() {
    return new ToStringBuilder(this)
        .append("apiVersion", apiVersion)
        .append("kind", kind)
        .append("metadata", metadata)
        .append("spec", spec)
        .append("status", status)
        .toString();
  }

  @Override
  public int hashCode() {
    return new HashCodeBuilder()
        .append(metadata)
        .append(apiVersion)
        .append(kind)
        .append(spec)
        .append(status)
        .toHashCode();
  }

  @Override
  public boolean equals(Object other) {
    if (other == this) {
      return true;
    }
    if (!(other instanceof Domain)) {
      return false;
    }
    Domain rhs = ((Domain) other);
    return new EqualsBuilder()
        .append(metadata, rhs.metadata)
        .append(apiVersion, rhs.apiVersion)
        .append(kind, rhs.kind)
        .append(spec, rhs.spec)
        .append(status, rhs.status)
        .isEquals();
  }
}
