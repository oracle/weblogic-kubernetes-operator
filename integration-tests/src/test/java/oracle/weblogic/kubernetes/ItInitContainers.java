// Copyright (c) 2020, 2021, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.weblogic.kubernetes;

import java.util.ArrayList;
import java.util.List;

import io.kubernetes.client.openapi.models.V1Container;
import io.kubernetes.client.openapi.models.V1EnvVar;
import io.kubernetes.client.openapi.models.V1LocalObjectReference;
import io.kubernetes.client.openapi.models.V1ObjectMeta;
import io.kubernetes.client.openapi.models.V1SecretReference;
import oracle.weblogic.domain.AdminServer;
import oracle.weblogic.domain.AdminService;
import oracle.weblogic.domain.Channel;
import oracle.weblogic.domain.Cluster;
import oracle.weblogic.domain.Configuration;
import oracle.weblogic.domain.Domain;
import oracle.weblogic.domain.DomainSpec;
import oracle.weblogic.domain.ManagedServer;
import oracle.weblogic.domain.Model;
import oracle.weblogic.domain.ServerPod;
import oracle.weblogic.kubernetes.actions.impl.primitive.HelmParams;
import oracle.weblogic.kubernetes.annotations.IntegrationTest;
import oracle.weblogic.kubernetes.annotations.Namespaces;
import oracle.weblogic.kubernetes.logging.LoggingFacade;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static oracle.weblogic.kubernetes.TestConstants.ADMIN_SERVER_NAME_BASE;
import static oracle.weblogic.kubernetes.TestConstants.DOMAIN_API_VERSION;
import static oracle.weblogic.kubernetes.TestConstants.MANAGED_SERVER_NAME_BASE;
import static oracle.weblogic.kubernetes.TestConstants.MII_BASIC_IMAGE_NAME;
import static oracle.weblogic.kubernetes.TestConstants.MII_BASIC_IMAGE_TAG;
import static oracle.weblogic.kubernetes.TestConstants.OCIR_SECRET_NAME;
import static oracle.weblogic.kubernetes.TestConstants.WLS_DOMAIN_TYPE;
import static oracle.weblogic.kubernetes.actions.TestActions.getPodLog;
import static oracle.weblogic.kubernetes.actions.TestActions.uninstallOperator;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.checkPodInitializing;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.checkPodReadyAndServiceExists;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.createDomainAndVerify;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.createOcirRepoSecret;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.createSecretWithUsernamePassword;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.dockerLoginAndPushImageToRegistry;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.installAndVerifyOperator;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.setPodAntiAffinity;
import static oracle.weblogic.kubernetes.utils.ThreadSafeLogger.getLogger;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;



/**
 * Simple JUnit test file used for testing server's pod init containers feature.
 */
@DisplayName("Test server's pod init container feature")
@IntegrationTest
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
        domain3Namespace, domain4Namespace);

  }

  private static void createSecrets(String domainNamespace) {
    //create secrets for domain

    // docker login and push image to docker registry if necessary
    dockerLoginAndPushImageToRegistry(miiImage);

    // create docker registry secret to pull the image from registry
    // this secret is used only for non-kind cluster
    logger.info("Creating docker registry secret in namespace {0}", domainNamespace);
    createOcirRepoSecret(domainNamespace);

    // create secret for admin credentials
    logger.info("Creating secret for admin credentials");

    createSecretWithUsernamePassword(adminSecretName, domainNamespace, "weblogic", "welcome1");

    // create encryption secret
    logger.info("Creating encryption secret");

    createSecretWithUsernamePassword(encryptionSecretName, domainNamespace, "weblogicenc", "weblogicenc");
  }


  @AfterAll
  public void tearDownAll() {
    //delete operator
    uninstallOperator(opHelmParams);
  }

  /**
   * Add initContainers at domain spec level and verify the admin server pod executes initContainer command.
   * Test fails if domain crd can't add the initContainers or
   * WebLogic server pods don't go through initialization and ready state.
   */
  @Test
  @DisplayName("Add initContainers at domain spec level and verify the server pods execute initContainer command "
      + " and starts the server pod")
  public void testDomainInitContainer() {
    logger.info("Installing and verifying domain");
    assertTrue(createVerifyDomain(domain1Namespace, domain1Uid, "spec"),
        "can't start or verify domain in namespace " + domain1Namespace);

    //check if init container got executed in the server pods
    assertTrue(assertDoesNotThrow(() -> getPodLog(domain1Uid + "-admin-server", domain1Namespace,"busybox")
            .contains("Hi from Domain"),
        "failed to init busybox container command for admin server"));
    assertTrue(assertDoesNotThrow(() -> getPodLog(domain1Uid + "-managed-server1", domain1Namespace,"busybox")
            .contains("Hi from Domain"),
        "failed to init busybox container command for managed server1"));
    assertTrue(assertDoesNotThrow(() -> getPodLog(domain1Uid + "-managed-server2", domain1Namespace,"busybox")
            .contains("Hi from Domain"),
        "failed to init busybox container command for managed server2"));

  }

  /**
   * Add initContainers to adminServer and verify the admin server pod executes initContainer command
   * and starts the admin server pod.
   * Test fails if domain crd can't add the initContainers or
   * Weblogic Admin server pod doesn't go through initialization and ready state.
   */
  @Test
  @DisplayName("Add initContainers to adminServer and verify the admin server pod executes initContainer command ")
  public void testAdminServerInitContainer() {
    assertTrue(createVerifyDomain(domain2Namespace, domain2Uid, "adminServer"),
        "can't start or verify domain in namespace " + domain2Namespace);

    //check if init container got executed for admin server pod
    assertTrue(assertDoesNotThrow(() -> getPodLog(domain2Uid + "-admin-server", domain2Namespace,"busybox")
            .contains("Hi from AdminServer"),
        "failed to init busybox container command for admin server"));
  }

  /**
   * Add initContainers to adminServer and verify the managed server pods in cluster execute initContainer command
   * before starting the admin server pod.
   * Test fails if if domain crd can't add the initContainers or
   * Weblogic server pods in the cluster don't go through initialization and ready state.
   */
  @Test
  @DisplayName("Add initContainers to cluster1 and verify all managed server pods go through Init state ")
  public void testClusterInitContainer() {
    assertTrue(createVerifyDomain(domain3Namespace, domain3Uid, "clusters"),
        "can't start or verify domain in namespace " + domain3Namespace);

    //check if init container got executed
    assertTrue(assertDoesNotThrow(() -> getPodLog(domain3Uid + "-managed-server1",
        domain3Namespace,"busybox").contains("Hi from Cluster"),
        "failed to init busybox container command for cluster's managed-server1"));
    assertTrue(assertDoesNotThrow(() -> getPodLog(domain3Uid + "-managed-server2",
        domain3Namespace,"busybox").contains("Hi from Cluster"),
        "failed to init busybox container command for cluster's managed-server2"));
  }

  /**
   * Add initContainers to managed-server1 and verify managed server pod executes initContainer command
   * before starting the managed server1 pod.
   * Test fails if domain crd can't add the initContainers or
   * WebLogic managed server pod doesn't go through initialization and ready state.
   */
  @Test
  @DisplayName("Add initContainers to managed-server1 and verify the pod goes through Init state ")
  public void testMsInitContainer() {
    assertTrue(createVerifyDomain(domain4Namespace, domain4Uid, "managedServers"),
        "can't start or verify domain in namespace " + domain4Namespace);

    //check if init container got executed
    assertTrue(assertDoesNotThrow(() -> getPodLog(domain4Uid + "-managed-server1", domain4Namespace,"busybox")
            .contains("Hi from managed-server1"),
        "failed to init busybox container command for managed server1"));
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


    // construct a list of oracle.weblogic.domain.Cluster objects to be used in the domain custom resource
    List<Cluster> clusters = new ArrayList<>();
    clusters.add(new Cluster()
        .clusterName(clusterName)
        .replicas(replicaCount)
        .serverStartState("RUNNING"));


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
                .name(OCIR_SECRET_NAME))
            .webLogicCredentialsSecret(new V1SecretReference()
                .name(adminSecretName)
                .namespace(domainNamespace))
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
            .clusters(clusters)
            .configuration(new Configuration()
                .model(new Model()
                    .domainType(WLS_DOMAIN_TYPE)
                    .runtimeEncryptionSecret(encryptionSecretName))));


    switch (testCaseName) {
      case "spec":
        domain.getSpec().getServerPod().addInitContainersItem(new V1Container()
            .addCommandItem("echo").addArgsItem("\"Hi from Domain\"")
            .name("busybox")
            .imagePullPolicy("IfNotPresent")
            .image("busybox"));
        setPodAntiAffinity(domain);
        break;
      case "adminServer":
        domain.getSpec().getAdminServer().serverPod(new ServerPod()
            .addInitContainersItem(new V1Container()
            .addCommandItem("echo").addArgsItem("\"Hi from AdminServer\"")
            .name("busybox")
            .imagePullPolicy("IfNotPresent")
            .image("busybox")));
        setPodAntiAffinity(domain);
        break;
      case "clusters":
        clusters = domain.getSpec().getClusters();
        assertNotNull(clusters, "Can't find clusters in CRD ");
        Cluster mycluster = clusters.stream()
            .filter(cluster -> clusterName.equals(cluster.getClusterName())).findAny()
            .orElse(null);
        assertNotNull(mycluster, "Can't find cluster " + clusterName);
        setPodAntiAffinity(domain);
        mycluster.getServerPod()
                .addInitContainersItem(new V1Container()
                    .addCommandItem("echo").addArgsItem("\"Hi from Cluster \"")
                    .name("busybox")
                    .imagePullPolicy("IfNotPresent")
                    .image("busybox"));
        break;
      case "managedServers":
        domain.getSpec().addManagedServersItem(new ManagedServer()
            .serverName("managed-server1")
            .serverPod(new ServerPod()
                .addInitContainersItem(new V1Container()
                    .addCommandItem("echo").addArgsItem("\"Hi from managed-server1\"")
                    .name("busybox")
                    .imagePullPolicy("IfNotPresent")
                    .image("busybox"))));
        setPodAntiAffinity(domain);
        break;
      default:
        logger.info("no match for provided case {0}", testCaseName);
    }

    // create model in image domain
    logger.info("Creating model in image domain {0} in namespace {1} using docker image {2}",
        domainUid, domainNamespace, miiImage);
    createDomainAndVerify(domain, domainNamespace);
    String adminServerPodName = domainUid + adminServerPrefix;
    //check if pod in init state
    checkPodInitializing(adminServerPodName,domainUid, domainNamespace);

    // check that admin service exists and pod is ready in the domain namespace
    logger.info("Checking that admin service {0} exists in namespace {1}",
        adminServerPodName, domainNamespace);
    checkPodReadyAndServiceExists(adminServerPodName, domainUid, domainNamespace);

    // check for managed server pods existence in the domain namespace
    for (int i = 1; i <= replicaCount; i++) {
      String managedServerPodName = domainUid + managedServerPrefix + i;
      //check if pod in init state
      checkPodInitializing(managedServerPodName,domainUid, domainNamespace);
      // check that the managed server service exists and pod is ready in the domain namespace
      logger.info("Checking that managed server service {0} exists in namespace {1}",
          managedServerPodName, domainNamespace);
      checkPodReadyAndServiceExists(managedServerPodName, domainUid, domainNamespace);

    }
  }
}
