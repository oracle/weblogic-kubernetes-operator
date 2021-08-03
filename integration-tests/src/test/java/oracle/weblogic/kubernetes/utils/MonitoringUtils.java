// Copyright (c) 2021, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.weblogic.kubernetes.utils;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Callable;

import io.kubernetes.client.openapi.ApiException;
import io.kubernetes.client.openapi.models.V1ConfigMap;
import io.kubernetes.client.openapi.models.V1Secret;
import io.kubernetes.client.openapi.models.V1SecretList;
import oracle.weblogic.kubernetes.actions.impl.GrafanaParams;
import oracle.weblogic.kubernetes.actions.impl.PrometheusParams;
import oracle.weblogic.kubernetes.actions.impl.primitive.Command;
import oracle.weblogic.kubernetes.actions.impl.primitive.CommandParams;
import oracle.weblogic.kubernetes.actions.impl.primitive.HelmParams;
import oracle.weblogic.kubernetes.actions.impl.primitive.Kubernetes;
import oracle.weblogic.kubernetes.logging.LoggingFacade;
import org.awaitility.core.ConditionFactory;

import static java.util.concurrent.TimeUnit.MINUTES;
import static java.util.concurrent.TimeUnit.SECONDS;
import static oracle.weblogic.kubernetes.TestConstants.GRAFANA_REPO_NAME;
import static oracle.weblogic.kubernetes.TestConstants.GRAFANA_REPO_URL;
import static oracle.weblogic.kubernetes.TestConstants.K8S_NODEPORT_HOST;
import static oracle.weblogic.kubernetes.TestConstants.MONITORING_EXPORTER_WEBAPP_VERSION;
import static oracle.weblogic.kubernetes.TestConstants.PROMETHEUS_REPO_NAME;
import static oracle.weblogic.kubernetes.TestConstants.PROMETHEUS_REPO_URL;
import static oracle.weblogic.kubernetes.actions.ActionConstants.MONITORING_EXPORTER_DOWNLOAD_URL;
import static oracle.weblogic.kubernetes.actions.ActionConstants.RESOURCE_DIR;
import static oracle.weblogic.kubernetes.actions.TestActions.installGrafana;
import static oracle.weblogic.kubernetes.actions.TestActions.installPrometheus;
import static oracle.weblogic.kubernetes.actions.TestActions.uninstallGrafana;
import static oracle.weblogic.kubernetes.actions.impl.primitive.Kubernetes.listSecrets;
import static oracle.weblogic.kubernetes.assertions.TestAssertions.isGrafanaReady;
import static oracle.weblogic.kubernetes.assertions.TestAssertions.isHelmReleaseDeployed;
import static oracle.weblogic.kubernetes.assertions.TestAssertions.isPrometheusReady;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.createSecretWithUsernamePassword;
import static oracle.weblogic.kubernetes.utils.FileUtils.checkDirectory;
import static oracle.weblogic.kubernetes.utils.FileUtils.checkFile;
import static oracle.weblogic.kubernetes.utils.TestUtils.getNextFreePort;
import static oracle.weblogic.kubernetes.utils.ThreadSafeLogger.getLogger;
import static org.apache.commons.io.FileUtils.deleteDirectory;
import static org.awaitility.Awaitility.with;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A utility class for Monitoring Weblogic Domain via Weblogic MonitoringExporter and Prometheus.
 */
public class MonitoringUtils {
  /**
   * Download monitoring exporter webapp wls-exporter.war based on provided version
   * and insert provided configuration.
   * @param configFile configuration to monitor Weblogic Domain
   * @param applicationDir location where application war file will be created
   */
  public static void downloadMonitoringExporterApp(String configFile, String applicationDir) {
    LoggingFacade logger = getLogger();
    //version of wls-exporter.war published in https://github.com/oracle/weblogic-monitoring-exporter/releases/
    String monitoringExporterWebAppVersion = Optional.ofNullable(System.getenv("MONITORING_EXPORTER_WEBAPP_VERSION"))
        .orElse(MONITORING_EXPORTER_WEBAPP_VERSION);

    String monitoringExporterBuildFile = String.format(
        "%s/get%s.sh", applicationDir, monitoringExporterWebAppVersion);
    checkDirectory(applicationDir);
    logger.info("Download a monitoring exporter build file {0} ", monitoringExporterBuildFile);
    String monitoringExporterRelease =
        monitoringExporterWebAppVersion.equals("2.0") ? "2.0.0" : monitoringExporterWebAppVersion;
    String curlDownloadCmd = String.format("cd %s && "
            + "curl -O -L -k https://github.com/oracle/weblogic-monitoring-exporter/releases/download/v%s/get%s.sh",
        applicationDir,
        monitoringExporterRelease,
        monitoringExporterWebAppVersion);
    logger.info("execute command  a monitoring exporter curl command {0} ", curlDownloadCmd);
    assertTrue(new Command()
        .withParams(new CommandParams()
            .command(curlDownloadCmd))
        .execute(), "Failed to download monitoring exporter webapp");
    String command = String.format("chmod 777 %s ", monitoringExporterBuildFile);
    assertTrue(new Command()
        .withParams(new CommandParams()
            .command(command))
        .execute(), "Failed to download monitoring exporter webapp");

    command = String.format("cd %s && %s  %s",
        applicationDir,
        monitoringExporterBuildFile,
        configFile);
    assertTrue(new Command()
        .withParams(new CommandParams()
            .command(command))
        .execute(), "Failed to build monitoring exporter webapp");
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
        RESOURCE_DIR,
        configFile);
    LoggingFacade logger = getLogger();
    logger.info("Executing command " + command);
    Path srcFile = Paths.get(monitoringExporterSrcDir,
        "target", "wls-exporter.war");

    assertTrue(new Command()
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

    String monitoringExporterBranch = Optional.ofNullable(System.getenv("MONITORING_EXPORTER_BRANCH"))
        .orElse("master");
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
   * @param prometheusPort prometheusPort
   * @throws Exception if command to check metrics fails
   */
  public static void checkMetricsViaPrometheus(String searchKey, String expectedVal, int prometheusPort)
      throws Exception {

    LoggingFacade logger = getLogger();
    // url
    String curlCmd =
        String.format("curl --silent --show-error --noproxy '*'  http://%s:%s/api/v1/query?query=%s",
            K8S_NODEPORT_HOST, prometheusPort, searchKey);

    logger.info("Executing Curl cmd {0}", curlCmd);
    logger.info("Checking searchKey: {0}", searchKey);
    logger.info(" expected Value {0} ", expectedVal);
    ConditionFactory withStandardRetryPolicy = null;
    // create standard, reusable retry/backoff policy
    withStandardRetryPolicy = with().pollDelay(2, SECONDS)
        .and().with().pollInterval(10, SECONDS)
        .atMost(5, MINUTES).await();
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
   * @param promReleaseName the prometheus release name
   * @param promNamespace the prometheus namespace in which the operator will be installed
   * @param promValueFile the promeheus value.yaml file path
   * @param promVersion the version of the prometheus helm chart
   * @param promServerNodePort nodePort value for prometheus server
   * @param alertManagerNodePort nodePort value for alertmanager
   * @return the prometheus Helm installation parameters
   */
  public static HelmParams installAndVerifyPrometheus(String promReleaseName,
                                                      String promNamespace,
                                                      String promValueFile,
                                                      String promVersion,
                                                      int promServerNodePort,
                                                      int alertManagerNodePort) {
    LoggingFacade logger = getLogger();
    // Helm install parameters
    HelmParams promHelmParams = new HelmParams()
        .releaseName(promReleaseName)
        .namespace(promNamespace)
        .repoUrl(PROMETHEUS_REPO_URL)
        .repoName(PROMETHEUS_REPO_NAME)
        .chartName("prometheus")
        .chartValuesFile(promValueFile);

    if (promVersion != null) {
      promHelmParams.chartVersion(promVersion);
    }

    // prometheus chart values to override
    PrometheusParams prometheusParams = new PrometheusParams()
        .helmParams(promHelmParams)
        .nodePortServer(promServerNodePort)
        .nodePortAlertManager(alertManagerNodePort);

    // install prometheus
    logger.info("Installing prometheus in namespace {0}", promNamespace);
    assertTrue(installPrometheus(prometheusParams),
        String.format("Failed to install prometheus in namespace %s", promNamespace));
    logger.info("Prometheus installed in namespace {0}", promNamespace);

    // list Helm releases matching operator release name in operator namespace
    logger.info("Checking prometheus release {0} status in namespace {1}",
        promReleaseName, promNamespace);
    assertTrue(isHelmReleaseDeployed(promReleaseName, promNamespace),
        String.format("Prometheus release %s is not in deployed status in namespace %s",
            promReleaseName, promNamespace));
    logger.info("Prometheus release {0} status is deployed in namespace {1}",
        promReleaseName, promNamespace);

    // wait for the promethues pods to be ready
    logger.info("Wait for the promethues pod is ready in namespace {0}", promNamespace);
    CommonTestUtils.withStandardRetryPolicy
        .conditionEvaluationListener(
            condition -> logger.info("Waiting for prometheus to be running in namespace {0} "
                    + "(elapsed time {1}ms, remaining time {2}ms)",
                promNamespace,
                condition.getElapsedTimeInMS(),
                condition.getRemainingTimeInMS()))
        .until(assertDoesNotThrow(() -> isPrometheusReady(promNamespace),
            "prometheusIsReady failed with ApiException"));

    return promHelmParams;
  }

  /**
   * Install Grafana and wait up to five minutes until the grafana pod is ready.
   *
   * @param grafanaReleaseName the grafana release name
   * @param grafanaNamespace the grafana namespace in which the operator will be installed
   * @param grafanaValueFile the grafana value.yaml file path
   * @param grafanaVersion the version of the grafana helm chart
   * @return the grafana Helm installation parameters
   */
  public static GrafanaParams installAndVerifyGrafana(String grafanaReleaseName,
                                                      String grafanaNamespace,
                                                      String grafanaValueFile,
                                                      String grafanaVersion) {
    LoggingFacade logger = getLogger();
    // Helm install parameters
    HelmParams grafanaHelmParams = new HelmParams()
        .releaseName(grafanaReleaseName)
        .namespace(grafanaNamespace)
        .repoUrl(GRAFANA_REPO_URL)
        .repoName(GRAFANA_REPO_NAME)
        .chartName("grafana")
        .chartValuesFile(grafanaValueFile);

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
    try {
      assertTrue(installGrafana(grafanaParams),
          String.format("Failed to install grafana in namespace %s", grafanaNamespace));
    } catch (AssertionError err) {
      //retry with different nodeport
      uninstallGrafana(grafanaHelmParams);
      grafanaNodePort = getNextFreePort();
      grafanaParams = new GrafanaParams()
          .helmParams(grafanaHelmParams)
          .nodePort(grafanaNodePort);
      isGrafanaInstalled = installGrafana(grafanaParams);
      if (!isGrafanaInstalled) {
        //clean up
        logger.info(String.format("Failed to install grafana in namespace %s with nodeport %s",
            grafanaNamespace, grafanaNodePort));
        uninstallGrafana(grafanaHelmParams);
        return null;
      }
    }
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
    CommonTestUtils.withStandardRetryPolicy
        .conditionEvaluationListener(
            condition -> logger.info("Waiting for grafana to be running in namespace {0} "
                    + "(elapsed time {1}ms, remaining time {2}ms)",
                grafanaNamespace,
                condition.getElapsedTimeInMS(),
                condition.getRemainingTimeInMS()))
        .until(assertDoesNotThrow(() -> isGrafanaReady(grafanaNamespace),
            "grafanaIsReady failed with ApiException"));

    //return grafanaHelmParams;
    return grafanaParams;
  }


}
