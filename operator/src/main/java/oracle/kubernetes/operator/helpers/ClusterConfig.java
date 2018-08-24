// Copyright 2018, Oracle Corporation and/or its affiliates.  All rights reserved.
// Licensed under the Universal Permissive License v 1.0 as shown at
// http://oss.oracle.com/licenses/upl.

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
  private Map<String, ClusteredServerConfig> servers = new HashMap<>();

  /**
   * Gets cluster's name.
   *
   * @return cluster's name
   */
  public String getClusterName() {
    return clusterName;
  }

  /**
   * Sets the cluster's name.
   *
   * @param clusterName the cluster's name.
   */
  public void setClusterName(String clusterName) {
    this.clusterName = clusterName;
  }

  /**
   * Sets cluster's name.
   *
   * @param clusterName the cluster's name.
   * @return this
   */
  public ClusterConfig withClusterName(String clusterName) {
    this.clusterName = clusterName;
    return this;
  }

  /**
   * Gets the desired number of managed servers running in this cluster.
   *
   * @return replicas
   */
  public int getReplicas() {
    return replicas;
  }

  /**
   * Sets the desired number of managed servers running in this cluster.
   *
   * @param replicas replicas
   */
  public void setReplicas(int replicas) {
    this.replicas = replicas;
  }

  /**
   * Sets the desired number of managed servers running in this cluster.
   *
   * @param replicas replicas
   * @return this
   */
  public ClusterConfig withReplicas(int replicas) {
    this.replicas = replicas;
    return this;
  }

  /**
   * Gets the desired minimum number of managed servers running in this cluster. This is only used
   * when the operator does a rolling restart of the cluster.
   *
   * @return min replicas
   */
  public int getMinReplicas() {
    return minReplicas;
  }

  /**
   * Sets the desired minimum number of managed servers running in this cluster.
   *
   * @param minReplicas min replicas
   */
  public void setMinReplicas(int minReplicas) {
    this.minReplicas = minReplicas;
  }

  /**
   * Sets the desired minimum number of managed servers running in this cluster.
   *
   * @param minReplicas min replicas
   * @return this
   */
  public ClusterConfig withMinReplicas(int minReplicas) {
    this.minReplicas = minReplicas;
    return this;
  }

  /**
   * Gets the desired maximum number of managed servers running in this cluster. This is only used
   * when the operator does a rolling restart of the cluster.
   *
   * @return max replicas
   */
  public int getMaxReplicas() {
    return maxReplicas;
  }

  /**
   * Sets the desired maximum number of managed servers running in this cluster.
   *
   * @param maxReplicas max replicas
   */
  public void setMaxReplicas(int maxReplicas) {
    this.maxReplicas = maxReplicas;
  }

  /**
   * Sets the desired maximum number of managed servers running in this cluster.
   *
   * @param maxReplicas max replicas
   * @return this
   */
  public ClusterConfig withMaxReplicas(int maxReplicas) {
    this.maxReplicas = maxReplicas;
    return this;
  }

  /**
   * Gets the configurations of the servers in this cluster.
   *
   * @return servers
   */
  public Map<String, ClusteredServerConfig> getServers() {
    return servers;
  }

  /**
   * Sets the configurations of the servers in this cluster.
   *
   * @param servers servers
   */
  public void setServers(Map<String, ClusteredServerConfig> servers) {
    this.servers = servers;
  }

  /**
   * Sets the configurations of the servers in this cluster.
   *
   * @param servers servers
   * @return this
   */
  public ClusterConfig withServers(Map<String, ClusteredServerConfig> servers) {
    this.servers = servers;
    return this;
  }

  /**
   * Sets the configuration of a server in this cluster.
   *
   * @param serverName server name
   * @param server server
   */
  public void setServer(String serverName, ClusteredServerConfig server) {
    this.servers.put(serverName, server);
  }

  /**
   * Sets the configuration of a server in this cluster.
   *
   * @param serverName server name
   * @param server server
   * @return this
   */
  public ClusterConfig withServer(String serverName, ClusteredServerConfig server) {
    this.servers.put(serverName, server);
    return this;
  }

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
    if ((other instanceof ClusterConfig) == false) {
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
