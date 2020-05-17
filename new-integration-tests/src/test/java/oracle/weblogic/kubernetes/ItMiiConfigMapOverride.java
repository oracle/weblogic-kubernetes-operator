// Copyright (c) 2020, Oracle Corporation and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.weblogic.kubernetes;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.HashMap;
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
import static oracle.weblogic.kubernetes.actions.TestActions.execCommand;
import static oracle.weblogic.kubernetes.actions.TestActions.getAdminServiceNodePort;
import static oracle.weblogic.kubernetes.actions.TestActions.getOperatorImageName;
import static oracle.weblogic.kubernetes.actions.TestActions.getPodCreationTimestamp;
import static oracle.weblogic.kubernetes.actions.TestActions.installOperator;
import static oracle.weblogic.kubernetes.actions.TestActions.patchDomainCustomResource;
import static oracle.weblogic.kubernetes.assertions.TestAssertions.domainExists;
import static oracle.weblogic.kubernetes.assertions.TestAssertions.isHelmReleaseDeployed;
import static oracle.weblogic.kubernetes.assertions.TestAssertions.isPodRestarted;
import static oracle.weblogic.kubernetes.assertions.TestAssertions.operatorIsReady;
import static oracle.weblogic.kubernetes.assertions.TestAssertions.podExists;
import static oracle.weblogic.kubernetes.assertions.TestAssertions.podReady;
import static oracle.weblogic.kubernetes.assertions.TestAssertions.serviceExists;
import static oracle.weblogic.kubernetes.utils.ExecCommand.exec;
import static org.awaitility.Awaitility.with;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DisplayName("Test to update a model in image domain with a configmap")
@IntegrationTest
class ItMiiConfigMapOverride implements LoggedTest {

  private static final String READ_STATE_COMMAND = "/weblogic-operator/scripts/readState.sh";

  private static HelmParams opHelmParams = null;
  private static V1ServiceAccount serviceAccount = null;
  private String serviceAccountName = null;
  private static String opNamespace = null;
  private static String operatorImage = null;
  private static String domainNamespace = null;
  private static ConditionFactory withStandardRetryPolicy = null;
  private static String dockerConfigJson = "";

  private String domainUid = "miicfgoverride";
  private StringBuffer checkJdbc = null;
  private V1Patch patch = null;

  private static Map<String, Object> secretNameMap;

  /**
   * Install Operator.
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
  }

  /**
   * Start a WebLogic domain with out any pre-defined configmap.
   * Create a configmap with a sparse JDBC model file.
   * Patch the domain resource with the configmap which is targeted to cluster
   * Update the restart version of the domain resource
   * Verify rolling restart of the domain by comparing PodCreationTimestamp before and after rolling restart
   * Verify the DataSource configuration using Rest API call to admin server
   */
  @Test
  @Order(1)
  @DisplayName("Add a JDBC DataSource to a model in image domain")
  @Slow
  @MustNotRunInParallel
  public void testCreateMiiConfigMapDomain() {
    final String adminServerPodName = domainUid + "-admin-server";
    final String managedServerPrefix = domainUid + "-managed-server";
    final int replicaCount = 2;

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

    Map<String, String> labels = new HashMap<>();
    labels.put("weblogic.domainUid", domainUid);
    String dsModelFile = MODEL_DIR + "/model.jdbc.yaml";
    Map<String, String> data = new HashMap<>();
    String configMapName = "dsconfigmap";
    String cmData = null;
    cmData = assertDoesNotThrow(() -> Files.readString(Paths.get(dsModelFile)),
        String.format("readString operation failed for %s", dsModelFile));
    assertNotNull(cmData, String.format("readString() operation failed while creating ConfigMap %s", configMapName));
    data.put("model.jdbc.yaml", cmData);

    V1ObjectMeta meta = new V1ObjectMeta()
        .labels(labels)
        .name(configMapName)
        .namespace(domainNamespace);
    V1ConfigMap configMap = new V1ConfigMap()
        .data(data)
        .metadata(meta);

    boolean cmCreated = assertDoesNotThrow(() -> createConfigMap(configMap),
        String.format("createConfigMap failed for %s", configMapName));
    assertTrue(cmCreated, String.format("createConfigMap failed while creating ConfigMap %s", configMapName));
     
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

    // check admin server pod exists
    logger.info("Check for admin server pod {0} existence in namespace {1}",
        adminServerPodName, domainNamespace);
    checkPodCreated(adminServerPodName, domainUid, domainNamespace);

    // check managed server pods exist
    for (int i = 1; i <= replicaCount; i++) {
      logger.info("Check for managed server pod {0} existence in namespace {1}",
          managedServerPrefix + i, domainNamespace);
      checkPodCreated(managedServerPrefix + i, domainUid, domainNamespace);
    }

    // check admin server pod is ready
    logger.info("Wait for admin server pod {0} to be ready in namespace {1}",
        adminServerPodName, domainNamespace);
    checkPodReady(adminServerPodName, domainUid, domainNamespace);

    logger.info("Check admin server status by calling read state command");
    checkServerReadyStatusByExec(adminServerPodName, domainNamespace);

    // check managed server pods are ready
    for (int i = 1; i <= replicaCount; i++) {
      logger.info("Wait for managed server pod {0} to be ready in namespace {1}",
          managedServerPrefix + i, domainNamespace);
      checkPodReady(managedServerPrefix + i, domainUid, domainNamespace);
    }

    logger.info("Check admin service {0} is created in namespace {1}",
        adminServerPodName, domainNamespace);
    checkServiceCreated(adminServerPodName, domainNamespace);

    // check managed server services created
    for (int i = 1; i <= replicaCount; i++) {
      logger.info("Check managed server service {0} is created in namespace {1}",
          managedServerPrefix + i, domainNamespace);
      checkServiceCreated(managedServerPrefix + i, domainNamespace);
    }

    String adminPodCreationTime =
        assertDoesNotThrow(() -> getPodCreationTimestamp(domainNamespace, "", adminServerPodName),
            String.format("Can not find PodCreationTime for pod %s", adminServerPodName));
    assertNotNull(adminPodCreationTime, "adminPodCreationTime returns NULL");
    logger.info("Got adminPodCreationTime {0} ", adminPodCreationTime);

    String managed1PodCreationTime = 
         assertDoesNotThrow(() -> getPodCreationTimestamp(domainNamespace, "", managedServerPrefix + 1),
         String.format("Can not find PodCreationTime for pod %s", managedServerPrefix + 1));
    assertNotNull(managed1PodCreationTime, "ManagedPodCreationTime returns NULL");
    logger.info("Got managed1PodCreationTime {0} ", managed1PodCreationTime);

    String managed2PodCreationTime = 
         assertDoesNotThrow(() -> getPodCreationTimestamp(domainNamespace, "", managedServerPrefix + 2),
         String.format("Can not find PodCreationTime for pod %s", managedServerPrefix + 2));
    assertNotNull(managed2PodCreationTime, "ManagedPodCreationTime returns NULL");
    logger.info("Got managed2PodCreationTime {0} ", managed2PodCreationTime);

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
        .append(" \"path\": \"/spec/restartVersion\",")
        .append(" \"value\": \"2\"")
        .append(" }]");
    logger.log(Level.INFO, "Restart version patch string: {0}", patchStr);

    patch = new V1Patch(new String(patchStr));
    boolean rvPatched = assertDoesNotThrow(() ->
            patchDomainCustomResource(domainUid, domainNamespace, patch, "application/json-patch+json"),
        "patchDomainCustomResource(restartVersion)  failed ");
    assertTrue(rvPatched, "patchDomainCustomResource(restartVersion) failed");

    // Check if the admin server pod has been restarted 
    // by comparing the PodCreationTime before and after rolling restart
    checkPodRestarted(adminServerPodName, domainUid, domainNamespace, adminPodCreationTime);

    // Check if the managed server pods have been restarted 
    // by comparing the PodCreationTime before and after rolling restart
    checkPodRestarted(managedServerPrefix + 1, domainUid, domainNamespace, managed1PodCreationTime);
    checkPodRestarted(managedServerPrefix + 2, domainUid, domainNamespace, managed2PodCreationTime);
    // check managed server services created
    // Even if pods are created, need for the service to created 
    // before checking the DataSource configuration
    for (int i = 1; i <= replicaCount; i++) {
      logger.info("Check managed server service {0} is created in namespace {1}",
          managedServerPrefix + i, domainNamespace);
      checkServiceCreated(managedServerPrefix + i, domainNamespace);
    }

    oracle.weblogic.kubernetes.utils.ExecResult result = null;
    int adminServiceNodePort = getAdminServiceNodePort(adminServerPodName + "-external", null, domainNamespace);
    checkJdbc = new StringBuffer("status=$(curl --user weblogic:welcome1 ");
    checkJdbc.append("http://" + K8S_NODEPORT_HOST + ":" + adminServiceNodePort)
          .append("/management/wls/latest/datasources/id/TestDataSource/")
          .append(" --silent --show-error ")
          .append(" -o /dev/null")
          .append(" -w %{http_code});")
          .append("echo ${status}");
    logger.info("curl command {0}", new String(checkJdbc));
    try {
      result = exec(new String(checkJdbc), true);
    } catch (Exception ex) {
      logger.info("Caught unexpected exception {0}", ex);
      fail("Got unexpected exception" + ex);
    }

    logger.info("curl command returns {0}", result.toString());
    assertEquals("200", result.stdout(), "DataSource configuration not found");
    logger.info("Found the DataSource configuration ");

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

  private void createRepoSecret(String domNamespace) throws ApiException {
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

  private void createDomainSecret(String secretName, String username, String password, String domNamespace)
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

  private void createDomainResource(
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

  private void checkPodCreated(String podName, String domainUid, String domNamespace) {
    withStandardRetryPolicy
        .conditionEvaluationListener(
            condition -> logger.info("Waiting for pod {0} to be created in namespace {1} "
                    + "(elapsed time {2}ms, remaining time {3}ms)",
                podName,
                domNamespace,
                condition.getElapsedTimeInMS(),
                condition.getRemainingTimeInMS()))
        .until(assertDoesNotThrow(() -> podExists(podName, domainUid, domNamespace),
            String.format("podExists failed with ApiException for %s in namespace in %s",
                podName, domNamespace)));

  }

  private void checkPodReady(String podName, String domainUid, String domNamespace) {
    withStandardRetryPolicy
        .conditionEvaluationListener(
            condition -> logger.info("Waiting for pod {0} to be ready in namespace {1} "
                    + "(elapsed time {2}ms, remaining time {3}ms)",
                podName,
                domNamespace,
                condition.getElapsedTimeInMS(),
                condition.getRemainingTimeInMS()))
        .until(assertDoesNotThrow(() -> podReady(podName, domainUid, domNamespace),
            String.format(
                "pod %s is not ready in namespace %s", podName, domNamespace)));

  }

  private void checkServiceCreated(String serviceName, String domNamespace) {
    withStandardRetryPolicy
        .conditionEvaluationListener(
            condition -> logger.info("Waiting for service {0} to be created in namespace {1} "
                    + "(elapsed time {2}ms, remaining time {3}ms)",
                serviceName,
                domNamespace,
                condition.getElapsedTimeInMS(),
                condition.getRemainingTimeInMS()))
        .until(assertDoesNotThrow(() -> serviceExists(serviceName, null, domNamespace),
            String.format(
                "Service %s is not ready in namespace %s", serviceName, domainNamespace)));

  }

  private void checkServerReadyStatusByExec(String podName, String namespace) {
    ExecResult execResult = assertDoesNotThrow(
        () -> execCommand(namespace, podName, null, true, READ_STATE_COMMAND));
    if (execResult.exitValue() == 0) {
      logger.info("execResult: " + execResult);
      assertEquals("RUNNING", execResult.stdout(),
          "Expected " + podName + ", in namespace " + namespace + ", to be in RUNNING ready status");
    } else {
      fail("Ready command failed with exit status code: " + execResult.exitValue());
    }
  }

  private void checkPodRestarted(String podName, String domainUid, String domNamespace, String timestamp) {
    withStandardRetryPolicy
        .conditionEvaluationListener(
            condition -> logger.info("Waiting for pod {0} to be restarted in namespace {1} "
                    + "(elapsed time {2}ms, remaining time {3}ms)",
                podName,
                domNamespace,
                condition.getElapsedTimeInMS(),
                condition.getRemainingTimeInMS()))
        .until(assertDoesNotThrow(() -> isPodRestarted(podName, domainUid, domNamespace, timestamp),
            String.format("podExists failed with ApiException for %s in namespace in %s",
                podName, domNamespace)));
  }

}
