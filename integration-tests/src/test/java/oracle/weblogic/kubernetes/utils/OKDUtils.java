// Copyright (c) 2021, 2022, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.weblogic.kubernetes.utils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import io.kubernetes.client.openapi.models.V1HTTPIngressPath;
import io.kubernetes.client.openapi.models.V1HTTPIngressRuleValue;
import io.kubernetes.client.openapi.models.V1IngressBackend;
import io.kubernetes.client.openapi.models.V1IngressRule;
import io.kubernetes.client.openapi.models.V1IngressServiceBackend;
import io.kubernetes.client.openapi.models.V1ServiceBackendPort;
import oracle.weblogic.kubernetes.actions.impl.primitive.Command;
import oracle.weblogic.kubernetes.actions.impl.primitive.CommandParams;

import static oracle.weblogic.kubernetes.TestConstants.OKD;
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
  public static String createRouteForOKD(String serviceName, String namespace, String... routeName) {
    boolean routeExists = false;
    String command = "oc -n " + namespace + " expose service " + serviceName;
    if (OKD) {
      getLogger().info("Going to create route for OKD");
      if (routeName.length == 0) {
        routeExists = doesRouteExist(serviceName, namespace);
      } else {
        routeExists = doesRouteExist(routeName[0], namespace);
        command = command + " --name " + routeName[0];
        serviceName = routeName[0];
      }
      if (!routeExists) {
        assertTrue(Command
            .withParams(new CommandParams()
              .command(command))
            .execute(), "oc expose service failed");
      }
      return getRouteHost(namespace, serviceName);
    } else {
      getLogger().info("This is non OKD env. No route is needed");
      return null;
    }
  }

  /**
   * Get the host name of the route.
   *
   * @param namespace - Namespace where the route is exposed
   * @param serviceName - Name of the route
   */
  public static String getRouteHost(String namespace, String serviceName) {
    if (OKD) {
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
    } else {
      return null;
    }
  }

  /**
   * Sets TLS termination in the route to passthrough.
   *
   * @param routeName name of the route
   * @param namespace namespace where the route is created
   */
  public static void setTlsTerminationForRoute(String routeName, String namespace) {
    if (OKD) {
      String command = "oc -n " + namespace + " patch route " + routeName
                          +  " --patch '{\"spec\": {\"tls\": {\"termination\": \"passthrough\"}}}'";

      ExecResult result = Command.withParams(
          new CommandParams()
              .command(command))
          .executeAndReturnResult();

      boolean success =
          result != null
              && result.exitValue() == 0
              && result.stdout() != null
              && result.stdout().contains("patched");

      String outStr = "Setting tls termination in route failed \n";
      outStr += ", command=\n{\n" + command + "\n}\n";
      outStr += ", stderr=\n{\n" + (result != null ? result.stderr() : "") + "\n}\n";
      outStr += ", stdout=\n{\n" + (result != null ? result.stdout() : "") + "\n}\n";

      assertTrue(success, outStr);

      getLogger().info("exitValue = {0}", result.exitValue());
      getLogger().info("stdout = {0}", result.stdout());
      getLogger().info("stderr = {0}", result.stderr());
    }
  }

  /**
   * Sets TLS termination in the route to passthrough.
   *
   * @param routeName name of the route
   * @param namespace namespace where the route is created
   */
  public static void setTlsEdgeTerminationForRoute(String routeName, String namespace, 
                                                   Path keyFile, Path certFile) throws IOException {
    if (OKD) {
      String tlsKey = Files.readString(keyFile);
      // Remove the last \n from the String above
      tlsKey = tlsKey.replaceAll("[\n\r]$", "");
      String tlsCert = Files.readString(certFile);
      // Remove the last \n from the String above
      tlsCert = tlsCert.replaceAll("[\n\r]$", "");
      String command = "oc -n " + namespace + " patch route " + routeName
          + " --patch '{\"spec\": {\"tls\": {\"termination\": \"edge\"," 
                                          + "\"key\": \"" + tlsKey + "\","  
                                          + "\"certificate\": \"" + tlsCert + "\"}}}'";

      ExecResult result = Command.withParams(
          new CommandParams()
              .command(command))
          .executeAndReturnResult();

      boolean success =
          result != null
              && result.exitValue() == 0
              && result.stdout() != null
              && result.stdout().contains("patched");

      String outStr = "Setting tls termination in route failed \n";
      outStr += ", command=\n{\n" + command + "\n}\n";
      outStr += ", stderr=\n{\n" + (result != null ? result.stderr() : "") + "\n}\n";
      outStr += ", stdout=\n{\n" + (result != null ? result.stdout() : "") + "\n}\n";

      assertTrue(success, outStr);

      getLogger().info("exitValue = {0}", result.exitValue());
      getLogger().info("stdout = {0}", result.stdout());
      getLogger().info("stderr = {0}", result.stderr());
    }
  }

  /** 
   * Sets the target port of the route.
   * 
   * @param routeName  name of the route
   * @param namespace namespace where the route is created
   * @param port target port
   */
  public static void setTargetPortForRoute(String routeName, String namespace, int port) {
    if (OKD) {
      String command = "oc -n " + namespace + " patch route " + routeName
                          +  " --patch '{\"spec\": {\"port\": {\"targetPort\": \"" + port + "\"}}}'";

      ExecResult result = Command.withParams(
          new CommandParams()
              .command(command))
          .executeAndReturnResult();

      boolean success =
          result != null
              && result.exitValue() == 0
              && result.stdout() != null
              && result.stdout().contains("patched");

      String outStr = "Setting target port in route failed \n";
      outStr += ", command=\n{\n" + command + "\n}\n";
      outStr += ", stderr=\n{\n" + (result != null ? result.stderr() : "") + "\n}\n";
      outStr += ", stdout=\n{\n" + (result != null ? result.stdout() : "") + "\n}\n";

      assertTrue(success, outStr);

      getLogger().info("exitValue = {0}", result.exitValue());
      getLogger().info("stdout = {0}", result.stdout());
      getLogger().info("stderr = {0}", result.stderr());
    }
  }

  private static boolean doesRouteExist(String routeName, String namespace) {
    String command = "oc -n " + namespace + " get route " + routeName;

    ExecResult result = Command.withParams(
          new CommandParams()
              .command(command))
          .executeAndReturnResult();

    if (result != null) {
      getLogger().info("exitValue = {0}", result.exitValue());
      getLogger().info("stdout = {0}", result.stdout());
      getLogger().info("stderr = {0}", result.stderr());
    }

    boolean exists =
        result != null 
         && result.exitValue() == 0 
         && result.stdout() != null 
         && result.stdout().contains(routeName);

    return exists;
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

    List<V1IngressRule> ingressRules = new ArrayList<>();
    List<V1HTTPIngressPath> httpIngressPaths = new ArrayList<>();

    V1HTTPIngressPath httpIngressPath = new V1HTTPIngressPath()
        .path("/")
        .pathType("Prefix")
        .backend(new V1IngressBackend()
            .service(new V1IngressServiceBackend()
                .name(asExtSvcName)
                .port(new V1ServiceBackendPort()
                    .number(7001)))
        );
    httpIngressPaths.add(httpIngressPath);

    V1IngressRule ingressRule = new V1IngressRule()
        .host(host)
        .http(new V1HTTPIngressRuleValue()
            .paths(httpIngressPaths));

    ingressRules.add(ingressRule);

    assertDoesNotThrow(() -> createIngress(ingressName, namespace, null, null, ingressRules, null));

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

    List<V1IngressRule> ingressRules = new ArrayList<>();
    List<V1HTTPIngressPath> httpIngressPaths = new ArrayList<>();

    V1HTTPIngressPath httpIngressPath = new V1HTTPIngressPath()
        .path("/")
        .pathType("Prefix")
        .backend(new V1IngressBackend()
            .service(new V1IngressServiceBackend()
                .name(svcName)
                .port(new V1ServiceBackendPort()
                    .number(8001)))
        );
    httpIngressPaths.add(httpIngressPath);

    V1IngressRule ingressRule = new V1IngressRule()
        .host(host)
        .http(new V1HTTPIngressRuleValue()
            .paths(httpIngressPaths));

    ingressRules.add(ingressRule);

    assertDoesNotThrow(() -> createIngress(ingressName, namespace, null, null, ingressRules, null));

    // check the ingress was found in the domain namespace
    assertThat(assertDoesNotThrow(() -> listIngresses(namespace)))
        .as(String.format("Test ingress %s was found in namespace %s", ingressName, namespace))
        .withFailMessage(String.format("Ingress %s was not found in namespace %s", ingressName, namespace))
        .contains(ingressName);

    getLogger().info("ingress {0} was created in namespace {1}", ingressName, namespace);
    return host;
  }

  /**
   * add security context constraints to the service account of namespace.
   * @param serviceAccount - service account to add to scc
   * @param namespace - namespace to which the service account belongs
   */
  public static void addSccToNsSvcAccount(String serviceAccount, String namespace) {
    assertTrue(Command
        .withParams(new CommandParams()
            .command("oc adm policy add-scc-to-user privileged -z " + serviceAccount + " -n " + namespace))
        .execute(), "oc expose service failed");
  }
}
