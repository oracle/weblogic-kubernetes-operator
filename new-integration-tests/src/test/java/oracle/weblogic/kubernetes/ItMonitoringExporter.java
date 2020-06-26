// Copyright (c) 2020, Oracle Corporation and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.weblogic.kubernetes;

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
import java.util.concurrent.Callable;

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
import oracle.weblogic.kubernetes.logging.LoggingFacade;
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
import static oracle.weblogic.kubernetes.actions.ActionConstants.MODEL_DIR;
import static oracle.weblogic.kubernetes.actions.ActionConstants.MONITORING_EXPORTER_DOWNLOAD_URL;
import static oracle.weblogic.kubernetes.actions.ActionConstants.RESOURCE_DIR;
import static oracle.weblogic.kubernetes.actions.TestActions.deleteImage;
import static oracle.weblogic.kubernetes.actions.TestActions.deletePersistentVolume;
import static oracle.weblogic.kubernetes.actions.TestActions.deletePersistentVolumeClaim;
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
import static oracle.weblogic.kubernetes.utils.TestUtils.callWebAppAndCheckForServerNameInResponse;
import static oracle.weblogic.kubernetes.utils.TestUtils.getNextFreePort;
import static oracle.weblogic.kubernetes.utils.ThreadSafeLogger.getLogger;
import static org.apache.commons.io.FileUtils.deleteDirectory;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.with;
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
class ItMonitoringExporter {


  // domain constants
  private static final int replicaCount = 2;
  private static String domain1Namespace = null;
  private static String domain2Namespace = null;
  private static String domain1Uid = "monexp-domain1";
  private static String domain2Uid = "monexp-domain2";
  private static HelmParams nginxHelmParams = null;
  private static int nodeportshttp = 0;
  private static List<String> ingressHostList = null;

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
  private static final String MONEXP_IMAGE_NAME = "miimonexp-image";
  private static final String SESSMIGR_APP_NAME = "sessmigr-app";

  private static String clusterName = "cluster-1";
  private static String adminServerPodName = domain1Uid + "-admin-server";
  private static String managedServerPrefix = domain1Uid + "-managed-server";
  private static String miiImage = null;
  private static String webhookImage = null;
  private static String  coordinatorImage = null;
  private static int managedServerPort = 8001;
  private static int nodeportserver;
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

    // get a unique operator namespace
    logger.info("Get a unique namespace for operator");
    assertNotNull(namespaces.get(0), "Namespace list is null");
    final String opNamespace = namespaces.get(0);

    // get a unique domain namespace
    logger.info("Get a unique namespace for WebLogic domain1");
    assertNotNull(namespaces.get(1), "Namespace list is null");
    domain1Namespace = namespaces.get(1);

    logger.info("Get a unique namespace for WebLogic domain2");
    assertNotNull(namespaces.get(2), "Namespace list is null");
    domain2Namespace = namespaces.get(2);

    // get a unique monitoring namespace
    logger.info("Get a unique namespace for monitoring");
    assertNotNull(namespaces.get(3), "Namespace list is null");
    monitoringNS = namespaces.get(3);

    // get a unique webhook namespace
    logger.info("Get a unique namespace for webhook");
    assertNotNull(namespaces.get(4), "Namespace list is null");
    webhookNS = namespaces.get(4);

    // get a unique NGINX namespace
    logger.info("Get a unique namespace for NGINX");
    assertNotNull(namespaces.get(5), "Namespace list is null");
    final String nginxNamespace = namespaces.get(5);

    // install and verify operator
    installAndVerifyOperator(opNamespace, domain1Namespace,domain2Namespace);

    //install monitoring exporter
    installMonitoringExporter();

    // create and verify WebLogic domain image using model in image with model files
    String imageName = createAndVerifyDomainImage();

    // create and verify one cluster domain
    logger.info("Create domain and verify that it's running");
    createAndVerifyDomain(imageName, domain1Uid);

    // install and verify NGINX
    nginxHelmParams = installAndVerifyNginx(nginxNamespace, 0, 0);
    String nginxServiceName = nginxHelmParams.getReleaseName() + "-nginx-ingress-controller";
    logger.info("NGINX service name: {0}", nginxServiceName);
    nodeportshttp = getServiceNodePort(nginxNamespace, nginxServiceName, "http");
    logger.info("NGINX http node port: {0}", nodeportshttp);

    // create ingress for the domain
    logger.info("Creating ingress for domain {0} in namespace {1}", domain1Uid, domain1Namespace);
    Map<String, Integer> clusterNameMsPortMap = new HashMap<>();
    clusterNameMsPortMap.put(clusterName, managedServerPort);
    ingressHostList =
        createIngressForDomainAndVerify(domain1Uid, domain1Namespace, clusterNameMsPortMap);

  }

  /**
   * Test covers the following use cases.
   * Create Prometheus, Grafana, Webhook, Coordinator
   * create domain Model in Image with monitoring exporter
   * verify access to monitoring exporter WebLogic metrics via nginx
   * check generated by monitoring exporter WebLogic metrics via Prometheus
   */
  @Test
  @DisplayName("Install Prometheus, Grafana , Webhook, Coordinator and verify WebLogic metrics")
  public void testCheckMetrics() throws Exception {

    installPrometheusGrafana();
    installWebhookCoordinator();

    //verify access to Monitoring Exporter
    verifyMonExpAppAccessThroughNginx();
    //verify metrics via prometheus
    String testappPrometheusSearchKey =
        "weblogic_servlet_invocation_total_count%7Bapp%3D%22wlsexporter%22%7D%5B15s%5D";
    checkMetricsViaPrometheus(testappPrometheusSearchKey, "wlsexporter");
  }

  /**
   * Install Prometheus, Grafana using helm chart, and verify that pods are running.
   * @throws ApiException when creating helm charts or pods fails
   */
  private void installPrometheusGrafana() throws IOException, ApiException {
    createPvAndPvc("prometheus");
    createPvAndPvc("alertmanager");
    createPvAndPvc("grafana");

    logger.info("create a staging location for monitoring creation scripts");
    Path fileTemp = Paths.get(RESULTS_ROOT, "ItMonitoringExporter", "promCreateTempValueFile");
    FileUtils.deleteDirectory(fileTemp.toFile());
    Files.createDirectories(fileTemp);


    logger.info("copy the promvalue.yaml to staging location");
    Path srcPromFile = Paths.get(RESOURCE_DIR, "exporter", "promvalues.yaml");
    Path targetPromFile = Paths.get(fileTemp.toString(), "promvalues.yaml");
    Files.copy(srcPromFile, targetPromFile, StandardCopyOption.REPLACE_EXISTING);
    String newValue = String.format("regex: %s;%s;%s", domain1Namespace,domain1Uid,clusterName);

    replaceStringInFile(targetPromFile.toString(),
        "regex: default;domain1;cluster-1",
        newValue);

    replaceStringInFile(targetPromFile.toString(),
        "regex: default;domain2;cluster-1",
        "regex: " + domain2Namespace
            + ";"
            + domain2Uid
            + ";cluster-1");
    int nodeportalertmanserver = getNextFreePort(30400, 30600);
    nodeportserver = getNextFreePort(32400, 32600);

    promHelmParams = installAndVerifyPrometheus("prometheus",
         monitoringNS,
        targetPromFile.toString(),
         PROMETHEUS_CHART_VERSION,
         nodeportserver,
         nodeportalertmanserver);
    logger.info("Prometheus is running");

    int nodeportgrafana = getNextFreePort(31000, 31200);
    grafanaHelmParams = installAndVerifyGrafana("grafana",
        monitoringNS,
        monitoringExporterEndToEndDir + "/grafana/values.yaml",
        GRAFANA_CHART_VERSION,
        nodeportgrafana);
    logger.info("Grafana is running");
  }

  /**
   * Create a Webhook, Coordinator images, install and verify that pods are running.
   * @throws ApiException when creating images or pods fails
   */
  private void installWebhookCoordinator() throws ApiException {
    assertTrue(installAndVerifyPodFromCustomImage(monitoringExporterEndToEndDir + "/webhook",
        "webhook",
        webhookNS,
        "app=webhook", REPO_SECRET_NAME), "Failed to start webhook");
    assertTrue(installAndVerifyPodFromCustomImage(monitoringExporterSrcDir + "/config_coordinator",
        "coordinator",
        domain1Namespace,
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

    // Delete domain custom resource
    logger.info("Delete domain custom resource in namespace {0}", domain1Namespace);
    assertDoesNotThrow(() -> deleteDomainCustomResource(domain1Uid, domain1Namespace),
        "deleteDomainCustomResource failed with ApiException");
    logger.info("Deleted Domain Custom Resource " + domain1Uid + " from " + domain1Namespace);
    Prometheus.uninstall(promHelmParams);
    logger.info("Prometheus is uninstalled");

    Grafana.uninstall(grafanaHelmParams);
    logger.info("Grafana is uninstalled");

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
    uninstallMonitoringExporter();

    // uninstall NGINX release
    if (nginxHelmParams != null) {
      assertThat(uninstallNginx(nginxHelmParams))
          .as("Test uninstallNginx returns true")
          .withFailMessage("uninstallNginx() did not return true")
          .isTrue();
    }
  }

  /**
   * Create a persistent volume and persistent volume claim.
   * @param nameSuffix unique nameSuffix for pv and pvc to create
   * @throws IOException when creating pv path fails
   */
  private void createPvAndPvc(String nameSuffix) throws IOException {
    logger.info("creating persistent volume and persistent volume claim");
    // create persistent volume and persistent volume claims
    Path pvHostPath = assertDoesNotThrow(
        () -> createDirectories(get(PV_ROOT, this.getClass().getSimpleName(), "monexp-persistentVolume")),
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
            .namespace(monitoringNS)
            .putLabelsItem("weblogic.domainUid", domain1Uid));


    V1PersistentVolumeClaim v1pvc = new V1PersistentVolumeClaim()
        .spec(new V1PersistentVolumeClaimSpec()
            .addAccessModesItem("ReadWriteMany")
            .storageClassName(nameSuffix)
            .volumeName("pv-test" + nameSuffix)
            .resources(new V1ResourceRequirements()
                .putRequestsItem("storage", Quantity.fromString("10Gi"))))
        .metadata(new V1ObjectMeta()
            .name("pvc-" + nameSuffix)
            .namespace(monitoringNS)
            .putLabelsItem("weblogic.domainUid", domain1Uid));
    createPVPVCAndVerify(v1pv,v1pvc, "weblogic.domainUid=" + domain1Uid, monitoringNS);
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

    coordinatorService = new V1Service()
        .metadata(new V1ObjectMeta()
            .name("coordinator")
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
    command = String.format("cd %s && %s  %s/exporter/rest_webapp.yml",
            appDest,
            monitoringExporterBuildFile,
            RESOURCE_DIR);
    assertTrue(new Command()
        .withParams(new CommandParams()
            .command(command))
        .execute(),"Failed to build monitoring exporter webapp");
  }

  private static void uninstallMonitoringExporter() {
    logger.info("delete temp dir for monitoring exporter github");
    Path monitoringTemp = Paths.get(RESULTS_ROOT, "monitoringexp", "srcdir");
    assertDoesNotThrow(() -> FileUtils.deleteDirectory(monitoringTemp.toFile()));
    Path monitoringApp = Paths.get(RESULTS_ROOT, "monitoringexp", "apps");
    assertDoesNotThrow(() -> FileUtils.deleteDirectory(monitoringApp.toFile()));
    Path fileTemp = Paths.get(RESULTS_ROOT, "ItMonitoringExporter", "promCreateTempValueFile");
    assertDoesNotThrow(() -> FileUtils.deleteDirectory(fileTemp.toFile()));
  }

  private static String createAndVerifyDomainImage() {
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

  private static void createAndVerifyDomain(String miiImage, String domainUid) {
    // create secret for admin credentials
    logger.info("Create secret for admin credentials");
    String adminSecretName = "weblogic-credentials";
    assertDoesNotThrow(() -> createSecretWithUsernamePassword(adminSecretName, domain1Namespace,
        "weblogic", "welcome1"),
        String.format("create secret for admin credentials failed for %s", adminSecretName));

    // create encryption secret
    logger.info("Create encryption secret");
    String encryptionSecretName = "encryptionsecret";
    assertDoesNotThrow(() -> createSecretWithUsernamePassword(encryptionSecretName, domain1Namespace,
        "weblogicenc", "weblogicenc"),
        String.format("create encryption secret failed for %s", encryptionSecretName));

    // create domain and verify
    logger.info("Create model in image domain {0} in namespace {1} using docker image {2}",
        domainUid, domain1Namespace, miiImage);
    createDomainCrAndVerify(adminSecretName, REPO_SECRET_NAME, encryptionSecretName, miiImage,domainUid);

    // check that admin server pod exists in the domain namespace
    logger.info("Checking that admin server pod {0} exists in namespace {1}",
        adminServerPodName, domain1Namespace);
    checkPodExists(adminServerPodName, domainUid, domain1Namespace);

    // check that admin server pod is ready
    logger.info("Checking that admin server pod {0} is ready in namespace {1}",
        adminServerPodName, domain1Namespace);
    checkPodReady(adminServerPodName, domainUid, domain1Namespace);

    // check that admin service exists in the domain namespace
    logger.info("Checking that admin service {0} exists in namespace {1}",
        adminServerPodName, domain1Namespace);
    checkServiceExists(adminServerPodName, domain1Namespace);

    // check for managed server pods existence in the domain namespace
    for (int i = 1; i <= replicaCount; i++) {
      String managedServerPodName = managedServerPrefix + i;

      // check that the managed server pod exists
      logger.info("Checking that managed server pod {0} exists in namespace {1}",
          managedServerPodName, domain1Namespace);
      checkPodExists(managedServerPodName, domainUid, domain1Namespace);

      // check that the managed server pod is ready
      logger.info("Checking that managed server pod {0} is ready in namespace {1}",
          managedServerPodName, domain1Namespace);
      checkPodReady(managedServerPodName, domainUid, domain1Namespace);

      // check that the managed server service exists in the domain namespace
      logger.info("Checking that managed server service {0} exists in namespace {1}",
          managedServerPodName, domain1Namespace);
      checkServiceExists(managedServerPodName, domain1Namespace);
    }
  }

  private static void createDomainCrAndVerify(String adminSecretName,
                                              String repoSecretName,
                                              String encryptionSecretName,
                                              String miiImage,
                                              String domainUid) {
    // create the domain CR
    Domain domain = new Domain()
        .apiVersion(DOMAIN_API_VERSION)
        .kind("Domain")
        .metadata(new V1ObjectMeta()
            .name(domainUid)
            .namespace(domain1Namespace))
        .spec(new DomainSpec()
            .domainUid(domainUid)
            .domainHomeSourceType("FromModel")
            .image(miiImage)
            .addImagePullSecretsItem(new V1LocalObjectReference()
                .name(repoSecretName))
            .webLogicCredentialsSecret(new V1SecretReference()
                .name(adminSecretName)
                .namespace(domain1Namespace))
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
        domainUid, domain1Namespace, miiImage);
    createDomainAndVerify(domain, domain1Namespace);
  }

  /**
   * Verify the monitoring exporter app can be accessed from all managed servers in the domain through NGINX.
   */
  private void verifyMonExpAppAccessThroughNginx() {

    List<String> managedServerNames = new ArrayList<>();
    for (int i = 1; i <= replicaCount; i++) {
      managedServerNames.add(MANAGED_SERVER_NAME_BASE + i);
    }

    // check that NGINX can access the sample apps from all managed servers in the domain
    String curlCmd =
        String.format("curl --silent --show-error --noproxy '*' -H 'host: %s' http://%s:%s@%s:%s/wls-exporter/metrics",
            ingressHostList.get(0),
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
        .until(assertDoesNotThrow(() -> searchForKey(curlCmd, expectedVal),
            String.format("Check prometheus metric %s against expected %s",
                searchKey, expectedVal)));
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
        .verbose(false);
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
    return () -> {
      return execCommandCheckResponse(cmd, searchKey);
    };
  }
}