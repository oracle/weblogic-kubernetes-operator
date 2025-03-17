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
import static oracle.weblogic.kubernetes.TestConstants.ISTIO_HTTP_HOSTPORT;
import static oracle.weblogic.kubernetes.TestConstants.K8S_NODEPORT_HOST;
import static oracle.weblogic.kubernetes.TestConstants.OKE_CLUSTER;
import static oracle.weblogic.kubernetes.TestConstants.SSL_PROPERTIES;
import static oracle.weblogic.kubernetes.TestConstants.TEST_IMAGES_REPO_SECRET_NAME;
import static oracle.weblogic.kubernetes.TestConstants.WDT_BASIC_IMAGE_NAME;
import static oracle.weblogic.kubernetes.TestConstants.WDT_BASIC_IMAGE_TAG;
import static oracle.weblogic.kubernetes.actions.ActionConstants.RESOURCE_DIR;
import static oracle.weblogic.kubernetes.actions.TestActions.addLabelsToNamespace;
import static oracle.weblogic.kubernetes.actions.TestActions.createDomainCustomResource;
import static oracle.weblogic.kubernetes.assertions.TestAssertions.domainExists;
import static oracle.weblogic.kubernetes.utils.ApplicationUtils.checkAppUsingHostHeader;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.checkServiceExists;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.createTestWebAppWarFile;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.formatIPv6Host;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.getServiceExtIPAddrtOke;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.isAppInServerPodReady;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.testUntil;
import static oracle.weblogic.kubernetes.utils.DeployUtil.deployToClusterUsingRest;
import static oracle.weblogic.kubernetes.utils.DeployUtil.deployUsingRest;
import static oracle.weblogic.kubernetes.utils.FileUtils.generateFileFromTemplate;
import static oracle.weblogic.kubernetes.utils.ImageUtils.createBaseRepoSecret;
import static oracle.weblogic.kubernetes.utils.ImageUtils.createTestRepoSecret;
import static oracle.weblogic.kubernetes.utils.IstioUtils.deployHttpIstioGatewayAndVirtualservice;
import static oracle.weblogic.kubernetes.utils.IstioUtils.deployIstioDestinationRule;
import static oracle.weblogic.kubernetes.utils.IstioUtils.getIstioHttpIngressPort;
import static oracle.weblogic.kubernetes.utils.OperatorUtils.installAndVerifyOperator;
import static oracle.weblogic.kubernetes.utils.PodUtils.checkPodReady;
import static oracle.weblogic.kubernetes.utils.PodUtils.setPodAntiAffinity;
import static oracle.weblogic.kubernetes.utils.SecretUtils.createSecretWithUsernamePassword;
import static oracle.weblogic.kubernetes.utils.ThreadSafeLogger.getLogger;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("Test to create two WebLogic domains in domainhome-in-image model with istio configuration")
@IntegrationTest
@Tag("kind-parallel")
@Tag("oke-parallel")
class ItIstioTwoDomainsInImage {

  private static String opNamespace = null;
  private static String domainNamespace1 = null;
  private static String domainNamespace2 = null;
  private final String clusterName = "cluster-1"; // do not modify
  private final String adminServerName = "admin-server"; // do not modify
  private final String domainUid1 = "istio-dii-wdt-1";
  private final String domainUid2 = "istio-dii-wdt-2";
  private final String adminServerPodName1 = domainUid1 + "-" + adminServerName;
  private final String adminServerPodName2 = domainUid2 + "-" + adminServerName;

  private static String testWebAppWarLoc = null;

  private static final String istioNamespace = "istio-system";
  private static final String istioIngressServiceName = "istio-ingressgateway";

  private static Map<String, Object> secretNameMap;
  private static LoggingFacade logger = null;

  /**
   * Install Operator.
   * @param namespaces list of namespaces created by the IntegrationTestWatcher by the
   JUnit engine parameter resolution mechanism
   */
  @BeforeAll
  public static void initAll(@Namespaces(3) List<String> namespaces) {
    logger = getLogger();

    // get a new unique opNamespace
    logger.info("Assigning unique namespace for Operator");
    assertNotNull(namespaces.get(0), "Namespace list is null");
    opNamespace = namespaces.get(0);

    logger.info("Assigning unique namespace for domain1");
    assertNotNull(namespaces.get(1), "Namespace list is null");
    domainNamespace1 = namespaces.get(1);

    logger.info("Assigning unique namespace for domain2");
    assertNotNull(namespaces.get(2), "Namespace list is null");
    domainNamespace2 = namespaces.get(2);

    // Label the domain/operator namespace with istio-injection=enabled
    Map<String, String> labelMap = new HashMap<>();
    labelMap.put("istio-injection", "enabled");

    assertDoesNotThrow(() -> addLabelsToNamespace(domainNamespace1,labelMap));
    assertDoesNotThrow(() -> addLabelsToNamespace(domainNamespace2,labelMap));
    assertDoesNotThrow(() -> addLabelsToNamespace(opNamespace,labelMap));

    logger.info("Namespaces [{0}, {1}, {2}] labeled with istio-injection",
         opNamespace, domainNamespace1, domainNamespace2);

    // create testwebapp.war
    testWebAppWarLoc = createTestWebAppWarFile(domainNamespace1);

    // install and verify operator
    installAndVerifyOperator(opNamespace, domainNamespace1,domainNamespace2);
  }

  /**
   * Create two domains using domainhome-in-image model.
   * Add istio configuration with default readinessPort.
   * Deploy istio gateway and virtual service on each domain namespaces.
   * Add host information to gateway and virtual service configurations.
   * Put the namespace.org as host configuration
   * Verify server pods are in ready state and services are created
   * Verify login to WebLogic console on domain1 through istio ingress http
   * port by passing host information in HTTP header.
   * Deploy a web application to domain1 through istio ingress http port
   * using host information in HTTP header.
   * Access web application through istio http ingress port using host
   * information in HTTP header.
   * Repeat the same steps for domain2.
   */
  @Test
  @DisplayName("Two WebLogic domainhome-in-image with single istio ingress")
  void testIstioTwoDomainsWithSingleIngress() throws UnknownHostException {
    final String managedServerPrefix1 = domainUid1 + "-managed-server";
    final String managedServerPrefix2 = domainUid2 + "-managed-server";
    final int replicaCount = 1;

    // Create the repo secret to pull the image
    // this secret is used only for non-kind cluster
    createTestRepoSecret(domainNamespace1);
    createTestRepoSecret(domainNamespace2);

    // create secret for admin credentials
    logger.info("Create secret for admin credentials");
    String adminSecretName1 = "weblogic-credentials-1";
    createSecretWithUsernamePassword(adminSecretName1, domainNamespace1,
         ADMIN_USERNAME_DEFAULT, ADMIN_PASSWORD_DEFAULT);

    String adminSecretName2 = "weblogic-credentials-2";
    createSecretWithUsernamePassword(adminSecretName2, domainNamespace2,
         ADMIN_USERNAME_DEFAULT, ADMIN_PASSWORD_DEFAULT);

    // create the domain CR(s)
    createDomainResource(domainUid1, domainNamespace1, adminSecretName1, TEST_IMAGES_REPO_SECRET_NAME,
        replicaCount);
    createDomainResource(domainUid2, domainNamespace2, adminSecretName2, TEST_IMAGES_REPO_SECRET_NAME,
        replicaCount);

    logger.info("Check for domain custom resource in namespace {0}", domainNamespace1);
    testUntil(
        domainExists(domainUid1, DOMAIN_VERSION, domainNamespace1),
        logger,
        "domain {0} to be created in namespace {1}",
        domainUid1,
        domainNamespace1);

    logger.info("Check for domain custom resource in namespace {0}", domainNamespace2);
    testUntil(
        domainExists(domainUid2, DOMAIN_VERSION, domainNamespace2),
        logger,
        "domain {0} to be created in namespace {1}",
        domainUid2,
        domainNamespace2);

    // check admin services are created
    logger.info("Check admin service {0} is created in namespace {1}",
        adminServerPodName1, domainNamespace1);
    checkServiceExists(adminServerPodName1, domainNamespace1);
    logger.info("Check admin service {0} is created in namespace {1}",
        adminServerPodName2, domainNamespace2);
    checkServiceExists(adminServerPodName2, domainNamespace2);

    // check admin server pods are ready
    logger.info("Wait for admin server pod {0} to be ready in namespace {1}",
        adminServerPodName1, domainNamespace1);
    checkPodReady(adminServerPodName1, domainUid1, domainNamespace1);
    logger.info("Wait for admin server pod {0} to be ready in namespace {1}",
        adminServerPodName2, domainNamespace2);
    checkPodReady(adminServerPodName2, domainUid2, domainNamespace2);

    // check managed server services are created
    for (int i = 1; i <= replicaCount; i++) {
      logger.info("Check managedserver service {0} is created in namespace {1}",
          managedServerPrefix1 + i, domainNamespace1);
      checkServiceExists(managedServerPrefix1 + i, domainNamespace1);
      logger.info("Check managedserver service {0} is created in namespace {1}",
          managedServerPrefix2 + i, domainNamespace2);
      checkServiceExists(managedServerPrefix2 + i, domainNamespace2);
    }

    // check managed server pods are ready
    for (int i = 1; i <= replicaCount; i++) {
      logger.info("Wait for managed pod {0} to be ready in namespace {1}",
          managedServerPrefix1 + i, domainNamespace1);
      checkPodReady(managedServerPrefix1 + i, domainUid1, domainNamespace1);
      logger.info("Wait for managed pod {0} to be ready in namespace {1}",
          managedServerPrefix2 + i, domainNamespace2);
      checkPodReady(managedServerPrefix2 + i, domainUid2, domainNamespace2);
    }

    String clusterService1 = domainUid1 + "-cluster-" + clusterName + "." + domainNamespace1 + ".svc.cluster.local";
    Map<String, String> templateMap  = new HashMap<>();
    templateMap.put("NAMESPACE", domainNamespace1);
    templateMap.put("DUID", domainUid1);
    templateMap.put("ADMIN_SERVICE",adminServerPodName1);
    templateMap.put("CLUSTER_SERVICE", clusterService1);
    templateMap.put("MANAGED_SERVER_PORT", "8001");

    Path srcHttpFile = Paths.get(RESOURCE_DIR, "istio", "istio-http-template.yaml");
    Path targetHttpFile = assertDoesNotThrow(
        () -> generateFileFromTemplate(srcHttpFile.toString(), "istio-http1.yaml", templateMap));
    logger.info("Generated Http VS/Gateway file path is {0} for domain1", targetHttpFile);
    boolean deployRes = assertDoesNotThrow(
        () -> deployHttpIstioGatewayAndVirtualservice(targetHttpFile));
    assertTrue(deployRes, "Failed to deploy Http Istio Gateway/VirtualService");

    Path srcDrFile = Paths.get(RESOURCE_DIR, "istio", "istio-dr-template.yaml");
    Path targetDrFile = assertDoesNotThrow(
        () -> generateFileFromTemplate(srcDrFile.toString(), "istio-dr1.yaml", templateMap));
    logger.info("Generated DestinationRule file path is {0}", targetDrFile);

    deployRes = assertDoesNotThrow(
        () -> deployIstioDestinationRule(targetDrFile));
    assertTrue(deployRes, "Failed to deploy Istio DestinationRule");
    String clusterService2 = domainUid2 + "-cluster-" + clusterName + "." + domainNamespace2 + ".svc.cluster.local";
    templateMap.put("NAMESPACE", domainNamespace2);
    templateMap.put("DUID", domainUid2);
    templateMap.put("ADMIN_SERVICE",adminServerPodName2);
    templateMap.put("CLUSTER_SERVICE", clusterService2);

    Path targetHttpFile2 = assertDoesNotThrow(
        () -> generateFileFromTemplate(srcHttpFile.toString(), "istio-http2.yaml", templateMap));
    logger.info("Generated Http VS/Gateway file path is {0} for domain2", targetHttpFile);
    deployRes = assertDoesNotThrow(
        () -> deployHttpIstioGatewayAndVirtualservice(targetHttpFile2));
    assertTrue(deployRes, "Failed to deploy Http Istio Gateway/VirtualService");

    Path targetDrFile2 = assertDoesNotThrow(
        () -> generateFileFromTemplate(srcDrFile.toString(), "istio-dr2.yaml", templateMap));
    logger.info("Generated DestinationRule file path is {0}", targetDrFile);

    deployRes = assertDoesNotThrow(
        () -> deployIstioDestinationRule(targetDrFile2));
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
    // In internal OKE env, use Istio EXTERNAL-IP; in non-OKE env, use K8S_NODEPORT_HOST + ":" + istioIngressPort
    String hostAndPort = getServiceExtIPAddrtOke(istioIngressServiceName, istioNamespace) != null
        ? getServiceExtIPAddrtOke(istioIngressServiceName, istioNamespace) : host + ":" + istioIngressPort;

    String readyAppUrl = "http://" + hostAndPort + "/weblogic/ready";
    boolean checlReadyApp = checkAppUsingHostHeader(readyAppUrl, domainNamespace1 + ".org");
    assertTrue(checlReadyApp, "Failed to access ready app on domain1");
    logger.info("WebLogic server on domain1 is accessible");

    Path archivePath = Paths.get(testWebAppWarLoc);
    String resourcePath = "/testwebapp/index.jsp";
    String target = "{identity: [clusters,'" + clusterName + "']}";

    // create secret for internal OKE cluster
    createBaseRepoSecret(domainNamespace1);

    ExecResult result = OKE_CLUSTER
        ? deployUsingRest(hostAndPort, ADMIN_USERNAME_DEFAULT, ADMIN_PASSWORD_DEFAULT,
            target, archivePath, domainNamespace1 + ".org", "testwebapp")
        : deployToClusterUsingRest(host, String.valueOf(istioIngressPort),
            ADMIN_USERNAME_DEFAULT, ADMIN_PASSWORD_DEFAULT,
            clusterName, archivePath, domainNamespace1 + ".org", "testwebapp");

    assertNotNull(result, "Application deployment failed on domain1");
    logger.info("Application deployment on domain1 returned {0}", result.toString());
    assertEquals("202", result.stdout(), "Deployment didn't return HTTP status code 202");
    logger.info("Application {0} deployed successfully at {1}", "testwebapp.war", domainUid1 + "-" + clusterName);

    if (OKE_CLUSTER) {
      testUntil(
          isAppInServerPodReady(domainNamespace1,
              managedServerPrefix1 + 1, 8001, resourcePath, "testwebapp"),
          logger, "Check Deployed App {0} in server {1}",
          archivePath,
          target);
    } else {
      String url = "http://" + host + ":" + istioIngressPort + "/testwebapp/index.jsp";
      logger.info("Application Access URL {0}", url);
      boolean checkApp = checkAppUsingHostHeader(url, domainNamespace1 + ".org");
      assertTrue(checkApp, "Failed to access WebLogic application on domain1");
    }
    logger.info("Application {0} is accessble to {1}", resourcePath, domainUid1);

    readyAppUrl = "http://" + hostAndPort + "/weblogic/ready";
    checlReadyApp = checkAppUsingHostHeader(readyAppUrl, domainNamespace2 + ".org");
    assertTrue(checlReadyApp, "Failed to access domain2 ready app");
    logger.info("ready app on domain2 is accessible");

    // In internal OKE env, deploy App in domain pods using WLST
    createBaseRepoSecret(domainNamespace2);

    result = OKE_CLUSTER
        ? deployUsingRest(hostAndPort, ADMIN_USERNAME_DEFAULT, ADMIN_PASSWORD_DEFAULT,
            target, archivePath, domainNamespace2 + ".org", "testwebapp")
        : deployToClusterUsingRest(host, String.valueOf(istioIngressPort),
            ADMIN_USERNAME_DEFAULT, ADMIN_PASSWORD_DEFAULT,
            clusterName, archivePath, domainNamespace2 + ".org", "testwebapp");

    assertNotNull(result, "Application deployment on domain2 failed");
    logger.info("Application deployment on domain2 returned {0}", result.toString());
    assertEquals("202", result.stdout(), "Deployment didn't return HTTP status code 202");
    logger.info("Application {0} deployed successfully at {1}", "testwebapp.war", domainUid2 + "-" + clusterName);

    if (OKE_CLUSTER) {
      testUntil(
          isAppInServerPodReady(domainNamespace2,
              managedServerPrefix2 + 1, 8001, resourcePath, "testwebapp"),
          logger, "Check Deployed App {0} in server {1}",
          archivePath,
          target);
    } else {
      String url = "http://" + host + ":" + istioIngressPort + resourcePath;
      logger.info("Application Access URL {0}", url);
      boolean checkApp = checkAppUsingHostHeader(url, domainNamespace2 + ".org");
      assertTrue(checkApp, "Failed to access WebLogic application on domain2");
    }
    logger.info("Application {0} is accessble to {1}", resourcePath, domainUid2);
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
