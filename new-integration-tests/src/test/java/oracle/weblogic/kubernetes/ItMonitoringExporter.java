// Copyright (c) 2020, Oracle Corporation and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.weblogic.kubernetes;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
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
import io.kubernetes.client.openapi.models.V1ObjectMetaBuilder;
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
import oracle.weblogic.kubernetes.extensions.LoggedTest;
import oracle.weblogic.kubernetes.utils.CommonTestUtils;
import org.apache.commons.io.FileUtils;
import org.awaitility.core.ConditionFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import static java.util.concurrent.TimeUnit.MINUTES;
import static java.util.concurrent.TimeUnit.SECONDS;
import static oracle.weblogic.kubernetes.TestConstants.ADMIN_PASSWORD_DEFAULT;
import static oracle.weblogic.kubernetes.TestConstants.ADMIN_USERNAME_DEFAULT;
import static oracle.weblogic.kubernetes.TestConstants.DOMAIN_API_VERSION;
import static oracle.weblogic.kubernetes.TestConstants.DOMAIN_VERSION;
import static oracle.weblogic.kubernetes.TestConstants.MONITORING_EXPORTER_VERSION;
import static oracle.weblogic.kubernetes.TestConstants.PV_ROOT;
import static oracle.weblogic.kubernetes.TestConstants.REPO_NAME;
import static oracle.weblogic.kubernetes.TestConstants.REPO_SECRET_NAME;
import static oracle.weblogic.kubernetes.TestConstants.RESULTS_ROOT;
import static oracle.weblogic.kubernetes.TestConstants.WDT_BASIC_IMAGE_NAME;
import static oracle.weblogic.kubernetes.TestConstants.WDT_BASIC_IMAGE_TAG;
import static oracle.weblogic.kubernetes.actions.ActionConstants.MONITORING_EXPORTER_DOWNLOAD_URL;
import static oracle.weblogic.kubernetes.actions.ActionConstants.RESOURCE_DIR;
import static oracle.weblogic.kubernetes.actions.TestActions.createDomainCustomResource;
import static oracle.weblogic.kubernetes.actions.TestActions.createPersistentVolume;
import static oracle.weblogic.kubernetes.actions.TestActions.createPersistentVolumeClaim;
import static oracle.weblogic.kubernetes.actions.TestActions.deletePersistentVolume;
import static oracle.weblogic.kubernetes.actions.TestActions.deletePersistentVolumeClaim;
import static oracle.weblogic.kubernetes.actions.TestActions.uninstallNginx;
import static oracle.weblogic.kubernetes.actions.impl.primitive.Kubernetes.createNamespace;
import static oracle.weblogic.kubernetes.actions.impl.primitive.Kubernetes.deleteDomainCustomResource;
import static oracle.weblogic.kubernetes.actions.impl.primitive.Kubernetes.deleteNamespace;
import static oracle.weblogic.kubernetes.assertions.TestAssertions.domainExists;
import static oracle.weblogic.kubernetes.assertions.impl.Kubernetes.listPods;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.checkPodExists;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.checkPodReady;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.checkServiceExists;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.createDockerRegistrySecret;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.createSecretWithUsernamePassword;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.installAndVerifyNginx;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.installAndVerifyOperator;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.installAndVerifyPrometheus;
import static oracle.weblogic.kubernetes.utils.TestUtils.getNextFreePort;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.with;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verify the model in image domain with multiple clusters can be scaled up and down.
 * Also verify the sample application can be accessed via NGINX ingress controller.
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DisplayName("Verify WebLogic Metric is processed as expected by MonitoringExporter via Prometheus and Grafana")
@IntegrationTest
class ItMonitoringExporter implements LoggedTest {


  // domain constants
  private static final String domainUid = "domain1";
  private static final int NUMBER_OF_CLUSTERS = 2;
  private static final String CLUSTER_NAME_PREFIX = "cluster-";
  private static final int MANAGED_SERVER_PORT = 8001;
  private static final int replicaCount = 2;

  private static String domain1Namespace = null;
  private static String domain2Namespace = null;
  private String domain1Uid = "domain1";
  private String domain2Uid = "domain2";
  private static HelmParams nginxHelmParams = null;
  private static int nodeportshttp = 0;
  private static List<String> ingressHostList = null;

  private String curlCmd = null;
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

  /**
   * Install operator and NGINX. Create model in image domain with multiple clusters.
   * Create ingress for the domain.
   *
   * @param namespaces list of namespaces created by the IntegrationTestWatcher by the
   *                   JUnit engine parameter resolution mechanism
   */
  @BeforeAll
  public static void initAll(@Namespaces(6) List<String> namespaces) {

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

    // get a free node port for NGINX
    nodeportshttp = getNextFreePort(30305, 30405);
    int nodeportshttps = getNextFreePort(30443, 30543);

    //install monitoring exporter
    installMonitoringExporter();

    // install and verify NGINX
    nginxHelmParams = installAndVerifyNginx(nginxNamespace, nodeportshttp, nodeportshttps);

  }

  @Test
  @DisplayName("Install Prometheus, Grafana , Webhook, Coordinator and verify it running")
  public void testCreatePrometheusGrafanaWebhookCoordinator() throws Exception {

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

    replaceStringInFile(targetPromFile.toString(),
        "regex: default;domain1;cluster-1",
        "regex: " + domain1Namespace
        + ";"
        + domain1Uid
        + ";cluster-1");
    replaceStringInFile(targetPromFile.toString(),
        "regex: default;domain2;cluster-1",
        "regex: " + domain2Namespace
            + ";"
            + domain2Uid
            + ";cluster-1");
    int nodeportalertmanserver = getNextFreePort(30400, 30600);
    int nodeportserver = getNextFreePort(32400, 32600);

    promHelmParams = installAndVerifyPrometheus("prometheus",
         monitoringNS,
        targetPromFile.toString(),
         null,
         nodeportserver,
         nodeportalertmanserver);
    logger.info("Prometheus is running");

    int nodeportgrafana = getNextFreePort(31000, 31200);
    grafanaHelmParams = CommonTestUtils.installAndVerifyGrafana("grafana",
        monitoringNS,
        monitoringExporterEndToEndDir + "/grafana/values.yaml",
        null,
        nodeportgrafana);
    logger.info("Grafana is running");
    assertTrue(installAndVerifyPodFromCustomImage(monitoringExporterEndToEndDir + "/webhook",
        "webhook",
        webhookNS,
        "app=webhook"), "Failed to start webhook");
    assertTrue(installAndVerifyPodFromCustomImage(monitoringExporterSrcDir + "/config_coordinator",
        "coordinator",
        domain1Namespace,
        "app=coordinator"), "Failed to start coordinator");
  }


  /**
   * TODO: remove this after Sankar's PR is merged
   * The cleanup framework does not uninstall NGINX release. Do it here for now.
   */
  @AfterAll
  public void tearDownAll() {

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

    Path pvHostPath = Files.createDirectories(Paths.get(
        PV_ROOT, this.getClass().getSimpleName(), "test" + nameSuffix));
    logger.info("Creating PV directory {0}", pvHostPath);
    FileUtils.deleteDirectory(pvHostPath.toFile());
    Files.createDirectories(pvHostPath);
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
        .metadata(new V1ObjectMetaBuilder()
            .withName("pv-test" + nameSuffix)
            .withNamespace(monitoringNS)
            .build());

    V1PersistentVolume finalV1pv = v1pv;
    boolean success = assertDoesNotThrow(
        () -> createPersistentVolume(finalV1pv),
        "Persistent volume creation failed, "
            + "look at the above console log messages for failure reason in ApiException responsebody"
    );
    assertTrue(success, "PersistentVolume creation failed");


    V1PersistentVolumeClaim v1pvc = new V1PersistentVolumeClaim()
        .spec(new V1PersistentVolumeClaimSpec()
            .addAccessModesItem("ReadWriteMany")
            .storageClassName(nameSuffix)
            .volumeName("pv-test" + nameSuffix)
            .resources(new V1ResourceRequirements()
                .putRequestsItem("storage", Quantity.fromString("10Gi"))))
        .metadata(new V1ObjectMetaBuilder()
            .withName("pvc-" + nameSuffix)
            .withNamespace(monitoringNS)
            .build());


    V1PersistentVolumeClaim finalV1pvc = v1pvc;
    success = assertDoesNotThrow(
        () -> createPersistentVolumeClaim(finalV1pvc),
        "Persistent volume claim creation failed for " + nameSuffix
            + "look at the above console log messages for failure reason in ApiException response body"
    );
    assertTrue(success, "PersistentVolumeClaim creation failed for " + nameSuffix);
  }

  private void createDomaininImageWdt(String domainNamespace, String domainUid, int repCount) {
    // admin/managed server name here should match with model yaml in WDT_MODEL_FILE
    final String adminServerPodName = domainUid + "-admin-server";
    final String managedServerPrefix = domainUid + "-managed-server";
    final int replicaCount = repCount;

    // Create the repo secret to pull the image
    createDockerRegistrySecret(domainNamespace);

    // create secret for admin credentials
    logger.info("Create secret for admin credentials");
    String adminSecretName = "weblogic-credentials";
    createSecretWithUsernamePassword(adminSecretName, domainNamespace, ADMIN_USERNAME_DEFAULT, ADMIN_PASSWORD_DEFAULT);

    // create the domain CR
    createDomainResource(domainUid, domainNamespace, adminSecretName, REPO_SECRET_NAME,
        replicaCount);

    // wait for the domain to exist
    logger.info("Check for domain custom resource in namespace {0}", domainNamespace);
    withStandardRetryPolicy
        .conditionEvaluationListener(
            condition -> logger.info("Waiting for domain {0} to be created in namespace {1} "
                    + "(elapsed time {2}ms, remaining time {3}ms)",
                domainUid,
                domainNamespace,
                condition.getElapsedTimeInMS(),
                condition.getRemainingTimeInMS()))
        .until(domainExists(domainUid, DOMAIN_VERSION, domainNamespace));


    // check admin server pod exists
    logger.info("Check for admin server pod {0} existence in namespace {1}",
        adminServerPodName, domainNamespace);
    checkPodExists(adminServerPodName, domainUid, domainNamespace);

    // check managed server pods exist
    for (int i = 1; i <= replicaCount; i++) {
      logger.info("Check for managed server pod {0} existence in namespace {1}",
          managedServerPrefix + i, domainNamespace);
      checkPodExists(managedServerPrefix + i, domainUid, domainNamespace);
    }

    // check admin server pod is ready
    logger.info("Wait for admin server pod {0} to be ready in namespace {1}",
        adminServerPodName, domainNamespace);
    checkPodReady(adminServerPodName, domainUid, domainNamespace);

    // check managed server pods are ready
    for (int i = 1; i <= replicaCount; i++) {
      logger.info("Wait for managed server pod {0} to be ready in namespace {1}",
          managedServerPrefix + i, domainNamespace);
      checkPodReady(managedServerPrefix + i, domainUid, domainNamespace);
    }

    logger.info("Check admin service {0} is created in namespace {1}",
        adminServerPodName, domainNamespace);
    checkServiceExists(adminServerPodName, domainNamespace);

    // check managed server services created
    for (int i = 1; i <= replicaCount; i++) {
      logger.info("Check managed server service {0} is created in namespace {1}",
          managedServerPrefix + i, domainNamespace);
      checkServiceExists(managedServerPrefix + i, domainNamespace);
    }

  }

  private void createDomainResource(String domainUid, String domNamespace, String adminSecretName,
                                    String repoSecretName, int replicaCount) {
    // create the domain CR
    Domain domain = new Domain()
        .apiVersion(DOMAIN_API_VERSION)
        .kind("Domain")
        .metadata(new V1ObjectMeta()
            .name(domainUid)
            .namespace(domNamespace))
        .spec(new DomainSpec()
            .domainUid(domainUid)
            .domainHomeSourceType("Image")
            .image(WDT_BASIC_IMAGE_NAME + ":" + WDT_BASIC_IMAGE_TAG)
            .addImagePullSecretsItem(new V1LocalObjectReference()
                .name(repoSecretName))
            .webLogicCredentialsSecret(new V1SecretReference()
                .name(adminSecretName)
                .namespace(domNamespace))
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
                .clusterName("cluster-1")
                .replicas(replicaCount)
                .serverStartState("RUNNING"))
            .configuration(new Configuration()
                .model(new Model()
                    .domainType("WLS"))
                .introspectorJobActiveDeadlineSeconds(300L)));

    logger.info("Create domain custom resource for domainUid {0} in namespace {1}",
        domainUid, domNamespace);
    boolean domCreated = assertDoesNotThrow(() -> createDomainCustomResource(domain),
        String.format("Create domain custom resource failed with ApiException for %s in namespace %s",
            domainUid, domNamespace));
    assertTrue(domCreated, String.format("Create domain custom resource failed with ApiException "
        + "for %s in namespace %s", domainUid, domNamespace));
  }

  /**
   * A utility method to sed files.
   *
   * @throws IOException when copying files from source location to staging area fails
   */
  private static void replaceStringInFile(String filePath, String oldValue, String newValue)
      throws IOException {
    Path src = Paths.get(filePath);
    logger.info("Copying {0}", src.toString());
    Charset charset = StandardCharsets.UTF_8;
    String content = new String(Files.readAllBytes(src), charset);
    content = content.replaceAll(oldValue, newValue);
    logger.info("to {0}", src.toString());
    Files.write(src, content.getBytes(charset));
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
                                                String labelSelector) throws ApiException {
    //build webhook image
    String imagePullPolicy = "IfNotPresent";
    if (!REPO_NAME.isEmpty()) {
      imagePullPolicy = "Always";
    }
    String image = createPushImage(dockerFileDir,baseImageName, namespace);
    logger.info("Installing {0} in namespace {1}", baseImageName, namespace);
    if (baseImageName.equalsIgnoreCase(("webhook"))) {
      createWebHook(image, imagePullPolicy, namespace);
    } else if (baseImageName.contains("coordinator")) {
      createCoordinator(image, imagePullPolicy, namespace);
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
   */
  private static void createWebHook(String image, String imagePullPolicy, String namespace) throws ApiException {
    Map labels = new HashMap<String, String>();
    labels.put("app", "webhook");
    webhookDepl = new V1Deployment()
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
                            .name("ocir-secret"))))));

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
      if (webhookDepl != null) {
        deploymentName = webhookDepl.getMetadata().getName();
        namespace = webhookDepl.getMetadata().getNamespace();
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
   * Create Webhook deployment and service.
   *
   * @param image full image name for deployment
   * @param imagePullPolicy policy for image
   * @param namespace webhook namespace
   */
  private static void createCoordinator(String image, String imagePullPolicy, String namespace) throws ApiException {
    Map labels = new HashMap<String, String>();
    labels.put("app", "coordinator");
    coordinatorDepl = new V1Deployment()
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
                            .name("ocir-secret"))))));

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
    assertDoesNotThrow(() -> Kubernetes.createService(coordinatorService),
        String.format("Create service failed with ApiException for coordinator in namespace %s",
            namespace));
  }

  /**
   * Checks if the pod is running in a given namespace.
   * The method assumes the pod name to starts with podName
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
   * @param namespace webhook namespace
   * @return image name
   */
  public static String createPushImage(String dockerFileDir, String baseImageName,
                                                    String namespace) throws ApiException {
    // create unique image name with date
    DateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd");
    Date date = new Date();
    final String imageTag = dateFormat.format(date) + "-" + System.currentTimeMillis();
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
    createDockerRegistrySecret(namespace);
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
    command = String.format("%s  %s/exporter/rest_webapp.yml", monitoringExporterBuildFile, RESOURCE_DIR);
    assertTrue(new Command()
        .withParams(new CommandParams()
            .command(command))
        .execute(),"Failed to build monitoring exporter webapp");
  }

  private static void uninstallMonitoringExporter() {
    logger.info("create a staging location for monitoring exporter github");
    Path monitoringTemp = Paths.get(RESULTS_ROOT, "monitoringexp", "srcdir");
    assertDoesNotThrow(() -> FileUtils.deleteDirectory(monitoringTemp.toFile()));
    Path fileTemp = Paths.get(RESULTS_ROOT, "ItMonitoringExporter", "promCreateTempValueFile");
    assertDoesNotThrow(() -> FileUtils.deleteDirectory(fileTemp.toFile()));
  }
}