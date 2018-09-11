// Copyright 2018, Oracle Corporation and/or its affiliates.  All rights reserved.
// Licensed under the Universal Permissive License v 1.0 as shown at
// http://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.helpers;

import java.util.HashMap;
import java.util.Map;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.apache.commons.lang3.builder.ToStringBuilder;

/** DomainConfig describes the desired state of a domain. */
public class DomainConfig {

  private Map<String, NonClusteredServerConfig> servers = new HashMap<>();
  private Map<String, ClusterConfig> clusters = new HashMap<>();

  /**
   * Gets the configurations of the non-clustered servers in this domain.
   *
   * @return servers
   */
  public Map<String, NonClusteredServerConfig> getServers() {
    return servers;
  }

  /**
   * Sets the configurations of the non-clustered servers in this domain.
   *
   * @param servers servers
   */
  public void setServers(Map<String, NonClusteredServerConfig> servers) {
    this.servers = servers;
  }

  /**
   * Sets the configurations of the non-clustered servers in this domain.
   *
   * @param servers servers
   * @return this
   */
  public DomainConfig withServers(Map<String, NonClusteredServerConfig> servers) {
    this.servers = servers;
    return this;
  }

  /**
   * Sets the configuration of a non-clustered server in this domain.
   *
   * @param serverName server name
   * @param server server
   */
  public void setServer(String serverName, NonClusteredServerConfig server) {
    this.servers.put(serverName, server);
  }

  /**
   * Sets the configuration of a non-clustered server in this domain.
   *
   * @param serverName server name
   * @param server server
   * @return this
   */
  public DomainConfig withServer(String serverName, NonClusteredServerConfig server) {
    this.servers.put(serverName, server);
    return this;
  }

  /**
   * Gets the configurations of the clusters in this domain.
   *
   * @return clusters
   */
  public Map<String, ClusterConfig> getClusters() {
    return clusters;
  }

  /**
   * Sets the configurations of the clusters in this domain.
   *
   * @param clusters clusters
   */
  public void setClusters(Map<String, ClusterConfig> clusters) {
    this.clusters = clusters;
  }

  /**
   * Sets the configurations of the clusters in this domain.
   *
   * @param clusters clusters
   * @return this
   */
  public DomainConfig withClusters(Map<String, ClusterConfig> clusters) {
    this.clusters = clusters;
    return this;
  }

  /**
   * Sets the configuration of a cluster in this domain.
   *
   * @param clusterName cluster name
   * @param cluster cluster
   */
  public void setCluster(String clusterName, ClusterConfig cluster) {
    this.clusters.put(clusterName, cluster);
  }

  /**
   * Sets the configuration of a cluster in this domain.
   *
   * @param clusterName cluster name
   * @param cluster cluster
   * @return this
   */
  public DomainConfig withCluster(String clusterName, ClusterConfig cluster) {
    this.clusters.put(clusterName, cluster);
    return this;
  }

  @Override
  public String toString() {
    return new ToStringBuilder(this)
        .append("servers", servers)
        .append("clusters", clusters)
        .toString();
  }

  @Override
  public int hashCode() {
    return new HashCodeBuilder().append(servers).append(clusters).toHashCode();
  }

  @Override
  public boolean equals(Object other) {
    if (other == this) {
      return true;
    }
    if ((other instanceof DomainConfig) == false) {
      return false;
    }
    DomainConfig rhs = ((DomainConfig) other);
    return new EqualsBuilder()
        .append(servers, rhs.servers)
        .append(clusters, rhs.clusters)
        .isEquals();
  }
}
