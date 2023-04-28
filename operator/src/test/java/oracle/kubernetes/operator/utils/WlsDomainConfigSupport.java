// Copyright (c) 2018, 2022, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.utils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;

import oracle.kubernetes.operator.wlsconfig.NetworkAccessPoint;
import oracle.kubernetes.operator.wlsconfig.WlsClusterConfig;
import oracle.kubernetes.operator.wlsconfig.WlsDomainConfig;
import oracle.kubernetes.operator.wlsconfig.WlsDynamicServersConfig;
import oracle.kubernetes.operator.wlsconfig.WlsServerConfig;

public class WlsDomainConfigSupport {
  public static final Integer DEFAULT_LISTEN_PORT = 7001;
  private final String domain;
  private String adminServerName;
  private final Map<String, WlsClusterConfig> wlsClusters = new HashMap<>();
  private final Map<String, WlsServerConfig> wlsServers = new HashMap<>();
  private final Map<String, WlsServerConfig> templates = new HashMap<>();

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

  public WlsDomainConfigSupport withDynamicWlsCluster(String clusterName, String serverTemplateName, Integer port) {
    addDynamicWlsCluster(clusterName, serverTemplateName, port);
    return this;
  }

  public WlsDomainConfigSupport withAdminServerName(String adminServerName) {
    setAdminServerName(adminServerName);
    return this;
  }

  public void setAdminServerName(String adminServerName) {
    this.adminServerName = adminServerName;
  }


  public WlsServerConfig getAdminServer() {
    return wlsServers.get(adminServerName);
  }

  /**
   * Adds a WLS server to the configuration. Any non-clustered server must be added explicitly.
   * Clustered servers may be added, or may be added simply as part of their cluster.
   *
   * @param serverName the name of the server.
   */
  public void addWlsServer(String serverName) {
    addWlsServer(serverName, DEFAULT_LISTEN_PORT);
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
   * Adds a WLS cluster to the configuration.
   * @param builder a builder for the cluster
   */
  public void addWlsCluster(ClusterConfigBuilder builder) {
    wlsClusters.put(builder.getName(), builder.build());
  }

  /**
   * Adds a WLS cluster to the configuration, including its member servers.
   *
   * @param clusterName the name of the cluster
   * @param serverNames the names of the servers
   */
  public void addWlsCluster(String clusterName, String... serverNames) {
    addWlsCluster(clusterName, DEFAULT_LISTEN_PORT, serverNames);
  }

  /**
   * Adds a WLS cluster to the configuration, including its member servers.
   *
   * @param clusterName the name of the cluster
   * @param port - the port of the servers
   * @param serverNames the names of the servers
   */
  public void addWlsCluster(String clusterName, Integer port, String... serverNames) {
    ClusterConfigBuilder builder = new ClusterConfigBuilder(clusterName);
    for (String serverName : serverNames) {
      builder.addServer(serverName, port);
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
   * Adds a dynamic WLS cluster to the configuration with the server template.
   *
   * @param clusterName the name of the cluster
   * @param serverTemplateName the names of the server template
   * @param port the default listen port
   */
  public void addDynamicWlsCluster(String clusterName, String serverTemplateName, int port) {
    ClusterConfigBuilder builder = new DynamicClusterConfigBuilder(clusterName, serverTemplateName, port);
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
        domain, adminServerName, wlsClusters, wlsServers, templates);
  }

  static class ServerConfigBuilder {
    private final String name;
    private final Integer listenPort;
    private List<NetworkAccessPoint> naps;

    ServerConfigBuilder(String name, Integer listenPort) {
      this.name = name;
      this.listenPort = listenPort;
    }

    public ServerConfigBuilder withNetworkAccessPoints(List<NetworkAccessPoint> naps) {
      this.naps = naps;
      return this;
    }

    WlsServerConfig build() {
      return new WlsServerConfig(name, null, null, listenPort, null, null, null);
    }
  }

  public static class ClusterConfigBuilder {
    final List<WlsServerConfig> serverConfigs = new ArrayList<>();
    private final String name;

    ClusterConfigBuilder(String name) {
      this.name = name;
    }

    String getName() {
      return name;
    }

    public ClusterConfigBuilder withServerNames(String... serverNames) {
      Arrays.stream(serverNames).forEach(this::addServer);
      return this;
    }

    void addServer(String serverName) {
      addServer(serverName, DEFAULT_LISTEN_PORT);
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

  public static class DynamicClusterConfigBuilder extends ClusterConfigBuilder {
    private final WlsServerConfig serverTemplate;
    private Integer minimumClusterSize;
    private Integer maximumClusterSize;

    public DynamicClusterConfigBuilder(String name) {
      this(name, null, 0);
    }

    DynamicClusterConfigBuilder(String name, String serverTemplateName, int port) {
      super(name);
      serverTemplate = new WlsServerConfig(serverTemplateName, null, port);
    }

    /**
     * Updates the builder to include minimum and maximum cluster sizes.
     * @param minimumClusterSize the fewest running servers allowed in this cluster
     * @param maximumClusterSize the most running servers allowed in this cluster
     */
    public DynamicClusterConfigBuilder withClusterLimits(int minimumClusterSize, int maximumClusterSize) {
      this.minimumClusterSize = minimumClusterSize;
      this.maximumClusterSize = maximumClusterSize;
      return this;
    }

    WlsClusterConfig build() {
      WlsDynamicServersConfig wlsDynamicServersConfig = new WlsDynamicServersConfig();
      wlsDynamicServersConfig.setServerConfigs(serverConfigs);
      wlsDynamicServersConfig.setDynamicClusterSize(serverConfigs.size());
      wlsDynamicServersConfig.setServerTemplate(serverTemplate);
      wlsDynamicServersConfig.setMinDynamicClusterSize(minimumClusterSize);
      wlsDynamicServersConfig.setMaxDynamicClusterSize(maximumClusterSize);
      return new WlsClusterConfig(getName(), wlsDynamicServersConfig);
    }
  }
}
