// Copyright (c) 2020, Oracle Corporation and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.weblogic.kubernetes.actions.impl.primitive;

import oracle.weblogic.kubernetes.extensions.LoggedTest;

import static org.assertj.core.api.Assertions.assertThat;

public class Helm implements LoggedTest {

  /**
   * install helm chart
   * @param params the parameters to helm as values
   * @return true on success, false otherwise
   */
  public static boolean install(HelmParams params) {
    String namespace = params.getNamespace();

    // assertions for required parameters
    assertThat(namespace)
        .as("make sure namespace is not empty or null")
        .isNotNull()
        .isNotEmpty();

    assertThat(params.getReleaseName())
        .as("make sure releaseName is not empty or null")
        .isNotNull()
        .isNotEmpty();

    boolean success = false;

    //chart reference to be used in helm install
    String chartRef = params.getChartDir();

    // use repo url as chart reference if provided
    if (params.getChartName() != null && params.getRepoUrl() != null) {
      Helm.addRepo(params.getChartName(), params.getRepoUrl());
      chartRef = params.getRepoUrl();
    }

    logger.info(String.format("Installing application in namespace %s using chart %s",
        namespace, chartRef));
    // build helm install command
    StringBuffer installCmd = new StringBuffer("helm install ")
        .append(params.getReleaseName()).append(" ").append(chartRef);

    // add all the parameters
    params.getValues().forEach((key, value) ->
        installCmd.append(" --set ")
            .append(key)
            .append("=")
            .append(value.toString().replaceAll("\\[", "{").replaceAll("\\]", "}")));

    // run the command
    logger.info("Running command - \n" + installCmd);
    success = true;
    return success;

  }

  public static boolean upgrade(HelmParams params) {
    return true;
  }

  public static boolean delete(HelmParams params) {
    return true;
  }

  public static boolean addRepo(String chartName, String repoUrl) {
    String addRepoCmd = "helm add repo " + chartName + " " + repoUrl;
    // execute addRepoCmd
    return true;
  }


}
