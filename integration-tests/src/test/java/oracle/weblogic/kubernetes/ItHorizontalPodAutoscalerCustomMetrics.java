// Copyright (c) 2022, 2024, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.weblogic.kubernetes;

import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import io.kubernetes.client.custom.Quantity;
import io.kubernetes.client.openapi.ApiException;
import io.kubernetes.client.openapi.models.CoreV1Event;
import io.kubernetes.client.openapi.models.V1LocalObjectReference;
import io.kubernetes.client.openapi.models.V1ResourceRequirements;
import io.kubernetes.client.util.Yaml;
import oracle.weblogic.domain.ClusterResource;
import oracle.weblogic.domain.DomainResource;
import oracle.weblogic.domain.ServerPod;
import oracle.weblogic.kubernetes.actions.impl.NginxParams;
import oracle.weblogic.kubernetes.actions.impl.Prometheus;
import oracle.weblogic.kubernetes.actions.impl.PrometheusParams;
import oracle.weblogic.kubernetes.actions.impl.primitive.Command;
import oracle.weblogic.kubernetes.actions.impl.primitive.CommandParams;
import oracle.weblogic.kubernetes.actions.impl.primitive.Helm;
import oracle.weblogic.kubernetes.actions.impl.primitive.HelmParams;
import oracle.weblogic.kubernetes.annotations.IntegrationTest;
import oracle.weblogic.kubernetes.annotations.Namespaces;
import oracle.weblogic.kubernetes.assertions.impl.Kubernetes;
import oracle.weblogic.kubernetes.logging.LoggingFacade;
import oracle.weblogic.kubernetes.utils.ExecCommand;
import oracle.weblogic.kubernetes.utils.ExecResult;
import oracle.weblogic.kubernetes.utils.MonitoringUtils;
import org.apache.commons.io.FileUtils;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIfEnvironmentVariable;

import static oracle.weblogic.kubernetes.TestConstants.ADMIN_PASSWORD_DEFAULT;
import static oracle.weblogic.kubernetes.TestConstants.ADMIN_SERVER_NAME_BASE;
import static oracle.weblogic.kubernetes.TestConstants.ADMIN_USERNAME_DEFAULT;
import static oracle.weblogic.kubernetes.TestConstants.IT_HPACUSTOMNGINX_INGRESS_HTTPS_NODEPORT;
import static oracle.weblogic.kubernetes.TestConstants.IT_HPACUSTOMNGINX_INGRESS_HTTP_HOSTPORT;
import static oracle.weblogic.kubernetes.TestConstants.IT_HPACUSTOMNGINX_INGRESS_HTTP_NODEPORT;
import static oracle.weblogic.kubernetes.TestConstants.K8S_NODEPORT_HOST;
import static oracle.weblogic.kubernetes.TestConstants.KIND_CLUSTER;
import static oracle.weblogic.kubernetes.TestConstants.KUBERNETES_CLI;
import static oracle.weblogic.kubernetes.TestConstants.MANAGED_SERVER_NAME_BASE;
import static oracle.weblogic.kubernetes.TestConstants.NGINX_CHART_VERSION;
import static oracle.weblogic.kubernetes.TestConstants.OKE_CLUSTER;
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
import static oracle.weblogic.kubernetes.actions.TestActions.deletePod;
import static oracle.weblogic.kubernetes.actions.TestActions.getServiceNodePort;
import static oracle.weblogic.kubernetes.actions.TestActions.now;
import static oracle.weblogic.kubernetes.actions.TestActions.uninstallNginx;
import static oracle.weblogic.kubernetes.actions.impl.primitive.Kubernetes.deleteNamespace;
import static oracle.weblogic.kubernetes.utils.ClusterUtils.createClusterAndVerify;
import static oracle.weblogic.kubernetes.utils.ClusterUtils.createClusterResource;
import static oracle.weblogic.kubernetes.utils.CommonMiiTestUtils.createDomainResource;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.checkClusterReplicaCountMatches;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.checkPodReadyAndServiceExists;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.createIngressPathRouting;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.formatIPv6Host;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.getServiceExtIPAddrtOke;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.testUntil;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.withLongRetryPolicy;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.withStandardRetryPolicy;
import static oracle.weblogic.kubernetes.utils.DomainUtils.createDomainAndVerify;
import static oracle.weblogic.kubernetes.utils.FileUtils.replaceStringInFile;
import static oracle.weblogic.kubernetes.utils.ImageUtils.createBaseRepoSecret;
import static oracle.weblogic.kubernetes.utils.ImageUtils.createTestRepoSecret;
import static oracle.weblogic.kubernetes.utils.K8sEvents.getEvents;
import static oracle.weblogic.kubernetes.utils.LoadBalancerUtils.createIngressForDomainAndVerify;
import static oracle.weblogic.kubernetes.utils.LoadBalancerUtils.installAndVerifyNginx;
import static oracle.weblogic.kubernetes.utils.MonitoringUtils.cleanupPromGrafanaClusterRoles;
import static oracle.weblogic.kubernetes.utils.MonitoringUtils.cleanupPrometheusAdapterClusterRoles;
import static oracle.weblogic.kubernetes.utils.MonitoringUtils.deleteMonitoringExporterTempDir;
import static oracle.weblogic.kubernetes.utils.MonitoringUtils.editPrometheusCM;
import static oracle.weblogic.kubernetes.utils.MonitoringUtils.installAndVerifyPrometheus;
import static oracle.weblogic.kubernetes.utils.MonitoringUtils.installAndVerifyPrometheusAdapter;
import static oracle.weblogic.kubernetes.utils.MonitoringUtils.installMonitoringExporter;
import static oracle.weblogic.kubernetes.utils.OperatorUtils.installAndVerifyOperator;
import static oracle.weblogic.kubernetes.utils.PersistentVolumeUtils.createPvAndPvc;
import static oracle.weblogic.kubernetes.utils.PodUtils.checkPodReady;
import static oracle.weblogic.kubernetes.utils.PodUtils.isPodDeleted;
import static oracle.weblogic.kubernetes.utils.SecretUtils.createSecretWithUsernamePassword;
import static oracle.weblogic.kubernetes.utils.ThreadSafeLogger.getLogger;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("Test to a create MII domain and test autoscaling using HPA and"
    + "custom metrics provided via use of monitoring exporter and prometheus and prometheus adapter")
@IntegrationTest
@Tag("oke-weekly-sequential")
@Tag("kind-parallel")
public class ItHorizontalPodAutoscalerCustomMetrics {
  private static final String MONEXP_MODEL_FILE = "model.monexp.custommetrics.yaml";
  private static final String MONEXP_IMAGE_NAME = "monexp-image";
  private static final String SESSMIGR_APP_NAME = "sessmigr-app";
  private static final String SESSMIGR_APP_WAR_NAME = "sessmigr-war";
  private static final String SESSMIGT_APP_URL = SESSMIGR_APP_WAR_NAME + "/?getCounter";
  private static final String domainUid = "hpacustomdomain";
  private static String ingressIP = null;

  private static String domainNamespace = null;
  private static String wlClusterName = "cluster-1";
  private static String clusterResName = "hpacustomcluster";
  private static String adminServerPodName = String.format("%s-%s", domainUid, ADMIN_SERVER_NAME_BASE);
  private static String managedServerPrefix = String.format("%s-%s-%s",
      domainUid, wlClusterName, MANAGED_SERVER_NAME_BASE);
  private static LoggingFacade logger = null;
  private static  String monitoringExporterDir;
  private static NginxParams nginxHelmParams = null;
  private static int nodeportshttp = 0;
  private static String monitoringNS = null;
  private static PrometheusParams promHelmParams = null;
  private static String releaseSuffix = "hpatest";
  private static String prometheusReleaseName = "prometheus" + releaseSuffix;
  private static String prometheusAdapterReleaseName = "prometheus-adapter" + releaseSuffix;
  private static String prometheusDomainRegexValue = null;
  private static int nodeportPrometheus;
  private Path targetHPAFile;
  private HelmParams prometheusAdapterHelmParams = null;

  /**
   * Assigns unique namespaces for operator and domains.
   * Pull WebLogic image if running tests in Kind cluster.
   * Installs operator.
   *
   * @param namespaces injected by JUnit
   */
  @BeforeAll
  public static void initAll(@Namespaces(4) List<String> namespaces) {
    logger = getLogger();
    int replicaCount = 2;
    String className = ItHorizontalPodAutoscalerCustomMetrics.class.getSimpleName();

    monitoringExporterDir = Paths.get(RESULTS_ROOT, className, "monitoringexp").toString();
    String monitoringExporterAppDir = Paths.get(monitoringExporterDir, "apps").toString();
    logger.info("Assign a unique namespace for operator");
    assertNotNull(namespaces.get(0), "Namespace is null");
    String opNamespace = namespaces.get(0);

    logger.info("Assign a unique namespace for WebLogic domain");
    assertNotNull(namespaces.get(1), "Namespace is null");
    domainNamespace = namespaces.get(1);
    logger.info("Get a unique namespace for prometheus and prometheus adapter");
    assertNotNull(namespaces.get(2), "Namespace list is null");
    monitoringNS = namespaces.get(2);

    logger.info("Get a unique namespace for Nginx");
    assertNotNull(namespaces.get(3), "Namespace list is null");
    String nginxNamespace = namespaces.get(3);

    // set the service account name for the operator
    String opServiceAccount = opNamespace + "-sa";
    // install and verify operator with REST API
    installAndVerifyOperator(opNamespace, opServiceAccount, true, 0, domainNamespace);

    // create pull secrets for WebLogic image when running in non Kind Kubernetes cluster
    // this secret is used only for non-kind cluster
    createTestRepoSecret(domainNamespace);
    createBaseRepoSecret(domainNamespace);

    // create secret for admin credentials
    logger.info("Create secret for admin credentials");
    String adminSecretName = "weblogic-credentials";
    createSecretWithUsernamePassword(adminSecretName, domainNamespace,
        ADMIN_USERNAME_DEFAULT, ADMIN_PASSWORD_DEFAULT);

    // create encryption secret
    logger.info("Create encryption secret");
    String encryptionSecretName = "encryptionsecret";
    createSecretWithUsernamePassword(encryptionSecretName, domainNamespace,
        "weblogicenc", "weblogicenc");

    logger.info("install monitoring exporter");
    installMonitoringExporter(monitoringExporterDir);

    logger.info("create and verify WebLogic domain image using model in image with model files");
    String miiImage = MonitoringUtils.createAndVerifyMiiImage(monitoringExporterAppDir,
        MODEL_DIR + "/" + MONEXP_MODEL_FILE,
        SESSMIGR_APP_NAME, MONEXP_IMAGE_NAME);
    HashMap<String, String> labels = new HashMap<>();
    labels.put("app", "monitoring");
    labels.put("weblogic.domainUid", "test");

    logger.info("create pv and pvc for monitoring");
    assertDoesNotThrow(() -> createPvAndPvc(prometheusReleaseName, monitoringNS, labels, className));
    assertDoesNotThrow(() -> createPvAndPvc("alertmanager" + releaseSuffix, monitoringNS, labels, className));
    cleanupPromGrafanaClusterRoles(prometheusReleaseName, null);
    cleanupPrometheusAdapterClusterRoles();

    DomainResource domain = createDomainResource(
        domainUid,
        domainNamespace,
        miiImage,
        adminSecretName,
        new String[]{TEST_IMAGES_REPO_SECRET_NAME},
        encryptionSecretName
    );

    // install and verify NGINX
    nginxHelmParams = installAndVerifyNginx(nginxNamespace, IT_HPACUSTOMNGINX_INGRESS_HTTP_NODEPORT,
        IT_HPACUSTOMNGINX_INGRESS_HTTPS_NODEPORT, NGINX_CHART_VERSION, (OKE_CLUSTER ? null : "NodePort"));

    String nginxServiceName = nginxHelmParams.getHelmParams().getReleaseName() + "-ingress-nginx-controller";
    logger.info("NGINX service name: {0}", nginxServiceName);
    nodeportshttp = getServiceNodePort(nginxNamespace, nginxServiceName, "http");
    logger.info("NGINX http node port: {0}", nodeportshttp);
    String host = formatIPv6Host(K8S_NODEPORT_HOST);
    ingressIP = getServiceExtIPAddrtOke(nginxServiceName, nginxNamespace) != null
        ? getServiceExtIPAddrtOke(nginxServiceName, nginxNamespace) : host;
    if (KIND_CLUSTER && !WLSIMG_BUILDER.equals(WLSIMG_BUILDER_DEFAULT)) {
      try {
        ingressIP = formatIPv6Host(InetAddress.getLocalHost().getHostAddress());
      } catch (UnknownHostException ex) {
        logger.severe(ex.getLocalizedMessage());
      }
      nodeportshttp = IT_HPACUSTOMNGINX_INGRESS_HTTP_HOSTPORT;
    }

    // create cluster resouce with limits and requests in serverPod
    ClusterResource clusterResource =
        createClusterResource(clusterResName, wlClusterName, domainNamespace, replicaCount);
    clusterResource.getSpec()
        .serverPod(new ServerPod().resources(
            new V1ResourceRequirements()
                .putLimitsItem("cpu", Quantity.fromString("2"))
                .putLimitsItem("memory", Quantity.fromString("2Gi"))
                .putRequestsItem("cpu", Quantity.fromString("250m"))
                .putRequestsItem("memory", Quantity.fromString("768Mi"))));
    logger.info("Creating cluster {0} in namespace {1}", clusterResName, domainNamespace);
    createClusterAndVerify(clusterResource);

    // set cluster references
    domain.getSpec().withCluster(new V1LocalObjectReference().name(clusterResName));

    // verify the domain custom resource is created
    createDomainAndVerify(domain, domainNamespace);

    // check admin server is up and running for domain1
    checkPodReadyAndServiceExists(adminServerPodName, domainUid, domainNamespace);

    // check managed servers are up and running for domain1
    for (int i = 1; i <= replicaCount; i++) {
      checkPodReadyAndServiceExists(managedServerPrefix + i, domainUid, domainNamespace);
    }
  }


  /**
   * Test autoscaling using HPA with custom WLS metrics, collected by Monitoring Exporter
   * and exposed via Prometheus and Prometheus Adapter.
   */
  @DisabledIfEnvironmentVariable(named = "OKD", matches = "true")
  @Test
  void testHPAWithCustomMetrics() {

    assertDoesNotThrow(() -> installPrometheus(PROMETHEUS_CHART_VERSION,
        domainNamespace,
        domainUid), "Failed to install Prometheus");

    String promURL = prometheusReleaseName + "-server." + monitoringNS + ".svc.cluster.local";
    prometheusAdapterHelmParams = assertDoesNotThrow(() -> installAndVerifyPrometheusAdapter(
        prometheusAdapterReleaseName,
        monitoringNS, promURL, 80), "Failed to install Prometheus Adapter");

    // wait till prometheus adapter could get the current custom metrics
    // total_opened_sessions_myear_app to make sure it is ready
    testUntil(withStandardRetryPolicy,
        () -> verifyCustomMetricsExposed(domainNamespace,"total_opened_sessions_myear_app"),
        logger,
        "Get current total_opened_sessions_myear_app from prometheus adapter in namespace {0}",
        domainNamespace);

    int managedServerPort = 8001;
    Map<String, Integer> clusterNameMsPortMap = new HashMap<>();
    clusterNameMsPortMap.put(wlClusterName, managedServerPort);

    String ingressClassName = nginxHelmParams.getIngressClassName();
    List<String> ingressHostList
        = createIngressForDomainAndVerify(domainUid, domainNamespace, 0, clusterNameMsPortMap,
        true, ingressClassName, false, 0);
    // create hpa with custom metrics
    createHPA();
    //invoke app 20 times to generate metrics with number of opened sessions > 5
    String host = formatIPv6Host(K8S_NODEPORT_HOST);
    String hostPort = OKE_CLUSTER_PRIVATEIP ? ingressIP : host + ":" + nodeportshttp;
    if (KIND_CLUSTER && !WLSIMG_BUILDER.equals(WLSIMG_BUILDER_DEFAULT)) {
      try {
        host = formatIPv6Host(InetAddress.getLocalHost().getHostAddress());
      } catch (UnknownHostException ex) {
        logger.severe(ex.getLocalizedMessage());
      }
      nodeportshttp = IT_HPACUSTOMNGINX_INGRESS_HTTP_HOSTPORT;
      hostPort = host + ":" + nodeportshttp;
    }
    String curlCmd =
        String.format("curl -g --silent --show-error -v --noproxy '*' -H 'host: %s' http://%s:%s@%s/" + SESSMIGT_APP_URL,
            ingressHostList.get(0),
            ADMIN_USERNAME_DEFAULT,
            ADMIN_PASSWORD_DEFAULT,
            hostPort);

    logger.info("Executing curl command " + curlCmd);
    assertDoesNotThrow(() -> {
      ExecResult result = ExecCommand.exec(curlCmd, true);
      String response = result.stdout().trim();
      getLogger().info("exitCode: {0}, \nstdout: {1}, \nstderr: {2}",
          result.exitValue(), response, result.stderr());
      if (!response.contains("cluster-1-managed")) {
        logger.info("Can't invoke application");

        if (OKE_CLUSTER) {
          LoggingFacade logger = getLogger();
          try {

            result = ExecCommand.exec(KUBERNETES_CLI + " get all -A");
            logger.info(result.stdout());
            //restart core-dns service
            result = ExecCommand.exec(KUBERNETES_CLI + " rollout restart deployment coredns -n kube-system");
            logger.info(result.stdout());
            checkPodReady("coredns", null, "kube-system");

          } catch (Exception ex) {
            logger.warning(ex.getLocalizedMessage());
          }
        }
      }
    });
    for (int i = 0; i < 50; i++) {
      assertDoesNotThrow(() -> {
        ExecResult result = ExecCommand.exec(curlCmd, true);
        String response = result.stdout().trim();
        getLogger().info("exitCode: {0}, \nstdout: {1}, \nstderr: {2}",
            result.exitValue(), response, result.stderr());
        assertTrue(response.contains("cluster-1-managed"), "Can't invoke application");
      });
    }
    //check hpa scaled up to one more server
    checkPodReadyAndServiceExists(managedServerPrefix + 3, domainUid, domainNamespace);
    //reboot server1 and server2 to kill open sessions
    assertDoesNotThrow(() -> deletePod(managedServerPrefix + 1, domainNamespace));
    assertDoesNotThrow(() -> deletePod(managedServerPrefix + 2, domainNamespace));
    OffsetDateTime timestamp = now();
    // wait until reboot
    for (int i = 1; i < 3; i++) {
      checkPodReadyAndServiceExists(managedServerPrefix + i, domainUid, domainNamespace);
    }
    //wait for new metric to fetch
    testUntil(
        withLongRetryPolicy,
        () -> verifyHPA(domainNamespace, "0/5"),
        logger,
        "Checking if total_open_session metric is 0");
    testUntil(
        withLongRetryPolicy,
        () -> verifyHPA(domainNamespace, "2         3         2"),
        logger,
        "Checking if replica switched to 2");

    if (!isPodDeleted(managedServerPrefix + 3, domainUid, domainNamespace)) {

      //check if different server was scaled down
      assertDoesNotThrow(() -> {
        logger.info("Checking if HPA scaled down managed server 1 or managed server 2");
        String command = KUBERNETES_CLI + " get pods -n"  + domainNamespace;

        logger.info("Executing command " + command);
        ExecResult result = ExecCommand.exec(command);
        logger.info(" Result output: " + result.stdout());
        command = KUBERNETES_CLI + " describe pod " + managedServerPrefix + 3 + " -n"  + domainNamespace;

        logger.info("Executing command " + command);
        result = ExecCommand.exec(command);
        logger.info(" Result output: " + result.stdout());
        List<CoreV1Event> events = getEvents(domainNamespace,timestamp);
        for (CoreV1Event event : events) {
          logger.info("Generated events after HPA scaling " + Yaml.dump(event));
        }
        int numberOfManagedSvs = 3;
        if (!Kubernetes.doesPodExist(domainNamespace, domainUid, managedServerPrefix + 1)
            || Kubernetes.isPodTerminating(domainNamespace, domainUid, managedServerPrefix + 1)) {
          logger.info("HPA scaled down managed server 1");
          --numberOfManagedSvs;
        } else if (!Kubernetes.doesPodExist(domainNamespace, domainUid, managedServerPrefix + 2)
            || Kubernetes.isPodTerminating(domainNamespace, domainUid, managedServerPrefix + 2)) {
          logger.info("HPA scaled down managed server 2");
          --numberOfManagedSvs;
        } else if (!Kubernetes.doesPodExist(domainNamespace, domainUid, managedServerPrefix + 3)
            || Kubernetes.isPodTerminating(domainNamespace, domainUid, managedServerPrefix + 3)) {
          logger.info("HPA scaled down managed server 3");
          --numberOfManagedSvs;
        }
        assertTrue(checkClusterReplicaCountMatches(clusterResName, domainNamespace, 2));
        assertEquals(2, numberOfManagedSvs);
      });
    }
  }

  /**
   * Create hpa on the cluster to autoscale with cpu usage over 50%
   * maintaining min replicas 2 and max replicas 4.
   */
  private void createHPA() {
    logger.info("create a staging location for custom hpa scripts");
    String customhpaFileDir = Paths.get(RESULTS_ROOT, this.getClass().getSimpleName(),
        "custom" + releaseSuffix).toString();
    Path fileTemp = Paths.get(customhpaFileDir);
    assertDoesNotThrow(() -> FileUtils.deleteDirectory(fileTemp.toFile()),"Failed to delete temp dir for custom hpa");

    assertDoesNotThrow(() -> Files.createDirectories(fileTemp), "Failed to create temp dir for custom hpa");

    logger.info("copy the customhpa.yaml to staging location");
    Path srcHPAFile = Paths.get(RESOURCE_DIR, "exporter", "customhpa.yaml");
    targetHPAFile = Paths.get(fileTemp.toString(), "customhpa.yaml");
    assertDoesNotThrow(() -> Files.copy(srcHPAFile, targetHPAFile,
        StandardCopyOption.REPLACE_EXISTING)," Failed to copy files");
    String oldValue = "default";
    assertDoesNotThrow(() -> replaceStringInFile(targetHPAFile.toString(),
        oldValue,
        domainNamespace), "Failed to replace String ");
    CommandParams params = new CommandParams().defaults();
    params.command(KUBERNETES_CLI + " apply -f " + targetHPAFile);
    ExecResult result = Command.withParams(params).executeAndReturnResult();
    assertEquals(0, result.exitValue(), "Failed to create hpa or autoscale, result " + result);
    /* check if hpa output does not contain <unknown>
     * kubectl get hpa --all-namespaces
     * NAMESPACE   NAME                REFERENCE                  TARGETS    MINPODS   MAXPODS   REPLICAS   AGE
     * ns-qsjlcw   custommetrics-hpa   Cluster/hpacustomcluster   0/5        2         3         2          52s
     *
     * when its not ready, it looks
     * NAMESPACE   NAME                REFERENCE                  TARGETS     MINPODS   MAXPODS   REPLICAS   AGE
     * ns-qsjlcw   custommetrics-hpa   Cluster/hpacustomcluster   <unknown>/5    2         4         2          18m
     */
    testUntil(withLongRetryPolicy,
        () -> verifyHPA(domainNamespace, "custommetrics-hpa"),
        logger,
        "hpa is ready in namespace {0}",
        domainNamespace);
  }

  // verify hpa is getting the metrics
  private boolean verifyHPA(String namespace, String expectedOutput) {
    CommandParams params = new CommandParams().defaults();
    params.command(KUBERNETES_CLI + " get hpa custommetrics-hpa -n " + namespace);

    ExecResult result = Command.withParams(params).executeAndReturnResult();
    logger.info(result.stdout());
    return result.stdout().contains(expectedOutput) && !result.stdout().contains("unknown");
  }

  // verify custom metrics is exposed via prometheus adapter
  private boolean verifyCustomMetricsExposed(String namespace, String customMetric) {
    CommandParams params = new CommandParams().defaults();
    params.command(KUBERNETES_CLI + " get --raw /apis/custom.metrics.k8s.io/v1beta1/namespaces/"
        + namespace + "/pods/%2A/" + customMetric + "  | jq .");

    ExecResult result = Command.withParams(params).executeAndReturnResult();
    logger.info(result.stdout());
    return result.stdout().contains(customMetric);
  }

  private void installPrometheus(String promChartVersion,
                                 String domainNS,
                                 String domainUid
  ) throws IOException, ApiException {
    final String prometheusRegexValue = String.format("regex: %s;%s", domainNS, domainUid);
    if (promHelmParams == null) {
      cleanupPromGrafanaClusterRoles(prometheusReleaseName, null);
      String promHelmValuesFileDir = Paths.get(RESULTS_ROOT, this.getClass().getSimpleName(),
          "prometheus" + releaseSuffix).toString();
      promHelmParams = installAndVerifyPrometheus(releaseSuffix,
          monitoringNS,
          promChartVersion,
          prometheusRegexValue, promHelmValuesFileDir);
      assertNotNull(promHelmParams, " Failed to install prometheus");
      prometheusDomainRegexValue = prometheusRegexValue;
      nodeportPrometheus = promHelmParams.getNodePortServer();
      String ingressClassName = nginxHelmParams.getIngressClassName();
      createIngressPathRouting(monitoringNS, "/",
          prometheusReleaseName + "-server", 80, ingressClassName);
    }
    //if prometheus already installed change CM for specified domain
    if (!prometheusRegexValue.equals(prometheusDomainRegexValue)) {
      logger.info("update prometheus Config Map with domain info");
      editPrometheusCM(prometheusDomainRegexValue, prometheusRegexValue, monitoringNS,
          prometheusReleaseName + "-server");
      prometheusDomainRegexValue = prometheusRegexValue;
    }
    logger.info("Prometheus is running");
  }

  /**
   * Delete created resources.
   */
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
    CommandParams params = new CommandParams().defaults();
    params.command(KUBERNETES_CLI + " delete -f " + targetHPAFile);
    ExecResult result = Command.withParams(params).executeAndReturnResult();
    assertEquals(0, result.exitValue(), "Failed to delete hpa , result " + result);
    if (prometheusAdapterHelmParams != null) {
      Helm.uninstall(prometheusAdapterHelmParams);
    }
    if (promHelmParams != null) {
      Prometheus.uninstall(promHelmParams.getHelmParams());
    }
    deletePersistentVolumeClaim("pvc-alertmanager" + releaseSuffix, monitoringNS);
    deletePersistentVolume("pv-testalertmanager" + releaseSuffix);
    deletePersistentVolumeClaim("pvc-" + prometheusReleaseName, monitoringNS);
    deletePersistentVolume("pv-test" + prometheusReleaseName);
    deleteNamespace(monitoringNS);
    deleteMonitoringExporterTempDir(monitoringExporterDir);
  }
}
