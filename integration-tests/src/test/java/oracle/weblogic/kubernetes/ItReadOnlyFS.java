// Copyright (c) 2022, 2024, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.weblogic.kubernetes;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import io.kubernetes.client.openapi.ApiException;
import io.kubernetes.client.openapi.models.V1Container;
import io.kubernetes.client.openapi.models.V1EmptyDirVolumeSource;
import io.kubernetes.client.openapi.models.V1LocalObjectReference;
import io.kubernetes.client.openapi.models.V1ObjectMeta;
import io.kubernetes.client.openapi.models.V1PersistentVolumeClaimVolumeSource;
import io.kubernetes.client.openapi.models.V1Pod;
import io.kubernetes.client.openapi.models.V1SecurityContext;
import io.kubernetes.client.openapi.models.V1Volume;
import io.kubernetes.client.openapi.models.V1VolumeMount;
import oracle.weblogic.domain.Configuration;
import oracle.weblogic.domain.DomainResource;
import oracle.weblogic.domain.DomainSpec;
import oracle.weblogic.domain.ServerPod;
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
import static oracle.weblogic.kubernetes.TestConstants.IMAGE_PULL_POLICY;
import static oracle.weblogic.kubernetes.TestConstants.K8S_NODEPORT_HOST;
import static oracle.weblogic.kubernetes.TestConstants.RESULTS_ROOT;
import static oracle.weblogic.kubernetes.TestConstants.RESULTS_TEMPFILE;
import static oracle.weblogic.kubernetes.TestConstants.TEST_IMAGES_REPO_SECRET_NAME;
import static oracle.weblogic.kubernetes.TestConstants.WEBLOGIC_IMAGE_TO_USE_IN_SPEC;
import static oracle.weblogic.kubernetes.actions.ActionConstants.RESOURCE_DIR;
import static oracle.weblogic.kubernetes.actions.TestActions.deleteDomainCustomResource;
import static oracle.weblogic.kubernetes.actions.TestActions.execCommand;
import static oracle.weblogic.kubernetes.actions.TestActions.getPod;
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
import static oracle.weblogic.kubernetes.utils.OperatorUtils.installAndVerifyOperator;
import static oracle.weblogic.kubernetes.utils.PersistentVolumeUtils.createPV;
import static oracle.weblogic.kubernetes.utils.PersistentVolumeUtils.createPVC;
import static oracle.weblogic.kubernetes.utils.PodUtils.getPodName;
import static oracle.weblogic.kubernetes.utils.PodUtils.setPodAntiAffinity;
import static oracle.weblogic.kubernetes.utils.SecretUtils.createSecretWithUsernamePassword;
import static oracle.weblogic.kubernetes.utils.ThreadSafeLogger.getLogger;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("Verify /tmp is mounted as tmpfs across containers in domain-on-pv")
@IntegrationTest
@Tag("olcne-mrg")
@Tag("kind-parallel")
@Tag("oke-arm")
@Tag("oke-parallel")
class ItReadOnlyFS {

  private static String opNamespace;
  private static String domainNamespace;
  private final String wlSecretName = "weblogic-credentials";
  private static LoggingFacade logger;
  private String lastTestedDomain;
  final String managedServerNameBase = "managed-";
  private final String domainUid = null;
  private final String clusterName = "cluster-1";
  private final String adminServerName = "admin-server";
  private static String exporterImage = null;
  final int replicaCount = 2;



  @BeforeAll
  public static void initAll(@Namespaces(2) List<String> namespaces) {
    logger = getLogger();
    opNamespace = namespaces.get(0);
    domainNamespace = namespaces.get(1);
    installAndVerifyOperator(opNamespace, domainNamespace);
    String monitoringExporterDir = Paths.get(RESULTS_ROOT,
        "ItReadOnly", "monitoringexp").toString();
    String monitoringExporterSrcDir = Paths.get(monitoringExporterDir, "srcdir").toString();
    exporterImage = assertDoesNotThrow(() ->
            buildMonitoringExporterCreateImageAndPushToRepo(monitoringExporterSrcDir, "exporter",
                domainNamespace, TEST_IMAGES_REPO_SECRET_NAME, getImageBuilderExtraArgs()),
        "Failed to create image for exporter");
  }

  @AfterEach
  public void cleanup() {
    if (lastTestedDomain != null) {
      logger.info("Cleaning up domain: {0}", lastTestedDomain);
      deleteDomainCustomResource(lastTestedDomain, domainNamespace);
    }
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

  private void runDomainWithOptions(String logType, boolean exporterEnabled)
      throws IOException, ApiException, InterruptedException {
    logger.info("Running domain test with logType: {0}, exporterEnabled: {1}", logType, exporterEnabled);
    String testSuffix = logType + (exporterEnabled ? "-exp" : "-noexp");
    String domainUid = "readonlyfs-dpv-" + testSuffix;
    lastTestedDomain = domainUid;
    String adminServerPodName = domainUid + "-" + adminServerName;
    String managedServerPodNamePrefix = domainUid + "-" + managedServerNameBase;

    String pvName = getUniqueName(domainUid + "-pv");
    String pvcName = getUniqueName(domainUid + "-pvc");

    createBaseRepoSecret(domainNamespace);
    createSecretWithUsernamePassword(wlSecretName, domainNamespace, ADMIN_USERNAME_DEFAULT, ADMIN_PASSWORD_DEFAULT);
    createPV(pvName, domainUid, this.getClass().getSimpleName());
    createPVC(pvName, pvcName, domainUid, domainNamespace);

    File domainPropsFile = createDomainProperties(domainUid);
    Path wlstScript = Paths.get(RESOURCE_DIR, "python-scripts", "sit-config-create-domain.py");
    createDomainOnPVUsingWlst(wlstScript, domainPropsFile.toPath(), pvName, pvcName, domainNamespace, domainUid);

    DomainResource domain = buildDomainResource(domainUid, pvName, pvcName, logType, exporterEnabled);
    setPodAntiAffinity(domain);
    createDomainAndVerify(domain, domainNamespace);
    // verify the admin server service created
    checkPodReadyAndServiceExists(adminServerPodName,domainUid,domainNamespace);

    // verify managed server services created
    for (int i = 1; i <= replicaCount; i++) {
      logger.info("Checking managed service {0} is created in namespace {1}",
          managedServerPodNamePrefix + i, domainNamespace);
      checkPodReadyAndServiceExists(managedServerPodNamePrefix + i, domainUid, domainNamespace);

    }

    logger.info("Domain {0} deployed and verified successfully", domainUid);
    verifyAllPodsTmpfs(domainUid);
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

    // Fix: use /memory-tmp instead of /tmp to avoid mount path conflict
    V1Volume tmpfsVol = new V1Volume()
        .name("memory-tmp")
        .emptyDir(new V1EmptyDirVolumeSource().medium("Memory"));
    V1VolumeMount tmpfsMount = new V1VolumeMount()
        .mountPath("/memory-tmp")
        .name("memory-tmp");

    List<V1Container> sidecars = new ArrayList<>();
    if ("fluentd".equals(logType)) {
      sidecars.add(new V1Container()
          .name("fluentd")
          .image("fluent/fluentd:latest")
          .securityContext(roContext)
          .volumeMounts(List.of(tmpfsMount)));
    } else if ("fluentbit".equals(logType)) {
      sidecars.add(new V1Container()
          .name("fluentbit")
          .image("fluent/fluent-bit:latest")
          .securityContext(roContext)
          .volumeMounts(List.of(tmpfsMount)));
    }
    if (exporterEnabled) {
      sidecars.add(new V1Container()
          .name("monitoring-exporter")
          .image(exporterImage)
          .securityContext(roContext)
          .volumeMounts(List.of(tmpfsMount)));
    }

    return new DomainResource()
        .apiVersion(DOMAIN_API_VERSION)
        .kind("Domain")
        .metadata(new V1ObjectMeta().name(domainUid).namespace(domainNamespace))
        .spec(new DomainSpec()
            .domainUid(domainUid)
            .domainHome("/shared/" + domainNamespace + "/domains/" + domainUid)
            .domainHomeSourceType("PersistentVolume")
            .image(WEBLOGIC_IMAGE_TO_USE_IN_SPEC)
            .imagePullPolicy(IMAGE_PULL_POLICY)
            .replicas(2)
            .imagePullSecrets(List.of(new V1LocalObjectReference().name(BASE_IMAGES_REPO_SECRET_NAME)))
            .webLogicCredentialsSecret(new V1LocalObjectReference().name(wlSecretName))
            .includeServerOutInPodLog(true)
            .logHomeEnabled(true)
            .logHome("/shared/" + domainNamespace + "/logs/" + domainUid)
            .dataHome("")
            .serverStartPolicy("IfNeeded")
            .serverPod(new ServerPod()
                .containerSecurityContext(roContext)
                .addVolumesItem(new V1Volume().name(pvName)
                    .persistentVolumeClaim(new V1PersistentVolumeClaimVolumeSource().claimName(pvcName)))
                .addVolumeMountsItem(new V1VolumeMount().mountPath("/shared").name(pvName))
                .addVolumesItem(tmpfsVol)
                .addVolumeMountsItem(tmpfsMount)
                .containers(sidecars))
            .adminServer(createAdminServer())
            .configuration(new Configuration()));
  }

  private void verifyAllPodsTmpfs(String domainUid) throws IOException, InterruptedException, ApiException {
    List<String> podNames = listPodNames(domainNamespace, domainUid);
    String labelSelector = String.format("weblogic.domainUID in (%s)", domainUid);

    for (String podName : podNames) {
      List<String> containerNames = listContainerNames(podName, domainNamespace, domainUid);
      for (String container : containerNames) {
        logger.info("Verifying /tmp mount in pod: {0}, container: {1}", podName, container);
        ExecResult result = execCommand(domainNamespace, podName, container, true, "df", "-h", "/memory-tmp");

        if (!result.stdout().contains("tmpfs")) {
          Path logDir = Paths.get(RESULTS_TEMPFILE, domainUid, podName);
          Files.createDirectories(logDir);
          Path logFile = logDir.resolve(container + "-tmp-check.log");
          Files.writeString(logFile, result.stdout());
          logger.severe("/tmp not mounted as tmpfs in container {0}. Log saved to: {1}", container, logFile);
        }
        assertTrue(result.stdout().contains("tmpfs"),
            "/tmp is not tmpfs in container " + container + " of pod " + podName + ": " + result.stdout());
      }
    }
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

  private List<String> listPodNames(String ns, String domainUid) {
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

  private List<String> listContainerNames(String podName, String namespace, String domainUid) {
    String labelSelector = String.format("weblogic.domainUID in (%s)", domainUid);
    try {
      V1Pod pod = getPod(namespace, labelSelector, podName);
      return pod.getSpec().getContainers()
          .stream()
          .map(V1Container::getName)
          .toList();
    } catch (ApiException e) {
      throw new RuntimeException("Failed to get containers from pod: " + podName, e);
    }
  }

}
