// Copyright 2017, 2018, Oracle Corporation and/or its affiliates.  All rights reserved.
// Licensed under the Universal Permissive License v 1.0 as shown at http://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.wlsconfig;

import com.fasterxml.jackson.databind.ObjectMapper;
import oracle.kubernetes.weblogic.domain.v1.ClusterStartup;
import oracle.kubernetes.weblogic.domain.v1.DomainSpec;
import oracle.kubernetes.operator.logging.LoggingFacade;
import oracle.kubernetes.operator.logging.LoggingFactory;
import oracle.kubernetes.operator.logging.MessageKeys;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Contains configuration for a WebLogid Domain
 */
public class WlsDomainConfig {
  private static final LoggingFacade LOGGER = LoggingFactory.getLogger("Operator", "Operator");

  private Map<String, WlsClusterConfig> wlsClusterConfigs = new HashMap<>();
  private Map<String, WlsServerConfig> wlsServerConfigs = new HashMap<>();

  static WlsDomainConfig create() {
    return new WlsDomainConfig();
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
   * Returns configuration of all servers found in the WLS domain, including admin server, standalone managed servers
   * that do not belong to any cluster, and managed servers that belong to a cluster.
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
      result = WlsClusterConfig.create(clusterName);
    }
    return result;
  }

  /**
   * Returns the configuration for the WLS server with the given name
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

  public synchronized WlsDomainConfig load(String jsonResult) {
    List<Map<String, Object>> serversMap = parseJson(jsonResult);
    if (serversMap != null) {
      for (Map<String, Object> thisServer : serversMap) {
        WlsServerConfig wlsServerConfig = new WlsServerConfig(thisServer);
        wlsServerConfigs.put(wlsServerConfig.getName(), wlsServerConfig);
        String clusterName = getClusterNameForServer(thisServer);
        if (clusterName != null) {
          WlsClusterConfig wlsClusterConfig = wlsClusterConfigs.computeIfAbsent(clusterName, WlsClusterConfig::create);
          wlsClusterConfig.addServerConfig(wlsServerConfig);
        }
      }
    }
    return this;
  }

  private String getClusterNameForServer(Map serverMap) {
    // serverMap contains a "cluster" entry from the REST call which is in the form: "cluster": ["clusters", "DockerCluster"]
    List clusterList = (List) serverMap.get("cluster");
    if (clusterList != null) {
      for (Object value : clusterList) {
        // the first entry that is not "clusters" is assumed to be the cluster name
        if (!"clusters".equals(value)) {
          return (String) value;
        }
      }
    }
    return null;
  }

  public static String getRetrieveServersSearchUrl() {
    return "/management/weblogic/latest/domainConfig/search";
  }

  public static String getRetrieveServersSearchPayload() {
    return "{ fields: [], " +
            "  links: [], " +
            "  children: { " +
            "    servers: { " +
            "      fields: [ " + WlsServerConfig.getSearchFields() + " ], " +
            "      links: [], " +
            "      children: { " +
            "        networkAccessPoints: { " +
            "          fields: [ " + NetworkAccessPoint.getSearchFields() + " ], " +
            "          links: [] " +
            "        } " +
            "      } " +
            "    } " +
            "  } " +
            "}";
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
  private List<Map<String, Object>> parseJson(String jsonString) {
    ObjectMapper mapper = new ObjectMapper();
    try {
      Map result = mapper.readValue(jsonString, Map.class);
      Map servers = (Map) result.get("servers");
      if (servers != null) {
        return (List<Map<String, Object>>) servers.get("items");
      }
    } catch (Exception e) {
      LOGGER.warning(MessageKeys.JSON_PARSING_FAILED, jsonString, e.getMessage());
    }
    return null;
  }

  /**
   * Update the provided k8s domain spec to be consistent with the configuration of the WLS domain.
   * The method also logs warning if inconsistent WLS configurations are found that cannot be fixed by updating
   * the provided Domain spec.
   * It is the responsibility of the caller to persist the changes to DomainSpec to kubernetes.
   *
   * @param domainSpec The DomainSpec to be validated against the WLS configuration
   * @return true if the DomainSpec has been updated, false otherwise
   */
  public boolean updateDomainSpecAsNeeded(DomainSpec domainSpec) {

    LOGGER.entering();

    boolean updated = false;
    List<ClusterStartup> clusterStartupList = domainSpec.getClusterStartup();

    // check each ClusterStartup if specified in the DomainSpec
    if (clusterStartupList != null) {
      for (ClusterStartup clusterStartup : clusterStartupList) {
        String clusterName = clusterStartup.getClusterName();
        if (clusterName != null) {
          WlsClusterConfig wlsClusterConfig = getClusterConfig(clusterName);
          updated |= wlsClusterConfig.validateClusterStartup(clusterStartup);
        }
      }
    }

    // validate replicas in DomainSpec if spcified
    if (domainSpec.getReplicas() != null) {
      Collection<WlsClusterConfig> clusterConfigs = getClusterConfigs().values();
      // WLS domain contains only one cluster
      if (clusterConfigs != null && clusterConfigs.size() == 1) {
        for (WlsClusterConfig wlsClusterConfig : clusterConfigs) {
          wlsClusterConfig.validateReplicas(domainSpec.getReplicas(), "domainSpec");
        }
      } else {
        // log info message if replicas is specified but number of WLS clusters in domain is not 1
        LOGGER.info(MessageKeys.DOMAIN_REPLICAS_IGNORED);
      }
    }

    LOGGER.exiting(updated);
    return updated;
  }

  @Override
  public String toString() {
    return "WlsDomainConfig{" +
        "wlsClusterConfigs=" + wlsClusterConfigs +
        '}';
  }
}
