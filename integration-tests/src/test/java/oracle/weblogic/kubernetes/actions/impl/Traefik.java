// Copyright (c) 2020, 2021, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.weblogic.kubernetes.actions.impl;

import oracle.weblogic.kubernetes.actions.impl.primitive.Helm;
import oracle.weblogic.kubernetes.actions.impl.primitive.HelmParams;

import static oracle.weblogic.kubernetes.actions.impl.primitive.Helm.exec;
import static oracle.weblogic.kubernetes.actions.impl.primitive.Helm.valuesToString;

public class Traefik {
  /**
   * install helm chart.
   * @param params the helm parameters like namespace, release name, repo url or chart dir,
   *               chart name and chart values to override
   * @return true on success, false otherwise
   */
  public static boolean install(TraefikParams params) {
    return Helm.install(params.getHelmParams(), params.getValues());
  }

  /**
   * Upgrade a helm release.
   * @param params the helm parameters like namespace, release name, repo url or chart dir,
   *               chart name and chart values to override
   * @return true on success, false otherwise
   */
  public static boolean upgrade(TraefikParams params) {
    return Helm.upgrade(params.getHelmParams(), params.getValues());
  }

  /**
   * Upgrade a helm release.
   * @param params the helm parameters to override. This method is mainly to upgrade
   *               Traefik image related infor, such as image.repository, image.registry,
   *               and image.tag. See TraefikParams getValues() for more details
   * @return true on success, false otherwise
   */
  public static boolean upgradeTraefikImage(TraefikParams params) {

    HelmParams helmParams = params.getHelmParams();
    String chartRef = null;

    if (helmParams.getRepoUrl() != null && helmParams.getChartName() != null) {
      if (helmParams.getRepoName() != null) {
        chartRef = helmParams.getRepoName() + "/" + helmParams.getChartName();
      } else {
        chartRef = helmParams.getChartName() + " --repo " + helmParams.getRepoUrl();
      }
    }

    // build Helm upgrade command
    String upgradeCmd = String.format("helm upgrade %1s %2s --namespace %3s --reuse-values",
        helmParams.getReleaseName(), chartRef,
        helmParams.getNamespace());

    // add override values
    upgradeCmd = upgradeCmd + valuesToString(params.getValues());

    return exec(upgradeCmd);
  }



  /**
   * Uninstall a helm release.
   * @param params the parameters to helm uninstall command, release name and namespace
   * @return true on success, false otherwise
   */
  public static boolean uninstall(HelmParams params) {
    return Helm.uninstall(params);
  }

  public static boolean createIngress(String valuesYaml) {
    return true;
  }
}
