// Copyright (c) 2020, Oracle Corporation and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.weblogic.domain;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.apache.commons.lang3.builder.ToStringBuilder;

@ApiModel(description = "ServerStatus describes the current status of a specific WebLogic Server.")
public class ServerStatus {

  @ApiModelProperty("WebLogic Server name. Required.")
  private String serverName;

  @ApiModelProperty("Current state of this WebLogic Server. Required.")
  private String state;

  @ApiModelProperty("Desired state of this WebLogic Server.")
  private String desiredState;

  @ApiModelProperty("WebLogic cluster name, if the server is part of a cluster.")
  private String clusterName;

  @ApiModelProperty("Name of node that is hosting the Pod containing this WebLogic Server.")
  private String nodeName;

  @ApiModelProperty("Current status and health of a specific WebLogic Server.")
  private ServerHealth health;

  public ServerStatus serverName(String serverName) {
    this.serverName = serverName;
    return this;
  }

  public String getServerName() {
    return serverName;
  }

  public ServerStatus state(String state) {
    this.state = state;
    return this;
  }

  public String getState() {
    return state;
  }

  public ServerStatus desiredState(String desiredState) {
    this.desiredState = desiredState;
    return this;
  }

  public String getDesiredState() {
    return desiredState;
  }

  public ServerStatus clusterName(String clusterName) {
    this.clusterName = clusterName;
    return this;
  }

  public String getClusterName() {
    return clusterName;
  }

  public ServerStatus nodeName(String nodeName) {
    this.nodeName = nodeName;
    return this;
  }

  public String getNodeName() {
    return nodeName;
  }

  public ServerStatus health(ServerHealth health) {
    this.health = health;
    return this;
  }

  public ServerHealth getHealth() {
    return health;
  }

  @Override
  public String toString() {
    return new ToStringBuilder(this)
        .append("serverName", serverName)
        .append("state", state)
        .append("desiredState", desiredState)
        .append("clusterName", clusterName)
        .append("nodeName", nodeName)
        .append("health", health)
        .toString();
  }

  @Override
  public int hashCode() {
    return new HashCodeBuilder()
        .append(serverName)
        .append(state)
        .append(desiredState)
        .append(clusterName)
        .append(nodeName)
        .append(health)
        .toHashCode();
  }

  @Override
  public boolean equals(Object other) {
    if (this == other) {
      return true;
    }

    if (other == null || getClass() != other.getClass()) {
      return false;
    }
    ServerStatus rhs = (ServerStatus) other;
    return new EqualsBuilder()
        .append(serverName, rhs.serverName)
        .append(state, rhs.state)
        .append(desiredState, rhs.desiredState)
        .append(clusterName, rhs.clusterName)
        .append(nodeName, rhs.nodeName)
        .append(health, rhs.health)
        .isEquals();
  }

}
