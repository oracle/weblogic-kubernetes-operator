// Copyright (c) 2017, 2020, Oracle Corporation and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.weblogic.domain.model;

import java.util.Optional;
import javax.annotation.Nonnull;
import javax.validation.Valid;
import javax.validation.constraints.NotNull;

import com.google.gson.annotations.Expose;
import oracle.kubernetes.json.Description;
import oracle.kubernetes.utils.OperatorUtils;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.apache.commons.lang3.builder.ToStringBuilder;

import static oracle.kubernetes.weblogic.domain.model.ObjectPatch.createObjectPatch;

/** ServerStatus describes the current status of a specific WebLogic Server. */
public class ServerStatus implements Comparable<ServerStatus>, PatchableComponent<ServerStatus> {

  @Description("WebLogic Server instance name.")
  @Expose
  @NotNull
  private String serverName;

  @Description("Current state of this WebLogic Server instance.")
  @Expose
  @NotNull
  private String state;

  @Description("Desired state of this WebLogic Server instance. Values are RUNNING, ADMIN, or SHUTDOWN.")
  @Expose
  private String desiredState;

  @Description("WebLogic cluster name, if the server is a member of a cluster.")
  @Expose
  private String clusterName;

  @Description("Name of Node that is hosting the Pod containing this WebLogic Server instance.")
  @Expose
  private String nodeName;

  @Description("Current status and health of a specific WebLogic Server instance.")
  @Expose
  @Valid
  private ServerHealth health;

  // volatile so it will not be included in the json schema
  private volatile boolean isAdminServer;

  public ServerStatus() {
  }

  /**
   * Copy constructor for a deep copy.
   * @param other the object to copy
   */
  ServerStatus(ServerStatus other) {
    this.serverName = other.serverName;
    this.state = other.state;
    this.desiredState = other.desiredState;
    this.clusterName = other.clusterName;
    this.nodeName = other.nodeName;
    this.isAdminServer = other.isAdminServer;
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
   * Desired state of this WebLogic Server. Required.
   *
   * @return requested state
   */
  public String getDesiredState() {
    return desiredState;
  }

  /**
   * Desired state of this WebLogic Server. Required.
   *
   * @param desiredState Requested state
   */
  public void setDesiredState(String desiredState) {
    this.desiredState = desiredState;
  }

  /**
   * Desired state of this WebLogic Server. Required.
   *
   * @param stateGoal stateGoal
   * @return this
   */
  public ServerStatus withDesiredState(String stateGoal) {
    this.desiredState = stateGoal;
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
  ServerHealth getHealth() {
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

  private boolean isAdminServer() {
    return isAdminServer;
  }

  /**
   * Boolean indication whether this server is the admin server.
   *
   * @param isAdminServer whether this server is the admin server
   * @return this
   */
  public ServerStatus withIsAdminServer(boolean isAdminServer) {
    this.isAdminServer = isAdminServer;
    return this;
  }

  @Override
  public String toString() {
    return new ToStringBuilder(this)
        .append("serverName", serverName)
        .append("isAdminServer", isAdminServer)
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
        .append(nodeName)
        .append(serverName)
        .append(health)
        .append(state)
        .append(desiredState)
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
        .append(desiredState, rhs.desiredState)
        .append(clusterName, rhs.clusterName)
        .isEquals();
  }

  @Override
  public int compareTo(@Nonnull ServerStatus o) {
    int clustersCompareTo = OperatorUtils.compareSortingStrings(clusterName, o.clusterName);
    return clustersCompareTo == 0 ? compareToServer(o) : clustersCompareTo;
  }

  private int compareToServer(ServerStatus o) {
    if (!isAdminServer && o.isAdminServer) {
      return 1;
    } else if (isAdminServer && !o.isAdminServer) {
      return -1;
    }
    return OperatorUtils.compareSortingStrings(serverName, o.serverName);
  }

  @Override
  public boolean isPatchableFrom(ServerStatus other) {
    return other.getServerName() != null && other.getServerName().equals(serverName);
  }

  private static final ObjectPatch<ServerStatus> serverPatch = createObjectPatch(ServerStatus.class)
        .withStringField("serverName", ServerStatus::getServerName)
        .withStringField("clusterName", ServerStatus::getClusterName)
        .withStringField("state", ServerStatus::getState)
        .withStringField("desiredState", ServerStatus::getDesiredState)
        .withStringField("nodeName", ServerStatus::getNodeName)
        .withObjectField("health", ServerStatus::getHealth, ServerHealth.getObjectPatch());

  static ObjectPatch<ServerStatus> getObjectPatch() {
    return serverPatch;
  }
}
