// Copyright (c) 2019, Oracle Corporation and/or its affiliates.  All rights reserved.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.weblogic.domain.model;

import com.google.gson.annotations.Expose;
import com.google.gson.annotations.SerializedName;
import oracle.kubernetes.json.Description;
import oracle.kubernetes.json.Range;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.apache.commons.lang3.builder.ToStringBuilder;

/** ServerStatus describes the current status of a specific WebLogic Server. */
public class ClusterStatus implements Comparable<ClusterStatus> {

  @Description("WebLogic cluster name. Required.")
  @SerializedName("clusterName")
  @Expose
  private String clusterName;

  /** The number of intended cluster members. Required. */
  @Description("The number of intended cluster members. Required.")
  @Range(minimum = 0)
  private Integer replicas;

  /** The number of ready cluster members. Required. */
  @Description("The number of ready cluster members. Required.")
  @Range(minimum = 0)
  private Integer readyReplicas;

  /** The maximum number of cluster members. Required. */
  @Description("The maximum number of cluster members. Required.")
  @Range(minimum = 0)
  private Integer maximumReplicas;

  /**
   * WebLogic cluster name. Required.
   *
   * @return cluster name
   */
  public String getClusterName() {
    return clusterName;
  }

  /**
   * WebLogic cluster name. Required.
   *
   * @param clusterName cluster name
   */
  public void setClusterName(String clusterName) {
    this.clusterName = clusterName;
  }

  /**
   * WebLogic cluster name. Required.
   *
   * @param clusterName cluster name
   * @return this
   */
  public ClusterStatus withClusterName(String clusterName) {
    this.clusterName = clusterName;
    return this;
  }

  public Integer getReplicas() {
    return replicas;
  }

  public void setReplicas(Integer replicas) {
    this.replicas = replicas;
  }

  public ClusterStatus withReplicas(Integer replicas) {
    this.replicas = replicas;
    return this;
  }

  public Integer getReadyReplicas() {
    return readyReplicas;
  }

  public void setReadyReplicas(Integer readyReplicas) {
    this.readyReplicas = readyReplicas;
  }

  public ClusterStatus withReadyReplicas(Integer readyReplicas) {
    this.readyReplicas = readyReplicas;
    return this;
  }

  public Integer getMaximumReplicas() {
    return maximumReplicas;
  }

  public void setMaximumReplicas(Integer maximumReplicas) {
    this.maximumReplicas = maximumReplicas;
  }

  public ClusterStatus withMaximumReplicas(Integer maximumReplicas) {
    this.maximumReplicas = maximumReplicas;
    return this;
  }

  @Override
  public String toString() {
    return new ToStringBuilder(this)
        .append("clusterName", clusterName)
        .append("replicas", replicas)
        .append("readyReplicas", readyReplicas)
        .append("maximumReplicas", maximumReplicas)
        .toString();
  }

  @Override
  public int hashCode() {
    return new HashCodeBuilder()
        .append(clusterName)
        .append(replicas)
        .append(readyReplicas)
        .append(maximumReplicas)
        .toHashCode();
  }

  @Override
  public boolean equals(Object other) {
    if (other == this) {
      return true;
    }
    if (!(other instanceof ClusterStatus)) {
      return false;
    }
    ClusterStatus rhs = ((ClusterStatus) other);
    return new EqualsBuilder()
        .append(clusterName, rhs.clusterName)
        .append(replicas, rhs.replicas)
        .append(readyReplicas, rhs.readyReplicas)
        .append(maximumReplicas, rhs.maximumReplicas)
        .isEquals();
  }

  @Override
  public int compareTo(ClusterStatus o) {
    return clusterName.compareTo(o.clusterName);
  }
}
