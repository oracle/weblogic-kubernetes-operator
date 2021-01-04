// Copyright (c) 2020, 2021, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.weblogic.kubernetes.actions.impl;

import oracle.weblogic.kubernetes.actions.impl.primitive.Helm;
import oracle.weblogic.kubernetes.actions.impl.primitive.HelmParams;

public class Prometheus {
  /**
   * install Prometheus.
   * @param params the helm parameters like namespace, release name, repo url or chart dir,
   *               chart name and chart values to override
   * @return true on success, false otherwise
   */
  public static boolean install(PrometheusParams params) {
    return Helm.install(params.getHelmParams(), params.getValues());
  }

  /**
   * Uninstall a Prometheus release.
   * @param params the parameters to helm uninstall command, release name and namespace
   * @return true on success, false otherwise
   */
  public static boolean uninstall(HelmParams params) {
    return Helm.uninstall(params);
  }

}
