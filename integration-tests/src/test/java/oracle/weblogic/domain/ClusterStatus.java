// Copyright (c) 2020, 2021, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.weblogic.domain;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.apache.commons.lang3.builder.ToStringBuilder;

@ApiModel(
    description = "ClusterStatus describes the current status of a specific WebLogic cluster.")
public class ClusterStatus {

  @ApiModelProperty("WebLogic cluster name. Required.")
  private String clusterName;

  @ApiModelProperty(
      value = "The number of intended cluster members. Required.",
      allowableValues = "range[0,infinity]")
  private Integer replicas;

  @ApiModelProperty(
      value = "The number of ready cluster members. Required.",
      allowableValues = "range[0,infinity]")
  private Integer readyReplicas;

  @ApiModelProperty(
      value = "The maximum number of cluster members. Required.",
      allowableValues = "range[0,infinity]")
  private Integer maximumReplicas;

  @ApiModelProperty(
      value = "The minimum number of cluster members.",
      allowableValues = "range[0,infinity]")
  private Integer minimumReplicas;

  @ApiModelProperty(
      value =
          "The requested number of cluster members from the domain spec. "
              + "Cluster members will be started by the operator if this value is larger than zero.",
      allowableValues = "range[0,infinity]")
  private Integer replicasGoal;

  public ClusterStatus clusterName(String clusterName) {
    this.clusterName = clusterName;
    return this;
  }

  public String clusterName() {
    return clusterName;
  }

  public String getClusterName() {
    return clusterName;
  }

  public void setClusterName(String clusterName) {
    this.clusterName = clusterName;
  }

  public ClusterStatus replicas(Integer replicas) {
    this.replicas = replicas;
    return this;
  }

  public Integer replicas() {
    return replicas;
  }

  public Integer getReplicas() {
    return replicas;
  }

  public void setReplicas(Integer replicas) {
    this.replicas = replicas;
  }

  public ClusterStatus readyReplicas(Integer readyReplicas) {
    this.readyReplicas = readyReplicas;
    return this;
  }

  public Integer readyReplicas() {
    return readyReplicas;
  }

  public Integer getReadyReplicas() {
    return readyReplicas;
  }

  public void setReadyReplicas(Integer readyReplicas) {
    this.readyReplicas = readyReplicas;
  }

  public ClusterStatus maximumReplicas(Integer maximumReplicas) {
    this.maximumReplicas = maximumReplicas;
    return this;
  }

  public Integer maximumReplicas() {
    return maximumReplicas;
  }

  public Integer getMaximumReplicas() {
    return maximumReplicas;
  }

  public void setMaximumReplicas(Integer maximumReplicas) {
    this.maximumReplicas = maximumReplicas;
  }

  public ClusterStatus minimumReplicas(Integer minimumReplicas) {
    this.minimumReplicas = minimumReplicas;
    return this;
  }

  public Integer minimumReplicas() {
    return minimumReplicas;
  }

  public Integer getMinimumReplicas() {
    return minimumReplicas;
  }

  public void setMinimumReplicas(Integer minimumReplicas) {
    this.minimumReplicas = minimumReplicas;
  }

  public ClusterStatus replicasGoal(Integer replicasGoal) {
    this.replicasGoal = replicasGoal;
    return this;
  }

  public Integer replicasGoal() {
    return replicasGoal;
  }

  public Integer getReplicasGoal() {
    return replicasGoal;
  }

  public void setReplicasGoal(Integer replicasGoal) {
    this.replicasGoal = replicasGoal;
  }

  @Override
  public String toString() {
    return new ToStringBuilder(this)
        .append("clusterName", clusterName)
        .append("replicas", replicas)
        .append("readyReplicas", readyReplicas)
        .append("maximumReplicas", maximumReplicas)
        .append("mimimumReplicas", minimumReplicas)
        .append("replicasGoal", replicasGoal)
        .toString();
  }

  @Override
  public int hashCode() {
    return new HashCodeBuilder()
        .append(clusterName)
        .append(replicas)
        .append(readyReplicas)
        .append(maximumReplicas)
        .append(minimumReplicas)
        .append(replicasGoal)
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
    ClusterStatus rhs = (ClusterStatus) other;
    return new EqualsBuilder()
        .append(clusterName, rhs.clusterName)
        .append(replicas, rhs.replicas)
        .append(readyReplicas, rhs.readyReplicas)
        .append(maximumReplicas, rhs.maximumReplicas)
        .append(minimumReplicas, rhs.minimumReplicas)
        .append(replicasGoal, rhs.replicasGoal)
        .isEquals();
  }
}
