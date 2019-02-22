// Copyright 2018, Oracle Corporation and/or its affiliates.  All rights reserved.
// Licensed under the Universal Permissive License v 1.0 as shown at
// http://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.wlsconfig;

import java.util.ArrayList;
import java.util.List;
import org.apache.commons.lang3.builder.ToStringBuilder;

/** Contains configuration of a WLS server that belongs to a dynamic cluster. */
public class WlsDynamicServerConfig extends WlsServerConfig {

  // default listen ports per WebLogic DynamicServersMBean
  static final int DEFAULT_LISTEN_PORT_RANGE_BASE = 7100;
  static final int DEFAULT_SSL_LISTEN_PORT_RANGE_BASE = 8100;
  static final int DEFAULT_NAP_LISTEN_PORT_RANGE_BASE = 9100;

  /**
   * Create a dynamic server config using server template and index number of this server.
   *
   * @param name Name of the server
   * @param index index of this server within the cluster, for example, the index of dserver-2 would
   *     be 2
   * @param clusterName name of the WLS cluster that this server belongs to
   * @param domainName name of the WLS domain that this server belongs to
   * @param calculatedListenPorts whether listen ports are calculated according to configuration in
   *     the dynamic cluster
   * @param serverTemplate server template used for servers in the dynamic cluster
   * @return a dynamic server configuration object containing configuration of this dynamic server
   */
  static WlsDynamicServerConfig create(
      String name,
      int index,
      String clusterName,
      String domainName,
      boolean calculatedListenPorts,
      WlsServerConfig serverTemplate) {
    Integer listenPort = serverTemplate.getListenPort();
    Integer sslListenPort = serverTemplate.getSslListenPort();
    List<NetworkAccessPoint> networkAccessPoints = new ArrayList<>();
    if (serverTemplate.getNetworkAccessPoints() != null) {
      for (NetworkAccessPoint networkAccessPoint : serverTemplate.getNetworkAccessPoints()) {
        Integer networkAccessPointListenPort = networkAccessPoint.getListenPort();
        if (calculatedListenPorts) {
          networkAccessPointListenPort =
              networkAccessPointListenPort == null
                  ? (DEFAULT_NAP_LISTEN_PORT_RANGE_BASE + index)
                  : networkAccessPointListenPort + index;
        }
        networkAccessPoints.add(
            new NetworkAccessPoint(
                networkAccessPoint.getName(),
                networkAccessPoint.getProtocol(),
                networkAccessPointListenPort,
                networkAccessPoint.getPublicPort()));
      }
    }
    // calculate listen ports if configured to do so
    if (calculatedListenPorts) {
      listenPort =
          (listenPort == null) ? (DEFAULT_LISTEN_PORT_RANGE_BASE + index) : (listenPort + index);
      sslListenPort =
          (sslListenPort == null)
              ? (DEFAULT_SSL_LISTEN_PORT_RANGE_BASE + index)
              : (sslListenPort + index);
    }
    MacroSubstitutor macroSubstitutor =
        new MacroSubstitutor(index, name, clusterName, domainName, serverTemplate.getMachineName());
    return new WlsDynamicServerConfig(
        name,
        listenPort,
        macroSubstitutor.substituteMacro(serverTemplate.getListenAddress()),
        sslListenPort,
        serverTemplate.isSslPortEnabled(),
        macroSubstitutor.substituteMacro(serverTemplate.getMachineName()),
        networkAccessPoints);
  }

  /**
   * private constructor. Use {@link #create(String, int, String, String, boolean, WlsServerConfig)}
   * for creating an instance of WlsDynamicServerConfig instead.
   *
   * @param name Name of the dynamic server
   * @param listenPort list port of the dynamic server
   * @param listenAddress listen address of the dynamic server
   * @param sslListenPort SSL listen port of the dynamic server
   * @param sslPortEnabled boolean indicating whether SSL listen port is enabled
   * @param machineName machine name of the dynamic server
   * @param networkAccessPoints network access points or channels configured for this dynamic server
   */
  private WlsDynamicServerConfig(
      String name,
      Integer listenPort,
      String listenAddress,
      Integer sslListenPort,
      boolean sslPortEnabled,
      String machineName,
      List<NetworkAccessPoint> networkAccessPoints) {
    super(
        name,
        listenPort,
        listenAddress,
        sslListenPort,
        sslPortEnabled,
        machineName,
        networkAccessPoints,
        null,
        false);
  }

  /**
   * Whether this server is a dynamic server, ie, not statically configured.
   *
   * @return True if this server is a dynamic server, false if this server is configured statically
   */
  @Override
  public boolean isDynamicServer() {
    return true;
  }

  @Override
  public String toString() {
    return new ToStringBuilder(this).toString();
  }
}
