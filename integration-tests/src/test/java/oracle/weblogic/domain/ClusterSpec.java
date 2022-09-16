// Copyright (c) 2020, 2021, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.weblogic.domain;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.apache.commons.lang3.builder.ToStringBuilder;


@ApiModel(description = "An element representing a cluster in the domain configuration.")
public class ClusterSpec {

  @ApiModelProperty("The name of this cluster. Required")
  private String clusterName;

  @ApiModelProperty(
      value = "The number of cluster members to run.",
      allowableValues = "range[0,infinity]")
  private Integer replicas;

  @ApiModelProperty(
      "The strategy for deciding whether to start a server. "
          + "Legal values are Never, or IfNeeded.")
  private String serverStartPolicy;

  @ApiModelProperty(
      value =
          "The maximum number of cluster members that can be temporarily unavailable. Defaults to 1.",
      allowableValues = "range[1,infinity]")
  private Integer maxUnavailable;

  @ApiModelProperty(
      "Customization affecting ClusterIP Kubernetes services for the WebLogic cluster.")
  private ClusterService clusterService;

  @ApiModelProperty("Configuration affecting server pods.")
  private ServerPod serverPod;

  @ApiModelProperty(
      "Customization affecting ClusterIP Kubernetes services for WebLogic Server instances.")
  private ServerService serverService;

  @ApiModelProperty(
      "If present, every time this value is updated the operator will restart"
          + " the required servers.")
  private String restartVersion;

  public ClusterSpec clusterName(String clusterName) {
    this.clusterName = clusterName;
    return this;
  }

  public String clusterName() {
    return clusterName;
  }

  public String getClusterName() {
    return clusterName;
  }

  public void setClusterName(String clusterName) {
    this.clusterName = clusterName;
  }

  public ClusterSpec withClusterName(String clusterName) {
    setClusterName(clusterName);
    return this;
  }

  public ClusterSpec replicas(Integer replicas) {
    this.replicas = replicas;
    return this;
  }

  public Integer replicas() {
    return replicas;
  }

  public Integer getReplicas() {
    return replicas;
  }

  public void setReplicas(Integer replicas) {
    this.replicas = replicas;
  }

  public ClusterSpec serverStartPolicy(String serverStartPolicy) {
    this.serverStartPolicy = serverStartPolicy;
    return this;
  }

  public String serverStartPolicy() {
    return serverStartPolicy;
  }

  public String getServerStartPolicy() {
    return serverStartPolicy;
  }

  public void setServerStartPolicy(String serverStartPolicy) {
    this.serverStartPolicy = serverStartPolicy;
  }

  public ClusterSpec maxUnavailable(Integer maxUnavailable) {
    this.maxUnavailable = maxUnavailable;
    return this;
  }

  public Integer maxUnavailable() {
    return maxUnavailable;
  }

  public Integer getMaxUnavailable() {
    return maxUnavailable;
  }

  public void setMaxUnavailable(Integer maxUnavailable) {
    this.maxUnavailable = maxUnavailable;
  }

  public ClusterSpec clusterService(ClusterService clusterService) {
    this.clusterService = clusterService;
    return this;
  }

  public ClusterService clusterService() {
    return clusterService;
  }

  public ClusterService getClusterService() {
    return clusterService;
  }

  public void setClusterService(ClusterService clusterService) {
    this.clusterService = clusterService;
  }

  public ClusterSpec serverPod(ServerPod serverPod) {
    this.serverPod = serverPod;
    return this;
  }

  public ServerPod serverPod() {
    return serverPod;
  }

  public ServerPod getServerPod() {
    return serverPod;
  }

  public void setServerPod(ServerPod serverPod) {
    this.serverPod = serverPod;
  }

  public ServerService getServerService() {
    return serverService;
  }

  public void setServerService(ServerService serverService) {
    this.serverService = serverService;
  }

  public ClusterSpec restartVersion(String restartVersion) {
    this.restartVersion = restartVersion;
    return this;
  }

  public String restartVersion() {
    return restartVersion;
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
        .append("clusterName", clusterName)
        .append("replicas", replicas)
        .append("serverStartPolicy", serverStartPolicy)
        .append("clusterService", clusterService)
        .append("maxUnavailable", maxUnavailable)
        .append("serverPod", serverPod)
        .append("serverService", serverService)
        .append("restartVersion", restartVersion)
        .toString();
  }

  @Override
  public boolean equals(Object other) {
    if (this == other) {
      return true;
    }

    if (other == null || getClass() != other.getClass()) {
      return false;
    }
    ClusterSpec rhs = (ClusterSpec) other;
    return new EqualsBuilder()
        .append(clusterName, rhs.clusterName)
        .append(replicas, rhs.replicas)
        .append(serverStartPolicy, rhs.serverStartPolicy)
        .append(clusterService, rhs.clusterService)
        .append(maxUnavailable, rhs.maxUnavailable)
        .append(serverPod, rhs.serverPod)
        .append(serverService, rhs.serverService)
        .append(restartVersion, rhs.restartVersion)
        .isEquals();
  }

  @Override
  public int hashCode() {
    return new HashCodeBuilder(17, 37)
        .append(clusterName)
        .append(replicas)
        .append(serverStartPolicy)
        .append(clusterService)
        .append(maxUnavailable)
        .append(serverPod)
        .append(serverService)
        .append(restartVersion)
        .toHashCode();
  }
}
