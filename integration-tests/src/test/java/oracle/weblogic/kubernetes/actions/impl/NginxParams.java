// Copyright (c) 2020, 2021, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.weblogic.kubernetes.actions.impl;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import oracle.weblogic.kubernetes.actions.impl.primitive.HelmParams;

/**
 * All parameters needed to install NGINX ingress controller.
 */
public class NginxParams {

  // Only add the values which need to be updated here.
  // The default values can be found here:
  // https://github.com/helm/charts/blob/master/stable/nginx-ingress/values.yaml
  private static final String NODEPORTS_HTTP = "controller.service.nodePorts.http";
  private static final String NODEPORTS_HTTPS = "controller.service.nodePorts.https";

  // Adding some of the most commonly used params for now
  private int nodePortsHttp;
  private int nodePortsHttps;
  private HelmParams helmParams;

  public NginxParams nodePortsHttp(int nodePortsHttp) {
    this.nodePortsHttp = nodePortsHttp;
    return this;
  }

  public NginxParams nodePortsHttps(int nodePortsHttps) {
    this.nodePortsHttps = nodePortsHttps;
    return this;
  }

  public NginxParams helmParams(HelmParams helmParams) {
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

    if (nodePortsHttp > 0) {
      values.put(NODEPORTS_HTTP, nodePortsHttp);
    }
    if (nodePortsHttps > 0) {
      values.put(NODEPORTS_HTTPS, nodePortsHttps);
    }

    values.values().removeIf(Objects::isNull);
    return values;
  }
}
