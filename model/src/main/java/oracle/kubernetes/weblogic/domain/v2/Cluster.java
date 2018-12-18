// Copyright 2018, Oracle Corporation and/or its affiliates.  All rights reserved.
// Licensed under the Universal Permissive License v 1.0 as shown at
// http://oss.oracle.com/licenses/upl.

package oracle.kubernetes.weblogic.domain.v2;

import javax.annotation.Nonnull;
import oracle.kubernetes.json.Description;
import oracle.kubernetes.json.Range;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.apache.commons.lang3.builder.ToStringBuilder;

/**
 * An element representing a cluster in the domain configuration.
 *
 * @since 2.0
 */
public class Cluster extends BaseConfiguration {
  /** The name of the cluster. Required. */
  @Description("The name of this cluster. Required")
  @Nonnull
  private String clusterName;

  /** The number of replicas to run in the cluster, if specified. */
  @Description("The number of managed servers to run in this cluster")
  @Range(minimum = 0)
  private Integer replicas;

  @Description(
      "The maximum number of cluster membrers that can be temporarily unavailable. Defaults to 1.")
  @Range(minimum = 1)
  private Integer maxUnavailable;

  public String getClusterName() {
    return clusterName;
  }

  public void setClusterName(@Nonnull String clusterName) {
    this.clusterName = clusterName;
  }

  Cluster withClusterName(@Nonnull String clusterName) {
    setClusterName(clusterName);
    return this;
  }

  public Integer getReplicas() {
    return replicas;
  }

  public void setReplicas(Integer replicas) {
    this.replicas = replicas;
  }

  public Integer getMaxUnavailable() {
    return maxUnavailable;
  }

  public void setMaxUnavailable(Integer maxUnavailable) {
    this.maxUnavailable = maxUnavailable;
  }

  @Override
  public String toString() {
    return new ToStringBuilder(this)
        .appendSuper(super.toString())
        .append("clusterName", clusterName)
        .append("replicas", replicas)
        .append("maxUnavailable", maxUnavailable)
        .toString();
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;

    if (o == null || getClass() != o.getClass()) return false;

    Cluster cluster = (Cluster) o;

    return new EqualsBuilder()
        .appendSuper(super.equals(o))
        .append(clusterName, cluster.clusterName)
        .append(replicas, cluster.replicas)
        .append(maxUnavailable, cluster.maxUnavailable)
        .isEquals();
  }

  @Override
  public int hashCode() {
    return new HashCodeBuilder(17, 37)
        .appendSuper(super.hashCode())
        .append(clusterName)
        .append(replicas)
        .append(maxUnavailable)
        .toHashCode();
  }
}
