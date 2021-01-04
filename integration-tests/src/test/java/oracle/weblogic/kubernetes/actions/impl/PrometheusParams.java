// Copyright (c) 2020, 2021, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.weblogic.kubernetes.actions.impl;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import oracle.weblogic.kubernetes.actions.impl.primitive.HelmParams;

// All parameters needed to install Prometheus

public class PrometheusParams {

  // Adding some of the most commonly used params for now
  private int nodePortAlertManager;
  private int nodePortServer;
  private HelmParams helmParams;

  private static final String ALERTMANAGER_SVC_NODEPORT = "alertmanager.service.nodePort";


  //params for server

  private static final String SERVER_SVC_NODEPORT = "server.service.nodePort";



  public PrometheusParams nodePortAlertManager(int nodePortAlertManager) {
    this.nodePortAlertManager = nodePortAlertManager;
    return this;
  }

  public PrometheusParams nodePortServer(int nodePortServer) {
    this.nodePortServer = nodePortServer;
    return this;
  }


  public PrometheusParams helmParams(HelmParams helmParams) {
    this.helmParams = helmParams;
    return this;
  }

  public HelmParams getHelmParams() {
    return helmParams;
  }

  /**
   * Loads Helm values into a value map.
   *
   * @return Map of values
   */
  public Map<String, Object> getValues() {
    Map<String, Object> values = new HashMap<>();
    if (nodePortAlertManager > 0) {
      values.put(ALERTMANAGER_SVC_NODEPORT, nodePortAlertManager);
    }
    if (nodePortServer > 0) {
      values.put(SERVER_SVC_NODEPORT, nodePortServer);
    }

    values.values().removeIf(Objects::isNull);
    return values;
  }

}
