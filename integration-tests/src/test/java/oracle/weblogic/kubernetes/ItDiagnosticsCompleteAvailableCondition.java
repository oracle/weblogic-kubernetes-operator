// Copyright (c) 2022, 2023, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.weblogic.kubernetes;

import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;

import io.kubernetes.client.custom.V1Patch;
import oracle.weblogic.domain.DomainResource;
import oracle.weblogic.kubernetes.annotations.IntegrationTest;
import oracle.weblogic.kubernetes.annotations.Namespaces;
import oracle.weblogic.kubernetes.logging.LoggingFacade;
import oracle.weblogic.kubernetes.utils.CommonTestUtils;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import static oracle.weblogic.kubernetes.TestConstants.ADMIN_SERVER_NAME_BASE;
import static oracle.weblogic.kubernetes.TestConstants.DOMAIN_STATUS_CONDITION_AVAILABLE_TYPE;
import static oracle.weblogic.kubernetes.TestConstants.DOMAIN_STATUS_CONDITION_COMPLETED_TYPE;
import static oracle.weblogic.kubernetes.TestConstants.DOMAIN_STATUS_CONDITION_FAILED_TYPE;
import static oracle.weblogic.kubernetes.TestConstants.MANAGED_SERVER_NAME_BASE;
import static oracle.weblogic.kubernetes.TestConstants.MII_BASIC_IMAGE_NAME;
import static oracle.weblogic.kubernetes.TestConstants.MII_BASIC_IMAGE_TAG;
import static oracle.weblogic.kubernetes.actions.TestActions.deleteClusterCustomResource;
import static oracle.weblogic.kubernetes.actions.TestActions.getDomainCustomResource;
import static oracle.weblogic.kubernetes.actions.TestActions.imageTag;
import static oracle.weblogic.kubernetes.actions.TestActions.patchClusterCustomResource;
import static oracle.weblogic.kubernetes.actions.TestActions.scaleCluster;
import static oracle.weblogic.kubernetes.actions.impl.Domain.patchDomainCustomResource;
import static oracle.weblogic.kubernetes.assertions.TestAssertions.verifyRollingRestartOccurred;
import static oracle.weblogic.kubernetes.utils.CommonMiiTestUtils.createMiiDomainAndVerify;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.checkPodReadyAndServiceExists;
import static oracle.weblogic.kubernetes.utils.DomainUtils.checkDomainStatusConditionTypeExists;
import static oracle.weblogic.kubernetes.utils.DomainUtils.checkDomainStatusConditionTypeHasExpectedStatus;
import static oracle.weblogic.kubernetes.utils.DomainUtils.deleteDomainResource;
import static oracle.weblogic.kubernetes.utils.DomainUtils.verifyDomainStatusConditionTypeDoesNotExist;
import static oracle.weblogic.kubernetes.utils.ImageUtils.createBaseRepoSecret;
import static oracle.weblogic.kubernetes.utils.ImageUtils.imageRepoLoginAndPushImageToRegistry;
import static oracle.weblogic.kubernetes.utils.OperatorUtils.installAndVerifyOperator;
import static oracle.weblogic.kubernetes.utils.PodUtils.checkPodDoesNotExist;
import static oracle.weblogic.kubernetes.utils.PodUtils.getPodCreationTime;
import static oracle.weblogic.kubernetes.utils.ThreadSafeLogger.getLogger;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests related to Domain status conditions logged by operator.
 * The tests check for the Completed/Available conditions for multiple usecases.
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DisplayName("Verify the domain status conditions for domain lifecycle")
@IntegrationTest
@Tag("olcne")
@Tag("kind-parallel")
@Tag("okd-wls-mrg")
@Tag("oke-gate")
class ItDiagnosticsCompleteAvailableCondition {

  private static final String cluster1Name = "cluster-1";

  private static final String clusterResName = cluster1Name;

  private static LoggingFacade logger = null;
  private static String domainNamespace1 = null;
  private static int replicaCount = 2;
  private static int maxClusterSize = 5;

  /**
   * Assigns unique namespaces for operator and domains.
   * Pull WebLogic image if running tests in Kind cluster.
   * Installs operator and create domain.
   *
   * @param namespaces injected by JUnit
   */
  @BeforeAll
  public static void initAll(@Namespaces(2) List<String> namespaces) {
    logger = getLogger();
    logger.info("Assign a unique namespace for operator");
    assertNotNull(namespaces.get(0), "Namespace is null");
    String opNamespace = namespaces.get(0);

    logger.info("Assign a unique namespace for WebLogic domain");
    assertNotNull(namespaces.get(1), "Namespace is null");
    domainNamespace1 = namespaces.get(1);

    // set the service account name for the operator
    String opServiceAccount = opNamespace + "-sa";

    // install and verify operator with REST API
    installAndVerifyOperator(opNamespace, opServiceAccount, true, 0, domainNamespace1);

    // create pull secrets for WebLogic image when running in non Kind Kubernetes cluster
    // this secret is used only for non-kind cluster
    createBaseRepoSecret(domainNamespace1);
  }

  /**
   * Test domain status condition with serverStartPolicy set to IfNeeded.
   * Verify the following conditions are generated:
   * type: Completed, status: true
   * type: Available, status: true
   * Verify no Failed type condition generated.
   */
  @Test
  @DisplayName("Test domain status condition with serverStartPolicy set to IfNeeded")
  @Tag("gate")
  void testCompleteAvailableConditionWithIfNeeded() {
    String domainUid = "diagnosticsdomain1";
    createDomainAndVerify(domainUid);

    try {
      // verify the condition type Completed exists
      checkDomainStatusConditionTypeExists(domainUid, domainNamespace1, DOMAIN_STATUS_CONDITION_COMPLETED_TYPE);
      // verify the condition type Available exists
      checkDomainStatusConditionTypeExists(domainUid, domainNamespace1, DOMAIN_STATUS_CONDITION_AVAILABLE_TYPE);
      // verify the condition Completed type has status True
      checkDomainStatusConditionTypeHasExpectedStatus(domainUid, domainNamespace1,
          DOMAIN_STATUS_CONDITION_COMPLETED_TYPE, "True");
      // verify the condition Available type has status True
      checkDomainStatusConditionTypeHasExpectedStatus(domainUid, domainNamespace1,
          DOMAIN_STATUS_CONDITION_AVAILABLE_TYPE, "True");
      // verify there is no status condition type Failed
      verifyDomainStatusConditionTypeDoesNotExist(domainUid, domainNamespace1, DOMAIN_STATUS_CONDITION_FAILED_TYPE);
    } finally {
      deleteDomainResource(domainNamespace1, domainUid);
      deleteClusterCustomResource(clusterResName, domainNamespace1);
    }
  }

  /**
   * Test domain status condition with serverStartPolicy set to AdminOnly.
   * Verify the following conditions are generated:
   * type: Completed, status: true
   * type: Available, status: true
   * Verify no Failed type condition generated.
   */
  @Test
  @DisplayName("Test domain events for various successful domain life cycle changes")
  void testCompleteAvailableConditionWithAdminOnly() {
    String domainUid = "diagnosticsdomain2";
    createDomainAndVerify(domainUid);

    String adminServerPodName = domainUid + "-" + ADMIN_SERVER_NAME_BASE;
    String managedServerPodNamePrefix = domainUid + "-" + MANAGED_SERVER_NAME_BASE;
    String patchStr;
    try {
      logger.info("patch the domain and change the serverStartPolicy to AdminOnly");
      patchStr = "[{\"op\": \"replace\",\"path\": \"/spec/serverStartPolicy\", \"value\": \"AdminOnly\"}]";

      logger.info("Updating domain configuration using patch string: {0}", patchStr);
      assertTrue(patchDomainCustomResource(domainUid, domainNamespace1, new V1Patch(patchStr),
          V1Patch.PATCH_FORMAT_JSON_PATCH), "Failed to patch domain");

      logger.info("Checking for admin server pod is up and running");
      checkPodReadyAndServiceExists(adminServerPodName, domainUid, domainNamespace1);

      // verify all the managed servers are shutdown
      logger.info("Checking managed server pods were shutdown");
      for (int i = 1; i <= replicaCount; i++) {
        checkPodDoesNotExist(managedServerPodNamePrefix + i, domainUid, domainNamespace1);
      }

      // verify the condition type Completed exists
      checkDomainStatusConditionTypeExists(domainUid, domainNamespace1, DOMAIN_STATUS_CONDITION_COMPLETED_TYPE);
      // verify the condition type Available exists
      checkDomainStatusConditionTypeExists(domainUid, domainNamespace1, DOMAIN_STATUS_CONDITION_AVAILABLE_TYPE);
      // verify the condition Completed type has status True
      checkDomainStatusConditionTypeHasExpectedStatus(domainUid, domainNamespace1,
          DOMAIN_STATUS_CONDITION_COMPLETED_TYPE, "True");
      // verify the condition Available type has status True
      checkDomainStatusConditionTypeHasExpectedStatus(domainUid, domainNamespace1,
          DOMAIN_STATUS_CONDITION_AVAILABLE_TYPE, "True");
      // verify there is no status condition type Failed
      verifyDomainStatusConditionTypeDoesNotExist(domainUid, domainNamespace1, DOMAIN_STATUS_CONDITION_FAILED_TYPE);
    } finally {
      deleteDomainResource(domainNamespace1, domainUid);
      deleteClusterCustomResource(clusterResName, domainNamespace1);
    }
  }

  /**
   * Test domain status condition with serverStartPolicy set to Never.
   * Verify all the servers will be shutdown.
   * Verify the following conditions are generated:
   * type: Completed, status: true
   * type: Available, status: False
   * Verify no Failed type condition generated.
   */
  @Test
  @DisplayName("Test domain status condition with serverStartPolicy set to Never")
  void testCompleteAvailableConditionWithNever() {
    String domainUid = "diagnosticsdomain3";
    createDomainAndVerify(domainUid);

    String adminServerPodName = domainUid + "-" + ADMIN_SERVER_NAME_BASE;
    String managedServerPodNamePrefix = domainUid + "-" + MANAGED_SERVER_NAME_BASE;
    String patchStr;
    try {
      logger.info("patch the domain resource with serverStartPolicy set to Never");
      patchStr = "[{\"op\": \"replace\",\"path\": \"/spec/serverStartPolicy\", \"value\": \"Never\"}]";

      logger.info("Updating domain configuration using patch string: {0}", patchStr);
      assertTrue(patchDomainCustomResource(domainUid, domainNamespace1, new V1Patch(patchStr),
          V1Patch.PATCH_FORMAT_JSON_PATCH), "Failed to patch domain");

      // verify all the servers are shutdown
      logger.info("Checking for admin server pod shutdown");
      checkPodDoesNotExist(adminServerPodName, domainUid, domainNamespace1);
      logger.info("Checking managed server pods were shutdown");
      for (int i = 1; i <= replicaCount; i++) {
        checkPodDoesNotExist(managedServerPodNamePrefix + i, domainUid, domainNamespace1);
      }

      // verify the condition type Completed exists
      checkDomainStatusConditionTypeExists(domainUid, domainNamespace1, DOMAIN_STATUS_CONDITION_COMPLETED_TYPE);
      // verify the condition type Available exists
      checkDomainStatusConditionTypeExists(domainUid, domainNamespace1, DOMAIN_STATUS_CONDITION_AVAILABLE_TYPE);
      // verify the condition Completed type has status True
      checkDomainStatusConditionTypeHasExpectedStatus(domainUid, domainNamespace1,
          DOMAIN_STATUS_CONDITION_COMPLETED_TYPE, "True");
      // verify the condition Available type has status True
      checkDomainStatusConditionTypeHasExpectedStatus(domainUid, domainNamespace1,
          DOMAIN_STATUS_CONDITION_AVAILABLE_TYPE, "False");
      // verify there is no status condition type Failed
      verifyDomainStatusConditionTypeDoesNotExist(domainUid, domainNamespace1, DOMAIN_STATUS_CONDITION_FAILED_TYPE);

    } finally {
      deleteDomainResource(domainNamespace1, domainUid);
      deleteClusterCustomResource(clusterResName, domainNamespace1);
    }
  }

  /**
   * Test domain status condition with cluster replica set to zero and min-replicas set to zero.
   * Verify all the cluster servers pods will be shutdown.
   * Verify the following conditions are generated:
   * type: Completed, status: true
   * type: Available, status: true
   * Verify no Failed type condition generated.
   */
  @Test
  @DisplayName("Test domain status condition with cluster replica set to zero and min-replicas set to zero")
  void testCompleteAvailableConditionWithReplicaZero() {
    String domainUid = "diagnosticsdomain4";
    createDomainAndVerify(domainUid);

    String adminServerPodName = domainUid + "-" + ADMIN_SERVER_NAME_BASE;
    String managedServerPodNamePrefix = domainUid + "-" + MANAGED_SERVER_NAME_BASE;
    String patchStr;
    try {
      logger.info("patch the cluster resource with new cluster replica 0");      
      patchStr
          = "["
          + "{\"op\": \"replace\", \"path\": \"/spec/replicas\", \"value\": 0}"
          + "]";
      V1Patch patch = new V1Patch(patchStr);
      assertTrue(patchClusterCustomResource(clusterResName, domainNamespace1,
          patch, V1Patch.PATCH_FORMAT_JSON_PATCH), "Failed to patch cluster");

      // verify the admin server service exists
      checkPodReadyAndServiceExists(adminServerPodName, domainUid, domainNamespace1);

      // verify the cluster server pods are shutdown
      logger.info("Checking managed server pods were shutdown");
      for (int i = 1; i <= replicaCount; i++) {
        checkPodDoesNotExist(managedServerPodNamePrefix + i, domainUid, domainNamespace1);
      }

      // verify the condition type Completed exists
      checkDomainStatusConditionTypeExists(domainUid, domainNamespace1, DOMAIN_STATUS_CONDITION_COMPLETED_TYPE);
      // verify the condition type Available exists
      checkDomainStatusConditionTypeExists(domainUid, domainNamespace1, DOMAIN_STATUS_CONDITION_AVAILABLE_TYPE);
      // verify the condition Completed type has status True
      checkDomainStatusConditionTypeHasExpectedStatus(domainUid, domainNamespace1,
          DOMAIN_STATUS_CONDITION_COMPLETED_TYPE, "True");
      // verify the condition Available type has status True
      checkDomainStatusConditionTypeHasExpectedStatus(domainUid, domainNamespace1,
          DOMAIN_STATUS_CONDITION_AVAILABLE_TYPE, "True");
      // verify there is no status condition type Failed
      verifyDomainStatusConditionTypeDoesNotExist(domainUid, domainNamespace1, DOMAIN_STATUS_CONDITION_FAILED_TYPE);

    } finally {
      deleteDomainResource(domainNamespace1, domainUid);
      deleteClusterCustomResource(clusterResName, domainNamespace1);
    }
  }

  /**
   * Test domain status condition with cluster serverStartPolicy to Never.
   * Verify all the cluster servers pods will be shutdown.
   * Verify the following conditions are generated:
   * type: Completed, status: true
   * type: Available, status: true
   * Verify no Failed type condition generated.
   */
  @Test
  @DisplayName("Test domain status condition with cluster serverStartPolicy to Never")
  void testCompleteAvailableConditionWithClusterNever() {
    String domainUid = "diagnosticsdomain5";
    createDomainAndVerify(domainUid);

    String adminServerPodName = domainUid + "-" + ADMIN_SERVER_NAME_BASE;
    String managedServerPodNamePrefix = domainUid + "-" + MANAGED_SERVER_NAME_BASE;
    String patchStr;
    try {
      logger.info("patch the cluster resource with cluster serverStartPolicy to Never");
      patchStr
          = "["
          + "{\"op\": \"replace\", \"path\": \"/spec/serverStartPolicy\", \"value\": \"Never\"}]";
      V1Patch patch = new V1Patch(patchStr);
      assertTrue(patchClusterCustomResource(clusterResName, domainNamespace1,
          patch, V1Patch.PATCH_FORMAT_JSON_PATCH), "Failed to patch cluster");

      // verify the admin server service exists
      checkPodReadyAndServiceExists(adminServerPodName, domainUid, domainNamespace1);

      // verify the cluster server pods are shutdown
      logger.info("Checking managed server pods were shutdown");
      for (int i = 1; i <= replicaCount; i++) {
        checkPodDoesNotExist(managedServerPodNamePrefix + i, domainUid, domainNamespace1);
      }

      // verify the condition type Completed exists
      checkDomainStatusConditionTypeExists(domainUid, domainNamespace1, DOMAIN_STATUS_CONDITION_COMPLETED_TYPE);
      // verify the condition type Available exists
      checkDomainStatusConditionTypeExists(domainUid, domainNamespace1, DOMAIN_STATUS_CONDITION_AVAILABLE_TYPE);
      // verify the condition Completed type has status True
      checkDomainStatusConditionTypeHasExpectedStatus(domainUid, domainNamespace1,
          DOMAIN_STATUS_CONDITION_COMPLETED_TYPE, "True");
      // verify the condition Available type has status True
      checkDomainStatusConditionTypeHasExpectedStatus(domainUid, domainNamespace1,
          DOMAIN_STATUS_CONDITION_AVAILABLE_TYPE, "True");
      // verify there is no status condition type Failed
      verifyDomainStatusConditionTypeDoesNotExist(domainUid, domainNamespace1, DOMAIN_STATUS_CONDITION_FAILED_TYPE);
    } finally {
      deleteDomainResource(domainNamespace1, domainUid);
      deleteClusterCustomResource(clusterResName, domainNamespace1);
    }
  }

  /**
   * Test domain status condition with cluster replica set to larger than max size of cluster.
   * Verify the patch operation failed.
   */
  @Test
  @DisplayName("Test domain status condition with cluster replica set to larger than max size of cluster")
  void testCompleteAvailableConditionWithReplicaExceedMaxSizeWithoutChangingIntrospectVersion() {
    String domainUid = "diagnosticsdomain6";
    createDomainAndVerify(domainUid);

    try {
      int newReplicaCount = maxClusterSize + 1;
      String patchStr;
      logger.info("patch the cluster resource with new cluster replica count {0}", newReplicaCount);
      patchStr
          = "["
          + "{\"op\": \"replace\", \"path\": \"/spec/replicas\", \"value\": " + newReplicaCount + "}"
          + "]";
      V1Patch patch = new V1Patch(patchStr);
      assertFalse(patchClusterCustomResource(clusterResName, domainNamespace1,
          patch, V1Patch.PATCH_FORMAT_JSON_PATCH), "Failed to patch cluster");
    } finally {
      deleteDomainResource(domainNamespace1, domainUid);
      deleteClusterCustomResource(clusterResName, domainNamespace1);
    }
  }

  /**
   * Test domain status condition with domain replica set to larger than max size of cluster and introspectVersion
   * changed.
   * Verify all the cluster servers pods will be up and running.
   * Verify the following conditions are generated:
   * type: Completed, status: false
   * type: Available, status: true
   * Verify no Failed type condition generated.
   */
  @Test
  @DisplayName("Test domain status condition with domain replica set to larger than max size of cluster")
  void testCompleteAvailableConditionWithReplicaExceedMaxSizeAndIntrospectVersionChanged() {
    String domainUid = "diagnosticsdomain7";
    createDomainAndVerify(domainUid);

    String adminServerPodName = domainUid + "-" + ADMIN_SERVER_NAME_BASE;
    String managedServerPodNamePrefix = domainUid + "-" + MANAGED_SERVER_NAME_BASE;
    String patchStr;
    try {
      int newReplicaCount = maxClusterSize + 1;
      logger.info("patch the domain resource with new introspectVersion and replicas higher than max cluster size");
      patchStr = "["
          + "{\"op\": \"replace\", \"path\": \"/spec/introspectVersion\", \"value\": \"12345\"},"
          + "{\"op\": \"replace\", \"path\": \"/spec/replicas\", \"value\": " + newReplicaCount + "}"
          + "]";
      logger.info("Updating domain configuration using patch string: {0}", patchStr);
      assertTrue(patchDomainCustomResource(domainUid, domainNamespace1, new V1Patch(patchStr),
          V1Patch.PATCH_FORMAT_JSON_PATCH), "Patch domain did not fail as expected");
      
      // verify the admin server service exists
      checkPodReadyAndServiceExists(adminServerPodName, domainUid, domainNamespace1);

      // verify the cluster server pods are up and running
      logger.info("Checking managed server pods were ready");
      for (int i = 1; i <= replicaCount; i++) {
        checkPodReadyAndServiceExists(managedServerPodNamePrefix + i, domainUid, domainNamespace1);
      }

      // verify there is no pod created larger than replicaCount since the cluster replicas does not change
      for (int i = replicaCount + 1; i <= newReplicaCount; i++) {
        checkPodDoesNotExist(managedServerPodNamePrefix + i, domainUid, domainNamespace1);
      }

      // verify the condition type Completed exists
      checkDomainStatusConditionTypeExists(domainUid, domainNamespace1, DOMAIN_STATUS_CONDITION_COMPLETED_TYPE);
      // verify the condition type Available exists
      checkDomainStatusConditionTypeExists(domainUid, domainNamespace1, DOMAIN_STATUS_CONDITION_AVAILABLE_TYPE);
      // verify the condition Completed type has status True
      checkDomainStatusConditionTypeHasExpectedStatus(domainUid, domainNamespace1,
          DOMAIN_STATUS_CONDITION_COMPLETED_TYPE, "True");
      // verify the condition Available type has status True
      checkDomainStatusConditionTypeHasExpectedStatus(domainUid, domainNamespace1,
          DOMAIN_STATUS_CONDITION_AVAILABLE_TYPE, "True");
    } finally {
      deleteDomainResource(domainNamespace1, domainUid);
      deleteClusterCustomResource(clusterResName, domainNamespace1);
    }
  }

  /**
   * Test domain status condition with cluster replica set to less than max size of cluster.
   * Verify all the cluster servers pods up to replicas will be up and running.
   * Verify the following conditions are generated:
   * type: Completed, status: true
   * type: Available, status: true
   * Verify no Failed type condition generated.
   */
  @Test
  @DisplayName("Test domain status condition with cluster replica set to less than max size of cluster")
  void testCompleteAvailableConditionWithReplicaLessThanMaxSize() {
    String domainUid = "diagnosticsdomain8";
    createDomainAndVerify(domainUid);

    String adminServerPodName = domainUid + "-" + ADMIN_SERVER_NAME_BASE;
    String managedServerPodNamePrefix = domainUid + "-" + MANAGED_SERVER_NAME_BASE;
    String patchStr;
    try {
      logger.info("patch the domain resource with replica less than max size of cluster");
      int newReplicaCount = replicaCount - 1;
      logger.info("patch the cluster resource with new cluster replica count {0}", newReplicaCount);
      patchStr
          = "["
          + "{\"op\": \"replace\", \"path\": \"/spec/replicas\", \"value\": " + newReplicaCount + "}"
          + "]";
      V1Patch patch = new V1Patch(patchStr);
      assertTrue(patchClusterCustomResource(clusterResName, domainNamespace1,
          patch, V1Patch.PATCH_FORMAT_JSON_PATCH), "Failed to patch cluster");     

      // verify the admin server service exists
      checkPodReadyAndServiceExists(adminServerPodName, domainUid, domainNamespace1);

      // verify the cluster server pods are up and running
      logger.info("Checking managed server pods were ready");
      for (int i = 1; i <= newReplicaCount; i++) {
        checkPodReadyAndServiceExists(managedServerPodNamePrefix + i, domainUid, domainNamespace1);
      }

      // verify there is no pod created larger than new replicas
      for (int i = newReplicaCount + 1; i <= replicaCount; i++) {
        checkPodDoesNotExist(managedServerPodNamePrefix + i, domainUid, domainNamespace1);
      }

      // verify the condition type Completed exists
      checkDomainStatusConditionTypeExists(domainUid, domainNamespace1, DOMAIN_STATUS_CONDITION_COMPLETED_TYPE);
      // verify the condition type Available exists
      checkDomainStatusConditionTypeExists(domainUid, domainNamespace1, DOMAIN_STATUS_CONDITION_AVAILABLE_TYPE);
      // verify the condition Completed type has status True
      checkDomainStatusConditionTypeHasExpectedStatus(domainUid, domainNamespace1,
          DOMAIN_STATUS_CONDITION_COMPLETED_TYPE, "True");
      // verify the condition Available type has status True
      checkDomainStatusConditionTypeHasExpectedStatus(domainUid, domainNamespace1,
          DOMAIN_STATUS_CONDITION_AVAILABLE_TYPE, "True");
      // verify there is no status condition type Failed
      verifyDomainStatusConditionTypeDoesNotExist(domainUid, domainNamespace1, DOMAIN_STATUS_CONDITION_FAILED_TYPE);
    } finally {
      deleteDomainResource(domainNamespace1, domainUid);
      deleteClusterCustomResource(clusterResName, domainNamespace1);
    }
  }

  /**
   * Test domain status condition with scaling up and down of cluster.
   * Verify all the cluster servers pods will be up and running.
   * Verify the following conditions are generated:
   * type: Completed, status: true
   * type: Available, status: true
   * Verify no Failed type condition generated.
   */
  @Test
  @DisplayName("Test domain status condition with scaling up and down of cluster")
  void testCompleteAvailableConditionWithScaleUpDownCluster() {
    String domainUid = "diagnosticsdomain9";
    createDomainAndVerify(domainUid);

    String adminServerPodName = domainUid + "-" + ADMIN_SERVER_NAME_BASE;
    String managedServerPodNamePrefix = domainUid + "-" + MANAGED_SERVER_NAME_BASE;

    try {
      // scale down the cluster
      int newReplicaCount = 1;
      assertDoesNotThrow(() ->
          scaleCluster(clusterResName, domainNamespace1, 1));

      // verify the admin server service exists
      checkPodReadyAndServiceExists(adminServerPodName, domainUid, domainNamespace1);

      // verify the cluster server pods are up and running
      logger.info("Checking managed server pods were ready");
      for (int i = 1; i <= newReplicaCount; i++) {
        checkPodReadyAndServiceExists(managedServerPodNamePrefix + i, domainUid, domainNamespace1);
      }

      // verify there is no pod created larger than replicas
      for (int i = newReplicaCount + 1; i <= replicaCount; i++) {
        checkPodDoesNotExist(managedServerPodNamePrefix + i, domainUid, domainNamespace1);
      }

      // verify the condition type Completed exists
      checkDomainStatusConditionTypeExists(domainUid, domainNamespace1, DOMAIN_STATUS_CONDITION_COMPLETED_TYPE);
      // verify the condition type Available exists
      checkDomainStatusConditionTypeExists(domainUid, domainNamespace1, DOMAIN_STATUS_CONDITION_AVAILABLE_TYPE);
      // verify the condition Completed type has status True
      checkDomainStatusConditionTypeHasExpectedStatus(domainUid, domainNamespace1,
          DOMAIN_STATUS_CONDITION_COMPLETED_TYPE, "True");
      // verify the condition Available type has status True
      checkDomainStatusConditionTypeHasExpectedStatus(domainUid, domainNamespace1,
          DOMAIN_STATUS_CONDITION_AVAILABLE_TYPE, "True");
      // verify there is no status condition type Failed
      verifyDomainStatusConditionTypeDoesNotExist(domainUid, domainNamespace1, DOMAIN_STATUS_CONDITION_FAILED_TYPE);

      // scale up the cluster
      newReplicaCount = 2;
      assertDoesNotThrow(() ->
          scaleCluster(clusterResName, domainNamespace1, 2));

      // verify the admin server service exists
      checkPodReadyAndServiceExists(adminServerPodName, domainUid, domainNamespace1);

      // verify the cluster server pods are up and running
      logger.info("Checking managed server pods were ready");
      for (int i = 1; i <= newReplicaCount; i++) {
        checkPodReadyAndServiceExists(managedServerPodNamePrefix + i, domainUid, domainNamespace1);
      }

      // verify the condition type Completed exists
      checkDomainStatusConditionTypeExists(domainUid, domainNamespace1, DOMAIN_STATUS_CONDITION_COMPLETED_TYPE);
      // verify the condition type Available exists
      checkDomainStatusConditionTypeExists(domainUid, domainNamespace1, DOMAIN_STATUS_CONDITION_AVAILABLE_TYPE);
      // verify the condition Completed type has status True
      checkDomainStatusConditionTypeHasExpectedStatus(domainUid, domainNamespace1,
          DOMAIN_STATUS_CONDITION_COMPLETED_TYPE, "True");
      // verify the condition Available type has status True
      checkDomainStatusConditionTypeHasExpectedStatus(domainUid, domainNamespace1,
          DOMAIN_STATUS_CONDITION_AVAILABLE_TYPE, "True");
      // verify there is no status condition type Failed
      verifyDomainStatusConditionTypeDoesNotExist(domainUid, domainNamespace1, DOMAIN_STATUS_CONDITION_FAILED_TYPE);
    } finally {
      deleteDomainResource(domainNamespace1, domainUid);
      deleteClusterCustomResource(clusterResName, domainNamespace1);
    }
  }

  /**
   * Test domain status condition with new restartVersion.
   * Verify all the server pods are restarted.
   * Verify the following conditions are generated:
   * type: Completed, status: true
   * type: Available, status: true
   * Verify no Failed type condition generated.
   */
  @Test
  @DisplayName("Test domain status condition with new restartVersion")
  void testCompleteAvailableConditionWithNewRestartVersion() {
    String domainUid = "diagnosticsdomain10";
    createDomainAndVerify(domainUid);

    String adminServerPodName = domainUid + "-" + ADMIN_SERVER_NAME_BASE;
    String managedServerPodNamePrefix = domainUid + "-" + MANAGED_SERVER_NAME_BASE;

    try {
      // get the pod creation time stamps
      LinkedHashMap<String, OffsetDateTime> pods = new LinkedHashMap<>();
      // get the creation time of the admin server pod before patching
      OffsetDateTime adminPodCreationTime = getPodCreationTime(domainNamespace1, adminServerPodName);
      pods.put(adminServerPodName, adminPodCreationTime);
      // get the creation time of the managed server pods before patching
      for (int i = 1; i <= replicaCount; i++) {
        pods.put(managedServerPodNamePrefix + i,
            getPodCreationTime(domainNamespace1, managedServerPodNamePrefix + i));
      }

      logger.info("patch the domain resource with new restartVersion");
      String patchStr = "[{\"op\": \"add\",\"path\": \"/spec/restartVersion\", \"value\": \"9\"}]";

      logger.info("Updating domain configuration using patch string: {0}", patchStr);
      assertTrue(patchDomainCustomResource(domainUid, domainNamespace1, new V1Patch(patchStr),
          V1Patch.PATCH_FORMAT_JSON_PATCH), "Failed to patch domain");

      // check the domain is restarted
      verifyRollingRestartOccurred(pods, 1, domainNamespace1);

      // verify the admin server service exists
      checkPodReadyAndServiceExists(adminServerPodName, domainUid, domainNamespace1);

      // verify the cluster server pods are up and running
      logger.info("Checking managed server pods were ready");
      for (int i = 1; i <= replicaCount; i++) {
        checkPodReadyAndServiceExists(managedServerPodNamePrefix + i, domainUid, domainNamespace1);
      }

      // verify the condition type Completed exists
      checkDomainStatusConditionTypeExists(domainUid, domainNamespace1, DOMAIN_STATUS_CONDITION_COMPLETED_TYPE);
      // verify the condition type Available exists
      checkDomainStatusConditionTypeExists(domainUid, domainNamespace1, DOMAIN_STATUS_CONDITION_AVAILABLE_TYPE);
      // verify the condition Completed type has status True
      checkDomainStatusConditionTypeHasExpectedStatus(domainUid, domainNamespace1,
          DOMAIN_STATUS_CONDITION_COMPLETED_TYPE, "True");
      // verify the condition Available type has status True
      checkDomainStatusConditionTypeHasExpectedStatus(domainUid, domainNamespace1,
          DOMAIN_STATUS_CONDITION_AVAILABLE_TYPE, "True");
      // verify there is no status condition type Failed
      verifyDomainStatusConditionTypeDoesNotExist(domainUid, domainNamespace1, DOMAIN_STATUS_CONDITION_FAILED_TYPE);
    } finally {
      deleteDomainResource(domainNamespace1, domainUid);
      deleteClusterCustomResource(clusterResName, domainNamespace1);
    }
  }

  /**
   * Test domain status condition with new image.
   * Verify all the servers pods are restarted.
   * Verify the following conditions are generated:
   * type: Completed, status: true
   * type: Available, status: true
   * Verify no Failed type condition generated.
   */
  @Test
  @DisplayName("Test domain status condition with new restartVersion")
  void testCompleteAvailableConditionWithNewImage() {
    String domainUid = "diagnosticsdomain11";
    createDomainAndVerify(domainUid);

    String adminServerPodName = domainUid + "-" + ADMIN_SERVER_NAME_BASE;
    String managedServerPodNamePrefix = domainUid + "-" + MANAGED_SERVER_NAME_BASE;

    try {
      // get the pod creation time stamps
      LinkedHashMap<String, OffsetDateTime> pods = new LinkedHashMap<>();
      // get the creation time of the admin server pod before patching
      OffsetDateTime adminPodCreationTime = getPodCreationTime(domainNamespace1, adminServerPodName);
      pods.put(adminServerPodName, adminPodCreationTime);
      // get the creation time of the managed server pods before patching
      for (int i = 1; i <= replicaCount; i++) {
        pods.put(managedServerPodNamePrefix + i,
            getPodCreationTime(domainNamespace1, managedServerPodNamePrefix + i));
      }

      DomainResource domain = assertDoesNotThrow(() -> getDomainCustomResource(domainUid, domainNamespace1));
      //print out the original image name
      String imageName = domain.getSpec().getImage();
      logger.info("Currently the image name used for the domain is: {0}", imageName);

      //change image name to imageUpdate
      String imageTag = CommonTestUtils.getDateAndTimeStamp();
      String newImage = MII_BASIC_IMAGE_NAME + ":" + imageTag;
      imageTag(imageName, newImage);
      imageRepoLoginAndPushImageToRegistry(newImage);
      logger.info("new image: {0}", newImage);

      logger.info("patch the domain resource with new image");
      String patchStr = "[{\"op\": \"replace\",\"path\": \"/spec/image\", \"value\": \"" + newImage + "\"}]";

      logger.info("Updating domain configuration using patch string: {0}", patchStr);
      assertTrue(patchDomainCustomResource(domainUid, domainNamespace1, new V1Patch(patchStr),
          V1Patch.PATCH_FORMAT_JSON_PATCH), "Failed to patch domain");

      // check the domain is restarted
      verifyRollingRestartOccurred(pods, 1, domainNamespace1);

      // verify the admin server service exists
      checkPodReadyAndServiceExists(adminServerPodName, domainUid, domainNamespace1);

      // verify the cluster server pods are up and running
      logger.info("Checking managed server pods were ready");
      for (int i = 1; i <= replicaCount; i++) {
        checkPodReadyAndServiceExists(managedServerPodNamePrefix + i, domainUid, domainNamespace1);
      }

      // verify the condition type Completed exists
      checkDomainStatusConditionTypeExists(domainUid, domainNamespace1, DOMAIN_STATUS_CONDITION_COMPLETED_TYPE);
      // verify the condition type Available exists
      checkDomainStatusConditionTypeExists(domainUid, domainNamespace1, DOMAIN_STATUS_CONDITION_AVAILABLE_TYPE);
      // verify the condition Completed type has status True
      checkDomainStatusConditionTypeHasExpectedStatus(domainUid, domainNamespace1,
          DOMAIN_STATUS_CONDITION_COMPLETED_TYPE, "True");
      // verify the condition Available type has status True
      checkDomainStatusConditionTypeHasExpectedStatus(domainUid, domainNamespace1,
          DOMAIN_STATUS_CONDITION_AVAILABLE_TYPE, "True");
      // verify there is no status condition type Failed
      verifyDomainStatusConditionTypeDoesNotExist(domainUid, domainNamespace1, DOMAIN_STATUS_CONDITION_FAILED_TYPE);
    } finally {
      deleteDomainResource(domainNamespace1, domainUid);
      deleteClusterCustomResource(clusterResName, domainNamespace1);
    }
  }

  private void createDomainAndVerify(String domainUid) {
    String adminServerPodName = domainUid + "-" + ADMIN_SERVER_NAME_BASE;
    String managedServerPodNamePrefix = domainUid + "-" + MANAGED_SERVER_NAME_BASE;
    // create a domain resource
    logger.info("Create model-in-image domain {0} in namespace {1}, and wait until it comes up",
        domainUid, domainNamespace1);
    createMiiDomainAndVerify(
        domainNamespace1,
        domainUid,
        MII_BASIC_IMAGE_NAME + ":" + MII_BASIC_IMAGE_TAG,
        adminServerPodName,
        managedServerPodNamePrefix,
        replicaCount,
        List.of(cluster1Name));
  }
}
