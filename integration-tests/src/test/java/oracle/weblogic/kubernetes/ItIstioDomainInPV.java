// Copyright (c) 2020, 2023, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.weblogic.kubernetes;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import io.kubernetes.client.openapi.models.V1Container;
import io.kubernetes.client.openapi.models.V1EnvVar;
import io.kubernetes.client.openapi.models.V1LocalObjectReference;
import io.kubernetes.client.openapi.models.V1ObjectMeta;
import io.kubernetes.client.openapi.models.V1PersistentVolumeClaimVolumeSource;
import io.kubernetes.client.openapi.models.V1SecretReference;
import io.kubernetes.client.openapi.models.V1Volume;
import io.kubernetes.client.openapi.models.V1VolumeMount;
import oracle.weblogic.domain.AdminService;
import oracle.weblogic.domain.Channel;
import oracle.weblogic.domain.Cluster;
import oracle.weblogic.domain.Configuration;
import oracle.weblogic.domain.Domain;
import oracle.weblogic.domain.DomainSpec;
import oracle.weblogic.domain.Istio;
import oracle.weblogic.domain.ServerPod;
import oracle.weblogic.kubernetes.actions.impl.primitive.Kubernetes;
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
import static oracle.weblogic.kubernetes.TestConstants.BASE_IMAGES_REPO_SECRET_NAME;
import static oracle.weblogic.kubernetes.TestConstants.DOMAIN_API_VERSION;
import static oracle.weblogic.kubernetes.TestConstants.K8S_NODEPORT_HOST;
import static oracle.weblogic.kubernetes.TestConstants.KIND_REPO;
import static oracle.weblogic.kubernetes.TestConstants.LOCALE_IMAGE_NAME;
import static oracle.weblogic.kubernetes.TestConstants.LOCALE_IMAGE_TAG;
import static oracle.weblogic.kubernetes.TestConstants.WEBLOGIC_IMAGE_TO_USE_IN_SPEC;
import static oracle.weblogic.kubernetes.TestConstants.WEBLOGIC_SLIM;
import static oracle.weblogic.kubernetes.actions.ActionConstants.ITTESTS_DIR;
import static oracle.weblogic.kubernetes.actions.ActionConstants.RESOURCE_DIR;
import static oracle.weblogic.kubernetes.actions.TestActions.addLabelsToNamespace;
import static oracle.weblogic.kubernetes.actions.TestActions.scaleCluster;
import static oracle.weblogic.kubernetes.utils.ApplicationUtils.checkAppUsingHostHeader;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.checkPodReadyAndServiceExists;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.checkServiceExists;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.getNextFreePort;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.getUniqueName;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.withLongRetryPolicy;
import static oracle.weblogic.kubernetes.utils.ConfigMapUtils.createConfigMapForDomainCreation;
import static oracle.weblogic.kubernetes.utils.DeployUtil.deployToClusterUsingRest;
import static oracle.weblogic.kubernetes.utils.DomainUtils.createDomainAndVerify;
import static oracle.weblogic.kubernetes.utils.FileUtils.generateFileFromTemplate;
import static oracle.weblogic.kubernetes.utils.ImageUtils.createBaseRepoSecret;
import static oracle.weblogic.kubernetes.utils.IstioUtils.createAdminServer;
import static oracle.weblogic.kubernetes.utils.IstioUtils.deployHttpIstioGatewayAndVirtualservice;
import static oracle.weblogic.kubernetes.utils.IstioUtils.getIstioHttpIngressPort;
import static oracle.weblogic.kubernetes.utils.IstioUtils.isLocalHostBindingsEnabled;
import static oracle.weblogic.kubernetes.utils.JobUtils.createDomainJob;
import static oracle.weblogic.kubernetes.utils.OperatorUtils.installAndVerifyOperator;
import static oracle.weblogic.kubernetes.utils.PatchDomainUtils.patchServerStartPolicy;
import static oracle.weblogic.kubernetes.utils.PersistentVolumeUtils.createPV;
import static oracle.weblogic.kubernetes.utils.PersistentVolumeUtils.createPVC;
import static oracle.weblogic.kubernetes.utils.PodUtils.checkPodDeleted;
import static oracle.weblogic.kubernetes.utils.PodUtils.checkPodReady;
import static oracle.weblogic.kubernetes.utils.PodUtils.setPodAntiAffinity;
import static oracle.weblogic.kubernetes.utils.SecretUtils.createSecretWithUsernamePassword;
import static oracle.weblogic.kubernetes.utils.ThreadSafeLogger.getLogger;
import static org.awaitility.Awaitility.with;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests to create domain in persistent volume using WLST.
 */
@DisplayName("Verify istio enabled WebLogic domain in domainhome-on-pv model")
@IntegrationTest
class ItIstioDomainInPV  {

  private static String opNamespace = null;
  private static String domainNamespace = null;

  private final String wlSecretName = "weblogic-credentials";
  private final String domainUid = "istio-dpv";
  private final String clusterName = "cluster-1";
  private final String adminServerName = "adminserver";
  private final String adminServerPodName = domainUid + "-" + adminServerName;
  private static LoggingFacade logger = null;

  // create standard, reusable retry/backoff policy
  private static final ConditionFactory withStandardRetryPolicy
      = with().pollDelay(2, SECONDS)
      .and().with().pollInterval(10, SECONDS)
      .atMost(5, MINUTES).await();


  /**
   * Assigns unique namespaces for operator and domains.
   * Pull WebLogic image if running tests in Kind cluster.
   * Installs operator.
   *
   * @param namespaces injected by JUnit
   */
  @BeforeAll
  public static void initAll(@Namespaces(2) List<String> namespaces) {

    logger = getLogger();
    logger.info("Assign a unique namespace for operator");
    assertNotNull(namespaces.get(0), "Namespace is null");
    opNamespace = namespaces.get(0);

    logger.info("Assign a unique namespace for WebLogic domain");
    assertNotNull(namespaces.get(1), "Namespace is null");
    domainNamespace = namespaces.get(1);

    // Label the operator/domain namespace with istio-injection=enabled
    Map<String, String> labelMap = new HashMap();
    labelMap.put("istio-injection", "enabled");

    assertDoesNotThrow(() -> addLabelsToNamespace(domainNamespace,labelMap));
    assertDoesNotThrow(() -> addLabelsToNamespace(opNamespace,labelMap));

    // install operator and verify its running in ready state
    installAndVerifyOperator(opNamespace, domainNamespace);
  }

  /**
   * Create a WebLogic domain using WLST in a persistent volume.
   * Use WebLogic (12.2.1.4) base image with Japanese Locale.
   * Add istio configuration.
   * Deploy istio gateways and virtual service.
   * Verify domain pods runs in ready state and services are created.
   * Check WebLogic Server log for few Japanese characters.
   * Verify login to WebLogic console is successful thru istio ingress Port.
   * Additionally, the test verifies that WebLogic cluster can be scaled down
   * and scaled up in the absence of Administration server.
   */
  @Test
  @DisplayName("Create WebLogic domain in PV with Istio")
  void testIstioDomainHomeInPv() {

    final String managedServerNameBase = "managed-";
    String managedServerPrefix = domainUid + "-" + managedServerNameBase;
    final int replicaCount = 2;
    final int t3ChannelPort = getNextFreePort();

    final String pvName = getUniqueName(domainUid + "-pv-");
    final String pvcName = getUniqueName(domainUid + "-pvc-");

    // create pull secrets for WebLogic image when running in non Kind Kubernetes cluster
    // this secret is used only for non-kind cluster
    createBaseRepoSecret(domainNamespace);

    // create WebLogic domain credential secret
    createSecretWithUsernamePassword(wlSecretName, domainNamespace,
        ADMIN_USERNAME_DEFAULT, ADMIN_PASSWORD_DEFAULT);

    // create persistent volume and persistent volume claim for domain
    // these resources should be labeled with domainUid for cleanup after test
    createPV(pvName, domainUid, this.getClass().getSimpleName());
    createPVC(pvName, pvcName, domainUid, domainNamespace);

    // create a temporary WebLogic domain property file
    File domainPropertiesFile = assertDoesNotThrow(() ->
            File.createTempFile("domain", "properties"),
        "Failed to create domain properties file");
    Properties p = new Properties();
    p.setProperty("domain_path", "/shared/domains");
    p.setProperty("domain_name", domainUid);
    p.setProperty("domain_uid", domainUid);
    p.setProperty("cluster_name", clusterName);
    p.setProperty("admin_server_name", adminServerName);
    p.setProperty("managed_server_port", "8001");
    p.setProperty("admin_server_port", "7001");
    p.setProperty("admin_username", ADMIN_USERNAME_DEFAULT);
    p.setProperty("admin_password", ADMIN_PASSWORD_DEFAULT);
    p.setProperty("admin_t3_public_address", K8S_NODEPORT_HOST);
    p.setProperty("admin_t3_channel_port", Integer.toString(t3ChannelPort));
    p.setProperty("number_of_ms", "4");
    p.setProperty("managed_server_name_base", managedServerNameBase);
    p.setProperty("domain_logs", "/shared/logs");
    p.setProperty("production_mode_enabled", "true");
    assertDoesNotThrow(() ->
            p.store(new FileOutputStream(domainPropertiesFile), "wlst properties file"),
        "Failed to write domain properties file");

    // WLST script for creating domain
    Path wlstScript = Paths.get(RESOURCE_DIR, "python-scripts", "wlst-create-istio-domain-onpv.py");

    // create configmap and domain on persistent volume using the WLST script and property file
    createDomainOnPVUsingWlst(wlstScript, domainPropertiesFile.toPath(),
        pvName, pvcName, domainNamespace);

    // Use the WebLogic(12.2.1.4) Base Image with Japanese Locale
    String imageLocation = null;
    if (KIND_REPO != null) {
      imageLocation = KIND_REPO + "test-images/weblogic:" + LOCALE_IMAGE_TAG;
    } else {
      imageLocation = LOCALE_IMAGE_NAME + ":" + LOCALE_IMAGE_TAG;
    }

    // Enable istio in domain custom resource configuration object.
    // Add T3Channel Service with port assigned to Istio TCP ingress port.
    logger.info("Creating domain custom resource");

    Domain domain = new Domain()
        .apiVersion(DOMAIN_API_VERSION)
        .kind("Domain")
        .metadata(new V1ObjectMeta()
            .name(domainUid)
            .namespace(domainNamespace))
        .spec(new DomainSpec()
            .domainUid(domainUid)
            .domainHome("/shared/domains/" + domainUid)
            .domainHomeSourceType("PersistentVolume")
            .image(imageLocation)
            .imagePullPolicy("IfNotPresent")
            .imagePullSecrets(Arrays.asList(
                new V1LocalObjectReference()
                    .name(BASE_IMAGES_REPO_SECRET_NAME)))     // this secret is used only on non-kind cluster
            .webLogicCredentialsSecret(new V1SecretReference()
                .name(wlSecretName)
                .namespace(domainNamespace))
            .includeServerOutInPodLog(true)
            .logHomeEnabled(Boolean.TRUE)
            .logHome("/shared/logs/" + domainUid)
            .dataHome("")
            .serverStartPolicy("IF_NEEDED")
            .serverPod(new ServerPod() //serverpod
                .addEnvItem(new V1EnvVar()
                    .name("LANG")
                    .value("ja_JP.utf8"))
                .addEnvItem(new V1EnvVar()
                    .name("JAVA_OPTIONS")
                    .value("-Dweblogic.StdoutDebugEnabled=false"))
                .addEnvItem(new V1EnvVar()
                    .name("USER_MEM_ARGS")
                    .value("-Djava.security.egd=file:/dev/./urandom "))
                .addVolumesItem(new V1Volume()
                    .name(pvName)
                    .persistentVolumeClaim(new V1PersistentVolumeClaimVolumeSource()
                        .claimName(pvcName)))
                .addVolumeMountsItem(new V1VolumeMount()
                    .mountPath("/shared")
                    .name(pvName)))
            .adminServer(createAdminServer()
                .adminService(new AdminService()
                    .addChannelsItem(new Channel()
                        .channelName("T3Channel")
                        .nodePort(t3ChannelPort))))
            .addClustersItem(new Cluster() //cluster
                .clusterName(clusterName)
                .replicas(replicaCount)
                .serverStartState("RUNNING"))
            .configuration(new Configuration()
                .istio(new Istio()
                    .enabled(Boolean.TRUE)
                    .readinessPort(8888)
                    .localhostBindingsEnabled(isLocalHostBindingsEnabled()))));
    setPodAntiAffinity(domain);
    // verify the domain custom resource is created
    createDomainAndVerify(domain, domainNamespace);

    // verify admin server pod is ready
    logger.info("Wait for admin server pod {0} to be ready in namespace {1}",
        adminServerPodName, domainNamespace);
    checkPodReadyAndServiceExists(adminServerPodName, domainUid, domainNamespace);
    // verify managed server services created
    for (int i = 1; i <= replicaCount; i++) {
      logger.info("Check managed service {0} is created in namespace {1}",
              managedServerPrefix + i, domainNamespace);
      checkPodReadyAndServiceExists(withLongRetryPolicy, managedServerPrefix + i, domainUid, domainNamespace);
    }
    
    // Make sure Japanese character is found in server pod log
    assertTrue(matchPodLog(),"LANG is not set to ja_JP.utf8");

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

    int istioIngressPort = getIstioHttpIngressPort();
    logger.info("Istio http ingress Port is {0}", istioIngressPort);

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
    for (int i = 1; i <= 10; i++) {
      result = deployToClusterUsingRest(K8S_NODEPORT_HOST,
        String.valueOf(istioIngressPort),
        ADMIN_USERNAME_DEFAULT, ADMIN_PASSWORD_DEFAULT,
        clusterName, archivePath, domainNamespace + ".org", "testwebapp");
      assertNotNull(result, "Application deployment failed");
      logger.info("(Loop:{0}) Application deployment returned {1}", i, result.toString());
      if (result.stdout().equals("202")) {
        break;
      }
    }
    assertEquals("202", result.stdout(), "Application deployment failed with wrong HTTP code");

    String url = "http://" + K8S_NODEPORT_HOST + ":" + istioIngressPort + "/testwebapp/index.jsp";
    logger.info("Application Access URL {0}", url);
    boolean checkApp = checkAppUsingHostHeader(url, domainNamespace + ".org");
    assertTrue(checkApp, "Failed to access WebLogic application");

    // Stop and Start the managed server in absense of administration server
    assertTrue(patchServerStartPolicy(domainUid, domainNamespace,
         "/spec/adminServer/serverStartPolicy", "NEVER"),
         "Failed to patch administrationi server serverStartPolicy to NEVER");
    logger.info("Domain is patched to shutdown administration server");
    checkPodDeleted(adminServerPodName, domainUid, domainNamespace);
    logger.info("Administration server shutdown success");

    boolean scalingSuccess = assertDoesNotThrow(() ->
        scaleCluster(domainUid, domainNamespace, "cluster-1", 1),
        String.format("Scaling down cluster cluster-1 of domain %s in namespace %s failed",
        domainUid, domainNamespace));
    assertTrue(scalingSuccess,
        String.format("Cluster scaling failed for domain %s in namespace %s", domainUid, domainNamespace));
    logger.info("Cluster is scaled down in absense of administration server");
    checkPodDeleted(managedServerPrefix + "2", domainUid, domainNamespace);
    logger.info("Managed Server stopped in absense of administration server");

    scalingSuccess = assertDoesNotThrow(() ->
        scaleCluster(domainUid, domainNamespace, "cluster-1", 2),
        String.format("Scaling up cluster cluster-1 of domain %s in namespace %s failed",
        domainUid, domainNamespace));
    assertTrue(scalingSuccess,
        String.format("Cluster scaling failed for domain %s in namespace %s", domainUid, domainNamespace));
    logger.info("Cluster is scaled up in absense of administration server");
    checkServiceExists(managedServerPrefix + "2", domainNamespace);
    checkPodReady(managedServerPrefix + "2", domainUid, domainNamespace);
    logger.info("Managed Server started in absense of administration server");
  }

  // Looks for some Japanese Character in Server Pod Logs
  private boolean matchPodLog() {
    String toMatch = "起動しました";
    // toMatch = "起起起モードで起動しました"; test fails
    String podLog = null;
    try {
      podLog = Kubernetes.getPodLog("istio-dpv-managed-1", domainNamespace, "weblogic-server");
      logger.info("{0}", podLog);
      logger.info("Looking for string [{0}] in Pod log", toMatch);
      if (podLog.contains(toMatch))  {
        logger.info("Found the string [{0}] in Pod log", toMatch);
        return true;
      } else {
        logger.info("Matching string  [{0}] Not found in Pod log", toMatch);
        return false;
      }
    } catch (Exception e) {
      e.printStackTrace();
      return false;
    }
  } 

  /**
   * Create a WebLogic domain on a persistent volume by doing the following.
   * Create a configmap containing WLST script and property file.
   * Create a Kubernetes job to create domain on persistent volume.
   *
   * @param wlstScriptFile       python script to create domain
   * @param domainPropertiesFile properties file containing domain configuration
   * @param pvName               name of the persistent volume to create domain in
   * @param pvcName              name of the persistent volume claim
   * @param namespace            name of the domain namespace in which the job is created
   */
  private void createDomainOnPVUsingWlst(Path wlstScriptFile, Path domainPropertiesFile,
                                         String pvName, String pvcName, String namespace) {
    logger.info("Preparing to run create domain job using WLST");

    List<Path> domainScriptFiles = new ArrayList<>();
    domainScriptFiles.add(wlstScriptFile);
    domainScriptFiles.add(domainPropertiesFile);

    logger.info("Creating a config map to hold domain creation scripts");
    String domainScriptConfigMapName = "create-domain-scripts-cm";
    assertDoesNotThrow(
        () -> createConfigMapForDomainCreation(domainScriptConfigMapName, domainScriptFiles,
           namespace, this.getClass().getSimpleName()),
        "Create configmap for domain creation failed");

    // create a V1Container with specific scripts and properties for creating domain
    V1Container jobCreationContainer = new V1Container()
        .addCommandItem("/bin/sh")
        .addArgsItem("/u01/oracle/oracle_common/common/bin/wlst.sh")
        .addArgsItem("/u01/weblogic/" + wlstScriptFile.getFileName()) //wlst.sh script
        .addArgsItem("-skipWLSModuleScanning")
        .addArgsItem("-loadProperties")
        .addArgsItem("/u01/weblogic/" + domainPropertiesFile.getFileName()); //domain property file

    logger.info("Running a Kubernetes job to create the domain");
    Map<String, String> annotMap = new HashMap<String, String>();
    annotMap.put("sidecar.istio.io/inject", "false");
    createDomainJob(WEBLOGIC_IMAGE_TO_USE_IN_SPEC, pvName, pvcName, domainScriptConfigMapName,
        namespace, jobCreationContainer, annotMap);

  }

}
