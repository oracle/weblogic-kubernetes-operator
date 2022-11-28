// Copyright (c) 2022, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.weblogic.kubernetes;

import java.util.Arrays;
import java.util.List;

import io.kubernetes.client.custom.V1Patch;
import oracle.weblogic.kubernetes.annotations.IntegrationTest;
import oracle.weblogic.kubernetes.annotations.Namespaces;
import oracle.weblogic.kubernetes.logging.LoggingFacade;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static oracle.weblogic.kubernetes.TestConstants.ADMIN_SERVER_NAME_BASE;
import static oracle.weblogic.kubernetes.TestConstants.DOMAIN_STATUS_CONDITION_AVAILABLE_TYPE;
import static oracle.weblogic.kubernetes.TestConstants.DOMAIN_STATUS_CONDITION_COMPLETED_TYPE;
import static oracle.weblogic.kubernetes.TestConstants.DOMAIN_VERSION;
import static oracle.weblogic.kubernetes.TestConstants.MANAGED_SERVER_NAME_BASE;
import static oracle.weblogic.kubernetes.TestConstants.MII_BASIC_IMAGE_NAME;
import static oracle.weblogic.kubernetes.TestConstants.MII_BASIC_IMAGE_TAG;
import static oracle.weblogic.kubernetes.actions.TestActions.patchClusterCustomResource;
import static oracle.weblogic.kubernetes.assertions.TestAssertions.domainStatusClustersConditionTypeHasExpectedStatus;
import static oracle.weblogic.kubernetes.assertions.TestAssertions.domainStatusConditionTypeHasExpectedStatus;
import static oracle.weblogic.kubernetes.utils.ClusterUtils.checkDomainStatusClusterConditionTypeHasExpectedStatus;
import static oracle.weblogic.kubernetes.utils.CommonMiiTestUtils.createMiiDomain;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.checkServiceExists;
import static oracle.weblogic.kubernetes.utils.DomainUtils.checkDomainStatusConditionTypeHasExpectedStatus;
import static oracle.weblogic.kubernetes.utils.OperatorUtils.installAndVerifyOperator;
import static oracle.weblogic.kubernetes.utils.PodUtils.checkPodReady;
import static oracle.weblogic.kubernetes.utils.ThreadSafeLogger.getLogger;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

// Miscellaneous use cases for WKO retry improvements
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
    checkPodReady(adminServerPodName, domainUid, domainNamespace);

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
      checkPodReady(managedServerPrefix + i, domainUid, domainNamespace);
    }

    logger.info("Check admin service {0} is created in namespace {1}",
        adminServerPodName, domainNamespace);
    checkServiceExists(adminServerPodName, domainNamespace);

    // check managed server services created
    for (int i = 1; i <= replicaCount; i++) {
      logger.info("Check managed server service {0} is created in namespace {1}",
          managedServerPrefix + i, domainNamespace);
      checkServiceExists(managedServerPrefix + i, domainNamespace);
    }

    // check the domain status is Available and Complete
    assertTrue(assertDoesNotThrow(
        () -> domainStatusConditionTypeHasExpectedStatus(domainUid, domainNamespace,
            DOMAIN_STATUS_CONDITION_COMPLETED_TYPE, "True").call().booleanValue()),
        "domain status condition Completed type does not have expected True value");
    assertTrue(assertDoesNotThrow(
        () -> domainStatusConditionTypeHasExpectedStatus(domainUid, domainNamespace,
            DOMAIN_STATUS_CONDITION_AVAILABLE_TYPE, "True").call().booleanValue()),
        "domain status condition Available type does not have expected True value");

    // check the cluster condition is Available and Complete
    assertTrue(assertDoesNotThrow(
        () -> domainStatusClustersConditionTypeHasExpectedStatus(domainUid, domainNamespace, clusterName,
            DOMAIN_STATUS_CONDITION_COMPLETED_TYPE, "True", DOMAIN_VERSION).call().booleanValue()),
        "domain status cluster condition Completed type does not have expected True value");
    assertTrue(assertDoesNotThrow(
        () -> domainStatusClustersConditionTypeHasExpectedStatus(domainUid, domainNamespace, clusterName,
            DOMAIN_STATUS_CONDITION_AVAILABLE_TYPE, "True", DOMAIN_VERSION).call().booleanValue()),
        "domain status cluster condition Available type does not have expected True value");
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
    logger.info("patch the cluster resource with serverStartPolicy");
    String patchStr = "[{\"op\": \"add\",\"path\": \"/spec/serverStartPolicy\", \"value\": \"Never\"}]";

    logger.info("Updating domain configuration using patch string: {0}", patchStr);
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
  }

}
