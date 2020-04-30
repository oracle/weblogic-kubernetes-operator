// Copyright (c) 2020, Oracle Corporation and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.weblogic.kubernetes.actions.impl;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import io.kubernetes.client.openapi.ApiException;
import io.kubernetes.client.openapi.models.ExtensionsV1beta1Ingress;
import io.kubernetes.client.openapi.models.ExtensionsV1beta1IngressList;
import oracle.weblogic.kubernetes.actions.impl.primitive.Helm;
import oracle.weblogic.kubernetes.actions.impl.primitive.HelmParams;
import oracle.weblogic.kubernetes.actions.impl.primitive.Kubernetes;

import static oracle.weblogic.kubernetes.TestConstants.INGRESS_SAMPLE_CHART_DIR;

/**
 * Utility class for Nginx ingress controller.
 */
public class Nginx {
  /**
   * Install Helm chart.
   *
   * @param params the parameters to Helm install command such as namespace, release name, repo url or chart dir,
   *               chart name and chart values
   * @return true on success, false otherwise
   */
  public static boolean install(NginxParams params) {
    return Helm.install(params.getHelmParams(), params.getValues());
  }

  /**
   * Upgrade a Helm release.
   *
   * @param params the parameters to Helm upgrade command such as namespace, release name and chart values to override
   * @return true on success, false otherwise
   */
  public static boolean upgrade(NginxParams params) {
    return Helm.upgrade(params.getHelmParams(), params.getValues());
  }

  /**
   * Uninstall a Helm release.
   *
   * @param params the parameters to Helm uninstall command such as release name and namespace
   * @return true on success, false otherwise
   */
  public static boolean uninstall(HelmParams params) {
    return Helm.uninstall(params);
  }

  /**
   * Create an ingress for the WebLogic domain with domainUid in the specified domain namespace.
   *
   * @param domainNamespace the WebLogic domain namespace in which to create the ingress
   * @param domainUid the WebLogic domainUid which is backend to the ingress
   * @return true on success, false otherwise
   */
  public static boolean createIngress(String domainNamespace, String domainUid) {
    HelmParams ingressParam = new HelmParams()
        .releaseName(domainUid + "-ingress")
        .namespace(domainNamespace)
        .chartDir(INGRESS_SAMPLE_CHART_DIR);

    HashMap<String, Object> values = new HashMap();
    values.put("wlsDomain.domainUID", domainUid);
    values.put("nginx.hostname", domainUid + ".org");

    Helm.install(ingressParam, values);
    return true;
  }

  /**
   * Delete an ingress which name containing the specified domainUid in the specified domain namespace.
   *
   * @param domainNamespace the domain namespace which contains the ingress to delete
   * @param domainUid the WebLogic domainUid which is backend to the ingress
   * @return true on success, false otherwise
   */
  public static boolean deleteIngress(String domainNamespace, String domainUid) {
    HelmParams ingressParam = new HelmParams()
        .releaseName(domainUid + "-ingress")
        .namespace(domainNamespace);
    return Helm.uninstall(ingressParam);
  }

  /**
   * Get a list of ingresses in the specified namespace.
   *
   * @param namespace the namespace to which the ingresses belong
   * @return a list of ingresses in the namespace
   */
  public static List<String> getIngressList(String namespace) throws ApiException {

    List<String> ingressNames = new ArrayList<>();
    ExtensionsV1beta1IngressList ingressList = Kubernetes.listIngress(namespace);
    List<ExtensionsV1beta1Ingress> listOfIngress = ingressList.getItems();

    listOfIngress.forEach(ingress -> {
      ingressNames.add(ingress.getMetadata().getName());

    });
    return ingressNames;
  }
}
