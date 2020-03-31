// Copyright (c) 2020, Oracle Corporation and/or its affiliates.
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

@Description("An element representing a cluster in the domain configuration.")
public class Cluster {
  @Description("The name of this cluster. Required")
  private String clusterName;

  @Description("The number of cluster members to run.")
  @Range(minimum = 0)
  private Integer replicas;

  @Description(
      "The strategy for deciding whether to start a server. "
          + "Legal values are NEVER, or IF_NEEDED.")
  private String serverStartPolicy;

  @Description(
      "The maximum number of cluster members that can be temporarily unavailable. Defaults to 1.")
  @Range(minimum = 1)
  private Integer maxUnavailable;

  @Description("Customization affecting ClusterIP Kubernetes services for the WebLogic cluster.")
  private KubernetesResource clusterService = new KubernetesResource();

  @Description("Configuration affecting server pods.")
  private final ServerPod serverPod;

  @Description(
      "Customization affecting ClusterIP Kubernetes services for WebLogic Server instances.")
  private final ServerService serverService;

  @Description(
      "The state in which the server is to be started. Use ADMIN if server should start "
          + "in the admin state. Defaults to RUNNING.")
  private String serverStartState;

  @Description(
      "If present, every time this value is updated the operator will restart"
          + " the required servers.")
  private String restartVersion;

  public Cluster clusterName(String clusterName) {
    this.clusterName = clusterName;
    return this;
  }

  public String getClusterName() {
    return clusterName;
  }

  public void setClusterName(String clusterName) {
    this.clusterName = clusterName;
  }

  public Cluster replicas(Integer replicas) {
    this.replicas = replicas;
    return this;
  }

  public Integer getReplicas() {
    return replicas;
  }

  public void setReplicas(Integer replicas) {
    this.replicas = replicas;
  }

  public Cluster serverStartPolicy(String serverStartPolicy) {
    this.serverStartPolicy = serverStartPolicy;
    return this;
  }

  public String getServerStartPolicy() {
    return serverStartPolicy;
  }

  public void setServerStartPolicy(String serverStartPolicy) {
    this.serverStartPolicy = serverStartPolicy;
  }

  public Cluster maxUnavailable(Integer maxUnavailable) {
    this.maxUnavailable = maxUnavailable;
    return this;
  }

  public Integer getMaxUnavailable() {
    return maxUnavailable;
  }

  public void setMaxUnavailable(Integer maxUnavailable) {
    this.maxUnavailable = maxUnavailable;
  }

  public Cluster clusterService(KubernetesResoruce clusterService) {
    this.clusterService = clusterService;
    return this;
  }

  public KubernetesResource getClusterService() {
    return clusterService;
  }

  public void setClusterService(KubernetesResource clusterService) {
    this.clusterService = clusterService;
  }

  public Cluster serverPod(ServerPod serverPod) {
    this.serverPod = serverPod;
    return this;
  }

  public ServerPod getServerPod() {
    return serverPod;
  }

  public void setServerPod(ServerPod serverPod) {
    this.serverPod = serverPod;
  }

  public Cluster serverStartState(String serverStartState) {
    this.serverStartState = serverStartState;
    return this;
  }

  public String getServerStartState() {
    return serverStartState;
  }

  public void setServerStartState(String serverStartState) {
    this.serverStartState = serverStartState;
  }

  public Cluster restartVersion(String restartVersion) {
    this.restartVersion = restartVersion;
  }

  public String getRestartVersion() {
    return restartVersion;
  }

  public void setRestartVersion(String restartVersion) {
    this.restartVersion = restartVersion;
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
        .append("serverStartState", serverStartState)
        .append("serverPod", serverPod)
        .append("serverService", serverService)
        .append("restartVersion", restartVersion)
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
        .append(serverPod, that.serverPod)
        .append(serverService, that.serverService)
        .append(serverStartState, that.serverStartState)
        .append(restartVersion, that.restartVersion)
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
        .append(serverPod)
        .append(serverService)
        .append(serverStartState)
        .append(restartVersion)
        .toHashCode();
  }

}
