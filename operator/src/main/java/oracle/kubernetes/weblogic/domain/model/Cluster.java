// Copyright (c) 2018, 2020, Oracle Corporation and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.weblogic.domain.model;

import java.util.Map;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.google.gson.annotations.Expose;
import com.google.gson.annotations.SerializedName;
import oracle.kubernetes.json.Description;
import oracle.kubernetes.json.EnumClass;
import oracle.kubernetes.json.Range;
import oracle.kubernetes.operator.ServerStartPolicy;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.apache.commons.lang3.builder.ToStringBuilder;

/**
 * An element representing a cluster in the domain configuration.
 *
 * @since 2.0
 */
public class Cluster extends BaseConfiguration implements Comparable<Cluster> {
  /** The name of the cluster. Required. */
  @Description("The name of the cluster. This value must match the name of a WebLogic cluster already defined "
      + "in the WebLogic domain configuration. Required.")
  @Nonnull
  private String clusterName;

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
      + "running than its `replicas` count. Defaults to 0.")
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
      + "Legal values are NEVER, or IF_NEEDED. Defaults to IF_NEEDED. "
      + "More info: https://oracle.github.io/weblogic-kubernetes-operator/userguide/managing-domains/"
      + "domain-lifecycle/startup/#starting-and-stopping-servers.")
  private String serverStartPolicy;

  @Description(
      "The maximum number of cluster members that can be temporarily unavailable. Defaults to 1.")
  @Range(minimum = 1)
  private Integer maxUnavailable;

  @Description("Customization affecting Kubernetes Service generated for this WebLogic cluster.")
  @SerializedName("clusterService")
  @Expose
  private KubernetesResource clusterService = new KubernetesResource();

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

  protected Cluster getConfiguration() {
    Cluster configuration = new Cluster();
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

  Cluster withClusterName(@Nonnull String clusterName) {
    setClusterName(clusterName);
    return this;
  }

  public Integer getReplicas() {
    return replicas;
  }

  public void setReplicas(Integer replicas) {
    this.replicas = replicas;
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

  @Nullable
  @Override
  public String getServerStartPolicy() {
    return serverStartPolicy;
  }

  @Override
  public void setServerStartPolicy(String serverStartPolicy) {
    this.serverStartPolicy = serverStartPolicy;
  }

  public KubernetesResource getClusterService() {
    return clusterService;
  }

  public void setClusterService(KubernetesResource clusterService) {
    this.clusterService = clusterService;
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

  Integer getMaxUnavailable() {
    return maxUnavailable;
  }

  void setMaxUnavailable(Integer maxUnavailable) {
    this.maxUnavailable = maxUnavailable;
  }

  void fillInFrom(Cluster other) {
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
        .append("replicas", replicas)
        .append("serverStartPolicy", serverStartPolicy)
        .append("clusterService", clusterService)
        .append("maxUnavailable", maxUnavailable)
        .append("allowReplicasBelowMinDynClusterSize", allowReplicasBelowMinDynClusterSize)
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
        .append(clusterName, cluster.clusterName)
        .append(replicas, cluster.replicas)
        .append(serverStartPolicy, cluster.serverStartPolicy)
        .append(clusterService, cluster.clusterService)
        .append(maxUnavailable, cluster.maxUnavailable)
        .append(allowReplicasBelowMinDynClusterSize, cluster.allowReplicasBelowMinDynClusterSize)
        .append(maxConcurrentStartup, cluster.maxConcurrentStartup)
        .isEquals();
  }

  @Override
  public int hashCode() {
    return new HashCodeBuilder(17, 37)
        .appendSuper(super.hashCode())
        .append(clusterName)
        .append(replicas)
        .append(serverStartPolicy)
        .append(clusterService)
        .append(maxUnavailable)
        .append(allowReplicasBelowMinDynClusterSize)
        .append(maxConcurrentStartup)
        .toHashCode();
  }

  @Override
  public int compareTo(@Nonnull Cluster o) {
    return clusterName.compareTo(o.clusterName);
  }
}
