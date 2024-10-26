// Copyright (c) 2021, 2024, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.weblogic.kubernetes;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import io.kubernetes.client.custom.IntOrString;
import io.kubernetes.client.openapi.ApiException;
import io.kubernetes.client.openapi.models.V1Container;
import io.kubernetes.client.openapi.models.V1ContainerPort;
import io.kubernetes.client.openapi.models.V1Deployment;
import io.kubernetes.client.openapi.models.V1DeploymentSpec;
import io.kubernetes.client.openapi.models.V1DeploymentStrategy;
import io.kubernetes.client.openapi.models.V1LabelSelector;
import io.kubernetes.client.openapi.models.V1LocalObjectReference;
import io.kubernetes.client.openapi.models.V1ObjectMeta;
import io.kubernetes.client.openapi.models.V1Pod;
import io.kubernetes.client.openapi.models.V1PodSpec;
import io.kubernetes.client.openapi.models.V1PodTemplateSpec;
import io.kubernetes.client.openapi.models.V1Service;
import io.kubernetes.client.openapi.models.V1ServicePort;
import io.kubernetes.client.openapi.models.V1ServiceSpec;
import oracle.weblogic.kubernetes.actions.impl.GrafanaParams;
import oracle.weblogic.kubernetes.actions.impl.NginxParams;
import oracle.weblogic.kubernetes.actions.impl.PrometheusParams;
import oracle.weblogic.kubernetes.actions.impl.primitive.Kubernetes;
import oracle.weblogic.kubernetes.annotations.IntegrationTest;
import oracle.weblogic.kubernetes.annotations.Namespaces;
import oracle.weblogic.kubernetes.assertions.impl.Deployment;
import oracle.weblogic.kubernetes.logging.LoggingFacade;
import oracle.weblogic.kubernetes.utils.ExecResult;
import org.apache.commons.io.FileUtils;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static oracle.weblogic.kubernetes.TestConstants.ADMIN_PASSWORD_DEFAULT;
import static oracle.weblogic.kubernetes.TestConstants.ADMIN_USERNAME_DEFAULT;
import static oracle.weblogic.kubernetes.TestConstants.GRAFANA_CHART_VERSION;
import static oracle.weblogic.kubernetes.TestConstants.IMAGE_PULL_POLICY;
import static oracle.weblogic.kubernetes.TestConstants.IT_MONITORINGEXPORTERSAMPLES_ALERT_HTTP_NODEPORT;
import static oracle.weblogic.kubernetes.TestConstants.IT_MONITORINGEXPORTERSAMPLES_NGINX_HTTPS_HOSTPORT;
import static oracle.weblogic.kubernetes.TestConstants.IT_MONITORINGEXPORTERSAMPLES_NGINX_HTTPS_NODEPORT;
import static oracle.weblogic.kubernetes.TestConstants.IT_MONITORINGEXPORTERSAMPLES_NGINX_HTTP_HOSTPORT;
import static oracle.weblogic.kubernetes.TestConstants.IT_MONITORINGEXPORTERSAMPLES_NGINX_HTTP_NODEPORT;
import static oracle.weblogic.kubernetes.TestConstants.IT_MONITORINGEXPORTERSAMPLES_PROMETHEUS_HTTP_HOSTPORT;
import static oracle.weblogic.kubernetes.TestConstants.IT_MONITORINGEXPORTERSAMPLES_PROMETHEUS_HTTP_NODEPORT;
import static oracle.weblogic.kubernetes.TestConstants.K8S_NODEPORT_HOST;
import static oracle.weblogic.kubernetes.TestConstants.KIND_CLUSTER;
import static oracle.weblogic.kubernetes.TestConstants.MANAGED_SERVER_NAME_BASE;
import static oracle.weblogic.kubernetes.TestConstants.OKD;
import static oracle.weblogic.kubernetes.TestConstants.OKE_CLUSTER_PRIVATEIP;
import static oracle.weblogic.kubernetes.TestConstants.PROMETHEUS_CHART_VERSION;
import static oracle.weblogic.kubernetes.TestConstants.RESULTS_ROOT;
import static oracle.weblogic.kubernetes.TestConstants.RESULTS_TEMPFILE;
import static oracle.weblogic.kubernetes.TestConstants.TEST_IMAGES_REPO_SECRET_NAME;
import static oracle.weblogic.kubernetes.TestConstants.WEBLOGIC_IMAGE_NAME;
import static oracle.weblogic.kubernetes.TestConstants.WEBLOGIC_IMAGE_TAG;
import static oracle.weblogic.kubernetes.TestConstants.WLSIMG_BUILDER;
import static oracle.weblogic.kubernetes.TestConstants.WLSIMG_BUILDER_DEFAULT;
import static oracle.weblogic.kubernetes.actions.ActionConstants.MODEL_DIR;
import static oracle.weblogic.kubernetes.actions.ActionConstants.WLS;
import static oracle.weblogic.kubernetes.actions.TestActions.deleteImage;
import static oracle.weblogic.kubernetes.actions.TestActions.deletePersistentVolume;
import static oracle.weblogic.kubernetes.actions.TestActions.deletePersistentVolumeClaim;
import static oracle.weblogic.kubernetes.actions.TestActions.getPod;
import static oracle.weblogic.kubernetes.actions.TestActions.getServiceNodePort;
import static oracle.weblogic.kubernetes.actions.TestActions.shutdownDomain;
import static oracle.weblogic.kubernetes.actions.TestActions.uninstallNginx;
import static oracle.weblogic.kubernetes.actions.impl.primitive.Kubernetes.deleteNamespace;
import static oracle.weblogic.kubernetes.assertions.TestAssertions.isPodReady;
import static oracle.weblogic.kubernetes.assertions.TestAssertions.searchPodLogForKey;
import static oracle.weblogic.kubernetes.assertions.impl.Kubernetes.listPods;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.createIngressPathRouting;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.createTestWebAppWarFile;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.formatIPv6Host;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.getNextFreePort;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.getServiceExtIPAddrtOke;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.scaleAndVerifyCluster;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.testUntil;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.withLongRetryPolicy;
import static oracle.weblogic.kubernetes.utils.DbUtils.createSqlFileInPod;
import static oracle.weblogic.kubernetes.utils.DbUtils.runMysqlInsidePod;
import static oracle.weblogic.kubernetes.utils.ExecCommand.exec;
import static oracle.weblogic.kubernetes.utils.FileUtils.replaceStringInFile;
import static oracle.weblogic.kubernetes.utils.ImageUtils.createImageAndPushToRepo;
import static oracle.weblogic.kubernetes.utils.ImageUtils.createImageAndVerify;
import static oracle.weblogic.kubernetes.utils.ImageUtils.imageRepoLoginAndPushImageToRegistry;
import static oracle.weblogic.kubernetes.utils.LoadBalancerUtils.createIngressForDomainAndVerify;
import static oracle.weblogic.kubernetes.utils.LoadBalancerUtils.installAndVerifyNginx;
import static oracle.weblogic.kubernetes.utils.MonitoringUtils.checkMetricsViaPrometheus;
import static oracle.weblogic.kubernetes.utils.MonitoringUtils.cleanupPromGrafanaClusterRoles;
import static oracle.weblogic.kubernetes.utils.MonitoringUtils.createAndVerifyDomain;
import static oracle.weblogic.kubernetes.utils.MonitoringUtils.createAndVerifyMiiImage;
import static oracle.weblogic.kubernetes.utils.MonitoringUtils.deleteMonitoringExporterTempDir;
import static oracle.weblogic.kubernetes.utils.MonitoringUtils.editPrometheusCM;
import static oracle.weblogic.kubernetes.utils.MonitoringUtils.installAndVerifyGrafana;
import static oracle.weblogic.kubernetes.utils.MonitoringUtils.installAndVerifyPrometheus;
import static oracle.weblogic.kubernetes.utils.MonitoringUtils.installMonitoringExporter;
import static oracle.weblogic.kubernetes.utils.MonitoringUtils.uninstallPrometheusGrafana;
import static oracle.weblogic.kubernetes.utils.MonitoringUtils.verifyMonExpAppAccess;
import static oracle.weblogic.kubernetes.utils.MonitoringUtils.verifyMonExpAppAccessThroughNginx;
import static oracle.weblogic.kubernetes.utils.MySQLDBUtils.createMySQLDB;
import static oracle.weblogic.kubernetes.utils.OKDUtils.createRouteForOKD;
import static oracle.weblogic.kubernetes.utils.OperatorUtils.installAndVerifyOperator;
import static oracle.weblogic.kubernetes.utils.PersistentVolumeUtils.createPvAndPvc;
import static oracle.weblogic.kubernetes.utils.ThreadSafeLogger.getLogger;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verify Prometheus, Grafana, Webhook, Coordinator are installed and running
 * Verify the monitoring exporter installed in model in image domain can generate the WebLogic metrics.
 * Verify WebLogic metrics can be accessed via NGINX ingress controller.
 * Verify WebLogic metrics can be accessed via Prometheus
 */
@DisplayName("Verify end to end sample, provided in the Monitoring Exporter github project")
@Tag("oke-weekly-sequential")
@Tag("kind-parallel")
@Tag("okd-wls-mrg")
@IntegrationTest
class ItMonitoringExporterSamples {

  // domain constants
  private static final int replicaCount = 2;
  private static int managedServersCount = 2;
  private static String domain1Namespace = null;
  private static String domain2Namespace = null;
  private static String domain1Uid = "monexpdomain1";
  private static String domain2Uid = "monexpdomain2";

  private static NginxParams nginxHelmParams = null;
  private static int nodeportshttp = 0;
  private static int nodeportshttps = 0;
  private static List<String> ingressHost1List = null;
  private static List<String> ingressHost2List = null;
  private static String dbService = null;

  private static String monitoringNS = null;
  private static String webhookNS = null;
  PrometheusParams promHelmParams = null;
  GrafanaParams grafanaHelmParams = null;
  private static String ingressIP = null;

  private static V1Service webhookService = null;
  private static V1Deployment webhookDepl = null;
  private static V1Service coordinatorService = null;
  private static V1Deployment coordinatorDepl = null;
  // constants for creating domain image using model in image
  private static final String MONEXP_MODEL_FILE = "model.monexp.yaml";
  private static final String MONEXP_WDT_FILE = "/demo-domains/domainBuilder/scripts/simple-topology.yaml";
  private static final String MONEXP_IMAGE_NAME = "monexp-image";
  private static final String SESSMIGR_APP_NAME = "sessmigr-app";

  private static String cluster1Name = "cluster-1";
  private static String cluster2Name = "cluster-2";
  private static String miiImage = null;
  private static String wdtImage = null;
  private static String webhookImage = null;
  private static String exporterImage = null;
  private static String  coordinatorImage = null;
  private static int managedServerPort = 8001;
  private static int nodeportPrometheus;
  private static String prometheusDomainRegexValue = null;
  private static Map<String, Integer> clusterNameMsPortMap;
  private static LoggingFacade logger = null;
  private static List<String> clusterNames = new ArrayList<>();
  private static String releaseSuffix = "testsamples";
  private static String prometheusReleaseName = "prometheus" + releaseSuffix;
  private static String grafanaReleaseName = "grafana" + releaseSuffix;
  private static  String monitoringExporterDir;
  private static  String monitoringExporterSrcDir;
  private static  String monitoringExporterEndToEndDir;
  private static  String monitoringExporterAppDir;
  private static String hostPortPrometheus;

  private static String testWebAppWarLoc = null;

  /**
   * Install operator and NGINX. Create model in image domain with multiple clusters.
   * Create ingress for the domain.
   *
   * @param namespaces list of namespaces created by the IntegrationTestWatcher by the
   *                   JUnit engine parameter resolution mechanism
   */
  @BeforeAll

  public static void initAll(@Namespaces(6) List<String> namespaces) {

    logger = getLogger();
    monitoringExporterDir = Paths.get(RESULTS_ROOT,
        "ItMonitoringExporterSamples", "monitoringexp").toString();
    monitoringExporterSrcDir = Paths.get(monitoringExporterDir, "srcdir").toString();
    monitoringExporterEndToEndDir = Paths.get(monitoringExporterSrcDir, "samples", "kubernetes", "end2end/").toString();
    monitoringExporterAppDir = Paths.get(monitoringExporterDir, "apps").toString();

    logger.info("Get a unique namespace for operator");
    assertNotNull(namespaces.get(0), "Namespace list is null");
    final String opNamespace = namespaces.get(0);

    logger.info("Get a unique namespace for WebLogic domain1");
    assertNotNull(namespaces.get(1), "Namespace list is null");
    domain1Namespace = namespaces.get(1);

    logger.info("Get a unique namespace for WebLogic domain2");
    assertNotNull(namespaces.get(2), "Namespace list is null");
    domain2Namespace = namespaces.get(2);

    logger.info("Get a unique namespace for monitoring");
    assertNotNull(namespaces.get(3), "Namespace list is null");
    monitoringNS = namespaces.get(3);

    logger.info("Get a unique namespace for webhook");
    assertNotNull(namespaces.get(4), "Namespace list is null");
    webhookNS = namespaces.get(4);

    logger.info("Get a unique namespace for NGINX");
    assertNotNull(namespaces.get(5), "Namespace list is null");
    final String nginxNamespace = namespaces.get(5);

    // create testwebapp.war
    testWebAppWarLoc = createTestWebAppWarFile(domain1Namespace);

    logger.info("install and verify operator");
    installAndVerifyOperator(opNamespace, domain1Namespace,domain2Namespace);

    logger.info("install monitoring exporter");
    installMonitoringExporter(monitoringExporterDir);

    logger.info("create and verify WebLogic domain image using model in image with model files");
    miiImage = createAndVerifyMiiImage(monitoringExporterAppDir, MODEL_DIR + "/" + MONEXP_MODEL_FILE,
        SESSMIGR_APP_NAME, MONEXP_IMAGE_NAME);
    if (!OKD) {
      // install and verify NGINX
      if (OKE_CLUSTER_PRIVATEIP) {
        nginxHelmParams = installAndVerifyNginx(nginxNamespace, 0, 0);
      } else {
        nginxHelmParams = installAndVerifyNginx(nginxNamespace,
            IT_MONITORINGEXPORTERSAMPLES_NGINX_HTTP_NODEPORT,
            IT_MONITORINGEXPORTERSAMPLES_NGINX_HTTPS_NODEPORT);
      }
      String nginxServiceName = nginxHelmParams.getHelmParams().getReleaseName() + "-ingress-nginx-controller";
      ingressIP = getServiceExtIPAddrtOke(nginxServiceName, nginxNamespace) != null
          ? getServiceExtIPAddrtOke(nginxServiceName, nginxNamespace) : K8S_NODEPORT_HOST;
      logger.info("NGINX service name: {0}", nginxServiceName);
      if (KIND_CLUSTER && !WLSIMG_BUILDER.equals(WLSIMG_BUILDER_DEFAULT)) {
        nodeportshttp = IT_MONITORINGEXPORTERSAMPLES_NGINX_HTTP_HOSTPORT;
        nodeportshttps = IT_MONITORINGEXPORTERSAMPLES_NGINX_HTTPS_HOSTPORT;
      } else {
        nodeportshttp = getServiceNodePort(nginxNamespace, nginxServiceName, "http");
        nodeportshttps = getServiceNodePort(nginxNamespace, nginxServiceName, "https");
      }
      logger.info("NGINX http node port: {0}", nodeportshttp);
      logger.info("NGINX https node port: {0}", nodeportshttps);
    }
    clusterNameMsPortMap = new HashMap<>();
    clusterNameMsPortMap.put(cluster1Name, managedServerPort);
    clusterNameMsPortMap.put(cluster2Name, managedServerPort);
    clusterNames.add(cluster1Name);
    clusterNames.add(cluster2Name);

    HashMap<String, String> labels = new HashMap<>();
    labels.put("app", "monitoring");
    labels.put("weblogic.domainUid", domain1Uid);
    if (!OKD) {
      logger.info("create pv and pvc for monitoring");
      String className = "ItMonitoringExporterSamples";
      assertDoesNotThrow(() -> createPvAndPvc(prometheusReleaseName, monitoringNS, labels, className));
      assertDoesNotThrow(() -> createPvAndPvc("alertmanager" + releaseSuffix, monitoringNS, labels, className));
      assertDoesNotThrow(() -> createPvAndPvc(grafanaReleaseName, monitoringNS, labels, className));
      cleanupPromGrafanaClusterRoles(prometheusReleaseName, grafanaReleaseName);
    }
    //start  MySQL database instance
    assertDoesNotThrow(() -> {
      dbService = createMySQLDB("mysql", "root", "root123", domain2Namespace, null);
      assertNotNull(dbService, "Failed to create database");
      V1Pod pod = getPod(domain2Namespace, null, "mysql");
      assertNotNull(pod, "pod is null");
      assertNotNull(pod.getMetadata(), "pod metadata is null");
      createFileInPod(pod.getMetadata().getName(), domain2Namespace, "root123");
      runMysqlInsidePod(pod.getMetadata().getName(), domain2Namespace, "root123", "/tmp/grant.sql");
      runMysqlInsidePod(pod.getMetadata().getName(), domain2Namespace, "root123", "/tmp/create.sql");
    });
  }

  /**
   * Test covers end to end sample, provided in the Monitoring Exporter github project.
   * Create Prometheus, Grafana, Webhook, Coordinator.
   * Create domain in Image with monitoring exporter.
   * Verify access to monitoring exporter WebLogic metrics via nginx.
   * Check generated by monitoring exporter WebLogic metrics via Prometheus, Grafana.
   * Fire Alert using Webhook.
   * Change prometheus to add different domain to monitor.
   */
  @Test
  @DisplayName("Test End to End example from MonitoringExporter github project.")
  void testEndToEndViaChart() throws Exception {
    try {
      wdtImage = createAndVerifyDomainInImage();
      logger.info("Create wdt domain and verify that it's running");
      createAndVerifyDomain(wdtImage, domain2Uid, domain2Namespace, "Image", replicaCount,
          false, null, null);

      if (!OKD) {
        ingressHost2List
            = createIngressForDomainAndVerify(domain2Uid, domain2Namespace, 0, clusterNameMsPortMap,
                true, nginxHelmParams.getIngressClassName(), false, 0);
        logger.info("verify access to Monitoring Exporter");
        if (OKE_CLUSTER_PRIVATEIP) {
          verifyMonExpAppAccessThroughNginx(ingressHost2List.get(0), managedServersCount, ingressIP);
        } else {
          verifyMonExpAppAccessThroughNginx(ingressHost2List.get(0), managedServersCount, nodeportshttp);
        }
      } else {
        String clusterService = domain2Uid + "-cluster-cluster-1";
        String hostName = createRouteForOKD(clusterService, domain2Namespace);
        logger.info("hostName = {0} ", hostName);
        verifyMonExpAppAccess(managedServersCount,hostName);
      }
      if (!OKD) {
        logger.info("Installing Prometheus and Grafana");
        installPrometheusGrafana(PROMETHEUS_CHART_VERSION, GRAFANA_CHART_VERSION,
            domain2Namespace,
            domain2Uid);
      }

      installWebhook();
      installCoordinator(domain2Namespace);
      if (!OKD) {
        logger.info("verify metrics via prometheus");
        String testappPrometheusSearchKey =
            "wls_servlet_invocation_total_count%7Bapp%3D%22test-webapp%22%7D%5B15s%5D";
        checkMetricsViaPrometheus(testappPrometheusSearchKey, "test-webapp", hostPortPrometheus,
            prometheusReleaseName
                + "." + monitoringNS);
        logger.info("fire alert by scaling down");
        fireAlert();

        logger.info("switch to monitor another domain");
        logger.info("create and verify WebLogic domain image using model in image with model files");

        // create and verify one cluster mii domain
        logger.info("Create domain and verify that it's running");
        createAndVerifyDomain(miiImage, domain1Uid, domain1Namespace, "FromModel", 1,
            true, null, null);

        String oldRegex = String.format("regex: %s;%s", domain2Namespace, domain2Uid);
        String newRegex = String.format("regex: %s;%s", domain1Namespace, domain1Uid);
        editPrometheusCM(oldRegex, newRegex, monitoringNS, prometheusReleaseName + "-server");
        String sessionAppPrometheusSearchKey =
            "wls_servlet_invocation_total_count%7Bapp%3D%22myear%22%7D%5B15s%5D";
        checkMetricsViaPrometheus(sessionAppPrometheusSearchKey, "sessmigr", hostPortPrometheus,
            prometheusReleaseName
                + "." + monitoringNS);
      }
    } finally {
      shutdownDomain(domain1Uid, domain1Namespace);
      shutdownDomain(domain2Uid, domain2Namespace);
    }
  }

  private void fireAlert() throws ApiException {
    // scale domain2
    logger.info("Scaling cluster {0} of domain {1} in namespace {2} to {3} servers.",
        cluster1Name, domain2Uid, domain2Namespace, 1);
    managedServersCount = 1;
    scaleAndVerifyCluster(domain2Uid + "-" + cluster1Name, domain2Uid, domain2Namespace,
        domain2Uid + "-" + MANAGED_SERVER_NAME_BASE, replicaCount, managedServersCount,
        null, null);

    //check webhook log for firing alert
    List<V1Pod> pods = listPods(webhookNS, "app=webhook").getItems();
    assertNotNull((pods), "No pods are running in namespace : " + webhookNS);
    V1Pod pod = pods.stream()
        .filter(testpod -> testpod.getMetadata() != null && testpod.getMetadata().getName() != null
            && testpod.getMetadata().getName().contains("webhook"))
        .findAny()
        .orElse(null);

    assertNotNull(pod, "Can't find running webhook pod");
    logger.info("Wait for the webhook to fire alert and check webhook log file in {0} namespace ", webhookNS);

    testUntil(withLongRetryPolicy,
        assertDoesNotThrow(() -> searchPodLogForKey(pod,
            "Some WLS cluster has only one running server for more than 1 minutes"),
            "webhook failed to fire alert"),
        logger,
        "webhook to fire alert");
  }

  /**
   * Install Prometheus, Grafana using specified helm chart version, and verify that pods are running.
   * @throws ApiException when creating helm charts or pods fails
   */
  private void installPrometheusGrafana(String promChartVersion,
                                        String grafanaChartVersion,
                                        String domainNS,
                                        String domainUid
  ) throws ApiException, UnknownHostException {
    final String prometheusRegexValue = String.format("regex: %s;%s", domainNS, domainUid);
    if (promHelmParams == null) {
      cleanupPromGrafanaClusterRoles(prometheusReleaseName,grafanaReleaseName);
      String promHelmValuesFileDir = Paths.get(RESULTS_ROOT, this.getClass().getSimpleName(),
              "prometheus" + releaseSuffix).toString();
      if (KIND_CLUSTER && !WLSIMG_BUILDER.equals(WLSIMG_BUILDER_DEFAULT)) {
        promHelmParams = installAndVerifyPrometheus(releaseSuffix,
            monitoringNS,
            promChartVersion,
            prometheusRegexValue, promHelmValuesFileDir, webhookNS,
            IT_MONITORINGEXPORTERSAMPLES_PROMETHEUS_HTTP_NODEPORT, IT_MONITORINGEXPORTERSAMPLES_ALERT_HTTP_NODEPORT);
      } else {
        promHelmParams = installAndVerifyPrometheus(releaseSuffix,
            monitoringNS,
            promChartVersion,
            prometheusRegexValue, promHelmValuesFileDir, webhookNS);
      }    
      assertNotNull(promHelmParams, " Failed to install prometheus");
      nodeportPrometheus = promHelmParams.getNodePortServer();
      String host = formatIPv6Host(K8S_NODEPORT_HOST);
      if (KIND_CLUSTER && !WLSIMG_BUILDER.equals(WLSIMG_BUILDER_DEFAULT)) {
        nodeportPrometheus = IT_MONITORINGEXPORTERSAMPLES_PROMETHEUS_HTTP_HOSTPORT;
        host = formatIPv6Host(InetAddress.getLocalHost().getHostAddress());
      }      
      prometheusDomainRegexValue = prometheusRegexValue;

      hostPortPrometheus = host + ":" + nodeportPrometheus;
      if (OKE_CLUSTER_PRIVATEIP) {
        hostPortPrometheus = ingressIP;
      }
      if (OKD) {
        hostPortPrometheus = createRouteForOKD("prometheus" + releaseSuffix
            + "-service", monitoringNS) + ":" + nodeportPrometheus;
      }
      String ingressClassName = nginxHelmParams.getIngressClassName();
      createIngressPathRouting(monitoringNS, "/api",
          prometheusReleaseName + "-server", 80, ingressClassName, prometheusReleaseName
              + "." + monitoringNS);
    }
    //if prometheus already installed change CM for specified domain
    if (!prometheusRegexValue.equals(prometheusDomainRegexValue)) {
      logger.info("update prometheus Config Map with domain info");
      editPrometheusCM(prometheusDomainRegexValue, prometheusRegexValue,
          monitoringNS, prometheusReleaseName + "-server");
      prometheusDomainRegexValue = prometheusRegexValue;
    }
    logger.info("Prometheus is running");

    if (grafanaHelmParams == null) {
      String grafanaHelmValuesFileDir =  Paths.get(RESULTS_ROOT, this.getClass().getSimpleName(),
              grafanaReleaseName).toString();
      grafanaHelmParams = installAndVerifyGrafana(grafanaReleaseName,
              monitoringNS,
              grafanaHelmValuesFileDir,
              grafanaChartVersion);
      assertNotNull(grafanaHelmParams, "Grafana failed to install");
      String host = formatIPv6Host(K8S_NODEPORT_HOST);

      String hostPortGrafana = host + ":" + grafanaHelmParams.getNodePort();
      if (OKD) {
        hostPortGrafana = createRouteForOKD(grafanaReleaseName, monitoringNS) + ":" + grafanaHelmParams.getNodePort();
      }
      // installVerifyGrafanaDashBoard(hostPortGrafana, monitoringExporterEndToEndDir);
    }
    logger.info("Grafana is running");
  }

  /**
   * Create a Webhook image, install and verify that pod is running.
   * @throws ApiException when creating images or pods fails
   */
  private void installWebhook() throws ApiException {
    Map<String,String> labelMap = new HashMap<>();
    labelMap.put("app", "webhook");
    assertTrue(installAndVerifyPodFromCustomImage(monitoringExporterEndToEndDir + "/webhook",
        "webhook",
        webhookNS,
        labelMap, TEST_IMAGES_REPO_SECRET_NAME), "Failed to start webhook");
  }

  /**
   * Create a Coordinator image, install and verify that pod is running.
   * @param namespace of domain to coordinate
   * @throws ApiException when creating images or pods fails
   */
  private void installCoordinator(String namespace) throws ApiException {
    Map<String,String> labelMap = new HashMap<>();
    labelMap.put("app", "coordinator");
    assertTrue(installAndVerifyPodFromCustomImage(monitoringExporterSrcDir + "/config_coordinator",
        "coordinator",
        namespace,
        labelMap, "coordsecret"), "Failed to start coordinator");
  }

  private static void createFileInPod(String podName, String namespace, String password) throws IOException {

    ExecResult result = assertDoesNotThrow(() -> exec(new String("hostname -i"), true));
    String ip = result.stdout();
    String sqlCommand = "select user();\n"
        + "SELECT host, user FROM mysql.user;\n"
        + "CREATE USER 'root'@'%' IDENTIFIED BY '" + password + "';\n"
        + "GRANT ALL PRIVILEGES ON *.* TO 'root'@'%' WITH GRANT OPTION;\n"
        + "CREATE USER 'root'@'" + ip + "' IDENTIFIED BY '" + password + "';\n"
        + "GRANT ALL PRIVILEGES ON *.* TO 'root'@'" + ip + "' WITH GRANT OPTION;\n"
        + "SELECT host, user FROM mysql.user;";
    String fileName = "grant.sql";
    createSqlFileInPod(podName, namespace, sqlCommand, fileName);
    fileName = "create.sql";
    sqlCommand =
        "CREATE DATABASE " + domain2Uid + ";\n"
            + "CREATE USER 'wluser1' IDENTIFIED BY 'wlpwd123';\n"
            + "GRANT ALL ON " + domain2Uid + ".* TO 'wluser1';";
    createSqlFileInPod(podName, namespace, sqlCommand, fileName);
  }

  @AfterAll
  public void tearDownAll() {

    // delete mii domain images created for parameterized test
    if (miiImage != null) {
      deleteImage(miiImage);
    }
    if (wdtImage != null) {
      deleteImage(miiImage);
    }
    uninstallPrometheusGrafana(promHelmParams.getHelmParams(), grafanaHelmParams);
    promHelmParams = null;
    grafanaHelmParams = null;
    prometheusDomainRegexValue = null;

    deletePersistentVolumeClaim("pvc-alertmanager" + releaseSuffix, monitoringNS);
    deletePersistentVolume("pv-testalertmanager" + releaseSuffix);
    deletePersistentVolumeClaim("pvc-" + prometheusReleaseName, monitoringNS);
    deletePersistentVolume("pv-test" + prometheusReleaseName);
    deletePersistentVolumeClaim("pvc-" + grafanaReleaseName, monitoringNS);
    deletePersistentVolume("pv-test" + grafanaReleaseName);
    deleteNamespace(monitoringNS);
    uninstallDeploymentService(webhookDepl, webhookService);
    uninstallDeploymentService(coordinatorDepl, coordinatorService);
    if (nginxHelmParams != null) {
      assertThat(uninstallNginx(nginxHelmParams.getHelmParams()))
          .as("Test uninstallNginx1 returns true")
          .withFailMessage("uninstallNginx() did not return true")
          .isTrue();
    }
    // delete coordinator and webhook images
    if (webhookImage != null) {
      deleteImage(webhookImage);
    }
    if (coordinatorImage != null) {
      deleteImage(coordinatorImage);
    }
    deleteMonitoringExporterTempDir(monitoringExporterDir);
  }

  /**
   * Create, install  Webhook or Coordinator and wait up to five minutes until the pod is ready.
   *
   * @param dockerFileDir directory where dockerfile is located
   * @param baseImageName base image name
   * @param namespace namespace
   * @return status of installation
   */
  public static boolean installAndVerifyPodFromCustomImage(String dockerFileDir,
                                                           String baseImageName,
                                                           String namespace,
                                                           Map<String, String> labels,
                                                           String secretName) throws ApiException {
    //build webhook image
    String imagePullPolicy = IMAGE_PULL_POLICY;
    logger.info("Creating and Installing {0} in namespace {1}", baseImageName, namespace);
    String image = createImageAndPushToRepo(dockerFileDir, baseImageName, namespace, secretName, "");

    if (baseImageName.equalsIgnoreCase(("webhook"))) {
      webhookImage = image;
      createWebHook(webhookImage, imagePullPolicy, namespace, TEST_IMAGES_REPO_SECRET_NAME);
    } else if (baseImageName.contains("coordinator")) {
      coordinatorImage = image;
      createCoordinator(coordinatorImage, imagePullPolicy, namespace, "coordsecret");
    } else {
      throw new ApiException("Custom image creation for " + baseImageName + "is not supported");
    }
    // wait for the pod to be ready
    logger.info("Wait for the {0} pod is ready in namespace {1}", baseImageName, namespace);
    testUntil(
        assertDoesNotThrow(() -> isPodReady(namespace, labels, baseImageName),
            baseImageName + " isPodReady failed with ApiException"),
        logger,
        "{0} to be running in namespace {1}",
        baseImageName,
        namespace);
    return true;
  }

  /**
   * Create Webhook deployment and service.
   *
   * @param image full image name for deployment
   * @param imagePullPolicy policy for image
   * @param namespace webhook namespace
   * @param secretName webhook image secret name
   */
  private static void createWebHook(String image,
                                    String imagePullPolicy,
                                    String namespace,
                                    String secretName) throws ApiException {
    Map<String, String> labels = new HashMap<>();
    labels.put("app", "webhook");

    webhookDepl = new V1Deployment()
        .apiVersion("apps/v1")
        .kind("Deployment")
        .metadata(new V1ObjectMeta()
            .name("webhook")
            .namespace(namespace)
            .labels(labels))
        .spec(new V1DeploymentSpec()
            .replicas(1)
            .selector(new V1LabelSelector()
                .matchLabels(labels))
            .template(new V1PodTemplateSpec()
                .metadata(new V1ObjectMeta()
                    .labels(labels))
                .spec(new V1PodSpec()
                    .containers(Arrays.asList(
                        new V1Container()
                            .image(image)
                            .imagePullPolicy(imagePullPolicy)
                            .name("webhook")))
                    .imagePullSecrets(Arrays.asList(
                        new V1LocalObjectReference()
                            .name(secretName))))));

    logger.info("Create deployment for webhook in namespace {0}",
        namespace);
    boolean deploymentCreated = assertDoesNotThrow(() -> Kubernetes.createDeployment(webhookDepl),
        String.format("Create deployment failed with ApiException for webhook in namespace %s",
            namespace));
    assertTrue(deploymentCreated, String.format(
        "Create deployment failed with ApiException for webhook in namespace %s ",
        namespace));
    logger.info("Checking if the deployment is ready {0} completed in namespace {1}",
        "webhook", namespace);
    testUntil(
        Deployment.isReady("webhook", labels, namespace),
        logger,
        "deployment webhook to be completed in namespace {0}",
        namespace);

    webhookService = new V1Service()
        .metadata(new V1ObjectMeta()
            .name("webhook")
            .namespace(namespace)
            .labels(labels))
        .spec(new V1ServiceSpec()
            .ports(Arrays.asList(
                new V1ServicePort()
                    .port(8080)
                    .protocol("TCP")))
            .selector(labels));

    logger.info("Create service for webhook in namespace {0}",
        namespace);
    boolean serviceCreated = assertDoesNotThrow(() -> Kubernetes.createService(webhookService),
        String.format("Create service failed with ApiException for webhook in namespace %s",
            namespace));
    assertTrue(serviceCreated, String.format(
        "Create service failed with ApiException for webhook in namespace %s ",
        namespace));
    // wait for the webhook pod to be ready
    logger.info("Wait for the webhook pod is ready in namespace {0}", namespace);
    Map<String,String> labelMap = new HashMap<>();
    labelMap.put("app", "webhook");
    testUntil(
        assertDoesNotThrow(() -> isPodReady(namespace, labelMap, "webhook"),
            "webhook podIsReady failed with ApiException"),
        logger,
        "webhook to be running in namespace {0}",
        namespace);
  }

  /**
   * Uninstall provided deployment and service.
   */
  private static void uninstallDeploymentService(V1Deployment deployment, V1Service service) {
    String namespace = null;
    String serviceName = null;
    String deploymentName = null;
    try {
      if (service != null && service.getMetadata() != null) {
        serviceName = service.getMetadata().getName();
        namespace = service.getMetadata().getNamespace();
        Kubernetes.deleteService(serviceName, namespace);
      }
    } catch (Exception ex) {
      logger.warning(ex.getMessage());
      logger.warning("Failed to delete service {0} in namespace {1} ",
          serviceName, namespace);
    }
    try {
      if (deployment != null && deployment.getMetadata() != null) {
        deploymentName = deployment.getMetadata().getName();
        namespace = deployment.getMetadata().getNamespace();
        Kubernetes.deleteDeployment(namespace, deploymentName);
      }
    } catch (Exception ex) {
      logger.warning(ex.getMessage());
      logger.warning("Failed to delete deployment {0} in namespace {1}",
          deploymentName, namespace);
    }
    if (namespace != null) {
      deleteNamespace(namespace);
    }
  }

  /**
   * Create Coordinator deployment and service.
   *
   * @param image full image name for deployment
   * @param imagePullPolicy policy for image
   * @param namespace coordinator namespace
   * @param secretName coordinator secret name
   */
  private static void createCoordinator(String image,
                                        String imagePullPolicy,
                                        String namespace,
                                        String secretName) throws ApiException {
    if (coordinatorDepl == null) {
      Map<String, String> labels = new HashMap<>();
      labels.put("app", "coordinator");
      coordinatorDepl = new V1Deployment()
          .apiVersion("apps/v1")
          .kind("Deployment")
          .metadata(new V1ObjectMeta()
              .name("coordinator")
              .namespace(namespace)
              .labels(labels))
          .spec(new V1DeploymentSpec()
              .replicas(1)
              .selector(new V1LabelSelector()
                  .matchLabels(labels))
              .strategy(new V1DeploymentStrategy()
                  .type("Recreate"))
              .template(new V1PodTemplateSpec()
                  .metadata(new V1ObjectMeta()
                      .labels(labels))
                  .spec(new V1PodSpec()
                      .containers(Arrays.asList(
                          new V1Container()
                              .image(image)
                              .imagePullPolicy(imagePullPolicy)
                              .name("coordinator")
                              .ports(Arrays.asList(
                                  new V1ContainerPort()
                                      .containerPort(8999)))))
                      .imagePullSecrets(Arrays.asList(
                          new V1LocalObjectReference()
                              .name(secretName))))));

      logger.info("Create deployment for coordinator in namespace {0}",
          namespace);
      boolean deploymentCreated = assertDoesNotThrow(() -> Kubernetes.createDeployment(coordinatorDepl),
          String.format("Create deployment failed with ApiException for coordinator in namespace %s",
              namespace));
      assertTrue(deploymentCreated, String.format(
          "Create deployment failed with ApiException for coordinator in namespace %s ",
          namespace));
      testUntil(
          Deployment.isReady("coordinator", labels, namespace),
          logger,
          "deployment coordinator to be completed in namespace {0}",
          namespace);

      HashMap<String,String> annotations = new HashMap<>();
      annotations.put("kubectl.kubernetes.io/last-applied-configuration","");
      coordinatorService = new V1Service()
          .metadata(new V1ObjectMeta()
              .name("coordinator")
              .annotations(annotations)
              .namespace(namespace)
              .labels(labels))
          .spec(new V1ServiceSpec()
              .ports(Arrays.asList(
                  new V1ServicePort()
                      .port(8999)
                      .targetPort(new IntOrString(8999))))
              .type("NodePort")
              .selector(labels));

      logger.info("Create service for coordinator in namespace {0}",
          namespace);
      boolean success = assertDoesNotThrow(() -> Kubernetes.createService(coordinatorService),
          String.format("Create service failed with ApiException for coordinator in namespace %s",
              namespace));
      assertTrue(success, "Coordinator service creation failed");
    }
  }


  /**
   * Create and verify domain in image from endtoend sample topology with monitoring exporter.
   * @return image name
   */
  private static String createAndVerifyDomainInImage() {
    // create image with model files
    logger.info("Create image with model file with monitoring exporter app and verify");
    String app1Path = String.format("%s/wls-exporter.war", monitoringExporterAppDir);
    String app2Path = testWebAppWarLoc;

    List<String> appList = new ArrayList<>();
    appList.add(app1Path);
    appList.add(app2Path);

    int t3ChannelPort = getNextFreePort();

    Properties p = new Properties();
    p.setProperty("ADMIN_USER", ADMIN_USERNAME_DEFAULT);
    p.setProperty("ADMIN_PWD", ADMIN_PASSWORD_DEFAULT);
    p.setProperty("DOMAIN_NAME", domain2Uid);
    p.setProperty("ADMIN_NAME", "admin-server");
    p.setProperty("PRODUCTION_MODE_ENABLED", "true");
    p.setProperty("CLUSTER_NAME", cluster1Name);
    p.setProperty("CLUSTER_TYPE", "DYNAMIC");
    p.setProperty("CONFIGURED_MANAGED_SERVER_COUNT", "2");
    p.setProperty("MANAGED_SERVER_NAME_BASE", "managed-server");
    p.setProperty("T3_CHANNEL_PORT", Integer.toString(t3ChannelPort));
    p.setProperty("T3_PUBLIC_ADDRESS", K8S_NODEPORT_HOST);
    p.setProperty("MANAGED_SERVER_PORT", "8001");
    p.setProperty("SERVER_START_MODE", "prod");
    p.setProperty("ADMIN_PORT", "7001");
    p.setProperty("MYSQL_USER", "wluser1");
    p.setProperty("MYSQL_PWD", "wlpwd123");
    // create a temporary WebLogic domain property file as a input for WDT model file
    File domainPropertiesFile = assertDoesNotThrow(() ->
            File.createTempFile("domain", ".properties", new File(RESULTS_TEMPFILE)),
        "Failed to create domain properties file");
    assertDoesNotThrow(() ->
            p.store(new FileOutputStream(domainPropertiesFile), "WDT properties file"),
        "Failed to write domain properties file");

    final List<String> propertyList = Collections.singletonList(domainPropertiesFile.getPath());

    // build the model file list
    logger.info("create a staging location for the scripts");
    Path fileTemp = Paths.get(RESULTS_ROOT, "ItMonitoringExporterSamples", "temp","sampleTopologyTemp");
    assertDoesNotThrow(() -> FileUtils.deleteDirectory(fileTemp.toFile()),"Failed to delete temp dir for topology");

    assertDoesNotThrow(() -> Files.createDirectories(fileTemp), "Failed to create temp dir for topology");
    logger.info("copy the " + MONEXP_WDT_FILE + "  to staging location");
    Path srcPromFile = Paths.get(monitoringExporterEndToEndDir, MONEXP_WDT_FILE);
    Path targetPromFile = Paths.get(fileTemp.toString(), "simple-topology.yaml");
    assertDoesNotThrow(() -> Files.copy(srcPromFile, targetPromFile,
        StandardCopyOption.REPLACE_EXISTING)," Failed to copy files");
    String dbURL = dbService + "." + domain2Namespace + ".svc";
    assertDoesNotThrow(() -> {
      replaceStringInFile(targetPromFile.toString(),
          "mysql.default.svc.cluster.local",
          dbURL);
    });

    final List<String> modelList = Collections.singletonList(targetPromFile.toString());

    wdtImage =
        createImageAndVerify(MONEXP_IMAGE_NAME,
            modelList,
            appList,
            propertyList,
            WEBLOGIC_IMAGE_NAME,
            WEBLOGIC_IMAGE_TAG,
            WLS,
            false,
            domain2Uid, true);

    // repo login and push image to registry if necessary
    imageRepoLoginAndPushImageToRegistry(wdtImage);

    return wdtImage;
  }
}
