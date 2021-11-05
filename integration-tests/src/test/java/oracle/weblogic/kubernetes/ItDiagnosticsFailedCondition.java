// Copyright (c) 2021, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.weblogic.kubernetes;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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
import oracle.weblogic.domain.Configuration;
import oracle.weblogic.domain.Domain;
import oracle.weblogic.domain.DomainSpec;
import oracle.weblogic.domain.Model;
import oracle.weblogic.domain.ServerPod;
import oracle.weblogic.domain.ServerService;
import oracle.weblogic.kubernetes.annotations.IntegrationTest;
import oracle.weblogic.kubernetes.annotations.Namespaces;
import oracle.weblogic.kubernetes.logging.LoggingFacade;
import oracle.weblogic.kubernetes.utils.CommonTestUtils;
import oracle.weblogic.kubernetes.utils.ConfigMapUtils;
import oracle.weblogic.kubernetes.utils.ImageUtils;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import static oracle.weblogic.kubernetes.TestConstants.ADMIN_PASSWORD_DEFAULT;
import static oracle.weblogic.kubernetes.TestConstants.ADMIN_USERNAME_DEFAULT;
import static oracle.weblogic.kubernetes.TestConstants.BASE_IMAGES_REPO_SECRET;
import static oracle.weblogic.kubernetes.TestConstants.DOMAIN_API_VERSION;
import static oracle.weblogic.kubernetes.TestConstants.DOMAIN_STATUS_CONDITION_AVAILABLE_TYPE;
import static oracle.weblogic.kubernetes.TestConstants.DOMAIN_STATUS_CONDITION_COMPLETED_TYPE;
import static oracle.weblogic.kubernetes.TestConstants.DOMAIN_STATUS_CONDITION_FAILED_TYPE;
import static oracle.weblogic.kubernetes.TestConstants.K8S_NODEPORT_HOST;
import static oracle.weblogic.kubernetes.TestConstants.KIND_REPO;
import static oracle.weblogic.kubernetes.TestConstants.OCIR_REGISTRY;
import static oracle.weblogic.kubernetes.TestConstants.OCIR_SECRET_NAME;
import static oracle.weblogic.kubernetes.TestConstants.WEBLOGIC_IMAGE_NAME;
import static oracle.weblogic.kubernetes.TestConstants.WEBLOGIC_IMAGE_TO_USE_IN_SPEC;
import static oracle.weblogic.kubernetes.actions.ActionConstants.MODEL_DIR;
import static oracle.weblogic.kubernetes.actions.ActionConstants.RESOURCE_DIR;
import static oracle.weblogic.kubernetes.actions.TestActions.deleteConfigMap;
import static oracle.weblogic.kubernetes.actions.TestActions.deletePersistentVolume;
import static oracle.weblogic.kubernetes.actions.TestActions.deletePersistentVolumeClaim;
import static oracle.weblogic.kubernetes.actions.TestActions.dockerTag;
import static oracle.weblogic.kubernetes.actions.TestActions.getDomainCustomResource;
import static oracle.weblogic.kubernetes.actions.TestActions.getServiceNodePort;
import static oracle.weblogic.kubernetes.actions.TestActions.scaleClusterWithRestApi;
import static oracle.weblogic.kubernetes.actions.impl.Domain.patchDomainCustomResource;
import static oracle.weblogic.kubernetes.assertions.TestAssertions.verifyRollingRestartOccurred;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.checkPodReadyAndServiceExists;
import static oracle.weblogic.kubernetes.utils.ConfigMapUtils.createConfigMapForDomainCreation;
import static oracle.weblogic.kubernetes.utils.DomainUtils.checkDomainStatusConditionTypeExists;
import static oracle.weblogic.kubernetes.utils.DomainUtils.checkDomainStatusConditionTypeHasExpectedStatus;
import static oracle.weblogic.kubernetes.utils.DomainUtils.createDomainAndVerify;
import static oracle.weblogic.kubernetes.utils.DomainUtils.deleteDomainResource;
import static oracle.weblogic.kubernetes.utils.DomainUtils.verifyDomainStatusConditionTypeDoesNotExist;
import static oracle.weblogic.kubernetes.utils.ImageUtils.createSecretForBaseImages;
import static oracle.weblogic.kubernetes.utils.ImageUtils.dockerLoginAndPushImageToRegistry;
import static oracle.weblogic.kubernetes.utils.JobUtils.createDomainJob;
import static oracle.weblogic.kubernetes.utils.OKDUtils.createRouteForOKD;
import static oracle.weblogic.kubernetes.utils.OKDUtils.setTlsTerminationForRoute;
import static oracle.weblogic.kubernetes.utils.OperatorUtils.installAndVerifyOperator;
import static oracle.weblogic.kubernetes.utils.PersistentVolumeUtils.createPV;
import static oracle.weblogic.kubernetes.utils.PersistentVolumeUtils.createPVC;
import static oracle.weblogic.kubernetes.utils.PodUtils.checkPodDoesNotExist;
import static oracle.weblogic.kubernetes.utils.PodUtils.getPodCreationTime;
import static oracle.weblogic.kubernetes.utils.PodUtils.setPodAntiAffinity;
import static oracle.weblogic.kubernetes.utils.SecretUtils.createSecretWithUsernamePassword;
import static oracle.weblogic.kubernetes.utils.ThreadSafeLogger.getLogger;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests related to Domain status conditions logged by operator.
 * The tests checks for the Completed/Available conditions for multiple usecases.
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DisplayName("Verify the domain status conditions for domain lifecycle")
@IntegrationTest
class ItDiagnosticsFailedCondition {

  private static String domainNamespace = null;

  final String adminServerName = "admin-server";
  final String adminServerPodName = domainUid + "-" + adminServerName;
  final String managedServerNameBase = "ms-";
  final String cluster1Name = "mycluster";
  String managedServerPodNamePrefix = domainUid + "-" + managedServerNameBase;
  final int managedServerPort = 8001;
  int replicaCount = 2;

  private static String adminSecretName;
  private static String encryptionSecretName;


  private static int externalRestHttpsPort = 0;
  private static final String domainUid = "diagnosticsdomain";
  private static final String pvName = domainUid + "-pv"; // name of the persistent volume
  private static final String pvcName = domainUid + "-pvc"; // name of the persistent volume claim
  private static String opServiceAccount = null;
  private static String opNamespace = null;

  private static LoggingFacade logger = null;

  /**
   * Assigns unique namespaces for operator and domains.
   * Pull WebLogic image if running tests in Kind cluster.
   * Installs operator.
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
    domainNamespace = namespaces.get(1);

    // set the service account name for the operator
    opServiceAccount = opNamespace + "-sa";

    // install and verify operator with REST API
    installAndVerifyOperator(opNamespace, opServiceAccount, true, 0, domainNamespace);
    externalRestHttpsPort = getServiceNodePort(opNamespace, "external-weblogic-operator-svc");

    // This test uses the operator restAPI to scale the domain. To do this in OKD cluster,
    // we need to expose the external service as route and set tls termination to  passthrough
    logger.info("Create a route for the operator external service - only for OKD");
    createRouteForOKD("external-weblogic-operator-svc", opNamespace);
    // Patch the route just created to set tls termination to passthrough
    setTlsTerminationForRoute("external-weblogic-operator-svc", opNamespace);

    // create pull secrets for WebLogic image when running in non Kind Kubernetes cluster
    // this secret is used only for non-kind cluster
    createSecretForBaseImages(domainNamespace);

    // create secret for admin credentials
    logger.info("Create secret for admin credentials");
    adminSecretName = "weblogic-credentials";
    createSecretWithUsernamePassword(adminSecretName, domainNamespace,
            ADMIN_USERNAME_DEFAULT, ADMIN_PASSWORD_DEFAULT);

    // create encryption secret
    logger.info("Create encryption secret");
    encryptionSecretName = "encryptionsecret";
    createSecretWithUsernamePassword(encryptionSecretName, domainNamespace,
            "weblogicenc", "weblogicenc");
  }

  /**
   * Test domain status condition with serverStartPolicy set to IF_NEEDED.
   * Verify the following conditions are generated:
   * type: Completed, status: true
   * type: Available, status: true
   * Verify no Failed type condition generated.
   */
  @Order(1)
  @Test
  @DisplayName("Test domain status condition with serverStartPolicy set to IF_NEEDED")
  void testBadModelFileStatus() {
    String imageName = TestConstants.MII_BASIC_IMAGE_NAME;
    String imageTag = "empty-domain-image";
    ItMiiDomainModelInPV.buildMIIandPushToRepo(imageName, imageTag, null);

    String badModelFileCm = "bad-model-in-cm";
    Path badModelFile = Paths.get(MODEL_DIR, "bad-model-file.yaml");
    final List<Path> modelList = Collections.singletonList(badModelFile);

    ConfigMapUtils.createConfigMapFromFiles(badModelFileCm, modelList, domainNamespace);
    logger.info("Creating domain with serverStartPolicy set to IF_NEEDED");

    try {
      // Test - test bad model file status with introspector failure
      Domain domain = createDomainResourceWithConfigMap(domainUid, domainNamespace, adminSecretName,
          OCIR_SECRET_NAME, encryptionSecretName, replicaCount, imageName + ":" + imageTag, badModelFileCm, 30L);

      createDomainAndVerify(domain, domainNamespace);
      // verify the condition type Failed exists
      checkDomainStatusConditionTypeExists(domainUid, domainNamespace, DOMAIN_STATUS_CONDITION_FAILED_TYPE);
      // verify the condition Failed type has status True
      checkDomainStatusConditionTypeHasExpectedStatus(domainUid, domainNamespace,
          DOMAIN_STATUS_CONDITION_FAILED_TYPE, "True");

      // Test - test incorrect secrets to pull images,
      // this test will pass if you set REPO_REGISTRY in local cluster runs.
      ImageUtils.createDockerRegistrySecret("foo", "bar", "foo@bar.com", OCIR_REGISTRY,
          "bad-pull-secret", domainNamespace);
      deleteDomainResource(domainUid, domainNamespace);

      domain = createDomainResourceWithConfigMap(domainUid, domainNamespace, adminSecretName,
          "bad-pull-secret", encryptionSecretName, replicaCount, imageName + ":" + imageTag, badModelFileCm, 30L);
      domain.getSpec().imagePullPolicy("ALWAYS");

      createDomainAndVerify(domain, domainNamespace);

      // verify the condition type Failed exists
      checkDomainStatusConditionTypeExists(domainUid, domainNamespace, DOMAIN_STATUS_CONDITION_FAILED_TYPE);
      // verify the condition Failed type has status True
      checkDomainStatusConditionTypeHasExpectedStatus(domainUid, domainNamespace,
          DOMAIN_STATUS_CONDITION_FAILED_TYPE, "True");

      // verify the condition type Completed exists
      checkDomainStatusConditionTypeExists(domainUid, domainNamespace, DOMAIN_STATUS_CONDITION_COMPLETED_TYPE);
      // verify the condition Completed type has status True
      checkDomainStatusConditionTypeHasExpectedStatus(domainUid, domainNamespace,
          DOMAIN_STATUS_CONDITION_COMPLETED_TYPE, "False");

      // verify the condition type Available exists
      checkDomainStatusConditionTypeExists(domainUid, domainNamespace, DOMAIN_STATUS_CONDITION_AVAILABLE_TYPE);
      // verify the condition Available type has status False
      checkDomainStatusConditionTypeHasExpectedStatus(domainUid, domainNamespace,
          DOMAIN_STATUS_CONDITION_AVAILABLE_TYPE, "False");

    } finally {
      deleteDomainResource(domainUid, domainNamespace);
      deleteConfigMap(badModelFileCm, domainNamespace);
    }
  }

  /**
   * Test domain status condition with serverStartPolicy set to IF_NEEDED. Verify the following conditions are
   * generated: type: Completed, status: true type: Available, status: true Verify no Failed type condition generated.
   */
  @Order(2)
  @Test
  @DisplayName("Test domain status condition with serverStartPolicy set to IF_NEEDED")
  void testBadApiVersionStatus() {
    String image = TestConstants.MII_BASIC_IMAGE_NAME + ":" + TestConstants.MII_BASIC_IMAGE_TAG;

    Domain domain = createDomainResource(domainUid, domainNamespace, adminSecretName,
        OCIR_SECRET_NAME, encryptionSecretName, replicaCount, image);
    domain.setApiVersion("weblogic.oracle/v1");

    try {
      createDomainAndVerify(domain, domainNamespace);

      // verify the condition type Failed exists
      checkDomainStatusConditionTypeExists(domainUid, domainNamespace, DOMAIN_STATUS_CONDITION_FAILED_TYPE);
      // verify the condition Failed type has status True
      checkDomainStatusConditionTypeHasExpectedStatus(domainUid, domainNamespace,
          DOMAIN_STATUS_CONDITION_FAILED_TYPE, "True");
    } finally {
      deleteDomainResource(domainUid, domainNamespace);
    }
  }


  /**
   * Test domain status condition with serverStartPolicy set to IF_NEEDED. Verify the following conditions are
   * generated: type: Completed, status: true type: Available, status: true Verify no Failed type condition generated.
   */
  @Order(3)
  @Test
  @DisplayName("Test domain status condition with serverStartPolicy set to IF_NEEDED")
  void testReplicasTooHigh() {
    String image = TestConstants.MII_BASIC_IMAGE_NAME + ":" + TestConstants.MII_BASIC_IMAGE_TAG;

    Domain domain = createDomainResource(domainUid, domainNamespace, adminSecretName,
        OCIR_SECRET_NAME, encryptionSecretName, 100, image);

    try {
      createDomainAndVerify(domain, domainNamespace);

      // verify the condition type Failed exists
      checkDomainStatusConditionTypeExists(domainUid, domainNamespace, DOMAIN_STATUS_CONDITION_FAILED_TYPE);
      // verify the condition Failed type has status True
      checkDomainStatusConditionTypeHasExpectedStatus(domainUid, domainNamespace,
          DOMAIN_STATUS_CONDITION_FAILED_TYPE, "True");

      // verify the condition type Completed exists
      checkDomainStatusConditionTypeExists(domainUid, domainNamespace, DOMAIN_STATUS_CONDITION_COMPLETED_TYPE);
      // verify the condition Completed type has status True
      checkDomainStatusConditionTypeHasExpectedStatus(domainUid, domainNamespace,
          DOMAIN_STATUS_CONDITION_COMPLETED_TYPE, "False");

      // verify the condition type Available exists
      checkDomainStatusConditionTypeExists(domainUid, domainNamespace, DOMAIN_STATUS_CONDITION_AVAILABLE_TYPE);
      // verify the condition Available type has status False
      checkDomainStatusConditionTypeHasExpectedStatus(domainUid, domainNamespace,
          DOMAIN_STATUS_CONDITION_AVAILABLE_TYPE, "False");

    } finally {
      deleteDomainResource(domainUid, domainNamespace);
    }
  }

  /**
   * Test domain status condition with serverStartPolicy set to IF_NEEDED. Verify the following conditions are
   * generated: type: Completed, status: true type: Available, status: true Verify no Failed type condition generated.
   */
  @Order(4)
  @Test
  @DisplayName("Test domain status condition with serverStartPolicy set to IF_NEEDED")
  void testImageDoesnotExist() {
    String image = TestConstants.MII_BASIC_IMAGE_NAME + ":non-existing";

    Domain domain = createDomainResource(domainUid, domainNamespace, adminSecretName,
        OCIR_SECRET_NAME, encryptionSecretName, 100, image);

    try {
      createDomainAndVerify(domain, domainNamespace);

      // verify the condition type Failed exists
      checkDomainStatusConditionTypeExists(domainUid, domainNamespace, DOMAIN_STATUS_CONDITION_FAILED_TYPE);
      // verify the condition Failed type has status True
      checkDomainStatusConditionTypeHasExpectedStatus(domainUid, domainNamespace,
          DOMAIN_STATUS_CONDITION_FAILED_TYPE, "True");

      // verify the condition type Completed exists
      checkDomainStatusConditionTypeExists(domainUid, domainNamespace, DOMAIN_STATUS_CONDITION_COMPLETED_TYPE);
      // verify the condition Completed type has status True
      checkDomainStatusConditionTypeHasExpectedStatus(domainUid, domainNamespace,
          DOMAIN_STATUS_CONDITION_COMPLETED_TYPE, "False");

      // verify the condition type Available exists
      checkDomainStatusConditionTypeExists(domainUid, domainNamespace, DOMAIN_STATUS_CONDITION_AVAILABLE_TYPE);
      // verify the condition Available type has status False
      checkDomainStatusConditionTypeHasExpectedStatus(domainUid, domainNamespace,
          DOMAIN_STATUS_CONDITION_AVAILABLE_TYPE, "False");

    } finally {
      deleteDomainResource(domainUid, domainNamespace);
    }
  }

  /**
   * Test domain status condition with serverStartPolicy set to IF_NEEDED. Verify the following conditions are
   * generated: type: Completed, status: true type: Available, status: true Verify no Failed type condition generated.
   */
  @Order(4)
  @Test
  @DisplayName("Test domain status condition with serverStartPolicy set to IF_NEEDED")
  void testImagePullSecretDoesnotExist() {
    String image = TestConstants.MII_BASIC_IMAGE_NAME + ":" + TestConstants.MII_BASIC_IMAGE_TAG;

    Domain domain = createDomainResource(domainUid, domainNamespace, adminSecretName,
        OCIR_SECRET_NAME + "bad", encryptionSecretName, 100, image);

    try {
      createDomainAndVerify(domain, domainNamespace);

      // verify the condition type Failed exists
      checkDomainStatusConditionTypeExists(domainUid, domainNamespace, DOMAIN_STATUS_CONDITION_FAILED_TYPE);
      // verify the condition Failed type has status True
      checkDomainStatusConditionTypeHasExpectedStatus(domainUid, domainNamespace,
          DOMAIN_STATUS_CONDITION_FAILED_TYPE, "True");

      // verify the condition type Completed exists
      checkDomainStatusConditionTypeExists(domainUid, domainNamespace, DOMAIN_STATUS_CONDITION_COMPLETED_TYPE);
      // verify the condition Completed type has status True
      checkDomainStatusConditionTypeHasExpectedStatus(domainUid, domainNamespace,
          DOMAIN_STATUS_CONDITION_COMPLETED_TYPE, "False");

      // verify the condition type Available exists
      checkDomainStatusConditionTypeExists(domainUid, domainNamespace, DOMAIN_STATUS_CONDITION_AVAILABLE_TYPE);
      // verify the condition Available type has status False
      checkDomainStatusConditionTypeHasExpectedStatus(domainUid, domainNamespace,
          DOMAIN_STATUS_CONDITION_AVAILABLE_TYPE, "False");

    } finally {
      deleteDomainResource(domainUid, domainNamespace);
    }
  }

  /**
   * Test domain status condition with serverStartPolicy set to IF_NEEDED. Verify the following conditions are
   * generated: type: Completed, status: true type: Available, status: true Verify no Failed type condition generated.
   */
  @Order(4)
  @Test
  @DisplayName("Test domain status condition with serverStartPolicy set to IF_NEEDED")
  void testIncorrectImagePullSecret() {
    String image = WEBLOGIC_IMAGE_NAME + ":nonexistent";
    ImageUtils.createDockerRegistrySecret("foo", "bar", "foo@bar.com", OCIR_REGISTRY,
        "bad-pull-secret", domainNamespace);

    Domain domain = createDomainResource(domainUid, domainNamespace, adminSecretName,
        "bad-pull-secret", encryptionSecretName, replicaCount, image);
    domain.getSpec().imagePullPolicy("Always");

    try {
      createDomainAndVerify(domain, domainNamespace);

      // verify the condition type Failed exists
      checkDomainStatusConditionTypeExists(domainUid, domainNamespace, DOMAIN_STATUS_CONDITION_FAILED_TYPE);
      // verify the condition Failed type has status True
      checkDomainStatusConditionTypeHasExpectedStatus(domainUid, domainNamespace,
          DOMAIN_STATUS_CONDITION_FAILED_TYPE, "True");

      // verify the condition type Completed exists
      checkDomainStatusConditionTypeExists(domainUid, domainNamespace, DOMAIN_STATUS_CONDITION_COMPLETED_TYPE);
      // verify the condition Completed type has status True
      checkDomainStatusConditionTypeHasExpectedStatus(domainUid, domainNamespace,
          DOMAIN_STATUS_CONDITION_COMPLETED_TYPE, "False");

      // verify the condition type Available exists
      checkDomainStatusConditionTypeExists(domainUid, domainNamespace, DOMAIN_STATUS_CONDITION_AVAILABLE_TYPE);
      // verify the condition Available type has status False
      checkDomainStatusConditionTypeHasExpectedStatus(domainUid, domainNamespace,
          DOMAIN_STATUS_CONDITION_AVAILABLE_TYPE, "False");

    } finally {
      deleteDomainResource(domainUid, domainNamespace);
    }
  }

  /**
   * Test domain status condition with serverStartPolicy set to ADMIN_ONLY.
   * Verify the following conditions are generated:
   * type: Completed, status: true
   * type: Available, status: true
   * Verify no Failed type condition generated.
   */
  @Disabled
  @Order(2)
  @Test
  @DisplayName("Test domain events for various successful domain life cycle changes")
  void testCompleteAvailableConditionWithAdminOnly() {

    logger.info("patch the domain and change the serverStartPolicy to ADMIN_ONLY");

    String patchStr
        = "[{\"op\": \"replace\",\"path\": \"/spec/serverStartPolicy\", \"value\": \"ADMIN_ONLY\"}]";

    logger.info("Updating domain configuration using patch string: {0}\n", patchStr);
    V1Patch patch = new V1Patch(patchStr);
    assertTrue(patchDomainCustomResource(domainUid, domainNamespace, patch, V1Patch.PATCH_FORMAT_JSON_PATCH),
        "Failed to patch domain");

    logger.info("Checking for admin server pod is up and running");
    checkPodReadyAndServiceExists(adminServerPodName, domainUid, domainNamespace);

    // verify all the managed servers are shutdown
    logger.info("Checking managed server pods were shutdown");
    for (int i = 1; i <= replicaCount; i++) {
      checkPodDoesNotExist(managedServerPodNamePrefix + i, domainUid, domainNamespace);
    }

    // verify the condition type Completed exists
    checkDomainStatusConditionTypeExists(domainUid, domainNamespace, DOMAIN_STATUS_CONDITION_COMPLETED_TYPE);
    // verify the condition type Available exists
    checkDomainStatusConditionTypeExists(domainUid, domainNamespace, DOMAIN_STATUS_CONDITION_AVAILABLE_TYPE);
    // verify the condition Completed type has status True
    checkDomainStatusConditionTypeHasExpectedStatus(domainUid, domainNamespace,
        DOMAIN_STATUS_CONDITION_COMPLETED_TYPE, "True");
    // verify the condition Available type has status True
    checkDomainStatusConditionTypeHasExpectedStatus(domainUid, domainNamespace,
        DOMAIN_STATUS_CONDITION_AVAILABLE_TYPE, "False");
    // verify there is no status condition type Failed
    verifyDomainStatusConditionTypeDoesNotExist(assertDoesNotThrow(() ->
        getDomainCustomResource(domainUid, domainNamespace)),
        DOMAIN_STATUS_CONDITION_FAILED_TYPE);
  }

  /**
   * Test domain status condition with serverStartPolicy set to NEVER.
   * Verify all the servers will be shutdown.
   * Verify the following conditions are generated:
   * type: Completed, status: true
   * type: Available, status: False
   * Verify no Failed type condition generated.
   */
  @Disabled
  @Order(3)
  @Test
  @DisplayName("Test domain status condition with serverStartPolicy set to NEVER")
  void testCompleteAvailableConditionWithNever() {
    logger.info("patch the domain resource with serverStartPolicy set to NEVER");
    String patchStr
        = "[{\"op\": \"replace\",\"path\": \"/spec/serverStartPolicy\", \"value\": \"NEVER\"}]";

    logger.info("Updating domain configuration using patch string: {0}\n", patchStr);
    V1Patch patch = new V1Patch(patchStr);
    assertTrue(patchDomainCustomResource(domainUid, domainNamespace, patch, V1Patch.PATCH_FORMAT_JSON_PATCH),
        "Failed to patch domain");

    // verify all the servers are shutdown
    logger.info("Checking for admin server pod shutdown");
    checkPodDoesNotExist(adminServerPodName, domainUid, domainNamespace);
    logger.info("Checking managed server pods were shutdown");
    for (int i = 1; i <= replicaCount; i++) {
      checkPodDoesNotExist(managedServerPodNamePrefix + i, domainUid, domainNamespace);
    }

    // verify the condition type Completed exists
    checkDomainStatusConditionTypeExists(domainUid, domainNamespace, DOMAIN_STATUS_CONDITION_COMPLETED_TYPE);
    // verify the condition type Available exists
    checkDomainStatusConditionTypeExists(domainUid, domainNamespace, DOMAIN_STATUS_CONDITION_AVAILABLE_TYPE);
    // verify the condition Completed type has status True
    checkDomainStatusConditionTypeHasExpectedStatus(domainUid, domainNamespace,
        DOMAIN_STATUS_CONDITION_COMPLETED_TYPE, "True");
    // verify the condition Available type has status True
    checkDomainStatusConditionTypeHasExpectedStatus(domainUid, domainNamespace,
        DOMAIN_STATUS_CONDITION_AVAILABLE_TYPE, "False");
    // verify there is no status condition type Failed
    verifyDomainStatusConditionTypeDoesNotExist(assertDoesNotThrow(() ->
        getDomainCustomResource(domainUid, domainNamespace)),
        DOMAIN_STATUS_CONDITION_FAILED_TYPE);
  }

  /**
   * Test domain status condition with cluster replica set to zero and min-replicas set to zero.
   * Verify all the cluster servers pods will be shutdown.
   * Verify the following conditions are generated:
   * type: Completed, status: true
   * type: Available, status: False
   * Verify no Failed type condition generated.
   */
  @Disabled
  @Order(4)
  @Test
  @DisplayName("Test domain status condition with cluster replica set to zero and min-replicas set to zero")
  void testCompleteAvailableConditionWithReplicaZero() {

    logger.info("patch the domain resource with new cluster replica 0");
    String patchStr
        = "[{\"op\": \"replace\",\"path\": \"/spec/clusters/0/replicas\", \"value\": 0}]";

    logger.info("Updating domain configuration using patch string: {0}\n", patchStr);
    V1Patch patch = new V1Patch(patchStr);
    assertTrue(patchDomainCustomResource(domainUid, domainNamespace, patch, V1Patch.PATCH_FORMAT_JSON_PATCH),
        "Failed to patch domain");

    // verify the admin server service exists
    checkPodReadyAndServiceExists(adminServerPodName, domainUid, domainNamespace);

    // verify the cluster server pods are shutdown
    logger.info("Checking managed server pods were shutdown");
    for (int i = 1; i <= replicaCount; i++) {
      checkPodDoesNotExist(managedServerPodNamePrefix + i, domainUid, domainNamespace);
    }

    // verify the condition type Completed exists
    checkDomainStatusConditionTypeExists(domainUid, domainNamespace, DOMAIN_STATUS_CONDITION_COMPLETED_TYPE);
    // verify the condition type Available exists
    checkDomainStatusConditionTypeExists(domainUid, domainNamespace, DOMAIN_STATUS_CONDITION_AVAILABLE_TYPE);
    // verify the condition Completed type has status True
    checkDomainStatusConditionTypeHasExpectedStatus(domainUid, domainNamespace,
        DOMAIN_STATUS_CONDITION_COMPLETED_TYPE, "True");
    // verify the condition Available type has status True
    checkDomainStatusConditionTypeHasExpectedStatus(domainUid, domainNamespace,
        DOMAIN_STATUS_CONDITION_AVAILABLE_TYPE, "False");
    // verify there is no status condition type Failed
    verifyDomainStatusConditionTypeDoesNotExist(assertDoesNotThrow(() ->
        getDomainCustomResource(domainUid, domainNamespace)),
        DOMAIN_STATUS_CONDITION_FAILED_TYPE);
  }

  /**
   * Test domain status condition with cluster serverStartPolicy to NEVER.
   * Verify all the cluster servers pods will be shutdown.
   * Verify the following conditions are generated:
   * type: Completed, status: true
   * type: Available, status: False
   * Verify no Failed type condition generated.
   */
  @Disabled
  @Order(5)
  @Test
  @DisplayName("Test domain status condition with cluster serverStartPolicy to NEVER")
  void testCompleteAvailableConditionWithClusterNever() {

    logger.info("patch the domain resource with cluster serverStartPolicy to NEVER");
    String patchStr
        = "[{\"op\": \"replace\",\"path\": \"/spec/clusters/0/serverStartPolicy\", \"value\": \"NEVER\"}]";
    logger.info("Updating domain configuration using patch string: {0}\n", patchStr);
    V1Patch patch = new V1Patch(patchStr);
    assertTrue(patchDomainCustomResource(domainUid, domainNamespace, patch, V1Patch.PATCH_FORMAT_JSON_PATCH),
        "Failed to patch domain");

    // verify the admin server service exists
    checkPodReadyAndServiceExists(adminServerPodName, domainUid, domainNamespace);

    // verify the cluster server pods are shutdown
    logger.info("Checking managed server pods were shutdown");
    for (int i = 1; i <= replicaCount; i++) {
      checkPodDoesNotExist(managedServerPodNamePrefix + i, domainUid, domainNamespace);
    }

    // verify the condition type Completed exists
    checkDomainStatusConditionTypeExists(domainUid, domainNamespace, DOMAIN_STATUS_CONDITION_COMPLETED_TYPE);
    // verify the condition type Available exists
    checkDomainStatusConditionTypeExists(domainUid, domainNamespace, DOMAIN_STATUS_CONDITION_AVAILABLE_TYPE);
    // verify the condition Completed type has status True
    checkDomainStatusConditionTypeHasExpectedStatus(domainUid, domainNamespace,
        DOMAIN_STATUS_CONDITION_COMPLETED_TYPE, "True");
    // verify the condition Available type has status True
    checkDomainStatusConditionTypeHasExpectedStatus(domainUid, domainNamespace,
        DOMAIN_STATUS_CONDITION_AVAILABLE_TYPE, "False");
    // verify there is no status condition type Failed
    verifyDomainStatusConditionTypeDoesNotExist(assertDoesNotThrow(() ->
        getDomainCustomResource(domainUid, domainNamespace)),
        DOMAIN_STATUS_CONDITION_FAILED_TYPE);
  }

  /**
   * Test domain status condition with cluster replica set to larger than max size of cluster.
   * Verify all the cluster servers pods will be up and running.
   * Verify the following conditions are generated:
   * type: Completed, status: false
   * type: Available, status: true
   * Verify no Failed type condition generated.
   */
  @Disabled
  @Order(6)
  @Test
  @DisplayName("Test domain status condition with cluster replica set to larger than max size of cluster")
  void testCompleteAvailableConditionWithReplicaExceedMaxSize() {

    logger.info("patch the domain resource with replica larger than max size of cluster");
    int newReplicaCount = replicaCount + 1;
    String patchStr
        = "[{\"op\": \"replace\",\"path\": \"/spec/clusters/0/replicas\", \"value\": " + newReplicaCount + "}]";

    logger.info("Updating domain configuration using patch string: {0}\n", patchStr);
    V1Patch patch = new V1Patch(patchStr);
    assertTrue(patchDomainCustomResource(domainUid, domainNamespace, patch, V1Patch.PATCH_FORMAT_JSON_PATCH),
        "Failed to patch domain");

    // verify the admin server service exists
    checkPodReadyAndServiceExists(adminServerPodName, domainUid, domainNamespace);

    // verify the cluster server pods are up and running
    logger.info("Checking managed server pods were ready");
    for (int i = 1; i <= replicaCount; i++) {
      checkPodReadyAndServiceExists(managedServerPodNamePrefix + i, domainUid, domainNamespace);
    }

    // verify there is no pod created larger than max size of cluster
    for (int i = replicaCount + 1; i <= newReplicaCount; i++) {
      checkPodDoesNotExist(managedServerPodNamePrefix + i, domainUid, domainNamespace);
    }

    // verify the condition type Completed exists
    checkDomainStatusConditionTypeExists(domainUid, domainNamespace, DOMAIN_STATUS_CONDITION_COMPLETED_TYPE);
    // verify the condition type Available exists
    checkDomainStatusConditionTypeExists(domainUid, domainNamespace, DOMAIN_STATUS_CONDITION_AVAILABLE_TYPE);
    // verify the condition Completed type has status True
    checkDomainStatusConditionTypeHasExpectedStatus(domainUid, domainNamespace,
        DOMAIN_STATUS_CONDITION_COMPLETED_TYPE, "false");
    // verify the condition Available type has status True
    checkDomainStatusConditionTypeHasExpectedStatus(domainUid, domainNamespace,
        DOMAIN_STATUS_CONDITION_AVAILABLE_TYPE, "true");
    // verify there is no status condition type Failed
    verifyDomainStatusConditionTypeDoesNotExist(assertDoesNotThrow(() ->
        getDomainCustomResource(domainUid, domainNamespace)),
        DOMAIN_STATUS_CONDITION_FAILED_TYPE);
  }

  /**
   * Test domain status condition with cluster replica set to less than max size of cluster.
   * Verify all the cluster servers pods will be up and running.
   * Verify the following conditions are generated:
   * type: Completed, status: true
   * type: Available, status: true
   * Verify no Failed type condition generated.
   */
  @Order(7)
  //@Test
  @DisplayName("Test domain status condition with cluster replica set to less than max size of cluster")
  void testCompleteAvailableConditionWithReplicaLessThanMaxSize() {

    logger.info("patch the domain resource with replica less than max size of cluster");
    int newReplicaCount = replicaCount - 1;
    String patchStr
        = "[{\"op\": \"replace\",\"path\": \"/spec/clusters/0/replicas\", \"value\": " + newReplicaCount + "}]";

    logger.info("Updating domain configuration using patch string: {0}\n", patchStr);
    V1Patch patch = new V1Patch(patchStr);
    assertTrue(patchDomainCustomResource(domainUid, domainNamespace, patch, V1Patch.PATCH_FORMAT_JSON_PATCH),
        "Failed to patch domain");

    // verify the admin server service exists
    checkPodReadyAndServiceExists(adminServerPodName, domainUid, domainNamespace);

    // verify the cluster server pods are up and running
    logger.info("Checking managed server pods were ready");
    for (int i = 1; i <= newReplicaCount; i++) {
      checkPodReadyAndServiceExists(managedServerPodNamePrefix + i, domainUid, domainNamespace);
    }

    // verify there is no pod created larger than replicas
    for (int i = newReplicaCount + 1; i <= replicaCount; i++) {
      checkPodDoesNotExist(managedServerPodNamePrefix + i, domainUid, domainNamespace);
    }

    // verify the condition type Completed exists
    checkDomainStatusConditionTypeExists(domainUid, domainNamespace, DOMAIN_STATUS_CONDITION_COMPLETED_TYPE);
    // verify the condition type Available exists
    checkDomainStatusConditionTypeExists(domainUid, domainNamespace, DOMAIN_STATUS_CONDITION_AVAILABLE_TYPE);
    // verify the condition Completed type has status True
    checkDomainStatusConditionTypeHasExpectedStatus(domainUid, domainNamespace,
        DOMAIN_STATUS_CONDITION_COMPLETED_TYPE, "True");
    // verify the condition Available type has status True
    checkDomainStatusConditionTypeHasExpectedStatus(domainUid, domainNamespace,
        DOMAIN_STATUS_CONDITION_AVAILABLE_TYPE, "True");
    // verify there is no status condition type Failed
    verifyDomainStatusConditionTypeDoesNotExist(assertDoesNotThrow(() ->
        getDomainCustomResource(domainUid, domainNamespace)),
        DOMAIN_STATUS_CONDITION_FAILED_TYPE);
  }

  /**
   * Test domain status condition with scaling up and down of cluster.
   * Verify all the cluster servers pods will be up and running.
   * Verify the following conditions are generated:
   * type: Completed, status: true
   * type: Available, status: true
   * Verify no Failed type condition generated.
   */
  @Order(8)
  //@Test
  @DisplayName("Test domain status condition with scaling up and down of cluster")
  void testCompleteAvailableConditionWithScaleUpDownCluster() {

    // scale down the cluster
    int newReplicaCount = 1;
    assertDoesNotThrow(() ->
        scaleClusterWithRestApi(domainUid, cluster1Name, 1, externalRestHttpsPort, opNamespace, opServiceAccount));

    // verify the admin server service exists
    checkPodReadyAndServiceExists(adminServerPodName, domainUid, domainNamespace);

    // verify the cluster server pods are up and running
    logger.info("Checking managed server pods were ready");
    for (int i = 1; i <= newReplicaCount; i++) {
      checkPodReadyAndServiceExists(managedServerPodNamePrefix + i, domainUid, domainNamespace);
    }

    // verify there is no pod created larger than replicas
    for (int i = newReplicaCount + 1; i <= replicaCount; i++) {
      checkPodDoesNotExist(managedServerPodNamePrefix + i, domainUid, domainNamespace);
    }

    // verify the condition type Completed exists
    checkDomainStatusConditionTypeExists(domainUid, domainNamespace, DOMAIN_STATUS_CONDITION_COMPLETED_TYPE);
    // verify the condition type Available exists
    checkDomainStatusConditionTypeExists(domainUid, domainNamespace, DOMAIN_STATUS_CONDITION_AVAILABLE_TYPE);
    // verify the condition Completed type has status True
    checkDomainStatusConditionTypeHasExpectedStatus(domainUid, domainNamespace,
        DOMAIN_STATUS_CONDITION_COMPLETED_TYPE, "True");
    // verify the condition Available type has status True
    checkDomainStatusConditionTypeHasExpectedStatus(domainUid, domainNamespace,
        DOMAIN_STATUS_CONDITION_AVAILABLE_TYPE, "True");
    // verify there is no status condition type Failed
    verifyDomainStatusConditionTypeDoesNotExist(assertDoesNotThrow(() ->
        getDomainCustomResource(domainUid, domainNamespace)),
        DOMAIN_STATUS_CONDITION_FAILED_TYPE);

    // scale up the cluster
    newReplicaCount = 2;
    assertDoesNotThrow(() ->
        scaleClusterWithRestApi(domainUid, cluster1Name, 2, externalRestHttpsPort, opNamespace, opServiceAccount));


    // verify the admin server service exists
    checkPodReadyAndServiceExists(adminServerPodName, domainUid, domainNamespace);

    // verify the cluster server pods are up and running
    logger.info("Checking managed server pods were ready");
    for (int i = 1; i <= newReplicaCount; i++) {
      checkPodReadyAndServiceExists(managedServerPodNamePrefix + i, domainUid, domainNamespace);
    }

    // verify the condition type Completed exists
    checkDomainStatusConditionTypeExists(domainUid, domainNamespace, DOMAIN_STATUS_CONDITION_COMPLETED_TYPE);
    // verify the condition type Available exists
    checkDomainStatusConditionTypeExists(domainUid, domainNamespace, DOMAIN_STATUS_CONDITION_AVAILABLE_TYPE);
    // verify the condition Completed type has status True
    checkDomainStatusConditionTypeHasExpectedStatus(domainUid, domainNamespace,
        DOMAIN_STATUS_CONDITION_COMPLETED_TYPE, "True");
    // verify the condition Available type has status True
    checkDomainStatusConditionTypeHasExpectedStatus(domainUid, domainNamespace,
        DOMAIN_STATUS_CONDITION_AVAILABLE_TYPE, "True");
    // verify there is no status condition type Failed
    verifyDomainStatusConditionTypeDoesNotExist(assertDoesNotThrow(() ->
        getDomainCustomResource(domainUid, domainNamespace)),
        DOMAIN_STATUS_CONDITION_FAILED_TYPE);
  }

  /**
   * Test domain status condition with new restartVersion.
   * Verify all the cluster servers pods will be up and running.
   * Verify the following conditions are generated:
   * type: Completed, status: true
   * type: Available, status: true
   * Verify no Failed type condition generated.
   */
  @Order(9)
  //@Test
  @DisplayName("Test domain status condition with new restartVersion")
  void testCompleteAvailableConditionWithNewRestartVersion() {

    // get the pod creation time stamps
    LinkedHashMap<String, OffsetDateTime> pods = new LinkedHashMap<>();
    // get the creation time of the admin server pod before patching
    OffsetDateTime adminPodCreationTime = getPodCreationTime(domainNamespace, adminServerPodName);
    pods.put(adminServerPodName, adminPodCreationTime);
    // get the creation time of the managed server pods before patching
    for (int i = 1; i <= replicaCount; i++) {
      pods.put(managedServerPodNamePrefix + i,
          getPodCreationTime(domainNamespace, managedServerPodNamePrefix + i));
    }

    logger.info("patch the domain resource with new restartVersion");
    String patchStr
        = "[{\"op\": \"add\",\"path\": \"/spec/restartVersion\", \"value\": \"9\"}]";

    logger.info("Updating domain configuration using patch string: {0}\n", patchStr);
    V1Patch patch = new V1Patch(patchStr);
    assertTrue(patchDomainCustomResource(domainUid, domainNamespace, patch, V1Patch.PATCH_FORMAT_JSON_PATCH),
        "Failed to patch domain");

    // check the domain is restarted
    verifyRollingRestartOccurred(pods, 1, domainNamespace);

    // verify the admin server service exists
    checkPodReadyAndServiceExists(adminServerPodName, domainUid, domainNamespace);

    // verify the cluster server pods are up and running
    logger.info("Checking managed server pods were ready");
    for (int i = 1; i <= replicaCount; i++) {
      checkPodReadyAndServiceExists(managedServerPodNamePrefix + i, domainUid, domainNamespace);
    }

    // verify the condition type Completed exists
    checkDomainStatusConditionTypeExists(domainUid, domainNamespace, DOMAIN_STATUS_CONDITION_COMPLETED_TYPE);
    // verify the condition type Available exists
    checkDomainStatusConditionTypeExists(domainUid, domainNamespace, DOMAIN_STATUS_CONDITION_AVAILABLE_TYPE);
    // verify the condition Completed type has status True
    checkDomainStatusConditionTypeHasExpectedStatus(domainUid, domainNamespace,
        DOMAIN_STATUS_CONDITION_COMPLETED_TYPE, "True");
    // verify the condition Available type has status True
    checkDomainStatusConditionTypeHasExpectedStatus(domainUid, domainNamespace,
        DOMAIN_STATUS_CONDITION_AVAILABLE_TYPE, "True");
    // verify there is no status condition type Failed
    verifyDomainStatusConditionTypeDoesNotExist(assertDoesNotThrow(() ->
        getDomainCustomResource(domainUid, domainNamespace)),
        DOMAIN_STATUS_CONDITION_FAILED_TYPE);
  }

  /**
   * Test domain status condition with new image.
   * Verify all the servers pods are restarted.
   * Verify the following conditions are generated:
   * type: Completed, status: true
   * type: Available, status: true
   * Verify no Failed type condition generated.
   */
  @Order(10)
  @Test
  @DisplayName("Test domain status condition with new restartVersion")
  void testCompleteAvailableConditionWithNewImage() {

    // get the pod creation time stamps
    LinkedHashMap<String, OffsetDateTime> pods = new LinkedHashMap<>();
    // get the creation time of the admin server pod before patching
    OffsetDateTime adminPodCreationTime = getPodCreationTime(domainNamespace, adminServerPodName);
    pods.put(adminServerPodName, adminPodCreationTime);
    // get the creation time of the managed server pods before patching
    for (int i = 1; i <= replicaCount; i++) {
      pods.put(managedServerPodNamePrefix + i,
          getPodCreationTime(domainNamespace, managedServerPodNamePrefix + i));
    }

    Domain domain = assertDoesNotThrow(() -> getDomainCustomResource(domainUid, domainNamespace));
    //print out the original image name
    String imageName = domain.getSpec().getImage();
    logger.info("Currently the image name used for the domain is: {0}", imageName);

    //change image name to imageUpdate
    String imageTag = CommonTestUtils.getDateAndTimeStamp();
    String imageUpdate = KIND_REPO != null ? KIND_REPO
        + (WEBLOGIC_IMAGE_NAME + ":" + imageTag).substring(TestConstants.BASE_IMAGES_REPO.length() + 1)
        : WEBLOGIC_IMAGE_NAME + ":" + imageTag;
    dockerTag(imageName, imageUpdate);
    dockerLoginAndPushImageToRegistry(imageUpdate);

    logger.info("patch the domain resource with new image");
    String patchStr
        = "[{\"op\": \"replace\",\"path\": \"/spec/image\", \"value\": \"" + imageUpdate + "\"}]";

    logger.info("Updating domain configuration using patch string: {0}\n", patchStr);
    V1Patch patch = new V1Patch(patchStr);
    assertTrue(patchDomainCustomResource(domainUid, domainNamespace, patch, V1Patch.PATCH_FORMAT_JSON_PATCH),
        "Failed to patch domain");

    // check the domain is restarted
    verifyRollingRestartOccurred(pods, 1, domainNamespace);

    // verify the admin server service exists
    checkPodReadyAndServiceExists(adminServerPodName, domainUid, domainNamespace);

    // verify the cluster server pods are up and running
    logger.info("Checking managed server pods were ready");
    for (int i = 1; i <= replicaCount; i++) {
      checkPodReadyAndServiceExists(managedServerPodNamePrefix + i, domainUid, domainNamespace);
    }

    // verify the condition type Completed exists
    checkDomainStatusConditionTypeExists(domainUid, domainNamespace, DOMAIN_STATUS_CONDITION_COMPLETED_TYPE);
    // verify the condition type Available exists
    checkDomainStatusConditionTypeExists(domainUid, domainNamespace, DOMAIN_STATUS_CONDITION_AVAILABLE_TYPE);
    // verify the condition Completed type has status True
    checkDomainStatusConditionTypeHasExpectedStatus(domainUid, domainNamespace,
        DOMAIN_STATUS_CONDITION_COMPLETED_TYPE, "True");
    // verify the condition Available type has status True
    checkDomainStatusConditionTypeHasExpectedStatus(domainUid, domainNamespace,
        DOMAIN_STATUS_CONDITION_AVAILABLE_TYPE, "True");
    // verify there is no status condition type Failed
    verifyDomainStatusConditionTypeDoesNotExist(assertDoesNotThrow(() ->
        getDomainCustomResource(domainUid, domainNamespace)),
        DOMAIN_STATUS_CONDITION_FAILED_TYPE);
  }

  /**
   * Cleanup the persistent volume and persistent volume claim used by the test.
   */
  @AfterAll
  public static void tearDown() {
    if (System.getenv("SKIP_CLEANUP") == null
        || (System.getenv("SKIP_CLEANUP") != null
        && System.getenv("SKIP_CLEANUP").equalsIgnoreCase("false"))) {
      deletePersistentVolumeClaim(pvcName, domainNamespace);
      deletePersistentVolume(pvName);
    }
  }

  // Create a domain resource with a custom ConfigMap
  private Domain createDomainResourceWithConfigMap(String domainUid,
          String domNamespace, String adminSecretName,
          String repoSecretName, String encryptionSecretName,
          int replicaCount, String miiImage, String configmapName, Long introspectorDeadline) {

    Map keyValueMap = new HashMap<String, String>();
    keyValueMap.put("testkey", "testvalue");

    // create the domain CR
    Domain domain = new Domain()
        .apiVersion(DOMAIN_API_VERSION)
        .kind("Domain")
        .metadata(new V1ObjectMeta()
            .name(domainUid)
            .namespace(domNamespace))
        .spec(new DomainSpec()
            .domainUid(domainUid)
            .domainHomeSourceType("FromModel")
            .image(miiImage)
            .addImagePullSecretsItem(new V1LocalObjectReference()
                .name(repoSecretName))
            .webLogicCredentialsSecret(new V1SecretReference()
                .name(adminSecretName)
                .namespace(domNamespace))
            .includeServerOutInPodLog(true)
            .serverStartPolicy("IF_NEEDED")
            .serverPod(new ServerPod()
                .addEnvItem(new V1EnvVar()
                    .name("JAVA_OPTIONS")
                    .value("-Dweblogic.security.SSL.ignoreHostnameVerification=true"))
                .addEnvItem(new V1EnvVar()
                    .name("USER_MEM_ARGS")
                    .value("-Djava.security.egd=file:/dev/./urandom ")))
            .adminServer(new AdminServer()
                .serverStartState("RUNNING")
                .serverService(new ServerService()
                    .annotations(keyValueMap)
                    .labels(keyValueMap))
                .adminService(new AdminService()
                    .addChannelsItem(new Channel()
                        .channelName("default")
                        .nodePort(0))))
            .addClustersItem(new Cluster()
                .clusterName("cluster-1")
                .replicas(replicaCount)
                .serverStartState("RUNNING"))
            .configuration(new Configuration()
                .model(new Model()
                    .domainType("WLS")
                    .configMap(configmapName)
                    .runtimeEncryptionSecret(encryptionSecretName))
                .introspectorJobActiveDeadlineSeconds(introspectorDeadline != null ? introspectorDeadline : 300L)));
    setPodAntiAffinity(domain);
    return domain;
  }

  // Create and start a WebLogic domain in PV
  private void createDomain() {

    final String wlSecretName = "weblogic-credentials";

    // create WebLogic domain credential secret
    createSecretWithUsernamePassword(wlSecretName, domainNamespace,
        ADMIN_USERNAME_DEFAULT, ADMIN_PASSWORD_DEFAULT);

    // create persistent volume and persistent volume claim for domain
    // these resources should be labeled with domainUid for cleanup after testing
    createPV(pvName, domainUid, this.getClass().getSimpleName());
    createPVC(pvName, pvcName, domainUid, domainNamespace);

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
        pvName, pvcName, domainNamespace);

    // create a domain custom resource configuration object
    logger.info("Creating domain custom resource");
    Domain domain = new Domain()
        .apiVersion(DOMAIN_API_VERSION)
        .kind("Domain")
        .metadata(new V1ObjectMeta()
            .name(domainUid)
            .namespace(domainNamespace))
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
                .namespace(domainNamespace))
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
    createDomainAndVerify(domain, domainNamespace);

    // verify the admin server service created
    checkPodReadyAndServiceExists(adminServerPodName, domainUid, domainNamespace);

    // verify managed server services created
    for (int i = 1; i <= replicaCount; i++) {
      logger.info("Checking managed server service/pod {0} is created in namespace {1}",
          managedServerPodNamePrefix + i, domainNamespace);
      checkPodReadyAndServiceExists(managedServerPodNamePrefix + i, domainUid, domainNamespace);
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

  private static void createImageWithEmptyModel(String imageName, String imageTag) {
    ItMiiDomainModelInPV.buildMIIandPushToRepo(imageName, imageTag, null);
  }

  private Domain createDomainResource(String domainUid, String domNamespace, String adminSecretName,
      String repoSecretName, String encryptionSecretName, int replicaCount,
      String miiImage) {
    // create the domain CR
    Domain domain = new Domain()
        .apiVersion(DOMAIN_API_VERSION)
        .kind("Domain")
        .metadata(new V1ObjectMeta()
            .name(domainUid)
            .namespace(domNamespace))
        .spec(new DomainSpec()
            .domainUid(domainUid)
            .domainHomeSourceType("FromModel")
            .image(miiImage)
            .addImagePullSecretsItem(new V1LocalObjectReference()
                .name(repoSecretName))
            .webLogicCredentialsSecret(new V1SecretReference()
                .name(adminSecretName)
                .namespace(domNamespace))
            .includeServerOutInPodLog(true)
            .serverStartPolicy("IF_NEEDED")
            .serverPod(new ServerPod()
                .addEnvItem(new V1EnvVar()
                    .name("JAVA_OPTIONS")
                    .value("-Dweblogic.security.SSL.ignoreHostnameVerification=true"))
                .addEnvItem(new V1EnvVar()
                    .name("USER_MEM_ARGS")
                    .value("-Djava.security.egd=file:/dev/./urandom ")))
            .adminServer(new AdminServer()
                .serverStartState("RUNNING")
                .adminService(new AdminService()
                    .addChannelsItem(new Channel()
                        .channelName("default")
                        .nodePort(0))))
            .addClustersItem(new Cluster()
                .clusterName("cluster-1")
                .replicas(replicaCount)
                .serverStartState("RUNNING"))
            .configuration(new Configuration()
                .model(new Model()
                    .domainType("WLS")
                    .runtimeEncryptionSecret(encryptionSecretName))
                .introspectorJobActiveDeadlineSeconds(300L)));
    setPodAntiAffinity(domain);
    return domain;
  }

}
