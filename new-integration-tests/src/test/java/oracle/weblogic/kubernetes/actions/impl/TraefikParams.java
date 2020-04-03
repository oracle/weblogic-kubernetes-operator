// Copyright (c) 2020, Oracle Corporation and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.weblogic.kubernetes.actions.impl;

import java.util.HashMap;

import oracle.weblogic.kubernetes.actions.impl.primitive.HelmParams;

// All parameters needed to install Traefik Operator

public class TraefikParams extends HelmParams {

  // Adding some of the most commonly used params for now
  private int nodePortsHttp;
  private int nodePortsHttps;

  public TraefikParams releaseName(String releaseName) {
    this.releaseName = releaseName;
    return this;
  }

  public TraefikParams namespace(String namespace) {
    this.namespace = namespace;
    return this;
  }

  public TraefikParams repoUrl(String repoUrl) {
    this.repoUrl = repoUrl;
    return this;
  }

  public TraefikParams chartName(String chartName) {
    this.chartName = chartName;
    return this;
  }

  public TraefikParams chartDir(String chartDir) {
    this.chartDir = chartDir;
    return this;
  }

  public TraefikParams nodePortsHttp(int nodePortsHttp) {
    this.nodePortsHttp = nodePortsHttp;
    return this;
  }

  public TraefikParams nodePortsHttps(int nodePortsHttps) {
    this.nodePortsHttps = nodePortsHttps;
    return this;
  }

  public HashMap<String, Object> getValues() {
    // add all params into map ?
    return new HashMap<String, Object>();
  }

}