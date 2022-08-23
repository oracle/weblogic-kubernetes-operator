// Copyright (c) 2017, 2022, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.wlsconfig;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.annotation.Nonnull;

import com.fasterxml.jackson.annotation.JsonIgnore;
import oracle.kubernetes.common.logging.MessageKeys;
import oracle.kubernetes.operator.logging.LoggingFacade;
import oracle.kubernetes.operator.logging.LoggingFactory;
import oracle.kubernetes.weblogic.domain.model.WlsDomain;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.apache.commons.lang3.builder.ToStringBuilder;

import static oracle.kubernetes.utils.OperatorUtils.isNullOrEmpty;

/** Contains a snapshot of configuration for a WebLogic Domain. */
public class WlsDomainConfig implements WlsDomain {
  private static final LoggingFacade LOGGER = LoggingFactory.getLogger("Operator", "Operator");

  // Name of this WLS domain (This is NOT the domain UID in the WebLogic domain kubernetes CRD)
  private String name;

  private String adminServerName;

  // Contains all configured WLS clusters in the WLS domain
  private List<WlsClusterConfig> configuredClusters = new ArrayList<>();
  // Contains all statically configured WLS servers in the WLS domain
  private List<WlsServerConfig> servers = new ArrayList<>();
  // Contains all configured server templates in the WLS domain
  private List<WlsServerConfig> serverTemplates = new ArrayList<>();

  public WlsDomainConfig() {
  }

  /**
   * Constructor when no JSON response is available.
   *
   * @param name Name of the WLS domain
   */
  public WlsDomainConfig(String name) {
    this.name = name;
  }

  /**
   * Constructor.
   *
   * @param name Name of this WLS domain
   * @param adminServerName Name of the admin server in this WLS domain
   * @param wlsClusterConfigs A Map containing clusters configured in this WLS domain
   * @param wlsServerConfigs A Map containing servers configured in the WLS domain
   * @param wlsServerTemplates A Map containing server templates configured in this WLS domain
   */
  public WlsDomainConfig(
      String name,
      String adminServerName,
      Map<String, WlsClusterConfig> wlsClusterConfigs,
      Map<String, WlsServerConfig> wlsServerConfigs,
      Map<String, WlsServerConfig> wlsServerTemplates) {
    this.configuredClusters = new ArrayList<>(wlsClusterConfigs.values());
    this.servers =
        wlsServerConfigs != null ? new ArrayList<>(wlsServerConfigs.values()) : new ArrayList<>();
    this.serverTemplates =
        wlsServerTemplates != null ? new ArrayList<>(wlsServerTemplates.values()) : null;
    this.name = name;
    this.adminServerName = adminServerName;
    // set domainConfig for each WlsClusterConfig
    for (WlsClusterConfig wlsClusterConfig : this.configuredClusters) {
      wlsClusterConfig.setWlsDomainConfig(this);
    }
  }

  /**
   * Get cluster name.
   * @param serverName server name
   * @return cluster name
   */
  public String getClusterName(String serverName) {
    return getConfiguredClusters().stream()
        .filter(c -> c.hasNamedServer(serverName))
        .findFirst()
        .map(WlsClusterConfig::getClusterName)
        .orElse(null);
  }

  /**
   * Return the name of the WLS domain.
   *
   * @return Name of the WLS domain
   */
  public String getName() {
    return name;
  }

  /**
   * Return the name of the admin server.
   *
   * @return Name of the admin server
   */
  public String getAdminServerName() {
    return this.adminServerName;
  }

  public void setAdminServerName(String adminServerName) {
    this.adminServerName = adminServerName;
  }

  /**
   * Returns all cluster configurations found in the WLS domain.
   *
   * @return A Map of WlsClusterConfig, keyed by name, containing server configurations for all
   *     clusters found in the WLS domain
   */
  public synchronized Map<String, WlsClusterConfig> getClusterConfigs() {
    Map<String, WlsClusterConfig> clusterConfigs = new HashMap<>();
    for (WlsClusterConfig clusterConfig : configuredClusters) {
      clusterConfigs.put(clusterConfig.getClusterName(), clusterConfig);
    }
    return clusterConfigs;
  }

  public List<WlsClusterConfig> getConfiguredClusters() {
    return this.configuredClusters;
  }

  /**
   * Returns a map of server names found in the WLS domain to their configurations, including the admin server
   * and standalone managed servers that do not belong to any cluster. It does not include servers in clusters.
   *
   * @return A Map of WlsServerConfig, keyed by name, for each server statically configured the WLS domain
   */
  public synchronized Map<String, WlsServerConfig> getServerConfigs() {
    Map<String, WlsServerConfig> serverConfigs = new HashMap<>();
    for (WlsServerConfig serverConfig : servers) {
      serverConfigs.put(serverConfig.getName(), serverConfig);
    }
    return serverConfigs;
  }

  /**
   * Returns a list of server configurations found in the WLS domain, including the admin server
   * and standalone managed servers that do not belong to any cluster. It does not include servers in clusters.
   *
   * @return A List of WlsServerConfig for each server statically configured the WLS domain
   */
  public List<WlsServerConfig> getServers() {
    return this.servers;
  }

  /**
   * Returns a list of all the server configurations found in the WLS domain, including the admin server
   * and standalone managed servers, plus servers in clusters.
   *
   * @return A List of WlsServerConfig for each server statically configured the WLS domain
   */
  public List<WlsServerConfig> getAllServers() {
    return Stream.concat(
          servers.stream(),
          configuredClusters.stream().flatMap(c -> c.getServerConfigs().stream()))
      .collect(Collectors.toList());
  }

  public List<WlsServerConfig> getServerTemplates() {
    return this.serverTemplates;
  }

  /**
   * Returns the configuration for the WLS cluster with the given name.
   *
   * @param clusterName name of the WLS cluster
   * @return The WlsClusterConfig object containing configuration of the WLS cluster with the given
   *     name. This methods return an empty WlsClusterConfig object even if no WLS configuration is
   *     found for the given cluster name.
   */
  public synchronized WlsClusterConfig getClusterConfig(String clusterName) {
    WlsClusterConfig result = null;
    if (clusterName != null) {
      for (WlsClusterConfig clusterConfig : configuredClusters) {
        if (clusterConfig.getClusterName().equals(clusterName)) {
          result = clusterConfig;
          break;
        }
      }
    }
    if (result == null) {
      // create an empty WlsClusterConfig, but do not add to configuredClusters
      result = new WlsClusterConfig(clusterName);
    }
    return result;
  }

  /**
   * Returns the configuration for the WLS server with the given name. Note that this method would
   * not return dynamic server.
   *
   * @param serverName name of the WLS server
   * @return The WlsServerConfig object containing configuration of the WLS server with the given
   *     name. This methods return null if no WLS configuration is found for the given server name.
   */
  public synchronized WlsServerConfig getServerConfig(String serverName) {
    WlsServerConfig result = null;
    if (serverName != null && servers != null) {
      for (WlsServerConfig serverConfig : servers) {
        if (serverConfig.getName().equals(serverName)) {
          result = serverConfig;
          break;
        }
      }
    }
    return result;
  }

  /**
   * Whether the WebLogic domain contains a cluster with the given cluster name.
   *
   * @param clusterName cluster name to be checked
   * @return True if the WebLogic domain contains a cluster with the given cluster name
   */
  public synchronized boolean containsCluster(String clusterName) {
    if (clusterName != null) {
      for (WlsClusterConfig clusterConfig : configuredClusters) {
        if (clusterConfig.getClusterName().equals(clusterName)) {
          return true;
        }
      }
    }

    return false;
  }

  /**
   * Whether the WebLogic domain contains a server with the given server name,
   * including standalone servers, and servers that belong to a configured or dynamic cluster.
   *
   * @param serverName server name to be checked
   * @return True if the WebLogic domain contains a server with the given server name
   */
  public synchronized boolean containsServer(String serverName) {
    if (!isNullOrEmpty(serverName)) {
      return getServers().stream().anyMatch(s -> serverName.equals(s.getName()))
          || getConfiguredClusters().stream().anyMatch(c -> c.containsServer(serverName));
    }
    return false;
  }

  @JsonIgnore
  @Nonnull
  public String[] getClusterNames() {
    return getClusterConfigs().keySet().toArray(new String[0]);
  }

  @Override
  public int getReplicaLimit(String clusterName) {
    if (!getClusterConfigs().containsKey(clusterName)) {
      return 0;
    }

    return getClusterConfigs().get(clusterName).getClusterSize();
  }

  /**
   * Build with admin server.
   * @param adminServerName admin server name
   * @param listenAddress listen address
   * @param port port
   * @return domain config
   */
  public WlsDomainConfig withAdminServer(String adminServerName, String listenAddress, int port) {
    setAdminServerName(adminServerName);
    addWlsServer(adminServerName, listenAddress, port);
    return this;
  }

  /**
   * Build the domain config with a WLS server.
   * @param server WLS server configuration
   * @param isAdmin Specifies if the server is Admin or managed server.
   * @return domain config
   */
  public WlsDomainConfig withServer(WlsServerConfig server, boolean isAdmin) {
    if (isAdmin) {
      setAdminServerName(server.getName());
    }
    getServers().add(server);
    return this;
  }

  public WlsDomainConfig addWlsServer(String name, String listenAddress, int port) {
    getServers().add(new WlsServerConfig(name, listenAddress, port));
    return this;
  }

  public WlsDomainConfig withCluster(WlsClusterConfig clusterConfig) {
    configuredClusters.add(clusterConfig);
    return this;
  }

  /**
   * Returns the topology equivalent of the domain configuration, as a map. It may be converted to
   * YAML or JSON via an object mapper.
   *
   * @return a map containing the topology
   */
  public Map<String, Object> toTopology() {
    Map<String, Object> topology = new HashMap<>();
    topology.put("domainValid", "true");
    topology.put("domain", createDomainTopology());
    return topology;
  }

  private Map<String, Object> createDomainTopology() {
    Map<String, Object> domainTopology = new HashMap<>();
    domainTopology.put("name", name);
    domainTopology.put("adminServerName", adminServerName);
    domainTopology.put("configuredClusters", createClustersList());
    domainTopology.put("servers", createServersList(servers));
    return domainTopology;
  }

  private List<Map<String, Object>> createClustersList() {
    return configuredClusters.stream().map(this::createTopology).collect(Collectors.toList());
  }

  private Map<String, Object> createTopology(WlsClusterConfig cluster) {
    Map<String, Object> topology = new HashMap<>();
    topology.put("name", cluster.getName());
    topology.put("servers", createServersList(cluster.getServers()));
    return topology;
  }

  private Map<String, Object> createTopology(WlsServerConfig server) {
    Map<String, Object> topology = new HashMap<>();
    topology.put("name", server.getName());
    topology.put("listenAddress", server.getListenAddress());
    topology.put("listenPort", server.getListenPort());
    return topology;
  }

  private List<Map<String, Object>> createServersList(List<WlsServerConfig> servers) {
    return servers.stream().map(this::createTopology).collect(Collectors.toList());
  }

  @Override
  public String toString() {
    return new ToStringBuilder(this)
        .append("name", name)
        .append("adminServerName", adminServerName)
        .append("configuredClusters", configuredClusters)
        .append("servers", servers)
        .append("serverTemplates", serverTemplates)
        .toString();
  }

  @Override
  public int hashCode() {
    HashCodeBuilder builder =
        new HashCodeBuilder()
            .append(name)
            .append(adminServerName)
            .append(configuredClusters)
            .append(servers)
            .append(serverTemplates);
    return builder.toHashCode();
  }

  @Override
  public boolean equals(Object other) {
    if (other == this) {
      return true;
    }
    if (!(other instanceof WlsDomainConfig)) {
      return false;
    }

    WlsDomainConfig rhs = ((WlsDomainConfig) other);
    EqualsBuilder builder =
        new EqualsBuilder()
            .append(name, rhs.name)
            .append(adminServerName, rhs.adminServerName)
            .append(configuredClusters, rhs.configuredClusters)
            .append(servers, rhs.servers)
            .append(serverTemplates, rhs.serverTemplates);
    return builder.isEquals();
  }

  /**
   * Process dynamic clusters.
   */
  public void processDynamicClusters() {
    for (WlsClusterConfig wlsClusterConfig : configuredClusters) {
      wlsClusterConfig.setWlsDomainConfig(this);
      if (wlsClusterConfig.hasDynamicServers()) {
        WlsDynamicServersConfig wlsDynamicServersConfig =
            wlsClusterConfig.getDynamicServersConfig();
        String serverTemplateName =
            wlsClusterConfig.getDynamicServersConfig().getServerTemplateName();
        WlsServerConfig serverTemplate = getServerTemplate(serverTemplateName);
        String clusterName = wlsClusterConfig.getClusterName();
        if (serverTemplate != null) {
          wlsDynamicServersConfig.generateDynamicServerConfigs(
              serverTemplate, clusterName, getName());
        } else {
          LOGGER.warning(
              MessageKeys.WLS_SERVER_TEMPLATE_NOT_FOUND, serverTemplateName, clusterName);
        }
      }
    }
  }

  WlsServerConfig getServerTemplate(String serverTemplateName) {
    for (WlsServerConfig serverTemplate : serverTemplates) {
      if (serverTemplate.getName().equals(serverTemplateName)) {
        return serverTemplate;
      }
    }
    return null;
  }
}
