// Copyright (c) 2020, 2021, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.weblogic.kubernetes;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import io.kubernetes.client.openapi.models.V1EnvVar;
import io.kubernetes.client.openapi.models.V1LocalObjectReference;
import io.kubernetes.client.openapi.models.V1ObjectMeta;
import io.kubernetes.client.openapi.models.V1SecretReference;
import oracle.weblogic.domain.AdminServer;
import oracle.weblogic.domain.Cluster;
import oracle.weblogic.domain.Configuration;
import oracle.weblogic.domain.Domain;
import oracle.weblogic.domain.DomainSpec;
import oracle.weblogic.domain.Istio;
import oracle.weblogic.domain.Model;
import oracle.weblogic.domain.ServerPod;
import oracle.weblogic.kubernetes.annotations.IntegrationTest;
import oracle.weblogic.kubernetes.annotations.Namespaces;
import oracle.weblogic.kubernetes.logging.LoggingFacade;
import oracle.weblogic.kubernetes.utils.ExecResult;
import org.awaitility.core.ConditionFactory;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static java.util.concurrent.TimeUnit.MINUTES;
import static java.util.concurrent.TimeUnit.SECONDS;
import static oracle.weblogic.kubernetes.TestConstants.ADMIN_PASSWORD_DEFAULT;
import static oracle.weblogic.kubernetes.TestConstants.ADMIN_USERNAME_DEFAULT;
import static oracle.weblogic.kubernetes.TestConstants.DOMAIN_API_VERSION;
import static oracle.weblogic.kubernetes.TestConstants.DOMAIN_VERSION;
import static oracle.weblogic.kubernetes.TestConstants.K8S_NODEPORT_HOST;
import static oracle.weblogic.kubernetes.TestConstants.OCIR_SECRET_NAME;
import static oracle.weblogic.kubernetes.TestConstants.WDT_BASIC_IMAGE_NAME;
import static oracle.weblogic.kubernetes.TestConstants.WDT_BASIC_IMAGE_TAG;
import static oracle.weblogic.kubernetes.TestConstants.WEBLOGIC_SLIM;
import static oracle.weblogic.kubernetes.actions.ActionConstants.ITTESTS_DIR;
import static oracle.weblogic.kubernetes.actions.ActionConstants.RESOURCE_DIR;
import static oracle.weblogic.kubernetes.actions.TestActions.addLabelsToNamespace;
import static oracle.weblogic.kubernetes.actions.TestActions.createDomainCustomResource;
import static oracle.weblogic.kubernetes.assertions.TestAssertions.domainExists;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.checkAppUsingHostHeader;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.checkPodReady;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.checkServiceExists;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.createOcirRepoSecret;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.createSecretWithUsernamePassword;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.generateFileFromTemplate;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.installAndVerifyOperator;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.setPodAntiAffinity;
import static oracle.weblogic.kubernetes.utils.DeployUtil.deployToClusterUsingRest;
import static oracle.weblogic.kubernetes.utils.IstioUtils.deployHttpIstioGatewayAndVirtualservice;
import static oracle.weblogic.kubernetes.utils.IstioUtils.deployIstioDestinationRule;
import static oracle.weblogic.kubernetes.utils.IstioUtils.getIstioHttpIngressPort;
import static oracle.weblogic.kubernetes.utils.ThreadSafeLogger.getLogger;
import static org.awaitility.Awaitility.with;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("Test to create two WebLogic domains in domainhome-in-image model with istio configuration")
@IntegrationTest
class ItIstioTwoDomainsInImage {

  private static String opNamespace = null;
  private static String domainNamespace1 = null;
  private static String domainNamespace2 = null;
  private static ConditionFactory withStandardRetryPolicy = null;
  private final String clusterName = "cluster-1"; // do not modify 
  private final String adminServerName = "admin-server"; // do not modify
  private final String domainUid1 = "istio-dii-wdt-1";
  private final String domainUid2 = "istio-dii-wdt-2";
  private final String adminServerPodName1 = domainUid1 + "-" + adminServerName;
  private final String adminServerPodName2 = domainUid2 + "-" + adminServerName;

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
    // create standard, reusable retry/backoff policy
    withStandardRetryPolicy = with().pollDelay(2, SECONDS)
        .and().with().pollInterval(10, SECONDS)
        .atMost(5, MINUTES).await();

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
    Map<String, String> labelMap = new HashMap();
    labelMap.put("istio-injection", "enabled");

    assertDoesNotThrow(() -> addLabelsToNamespace(domainNamespace1,labelMap));
    assertDoesNotThrow(() -> addLabelsToNamespace(domainNamespace2,labelMap));
    assertDoesNotThrow(() -> addLabelsToNamespace(opNamespace,labelMap));

    logger.info("Namespaces [{0}, {1}, {2}] labeled with istio-injection",
         opNamespace, domainNamespace1, domainNamespace2);

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
  public void testIstioTwoDomainsWithSingleIngress() {
    final String managedServerPrefix1 = domainUid1 + "-managed-server";
    final String managedServerPrefix2 = domainUid2 + "-managed-server";
    final int replicaCount = 2;

    // Create the repo secret to pull the image
    // this secret is used only for non-kind cluster
    createOcirRepoSecret(domainNamespace1);
    createOcirRepoSecret(domainNamespace2);

    // create secret for admin credentials
    logger.info("Create secret for admin credentials");
    String adminSecretName1 = "weblogic-credentials-1";
    createSecretWithUsernamePassword(adminSecretName1, domainNamespace1, 
         ADMIN_USERNAME_DEFAULT, ADMIN_PASSWORD_DEFAULT);

    String adminSecretName2 = "weblogic-credentials-2";
    createSecretWithUsernamePassword(adminSecretName2, domainNamespace2, 
         ADMIN_USERNAME_DEFAULT, ADMIN_PASSWORD_DEFAULT);

    // create the domain CR(s)
    createDomainResource(domainUid1, domainNamespace1, adminSecretName1, OCIR_SECRET_NAME,
        replicaCount);
    createDomainResource(domainUid2, domainNamespace2, adminSecretName2, OCIR_SECRET_NAME,
        replicaCount);

    logger.info("Check for domain custom resource in namespace {0}", domainNamespace1);
    withStandardRetryPolicy
        .conditionEvaluationListener(
            condition -> logger.info("Waiting for domain {0} to be created in namespace {1} "
                    + "(elapsed time {2}ms, remaining time {3}ms)",
                domainUid1,
                domainNamespace1,
                condition.getElapsedTimeInMS(),
                condition.getRemainingTimeInMS()))
        .until(domainExists(domainUid1, DOMAIN_VERSION, domainNamespace1));

    logger.info("Check for domain custom resource in namespace {0}", domainNamespace2);
    withStandardRetryPolicy
        .conditionEvaluationListener(
            condition -> logger.info("Waiting for domain {0} to be created in namespace {1} "
                    + "(elapsed time {2}ms, remaining time {3}ms)",
                domainUid2,
                domainNamespace2,
                condition.getElapsedTimeInMS(),
                condition.getRemainingTimeInMS()))
        .until(domainExists(domainUid2, DOMAIN_VERSION, domainNamespace2));

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
    Map<String, String> templateMap  = new HashMap();
    templateMap.put("NAMESPACE", domainNamespace1);
    templateMap.put("DUID", domainUid1);
    templateMap.put("ADMIN_SERVICE",adminServerPodName1);
    templateMap.put("CLUSTER_SERVICE", clusterService1);

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

    int istioIngressPort = getIstioHttpIngressPort();
    logger.info("Istio Ingress Port is {0}", istioIngressPort);

    // We can not verify Rest Management console thru Adminstration NodePort 
    // in istio, as we can not enable Adminstration NodePort
    if (!WEBLOGIC_SLIM) {
      String consoleUrl = "http://" + K8S_NODEPORT_HOST + ":" + istioIngressPort + "/console/login/LoginForm.jsp";
      boolean checkConsole = 
          checkAppUsingHostHeader(consoleUrl, domainNamespace1 + ".org");
      assertTrue(checkConsole, "Failed to access WebLogic console on domain1");
      logger.info("WebLogic console on domain1 is accessible");
    } else {
      logger.info("Skipping WebLogic console in WebLogic slim image");
    }
  
    Path archivePath = Paths.get(ITTESTS_DIR, "../operator/integration-tests/apps/testwebapp.war");
    ExecResult result = null;
    result = deployToClusterUsingRest(K8S_NODEPORT_HOST, 
        String.valueOf(istioIngressPort),
        ADMIN_USERNAME_DEFAULT, ADMIN_PASSWORD_DEFAULT, 
        clusterName, archivePath, domainNamespace1 + ".org", "testwebapp");
    assertNotNull(result, "Application deployment failed on domain1");
    logger.info("Application deployment on domain1 returned {0}", result.toString());
    assertEquals("202", result.stdout(), "Deployment didn't return HTTP status code 202");

    String url = "http://" + K8S_NODEPORT_HOST + ":" + istioIngressPort + "/testwebapp/index.jsp";
    logger.info("Application Access URL {0}", url);
    boolean checkApp = checkAppUsingHostHeader(url, domainNamespace1 + ".org");
    assertTrue(checkApp, "Failed to access WebLogic application on domain1");

    // We can not verify Rest Management console thru Adminstration NodePort 
    // in istio, as we can not enable Adminstration NodePort
    if (!WEBLOGIC_SLIM) {
      String consoleUrl = "http://" + K8S_NODEPORT_HOST + ":" + istioIngressPort + "/console/login/LoginForm.jsp";
      boolean checkConsole = 
          checkAppUsingHostHeader(consoleUrl, domainNamespace2 + ".org");
      assertTrue(checkConsole, "Failed to access domain2 WebLogic console");
      logger.info("WebLogic console on domain2 is accessible");
    } else {
      logger.info("Skipping WebLogic console in WebLogic slim image");
    }

    result = deployToClusterUsingRest(K8S_NODEPORT_HOST, 
        String.valueOf(istioIngressPort),
        ADMIN_USERNAME_DEFAULT, ADMIN_PASSWORD_DEFAULT, 
        clusterName, archivePath, domainNamespace2 + ".org", "testwebapp");
    assertNotNull(result, "Application deployment on domain2 failed");
    logger.info("Application deployment on domain2 returned {0}", result.toString());
    assertEquals("202", result.stdout(), "Deployment didn't return HTTP status code 202");

    logger.info("Application Access URL {0}", url);
    checkApp = checkAppUsingHostHeader(url, domainNamespace2 + ".org");
    assertTrue(checkApp, "Failed to access WebLogic application on domain2");

  }

  private void createDomainResource(String domainUid, String domNamespace, String adminSecretName,
                                    String repoSecretName, int replicaCount) {
    // In case of istio "default" channel can not be exposed through nodeport.
    // No AdminService on domain resource.
    Domain domain = new Domain()
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
                    .webLogicCredentialsSecret(new V1SecretReference()
                            .name(adminSecretName)
                            .namespace(domNamespace))
                    .includeServerOutInPodLog(true)
                    .serverStartPolicy("IF_NEEDED")
                    .serverPod(new ServerPod()
                            .addEnvItem(new V1EnvVar()
                                    .name("JAVA_OPTIONS")
                                    .value("-Dweblogic.StdoutDebugEnabled=false"))
                            .addEnvItem(new V1EnvVar()
                                    .name("USER_MEM_ARGS")
                                    .value("-Djava.security.egd=file:/dev/./urandom ")))
                    .adminServer(new AdminServer()
                            .serverStartState("RUNNING"))
                    .addClustersItem(new Cluster()
                            .clusterName(clusterName)
                            .replicas(replicaCount)
                            .serverStartState("RUNNING"))
                    .configuration(new Configuration()
                            .istio(new Istio()
                                 .enabled(Boolean.TRUE)
                                 .readinessPort(8888))
                            .model(new Model()
                                    .domainType("WLS"))
                        .introspectorJobActiveDeadlineSeconds(300L)));
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
