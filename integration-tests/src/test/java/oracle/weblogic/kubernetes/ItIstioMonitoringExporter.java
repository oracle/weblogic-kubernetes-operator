// Copyright (c) 2021, 2024, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.weblogic.kubernetes;


import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import oracle.weblogic.domain.DomainResource;
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
import static oracle.weblogic.kubernetes.TestConstants.ISTIO_HTTP_HOSTPORT;
import static oracle.weblogic.kubernetes.TestConstants.IT_ISTIOMONITORINGEXPORTER_PROMETHEUS_HTTP_HOSTPORT;
import static oracle.weblogic.kubernetes.TestConstants.IT_ISTIOMONITORINGEXPORTER_PROMETHEUS_HTTP_NODEPORT;
import static oracle.weblogic.kubernetes.TestConstants.K8S_NODEPORT_HOST;
import static oracle.weblogic.kubernetes.TestConstants.OCNE;
import static oracle.weblogic.kubernetes.TestConstants.OKE_CLUSTER;
import static oracle.weblogic.kubernetes.TestConstants.OKE_CLUSTER_PRIVATEIP;
import static oracle.weblogic.kubernetes.TestConstants.RESULTS_ROOT;
import static oracle.weblogic.kubernetes.TestConstants.TEST_IMAGES_REPO_SECRET_NAME;
import static oracle.weblogic.kubernetes.actions.ActionConstants.MODEL_DIR;
import static oracle.weblogic.kubernetes.actions.ActionConstants.RESOURCE_DIR;
import static oracle.weblogic.kubernetes.actions.TestActions.addLabelsToNamespace;
import static oracle.weblogic.kubernetes.actions.TestActions.deleteImage;
import static oracle.weblogic.kubernetes.utils.ApplicationUtils.checkAppUsingHostHeader;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.checkServiceExists;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.createTestWebAppWarFile;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.formatIPv6Host;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.getImageBuilderExtraArgs;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.getServiceExtIPAddrtOke;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.startPortForwardProcess;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.stopPortForwardProcess;
import static oracle.weblogic.kubernetes.utils.ConfigMapUtils.createConfigMapAndVerify;
import static oracle.weblogic.kubernetes.utils.DeployUtil.deployToClusterUsingRest;
import static oracle.weblogic.kubernetes.utils.DeployUtil.deployUsingRest;
import static oracle.weblogic.kubernetes.utils.DomainUtils.createDomainAndVerify;
import static oracle.weblogic.kubernetes.utils.FileUtils.generateFileFromTemplate;
import static oracle.weblogic.kubernetes.utils.ImageUtils.createMiiImageAndVerify;
import static oracle.weblogic.kubernetes.utils.ImageUtils.createTestRepoSecret;
import static oracle.weblogic.kubernetes.utils.ImageUtils.imageRepoLoginAndPushImageToRegistry;
import static oracle.weblogic.kubernetes.utils.IstioUtils.createIstioDomainResource;
import static oracle.weblogic.kubernetes.utils.IstioUtils.deployHttpIstioGatewayAndVirtualservice;
import static oracle.weblogic.kubernetes.utils.IstioUtils.deployIstioDestinationRule;
import static oracle.weblogic.kubernetes.utils.IstioUtils.deployIstioPrometheus;
import static oracle.weblogic.kubernetes.utils.IstioUtils.getIstioHttpIngressPort;
import static oracle.weblogic.kubernetes.utils.MonitoringUtils.buildMonitoringExporterCreateImageAndPushToRepo;
import static oracle.weblogic.kubernetes.utils.MonitoringUtils.checkMetricsViaPrometheus;
import static oracle.weblogic.kubernetes.utils.MonitoringUtils.cloneMonitoringExporter;
import static oracle.weblogic.kubernetes.utils.MonitoringUtils.downloadMonitoringExporterApp;
import static oracle.weblogic.kubernetes.utils.MonitoringUtils.editPrometheusCM;
import static oracle.weblogic.kubernetes.utils.OperatorUtils.installAndVerifyOperator;
import static oracle.weblogic.kubernetes.utils.PodUtils.checkPodReady;
import static oracle.weblogic.kubernetes.utils.PodUtils.getPodName;
import static oracle.weblogic.kubernetes.utils.SecretUtils.createSecretWithUsernamePassword;
import static oracle.weblogic.kubernetes.utils.ThreadSafeLogger.getLogger;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("Test the monitoring WebLogic Domain via istio provided Prometheus")
@IntegrationTest
@Tag("oke-parallel")
@Tag("kind-parallel")
class ItIstioMonitoringExporter {

  private static String opNamespace = null;
  private static String domain1Namespace = null;
  private static String domain2Namespace = null;
  private static String nginxNamespace = null;

  private String domain1Uid = "istio1-mii";
  private String domain2Uid = "istio2-mii";
  private String configMapName = "dynamicupdate-istio-configmap";
  private final String clusterName = "cluster-1"; // do not modify
  private final String workManagerName = "newWM";
  private final int replicaCount = 2;
  private static int prometheusPort;
  private static final String istioNamespace = "istio-system";
  private static final String istioIngressServiceName = "istio-ingressgateway";

  private boolean isPrometheusDeployed = false;
  private boolean isPrometheusPortForward = false;
  private static LoggingFacade logger = null;
  private static String oldRegex;
  private static String miiImageSideCar = null;
  private static String miiImageWebApp = null;
  private static String exporterImage = null;
  private static String sessionAppPrometheusSearchKey =
      "wls_servlet_invocation_total_count%7Bapp%3D%22myear%22%7D%5B15s%5D";

  private static String testWebAppWarLoc = null;
  private static String ingressIP = null;
  private static String hostPortPrometheus = null;

  /**
   * Install Operator.
   * @param namespaces list of namespaces created by the IntegrationTestWatcher
   */
  @BeforeAll
  public static void initAll(@Namespaces(3) List<String> namespaces) {
    logger = getLogger();

    // get a new unique opNamespace
    logger.info("Assign unique namespace for Operator");
    assertNotNull(namespaces.get(0), "Namespace list is null");
    opNamespace = namespaces.get(0);

    logger.info("Assign unique namespace for Domain1");
    assertNotNull(namespaces.get(1), "Namespace list is null");
    domain1Namespace = namespaces.get(1);

    logger.info("Assign unique namespace for Domain2");
    assertNotNull(namespaces.get(2), "Namespace list is null");
    domain2Namespace = namespaces.get(2);


    // Label the domain/operator namespace with istio-injection=enabled
    Map<String, String> labelMap = new HashMap<>();
    labelMap.put("istio-injection", "enabled");
    assertDoesNotThrow(() -> addLabelsToNamespace(domain1Namespace,labelMap));
    assertDoesNotThrow(() -> addLabelsToNamespace(domain2Namespace,labelMap));
    assertDoesNotThrow(() -> addLabelsToNamespace(opNamespace,labelMap));

    // create testwebapp.war
    testWebAppWarLoc = createTestWebAppWarFile(domain1Namespace);

    // install and verify operator
    installAndVerifyOperator(opNamespace, domain1Namespace, domain2Namespace);
    prometheusPort = IT_ISTIOMONITORINGEXPORTER_PROMETHEUS_HTTP_NODEPORT;
  }

  /**
   * Create a domain using model-in-image model and monitoring exporter webapp.
   * Add istio configuration with default readinessPort.
   * Do not add any AdminService under AdminServer configuration.
   * Deploy istio gateways and virtual service.
   * Verify server pods are in ready state and services are created.
   * Verify login to WebLogic console is successful thru istio ingress port.
   * Deploy a web application thru istio http ingress port using REST api.
   * Deploy Istio provided Promethues
   * Verify Weblogic metrics can be processed via istio based prometheus
   */
  @Test
  @DisplayName("Create istio provided prometheus and verify "
      + "it can monitor Weblogic domain via weblogic exporter webapp")
  void testIstioPrometheusViaExporterWebApp() {
    assertDoesNotThrow(() -> downloadMonitoringExporterApp(RESOURCE_DIR
        + "/exporter/exporter-config.yaml", RESULTS_ROOT), "Failed to download monitoring exporter application");
    miiImageWebApp = createAndVerifyMiiImageWithMonitoringExporter(RESULTS_ROOT + "/wls-exporter.war",
        MODEL_DIR + "/model.monexp.yaml");
    String managedServerPrefix = domain1Uid + "-cluster-1-managed-server";
    assertDoesNotThrow(() -> setupIstioModelInImageDomain(miiImageWebApp,
        domain1Namespace,domain1Uid, managedServerPrefix), "setup for istio based domain failed");
    assertDoesNotThrow(() -> deployPrometheusAndVerify(domain1Namespace, domain1Uid, sessionAppPrometheusSearchKey),
        "failed to fetch expected metrics from Prometheus using monitoring exporter webapp");
  }

  /**
   * Create a domain using model-in-image model with monitoring exporter sidecar.
   * Add istio configuration with default readinessPort.
   * Do not add any AdminService under AdminServer configuration.
   * Deploy istio gateways and virtual service.
   * Verify server pods are in ready state and services are created.
   * Verify login to WebLogic console is successful thru istio ingress port.
   * Deploy Istio provided Prometheus
   * Verify Weblogic metrics can be processed via istio based prometheus
   */
  @Test
  @DisplayName("Create istio provided prometheus and verify "
      + "it can monitor Weblogic domain via weblogic exporter sidecar")
  void testIstioPrometheusWithSideCar() {
    // create image with model files
    logger.info("Create image with model file and verify");

    List<String> appList = new ArrayList<>();
    appList.add("sessmigr-app");

    // build the model file list
    final List<String> modelList = Collections.singletonList(MODEL_DIR + "/model.sessmigr.yaml");
    miiImageSideCar =
        createMiiImageAndVerify("miimonexp-istio-image", modelList, appList);

    // repo login and push image to registry if necessary
    imageRepoLoginAndPushImageToRegistry(miiImageSideCar);

    String monitoringExporterSrcDir = Paths.get(RESULTS_ROOT, "monitoringexp", "srcdir").toString();
    cloneMonitoringExporter(monitoringExporterSrcDir);
    exporterImage = assertDoesNotThrow(() ->
            buildMonitoringExporterCreateImageAndPushToRepo(monitoringExporterSrcDir, "exporter",
        domain2Namespace, TEST_IMAGES_REPO_SECRET_NAME, getImageBuilderExtraArgs()),
        "Failed to create image for exporter");
    String exporterConfig = RESOURCE_DIR + "/exporter/exporter-config.yaml";
    String managedServerPrefix = domain2Uid + "-managed-server";
    assertDoesNotThrow(() -> setupIstioModelInImageDomain(miiImageSideCar, domain2Namespace, domain2Uid, exporterConfig,
        exporterImage, managedServerPrefix), "setup for istio based domain failed");
    assertDoesNotThrow(() -> deployPrometheusAndVerify(domain2Namespace, domain2Uid, sessionAppPrometheusSearchKey),
        "failed to fetch expected metrics from Prometheus using monitoring exporter sidecar");
  }

  private void deployPrometheusAndVerify(String domainNamespace, String domainUid, String searchKey) throws Exception {
    if (!isPrometheusDeployed) {
      assertTrue(deployIstioPrometheus(domain2Namespace, domain2Uid,
          String.valueOf(prometheusPort)), "failed to install istio prometheus");
      isPrometheusDeployed = true;
      oldRegex = String.format("regex: %s;%s", domainNamespace, domainUid);
      //verify metrics via prometheus
      String host = formatIPv6Host(K8S_NODEPORT_HOST);

      // In internal OKE env, use Istio EXTERNAL-IP; in non-OKE env, use K8S_NODEPORT_HOST + ":" + istioIngressPort
      hostPortPrometheus = getServiceExtIPAddrtOke(istioIngressServiceName, istioNamespace) != null
          ? getServiceExtIPAddrtOke(istioIngressServiceName, istioNamespace) : host + ":" + prometheusPort;
      if (!TestConstants.WLSIMG_BUILDER.equals(TestConstants.WLSIMG_BUILDER_DEFAULT) && !OCNE) {
        try {
          hostPortPrometheus = InetAddress.getLocalHost().getHostAddress()
              + ":" + IT_ISTIOMONITORINGEXPORTER_PROMETHEUS_HTTP_HOSTPORT;
        } catch (UnknownHostException ex) {
          logger.severe(ex.getLocalizedMessage());
        }
      }      

      if (OKE_CLUSTER_PRIVATEIP) {
        String localhost = "localhost";
        // Forward the non-ssl port 9090
        String podName = getPodName(istioNamespace, "prometheus-");
        String forwardPort = startPortForwardProcess(localhost, istioNamespace, 9090, podName);
        assertNotNull(forwardPort, "port-forward fails to assign local port");
        logger.info("Forwarded local port is {0}", forwardPort);
        hostPortPrometheus = localhost + ":" + forwardPort;
        isPrometheusPortForward = true;
      }
    } else {
      String newRegex = String.format("regex: %s;%s", domainNamespace, domainUid);
      assertDoesNotThrow(() -> editPrometheusCM(oldRegex, newRegex, "istio-system", "prometheus"),
          "Can't modify Prometheus CM, not possible to monitor " + domainUid);
    }

    checkMetricsViaPrometheus(searchKey, "sessmigr", hostPortPrometheus);
  }

  @AfterAll
  public void tearDownAll() {

    // delete mii domain images created for parameterized test
    if (miiImageWebApp != null) {
      deleteImage(miiImageWebApp);
    }
    if (miiImageSideCar != null) {
      deleteImage(miiImageSideCar);
    }
    if (exporterImage != null) {
      deleteImage(exporterImage);
    }
    if (OKE_CLUSTER_PRIVATEIP) {
      stopPortForwardProcess(istioNamespace);
    }
  }

  /**
   * Create mii image with monitoring exporter webapp.
   */
  private static String createAndVerifyMiiImageWithMonitoringExporter(String monexpAppDir, String modelFilePath) {
    // create image with model files
    logger.info("Create image with model file with monitoring exporter app and verify");

    List<String> appList = new ArrayList<>();
    appList.add(monexpAppDir);
    appList.add("sessmigr-app");

    // build the model file list
    final List<String> modelList = Collections.singletonList(modelFilePath);
    String myImage =
        createMiiImageAndVerify("monexp", modelList, appList);

    // repo login and push image to registry if necessary
    imageRepoLoginAndPushImageToRegistry(myImage);

    return myImage;
  }

  private void setupIstioModelInImageDomain(String miiImage, String domainNamespace,
                                            String domainUid, String managedServerPrefix) {
    setupIstioModelInImageDomain(miiImage, domainNamespace,
        domainUid, null, null,managedServerPrefix);
  }

  private void setupIstioModelInImageDomain(String miiImage, String domainNamespace, String domainUid,
                                            String exporterConfig, String exporterImage,
                                            String managedServerPrefix) {

    // Create the repo secret to pull the image
    // this secret is used only for non-kind cluster
    createTestRepoSecret(domainNamespace);

    // create secret for admin credentials
    logger.info("Create secret for admin credentials");
    String adminSecretName = "weblogic-credentials";
    assertDoesNotThrow(() -> createSecretWithUsernamePassword(
        adminSecretName,
        domainNamespace,
        ADMIN_USERNAME_DEFAULT,
        ADMIN_PASSWORD_DEFAULT),
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
    createConfigMapAndVerify(configMapName, domainUid, domainNamespace, Collections.emptyList());

    // create the domain object
    DomainResource domain = createIstioDomainResource(domainUid,
        domainNamespace,
        adminSecretName,
        TEST_IMAGES_REPO_SECRET_NAME,
        encryptionSecretName,
        replicaCount,
        miiImage,
        configMapName,
        clusterName,
        exporterConfig,
        exporterImage);

    // create model in image domain
    createDomainAndVerify(domain, domainNamespace);
    String adminServerPodName = domainUid + "-admin-server";
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

    Map<String, String> templateMap  = new HashMap<>();
    templateMap.put("NAMESPACE", domainNamespace);
    templateMap.put("DUID", domainUid);
    templateMap.put("ADMIN_SERVICE",adminServerPodName);
    templateMap.put("CLUSTER_SERVICE", clusterService);
    templateMap.put("MANAGED_SERVER_PORT", "7001");

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
    String host = formatIPv6Host(K8S_NODEPORT_HOST);

    // In internal OKE env, use Istio EXTERNAL-IP; in non-OKE env, use K8S_NODEPORT_HOST + ":" + istioIngressPort
    String hostAndPort = getServiceExtIPAddrtOke(istioIngressServiceName, istioNamespace) != null
        ? getServiceExtIPAddrtOke(istioIngressServiceName, istioNamespace) : host + ":" + istioIngressPort;
    String deployHost = K8S_NODEPORT_HOST;
    
    if (!TestConstants.WLSIMG_BUILDER.equals(TestConstants.WLSIMG_BUILDER_DEFAULT) && !OCNE) {
      istioIngressPort = ISTIO_HTTP_HOSTPORT;
      try {
        hostAndPort = InetAddress.getLocalHost().getHostAddress() + ":" + istioIngressPort;
        deployHost = InetAddress.getLocalHost().getHostAddress();
      } catch (UnknownHostException ex) {
        logger.severe(ex.getLocalizedMessage());
      }
    }
    String readyAppUrl = "http://" + hostAndPort + "/weblogic/ready";
    boolean checlReadyApp =
        checkAppUsingHostHeader(readyAppUrl, domainNamespace + ".org");
    assertTrue(checlReadyApp, "Failed to access ready app");
    logger.info("ready app is accessible");

    Path archivePath = Paths.get(testWebAppWarLoc);
    String target = "{identity: [clusters,'" + clusterName + "']}";
    ExecResult result = OKE_CLUSTER
        ? deployUsingRest(hostAndPort, ADMIN_USERNAME_DEFAULT, ADMIN_PASSWORD_DEFAULT,
        target, archivePath, domainNamespace + ".org", "testwebapp")
        : deployToClusterUsingRest(deployHost,
        String.valueOf(istioIngressPort),
        ADMIN_USERNAME_DEFAULT, ADMIN_PASSWORD_DEFAULT,
        clusterName, archivePath, domainNamespace + ".org", "testwebapp");

    assertNotNull(result, "Application deployment failed");
    logger.info("Application deployment returned {0}", result.toString());
    assertEquals("202", result.stdout(), "Deployment didn't return HTTP status code 202");

    String url = "http://" + hostAndPort + "/testwebapp/index.jsp";
    logger.info("Application Access URL {0}", url);
  }

}
