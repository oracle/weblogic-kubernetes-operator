// Copyright (c) 2021, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.weblogic.kubernetes;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import io.kubernetes.client.openapi.models.V1EnvVar;
import io.kubernetes.client.openapi.models.V1LocalObjectReference;
import io.kubernetes.client.openapi.models.V1ObjectMeta;
import io.kubernetes.client.openapi.models.V1PodSecurityContext;
import io.kubernetes.client.openapi.models.V1ResourceRequirements;
import io.kubernetes.client.openapi.models.V1SecretReference;
import oracle.weblogic.domain.AdminServer;
import oracle.weblogic.domain.Cluster;
import oracle.weblogic.domain.Configuration;
import oracle.weblogic.domain.Domain;
import oracle.weblogic.domain.DomainSpec;
import oracle.weblogic.domain.Model;
import oracle.weblogic.domain.ServerPod;
import oracle.weblogic.kubernetes.annotations.IntegrationTest;
import oracle.weblogic.kubernetes.annotations.Namespaces;
import oracle.weblogic.kubernetes.logging.LoggingFacade;
import org.awaitility.core.ConditionFactory;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import static java.util.concurrent.TimeUnit.MINUTES;
import static java.util.concurrent.TimeUnit.SECONDS;
import static oracle.weblogic.kubernetes.TestConstants.ADMIN_SERVER_NAME_BASE;
import static oracle.weblogic.kubernetes.TestConstants.DOMAIN_API_VERSION;
import static oracle.weblogic.kubernetes.TestConstants.MANAGED_SERVER_NAME_BASE;
import static oracle.weblogic.kubernetes.TestConstants.MII_BASIC_APP_NAME;
import static oracle.weblogic.kubernetes.TestConstants.OCIR_SECRET_NAME;
import static oracle.weblogic.kubernetes.TestConstants.WLS_DOMAIN_TYPE;
import static oracle.weblogic.kubernetes.actions.ActionConstants.RESOURCE_DIR;
import static oracle.weblogic.kubernetes.actions.TestActions.getContainerRestartCount;
import static oracle.weblogic.kubernetes.actions.TestActions.getDomainCustomResource;
import static oracle.weblogic.kubernetes.actions.impl.primitive.Kubernetes.copyFileToPod;
import static oracle.weblogic.kubernetes.assertions.TestAssertions.appAccessibleInPod;
import static oracle.weblogic.kubernetes.assertions.TestAssertions.appNotAccessibleInPod;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.checkPodReadyAndServiceExists;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.createDomainAndVerify;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.createMiiImageAndVerify;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.createOcirRepoSecret;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.createSecretWithUsernamePassword;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.dockerLoginAndPushImageToRegistry;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.installAndVerifyOperator;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.setPodAntiAffinity;
import static oracle.weblogic.kubernetes.utils.ThreadSafeLogger.getLogger;
import static org.awaitility.Awaitility.with;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Test liveness probe customization in a multicluster mii domain.
 * Build model in image with liveness probe custom script named customLivenessProbe.sh
 * Enable
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DisplayName("Verify liveness probe customization")
@IntegrationTest
public class ItLivenessProbeCustomization {

  private static String domainNamespace = null;

  // domain constants
  private static final String domainUid = "liveprobecustom";
  private static final String MII_IMAGE_NAME = "liveprobecustom-mii";
  private static final String CLUSTER_NAME_PREFIX = "cluster-";
  private static final int replicaCount = 1;
  private static final int NUMBER_OF_CLUSTERS_MIIDOMAIN = 2;
  private static final String adminServerPodName = domainUid + "-" + ADMIN_SERVER_NAME_BASE;
  private static final String APPCHECK_SCRIPT = "customLivenessProbe.sh";
  private static final String COPY_CMD = "copy-cmd.txt";
  private static final String internalPort = "8001";
  private static final String appPath = "sample-war/index.jsp";

  private static LoggingFacade logger = null;

  private static ConditionFactory withStandardRetryPolicy =
      with().pollDelay(2, SECONDS)
          .and().with().pollInterval(10, SECONDS)
          .atMost(5, MINUTES).await();

  /**
   * Get namespaces for operator and WebLogic domain.
   *
   * @param namespaces list of namespaces created by the IntegrationTestWatcher by the
   *                   JUnit engine parameter resolution mechanism
   */
  @BeforeAll
  public static void initAll(@Namespaces(2) List<String> namespaces) {
    logger = getLogger();
    // get a unique operator namespace
    logger.info("Getting a unique namespace for operator");
    assertNotNull(namespaces.get(0), "Namespace list is null");
    final String opNamespace = namespaces.get(0);

    // get a unique domain namespace
    logger.info("Getting a unique namespace for WebLogic domain");
    assertNotNull(namespaces.get(1), "Namespace list is null");
    domainNamespace = namespaces.get(1);

    // install and verify operator
    installAndVerifyOperator(opNamespace, domainNamespace);

    // create mii with additional livenessprobecustom script
    String imageName = createAndVerifyDomainImage();

    // create a basic model in image domain
    createAndVerifyMiiDomain(imageName);
  }

  /**
   * Verify the customization of liveness probe.
   * Build model in image with liveness probe custom script named customLivenessProbe.sh
   * for a 2 clusters domain.
   * Enable "LIVENESS_PROBE_CUSTOM_SCRIPT" while creating domain CR.
   * After the domain is created copy the file named tempfile.txt into tested managed server pods for
   * both clusters, which is used by custom script to trigger liveness probe.
   * Verify the container managed server pods in both clusters are restarted
   */
  @Test
  @Order(1)
  @DisplayName("Test customization of the liveness probe")
  public void testCustomLivenessProbe() {
    Domain domain1 = assertDoesNotThrow(() -> getDomainCustomResource(domainUid, domainNamespace),
        String.format("getDomainCustomResource failed with ApiException when tried to get domain %s in namespace %s",
            domainUid, domainNamespace));
    assertNotNull(domain1, "Got null domain resource");

    // create temp file
    String fileName = "tempFile";
    File tempFile = assertDoesNotThrow(() -> createTempfile(fileName),
        "Failed to create temp file");
    logger.info("File created  {0}", tempFile);

    for (int i = 1; i <= NUMBER_OF_CLUSTERS_MIIDOMAIN; i++) {
      for (int j = 1; j <= replicaCount; j++) {
        String managedServerPodName =
            domainUid + "-" + CLUSTER_NAME_PREFIX + i + "-" + MANAGED_SERVER_NAME_BASE + j;

        // get the restart count of the container in pod before liveness probe restarts
        final int beforeRestartCount =
            assertDoesNotThrow(() -> getContainerRestartCount(domainNamespace, null,
            managedServerPodName, null),
            String.format("Failed to get the restart count of the container from pod {0} in namespace {1}",
                managedServerPodName, domainNamespace));
        logger.info("For server {0} restart count before liveness probe is: {1}",
            managedServerPodName, beforeRestartCount);

        // copy script to pod
        String destLocation = "/u01/tempFile.txt";
        assertDoesNotThrow(() -> copyFileToPod(domainNamespace, managedServerPodName, "weblogic-server",
            tempFile.toPath(), Paths.get(destLocation)),
            String.format("Failed to copy file %s to pod %s in namespace %s",
            tempFile, managedServerPodName, domainNamespace));
        logger.info("File copied to Pod {0} in namespace {1}", managedServerPodName, domainNamespace);

        String expectedStr = "Hello World, you have reached server "
            + CLUSTER_NAME_PREFIX + i + "-" + MANAGED_SERVER_NAME_BASE + j;

        checkAppNotRunning(
            domainNamespace,
            managedServerPodName,
            expectedStr);

        checkAppIsRunning(
            domainNamespace,
            managedServerPodName,
            expectedStr);

        // get the restart count of the container in pod after liveness probe restarts
        int afterRestartCount = assertDoesNotThrow(() ->
            getContainerRestartCount(domainNamespace, null, managedServerPodName, null),
            String.format("Failed to get the restart count of the container from pod {0} in namespace {1}",
            managedServerPodName, domainNamespace));
        logger.info("Restart count after liveness probe {0}", afterRestartCount);
        assertTrue(afterRestartCount - beforeRestartCount == 1,
            String.format("Liveness probe did not start the container in pod %s in namespace %s",
            managedServerPodName, domainNamespace));
      }
    }
  }

  /**
   * Verify the negative test case of customization of liveness probe.
   * Build model in image with liveness probe custom script named customLivenessProbe.sh
   * for a 2 clusters domain.
   * Enable "LIVENESS_PROBE_CUSTOM_SCRIPT" while creating domain CR.
   * Since there is no temp file named "tempFile.txt" in the tested managed server pods, based on
   * custom script logic, liveness probe will not be triggered.
   * Verify the container managed server pods in both clusters are NOT restarted
   */
  @Test
  @Order(2)
  @DisplayName("Test custom liveness probe is not trigged")
  public void testCustomLivenessProbeNotTrigged() {
    Domain domain1 = assertDoesNotThrow(() -> getDomainCustomResource(domainUid, domainNamespace),
        String.format("getDomainCustomResource failed with ApiException when tried to get domain %s in namespace %s",
            domainUid, domainNamespace));
    assertNotNull(domain1, "Got null domain resource");

    for (int i = 1; i <= NUMBER_OF_CLUSTERS_MIIDOMAIN; i++) {
      for (int j = 1; j <= replicaCount; j++) {
        String managedServerPodName =
            domainUid + "-" + CLUSTER_NAME_PREFIX + i + "-" + MANAGED_SERVER_NAME_BASE + j;

        String expectedStr = "Hello World, you have reached server "
            + CLUSTER_NAME_PREFIX + i + "-" + MANAGED_SERVER_NAME_BASE + j;
        checkAppIsRunning(
            domainNamespace,
            managedServerPodName,
            expectedStr);

        // get the restart count of the container, which should be 1 after positive test case
        int restartCount = assertDoesNotThrow(() ->
            getContainerRestartCount(domainNamespace, null, managedServerPodName, null),
            String.format("Failed to get the restart count of the container from pod {0} in namespace {1}",
            managedServerPodName, domainNamespace));
        logger.info("restart count is: {0}", restartCount);
        assertTrue(restartCount == 1,
            String.format("Liveness probe starts the container in pod %s in namespace %s",
            managedServerPodName, domainNamespace));
      }
    }
  }


  /**
   * Create a model in image domain and verify the server pods are ready.
   */
  private static void createAndVerifyMiiDomain(String miiImage) {

    // docker login and push image to docker registry if necessary
    dockerLoginAndPushImageToRegistry(miiImage);

    // create docker registry secret to pull the image from registry
    // this secret is used only for non-kind cluster
    logger.info("Creating docker registry secret in namespace {0}", domainNamespace);
    createOcirRepoSecret(domainNamespace);

    // create secret for admin credentials
    logger.info("Creating secret for admin credentials");
    String adminSecretName = "weblogic-credentials";
    createSecretWithUsernamePassword(adminSecretName, domainNamespace, "weblogic", "welcome1");

    // create encryption secret
    logger.info("Creating encryption secret");
    String encryptionSecretName = "encryptionsecret";
    createSecretWithUsernamePassword(encryptionSecretName, domainNamespace, "weblogicenc", "weblogicenc");

    // construct the cluster list used for domain custom resource
    List<Cluster> clusterList = new ArrayList<>();
    for (int i = NUMBER_OF_CLUSTERS_MIIDOMAIN; i >= 1; i--) {
      clusterList.add(new Cluster()
          .clusterName(CLUSTER_NAME_PREFIX + i)
          .replicas(replicaCount)
          .serverStartState("RUNNING"));
    }
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
                    .name("LIVENESS_PROBE_CUSTOM_SCRIPT")
                    .value("/u01/customLivenessProbe.sh"))
                .resources(new V1ResourceRequirements()
                    .limits(new HashMap<>())
                    .requests(new HashMap<>()))
                .podSecurityContext(new V1PodSecurityContext()
                    .runAsUser(0L)))
            .adminServer(new AdminServer()
                .serverStartState("RUNNING"))
            .clusters(clusterList)
            .configuration(new Configuration()
                .model(new Model()
                    .domainType(WLS_DOMAIN_TYPE)
                    .runtimeEncryptionSecret(encryptionSecretName))));
    setPodAntiAffinity(domain);
    // create model in image domain
    logger.info("Creating model in image domain {0} in namespace {1} using docker image {2}",
        domainUid, domainNamespace, miiImage);
    createDomainAndVerify(domain, domainNamespace);

    // verify the admin server service and pod is created
    checkPodReadyAndServiceExists(adminServerPodName, domainUid, domainNamespace);

    // check the readiness for the managed servers in each cluster
    for (int i = 1; i <= NUMBER_OF_CLUSTERS_MIIDOMAIN; i++) {
      for (int j = 1; j <= replicaCount; j++) {
        String managedServerPodName =
            domainUid + "-" + CLUSTER_NAME_PREFIX + i + "-" + MANAGED_SERVER_NAME_BASE + j;

        // check managed server pod is ready and service exists in the namespace
        logger.info("Checking that managed server pod {0} is ready and service exists in namespace {1}",
            managedServerPodName, domainNamespace);
        checkPodReadyAndServiceExists(managedServerPodName, domainUid, domainNamespace);
      }
    }

    //check and wait for the application to be accessible in all server pods
    for (int i = 1; i <= NUMBER_OF_CLUSTERS_MIIDOMAIN; i++) {
      for (int j = 1; j <= replicaCount; j++) {
        String managedServerPodName =
            domainUid + "-" + CLUSTER_NAME_PREFIX + i + "-" + MANAGED_SERVER_NAME_BASE + j;
        String expectedStr = "Hello World, you have reached server "
            + CLUSTER_NAME_PREFIX + i + "-" + MANAGED_SERVER_NAME_BASE + j;


        logger.info("Checking that application is running on managed server pod {0}  in namespace {1} with "
             + "expectedString {3}", managedServerPodName, domainNamespace, expectedStr);
        checkAppIsRunning(
            domainNamespace,
            managedServerPodName,
            expectedStr);
      }
    }

    logger.info("Domain {0} is fully started - servers are running and application is available",
        domainUid);
  }

  private static void checkAppIsRunning(
      String namespace,
      String podName,
      String expectedStr
  ) {

    // check that the application is NOT running inside of a server pod
    withStandardRetryPolicy
        .conditionEvaluationListener(
            condition -> logger.info("Checking if application {0} IS running on pod {1} in namespace {2} "
            + "(elapsed time {3}ms, remaining time {4}ms)",
            appPath,
            podName,
            namespace,
            condition.getElapsedTimeInMS(),
            condition.getRemainingTimeInMS()))
        .until(() -> appAccessibleInPod(
                namespace,
                podName,
                internalPort,
                appPath,
                expectedStr));

  }

  private static void checkAppNotRunning(
      String namespace,
      String podName,
      String expectedStr
  ) {

    // check that the application is NOT running inside of a server pod
    withStandardRetryPolicy
        .conditionEvaluationListener(
            condition -> logger.info("Checking if application {0} is NOT running on pod {1} in namespace {2} "
            + "(elapsed time {3}ms, remaining time {4}ms)",
            appPath,
            podName,
            namespace,
            condition.getElapsedTimeInMS(),
            condition.getRemainingTimeInMS()))
        .until(() -> appNotAccessibleInPod(
                namespace,
                podName,
                internalPort,
                appPath,
                expectedStr));
  }

  private static String createAndVerifyDomainImage() {

    String additionalBuildCommands = RESOURCE_DIR + "/bash-scripts/" + COPY_CMD;
    logger.info("additionalBuildCommands is: " + additionalBuildCommands);

    StringBuffer additionalBuildFilesVarargsBuff = new StringBuffer()
        .append(RESOURCE_DIR)
        .append("/")
        .append("bash-scripts")
        .append("/")
        .append(APPCHECK_SCRIPT);
    logger.info("additionalBuildFilesVarargsBuff: " + additionalBuildFilesVarargsBuff.toString());

    final String wdtModelFileForMiiDomain = "model-multiclusterdomain-singlesampleapp-wls.yaml";
    logger.info("Create image with model file and verify");
    String miiImage =
        createMiiImageAndVerify(MII_IMAGE_NAME, wdtModelFileForMiiDomain, MII_BASIC_APP_NAME,
            additionalBuildCommands, additionalBuildFilesVarargsBuff.toString());

    // docker login and push image to docker registry if necessary
    dockerLoginAndPushImageToRegistry(miiImage);

    // create docker registry secret to pull the image from registry
    // this secret is used only for non-kind cluster
    logger.info("Create docker registry secret in namespace {0}", domainNamespace);
    createOcirRepoSecret(domainNamespace);
    return miiImage;
  }

  private File createTempfile(String filename) throws IOException {
    File tempFile = File.createTempFile(filename, ".txt");
    //deletes the file when VM terminates
    tempFile.deleteOnExit();
    try (FileWriter fw = new FileWriter(tempFile)) {
      fw.write("This one line file is to test liveness Probe custom script");
    }
    return tempFile;
  }


}
