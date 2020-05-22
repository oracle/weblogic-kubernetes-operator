// Copyright (c) 2020, Oracle Corporation and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.weblogic.kubernetes;

import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;

import io.kubernetes.client.custom.Quantity;
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
import oracle.weblogic.kubernetes.annotations.IntegrationTest;
import oracle.weblogic.kubernetes.annotations.Namespaces;
import oracle.weblogic.kubernetes.extensions.LoggedTest;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static java.nio.file.Files.copy;
import static java.nio.file.Files.createDirectories;
import static java.nio.file.Paths.get;
import static oracle.weblogic.kubernetes.TestConstants.ADMIN_SERVER_NAME_BASE;
import static oracle.weblogic.kubernetes.TestConstants.DOMAIN_API_VERSION;
import static oracle.weblogic.kubernetes.TestConstants.K8S_NODEPORT_HOST;
import static oracle.weblogic.kubernetes.TestConstants.KIND_REPO;
import static oracle.weblogic.kubernetes.TestConstants.MANAGED_SERVER_NAME_BASE;
import static oracle.weblogic.kubernetes.TestConstants.OCR_PASSWORD;
import static oracle.weblogic.kubernetes.TestConstants.OCR_REGISTRY;
import static oracle.weblogic.kubernetes.TestConstants.OCR_SECRET_NAME;
import static oracle.weblogic.kubernetes.TestConstants.OCR_USERNAME;
import static oracle.weblogic.kubernetes.TestConstants.PV_ROOT;
import static oracle.weblogic.kubernetes.TestConstants.RESULTS_ROOT;
import static oracle.weblogic.kubernetes.actions.ActionConstants.RESOURCE_DIR;
import static oracle.weblogic.kubernetes.actions.ActionConstants.WLS_BASE_IMAGE_NAME;
import static oracle.weblogic.kubernetes.actions.ActionConstants.WLS_BASE_IMAGE_TAG;
import static oracle.weblogic.kubernetes.actions.TestActions.dockerLogin;
import static oracle.weblogic.kubernetes.actions.TestActions.dockerPull;
import static oracle.weblogic.kubernetes.actions.TestActions.dockerPush;
import static oracle.weblogic.kubernetes.actions.TestActions.dockerTag;
import static oracle.weblogic.kubernetes.actions.TestActions.getPodCreationTimestamp;
import static oracle.weblogic.kubernetes.actions.TestActions.getServiceNodePort;
import static oracle.weblogic.kubernetes.actions.TestActions.restart;
import static oracle.weblogic.kubernetes.actions.TestActions.shutdown;
import static oracle.weblogic.kubernetes.assertions.TestAssertions.adminNodePortAccessible;
import static oracle.weblogic.kubernetes.assertions.TestAssertions.podStateNotChanged;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.checkPodDoesNotExist;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.checkPodExists;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.checkPodReady;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.checkPodRestarted;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.checkServiceExists;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.createConfigMapForDomainCreation;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.createDomainAndVerify;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.createOCRRepoSecret;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.createPVPVCAndVerify;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.createSecretWithUsernamePassword;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.installAndVerifyOperator;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.runCreateDomainJob;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.scaleAndVerifyCluster;
import static org.apache.commons.io.FileUtils.deleteDirectory;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Test operator manages multiple domains.
 */
@DisplayName("Verify operator manages multiple domains")
@IntegrationTest
public class ItOperatorTwoDomains implements LoggedTest {

  private static final int numberOfDomains = 2;
  private static final int numberOfOperators = 2;

  private static List<String> opNamespaces = new ArrayList<>();
  private static List<String> domainNamespaces = new ArrayList<>();
  private static List<String> domainUids = new ArrayList<>();

  // domain constants
  private final String clusterName = "cluster-1";
  private final int replicaCount = 2;
  private final String adminUser = "weblogic";
  private final String adminPassword = "welcome1";

  private String image = null;
  private boolean isUseSecret = false;
  private int replicasAfterScale;
  private List<String> domainAdminServerPodNames = new ArrayList<>();
  private List<String> domainAdminPodOriginalTimestamps = new ArrayList<>();
  private List<String> domain1ManagedServerPodOriginalTimestampList = new ArrayList<>();
  private List<String> domain2ManagedServerPodOriginalTimestampList = new ArrayList<>();

  /**
   * Install operator.
   *
   * @param namespaces injected by JUnit
   */
  @BeforeAll
  public static void initAll(@Namespaces(4) List<String> namespaces) {

    // get a unique operator namespace
    logger.info("Get a unique namespace for operator1");
    for (int i = 0; i < numberOfOperators; i++) {
      assertNotNull(namespaces.get(i), "Namespace list is null");
      opNamespaces.add(namespaces.get(i));
    }

    // get a unique domain namespace
    logger.info("Get a unique namespace for WebLogic domain");
    for (int i = numberOfOperators; i < numberOfOperators + numberOfDomains; i++) {
      assertNotNull(namespaces.get(i), "Namespace list is null");
      domainNamespaces.add(namespaces.get(i));
    }

    // install and verify operator
    for (int i = 0; i < numberOfOperators; i++) {
      installAndVerifyOperator(opNamespaces.get(i), domainNamespaces.get(i));
    }

    // initiate domainUid list for two domains
    for (int i = 1; i <= numberOfDomains; i++) {
      domainUids.add("domain" + i);
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
   * @throws IOException when creating PV path fails
   */
  @Test
  @DisplayName("Create domain on PV using WLST script")
  public void testTwoDomainsManagedByTwoOperators() throws IOException {

    image = WLS_BASE_IMAGE_NAME + ":" + WLS_BASE_IMAGE_TAG;

    if (!KIND_REPO.isEmpty()) {
      // We can't figure out why the kind clusters can't pull images from OCR using the image pull secret. There
      // is some evidence it may be a containerd bug. Therefore, we are going to "give up" and workaround the issue.
      // The workaround will be to:
      //   1. docker login
      //   2. docker pull
      //   3. docker tag with the KIND_REPO value
      //   4. docker push this new image name
      //   5. use this image name to create the domain resource
      assertTrue(dockerLogin(OCR_REGISTRY, OCR_USERNAME, OCR_PASSWORD), "docker login failed");
      assertTrue(dockerPull(image), String.format("docker pull failed for image %s", image));

      String kindRepoImage = KIND_REPO + image.substring(TestConstants.OCR_REGISTRY.length() + 1);
      assertTrue(dockerTag(image, kindRepoImage),
          String.format("docker tag failed for images %s, %s", image, kindRepoImage));
      assertTrue(dockerPush(kindRepoImage), String.format("docker push failed for image %s", kindRepoImage));
      image = kindRepoImage;
    } else {
      // create pull secrets for WebLogic image
      for (int i = 0; i < numberOfDomains; i++) {
        createOCRRepoSecret(domainNamespaces.get(i));
      }
      isUseSecret = true;
    }

    // create two domains on PV using WLST
    createTwoDomainsOnPVUsingWlstAndVerify();

    // get the domain1 and domain2 pods original creation timestamp
    getBothDomainsPodsOriginalCreationTimestamp();

    // scale cluster in domain 1 from 2 to 3 servers and verify no impact on domain 2
    replicasAfterScale = 3;
    scaleDomain1AndVerifyNoImpactOnDomain2();

    // restart domain1 and verify no impact on domain2
    restartDomain1AndVerifyNoImpactOnDomain2();

    // shutdown both domains and verify the pods were shutdown
    shutdownBothDomainsAndVerify();
  }

  /**
   * Create two domains on PV using WLST.
   * @throws IOException when creating PV path fails
   */
  private void createTwoDomainsOnPVUsingWlstAndVerify() throws IOException {

    String wlSecretName = "weblogic-credentials";

    for (int i = 0; i < numberOfDomains; i++) {
      String domainUid = domainUids.get(i);
      String domainNamespace = domainNamespaces.get(i);
      String pvName = domainUid + "-pv";
      String pvcName = domainUid + "-pvc";

      // create WebLogic credentials secret
      createSecretWithUsernamePassword(wlSecretName, domainNamespace, adminUser, adminPassword);

      // create persistent volume and persistent volume claims
      Path pvHostPath =
          createDirectories(get(PV_ROOT, this.getClass().getSimpleName(), domainUid + "-persistentVolume"));

      logger.info("Creating PV directory {0}", pvHostPath);
      deleteDirectory(pvHostPath.toFile());
      createDirectories(pvHostPath);

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
              .withNamespace(domainNamespace)
              .build()
              .putLabelsItem("weblogic.resourceVersion", "domain-v2")
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
              .putLabelsItem("weblogic.resourceVersion", "domain-v2")
              .putLabelsItem("weblogic.domainUid", domainUid));

      String labelSelector = String.format("weblogic.domainUid in (%s)", domainUid);
      createPVPVCAndVerify(v1pv, v1pvc, labelSelector, domainNamespace);

      // create a domain on PV using WLST
      createDomainOnPVUsingWlst(pvName, pvcName, domainUid, domainNamespace);

      // create the domain custom resource configuration object
      logger.info("Creating domain custom resource");
      Domain domain = new Domain()
          .apiVersion(DOMAIN_API_VERSION)
          .kind("Domain")
          .metadata(new V1ObjectMeta() //metadata
              .name(domainUid)
              .namespace(domainNamespace))
          .spec(new DomainSpec() //spec
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
              .serverPod(new ServerPod() //serverpod
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
              .adminServer(new AdminServer() //admin server
                  .serverStartState("RUNNING")
                  .adminService(new AdminService()
                      .addChannelsItem(new Channel()
                          .channelName("default")
                          .nodePort(0))
                      .addChannelsItem(new Channel()
                          .channelName("T3Channel")
                          .nodePort(0))))
              .addClustersItem(new Cluster() //cluster
                  .clusterName(clusterName)
                  .replicas(replicaCount)
                  .serverStartState("RUNNING")));

      logger.info("Creating domain custom resource {0} in namespace {1}", domainUid, domainNamespace);
      createDomainAndVerify(domain, domainNamespace);

      String adminServerPodName = domainUid + "-" + ADMIN_SERVER_NAME_BASE;
      // check admin server pod is ready and service exists in domain namespace
      checkPodExistsReadyAndServiceExists(adminServerPodName, domainUid, domainNamespace);

      // check for managed server pods existence
      for (int j = 1; j <= replicaCount; j++) {
        String managedServerPodName = domainUid + "-" + MANAGED_SERVER_NAME_BASE + j;
        checkPodExistsReadyAndServiceExists(managedServerPodName, domainUid, domainNamespace);
      }

      logger.info("Getting node port");
      int serviceNodePort = assertDoesNotThrow(() ->
              getServiceNodePort(domainNamespace, adminServerPodName + "-external", "default"),
          "Getting admin server node port failed");

      logger.info("Validating WebLogic admin server access by login to console");
      assertTrue(assertDoesNotThrow(() -> adminNodePortAccessible(serviceNodePort, adminUser, adminPassword),
          "Access to admin server node port failed"), "Console login validation failed");
    }
  }

  /**
   * Creates a WebLogic domain on a persistent volume by doing the following.
   * Copies the WLST domain script to a temp location.
   * Creates a domain properties in the temp location.
   * Creates a configmap containing domain scripts and property files.
   * Runs a job to create domain on persistent volume.
   *
   * @param pvName persistence volume on which the WebLogic domain home will be hosted
   * @param pvcName persistence volume claim for the WebLogic domain
   * @param domainUid the Uid of the domain to create
   * @param domainNamespace the namespace in which the domain will be created
   * @throws IOException when reading/writing domain scripts fails
   */
  private void createDomainOnPVUsingWlst(String pvName,
                                         String pvcName,
                                         String domainUid,
                                         String domainNamespace) throws IOException {

    logger.info("Creating a staging location for domain creation scripts");
    Path pvTemp = get(RESULTS_ROOT, this.getClass().getSimpleName(), "domainCreateTempPV");
    deleteDirectory(pvTemp.toFile());
    createDirectories(pvTemp);

    logger.info("copy the create domain WLST script to staging location");
    Path srcWlstScript = get(RESOURCE_DIR, "python-scripts", "wlst-create-domain-onpv.py");
    Path targetWlstScript = get(pvTemp.toString(), "create-domain.py");
    copy(srcWlstScript, targetWlstScript, StandardCopyOption.REPLACE_EXISTING);

    logger.info("create WebLogic domain properties file");
    Path domainPropertiesFile = get(pvTemp.toString(), "domain.properties");
    assertDoesNotThrow(
        () -> createDomainProperties(domainPropertiesFile, domainUid),
        "Creating domain properties file failed");

    logger.info("add files to a config map for domain creation job");
    List<Path> domainScriptFiles = new ArrayList<>();
    domainScriptFiles.add(targetWlstScript);
    domainScriptFiles.add(domainPropertiesFile);

    logger.info("Create a config map to hold domain creation scripts");
    String domainScriptConfigMapName = "create-domain-scripts-cm";
    assertDoesNotThrow(
        () -> createConfigMapForDomainCreation(domainScriptConfigMapName, domainScriptFiles, domainNamespace),
        "Creating configmap for domain creation failed");

    logger.info("Running a Kubernetes job to create the domain");
    V1Job jobBody = new V1Job()
        .metadata(
            new V1ObjectMeta()
                .name("create-domain-onpv-job") // name of the create domain job
                .namespace(domainNamespace))
        .spec(new V1JobSpec()
            .backoffLimit(0) // try only once
            .template(new V1PodTemplateSpec()
                .spec(new V1PodSpec()
                    .restartPolicy("Never")
                    .initContainers(Arrays.asList(new V1Container()
                        .name("fix-pvc-owner")  // change the ownership of the pv to opc:opc
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
                        .imagePullPolicy("Always")
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
                                    .name(domainScriptConfigMapName))))  //config map containing domain scripts
                    .imagePullSecrets(isUseSecret ? Arrays.asList(
                        new V1LocalObjectReference()
                            .name(OCR_SECRET_NAME))
                        : null))));

    runCreateDomainJob(jobBody, domainNamespace);

  }

  /**
   * Create a properties file for WebLogic domain configuration.
   * @param wlstPropertiesFile path of the properties file
   * @param domainUid the Uid of WebLogic domain to which the properties file is associated
   * @throws FileNotFoundException when properties file path not found
   * @throws IOException when writing properties fails
   */
  private void createDomainProperties(Path wlstPropertiesFile,
                                            String domainUid) throws FileNotFoundException, IOException {
    // create a list of properties for the WebLogic domain configuration
    Properties p = new Properties();

    p.setProperty("domain_path", "/shared/domains");
    p.setProperty("domain_name", domainUid);
    p.setProperty("cluster_name", clusterName);
    p.setProperty("admin_server_name", ADMIN_SERVER_NAME_BASE);
    p.setProperty("managed_server_port", "8001");
    p.setProperty("admin_server_port", "7001");
    p.setProperty("admin_username", adminUser);
    p.setProperty("admin_password", adminPassword);
    p.setProperty("admin_t3_public_address", K8S_NODEPORT_HOST);
    p.setProperty("admin_t3_channel_port", "32001");
    p.setProperty("number_of_ms", "4");
    p.setProperty("managed_server_name_base", MANAGED_SERVER_NAME_BASE);
    p.setProperty("domain_logs", "/shared/logs");
    p.setProperty("production_mode_enabled", "true");

    p.store(new FileOutputStream(wlstPropertiesFile.toFile()), "WLST properties file");
  }

  /**
   * Scale domain1 and verify there is no impact on domain2.
   */
  private void scaleDomain1AndVerifyNoImpactOnDomain2() {

    // scale domain1
    logger.info("Scaling cluster {0} of domain {1} in namespace {2} to {3} servers.",
        clusterName, domainUids.get(0), domainNamespaces.get(0), replicasAfterScale);
    scaleAndVerifyCluster(clusterName, domainUids.get(0), domainNamespaces.get(0),
        domainUids.get(0) + "-" + MANAGED_SERVER_NAME_BASE, replicaCount, replicasAfterScale,
        null, null);

    // add the third managed server pod original creation timestamp to the list
    domain1ManagedServerPodOriginalTimestampList.add(
        getPodOriginalCreationTimestamp(
            domainUids.get(0) + "-" + MANAGED_SERVER_NAME_BASE + replicasAfterScale,
            domainNamespaces.get(0)));

    // verify scaling domain1 has no impact on domain2
    logger.info("Checking that domain2 was not changed after domain1 was scaled up");
    verifyDomain2NotChanged();
  }

  /**
   * Restart domain1 and verify there was no impact on domain2.
   */
  private void restartDomain1AndVerifyNoImpactOnDomain2() {

    // shutdown domain1
    assertTrue(shutdown(domainUids.get(0), domainNamespaces.get(0)),
        String.format("restart domain %s in namespace %s failed", domainUids.get(0), domainNamespaces.get(0)));

    // verify all the server pods in domain1 were shutdown
    checkPodDoesNotExist(domainAdminServerPodNames.get(0), domainUids.get(0), domainNamespaces.get(0));

    for (int i = 1; i <= replicasAfterScale; i++) {
      String domain1ManagedServerPodName = domainUids.get(0) + "-" + MANAGED_SERVER_NAME_BASE + i;
      checkPodDoesNotExist(domain1ManagedServerPodName, domainUids.get(0), domainNamespaces.get(0));
    }

    // restart domain1
    assertTrue(restart(domainUids.get(0), domainNamespaces.get(0)),
        String.format("restart domain %s in namespace %s failed", domainUids.get(0), domainNamespaces.get(0)));

    // verify domain1 is restarted
    // check domain1 admin server pod exists and ready, also check admin service exists in the domain1 namespace
    checkPodExistsReadyAndServiceExists(domainAdminServerPodNames.get(0), domainUids.get(0), domainNamespaces.get(0));
    checkPodRestarted(domainUids.get(0), domainNamespaces.get(0), domainAdminServerPodNames.get(0),
        domainAdminPodOriginalTimestamps.get(0));

    // check managed server pods in domain1
    for (int i = 1; i <= replicasAfterScale; i++) {
      String domain1ManagedServerPodName = domainUids.get(0) + "-" + MANAGED_SERVER_NAME_BASE + i;
      checkPodExistsReadyAndServiceExists(domain1ManagedServerPodName, domainUids.get(0), domainNamespaces.get(0));
      checkPodRestarted(domainUids.get(0), domainNamespaces.get(0), domain1ManagedServerPodName,
          domain1ManagedServerPodOriginalTimestampList.get(i - 1));
    }

    // verify domain 2 was not changed after domain1 was restarted
    verifyDomain2NotChanged();
  }

  /**
   * Verify domain2 server pods were no changed.
   */
  private void verifyDomain2NotChanged() {
    String domain2AdminServerPodName = domainAdminServerPodNames.get(1);
    String domain2Namespace = domainNamespaces.get(1);
    String domain2Uid = domainUids.get(1);

    logger.info("Checking that domain2 admin server pod state was not changed");
    assertThat(podStateNotChanged(domain2AdminServerPodName, domain2Uid, domain2Namespace,
        domainAdminPodOriginalTimestamps.get(1)))
        .as("Test state of pod {0} was not changed in namespace {1}",
            domain2AdminServerPodName, domain2Namespace)
        .withFailMessage("State of pod {0} was changed in namespace {1}",
            domain2AdminServerPodName, domain2Namespace)
        .isTrue();

    logger.info("Checking that domain2 managed server pod state was not changed");
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
   * Get domain1 an domain2 server pods original creation timestamp.
   */
  private void getBothDomainsPodsOriginalCreationTimestamp() {
    // get the domain1 pods original creation timestamp
    logger.info("Getting admin server pod original creation timestamp for both domains");
    for (int i = 0; i < numberOfDomains; i++) {
      domainAdminServerPodNames.add(domainUids.get(i) + "-" + ADMIN_SERVER_NAME_BASE);
      domainAdminPodOriginalTimestamps.add(
          getPodOriginalCreationTimestamp(domainAdminServerPodNames.get(i), domainNamespaces.get(i)));
    }

    // get the managed server pods original creation timestamps
    logger.info("Getting managed server pods original creation timestamps for both domains");
    for (int i = 1; i <= replicaCount; i++) {
      String managedServerPodName = domainUids.get(0) + "-" + MANAGED_SERVER_NAME_BASE + i;
      domain1ManagedServerPodOriginalTimestampList.add(
          getPodOriginalCreationTimestamp(managedServerPodName, domainNamespaces.get(0)));

      managedServerPodName = domainUids.get(1) + "-" + MANAGED_SERVER_NAME_BASE + i;
      domain2ManagedServerPodOriginalTimestampList.add(
          getPodOriginalCreationTimestamp(managedServerPodName, domainNamespaces.get(1)));
    }
  }

  /**
   * Shutdown both domains and verify all the server pods were shutdown.
   */
  private void shutdownBothDomainsAndVerify() {

    // shutdown both domains
    for (int i = 0; i < numberOfDomains; i++) {
      shutdown(domainUids.get(i), domainNamespaces.get(i));
    }

    // verify all the pods were shutdown
    for (int i = 0; i < numberOfDomains; i++) {
      // check admin server pod was shutdown
      checkPodDoesNotExist(domainUids.get(i) + "-" + ADMIN_SERVER_NAME_BASE,
          domainUids.get(i), domainNamespaces.get(i));

      for (int j = 1; j <= replicaCount; j++) {
        String managedServerPodName = domainUids.get(i) + "-" + MANAGED_SERVER_NAME_BASE + j;
        checkPodDoesNotExist(managedServerPodName, domainUids.get(i), domainNamespaces.get(i));
      }
    }

    // check the scaled up managed servers in domain1 was shutdown
    for (int i = replicaCount + 1; i <= replicasAfterScale; i++) {
      String managedServerPodName = domainUids.get(0) + "-" + MANAGED_SERVER_NAME_BASE + i;
      checkPodDoesNotExist(managedServerPodName, domainUids.get(0), domainNamespaces.get(0));
    }

  }

  /**
   * Get pod original creation timestamp.
   * @param podName pod name to get the original creation timestamp
   * @param namespace the namespace in which the pod exists
   * @return the pod original creation timestamp
   */
  private String getPodOriginalCreationTimestamp(String podName, String namespace) {
    return assertDoesNotThrow(() -> getPodCreationTimestamp(namespace, "", podName),
        String.format("getPodCreationTimestamp failed with ApiException for pod %s in namespace %s",
            podName, namespace));
  }

  /**
   * Check pod exist, ready and service exists in the specified namespace.
   *
   * @param podName pod name to check
   * @param domainUid the label the pod is decorated with
   * @param namespace the namespace in which the pod exists
   */
  private void checkPodExistsReadyAndServiceExists(String podName, String domainUid, String namespace) {
    logger.info("Checking that pod {0} exists in namespace {1}", podName, namespace);
    checkPodExists(podName, domainUid, namespace);

    logger.info("Waiting for pod {0} to be ready in namespace {1}", podName, namespace);
    checkPodReady(podName, domainUid, namespace);

    logger.info("Check service {0} exists in namespace {1}", podName, namespace);
    checkServiceExists(podName, namespace);

  }
}
