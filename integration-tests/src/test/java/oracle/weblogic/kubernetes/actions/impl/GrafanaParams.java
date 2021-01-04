// Copyright (c) 2020, 2021, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.weblogic.kubernetes.actions.impl;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import oracle.weblogic.kubernetes.actions.impl.primitive.HelmParams;

// All parameters needed to install Grafana

public class GrafanaParams {

  // Adding some of the most commonly used params for now
  private int nodePort;
  private HelmParams helmParams;

  //params for server

  private static final String SVC_NODEPORT = "service.nodePort";

  public GrafanaParams nodePort(int nodePort) {
    this.nodePort = nodePort;
    return this;
  }

  public int getNodePort() {
    return nodePort;
  }

  public GrafanaParams helmParams(HelmParams helmParams) {
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
    if (nodePort > 0) {
      values.put(SVC_NODEPORT, nodePort);
    }

    values.values().removeIf(Objects::isNull);
    return values;
  }

}
