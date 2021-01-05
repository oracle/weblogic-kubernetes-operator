// Copyright (c) 2020, 2021, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.weblogic.kubernetes.actions.impl.primitive;

import java.util.Map;

import oracle.weblogic.kubernetes.utils.ExecCommand;
import oracle.weblogic.kubernetes.utils.ExecResult;

import static oracle.weblogic.kubernetes.utils.ThreadSafeLogger.getLogger;
import static org.assertj.core.api.Assertions.assertThat;

public class Helm {

  /**
   * Installs a Helm chart.
   * @param helmParams the parameters to Helm install command like namespace, release name,
   *                   repo url or chart dir, chart name
   * @param chartValues the values to override in a chart
   * @return true on success, false otherwise
   */
  public static boolean install(HelmParams helmParams, Map<String, Object> chartValues) {
    String namespace = helmParams.getNamespace();

    // assertions for required parameters
    assertThat(namespace)
        .as("make sure namespace is not empty or null")
        .isNotNull()
        .isNotEmpty();

    assertThat(helmParams.getReleaseName())
        .as("make sure releaseName is not empty or null")
        .isNotNull()
        .isNotEmpty();

    assertThat(helmParams.getRepoUrl() != null && helmParams.getChartName() == null)
        .as("make sure chart name is not empty or null when repo url is provided")
        .isFalse();

    assertThat(helmParams.getRepoUrl() == null && helmParams.getChartDir() == null)
        .as("make sure repo url, chart name and chart dir are not empty or null. "
            + "repo url, chart name or chart dir must be provided")
        .isFalse();

    //chart reference to be used in Helm install
    String chartRef = helmParams.getChartDir();

    // use repo url as chart reference if provided
    if (helmParams.getRepoUrl() != null && helmParams.getChartName() != null) {
      if (helmParams.getRepoName() != null) {
        // call 'helm repo add <repo_name> <repo_url>' first to add the repo
        addRepo(helmParams.getRepoName(), helmParams.getRepoUrl());
        updateRepo();
        chartRef = helmParams.getRepoName() + "/" + helmParams.getChartName();
      } else {
        chartRef = helmParams.getChartName() + " --repo " + helmParams.getRepoUrl();
      }
    }

    getLogger().fine("Installing a chart in namespace {0} using chart reference {1}", namespace, chartRef);

    // build Helm install command
    String installCmd = String.format("helm install %1s %2s --namespace %3s ",
        helmParams.getReleaseName(), chartRef, helmParams.getNamespace());

    // if we have chart values file
    String chartValuesFile = helmParams.getChartValuesFile();
    if (chartValuesFile != null) {
      installCmd = installCmd + " --values " + chartValuesFile;
    }

    // if we have chart version
    String chartVersion = helmParams.getChartVersion();
    if (chartVersion != null) {
      installCmd = installCmd + " --version " + chartVersion;
    }

    // add override chart values
    installCmd = installCmd + valuesToString(chartValues);

    // run the command
    return exec(installCmd);
  }

  /**
   * Upgrade a Helm release.
   * @param params the parameters to Helm upgrade command such as namespace, release name,
   *               repo url or chart dir and chart name
   * @param chartValues the values to override in a chart
   * @return true on success, false otherwise
   */
  public static boolean upgrade(HelmParams params, Map<String, Object> chartValues) {
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

    // chart reference to be used in Helm upgrade
    String chartDir = params.getChartDir();

    getLogger().fine("Upgrading a release in namespace {0} using chart reference {1}", namespace, chartDir);

    // build Helm upgrade command
    String upgradeCmd = String.format("helm upgrade %1s %2s --namespace %3s --reuse-values",
        params.getReleaseName(), chartDir, params.getNamespace());

    // add override chart values
    upgradeCmd = upgradeCmd + valuesToString(chartValues);

    // run the command
    return exec(upgradeCmd);
  }

  /**
   * Uninstall a Helm release.
   * @param params the parameters to Helm uninstall command, release name and namespace
   * @return true on success, false otherwise
   */
  public static boolean uninstall(HelmParams params) {
    // assertions for required parameters
    assertThat(params.getNamespace())
        .as("make sure namespace is not empty or null")
        .isNotNull()
        .isNotEmpty();

    assertThat(params.getReleaseName())
        .as("make sure releaseName is not empty or null")
        .isNotNull()
        .isNotEmpty();

    getLogger().fine("Uninstalling release {0} in namespace {1}", params.getReleaseName(), params.getNamespace());

    String uninstallCmd = String.format("helm uninstall %1s -n %2s", params.getReleaseName(),
        params.getNamespace());
    return exec(uninstallCmd);
  }

  /**
   * List releases.
   * @param params namespace
   * @return true on success
   */
  public static boolean list(HelmParams params) {
    // assertions for required parameters
    assertThat(params.getNamespace())
        .as("make sure namespace is not empty or null")
        .isNotNull()
        .isNotEmpty();

    return exec(String.format("helm list -n %s", params.getNamespace()));
  }

  /**
   * Add a chart repository.
   * @param repoName the name of the repo
   * @param repoUrl repository url
   * @return true on success, false otherwise
   */
  public static boolean addRepo(String repoName, String repoUrl) {
    String addRepoCmd = "helm repo add " + repoName + " " + repoUrl;
    return exec(addRepoCmd);
  }

  /**
   * Update a chart repository.
   * @return true on success, false otherwise
   */
  public static boolean updateRepo() {
    String updateRepoCmd = "helm repo update ";
    return exec(updateRepoCmd);
  }

  /**
   * Append the helmValues to the given string buffer.
   * @param helmValues hash map with key, value pairs
   * @return string with chart helmValues
   */
  public static String valuesToString(Map<String, Object> helmValues) {
    StringBuffer valuesString = new StringBuffer("");
    // values can be Map or String
    for (Map.Entry<String,Object> entry : helmValues.entrySet()) {
      if (entry.getValue() instanceof Map) {
        Map<String, Object> item = (Map<String, Object>) entry.getValue();
        int index = 0;
        for (Map.Entry<String,Object> itemEntry : item.entrySet()) {
          valuesString.append(" --set \"" + entry.getKey() + "[" + index + "]."
              + itemEntry.getKey() + "=" + itemEntry.getValue() + "\"");
          ++index;
        }
      } else {
        valuesString.append(String.format(" --set \"%s=%s\"",
            entry.getKey(), entry.getValue().toString()
                .replaceAll("\\[", "{")
                .replaceAll("\\]", "}").replace(" ","")));
      }
    }
    return valuesString.toString();
  }

  /**
   * Executes the given command.
   * @param command the command to execute
   * @return true on success, false otherwise
   */
  private static boolean exec(String command) {
    getLogger().info("Running command - \n" + command);
    try {
      ExecResult result = ExecCommand.exec(command, true);
      getLogger().info("The command returned exit value: "
          + result.exitValue() + " command output: "
          + result.stderr() + "\n" + result.stdout());
      if (result.exitValue() != 0) {
        getLogger().info("Command failed with errors " + result.stderr() + "\n" + result.stdout());
        return false;
      }
    } catch (Exception e) {
      getLogger().info("Got exception, command failed with errors " + e.getMessage());
      return false;
    }
    return true;
  }
}
