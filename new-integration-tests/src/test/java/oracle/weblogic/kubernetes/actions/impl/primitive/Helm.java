// Copyright (c) 2020, Oracle Corporation and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.weblogic.kubernetes.actions.impl.primitive;

import java.util.HashMap;

import oracle.weblogic.kubernetes.extensions.LoggedTest;

public class Helm implements LoggedTest {

  private String chartName;
  private String releaseName;
  private String namespace;
  private String repoUrl;
  private HashMap<String, Object> values;

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

  public Helm values(HashMap<String, Object> values) {
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

  public HashMap<String, Object> getValues() {
    return values;
  }

  public boolean install() {
    StringBuffer installCmd = new StringBuffer("helm install ")
                              .append(releaseName).append(" ").append(chartName);
    values.forEach((key, value) ->
                    installCmd.append(" --set ")
                              .append(key)
                              .append("=")
                              .append(value.toString().replaceAll("\\[","{").replaceAll("\\]","}")));
    logger.info("Running helm install command " + installCmd);
    return true;
  }

  public boolean upgrade() {
    return true;
  }

  public boolean delete() {
    return true;
  }

  public boolean addRepo() {
    String addRepoCmd = "helm add repo " + chartName + " " + repoUrl;
    // execute addRepoCmd
    return true;
  }


}
