// Copyright (c) 2017, 2022, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.weblogic.domain.model;

import java.util.Optional;
import javax.annotation.Nonnull;

import com.google.gson.annotations.Expose;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import oracle.kubernetes.json.Description;
import oracle.kubernetes.utils.OperatorUtils;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.apache.commons.lang3.builder.ToStringBuilder;

import static oracle.kubernetes.operator.WebLogicConstants.SHUTDOWN_STATE;
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
  private String stateGoal;

  @Description("WebLogic cluster name, if the server is a member of a cluster.")
  @Expose
  private String clusterName;

  @Description("Name of Node that is hosting the Pod containing this WebLogic Server instance.")
  @Expose
  private String nodeName;

  @Description("Phase of the WebLogic Server pod. Possible values are: Pending, Succeeded, Failed, Running, "
      + "or Unknown.")
  @Expose
  private String podPhase;

  @Description("Status of the WebLogic Server pod's Ready condition if the pod is in Running phase, otherwise Unknown. "
      + "Possible values are: True, False or Unknown.")
  @Expose
  private String podReady;

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
    this.stateGoal = other.stateGoal;
    this.clusterName = other.clusterName;
    this.nodeName = other.nodeName;
    this.isAdminServer = other.isAdminServer;
    this.podReady = other.podReady;
    this.podPhase = other.podPhase;
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
   * @param state the new state. If null, will preserve previous value if this server already has a health setting,
   *              or will set the value as SHUTDOWN if not.
   */
  public void setState(String state) {
    if (state != null) {
      this.state = state;
    } else if (health == null) {
      this.state = SHUTDOWN_STATE;
    }
  }

  /**
   * Current state of this WebLogic Server. Required.
   *
   * @param state the new state.
   * @return this
   */
  public ServerStatus withState(String state) {
    setState(state);
    return this;
  }

  /**
   * Desired state of this WebLogic Server. Required.
   *
   * @return requested state
   */
  public String getStateGoal() {
    return stateGoal;
  }

  /**
   * Desired state of this WebLogic Server. Required.
   *
   * @param stateGoal stateGoal
   * @return this
   */
  public ServerStatus withStateGoal(String stateGoal) {
    this.stateGoal = stateGoal;
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
  public ServerHealth getHealth() {
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

  /**
   * PodPhase describes the phase of a WebLogic server pod status.
   *
   * @return podPhase
   */
  public String getPodPhase() {
    return podPhase;
  }

  /**
   * PodPhase describes the phase of a WebLogic server pod status.
   *
   * @return podPhase as String
   */
  public String getPodPhaseAsString() {
    return Optional.ofNullable(podPhase).map(Object::toString).orElse(null);
  }

  /**
   * PodPhase describes the phase of a WebLogic server pod status.
   *
   * @param podPhase  phase of server pod
   * @return this
   */
  public ServerStatus withPodPhase(String podPhase) {
    this.podPhase = podPhase;
    return this;
  }

  /**
   * PodReady describes the status of a WebLogic server pod Ready condition if the pod is in Running phase,
   * otherwise Unknown.
   *
   * @return podReady
   */
  public String getPodReady() {
    return podReady;
  }

  /**
   * PodReady describes the status of a WebLogic server pod Ready condition when the pod is in Running phase,
   * otherwise Unknown.
   *
   * @param podReady status of server pod's condition type Ready
   * @return this
   */
  public ServerStatus withPodReady(String podReady) {
    this.podReady = podReady;
    return this;
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

  /**
   * Returns true if this server is an admin server.
   */
  public boolean isAdminServer() {
    return isAdminServer;
  }

  @Override
  public String toString() {
    return new ToStringBuilder(this)
        .append("serverName", serverName)
        .append("isAdminServer", isAdminServer)
        .append("state", state)
        .append("stateGoal", stateGoal)
        .append("clusterName", clusterName)
        .append("nodeName", nodeName)
        .append("podPhase", podPhase)
        .append("podReady", podReady)
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
        .append(stateGoal)
        .append(clusterName)
        .append(podPhase)
        .append(podReady)
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
        .append(stateGoal, rhs.stateGoal)
        .append(clusterName, rhs.clusterName)
        .append(podPhase, rhs.podPhase)
        .append(podReady, rhs.podReady)
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
        .withStringField("stateGoal", ServerStatus::getStateGoal)
        .withStringField("nodeName", ServerStatus::getNodeName)
        .withStringField("podPhase", ServerStatus::getPodPhaseAsString)
        .withStringField("podReady", ServerStatus::getPodReady)
        .withObjectField("health", ServerStatus::getHealth, ServerHealth.getObjectPatch());

  static ObjectPatch<ServerStatus> getObjectPatch() {
    return serverPatch;
  }
}
