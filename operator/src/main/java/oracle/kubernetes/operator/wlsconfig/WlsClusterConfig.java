// Copyright (c) 2017, 2020, Oracle Corporation and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.wlsconfig;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.apache.commons.lang3.builder.ToStringBuilder;

/** Contains configuration of a WLS cluster. */
public class WlsClusterConfig {

  private String name;
  private List<WlsServerConfig> servers = new ArrayList<>();
  private WlsDynamicServersConfig dynamicServersConfig;

  // owner -- don't include in toString, hashCode, equals
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
   * Creates a WlsClusterConfig object using an "clusters" item parsed from JSON result from WLS
   * REST call.
   *
   * @param clusterConfigMap Map containing "cluster" item parsed from JSON result from WLS REST
   *     call
   * @param serverTemplates Map containing all server templates configuration read from the WLS
   *     domain
   * @param domainName Name of the WLS domain that this WLS cluster belongs to
   * @return A new WlsClusterConfig object created based on the JSON result
   */
  @SuppressWarnings("unchecked")
  static WlsClusterConfig create(
      Map<String, Object> clusterConfigMap,
      Map<String, WlsServerConfig> serverTemplates,
      String domainName) {
    String clusterName = (String) clusterConfigMap.get("name");
    WlsDynamicServersConfig dynamicServersConfig =
        WlsDynamicServersConfig.create(
            (Map<String, Object>) clusterConfigMap.get("dynamicServers"),
            serverTemplates,
            clusterName,
            domainName);
    // set dynamicServersConfig only if the cluster contains dynamic servers, i.e., its dynamic
    // servers configuration
    // contains non-null server template name
    if (dynamicServersConfig.getServerTemplate() == null) {
      dynamicServersConfig = null;
    }
    return new WlsClusterConfig(clusterName, dynamicServersConfig);
  }

  /**
   * Return the list of configuration attributes to be retrieved from the REST search request to the
   * WLS admin server. The value would be used for constructing the REST POST request.
   *
   * @return The list of configuration attributes to be retrieved from the REST search request to
   *     the WLS admin server. The value would be used for constructing the REST POST request.
   */
  static String getSearchPayload() {
    return "   fields: [ "
        + getSearchFields()
        + " ], "
        + "   links: [], "
        + "   children: { "
        + "      dynamicServers: { "
        + "      fields: [ "
        + WlsDynamicServersConfig.getSearchFields()
        + " ], "
        + "      links: [] "
        + "        }"
        + "    } ";
  }

  /**
   * Return the fields from cluster WLS configuration that should be retrieved from the WLS REST
   * request.
   *
   * @return A string containing cluster configuration fields that should be retrieved from the WLS
   *     REST request, in a format that can be used in the REST request payload
   */
  private static String getSearchFields() {
    return "'name' ";
  }

  /**
   * Checks the JSON result from the dynamic cluster size update REST request.
   *
   * @param jsonResult The JSON String result from the dynamic server cluster size update REST
   *     request
   * @return true if the result means the update was successful, false otherwise
   */
  static boolean checkUpdateDynamicClusterSizeJsonResult(String jsonResult) {
    final String expectedResult = "{}";

    boolean result = false;
    if (expectedResult.equals(jsonResult)) {
      result = true;
    }
    return result;
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

  /**
   * Returns the number of servers that are statically configured in this cluster.
   *
   * @return The number of servers that are statically configured in this cluster
   */
  public synchronized int getClusterSize() {
    return servers.size();
  }

  public synchronized int getMaxClusterSize() {
    return hasDynamicServers() ? getClusterSize() + getMaxDynamicClusterSize() : getClusterSize();
  }

  /**
   * Returns the name of the cluster that this WlsClusterConfig is created for.
   *
   * @return the name of the cluster that this WlsClusterConfig is created for
   */
  public String getClusterName() {
    return name;
  }

  /**
   * Returns the name of the cluster that this WlsClusterConfig is created for.
   *
   * @return the name of the cluster that this WlsClusterConfig is created for
   */
  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public WlsDynamicServersConfig getDynamicServersConfig() {
    return this.dynamicServersConfig;
  }

  public void setDynamicServersConfig(WlsDynamicServersConfig dynamicServersConfig) {
    this.dynamicServersConfig = dynamicServersConfig;
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
   * Returns a list of server configurations for servers that belong to this cluster, which includes
   * both statically configured servers and dynamic servers.
   *
   * @return A list of WlsServerConfig containing configurations of servers that belong to this
   *     cluster
   */
  public synchronized List<WlsServerConfig> getServerConfigs() {
    if (dynamicServersConfig != null) {
      List<WlsServerConfig> result =
          new ArrayList<>(dynamicServersConfig.getDynamicClusterSize() + servers.size());
      result.addAll(dynamicServersConfig.getServerConfigs());
      result.addAll(servers);
      return result;
    }
    return servers;
  }

  public List<WlsServerConfig> getServers() {
    return this.servers;
  }

  public void setServers(List<WlsServerConfig> servers) {
    this.servers = servers;
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
   * @return the current size of the dynamic cluster, or -1 if there is no dynamic servers in this
   *     cluster
   */
  public int getDynamicClusterSize() {
    return dynamicServersConfig != null ? dynamicServersConfig.getDynamicClusterSize() : -1;
  }

  /**
   * Returns the maximum size of the dynamic cluster.
   *
   * @return the maximum size of the dynamic cluster, or -1 if there is no dynamic servers in this
   *     cluster
   */
  public int getMaxDynamicClusterSize() {
    return dynamicServersConfig != null ? dynamicServersConfig.getMaxDynamicClusterSize() : -1;
  }

  /**
   * Verify whether the WebLogic domain already has all the machines configured for use by the
   * dynamic cluster. For example, if machineNamePrefix is "domain1-cluster1-machine" and
   * numMachinesNeeded is 2, this method return true if machines named "domain1-cluster1-machine1"
   * and "domain1-cluster1-machine2" are configured in the WebLogic domain.
   *
   * @param machineNamePrefix Prefix of the names of the machines
   * @param numMachinesNeeded Number of machines needed for this dynamic cluster
   * @return True if the WebLogic domain already has all the machines configured, or if there is no
   *     WlsDomainConfig object associated with this cluster, in which case we cannot perform the
   *     verification, or if machineNamePrefix is null, false otherwise
   */
  boolean verifyMachinesConfigured(String machineNamePrefix, int numMachinesNeeded) {
    if (wlsDomainConfig != null && machineNamePrefix != null) {
      for (int suffix = 1; suffix <= numMachinesNeeded; suffix++) {
        if (wlsDomainConfig.getMachineConfig(machineNamePrefix + suffix) == null) {
          return false;
        }
      }
    }
    return true;
  }

  /**
   * Finds the names of a machine to be created for all dynamic servers in this dynamic cluster.
   *
   * @param machineNamePrefix Prefix for the new machine names (should match
   *     machineNameMatchExpression in dynamic servers config)
   * @param targetClusterSize the target dynamic cluster size
   * @return A String array containing names of new machines to be created in the WebLogic domain
   *     for use by dynamic servers in this cluster
   */
  String[] getMachineNamesForDynamicServers(String machineNamePrefix, int targetClusterSize) {
    if (targetClusterSize < 1 || !hasDynamicServers() || wlsDomainConfig == null) {
      return new String[0];
    }
    // machine names needed are [machineNamePrefix] appended by id of the dynamic servers
    // for example, if prefix is "domain1-cluster1-machine" and targetClusterSize is 3, and machine
    // with name
    // "domain1-cluster1-machine1" already exists, the names of machines to be created should be
    // {"domain1-cluster1-machine2", "domain1-cluster1-machine3"}
    ArrayList<String> names = new ArrayList<>();
    for (int suffix = 1; suffix <= targetClusterSize; suffix++) {
      String newMachineName = machineNamePrefix == null ? "" + suffix : machineNamePrefix + suffix;
      if (wlsDomainConfig.getMachineConfig(newMachineName) == null) {
        // only need to create machine if it does not already exist
        names.add(newMachineName);
      }
    }
    String[] machineNameArray = new String[names.size()];
    names.toArray(machineNameArray);
    return machineNameArray;
  }

  /**
   * Return the URL path of REST request for updating dynamic cluster size.
   *
   * @return The REST URL path for updating cluster size of dynamic servers for this cluster
   */
  public String getUpdateDynamicClusterSizeUrl() {
    return "/management/weblogic/latest/edit/clusters/" + name + "/dynamicServers";
  }

  /**
   * Return the payload used in the REST request for updating the dynamic cluster size. It will be
   * used to update the cluster size of the dynamic servers of this cluster.
   *
   * @param clusterSize Desired dynamic cluster size
   * @return A string containing the payload to be used in the REST request for updating the dynamic
   *     cluster size to the specified value.
   */
  public String getUpdateDynamicClusterSizePayload(final int clusterSize) {
    return "{ dynamicClusterSize: " + clusterSize + " }";
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
