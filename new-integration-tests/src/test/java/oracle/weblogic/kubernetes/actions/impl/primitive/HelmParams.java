// Copyright (c) 2020, Oracle Corporation and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.weblogic.kubernetes.actions.impl.primitive;

// All common parameters needed to install a Helm application

public class HelmParams {

  // Adding some of the most commonly used params for now
  private String releaseName;
  private String namespace;
  private String repoUrl;
  private String chartName;
  private String chartDir;
  private String filter;

  public HelmParams releaseName(String releaseName) {
    this.releaseName = releaseName;
    return this;
  }

  public HelmParams namespace(String namespace) {
    this.namespace = namespace;
    return this;
  }

  public HelmParams repoUrl(String repoUrl) {
    this.repoUrl = repoUrl;
    return this;
  }

  public HelmParams chartName(String chartName) {
    this.chartName = chartName;
    return this;
  }

  public HelmParams chartDir(String chartDir) {
    this.chartDir = chartDir;
    return this;
  }

  public HelmParams filter(String filter) {
    this.filter = filter;
    return this;
  }

  public String getReleaseName() {
    return releaseName;
  }

  public String getNamespace() {
    return namespace;
  }

  public String getRepoUrl() {
    return repoUrl;
  }

  public String getChartName() {
    return chartName;
  }

  public String getChartDir() {
    return chartDir;
  }

  public String getFilter() {
    return filter;
  }
}