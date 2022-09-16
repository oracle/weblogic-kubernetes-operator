// Copyright (c) 2020, 2021, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.weblogic.domain;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.apache.commons.lang3.builder.ToStringBuilder;

@ApiModel(
    description =
        "AdminServer represents the operator configuration for the Administration Server.")
public class AdminServer {

  @ApiModelProperty(
      "Configures which of the Administration Server's WebLogic admin channels should be exposed outside"
          + " the Kubernetes cluster via a node port service.")
  private AdminService adminService;

  @ApiModelProperty(
      "The strategy for deciding whether to start a server. "
          + "Legal values are Always, Never, or IfNeeded.")
  private String serverStartPolicy;

  @ApiModelProperty("Configuration affecting server pods.")
  private ServerPod serverPod;

  @ApiModelProperty(
      "Customization affecting ClusterIP Kubernetes services for WebLogic Server instances.")
  private ServerService serverService;

  @ApiModelProperty(
      "If present, every time this value is updated the operator will restart"
          + " the required servers.")
  private String restartVersion;

  @ApiModelProperty(
      "When this flag is enabled, the operator updates the domain's WebLogic\n"
          + " configuration for its Administration Server to have an admin protocol\n"
          + " NetworkAccessPoint with a 'localhost' address for each existing admin\n"
          + " protocol capable port. This allows external Administration Console and WLST\n"
          + " 'T3' access when using the 'kubectl port-forward' pattern. Defaults to true.")
  private boolean adminChannelPortForwardingEnabled = true;

  public AdminServer adminService(AdminService adminService) {
    this.adminService = adminService;
    return this;
  }

  public AdminService adminService() {
    return adminService;
  }

  public AdminService getAdminService() {
    return adminService;
  }

  public void setAdminService(AdminService adminService) {
    this.adminService = adminService;
  }

  public AdminServer serverStartPolicy(String serverStartPolicy) {
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

  public AdminServer serverPod(ServerPod serverPod) {
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

  public AdminServer serverService(ServerService serverService) {
    this.serverService = serverService;
    return this;
  }

  public ServerService serverService() {
    return serverService;
  }

  public ServerService getServerService() {
    return serverService;
  }

  public void setServerService(ServerService serverService) {
    this.serverService = serverService;
  }

  public AdminServer restartVersion(String restartVersion) {
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

  public AdminServer adminChannelPortForwardingEnabled(boolean adminChannelPortForwardingEnabled) {
    this.adminChannelPortForwardingEnabled = adminChannelPortForwardingEnabled;
    return this;
  }

  public boolean adminChannelPortForwardingEnabled() {
    return adminChannelPortForwardingEnabled;
  }

  public boolean getAdminChannelPortForwardingEnabled() {
    return adminChannelPortForwardingEnabled;
  }

  public void setAdminChannelPortForwardingEnabled(boolean adminChannelPortForwardingEnabled) {
    this.adminChannelPortForwardingEnabled = adminChannelPortForwardingEnabled;
  }

  @Override
  public String toString() {
    return new ToStringBuilder(this)
        .append("adminService", adminService)
        .append("serverStartPolicy", serverStartPolicy)
        .append("serverPod", serverPod)
        .append("serverService", serverService)
        .append("restartVersion", restartVersion)
        .append("adminChannelPortForwardingEnabled", adminChannelPortForwardingEnabled)
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
        .append(serverPod, rhs.serverPod)
        .append(serverService, rhs.serverService)
        .append(restartVersion, rhs.restartVersion)
        .append(adminChannelPortForwardingEnabled, rhs.adminChannelPortForwardingEnabled)
        .isEquals();
  }

  @Override
  public int hashCode() {
    return new HashCodeBuilder(17, 37)
        .append(adminService)
        .append(serverStartPolicy)
        .append(serverPod)
        .append(serverService)
        .append(restartVersion)
        .append(adminChannelPortForwardingEnabled)
        .toHashCode();
  }
}
