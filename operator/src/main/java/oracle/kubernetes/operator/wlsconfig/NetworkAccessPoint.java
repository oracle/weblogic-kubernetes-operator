// Copyright (c) 2017, 2021, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.wlsconfig;

import java.util.Map;

import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.apache.commons.lang3.builder.ToStringBuilder;

/** Contains configuration for a Network Access Point. */
public class NetworkAccessPoint {

  String name;
  String protocol;
  Integer listenPort;
  Integer publicPort;

  public NetworkAccessPoint() {
  }

  NetworkAccessPoint(Map<String, Object> networkAccessPointConfigMap) {
    this(
        (String) networkAccessPointConfigMap.get("name"),
        (String) networkAccessPointConfigMap.get("protocol"),
        (Integer) networkAccessPointConfigMap.get("listenPort"),
        (Integer) networkAccessPointConfigMap.get("publicPort"));
  }

  /**
   * Constructor for NetworkAccessPoint (channel).
   * @param name the name of the network access point
   * @param protocol the protocol, e.g. T3, HTTP
   * @param listenPort the listen port (on the container)
   * @param publicPort the public listen port (i.e. the node port)
   */
  public NetworkAccessPoint(String name, String protocol, Integer listenPort, Integer publicPort) {
    this.name = name;
    this.protocol = protocol;
    this.listenPort = listenPort;
    this.publicPort = publicPort;
  }

  /**
   * Return the list of configuration attributes to be retrieved from the REST search request to the
   * WLS admin server. The value would be used for constructing the REST POST request.
   */
  static String getSearchFields() {
    return "'name', 'protocol', 'listenPort', 'publicPort'";
  }

  public String getName() {
    return name;
  }

  public String getProtocol() {
    return protocol;
  }

  public boolean isAdminProtocol() {
    return "admin".equals(protocol);
  }

  public Integer getListenPort() {
    return listenPort;
  }

  public Integer getPublicPort() {
    return publicPort;
  }

  @Override
  public String toString() {
    return new ToStringBuilder(this)
        .append("name", name)
        .append("protocol", protocol)
        .append("listenPort", listenPort)
        .append("publicPort", publicPort)
        .toString();
  }

  @Override
  public int hashCode() {
    HashCodeBuilder builder =
        new HashCodeBuilder().append(name).append(protocol).append(listenPort).append(publicPort);
    return builder.toHashCode();
  }

  @Override
  public boolean equals(Object other) {
    if (other == this) {
      return true;
    }
    if (!(other instanceof NetworkAccessPoint)) {
      return false;
    }

    NetworkAccessPoint rhs = ((NetworkAccessPoint) other);
    EqualsBuilder builder =
        new EqualsBuilder()
            .append(name, rhs.name)
            .append(protocol, rhs.protocol)
            .append(listenPort, rhs.listenPort)
            .append(publicPort, rhs.publicPort);
    return builder.isEquals();
  }
}
