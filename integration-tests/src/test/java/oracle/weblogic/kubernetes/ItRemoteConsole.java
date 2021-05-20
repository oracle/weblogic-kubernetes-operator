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
import org.awaitility.core.ConditionFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static java.util.concurrent.TimeUnit.MINUTES;
import static java.util.concurrent.TimeUnit.SECONDS;
import static oracle.weblogic.kubernetes.TestConstants.ADMIN_SERVER_NAME_BASE;
import static oracle.weblogic.kubernetes.TestConstants.K8S_NODEPORT_HOST;
import static oracle.weblogic.kubernetes.TestConstants.MANAGED_SERVER_NAME_BASE;
import static oracle.weblogic.kubernetes.TestConstants.MII_BASIC_IMAGE_NAME;
import static oracle.weblogic.kubernetes.TestConstants.MII_BASIC_IMAGE_TAG;
import static oracle.weblogic.kubernetes.TestConstants.RESULTS_ROOT;
import static oracle.weblogic.kubernetes.TestConstants.VOYAGER_CHART_NAME;
import static oracle.weblogic.kubernetes.actions.ActionConstants.RESOURCE_DIR;
import static oracle.weblogic.kubernetes.actions.TestActions.createIngress;
import static oracle.weblogic.kubernetes.actions.TestActions.listIngresses;
import static oracle.weblogic.kubernetes.actions.impl.Service.getServiceNodePort;
import static oracle.weblogic.kubernetes.assertions.TestAssertions.isVoyagerReady;
import static oracle.weblogic.kubernetes.utils.CommonMiiTestUtils.createMiiDomainAndVerify;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.createIngressAndRetryIfFail;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.getExternalServicePodName;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.installAndVerifyNginx;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.installAndVerifyOperator;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.installAndVerifyTraefik;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.installAndVerifyVoyager;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.installAndVerifyWlsRemoteConsole;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.shutdownWlsRemoteConsole;
import static oracle.weblogic.kubernetes.utils.TestUtils.callWebAppAndWaitTillReady;
import static oracle.weblogic.kubernetes.utils.TestUtils.callWebAppAndWaitTillReturnedCode;
import static oracle.weblogic.kubernetes.utils.ThreadSafeLogger.getLogger;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.with;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("Test WebLogic remote console connecting to mii domain")
@IntegrationTest
class ItRemoteConsole {

  private static String domainNamespace = null;
  private static String traefikNamespace = null;
  private static String voyagerNamespace = null;
  private static String nginxNamespace = null;
  private static HelmParams traefikHelmParams = null;
  private static HelmParams voyagerHelmParams = null;
  private static HelmParams nginxHelmParams = null;
  private static int voyagerNodePort;
  private static int nginxNodePort;

  // domain constants
  private static final String domainUid = "domain1";
  private static final int replicaCount = 1;
  private static final String adminServerPodName = domainUid + "-" + ADMIN_SERVER_NAME_BASE;
  private static final String managedServerPrefix = domainUid + "-" + MANAGED_SERVER_NAME_BASE;
  private static LoggingFacade logger = null;
  private static final int ADMIN_SERVER_PORT = 7001;
  private static final String voyagerIngressName = "voyager-path-routing";

  private static ConditionFactory withStandardRetryPolicy =
      with().pollDelay(2, SECONDS)
          .and().with().pollInterval(10, SECONDS)
          .atMost(5, MINUTES).await();

  /**
   * Get namespaces for operator and WebLogic domain.
   *
   * @param namespaces list of namespaces created by the IntegrationTestWatcher by the
   *                   JUnit engine parameter resolution mechanism
   */
  @BeforeAll
  public static void initAll(@Namespaces(5) List<String> namespaces) {
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

    // get a unique Voyager namespace
    logger.info("Assign a unique namespace for Voyager");
    assertNotNull(namespaces.get(3), "Namespace list is null");
    voyagerNamespace = namespaces.get(3);

    // get a unique Nginx namespace
    logger.info("Assign a unique namespace for Nginx");
    assertNotNull(namespaces.get(4), "Namespace list is null");
    nginxNamespace = namespaces.get(4);

    // install and verify operator
    installAndVerifyOperator(opNamespace, domainNamespace);

    // install and verify Traefik
    logger.info("Installing Traefik controller using helm");
    traefikHelmParams = installAndVerifyTraefik(traefikNamespace, 0, 0);

    // install and verify Voyager
    final String cloudProvider = "baremetal";
    final boolean enableValidatingWebhook = false;
    voyagerHelmParams =
        installAndVerifyVoyager(voyagerNamespace, cloudProvider, enableValidatingWebhook);

    // install and verify Nginx
    nginxHelmParams = installAndVerifyNginx(nginxNamespace, 0, 0);

    // create a basic model in image domain
    createMiiDomainAndVerify(
        domainNamespace,
        domainUid,
        MII_BASIC_IMAGE_NAME + ":" + MII_BASIC_IMAGE_TAG,
        adminServerPodName,
        managedServerPrefix,
        replicaCount);

    // create ingress rules with path routing for Traefik, Voyager and NGINX
    createTraefikIngressRoutingRules(domainNamespace);
    createVoyagerIngressPathRoutingRules();
    createNginxIngressPathRoutingRules();

    // install WebLogic remote console
    assertTrue(installAndVerifyWlsRemoteConsole(), "Remote Console installation failed");

    // Verify k8s WebLogic domain is accessible through remote console using admin server nodeport
    verifyWlsRemoteConsoleConnection();
  }

  /**
   * Verify k8s WebLogic domain is accessible through remote console using Traefik.
   */
  @Test
  @DisplayName("Verify Connecting to Mii domain WLS Remote Console through Traefik is successful")
  public void testWlsRemoteConsoleConnectionThroughTraefik() {

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
   * Verify k8s WebLogic domain is accessible through remote console using Voyager.
   */
  @Test
  @DisplayName("Verify Connecting to Mii domain WLS Remote Console through Voyager is successful")
  public void testWlsRemoteConsoleConnectionThroughVoyager() {

    assertTrue(voyagerNodePort != -1, "Could not get the default external service node port");
    logger.info("Found the Voyager service nodePort {0}", voyagerNodePort);
    logger.info("The K8S_NODEPORT_HOST is {0}", K8S_NODEPORT_HOST);

    String curlCmd = "curl -v --user weblogic:welcome1 -H Content-Type:application/json -d "
        + "\"{ \\" + "\"domainUrl\\" + "\"" + ": " + "\\" + "\"" + "http://"
        + K8S_NODEPORT_HOST + ":" + voyagerNodePort + "\\" + "\" }" + "\""
        + " http://localhost:8012/api/connection  --write-out %{http_code} -o /dev/null";
    logger.info("Executing Voyager nodeport curl command {0}", curlCmd);
    assertTrue(callWebAppAndWaitTillReturnedCode(curlCmd, "201", 10), "Calling web app failed");
    logger.info("WebLogic domain is accessible through remote console using Voyager");
  }

  /**
   * Verify k8s WebLogic domain is accessible through remote console using NGINX.
   */
  @Test
  @DisplayName("Verify Connecting to Mii domain WLS Remote Console through NGINX is successful")
  public void testWlsRemoteConsoleConnectionThroughNginx() {

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

  private static void createVoyagerIngressPathRoutingRules() {

    // set the annotations for Voyager
    HashMap<String, String> annotations = new HashMap<>();
    annotations.put("ingress.appscode.com/type", "NodePort");
    annotations.put("kubernetes.io/ingress.class", "voyager");
    annotations.put("ingress.appscode.com/rewrite-target", "/");

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

    assertDoesNotThrow(() -> createIngress(voyagerIngressName, domainNamespace, annotations, ingressRules, null));

    // wait until voyager ingress pod is ready
    withStandardRetryPolicy
        .conditionEvaluationListener(
            condition -> logger.info(
                "Waiting for Voyager ingress to be ready in namespace {0} (elapsed time {1}ms, remaining time {2}ms)",
                domainNamespace,
                condition.getElapsedTimeInMS(),
                condition.getRemainingTimeInMS()))
        .until(assertDoesNotThrow(() -> isVoyagerReady(domainNamespace, voyagerIngressName),
            "isVoyagerReady failed with ApiException"));

    // check the ingress was found in the domain namespace
    assertThat(assertDoesNotThrow(() -> listIngresses(domainNamespace)))
        .as(String.format("Test ingress %s was found in namespace %s", voyagerIngressName, domainNamespace))
        .withFailMessage(String.format("Ingress %s was not found in namespace %s", voyagerIngressName, domainNamespace))
        .contains(voyagerIngressName);

    logger.info("ingress {0} was created in namespace {1}", voyagerIngressName, domainNamespace);

    // check the ingress is ready to route the app to the server pod
    voyagerNodePort = assertDoesNotThrow(() ->
            getServiceNodePort(domainNamespace, VOYAGER_CHART_NAME + "-" + voyagerIngressName, "tcp-80"),
        "Getting voyager loadbalancer service node port failed");
    String curlCmd = "curl --silent --show-error --noproxy '*' http://" + K8S_NODEPORT_HOST + ":" + voyagerNodePort
        + "/weblogic/ready --write-out %{http_code} -o /dev/null";

    logger.info("Executing curl command {0}", curlCmd);
    assertTrue(callWebAppAndWaitTillReady(curlCmd, 60));
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
    String curlCmd = "curl -v --user weblogic:welcome1 -H Content-Type:application/json -d "
        + "\"{ \\" + "\"domainUrl\\" + "\"" + ": " + "\\" + "\"" + "http://"
        + K8S_NODEPORT_HOST + ":" + nodePort + "\\" + "\" }" + "\""
        + " http://localhost:8012/api/connection  --write-out %{http_code} -o /dev/null";
    logger.info("Executing default nodeport curl command {0}", curlCmd);
    assertTrue(callWebAppAndWaitTillReturnedCode(curlCmd, "201", 10), "Calling web app failed");
    logger.info("WebLogic domain is accessible through remote console");
  }
}
