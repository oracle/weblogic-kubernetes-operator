// Copyright (c) 2020, Oracle Corporation and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.weblogic.kubernetes.actions.impl;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

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
import oracle.weblogic.kubernetes.actions.impl.primitive.Kubernetes;

import static oracle.weblogic.kubernetes.actions.ActionConstants.INGRESS_API_VERSION;
import static oracle.weblogic.kubernetes.actions.ActionConstants.INGRESS_KIND;
import static oracle.weblogic.kubernetes.extensions.LoggedTest.logger;

/**
 * Utility class for ingress resource .
 */
public class Ingress {

  /**
   * Create an ingress for the WebLogic domain with domainUid in the specified domain namespace.
   * The ingress host is set to 'domainUid.clusterName.test'.
   *
   * @param ingressName name of the ingress to be created
   * @param domainNamespace the WebLogic domain namespace in which the ingress will be created
   * @param domainUid the WebLogic domainUid which is backend to the ingress
   * @param clusterNameMsPortMap the map with key as cluster name and value as managed server port of the cluster
   * @param annotations annotations to create ingress resource
   * @return list of ingress hosts or null if got ApiException when calling Kubernetes client API to create ingress
   */
  public static List<String> createIngress(String ingressName,
                                           String domainNamespace,
                                           String domainUid,
                                           Map<String, Integer> clusterNameMsPortMap,
                                           Map<String, String> annotations) {

    List<String> ingressHostList = new ArrayList<>();
    ArrayList<ExtensionsV1beta1IngressRule> ingressRules = new ArrayList<>();
    clusterNameMsPortMap.forEach((clusterName, managedServerPort) -> {
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
      String ingressHost = domainUid + "." + clusterName + ".test";
      ExtensionsV1beta1IngressRule ingressRule = new ExtensionsV1beta1IngressRule()
          .host(ingressHost)
          .http(new ExtensionsV1beta1HTTPIngressRuleValue()
              .paths(httpIngressPaths));

      ingressRules.add(ingressRule);
      ingressHostList.add(ingressHost);
    });

    // set the ingress
    ExtensionsV1beta1Ingress ingress = new ExtensionsV1beta1Ingress()
        .apiVersion(INGRESS_API_VERSION)
        .kind(INGRESS_KIND)
        .metadata(new V1ObjectMeta()
            .name(ingressName)
            .namespace(domainNamespace)
            .annotations(annotations))
        .spec(new ExtensionsV1beta1IngressSpec()
            .rules(ingressRules));

    // create the ingress
    try {
      Kubernetes.createIngress(domainNamespace, ingress);
    } catch (ApiException apex) {
      logger.severe("got ApiException while calling createIngress: {0}", apex.getResponseBody());
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
