// Copyright (c) 2020, 2022, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.weblogic.kubernetes;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import io.kubernetes.client.custom.V1Patch;
import io.kubernetes.client.openapi.ApiException;
import io.kubernetes.client.openapi.models.CoreV1Event;
import io.kubernetes.client.openapi.models.V1Container;
import io.kubernetes.client.openapi.models.V1EnvVar;
import io.kubernetes.client.openapi.models.V1LocalObjectReference;
import io.kubernetes.client.openapi.models.V1ObjectMeta;
import io.kubernetes.client.openapi.models.V1PersistentVolumeClaimVolumeSource;
import io.kubernetes.client.openapi.models.V1SecretReference;
import io.kubernetes.client.openapi.models.V1Volume;
import io.kubernetes.client.openapi.models.V1VolumeMount;
import io.kubernetes.client.util.Yaml;
import oracle.weblogic.domain.AdminServer;
import oracle.weblogic.domain.AdminService;
import oracle.weblogic.domain.Channel;
import oracle.weblogic.domain.Cluster;
import oracle.weblogic.domain.Domain;
import oracle.weblogic.domain.DomainSpec;
import oracle.weblogic.domain.ServerPod;
import oracle.weblogic.kubernetes.actions.impl.OperatorParams;
import oracle.weblogic.kubernetes.actions.impl.primitive.Command;
import oracle.weblogic.kubernetes.actions.impl.primitive.CommandParams;
import oracle.weblogic.kubernetes.annotations.IntegrationTest;
import oracle.weblogic.kubernetes.annotations.Namespaces;
import oracle.weblogic.kubernetes.logging.LoggingFacade;
import org.awaitility.core.ConditionFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static java.util.concurrent.TimeUnit.MINUTES;
import static java.util.concurrent.TimeUnit.SECONDS;
import static oracle.weblogic.kubernetes.TestConstants.ADMIN_PASSWORD_DEFAULT;
import static oracle.weblogic.kubernetes.TestConstants.ADMIN_USERNAME_DEFAULT;
import static oracle.weblogic.kubernetes.TestConstants.BASE_IMAGES_REPO_SECRET;
import static oracle.weblogic.kubernetes.TestConstants.DOMAIN_API_VERSION;
import static oracle.weblogic.kubernetes.TestConstants.K8S_NODEPORT_HOST;
import static oracle.weblogic.kubernetes.TestConstants.WEBLOGIC_IMAGE_TO_USE_IN_SPEC;
import static oracle.weblogic.kubernetes.actions.ActionConstants.RESOURCE_DIR;
import static oracle.weblogic.kubernetes.actions.TestActions.createNamespace;
import static oracle.weblogic.kubernetes.actions.TestActions.deleteDomainCustomResource;
import static oracle.weblogic.kubernetes.actions.TestActions.deletePersistentVolume;
import static oracle.weblogic.kubernetes.actions.TestActions.deletePersistentVolumeClaim;
import static oracle.weblogic.kubernetes.actions.TestActions.getDomainCustomResource;
import static oracle.weblogic.kubernetes.actions.TestActions.getNextIntrospectVersion;
import static oracle.weblogic.kubernetes.actions.TestActions.getServiceNodePort;
import static oracle.weblogic.kubernetes.actions.TestActions.getServicePort;
import static oracle.weblogic.kubernetes.actions.TestActions.now;
import static oracle.weblogic.kubernetes.actions.TestActions.scaleClusterWithRestApi;
import static oracle.weblogic.kubernetes.actions.impl.Domain.patchDomainCustomResource;
import static oracle.weblogic.kubernetes.assertions.TestAssertions.verifyRollingRestartOccurred;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.checkPodReadyAndServiceExists;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.checkServiceExists;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.testUntil;
import static oracle.weblogic.kubernetes.utils.ConfigMapUtils.createConfigMapForDomainCreation;
import static oracle.weblogic.kubernetes.utils.DomainUtils.createDomainAndVerify;
import static oracle.weblogic.kubernetes.utils.ImageUtils.createSecretForBaseImages;
import static oracle.weblogic.kubernetes.utils.JobUtils.createDomainJob;
import static oracle.weblogic.kubernetes.utils.JobUtils.getIntrospectJobName;
import static oracle.weblogic.kubernetes.utils.K8sEvents.ABORTED_ERROR;
import static oracle.weblogic.kubernetes.utils.K8sEvents.DOMAIN_AVAILABLE;
import static oracle.weblogic.kubernetes.utils.K8sEvents.DOMAIN_CHANGED;
import static oracle.weblogic.kubernetes.utils.K8sEvents.DOMAIN_COMPLETED;
import static oracle.weblogic.kubernetes.utils.K8sEvents.DOMAIN_CREATED;
import static oracle.weblogic.kubernetes.utils.K8sEvents.DOMAIN_DELETED;
import static oracle.weblogic.kubernetes.utils.K8sEvents.DOMAIN_FAILED;
import static oracle.weblogic.kubernetes.utils.K8sEvents.DOMAIN_ROLL_COMPLETED;
import static oracle.weblogic.kubernetes.utils.K8sEvents.DOMAIN_ROLL_STARTING;
import static oracle.weblogic.kubernetes.utils.K8sEvents.NAMESPACE_WATCHING_STARTED;
import static oracle.weblogic.kubernetes.utils.K8sEvents.POD_CYCLE_STARTING;
import static oracle.weblogic.kubernetes.utils.K8sEvents.REPLICAS_TOO_HIGH_ERROR;
import static oracle.weblogic.kubernetes.utils.K8sEvents.TOPOLOGY_MISMATCH_ERROR;
import static oracle.weblogic.kubernetes.utils.K8sEvents.checkDomainEvent;
import static oracle.weblogic.kubernetes.utils.K8sEvents.checkDomainEventWatchingStopped;
import static oracle.weblogic.kubernetes.utils.K8sEvents.checkDomainEventWithCount;
import static oracle.weblogic.kubernetes.utils.K8sEvents.checkDomainFailedEventWithReason;
import static oracle.weblogic.kubernetes.utils.K8sEvents.domainEventExists;
import static oracle.weblogic.kubernetes.utils.K8sEvents.getDomainEventCount;
import static oracle.weblogic.kubernetes.utils.K8sEvents.getOpGeneratedEvent;
import static oracle.weblogic.kubernetes.utils.K8sEvents.getOpGeneratedEventCount;
import static oracle.weblogic.kubernetes.utils.OKDUtils.createRouteForOKD;
import static oracle.weblogic.kubernetes.utils.OKDUtils.setTlsTerminationForRoute;
import static oracle.weblogic.kubernetes.utils.OperatorUtils.installAndVerifyOperator;
import static oracle.weblogic.kubernetes.utils.OperatorUtils.upgradeAndVerifyOperator;
import static oracle.weblogic.kubernetes.utils.PatchDomainUtils.patchDomainResource;
import static oracle.weblogic.kubernetes.utils.PersistentVolumeUtils.createPV;
import static oracle.weblogic.kubernetes.utils.PersistentVolumeUtils.createPVC;
import static oracle.weblogic.kubernetes.utils.PodUtils.checkPodDeleted;
import static oracle.weblogic.kubernetes.utils.PodUtils.checkPodDoesNotExist;
import static oracle.weblogic.kubernetes.utils.PodUtils.checkPodExists;
import static oracle.weblogic.kubernetes.utils.PodUtils.checkPodReady;
import static oracle.weblogic.kubernetes.utils.PodUtils.getExternalServicePodName;
import static oracle.weblogic.kubernetes.utils.PodUtils.getPodsWithTimeStamps;
import static oracle.weblogic.kubernetes.utils.PodUtils.setPodAntiAffinity;
import static oracle.weblogic.kubernetes.utils.SecretUtils.createSecretWithUsernamePassword;
import static oracle.weblogic.kubernetes.utils.ThreadSafeLogger.getLogger;
import static oracle.weblogic.kubernetes.utils.WLSTUtils.executeWLSTScript;
import static org.awaitility.Awaitility.with;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests related to Domain events logged by operator.
 * The tests checks for the following events in the domain name space.
 * DomainCreated, DomainChanged, DomainDeleted, DomainCompleted,
 * DomainFailed, NamespaceWatchingStarted, and NamespaceWatchingStopped.
 * The tests creates the domain resource, modifies it, introduces some validation errors in the domain resource
 * and finally deletes it to generate all the domain related events.
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DisplayName("Verify the Kubernetes events for domain lifecycle")
@IntegrationTest
class ItKubernetesEvents {

  private static String opNamespace = null;
  private static String domainNamespace1 = null;
  private static String domainNamespace2 = null;
  private static String domainNamespace3 = null;
  private static String domainNamespace4 = null;
  private static String domainNamespace5 = null;
  private static String opServiceAccount = null;
  private static int externalRestHttpsPort = 0;
  private static OperatorParams opParams = null;

  final String cluster1Name = "mycluster";
  final String cluster2Name = "cl2";
  final String adminServerName = "admin-server";
  final String adminServerPodName = domainUid + "-" + adminServerName;
  final String managedServerNameBase = "ms-";
  String managedServerPodNamePrefix = domainUid + "-" + managedServerNameBase;
  final int managedServerPort = 8001;
  int replicaCount = 2;

  final String pvName = domainUid + "-pv"; // name of the persistent volume
  final String pvcName = domainUid + "-pvc"; // name of the persistent volume claim
  private static final String domainUid = "k8seventsdomain";
  private final String wlSecretName = "weblogic-credentials";

  private static LoggingFacade logger = null;

  public static ConditionFactory withLongRetryPolicy = with().pollDelay(2, SECONDS)
      .and().with().pollInterval(10, SECONDS)
      .atMost(10, MINUTES).await();

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
    opParams = installAndVerifyOperator(opNamespace, opServiceAccount, true, 0, domainNamespace1);
    externalRestHttpsPort = getServiceNodePort(opNamespace, "external-weblogic-operator-svc");

    // This test uses the operator restAPI to scale the domain. To do this in OKD cluster,
    // we need to expose the external service as route and set tls termination to  passthrough
    logger.info("Create a route for the operator external service - only for OKD");
    String opExternalSvc = createRouteForOKD("external-weblogic-operator-svc", opNamespace);
    // Patch the route just created to set tls termination to passthrough
    setTlsTerminationForRoute("external-weblogic-operator-svc", opNamespace);

    // create pull secrets for WebLogic image when running in non Kind Kubernetes cluster
    // this secret is used only for non-kind cluster
    createSecretForBaseImages(domainNamespace1);
  }

  /**
   * Test domain events are logged when domain resource goes through various life cycle stages.
   * Verifies DomainCreated is logged when domain resource is created.
   * Verifies DomainCompleted is logged when the domain resource reaches its completed state.
   */
  @Order(1)
  @Test
  @DisplayName("Test domain events for various successful domain life cycle changes")
  void testDomainK8SEventsSuccess() {
    OffsetDateTime timestamp = now();
    logger.info("Creating domain");
    createDomain();

    logger.info("verify the DomainCreated event is generated");
    checkEvent(opNamespace, domainNamespace1, domainUid, DOMAIN_CREATED, "Normal", timestamp);
    logger.info("verify the DomainCompleted event is generated");
    checkEvent(opNamespace, domainNamespace1, domainUid, DOMAIN_COMPLETED, "Normal", timestamp);
  }

  /**
   * Patch a domain resource with a new managed server not existing in actual WebLogic domain and verify
   * the warning DomainFailed event is logged by the operator in the domain namespace.
   */
  @Order(2)
  @Test
  @DisplayName("Test domain DomainFailed event with TopologyMismatch for non-existing managed server")
  void testDomainK8sEventsNonExistingManagedServer() {
    OffsetDateTime timestamp = now();
    logger.info("patch the domain resource with non-existing managed server");
    String patchStr
        = "[{\"op\": \"add\",\"path\": \""
        + "/spec/managedServers/-\", \"value\": "
        + "{\"serverName\" : \"nonexisting-ms\", "
        + "\"serverStartPolicy\": \"IF_NEEDED\","
        + "\"serverStartState\": \"RUNNING\"}"
        + "}]";
    logger.info("Updating domain configuration using patch string: {0}\n", patchStr);
    V1Patch patch = new V1Patch(patchStr);
    assertTrue(patchDomainCustomResource(domainUid, domainNamespace1, patch, V1Patch.PATCH_FORMAT_JSON_PATCH),
        "Failed to patch domain");
    logger.info("verify the DomainFailed event is generated");
    checkFailedEvent(opNamespace, domainNamespace1, domainUid, TOPOLOGY_MISMATCH_ERROR, "Warning", timestamp);

    // remove the managed server from domain resource
    timestamp = now();
    patchStr
        = "[{\"op\": \"remove\",\"path\": \""
        + "/spec/managedServers\""
        + "}]";
    logger.info("Updating domain configuration using patch string: {0}\n", patchStr);
    patch = new V1Patch(patchStr);
    assertTrue(patchDomainCustomResource(domainUid, domainNamespace1, patch, V1Patch.PATCH_FORMAT_JSON_PATCH),
        "Failed to patch domain");

    logger.info("verify the DomainChanged event is generated");
    checkEvent(opNamespace, domainNamespace1, domainUid, DOMAIN_CHANGED, "Normal", timestamp);
  }

  /**
   * Patch a domain resource with a new cluster not existing in actual WebLogic domain and verify
   * the warning DomainFailed event is logged by the operator in the domain namespace.
   */
  @Order(3)
  @Test
  @DisplayName("Test domain DomainFailed event for non-existing cluster")
  void testDomainK8sEventsNonExistingCluster() {
    OffsetDateTime timestamp = now();
    logger.info("patch the domain resource with new cluster");
    String patchStr
        = "["
        + "{\"op\": \"add\",\"path\": \"/spec/clusters/-\", \"value\": "
        + "    {\"clusterName\" : \"nonexisting-cluster\", \"replicas\": 2, \"serverStartState\": \"RUNNING\"}"
        + "}]";
    logger.info("Updating domain configuration using patch string: {0}\n", patchStr);
    V1Patch patch = new V1Patch(patchStr);
    assertTrue(patchDomainCustomResource(domainUid, domainNamespace1, patch, V1Patch.PATCH_FORMAT_JSON_PATCH),
        "Failed to patch domain");
    // verify the DomainFailed event is generated
    checkFailedEvent(opNamespace, domainNamespace1, domainUid, TOPOLOGY_MISMATCH_ERROR, "Warning", timestamp);

    //remove the cluster from domain resource
    timestamp = now();
    patchStr = "[{\"op\": \"remove\",\"path\": \"/spec/clusters/1\"}]";
    logger.info("Updating domain configuration using patch string: {0}\n", patchStr);
    patch = new V1Patch(patchStr);
    assertTrue(patchDomainCustomResource(domainUid, domainNamespace1, patch, V1Patch.PATCH_FORMAT_JSON_PATCH),
        "Failed to patch domain");

    // verify the DomainChanged event is generated
    checkEvent(opNamespace, domainNamespace1, domainUid, DOMAIN_CHANGED, "Normal", timestamp);
  }

  /**
   * Test the following domain events are logged when domain resource goes through introspector failure.
   * Patch the domain resource to shutdown servers.
   * Patch the domain resource to point to a bad DOMAIN_HOME and update serverStartPolicy to IF_NEEDED.
   * Verifies DomainFailed event with Aborted failure reason is logged.
   * Cleanup by patching the domain resource to a valid location and introspectVersion to bring up all servers again.
   */
  @Order(4)
  @Test
  @DisplayName("Test domain events for failed/retried domain life cycle changes")
  void testDomainK8SEventsFailed() {
    V1Patch patch;
    String patchStr;
    Domain domain = assertDoesNotThrow(() -> getDomainCustomResource(domainUid, domainNamespace1));
    String originalDomainHome = domain.getSpec().getDomainHome();

    OffsetDateTime timestamp = now();
    try {
      logger.info("Shutting down all servers in domain with serverStartPolicy : NEVER");
      patchStr = "[{\"op\": \"replace\", \"path\": \"/spec/serverStartPolicy\", \"value\": \"NEVER\"}]";
      patch = new V1Patch(patchStr);
      assertTrue(patchDomainCustomResource(domainUid, domainNamespace1, patch, V1Patch.PATCH_FORMAT_JSON_PATCH),
              "patchDomainCustomResource failed");

      logger.info("Checking if the admin server {0} is shutdown in namespace {1}",
              adminServerPodName, domainNamespace1);
      checkPodDoesNotExist(adminServerPodName, domainUid, domainNamespace1);

      for (int i = 1; i <= replicaCount; i++) {
        logger.info("Checking if the managed server {0} is shutdown in namespace {1}",
                managedServerPodNamePrefix + i, domainNamespace1);
        checkPodDoesNotExist(managedServerPodNamePrefix + i, domainUid, domainNamespace1);
      }

      logger.info("Replace the domainHome to a nonexisting location to verify the following events"
              + " DomainChanged and DomainFailed events are logged");
      patchStr = "[{\"op\": \"replace\", "
              + "\"path\": \"/spec/domainHome\", \"value\": \"" + originalDomainHome + "bad\"},"
              + "{\"op\": \"replace\", \"path\": \"/spec/serverStartPolicy\", \"value\": \"IF_NEEDED\"}]";
      logger.info("PatchStr for domainHome: {0}", patchStr);

      patch = new V1Patch(patchStr);
      assertTrue(patchDomainCustomResource(domainUid, domainNamespace1, patch, V1Patch.PATCH_FORMAT_JSON_PATCH),
              "patchDomainCustomResource failed");

      logger.info("verify domain changed event is logged");
      checkEvent(opNamespace, domainNamespace1, domainUid, DOMAIN_CHANGED, "Normal", timestamp);
      logger.info("verify domain failed event");
      checkFailedEvent(opNamespace, domainNamespace1, domainUid, ABORTED_ERROR, "Warning", timestamp);
    } finally {
      logger.info("Restoring the domain with valid location and bringing up all servers");
      timestamp = now();
      String introspectVersion = assertDoesNotThrow(() -> getNextIntrospectVersion(domainUid, domainNamespace1));
      // add back the original domain home
      patchStr = "["
              + "{\"op\": \"replace\", \"path\": \"/spec/domainHome\", \"value\": \"" + originalDomainHome + "\"},"
              + "{\"op\": \"add\", \"path\": \"/spec/introspectVersion\", \"value\": \"" + introspectVersion + "\"}"
              + "]";
      logger.info("PatchStr for domainHome: {0}", patchStr);

      patch = new V1Patch(patchStr);
      assertTrue(patchDomainCustomResource(domainUid, domainNamespace1, patch, V1Patch.PATCH_FORMAT_JSON_PATCH),
              "patchDomainCustomResource failed");

      logger.info("verify domain changed event is logged");
      checkEvent(opNamespace, domainNamespace1, domainUid, DOMAIN_CHANGED, "Normal", timestamp);
      logger.info("verifying the admin server is created and started");
      checkPodReadyAndServiceExists(adminServerPodName, domainUid, domainNamespace1);

      // verify managed server services created
      for (int i = 1; i <= replicaCount; i++) {
        logger.info("Checking managed server service/pod {0} is created in namespace {1}",
                managedServerPodNamePrefix + i, domainNamespace1);
        checkPodReadyAndServiceExists(managedServerPodNamePrefix + i, domainUid, domainNamespace1);
      }
    }
  }

  /**
   * Test verifies there is only 1 DomainCompleted event is logged
   * regardless of how many clusters exists in the domain.
   * Test creates a new cluster in the WebLogic domain, patches the domain resource to add the new cluster
   * and starts up the new cluster.
   * Verifies the scaling operation generates only one DomainCompleted.
   */
  @Order(5)
  @Test
  void testK8SEventsMultiClusterEvents() {
    OffsetDateTime timestamp = now();
    createNewCluster();
    scaleClusterWithRestApi(domainUid, cluster2Name, 1,
        externalRestHttpsPort, opNamespace, opServiceAccount);
    logger.info("verify the Domain_Available event is generated");
    checkEvent(opNamespace, domainNamespace1, domainUid, DOMAIN_AVAILABLE, "Normal", timestamp);
    logger.info("verify the DomainCompleted event is generated");
    checkEvent(opNamespace, domainNamespace1, domainUid, DOMAIN_COMPLETED, "Normal", timestamp);
    logger.info("verify the only 1 DomainCompleted event is generated");
    assertEquals(1, getOpGeneratedEventCount(domainNamespace1, domainUid, DOMAIN_COMPLETED, timestamp));
  }

  /**
   * Scale the cluster beyond maximum dynamic cluster size and verify the
   * DomainFailed warning event is generated.
   */
  @Order(6)
  @Test
  void testDomainK8sEventsScalePastMax() {
    OffsetDateTime timestamp = now();
    try {
      logger.info("Scaling cluster using patching");
      String patchStr
          = "["
          + "{\"op\": \"replace\", \"path\": \"/spec/clusters/0/replicas\", \"value\": 3}"
          + "]";
      logger.info("Updating replicas in cluster {0} using patch string: {1}", cluster1Name, patchStr);
      V1Patch patch = new V1Patch(patchStr);
      assertTrue(patchDomainCustomResource(domainUid, domainNamespace1, patch, V1Patch.PATCH_FORMAT_JSON_PATCH),
          "Failed to patch domain");

      logger.info("verify the DomainFailed event is generated");
      checkFailedEvent(opNamespace, domainNamespace1, domainUid, REPLICAS_TOO_HIGH_ERROR, "Warning", timestamp);
    } finally {
      timestamp = now();
      logger.info("Updating domain resource to set correct replicas size");
      String patchStr
          = "["
          + "{\"op\": \"replace\", \"path\": \"/spec/clusters/0/replicas\", \"value\": 2}"
          + "]";
      logger.info("Updating replicas in cluster {0} using patch string: {1}", cluster1Name, patchStr);
      V1Patch patch = new V1Patch(patchStr);
      assertTrue(patchDomainCustomResource(domainUid, domainNamespace1, patch, V1Patch.PATCH_FORMAT_JSON_PATCH),
          "Failed to patch domain");

      logger.info("verify the DomainCompleted event is generated");
      checkEvent(opNamespace, domainNamespace1, domainUid, DOMAIN_COMPLETED, "Normal", timestamp);

    }
  }

  /**
   * Scale down and scale up the domain and verify that
   * DomainCompleted normal event is generated.
   */
  @Order(7)
  @Test
  @DisplayName("Test domain completed event when domain is scaled.")
  void testScaleDomainAndVerifyCompletedEvent() {
    try {
      scaleDomainAndVerifyCompletedEvent(1, ScaleAction.scaleDown, true);
      scaleDomainAndVerifyCompletedEvent(2, ScaleAction.scaleUp, true);
    } finally {
      scaleDomain(2);
    }
  }

  /**
   * Scale the cluster below minimum dynamic cluster size and verify the DomainFailed
   * warning event is generated.
   */
  @Order(8)
  @Test
  void testDomainK8sEventsScaleBelowMin() {
    OffsetDateTime timestamp = now();
    try {
      String patchStr
          = "["
          + "{\"op\": \"add\", \"path\": \"/spec/allowReplicasBelowMinDynClusterSize\", \"value\": false},"
          + "{\"op\": \"replace\", \"path\": \"/spec/clusters/0/replicas\", \"value\": 1}"
          + "]";
      logger.info("Updating replicas in cluster {0} using patch string: {1}", cluster1Name, patchStr);
      V1Patch patch = new V1Patch(patchStr);
      assertTrue(patchDomainCustomResource(domainUid, domainNamespace1, patch, V1Patch.PATCH_FORMAT_JSON_PATCH),
          "Failed to patch domain");

      // No event will be created for this
      logger.info("verify the DomainFailed event is NOT generated");
      assertFalse(domainEventExists(opNamespace, domainNamespace1, domainUid,  DOMAIN_FAILED, "Warning", timestamp));
    } finally {
      timestamp = now();
      logger.info("Updating domain resource to set correct replicas size");
      String patchStr
          = "["
          + "{\"op\": \"replace\", \"path\": \"/spec/clusters/0/replicas\", \"value\": 2}"
          + "]";
      logger.info("Updating replicas in cluster {0} using patch string: {1}", cluster1Name, patchStr);
      V1Patch patch = new V1Patch(patchStr);
      assertTrue(patchDomainCustomResource(domainUid, domainNamespace1, patch, V1Patch.PATCH_FORMAT_JSON_PATCH),
          "Failed to patch domain");
    }
  }

  /**
   * Replace the pv and pvc in the domain resource with a pv/pvc not containing any WebLogic domain
   * and verify the DomainFailed warning event is generated.
   */
  @Order(9)
  @Test
  void testDomainK8sEventsFailed() {
    OffsetDateTime timestamp = now();
    try {
      createPV("sample-pv", domainUid, this.getClass().getSimpleName());
      createPVC("sample-pv", "sample-pvc", domainUid, domainNamespace1);
      String introspectVersion = assertDoesNotThrow(() -> getNextIntrospectVersion(domainUid, domainNamespace1));
      String patchStr
          = "["
          + "{\"op\": \"replace\", \"path\": \"/spec/serverPod/volumeMounts/0/name\", \"value\": \"sample-pv\"},"
          + "{\"op\": \"replace\", \"path\": \"/spec/serverPod/volumes/0/name\", \"value\": \"sample-pv\"},"
          + "{\"op\": \"replace\", \"path\": "
          + "\"/spec/serverPod/volumes/0/persistentVolumeClaim/claimName\", \"value\": \"sample-pvc\"},"
          + "{\"op\": \"add\", \"path\": \"/spec/introspectVersion\", \"value\": \"" + introspectVersion + "\"}"
          + "]";
      logger.info("Updating pv/pvcs in domain resource using patch string: {0}", patchStr);
      V1Patch patch = new V1Patch(patchStr);
      assertTrue(patchDomainCustomResource(domainUid, domainNamespace1, patch, V1Patch.PATCH_FORMAT_JSON_PATCH),
          "Failed to patch domain");

      logger.info("verify the DomainFailed event is generated");
      checkEvent(opNamespace, domainNamespace1, domainUid, DOMAIN_FAILED, "Warning", timestamp);
    } finally {
      String introspectVersion = assertDoesNotThrow(() -> getNextIntrospectVersion(domainUid, domainNamespace1));
      String patchStr
          = "["
          + "{\"op\": \"replace\", \"path\": \"/spec/serverPod/volumeMounts/0/name\", \"value\": \"" + pvName + "\"},"
          + "{\"op\": \"replace\", \"path\": \"/spec/serverPod/volumes/0/name\", \"value\": \"" + pvName + "\"},"
          + "{\"op\": \"replace\", \"path\": "
          + "\"/spec/serverPod/volumes/0/persistentVolumeClaim/claimName\", \"value\": \"" + pvcName + "\"},"
          + "{\"op\": \"add\", \"path\": \"/spec/introspectVersion\", \"value\": \"" + introspectVersion + "\"}"
          + "]";
      logger.info("Updating pv/pvcs in domain resource using patch string: {0}", patchStr);
      V1Patch patch = new V1Patch(patchStr);
      timestamp = now();
      assertTrue(patchDomainCustomResource(domainUid, domainNamespace1, patch, V1Patch.PATCH_FORMAT_JSON_PATCH),
          "Failed to patch domain");

      logger.info("verify domain changed and completed events are logged");
      checkEvent(opNamespace, domainNamespace1, domainUid, DOMAIN_CHANGED, "Normal", timestamp);
      checkEvent(opNamespace, domainNamespace1, domainUid, DOMAIN_COMPLETED, "Normal", timestamp);
    }
  }

  /**
   * The test modifies the logHome property and verifies the domain roll events are logged.
   */
  @Order(10)
  @Test
  @DisplayName("Verify logHome property change rolls domain and relevant events are logged")
  void testLogHomeChangeEvents() {

    OffsetDateTime timestamp = now();

    // get the original domain resource before update
    Domain domain1 = assertDoesNotThrow(() -> getDomainCustomResource(domainUid, domainNamespace1),
        String.format("getDomainCustomResource failed with ApiException when tried to get domain %s in namespace %s",
            domainUid, domainNamespace1));

    // get the map with server pods and their original creation timestamps
    Map<String, OffsetDateTime> podsWithTimeStamps = getPodsWithTimeStamps(domainNamespace1,
        adminServerPodName, managedServerPodNamePrefix, replicaCount);

    //print out the original image name
    String logHome = domain1.getSpec().getLogHome();
    logger.info("Currently the log home used by the domain is: {0}", logHome);

    //change logHome from /shared/logs to /shared/logs/logHome
    String patchStr = "["
        + "{\"op\": \"replace\", \"path\": \"/spec/logHome\", \"value\": \"/shared/logs/logHome\"}"
        + "]";
    logger.info("PatchStr for logHome: {0}", patchStr);

    assertTrue(patchDomainResource(domainUid, domainNamespace1, new StringBuffer(patchStr)),
        "patchDomainCustomResource(logHome) failed");

    domain1 = assertDoesNotThrow(() -> getDomainCustomResource(domainUid, domainNamespace1),
        String.format("getDomainCustomResource failed with ApiException when tried to get domain %s in namespace %s",
            domainUid, domainNamespace1));

    //print out logHome in the new patched domain
    logger.info("In the new patched domain logHome is: {0}", domain1.getSpec().getLogHome());
    assertTrue(domain1.getSpec().getLogHome().equals("/shared/logs/logHome"), "logHome is not updated");

    // verify the server pods are rolling restarted and back to ready state
    logger.info("Verifying rolling restart occurred for domain {0} in namespace {1}",
        domainUid, domainNamespace1);
    assertTrue(verifyRollingRestartOccurred(podsWithTimeStamps, 1, domainNamespace1),
        String.format("Rolling restart failed for domain %s in namespace %s", domainUid, domainNamespace1));

    checkPodReadyAndServiceExists(adminServerPodName, domainUid, domainNamespace1);

    for (int i = 1; i <= replicaCount; i++) {
      logger.info("Checking managed server service {0} is created in namespace {1}",
          managedServerPodNamePrefix + i, domainNamespace1);
      checkPodReadyAndServiceExists(managedServerPodNamePrefix + i, domainUid, domainNamespace1);
    }

    //verify the logHome change causes the domain roll events to be logged
    logger.info("verify domain roll starting/pod cycle starting events are logged");
    checkEvent(opNamespace, domainNamespace1, domainUid, DOMAIN_ROLL_STARTING, "Normal", timestamp);
    checkEvent(opNamespace, domainNamespace1, domainUid, POD_CYCLE_STARTING, "Normal", timestamp);

    CoreV1Event event = getOpGeneratedEvent(domainNamespace1,
        DOMAIN_ROLL_STARTING, "Normal", timestamp);
    logger.info(Yaml.dump(event));
    logger.info("verify the event message contains the logHome changed messages is logged");
    assertTrue(event.getMessage().contains("logHome"));

    event = getOpGeneratedEvent(domainNamespace1,
        POD_CYCLE_STARTING, "Normal", timestamp);
    logger.info(Yaml.dump(event));
    logger.info("verify the event message contains the LOG_HOME changed messages is logged");
    assertTrue(event.getMessage().contains("LOG_HOME"));

    checkEvent(opNamespace, domainNamespace1, domainUid, DOMAIN_ROLL_COMPLETED, "Normal", timestamp);
  }


  /**
   * The test modifies the includeServerOutInPodLog property and verifies the domain roll starting events are logged.
   */
  @Order(11)
  @Test
  @DisplayName("Verify includeServerOutInPodLog property change rolls domain and relevant events are logged")
  void testIncludeServerOutInPodLog() {

    OffsetDateTime timestamp = now();

    // get the original domain resource before update
    Domain domain1 = assertDoesNotThrow(() -> getDomainCustomResource(domainUid, domainNamespace1),
        String.format("getDomainCustomResource failed with ApiException when tried to get domain %s in namespace %s",
            domainUid, domainNamespace1));

    // get the map with server pods and their original creation timestamps
    Map<String, OffsetDateTime> podsWithTimeStamps = getPodsWithTimeStamps(domainNamespace1,
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

    assertTrue(patchDomainResource(domainUid, domainNamespace1, new StringBuffer(patchStr)),
        "patchDomainCustomResource(includeServerOutInPodLog) failed");

    domain1 = assertDoesNotThrow(() -> getDomainCustomResource(domainUid, domainNamespace1),
        String.format("getDomainCustomResource failed with ApiException when tried to get domain %s in namespace %s",
            domainUid, domainNamespace1));

    //print out includeServerOutInPodLog in the new patched domain
    logger.info("In the new patched domain includeServerOutInPodLog is: {0}",
        domain1.getSpec().includeServerOutInPodLog());
    assertTrue(domain1.getSpec().includeServerOutInPodLog() != includeLogInPod,
        "includeServerOutInPodLog is not updated");

    // verify the server pods are rolling restarted and back to ready state
    logger.info("Verifying rolling restart occurred for domain {0} in namespace {1}",
        domainUid, domainNamespace1);
    assertTrue(verifyRollingRestartOccurred(podsWithTimeStamps, 1, domainNamespace1),
        String.format("Rolling restart failed for domain %s in namespace %s", domainUid, domainNamespace1));

    checkPodReadyAndServiceExists(adminServerPodName, domainUid, domainNamespace1);

    for (int i = 1; i <= replicaCount; i++) {
      logger.info("Checking managed server service {0} is created in namespace {1}",
          managedServerPodNamePrefix + i, domainNamespace1);
      checkPodReadyAndServiceExists(managedServerPodNamePrefix + i, domainUid, domainNamespace1);
    }

    //verify the includeServerOutInPodLog change causes the domain roll events to be logged
    logger.info("verify domain roll starting/pod cycle starting events are logged");
    checkEvent(opNamespace, domainNamespace1, domainUid, DOMAIN_ROLL_STARTING, "Normal", timestamp);
    checkEvent(opNamespace, domainNamespace1, domainUid, POD_CYCLE_STARTING, "Normal", timestamp);

    CoreV1Event event = getOpGeneratedEvent(domainNamespace1,
        DOMAIN_ROLL_STARTING, "Normal", timestamp);
    logger.info(Yaml.dump(event));
    logger.info("verify the event message contains the includeServerOutInPodLog changed messages is logged");
    assertTrue(event.getMessage().contains("isIncludeServerOutInPodLog"));

    event = getOpGeneratedEvent(domainNamespace1, POD_CYCLE_STARTING, "Normal", timestamp);
    logger.info(Yaml.dump(event));
    logger.info("verify the event message contains the SERVER_OUT_IN_POD_LOG changed messages is logged");
    assertTrue(event.getMessage().contains("SERVER_OUT_IN_POD_LOG"));

    checkEvent(opNamespace, domainNamespace1, domainUid, DOMAIN_ROLL_COMPLETED, "Normal", timestamp);
  }

  /**
   * Test DomainDeleted event is logged when domain resource is deleted.
   */
  @Order(13)
  @Test
  @DisplayName("Test domain events for various domain life cycle changes")
  void testDomainK8SEventsDelete() {
    OffsetDateTime timestamp = now();

    deleteDomainCustomResource(domainUid, domainNamespace1);
    checkPodDoesNotExist(adminServerPodName, domainUid, domainNamespace1);
    checkPodDoesNotExist(managedServerPodNamePrefix + 1, domainUid, domainNamespace1);
    checkPodDoesNotExist(managedServerPodNamePrefix + 2, domainUid, domainNamespace1);

    //verify domain deleted event
    checkEvent(opNamespace, domainNamespace1, domainUid, DOMAIN_DELETED, "Normal", timestamp);
  }

  /**
   * Test verifies the operator logs a NamespaceWatchingStarted event in the respective domain namespace
   * when it starts watching a new domain namespace with domainNamespaceSelectionStrategy default to List and
   * operator logs a NamespaceWatchingStopped event in the respective domain namespace
   * when it stops watching a domain namespace.
   * The test upgrades the operator instance through helm to add or remove another domain namespace
   * in the operator watch list.
   * This is a parameterized test with enableClusterRoleBinding set to either true or false.
   *<p>
   *<pre>{@literal
   * helm upgrade weblogic-operator kubernetes/charts/weblogic-operator
   * --namespace ns-ipqy
   * --reuse-values
   * --set "domainNamespaces={ns-xghr,ns-idir}"
   * }
   * </pre>
   * </p>
   */
  @Order(14)
  @ParameterizedTest
  @ValueSource(booleans = { true, false })
  void testK8SEventsStartStopWatchingNS(boolean enableClusterRoleBinding) {
    logger.info("testing testK8SEventsStartStopWatchingNS with enableClusterRoleBinding={0}",
        enableClusterRoleBinding);
    OffsetDateTime timestamp = now();

    logger.info("Adding a new domain namespace in the operator watch list");
    List<String> domainNamespaces = new ArrayList<>();
    domainNamespaces.add(domainNamespace1);
    domainNamespaces.add(domainNamespace2);
    opParams = opParams.domainNamespaces(domainNamespaces).enableClusterRoleBinding(enableClusterRoleBinding);
    upgradeAndVerifyOperator(opNamespace, opParams);

    logger.info("verify NamespaceWatchingStarted event is logged in namespace {0}", domainNamespace2);
    checkEvent(opNamespace, domainNamespace2, null, NAMESPACE_WATCHING_STARTED, "Normal", timestamp);

    timestamp = now();

    logger.info("Removing domain namespace {0} in the operator watch list", domainNamespace2);
    domainNamespaces.clear();
    domainNamespaces.add(domainNamespace1);
    opParams = opParams.domainNamespaces(domainNamespaces);
    upgradeAndVerifyOperator(opNamespace, opParams);

    logger.info("verify NamespaceWatchingStopped event is logged in namespace {0}", domainNamespace2);
    checkNamespaceWatchingStoppedEvent(opNamespace, domainNamespace2, null, "Normal", timestamp,
        enableClusterRoleBinding);
  }

  /**
   * Test verifies the operator logs a NamespaceWatchingStarted event in the respective domain namespace
   * when it starts watching a new domain namespace with domainNamespaceSelectionStrategy set to LabelSelector and
   * operator logs a NamespaceWatchingStopped event in the respective domain namespace
   * when it stops watching a domain namespace.
   * If set to LabelSelector, then the operator will manage the set of namespaces discovered by a list of namespaces
   * using the value specified by domainNamespaceLabelSelector as a label selector.
   * The test upgrades the operator instance through helm to add or remove another domain namespace
   * in the operator watch list.
   *<p>
   *<pre>{@literal
   * helm upgrade weblogic-operator kubernetes/charts/weblogic-operator
   * --namespace ns-ipqy
   * --reuse-values
   * --set "domainNamespaceSelectionStrategy=LabelSelector"
   * --set "domainNamespaceLabelSelector=weblogic-operator\=enabled"
   * }
   * </pre>
   * </p>
   */
  @Order(15)
  @ParameterizedTest
  @ValueSource(booleans = { true, false })
  void testK8SEventsStartStopWatchingNSWithLabelSelector(boolean enableClusterRoleBinding) {
    logger.info("testing testK8SEventsStartStopWatchingNSWithLabelSelector with enableClusterRoleBinding={0}",
        enableClusterRoleBinding);
    OffsetDateTime timestamp = now();

    logger.info("Labeling namespace {0} to enable it in the operator watch list", domainNamespace3);
    // label domainNamespace3
    new Command()
        .withParams(new CommandParams()
            .command("kubectl label ns " + domainNamespace3 + " weblogic-operator=enabled --overwrite"))
        .execute();

    // Helm upgrade parameters
    opParams = opParams
        .domainNamespaceSelectionStrategy("LabelSelector")
        .domainNamespaceLabelSelector("weblogic-operator=enabled")
        .enableClusterRoleBinding(enableClusterRoleBinding);
    upgradeAndVerifyOperator(opNamespace, opParams);

    logger.info("verify NamespaceWatchingStarted event is logged in namespace {0}", domainNamespace3);
    checkEvent(opNamespace, domainNamespace3, null, NAMESPACE_WATCHING_STARTED, "Normal", timestamp);

    // verify there is no event logged in domainNamespace4
    logger.info("verify NamespaceWatchingStarted event is not logged in {0}", domainNamespace4);
    assertFalse(domainEventExists(opNamespace, domainNamespace4, null, NAMESPACE_WATCHING_STARTED,
        "Normal", timestamp), "domain event " + NAMESPACE_WATCHING_STARTED + " is logged in "
        + domainNamespace4 + ", expected no such event will be logged");

    timestamp = now();
    logger.info("Labelling namespace {0} to \"weblogic-operator=disabled\" to disable it in the operator "
        + "watch list", domainNamespace3);

    // label domainNamespace3 to weblogic-operator=disabled
    new Command()
        .withParams(new CommandParams()
            .command("kubectl label ns " + domainNamespace3 + " weblogic-operator=disabled --overwrite"))
        .execute();

    logger.info("verify NamespaceWatchingStopped event is logged in namespace {0}", domainNamespace3);
    checkNamespaceWatchingStoppedEvent(opNamespace, domainNamespace3, null, "Normal", timestamp,
        enableClusterRoleBinding);

    if (enableClusterRoleBinding) {
      String newNSWithoutLabels = "ns-newnamespace1";
      String newNSWithLabels = "ns-newnamespace2";

      assertDoesNotThrow(() -> createNamespaces(newNSWithoutLabels, newNSWithLabels),
          "Failed to create new namespaces");

      new Command()
          .withParams(new CommandParams()
              .command("kubectl label ns " + newNSWithLabels + " weblogic-operator=enabled --overwrite"))
          .execute();

      logger.info("verify NamespaceWatchingStarted event is logged in namespace {0}", newNSWithLabels);
      checkEvent(opNamespace, newNSWithLabels, null, NAMESPACE_WATCHING_STARTED, "Normal", timestamp);

      // verify there is no event logged in domainNamespace4
      logger.info("verify NamespaceWatchingStarted event is not logged in {0}", domainNamespace4);
      assertFalse(domainEventExists(opNamespace, newNSWithoutLabels, null, NAMESPACE_WATCHING_STARTED,
          "Normal", timestamp), "domain event " + NAMESPACE_WATCHING_STARTED + " is logged in "
          + newNSWithoutLabels + ", expected no such event will be logged");
    }
  }

  private void createNamespaces(String newNSWithoutLabels, String newNSWithLabels) throws ApiException {
    createNamespace(newNSWithoutLabels);
    createNamespace(newNSWithLabels);
  }

  /**
   * Test verifies the operator logs a NamespaceWatchingStarted event in the respective domain namespace
   * when it starts watching a new domain namespace with domainNamespaceSelectionStrategy set to RegExp and
   * operator logs a NamespaceWatchingStopped event in the respective domain namespace
   * when it stops watching a domain namespace.
   * If set to RegExp, then the operator will manage the set of namespaces discovered by a list of namespaces
   * using the value specified by domainNamespaceRegExp as a regular expression matched against the namespace names.
   * The test upgrades the operator instance through helm to add or remove another domain namespace
   * in the operator watch list.
   *<p>
   *<pre>{@literal
   * helm upgrade weblogic-operator kubernetes/charts/weblogic-operator
   * --namespace ns-ipqy
   * --reuse-values
   * --set "domainNamespaceSelectionStrategy=RegExp"
   * --set "domainNamespaceRegExp=abcd"
   * }
   * </pre>
   * </p>
   */
  @Order(16)
  @ParameterizedTest
  @ValueSource(booleans = { true, false })
  void testK8SEventsStartStopWatchingNSWithRegExp(boolean enableClusterRoleBinding) {
    OffsetDateTime timestamp = now();
    logger.info("Adding a new domain namespace {0} in the operator watch list", domainNamespace5);
    // Helm upgrade parameters
    opParams = opParams
        .domainNamespaceSelectionStrategy("RegExp")
        .domainNamespaceRegExp(domainNamespace5.substring(3))
        .enableClusterRoleBinding(enableClusterRoleBinding);

    upgradeAndVerifyOperator(opNamespace, opParams);

    logger.info("verify NamespaceWatchingStarted event is logged in {0}", domainNamespace5);
    checkEvent(opNamespace, domainNamespace5, null, NAMESPACE_WATCHING_STARTED, "Normal", timestamp);

    // verify there is no event logged in domainNamespace4
    logger.info("verify NamespaceWatchingStarted event is not logged in {0}", domainNamespace4);
    assertFalse(domainEventExists(opNamespace, domainNamespace4, null, NAMESPACE_WATCHING_STARTED,
        "Normal", timestamp), "domain event " + NAMESPACE_WATCHING_STARTED + " is logged in "
        + domainNamespace4 + ", expected no such event will be logged");

    timestamp = now();
    logger.info("Setting the domainNamesoaceRegExp to a new value {0}", domainNamespace4.substring(3));

    // Helm upgrade parameters
    opParams = opParams
        .domainNamespaceSelectionStrategy("RegExp")
        .domainNamespaceRegExp(domainNamespace4.substring(3));

    upgradeAndVerifyOperator(opNamespace, opParams);

    logger.info("verify NamespaceWatchingStopped event is logged in namespace {0}", domainNamespace5);
    checkNamespaceWatchingStoppedEvent(opNamespace, domainNamespace5, null, "Normal", timestamp,
        enableClusterRoleBinding);

    logger.info("verify NamespaceWatchingStarted event is logged in namespace {0}", domainNamespace4);
    checkEvent(opNamespace, domainNamespace4, null, NAMESPACE_WATCHING_STARTED, "Normal", timestamp);
  }

  /**
   * Operator helm parameter domainNamespaceSelectionStrategy is set to Dedicated.
   * If set to Dedicated, then operator will manage WebLogic Domains only in the same namespace which the operator
   * itself is deployed, which is the namespace of the Helm release.
   * Operator logs a NamespaceWatchingStopped in the operator domain namespace and
   * NamespaceWatchingStopped event in the other domain namespaces when it stops watching a domain namespace.
   *
   * Test verifies NamespaceWatchingStopped event is logged when operator stops watching a domain namespace.
   */
  @Order(17)
  @Test
  void testK8SEventsStartStopWatchingNSWithDedicated() {
    OffsetDateTime timestamp = now();

    // Helm upgrade parameters
    opParams = opParams.domainNamespaceSelectionStrategy("Dedicated")
                .enableClusterRoleBinding(false);

    upgradeAndVerifyOperator(opNamespace, opParams);

    logger.info("verify NamespaceWatchingStarted event is logged in {0}", opNamespace);
    checkEvent(opNamespace, opNamespace, null, NAMESPACE_WATCHING_STARTED, "Normal", timestamp);

    logger.info("verify NamespaceWatchingStopped event is logged in {0}", domainNamespace4);
    checkNamespaceWatchingStoppedEvent(opNamespace, domainNamespace4, null, "Normal", timestamp, false);
  }

  /**
   * Cleanup the persistent volume and persistent volume claim used by the test.
   */
  @AfterAll
  public static void tearDown() {
    if (System.getenv("SKIP_CLEANUP") == null
        || (System.getenv("SKIP_CLEANUP") != null
        && System.getenv("SKIP_CLEANUP").equalsIgnoreCase("false"))) {
      deletePersistentVolumeClaim(domainNamespace1, "sample-pvc");
      deletePersistentVolume("sample-pv");
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

  private static void checkNamespaceWatchingStoppedEvent(
      String opNamespace, String domainNamespace, String domainUid,
      String type, OffsetDateTime timestamp, boolean enableClusterRoleBinding) {
    testUntil(
        checkDomainEventWatchingStopped(
            opNamespace, domainNamespace, domainUid, type, timestamp, enableClusterRoleBinding),
        logger,
        "domain event NamespaceWatchingStopped to be logged in namespace {0}",
        domainNamespace);
  }

  private static void checkEventWithCount(
      String opNamespace, String domainNamespace, String domainUid,
      String reason, String type, OffsetDateTime timestamp, int countBefore) {
    testUntil(
        checkDomainEventWithCount(
            opNamespace, domainNamespace, domainUid, reason, type, timestamp, countBefore),
        logger,
        "domain event {0} to be logged",
        reason);
  }

  // Create and start a WebLogic domain in PV
  private void createDomain() {

    // create WebLogic domain credential secret
    createSecretWithUsernamePassword(wlSecretName, domainNamespace1,
        ADMIN_USERNAME_DEFAULT, ADMIN_PASSWORD_DEFAULT);

    // create persistent volume and persistent volume claim for domain
    // these resources should be labeled with domainUid for cleanup after testing
    createPV(pvName, domainUid, this.getClass().getSimpleName());
    createPVC(pvName, pvcName, domainUid, domainNamespace1);

    // create a temporary WebLogic domain property file
    File domainPropertiesFile = assertDoesNotThrow(()
        -> File.createTempFile("domain", "properties"),
        "Failed to create domain properties file");
    Properties p = new Properties();
    p.setProperty("domain_path", "/shared/domains");
    p.setProperty("domain_name", domainUid);
    p.setProperty("cluster_name", cluster1Name);
    p.setProperty("admin_server_name", adminServerName);
    p.setProperty("managed_server_port", Integer.toString(managedServerPort));
    p.setProperty("admin_server_port", "7001");
    p.setProperty("admin_username", ADMIN_USERNAME_DEFAULT);
    p.setProperty("admin_password", ADMIN_PASSWORD_DEFAULT);
    p.setProperty("admin_t3_public_address", K8S_NODEPORT_HOST);
    p.setProperty("admin_t3_channel_port", Integer.toString(32000));
    p.setProperty("number_of_ms", "2");
    p.setProperty("managed_server_name_base", managedServerNameBase);
    p.setProperty("domain_logs", "/shared/logs");
    p.setProperty("production_mode_enabled", "true");
    assertDoesNotThrow(()
        -> p.store(new FileOutputStream(domainPropertiesFile), "domain properties file"),
        "Failed to write domain properties file");

    // WLST script for creating domain
    Path wlstScript = Paths.get(RESOURCE_DIR, "python-scripts", "wlst-create-domain-onpv.py");

    // create configmap and domain on persistent volume using the WLST script and property file
    createDomainOnPVUsingWlst(wlstScript, domainPropertiesFile.toPath(),
        pvName, pvcName, domainNamespace1);

    // create a domain custom resource configuration object
    logger.info("Creating domain custom resource");
    Domain domain = new Domain()
        .apiVersion(DOMAIN_API_VERSION)
        .kind("Domain")
        .metadata(new V1ObjectMeta()
            .name(domainUid)
            .namespace(domainNamespace1))
        .spec(new DomainSpec()
            .domainUid(domainUid)
            .domainHome("/shared/domains/" + domainUid) // point to domain home in pv
            .domainHomeSourceType("PersistentVolume") // set the domain home source type as pv
            .image(WEBLOGIC_IMAGE_TO_USE_IN_SPEC)
            .imagePullPolicy("IfNotPresent")
            .imagePullSecrets(Arrays.asList(
                new V1LocalObjectReference()
                    .name(BASE_IMAGES_REPO_SECRET))) // this secret is used only in non-kind cluster
            .webLogicCredentialsSecret(new V1SecretReference()
                .name(wlSecretName)
                .namespace(domainNamespace1))
            .includeServerOutInPodLog(true)
            .logHomeEnabled(Boolean.TRUE)
            .logHome("/shared/logs/" + domainUid)
            .dataHome("")
            .serverStartPolicy("IF_NEEDED")
            .serverPod(new ServerPod() //serverpod
                .addEnvItem(new V1EnvVar()
                    .name("USER_MEM_ARGS")
                    .value("-Djava.security.egd=file:/dev/./urandom "))
                .addVolumesItem(new V1Volume()
                    .name(pvName)
                    .persistentVolumeClaim(new V1PersistentVolumeClaimVolumeSource()
                        .claimName(pvcName)))
                .addVolumeMountsItem(new V1VolumeMount()
                    .mountPath("/shared")
                    .name(pvName)))
            .adminServer(new AdminServer() //admin server
                .serverStartState("RUNNING")
                .adminService(new AdminService()
                    .addChannelsItem(new Channel()
                        .channelName("default")
                        .nodePort(0))))
            .addClustersItem(new Cluster() //cluster
                .clusterName(cluster1Name)
                .replicas(replicaCount)
                .serverStartState("RUNNING")));
    setPodAntiAffinity(domain);

    // verify the domain custom resource is created
    createDomainAndVerify(domain, domainNamespace1);

    // verify the admin server service created
    checkPodReadyAndServiceExists(adminServerPodName, domainUid, domainNamespace1);

    // verify managed server services created
    for (int i = 1; i <= replicaCount; i++) {
      logger.info("Checking managed server service/pod {0} is created in namespace {1}",
          managedServerPodNamePrefix + i, domainNamespace1);
      checkPodReadyAndServiceExists(managedServerPodNamePrefix + i, domainUid, domainNamespace1);
    }
  }

  /**
   * Create a WebLogic domain on a persistent volume by doing the following. Create a configmap containing WLST script
   * and property file. Create a Kubernetes job to create domain on persistent volume.
   *
   * @param wlstScriptFile python script to create domain
   * @param domainPropertiesFile properties file containing domain configuration
   * @param pvName name of the persistent volume to create domain in
   * @param pvcName name of the persistent volume claim
   * @param namespace name of the domain namespace in which the job is created
   */
  private void createDomainOnPVUsingWlst(Path wlstScriptFile, Path domainPropertiesFile,
      String pvName, String pvcName, String namespace) {
    logger.info("Preparing to run create domain job using WLST");

    List<Path> domainScriptFiles = new ArrayList<>();
    domainScriptFiles.add(wlstScriptFile);
    domainScriptFiles.add(domainPropertiesFile);

    logger.info("Creating a config map to hold domain creation scripts");
    String domainScriptConfigMapName = "create-domain-scripts-cm";
    assertDoesNotThrow(
        () -> createConfigMapForDomainCreation(
            domainScriptConfigMapName, domainScriptFiles, namespace, this.getClass().getSimpleName()),
        "Create configmap for domain creation failed");

    // create a V1Container with specific scripts and properties for creating domain
    V1Container jobCreationContainer = new V1Container()
        .addCommandItem("/bin/sh")
        .addArgsItem("/u01/oracle/oracle_common/common/bin/wlst.sh")
        .addArgsItem("/u01/weblogic/" + wlstScriptFile.getFileName()) //wlst.sh script
        .addArgsItem("-skipWLSModuleScanning")
        .addArgsItem("-loadProperties")
        .addArgsItem("/u01/weblogic/" + domainPropertiesFile.getFileName()); //domain property file

    logger.info("Running a Kubernetes job to create the domain");
    createDomainJob(WEBLOGIC_IMAGE_TO_USE_IN_SPEC, pvName, pvcName, domainScriptConfigMapName,
        namespace, jobCreationContainer);
  }

  private void createNewCluster() {
    final String managedServerNameBase = "cl2-ms-";
    String managedServerPodNamePrefix = domainUid + "-" + managedServerNameBase;

    logger.info("Getting port for default channel");
    int adminServerPort
        = getServicePort(domainNamespace1, getExternalServicePodName(adminServerPodName), "default");

    // create a temporary WebLogic WLST property file
    File wlstPropertiesFile = assertDoesNotThrow(() -> File.createTempFile("wlst", "properties"),
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
    executeWLSTScript(configScript, wlstPropertiesFile.toPath(), domainNamespace1);

    String introspectVersion = assertDoesNotThrow(() -> getNextIntrospectVersion(domainUid, domainNamespace1));

    logger.info("patch the domain resource with new cluster and introspectVersion");
    String patchStr
        = "["
        + "{\"op\": \"add\",\"path\": \"/spec/clusters/-\", \"value\": "
        + "    {\"clusterName\" : \"" + cluster2Name + "\", \"replicas\": 2, \"serverStartState\": \"RUNNING\"}"
        + "},"
        + "{\"op\": \"replace\", \"path\": \"/spec/introspectVersion\", \"value\": \"" + introspectVersion + "\"}"
        + "]";
    logger.info("Updating domain configuration using patch string: {0}\n", patchStr);
    V1Patch patch = new V1Patch(patchStr);
    assertTrue(patchDomainCustomResource(domainUid, domainNamespace1, patch, V1Patch.PATCH_FORMAT_JSON_PATCH),
        "Failed to patch domain");

    //verify the introspector pod is created and runs
    String introspectPodNameBase = getIntrospectJobName(domainUid);

    checkPodExists(introspectPodNameBase, domainUid, domainNamespace1);
    checkPodDoesNotExist(introspectPodNameBase, domainUid, domainNamespace1);

    // verify new cluster managed server services created
    for (int i = 1; i <= replicaCount; i++) {
      logger.info("Checking managed server service {0} is created in namespace {1}",
          managedServerPodNamePrefix + i, domainNamespace1);
      checkServiceExists(managedServerPodNamePrefix + i, domainNamespace1);
    }

    // verify new cluster managed server pods are ready
    for (int i = 1; i <= replicaCount; i++) {
      logger.info("Waiting for managed server pod {0} to be ready in namespace {1}",
          managedServerPodNamePrefix + i, domainNamespace1);
      checkPodReady(managedServerPodNamePrefix + i, domainUid, domainNamespace1);
    }
  }

  private void scaleDomainAndVerifyCompletedEvent(int replicaCount, ScaleAction testType, boolean verify) {
    OffsetDateTime timestamp = now();
    logger.info("Updating domain resource to set the replicas for cluster " + cluster1Name + " to " + replicaCount);
    int countBefore = getDomainEventCount(domainNamespace1, domainUid, DOMAIN_COMPLETED, "Normal");
    V1Patch patch = new V1Patch("["
        + "{\"op\": \"replace\", \"path\": \"/spec/clusters/0/replicas\", \"value\": " + replicaCount + "}" + "]");
    assertTrue(patchDomainCustomResource(domainUid, domainNamespace1, patch, V1Patch.PATCH_FORMAT_JSON_PATCH),
        "Failed to patch domain");
    int serverNumber = replicaCount + 1;

    switch (testType) {
      case scaleUp:
        checkPodReady(managedServerPodNamePrefix + replicaCount, domainUid, domainNamespace1);
        break;
      case scaleDown:
        checkPodDeleted(managedServerPodNamePrefix + serverNumber, domainUid, domainNamespace1);
        break;
      case reset:
        checkPodReady(managedServerPodNamePrefix + replicaCount, domainUid, domainNamespace1);
        checkPodDeleted(managedServerPodNamePrefix + serverNumber, domainUid, domainNamespace1);
        break;
      default:
    }

    if (verify) {
      logger.info("Verify the DomainCompleted event is generated after " + testType);
      checkEventWithCount(
          opNamespace, domainNamespace1, domainUid, DOMAIN_COMPLETED, "Normal", timestamp, countBefore);
    }
  }

  private void scaleDomain(int replicaCount) {
    scaleDomainAndVerifyCompletedEvent(replicaCount, ScaleAction.reset, false);
  }

}
