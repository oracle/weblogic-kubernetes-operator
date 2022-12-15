// Copyright (c) 2021, 2022, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.weblogic.kubernetes.utils;

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
import java.util.concurrent.Callable;

import io.kubernetes.client.openapi.ApiException;
import io.kubernetes.client.openapi.models.V1ConfigMap;
import io.kubernetes.client.openapi.models.V1EnvVar;
import io.kubernetes.client.openapi.models.V1LocalObjectReference;
import io.kubernetes.client.openapi.models.V1ObjectMeta;
import io.kubernetes.client.openapi.models.V1Pod;
import io.kubernetes.client.openapi.models.V1Secret;
import io.kubernetes.client.openapi.models.V1SecretList;
import oracle.weblogic.domain.AdminServer;
import oracle.weblogic.domain.AdminService;
import oracle.weblogic.domain.Channel;
import oracle.weblogic.domain.ClusterList;
import oracle.weblogic.domain.Configuration;
import oracle.weblogic.domain.DomainResource;
import oracle.weblogic.domain.DomainSpec;
import oracle.weblogic.domain.Model;
import oracle.weblogic.domain.MonitoringExporterSpecification;
import oracle.weblogic.domain.ServerPod;
import oracle.weblogic.kubernetes.actions.impl.Cluster;
import oracle.weblogic.kubernetes.actions.impl.Grafana;
import oracle.weblogic.kubernetes.actions.impl.GrafanaParams;
import oracle.weblogic.kubernetes.actions.impl.Prometheus;
import oracle.weblogic.kubernetes.actions.impl.PrometheusParams;
import oracle.weblogic.kubernetes.actions.impl.primitive.Command;
import oracle.weblogic.kubernetes.actions.impl.primitive.CommandParams;
import oracle.weblogic.kubernetes.actions.impl.primitive.Helm;
import oracle.weblogic.kubernetes.actions.impl.primitive.HelmParams;
import oracle.weblogic.kubernetes.actions.impl.primitive.Kubernetes;
import oracle.weblogic.kubernetes.assertions.impl.ClusterRole;
import oracle.weblogic.kubernetes.assertions.impl.ClusterRoleBinding;
import oracle.weblogic.kubernetes.assertions.impl.RoleBinding;
import oracle.weblogic.kubernetes.logging.LoggingFacade;
import org.apache.commons.io.FileUtils;

import static oracle.weblogic.kubernetes.TestConstants.ADMIN_PASSWORD_DEFAULT;
import static oracle.weblogic.kubernetes.TestConstants.ADMIN_USERNAME_DEFAULT;
import static oracle.weblogic.kubernetes.TestConstants.DOMAIN_API_VERSION;
import static oracle.weblogic.kubernetes.TestConstants.FAILURE_RETRY_INTERVAL_SECONDS;
import static oracle.weblogic.kubernetes.TestConstants.FAILURE_RETRY_LIMIT_MINUTES;
import static oracle.weblogic.kubernetes.TestConstants.GRAFANA_REPO_NAME;
import static oracle.weblogic.kubernetes.TestConstants.GRAFANA_REPO_URL;
import static oracle.weblogic.kubernetes.TestConstants.HTTPS_PROXY;
import static oracle.weblogic.kubernetes.TestConstants.IMAGE_PULL_POLICY;
import static oracle.weblogic.kubernetes.TestConstants.K8S_NODEPORT_HOST;
import static oracle.weblogic.kubernetes.TestConstants.KUBERNETES_CLI;
import static oracle.weblogic.kubernetes.TestConstants.MANAGED_SERVER_NAME_BASE;
import static oracle.weblogic.kubernetes.TestConstants.MONITORING_EXPORTER_BRANCH;
import static oracle.weblogic.kubernetes.TestConstants.MONITORING_EXPORTER_WEBAPP_VERSION;
import static oracle.weblogic.kubernetes.TestConstants.OKD;
import static oracle.weblogic.kubernetes.TestConstants.OKE_CLUSTER;
import static oracle.weblogic.kubernetes.TestConstants.PROMETHEUS_REPO_NAME;
import static oracle.weblogic.kubernetes.TestConstants.PROMETHEUS_REPO_URL;
import static oracle.weblogic.kubernetes.TestConstants.TEST_IMAGES_REPO_SECRET_NAME;
import static oracle.weblogic.kubernetes.TestConstants.WLSIMG_BUILDER;
import static oracle.weblogic.kubernetes.actions.ActionConstants.MONITORING_EXPORTER_DOWNLOAD_URL;
import static oracle.weblogic.kubernetes.actions.ActionConstants.RESOURCE_DIR;
import static oracle.weblogic.kubernetes.actions.TestActions.deleteSecret;
import static oracle.weblogic.kubernetes.actions.TestActions.getPod;
import static oracle.weblogic.kubernetes.actions.TestActions.installGrafana;
import static oracle.weblogic.kubernetes.actions.TestActions.installPrometheus;
import static oracle.weblogic.kubernetes.actions.impl.primitive.Kubernetes.copyFileToPod;
import static oracle.weblogic.kubernetes.actions.impl.primitive.Kubernetes.listSecrets;
import static oracle.weblogic.kubernetes.assertions.TestAssertions.callTestWebAppAndCheckForServerNameInResponse;
import static oracle.weblogic.kubernetes.assertions.TestAssertions.isGrafanaReady;
import static oracle.weblogic.kubernetes.assertions.TestAssertions.isHelmReleaseDeployed;
import static oracle.weblogic.kubernetes.assertions.TestAssertions.isPrometheusAdapterReady;
import static oracle.weblogic.kubernetes.assertions.TestAssertions.isPrometheusReady;
import static oracle.weblogic.kubernetes.utils.ApplicationUtils.callWebAppAndCheckForServerNameInResponse;
import static oracle.weblogic.kubernetes.utils.ClusterUtils.createClusterAndVerify;
import static oracle.weblogic.kubernetes.utils.ClusterUtils.createClusterResource;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.addSccToDBSvcAccount;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.checkPodReadyAndServiceExists;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.getNextFreePort;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.testUntil;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.withLongRetryPolicy;
import static oracle.weblogic.kubernetes.utils.DomainUtils.createDomainAndVerify;
import static oracle.weblogic.kubernetes.utils.FileUtils.checkFile;
import static oracle.weblogic.kubernetes.utils.FileUtils.replaceStringInFile;
import static oracle.weblogic.kubernetes.utils.ImageUtils.createMiiImageAndVerify;
import static oracle.weblogic.kubernetes.utils.ImageUtils.createTestRepoSecret;
import static oracle.weblogic.kubernetes.utils.ImageUtils.imageRepoLoginAndPushImageToRegistry;
import static oracle.weblogic.kubernetes.utils.PodUtils.execInPod;
import static oracle.weblogic.kubernetes.utils.PodUtils.getPodName;
import static oracle.weblogic.kubernetes.utils.PodUtils.setPodAntiAffinity;
import static oracle.weblogic.kubernetes.utils.SecretUtils.createSecretWithUsernamePassword;
import static oracle.weblogic.kubernetes.utils.ThreadSafeLogger.getLogger;
import static org.apache.commons.io.FileUtils.deleteDirectory;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A utility class for Monitoring Weblogic Domain via Weblogic MonitoringExporter and Prometheus.
 */
public class MonitoringUtils {

  private static LoggingFacade logger = getLogger();
  private static String cluster1Name = "cluster-1";
  private static String cluster2Name = "cluster-2";

  /**
   * Download monitoring exporter webapp wls-exporter.war based on provided version
   * and insert provided configuration.
   * @param configFile configuration to monitor Weblogic Domain
   * @param applicationDir location where application war file will be created
   */
  public static void downloadMonitoringExporterApp(String configFile, String applicationDir) {
    //version of wls-exporter.war published in https://github.com/oracle/weblogic-monitoring-exporter/releases/
    String monitoringExporterRelease = MONITORING_EXPORTER_WEBAPP_VERSION;
    String monitoringExporterWebAppScriptVersion = monitoringExporterRelease.substring(0,
        monitoringExporterRelease.length() - 2);
    String curlDownloadCmd = String.format("cd %s && "
            + "curl -O -L -k https://github.com/oracle/weblogic-monitoring-exporter/releases/download/v%s/get%s.sh",
        applicationDir,
        monitoringExporterRelease,
        monitoringExporterWebAppScriptVersion);
    String monitoringExporterBuildFile = String.format(
        "%s/get%s.sh", applicationDir, monitoringExporterWebAppScriptVersion);
    logger.info("execute command  a monitoring exporter curl command {0} ", curlDownloadCmd);
    assertTrue(Command
        .withParams(new CommandParams()
            .command(curlDownloadCmd))
        .execute(), "Failed to download monitoring exporter webapp");
    String command = String.format("chmod 777 %s ", monitoringExporterBuildFile);
    assertTrue(Command
        .withParams(new CommandParams()
            .command(command))
        .execute(), "Failed to download monitoring exporter webapp");

    String command1 = String.format("cd %s && %s  %s",
        applicationDir,
        monitoringExporterBuildFile,
        configFile);

    testUntil(
        (() -> Command
          .withParams(
              new CommandParams()
              .verbose(true)
              .command(command1))
          .executeAndVerify("adding: config.yml")
        ),
        logger,
        "Downloading monitoring exporter webapp");

    assertDoesNotThrow(() -> checkFile(applicationDir + "/wls-exporter.war"),
        "Monitoring Exporter web application file was not found");
  }

  /**
 * Build monitoring exporter web applicaiont wls-exporter.war with provided configuration
   * @param monitoringExporterSrcDir directory containing github monitoring exporter
   * @param configFile configuration file for weblogic domain monitoring
   * @param appDir directory where war file will be created
   */
  public static void buildMonitoringExporterApp(String monitoringExporterSrcDir, String configFile, String appDir) {

    String command = String.format("cd %s && mvn clean install -Dmaven.test.skip=true -Dconfiguration=%s",
        monitoringExporterSrcDir,
        configFile);
    LoggingFacade logger = getLogger();
    logger.info("Executing command " + command);
    Path srcFile = Paths.get(monitoringExporterSrcDir,
        "target", "wls-exporter.war");

    assertTrue(Command
        .withParams(new CommandParams()
            .command(command))
        .execute(), "Failed to build monitoring exporter webapp");

    Path targetFile = Paths.get(appDir, "wls-exporter.war");
    assertDoesNotThrow(() ->
        Files.copy(srcFile, targetFile, StandardCopyOption.REPLACE_EXISTING));
  }

  /**
   * Clone monitoring exporter github src.
   * @param monitoringExporterSrcDir directory containing github monitoring exporter
   */
  public static void cloneMonitoringExporter(String monitoringExporterSrcDir) {
    LoggingFacade logger = getLogger();
    logger.info("create a staging location for monitoring exporter github");
    Path monitoringTemp = Paths.get(monitoringExporterSrcDir);
    assertDoesNotThrow(() -> deleteDirectory(monitoringTemp.toFile()));
    assertDoesNotThrow(() -> Files.createDirectories(monitoringTemp));

    String monitoringExporterBranch = MONITORING_EXPORTER_BRANCH;
    CommandParams params = Command.defaultCommandParams()
        .command("git clone -b "
            + monitoringExporterBranch
            + " "
            + MONITORING_EXPORTER_DOWNLOAD_URL
            + " " + monitoringTemp)
        .saveResults(true)
        .redirect(false);
    assertTrue(() -> Command.withParams(params)
        .execute());
  }

  /**
   * Check metrics using Prometheus.
   *
   * @param searchKey   - metric query expression
   * @param expectedVal - expected metrics to search
   * @param hostPortPrometheus host:nodePort for prometheus
   * @throws Exception if command to check metrics fails
   */
  public static void checkMetricsViaPrometheus(String searchKey, String expectedVal, String hostPortPrometheus)
      throws Exception {

    LoggingFacade logger = getLogger();
    // url
    String curlCmd =
        String.format("curl --silent --show-error --noproxy '*'  http://%s/api/v1/query?query=%s",
            hostPortPrometheus, searchKey);

    logger.info("Executing Curl cmd {0}", curlCmd);
    logger.info("Checking searchKey: {0}", searchKey);
    logger.info(" expected Value {0} ", expectedVal);
    testUntil(
        searchForKey(curlCmd, expectedVal),
        logger,
        "Check prometheus metric {0} against expected {1}",
        searchKey,
        expectedVal);
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
  public static Callable<Boolean> searchForKey(String cmd, String searchKey) {
    return () -> execCommandCheckResponse(cmd, searchKey);
  }

  /**
   * Edit Prometheus Config Map.
   * @param oldRegex search for existed value to replace
   * @param newRegex new value
   * @param prometheusNS namespace for prometheus pod
   * @param cmName name of Config Map to modify
   * @throws ApiException when update fails
   */
  public static void editPrometheusCM(String oldRegex, String newRegex,
                                      String prometheusNS, String cmName) throws ApiException {
    List<V1ConfigMap> cmList = Kubernetes.listConfigMaps(prometheusNS).getItems();
    V1ConfigMap promCm = cmList.stream()
        .filter(cm -> cmName.equals(cm.getMetadata().getName()))
        .findAny()
        .orElse(null);

    assertNotNull(promCm,"Can't find cm for " + cmName);
    Map<String, String> cmData = promCm.getData();
    String values = cmData.get("prometheus.yml").replace(oldRegex,newRegex);
    assertNotNull(values, "can't find values for key prometheus.yml");
    cmData.replace("prometheus.yml", values);

    promCm.setData(cmData);
    Kubernetes.replaceConfigMap(promCm);

    cmList = Kubernetes.listConfigMaps(prometheusNS).getItems();

    promCm = cmList.stream()
        .filter(cm -> cmName.equals(cm.getMetadata().getName()))
        .findAny()
        .orElse(null);

    assertNotNull(promCm,"Can't find cm for " + cmName);
    assertNotNull(promCm.getData(), "Can't retreive the cm data for " + cmName + " after modification");

  }

  /**
   * Install Prometheus and wait up to five minutes until the prometheus pods are ready.
   *
   * @param promReleaseSuffix the prometheus release name unique suffix
   * @param promNamespace the prometheus namespace in which the prometheus will be installed
   * @param promVersion the version of the prometheus helm chart
   * @param prometheusRegexValue string (namespace;domainuid) to manage specific domain,
   *                            default is regex: default;domain1
   * @return the prometheus Helm installation parameters
   */
  public static PrometheusParams installAndVerifyPrometheus(String promReleaseSuffix,
                                                            String promNamespace,
                                                            String promVersion,
                                                            String prometheusRegexValue,
                                                            String promHelmValuesFile) {
    return installAndVerifyPrometheus(promReleaseSuffix,
            promNamespace,
            promVersion,
            prometheusRegexValue,
            promHelmValuesFile,
            null);
  }

  /**
   * Install Prometheus and wait up to five minutes until the prometheus pods are ready.
   *
   * @param promReleaseSuffix the prometheus release name unigue suffix
   * @param promNamespace the prometheus namespace in which the operator will be installed
   * @param promVersion the version of the prometheus helm chart
   * @param prometheusRegexValue string (namespace;domainuid) to manage specific domain,
   *                            default is regex: default;domain1
   * @param promHelmValuesFileDir path to prometheus helm values file directory
   * @param webhookNS namespace for webhook namespace
   * @return the prometheus Helm installation parameters
   */
  public static PrometheusParams installAndVerifyPrometheus(String promReleaseSuffix,
                                                      String promNamespace,
                                                      String promVersion,
                                                      String prometheusRegexValue,
                                                      String promHelmValuesFileDir,
                                                      String webhookNS) {
    LoggingFacade logger = getLogger();
    String prometheusReleaseName = "prometheus" + promReleaseSuffix;
    logger.info("create a staging location for prometheus scripts");
    Path fileTemp = Paths.get(promHelmValuesFileDir);
    assertDoesNotThrow(() -> FileUtils.deleteDirectory(fileTemp.toFile()),"Failed to delete temp dir for prometheus");

    assertDoesNotThrow(() -> Files.createDirectories(fileTemp), "Failed to create temp dir for prometheus");

    logger.info("copy the promvalues.yaml to staging location");
    Path srcPromFile = Paths.get(RESOURCE_DIR, "exporter", "promvalues.yaml");
    Path targetPromFile = Paths.get(fileTemp.toString(), "promvalues.yaml");
    assertDoesNotThrow(() -> Files.copy(srcPromFile, targetPromFile,
        StandardCopyOption.REPLACE_EXISTING)," Failed to copy files");
    String oldValue = "regex: default;domain1";
    assertDoesNotThrow(() -> replaceStringInFile(targetPromFile.toString(),
        oldValue,
        prometheusRegexValue), "Failed to replace String ");
    assertDoesNotThrow(() -> replaceStringInFile(targetPromFile.toString(),
        "pvc-alertmanager",
        "pvc-alertmanager" + promReleaseSuffix), "Failed to replace String ");;
    assertDoesNotThrow(() -> replaceStringInFile(targetPromFile.toString(),
        "pvc-prometheus",
        "pvc-" + prometheusReleaseName),"Failed to replace String ");
    if (webhookNS != null) {
      //replace with webhook ns
      assertDoesNotThrow(() -> replaceStringInFile(targetPromFile.toString(),
              "webhook.webhook.svc.cluster.local",
              String.format("webhook.%s.svc.cluster.local", webhookNS)), "Failed to replace String ");
    }
    if (OKD) {
      assertDoesNotThrow(() -> replaceStringInFile(targetPromFile.toString(),
          "65534",
          "1000620001"), "Failed to replace String ");
    }
    int promServerNodePort = getNextFreePort();
    int alertManagerNodePort = getNextFreePort();

    // Helm install parameters
    HelmParams promHelmParams = new HelmParams()
        .releaseName(prometheusReleaseName)
        .namespace(promNamespace)
        .repoUrl(PROMETHEUS_REPO_URL)
        .repoName(PROMETHEUS_REPO_NAME)
        .chartName("prometheus")
        .chartValuesFile(targetPromFile.toString());

    if (promVersion != null) {
      promHelmParams.chartVersion(promVersion);
    }

    // prometheus chart values to override
    PrometheusParams prometheusParams = new PrometheusParams()
        .helmParams(promHelmParams)
        .nodePortServer(promServerNodePort)
        .nodePortAlertManager(alertManagerNodePort);

    if (OKD) {
      addSccToDBSvcAccount(prometheusReleaseName + "-server", promNamespace);
      addSccToDBSvcAccount(prometheusReleaseName + "-kube-state-metrics", promNamespace);
      addSccToDBSvcAccount(prometheusReleaseName + "-alertmanager", promNamespace);
      addSccToDBSvcAccount("default", promNamespace);
    }
    // install prometheus
    logger.info("Installing prometheus in namespace {0}", promNamespace);
    assertTrue(installPrometheus(prometheusParams),
        String.format("Failed to install prometheus in namespace %s", promNamespace));
    logger.info("Prometheus installed in namespace {0}", promNamespace);

    // list Helm releases matching operator release name in operator namespace
    logger.info("Checking prometheus release {0} status in namespace {1}",
        prometheusReleaseName, promNamespace);
    assertTrue(isHelmReleaseDeployed(prometheusReleaseName, promNamespace),
        String.format("Prometheus release %s is not in deployed status in namespace %s",
            prometheusReleaseName, promNamespace));
    logger.info("Prometheus release {0} status is deployed in namespace {1}",
        prometheusReleaseName, promNamespace);

    // wait for the promethues pods to be ready
    logger.info("Wait for the promethues pod is ready in namespace {0}", promNamespace);
    testUntil(
        assertDoesNotThrow(() -> isPrometheusReady(promNamespace,prometheusReleaseName),
          "prometheusIsReady failed with ApiException"),
        logger,
        "prometheus to be running in namespace {0}",
        promNamespace);

    return prometheusParams;
  }

  /**
   * Install Prometheus adapter and wait up to five minutes until the prometheus adapter pods are ready.
   *
   * @param promAdapterReleaseName the prometheus adapter release name
   * @param promAdapterNamespace the prometheus adapter namespace
   * @return the prometheus adapter Helm installation parameters
   */
  public static HelmParams installAndVerifyPrometheusAdapter(String promAdapterReleaseName,
                                                            String promAdapterNamespace,
                                                             String prometheusHost,
                                                             int prometheusPort) {
    LoggingFacade logger = getLogger();
    Path srcPromAdapterFile = Paths.get(RESOURCE_DIR, "exporter", "promadaptervalues.yaml");

    // Helm install parameters
    HelmParams promAdapterHelmParams = new HelmParams()
        .releaseName(promAdapterReleaseName)
        .namespace(promAdapterNamespace)
        .repoUrl(PROMETHEUS_REPO_URL)
        .repoName(PROMETHEUS_REPO_NAME)
        .chartName("prometheus-adapter")
        .chartValuesFile(srcPromAdapterFile.toString());


    // install prometheus adapter
    logger.info("Installing prometheus adapter in namespace {0}", promAdapterNamespace);
    Map<String, Object> chartValues = new HashMap<>();
    chartValues.put("prometheus.url", "http://" + prometheusHost);
    chartValues.put("prometheus.port", prometheusPort);
    assertTrue(Helm.install(promAdapterHelmParams, chartValues),
        String.format("Failed to install prometheus adapter in namespace %s", promAdapterNamespace));
    logger.info("Prometheus Adapter installed in namespace {0}", promAdapterNamespace);

    // list Helm releases matching operator release name in operator namespace
    logger.info("Checking prometheus adapter release {0} status in namespace {1}",
        promAdapterReleaseName, promAdapterNamespace);
    assertTrue(isHelmReleaseDeployed(promAdapterReleaseName, promAdapterNamespace),
        String.format("Prometheus release %s is not in deployed status in namespace %s",
            promAdapterReleaseName, promAdapterNamespace));
    logger.info("Prometheus adapter release {0} status is deployed in namespace {1}",
        promAdapterReleaseName, promAdapterNamespace);

    // wait for the promethues adapter pod to be ready
    logger.info("Wait for the promethues adapter pod is ready in namespace {0}", promAdapterNamespace);
    String podName = assertDoesNotThrow(() -> getPodName(promAdapterNamespace, "prometheus-adapter"),
        "Can't find prometheus-adapter pod");
    testUntil(
        assertDoesNotThrow(() -> isPrometheusAdapterReady(promAdapterNamespace, podName),
            "prometheusAdapterIsReady failed with ApiException"),
        logger,
        "prometheus adapter to be running in namespace {0}",
        promAdapterNamespace);

    return promAdapterHelmParams;
  }


  /**
   * Install Grafana and wait up to five minutes until the grafana pod is ready.
   *
   * @param grafanaReleaseName the grafana release name
   * @param grafanaNamespace the grafana namespace in which the operator will be installed
   * @param grafanaHelmValuesFileDir the grafana helm values.yaml file directory
   * @param grafanaVersion the version of the grafana helm chart
   * @return the grafana Helm installation parameters
   */
  public static GrafanaParams installAndVerifyGrafana(String grafanaReleaseName,
                                                      String grafanaNamespace,
                                                      String grafanaHelmValuesFileDir,
                                                      String grafanaVersion) {
    LoggingFacade logger = getLogger();
    logger.info("create a staging location for prometheus scripts");
    Path fileTemp = Paths.get(grafanaHelmValuesFileDir);
    assertDoesNotThrow(() -> FileUtils.deleteDirectory(fileTemp.toFile()),"Failed to delete temp dir for grafana");

    assertDoesNotThrow(() -> Files.createDirectories(fileTemp), "Failed to create temp dir for grafana");

    logger.info("copy the grafanavalues.yaml to staging location");
    Path srcGrafanaFile = Paths.get(RESOURCE_DIR, "exporter", "grafanavalues.yaml");
    Path targetGrafanaFile = Paths.get(fileTemp.toString(), "grafanavalues.yaml");
    assertDoesNotThrow(() -> Files.copy(srcGrafanaFile, targetGrafanaFile,
            StandardCopyOption.REPLACE_EXISTING)," Failed to copy files");
    assertDoesNotThrow(() -> replaceStringInFile(targetGrafanaFile.toString(),
            "pvc-grafana", "pvc-" + grafanaReleaseName));
    if (!OKE_CLUSTER) {
      assertDoesNotThrow(() -> replaceStringInFile(targetGrafanaFile.toString(),
              "enabled: false", "enabled: true"));
    }
    // Helm install parameters
    HelmParams grafanaHelmParams = new HelmParams()
        .releaseName(grafanaReleaseName)
        .namespace(grafanaNamespace)
        .repoUrl(GRAFANA_REPO_URL)
        .repoName(GRAFANA_REPO_NAME)
        .chartName("grafana")
        .chartValuesFile(targetGrafanaFile.toString());

    if (grafanaVersion != null) {
      grafanaHelmParams.chartVersion(grafanaVersion);
    }

    boolean secretExists = false;
    V1SecretList listSecrets = listSecrets(grafanaNamespace);
    if (null != listSecrets) {
      for (V1Secret item : listSecrets.getItems()) {
        if (item.getMetadata().getName().equals("grafana-secret")) {
          secretExists = true;
          break;
        }
      }
    }
    if (!secretExists) {
      //create grafana secret
      createSecretWithUsernamePassword("grafana-secret", grafanaNamespace, "admin", "12345678");
    }
    // install grafana
    logger.info("Installing grafana in namespace {0}", grafanaNamespace);
    int grafanaNodePort = getNextFreePort();
    logger.info("Installing grafana with node port {0}", grafanaNodePort);
    // grafana chart values to override
    GrafanaParams grafanaParams = new GrafanaParams()
        .helmParams(grafanaHelmParams)
        .nodePort(grafanaNodePort);
    boolean isGrafanaInstalled = false;
    if (OKD) {
      addSccToDBSvcAccount(grafanaReleaseName,grafanaNamespace);
    }
    assertTrue(installGrafana(grafanaParams),
        String.format("Failed to install grafana in namespace %s",grafanaNamespace));
    logger.info("Grafana installed in namespace {0}", grafanaNamespace);

    // list Helm releases matching grafana release name in  namespace
    logger.info("Checking grafana release {0} status in namespace {1}",
        grafanaReleaseName, grafanaNamespace);
    assertTrue(isHelmReleaseDeployed(grafanaReleaseName, grafanaNamespace),
        String.format("Grafana release %s is not in deployed status in namespace %s",
            grafanaReleaseName, grafanaNamespace));
    logger.info("Grafana release {0} status is deployed in namespace {1}",
        grafanaReleaseName, grafanaNamespace);

    // wait for the grafana pod to be ready
    logger.info("Wait for the grafana pod is ready in namespace {0}", grafanaNamespace);
    testUntil(
        assertDoesNotThrow(() -> isGrafanaReady(grafanaNamespace),
          "grafanaIsReady failed with ApiException"),
        logger,
        "grafana to be running in namespace {0}",
        grafanaNamespace);



    //return grafanaHelmParams;
    return grafanaParams;
  }

  /**
   * Extra clean up for Prometheus and  Grafana artifacts.
   *
   * @param grafanaReleaseName the grafana release name
   * @param prometheusReleaseName prometheus release name
   */
  public static void cleanupPromGrafanaClusterRoles(String prometheusReleaseName, String grafanaReleaseName) {
    //extra cleanup
    try {
      if (ClusterRole.clusterRoleExists(prometheusReleaseName + "-kube-state-metrics")) {
        Kubernetes.deleteClusterRole(prometheusReleaseName + "-kube-state-metrics");
      }
      if (ClusterRole.clusterRoleExists(prometheusReleaseName + "-server")) {
        Kubernetes.deleteClusterRole(prometheusReleaseName + "-server");
      }
      if (ClusterRole.clusterRoleExists(prometheusReleaseName + "-alertmanager")) {
        Kubernetes.deleteClusterRole(prometheusReleaseName + "-alertmanager");
      }
      if (ClusterRole.clusterRoleExists(grafanaReleaseName + "-clusterrole")) {
        Kubernetes.deleteClusterRole(grafanaReleaseName + "-clusterrole");
      }
      if (ClusterRoleBinding.clusterRoleBindingExists(grafanaReleaseName + "-clusterrolebinding")) {
        Kubernetes.deleteClusterRoleBinding(grafanaReleaseName + "-clusterrolebinding");
      }
      if (ClusterRoleBinding.clusterRoleBindingExists(prometheusReleaseName + "-alertmanager")) {
        Kubernetes.deleteClusterRoleBinding(prometheusReleaseName + "-alertmanager");
      }
      if (ClusterRoleBinding.clusterRoleBindingExists(prometheusReleaseName + "-kube-state-metrics")) {
        Kubernetes.deleteClusterRoleBinding(prometheusReleaseName + "-kube-state-metrics");
      }
      if (ClusterRoleBinding.clusterRoleBindingExists(prometheusReleaseName + "-server")) {
        Kubernetes.deleteClusterRoleBinding(prometheusReleaseName + "-server");
      }
      String command = KUBERNETES_CLI + " delete psp " + grafanaReleaseName + "  " + grafanaReleaseName + "-test";
      ExecCommand.exec(command);
    } catch (Exception ex) {
      //ignoring
      logger.info("getting exception during delete artifacts for grafana and prometheus");
    }
  }

  /**
   * Extra clean up for Prometheus Adapter artifacts.
   *
   */
  public static void cleanupPrometheusAdapterClusterRoles() {
    //extra cleanup
    String prometheusAdapterReleaseName = "prometheus-adapter";
    try {
      if (ClusterRole.clusterRoleExists(prometheusAdapterReleaseName + "-resource-reader")) {
        Kubernetes.deleteClusterRole(prometheusAdapterReleaseName + "-resource-reader");
      }
      if (ClusterRole.clusterRoleExists(prometheusAdapterReleaseName + "-server-resources")) {
        Kubernetes.deleteClusterRole(prometheusAdapterReleaseName + "-server-resources");
      }
      if (ClusterRoleBinding.clusterRoleBindingExists(prometheusAdapterReleaseName + "-hpa-controller")) {
        Kubernetes.deleteClusterRoleBinding(prometheusAdapterReleaseName + "-hpa-controller");
      }
      if (ClusterRoleBinding.clusterRoleBindingExists(prometheusAdapterReleaseName + "-resource-reader")) {
        Kubernetes.deleteClusterRoleBinding(prometheusAdapterReleaseName + "-resource-reader");
      }
      if (ClusterRoleBinding.clusterRoleBindingExists(prometheusAdapterReleaseName + "-system-auth-delegator")) {
        Kubernetes.deleteClusterRoleBinding(prometheusAdapterReleaseName + "-system-auth-delegator");
      }
      if (RoleBinding.roleBindingExists(prometheusAdapterReleaseName + "-auth-reader", "kube-system")) {
        Kubernetes.deleteNamespacedRoleBinding("kube-system", prometheusAdapterReleaseName + "-auth-reader");
      }

    } catch (Exception ex) {
      //ignoring
      logger.info("getting exception during delete artifacts for prometheus adapter");
    }
  }

  /**
   * Download src from monitoring exporter github project and build webapp.
   *
   * @param monitoringExporterDir full path to monitoring exporter install location
   */
  public static void installMonitoringExporter(String monitoringExporterDir) {

    String monitoringExporterSrcDir = Paths.get(monitoringExporterDir, "srcdir").toString();
    String monitoringExporterAppDir = Paths.get(monitoringExporterDir, "apps").toString();

    cloneMonitoringExporter(monitoringExporterSrcDir);
    Path monitoringApp = Paths.get(monitoringExporterAppDir);
    assertDoesNotThrow(() -> deleteDirectory(monitoringApp.toFile()));
    assertDoesNotThrow(() -> Files.createDirectories(monitoringApp));
    Path monitoringAppNoRestPort = Paths.get(monitoringExporterAppDir, "norestport");
    assertDoesNotThrow(() -> deleteDirectory(monitoringAppNoRestPort.toFile()));
    assertDoesNotThrow(() -> Files.createDirectories(monitoringAppNoRestPort));
    Path monitoringAppAdministrationRestPort = Paths.get(monitoringExporterAppDir, "administrationrestport");
    assertDoesNotThrow(() -> deleteDirectory(monitoringAppAdministrationRestPort.toFile()));
    assertDoesNotThrow(() -> Files.createDirectories(monitoringAppAdministrationRestPort));

    //adding ability to build monitoring exporter if branch is not main
    boolean toBuildMonitoringExporter = (!MONITORING_EXPORTER_BRANCH.equalsIgnoreCase(("main")));
    monitoringExporterAppDir = monitoringApp.toString();
    String monitoringExporterAppNoRestPortDir = monitoringAppNoRestPort.toString();
    String monitoringExporterAppAdministrationRestPortDir = monitoringAppAdministrationRestPort.toString();
    if (!toBuildMonitoringExporter) {
      downloadMonitoringExporterApp(RESOURCE_DIR
          + "/exporter/exporter-config.yaml", monitoringExporterAppDir);
      downloadMonitoringExporterApp(RESOURCE_DIR
          + "/exporter/exporter-config-norestport.yaml", monitoringExporterAppNoRestPortDir);
      downloadMonitoringExporterApp(RESOURCE_DIR
          + "/exporter/exporter-config-administrationrestport.yaml", monitoringExporterAppAdministrationRestPortDir);
    } else {
      buildMonitoringExporterApp(monitoringExporterSrcDir, RESOURCE_DIR
          + "/exporter/exporter-config.yaml", monitoringExporterAppDir);
      buildMonitoringExporterApp(monitoringExporterSrcDir,RESOURCE_DIR
          + "/exporter/exporter-config-norestport.yaml", monitoringExporterAppNoRestPortDir);
      buildMonitoringExporterApp(monitoringExporterSrcDir,RESOURCE_DIR
          + "/exporter/exporter-config-administrationrestport.yaml", monitoringExporterAppAdministrationRestPortDir);
    }
    logger.info("Finished to build Monitoring Exporter webapp.");
  }

  /**
   * Delete monitoring exporter dir.
   *
   * @param monitoringExporterDir full path to monitoring exporter install location
   */
  public static void deleteMonitoringExporterTempDir(String monitoringExporterDir) {
    logger.info("delete temp dir for monitoring exporter github");
    Path monitoringTemp = Paths.get(monitoringExporterDir, "srcdir");
    assertDoesNotThrow(() -> org.apache.commons.io.FileUtils.deleteDirectory(monitoringTemp.toFile()));
    Path monitoringApp = Paths.get(monitoringExporterDir, "apps");
    assertDoesNotThrow(() -> org.apache.commons.io.FileUtils.deleteDirectory(monitoringApp.toFile()));
    Path fileTemp = Paths.get(monitoringExporterDir, "../", "promCreateTempValueFile");
    assertDoesNotThrow(() -> org.apache.commons.io.FileUtils.deleteDirectory(fileTemp.toFile()));
  }

  /**
   * Create mii image with monitoring exporter webapp and one more app.
   * @param modelFilePath - path to model file
   * @param monexpAppDir - location for monitoring exporter webapp
   * @param appName  -extra app name
   * @param imageName - desired imagename
   */
  public static String createAndVerifyMiiImage(String monexpAppDir, String modelFilePath,
                                               String appName, String imageName) {
    // create image with model files
    logger.info("Create image with model file with monitoring exporter app and verify");
    String appPath = String.format("%s/wls-exporter.war", monexpAppDir);
    List<String> appList = new ArrayList<>();
    appList.add(appPath);
    appList.add(appName);

    // build the model file list
    final List<String> modelList = Collections.singletonList(modelFilePath);
    String myImage =
        createMiiImageAndVerify(imageName, modelList, appList);

    // login and push image to registry if necessary
    imageRepoLoginAndPushImageToRegistry(myImage);

    return myImage;
  }

  /**
   * Uninstall Prometheus and Grafana helm charts.
   *
   * @param promHelmParams  -helm chart params for prometheus
   * @param grafanaHelmParams - helm chart params for grafana
   */
  public static void uninstallPrometheusGrafana(HelmParams promHelmParams, GrafanaParams grafanaHelmParams) {
    String prometheusReleaseName = null;
    String grafanaReleaseName = null;
    if (promHelmParams != null) {
      prometheusReleaseName = promHelmParams.getReleaseName();
      Prometheus.uninstall(promHelmParams);
      logger.info("Prometheus is uninstalled");
    }
    if (grafanaHelmParams != null) {
      grafanaReleaseName = grafanaHelmParams.getHelmParams().getReleaseName();
      Grafana.uninstall(grafanaHelmParams.getHelmParams());
      deleteSecret("grafana-secret",grafanaHelmParams.getHelmParams().getNamespace());
      logger.info("Grafana is uninstalled");
    }
    cleanupPromGrafanaClusterRoles(prometheusReleaseName, grafanaReleaseName);
  }

  /**
   * Create Domain Cr and verity.
   *
   * @param adminSecretName WebLogic admin credentials
   * @param repoSecretName image repository secret name
   * @param encryptionSecretName model encryption secret name
   * @param miiImage model in image name
   * @param domainUid domain uid
   * @param namespace namespace
   * @param domainHomeSource domain home source type
   * @param replicaCount replica count for the cluster
   * @param twoClusters boolean indicating if the domain has 2 clusters
   * @param monexpConfig monitoring exporter config
   * @param exporterImage exporter image
   */
  public static void createDomainCrAndVerify(String adminSecretName,
                                             String repoSecretName,
                                             String encryptionSecretName,
                                             String miiImage,
                                             String domainUid,
                                             String namespace,
                                             String domainHomeSource,
                                             int replicaCount,
                                             boolean twoClusters,
                                             String monexpConfig,
                                             String exporterImage) {
    int t3ChannelPort = getNextFreePort();

    // create the domain CR
    DomainResource domain = new DomainResource()
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
            .webLogicCredentialsSecret(new V1LocalObjectReference()
                .name(adminSecretName))
            .includeServerOutInPodLog(true)
            .serverStartPolicy("IfNeeded")
            .failureRetryIntervalSeconds(FAILURE_RETRY_INTERVAL_SECONDS)
            .failureRetryLimitMinutes(FAILURE_RETRY_LIMIT_MINUTES)
            .serverPod(new ServerPod()
                .addEnvItem(new V1EnvVar()
                    .name("JAVA_OPTIONS")
                    .value("-Dweblogic.StdoutDebugEnabled=false "
                        + "-Dweblogic.security.SSL.ignoreHostnameVerification=true "))
                .addEnvItem(new V1EnvVar()
                    .name("USER_MEM_ARGS")
                    .value("-Djava.security.egd=file:/dev/./urandom ")))
            .adminServer(new AdminServer()
                .adminService(new AdminService()
                    .addChannelsItem(new Channel()
                        .channelName("default")
                        .nodePort(0))
                    .addChannelsItem(new Channel()
                        .channelName("T3Channel")
                        .nodePort(t3ChannelPort))))
            .configuration(new Configuration()
                .model(new Model()
                    .domainType("WLS")
                    .runtimeEncryptionSecret(encryptionSecretName))
                .introspectorJobActiveDeadlineSeconds(300L)));

    // add clusters to the domain resource
    ClusterList clusters = Cluster.listClusterCustomResources(namespace);
    String[] clusterNames = twoClusters ? new String[]{cluster1Name, cluster2Name} : new String[]{cluster1Name};
    for (String clusterName : clusterNames) {
      String clusterResName = domainUid + "-" + clusterName;
      if (clusters.getItems().stream().anyMatch(cluster -> cluster.getClusterResourceName().equals(clusterResName))) {
        getLogger().info("!!!Cluster resource {0} in namespace {1} already exists, skipping...",
            clusterResName, namespace);
      } else {
        getLogger().info("Creating cluster resource {0} in namespace {1}", clusterResName, namespace);
        createClusterAndVerify(createClusterResource(clusterResName, clusterName, namespace, replicaCount));
      }
      // set cluster references
      domain.getSpec().withCluster(new V1LocalObjectReference().name(clusterResName));
    }
    
    setPodAntiAffinity(domain);
    // create domain using model in image
    logger.info("Create model in image domain {0} in namespace {1} using image {2}",
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

      domain.getSpec().monitoringExporter(new MonitoringExporterSpecification()
          .image(exporterImage)
          .imagePullPolicy(IMAGE_PULL_POLICY)
          .configuration(contents));

      logger.info("Created domain CR with Monitoring exporter configuration : "
          + domain.getSpec().getMonitoringExporter().toString());
    }
    createDomainAndVerify(domain, namespace);
  }
  
  /**
   * create domain from provided image and monitoring exporter sidecar and verify it's start.
   *
   * @param miiImage model in image name
   * @param domainUid domain uid
   * @param namespace namespace
   * @param domainHomeSource domain home source type
   * @param replicaCount replica count for the cluster
   * @param twoClusters boolean indicating if the domain has 2 clusters
   * @param monexpConfig monitoring exporter config
   * @param exporterImage exporter image
   */
  public static void createAndVerifyDomain(String miiImage,
                                            String domainUid,
                                            String namespace,
                                            String domainHomeSource,
                                            int replicaCount,
                                            boolean twoClusters,
                                            String monexpConfig,
                                            String exporterImage) {
    // create registry secret to pull the image from registry
    // this secret is used only for non-kind cluster
    // create secret for admin credentials
    logger.info("Create registry secret in namespace {0}", namespace);
    createTestRepoSecret(namespace);
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
    logger.info("Create model in image domain {0} in namespace {1} using image {2}",
        domainUid, namespace, miiImage);
    createDomainCrAndVerify(adminSecretName, TEST_IMAGES_REPO_SECRET_NAME, encryptionSecretName, miiImage,domainUid,
        namespace, domainHomeSource, replicaCount, twoClusters, monexpConfig, exporterImage);
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

  /**
   * Install wls dashboard from endtoend sample and verify it is accessable.
   *
   * @param hostPort  host:nodeport string for grafana
   * @param monitoringExporterEndToEndDir endtoend sample directory
   *
   */
  //public static void installVerifyGrafanaDashBoard(int nodeportGrafana, String monitoringExporterEndToEndDir) {
  public static void installVerifyGrafanaDashBoard(String hostPort, String monitoringExporterEndToEndDir) {
    //wait until it starts dashboard
    String curlCmd = String.format("curl -v  -H 'Content-Type: application/json' "
            + " -X GET http://admin:12345678@%s/api/dashboards",
        hostPort);
    testUntil(
        assertDoesNotThrow(() -> searchForKey(curlCmd, "grafana"), "Check access to grafana dashboard"),
        logger,
        "Check access to grafana dashboard");
    logger.info("installing grafana dashboard");
    // url
    String curlCmd0 =
        String.format("curl -v -H 'Content-Type: application/json' -H \"Content-Type: application/json\""
                + "  -X POST http://admin:12345678@%s/api/datasources/"
                + "  --data-binary @%s/grafana/datasource.json",
            hostPort, monitoringExporterEndToEndDir);

    logger.info("Executing Curl cmd {0}", curlCmd);
    assertDoesNotThrow(() -> ExecCommand.exec(curlCmd0));

    String curlCmd1 =
        String.format("curl -v -H 'Content-Type: application/json' -H \"Content-Type: application/json\""
                + "  -X POST http://admin:12345678@%s/api/dashboards/db/"
                + "  --data-binary @%s/grafana/dashboard.json",
            hostPort, monitoringExporterEndToEndDir);
    logger.info("Executing Curl cmd {0}", curlCmd1);
    assertDoesNotThrow(() -> ExecCommand.exec(curlCmd1));

    String curlCmd2 = String.format("curl -v  -H 'Content-Type: application/json' "
            + " -X GET http://admin:12345678@%s/api/dashboards/db/weblogic-server-dashboard",
        hostPort);
    testUntil(
        assertDoesNotThrow(() -> searchForKey(curlCmd2, "wls_jvm_uptime"),
            String.format("Check grafana dashboard wls against expected %s", "wls_jvm_uptime")),
        logger,
        "Check grafana dashboard metric against expected wls_jvm_uptime");
  }

  /**
   * Verify the monitoring exporter app can be accessed from all managed servers in the domain through NGINX.
   *
   * @param nginxHost nginx host name
   * @param replicaCount number of managed servers
   * @param nodeport  nginx nodeport
   */
  public static void verifyMonExpAppAccessThroughNginx(String nginxHost, int replicaCount, int nodeport) {

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
            nodeport);
    testUntil(withLongRetryPolicy,
        callTestWebAppAndCheckForServerNameInResponse(curlCmd, managedServerNames, 50),
        logger,
        "Verify NGINX can access the monitoring exporter metrics \n"
            + "from all managed servers in the domain via http");
  }

  /**
   * Verify the monitoring exporter app can be accessed from all managed servers in the domain through NGINX.
   *
   * @param replicaCount number of managed servers
   * @param hostPort  host:port combination to access app
   */
  public static void verifyMonExpAppAccess(int replicaCount, String hostPort) {

    List<String> managedServerNames = new ArrayList<>();
    for (int i = 1; i <= replicaCount; i++) {
      managedServerNames.add(MANAGED_SERVER_NAME_BASE + i);
    }

    // check the access to monitoring exporter apps from all managed servers in the domain
    String curlCmd =
        String.format("curl --silent --show-error --noproxy '*'  http://%s:%s@%s/wls-exporter/metrics",
            ADMIN_USERNAME_DEFAULT,
            ADMIN_PASSWORD_DEFAULT,
            hostPort);
    assertThat(callWebAppAndCheckForServerNameInResponse(curlCmd, managedServerNames, 50))
        .as("Verify the access to the monitoring exporter metrics "
            + "from all managed servers in the domain via http")
        .withFailMessage("Can not access the monitoring exporter metrics "
            + "from one or more of the managed servers via http")
        .isTrue();
  }

  /**
   * Verify the monitoring exporter app can be accessed from all managed servers in the domain
   * through direct access to managed server dashboard.
   * @param clusterName - name of cluster
   * @param domainNS - domain namespace
   * @param domainUid  - domain uid
   * @param isHttps  - protocol
   * @param uri - weburl
   * @param searchKey  - search key in response
   */
  public static boolean verifyMonExpAppAccess(String uri, String searchKey, String domainUid,
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
        KUBERNETES_CLI + " exec -n " + domainNS + "  " + podName + " -- curl -k %s://"
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
      logger.info("Can't execute command " + command + Arrays.toString(ex.getStackTrace()));
      return false;
    }
    return isFound;
  }

  /**
   * Verify the monitoring exporter sidecar can be accessed from all managed servers in the domain
   * through direct access to managed server dashboard.
   * @param domainNS - domain namespace
   * @param podName  - managed server pod name
   * @param searchKey  - search key in response
   */
  public static boolean verifyMonExpAppAccessSideCar(String searchKey,
                                              String domainNS, String podName) {

    // access metrics
    final String command = String.format(
        KUBERNETES_CLI + " exec -n " + domainNS + "  " + podName + " -- curl -X GET -u %s:%s http://localhost:8080/metrics", ADMIN_USERNAME_DEFAULT,
        ADMIN_PASSWORD_DEFAULT);

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
      logger.info("Can't execute command " + command + Arrays.toString(ex.getStackTrace()));
      return false;
    }
    return isFound;
  }

  /** To build monitoring exporter sidecar image.
   *
   * @param imageName image nmae
   * @param monitoringExporterSrcDir path to monitoring exporter src location
   */
  public static void buildMonitoringExporterImage(String imageName, String monitoringExporterSrcDir) {
    String httpsproxy = HTTPS_PROXY;
    logger.info(" httpsproxy : " + httpsproxy);
    String proxyHost = "";
    String command;
    if (httpsproxy != null) {
      int firstIndex = httpsproxy.lastIndexOf("www");
      int lastIndex = httpsproxy.lastIndexOf(":");
      logger.info("Got indexes : " + firstIndex + " : " + lastIndex);
      proxyHost = httpsproxy.substring(firstIndex,lastIndex);
      logger.info(" proxyHost: " + proxyHost);

      command = String.format("cd %s && mvn clean install -Dmaven.test.skip=true "
              + " &&   " + WLSIMG_BUILDER + " build . -t "
              + imageName
              + " --build-arg MAVEN_OPTS=\"-Dhttps.proxyHost=%s -Dhttps.proxyPort=80\" --build-arg https_proxy=%s",
          monitoringExporterSrcDir, proxyHost, httpsproxy);
    } else {
      command = String.format("cd %s && mvn clean install -Dmaven.test.skip=true "
          + " &&   " + WLSIMG_BUILDER + " build . -t "
          + imageName
          + monitoringExporterSrcDir);
    }
    logger.info("Executing command " + command);
    assertTrue(Command
        .withParams(new CommandParams()
            .command(command))
        .execute(), "Failed to build monitoring exporter image");
    // login and push image to registry if necessary
    imageRepoLoginAndPushImageToRegistry(imageName);
  }

  /** Change monitoring exporter webapp confiuration inside the pod.
   *
   * @param podName pod name
   * @param namespace pod namespace
   * @param configYaml monitorin exporter configuration
   */
  public static void changeConfigInPod(String podName, String namespace, String configYaml) {
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

}
