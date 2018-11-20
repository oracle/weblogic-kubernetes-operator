// Copyright 2017, 2018, Oracle Corporation and/or its affiliates.  All rights reserved.
// Licensed under the Universal Permissive License v 1.0 as shown at
// http://oss.oracle.com/licenses/upl.

package oracle.kubernetes.weblogic.domain.v2;

import com.google.gson.annotations.Expose;
import com.google.gson.annotations.SerializedName;
import io.kubernetes.client.models.V1ObjectMeta;
import io.kubernetes.client.models.V1PersistentVolume;
import io.kubernetes.client.models.V1PersistentVolumeClaim;
import io.kubernetes.client.models.V1SecretReference;
import java.util.List;
import java.util.Map;
import javax.annotation.Nonnull;
import javax.validation.Valid;
import oracle.kubernetes.json.Description;
import oracle.kubernetes.operator.LabelConstants;
import oracle.kubernetes.operator.VersionConstants;
import oracle.kubernetes.weblogic.domain.EffectiveConfigurationFactory;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.apache.commons.lang3.builder.ToStringBuilder;

/** Domain represents a WebLogic domain and how it will be realized in the Kubernetes cluster. */
@SuppressWarnings("deprecation")
public class Domain {

  /** The default number of replicas for a cluster. */
  static final int DEFAULT_REPLICA_LIMIT = 2;

  /**
   * APIVersion defines the versioned schema of this representation of an object. Servers should
   * convert recognized schemas to the latest internal value, and may reject unrecognized values.
   * More info: https://git.k8s.io/community/contributors/devel/api-conventions.md#resources
   */
  @SerializedName("apiVersion")
  @Expose
  @Description("The API version for the Domain. Must be 'weblogic.oracle/v2'")
  private String apiVersion;
  /**
   * Kind is a string value representing the REST resource this object represents. Servers may infer
   * this from the endpoint the client submits requests to. Cannot be updated. In CamelCase. More
   * info: https://git.k8s.io/community/contributors/devel/api-conventions.md#types-kinds
   */
  @SerializedName("kind")
  @Expose
  @Description("The type of resourced. Should be 'Domain'")
  private String kind;
  /**
   * Standard object's metadata. More info:
   * https://git.k8s.io/community/contributors/devel/api-conventions.md#metadata
   */
  @SerializedName("metadata")
  @Expose
  @Valid
  @Description("The domain meta-data. Must include the name and namespace.")
  @Nonnull
  private V1ObjectMeta metadata = new V1ObjectMeta();

  /** DomainSpec is a description of a domain. */
  @SerializedName("spec")
  @Expose
  @Valid
  @Description("The actual specification of the domain. Required.")
  @Nonnull
  private DomainSpec spec = new DomainSpec();

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
  public @Nonnull V1ObjectMeta getMetadata() {
    return metadata;
  }

  /**
   * Standard object's metadata. More info:
   * https://git.k8s.io/community/contributors/devel/api-conventions.md#metadata
   *
   * @param metadata Metadata
   */
  public void setMetadata(@Nonnull V1ObjectMeta metadata) {
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
    return getEffectiveConfigurationFactory().getAdminServerSpec();
  }

  private EffectiveConfigurationFactory getEffectiveConfigurationFactory() {
    return spec.getEffectiveConfigurationFactory(getResourceVersion());
  }

  private String getResourceVersion() {
    Map<String, String> labels = metadata.getLabels();
    if (labels == null) return VersionConstants.DEFAULT_DOMAIN_VERSION;
    return labels.get(LabelConstants.RESOURCE_VERSION_LABEL);
  }

  /**
   * Returns the specification applicable to a particular server/cluster combination.
   *
   * @param serverName the name of the server
   * @param clusterName the name of the cluster; may be null or empty if no applicable cluster.
   * @return the effective configuration for the server
   */
  public ServerSpec getServer(String serverName, String clusterName) {
    return getEffectiveConfigurationFactory().getServerSpec(serverName, clusterName);
  }

  /**
   * Returns the number of replicas to start for the specified cluster.
   *
   * @param clusterName the name of the cluster
   * @return the result of applying any configurations for this value
   */
  public int getReplicaCount(String clusterName) {
    return getEffectiveConfigurationFactory().getReplicaCount(clusterName);
  }

  public void setReplicaCount(String clusterName, int replicaLimit) {
    getEffectiveConfigurationFactory().setReplicaCount(clusterName, replicaLimit);
  }

  /**
   * DomainSpec is a description of a domain.
   *
   * @return Specification
   */
  public @Nonnull DomainSpec getSpec() {
    return spec;
  }

  /**
   * DomainSpec is a description of a domain.
   *
   * @param spec Specification
   */
  public void setSpec(@Nonnull DomainSpec spec) {
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

  public String getLogHome() {
    return spec.getLogHome();
  }

  public String getIncludeServerOutInPodLog() {
    return spec.getIncludeServerOutInPodLog();
  }

  public boolean isDomainHomeInImage() {
    return spec.isDomainHomeInImage();
  }

  /**
   * Returns the domain home
   *
   * <p>Defaults to either /shared/domain/ or /shared/domains/domainUID
   *
   * @return domain home
   */
  public String getDomainHome() {
    if (spec.getDomainHome() != null) return spec.getDomainHome();
    if (spec.isDomainHomeInImage()) return "/shared/domain";
    return "/shared/domains/" + getDomainUID();
  }

  public boolean isShuttingDown() {
    return getEffectiveConfigurationFactory().isShuttingDown();
  }

  /**
   * Return the names of the exported admin NAPs.
   *
   * @return a list of names; may be empty
   */
  public List<String> getExportedNetworkAccessPointNames() {
    return getEffectiveConfigurationFactory().getExportedNetworkAccessPointNames();
  }

  public Map<String, String> getChannelServiceLabels(String channel) {
    return getEffectiveConfigurationFactory().getChannelServiceLabels(channel);
  }

  public Map<String, String> getChannelServiceAnnotations(String channel) {
    return getEffectiveConfigurationFactory().getChannelServiceAnnotations(channel);
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

  /**
   * Returns the name of the Kubernetes configmap that contains optional configuration overrides.
   *
   * @return name of the configmap
   */
  public String getConfigOverrides() {
    return spec.getConfigOverrides();
  }

  /**
   * Returns a list of Kubernetes secret names used in optional configuration overrides.
   *
   * @return list of Kubernetes secret names
   */
  public List<String> getConfigOverrideSecrets() {
    return spec.getConfigOverrideSecrets();
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
