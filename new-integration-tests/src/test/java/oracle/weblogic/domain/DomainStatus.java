// Copyright (c) 2020, Oracle Corporation and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.weblogic.domain.model;

import java.util.ArrayList;
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

import static oracle.kubernetes.weblogic.domain.model.ObjectPatch.createObjectPatch;

@Description(
    "DomainStatus represents information about the status of a domain. "
        + "Status may trail the actual state of a system.")
public class DomainStatus {

  @Description("Current service state of domain.")
  private List<DomainCondition> conditions = new ArrayList<>();

  @Description(
      "A human readable message indicating details about why the domain is in this condition.")
  private String message;

  @Description(
      "A brief CamelCase message indicating details about why the domain is in this state.")
  private String reason;

  @Description("Status of WebLogic Servers in this domain.")
  private List<ServerStatus> servers = new ArrayList<>();

  @Description("Status of WebLogic clusters in this domain.")
  private List<ClusterStatus> clusters = new ArrayList<>();

  @Description(
      "RFC 3339 date and time at which the operator started the domain. This will be when "
          + "the operator begins processing and will precede when the various servers "
          + "or clusters are available.")
  private DateTime startTime;

  @Description(
      "The number of running Managed Servers in the WebLogic cluster if there is "
          + "only one cluster in the domain and where the cluster does not explicitly "
          + "configure its replicas in a cluster specification.")
  @Range(minimum = 0)
  private Integer replicas;

  public DomainStatus conditions(List<DomainCondition> conditions) {
    this.conditions = conditions;
    return DomainStatus;
  }

  public DomainStatus addConditionsItem(DomainCondition conditionsItem) {
    if (conditions == null) {
      conditions = new ArrayList<>();
    }
    conditions.add(conditionsItem);
    return this;
  }

  public List<DomainCondition> getConditions() {
    return conditions;
  }

  public void setConditions(List<DomainCondition> conditions) {
    this.conditions = conditions;
  }

  public DomainStatus message(String message) {
    this.message = message;
    return this;
  }

  public String getMessage() {
    return message;
  }

  public void setMessage(String message) {
    this.message = message;
  }

  public String getReason() {
    return reason;
  }

  public DomainStatus reason(String reason) {
    this.reason = reason;
    return this;
  }

  public void setReason(String reason) {
    this.reason = reason;
  }

  public DomainStatus servers(List<ServerStatus> servers) {
    this.servers = servers;
    return this;
  }

  public DomainStatus addServersItem(ServerStatus serversItem) {
    if (servers == null) {
      servers = new ArrayList<>();
    }
    servers.add(serversItem);
    return this;
  }

  public List<ServerStatus> getServers() {
    return servers;
  }

  public void setServers(List<ServerStatus> servers) {
    this.servers = servers;
  }

  public DomainStatus clusters(List<ClusterStatus> clusters) {
    this.clusters = clusters;
    return this;
  }

  public DomainStatus addClustersItem(ClusterStatus clustersItem) {
    if (clusters == null) {
      clusters = new ArrayList<>();
    }
    clusters.add(clustersItem);
    return this;
  }

  public List<ClusterStatus> getClusters() {
    return clusters;
  }

  public void setClusters(List<ClusterStatus> clusters) {
    this.clusters = clusters;
  }

  public DomainStatus startTime(DateTime startTime) {
    this.startTime = startTime;
    return this;
  }

  public DateTime getStartTime() {
    return startTime;
  }

  public void setStartTime(DateTime startTime) {
    this.startTime = startTime;
  }

  public DomainStatus replicas(Integer replicas) {
    this.replicas = replicas;
    return this;
  }

  public Integer getReplicas() {
    return this.replicas;
  }

  public void setReplicas(Integer replicas) {
    this.replicas = replicas;
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
        .append("replicas", replicas)
        .toString();
  }

  @Override
  public int hashCode() {
    return new HashCodeBuilder()
        .append(conditions)
        .append(message)
        .append(reason)
        .append(servers)
        .append(clusters)
        .append(startTime)
        .append(replicas)
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
        .append(conditions, rhs.conditions)
        .append(message, rhs.message)
        .append(reason, rhs.reason)
        .append(servers, rhs.servers)
        .append(clusters, rhs.clusters)
        .append(startTime, rhs.startTime)
        .append(replicas, rhs.replicas)
        .isEquals();
  }

}
