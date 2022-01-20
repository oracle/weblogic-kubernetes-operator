// Copyright (c) 2022, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.weblogic.kubernetes;

import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;

import io.kubernetes.client.custom.V1Patch;
import oracle.weblogic.domain.Domain;
import oracle.weblogic.kubernetes.annotations.IntegrationTest;
import oracle.weblogic.kubernetes.annotations.Namespaces;
import oracle.weblogic.kubernetes.logging.LoggingFacade;
import oracle.weblogic.kubernetes.utils.CommonTestUtils;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import static oracle.weblogic.kubernetes.TestConstants.DOMAIN_STATUS_CONDITION_AVAILABLE_TYPE;
import static oracle.weblogic.kubernetes.TestConstants.DOMAIN_STATUS_CONDITION_COMPLETED_TYPE;
import static oracle.weblogic.kubernetes.TestConstants.DOMAIN_STATUS_CONDITION_FAILED_TYPE;
import static oracle.weblogic.kubernetes.TestConstants.MANAGED_SERVER_NAME_BASE;
import static oracle.weblogic.kubernetes.TestConstants.MII_BASIC_IMAGE_NAME;
import static oracle.weblogic.kubernetes.TestConstants.MII_BASIC_IMAGE_TAG;
import static oracle.weblogic.kubernetes.actions.TestActions.dockerTag;
import static oracle.weblogic.kubernetes.actions.TestActions.getDomainCustomResource;
import static oracle.weblogic.kubernetes.actions.TestActions.getServiceNodePort;
import static oracle.weblogic.kubernetes.actions.TestActions.scaleClusterWithRestApi;
import static oracle.weblogic.kubernetes.actions.impl.Domain.patchDomainCustomResource;
import static oracle.weblogic.kubernetes.assertions.TestAssertions.verifyRollingRestartOccurred;
import static oracle.weblogic.kubernetes.utils.CommonMiiTestUtils.createMiiDomainAndVerify;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.checkPodReadyAndServiceExists;
import static oracle.weblogic.kubernetes.utils.DomainUtils.checkDomainStatusConditionTypeExists;
import static oracle.weblogic.kubernetes.utils.DomainUtils.checkDomainStatusConditionTypeHasExpectedStatus;
import static oracle.weblogic.kubernetes.utils.DomainUtils.verifyDomainStatusConditionTypeDoesNotExist;
import static oracle.weblogic.kubernetes.utils.ImageUtils.createSecretForBaseImages;
import static oracle.weblogic.kubernetes.utils.ImageUtils.dockerLoginAndPushImageToRegistry;
import static oracle.weblogic.kubernetes.utils.OKDUtils.createRouteForOKD;
import static oracle.weblogic.kubernetes.utils.OKDUtils.setTlsTerminationForRoute;
import static oracle.weblogic.kubernetes.utils.OperatorUtils.installAndVerifyOperator;
import static oracle.weblogic.kubernetes.utils.PodUtils.checkPodDoesNotExist;
import static oracle.weblogic.kubernetes.utils.PodUtils.getPodCreationTime;
import static oracle.weblogic.kubernetes.utils.ThreadSafeLogger.getLogger;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests related to Domain status conditions logged by operator.
 * The tests check for the Completed/Available conditions for multiple usecases.
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DisplayName("Verify the domain status conditions for domain lifecycle")
@IntegrationTest
class ItDiagnosticsCompleteAvailableCondition {

  private static final String adminServerName = "admin-server";
  private static final String cluster1Name = "cluster-1";
  private static final String domainUid = "diagnosticsdomain";
  private static final String adminServerPodName = domainUid + "-" + adminServerName;
  private static final String managedServerPodNamePrefix = domainUid + "-" + MANAGED_SERVER_NAME_BASE;

  private static String opServiceAccount = null;
  private static String opNamespace = null;
  private static int externalRestHttpsPort = 0;
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
    opNamespace = namespaces.get(0);

    logger.info("Assign a unique namespace for WebLogic domain");
    assertNotNull(namespaces.get(1), "Namespace is null");
    domainNamespace1 = namespaces.get(1);

    // set the service account name for the operator
    opServiceAccount = opNamespace + "-sa";

    // install and verify operator with REST API
    installAndVerifyOperator(opNamespace, opServiceAccount, true, 0, domainNamespace1);
    externalRestHttpsPort = getServiceNodePort(opNamespace, "external-weblogic-operator-svc");

    // This test uses the operator restAPI to scale the domain. To do this in OKD cluster,
    // we need to expose the external service as route and set tls termination to  passthrough
    logger.info("Create a route for the operator external service - only for OKD");
    createRouteForOKD("external-weblogic-operator-svc", opNamespace);
    // Patch the route just created to set tls termination to passthrough
    setTlsTerminationForRoute("external-weblogic-operator-svc", opNamespace);

    // create pull secrets for WebLogic image when running in non Kind Kubernetes cluster
    // this secret is used only for non-kind cluster
    createSecretForBaseImages(domainNamespace1);

    // create a domain resource
    logger.info("Create model-in-image domain {0} in namespace {1}, and wait until it comes up",
        domainUid, domainNamespace1);
    createMiiDomainAndVerify(
        domainNamespace1,
        domainUid,
        MII_BASIC_IMAGE_NAME + ":" + MII_BASIC_IMAGE_TAG,
        adminServerPodName,
        managedServerPodNamePrefix,
        replicaCount);
  }

  /**
   * Test domain status condition with serverStartPolicy set to IF_NEEDED.
   * Verify the following conditions are generated:
   * type: Completed, status: true
   * type: Available, status: true
   * Verify no Failed type condition generated.
   */
  @Test
  @DisplayName("Test domain status condition with serverStartPolicy set to IF_NEEDED")
  void testCompleteAvailableConditionWithIfNeeded() {

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
  }

  /**
   * Test domain status condition with serverStartPolicy set to ADMIN_ONLY.
   * Verify the following conditions are generated:
   * type: Completed, status: true
   * type: Available, status: true
   * Verify no Failed type condition generated.
   */
  @Test
  @DisplayName("Test domain events for various successful domain life cycle changes")
  void testCompleteAvailableConditionWithAdminOnly() {
    String patchStr;
    try {
      logger.info("patch the domain and change the serverStartPolicy to ADMIN_ONLY");

      patchStr = "[{\"op\": \"replace\",\"path\": \"/spec/serverStartPolicy\", \"value\": \"ADMIN_ONLY\"}]";

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
          DOMAIN_STATUS_CONDITION_AVAILABLE_TYPE, "False");
      // verify there is no status condition type Failed
      verifyDomainStatusConditionTypeDoesNotExist(domainUid, domainNamespace1, DOMAIN_STATUS_CONDITION_FAILED_TYPE);
    } finally {
      restoreDomainResource();
    }
  }

  /**
   * Test domain status condition with serverStartPolicy set to NEVER.
   * Verify all the servers will be shutdown.
   * Verify the following conditions are generated:
   * type: Completed, status: true
   * type: Available, status: False
   * Verify no Failed type condition generated.
   */
  @Test
  @DisplayName("Test domain status condition with serverStartPolicy set to NEVER")
  void testCompleteAvailableConditionWithNever() {
    String patchStr;
    try {
      logger.info("patch the domain resource with serverStartPolicy set to NEVER");
      patchStr = "[{\"op\": \"replace\",\"path\": \"/spec/serverStartPolicy\", \"value\": \"NEVER\"}]";

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
      restoreDomainResource();
    }
  }

  /**
   * Test domain status condition with cluster replica set to zero and min-replicas set to zero.
   * Verify all the cluster servers pods will be shutdown.
   * Verify the following conditions are generated:
   * type: Completed, status: true
   * type: Available, status: False
   * Verify no Failed type condition generated.
   */
  @Test
  @DisplayName("Test domain status condition with cluster replica set to zero and min-replicas set to zero")
  void testCompleteAvailableConditionWithReplicaZero() {
    String patchStr;
    try {
      logger.info("patch the domain resource with new cluster replica 0");
      patchStr = "[{\"op\": \"replace\",\"path\": \"/spec/clusters/0/replicas\", \"value\": 0}]";

      logger.info("Updating domain configuration using patch string: {0}", patchStr);
      assertTrue(patchDomainCustomResource(domainUid, domainNamespace1, new V1Patch(patchStr),
          V1Patch.PATCH_FORMAT_JSON_PATCH), "Failed to patch domain");

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
          DOMAIN_STATUS_CONDITION_AVAILABLE_TYPE, "False");
      // verify there is no status condition type Failed
      verifyDomainStatusConditionTypeDoesNotExist(domainUid, domainNamespace1, DOMAIN_STATUS_CONDITION_FAILED_TYPE);

    } finally {
      restoreDomainResource();
    }
  }

  /**
   * Test domain status condition with cluster serverStartPolicy to NEVER.
   * Verify all the cluster servers pods will be shutdown.
   * Verify the following conditions are generated:
   * type: Completed, status: true
   * type: Available, status: False
   * Verify no Failed type condition generated.
   */
  @Test
  @DisplayName("Test domain status condition with cluster serverStartPolicy to NEVER")
  void testCompleteAvailableConditionWithClusterNever() {
    String patchStr;
    try {
      logger.info("patch the domain resource with cluster serverStartPolicy to NEVER");
      patchStr = "[{\"op\": \"replace\",\"path\": \"/spec/clusters/0/serverStartPolicy\", \"value\": \"NEVER\"}]";

      logger.info("Updating domain configuration using patch string: {0}", patchStr);
      assertTrue(patchDomainCustomResource(domainUid, domainNamespace1, new V1Patch(patchStr),
          V1Patch.PATCH_FORMAT_JSON_PATCH), "Failed to patch domain");

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
          DOMAIN_STATUS_CONDITION_AVAILABLE_TYPE, "False");
      // verify there is no status condition type Failed
      verifyDomainStatusConditionTypeDoesNotExist(domainUid, domainNamespace1, DOMAIN_STATUS_CONDITION_FAILED_TYPE);
    } finally {
      restoreDomainResource();
    }
  }

  /**
   * Test domain status condition with cluster replica set to larger than max size of cluster.
   * Verify all the cluster servers pods will be up and running.
   * Verify the following conditions are generated:
   * type: Completed, status: false
   * type: Available, status: true
   * Verify no Failed type condition generated.
   */
  @Test
  @DisplayName("Test domain status condition with cluster replica set to larger than max size of cluster")
  void testCompleteAvailableConditionWithReplicaExceedMaxSize() {
    String patchStr;
    try {
      logger.info("patch the domain resource with replica larger than max size of cluster");
      int newReplicaCount = maxClusterSize + 1;
      patchStr = "[{\"op\": \"replace\",\"path\": \"/spec/clusters/0/replicas\", \"value\": " + newReplicaCount + "}]";

      logger.info("Updating domain configuration using patch string: {0}", patchStr);
      assertTrue(patchDomainCustomResource(domainUid, domainNamespace1, new V1Patch(patchStr),
          V1Patch.PATCH_FORMAT_JSON_PATCH), "Failed to patch domain");

      // verify the admin server service exists
      checkPodReadyAndServiceExists(adminServerPodName, domainUid, domainNamespace1);

      // verify the cluster server pods are up and running
      logger.info("Checking managed server pods were ready");
      for (int i = 1; i <= maxClusterSize; i++) {
        checkPodReadyAndServiceExists(managedServerPodNamePrefix + i, domainUid, domainNamespace1);
      }

      // verify there is no pod created larger than max size of cluster
      for (int i = maxClusterSize + 1; i <= newReplicaCount; i++) {
        checkPodDoesNotExist(managedServerPodNamePrefix + i, domainUid, domainNamespace1);
      }

      // verify the condition type Completed exists
      checkDomainStatusConditionTypeExists(domainUid, domainNamespace1, DOMAIN_STATUS_CONDITION_COMPLETED_TYPE);
      // verify the condition type Available exists
      checkDomainStatusConditionTypeExists(domainUid, domainNamespace1, DOMAIN_STATUS_CONDITION_AVAILABLE_TYPE);
      // verify the condition Completed type has status True
      checkDomainStatusConditionTypeHasExpectedStatus(domainUid, domainNamespace1,
          DOMAIN_STATUS_CONDITION_COMPLETED_TYPE, "False");
      // verify the condition Available type has status True
      checkDomainStatusConditionTypeHasExpectedStatus(domainUid, domainNamespace1,
          DOMAIN_STATUS_CONDITION_AVAILABLE_TYPE, "True");
      // verify the condition Failed type has status True
      checkDomainStatusConditionTypeHasExpectedStatus(domainUid, domainNamespace1,
          DOMAIN_STATUS_CONDITION_FAILED_TYPE, "True");

    } finally {
      restoreDomainResource();
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
    String patchStr;
    try {
      logger.info("patch the domain resource with replica less than max size of cluster");
      int newReplicaCount = replicaCount - 1;
      patchStr = "[{\"op\": \"replace\",\"path\": \"/spec/clusters/0/replicas\", \"value\": " + newReplicaCount + "}]";

      logger.info("Updating domain configuration using patch string: {0}", patchStr);
      assertTrue(patchDomainCustomResource(domainUid, domainNamespace1, new V1Patch(patchStr),
          V1Patch.PATCH_FORMAT_JSON_PATCH), "Failed to patch domain");

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
      restoreDomainResource();
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

    // scale down the cluster
    int newReplicaCount = 1;
    assertDoesNotThrow(() ->
        scaleClusterWithRestApi(domainUid, cluster1Name, 1, externalRestHttpsPort, opNamespace, opServiceAccount));

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
        scaleClusterWithRestApi(domainUid, cluster1Name, 2, externalRestHttpsPort, opNamespace, opServiceAccount));

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

    Domain domain = assertDoesNotThrow(() -> getDomainCustomResource(domainUid, domainNamespace1));
    //print out the original image name
    String imageName = domain.getSpec().getImage();
    logger.info("Currently the image name used for the domain is: {0}", imageName);

    //change image name to imageUpdate
    String imageTag = CommonTestUtils.getDateAndTimeStamp();
    String newImage = MII_BASIC_IMAGE_NAME + ":" + imageTag;
    dockerTag(imageName, newImage);
    dockerLoginAndPushImageToRegistry(newImage);
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
  }

  private void restoreDomainResource() {
    // patch the domain back to the original state
    logger.info("patch the domain and change the serverStartPolicy to IF_NEEDED");
    String patchStr
        = "["
        + "{\"op\": \"replace\",\"path\": \"/spec/serverStartPolicy\", \"value\": \"IF_NEEDED\"},"
        + "{\"op\": \"replace\",\"path\": \"/spec/clusters/0/replicas\", \"value\": 2},"
        + "{\"op\": \"replace\",\"path\": \"/spec/clusters/0/serverStartPolicy\", \"value\": \"IF_NEEDED\"}"
        + "]";

    logger.info("Updating domain configuration using patch string: {0}", patchStr);
    assertTrue(patchDomainCustomResource(domainUid, domainNamespace1, new V1Patch(patchStr),
        V1Patch.PATCH_FORMAT_JSON_PATCH), "Failed to patch domain");

    logger.info("Checking for admin server pod is up and running");
    checkPodReadyAndServiceExists(adminServerPodName, domainUid, domainNamespace1);

    // verify all the managed servers are up and running
    logger.info("Checking managed server pods are up and running");
    for (int i = 1; i <= replicaCount; i++) {
      checkPodReadyAndServiceExists(managedServerPodNamePrefix + i, domainUid, domainNamespace1);
    }
  }
}
