// Copyright (c) 2020, 2022, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.weblogic.kubernetes;

import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;

import io.kubernetes.client.openapi.models.V1Container;
import io.kubernetes.client.openapi.models.V1EnvVar;
import io.kubernetes.client.openapi.models.V1LocalObjectReference;
import io.kubernetes.client.openapi.models.V1ObjectMeta;
import io.kubernetes.client.util.Yaml;
import oracle.weblogic.domain.AdminServer;
import oracle.weblogic.domain.AdminService;
import oracle.weblogic.domain.Channel;
import oracle.weblogic.domain.ClusterResource;
import oracle.weblogic.domain.ClusterSpec;
import oracle.weblogic.domain.Configuration;
import oracle.weblogic.domain.DomainResource;
import oracle.weblogic.domain.DomainSpec;
import oracle.weblogic.domain.ManagedServer;
import oracle.weblogic.domain.Model;
import oracle.weblogic.domain.ServerPod;
import oracle.weblogic.kubernetes.actions.impl.OperatorParams;
import oracle.weblogic.kubernetes.actions.impl.primitive.HelmParams;
import oracle.weblogic.kubernetes.annotations.IntegrationTest;
import oracle.weblogic.kubernetes.annotations.Namespaces;
import oracle.weblogic.kubernetes.logging.LoggingFacade;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static oracle.weblogic.kubernetes.TestConstants.ADMIN_PASSWORD_DEFAULT;
import static oracle.weblogic.kubernetes.TestConstants.ADMIN_SERVER_NAME_BASE;
import static oracle.weblogic.kubernetes.TestConstants.ADMIN_USERNAME_DEFAULT;
import static oracle.weblogic.kubernetes.TestConstants.BUSYBOX_IMAGE;
import static oracle.weblogic.kubernetes.TestConstants.BUSYBOX_TAG;
import static oracle.weblogic.kubernetes.TestConstants.DOMAIN_API_VERSION;
import static oracle.weblogic.kubernetes.TestConstants.IMAGE_PULL_POLICY;
import static oracle.weblogic.kubernetes.TestConstants.MANAGED_SERVER_NAME_BASE;
import static oracle.weblogic.kubernetes.TestConstants.MII_BASIC_IMAGE_NAME;
import static oracle.weblogic.kubernetes.TestConstants.MII_BASIC_IMAGE_TAG;
import static oracle.weblogic.kubernetes.TestConstants.TEST_IMAGES_REPO_SECRET_NAME;
import static oracle.weblogic.kubernetes.TestConstants.WLS_DOMAIN_TYPE;
import static oracle.weblogic.kubernetes.actions.TestActions.getPodLog;
import static oracle.weblogic.kubernetes.actions.TestActions.patchDomainResourceWithNewIntrospectVersion;
import static oracle.weblogic.kubernetes.utils.ClusterUtils.createClusterAndVerify;
import static oracle.weblogic.kubernetes.utils.ClusterUtils.createClusterResource;
import static oracle.weblogic.kubernetes.utils.CommonMiiTestUtils.verifyPodsNotRolled;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.checkPodReadyAndServiceExists;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.getNextFreePort;
import static oracle.weblogic.kubernetes.utils.DomainUtils.createDomainAndVerify;
import static oracle.weblogic.kubernetes.utils.ImageUtils.createTestRepoSecret;
import static oracle.weblogic.kubernetes.utils.ImageUtils.imageRepoLoginAndPushImageToRegistry;
import static oracle.weblogic.kubernetes.utils.OperatorUtils.installAndVerifyOperator;
import static oracle.weblogic.kubernetes.utils.OperatorUtils.upgradeAndVerifyOperator;
import static oracle.weblogic.kubernetes.utils.PodUtils.checkPodInitialized;
import static oracle.weblogic.kubernetes.utils.PodUtils.checkPodReady;
import static oracle.weblogic.kubernetes.utils.PodUtils.getPodCreationTime;
import static oracle.weblogic.kubernetes.utils.PodUtils.setPodAntiAffinity;
import static oracle.weblogic.kubernetes.utils.SecretUtils.createSecretWithUsernamePassword;
import static oracle.weblogic.kubernetes.utils.ThreadSafeLogger.getLogger;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;



/**
 * Simple JUnit test file used for testing server's pod init containers feature.
 */
@DisplayName("Test server's pod init container feature")
@IntegrationTest
@Tag("olcne")
@Tag("oke-parallel")
@Tag("kind-parallel")
@Tag("okd-wls-mrg")
class ItInitContainers {

  private static String opNamespace = null;
  private static HelmParams opHelmParams = null;
  private static String domain1Namespace = null;
  private static String domain2Namespace = null;
  private static String domain3Namespace = null;
  private static String domain4Namespace = null;

  // domain constants
  private final String domain1Uid = "initcontainersdomain1";
  private final String domain2Uid = "initcontainerdomain2";
  private final String domain3Uid = "initcontainerdomain3";
  private final String domain4Uid = "initcontainerdomain4";
  private final String clusterName = "cluster-1";
  private final int replicaCount = 2;
  private final String adminServerPrefix = "-" + ADMIN_SERVER_NAME_BASE;
  private final String managedServerPrefix = "-" + MANAGED_SERVER_NAME_BASE;
  private static String adminSecretName = "weblogic-credentials";
  private static String encryptionSecretName = "encryptionsecret";
  private static String miiImage = MII_BASIC_IMAGE_NAME + ":" + MII_BASIC_IMAGE_TAG;

  private static LoggingFacade logger = null;


  /**
   * Get namespaces for operator, domains.
   *
   * @param namespaces list of namespaces created by the IntegrationTestWatcher by the
   *                   JUnit engine parameter resolution mechanism
   */
  @BeforeAll
  public static void initAll(@Namespaces(5) List<String> namespaces) {
    logger = getLogger();
    // get a unique operator namespace
    logger.info("Getting a unique namespace for operator");
    assertNotNull(namespaces.get(0), "Namespace list is null");
    opNamespace = namespaces.get(0);

    // get a unique domain1 namespace
    logger.info("Getting a unique namespace for WebLogic domain1");
    assertNotNull(namespaces.get(1), "Namespace list is null");
    domain1Namespace = namespaces.get(1);

    // get a unique domain2 namespace
    logger.info("Getting a unique namespace for WebLogic domain2");
    assertNotNull(namespaces.get(2), "Namespace list is null");
    domain2Namespace = namespaces.get(2);

    // get a unique domain3 namespace
    logger.info("Getting a unique namespace for WebLogic domain3");
    assertNotNull(namespaces.get(3), "Namespace list is null");
    domain3Namespace = namespaces.get(3);

    // get a unique domain4 namespace
    logger.info("Getting a unique namespace for WebLogic domain4");
    assertNotNull(namespaces.get(4), "Namespace list is null");
    domain4Namespace = namespaces.get(4);

    // install and verify operator
    logger.info("Installing and verifying operator");

    opHelmParams = installAndVerifyOperator(opNamespace,
        domain1Namespace, domain2Namespace,
        domain3Namespace, domain4Namespace).getHelmParams();

    // operator chart values to override
    OperatorParams opParams = new OperatorParams()
        .helmParams(opHelmParams)
        .javaLoggingLevel(("FINE"));
    assertTrue(upgradeAndVerifyOperator(opNamespace, opParams),
        "Failed to upgrade operator to FINE  logging leve");
  }

  private static void createSecrets(String domainNamespace) {
    //create secrets for domain

    // repo login and push image to registry if necessary
    imageRepoLoginAndPushImageToRegistry(miiImage);

    // create registry secret to pull the image from registry
    // this secret is used only for non-kind cluster
    logger.info("Creating registry secret in namespace {0}", domainNamespace);
    createTestRepoSecret(domainNamespace);

    // create secret for admin credentials
    logger.info("Creating secret for admin credentials");

    createSecretWithUsernamePassword(adminSecretName, domainNamespace, ADMIN_USERNAME_DEFAULT, ADMIN_PASSWORD_DEFAULT);

    // create encryption secret
    logger.info("Creating encryption secret");

    createSecretWithUsernamePassword(encryptionSecretName, domainNamespace, "weblogicenc", "weblogicenc");
  }


  /**
   * Add initContainers at domain spec level and verify the admin server pod executes initContainer command.
   * Test fails if domain crd can't add the initContainers or
   * WebLogic server pods don't go through initialization and ready state.
   * The following introspect version usecase was added based on issue
   * reported by OFSS team. With initContainer configured, the WebLogic server
   * pod should not roll with modified introspect version without any update to
   * domain resource.
   * Update the introspect version with out any change to domain resource
   * Make sure no WebLogic server pod get rolled.
   */
  @Test
  @DisplayName("Add initContainers at domain spec level and verify the server pods execute initContainer command "
      + " and starts the server pod")
  void testDomainInitContainer() {
    logger.info("Installing and verifying domain");
    assertTrue(createVerifyDomain(domain1Namespace, domain1Uid, "spec"),
        "can't start or verify domain in namespace " + domain1Namespace);

    //check if init container got executed in the server pods
    assertTrue(checkPodLogContainMsg(domain1Uid + "-admin-server",domain1Namespace,
        domain1Uid,"Hi from Domain"),
         "failed to init init-container container command for admin server");
    assertTrue(checkPodLogContainMsg(domain1Uid + "-managed-server1",domain1Namespace,
        domain1Uid, "Hi from Domain"),
        "failed to init init-container container command for managed server1");
    assertTrue(checkPodLogContainMsg(domain1Uid + "-managed-server2",domain1Namespace,
        domain1Uid,"Hi from Domain"),
        "failed to init init-container container command for managed server2");

    // get the pod creation time stamps
    LinkedHashMap<String, OffsetDateTime> pods = new LinkedHashMap<>();
    // get the creation time of the admin server pod before patching
    OffsetDateTime adminPodCreationTime = getPodCreationTime(domain1Namespace,domain1Uid + "-admin-server");
    String adminServerPodName = domain1Uid + adminServerPrefix;
    pods.put(adminServerPodName, adminPodCreationTime);
    // get the creation time of the managed server pods before patching
    String managedServerNameBase = "managed-server";
    String managedServerPodNamePrefix = domain1Uid + "-managed-server";
    for (int i = 1; i <= replicaCount; i++) {
      pods.put(managedServerPodNamePrefix + i,
          getPodCreationTime(domain1Namespace, managedServerPodNamePrefix + i));
    }
    patchDomainResourceWithNewIntrospectVersion(domain1Uid,domain1Namespace);
    //verify the pods are not restarted in any introspectVersion update
    verifyPodsNotRolled(domain1Namespace, pods);

  }

  private boolean checkPodLogContainMsg(String podName, String podNamespace, String domainUid, String msg) {
    String podLog = null;
    try {
      // check that pod is ready in the domain namespace
      logger.info("Checking that pod {0} exists in namespace {1}",
          podName, podNamespace);
      checkPodReady(podName, domainUid, podNamespace);
      podLog = getPodLog(podName, podNamespace,"init-container");
    } catch (Exception ex) {
      logger.info("Caught unexpected exception while calling getPodLog for pod "
          + podName
          + ex.getMessage()
          + Arrays.toString(ex.getStackTrace()));
      return false;
    }
    if (podLog != null) {
      logger.info("PodLog " + podLog);
      return podLog.contains(msg);
    } else {
      logger.info("getPodLog returns null , can't retrieve pod's log for " + podName);
      return false;
    }
  }

  /**
   * Add initContainers to adminServer and verify the admin server pod executes initContainer command
   * and starts the admin server pod.
   * Test fails if domain crd can't add the initContainers or
   * Weblogic Admin server pod doesn't go through initialization and ready state.
   */
  @Test
  @DisplayName("Add initContainers to adminServer and verify the admin server pod executes initContainer command ")
  void testAdminServerInitContainer() {
    assertTrue(createVerifyDomain(domain2Namespace, domain2Uid, "adminServer"),
        "can't start or verify domain in namespace " + domain2Namespace);

    //check if init container got executed for admin server pod
    assertTrue(assertDoesNotThrow(() -> getPodLog(domain2Uid + "-admin-server", domain2Namespace,"init-container")
            .contains("Hi from AdminServer"),
        "failed to init init-container container command for admin server"));
  }

  /**
   * Add initContainers to adminServer and verify the managed server pods in cluster execute initContainer command
   * before starting the admin server pod.
   * Test fails if if domain crd can't add the initContainers or
   * Weblogic server pods in the cluster don't go through initialization and ready state.
   */
  @Test
  @DisplayName("Add initContainers to cluster1 and verify all managed server pods go through Init state ")
  @Tag("gate")
  @Tag("crio")
  void testClusterInitContainer() {
    assertTrue(createVerifyDomain(domain3Namespace, domain3Uid, "clusters"),
        "can't start or verify domain in namespace " + domain3Namespace);

    //check if init container got executed
    assertTrue(assertDoesNotThrow(() -> getPodLog(domain3Uid + "-managed-server1",
        domain3Namespace, "init-container").contains("Hi from Cluster"),
        "failed to init init-container container command for cluster's managed-server1"));
    assertTrue(assertDoesNotThrow(() -> getPodLog(domain3Uid + "-managed-server2",
        domain3Namespace, "init-container").contains("Hi from Cluster"),
        "failed to init init-container container command for cluster's managed-server2"));
  }

  /**
   * Add initContainers to managed-server1 and verify managed server pod executes initContainer command
   * before starting the managed server1 pod.
   * Test fails if domain crd can't add the initContainers or
   * WebLogic managed server pod doesn't go through initialization and ready state.
   */
  @Test
  @DisplayName("Add initContainers to managed-server1 and verify the pod goes through Init state ")
  void testMsInitContainer() {
    assertTrue(createVerifyDomain(domain4Namespace, domain4Uid, "managedServers"),
        "can't start or verify domain in namespace " + domain4Namespace);

    //check if init container got executed
    assertTrue(assertDoesNotThrow(() -> getPodLog(domain4Uid + "-managed-server1", domain4Namespace,"init-container")
            .contains("Hi from managed-server1"),
        "failed to init init-container container command for managed server1"));
  }

  private boolean createVerifyDomain(String domainNamespace, String domainUid, String parentNodeName) {

    createSecrets(domainNamespace);
    // create and verify the domain
    logger.info("Creating and verifying model in image domain");

    createAndVerifyMiiDomain(domainNamespace, domainUid, parentNodeName);
    return true;
  }

  /**
   * Create a model in image domain, add initContainers section to domain crd and verify the domain pods are ready.
   */
  private void createAndVerifyMiiDomain(String domainNamespace, String domainUid, String testCaseName) {

    String clusterResName = domainUid + "-" + clusterName;

    // create the domain CR
    DomainResource domain = new DomainResource()
        .apiVersion(DOMAIN_API_VERSION)
        .kind("Domain")
        .metadata(new V1ObjectMeta()
            .name(domainUid)
            .namespace(domainNamespace))
        .spec(new DomainSpec()
            .domainUid(domainUid)
            .domainHomeSourceType("FromModel")
            .image(miiImage)
            .imagePullPolicy(IMAGE_PULL_POLICY)
            .addImagePullSecretsItem(new V1LocalObjectReference()
                .name(TEST_IMAGES_REPO_SECRET_NAME))
            .webLogicCredentialsSecret(new V1LocalObjectReference()
                .name(adminSecretName))
            .includeServerOutInPodLog(true)
            .serverStartPolicy("IfNeeded")
            .serverPod(new ServerPod()
                .addEnvItem(new V1EnvVar()
                    .name("JAVA_OPTIONS")
                    .value("-Dweblogic.StdoutDebugEnabled=false"))
                .addEnvItem(new V1EnvVar()
                    .name("USER_MEM_ARGS")
                    .value("-Djava.security.egd=file:/dev/./urandom ")))
            .adminServer(new AdminServer()
                .adminService(new AdminService()
                    .addChannelsItem(new Channel()
                        .channelName("default")
                        .nodePort(getNextFreePort()))))
            .configuration(new Configuration()
                .model(new Model()
                    .domainType(WLS_DOMAIN_TYPE)
                    .runtimeEncryptionSecret(encryptionSecretName))));

    if (!testCaseName.equals("clusters")) {
      // create cluster object
      ClusterResource cluster = createClusterResource(clusterResName,
          clusterName, domainNamespace, replicaCount);

      logger.info("Creating cluster resource {0} in namespace {1}", clusterResName, domainNamespace);
      createClusterAndVerify(cluster);
      // set cluster references
      domain.getSpec().withCluster(new V1LocalObjectReference().name(clusterResName));
    }

    switch (testCaseName) {
      case "spec":
        domain.getSpec().getServerPod().addInitContainersItem(new V1Container()
            .addCommandItem("echo").addArgsItem("\"Hi from Domain\"")
            .name("init-container")
            .imagePullPolicy(IMAGE_PULL_POLICY)
            .image(BUSYBOX_IMAGE + ":" + BUSYBOX_TAG).addEnvItem(new V1EnvVar()
                                    .name("DOMAIN_NAME")
                                    .value("xyz")));
        setPodAntiAffinity(domain);
        break;
      case "adminServer":
        domain.getSpec().getAdminServer().serverPod(new ServerPod()
            .addInitContainersItem(new V1Container()
            .addCommandItem("echo").addArgsItem("\"Hi from AdminServer\"")
            .name("init-container")
            .imagePullPolicy(IMAGE_PULL_POLICY)
            .image(BUSYBOX_IMAGE + ":" + BUSYBOX_TAG)));
        setPodAntiAffinity(domain);
        break;
      case "clusters":
        ClusterSpec clusterSpec = new ClusterSpec()
            .withClusterName(clusterName)
            .replicas(replicaCount)
            .serverPod(new ServerPod()
                .addInitContainersItem(new V1Container()
                    .addCommandItem("echo").addArgsItem("\"Hi from Cluster \"")
                    .name("init-container")
                    .imagePullPolicy(IMAGE_PULL_POLICY)
                    .image(BUSYBOX_IMAGE + ":" + BUSYBOX_TAG)));
        logger.info(Yaml.dump(clusterSpec));
        ClusterResource cluster = createClusterResource(clusterName, domainNamespace, clusterSpec);
        logger.info("Creating cluster {0} in namespace {1}", clusterName, domainNamespace);
        createClusterAndVerify(cluster);
        // set cluster references
        domain.getSpec().withCluster(new V1LocalObjectReference().name(clusterName));
        break;
      case "managedServers":
        domain.getSpec().addManagedServersItem(new ManagedServer()
            .serverName("managed-server1")
            .serverPod(new ServerPod()
                .addInitContainersItem(new V1Container()
                    .addCommandItem("echo").addArgsItem("\"Hi from managed-server1\"")
                    .name("init-container")
                    .imagePullPolicy(IMAGE_PULL_POLICY)
                    .image(BUSYBOX_IMAGE + ":" + BUSYBOX_TAG))));
        setPodAntiAffinity(domain);
        break;
      default:
        logger.info("no match for provided case {0}", testCaseName);
    }

    // create model in image domain
    logger.info("Creating model in image domain {0} in namespace {1} using image {2}",
        domainUid, domainNamespace, miiImage);
    createDomainAndVerify(domain, domainNamespace);
    String adminServerPodName = domainUid + adminServerPrefix;
    //check if pod in init state
    checkPodInitialized(adminServerPodName,domainUid, domainNamespace);

    // check that admin service exists and pod is ready in the domain namespace
    logger.info("Checking that admin service {0} exists in namespace {1}",
        adminServerPodName, domainNamespace);
    checkPodReadyAndServiceExists(adminServerPodName, domainUid, domainNamespace);

    // check for managed server pods existence in the domain namespace
    for (int i = 1; i <= replicaCount; i++) {
      String managedServerPodName = domainUid + managedServerPrefix + i;
      //check if pod in init state
      checkPodInitialized(managedServerPodName,domainUid, domainNamespace);
      // check that the managed server service exists and pod is ready in the domain namespace
      logger.info("Checking that managed server service {0} exists in namespace {1}",
          managedServerPodName, domainNamespace);
      checkPodReadyAndServiceExists(managedServerPodName, domainUid, domainNamespace);

    }
  }
}
