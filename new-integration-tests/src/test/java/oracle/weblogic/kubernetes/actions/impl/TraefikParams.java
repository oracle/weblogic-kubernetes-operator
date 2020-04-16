// Copyright (c) 2020, Oracle Corporation and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.weblogic.kubernetes.actions.impl;

import java.util.HashMap;
import java.util.Objects;

import oracle.weblogic.kubernetes.actions.impl.primitive.HelmParams;

// All parameters needed to install Traefik Operator

public class TraefikParams {

  // adding the params based on weblogic-kubernetes-operator/kubernetes/samples/charts/traefik/values.yaml
  // you can add more values here
  private static final String NAMESPACES = "kubernetes.namespaces";
  private static final String NODEPORTS_HTTP = "service.nodePorts.http";
  private static final String NODEPORTS_HTTPS = "service.nodePorts.https";

  // Adding some of the most commonly used params for now
  private int nodePortsHttp;
  private int nodePortsHttps;
  private String nameSpaces;
  private HelmParams helmParams;

  public TraefikParams nodePortsHttp(int nodePortsHttp) {
    this.nodePortsHttp = nodePortsHttp;
    return this;
  }

  public TraefikParams nodePortsHttps(int nodePortsHttps) {
    this.nodePortsHttps = nodePortsHttps;
    return this;
  }

  public TraefikParams nameSpaces(String nameSpaces) {
    this.nameSpaces = nameSpaces;
    return this;
  }

  public TraefikParams helmParams(HelmParams helmParams) {
    this.helmParams = helmParams;
    return this;
  }

  public HelmParams getHelmParams() {
    return helmParams;
  }

  public HashMap<String, Object> getValues() {
    HashMap<String, Object> values = new HashMap();
    values.put(NAMESPACES, nameSpaces);

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