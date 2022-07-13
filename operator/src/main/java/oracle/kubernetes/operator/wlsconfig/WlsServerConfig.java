// Copyright (c) 2017, 2021, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.wlsconfig;

import java.util.ArrayList;
import java.util.List;

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

  /**
   * Return whether the SSL listen port is configured to be enabled or not.
   *
   * @return True if the SSL listen port should be enabled, false otherwise
   */
  @JsonIgnore
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

  @JsonIgnore
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
  @JsonIgnore
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
  @JsonIgnore
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
