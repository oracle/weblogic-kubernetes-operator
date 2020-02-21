// Copyright (c) 2017, 2020, Oracle Corporation and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.weblogic.domain.model;

import java.util.Optional;
import javax.annotation.Nonnull;
import javax.validation.Valid;
import javax.validation.constraints.NotNull;

import com.google.gson.annotations.Expose;
import oracle.kubernetes.json.Description;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.apache.commons.lang3.builder.ToStringBuilder;

import static oracle.kubernetes.weblogic.domain.model.ObjectPatch.createObjectPatch;

/** ServerStatus describes the current status of a specific WebLogic Server. */
public class ServerStatus implements Comparable<ServerStatus>, PatchableComponent<ServerStatus> {

  @Description("WebLogic Server name. Required.")
  @Expose
  @NotNull
  private String serverName;

  @Description("Current state of this WebLogic Server. Required.")
  @Expose
  @NotNull
  private String state;

  @Description("WebLogic cluster name, if the server is part of a cluster.")
  @Expose
  private String clusterName;

  @Description("Name of node that is hosting the Pod containing this WebLogic Server.")
  @Expose
  private String nodeName;

  @Description("Current status and health of a specific WebLogic Server.")
  @Expose
  @Valid
  private ServerHealth health;

  public ServerStatus() {
  }

  /**
   * Copy constructor for a deep copy.
   * @param other the object to copy
   */
  ServerStatus(ServerStatus other) {
    this.serverName = other.serverName;
    this.state = other.state;
    this.clusterName = other.clusterName;
    this.nodeName = other.nodeName;
    this.health = Optional.ofNullable(other.health).map(ServerHealth::new).orElse(null);
  }

  /**
   * WebLogic Server name. Required.
   *
   * @return server name
   */
  public String getServerName() {
    return serverName;
  }

  /**
   * WebLogic Server name. Required.
   *
   * @param serverName server name
   */
  public void setServerName(String serverName) {
    this.serverName = serverName;
  }

  /**
   * WebLogic Server name. Required.
   *
   * @param serverName server name
   * @return this
   */
  public ServerStatus withServerName(String serverName) {
    this.serverName = serverName;
    return this;
  }

  /**
   * Current state of this WebLogic Server. Required.
   *
   * @return state
   */
  public String getState() {
    return state;
  }

  /**
   * Current state of this WebLogic Server. Required.
   *
   * @param state state
   */
  public void setState(String state) {
    this.state = state;
  }

  /**
   * Current state of this WebLogic Server. Required.
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
   * Name of node that is hosting the Pod containing this WebLogic Server.
   *
   * @return node name
   */
  public String getNodeName() {
    return nodeName;
  }

  /**
   * Name of node that is hosting the Pod containing this WebLogic Server.
   *
   * @param nodeName node name
   * @return this
   */
  public ServerStatus withNodeName(String nodeName) {
    this.nodeName = nodeName;
    return this;
  }

  /**
   * ServerHealth describes the current status and health of a specific WebLogic Server.
   *
   * @return health
   */
  private ServerHealth getHealth() {
    return health;
  }

  /**
   * ServerHealth describes the current status and health of a specific WebLogic Server.
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
  public int compareTo(@Nonnull ServerStatus o) {
    return serverName.compareTo(o.serverName);
  }

  @Override
  public boolean isPatchableFrom(ServerStatus other) {
    return other.getServerName() != null && other.getServerName().equals(serverName);
  }

  private static final ObjectPatch<ServerStatus> serverPatch = createObjectPatch(ServerStatus.class)
        .withStringField("serverName", ServerStatus::getServerName)
        .withStringField("clusterName", ServerStatus::getClusterName)
        .withStringField("state", ServerStatus::getState)
        .withStringField("nodeName", ServerStatus::getNodeName)
        .withObjectField("health", ServerStatus::getHealth, ServerHealth.getObjectPatch());

  static ObjectPatch<ServerStatus> getObjectPatch() {
    return serverPatch;
  }
}
