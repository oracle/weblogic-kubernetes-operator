// Copyright (c) 2020, Oracle Corporation and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.weblogic.kubernetes;

import java.io.FileOutputStream;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;

import io.kubernetes.client.custom.Quantity;
import io.kubernetes.client.openapi.ApiException;
import io.kubernetes.client.openapi.models.V1ConfigMapVolumeSource;
import io.kubernetes.client.openapi.models.V1Container;
import io.kubernetes.client.openapi.models.V1ContainerPort;
import io.kubernetes.client.openapi.models.V1EnvVar;
import io.kubernetes.client.openapi.models.V1HostPathVolumeSource;
import io.kubernetes.client.openapi.models.V1Job;
import io.kubernetes.client.openapi.models.V1JobSpec;
import io.kubernetes.client.openapi.models.V1LocalObjectReference;
import io.kubernetes.client.openapi.models.V1ObjectMeta;
import io.kubernetes.client.openapi.models.V1ObjectMetaBuilder;
import io.kubernetes.client.openapi.models.V1PersistentVolume;
import io.kubernetes.client.openapi.models.V1PersistentVolumeClaim;
import io.kubernetes.client.openapi.models.V1PersistentVolumeClaimSpec;
import io.kubernetes.client.openapi.models.V1PersistentVolumeClaimVolumeSource;
import io.kubernetes.client.openapi.models.V1PersistentVolumeSpec;
import io.kubernetes.client.openapi.models.V1PodSpec;
import io.kubernetes.client.openapi.models.V1PodTemplateSpec;
import io.kubernetes.client.openapi.models.V1ResourceRequirements;
import io.kubernetes.client.openapi.models.V1SecretReference;
import io.kubernetes.client.openapi.models.V1SecurityContext;
import io.kubernetes.client.openapi.models.V1Volume;
import io.kubernetes.client.openapi.models.V1VolumeMount;
import oracle.weblogic.domain.AdminServer;
import oracle.weblogic.domain.AdminService;
import oracle.weblogic.domain.Channel;
import oracle.weblogic.domain.Cluster;
import oracle.weblogic.domain.Domain;
import oracle.weblogic.domain.DomainSpec;
import oracle.weblogic.domain.ServerPod;
import oracle.weblogic.kubernetes.actions.impl.primitive.HelmParams;
import oracle.weblogic.kubernetes.annotations.IntegrationTest;
import oracle.weblogic.kubernetes.annotations.Namespaces;
import oracle.weblogic.kubernetes.logging.LoggingFacade;
import org.awaitility.core.ConditionFactory;
import org.joda.time.DateTime;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static java.nio.file.Files.copy;
import static java.nio.file.Files.createDirectories;
import static java.nio.file.Paths.get;
import static java.util.concurrent.TimeUnit.MINUTES;
import static java.util.concurrent.TimeUnit.SECONDS;
import static oracle.weblogic.kubernetes.TestConstants.ADMIN_PASSWORD_DEFAULT;
import static oracle.weblogic.kubernetes.TestConstants.ADMIN_SERVER_NAME_BASE;
import static oracle.weblogic.kubernetes.TestConstants.ADMIN_USERNAME_DEFAULT;
import static oracle.weblogic.kubernetes.TestConstants.DOMAIN_API_VERSION;
import static oracle.weblogic.kubernetes.TestConstants.DOMAIN_VERSION;
import static oracle.weblogic.kubernetes.TestConstants.K8S_NODEPORT_HOST;
import static oracle.weblogic.kubernetes.TestConstants.KIND_REPO;
import static oracle.weblogic.kubernetes.TestConstants.MANAGED_SERVER_NAME_BASE;
import static oracle.weblogic.kubernetes.TestConstants.OCR_REGISTRY;
import static oracle.weblogic.kubernetes.TestConstants.OCR_SECRET_NAME;
import static oracle.weblogic.kubernetes.TestConstants.PV_ROOT;
import static oracle.weblogic.kubernetes.TestConstants.RESULTS_ROOT;
import static oracle.weblogic.kubernetes.actions.ActionConstants.RESOURCE_DIR;
import static oracle.weblogic.kubernetes.actions.ActionConstants.WLS_BASE_IMAGE_NAME;
import static oracle.weblogic.kubernetes.actions.ActionConstants.WLS_BASE_IMAGE_TAG;
import static oracle.weblogic.kubernetes.actions.TestActions.deleteConfigMap;
import static oracle.weblogic.kubernetes.actions.TestActions.deleteDomainCustomResource;
import static oracle.weblogic.kubernetes.actions.TestActions.deleteJob;
import static oracle.weblogic.kubernetes.actions.TestActions.deletePersistentVolume;
import static oracle.weblogic.kubernetes.actions.TestActions.deletePersistentVolumeClaim;
import static oracle.weblogic.kubernetes.actions.TestActions.deletePod;
import static oracle.weblogic.kubernetes.actions.TestActions.deleteSecret;
import static oracle.weblogic.kubernetes.actions.TestActions.getServiceNodePort;
import static oracle.weblogic.kubernetes.actions.TestActions.listJobs;
import static oracle.weblogic.kubernetes.actions.TestActions.listPods;
import static oracle.weblogic.kubernetes.actions.TestActions.shutdownDomain;
import static oracle.weblogic.kubernetes.actions.TestActions.startDomain;
import static oracle.weblogic.kubernetes.actions.TestActions.uninstallOperator;
import static oracle.weblogic.kubernetes.assertions.TestAssertions.adminNodePortAccessible;
import static oracle.weblogic.kubernetes.assertions.TestAssertions.domainDoesNotExist;
import static oracle.weblogic.kubernetes.assertions.TestAssertions.podStateNotChanged;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.checkPodDoesNotExist;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.checkPodReady;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.checkPodRestarted;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.checkServiceExists;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.createConfigMapFromFiles;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.createDomainAndVerify;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.createJobAndWaitUntilComplete;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.createOCRRepoSecret;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.createPVPVCAndVerify;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.createSecretWithUsernamePassword;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.getPodCreationTime;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.installAndVerifyOperator;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.scaleAndVerifyCluster;
import static oracle.weblogic.kubernetes.utils.TestUtils.getNextFreePort;
import static oracle.weblogic.kubernetes.utils.ThreadSafeLogger.getLogger;
import static org.apache.commons.io.FileUtils.deleteDirectory;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.with;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Test operator manages multiple domains.
 */
@DisplayName("Verify operator manages multiple domains")
@IntegrationTest
public class ItOperatorTwoDomains {

  private static final int numberOfDomains = 2;
  private static final int numberOfOperators = 2;
  private static final String defaultNamespace = "default";
  private static final String wlSecretName = "weblogic-credentials";

  private static boolean isUseSecret = true;
  private static String image = WLS_BASE_IMAGE_NAME + ":" + WLS_BASE_IMAGE_TAG;
  private static String domain1Uid = null;
  private static String domain2Uid = null;
  private static String domain1Namespace = null;
  private static String domain2Namespace = null;
  private static List<String> opNamespaces = new ArrayList<>();
  private static List<String> domainNamespaces = new ArrayList<>();
  private static List<String> domainUids = new ArrayList<>();
  private static HelmParams operatorHelmParams = null;

  // domain constants
  private final String clusterName = "cluster-1";
  private final int replicaCount = 2;

  private int t3ChannelPort = 0;
  private int replicasAfterScale;
  private List<String> domainAdminServerPodNames = new ArrayList<>();
  private List<DateTime> domainAdminPodOriginalTimestamps = new ArrayList<>();
  private List<DateTime> domain1ManagedServerPodOriginalTimestampList = new ArrayList<>();
  private List<DateTime> domain2ManagedServerPodOriginalTimestampList = new ArrayList<>();
  private static LoggingFacade logger = null;

  /**
   * Get namespaces, install operator and initiate domain UID list.
   *
   * @param namespaces injected by JUnit
   */
  @BeforeAll
  public static void initAll(@Namespaces(4) List<String> namespaces) {
    logger = getLogger();
    // get unique operator namespaces
    logger.info("Get unique namespaces for operator1 and operator2");
    for (int i = 0; i < numberOfOperators; i++) {
      assertNotNull(namespaces.get(i), "Namespace list is null");
      opNamespaces.add(namespaces.get(i));
    }

    // get unique domain namespaces
    logger.info("Get unique namespaces for WebLogic domain1 and domain2");
    for (int i = numberOfOperators; i < numberOfOperators + numberOfDomains; i++) {
      assertNotNull(namespaces.get(i), "Namespace list is null");
      domainNamespaces.add(namespaces.get(i));
    }

    // install and verify operator
    operatorHelmParams = installAndVerifyOperator(opNamespaces.get(0), domainNamespaces.get(0), defaultNamespace);
    installAndVerifyOperator(opNamespaces.get(1), domainNamespaces.get(1));

    // initiate domainUid list for two domains
    for (int i = 1; i <= numberOfDomains; i++) {
      domainUids.add("domain" + i);
    }

    domain1Uid = domainUids.get(0);
    domain2Uid = domainUids.get(1);
    domain1Namespace = domainNamespaces.get(0);
    domain2Namespace = domainNamespaces.get(1);

    //determine if the tests are running in Kind cluster. if true use images from Kind registry
    if (KIND_REPO != null) {
      String kindRepoImage = KIND_REPO + image.substring(OCR_REGISTRY.length() + 1);
      logger.info("Using image {0}", kindRepoImage);
      image = kindRepoImage;
      isUseSecret = false;
    }
  }

  /**
   * Test covers the following use cases.
   * create two domains on PV using WLST
   * domain1 managed by operator1
   * domain2 managed by operator2
   * scale cluster in domain1 from 2 to 3 servers and verify no impact on domain2, domain2 continues to run
   * restart domain1 and verify no impact on domain2, domain2 continues to run
   * shutdown the domains using serverStartPolicy
   */
  @Test
  @DisplayName("Create domain on PV using WLST script")
  public void testTwoDomainsManagedByTwoOperators() {

    // create two domains on PV using WLST
    createTwoDomainsOnPVUsingWlstAndVerify();

    // get the domain1 and domain2 pods original creation timestamps
    getBothDomainsPodsOriginalCreationTimestamp(domainUids, domainNamespaces);

    // scale cluster in domain1 from 2 to 3 servers and verify no impact on domain2
    replicasAfterScale = 3;
    scaleDomain1AndVerifyNoImpactOnDomain2();

    // restart domain1 and verify no impact on domain2
    restartDomain1AndVerifyNoImpactOnDomain2(replicasAfterScale, domain1Namespace, domain2Namespace);

    // shutdown both domains and verify the pods were shutdown
    shutdownBothDomainsAndVerify(domainNamespaces);
  }

  /**
   * Create one operator if it is not running.
   * Create domain domain1 and domain2 dynamic cluster in default namespace, managed by operator1.
   * Both domains share one PV.
   * Verify scaling for domain2 cluster from 2 to 3 servers and back to 2, plus verify no impact on domain1.
   * Shut down domain1 and back up, plus verify no impact on domain2.
   * shutdown both domains
   */
  @Test
  public void testTwoDomainsManagedByOneOperatorSharingPV() {
    // create two domains sharing one PV in default namespace
    createTwoDomainsSharingPVUsingWlstAndVerify();

    // get the domain1 and domain2 pods original creation timestamps
    List<String> domainNamespaces = new ArrayList<>();
    domainNamespaces.add(defaultNamespace);
    domainNamespaces.add(defaultNamespace);
    getBothDomainsPodsOriginalCreationTimestamp(domainUids, domainNamespaces);

    // scale domain2 to 3 servers and back to 2 and verify no impact on domain1
    scaleDomain2AndVerifyNoImpactOnDomain1();

    // restart domain1 and verify no impact on domain2
    restartDomain1AndVerifyNoImpactOnDomain2(replicaCount, defaultNamespace, defaultNamespace);

    // shutdown both domains
    shutdownBothDomainsAndVerify(domainNamespaces);
  }

  /**
   * Cleanup all the remaining artifacts in default namespace created by the test.
   */
  @AfterAll
  public void tearDownAll() {

    // uninstall operator which manages default namespace
    logger.info("uninstalling operator which manages default namespace");
    if (operatorHelmParams != null) {
      assertThat(uninstallOperator(operatorHelmParams))
          .as("Test uninstallOperator returns true")
          .withFailMessage("uninstallOperator() did not return true")
          .isTrue();
    }

    ConditionFactory withStandardRetryPolicy =
        with().pollDelay(2, SECONDS)
            .and().with().pollInterval(10, SECONDS)
            .atMost(5, MINUTES).await();

    for (int i = 1; i <= numberOfDomains; i++) {
      String domainUid = domainUids.get(i - 1);
      // delete domain
      logger.info("deleting domain custom resource {0}", domainUid);
      assertTrue(deleteDomainCustomResource(domainUid, defaultNamespace));

      // wait until domain was deleted
      withStandardRetryPolicy
          .conditionEvaluationListener(
              condition -> logger.info("Waiting for domain {0} to be created in namespace {1} "
                      + "(elapsed time {2}ms, remaining time {3}ms)",
                  domainUid,
                  defaultNamespace,
                  condition.getElapsedTimeInMS(),
                  condition.getRemainingTimeInMS()))
          .until(domainDoesNotExist(domainUid, DOMAIN_VERSION, defaultNamespace));

      // delete configMap in default namespace
      logger.info("deleting configMap {0}", "create-domain" + i + "-scripts-cm");
      assertTrue(deleteConfigMap("create-domain" + i + "-scripts-cm", defaultNamespace));
    }

    // delete configMap weblogic-scripts-cm in default namespace
    logger.info("deleting configMap weblogic-scripts-cm");
    assertTrue(deleteConfigMap("weblogic-scripts-cm", defaultNamespace));

    // delete secret in default namespace
    logger.info("deleting secret {0}", OCR_SECRET_NAME);
    assertTrue(deleteSecret(OCR_SECRET_NAME, defaultNamespace));
    logger.info("deleting secret {0}", wlSecretName);
    assertTrue(deleteSecret(wlSecretName, defaultNamespace));

    // Delete jobs
    try {
      for (var item :listJobs(defaultNamespace).getItems()) {
        deleteJob(item.getMetadata().getName(), defaultNamespace);
      }

      for (var item : listPods(defaultNamespace, null).getItems()) {
        deletePod(item.getMetadata().getName(), defaultNamespace);
      }
    } catch (ApiException ex) {
      logger.warning(ex.getMessage());
      logger.warning("Failed to delete jobs");
    }

    // delete pv and pvc in default namespace
    logger.info("deleting pvc default-sharing-pvc");
    logger.info("deleting pv default-sharing-pv");
    assertTrue(deletePersistentVolumeClaim("default-sharing-pvc", defaultNamespace));
    assertTrue(deletePersistentVolume("default-sharing-pv"));
  }

  /**
   * Create two domains on PV using WLST.
   */
  private void createTwoDomainsOnPVUsingWlstAndVerify() {

    for (int i = 0; i < numberOfDomains; i++) {

      if (isUseSecret) {
        // create pull secrets for WebLogic image
        createOCRRepoSecret(domainNamespaces.get(i));
      }

      t3ChannelPort = getNextFreePort(32001, 32700);
      logger.info("t3ChannelPort for domain {0} is {1}", domainUids.get(i), t3ChannelPort);

      String domainUid = domainUids.get(i);
      String domainNamespace = domainNamespaces.get(i);
      String pvName = domainUid + "-pv-" + domainNamespace;
      String pvcName = domainUid + "-pvc";

      // create WebLogic credentials secret
      createSecretWithUsernamePassword(wlSecretName, domainNamespace, ADMIN_USERNAME_DEFAULT, ADMIN_PASSWORD_DEFAULT);

      // create persistent volume and persistent volume claims
      Path pvHostPath = assertDoesNotThrow(
          () -> createDirectories(get(PV_ROOT, this.getClass().getSimpleName(), domainUid + "-persistentVolume")),
              "createDirectories failed with IOException");

      logger.info("Creating PV directory {0}", pvHostPath);
      assertDoesNotThrow(() -> deleteDirectory(pvHostPath.toFile()), "deleteDirectory failed with IOException");
      assertDoesNotThrow(() -> createDirectories(pvHostPath), "createDirectories failed with IOException");

      V1PersistentVolume v1pv = new V1PersistentVolume()
          .spec(new V1PersistentVolumeSpec()
              .addAccessModesItem("ReadWriteMany")
              .storageClassName(domainUid + "-weblogic-domain-storage-class")
              .volumeMode("Filesystem")
              .putCapacityItem("storage", Quantity.fromString("5Gi"))
              .persistentVolumeReclaimPolicy("Recycle")
              .accessModes(Arrays.asList("ReadWriteMany"))
              .hostPath(new V1HostPathVolumeSource()
                  .path(pvHostPath.toString())))
          .metadata(new V1ObjectMetaBuilder()
              .withName(pvName)
              .build()
              .putLabelsItem("weblogic.domainUid", domainUid));

      V1PersistentVolumeClaim v1pvc = new V1PersistentVolumeClaim()
          .spec(new V1PersistentVolumeClaimSpec()
              .addAccessModesItem("ReadWriteMany")
              .storageClassName(domainUid + "-weblogic-domain-storage-class")
              .volumeName(pvName)
              .resources(new V1ResourceRequirements()
                  .putRequestsItem("storage", Quantity.fromString("5Gi"))))
          .metadata(new V1ObjectMetaBuilder()
              .withName(pvcName)
              .withNamespace(domainNamespace)
              .build()
              .putLabelsItem("weblogic.domainUid", domainUid));

      String labelSelector = String.format("weblogic.domainUid in (%s)", domainUid);
      createPVPVCAndVerify(v1pv, v1pvc, labelSelector, domainNamespace);

      // run create a domain on PV job using WLST
      runCreateDomainOnPVJobUsingWlst(pvName, pvcName, domainUid, domainNamespace,
          "create-domain-scripts-cm", "create-domain-onpv-job");

      // create the domain custom resource configuration object
      logger.info("Creating domain custom resource");
      Domain domain =
          createDomainCustomResource(domainUid, domainNamespace, wlSecretName, pvName, pvcName, t3ChannelPort);

      logger.info("Creating domain custom resource {0} in namespace {1}", domainUid, domainNamespace);
      createDomainAndVerify(domain, domainNamespace);

      String adminServerPodName = domainUid + "-" + ADMIN_SERVER_NAME_BASE;
      // check admin server pod is ready and service exists in domain namespace
      checkPodReadyAndServiceExists(adminServerPodName, domainUid, domainNamespace);

      // check for managed server pods are ready and services exist in domain namespace
      for (int j = 1; j <= replicaCount; j++) {
        String managedServerPodName = domainUid + "-" + MANAGED_SERVER_NAME_BASE + j;
        checkPodReadyAndServiceExists(managedServerPodName, domainUid, domainNamespace);
      }

      logger.info("Getting admin service node port");
      int serviceNodePort =
              getServiceNodePort(domainNamespace, adminServerPodName + "-external", "default");

      logger.info("Validating WebLogic admin server access by login to console");
      assertTrue(assertDoesNotThrow(
          () -> adminNodePortAccessible(serviceNodePort, ADMIN_USERNAME_DEFAULT, ADMIN_PASSWORD_DEFAULT),
          "Access to admin server node port failed"), "Console login validation failed");
    }
  }

  /**
   * Run a job to create a WebLogic domain on a persistent volume by doing the following.
   * Copies the WLST domain script to a temp location.
   * Creates a domain properties in the temp location.
   * Creates a configmap containing domain scripts and property files.
   * Runs a job to create domain on persistent volume.
   *
   * @param pvName persistence volume on which the WebLogic domain home will be hosted
   * @param pvcName persistence volume claim for the WebLogic domain
   * @param domainUid the Uid of the domain to create
   * @param domainNamespace the namespace in which the domain will be created
   * @param domainScriptConfigMapName the configMap name for domain script
   * @param createDomainInPVJobName the job name for creating domain in PV
   */
  private void runCreateDomainOnPVJobUsingWlst(String pvName,
                                               String pvcName,
                                               String domainUid,
                                               String domainNamespace,
                                               String domainScriptConfigMapName,
                                               String createDomainInPVJobName) {

    logger.info("Creating a staging location for domain creation scripts");
    Path pvTemp = get(RESULTS_ROOT, this.getClass().getSimpleName(), "domainCreateTempPV");
    assertDoesNotThrow(() -> deleteDirectory(pvTemp.toFile()),"deleteDirectory failed with IOException");
    assertDoesNotThrow(() -> createDirectories(pvTemp), "createDirectories failed with IOException");

    logger.info("Copying the domain creation WLST script to staging location");
    Path srcWlstScript = get(RESOURCE_DIR, "python-scripts", "wlst-create-domain-onpv.py");
    Path targetWlstScript = get(pvTemp.toString(), "create-domain.py");
    assertDoesNotThrow(() -> copy(srcWlstScript, targetWlstScript, StandardCopyOption.REPLACE_EXISTING),
        "copy failed with IOException");

    logger.info("Creating WebLogic domain properties file");
    Path domainPropertiesFile = get(pvTemp.toString(), "domain.properties");
    createDomainProperties(domainPropertiesFile, domainUid);

    logger.info("Adding files to a ConfigMap for domain creation job");
    List<Path> domainScriptFiles = new ArrayList<>();
    domainScriptFiles.add(targetWlstScript);
    domainScriptFiles.add(domainPropertiesFile);

    logger.info("Creating a ConfigMap to hold domain creation scripts");
    createConfigMapFromFiles(domainScriptConfigMapName, domainScriptFiles, domainNamespace);

    logger.info("Running a Kubernetes job to create the domain");
    V1Job jobBody = new V1Job()
        .metadata(
            new V1ObjectMeta()
                .name(createDomainInPVJobName)
                .namespace(domainNamespace))
        .spec(new V1JobSpec()
            .backoffLimit(0) // try only once
            .template(new V1PodTemplateSpec()
                .spec(new V1PodSpec()
                    .restartPolicy("Never")
                    .initContainers(Arrays.asList(new V1Container()
                        .name("fix-pvc-owner")
                        .image(image)
                        .addCommandItem("/bin/sh")
                        .addArgsItem("-c")
                        .addArgsItem("chown -R 1000:1000 /shared")
                        .volumeMounts(Arrays.asList(
                            new V1VolumeMount()
                                .name(pvName)
                                .mountPath("/shared")))
                        .securityContext(new V1SecurityContext()
                            .runAsGroup(0L)
                            .runAsUser(0L))))
                    .containers(Arrays.asList(new V1Container()
                        .name("create-weblogic-domain-onpv-container")
                        .image(image)
                        .ports(Arrays.asList(new V1ContainerPort()
                            .containerPort(7001)))
                        .volumeMounts(Arrays.asList(
                            new V1VolumeMount()
                                .name("create-weblogic-domain-job-cm-volume") // domain creation scripts volume
                                .mountPath("/u01/weblogic"), // availble under /u01/weblogic inside pod
                            new V1VolumeMount()
                                .name(pvName) // location to write domain
                                .mountPath("/shared"))) // mounted under /shared inside pod
                        .addCommandItem("/bin/sh") //call wlst.sh script with py and properties file
                        .addArgsItem("/u01/oracle/oracle_common/common/bin/wlst.sh")
                        .addArgsItem("/u01/weblogic/create-domain.py")
                        .addArgsItem("-skipWLSModuleScanning")
                        .addArgsItem("-loadProperties")
                        .addArgsItem("/u01/weblogic/domain.properties")))
                    .volumes(Arrays.asList(
                        new V1Volume()
                            .name(pvName)
                            .persistentVolumeClaim(
                                new V1PersistentVolumeClaimVolumeSource()
                                    .claimName(pvcName)),
                        new V1Volume()
                            .name("create-weblogic-domain-job-cm-volume")
                            .configMap(
                                new V1ConfigMapVolumeSource()
                                    .name(domainScriptConfigMapName))))  //ConfigMap containing domain scripts
                    .imagePullSecrets(isUseSecret ? Arrays.asList(
                        new V1LocalObjectReference()
                            .name(OCR_SECRET_NAME))
                        : null))));

    logger.info("Running a job {0} to create a domain on PV for domain {1} in namespace {2}",
        jobBody.getMetadata().getName(), domainUid, domainNamespace);
    createJobAndWaitUntilComplete(jobBody, domainNamespace);
  }

  /**
   * Create a properties file for WebLogic domain configuration.
   * @param wlstPropertiesFile path of the properties file
   * @param domainUid the WebLogic domain for which the properties file is created
   */
  private void createDomainProperties(Path wlstPropertiesFile,
                                      String domainUid) {
    // create a list of properties for the WebLogic domain configuration
    Properties p = new Properties();

    p.setProperty("domain_path", "/shared/domains");
    p.setProperty("domain_name", domainUid);
    p.setProperty("cluster_name", clusterName);
    p.setProperty("admin_server_name", ADMIN_SERVER_NAME_BASE);
    p.setProperty("managed_server_port", "8001");
    p.setProperty("admin_server_port", "7001");
    p.setProperty("admin_username", ADMIN_USERNAME_DEFAULT);
    p.setProperty("admin_password", ADMIN_PASSWORD_DEFAULT);
    p.setProperty("admin_t3_public_address", K8S_NODEPORT_HOST);
    p.setProperty("admin_t3_channel_port", Integer.toString(t3ChannelPort));
    p.setProperty("number_of_ms", "4");
    p.setProperty("managed_server_name_base", MANAGED_SERVER_NAME_BASE);
    p.setProperty("domain_logs", "/shared/logs");
    p.setProperty("production_mode_enabled", "true");

    FileOutputStream fileOutputStream =
        assertDoesNotThrow(() -> new FileOutputStream(wlstPropertiesFile.toFile()),
            "new FileOutputStream failed with FileNotFoundException");
    assertDoesNotThrow(() -> p.store(fileOutputStream, "WLST properties file"),
        "Writing the property list to the specified output stream failed with IOException");
  }

  /**
   * Scale domain1 and verify there is no impact on domain2.
   */
  private void scaleDomain1AndVerifyNoImpactOnDomain2() {

    // scale domain1
    logger.info("Scaling cluster {0} of domain {1} in namespace {2} to {3} servers.",
        clusterName, domain1Uid, domain1Namespace, replicasAfterScale);
    scaleAndVerifyCluster(clusterName, domain1Uid, domain1Namespace,
        domain1Uid + "-" + MANAGED_SERVER_NAME_BASE, replicaCount, replicasAfterScale,
        null, null);

    // add the third managed server pod original creation timestamp to the list
    domain1ManagedServerPodOriginalTimestampList.add(
        getPodCreationTime(domain1Namespace, domain1Uid + "-" + MANAGED_SERVER_NAME_BASE + replicasAfterScale));

    // verify scaling domain1 has no impact on domain2
    logger.info("Checking that domain2 was not changed after domain1 was scaled up");
    verifyDomain2NotChanged(domain2Namespace);
  }

  /**
   * Restart domain1 and verify there was no impact on domain2.
   *
   * @param numServersInDomain1 number of servers in domain1
   * @param domain1Namespace  namespace in which domain1 exists
   * @param domain2Namespace  namespace in which domain2 exists
   */
  private void restartDomain1AndVerifyNoImpactOnDomain2(int numServersInDomain1,
                                                        String domain1Namespace,
                                                        String domain2Namespace) {
    String domain1AdminServerPodName = domainAdminServerPodNames.get(0);

    // shutdown domain1
    logger.info("Shutting down domain1");
    assertTrue(shutdownDomain(domain1Uid, domain1Namespace),
        String.format("shutdown domain %s in namespace %s failed", domain1Uid, domain1Namespace));

    // verify all the server pods in domain1 were shutdown
    logger.info("Checking that admin server pod in domain1 was shutdown");
    checkPodDoesNotExist(domain1AdminServerPodName, domain1Uid, domain1Namespace);

    logger.info("Checking managed server pods in domain1 were shutdown");
    for (int i = 1; i <= numServersInDomain1; i++) {
      String domain1ManagedServerPodName = domain1Uid + "-" + MANAGED_SERVER_NAME_BASE + i;
      checkPodDoesNotExist(domain1ManagedServerPodName, domain1Uid, domain1Namespace);
    }

    // verify domain2 was not changed after domain1 was shut down
    logger.info("Verifying that domain2 was not changed after domain1 was shut down");
    verifyDomain2NotChanged(domain2Namespace);

    // restart domain1
    logger.info("Starting domain1");
    assertTrue(startDomain(domain1Uid, domain1Namespace),
        String.format("start domain %s in namespace %s failed", domain1Uid, domain1Namespace));

    // verify domain1 is restarted
    // check domain1 admin server pod is ready, also check admin service exists in the domain1 namespace
    logger.info("Checking admin server pod in domain1 was started");
    checkPodReadyAndServiceExists(domain1AdminServerPodName, domain1Uid, domain1Namespace);
    checkPodRestarted(domain1Uid, domain1Namespace, domain1AdminServerPodName,
        domainAdminPodOriginalTimestamps.get(0));

    // check managed server pods in domain1
    logger.info("Checking managed server pods in domain1 were started");
    for (int i = 1; i <= numServersInDomain1; i++) {
      String domain1ManagedServerPodName = domain1Uid + "-" + MANAGED_SERVER_NAME_BASE + i;
      checkPodReadyAndServiceExists(domain1ManagedServerPodName, domain1Uid, domain1Namespace);
      checkPodRestarted(domain1Uid, domain1Namespace, domain1ManagedServerPodName,
          domain1ManagedServerPodOriginalTimestampList.get(i - 1));
    }

    // verify domain2 was not changed after domain1 was restarted
    logger.info("Verifying that domain2 was not changed after domain1 was started");
    verifyDomain2NotChanged(domain2Namespace);
  }

  /**
   * Verify domain2 server pods were not changed.
   *
   * @param domain2Namespace namespace in which domain2 exists
   */
  private void verifyDomain2NotChanged(String domain2Namespace) {
    String domain2AdminServerPodName = domainAdminServerPodNames.get(1);

    logger.info("Checking that domain2 admin server pod state was not changed");
    assertThat(podStateNotChanged(domain2AdminServerPodName, domain2Uid, domain2Namespace,
        domainAdminPodOriginalTimestamps.get(1)))
        .as("Test state of pod {0} was not changed in namespace {1}",
            domain2AdminServerPodName, domain2Namespace)
        .withFailMessage("State of pod {0} was changed in namespace {1}",
            domain2AdminServerPodName, domain2Namespace)
        .isTrue();

    logger.info("Checking that domain2 managed server pods states were not changed");
    for (int i = 1; i <= replicaCount; i++) {
      String managedServerPodName = domain2Uid + "-" + MANAGED_SERVER_NAME_BASE + i;
      assertThat(podStateNotChanged(managedServerPodName, domain2Uid, domain2Namespace,
          domain2ManagedServerPodOriginalTimestampList.get(i - 1)))
          .as("Test state of pod {0} was not changed in namespace {1}",
              managedServerPodName, domain2Namespace)
          .withFailMessage("State of pod {0} was changed in namespace {1}",
              managedServerPodName, domain2Namespace)
          .isTrue();
    }
  }

  /**
   * Get domain1 and domain2 server pods original creation timestamps.
   * @param domainUids list of domainUids
   * @param domainNamespaces list of domain namespaces
   */
  private void getBothDomainsPodsOriginalCreationTimestamp(List<String> domainUids,
                                                           List<String> domainNamespaces) {
    domainAdminServerPodNames.clear();
    domainAdminPodOriginalTimestamps.clear();
    domain1ManagedServerPodOriginalTimestampList.clear();
    domain2ManagedServerPodOriginalTimestampList.clear();

    // get the domain1 pods original creation timestamp
    logger.info("Getting admin server pod original creation timestamps for both domains");
    for (int i = 0; i < numberOfDomains; i++) {
      domainAdminServerPodNames.add(domainUids.get(i) + "-" + ADMIN_SERVER_NAME_BASE);
      domainAdminPodOriginalTimestamps.add(
          getPodCreationTime(domainNamespaces.get(i), domainAdminServerPodNames.get(i)));
    }

    // get the managed server pods original creation timestamps
    logger.info("Getting managed server pods original creation timestamps for both domains");
    for (int i = 1; i <= replicaCount; i++) {
      domain1ManagedServerPodOriginalTimestampList.add(
          getPodCreationTime(domainNamespaces.get(0), domainUids.get(0) + "-" + MANAGED_SERVER_NAME_BASE + i));

      domain2ManagedServerPodOriginalTimestampList.add(
          getPodCreationTime(domainNamespaces.get(1), domainUids.get(1) + "-" + MANAGED_SERVER_NAME_BASE + i));
    }
  }

  /**
   * Shutdown both domains and verify all the server pods were shutdown.
   *
   * @param domainNamespaces list of domain namespaces
   */
  private void shutdownBothDomainsAndVerify(List<String> domainNamespaces) {

    // shutdown both domains
    logger.info("Shutting down both domains");
    for (int i = 0; i < numberOfDomains; i++) {
      shutdownDomain(domainUids.get(i), domainNamespaces.get(i));
    }

    // verify all the pods were shutdown
    logger.info("Verifying all server pods were shutdown for both domains");
    for (int i = 0; i < numberOfDomains; i++) {
      // check admin server pod was shutdown
      checkPodDoesNotExist(domainUids.get(i) + "-" + ADMIN_SERVER_NAME_BASE,
          domainUids.get(i), domainNamespaces.get(i));

      for (int j = 1; j <= replicaCount; j++) {
        String managedServerPodName = domainUids.get(i) + "-" + MANAGED_SERVER_NAME_BASE + j;
        checkPodDoesNotExist(managedServerPodName, domainUids.get(i), domainNamespaces.get(i));
      }
    }

    // check the scaled up managed servers in domain1 were shutdown
    logger.info("Verifying the scaled up managed servers in domain1 were shutdown");
    for (int i = replicaCount + 1; i <= replicasAfterScale; i++) {
      String managedServerPodName = domain1Uid + "-" + MANAGED_SERVER_NAME_BASE + i;
      checkPodDoesNotExist(managedServerPodName, domain1Uid, domain1Namespace);
    }

  }

  /**
   * Check pod is ready and service exists in the specified namespace.
   *
   * @param podName pod name to check
   * @param domainUid the label the pod is decorated with
   * @param namespace the namespace in which the pod exists
   */
  private void checkPodReadyAndServiceExists(String podName, String domainUid, String namespace) {
    logger.info("Waiting for pod {0} to be ready in namespace {1}", podName, namespace);
    checkPodReady(podName, domainUid, namespace);

    logger.info("Check service {0} exists in namespace {1}", podName, namespace);
    checkServiceExists(podName, namespace);

  }

  /**
   * Create a domain custom resource object.
   *
   * @param domainUid uid of the domain
   * @param domainNamespace namespace of the domain
   * @param wlSecretName WebLogic secret name
   * @param pvName name of persistence volume
   * @param pvcName name of persistence volume claim
   * @param t3ChannelPort t3 channel port for admin server
   * @return oracle.weblogic.domain.Domain object
   */
  private Domain createDomainCustomResource(String domainUid,
                                            String domainNamespace,
                                            String wlSecretName,
                                            String pvName,
                                            String pvcName,
                                            int t3ChannelPort) {
    Domain domain = new Domain()
        .apiVersion(DOMAIN_API_VERSION)
        .kind("Domain")
        .metadata(new V1ObjectMeta()
            .name(domainUid)
            .namespace(domainNamespace))
        .spec(new DomainSpec()
            .domainUid(domainUid)
            .domainHome("/shared/domains/" + domainUid)
            .domainHomeSourceType("PersistentVolume")
            .image(image)
            .imagePullSecrets(isUseSecret ? Arrays.asList(
                new V1LocalObjectReference()
                    .name(OCR_SECRET_NAME))
                : null)
            .webLogicCredentialsSecret(new V1SecretReference()
                .name(wlSecretName)
                .namespace(domainNamespace))
            .includeServerOutInPodLog(true)
            .logHomeEnabled(Boolean.TRUE)
            .logHome("/shared/logs/" + domainUid)
            .dataHome("")
            .serverStartPolicy("IF_NEEDED")
            .serverPod(new ServerPod()
                .addEnvItem(new V1EnvVar()
                    .name("JAVA_OPTIONS")
                    .value("-Dweblogic.StdoutDebugEnabled=false"))
                .addEnvItem(new V1EnvVar()
                    .name("USER_MEM_ARGS")
                    .value("-Djava.security.egd=file:/dev/./urandom "))
                .addVolumesItem(new V1Volume()
                    .name(pvName)
                    .persistentVolumeClaim(new V1PersistentVolumeClaimVolumeSource()
                        .claimName(pvcName)))
                .addVolumeMountsItem(new V1VolumeMount()
                    .mountPath("/shared")
                    .name(pvName)))
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
                .clusterName(clusterName)
                .replicas(replicaCount)
                .serverStartState("RUNNING")));

    return domain;
  }

  /**
   * Create two domains on PV using WLST.
   */
  private void createTwoDomainsSharingPVUsingWlstAndVerify() {

    String pvName = "default-sharing-pv";
    String pvcName = "default-sharing-pvc";

    if (isUseSecret) {
      // create pull secrets for WebLogic image
      createOCRRepoSecret(defaultNamespace);
    }

    // create WebLogic credentials secret
    createSecretWithUsernamePassword(wlSecretName, defaultNamespace, ADMIN_USERNAME_DEFAULT, ADMIN_PASSWORD_DEFAULT);

    // create persistent volume
    Path pvHostPath = assertDoesNotThrow(
        () -> createDirectories(get(PV_ROOT, this.getClass().getSimpleName(), "default-sharing-persistentVolume")),
        "createDirectories failed with IOException");

    logger.info("Creating PV directory {0}", pvHostPath);
    assertDoesNotThrow(() -> deleteDirectory(pvHostPath.toFile()), "deleteDirectory failed with IOException");
    assertDoesNotThrow(() -> createDirectories(pvHostPath), "createDirectories failed with IOException");

    V1PersistentVolume v1pv = new V1PersistentVolume()
        .spec(new V1PersistentVolumeSpec()
            .addAccessModesItem("ReadWriteMany")
            .storageClassName("default-sharing-weblogic-domain-storage-class")
            .volumeMode("Filesystem")
            .putCapacityItem("storage", Quantity.fromString("5Gi"))
            .persistentVolumeReclaimPolicy("Recycle")
            .accessModes(Arrays.asList("ReadWriteMany"))
            .hostPath(new V1HostPathVolumeSource()
                .path(pvHostPath.toString())))
        .metadata(new V1ObjectMetaBuilder()
            .withName(pvName)
            .build()
            .putLabelsItem("sharing-pv", "true"));

    V1PersistentVolumeClaim v1pvc = new V1PersistentVolumeClaim()
        .spec(new V1PersistentVolumeClaimSpec()
            .addAccessModesItem("ReadWriteMany")
            .storageClassName("default-sharing-weblogic-domain-storage-class")
            .volumeName(pvName)
            .resources(new V1ResourceRequirements()
                .putRequestsItem("storage", Quantity.fromString("5Gi"))))
        .metadata(new V1ObjectMetaBuilder()
            .withName(pvcName)
            .withNamespace(defaultNamespace)
            .build()
            .putLabelsItem("sharing-pvc", "true"));

    // create pv and pvc
    String labelSelector = String.format("sharing-pv in (%s)", "true");
    createPVPVCAndVerify(v1pv, v1pvc, labelSelector, defaultNamespace);

    for (int i = 1; i <= numberOfDomains; i++) {
      String domainUid = domainUids.get(i - 1);
      String domainScriptConfigMapName = "create-domain" + i + "-scripts-cm";
      String createDomainInPVJobName = "create-domain" + i + "-onpv-job";

      t3ChannelPort = getNextFreePort(32003, 32700);
      logger.info("t3ChannelPort for domain {0} is {1}", domainUid, t3ChannelPort);

      // run create a domain on PV job using WLST
      runCreateDomainOnPVJobUsingWlst(pvName, pvcName, domainUid, defaultNamespace, domainScriptConfigMapName,
          createDomainInPVJobName);

      // create the domain custom resource configuration object
      logger.info("Creating domain custom resource");
      Domain domain =
          createDomainCustomResource(domainUid, defaultNamespace, wlSecretName, pvName, pvcName, t3ChannelPort);

      logger.info("Creating domain custom resource {0} in namespace {1}", domainUid, defaultNamespace);
      createDomainAndVerify(domain, defaultNamespace);

      String adminServerPodName = domainUid + "-" + ADMIN_SERVER_NAME_BASE;
      // check admin server pod is ready and service exists in domain namespace
      checkPodReadyAndServiceExists(adminServerPodName, domainUid, defaultNamespace);

      // check for managed server pods are ready and services exist in domain namespace
      for (int j = 1; j <= replicaCount; j++) {
        String managedServerPodName = domainUid + "-" + MANAGED_SERVER_NAME_BASE + j;
        checkPodReadyAndServiceExists(managedServerPodName, domainUid, defaultNamespace);
      }

      logger.info("Getting admin service node port");
      int serviceNodePort =
          getServiceNodePort(defaultNamespace, adminServerPodName + "-external", "default");

      logger.info("Validating WebLogic admin server access by login to console");
      assertTrue(assertDoesNotThrow(
          () -> adminNodePortAccessible(serviceNodePort, ADMIN_USERNAME_DEFAULT, ADMIN_PASSWORD_DEFAULT),
          "Access to admin server node port failed"), "Console login validation failed");
    }
  }

  /**
   * Scale domain2 and verify there is no impact on domain1.
   */
  private void scaleDomain2AndVerifyNoImpactOnDomain1() {

    // scale domain2 from 2 servers to 3 servers
    replicasAfterScale = 3;
    logger.info("Scaling cluster {0} of domain {1} in namespace {2} to {3} servers.",
        clusterName, domain2Uid, defaultNamespace, replicasAfterScale);
    scaleAndVerifyCluster(clusterName, domain2Uid, defaultNamespace,
        domain2Uid + "-" + MANAGED_SERVER_NAME_BASE, replicaCount, replicasAfterScale,
        null, null);

    // scale domain2 from 3 servers to 2 servers
    logger.info("Scaling cluster {0} of domain {1} in namespace {2} to {3} servers.",
        clusterName, domain2Uid, defaultNamespace, replicaCount);
    scaleAndVerifyCluster(clusterName, domain2Uid, defaultNamespace,
        domain2Uid + "-" + MANAGED_SERVER_NAME_BASE, replicasAfterScale, replicaCount,
        null, null);

    // verify scaling domain2 has no impact on domain1
    logger.info("Checking that domain1 was not changed after domain2 was scaled up");
    verifyDomain1NotChanged();
  }

  /**
   * Verify domain1 server pods were not changed.
   */
  private void verifyDomain1NotChanged() {
    String domain1AdminServerPodName = domainAdminServerPodNames.get(0);

    logger.info("Checking that domain1 admin server pod state was not changed");
    assertThat(podStateNotChanged(domain1AdminServerPodName, domain1Uid, defaultNamespace,
        domainAdminPodOriginalTimestamps.get(0)))
        .as("Test state of pod {0} was not changed in namespace {1}",
            domain1AdminServerPodName, defaultNamespace)
        .withFailMessage("State of pod {0} was changed in namespace {1}",
            domain1AdminServerPodName, defaultNamespace)
        .isTrue();

    logger.info("Checking that domain1 managed server pods states were not changed");
    for (int i = 1; i <= replicaCount; i++) {
      String managedServerPodName = domain1Uid + "-" + MANAGED_SERVER_NAME_BASE + i;
      assertThat(podStateNotChanged(managedServerPodName, domain1Uid, defaultNamespace,
          domain1ManagedServerPodOriginalTimestampList.get(i - 1)))
          .as("Test state of pod {0} was not changed in namespace {1}",
              managedServerPodName, defaultNamespace)
          .withFailMessage("State of pod {0} was changed in namespace {1}",
              managedServerPodName, defaultNamespace)
          .isTrue();
    }
  }
}
