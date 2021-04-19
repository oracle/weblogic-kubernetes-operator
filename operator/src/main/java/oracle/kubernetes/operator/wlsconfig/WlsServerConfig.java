// Copyright (c) 2017, 2021, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.wlsconfig;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonIgnore;
import oracle.kubernetes.operator.helpers.LegalNames;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.apache.commons.lang3.builder.ToStringBuilder;

/** Contains configuration of a WebLogic server. */
public class WlsServerConfig {
  private String name;
  private Integer listenPort;
  private String listenAddress;
  private String clusterName;
  private Integer sslListenPort;
  private String machineName;
  private Integer adminPort;
  private List<NetworkAccessPoint> networkAccessPoints;

  public WlsServerConfig() {
  }

  /**
   * Creates a server configuration.
   *
   * @param name the server name
   * @param listenAddress the listen address
   * @param listenPort the listen port
   */
  public WlsServerConfig(String name, String listenAddress, int listenPort) {
    this.name = name;
    this.listenAddress = listenAddress;
    this.listenPort = listenPort;
  }

  /**
   * Construct a WlsServerConfig object using values provided.
   *
   * @param name Name of the WLS server
   * @param listenAddress Configured listen address for this WLS server
   * @param machineName Configured machine name for this WLS server
   * @param listenPort Configured listen port for this WLS server
   * @param sslListenPort Configured SSL listen port for this WLS server
   * @param adminPort Configured domain wide administration port
   * @param networkAccessPoints List of NetworkAccessPoint containing channels configured for this
   */
  public WlsServerConfig(
      String name,
      String listenAddress,
      String machineName,
      Integer listenPort,
      Integer sslListenPort,
      Integer adminPort,
      List<NetworkAccessPoint> networkAccessPoints) {
    this.name = name;
    this.listenAddress = listenAddress;
    this.machineName = machineName;
    this.listenPort = listenPort;
    this.sslListenPort = sslListenPort;
    this.adminPort = adminPort;
    this.networkAccessPoints = networkAccessPoints;
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
    boolean sslPortEnabled = sslMap != null && sslMap.get("listenPort") != null;

    // parse the administration port

    return new WlsServerConfig(
        (String) serverConfigMap.get("name"),
        (String) serverConfigMap.get("listenAddress"),
        getMachineNameFromJsonMap(serverConfigMap),
        (Integer) serverConfigMap.get("listenPort"),
        sslListenPort,
        (Integer) serverConfigMap.get("adminPort"),
        networkAccessPoints);
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
    // ["clusters", "ApplicationCluster"]
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
  private static String getMachineNameFromJsonMap(Map<String, Object> serverMap) {
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
        + getSslSearchFields()
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
  private static String getSslSearchFields() {
    return "'enabled', 'listenPort'";
  }

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

  public void setListenPort(Integer listenPort) {
    this.listenPort = listenPort;
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

  public void setSslListenPort(Integer listenPort) {
    this.sslListenPort = listenPort;
  }

  public PortDetails getSslListenPortDetails() {
    return sslListenPort == null ? null : new PortDetails(sslListenPort, true);
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

  /**
   * Add a network access point (channel).
   * @param networkAccessPoint the NetworkAccessPoint to add
   */
  public void addNetworkAccessPoint(NetworkAccessPoint networkAccessPoint) {
    if (networkAccessPoints == null) {
      networkAccessPoints = new ArrayList<>();
    }
    networkAccessPoints.add(networkAccessPoint);
  }

  public WlsServerConfig addNetworkAccessPoint(String name, int listenPort) {
    return addNetworkAccessPoint(name, "TCP", listenPort);
  }

  public WlsServerConfig addNetworkAccessPoint(String name, String protocol, int listenPort) {
    addNetworkAccessPoint(new NetworkAccessPoint(name, protocol, listenPort, null));
    return this;
  }

  public Integer getAdminPort() {
    return adminPort;
  }

  public void setAdminPort(Integer adminPort) {
    this.adminPort = adminPort;
  }

  public WlsServerConfig setAdminPort(int adminPort) {
    this.adminPort = adminPort;
    return this;
  }

  public String getClusterName() {
    return clusterName;
  }

  public boolean isAdminPortEnabled() {
    return adminPort != null;
  }

  /**
   * Get the name of the Admin protocol channel as a safe/legal kubernetes service name.
   * @return the name of the admin channel
   */
  @JsonIgnore
  public String getAdminProtocolChannelName() {
    String adminProtocolChannel = null;
    if (networkAccessPoints != null) {
      for (NetworkAccessPoint nap : networkAccessPoints) {
        if (nap.isAdminProtocol()) {
          adminProtocolChannel = LegalNames.toDns1123LegalName(nap.getName());
          break;
        }
      }
    }
    if (adminProtocolChannel == null) {
      if (adminPort != null) {
        adminProtocolChannel = "default-admin";
      } else if (sslListenPort != null) {
        adminProtocolChannel = "default-secure";
      } else if (listenPort != null) {
        adminProtocolChannel = "default";
      }
    }

    return adminProtocolChannel;
  }

  /**
   * Get the port number of the local admin protocol channel.
   * @return the port number
   */
  @JsonIgnore
  public Integer getLocalAdminProtocolChannelPort() {
    Integer adminProtocolPort = null;
    if (networkAccessPoints != null) {
      for (NetworkAccessPoint nap : networkAccessPoints) {
        if (nap.isAdminProtocol()) {
          adminProtocolPort = nap.getListenPort();
          break;
        }
      }
    }
    if (adminProtocolPort == null) {
      if (adminPort != null) {
        adminProtocolPort = adminPort;
      } else if (sslListenPort != null) {
        adminProtocolPort = sslListenPort;
      } else if (listenPort != null) {
        adminProtocolPort = listenPort;
      }
    }

    return adminProtocolPort;
  }

  /**
   * Check if the admin protocol channel is using a secure protocol like T3S or HTTPS.
   * @return true is a secure protocol is being used
   */
  public boolean isLocalAdminProtocolChannelSecure() {
    boolean adminProtocolPortSecure = false;
    boolean adminProtocolPortFound = false;
    if (networkAccessPoints != null) {
      for (NetworkAccessPoint nap : networkAccessPoints) {
        if (nap.isAdminProtocol()) {
          adminProtocolPortFound = true;
          adminProtocolPortSecure = true;
          break;
        }
      }
    }
    if (!adminProtocolPortFound) {
      if (adminPort != null) {
        adminProtocolPortSecure = true;
      } else if (sslListenPort != null) {
        adminProtocolPortSecure = true;
      } else if (listenPort != null) {
        adminProtocolPortSecure = false;
      }
    }
    return adminProtocolPortSecure;
  }

  /**
   * Whether this server is a dynamic server, ie, not statically configured.
   *
   * @return True if this server is a dynamic server, false if this server is configured statically
   */
  public boolean isDynamicServer() {
    return false;
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
        .append(name, that.name)
        .append(listenPort, that.listenPort)
        .append(listenAddress, that.listenAddress)
        .append(sslListenPort, that.sslListenPort)
        .append(adminPort, that.adminPort)
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
        .append(adminPort)
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
        .append("adminPort", adminPort)
        .append("machineName", machineName)
        .append("networkAccessPoints", networkAccessPoints)
        .toString();
  }

}
