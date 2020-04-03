// Copyright (c) 2020, Oracle Corporation and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.weblogic.domain;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.apache.commons.lang3.builder.ToStringBuilder;

@ApiModel(description = "AdminServer represents the operator configuration for the Administration Server.")
public class AdminServer {

  @ApiModelProperty(
      "Configures which of the Administration Server's WebLogic admin channels should be exposed outside"
          + " the Kubernetes cluster via a node port service.")
  private AdminService adminService;

  @ApiModelProperty(
      "The strategy for deciding whether to start a server. "
          + "Legal values are ALWAYS, NEVER, or IF_NEEDED.")
  private String serverStartPolicy;

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

  public AdminServer adminService(AdminService adminService) {
    this.adminService = adminService;
    return this;
  }

  public AdminService getAdminService() {
    return adminService;
  }

  public AdminServer serverStartPolicy(String serverStartPolicy) {
    this.serverStartPolicy = serverStartPolicy;
    return this;
  }

  public String getServerStartPolicy() {
    return serverStartPolicy;
  }

  public AdminServer serverPod(ServerPod serverPod) {
    this.serverPod = serverPod;
    return this;
  }

  public ServerPod getServerPod() {
    return serverPod;
  }

  public AdminServer serverStartState(String serverStartState) {
    this.serverStartState = serverStartState;
    return this;
  }

  public String getServerStartState() {
    return serverStartState;
  }

  public AdminServer restartVersion(String restartVersion) {
    this.restartVersion = restartVersion;
    return this;
  }

  public String getRestartVersion() {
    return restartVersion;
  }

  @Override
  public String toString() {
    return new ToStringBuilder(this)
        .append("adminService", adminService)
        .append("serverStartPolicy", serverStartPolicy)
        .append("serverStartState", serverStartState)
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
    AdminServer rhs = (AdminServer) other;
    return new EqualsBuilder()
        .append(adminService, rhs.adminService)
        .append(serverStartPolicy, rhs.serverStartPolicy)
        .append(serverStartState, rhs.serverStartState)
        .append(serverPod, rhs.serverPod)
        .append(serverService, rhs.serverService)
        .append(restartVersion, rhs.restartVersion)
        .isEquals();
  }

  @Override
  public int hashCode() {
    return new HashCodeBuilder(17, 37)
        .append(adminService)
        .append(serverStartPolicy)
        .append(serverStartState)
        .append(serverPod)
        .append(serverService)
        .append(restartVersion)
        .toHashCode();
  }

}
