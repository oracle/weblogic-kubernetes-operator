// Copyright (c) 2020, Oracle Corporation and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.weblogic.kubernetes.actions.impl;

import java.util.HashMap;

import oracle.weblogic.kubernetes.actions.impl.primitive.Helm;
import oracle.weblogic.kubernetes.actions.impl.primitive.HelmParams;

public class Traefik {
  /**
   * install helm chart
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
   * Uninstall a helm release.
   * @param params the parameters to helm uninstall command, release name and namespace
   * @return true on success, false otherwise
   */
  public static boolean uninstall(HelmParams params) {
    return Helm.uninstall(params);
  }

  /**
   * Create a ingress per domain
   * @param params the params to helm install command, releaseName, chartDir and WLS domain namespace
   * @param domainUID the weblogic domainUID to create the ingress
   * @param traefikHostname the hostname for the ingress
   * @return true on success, false otherwise
   */
  public static boolean createIngress(HelmParams params, String domainUID, String traefikHostname) {
    HashMap<String, Object> values = new HashMap();
    values.put("wlsDomain.domainUID", domainUID);
    values.put("traefik.hostname", traefikHostname);

    Helm.install(params, values);
    return true;
  }

  /**
   * Uninstall Ingress on a wls domain namespace
   * @param params the parameters to helm uninstall command, including release name and wls domain namespace containing
   *               the ingress
   * @return true on success, false otherwise
   */
  public static boolean uninstallIngress(HelmParams params) {
    return Helm.uninstall(params);
  }

}
