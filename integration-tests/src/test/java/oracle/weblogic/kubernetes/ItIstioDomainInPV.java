// Copyright (c) 2020, 2024, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.weblogic.kubernetes;

import java.io.File;
import java.io.FileOutputStream;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import io.kubernetes.client.openapi.ApiException;
import io.kubernetes.client.openapi.models.V1Container;
import io.kubernetes.client.openapi.models.V1EnvVar;
import io.kubernetes.client.openapi.models.V1LocalObjectReference;
import io.kubernetes.client.openapi.models.V1ObjectMeta;
import io.kubernetes.client.openapi.models.V1PersistentVolumeClaimVolumeSource;
import io.kubernetes.client.openapi.models.V1Volume;
import io.kubernetes.client.openapi.models.V1VolumeMount;
import oracle.weblogic.domain.AdminService;
import oracle.weblogic.domain.Channel;
import oracle.weblogic.domain.Configuration;
import oracle.weblogic.domain.DomainResource;
import oracle.weblogic.domain.DomainSpec;
import oracle.weblogic.domain.ServerPod;
import oracle.weblogic.kubernetes.actions.impl.primitive.Kubernetes;
import oracle.weblogic.kubernetes.annotations.IntegrationTest;
import oracle.weblogic.kubernetes.annotations.Namespaces;
import oracle.weblogic.kubernetes.logging.LoggingFacade;
import oracle.weblogic.kubernetes.utils.ExecResult;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static oracle.weblogic.kubernetes.TestConstants.ADMIN_PASSWORD_DEFAULT;
import static oracle.weblogic.kubernetes.TestConstants.ADMIN_USERNAME_DEFAULT;
import static oracle.weblogic.kubernetes.TestConstants.BASE_IMAGES_REPO_SECRET_NAME;
import static oracle.weblogic.kubernetes.TestConstants.DOMAIN_API_VERSION;
import static oracle.weblogic.kubernetes.TestConstants.IMAGE_PULL_POLICY;
import static oracle.weblogic.kubernetes.TestConstants.ISTIO_HTTP_HOSTPORT;
import static oracle.weblogic.kubernetes.TestConstants.K8S_NODEPORT_HOST;
import static oracle.weblogic.kubernetes.TestConstants.KIND_REPO;
import static oracle.weblogic.kubernetes.TestConstants.LOCALE_IMAGE_NAME;
import static oracle.weblogic.kubernetes.TestConstants.LOCALE_IMAGE_TAG;
import static oracle.weblogic.kubernetes.TestConstants.OKE_CLUSTER;
import static oracle.weblogic.kubernetes.TestConstants.RESULTS_TEMPFILE;
import static oracle.weblogic.kubernetes.TestConstants.SKIP_CLEANUP;
import static oracle.weblogic.kubernetes.TestConstants.WEBLOGIC_IMAGE_TAG;
import static oracle.weblogic.kubernetes.TestConstants.WEBLOGIC_IMAGE_TO_USE_IN_SPEC;
import static oracle.weblogic.kubernetes.actions.ActionConstants.RESOURCE_DIR;
import static oracle.weblogic.kubernetes.actions.TestActions.addLabelsToNamespace;
import static oracle.weblogic.kubernetes.actions.TestActions.deletePersistentVolume;
import static oracle.weblogic.kubernetes.actions.TestActions.deletePersistentVolumeClaim;
import static oracle.weblogic.kubernetes.actions.TestActions.scaleAllClustersInDomain;
import static oracle.weblogic.kubernetes.assertions.TestAssertions.pvExists;
import static oracle.weblogic.kubernetes.assertions.TestAssertions.pvcExists;
import static oracle.weblogic.kubernetes.utils.ApplicationUtils.checkAppUsingHostHeader;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.checkPodReadyAndServiceExists;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.checkServiceExists;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.createTestWebAppWarFile;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.formatIPv6Host;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.getNextFreePort;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.getServiceExtIPAddrtOke;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.getUniqueName;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.isAppInServerPodReady;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.isWebLogicPsuPatchApplied;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.startPortForwardProcess;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.stopPortForwardProcess;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.testUntil;
import static oracle.weblogic.kubernetes.utils.ConfigMapUtils.createConfigMapForDomainCreation;
import static oracle.weblogic.kubernetes.utils.DeployUtil.deployToClusterUsingRest;
import static oracle.weblogic.kubernetes.utils.DeployUtil.deployUsingRest;
import static oracle.weblogic.kubernetes.utils.DomainUtils.createDomainAndVerify;
import static oracle.weblogic.kubernetes.utils.ExecCommand.exec;
import static oracle.weblogic.kubernetes.utils.FileUtils.generateFileFromTemplate;
import static oracle.weblogic.kubernetes.utils.ImageUtils.createBaseRepoSecret;
import static oracle.weblogic.kubernetes.utils.IstioUtils.createAdminServer;
import static oracle.weblogic.kubernetes.utils.IstioUtils.deployHttpIstioGatewayAndVirtualservice;
import static oracle.weblogic.kubernetes.utils.IstioUtils.getIstioHttpIngressPort;
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
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests to create domain in persistent volume using WLST.
 */
@DisplayName("Verify istio enabled WebLogic domain in domainhome-on-pv model")
@IntegrationTest
@Tag("kind-parallel")
@Tag("olcne-mrg")
@Tag("oke-arm")
@Tag("oke-parallel")
class ItIstioDomainInPV  {

  private static String opNamespace = null;
  private static String domainNamespace = null;

  private final String wlSecretName = "weblogic-credentials";
  private final String domainUid = "istio-dpv";
  private final String clusterName = "cluster-1";
  private final String adminServerName = "admin-server";
  private final String adminServerPodName = domainUid + "-" + adminServerName;
  private static LoggingFacade logger = null;
  private final String pvName = getUniqueName(domainUid + "-pv-");
  private final String pvcName = getUniqueName(domainUid + "-pvc-");

  private static String testWebAppWarLoc = null;

  private static final String istioNamespace = "istio-system";
  private static final String istioIngressServiceName = "istio-ingressgateway";

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
    Map<String, String> labelMap = new HashMap<>();
    labelMap.put("istio-injection", "enabled");

    assertDoesNotThrow(() -> addLabelsToNamespace(domainNamespace,labelMap));
    assertDoesNotThrow(() -> addLabelsToNamespace(opNamespace,labelMap));

    // create testwebapp.war
    testWebAppWarLoc = createTestWebAppWarFile(domainNamespace);

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
   * Verify ready app is accessible thru istio ingress http port
   * Verify ready app is accessible thru kubectl forwarded port
   * Additionally, the test verifies that WebLogic cluster can be scaled down
   * and scaled up in the absence of Administration server.
   */
  @Test
  @DisplayName("Create WebLogic domain in PV with Istio")
  void testIstioDomainHomeInPv() throws UnknownHostException {

    final String managedServerNameBase = "managed-";
    String managedServerPodNamePrefix = domainUid + "-" + managedServerNameBase;
    final int replicaCount = 2;
    final int t3ChannelPort = getNextFreePort();
  
    // In internal OKE env, use Istio EXTERNAL-IP; in non-internal-OKE env, use K8S_NODEPORT_HOST
    String hostName = getServiceExtIPAddrtOke(istioIngressServiceName, istioNamespace) != null
        ? getServiceExtIPAddrtOke(istioIngressServiceName, istioNamespace) : K8S_NODEPORT_HOST;

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
    File domainPropertiesFile =
        assertDoesNotThrow(() -> File.createTempFile("domain", ".properties", new File(RESULTS_TEMPFILE)),
        "Failed to create domain properties file");
    Properties p = new Properties();
    p.setProperty("domain_path", "/shared/" + domainNamespace + "/domains");
    p.setProperty("domain_name", domainUid);
    p.setProperty("domain_uid", domainUid);
    p.setProperty("cluster_name", clusterName);
    p.setProperty("admin_server_name", adminServerName);
    p.setProperty("managed_server_port", "8001");
    p.setProperty("admin_server_port", "7001");
    p.setProperty("admin_username", ADMIN_USERNAME_DEFAULT);
    p.setProperty("admin_password", ADMIN_PASSWORD_DEFAULT);
    p.setProperty("admin_t3_public_address", hostName);
    p.setProperty("admin_t3_channel_port", Integer.toString(t3ChannelPort));
    p.setProperty("number_of_ms", "4");
    p.setProperty("managed_server_name_base", managedServerNameBase);
    p.setProperty("domain_logs", "/shared/" + domainNamespace + "/logs/" + domainUid);
    p.setProperty("production_mode_enabled", "true");
    assertDoesNotThrow(() ->
            p.store(new FileOutputStream(domainPropertiesFile), "wlst properties file"),
        "Failed to write domain properties file");

    // WLST script for creating domain
    Path wlstScript = Paths.get(RESOURCE_DIR, "python-scripts", "wlst-create-istio-domain-onpv.py");

    // create configmap and domain on persistent volume using the WLST script and property file
    createDomainOnPVUsingWlst(wlstScript, domainPropertiesFile.toPath(), pvName, pvcName, domainNamespace);

    // Use the WebLogic(12.2.1.4) Base Image with Japanese Locale
    // Add the LANG environment variable to ja_JP.utf8
    // Currently LOCALE testing is disabled till we have 1412 image with 
    // Japanease Locale 
    String imageLocation;
    if (KIND_REPO != null) {
      imageLocation = KIND_REPO + "test-images/weblogic:" + LOCALE_IMAGE_TAG;
    } else {
      imageLocation = LOCALE_IMAGE_NAME + ":" + LOCALE_IMAGE_TAG;
    }
    // remove the below line when 141200 lacale image is available 
    // and modify serverPod env value("en_US.UTF-8")) to ja_JP.utf8
    // uncomment assertTrue(matchPodLog(),"LANG is not set to ja_JP.utf8");
    imageLocation = WEBLOGIC_IMAGE_TO_USE_IN_SPEC;

    // Enable istio in domain custom resource configuration object.
    // Add T3Channel Service with port assigned to Istio TCP ingress port.
    logger.info("Creating domain custom resource");
    DomainResource domain = new DomainResource()
        .apiVersion(DOMAIN_API_VERSION)
        .kind("Domain")
        .metadata(new V1ObjectMeta()
            .name(domainUid)
            .namespace(domainNamespace))
        .spec(new DomainSpec()
            .domainUid(domainUid)
            .domainHome("/shared/" + domainNamespace + "/domains/" + domainUid)
            .domainHomeSourceType("PersistentVolume")
            .image(imageLocation)
            .imagePullPolicy(IMAGE_PULL_POLICY)
            .replicas(replicaCount)
            .imagePullSecrets(Arrays.asList(
                new V1LocalObjectReference()
                    .name(BASE_IMAGES_REPO_SECRET_NAME)))     // this secret is used only on non-kind cluster
            .webLogicCredentialsSecret(new V1LocalObjectReference()
                .name(wlSecretName))
            .includeServerOutInPodLog(true)
            .logHomeEnabled(Boolean.TRUE)
            .logHome("/shared/" + domainNamespace + "logs/" + domainUid)
            .dataHome("")
            .serverStartPolicy("IfNeeded")
            .serverPod(new ServerPod() //serverpod
                .addEnvItem(new V1EnvVar()
                    .name("LANG")
                    .value("en_US.UTF-8")) // ja_JP.utf8
                .addEnvItem(new V1EnvVar()
                    .name("JAVA_OPTIONS")
                    .value("-Dweblogic.StdoutDebugEnabled=false "
                        + "-Dweblogic.security.remoteAnonymousRMIT3Enabled=true "))
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
            .configuration(new Configuration()
                ));
    setPodAntiAffinity(domain);
    // verify the domain custom resource is created
    createDomainAndVerify(domain, domainNamespace);

    // verify the admin server service created
    checkPodReadyAndServiceExists(adminServerPodName, domainUid, domainNamespace);
    // verify managed server services created
    // verify managed server pods are ready
    for (int i = 1; i <= replicaCount; i++) {
      logger.info("Checking managed service {0} is created in namespace {1}",
          managedServerPodNamePrefix + i, domainNamespace);
      checkPodReadyAndServiceExists(managedServerPodNamePrefix + i, domainUid, domainNamespace);
    }
    // Make sure Japanese character is found in server pod log
    // assertTrue(matchPodLog(),"LANG is not set to ja_JP.utf8");

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

    // In internal OKE env, use Istio EXTERNAL-IP;
    // in non-internal-OKE env, use K8S_NODEPORT_HOST + ":" + istioIngressPort
    
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
    String hostAndPort = hostName.contains(K8S_NODEPORT_HOST) ? host + ":" + istioIngressPort : hostName;
    if (TestConstants.KIND_CLUSTER
        && !TestConstants.WLSIMG_BUILDER.equals(TestConstants.WLSIMG_BUILDER_DEFAULT)) {
      istioIngressPort = ISTIO_HTTP_HOSTPORT;
      hostAndPort = host + ":" + istioIngressPort;
    }

    String readyAppUrl = "http://" + hostAndPort + "/weblogic/ready";
    boolean checkConsole = checkAppUsingHostHeader(readyAppUrl, domainNamespace + ".org");
    assertTrue(checkConsole, "Failed to access WebLogic readyapp ");
    logger.info("WebLogic server is accessible");
    if (!(TestConstants.KIND_CLUSTER
        && !TestConstants.WLSIMG_BUILDER.equals(TestConstants.WLSIMG_BUILDER_DEFAULT))) {
      String localhost = "localhost";
      String forwardPort = startPortForwardProcess(localhost, domainNamespace, domainUid, 7001);
      assertNotNull(forwardPort, "port-forward fails to assign local port");
      logger.info("Forwarded local port is {0}", forwardPort);
      readyAppUrl = "http://" + localhost + ":" + forwardPort + "/weblogic/ready";
      checkConsole = checkAppUsingHostHeader(readyAppUrl, domainNamespace + ".org");
      assertTrue(checkConsole, "Failed to access WebLogic server thru port-forwarded port");
      logger.info("WebLogic readyapp is accessible thru port forwarding");
      stopPortForwardProcess(domainNamespace);
    }

    ExecResult result = null;
    if (isWebLogicPsuPatchApplied()) {
      String curlCmd2 = "curl -g -j -sk --show-error --noproxy '*' "
          + " -H 'Host: " + domainNamespace + ".org'"
          + " --user " + ADMIN_USERNAME_DEFAULT + ":" + ADMIN_PASSWORD_DEFAULT
          + " --url http://" + host + ":" + istioIngressPort
          + "/management/weblogic/latest/domainRuntime/domainSecurityRuntime?"
          + "link=none";

      logger.info("curl command {0}", curlCmd2);
      result = assertDoesNotThrow(() -> exec(curlCmd2, true));

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

    Path archivePath = Paths.get(testWebAppWarLoc);
    if (OKE_CLUSTER) {
      String managedServerPrefix = domainUid + "-managed-";
      String target = "{identity: [clusters,'" + clusterName + "']}";

      result = deployUsingRest(hostAndPort, ADMIN_USERNAME_DEFAULT, ADMIN_PASSWORD_DEFAULT,
          target, archivePath, domainNamespace + ".org", "testwebapp");

      assertNotNull(result, "Application deployment failed");
      logger.info("Application deployment returned {0}", result.toString());
      assertEquals("202", result.stdout(), "Application deployment failed with wrong HTTP code");
      logger.info("Application {0} deployed successfully at {1}", "testwebapp.war", domainUid + "-" + clusterName);

      testUntil(isAppInServerPodReady(domainNamespace,
          managedServerPrefix + 1, 8001, "/testwebapp/index.jsp", "testwebapp"),
          logger, "Check Deployed App {0} in server {1}",
          archivePath,
          target);
    } else {
      for (int i = 1; i <= 10; i++) {
        result = deployToClusterUsingRest(host, String.valueOf(istioIngressPort),
            ADMIN_USERNAME_DEFAULT, ADMIN_PASSWORD_DEFAULT,
            clusterName, archivePath, domainNamespace + ".org", "testwebapp");
        assertNotNull(result, "Application deployment failed");
        logger.info("(Loop:{0}) Application deployment returned {1}", i, result.toString());

        if (result.stdout().equals("202")) {
          break;
        }
      }
      assertEquals("202", result.stdout(), "Application deployment failed with wrong HTTP code");
      logger.info("Application {0} deployed successfully at {1}", "testwebapp.war", domainUid + "-" + clusterName);

      String url = "http://" + host + ":" + istioIngressPort + "/testwebapp/index.jsp";
      logger.info("Application Access URL {0}", url);
      boolean checkApp = checkAppUsingHostHeader(url, domainNamespace + ".org");
      assertTrue(checkApp, "Failed to access WebLogic application");
    }
    logger.info("Application /testwebapp/index.jsp is accessble to {0}", domainUid);

    // Refer JIRA OWLS-86407
    // Stop and Start the managed server in absense of administration server
    assertTrue(patchServerStartPolicy(domainUid, domainNamespace,
         "/spec/adminServer/serverStartPolicy", "Never"),
         "Failed to patch administration server serverStartPolicy to Never");
    logger.info("Domain is patched to shutdown administration server");
    checkPodDeleted(adminServerPodName, domainUid, domainNamespace);
    logger.info("Administration server shutdown success");

    boolean scalingSuccess = scaleAllClustersInDomain(domainUid, domainNamespace, 1);
    assertTrue(scalingSuccess,
        String.format("Cluster scaling failed for domain %s in namespace %s", domainUid, domainNamespace));
    logger.info("Cluster is scaled down in absence of administration server");
    checkPodDeleted(managedServerPodNamePrefix + "2", domainUid, domainNamespace);
    logger.info("Managed Server stopped in absence of administration server");

    scalingSuccess = scaleAllClustersInDomain(domainUid, domainNamespace, 2);
    assertTrue(scalingSuccess,
        String.format("Cluster scaling failed for domain %s in namespace %s", domainUid, domainNamespace));
    logger.info("Cluster is scaled up in absence of administration server");
    checkServiceExists(managedServerPodNamePrefix + "2", domainNamespace);
    checkPodReady(managedServerPodNamePrefix + "2", domainUid, domainNamespace);
    logger.info("Managed Server started in absence of administration server");
  }

  /**
   * Delete PV and PVC.
   * @throws ApiException if Kubernetes API call fails
   */
  @AfterAll
  public void tearDownAll() throws ApiException {
    if (!SKIP_CLEANUP) {
      if (assertDoesNotThrow(() -> pvcExists(pvcName, domainNamespace).call())) {
        // delete pvc
        deletePersistentVolumeClaim(pvcName, domainNamespace);
      }
      if (assertDoesNotThrow(() -> pvExists(pvName, null).call())) {
        // delete pv
        deletePersistentVolume(pvName);
      }
    }
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
    assertDoesNotThrow(() -> createConfigMapForDomainCreation(domainScriptConfigMapName, domainScriptFiles,
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
