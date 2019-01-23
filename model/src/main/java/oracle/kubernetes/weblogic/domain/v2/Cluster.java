// Copyright 2018, 2019, Oracle Corporation and/or its affiliates.  All rights reserved.
// Licensed under the Universal Permissive License v 1.0 as shown at
// http://oss.oracle.com/licenses/upl.

package oracle.kubernetes.weblogic.domain.v2;

import com.google.gson.annotations.Expose;
import com.google.gson.annotations.SerializedName;
import java.util.Map;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
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
@Description("An element representing a cluster in the domain configuration.")
public class Cluster extends BaseConfiguration implements Comparable<Cluster> {
  /** The name of the cluster. Required. */
  @Description("The name of this cluster. Required")
  @Nonnull
  private String clusterName;

  /** The number of replicas to run in the cluster, if specified. */
  @Description("The number of managed servers to run in this cluster.")
  @Range(minimum = 0)
  private Integer replicas;

  /**
   * Tells the operator whether the customer wants the server to be running. For clustered servers -
   * the operator will start it if the policy is ALWAYS or the policy is IF_NEEDED and the server
   * needs to be started to get to the cluster's replica count..
   *
   * @since 2.0
   */
  @EnumClass(value = ServerStartPolicy.class, qualifier = "forCluster")
  @Description(
      "The strategy for deciding whether to start a server. "
          + "Legal values are NEVER, or IF_NEEDED.")
  private String serverStartPolicy;

  @Description(
      "The maximum number of cluster members that can be temporarily unavailable. Defaults to 1.")
  @Range(minimum = 1)
  private Integer maxUnavailable;

  @Description("Customization affecting ClusterIP Kubernetes services for WebLogic cluster.")
  @SerializedName("clusterService")
  @Expose
  private KubernetesResource clusterService = new KubernetesResource();

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

  @Override
  public void setServerStartPolicy(String serverStartPolicy) {
    this.serverStartPolicy = serverStartPolicy;
  }

  @Nullable
  @Override
  public String getServerStartPolicy() {
    return serverStartPolicy;
  }

  public void setClusterService(KubernetesResource clusterService) {
    this.clusterService = clusterService;
  }

  public KubernetesResource getClusterService() {
    return clusterService;
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
        .toString();
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;

    if (o == null || getClass() != o.getClass()) return false;

    Cluster cluster = (Cluster) o;

    return new EqualsBuilder()
        .appendSuper(super.equals(o))
        .append(clusterName, cluster.clusterName)
        .append(replicas, cluster.replicas)
        .append(serverStartPolicy, cluster.serverStartPolicy)
        .append(clusterService, cluster.clusterService)
        .append(maxUnavailable, cluster.maxUnavailable)
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
        .toHashCode();
  }

  @Override
  public int compareTo(@Nonnull Cluster o) {
    return clusterName.compareTo(o.clusterName);
  }
}
