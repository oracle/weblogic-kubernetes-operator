// Copyright (c) 2022, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.weblogic.kubernetes;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import io.kubernetes.client.custom.Quantity;
import io.kubernetes.client.openapi.ApiException;
import io.kubernetes.client.openapi.models.V1LocalObjectReference;
import io.kubernetes.client.openapi.models.V1ResourceRequirements;
import oracle.weblogic.domain.ClusterResource;
import oracle.weblogic.domain.DomainResource;
import oracle.weblogic.domain.ServerPod;
import oracle.weblogic.kubernetes.actions.impl.NginxParams;
import oracle.weblogic.kubernetes.actions.impl.PrometheusParams;
import oracle.weblogic.kubernetes.actions.impl.primitive.Command;
import oracle.weblogic.kubernetes.actions.impl.primitive.CommandParams;
import oracle.weblogic.kubernetes.actions.impl.primitive.Helm;
import oracle.weblogic.kubernetes.actions.impl.primitive.HelmParams;
import oracle.weblogic.kubernetes.annotations.IntegrationTest;
import oracle.weblogic.kubernetes.annotations.Namespaces;
import oracle.weblogic.kubernetes.logging.LoggingFacade;
import oracle.weblogic.kubernetes.utils.ExecResult;
import oracle.weblogic.kubernetes.utils.MonitoringUtils;
import org.apache.commons.io.FileUtils;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static oracle.weblogic.kubernetes.TestConstants.ADMIN_PASSWORD_DEFAULT;
import static oracle.weblogic.kubernetes.TestConstants.ADMIN_SERVER_NAME_BASE;
import static oracle.weblogic.kubernetes.TestConstants.ADMIN_USERNAME_DEFAULT;
import static oracle.weblogic.kubernetes.TestConstants.K8S_NODEPORT_HOST;
import static oracle.weblogic.kubernetes.TestConstants.MANAGED_SERVER_NAME_BASE;
import static oracle.weblogic.kubernetes.TestConstants.OKD;
import static oracle.weblogic.kubernetes.TestConstants.PROMETHEUS_CHART_VERSION;
import static oracle.weblogic.kubernetes.TestConstants.RESULTS_ROOT;
import static oracle.weblogic.kubernetes.TestConstants.TEST_IMAGES_REPO_SECRET_NAME;
import static oracle.weblogic.kubernetes.actions.ActionConstants.MODEL_DIR;
import static oracle.weblogic.kubernetes.actions.ActionConstants.RESOURCE_DIR;
import static oracle.weblogic.kubernetes.actions.TestActions.deletePersistentVolume;
import static oracle.weblogic.kubernetes.actions.TestActions.deletePersistentVolumeClaim;
import static oracle.weblogic.kubernetes.actions.TestActions.getServiceNodePort;
import static oracle.weblogic.kubernetes.actions.TestActions.uninstallNginx;
import static oracle.weblogic.kubernetes.actions.impl.primitive.Kubernetes.deleteNamespace;
import static oracle.weblogic.kubernetes.utils.ClusterUtils.createClusterAndVerify;
import static oracle.weblogic.kubernetes.utils.ClusterUtils.createClusterResource;
import static oracle.weblogic.kubernetes.utils.CommonMiiTestUtils.createDomainResource;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.checkPodReadyAndServiceExists;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.testUntil;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.withStandardRetryPolicy;
import static oracle.weblogic.kubernetes.utils.DomainUtils.createDomainAndVerify;
import static oracle.weblogic.kubernetes.utils.FileUtils.replaceStringInFile;
import static oracle.weblogic.kubernetes.utils.ImageUtils.createBaseRepoSecret;
import static oracle.weblogic.kubernetes.utils.ImageUtils.createTestRepoSecret;
import static oracle.weblogic.kubernetes.utils.LoadBalancerUtils.createIngressForDomainAndVerify;
import static oracle.weblogic.kubernetes.utils.LoadBalancerUtils.installAndVerifyNginx;
import static oracle.weblogic.kubernetes.utils.MonitoringUtils.cleanupPromGrafanaClusterRoles;
import static oracle.weblogic.kubernetes.utils.MonitoringUtils.cleanupPrometheusAdapterClusterRoles;
import static oracle.weblogic.kubernetes.utils.MonitoringUtils.deleteMonitoringExporterTempDir;
import static oracle.weblogic.kubernetes.utils.MonitoringUtils.editPrometheusCM;
import static oracle.weblogic.kubernetes.utils.MonitoringUtils.installAndVerifyPrometheus;
import static oracle.weblogic.kubernetes.utils.MonitoringUtils.installAndVerifyPrometheusAdapter;
import static oracle.weblogic.kubernetes.utils.MonitoringUtils.installMonitoringExporter;
import static oracle.weblogic.kubernetes.utils.MonitoringUtils.verifyMonExpAppAccess;
import static oracle.weblogic.kubernetes.utils.MonitoringUtils.verifyMonExpAppAccessThroughNginx;
import static oracle.weblogic.kubernetes.utils.OKDUtils.createRouteForOKD;
import static oracle.weblogic.kubernetes.utils.OperatorUtils.installAndVerifyOperator;
import static oracle.weblogic.kubernetes.utils.PersistentVolumeUtils.createPvAndPvc;
import static oracle.weblogic.kubernetes.utils.SecretUtils.createSecretWithUsernamePassword;
import static oracle.weblogic.kubernetes.utils.ThreadSafeLogger.getLogger;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("Test to a create MII domain and test autoscaling using HPA")
@IntegrationTest
@Tag("oke-parallel")
@Tag("kind-parallel")
public class ItHorizontalPodAutoscalerCustomMetrics {
  private static String domainNamespace = null;
  static int replicaCount = 2;
  static String wlClusterName = "cluster-1";
  static String clusterResName = "hpacustomcluster";

  private static String adminSecretName;
  private static String encryptionSecretName;
  private static final String domainUid = "hpacustomdomain";
  private static String adminServerPodName = String.format("%s-%s", domainUid, ADMIN_SERVER_NAME_BASE);
  private static String managedServerPrefix = String.format("%s-%s-%s",
      domainUid, wlClusterName, MANAGED_SERVER_NAME_BASE);
  static DomainResource domain = null;

  private static String opServiceAccount = null;
  private static String opNamespace = null;

  private static LoggingFacade logger = null;
  private static  String monitoringExporterDir;
  private static  String monitoringExporterSrcDir;
  private static  String monitoringExporterAppDir;
  private static NginxParams nginxHelmParams = null;
  private static int nodeportshttp = 0;
  private static int nodeportshttps = 0;
  private static String nginxNamespace = null;
  private static final String MONEXP_MODEL_FILE = "model.monexp.yaml";
  private static final String MONEXP_IMAGE_NAME = "monexp-image";
  private static final String SESSMIGR_APP_NAME = "sessmigr-app";
  private static String monitoringNS = null;
  static PrometheusParams promHelmParams = null;
  private static String releaseSuffix = "hpatest";
  private static String prometheusReleaseName = "prometheus" + releaseSuffix;
  private static String prometheusAdapterReleaseName = "prometheus-adapter" + releaseSuffix;
  private static String hostPortPrometheus = null;
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
    monitoringExporterDir = Paths.get(RESULTS_ROOT,
        "ItMonitoringExporterWebApp", "monitoringexp").toString();
    monitoringExporterSrcDir = Paths.get(monitoringExporterDir, "srcdir").toString();
    monitoringExporterAppDir = Paths.get(monitoringExporterDir, "apps").toString();
    logger.info("Assign a unique namespace for operator");
    assertNotNull(namespaces.get(0), "Namespace is null");
    opNamespace = namespaces.get(0);

    logger.info("Assign a unique namespace for WebLogic domain");
    assertNotNull(namespaces.get(1), "Namespace is null");
    domainNamespace = namespaces.get(1);
    logger.info("Get a unique namespace for monitoring");
    assertNotNull(namespaces.get(2), "Namespace list is null");
    monitoringNS = namespaces.get(2);

    logger.info("Get a unique namespace for nginx");
    assertNotNull(namespaces.get(3), "Namespace list is null");
    nginxNamespace = namespaces.get(3);

    // set the service account name for the operator
    opServiceAccount = opNamespace + "-sa";

    // install and verify operator with REST API
    installAndVerifyOperator(opNamespace, opServiceAccount, true, 0, domainNamespace);

    // create pull secrets for WebLogic image when running in non Kind Kubernetes cluster
    // this secret is used only for non-kind cluster
    createTestRepoSecret(domainNamespace);
    createBaseRepoSecret(domainNamespace);

    // create secret for admin credentials
    logger.info("Create secret for admin credentials");
    adminSecretName = "weblogic-credentials";
    createSecretWithUsernamePassword(adminSecretName, domainNamespace,
        ADMIN_USERNAME_DEFAULT, ADMIN_PASSWORD_DEFAULT);

    // create encryption secret
    logger.info("Create encryption secret");
    encryptionSecretName = "encryptionsecret";
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
    String className = "ItMonitoringExporterWebApp";
    if (!OKD) {
      logger.info("create pv and pvc for monitoring");
      assertDoesNotThrow(() -> createPvAndPvc(prometheusReleaseName, monitoringNS, labels, className));
      assertDoesNotThrow(() -> createPvAndPvc("alertmanager" + releaseSuffix, monitoringNS, labels, className));
      cleanupPromGrafanaClusterRoles(prometheusReleaseName, null);
      cleanupPrometheusAdapterClusterRoles();
    }
    domain = createDomainResource(
        domainUid,
        domainNamespace,
        miiImage,
        adminSecretName,
        new String[]{TEST_IMAGES_REPO_SECRET_NAME},
        encryptionSecretName
    );
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
   * Test autoscaling using HPA by increasing the CPU usage.
   */
  @Test
  void testHPAWithMetricsServer() {
    if (!OKD) {
      assertDoesNotThrow(() -> installPrometheus(PROMETHEUS_CHART_VERSION,
          domainNamespace,
          domainUid), "Failed to install Prometheus");
      prometheusAdapterHelmParams = assertDoesNotThrow(() -> installAndVerifyPrometheusAdapter(
          prometheusAdapterReleaseName,
          monitoringNS, K8S_NODEPORT_HOST, nodeportPrometheus), "Failed to install Prometheus Adapter");
    }

    // create hpa with custom metrics
    createHPA();
    String exporterUrl = String.format("http://%s:%s/wls-exporter/",K8S_NODEPORT_HOST,nodeportshttp);
    String clusterService = domainUid + "-cluster-cluster-1";
    int managedServerPort = 8001;
    Map<String, Integer> clusterNameMsPortMap = new HashMap<>();
    clusterNameMsPortMap.put(wlClusterName, managedServerPort);

    if (!OKD) {
      String ingressClassName = nginxHelmParams.getIngressClassName();
      List<String> ingressHostList
          = createIngressForDomainAndVerify(domainUid, domainNamespace, 0, clusterNameMsPortMap,
          false, ingressClassName, false, 0);
      verifyMonExpAppAccessThroughNginx(ingressHostList.get(0), 1, nodeportshttp);
      // Need to expose the admin server external service to access the console in OKD cluster only
    } else {
      String hostName = createRouteForOKD(clusterService, domainNamespace);
      logger.info("hostName = {0} ", hostName);
      verifyMonExpAppAccess(1,hostName);
    }
    //check hpa scaled up to one more server
    checkPodReadyAndServiceExists(managedServerPrefix + 3, domainUid, domainNamespace);
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

    logger.info("copy the promvalues.yaml to staging location");
    Path srcHPAFile = Paths.get(RESOURCE_DIR, "exporter", "customhpa.yaml");
    targetHPAFile = Paths.get(fileTemp.toString(), "customhpa.yaml");
    assertDoesNotThrow(() -> Files.copy(srcHPAFile, targetHPAFile,
        StandardCopyOption.REPLACE_EXISTING)," Failed to copy files");
    String oldValue = "default";
    assertDoesNotThrow(() -> replaceStringInFile(targetHPAFile.toString(),
        oldValue,
        domainNamespace), "Failed to replace String ");
    CommandParams params = new CommandParams().defaults();
    params.command("kubectl apply -f " + targetHPAFile);
    ExecResult result = Command.withParams(params).executeAndReturnResult();
    assertTrue(result.exitValue() == 0,
        "Failed to create hpa or autoscale, result " + result);
    // wait till autoscaler could get the current cpu utilization to make sure it is ready
    testUntil(withStandardRetryPolicy,
        () -> verifyHPA(domainNamespace, "custommetrics-hpa"),
        logger,
        "Get sum_of_total_opened_sessions_exporter_app from hpa {0} in namespace {1}",
        "custommetrics-hpa",
        domainNamespace);
  }

  // verify hpa is getting the metrics
  private boolean verifyHPA(String namespace, String hpaName) {
    CommandParams params = new CommandParams().defaults();
    params.command("kubectl get hpa " + hpaName + " -n " + namespace);

    ExecResult result = Command.withParams(params).executeAndReturnResult();
    /* check if hpa output contains something like 7%/50%
     * kubectl get hpa --all-namespaces
     * NAMESPACE   NAME         REFERENCE            TARGETS   MINPODS   MAXPODS   REPLICAS   AGE
     * ns-qsjlcw   hpacluster   Cluster/hpacluster   4%/50%    2         3        3          18m
     */
    logger.info(result.stdout());
    return result.stdout().contains(hpaName);
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
    params.command("kubectl delete -f " + targetHPAFile);
    ExecResult result = Command.withParams(params).executeAndReturnResult();
    assertTrue(result.exitValue() == 0,
        "Failed to delete hpa , result " + result);
    if (prometheusAdapterHelmParams != null) {
      Helm.uninstall(prometheusAdapterHelmParams);
    }
    if (!OKD) {
      deletePersistentVolumeClaim("pvc-alertmanager" + releaseSuffix, monitoringNS);
      deletePersistentVolume("pv-testalertmanager" + releaseSuffix);
      deletePersistentVolumeClaim("pvc-" + prometheusReleaseName, monitoringNS);
      deletePersistentVolume("pv-test" + prometheusReleaseName);

    }
    deleteNamespace(monitoringNS);
    deleteMonitoringExporterTempDir(monitoringExporterDir);
  }

}
