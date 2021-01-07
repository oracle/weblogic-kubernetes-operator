// Copyright (c) 2020, 2021, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.weblogic.kubernetes.actions.impl;

import oracle.weblogic.kubernetes.actions.impl.primitive.Helm;
import oracle.weblogic.kubernetes.actions.impl.primitive.HelmParams;

/**
 * Utility class for Voyager ingress controller.
 */
public class Voyager {
  /**
   * Install Voyager Helm chart.
   *
   * @param params the parameters to Helm install command such as release name, namespace, repo url or chart dir,
   *               chart name and chart values
   * @return true on success, false otherwise
   */
  public static boolean install(VoyagerParams params) {
    return Helm.install(params.getHelmParams(), params.getValues());
  }

  /**
   * Upgrade Voyager Helm release.
   *
   * @param params the parameters to Helm upgrade command such as release name, namespace and chart values to override
   * @return true on success, false otherwise
   */
  public static boolean upgrade(VoyagerParams params) {
    return Helm.upgrade(params.getHelmParams(), params.getValues());
  }

  /**
   * Uninstall Voyager Helm release.
   *
   * @param params the parameters to Helm uninstall command such as release name and namespace
   * @return true on success, false otherwise
   */
  public static boolean uninstall(HelmParams params) {
    return Helm.uninstall(params);
  }
}
