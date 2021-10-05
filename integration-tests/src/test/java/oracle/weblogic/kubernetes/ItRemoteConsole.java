// Copyright (c) 2021, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.weblogic.kubernetes;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import io.kubernetes.client.custom.IntOrString;
import io.kubernetes.client.openapi.models.NetworkingV1beta1HTTPIngressPath;
import io.kubernetes.client.openapi.models.NetworkingV1beta1HTTPIngressRuleValue;
import io.kubernetes.client.openapi.models.NetworkingV1beta1IngressBackend;
import io.kubernetes.client.openapi.models.NetworkingV1beta1IngressRule;
import oracle.weblogic.kubernetes.actions.impl.primitive.HelmParams;
import oracle.weblogic.kubernetes.annotations.IntegrationTest;
import oracle.weblogic.kubernetes.annotations.Namespaces;
import oracle.weblogic.kubernetes.logging.LoggingFacade;
import oracle.weblogic.kubernetes.utils.ExecCommand;
import oracle.weblogic.kubernetes.utils.ExecResult;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static oracle.weblogic.kubernetes.TestConstants.ADMIN_SERVER_NAME_BASE;
import static oracle.weblogic.kubernetes.TestConstants.K8S_NODEPORT_HOST;
import static oracle.weblogic.kubernetes.TestConstants.MANAGED_SERVER_NAME_BASE;
import static oracle.weblogic.kubernetes.TestConstants.MII_BASIC_IMAGE_NAME;
import static oracle.weblogic.kubernetes.TestConstants.MII_BASIC_IMAGE_TAG;
import static oracle.weblogic.kubernetes.TestConstants.RESULTS_ROOT;
import static oracle.weblogic.kubernetes.actions.ActionConstants.RESOURCE_DIR;
import static oracle.weblogic.kubernetes.actions.TestActions.listIngresses;
import static oracle.weblogic.kubernetes.actions.impl.Service.getServiceNodePort;
import static oracle.weblogic.kubernetes.utils.ApplicationUtils.callWebAppAndWaitTillReady;
import static oracle.weblogic.kubernetes.utils.ApplicationUtils.callWebAppAndWaitTillReturnedCode;
import static oracle.weblogic.kubernetes.utils.CommonMiiTestUtils.createSSLenabledMiiDomainAndVerify;
import static oracle.weblogic.kubernetes.utils.LoadBalancerUtils.createIngressAndRetryIfFail;
import static oracle.weblogic.kubernetes.utils.LoadBalancerUtils.installAndVerifyNginx;
import static oracle.weblogic.kubernetes.utils.LoadBalancerUtils.installAndVerifyTraefik;
import static oracle.weblogic.kubernetes.utils.OperatorUtils.installAndVerifyOperator;
import static oracle.weblogic.kubernetes.utils.PodUtils.getExternalServicePodName;
import static oracle.weblogic.kubernetes.utils.ThreadSafeLogger.getLogger;
import static oracle.weblogic.kubernetes.utils.WebLogicRemoteConsoleUtils.installAndVerifyWlsRemoteConsole;
import static oracle.weblogic.kubernetes.utils.WebLogicRemoteConsoleUtils.shutdownWlsRemoteConsole;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("Test WebLogic remote console connecting to mii domain")
@IntegrationTest
class ItRemoteConsole {

  private static String domainNamespace = null;
  private static String traefikNamespace = null;
  private static String nginxNamespace = null;
  private static HelmParams traefikHelmParams = null;
  private static HelmParams nginxHelmParams = null;
  private static int nginxNodePort;

  // domain constants
  private static final String domainUid = "domain1";
  private static final int replicaCount = 1;
  private static final String adminServerPodName = domainUid + "-" + ADMIN_SERVER_NAME_BASE;
  private static final String managedServerPrefix = domainUid + "-" + MANAGED_SERVER_NAME_BASE;
  private static LoggingFacade logger = null;
  private static final int ADMIN_SERVER_PORT = 7001;

  /**
   * Get namespaces for operator and WebLogic domain.
   *
   * @param namespaces list of namespaces created by the IntegrationTestWatcher by the
   *                   JUnit engine parameter resolution mechanism
   */
  @BeforeAll
  public static void initAll(@Namespaces(4) List<String> namespaces) {
    logger = getLogger();
    // get a unique operator namespace
    logger.info("Getting a unique namespace for operator");
    assertNotNull(namespaces.get(0), "Namespace list is null");
    final String opNamespace = namespaces.get(0);

    // get a unique domain namespace
    logger.info("Getting a unique namespace for WebLogic domain");
    assertNotNull(namespaces.get(1), "Namespace list is null");
    domainNamespace = namespaces.get(1);

    logger.info("Assign a unique namespace for Traefik");
    assertNotNull(namespaces.get(2), "Namespace list is null");
    traefikNamespace = namespaces.get(2);

    // get a unique Nginx namespace
    logger.info("Assign a unique namespace for Nginx");
    assertNotNull(namespaces.get(3), "Namespace list is null");
    nginxNamespace = namespaces.get(3);

    // install and verify operator
    installAndVerifyOperator(opNamespace, domainNamespace);

    // install and verify Traefik
    logger.info("Installing Traefik controller using helm");
    traefikHelmParams = installAndVerifyTraefik(traefikNamespace, 0, 0);

    // install and verify Nginx
    nginxHelmParams = installAndVerifyNginx(nginxNamespace, 0, 0);

    createSSLenabledMiiDomainAndVerify(
        domainNamespace,
        domainUid,
        MII_BASIC_IMAGE_NAME + ":" + MII_BASIC_IMAGE_TAG,
        adminServerPodName,
        managedServerPrefix,
        replicaCount);

    // create ingress rules with path routing for Traefik and NGINX
    createTraefikIngressRoutingRules(domainNamespace);
    createNginxIngressPathRoutingRules();

    // install WebLogic remote console
    assertTrue(installAndVerifyWlsRemoteConsole(domainNamespace, adminServerPodName),
        "Remote Console installation failed");

    // Verify k8s WebLogic domain is accessible through remote console using admin server nodeport
    verifyWlsRemoteConsoleConnection();
  }

  /**
   * Access WebLogic domain through remote console using Traefik.
   */
  @Test
  @DisplayName("Verify Connecting to Mii domain WLS Remote Console through Traefik is successful")
  void testWlsRemoteConsoleConnectionThroughTraefik() {

    int traefikNodePort = getServiceNodePort(traefikNamespace, traefikHelmParams.getReleaseName(), "web");
    assertTrue(traefikNodePort != -1,
        "Could not get the default external service node port");
    logger.info("Found the Traefik service nodePort {0}", traefikNodePort);
    logger.info("The K8S_NODEPORT_HOST is {0}", K8S_NODEPORT_HOST);
    String curlCmd = "curl -v --user weblogic:welcome1 -H Content-Type:application/json -d "
        + "\"{ \\" + "\"domainUrl\\" + "\"" + ": " + "\\" + "\"" + "http://"
        + K8S_NODEPORT_HOST + ":" + traefikNodePort + "\\" + "\" }" + "\""
        + " http://localhost:8012/api/connection  --write-out %{http_code} -o /dev/null";
    logger.info("Executing Traefik nodeport curl command {0}", curlCmd);
    assertTrue(callWebAppAndWaitTillReturnedCode(curlCmd, "201", 10), "Calling web app failed");
    logger.info("WebLogic domain is accessible through remote console using Traefik");
  }

  /**
   * Access WebLogic domain through remote console using NGINX.
   */
  @Test
  @DisplayName("Verify Connecting to Mii domain WLS Remote Console through NGINX is successful")
  void testWlsRemoteConsoleConnectionThroughNginx() {

    assertTrue(nginxNodePort != -1, "Could not get the default external service node port");
    logger.info("Found the NGINX service nodePort {0}", nginxNodePort);
    logger.info("The K8S_NODEPORT_HOST is {0}", K8S_NODEPORT_HOST);

    String curlCmd = "curl -v --user weblogic:welcome1 -H Content-Type:application/json -d "
        + "\"{ \\" + "\"domainUrl\\" + "\"" + ": " + "\\" + "\"" + "http://"
        + K8S_NODEPORT_HOST + ":" + nginxNodePort + "\\" + "\" }" + "\""
        + " http://localhost:8012/api/connection  --write-out %{http_code} -o /dev/null";
    logger.info("Executing NGINX nodeport curl command {0}", curlCmd);
    assertTrue(callWebAppAndWaitTillReturnedCode(curlCmd, "201", 10), "Calling web app failed");
    logger.info("WebLogic domain is accessible through remote console using NGINX");
  }

  /**
   * Verify k8s WebLogic domain is accessible through remote console using SSL.
   */
  @Test
  @DisplayName("Verify Connecting to Mii domain by Remote Console using SSL is successful")
  void testWlsRemoteConsoleConnectionUsingSSL() {
    int sslNodePort = getServiceNodePort(
         domainNamespace, getExternalServicePodName(adminServerPodName), "default-secure");
    assertTrue(sslNodePort != -1,
          "Could not get the default-secure external service node port");
    logger.info("Found the administration service nodePort {0}", sslNodePort);
    logger.info("The K8S_NODEPORT_HOST is {0}", K8S_NODEPORT_HOST);

    //verify WebLogic console is accessible through default-secure nodeport
    String curlCmd = "curl -sk --show-error --noproxy '*' "
          + " https://" + K8S_NODEPORT_HOST + ":" + sslNodePort
          + "/console/login/LoginForm.jsp --write-out %{http_code} -o /dev/null";
    logger.info("Executing WebLogic console default-secure nodeport curl command {0}", curlCmd);
    assertTrue(callWebAppAndWaitTillReady(curlCmd, 10));
    logger.info("WebLogic console is accessible thru default-secure service");

    //verify remote console is accessible through default-secure nodeport
    curlCmd = "curl -sk -v --show-error --noproxy '*' --user weblogic:welcome1 -H "
        + "Content-Type:application/json -d "
        + "\"{ \\" + "\"domainUrl\\" + "\"" + ": " + "\\" + "\"" + "https://"
        + K8S_NODEPORT_HOST + ":" + sslNodePort + "\\" + "\" }" + "\""
        + " http://localhost:8012/api/connection  --write-out %{http_code} -o /dev/null";
    logger.info("Executing remote console default-secure nodeport curl command {0}", curlCmd);
    assertTrue(callWebAppAndWaitTillReturnedCode(curlCmd, "201", 10), "Calling web app failed");
    logger.info("Remote console is accessible through default-secure service");

  }

  /**
   * Shutdown WLS Remote Console.
   */
  @AfterAll
  public void tearDownAll() {
    if (System.getenv("SKIP_CLEANUP") == null
        || (System.getenv("SKIP_CLEANUP") != null
        && System.getenv("SKIP_CLEANUP").equalsIgnoreCase("false")))  {
      assertTrue(shutdownWlsRemoteConsole(), "Remote Console shutdown failed");
    }
  }

  private static void createTraefikIngressRoutingRules(String domainNamespace) {
    logger.info("Creating ingress rules for domain traffic routing");
    Path srcFile = Paths.get(RESOURCE_DIR, "traefik/traefik-ingress-rules-remoteconsole.yaml");
    Path dstFile = Paths.get(RESULTS_ROOT, "traefik/traefik-ingress-rules-remoteconsole.yaml");
    assertDoesNotThrow(() -> {
      Files.deleteIfExists(dstFile);
      Files.createDirectories(dstFile.getParent());
      Files.write(dstFile, Files.readString(srcFile).replaceAll("@NS@", domainNamespace)
          .replaceAll("@domain1uid@", domainUid)
          .getBytes(StandardCharsets.UTF_8));
    });
    String command = "kubectl create -f " + dstFile;
    logger.info("Running {0}", command);
    ExecResult result;
    try {
      result = ExecCommand.exec(command, true);
      String response = result.stdout().trim();
      logger.info("exitCode: {0}, \nstdout: {1}, \nstderr: {2}",
          result.exitValue(), response, result.stderr());
      assertEquals(0, result.exitValue(), "Command didn't succeed");
    } catch (IOException | InterruptedException ex) {
      logger.severe(ex.getMessage());
    }
  }

  private static void createNginxIngressPathRoutingRules() {

    // create an ingress in domain namespace
    String ingressName = domainNamespace + "-nginx-path-routing";

    HashMap<String, String> annotations = new HashMap<>();
    annotations.put("kubernetes.io/ingress.class", "nginx");

    // create ingress rules for two domains
    List<NetworkingV1beta1IngressRule> ingressRules = new ArrayList<>();
    List<NetworkingV1beta1HTTPIngressPath> httpIngressPaths = new ArrayList<>();

    NetworkingV1beta1HTTPIngressPath httpIngressPath = new NetworkingV1beta1HTTPIngressPath()
        .path("/")
        .backend(new NetworkingV1beta1IngressBackend()
            .serviceName(domainUid + "-admin-server")
            .servicePort(new IntOrString(ADMIN_SERVER_PORT))
        );
    httpIngressPaths.add(httpIngressPath);

    NetworkingV1beta1IngressRule ingressRule = new NetworkingV1beta1IngressRule()
        .host("")
        .http(new NetworkingV1beta1HTTPIngressRuleValue()
            .paths(httpIngressPaths));

    ingressRules.add(ingressRule);

    createIngressAndRetryIfFail(60, false, ingressName, domainNamespace, annotations, ingressRules, null);

    // check the ingress was found in the domain namespace
    assertThat(assertDoesNotThrow(() -> listIngresses(domainNamespace)))
        .as(String.format("Test ingress %s was found in namespace %s", ingressName, domainNamespace))
        .withFailMessage(String.format("Ingress %s was not found in namespace %s", ingressName, domainNamespace))
        .contains(ingressName);

    logger.info("ingress {0} was created in namespace {1}", ingressName, domainNamespace);

    // check the ingress is ready to route the app to the server pod
    String nginxServiceName = nginxHelmParams.getReleaseName() + "-ingress-nginx-controller";
    nginxNodePort = assertDoesNotThrow(() -> getServiceNodePort(nginxNamespace, nginxServiceName, "http"),
        "Getting Nginx loadbalancer service node port failed");
    String curlCmd = "curl --silent --show-error --noproxy '*' http://" + K8S_NODEPORT_HOST + ":" + nginxNodePort
        + "/weblogic/ready --write-out %{http_code} -o /dev/null";

    logger.info("Executing curl command {0}", curlCmd);
    assertTrue(callWebAppAndWaitTillReady(curlCmd, 60));
  }

  private static void verifyWlsRemoteConsoleConnection() {
    int nodePort = getServiceNodePort(
        domainNamespace, getExternalServicePodName(adminServerPodName), "default");
    assertTrue(nodePort != -1,
        "Could not get the default external service node port");
    logger.info("Found the default service nodePort {0}", nodePort);
    logger.info("The K8S_NODEPORT_HOST is {0}", K8S_NODEPORT_HOST);

    String curlCmd = "curl -v --show-error --noproxy '*' --user weblogic:welcome1 -H Content-Type:application/json -d "
        + "\"{ \\" + "\"domainUrl\\" + "\"" + ": " + "\\" + "\"" + "http://"
        + K8S_NODEPORT_HOST + ":" + nodePort + "\\" + "\" }" + "\""
        + " http://localhost:8012/api/connection  --write-out %{http_code} -o /dev/null";
    logger.info("Executing default nodeport curl command {0}", curlCmd);
    assertTrue(callWebAppAndWaitTillReturnedCode(curlCmd, "201", 10), "Calling web app failed");
    logger.info("WebLogic domain is accessible through remote console");
  }

}
