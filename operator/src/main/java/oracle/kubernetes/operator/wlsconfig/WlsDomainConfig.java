// Copyright 2017, 2019, Oracle Corporation and/or its affiliates.  All rights reserved.
// Licensed under the Universal Permissive License v 1.0 as shown at
// http://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.wlsconfig;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.annotation.Nonnull;
import oracle.kubernetes.operator.logging.LoggingFacade;
import oracle.kubernetes.operator.logging.LoggingFactory;
import oracle.kubernetes.operator.logging.MessageKeys;
import oracle.kubernetes.weblogic.domain.v2.Domain;
import oracle.kubernetes.weblogic.domain.v2.WlsDomain;

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

  public WlsDomainConfig() {}

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

  public String getClusterName(String serverName) {
    return getConfiguredClusters()
        .stream()
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
   * Return the name of the WLS domain.
   *
   * @return Name of the WLS domain
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

    String name = parsedResult.domainName;
    String adminServerName = parsedResult.adminServerName;
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

  /** Object used by the {@link #parseJson(String)} method to return multiple parsed objects. */
  static class ParsedJson {
    String domainName;
    String adminServerName;
    List<Map<String, Object>> servers;
    List<Map<String, Object>> serverTemplates;
    List<Map<String, Object>> clusters;
    List<Map<String, Object>> machines;
  }

  @Override
  public String toString() {
    return "WlsDomainConfig{"
        + "name='"
        + name
        + '\''
        + ", adminServerName='"
        + adminServerName
        + '\''
        + ", configuredClusters="
        + configuredClusters
        + ", servers="
        + servers
        + ", serverTemplates="
        + serverTemplates
        + ", wlsMachineConfigs="
        + wlsMachineConfigs
        + '}';
  }

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
