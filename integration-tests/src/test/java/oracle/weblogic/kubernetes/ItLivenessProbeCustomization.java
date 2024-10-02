// Copyright (c) 2021, 2024, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.weblogic.kubernetes;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Paths;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.annotation.Nonnull;

import io.kubernetes.client.custom.V1Patch;
import io.kubernetes.client.openapi.ApiException;
import io.kubernetes.client.openapi.models.V1EnvVar;
import io.kubernetes.client.openapi.models.V1LocalObjectReference;
import io.kubernetes.client.openapi.models.V1ObjectMeta;
import io.kubernetes.client.openapi.models.V1ResourceRequirements;
import oracle.weblogic.domain.Configuration;
import oracle.weblogic.domain.DomainResource;
import oracle.weblogic.domain.DomainSpec;
import oracle.weblogic.domain.Model;
import oracle.weblogic.domain.ProbeTuning;
import oracle.weblogic.domain.ServerPod;
import oracle.weblogic.kubernetes.annotations.IntegrationTest;
import oracle.weblogic.kubernetes.annotations.Namespaces;
import oracle.weblogic.kubernetes.logging.LoggingFacade;
import oracle.weblogic.kubernetes.utils.ExecResult;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static io.kubernetes.client.custom.V1Patch.PATCH_FORMAT_JSON_PATCH;
import static oracle.weblogic.kubernetes.TestConstants.ADMIN_PASSWORD_DEFAULT;
import static oracle.weblogic.kubernetes.TestConstants.ADMIN_SERVER_NAME_BASE;
import static oracle.weblogic.kubernetes.TestConstants.ADMIN_USERNAME_DEFAULT;
import static oracle.weblogic.kubernetes.TestConstants.DOMAIN_API_VERSION;
import static oracle.weblogic.kubernetes.TestConstants.DOMAIN_VERSION;
import static oracle.weblogic.kubernetes.TestConstants.MANAGED_SERVER_NAME_BASE;
import static oracle.weblogic.kubernetes.TestConstants.MII_BASIC_APP_NAME;
import static oracle.weblogic.kubernetes.TestConstants.RESULTS_TEMPFILE;
import static oracle.weblogic.kubernetes.TestConstants.TEST_IMAGES_REPO_SECRET_NAME;
import static oracle.weblogic.kubernetes.TestConstants.WLS_DOMAIN_TYPE;
import static oracle.weblogic.kubernetes.actions.ActionConstants.RESOURCE_DIR;
import static oracle.weblogic.kubernetes.actions.TestActions.createDomainCustomResource;
import static oracle.weblogic.kubernetes.actions.TestActions.execCommand;
import static oracle.weblogic.kubernetes.actions.TestActions.getContainerRestartCount;
import static oracle.weblogic.kubernetes.actions.TestActions.getDomainCustomResource;
import static oracle.weblogic.kubernetes.actions.TestActions.getPodCreationTimestamp;
import static oracle.weblogic.kubernetes.actions.impl.Domain.patchDomainCustomResource;
import static oracle.weblogic.kubernetes.actions.impl.primitive.Kubernetes.copyFileToPod;
import static oracle.weblogic.kubernetes.assertions.TestAssertions.appAccessibleInPod;
import static oracle.weblogic.kubernetes.assertions.TestAssertions.appNotAccessibleInPod;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.checkPodReadyAndServiceExists;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.testUntil;
import static oracle.weblogic.kubernetes.utils.DomainUtils.createDomainAndVerify;
import static oracle.weblogic.kubernetes.utils.FileUtils.checkCopyFileToPod;
import static oracle.weblogic.kubernetes.utils.ImageUtils.createMiiImageAndVerify;
import static oracle.weblogic.kubernetes.utils.ImageUtils.createTestRepoSecret;
import static oracle.weblogic.kubernetes.utils.ImageUtils.imageRepoLoginAndPushImageToRegistry;
import static oracle.weblogic.kubernetes.utils.OperatorUtils.installAndVerifyOperator;
import static oracle.weblogic.kubernetes.utils.PodUtils.checkPodReady;
import static oracle.weblogic.kubernetes.utils.PodUtils.checkPodRestarted;
import static oracle.weblogic.kubernetes.utils.PodUtils.setPodAntiAffinity;
import static oracle.weblogic.kubernetes.utils.SecretUtils.createSecretWithUsernamePassword;
import static oracle.weblogic.kubernetes.utils.ThreadSafeLogger.getLogger;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Test liveness probe customization in a multicluster mii domain.
 * Build model in image with liveness probe custom script named
 * customLivenessProbe.sh that retuns success(0) when a file /u01/tempFile.txt
 * avilable on the pod else it returns failure(1).
 * Note: Livenessprobe is triggered only when the script returns success
 */
@DisplayName("Verify liveness probe customization")
@IntegrationTest
@Tag("olcne-srg")
@Tag("kind-parallel")
@Tag("okd-wls-mrg")
@Tag("oke-arm")
@Tag("oke-weekly-sequential")
class ItLivenessProbeCustomization {

  // domain constants
  private static final String domainUid = "liveprobecustom";
  private static final String domainUid2 = "liveprobecustom2";
  private static final String MII_IMAGE_NAME = "liveprobecustom-mii";
  private static final String CLUSTER_NAME_PREFIX = "cluster-";
  private static final int replicaCount = 1;
  private static final int NUMBER_OF_CLUSTERS_MIIDOMAIN = 2;
  private static final String adminServerPodName = domainUid + "-" + ADMIN_SERVER_NAME_BASE;
  private static final String APPCHECK_SCRIPT = "customLivenessProbe.sh";
  private static final String COPY_CMD = "copy-cmd.txt";
  private static final String internalPort = "8001";
  private static final String appPath = "sample-war/index.jsp";
  public static final String WEBLOGIC_CREDENTIALS = "weblogic-credentials";
  public static final String ENCRYPTION_SECRET = "encryptionsecret";

  private static String domainNamespace = null;
  private static String opNamespace = null;
  private static LoggingFacade logger = null;
  private static File tempFile = null;
  private static String imageName = null;

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
    opNamespace = namespaces.get(0);

    // get a unique domain namespace
    logger.info("Getting a unique namespace for WebLogic domain");
    assertNotNull(namespaces.get(1), "Namespace list is null");
    domainNamespace = namespaces.get(1);

    // install and verify operator
    installAndVerifyOperator(opNamespace, domainNamespace);

    // create mii with additional livenessprobecustom script
    // Build model in image with liveness probe custom script named
    // customLivenessProbe.sh for a 2 clusters domain.
    // Enable "LIVENESS_PROBE_CUSTOM_SCRIPT" while creating domain CR.

    imageName = createAndVerifyDomainImage();
    createAndVerifyMiiDomain(imageName);

    // create temp file to be copied to managed server pods which will be used
    // in customLivenessProbe.sh
    String fileName = "tempFile";
    tempFile = assertDoesNotThrow(() -> createTempfile(fileName), "Failed to create temp file");
    logger.info("File created  {0}", tempFile);
  }

  /**
   * After the domain is started, copy the file named tempFile.txt into all
   * managed server pods for both clusters. On copying the files, the custom
   * script customLivenessProbe.sh return success(0) which will roll the pod
   * to trigger liveness probe. Verify the managed server pods in both clusters
   * are restarted
   */
  @Test
  @DisplayName("Test custom liveness probe is triggered")
  @Tag("crio")
  void testCustomLivenessProbeTriggered() {
    DomainResource domain1 = assertDoesNotThrow(() -> getDomainCustomResource(domainUid, domainNamespace),
        String.format("getDomainCustomResource failed with ApiException when tried to get domain %s in namespace %s",
            domainUid, domainNamespace));
    assertNotNull(domain1, "Got null domain resource");

    for (int i = 1; i <= NUMBER_OF_CLUSTERS_MIIDOMAIN; i++) {
      for (int j = 1; j <= replicaCount; j++) {
        String managedServerPodName =
            domainUid + "-" + CLUSTER_NAME_PREFIX + i + "-" + MANAGED_SERVER_NAME_BASE + j;

        // get the restart count of the container in pod before liveness probe restarts
        final int beforeRestartCount =
            assertDoesNotThrow(() -> getContainerRestartCount(domainNamespace, null,
            managedServerPodName, null),
            String.format("Failed to get the restart count of the container from pod %s in namespace %s",
                managedServerPodName, domainNamespace));
        logger.info("For server {0} restart count before liveness probe is: {1}",
            managedServerPodName, beforeRestartCount);

        // copy script to pod
        String destLocation = "/u01/tempFile.txt";
        testUntil(
            checkCopyFileToPod(domainNamespace, managedServerPodName, "weblogic-server",
                tempFile.toPath(), Paths.get(destLocation)),
            logger,
            "copying file {0} to pod {1} in namspace {2}",
            tempFile.toPath(),
            managedServerPodName,
            domainNamespace);
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
            String.format("Failed to get the restart count of the container from pod %s in namespace %s",
            managedServerPodName, domainNamespace));
        logger.info("Restart count after liveness probe {0}", afterRestartCount);
        assertEquals(1, afterRestartCount - beforeRestartCount,
            String.format("Liveness probe did not start the container in pod %s in namespace %s",
            managedServerPodName, domainNamespace));
      }
    }
  }

  /* Since there is no temp file named "/u01/tempFile.txt" in the managed
   * server pods, liveness probe will not be activated.
   * Verify the container in managed server pods are not restarted
   */
  @Test
  @DisplayName("Test custom liveness probe is not triggered")
  void testCustomLivenessProbeNotTriggered() {
    DomainResource domain1 = assertDoesNotThrow(() -> getDomainCustomResource(domainUid, domainNamespace),
        String.format("getDomainCustomResource failed with ApiException when tried to get domain %s in namespace %s",
            domainUid, domainNamespace));
    assertNotNull(domain1, "Got null domain resource");
    for (int i = 1; i <= NUMBER_OF_CLUSTERS_MIIDOMAIN; i++) {
      for (int j = 1; j <= replicaCount; j++) {
        String managedServerPodName =
            domainUid + "-" + CLUSTER_NAME_PREFIX + i + "-" + MANAGED_SERVER_NAME_BASE + j;
        // get the initial restart count of the container in pod
        final int beforeRestartCount =
            assertDoesNotThrow(() -> getContainerRestartCount(domainNamespace, null,
            managedServerPodName, null),
            String.format("Failed to get the restart count of the container from pod %s in namespace %s",
                managedServerPodName, domainNamespace));
        logger.info("For server {0} restart count before liveness probe is: {1}",
            managedServerPodName, beforeRestartCount);

        logger.info("[1] restart count is: {0}", beforeRestartCount);
        String expectedStr = "Hello World, you have reached server "
            + CLUSTER_NAME_PREFIX + i + "-" + MANAGED_SERVER_NAME_BASE + j;

        checkAppIsRunning(
            domainNamespace,
            managedServerPodName,
            expectedStr);

        // get the restart count of the container
        // It should not increase since the Pod should not be started
        int afterRestartCount = assertDoesNotThrow(() ->
            getContainerRestartCount(domainNamespace, null, managedServerPodName, null),
            String.format("Failed to get the restart count of the container from pod %s in namespace %s",
            managedServerPodName, domainNamespace));
        logger.info("[2] restart count is: {0}", afterRestartCount);
        assertEquals(0, afterRestartCount - beforeRestartCount,
            String.format("Liveness probe starts the container in pod %s in namespace %s",
            managedServerPodName, domainNamespace));
      }
    }
  }

  /**
   * Patch the domain with custom livenessProbe failureThreshold and
   * successThreshold value in serverPod.
   * Verify the domain is restarted.
   * Verify failureThreshold and successThreshold value is updated.
   * Verify the failureThreshold runtime behavior.
   */
  @Test
  @DisplayName("Test custom livenessProbe failureThreshold and successThreshold in serverPod")
  void testCustomLivenessProbeFailureThresholdSuccessThreshold() {
    DomainResource domain1 = assertDoesNotThrow(() -> getDomainCustomResource(domainUid, domainNamespace),
        String.format("getDomainCustomResource failed with ApiException when tried to get domain %s in namespace %s",
            domainUid, domainNamespace));
    assertNotNull(domain1, "Got null domain resource");
    assertNotNull(domain1.getSpec(), "domain1.getSpec() is null");
    assertNotNull(domain1.getSpec().getServerPod(), "domain1.getSpec().getServerPod() is null");
    assertNotNull(domain1.getSpec().getServerPod().getLivenessProbe(),
        "domain1.getSpec().getServerPod().getLivenessProbe() is null");

    // get the original failureThreshold of livenessProbe
    Integer failureThreshold = domain1.getSpec().getServerPod().getLivenessProbe().getFailureThreshold();
    logger.info("Original livenessProbe failureThreshold is: {0}", failureThreshold);
    assertEquals(1, failureThreshold.intValue(), "The original livenessProbe failureThreshold is not 1");

    // get the original successThreshold of livenessProbe
    Integer successThreshold = domain1.getSpec().getServerPod().getLivenessProbe().getSuccessThreshold();
    logger.info("Original livenessProbe successThreshold is: {0}", successThreshold);
    assertEquals(1, successThreshold.intValue(), "The original livenessProbe successThreshold is not 1");

    // get the original admin server pod and managed server pods creation time
    OffsetDateTime adminPodCreationTime =
        assertDoesNotThrow(() -> getPodCreationTimestamp(domainNamespace, "", adminServerPodName),
            String.format("Failed to get creationTimestamp for pod %s", adminServerPodName));
    assertNotNull(adminPodCreationTime, "creationTimestamp of the admin server pod is null");

    Map<String, OffsetDateTime> managedServerPodsCreationTime = new HashMap<>();
    for (int i = 1; i <= NUMBER_OF_CLUSTERS_MIIDOMAIN; i++) {
      for (int j = 1; j <= replicaCount; j++) {
        String managedServerPodName =
            domainUid + "-" + CLUSTER_NAME_PREFIX + i + "-" + MANAGED_SERVER_NAME_BASE + j;
        OffsetDateTime managedServerPodCreationTime =
            assertDoesNotThrow(() -> getPodCreationTimestamp(domainNamespace, "", managedServerPodName),
                String.format("Failed to get creationTimestamp for pod %s", managedServerPodName));
        assertNotNull(managedServerPodCreationTime, "creationTimestamp of the managed server pod is null");
        managedServerPodsCreationTime.put(managedServerPodName, managedServerPodCreationTime);
      }
    }

    // patch the domain with custom failureThreshold
    String patchStr
        = "[{\"op\": \"replace\", \"path\": \"/spec/serverPod/livenessProbe/failureThreshold\", \"value\": 3},"
        + "{\"op\": \"add\", \"path\": \"/spec/serverPod/livenessProbe/periodSeconds\", \"value\": 30}]";
    logger.info("Updating domain configuration using patch string: {0}", patchStr);
    assertTrue(patchDomainCustomResource(domainUid, domainNamespace, new V1Patch(patchStr), PATCH_FORMAT_JSON_PATCH),
        String.format("failed to patch domain %s in namespace %s", domainUid, domainNamespace));

    // check the domain get restarted
    checkPodRestarted(domainUid, domainNamespace, adminServerPodName, adminPodCreationTime);

    for (int i = 1; i <= NUMBER_OF_CLUSTERS_MIIDOMAIN; i++) {
      for (int j = 1; j <= replicaCount; j++) {
        String managedServerPodName =
            domainUid + "-" + CLUSTER_NAME_PREFIX + i + "-" + MANAGED_SERVER_NAME_BASE + j;
        checkPodRestarted(domainUid, domainNamespace, managedServerPodName,
            managedServerPodsCreationTime.get(managedServerPodName));
      }
    }

    // check the admin server is up and running
    checkPodReadyAndServiceExists(adminServerPodName, domainUid, domainNamespace);

    // check the managed servers are up and running
    for (int i = 1; i <= NUMBER_OF_CLUSTERS_MIIDOMAIN; i++) {
      for (int j = 1; j <= replicaCount; j++) {
        String managedServerPodName =
            domainUid + "-" + CLUSTER_NAME_PREFIX + i + "-" + MANAGED_SERVER_NAME_BASE + j;
        checkPodReadyAndServiceExists(managedServerPodName, domainUid, domainNamespace);
      }
    }

    // check the livenessProbe failureThreshold and successThreshold after the domain got patched
    domain1 = assertDoesNotThrow(() -> getDomainCustomResource(domainUid, domainNamespace),
        String.format("getDomainCustomResource failed with ApiException when tried to get domain %s in namespace %s",
            domainUid, domainNamespace));
    assertNotNull(domain1, "Got null domain resource");
    assertNotNull(domain1.getSpec(), "domain1.getSpec() is null");
    assertNotNull(domain1.getSpec().getServerPod(), "domain1.getSpec().getServerPod() is null");
    assertNotNull(domain1.getSpec().getServerPod().getLivenessProbe(),
        "domain1.getSpec().getServerPod().getLivenessProbe() is null");

    // get the failureThreshold of livenessProbe after patch
    failureThreshold = domain1.getSpec().getServerPod().getLivenessProbe().getFailureThreshold();
    logger.info("livenessProbe failureThreshold after patch is: {0}", failureThreshold);
    assertEquals(3, failureThreshold.intValue(), "The livenessProbe failureThreshold after patch is not 3");

    // verify the failureThreshold behavior of livenessProbe
    // copy temp file to pod and verify the restartCount only happens after 1m 30 second
    for (int i = 1; i <= NUMBER_OF_CLUSTERS_MIIDOMAIN; i++) {
      for (int j = 1; j <= replicaCount; j++) {
        String managedServerPodName =
            domainUid + "-" + CLUSTER_NAME_PREFIX + i + "-" + MANAGED_SERVER_NAME_BASE + j;

        // get the restart count of the container in pod before liveness probe restarts
        int beforeRestartCount =
            assertDoesNotThrow(() -> getContainerRestartCount(domainNamespace, null,
                managedServerPodName, null),
                String.format("Failed to get the restart count of the container from pod %s in namespace %s",
                    managedServerPodName, domainNamespace));
        logger.info("For server {0} restart count before liveness probe is: {1}",
            managedServerPodName, beforeRestartCount);

        String destLocation = "/u01/tempFile.txt";
        testUntil(
            checkCopyFileToPod(domainNamespace, managedServerPodName, "weblogic-server",
                tempFile.toPath(), Paths.get(destLocation)),
            logger,
            "copying file {0} to pod {1} in namspace {2}",
            tempFile.toPath(),
            managedServerPodName,
            domainNamespace);
        logger.info("File copied to Pod {0} in namespace {1}", managedServerPodName, domainNamespace);

        // check the pod should be restarted after 1m since the livenessProbe periodSeconds is changed to 30s.
        // sleep for 45s
        try {
          Thread.sleep(45000);
        } catch (InterruptedException ie) {
          // ignore
        }
        int afterDelayRestartCount =
            assertDoesNotThrow(() -> getContainerRestartCount(domainNamespace, null,
                managedServerPodName, null),
                String.format("Failed to get the restart count of the container from pod %s in namespace %s after 1m",
                    managedServerPodName, domainNamespace));
        logger.info("checking after 45s the restartCount is not changed.");
        assertEquals(beforeRestartCount, afterDelayRestartCount, "The pod was restarted after 45s, "
            + "it should restart after that");

        String expectedStr = "Hello World, you have reached server "
            + CLUSTER_NAME_PREFIX + i + "-" + MANAGED_SERVER_NAME_BASE + j;
        checkAppNotRunning(domainNamespace, managedServerPodName, expectedStr);
        checkAppIsRunning(domainNamespace, managedServerPodName, expectedStr);

        // get the restart count of the container in pod after liveness probe restarts
        int afterRestartCount = assertDoesNotThrow(() ->
                getContainerRestartCount(domainNamespace, null, managedServerPodName, null),
            String.format("Failed to get the restart count of the container from pod %s in namespace %s",
                managedServerPodName, domainNamespace));
        logger.info("Restart count after liveness probe {0}", afterRestartCount);
        assertEquals(1, afterRestartCount - beforeRestartCount,
            String.format("Liveness probe did not start the container in pod %s in namespace %s",
                managedServerPodName, domainNamespace));
      }
    }
  }

  /**
   * Patch the domain with custom livenessProbe successThreshold value in serverPod to an invalid value.
   * Verify the patch operation failed.
   */
  @Test
  @DisplayName("Test custom livenessProbe invalid successThreshold in serverPod")
  void testCustomLivenessProbeNegativeSuccessThreshold() {
    DomainResource domain1 = assertDoesNotThrow(() -> getDomainCustomResource(domainUid, domainNamespace),
        String.format("getDomainCustomResource failed with ApiException when tried to get domain %s in namespace %s",
            domainUid, domainNamespace));
    assertNotNull(domain1, "Got null domain resource");
    assertNotNull(domain1.getSpec(), "domain1.getSpec() is null");
    assertNotNull(domain1.getSpec().getServerPod(), "domain1.getSpec().getServerPod() is null");
    assertNotNull(domain1.getSpec().getServerPod().getLivenessProbe(),
        "domain1.getSpec().getServerPod().getLivenessProbe() is null");

    // patch the domain with custom livenessProbe successThreshold
    String patchStr
        = "[{\"op\": \"replace\", \"path\": \"/spec/serverPod/livenessProbe/successThreshold\", \"value\": 2}]";
    logger.info("Updating domain configuration using patch string: {0}", patchStr);
    assertTrue(!patchDomainCustomResource(domainUid, domainNamespace, new V1Patch(patchStr), PATCH_FORMAT_JSON_PATCH),
        String.format("Patch domain %s in namespace %s should fail", domainUid, domainNamespace));
  }

  /**
   * Create a new domain with custom livenessProbe successThreshold value in serverPod to an invalid value.
   * Verify the create of domain resource failed.
   */
  @Test
  @DisplayName("Test custom livenessProbe successThreshold in serverPod with an invalid value")
  void testCustomLivenessProbeInvalidNegative() {
    DomainResource domain = createDomainResource(domainUid2);
    domain.getSpec().serverPod().livenessProbe().successThreshold(2);

    // create model in image domain
    logger.info("Creating model in image domain {0} in namespace {1} using image {2}",
        domainUid2, domainNamespace, imageName);

    // check the operator log contains expected error msg
    String expectedErrorMsg = "The liveness probe successThreshold value must be 1";

    logger.info("Creating domain custom resource for domainUid {0} in namespace {1}",
        domainUid2, domainNamespace);
    boolean succeeded = true;
    ApiException exception = null;
    try {
      succeeded = createDomainCustomResource(domain, DOMAIN_VERSION);
    } catch (ApiException e) {
      exception = e;
    }
    assertTrue(failedWithExpectedErrorMsg(succeeded, exception, expectedErrorMsg),
            String.format("Create domain custom resource unexpectedly succeeded for %s in namespace %s",
                domainUid2, domainNamespace));
  }

  /**
   * Verify liveness probe by killing managed server process 3 times to kick pod container auto-restart.
   */
  @Test
  @DisplayName("Test liveness probe of pod")
  void testLivenessProbe() {
    DomainResource domain = assertDoesNotThrow(() -> getDomainCustomResource(domainUid, domainNamespace),
        String.format("getDomainCustomResource failed with ApiException when tried to get domain %s in namespace %s",
            domainUid, domainNamespace));
    assertNotNull(domain, "Got null domain resource");

    String domainUid = domain.getSpec().getDomainUid();
    String domainNamespace = domain.getMetadata().getNamespace();

    String serverNamePrefix = domainUid + "-cluster-1-" + MANAGED_SERVER_NAME_BASE;

    // create file to kill server process
    File killServerScript = assertDoesNotThrow(() -> createScriptToKillServer(),
        "Failed to create script to kill server");
    logger.info("File/script created to kill server {0}", killServerScript);

    String server1Name = serverNamePrefix + "1";
    checkPodReady(server1Name, domainUid, domainNamespace);

    // copy script to pod
    String destLocation = "/u01/killserver.sh";
    assertDoesNotThrow(() -> copyFileToPod(domainNamespace, server1Name, "weblogic-server",
        killServerScript.toPath(), Paths.get(destLocation)),
        String.format("Failed to copy file %s to pod %s in namespace %s",
            killServerScript, server1Name, domainNamespace));
    logger.info("File copied to Pod {0} in namespace {1}", server1Name, domainNamespace);

    // get the restart count of the container in pod before liveness probe restarts
    final int beforeRestartCount =
        assertDoesNotThrow(() -> getContainerRestartCount(domainNamespace, null, server1Name, null),
            String.format("Failed to get the restart count of the container from pod %s in namespace %s",
                server1Name, domainNamespace));
    logger.info("Restart count before liveness probe {0}", beforeRestartCount);

    // change file permissions
    ExecResult execResult = assertDoesNotThrow(() -> execCommand(domainNamespace, server1Name, null,
        true, "/bin/sh", "-c", "chmod +x " + destLocation),
        String.format("Failed to change permissions for file %s in pod %s", destLocation, server1Name));
    assertTrue(execResult.exitValue() == 0,
        String.format("Failed to change file %s permissions, stderr %s stdout %s", destLocation,
            execResult.stderr(), execResult.stdout()));
    logger.info("File permissions changed inside pod");

    /* First, kill the managed server process in the container three times to cause the node manager to
     * mark the server 'failed not restartable'. This in turn is detected by the liveness probe, which
     * initiates a container restart.
     */
    for (int i = 0; i < 3; i++) {
      execResult = assertDoesNotThrow(() -> execCommand(domainNamespace, server1Name, null,
          true, "/bin/sh", "-c", destLocation + " " + server1Name),
          String.format("Failed to execute script %s in pod %s namespace %s", destLocation,
              server1Name, domainNamespace));
      logger.info("Command executed to kill server inside pod, exit value {0}, stdout {1}, stderr {2}",
          execResult.exitValue(), execResult.stdout(), execResult.stderr());

      try {
        Thread.sleep(2 * 1000);
      } catch (InterruptedException ie) {
        // ignore
      }
    }

    // check pod is ready
    checkPodReady(server1Name, domainUid, domainNamespace);

    // get the restart count of the container in pod after liveness probe restarts
    int afterRestartCount = assertDoesNotThrow(() ->
            getContainerRestartCount(domainNamespace, null, server1Name, null),
        String.format("Failed to get the restart count of the container from pod %s in namespace %s",
            server1Name, domainNamespace));
    assertTrue(afterRestartCount - beforeRestartCount == 1,
        String.format("Liveness probe did not start the container in pod %s in namespace %s",
            server1Name, domainNamespace));

    for (int j = 1; j <= replicaCount; j++) {
      String managedServerPodName = domainUid + "-cluster-1-" + MANAGED_SERVER_NAME_BASE + j;
      String expectedStr = "Hello World, you have reached server " + "cluster-1-" + MANAGED_SERVER_NAME_BASE + j;
      checkAppIsRunning(
          domainNamespace,
          managedServerPodName,
          expectedStr);
    }
  }

  private boolean failedWithExpectedErrorMsg(boolean succeeded, ApiException exception, String expectedErrorMsg) {
    return !succeeded || hasExpectedException(exception, expectedErrorMsg);
  }

  private boolean hasExpectedException(ApiException exception, String expectedMsg) {
    return exception != null && exception.getResponseBody().contains(expectedMsg);
  }

  @Nonnull
  private static DomainResource createDomainResource(String domainName) {

    // create the domain CR
    DomainResource domain = new DomainResource()
        .apiVersion(DOMAIN_API_VERSION)
        .kind("Domain")
        .metadata(new V1ObjectMeta()
            .name(domainName)
            .namespace(domainNamespace))
        .spec(new DomainSpec()
            .domainUid(domainName)
            .domainHomeSourceType("FromModel")
            .image(imageName)
            .addImagePullSecretsItem(new V1LocalObjectReference()
                .name(TEST_IMAGES_REPO_SECRET_NAME))
            .webLogicCredentialsSecret(new V1LocalObjectReference()
                .name(WEBLOGIC_CREDENTIALS))
            .includeServerOutInPodLog(true)
            .serverStartPolicy("IfNeeded")
            .serverPod(new ServerPod()
                .addEnvItem(new V1EnvVar()
                    .name("JAVA_OPTIONS")
                    .value("-Dweblogic.StdoutDebugEnabled=false"))
                .addEnvItem(new V1EnvVar()
                    .name("LIVENESS_PROBE_CUSTOM_SCRIPT")
                    .value("/u01/customLivenessProbe.sh"))
                .livenessProbe(new ProbeTuning()
                    .failureThreshold(1)
                    .successThreshold(1))
                .readinessProbe(new ProbeTuning()
                    .failureThreshold(1)
                    .successThreshold(1))
                .resources(new V1ResourceRequirements()
                    .limits(new HashMap<>())
                    .requests(new HashMap<>())))
            .configuration(new Configuration()
                .model(new Model()
                    .domainType(WLS_DOMAIN_TYPE)
                    .runtimeEncryptionSecret(ENCRYPTION_SECRET))));
    setPodAntiAffinity(domain);
    return domain;
  }

  /**
   * Patch the domain with custom readinessProbe failureThreshold and successThreshold value in serverPod.
   * Verify the domain is restarted and the failureThreshold and sucessThreshold are updated.
   */
  @Test
  @DisplayName("Test custom readinessProbe failureThreshold and successThreshold")
  void testCustomReadinessProbeFailureThresholdSuccessThreshold() {
    DomainResource domain1 = assertDoesNotThrow(() -> getDomainCustomResource(domainUid, domainNamespace),
        String.format("getDomainCustomResource failed with ApiException when tried to get domain %s in namespace %s",
            domainUid, domainNamespace));
    assertNotNull(domain1, "Got null domain resource");
    assertNotNull(domain1.getSpec(), "domain1.getSpec() is null");
    assertNotNull(domain1.getSpec().getServerPod(), "domain1.getSpec().getServerPod() is null");
    assertNotNull(domain1.getSpec().getServerPod().getLivenessProbe(),
        "domain1.getSpec().getServerPod().getLivenessProbe() is null");

    // get the original failureThreshold of readinessProbe
    Integer failureThreshold = domain1.getSpec().getServerPod().getReadinessProbe().getFailureThreshold();
    logger.info("Original readinessProbe failureThreshold is: {0}", failureThreshold);
    assertEquals(1, failureThreshold.intValue(), "The original readinessProbe failureThreshold is not 1");

    // get the original successThreshold of readinessProbe
    Integer successThreshold = domain1.getSpec().getServerPod().getReadinessProbe().getSuccessThreshold();
    logger.info("Original readinessProbe successThreshold is: {0}", successThreshold);
    assertEquals(1, successThreshold.intValue(), "The original readinessProbe successThreshold is not 1");

    // get the original admin server pod and managed server pods creation time
    OffsetDateTime adminPodCreationTime =
        assertDoesNotThrow(() -> getPodCreationTimestamp(domainNamespace, "", adminServerPodName),
            String.format("Failed to get creationTimestamp for pod %s", adminServerPodName));
    assertNotNull(adminPodCreationTime, "creationTimestamp of the admin server pod is null");

    Map<String, OffsetDateTime> managedServerPodsCreationTime = new HashMap<>();
    for (int i = 1; i <= NUMBER_OF_CLUSTERS_MIIDOMAIN; i++) {
      for (int j = 1; j <= replicaCount; j++) {
        String managedServerPodName =
            domainUid + "-" + CLUSTER_NAME_PREFIX + i + "-" + MANAGED_SERVER_NAME_BASE + j;
        OffsetDateTime managedServerPodCreationTime =
            assertDoesNotThrow(() -> getPodCreationTimestamp(domainNamespace, "", managedServerPodName),
                String.format("Failed to get creationTimestamp for pod %s", managedServerPodName));
        assertNotNull(managedServerPodCreationTime, "creationTimestamp of the managed server pod is null");
        managedServerPodsCreationTime.put(managedServerPodName, managedServerPodCreationTime);
      }
    }

    // patch the domain with custom readinessProbe failureThreshold and successThreshold in serverPod
    String patchStr
        = "[{\"op\": \"replace\", \"path\": \"/spec/serverPod/readinessProbe/failureThreshold\", \"value\": 3}, "
        + "{\"op\": \"replace\", \"path\": \"/spec/serverPod/readinessProbe/successThreshold\", \"value\": 3}]";
    logger.info("Updating domain configuration using patch string: {0}", patchStr);
    assertTrue(patchDomainCustomResource(domainUid, domainNamespace, new V1Patch(patchStr), PATCH_FORMAT_JSON_PATCH),
        String.format("failed to patch domain %s in namespace %s", domainUid, domainNamespace));

    // check the domain get restarted
    checkPodRestarted(domainUid, domainNamespace, adminServerPodName, adminPodCreationTime);

    for (int i = 1; i <= NUMBER_OF_CLUSTERS_MIIDOMAIN; i++) {
      for (int j = 1; j <= replicaCount; j++) {
        String managedServerPodName =
            domainUid + "-" + CLUSTER_NAME_PREFIX + i + "-" + MANAGED_SERVER_NAME_BASE + j;
        checkPodRestarted(domainUid, domainNamespace, managedServerPodName,
            managedServerPodsCreationTime.get(managedServerPodName));
      }
    }

    // check the admin server is up and running
    checkPodReadyAndServiceExists(adminServerPodName, domainUid, domainNamespace);

    // check the managed servers are up and running
    for (int i = 1; i <= NUMBER_OF_CLUSTERS_MIIDOMAIN; i++) {
      for (int j = 1; j <= replicaCount; j++) {
        String managedServerPodName =
            domainUid + "-" + CLUSTER_NAME_PREFIX + i + "-" + MANAGED_SERVER_NAME_BASE + j;
        checkPodReadyAndServiceExists(managedServerPodName, domainUid, domainNamespace);
      }
    }

    // check the readinessProbe failureThreshold and successThreshold after the domain got patched
    domain1 = assertDoesNotThrow(() -> getDomainCustomResource(domainUid, domainNamespace),
        String.format("getDomainCustomResource failed with ApiException when tried to get domain %s in namespace %s",
            domainUid, domainNamespace));
    assertNotNull(domain1, "Got null domain resource");
    assertNotNull(domain1.getSpec(), "domain1.getSpec() is null");
    assertNotNull(domain1.getSpec().getServerPod(), "domain1.getSpec().getServerPod() is null");
    assertNotNull(domain1.getSpec().getServerPod().getReadinessProbe(),
        "domain1.getSpec().getServerPod().getReadinessProbe() is null");

    // get the failureThreshold of readinessProbe after patch
    failureThreshold = domain1.getSpec().getServerPod().getReadinessProbe().getFailureThreshold();
    logger.info("The readinessProbe failureThreshold after patch is: {0}", failureThreshold);
    assertEquals(3, failureThreshold.intValue(), "The readinessProbe failureThreshold after patch is not 3");

    // get the successThreshold of readinessProbe
    successThreshold = domain1.getSpec().getServerPod().getReadinessProbe().getSuccessThreshold();
    logger.info("readinessProbe successThreshold after patch is: {0}", successThreshold);
    assertEquals(3, successThreshold.intValue(), "The readinessProbe successThreshold after patch is not 3");

    // patch the domain to the original state
    // get the original admin server pod and managed server pods creation time
    adminPodCreationTime =
        assertDoesNotThrow(() -> getPodCreationTimestamp(domainNamespace, "", adminServerPodName),
            String.format("Failed to get creationTimestamp for pod %s", adminServerPodName));
    assertNotNull(adminPodCreationTime, "creationTimestamp of the admin server pod is null");

    managedServerPodsCreationTime = new HashMap<>();
    for (int i = 1; i <= NUMBER_OF_CLUSTERS_MIIDOMAIN; i++) {
      for (int j = 1; j <= replicaCount; j++) {
        String managedServerPodName =
            domainUid + "-" + CLUSTER_NAME_PREFIX + i + "-" + MANAGED_SERVER_NAME_BASE + j;
        OffsetDateTime managedServerPodCreationTime =
            assertDoesNotThrow(() -> getPodCreationTimestamp(domainNamespace, "", managedServerPodName),
                String.format("Failed to get creationTimestamp for pod %s", managedServerPodName));
        assertNotNull(managedServerPodCreationTime, "creationTimestamp of the managed server pod is null");
        managedServerPodsCreationTime.put(managedServerPodName, managedServerPodCreationTime);
      }
    }

    // patch the domain with original readinessProbe failureThreshold and successThreshold in serverPod
    patchStr
        = "[{\"op\": \"replace\", \"path\": \"/spec/serverPod/readinessProbe/failureThreshold\", \"value\": 1}, "
        + "{\"op\": \"replace\", \"path\": \"/spec/serverPod/readinessProbe/successThreshold\", \"value\": 1}]";
    logger.info("Updating domain configuration using patch string: {0}", patchStr);
    assertTrue(patchDomainCustomResource(domainUid, domainNamespace, new V1Patch(patchStr), PATCH_FORMAT_JSON_PATCH),
        String.format("failed to patch domain %s in namespace %s", domainUid, domainNamespace));

    // check the domain get restarted
    checkPodRestarted(domainUid, domainNamespace, adminServerPodName, adminPodCreationTime);

    for (int i = 1; i <= NUMBER_OF_CLUSTERS_MIIDOMAIN; i++) {
      for (int j = 1; j <= replicaCount; j++) {
        String managedServerPodName =
            domainUid + "-" + CLUSTER_NAME_PREFIX + i + "-" + MANAGED_SERVER_NAME_BASE + j;
        checkPodRestarted(domainUid, domainNamespace, managedServerPodName,
            managedServerPodsCreationTime.get(managedServerPodName));
      }
    }

    // check the admin server is up and running
    checkPodReadyAndServiceExists(adminServerPodName, domainUid, domainNamespace);

    // check the managed servers are up and running
    for (int i = 1; i <= NUMBER_OF_CLUSTERS_MIIDOMAIN; i++) {
      for (int j = 1; j <= replicaCount; j++) {
        String managedServerPodName =
            domainUid + "-" + CLUSTER_NAME_PREFIX + i + "-" + MANAGED_SERVER_NAME_BASE + j;
        checkPodReadyAndServiceExists(managedServerPodName, domainUid, domainNamespace);
      }
    }
  }

  /**
   * Create a model in image domain and verify the server pods are ready.
   */
  private static void createAndVerifyMiiDomain(String miiImage) {

    // repo login and push image to registry if necessary
    imageRepoLoginAndPushImageToRegistry(miiImage);

    // create registry secret to pull the image from registry
    // this secret is used only for non-kind cluster
    logger.info("Creating registry secret in namespace {0}", domainNamespace);
    createTestRepoSecret(domainNamespace);

    // create secret for admin credentials
    logger.info("Creating secret for admin credentials");
    createSecretWithUsernamePassword(WEBLOGIC_CREDENTIALS, domainNamespace,
        ADMIN_USERNAME_DEFAULT, ADMIN_PASSWORD_DEFAULT);

    // create encryption secret
    logger.info("Creating encryption secret");
    createSecretWithUsernamePassword(ENCRYPTION_SECRET, domainNamespace, "weblogicenc", "weblogicenc");


    DomainResource domain = createDomainResource(domainUid);

    // create model in image domain
    logger.info("Creating model in image domain {0} in namespace {1} using image {2}",
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
    testUntil(
        () -> appAccessibleInPod(
          namespace,
          podName,
          internalPort,
          appPath,
          expectedStr),
        logger,
        "application {0} is running on pod {1} in namespace {2}",
        appPath,
        podName,
        namespace);
  }

  private static void checkAppNotRunning(
      String namespace,
      String podName,
      String expectedStr
  ) {

    // check that the application is NOT running inside of a server pod
    testUntil(
        () -> appNotAccessibleInPod(
          namespace,
          podName,
          internalPort,
          appPath,
          expectedStr),
        logger,
        "app {0} is not running on pod {1} in namespace {2}",
        appPath,
        podName,
        namespace);
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
    logger.info("additionalBuildFilesVarargsBuff: " + additionalBuildFilesVarargsBuff);

    final String wdtModelFileForMiiDomain = "model-multiclusterdomain-singlesampleapp-wls.yaml";
    logger.info("Create image with model file and verify");
    String miiImage =
        createMiiImageAndVerify(MII_IMAGE_NAME, wdtModelFileForMiiDomain, MII_BASIC_APP_NAME,
            additionalBuildCommands, additionalBuildFilesVarargsBuff.toString());

    // repo login and push image to registry if necessary
    imageRepoLoginAndPushImageToRegistry(miiImage);

    // create registry secret to pull the image from registry
    // this secret is used only for non-kind cluster
    logger.info("Create registry secret in namespace {0}", domainNamespace);
    createTestRepoSecret(domainNamespace);
    return miiImage;
  }

  private static File createTempfile(String filename) throws IOException {
    File tempFile = File.createTempFile(filename, ".txt", new File(RESULTS_TEMPFILE));
    //deletes the file when VM terminates
    tempFile.deleteOnExit();
    try (FileWriter fw = new FileWriter(tempFile)) {
      fw.write("This one line file is to test liveness Probe custom script");
    }
    return tempFile;
  }

  /**
   * Create a script to kill server.
   * @return a File object
   * @throws IOException if can not create a file
   */
  private File createScriptToKillServer() throws IOException {
    File killServerScript = File.createTempFile("killserver", ".sh", new File(RESULTS_TEMPFILE));
    //deletes the file when VM terminates
    killServerScript.deleteOnExit();
    try (FileWriter fw = new FileWriter(killServerScript)) {
      fw.write("#!/bin/bash\n");
      fw.write("jps\n");
      fw.write("jps | grep Server\n");
      fw.write("jps | grep Server | awk '{print $1}'\n");
      fw.write("kill -9 `jps | grep Server | awk '{print $1}'`");
    }
    killServerScript.setExecutable(true, false);
    return killServerScript;
  }
}
