// Copyright 2017, 2019, Oracle Corporation and/or its affiliates.  All rights reserved.
// Licensed under the Universal Permissive License v 1.0 as shown at
// http://oss.oracle.com/licenses/upl.

package oracle.kubernetes.weblogic.domain.v2;

import com.google.gson.annotations.Expose;
import com.google.gson.annotations.SerializedName;
import javax.validation.Valid;
import javax.validation.constraints.NotNull;
import oracle.kubernetes.json.Description;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.apache.commons.lang3.builder.ToStringBuilder;

/** ServerStatus describes the current status of a specific WebLogic server. */
public class ServerStatus implements Comparable<ServerStatus> {

  @Description("WebLogic server name. Required")
  @SerializedName("serverName")
  @Expose
  @NotNull
  private String serverName;

  @Description("Current state of this WebLogic server. Required")
  @SerializedName("state")
  @Expose
  @NotNull
  private String state;

  @Description("WebLogic cluster name, if the server is part of a cluster.")
  @SerializedName("clusterName")
  @Expose
  private String clusterName;

  @Description("Name of node that is hosting the Pod containing this WebLogic server.")
  @SerializedName("nodeName")
  @Expose
  private String nodeName;

  @Description("Current status and health of a specific WebLogic server.")
  @SerializedName("health")
  @Expose
  @Valid
  private ServerHealth health;

  /**
   * WebLogic server name. (Required)
   *
   * @return server name
   */
  public String getServerName() {
    return serverName;
  }

  /**
   * WebLogic server name. (Required)
   *
   * @param serverName server name
   */
  public void setServerName(String serverName) {
    this.serverName = serverName;
  }

  /**
   * WebLogic server name. (Required)
   *
   * @param serverName server name
   * @return this
   */
  public ServerStatus withServerName(String serverName) {
    this.serverName = serverName;
    return this;
  }

  /**
   * Current state of this WebLogic server. (Required)
   *
   * @return state
   */
  public String getState() {
    return state;
  }

  /**
   * Current state of this WebLogic server. (Required)
   *
   * @param state state
   */
  public void setState(String state) {
    this.state = state;
  }

  /**
   * Current state of this WebLogic server. (Required)
   *
   * @param state state
   * @return this
   */
  public ServerStatus withState(String state) {
    this.state = state;
    return this;
  }

  /**
   * WebLogic cluster name, if the server is part of a cluster.
   *
   * @return cluster name
   */
  public String getClusterName() {
    return clusterName;
  }

  /**
   * WebLogic cluster name, if the server is part of a cluster.
   *
   * @param clusterName cluster name
   */
  public void setClusterName(String clusterName) {
    this.clusterName = clusterName;
  }

  /**
   * WebLogic cluster name, if the server is part of a cluster.
   *
   * @param clusterName cluster name
   * @return this
   */
  public ServerStatus withClusterName(String clusterName) {
    this.clusterName = clusterName;
    return this;
  }

  /**
   * Name of node that is hosting the Pod containing this WebLogic server.
   *
   * @return node name
   */
  public String getNodeName() {
    return nodeName;
  }

  /**
   * Name of node that is hosting the Pod containing this WebLogic server.
   *
   * @param nodeName node name
   */
  public void setNodeName(String nodeName) {
    this.nodeName = nodeName;
  }

  /**
   * Name of node that is hosting the Pod containing this WebLogic server.
   *
   * @param nodeName node name
   * @return this
   */
  public ServerStatus withNodeName(String nodeName) {
    this.nodeName = nodeName;
    return this;
  }

  /**
   * ServerHealth describes the current status and health of a specific WebLogic server.
   *
   * @return health
   */
  public ServerHealth getHealth() {
    return health;
  }

  /**
   * ServerHealth describes the current status and health of a specific WebLogic server.
   *
   * @param health health
   */
  public void setHealth(ServerHealth health) {
    this.health = health;
  }

  /**
   * ServerHealth describes the current status and health of a specific WebLogic server.
   *
   * @param health health
   * @return this
   */
  public ServerStatus withHealth(ServerHealth health) {
    this.health = health;
    return this;
  }

  @Override
  public String toString() {
    return new ToStringBuilder(this)
        .append("serverName", serverName)
        .append("state", state)
        .append("clusterName", clusterName)
        .append("nodeName", nodeName)
        .append("health", health)
        .toString();
  }

  @Override
  public int hashCode() {
    return new HashCodeBuilder()
        .append(nodeName)
        .append(serverName)
        .append(health)
        .append(state)
        .append(clusterName)
        .toHashCode();
  }

  @Override
  public boolean equals(Object other) {
    if (other == this) {
      return true;
    }
    if (!(other instanceof ServerStatus)) {
      return false;
    }
    ServerStatus rhs = ((ServerStatus) other);
    return new EqualsBuilder()
        .append(nodeName, rhs.nodeName)
        .append(serverName, rhs.serverName)
        .append(health, rhs.health)
        .append(state, rhs.state)
        .append(clusterName, rhs.clusterName)
        .isEquals();
  }

  @Override
  public int compareTo(ServerStatus o) {
    return serverName.compareTo(o.serverName);
  }
}
