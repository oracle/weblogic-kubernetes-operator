// Copyright (c) 2020, 2021, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.weblogic.kubernetes;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;

import io.kubernetes.client.custom.V1Patch;
import io.kubernetes.client.openapi.models.V1Container;
import io.kubernetes.client.openapi.models.V1EnvVar;
import io.kubernetes.client.openapi.models.V1LocalObjectReference;
import io.kubernetes.client.openapi.models.V1ObjectMeta;
import io.kubernetes.client.openapi.models.V1PersistentVolumeClaimVolumeSource;
import io.kubernetes.client.openapi.models.V1SecretReference;
import io.kubernetes.client.openapi.models.V1Volume;
import io.kubernetes.client.openapi.models.V1VolumeMount;
import oracle.weblogic.domain.AdminServer;
import oracle.weblogic.domain.AdminService;
import oracle.weblogic.domain.Channel;
import oracle.weblogic.domain.Cluster;
import oracle.weblogic.domain.Domain;
import oracle.weblogic.domain.DomainSpec;
import oracle.weblogic.domain.ServerPod;
import oracle.weblogic.kubernetes.annotations.IntegrationTest;
import oracle.weblogic.kubernetes.annotations.Namespaces;
import oracle.weblogic.kubernetes.logging.LoggingFacade;
import org.awaitility.core.ConditionFactory;
import org.joda.time.DateTime;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import static java.util.concurrent.TimeUnit.MINUTES;
import static java.util.concurrent.TimeUnit.SECONDS;
import static oracle.weblogic.kubernetes.TestConstants.ADMIN_PASSWORD_DEFAULT;
import static oracle.weblogic.kubernetes.TestConstants.ADMIN_USERNAME_DEFAULT;
import static oracle.weblogic.kubernetes.TestConstants.BASE_IMAGES_REPO_SECRET;
import static oracle.weblogic.kubernetes.TestConstants.DOMAIN_API_VERSION;
import static oracle.weblogic.kubernetes.TestConstants.K8S_NODEPORT_HOST;
import static oracle.weblogic.kubernetes.TestConstants.WEBLOGIC_IMAGE_TO_USE_IN_SPEC;
import static oracle.weblogic.kubernetes.actions.ActionConstants.RESOURCE_DIR;
import static oracle.weblogic.kubernetes.actions.TestActions.deleteDomainCustomResource;
import static oracle.weblogic.kubernetes.actions.TestActions.deletePersistentVolume;
import static oracle.weblogic.kubernetes.actions.TestActions.deletePersistentVolumeClaim;
import static oracle.weblogic.kubernetes.actions.TestActions.getNextIntrospectVersion;
import static oracle.weblogic.kubernetes.actions.TestActions.getServiceNodePort;
import static oracle.weblogic.kubernetes.actions.TestActions.getServicePort;
import static oracle.weblogic.kubernetes.actions.TestActions.scaleClusterWithRestApi;
import static oracle.weblogic.kubernetes.actions.impl.Domain.patchDomainCustomResource;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.checkPodDoesNotExist;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.checkPodExists;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.checkPodReady;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.checkPodReadyAndServiceExists;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.checkServiceExists;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.createConfigMapForDomainCreation;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.createDomainAndVerify;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.createDomainJob;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.createPV;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.createPVC;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.createSecretForBaseImages;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.createSecretWithUsernamePassword;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.getExternalServicePodName;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.getIntrospectJobName;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.installAndVerifyOperator;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.setPodAntiAffinity;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.upgradeAndVerifyOperator;
import static oracle.weblogic.kubernetes.utils.K8sEvents.DOMAIN_CHANGED;
import static oracle.weblogic.kubernetes.utils.K8sEvents.DOMAIN_CREATED;
import static oracle.weblogic.kubernetes.utils.K8sEvents.DOMAIN_DELETED;
import static oracle.weblogic.kubernetes.utils.K8sEvents.DOMAIN_PROCESSING_ABORTED;
import static oracle.weblogic.kubernetes.utils.K8sEvents.DOMAIN_PROCESSING_COMPLETED;
import static oracle.weblogic.kubernetes.utils.K8sEvents.DOMAIN_PROCESSING_FAILED;
import static oracle.weblogic.kubernetes.utils.K8sEvents.DOMAIN_PROCESSING_RETRYING;
import static oracle.weblogic.kubernetes.utils.K8sEvents.DOMAIN_PROCESSING_STARTING;
import static oracle.weblogic.kubernetes.utils.K8sEvents.DOMAIN_VALIDATION_ERROR;
import static oracle.weblogic.kubernetes.utils.K8sEvents.NAMESPACE_WATCHING_STARTED;
import static oracle.weblogic.kubernetes.utils.K8sEvents.NAMESPACE_WATCHING_STOPPED;
import static oracle.weblogic.kubernetes.utils.K8sEvents.checkDomainEvent;
import static oracle.weblogic.kubernetes.utils.K8sEvents.getEventCount;
import static oracle.weblogic.kubernetes.utils.ThreadSafeLogger.getLogger;
import static oracle.weblogic.kubernetes.utils.WLSTUtils.executeWLSTScript;
import static org.awaitility.Awaitility.with;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests related to Domain events logged by operator.
 * The tests checks for the following events in the domain name space.
 * DomainCreated, DomainChanged, DomainDeleted, DomainProcessingStarting, DomainProcessingCompleted,
 * DomainProcessingFailed, DomainProcessingRetrying, DomainProcessingAborted, NamespaceWatchingStarted, and
 * NamespaceWatchingStopped.
 * The tests creates the domain resource, modifies it, introduces some validation errors in the domain resource
 * and finally deletes it to generate all the domain related events.
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DisplayName("Verify the Kubernetes events for domain lifecycle")
@IntegrationTest
public class ItKubernetesEvents {

  private static String opNamespace = null;
  private static String domainNamespace1 = null;
  private static String domainNamespace2 = null;
  private static String opServiceAccount = null;
  private static int externalRestHttpsPort = 0;

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

  // create standard, reusable retry/backoff policy
  private static final ConditionFactory withStandardRetryPolicy
      = with().pollDelay(2, SECONDS)
          .and().with().pollInterval(10, SECONDS)
          .atMost(10, MINUTES).await();

  private static LoggingFacade logger = null;

  /**
   * Assigns unique namespaces for operator and domains.
   * Pull WebLogic image if running tests in Kind cluster.
   * Installs operator.
   *
   * @param namespaces injected by JUnit
   */
  @BeforeAll
  public static void initAll(@Namespaces(3) List<String> namespaces) {
    logger = getLogger();
    logger.info("Assign a unique namespace for operator");
    assertNotNull(namespaces.get(0), "Namespace is null");
    opNamespace = namespaces.get(0);
    logger.info("Assign a unique namespace for WebLogic domain");
    assertNotNull(namespaces.get(1), "Namespace is null");
    domainNamespace1 = namespaces.get(1);
    assertNotNull(namespaces.get(2), "Namespace is null");
    domainNamespace2 = namespaces.get(2);

    // set the service account name for the operator
    opServiceAccount = opNamespace + "-sa";

    // install and verify operator with REST API
    installAndVerifyOperator(opNamespace, opServiceAccount, true, 0, domainNamespace1);
    externalRestHttpsPort = getServiceNodePort(opNamespace, "external-weblogic-operator-svc");

    // create pull secrets for WebLogic image when running in non Kind Kubernetes cluster
    // this secret is used only for non-kind cluster
    createSecretForBaseImages(domainNamespace1);
  }

  /**
   * Test domain events are logged when domain resource goes through various life cycle stages.
   * Verifies DomainCreated is logged when domain resource is created.
   * Verifies DomainProcessingStarting is logged when operator processes the domain resource.
   * Verifies DomainProcessingCompleted is logged when operator done processes the domain resource.
   */
  @Order(1)
  @Test
  @DisplayName("Test domain events for various successful domain life cycle changes")
  public void testDomainK8SEventsSuccess() {
    DateTime timestamp = new DateTime(Instant.now().getEpochSecond() * 1000L);
    logger.info("Creating domain");
    createDomain();

    logger.info("verify the DomainCreated event is generated");
    checkEvent(opNamespace, domainNamespace1, domainUid, DOMAIN_CREATED, "Normal", timestamp);
    logger.info("verify the DomainProcessing Starting/Completed event is generated");
    checkEvent(opNamespace, domainNamespace1, domainUid, DOMAIN_PROCESSING_STARTING, "Normal", timestamp);
    checkEvent(opNamespace, domainNamespace1, domainUid, DOMAIN_PROCESSING_COMPLETED, "Normal", timestamp);
  }

  /**
   * Patch a domain resource with a new managed server not existing in actual WebLogic domain and verify
   * the warning DomainValidationError event is logged by the operator in the domain namespace.
   */
  @Order(2)
  @Test
  @DisplayName("Test domain DomainValidationError event for non-existing managed server")
  public void testDomainK8sEventsNonExistingManagedServer() {
    DateTime timestamp = new DateTime(Instant.now().getEpochSecond() * 1000L);
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
    logger.info("verify the DomainValidationError event is generated");
    checkEvent(opNamespace, domainNamespace1, domainUid, DOMAIN_VALIDATION_ERROR, "Warning", timestamp);

    // remove the managed server from domain resource
    timestamp = new DateTime(Instant.now().getEpochSecond() * 1000L);
    patchStr
        = "[{\"op\": \"remove\",\"path\": \""
        + "/spec/managedServers\""
        + "}]";
    logger.info("Updating domain configuration using patch string: {0}\n", patchStr);
    patch = new V1Patch(patchStr);
    assertTrue(patchDomainCustomResource(domainUid, domainNamespace1, patch, V1Patch.PATCH_FORMAT_JSON_PATCH),
        "Failed to patch domain");

    logger.info("verify the DomainProcessingCompleted event is generated");
    checkEvent(opNamespace, domainNamespace1, domainUid, DOMAIN_CHANGED, "Normal", timestamp);
  }

  /**
   * Patch a domain resource with a new cluster not existing in actual WebLogic domain and verify
   * the warning DomainValidationError event is logged by the operator in the domain namespace.
   */
  @Order(3)
  @Test
  @DisplayName("Test domain DomainValidationError event for non-existing cluster")
  public void testDomainK8sEventsNonExistingCluster() {
    DateTime timestamp = new DateTime(Instant.now().getEpochSecond() * 1000L);
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
    // verify the DomainValidationError event is generated
    checkEvent(opNamespace, domainNamespace1, domainUid, DOMAIN_VALIDATION_ERROR, "Warning", timestamp);

    //remove the cluster from domain resource
    timestamp = new DateTime(Instant.now().getEpochSecond() * 1000L);
    patchStr = "[{\"op\": \"remove\",\"path\": \"/spec/clusters/1\"}]";
    logger.info("Updating domain configuration using patch string: {0}\n", patchStr);
    patch = new V1Patch(patchStr);
    assertTrue(patchDomainCustomResource(domainUid, domainNamespace1, patch, V1Patch.PATCH_FORMAT_JSON_PATCH),
        "Failed to patch domain");

    // verify the DomainProcessingStarting/Completed event is generated
    checkEvent(opNamespace, domainNamespace1, domainUid, DOMAIN_CHANGED, "Normal", timestamp);
  }

  /**
   * Test the following domain events are logged when domain resource goes through various life cycle stages.
   * Patch the domain resource to remove the webLogicCredentialsSecret and verify DomainChanged is
   * logged when operator processes the domain resource changes.
   * Verifies DomainProcessingRetrying is logged when operator retries the failed domain resource
   * changes since webLogicCredentialsSecret is still missing.
   * Verifies DomainProcessingAborted is logged when operator exceeds the maximum retries and gives
   * up processing the domain resource.
   */
  @Order(4)
  @Test
  @DisplayName("Test domain events for failed/retried domain life cycle changes")
  public void testDomainK8SEventsFailed() {
    V1Patch patch;
    String patchStr;

    DateTime timestamp = new DateTime(Instant.now().getEpochSecond() * 1000L);
    try {
      logger.info("remove the webLogicCredentialsSecret to verify the following events"
          + " DomainChanged, DomainProcessingRetrying and DomainProcessingAborted are logged");
      patchStr = "[{\"op\": \"remove\", \"path\": \"/spec/webLogicCredentialsSecret\"}]";
      logger.info("PatchStr for webLogicCredentialsSecret: {0}", patchStr);

      patch = new V1Patch(patchStr);
      assertTrue(patchDomainCustomResource(domainUid, domainNamespace1, patch, V1Patch.PATCH_FORMAT_JSON_PATCH),
          "patchDomainCustomResource failed");

      logger.info("verify domain changed event is logged");
      checkEvent(opNamespace, domainNamespace1, domainUid, DOMAIN_CHANGED, "Normal", timestamp);

      logger.info("verify domain processing retrying event");
      checkEvent(opNamespace, domainNamespace1, domainUid, DOMAIN_PROCESSING_RETRYING, "Normal", timestamp);

      logger.info("verify domain processing aborted event");
      checkEvent(opNamespace, domainNamespace1, domainUid, DOMAIN_PROCESSING_ABORTED, "Warning", timestamp);
    } finally {
      timestamp = new DateTime(Instant.now().getEpochSecond() * 1000L);
      // add back the webLogicCredentialsSecret
      patchStr = "[{\"op\": \"add\", \"path\": \"/spec/webLogicCredentialsSecret\", "
          + "\"value\" : {\"name\":\"" + wlSecretName + "\" , \"namespace\":\"" + domainNamespace1 + "\"}"
          + "}]";
      logger.info("PatchStr for webLogicCredentialsSecret: {0}", patchStr);

      patch = new V1Patch(patchStr);
      assertTrue(patchDomainCustomResource(domainUid, domainNamespace1, patch, V1Patch.PATCH_FORMAT_JSON_PATCH),
          "patchDomainCustomResource failed");

      logger.info("verify domain changed event is logged");
      checkEvent(opNamespace, domainNamespace1, domainUid, DOMAIN_CHANGED, "Normal", timestamp);
    }
  }

  /**
   * Test verifies there is only 1 DomainProcessing Starting/Completed event is logged
   * regardless of how many clusters exists in the domain.
   * Test creates a new cluster in the WebLogic domain, patches the domain resource to add the new cluster
   * and starts up the new cluster.
   * Verifies the scaling operation generates only one DomainProcessing Starting/Completed.
   */
  @Order(5)
  @Test
  public void testK8SEventsMultiClusterEvents() {
    createNewCluster();
    DateTime timestamp = new DateTime(Instant.now().getEpochSecond() * 1000L);
    scaleClusterWithRestApi(domainUid, cluster2Name, 1,
        externalRestHttpsPort, opNamespace, opServiceAccount);
    logger.info("verify the DomainProcessing Starting/Completed event is generated");
    checkEvent(opNamespace, domainNamespace1, domainUid, DOMAIN_PROCESSING_STARTING, "Normal", timestamp);
    checkEvent(opNamespace, domainNamespace1, domainUid, DOMAIN_PROCESSING_COMPLETED, "Normal", timestamp);
    logger.info("verify the only 1 DomainProcessing Starting/Completed event is generated");
    assertEquals(1, getEventCount(domainNamespace1, domainUid, DOMAIN_PROCESSING_STARTING, timestamp));
    assertEquals(1, getEventCount(domainNamespace1, domainUid, DOMAIN_PROCESSING_COMPLETED, timestamp));
  }

  /**
   * Scale the cluster beyond maximum dynamic cluster size and verify the
   * DomainValidationError warning event is generated.
   */
  @Order(6)
  @Test
  public void testDomainK8sEventsScalePastMax() {
    DateTime timestamp = new DateTime(Instant.now().getEpochSecond() * 1000L);
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

      logger.info("verify the DomainValidationError event is generated");
      checkEvent(opNamespace, domainNamespace1, domainUid, DOMAIN_VALIDATION_ERROR, "Warning", timestamp);
    } finally {
      timestamp = new DateTime(Instant.now().getEpochSecond() * 1000L);
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
   * Scale the cluster below minimum dynamic cluster size and verify the DomainValidationError
   * warning event is generated.
   */
  @Order(7)
  @Test
  public void testDomainK8sEventsScaleBelowMin() {
    DateTime timestamp = new DateTime(Instant.now().getEpochSecond() * 1000L);
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

      logger.info("verify the DomainValidationError event is generated");
      checkEvent(opNamespace, domainNamespace1, domainUid, DOMAIN_VALIDATION_ERROR, "Warning", timestamp);
    } finally {
      timestamp = new DateTime(Instant.now().getEpochSecond() * 1000L);
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
   * and verify the DomainProcessingFailed warning event is generated.
   */
  @Order(8)
  @Test
  public void testDomainK8sEventsProcessingFailed() {
    DateTime timestamp = new DateTime(Instant.now().getEpochSecond() * 1000L);
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

      logger.info("verify the DomainProcessingFailed event is generated");
      checkEvent(opNamespace, domainNamespace1, domainUid, DOMAIN_PROCESSING_FAILED, "Warning", timestamp);
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
      timestamp = new DateTime(Instant.now().getEpochSecond() * 1000L);
      assertTrue(patchDomainCustomResource(domainUid, domainNamespace1, patch, V1Patch.PATCH_FORMAT_JSON_PATCH),
          "Failed to patch domain");

      logger.info("verify domain changed/processing completed events are logged");
      checkEvent(opNamespace, domainNamespace1, domainUid, DOMAIN_CHANGED, "Normal", timestamp);
      checkEvent(opNamespace, domainNamespace1, domainUid, DOMAIN_PROCESSING_COMPLETED, "Normal", timestamp);
    }
  }


  /**
   * Test DomainDeleted event is logged when domain resource is deleted.
   */
  @Order(9)
  @Test
  @DisplayName("Test domain events for various domain life cycle changes")
  public void testDomainK8SEventsDelete() {
    DateTime timestamp = new DateTime(Instant.now().getEpochSecond() * 1000L);

    deleteDomainCustomResource(domainUid, domainNamespace1);
    checkPodDoesNotExist(adminServerPodName, domainUid, domainNamespace1);
    checkPodDoesNotExist(managedServerPodNamePrefix + 1, domainUid, domainNamespace1);
    checkPodDoesNotExist(managedServerPodNamePrefix + 2, domainUid, domainNamespace1);

    //verify domain deleted event
    checkEvent(opNamespace, domainNamespace1, domainUid, DOMAIN_DELETED, "Normal", timestamp);
  }

  /**
   * Operator logs a NamespaceWatchingStarted event in the respective domain name space
   * when it starts watching a new domain namespace.
   * The test upgrades the operator instance through helm to add another domain namespace in the operator watch list.
   *<p>
   *<pre>{@literal
   * helm upgrade weblogic-operator kubernetes/charts/weblogic-operator
   * --namespace ns-ipqy
   * --reuse-values
   * --set "domainNamespaces={ns-xghr,ns-idir}"
   * --set "externalRestEnabled=false"
   * --set "elkIntegrationEnabled=false"
   * --set "enableClusterRoleBinding=false"
   * --set "externalRestHttpsPort=0">
   * }
   * </pre>
   * </p>
   * Test verifies NamespaceWatchingStarted event is logged when operator starts watching an another domain namespace.
   */
  @Order(10)
  @Test
  public void testK8SEventsStartWatchingNS() {
    DateTime timestamp = new DateTime(Instant.now().getEpochSecond() * 1000L);
    logger.info("Adding a new domain namespace in the operator watch list");
    upgradeAndVerifyOperator(opNamespace, domainNamespace1, domainNamespace2);
    logger.info("verify NamespaceWatchingStarted event is logged");
    checkEvent(opNamespace, domainNamespace2, null, NAMESPACE_WATCHING_STARTED, "Normal", timestamp);
  }

  /**
   * Operator logs a NamespaceWatchingStopped event in the respective domain name space
   * when it stops watching a domain namespace.
   * The test upgrades the operator instance through helm to remove domain namespace in the operator watch list.
   *<p>
   *<pre>{@literal
   * helm upgrade weblogic-operator kubernetes/charts/weblogic-operator
   * --namespace ns-ipqy
   * --reuse-values
   * --set "domainNamespaces={ns-xghr}"
   * --set "externalRestEnabled=false"
   * --set "elkIntegrationEnabled=false"
   * --set "enableClusterRoleBinding=false"
   * --set "externalRestHttpsPort=0">
   * }
   * </pre>
   * </p>
   * Test verifies NamespaceWatchingStopped event is logged when operator stops watching a domain namespace.
   */
  @Order(11)
  @Test
  @Disabled("Bug - OWLS-87181")
  public void testK8SEventsStopWatchingNS() {
    DateTime timestamp = new DateTime(Instant.now().getEpochSecond() * 1000L);
    logger.info("Removing domain namespace in the operator watch list");
    upgradeAndVerifyOperator(opNamespace, domainNamespace1);
    logger.info("verify NamespaceWatchingStopped event is logged");
    checkEvent(opNamespace, domainNamespace2, null, NAMESPACE_WATCHING_STOPPED, "Normal", timestamp);
  }

  // Utility method to check event
  private static void checkEvent(
      String opNamespace, String domainNamespace, String domainUid, String reason, String type, DateTime timestamp) {
    //verify domain deleted event
    withStandardRetryPolicy
        .conditionEvaluationListener(condition -> logger.info("Waiting for domain event {0} to be logged "
        + "(elapsed time {1}ms, remaining time {2}ms)",
        reason,
        condition.getElapsedTimeInMS(),
        condition.getRemainingTimeInMS()))
        .until(checkDomainEvent(opNamespace, domainNamespace, domainUid,
            reason, type, timestamp));
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

  /**
   * Cleanup the persistent volume and persistent volume claim used by the test.
   */
  @AfterAll
  public static void tearDown() {
    deletePersistentVolume("sample-pv");
    deletePersistentVolumeClaim(domainNamespace1, "sample-pvc");
  }

}
