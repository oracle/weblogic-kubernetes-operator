// Copyright (c) 2021, 2024, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.weblogic.kubernetes;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.annotation.Nonnull;

import io.kubernetes.client.custom.V1Patch;
import io.kubernetes.client.openapi.ApiException;
import io.kubernetes.client.openapi.models.V1EnvVar;
import io.kubernetes.client.openapi.models.V1LocalObjectReference;
import io.kubernetes.client.openapi.models.V1ObjectMeta;
import io.kubernetes.client.openapi.models.V1PersistentVolumeClaimVolumeSource;
import io.kubernetes.client.openapi.models.V1Volume;
import io.kubernetes.client.openapi.models.V1VolumeMount;
import oracle.weblogic.domain.AdminServer;
import oracle.weblogic.domain.AdminService;
import oracle.weblogic.domain.Channel;
import oracle.weblogic.domain.ClusterResource;
import oracle.weblogic.domain.Configuration;
import oracle.weblogic.domain.DomainResource;
import oracle.weblogic.domain.DomainSpec;
import oracle.weblogic.domain.Model;
import oracle.weblogic.domain.ServerPod;
import oracle.weblogic.domain.ServerService;
import oracle.weblogic.kubernetes.annotations.IntegrationTest;
import oracle.weblogic.kubernetes.annotations.Namespaces;
import oracle.weblogic.kubernetes.logging.LoggingFacade;
import oracle.weblogic.kubernetes.utils.FmwUtils;
import oracle.weblogic.kubernetes.utils.LoggingUtil;
import oracle.weblogic.kubernetes.utils.PodUtils;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIfEnvironmentVariable;

import static oracle.weblogic.kubernetes.ItMiiDomainModelInPV.buildMIIandPushToRepo;
import static oracle.weblogic.kubernetes.TestConstants.ADMIN_PASSWORD_DEFAULT;
import static oracle.weblogic.kubernetes.TestConstants.ADMIN_USERNAME_DEFAULT;
import static oracle.weblogic.kubernetes.TestConstants.BASE_IMAGES_REPO;
import static oracle.weblogic.kubernetes.TestConstants.BASE_IMAGES_REPO_SECRET_NAME;
import static oracle.weblogic.kubernetes.TestConstants.CLUSTER_VERSION;
import static oracle.weblogic.kubernetes.TestConstants.DOMAIN_API_VERSION;
import static oracle.weblogic.kubernetes.TestConstants.DOMAIN_STATUS_CONDITION_AVAILABLE_TYPE;
import static oracle.weblogic.kubernetes.TestConstants.DOMAIN_STATUS_CONDITION_COMPLETED_TYPE;
import static oracle.weblogic.kubernetes.TestConstants.DOMAIN_STATUS_CONDITION_FAILED_TYPE;
import static oracle.weblogic.kubernetes.TestConstants.DOMAIN_VERSION;
import static oracle.weblogic.kubernetes.TestConstants.ENCRYPION_PASSWORD_DEFAULT;
import static oracle.weblogic.kubernetes.TestConstants.ENCRYPION_USERNAME_DEFAULT;
import static oracle.weblogic.kubernetes.TestConstants.FMWINFRA_IMAGE_NAME;
import static oracle.weblogic.kubernetes.TestConstants.FMWINFRA_IMAGE_TAG;
import static oracle.weblogic.kubernetes.TestConstants.FMWINFRA_IMAGE_TO_USE_IN_SPEC;
import static oracle.weblogic.kubernetes.TestConstants.IMAGE_PULL_POLICY;
import static oracle.weblogic.kubernetes.TestConstants.MII_BASIC_APP_NAME;
import static oracle.weblogic.kubernetes.TestConstants.MII_BASIC_IMAGE_NAME;
import static oracle.weblogic.kubernetes.TestConstants.MII_BASIC_IMAGE_TAG;
import static oracle.weblogic.kubernetes.TestConstants.SKIP_CLEANUP;
import static oracle.weblogic.kubernetes.TestConstants.TEST_IMAGES_REPO_SECRET_NAME;
import static oracle.weblogic.kubernetes.TestConstants.WEBLOGIC_IMAGE_TO_USE_IN_SPEC;
import static oracle.weblogic.kubernetes.actions.ActionConstants.MODEL_DIR;
import static oracle.weblogic.kubernetes.actions.TestActions.deleteClusterCustomResource;
import static oracle.weblogic.kubernetes.actions.TestActions.deleteConfigMap;
import static oracle.weblogic.kubernetes.actions.TestActions.patchClusterCustomResource;
import static oracle.weblogic.kubernetes.actions.TestActions.patchDomainCustomResource;
import static oracle.weblogic.kubernetes.assertions.TestAssertions.clusterExists;
import static oracle.weblogic.kubernetes.assertions.TestAssertions.domainExists;
import static oracle.weblogic.kubernetes.assertions.TestAssertions.domainStatusReasonMatches;
import static oracle.weblogic.kubernetes.utils.ClusterUtils.createClusterAndVerify;
import static oracle.weblogic.kubernetes.utils.ClusterUtils.createClusterResource;
import static oracle.weblogic.kubernetes.utils.ClusterUtils.createClusterResourceAndAddReferenceToDomain;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.checkPodReadyAndServiceExists;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.getNextFreePort;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.getUniqueName;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.testUntil;
import static oracle.weblogic.kubernetes.utils.ConfigMapUtils.configMapExist;
import static oracle.weblogic.kubernetes.utils.ConfigMapUtils.createConfigMapAndVerify;
import static oracle.weblogic.kubernetes.utils.ConfigMapUtils.createConfigMapFromFiles;
import static oracle.weblogic.kubernetes.utils.DbUtils.createOracleDBUsingOperator;
import static oracle.weblogic.kubernetes.utils.DbUtils.createRcuAccessSecret;
import static oracle.weblogic.kubernetes.utils.DbUtils.createRcuSchema;
import static oracle.weblogic.kubernetes.utils.DbUtils.deleteOracleDB;
import static oracle.weblogic.kubernetes.utils.DomainUtils.checkDomainStatusConditionTypeExists;
import static oracle.weblogic.kubernetes.utils.DomainUtils.checkDomainStatusConditionTypeHasExpectedStatus;
import static oracle.weblogic.kubernetes.utils.DomainUtils.checkServerStatusPodPhaseAndPodReady;
import static oracle.weblogic.kubernetes.utils.DomainUtils.createDomainAndVerify;
import static oracle.weblogic.kubernetes.utils.DomainUtils.deleteDomainResource;
import static oracle.weblogic.kubernetes.utils.ImageUtils.createBaseRepoSecret;
import static oracle.weblogic.kubernetes.utils.ImageUtils.createImageRegistrySecret;
import static oracle.weblogic.kubernetes.utils.ImageUtils.createMiiImageAndVerify;
import static oracle.weblogic.kubernetes.utils.ImageUtils.createTestRepoSecret;
import static oracle.weblogic.kubernetes.utils.ImageUtils.imageRepoLoginAndPushImageToRegistry;
import static oracle.weblogic.kubernetes.utils.OperatorUtils.installAndVerifyOperator;
import static oracle.weblogic.kubernetes.utils.PatchDomainUtils.patchDomainResource;
import static oracle.weblogic.kubernetes.utils.PodUtils.setPodAntiAffinity;
import static oracle.weblogic.kubernetes.utils.SecretUtils.createOpsswalletpasswordSecret;
import static oracle.weblogic.kubernetes.utils.SecretUtils.createSecretWithUsernamePassword;
import static oracle.weblogic.kubernetes.utils.ThreadSafeLogger.getLogger;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests related to Domain status conditions logged by operator.
 * The tests checks for the Failed conditions for multiple usecases.
 */
@DisplayName("Verify the domain status failed conditions for domain lifecycle")
@IntegrationTest
@Tag("olcne-mrg")
@Tag("kind-parallel")
@Tag("okd-wls-mrg")
@Tag("oke-arm")
@Tag("oke-parallel")
class ItDiagnosticsFailedCondition {

  private static String domainNamespace = null;
  int replicaCount = 2;
  private String wlClusterName = "cluster-1";
  private String adminServerName = "admin-server";


  private static String adminSecretName;
  private static String encryptionSecretName;
  private static final String domainUid = "diagnosticsdomain";

  private static LoggingFacade logger = null;
  private static List<String> ns;

  /**
   * Assigns unique namespaces for operator and domains.
   * Pull WebLogic image if running tests in Kind cluster.
   * Installs operator.
   *
   * @param namespaces injected by JUnit
   */
  @BeforeAll
  public static void initAll(@Namespaces(2) List<String> namespaces) {
    ns = namespaces;
    logger = getLogger();
    logger.info("Assign a unique namespace for operator");
    assertNotNull(namespaces.get(0), "Namespace is null");
    String opNamespace = namespaces.get(0);

    logger.info("Assign a unique namespace for WebLogic domain");
    assertNotNull(namespaces.get(1), "Namespace is null");
    domainNamespace = namespaces.get(1);

    // set the service account name for the operator
    String opServiceAccount = opNamespace + "-sa";

    // install and verify operator with REST API
    installAndVerifyOperator(opNamespace, opServiceAccount, true, 0, domainNamespace);

    // create pull secrets for WebLogic image when running in non Kind Kubernetes cluster
    // this secret is used only for non-kind cluster
    createBaseRepoSecret(domainNamespace);

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
   * Test domain status condition with a bad model file.
   * Verify the following conditions are generated in an order after an introspector failure.
   * type: Failed, status: true
   * type: Available, status: false
   * type: Completed, status: false
   */
  @Test
  @DisplayName("Test domain status condition with bad model file")
  void testBadModelFileStatus() {
    boolean testPassed = false;
    String domainName = getDomainName();
    String clusterResName = getClusterResName(domainName);
    // build an image with empty WebLogic domain
    String imageName = MII_BASIC_IMAGE_NAME;
    String imageTag = "empty-domain-image";
    buildMIIandPushToRepo(imageName, imageTag, null);

    String badModelFileCm = "bad-model-in-cm";
    Path badModelFile = Paths.get(MODEL_DIR, "bad-model-file.yaml");
    final List<Path> modelList = Collections.singletonList(badModelFile);

    logger.info("creating a config map containing the bad model file");
    createConfigMapFromFiles(badModelFileCm, modelList, domainNamespace);

    try {
      // Test - test bad model file status with introspector failure
      logger.info("Creating a domain resource with bad model file from configmap");
      DomainResource domain = createDomainResourceWithConfigMap(domainName,
          domainNamespace, adminSecretName,
          BASE_IMAGES_REPO_SECRET_NAME, 
          encryptionSecretName, replicaCount, 
          imageName + ":" + imageTag, badModelFileCm, 30L, clusterResName);
      createDomainAndVerify(domain, domainNamespace);

      //check the desired completed, available and failed statuses
      checkStatus(domainName, "False", "False", "True");
      testPassed = true;

    } finally {
      if (!testPassed) {
        LoggingUtil.generateLog(this, ns);
      }
      if (assertDoesNotThrow(() -> domainExists(domainName, DOMAIN_VERSION, domainNamespace).call())) {
        deleteDomainResource(domainNamespace, domainName);
      }
      if (assertDoesNotThrow(() -> configMapExist(domainNamespace, badModelFileCm).call())) {
        deleteConfigMap(badModelFileCm, domainNamespace);
      }
      if (assertDoesNotThrow(() -> clusterExists(clusterResName, CLUSTER_VERSION, domainNamespace).call())) {
        deleteClusterCustomResource(clusterResName, domainNamespace);
      }
    }
  }

  /**
   * Test domain status condition with replicas set to more than maximum size of the WebLogic cluster created.
   * Verify the following conditions are generated
   * type: Failed, status: true
   * type: Available, status: false
   * type: Completed, status: false
   * Disabled due to bug.
   */
  @Test
  @DisplayName("Test domain status condition with replicas set to more than available in cluster")
  void testReplicasTooHigh() {
    boolean testPassed = false;
    String domainName = getDomainName();
    String clusterResName = getClusterResName(domainName);
    String image = MII_BASIC_IMAGE_NAME + ":" + MII_BASIC_IMAGE_TAG;

    logger.info("Creating domain resource with replicas=100");
    DomainResource domain = createDomainResource(domainName, domainNamespace, adminSecretName,
        BASE_IMAGES_REPO_SECRET_NAME, encryptionSecretName, 100, image, clusterResName);

    try {
      logger.info("Creating domain");
      createDomainAndVerify(domain, domainNamespace);

      //check the desired completed, available and failed statuses
      checkStatus(domainName, "False", "False", "True");

      testUntil(
          domainStatusReasonMatches(domainName, domainNamespace, "ReplicasTooHigh"),
          getLogger(),
          "waiting for domain status condition reason ReplicasTooHigh exists"
      );

      // Need to patch the cluster first, otherwise the domain can not be patched
      // You will get this error:
      // the replica count of cluster 'cluster-1' would exceed the cluster size '5' when patching the domain
      String patchStr
          = "["
          + "{\"op\": \"replace\", \"path\": \"/spec/replicas\", \"value\": 2}"
          + "]";
      V1Patch patch = new V1Patch(patchStr);
      logger.info("Patching cluster resource using patch string {0} ", patchStr);

      assertTrue(patchClusterCustomResource(clusterResName, domainNamespace,
          patch, V1Patch.PATCH_FORMAT_JSON_PATCH), "Failed to patch cluster");

      patchStr = "[{\"op\": \"replace\", "
          + "\"path\": \"/spec/webLogicCredentialsSecret/name\", \"value\": \"weblogic-credentials-foo\"}]";
      logger.info("PatchStr for domainHome: {0}", patchStr);

      patch = new V1Patch(patchStr);
      assertTrue(patchDomainCustomResource(domainName, domainNamespace, patch, V1Patch.PATCH_FORMAT_JSON_PATCH),
          "patchDomainCustomResource failed");
      testUntil(
          domainStatusReasonMatches(domainName, domainNamespace, "DomainInvalid"),
          getLogger(),
          "waiting for domain status condition reason DomainInvalid exists"
      );

      patchStr
          = "["
          + "{\"op\": \"replace\", \"path\": \"/spec/replicas\", \"value\": 2}"
          + "]";
      patch = new V1Patch(patchStr);
      logger.info("Patching cluster resource using patch string {0} ", patchStr);
      assertTrue(patchClusterCustomResource(clusterResName, domainNamespace,
          patch, V1Patch.PATCH_FORMAT_JSON_PATCH), "Failed to patch cluster");
      testUntil(
          domainStatusReasonMatches(domainName, domainNamespace, "DomainInvalid"),
          getLogger(),
          "waiting for domain status condition reason DomainInvalid exists"
      );
      testPassed = true;
    } finally {
      if (!testPassed) {
        LoggingUtil.generateLog(this, ns);
      }
      if (assertDoesNotThrow(() -> domainExists(domainName, DOMAIN_VERSION, domainNamespace).call())) {
        deleteDomainResource(domainNamespace, domainName);
      }
      if (assertDoesNotThrow(() -> clusterExists(clusterResName, CLUSTER_VERSION, domainNamespace).call())) {
        deleteClusterCustomResource(clusterResName, domainNamespace);
      }
    }
  }

  @Nonnull
  private String getClusterResName(String domainName) {
    return domainName + "-" + this.wlClusterName;
  }

  /**
   * Test domain status condition with replicas set to more than maximum size of the WebLogic cluster created followed
   * by changing it to a new value that is still too high.
   * Verify the patching operation fails.
   */
  @Test
  @DisplayName("Test domain patch operation fails when replicas set to more than available in cluster")
  void testReplicasTooHighNegative() {
    boolean testPassed = false;
    String domainName = getDomainName();
    String clusterResName = getClusterResName(domainName);
    String image = MII_BASIC_IMAGE_NAME + ":" + MII_BASIC_IMAGE_TAG;

    logger.info("Creating domain resource with replicas=100");
    try {
      DomainResource domain = createDomainResource(domainName, domainNamespace, adminSecretName,
          BASE_IMAGES_REPO_SECRET_NAME, encryptionSecretName, 100, image, clusterResName);

      logger.info("Creating domain");
      createDomainAndVerify(domain, domainNamespace);

      //check the desired completed, available and failed statuses
      checkStatus(domainName, "False", "False", "True");

      // remove after debug
      String patchStr
          = "["
          + "{\"op\": \"replace\", \"path\": \"/spec/replicas\", \"value\": 10}"
          + "]";
      V1Patch patch = new V1Patch(patchStr);
      logger.info("Patching cluster resource using patch string {0} ", patchStr);
      assertFalse(patchClusterCustomResource(clusterResName, domainNamespace,
          patch, V1Patch.PATCH_FORMAT_JSON_PATCH), "Patch cluster should fail");
      testPassed = true;
    } finally {
      if (!testPassed) {
        LoggingUtil.generateLog(this, ns);
      }
      if (assertDoesNotThrow(() -> domainExists(domainName, DOMAIN_VERSION, domainNamespace).call())) {
        deleteDomainResource(domainNamespace, domainName);
      }
      if (assertDoesNotThrow(() -> clusterExists(clusterResName, CLUSTER_VERSION, domainNamespace).call())) {
        deleteClusterCustomResource(clusterResName, domainNamespace);
      }
    }
  }

  /**
   * Test domain status condition with non-existing image.
   * Verify the following conditions are generated
   * type: Failed, status: true
   * type: Available, status: false
   * type: Completed, status: false
   */
  @Test
  @DisplayName("Test domain status failed condition with non-existing image")
  @Tag("gate")
  void testImageDoesnotExist() {
    boolean testPassed = false;
    String domainName = getDomainName();
    String clusterResName = getClusterResName(domainName);
    String image = MII_BASIC_IMAGE_NAME + ":non-existing";

    logger.info("Creating domain resource with non-existing image");
    DomainResource domain = createDomainResource(domainName, domainNamespace, adminSecretName,
        BASE_IMAGES_REPO_SECRET_NAME, encryptionSecretName, replicaCount, image, clusterResName);

    try {
      logger.info("Creating domain");
      createDomainAndVerify(domain, domainNamespace);

      //check the desired completed, available and failed statuses
      checkStatus(domainName, "False", "False", "True");
      testPassed = true;

    } finally {
      if (!testPassed) {
        LoggingUtil.generateLog(this, ns);
      }
      if (assertDoesNotThrow(() -> domainExists(domainName, DOMAIN_VERSION, domainNamespace).call())) {
        deleteDomainResource(domainNamespace, domainName);
      }
      if (assertDoesNotThrow(() -> clusterExists(clusterResName, CLUSTER_VERSION, domainNamespace).call())) {
        deleteClusterCustomResource(clusterResName, domainNamespace);
      }
    }
  }

  /**
   * Test domain status failed condition with missing image pull secret.
   * Verify the following conditions are generated
   * type: Failed, status: true
   * type: Available, status: false
   * type: Completed, status: false
   */
  @Test
  @DisplayName("Test domain status condition with missing image pull secret")
  @Tag("gate")
  void testImagePullSecretDoesnotExist() {
    boolean testPassed = false;
    String domainName = getDomainName();
    String clusterResName = getClusterResName(domainName);
    String image = MII_BASIC_IMAGE_NAME + ":" + MII_BASIC_IMAGE_TAG;

    logger.info("Creating domain resource with missing image pull secret");
    DomainResource domain = createDomainResource(domainName, domainNamespace, adminSecretName,
        BASE_IMAGES_REPO_SECRET_NAME + "bad", encryptionSecretName, 100, image, clusterResName);

    try {
      logger.info("Creating domain");
      createDomainAndVerify(domain, domainNamespace);

      //check the desired completed, available and failed statuses
      checkStatus(domainName, "False", "False", "True");
      testPassed = true;

    } finally {
      if (!testPassed) {
        LoggingUtil.generateLog(this, ns);
      }
      if (assertDoesNotThrow(() -> domainExists(domainName, DOMAIN_VERSION, domainNamespace).call())) {
        deleteDomainResource(domainNamespace, domainName);
      }
      if (assertDoesNotThrow(() -> clusterExists(clusterResName, CLUSTER_VERSION, domainNamespace).call())) {
        deleteClusterCustomResource(clusterResName, domainNamespace);
      }
    }
  }

  /**
   * Test domain status failed condition with incorrect image pull secret.
   * Verify the following conditions are generated
   * type: Failed, status: true
   * type: Available, status: false
   * type: Completed, status: false
   */
  @Test
  @DisplayName("Test domain status condition with incorrect image pull secret")
  void testIncorrectImagePullSecret() {
    boolean testPassed = false;
    String domainName = getDomainName();
    String clusterResName = getClusterResName(domainName);
    String image = MII_BASIC_IMAGE_NAME + ":" + MII_BASIC_IMAGE_TAG;
    logger.info("Creating a registry secret with invalid credentials");
    createImageRegistrySecret("foo", "bar", "foo@bar.com", BASE_IMAGES_REPO,
        "bad-pull-secret", domainNamespace);

    logger.info("Creating domain resource with incorrect image pull secret");
    DomainResource domain = createDomainResource(domainName, domainNamespace, adminSecretName,
        "bad-pull-secret", encryptionSecretName, replicaCount, image, clusterResName);
    domain.getSpec().imagePullPolicy("Always");

    try {
      logger.info("Creating domain");
      createDomainAndVerify(domain, domainNamespace);

      //check the desired completed, available and failed statuses
      checkStatus(domainName, "False", "False", "True");
      testPassed = true;

    } finally {
      if (!testPassed) {
        LoggingUtil.generateLog(this, ns);
      }
      if (assertDoesNotThrow(() -> domainExists(domainName, DOMAIN_VERSION, domainNamespace).call())) {
        deleteDomainResource(domainNamespace, domainName);
      }
      if (assertDoesNotThrow(() -> clusterExists(clusterResName, CLUSTER_VERSION, domainNamespace).call())) {
        deleteClusterCustomResource(clusterResName, domainNamespace);
      }
    }
  }

  /**
   * Test domain status failed condition with non-existing persistent volume.
   * Verify the following conditions are generated
   * type: Failed, status: true
   * type: Available, status: false
   * type: Completed, status: false
   */
  @Test
  @DisplayName("Test domain status condition with non-existent pv")
  void testNonexistentPVC() {
    boolean testPassed = false;
    String domainName = getDomainName();
    String clusterResName = getClusterResName(domainName);
    final String pvName = getUniqueName(domainName + "-pv-");
    final String pvcName = getUniqueName(domainName + "-pvc-");
    try {
      // create a domain custom resource configuration object
      logger.info("Creating domain custom resource");

      DomainResource domain = new DomainResource()
          .apiVersion(DOMAIN_API_VERSION)
          .kind("Domain")
          .metadata(new V1ObjectMeta()
              .name(domainName)
              .namespace(domainNamespace))
          .spec(new DomainSpec()
              .domainUid(domainName)
              .domainHome("/shared/" + domainNamespace + "/domains/" + domainName) // point to domain home in pv
              .domainHomeSourceType("PersistentVolume") // set the domain home source type as pv
              .image(WEBLOGIC_IMAGE_TO_USE_IN_SPEC)
              .imagePullPolicy(IMAGE_PULL_POLICY)
              .imagePullSecrets(Arrays.asList(
                  new V1LocalObjectReference()
                      .name(TestConstants.BASE_IMAGES_REPO_SECRET_NAME))) // this secret for non-kind cluster
              .webLogicCredentialsSecret(new V1LocalObjectReference()
                  .name(adminSecretName))
              .includeServerOutInPodLog(true)
              .logHomeEnabled(Boolean.TRUE)
              .logHome("/shared/logs/" + domainName)
              .dataHome("")
              .serverStartPolicy("IfNeeded")
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
                      .name(pvName))));
      setPodAntiAffinity(domain);

      ClusterResource cluster = createClusterResource(clusterResName, wlClusterName, domainNamespace, replicaCount);
      logger.info("Creating cluster {0} in namespace {1}", clusterResName, domainNamespace);
      createClusterAndVerify(cluster);
      // set cluster references
      domain.getSpec().withCluster(new V1LocalObjectReference().name(clusterResName));
    
      // verify the domain custom resource is created
      createDomainAndVerify(domain, domainNamespace);

      //check the desired completed, available and failed statuses
      checkStatus(domainName, "False", "False", "True");
      testPassed = true;

    } finally {
      if (!testPassed) {
        LoggingUtil.generateLog(this, ns);
      }
      if (assertDoesNotThrow(() -> clusterExists(clusterResName, CLUSTER_VERSION, domainNamespace).call())) {
        deleteClusterCustomResource(clusterResName, domainNamespace);
      }
      if (assertDoesNotThrow(() -> domainExists(domainName, DOMAIN_VERSION, domainNamespace).call())) {
        deleteDomainResource(domainNamespace, domainName);
      }
    }
  }

  /**
   * Test domain status failed condition with non-existent admin secret.
   * Verify the following conditions are generated
   * type: Failed, status: true
   * type: Available, status: false
   * type: Completed, status: false
   */
  @Test
  @DisplayName("Test domain status condition with non-existent admin secret")
  void testNonexistentAdminSecret() {
    boolean testPassed = false;
    String domainName = getDomainName();
    String clusterResName = getClusterResName(domainName);
    String image = TestConstants.MII_BASIC_IMAGE_NAME + ":" + TestConstants.MII_BASIC_IMAGE_TAG;

    logger.info("Creating domain custom resource");
    DomainResource domain = createDomainResource(domainName, domainNamespace, "non-existent-secret",
        BASE_IMAGES_REPO_SECRET_NAME, encryptionSecretName, replicaCount, image, clusterResName);

    try {
      logger.info("Creating domain");
      createDomainAndVerify(domain, domainNamespace);

      //check the desired completed, available and failed statuses
      checkStatus(domainName, "False", "False", "True");
      testPassed = true;

    } finally {
      if (!testPassed) {
        LoggingUtil.generateLog(this, ns);
      }
      if (assertDoesNotThrow(() -> domainExists(domainName, DOMAIN_VERSION, domainNamespace).call())) {
        deleteDomainResource(domainNamespace, domainName);
      }
      if (assertDoesNotThrow(() -> clusterExists(clusterResName, CLUSTER_VERSION, domainNamespace).call())) {
        deleteClusterCustomResource(clusterResName, domainNamespace);
      }
    }
  }


  /**
   * Test domain status failed condition with invalid node port.
   * Verify the following conditions are generated
   * type: Failed, status: true
   * type: Available, status: false
   * type: Completed, status: false
   */
  @Test
  @DisplayName("Test domain status failed condition with invalid node port.")
  void testInvalidNodePort() {
    boolean testPassed = false;
    String domainName = getDomainName();
    String clusterResName = getClusterResName(domainName);
    String image = MII_BASIC_IMAGE_NAME + ":" + MII_BASIC_IMAGE_TAG;

    logger.info("Creating domain custom resource");
    DomainResource domain = createDomainResource(domainName, domainNamespace, adminSecretName,
        BASE_IMAGES_REPO_SECRET_NAME, encryptionSecretName, replicaCount, image, clusterResName);

    AdminServer as = new AdminServer()
        .adminService(new AdminService()
            .addChannelsItem(new Channel()
                .channelName("default")
                .nodePort(19000)));
    domain.getSpec().adminServer(as);

    try {
      logger.info("Creating domain");
      createDomainAndVerify(domain, domainNamespace);

      //check the desired completed, available and failed statuses
      checkStatus(domainName, "False", "False", "True");
      testPassed = true;

    } finally {
      if (!testPassed) {
        LoggingUtil.generateLog(this, ns);
      }
      if (assertDoesNotThrow(() -> domainExists(domainName, DOMAIN_VERSION, domainNamespace).call())) {
        deleteDomainResource(domainNamespace, domainName);
      }
      if (assertDoesNotThrow(() -> clusterExists(clusterResName, CLUSTER_VERSION, domainNamespace).call())) {
        deleteClusterCustomResource(clusterResName, domainNamespace);
      }
    }
  }

  /**
   * Test domain status failed condition with introspector failure.
   * Verify the following conditions are generated
   * type: Failed, status: true
   * type: Available, status: false
   * type: Completed, status: false
   * Verify after the introspector successfully completes the Failed condition is removed.
   */
  @Test
  @DisplayName("Test domain status condition with introspector timeout failure")
  void testIntrospectorTimeoutFailure() {
    boolean testPassed = false;
    String domainName = getDomainName();
    String clusterResName = getClusterResName(domainName);

    String image = MII_BASIC_IMAGE_NAME + ":" + MII_BASIC_IMAGE_TAG;

    logger.info("Creating domain custom resource");
    DomainResource domain = createDomainResource(domainName, domainNamespace, adminSecretName,
        BASE_IMAGES_REPO_SECRET_NAME, encryptionSecretName, replicaCount, image, clusterResName);
    domain.getSpec().configuration().introspectorJobActiveDeadlineSeconds(5L);

    try {
      logger.info("Creating domain");
      createDomainAndVerify(domain, domainNamespace);

      //check the desired completed, available and failed statuses
      checkStatus(domainName, "False", "False", "True");
      testPassed = true;

    } finally {
      if (!testPassed) {
        LoggingUtil.generateLog(this, ns);
      }
      if (assertDoesNotThrow(() -> domainExists(domainName, DOMAIN_VERSION, domainNamespace).call())) {
        deleteDomainResource(domainNamespace, domainName);
      }
      if (assertDoesNotThrow(() -> clusterExists(clusterResName, CLUSTER_VERSION, domainNamespace).call())) {
        deleteClusterCustomResource(clusterResName, domainNamespace);
      }
    }
  }


  /**
   * Test domain status condition with managed server boot failure.
   * Test is disabled due to unavailability of operator support to detect boot failures.
   * Verify the following conditions are generated
   * type: Failed, status: true
   * type: Available, status: false
   * type: Completed, status: false
   */
  @DisabledIfEnvironmentVariable(named = "ARM", matches = "true")
  @DisabledIfEnvironmentVariable(named = "OCNE", matches = "true")
  @Test
  @DisplayName("Test domain status condition with managed server boot failure.")
  void testMSBootFailureStatus() {
    boolean testPassed = false;
    String domainName = getDomainName();
    String clusterResName = getClusterResName(domainName);
    try {
      String rcuSchemaPrefix = "FMWDOMAINMII";
      String oracleDbUrlPrefix = "oracledb.";
      String rcuSchemaPassword = "Oradoc_db1";
      String modelFile = "model-singleclusterdomain-sampleapp-jrf.yaml";

      final int dbListenerPort = getNextFreePort();
      String oracleDbSuffix = ".svc.cluster.local:" + dbListenerPort + "/devpdb.k8s";
      String dbUrl = oracleDbUrlPrefix + domainNamespace + oracleDbSuffix;

      String rcuaccessSecretName = domainName + "-rcu-access";
      String opsswalletpassSecretName = domainName + "-opss-wallet-password-secret";
      String rcuSysPassword = "Oradoc_db1";
      String dbName = domainName + "my-oracle-db";

      logger.info("Create Oracle DB in namespace: {0} ", domainNamespace);
      createBaseRepoSecret(domainNamespace);
      dbUrl = assertDoesNotThrow(() -> createOracleDBUsingOperator(dbName, rcuSysPassword, domainNamespace));

      logger.info("Create RCU schema with fmwImage: {0}, rcuSchemaPrefix: {1}, dbUrl: {2}, "
          + " dbNamespace: {3}", FMWINFRA_IMAGE_TO_USE_IN_SPEC, rcuSchemaPrefix, dbUrl, domainNamespace);
      createRcuSchema(FMWINFRA_IMAGE_TO_USE_IN_SPEC, rcuSchemaPrefix, dbUrl, domainNamespace);

      // create RCU access secret
      logger.info("Creating RCU access secret: {0}, with prefix: {1}, dbUrl: {2}, schemapassword: {3})",
          rcuaccessSecretName, rcuSchemaPrefix, rcuSchemaPassword, dbUrl);
      createRcuAccessSecret(rcuaccessSecretName, domainNamespace, rcuSchemaPrefix, rcuSchemaPassword, dbUrl);

      logger.info("Create OPSS wallet password secret");
      assertDoesNotThrow(() -> createOpsswalletpasswordSecret(
          opsswalletpassSecretName,
          domainNamespace,
          ADMIN_PASSWORD_DEFAULT),
          String.format("createSecret failed for %s", opsswalletpassSecretName));

      logger.info("Create an image with jrf model file");
      final List<String> modelList = Collections.singletonList(MODEL_DIR + "/" + modelFile);
      String fmwMiiImage = createMiiImageAndVerify(
          "jrf-mii-image-status",
          modelList,
          Collections.singletonList(MII_BASIC_APP_NAME),
          FMWINFRA_IMAGE_NAME,
          FMWINFRA_IMAGE_TAG,
          "JRF",
          false);

      // push the image to a registry to make it accessible in multi-node cluster
      imageRepoLoginAndPushImageToRegistry(fmwMiiImage);

      // create the domain object
      DomainResource domain = FmwUtils.createDomainResourceWithMaxServerPodReadyWaitTime(domainName,
          domainNamespace,
          adminSecretName,
          BASE_IMAGES_REPO_SECRET_NAME,
          encryptionSecretName,
          rcuaccessSecretName,
          opsswalletpassSecretName,
          replicaCount,
          fmwMiiImage,
          5L,
          "-Dcoherence.wka=" + domainName + "-" + adminServerName
      );

      getLogger().info("Creating cluster {0} in namespace {1}", clusterResName, domainNamespace);

      domain = createClusterResourceAndAddReferenceToDomain(
          clusterResName, wlClusterName, domainNamespace, domain, replicaCount);

      createDomainAndVerify(domain, domainNamespace);

      String adminServerPodName = domainName + "-admin-server";
      String managedServerPrefix = domainName + "-managed-server";

      checkPodReadyAndServiceExists(adminServerPodName, domainName, domainNamespace);

      for (int i = 1; i <= replicaCount; i++) {
        String managedServerName = managedServerPrefix + i + "-c1";
        logger.info("Checking managed server service {0} is created in namespace {1}",
            managedServerName, domainNamespace);
        checkPodReadyAndServiceExists(managedServerName, domainName, domainNamespace);
      }

      String patchStr
          = "[{\"op\": \"add\", \"path\": \"/spec/serverStartPolicy\", \"value\": \"Never\"}]";

      logger.info("Shutting down cluster using patch string: {0}", patchStr);
      V1Patch patch = new V1Patch(patchStr);
      assertTrue(patchClusterCustomResource(clusterResName, domainNamespace, patch, V1Patch.PATCH_FORMAT_JSON_PATCH),
          "Failed to patch cluster");

      for (int i = 1; i <= replicaCount; i++) {
        String managedServerName = managedServerPrefix + i + "-c1";
        logger.info("Checking managed server {0} has been shutdown in namespace {1}",
            managedServerName, domainNamespace);
        PodUtils.checkPodDoesNotExist(managedServerName, domainName, domainNamespace);
      }

      // delete Oracle database
      deleteOracleDB(domainNamespace, dbName);

      patchStr
          = "[{\"op\": \"replace\", \"path\": \"/spec/serverStartPolicy\", \"value\": \"IfNeeded\"}]";

      logger.info("Starting cluster using patch string: {0}", patchStr);
      patch = new V1Patch(patchStr);
      assertTrue(patchClusterCustomResource(clusterResName, domainNamespace, patch, V1Patch.PATCH_FORMAT_JSON_PATCH),
          "Failed to patch cluster");

      //check the desired completed, available and failed statuses
      checkStatus(domainName, "False", "False", "True");

      for (int i = 1; i <= replicaCount; i++) {
        String managedServerName = managedServerPrefix + i + "-c1";
        logger.info("Checking managed server {0} has been shutdown in namespace {1}",
            managedServerName, domainNamespace);
        checkServerStatusPodPhaseAndPodReady(domainName, domainNamespace, managedServerName, "Running", "False");
      }
      testPassed = true;
    } catch (ApiException ex) {
      logger.severe(ex.getLocalizedMessage());
    } finally {
      if (!testPassed) {
        LoggingUtil.generateLog(this, ns);
      }
      if (!SKIP_CLEANUP) {
        if (assertDoesNotThrow(() -> clusterExists(clusterResName, CLUSTER_VERSION, domainNamespace).call())) {
          deleteClusterCustomResource(clusterResName, domainNamespace);
        }
        if (assertDoesNotThrow(() -> domainExists(domainName, DOMAIN_VERSION, domainNamespace).call())) {
          deleteDomainResource(domainNamespace, domainName);
        }
      }
    }
  }

  /**
   * Test domain status condition with a bad model file.
   * Verify the following conditions are generated in an order after an introspector failure.
   * type: Failed, status: true
   * type: Available, status: false
   * type: Completed, status: false
   * Verify the introspector reruns to make it right when model file is fixed.
   */
  @Test
  @DisplayName("Test domain status condition with bad model file")
  void testIntrospectorMakerightAvailableFromFailure() {
    // Create the repo secret to pull the image
    // this secret is used only for non-kind cluster
    createTestRepoSecret(domainNamespace);

    // create secret for admin credentials
    logger.info("Create secret for admin credentials");
    assertDoesNotThrow(() -> createSecretWithUsernamePassword(
        adminSecretName,
        domainNamespace,
        ADMIN_USERNAME_DEFAULT,
        ADMIN_PASSWORD_DEFAULT),
        String.format("createSecret failed for %s", adminSecretName));

    // create encryption secret
    logger.info("Create encryption secret");
    assertDoesNotThrow(() -> createSecretWithUsernamePassword(
        encryptionSecretName,
        domainNamespace,
        ENCRYPION_USERNAME_DEFAULT,
        ENCRYPION_PASSWORD_DEFAULT),
        String.format("createSecret failed for %s", encryptionSecretName));

    // create WDT config map without any files
    createConfigMapAndVerify("empty-cm", domainUid, domainNamespace, Collections.emptyList());
    String image = MII_BASIC_IMAGE_NAME + ":" + MII_BASIC_IMAGE_TAG;

    // create the domain object
    DomainResource domain = createDomainResourceWithConfigMap(domainUid,
        domainNamespace,
        adminSecretName,
        TEST_IMAGES_REPO_SECRET_NAME,
        encryptionSecretName,
        2,
        image,
        "empty-cm",
        180L,
        "mymii-cluster-resource");

    logger.info("Creating a domain resource with model file image");
    createDomainAndVerify(domain, domainNamespace);
    checkDomainStatusConditionTypeHasExpectedStatus(domainUid, domainNamespace,
        DOMAIN_STATUS_CONDITION_COMPLETED_TYPE, "True");    
    
    //patch the domain with bad image
    //check the desired completed, available and failed status
    //verify the condition type Failed exists
    StringBuffer patchStr = new StringBuffer("[{");    
    patchStr.append("\"op\": \"replace\",")
        .append(" \"path\": \"/spec/image\",")
        .append("\"value\": \"")
        .append("bad-mii-image:doesntexist")
        .append("\"}]");
    logger.info("PatchStr for imageUpdate: {0}", patchStr.toString());

    assertTrue(patchDomainResource(domainUid, domainNamespace, patchStr),
        "patchDomainCustomResource(imageUpdate) failed");
    checkDomainStatusConditionTypeHasExpectedStatus(domainUid, domainNamespace,
        DOMAIN_STATUS_CONDITION_FAILED_TYPE, "True");

    //fix the domain failure by patching the domain resource with good image
    patchStr = new StringBuffer("["
        + "{");
    patchStr.append("\"op\": \"replace\",")
        .append(" \"path\": \"/spec/image\",")
        .append("\"value\": \"")
        .append(MII_BASIC_IMAGE_NAME + ":" + MII_BASIC_IMAGE_TAG)
        .append("\"},")
        .append("{\"op\": \"add\",")
        .append(" \"path\": \"/spec/restartVersion\",").append("\"value\": ").append("\"1\"")
        .append("}"
            + "]");
    logger.info("PatchStr for imageUpdate: {0}", patchStr.toString());

    assertTrue(patchDomainResource(domainUid, domainNamespace, patchStr),
        "patchDomainCustomResource(imageUpdate) failed");

    final String adminServerPodName = domainUid + "-admin-server";
    final String managedServerPrefix = domainUid + "-managed-server";

    // check admin server pod is ready
    logger.info("Wait for admin server pod {0} to be ready in namespace {1}",
        adminServerPodName, domainNamespace);
    checkPodReadyAndServiceExists(adminServerPodName, domainUid, domainNamespace);
    // check managed server pods are ready
    for (int i = 1; i <= 2; i++) {
      logger.info("Wait for managed server pod {0} to be ready in namespace {1}",
          managedServerPrefix + i, domainNamespace);
      checkPodReadyAndServiceExists(managedServerPrefix + i, domainUid, domainNamespace);
    }
    checkDomainStatusConditionTypeHasExpectedStatus(domainUid, domainNamespace,
        DOMAIN_STATUS_CONDITION_COMPLETED_TYPE, "True");
    checkDomainStatusConditionTypeExists(domainUid, domainNamespace, DOMAIN_STATUS_CONDITION_AVAILABLE_TYPE);
  }
  
  // Create a domain resource with a custom ConfigMap
  private DomainResource createDomainResourceWithConfigMap(String domainUid,
                                                           String domNamespace, String adminSecretName,
                                                           String repoSecretName, String encryptionSecretName,
                                                           int replicaCount, String miiImage, String configmapName,
                                                           Long introspectorDeadline, String clusterResName) {

    Map<String, String> keyValueMap = new HashMap<>();
    keyValueMap.put("testkey", "testvalue");

    // create the domain CR
    DomainResource domain = new DomainResource()
        .apiVersion(DOMAIN_API_VERSION)
        .kind("Domain")
        .metadata(new V1ObjectMeta()
            .name(domainUid)
            .namespace(domNamespace))
        .spec(new DomainSpec()
            .domainUid(domainUid)
            .domainHomeSourceType("FromModel")
            .image(miiImage)
            .imagePullPolicy(IMAGE_PULL_POLICY)
            .addImagePullSecretsItem(new V1LocalObjectReference()
                .name(repoSecretName))
            .webLogicCredentialsSecret(new V1LocalObjectReference()
                .name(adminSecretName))
            .includeServerOutInPodLog(true)
            .serverStartPolicy("IfNeeded")
            .serverPod(new ServerPod()
                .addEnvItem(new V1EnvVar()
                    .name("JAVA_OPTIONS")
                    .value("-Dweblogic.security.SSL.ignoreHostnameVerification=true"))
                .addEnvItem(new V1EnvVar()
                    .name("USER_MEM_ARGS")
                    .value("-Djava.security.egd=file:/dev/./urandom ")))
            .adminServer(new AdminServer()
                .serverService(new ServerService()
                    .annotations(keyValueMap)
                    .labels(keyValueMap)))
            .configuration(new Configuration()
                .model(new Model()
                    .domainType("WLS")
                    .configMap(configmapName)
                    .runtimeEncryptionSecret(encryptionSecretName))
                .introspectorJobActiveDeadlineSeconds(introspectorDeadline != null ? introspectorDeadline : 3000L)));
    setPodAntiAffinity(domain);


    ClusterResource cluster = createClusterResource(clusterResName, wlClusterName, domNamespace, replicaCount);
    logger.info("Creating cluster resource {0} in namespace {1}", clusterResName, domNamespace);

    createClusterAndVerify(cluster);
    // set cluster references
    domain.getSpec().withCluster(new V1LocalObjectReference().name(clusterResName));
    
    return domain;
  }

  private DomainResource createDomainResource(String domainUid, String domNamespace, String adminSecretName,
                                              String repoSecretName, String encryptionSecretName, int replicaCount,
                                              String miiImage, String clusterResName) {

    // create the domain CR
    DomainResource domain = new DomainResource()
        .apiVersion(DOMAIN_API_VERSION)
        .kind("Domain")
        .metadata(new V1ObjectMeta()
            .name(domainUid)
            .namespace(domNamespace))
        .spec(new DomainSpec()
            .domainUid(domainUid)
            .domainHomeSourceType("FromModel")
            .image(miiImage)
            .imagePullPolicy(IMAGE_PULL_POLICY)
            .addImagePullSecretsItem(new V1LocalObjectReference()
                .name(repoSecretName))
            .webLogicCredentialsSecret(new V1LocalObjectReference()
                .name(adminSecretName))
            .includeServerOutInPodLog(true)
            .serverStartPolicy("IfNeeded")
            .serverPod(new ServerPod()
                .addEnvItem(new V1EnvVar()
                    .name("JAVA_OPTIONS")
                    .value("-Dweblogic.security.SSL.ignoreHostnameVerification=true"))
                .addEnvItem(new V1EnvVar()
                    .name("USER_MEM_ARGS")
                    .value("-Djava.security.egd=file:/dev/./urandom ")))
            .configuration(new Configuration()
                .model(new Model()
                    .domainType("WLS")
                    .runtimeEncryptionSecret(encryptionSecretName))
                .introspectorJobActiveDeadlineSeconds(3000L)));
    setPodAntiAffinity(domain);

    ClusterResource cluster = createClusterResource(clusterResName, wlClusterName, domNamespace, replicaCount);
    logger.info("Creating cluster resource {0} in namespace {1}", clusterResName, domNamespace);
    createClusterAndVerify(cluster);

    // set cluster references
    domain.getSpec().withCluster(new V1LocalObjectReference().name(clusterResName));

    return domain;
  }

  // check the desired statuses of Completed, Available and Failed type conditions
  private void checkStatus(String domainName, String completed, String available, String failed) {

    if (failed != null) {
      // verify the condition type Failed exists
      checkDomainStatusConditionTypeExists(domainName, domainNamespace, DOMAIN_STATUS_CONDITION_FAILED_TYPE);
      // verify the condition Failed type has expected status
      checkDomainStatusConditionTypeHasExpectedStatus(domainName, domainNamespace,
          DOMAIN_STATUS_CONDITION_FAILED_TYPE, failed);
    }

    if (available != null) {
      // verify the condition type Available exists
      checkDomainStatusConditionTypeExists(domainName, domainNamespace, DOMAIN_STATUS_CONDITION_AVAILABLE_TYPE);
      // verify the condition Available type has expected status
      checkDomainStatusConditionTypeHasExpectedStatus(domainName, domainNamespace,
          DOMAIN_STATUS_CONDITION_AVAILABLE_TYPE, available);
    }

    if (completed != null) {
      // verify the condition type Completed exists
      checkDomainStatusConditionTypeExists(domainName, domainNamespace, DOMAIN_STATUS_CONDITION_COMPLETED_TYPE);
      // verify the condition Completed type has expected status
      checkDomainStatusConditionTypeHasExpectedStatus(domainName, domainNamespace,
          DOMAIN_STATUS_CONDITION_COMPLETED_TYPE, completed);
    }
  }

  private String getDomainName() {
    return domainUid + ((int) (Math.random() * Integer.MAX_VALUE));
  }

}
