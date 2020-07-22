// Copyright (c) 2020, Oracle Corporation and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.weblogic.kubernetes;

import java.io.FileOutputStream;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.stream.Stream;

import io.kubernetes.client.custom.Quantity;
import io.kubernetes.client.custom.V1Patch;
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
import oracle.weblogic.domain.Configuration;
import oracle.weblogic.domain.Domain;
import oracle.weblogic.domain.DomainSpec;
import oracle.weblogic.domain.Model;
import oracle.weblogic.domain.ServerPod;
import oracle.weblogic.kubernetes.annotations.IntegrationTest;
import oracle.weblogic.kubernetes.annotations.Namespaces;
import oracle.weblogic.kubernetes.logging.LoggingFacade;
import org.joda.time.DateTime;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import static java.nio.file.Files.copy;
import static java.nio.file.Files.createDirectories;
import static java.nio.file.Paths.get;
import static oracle.weblogic.kubernetes.TestConstants.ADMIN_PASSWORD_DEFAULT;
import static oracle.weblogic.kubernetes.TestConstants.ADMIN_SERVER_NAME_BASE;
import static oracle.weblogic.kubernetes.TestConstants.ADMIN_USERNAME_DEFAULT;
import static oracle.weblogic.kubernetes.TestConstants.DOMAIN_API_VERSION;
import static oracle.weblogic.kubernetes.TestConstants.K8S_NODEPORT_HOST;
import static oracle.weblogic.kubernetes.TestConstants.KIND_REPO;
import static oracle.weblogic.kubernetes.TestConstants.MANAGED_SERVER_NAME_BASE;
import static oracle.weblogic.kubernetes.TestConstants.MII_BASIC_IMAGE_NAME;
import static oracle.weblogic.kubernetes.TestConstants.MII_BASIC_IMAGE_TAG;
import static oracle.weblogic.kubernetes.TestConstants.OCR_REGISTRY;
import static oracle.weblogic.kubernetes.TestConstants.OCR_SECRET_NAME;
import static oracle.weblogic.kubernetes.TestConstants.PV_ROOT;
import static oracle.weblogic.kubernetes.TestConstants.REPO_SECRET_NAME;
import static oracle.weblogic.kubernetes.TestConstants.RESULTS_ROOT;
import static oracle.weblogic.kubernetes.TestConstants.WDT_BASIC_IMAGE_NAME;
import static oracle.weblogic.kubernetes.TestConstants.WDT_BASIC_IMAGE_TAG;
import static oracle.weblogic.kubernetes.TestConstants.WLS_DOMAIN_TYPE;
import static oracle.weblogic.kubernetes.actions.ActionConstants.RESOURCE_DIR;
import static oracle.weblogic.kubernetes.actions.ActionConstants.WLS_BASE_IMAGE_NAME;
import static oracle.weblogic.kubernetes.actions.ActionConstants.WLS_BASE_IMAGE_TAG;
import static oracle.weblogic.kubernetes.actions.TestActions.getDomainCustomResource;
import static oracle.weblogic.kubernetes.actions.TestActions.getPodCreationTimestamp;
import static oracle.weblogic.kubernetes.actions.TestActions.patchDomainCustomResource;
import static oracle.weblogic.kubernetes.assertions.TestAssertions.verifyRollingRestartOccurred;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.checkPodReadyAndServiceExists;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.createConfigMapFromFiles;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.createDockerRegistrySecret;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.createDomainAndVerify;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.createJobAndWaitUntilComplete;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.createOCRRepoSecret;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.createPVPVCAndVerify;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.createSecretWithUsernamePassword;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.dockerLoginAndPushImageToRegistry;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.installAndVerifyOperator;
import static oracle.weblogic.kubernetes.utils.TestUtils.getNextFreePort;
import static oracle.weblogic.kubernetes.utils.ThreadSafeLogger.getLogger;
import static org.apache.commons.io.FileUtils.deleteDirectory;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Test pods are restarted by changing properties in server pods for different type of domains.
 */
@DisplayName("Test pods are restarted by changing properties in server pods for different type of domains")
@IntegrationTest
class ItParameterizedPodsRestart {

  // domain constants
  private static final String clusterName = "cluster-1";
  private static final String wlSecretName = "weblogic-credentials";
  private static final int replicaCount = 2;

  private static String wlsBaseImage = WLS_BASE_IMAGE_NAME + ":" + WLS_BASE_IMAGE_TAG;
  private static boolean isUseSecret = true;
  private static int t3ChannelPort = 0;
  private static List<Domain> domains = new ArrayList<>();
  private static LoggingFacade logger = null;

  /**
   * Get namespaces for operator and three different type of domains.
   * Install operator.
   * Create three different type of domains: model in image, domain in PV, domain in image.
   *
   * @param namespaces list of namespaces created by the IntegrationTestWatcher by the
   *                   JUnit engine parameter resolution mechanism
   */
  @BeforeAll
  public static void initAll(@Namespaces(4) List<String> namespaces) {
    logger = getLogger();

    // get a unique operator namespace
    logger.info("Getting a unique namespace for operator");
    assertNotNull(namespaces.get(0), "Namespace list is null");
    String opNamespace = namespaces.get(0);

    // get unique domain namespaces for each domain
    logger.info("Getting unique namespaces for three different type of domains");
    assertNotNull(namespaces.get(1));
    String miiDomainNamespace = namespaces.get(1);
    assertNotNull(namespaces.get(2));
    String domainInPVNamespace = namespaces.get(2);
    assertNotNull(namespaces.get(3));
    String domainInImageNamespace = namespaces.get(3);

    // install and verify operator
    installAndVerifyOperator(opNamespace, miiDomainNamespace, domainInPVNamespace, domainInImageNamespace);

    //determine if the tests are running in Kind cluster. if true use images from Kind registry
    if (KIND_REPO != null) {
      String kindRepoImage = KIND_REPO + wlsBaseImage.substring(OCR_REGISTRY.length() + 1);
      logger.info("Using image {0}", kindRepoImage);
      wlsBaseImage = kindRepoImage;
      isUseSecret = false;
    }

    // create domains with different domain types
    Domain miiDomain = createAndVerifyMiiDomain(miiDomainNamespace);
    Domain domainInPV = createAndVerifyDomainInPVUsingWlst(domainInPVNamespace);
    Domain domainInImage = createAndVerifyDomainInImageUsingWdt(domainInImageNamespace);

    domains.add(miiDomain);
    domains.add(domainInPV);
    domains.add(domainInImage);
  }

  /**
   * For each domain type, test the following.
   * Add/Modify server pod resources by patching the domain custom resource.
   * Verify all pods are restarted and back to ready state.
   * The resources tested: resources: limits: cpu: "1", resources: requests: cpu: "0.5"
   * Test fails if any server pod is not restarted and back to ready state or the compute resources in the patched
   * domain custom resource do not match the values we planned to add or modify.
   *
   * @param domain oracle.weblogic.domain.Domain object
   */
  @ParameterizedTest
  @DisplayName("Test pods are restarted by changing properties in server pods for three different type of domains")
  @MethodSource("domainProvider")
  public void testParamsServerPodsRestartByChangingResource(Domain domain) {
    assertNotNull(domain, "domain is null");
    assertNotNull(domain.getMetadata(), domain + " metadata is null");

    // Add/Modify server pod resources by patching the domain custom resource
    // verify all pods are restarted and back to ready state
    logger.info("testServerPodsRestartByChangingResource with domain {0}", domain.getMetadata().getName());
    testServerPodsRestartByChangingResource(domain);
  }

  /**
   * Generate a steam of Domain objects used in parameterized tests.
   * @return stream of oracle.weblogic.domain.Domain objects
   */
  private static Stream<Domain> domainProvider() {
    return domains.stream();
  }

  /**
   * Add/Modify server pod resources by patching the domain custom resource.
   * Verify all pods are restarted and back to ready state.
   * @param domain1 oracle.weblogic.domain.Domain object
   */
  private void testServerPodsRestartByChangingResource(Domain domain1) {

    assertNotNull(domain1, domain1 + " is null");
    assertNotNull(domain1.getSpec(), domain1 + "/spec is null");
    assertNotNull(domain1.getSpec().getServerPod(), domain1 + "/spec/serverPod is null");
    assertNotNull(domain1.getSpec().getServerPod().getResources(), domain1 + "/spec/serverPod/resources is null");
    assertNotNull(domain1.getMetadata(), domain1 + " metadata is null");

    String domainUid = domain1.getSpec().getDomainUid();
    String domainNamespace = domain1.getMetadata().getNamespace();
    String adminServerPodName = domainUid + "-" + ADMIN_SERVER_NAME_BASE;
    String managedServerPrefix = domainUid + "-" + MANAGED_SERVER_NAME_BASE;

    // get the current server pod compute resource limit
    Map<String, Quantity> limits = domain1.getSpec().getServerPod().getResources().getLimits();
    assertNotNull(limits, domain1 + "/spec/serverPod/resources/limits is null");

    // print out current server pod compute resource limits
    logger.info("Current value for server pod compute resource limits:");
    limits.forEach((key, value) -> logger.info(key + ": " + value.toString()));

    // get the current server pod compute resource requests
    Map<String, Quantity> requests = domain1.getSpec().getServerPod().getResources().getRequests();
    assertNotNull(requests, domain1 + "/spec/serverPod/resources/requests is null");

    // print out current server pod compute resource requests
    logger.info("Current value for server pod compute resource requests:");
    requests.forEach((key, value) -> logger.info(key + ": " + value.toString()));

    // create the map with server pods and their original creation timestamps
    Map<String, DateTime> podsWithTimeStamps = new LinkedHashMap<>();
    podsWithTimeStamps.put(adminServerPodName,
        assertDoesNotThrow(() -> getPodCreationTimestamp(domainNamespace, "", adminServerPodName),
        String.format("getPodCreationTimestamp failed with ApiException for pod %s in namespace %s",
            adminServerPodName, domainNamespace)));

    for (int i = 1; i <= replicaCount; i++) {
      String managedServerPodName = managedServerPrefix + i;
      podsWithTimeStamps.put(managedServerPodName,
          assertDoesNotThrow(() -> getPodCreationTimestamp(domainNamespace, "", managedServerPodName),
              String.format("getPodCreationTimestamp failed with ApiException for pod %s in namespace %s",
                  managedServerPodName, domainNamespace)));
    }

    // add the new server pod compute resources limits: cpu: 1, requests: cpu: 0.5
    BigDecimal cpuLimit = new BigDecimal(1);
    BigDecimal cpuRequest = new BigDecimal(0.5);

    // verify if cpu limit was set then the new value should be different than the original value
    if (limits.get("cpu") != null) {
      assertNotEquals(limits.get("cpu").getNumber().compareTo(cpuLimit), 0,
          String.format("server pod compute resources cpu limit is already set to %s, set cpu limit to "
              + "a different value", cpuLimit));
    }

    // verify if cpu request was set then the new value should be different than the original value
    if (requests.get("cpu") != null) {
      assertNotEquals(requests.get("cpu").getNumber().compareTo(cpuRequest), 0,
          String.format("server pod compute resources cpu request is already set to %s, set cpu request to "
              + "a different value", cpuRequest));
    }

    // add/modify the server pod resources by patching the domain custom resource
    assertTrue(addServerPodResources(domainUid, domainNamespace, cpuLimit, cpuRequest),
        String.format("Failed to add server pod compute resources for domain %s in namespace %s",
            domainUid, domainNamespace));

    // verify the server pods are rolling restarted and back to ready state
    logger.info("Verifying rolling restart occurred for domain {0} in namespace {1}",
        domainUid, domainNamespace);
    assertTrue(assertDoesNotThrow(
        () -> verifyRollingRestartOccurred(podsWithTimeStamps, 1, domainNamespace),
        "More than one pod was restarted at same time"),
        String.format("Rolling restart failed for domain %s in namespace %s", domainUid, domainNamespace));

    // get the patched domain custom resource
    domain1 = assertDoesNotThrow(() -> getDomainCustomResource(domainUid, domainNamespace),
        String.format("getDomainCustomResource failed with ApiException when tried to get domain %s in namespace %s",
            domainUid, domainNamespace));

    assertNotNull(domain1, domain1 + " is null");
    assertNotNull(domain1.getSpec(), domain1 + "/spec is null");
    assertNotNull(domain1.getSpec().getServerPod(), domain1 + "/spec/serverPod is null");
    assertNotNull(domain1.getSpec().getServerPod().getResources(), domain1 + "/spec/serverPod/resources is null");

    // get new server pod compute resources limits
    limits = domain1.getSpec().getServerPod().getResources().getLimits();
    assertNotNull(limits, domain1 + "/spec/serverPod/resources/limits is null");

    // print out server pod compute resource limits
    logger.info("New value for server pod compute resource limits:");
    limits.forEach((key, value) -> logger.info(key + ": " + value.getNumber().toString()));

    // verify the server pod resources limits got updated
    logger.info("Checking that the server pod resources cpu limit was updated correctly");
    assertNotNull(limits.get("cpu"), domain1 + "/spec/serverPod/resources/limits/cpu is null");
    assertEquals(limits.get("cpu").getNumber().compareTo(cpuLimit), 0,
        String.format("server pod compute resource limits were not updated correctly, set cpu limit to %s, got %s",
            cpuLimit, limits.get("cpu").getNumber()));

    // get new server pod compute resources requests
    requests = domain1.getSpec().getServerPod().getResources().getRequests();
    assertNotNull(requests, domain1 + "/spec/serverPod/resources/requests is null");

    // print out server pod compute resource requests
    logger.info("New value for server pod compute resource requests:");
    requests.forEach((key, value) -> logger.info(key + ": " + value.getNumber()));

    // verify the server pod resources requests got updated
    logger.info("Checking that the server pod resources cpu request is updated correctly");
    assertNotNull(requests.get("cpu"), domain1 + "/spec/serverPod/resources/requests/cpu is null");
    assertEquals(requests.get("cpu").getNumber().compareTo(cpuRequest), 0,
        String.format("server pod compute resources requests was not updated correctly, set cpu request to %s, got %s",
            cpuRequest, requests.get("cpu").getNumber()));
  }

  /**
   * Create a model in image domain and verify the domain was created, server pods are ready and services exist.
   *
   * @param domainNamespace namespace in which the domain to be created
   * @return oracle.weblogic.domain.Domain object
   */
  private static Domain createAndVerifyMiiDomain(String domainNamespace) {

    String domainUid = "miidomain";

    // get the pre-built image created by IntegrationTestWatcher
    String miiImage = MII_BASIC_IMAGE_NAME + ":" + MII_BASIC_IMAGE_TAG;

    // docker login and push image to docker registry if necessary
    dockerLoginAndPushImageToRegistry(miiImage);

    // create docker registry secret to pull the image from registry
    logger.info("Creating docker registry secret in namespace {0}", domainNamespace);
    createDockerRegistrySecret(domainNamespace);

    // create secret for admin credentials
    logger.info("Creating secret for admin credentials");
    createSecretWithUsernamePassword(wlSecretName, domainNamespace, ADMIN_USERNAME_DEFAULT, ADMIN_PASSWORD_DEFAULT);

    // create encryption secret
    logger.info("Creating encryption secret");
    String encryptionSecretName = "encryptionsecret";
    createSecretWithUsernamePassword(encryptionSecretName, domainNamespace, "weblogicenc", "weblogicenc");

    // create the domain CR
    Domain domain = new Domain()
        .apiVersion(DOMAIN_API_VERSION)
        .kind("Domain")
        .metadata(new V1ObjectMeta()
            .name(domainUid)
            .namespace(domainNamespace))
        .spec(new DomainSpec()
            .domainUid(domainUid)
            .domainHomeSourceType("FromModel")
            .image(miiImage)
            .addImagePullSecretsItem(new V1LocalObjectReference()
                .name(REPO_SECRET_NAME))
            .webLogicCredentialsSecret(new V1SecretReference()
                .name(wlSecretName)
                .namespace(domainNamespace))
            .includeServerOutInPodLog(true)
            .serverStartPolicy("IF_NEEDED")
            .serverPod(new ServerPod()
                .addEnvItem(new V1EnvVar()
                    .name("JAVA_OPTIONS")
                    .value("-Dweblogic.StdoutDebugEnabled=false"))
                .addEnvItem(new V1EnvVar()
                    .name("USER_MEM_ARGS")
                    .value("-Djava.security.egd=file:/dev/./urandom "))
                .resources(new V1ResourceRequirements()
                    .limits(new HashMap<>())
                    .requests(new HashMap<>())))
            .adminServer(new AdminServer()
                .serverStartState("RUNNING"))
            .addClustersItem(new Cluster()
                .clusterName(clusterName)
                .replicas(replicaCount)
                .serverStartState("RUNNING"))
            .configuration(new Configuration()
                .model(new Model()
                    .domainType(WLS_DOMAIN_TYPE)
                    .runtimeEncryptionSecret(encryptionSecretName))));

    // create model in image domain
    logger.info("Creating model in image domain {0} in namespace {1} using docker image {2}",
        domainUid, domainNamespace, miiImage);
    createDomainAndVerify(domain, domainNamespace);

    String adminServerPodName = domainUid + "-" + ADMIN_SERVER_NAME_BASE;
    // check that admin server pod ready and service exists in the domain namespace
    checkPodReadyAndServiceExists(adminServerPodName, domainUid, domainNamespace);

    // check that managed server pods ready and service exists in the domain namespace
    for (int i = 1; i <= replicaCount; i++) {
      String managedServerPodName = domainUid + "-" + MANAGED_SERVER_NAME_BASE + i;
      // check that the managed server pod exists in the domain namespace
      checkPodReadyAndServiceExists(managedServerPodName, domainUid, domainNamespace);
    }

    return domain;
  }

  /**
   * Create domain in PV using WLST and verify the domain was created, server pods are ready and services exist.
   *
   * @param domainNamespace namespace in which the domain to be created
   * @return oracle.weblogic.domain.Domain object
   */
  private static Domain createAndVerifyDomainInPVUsingWlst(String domainNamespace) {
    String domainUid = "domaininpv" + "-" + domainNamespace.substring(3);
    String pvName = domainUid + "-pv";
    String pvcName = domainUid + "-pvc";

    if (isUseSecret) {
      // create pull secrets for WebLogic image
      createOCRRepoSecret(domainNamespace);
    }

    t3ChannelPort = getNextFreePort(32101, 32700);
    logger.info("t3ChannelPort for domain {0} is {1}", domainUid, t3ChannelPort);

    // create WebLogic credentials secret
    createSecretWithUsernamePassword(wlSecretName, domainNamespace, ADMIN_USERNAME_DEFAULT, ADMIN_PASSWORD_DEFAULT);

    // create persistent volume and persistent volume claims
    Path pvHostPath =
        get(PV_ROOT, ItParameterizedPodsRestart.class.getSimpleName(), domainUid + "-persistentVolume");

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
            .hostPath(new V1HostPathVolumeSource()
                .path(pvHostPath.toString())))
        .metadata(new V1ObjectMetaBuilder()
            .withName(pvName)
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

    // run a job to create a domain in PV using WLST
    runCreateDomainInPVJobUsingWlst(pvName, pvcName, domainUid, domainNamespace);

    // create the domain custom resource object
    logger.info("Creating domain custom resource");
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
            .image(wlsBaseImage)
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
                    .name(pvName))
                .resources(new V1ResourceRequirements()
                    .limits(new HashMap<>())
                    .requests(new HashMap<>())))
            .adminServer(new AdminServer()
                .serverStartState("RUNNING")
                .adminService(new AdminService()
                    .addChannelsItem(new Channel()
                        .channelName("T3Channel")
                        .nodePort(t3ChannelPort))))
            .addClustersItem(new Cluster()
                .clusterName(clusterName)
                .replicas(replicaCount)
                .serverStartState("RUNNING")));

    logger.info("Creating domain custom resource {0} in namespace {1}", domainUid, domainNamespace);
    createDomainAndVerify(domain, domainNamespace);

    // check that admin server pod is ready and service exists in domain namespace
    String adminServerPodName = domainUid + "-" + ADMIN_SERVER_NAME_BASE;
    checkPodReadyAndServiceExists(adminServerPodName, domainUid, domainNamespace);

    // check that managed server pods are ready and services exist in domain namespace
    for (int i = 1; i <= replicaCount; i++) {
      String managedServerPodName = domainUid + "-" + MANAGED_SERVER_NAME_BASE + i;
      checkPodReadyAndServiceExists(managedServerPodName, domainUid, domainNamespace);
    }

    return domain;
  }

  /**
   * Run a job to create a WebLogic domain in a persistent volume by doing the following.
   * Copies the WLST domain script to a temp location.
   * Creates a domain properties in the temp location.
   * Creates a configmap containing domain scripts and property files.
   * Runs a job to create domain in a persistent volume.
   *
   * @param pvName persistence volume in which the WebLogic domain home will be hosted
   * @param pvcName persistence volume claim for the WebLogic domain
   * @param domainUid the Uid of the domain to create
   * @param domainNamespace the namespace in which the domain will be created
   */
  private static void runCreateDomainInPVJobUsingWlst(String pvName,
                                                      String pvcName,
                                                      String domainUid,
                                                      String domainNamespace) {

    logger.info("Creating a staging location for domain creation scripts");
    Path pvTemp = get(RESULTS_ROOT, ItParameterizedPodsRestart.class.getSimpleName(), "domainCreateTempPV");
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
    String domainScriptConfigMapName = "create-domain-scripts-cm";
    createConfigMapFromFiles(domainScriptConfigMapName, domainScriptFiles, domainNamespace);

    logger.info("Running a Kubernetes job to create the domain");
    V1Job jobBody = new V1Job()
        .metadata(
            new V1ObjectMeta()
                .name("create-domain-onpv-job")
                .namespace(domainNamespace))
        .spec(new V1JobSpec()
            .backoffLimit(0) // try only once
            .template(new V1PodTemplateSpec()
                .spec(new V1PodSpec()
                    .restartPolicy("Never")
                    .addInitContainersItem(new V1Container()
                        .name("fix-pvc-owner")
                        .image(wlsBaseImage)
                        .addCommandItem("/bin/sh")
                        .addArgsItem("-c")
                        .addArgsItem("chown -R 1000:1000 /shared")
                        .addVolumeMountsItem(new V1VolumeMount()
                            .name(pvName)
                            .mountPath("/shared"))
                        .securityContext(new V1SecurityContext()
                            .runAsGroup(0L)
                            .runAsUser(0L)))
                    .addContainersItem(new V1Container()
                        .name("create-weblogic-domain-onpv-container")
                        .image(wlsBaseImage)
                        .addPortsItem(new V1ContainerPort()
                            .containerPort(7001))
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
                        .addArgsItem("/u01/weblogic/domain.properties"))
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
  private static void createDomainProperties(Path wlstPropertiesFile,
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
   * Create a WebLogic domain in image using WDT.
   *
   * @param domainNamespace namespace in which the domain to be created
   * @return oracle.weblogic.domain.Domain object
   */
  private static Domain createAndVerifyDomainInImageUsingWdt(String domainNamespace) {
    String domainUid = "domaininimage";

    // Create the repo secret to pull the image
    createDockerRegistrySecret(domainNamespace);

    // create secret for admin credentials
    logger.info("Create secret for admin credentials");
    createSecretWithUsernamePassword(wlSecretName, domainNamespace, ADMIN_USERNAME_DEFAULT, ADMIN_PASSWORD_DEFAULT);

    // create the domain CR
    Domain domain = new Domain()
        .apiVersion(DOMAIN_API_VERSION)
        .kind("Domain")
        .metadata(new V1ObjectMeta()
            .name(domainUid)
            .namespace(domainNamespace))
        .spec(new DomainSpec()
            .domainUid(domainUid)
            .domainHomeSourceType("Image")
            .image(WDT_BASIC_IMAGE_NAME + ":" + WDT_BASIC_IMAGE_TAG)
            .addImagePullSecretsItem(new V1LocalObjectReference()
                .name(REPO_SECRET_NAME))
            .webLogicCredentialsSecret(new V1SecretReference()
                .name(wlSecretName)
                .namespace(domainNamespace))
            .includeServerOutInPodLog(true)
            .serverStartPolicy("IF_NEEDED")
            .serverPod(new ServerPod()
                .addEnvItem(new V1EnvVar()
                    .name("JAVA_OPTIONS")
                    .value("-Dweblogic.StdoutDebugEnabled=false"))
                .addEnvItem(new V1EnvVar()
                    .name("USER_MEM_ARGS")
                    .value("-Djava.security.egd=file:/dev/./urandom "))
                .resources(new V1ResourceRequirements()
                    .limits(new HashMap<>())
                    .requests(new HashMap<>())))
            .adminServer(new AdminServer()
                .serverStartState("RUNNING"))
            .addClustersItem(new Cluster()
                .clusterName(clusterName)
                .replicas(replicaCount)
                .serverStartState("RUNNING"))
            .configuration(new Configuration()
                .model(new Model()
                    .domainType(WLS_DOMAIN_TYPE))
                .introspectorJobActiveDeadlineSeconds(300L)));

    createDomainAndVerify(domain, domainNamespace);

    // check admin server pod ready and service exists in the domain namespace
    String adminServerPodName = domainUid + "-" + ADMIN_SERVER_NAME_BASE;
    logger.info("Check for admin server pod {0} ready and service exists in namespace {1}",
        adminServerPodName, domainNamespace);
    checkPodReadyAndServiceExists(adminServerPodName, domainUid, domainNamespace);

    // check managed server pods are ready and service exists in the domain namespace
    String managedServerPrefix = domainUid + "-" + MANAGED_SERVER_NAME_BASE;
    for (int i = 1; i <= replicaCount; i++) {
      logger.info("Wait for managed server pod {0} to be ready and services exist in namespace {1}",
          managedServerPrefix + i, domainNamespace);
      checkPodReadyAndServiceExists(managedServerPrefix + i, domainUid, domainNamespace);
    }

    return domain;
  }

  /**
   * Add server pod compute resources.
   *
   * @param cpuLimit cpu limit to be added to domain spec serverPod resources limits
   * @param cpuRequest cpu request to be added to domain spec serverPod resources requests
   * @return true if patching domain custom resource is successful, false otherwise
   */
  private boolean addServerPodResources(String domainUid,
                                        String domainNamespace,
                                        BigDecimal cpuLimit,
                                        BigDecimal cpuRequest) {
    // construct the patch string for adding server pod resources
    StringBuffer patchStr = new StringBuffer("[{")
        .append("\"op\": \"add\", ")
        .append("\"path\": \"/spec/serverPod/resources/limits/cpu\", ")
        .append("\"value\": \"")
        .append(cpuLimit)
        .append("\"}, {")
        .append("\"op\": \"add\", ")
        .append("\"path\": \"/spec/serverPod/resources/requests/cpu\", ")
        .append("\"value\": \"")
        .append(cpuRequest)
        .append("\"}]");

    logger.info("Adding server pod compute resources for domain {0} in namespace {1} using patch string: {2}",
        domainUid, domainNamespace, patchStr.toString());

    V1Patch patch = new V1Patch(new String(patchStr));

    return patchDomainCustomResource(domainUid, domainNamespace, patch, V1Patch.PATCH_FORMAT_JSON_PATCH);
  }
}
