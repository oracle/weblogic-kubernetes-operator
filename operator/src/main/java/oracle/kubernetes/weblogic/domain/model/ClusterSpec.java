// Copyright (c) 2018, 2022, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.weblogic.domain.model;

import java.util.Map;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.google.gson.annotations.Expose;
import com.google.gson.annotations.SerializedName;
import io.kubernetes.client.openapi.models.V1ServiceSpec;
import oracle.kubernetes.json.Description;
import oracle.kubernetes.json.EnumClass;
import oracle.kubernetes.json.Range;
import oracle.kubernetes.operator.ServerStartPolicy;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.jetbrains.annotations.NotNull;

/**
 * An element representing a cluster in the domain configuration.
 *
 * @since 2.0
 */
public class ClusterSpec extends BaseConfiguration implements Comparable<ClusterSpec> {
  /** The name of the cluster. Required. */
  @Description("The name of the cluster. This value must match the name of a WebLogic cluster already defined "
      + "in the WebLogic domain configuration. Required.")
  @NotNull
  private String clusterName;

  /** Domain unique identifier. Must be unique across the Kubernetes cluster. */
  @Description(
          "Domain unique identifier. This domainUID is used to identify the Domain to which this Cluster "
                  + "is associated with.")
  @SerializedName("domainUID")
  private String domainUid;

  /** The number of replicas to run in the cluster, if specified. */
  @Description(
      "The number of cluster member Managed Server instances to start for this WebLogic cluster. "
      + "The operator will sort cluster member Managed Server names from the WebLogic domain "
      + "configuration by normalizing any numbers in the Managed Server name and then sorting alphabetically. "
      + "This is done so that server names such as \"managed-server10\" come after \"managed-server9\". "
      + "The operator will then start Managed Server instances from the sorted list, "
      + "up to the `replicas` count, unless specific Managed Servers are specified as "
      + "starting in their entry under the `managedServers` field. In that case, the specified Managed Server "
      + "instances will be started and then additional cluster members "
      + "will be started, up to the `replicas` count, by finding further cluster members in the sorted list that are "
      + "not already started. If cluster members are started "
      + "because of their related entries under `managedServers`, then this cluster may have more cluster members "
      + "running than its `replicas` count. Defaults to `spec.replicas`, which defaults 1.")
  @Range(minimum = 0)
  private Integer replicas;

  /**
   * Tells the operator whether the customer wants the server to be running. For clustered servers -
   * the operator will start it if the policy is ALWAYS or the policy is IF_NEEDED and the server
   * needs to be started to get to the cluster's replica count.
   *
   * @since 2.0
   */
  @EnumClass(value = ServerStartPolicy.class, qualifier = "forCluster")
  @Description("The strategy for deciding whether to start a WebLogic Server instance. "
      + "Legal values are `Never`, or `IfNeeded`. Defaults to `IfNeeded`. "
      + "More info: https://oracle.github.io/weblogic-kubernetes-operator/userguide/managing-domains/"
      + "domain-lifecycle/startup/#starting-and-stopping-servers.")
  private ServerStartPolicy serverStartPolicy;

  @Description(
      "The maximum number of cluster members that can be temporarily unavailable. Defaults to 1.")
  @Range(minimum = 1)
  private Integer maxUnavailable;

  @Description("Customization affecting Kubernetes Service generated for this WebLogic cluster.")
  @SerializedName("clusterService")
  @Expose
  private ClusterService clusterService = new ClusterService();

  @Description("Specifies whether the number of running cluster members is allowed to drop below the "
      + "minimum dynamic cluster size configured in the WebLogic domain configuration. "
      + "Otherwise, the operator will ensure that the number of running cluster members is not less than "
      + "the minimum dynamic cluster setting. This setting applies to dynamic clusters only. "
      + "Defaults to true."
  )
  private Boolean allowReplicasBelowMinDynClusterSize;

  @Description(
      "The maximum number of Managed Servers instances that the operator will start in parallel "
      + "for this cluster in response to a change in the `replicas` count. "
      + "If more Managed Server instances must be started, the operator will wait until a Managed "
      + "Server Pod is in the `Ready` state before starting the next Managed Server instance. "
      + "A value of 0 means all Managed Server instances will start in parallel. Defaults to 0."
  )
  @Range(minimum = 0)
  private Integer maxConcurrentStartup;

  @Description(
          "The maximum number of WebLogic Server instances that will shut down in parallel "
                  + "for this cluster when it is being partially shut down by lowering its replica count. "
                  + "A value of 0 means there is no limit. Defaults to `spec.maxClusterConcurrentShutdown`, "
                  + "which defaults to 1."
  )
  @Range(minimum = 0)
  private Integer maxConcurrentShutdown;

  protected ClusterSpec getConfiguration() {
    ClusterSpec configuration = new ClusterSpec();
    configuration.fillInFrom(this);
    configuration.setRestartVersion(this.getRestartVersion());
    return configuration;
  }

  public String getClusterName() {
    return clusterName;
  }

  public void setClusterName(@Nonnull String clusterName) {
    this.clusterName = clusterName;
  }

  public ClusterSpec withClusterName(@Nonnull String clusterName) {
    setClusterName(clusterName);
    return this;
  }

  public String getDomainUid() {
    return domainUid;
  }

  public ClusterSpec withDomainUid(String domainUid) {
    this.domainUid = domainUid;
    return this;
  }

  public Integer getReplicas() {
    return replicas;
  }

  public void setReplicas(Integer replicas) {
    this.replicas = replicas;
  }

  public ClusterSpec withReplicas(Integer replicas) {
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
    return allowReplicasBelowMinDynClusterSize;
  }

  public void setAllowReplicasBelowMinDynClusterSize(Boolean value) {
    allowReplicasBelowMinDynClusterSize = value;
  }

  public Integer getMaxConcurrentStartup() {
    return maxConcurrentStartup;
  }

  public void setMaxConcurrentStartup(Integer value) {
    maxConcurrentStartup = value;
  }

  public Integer getMaxConcurrentShutdown() {
    return maxConcurrentShutdown;
  }

  public void setMaxConcurrentShutdown(Integer value) {
    maxConcurrentShutdown = value;
  }

  @Nullable
  @Override
  public ServerStartPolicy getServerStartPolicy() {
    return serverStartPolicy;
  }

  @Override
  public void setServerStartPolicy(ServerStartPolicy serverStartPolicy) {
    this.serverStartPolicy = serverStartPolicy;
  }

  public ClusterSpec withServerStartPolicy(ServerStartPolicy serverStartPolicy) {
    setServerStartPolicy(serverStartPolicy);
    return this;
  }

  public ClusterService getClusterService() {
    return clusterService;
  }

  public void setClusterService(ClusterService clusterService) {
    this.clusterService = clusterService;
  }

  public ClusterSpec withClusterService(ClusterService clusterService) {
    this.setClusterService(clusterService);
    return this;
  }

  public Map<String, String> getClusterLabels() {
    return clusterService.getLabels();
  }

  void addClusterLabel(String name, String value) {
    clusterService.addLabel(name, value);
  }

  public Map<String, String> getClusterAnnotations() {
    return clusterService.getAnnotations();
  }

  void addClusterAnnotation(String name, String value) {
    clusterService.addAnnotations(name, value);
  }

  public V1ServiceSpec.SessionAffinityEnum getClusterSessionAffinity() {
    return clusterService.getSessionAffinity();
  }

  Integer getMaxUnavailable() {
    return maxUnavailable;
  }

  public void setMaxUnavailable(Integer maxUnavailable) {
    this.maxUnavailable = maxUnavailable;
  }

  void fillInFrom(ClusterSpec other) {
    if (other == null) {
      return;
    }
    super.fillInFrom(other);
    clusterService.fillInFrom(other.clusterService);
  }

  @Override
  public String toString() {
    return new ToStringBuilder(this)
        .appendSuper(super.toString())
        .append("clusterName", clusterName)
        .append("domainUid", domainUid)
        .append("replicas", replicas)
        .append("serverStartPolicy", serverStartPolicy)
        .append("clusterService", clusterService)
        .append("maxUnavailable", maxUnavailable)
        .append("allowReplicasBelowMinDynClusterSize", allowReplicasBelowMinDynClusterSize)
        .append("maxConcurrentStartup", maxConcurrentStartup)
        .append("maxConcurrentShutdown", maxConcurrentShutdown)
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

    ClusterSpec clusterSpec = (ClusterSpec) o;

    return new EqualsBuilder()
        .appendSuper(super.equals(o))
        .append(clusterName, clusterSpec.clusterName)
        .append(domainUid, clusterSpec.domainUid)
        .append(replicas, clusterSpec.replicas)
        .append(serverStartPolicy, clusterSpec.serverStartPolicy)
        .append(clusterService, clusterSpec.clusterService)
        .append(maxUnavailable, clusterSpec.maxUnavailable)
        .append(allowReplicasBelowMinDynClusterSize, clusterSpec.allowReplicasBelowMinDynClusterSize)
        .append(maxConcurrentStartup, clusterSpec.maxConcurrentStartup)
        .append(maxConcurrentShutdown, clusterSpec.maxConcurrentShutdown)
        .isEquals();
  }

  @Override
  public int hashCode() {
    return new HashCodeBuilder(17, 37)
        .appendSuper(super.hashCode())
        .append(clusterName)
        .append(domainUid)
        .append(replicas)
        .append(serverStartPolicy)
        .append(clusterService)
        .append(maxUnavailable)
        .append(allowReplicasBelowMinDynClusterSize)
        .append(maxConcurrentStartup)
        .append(maxConcurrentShutdown)
        .toHashCode();
  }

  @Override
  public int compareTo(@Nonnull ClusterSpec o) {
    return clusterName.compareTo(o.clusterName);
  }
}
