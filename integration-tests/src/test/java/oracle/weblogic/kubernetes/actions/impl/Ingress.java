// Copyright (c) 2020, 2021, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.weblogic.kubernetes.actions.impl;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import io.kubernetes.client.custom.IntOrString;
import io.kubernetes.client.openapi.ApiException;
import io.kubernetes.client.openapi.models.NetworkingV1beta1HTTPIngressPath;
import io.kubernetes.client.openapi.models.NetworkingV1beta1HTTPIngressRuleValue;
import io.kubernetes.client.openapi.models.NetworkingV1beta1Ingress;
import io.kubernetes.client.openapi.models.NetworkingV1beta1IngressBackend;
import io.kubernetes.client.openapi.models.NetworkingV1beta1IngressList;
import io.kubernetes.client.openapi.models.NetworkingV1beta1IngressRule;
import io.kubernetes.client.openapi.models.NetworkingV1beta1IngressSpec;
import io.kubernetes.client.openapi.models.NetworkingV1beta1IngressTLS;
import io.kubernetes.client.openapi.models.V1ObjectMeta;
import oracle.weblogic.kubernetes.actions.impl.primitive.Kubernetes;

import static oracle.weblogic.kubernetes.actions.ActionConstants.INGRESS_API_VERSION;
import static oracle.weblogic.kubernetes.actions.ActionConstants.INGRESS_KIND;
import static oracle.weblogic.kubernetes.utils.ThreadSafeLogger.getLogger;

/**
 * Utility class for ingress resource .
 */
public class Ingress {

  /**
   * Create an ingress for the WebLogic domain with domainUid in the specified domain namespace.
   * The ingress hosts are set to
   * [domainUid.domainNamespace.adminserver.test, domainUid.domainNamespace.clusterName.test'].
   *
   * @param ingressName name of the ingress to be created
   * @param domainNamespace the WebLogic domain namespace in which the ingress will be created
   * @param domainUid the WebLogic domainUid which is backend to the ingress
   * @param clusterNameMsPortMap the map with key as cluster name and value as managed server port of the cluster
   * @param annotations annotations to create ingress resource
   * @param setIngressHost if false does not set ingress host
   * @param tlsSecret name of the TLS secret if any
   * @param enableAdminServerRouting enable the ingress rule to admin server
   * @param adminServerPort the port number of admin server pod of the domain
   * @return list of ingress hosts or null if got ApiException when calling Kubernetes client API to create ingress
   */
  public static List<String> createIngress(String ingressName,
                                           String domainNamespace,
                                           String domainUid,
                                           Map<String, Integer> clusterNameMsPortMap,
                                           Map<String, String> annotations,
                                           boolean setIngressHost,
                                           String tlsSecret,
                                           boolean enableAdminServerRouting,
                                           int adminServerPort) {

    List<String> ingressHostList = new ArrayList<>();
    ArrayList<NetworkingV1beta1IngressRule> ingressRules = new ArrayList<>();

    // set the ingress rule for admin server
    if (enableAdminServerRouting) {
      NetworkingV1beta1HTTPIngressPath httpIngressPath = new NetworkingV1beta1HTTPIngressPath()
          .path(null)
          .backend(new NetworkingV1beta1IngressBackend()
              .serviceName(domainUid + "-admin-server")
              .servicePort(new IntOrString(adminServerPort))
          );
      ArrayList<NetworkingV1beta1HTTPIngressPath> httpIngressPaths = new ArrayList<>();
      httpIngressPaths.add(httpIngressPath);

      // set the ingress rule
      String ingressHost = domainUid + "." + domainNamespace + ".adminserver.test";
      if (!setIngressHost) {
        ingressHost = "";
        ingressHostList.add("*");
      } else {
        ingressHostList.add(ingressHost);
      }
      NetworkingV1beta1IngressRule ingressRule = new NetworkingV1beta1IngressRule()
          .host(ingressHost)
          .http(new NetworkingV1beta1HTTPIngressRuleValue()
              .paths(httpIngressPaths));

      ingressRules.add(ingressRule);
    }

    // set the ingress rule for clusters
    clusterNameMsPortMap.forEach((clusterName, managedServerPort) -> {
      // set the http ingress paths
      NetworkingV1beta1HTTPIngressPath httpIngressPath1 = new NetworkingV1beta1HTTPIngressPath()
              .path(null)
              .backend(new NetworkingV1beta1IngressBackend()
                      .serviceName(domainUid + "-cluster-" + clusterName.toLowerCase().replace("_", "-"))
                      .servicePort(new IntOrString(managedServerPort))
              );
      ArrayList<NetworkingV1beta1HTTPIngressPath> httpIngressPaths1 = new ArrayList<>();
      httpIngressPaths1.add(httpIngressPath1);

      // set the ingress rule
      String ingressHost1 = domainUid + "." + domainNamespace + "." + clusterName + ".test";
      if (!setIngressHost) {
        ingressHost1 = "";
        ingressHostList.add("*");
      } else {
        ingressHostList.add(ingressHost1);
      }

      NetworkingV1beta1IngressRule ingressRule1 = new NetworkingV1beta1IngressRule()
              .host(ingressHost1)
              .http(new NetworkingV1beta1HTTPIngressRuleValue()
                      .paths(httpIngressPaths1));

      ingressRules.add(ingressRule1);
    });

    List<NetworkingV1beta1IngressTLS> tlsList = new ArrayList<>();
    if (tlsSecret != null) {
      clusterNameMsPortMap.forEach((clusterName, port) -> {
        tlsList.add(new NetworkingV1beta1IngressTLS()
            .hosts(Arrays.asList(
                domainUid + "." + domainNamespace + "." + clusterName + ".test"))
            .secretName(tlsSecret));
      });
    }

    // set the ingress
    NetworkingV1beta1Ingress ingress = new NetworkingV1beta1Ingress()
        .apiVersion(INGRESS_API_VERSION)
        .kind(INGRESS_KIND)
        .metadata(new V1ObjectMeta()
            .name(ingressName)
            .namespace(domainNamespace)
            .annotations(annotations))
        .spec(new NetworkingV1beta1IngressSpec()
            .rules(ingressRules));
    if (tlsSecret != null) {
      NetworkingV1beta1IngressSpec spec = ingress.getSpec().tls(tlsList);
      ingress.setSpec(spec);
    }

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
   * Create an ingress in specified namespace.
   * @param ingressName ingress name
   * @param namespace namespace in which the ingress will be created
   * @param annotations annotations of the ingress
   * @param ingressRules a list of ingress rules
   * @param tlsList list of ingress tls
   * @throws ApiException if Kubernetes API call fails
   */
  public static void createIngress(String ingressName,
                                   String namespace,
                                   Map<String, String> annotations,
                                   List<NetworkingV1beta1IngressRule> ingressRules,
                                   List<NetworkingV1beta1IngressTLS> tlsList) throws ApiException {

    // set the ingress
    NetworkingV1beta1Ingress ingress = new NetworkingV1beta1Ingress()
        .apiVersion(INGRESS_API_VERSION)
        .kind(INGRESS_KIND)
        .metadata(new V1ObjectMeta()
            .name(ingressName)
            .namespace(namespace)
            .annotations(annotations))
        .spec(new NetworkingV1beta1IngressSpec()
            .rules(ingressRules));

    if (tlsList != null) {
      NetworkingV1beta1IngressSpec spec = ingress.getSpec().tls(tlsList);
      ingress.setSpec(spec);
    }

    // create the ingress
    try {
      Kubernetes.createIngress(namespace, ingress);
    } catch (ApiException apex) {
      getLogger().severe("got ApiException while calling createIngress: {0}", apex.getResponseBody());
      throw apex;
    }
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
    NetworkingV1beta1IngressList ingressList = Kubernetes.listNamespacedIngresses(namespace);
    List<NetworkingV1beta1Ingress> listOfIngress = ingressList.getItems();

    listOfIngress.forEach(ingress -> {
      if (ingress.getMetadata() != null) {
        ingressNames.add(ingress.getMetadata().getName());
      }
    });

    return ingressNames;
  }

  /**
   * Delete an ingress in the specified namespace.
   *
   * @param name  ingress name to be deleted
   * @param namespace namespace in which the specified ingress exists
   * @return true if deleting ingress succeed, false otherwise
   * @throws ApiException if Kubernetes API client call fails
   */
  public static boolean deleteIngress(String name, String namespace) throws ApiException {
    return Kubernetes.deleteIngress(name, namespace);
  }
}
