// Copyright (c) 2023, 2025, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.weblogic.kubernetes;

import java.time.OffsetDateTime;
import java.time.temporal.ChronoField;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import io.kubernetes.client.openapi.models.V1ConfigMap;
import io.kubernetes.client.openapi.models.V1EnvVar;
import io.kubernetes.client.openapi.models.V1LocalObjectReference;
import io.kubernetes.client.openapi.models.V1ObjectMeta;
import oracle.weblogic.domain.AdminServer;
import oracle.weblogic.domain.AdminService;
import oracle.weblogic.domain.Channel;
import oracle.weblogic.domain.Configuration;
import oracle.weblogic.domain.DomainResource;
import oracle.weblogic.domain.DomainSpec;
import oracle.weblogic.domain.Model;
import oracle.weblogic.domain.ServerPod;
import oracle.weblogic.domain.ServerService;
import oracle.weblogic.kubernetes.actions.impl.OperatorParams;
import oracle.weblogic.kubernetes.actions.impl.primitive.Command;
import oracle.weblogic.kubernetes.actions.impl.primitive.CommandParams;
import oracle.weblogic.kubernetes.actions.impl.primitive.HelmParams;
import oracle.weblogic.kubernetes.annotations.IntegrationTest;
import oracle.weblogic.kubernetes.annotations.Namespaces;
import oracle.weblogic.kubernetes.logging.LoggingFacade;
import oracle.weblogic.kubernetes.utils.ExecResult;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import static oracle.weblogic.kubernetes.TestConstants.ADMIN_PASSWORD_DEFAULT;
import static oracle.weblogic.kubernetes.TestConstants.ADMIN_USERNAME_DEFAULT;
import static oracle.weblogic.kubernetes.TestConstants.DOMAIN_API_VERSION;
import static oracle.weblogic.kubernetes.TestConstants.IMAGE_PULL_POLICY;
import static oracle.weblogic.kubernetes.TestConstants.KUBERNETES_CLI;
import static oracle.weblogic.kubernetes.TestConstants.MII_BASIC_IMAGE_NAME;
import static oracle.weblogic.kubernetes.TestConstants.MII_BASIC_IMAGE_TAG;
import static oracle.weblogic.kubernetes.TestConstants.TEST_IMAGES_REPO_SECRET_NAME;
import static oracle.weblogic.kubernetes.actions.TestActions.createConfigMap;
import static oracle.weblogic.kubernetes.actions.TestActions.getPodCreationTimestamp;
import static oracle.weblogic.kubernetes.actions.TestActions.patchDomainResourceWithNewIntrospectVersion;
import static oracle.weblogic.kubernetes.actions.TestActions.patchDomainResourceWithNewRestartVersion;
import static oracle.weblogic.kubernetes.actions.impl.Cluster.scaleCluster;
import static oracle.weblogic.kubernetes.assertions.TestAssertions.verifyRollingRestartOccurred;
import static oracle.weblogic.kubernetes.utils.ClusterUtils.createClusterResourceAndAddToDomainResource;
import static oracle.weblogic.kubernetes.utils.ClusterUtils.deleteClusterCustomResourceAndVerify;
import static oracle.weblogic.kubernetes.utils.ClusterUtils.patchClusterResourceAndVerify;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.checkPodReadyAndServiceExists;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.getNextFreePort;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.testUntil;
import static oracle.weblogic.kubernetes.utils.DomainUtils.createDomainAndVerify;
import static oracle.weblogic.kubernetes.utils.ImageUtils.createTestRepoSecret;
import static oracle.weblogic.kubernetes.utils.OperatorUtils.installAndVerifyOperator;
import static oracle.weblogic.kubernetes.utils.OperatorUtils.upgradeAndVerifyOperator;
import static oracle.weblogic.kubernetes.utils.PatchDomainUtils.patchDomainResource;
import static oracle.weblogic.kubernetes.utils.PodUtils.checkPodDeleted;
import static oracle.weblogic.kubernetes.utils.PodUtils.getPodCreationTime;
import static oracle.weblogic.kubernetes.utils.PodUtils.setPodAntiAffinity;
import static oracle.weblogic.kubernetes.utils.SecretUtils.createSecretWithUsernamePassword;
import static oracle.weblogic.kubernetes.utils.ThreadSafeLogger.getLogger;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 
 * Test the config fields below in a MiiDomain.
 * 1. domain.spec.maxClusterConcurrentStartup
 * 2. domain.spec.maxClusterConcurrentShutdown
 * 3. domain.spec.maxClusterUnavailable
 * 4. cluster.spec.maxUnavailable
 * 5. cluster.spec.maxConcurrentShutdown
 * 6. cluster.spec.maxConcurrentStartup.
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DisplayName("Test to a create model in image domain with Cluster Resourcees")
@IntegrationTest
@Tag("kind-parallel")
@Tag("olcne-sequential")
class ItMaxConcurOptions {

  private static String opNamespace = null;
  private static String domainNamespace = null;
  private static final String domainUid = "maxconcuroptionsdomain";
  private static final String adminServerPodName = domainUid + "-admin-server";
  private static final String managedServerC1NamePrefix = "c1-managed-server";
  private static final String managedServerC2NamePrefix = "c2-managed-server";
  private static final String managedServerC1PodNamePrefix = domainUid + "-" + managedServerC1NamePrefix;
  private static final String managedServerC2PodNamePrefix = domainUid + "-" + managedServerC2NamePrefix;
  private static final String cluster1Res   = domainUid + "-cluster-1";
  private static final String cluster2Res   = domainUid + "-cluster-2";
  private static final String cluster1Name  = "cluster-1";
  private static final String cluster2Name  = "cluster-2";

  private static LoggingFacade logger = null;
  private static final int replicaCount = 4;
  private static final int maxClusterUnavailable = 2;
  private static final int maxClusterConcurrentStartup = 2;
  private static final int maxClusterConcurrentShutdown = 2;

  private static DomainResource domain = null;

  /**
   * Install Operator.
   * @param namespaces list of namespaces created by the IntegrationTestWatcher by the
   JUnit engine parameter resolution mechanism
   */
  @BeforeAll
  public static void initAll(@Namespaces(2) List<String> namespaces) {
    logger = getLogger();

    // get a new unique opNamespace
    logger.info("Creating unique namespace for Operator");
    assertNotNull(namespaces.get(0), "Namespace list is null");
    opNamespace = namespaces.get(0);

    logger.info("Creating unique namespace for Domain");
    assertNotNull(namespaces.get(1), "Namespace list is null");
    domainNamespace = namespaces.get(1);

    // install and verify operator
    HelmParams opHelmParams  = installAndVerifyOperator(opNamespace, domainNamespace).getHelmParams();

    // operator chart values to override
    OperatorParams opParams = new OperatorParams()
        .helmParams(opHelmParams)
        .javaLoggingLevel(("FINE"));
    assertTrue(upgradeAndVerifyOperator(opNamespace, opParams),
        "Failed to upgrade operator to FINE  logging leve");

    // create and verify one MII domain
    domain = createDomainAndVerifyWithConfigMap();
  }

  /**
   * Test domain.spec.maxClusterConcurrentStartup - the maximum number of cluster member Managed Server
   *    instances that the operator will start in parallel for a given cluster,
   *    if `maxConcurrentStartup` is not specified for a specific cluster under the `clusters` field.
   *    A value of 0 means there is no configured limit. Defaults to 0.
   *
   * Set domain.spec.maxClusterConcurrentStartup = 2
   * verify that the Operator startup 2 managed servers concurrently in each cluster.
   */
  @Test
  @DisplayName("Verify that the Operator startup 2 managed servers concurrently "
      + "when domain.spec.maxClusterConcurrentStartup = 2")
  void testMaxClusterConcurrentStartup() {
    // reduce replicas in domain resource from 4 to 2
    ArrayList<String> managedServerPodNamePrefixList =
        new ArrayList<String>(List.of(managedServerC1PodNamePrefix, managedServerC2PodNamePrefix));
    int newReplicas = 2;
    boolean scaleup = false;
    patchDomainResourceWithReplicasAndVerify(managedServerPodNamePrefixList, 3, 4, newReplicas, scaleup);

    // increase replicas in domain resource from 2 to 4
    newReplicas = 4;
    scaleup = true;
    patchDomainResourceWithReplicasAndVerify(managedServerPodNamePrefixList, 3, 4, newReplicas, scaleup);

    // verify that the Operator starts 2 managed servers in each cluster concurrently
    verifyServersStartedConcurrently(managedServerC1PodNamePrefix, 3, replicaCount);
    verifyServersStartedConcurrently(managedServerC2PodNamePrefix, 3, replicaCount);
  }

  /**
   * Test domain.spec.maxClusterConcurrentShutdown - the default maximum number of WebLogic Server instances
   *    that a cluster will shut down in parallel when it is being partially shut down by lowering its replica count.
   *    You can override this default on a per-cluster basis by setting the cluster's `maxConcurrentShutdown` field.
   *    A value of 0 means there is no limit. Defaults to 1.
   *
   * Set domain.spec.maxClusterConcurrentShutdown = 2
   * verify that the Operator shutdown 2 managed servers concurrently in each cluster.
   */
  @Test
  @DisplayName("Verify that the Operator shutdown 2 managed servers concurrently "
      + "when domain.spec.maxClusterConcurrentShutdown = 2")
  void testMaxClusterConcurrentShutdown() {
    // reduce replicas in domain resource from 4 to 2
    ArrayList<String> managedServerPodNamePrefixList =
        new ArrayList<>(List.of(managedServerC1PodNamePrefix, managedServerC2PodNamePrefix));
    int newReplicas = 2;
    boolean scaleup = false;
    patchDomainResourceWithReplicasAndVerify(managedServerPodNamePrefixList, 3, 4, newReplicas, scaleup);

    // verify that the Operator shutdown 2 managed servers concurrently in each cluster
    verifyShutdownConcurrently(managedServerC1PodNamePrefix,
        managedServerC1NamePrefix, replicaCount, maxClusterConcurrentShutdown);

    // restore test env
    restoreTestEnv(new ArrayList<String>());
  }

  /**
   * Test domain.spec.maxClusterUnavailable -
   *    The maximum number of cluster members that can be temporarily unavailable.
   *    You can override this default on a per cluster basis by setting the cluster's `maxUnavailable` field.
   *
   * Set domain.spec.maxClusterUnavailable = 2
   * verify that the Operator rolling restarts 2 managed servers concurrently in each cluster.
   */
  @Test
  @DisplayName("Verify that the Operator rolling restarts 2 managed servers concurrently "
      + "when domain.spec.maxClusterUnavailable = 2")
  void testMaxClusterUnavailable() {
    // verify that the Operator rolling restarts 2 managed servers in each cluster concurrently
    rollingRestartDomainAndVerify(managedServerC1PodNamePrefix, maxClusterUnavailable, true);
    rollingRestartDomainAndVerify(managedServerC2PodNamePrefix, maxClusterUnavailable, true);
  }

  /**
   * Test cluster.spec.maxUnavailable and cluster.spec.replicas -
   *    the maximum number of cluster members that can be temporarily unavailable.
   *    Defaults to `domain.spec.maxClusterUnavailable`, which defaults to 1.
   *
   * 1. Config domain.spec.maxClusterUnavailable =2 and domain.spec.replicas = 4 in domain resource
   * 2. Config maxUnavailable =1 and replicas = 2 in cluster-1 resource
   * 3. During rolling restart
   *    1) verify that the Operator rolling-restarts 2 managed server in cluster-1 one by one, not concurrently
   *    2) verify that the Operator rolling-restarts 2 managed server in cluster-2 concurrently
   *       until all 4 managed servers are rolling restarted
   */
  @Test
  @DisplayName("Verify that the Operator rolling restarts 2 managed servers concurrently "
      + "when domain.spec.maxClusterUnavailable = 2")
  void testMaxUnavailable() {
    // create cluster-1 and reference it in domain resource
    createClusterResourceAndAddToDomainResource(cluster1Res, cluster1Name,
        0, domainUid, domainNamespace, replicaCount);

    // Config maxUnavailable =1 and replicas = 2 in cluster-1 resource and patch the cluster
    StringBuffer patchStr = new StringBuffer("[")
        .append("{\"op\": \"replace\", \"path\": \"/spec/replicas\", \"value\": 2},")
        .append("{\"op\": \"replace\", \"path\": \"/spec/maxUnavailable\", \"value\": 1}")
        .append("]");
    patchClusterResourceAndVerify(domainNamespace, cluster1Res, patchStr.toString());

    // verify that the Operator rolling-restarts 2 managed server in cluster-1 one by one, not concurrently
    rollingRestartDomainAndVerify(managedServerC1PodNamePrefix, maxClusterUnavailable, false);
    // verify that the Operator rolling-restarts 2 managed server in cluster-2 concurrently
    rollingRestartDomainAndVerify(managedServerC2PodNamePrefix, maxClusterUnavailable, true);

    // restore test env
    ArrayList<String> clusterList = new ArrayList<>(List.of(cluster1Res));
    restoreTestEnv(clusterList);
  }

  /**
   * Test cluster.spec.maxConcurrentShutdown - the maximum number of WebLogic Server instances
   *    that will shut down in parallel for this cluster when it is being partially shut down
   *    by lowering its replica count. A value of 0 means there is no limit.
   *    Defaults to `spec.maxClusterConcurrentShutdown`, which defaults to 1.
   *
   * 1) Config domain.spec.replicas = 5 to startup 5 managed servers in clusters
   * 2) Config domain.spec.maxClusterConcurrentShutdown = 2
   * 3) Create cluster-1 resource and config cluster.spec.maxConcurrentShutdown = 3
   *    and cluster.spec.replicas = 2 in cluster-1 resource to scale down cluster-1,
   *    verify that the Operator shuts down 3 managed servers concurrently in cluster-1
   * 4) Config domain.spec.replicas = 3 in domain resource to scale down cluster-2,
   *    verify that the Operator shuts down 2 managed servers concurrently in cluster-2.
   */
  @Test
  @DisplayName("Verify that the Operator rolling restarts 2 managed servers concurrently "
      + "when domain.spec.maxClusterUnavailable = 2")
  void testMaxConcurrentShutdown() {
    // increase replicas to 5 in domain resource and patch domain with a new introspectVersion
    ArrayList<String> managedServerPodNamePrefixList =
        new ArrayList<>(List.of(managedServerC1PodNamePrefix, managedServerC2PodNamePrefix));
    int newReplicas = 5;
    boolean scaleup = true;
    patchDomainResourceWithReplicasAndVerify(managedServerPodNamePrefixList, 1, newReplicas, newReplicas, scaleup);

    // create cluster-1 and reference it in domain resource
    createClusterResourceAndAddToDomainResource(cluster1Res, cluster1Name,
        0, domainUid, domainNamespace, replicaCount);

    // Config and patch maxConcurrentShutdown = 3 in cluster-1 resource
    StringBuffer patchStr = new StringBuffer("[")
        .append("{\"op\": \"replace\", \"path\": \"/spec/maxConcurrentShutdown\", \"value\": 3}")
        .append("]");
    patchClusterResourceAndVerify(domainNamespace, cluster1Res, patchStr.toString());

    // scale down the cluster by 2
    int newReplicaCount = 2;
    logger.info("Scaling down the cluster {0} in namespace {1} to set the replicas to {2}",
        cluster1Name, domainNamespace, newReplicaCount);
    boolean scalingSuccess = assertDoesNotThrow(() -> scaleCluster(cluster1Res, domainNamespace, newReplicaCount),
        String.format("failed to scale down cluster %s in namespace %s", cluster1Name, domainNamespace));
    assertTrue(scalingSuccess,
        String.format("Cluster scaling down failed for domain %s in namespace %s", domainUid, domainNamespace));

    // verify two managed servers in cluster-1 are shutdown concurrently
    verifyShutdownConcurrently(managedServerC1PodNamePrefix, managedServerC1NamePrefix, replicaCount, newReplicaCount);

    // restore test env
    ArrayList<String> clusterList = new ArrayList<>(List.of(cluster1Res));
    restoreTestEnv(clusterList);
  }

  /**
   * Test domain.spec.maxConcurrentStartup - the maximum number of Managed Servers instances
   *    that the operator will start in parallel for this cluster in response to a change
   *    in the `replicas` count. If more Managed Server instances must be started,
   *    the operator will wait until a Managed Server Pod is in the `Ready` state
   *    before starting the next Managed Server instance. A value of 0 means all Managed Server instances
   *    will start in parallel. Defaults to `domain.spec.maxClusterConcurrentStartup`, which defaults to 0.
   *
   *  1) Config domain.spec.replicas = 1 to startup 1 managed servers in two clusters
   *  2) Config domain.spec.maxClusterConcurrentStartup = 2
   *  3) Config cluster.spec.maxConcurrentStartup= 2 in cluster-1
   *  4) Config cluster.spec.replicas = 3 in cluster-1 resource to scale up cluster-1,
   *     verify that the Operator starts up 2 managed servers concurrently
   *  5) Config cluster.spec.maxConcurrentStartup= 1 in cluster-2
   *  6) Config cluster.spec.replicas = 3 in cluster-2 resource to scale up cluster-2,
   *     verify that the Operator starts up 2 managed servers one by one, not concurrently.
   */
  @Test
  @DisplayName("Verify that the Operator starts 3 managed servers concurrently "
      + "when cluster.spec.maxConcurrentStartupe = 3 and it starts the managed server sequentially "
      + "when cluster.spec.maxConcurrentStartupe = 1")
  void testMaxConcurrentStartup() {
    // decrease replicas from 4 to 1 in domain resource and patch domain with a new introspectVersion
    ArrayList<String> managedServerPodNamePrefixList =
        new ArrayList<>(List.of(managedServerC1PodNamePrefix, managedServerC2PodNamePrefix));
    int newReplicas = 1;
    boolean scaleup = false;
    patchDomainResourceWithReplicasAndVerify(managedServerPodNamePrefixList, 2, 4, newReplicas, scaleup);

    // create cluster-1 and reference it in domain resource
    createClusterResourceAndAddToDomainResource(cluster1Res, cluster1Name,
        0, domainUid, domainNamespace, replicaCount);

    // Config maxConcurrentStartup = 2 in cluster-1 resource and scale up the cluster by 3
    int configValue = 2;
    int newReplicaCount = 3;
    configAndScaleCluster(cluster1Name, cluster1Res, managedServerC1PodNamePrefix,
        "maxConcurrentStartup", configValue, newReplicaCount);

    // verify that the Operator starts up 2 managed servers in cluster-1 concurrently
    verifyServersStartedConcurrently(managedServerC1PodNamePrefix, 2, newReplicaCount);

    // create cluster-2 and reference it in domain resource
    createClusterResourceAndAddToDomainResource(cluster2Res, cluster2Name,
        1, domainUid, domainNamespace, 1);

    // Config maxConcurrentStartup = 1 in cluster-2 resource and scale up the cluster by 3
    configValue = 1;
    newReplicaCount = 3;
    configAndScaleCluster(cluster2Name, cluster2Res, managedServerC2PodNamePrefix,
        "maxConcurrentStartup", configValue, newReplicaCount);

    // verify that the Operator starts up 2 managed servers in cluster-2 one by one
    verifyServersStartedSequentially(managedServerC2PodNamePrefix, 2, newReplicaCount);

    // restore test env
    ArrayList<String> clusterList = new ArrayList<String>(List.of(cluster1Res, cluster2Res));
    restoreTestEnv(clusterList);
  }

  private static DomainResource createDomainAndVerifyWithConfigMap() {
    final String adminSecretName = "weblogic-credentials";
    final String encryptionSecretName = "encryptionsecret";

    String configMapName = domainUid + "-configmap";
    createModelConfigMap(domainUid,configMapName);

    // Create the repo secret to pull the image
    createTestRepoSecret(domainNamespace);

    // create secret for admin credentials
    logger.info("Create secret for admin credentials");
    createSecretWithUsernamePassword(adminSecretName, domainNamespace,
        ADMIN_USERNAME_DEFAULT, ADMIN_PASSWORD_DEFAULT);

    // create encryption secret
    logger.info("Create encryption secret");
    createSecretWithUsernamePassword(encryptionSecretName, domainNamespace,
        "weblogicenc", "weblogicenc");

    // create and deploy domain resource
    DomainResource domain =
        createDomainResource(domainUid, domainNamespace, adminSecretName,
            encryptionSecretName, configMapName);
    logger.info("Creating Domain Resource {0} in namespace {1} using image {2}",
        domainUid, domainNamespace, MII_BASIC_IMAGE_NAME + ":" + MII_BASIC_IMAGE_TAG);
    createDomainAndVerify(domain, domainNamespace);

    // check admin server pod is ready
    logger.info("Wait for admin server pod {0} to be ready in namespace {1}",
        adminServerPodName, domainNamespace);
    checkPodReadyAndServiceExists(adminServerPodName, domainUid, domainNamespace);
    // check managed server pods are ready
    for (int i = 1; i <= replicaCount; i++) {
      checkPodReadyAndServiceExists(managedServerC1PodNamePrefix + i, domainUid, domainNamespace);
      checkPodReadyAndServiceExists(managedServerC2PodNamePrefix + i, domainUid, domainNamespace);
    }

    return domain;
  }

  private static DomainResource createDomainResource(String domainUid,
                                                     String domNamespace,
                                                     String adminSecretName,
                                                     String encryptionSecretName,
                                                     String configmapName) {
    Map<String, String> keyValueMap = new HashMap<>();
    keyValueMap.put("testkey", "testvalue");

    DomainResource domain = new DomainResource()
        .apiVersion(DOMAIN_API_VERSION)
        .kind("Domain")
        .metadata(new V1ObjectMeta()
            .name(domainUid)
            .namespace(domNamespace))
        .spec(new DomainSpec()
            .domainUid(domainUid)
            .domainHomeSourceType("FromModel")
            .replicas(replicaCount)
            .maxClusterUnavailable(maxClusterUnavailable)
            .maxClusterConcurrentStartup(maxClusterConcurrentStartup)
            .maxClusterConcurrentShutdown(maxClusterConcurrentShutdown)
            .image(MII_BASIC_IMAGE_NAME + ":" + MII_BASIC_IMAGE_TAG)
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
                    .value("-Dweblogic.security.SSL.ignoreHostnameVerification=true"))
                .addEnvItem(new V1EnvVar()
                    .name("USER_MEM_ARGS")
                    .value("-Djava.security.egd=file:/dev/./urandom ")))
            .adminServer(new AdminServer()
                .adminChannelPortForwardingEnabled(false)
                .serverService(new ServerService()
                    .annotations(keyValueMap)
                    .labels(keyValueMap))
                .adminService(new AdminService()
                    .addChannelsItem(new Channel()
                        .channelName("default")
                        .nodePort(getNextFreePort()))))
            .configuration(new Configuration()
                .model(new Model()
                    .configMap(configmapName)
                    .domainType("WLS")
                    .runtimeEncryptionSecret(encryptionSecretName))
                .introspectorJobActiveDeadlineSeconds(3000L)));

    setPodAntiAffinity(domain);

    return domain;
  }

  private static void createModelConfigMap(String domainid, String cfgMapName) {
    String yamlString = "topology:\n"
        + "  Cluster:\n"
        + "    'cluster-1':\n"
        + "       DynamicServers: \n"
        + "         ServerTemplate: 'cluster-1-template' \n"
        + "         ServerNamePrefix: 'c1-managed-server' \n"
        + "         DynamicClusterSize: 5 \n"
        + "         MaxDynamicClusterSize: 5 \n"
        + "         CalculatedListenPorts: false \n"
        + "    'cluster-2':\n"
        + "  ServerTemplate:\n"
        + "    'cluster-1-template':\n"
        + "       Cluster: 'cluster-1' \n"
        + "       ListenPort : 8001 \n"
        + "  Server:\n"
        + "    'c2-managed-server1':\n"
        + "       Cluster: 'cluster-2' \n"
        + "       ListenPort : 8001 \n"
        + "    'c2-managed-server2':\n"
        + "       Cluster: 'cluster-2' \n"
        + "       ListenPort : 8001 \n"
        + "    'c2-managed-server3':\n"
        + "       Cluster: 'cluster-2' \n"
        + "       ListenPort : 8001 \n"
        + "    'c2-managed-server4':\n"
        + "       Cluster: 'cluster-2' \n"
        + "       ListenPort : 8001 \n"
        + "    'c2-managed-server5':\n"
        + "       Cluster: 'cluster-2' \n"
        + "       ListenPort : 8001 \n";

    Map<String, String> labels = new HashMap<>();
    labels.put("weblogic.domainUid", domainid);
    Map<String, String> data = new HashMap<>();
    data.put("model.cluster.yaml", yamlString);

    V1ConfigMap configMap = new V1ConfigMap()
        .data(data)
        .metadata(new V1ObjectMeta()
            .labels(labels)
            .name(cfgMapName)
            .namespace(domainNamespace));
    boolean cmCreated = assertDoesNotThrow(() -> createConfigMap(configMap),
        String.format("Can't create ConfigMap %s", cfgMapName));
    assertTrue(cmCreated, String.format("createConfigMap failed %s", cfgMapName));
  }

  private static boolean findStringInDomainStatusServerHealth(String regex) {
    // get the domain server health message
    StringBuffer getDomainInfoCmd = new StringBuffer(KUBERNETES_CLI + " get domain/");
    getDomainInfoCmd
        .append(domainUid)
        .append(" -n ")
        .append(domainNamespace)
        .append(" -o jsonpath=\"{.status.servers[2,3,4]['health.activationTime', 'serverName', 'state']}\"");
    getLogger().info("Command to get domain status message: " + getDomainInfoCmd);

    CommandParams params = new CommandParams().defaults();
    params.command(getDomainInfoCmd.toString());
    ExecResult execResult = Command.withParams(params).executeAndReturnResult();
    logger.info("Search: {0} in Domain status servers {1}", regex, execResult.stdout());

    // match regex in domain server health message
    Pattern pattern = Pattern.compile(regex);
    Matcher matcher = pattern.matcher(execResult.stdout());
    boolean matchFound = matcher.find();

    if (matchFound) {
      logger.info("Found match: {0} {1} {2}", matcher.group(1), matcher.group(2), matcher.group(3));
    } else {
      logger.info("No match found");
    }

    return matchFound;
  }

  private OffsetDateTime verifyServersStartedConcurrently(String managedServerPodNamePrefix,
                                                          int startPodNum,
                                                          int endPodNum) {
    final int deltaValue = 120;
    OffsetDateTime msPodCreationTime = null;

    // get managed server pod creation time
    ArrayList<Long> podCreationTimestampList = new ArrayList<Long>();
    for (int i = startPodNum; i <= endPodNum; i++) {
      try {
        msPodCreationTime =
            getPodCreationTimestamp(domainNamespace, "", managedServerPodNamePrefix + i);
        logger.info("Managed server: {0} start at: {1}",
            managedServerPodNamePrefix + i, msPodCreationTime.getLong(ChronoField.SECOND_OF_DAY));

        podCreationTimestampList.add(Math.abs(msPodCreationTime.getLong(ChronoField.SECOND_OF_DAY)));
      } catch (Exception ex) {
        logger.info("Faild to get pod creation time: {0}", ex.getMessage());
      }
    }

    // verify that the Operator starts up (endPodNum - startPodNum) managed servers in the cluster concurrently
    for (int i = 0; i <= (endPodNum - startPodNum); i++) {
      assertTrue(Math.abs(podCreationTimestampList.get(i)
          - podCreationTimestampList.get(0)) < deltaValue,
              String.format("Two managed servers %s and %s failed to start concurrently",
                  managedServerPodNamePrefix + (i + 1), managedServerPodNamePrefix + (i + 2)));

      logger.info("Managed servers {0} and {1} started concurrently at {2}. Test passed",
          managedServerPodNamePrefix + (i + 1), managedServerPodNamePrefix + (i + 2),
              podCreationTimestampList.get(i));
    }

    return msPodCreationTime;
  }

  private void verifyServersStartedSequentially(String managedServerPodNamePrefix,
                                                int startPodNum,
                                                int endPodNum) {
    final int deltaValue = 15; // seconds
    // get managed server pod creation time
    ArrayList<Long> podCreationTimestampList = new ArrayList<Long>();
    for (int i = startPodNum; i <= endPodNum; i++) {
      try {
        OffsetDateTime msPodCreationTime =
            getPodCreationTimestamp(domainNamespace, "", managedServerPodNamePrefix + i);
        podCreationTimestampList.add(Math.abs(msPodCreationTime.getLong(ChronoField.SECOND_OF_DAY)));
      } catch (Exception ex) {
        logger.info("Faild to get pod creation time: {0}", ex.getMessage());
      }
    }

    // verify that the Operator starts up (endPodNum - startPodNum) managed servers in the cluster sequentially
    for (int i = 1; i <= (endPodNum - startPodNum); i++) {

      logger.info("Managed servers {0} started at: {1}",
          managedServerPodNamePrefix + (i + 1), podCreationTimestampList.get(i - 1));
      logger.info("Managed servers {0} started at: {1}",
          managedServerPodNamePrefix + (i + 2), podCreationTimestampList.get(i));
      assertTrue(Math.abs(podCreationTimestampList.get(i)
          - podCreationTimestampList.get(0)) > deltaValue,
              String.format("Two managed servers %s and %s failed to start sequentially",
                  managedServerPodNamePrefix + (i + 1), managedServerPodNamePrefix + (i + 2)));

    }
  }

  private void rollingRestartDomainAndVerify(String managedServerPodNamePrefix,
                                             int podNum,
                                             boolean concurrent) {
    LinkedHashMap<String, OffsetDateTime> pods = new LinkedHashMap<>();

    // get the creation time of the server pods before patching
    OffsetDateTime adminPodCreationTime = getPodCreationTime(domainNamespace, adminServerPodName);
    OffsetDateTime msPodCreationTimeBf = assertDoesNotThrow(
        () -> getPodCreationTimestamp(domainNamespace, "", managedServerPodNamePrefix + 1));

    // patch domain with a new restartVersion to kick off rolling restart
    String newRestartVersion = patchDomainResourceWithNewRestartVersion(domainUid, domainNamespace);
    logger.info("New restart version : {0}", newRestartVersion);

    pods.put(adminServerPodName, adminPodCreationTime);
    for (int i = 1; i <= podNum; i++) {
      pods.put(managedServerPodNamePrefix + i,
          getPodCreationTime(domainNamespace, managedServerPodNamePrefix + i));
    }
    assertTrue(verifyRollingRestartOccurred(pods, podNum, domainNamespace),
        "Rolling restart managed server in " + cluster1Name + " failed");

    if (concurrent) {
      OffsetDateTime msPodCreationTimeAf =
          verifyServersStartedConcurrently(managedServerPodNamePrefix, 1, maxClusterUnavailable);

      assertNotEquals(0, msPodCreationTimeAf.compareTo(msPodCreationTimeBf),
          "Pod creation time should be different before and after restart");

      logger.info("Pod creation time before restart: {0} and after restart: {1}",
          msPodCreationTimeBf, msPodCreationTimeAf);
    } else {
      logger.info("Sequentially : {0}", managedServerPodNamePrefix);
      OffsetDateTime msPod1CreationTimeAf = assertDoesNotThrow(
          () -> getPodCreationTimestamp(domainNamespace, "", managedServerPodNamePrefix + 1));
      OffsetDateTime msPod2CreationTimeAf = assertDoesNotThrow(
          () -> getPodCreationTimestamp(domainNamespace, "", managedServerPodNamePrefix + 2));

      assertNotEquals(0, msPod1CreationTimeAf.compareTo(msPod2CreationTimeAf),
          "Pod creation time should be different after restart");
    }
  }

  private void configAndScaleCluster(String clusterName,
                                     String clusterRes,
                                     String managedServerPodNamePrefix,
                                     String configKey,
                                     int configValue,
                                     int newReplicas) {
    // Config maxConcurrentShutdown or maxConcurrentStartup in cluster resource
    StringBuffer patchStr = new StringBuffer()
        .append("[{\"op\": \"replace\", \"path\": \"/spec/")
        .append(configKey)
        .append("\", \"value\": ")
        .append(configValue)
        .append("}]");
    patchClusterResourceAndVerify(domainNamespace, clusterRes, patchStr.toString());

    // scale up the cluster by newReplicas
    logger.info("Scaling down the cluster {0} in namespace {1} to set the replicas to {2}",
        clusterName, domainNamespace, newReplicas);
    boolean scalingSuccess = assertDoesNotThrow(() -> scaleCluster(clusterRes, domainNamespace, newReplicas),
        String.format("failed to scale down cluster %s in namespace %s", clusterName, domainNamespace));
    assertTrue(scalingSuccess, String.format("Cluster scaling down failed "
        + "for cluster %s in domain %s and namespace %s", clusterRes, domainUid, domainNamespace));

    // check managed server pods are ready
    for (int i = 2; i <= newReplicas; i++) {
      checkPodReadyAndServiceExists(managedServerPodNamePrefix + i, domainUid, domainNamespace);
    }
  }

  private void verifyShutdownConcurrently(String managedServerPodNamePrefix,
                                          String managedServerNamePrefix,
                                          int origReplicas,
                                          int newReplicas) {
    // verify that two managed servers in each cluster are down
    for (int i = origReplicas; i > newReplicas; i--) {
      logger.info("Wait for managed pod {0} to be deleted in namespace {1}",
          managedServerPodNamePrefix + i, domainNamespace);
      checkPodDeleted(managedServerPodNamePrefix + i, domainUid, domainNamespace);
    }

    // verify that two managed servers in each cluster are shutdown concurrently
    StringBuffer serverStateRegex =
        new StringBuffer("([0-9]{4}-[0-9]{2}-[0-9]{2}.*[0-9]{2}:[0-9]{2}:[0-9]{2}).*(");

    for (int i = newReplicas + 1; i <= origReplicas; i++) {
      serverStateRegex.append(managedServerNamePrefix + i).append("\\s*");
    }

    serverStateRegex.append(").*(SHUTDOWN\\s*SHUTDOWN).*");

    testUntil(() -> findStringInDomainStatusServerHealth(serverStateRegex.toString()),
        logger, "The Operator shutdown 2 managed servers concurrently as expected");
  }

  private void patchDomainResourceWithReplicasAndVerify(ArrayList<String>  managedServerPodNamePrefixList,
                                                        int startPodNum,
                                                        int endPodNum,
                                                        int replicas,
                                                        boolean scaleup) {
    // patch in domain resource with replicas
    StringBuffer patchStr =
        new StringBuffer("[{\"op\": \"replace\", \"path\": \"/spec/replicas\", \"value\": ")
            .append(replicas).append("}]");
    assertTrue(patchDomainResource(domainUid, domainNamespace, patchStr), "Failed to patch domain");
    patchDomainResourceWithNewIntrospectVersion(domainUid, domainNamespace);

    // verify the patch results, server are scaled up or down correctly
    for (int i = startPodNum, j = 0; i <= endPodNum; i++, j++) {
      if (scaleup) {
        for (String managedServerPodNamePrefix : managedServerPodNamePrefixList) {
          logger.info("Wait for managed pod {0} to be ready in namespace {1}",
              managedServerPodNamePrefix + i, domainNamespace);
          checkPodReadyAndServiceExists(managedServerPodNamePrefix + i, domainUid, domainNamespace);
        }
      } else {
        for (String managedServerPodNamePrefix : managedServerPodNamePrefixList) {
          logger.info("Wait for managed pod {0} to be deleted in namespace {1}",
              managedServerPodNamePrefix + i, domainNamespace);
          checkPodDeleted(managedServerPodNamePrefix + i, domainUid, domainNamespace);
        }
      }
    }
  }

  private void restoreTestEnv(ArrayList<String>  clusterResources) {
    // delete CR referenced in domain resource
    clusterResources.forEach(
        (clusterResource) -> deleteClusterCustomResourceAndVerify(clusterResource,domainNamespace));

    // remove the cluster resource from domain resource
    logger.info("Patch the domain resource to remove cluster resource");
    StringBuffer patchStr = new StringBuffer("[{\"op\": \"remove\",\"path\": \"/spec/clusters/0\"}]");
    logger.info("Updating domain configuration using patch string: {0}\n", patchStr);
    clusterResources.forEach((clusterResource) -> patchDomainResource(domainUid, domainNamespace, patchStr));

    // restore replicas at domain level bask to 4
    ArrayList<String> managedServerPodNamePrefixList =
        new ArrayList<String>(List.of(managedServerC1PodNamePrefix, managedServerC2PodNamePrefix));
    boolean scaleup = true;
    patchDomainResourceWithReplicasAndVerify(managedServerPodNamePrefixList, 1, replicaCount, replicaCount, scaleup);
  }
}
