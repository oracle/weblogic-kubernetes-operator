// Copyright (c) 2021, 2025, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.weblogic.kubernetes.utils;

import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import io.kubernetes.client.openapi.ApiException;
import io.kubernetes.client.openapi.models.V1ConfigMap;
import io.kubernetes.client.openapi.models.V1EnvVar;
import io.kubernetes.client.openapi.models.V1LocalObjectReference;
import io.kubernetes.client.openapi.models.V1ObjectMeta;
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
import oracle.weblogic.kubernetes.TestConstants;
import oracle.weblogic.kubernetes.actions.ActionConstants;
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
import static oracle.weblogic.kubernetes.TestConstants.BASE_IMAGES_REPO_PASSWORD;
import static oracle.weblogic.kubernetes.TestConstants.BASE_IMAGES_REPO_USERNAME;
import static oracle.weblogic.kubernetes.TestConstants.BUSYBOX_IMAGE;
import static oracle.weblogic.kubernetes.TestConstants.BUSYBOX_TAG;
import static oracle.weblogic.kubernetes.TestConstants.DOMAIN_API_VERSION;
import static oracle.weblogic.kubernetes.TestConstants.FAILURE_RETRY_INTERVAL_SECONDS;
import static oracle.weblogic.kubernetes.TestConstants.FAILURE_RETRY_LIMIT_MINUTES;
import static oracle.weblogic.kubernetes.TestConstants.GRAFANA_IMAGE_NAME;
import static oracle.weblogic.kubernetes.TestConstants.GRAFANA_IMAGE_TAG;
import static oracle.weblogic.kubernetes.TestConstants.GRAFANA_REPO_NAME;
import static oracle.weblogic.kubernetes.TestConstants.GRAFANA_REPO_URL;
import static oracle.weblogic.kubernetes.TestConstants.IMAGE_PULL_POLICY;
import static oracle.weblogic.kubernetes.TestConstants.K8S_NODEPORT_HOST;
import static oracle.weblogic.kubernetes.TestConstants.KIND_CLUSTER;
import static oracle.weblogic.kubernetes.TestConstants.KUBERNETES_CLI;
import static oracle.weblogic.kubernetes.TestConstants.MANAGED_SERVER_NAME_BASE;
import static oracle.weblogic.kubernetes.TestConstants.MONITORING_EXPORTER_BRANCH;
import static oracle.weblogic.kubernetes.TestConstants.MONITORING_EXPORTER_WEBAPP_VERSION;
import static oracle.weblogic.kubernetes.TestConstants.OKD;
import static oracle.weblogic.kubernetes.TestConstants.OKE_CLUSTER;
import static oracle.weblogic.kubernetes.TestConstants.OKE_CLUSTER_PRIVATEIP;
import static oracle.weblogic.kubernetes.TestConstants.PROMETHEUS_ALERT_MANAGER_IMAGE_NAME;
import static oracle.weblogic.kubernetes.TestConstants.PROMETHEUS_ALERT_MANAGER_IMAGE_TAG;
import static oracle.weblogic.kubernetes.TestConstants.PROMETHEUS_CONFIG_MAP_RELOAD_IMAGE_NAME;
import static oracle.weblogic.kubernetes.TestConstants.PROMETHEUS_CONFIG_MAP_RELOAD_IMAGE_TAG;
import static oracle.weblogic.kubernetes.TestConstants.PROMETHEUS_IMAGE_NAME;
import static oracle.weblogic.kubernetes.TestConstants.PROMETHEUS_IMAGE_TAG;
import static oracle.weblogic.kubernetes.TestConstants.PROMETHEUS_NODE_EXPORTER_IMAGE_NAME;
import static oracle.weblogic.kubernetes.TestConstants.PROMETHEUS_NODE_EXPORTER_IMAGE_TAG;
import static oracle.weblogic.kubernetes.TestConstants.PROMETHEUS_PUSHGATEWAY_IMAGE_NAME;
import static oracle.weblogic.kubernetes.TestConstants.PROMETHEUS_PUSHGATEWAY_IMAGE_TAG;
import static oracle.weblogic.kubernetes.TestConstants.PROMETHEUS_REPO_NAME;
import static oracle.weblogic.kubernetes.TestConstants.PROMETHEUS_REPO_URL;
import static oracle.weblogic.kubernetes.TestConstants.RESULTS_ROOT;
import static oracle.weblogic.kubernetes.TestConstants.TEST_IMAGES_REPO_SECRET_NAME;
import static oracle.weblogic.kubernetes.TestConstants.WLSIMG_BUILDER;
import static oracle.weblogic.kubernetes.TestConstants.WLSIMG_BUILDER_DEFAULT;
import static oracle.weblogic.kubernetes.actions.ActionConstants.MONITORING_EXPORTER_DOWNLOAD_URL;
import static oracle.weblogic.kubernetes.actions.ActionConstants.RESOURCE_DIR;
import static oracle.weblogic.kubernetes.actions.TestActions.deleteSecret;
import static oracle.weblogic.kubernetes.actions.TestActions.imageRepoLogin;
import static oracle.weblogic.kubernetes.actions.TestActions.installGrafana;
import static oracle.weblogic.kubernetes.actions.TestActions.installPrometheus;
import static oracle.weblogic.kubernetes.actions.impl.primitive.Kubernetes.listSecrets;
import static oracle.weblogic.kubernetes.assertions.TestAssertions.callTestWebAppAndCheckForServerNameInResponse;
import static oracle.weblogic.kubernetes.assertions.TestAssertions.isGrafanaReady;
import static oracle.weblogic.kubernetes.assertions.TestAssertions.isHelmReleaseDeployed;
import static oracle.weblogic.kubernetes.assertions.TestAssertions.isPrometheusAdapterReady;
import static oracle.weblogic.kubernetes.assertions.TestAssertions.isPrometheusReady;
import static oracle.weblogic.kubernetes.utils.ApplicationUtils.callWebAppAndCheckForServerNameInResponse;
import static oracle.weblogic.kubernetes.utils.ApplicationUtils.callWebAppAndWaitTillReady;
import static oracle.weblogic.kubernetes.utils.ClusterUtils.createClusterAndVerify;
import static oracle.weblogic.kubernetes.utils.ClusterUtils.createClusterResource;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.addSccToDBSvcAccount;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.checkPodReadyAndServiceExists;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.formatIPv6Host;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.getNextFreePort;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.testUntil;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.withLongRetryPolicy;
import static oracle.weblogic.kubernetes.utils.DomainUtils.createDomainAndVerify;
import static oracle.weblogic.kubernetes.utils.FileUtils.checkFile;
import static oracle.weblogic.kubernetes.utils.FileUtils.replaceStringInFile;
import static oracle.weblogic.kubernetes.utils.ImageUtils.createImageAndPushToRepo;
import static oracle.weblogic.kubernetes.utils.ImageUtils.createMiiImageAndVerify;
import static oracle.weblogic.kubernetes.utils.ImageUtils.createTestRepoSecret;
import static oracle.weblogic.kubernetes.utils.ImageUtils.imageRepoLoginAndPushImageToRegistry;
import static oracle.weblogic.kubernetes.utils.PodUtils.checkPodExists;
import static oracle.weblogic.kubernetes.utils.PodUtils.checkPodReady;
import static oracle.weblogic.kubernetes.utils.PodUtils.getPodName;
import static oracle.weblogic.kubernetes.utils.PodUtils.setPodAntiAffinity;
import static oracle.weblogic.kubernetes.utils.SecretUtils.createSecretWithUsernamePassword;
import static oracle.weblogic.kubernetes.utils.ThreadSafeLogger.getLogger;
import static org.apache.commons.io.FileUtils.deleteDirectory;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A utility class for Monitoring WebLogic Domain via WebLogic MonitoringExporter and Prometheus.
 */
public class MonitoringUtils {

  private static LoggingFacade logger = getLogger();
  private static String cluster1Name = "cluster-1";
  private static String cluster2Name = "cluster-2";

  /**
   * Download monitoring exporter webapp wls-exporter.war based on provided version
   * and insert provided configuration.
   *
   * @param configFile     configuration to monitor Weblogic Domain
   * @param applicationDir location where application war file will be created
   */
  public static void downloadMonitoringExporterApp(String configFile, String applicationDir) {
    //version of wls-exporter.war published in https://github.com/oracle/weblogic-monitoring-exporter/releases/
    String monitoringExporterRelease = MONITORING_EXPORTER_WEBAPP_VERSION;
    String curlDownloadCmd = String.format("cd %s && "
            + "curl -O -L -k https://github.com/oracle/weblogic-monitoring-exporter/releases/download/v%s/get_v%s.sh",
        applicationDir,
        monitoringExporterRelease,
        monitoringExporterRelease);
    String monitoringExporterBuildFile = String.format(
        "%s/get_v%s.sh", applicationDir, monitoringExporterRelease);
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
   * Build monitoring exporter web applicaiont wls-exporter.war with provided configuration.
   *
   * @param monitoringExporterSrcDir directory containing github monitoring exporter
   * @param configFile               configuration file for weblogic domain monitoring
   * @param appDir                   directory where war file will be created
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
   *
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
   * @param searchKey          - metric query expression
   * @param expectedVal        - expected metrics to search
   * @param hostPortPrometheus host:nodePort for prometheus
   * @throws Exception if command to check metrics fails
   */
  public static void checkMetricsViaPrometheus(String searchKey, String expectedVal,
                                               String hostPortPrometheus)
      throws Exception {

    LoggingFacade logger = getLogger();
    // url
    String curlCmd =
        String.format("curl -g --silent --show-error --noproxy '*' -v "
                + " --max-time 60 -H 'host: *'"
                + " http://%s/api/v1/query?query=%s",
            hostPortPrometheus, searchKey);

    logger.info("Executing Curl cmd {0}", curlCmd);
    logger.info("Checking searchKey: {0}", searchKey);
    logger.info(" expected Value {0} ", expectedVal);
    if (OKE_CLUSTER) {
      try {
        if (!callWebAppAndWaitTillReady(curlCmd, 5)) {
          ExecResult result = ExecCommand.exec(KUBERNETES_CLI + " get all -A");
          logger.info(result.stdout());
          //restart core-dns service
          result = ExecCommand.exec(KUBERNETES_CLI + " rollout restart deployment coredns -n kube-system");
          logger.info(result.stdout());
          checkPodReady("coredns", null, "kube-system");
          result = ExecCommand.exec(curlCmd);
          logger.info(result.stdout());
        }
      } catch (Exception ex) {
        logger.warning(ex.getLocalizedMessage());
      }
    }
    testUntil(
        searchForKey(curlCmd, expectedVal),
        logger,
        "Check prometheus metric {0} against expected {1}",
        searchKey,
        expectedVal);
  }

  /**
   * Check metrics using Prometheus.
   *
   * @param searchKey          - metric query expression
   * @param expectedVal        - expected metrics to search
   * @param hostPortPrometheus host:nodePort for prometheus
   * @throws Exception if command to check metrics fails
   */
  public static void checkMetricsViaPrometheus(String searchKey, String expectedVal,
                                               String hostPortPrometheus, String ingressHost)
      throws Exception {

    LoggingFacade logger = getLogger();
    // url
    String curlCmd =
        String.format("curl -g --silent --show-error --noproxy '*' -v  -H 'host: " + ingressHost + "'"
                + " http://%s/api/v1/query?query=%s",
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
   * @param cmd       command
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
   * @param cmd       command to execute
   * @param searchKey expected output
   * @return true if the output matches searchKey otherwise false
   */
  public static Callable<Boolean> searchForKey(String cmd, String searchKey) {
    return () -> execCommandCheckResponse(cmd, searchKey);
  }

  /**
   * Edit Prometheus Config Map.
   *
   * @param oldRegex     search for existed value to replace
   * @param newRegex     new value
   * @param prometheusNS namespace for prometheus pod
   * @param cmName       name of Config Map to modify
   * @throws ApiException when update fails
   */
  public static void editPrometheusCM(String oldRegex, String newRegex,
                                      String prometheusNS, String cmName) throws ApiException {
    List<V1ConfigMap> cmList = Kubernetes.listConfigMaps(prometheusNS).getItems();
    V1ConfigMap promCm = cmList.stream()
        .filter(cm -> cm.getMetadata() != null && cmName.equals(cm.getMetadata().getName()))
        .findAny()
        .orElse(null);

    assertNotNull(promCm, "Can't find cm for " + cmName);
    Map<String, String> cmData = promCm.getData();
    String values = cmData.get("prometheus.yml").replace(oldRegex, newRegex);
    assertNotNull(values, "can't find values for key prometheus.yml");
    cmData.replace("prometheus.yml", values);

    promCm.setData(cmData);
    Kubernetes.replaceConfigMap(promCm);

    cmList = Kubernetes.listConfigMaps(prometheusNS).getItems();

    promCm = cmList.stream()
        .filter(cm -> cmName.equals(cm.getMetadata().getName()))
        .findAny()
        .orElse(null);

    assertNotNull(promCm, "Can't find cm for " + cmName);
    assertNotNull(promCm.getData(), "Can't retreive the cm data for " + cmName + " after modification");

  }

  /**
   * Install Prometheus and wait up to five minutes until the prometheus pods are ready.
   *
   * @param promReleaseSuffix    the prometheus release name unique suffix
   * @param promNamespace        the prometheus namespace in which the prometheus will be installed
   * @param promVersion          the version of the prometheus helm chart
   * @param prometheusRegexValue string (namespace;domainuid) to manage specific domain,
   *                             default is regex: default;domain1
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
   * @param promReleaseSuffix     the prometheus release name unigue suffix
   * @param promNamespace         the prometheus namespace in which the operator will be installed
   * @param promVersion           the version of the prometheus helm chart
   * @param prometheusRegexValue  string (namespace;domainuid) to manage specific domain,
   *                              default is regex: default;domain1
   * @param promHelmValuesFileDir path to prometheus helm values file directory
   * @param webhookNS             namespace for webhook namespace
   * @param ports                 optional prometheus and alert manager ports
   * @return the prometheus Helm installation parameters
   */
  public static PrometheusParams installAndVerifyPrometheus(String promReleaseSuffix,
                                                            String promNamespace,
                                                            String promVersion,
                                                            String prometheusRegexValue,
                                                            String promHelmValuesFileDir,
                                                            String webhookNS,
                                                            int... ports) {
    LoggingFacade logger = getLogger();
    String prometheusReleaseName = "prometheus" + promReleaseSuffix;
    logger.info("create a staging location for prometheus scripts");
    Path fileTemp = Paths.get(promHelmValuesFileDir);
    assertDoesNotThrow(() -> FileUtils.deleteDirectory(fileTemp.toFile()), "Failed to delete temp dir for prometheus");

    assertDoesNotThrow(() -> Files.createDirectories(fileTemp), "Failed to create temp dir for prometheus");
    String promValuesFile = OKE_CLUSTER_PRIVATEIP ? "promvaluesoke.yaml" : "promvalues.yaml";
    logger.info("copy the " + promValuesFile + "  to staging location");
    Path srcPromFile = Paths.get(RESOURCE_DIR, "exporter", promValuesFile);
    Path targetPromFile = Paths.get(fileTemp.toString(), "promvalues.yaml");
    assertDoesNotThrow(() -> Files.copy(srcPromFile, targetPromFile,
        StandardCopyOption.REPLACE_EXISTING), " Failed to copy files");
    String oldValue = "regex: default;domain1";
    assertDoesNotThrow(() -> {
      replaceStringInFile(targetPromFile.toString(),
          oldValue,
          prometheusRegexValue);
      replaceStringInFile(targetPromFile.toString(),
          "pvc-alertmanager",
          "pvc-alertmanager" + promReleaseSuffix);
      replaceStringInFile(targetPromFile.toString(),
          "pvc-prometheus",
          "pvc-" + prometheusReleaseName);
      replaceStringInFile(targetPromFile.toString(),
          "pushgateway_image",
          PROMETHEUS_PUSHGATEWAY_IMAGE_NAME);
      replaceStringInFile(targetPromFile.toString(),
          "pushgateway_tag",
          PROMETHEUS_PUSHGATEWAY_IMAGE_TAG);
      replaceStringInFile(targetPromFile.toString(),
          "prometheus_image",
          PROMETHEUS_IMAGE_NAME);
      replaceStringInFile(targetPromFile.toString(),
          "prometheus_tag",
          PROMETHEUS_IMAGE_TAG);
      replaceStringInFile(targetPromFile.toString(),
          "prometheus_alertmanager_image",
          PROMETHEUS_ALERT_MANAGER_IMAGE_NAME);
      replaceStringInFile(targetPromFile.toString(),
          "prometheus_alertmanager_tag",
          PROMETHEUS_ALERT_MANAGER_IMAGE_TAG);
      replaceStringInFile(targetPromFile.toString(),
          "prometheus_configmap_reload_image",
          PROMETHEUS_CONFIG_MAP_RELOAD_IMAGE_NAME);
      replaceStringInFile(targetPromFile.toString(),
          "prometheus_configmap_reload_tag",
          PROMETHEUS_CONFIG_MAP_RELOAD_IMAGE_TAG);
      replaceStringInFile(targetPromFile.toString(),
          "prometheus_node_exporter_image",
          PROMETHEUS_NODE_EXPORTER_IMAGE_NAME);
      replaceStringInFile(targetPromFile.toString(),
          "prometheus_node_exporter_tag",
          PROMETHEUS_NODE_EXPORTER_IMAGE_TAG);
    }, "Failed to create " + targetPromFile);
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
    if (ports.length != 0 && KIND_CLUSTER && !WLSIMG_BUILDER.equals(WLSIMG_BUILDER_DEFAULT)) {
      promServerNodePort = ports[0];
      alertManagerNodePort = ports[1];
    }

    assertTrue(imageRepoLogin(TestConstants.BASE_IMAGES_REPO,
        BASE_IMAGES_REPO_USERNAME, BASE_IMAGES_REPO_PASSWORD), WLSIMG_BUILDER + " login failed");

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
        .nodePortAlertManager(alertManagerNodePort);
    if (!OKE_CLUSTER_PRIVATEIP) {
      prometheusParams.nodePortServer(promServerNodePort);
    }

    if (OKD) {
      addSccToDBSvcAccount(prometheusReleaseName + "-server", promNamespace);
      addSccToDBSvcAccount(prometheusReleaseName + "-kube-state-metrics", promNamespace);
      addSccToDBSvcAccount(prometheusReleaseName + "-alertmanager", promNamespace);
      addSccToDBSvcAccount("default", promNamespace);
    }
    // install prometheus
    logger.info("Installing prometheus in namespace {0}", promNamespace);
    assertDoesNotThrow(() -> logger.info(Files.readString(targetPromFile)));
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
        assertDoesNotThrow(() -> isPrometheusReady(promNamespace, prometheusReleaseName),
            "prometheusIsReady failed with ApiException"),
        logger,
        "prometheus to be running in namespace {0}",
        promNamespace);
    String command1 = KUBERNETES_CLI + " get svc -n " + promNamespace;
    assertDoesNotThrow(() -> ExecCommand.exec(command1, true));
    String command2 = KUBERNETES_CLI + " describe svc -n " + promNamespace;
    assertDoesNotThrow(() -> ExecCommand.exec(command2, true));
    return prometheusParams;
  }

  /**
   * Install Prometheus adapter and wait up to five minutes until the prometheus adapter pods are ready.
   *
   * @param promAdapterReleaseName the prometheus adapter release name
   * @param promAdapterNamespace   the prometheus adapter namespace
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
    logger.info("Wait for the prometheus adapter pod is ready in namespace {0}", promAdapterNamespace);
    String command = KUBERNETES_CLI + " get pods  -n " + promAdapterNamespace;

    assertDoesNotThrow(() -> {
      ExecResult result = ExecCommand.exec(command, true);
      String response = result.stdout().trim();
      logger.info("Response : exitValue {0}, stdout {1}, stderr {2}",
          result.exitValue(), response, result.stderr());
    });
    testUntil(
        searchForKey(command, "prometheus-adapterhpatest"),
        logger,
        "Check pod creation {0} ",
        "prometheus-adapterhpatest");

    logger.info("Get pod name for adapter in namespace {0}", promAdapterNamespace);

    String podName = assertDoesNotThrow(() -> getPodName(promAdapterNamespace, "prometheus-adapterhpatest"),
        "Can't find prometheus-adapter pod");
    checkPodExists(podName, null, promAdapterNamespace);
    String command1 = KUBERNETES_CLI + " describe pods " + podName + " -n " + promAdapterNamespace;
    assertDoesNotThrow(() -> {
      ExecResult result = ExecCommand.exec(command1, true);
      String response = result.stdout().trim();
      logger.info("Response : exitValue {0}, stdout {1}, stderr {2}",
          result.exitValue(), response, result.stderr());
    });
    logger.info("prometheus adapter pod  {0} is ready in namespace {1}", podName, promAdapterNamespace);
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
   * @param grafanaReleaseName       the grafana release name
   * @param grafanaNamespace         the grafana namespace in which the operator will be installed
   * @param grafanaHelmValuesFileDir the grafana helm values.yaml file directory
   * @param grafanaVersion           the version of the grafana helm chart
   * @return the grafana Helm installation parameters
   */
  public static GrafanaParams installAndVerifyGrafana(String grafanaReleaseName,
                                                      String grafanaNamespace,
                                                      String grafanaHelmValuesFileDir,
                                                      String grafanaVersion) {
    LoggingFacade logger = getLogger();
    logger.info("create a staging location for grafana scripts");
    Path fileTemp = Paths.get(grafanaHelmValuesFileDir);
    assertDoesNotThrow(() -> FileUtils.deleteDirectory(fileTemp.toFile()), "Failed to delete temp dir for grafana");

    assertDoesNotThrow(() -> Files.createDirectories(fileTemp), "Failed to create temp dir for grafana");
    String grafanavaluesFile = OKE_CLUSTER_PRIVATEIP ? "grafanavaluesoke.yaml" : "grafanavalues.yaml";
    logger.info("copy the " + grafanavaluesFile + " to staging location");
    Path srcGrafanaFile = Paths.get(RESOURCE_DIR, "exporter", grafanavaluesFile);
    Path targetGrafanaFile = Paths.get(fileTemp.toString(), "grafanavalues.yaml");
    assertDoesNotThrow(() -> Files.copy(srcGrafanaFile, targetGrafanaFile,
        StandardCopyOption.REPLACE_EXISTING), " Failed to copy files");
    assertDoesNotThrow(() -> replaceStringInFile(targetGrafanaFile.toString(),
        "pvc-grafana", "pvc-" + grafanaReleaseName));
    assertDoesNotThrow(() -> replaceStringInFile(targetGrafanaFile.toString(),
        "grafana_image",
        GRAFANA_IMAGE_NAME), "Failed to replace String ");
    assertDoesNotThrow(() -> replaceStringInFile(targetGrafanaFile.toString(),
        "grafana_tag",
        GRAFANA_IMAGE_TAG), "Failed to replace String ");
    assertDoesNotThrow(() -> replaceStringInFile(targetGrafanaFile.toString(),
        "busybox_image",
        BUSYBOX_IMAGE), "Failed to replace String ");
    assertDoesNotThrow(() -> replaceStringInFile(targetGrafanaFile.toString(),
        "busybox_tag",
        BUSYBOX_TAG), "Failed to replace String ");
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
        if (item.getMetadata() != null && item.getMetadata().getName() != null
            && item.getMetadata().getName().equals("grafana-secret")) {
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
    assertDoesNotThrow(() -> logger.info(Files.readString(targetGrafanaFile)));
    GrafanaParams grafanaParams = null;
    if (!OKE_CLUSTER_PRIVATEIP) {
      int grafanaNodePort = getNextFreePort();
      logger.info("Installing grafana with node port {0}", grafanaNodePort);
      // grafana chart values to override
      grafanaParams = new GrafanaParams()
          .helmParams(grafanaHelmParams)
          .nodePort(grafanaNodePort);
    } else {
      grafanaParams = new GrafanaParams()
          .helmParams(grafanaHelmParams);
    }
    boolean isGrafanaInstalled = false;
    if (OKD) {
      addSccToDBSvcAccount(grafanaReleaseName, grafanaNamespace);
    }
    assertTrue(installGrafana(grafanaParams),
        String.format("Failed to install grafana in namespace %s", grafanaNamespace));
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
   * @param grafanaReleaseName    the grafana release name
   * @param prometheusReleaseName prometheus release name
   */
  public static void cleanupPromGrafanaClusterRoles(String prometheusReleaseName, String grafanaReleaseName) {
    //extra cleanup
    try {
      if (ClusterRole.clusterRoleExists(prometheusReleaseName + "-kube-state-metrics")) {
        Kubernetes.deleteClusterRole(prometheusReleaseName + "-kube-state-metrics");
      }
      if (ClusterRole.clusterRoleExists(prometheusReleaseName + "-pushgateway")) {
        Kubernetes.deleteClusterRole(prometheusReleaseName + "-pushgateway");
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
      if (ClusterRoleBinding.clusterRoleBindingExists(prometheusReleaseName + "-pushgateway")) {
        Kubernetes.deleteClusterRoleBinding(prometheusReleaseName + "-pushgateway");
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

    //adding ability to build monitoring exporter if branch is not main
    boolean toBuildMonitoringExporter = (!MONITORING_EXPORTER_BRANCH.equalsIgnoreCase(("main")));
    installMonitoringExporter(monitoringExporterDir, toBuildMonitoringExporter);
  }

  /**
   * Download src from monitoring exporter github project and build or install webapp.
   *
   * @param monitoringExporterDir     full path to monitoring exporter install location
   * @param toBuildMonitoringExporter if true build monitoring exporter webapp or download if false.
   */
  public static void installMonitoringExporter(String monitoringExporterDir, boolean toBuildMonitoringExporter) {

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
      buildMonitoringExporterApp(monitoringExporterSrcDir, RESOURCE_DIR
          + "/exporter/exporter-config-norestport.yaml", monitoringExporterAppNoRestPortDir);
      buildMonitoringExporterApp(monitoringExporterSrcDir, RESOURCE_DIR
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
   *
   * @param modelFilePath - path to model file
   * @param monexpAppDir  - location for monitoring exporter webapp
   * @param appName       -extra app name
   * @param imageName     - desired imagename
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
   * Create mii image with monitoring exporter webapp and one more app.
   *
   * @param modelList    - list of the paths to model files
   * @param monexpAppDir - location for monitoring exporter webapp
   * @param appName1     -extra app names
   * @param imageName    - desired imagename
   */
  public static String createAndVerifyMiiImage(String monexpAppDir, List<String> modelList,
                                               String appName1, String appName2,
                                               String imageName) {
    // create image with model files
    logger.info("Create image with model file with monitoring exporter app and verify");
    String appPath = String.format("%s/wls-exporter.war", monexpAppDir);
    List<String> appList = new ArrayList<>();
    appList.add(appPath);
    appList.add(appName1);
    appList.add(appName2);

    // build the model file list
    //final List<String> modelList = Collections.singletonList(modelFilePath);
    String myImage =
        createMiiImageAndVerify(imageName, modelList, appList);

    // login and push image to registry if necessary
    imageRepoLoginAndPushImageToRegistry(myImage);

    return myImage;
  }

  /**
   * Uninstall Prometheus and Grafana helm charts.
   *
   * @param promHelmParams    -helm chart params for prometheus
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
      deleteSecret("grafana-secret", grafanaHelmParams.getHelmParams().getNamespace());
      logger.info("Grafana is uninstalled");
    }
    cleanupPromGrafanaClusterRoles(prometheusReleaseName, grafanaReleaseName);
  }

  /**
   * Create Domain Cr and verity.
   *
   * @param adminSecretName      WebLogic admin credentials
   * @param repoSecretName       image repository secret name
   * @param encryptionSecretName model encryption secret name
   * @param miiImage             model in image name
   * @param domainUid            domain uid
   * @param namespace            namespace
   * @param domainHomeSource     domain home source type
   * @param replicaCount         replica count for the cluster
   * @param twoClusters          boolean indicating if the domain has 2 clusters
   * @param monexpConfig         monitoring exporter config
   * @param exporterImage        exporter image
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
                        + "-Dweblogic.security.SSL.ignoreHostnameVerification=true "
                        + "-Dweblogic.security.remoteAnonymousRMIT3Enabled=true "))
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
                .introspectorJobActiveDeadlineSeconds(3000L)));

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
      //String monexpImage = "${OCIR_HOST}/${WKT_TENANCY}/exporter:beta";
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
   * @param miiImage         model in image name
   * @param domainUid        domain uid
   * @param namespace        namespace
   * @param domainHomeSource domain home source type
   * @param replicaCount     replica count for the cluster
   * @param twoClusters      boolean indicating if the domain has 2 clusters
   * @param monexpConfig     monitoring exporter config
   * @param exporterImage    exporter image
   */
  public static void createAndVerifyDomain(String miiImage,
                                           String domainUid,
                                           String namespace,
                                           String domainHomeSource,
                                           int replicaCount,
                                           boolean twoClusters,
                                           String monexpConfig,
                                           String exporterImage) {
    createAndVerifyDomain(miiImage,
        domainUid,
        namespace,
        domainHomeSource,
        replicaCount,
        twoClusters,
        monexpConfig,
        exporterImage, true);
  }

  /**
   * create domain from provided image and monitoring exporter sidecar and verify it's start.
   *
   * @param miiImage         model in image name
   * @param domainUid        domain uid
   * @param namespace        namespace
   * @param domainHomeSource domain home source type
   * @param replicaCount     replica count for the cluster
   * @param twoClusters      boolean indicating if the domain has 2 clusters
   * @param monexpConfig     monitoring exporter config
   * @param exporterImage    exporter image
   * @param checkPodsReady   true or false if test need to check pods status
   */
  public static void createAndVerifyDomain(String miiImage,
                                           String domainUid,
                                           String namespace,
                                           String domainHomeSource,
                                           int replicaCount,
                                           boolean twoClusters,
                                           String monexpConfig,
                                           String exporterImage,
                                           boolean checkPodsReady) {
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
    createDomainCrAndVerify(adminSecretName, TEST_IMAGES_REPO_SECRET_NAME, encryptionSecretName, miiImage, domainUid,
        namespace, domainHomeSource, replicaCount, twoClusters, monexpConfig, exporterImage);
    if (checkPodsReady) {
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
  }

  /**
   * Verify the monitoring exporter app can be accessed from all managed servers in the domain through NGINX.
   *
   * @param nginxHost    nginx host name
   * @param replicaCount number of managed servers
   * @param nodeport     nginx nodeport
   */
  public static void verifyMonExpAppAccessThroughNginx(String nginxHost, int replicaCount,
                                                       int nodeport) throws UnknownHostException {

    List<String> managedServerNames = new ArrayList<>();
    for (int i = 1; i <= replicaCount; i++) {
      managedServerNames.add(MANAGED_SERVER_NAME_BASE + i);
    }

    // check that NGINX can access the sample apps from all managed servers in the domain
    String host = formatIPv6Host(K8S_NODEPORT_HOST);
    if (KIND_CLUSTER && !WLSIMG_BUILDER.equals(WLSIMG_BUILDER_DEFAULT)) {
      host = InetAddress.getLocalHost().getHostAddress();
    }
    String curlCmd =
        String.format("curl -g --silent --show-error --noproxy '*' -v "
                + " --max-time 60 -H 'host: %s' http://%s:%s@%s:%s/wls-exporter/metrics",
            nginxHost,
            ADMIN_USERNAME_DEFAULT,
            ADMIN_PASSWORD_DEFAULT,
            host,
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
   * @param nginxHost    nginx host name
   * @param replicaCount number of managed servers
   * @param hostPort     host:port combo or host string
   */
  public static void verifyMonExpAppAccessThroughNginx(String nginxHost, int replicaCount, String hostPort) {

    List<String> managedServerNames = new ArrayList<>();
    for (int i = 1; i <= replicaCount; i++) {
      managedServerNames.add(MANAGED_SERVER_NAME_BASE + i);
    }

    // check that NGINX can access the sample apps from all managed servers in the domain
    String curlCmd =
        String.format("curl -g --silent --show-error --noproxy '*' -v "
                + " --max-time 60 -H 'host: %s' http://%s:%s@%s/wls-exporter/metrics",
            nginxHost,
            ADMIN_USERNAME_DEFAULT,
            ADMIN_PASSWORD_DEFAULT,
            hostPort);
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
   * @param hostPort     host:port combination to access app
   */
  public static void verifyMonExpAppAccess(int replicaCount, String hostPort) {

    List<String> managedServerNames = new ArrayList<>();
    for (int i = 1; i <= replicaCount; i++) {
      managedServerNames.add(MANAGED_SERVER_NAME_BASE + i);
    }

    // check the access to monitoring exporter apps from all managed servers in the domain
    String curlCmd =
        String.format("curl -g --silent --show-error --noproxy '*' -v "
                + " --max-time 60 http://%s:%s@%s/wls-exporter/metrics",
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
   *
   * @param clusterName - name of cluster
   * @param domainNS    - domain namespace
   * @param domainUid   - domain uid
   * @param isHttps     - protocol
   * @param uri         - weburl
   * @param searchKey   - search key in response
   */
  public static boolean verifyMonExpAppAccess(String uri, String searchKey, String domainUid,
                                              String domainNS, boolean isHttps, String clusterName) {
    return verifyMonExpAppAccess(uri, searchKey, false, domainUid,
        domainNS, isHttps, clusterName);
  }

  /**
   * Verify the monitoring exporter app can be accessed from all managed servers in the domain
   * through direct access to managed server dashboard.
   *
   * @param clusterName - name of cluster
   * @param domainNS    - domain namespace
   * @param domainUid   - domain uid
   * @param isHttps     - protocol
   * @param uri         - weburl
   * @param searchKey   - search key in response
   * @param isRegex     - search key contains regex
   */
  public static boolean verifyMonExpAppAccess(String uri, String searchKey, Boolean isRegex, String domainUid,
                                              String domainNS, boolean isHttps, String clusterName) {
    String protocol = "http";
    String port = "8001";
    if (isHttps) {
      protocol = "https";
      port = "7002";
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
      if (isRegex) {
        isFound = containsValidServletName(response, searchKey);
      } else {
        isFound = response.contains(searchKey);
      }
      logger.info("isFound value:" + isFound);
    } catch (Exception ex) {
      logger.info("Can't execute command " + command + Arrays.toString(ex.getStackTrace()));
      return false;
    }
    return isFound;
  }

  // Method to check if the string contains the required pattern
  // Regular expression pattern to match servletName="ANYTHING.ExporterServlet"
  // regex = "servletName=\"[^\"]*ExporterServlet\"";
  private static boolean containsValidServletName(String input, String regex) {
    Pattern pattern = Pattern.compile(regex);
    Matcher matcher = pattern.matcher(input);
    return matcher.find();
  }

  /**
   * Verify the monitoring exporter sidecar can be accessed from all managed servers in the domain
   * through direct access to managed server dashboard.
   *
   * @param domainNS  - domain namespace
   * @param podName   - managed server pod name
   * @param searchKey - search key in response
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

  /**
   * Build exporter, create image with unique name, create corresponding repo secret and push to registry.
   *
   * @param srcDir                directory where source is located
   * @param baseImageName         base image name
   * @param namespace             image namespace
   * @param secretName            repo secretname for image
   * @param extraImageBuilderArgs user specified extra args
   * @return image name
   */
  public static String buildMonitoringExporterCreateImageAndPushToRepo(
      String srcDir, String baseImageName,
      String namespace, String secretName,
      String extraImageBuilderArgs) throws ApiException {
    String command = String.format("cd %s && mvn clean install -Dmaven.test.skip=true", srcDir);
    logger.info("Executing command " + command);
    assertTrue(Command
        .withParams(new CommandParams()
            .command(command))
        .execute(), "Failed to build monitoring exporter");
    return createImageAndPushToRepo(srcDir, baseImageName, namespace, secretName, extraImageBuilderArgs);
  }

  /**
   * Create Traefik Ingress routing rules for prometheus.
   *
   * @param namespace            namespace of prometheus
   * @param serviceName          name of exposed service
   * @param ingressRulesFileName ingress rules file name
   */
  public static void createTraefikIngressRoutingRulesForMonitoring(String namespace, String serviceName,
                                                                   String ingressRulesFileName) {
    logger.info("Creating ingress rules for prometheus traffic routing");
    Path srcFile = Paths.get(ActionConstants.RESOURCE_DIR, ingressRulesFileName);
    Path dstFile = Paths.get(TestConstants.RESULTS_ROOT, namespace, serviceName, ingressRulesFileName);
    assertDoesNotThrow(() -> {
      Files.deleteIfExists(dstFile);
      Files.createDirectories(dstFile.getParent());
      Files.write(dstFile, Files.readString(srcFile).replaceAll("@NS@", namespace)
          .replaceAll("@servicename@", serviceName)
          .getBytes(StandardCharsets.UTF_8));
    });
    String command = KUBERNETES_CLI + " apply -f " + dstFile;
    logger.info("Running {0}", command);
    ExecResult result;
    try {
      result = ExecCommand.exec(command, true);
      String response = result.stdout().trim();
      logger.info("exitCode: {0}, \nstdout: {1}, \nstderr: {2}",
          result.exitValue(), response, result.stderr());
      assertEquals(0, result.exitValue(), "Command didn't succeed");
    } catch (IOException | InterruptedException ex) {
      logger.severe(ex.getMessage());
    }
  }

  /**
   * Delete Traefik Ingress routing rules for prometheus.
   *
   * @param dstFile path for ingress rule deployment
   */
  public static void deleteTraefikIngressRoutingRules(Path dstFile) {

    String command = KUBERNETES_CLI + " delete -f " + dstFile;
    logger.info("Running {0}", command);
    ExecResult result;
    try {
      result = ExecCommand.exec(command, true);
      String response = result.stdout().trim();
      logger.info("exitCode: {0}, \nstdout: {1}, \nstderr: {2}",
          result.exitValue(), response, result.stderr());
      assertEquals(0, result.exitValue(), "Command didn't succeed");
    } catch (IOException | InterruptedException ex) {
      logger.severe(ex.getMessage());
    }
    assertDoesNotThrow(() -> {
      Files.deleteIfExists(dstFile);
    });
  }

  /**
   * Replace Value In File.
   *
   * @param tempFileName file name
   * @param newValue new value to search
   * @param oldValue old value to replace
   * @param srcFileName name of source file in exporter dir
   * @return modified file path
   */
  public static String replaceValueInFile(String tempFileName, String srcFileName, String oldValue, String newValue) {

    String tempFileDir = Paths.get(RESULTS_ROOT,
        tempFileName).toString();
    Path fileTemp = Paths.get(tempFileDir);
    assertDoesNotThrow(() -> FileUtils.deleteDirectory(fileTemp.toFile()), "Failed to delete temp dir ");

    assertDoesNotThrow(() -> Files.createDirectories(fileTemp), "Failed to create temp dir ");

    logger.info("copy the " + srcFileName + "  to staging location");
    Path srcFile = Paths.get(RESOURCE_DIR, "exporter", srcFileName);
    Path targetFile = Paths.get(fileTemp.toString(), srcFileName);
    assertDoesNotThrow(() -> Files.copy(srcFile, targetFile,
        StandardCopyOption.REPLACE_EXISTING), " Failed to copy files");

    assertDoesNotThrow(() -> {
      replaceStringInFile(targetFile.toString(),
          oldValue,
          newValue);
    });
    return targetFile.toString();
  }
}
