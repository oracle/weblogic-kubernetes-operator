// Copyright (c) 2020, Oracle Corporation and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.weblogic.kubernetes.actions.impl.primitive;

import java.util.HashMap;

public class Helm {

  private String chartName;
  private String releaseName;
  private String namespace;
  private String repoUrl;
  private HashMap<String, String> values;

  public Helm chartName(String chartName) {
    this.chartName = chartName;
    return this;
  }

  public Helm releaseName(String releaseName) {
    this.releaseName = releaseName;
    return this;
  }

  public Helm namespace(String namespace) {
    this.namespace = namespace;
    return this;
  }

  public Helm repoUrl(String repoUrl) {
    this.repoUrl = repoUrl;
    return this;
  }

  public Helm values(HashMap<String, String> values) {
    this.values = values;
    return this;
  }

  public String getChartName() {
    return chartName;
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

  public HashMap<String, String> getValues() {
    return values;
  }

  public boolean install() {
    return true;
  }

  public boolean upgrade() {
    return true;
  }

  public boolean delete() {
    return true;
  }

  public boolean addRepo() {
    String addRepoCmd = "helm add repo ";
    return true;
  }


}
