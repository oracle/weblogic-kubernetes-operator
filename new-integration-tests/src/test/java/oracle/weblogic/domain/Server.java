// Copyright (c) 2020, Oracle Corporation and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.weblogic.domain.model;

import io.swagger.annotations.ApiModelProperty;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.apache.commons.lang3.builder.ToStringBuilder;

public class Server {

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

  public Server serverStartPolicy(String serverStartPolicy) {
    this.serverStartPolicy = serverStartPolicy;
    return this;
  }

  public String getServerStartPolicy() {
    return serverStartPolicy;
  }

  public void setServerStartPolicy(String serverStartPolicy) {
    this.serverStartPolicy = serverStartPolicy;
  }

  public Server serverPod(ServerPod serverPod) {
    this.serverPod = serverPod;
    return this;
  }

  public ServerPod getServerPod() {
    return serverPod;
  }

  public void setServerPod(ServerPod serverPod) {
    this.serverPod = serverPod;
  }

  public Server serverStartState(String serverStartState) {
    this.serverStartState = serverStartState;
    return this;
  }

  public String getServerStartState() {
    return serverStartState;
  }

  public void setServerStartState(String serverStartState) {
    this.serverStartState = serverStartState;
  }

  public Server restartVersion(String restartVersion) {
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
        .append("serverStartPolicy", serverStartPolicy)
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

    if (!(o instanceof Server)) {
      return false;
    }

    Server rhs = (Server) o;

    return new EqualsBuilder()
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
        .append(serverStartPolicy)
        .append(serverStartState)
        .append(serverPod)
        .append(serverService)
        .append(restartVersion)
        .toHashCode();
  }

}
