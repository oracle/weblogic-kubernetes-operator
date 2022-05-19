// Copyright (c) 2018, 2021, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.weblogic.domain.model;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.google.gson.annotations.Expose;
import com.google.gson.annotations.SerializedName;
import io.kubernetes.client.common.KubernetesObject;
import io.kubernetes.client.openapi.models.V1Affinity;
import io.kubernetes.client.openapi.models.V1Container;
import io.kubernetes.client.openapi.models.V1EnvVar;
import io.kubernetes.client.openapi.models.V1ObjectMeta;
import io.kubernetes.client.openapi.models.V1PodSecurityContext;
import io.kubernetes.client.openapi.models.V1PodSpec;
import io.kubernetes.client.openapi.models.V1PodSpec.RestartPolicyEnum;
import io.kubernetes.client.openapi.models.V1SecurityContext;
import io.kubernetes.client.openapi.models.V1ServiceSpec.SessionAffinityEnum;
import io.kubernetes.client.openapi.models.V1VolumeMount;
import jakarta.validation.Valid;
import oracle.kubernetes.json.Description;
import oracle.kubernetes.operator.ServerStartPolicy;
import oracle.kubernetes.operator.ServerStartState;
import oracle.kubernetes.operator.helpers.KubernetesUtils;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.apache.commons.lang3.builder.ToStringBuilder;

import static oracle.kubernetes.operator.LabelConstants.DOMAINUID_LABEL;

/**
 * An element representing a cluster in the domain configuration.
 *
 * @since 2.0
 */
public class Cluster implements Comparable<Cluster>, KubernetesObject {
  /**
   * APIVersion defines the versioned schema of this representation of an object. Servers should
   * convert recognized schemas to the latest internal value, and may reject unrecognized values.
   * More info: https://git.k8s.io/community/contributors/devel/api-conventions.md#resources
   */
  @SerializedName("apiVersion")
  @Description("The API version defines the versioned schema of this Domain.")
  private String apiVersion;

  /**
   * Kind is a string value representing the REST resource this object represents. Servers may infer
   * this from the endpoint the client submits requests to. Cannot be updated. In CamelCase. More
   * info: https://git.k8s.io/community/contributors/devel/api-conventions.md#types-kinds
   */
  @SerializedName("kind")
  @Description("The type of the REST resource. Must be \"Domain\".")
  private String kind;

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

  protected ClusterSpec getConfiguration() {
    ClusterSpec configuration = new ClusterSpec();
    configuration.fillInFrom(this.getSpec());
    configuration.setRestartVersion(this.getRestartVersion());
    return configuration;
  }

  public String getClusterName() {
    return metadata.getName();
  }

  public void setClusterName(@Nonnull String clusterName) {
    metadata.setName(clusterName);
  }

  public Cluster withClusterName(@Nonnull String clusterName) {
    setClusterName(clusterName);
    return this;
  }

  public String getNamespace() {
    return metadata.getNamespace();
  }

  public Integer getReplicas() {
    return spec.getReplicas();
  }

  public void setReplicas(Integer replicas) {
    spec.setReplicas(replicas);
  }

  public Cluster withReplicas(Integer replicas) {
    setReplicas(replicas);
    return this;
  }

  /**
   * Whether to allow number of replicas to drop below the minimum dynamic cluster size configured
   * in the WebLogic domain home configuration.
   *
   * @return whether to allow number of replicas to drop below the minimum dynamic cluster size
   *     configured in the WebLogic domain home configuration.
   */
  public Boolean isAllowReplicasBelowMinDynClusterSize() {
    return spec.isAllowReplicasBelowMinDynClusterSize();
  }

  public void setAllowReplicasBelowMinDynClusterSize(Boolean value) {
    spec.setAllowReplicasBelowMinDynClusterSize(value);
  }

  public Integer getMaxConcurrentStartup() {
    return spec.getMaxConcurrentStartup();
  }

  public void setMaxConcurrentStartup(Integer value) {
    spec.setMaxConcurrentStartup(value);
  }

  public Integer getMaxConcurrentShutdown() {
    return spec.getMaxConcurrentShutdown();
  }

  public void setMaxConcurrentShutdown(Integer value) {
    spec.setMaxConcurrentShutdown(value);
  }

  public ServerStartPolicy getServerStartPolicy() {
    return spec.getServerStartPolicy();
  }

  public void setServerStartPolicy(ServerStartPolicy serverStartPolicy) {
    spec.setServerStartPolicy(serverStartPolicy);
  }

  public Cluster withServerStartPolicy(ServerStartPolicy serverStartPolicy) {
    setServerStartPolicy(serverStartPolicy);
    return this;
  }

  public ClusterService getClusterService() {
    return spec.getClusterService();
  }

  public void setClusterService(ClusterService clusterService) {
    spec.setClusterService(clusterService);
  }

  public Cluster withClusterService(ClusterService clusterService) {
    this.setClusterService(clusterService);
    return this;
  }

  public Cluster spec(ClusterSpec spec) {
    this.spec = spec;
    return this;
  }

  public Map<String, String> getClusterLabels() {
    return getClusterService().getLabels();
  }

  void addClusterLabel(String name, String value) {
    getClusterService().addLabel(name, value);
  }

  public Map<String, String> getClusterAnnotations() {
    return getClusterService().getAnnotations();
  }

  void addClusterAnnotation(String name, String value) {
    getClusterService().addAnnotations(name, value);
  }

  public SessionAffinityEnum getClusterSessionAffinity() {
    return getClusterService().getSessionAffinity();
  }

  Integer getMaxUnavailable() {
    return spec.getMaxUnavailable();
  }

  void setMaxUnavailable(Integer maxUnavailable) {
    spec.setMaxUnavailable(maxUnavailable);
  }

  void fillInFrom(BaseConfiguration other) {
    if (other == null) {
      return;
    }
    spec.fillInFrom(other);
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

    Cluster cluster = (Cluster) o;

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

  public int compareTo(@Nonnull Cluster o) {
    return getClusterName().compareTo(o.getClusterName());
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
  public Cluster withApiVersion(String apiVersion) {
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
  public Cluster withKind(String kind) {
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
  public Cluster withMetadata(V1ObjectMeta metadata) {
    this.metadata = metadata;
    return this;
  }

  /**
   * Returns the domain unique identifier.
   *
   * @return domain UID
   */
  public String getDomainUid() {
    return KubernetesUtils.getDomainUidLabel(metadata);
  }

  /**
   * Sets the value of the domainUID label in the given Kubernetes resource metadata.
   *
   * @param name the uid
   * @return value of the domainUID label
   */
  public Cluster withDomainUid(String name) {
    Optional.ofNullable(metadata)
        .map(V1ObjectMeta::getLabels)
        .ifPresent(labels -> labels.put(DOMAINUID_LABEL, name));
    return this;
  }

  String getRestartVersion() {
    return spec.getRestartVersion();
  }

  void setRestartVersion(String restartVersion) {
    spec.setRestartVersion(restartVersion);
  }

  public List<V1VolumeMount> getAdditionalVolumeMounts() {
    return spec.getAdditionalVolumeMounts();
  }

  void addAdditionalVolume(String name, String path) {
    spec.addAdditionalVolume(name, path);
  }

  void addAdditionalPvClaimVolume(String name, String claimName) {
    spec.addAdditionalPvClaimVolume(name, claimName);
  }

  ProbeTuning getLivenessProbe() {
    return spec.getLivenessProbe();
  }

  void setLivenessProbe(Integer initialDelay, Integer timeout, Integer period) {
    spec.setLivenessProbe(initialDelay, timeout, period);
  }

  public List<V1Container> getContainers() {
    return spec.getContainers();
  }

  public ClusterSpec getSpec() {
    return spec;
  }

  public List<V1Container> getInitContainers() {
    return spec.getInitContainers();
  }

  Shutdown getShutdown() {
    return spec.getShutdown();
  }

  public RestartPolicyEnum getRestartPolicy() {
    return spec.getRestartPolicy();
  }

  public String getRuntimeClassName() {
    return spec.getRuntimeClassName();
  }

  public String getSchedulerName() {
    return spec.getSchedulerName();
  }

  void addEnvironmentVariable(String name, String value) {
    spec.addEnvironmentVariable(new V1EnvVar().name(name).value(value));
  }

  void setServerStartState(@Nullable ServerStartState serverStartState) {
    spec.setServerStartState(serverStartState);
  }

  void setReadinessProbe(Integer initialDelay, Integer timeout, Integer period) {
    spec.setReadinessProbe(initialDelay, timeout, period);
  }

  void setReadinessProbeThresholds(Integer successThreshold, Integer failureThreshold) {
    spec.setReadinessProbeThresholds(successThreshold, failureThreshold);
  }

  void setLivenessProbeThresholds(Integer successThreshold, Integer failureThreshold) {
    spec.setLivenessProbeThresholds(successThreshold, failureThreshold);
  }

  void addNodeSelector(String labelKey, String labelValue) {
    spec.addNodeSelector(labelKey, labelValue);
  }

  void addRequestRequirement(String resource, String quantity) {
    spec.addRequestRequirement(resource, quantity);
  }

  void addLimitRequirement(String resource, String quantity) {
    spec.addLimitRequirement(resource, quantity);
  }

  void setPodSecurityContext(V1PodSecurityContext podSecurityContext) {
    spec.setPodSecurityContext(podSecurityContext);
  }

  V1SecurityContext getContainerSecurityContext() {
    return spec.getContainerSecurityContext();
  }

  void setContainerSecurityContext(V1SecurityContext containerSecurityContext) {
    spec.setContainerSecurityContext(containerSecurityContext);
  }

  public void addAdditionalVolumeMount(String name, String path) {
    spec.addAdditionalVolumeMount(name, path);
  }

  void addInitContainer(V1Container initContainer) {
    spec.addInitContainer(initContainer);
  }

  void addContainer(V1Container container) {
    spec.addContainer(container);
  }

  void addPodLabel(String name, String value) {
    spec.addPodLabel(name, value);
  }

  Map<String, String> getPodAnnotations() {
    return spec.getPodAnnotations();
  }

  void addPodAnnotation(String name, String value) {
    spec.addPodAnnotation(name, value);
  }

  void setRestartPolicy(V1PodSpec.RestartPolicyEnum restartPolicy) {
    spec.setRestartPolicy(restartPolicy);
  }

  void setAffinity(V1Affinity affinity) {
    spec.setAffinity(affinity);
  }

  public void setNodeName(String nodeName) {
    spec.setNodeName(nodeName);
  }

  public void setMaxReadyWaitTimeSeconds(long waitTime) {
    spec.setMaxReadyWaitTimeSeconds(waitTime);
  }
}
