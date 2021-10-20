// Copyright (c) 2020, 2021, Oracle and/or its affiliates.
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
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.concurrent.Callable;
import java.util.stream.Collectors;

import io.kubernetes.client.custom.IntOrString;
import io.kubernetes.client.custom.Quantity;
import io.kubernetes.client.openapi.ApiException;
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
import oracle.weblogic.domain.MonitoringExporterSpecification;
import oracle.weblogic.domain.ServerPod;
import oracle.weblogic.kubernetes.actions.impl.Grafana;
import oracle.weblogic.kubernetes.actions.impl.GrafanaParams;
import oracle.weblogic.kubernetes.actions.impl.Prometheus;
import oracle.weblogic.kubernetes.actions.impl.primitive.HelmParams;
import oracle.weblogic.kubernetes.actions.impl.primitive.Kubernetes;
import oracle.weblogic.kubernetes.annotations.IntegrationTest;
import oracle.weblogic.kubernetes.annotations.Namespaces;
import oracle.weblogic.kubernetes.assertions.impl.ClusterRole;
import oracle.weblogic.kubernetes.assertions.impl.ClusterRoleBinding;
import oracle.weblogic.kubernetes.assertions.impl.Deployment;
import oracle.weblogic.kubernetes.logging.LoggingFacade;
import oracle.weblogic.kubernetes.utils.ExecCommand;
import org.apache.commons.io.FileUtils;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static java.nio.file.Files.createDirectories;
import static java.nio.file.Paths.get;
import static oracle.weblogic.kubernetes.TestConstants.ADMIN_PASSWORD_DEFAULT;
import static oracle.weblogic.kubernetes.TestConstants.ADMIN_USERNAME_DEFAULT;
import static oracle.weblogic.kubernetes.TestConstants.DOMAIN_API_VERSION;
import static oracle.weblogic.kubernetes.TestConstants.GRAFANA_CHART_VERSION;
import static oracle.weblogic.kubernetes.TestConstants.K8S_NODEPORT_HOST;
import static oracle.weblogic.kubernetes.TestConstants.MANAGED_SERVER_NAME_BASE;
import static oracle.weblogic.kubernetes.TestConstants.OCIR_SECRET_NAME;
import static oracle.weblogic.kubernetes.TestConstants.PROMETHEUS_CHART_VERSION;
import static oracle.weblogic.kubernetes.TestConstants.PV_ROOT;
import static oracle.weblogic.kubernetes.TestConstants.RESULTS_ROOT;
import static oracle.weblogic.kubernetes.TestConstants.WEBLOGIC_IMAGE_NAME;
import static oracle.weblogic.kubernetes.TestConstants.WEBLOGIC_IMAGE_TAG;
import static oracle.weblogic.kubernetes.actions.ActionConstants.ITTESTS_DIR;
import static oracle.weblogic.kubernetes.actions.ActionConstants.MODEL_DIR;
import static oracle.weblogic.kubernetes.actions.ActionConstants.RESOURCE_DIR;
import static oracle.weblogic.kubernetes.actions.ActionConstants.WLS;
import static oracle.weblogic.kubernetes.actions.TestActions.deleteImage;
import static oracle.weblogic.kubernetes.actions.TestActions.deletePersistentVolume;
import static oracle.weblogic.kubernetes.actions.TestActions.deletePersistentVolumeClaim;
import static oracle.weblogic.kubernetes.actions.TestActions.deleteSecret;
import static oracle.weblogic.kubernetes.actions.TestActions.getServiceNodePort;
import static oracle.weblogic.kubernetes.actions.TestActions.uninstallNginx;
import static oracle.weblogic.kubernetes.actions.impl.primitive.Kubernetes.deleteNamespace;
import static oracle.weblogic.kubernetes.assertions.impl.Kubernetes.listPods;
import static oracle.weblogic.kubernetes.utils.ApplicationUtils.callWebAppAndCheckForServerNameInResponse;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.checkPodReadyAndServiceExists;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.getNextFreePort;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.scaleAndVerifyCluster;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.testUntil;
import static oracle.weblogic.kubernetes.utils.DomainUtils.createDomainAndVerify;
import static oracle.weblogic.kubernetes.utils.FileUtils.replaceStringInFile;
import static oracle.weblogic.kubernetes.utils.ImageUtils.createImageAndPushToRepo;
import static oracle.weblogic.kubernetes.utils.ImageUtils.createImageAndVerify;
import static oracle.weblogic.kubernetes.utils.ImageUtils.createMiiImageAndVerify;
import static oracle.weblogic.kubernetes.utils.ImageUtils.createOcirRepoSecret;
import static oracle.weblogic.kubernetes.utils.ImageUtils.dockerLoginAndPushImageToRegistry;
import static oracle.weblogic.kubernetes.utils.LoadBalancerUtils.createIngressForDomainAndVerify;
import static oracle.weblogic.kubernetes.utils.LoadBalancerUtils.installAndVerifyNginx;
import static oracle.weblogic.kubernetes.utils.MonitoringUtils.buildMonitoringExporterApp;
import static oracle.weblogic.kubernetes.utils.MonitoringUtils.checkMetricsViaPrometheus;
import static oracle.weblogic.kubernetes.utils.MonitoringUtils.cloneMonitoringExporter;
import static oracle.weblogic.kubernetes.utils.MonitoringUtils.downloadMonitoringExporterApp;
import static oracle.weblogic.kubernetes.utils.MonitoringUtils.editPrometheusCM;
import static oracle.weblogic.kubernetes.utils.MonitoringUtils.installAndVerifyGrafana;
import static oracle.weblogic.kubernetes.utils.MonitoringUtils.installAndVerifyPrometheus;
import static oracle.weblogic.kubernetes.utils.MonitoringUtils.searchForKey;
import static oracle.weblogic.kubernetes.utils.OperatorUtils.installAndVerifyOperator;
import static oracle.weblogic.kubernetes.utils.PersistentVolumeUtils.createPVPVCAndVerify;
import static oracle.weblogic.kubernetes.utils.PodUtils.setPodAntiAffinity;
import static oracle.weblogic.kubernetes.utils.SecretUtils.createSecretWithUsernamePassword;
import static oracle.weblogic.kubernetes.utils.ThreadSafeLogger.getLogger;
import static org.apache.commons.io.FileUtils.deleteDirectory;
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
@DisplayName("Verify WebLogic Metric is processed as expected by MonitoringExporter via Prometheus and Grafana")
@IntegrationTest
class ItMonitoringExporterSamples {


  // domain constants
  private static final int replicaCount = 2;
  private static int managedServersCount = 2;
  private static String domain1Namespace = null;
  private static String domain2Namespace = null;
  private static String domain1Uid = "monexp-domain-1";
  private static String domain2Uid = "monexp-domain-2";

  private static HelmParams nginxHelmParams = null;
  private static int nodeportshttp = 0;
  private static int nodeportshttps = 0;
  private static List<String> ingressHost1List = null;
  private static List<String> ingressHost2List = null;

  private static String monitoringNS = null;
  private static String webhookNS = null;
  HelmParams promHelmParams = null;
  GrafanaParams grafanaHelmParams = null;
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

  private static String cluster1Name = "cluster-1";
  private static String cluster2Name = "cluster-2";
  private static String miiImage = null;
  private static String wdtImage = null;
  private static String webhookImage = null;
  private static String exporterImage = null;
  private static String  coordinatorImage = null;
  private static int managedServerPort = 8001;
  private static int nodeportserver;
  private static String exporterUrl = null;
  private static String prometheusDomainRegexValue = null;
  private static Map<String, Integer> clusterNameMsPortMap;
  private static LoggingFacade logger = null;
  private static List<String> clusterNames = new ArrayList<>();

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

    logger.info("install monitoring exporter");
    installMonitoringExporter();

    logger.info("create and verify WebLogic domain image using model in image with model files");
    miiImage = createAndVerifyMiiImage(monitoringExporterAppDir, MODEL_DIR + "/" + MONEXP_MODEL_FILE);

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
    labels.put("weblogic.domainUid", domain1Uid);
    assertDoesNotThrow(() -> createPvAndPvc("prometheus", monitoringNS, labels));
    assertDoesNotThrow(() -> createPvAndPvc("alertmanager",monitoringNS, labels));
    assertDoesNotThrow(() -> createPvAndPvc("grafana", monitoringNS, labels));
    cleanupPromGrafanaClusterRoles();
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
    wdtImage = createAndVerifyDomainInImage();
    logger.info("Create wdt domain and verify that it's running");
    createAndVerifyDomain(wdtImage, domain2Uid, domain2Namespace, "Image", replicaCount, false);
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
    checkMetricsViaPrometheus(testappPrometheusSearchKey, "test-webapp",nodeportserver);
    logger.info("fire alert by scaling down");
    fireAlert();
    logger.info("switch to monitor another domain");
    logger.info("create and verify WebLogic domain image using model in image with model files");

    // create and verify one cluster mii domain
    logger.info("Create domain and verify that it's running");
    createAndVerifyDomain(miiImage, domain1Uid, domain1Namespace, "FromModel", 1, true);

    String oldRegex = String.format("regex: %s;%s", domain2Namespace, domain2Uid);
    String newRegex = String.format("regex: %s;%s", domain1Namespace, domain1Uid);
    editPrometheusCM(oldRegex, newRegex, monitoringNS, "prometheus-server");
    String sessionAppPrometheusSearchKey =
        "wls_servlet_invocation_total_count%7Bapp%3D%22myear%22%7D%5B15s%5D";
    checkMetricsViaPrometheus(sessionAppPrometheusSearchKey, "sessmigr",nodeportserver);
    checkPromGrafanaLatestVersion();
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
      logger.info("verify http access");
      verifyMonExpAppAccessThroughNginx(ingressHost2List.get(0),managedServersCount);
      //verify metrics via prometheus
      String testappPrometheusSearchKey =
          "wls_servlet_invocation_total_count%7Bapp%3D%22test-webapp%22%7D%5B15s%5D";
      checkMetricsViaPrometheus(testappPrometheusSearchKey, "test-webapp",nodeportserver);
    } finally {
      uninstallPrometheusGrafana();
    }
  }

  private void fireAlert() throws ApiException {
    // scale domain2
    logger.info("Scaling cluster {0} of domain {1} in namespace {2} to {3} servers.",
        cluster1Name, domain2Uid, domain2Namespace, 1);
    managedServersCount = 1;
    scaleAndVerifyCluster(cluster1Name, domain2Uid, domain2Namespace,
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

    testUntil(
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
  ) throws IOException, ApiException {
    final String prometheusRegexValue = String.format("regex: %s;%s", domainNS, domainUid);
    if (promHelmParams == null) {
      cleanupPromGrafanaClusterRoles();
      logger.info("create a staging location for monitoring creation scripts");
      Path fileTemp = Paths.get(RESULTS_ROOT, "ItMonitoringExporterSamples", "createTempValueFile");
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
      //replace with webhook ns
      replaceStringInFile(targetPromFile.toString(),
          "webhook.webhook.svc.cluster.local",
          String.format("webhook.%s.svc.cluster.local", webhookNS));


      nodeportserver = getNextFreePort();
      int nodeportalertmanserver = getNextFreePort();
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
      editPrometheusCM(prometheusDomainRegexValue, prometheusRegexValue, monitoringNS, "prometheus-server");
      prometheusDomainRegexValue = prometheusRegexValue;
    }
    logger.info("Prometheus is running");

    if (grafanaHelmParams == null) {
      //logger.info("Node Port for Grafana is " + nodeportgrafana);
      grafanaHelmParams = installAndVerifyGrafana("grafana",
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

  /**
   * Create a Webhook image, install and verify that pod is running.
   * @throws ApiException when creating images or pods fails
   */
  private void installWebhook() throws ApiException {
    assertTrue(installAndVerifyPodFromCustomImage(monitoringExporterEndToEndDir + "/webhook",
        "webhook",
        webhookNS,
        "app=webhook", OCIR_SECRET_NAME), "Failed to start webhook");
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

    // uninstall NGINX release
    logger.info("Uninstalling NGINX");
    if (nginxHelmParams != null) {
      assertThat(uninstallNginx(nginxHelmParams))
          .as("Test uninstallNginx1 returns true")
          .withFailMessage("uninstallNginx() did not return true")
          .isTrue();
    }

    // delete mii domain images created for parameterized test
    if (miiImage != null) {
      deleteImage(miiImage);
    }
    if (wdtImage != null) {
      deleteImage(miiImage);
    }

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
      deleteImage(coordinatorImage);
    }
    deleteMonitoringExporterTempDir();
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
        () -> createDirectories(get(PV_ROOT, "ItMonitoringExporterSamples", "monexp-persistentVolume",nameSuffix)),
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
    String image = createImageAndPushToRepo(dockerFileDir,baseImageName, namespace, secretName, "");
    logger.info("Installing {0} in namespace {1}", baseImageName, namespace);
    if (baseImageName.equalsIgnoreCase(("webhook"))) {
      webhookImage = image;
      createWebHook(webhookImage, imagePullPolicy, namespace, OCIR_SECRET_NAME);
    } else if (baseImageName.contains("coordinator")) {
      coordinatorImage = image;
      createCoordinator(coordinatorImage, imagePullPolicy, namespace, "coordsecret");
    } else {
      throw new ApiException("Custom image creation for " + baseImageName + "is not supported");
    }
    // wait for the pod to be ready
    logger.info("Wait for the {0} pod is ready in namespace {1}", baseImageName, namespace);
    testUntil(
        assertDoesNotThrow(() -> podIsReady(namespace, labelSelector, baseImageName),
            baseImageName + " podIsReady failed with ApiException"),
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
    testUntil(
        assertDoesNotThrow(() -> podIsReady(namespace, "app=webhook", "webhook"),
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
    Path monitoringTemp = Paths.get(RESULTS_ROOT, "monitoringexp", "srcdir");
    monitoringExporterSrcDir = monitoringTemp.toString();
    cloneMonitoringExporter(monitoringExporterSrcDir);
    Path monitoringApp = Paths.get(RESULTS_ROOT, "monitoringexp", "apps");
    assertDoesNotThrow(() -> deleteDirectory(monitoringApp.toFile()));
    assertDoesNotThrow(() -> Files.createDirectories(monitoringApp));
    Path monitoringAppNoRestPort = Paths.get(RESULTS_ROOT, "monitoringexp", "apps", "norestport");
    assertDoesNotThrow(() -> deleteDirectory(monitoringAppNoRestPort.toFile()));
    assertDoesNotThrow(() -> Files.createDirectories(monitoringAppNoRestPort));
    monitoringExporterEndToEndDir = monitoringTemp + "/samples/kubernetes/end2end/";

    String monitoringExporterBranch = Optional.ofNullable(System.getenv("MONITORING_EXPORTER_BRANCH"))
        .orElse("master");
    //adding ability to build monitoring exporter if branch is not master
    boolean toBuildMonitoringExporter = (!monitoringExporterBranch.equalsIgnoreCase(("master")));
    monitoringExporterAppDir = monitoringApp.toString();
    String monitoringExporterAppNoRestPortDir = monitoringAppNoRestPort.toString();

    if (!toBuildMonitoringExporter) {
      downloadMonitoringExporterApp(RESOURCE_DIR
          + "/exporter/exporter-config.yaml", monitoringExporterAppDir);
      downloadMonitoringExporterApp(RESOURCE_DIR
          + "/exporter/exporter-config-norestport.yaml", monitoringExporterAppNoRestPortDir);
    } else {
      buildMonitoringExporterApp(monitoringExporterSrcDir, RESOURCE_DIR
          + "/exporter/exporter-config.yaml", monitoringExporterAppDir);
      buildMonitoringExporterApp(monitoringExporterSrcDir,RESOURCE_DIR
          + "/exporter/exporter-config-norestport.yaml", monitoringExporterAppNoRestPortDir);
    }
    logger.info("Finished to build Monitoring Exporter webapp.");
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
    Path fileTemp = Paths.get(RESULTS_ROOT, "ItMonitoringExporterSamples", "promCreateTempValueFile");
    assertDoesNotThrow(() -> FileUtils.deleteDirectory(fileTemp.toFile()));
  }


  /**
   * Create mii image with monitoring exporter webapp.
   */
  private static String createAndVerifyMiiImage(String monexpAppDir, String modelFilePath) {
    // create image with model files
    logger.info("Create image with model file with monitoring exporter app and verify");
    String appPath = String.format("%s/wls-exporter.war", monexpAppDir);
    List<String> appList = new ArrayList();
    appList.add(appPath);
    appList.add(SESSMIGR_APP_NAME);

    // build the model file list
    final List<String> modelList = Collections.singletonList(modelFilePath);
    String myImage =
        createMiiImageAndVerify(MONEXP_IMAGE_NAME, modelList, appList);

    // docker login and push image to docker registry if necessary
    dockerLoginAndPushImageToRegistry(myImage);

    return myImage;
  }

  /**
   * Create and verify domain in image from endtoend sample topology with monitoring exporter.
   * @return image name
   */
  private static String createAndVerifyDomainInImage() {
    // create image with model files
    logger.info("Create image with model file with monitoring exporter app and verify");
    String app1Path = String.format("%s/wls-exporter.war", monitoringExporterAppDir);
    String app2Path = String.format("%s/../operator/integration-tests/apps/testwebapp.war", ITTESTS_DIR);

    List<String> appList = new ArrayList();
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
            WEBLOGIC_IMAGE_NAME,
            WEBLOGIC_IMAGE_TAG,
            WLS,
            false,
            domain2Uid, true);

    // docker login and push image to docker registry if necessary
    dockerLoginAndPushImageToRegistry(wdtImage);

    return wdtImage;
  }

  //create domain from provided image and verify it's start
  private static void createAndVerifyDomain(String miiImage,
                                            String domainUid,
                                            String namespace,
                                            String domainHomeSource,
                                            int replicaCount,
                                            boolean twoClusters) {
    createAndVerifyDomain(miiImage,domainUid, namespace, domainHomeSource, replicaCount, twoClusters, null);

  }

  //create domain from provided image and monitoring exporter sidecar and verify it's start
  private static void createAndVerifyDomain(String miiImage,
                                            String domainUid,
                                            String namespace,
                                            String domainHomeSource,
                                            int replicaCount,
                                            boolean twoClusters,
                                            String monexpConfig) {
    // create docker registry secret to pull the image from registry
    // this secret is used only for non-kind cluster
    // create secret for admin credentials
    logger.info("Create docker registry secret in namespace {0}", namespace);
    createOcirRepoSecret(namespace);
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
    createDomainCrAndVerify(adminSecretName, OCIR_SECRET_NAME, encryptionSecretName, miiImage,domainUid,
        namespace, domainHomeSource, replicaCount, twoClusters, monexpConfig);
    String adminServerPodName = domainUid + "-admin-server";

    // check that admin server pod is ready
    logger.info("Checking that admin server pod {0} is ready in namespace {1}",
        adminServerPodName, namespace);
    checkPodReadyAndServiceExists(adminServerPodName, domainUid, namespace);

    // check for managed server pods existence in the domain namespace

    for (int i = 1; i <= replicaCount; i++) {
      if (twoClusters) {
        String managedServerCluster1PodName = domainUid
            + "-" + cluster1Name + "-managed-server" + i;
        String managedServerCluster2PodName = domainUid
            + "-" + cluster2Name + "-managed-server" + i;
        logger.info("Checking that managed server pod {0} exists and ready in namespace {1}",
            managedServerCluster1PodName, namespace);
        checkPodReadyAndServiceExists(managedServerCluster1PodName, domainUid, namespace);
        logger.info("Checking that managed server pod {0} exists and ready in namespace {1}",
            managedServerCluster2PodName, namespace);
        checkPodReadyAndServiceExists(managedServerCluster2PodName, domainUid, namespace);
      } else {
        String managedServerPodName = domainUid + "-managed-server" + i;
        // check that the managed server pod exists
        logger.info("Checking that managed server pod {0} exists and ready in namespace {1}",
            managedServerPodName, namespace);
        checkPodReadyAndServiceExists(managedServerPodName, domainUid, namespace);
      }
    }
  }

  private static void createDomainCrAndVerify(String adminSecretName,
                                              String repoSecretName,
                                              String encryptionSecretName,
                                              String miiImage,
                                              String domainUid,
                                              String namespace,
                                              String domainHomeSource,
                                              int replicaCount,
                                              boolean twoClusters,
                                              String monexpConfig) {
    int t3ChannelPort = getNextFreePort();
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
                    .value("-Dweblogic.StdoutDebugEnabled=false "
                        + "-Dweblogic.security.SSL.ignoreHostnameVerification=true "))
                .addEnvItem(new V1EnvVar()
                    .name("USER_MEM_ARGS")
                    .value("-Djava.security.egd=file:/dev/./urandom ")))
            .adminServer(new AdminServer()
                .serverStartState("RUNNING")
                .adminService(new AdminService()
                    .addChannelsItem(new Channel()
                        .channelName("default")
                        .nodePort(0))
                    .addChannelsItem(new Channel()
                        .channelName("T3Channel")
                        .nodePort(t3ChannelPort))))
            .addClustersItem(new Cluster()
                .clusterName(cluster1Name)
                .replicas(replicaCount)
                .serverStartState("RUNNING"))
            .configuration(new Configuration()
                .model(new Model()
                    .domainType("WLS")
                    .runtimeEncryptionSecret(encryptionSecretName))
                .introspectorJobActiveDeadlineSeconds(300L)));
    if (twoClusters) {
      domain.getSpec().getClusters().add(new Cluster()
          .clusterName(cluster2Name)
          .replicas(replicaCount)
          .serverStartState("RUNNING"));
    }
    setPodAntiAffinity(domain);
    // create domain using model in image
    logger.info("Create model in image domain {0} in namespace {1} using docker image {2}",
        domainUid, namespace, miiImage);
    if (monexpConfig != null) {
      //String monexpImage = "phx.ocir.io/weblogick8s/exporter:beta";
      logger.info("yaml config file path : " + monexpConfig);
      String contents = null;
      try {
        contents = new String(Files.readAllBytes(Paths.get(monexpConfig)));
      } catch (IOException e) {
        e.printStackTrace();
      }
      String imagePullPolicy = "IfNotPresent";
      domain.getSpec().monitoringExporter(new MonitoringExporterSpecification()
          .image(exporterImage)
          .imagePullPolicy(imagePullPolicy)
          .configuration(contents));

      logger.info("Created domain CR with Monitoring exporter configuration : "
          + domain.getSpec().getMonitoringExporter().toString());
    }
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
        .as("Verify NGINX can access the monitoring exporter metrics "
            + "from all managed servers in the domain via http")
        .withFailMessage("NGINX can not access the monitoring exporter metrics "
            + "from one or more of the managed servers via http")
        .isTrue();
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
      Grafana.uninstall(grafanaHelmParams.getHelmParams());
      deleteSecret("grafana-secret",monitoringNS);
      grafanaHelmParams = null;
      logger.info("Grafana is uninstalled");
    }
    cleanupPromGrafanaClusterRoles();
  }

  private static void cleanupPromGrafanaClusterRoles() {
    //extra cleanup
    try {
      if (ClusterRole.clusterRoleExists("prometheus-kube-state-metrics")) {
        Kubernetes.deleteClusterRole("prometheus-kube-state-metrics");
      }
      if (ClusterRole.clusterRoleExists("prometheus-server")) {
        Kubernetes.deleteClusterRole("prometheus-server");
      }
      if (ClusterRole.clusterRoleExists("prometheus-alertmanager")) {
        Kubernetes.deleteClusterRole("prometheus-alertmanager");
      }
      if (ClusterRole.clusterRoleExists("grafana-clusterrole")) {
        Kubernetes.deleteClusterRole("grafana-clusterrole");
      }
      if (ClusterRoleBinding.clusterRoleBindingExists("grafana-clusterrolebinding")) {
        Kubernetes.deleteClusterRoleBinding("grafana-clusterrolebinding");
      }
      if (ClusterRoleBinding.clusterRoleBindingExists("prometheus-alertmanager")) {
        Kubernetes.deleteClusterRoleBinding("prometheus-alertmanager");
      }
      if (ClusterRoleBinding.clusterRoleBindingExists("prometheus-kube-state-metrics")) {
        Kubernetes.deleteClusterRoleBinding("prometheus-kube-state-metrics");
      }
      if (ClusterRoleBinding.clusterRoleBindingExists("prometheus-server")) {
        Kubernetes.deleteClusterRoleBinding("prometheus-server");
      }
      String command = "kubectl delete psp grafana grafana-test";
      ExecCommand.exec(command);
    } catch (Exception ex) {
      //ignoring
      logger.info("getting exception during delete artifacts for grafana and prometheus");
    }
  }
}