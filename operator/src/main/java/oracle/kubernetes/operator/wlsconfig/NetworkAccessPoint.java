// Copyright 2017, 2018, Oracle Corporation and/or its affiliates.  All rights reserved.
// Licensed under the Universal Permissive License v 1.0 as shown at
// http://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.wlsconfig;

import java.util.Map;

/** Contains configuration for a Network Access Point. */
public class NetworkAccessPoint {

  String name;
  String protocol;
  Integer listenPort;
  Integer publicPort;

  public NetworkAccessPoint() {}

  NetworkAccessPoint(Map<String, Object> networkAccessPointConfigMap) {
    this(
        (String) networkAccessPointConfigMap.get("name"),
        (String) networkAccessPointConfigMap.get("protocol"),
        (Integer) networkAccessPointConfigMap.get("listenPort"),
        (Integer) networkAccessPointConfigMap.get("publicPort"));
  }

  public NetworkAccessPoint(String name, String protocol, Integer listenPort, Integer publicPort) {
    this.name = name;
    this.protocol = protocol;
    this.listenPort = listenPort;
    this.publicPort = publicPort;
  }

  public String getName() {
    return name;
  }

  public String getProtocol() {
    return protocol;
  }

  public Integer getListenPort() {
    return listenPort;
  }

  public Integer getPublicPort() {
    return publicPort;
  }

  /**
   * Return the list of configuration attributes to be retrieved from the REST search request to the
   * WLS admin server. The value would be used for constructing the REST POST request.
   */
  static String getSearchFields() {
    return "'name', 'protocol', 'listenPort', 'publicPort'";
  }

  @Override
  public String toString() {
    return "NetworkAccessPoint{"
        + "name='"
        + name
        + '\''
        + ", protocol='"
        + protocol
        + '\''
        + ", listenPort="
        + listenPort
        + '\''
        + ", publicPort="
        + publicPort
        + '}';
  }
}
