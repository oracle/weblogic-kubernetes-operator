// Copyright (c) 2021, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.weblogic.kubernetes.utils;

import java.util.ArrayList;
import java.util.List;

import io.kubernetes.client.custom.IntOrString;
import io.kubernetes.client.openapi.models.NetworkingV1beta1HTTPIngressPath;
import io.kubernetes.client.openapi.models.NetworkingV1beta1HTTPIngressRuleValue;
import io.kubernetes.client.openapi.models.NetworkingV1beta1IngressBackend;
import io.kubernetes.client.openapi.models.NetworkingV1beta1IngressRule;
import oracle.weblogic.kubernetes.actions.impl.primitive.Command;
import oracle.weblogic.kubernetes.actions.impl.primitive.CommandParams;

import static oracle.weblogic.kubernetes.TestConstants.WLS_DEFAULT_CHANNEL_NAME;
import static oracle.weblogic.kubernetes.actions.TestActions.createIngress;
import static oracle.weblogic.kubernetes.actions.TestActions.getServicePort;
import static oracle.weblogic.kubernetes.actions.TestActions.listIngresses;
import static oracle.weblogic.kubernetes.utils.PodUtils.getExternalServicePodName;
import static oracle.weblogic.kubernetes.utils.ThreadSafeLogger.getLogger;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class OKDUtils {
  /**
   * We need to expose the service as a route for ingress.
   *
   * @param serviceName - Name of the route and service
   * @param namespace - Namespace where the route is exposed
   */
  public static String createRouteForOKD(String serviceName, String namespace) {
    assertTrue(new Command()
        .withParams(new CommandParams()
            .command("oc expose service " + serviceName + " -n " + namespace))
        .execute(), "oc expose service failed");
    String command = "oc -n " + namespace + " get routes " + serviceName + "  '-o=jsonpath={.spec.host}'";

    ExecResult result = Command.withParams(
        new CommandParams()
            .command(command))
        .executeAndReturnResult();

    boolean success =
        result != null
            && result.exitValue() == 0
            && result.stdout() != null
            && result.stdout().contains(serviceName);

    String outStr = "Did not get the route hostName \n";
    outStr += ", command=\n{\n" + command + "\n}\n";
    outStr += ", stderr=\n{\n" + (result != null ? result.stderr() : "") + "\n}\n";
    outStr += ", stdout=\n{\n" + (result != null ? result.stdout() : "") + "\n}\n";

    assertTrue(success, outStr);

    getLogger().info("exitValue = {0}", result.exitValue());
    getLogger().info("stdout = {0}", result.stdout());
    getLogger().info("stderr = {0}", result.stderr());

    String hostName = result.stdout();
    getLogger().info("route hostname = {0}", hostName);

    return hostName;
  }

  /**
   * In OKD environment, the nodePort cannot be accessed directly. We need to create an ingress
   *
   * @param podName name of the pod - to create the ingress for its external service
   * @param namespace namespace of the domain
   * @return hostname to access the ingress
   */
  public static String createASIngressForOKD(String podName, String namespace) {
    String asExtSvcName = getExternalServicePodName(podName);
    getLogger().info("admin server external svc = {0}", asExtSvcName);

    //String host = asExtSvcName + "-" + namespace;
    String host = asExtSvcName;

    int adminServicePort
        = getServicePort(namespace, getExternalServicePodName(podName), WLS_DEFAULT_CHANNEL_NAME);
    getLogger().info("admin service external port = {0}", adminServicePort);

    String ingressName = asExtSvcName + "-ingress-path-routing";

    List<NetworkingV1beta1IngressRule> ingressRules = new ArrayList<>();
    List<NetworkingV1beta1HTTPIngressPath> httpIngressPaths = new ArrayList<>();

    NetworkingV1beta1HTTPIngressPath httpIngressPath = new NetworkingV1beta1HTTPIngressPath()
        .path("/")
        .backend(new NetworkingV1beta1IngressBackend()
            .serviceName(asExtSvcName)
            .servicePort(new IntOrString(7001))
        );
    httpIngressPaths.add(httpIngressPath);

    NetworkingV1beta1IngressRule ingressRule = new NetworkingV1beta1IngressRule()
        .host(host)
        .http(new NetworkingV1beta1HTTPIngressRuleValue()
            .paths(httpIngressPaths));

    ingressRules.add(ingressRule);

    assertDoesNotThrow(() -> createIngress(ingressName, namespace, null, ingressRules, null));

    // check the ingress was found in the domain namespace
    assertThat(assertDoesNotThrow(() -> listIngresses(namespace)))
        .as(String.format("Test ingress %s was found in namespace %s", ingressName, namespace))
        .withFailMessage(String.format("Ingress %s was not found in namespace %s", ingressName, namespace))
        .contains(ingressName);

    getLogger().info("ingress {0} was created in namespace {1}", ingressName, namespace);
    return host;
  }

  /**
   * In OKD environment, the nodePort cannot be accessed directly. We need to create an ingress
   *
   * @param svcName - cluster service name
   * @param namespace namespace of the domain
   * @return hostname to access the ingress
   */
  public static String createClusterIngressForOKD(String svcName, String namespace) {

    //String host = svcName + "-" + namespace;
    String host = svcName;

    String ingressName = host + "-ingress-path-routing";

    List<NetworkingV1beta1IngressRule> ingressRules = new ArrayList<>();
    List<NetworkingV1beta1HTTPIngressPath> httpIngressPaths = new ArrayList<>();

    NetworkingV1beta1HTTPIngressPath httpIngressPath = new NetworkingV1beta1HTTPIngressPath()
        .path("/")
        .backend(new NetworkingV1beta1IngressBackend()
            .serviceName(svcName)
            .servicePort(new IntOrString(8001))
        );
    httpIngressPaths.add(httpIngressPath);

    NetworkingV1beta1IngressRule ingressRule = new NetworkingV1beta1IngressRule()
        .host(host)
        .http(new NetworkingV1beta1HTTPIngressRuleValue()
            .paths(httpIngressPaths));

    ingressRules.add(ingressRule);

    assertDoesNotThrow(() -> createIngress(ingressName, namespace, null, ingressRules, null));

    // check the ingress was found in the domain namespace
    assertThat(assertDoesNotThrow(() -> listIngresses(namespace)))
        .as(String.format("Test ingress %s was found in namespace %s", ingressName, namespace))
        .withFailMessage(String.format("Ingress %s was not found in namespace %s", ingressName, namespace))
        .contains(ingressName);

    getLogger().info("ingress {0} was created in namespace {1}", ingressName, namespace);
    return host;
  }
}
