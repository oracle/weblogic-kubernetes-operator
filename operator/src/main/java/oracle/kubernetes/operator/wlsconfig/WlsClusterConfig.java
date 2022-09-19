// Copyright (c) 2017, 2022, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.wlsconfig;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import javax.annotation.Nonnull;

import com.fasterxml.jackson.annotation.JsonIgnore;
import oracle.kubernetes.utils.OperatorUtils;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.apache.commons.lang3.builder.ToStringBuilder;

/** Contains configuration of a WLS cluster. */
public class WlsClusterConfig {

  private String name;
  private final List<WlsServerConfig> servers = new ArrayList<>();
  private WlsDynamicServersConfig dynamicServersConfig;

  // owner -- don't include in toString, hashCode, equals
  @JsonIgnore
  private WlsDomainConfig wlsDomainConfig;

  public WlsClusterConfig() {
  }

  /**
   * Constructor for a static cluster when Json result is not available.
   *
   * @param clusterName Name of the WLS cluster
   */
  public WlsClusterConfig(String clusterName) {
    this.name = clusterName;
    this.dynamicServersConfig = null;
  }

  /**
   * Constructor that can also be used for a dynamic cluster.
   *
   * @param clusterName Name of the WLS cluster
   * @param dynamicServersConfig A WlsDynamicServersConfig object containing the dynamic servers
   *     configuration for this cluster
   */
  public WlsClusterConfig(String clusterName, WlsDynamicServersConfig dynamicServersConfig) {
    this.name = clusterName;
    this.dynamicServersConfig = dynamicServersConfig;
  }

  /**
   * Returns true if one of the servers in the cluster has the specified name.
   *
   * @param serverName the name to look for
   * @return true or false
   */
  public boolean hasNamedServer(String serverName) {
    return getServerConfigs().stream().anyMatch(c -> serverName.equals(c.getName()));
  }

  /**
   * Add a statically configured WLS server to this cluster.
   *
   * @param wlsServerConfig A WlsServerConfig object containing the configuration of the statically
   *     configured WLS server that belongs to this cluster
   * @return Cluster configuration
   */
  public synchronized WlsClusterConfig addServerConfig(WlsServerConfig wlsServerConfig) {
    servers.add(wlsServerConfig);
    return this;
  }

  public WlsClusterConfig addWlsServer(String name, String listenAddress, int port) {
    return addServerConfig(new WlsServerConfig(name, listenAddress, port));
  }

  /**
   * Returns the number of servers that are statically configured in this cluster.
   *
   * @return The number of servers that are statically configured in this cluster
   */
  @JsonIgnore
  public synchronized int getConfiguredClusterSize() {
    return servers.size();
  }

  @JsonIgnore
  public synchronized int getClusterSize() {
    return hasDynamicServers()
        ? getConfiguredClusterSize() + getDynamicClusterSizeOrZero() : getConfiguredClusterSize();
  }

  /**
   * Returns the name of the cluster that this WlsClusterConfig is created for.
   *
   * @return the name of the cluster that this WlsClusterConfig is created for
   */
  @JsonIgnore
  public String getClusterName() {
    return name;
  }

  /**
   * Returns the name of the cluster that this WlsClusterConfig is created for.
   *
   * @return the name of the cluster that this WlsClusterConfig is created for
   */
  public String getName() {
    return getClusterName();
  }

  public WlsDynamicServersConfig getDynamicServersConfig() {
    return this.dynamicServersConfig;
  }

  /**
   * Returns the WlsDomainConfig object for the WLS domain that this cluster belongs to.
   *
   * @return the WlsDomainConfig object for the WLS domain that this cluster belongs to
   */
  public WlsDomainConfig getWlsDomainConfig() {
    return wlsDomainConfig;
  }

  /**
   * Associate this cluster to the WlsDomainConfig object for the WLS domain that this cluster
   * belongs to.
   *
   * @param wlsDomainConfig the WlsDomainConfig object for the WLS domain that this cluster belongs
   *     to
   */
  public void setWlsDomainConfig(WlsDomainConfig wlsDomainConfig) {
    this.wlsDomainConfig = wlsDomainConfig;
  }

  /**
   * Returns a sorted list of server configurations for servers that belong to this cluster,
   * which includes both statically configured servers and dynamic servers.
   *
   * @return A sorted list of WlsServerConfig containing configurations of servers that belong to
   *     this cluster
   */
  public synchronized List<WlsServerConfig> getServerConfigs() {
    int dcsize = dynamicServersConfig == null ? 0 : dynamicServersConfig.getDynamicClusterSize();
    List<WlsServerConfig> result = new ArrayList<>(dcsize + servers.size());
    Optional.ofNullable(dynamicServersConfig).map(WlsDynamicServersConfig::getServerConfigs)
        .ifPresent(result::addAll);
    result.addAll(servers);
    result.sort(Comparator.comparing((WlsServerConfig sc) -> OperatorUtils.getSortingString(sc.getName())));
    return result;
  }

  public List<WlsServerConfig> getServers() {
    return this.servers;
  }

  /**
   * Whether the cluster contains any statically configured servers.
   *
   * @return True if the cluster contains any statically configured servers
   */
  public synchronized boolean hasStaticServers() {
    return !servers.isEmpty();
  }

  /**
   * Whether the cluster contains any dynamic servers.
   *
   * @return True if the cluster contains any dynamic servers
   */
  public boolean hasDynamicServers() {
    return dynamicServersConfig != null;
  }

  /**
   * Returns the current size of the dynamic cluster (the number of dynamic server instances allowed
   * to be created).
   *
   * @return the current size of the dynamic cluster, or -1 if there is no dynamic servers in this cluster
   */
  @JsonIgnore
  public int getDynamicClusterSize() {
    return dynamicServersConfig != null ? dynamicServersConfig.getDynamicClusterSize() : -1;
  }

  /**
   * Returns the size of the dynamic cluster.
   *
   * @return the size of the dynamic cluster, or 0 if there are no dynamic servers in this cluster
   */
  @JsonIgnore
  public int getDynamicClusterSizeOrZero() {
    return Optional.ofNullable(dynamicServersConfig).map(WlsDynamicServersConfig::getDynamicClusterSize).orElse(0);
  }

  /**
   * Whether this cluster contains a server with the given server name,
   * including servers that are both configured and dynamic servers.
   *
   * @param serverName server name to be checked
   * @return True if the cluster contains a server with the given server name
   */
  boolean containsServer(@Nonnull String serverName) {
    return getServerConfigs().stream().anyMatch(c -> serverName.equals(c.getName()));
  }

  @Override
  public String toString() {
    return new ToStringBuilder(this)
        .append("name", name)
        .append("servers", servers)
        .append("dynamicServersConfig", dynamicServersConfig)
        .toString();
  }

  @Override
  public int hashCode() {
    HashCodeBuilder builder =
        new HashCodeBuilder().append(name).append(servers).append(dynamicServersConfig);
    return builder.toHashCode();
  }

  @Override
  public boolean equals(Object other) {
    if (other == this) {
      return true;
    }
    if (!(other instanceof WlsClusterConfig)) {
      return false;
    }

    WlsClusterConfig rhs = ((WlsClusterConfig) other);
    EqualsBuilder builder =
        new EqualsBuilder()
            .append(name, rhs.name)
            .append(servers, rhs.servers)
            .append(dynamicServersConfig, rhs.dynamicServersConfig);
    return builder.isEquals();
  }

}
