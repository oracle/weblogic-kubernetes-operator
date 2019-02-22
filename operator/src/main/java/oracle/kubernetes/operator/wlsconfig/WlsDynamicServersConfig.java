// Copyright 2018, Oracle Corporation and/or its affiliates.  All rights reserved.
// Licensed under the Universal Permissive License v 1.0 as shown at
// http://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.wlsconfig;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import oracle.kubernetes.operator.logging.LoggingFacade;
import oracle.kubernetes.operator.logging.LoggingFactory;
import oracle.kubernetes.operator.logging.MessageKeys;

/**
 * Contains values from a WLS dynamic servers configuration, which configures a WLS dynamic cluster.
 */
public class WlsDynamicServersConfig {

  private static final LoggingFacade LOGGER = LoggingFactory.getLogger("Operator", "Operator");

  String name;
  String serverTemplateName;
  Integer dynamicClusterSize;
  Integer maxDynamicClusterSize;
  String serverNamePrefix;
  boolean calculatedListenPorts;

  WlsServerConfig serverTemplate;
  String machineNameMatchExpression;
  List<WlsServerConfig> serverConfigs;

  /**
   * Creates a WlsDynamicServersConfig object using an "dynamicServers" item parsed from JSON result
   * from WLS REST call.
   *
   * @param dynamicServerConfig Map containing "dynamicServers" item parsed from JSON result from
   *     WLS REST call
   * @param serverTemplates Map containing all server templates configuration read from the WLS
   *     domain
   * @param clusterName Name of the WLS cluster that this dynamic servers configuration belongs to
   * @param domainName Name of the WLS domain that this WLS cluster belongs to
   * @return A new WlsDynamicServersConfig object created based on the JSON result
   */
  @SuppressWarnings("unchecked")
  static WlsDynamicServersConfig create(
      Map<String, Object> dynamicServerConfig,
      Map<String, WlsServerConfig> serverTemplates,
      String clusterName,
      String domainName) {
    Integer dynamicClusterSize = null;
    Integer maxDynamicClusterSize = null;
    String serverNamePrefix = null;
    boolean calculatedListenPorts = false;
    String machineNameMatchExpression = null;
    WlsServerConfig serverTemplate = null;
    List<WlsServerConfig> serverConfigs = null;
    if (dynamicServerConfig != null) {
      dynamicClusterSize = (Integer) dynamicServerConfig.get("dynamicClusterSize");
      maxDynamicClusterSize = (Integer) dynamicServerConfig.get("maxDynamicClusterSize");
      serverNamePrefix = (String) dynamicServerConfig.get("serverNamePrefix");
      calculatedListenPorts = (boolean) dynamicServerConfig.get("calculatedListenPorts");
      machineNameMatchExpression = (String) dynamicServerConfig.get("machineNameMatchExpression");
      String serverTemplateName = getServerTemplateNameFromConfig(dynamicServerConfig);

      if (serverTemplateName != null) {
        serverTemplate = serverTemplates.get(serverTemplateName);
        if (serverTemplate == null) {
          LOGGER.warning(
              MessageKeys.WLS_SERVER_TEMPLATE_NOT_FOUND, serverTemplateName, clusterName);
        } else {
          serverConfigs =
              createServerConfigsFromTemplate(
                  (List<String>) dynamicServerConfig.get("dynamicServerNames"),
                  serverTemplate,
                  clusterName,
                  domainName,
                  calculatedListenPorts);
        }
      }
    }
    return new WlsDynamicServersConfig(
        dynamicClusterSize,
        maxDynamicClusterSize,
        serverNamePrefix,
        calculatedListenPorts,
        machineNameMatchExpression,
        serverTemplate,
        serverConfigs);
  }

  public WlsDynamicServersConfig() {}

  /**
   * Constructor.
   *
   * @param dynamicClusterSize current size of the dynamic cluster
   * @param maxDynamicClusterSize maximum size of the dynamic cluster
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
      String serverNamePrefix,
      boolean calculatedListenPorts,
      String machineNameMatchExpression,
      WlsServerConfig serverTemplate,
      List<WlsServerConfig> serverConfigs) {
    this.dynamicClusterSize = dynamicClusterSize;
    this.maxDynamicClusterSize = maxDynamicClusterSize;
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
   * Return the expression used in matching machine names assigned to dynamic servers.
   *
   * @return the expression used in matching machine names assigned to dynamic servers
   */
  public String getMachineNameMatchExpression() {
    return machineNameMatchExpression;
  }

  public void setMachineNameMatchExpression(String machineNameMatchExpression) {
    this.machineNameMatchExpression = machineNameMatchExpression;
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
   * Helper method to extract the server template name from the Map obtained from parsing the
   * "dynamicServers" element from the REST result.
   *
   * @param dynamicServerConfig Map containing the "dynamicServers" element from the REST call
   * @return Name of the server template associated with this dynamic server configuration
   */
  private static String getServerTemplateNameFromConfig(Map dynamicServerConfig) {
    // dynamicServerConfig contains a "serverTemplates" entry from the REST call which is in the
    // form: "serverTemplate": ["serverTemplates", "my-server-template-name"]
    List serverTemplatesList = (List) dynamicServerConfig.get("serverTemplate");
    if (serverTemplatesList != null) {
      for (Object value : serverTemplatesList) {
        // the first entry that is not "serverTemplates" is assumed to be the server template name
        if (!"serverTemplates".equals(value)) {
          return (String) value;
        }
      }
    }
    return null;
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

  public void setName(String name) {
    this.name = name;
  }

  public String getServerTemplateName() {
    return this.serverTemplateName;
  }

  public void setServerTemplateName(String serverTemplateName) {
    this.serverTemplateName = serverTemplateName;
  }

  public String getServerNamePrefix() {
    return this.serverNamePrefix;
  }

  public void setServerNamePrefix(String serverNamePrefix) {
    this.serverNamePrefix = serverNamePrefix;
  }

  public boolean getCalculatedListenPorts() {
    return this.calculatedListenPorts;
  }

  public void setCalculatedListenPorts(boolean calculatedListenPorts) {
    this.calculatedListenPorts = calculatedListenPorts;
  }

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

  /**
   * Returns a String containing the fields that we are interested in from the dynamic servers
   * configuration which will used in the payload to the REST call to WLS admin server.
   *
   * @return a String containing the fields that we are interested in from the dynamic servers
   *     configuration which will used in the payload to the REST call to WLS admin server
   */
  static String getSearchFields() {
    return "'serverTemplate', 'dynamicClusterSize', 'maxDynamicClusterSize', 'serverNamePrefix', 'calculatedListenPorts', 'dynamicServerNames', 'machineNameMatchExpression' ";
  }

  @Override
  public String toString() {
    return "WlsDynamicServersConfig{"
        + "dynamicClusterSize="
        + dynamicClusterSize
        + ", maxDynamicClusterSize="
        + maxDynamicClusterSize
        + ", serverNamePrefix='"
        + serverNamePrefix
        + '\''
        + ", calculatedListenPorts="
        + calculatedListenPorts
        + ", machineNameMatchExpression="
        + machineNameMatchExpression
        + ", serverTemplate="
        + serverTemplate
        + ", serverConfigs="
        + serverConfigs
        + '}';
  }
}
