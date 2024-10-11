// Copyright (c) 2022, 2024, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.weblogic.kubernetes;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.function.UnaryOperator;

import io.kubernetes.client.custom.Quantity;
import io.kubernetes.client.custom.V1Patch;
import io.kubernetes.client.openapi.models.V1EnvVar;
import io.kubernetes.client.openapi.models.V1LocalObjectReference;
import io.kubernetes.client.openapi.models.V1ObjectMeta;
import io.kubernetes.client.openapi.models.V1PersistentVolumeClaimVolumeSource;
import io.kubernetes.client.openapi.models.V1Volume;
import io.kubernetes.client.openapi.models.V1VolumeMount;
import oracle.weblogic.domain.AdminServer;
import oracle.weblogic.domain.AdminService;
import oracle.weblogic.domain.ClusterList;
import oracle.weblogic.domain.ClusterResource;
import oracle.weblogic.domain.Configuration;
import oracle.weblogic.domain.CreateIfNotExists;
import oracle.weblogic.domain.DomainCreationImage;
import oracle.weblogic.domain.DomainOnPV;
import oracle.weblogic.domain.DomainOnPVType;
import oracle.weblogic.domain.DomainResource;
import oracle.weblogic.domain.DomainSpec;
import oracle.weblogic.domain.ServerPod;
import oracle.weblogic.kubernetes.actions.impl.primitive.WitParams;
import oracle.weblogic.kubernetes.annotations.IntegrationTest;
import oracle.weblogic.kubernetes.annotations.Namespaces;
import oracle.weblogic.kubernetes.assertions.impl.Cluster;
import oracle.weblogic.kubernetes.logging.LoggingFacade;
import oracle.weblogic.kubernetes.utils.DomainUtils;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static oracle.weblogic.kubernetes.TestConstants.ADMIN_PASSWORD_DEFAULT;
import static oracle.weblogic.kubernetes.TestConstants.ADMIN_USERNAME_DEFAULT;
import static oracle.weblogic.kubernetes.TestConstants.BASE_IMAGES_REPO_SECRET_NAME;
import static oracle.weblogic.kubernetes.TestConstants.CLUSTER_VERSION;
import static oracle.weblogic.kubernetes.TestConstants.DOMAIN_API_VERSION;
import static oracle.weblogic.kubernetes.TestConstants.DOMAIN_IMAGES_PREFIX;
import static oracle.weblogic.kubernetes.TestConstants.DOMAIN_STATUS_CONDITION_ROLLING_TYPE;
import static oracle.weblogic.kubernetes.TestConstants.FAILURE_RETRY_INTERVAL_SECONDS;
import static oracle.weblogic.kubernetes.TestConstants.FAILURE_RETRY_LIMIT_MINUTES;
import static oracle.weblogic.kubernetes.TestConstants.IMAGE_PULL_POLICY;
import static oracle.weblogic.kubernetes.TestConstants.K8S_NODEPORT_HOST;
import static oracle.weblogic.kubernetes.TestConstants.OKE_CLUSTER;
import static oracle.weblogic.kubernetes.TestConstants.RESULTS_TEMPFILE;
import static oracle.weblogic.kubernetes.TestConstants.SKIP_CLEANUP;
import static oracle.weblogic.kubernetes.TestConstants.WEBLOGIC_IMAGE_TO_USE_IN_SPEC;
import static oracle.weblogic.kubernetes.actions.ActionConstants.MODEL_DIR;
import static oracle.weblogic.kubernetes.actions.ActionConstants.RESOURCE_DIR;
import static oracle.weblogic.kubernetes.actions.TestActions.deleteClusterCustomResource;
import static oracle.weblogic.kubernetes.actions.TestActions.deleteDomainCustomResource;
import static oracle.weblogic.kubernetes.actions.TestActions.deletePersistentVolume;
import static oracle.weblogic.kubernetes.actions.TestActions.deletePersistentVolumeClaim;
import static oracle.weblogic.kubernetes.actions.TestActions.getDomainCustomResource;
import static oracle.weblogic.kubernetes.actions.TestActions.getNextIntrospectVersion;
import static oracle.weblogic.kubernetes.actions.TestActions.getServicePort;
import static oracle.weblogic.kubernetes.actions.TestActions.now;
import static oracle.weblogic.kubernetes.actions.TestActions.scaleCluster;
import static oracle.weblogic.kubernetes.actions.TestActions.shutdownDomain;
import static oracle.weblogic.kubernetes.actions.impl.Cluster.listClusterCustomResources;
import static oracle.weblogic.kubernetes.actions.impl.Domain.patchDomainCustomResource;
import static oracle.weblogic.kubernetes.assertions.TestAssertions.verifyRollingRestartOccurred;
import static oracle.weblogic.kubernetes.utils.AuxiliaryImageUtils.createAndPushAuxiliaryImage;
import static oracle.weblogic.kubernetes.utils.ClusterUtils.createClusterAndVerify;
import static oracle.weblogic.kubernetes.utils.ClusterUtils.createClusterResource;
import static oracle.weblogic.kubernetes.utils.ClusterUtils.removeReplicasSettingAndVerify;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.checkPodReadyAndServiceExists;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.checkServiceExists;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.getDateAndTimeStamp;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.getNextFreePort;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.getUniqueName;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.testUntil;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.withLongRetryPolicy;
import static oracle.weblogic.kubernetes.utils.DomainUtils.createDomainAndVerify;
import static oracle.weblogic.kubernetes.utils.DomainUtils.deleteDomainResource;
import static oracle.weblogic.kubernetes.utils.DomainUtils.removeClusterInDomainResource;
import static oracle.weblogic.kubernetes.utils.DomainUtils.verifyDomainStatusConditionTypeDoesNotExist;
import static oracle.weblogic.kubernetes.utils.FileUtils.createWdtPropertyFile;
import static oracle.weblogic.kubernetes.utils.FmwUtils.getConfiguration;
import static oracle.weblogic.kubernetes.utils.ImageUtils.createBaseRepoSecret;
import static oracle.weblogic.kubernetes.utils.JobUtils.getIntrospectJobName;
import static oracle.weblogic.kubernetes.utils.K8sEvents.ABORTED_ERROR;
import static oracle.weblogic.kubernetes.utils.K8sEvents.CLUSTER_AVAILABLE;
import static oracle.weblogic.kubernetes.utils.K8sEvents.CLUSTER_CHANGED;
import static oracle.weblogic.kubernetes.utils.K8sEvents.CLUSTER_COMPLETED;
import static oracle.weblogic.kubernetes.utils.K8sEvents.CLUSTER_DELETED;
import static oracle.weblogic.kubernetes.utils.K8sEvents.DOMAIN_AVAILABLE;
import static oracle.weblogic.kubernetes.utils.K8sEvents.DOMAIN_CHANGED;
import static oracle.weblogic.kubernetes.utils.K8sEvents.DOMAIN_COMPLETED;
import static oracle.weblogic.kubernetes.utils.K8sEvents.DOMAIN_CREATED;
import static oracle.weblogic.kubernetes.utils.K8sEvents.DOMAIN_DELETED;
import static oracle.weblogic.kubernetes.utils.K8sEvents.DOMAIN_FAILED;
import static oracle.weblogic.kubernetes.utils.K8sEvents.DOMAIN_ROLL_COMPLETED;
import static oracle.weblogic.kubernetes.utils.K8sEvents.DOMAIN_ROLL_STARTING;
import static oracle.weblogic.kubernetes.utils.K8sEvents.POD_CYCLE_STARTING;
import static oracle.weblogic.kubernetes.utils.K8sEvents.REPLICAS_TOO_HIGH_ERROR;
import static oracle.weblogic.kubernetes.utils.K8sEvents.TOPOLOGY_MISMATCH_ERROR;
import static oracle.weblogic.kubernetes.utils.K8sEvents.checkDomainEvent;
import static oracle.weblogic.kubernetes.utils.K8sEvents.checkDomainEventWithCount;
import static oracle.weblogic.kubernetes.utils.K8sEvents.checkDomainFailedEventWithReason;
import static oracle.weblogic.kubernetes.utils.K8sEvents.getDomainEventCount;
import static oracle.weblogic.kubernetes.utils.K8sEvents.getOpGeneratedEventCountForResource;
import static oracle.weblogic.kubernetes.utils.OperatorUtils.installAndVerifyOperator;
import static oracle.weblogic.kubernetes.utils.PatchDomainUtils.patchDomainResource;
import static oracle.weblogic.kubernetes.utils.PersistentVolumeUtils.createPV;
import static oracle.weblogic.kubernetes.utils.PersistentVolumeUtils.createPVC;
import static oracle.weblogic.kubernetes.utils.PodUtils.checkPodDeleted;
import static oracle.weblogic.kubernetes.utils.PodUtils.checkPodDoesNotExist;
import static oracle.weblogic.kubernetes.utils.PodUtils.checkPodExists;
import static oracle.weblogic.kubernetes.utils.PodUtils.checkPodReady;
import static oracle.weblogic.kubernetes.utils.PodUtils.getPodsWithTimeStamps;
import static oracle.weblogic.kubernetes.utils.SecretUtils.createSecretWithUsernamePassword;
import static oracle.weblogic.kubernetes.utils.ThreadSafeLogger.getLogger;
import static oracle.weblogic.kubernetes.utils.WLSTUtils.executeWLSTScript;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests related to Domain events logged by operator.
 * The tests checks for the following events in the domain name space.
 * Created, Changed, Deleted, Completed,
 * Failed.
 * The tests creates the domain resource, modifies it, introduces some validation errors in the domain resource
 * and finally deletes it to generate all the domain related events.
 */
@DisplayName("Verify the Kubernetes events for domain lifecycle")
@Tag("kind-parallel")
@Tag("okd-wls-srg")
@Tag("oke-arm")
@IntegrationTest
@Tag("olcne-srg")
@Tag("oke-sequential")
class ItKubernetesDomainEvents {

  private static String opNamespace = null;
  private static String domainNamespace1 = null;
  private static String domainNamespace2 = null;
  private static String domainNamespace3 = null;
  private static String domainNamespace4 = null;
  private static String domainNamespace5 = null;
  private static String opServiceAccount = null;

  static final String cluster1Name = "mycluster";
  static final String cluster2Name = "cl2";
  static final String adminServerName = "admin-server";
  static final String domainUid = "k8seventsdomain";
  static final String adminServerPodName = domainUid + "-" + adminServerName;
  static final String managedServerNameBase = "managed-server";
  static String managedServerPodNamePrefix = domainUid + "-" + managedServerNameBase;
  static int replicaCount = 2;
  String clusterRes2Name = cluster2Name;
  String clusterRes1Name = cluster1Name;

  static final String pvName1 = getUniqueName(domainUid + "-pv-");
  static final String pvcName1 = getUniqueName(domainUid + "-pvc-");
  static final String pvName2 = getUniqueName(domainUid + "-pv-");
  static final String pvcName2 = getUniqueName(domainUid + "-pvc-");
  static final String pvName3 = getUniqueName(domainUid + "-pv-");
  static final String pvcName3 = getUniqueName(domainUid + "-pvc-");
  static final String pvName4 = getUniqueName(domainUid + "-pv-");
  static final String pvcName4 = getUniqueName(domainUid + "-pvc-");
  static final String pvName5 = getUniqueName(domainUid + "-pv-");
  static final String pvcName5 = getUniqueName(domainUid + "-pvc-");
  private static final String wlSecretName = "weblogic-credentials";

  private static LoggingFacade logger = null;
  private  static String className = new Object(){}.getClass().getEnclosingClass().getSimpleName();

  public enum ScaleAction {
    scaleUp,
    scaleDown,
    reset
  }

  /**
   * Assigns unique namespaces for operator and domains.
   * Pull WebLogic image if running tests in Kind cluster.
   * Installs operator.
   *
   * @param namespaces injected by JUnit
   */
  @BeforeAll
  public static void initAll(@Namespaces(6) List<String> namespaces) {
    logger = getLogger();
    logger.info("Assign a unique namespace for operator");
    assertNotNull(namespaces.get(0), "Namespace is null");
    opNamespace = namespaces.get(0);
    logger.info("Assign a unique namespace for WebLogic domain");
    assertNotNull(namespaces.get(1), "Namespace is null");
    domainNamespace1 = namespaces.get(1);
    assertNotNull(namespaces.get(2), "Namespace is null");
    domainNamespace2 = namespaces.get(2);
    assertNotNull(namespaces.get(3), "Namespace is null");
    domainNamespace3 = namespaces.get(3);
    assertNotNull(namespaces.get(4), "Namespace is null");
    domainNamespace4 = namespaces.get(4);
    assertNotNull(namespaces.get(5), "Namespace is null");
    domainNamespace5 = namespaces.get(5);

    // set the service account name for the operator
    opServiceAccount = opNamespace + "-sa";

    // install and verify operator with REST API
    installAndVerifyOperator(opNamespace, opServiceAccount,
            true, 0, domainNamespace1, domainNamespace2, domainNamespace3,
            domainNamespace4, domainNamespace5);

    createDomain(domainNamespace3, domainUid, pvName3, pvcName3);
  }

  /**
   * Test domain events are logged when domain resource goes through various life cycle stages.
   * Verifies Created is logged when domain resource is created.
   * Verifies Completed is logged when the domain resource reaches its completed state.
   */
  @Test
  @DisplayName("Test domain events for various successful domain life cycle changes")
  void testK8SEventsSuccess() {
    OffsetDateTime timestamp = now();
    logger.info("Creating domain");
    createDomain(domainNamespace1, domainUid, pvName1, pvcName1);

    logger.info("verify the Created event is generated");
    checkEvent(opNamespace, domainNamespace1, domainUid, DOMAIN_CREATED, "Normal", timestamp);
    logger.info("verify the Completed event is generated");
    checkEvent(opNamespace, domainNamespace1, domainUid, DOMAIN_COMPLETED, "Normal", timestamp);
    logger.info("verify the ClusterCompleted event is generated");
    checkEvent(opNamespace, domainNamespace1, domainUid, CLUSTER_COMPLETED, "Normal", timestamp);
    shutdownDomain(domainUid, domainNamespace1);
  }

  /**
   * Patch a domain resource with a new managed server not existing in actual WebLogic domain and verify
   * the warning Failed event is logged by the operator in the domain namespace.
   */
  @Test
  @DisplayName("Test domain Failed event with TopologyMismatch for non-existing managed server")
  void testDomainK8sEventsNonExistingManagedServer() {
    OffsetDateTime timestamp;
    String patchStr;
    V1Patch patch;
    try {
      timestamp = now();
      logger.info("patch the domain resource with non-existing managed server");
      patchStr = "[{\"op\": \"add\",\"path\": \""
        + "/spec/managedServers/-\", \"value\": "
        + "{\"serverName\" : \"nonexisting-ms\", "
        + "\"serverStartPolicy\": \"IfNeeded\"}"
        + "}]";
      logger.info("Updating domain configuration using patch string: {0}\n", patchStr);
      patch = new V1Patch(patchStr);
      assertTrue(patchDomainCustomResource(domainUid, domainNamespace3, patch, V1Patch.PATCH_FORMAT_JSON_PATCH),
          "Failed to patch domain");
      logger.info("verify the Failed event is generated");
      checkFailedEvent(opNamespace, domainNamespace3, domainUid, TOPOLOGY_MISMATCH_ERROR, "Warning", timestamp);
    } finally {
      // remove the managed server from domain resource
      timestamp = now();
      patchStr
          = "[{\"op\": \"remove\",\"path\": \""
          + "/spec/managedServers\""
          + "}]";
      logger.info("Updating domain configuration using patch string: {0}\n", patchStr);
      patch = new V1Patch(patchStr);
      assertTrue(patchDomainCustomResource(domainUid, domainNamespace3, patch, V1Patch.PATCH_FORMAT_JSON_PATCH),
          "Failed to patch domain");

      logger.info("verify the Changed event is generated");
      checkEvent(opNamespace, domainNamespace3, domainUid, DOMAIN_CHANGED, "Normal", timestamp);
    }

  }

  /**
   * Patch a domain resource with a new cluster not existing in actual WebLogic domain and verify
   * the warning Failed event is logged by the operator in the domain namespace.
   */
  @Test
  @DisplayName("Test domain Failed event for non-existing cluster")
  void testDomainK8sEventsNonExistingCluster() {
    String nonExistingClusterName = "nonexisting-cluster";
    OffsetDateTime timestamp = now();
    createClusterAndVerify(createClusterResource(
        nonExistingClusterName, nonExistingClusterName, domainNamespace3, replicaCount));
    logger.info("patch the domain resource with new cluster");
    try {
      String patchStr
          = "["
          + "{\"op\": \"add\",\"path\": \"/spec/clusters/-\", \"value\": "
          + "    {\"name\" : \"" + nonExistingClusterName + "\"}"
          + "}]";
      logger.info("Updating domain configuration using patch string: {0}\n", patchStr);
      V1Patch patch = new V1Patch(patchStr);
      assertTrue(patchDomainCustomResource(domainUid, domainNamespace3, patch, V1Patch.PATCH_FORMAT_JSON_PATCH),
          "Failed to patch domain");
      // verify the Failed event is generated
      checkFailedEvent(opNamespace, domainNamespace3, domainUid, TOPOLOGY_MISMATCH_ERROR, "Warning", timestamp);
    } finally {
      //remove the cluster from domain resource
      timestamp = now();
      assertDoesNotThrow(() -> removeClusterInDomainResource(nonExistingClusterName, domainUid, domainNamespace3));
      // verify the Changed event is generated
      checkEvent(opNamespace, domainNamespace3, domainUid, DOMAIN_CHANGED, "Normal", timestamp);
    }
  }

  /**
   * Test the following domain events are logged when domain resource goes through introspector failure.
   * Patch the domain resource to shutdown servers.
   * Patch the domain resource to point to a bad DOMAIN_HOME and update serverStartPolicy to IfNeeded.
   * Verifies Failed event with Aborted failure reason is logged.
   * Cleanup by patching the domain resource to a valid location and introspectVersion to bring up all servers again.
   */
  @Test
  @DisplayName("Test domain events for failed/retried domain life cycle changes")
  void testK8SEventsFailedLifeCycle() {
    V1Patch patch;
    String patchStr;
    String domainUid5 = domainUid + "5";
    DomainResource domain = createDomain(domainNamespace5, domainUid5, pvName5, pvcName5, "Never",
        spec -> spec.failureRetryLimitMinutes(2L));
    assertNotNull(domain, " Can't create domain resource");

    String originalDomainHome = domain.getSpec().getDomainHome();

    OffsetDateTime timestamp = now();

    logger.info("Checking if the admin server {0} is shutdown in namespace {1}",
        adminServerPodName, domainNamespace5);
    checkPodDoesNotExist(adminServerPodName, domainUid5, domainNamespace5);

    for (int i = 1; i <= replicaCount; i++) {
      logger.info("Checking if the managed server {0} is shutdown in namespace {1}",
          managedServerPodNamePrefix + i, domainNamespace5);
      checkPodDoesNotExist(managedServerPodNamePrefix + i, domainUid5, domainNamespace5);
    }

    logger.info("Replace the domainHome to a nonexisting location to verify the following events"
        + " Changed and Failed events are logged");
    patchStr = "[{\"op\": \"replace\", "
        + "\"path\": \"/spec/domainHome\", \"value\": \"" + originalDomainHome + "bad\"},"
        + "{\"op\": \"replace\", \"path\": \"/spec/serverStartPolicy\", \"value\": \"IfNeeded\"},"
        + "{\"op\": \"remove\", \"path\": \"/spec/configuration/initializeDomainOnPV\"}]";
    logger.info("PatchStr for domainHome: {0}", patchStr);

    patch = new V1Patch(patchStr);
    assertTrue(patchDomainCustomResource(domainUid5, domainNamespace5, patch, V1Patch.PATCH_FORMAT_JSON_PATCH),
        "patchDomainCustomResource failed");

    logger.info("verify domain changed event is logged");
    checkEvent(opNamespace, domainNamespace5, domainUid5, DOMAIN_CHANGED, "Normal", timestamp);
    logger.info("verify domain failed event");
    checkFailedEvent(opNamespace, domainNamespace5, domainUid5, ABORTED_ERROR, "Warning", timestamp);

    shutdownDomain(domainUid5, domainNamespace5);
  }

  /**
   * Test verifies there is only 1 Completed event is logged
   * regardless of how many clusters exists in the domain.
   * Test creates a new cluster in the WebLogic domain, patches the domain resource to add the new cluster
   * and starts up the new cluster.
   * Verifies the scaling operation generates only one Completed.
   */
  @Test
  void testK8SEventsMultiClusterEvents() {
    OffsetDateTime timestamp = now();
    createNewCluster();
    OffsetDateTime timestamp2 = now();
    logger.info("Scale the newly-added cluster");
    scaleCluster(clusterRes2Name, domainNamespace3, 1);
    logger.info("verify the Domain_Available event is generated");
    checkEvent(opNamespace, domainNamespace3, domainUid,
            DOMAIN_AVAILABLE, "Normal", timestamp);
    logger.info("verify the Cluster_Available event is generated");
    checkEvent(opNamespace, domainNamespace3, domainUid,
        CLUSTER_CHANGED, "Normal", timestamp);
    assertEquals(1, getOpGeneratedEventCountForResource(domainNamespace3, domainUid, cluster2Name,
        CLUSTER_CHANGED, timestamp));
    checkEvent(opNamespace, domainNamespace3, domainUid,
        CLUSTER_AVAILABLE, "Normal", timestamp);
    logger.info("verify the Completed event is generated");
    checkEvent(opNamespace, domainNamespace3,
            domainUid, DOMAIN_COMPLETED, "Normal", timestamp);
    logger.info("verify the ClusterCompleted event is generated");
    checkEvent(opNamespace, domainNamespace3,
        domainUid, CLUSTER_COMPLETED, "Normal", timestamp2);
    logger.info("verify the only 1 Completed event for domain is generated");
    assertEquals(1, getOpGeneratedEventCountForResource(domainNamespace3, domainUid, domainUid,
            DOMAIN_COMPLETED, timestamp));
    logger.info("verify the only 1 ClusterCompleted event for domain is generated");
    assertEquals(1, getOpGeneratedEventCountForResource(domainNamespace3, domainUid, cluster2Name,
        CLUSTER_COMPLETED, timestamp2));
  }

  /**
   * Scale the cluster beyond maximum dynamic cluster size and verify the patch operation failed.
   */
  @Test
  void testDomainK8sEventsScalePastMaxWithoutChangingIntrospectVersion() {
    OffsetDateTime timestamp = now();
    logger.info("Scaling cluster using patching");
    assertFalse(scaleCluster(cluster1Name, domainNamespace3, 3),
            "Patching cluster with a replica count that exceeds the cluster size did not fail as expected");
  }

  /**
   * Scale the cluster beyond maximum dynamic cluster size and change the introspectVersion as well, verify the
   * Failed warning event is generated.
   */
  @Test
  void testDomainK8sEventsScalePastMaxAndChangeIntrospectVersion() {
    OffsetDateTime timestamp = now();
    try {
      removeReplicasSettingAndVerify(domainUid, cluster1Name, domainNamespace3, replicaCount - 1,
          managedServerPodNamePrefix);
      checkPodDoesNotExist(managedServerPodNamePrefix + 2, domainUid, domainNamespace3);

      String introspectVersion = assertDoesNotThrow(() -> getNextIntrospectVersion(domainUid, domainNamespace3));
      String patchStr
          = "["
          + "{\"op\": \"replace\", \"path\": \"/spec/introspectVersion\", \"value\": \"" + introspectVersion + "\"},"
          + "{\"op\": \"replace\", \"path\": \"/spec/replicas\", \"value\": 3}"
          + "]";

      logger.info("Updating introspect version  using patch string: {0}",  patchStr);
      assertTrue(patchDomainCustomResource(domainUid, domainNamespace3, new V1Patch(patchStr),
          V1Patch.PATCH_FORMAT_JSON_PATCH), "Patch domain did not fail as expected");

      logger.info("verify the Failed event is generated");
      checkFailedEvent(opNamespace, domainNamespace3, domainUid, REPLICAS_TOO_HIGH_ERROR, "Warning", timestamp);
    } finally {
      timestamp = now();
      logger.info("Updating domain resource to set correct replicas size");

      assertTrue(scaleCluster(clusterRes1Name, domainNamespace3, 2), "failed to scale cluster via patching");
      logger.info("verify the ClusterChanged event is generated");
      checkEvent(opNamespace, domainNamespace3, null, CLUSTER_CHANGED, "Normal", timestamp);
      checkPodReadyAndServiceExists(adminServerPodName, domainUid, domainNamespace3);

      for (int i = 1; i <= replicaCount; i++) {
        logger.info("Checking managed server service {0} is created in namespace {1}",
                managedServerPodNamePrefix + i, domainNamespace3);
        checkPodReadyAndServiceExists(managedServerPodNamePrefix + i, domainUid, domainNamespace3);
      }
      logger.info("verify the Completed event is generated");
      checkEvent(opNamespace, domainNamespace3, domainUid, DOMAIN_COMPLETED, "Normal", timestamp);

    }
  }

  /**
   * Scale down and scale up the domain and verify that
   * Completed normal event is generated.
   */
  @Test
  @DisplayName("Test domain completed event when domain is scaled.")
  void testScaleDomainAndVerifyCompletedEvent() {
    createDomain(domainNamespace4, domainUid, pvName4, pvcName4);
    scaleDomainAndVerifyCompletedEvent(1, ScaleAction.scaleDown, true, domainNamespace4);
    scaleDomainAndVerifyCompletedEvent(2, ScaleAction.scaleUp, true, domainNamespace4);
    deleteDomainResource(domainNamespace4, domainUid);
  }

  /**
   * Replace the pv and pvc in the domain resource with a pv/pvc not containing any WebLogic domain
   * and verify the Failed warning event is generated.
   */
  @Test
  void testK8sEventsFailed() {
    OffsetDateTime timestamp = now();
    try {
      String pvName = getUniqueName("sample-pv-");
      String pvcName = getUniqueName("sample-pvc-");
      createPV(pvName, domainUid, this.getClass().getSimpleName());
      createPVC(pvName, pvcName, domainUid, domainNamespace3);
      String introspectVersion = assertDoesNotThrow(() -> getNextIntrospectVersion(domainUid, domainNamespace3));
      String patchStr
          = "["
          + "{\"op\": \"replace\", \"path\": \"/spec/serverPod/volumeMounts/0/name\", \"value\": \"sample-pv\"},"
          + "{\"op\": \"replace\", \"path\": \"/spec/serverPod/volumes/0/name\", \"value\": \"sample-pv\"},"
          + "{\"op\": \"replace\", \"path\": "
          + "\"/spec/serverPod/volumes/0/persistentVolumeClaim/claimName\", \"value\": \"sample-pvc\"},"
          + "{\"op\": \"add\", \"path\": \"/spec/introspectVersion\", \"value\": \"" + introspectVersion + "\"},"
          + "{\"op\": \"remove\", \"path\": \"/spec/configuration/initializeDomainOnPV\"}]";
      logger.info("Updating pv/pvcs in domain resource using patch string: {0}", patchStr);
      V1Patch patch = new V1Patch(patchStr);
      assertTrue(patchDomainCustomResource(domainUid, domainNamespace3, patch, V1Patch.PATCH_FORMAT_JSON_PATCH),
          "Failed to patch domain");
      
      logger.info("verify the Failed event is generated");
      checkEvent(opNamespace, domainNamespace3, domainUid, DOMAIN_FAILED, "Warning", timestamp);
    } finally {
      String introspectVersion = assertDoesNotThrow(() -> getNextIntrospectVersion(domainUid, domainNamespace3));
      String patchStr
          = "["
          + "{\"op\": \"replace\", \"path\": \"/spec/serverPod/volumeMounts/0/name\", \"value\": \"" + pvName3 + "\"},"
          + "{\"op\": \"replace\", \"path\": \"/spec/serverPod/volumes/0/name\", \"value\": \"" + pvName3 + "\"},"
          + "{\"op\": \"replace\", \"path\": "
          + "\"/spec/serverPod/volumes/0/persistentVolumeClaim/claimName\", \"value\": \"" + pvcName3 + "\"},"
          + "{\"op\": \"add\", \"path\": \"/spec/introspectVersion\", \"value\": \"" + introspectVersion + "\"}"
          + "]";
      logger.info("Updating pv/pvcs in domain resource using patch string: {0}", patchStr);
      V1Patch patch = new V1Patch(patchStr);
      timestamp = now();
      assertTrue(patchDomainCustomResource(domainUid, domainNamespace3, patch, V1Patch.PATCH_FORMAT_JSON_PATCH),
          "Failed to patch domain");

      logger.info("verify domain changed and completed events are logged");
      checkEvent(opNamespace, domainNamespace3, domainUid, DOMAIN_CHANGED, "Normal", timestamp);
      checkEvent(opNamespace, domainNamespace3, domainUid, DOMAIN_COMPLETED, "Normal", timestamp);
    }
  }

  /**
   * The test modifies the logHome property and verifies the domain roll events are logged.
   */
  @Test
  @DisplayName("Verify logHome property change rolls domain and relevant events are logged")
  void testLogHomeChangeEvents() {

    OffsetDateTime timestamp = now();

    // get the original domain resource before update
    DomainResource domain1 = DomainUtils.getAndValidateInitialDomain(domainNamespace3, domainUid);

    // get the map with server pods and their original creation timestamps
    Map<String, OffsetDateTime> podsWithTimeStamps = getPodsWithTimeStamps(domainNamespace3,
        adminServerPodName, managedServerPodNamePrefix, replicaCount);

    String newLogHome = "/shared/" + domainNamespace3 + "/domains/logHome/" + domainUid;
    //print out the original image name
    String logHome = domain1.getSpec().getLogHome();
    logger.info("Changing the current log home used by the domain : {0} to {1}", logHome, newLogHome);

    //change logHome from /shared/logs to /shared/logs/logHome
    String patchStr = "["
        + "{\"op\": \"replace\", \"path\": \"/spec/logHome\", \"value\": \"" + newLogHome + "\"}"
        + "]";
    logger.info("PatchStr for logHome: {0}", patchStr);

    assertTrue(patchDomainResource(domainUid, domainNamespace3, new StringBuffer(patchStr)),
        "patchDomainCustomResource(logHome) failed");

    domain1 = assertDoesNotThrow(() -> getDomainCustomResource(domainUid, domainNamespace3),
        String.format("getDomainCustomResource failed with ApiException when tried to get domain %s in namespace %s",
            domainUid, domainNamespace3));

    //print out logHome in the new patched domain
    logger.info("In the new patched domain logHome is: {0}", domain1.getSpec().getLogHome());
    assertEquals(newLogHome, domain1.getSpec().getLogHome(), "logHome is not updated");

    // verify the server pods are rolling restarted and back to ready state
    logger.info("Verifying rolling restart occurred for domain {0} in namespace {1}",
        domainUid, domainNamespace3);
    assertTrue(verifyRollingRestartOccurred(podsWithTimeStamps, 1, domainNamespace3),
        String.format("Rolling restart failed for domain %s in namespace %s", domainUid, domainNamespace3));

    checkPodReadyAndServiceExists(adminServerPodName, domainUid, domainNamespace3);

    for (int i = 1; i <= replicaCount; i++) {
      logger.info("Checking managed server service {0} is created in namespace {1}",
          managedServerPodNamePrefix + i, domainNamespace3);
      checkPodReadyAndServiceExists(managedServerPodNamePrefix + i, domainUid, domainNamespace3);
    }

    //verify the logHome change causes the domain roll events to be logged
    verifyDomainRollAndPodCycleEvents(timestamp, domainNamespace3);
  }

  private void verifyDomainRollAndPodCycleEvents(OffsetDateTime timestamp, String domainNamespace) {
    logger.info("verify domain roll starting/pod cycle starting events are logged");
    checkEvent(opNamespace, domainNamespace, domainUid, DOMAIN_ROLL_STARTING, "Normal", timestamp);
    checkEvent(opNamespace, domainNamespace, domainUid, POD_CYCLE_STARTING, "Normal", timestamp);
    checkEvent(opNamespace, domainNamespace, domainUid, DOMAIN_ROLL_COMPLETED, "Normal", timestamp);
    // verify that Rolling condition is removed
    testUntil(
        () -> verifyDomainStatusConditionTypeDoesNotExist(
            domainUid, domainNamespace, DOMAIN_STATUS_CONDITION_ROLLING_TYPE),
        logger,
        "Verifying domain {0} in namespace {1} no longer has a Rolling status condition",
        domainUid,
        domainNamespace);
  }


  /**
   * The test modifies the includeServerOutInPodLog property and verifies the domain roll starting events are logged.
   */
  @Test
  @DisplayName("Verify includeServerOutInPodLog property change rolls domain and relevant events are logged")
  void testIncludeServerOutInPodLog() {

    OffsetDateTime timestamp = now();

    // get the original domain resource before update
    DomainResource domain1 = DomainUtils.getAndValidateInitialDomain(domainNamespace3, domainUid);

    // get the map with server pods and their original creation timestamps
    Map<String, OffsetDateTime> podsWithTimeStamps = getPodsWithTimeStamps(domainNamespace3,
        adminServerPodName, managedServerPodNamePrefix, replicaCount);

    //print out the original includeServerOutInPodLog value
    boolean includeLogInPod = domain1.getSpec().includeServerOutInPodLog();
    logger.info("Currently the includeServerOutInPodLog used for the domain is: {0}", includeLogInPod);

    //change includeServerOutInPodLog
    String patchStr = "["
        + "{\"op\": \"replace\", \"path\": \"/spec/includeServerOutInPodLog\", "
        + "\"value\": " + Boolean.toString(!includeLogInPod) + "}"
        + "]";
    logger.info("PatchStr for includeServerOutInPodLog: {0}", patchStr);

    assertTrue(patchDomainResource(domainUid, domainNamespace3, new StringBuffer(patchStr)),
        "patchDomainCustomResource(includeServerOutInPodLog) failed");

    domain1 = assertDoesNotThrow(() -> getDomainCustomResource(domainUid, domainNamespace3),
        String.format("getDomainCustomResource failed with ApiException when tried to get domain %s in namespace %s",
            domainUid, domainNamespace3));

    //print out includeServerOutInPodLog in the new patched domain
    logger.info("In the new patched domain includeServerOutInPodLog is: {0}",
        domain1.getSpec().includeServerOutInPodLog());
    assertNotEquals(includeLogInPod, domain1.getSpec().includeServerOutInPodLog(),
        "includeServerOutInPodLog is not updated");

    // verify the server pods are rolling restarted and back to ready state
    logger.info("Verifying rolling restart occurred for domain {0} in namespace {1}",
        domainUid, domainNamespace3);
    assertTrue(verifyRollingRestartOccurred(podsWithTimeStamps, 1, domainNamespace3),
        String.format("Rolling restart failed for domain %s in namespace %s", domainUid, domainNamespace3));

    checkPodReadyAndServiceExists(adminServerPodName, domainUid, domainNamespace3);

    for (int i = 1; i <= replicaCount; i++) {
      logger.info("Checking managed server service {0} is created in namespace {1}",
          managedServerPodNamePrefix + i, domainNamespace3);
      checkPodReadyAndServiceExists(managedServerPodNamePrefix + i, domainUid, domainNamespace3);
    }

    //verify the includeServerOutInPodLog change causes the domain roll events to be logged
    verifyDomainRollAndPodCycleEvents(timestamp, domainNamespace3);
  }

  /**
   * Test DomainDeleted and ClusterDeleted events are logged when domain and cluster resources are deleted.
   */
  @Test
  @DisplayName("Test domain and cluster events for deleting domain and cluster resources")
  void testK8SEventsDelete() {
    OffsetDateTime timestamp = now();
    createDomain(domainNamespace2, domainUid, pvName2, pvcName2);
    deleteDomainCustomResource(domainUid, domainNamespace2);
    deleteClusterCustomResource(cluster1Name, domainNamespace2);

    checkPodDoesNotExist(adminServerPodName, domainUid, domainNamespace2);
    checkPodDoesNotExist(managedServerPodNamePrefix + 1, domainUid, domainNamespace2);
    checkPodDoesNotExist(managedServerPodNamePrefix + 2, domainUid, domainNamespace2);

    //verify domain deleted event
    checkEvent(opNamespace, domainNamespace2, domainUid, DOMAIN_DELETED, "Normal", timestamp);
    //verify cluster deleted event
    checkEvent(opNamespace, domainNamespace2, null, CLUSTER_DELETED, "Normal", timestamp);
  }

  /**
   * Cleanup the persistent volume and persistent volume claim used by the test.
   */
  @AfterAll
  public static void tearDown() {
    if (!SKIP_CLEANUP) {
      deletePersistentVolumeClaim(domainNamespace3, "sample-pvc");
      deletePersistentVolume("sample-pv");
      shutdownDomain(domainUid, domainNamespace3);
    }
  }

  // Utility method to check event
  private static void checkEvent(
      String opNamespace, String domainNamespace, String domainUid,
      String reason, String type, OffsetDateTime timestamp) {
    testUntil(withLongRetryPolicy,
        checkDomainEvent(opNamespace, domainNamespace, domainUid, reason, type, timestamp),
        logger,
        "domain event {0} to be logged in namespace {1}",
        reason,
        domainNamespace);
  }

  private static void checkFailedEvent(
      String opNamespace, String domainNamespace, String domainUid,
      String failureReason, String type, OffsetDateTime timestamp) {
    testUntil(withLongRetryPolicy,
        checkDomainFailedEventWithReason(opNamespace, domainNamespace, domainUid, failureReason, type, timestamp),
        logger,
        "domain event {0} to be logged in namespace {1}",
        failureReason,
        domainNamespace);
  }

  private static void checkEventWithCount(
      String opNamespace, String domainNamespace, String domainUid,
      String reason, String type, OffsetDateTime timestamp, int countBefore) {
    testUntil(withLongRetryPolicy,
        checkDomainEventWithCount(
            opNamespace, domainNamespace, domainUid, reason, type, timestamp, countBefore),
        logger,
        "domain event {0} to be logged",
        reason);
  }

  // Create and start a WebLogic domain in PV
  private  static void createDomain(String domainNamespace, String domainUid, String pvName, String pvcName) {

    assertDoesNotThrow(() -> createDomain(domainNamespace, domainUid, pvName, pvcName,
            "IfNeeded"),
            "Failed to create domain custom resource");

    // verify the admin server service created
    checkPodReadyAndServiceExists(adminServerPodName, domainUid, domainNamespace);

    // verify managed server services created
    for (int i = 1; i <= replicaCount; i++) {
      logger.info("Checking managed server service/pod {0} is created in namespace {1}",
          managedServerPodNamePrefix + i, domainNamespace);
      checkPodReadyAndServiceExists(managedServerPodNamePrefix + i, domainUid, domainNamespace);
    }
  }

  // Create and start a WebLogic domain in PV
  private static DomainResource createDomain(String domainNamespace, String domainUid,
                                             String pvName, String pvcName, String serverStartupPolicy) {
    return createDomain(domainNamespace, domainUid, pvName, pvcName, serverStartupPolicy, UnaryOperator.identity());
  }

  // Create and start a WebLogic domain in PV
  private static DomainResource createDomain(String domainNamespace, String domainUid,
      String pvName, String pvcName, String serverStartupPolicy,
      UnaryOperator<DomainSpec> domainSpecUnaryOperator) {
    String uniqueDomainHome = "/shared/" + domainNamespace + "/domains/";

    // create pull secrets for WebLogic image when running in non Kind Kubernetes cluster
    // this secret is used only for non-kind cluster
    createBaseRepoSecret(domainNamespace);

    // create WebLogic domain credential secret
    createSecretWithUsernamePassword(wlSecretName, domainNamespace,
        ADMIN_USERNAME_DEFAULT, ADMIN_PASSWORD_DEFAULT);
    final String wlsModelFilePrefix = "model-dci-introspect";
    final String wlsModelFile = wlsModelFilePrefix + ".yaml";
    int t3ChannelPort = getNextFreePort();
    File wlsModelPropFile = createWdtPropertyFile(wlsModelFilePrefix,
        K8S_NODEPORT_HOST, t3ChannelPort);

    // create domainCreationImage
    String domainCreationImageName = DOMAIN_IMAGES_PREFIX + "wls-domain-on-pv-image";
    String domainCreationImagetag = getDateAndTimeStamp();
    // create image with model and wdt installation files
    WitParams witParams
        = new WitParams()
            .modelImageName(domainCreationImageName)
            .modelImageTag(domainCreationImagetag)
            .modelFiles(Collections.singletonList(MODEL_DIR + "/" + wlsModelFile))
            .modelVariableFiles(Collections.singletonList(wlsModelPropFile.getAbsolutePath()));
    createAndPushAuxiliaryImage(domainCreationImageName, domainCreationImagetag, witParams);

    DomainCreationImage domainCreationImage
        = new DomainCreationImage().image(domainCreationImageName + ":" + domainCreationImagetag);

    // create a domain resource
    logger.info("Creating domain custom resource");
    Map<String, Quantity> pvCapacity = new HashMap<>();
    pvCapacity.put("storage", new Quantity("2Gi"));

    Map<String, Quantity> pvcRequest = new HashMap<>();
    pvcRequest.put("storage", new Quantity("2Gi"));
    Configuration configuration = null;
    final String storageClassName = "weblogic-domain-storage-class";
    if (OKE_CLUSTER) {
      configuration = getConfiguration(pvcName, pvcRequest, "oci-fss");
    } else {
      configuration = getConfiguration(pvName, pvcName, pvCapacity, pvcRequest, storageClassName,
          ItKubernetesDomainEvents.class.getSimpleName());
    }
    configuration.getInitializeDomainOnPV().domain(new DomainOnPV()
        .createMode(CreateIfNotExists.DOMAIN)
        .domainCreationImages(Collections.singletonList(domainCreationImage))
        .domainType(DomainOnPVType.WLS));
    configuration.overrideDistributionStrategy("Dynamic");

    // create secrets
    List<V1LocalObjectReference> secrets = new ArrayList<>();
    for (String secret : new String[]{wlSecretName, BASE_IMAGES_REPO_SECRET_NAME}) {
      secrets.add(new V1LocalObjectReference().name(secret));
    }

    // create a domain custom resource configuration object
    DomainResource domain = new DomainResource()
        .apiVersion(DOMAIN_API_VERSION)
        .kind("Domain")
        .metadata(new V1ObjectMeta()
            .name(domainUid)
            .namespace(domainNamespace))
        .spec(domainSpecUnaryOperator.apply(new DomainSpec()
            .domainUid(domainUid)
            .domainHome(uniqueDomainHome + domainUid)
            .domainHomeSourceType("PersistentVolume")
            .image(WEBLOGIC_IMAGE_TO_USE_IN_SPEC)
            .imagePullPolicy(IMAGE_PULL_POLICY)
            .webLogicCredentialsSecret(new V1LocalObjectReference()
                .name(wlSecretName))
            .includeServerOutInPodLog(true)
            .logHomeEnabled(Boolean.TRUE)
            .logHome("/shared/" + domainNamespace + "/logs/" + domainUid)
            .dataHome("")
            .serverStartPolicy(serverStartupPolicy)
            .failureRetryIntervalSeconds(FAILURE_RETRY_INTERVAL_SECONDS)
            .failureRetryLimitMinutes(FAILURE_RETRY_LIMIT_MINUTES)
            .serverPod(new ServerPod() //serverpod
                .addEnvItem(new V1EnvVar()
                    .name("JAVA_OPTIONS")
                    .value("-Dweblogic.StdoutDebugEnabled=false"))
                .addEnvItem(new V1EnvVar()
                    .name("USER_MEM_ARGS")
                    .value("-Djava.security.egd=file:/dev/./urandom"))
                .addVolumesItem(new V1Volume()
                    .name(pvName)
                    .persistentVolumeClaim(new V1PersistentVolumeClaimVolumeSource()
                        .claimName(pvcName)))
                .addVolumeMountsItem(new V1VolumeMount()
                    .mountPath("/shared")
                    .name(pvName)))
            .adminServer(new AdminServer() //admin server
                .adminService(new AdminService()))
            .configuration(configuration)));
    domain.spec().setImagePullSecrets(secrets);

    // create cluster resource for the domain    
    if (!Cluster.doesClusterExist(cluster1Name, CLUSTER_VERSION, domainNamespace)) {
      ClusterResource cluster = createClusterResource(cluster1Name,
          cluster1Name, domainNamespace, replicaCount);
      createClusterAndVerify(cluster);
    }
    domain.getSpec().withCluster(new V1LocalObjectReference().name(cluster1Name));

    createDomainAndVerify(domain, domainNamespace);
    return domain;
  }

  private void createNewCluster() {
    final String managedServerNameBase = "cl2-ms-";
    String managedServerPodNamePrefix = domainUid + "-" + managedServerNameBase;

    logger.info("Getting port for default channel");
    int adminServerPort
        = getServicePort(domainNamespace3, adminServerPodName, "default");

    // create a temporary WebLogic WLST property file
    File wlstPropertiesFile =
        assertDoesNotThrow(() -> File.createTempFile("wlst", ".properties", new File(RESULTS_TEMPFILE)),
        "Creating WLST properties file failed");
    Properties p = new Properties();
    p.setProperty("admin_host", adminServerPodName);
    p.setProperty("admin_port", Integer.toString(adminServerPort));
    p.setProperty("admin_username", ADMIN_USERNAME_DEFAULT);
    p.setProperty("admin_password", ADMIN_PASSWORD_DEFAULT);
    p.setProperty("test_name", "create_cluster");
    p.setProperty("cluster_name", cluster2Name);
    p.setProperty("server_prefix", managedServerNameBase);
    p.setProperty("server_count", "3");
    assertDoesNotThrow(() -> p.store(new FileOutputStream(wlstPropertiesFile), "wlst properties file"),
        "Failed to write the WLST properties to file");

    // changet the admin server port to a different value to force pod restart
    Path configScript = Paths.get(RESOURCE_DIR, "python-scripts", "introspect_version_script.py");
    executeWLSTScript(configScript, wlstPropertiesFile.toPath(), domainNamespace3);

    ClusterList clusters = listClusterCustomResources(domainNamespace3);

    if (clusters.getItems().stream().anyMatch(cluster -> cluster.getClusterName().equals(cluster2Name))) {
      getLogger().info("!!!Cluster {0} in namespace {1} already exists, skipping...", cluster2Name, domainNamespace3);
    } else {
      getLogger().info("Creating cluster {0} in namespace {1}", cluster2Name, domainNamespace3);
      createClusterAndVerify(createClusterResource(
           cluster2Name, cluster2Name, domainNamespace3, replicaCount));
    }

    String introspectVersion = assertDoesNotThrow(() -> getNextIntrospectVersion(domainUid, domainNamespace3));

    logger.info("patch the domain resource with new cluster and introspectVersion");
    String patchStr
        = "["
        + "{\"op\": \"add\",\"path\": \"/spec/clusters/-\", \"value\": "
        + "    {\"name\" : \"" + cluster2Name + "\"}"
        + "},"
        + "{\"op\": \"replace\", \"path\": \"/spec/introspectVersion\", \"value\": \"" + introspectVersion + "\"}"
        + "]";
    logger.info("Updating domain configuration using patch string: {0}\n", patchStr);
    V1Patch patch = new V1Patch(patchStr);
    assertTrue(patchDomainCustomResource(domainUid, domainNamespace3, patch, V1Patch.PATCH_FORMAT_JSON_PATCH),
        "Failed to patch domain");

    //verify the introspector pod is created and runs
    String introspectPodNameBase = getIntrospectJobName(domainUid);

    checkPodExists(introspectPodNameBase, domainUid, domainNamespace3);
    checkPodDoesNotExist(introspectPodNameBase, domainUid, domainNamespace3);

    // verify new cluster managed server services created
    for (int i = 1; i <= replicaCount; i++) {
      logger.info("Checking managed server service {0} is created in namespace {1}",
          managedServerPodNamePrefix + i, domainNamespace3);
      checkServiceExists(managedServerPodNamePrefix + i, domainNamespace3);
    }

    // verify new cluster managed server pods are ready
    for (int i = 1; i <= replicaCount; i++) {
      logger.info("Waiting for managed server pod {0} to be ready in namespace {1}",
          managedServerPodNamePrefix + i, domainNamespace3);
      checkPodReady(managedServerPodNamePrefix + i, domainUid, domainNamespace3);
    }
  }

  private void scaleDomainAndVerifyCompletedEvent(int replicaCount, ScaleAction testType,
                                                  boolean verify, String namespace) {
    OffsetDateTime timestamp = now();
    logger.info("Updating domain resource to set the replicas for cluster " + cluster1Name + " to " + replicaCount);
    int countBefore = getDomainEventCount(namespace, domainUid, DOMAIN_COMPLETED, "Normal");
    assertTrue(scaleCluster(cluster1Name, namespace, replicaCount), "failed to scale domain to " + replicaCount);
    int serverNumber = replicaCount + 1;

    switch (testType) {
      case scaleUp:
        checkPodReady(managedServerPodNamePrefix + replicaCount, domainUid, namespace);
        break;
      case scaleDown:
        checkPodDeleted(managedServerPodNamePrefix + serverNumber, domainUid, namespace);
        break;
      case reset:
        checkPodReady(managedServerPodNamePrefix + replicaCount, domainUid, namespace);
        checkPodDeleted(managedServerPodNamePrefix + serverNumber, domainUid, namespace);
        break;
      default:
    }

    if (verify) {
      logger.info("Verify the Completed event is generated after " + testType);
      checkEventWithCount(
          opNamespace, namespace, domainUid, DOMAIN_COMPLETED, "Normal", timestamp, countBefore);
    }
  }
  
}
