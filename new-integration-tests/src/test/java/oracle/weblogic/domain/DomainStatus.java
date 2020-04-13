// Copyright (c) 2020, Oracle Corporation and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.weblogic.domain;

import java.util.ArrayList;
import java.util.List;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.joda.time.DateTime;

@ApiModel(description =
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
  private DateTime startTime;

  @ApiModelProperty(
      value = "The number of running Managed Servers in the WebLogic cluster if there is "
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

  public DomainStatus addConditionsItem(DomainCondition conditionsItem) {
    if (conditions == null) {
      conditions = new ArrayList<>();
    }
    conditions.add(conditionsItem);
    return this;
  }

  public DomainStatus message(String message) {
    this.message = message;
    return this;
  }

  public String message() {
    return message;
  }

  public DomainStatus reason(String reason) {
    this.reason = reason;
    return this;
  }

  public String reason() {
    return reason;
  }

  public DomainStatus servers(List<ServerStatus> servers) {
    this.servers = servers;
    return this;
  }

  public List<ServerStatus> servers() {
    return servers;
  }

  public DomainStatus addServersItem(ServerStatus serversItem) {
    if (servers == null) {
      servers = new ArrayList<>();
    }
    servers.add(serversItem);
    return this;
  }

  public DomainStatus clusters(List<ClusterStatus> clusters) {
    this.clusters = clusters;
    return this;
  }

  public List<ClusterStatus> clusters() {
    return clusters;
  }

  public DomainStatus addClustersItem(ClusterStatus clustersItem) {
    if (clusters == null) {
      clusters = new ArrayList<>();
    }
    clusters.add(clustersItem);
    return this;
  }

  public DomainStatus startTime(DateTime startTime) {
    this.startTime = startTime;
    return this;
  }

  public DateTime startTime() {
    return startTime;
  }

  public DomainStatus replicas(Integer replicas) {
    this.replicas = replicas;
    return this;
  }

  public Integer replicas() {
    return this.replicas;
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
