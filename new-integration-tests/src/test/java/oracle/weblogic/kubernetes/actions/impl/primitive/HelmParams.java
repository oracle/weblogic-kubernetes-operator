// Copyright (c) 2020, Oracle Corporation and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.weblogic.kubernetes.actions.impl.primitive;

import java.util.HashMap;

// All common parameters needed to install a helm application

public abstract class HelmParams {

  // Adding some of the most commonly used params for now
  protected String releaseName;
  protected String namespace;
  protected String repoUrl;
  protected String chartName;
  protected String chartDir;

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

  public abstract HashMap<String, Object> getValues();
}