// Copyright 2017, 2018, Oracle Corporation and/or its affiliates.  All rights reserved.
// Licensed under the Universal Permissive License v 1.0 as shown at http://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.wlsconfig;

import com.fasterxml.jackson.databind.ObjectMapper;
import oracle.kubernetes.operator.domain.model.oracle.kubernetes.weblogic.domain.v1.ClusterStartup;
import oracle.kubernetes.operator.domain.model.oracle.kubernetes.weblogic.domain.v1.DomainSpec;
import oracle.kubernetes.operator.logging.LoggingFacade;
import oracle.kubernetes.operator.logging.LoggingFactory;
import oracle.kubernetes.operator.logging.MessageKeys;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class WlsDomainConfig {
  private static final LoggingFacade LOGGER = LoggingFactory.getLogger("Operator", "Operator");

  // Contains all configured WLS clusters in the WLS domain
  private Map<String, WlsClusterConfig> wlsClusterConfigs = new HashMap<>();
  // Contains all statically configured WLS servers in the WLS domain
  private Map<String, WlsServerConfig> wlsServerConfigs = new HashMap<>();
  // Contains all configured server templates in the WLS domain
  private Map<String, WlsServerConfig> wlsServerTemplates = new HashMap<>();

  // Name of this WLS domain (This is NOT the domain UID in the weblogic domain kubernetes CRD)
  private final String name;

  /**
   * Create a new WlsDomainConfig object using the json result from the WLS REST call
   *
   * @param jsonResult A String containing the JSON response from the WLS REST call
   *
   * @return A new WlsDomainConfig object created with information from the JSON response
   */
  public static WlsDomainConfig create(String jsonResult) {
    ParsedJson parsedResult = parseJson(jsonResult);
    return WlsDomainConfig.create(parsedResult);
  }

  /**
   * Constructor when no JSON response is available
   *
   * @param name Name of the WLS domain
   */
  public WlsDomainConfig(String name) {
    this.name = name;
  }

  /**
   * Constructor
   *
   * @param name Name of this WLS domain
   * @param wlsClusterConfigs A Map containing clusters configured in this WLS domain
   * @param wlsServerConfigs A Map containing servers configured in the WLS domain
   * @param wlsServerTemplates A Map containing server templates configued in this WLS domain
   */
  WlsDomainConfig(String name, Map<String, WlsClusterConfig> wlsClusterConfigs,
                  Map<String, WlsServerConfig> wlsServerConfigs,
                  Map<String, WlsServerConfig> wlsServerTemplates) {
    this.wlsClusterConfigs = wlsClusterConfigs;
    this.wlsServerConfigs = wlsServerConfigs;
    this.wlsServerTemplates = wlsServerTemplates;
    this.name = name;
  }

  /**
   * Return the name of the WLS domain
   *
   * @return Name of the WLS domain
   */
  public String getName() {
    return name;
  }

  /**
   * Returns all cluster configurations found in the WLS domain
   *
   * @return A Map of WlsClusterConfig, keyed by name, containing server configurations for all clusters found in the WLS domain
   */
  public synchronized Map<String, WlsClusterConfig> getClusterConfigs() {
    return wlsClusterConfigs;
  }

  /**
   * Returns configuration of servers found in the WLS domain, including admin server, standalone managed servers
   * that do not belong to any cluster, and statically configured managed servers that belong to a cluster.
   * It does not include dynamic servers configured in dynamic clusters.
   *
   * @return A Map of WlsServerConfig, keyed by name, for each server configured the WLS domain
   */
  public synchronized Map<String, WlsServerConfig> getServerConfigs() {
    return wlsServerConfigs;
  }

  /**
   * Returns the configuration for the WLS cluster with the given name
   *
   * @param clusterName name of the WLS cluster
   * @return The WlsClusterConfig object containing configuration of the WLS cluster with the given name. This methods
   * return an empty WlsClusterConfig object even if no WLS configuration is found for the given cluster name.
   */
  public synchronized WlsClusterConfig getClusterConfig(String clusterName) {
    WlsClusterConfig result = null;
    if (clusterName != null) {
      result = wlsClusterConfigs.get(clusterName);
    }
    if (result == null) {
      // create an empty WlsClusterConfig, but do not add to wlsClusterConfigs
      result = new WlsClusterConfig(clusterName);
    }
    return result;
  }

  /**
   * Returns the configuration for the WLS server with the given name. Note that this method would not return
   * dynamic server.
   *
   * @param serverName name of the WLS server
   * @return The WlsServerConfig object containing configuration of the WLS server with the given name. This methods
   * return null if no WLS configuration is found for the given server name.
   */
  public synchronized WlsServerConfig getServerConfig(String serverName) {
    WlsServerConfig result = null;
    if (serverName != null) {
      result = wlsServerConfigs.get(serverName);
    }
    return result;
  }

  /**
   * Create a new WlsDomainConfig object based on the parsed JSON result from WLS admin server
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
    Map<String, WlsClusterConfig> wlsClusterConfigs = new HashMap<>();
    Map<String, WlsServerConfig> wlsServerConfigs = new HashMap<>();
    Map<String, WlsServerConfig> wlsServerTemplates = new HashMap<>();

    // process list of server templates
    if (parsedResult.serverTemplates != null) {
      for (Map<String, Object> thisServerTemplate : parsedResult.serverTemplates) {
        WlsServerConfig wlsServerTemplate = WlsServerConfig.create(thisServerTemplate);
        wlsServerTemplates.put(wlsServerTemplate.getName(), wlsServerTemplate);
      }
    }
    // process list of clusters
    if (parsedResult.clusters != null) {
      for (Map<String, Object> clusterConfig : parsedResult.clusters) {
        WlsClusterConfig wlsClusterConfig = WlsClusterConfig.create(clusterConfig, wlsServerTemplates, name);
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
          WlsClusterConfig wlsClusterConfig = wlsClusterConfigs.computeIfAbsent(clusterName, WlsClusterConfig::new);
          wlsClusterConfig.addServerConfig(wlsServerConfig);
        }
      }
    }
    return new WlsDomainConfig(name, wlsClusterConfigs, wlsServerConfigs, wlsServerTemplates);
  }

  public static String getRetrieveServersSearchUrl() {
    return "/management/weblogic/latest/domainConfig/search";
  }

  public static String getRetrieveServersSearchPayload() {
    return "{ fields: [ " + getSearchFields() + " ], " +
            "  links: [], " +
            "  children: { " +
            "    servers: { " + WlsServerConfig.getSearchPayload() + " }, " +
            "    serverTemplates: { " + WlsServerConfig.getSearchPayload() + " }, " +
            "    clusters: { " + WlsClusterConfig.getSearchPayload() + " } " +
            "  } " +
            "}";
  }

  private static String getSearchFields() {
    return "'name' ";
  }

  /**
   * Parse the json string containing WLS configuration and return a list containing a map of (server
   * attribute name, attribute value).
   *
   * @param jsonString JSON string containing WLS configuration to be parsed
   * @return a list containing configuration attributes of each WLS server, each consist of a map of
   * (server attribute name, attribute value)
   */
  @SuppressWarnings("unchecked")
  private static ParsedJson parseJson(String jsonString) {
    ObjectMapper mapper = new ObjectMapper();
    try {
      ParsedJson parsedJson = new ParsedJson();
      Map result = mapper.readValue(jsonString, Map.class);
      parsedJson.domainName = (String) result.get("name");
      Map servers = (Map) result.get("servers");
      if (servers != null) {
        parsedJson.servers = (List<Map<String, Object>>) servers.get("items");
      }
      Map serverTemplates = (Map) result.get("serverTemplates");
      if (serverTemplates != null) {
        parsedJson.serverTemplates = (List<Map<String, Object>>) serverTemplates.get("items");
      }
      Map clusters = (Map) result.get("clusters");
      if (clusters != null) {
        parsedJson.clusters = (List<Map<String, Object>>) clusters.get("items");
      }
      return parsedJson;
    } catch (Exception e) {
      LOGGER.warning(MessageKeys.JSON_PARSING_FAILED, jsonString, e.getMessage());
    }
    return null;
  }

  public boolean validate(DomainSpec domainSpec) {
    return validate(domainSpec, null);
  }

  /**
   * Checks the provided k8s domain spec to see if it is consistent with the configuration of the WLS domain.
   * The method also logs warning if inconsistent WLS configurations are found.
   *
   * @param domainSpec The DomainSpec to be validated against the WLS configuration
   * @param suggestedConfigUpdates a List of ConfigUpdate objects containing suggested WebLogic config updates that
   *                               are necessary to make the WebLogic domain consistent with the DomainSpec. Optional.
   * @return true if the DomainSpec has been updated, false otherwise
   */
  public boolean validate(DomainSpec domainSpec, List<ConfigUpdate> suggestedConfigUpdates) {

    LOGGER.entering();

    boolean updated = false;
    List<ClusterStartup> clusterStartupList = domainSpec.getClusterStartup();

    // check each ClusterStartup if specified in the DomainSpec
    if (clusterStartupList != null) {
      for (ClusterStartup clusterStartup : clusterStartupList) {
        String clusterName = clusterStartup.getClusterName();
        if (clusterName != null) {
          WlsClusterConfig wlsClusterConfig = getClusterConfig(clusterName);
          updated |= wlsClusterConfig.validateClusterStartup(clusterStartup, suggestedConfigUpdates);
        }
      }
    }

    // validate replicas in DomainSpec if specified
    if (domainSpec.getReplicas() != null) {
      Collection<WlsClusterConfig> clusterConfigs = getClusterConfigs().values();
      // WLS domain contains only one cluster
      if (clusterConfigs != null && clusterConfigs.size() == 1) {
        for (WlsClusterConfig wlsClusterConfig : clusterConfigs) {
          wlsClusterConfig.validateReplicas(domainSpec.getReplicas(), "domainSpec",
            suggestedConfigUpdates);
        }
      } else {
        // log info message if replicas is specified but number of WLS clusters in domain is not 1
        LOGGER.info(MessageKeys.DOMAIN_REPLICAS_IGNORED);
      }
    }

    LOGGER.exiting(updated);
    return updated;
  }

  /**
   * Object used by the {@link #parseJson(String)} method to return multiple parsed objects
   */
  static class ParsedJson {
    String domainName;
    List<Map<String, Object>> servers;
    List<Map<String, Object>> serverTemplates;
    List<Map<String, Object>> clusters;
  }

  @Override
  public String toString() {
    return "WlsDomainConfig{" +
            "wlsClusterConfigs=" + wlsClusterConfigs +
            ", wlsServerConfigs=" + wlsServerConfigs +
            ", wlsServerTemplates=" + wlsServerTemplates +
            ", name='" + name + '\'' +
            '}';
  }

}
