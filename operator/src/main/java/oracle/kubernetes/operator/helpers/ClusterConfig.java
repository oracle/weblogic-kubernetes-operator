// Copyright (c) 2018, 2021, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.helpers;

import java.util.HashMap;
import java.util.Map;

import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.apache.commons.lang3.builder.ToStringBuilder;

/** ClusterConfig describes the desired state of a cluster. */
public class ClusterConfig {

  private int replicas;
  private int maxReplicas;
  private int minReplicas;
  private String clusterName;
  private final Map<String, ClusteredServerConfig> servers = new HashMap<>();

  @Override
  public String toString() {
    return new ToStringBuilder(this)
        .append("clusterName", clusterName)
        .append("replicas", replicas)
        .append("minReplicas", minReplicas)
        .append("maxReplicas", maxReplicas)
        .append("servers", servers)
        .toString();
  }

  @Override
  public int hashCode() {
    return new HashCodeBuilder()
        .append(clusterName)
        .append(replicas)
        .append(minReplicas)
        .append(maxReplicas)
        .append(servers)
        .toHashCode();
  }

  @Override
  public boolean equals(Object other) {
    if (other == this) {
      return true;
    }
    if (!(other instanceof ClusterConfig)) {
      return false;
    }
    ClusterConfig rhs = ((ClusterConfig) other);
    return new EqualsBuilder()
        .append(clusterName, rhs.clusterName)
        .append(replicas, rhs.replicas)
        .append(minReplicas, rhs.minReplicas)
        .append(maxReplicas, rhs.maxReplicas)
        .append(servers, rhs.servers)
        .isEquals();
  }
}
