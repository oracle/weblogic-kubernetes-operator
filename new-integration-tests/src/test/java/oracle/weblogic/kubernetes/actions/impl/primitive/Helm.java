// Copyright (c) 2020, Oracle Corporation and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.weblogic.kubernetes.actions.impl.primitive;

import java.util.HashMap;

import oracle.weblogic.kubernetes.utils.ExecCommand;
import oracle.weblogic.kubernetes.utils.ExecResult;

import static oracle.weblogic.kubernetes.extensions.LoggedTest.logger;
import static org.assertj.core.api.Assertions.assertThat;

public class Helm {

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

    assertThat(params.getRepoUrl() != null && params.getChartName() == null)
        .as("make sure chart name is not empty or null when repo url is provided")
        .isFalse();

    assertThat(params.getRepoUrl() == null && params.getChartDir() == null)
        .as("make sure repo url, chart name and chart dir are not empty or null. "
            + "repo url, chart name or chart dir must be provided")
        .isFalse();

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
    appendValues(params.getValues(), installCmd);

    installCmd.append(" && helm list");
    // run the command
    return exec(installCmd.toString());

  }

  /**
   * upgrade a helm release
   * @param params the parameters to helm as values
   * @return true on success, false otherwise
   */
  public static boolean upgrade(HelmParams params) {
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

    assertThat(params.getChartDir())
        .as("make sure chart dir is not empty or null")
        .isNotNull()
        .isNotEmpty();

    //chart reference to be used in helm upgrade
    String chartDir = params.getChartDir();

    logger.info(String.format("Upgrade application in namespace %s using chart %s",
        namespace, chartDir));

    // build helm upgrade command
    StringBuffer upgradeCmd = new StringBuffer("helm upgrade ")
        .append(params.getReleaseName()).append(" ").append(chartDir);

    // add all the parameters
    appendValues(params.getValues(), upgradeCmd);

    upgradeCmd.append(" && helm list");
    // run the command
    return exec(upgradeCmd.toString());
  }

  /**
   * uninstall a helm release
   * @param params the parameters to helm as values
   * @return true on success, false otherwise
   */
  public static boolean delete(HelmParams params) {
    //ToDo: assert for chartName
    return exec("helm uninstall " + params.getReleaseName());
  }

  /**
   * Add a chart repository
   * @param chartName the name of the chart
   * @param repoUrl reposiroty url
   * @return true on success, false otherwise
   */
  public static boolean addRepo(String chartName, String repoUrl) {
    String addRepoCmd = "helm add repo " + chartName + " " + repoUrl;
    return exec(addRepoCmd);
  }

  /**
   * append the values to the given string buffer
   * @param values hash map with key, value pairs
   * @param command the command to append to
   */
  private static void appendValues(HashMap<String, Object> values, StringBuffer command) {
    values.forEach((key, value) ->
        command.append(" --set \"")
            .append(key)
            .append("=")
            .append(value.toString().replaceAll("\\[", "{").replaceAll("\\]", "}").replace(" ",""))
            .append("\""));
  }

  /**
   * Executes the given command
   * @param command the command to run
   * @return true on success, false otherwise
   */
  private static boolean exec(String command) {
    logger.info("Running command - \n" + command);
    try {
      ExecResult result = ExecCommand.exec(command, true);
      if (result.exitValue() != 0) {
        logger.info("Command failed with errors " + result.stderr() + "\n" + result.stdout());
        return false;
      }
    } catch (Exception e) {
      logger.info("Command failed with errors " + e.getMessage());
      return false;
    }
    return true;
  }

}
