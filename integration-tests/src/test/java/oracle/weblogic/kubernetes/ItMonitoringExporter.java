// Copyright (c) 2020, 2021, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.weblogic.kubernetes;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;

import com.gargoylesoftware.htmlunit.FailingHttpStatusCodeException;
import com.gargoylesoftware.htmlunit.TextPage;
import com.gargoylesoftware.htmlunit.WebClient;
import com.gargoylesoftware.htmlunit.html.HtmlFileInput;
import com.gargoylesoftware.htmlunit.html.HtmlForm;
import com.gargoylesoftware.htmlunit.html.HtmlPage;
import com.gargoylesoftware.htmlunit.html.HtmlRadioButtonInput;
import com.gargoylesoftware.htmlunit.html.HtmlSubmitInput;
import com.google.gson.Gson;
import io.kubernetes.client.openapi.ApiException;
import io.kubernetes.client.openapi.models.V1Pod;
import oracle.weblogic.kubernetes.actions.impl.GrafanaParams;
import oracle.weblogic.kubernetes.actions.impl.primitive.HelmParams;
import oracle.weblogic.kubernetes.actions.impl.primitive.Kubernetes;
import oracle.weblogic.kubernetes.annotations.IntegrationTest;
import oracle.weblogic.kubernetes.annotations.Namespaces;
import oracle.weblogic.kubernetes.logging.LoggingFacade;
import oracle.weblogic.kubernetes.utils.ExecCommand;
import oracle.weblogic.kubernetes.utils.ExecResult;
import oracle.weblogic.kubernetes.utils.MonitoringUtils;
import org.apache.commons.io.FileUtils;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

import static oracle.weblogic.kubernetes.TestConstants.ADMIN_PASSWORD_DEFAULT;
import static oracle.weblogic.kubernetes.TestConstants.ADMIN_SERVER_NAME_BASE;
import static oracle.weblogic.kubernetes.TestConstants.ADMIN_USERNAME_DEFAULT;
import static oracle.weblogic.kubernetes.TestConstants.GRAFANA_CHART_VERSION;
import static oracle.weblogic.kubernetes.TestConstants.K8S_NODEPORT_HOST;
import static oracle.weblogic.kubernetes.TestConstants.MANAGED_SERVER_NAME_BASE;
import static oracle.weblogic.kubernetes.TestConstants.OCIR_SECRET_NAME;
import static oracle.weblogic.kubernetes.TestConstants.PROMETHEUS_CHART_VERSION;
import static oracle.weblogic.kubernetes.TestConstants.PV_ROOT;
import static oracle.weblogic.kubernetes.TestConstants.RESULTS_ROOT;
import static oracle.weblogic.kubernetes.actions.ActionConstants.MODEL_DIR;
import static oracle.weblogic.kubernetes.actions.ActionConstants.RESOURCE_DIR;
import static oracle.weblogic.kubernetes.actions.TestActions.deleteImage;
import static oracle.weblogic.kubernetes.actions.TestActions.deletePersistentVolume;
import static oracle.weblogic.kubernetes.actions.TestActions.deletePersistentVolumeClaim;
import static oracle.weblogic.kubernetes.actions.TestActions.getPod;
import static oracle.weblogic.kubernetes.actions.TestActions.getServiceNodePort;
import static oracle.weblogic.kubernetes.actions.TestActions.uninstallNginx;
import static oracle.weblogic.kubernetes.actions.impl.primitive.Kubernetes.copyFileToPod;
import static oracle.weblogic.kubernetes.actions.impl.primitive.Kubernetes.deleteNamespace;
import static oracle.weblogic.kubernetes.actions.impl.primitive.Kubernetes.exec;
import static oracle.weblogic.kubernetes.utils.ApplicationUtils.callWebAppAndCheckForServerNameInResponse;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.getDockerExtraArgs;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.getNextFreePort;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.testUntil;
import static oracle.weblogic.kubernetes.utils.FileUtils.replaceStringInFile;
import static oracle.weblogic.kubernetes.utils.ImageUtils.createImageAndPushToRepo;
import static oracle.weblogic.kubernetes.utils.LoadBalancerUtils.createIngressForDomainAndVerify;
import static oracle.weblogic.kubernetes.utils.LoadBalancerUtils.installAndVerifyNginx;
import static oracle.weblogic.kubernetes.utils.MonitoringUtils.checkMetricsViaPrometheus;
import static oracle.weblogic.kubernetes.utils.MonitoringUtils.cleanupPromGrafanaClusterRoles;
import static oracle.weblogic.kubernetes.utils.MonitoringUtils.createAndVerifyDomain;
import static oracle.weblogic.kubernetes.utils.MonitoringUtils.deleteMonitoringExporterTempDir;
import static oracle.weblogic.kubernetes.utils.MonitoringUtils.editPrometheusCM;
import static oracle.weblogic.kubernetes.utils.MonitoringUtils.installAndVerifyGrafana;
import static oracle.weblogic.kubernetes.utils.MonitoringUtils.installAndVerifyPrometheus;
import static oracle.weblogic.kubernetes.utils.MonitoringUtils.installMonitoringExporter;
import static oracle.weblogic.kubernetes.utils.MonitoringUtils.searchForKey;
import static oracle.weblogic.kubernetes.utils.MonitoringUtils.uninstallPrometheusGrafana;
import static oracle.weblogic.kubernetes.utils.OperatorUtils.installAndVerifyOperator;
import static oracle.weblogic.kubernetes.utils.PersistentVolumeUtils.createPvAndPvc;
import static oracle.weblogic.kubernetes.utils.PodUtils.execInPod;
import static oracle.weblogic.kubernetes.utils.ThreadSafeLogger.getLogger;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;



/**
 * Verify Prometheus, Grafana, Webhook, Coordinator are installed and running
 * Verify the monitoring exporter installed in model in image domain can generate the WebLogic metrics.
 * Verify WebLogic metrics can be accessed via NGINX ingress controller.
 * Verify WebLogic metrics can be accessed via Prometheus
 */
@DisplayName("Verify WebLogic Metric is processed as expected by MonitoringExporter via Prometheus and Grafana")
@IntegrationTest
class ItMonitoringExporter {


  // domain constants
  private static final int replicaCount = 2;
  private static int managedServersCount = 2;
  private static String domain3Namespace = null;
  private static String domain4Namespace = null;
  private static String domain5Namespace = null;
  private static String domain6Namespace = null;
  private static String domain7Namespace = null;
  private static String domain8Namespace = null;

  private static String domain3Uid = "monexp-domain-3";
  private static String domain4Uid = "monexp-domain-4";
  private static String domain5Uid = "monexp-domain-5";
  private static String domain6Uid = "monexp-domain-6";
  private static String domain7Uid = "monexp-domain-7";
  private static String domain8Uid = "monexp-domain-8";
  private static HelmParams nginxHelmParams = null;
  private static int nodeportshttp = 0;
  private static int nodeportshttps = 0;
  private static List<String> ingressHost1List = null;
  private static List<String> ingressHost2List = null;

  private static String monitoringNS = null;
  //private static String webhookNS = null;
  HelmParams promHelmParams = null;
  GrafanaParams grafanaHelmParams = null;
  private static String monitoringExporterEndToEndDir = null;
  private static String monitoringExporterSrcDir = null;
  private static String monitoringExporterAppDir = null;
  // constants for creating domain image using model in image
  private static final String MONEXP_MODEL_FILE = "model.monexp.yaml";
  private static final String MONEXP_WDT_FILE = "/demo-domains/domainBuilder/scripts/simple-topology.yaml";
  private static final String MONEXP_IMAGE_NAME = "monexp-image";
  private static final String SESSMIGR_APP_NAME = "sessmigr-app";

  private static String cluster1Name = "cluster-1";
  private static String cluster2Name = "cluster-2";
  private static String miiImage = null;
  //private static String webhookImage = null;
  private static String exporterImage = null;
  private static String  coordinatorImage = null;
  private static int managedServerPort = 8001;
  private static int nodeportserver;
  private static String exporterUrl = null;
  private static String prometheusDomainRegexValue = null;
  private static Map<String, Integer> clusterNameMsPortMap;
  private static LoggingFacade logger = null;
  private static List<String> clusterNames = new ArrayList<>();
  private static String prometheusReleaseName = "prometheustest2";
  private static String grafanaReleaseName = "grafanatest2";
  private static  String monitoringExporterDir;


  /**
   * Install operator and NGINX. Create model in image domain with multiple clusters.
   * Create ingress for the domain.
   *
   * @param namespaces list of namespaces created by the IntegrationTestWatcher by the
   *                   JUnit engine parameter resolution mechanism
   */
  @BeforeAll

  public static void initAll(@Namespaces(12) List<String> namespaces) {

    logger = getLogger();
    monitoringExporterDir = monitoringExporterDir = Paths.get(RESULTS_ROOT,
        "ItMonitoringExporterWebApp", "monitoringexp").toString();
    monitoringExporterSrcDir = Paths.get(monitoringExporterDir, "srcdir").toString();
    monitoringExporterEndToEndDir = Paths.get(monitoringExporterSrcDir, "samples", "kubernetes", "end2end/").toString();
    monitoringExporterAppDir = Paths.get(monitoringExporterDir, "apps").toString();
    logger.info("Get a unique namespace for operator");
    assertNotNull(namespaces.get(0), "Namespace list is null");
    final String opNamespace = namespaces.get(0);

    logger.info("Get a unique namespace for monitoring");
    assertNotNull(namespaces.get(3), "Namespace list is null");
    monitoringNS = namespaces.get(3);

    logger.info("Get a unique namespace for NGINX");
    assertNotNull(namespaces.get(5), "Namespace list is null");
    final String nginxNamespace = namespaces.get(5);

    logger.info("Get a unique namespace for domain3");
    assertNotNull(namespaces.get(6), "Namespace list is null");
    domain3Namespace = namespaces.get(6);

    logger.info("Get a unique namespace for domain4");
    assertNotNull(namespaces.get(7), "Namespace list is null");
    domain4Namespace = namespaces.get(7);

    logger.info("Get a unique namespace for domain5");
    assertNotNull(namespaces.get(8), "Namespace list is null");
    domain5Namespace = namespaces.get(8);

    logger.info("Get a unique namespace for domain6");
    assertNotNull(namespaces.get(9), "Namespace list is null");
    domain6Namespace = namespaces.get(9);

    logger.info("Get a unique namespace for domain7");
    assertNotNull(namespaces.get(10), "Namespace list is null");
    domain7Namespace = namespaces.get(10);

    logger.info("Get a unique namespace for domain8");
    assertNotNull(namespaces.get(11), "Namespace list is null");
    domain8Namespace = namespaces.get(11);

    logger.info("install and verify operator");
    installAndVerifyOperator(opNamespace, domain3Namespace,
        domain4Namespace,domain5Namespace, domain6Namespace, domain7Namespace, domain8Namespace);

    logger.info("install monitoring exporter");
    installMonitoringExporter(monitoringExporterDir);
    assertDoesNotThrow(() -> replaceStringInFile(monitoringExporterEndToEndDir + "/grafana/values.yaml",
        "pvc-grafana", "pvc-" + grafanaReleaseName));
    exporterImage = assertDoesNotThrow(() -> createImageAndPushToRepo(monitoringExporterSrcDir, "exporter",
        domain5Namespace, OCIR_SECRET_NAME, getDockerExtraArgs()),
        "Failed to create image for exporter");
    //this is temporary untill image is released
    //buildMonitoringExporterImage("phx.ocir.io/weblogick8s/exporter:beta");

    logger.info("create and verify WebLogic domain image using model in image with model files");
    miiImage = MonitoringUtils.createAndVerifyMiiImage(monitoringExporterAppDir, MODEL_DIR + "/" + MONEXP_MODEL_FILE,
        SESSMIGR_APP_NAME, MONEXP_IMAGE_NAME);

    // install and verify NGINX
    nginxHelmParams = installAndVerifyNginx(nginxNamespace, 0, 0);
    String nginxServiceName = nginxHelmParams.getReleaseName() + "-ingress-nginx-controller";
    logger.info("NGINX service name: {0}", nginxServiceName);
    nodeportshttp = getServiceNodePort(nginxNamespace, nginxServiceName, "http");
    nodeportshttps = getServiceNodePort(nginxNamespace, nginxServiceName, "https");
    logger.info("NGINX http node port: {0}", nodeportshttp);
    logger.info("NGINX https node port: {0}", nodeportshttps);
    clusterNameMsPortMap = new HashMap<>();
    clusterNameMsPortMap.put(cluster1Name, managedServerPort);
    clusterNameMsPortMap.put(cluster2Name, managedServerPort);
    clusterNames.add(cluster1Name);
    clusterNames.add(cluster2Name);

    exporterUrl = String.format("http://%s:%s/wls-exporter/",K8S_NODEPORT_HOST,nodeportshttp);
    logger.info("create pv and pvc for monitoring");
    HashMap<String, String> labels = new HashMap<>();
    labels.put("app", "monitoring");
    labels.put("weblogic.domainUid", "test");
    String pvDir = PV_ROOT + "/ItMonitoringExporterWebApp/monexp-persistentVolume/";
    assertDoesNotThrow(() -> createPvAndPvc(prometheusReleaseName, monitoringNS, labels, pvDir));
    assertDoesNotThrow(() -> createPvAndPvc("alertmanagertest2",monitoringNS, labels,pvDir));
    assertDoesNotThrow(() -> createPvAndPvc(grafanaReleaseName, monitoringNS, labels,pvDir));
    cleanupPromGrafanaClusterRoles(prometheusReleaseName,grafanaReleaseName);
  }


  /**
   * Test covers end to end sample, provided in the Monitoring Exporter github project .
   * Create Prometheus, Grafana.
   * Create Model in Image with monitoring exporter.
   * Verify access to monitoring exporter WebLogic metrics via nginx.
   * Check generated monitoring exporter WebLogic metrics via Prometheus, Grafana.
   * Check basic functionality of monitoring exporter.
   */
  @Test
  @DisplayName("Test Basic Functionality of Monitoring Exporter.")
  void testBasicFunctionality() throws Exception {
    // create and verify one cluster mii domain
    logger.info("Create domain and verify that it's running");
    createAndVerifyDomain(miiImage, domain4Uid, domain4Namespace, "FromModel", 1, true, null, null);

    // create ingress for the domain
    logger.info("Creating ingress for domain {0} in namespace {1}", domain4Uid, domain4Namespace);
    ingressHost1List =
       createIngressForDomainAndVerify(domain4Uid, domain4Namespace, clusterNameMsPortMap, false);
    verifyMonExpAppAccessThroughNginx(ingressHost1List.get(0), 1);
    installPrometheusGrafana(PROMETHEUS_CHART_VERSION, GRAFANA_CHART_VERSION,
        domain4Namespace,
        domain4Uid);

    logger.info("Testing replace configuration");
    replaceConfiguration();
    logger.info("Testing append configuration");
    appendConfiguration();
    logger.info("Testing replace One Attribute Value AsArray configuration");
    replaceOneAttributeValueAsArrayConfiguration();
    logger.info("Testing append One Attribute Value AsArray configuration");
    appendArrayWithOneExistedAndOneDifferentAttributeValueAsArrayConfiguration();
    logger.info("Testing append with empty configuration");
    appendWithEmptyConfiguration();
    logger.info("Testing append with invalid yaml configuration");
    appendWithNotYamlConfiguration();
    logger.info("Testing replace with invalid yaml configuration");
    replaceWithNotYamlConfiguration();
    logger.info("Testing append with corrupted yaml configuration");
    appendWithCorruptedYamlConfiguration();
    logger.info("Testing replace with corrupted yaml configuration");
    replaceWithCorruptedYamlConfiguration();
    logger.info("Testing replace with dublicated values yaml configuration");
    replaceWithDublicatedValuesConfiguration();
    logger.info("Testing append with corrupted yaml configuration");
    appendWithDuplicatedValuesConfiguration();
    logger.info("Testing replace with name snake false yaml configuration");
    replaceMetricsNameSnakeCaseFalseConfiguration();
    logger.info("Testing change with no credentials configuration");
    changeConfigNoCredentials();
    logger.info("Testing change with no invalid user configuration");
    changeConfigInvalidUser();
    logger.info("Testing change with no invalid pass configuration");
    changeConfigInvalidPass();
    logger.info("Testing change with empty user configuration");
    changeConfigEmptyUser();
    logger.info("Testing change with no empty pass configuration");
    changeConfigEmptyPass();
    logger.info("Testing replace with domain qualifier configuration");
    replaceMetricsDomainQualifierTrueConfiguration();
    logger.info("Testing replace with no restPort configuration");
    replaceMetricsNoRestPortConfiguration();
  }


  /**
   * Test covers scenario when admin port enabled .
   * Create Model in Image with admin port and ssl enabled.
   * Check generated monitoring exporter WebLogic metrics via https request.
   */
  //commented out untill Issue (see oracle/weblogic-monitoring-exporter#138) will be fixed
  //@Test
  @DisplayName("Test Accesability of Monitoring Exporter dashboard and metrics if admin port is enabled.")
  void testAdminPortEnabled() throws Exception {

    // create and verify one cluster mii domain with admin port enabled
    logger.info("Create domain and verify that it's running");
    String  miiImage1 = MonitoringUtils.createAndVerifyMiiImage(monitoringExporterAppDir,
        MODEL_DIR + "/model-adminportenabled.yaml",
        SESSMIGR_APP_NAME, MONEXP_IMAGE_NAME);
    createAndVerifyDomain(miiImage1, domain8Uid, domain8Namespace,
        "FromModel", 2, false, null, null);
    logger.info("checking access to wls metrics via https connection");

    assertTrue(verifyMonExpAppAccess("wls-exporter",
        "type: WebAppComponentRuntime",
        domain8Uid,
        domain8Namespace,
        true, null),
        "monitoring exporter dashboard page can't be accessed via https");

    assertTrue(verifyMonExpAppAccess("wls-exporter/metrics",
        "wls_servlet_invocation_total_count",
        domain8Uid,
        domain8Namespace,
        true, null),
        "monitoring exporter metrics page can't be accessed via https");

  }

  /**
   * Verify access to monitoring exporter WebLogic metrics via https.
   */
  @Test
  @DisplayName("Test Monitoring Exporter access to metrics via https.")
  void testAccessExporterViaHttps() throws Exception {
    String miiImage1 = null;

    try {
      logger.info("create and verify WebLogic domain image using model in image with model files for norestport");

      miiImage1 = MonitoringUtils.createAndVerifyMiiImage(monitoringExporterAppDir + "/norestport",
          MODEL_DIR + "/" + MONEXP_MODEL_FILE, SESSMIGR_APP_NAME, MONEXP_IMAGE_NAME);

      // create and verify one cluster mii domain
      logger.info("Create domain and verify that it's running");
      createAndVerifyDomain(miiImage1, domain3Uid, domain3Namespace, "FromModel",
          1, true, null, null);
      //verify access to Monitoring Exporter
      logger.info("checking access to wls metrics via http connection");

      clusterNames.stream().forEach((clusterName) -> {
        assertFalse(verifyMonExpAppAccess("wls-exporter",
            "restPort",
            domain3Uid,
            domain3Namespace,
            false, clusterName));
        assertTrue(verifyMonExpAppAccess("wls-exporter/metrics",
            "wls_servlet_invocation_total_count",
            domain3Uid,
            domain3Namespace,
            false, clusterName));
      });
      logger.info("checking access to wl metrics via https connection");
      //set to listen only ssl
      changeListenPort(domain3Uid, domain3Namespace,"False");
      clusterNames.stream().forEach((clusterName) -> {
        assertTrue(verifyMonExpAppAccess("wls-exporter/metrics",
            "wls_servlet_invocation_total_count",
            domain3Uid,
            domain3Namespace,
            true, clusterName),
            "monitoring exporter metrics page can't be accessed via https");
      });
    } finally {
      logger.info("Shutting down domain3");
      if (miiImage1 != null) {
        deleteImage(miiImage1);
      }
    }
  }

  /**
   * Install Prometheus, Grafana using specified helm chart version, and verify that pods are running.
   * @throws ApiException when creating helm charts or pods fails
   */
  private void installPrometheusGrafana(String promChartVersion,
                                        String grafanaChartVersion,
                                        String domainNS,
                                        String domainUid
                                        ) throws IOException, ApiException {
    final String prometheusRegexValue = String.format("regex: %s;%s", domainNS, domainUid);
    if (promHelmParams == null) {
      cleanupPromGrafanaClusterRoles(prometheusReleaseName,grafanaReleaseName);
      logger.info("create a staging location for monitoring creation scripts");
      Path fileTemp = Paths.get(RESULTS_ROOT, "ItMonitoringExporter", "createTempValueFile");
      FileUtils.deleteDirectory(fileTemp.toFile());
      Files.createDirectories(fileTemp);

      logger.info("copy the promvalue.yaml to staging location");
      Path srcPromFile = Paths.get(RESOURCE_DIR, "exporter", "promvalues.yaml");
      Path targetPromFile = Paths.get(fileTemp.toString(), "promvalues.yaml");
      Files.copy(srcPromFile, targetPromFile, StandardCopyOption.REPLACE_EXISTING);
      String oldValue = "regex: default;domain1";
      replaceStringInFile(targetPromFile.toString(),
              oldValue,
              prometheusRegexValue);

      replaceStringInFile(targetPromFile.toString(),
          "pvc-alertmanager",
          "pvc-alertmanagertest2");
      replaceStringInFile(targetPromFile.toString(),
          "pvc-prometheus",
          "pvc-" + prometheusReleaseName);

      nodeportserver = getNextFreePort();
      int nodeportalertmanserver = getNextFreePort();
      promHelmParams = installAndVerifyPrometheus(prometheusReleaseName,
              monitoringNS,
              targetPromFile.toString(),
              promChartVersion,
              nodeportserver,
              nodeportalertmanserver);

      prometheusDomainRegexValue = prometheusRegexValue;
    }
    //if prometheus already installed change CM for specified domain
    if (!prometheusRegexValue.equals(prometheusDomainRegexValue)) {
      logger.info("update prometheus Config Map with domain info");
      editPrometheusCM(prometheusDomainRegexValue, prometheusRegexValue, monitoringNS,
          prometheusReleaseName + "-server");
      prometheusDomainRegexValue = prometheusRegexValue;
    }
    logger.info("Prometheus is running");

    if (grafanaHelmParams == null) {
      //logger.info("Node Port for Grafana is " + nodeportgrafana);
      grafanaHelmParams = installAndVerifyGrafana(grafanaReleaseName,
              monitoringNS,
              monitoringExporterEndToEndDir + "/grafana/values.yaml",
              grafanaChartVersion);
      assertNotNull(grafanaHelmParams, "Grafana failed to install");
      int nodeportgrafana = grafanaHelmParams.getNodePort();
      //wait until it starts dashboard
      String curlCmd = String.format("curl -v  -H 'Content-Type: application/json' "
                      + " -X GET http://admin:12345678@%s:%s/api/dashboards",
              K8S_NODEPORT_HOST, nodeportgrafana);
      testUntil(
          assertDoesNotThrow(() -> searchForKey(curlCmd, "grafana"),
            String.format("Check access to grafana dashboard")),
          logger,
          "Check access to grafana dashboard");
      logger.info("installing grafana dashboard");
      // url
      String curlCmd0 =
              String.format("curl -v -H 'Content-Type: application/json' -H \"Content-Type: application/json\""
                              + "  -X POST http://admin:12345678@%s:%s/api/datasources/"
                              + "  --data-binary @%sgrafana/datasource.json",
                      K8S_NODEPORT_HOST, nodeportgrafana, monitoringExporterEndToEndDir);

      logger.info("Executing Curl cmd {0}", curlCmd);
      assertDoesNotThrow(() -> ExecCommand.exec(curlCmd0));

      String curlCmd1 =
              String.format("curl -v -H 'Content-Type: application/json' -H \"Content-Type: application/json\""
                              + "  -X POST http://admin:12345678@%s:%s/api/dashboards/db/"
                              + "  --data-binary @%sgrafana/dashboard.json",
                      K8S_NODEPORT_HOST, nodeportgrafana, monitoringExporterEndToEndDir);
      logger.info("Executing Curl cmd {0}", curlCmd1);
      assertDoesNotThrow(() -> ExecCommand.exec(curlCmd1));

      String curlCmd2 = String.format("curl -v  -H 'Content-Type: application/json' "
                      + " -X GET http://admin:12345678@%s:%s/api/dashboards/db/weblogic-server-dashboard",
              K8S_NODEPORT_HOST, nodeportgrafana);
      testUntil(
          assertDoesNotThrow(() -> searchForKey(curlCmd2, "wls_jvm_uptime"),
            String.format("Check grafana dashboard wls against expected %s", "wls_jvm_uptime")),
          logger,
          "Check grafana dashboard metric against expected wls_jvm_uptime");
    }
    logger.info("Grafana is running");
  }


  @AfterAll
  public void tearDownAll() {

    // uninstall NGINX release
    logger.info("Uninstalling NGINX");
    if (nginxHelmParams != null) {
      assertThat(uninstallNginx(nginxHelmParams))
          .as("Test uninstallNginx1 returns true")
          .withFailMessage("uninstallNginx() did not return true")
          .isTrue();
    }

    // delete mii domain images created
    if (miiImage != null) {
      deleteImage(miiImage);
    }

    uninstallPrometheusGrafana(promHelmParams, grafanaHelmParams);

    deletePersistentVolumeClaim("pvc-alertmanager",monitoringNS);
    deletePersistentVolume("pv-testalertmanager");
    deletePersistentVolumeClaim("pvc-" + prometheusReleaseName, monitoringNS);
    deletePersistentVolume("pv-test" + prometheusReleaseName);
    deletePersistentVolumeClaim("pvc-" + grafanaReleaseName, monitoringNS);
    deletePersistentVolume("pv-test" + grafanaReleaseName);
    deleteNamespace(monitoringNS);
    deleteMonitoringExporterTempDir(monitoringExporterDir);
  }

  /**
   * Verify the monitoring exporter app can be accessed from all managed servers in the domain through NGINX.
   */
  private void verifyMonExpAppAccessThroughNginx(String nginxHost, int replicaCount) {

    List<String> managedServerNames = new ArrayList<>();
    for (int i = 1; i <= replicaCount; i++) {
      managedServerNames.add(MANAGED_SERVER_NAME_BASE + i);
    }

    // check that NGINX can access the sample apps from all managed servers in the domain
    String curlCmd =
        String.format("curl --silent --show-error --noproxy '*' -H 'host: %s' http://%s:%s@%s:%s/wls-exporter/metrics",
            nginxHost,
            ADMIN_USERNAME_DEFAULT,
            ADMIN_PASSWORD_DEFAULT,
            K8S_NODEPORT_HOST,
            nodeportshttp);
    assertThat(callWebAppAndCheckForServerNameInResponse(curlCmd, managedServerNames, 100))
        .as("Verify NGINX can access the monitoring exporter metrics "
            + "from all managed servers in the domain via http")
        .withFailMessage("NGINX can not access the monitoring exporter metrics "
            + "from one or more of the managed servers via http")
        .isTrue();
  }

  /**
   * Verify the monitoring exporter app can be accessed from all managed servers in the domain
   * through direct access to managed server dashboard.
   */
  private boolean verifyMonExpAppAccess(String uri, String searchKey, String domainUid,
                                        String domainNS, boolean isHttps, String clusterName) {
    String protocol = "http";
    String port = "8001";
    if (isHttps) {
      protocol = "https";
      port = "8100";
    }
    String podName = domainUid + "-" + clusterName + "-managed-server1";
    if (clusterName == null) {
      podName = domainUid + "-managed-server1";
    }
    // access metrics
    final String command = String.format(
        "kubectl exec -n " + domainNS + "  " + podName + " -- curl -k %s://"
            + ADMIN_USERNAME_DEFAULT
            + ":"
            + ADMIN_PASSWORD_DEFAULT
            + "@" + podName + ":%s/%s", protocol, port, uri);
    logger.info("accessing managed server exporter via " + command);

    boolean isFound = false;
    try {
      ExecResult result = ExecCommand.exec(command, true);
      String response = result.stdout().trim();
      logger.info("Response : exitValue {0}, stdout {1}, stderr {2}",
          result.exitValue(), response, result.stderr());
      isFound = response.contains(searchKey);
      logger.info("isFound value:" + isFound);
    } catch (Exception ex) {
      logger.info("Can't execute command " + command + ex.getStackTrace());
      return false;
    }
    return isFound;
  }

  private static void changeConfigInPod(String podName, String namespace, String configYaml) {
    V1Pod exporterPod = assertDoesNotThrow(() -> getPod(namespace, "", podName),
        " Can't retreive pod " + podName);
    logger.info("Copying config file {0} to pod directory {1}",
        Paths.get(RESOURCE_DIR,"/exporter/" + configYaml).toString(), "/tmp/" + configYaml);
    assertDoesNotThrow(() -> copyFileToPod(namespace, podName, "monitoring-exporter",
        Paths.get(RESOURCE_DIR,"/exporter/" + configYaml), Paths.get("/tmp/" + configYaml)),
        "Copying file to pod failed");
    execInPod(exporterPod, "monitoring-exporter", true,
        "curl -X PUT -H \"content-type: application/yaml\" --data-binary \"@/tmp/"
        + configYaml + "\" -i -u weblogic:welcome1 http://localhost:8080/configuration");
    execInPod(exporterPod, "monitoring-exporter", true, "curl -X GET  "
         + " -i -u weblogic:welcome1 http://localhost:8080/metrics");

  }

  /**
   * Check if executed command contains expected output.
   *
   * @param pod   V1Pod object
   * @param searchKey expected string in the log
   * @return true if the output matches searchKey otherwise false
   */
  private static Callable<Boolean> searchPodLogForKey(V1Pod pod, String searchKey) {
    return () -> Kubernetes.getPodLog(pod.getMetadata().getName(),
            pod.getMetadata().getNamespace()).contains(searchKey);
  }

  private void changeConfigNegative(String effect, String configFile, String expectedErrorMsg)
          throws Exception {
    final WebClient webClient = new WebClient();
    //webClient.addRequestHeader("Host", ingressHost1List.get(0));
    HtmlPage originalPage = webClient.getPage(exporterUrl);
    assertNotNull(originalPage);
    HtmlPage page = submitConfigureForm(exporterUrl, effect, configFile);
    assertTrue((page.asText()).contains(expectedErrorMsg));
    assertTrue(!(page.asText()).contains("Error 500--Internal Server Error"));
  }

  private void changeConfigNegativeAuth(
          String effect, String configFile, String expectedErrorMsg, String username, String password)
          throws Exception {
    try {
      final WebClient webClient = new WebClient();
      setCredentials(webClient, username, password);
      HtmlPage page = submitConfigureForm(exporterUrl, effect, configFile, webClient);
      throw new RuntimeException("Expected exception was not thrown ");
    } catch (FailingHttpStatusCodeException ex) {
      assertTrue((ex.getMessage()).contains(expectedErrorMsg));
    }
  }

  private HtmlPage submitConfigureForm(String exporterUrl, String effect, String configFile)
          throws Exception {
    final WebClient webClient = new WebClient();
    webClient.getOptions().setThrowExceptionOnFailingStatusCode(false);
    setCredentials(webClient);
    return submitConfigureForm(exporterUrl, effect, configFile, webClient);
  }

  private HtmlPage submitConfigureForm(
          String exporterUrl, String effect, String configFile, WebClient webClient) throws Exception {
    // Get the first page
    HtmlPage page1 = webClient.getPage(exporterUrl);
    if (page1 == null) {
      //try again
      page1 = webClient.getPage(exporterUrl);
    }
    assertNotNull(page1, "can't retrieve exporter dashboard page");
    assertTrue((page1.asText()).contains("This is the WebLogic Monitoring Exporter."));

    // Get the form that we are dealing with and within that form,
    // find the submit button and the field that we want to change.Generated form for cluster had
    // extra path for wls-exporter
    HtmlForm form = page1.getFirstByXPath("//form[@action='configure']");
    if (form == null) {
      form = page1.getFirstByXPath("//form[@action='/wls-exporter/configure']");
    }
    assertNotNull(form, "can't retrieve configure form");
    List<HtmlRadioButtonInput> radioButtons = form.getRadioButtonsByName("effect");
    assertNotNull(radioButtons, "can't retrieve buttons with effect");
    for (HtmlRadioButtonInput radioButton : radioButtons) {
      if (radioButton.getValueAttribute().equalsIgnoreCase(effect)) {
        radioButton.setChecked(true);
      }
    }

    HtmlSubmitInput button =
            page1.getFirstByXPath("//form//input[@type='submit']");
    assertNotNull(button, "can't retrieve submit button");
    final HtmlFileInput fileField = form.getInputByName("configuration");
    assertNotNull(fileField);

    // Change the value of the text field
    fileField.setValueAttribute(configFile);
    fileField.setContentType("multipart/form-data");

    // Now submit the form by clicking the button and get back the second page.
    HtmlPage page2 = null;
    try {
      page2 = button.click();
      assertNotNull(page2, "can't reach page after submit");
      assertFalse((page2.asText()).contains("Error 500--Internal Server Error"),
          "page returns Error 500--Internal Server Error");
    } catch (ClassCastException ex) {
      logger.info(" Can't generate html page, collecting the error ");
      TextPage page3 = button.click();
      assertNotNull(page3, "can't reach page after submit");
      assertTrue(page3.getContent().contains("Unable to contact the REST API"),
          "submit does not return html page, here is received page "
          + page3.getContent());
    }
    return page2;
  }

  private static void setCredentials(WebClient webClient) {
    String base64encodedUsernameAndPassword =
            base64Encode(String.format("%s:%s",
                    ADMIN_USERNAME_DEFAULT,
                    ADMIN_PASSWORD_DEFAULT));
    webClient.addRequestHeader("Authorization", "Basic " + base64encodedUsernameAndPassword);
  }

  private static void setCredentials(WebClient webClient, String username, String password) {
    String base64encodedUsernameAndPassword = base64Encode(username + ":" + password);
    webClient.addRequestHeader("Authorization", "Basic " + base64encodedUsernameAndPassword);
  }

  private static String base64Encode(String stringToEncode) {
    Base64.Encoder enc = Base64.getEncoder();
    return enc.encodeToString(stringToEncode.getBytes());
  }

  /**
   * Replace monitoring exporter configuration and verify it was applied to both managed servers.
   *
   * @throws Exception if test fails
   */
  private void replaceConfiguration() throws Exception {
    HtmlPage page = submitConfigureForm(exporterUrl, "replace", RESOURCE_DIR + "/exporter/rest_jvm.yaml");
    assertNotNull(page, "Failed to replace configuration");
    Thread.sleep(20 * 1000);

    assertTrue(page.asText().contains("JVMRuntime"),
        "Page does not contain expected JVMRuntime configuration");
    assertFalse(page.asText().contains("WebAppComponentRuntime"),
        "Page contains unexpected WebAppComponentRuntime configuration");
    //needs 10 secs to fetch the metrics to prometheus
    Thread.sleep(20 * 1000);
    // "heap_free_current{name="managed-server1"}[15s]" search for results for last 15secs
    checkMetricsViaPrometheus("heap_free_current%7Bname%3D%22" + cluster1Name + "-managed-server1%22%7D%5B15s%5D",
        cluster1Name + "-managed-server1",nodeportserver);

  }

  /**
   * Add additional monitoring exporter configuration and verify it was applied.
   *
   * @throws Exception if test fails
   */
  private void appendConfiguration() throws Exception {

    // run append
    HtmlPage page = submitConfigureForm(exporterUrl, "append", RESOURCE_DIR + "/exporter/rest_webapp.yaml");
    assertTrue(page.asText().contains("WebAppComponentRuntime"),
            "Page does not contain expected WebAppComponentRuntime configuration");
    // check previous config is there
    assertTrue(page.asText().contains("JVMRuntime"), "Page does not contain expected JVMRuntime configuration");

    String sessionAppPrometheusSearchKey =
            "wls_servlet_invocation_total_count%7Bapp%3D%22myear%22%7D%5B15s%5D";
    checkMetricsViaPrometheus(sessionAppPrometheusSearchKey, "sessmigr",nodeportserver);
  }

  /**
   * Replace monitoring exporter configuration with only one attribute and verify it was applied.
   *
   * @throws Exception if test fails
   */
  private void replaceOneAttributeValueAsArrayConfiguration() throws Exception {
    HtmlPage page =
            submitConfigureForm(exporterUrl, "replace", RESOURCE_DIR + "/exporter/rest_oneattribval.yaml");
    assertTrue(page.asText().contains("values: invocationTotalCount"));
    assertFalse(page.asText().contains("reloadTotal"));
  }

  /**
   * Append monitoring exporter configuration with one more attribute and verify it was applied
   * append to [a] new config [a,b].
   *
   * @throws Exception if test fails
   */
  private void appendArrayWithOneExistedAndOneDifferentAttributeValueAsArrayConfiguration()
          throws Exception {
    HtmlPage page =
            submitConfigureForm(exporterUrl, "replace", RESOURCE_DIR + "/exporter/rest_oneattribval.yaml");
    assertTrue(page.asText().contains("values: invocationTotalCount"));
    page = submitConfigureForm(exporterUrl, "append", RESOURCE_DIR + "/exporter/rest_twoattribs.yaml");
    assertTrue(page.asText().contains("values: [invocationTotalCount, executionTimeAverage]"));
  }

  /**
   * Replace monitoring exporter configuration with empty configuration.
   *
   * @throws Exception if test fails
   */
  private void replaceWithEmptyConfiguration() throws Exception {
    submitConfigureForm(exporterUrl, "replace", RESOURCE_DIR + "/exporter/rest_empty.yaml");
    assertFalse(verifyMonExpAppAccess("wls-exporter","values", domain4Uid, domain4Namespace,false, cluster1Name));
    assertTrue(verifyMonExpAppAccess("wls-exporter","queries", domain4Uid, domain4Namespace,false, cluster1Name));
  }

  /**
   * Try to append monitoring exporter configuration with empty configuration.
   *
   * @throws Exception if failed to apply configuration or check the expected values.
   */
  private void appendWithEmptyConfiguration() throws Exception {
    HtmlPage originalPage = submitConfigureForm(exporterUrl, "replace", RESOURCE_DIR + "/exporter/rest_jvm.yaml");
    assertNotNull(originalPage, "Failed to replace configuration");
    assertTrue(originalPage.asText().contains("JVMRuntime"),
        "Page does not contain expected JVMRuntime configuration");
    HtmlPage page = submitConfigureForm(exporterUrl, "append", RESOURCE_DIR + "/exporter/rest_empty.yaml");
    assertTrue(originalPage.asText().equals(page.asText()));
  }

  /**
   * Try to append monitoring exporter configuration with configuration file not in the yaml format.
   *
   * @throws Exception if test fails
   */
  private void appendWithNotYamlConfiguration() throws Exception {
    changeConfigNegative(
            "append", RESOURCE_DIR + "/exporter/rest_notyamlformat.yaml", "Configuration is not in YAML format");
  }

  /**
   * Try to replace monitoring exporter configuration with configuration file not in the yaml
   * format.
   *
   * @throws Exception if failed to apply configuration or check the expected values.
   */
  private void replaceWithNotYamlConfiguration() throws Exception {
    changeConfigNegative(
            "replace", RESOURCE_DIR + "/exporter/rest_notyamlformat.yaml", "Configuration is not in YAML format");
  }

  /**
   * Try to append monitoring exporter configuration with configuration file in the corrupted yaml
   * format.
   *
   * @throws Exception if test fails
   */
  private void appendWithCorruptedYamlConfiguration() throws Exception {
    changeConfigNegative(
            "append",
            RESOURCE_DIR + "/exporter/rest_notyaml.yaml",
            "Configuration YAML format has errors while scanning a simple key");
  }

  /**
   * Try to replace monitoring exporter configuration with configuration file in the corrupted yaml
   * format.
   *
   * @throws Exception if failed to apply configuration or check the expected values.
   */
  private void replaceWithCorruptedYamlConfiguration() throws Exception {
    changeConfigNegative(
            "replace",
            RESOURCE_DIR + "/exporter/rest_notyaml.yaml",
            "Configuration YAML format has errors while scanning a simple key");
  }

  /**
   * Try to replace monitoring exporter configuration with configuration file with dublicated
   * values.
   *
   * @throws Exception if test fails
   */
  private void replaceWithDublicatedValuesConfiguration() throws Exception {
    changeConfigNegative(
            "replace",
            RESOURCE_DIR + "/exporter/rest_dublicatedval.yaml",
            "Duplicate values for [deploymentState] at applicationRuntimes.componentRuntimes");
  }

  /**
   * Try to append monitoring exporter configuration with configuration file with duplicated values.
   *
   * @throws Exception if test fails
   */
  private void appendWithDuplicatedValuesConfiguration() throws Exception {
    changeConfigNegative(
            "append",
            RESOURCE_DIR + "/exporter/rest_dublicatedval.yaml",
            "Duplicate values for [deploymentState] at applicationRuntimes.componentRuntimes");
  }

  /**
   * Try to replace monitoring exporter configuration with configuration file with
   * NameSnakeCase=false.
   *
   * @throws Exception if failed to apply configuration or check the expected values.
   */
  private void replaceMetricsNameSnakeCaseFalseConfiguration() throws Exception {
    HtmlPage page =
            submitConfigureForm(exporterUrl, "replace", RESOURCE_DIR + "/exporter/rest_snakecasefalse.yaml");
    assertNotNull(page);
    assertFalse(page.asText().contains("metricsNameSnakeCase"));
    String searchKey = "wls_servlet_executionTimeAverage%7Bapp%3D%22myear%22%7D%5B15s%5D";
    checkMetricsViaPrometheus(searchKey, "sessmigr",nodeportserver);
  }

  /**
   * Try to replace monitoring exporter configuration with configuration file with
   * no restPort value.
   *
   * @throws Exception if failed to apply configuration or check the expected values.
   */
  private void replaceMetricsNoRestPortConfiguration() throws Exception {
    HtmlPage page =
        submitConfigureForm(exporterUrl, "replace", RESOURCE_DIR + "/exporter/norestport.yaml");
    assertNotNull(page);
    assertFalse(page.asText().contains("restPort"));
    //needs 10 secs to fetch the metrics to prometheus
    Thread.sleep(20 * 1000);
    // "heap_free_current{name="managed-server1"}[15s]" search for results for last 15secs

    String prometheusSearchKey1 =
        "heap_free_current";
    checkMetricsViaPrometheus(prometheusSearchKey1, "managed-server1",nodeportserver);
  }

  /**
   * Test to replace monitoring exporter configuration with configuration file with
   * domainQualifier=true.
   *
   * @throws Exception if failed to apply configuration or check the expected values.
   */
  private void replaceMetricsDomainQualifierTrueConfiguration() throws Exception {
    HtmlPage page =
            submitConfigureForm(exporterUrl, "replace", RESOURCE_DIR + "/exporter/rest_domainqualtrue.yaml");
    assertNotNull(page);
    logger.info("page - " + page.asText());
    assertTrue(page.asText().contains("domainQualifier"));

    String searchKey = "wls_servlet_executionTimeAverage%7Bapp%3D%22myear%22%7D%5B15s%5D";
    checkMetricsViaPrometheus(searchKey, "\"domain\":\"wls-monexp-domain-1" + "\"",nodeportserver);
  }

  /**
   * Test to change monitoring exporter configuration without authentication.
   *
   * @throws Exception if failed to apply configuration or check the expected values.
   */
  // verify that change configuration fails without authentication
  private void changeConfigNoCredentials() throws Exception {
    WebClient webClient = new WebClient();
    String expectedErrorMsg = "401 Unauthorized for " + exporterUrl;
    try {
      HtmlPage page =
              submitConfigureForm(
                      exporterUrl, "append", RESOURCE_DIR + "/exporter/rest_snakecasetrue.yaml", webClient);
      throw new RuntimeException("Form was submitted successfully with no credentials");
    } catch (FailingHttpStatusCodeException ex) {
      assertTrue((ex.getMessage()).contains(expectedErrorMsg));
    }
  }

  /**
   * Try to change monitoring exporter configuration with invalid username.
   *
   * @throws Exception if the expected exception message does not match
   */
  private void changeConfigInvalidUser() throws Exception {
    changeConfigNegativeAuth(
            "replace",
            RESOURCE_DIR + "/exporter/rest_snakecasetrue.yaml",
            "401 Unauthorized for " + exporterUrl,
            "invaliduser",
            ADMIN_PASSWORD_DEFAULT);
  }

  /**
   * Try to change monitoring exporter configuration with invalid password.
   *
   * @throws Exception if the expected exception message does not match
   */
  private void changeConfigInvalidPass() throws Exception {
    changeConfigNegativeAuth(
            "replace",
            RESOURCE_DIR + "/exporter/rest_snakecasetrue.yaml",
            "401 Unauthorized for " + exporterUrl,
            ADMIN_USERNAME_DEFAULT,
            "invalidpass");
  }

  /**
   * Try to change monitoring exporter configuration with empty username.
   *
   * @throws Exception if the expected exception message does not match
   */
  private void changeConfigEmptyUser() throws Exception {
    changeConfigNegativeAuth(
            "replace",
            RESOURCE_DIR + "/exporter/rest_snakecasetrue.yaml",
            "401 Unauthorized for " + exporterUrl,
            "",
            ADMIN_PASSWORD_DEFAULT);
  }

  /**
   * Try to change monitoring exporter configuration with empty pass.
   *
   * @throws Exception if the expected exception message does not match
   */
  private void changeConfigEmptyPass() throws Exception {
    changeConfigNegativeAuth(
            "replace",
            RESOURCE_DIR + "/exporter/rest_snakecasetrue.yaml",
            "401 Unauthorized for " + exporterUrl,
            ADMIN_USERNAME_DEFAULT,
            "");
  }

  private boolean changeListenPort(String domainUid, String domainNS, String setListenPortEnabled) throws Exception {
    // copy changeListenPort.py and callpyscript.sh to Admin Server pod
    String adminServerPodName = domainUid + "-" + ADMIN_SERVER_NAME_BASE;
    V1Pod adminPod = Kubernetes.getPod(domainNS, null, adminServerPodName);
    if (adminPod == null) {
      logger.info("The admin pod {0} does not exist in namespace {1}!", adminServerPodName, domainNS);
      return false;
    }

    logger.info("Copying changeListenPort.py and callpyscript.sh to admin server pod");
    try {
      copyFileToPod(domainNS, adminServerPodName, null,
          Paths.get(RESOURCE_DIR, "python-scripts", "changeListenPort.py"),
          Paths.get("/u01/oracle/changeListenPort.py"));

      copyFileToPod(domainNS, adminServerPodName, null,
          Paths.get(RESOURCE_DIR, "bash-scripts", "callpyscript.sh"),
          Paths.get("/u01/oracle/callpyscript.sh"));
    } catch (ApiException apex) {
      logger.severe("Got ApiException while copying file to admin pod {0}", apex.getResponseBody());
      return false;
    } catch (IOException ioex) {
      logger.severe("Got IOException while copying file to admin pod {0}", ioex.getStackTrace());
      return false;
    }

    logger.info("Adding execute mode for callpyscript.sh");
    ExecResult result = exec(adminPod, null, true,
        "/bin/sh", "-c", "chmod +x /u01/oracle/callpyscript.sh");
    if (result.exitValue() != 0) {
      return false;
    }
    logger.info("Changing ListenPortEnabled");
    String command = new StringBuffer("/u01/oracle/callpyscript.sh /u01/oracle/changeListenPort.py ")
        .append(ADMIN_USERNAME_DEFAULT)
        .append(" ")
        .append(ADMIN_PASSWORD_DEFAULT)
        .append(" t3://")
        .append(adminServerPodName)
        .append(":7001 ")
        .append(setListenPortEnabled)
        .append(" ")
        .append("managed-server1")
        .toString();

    result = exec(adminPod, null, true, "/bin/sh", "-c", command);
    if (result.exitValue() != 0) {
      return false;
    }
    return true;
  }

  private static String convertToJson(String yaml) {
    final Object loadedYaml = new Yaml().load(yaml);
    return new Gson().toJson(loadedYaml, LinkedHashMap.class);
  }

}