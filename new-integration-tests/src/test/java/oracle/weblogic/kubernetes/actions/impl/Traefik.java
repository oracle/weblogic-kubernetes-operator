// Copyright (c) 2020, Oracle Corporation and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.weblogic.kubernetes.actions.impl;

import oracle.weblogic.kubernetes.actions.impl.primitive.Helm;

public class Traefik {
  /**
   * The URL of the Traefik's Helm Repository.
   */
  private static String TRAEFIK_HELM_REPO_URL = "";
  /**
   * The name of the Traefik Helm Chart (in the repository).
   */
  private static String TRAEFIK_CHART_NAME = "traefik";

  /**
   * Install Traefik Operator.
   *
   * @param params parameters for helm values
   * @return true on success, false otherwise
   */
  public static boolean install(TraefikParams params) {
    boolean success = false;
    if (new Helm().chartName(TRAEFIK_CHART_NAME).repoUrl(TRAEFIK_HELM_REPO_URL).addRepo()) {
      //logger.info(String.format("Installing Traefik Operator in namespace %s", namespace));
      success = new Helm().chartName(TRAEFIK_CHART_NAME)
          .releaseName(params.getReleaseName())
          .namespace(params.getNamespace())
          .values(params.getValues())
          .install();
    }
    return success;

  }

  public static boolean createIngress(String valuesYaml) {
    return true;
  }
}
