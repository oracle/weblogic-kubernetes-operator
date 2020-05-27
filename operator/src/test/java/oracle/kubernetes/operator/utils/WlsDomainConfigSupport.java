// Copyright (c) 2018, 2020, Oracle Corporation and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.utils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;

import oracle.kubernetes.operator.wlsconfig.WlsClusterConfig;
import oracle.kubernetes.operator.wlsconfig.WlsDomainConfig;
import oracle.kubernetes.operator.wlsconfig.WlsDynamicServersConfig;
import oracle.kubernetes.operator.wlsconfig.WlsMachineConfig;
import oracle.kubernetes.operator.wlsconfig.WlsServerConfig;

public class WlsDomainConfigSupport {
  private final String domain;
  private String adminServerName;
  private final Map<String, WlsClusterConfig> wlsClusters = new HashMap<>();
  private final Map<String, WlsServerConfig> wlsServers = new HashMap<>();
  private final Map<String, WlsServerConfig> templates = new HashMap<>();
  private final Map<String, WlsMachineConfig> machineConfigs = new HashMap<>();

  public WlsDomainConfigSupport(String domain) {
    this.domain = domain;
  }

  private static WlsServerConfig createServerConfig(String serverName, Integer listenPort) {
    return new ServerConfigBuilder(serverName, listenPort).build();
  }

  public WlsDomainConfigSupport withWlsServer(String serverName, Integer listenPort) {
    addWlsServer(serverName, listenPort);
    return this;
  }

  public WlsDomainConfigSupport withWlsCluster(String clusterName, String... serverNames) {
    addWlsCluster(clusterName, serverNames);
    return this;
  }

  public WlsDomainConfigSupport withDynamicWlsCluster(String clusterName, String... serverNames) {
    addDynamicWlsCluster(clusterName, serverNames);
    return this;
  }

  public WlsDomainConfigSupport withAdminServerName(String adminServerName) {
    setAdminServerName(adminServerName);
    return this;
  }

  public void setAdminServerName(String adminServerName) {
    this.adminServerName = adminServerName;
  }

  /**
   * Adds a WLS server to the configuration. Any non-clustered server must be added explicitly.
   * Clustered servers may be added, or may be added simply as part of their cluster.
   *
   * @param serverName the name of the server.
   */
  public void addWlsServer(String serverName) {
    addWlsServer(serverName, null);
  }

  /**
   * Adds a WLS server to the configuration. Any non-clustered server must be added explicitly.
   * Clustered servers may be added, or may be added simply as part of their cluster.
   *
   * @param serverName the name of the server.
   * @param listenPort the listen port
   * @return Server configuration
   */
  public WlsServerConfig addWlsServer(String serverName, Integer listenPort) {
    WlsServerConfig serverConfig = createServerConfig(serverName, listenPort);
    wlsServers.put(serverName, serverConfig);
    return serverConfig;
  }

  /**
   * Returns the configuration for the named server, if any has been defined.
   *
   * @param serverName the name of the server.
   * @return a server configuration, or null.
   */
  public WlsServerConfig getWlsServer(String serverName) {
    return wlsServers.get(serverName);
  }

  /**
   * Returns the configuration for the named clustered server.
   *
   * @param clusterName the name of the cluster containing the server
   * @param serverName the name of the server
   * @return a server configuration, or null
   */
  public WlsServerConfig getWlsServer(String clusterName, String serverName) {
    WlsClusterConfig wlsClusterConfig = this.wlsClusters.get(clusterName);
    if (wlsClusterConfig == null) {
      return null;
    }

    for (WlsServerConfig serverConfig : wlsClusterConfig.getServerConfigs()) {
      if (serverConfig.getName().equals(serverName)) {
        return serverConfig;
      }
    }

    return null;
  }

  /**
   * Adds a WLS cluster to the configuration, including its member servers.
   *
   * @param clusterName the name of the cluster
   * @param serverNames the names of the servers
   */
  public void addWlsCluster(String clusterName, String... serverNames) {
    ClusterConfigBuilder builder = new ClusterConfigBuilder(clusterName);
    for (String serverName : serverNames) {
      builder.addServer(serverName);
    }
    wlsClusters.put(clusterName, builder.build());
  }

  /**
   * Returns the configuration for the named cluster, if any has been defined.
   *
   * @param clusterName the name of the cluster
   * @return a cluster configuration, or null.
   */
  public WlsClusterConfig getWlsCluster(String clusterName) {
    return wlsClusters.get(clusterName);
  }

  /**
   * Adds a dynamic WLS cluster to the configuration, including its member servers.
   *
   * @param clusterName the name of the cluster
   * @param serverNames the names of the servers
   */
  public void addDynamicWlsCluster(String clusterName, String... serverNames) {
    ClusterConfigBuilder builder = new DynamicClusterConfigBuilder(clusterName);
    for (String serverName : serverNames) {
      builder.addServer(serverName);
    }
    wlsClusters.put(clusterName, builder.build());
  }

  /**
   * Creates a domain configuration, based on the defined servers and clusters.
   *
   * @return a domain configuration, or null
   */
  public WlsDomainConfig createDomainConfig() {
    // reconcile static clusters
    for (WlsClusterConfig cluster : wlsClusters.values()) {
      ListIterator<WlsServerConfig> servers = cluster.getServers().listIterator();
      while (servers.hasNext()) {
        WlsServerConfig existing = wlsServers.get(servers.next().getName());
        if (existing != null) {
          servers.set(existing);
        }
      }
    }
    return new WlsDomainConfig(
        domain, adminServerName, wlsClusters, wlsServers, templates, machineConfigs);
  }

  static class ServerConfigBuilder {
    private final String name;
    private final Integer listenPort;
    private Integer adminPort;

    ServerConfigBuilder(String name, Integer listenPort) {
      this.name = name;
      this.listenPort = listenPort;
    }

    ServerConfigBuilder withAdminPort(Integer adminPort) {
      this.adminPort = adminPort;
      return this;
    }

    WlsServerConfig build() {
      return new WlsServerConfig(name, null, null, listenPort, null, adminPort, null);
    }
  }

  static class ClusterConfigBuilder {
    final List<WlsServerConfig> serverConfigs = new ArrayList<>();
    private final String name;

    ClusterConfigBuilder(String name) {
      this.name = name;
    }

    String getName() {
      return name;
    }

    void addServer(String serverName) {
      addServer(serverName, null);
    }

    void addServer(String serverName, Integer listenPort) {
      serverConfigs.add(createServerConfig(serverName, listenPort));
    }

    WlsClusterConfig build() {
      WlsClusterConfig wlsClusterConfig = new WlsClusterConfig(name);
      for (WlsServerConfig serverConfig : serverConfigs) {
        wlsClusterConfig.addServerConfig(serverConfig);
      }
      return wlsClusterConfig;
    }
  }

  static class DynamicClusterConfigBuilder extends ClusterConfigBuilder {
    DynamicClusterConfigBuilder(String name) {
      super(name);
    }

    WlsClusterConfig build() {
      WlsDynamicServersConfig wlsDynamicServersConfig = new WlsDynamicServersConfig();
      wlsDynamicServersConfig.setServerConfigs(serverConfigs);
      wlsDynamicServersConfig.setDynamicClusterSize(serverConfigs.size());
      WlsClusterConfig wlsClusterConfig = new WlsClusterConfig(getName(), wlsDynamicServersConfig);
      return wlsClusterConfig;
    }
  }
}
