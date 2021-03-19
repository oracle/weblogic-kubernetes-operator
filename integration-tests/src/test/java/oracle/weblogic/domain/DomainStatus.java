// Copyright (c) 2020, 2021, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.weblogic.domain;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.apache.commons.lang3.builder.ToStringBuilder;

@ApiModel(
    description =
        "DomainStatus represents information about the status of a domain. "
            + "Status may trail the actual state of a system.")
public class DomainStatus {

  @ApiModelProperty("Current service state of domain.")
  private List<DomainCondition> conditions = new ArrayList<>();

  @ApiModelProperty(
      "A human readable message indicating details about why the domain is in this condition.")
  private String message;

  @ApiModelProperty(
      "A brief CamelCase message indicating details about why the domain is in this state.")
  private String reason;

  @ApiModelProperty("Status of WebLogic Servers in this domain.")
  private List<ServerStatus> servers = new ArrayList<>();

  @ApiModelProperty("Status of WebLogic clusters in this domain.")
  private List<ClusterStatus> clusters = new ArrayList<>();

  @ApiModelProperty(
      "RFC 3339 date and time at which the operator started the domain. This will be when "
          + "the operator begins processing and will precede when the various servers "
          + "or clusters are available.")
  private OffsetDateTime startTime;

  @ApiModelProperty(
      value =
          "The number of running Managed Servers in the WebLogic cluster if there is "
              + "only one cluster in the domain and where the cluster does not explicitly "
              + "configure its replicas in a cluster specification.",
      allowableValues = "range[0,infinity]")
  private Integer replicas;

  public DomainStatus conditions(List<DomainCondition> conditions) {
    this.conditions = conditions;
    return this;
  }

  public List<DomainCondition> conditions() {
    return conditions;
  }

  /**
   * Adds condition item.
   * @param conditionsItem Condition
   * @return this
   */
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

  public String message() {
    return message;
  }

  public String getMessage() {
    return message;
  }

  public void setMessage(String message) {
    this.message = message;
  }

  public DomainStatus reason(String reason) {
    this.reason = reason;
    return this;
  }

  public String reason() {
    return reason;
  }

  public String getReason() {
    return reason;
  }

  public void setReason(String reason) {
    this.reason = reason;
  }

  public DomainStatus servers(List<ServerStatus> servers) {
    this.servers = servers;
    return this;
  }

  public List<ServerStatus> servers() {
    return servers;
  }

  /**
   * Adds servers item.
   * @param serversItem Server
   * @return this
   */
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

  public List<ClusterStatus> clusters() {
    return clusters;
  }

  /**
   * Adds clusters item.
   * @param clustersItem Cluster
   * @return this
   */
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

  public DomainStatus startTime(OffsetDateTime startTime) {
    this.startTime = startTime;
    return this;
  }

  public OffsetDateTime startTime() {
    return startTime;
  }

  public OffsetDateTime getStartTime() {
    return startTime;
  }

  public void setStartTime(OffsetDateTime startTime) {
    this.startTime = startTime;
  }

  public DomainStatus replicas(Integer replicas) {
    this.replicas = replicas;
    return this;
  }

  public Integer replicas() {
    return this.replicas;
  }

  public Integer getReplicas() {
    return replicas;
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
    if (this == other) {
      return true;
    }

    if (other == null || getClass() != other.getClass()) {
      return false;
    }
    DomainStatus rhs = (DomainStatus) other;
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
