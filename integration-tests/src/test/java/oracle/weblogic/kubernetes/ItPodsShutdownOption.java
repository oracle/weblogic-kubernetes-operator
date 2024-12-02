// Copyright (c) 2020, 2023, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.weblogic.kubernetes;

import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import io.kubernetes.client.openapi.models.V1ConfigMap;
import io.kubernetes.client.openapi.models.V1EnvVar;
import io.kubernetes.client.openapi.models.V1LocalObjectReference;
import io.kubernetes.client.openapi.models.V1ObjectMeta;
import oracle.weblogic.domain.AdminServer;
import oracle.weblogic.domain.ClusterResource;
import oracle.weblogic.domain.Configuration;
import oracle.weblogic.domain.DomainResource;
import oracle.weblogic.domain.DomainSpec;
import oracle.weblogic.domain.ManagedServer;
import oracle.weblogic.domain.Model;
import oracle.weblogic.domain.ServerPod;
import oracle.weblogic.domain.Shutdown;
import oracle.weblogic.kubernetes.actions.TestActions;
import oracle.weblogic.kubernetes.actions.impl.primitive.HelmParams;
import oracle.weblogic.kubernetes.annotations.IntegrationTest;
import oracle.weblogic.kubernetes.annotations.Namespaces;
import oracle.weblogic.kubernetes.logging.LoggingFacade;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static java.util.concurrent.TimeUnit.MINUTES;
import static java.util.concurrent.TimeUnit.SECONDS;
import static oracle.weblogic.kubernetes.TestConstants.ADMIN_PASSWORD_DEFAULT;
import static oracle.weblogic.kubernetes.TestConstants.ADMIN_SERVER_NAME_BASE;
import static oracle.weblogic.kubernetes.TestConstants.ADMIN_USERNAME_DEFAULT;
import static oracle.weblogic.kubernetes.TestConstants.DOMAIN_API_VERSION;
import static oracle.weblogic.kubernetes.TestConstants.IMAGE_PULL_POLICY;
import static oracle.weblogic.kubernetes.TestConstants.MANAGED_SERVER_NAME_BASE;
import static oracle.weblogic.kubernetes.TestConstants.MII_BASIC_IMAGE_NAME;
import static oracle.weblogic.kubernetes.TestConstants.MII_BASIC_IMAGE_TAG;
import static oracle.weblogic.kubernetes.TestConstants.OPERATOR_CHART_DIR;
import static oracle.weblogic.kubernetes.TestConstants.OPERATOR_RELEASE_NAME;
import static oracle.weblogic.kubernetes.TestConstants.TEST_IMAGES_REPO_SECRET_NAME;
import static oracle.weblogic.kubernetes.TestConstants.WLS_DOMAIN_TYPE;
import static oracle.weblogic.kubernetes.actions.TestActions.createConfigMap;
import static oracle.weblogic.kubernetes.actions.TestActions.deletePod;
import static oracle.weblogic.kubernetes.actions.TestActions.getOperatorPodName;
import static oracle.weblogic.kubernetes.actions.TestActions.getPodCreationTimestamp;
import static oracle.weblogic.kubernetes.actions.TestActions.scaleCluster;
import static oracle.weblogic.kubernetes.utils.ClusterUtils.createClusterAndVerify;
import static oracle.weblogic.kubernetes.utils.ClusterUtils.createClusterResource;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.checkPodReadyAndServiceExists;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.testUntil;
import static oracle.weblogic.kubernetes.utils.DomainUtils.createDomainAndVerify;
import static oracle.weblogic.kubernetes.utils.DomainUtils.shutdownDomainAndVerify;
import static oracle.weblogic.kubernetes.utils.ImageUtils.createTestRepoSecret;
import static oracle.weblogic.kubernetes.utils.LoggingUtil.checkPodLogContainsString;
import static oracle.weblogic.kubernetes.utils.LoggingUtil.doesPodLogContainString;
import static oracle.weblogic.kubernetes.utils.OperatorUtils.installAndVerifyOperator;
import static oracle.weblogic.kubernetes.utils.PodUtils.checkPodDeleted;
import static oracle.weblogic.kubernetes.utils.PodUtils.checkPodDoesNotExist;
import static oracle.weblogic.kubernetes.utils.PodUtils.checkPodRestarted;
import static oracle.weblogic.kubernetes.utils.PodUtils.setPodAntiAffinity;
import static oracle.weblogic.kubernetes.utils.SecretUtils.createSecretWithUsernamePassword;
import static oracle.weblogic.kubernetes.utils.ThreadSafeLogger.getLogger;
import static org.awaitility.Awaitility.with;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * This test is to verify shutdown rules when shutdown properties are defined at different levels
 * (domain, cluster, adminServer and managedServer level).
 */
@DisplayName("Verify shutdown rules when shutdown properties are defined at different levels")
@IntegrationTest
@Tag("olcne-srg")
@Tag("kind-parallel")
@Tag("okd-wls-mrg")
@Tag("oke-arm")
@Tag("oke-weekly-sequential")
class ItPodsShutdownOption {

  private static String domainNamespace = null;
  private static String opNamespace = null;

  // domain constants
  private static String domainUid = "domain1";
  private static int replicaCount = 2;
  private static String adminServerPodName = domainUid + "-" + ADMIN_SERVER_NAME_BASE;
  private static String managedServerPodNamePrefix = domainUid + "-" + MANAGED_SERVER_NAME_BASE;
  String clusterName = "cluster-1";
  private static String indManagedServerName1 = "ms-1";
  private static String indManagedServerPodName1 = domainUid + "-" + indManagedServerName1;
  private static String indManagedServerName2 = "ms-2";
  private static String indManagedServerPodName2 = domainUid + "-" + indManagedServerName2;

  private static LoggingFacade logger = null;

  private static String miiImage;
  private static String adminSecretName;
  private static String encryptionSecretName;
  private static String cmName = "configuredcluster";
  private static ClusterResource cluster = null;

  /**
   * 1. Get namespaces for operator and WebLogic domain.
   * 2. Push MII image to registry.
   * 3. Create WebLogic credential secret and encryption secret.
   * 4. Create configmap for independent managed server additions.
   *
   * @param namespaces list of namespaces injected by JUnit
   */
  @BeforeAll
  public static void initAll(@Namespaces(2) List<String> namespaces) {
    logger = getLogger();
    // get a unique operator namespace
    logger.info("Getting a unique namespace for operator");
    assertNotNull(namespaces.get(0), "Namespace list is null");
    opNamespace = namespaces.get(0);

    // get a unique domain namespace
    logger.info("Getting a unique namespace for WebLogic domain");
    assertNotNull(namespaces.get(1), "Namespace list is null");
    domainNamespace = namespaces.get(1);

    // install and verify operator with FINE logging level
    HelmParams opHelmParams =
        new HelmParams().releaseName(OPERATOR_RELEASE_NAME)
            .namespace(opNamespace)
            .chartDir(OPERATOR_CHART_DIR);
    installAndVerifyOperator(opNamespace, opNamespace + "-sa", false, 0, "FINE",
        opHelmParams, domainNamespace);

    // get the pre-built image created by IntegrationTestWatcher
    miiImage = MII_BASIC_IMAGE_NAME + ":" + MII_BASIC_IMAGE_TAG;

    // create registry secret to pull the image from registry
    // this secret is used only for non-kind cluster
    logger.info("Creating registry secret in namespace {0}", domainNamespace);
    createTestRepoSecret(domainNamespace);

    // create secret for admin credentials
    logger.info("Creating secret for admin credentials");
    adminSecretName = "weblogic-credentials";
    createSecretWithUsernamePassword(adminSecretName, domainNamespace, ADMIN_USERNAME_DEFAULT, ADMIN_PASSWORD_DEFAULT);

    // create encryption secret
    logger.info("Creating encryption secret");
    encryptionSecretName = "encryptionsecret";
    createSecretWithUsernamePassword(encryptionSecretName, domainNamespace, "weblogicenc", "weblogicenc");

    String yamlString = "topology:\n"
        + "  Server:\n"
        + "    'ms-1':\n"
        + "      ListenPort: '10001'\n"
        + "    'ms-2':\n"
        + "      ListenPort: '9001'\n";

    createModelConfigMap(cmName, yamlString);
  }

  /**
   * Delete the domain created by each test for the next test to start over.
   */
  @AfterEach
  public void afterEach() {
    logger.info("Deleting the domain resource");
    shutdownDomainAndVerify(domainNamespace, domainUid, replicaCount);
    TestActions.deleteDomainCustomResource(domainUid, domainNamespace);
    checkPodDoesNotExist(indManagedServerPodName1, domainUid, domainNamespace);
    checkPodDoesNotExist(indManagedServerPodName2, domainUid, domainNamespace);
    if (cluster != null) {
      TestActions.deleteClusterCustomResource(clusterName, domainNamespace);
    }
  }

  /**
   * Add shutdown options for servers at all levels: domain, admin server, cluster and managed server levels.
   * Verify individual specific level options takes precedence.
   *
   *<p>Domain level shutdown option which is applicable for all servers in the domain
   *      shutdownType - Forced , timeoutSeconds - 30 secs, ignoreSessions - true
   * Admin server level shutdown option applicable only to the admin server
   *      shutdownType - Forced , timeoutSeconds - 40 secs, ignoreSessions - true
   * Cluster level shutdown option applicable only to the clustered instances
   *      shutdownType - Graceful , timeoutSeconds - 60 secs, ignoreSessions - false
   * Managed server server level shutdown option applicable only to the independent managed servers
   *      shutdownType - Forced , timeoutSeconds - 45 secs, ignoreSessions - true
   *
   *<p>Since the shutdown options are provided at all levels the domain level shutdown options has no effect on the
   * admin server, cluster, or managed server options. All of those entities use their own shutdown options.
   *
   *<p>When server pods starts up the server.out log will show the options applied to the servers. The test verifies
   * the logs and determine the outcome of the test.
   *
   *<p>This use case shows how to add shutdown options at domain and how to override them at server level.
   */
  @Test
  @DisplayName("Verify shutdown rules when shutdown properties are defined at different levels ")
  @Tag("gate")
  @Tag("crio")
  void testShutdownPropsAllLevels() {


    // create Shutdown objects for each server and cluster
    Shutdown[] shutDownObjects = new Shutdown[5];
    Shutdown dom = new Shutdown().ignoreSessions(Boolean.TRUE).shutdownType("Forced").timeoutSeconds(30L);
    Shutdown admin = new Shutdown().ignoreSessions(Boolean.TRUE).shutdownType("Forced").timeoutSeconds(40L);
    Shutdown cluster = new Shutdown().ignoreSessions(Boolean.FALSE).shutdownType("Graceful").timeoutSeconds(60L);
    Shutdown ms1 = new Shutdown().ignoreSessions(Boolean.FALSE).shutdownType("Graceful").timeoutSeconds(120L);
    Shutdown ms2 = new Shutdown().ignoreSessions(Boolean.TRUE).shutdownType("Forced").timeoutSeconds(45L);
    shutDownObjects[0] = dom;
    shutDownObjects[1] = admin;
    shutDownObjects[2] = cluster;
    shutDownObjects[3] = ms1;
    shutDownObjects[4] = ms2;
    // create domain custom resource and verify all the pods came up
    DomainResource domain = buildDomainResource(shutDownObjects);
    createVerifyDomain(domain);

    // get pod logs each server which contains server.out file logs and verify values set above are present in the log
    verifyServerLog(domainNamespace, adminServerPodName,
        new String[]{"SHUTDOWN_IGNORE_SESSIONS=true", "SHUTDOWN_TYPE=Forced", "SHUTDOWN_TIMEOUT=40"});
    verifyServerLog(domainNamespace, managedServerPodNamePrefix + 1,
        new String[]{"SHUTDOWN_IGNORE_SESSIONS=false", "SHUTDOWN_TYPE=Graceful", "SHUTDOWN_TIMEOUT=60"});
    verifyServerLog(domainNamespace, managedServerPodNamePrefix + 2,
        new String[]{"SHUTDOWN_IGNORE_SESSIONS=false", "SHUTDOWN_TYPE=Graceful", "SHUTDOWN_TIMEOUT=60"});
    verifyServerLog(domainNamespace, indManagedServerPodName1,
        new String[]{"SHUTDOWN_IGNORE_SESSIONS=false", "SHUTDOWN_TYPE=Graceful", "SHUTDOWN_TIMEOUT=120"});
    verifyServerLog(domainNamespace, indManagedServerPodName2,
        new String[]{"SHUTDOWN_IGNORE_SESSIONS=true", "SHUTDOWN_TYPE=Forced", "SHUTDOWN_TIMEOUT=45"});
  }

  /**
   * Add shutdown options for servers at all levels: domain, admin server, cluster and managed server levels
   * and override all those options with a domain level ENV variables. Verify the domain level options takes precedence.
   *
   *<p>Domain level shutdown option which is applicable for all servers in the domain
   *      shutdownType - Forced , timeoutSeconds - 30 secs, ignoreSessions - true
   * Admin server level shutdown option applicable only to the admin server
   *      shutdownType - Forced , timeoutSeconds - 40 secs, ignoreSessions - true
   * Cluster level shutdown option applicable only to the clustered instances
   *      shutdownType - Graceful , timeoutSeconds - 60 secs, ignoreSessions - false
   * Managed server server level shutdown option applicable only to the independent managed servers
   *      shutdownType - Forced , timeoutSeconds - 45 secs, ignoreSessions - true
   *
   *<p>After creating the above options override the shutdownType for all servers using a ENV level value - Forced
   * Now shutdownType for all servers are overridden with Forced as the shutdown option but rest of the properties
   * are applied as set in the individual server levels.
   *
   *<p>When server pods starts up the server.out log will show the shutdown options applied to the servers.
   * The test verifies the logs and determine the outcome of the test.
   *
   *<p>This use case shows how to add shutdown options at domain and how to override them using ENV variable.
   */
  @Test
  @DisplayName("Verify shutdown rules when shutdown properties are defined at different levels ")
  void testShutdownPropsEnvOverride() {


    // create Shutdown objects for each server and cluster
    Shutdown[] shutDownObjects = new Shutdown[5];
    Shutdown dom = new Shutdown().ignoreSessions(Boolean.TRUE).shutdownType("Forced").timeoutSeconds(30L);
    Shutdown admin = new Shutdown().ignoreSessions(Boolean.TRUE).shutdownType("Forced").timeoutSeconds(40L);
    Shutdown cluster = new Shutdown().ignoreSessions(Boolean.FALSE).shutdownType("Graceful").timeoutSeconds(60L);
    Shutdown ms1 = new Shutdown().ignoreSessions(Boolean.FALSE).shutdownType("Graceful").timeoutSeconds(120L);
    Shutdown ms2 = new Shutdown().ignoreSessions(Boolean.TRUE).shutdownType("Forced").timeoutSeconds(45L);
    shutDownObjects[0] = dom;
    shutDownObjects[1] = admin;
    shutDownObjects[2] = cluster;
    shutDownObjects[3] = ms1;
    shutDownObjects[4] = ms2;
    // create domain custom resource and verify all the pods came up
    DomainResource domain = buildDomainResource(shutDownObjects);
    domain.spec().serverPod()
        .addEnvItem(new V1EnvVar()
            .name("SHUTDOWN_TYPE")
            .value("Graceful"));
    createVerifyDomain(domain);

    // get pod logs each server which contains server.out file logs and verify values set above are present in the log
    // except shutdowntype rest of the values should match with abobe shutdown object values
    verifyServerLog(domainNamespace, adminServerPodName,
        new String[]{"SHUTDOWN_IGNORE_SESSIONS=true", "SHUTDOWN_TYPE=Graceful", "SHUTDOWN_TIMEOUT=40"});
    verifyServerLog(domainNamespace, managedServerPodNamePrefix + 1,
        new String[]{"SHUTDOWN_IGNORE_SESSIONS=false", "SHUTDOWN_TYPE=Graceful", "SHUTDOWN_TIMEOUT=60"});
    verifyServerLog(domainNamespace, managedServerPodNamePrefix + 2,
        new String[]{"SHUTDOWN_IGNORE_SESSIONS=false", "SHUTDOWN_TYPE=Graceful", "SHUTDOWN_TIMEOUT=60"});
    verifyServerLog(domainNamespace, indManagedServerPodName1,
        new String[]{"SHUTDOWN_IGNORE_SESSIONS=false", "SHUTDOWN_TYPE=Graceful", "SHUTDOWN_TIMEOUT=120"});
    verifyServerLog(domainNamespace, indManagedServerPodName2,
        new String[]{"SHUTDOWN_IGNORE_SESSIONS=true", "SHUTDOWN_TYPE=Graceful", "SHUTDOWN_TIMEOUT=45"});
  }

  /**
   * Set the shutdown options in different server level.
   *
   * Cluster: ignoreSessions -> false, waitForAllSessions -> true, shutdownType -> Graceful.
   * MS1: ignoreSessions -> false, waitForAllSessions -> true, shutdownType -> Graceful.
   * MS2: ignoreSessions -> true, waitForAllSessions -> true,
   *      When ignoreSessions is set to true, waitForAllSessions does not apply
   *
   * Verify the shutdown options are set in the server log.
   * Scale down the cluster and verify the operator will call REST API to shutdown the server before deleting the pod.
   */
  @Test
  @DisplayName("Verify operator will call REST API to shutdown the server before deleting the pod")
  void testShutdownServersUsingRestBeforeDelete() {

    // create Shutdown objects for each server and cluster
    Shutdown[] shutDownObjects = new Shutdown[5];
    Shutdown dom = new Shutdown().ignoreSessions(Boolean.TRUE).shutdownType("Forced").timeoutSeconds(30L);
    Shutdown admin = new Shutdown().ignoreSessions(Boolean.TRUE).shutdownType("Forced").timeoutSeconds(40L);
    Shutdown cluster = new Shutdown().ignoreSessions(Boolean.FALSE).shutdownType("Graceful").timeoutSeconds(60L)
        .waitForAllSessions(Boolean.TRUE);
    Shutdown ms1 = new Shutdown().ignoreSessions(Boolean.FALSE).shutdownType("Graceful").timeoutSeconds(120L)
        .waitForAllSessions(Boolean.TRUE);
    Shutdown ms2 = new Shutdown().ignoreSessions(Boolean.TRUE).shutdownType("Graceful").timeoutSeconds(45L)
        .waitForAllSessions(Boolean.TRUE);
    shutDownObjects[0] = dom;
    shutDownObjects[1] = admin;
    shutDownObjects[2] = cluster;
    shutDownObjects[3] = ms1;
    shutDownObjects[4] = ms2;
    // create domain custom resource and verify all the pods came up
    DomainResource domain = buildDomainResource(shutDownObjects);
    createVerifyDomain(domain);

    // Verify values set above are present in the server log
    verifyServerLog(domainNamespace, adminServerPodName,
        new String[]{"SHUTDOWN_IGNORE_SESSIONS=true", "SHUTDOWN_TYPE=Forced", "SHUTDOWN_TIMEOUT=40"});
    verifyServerLog(domainNamespace, managedServerPodNamePrefix + 1,
        new String[]{"SHUTDOWN_IGNORE_SESSIONS=false", "SHUTDOWN_TYPE=Graceful", "SHUTDOWN_TIMEOUT=60",
            "SHUTDOWN_WAIT_FOR_ALL_SESSIONS=true"});
    verifyServerLog(domainNamespace, managedServerPodNamePrefix + 2,
        new String[]{"SHUTDOWN_IGNORE_SESSIONS=false", "SHUTDOWN_TYPE=Graceful", "SHUTDOWN_TIMEOUT=60",
            "SHUTDOWN_WAIT_FOR_ALL_SESSIONS=true"});
    verifyServerLog(domainNamespace, indManagedServerPodName1,
        new String[]{"SHUTDOWN_IGNORE_SESSIONS=false", "SHUTDOWN_TYPE=Graceful", "SHUTDOWN_TIMEOUT=120",
            "SHUTDOWN_WAIT_FOR_ALL_SESSIONS=true"});
    verifyServerLog(domainNamespace, indManagedServerPodName2,
        new String[]{"SHUTDOWN_IGNORE_SESSIONS=true", "SHUTDOWN_TYPE=Graceful", "SHUTDOWN_TIMEOUT=45",
            "SHUTDOWN_WAIT_FOR_ALL_SESSIONS=true"});

    // scale down the cluster to 1 server
    int newReplicaCount = replicaCount - 1;
    logger.info("Scaling down the cluster {0} in namespace {1} to set the replicas to {2}",
        clusterName, domainNamespace, newReplicaCount);
    assertDoesNotThrow(() -> scaleCluster(clusterName, domainNamespace, newReplicaCount),
        String.format("failed to scale down cluster %s in namespace %s", clusterName, domainNamespace));

    checkPodDeleted(managedServerPodNamePrefix + replicaCount, domainUid, domainNamespace);

    // verify operator will call REST API to shutdown the server before deleting the pod
    String operatorPodName =
        assertDoesNotThrow(() -> getOperatorPodName(OPERATOR_RELEASE_NAME, opNamespace),
            "Can't get operator's pod name");
    String expectedMsg = "WL pod shutdown: Initiating shutdown of WebLogic server managed-server2 via REST interface.";
    checkPodLogContainsString(opNamespace, operatorPodName, expectedMsg);
  }

  /**
   * Set the shutdown options in different server level.
   * Set the ignoreSession for cluster and ms2 to true.
   * Verify the operator will issue REST API call before scaling down the cluster.
   *
   * Domain: ignoreSessions -> True, shutdownType -> Forced.
   * Cluster: ignoreSessions -> True, waitForAllSessions -> true, shutdownType -> Graceful.
   * MS1: ignoreSessions -> false, waitForAllSessions -> true, shutdownType -> Graceful.
   * MS2: ignoreSessions -> true, waitForAllSessions -> true, shutdownType -> Graceful.
   *
   * Verify the shutdown options are set in the server log.
   * Scale down the cluster and verify the operator will call REST API to shutdown the server before deleting the pod.
   * Delete ms2, verify the operator will NOT call REST API to shutdown the server first before deleting the pod.
   */
  @Test
  @DisplayName("Verify shutdown servers using REST prior to deleting pods")
  void testShutdownServersUsingRestBeforeDeleteIgnoreSession() {

    // create Shutdown objects for each server and cluster
    Shutdown[] shutDownObjects = new Shutdown[5];
    Shutdown dom = new Shutdown().ignoreSessions(Boolean.TRUE).shutdownType("Forced").timeoutSeconds(30L);
    Shutdown admin = new Shutdown().ignoreSessions(Boolean.TRUE).shutdownType("Forced").timeoutSeconds(40L);
    Shutdown cluster = new Shutdown().ignoreSessions(Boolean.TRUE).shutdownType("Graceful").timeoutSeconds(60L)
        .waitForAllSessions(Boolean.TRUE);
    Shutdown ms1 = new Shutdown().ignoreSessions(Boolean.FALSE).shutdownType("Graceful").timeoutSeconds(120L)
        .waitForAllSessions(Boolean.TRUE);
    Shutdown ms2 = new Shutdown().ignoreSessions(Boolean.TRUE).shutdownType("Graceful").timeoutSeconds(45L)
        .waitForAllSessions(Boolean.TRUE);
    shutDownObjects[0] = dom;
    shutDownObjects[1] = admin;
    shutDownObjects[2] = cluster;
    shutDownObjects[3] = ms1;
    shutDownObjects[4] = ms2;
    // create domain custom resource and verify all the pods came up
    DomainResource domain = buildDomainResource(shutDownObjects);
    createVerifyDomain(domain);

    // Verify values set above are present in the server log
    verifyServerLog(domainNamespace, adminServerPodName,
        new String[]{"SHUTDOWN_IGNORE_SESSIONS=true", "SHUTDOWN_TYPE=Forced", "SHUTDOWN_TIMEOUT=40"});
    verifyServerLog(domainNamespace, managedServerPodNamePrefix + 1,
        new String[]{"SHUTDOWN_IGNORE_SESSIONS=true", "SHUTDOWN_TYPE=Graceful", "SHUTDOWN_TIMEOUT=60",
            "SHUTDOWN_WAIT_FOR_ALL_SESSIONS=true"});
    verifyServerLog(domainNamespace, managedServerPodNamePrefix + 2,
        new String[]{"SHUTDOWN_IGNORE_SESSIONS=true", "SHUTDOWN_TYPE=Graceful", "SHUTDOWN_TIMEOUT=60",
            "SHUTDOWN_WAIT_FOR_ALL_SESSIONS=true"});
    verifyServerLog(domainNamespace, indManagedServerPodName1,
        new String[]{"SHUTDOWN_IGNORE_SESSIONS=false", "SHUTDOWN_TYPE=Graceful", "SHUTDOWN_TIMEOUT=120",
            "SHUTDOWN_WAIT_FOR_ALL_SESSIONS=true"});
    verifyServerLog(domainNamespace, indManagedServerPodName2,
        new String[]{"SHUTDOWN_IGNORE_SESSIONS=true", "SHUTDOWN_TYPE=Graceful", "SHUTDOWN_TIMEOUT=45",
            "SHUTDOWN_WAIT_FOR_ALL_SESSIONS=true"});

    // scale down the cluster to 1 server
    int newReplicaCount = replicaCount - 1;
    logger.info("Scaling down the cluster {0} in namespace {1} to set the replicas to {2}",
        clusterName, domainNamespace, newReplicaCount);
    assertDoesNotThrow(() -> scaleCluster(clusterName, domainNamespace, newReplicaCount),
        String.format("failed to scale down cluster %s in namespace %s", clusterName, domainNamespace));

    checkPodDeleted(managedServerPodNamePrefix + replicaCount, domainUid, domainNamespace);

    String operatorPodName =
        assertDoesNotThrow(() -> getOperatorPodName(OPERATOR_RELEASE_NAME, opNamespace),
            "Can't get operator's pod name");
    String expectedMsg = "WL pod shutdown: Initiating shutdown of WebLogic server managed-server2 via REST interface";
    checkPodLogContainsString(opNamespace, operatorPodName, expectedMsg);

    // delete ms2
    OffsetDateTime ms2PodCreationTime =
        assertDoesNotThrow(() -> getPodCreationTimestamp(domainNamespace, "", indManagedServerPodName2),
            String.format("Failed to get creationTimestamp for pod %s", indManagedServerName2));
    assertDoesNotThrow(() -> deletePod(indManagedServerPodName2, domainNamespace));
    checkPodRestarted(domainUid, domainNamespace, indManagedServerPodName2, ms2PodCreationTime);
    expectedMsg = "WL pod shutdown: Initiating shutdown of WebLogic server ms-2 via REST interface.";
    assertFalse(doesPodLogContainString(opNamespace, operatorPodName, expectedMsg));
  }

  // create custom domain resource with different shutdownobject values for adminserver/cluster/independent ms
  private DomainResource buildDomainResource(Shutdown[] shutDownObject) {
    logger.info("Creating domain custom resource");
    // create cluster object
    DomainResource domain = new DomainResource()
        .apiVersion(DOMAIN_API_VERSION)
        .kind("Domain")
        .metadata(new V1ObjectMeta()
            .name(domainUid)
            .namespace(domainNamespace))
        .spec(new DomainSpec()
            .domainUid(domainUid)
            .domainHomeSourceType("FromModel")
            .replicas(replicaCount)
            .image(miiImage)
            .imagePullPolicy(IMAGE_PULL_POLICY)
            .addImagePullSecretsItem(new V1LocalObjectReference()
                .name(TEST_IMAGES_REPO_SECRET_NAME))
            .webLogicCredentialsSecret(new V1LocalObjectReference()
                .name(adminSecretName))
            .includeServerOutInPodLog(true)
            .serverStartPolicy("IfNeeded")
            .serverPod(new ServerPod()
                .shutdown(shutDownObject[0])
                .addEnvItem(new V1EnvVar()
                    .name("JAVA_OPTIONS")
                    .value("-Dweblogic.StdoutDebugEnabled=false"))
                .addEnvItem(new V1EnvVar()
                    .name("USER_MEM_ARGS")
                    .value("-Djava.security.egd=file:/dev/./urandom ")))
            .adminServer(new AdminServer()
                .serverPod(new ServerPod()
                    .shutdown(shutDownObject[1])))
            .configuration(new Configuration()
                .model(new Model()
                    .configMap(cmName)
                    .domainType(WLS_DOMAIN_TYPE)
                    .runtimeEncryptionSecret(encryptionSecretName))
                .introspectorJobActiveDeadlineSeconds(3000L))
            .addManagedServersItem(new ManagedServer()
                .serverStartPolicy("Always")
                .serverName(indManagedServerName1)
                .serverPod(new ServerPod()
                    .shutdown(shutDownObject[3])))
            .addManagedServersItem(new ManagedServer()
                .serverStartPolicy("Always")
                .serverName(indManagedServerName2)
                .serverPod(new ServerPod()
                    .shutdown(shutDownObject[4]))));
    setPodAntiAffinity(domain);
    cluster = createClusterResource(
        clusterName, clusterName, domainNamespace, replicaCount);
    cluster.getSpec().serverPod(new ServerPod()
        .shutdown((shutDownObject[2])));

    logger.info("Creating cluster resource {0} in namespace {1}",clusterName, domainNamespace);
    createClusterAndVerify(cluster);

    // set cluster references
    domain.getSpec().withCluster(new V1LocalObjectReference().name(clusterName));

    return domain;
  }

  // create domain resource and verify all the server pods are ready
  private void createVerifyDomain(DomainResource domain) {
    // create model in image domain
    logger.info("Creating model in image domain {0} in namespace {1} using image {2}",
        domainUid, domainNamespace, miiImage);
    createDomainAndVerify(domain, domainNamespace);

    // check that admin service/pod exists in the domain namespace
    logger.info("Checking that admin service/pod {0} exists in namespace {1}",
        adminServerPodName, domainNamespace);
    checkPodReadyAndServiceExists(with().pollDelay(2, SECONDS)
        .and().with().pollInterval(10, SECONDS)
        .atMost(20, MINUTES).await(), adminServerPodName, domainUid, domainNamespace);

    for (int i = 1; i <= replicaCount; i++) {
      String managedServerPodName = managedServerPodNamePrefix + i;

      // check that ms service/pod exists in the domain namespace
      logger.info("Checking that clustered ms service/pod {0} exists in namespace {1}",
          managedServerPodName, domainNamespace);
      checkPodReadyAndServiceExists(with().pollDelay(2, SECONDS)
          .and().with().pollInterval(10, SECONDS)
          .atMost(20, MINUTES).await(), managedServerPodName, domainUid, domainNamespace);
    }

    // check for independent managed server pods existence in the domain namespace
    for (String podName : new String[]{indManagedServerPodName1, indManagedServerPodName2}) {
      // check that ms service/pod exists in the domain namespace
      logger.info("Checking that independent ms service/pod {0} exists in namespace {1}",
          podName, domainNamespace);
      checkPodReadyAndServiceExists(with().pollDelay(2, SECONDS)
          .and().with().pollInterval(10, SECONDS)
          .atMost(20, MINUTES).await(), podName, domainUid, domainNamespace);
    }
  }

  // get pod log which includes the server.out logs and verify the messages contain the set shutdown properties
  private void verifyServerLog(String namespace, String podName, String[] envVars) {
    testUntil(
        () -> {
          boolean result = true;
          String podLog = assertDoesNotThrow(() -> TestActions.getPodLog(podName, namespace));
          for (String envVar : envVars) {
            logger.info("Checking Pod {0} for server startup property {1}", podName, envVar);
            result = result && podLog.contains(envVar);
          }
          return result;
        },
        logger,
        "server log for pod {0} contains environment variables {1}",
        podName,
        envVars
    );
  }

  // Crate a ConfigMap with a model to add a 2 independent managed servers
  private static void createModelConfigMap(String configMapName, String model) {
    Map<String, String> labels = new HashMap<>();
    labels.put("weblogic.domainUid", domainUid);
    Map<String, String> data = new HashMap<>();
    data.put("independent-ms.yaml", model);

    V1ConfigMap configMap = new V1ConfigMap()
        .data(data)
        .metadata(new V1ObjectMeta()
            .labels(labels)
            .name(configMapName)
            .namespace(domainNamespace));

    boolean cmCreated = assertDoesNotThrow(() -> createConfigMap(configMap),
        String.format("Can't create ConfigMap %s", configMapName));
    assertTrue(cmCreated, String.format("createConfigMap failed %s", configMapName));
  }
}

