// Copyright 2017, 2018, Oracle Corporation and/or its affiliates.  All rights reserved.
// Licensed under the Universal Permissive License v 1.0 as shown at
// http://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.wlsconfig;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.apache.commons.lang3.builder.ToStringBuilder;

/** Contains configuration of a WebLogic server. */
public class WlsServerConfig {
  String name;
  Integer listenPort;
  String listenAddress;
  String clusterName;
  Integer sslListenPort;
  boolean sslPortEnabled;
  String machineName;
  Integer adminPort;
  boolean adminPortEnabled;
  List<NetworkAccessPoint> networkAccessPoints;

  public WlsServerConfig() {}

  /**
   * Return the name of this WLS server.
   *
   * @return The name of this WLS server
   */
  public String getName() {
    return name;
  }

  /**
   * Return the configured listen port of this WLS server.
   *
   * @return The configured listen port of this WLS server
   */
  public Integer getListenPort() {
    return listenPort;
  }

  /**
   * Return the configured listen address of this WLS server.
   *
   * @return The configured listen address of this WLS server
   */
  public String getListenAddress() {
    return listenAddress;
  }

  /**
   * Return the configured SSL listen port of this WLS server.
   *
   * @return The configured SSL listen port of this WLS server
   */
  public Integer getSslListenPort() {
    return sslListenPort;
  }

  /**
   * Return whether the SSL listen port is configured to be enabled or not.
   *
   * @return True if the SSL listen port should be enabled, false otherwise
   */
  public boolean isSslPortEnabled() {
    return sslListenPort != null;
  }

  /**
   * Return the machine name configured for this WLS server.
   *
   * @return The configured machine name for this WLS server
   */
  public String getMachineName() {
    return machineName;
  }

  /**
   * Returns an array containing all network access points configured in this WLS server.
   *
   * @return An array of NetworkAccessPoint containing configured network access points in this WLS
   *     server. If there are no network access points configured in this server, an empty array is
   *     returned.
   */
  public List<NetworkAccessPoint> getNetworkAccessPoints() {
    return networkAccessPoints;
  }

  public String getClusterName() {
    return this.clusterName;
  }

  public void setClusterName(String clusterName) {
    this.clusterName = clusterName;
  }

  public Integer getAdminPort() {
    return adminPort;
  }

  public void setAdminPort(Integer adminPort) {
    this.adminPort = adminPort;
  }

  public boolean isAdminPortEnabled() {
    return adminPort != null;
  }

  /**
   * Creates a WLSServerConfig object using an "servers" or "serverTemplates" item parsed from JSON
   * result from WLS REST call.
   *
   * @param serverConfigMap A Map containing the parsed "servers" or "serverTemplates" element for a
   *     WLS server or WLS server template.
   * @return A new WlsServerConfig object using the provided configuration from the configuration
   *     map
   */
  @SuppressWarnings("unchecked")
  static WlsServerConfig create(Map<String, Object> serverConfigMap) {
    // parse the configured network access points or channels
    Map networkAccessPointsMap = (Map<String, Object>) serverConfigMap.get("networkAccessPoints");
    List<NetworkAccessPoint> networkAccessPoints = new ArrayList<>();
    if (networkAccessPointsMap != null) {
      List<Map<String, Object>> networkAccessPointItems =
          (List<Map<String, Object>>) networkAccessPointsMap.get("items");
      if (networkAccessPointItems != null && networkAccessPointItems.size() > 0) {
        for (Map<String, Object> networkAccessPointConfigMap : networkAccessPointItems) {
          NetworkAccessPoint networkAccessPoint =
              new NetworkAccessPoint(networkAccessPointConfigMap);
          networkAccessPoints.add(networkAccessPoint);
        }
      }
    }
    // parse the SSL configuration
    Map<String, Object> sslMap = (Map<String, Object>) serverConfigMap.get("SSL");
    Integer sslListenPort = (sslMap == null) ? null : (Integer) sslMap.get("listenPort");
    boolean sslPortEnabled = (sslMap != null && sslMap.get("listenPort") != null) ? true : false;

    // parse the administration port
    Integer adminPort = (Integer) serverConfigMap.get("adminPort");
    boolean adminPortEnabled = (adminPort != null);

    return new WlsServerConfig(
        (String) serverConfigMap.get("name"),
        (Integer) serverConfigMap.get("listenPort"),
        (String) serverConfigMap.get("listenAddress"),
        sslListenPort,
        sslPortEnabled,
        getMachineNameFromJsonMap(serverConfigMap),
        networkAccessPoints,
        adminPort,
        adminPortEnabled);
  }

  /**
   * Construct a WlsServerConfig object using values provided.
   *
   * @param name Name of the WLS server
   * @param listenPort Configured listen port for this WLS server
   * @param listenAddress Configured listen address for this WLS server
   * @param sslListenPort Configured SSL listen port for this WLS server
   * @param sslPortEnabled boolean indicating whether the SSL listen port should be enabled
   * @param machineName Configured machine name for this WLS server
   * @param networkAccessPoints List of NetworkAccessPoint containing channels configured for this
   * @param adminPort Configured domain wide administration port
   * @param adminPortEnabled boolean indicating whether administration port should be enabled
   */
  public WlsServerConfig(
      String name,
      Integer listenPort,
      String listenAddress,
      Integer sslListenPort,
      boolean sslPortEnabled,
      String machineName,
      List<NetworkAccessPoint> networkAccessPoints,
      Integer adminPort,
      boolean adminPortEnabled) {
    this.name = name;
    this.listenPort = listenPort;
    this.listenAddress = listenAddress;
    this.networkAccessPoints = networkAccessPoints;
    this.sslListenPort = sslListenPort;
    this.sslPortEnabled = sslPortEnabled;
    this.adminPort = adminPort;
    this.adminPortEnabled = adminPortEnabled;
    this.machineName = machineName;
  }

  /**
   * Helper method to parse the cluster name from an item from the Json "servers" or
   * "serverTemplates" element.
   *
   * @param serverMap Map containing parsed Json "servers" or "serverTemplates" element
   * @return Cluster name contained in the Json element
   */
  static String getClusterNameFromJsonMap(Map<String, Object> serverMap) {
    // serverMap contains a "cluster" entry from the REST call which is in the form: "cluster":
    // ["clusters", "DockerCluster"]
    @SuppressWarnings({"unchecked", "rawtypes"})
    List<String> clusterList = (List) serverMap.get("cluster");
    if (clusterList != null) {
      for (String value : clusterList) {
        // the first entry that is not "clusters" is assumed to be the cluster name
        if (!"clusters".equals(value)) {
          return value;
        }
      }
    }
    return null;
  }

  /**
   * Helper method to parse the machine name from an item from the Json "servers" or
   * "serverTemplates" element.
   *
   * @param serverMap Map containing parsed Json "servers" or "serverTemplates" element
   * @return Machine name contained in the Json element
   */
  static String getMachineNameFromJsonMap(Map<String, Object> serverMap) {
    // serverMap contains a "machine" entry from the REST call which is in the form: "machine":
    // ["machines", "domain1-machine1"]
    @SuppressWarnings({"unchecked", "rawtypes"})
    List<String> clusterList = (List) serverMap.get("machine");
    if (clusterList != null) {
      for (String value : clusterList) {
        // the first entry that is not "machines" is assumed to be the machine name
        if (!"machines".equals(value)) {
          return value;
        }
      }
    }
    return null;
  }

  /**
   * Whether this server is a dynamic server, ie, not statically configured.
   *
   * @return True if this server is a dynamic server, false if this server is configured statically
   */
  public boolean isDynamicServer() {
    return false;
  }

  /**
   * Return the list of configuration attributes to be retrieved from the REST search request to the
   * WLS admin server. The value would be used for constructing the REST POST request.
   *
   * @return The list of configuration attributes to be retrieved from the REST search request to
   *     the WLS admin server. The value would be used for constructing the REST POST request.
   */
  static String getSearchPayload() {
    return "      fields: [ "
        + getSearchFields()
        + " ], "
        + "      links: [], "
        + "      children: { "
        + "        SSL: { "
        + "          fields: [ "
        + getSSLSearchFields()
        + " ], "
        + "          links: [] "
        + "        }, "
        + "        networkAccessPoints: { "
        + "          fields: [ "
        + NetworkAccessPoint.getSearchFields()
        + " ], "
        + "          links: [] "
        + "        } "
        + "      } ";
  }

  /**
   * Return the fields from server or server template WLS configuration that should be retrieved
   * from the WLS REST request.
   *
   * @return A string containing server or server template fields that should be retrieved from the
   *     WLS REST request, in a format that can be used in the REST request payload
   */
  private static String getSearchFields() {
    return "'name', 'cluster', 'listenPort', 'listenAddress', 'publicPort', 'machine' ";
  }

  /**
   * Return the fields from SSL WLS configuration that should be retrieved from the WLS REST
   * request.
   *
   * @return A string containing SSL fields that should be retrieved from the WLS REST request, in a
   *     format that can be used in the REST request payload
   */
  private static String getSSLSearchFields() {
    return "'enabled', 'listenPort'";
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }

    if (o == null || getClass() != o.getClass()) {
      return false;
    }

    WlsServerConfig that = (WlsServerConfig) o;

    return new EqualsBuilder()
        .append(sslPortEnabled, that.sslPortEnabled)
        .append(name, that.name)
        .append(listenPort, that.listenPort)
        .append(listenAddress, that.listenAddress)
        .append(sslListenPort, that.sslListenPort)
        .append(adminPort, that.adminPort)
        .append(adminPortEnabled, that.adminPortEnabled)
        .append(machineName, that.machineName)
        .append(networkAccessPoints, that.networkAccessPoints)
        .isEquals();
  }

  @Override
  public int hashCode() {
    return new HashCodeBuilder(17, 37)
        .append(name)
        .append(listenPort)
        .append(listenAddress)
        .append(sslListenPort)
        .append(sslPortEnabled)
        .append(adminPort)
        .append(adminPortEnabled)
        .append(machineName)
        .append(networkAccessPoints)
        .toHashCode();
  }

  @Override
  public String toString() {
    return new ToStringBuilder(this)
        .append("name", name)
        .append("listenPort", listenPort)
        .append("listenAddress", listenAddress)
        .append("sslListenPort", sslListenPort)
        .append("sslPortEnabled", sslPortEnabled)
        .append("adminPort", adminPort)
        .append("adminPortEnabled", adminPortEnabled)
        .append("machineName", machineName)
        .append("networkAccessPoints", networkAccessPoints)
        .toString();
  }
}
