// Copyright (c) 2025, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.weblogic.kubernetes;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import io.kubernetes.client.openapi.ApiException;
import io.kubernetes.client.openapi.models.V1Container;
import io.kubernetes.client.openapi.models.V1EmptyDirVolumeSource;
import io.kubernetes.client.openapi.models.V1EnvVar;
import io.kubernetes.client.openapi.models.V1LocalObjectReference;
import io.kubernetes.client.openapi.models.V1ObjectMeta;
import io.kubernetes.client.openapi.models.V1PersistentVolumeClaimVolumeSource;
import io.kubernetes.client.openapi.models.V1Pod;
import io.kubernetes.client.openapi.models.V1PodList;
import io.kubernetes.client.openapi.models.V1SecurityContext;
import io.kubernetes.client.openapi.models.V1Volume;
import io.kubernetes.client.openapi.models.V1VolumeMount;
import oracle.weblogic.domain.Configuration;
import oracle.weblogic.domain.DomainResource;
import oracle.weblogic.domain.DomainSpec;
import oracle.weblogic.domain.FluentdSpecification;
import oracle.weblogic.domain.MonitoringExporterSpecification;
import oracle.weblogic.domain.ServerPod;
import oracle.weblogic.kubernetes.actions.impl.AppParams;
import oracle.weblogic.kubernetes.annotations.IntegrationTest;
import oracle.weblogic.kubernetes.annotations.Namespaces;
import oracle.weblogic.kubernetes.logging.LoggingFacade;
import oracle.weblogic.kubernetes.utils.ExecResult;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static oracle.weblogic.kubernetes.TestConstants.ADMIN_PASSWORD_DEFAULT;
import static oracle.weblogic.kubernetes.TestConstants.ADMIN_USERNAME_DEFAULT;
import static oracle.weblogic.kubernetes.TestConstants.BASE_IMAGES_REPO_SECRET_NAME;
import static oracle.weblogic.kubernetes.TestConstants.DOMAIN_API_VERSION;
import static oracle.weblogic.kubernetes.TestConstants.ELASTICSEARCH_HTTP_PORT;
import static oracle.weblogic.kubernetes.TestConstants.FLUENTD_IMAGE;
import static oracle.weblogic.kubernetes.TestConstants.IMAGE_PULL_POLICY;
import static oracle.weblogic.kubernetes.TestConstants.K8S_NODEPORT_HOST;
import static oracle.weblogic.kubernetes.TestConstants.MII_AUXILIARY_IMAGE_NAME;
import static oracle.weblogic.kubernetes.TestConstants.MII_BASIC_APP_NAME;
import static oracle.weblogic.kubernetes.TestConstants.MII_BASIC_IMAGE_TAG;
import static oracle.weblogic.kubernetes.TestConstants.MII_BASIC_WDT_MODEL_FILE;
import static oracle.weblogic.kubernetes.TestConstants.RESULTS_ROOT;
import static oracle.weblogic.kubernetes.TestConstants.RESULTS_TEMPFILE;
import static oracle.weblogic.kubernetes.TestConstants.TEST_IMAGES_REPO_SECRET_NAME;
import static oracle.weblogic.kubernetes.TestConstants.WEBLOGIC_IMAGE_TO_USE_IN_SPEC;
import static oracle.weblogic.kubernetes.actions.ActionConstants.ARCHIVE_DIR;
import static oracle.weblogic.kubernetes.actions.ActionConstants.MODEL_DIR;
import static oracle.weblogic.kubernetes.actions.ActionConstants.RESOURCE_DIR;
import static oracle.weblogic.kubernetes.actions.TestActions.buildAppArchive;
import static oracle.weblogic.kubernetes.actions.TestActions.defaultAppParams;
import static oracle.weblogic.kubernetes.actions.TestActions.deleteDomainCustomResource;
import static oracle.weblogic.kubernetes.actions.TestActions.execCommand;
import static oracle.weblogic.kubernetes.actions.TestActions.listPods;
import static oracle.weblogic.kubernetes.utils.AuxiliaryImageUtils.createPushAuxiliaryImageWithDomainConfig;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.checkPodReadyAndServiceExists;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.getImageBuilderExtraArgs;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.getNextFreePort;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.getUniqueName;
import static oracle.weblogic.kubernetes.utils.ConfigMapUtils.createConfigMapForDomainCreation;
import static oracle.weblogic.kubernetes.utils.DomainUtils.createDomainAndVerify;
import static oracle.weblogic.kubernetes.utils.ImageUtils.createBaseRepoSecret;
import static oracle.weblogic.kubernetes.utils.IstioUtils.createAdminServer;
import static oracle.weblogic.kubernetes.utils.JobUtils.createDomainJob;
import static oracle.weblogic.kubernetes.utils.MonitoringUtils.buildMonitoringExporterCreateImageAndPushToRepo;
import static oracle.weblogic.kubernetes.utils.MonitoringUtils.installMonitoringExporter;
import static oracle.weblogic.kubernetes.utils.OperatorUtils.installAndVerifyOperator;
import static oracle.weblogic.kubernetes.utils.PersistentVolumeUtils.createPV;
import static oracle.weblogic.kubernetes.utils.PersistentVolumeUtils.createPVC;
import static oracle.weblogic.kubernetes.utils.PodUtils.getPodName;
import static oracle.weblogic.kubernetes.utils.PodUtils.setPodAntiAffinity;
import static oracle.weblogic.kubernetes.utils.SecretUtils.createSecretWithUsernamePassword;
import static oracle.weblogic.kubernetes.utils.SecretUtils.createSecretWithUsernamePasswordElk;
import static oracle.weblogic.kubernetes.utils.ThreadSafeLogger.getLogger;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

@DisplayName("Verify /tmp is mounted as tmpfs across containers in PV domains and "
    + "readOnlyRootFilesystem flag enabled in its security context in each container")
@IntegrationTest
@Tag("kind-parallel")
@Tag("oke-parallel")
class ItReadOnlyRootFS {

  private static String opNamespace;
  private static String domainNamespace;
  private final String wlSecretName = "weblogic-credentials";
  private static LoggingFacade logger;
  final String managedServerNameBase = "managed-";

  private final String adminServerName = "admin-server";
  private static String exporterImage = null;
  final int replicaCount = 2;
  private static final String FLUENTD_CONFIGMAP_YAML = "fluentd.configmap.elk.yaml";
  private final List<String> domainsToClean = new ArrayList<>();


  @BeforeAll
  public static void initAll(@Namespaces(2) List<String> namespaces) {
    logger = getLogger();
    opNamespace = namespaces.get(0);
    domainNamespace = namespaces.get(1);
    installAndVerifyOperator(opNamespace, domainNamespace);
    String monitoringExporterDir = Paths.get(RESULTS_ROOT,
        "ItReadOnlyRootFS", "monitoringexp", domainNamespace).toString();
    logger.info("install monitoring exporter");
    installMonitoringExporter(monitoringExporterDir);
    String monitoringExporterSrcDir = Paths.get(monitoringExporterDir, "srcdir").toString();
    exporterImage = assertDoesNotThrow(() ->
            buildMonitoringExporterCreateImageAndPushToRepo(monitoringExporterSrcDir, "exporter",
                domainNamespace, TEST_IMAGES_REPO_SECRET_NAME, getImageBuilderExtraArgs()),
        "Failed to create image for exporter");
  }

  @AfterEach
  public void cleanupDomains() {
    for (String domainUid : domainsToClean) {
      logger.info("Cleaning up domain {0} in namespace {1}", domainUid, domainNamespace);
      deleteDomainCustomResource(domainUid, domainNamespace);
    }
    domainsToClean.clear(); // Reset for next test
  }

  @Test
  @DisplayName("fluentd with exporter")
  void testFluentdWithExporter() {
    logger.info("Starting test: fluentd with exporter");
    assertDoesNotThrow(() -> runDomainWithOptions("fluentd", true));
    logger.info("Finished test: fluentd with exporter");
  }

  @Test
  @DisplayName("fluentd without exporter")
  void testFluentdWithoutExporter() {
    logger.info("Starting test: fluentd without exporter");
    assertDoesNotThrow(() -> runDomainWithOptions("fluentd", false));
    logger.info("Finished test: fluentd without exporter");
  }

  @Test
  @DisplayName("fluentbit with exporter")
  void testFluentbitWithExporter() {
    logger.info("Starting test: fluentbit with exporter");
    assertDoesNotThrow(() -> runDomainWithOptions("fluentbit", true));
    logger.info("Finished test: fluentbit with exporter");
  }

  @Test
  @DisplayName("fluentbit without exporter")
  void testFluentbitWithoutExporter() {
    logger.info("Starting test: fluentbit without exporter");
    assertDoesNotThrow(() -> runDomainWithOptions("fluentbit", false));
    logger.info("Finished test: fluentbit without exporter");
  }

  @Test
  @DisplayName("no logging with exporter")
  void testNoLoggingWithExporter() {
    logger.info("Starting test: no logging with exporter");
    assertDoesNotThrow(() -> runDomainWithOptions("none", true));
    logger.info("Finished test: no logging with exporter");
  }

  @Test
  @DisplayName("no logging without exporter")
  void testNoLoggingWithoutExporter() {
    logger.info("Starting test: no logging without exporter");
    assertDoesNotThrow(() -> runDomainWithOptions("none", false));
    logger.info("Finished test: no logging without exporter");
  }

  @Test
  @DisplayName("auxiliary init container with readOnlyRootFilesystem and emptyDir domainHome")
  void testAuxiliaryInitContainerWithReadOnlyFSOnEmptyDir() throws Exception {
    logger.info("Starting test: auxiliary init container with readOnlyRootFilesystem and emptyDir domainHome");

    String domainUid = "auxemptyreadonly";
    String domainHomeBase = "/u02";
    String domainHome = domainHomeBase + "/domains/" + domainUid;
    String adminServerPodName = domainUid + "-admin-server";
    String managedServerPodPrefix = domainUid + "-managed-server";

    // Prepare secrets
    createBaseRepoSecret(domainNamespace);
    createSecretWithUsernamePassword(wlSecretName + domainUid, domainNamespace,
        ADMIN_USERNAME_DEFAULT, ADMIN_PASSWORD_DEFAULT);

    // Build and push auxiliary image
    String auxImageName = buildAndPushAuxiliaryImage();

    // Create domain resource with auxiliary image and readonly FS
    DomainResource domain = createDomainResourceWithAuxiliaryImageAndReadOnlyFS(
        domainUid, domainHomeBase, domainHome, auxImageName);

    setPodAntiAffinity(domain);
    createDomainAndTrackForCleanup(domain, domainNamespace);

    // Check pod readiness
    checkPodReadyAndServiceExists(adminServerPodName, domainUid, domainNamespace);
    for (int i = 1; i <= replicaCount; i++) {
      checkPodReadyAndServiceExists(managedServerPodPrefix + i, domainUid, domainNamespace);
    }

    logger.info("Verifying /tmp mounted as tmpfs and readOnly filesystem for all containers including auxiliary");
    verifyAllPodsTmpfsAndReadOnlyFS(domainNamespace, domainUid);
  }

  private String buildAndPushAuxiliaryImage() throws IOException {
    logger.info("Building auxiliary image for test");

    List<String> modelFiles = List.of(
        MODEL_DIR + "/" + MII_BASIC_WDT_MODEL_FILE,
        MODEL_DIR + "/multi-model-one-ds.20.yaml"
    );

    String appDir = "sample-app";
    AppParams appParams = defaultAppParams()
        .appArchiveDir(ARCHIVE_DIR + ItReadOnlyRootFS.class.getSimpleName());

    assertTrue(
        buildAppArchive(appParams.srcDirList(List.of(appDir))),
        String.format("Failed to create application archive for %s", MII_BASIC_APP_NAME)
    );

    String archiveFile = String.format("%s/%s.zip", appParams.appArchiveDir(), MII_BASIC_APP_NAME);
    List<String> archiveFiles = Collections.singletonList(archiveFile);

    String auxImageTag = MII_BASIC_IMAGE_TAG;
    String auxImageName = MII_AUXILIARY_IMAGE_NAME + ":" + auxImageTag;

    assertDoesNotThrow(() ->
            createPushAuxiliaryImageWithDomainConfig(MII_AUXILIARY_IMAGE_NAME, auxImageTag, archiveFiles, modelFiles),
        "Failed to create auxiliary image");

    return auxImageName;
  }

  private DomainResource createDomainResourceWithAuxiliaryImageAndReadOnlyFS(
      String domainUid, String domainHomeBase, String domainHome, String auxImageName) {

    return new DomainResource()
        .apiVersion(DOMAIN_API_VERSION)
        .kind("Domain")
        .metadata(new V1ObjectMeta()
            .name(domainUid)
            .namespace(domainNamespace))
        .spec(new DomainSpec()
            .domainUid(domainUid)
            .domainHome(domainHome)
            .domainHomeSourceType("FromModel")
            .image(WEBLOGIC_IMAGE_TO_USE_IN_SPEC)
            .imagePullPolicy(IMAGE_PULL_POLICY)
            .replicas(replicaCount)
            .webLogicCredentialsSecret(new V1LocalObjectReference()
                .name(wlSecretName + domainUid))
            .includeServerOutInPodLog(true)
            .logHomeEnabled(true)
            .logHome(domainHomeBase + "/logs/" + domainUid)
            .serverStartPolicy("IfNeeded")
            .serverPod(buildServerPod(domainHomeBase))
            .configuration(buildConfiguration(auxImageName, wlSecretName + domainUid)));
  }

  private ServerPod buildServerPod(String domainHomeBase) {
    return new ServerPod()
        .addEnvItem(new V1EnvVar()
            .name("JAVA_OPTIONS")
            .value("-Dweblogic.security.SSL.ignoreHostnameVerification=true "
                + "-Dweblogic.StdoutDebugEnabled=false"))
        .addEnvItem(new V1EnvVar()
            .name("USER_MEM_ARGS")
            .value("-Djava.security.egd=file:/dev/./urandom"))
        .containerSecurityContext(new V1SecurityContext()
            .readOnlyRootFilesystem(true))
        .addVolumesItem(new V1Volume()
            .name("domain-dir")
            .emptyDir(new V1EmptyDirVolumeSource()))
        .addVolumeMountsItem(new V1VolumeMount()
            .mountPath(domainHomeBase)
            .name("domain-dir"));
  }

  private Configuration buildConfiguration(String auxImageName, String runtimeEncryptionSecretName) {
    return new Configuration()
        .model(new oracle.weblogic.domain.Model()
            .domainType("WLS")
            .runtimeEncryptionSecret(runtimeEncryptionSecretName)
            .withAuxiliaryImage(new oracle.weblogic.domain.AuxiliaryImage()
                .image(auxImageName)
                .imagePullPolicy(IMAGE_PULL_POLICY)
                .sourceWDTInstallHome("/auxiliary/weblogic-deploy")
                .sourceModelHome("/auxiliary/models")));
  }


  private void runDomainWithOptions(String logType, boolean exporterEnabled)
      throws IOException, ApiException {
    logger.info("Running domain test with logType: {0}, exporterEnabled: {1}", logType, exporterEnabled);
    String testSuffix = logType + (exporterEnabled ? "exp" : "noexp");
    String domainUid = "dpv" + testSuffix;

    String adminServerPodName = domainUid + "-" + adminServerName;
    String managedServerPodNamePrefix = domainUid + "-" + managedServerNameBase;

    String pvName = getUniqueName(domainUid + "-pv");
    String pvcName = getUniqueName(domainUid + "-pvc");

    createBaseRepoSecret(domainNamespace);

    if ("fluentd".equals(logType)) {
      logger.info("Create secret for admin credentials");

      String elasticSearchHost = "elasticsearch." + domainNamespace + ".svc";
      assertDoesNotThrow(() -> createSecretWithUsernamePasswordElk(wlSecretName + domainUid, domainNamespace,
              ADMIN_USERNAME_DEFAULT, ADMIN_PASSWORD_DEFAULT,
              elasticSearchHost, String.valueOf(ELASTICSEARCH_HTTP_PORT)),
          String.format("create secret for admin credentials failed for %s", wlSecretName + domainUid));
    } else {
      createSecretWithUsernamePassword(wlSecretName + domainUid,
          domainNamespace, ADMIN_USERNAME_DEFAULT, ADMIN_PASSWORD_DEFAULT);
    }

    createPV(pvName, domainUid, this.getClass().getSimpleName());
    createPVC(pvName, pvcName, domainUid, domainNamespace);

    File domainPropsFile = createDomainProperties(domainUid);
    Path wlstScript = Paths.get(RESOURCE_DIR, "python-scripts", "sit-config-create-domain.py");
    createDomainOnPVUsingWlst(wlstScript, domainPropsFile.toPath(), pvName, pvcName, domainNamespace, domainUid);

    DomainResource domain = buildDomainResource(domainUid, pvName, pvcName, logType, exporterEnabled);
    setPodAntiAffinity(domain);

    createDomainAndTrackForCleanup(domain, domainNamespace);
    // verify the admin server service created
    checkPodReadyAndServiceExists(adminServerPodName, domainUid, domainNamespace);

    // verify managed server services created
    for (int i = 1; i <= replicaCount; i++) {
      logger.info("Checking managed service {0} is created in namespace {1}",
          managedServerPodNamePrefix + i, domainNamespace);
      checkPodReadyAndServiceExists(managedServerPodNamePrefix + i, domainUid, domainNamespace);

    }

    logger.info("Domain {0} deployed and verified successfully", domainUid);
    verifyAllPodsTmpfsAndReadOnlyFS(domainNamespace, domainUid);
  }

  private File createDomainProperties(String domainUid) {
    try {
      File props = assertDoesNotThrow(() ->
              File.createTempFile("domain-" + domainUid, ".properties", new File(RESULTS_TEMPFILE)),
          "Failed to create domain properties file");
      Properties p = new Properties();

      // Base domain properties
      p.setProperty("domain_path", "/shared/" + domainNamespace + "/domains");
      p.setProperty("domain_name", domainUid);
      p.setProperty("domain_uid", domainUid);
      p.setProperty("cluster_name", "cluster-1");
      p.setProperty("admin_server_name", "admin-server");
      p.setProperty("managed_server_port", "8001");
      p.setProperty("admin_server_port", "7001");
      p.setProperty("admin_username", ADMIN_USERNAME_DEFAULT);
      p.setProperty("admin_password", ADMIN_PASSWORD_DEFAULT);
      p.setProperty("admin_t3_public_address", K8S_NODEPORT_HOST);
      p.setProperty("admin_t3_channel_port", Integer.toString(getNextFreePort()));
      p.setProperty("number_of_ms", "2");
      p.setProperty("managed_server_name_base", "managed-");
      p.setProperty("domain_logs", "/shared/" + domainNamespace + "/logs/" + domainUid);
      p.setProperty("production_mode_enabled", "true");

      try (FileOutputStream fos = new FileOutputStream(props)) {
        p.store(fos, "WLST domain creation properties");
      }

      return props;

    } catch (Exception e) {
      throw new RuntimeException("Failed to create domain properties file for " + domainUid, e);
    }
  }


  private DomainResource buildDomainResource(String domainUid, String pvName, String pvcName,
                                             String logType, boolean exporterEnabled) {
    V1SecurityContext roContext = new V1SecurityContext().readOnlyRootFilesystem(true);
    FluentdSpecification fluentdSpec = null;
    MonitoringExporterSpecification monitoringExporterSpec = null;

    V1Volume tmpfsVol = new V1Volume()
        .name("memory-tmp")
        .emptyDir(new V1EmptyDirVolumeSource().medium("Memory"));
    V1VolumeMount tmpfsMount = new V1VolumeMount()
        .mountPath("/memory-tmp")
        .name("memory-tmp");

    List<V1Container> sidecars = new ArrayList<>();
    if ("fluentd".equals(logType)) {
      logger.info("Choosen FLUENTD_IMAGE {0}", FLUENTD_IMAGE);
      String imagePullPolicy = "IfNotPresent";
      FluentdSpecification fluentdSpecification = new FluentdSpecification();
      fluentdSpecification.setImage(FLUENTD_IMAGE);
      fluentdSpecification.setWatchIntrospectorLogs(true);
      fluentdSpecification.setImagePullPolicy(imagePullPolicy);
      fluentdSpecification.setElasticSearchCredentials("weblogic-credentials" + domainUid);
      V1VolumeMount fluentdLogMount = new V1VolumeMount()
          .mountPath("/memory-tmp/logs") // or wherever Fluentd writes logs
          .name("memory-tmp");
      fluentdSpecification.setVolumeMounts(List.of(tmpfsMount, fluentdLogMount));

      assertDoesNotThrow(() -> {
        Path filePath = Path.of(MODEL_DIR + "/" + FLUENTD_CONFIGMAP_YAML);
        fluentdSpecification.setFluentdConfiguration(Files.readString(filePath));
      });
      fluentdSpec = fluentdSpecification;

    } else if ("fluentbit".equals(logType)) {
      sidecars.add(new V1Container()
          .name("fluentbit")
          .image("fluent/fluent-bit:latest")
          .securityContext(roContext)
          .volumeMounts(List.of(tmpfsMount)));
    }
    if (exporterEnabled) {
      String monexpConfigFile = RESOURCE_DIR + "/exporter/rest_webapp.yaml";
      logger.info("YAML config file path: {}", monexpConfigFile);

      String contents;
      try {
        contents = Files.readString(Paths.get(monexpConfigFile));
        monitoringExporterSpec = new MonitoringExporterSpecification()
            .image(exporterImage)
            .imagePullPolicy(IMAGE_PULL_POLICY)
            .configuration(contents);
      } catch (IOException e) {
        logger.severe("Failed to read monitoring exporter config file: {0}", e.getMessage());
        throw new RuntimeException("Unable to read monitoring exporter config", e);

      }
    }
    DomainSpec spec = new DomainSpec()
        .domainUid(domainUid)
        .domainHome("/shared/" + domainNamespace + "/domains/" + domainUid)
        .domainHomeSourceType("PersistentVolume")
        .image(WEBLOGIC_IMAGE_TO_USE_IN_SPEC)
        .imagePullPolicy(IMAGE_PULL_POLICY)
        .replicas(2)
        .imagePullSecrets(List.of(new V1LocalObjectReference().name(BASE_IMAGES_REPO_SECRET_NAME)))
        .webLogicCredentialsSecret(new V1LocalObjectReference().name(wlSecretName + domainUid))
        .includeServerOutInPodLog(true)
        .logHomeEnabled(true)
        .logHome("/shared/" + domainNamespace + "/logs/" + domainUid)
        .dataHome("")
        .serverStartPolicy("IfNeeded")
        .serverPod(new ServerPod()
            .addEnvItem(new V1EnvVar()
                .name("JAVA_OPTIONS")
                .value("-Dweblogic.security.SSL.ignoreHostnameVerification=true"))
            .addEnvItem(new io.kubernetes.client.openapi.models.V1EnvVar()
                .name("JAVA_OPTIONS")
                .value("-Dweblogic.StdoutDebugEnabled=false"))
            .addEnvItem(new io.kubernetes.client.openapi.models.V1EnvVar()
                .name("USER_MEM_ARGS")
                .value("-Djava.security.egd=file:/dev/./urandom "))
            .containerSecurityContext(roContext)
            .addVolumesItem(new V1Volume().name(pvName)
                .persistentVolumeClaim(new V1PersistentVolumeClaimVolumeSource().claimName(pvcName)))
            .addVolumeMountsItem(new V1VolumeMount().mountPath("/shared").name(pvName))
            .addVolumesItem(tmpfsVol)
            .addVolumeMountsItem(tmpfsMount)
            .addVolumeMountsItem(new V1VolumeMount()
                .name("memory-tmp")
                .mountPath("/memory-tmp/logs")))
        .adminServer(createAdminServer())
        .configuration(new Configuration());
    if (fluentdSpec != null) {
      spec.withFluentdConfiguration(fluentdSpec);
    }
    if (monitoringExporterSpec != null) {
      spec.monitoringExporter(monitoringExporterSpec);
    }
    return new DomainResource()
        .apiVersion(DOMAIN_API_VERSION)
        .kind("Domain")
        .metadata(new V1ObjectMeta().name(domainUid).namespace(domainNamespace))
        .spec(spec);

  }

  public static void verifyAllPodsTmpfsAndReadOnlyFS(String domainNamespace, String domainUid) throws ApiException {
    List<String> failures = new ArrayList<>();

    V1PodList podList = listPods(domainNamespace, String.format("weblogic.domainUID in (%s)", domainUid));
    List<V1Pod> pods = podList.getItems();

    for (V1Pod pod : pods) {
      String podName = pod.getMetadata().getName();
      logger.info("Checking pod: {0}", podName);

      // Check init containers
      List<V1Container> initContainers = pod.getSpec().getInitContainers();
      if (initContainers != null) {
        for (V1Container initContainer : initContainers) {
          logger.info("Checking init container: {0}", initContainer.getName());
          validateContainerSpec(initContainer, podName, domainNamespace, true, failures);
        }
      }

      // Check normal containers
      List<V1Container> containers = pod.getSpec().getContainers();
      for (V1Container container : containers) {
        logger.info("Checking container: {0}", container.getName());
        validateContainerSpec(container, podName, domainNamespace, false, failures);
      }
    }

    if (!failures.isEmpty()) {
      failures.forEach(logger::severe);
      fail("Some containers failed /tmp mount or readOnlyRootFilesystem checks: " + failures.size() + " failures");
    } else {
      logger.info("All containers passed /tmp mount and readOnlyRootFilesystem checks");
    }
  }


  private static void validateContainerSpec(V1Container container, String podName, String namespace,
                                            boolean isInitContainer, List<String> failures) {
    String containerName = container.getName();

    // 1. Check securityContext.readOnlyRootFilesystem
    V1SecurityContext securityContext = container.getSecurityContext();
    if (securityContext == null || !Boolean.TRUE.equals(securityContext.getReadOnlyRootFilesystem())) {
      String msg = "FAIL: Container " + containerName
          + " in pod " + podName + " does not have readOnlyRootFilesystem=true";
      logger.severe(msg);
      failures.add(msg);
    } else {
      logger.info("PASS: Container " + containerName + " in pod " + podName + " has readOnlyRootFilesystem=true");
    }

    if (!isInitContainer) {
      // 2. For regular containers, exec to check /tmp mount
      try {

        ExecResult result = execCommand(namespace, podName, containerName, true, "df", "-h", "/tmp");
        String stdout = result.stdout();
        if (stdout == null || !stdout.contains("tmpfs")) {
          String msg = "FAIL: /tmp is not mounted as tmpfs in container " + containerName + " in pod " + podName;
          logger.severe(msg);
          failures.add(msg);
        } else {
          logger.info("PASS: /tmp is mounted as tmpfs in container " + containerName + " in pod " + podName);
        }
      } catch (Exception e) {
        String msg = "FAIL: Exec failed for container " + containerName + " in pod " + podName + ": " + e.getMessage();
        logger.severe(msg);
        failures.add(msg);
      }
    } else {
      // 3. For init container, check volumeMounts
      List<V1VolumeMount> volumeMounts = container.getVolumeMounts();
      boolean hasTmpMount = volumeMounts != null && volumeMounts.stream()
          .anyMatch(mount -> mount.getMountPath() != null && mount.getMountPath().startsWith("/tmp"));
      if (!hasTmpMount) {
        String msg = "FAIL: Init container " + containerName + " in pod " + podName + " does not have /tmp mounted";
        logger.severe(msg);
        failures.add(msg);
      } else {
        logger.info("PASS: Init container " + containerName + " in pod " + podName + " has /tmp mounted");
      }
    }
  }

  private void verifyTmpfsAndSecurityContext(String podName, V1Container container)
      throws IOException, ApiException, InterruptedException {
    String containerName = container.getName();
    logger.info("Verifying /tmp mount in pod: {0}, container: {1}", podName, containerName);

    ExecResult result = execCommand(domainNamespace, podName, containerName, true, "df", "-h", "/tmp");
    logger.info("Output for pod: {0}, container: {1} for df -h /tmp : {2}",
        podName, containerName, result.stdout());
    if (!result.stdout().contains("tmpfs")) {
      Path logDir = Paths.get(RESULTS_TEMPFILE, podName);
      Files.createDirectories(logDir);
      Path logFile = logDir.resolve(containerName + "-tmp-check.log");
      Files.writeString(logFile, result.stdout());
      logger.severe("/tmp not mounted as tmpfs in container {0}. Log saved to: {1}", containerName, logFile);
    }
    assertTrue(result.stdout().contains("tmpfs"),
        "/tmp is not tmpfs in container " + containerName + " of pod " + podName);

    V1SecurityContext context = container.getSecurityContext();
    assertTrue(context != null && Boolean.TRUE.equals(context.getReadOnlyRootFilesystem()),
        "readOnlyRootFilesystem not set to true for container " + containerName + " in pod " + podName);
  }

  private void createDomainOnPVUsingWlst(Path wlstScriptFile, Path domainPropertiesFile,
                                         String pvName, String pvcName,
                                         String namespace, String domainUid) throws IOException, ApiException {
    List<Path> files = List.of(wlstScriptFile, domainPropertiesFile);
    String cmName = "create-domain-scripts-cm";
    createConfigMapForDomainCreation(cmName, files, namespace, domainUid, this.getClass().getSimpleName());
    V1Container jobContainer = new V1Container()
        .addCommandItem("/bin/sh")
        .addArgsItem("/u01/oracle/oracle_common/common/bin/wlst.sh")
        .addArgsItem("/u01/weblogic/" + wlstScriptFile.getFileName())
        .addArgsItem("-skipWLSModuleScanning")
        .addArgsItem("-loadProperties")
        .addArgsItem("/u01/weblogic/" + domainPropertiesFile.getFileName());
    Map<String, String> annotations = Map.of("sidecar.istio.io/inject", "false");
    createDomainJob(WEBLOGIC_IMAGE_TO_USE_IN_SPEC, pvName, pvcName, cmName, namespace, jobContainer, annotations);
  }

  private static List<String> listPodNames(String ns, String domainUid) {
    List<String> podNames = new ArrayList<>();
    try {
      // Add admin server pod
      podNames.add(getPodName(ns, domainUid + "-admin-server"));
      // Add managed server pods: assuming 2 replicas
      for (int i = 1; i <= 2; i++) {
        podNames.add(getPodName(ns, domainUid + "-managed-" + i));
      }
    } catch (Exception e) {
      throw new RuntimeException("Failed to get pod names for domain: " + domainUid, e);
    }
    return podNames;
  }

  private void createDomainAndTrackForCleanup(DomainResource domain, String namespace) {
    createDomainAndVerify(domain, namespace);
    domainsToClean.add(domain.getMetadata().getName());
  }
}
