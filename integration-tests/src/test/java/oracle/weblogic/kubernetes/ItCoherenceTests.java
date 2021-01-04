// Copyright (c) 2020, 2021, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.weblogic.kubernetes;

import java.io.IOException;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import io.kubernetes.client.openapi.ApiException;
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
import oracle.weblogic.domain.Model;
import oracle.weblogic.domain.ServerPod;
import oracle.weblogic.kubernetes.annotations.IntegrationTest;
import oracle.weblogic.kubernetes.annotations.Namespaces;
import oracle.weblogic.kubernetes.logging.LoggingFacade;
import oracle.weblogic.kubernetes.utils.ExecResult;
import oracle.weblogic.kubernetes.utils.FileUtils;
import org.awaitility.core.ConditionFactory;
import org.joda.time.DateTime;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static java.util.concurrent.TimeUnit.MINUTES;
import static java.util.concurrent.TimeUnit.SECONDS;
import static oracle.weblogic.kubernetes.TestConstants.DOMAIN_API_VERSION;
import static oracle.weblogic.kubernetes.TestConstants.OCIR_SECRET_NAME;
import static oracle.weblogic.kubernetes.actions.ActionConstants.APP_DIR;
import static oracle.weblogic.kubernetes.actions.TestActions.createDomainCustomResource;
import static oracle.weblogic.kubernetes.actions.TestActions.deleteDomainCustomResource;
import static oracle.weblogic.kubernetes.actions.TestActions.execCommand;
import static oracle.weblogic.kubernetes.actions.TestActions.getPodIP;
import static oracle.weblogic.kubernetes.actions.TestActions.patchDomainResourceWithNewRestartVersion;
import static oracle.weblogic.kubernetes.assertions.TestAssertions.verifyRollingRestartOccurred;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.checkPodReady;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.checkServiceExists;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.createImageAndVerify;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.createOcirRepoSecret;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.createSecretWithUsernamePassword;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.dockerLoginAndPushImageToRegistry;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.getPodCreationTime;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.installAndVerifyOperator;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.setPodAntiAffinity;
import static oracle.weblogic.kubernetes.utils.ThreadSafeLogger.getLogger;
import static org.awaitility.Awaitility.with;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

// Test to create a WebLogic domain with Coherence, build the Coherence proxy client program
// which load and verify the cache.
@DisplayName("Test to create a WebLogic domain with Coherence and verify the use of Coherence cache service")
@IntegrationTest
class ItCoherenceTests {

  // constants for Coherence
  private static final String PROXY_CLIENT_APP_NAME = "coherence-proxy-client";
  private static final String PROXY_SERVER_APP_NAME = "coherence-proxy-server";
  private static final String APP_LOC_ON_HOST = APP_DIR + "/" + PROXY_CLIENT_APP_NAME;
  private static final String APP_LOC_IN_POD = "/u01/oracle/apps/" + PROXY_CLIENT_APP_NAME;
  private static final String PROXY_CLIENT_SCRIPT = "buildRunProxyClient.sh";
  private static final String OP_CACHE_LOAD = "load";
  private static final String OP_CACHE_VALIDATE = "validate";
  private static final String PROXY_PORT = "9000";

  // constants for creating domain image using model in image
  private static final String COHERENCE_MODEL_FILE = "coherence-wdt-config.yaml";
  private static final String COHERENCE_MODEL_PROP = "coherence-wdt-config.properties";
  private static final String COHERENCE_IMAGE_NAME = "coherence-image";

  private static String domainUid = "coh-domain1";
  private static String clusterName = "cluster-1";
  private static String adminServerPodName = domainUid + "-admin-server";
  private static String managedServerPrefix = domainUid + "-managed-server";
  private static String containerName = "weblogic-server";
  private static int replicaCount = 2;

  private static String opNamespace = null;
  private static String domainNamespace = null;
  private static ConditionFactory withStandardRetryPolicy = null;
  private static Map<String, Object> secretNameMap;
  private static LoggingFacade logger = null;

  /**
   * Install Operator.
   *
   * @param namespaces list of namespaces created by the IntegrationTestWatcher by the
   *                   JUnit engine parameter resolution mechanism
   */
  @BeforeAll
  public static void init(@Namespaces(2) List<String> namespaces) {
    logger = getLogger();
    // create standard, reusable retry/backoff policy
    withStandardRetryPolicy = with().pollDelay(2, SECONDS)
      .and().with().pollInterval(10, SECONDS)
      .atMost(5, MINUTES).await();

    // get a new unique opNamespace
    logger.info("Assigning a unique namespace for Operator");
    assertNotNull(namespaces.get(0), "Namespace list is null");
    opNamespace = namespaces.get(0);

    // get a new unique domainNamespace
    logger.info("Assigning a unique namespace for Domain");
    assertNotNull(namespaces.get(1), "Namespace list is null");
    domainNamespace = namespaces.get(1);

    // install and verify operator
    installAndVerifyOperator(opNamespace, domainNamespace);
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

  /**
   * Create a WebLogic domain with a Coherence cluster and deploying it using WDT
   * Test rolling restart of Coherence managed servers and verify
   * that data are not lost during a domain restart.
   */
  @Test
  @DisplayName("Create domain with a Coherence cluster using WDT and test rolling restart")
  public void testCohernceServerRollingRestart() {
    final String successMarker = "CACHE-SUCCESS";

    // create a DomainHomeInImage image using WebLogic Image Tool
    String domImage = createAndVerifyDomainImage();

    // create and verify a WebLogic domain with a Coherence cluster
    createAndVerifyDomain(domImage);

    // build the Coherence proxy client program in the server pods
    // which load and verify the cache
    copyCohProxyClientAppToPods();

    // run the Coherence proxy client to load the cache
    String serverName = managedServerPrefix + "1";
    final ExecResult execResult1 = assertDoesNotThrow(
        () -> runCoherenceProxyClient(serverName, OP_CACHE_LOAD),
        String.format("Failed to call Coherence proxy client in pod %s, namespace %s",
            serverName, domainNamespace));

    assertAll("Check that the cache loaded successfully",
        () -> assertTrue(execResult1.exitValue() == 0, "Failed to load the cache"),
        () -> assertTrue(execResult1.stdout().contains(successMarker), "Failed to load the cache")
    );

    logger.info("Coherence proxy client {0} returns {1} \n ",
        OP_CACHE_LOAD, execResult1.stdout());

    // patch domain to rolling restart it by change restartVersion
    rollingRestartDomainAndVerify();

    // build the Coherence proxy client program in the server pods
    // which load and verify the cache
    copyCohProxyClientAppToPods();

    // run the Coherence proxy client to verify the cache contents
    final ExecResult execResult2 = assertDoesNotThrow(
        () -> runCoherenceProxyClient(serverName, OP_CACHE_VALIDATE),
        String.format("Failed to call Coherence proxy client in pod %s, namespace %s",
            serverName, domainNamespace));

    assertAll("Check that the cache loaded successfully",
        () -> assertTrue(execResult1.exitValue() == 0, "Failed to validate the cache"),
        () -> assertTrue(execResult2.stdout().contains(successMarker), "Failed to validate the cache")
    );

    logger.info("Coherence proxy client {0} returns {1}",
        OP_CACHE_VALIDATE, execResult2.stdout());

    logger.info("Coherence Server restarted in rolling fashion");
  }

  private void copyCohProxyClientAppToPods() {
    List<String> dirsToMake = new ArrayList<String>();
    dirsToMake.add(APP_LOC_IN_POD + "/src/main/java/cohapp");
    dirsToMake.add(APP_LOC_IN_POD + "/src/main/resources");

    // copy the shell script file and all Coherence app files over to the managed server pods
    for (int i = 1; i < replicaCount; i++) {
      String serverName = managedServerPrefix + i;
      assertDoesNotThrow(
          () -> FileUtils.makeDirectories(domainNamespace, serverName,
              null, true, dirsToMake),
          String.format("Failed to create dir %s in pod %s in namespace %s ",
              dirsToMake.toString(), serverName, domainNamespace));
      logger.info("Failed to create dir {0} in Pod {1} in namespace {2} ",
          dirsToMake.toString(), serverName, domainNamespace);

      assertDoesNotThrow(
          () -> FileUtils.copyFolderToPod(domainNamespace, serverName,
              containerName, Paths.get(APP_LOC_ON_HOST), Paths.get(APP_LOC_IN_POD)),
          String.format("Failed to copy file %s to pod %s in namespace %s and located at %s ",
              APP_LOC_ON_HOST, serverName, domainNamespace, APP_LOC_IN_POD));
      logger.info("File {0} copied to {1} to Pod {2} in namespace {3} ",
          APP_LOC_ON_HOST, APP_LOC_IN_POD, serverName, domainNamespace);
    }
  }

  private ExecResult runCoherenceProxyClient(String serverName, String cacheOp
  ) throws IOException, ApiException, InterruptedException {

    // build the proxy client in the pod and run the proxy test.
    final String coherenceScriptPathInPod = APP_LOC_IN_POD + "/" + PROXY_CLIENT_SCRIPT;

    String serverPodIP = assertDoesNotThrow(
        () -> getPodIP(domainNamespace, "", serverName),
        String.format("Get pod IP address failed with ApiException for %s in namespace %s",
            serverName, domainNamespace));
    logger.info("Admin Pod IP {0} ", serverPodIP);


    StringBuffer coherenceProxyClientCmd = new StringBuffer("chmod +x -R ");
    coherenceProxyClientCmd
        .append(APP_LOC_IN_POD)
        .append(" && sh ")
        .append(coherenceScriptPathInPod)
        .append(" ")
        .append(APP_LOC_IN_POD)
        .append(" ")
        .append(cacheOp)
        .append(" ")
        .append(serverPodIP)
        .append(" ")
        .append(PROXY_PORT);

    logger.info("Command to exec script file: " + coherenceProxyClientCmd);

    ExecResult execResult =
        execCommand(domainNamespace, serverName, containerName, true,
            "/bin/sh", "-c", coherenceProxyClientCmd.toString());

    logger.info("Coherence proxy client returns {0}", execResult.stdout());

    return execResult;
  }

  private static String createAndVerifyDomainImage() {
    // create image with model files
    logger.info("Create image with model file and verify");
    String domImage = createImageAndVerify(
        COHERENCE_IMAGE_NAME, COHERENCE_MODEL_FILE,
            PROXY_SERVER_APP_NAME, COHERENCE_MODEL_PROP, domainUid);

    // docker login and push image to docker registry if necessary
    dockerLoginAndPushImageToRegistry(domImage);

    // create docker registry secret to pull the image from registry
    // this secret is used only for non-kind cluster
    logger.info("Create docker registry secret in namespace {0}", domainNamespace);
    createOcirRepoSecret(domainNamespace);

    return domImage;
  }

  private static void createAndVerifyDomain(String domImage) {
    // create secret for admin credentials
    logger.info("Create secret for admin credentials");
    String adminSecretName = "weblogic-credentials";
    assertDoesNotThrow(() -> createSecretWithUsernamePassword(adminSecretName, domainNamespace,
        "weblogic", "welcome1"),
        String.format("create secret for admin credentials failed for %s", adminSecretName));

    // create encryption secret
    logger.info("Create encryption secret");
    String encryptionSecretName = "encryptionsecret";
    assertDoesNotThrow(() -> createSecretWithUsernamePassword(encryptionSecretName, domainNamespace,
        "weblogicenc", "weblogicenc"),
        String.format("create encryption secret failed for %s", encryptionSecretName));

    // create domain and verify
    logger.info("Create model in image domain {0} in namespace {1} using docker image {2}",
        domainUid, domainNamespace, domImage);
    createDomainCrAndVerify(adminSecretName, domImage);

    // check that admin service exists in the domain namespace
    logger.info("Checking that admin service {0} exists in namespace {1}",
        adminServerPodName, domainNamespace);
    checkServiceExists(adminServerPodName, domainNamespace);

    // check that admin server pod is ready
    logger.info("Checking that admin server pod {0} is ready in namespace {1}",
        adminServerPodName, domainNamespace);
    checkPodReady(adminServerPodName, domainUid, domainNamespace);

    // check for managed server pods existence in the domain namespace
    for (int i = 1; i <= replicaCount; i++) {
      String managedServerPodName = managedServerPrefix + i;

      // check that the managed server service exists in the domain namespace
      logger.info("Checking that managed server service {0} exists in namespace {1}",
          managedServerPodName, domainNamespace);
      checkServiceExists(managedServerPodName, domainNamespace);

      // check that the managed server pod is ready
      logger.info("Checking that managed server pod {0} is ready in namespace {1}",
          managedServerPodName, domainNamespace);
      checkPodReady(managedServerPodName, domainUid, domainNamespace);
    }
  }

  private static void createDomainCrAndVerify(String adminSecretName, String domImage) {
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
            .image(domImage)
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
            .addClustersItem(new Cluster()
                .clusterName(clusterName)
                .replicas(replicaCount)
                .serverStartState("RUNNING"))
            .configuration(new Configuration()
                .model(new Model()
                    .domainType("WLS"))
                .introspectorJobActiveDeadlineSeconds(300L)));
    setPodAntiAffinity(domain);
    logger.info("Create domain custom resource for domainUid {0} in namespace {1}",
        domainUid, domainNamespace);
    boolean domCreated = assertDoesNotThrow(() -> createDomainCustomResource(domain),
        String.format("Create domain custom resource failed with ApiException for %s in namespace %s",
            domainUid, domainNamespace));
    assertTrue(domCreated, String.format("Create domain custom resource failed with ApiException "
        + "for %s in namespace %s", domainUid, domainNamespace));
  }

  private void rollingRestartDomainAndVerify() {
    LinkedHashMap<String, DateTime> pods = new LinkedHashMap<>();
    // get the creation time of the server pods before patching
    DateTime adminPodCreationTime = getPodCreationTime(domainNamespace, adminServerPodName);
    pods.put(adminServerPodName, adminPodCreationTime);
    for (int i = 1; i <= replicaCount; i++) {
      pods.put(managedServerPrefix + i, getPodCreationTime(domainNamespace, managedServerPrefix + i));
    }

    String newRestartVersion = patchDomainResourceWithNewRestartVersion(domainUid, domainNamespace);
    logger.info("New restart version : {0}", newRestartVersion);

    assertTrue(verifyRollingRestartOccurred(pods, 1, domainNamespace), "Rolling restart failed");
  }
}
