// Copyright (c) 2020, Oracle Corporation and/or its affiliates.
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
import oracle.weblogic.kubernetes.actions.impl.primitive.HelmParams;
import oracle.weblogic.kubernetes.annotations.IntegrationTest;
import oracle.weblogic.kubernetes.annotations.Namespaces;
import oracle.weblogic.kubernetes.annotations.tags.MustNotRunInParallel;
import oracle.weblogic.kubernetes.annotations.tags.Slow;
import oracle.weblogic.kubernetes.logging.LoggingFacade;
import oracle.weblogic.kubernetes.utils.DeployUtil;
import oracle.weblogic.kubernetes.utils.ExecResult;
import oracle.weblogic.kubernetes.utils.OracleHttpClient;
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
import static oracle.weblogic.kubernetes.TestConstants.REPO_SECRET_NAME;
import static oracle.weblogic.kubernetes.TestConstants.WDT_BASIC_IMAGE_NAME;
import static oracle.weblogic.kubernetes.TestConstants.WDT_BASIC_IMAGE_TAG;
import static oracle.weblogic.kubernetes.actions.ActionConstants.ITTESTS_DIR;
import static oracle.weblogic.kubernetes.actions.ActionConstants.RESOURCE_DIR;
import static oracle.weblogic.kubernetes.actions.TestActions.addLabelsToNamespace;
import static oracle.weblogic.kubernetes.actions.TestActions.createDomainCustomResource;
import static oracle.weblogic.kubernetes.assertions.TestAssertions.adminNodePortAccessible;
import static oracle.weblogic.kubernetes.assertions.TestAssertions.domainExists;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.checkPodReady;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.checkServiceExists;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.createDockerRegistrySecret;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.createSecretWithUsernamePassword;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.generateFileFromTemplate;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.installAndVerifyOperator;
import static oracle.weblogic.kubernetes.utils.IstioUtils.deployHttpIstioGatewayAndVirtualservice;
import static oracle.weblogic.kubernetes.utils.IstioUtils.deployIstioDestinationRule;
import static oracle.weblogic.kubernetes.utils.IstioUtils.getIstioHttpIngressPort;
import static oracle.weblogic.kubernetes.utils.IstioUtils.installIstio;
import static oracle.weblogic.kubernetes.utils.ThreadSafeLogger.getLogger;
import static org.awaitility.Awaitility.with;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("Test to create WebLogic domain in domainhome-in-image model with istio configuration")
@IntegrationTest
class ItIstioDomainInImage {

  private static HelmParams opHelmParams = null;
  private static String opNamespace = null;
  private static String operatorImage = null;
  private static String domainNamespace = null;
  private static ConditionFactory withStandardRetryPolicy = null;
  private static String dockerConfigJson = "";
  private final String domainUid = "istio-dii-wdt";
  private final String clusterName = "cluster-1"; // do not modify 
  private final String adminServerName = "admin-server"; // do not modify
  private final String adminServerPodName = domainUid + "-" + adminServerName;

  private static Map<String, Object> secretNameMap;
  private static LoggingFacade logger = null;

  /**
   * Install Operator.
   * @param namespaces list of namespaces created by the IntegrationTestWatcher by the
   JUnit engine parameter resolution mechanism
   */
  @BeforeAll
  public static void initAll(@Namespaces(2) List<String> namespaces) {
    logger = getLogger();
    installIstio();
    // create standard, reusable retry/backoff policy
    withStandardRetryPolicy = with().pollDelay(2, SECONDS)
        .and().with().pollInterval(10, SECONDS)
        .atMost(5, MINUTES).await();

    // get a new unique opNamespace
    logger.info("Creating unique namespace for Operator");
    assertNotNull(namespaces.get(0), "Namespace list is null");
    opNamespace = namespaces.get(0);

    logger.info("Creating unique namespace for Domain");
    assertNotNull(namespaces.get(1), "Namespace list is null");
    domainNamespace = namespaces.get(1);

    // Label the domain/operator namespace with istio-injection=enabled
    
    Map<String, String> labelMap = new HashMap();
    labelMap.put("istio-injection", "enabled");

    assertDoesNotThrow(() -> addLabelsToNamespace(domainNamespace,labelMap));
    assertDoesNotThrow(() -> addLabelsToNamespace(opNamespace,labelMap));

    // install and verify operator
    installAndVerifyOperator(opNamespace, domainNamespace);

  }

  /**
   * Create a domain using domainhome-in-image model.
   * Add istio configuration with default readinessPort 
   * Do not add any AdminService under AdminServer configuration
   * Deploy istio gateways and virtual service 
   * Verify domain pods runs in ready state and services are created.
   * Verify login to WebLogic console is successful thru istio ingress http port.
   * Deploy a web application thru istio http ingress port using REST api  
   * Access web application thru istio http ingress port using curl
   */
  @Test
  @DisplayName("Create WebLogic domainhome-in-image with istio")
  @Slow
  @MustNotRunInParallel
  public void testIstioDomainHomeInImageUsingWdt() {
    final String managedServerPrefix = domainUid + "-managed-server";
    final int replicaCount = 2;

    // Create the repo secret to pull the image
    createDockerRegistrySecret(domainNamespace);

    // create secret for admin credentials
    logger.info("Create secret for admin credentials");
    String adminSecretName = "weblogic-credentials";
    createSecretWithUsernamePassword(adminSecretName, domainNamespace, ADMIN_USERNAME_DEFAULT, ADMIN_PASSWORD_DEFAULT);

    // create the domain CR
    createDomainResource(domainUid, domainNamespace, adminSecretName, REPO_SECRET_NAME,
        replicaCount);

    // wait for the domain to exist
    logger.info("Check for domain custom resource in namespace {0}", domainNamespace);
    withStandardRetryPolicy
        .conditionEvaluationListener(
            condition -> logger.info("Waiting for domain {0} to be created in namespace {1} "
                    + "(elapsed time {2}ms, remaining time {3}ms)",
                domainUid,
                domainNamespace,
                condition.getElapsedTimeInMS(),
                condition.getRemainingTimeInMS()))
        .until(domainExists(domainUid, DOMAIN_VERSION, domainNamespace));

    // check admin server pod is ready
    logger.info("Wait for admin server pod {0} to be ready in namespace {1}",
        adminServerPodName, domainNamespace);
    checkPodReady(adminServerPodName, domainUid, domainNamespace);

    // check managed server pods are ready
    for (int i = 1; i <= replicaCount; i++) {
      logger.info("Wait for managed server pod {0} to be ready in namespace {1}",
          managedServerPrefix + i, domainNamespace);
      checkPodReady(managedServerPrefix + i, domainUid, domainNamespace);
    }

    logger.info("Check admin service {0} is created in namespace {1}",
        adminServerPodName, domainNamespace);
    checkServiceExists(adminServerPodName, domainNamespace);

    // check managed server services created
    for (int i = 1; i <= replicaCount; i++) {
      logger.info("Check managedserver service {0} is created in namespace {1}",
          managedServerPrefix + i, domainNamespace);
      checkServiceExists(managedServerPrefix + i, domainNamespace);
    }

    String clusterService = domainUid + "-cluster-" + clusterName + "." + domainNamespace + ".svc.cluster.local";

    Map<String, String> templateMap  = new HashMap();
    templateMap.put("NAMESPACE", domainNamespace);
    templateMap.put("DUID", domainUid);
    templateMap.put("ADMIN_SERVICE",adminServerPodName);
    templateMap.put("CLUSTER_SERVICE", clusterService);

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

    int istioIngressPort = getIstioHttpIngressPort();
    logger.info("Istio Ingress Port is {0}", istioIngressPort);

    logger.info("Validating WebLogic admin server access by login to console");
    boolean loginSuccessful = assertDoesNotThrow(() -> {
      return adminNodePortAccessible(istioIngressPort, ADMIN_USERNAME_DEFAULT, ADMIN_PASSWORD_DEFAULT);
    }, "Access to admin server node port failed");
    assertTrue(loginSuccessful, "Console login validation failed");

    Path archivePath = Paths.get(ITTESTS_DIR, "../src/integration-tests/apps/testwebapp.war");
    ExecResult result = null;
    result = DeployUtil.deployUsingRest(K8S_NODEPORT_HOST, 
        String.valueOf(istioIngressPort),
        ADMIN_USERNAME_DEFAULT, ADMIN_PASSWORD_DEFAULT, 
        clusterName, archivePath);
    assertNotNull(result, "Application deployment failed");
    logger.info("Application deployment returned {0}", result.toString());
    assertEquals("202", result.stdout(), "Application deployed successfully");
    String url = "http://" + K8S_NODEPORT_HOST + ":" + istioIngressPort + "/testwebapp/index.jsp";
    logger.info("Application Access URL {0}", url);

    try {
      Thread.sleep(5 * 1000);
    } catch (InterruptedException ie) {
      //    
    }
    assertEquals(200,
        assertDoesNotThrow(() -> OracleHttpClient.get(url, true),
            "Accessing sample application on admin server failed")
            .statusCode(), "Status code not equals to 200");
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

    logger.info("Create domain custom resource for domainUid {0} in namespace {1}",
            domainUid, domNamespace);
    boolean domCreated = assertDoesNotThrow(() -> createDomainCustomResource(domain),
            String.format("Create domain custom resource failed with ApiException for %s in namespace %s",
                    domainUid, domNamespace));
    assertTrue(domCreated, String.format("Create domain custom resource failed with ApiException "
                    + "for %s in namespace %s", domainUid, domNamespace));
  }

}
