// Copyright (c) 2023, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.weblogic.kubernetes;

import java.io.IOException;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.gargoylesoftware.htmlunit.TextPage;
import com.gargoylesoftware.htmlunit.WebClient;
import com.gargoylesoftware.htmlunit.html.HtmlFileInput;
import com.gargoylesoftware.htmlunit.html.HtmlForm;
import com.gargoylesoftware.htmlunit.html.HtmlPage;
import com.gargoylesoftware.htmlunit.html.HtmlRadioButtonInput;
import com.gargoylesoftware.htmlunit.html.HtmlSubmitInput;
import io.kubernetes.client.openapi.ApiException;
import oracle.weblogic.kubernetes.actions.impl.GrafanaParams;
import oracle.weblogic.kubernetes.actions.impl.NginxParams;
import oracle.weblogic.kubernetes.actions.impl.PrometheusParams;
import oracle.weblogic.kubernetes.annotations.IntegrationTest;
import oracle.weblogic.kubernetes.annotations.Namespaces;
import oracle.weblogic.kubernetes.logging.LoggingFacade;
import oracle.weblogic.kubernetes.utils.MonitoringUtils;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static oracle.weblogic.kubernetes.TestConstants.ADMIN_PASSWORD_DEFAULT;
import static oracle.weblogic.kubernetes.TestConstants.ADMIN_USERNAME_DEFAULT;
import static oracle.weblogic.kubernetes.TestConstants.GRAFANA_CHART_VERSION;
import static oracle.weblogic.kubernetes.TestConstants.K8S_NODEPORT_HOST;
import static oracle.weblogic.kubernetes.TestConstants.OKD;
import static oracle.weblogic.kubernetes.TestConstants.PROMETHEUS_CHART_VERSION;
import static oracle.weblogic.kubernetes.TestConstants.RESULTS_ROOT;
import static oracle.weblogic.kubernetes.actions.ActionConstants.MODEL_DIR;
import static oracle.weblogic.kubernetes.actions.ActionConstants.RESOURCE_DIR;
import static oracle.weblogic.kubernetes.actions.TestActions.deleteImage;
import static oracle.weblogic.kubernetes.actions.TestActions.deletePersistentVolume;
import static oracle.weblogic.kubernetes.actions.TestActions.deletePersistentVolumeClaim;
import static oracle.weblogic.kubernetes.actions.TestActions.getServiceNodePort;
import static oracle.weblogic.kubernetes.actions.TestActions.uninstallNginx;
import static oracle.weblogic.kubernetes.actions.impl.primitive.Kubernetes.deleteNamespace;
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
import static oracle.weblogic.kubernetes.utils.MonitoringUtils.uninstallPrometheusGrafana;
import static oracle.weblogic.kubernetes.utils.MonitoringUtils.verifyMonExpAppAccess;
import static oracle.weblogic.kubernetes.utils.MonitoringUtils.verifyMonExpAppAccessThroughNginx;
import static oracle.weblogic.kubernetes.utils.OKDUtils.createRouteForOKD;
import static oracle.weblogic.kubernetes.utils.OperatorUtils.installAndVerifyOperator;
import static oracle.weblogic.kubernetes.utils.PersistentVolumeUtils.createPvAndPvc;
import static oracle.weblogic.kubernetes.utils.ThreadSafeLogger.getLogger;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verify WebLogic metrics can be filtered as expected.
 */
@DisplayName("Verify WebLogic Metric is processed and filtered as expected by MonitoringExporter")
@IntegrationTest
@Tag("olcne")
@Tag("oke-sequential")
@Tag("kind-parallel")
@Tag("okd-wls-mrg")
class ItMonitoringExporterMetricsFiltering {

  // domain constants
  private static String domain1Namespace = null;
  private static String domain1Uid = "monexp-domain-4";
  private static NginxParams nginxHelmParams = null;
  private static int nodeportshttp = 0;
  private static int nodeportshttps = 0;
  private static List<String> ingressHost1List = null;

  private static String monitoringNS = null;
  static PrometheusParams promHelmParams = null;
  GrafanaParams grafanaHelmParams = null;
  private static String monitoringExporterEndToEndDir = null;
  private static String monitoringExporterSrcDir = null;
  private static String monitoringExporterAppDir = null;
  // constants for creating domain image using model in image
  private static final String MONEXP_MODEL_FILE = "model.monexp.filter.yaml";
  private static final String JDBC_MODEL_FILE = "multi-model-one-ds.20.yaml";
  private static final String MONEXP_IMAGE_NAME = "monexp-image";
  private static final String SESSMIGR_APP_NAME = "sessmigr-app";
  private static final String STICKYSESS_APP_NAME = "stickysess-app";

  private static String cluster1Name = "cluster-1";
  private static String cluster2Name = "cluster-2";
  private static String miiImage = null;
  private static int managedServerPort = 8001;
  private static int nodeportPrometheus;
  private static String exporterUrl = null;
  private static String prometheusDomainRegexValue = null;
  private static Map<String, Integer> clusterNameMsPortMap;
  private static LoggingFacade logger = null;
  private static List<String> clusterNames = new ArrayList<>();
  private static String releaseSuffix = "test2";
  private static String prometheusReleaseName = "prometheus" + releaseSuffix;
  private static String grafanaReleaseName = "grafana" + releaseSuffix;
  private static  String monitoringExporterDir;
  private static String hostPortPrometheus = null;


  /**
   * Install operator and NGINX. Create model in image domain with multiple clusters.
   * Create ingress for the domain.
   *
   * @param namespaces list of namespaces created by the IntegrationTestWatcher by the
   *                   JUnit engine parameter resolution mechanism
   */
  @BeforeAll

  public void initAll(@Namespaces(4) List<String> namespaces) {

    logger = getLogger();
    monitoringExporterDir = Paths.get(RESULTS_ROOT,
        "ItMonitoringExporterMetricsFiltering", "monitoringexp").toString();
    monitoringExporterSrcDir = Paths.get(monitoringExporterDir, "srcdir").toString();
    monitoringExporterEndToEndDir = Paths.get(monitoringExporterSrcDir, "samples", "kubernetes", "end2end").toString();
    monitoringExporterAppDir = Paths.get(monitoringExporterDir, "apps").toString();
    logger.info("Get a unique namespace for operator");
    assertNotNull(namespaces.get(0), "Namespace list is null");
    final String opNamespace = namespaces.get(0);

    logger.info("Get a unique namespace for monitoring");
    assertNotNull(namespaces.get(1), "Namespace list is null");
    monitoringNS = namespaces.get(1);

    logger.info("Get a unique namespace for NGINX");
    assertNotNull(namespaces.get(2), "Namespace list is null");
    final String nginxNamespace = namespaces.get(2);
    

    logger.info("Get a unique namespace for domain1");
    assertNotNull(namespaces.get(3), "Namespace list is null");
    domain1Namespace = namespaces.get(3);

    logger.info("install and verify operator");
    installAndVerifyOperator(opNamespace,
        domain1Namespace);

    logger.info("install monitoring exporter");
    installMonitoringExporter(monitoringExporterDir, true);

    logger.info("create and verify WebLogic domain image using model in image with model files");
    List<String> modelList = new ArrayList<>();
    modelList.add(MODEL_DIR + "/" + MONEXP_MODEL_FILE);
    modelList.add(MODEL_DIR + "/" + JDBC_MODEL_FILE);
    miiImage = MonitoringUtils.createAndVerifyMiiImage(monitoringExporterAppDir, modelList,
        STICKYSESS_APP_NAME, SESSMIGR_APP_NAME, MONEXP_IMAGE_NAME);
    if (!OKD) {
      // install and verify NGINX
      nginxHelmParams = installAndVerifyNginx(nginxNamespace, 0, 0);

      String nginxServiceName = nginxHelmParams.getHelmParams().getReleaseName() + "-ingress-nginx-controller";
      logger.info("NGINX service name: {0}", nginxServiceName);
      nodeportshttp = getServiceNodePort(nginxNamespace, nginxServiceName, "http");
      nodeportshttps = getServiceNodePort(nginxNamespace, nginxServiceName, "https");
    }
    logger.info("NGINX http node port: {0}", nodeportshttp);
    logger.info("NGINX https node port: {0}", nodeportshttps);
    clusterNameMsPortMap = new HashMap<>();
    clusterNameMsPortMap.put(cluster1Name, managedServerPort);
    clusterNameMsPortMap.put(cluster2Name, managedServerPort);
    clusterNames.add(cluster1Name);
    clusterNames.add(cluster2Name);
    exporterUrl = String.format("http://%s:%s/wls-exporter/",K8S_NODEPORT_HOST,nodeportshttp);
    HashMap<String, String> labels = new HashMap<>();
    labels.put("app", "monitoring");
    labels.put("weblogic.domainUid", "test");
    if (!OKD) {
      logger.info("create pv and pvc for monitoring");
      assertDoesNotThrow(() -> createPvAndPvc(prometheusReleaseName, monitoringNS,
          labels, this.getClass().getSimpleName()));
      assertDoesNotThrow(() -> createPvAndPvc("alertmanager" + releaseSuffix,
          monitoringNS, labels, this.getClass().getSimpleName()));
      assertDoesNotThrow(() -> createPvAndPvc(grafanaReleaseName, monitoringNS,
          labels, this.getClass().getSimpleName()));
      cleanupPromGrafanaClusterRoles(prometheusReleaseName, grafanaReleaseName);
    }
    assertDoesNotThrow(() -> setupDomainAndMonitoringTools(domain1Namespace, domain1Uid),
        "failed to setup domain and monitoring tools");
  }

  /**
   * Check filtering functionality of monitoring exporter for includedKeyValues on top level.
   */
  @Test
  @DisplayName("Test Filtering of the Metrics with includedKeyValues of Monitoring Exporter on the top level.")
  void testFilterIIncludedKeysFromTopLevel() throws Exception {
    logger.info("Testing filtering only included specific app name in the metrics ");
    List<String> checkIncluded = new ArrayList<>();
    checkIncluded.add("app=\"wls-exporter\"");
    List<String> checkExcluded = new ArrayList<>();
    checkExcluded.add("app=\"myear1\"");

    replaceConfigurationWithFilter(RESOURCE_DIR + "/exporter/rest_filter_included_webapp_name.yaml",
        checkIncluded, checkExcluded);
  }

  /**
   * Check filtering functionality of monitoring exporter for includedKeyValues on  sublevel.
   */
  @Test
  @DisplayName("Test Filtering of the Metrics with includedKeyValues of Monitoring Exporter on sublevel.")
  void testFilterIIncludedKeysFromSubLevel() throws Exception {
    logger.info("Testing filtering only included specific app name in the metrics ");
    List<String> checkIncluded = new ArrayList<>();
    checkIncluded.add("servletName=\"com.oracle.wls.exporter.webapp.ExporterServlet\"");
    List<String> checkExcluded = new ArrayList<>();
    checkExcluded.add("servletName=\"com.oracle.wls.exporter.webapp.MainServlet\"");

    replaceConfigurationWithFilter(RESOURCE_DIR + "/exporter/rest_filter_included_servlet_name.yaml",
        checkIncluded, checkExcluded);
  }

  /**
   * Check filtering functionality of monitoring exporter for includedKeyValues on  sublevel.
   */
  @Test
  @DisplayName("Test Filtering of the Metrics with includedKeyValues of "
      + "Monitoring Exporter on both upper and sublevel.")
  void testFilterIIncludedKeysFromBothLevels() throws Exception {
    logger.info("Testing filtering only included specific app name in the metrics ");
    List<String> checkIncluded = new ArrayList<>();
    checkIncluded.add("servletName=\"com.oracle.wls.exporter.webapp.ExporterServlet\"");
    checkIncluded.add("app=\"wls-exporter\"");
    List<String> checkExcluded = new ArrayList<>();
    checkExcluded.add("servletName=\"com.oracle.wls.exporter.webapp.MainServlet\"");
    checkExcluded.add("app=\"myear1\"");
    replaceConfigurationWithFilter(RESOURCE_DIR
        + "/exporter/rest_filter_included_webapp_and_servlet_names.yaml",checkIncluded, checkExcluded);
  }

  /**
   * Check filtering functionality of monitoring exporter for includedKeyValues on top level.
   */
  @Test
  @DisplayName("Test Filtering of the Metrics with excludedKeyValues of Monitoring Exporter on the top level.")
  void testFilterExcludedKeysFromTopLevel() throws Exception {
    logger.info("Testing filtering only excluded specific app name in the metrics ");
    List<String> checkIncluded = new ArrayList<>();
    checkIncluded.add("app=\"myear1\"");
    List<String> checkExcluded = new ArrayList<>();
    checkExcluded.add("app=\"wls-exporter\"");

    replaceConfigurationWithFilter(RESOURCE_DIR
        + "/exporter/rest_filter_excluded_webapp_name.yaml",checkIncluded, checkExcluded);
  }

  /**
   * Check filtering functionality of monitoring exporter for includedKeyValues on  sublevel.
   */
  @Test
  @DisplayName("Test Filtering of the Metrics with excludedKeyValues of Monitoring Exporter on sublevel.")
  void testFilterExcludedKeysFromSubLevel() throws Exception {
    logger.info("Testing filtering only excluded specific app name in the metrics ");
    List<String> checkIncluded = new ArrayList<>();
    checkIncluded.add("servletName=\"com.oracle.wls.exporter.webapp.MainServlet\"");
    List<String> checkExcluded = new ArrayList<>();
    checkExcluded.add("servletName=\"com.oracle.wls.exporter.webapp.ExporterServlet\"");

    replaceConfigurationWithFilter(RESOURCE_DIR + "/exporter/rest_filter_excluded_servlet_name.yaml",
        checkIncluded, checkExcluded);
  }

  /**
   * Check filtering functionality of monitoring exporter for excludedKeyValues on  sublevel.
   */
  @Test
  @DisplayName("Test Filtering of the Metrics with includedKeyValues "
      + "of Monitoring Exporter on both upper and sublevel.")
  void testFilterExcludedKeysFromBothLevels() throws Exception {
    logger.info("Testing filtering only excluded specific app name in the metrics ");
    List<String> checkIncluded = new ArrayList<>();
    checkIncluded.add("servletName=\"com.oracle.wls.exporter.webapp.ExporterServlet\"");
    checkIncluded.add("app=\"myear1\"");
    List<String> checkExcluded = new ArrayList<>();
    checkExcluded.add("servletName=\"com.oracle.wls.exporter.webapp.MainServlet\"");
    checkExcluded.add("app=\"myear123\"");
    replaceConfigurationWithFilter(RESOURCE_DIR
        + "/exporter/rest_filter_excluded_webapp_and_servlet_names.yaml",checkIncluded, checkExcluded);
  }

  /**
   * Check filtering functionality of monitoring exporter for excludedKeyValues on  sublevel.
   */
  @Test
  @DisplayName("Test Filtering of the Metrics with includedKeyValues of Monitoring Exporter on "
      + " upper and excludedKeyValues on sublevel.")
  void testFilterIncludedTopExcludedKeysSubLevels() throws Exception {
    logger.info("Testing filtering only excluded specific app name in the metrics ");
    List<String> checkIncluded = new ArrayList<>();
    checkIncluded.add("app=\"wls-exporter\"");
    List<String> checkExcluded = new ArrayList<>();
    checkExcluded.add("servletName=\"com.oracle.wls.exporter.webapp.ExporterServlet\"");
    replaceConfigurationWithFilter(RESOURCE_DIR
        + "/exporter/rest_filter_included_webapp_excluded_servlet_name.yaml",checkIncluded, checkExcluded);
  }

  /**
   * Check filtering functionality of monitoring exporter for
   * combo of includedKeyValues and excludedKeyValues on  top level.
   */
  @Test
  @DisplayName("Test Filtering of the Metrics with includedKeyValues and excludedKeyValues of Monitoring Exporter on "
      + " top level.")
  void testFilterIncludedExcludedKeysComboTopLevel() throws Exception {
    logger.info("Testing filtering included and excluded specific app names in the metrics ");
    List<String> checkIncluded = new ArrayList<>();
    checkIncluded.add("app=\"myear1\"");
    List<String> checkExcluded = new ArrayList<>();
    checkExcluded.add("app=\"myear123\"");
    replaceConfigurationWithFilter(RESOURCE_DIR
        + "/exporter/rest_filter_included_excluded_webapp_names.yaml",checkIncluded, checkExcluded);
  }

  /**
   * Check filtering functionality of monitoring exporter for
   * combo of includedKeyValues and excludedKeyValues on  sub level.
   */
  @Test
  @DisplayName("Test Filtering of the Metrics with includedKeyValues and excludedKeyValues of Monitoring Exporter on "
      + " sub level.")
  void testFilterIncludedExcludedKeysComboSubLevel() throws Exception {
    logger.info("Testing filtering included and excluded specific app names in the metrics ");
    List<String> checkIncluded = new ArrayList<>();
    checkIncluded.add("servletName=\"com.oracle.wls.exporter.webapp");
    List<String> checkExcluded = new ArrayList<>();
    checkExcluded.add("servletName=\"com.oracle.wls.exporter.webapp.ExporterServlet\"");
    replaceConfigurationWithFilter(RESOURCE_DIR
        + "/exporter/rest_filter_included_excluded_servlet_name.yaml",checkIncluded, checkExcluded);
  }

  /**
   * Check filtering functionality of monitoring exporter for
   * not existed includedKeyValues .
   */
  @Test
  @DisplayName("Test Filtering of the Metrics with includedKeyValues for not existed key of Monitoring Exporter on "
      + " top level.")
  void testFilterIncludedNotExistedKeysTopLevel() throws Exception {
    logger.info("Testing filtering included not existed app names in the metrics ");
    List<String> checkIncluded = new ArrayList<>();
    checkIncluded.add("");
    List<String> checkExcluded = new ArrayList<>();
    checkExcluded.add("app=");
    replaceConfigurationWithFilter(RESOURCE_DIR
        + "/exporter/rest_filter_included_not_existedkey.yaml",checkIncluded, checkExcluded);
  }

  /**
   * Check filtering functionality of monitoring exporter for
   * not existed includedKeyValues .
   */
  @Test
  @DisplayName("Test Filtering of the Metrics with excludedKeyValues for not existed key of Monitoring Exporter on "
      + " top level.")
  void testFilterExcludedNotExistedKeysTopLevel() throws Exception {
    logger.info("Testing filtering excluded not existing app names in the metrics ");
    List<String> checkIncluded = new ArrayList<>();
    checkIncluded.add("app=");
    List<String> checkExcluded = new ArrayList<>();
    checkExcluded.add("app=notexisted");
    replaceConfigurationWithFilter(RESOURCE_DIR
        + "/exporter/rest_filter_excluded_not_existedkey.yaml",checkIncluded, checkExcluded);
  }

  /**
   * Check filtering functionality of monitoring exporter for
   * not existed includedKeyValues on sublevel.
   */
  @Test
  @DisplayName("Test Filtering of the Metrics with includedKeyValues for not existed key of Monitoring Exporter on "
      + " sub level.")
  void testFilterIncludedNotExistedKeysSubLevel() throws Exception {
    logger.info("Testing filtering included not existed servlet names in the metrics ");
    List<String> checkIncluded = new ArrayList<>();
    checkIncluded.add("");
    List<String> checkExcluded = new ArrayList<>();
    checkExcluded.add("servletName=");
    replaceConfigurationWithFilter(RESOURCE_DIR
        + "/exporter/rest_filter_included_not_existedkey_sublevel.yaml",checkIncluded, checkExcluded);
  }

  /**
   * Check filtering functionality of monitoring exporter for
   * not existed includedKeyValues on sublevel.
   */
  @Test
  @DisplayName("Test Filtering of the Metrics with excludedKeyValues for not existed key of Monitoring Exporter on "
      + " sub level.")
  void testFilterExcludedNotExistedKeysSubLevel() throws Exception {
    logger.info("Testing filtering excluded not existing servlet in the metrics ");
    List<String> checkIncluded = new ArrayList<>();
    checkIncluded.add("servletName=");
    List<String> checkExcluded = new ArrayList<>();
    checkExcluded.add("servletName=notexisted");
    replaceConfigurationWithFilter(RESOURCE_DIR
        + "/exporter/rest_filter_excluded_not_existedkey_sublevel.yaml",checkIncluded, checkExcluded);
  }

  /**
   * Check filtering functionality of monitoring exporter for
   * of the properties field with no admin privileges.
   */
  @Test
  @DisplayName("Test Filtering of the properties field with no admin privileges")
  void testFilterPrivilegedFields() throws Exception {
    logger.info("Testing filtering of properties field with no admin privileges");

    replaceConfiguration(RESOURCE_DIR
        + "/exporter/rest_filter_jdbc_privileged.yaml",
        "wls_datasource_state%7Bname%3D%22TestDataSource%22%7D%5B15s%5D",
        "Running, Suspended, Shutdown, Overloaded, Unknown", "TestDataSource");
  }

  /**
   * Check filtering functionality of monitoring exporter for appending configuration with filter.
   */
  @Test
  @DisplayName("Test appending configuration with filter.")
  void testAppendConfigWithFilter() throws Exception {
    logger.info("Testing appending configuration containing filters ");
    List<String> checkIncluded = new ArrayList<>();
    checkIncluded.add("app=\"myear1\"");
    List<String> checkExcluded = new ArrayList<>();
    checkExcluded.add("app=\"wls-exporter\"");

    replaceConfigurationWithFilter(RESOURCE_DIR
        + "/exporter/rest_filter_excluded_webapp_name.yaml",checkIncluded, checkExcluded);
    appendConfiguration(RESOURCE_DIR
        + "/exporter/rest_filter_excluded_servlet_name.yaml");
    logger.info("Verify metrics configuration has not change");
    checkIncluded.add("servletName=\"JspServlet\"");
    verifyMetrics(checkIncluded, checkExcluded);
  }

  private void setupDomainAndMonitoringTools(String domainNamespace, String domainUid)
      throws IOException, ApiException {
    // create and verify one cluster mii domain
    logger.info("Create domain and verify that it's running");
    createAndVerifyDomain(miiImage, domainUid, domainNamespace, "FromModel", 1, true, null, null);

    // create ingress for the domain
    logger.info("Creating ingress for domain {0} in namespace {1}", domainUid, domainNamespace);
    String adminServerPodName = domainUid + "-admin-server";
    String clusterService = domainUid + "-cluster-cluster-1";
    if (!OKD) {
      String ingressClassName = nginxHelmParams.getIngressClassName();
      ingressHost1List
          = createIngressForDomainAndVerify(domainUid, domainNamespace, 0, clusterNameMsPortMap,
          false, ingressClassName, false, 0);
      verifyMonExpAppAccessThroughNginx(ingressHost1List.get(0), 1, nodeportshttp);
      // Need to expose the admin server external service to access the console in OKD cluster only
    } else {
      String hostName = createRouteForOKD(clusterService, domainNamespace);
      logger.info("hostName = {0} ", hostName);
      verifyMonExpAppAccess(1,hostName);
      exporterUrl = String.format("http://%s/wls-exporter/",hostName);
    }
    if (!OKD) {
      installPrometheusGrafana(PROMETHEUS_CHART_VERSION, GRAFANA_CHART_VERSION,
          domainNamespace,
          domainUid);
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
      cleanupPromGrafanaClusterRoles(prometheusReleaseName, grafanaReleaseName);
      String promHelmValuesFileDir = Paths.get(RESULTS_ROOT,this.getClass().getSimpleName(),
          "prometheus" + releaseSuffix).toString();
      promHelmParams = installAndVerifyPrometheus(releaseSuffix,
          monitoringNS,
          promChartVersion,
          prometheusRegexValue, promHelmValuesFileDir);
      assertNotNull(promHelmParams, " Failed to install prometheus");
      prometheusDomainRegexValue = prometheusRegexValue;
      nodeportPrometheus = promHelmParams.getNodePortServer();
      hostPortPrometheus = K8S_NODEPORT_HOST + ":" + nodeportPrometheus;
      if (OKD) {
        hostPortPrometheus = createRouteForOKD("prometheus" + releaseSuffix
            + "-service", monitoringNS) + ":" + nodeportPrometheus;
      }
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
      String grafanaHelmValuesFileDir =  Paths.get(RESULTS_ROOT, this.getClass().getSimpleName(),
          grafanaReleaseName).toString();
      grafanaHelmParams = installAndVerifyGrafana(grafanaReleaseName,
          monitoringNS,
          grafanaHelmValuesFileDir,
          grafanaChartVersion);
      assertNotNull(grafanaHelmParams, "Grafana failed to install");
      String hostPortGrafana = K8S_NODEPORT_HOST + ":" + grafanaHelmParams.getNodePort();
      if (OKD) {
        hostPortGrafana = createRouteForOKD(grafanaReleaseName, monitoringNS) + ":" + grafanaHelmParams.getNodePort();
      }
    }
    logger.info("Grafana is running");
  }


  @AfterAll
  public void tearDownAll() {

    // uninstall NGINX release
    logger.info("Uninstalling NGINX");
    if (nginxHelmParams != null) {
      assertThat(uninstallNginx(nginxHelmParams.getHelmParams()))
          .as("Test uninstallNginx1 returns true")
          .withFailMessage("uninstallNginx() did not return true")
          .isTrue();
    }
    // delete mii domain images created
    if (miiImage != null) {
      deleteImage(miiImage);
    }
    if (!OKD) {
      uninstallPrometheusGrafana(promHelmParams.getHelmParams(), grafanaHelmParams);

      deletePersistentVolumeClaim("pvc-alertmanager" + releaseSuffix, monitoringNS);
      deletePersistentVolume("pv-testalertmanager" + releaseSuffix);
      deletePersistentVolumeClaim("pvc-" + prometheusReleaseName, monitoringNS);
      deletePersistentVolume("pv-test" + prometheusReleaseName);
      deletePersistentVolumeClaim("pvc-" + grafanaReleaseName, monitoringNS);
      deletePersistentVolume("pv-test" + grafanaReleaseName);
    }
    deleteNamespace(monitoringNS);
    deleteMonitoringExporterTempDir(monitoringExporterDir);
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
    assertTrue((page1.asNormalizedText()).contains("Oracle WebLogic Monitoring Exporter"));

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
      assertFalse((page2.asNormalizedText()).contains("Error 500--Internal Server Error"),
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
  private void replaceConfiguration(String configFile, String checkMetricsPrometheusString,
                                    String checkConfig, String expectedValue) throws Exception {
    HtmlPage page = submitConfigureForm(exporterUrl, "replace", configFile);
    assertNotNull(page, "Failed to replace configuration");

    assertTrue(page.asNormalizedText().contains(checkConfig),
        "Page does not contain expected configuration" + checkConfig);

    if (!OKD) {
      //needs 20 secs to fetch the metrics to prometheus
      Thread.sleep(20 * 1000);
      // "heap_free_current{name="managed-server1"}[15s]" search for results for last 15secs
      checkMetricsViaPrometheus(checkMetricsPrometheusString,
          expectedValue, hostPortPrometheus);
    }
  }

  private void replaceConfigurationWithFilter(String configurationFile,
                                              List<String> checkIncluded, List<String> checkExcluded) throws Exception {
    HtmlPage page = submitConfigureForm(exporterUrl, "replace", configurationFile);
    assertNotNull(page, "Failed to replace configuration");
    logger.info("Current Configuration " + page);
    assertTrue(page.asNormalizedText().contains("KeyValues"),
        "Page does not contain expected filtering KeyValues configuration" + page.asNormalizedText());
    if (!OKD) {
      verifyMetrics(checkIncluded, checkExcluded);
    }

  }

  private static void verifyMetrics(List<String> checkIncluded, List<String> checkExcluded) {
    for (String includedString : checkIncluded) {
      assertTrue(verifyMonExpAppAccess("wls-exporter/metrics",
              includedString,
              domain1Uid,
              domain1Namespace,
              false, cluster1Name),
          "monitoring exporter metrics can't filter to included " + includedString);
    }
    for (String excludedString : checkExcluded) {
      assertFalse(verifyMonExpAppAccess("wls-exporter/metrics",
              excludedString,
              domain1Uid,
              domain1Namespace,
              false, cluster1Name),
          "monitoring exporter metrics can't filter to excluded " + excludedString);
    }
  }

  /**
   * Add additional monitoring exporter configuration and verify it was applied.
   *
   * @throws Exception if test fails
   */
  private void appendConfiguration(String configFile) throws Exception {

    // run append
    HtmlPage page = submitConfigureForm(exporterUrl, "append", configFile);
    assertTrue(page.asNormalizedText().contains("Unable to Update Configuration"),
        "Page does not contain expected Unable to Update Configuration");
  }
}

