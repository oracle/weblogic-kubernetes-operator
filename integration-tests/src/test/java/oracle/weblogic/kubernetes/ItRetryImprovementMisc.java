// Copyright (c) 2022, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.weblogic.kubernetes;

import java.util.Arrays;
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

import static oracle.weblogic.kubernetes.TestConstants.ADMIN_SERVER_NAME_BASE;
import static oracle.weblogic.kubernetes.TestConstants.DOMAIN_STATUS_CONDITION_AVAILABLE_TYPE;
import static oracle.weblogic.kubernetes.TestConstants.DOMAIN_STATUS_CONDITION_COMPLETED_TYPE;
import static oracle.weblogic.kubernetes.TestConstants.DOMAIN_VERSION;
import static oracle.weblogic.kubernetes.TestConstants.MANAGED_SERVER_NAME_BASE;
import static oracle.weblogic.kubernetes.TestConstants.MII_BASIC_IMAGE_NAME;
import static oracle.weblogic.kubernetes.TestConstants.MII_BASIC_IMAGE_TAG;
import static oracle.weblogic.kubernetes.actions.TestActions.deletePod;
import static oracle.weblogic.kubernetes.actions.TestActions.patchClusterCustomResource;
import static oracle.weblogic.kubernetes.assertions.TestAssertions.domainStatusClustersConditionTypeHasExpectedStatus;
import static oracle.weblogic.kubernetes.assertions.TestAssertions.domainStatusConditionTypeHasExpectedStatus;
import static oracle.weblogic.kubernetes.utils.ClusterUtils.checkDomainStatusClusterConditionTypeHasExpectedStatus;
import static oracle.weblogic.kubernetes.utils.CommonMiiTestUtils.createMiiDomain;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.checkPodReadyAndServiceExists;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.createRetryPolicy;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.withQuickRetryPolicy;
import static oracle.weblogic.kubernetes.utils.DomainUtils.checkDomainStatusConditionTypeHasExpectedStatus;
import static oracle.weblogic.kubernetes.utils.OperatorUtils.installAndVerifyOperator;
import static oracle.weblogic.kubernetes.utils.PodUtils.checkPodDeleted;
import static oracle.weblogic.kubernetes.utils.PodUtils.checkPodDoesNotExist;
import static oracle.weblogic.kubernetes.utils.PodUtils.checkPodReady;
import static oracle.weblogic.kubernetes.utils.ThreadSafeLogger.getLogger;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

// Miscellaneous use cases for WKO retry improvements
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DisplayName("Miscellaneous use cases for WKO retry improvements")
@IntegrationTest
@Tag("olcne")
@Tag("oke-parallel")
@Tag("kind-parallel")
@Tag("okd-wls-mrg")
class ItRetryImprovementMisc {
  private static String opNamespace = null;
  private static String domainNamespace = null;
  private static String domainUid = "domain1";

  private static String adminServerPodName = String.format("%s-%s", domainUid, ADMIN_SERVER_NAME_BASE);
  private static String managedServerPrefix = String.format("%s-%s", domainUid, MANAGED_SERVER_NAME_BASE);
  private static int replicaCount = 1;
  private static LoggingFacade logger = null;
  private static String opServiceAccount = null;
  private static String clusterName = "cluster-1";

  private ConditionFactory shortRetryPolicy = createRetryPolicy(0, 1, 30);

  /**
   * Perform initialization for all the tests in this class.
   * Set up the necessary namespaces, install the operator in the first namespace, and
   * create a domain in the second namespace using the pre-created basic MII image,
   * Max cluster size set in the model file is 5.
   *
   * @param namespaces list of namespaces created by the IntegrationTestWatcher by the
   *           JUnit engine parameter resolution mechanism
   */
  @BeforeAll
  public static void initAll(@Namespaces(2) List<String> namespaces) {
    logger = getLogger();

    // get a namespace for operator
    assertNotNull(namespaces.get(0), "Namespace namespaces.get(0) is null");
    opNamespace = namespaces.get(0);

    // get a namespace for domain1
    assertNotNull(namespaces.get(1), "Namespace namespaces.get(1) is null");
    domainNamespace = namespaces.get(1);

    opServiceAccount = opNamespace + "-sa";
    // install and verify operator with REST API
    logger.info("Install an operator in namespace {0}, managing namespace {1}",
        opNamespace, domainNamespace);
    installAndVerifyOperator(opNamespace, opServiceAccount, true, 0, domainNamespace);
  }

  /**
   * Start a domain with a single cluster, where cluster has 1 server (replicas = 1).
   * Expect Cluster and Domain status to report Unavailable and Incomplete almost immediately.
   * Wait for Cluster and Domain status to both report Available=True and Complete=True,
   * and expect this to happen at the same time all pods reports ready (not sooner).
   */
  @Order(1)
  @Test
  @DisplayName("Start a domain with a single cluster which has 1 server")
  void testStartSingleClusterDomainWithOneServer() {
    // create a mii domain resource with one cluster
    logger.info("Create model-in-image domain {0} in namespace {1}, and wait until it comes up",
        domainUid, domainNamespace);
    createMiiDomain(
        domainNamespace,
        domainUid,
        MII_BASIC_IMAGE_NAME + ":" + MII_BASIC_IMAGE_TAG,
        replicaCount,
        Arrays.asList(clusterName),
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

    // wait for all the pods are up
    // check admin server pod is ready
    logger.info("Wait for admin server pod {0} to be ready in namespace {1}",
        adminServerPodName, domainNamespace);
    checkPodReadyAndServiceExists(adminServerPodName, domainUid, domainNamespace);

    // check the domain status is Unavailable and Incomplete
    assertTrue(assertDoesNotThrow(
        () -> domainStatusConditionTypeHasExpectedStatus(domainUid, domainNamespace,
            DOMAIN_STATUS_CONDITION_COMPLETED_TYPE, "False").call().booleanValue()),
        "domain status condition Completed type does not have expected False value");
    assertTrue(assertDoesNotThrow(
        () -> domainStatusConditionTypeHasExpectedStatus(domainUid, domainNamespace,
            DOMAIN_STATUS_CONDITION_AVAILABLE_TYPE, "False").call().booleanValue()),
        "domain status condition Available type does not have expected False value");

    // check the cluster condition is Unavailable and Incomplete
    assertTrue(assertDoesNotThrow(
        () -> domainStatusClustersConditionTypeHasExpectedStatus(domainUid, domainNamespace, clusterName,
            DOMAIN_STATUS_CONDITION_COMPLETED_TYPE, "False", DOMAIN_VERSION).call().booleanValue()),
        "domain status cluster condition Completed type does not have expected False value");
    assertTrue(assertDoesNotThrow(
        () -> domainStatusClustersConditionTypeHasExpectedStatus(domainUid, domainNamespace, clusterName,
            DOMAIN_STATUS_CONDITION_AVAILABLE_TYPE, "False", DOMAIN_VERSION).call().booleanValue()),
        "domain status cluster condition Available type does not have expected False value");

    // check managed server pods are ready
    for (int i = 1; i <= replicaCount; i++) {
      logger.info("Wait for managed server pod {0} to be ready in namespace {1}",
          managedServerPrefix + i, domainNamespace);
      checkPodReadyAndServiceExists(managedServerPrefix + i, domainUid, domainNamespace);
    }

    // check the domain status is Available and Complete
    checkDomainStatusConditionTypeHasExpectedStatus(shortRetryPolicy, domainUid, domainNamespace,
        DOMAIN_STATUS_CONDITION_COMPLETED_TYPE, "True", DOMAIN_VERSION);
    checkDomainStatusConditionTypeHasExpectedStatus(shortRetryPolicy, domainUid, domainNamespace,
        DOMAIN_STATUS_CONDITION_AVAILABLE_TYPE, "True", DOMAIN_VERSION);

    // check the cluster condition is Available and Complete
    checkDomainStatusClusterConditionTypeHasExpectedStatus(shortRetryPolicy, domainUid, domainNamespace,
        clusterName, DOMAIN_STATUS_CONDITION_COMPLETED_TYPE, "True", DOMAIN_VERSION);
    checkDomainStatusClusterConditionTypeHasExpectedStatus(shortRetryPolicy, domainUid, domainNamespace,
        clusterName, DOMAIN_STATUS_CONDITION_AVAILABLE_TYPE, "True", DOMAIN_VERSION);
  }

  /**
   * Set cluster to NEVER. Expect domain status to stay available and complete.
   * And expect cluster status to go to unavailable, and expect cluster complete to stay complete.
   */
  @Order(2)
  @Test
  @DisplayName("Set cluster to Never, expect  domain status to stay available and complete. "
      + "And expect cluster status to go to unavailable, and expect cluster complete to stay complete.")
  void testSetClusterToNeverAndVerifyStatus() {
    logger.info("patch the cluster resource with serverStartPolicy to Never");
    String patchStr = "[{\"op\": \"replace\", \"path\": \"/spec/serverStartPolicy\", \"value\": \"Never\"}]";

    logger.info("Updating cluster configuration using patch string: {0}", patchStr);
    V1Patch patch = new V1Patch(patchStr);
    assertTrue(patchClusterCustomResource(clusterName, domainNamespace,
        patch, V1Patch.PATCH_FORMAT_JSON_PATCH), "Failed to patch cluster");

    // check domain status to stay available and complete
    assertTrue(assertDoesNotThrow(
        () -> domainStatusConditionTypeHasExpectedStatus(domainUid, domainNamespace,
            DOMAIN_STATUS_CONDITION_COMPLETED_TYPE, "True").call().booleanValue()),
        "domain status condition Completed type does not have expected True value");
    assertTrue(assertDoesNotThrow(
        () -> domainStatusConditionTypeHasExpectedStatus(domainUid, domainNamespace,
            DOMAIN_STATUS_CONDITION_AVAILABLE_TYPE, "True").call().booleanValue()),
        "domain status condition Available type does not have expected True value");

    // wait for the cluster status to go to unavailable
    checkDomainStatusClusterConditionTypeHasExpectedStatus(domainUid, domainNamespace, clusterName,
        DOMAIN_STATUS_CONDITION_AVAILABLE_TYPE, "False");

    // check the cluster status is complete
    checkDomainStatusClusterConditionTypeHasExpectedStatus(domainUid, domainNamespace, clusterName,
        DOMAIN_STATUS_CONDITION_COMPLETED_TYPE, "True");

    // check the cluster pod is deleted
    for (int i = 1; i <= replicaCount; i++) {
      logger.info("Wait for managed server pod {0} to be deleted in namespace {1}",
          managedServerPrefix + i, domainNamespace);
      checkPodDeleted(managedServerPrefix + i, domainUid, domainNamespace);
    }
  }

  /**
   * Set cluster back to IF_NEEDED, expect recovery, where cluster becomes Available once its one pod is ready
   * and then, perhaps only a very small time later, Complete too.
   * Domain status should flip to incomplete and unavailable during this period,
   * and come back to complete/available at the same time Cluster recovers.
   */
  @Order(3)
  @Test
  @DisplayName("Set cluster back to IF_NEEDED, expect recovery")
  void testSetClusterToIfNeeded() {
    logger.info("patch the cluster resource with serverStartPolicy to IfNeeded");
    String patchStr = "[{\"op\": \"replace\", \"path\": \"/spec/serverStartPolicy\", \"value\": \"IfNeeded\"}]";
    
    logger.info("Updating cluster configuration using patch string: {0}", patchStr);
    V1Patch patch = new V1Patch(patchStr);
    assertTrue(patchClusterCustomResource(clusterName, domainNamespace,
        patch, V1Patch.PATCH_FORMAT_JSON_PATCH), "Failed to patch cluster");

    // check domain status to become unavailable and incomplete
    checkDomainStatusConditionTypeHasExpectedStatus(withQuickRetryPolicy, domainUid, domainNamespace,
        DOMAIN_STATUS_CONDITION_COMPLETED_TYPE, "False", DOMAIN_VERSION);
    checkDomainStatusConditionTypeHasExpectedStatus(withQuickRetryPolicy, domainUid, domainNamespace,
        DOMAIN_STATUS_CONDITION_AVAILABLE_TYPE, "False", DOMAIN_VERSION);

    // check managed server pods are ready
    for (int i = 1; i <= replicaCount; i++) {
      logger.info("Wait for managed server pod {0} to be ready in namespace {1}",
          managedServerPrefix + i, domainNamespace);
      checkPodReadyAndServiceExists(managedServerPrefix + i, domainUid, domainNamespace);
    }

    // check the cluster status is Available and Complete
    checkDomainStatusClusterConditionTypeHasExpectedStatus(shortRetryPolicy, domainUid, domainNamespace,
        clusterName, DOMAIN_STATUS_CONDITION_COMPLETED_TYPE, "True", DOMAIN_VERSION);
    checkDomainStatusClusterConditionTypeHasExpectedStatus(shortRetryPolicy, domainUid, domainNamespace,
        clusterName, DOMAIN_STATUS_CONDITION_AVAILABLE_TYPE, "True", DOMAIN_VERSION);

    // wait for the domain status to go to available and complete
    checkDomainStatusConditionTypeHasExpectedStatus(domainUid, domainNamespace,
        DOMAIN_STATUS_CONDITION_COMPLETED_TYPE, "True");
    checkDomainStatusConditionTypeHasExpectedStatus(domainUid, domainNamespace,
        DOMAIN_STATUS_CONDITION_AVAILABLE_TYPE, "True");
  }

  /**
   * Delete a server pod in the cluster. For both domain & cluster: Available should go false, Complete should also go
   * false, and both should eventually return to true when the pod auto-restarts and declares itself ready.
   */
  @Order(4)
  @Test
  @DisplayName("Delete a server pod, verify the domain and cluster status")
  void testDeleteServerPodAndVerifyDomainClusterStatus() {
    // delete a cluster server pod
    String podName = managedServerPrefix + 1;
    assertDoesNotThrow(() -> deletePod(podName, domainNamespace),
        String.format("delete pod %s in namespace %s failed", podName, domainNamespace));

    // verify both domain and cluster: Available should go false, Complete should also go false
    checkDomainStatusClusterConditionTypeHasExpectedStatus(withQuickRetryPolicy, domainUid, domainNamespace,
        clusterName, DOMAIN_STATUS_CONDITION_AVAILABLE_TYPE, "False", DOMAIN_VERSION);
    checkDomainStatusClusterConditionTypeHasExpectedStatus(withQuickRetryPolicy, domainUid, domainNamespace,
        clusterName, DOMAIN_STATUS_CONDITION_COMPLETED_TYPE, "False", DOMAIN_VERSION);
    checkDomainStatusConditionTypeHasExpectedStatus(domainUid, domainNamespace,
        DOMAIN_STATUS_CONDITION_COMPLETED_TYPE, "False");
    checkDomainStatusConditionTypeHasExpectedStatus(domainUid, domainNamespace,
        DOMAIN_STATUS_CONDITION_AVAILABLE_TYPE, "False");

    // wait the pod is restarted and back to ready
    checkPodReady(podName, domainUid, domainNamespace);

    // verify both domain and cluster: Available should go true, Complete should also go true
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
   * Set cluster replicas=0. Cluster Available should go to false and stay false. Domain available should stay true
   * throughout. Domain and Cluster Complete should go false while the cluster is shutting down,
   * and go True once the shutdown completes
   */
  @Order(5)
  @Test
  @DisplayName("Set cluster replicas to 0 and verify the cluster and domain status")
  void testClusterReplicasToZero() {
    logger.info("patch the cluster resource with replicas to 0");
    String patchStr = "[{\"op\": \"replace\", \"path\": \"/spec/replicas\", \"value\": 0}]";

    logger.info("Updating cluster configuration using patch string: {0}", patchStr);
    V1Patch patch = new V1Patch(patchStr);
    assertTrue(patchClusterCustomResource(clusterName, domainNamespace,
        patch, V1Patch.PATCH_FORMAT_JSON_PATCH), "Failed to patch cluster");

    // check the domain Complete goes to false
    checkDomainStatusConditionTypeHasExpectedStatus(shortRetryPolicy, domainUid, domainNamespace,
        DOMAIN_STATUS_CONDITION_COMPLETED_TYPE, "False", DOMAIN_VERSION);

    // Enable this check when OWLS-104622 is fixed
    // check the cluster Complete should go false
    //checkDomainStatusClusterConditionTypeHasExpectedStatus(shortRetryPolicy, domainUid, domainNamespace,
    //    clusterName, DOMAIN_STATUS_CONDITION_COMPLETED_TYPE, "False", DOMAIN_VERSION);

    // verify Cluster Available should go to false and stay false
    checkDomainStatusClusterConditionTypeHasExpectedStatus(withQuickRetryPolicy, domainUid, domainNamespace,
        clusterName, DOMAIN_STATUS_CONDITION_AVAILABLE_TYPE, "False", DOMAIN_VERSION);

    // verify Domain available should stay true throughout
    checkDomainStatusConditionTypeHasExpectedStatus(withQuickRetryPolicy, domainUid, domainNamespace,
        DOMAIN_STATUS_CONDITION_AVAILABLE_TYPE, "True", DOMAIN_VERSION);

    // check the cluster server pod is deleted
    checkPodDoesNotExist(managedServerPrefix + 1, domainUid, domainNamespace);

    // check the cluster Complete should go true
    checkDomainStatusClusterConditionTypeHasExpectedStatus(withQuickRetryPolicy, domainUid, domainNamespace,
        clusterName, DOMAIN_STATUS_CONDITION_COMPLETED_TYPE, "True", DOMAIN_VERSION);
    // verify Cluster Available should stay false
    checkDomainStatusClusterConditionTypeHasExpectedStatus(withQuickRetryPolicy, domainUid, domainNamespace,
        clusterName, DOMAIN_STATUS_CONDITION_AVAILABLE_TYPE, "False", DOMAIN_VERSION);

    // verify Domain available should stay true throughout
    checkDomainStatusConditionTypeHasExpectedStatus(withQuickRetryPolicy, domainUid, domainNamespace,
        DOMAIN_STATUS_CONDITION_AVAILABLE_TYPE, "True", DOMAIN_VERSION);
    // check the domain Complete back to True when the cluster shutdown completes
    checkDomainStatusConditionTypeHasExpectedStatus(withQuickRetryPolicy, domainUid, domainNamespace,
        DOMAIN_STATUS_CONDITION_COMPLETED_TYPE, "True", DOMAIN_VERSION);
  }

  /**
   * Try set cluster replicas=2. The cluster Complete should immediately go false, and cluster Available should go true
   * the moment one of the cluster's members becomes ready, and cluster Complete should become true once both servers
   * are ready. Domain available and complete should both flip to false, domain available should become true when
   * cluster available becomes true, and domain complete should only come back to true when the cluster is fully up
   * and ready.
   */
  @Order(6)
  @Test
  @DisplayName("Set cluster replicas to 2, verify the domain and cluster status Complete and Available condition")
  void testClusterReplicasTo2() {
    logger.info("patch the cluster resource with replicas to 2");
    String patchStr = "[{\"op\": \"replace\", \"path\": \"/spec/replicas\", \"value\": 2}]";

    logger.info("Updating cluster configuration using patch string: {0}", patchStr);
    V1Patch patch = new V1Patch(patchStr);
    assertTrue(patchClusterCustomResource(clusterName, domainNamespace,
        patch, V1Patch.PATCH_FORMAT_JSON_PATCH), "Failed to patch cluster");

    // check the Cluster Available and Complete go to false
    checkDomainStatusClusterConditionTypeHasExpectedStatus(shortRetryPolicy, domainUid, domainNamespace,
        clusterName, DOMAIN_STATUS_CONDITION_COMPLETED_TYPE, "False", DOMAIN_VERSION);
    checkDomainStatusClusterConditionTypeHasExpectedStatus(shortRetryPolicy, domainUid, domainNamespace,
        clusterName, DOMAIN_STATUS_CONDITION_AVAILABLE_TYPE, "False", DOMAIN_VERSION);
    // check the Domain Available and Complete flip to false
    checkDomainStatusConditionTypeHasExpectedStatus(shortRetryPolicy, domainUid, domainNamespace,
        DOMAIN_STATUS_CONDITION_AVAILABLE_TYPE, "False", DOMAIN_VERSION);
    checkDomainStatusConditionTypeHasExpectedStatus(shortRetryPolicy, domainUid, domainNamespace,
        DOMAIN_STATUS_CONDITION_COMPLETED_TYPE, "False", DOMAIN_VERSION);

    checkPodReady(managedServerPrefix + 1, domainUid, domainNamespace);
    // check the cluster Available should go true after the first cluster member becomes ready
    checkDomainStatusClusterConditionTypeHasExpectedStatus(shortRetryPolicy, domainUid, domainNamespace,
        clusterName, DOMAIN_STATUS_CONDITION_AVAILABLE_TYPE, "True", DOMAIN_VERSION);
    // check the Domain Available should become true when cluster available becomes true
    checkDomainStatusConditionTypeHasExpectedStatus(shortRetryPolicy, domainUid, domainNamespace,
        DOMAIN_STATUS_CONDITION_AVAILABLE_TYPE, "True", DOMAIN_VERSION);

    checkPodReady(managedServerPrefix + 2, domainUid, domainNamespace);
    // check cluster Complete becomes true once both servers are ready
    checkDomainStatusClusterConditionTypeHasExpectedStatus(shortRetryPolicy, domainUid, domainNamespace,
        clusterName, DOMAIN_STATUS_CONDITION_COMPLETED_TYPE, "True", DOMAIN_VERSION);
    // check domain complete should only come back to true when the cluster is fully up
    checkDomainStatusConditionTypeHasExpectedStatus(shortRetryPolicy, domainUid, domainNamespace,
        DOMAIN_STATUS_CONDITION_COMPLETED_TYPE, "True", DOMAIN_VERSION);
  }

  /**
   * Delete one of the cluster's pods. Cluster Available should stay true throughout, because the cluster remains
   * 'ready enough', but cluster Complete should go false.  Then, once pod auto-restarts and becomes ready,
   * cluster Complete should become true.  Domain Available and Complete should act the same as the Cluster conditions
   */
  @Order(7)
  @Test
  @DisplayName("delete one cluster pod and verify the domain and cluster condition")
  void testDeleteOneClusterPodWithReplicasTo2() {
    // delete one server pod
    String podName = managedServerPrefix + 1;
    assertDoesNotThrow(() -> deletePod(podName, domainNamespace),
        String.format("delete pod %s in namespace %s failed", podName, domainNamespace));

    // check the cluster and domain Complete should go false
    checkDomainStatusClusterConditionTypeHasExpectedStatus(shortRetryPolicy, domainUid, domainNamespace,
        clusterName, DOMAIN_STATUS_CONDITION_COMPLETED_TYPE, "False", DOMAIN_VERSION);
    checkDomainStatusConditionTypeHasExpectedStatus(shortRetryPolicy, domainUid, domainNamespace,
        DOMAIN_STATUS_CONDITION_COMPLETED_TYPE, "False", DOMAIN_VERSION);

    // check the domain and cluster Available should stay true
    checkDomainStatusClusterConditionTypeHasExpectedStatus(shortRetryPolicy, domainUid, domainNamespace,
        clusterName, DOMAIN_STATUS_CONDITION_AVAILABLE_TYPE, "True", DOMAIN_VERSION);
    checkDomainStatusConditionTypeHasExpectedStatus(shortRetryPolicy, domainUid, domainNamespace,
        DOMAIN_STATUS_CONDITION_AVAILABLE_TYPE, "TRUE", DOMAIN_VERSION);

    // Wait for the pod restarted
    checkPodReady(podName, domainUid, domainNamespace);

    // check the cluster and domain complete should become true
    checkDomainStatusClusterConditionTypeHasExpectedStatus(withQuickRetryPolicy, domainUid, domainNamespace,
        clusterName, DOMAIN_STATUS_CONDITION_COMPLETED_TYPE, "True", DOMAIN_VERSION);
    checkDomainStatusConditionTypeHasExpectedStatus(withQuickRetryPolicy, domainUid, domainNamespace,
        DOMAIN_STATUS_CONDITION_COMPLETED_TYPE, "True", DOMAIN_VERSION);
  }

  /**
   * Delete the Admin server pod. Expect domain status to flip to Complete false and Available false,
   * but Cluster status to remain unchanged throughout, staying at Complete and Available.
   * Once Admin server pod restarts, the Domain status should become true.
   */
  @Order(8)
  @Test
  @DisplayName("Delete the admin server pod. Verify the domain and cluster status")
  void testDeleteAdminServer() {
    // delete admin server pod
    assertDoesNotThrow(() -> deletePod(adminServerPodName, domainNamespace),
        String.format("delete pod %s in namespace %s failed", adminServerPodName, domainNamespace));

    // check the domain Complete and Available should go false
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
    checkPodReady(adminServerPodName, domainUid, domainNamespace);

    // check the domain status becomes true
    checkDomainStatusConditionTypeHasExpectedStatus(withQuickRetryPolicy, domainUid, domainNamespace,
        DOMAIN_STATUS_CONDITION_COMPLETED_TYPE, "True", DOMAIN_VERSION);
    checkDomainStatusConditionTypeHasExpectedStatus(withQuickRetryPolicy, domainUid, domainNamespace,
        DOMAIN_STATUS_CONDITION_AVAILABLE_TYPE, "True", DOMAIN_VERSION);
  }
}
