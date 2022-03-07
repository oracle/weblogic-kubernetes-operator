// Copyright (c) 2020, 2022, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.weblogic.kubernetes.actions.impl;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import io.kubernetes.client.openapi.ApiException;
import io.kubernetes.client.openapi.models.V1HTTPIngressPath;
import io.kubernetes.client.openapi.models.V1HTTPIngressRuleValue;
import io.kubernetes.client.openapi.models.V1Ingress;
import io.kubernetes.client.openapi.models.V1IngressBackend;
import io.kubernetes.client.openapi.models.V1IngressList;
import io.kubernetes.client.openapi.models.V1IngressRule;
import io.kubernetes.client.openapi.models.V1IngressServiceBackend;
import io.kubernetes.client.openapi.models.V1IngressSpec;
import io.kubernetes.client.openapi.models.V1ObjectMeta;
import io.kubernetes.client.openapi.models.V1ServiceBackendPort;
import oracle.weblogic.kubernetes.actions.impl.primitive.Helm;
import oracle.weblogic.kubernetes.actions.impl.primitive.HelmParams;
import oracle.weblogic.kubernetes.actions.impl.primitive.Kubernetes;

import static oracle.weblogic.kubernetes.utils.ThreadSafeLogger.getLogger;

/**
 * Utility class for NGINX ingress controller.
 */
public class Nginx {

  private static final String INGRESS_API_VERSION = "networking.k8s.io/v1";
  private static final String INGRESS_KIND = "Ingress";

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
   * The ingress host is set to 'domainUid.clusterName.test'.
   *
   * @param ingressName name of the ingress to be created
   * @param ingressClassName Ingress class name
   * @param domainNamespace the WebLogic domain namespace in which the ingress will be created
   * @param domainUid the WebLogic domainUid which is backend to the ingress
   * @param clusterNameMsPortMap the map with key as cluster name and value as managed server port of the cluster
   * @return list of ingress hosts or null if got ApiException when calling Kubernetes client API to create ingress
   */
  public static List<String> createIngress(String ingressName,
                                      String ingressClassName,
                                      String domainNamespace,
                                      String domainUid,
                                      Map<String, Integer> clusterNameMsPortMap) {
    return createIngress(ingressName, ingressClassName, domainNamespace, domainUid, clusterNameMsPortMap, true);
  }

  /**
   * Create an ingress for the WebLogic domain with domainUid in the specified domain namespace.
   * The ingress host is set to 'domainUid.domainNamespace.clusterName.test'.
   *
   * @param ingressName name of the ingress to be created
   * @param ingressClassName Ingress class name
   * @param domainNamespace the WebLogic domain namespace in which the ingress will be created
   * @param domainUid the WebLogic domainUid which is backend to the ingress
   * @param clusterNameMsPortMap the map with key as cluster name and value as managed server port of the cluster
   * @param setIngressHost if false will set to any
   * @return list of ingress hosts or null if got ApiException when calling Kubernetes client API to create ingress
   */
  public static List<String> createIngress(String ingressName,
                                           String ingressClassName,
                                           String domainNamespace,
                                           String domainUid,
                                           Map<String, Integer> clusterNameMsPortMap,
                                           boolean setIngressHost) {

    List<String> ingressHostList = new ArrayList<>();
    ArrayList<V1IngressRule> ingressRules = new ArrayList<>();
    clusterNameMsPortMap.forEach((clusterName, managedServerPort) -> {
      // set the http ingress paths
      V1HTTPIngressPath httpIngressPath = new V1HTTPIngressPath()
              .path(null)
              .backend(new V1IngressBackend()
                  .service(new V1IngressServiceBackend()
                      .name(domainUid + "-cluster-" + clusterName.toLowerCase().replace("_", "-"))
                      .port(new V1ServiceBackendPort()
                          .number(managedServerPort)))
              );
      ArrayList<V1HTTPIngressPath> httpIngressPaths = new ArrayList<>();
      httpIngressPaths.add(httpIngressPath);

      // set the ingress rule
      String ingressHost = domainUid + "." + domainNamespace + "." + clusterName + ".test";
      if (!setIngressHost) {
        ingressHost = "";
        ingressHostList.add("*");
      }
      V1IngressRule ingressRule = new V1IngressRule()
              .host(ingressHost)
              .http(new V1HTTPIngressRuleValue()
                      .paths(httpIngressPaths));

      ingressRules.add(ingressRule);
      ingressHostList.add(ingressHost);

    });

    // set the ingress
    V1Ingress ingress = new V1Ingress()
            .apiVersion(INGRESS_API_VERSION)
            .kind(INGRESS_KIND)
            .metadata(new V1ObjectMeta()
                    .name(ingressName)
                    .namespace(domainNamespace))
            .spec(new V1IngressSpec()
                    .rules(ingressRules)
                .ingressClassName(ingressClassName));

    // create the ingress
    try {
      Kubernetes.createIngress(domainNamespace, ingress);
    } catch (ApiException apex) {
      getLogger().severe("got ApiException while calling createIngress: {0}", apex.getResponseBody());
      return null;
    }
    return ingressHostList;
  }

  /**
   * List all of the ingresses in the specified namespace.
   *
   * @param namespace the namespace to which the ingresses belong
   * @return a list of ingress names in the namespace
   * @throws ApiException if Kubernetes client API call fails
   */
  public static List<String> listIngresses(String namespace) throws ApiException {

    List<String> ingressNames = new ArrayList<>();
    V1IngressList ingressList = Kubernetes.listNamespacedIngresses(namespace);
    List<V1Ingress> listOfIngress = ingressList.getItems();

    listOfIngress.forEach(ingress -> {
      if (ingress.getMetadata() != null) {
        ingressNames.add(ingress.getMetadata().getName());
      }
    });

    return ingressNames;
  }
}
