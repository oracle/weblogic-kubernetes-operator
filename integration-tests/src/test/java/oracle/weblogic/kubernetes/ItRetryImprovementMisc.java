// Copyright (c) 2022, 2023, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.weblogic.kubernetes;

import java.time.OffsetDateTime;
import java.util.Collections;
import java.util.List;

import io.kubernetes.client.custom.V1Patch;
import oracle.weblogic.kubernetes.annotations.IntegrationTest;
import oracle.weblogic.kubernetes.annotations.Namespaces;
import oracle.weblogic.kubernetes.logging.LoggingFacade;
import org.awaitility.core.ConditionFactory;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.SECONDS;
import static oracle.weblogic.kubernetes.TestConstants.ADMIN_SERVER_NAME_BASE;
import static oracle.weblogic.kubernetes.TestConstants.DOMAIN_STATUS_CONDITION_AVAILABLE_TYPE;
import static oracle.weblogic.kubernetes.TestConstants.DOMAIN_STATUS_CONDITION_COMPLETED_TYPE;
import static oracle.weblogic.kubernetes.TestConstants.DOMAIN_VERSION;
import static oracle.weblogic.kubernetes.TestConstants.MANAGED_SERVER_NAME_BASE;
import static oracle.weblogic.kubernetes.TestConstants.MII_BASIC_IMAGE_NAME;
import static oracle.weblogic.kubernetes.TestConstants.MII_BASIC_IMAGE_TAG;
import static oracle.weblogic.kubernetes.actions.TestActions.deletePod;
import static oracle.weblogic.kubernetes.actions.TestActions.getPodCreationTimestamp;
import static oracle.weblogic.kubernetes.actions.TestActions.patchClusterCustomResource;
import static oracle.weblogic.kubernetes.assertions.TestAssertions.domainStatusClustersConditionTypeHasExpectedStatus;
import static oracle.weblogic.kubernetes.assertions.TestAssertions.domainStatusConditionTypeHasExpectedStatus;
import static oracle.weblogic.kubernetes.utils.ClusterUtils.checkDomainStatusClusterConditionTypeHasExpectedStatus;
import static oracle.weblogic.kubernetes.utils.CommonMiiTestUtils.createMiiDomain;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.checkPodReadyAndServiceExists;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.withQuickRetryPolicy;
import static oracle.weblogic.kubernetes.utils.DomainUtils.checkDomainStatusConditionTypeHasExpectedStatus;
import static oracle.weblogic.kubernetes.utils.OperatorUtils.installAndVerifyOperator;
import static oracle.weblogic.kubernetes.utils.PodUtils.checkPodDeleted;
import static oracle.weblogic.kubernetes.utils.PodUtils.checkPodDoesNotExist;
import static oracle.weblogic.kubernetes.utils.PodUtils.checkPodReady;
import static oracle.weblogic.kubernetes.utils.PodUtils.checkPodRestarted;
import static oracle.weblogic.kubernetes.utils.ThreadSafeLogger.getLogger;
import static org.awaitility.Awaitility.with;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

// Miscellaneous use cases for WKO retry improvements
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DisplayName("Miscellaneous use cases for WKO retry improvements")
@IntegrationTest
@Tag("kind-parallel")
@Tag("olcne")
class ItRetryImprovementMisc {

  private static String domainNamespace = null;
  private static String domainUid = "rimiscdomain1";

  private static String adminServerPodName = String.format("%s-%s", domainUid, ADMIN_SERVER_NAME_BASE);
  private static String managedServerPrefix = String.format("%s-%s", domainUid, MANAGED_SERVER_NAME_BASE);
  private static int replicaCount = 1;
  private static LoggingFacade logger = null;
  private static String clusterName = "cluster-1";

  private ConditionFactory shortRetryPolicy = with().pollDelay(0, SECONDS)
      .and().with().pollInterval(100, MILLISECONDS)
      .atMost(30, SECONDS).await();

  /**
   * Perform initialization for all the tests in this class.
   * Set up the necessary namespaces, install the operator in the first namespace.
   *
   * @param namespaces list of namespaces created by the IntegrationTestWatcher by the
   *           JUnit engine parameter resolution mechanism
   */
  @BeforeAll
  public static void initAll(@Namespaces(2) List<String> namespaces) {
    logger = getLogger();

    // get a namespace for operator
    assertNotNull(namespaces.get(0), "Namespace namespaces.get(0) is null");
    String opNamespace = namespaces.get(0);

    // get a namespace for domain1
    assertNotNull(namespaces.get(1), "Namespace namespaces.get(1) is null");
    domainNamespace = namespaces.get(1);

    String opServiceAccount = opNamespace + "-sa";

    // install and verify operator with REST API
    logger.info("Install an operator in namespace {0}, managing namespace {1}", opNamespace, domainNamespace);
    installAndVerifyOperator(opNamespace, opServiceAccount, true, 0, domainNamespace);
  }

  /**
   * Test domain and cluster status conditions while starting a single cluster domain.
   * Start a domain with a single cluster, where cluster has 1 server.
   * Verify Cluster and Domain status conditions are Unavailable and Incomplete after the domain is created and
   * waiting for all the servers to be ready.
   * Verify Cluster and Domain status conditions become Available and Complete once all pods are ready.
   */
  @Order(1)
  @Test
  @DisplayName("Test domain and cluster status conditions while starting a single cluster domain.")
  void testStartSingleClusterDomainWithOneServer() {
    // create a mii domain resource with one cluster
    logger.info("Create model-in-image domain {0} in namespace {1}, and wait until it comes up",
        domainUid, domainNamespace);
    createMiiDomain(
        domainNamespace,
        domainUid,
        MII_BASIC_IMAGE_NAME + ":" + MII_BASIC_IMAGE_TAG,
        replicaCount,
        Collections.singletonList(clusterName),
        false,
        null);

    // check the domain status is Unavailable and Incomplete
    checkDomainStatusConditionTypeHasExpectedStatus(domainUid, domainNamespace,
        DOMAIN_STATUS_CONDITION_COMPLETED_TYPE, "False");
    checkDomainStatusConditionTypeHasExpectedStatus(domainUid, domainNamespace,
        DOMAIN_STATUS_CONDITION_AVAILABLE_TYPE, "False");

    // check the cluster status is Unavailable and Incomplete
    checkDomainStatusClusterConditionTypeHasExpectedStatus(domainUid, domainNamespace, clusterName,
        DOMAIN_STATUS_CONDITION_COMPLETED_TYPE, "False");
    checkDomainStatusClusterConditionTypeHasExpectedStatus(domainUid, domainNamespace, clusterName,
        DOMAIN_STATUS_CONDITION_AVAILABLE_TYPE, "False");

    // wait for admin server pod is ready
    logger.info("Wait for admin server pod {0} to be ready in namespace {1}",
        adminServerPodName, domainNamespace);
    checkPodReadyAndServiceExists(adminServerPodName, domainUid, domainNamespace);

    // check the domain status is Unavailable and Incomplete while waiting for the managed servers are ready
    assertTrue(assertDoesNotThrow(
        () -> domainStatusConditionTypeHasExpectedStatus(domainUid, domainNamespace,
            DOMAIN_STATUS_CONDITION_COMPLETED_TYPE, "False").call()),
        "domain status condition Completed type does not have expected False value");
    assertTrue(assertDoesNotThrow(
        () -> domainStatusConditionTypeHasExpectedStatus(domainUid, domainNamespace,
            DOMAIN_STATUS_CONDITION_AVAILABLE_TYPE, "False").call()),
        "domain status condition Available type does not have expected False value");

    // check the cluster condition is Unavailable and Incomplete while waiting for the managed servers are ready
    assertTrue(assertDoesNotThrow(
        () -> domainStatusClustersConditionTypeHasExpectedStatus(domainUid, domainNamespace, clusterName,
            DOMAIN_STATUS_CONDITION_COMPLETED_TYPE, "False", DOMAIN_VERSION).call()),
        "domain status cluster condition Completed type does not have expected False value");
    assertTrue(assertDoesNotThrow(
        () -> domainStatusClustersConditionTypeHasExpectedStatus(domainUid, domainNamespace, clusterName,
            DOMAIN_STATUS_CONDITION_AVAILABLE_TYPE, "False", DOMAIN_VERSION).call()),
        "domain status cluster condition Available type does not have expected False value");

    // check managed server pods are ready
    for (int i = 1; i <= replicaCount; i++) {
      logger.info("Wait for managed server pod {0} to be ready in namespace {1}",
          managedServerPrefix + i, domainNamespace);
      checkPodReadyAndServiceExists(managedServerPrefix + i, domainUid, domainNamespace);
    }

    // check the domain status is Available and Complete after all servers are ready
    checkDomainStatusConditionTypeHasExpectedStatus(shortRetryPolicy, domainUid, domainNamespace,
        DOMAIN_STATUS_CONDITION_COMPLETED_TYPE, "True", DOMAIN_VERSION);
    checkDomainStatusConditionTypeHasExpectedStatus(shortRetryPolicy, domainUid, domainNamespace,
        DOMAIN_STATUS_CONDITION_AVAILABLE_TYPE, "True", DOMAIN_VERSION);

    // check the cluster condition is Available and Complete after all servers are ready
    checkDomainStatusClusterConditionTypeHasExpectedStatus(shortRetryPolicy, domainUid, domainNamespace,
        clusterName, DOMAIN_STATUS_CONDITION_COMPLETED_TYPE, "True", DOMAIN_VERSION);
    checkDomainStatusClusterConditionTypeHasExpectedStatus(shortRetryPolicy, domainUid, domainNamespace,
        clusterName, DOMAIN_STATUS_CONDITION_AVAILABLE_TYPE, "True", DOMAIN_VERSION);
  }

  /**
   * Test domain and cluster status conditions after setting cluster serverStartPolicy to Never.
   * Set cluster serverStartPolicy to Never.
   * Verify domain status conditions stay Available and Complete.
   * Verify cluster status conditions become Unavailable and Complete.
   */
  @Order(2)
  @Test
  @DisplayName("Test domain and cluster status conditions after setting cluster serverStartPolicy to Never.")
  void testSetClusterToNeverAndVerifyStatus() {
    logger.info("patch the cluster resource with serverStartPolicy to Never");
    String patchStr = "[{\"op\": \"replace\", \"path\": \"/spec/serverStartPolicy\", \"value\": \"Never\"}]";

    logger.info("Updating cluster configuration using patch string: {0}", patchStr);
    V1Patch patch = new V1Patch(patchStr);
    assertTrue(patchClusterCustomResource(clusterName, domainNamespace,
        patch, V1Patch.PATCH_FORMAT_JSON_PATCH), "Failed to patch cluster");

    // check domain status conditions stay Available and Complete after the cluster serverStartPolicy is set to Never
    assertTrue(assertDoesNotThrow(
        () -> domainStatusConditionTypeHasExpectedStatus(domainUid, domainNamespace,
            DOMAIN_STATUS_CONDITION_COMPLETED_TYPE, "True").call()),
        "domain status condition Completed type does not have expected True value");
    assertTrue(assertDoesNotThrow(
        () -> domainStatusConditionTypeHasExpectedStatus(domainUid, domainNamespace,
            DOMAIN_STATUS_CONDITION_AVAILABLE_TYPE, "True").call()),
        "domain status condition Available type does not have expected True value");

    // wait for the cluster status conditions go to Unavailable after the cluster serverStartPolicy is set to Never
    checkDomainStatusClusterConditionTypeHasExpectedStatus(domainUid, domainNamespace, clusterName,
        DOMAIN_STATUS_CONDITION_AVAILABLE_TYPE, "False");

    // wait for the cluster status condition go to Complete after the cluster serverStartPolicy is set to Never
    checkDomainStatusClusterConditionTypeHasExpectedStatus(domainUid, domainNamespace, clusterName,
        DOMAIN_STATUS_CONDITION_COMPLETED_TYPE, "True");

    // check the cluster pod is deleted
    for (int i = 1; i <= replicaCount; i++) {
      logger.info("Wait for managed server pod {0} to be deleted in namespace {1}",
          managedServerPrefix + i, domainNamespace);
      checkPodDeleted(managedServerPrefix + i, domainUid, domainNamespace);
    }

    // check domain status condition stay Available and Complete after the cluster server pods are deleted
    assertTrue(assertDoesNotThrow(
        () -> domainStatusConditionTypeHasExpectedStatus(domainUid, domainNamespace,
            DOMAIN_STATUS_CONDITION_COMPLETED_TYPE, "True").call()),
        "domain status condition Completed type does not have expected True value");
    assertTrue(assertDoesNotThrow(
        () -> domainStatusConditionTypeHasExpectedStatus(domainUid, domainNamespace,
            DOMAIN_STATUS_CONDITION_AVAILABLE_TYPE, "True").call()),
        "domain status condition Available type does not have expected True value");

    // check cluster conditions are Unavailable and Complete after the cluster server pods are deleted
    assertTrue(assertDoesNotThrow(
        () -> domainStatusClustersConditionTypeHasExpectedStatus(domainUid, domainNamespace, clusterName,
            DOMAIN_STATUS_CONDITION_COMPLETED_TYPE, "True", DOMAIN_VERSION).call()),
        "domain status cluster condition Completed type does not have expected True value");
    assertTrue(assertDoesNotThrow(
        () -> domainStatusClustersConditionTypeHasExpectedStatus(domainUid, domainNamespace, clusterName,
            DOMAIN_STATUS_CONDITION_AVAILABLE_TYPE, "False", DOMAIN_VERSION).call()),
        "domain status cluster condition Available type does not have expected False value");
  }

  /**
   * Test domain and cluster status conditions after set cluster serverStartPolicy from Never to IfNeeded.
   * Set cluster serverStartPolicy back to IfNeeded.
   * Verify Cluster status conditions become Available and Complete once the managed server pods are ready.
   * Verify Domain status conditions become Incomplete and Unavailable during the patching operation,
   * and come back to Complete and Available at the same time Cluster recovers.
   */
  @Order(3)
  @Test
  @DisplayName("Test domain and cluster status conditions after set cluster serverStartPolicy from Never to IfNeeded.")
  void testSetClusterToIfNeeded() {
    logger.info("patch the cluster resource with serverStartPolicy to IfNeeded");
    String patchStr = "[{\"op\": \"replace\", \"path\": \"/spec/serverStartPolicy\", \"value\": \"IfNeeded\"}]";
    
    logger.info("Updating cluster configuration using patch string: {0}", patchStr);
    V1Patch patch = new V1Patch(patchStr);
    assertTrue(patchClusterCustomResource(clusterName, domainNamespace,
        patch, V1Patch.PATCH_FORMAT_JSON_PATCH), "Failed to patch cluster");

    // check domain status conditions become Unavailable and Incomplete during the patching operation
    checkDomainStatusConditionTypeHasExpectedStatus(withQuickRetryPolicy, domainUid, domainNamespace,
        DOMAIN_STATUS_CONDITION_COMPLETED_TYPE, "False", DOMAIN_VERSION);
    checkDomainStatusConditionTypeHasExpectedStatus(withQuickRetryPolicy, domainUid, domainNamespace,
        DOMAIN_STATUS_CONDITION_AVAILABLE_TYPE, "False", DOMAIN_VERSION);

    // wait for the managed server pods are ready
    for (int i = 1; i <= replicaCount; i++) {
      logger.info("Wait for managed server pod {0} to be ready in namespace {1}",
          managedServerPrefix + i, domainNamespace);
      checkPodReadyAndServiceExists(managedServerPrefix + i, domainUid, domainNamespace);
    }

    // check the cluster status conditions are Available and Complete after the cluster servers pods are ready
    checkDomainStatusClusterConditionTypeHasExpectedStatus(shortRetryPolicy, domainUid, domainNamespace,
        clusterName, DOMAIN_STATUS_CONDITION_COMPLETED_TYPE, "True", DOMAIN_VERSION);
    checkDomainStatusClusterConditionTypeHasExpectedStatus(shortRetryPolicy, domainUid, domainNamespace,
        clusterName, DOMAIN_STATUS_CONDITION_AVAILABLE_TYPE, "True", DOMAIN_VERSION);

    // wait for the domain status conditions go to Available and Complete after the cluster servers pods are ready
    checkDomainStatusConditionTypeHasExpectedStatus(domainUid, domainNamespace,
        DOMAIN_STATUS_CONDITION_COMPLETED_TYPE, "True");
    checkDomainStatusConditionTypeHasExpectedStatus(domainUid, domainNamespace,
        DOMAIN_STATUS_CONDITION_AVAILABLE_TYPE, "True");
  }

  /**
   * Test domain and cluster status conditions after deleting a server pod of the cluster.
   * Delete a server pod in the cluster.
   * Verify domain and cluster status conditions Available and Complete should go false,
   * and both should eventually return to true when the pod auto-restarts and is ready.
   */
  @Order(4)
  @Test
  @DisplayName("Test domain and cluster status conditions after deleting a server pod of the cluster with "
      + "cluster replicas set to 1.")
  void testDeleteServerPodWithReplicasSetTo1() {

    String podName = managedServerPrefix + 1;
    OffsetDateTime ms1PodCreationTime =
        assertDoesNotThrow(() -> getPodCreationTimestamp(domainNamespace, "", podName),
            String.format("Failed to get creationTimestamp for pod %s", podName));

    // delete a cluster server pod
    assertDoesNotThrow(() -> deletePod(podName, domainNamespace),
        String.format("delete pod %s in namespace %s failed", podName, domainNamespace));

    // verify domain and cluster status conditions Available and Complete become false
    checkDomainStatusClusterConditionTypeHasExpectedStatus(withQuickRetryPolicy, domainUid, domainNamespace,
        clusterName, DOMAIN_STATUS_CONDITION_AVAILABLE_TYPE, "False", DOMAIN_VERSION);
    checkDomainStatusClusterConditionTypeHasExpectedStatus(withQuickRetryPolicy, domainUid, domainNamespace,
        clusterName, DOMAIN_STATUS_CONDITION_COMPLETED_TYPE, "False", DOMAIN_VERSION);
    checkDomainStatusConditionTypeHasExpectedStatus(domainUid, domainNamespace,
        DOMAIN_STATUS_CONDITION_COMPLETED_TYPE, "False");
    checkDomainStatusConditionTypeHasExpectedStatus(domainUid, domainNamespace,
        DOMAIN_STATUS_CONDITION_AVAILABLE_TYPE, "False");

    // wait the pod is restarted and back to ready
    checkPodRestarted(domainUid, domainNamespace, podName, ms1PodCreationTime);
    checkPodReady(podName, domainUid, domainNamespace);

    // verify domain and cluster status conditions Available and Complete become true
    checkDomainStatusClusterConditionTypeHasExpectedStatus(withQuickRetryPolicy, domainUid, domainNamespace,
        clusterName, DOMAIN_STATUS_CONDITION_AVAILABLE_TYPE, "True", DOMAIN_VERSION);
    checkDomainStatusClusterConditionTypeHasExpectedStatus(withQuickRetryPolicy, domainUid, domainNamespace,
        clusterName, DOMAIN_STATUS_CONDITION_COMPLETED_TYPE, "True", DOMAIN_VERSION);
    checkDomainStatusConditionTypeHasExpectedStatus(domainUid, domainNamespace,
        DOMAIN_STATUS_CONDITION_COMPLETED_TYPE, "True");
    checkDomainStatusConditionTypeHasExpectedStatus(domainUid, domainNamespace,
        DOMAIN_STATUS_CONDITION_AVAILABLE_TYPE, "True");
  }

  /**
   * Test domain and cluster status conditions when set cluster replicas to zero.
   * Set cluster replicas to 0.
   * Verify Cluster status condition Available becomes false and stay false.
   * Verify Domain status condition Available should stay true.
   * Verify Domain and Cluster status condition Complete becomes false while the cluster is shutting down and become
   * True once the shutdown completes.
   */
  @Order(5)
  @Test
  @DisplayName("Test domain and cluster status conditions when set cluster replicas to zero.")
  void testClusterReplicasToZero() {
    logger.info("patch the cluster resource with replicas to 0");
    String patchStr = "[{\"op\": \"replace\", \"path\": \"/spec/replicas\", \"value\": 0}]";

    logger.info("Updating cluster configuration using patch string: {0}", patchStr);
    V1Patch patch = new V1Patch(patchStr);
    assertTrue(patchClusterCustomResource(clusterName, domainNamespace,
        patch, V1Patch.PATCH_FORMAT_JSON_PATCH), "Failed to patch cluster");

    // check the domain status condition Complete becomes false
    checkDomainStatusConditionTypeHasExpectedStatus(shortRetryPolicy, domainUid, domainNamespace,
        DOMAIN_STATUS_CONDITION_COMPLETED_TYPE, "False", DOMAIN_VERSION);

    // check the cluster status condition Complete becomes false
    checkDomainStatusClusterConditionTypeHasExpectedStatus(shortRetryPolicy, domainUid, domainNamespace,
        clusterName, DOMAIN_STATUS_CONDITION_COMPLETED_TYPE, "False", DOMAIN_VERSION);

    // verify Cluster status condition Available becomes false and stay false when the server pod is deleting
    checkDomainStatusClusterConditionTypeHasExpectedStatus(withQuickRetryPolicy, domainUid, domainNamespace,
        clusterName, DOMAIN_STATUS_CONDITION_AVAILABLE_TYPE, "False", DOMAIN_VERSION);

    // verify Domain status condition Available stays true when the server pod is deleting
    checkDomainStatusConditionTypeHasExpectedStatus(withQuickRetryPolicy, domainUid, domainNamespace,
        DOMAIN_STATUS_CONDITION_AVAILABLE_TYPE, "True", DOMAIN_VERSION);

    // check the cluster server pod is deleted
    checkPodDoesNotExist(managedServerPrefix + 1, domainUid, domainNamespace);

    // check the cluster status condition Complete becomes true
    checkDomainStatusClusterConditionTypeHasExpectedStatus(withQuickRetryPolicy, domainUid, domainNamespace,
        clusterName, DOMAIN_STATUS_CONDITION_COMPLETED_TYPE, "True", DOMAIN_VERSION);
    // verify Cluster status condition Available becomes false
    checkDomainStatusClusterConditionTypeHasExpectedStatus(withQuickRetryPolicy, domainUid, domainNamespace,
        clusterName, DOMAIN_STATUS_CONDITION_AVAILABLE_TYPE, "False", DOMAIN_VERSION);

    // verify Domain status condition Available stays true while the cluster server pods are deleted
    checkDomainStatusConditionTypeHasExpectedStatus(withQuickRetryPolicy, domainUid, domainNamespace,
        DOMAIN_STATUS_CONDITION_AVAILABLE_TYPE, "True", DOMAIN_VERSION);
    // check domain status condition Complete becomes True when the cluster shutdown Completes
    checkDomainStatusConditionTypeHasExpectedStatus(withQuickRetryPolicy, domainUid, domainNamespace,
        DOMAIN_STATUS_CONDITION_COMPLETED_TYPE, "True", DOMAIN_VERSION);
  }

  /**
   * Test domain and cluster status condition when set cluster replicas to 2.
   * Set cluster replicas to 2.
   * Verify the cluster status condition Complete and Available become false.
   * Verify the domain status condition Complete and Available become false.
   * Verify the cluster status condition Available becomes true when one of the cluster's members becomes ready.
   * Verify Domain status condition Available become true when cluster status condition Available becomes true.
   * Verify the cluster status condition Complete becomes true once both servers are ready.
   * Verify domain status condition Complete becomes true when the cluster is fully up and ready.
   */
  @Order(6)
  @Test
  @DisplayName("Test domain and cluster status condition when set cluster replicas to 2.")
  void testClusterReplicasTo2() {
    logger.info("patch the cluster resource with replicas to 2");
    String patchStr = "[{\"op\": \"replace\", \"path\": \"/spec/replicas\", \"value\": 2}]";

    logger.info("Updating cluster configuration using patch string: {0}", patchStr);
    V1Patch patch = new V1Patch(patchStr);
    assertTrue(patchClusterCustomResource(clusterName, domainNamespace,
        patch, V1Patch.PATCH_FORMAT_JSON_PATCH), "Failed to patch cluster");

    // check the Cluster status condition Available and Complete become false
    checkDomainStatusClusterConditionTypeHasExpectedStatus(shortRetryPolicy, domainUid, domainNamespace,
        clusterName, DOMAIN_STATUS_CONDITION_COMPLETED_TYPE, "False", DOMAIN_VERSION);
    checkDomainStatusClusterConditionTypeHasExpectedStatus(shortRetryPolicy, domainUid, domainNamespace,
        clusterName, DOMAIN_STATUS_CONDITION_AVAILABLE_TYPE, "False", DOMAIN_VERSION);
    // check the Domain status condition Available and Complete become false
    checkDomainStatusConditionTypeHasExpectedStatus(shortRetryPolicy, domainUid, domainNamespace,
        DOMAIN_STATUS_CONDITION_AVAILABLE_TYPE, "False", DOMAIN_VERSION);
    checkDomainStatusConditionTypeHasExpectedStatus(shortRetryPolicy, domainUid, domainNamespace,
        DOMAIN_STATUS_CONDITION_COMPLETED_TYPE, "False", DOMAIN_VERSION);

    // wait for the first managed server pod is ready
    checkPodReady(managedServerPrefix + 1, domainUid, domainNamespace);

    // check the cluster status condition Available becomes true after the first cluster member becomes ready
    checkDomainStatusClusterConditionTypeHasExpectedStatus(shortRetryPolicy, domainUid, domainNamespace,
        clusterName, DOMAIN_STATUS_CONDITION_AVAILABLE_TYPE, "True", DOMAIN_VERSION);
    // check the Domain status condition Available become true when cluster status condition Available becomes true
    checkDomainStatusConditionTypeHasExpectedStatus(shortRetryPolicy, domainUid, domainNamespace,
        DOMAIN_STATUS_CONDITION_AVAILABLE_TYPE, "True", DOMAIN_VERSION);

    // wait for the second managed server is ready
    checkPodReady(managedServerPrefix + 2, domainUid, domainNamespace);

    // check cluster status condition Complete becomes true once both servers are ready
    checkDomainStatusClusterConditionTypeHasExpectedStatus(shortRetryPolicy, domainUid, domainNamespace,
        clusterName, DOMAIN_STATUS_CONDITION_COMPLETED_TYPE, "True", DOMAIN_VERSION);
    // check domain status condition Complete becomes true when the cluster is fully up
    checkDomainStatusConditionTypeHasExpectedStatus(shortRetryPolicy, domainUid, domainNamespace,
        DOMAIN_STATUS_CONDITION_COMPLETED_TYPE, "True", DOMAIN_VERSION);
  }

  /**
   * Test domain and cluster status conditions when deleting one of the cluster server pods.
   * Delete one of the cluster server pods.
   * Verify Domain and cluster status condition Complete become false.
   * Verify Domain and Cluster status condition Available stay true.
   * Verify Domain and cluster status condition Complete becomes true when the pod auto-restarts and becomes ready.
   * This test is different than Test 4 since the cluster replicas is 2 and when one of the cluster server pods
   * is deleted, another server pod is still running. The cluster status condition Available should be True even
   * one of the pods get deleted.
   */
  @Order(7)
  @Test
  @DisplayName("Test domain and cluster status conditions when deleting one of the cluster server pods with cluster "
      + "replicas set to 2.")
  void testDeleteOneClusterPodWithReplicasSetTo2() {
    String podName = managedServerPrefix + 1;

    OffsetDateTime ms1PodCreationTime =
        assertDoesNotThrow(() -> getPodCreationTimestamp(domainNamespace, "", podName),
            String.format("Failed to get creationTimestamp for pod %s", podName));

    // delete one server pod
    assertDoesNotThrow(() -> deletePod(podName, domainNamespace),
        String.format("delete pod %s in namespace %s failed", podName, domainNamespace));

    // check the cluster and domain Complete become false while the pod is deleted
    checkDomainStatusClusterConditionTypeHasExpectedStatus(shortRetryPolicy, domainUid, domainNamespace,
        clusterName, DOMAIN_STATUS_CONDITION_COMPLETED_TYPE, "False", DOMAIN_VERSION);
    checkDomainStatusConditionTypeHasExpectedStatus(shortRetryPolicy, domainUid, domainNamespace,
        DOMAIN_STATUS_CONDITION_COMPLETED_TYPE, "False", DOMAIN_VERSION);

    // check the domain and cluster Available stay true while the pod is deleted
    checkDomainStatusClusterConditionTypeHasExpectedStatus(shortRetryPolicy, domainUid, domainNamespace,
        clusterName, DOMAIN_STATUS_CONDITION_AVAILABLE_TYPE, "True", DOMAIN_VERSION);
    checkDomainStatusConditionTypeHasExpectedStatus(shortRetryPolicy, domainUid, domainNamespace,
        DOMAIN_STATUS_CONDITION_AVAILABLE_TYPE, "TRUE", DOMAIN_VERSION);

    // Wait for the pod restarted
    checkPodRestarted(domainUid, domainNamespace, podName, ms1PodCreationTime);
    checkPodReady(podName, domainUid, domainNamespace);

    // check the cluster and domain Complete should become true
    checkDomainStatusClusterConditionTypeHasExpectedStatus(withQuickRetryPolicy, domainUid, domainNamespace,
        clusterName, DOMAIN_STATUS_CONDITION_COMPLETED_TYPE, "True", DOMAIN_VERSION);
    checkDomainStatusConditionTypeHasExpectedStatus(withQuickRetryPolicy, domainUid, domainNamespace,
        DOMAIN_STATUS_CONDITION_COMPLETED_TYPE, "True", DOMAIN_VERSION);
  }

  /**
   * Test domain and cluster status condition when deleting the admin server pod.
   * Delete the Admin server pod.
   * Verify domain status condition Complete and Available become false.
   * Verify Cluster status condition Complete and Available stay true.
   * Verify Domain status condition Complete and Available become true after Admin server pod restarts.
   */
  @Order(8)
  @Test
  @DisplayName("Test domain and cluster status condition when deleting the admin server pod.")
  void testDeleteAdminServer() {
    OffsetDateTime adminPodCreationTime =
        assertDoesNotThrow(() -> getPodCreationTimestamp(domainNamespace, "", adminServerPodName),
            String.format("Failed to get creationTimestamp for pod %s", adminServerPodName));

    // delete admin server pod
    assertDoesNotThrow(() -> deletePod(adminServerPodName, domainNamespace),
        String.format("delete pod %s in namespace %s failed", adminServerPodName, domainNamespace));

    // check the domain status condition Complete and Available become false
    checkDomainStatusConditionTypeHasExpectedStatus(shortRetryPolicy, domainUid, domainNamespace,
        DOMAIN_STATUS_CONDITION_COMPLETED_TYPE, "False", DOMAIN_VERSION);
    checkDomainStatusConditionTypeHasExpectedStatus(shortRetryPolicy, domainUid, domainNamespace,
        DOMAIN_STATUS_CONDITION_AVAILABLE_TYPE, "False", DOMAIN_VERSION);

    // check the cluster status Complete and Available stay at True
    checkDomainStatusClusterConditionTypeHasExpectedStatus(withQuickRetryPolicy, domainUid, domainNamespace,
        clusterName, DOMAIN_STATUS_CONDITION_COMPLETED_TYPE, "True", DOMAIN_VERSION);
    checkDomainStatusClusterConditionTypeHasExpectedStatus(withQuickRetryPolicy, domainUid, domainNamespace,
        clusterName, DOMAIN_STATUS_CONDITION_AVAILABLE_TYPE, "True", DOMAIN_VERSION);

    // wait for admin server restart
    checkPodRestarted(domainUid, domainNamespace, adminServerPodName, adminPodCreationTime);
    checkPodReady(adminServerPodName, domainUid, domainNamespace);

    // check the domain status condition Available and Complete become true
    checkDomainStatusConditionTypeHasExpectedStatus(withQuickRetryPolicy, domainUid, domainNamespace,
        DOMAIN_STATUS_CONDITION_COMPLETED_TYPE, "True", DOMAIN_VERSION);
    checkDomainStatusConditionTypeHasExpectedStatus(withQuickRetryPolicy, domainUid, domainNamespace,
        DOMAIN_STATUS_CONDITION_AVAILABLE_TYPE, "True", DOMAIN_VERSION);
  }
}
