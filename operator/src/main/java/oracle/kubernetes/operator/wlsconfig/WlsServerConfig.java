// Copyright 2017, 2018, Oracle Corporation and/or its affiliates.  All rights reserved.
// Licensed under the Universal Permissive License v 1.0 as shown at http://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.wlsconfig;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Contains configuration of a WebLogic server
 */
public class WlsServerConfig {
  final String name;
  final Integer listenPort;
  final String listenAddress;
  final Map<String, NetworkAccessPoint> networkAccessPoints = new HashMap<>();

  public String getName() {
    return name;
  }

  public Integer getListenPort() {
    return listenPort;
  }

  public String getListenAddress() {
    return listenAddress;
  }

  /**
   * Returns an array containing all network access points configured in this WLS server
   * @return An array of NetworkAccessPoint containing configured network access points in this WLS server. If there
   * are no network access points configured in this server, an empty array is returned.
   */
  public List<NetworkAccessPoint> getNetworkAccessPoints() {
    return new ArrayList<>(networkAccessPoints.values());
  }

  WlsServerConfig(Map<String, Object> serverConfigMap) {
    this((String) serverConfigMap.get("name"),
            (Integer) serverConfigMap.get("listenPort"),
            (String) serverConfigMap.get("listenAddress"),
            (Map) serverConfigMap.get("networkAccessPoints"));
  }

  public WlsServerConfig(String name, Integer listenPort, String listenAddress, Map networkAccessPointsMap) {
    this.name = name;
    this.listenPort = listenPort;
    this.listenAddress = listenAddress;
    if (networkAccessPointsMap != null) {
      List<Map<String, Object>> networkAccessPointItems =  (List<Map<String, Object>>) networkAccessPointsMap.get("items");
      if (networkAccessPointItems != null && networkAccessPointItems.size() > 0) {
        for (Map<String, Object> networkAccessPointConfigMap:  networkAccessPointItems) {
          NetworkAccessPoint networkAccessPoint = new NetworkAccessPoint(networkAccessPointConfigMap);
          this.networkAccessPoints.put(networkAccessPoint.getName(), networkAccessPoint);
        }
      }
    }
  }

  /**
   * Return the list of configuration attributes to be retrieved from the REST search request to the WLS admin server.
   * The value would be used for constructing the REST POST request.
   */
  static String getSearchFields() {
    return "'name', 'cluster', 'listenPort', 'listenAddress', 'publicPort'";
  }

  @Override
  public String toString() {
    return "WlsServerConfig{" +
            "name='" + name + '\'' +
            ", listenPort=" + listenPort +
            ", listenAddress='" + listenAddress + '\'' +
            ", networkAccessPoints=" + networkAccessPoints +
            '}';
  }
}
