// Copyright (c) 2020, Oracle Corporation and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.weblogic.kubernetes.actions.impl;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import io.kubernetes.client.custom.IntOrString;
import io.kubernetes.client.openapi.ApiException;
import io.kubernetes.client.openapi.models.ExtensionsV1beta1HTTPIngressPath;
import io.kubernetes.client.openapi.models.ExtensionsV1beta1HTTPIngressRuleValue;
import io.kubernetes.client.openapi.models.ExtensionsV1beta1Ingress;
import io.kubernetes.client.openapi.models.ExtensionsV1beta1IngressBackend;
import io.kubernetes.client.openapi.models.ExtensionsV1beta1IngressList;
import io.kubernetes.client.openapi.models.ExtensionsV1beta1IngressRule;
import io.kubernetes.client.openapi.models.ExtensionsV1beta1IngressSpec;
import io.kubernetes.client.openapi.models.V1ObjectMeta;
import oracle.weblogic.kubernetes.actions.impl.primitive.Helm;
import oracle.weblogic.kubernetes.actions.impl.primitive.HelmParams;
import oracle.weblogic.kubernetes.actions.impl.primitive.Kubernetes;

import static oracle.weblogic.kubernetes.extensions.LoggedTest.logger;

/**
 * Utility class for NGINX ingress controller.
 */
public class Nginx {

  private static final String INGRESS_API_VERSION = "extensions/v1beta1";
  private static final String INGRESS_KIND = "Ingress";
  private static final String INGRESS_NGINX_CLASS = "nginx";

  /**
   * Install NGINX Helm chart.
   *
   * @param params the parameters to Helm install command such as release name, namespace, repo url or chart dir,
   *               chart name and chart values
   * @return true on success, false otherwise
   */
  public static boolean install(NginxParams params) {
    return Helm.install(params.getHelmParams(), params.getValues());
  }

  /**
   * Upgrade NGINX Helm release.
   *
   * @param params the parameters to Helm upgrade command such as release name, namespace and chart values to override
   * @return true on success, false otherwise
   */
  public static boolean upgrade(NginxParams params) {
    return Helm.upgrade(params.getHelmParams(), params.getValues());
  }

  /**
   * Uninstall NGINX Helm release.
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
   * @param domainNamespace the WebLogic domain namespace in which the ingress will be created
   * @param domainUid the WebLogic domainUid which is backend to the ingress
   * @param clusterName the name of the WebLogic domain cluster
   * @param managedServerPort the port number of the WebLogic domain managed servers
   * @return true on success, false otherwise
   * @throws ApiException if Kubernetes client API call fails
   */
  public static boolean createIngress(String domainNamespace,
                                      String domainUid,
                                      String clusterName,
                                      int managedServerPort) throws ApiException {

    // set the annotation for kubernetes.io/ingress.class to "nginx"
    HashMap<String, String> annotation = new HashMap<>();
    annotation.put("kubernetes.io/ingress.class", INGRESS_NGINX_CLASS);

    // set the http ingress paths
    ExtensionsV1beta1HTTPIngressPath httpIngressPath = new ExtensionsV1beta1HTTPIngressPath()
        .path(null)
        .backend(new ExtensionsV1beta1IngressBackend()
                .serviceName(domainUid + "-cluster-" + clusterName.toLowerCase().replace("_", "-"))
                .servicePort(new IntOrString(managedServerPort))
        );
    ArrayList<ExtensionsV1beta1HTTPIngressPath> httpIngressPaths = new ArrayList<>();
    httpIngressPaths.add(httpIngressPath);

    // set the ingress rule
    ExtensionsV1beta1IngressRule ingressRule = new ExtensionsV1beta1IngressRule()
        .host(domainUid + ".test")
        .http(new ExtensionsV1beta1HTTPIngressRuleValue()
              .paths(httpIngressPaths)
        );
    ArrayList<ExtensionsV1beta1IngressRule> ingressRules = new ArrayList<>();
    ingressRules.add(ingressRule);

    // set the ingress
    ExtensionsV1beta1Ingress ingress = new ExtensionsV1beta1Ingress()
        .apiVersion(INGRESS_API_VERSION)
        .kind(INGRESS_KIND)
        .metadata(new V1ObjectMeta()
                  .name(domainUid + "-nginx")
                  .namespace(domainNamespace)
                  .annotations(annotation))
        .spec(new ExtensionsV1beta1IngressSpec()
              .rules(ingressRules));

    // create the ingress
    try {
      Kubernetes.createIngress(domainNamespace, ingress);
    } catch (ApiException apex) {
      logger.warning(apex.getResponseBody());
      throw apex;
    }
    return true;
  }

  /**
   * Get a list of ingress names in the specified namespace.
   *
   * @param namespace the namespace to which the ingresses belong
   * @return a list of ingress names in the namespace
   * @throws ApiException if Kubernetes client API call fails
   */
  public static List<String> getIngressList(String namespace) throws ApiException {

    List<String> ingressNames = new ArrayList<>();
    ExtensionsV1beta1IngressList ingressList = Kubernetes.listNamespacedIngresses(namespace);
    List<ExtensionsV1beta1Ingress> listOfIngress = ingressList.getItems();

    listOfIngress.forEach(ingress -> {
      if (ingress.getMetadata() != null) {
        ingressNames.add(ingress.getMetadata().getName());
      }
    });

    return ingressNames;
  }
}
