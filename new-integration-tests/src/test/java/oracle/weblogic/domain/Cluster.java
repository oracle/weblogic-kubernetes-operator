// Copyright (c) 2020, Oracle Corporation and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.weblogic.domain;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.apache.commons.lang3.builder.ToStringBuilder;

@ApiModel(description = "An element representing a cluster in the domain configuration.")
public class Cluster {

  @ApiModelProperty("The name of this cluster. Required")
  private String clusterName;

  @ApiModelProperty(value = "The number of cluster members to run.", allowableValues = "range[0,infinity]")
  private Integer replicas;

  @ApiModelProperty(
      "The strategy for deciding whether to start a server. "
          + "Legal values are NEVER, or IF_NEEDED.")
  private String serverStartPolicy;

  @ApiModelProperty(
      value = "The maximum number of cluster members that can be temporarily unavailable. Defaults to 1.",
      allowableValues = "range[1,infinity]")
  private Integer maxUnavailable;

  @ApiModelProperty("Customization affecting ClusterIP Kubernetes services for the WebLogic cluster.")
  private ClusterService clusterService;

  @ApiModelProperty("Configuration affecting server pods.")
  private ServerPod serverPod;

  @ApiModelProperty(
      "Customization affecting ClusterIP Kubernetes services for WebLogic Server instances.")
  private ServerService serverService;

  @ApiModelProperty(
      "The state in which the server is to be started. Use ADMIN if server should start "
          + "in the admin state. Defaults to RUNNING.")
  private String serverStartState;

  @ApiModelProperty(
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

  public Cluster clusterService(ClusterService clusterService) {
    this.clusterService = clusterService;
    return this;
  }

  public ClusterService getClusterService() {
    return clusterService;
  }

  public void setClusterService(ClusterService clusterService) {
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
    return this;
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

    Cluster rhs = (Cluster) o;

    return new EqualsBuilder()
        .appendSuper(super.equals(o))
        .append(clusterName, rhs.clusterName)
        .append(replicas, rhs.replicas)
        .append(serverStartPolicy, rhs.serverStartPolicy)
        .append(clusterService, rhs.clusterService)
        .append(maxUnavailable, rhs.maxUnavailable)
        .append(serverPod, rhs.serverPod)
        .append(serverService, rhs.serverService)
        .append(serverStartState, rhs.serverStartState)
        .append(restartVersion, rhs.restartVersion)
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
