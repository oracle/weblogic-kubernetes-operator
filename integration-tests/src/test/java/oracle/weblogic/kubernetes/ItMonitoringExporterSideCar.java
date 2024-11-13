// Copyright (c) 2021, 2024, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.weblogic.kubernetes;

import java.io.IOException;
import java.net.InetAddress;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import io.kubernetes.client.openapi.ApiException;
import oracle.weblogic.domain.DomainResource;
import oracle.weblogic.domain.MonitoringExporterConfiguration;
import oracle.weblogic.domain.MonitoringExporterSpecification;
import oracle.weblogic.kubernetes.actions.TestActions;
import oracle.weblogic.kubernetes.actions.impl.GrafanaParams;
import oracle.weblogic.kubernetes.actions.impl.PrometheusParams;
import oracle.weblogic.kubernetes.actions.impl.TraefikParams;
import oracle.weblogic.kubernetes.actions.impl.primitive.HelmParams;
import oracle.weblogic.kubernetes.annotations.IntegrationTest;
import oracle.weblogic.kubernetes.annotations.Namespaces;
import oracle.weblogic.kubernetes.logging.LoggingFacade;
import oracle.weblogic.kubernetes.utils.ExecCommand;
import oracle.weblogic.kubernetes.utils.LoggingUtil;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIfEnvironmentVariable;

import static oracle.weblogic.kubernetes.TestConstants.DOMAIN_STATUS_CONDITION_FAILED_TYPE;
import static oracle.weblogic.kubernetes.TestConstants.GRAFANA_CHART_VERSION;
import static oracle.weblogic.kubernetes.TestConstants.IT_MONITORINGEXPORTERSIDECAR_ALERT_HTTP_NODEPORT;
import static oracle.weblogic.kubernetes.TestConstants.IT_MONITORINGEXPORTERSIDECAR_PROMETHEUS_HTTP_HOSTPORT;
import static oracle.weblogic.kubernetes.TestConstants.IT_MONITORINGEXPORTERSIDECAR_PROMETHEUS_HTTP_NODEPORT;
import static oracle.weblogic.kubernetes.TestConstants.K8S_NODEPORT_HOST;
import static oracle.weblogic.kubernetes.TestConstants.KIND_CLUSTER;
import static oracle.weblogic.kubernetes.TestConstants.KUBERNETES_CLI;
import static oracle.weblogic.kubernetes.TestConstants.OKD;
import static oracle.weblogic.kubernetes.TestConstants.OKE_CLUSTER_PRIVATEIP;
import static oracle.weblogic.kubernetes.TestConstants.PROMETHEUS_CHART_VERSION;
import static oracle.weblogic.kubernetes.TestConstants.RESULTS_ROOT;
import static oracle.weblogic.kubernetes.TestConstants.TEST_IMAGES_REPO_SECRET_NAME;
import static oracle.weblogic.kubernetes.TestConstants.WLSIMG_BUILDER;
import static oracle.weblogic.kubernetes.TestConstants.WLSIMG_BUILDER_DEFAULT;
import static oracle.weblogic.kubernetes.actions.ActionConstants.MODEL_DIR;
import static oracle.weblogic.kubernetes.actions.ActionConstants.RESOURCE_DIR;
import static oracle.weblogic.kubernetes.actions.TestActions.deletePersistentVolume;
import static oracle.weblogic.kubernetes.actions.TestActions.deletePersistentVolumeClaim;
import static oracle.weblogic.kubernetes.actions.TestActions.shutdownDomain;
import static oracle.weblogic.kubernetes.actions.TestActions.uninstallTraefik;
import static oracle.weblogic.kubernetes.actions.impl.primitive.Kubernetes.deleteNamespace;
import static oracle.weblogic.kubernetes.actions.impl.primitive.Kubernetes.getDomainCustomResource;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.checkPodReadyAndServiceExists;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.createIngressPathRouting;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.formatIPv6Host;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.generateNewModelFileWithUpdatedDomainUid;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.getImageBuilderExtraArgs;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.getServiceExtIPAddrtOke;
import static oracle.weblogic.kubernetes.utils.DomainUtils.checkDomainStatusConditionTypeExists;
import static oracle.weblogic.kubernetes.utils.DomainUtils.checkDomainStatusConditionTypeHasExpectedStatus;
import static oracle.weblogic.kubernetes.utils.ImageUtils.createMiiImageAndVerify;
import static oracle.weblogic.kubernetes.utils.ImageUtils.imageRepoLoginAndPushImageToRegistry;
import static oracle.weblogic.kubernetes.utils.LoadBalancerUtils.installAndVerifyTraefik;
import static oracle.weblogic.kubernetes.utils.MonitoringUtils.buildMonitoringExporterCreateImageAndPushToRepo;
import static oracle.weblogic.kubernetes.utils.MonitoringUtils.checkMetricsViaPrometheus;
import static oracle.weblogic.kubernetes.utils.MonitoringUtils.cleanupPromGrafanaClusterRoles;
import static oracle.weblogic.kubernetes.utils.MonitoringUtils.createAndVerifyDomain;
import static oracle.weblogic.kubernetes.utils.MonitoringUtils.deleteMonitoringExporterTempDir;
import static oracle.weblogic.kubernetes.utils.MonitoringUtils.editPrometheusCM;
import static oracle.weblogic.kubernetes.utils.MonitoringUtils.installAndVerifyGrafana;
import static oracle.weblogic.kubernetes.utils.MonitoringUtils.installAndVerifyPrometheus;
import static oracle.weblogic.kubernetes.utils.MonitoringUtils.installMonitoringExporter;
import static oracle.weblogic.kubernetes.utils.MonitoringUtils.uninstallPrometheusGrafana;
import static oracle.weblogic.kubernetes.utils.MonitoringUtils.verifyMonExpAppAccessSideCar;
import static oracle.weblogic.kubernetes.utils.OKDUtils.createRouteForOKD;
import static oracle.weblogic.kubernetes.utils.OperatorUtils.installAndVerifyOperator;
import static oracle.weblogic.kubernetes.utils.PatchDomainUtils.patchDomainResource;
import static oracle.weblogic.kubernetes.utils.PersistentVolumeUtils.createPvAndPvc;
import static oracle.weblogic.kubernetes.utils.PodUtils.verifyIntrospectorPodLogContainsExpectedErrorMsg;
import static oracle.weblogic.kubernetes.utils.SessionMigrationUtil.getOrigModelFile;
import static oracle.weblogic.kubernetes.utils.ThreadSafeLogger.getLogger;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verify Prometheus, Grafana, Webhook, Coordinator are installed and running
 * Verify the monitoring exporter installed in model in image domain can generate the WebLogic metrics.
 * Verify WebLogic metrics can be accessed via Traefik ingress controller.
 * Verify WebLogic metrics can be accessed via Prometheus
 */
@DisplayName("Verify WebLogic Metric is processed as expected by "
    + "MonitoringExporter Side Car via Prometheus and Grafana")
@IntegrationTest
@Tag("olcne-mrg")
@Tag("oke-weekly-sequential")
@Tag("kind-parallel")
@Tag("okd-wls-mrg")
class ItMonitoringExporterSideCar {

  // domain constants

  private static String domain1Namespace = null;
  private static String domain2Namespace = null;
  private static String domain3Namespace = null;
  private static String domain4Namespace = null;
  private static String traefikNamespace = null;

  private static String domain1Uid = "monexp-domain-1";
  private static String domain2Uid = "monexp-domain-2";
  private static String domain3Uid = "monexp-domain-3";
  private static String domain4Uid = "monexp-domain-4";
  private static HelmParams traefikHelmParams = null;

  private static String monitoringNS = null;
  PrometheusParams promHelmParams = null;
  GrafanaParams grafanaHelmParams = null;
  private static String ingressClassName;
  private static String monitoringExporterEndToEndDir = null;
  private static String monitoringExporterSrcDir = null;

  // constants for creating domain image using model in image
  private static final String MONEXP_IMAGE_NAME = "monexp-image";
  private static final String SESSMIGR_APP_NAME = "sessmigr-app";

  private static String cluster1Name = "cluster-1";
  private static String cluster2Name = "cluster-2";
  private static String exporterImage = null;
  private static int managedServerPort = 8001;
  private static int nodeportPrometheus;
  private static String prometheusDomainRegexValue = null;
  private static Map<String, Integer> clusterNameMsPortMap;
  private static LoggingFacade logger = null;
  private static List<String> clusterNames = new ArrayList<>();
  private static String releaseSuffix = "test1";
  private static String prometheusReleaseName = "prometheus" + releaseSuffix;
  private static String grafanaReleaseName = "grafana" + releaseSuffix;
  private static String monitoringExporterDir;
  private static String hostPortPrometheus;
  private static String ingressIP = null;


  /**
   * Install operator . Create model in image domain with multiple clusters.
   * Create ingress for the domain.
   *
   * @param namespaces list of namespaces created by the IntegrationTestWatcher by the
   *                   JUnit engine parameter resolution mechanism
   */
  @BeforeAll

  public static void initAll(@Namespaces(7) List<String> namespaces) {

    logger = getLogger();

    monitoringExporterDir = Paths.get(RESULTS_ROOT,
        "ItMonitoringExporterSideCar", "monitoringexp").toString();
    monitoringExporterSrcDir = Paths.get(monitoringExporterDir, "srcdir").toString();
    monitoringExporterEndToEndDir = Paths.get(monitoringExporterSrcDir, "samples", "kubernetes", "end2end").toString();

    logger.info("Get a unique namespace for operator");
    assertNotNull(namespaces.get(0), "Namespace list is null");
    final String opNamespace = namespaces.get(0);

    logger.info("Get a unique namespace for monitoring");
    assertNotNull(namespaces.get(1), "Namespace list is null");
    monitoringNS = namespaces.get(1);

    logger.info("Get a unique namespace for domain1");
    assertNotNull(namespaces.get(2), "Namespace list is null");
    domain1Namespace = namespaces.get(2);

    logger.info("Get a unique namespace for domain2");
    assertNotNull(namespaces.get(3), "Namespace list is null");
    domain2Namespace = namespaces.get(3);

    logger.info("Get a unique namespace for domain3");
    assertNotNull(namespaces.get(4), "Namespace list is null");
    domain3Namespace = namespaces.get(4);

    logger.info("Get a unique namespace for domain4");
    assertNotNull(namespaces.get(5), "Namespace list is null");
    domain4Namespace = namespaces.get(5);
    logger.info("Get a unique namespace for traefik");
    assertNotNull(namespaces.get(6), "Namespace list is null");
    traefikNamespace = namespaces.get(6);

    logger.info("install and verify operator");
    installAndVerifyOperator(opNamespace,
        domain1Namespace, domain2Namespace, domain3Namespace, domain4Namespace);

    logger.info("install monitoring exporter");
    installMonitoringExporter(monitoringExporterDir);
    exporterImage = assertDoesNotThrow(() ->
            buildMonitoringExporterCreateImageAndPushToRepo(monitoringExporterSrcDir, "exporter",
        domain1Namespace, TEST_IMAGES_REPO_SECRET_NAME, getImageBuilderExtraArgs()),
        "Failed to create image for exporter");

    clusterNameMsPortMap = new HashMap<>();
    clusterNameMsPortMap.put(cluster1Name, managedServerPort);
    clusterNameMsPortMap.put(cluster2Name, managedServerPort);
    clusterNames.add(cluster1Name);
    clusterNames.add(cluster2Name);

    logger.info("create pv and pvc for monitoring");
    HashMap<String, String> labels = new HashMap<>();
    labels.put("app", "monitoring");
    labels.put("weblogic.domainUid", "test");
    if (!OKD) {
      String className = ItMonitoringExporterSideCar.class.getSimpleName();
      assertDoesNotThrow(() -> createPvAndPvc(prometheusReleaseName, monitoringNS, labels, className));
      assertDoesNotThrow(() -> createPvAndPvc("alertmanager" + releaseSuffix, monitoringNS, labels, className));
      assertDoesNotThrow(() -> createPvAndPvc(grafanaReleaseName, monitoringNS, labels, className));
    }
    cleanupPromGrafanaClusterRoles(prometheusReleaseName, grafanaReleaseName);

    if (OKE_CLUSTER_PRIVATEIP) {
      // install Traefik ingress controller for all test cases using Traefik
      installTraefikIngressController();

      String ingressServiceName = traefikHelmParams.getReleaseName();
      ingressIP = getServiceExtIPAddrtOke(ingressServiceName, traefikNamespace) != null
          ? getServiceExtIPAddrtOke(ingressServiceName, traefikNamespace) : K8S_NODEPORT_HOST;
      hostPortPrometheus = ingressIP;
    }
  }

  /**
   * Test covers basic functionality for MonitoringExporter SideCar .
   * Create Prometheus, Grafana.
   * Create Model in Image with monitoring exporter.
   * Check generated monitoring exporter WebLogic metrics via Prometheus, Grafana.
   * Check basic functionality of monitoring exporter.
   */
  @Test
  @DisplayName("Test Basic Functionality of Monitoring Exporter SideCar.")
  void testSideCarBasicFunctionality() throws Exception {
    try {
      // create and verify one cluster mii domain
      logger.info("Create domain and verify that it's running");
      String modelFile = generateNewModelFileWithUpdatedDomainUid(domain3Uid,
          "ItMonitoringExporterSideCar", getOrigModelFile());
      String miiImage1 = createAndVerifyMiiImage(modelFile);
      String yaml = RESOURCE_DIR + "/exporter/rest_webapp.yaml";
      createAndVerifyDomain(miiImage1, domain3Uid, domain3Namespace, "FromModel", 2, false, yaml, exporterImage);
      if (!OKD) {
        installPrometheusGrafana(PROMETHEUS_CHART_VERSION, GRAFANA_CHART_VERSION,
            domain3Namespace,
            domain3Uid);

        String sessionAppPrometheusSearchKey =
            "wls_servlet_invocation_total_count%7Bapp%3D%22myear%22%7D%5B15s%5D";
        checkMetricsViaPrometheus(sessionAppPrometheusSearchKey, "sessmigr", hostPortPrometheus,
            prometheusReleaseName + "." + monitoringNS);
      }
      DomainResource domain = getDomainCustomResource(domain3Uid, domain3Namespace);
      String monexpConfig = domain.getSpec().getMonitoringExporter().toString();
      logger.info("MonitorinExporter new Configuration from crd " + monexpConfig);
      assertTrue(monexpConfig.contains("openSessionsHighCount"));
      assertTrue(verifyMonExpAppAccessSideCar("webapp_config_open_sessions_high_count",
          domain3Namespace, domain3Uid + "-managed-server1"));
      assertTrue(verifyMonExpAppAccessSideCar("webapp_config_open_sessions_high_count",
          domain3Namespace, domain3Uid + "-managed-server2"));
      logger.info("Testing replace configuration");
      changeMonitoringExporterSideCarConfig(RESOURCE_DIR + "/exporter/rest_jvm.yaml", domain3Uid, domain3Namespace,
          "heapFreeCurrent", "heap_free_current", "managed-server1");

      assertTrue(verifyMonExpAppAccessSideCar("heap_free_current", domain3Namespace, domain3Uid + "-managed-server1"));
      assertTrue(verifyMonExpAppAccessSideCar("heap_free_current", domain3Namespace, domain3Uid + "-managed-server2"));

      logger.info("replace monitoring exporter configuration with configuration file with domainQualifier=true.");
      changeMonitoringExporterSideCarConfig(RESOURCE_DIR + "/exporter/rest_domainqualtrue.yaml",
          domain3Uid, domain3Namespace,
          "domainQualifier", "wls_servlet_executionTimeAverage%7Bapp%3D%22myear%22%7D%5B15s%5D",
          "\"domain\":\"" + domain3Uid + "\"");

      logger.info("replace monitoring exporter configuration with configuration file with metricsNameSnakeCase=false.");
      changeMonitoringExporterSideCarConfig(RESOURCE_DIR + "/exporter/rest_snakecasefalse.yaml",
          domain3Uid, domain3Namespace,
          "metricsNameSnakeCase", "wls_servlet_executionTimeAverage%7Bapp%3D%22myear%22%7D%5B15s%5D",
          "sessmigr");
      assertTrue(verifyMonExpAppAccessSideCar("executionTimeAverage", domain3Namespace,
          domain3Uid + "-managed-server1"));
      assertTrue(verifyMonExpAppAccessSideCar("executionTimeAverage", domain3Namespace,
          domain3Uid + "-managed-server2"));

    } finally {
      shutdownDomain(domain3Uid, domain3Namespace);
    }

  }

  /**
   * Test Negative test to check error message in case
   * if restfull management services are disabled.
   * Create Model in Image with monitoring exporter and restfull services disabled.
   * Check that introspector job fails with expected error message
   * if domain crd contains exporter config with restfull services disabled
   */
  //@Test - test disabled until OWLS-111639 will be implemented
  @DisplayName("Negative test to check error message in case if restfull"
      + " services in the domain are disabled.")
  void testSideCarRESTfullServicesDisabled() throws Exception {
    boolean testPassed = false;
    try {
      // create and verify one cluster mii domain
      logger.info("Create domain and verify that it's running");
      String modelFile = generateNewModelFileWithUpdatedDomainUid(domain4Uid,
          "ItMonitoringExporterSideCar", "model.sessmigr.restdisabled.yaml");
      String miiImage1 = createAndVerifyMiiImage(modelFile);
      String yaml = RESOURCE_DIR + "/exporter/rest_webapp.yaml";

      createAndVerifyDomain(miiImage1, domain4Uid, domain4Namespace,
          "FromModel", 2, false, yaml, exporterImage, false);
      // verify the condition type Failed exists
      checkDomainStatusConditionTypeExists(domain4Uid, domain4Namespace, DOMAIN_STATUS_CONDITION_FAILED_TYPE);
      // verify the condition Failed type has expected status
      checkDomainStatusConditionTypeHasExpectedStatus(domain4Uid, domain4Namespace,
          DOMAIN_STATUS_CONDITION_FAILED_TYPE, "True");
      String errorMessage =
          "[SEVERE] exporter config is specified and the topology has the REST port disabled ";
      verifyIntrospectorPodLogContainsExpectedErrorMsg(domain4Uid, domain4Namespace, errorMessage);
      testPassed = true;
    } finally {
      if (!testPassed) {
        List<String> ns = new ArrayList<>();
        ns.add(domain4Namespace);
        LoggingUtil.generateLog(this, ns);
        shutdownDomain(domain4Uid, domain4Namespace);
      }
    }
  }

  private void changeMonitoringExporterSideCarConfig(String configYamlFile, String domainUid,
                                                     String domainNamespace,
                                                     String configSearchKey,
                                                     String promSearchString, String expectedVal) throws Exception {
    String contents = null;
    try {
      contents = new String(Files.readAllBytes(Paths.get(configYamlFile)));
    } catch (IOException e) {
      e.printStackTrace();
    }

    MonitoringExporterSpecification monexpSpec = new MonitoringExporterSpecification().configuration(contents);
    MonitoringExporterConfiguration monexpCong = monexpSpec.configuration();

    StringBuffer patchStr = null;
    patchStr = new StringBuffer("[{");
    patchStr.append("\"op\": \"replace\",")
        .append(" \"path\": \"/spec/monitoringExporter/configuration\",")
        .append("\"value\": ")
        .append(monexpCong.asJsonString())
        .append("}]");
    logger.info("PatchStr for change Monitoring Exporter Configuration : {0}", patchStr.toString());

    boolean cmPatched = patchDomainResource(domainUid, domainNamespace, patchStr);
    assertTrue(cmPatched, "patchDomainCustomResource(changeMonExporter) failed");

    DomainResource domain = assertDoesNotThrow(() -> TestActions.getDomainCustomResource(domainUid, domainNamespace),
        String.format("getDomainCustomResource failed with ApiException when tried to get domain %s in namespace %s",
            domainUid, domainNamespace));
    assertNotNull(domain, "Got null domain resource after patching");
    String monexpConfig = domain.getSpec().getMonitoringExporter().toString();
    logger.info("MonitorinExporter new Configuration from crd " + monexpConfig);
    assertTrue(monexpConfig.contains(configSearchKey));

    logger.info(domain.getSpec().getMonitoringExporter().toString());
    Thread.sleep(20 * 1000);
    String managedServerPodName = domainUid + "-managed-server";
    // check that the managed server pod exists
    logger.info("Checking that managed server pod {0} exists and ready in namespace {1}",
        managedServerPodName, domainNamespace);
    checkPodReadyAndServiceExists(managedServerPodName + "1", domainUid, domainNamespace);
    checkPodReadyAndServiceExists(managedServerPodName + "2", domainUid, domainNamespace);
    if (!OKD) {
      checkMetricsViaPrometheus(promSearchString, expectedVal, hostPortPrometheus,
          prometheusReleaseName + "." + monitoringNS);
    }
  }

  /**
   * Test covers basic functionality for MonitoringExporter SideCar for domain with two clusters.
   * Create Prometheus, Grafana.
   * Create Model in Image with monitoring exporter.
   * Check generated monitoring exporter WebLogic metrics via Prometheus, Grafana.
   * Check basic functionality of monitoring exporter.
   */
  @Test
  @DisabledIfEnvironmentVariable(named = "OKD", matches = "true")
  @DisplayName("Test Basic Functionality of Monitoring Exporter SideCar for domain with two clusters.")
  void testSideCarBasicFunctionalityTwoClusters() throws Exception {
    try {
      // create and verify one cluster mii domain
      logger.info("Create domain and verify that it's running");
      String miiImage1 = createAndVerifyMiiImage(MODEL_DIR + "/model.sessmigr.2clusters.yaml");
      String yaml = RESOURCE_DIR + "/exporter/rest_jvm.yaml";
      createAndVerifyDomain(miiImage1, domain1Uid, domain1Namespace, "FromModel", 2, true, yaml, exporterImage);
      installPrometheusGrafana(PROMETHEUS_CHART_VERSION, GRAFANA_CHART_VERSION,
          domain1Namespace,
          domain1Uid);

      // "heap_free_current{name="managed-server1"}[15s]" search for results for last 15secs
      checkMetricsViaPrometheus("heap_free_current%7Bname%3D%22" + cluster1Name + "-managed-server1%22%7D%5B15s%5D",
          cluster1Name + "-managed-server1",hostPortPrometheus,
          prometheusReleaseName + "." + monitoringNS);
      checkMetricsViaPrometheus("heap_free_current%7Bname%3D%22" + cluster2Name + "-managed-server2%22%7D%5B15s%5D",
          cluster2Name + "-managed-server2",hostPortPrometheus,
          prometheusReleaseName + "." + monitoringNS);
    } finally {
      shutdownDomain(domain1Uid, domain1Namespace);
    }
  }

  /**
   * Test covers basic functionality for MonitoringExporter SideCar .
   * Create Prometheus, Grafana.
   * Create Model in Image with SSL enabled.
   * Check generated monitoring exporter WebLogic metrics via Prometheus, Grafana.
   * Check basic functionality of monitoring exporter.
   */

  @Test
  @DisplayName("Test Basic Functionality of Monitoring Exporter SideCar with ssl enabled.")
  void testSideCarBasicFunctionalityWithSSL() throws Exception {
    try {
      // create and verify one cluster mii domain
      logger.info("Create domain and verify that it's running");
      String yaml = RESOURCE_DIR + "/exporter/rest_webapp.yaml";
      String  miiImage1 = createAndVerifyMiiImage(MODEL_DIR + "/model.ssl.yaml");
      createAndVerifyDomain(miiImage1, domain2Uid, domain2Namespace, "FromModel",
          2, false, yaml, exporterImage);
      if (!OKD) {
        installPrometheusGrafana(PROMETHEUS_CHART_VERSION, GRAFANA_CHART_VERSION,
            domain2Namespace,
            domain2Uid);

        String sessionAppPrometheusSearchKey =
            "wls_servlet_invocation_total_count%7Bapp%3D%22myear%22%7D%5B15s%5D";
        checkMetricsViaPrometheus(sessionAppPrometheusSearchKey, "sessmigr", hostPortPrometheus,
            prometheusReleaseName + "." + monitoringNS);
      }

      DomainResource domain = getDomainCustomResource(domain2Uid,domain2Namespace);
      String monexpConfig = domain.getSpec().getMonitoringExporter().toString();
      logger.info("MonitorinExporter new Configuration from crd " + monexpConfig);
      assertTrue(monexpConfig.contains("openSessionsHighCount"));
      assertTrue(verifyMonExpAppAccessSideCar("webapp_config_open_sessions_high_count",
          domain2Namespace, domain2Uid + "-managed-server1"));
      assertTrue(verifyMonExpAppAccessSideCar("webapp_config_open_sessions_high_count",
          domain2Namespace, domain2Uid + "-managed-server2"));
    } finally {
      shutdownDomain(domain2Uid, domain2Namespace);
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
      String promHelmValuesFileDir = Paths.get(RESULTS_ROOT, this.getClass().getSimpleName(),
              "prometheus" + releaseSuffix).toString();
      if (KIND_CLUSTER && !WLSIMG_BUILDER.equals(WLSIMG_BUILDER_DEFAULT)) {
        promHelmParams = installAndVerifyPrometheus(releaseSuffix,
            monitoringNS,
            promChartVersion,
            prometheusRegexValue, promHelmValuesFileDir, null,
            IT_MONITORINGEXPORTERSIDECAR_PROMETHEUS_HTTP_NODEPORT, IT_MONITORINGEXPORTERSIDECAR_ALERT_HTTP_NODEPORT);
      } else {
        promHelmParams = installAndVerifyPrometheus(releaseSuffix,
            monitoringNS,
            promChartVersion,
            prometheusRegexValue, promHelmValuesFileDir);
      }
      assertNotNull(promHelmParams, " Failed to install prometheus");
      String command1 = KUBERNETES_CLI + " get svc -n " + monitoringNS;
      assertDoesNotThrow(() -> ExecCommand.exec(command1,true));
      String command2 = KUBERNETES_CLI + " describe svc -n " + monitoringNS;
      assertDoesNotThrow(() -> ExecCommand.exec(command2, true));

      createIngressPathRouting(monitoringNS, "/api",
            prometheusReleaseName + "-server", 80, ingressClassName, prometheusReleaseName
              + "." + monitoringNS);

      if (!OKE_CLUSTER_PRIVATEIP) {
        nodeportPrometheus = promHelmParams.getNodePortServer();
        String host = formatIPv6Host(K8S_NODEPORT_HOST);
        if (KIND_CLUSTER && !WLSIMG_BUILDER.equals(WLSIMG_BUILDER_DEFAULT)) {
          host = formatIPv6Host(InetAddress.getLocalHost().getHostAddress());
          nodeportPrometheus = IT_MONITORINGEXPORTERSIDECAR_PROMETHEUS_HTTP_HOSTPORT;
          logger.info("Running in podman Debug 1 : {0}", hostPortPrometheus);
        }
        hostPortPrometheus = host + ":" + nodeportPrometheus;
        logger.info("Running in podman Debug 2 : {0}", hostPortPrometheus);
      }
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
    String command1 = KUBERNETES_CLI + " get svc -n " + monitoringNS;
    assertDoesNotThrow(() -> ExecCommand.exec(command1,true));
    String command2 = KUBERNETES_CLI + " describe svc -n " + monitoringNS;
    assertDoesNotThrow(() -> ExecCommand.exec(command2,true));
    logger.info("Running in podman Debug 3 : {0}", hostPortPrometheus);

    if (OKD) {
      hostPortPrometheus = createRouteForOKD("prometheus" + releaseSuffix
          + "-server", monitoringNS) + ":" + nodeportPrometheus;
    }
    if (grafanaHelmParams == null) {
      String grafanaHelmValuesFileDir = Paths.get(RESULTS_ROOT, this.getClass().getSimpleName(),
              grafanaReleaseName).toString();
      grafanaHelmParams = installAndVerifyGrafana(grafanaReleaseName,
              monitoringNS,
              grafanaHelmValuesFileDir,
              grafanaChartVersion);
      logger.info("Running in podman Debug 4 : {0}", hostPortPrometheus);
      assertNotNull(grafanaHelmParams, "Grafana failed to install");
      String host = formatIPv6Host(K8S_NODEPORT_HOST);
      logger.info("Running in podman Debug 5 : {0}", hostPortPrometheus);

      String hostPortGrafana = host + ":" + grafanaHelmParams.getNodePort();
      if (OKE_CLUSTER_PRIVATEIP) {
        hostPortGrafana = "http://" + ingressIP + "/" + "grafana";
      }
      if (OKD) {
        hostPortGrafana = createRouteForOKD(grafanaReleaseName, monitoringNS) + ":" + grafanaHelmParams.getNodePort();
      }
    }
    logger.info("Running in podman Debug 6 : {0}", hostPortPrometheus);
    logger.info("Grafana is running");
  }


  @AfterAll
  public void tearDownAll() {

    logger.info("Uninstalling Traefik");
    if (traefikHelmParams != null) {

      assertThat(uninstallTraefik(traefikHelmParams))
          .as("Test uninstallTraefik returns true")
          .withFailMessage("uninstallTraefik did not return true")
          .isTrue();
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

  /**
   * Create mii image with SESSMIGR application.
   */
  private static String createAndVerifyMiiImage(String modelFile) {
    // create image with model files
    logger.info("Create image with model file and verify");

    List<String> appList = new ArrayList<>();
    appList.add(SESSMIGR_APP_NAME);

    // build the model file list
    final List<String> modelList = Collections.singletonList(modelFile);
    String myImage =
        createMiiImageAndVerify(MONEXP_IMAGE_NAME, modelList, appList);

    // repo login and push image to registry if necessary
    imageRepoLoginAndPushImageToRegistry(myImage);

    return myImage;
  }

  private static void installTraefikIngressController() {
    // install and verify Traefik
    logger.info("Installing Traefik controller using helm");
    TraefikParams traefikParams = installAndVerifyTraefik(traefikNamespace, 0, 0);
    traefikHelmParams = traefikParams.getHelmParams();
    ingressClassName = traefikParams.getIngressClassName();
  }
}
