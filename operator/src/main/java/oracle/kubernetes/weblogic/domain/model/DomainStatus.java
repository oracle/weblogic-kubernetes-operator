// Copyright (c) 2017, 2020, Oracle Corporation and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.weblogic.domain.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.ListIterator;
import java.util.Objects;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.json.JsonPatchBuilder;
import javax.validation.Valid;

import oracle.kubernetes.json.Description;
import oracle.kubernetes.json.Range;
import oracle.kubernetes.utils.SystemClock;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.joda.time.DateTime;

import static oracle.kubernetes.operator.WebLogicConstants.SHUTDOWN_STATE;
import static oracle.kubernetes.weblogic.domain.model.ObjectPatch.createObjectPatch;

/**
 * DomainStatus represents information about the status of a domain. Status may trail the actual
 * state of a system.
 */
@Description("The current status of the operation of the WebLogic domain. Updated automatically by the operator.")
public class DomainStatus {

  @Description("Current service state of the domain.")
  @Valid
  private List<DomainCondition> conditions = new ArrayList<>();

  @Description(
      "A human readable message indicating details about why the domain is in this condition.")
  private String message;

  @Description(
      "A brief CamelCase message indicating details about why the domain is in this state.")
  private String reason;

  @Description("Status of WebLogic Servers in this domain.")
  @Valid
  // sorted list of ServerStatus
  private final List<ServerStatus> servers;

  @Description("Status of WebLogic clusters in this domain.")
  @Valid
  // sorted list of ClusterStatus
  List<ClusterStatus> clusters = new ArrayList<>();

  @Description(
      "RFC 3339 date and time at which the operator started the domain. This will be when "
          + "the operator begins processing and will precede when the various servers "
          + "or clusters are available.")
  private DateTime startTime = SystemClock.now();

  @Description(
      "The number of running cluster member Managed Servers in the WebLogic cluster if there is "
      + "exactly one cluster defined in the domain configuration and where the `replicas` field is set at the `spec` "
      + "level rather than for the specific cluster under `clusters`. This field is provided to support use of "
      + "Kubernetes scaling for this limited use case.")
  @Range(minimum = 0)
  private Integer replicas;

  public DomainStatus() {
    servers = new ArrayList<>();
  }

  /**
   * A copy constructor that creates a deep copy.
   * @param that the object to copy
   */
  public DomainStatus(DomainStatus that) {
    message = that.message;
    reason = that.reason;
    conditions = that.conditions.stream().map(DomainCondition::new).collect(Collectors.toList());
    servers = that.servers.stream().map(ServerStatus::new).collect(Collectors.toList());
    clusters = that.clusters.stream().map(ClusterStatus::new).collect(Collectors.toList());
    startTime = that.startTime;
    replicas = that.replicas;
  }

  /**
   * Current service state of domain.
   *
   * @return conditions
   */
  public @Nonnull List<DomainCondition> getConditions() {
    return conditions;
  }

  /**
   * Adds a condition to the status, replacing any existing conditions with the same type, and removing other
   * conditions according to the domain rules.
   *
   * @param newCondition the condition to add.
   * @return this object.
   */
  public DomainStatus addCondition(DomainCondition newCondition) {
    if (conditions.contains(newCondition)) {
      return this;
    }

    conditions = conditions.stream().filter(c -> preserve(c, newCondition.getType())).collect(Collectors.toList());

    conditions.add(newCondition);
    reason = newCondition.getStatusReason();
    message = newCondition.getStatusMessage();
    return this;
  }

  private boolean preserve(DomainCondition condition, DomainConditionType newType) {
    for (DomainConditionType type : newType.typesToRemove()) {
      if (condition.getType() == type) {
        return false;
      }
    }

    return true;
  }

  /**
   * True, if condition present based on predicate.
   *
   * @param predicate Predicate
   * @return True, if predicate is satisfied
   */
  public boolean hasConditionWith(Predicate<DomainCondition> predicate) {
    return !getConditionsMatching(predicate).isEmpty();
  }

  /**
   * Removes condition based on predicate.
   *
   * @param predicate Predicate
   */
  public void removeConditionIf(Predicate<DomainCondition> predicate) {
    for (DomainCondition condition : getConditionsMatching(predicate)) {
      removeCondition(condition);
    }
  }

  private List<DomainCondition> getConditionsMatching(Predicate<DomainCondition> predicate) {
    return conditions.stream().filter(predicate).collect(Collectors.toList());
  }

  private void removeCondition(DomainCondition condition) {
    if (condition != null) {
      conditions.remove(condition);
    }
  }

  private DomainCondition getConditionWithType(DomainConditionType type) {
    for (DomainCondition condition : conditions) {
      if (type == condition.getType()) {
        return condition;
      }
    }

    return null;
  }

  private boolean isNewCondition(DomainCondition condition) {
    DomainCondition oldCondition = getConditionWithType(condition.getType());
    if (oldCondition == null) {
      return true;
    }

    if (oldCondition.equals(condition)) {
      return false;
    }

    conditions.remove(oldCondition);
    return true;
  }

  /**
   * A human readable message indicating details about why the domain is in this condition.
   *
   * @return message
   */
  public String getMessage() {
    return message;
  }

  /**
   * A human readable message indicating details about why the domain is in this condition.
   *
   * @param message message
   */
  public void setMessage(String message) {
    this.message = message;
  }

  /**
   * A human readable message indicating details about why the domain is in this condition.
   *
   * @param message message
   * @return this
   */
  public DomainStatus withMessage(String message) {
    this.message = message;
    return this;
  }

  /**
   * A brief CamelCase message indicating details about why the domain is in this state.
   *
   * @return reason
   */
  public String getReason() {
    return reason;
  }

  /**
   * A brief CamelCase message indicating details about why the domain is in this state.
   *
   * @param reason reason
   */
  public void setReason(String reason) {
    this.reason = reason;
  }

  /**
   * A brief CamelCase message indicating details about why the domain is in this state.
   *
   * @param reason reason
   * @return this
   */
  public DomainStatus withReason(String reason) {
    this.reason = reason;
    return this;
  }

  /**
   * The number of running managed servers in the WebLogic cluster if there is only one cluster in
   * the domain and where the cluster does not explicitly configure its replicas in a cluster
   * specification.
   *
   * @return replicas
   */
  public Integer getReplicas() {
    return this.replicas;
  }

  /**
   * The number of running managed servers in the WebLogic cluster if there is only one cluster in
   * the domain and where the cluster does not explicitly configure its replicas in a cluster
   * specification.
   *
   * @param replicas replicas
   */
  public void setReplicas(Integer replicas) {
    this.replicas = replicas;
  }

  /**
   * The number of running managed servers in the WebLogic cluster if there is only one cluster in
   * the domain and where the cluster does not explicitly configure its replicas in a cluster
   * specification.
   *
   * @param replicas replicas
   * @return this
   */
  public DomainStatus withReplicas(Integer replicas) {
    this.replicas = replicas;
    return this;
  }

  /**
   * Status of WebLogic Servers in this domain.
   *
   * @return a sorted list of ServerStatus containing status of WebLogic Servers in this domain
   */
  public List<ServerStatus> getServers() {
    synchronized (servers) {
      return new ArrayList<>(servers);
    }
  }

  /**
   * Status of WebLogic Servers in this domain.
   *
   * @param servers servers
   */
  public void setServers(List<ServerStatus> servers) {
    synchronized (this.servers) {
      if (this.servers.equals(servers)) {
        return;
      }

      List<ServerStatus> newServers = servers
            .stream()
            .map(ServerStatus::new)
            .map(this::adjust)
            .sorted(Comparator.naturalOrder())
            .collect(Collectors.toList());

      this.servers.clear();
      this.servers.addAll(newServers);
    }
  }

  private ServerStatus adjust(ServerStatus server) {
    if (server.getState() == null) {
      ServerStatus oldServer = getMatchingServer(server);
      if ((oldServer != null) && (oldServer.getHealth() == null)) {
        return server;
      }
      server.setState(oldServer == null ? SHUTDOWN_STATE : oldServer.getState());
    }
    return server;
  }

  private ServerStatus getMatchingServer(ServerStatus server) {
    return getServers()
          .stream()
          .filter(s -> Objects.equals(s.getClusterName(), server.getClusterName()))
          .filter(s -> Objects.equals(s.getServerName(), server.getServerName()))
          .findFirst()
          .orElse(null);
  }


  /**
   * Status of WebLogic Servers in this domain.
   *
   * @param servers servers
   * @return this
   */
  public DomainStatus withServers(List<ServerStatus> servers) {
    setServers(servers);
    return this;
  }

  /**
   * Add the status for a server.
   * @param server the status for one server
   * @return this object
   */
  public DomainStatus addServer(ServerStatus server) {
    synchronized (servers) {
      for (ListIterator<ServerStatus> it = servers.listIterator(); it.hasNext(); ) {
        if (Objects.equals(it.next().getServerName(), server.getServerName())) {
          it.remove();
        }
      }

      servers.add(server);
      Collections.sort(servers);
    }
    return this;
  }

  /**
   * Status of WebLogic clusters in this domain.
   *
   * @return a sorted list of ClusterStatus containing status of WebLogic clusters in this domain
   */
  public List<ClusterStatus> getClusters() {
    synchronized (clusters) {
      return new ArrayList<>(clusters);
    }
  }

  /**
   * Set the clusters list.
   * @param clusters the list of clusters to use
   */
  public void setClusters(List<ClusterStatus> clusters) {
    synchronized (this.clusters) {
      if (isClustersEqualIgnoringOrder(clusters, this.clusters)) {
        return;
      }

      List<ClusterStatus> sortedClusters = new ArrayList<>(clusters);
      sortedClusters.sort(Comparator.naturalOrder());

      this.clusters = sortedClusters;
    }
  }

  private boolean isClustersEqualIgnoringOrder(List<ClusterStatus> clusters1, List<ClusterStatus> clusters2) {
    return new HashSet<>(clusters1).equals(new HashSet<>(clusters2));
  }

  /**
   * Add the status for a cluster.
   * @param cluster the status for one cluster
   * @return this object
   */
  public DomainStatus addCluster(ClusterStatus cluster) {
    synchronized (clusters) {
      for (ListIterator<ClusterStatus> it = clusters.listIterator(); it.hasNext(); ) {
        if (Objects.equals(it.next().getClusterName(), cluster.getClusterName())) {
          it.remove();
        }
      }

      clusters.add(cluster);
      Collections.sort(clusters);
    }
    return this;
  }

  /**
   * RFC 3339 date and time at which the operator started the domain. This will be when the operator
   * begins processing and will precede when the various servers or clusters are available.
   *
   * @return start time
   */
  DateTime getStartTime() {
    return startTime;
  }

  @Override
  public String toString() {
    return new ToStringBuilder(this)
        .append("conditions", conditions)
        .append("message", message)
        .append("reason", reason)
        .append("servers", servers)
        .append("clusters", clusters)
        .append("startTime", startTime)
        .toString();
  }

  @Override
  public int hashCode() {
    return new HashCodeBuilder()
        .append(reason)
        .append(startTime)
        .append(Domain.sortOrNull(servers))
        .append(Domain.sortOrNull(clusters))
        .append(Domain.sortOrNull(conditions))
        .append(message)
        .toHashCode();
  }

  @Override
  public boolean equals(Object other) {
    if (other == this) {
      return true;
    }
    if (!(other instanceof DomainStatus)) {
      return false;
    }
    DomainStatus rhs = ((DomainStatus) other);
    return new EqualsBuilder()
        .append(reason, rhs.reason)
        .append(startTime, rhs.startTime)
        .append(servers, rhs.servers)
        .append(Domain.sortOrNull(clusters), Domain.sortOrNull(rhs.clusters))
        .append(Domain.sortOrNull(conditions), Domain.sortOrNull(rhs.conditions))
        .append(message, rhs.message)
        .isEquals();
  }

  private static final ObjectPatch<DomainStatus> statusPatch = createObjectPatch(DomainStatus.class)
        .withConstructor(DomainStatus::new)
        .withStringField("message", DomainStatus::getMessage)
        .withStringField("reason", DomainStatus::getReason)
        .withIntegerField("replicas", DomainStatus::getReplicas)
        .withListField("conditions", DomainCondition.getObjectPatch(), DomainStatus::getConditions)
        .withListField("clusters", ClusterStatus.getObjectPatch(), DomainStatus::getClusters)
        .withListField("servers", ServerStatus.getObjectPatch(), DomainStatus::getServers);

  public void createPatchFrom(JsonPatchBuilder builder, @Nullable DomainStatus oldStatus) {
    statusPatch.createPatch(builder, "/status", oldStatus, this);
  }

}
