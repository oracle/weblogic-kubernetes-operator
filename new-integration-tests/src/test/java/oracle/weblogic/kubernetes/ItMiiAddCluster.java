// Copyright (c) 2020, Oracle Corporation and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.weblogic.kubernetes;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;

import com.google.gson.JsonObject;
import io.kubernetes.client.custom.V1Patch;
import io.kubernetes.client.openapi.ApiException;
import io.kubernetes.client.openapi.models.V1ConfigMap;
import io.kubernetes.client.openapi.models.V1EnvVar;
import io.kubernetes.client.openapi.models.V1LocalObjectReference;
import io.kubernetes.client.openapi.models.V1ObjectMeta;
import io.kubernetes.client.openapi.models.V1Secret;
import io.kubernetes.client.openapi.models.V1SecretReference;
import io.kubernetes.client.openapi.models.V1ServiceAccount;
import oracle.weblogic.domain.AdminServer;
import oracle.weblogic.domain.AdminService;
import oracle.weblogic.domain.Channel;
import oracle.weblogic.domain.Cluster;
import oracle.weblogic.domain.Configuration;
import oracle.weblogic.domain.Domain;
import oracle.weblogic.domain.DomainSpec;
import oracle.weblogic.domain.Model;
import oracle.weblogic.domain.ServerPod;
import oracle.weblogic.kubernetes.actions.impl.OperatorParams;
import oracle.weblogic.kubernetes.actions.impl.primitive.HelmParams;
import oracle.weblogic.kubernetes.annotations.IntegrationTest;
import oracle.weblogic.kubernetes.annotations.Namespaces;
import oracle.weblogic.kubernetes.annotations.tags.MustNotRunInParallel;
import oracle.weblogic.kubernetes.annotations.tags.Slow;
import oracle.weblogic.kubernetes.extensions.LoggedTest;
import oracle.weblogic.kubernetes.utils.ExecResult;
import org.awaitility.core.ConditionFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import static java.util.concurrent.TimeUnit.MINUTES;
import static java.util.concurrent.TimeUnit.SECONDS;
import static oracle.weblogic.kubernetes.TestConstants.DOMAIN_API_VERSION;
import static oracle.weblogic.kubernetes.TestConstants.DOMAIN_VERSION;
import static oracle.weblogic.kubernetes.TestConstants.K8S_NODEPORT_HOST;
import static oracle.weblogic.kubernetes.TestConstants.MII_BASIC_IMAGE_NAME;
import static oracle.weblogic.kubernetes.TestConstants.MII_BASIC_IMAGE_TAG;
import static oracle.weblogic.kubernetes.TestConstants.OPERATOR_CHART_DIR;
import static oracle.weblogic.kubernetes.TestConstants.OPERATOR_RELEASE_NAME;
import static oracle.weblogic.kubernetes.TestConstants.REPO_EMAIL;
import static oracle.weblogic.kubernetes.TestConstants.REPO_PASSWORD;
import static oracle.weblogic.kubernetes.TestConstants.REPO_REGISTRY;
import static oracle.weblogic.kubernetes.TestConstants.REPO_SECRET_NAME;
import static oracle.weblogic.kubernetes.TestConstants.REPO_USERNAME;
import static oracle.weblogic.kubernetes.actions.ActionConstants.MODEL_DIR;
import static oracle.weblogic.kubernetes.actions.TestActions.createConfigMap;
import static oracle.weblogic.kubernetes.actions.TestActions.createDockerConfigJson;
import static oracle.weblogic.kubernetes.actions.TestActions.createDomainCustomResource;
import static oracle.weblogic.kubernetes.actions.TestActions.createSecret;
import static oracle.weblogic.kubernetes.actions.TestActions.createServiceAccount;
import static oracle.weblogic.kubernetes.actions.TestActions.deleteDomainCustomResource;
import static oracle.weblogic.kubernetes.actions.TestActions.getOperatorImageName;
import static oracle.weblogic.kubernetes.actions.TestActions.getServiceNodePort;
import static oracle.weblogic.kubernetes.actions.TestActions.installOperator;
import static oracle.weblogic.kubernetes.actions.TestActions.patchDomainCustomResource;
import static oracle.weblogic.kubernetes.actions.TestActions.patchDomainResourceWithNewRestartVersion;
import static oracle.weblogic.kubernetes.assertions.TestAssertions.domainExists;
import static oracle.weblogic.kubernetes.assertions.TestAssertions.isHelmReleaseDeployed;
import static oracle.weblogic.kubernetes.assertions.TestAssertions.operatorIsReady;
import static oracle.weblogic.kubernetes.assertions.TestAssertions.podDoesNotExist;
import static oracle.weblogic.kubernetes.assertions.TestAssertions.verifyRollingRestartOccurred;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.checkPodReady;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.checkServiceExists;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.getPodCreationTime;
import static oracle.weblogic.kubernetes.utils.ExecCommand.exec;
import static org.awaitility.Awaitility.with;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DisplayName("Test to add a cluster to model in image domain")
@IntegrationTest
class ItMiiAddCluster implements LoggedTest {

  private static HelmParams opHelmParams = null;
  private static V1ServiceAccount serviceAccount = null;
  private String serviceAccountName = null;
  private static String opNamespace = null;
  private static String operatorImage = null;
  private static String domainNamespace = null;
  private static ConditionFactory withStandardRetryPolicy = null;
  private static String dockerConfigJson = "";

  private static int replicaCount = 2;
  private static final String domainUid = "miiaddcluster";

  private StringBuffer checkCluster = null;
  private V1Patch patch = null;

  private static Map<String, Object> secretNameMap;

  private final String adminServerPodName = domainUid + "-admin-server";
  private final String managedServerPrefix = domainUid + "-managed-server";

  /**
   * Install Operator.
   * Create domain resource defintion.
   * @param namespaces list of namespaces created by the IntegrationTestWatcher by the
   JUnit engine parameter resolution mechanism
   */
  @BeforeAll
  public static void initAll(@Namespaces(2) List<String> namespaces) {
    // create standard, reusable retry/backoff policy
    withStandardRetryPolicy = with().pollDelay(2, SECONDS)
        .and().with().pollInterval(10, SECONDS)
        .atMost(5, MINUTES).await();

    // get a new unique opNamespace
    logger.info("Creating unique namespace for Operator");
    assertNotNull(namespaces.get(0), "Namespace list is null");
    opNamespace = namespaces.get(0);

    logger.info("Creating unique namespace for Domain");
    assertNotNull(namespaces.get(1), "Namespace list is null");
    domainNamespace = namespaces.get(1);

    // Create a service account for the unique opNamespace
    logger.info("Creating service account");
    String serviceAccountName = opNamespace + "-sa";
    assertDoesNotThrow(() -> createServiceAccount(new V1ServiceAccount()
        .metadata(
            new V1ObjectMeta()
                .namespace(opNamespace)
                .name(serviceAccountName))));
    logger.info("Created service account: {0}", serviceAccountName);

    // get Operator image name
    operatorImage = getOperatorImageName();
    assertFalse(operatorImage.isEmpty(), "Operator image name can not be empty");
    logger.info("Operator image name {0}", operatorImage);

    // Create docker registry secret in the operator namespace to pull the image from repository
    logger.info("Creating docker registry secret in namespace {0}", opNamespace);
    JsonObject dockerConfigJsonObject = createDockerConfigJson(
        REPO_USERNAME, REPO_PASSWORD, REPO_EMAIL, REPO_REGISTRY);
    dockerConfigJson = dockerConfigJsonObject.toString();

    // Create the V1Secret configuration
    logger.info("Creating repo secret {0}", REPO_SECRET_NAME);
    V1Secret repoSecret = new V1Secret()
        .metadata(new V1ObjectMeta()
            .name(REPO_SECRET_NAME)
            .namespace(opNamespace))
        .type("kubernetes.io/dockerconfigjson")
        .putDataItem(".dockerconfigjson", dockerConfigJson.getBytes());

    boolean secretCreated = assertDoesNotThrow(() -> createSecret(repoSecret),
        String.format("createSecret failed for %s", REPO_SECRET_NAME));
    assertTrue(secretCreated, String.format("createSecret failed while creating secret %s in namespace",
                  REPO_SECRET_NAME, opNamespace));

    // map with secret
    secretNameMap = new HashMap<String, Object>();
    secretNameMap.put("name", REPO_SECRET_NAME);
    // helm install parameters
    opHelmParams = new HelmParams()
        .releaseName(OPERATOR_RELEASE_NAME)
        .namespace(opNamespace)
        .chartDir(OPERATOR_CHART_DIR);

    // Operator chart values to override
    OperatorParams opParams =
        new OperatorParams()
            .helmParams(opHelmParams)
            .image(operatorImage)
            .imagePullSecrets(secretNameMap)
            .domainNamespaces(Arrays.asList(domainNamespace))
            .serviceAccount(serviceAccountName);

    // install Operator
    logger.info("Installing Operator in namespace {0}", opNamespace);
    assertTrue(installOperator(opParams),
        String.format("Operator install failed in namespace %s", opNamespace));
    logger.info("Operator installed in namespace {0}", opNamespace);

    // list helm releases matching Operator release name in operator namespace
    logger.info("Checking Operator release {0} status in namespace {1}",
        OPERATOR_RELEASE_NAME, opNamespace);
    assertTrue(isHelmReleaseDeployed(OPERATOR_RELEASE_NAME, opNamespace),
        String.format("Operator release %s is not in deployed status in namespace %s",
            OPERATOR_RELEASE_NAME, opNamespace));
    logger.info("Operator release {0} status is deployed in namespace {1}",
        OPERATOR_RELEASE_NAME, opNamespace);

    // check operator is running
    logger.info("Check Operator pod is running in namespace {0}", opNamespace);
    withStandardRetryPolicy
        .conditionEvaluationListener(
            condition -> logger.info("Waiting for operator to be running in namespace {0} "
                    + "(elapsed time {1}ms, remaining time {2}ms)",
                opNamespace,
                condition.getElapsedTimeInMS(),
                condition.getRemainingTimeInMS()))
        .until(operatorIsReady(opNamespace));

    // Create the repo secret to pull the image
    assertDoesNotThrow(() -> createRepoSecret(domainNamespace),
            String.format("createSecret failed for %s", REPO_SECRET_NAME));

    // create secret for admin credentials
    logger.info("Create secret for admin credentials");
    String adminSecretName = "weblogic-credentials";
    assertDoesNotThrow(() -> createDomainSecret(adminSecretName,"weblogic",
            "welcome1", domainNamespace),
            String.format("createSecret failed for %s", adminSecretName));

    // create encryption secret
    logger.info("Create encryption secret");
    String encryptionSecretName = "encryptionsecret";
    assertDoesNotThrow(() -> createDomainSecret(encryptionSecretName, "weblogicenc",
            "weblogicenc", domainNamespace),
             String.format("createSecret failed for %s", encryptionSecretName));
    // create the domain CR with no configmap
    createDomainResource(domainUid, domainNamespace, adminSecretName,
        REPO_SECRET_NAME, encryptionSecretName,
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
  }

  /**
   * Verify all server pods are running.
   * Verify all k8s services for all servers are created.
   */
  @BeforeEach
  public void beforeEach() {

    // check admin server pod is ready
    logger.info("Wait for admin server pod {0} to be ready in namespace {1}",
        adminServerPodName, domainNamespace);
    checkPodReady(adminServerPodName, domainUid, domainNamespace);
    logger.info("Check admin service {0} is created in namespace {1}",
        adminServerPodName, domainNamespace);
    checkServiceExists(adminServerPodName, domainNamespace);

    // check managed server pods are ready
    for (int i = 1; i <= replicaCount; i++) {
      logger.info("Wait for managed server pod {0} to be ready in namespace {1}",
          managedServerPrefix + i, domainNamespace);
      checkPodReady(managedServerPrefix + i, domainUid, domainNamespace);
    }

    // check managed server services created
    for (int i = 1; i <= replicaCount; i++) {
      logger.info("Check managed server service {0} is created in namespace {1}",
          managedServerPrefix + i, domainNamespace);
      checkServiceExists(managedServerPrefix + i, domainNamespace);
    }
  }

  /**
   * Patch the domain resource with the configmap to add a cluster.
   * Update the restart version of the domain resource to 1.
   * Verify rolling restart of the domain by comparing PodCreationTimestamp 
   * before and after rolling restart.
   * Verify servers from new cluster are not in running state, because i
   * the spec level replica count to zero(default).
   */
  @Test
  @Order(1)
  @DisplayName("Add a cluster to model in image domain with default replica count")
  @Slow
  @MustNotRunInParallel
  public void testAddMiiClusteriWithNoReplica() {

    // This test uses the WebLogic domain created in BeforeAll method
    // BeforeEach method ensures that the server pods are running

    String configMapName = "noreplicaconfigmap";
    createClusterConfigMap(configMapName, "model.config.cluster.yaml");

    LinkedHashMap<String, String> pods = new LinkedHashMap<>();

    // get the creation time of the server pods before patching
    String adminPodCreationTime = getPodCreationTime(domainNamespace, adminServerPodName);
    pods.put(adminServerPodName, adminPodCreationTime);
    for (int i = 1; i <= replicaCount; i++) {
      pods.put(managedServerPrefix + i, getPodCreationTime(domainNamespace, managedServerPrefix + i));
    }
    
    StringBuffer patchStr = null;
    patchStr = new StringBuffer("[{");
    patchStr.append("\"op\": \"replace\",")
        .append(" \"path\": \"/spec/configuration/model/configMap\",")
        .append(" \"value\":  \"" + configMapName + "\"")
        .append(" }]");
    logger.log(Level.INFO, "Configmap patch string: {0}", patchStr);

    patch = new V1Patch(new String(patchStr));
    boolean cmPatched = assertDoesNotThrow(() ->
            patchDomainCustomResource(domainUid, domainNamespace, patch, "application/json-patch+json"),
        "patchDomainCustomResource(configMap)  failed ");
    assertTrue(cmPatched, "patchDomainCustomResource(configMap) failed");

    String newRestartVersion = patchDomainResourceWithNewRestartVersion(domainUid, domainNamespace);
    logger.log(Level.INFO, "New restart version : {0}", newRestartVersion);
    
    assertTrue(assertDoesNotThrow(
        () -> (verifyRollingRestartOccurred(pods, 1, domainNamespace)),
         "More than one pod was restarted at same time"),
        "Rolling restart failed");

    // The ServerNamePrefix for the new configured cluster is config-server
    // Make sure the managed server from new cluster is not running

    String newServerPodName = domainUid + "-config-server1";
    checkPodNotCreated(newServerPodName, domainUid, domainNamespace);

    boolean isServerConfigured = checkManagedServerConfiguration("config-server1");
    assertTrue(isServerConfigured, "Could not find new managed server configuration");
    logger.info("Found new managed server configuration");
  }

  /**
   * Create a configmap with a sparse model file to add a dynamic cluster.
   * Patch the domain resource with the configmap.
   * Update the restart version of the domain resource to 2.
   * Patch the domain resource with the spec/replicas set to 1.
   * Verify rolling restart of the domain by comparing PodCreationTimestamp 
   * before and after rolling restart.
   * Verify servers from new cluster are in running state.
   */
  @Test
  @Order(2)
  @DisplayName("Add a dynamic cluster to model in image domain")
  @Slow
  @MustNotRunInParallel
  public void testAddMiiDynamicCluster() {

    // This test uses the WebLogic domain created in BeforeAll method
    // BeforeEach method ensures that the server pods are running
    
    String configMapName = "dynamicclusterconfigmap";
    createClusterConfigMap(configMapName, "model.dynamic.cluster.yaml");

    LinkedHashMap<String, String> pods = new LinkedHashMap<>();
    // get the creation time of the admin server pod before patching
    String adminPodCreationTime = getPodCreationTime(domainNamespace, adminServerPodName);
    pods.put(adminServerPodName, adminPodCreationTime);
    // get the creation time of the managed server pods before patching
    for (int i = 1; i <= replicaCount; i++) {
      pods.put(managedServerPrefix + i, getPodCreationTime(domainNamespace, managedServerPrefix + i));
    }

    StringBuffer patchStr = null;
    patchStr = new StringBuffer("[{");
    patchStr.append("\"op\": \"replace\",")
        .append(" \"path\": \"/spec/configuration/model/configMap\",")
        .append(" \"value\":  \"" + configMapName + "\"")
        .append(" }]");
    logger.log(Level.INFO, "Configmap patch string: {0}", patchStr);

    patch = new V1Patch(new String(patchStr));
    boolean cmPatched = assertDoesNotThrow(() ->
            patchDomainCustomResource(domainUid, domainNamespace, patch, "application/json-patch+json"),
        "patchDomainCustomResource(configMap)  failed ");
    assertTrue(cmPatched, "patchDomainCustomResource(configMap) failed");
    
    patchStr = new StringBuffer("[{");
    patchStr.append(" \"op\": \"replace\",")
        .append(" \"path\": \"/spec/replicas\",")
        .append(" \"value\": 1")
        .append(" }]");
    logger.log(Level.INFO, "Replicas patch string: {0}", patchStr);

    patch = new V1Patch(new String(patchStr));
    boolean repilcaPatched = assertDoesNotThrow(() ->
            patchDomainCustomResource(domainUid, domainNamespace, patch, "application/json-patch+json"),
        "patchDomainCustomResource(restartVersion)  failed ");
    assertTrue(repilcaPatched, "patchDomainCustomResource(repilcas) failed");

    String newRestartVersion = patchDomainResourceWithNewRestartVersion(domainUid, domainNamespace);
    logger.log(Level.INFO, "New restart version : {0}", newRestartVersion);
    
    // Check if the admin server pod has been restarted 
    // by comparing the PodCreationTime before and after rolling restart
    
    assertTrue(assertDoesNotThrow(
        () -> (verifyRollingRestartOccurred(pods, 1, domainNamespace)),
         "More than one pod was restarted at same time"),
        "Rolling restart failed");

    // The ServerNamePrefix for the new dynamic cluster is dynamic-server
    // Make sure the managed server from the new cluster is running
    
    String newServerPodName = domainUid + "-dynamic-server1";
    checkPodReady(newServerPodName, domainUid, domainNamespace);
    checkServiceExists(newServerPodName, domainNamespace);

    boolean isServerConfigured = checkManagedServerConfiguration("dynamic-server1");
    assertTrue(isServerConfigured, "Could not find new managed server configuration");
    logger.info("Found new managed server configuration");
  }

  /**
   * Create a configmap with a sparse model file to add a configured cluster.
   * Patch the domain resource with the configmap.
   * Update the restart version of the domain resource to 3.
   * Patch the domain resource with the spec/replicas set to 1.
   * Verify rolling restart of the domain by comparing PodCreationTimestamp 
   * before and after rolling restart.
   * Verify servers from new cluster are in running state.
   */
  @Test
  @Order(3)
  @DisplayName("Add a configured cluster to model in image domain")
  @Slow
  @MustNotRunInParallel
  public void testAddMiiConfiguredCluster() {
 
    // This test uses the WebLogic domain created in BeforeAll method
    // BeforeEach method ensures that the server pods are running

    String configMapName = "configclusterconfigmap";
    createClusterConfigMap(configMapName, "model.config.cluster.yaml");

    LinkedHashMap<String, String> pods = new LinkedHashMap<>();

    // get the creation time of the admin server pod before patching
    String adminPodCreationTime = getPodCreationTime(domainNamespace, adminServerPodName);
    // get the creation time of the managed server pods before patching
    pods.put(adminServerPodName, adminPodCreationTime);
    for (int i = 1; i <= replicaCount; i++) {
      pods.put(managedServerPrefix + i, getPodCreationTime(domainNamespace,   managedServerPrefix + i));
    }

    StringBuffer patchStr = null;
    patchStr = new StringBuffer("[{");
    patchStr.append("\"op\": \"replace\",")
        .append(" \"path\": \"/spec/configuration/model/configMap\",")
        .append(" \"value\":  \"" + configMapName + "\"")
        .append(" }]");
    logger.log(Level.INFO, "Configmap patch string: {0}", patchStr);

    patch = new V1Patch(new String(patchStr));
    boolean cmPatched = assertDoesNotThrow(() ->
            patchDomainCustomResource(domainUid, domainNamespace, patch, "application/json-patch+json"),
        "patchDomainCustomResource(configMap)  failed ");
    assertTrue(cmPatched, "patchDomainCustomResource(configMap) failed");
    
    patchStr = new StringBuffer("[{");
    patchStr.append(" \"op\": \"replace\",")
        .append(" \"path\": \"/spec/replicas\",")
        .append(" \"value\": 1")
        .append(" }]");
    logger.log(Level.INFO, "Replicas patch string: {0}", patchStr);

    patch = new V1Patch(new String(patchStr));
    boolean repilcaPatched = assertDoesNotThrow(() ->
            patchDomainCustomResource(domainUid, domainNamespace, patch, "application/json-patch+json"),
        "patchDomainCustomResource(restartVersion)  failed ");
    assertTrue(repilcaPatched, "patchDomainCustomResource(repilcas) failed");

    String newRestartVersion = patchDomainResourceWithNewRestartVersion(domainUid, domainNamespace);
    logger.log(Level.INFO, "New restart version : {0}", newRestartVersion);
    
    assertTrue(assertDoesNotThrow(
        () -> (verifyRollingRestartOccurred(pods, 1, domainNamespace)),
         "More than one pod was restarted at same time"),
        "Rolling restart failed");

    // The ServerNamePrefix for the new configured cluster is config-server
    // Make sure the managed server from the new cluster is running
    String newServerPodName = domainUid + "-config-server1";
    checkPodReady(newServerPodName, domainUid, domainNamespace);
    checkServiceExists(newServerPodName, domainNamespace);

    boolean isServerConfigured = checkManagedServerConfiguration("config-server1");
    assertTrue(isServerConfigured, "Could not find new managed server configuration");
    logger.info("Found new managed server configuration");
  }

  // This method is needed in this test class, since the cleanup util
  // won't cleanup the images.
  @AfterAll
  void tearDown() {
    // Delete domain custom resource
    logger.info("Delete domain custom resource in namespace {0}", domainNamespace);
    assertDoesNotThrow(() -> deleteDomainCustomResource(domainUid, domainNamespace),
        "deleteDomainCustomResource failed with ApiException");
    logger.info("Deleted Domain Custom Resource " + domainUid + " from " + domainNamespace);
  }

  private static void createRepoSecret(String domNamespace) throws ApiException {
    V1Secret repoSecret = new V1Secret()
            .metadata(new V1ObjectMeta()
                    .name(REPO_SECRET_NAME)
                    .namespace(domNamespace))
            .type("kubernetes.io/dockerconfigjson")
            .putDataItem(".dockerconfigjson", dockerConfigJson.getBytes());

    boolean secretCreated = false;
    try {
      secretCreated = createSecret(repoSecret);
    } catch (ApiException e) {
      logger.info("Exception when calling CoreV1Api#createNamespacedSecret");
      logger.info("Status code: " + e.getCode());
      logger.info("Reason: " + e.getResponseBody());
      logger.info("Response headers: " + e.getResponseHeaders());
      //409 means that the secret already exists - it is not an error, so can proceed
      if (e.getCode() != 409) {
        throw e;
      } else {
        secretCreated = true;
      }

    }
    assertTrue(secretCreated, String.format("create secret failed for %s in namespace %s",
            REPO_SECRET_NAME, domNamespace));
  }

  private static void createDomainSecret(String secretName, String username, String password, String domNamespace)
          throws ApiException {
    Map<String, String> secretMap = new HashMap();
    secretMap.put("username", username);
    secretMap.put("password", password);
    boolean secretCreated = assertDoesNotThrow(() -> createSecret(new V1Secret()
            .metadata(new V1ObjectMeta()
                    .name(secretName)
                    .namespace(domNamespace))
            .stringData(secretMap)), "Create secret failed with ApiException");
    assertTrue(secretCreated, String.format("create secret failed for %s in namespace %s", secretName, domNamespace));

  }

  private static void  createDomainResource(
      String domainUid, String domNamespace, String adminSecretName,
      String repoSecretName, String encryptionSecretName, 
      int replicaCount) {
    // create the domain CR
    Domain domain = new Domain()
            .apiVersion(DOMAIN_API_VERSION)
            .kind("Domain")
            .metadata(new V1ObjectMeta()
                    .name(domainUid)
                    .namespace(domNamespace))
            .spec(new DomainSpec()
                    .domainUid(domainUid)
                    .domainHomeSourceType("FromModel")
                    .image(MII_BASIC_IMAGE_NAME + ":" + MII_BASIC_IMAGE_TAG)
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
                                    .domainType("WLS")
                                    .runtimeEncryptionSecret(encryptionSecretName))
                        .introspectorJobActiveDeadlineSeconds(300L)));

    logger.info("Create domain custom resource for domainUid {0} in namespace {1}",
            domainUid, domNamespace);
    boolean domCreated = assertDoesNotThrow(() -> createDomainCustomResource(domain),
            String.format("Create domain custom resource failed with ApiException for %s in namespace %s",
                    domainUid, domNamespace));
    assertTrue(domCreated, String.format("Create domain custom resource failed with ApiException "
                    + "for %s in namespace %s", domainUid, domNamespace));
  }

  private void checkPodNotCreated(String podName, String domainUid, String domNamespace) {
    withStandardRetryPolicy
        .conditionEvaluationListener(
            condition -> logger.info("Waiting for pod {0} to be not created in namespace {1} "
                    + "(elapsed time {2}ms, remaining time {3}ms)",
                podName,
                domNamespace,
                condition.getElapsedTimeInMS(),
                condition.getRemainingTimeInMS()))
        .until(assertDoesNotThrow(() -> podDoesNotExist(podName, domainUid, domNamespace),
            String.format("podDoesNotExist failed with ApiException for %s in namespace in %s",
                podName, domNamespace)));

  }

  /* 
   * Verify the server MBEAN configuration through rest API.
   * @param managedServer name of the managed server 
   * @returns true if MBEAN is found otherwise false
   **/
  private boolean checkManagedServerConfiguration(String managedServer) {
    ExecResult result = null;
    int adminServiceNodePort = getServiceNodePort(domainNamespace, adminServerPodName + "-external", "default");
    checkCluster = new StringBuffer("status=$(curl --user weblogic:welcome1 ");
    checkCluster.append("http://" + K8S_NODEPORT_HOST + ":" + adminServiceNodePort)
          .append("/management/tenant-monitoring/servers/")
          .append(managedServer)
          .append(" --silent --show-error ")
          .append(" -o /dev/null")
          .append(" -w %{http_code});")
          .append("echo ${status}");
    logger.info("checkManagedServerConfiguration: curl command {0}", new String(checkCluster));
    try {
      result = exec(new String(checkCluster), true);
    } catch (Exception ex) {
      logger.info("Exception in checkManagedServerConfiguration() {0}", ex);
      return false;
    }
    logger.info("checkManagedServerConfiguration: curl command returned {0}", result.toString());
    if (result.stdout().equals("200")) {
      return true;
    } else {
      return false;
    }
  }

  // Crate a ConfigMap with a model file to add a new WebLogic cluster
  private void createClusterConfigMap(String configMapName, String modelFile) {
    Map<String, String> labels = new HashMap<>();
    labels.put("weblogic.domainUid", domainUid);
    String dsModelFile =  String.format("%s/%s", MODEL_DIR,modelFile);
    Map<String, String> data = new HashMap<>();
    String cmData = null;
    cmData = assertDoesNotThrow(() -> Files.readString(Paths.get(dsModelFile)),
        String.format("readString operation failed for %s", dsModelFile));
    assertNotNull(cmData, String.format("readString() operation failed while creating ConfigMap %s", configMapName));
    data.put(modelFile, cmData);

    V1ObjectMeta meta = new V1ObjectMeta()
        .labels(labels)
        .name(configMapName)
        .namespace(domainNamespace);
    V1ConfigMap configMap = new V1ConfigMap()
        .data(data)
        .metadata(meta);

    boolean cmCreated = assertDoesNotThrow(() -> createConfigMap(configMap),
        String.format("Can't create ConfigMap %s", configMapName));
    assertTrue(cmCreated, String.format("createConfigMap failed while creating ConfigMap %s", configMapName));
  }     

}
