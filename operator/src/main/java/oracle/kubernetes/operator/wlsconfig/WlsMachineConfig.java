// Copyright (c) 2018, 2020, Oracle Corporation and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.wlsconfig;

import java.util.Map;

/** Contains values from a WLS machine configuration. */
public class WlsMachineConfig {

  final String name;
  final Integer nodeManagerListenPort;
  final String nodeManagerListenAddress;
  final String nodeManagerType;

  /**
   * Constructor for WlsMachineConfig.
   * @param name the machine name
   * @param nodeManagerListenPort the port that the node manager will listen on, e.g. 1234
   * @param nodeManagerListenAddress the listen address for the node manager to bind to, e.g. 0.0.0.0
   * @param nodeManagerType the node manager type, e.g. plain, SSL
   */
  public WlsMachineConfig(
      String name,
      Integer nodeManagerListenPort,
      String nodeManagerListenAddress,
      String nodeManagerType) {
    this.name = name;
    this.nodeManagerListenPort = nodeManagerListenPort;
    this.nodeManagerListenAddress = nodeManagerListenAddress;
    this.nodeManagerType = nodeManagerType;
  }

  /**
   * Creates a WlsMachineConfig object using an "machines" item parsed from JSON result from WLS
   * REST call.
   *
   * @param machineConfigMap Map containing "machine" item parsed from JSON result from WLS REST
   *     call
   * @return A new WlsMachineConfig object created based on the JSON result
   */
  @SuppressWarnings("unchecked")
  static WlsMachineConfig create(Map<String, Object> machineConfigMap) {
    String machineName = (String) machineConfigMap.get("name");
    Map nodeManager = (Map<String, Object>) machineConfigMap.get("nodeManager");
    Integer nodeManagerListenPort = null;
    String nodeManagerListenAddress = null;
    String nodeManagerType = null;
    if (nodeManager != null) {
      nodeManagerListenAddress = (String) nodeManager.get("listenAddress");
      nodeManagerListenPort = (Integer) nodeManager.get("listenPort");
      nodeManagerType = (String) nodeManager.get("NMType");
    }
    return new WlsMachineConfig(
        machineName, nodeManagerListenPort, nodeManagerListenAddress, nodeManagerType);
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
        + "      nodeManager: { "
        + "      fields: [ "
        + getNodeManagerSearchFields()
        + " ], "
        + "      links: [] "
        + "        }"
        + "    } ";
  }

  /**
   * Return the fields from machine WLS configuration that should be retrieved from the WLS REST
   * request.
   *
   * @return A string containing machine configuration fields that should be retrieved from the WLS
   *     REST request, in a format that can be used in the REST request payload
   */
  private static String getSearchFields() {
    return "'name' ";
  }

  static String getCreateUrl() {
    return "/management/weblogic/latest/edit/machines";
  }

  static String getCreatePayload(String machineName) {
    return " { name: \'" + machineName + "\' }";
  }

  /**
   * Return the fields from node manager WLS configuration that should be retrieved from the WLS
   * REST request.
   *
   * @return A string containing node manager configuration fields that should be retrieved from the
   *     WLS REST request, in a format that can be used in the REST request payload
   */
  private static String getNodeManagerSearchFields() {
    return "'listenAddress', 'listenPort', 'NMType' ";
  }

  /**
   * Name of the machine that this WlsMachineConfig is created for.
   *
   * @return Name of the machine that this WlsMachineConfig is created for
   */
  public String getName() {
    return name;
  }

  /**
   * Listen port of the node manager for the machine that this WlsMachineConfig is created for.
   *
   * @return Listen port of the node manager for the machine that this WlsMachineConfig is created
   *     for
   */
  public Integer getNodeManagerListenPort() {
    return nodeManagerListenPort;
  }

  /**
   * Listen address of the node manager for the machine that this WlsMachineConfig is created for.
   *
   * @return Listen address of the node manager for the machine that this WlsMachineConfig is
   *     created for
   */
  public String getNodeManagerListenAddress() {
    return nodeManagerListenAddress;
  }

  /**
   * Type of node manager (Plain, SSL, etc) for the machine that this WlsMachineConfig is created
   * for.
   *
   * @return Type of node manager (Plain, SSL, etc) for the machine that this WlsMachineConfig is
   *     created for
   */
  public String getNodeManagerType() {
    return nodeManagerType;
  }

  @Override
  public String toString() {
    return "WlsMachineConfig{"
        + "name='"
        + name
        + '\''
        + ", nodeManagerListenPort="
        + nodeManagerListenPort
        + ", nodeManagerListenAddress='"
        + nodeManagerListenAddress
        + '\''
        + ", nodeManagerType='"
        + nodeManagerType
        + '\''
        + '}';
  }
}
