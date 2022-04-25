// Copyright (c) 2018, 2021, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.wlsconfig;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.apache.commons.lang3.builder.ToStringBuilder;

/**
 * Contains values from a WLS dynamic servers configuration, which configures a WLS dynamic cluster.
 */
public class WlsDynamicServersConfig {

  String name;
  String serverTemplateName;
  Integer dynamicClusterSize;
  Integer maxDynamicClusterSize;
  Integer minDynamicClusterSize;
  String serverNamePrefix;
  boolean calculatedListenPorts;

  WlsServerConfig serverTemplate;
  String machineNameMatchExpression;
  List<WlsServerConfig> serverConfigs;

  public WlsDynamicServersConfig() {
  }

  /**
   * Constructor.
   *
   * @param dynamicClusterSize current size of the dynamic cluster
   * @param maxDynamicClusterSize maximum size of the dynamic cluster
   * @param minDynamicClusterSize minimum size of the dynamic cluster
   * @param serverNamePrefix prefix for names of servers in this dynamic cluster
   * @param calculatedListenPorts whether listen ports are fixed or calculated based on server index
   * @param machineNameMatchExpression the expression is used when determining machines to use for
   *     server assignments
   * @param serverTemplate template of servers in the dynamic cluster
   * @param serverConfigs List of WlsServerConfig containing configurations of dynamic servers that
   *     corresponds to the current cluster size
   */
  public WlsDynamicServersConfig(
      Integer dynamicClusterSize,
      Integer maxDynamicClusterSize,
      Integer minDynamicClusterSize,
      String serverNamePrefix,
      boolean calculatedListenPorts,
      String machineNameMatchExpression,
      WlsServerConfig serverTemplate,
      List<WlsServerConfig> serverConfigs) {
    this.dynamicClusterSize = dynamicClusterSize;
    this.maxDynamicClusterSize = maxDynamicClusterSize;
    this.minDynamicClusterSize = minDynamicClusterSize;
    this.serverNamePrefix = serverNamePrefix;
    this.calculatedListenPorts = calculatedListenPorts;
    this.machineNameMatchExpression = machineNameMatchExpression;
    this.serverTemplate = serverTemplate;
    this.serverConfigs = serverConfigs;
  }

  /**
   * Create a list of WlsServerConfig objects for dynamic servers that corresponds to the current
   * cluster size.
   *
   * @param serverNames Names of the servers corresponding to the current cluster size
   * @param serverTemplate WlsServerConfig object containing template used for creating dynamic
   *     servers in this cluster
   * @param clusterName Name of the WLS cluster that this dynamic servers configuration belongs to
   * @param domainName Name of the WLS domain that this WLS cluster belongs to
   * @param calculatedListenPorts whether listen ports are fixed or calculated based on server index
   * @return A list of WlsServerConfig objects for dynamic servers
   */
  static List<WlsServerConfig> createServerConfigsFromTemplate(
      List<String> serverNames,
      WlsServerConfig serverTemplate,
      String clusterName,
      String domainName,
      boolean calculatedListenPorts) {
    List<WlsServerConfig> serverConfigs = null;
    if (serverNames != null && !serverNames.isEmpty()) {
      serverConfigs = new ArrayList<>(serverNames.size());
      int index = 0;
      int startingServerIndex =
          1; // hard coded to 1 for the time being. This will be configurable in later version of
      // WLS
      for (String serverName : serverNames) {
        serverConfigs.add(
            WlsDynamicServerConfig.create(
                serverName,
                index + startingServerIndex,
                clusterName,
                domainName,
                calculatedListenPorts,
                serverTemplate));
        index++;
      }
    }
    return serverConfigs;
  }

  /**
   * Return current size of the dynamic cluster.
   *
   * @return current size of the dynamic cluster
   */
  public Integer getDynamicClusterSize() {
    return dynamicClusterSize;
  }

  public void setDynamicClusterSize(Integer dynamicClusterSize) {
    this.dynamicClusterSize = dynamicClusterSize;
  }

  /**
   * Return maximum size of the dynamic cluster.
   *
   * @return maximum size of the dynamic cluster
   */
  public Integer getMaxDynamicClusterSize() {
    return maxDynamicClusterSize;
  }

  public void setMaxDynamicClusterSize(Integer maxDynamicClusterSize) {
    this.maxDynamicClusterSize = maxDynamicClusterSize;
  }

  /**
   * Return minimum size of the dynamic cluster.
   *
   * @return minimum size of the dynamic cluster
   */
  public Integer getMinDynamicClusterSize() {
    return Optional.ofNullable(minDynamicClusterSize).orElse(-1);
  }

  public void setMinDynamicClusterSize(Integer minDynamicClusterSize) {
    this.minDynamicClusterSize = minDynamicClusterSize;
  }

  /**
   * Return list of WlsServerConfig objects containing configurations of WLS dynamic server that can
   * be started under the current cluster size.
   *
   * @return A list of WlsServerConfig objects containing configurations of WLS dynamic server that
   *     can be started under the current cluster size
   */
  public List<WlsServerConfig> getServerConfigs() {
    return serverConfigs;
  }

  public void setServerConfigs(List<WlsServerConfig> serverConfigs) {
    this.serverConfigs = serverConfigs;
  }

  /**
   * Return the server template associated with this dynamic servers configuration.
   *
   * @return The server template associated with this dynamic servers configuration
   */
  public WlsServerConfig getServerTemplate() {
    return serverTemplate;
  }

  public void setServerTemplate(WlsServerConfig serverTemplate) {
    this.serverTemplate = serverTemplate;
  }

  public String getName() {
    return this.name;
  }

  public String getServerTemplateName() {
    return this.serverTemplateName;
  }

  public String getServerNamePrefix() {
    return this.serverNamePrefix;
  }

  public boolean getCalculatedListenPorts() {
    return this.calculatedListenPorts;
  }

  /**
   * Generate the Dynamic Server configurations.
   * @param serverTemplate name of the dynamic server template
   * @param clusterName name of the cluster
   * @param domainName name of the domain
   */
  public void generateDynamicServerConfigs(
      WlsServerConfig serverTemplate, String clusterName, String domainName) {
    List<String> dynamicServerNames = generateDynamicServerNames();
    serverConfigs =
        createServerConfigsFromTemplate(
            dynamicServerNames, serverTemplate, clusterName, domainName, calculatedListenPorts);
  }

  private List<String> generateDynamicServerNames() {
    List<String> serverNames = new ArrayList<>();
    for (int index = 1; index <= dynamicClusterSize; index++) {
      serverNames.add(serverNamePrefix + index);
    }
    return serverNames;
  }

  @Override
  public String toString() {
    return new ToStringBuilder(this)
        .append("name", name)
        .append("serverTemplateName", serverTemplateName)
        .append("dynamicClusterSize", dynamicClusterSize)
        .append("maxDynamicClusterSize", maxDynamicClusterSize)
        .append("minDynamicClusterSize", minDynamicClusterSize)
        .append("serverNamePrefix", serverNamePrefix)
        .append("calculatedListenPorts", calculatedListenPorts)
        .append("serverTemplate", serverTemplate)
        .append("machineNameMatchExpression", machineNameMatchExpression)
        .append("serverConfigs", serverConfigs)
        .toString();
  }

  @Override
  public int hashCode() {
    HashCodeBuilder builder =
        new HashCodeBuilder()
            .append(name)
            .append(serverTemplateName)
            .append(dynamicClusterSize)
            .append(maxDynamicClusterSize)
            .append(minDynamicClusterSize)
            .append(serverNamePrefix)
            .append(calculatedListenPorts)
            .append(serverTemplate)
            .append(machineNameMatchExpression)
            .append(serverConfigs);
    return builder.toHashCode();
  }

  @Override
  public boolean equals(Object other) {
    if (other == this) {
      return true;
    }
    if (!(other instanceof WlsDynamicServersConfig)) {
      return false;
    }

    WlsDynamicServersConfig rhs = ((WlsDynamicServersConfig) other);
    EqualsBuilder builder =
        new EqualsBuilder()
            .append(name, rhs.name)
            .append(serverTemplateName, rhs.serverTemplateName)
            .append(dynamicClusterSize, rhs.dynamicClusterSize)
            .append(maxDynamicClusterSize, rhs.maxDynamicClusterSize)
            .append(minDynamicClusterSize, rhs.minDynamicClusterSize)
            .append(serverNamePrefix, rhs.serverNamePrefix)
            .append(calculatedListenPorts, rhs.calculatedListenPorts)
            .append(serverTemplate, rhs.serverTemplate)
            .append(machineNameMatchExpression, rhs.machineNameMatchExpression)
            .append(serverConfigs, rhs.serverConfigs);
    return builder.isEquals();
  }
}
