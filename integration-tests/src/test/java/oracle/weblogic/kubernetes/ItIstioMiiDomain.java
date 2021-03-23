// Copyright (c) 2020, 2021, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.weblogic.kubernetes;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
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
import oracle.weblogic.domain.OnlineUpdate;
import oracle.weblogic.domain.ServerPod;
import oracle.weblogic.kubernetes.annotations.IntegrationTest;
import oracle.weblogic.kubernetes.annotations.Namespaces;
import oracle.weblogic.kubernetes.logging.LoggingFacade;
import oracle.weblogic.kubernetes.utils.ExecResult;
import org.awaitility.core.ConditionFactory;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import static java.util.concurrent.TimeUnit.MINUTES;
import static java.util.concurrent.TimeUnit.SECONDS;
import static oracle.weblogic.kubernetes.TestConstants.ADMIN_PASSWORD_DEFAULT;
import static oracle.weblogic.kubernetes.TestConstants.ADMIN_USERNAME_DEFAULT;
import static oracle.weblogic.kubernetes.TestConstants.DOMAIN_API_VERSION;
import static oracle.weblogic.kubernetes.TestConstants.K8S_NODEPORT_HOST;
import static oracle.weblogic.kubernetes.TestConstants.MII_BASIC_IMAGE_NAME;
import static oracle.weblogic.kubernetes.TestConstants.MII_BASIC_IMAGE_TAG;
import static oracle.weblogic.kubernetes.TestConstants.OCIR_SECRET_NAME;
import static oracle.weblogic.kubernetes.TestConstants.WEBLOGIC_SLIM;
import static oracle.weblogic.kubernetes.actions.ActionConstants.ITTESTS_DIR;
import static oracle.weblogic.kubernetes.actions.ActionConstants.MODEL_DIR;
import static oracle.weblogic.kubernetes.actions.ActionConstants.RESOURCE_DIR;
import static oracle.weblogic.kubernetes.actions.TestActions.addLabelsToNamespace;
import static oracle.weblogic.kubernetes.actions.TestActions.patchDomainResourceWithNewIntrospectVersion;
import static oracle.weblogic.kubernetes.utils.CommonMiiTestUtils.replaceConfigMapWithModelFiles;
import static oracle.weblogic.kubernetes.utils.CommonMiiTestUtils.verifyIntrospectorRuns;
import static oracle.weblogic.kubernetes.utils.CommonMiiTestUtils.verifyPodIntrospectVersionUpdated;
import static oracle.weblogic.kubernetes.utils.CommonMiiTestUtils.verifyPodsNotRolled;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.checkAppUsingHostHeader;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.checkPodReady;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.checkServiceExists;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.createConfigMapAndVerify;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.createDomainAndVerify;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.createOcirRepoSecret;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.createSecretWithUsernamePassword;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.generateFileFromTemplate;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.getPodCreationTime;
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

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DisplayName("Test istio enabled WebLogic Domain in mii model")
@IntegrationTest
class ItIstioMiiDomain {

  private static String opNamespace = null;
  private static String domainNamespace = null;

  private String domainUid = "istio-mii";
  private String configMapName = "dynamicupdate-istio-configmap";
  private final String clusterName = "cluster-1"; // do not modify
  private final String adminServerPodName = domainUid + "-admin-server";
  private final String managedServerPrefix = domainUid + "-managed-server";
  private final String workManagerName = "newWM";
  private final int replicaCount = 2;
  

  // create standard, reusable retry/backoff policy
  private static ConditionFactory withStandardRetryPolicy = null;
  private static LoggingFacade logger = null;

  /**
   * Install Operator.
   * @param namespaces list of namespaces created by the IntegrationTestWatcher
  */
  @BeforeAll
  public static void initAll(@Namespaces(2) List<String> namespaces) {
    logger = getLogger();
    // create standard, reusable retry/backoff policy
    withStandardRetryPolicy = with().pollDelay(2, SECONDS)
        .and().with().pollInterval(10, SECONDS)
        .atMost(6, MINUTES).await();

    // get a new unique opNamespace
    logger.info("Assign unique namespace for Operator");
    assertNotNull(namespaces.get(0), "Namespace list is null");
    opNamespace = namespaces.get(0);

    logger.info("Assign unique namespace for Domain");
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
   * Create a domain using model-in-image model.
   * Add istio configuration with default readinessPort.
   * Do not add any AdminService under AdminServer configuration.
   * Deploy istio gateways and virtual service.
   * Verify server pods are in ready state and services are created.
   * Verify login to WebLogic console is successful thru istio ingress port.
   * Deploy a web application thru istio http ingress port using REST api.  
   * Access web application thru istio http ingress port using curl.
   */
  @Test
  @Order(1)
  @DisplayName("Create WebLogic Domain with mii model with istio")
  public void testIstioModelInImage() {

    // Create the repo secret to pull the image
    // this secret is used only for non-kind cluster
    createOcirRepoSecret(domainNamespace);

    // create secret for admin credentials
    logger.info("Create secret for admin credentials");
    String adminSecretName = "weblogic-credentials";
    assertDoesNotThrow(() -> createSecretWithUsernamePassword(
                                    adminSecretName,
                                    domainNamespace,
                                    "weblogic",
                                    "welcome1"),
        String.format("createSecret failed for %s", adminSecretName));

    // create encryption secret
    logger.info("Create encryption secret");
    String encryptionSecretName = "encryptionsecret";
    assertDoesNotThrow(() -> createSecretWithUsernamePassword(
                                      encryptionSecretName,
                                      domainNamespace,
                            "weblogicenc",
                            "weblogicenc"),
                    String.format("createSecret failed for %s", encryptionSecretName));

    // create WDT config map without any files
    createConfigMapAndVerify(configMapName, domainUid, domainNamespace, Collections.EMPTY_LIST);
    // create the domain object
    Domain domain = createDomainResource(domainUid,
                                      domainNamespace,
                                      adminSecretName,
                                      OCIR_SECRET_NAME,
                                      encryptionSecretName,
                                      replicaCount,
                              MII_BASIC_IMAGE_NAME + ":" + MII_BASIC_IMAGE_TAG,
                              configMapName);

    // create model in image domain
    createDomainAndVerify(domain, domainNamespace);

    logger.info("Check admin service {0} is created in namespace {1}",
        adminServerPodName, domainNamespace);
    checkServiceExists(adminServerPodName, domainNamespace);

    // check admin server pod is ready
    logger.info("Wait for admin server pod {0} to be ready in namespace {1}",
        adminServerPodName, domainNamespace);
    checkPodReady(adminServerPodName, domainUid, domainNamespace);

    // check managed server services created
    for (int i = 1; i <= replicaCount; i++) {
      logger.info("Check managed service {0} is created in namespace {1}",
          managedServerPrefix + i, domainNamespace);
      checkServiceExists(managedServerPrefix + i, domainNamespace);
    }

    // check managed server pods are ready
    for (int i = 1; i <= replicaCount; i++) {
      logger.info("Wait for managed pod {0} to be ready in namespace {1}",
          managedServerPrefix + i, domainNamespace);
      checkPodReady(managedServerPrefix + i, domainUid, domainNamespace);
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

    // We can not verify Rest Management console thru Adminstration NodePort 
    // in istio, as we can not enable Adminstration NodePort
    if (!WEBLOGIC_SLIM) {
      String consoleUrl = "http://" + K8S_NODEPORT_HOST + ":" + istioIngressPort + "/console/login/LoginForm.jsp";
      boolean checkConsole = 
          checkAppUsingHostHeader(consoleUrl, domainNamespace + ".org");
      assertTrue(checkConsole, "Failed to access WebLogic console");
      logger.info("WebLogic console is accessible");
    } else {
      logger.info("Skipping WebLogic console in WebLogic slim image");
    }
  
    Path archivePath = Paths.get(ITTESTS_DIR, "../operator/integration-tests/apps/testwebapp.war");
    ExecResult result = null;
    result = deployToClusterUsingRest(K8S_NODEPORT_HOST, 
        String.valueOf(istioIngressPort),
        ADMIN_USERNAME_DEFAULT, ADMIN_PASSWORD_DEFAULT, 
        clusterName, archivePath, domainNamespace + ".org", "testwebapp");
    assertNotNull(result, "Application deployment failed");
    logger.info("Application deployment returned {0}", result.toString());
    assertEquals("202", result.stdout(), "Deployment didn't return HTTP status code 202");

    String url = "http://" + K8S_NODEPORT_HOST + ":" + istioIngressPort + "/testwebapp/index.jsp";
    logger.info("Application Access URL {0}", url);
    boolean checkApp = checkAppUsingHostHeader(url, domainNamespace + ".org");
    assertTrue(checkApp, "Failed to access WebLogic application");
  }

  /**
   * Create a configmap containing model yaml to add a new work manager, 
   * a min threads constraint, and a max threads constraint
   * Patch the domain resource with the configmap.
   * Update the introspect version of the domain resource.
   * Verify rolling restart of the domain by comparing PodCreationTimestamp
   * before and after rolling restart.
   * Verify new work manager is configured.
   */
  @Test
  @Order(2)
  @DisplayName("Add a work manager to a model-in-image domain using dynamic update")
  public void testMiiIstioDynamicUpdate() {
    LinkedHashMap<String, OffsetDateTime> pods = new LinkedHashMap<>();
    // get the creation time of the admin server pod before patching
    OffsetDateTime adminPodCreationTime = getPodCreationTime(domainNamespace, adminServerPodName);
    pods.put(adminServerPodName, getPodCreationTime(domainNamespace, adminServerPodName));
    // get the creation time of the managed server pods before patching
    for (int i = 1; i <= replicaCount; i++) {
      pods.put(managedServerPrefix + i, getPodCreationTime(domainNamespace, managedServerPrefix + i));
    }
    for (int i = 1; i <= replicaCount; i++) {
      pods.put(managedServerPrefix + i, getPodCreationTime(domainNamespace, managedServerPrefix + i));
    }

    replaceConfigMapWithModelFiles(configMapName, domainUid, domainNamespace,
        Arrays.asList(MODEL_DIR + "/model.config.wm.yaml"), withStandardRetryPolicy);

    String introspectVersion = patchDomainResourceWithNewIntrospectVersion(domainUid, domainNamespace);

    verifyIntrospectorRuns(domainUid, domainNamespace);

    int istioIngressPort = getIstioHttpIngressPort();
    logger.info("Istio Ingress Port is {0}", istioIngressPort);

    String wmRuntimeUrl  = "http://" + K8S_NODEPORT_HOST + ":"  
           + istioIngressPort + "/management/weblogic/latest/domainRuntime"
           + "/serverRuntimes/managed-server1/applicationRuntimes"
           + "/testwebapp/workManagerRuntimes/newWM/"
           + "maxThreadsConstraintRuntime ";

    boolean checkWm =
          checkAppUsingHostHeader(wmRuntimeUrl, domainNamespace + ".org");
    assertTrue(checkWm, "Failed to access WorkManagerRuntime");
    logger.info("Found new work manager rintime");

    verifyPodsNotRolled(domainNamespace, pods);
    verifyPodIntrospectVersionUpdated(pods.keySet(), introspectVersion, domainNamespace);
  }

  private Domain createDomainResource(String domainUid, String domNamespace, 
           String adminSecretName, String repoSecretName, 
           String encryptionSecretName, int replicaCount, 
           String miiImage, String configmapName) {

    // create the domain CR
    Domain domain = new Domain()
        .apiVersion(DOMAIN_API_VERSION)
        .kind("Domain")
        .metadata(new V1ObjectMeta()
            .name(domainUid)
            .namespace(domNamespace))
        .spec(new DomainSpec()
            .domainUid(domainUid)
            .domainHomeSourceType("FromModel")
            .image(miiImage)
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
                         .domainType("WLS")
                         .configMap(configmapName)
                         .onlineUpdate(new OnlineUpdate().enabled(true))
                         .runtimeEncryptionSecret(encryptionSecretName))
            .introspectorJobActiveDeadlineSeconds(300L)));
    setPodAntiAffinity(domain);
    return domain;
  }

}
