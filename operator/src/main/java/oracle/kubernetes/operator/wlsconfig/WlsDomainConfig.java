// Copyright (c) 2017, 2020, Oracle Corporation and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.wlsconfig;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import javax.annotation.Nonnull;

import com.fasterxml.jackson.databind.ObjectMapper;
import oracle.kubernetes.operator.logging.LoggingFacade;
import oracle.kubernetes.operator.logging.LoggingFactory;
import oracle.kubernetes.operator.logging.MessageKeys;
import oracle.kubernetes.weblogic.domain.model.Domain;
import oracle.kubernetes.weblogic.domain.model.WlsDomain;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.apache.commons.lang3.builder.ToStringBuilder;

/** Contains a snapshot of configuration for a WebLogic Domain. */
public class WlsDomainConfig implements WlsDomain {
  private static final LoggingFacade LOGGER = LoggingFactory.getLogger("Operator", "Operator");

  // Name of this WLS domain (This is NOT the domain UID in the weblogic domain kubernetes CRD)
  private String name;

  private String adminServerName;

  // Contains all configured WLS clusters in the WLS domain
  private List<WlsClusterConfig> configuredClusters = new ArrayList<>();
  // Contains all statically configured WLS servers in the WLS domain
  private List<WlsServerConfig> servers = new ArrayList<>();
  // Contains all configured server templates in the WLS domain
  private List<WlsServerConfig> serverTemplates = new ArrayList<>();
  // Contains all configured machines in the WLS domain
  private Map<String, WlsMachineConfig> wlsMachineConfigs = new HashMap<>();

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
   * @param wlsServerTemplates A Map containing server templates configued in this WLS domain
   * @param wlsMachineConfigs A Map containing machines configured in the WLS domain
   */
  public WlsDomainConfig(
      String name,
      String adminServerName,
      Map<String, WlsClusterConfig> wlsClusterConfigs,
      Map<String, WlsServerConfig> wlsServerConfigs,
      Map<String, WlsServerConfig> wlsServerTemplates,
      Map<String, WlsMachineConfig> wlsMachineConfigs) {
    this.configuredClusters = new ArrayList<>(wlsClusterConfigs.values());
    this.servers =
        wlsServerConfigs != null ? new ArrayList<>(wlsServerConfigs.values()) : new ArrayList<>();
    this.serverTemplates =
        wlsServerTemplates != null ? new ArrayList<>(wlsServerTemplates.values()) : null;
    this.wlsMachineConfigs = wlsMachineConfigs;
    this.name = name;
    this.adminServerName = adminServerName;
    // set domainConfig for each WlsClusterConfig
    if (wlsClusterConfigs != null) {
      for (WlsClusterConfig wlsClusterConfig : this.configuredClusters) {
        wlsClusterConfig.setWlsDomainConfig(this);
      }
    }
  }

  /**
   * Create a new WlsDomainConfig object using the json result from the WLS REST call.
   *
   * @param jsonResult A String containing the JSON response from the WLS REST call
   * @return A new WlsDomainConfig object created with information from the JSON response
   */
  public static WlsDomainConfig create(String jsonResult) {
    ParsedJson parsedResult = parseJson(jsonResult);
    return WlsDomainConfig.create(parsedResult);
  }

  /**
   * Create a new WlsDomainConfig object based on the parsed JSON result from WLS admin server.
   *
   * @param parsedResult ParsedJson object containing the parsed JSON result
   * @return A new WlsDomainConfig object based on the provided parsed JSON result
   */
  private static WlsDomainConfig create(ParsedJson parsedResult) {
    if (parsedResult == null) {
      // return empty WlsDomainConfig if no parsedResult is provided
      return new WlsDomainConfig(null);
    }

    final String name = parsedResult.domainName;
    final String adminServerName = parsedResult.adminServerName;
    Map<String, WlsClusterConfig> wlsClusterConfigs = new HashMap<>();
    Map<String, WlsServerConfig> wlsServerConfigs = new HashMap<>();
    Map<String, WlsServerConfig> wlsServerTemplates = new HashMap<>();
    Map<String, WlsMachineConfig> wlsMachineConfigs = new HashMap<>();

    // process list of server templates
    if (parsedResult.serverTemplates != null) {
      for (Map<String, Object> thisServerTemplate : parsedResult.serverTemplates) {
        WlsServerConfig wlsServerTemplate = WlsServerConfig.create(thisServerTemplate);
        wlsServerTemplates.put(wlsServerTemplate.getName(), wlsServerTemplate);
      }
    }
    // process list of clusters (Note: must process server templates before processing clusters)
    if (parsedResult.clusters != null) {
      for (Map<String, Object> clusterConfig : parsedResult.clusters) {
        WlsClusterConfig wlsClusterConfig =
            WlsClusterConfig.create(clusterConfig, wlsServerTemplates, name);
        wlsClusterConfigs.put(wlsClusterConfig.getClusterName(), wlsClusterConfig);
      }
    }
    // process list of statically configured servers
    if (parsedResult.servers != null) {
      for (Map<String, Object> thisServer : parsedResult.servers) {
        WlsServerConfig wlsServerConfig = WlsServerConfig.create(thisServer);
        wlsServerConfigs.put(wlsServerConfig.getName(), wlsServerConfig);
        String clusterName = WlsServerConfig.getClusterNameFromJsonMap(thisServer);
        if (clusterName != null) {
          WlsClusterConfig wlsClusterConfig =
              wlsClusterConfigs.computeIfAbsent(clusterName, WlsClusterConfig::new);
          wlsClusterConfig.addServerConfig(wlsServerConfig);
        }
      }
    }
    // process list of machines
    if (parsedResult.machines != null) {
      for (Map<String, Object> machineConfig : parsedResult.machines) {
        WlsMachineConfig wlsMachineConfig = WlsMachineConfig.create(machineConfig);
        wlsMachineConfigs.put(wlsMachineConfig.getName(), wlsMachineConfig);
      }
    }
    return new WlsDomainConfig(
        name,
        adminServerName,
        wlsClusterConfigs,
        wlsServerConfigs,
        wlsServerTemplates,
        wlsMachineConfigs);
  }

  public static String getRetrieveServersSearchUrl() {
    return "/management/weblogic/latest/domainConfig/search";
  }

  /**
   * JSON payload for retrieve servers REST request.
   * @return payload
   */
  public static String getRetrieveServersSearchPayload() {
    return "{ fields: [ "
        + getSearchFields()
        + " ], "
        + "  links: [], "
        + "  children: { "
        + "    servers: { "
        + WlsServerConfig.getSearchPayload()
        + " }, "
        + "    serverTemplates: { "
        + WlsServerConfig.getSearchPayload()
        + " }, "
        + "    clusters: { "
        + WlsClusterConfig.getSearchPayload()
        + " }, "
        + "    machines: { "
        + WlsMachineConfig.getSearchPayload()
        + " } "
        + "  } "
        + "}";
  }

  private static String getSearchFields() {
    return "'name' ";
  }

  /**
   * Parse the json string containing WLS configuration and return a list containing a map of
   * (server attribute name, attribute value).
   *
   * @param jsonString JSON string containing WLS configuration to be parsed
   * @return a ParsedJson object containing WebLogic domain configuration by parsing the given JSON
   *     string
   */
  @SuppressWarnings("unchecked")
  private static ParsedJson parseJson(String jsonString) {
    ObjectMapper mapper = new ObjectMapper();
    try {
      ParsedJson parsedJson = new ParsedJson();
      Map result = mapper.readValue(jsonString, Map.class);
      parsedJson.domainName = (String) result.get("name");
      parsedJson.adminServerName = (String) result.get("adminServerName");
      Map servers = (Map<String, Object>) result.get("servers");
      if (servers != null) {
        parsedJson.servers = (List<Map<String, Object>>) servers.get("items");
      }
      Map serverTemplates = (Map<String, Object>) result.get("serverTemplates");
      if (serverTemplates != null) {
        parsedJson.serverTemplates = (List<Map<String, Object>>) serverTemplates.get("items");
      }
      Map clusters = (Map<String, Object>) result.get("clusters");
      if (clusters != null) {
        parsedJson.clusters = (List<Map<String, Object>>) clusters.get("items");
      }
      Map machines = (Map<String, Object>) result.get("machines");
      if (machines != null) {
        parsedJson.machines = (List<Map<String, Object>>) machines.get("items");
      }
      return parsedJson;
    } catch (Exception e) {
      LOGGER.warning(MessageKeys.JSON_PARSING_FAILED, jsonString, e.getMessage());
    }
    return null;
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

  public void setConfiguredClusters(List<WlsClusterConfig> configuredClusters) {
    this.configuredClusters = configuredClusters;
  }

  /**
   * Returns configuration of servers found in the WLS domain, including admin server, standalone
   * managed servers that do not belong to any cluster, and statically configured managed servers
   * that belong to a cluster. It does not include dynamic servers configured in dynamic clusters.
   *
   * @return A Map of WlsServerConfig, keyed by name, for each server configured the WLS domain
   */
  public synchronized Map<String, WlsServerConfig> getServerConfigs() {
    Map<String, WlsServerConfig> serverConfigs = new HashMap<>();
    for (WlsServerConfig serverConfig : servers) {
      serverConfigs.put(serverConfig.getName(), serverConfig);
    }
    return serverConfigs;
  }

  public List<WlsServerConfig> getServers() {
    return this.servers;
  }

  public void setServers(List<WlsServerConfig> servers) {
    this.servers = servers;
  }

  public List<WlsServerConfig> getServerTemplates() {
    return this.serverTemplates;
  }

  public void setServerTemplates(List<WlsServerConfig> serverTemplates) {
    this.serverTemplates = serverTemplates;
  }

  /**
   * Returns configuration of machines found in the WLS domain.
   *
   * @return A Map of WlsMachineConfig, keyed by name, for each machine configured the WLS domain
   */
  public synchronized Map<String, WlsMachineConfig> getMachineConfigs() {
    return wlsMachineConfigs;
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
   * Returns the configuration for the WLS machine with the given name.
   *
   * @param machineName name of the WLS machine
   * @return The WlsMachineConfig object containing configuration of the WLS machine with the given
   *     name. This methods return null if no WLS machine is configured with the given name.
   */
  public synchronized WlsMachineConfig getMachineConfig(String machineName) {
    WlsMachineConfig result = null;
    if (machineName != null && wlsMachineConfigs != null) {
      result = wlsMachineConfigs.get(machineName);
    }
    return result;
  }

  public boolean validate(Domain domain) {
    return validate(domain, null);
  }

  /**
   * Checks the provided k8s domain spec to see if it is consistent with the configuration of the
   * WLS domain. The method also logs warning if inconsistent WLS configurations are found.
   *
   * @param domain The Domain to be validated against the WLS configuration
   * @param suggestedConfigUpdates a List of ConfigUpdate objects containing suggested WebLogic
   *     config updates that are necessary to make the WebLogic domain consistent with the
   *     DomainSpec. Optional.
   * @return true if the DomainSpec has been updated, false otherwise
   */
  public boolean validate(Domain domain, List<ConfigUpdate> suggestedConfigUpdates) {
    LOGGER.entering();

    boolean updated = false;
    for (String clusterName : getClusterNames()) {
      WlsClusterConfig wlsClusterConfig = getClusterConfig(clusterName);
      if (wlsClusterConfig.getMaxClusterSize() > 0) {
        int proposedReplicas = domain.getReplicaCount(clusterName);
        int replicaLimit = getReplicaLimit(clusterName);
        if (proposedReplicas > replicaLimit) {
          LOGGER.warning(
              MessageKeys.REPLICA_MORE_THAN_WLS_SERVERS,
              "clusterSpec",
              clusterName,
              proposedReplicas,
              replicaLimit);
        }
      }
    }

    for (WlsClusterConfig clusterConfig : configuredClusters) {
      String clusterName = clusterConfig.getClusterName();
      if (clusterConfig.getMaxClusterSize() == 0) {
        LOGGER.warning(MessageKeys.NO_WLS_SERVER_IN_CLUSTER, clusterName);
      } else {
        clusterConfig.validateCluster(domain.getReplicaCount(clusterName), suggestedConfigUpdates);
      }
    }

    LOGGER.exiting(updated);
    return updated;
  }

  @Override
  @Nonnull
  public String[] getClusterNames() {
    return getClusterConfigs().keySet().toArray(new String[0]);
  }

  @Override
  public int getReplicaLimit(String clusterName) {
    if (!getClusterConfigs().containsKey(clusterName)) {
      return 0;
    }

    return getClusterConfigs().get(clusterName).getMaxClusterSize();
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
        .append("wlsMachineConfigs", wlsMachineConfigs)
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
            .append(serverTemplates)
            .append(wlsMachineConfigs);
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
            .append(serverTemplates, rhs.serverTemplates)
            .append(wlsMachineConfigs, rhs.wlsMachineConfigs);
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

  /**
   * Object used by the {@link #parseJson(String)} method to return multiple parsed objects.
   */
  static class ParsedJson {
    String domainName;
    String adminServerName;
    List<Map<String, Object>> servers;
    List<Map<String, Object>> serverTemplates;
    List<Map<String, Object>> clusters;
    List<Map<String, Object>> machines;
  }
}
