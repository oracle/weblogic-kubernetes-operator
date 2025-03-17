// Copyright (c) 2020, 2024, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.weblogic.kubernetes;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import io.kubernetes.client.openapi.models.V1EnvVar;
import io.kubernetes.client.openapi.models.V1LocalObjectReference;
import io.kubernetes.client.openapi.models.V1ObjectMeta;
import oracle.weblogic.domain.Configuration;
import oracle.weblogic.domain.DomainResource;
import oracle.weblogic.domain.DomainSpec;
import oracle.weblogic.domain.Model;
import oracle.weblogic.domain.ServerPod;
import oracle.weblogic.kubernetes.annotations.IntegrationTest;
import oracle.weblogic.kubernetes.annotations.Namespaces;
import oracle.weblogic.kubernetes.logging.LoggingFacade;
import oracle.weblogic.kubernetes.utils.ExecResult;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static oracle.weblogic.kubernetes.TestConstants.ADMIN_PASSWORD_DEFAULT;
import static oracle.weblogic.kubernetes.TestConstants.ADMIN_USERNAME_DEFAULT;
import static oracle.weblogic.kubernetes.TestConstants.DOMAIN_API_VERSION;
import static oracle.weblogic.kubernetes.TestConstants.DOMAIN_VERSION;
import static oracle.weblogic.kubernetes.TestConstants.IMAGE_PULL_POLICY;
import static oracle.weblogic.kubernetes.TestConstants.ISTIO_HTTP_HOSTPORT;
import static oracle.weblogic.kubernetes.TestConstants.K8S_NODEPORT_HOST;
import static oracle.weblogic.kubernetes.TestConstants.OKE_CLUSTER;
import static oracle.weblogic.kubernetes.TestConstants.SSL_PROPERTIES;
import static oracle.weblogic.kubernetes.TestConstants.TEST_IMAGES_REPO_SECRET_NAME;
import static oracle.weblogic.kubernetes.TestConstants.WDT_BASIC_IMAGE_NAME;
import static oracle.weblogic.kubernetes.TestConstants.WDT_BASIC_IMAGE_TAG;
import static oracle.weblogic.kubernetes.TestConstants.WEBLOGIC_IMAGE_TAG;
import static oracle.weblogic.kubernetes.actions.ActionConstants.RESOURCE_DIR;
import static oracle.weblogic.kubernetes.actions.TestActions.addLabelsToNamespace;
import static oracle.weblogic.kubernetes.actions.TestActions.createDomainCustomResource;
import static oracle.weblogic.kubernetes.assertions.TestAssertions.domainExists;
import static oracle.weblogic.kubernetes.utils.ApplicationUtils.checkAppUsingHostHeader;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.checkPodReadyAndServiceExists;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.createTestWebAppWarFile;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.formatIPv6Host;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.getServiceExtIPAddrtOke;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.isAppInServerPodReady;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.isWebLogicPsuPatchApplied;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.startPortForwardProcess;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.stopPortForwardProcess;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.testUntil;
import static oracle.weblogic.kubernetes.utils.DeployUtil.deployToClusterUsingRest;
import static oracle.weblogic.kubernetes.utils.DeployUtil.deployUsingRest;
import static oracle.weblogic.kubernetes.utils.ExecCommand.exec;
import static oracle.weblogic.kubernetes.utils.FileUtils.generateFileFromTemplate;
import static oracle.weblogic.kubernetes.utils.ImageUtils.createTestRepoSecret;
import static oracle.weblogic.kubernetes.utils.IstioUtils.createAdminServer;
import static oracle.weblogic.kubernetes.utils.IstioUtils.deployHttpIstioGatewayAndVirtualservice;
import static oracle.weblogic.kubernetes.utils.IstioUtils.deployIstioDestinationRule;
import static oracle.weblogic.kubernetes.utils.IstioUtils.getIstioHttpIngressPort;
import static oracle.weblogic.kubernetes.utils.OperatorUtils.installAndVerifyOperator;
import static oracle.weblogic.kubernetes.utils.PodUtils.setPodAntiAffinity;
import static oracle.weblogic.kubernetes.utils.SecretUtils.createSecretWithUsernamePassword;
import static oracle.weblogic.kubernetes.utils.ThreadSafeLogger.getLogger;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("Verify istio enabled WebLogic domain in domainhome-in-image model")
@IntegrationTest
@Tag("kind-parallel")
@Tag("oke-arm")
@Tag("oke-parallel")
class ItIstioDomainInImage {

  private static String opNamespace = null;
  private static String domainNamespace = null;
  private final String domainUid = "istio-dii-wdt";
  private final String clusterName = "cluster-1"; // do not modify
  private final String adminServerName = "admin-server"; // do not modify
  private final String adminServerPodName = domainUid + "-" + adminServerName;
  private static String testWebAppWarLoc = null;

  private static final String istioNamespace = "istio-system";
  private static final String istioIngressServiceName = "istio-ingressgateway";

  private static LoggingFacade logger = null;

  /**
   * Install Operator.
   * @param namespaces list of namespaces created by the IntegrationTestWatcher by the
   JUnit engine parameter resolution mechanism
   */
  @BeforeAll
  public static void initAll(@Namespaces(2) List<String> namespaces) {
    logger = getLogger();

    // get a new unique opNamespace
    logger.info("Assigning unique namespace for Operator");
    assertNotNull(namespaces.get(0), "Namespace list is null");
    opNamespace = namespaces.get(0);

    logger.info("Assigning unique namespace for Domain");
    assertNotNull(namespaces.get(1), "Namespace list is null");
    domainNamespace = namespaces.get(1);

    // Label the domain/operator namespace with istio-injection=enabled
    Map<String, String> labelMap = new HashMap<>();
    labelMap.put("istio-injection", "enabled");

    assertDoesNotThrow(() -> addLabelsToNamespace(domainNamespace,labelMap));
    assertDoesNotThrow(() -> addLabelsToNamespace(opNamespace,labelMap));

    // create testwebapp.war
    testWebAppWarLoc = createTestWebAppWarFile(domainNamespace);

    // install and verify operator
    installAndVerifyOperator(opNamespace, domainNamespace);
  }

  /**
   * Create a domain using domainhome-in-image model using wdt.
   * Add istio configuration with default readinessPort
   * Do not add any AdminService under AdminServer configuration
   * Deploy istio gateways and virtual service
   * Verify server pods are in ready state and services are created.
   * Verify ready app is accessible thru istio ingress http port
   * Verify ready app is accessible thru kubectl forwarded port(s)
   * Deploy a web application thru istio http ingress port using REST api
   * Access web application thru istio http ingress port using curl
   * Verify Security Warning Tool does not detect any security warning message
   *
   */
  @Test
  @DisplayName("Create WebLogic domainhome-in-image with istio")
  void testIstioDomainHomeInImage() throws UnknownHostException {
    final String managedServerPrefix = domainUid + "-managed-server";
    final int replicaCount = 1;

    // Create the repo secret to pull the image
    // this secret is used only for non-kind cluster
    createTestRepoSecret(domainNamespace);

    // create secret for admin credentials
    logger.info("Create secret for admin credentials");
    String adminSecretName = "weblogic-credentials";
    createSecretWithUsernamePassword(adminSecretName, domainNamespace, ADMIN_USERNAME_DEFAULT, ADMIN_PASSWORD_DEFAULT);

    // create the domain CR
    createDomainResource(domainUid, domainNamespace, adminSecretName, TEST_IMAGES_REPO_SECRET_NAME,
        replicaCount);

    // wait for the domain to exist
    logger.info("Check for domain custom resource in namespace {0}", domainNamespace);
    testUntil(
        domainExists(domainUid, DOMAIN_VERSION, domainNamespace),
        logger,
        "domain {0} to be created in namespace {1}",
        domainUid,
        domainNamespace);

    // check admin server service is created
    logger.info("Check admin service {0} is created in namespace {1}",
        adminServerPodName, domainNamespace);
    checkPodReadyAndServiceExists(adminServerPodName, domainUid, domainNamespace);
    // check managed server services created
    for (int i = 1; i <= replicaCount; i++) {
      logger.info("Check managed service {0} is created in namespace {1}",
          managedServerPrefix + i, domainNamespace);
      checkPodReadyAndServiceExists(managedServerPrefix + i, domainUid, domainNamespace);
    }

    String clusterService = domainUid + "-cluster-" + clusterName + "." + domainNamespace + ".svc.cluster.local";
    Map<String, String> templateMap  = new HashMap<>();
    templateMap.put("NAMESPACE", domainNamespace);
    templateMap.put("DUID", domainUid);
    templateMap.put("ADMIN_SERVICE",adminServerPodName);
    templateMap.put("CLUSTER_SERVICE", clusterService);
    templateMap.put("MANAGED_SERVER_PORT", "8001");

    Path srcHttpFile = Paths.get(RESOURCE_DIR, "istio", "istio-http-template.yaml");
    Path targetHttpFile = assertDoesNotThrow(
        () -> generateFileFromTemplate(srcHttpFile.toString(), "istio-http.yaml", templateMap));
    logger.info("Generated Http VS/Gateway file path is {0}", targetHttpFile);

    boolean deployRes = assertDoesNotThrow(
        () -> deployHttpIstioGatewayAndVirtualservice(targetHttpFile));
    assertTrue(deployRes, "Failed to deploy Http Istio Gateway/VirtualService");

    Path srcDrFile = Paths.get(RESOURCE_DIR, "istio", "istio-dr-template.yaml");
    Path targetDrFile = assertDoesNotThrow(
        () -> generateFileFromTemplate(srcDrFile.toString(), "istio-dr.yaml", templateMap));
    logger.info("Generated DestinationRule file path is {0}", targetDrFile);

    deployRes = assertDoesNotThrow(
        () -> deployIstioDestinationRule(targetDrFile));
    assertTrue(deployRes, "Failed to deploy Istio DestinationRule");

    String host;
    int istioIngressPort;
    if (TestConstants.KIND_CLUSTER
        && !TestConstants.WLSIMG_BUILDER.equals(TestConstants.WLSIMG_BUILDER_DEFAULT)) {
      host = formatIPv6Host(InetAddress.getLocalHost().getHostAddress());
      istioIngressPort = ISTIO_HTTP_HOSTPORT;
    } else {
      istioIngressPort = getIstioHttpIngressPort();
      logger.info("Istio Ingress Port is {0}", istioIngressPort);
      host = formatIPv6Host(K8S_NODEPORT_HOST);
    }

    // In internal OKE env, use Istio EXTERNAL-IP;
    // in non-internal-OKE env, use K8S_NODEPORT_HOST + ":" + istioIngressPort
    String hostAndPort = getServiceExtIPAddrtOke(istioIngressServiceName, istioNamespace) != null
        ? getServiceExtIPAddrtOke(istioIngressServiceName, istioNamespace) : host + ":" + istioIngressPort;
    
    String readyAppUrl = "http://" + hostAndPort + "/weblogic/ready";
    boolean checkReadyApp = checkAppUsingHostHeader(readyAppUrl, domainNamespace + ".org");
    assertTrue(checkReadyApp, "Failed to access ready app");
    logger.info("ready app is accessible");
    String localhost = "localhost";
    // Forward the non-ssl port 7001
    String forwardPort = startPortForwardProcess(localhost, domainNamespace, domainUid, 7001);
    assertNotNull(forwardPort, "port-forward fails to assign local port");
    logger.info("Forwarded local port is {0}", forwardPort);
    readyAppUrl = "http://" + localhost + ":" + forwardPort + "/weblogic/ready";
    checkReadyApp = checkAppUsingHostHeader(readyAppUrl, domainNamespace + ".org");
    assertTrue(checkReadyApp, "Failed to access ready app thru port-forwarded port");
    logger.info("ready app is accessible thru non-ssl port forwarding");
    // Forward the ssl port 7002
    forwardPort = startPortForwardProcess(localhost, domainNamespace, domainUid, 7002);
    assertNotNull(forwardPort, "(ssl) port-forward fails to assign local port");
    logger.info("Forwarded local port is {0}", forwardPort);
    readyAppUrl = "https://" + localhost + ":" + forwardPort + "/weblogic/ready";
    checkReadyApp = checkAppUsingHostHeader(readyAppUrl, domainNamespace + ".org");
    assertTrue(checkReadyApp, "Failed to access ready app thru port-forwarded port");
    logger.info("ready app is accessible thru ssl port forwarding");

    stopPortForwardProcess(domainNamespace);

    Path archivePath = Paths.get(testWebAppWarLoc);
    String target = "{identity: [clusters,'" + clusterName + "']}";
    ExecResult result = OKE_CLUSTER
        ? deployUsingRest(hostAndPort, ADMIN_USERNAME_DEFAULT, ADMIN_PASSWORD_DEFAULT,
            target, archivePath, domainNamespace + ".org", "testwebapp")
        : deployToClusterUsingRest(host, String.valueOf(istioIngressPort),
            ADMIN_USERNAME_DEFAULT, ADMIN_PASSWORD_DEFAULT,
            clusterName, archivePath, domainNamespace + ".org", "testwebapp");

    assertNotNull(result, "Application deployment failed");
    logger.info("Application deployment returned {0}", result.toString());
    assertEquals("202", result.stdout(), "Deployment didn't return HTTP status code 202");
    logger.info("Application {0} deployed successfully at {1}", "testwebapp.war", domainUid + "-" + clusterName);

    if (OKE_CLUSTER) {
      testUntil(isAppInServerPodReady(domainNamespace,
          managedServerPrefix + 1, 8001, "/testwebapp/index.jsp", "testwebapp"),
          logger, "Check Deployed App {0} in server {1}",
          archivePath,
          target);
    } else {
      String url = "http://" + hostAndPort + "/testwebapp/index.jsp";
      logger.info("Application Access URL {0}", url);
      boolean checkApp = checkAppUsingHostHeader(url, domainNamespace + ".org");
      assertTrue(checkApp, "Failed to access WebLogic application");
    }
    logger.info("Application /testwebapp/index.jsp is accessble to {0}", domainUid);

    // Verify that Security Warning Tool does not detect any security warning
    // messages on console.
    istioIngressPort = getIstioHttpIngressPort();

    logger.info("Istio Ingress Port is {0}", istioIngressPort);
    if (isWebLogicPsuPatchApplied()) {
      String curlCmd2 = "curl -g -j -sk --show-error --noproxy '*' "
          + " -H 'Host: " + domainNamespace + ".org'"
          + " --user " + ADMIN_USERNAME_DEFAULT + ":" + ADMIN_PASSWORD_DEFAULT
          + " --url http://" + hostAndPort
          + "/management/weblogic/latest/domainRuntime/domainSecurityRuntime?"
          + "link=none";

      logger.info("curl command {0}", curlCmd2);
      result = assertDoesNotThrow(
        () -> exec(curlCmd2, true));

      if (result.exitValue() == 0) {
        logger.info("curl command returned {0}", result.toString());
        assertTrue(result.stdout().contains("SecurityValidationWarnings"),
                "Could not access the Security Warning Tool page");
        assertTrue(!result.stdout().contains("minimum of umask 027"), "umask warning check failed");
        logger.info("No minimum umask warning reported");
      } else {
        assertTrue(false, "Curl command failed to get DomainSecurityRuntime");
      }
    } else {
      logger.info("Skipping Security warning check, since Security Warning tool "
           + " is not available in the WLS Release {0}", WEBLOGIC_IMAGE_TAG);
    }
  }

  private void createDomainResource(String domainUid, String domNamespace, String adminSecretName,
                                    String repoSecretName, int replicaCount) {
    // In case of istio "default" channel can not be exposed through nodeport.
    // No AdminService on domain resource.
    DomainResource domain = new DomainResource()
            .apiVersion(DOMAIN_API_VERSION)
            .kind("Domain")
            .metadata(new V1ObjectMeta()
                    .name(domainUid)
                    .namespace(domNamespace))
            .spec(new DomainSpec()
                    .domainUid(domainUid)
                    .domainHomeSourceType("Image")
                    .image(WDT_BASIC_IMAGE_NAME + ":" + WDT_BASIC_IMAGE_TAG)
                    .imagePullPolicy(IMAGE_PULL_POLICY)
                    .addImagePullSecretsItem(new V1LocalObjectReference()
                            .name(repoSecretName))
                    .webLogicCredentialsSecret(new V1LocalObjectReference()
                            .name(adminSecretName))
                    .includeServerOutInPodLog(true)
                    .serverStartPolicy("IfNeeded")
                    .serverPod(new ServerPod()
                            .addEnvItem(new V1EnvVar()
                                    .name("JAVA_OPTIONS")
                                    .value(SSL_PROPERTIES))
                            .addEnvItem(new V1EnvVar()
                                    .name("USER_MEM_ARGS")
                                    .value("-Djava.security.egd=file:/dev/./urandom ")))
                    .adminServer(createAdminServer())
                    .configuration(new Configuration()
                            .model(new Model()
                                    .domainType("WLS"))
                        .introspectorJobActiveDeadlineSeconds(3000L)));
    setPodAntiAffinity(domain);
    logger.info("Create domain custom resource for domainUid {0} in namespace {1}",
            domainUid, domNamespace);
    boolean domCreated = assertDoesNotThrow(() -> createDomainCustomResource(domain),
            String.format("Create domain custom resource failed with ApiException for %s in namespace %s",
                    domainUid, domNamespace));
    assertTrue(domCreated, String.format("Create domain custom resource failed with ApiException "
                    + "for %s in namespace %s", domainUid, domNamespace));
  }
}
