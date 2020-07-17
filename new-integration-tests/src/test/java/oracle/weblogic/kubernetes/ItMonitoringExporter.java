// Copyright (c) 2020, Oracle Corporation and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.weblogic.kubernetes;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.concurrent.Callable;
import java.util.stream.Collectors;

import com.gargoylesoftware.htmlunit.FailingHttpStatusCodeException;
import com.gargoylesoftware.htmlunit.WebClient;
import com.gargoylesoftware.htmlunit.html.HtmlFileInput;
import com.gargoylesoftware.htmlunit.html.HtmlForm;
import com.gargoylesoftware.htmlunit.html.HtmlPage;
import com.gargoylesoftware.htmlunit.html.HtmlRadioButtonInput;
import com.gargoylesoftware.htmlunit.html.HtmlSubmitInput;
import io.kubernetes.client.custom.IntOrString;
import io.kubernetes.client.custom.Quantity;
import io.kubernetes.client.openapi.ApiException;
import io.kubernetes.client.openapi.models.V1ConfigMap;
import io.kubernetes.client.openapi.models.V1Container;
import io.kubernetes.client.openapi.models.V1ContainerPort;
import io.kubernetes.client.openapi.models.V1Deployment;
import io.kubernetes.client.openapi.models.V1DeploymentSpec;
import io.kubernetes.client.openapi.models.V1DeploymentStrategy;
import io.kubernetes.client.openapi.models.V1EnvVar;
import io.kubernetes.client.openapi.models.V1HostPathVolumeSource;
import io.kubernetes.client.openapi.models.V1LabelSelector;
import io.kubernetes.client.openapi.models.V1LocalObjectReference;
import io.kubernetes.client.openapi.models.V1ObjectMeta;
import io.kubernetes.client.openapi.models.V1PersistentVolume;
import io.kubernetes.client.openapi.models.V1PersistentVolumeClaim;
import io.kubernetes.client.openapi.models.V1PersistentVolumeClaimSpec;
import io.kubernetes.client.openapi.models.V1PersistentVolumeSpec;
import io.kubernetes.client.openapi.models.V1Pod;
import io.kubernetes.client.openapi.models.V1PodCondition;
import io.kubernetes.client.openapi.models.V1PodList;
import io.kubernetes.client.openapi.models.V1PodSpec;
import io.kubernetes.client.openapi.models.V1PodTemplateSpec;
import io.kubernetes.client.openapi.models.V1ResourceRequirements;
import io.kubernetes.client.openapi.models.V1SecretReference;
import io.kubernetes.client.openapi.models.V1Service;
import io.kubernetes.client.openapi.models.V1ServicePort;
import io.kubernetes.client.openapi.models.V1ServiceSpec;
import oracle.weblogic.domain.AdminServer;
import oracle.weblogic.domain.AdminService;
import oracle.weblogic.domain.Channel;
import oracle.weblogic.domain.Cluster;
import oracle.weblogic.domain.Configuration;
import oracle.weblogic.domain.Domain;
import oracle.weblogic.domain.DomainSpec;
import oracle.weblogic.domain.Model;
import oracle.weblogic.domain.ServerPod;
import oracle.weblogic.kubernetes.actions.impl.Grafana;
import oracle.weblogic.kubernetes.actions.impl.Namespace;
import oracle.weblogic.kubernetes.actions.impl.Prometheus;
import oracle.weblogic.kubernetes.actions.impl.primitive.Command;
import oracle.weblogic.kubernetes.actions.impl.primitive.CommandParams;
import oracle.weblogic.kubernetes.actions.impl.primitive.Docker;
import oracle.weblogic.kubernetes.actions.impl.primitive.HelmParams;
import oracle.weblogic.kubernetes.actions.impl.primitive.Kubernetes;
import oracle.weblogic.kubernetes.annotations.IntegrationTest;
import oracle.weblogic.kubernetes.annotations.Namespaces;
import oracle.weblogic.kubernetes.assertions.impl.Deployment;
import oracle.weblogic.kubernetes.logging.LoggingFacade;
import oracle.weblogic.kubernetes.utils.ExecCommand;
import oracle.weblogic.kubernetes.utils.TestUtils;
import org.apache.commons.io.FileUtils;
import org.awaitility.core.ConditionFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static java.nio.file.Files.createDirectories;
import static java.nio.file.Paths.get;
import static java.util.concurrent.TimeUnit.MINUTES;
import static java.util.concurrent.TimeUnit.SECONDS;
import static oracle.weblogic.kubernetes.TestConstants.ADMIN_PASSWORD_DEFAULT;
import static oracle.weblogic.kubernetes.TestConstants.ADMIN_USERNAME_DEFAULT;
import static oracle.weblogic.kubernetes.TestConstants.DOMAIN_API_VERSION;
import static oracle.weblogic.kubernetes.TestConstants.GRAFANA_CHART_VERSION;
import static oracle.weblogic.kubernetes.TestConstants.K8S_NODEPORT_HOST;
import static oracle.weblogic.kubernetes.TestConstants.MANAGED_SERVER_NAME_BASE;
import static oracle.weblogic.kubernetes.TestConstants.MONITORING_EXPORTER_VERSION;
import static oracle.weblogic.kubernetes.TestConstants.PROMETHEUS_CHART_VERSION;
import static oracle.weblogic.kubernetes.TestConstants.PV_ROOT;
import static oracle.weblogic.kubernetes.TestConstants.REPO_EMAIL;
import static oracle.weblogic.kubernetes.TestConstants.REPO_NAME;
import static oracle.weblogic.kubernetes.TestConstants.REPO_PASSWORD;
import static oracle.weblogic.kubernetes.TestConstants.REPO_REGISTRY;
import static oracle.weblogic.kubernetes.TestConstants.REPO_SECRET_NAME;
import static oracle.weblogic.kubernetes.TestConstants.REPO_USERNAME;
import static oracle.weblogic.kubernetes.TestConstants.RESULTS_ROOT;
import static oracle.weblogic.kubernetes.actions.ActionConstants.ITTESTS_DIR;
import static oracle.weblogic.kubernetes.actions.ActionConstants.MODEL_DIR;
import static oracle.weblogic.kubernetes.actions.ActionConstants.MONITORING_EXPORTER_DOWNLOAD_URL;
import static oracle.weblogic.kubernetes.actions.ActionConstants.RESOURCE_DIR;
import static oracle.weblogic.kubernetes.actions.ActionConstants.WLS;
import static oracle.weblogic.kubernetes.actions.ActionConstants.WLS_BASE_IMAGE_NAME;
import static oracle.weblogic.kubernetes.actions.ActionConstants.WLS_BASE_IMAGE_TAG;
import static oracle.weblogic.kubernetes.actions.TestActions.deleteImage;
import static oracle.weblogic.kubernetes.actions.TestActions.deletePersistentVolume;
import static oracle.weblogic.kubernetes.actions.TestActions.deletePersistentVolumeClaim;
import static oracle.weblogic.kubernetes.actions.TestActions.deleteSecret;
import static oracle.weblogic.kubernetes.actions.TestActions.getServiceNodePort;
import static oracle.weblogic.kubernetes.actions.TestActions.shutdownDomain;
import static oracle.weblogic.kubernetes.actions.TestActions.uninstallNginx;
import static oracle.weblogic.kubernetes.actions.impl.primitive.Kubernetes.createNamespace;
import static oracle.weblogic.kubernetes.actions.impl.primitive.Kubernetes.deleteDomainCustomResource;
import static oracle.weblogic.kubernetes.actions.impl.primitive.Kubernetes.deleteNamespace;
import static oracle.weblogic.kubernetes.assertions.impl.Kubernetes.listPods;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.checkPodExists;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.checkPodReady;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.checkServiceExists;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.createDockerRegistrySecret;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.createDomainAndVerify;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.createImageAndVerify;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.createIngressForDomainAndVerify;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.createMiiImageAndVerify;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.createPVPVCAndVerify;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.createSecretWithUsernamePassword;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.dockerLoginAndPushImageToRegistry;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.installAndVerifyGrafana;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.installAndVerifyNginx;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.installAndVerifyOperator;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.installAndVerifyPrometheus;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.replaceStringInFile;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.scaleAndVerifyCluster;
import static oracle.weblogic.kubernetes.utils.TestUtils.callWebAppAndCheckForServerNameInResponse;
import static oracle.weblogic.kubernetes.utils.TestUtils.getNextFreePort;
import static oracle.weblogic.kubernetes.utils.ThreadSafeLogger.getLogger;
import static org.apache.commons.io.FileUtils.deleteDirectory;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.with;
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
  private static String domain1Namespace = null;
  private static String domain2Namespace = null;
  private static String domain1Uid = "monexp-domain-1";
  private static String domain2Uid = "monexp-domain-2";
  private static HelmParams nginxHelmParams = null;
  private static int nodeportshttp = 0;
  private static List<String> ingressHost1List = null;
  private static List<String> ingressHost2List = null;

  private static String monitoringNS = null;
  private static String webhookNS = null;
  private static ConditionFactory withStandardRetryPolicy = null;
  HelmParams promHelmParams = null;
  HelmParams grafanaHelmParams = null;
  private static String monitoringExporterEndToEndDir = null;
  private static String monitoringExporterSrcDir = null;
  private static String monitoringExporterAppDir = null;
  private static V1Service webhookService = null;
  private static V1Deployment webhookDepl = null;
  private static V1Service coordinatorService = null;
  private static V1Deployment coordinatorDepl = null;
  // constants for creating domain image using model in image
  private static final String MONEXP_MODEL_FILE = "model.monexp.yaml";
  private static final String MONEXP_WDT_FILE = "/demo-domains/domainBuilder/scripts/simple-topology.yaml";
  private static final String MONEXP_IMAGE_NAME = "monexp-image";
  private static final String SESSMIGR_APP_NAME = "sessmigr-app";

  private static String clusterName = "cluster-1";
  private static String miiImage = null;
  private static String wdtImage = null;
  private static String webhookImage = null;
  private static String  coordinatorImage = null;
  private static int managedServerPort = 8001;
  private static int nodeportserver;
  private static String exporterUrl = null;
  private static String prometheusDomainRegexValue = null;
  private static Map<String, Integer> clusterNameMsPortMap;
  private static LoggingFacade logger = null;

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
    // create standard, reusable retry/backoff policy
    withStandardRetryPolicy = with().pollDelay(2, SECONDS)
        .and().with().pollInterval(10, SECONDS)
        .atMost(5, MINUTES).await();

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


    logger.info("install and verify operator");
    installAndVerifyOperator(opNamespace, domain1Namespace,domain2Namespace);

    logger.info("nstall monitoring exporter");
    installMonitoringExporter();

    logger.info("create and verify WebLogic domain image using model in image with model files");
    miiImage = createAndVerifyMiiImage();

    // create and verify one cluster mii domain
    logger.info("Create domain and verify that it's running");
    createAndVerifyDomain(miiImage, domain1Uid, domain1Namespace, "FromModel", 1);


    // install and verify NGINX
    nginxHelmParams = installAndVerifyNginx(nginxNamespace, 0, 0);
    String nginxServiceName = nginxHelmParams.getReleaseName() + "-nginx-ingress-controller";
    logger.info("NGINX service name: {0}", nginxServiceName);
    nodeportshttp = getServiceNodePort(nginxNamespace, nginxServiceName, "http");
    logger.info("NGINX http node port: {0}", nodeportshttp);

    // create ingress for the domain
    logger.info("Creating ingress for domain {0} in namespace {1}", domain1Uid, domain1Namespace);
    clusterNameMsPortMap = new HashMap<>();
    clusterNameMsPortMap.put(clusterName, managedServerPort);
    ingressHost1List =
        createIngressForDomainAndVerify(domain1Uid, domain1Namespace,clusterNameMsPortMap, false);

    exporterUrl = String.format("http://%s:%s/wls-exporter/",K8S_NODEPORT_HOST,nodeportshttp);

    logger.info("create pv and pvc for monitoring");
    HashMap<String, String> labels = new HashMap<>();
    labels.put("app", "monitoring");
    labels.put("weblogic.domainUid", domain1Uid);
    assertDoesNotThrow(() -> createPvAndPvc("prometheus", monitoringNS, labels));
    assertDoesNotThrow(() -> createPvAndPvc("alertmanager",monitoringNS, labels));
    assertDoesNotThrow(() -> createPvAndPvc("grafana", monitoringNS, labels));

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
  public void testEndToEndViaChart() throws Exception {
    wdtImage = createAndVerifyDomainInImage();
    try {
      logger.info("Create wdt domain and verify that it's running");
      createAndVerifyDomain(wdtImage, domain2Uid, domain2Namespace, "Image", replicaCount);
      ingressHost2List =
              createIngressForDomainAndVerify(domain2Uid, domain2Namespace, clusterNameMsPortMap);
      logger.info("Installing Prometheus and Grafana");
      installPrometheusGrafana(PROMETHEUS_CHART_VERSION, GRAFANA_CHART_VERSION,
              domain2Namespace,
              domain2Uid);

      installWebhook();
      installCoordinator(domain2Namespace);

      logger.info("verify access to Monitoring Exporter");
      verifyMonExpAppAccessThroughNginx(ingressHost2List.get(0), managedServersCount);
      logger.info("verify metrics via prometheus");
      String testappPrometheusSearchKey =
              "wls_servlet_invocation_total_count%7Bapp%3D%22test-webapp%22%7D%5B15s%5D";
      checkMetricsViaPrometheus(testappPrometheusSearchKey, "test-webapp");
      logger.info("fire alert by scaling down");
      fireAlert();
      logger.info("switch to monitor another domain");
      String oldRegex = String.format("regex: %s;%s;%s", domain2Namespace, domain2Uid, clusterName);
      String newRegex = String.format("regex: %s;%s;%s", domain1Namespace, domain1Uid, clusterName);
      editPrometheusCM(oldRegex, newRegex);
      String sessionAppPrometheusSearchKey =
              "wls_servlet_invocation_total_count%7Bapp%3D%22myear%22%7D%5B15s%5D";
      checkMetricsViaPrometheus(sessionAppPrometheusSearchKey, "sessmigr");
      checkPromGrafanaLatestVersion();
    } finally {
      logger.info("Shutting down domain2");
      assertTrue(shutdownDomain(domain2Uid, domain2Namespace),
              String.format("shutdown domain %s in namespace %s failed", domain2Uid, domain2Namespace));
    }
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
  public void testBasicFunctionality() throws Exception {

    installPrometheusGrafana(PROMETHEUS_CHART_VERSION, GRAFANA_CHART_VERSION,
            domain1Namespace,
            domain1Uid);

    //verify access to Monitoring Exporter
    verifyMonExpAppAccessThroughNginx(ingressHost1List.get(0),1);

    try {
      logger.info("Testing replace configuration");
      replaceConfiguration();
      logger.info("Testing append configuration");
      appendConfiguration();
      logger.info("Testing replace One Attribute Value AsArray configuration");
      replaceOneAttributeValueAsArrayConfiguration();
      logger.info("Testing append One Attribute Value AsArray configuration");
      appendArrayWithOneExistedAndOneDifferentAttributeValueAsArrayConfiguration();
      logger.info("Testing replace with empty configuration");
      replaceWithEmptyConfiguration();
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
    } finally {
      //restore configuration
      submitConfigureForm(exporterUrl, "replace", RESOURCE_DIR + "/exporter/exporter-config.yaml");
    }
  }

  /**
   * Test covers the following use cases.
   * Create Prometheus, Grafana from latest version of helm chart
   * verify access to monitoring exporter WebLogic metrics via nginx
   * check WebLogic metrics via Prometheus
   */
  private void checkPromGrafanaLatestVersion() throws Exception {
    //uninstall prometheus and grafana if running
    uninstallPrometheusGrafana();
    try {
      installPrometheusGrafana(null, null,
              domain2Namespace,
              domain2Uid);


      //verify access to Monitoring Exporter
      verifyMonExpAppAccessThroughNginx(ingressHost2List.get(0),managedServersCount);
      //verify metrics via prometheus
      String testappPrometheusSearchKey =
              "wls_servlet_invocation_total_count%7Bapp%3D%22test-webapp%22%7D%5B15s%5D";
      checkMetricsViaPrometheus(testappPrometheusSearchKey, "test-webapp");
    } finally {
      uninstallPrometheusGrafana();
    }
  }

  private void fireAlert() throws ApiException {
    // scale domain2
    logger.info("Scaling cluster {0} of domain {1} in namespace {2} to {3} servers.",
            clusterName, domain2Uid, domain2Namespace, 1);
    managedServersCount = 1;
    scaleAndVerifyCluster(clusterName, domain2Uid, domain2Namespace,
            domain2Uid + "-" + MANAGED_SERVER_NAME_BASE, replicaCount, managedServersCount,
            null, null);

    //check webhook log for firing alert
    List<V1Pod> pods = listPods(webhookNS, "app=webhook").getItems();
    assertNotNull((pods), "No pods are running in namespace : " + webhookNS);
    V1Pod pod = pods.stream()
            .filter(testpod -> testpod
                    .getMetadata()
                    .getName()
                    .contains("webhook"))
            .findAny()
            .orElse(null);

    assertNotNull(pod, "Can't find running webhook pod");
    logger.info("Wait for the webhook to fire alert and check webhook log file in {0} namespace ", webhookNS);

    withStandardRetryPolicy
            .conditionEvaluationListener(
                condition -> logger.info("Waiting for webhook to fire alert  "
                                + "(elapsed time {0}ms, remaining time {1}ms)",
                        condition.getElapsedTimeInMS(),
                        condition.getRemainingTimeInMS()))
            .until(assertDoesNotThrow(() -> searchPodLogForKey(pod,
                    "Some WLS cluster has only one running server for more than 1 minutes"),

                    "webhook failed to fire alert"));
  }

  /**
   * Edit Prometheus Config Map.
   * @param oldRegex search for existed value to replace
   * @param newRegex new value
   * @throws ApiException when update fails
   */
  private void editPrometheusCM(String oldRegex, String newRegex) throws ApiException {
    List<V1ConfigMap> cmList = Kubernetes.listConfigMaps(monitoringNS).getItems();
    V1ConfigMap promCm = cmList.stream()
            .filter(cm -> "prometheus-server".equals(cm.getMetadata().getName()))
            .findAny()
            .orElse(null);

    assertNotNull(promCm,"Can't find cm for prometheus-server");
    Map<String, String> cmData = promCm.getData();
    String values = cmData.get("prometheus.yml").replace(oldRegex,newRegex);
    assertNotNull(values, "can't find values for key prometheus.yml");
    cmData.replace("prometheus.yml", values);

    promCm.setData(cmData);
    Kubernetes.replaceConfigMap(promCm);

    cmList = Kubernetes.listConfigMaps(monitoringNS).getItems();

    promCm = cmList.stream()
            .filter(cm -> "prometheus-server".equals(cm.getMetadata().getName()))
            .findAny()
            .orElse(null);

    assertNotNull(promCm,"Can't find cm for prometheus-server");
    assertNotNull(promCm.getData(), "Can't retreive the cm data for prometheus-server after modification");

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
    final String prometheusRegexValue = String.format("regex: %s;%s;%s", domainNS, domainUid, clusterName);
    if (promHelmParams == null) {
      logger.info("create a staging location for monitoring creation scripts");
      Path fileTemp = Paths.get(RESULTS_ROOT, "ItMonitoringExporter", "createTempValueFile");
      FileUtils.deleteDirectory(fileTemp.toFile());
      Files.createDirectories(fileTemp);
  
      logger.info("copy the promvalue.yaml to staging location");
      Path srcPromFile = Paths.get(RESOURCE_DIR, "exporter", "promvalues.yaml");
      Path targetPromFile = Paths.get(fileTemp.toString(), "promvalues.yaml");
      Files.copy(srcPromFile, targetPromFile, StandardCopyOption.REPLACE_EXISTING);
      String oldValue = "regex: default;domain1;cluster-1";
      replaceStringInFile(targetPromFile.toString(),
              oldValue,
              prometheusRegexValue);
      //replace with webhook ns
      replaceStringInFile(targetPromFile.toString(),
              "webhook.webhook.svc.cluster.local",
              String.format("webhook.%s.svc.cluster.local", webhookNS));

    
      nodeportserver = getNextFreePort(32400, 32600);
      int nodeportalertmanserver = getNextFreePort(30400, 30600);
      promHelmParams = installAndVerifyPrometheus("prometheus",
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
      editPrometheusCM(prometheusDomainRegexValue, prometheusRegexValue);
      prometheusDomainRegexValue = prometheusRegexValue;
    }
    logger.info("Prometheus is running");

    String testappPrometheusSearchKey =
            "wls_servlet_invocation_total_count%7Bapp%3D%22wls-exporter%22%7D%5B15s%5D";

    assertDoesNotThrow(() -> checkMetricsViaPrometheus(testappPrometheusSearchKey, "wls-exporter"));


    if (grafanaHelmParams == null) {
      int nodeportgrafana = getNextFreePort(31000, 31200);
      grafanaHelmParams = installAndVerifyGrafana("grafana",
              monitoringNS,
              monitoringExporterEndToEndDir + "/grafana/values.yaml",
              grafanaChartVersion,
              nodeportgrafana);

      //wait until it starts dashboard
      String curlCmd = String.format("curl -v  -H 'Content-Type: application/json' "
                      + " -X GET http://admin:12345678@%s:%s/api/dashboards",
              K8S_NODEPORT_HOST, nodeportgrafana);
      withStandardRetryPolicy
              .conditionEvaluationListener(
                condition -> logger.info("Check access to grafana dashboard  "
                                      + "(elapsed time {0}ms, remaining time {1}ms)",
                              condition.getElapsedTimeInMS(),
                              condition.getRemainingTimeInMS()))
              .until(assertDoesNotThrow(() -> searchForKey(curlCmd, "grafana"),
                      String.format("Check access to grafana dashboard"
                              )));
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
      withStandardRetryPolicy
              .conditionEvaluationListener(
                  condition -> logger.info("Check grafana dashboard metric against expected {0} "
                                  + "(elapsed time {2}ms, remaining time {3}ms)",
                          "wls_jvm_uptime",
                          condition.getElapsedTimeInMS(),
                          condition.getRemainingTimeInMS()))
              .until(assertDoesNotThrow(() -> searchForKey(curlCmd2, "wls_jvm_uptime"),
                      String.format("Check grafana dashboard wls against expected %s",
                              "wls_jvm_uptime")));
    }
    logger.info("Grafana is running");
  }

  /**
   * Create a Webhook image, install and verify that pod is running.
   * @throws ApiException when creating images or pods fails
   */
  private void installWebhook() throws ApiException {
    assertTrue(installAndVerifyPodFromCustomImage(monitoringExporterEndToEndDir + "/webhook",
        "webhook",
        webhookNS,
        "app=webhook", REPO_SECRET_NAME), "Failed to start webhook");
  }

  /**
   * Create a Coordinator image, install and verify that pod is running.
   * @param namespace of domain to coordinate
   * @throws ApiException when creating images or pods fails
   */
  private void installCoordinator(String namespace) throws ApiException {
    assertTrue(installAndVerifyPodFromCustomImage(monitoringExporterSrcDir + "/config_coordinator",
        "coordinator",
        namespace,
        "app=coordinator", "coordsecret"), "Failed to start coordinator");
  }

  @AfterAll
  public void tearDownAll() {

    // shutdown domain1
    logger.info("Shutting down domain1");
    assertTrue(shutdownDomain(domain1Uid, domain1Namespace),
            String.format("shutdown domain %s in namespace %s failed", domain1Uid, domain1Namespace));

    // delete mii domain images created for parameterized test
    if (miiImage != null) {
      deleteImage(miiImage);
    }
    if (wdtImage != null) {
      deleteImage(miiImage);
    }

    // Delete domain custom resource
    logger.info("Delete domain custom resource in namespace {0}", domain1Namespace);
    assertDoesNotThrow(() -> deleteDomainCustomResource(domain1Uid, domain1Namespace),
        "deleteDomainCustomResource failed with ApiException");
    logger.info("Deleted Domain Custom Resource " + domain1Uid + " from " + domain1Namespace);

    // Delete wdt domain custom resource
    logger.info("Delete domain custom resource in namespace {0}", domain2Namespace);
    assertDoesNotThrow(() -> deleteDomainCustomResource(domain2Uid, domain2Namespace),
            "deleteDomainCustomResource failed with ApiException");
    logger.info("Deleted Domain Custom Resource " + domain2Uid + " from " + domain2Namespace);

    uninstallPrometheusGrafana();

    deletePersistentVolumeClaim("pvc-alertmanager",monitoringNS);
    deletePersistentVolume("pv-testalertmanager");
    deletePersistentVolumeClaim("pvc-prometheus",monitoringNS);
    deletePersistentVolume("pv-testprometheus");
    deletePersistentVolumeClaim("pvc-grafana",monitoringNS);
    deletePersistentVolume("pv-testgrafana");
    deleteNamespace(monitoringNS);
    uninstallDeploymentService(webhookDepl, webhookService);
    uninstallDeploymentService(coordinatorDepl, coordinatorService);
    // delete coordinator and webhook images
    if (webhookImage != null) {
      deleteImage(webhookImage);
    }
    if (coordinatorImage != null) {
      deleteImage(webhookImage);
    }
    deleteMonitoringExporterTempDir();

    // uninstall NGINX release
    if (nginxHelmParams != null) {
      assertThat(uninstallNginx(nginxHelmParams))
          .as("Test uninstallNginx1 returns true")
          .withFailMessage("uninstallNginx() did not return true")
          .isTrue();
    }
  }

  /**
   * Create a persistent volume and persistent volume claim.
   * @param nameSuffix unique nameSuffix for pv and pvc to create
   * @throws IOException when creating pv path fails
   */
  private static void createPvAndPvc(String nameSuffix, String namespace, HashMap<String,String> labels)
          throws IOException {
    logger.info("creating persistent volume and persistent volume claim");
    // create persistent volume and persistent volume claims
    Path pvHostPath = assertDoesNotThrow(
        () -> createDirectories(get(PV_ROOT, "ItMonitoringExporter", "monexp-persistentVolume",nameSuffix)),
            "createDirectories failed with IOException");
    logger.info("Creating PV directory {0}", pvHostPath);
    assertDoesNotThrow(() -> deleteDirectory(pvHostPath.toFile()), "deleteDirectory failed with IOException");
    assertDoesNotThrow(() -> createDirectories(pvHostPath), "createDirectories failed with IOException");

    V1PersistentVolume v1pv = new V1PersistentVolume()
        .spec(new V1PersistentVolumeSpec()
            .addAccessModesItem("ReadWriteMany")
            .storageClassName(nameSuffix)
            .volumeMode("Filesystem")
            .putCapacityItem("storage", Quantity.fromString("10Gi"))
            .persistentVolumeReclaimPolicy("Retain")
            .accessModes(Arrays.asList("ReadWriteMany"))
            .hostPath(new V1HostPathVolumeSource()
                .path(pvHostPath.toString())))
        .metadata(new V1ObjectMeta()
            .name("pv-test" + nameSuffix)
            .namespace(namespace));

    boolean hasLabels = false;
    String labelSelector = null;
    if (labels != null || !labels.isEmpty()) {
      hasLabels = true;
      v1pv.getMetadata().setLabels(labels);
      labelSelector = labels.entrySet()
              .stream()
              .map(e -> e.getKey() + "="
                      + e.getValue())
              .collect(Collectors.joining(","));
    }


    V1PersistentVolumeClaim v1pvc = new V1PersistentVolumeClaim()
        .spec(new V1PersistentVolumeClaimSpec()
        .addAccessModesItem("ReadWriteMany")
        .storageClassName(nameSuffix)
        .volumeName("pv-test" + nameSuffix)
        .resources(new V1ResourceRequirements()
            .putRequestsItem("storage", Quantity.fromString("10Gi"))))
        .metadata(new V1ObjectMeta()
            .name("pvc-" + nameSuffix)
            .namespace(namespace));
    if (hasLabels) {
      v1pvc.getMetadata().setLabels(labels);
    }

    createPVPVCAndVerify(v1pv,v1pvc, labelSelector, namespace);
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
                                                String labelSelector,
                                                String secretName) throws ApiException {
    //build webhook image
    String imagePullPolicy = "IfNotPresent";
    if (!REPO_NAME.isEmpty()) {
      imagePullPolicy = "Always";
    }
    String image = createPushImage(dockerFileDir,baseImageName, namespace, secretName);
    logger.info("Installing {0} in namespace {1}", baseImageName, namespace);
    if (baseImageName.equalsIgnoreCase(("webhook"))) {
      webhookImage = image;
      createWebHook(webhookImage, imagePullPolicy, namespace, REPO_SECRET_NAME);
    } else if (baseImageName.contains("coordinator")) {
      coordinatorImage = image;
      createCoordinator(coordinatorImage, imagePullPolicy, namespace, "coordsecret");
    } else {
      throw new ApiException("Custom image creation for " + baseImageName + "is not supported");
    }
    // wait for the pod to be ready
    logger.info("Wait for the {0} pod is ready in namespace {1}", baseImageName, namespace);
    withStandardRetryPolicy
        .conditionEvaluationListener(
            condition -> logger.info("Waiting for {0} to be running in namespace {1} "
                    + "(elapsed time {2}ms, remaining time {3}ms)",
                baseImageName,
                namespace,
                condition.getElapsedTimeInMS(),
                condition.getRemainingTimeInMS()))
        .until(assertDoesNotThrow(() -> podIsReady(namespace, labelSelector, baseImageName),
            baseImageName + " podIsReady failed with ApiException"));
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
    Map labels = new HashMap<String, String>();
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
    withStandardRetryPolicy
      .conditionEvaluationListener(
          condition -> logger.info("Waiting for deployment {0} to be completed in namespace {1} "
                          + "(elapsed time {2} ms, remaining time {3} ms)",
                  "webhook",
                  namespace,
                  condition.getElapsedTimeInMS(),
                  condition.getRemainingTimeInMS()))
        .until(Deployment.isReady("webhook", labels, namespace));

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
    withStandardRetryPolicy
        .conditionEvaluationListener(
            condition -> logger.info("Waiting for webhook to be running in namespace {0} "
                    + "(elapsed time {1}ms, remaining time {2}ms)",
                namespace,
                condition.getElapsedTimeInMS(),
                condition.getRemainingTimeInMS()))
        .until(assertDoesNotThrow(() -> podIsReady(namespace, "app=webhook", "webhook"),
            "webhook podIsReady failed with ApiException"));
  }

  /**
   * Uninstall provided deployment and service.
   */
  private static void uninstallDeploymentService(V1Deployment deployment, V1Service service) {
    String namespace = null;
    String serviceName = null;
    String deploymentName = null;
    try {
      if (service != null) {
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
      if (deployment != null) {
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
      Map labels = new HashMap<String, String>();
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
      withStandardRetryPolicy
              .conditionEvaluationListener(
                condition -> logger.info("Waiting for deployment {0} to be completed in namespace {1} "
                                      + "(elapsed time {2} ms, remaining time {3} ms)",
                              "coordinator",
                              namespace,
                              condition.getElapsedTimeInMS(),
                              condition.getRemainingTimeInMS()))
              .until(Deployment.isReady("coordinator", labels, namespace));

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
   * Checks if the pod is running in a given namespace.
   * The method assumes the pod name to starts with provided value for podName
   * and decorated with provided label selector
   * @param namespace in which to check for the pod existence
   * @return true if pods are exist and running otherwise false
   * @throws ApiException when there is error in querying the cluster
   */
  public static boolean isPodReady(String namespace, String labelSelector, String podName) throws ApiException {
    boolean status = false;
    V1PodList pods = listPods(namespace, labelSelector);
    V1Pod pod = null;
    for (var testpod : pods.getItems()) {
      if ((testpod.getMetadata().getName()).contains(podName)) {
        pod = testpod;
      }
    }
    if (pod != null) {
      // get the podCondition with the 'Ready' type field
      V1PodCondition v1PodReadyCondition = pod.getStatus().getConditions().stream()
          .filter(v1PodCondition -> "Ready".equals(v1PodCondition.getType()))
          .findAny()
          .orElse(null);

      if (v1PodReadyCondition != null) {
        status = v1PodReadyCondition.getStatus().equalsIgnoreCase("true");
      }
    } else {
      logger.info(podName + " pod doesn't exist");
    }
    return status;
  }

  /**
   * Build image with unique name, create corresponding docker secret and push to registry.
   *
   * @param dockerFileDir directory where dockerfile is located
   * @param baseImageName base image name
   * @param namespace image namespace
   * @param secretName docker secretname for image
   * @return image name
   */
  public static String createPushImage(String dockerFileDir, String baseImageName,
                                                    String namespace, String secretName) throws ApiException {
    // create unique image name with date
    final String imageTag = TestUtils.getDateAndTimeStamp();
    // Add repository name in image name for Jenkins runs
    final String imageName = REPO_NAME + baseImageName;

    final String image = imageName + ":" + imageTag;

    //build image
    assertTrue(Docker.createImage(dockerFileDir, image), "Failed to create image " + baseImageName);
    logger.info("image is created with name {0}", image);
    if (!new Namespace().exists(namespace)) {
      createNamespace(namespace);
    }

    //create registry docker secret
    createDockerRegistrySecret(REPO_USERNAME, REPO_PASSWORD, REPO_EMAIL,
        REPO_REGISTRY, secretName, namespace);
    // docker login and push image to docker registry if necessary
    dockerLoginAndPushImageToRegistry(image);

    return image;
  }

  /**
   * Check if Pod is running.
   *
   * @param namespace in which is pod is running
   * @return true if running false otherwise
   */
  public static Callable<Boolean> podIsReady(String namespace,
                                             String labelSelector,
                                             String podName) throws ApiException {
    return () -> {
      return isPodReady(namespace, labelSelector, podName);
    };
  }

  //download src from monitoring exporter github project and build webapp.
  private static void installMonitoringExporter() {
    logger.info("create a staging location for monitoring exporter github");
    Path monitoringTemp = Paths.get(RESULTS_ROOT, "monitoringexp", "srcdir");
    assertDoesNotThrow(() -> FileUtils.deleteDirectory(monitoringTemp.toFile()));
    assertDoesNotThrow(() -> Files.createDirectories(monitoringTemp));
    Path monitoringApp = Paths.get(RESULTS_ROOT, "monitoringexp", "apps");
    assertDoesNotThrow(() -> FileUtils.deleteDirectory(monitoringApp.toFile()));
    assertDoesNotThrow(() -> Files.createDirectories(monitoringApp));

    CommandParams params = Command.defaultCommandParams()
        .command("git clone " + MONITORING_EXPORTER_DOWNLOAD_URL + " " + monitoringTemp)
        .saveResults(true)
        .redirect(false);
    assertTrue(() -> Command.withParams(params)
        .execute());

    monitoringExporterSrcDir = monitoringTemp.toString();
    monitoringExporterEndToEndDir = monitoringTemp + "/samples/kubernetes/end2end/";
    String monitoringExporterVersion = Optional.ofNullable(System.getenv("MONITORING_EXPORTER_VERSION"))
        .orElse(MONITORING_EXPORTER_VERSION);
    logger.info("create a monitoring exporter version {0} ",monitoringExporterVersion);
    monitoringExporterAppDir = monitoringApp.toString();
    String monitoringExporterBuildFile = String.format(
        "%s/get%s.sh", monitoringExporterAppDir, monitoringExporterVersion);
    logger.info("Download a monitoring exporter build file {0} ", monitoringExporterBuildFile);
    String curlDownloadCmd = String.format("cd %s && "
        + "curl -O -L -k https://github.com/oracle/weblogic-monitoring-exporter/releases/download/v%s/get%s.sh",
        monitoringExporterAppDir,
        monitoringExporterVersion,
        monitoringExporterVersion);
    logger.info("execute command  a monitoring exporter curl command {0} ",curlDownloadCmd);
    assertTrue(new Command()
        .withParams(new CommandParams()
            .command(curlDownloadCmd))
        .execute(),"Failed to download monitoring exporter webapp");
    String command = String.format("chmod 777 %s ", monitoringExporterBuildFile);
    assertTrue(new Command()
        .withParams(new CommandParams()
            .command(command))
        .execute(),"Failed to build monitoring exporter webapp");
    String appDest = monitoringExporterAppDir;
    command = String.format("cd %s && %s  %s/exporter/exporter-config.yaml",
            appDest,
            monitoringExporterBuildFile,
            RESOURCE_DIR);
    assertTrue(new Command()
        .withParams(new CommandParams()
            .command(command))
        .execute(),"Failed to build monitoring exporter webapp");
  }

  /**
  * Delete monitoring exporter dir.
   */
  private static void deleteMonitoringExporterTempDir() {
    logger.info("delete temp dir for monitoring exporter github");
    Path monitoringTemp = Paths.get(RESULTS_ROOT, "monitoringexp", "srcdir");
    assertDoesNotThrow(() -> FileUtils.deleteDirectory(monitoringTemp.toFile()));
    Path monitoringApp = Paths.get(RESULTS_ROOT, "monitoringexp", "apps");
    assertDoesNotThrow(() -> FileUtils.deleteDirectory(monitoringApp.toFile()));
    Path fileTemp = Paths.get(RESULTS_ROOT, "ItMonitoringExporter", "promCreateTempValueFile");
    assertDoesNotThrow(() -> FileUtils.deleteDirectory(fileTemp.toFile()));
  }

  /**
   * Create mii image with monitoring exporter webapp.
   */
  private static String createAndVerifyMiiImage() {
    // create image with model files
    logger.info("Create image with model file with monitoring exporter app and verify");
    String appPath = String.format("%s/wls-exporter.war", monitoringExporterAppDir);
    List<String> appList = new ArrayList();
    appList.add(appPath);
    appList.add(SESSMIGR_APP_NAME);

    // build the model file list
    final List<String> modelList = Collections.singletonList(MODEL_DIR + "/" + MONEXP_MODEL_FILE);
    miiImage =
            createMiiImageAndVerify(MONEXP_IMAGE_NAME, modelList, appList);

    // docker login and push image to docker registry if necessary
    dockerLoginAndPushImageToRegistry(miiImage);

    // create docker registry secret to pull the image from registry
    logger.info("Create docker registry secret in namespace {0}", domain1Namespace);
    assertDoesNotThrow(() -> createDockerRegistrySecret(domain1Namespace),
            String.format("create Docker Registry Secret failed for %s", REPO_SECRET_NAME));

    return miiImage;
  }

  /**
   * Create and verify domain in image from endtoend sample topology with monitoring exporter.
   * @return image name
   */
  private static String createAndVerifyDomainInImage() {
    // create image with model files
    logger.info("Create image with model file with monitoring exporter app and verify");
    String app1Path = String.format("%s/wls-exporter.war", monitoringExporterAppDir);
    String app2Path = String.format("%s/../src/integration-tests/apps/testwebapp.war", ITTESTS_DIR);

    List<String> appList = new ArrayList();
    appList.add(app1Path);
    appList.add(app2Path);

    final int t3ChannelPort = getNextFreePort(31000, 32767);  // the port range has to be between 30,000 to 32,767

    Properties p = new Properties();
    p.setProperty("ADMIN_USER", ADMIN_USERNAME_DEFAULT);
    p.setProperty("ADMIN_PWD", ADMIN_PASSWORD_DEFAULT);
    p.setProperty("DOMAIN_NAME", domain2Uid);
    p.setProperty("ADMIN_NAME", "admin-server");
    p.setProperty("PRODUCTION_MODE_ENABLED", "true");
    p.setProperty("CLUSTER_NAME", clusterName);
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
                    File.createTempFile("domain", "properties"),
            "Failed to create domain properties file");
    assertDoesNotThrow(() ->
                    p.store(new FileOutputStream(domainPropertiesFile), "WDT properties file"),
            "Failed to write domain properties file");

    final List<String> propertyList = Collections.singletonList(domainPropertiesFile.getPath());

    // build the model file list
    final List<String> modelList = Collections.singletonList(monitoringExporterEndToEndDir
            + MONEXP_WDT_FILE);

    wdtImage =
        createImageAndVerify(MONEXP_IMAGE_NAME,
                modelList,
                appList,
                propertyList,
                WLS_BASE_IMAGE_NAME,
                WLS_BASE_IMAGE_TAG,
                WLS,
                false,
                domain2Uid, true);



    // docker login and push image to docker registry if necessary
    dockerLoginAndPushImageToRegistry(wdtImage);

    // create docker registry secret to pull the image from registry
    logger.info("Create docker registry secret in namespace {0}", domain2Namespace);
    assertDoesNotThrow(() -> createDockerRegistrySecret(domain2Namespace),
        String.format("create Docker Registry Secret failed for %s", REPO_SECRET_NAME));

    return wdtImage;
  }

  //create domain from provided image and verify it's start
  private static void createAndVerifyDomain(String miiImage,
                                            String domainUid,
                                            String namespace,
                                            String domainHomeSource,
                                            int replicaCount) {
    // create secret for admin credentials
    logger.info("Create secret for admin credentials");
    String adminSecretName = "weblogic-credentials";
    assertDoesNotThrow(() -> createSecretWithUsernamePassword(adminSecretName, namespace,
        "weblogic", "welcome1"),
        String.format("create secret for admin credentials failed for %s", adminSecretName));

    // create encryption secret
    logger.info("Create encryption secret");
    String encryptionSecretName = "encryptionsecret";
    assertDoesNotThrow(() -> createSecretWithUsernamePassword(encryptionSecretName, namespace,
        "weblogicenc", "weblogicenc"),
        String.format("create encryption secret failed for %s", encryptionSecretName));

    // create domain and verify
    logger.info("Create model in image domain {0} in namespace {1} using docker image {2}",
        domainUid, namespace, miiImage);
    createDomainCrAndVerify(adminSecretName, REPO_SECRET_NAME, encryptionSecretName, miiImage,domainUid,
            namespace, domainHomeSource, replicaCount);
    String adminServerPodName = domainUid + "-admin-server";

    // check that admin service exists in the domain namespace
    logger.info("Checking that admin service {0} exists in namespace {1}",
            adminServerPodName, namespace);
    checkServiceExists(adminServerPodName, namespace);

    // check that admin server pod is ready
    logger.info("Checking that admin server pod {0} is ready in namespace {1}",
        adminServerPodName, namespace);
    checkPodReady(adminServerPodName, domainUid, namespace);

    String managedServerPrefix = domainUid + "-managed-server";
    // check for managed server pods existence in the domain namespace
    for (int i = 1; i <= replicaCount; i++) {
      String managedServerPodName = managedServerPrefix + i;

      // check that the managed server pod exists
      logger.info("Checking that managed server pod {0} exists in namespace {1}",
          managedServerPodName, namespace);
      checkPodExists(managedServerPodName, domainUid, namespace);

      // check that the managed server pod is ready
      logger.info("Checking that managed server pod {0} is ready in namespace {1}",
          managedServerPodName, namespace);
      checkPodReady(managedServerPodName, domainUid, namespace);

      // check that the managed server service exists in the domain namespace
      logger.info("Checking that managed server service {0} exists in namespace {1}",
          managedServerPodName, namespace);
      checkServiceExists(managedServerPodName, namespace);
    }
  }

  private static void createDomainCrAndVerify(String adminSecretName,
                                              String repoSecretName,
                                              String encryptionSecretName,
                                              String miiImage,
                                              String domainUid,
                                              String namespace,
                                              String domainHomeSource,
                                              int replicaCount) {
    // create the domain CR
    Domain domain = new Domain()
        .apiVersion(DOMAIN_API_VERSION)
        .kind("Domain")
        .metadata(new V1ObjectMeta()
            .name(domainUid)
            .namespace(namespace))
        .spec(new DomainSpec()
            .domainUid(domainUid)
            .domainHomeSourceType(domainHomeSource)
            .image(miiImage)
            .addImagePullSecretsItem(new V1LocalObjectReference()
                .name(repoSecretName))
            .webLogicCredentialsSecret(new V1SecretReference()
                .name(adminSecretName)
                .namespace(namespace))
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
                .serverStartState("RUNNING")
                .adminService(new AdminService()
                    .addChannelsItem(new Channel()
                        .channelName("default")
                        .nodePort(0))))
            .addClustersItem(new Cluster()
                .clusterName(clusterName)
                .replicas(replicaCount)
                .serverStartState("RUNNING"))
            .configuration(new Configuration()
                .model(new Model()
                    .domainType("WLS")
                    .runtimeEncryptionSecret(encryptionSecretName))
                .introspectorJobActiveDeadlineSeconds(300L)));

    // create domain using model in image
    logger.info("Create model in image domain {0} in namespace {1} using docker image {2}",
        domainUid, namespace, miiImage);
    createDomainAndVerify(domain, namespace);
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
    assertThat(callWebAppAndCheckForServerNameInResponse(curlCmd, managedServerNames, 50))
        .as("Verify NGINX can access the monitoring exporter metrics from all managed servers in the domain")
        .withFailMessage("NGINX can not access the monitoring exporter metrics from one or more of the managed servers")
        .isTrue();
  }

  /**
   * Check metrics using Prometheus.
   *
   * @param searchKey   - metric query expression
   * @param expectedVal - expected metrics to search
   * @throws Exception if command to check metrics fails
   */
  private static void checkMetricsViaPrometheus(String searchKey, String expectedVal)
      throws Exception {

    // url
    String curlCmd =
        String.format("curl --silent --show-error --noproxy '*'  http://%s:%s/api/v1/query?query=%s",
            K8S_NODEPORT_HOST, nodeportserver, searchKey);

    logger.info("Executing Curl cmd {0}", curlCmd);
    logger.info("Checking searchKey: {0}", searchKey);
    logger.info(" expected Value {0} ", expectedVal);


    withStandardRetryPolicy
            .conditionEvaluationListener(
                condition -> logger.info("Check prometheus metric {0} against expected {1} "
                                + "(elapsed time {2}ms, remaining time {3}ms)",
                        searchKey,
                        expectedVal,
                        condition.getElapsedTimeInMS(),
                        condition.getRemainingTimeInMS()))
            .until(searchForKey(curlCmd, expectedVal));
  }

  /**
   * Check output of the command against expected output.
   *
   * @param cmd command
   * @param searchKey expected response from the command
   * @return true if the command succeeds
   */
  public static boolean execCommandCheckResponse(String cmd, String searchKey) {
    CommandParams params = Command
        .defaultCommandParams()
        .command(cmd)
        .saveResults(true)
        .redirect(false)
        .verbose(true);
    return Command.withParams(params).executeAndVerify(searchKey);
  }

  /**
   * Check if executed command contains expected output.
   *
   * @param cmd   command to execute
   * @param searchKey expected output
   * @return true if the output matches searchKey otherwise false
   */
  private static Callable<Boolean> searchForKey(String cmd, String searchKey) {
    return () -> execCommandCheckResponse(cmd, searchKey);
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

  /*
   ** uninstall Prometheus and Grafana helm charts
   */
  private void uninstallPrometheusGrafana() {
    if (promHelmParams != null) {
      Prometheus.uninstall(promHelmParams);
      promHelmParams = null;
      prometheusDomainRegexValue = null;
      logger.info("Prometheus is uninstalled");
    }
    if (grafanaHelmParams != null) {
      Grafana.uninstall(grafanaHelmParams);
      deleteSecret("grafana-secret",monitoringNS);
      grafanaHelmParams = null;
      logger.info("Grafana is uninstalled");
    }
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
    HtmlPage page2 = button.click();
    assertNotNull(page2, "can't reach page after submit");
    assertFalse((page2.asText()).contains("Error 500--Internal Server Error"),
            "page returns Error 500--Internal Server Error");
    // wait time to update configuration
    Thread.sleep(15 * 1000);
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
    assertTrue(page.asText().contains("JVMRuntime"),
            "Page does not contain expected JVMRuntime configuration");
    assertFalse(page.asText().contains("WebAppComponentRuntime"),
            "Page contains unexpected WebAppComponentRuntime configuration");
    Thread.sleep(20 * 1000);
    // "heap_free_current{name="managed-server1"}[15s]" search for results for last 15secs
    String prometheusSearchKey1 =
            "heap_free_current%7Bname%3D%22managed-server1%22%7D%5B15s%5D";
    checkMetricsViaPrometheus(prometheusSearchKey1, "managed-server1");
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
    checkMetricsViaPrometheus(sessionAppPrometheusSearchKey, "sessmigr");
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
    HtmlPage page = submitConfigureForm(exporterUrl, "replace", RESOURCE_DIR + "/exporter/rest_empty.yaml");
    assertTrue(page.asText().contains("queries:") && !page.asText().contains("values"));
  }

  /**
   * Try to append monitoring exporter configuration with empty configuration.
   *
   * @throws Exception if failed to apply configuration or check the expected values.
   */
  private void appendWithEmptyConfiguration() throws Exception {
    final WebClient webClient = new WebClient();
    HtmlPage originalPage = webClient.getPage(exporterUrl);
    assertNotNull(originalPage);
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
    checkMetricsViaPrometheus(searchKey, "sessmigr");
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
    checkMetricsViaPrometheus(searchKey, "\"domain\":\"wls-" + domain1Uid + "\"");
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
}