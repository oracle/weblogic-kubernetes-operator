// Copyright (c) 2022, 2023, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.weblogic.kubernetes;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Callable;

import io.kubernetes.client.custom.V1Patch;
import io.kubernetes.client.openapi.models.V1EnvVar;
import io.kubernetes.client.openapi.models.V1LocalObjectReference;
import io.kubernetes.client.openapi.models.V1ObjectMeta;
import oracle.weblogic.domain.AdminServer;
import oracle.weblogic.domain.AdminService;
import oracle.weblogic.domain.Channel;
import oracle.weblogic.domain.ClusterResource;
import oracle.weblogic.domain.ClusterSpec;
import oracle.weblogic.domain.Configuration;
import oracle.weblogic.domain.DomainResource;
import oracle.weblogic.domain.DomainSpec;
import oracle.weblogic.domain.Model;
import oracle.weblogic.domain.ServerPod;
import oracle.weblogic.kubernetes.actions.impl.primitive.Command;
import oracle.weblogic.kubernetes.actions.impl.primitive.CommandParams;
import oracle.weblogic.kubernetes.annotations.IntegrationTest;
import oracle.weblogic.kubernetes.annotations.Namespaces;
import oracle.weblogic.kubernetes.assertions.impl.Cluster;
import oracle.weblogic.kubernetes.logging.LoggingFacade;
import oracle.weblogic.kubernetes.utils.ExecResult;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static oracle.weblogic.kubernetes.TestConstants.ADMIN_PASSWORD_DEFAULT;
import static oracle.weblogic.kubernetes.TestConstants.ADMIN_SERVER_NAME_BASE;
import static oracle.weblogic.kubernetes.TestConstants.ADMIN_USERNAME_DEFAULT;
import static oracle.weblogic.kubernetes.TestConstants.CLUSTER_VERSION;
import static oracle.weblogic.kubernetes.TestConstants.DEFAULT_MAX_CLUSTER_SIZE;
import static oracle.weblogic.kubernetes.TestConstants.DOMAIN_API_VERSION;
import static oracle.weblogic.kubernetes.TestConstants.DOMAIN_IMAGES_REPO;
import static oracle.weblogic.kubernetes.TestConstants.IMAGE_PULL_POLICY;
import static oracle.weblogic.kubernetes.TestConstants.KUBERNETES_CLI;
import static oracle.weblogic.kubernetes.TestConstants.MANAGED_SERVER_NAME_BASE;
import static oracle.weblogic.kubernetes.TestConstants.MII_BASIC_APP_NAME;
import static oracle.weblogic.kubernetes.TestConstants.MII_BASIC_IMAGE_NAME;
import static oracle.weblogic.kubernetes.TestConstants.MII_BASIC_IMAGE_TAG;
import static oracle.weblogic.kubernetes.TestConstants.TEST_IMAGES_REPO;
import static oracle.weblogic.kubernetes.TestConstants.TEST_IMAGES_REPO_PASSWORD;
import static oracle.weblogic.kubernetes.TestConstants.TEST_IMAGES_REPO_SECRET_NAME;
import static oracle.weblogic.kubernetes.TestConstants.TEST_IMAGES_REPO_USERNAME;
import static oracle.weblogic.kubernetes.TestConstants.WEBLOGIC_IMAGE_NAME;
import static oracle.weblogic.kubernetes.TestConstants.WEBLOGIC_IMAGE_TAG;
import static oracle.weblogic.kubernetes.TestConstants.WLS_DOMAIN_TYPE;
import static oracle.weblogic.kubernetes.actions.ActionConstants.MODEL_DIR;
import static oracle.weblogic.kubernetes.actions.TestActions.getDomainCustomResource;
import static oracle.weblogic.kubernetes.actions.TestActions.getServiceNodePort;
import static oracle.weblogic.kubernetes.actions.TestActions.imagePush;
import static oracle.weblogic.kubernetes.actions.TestActions.imageRepoLogin;
import static oracle.weblogic.kubernetes.actions.TestActions.imageTag;
import static oracle.weblogic.kubernetes.actions.TestActions.now;
import static oracle.weblogic.kubernetes.actions.TestActions.patchClusterCustomResourceReturnResponse;
import static oracle.weblogic.kubernetes.actions.TestActions.patchDomainCustomResourceReturnResponse;
import static oracle.weblogic.kubernetes.actions.TestActions.scaleClusterWithRestApiAndReturnResult;
import static oracle.weblogic.kubernetes.utils.ClusterUtils.createClusterAndVerify;
import static oracle.weblogic.kubernetes.utils.ClusterUtils.createClusterResource;
import static oracle.weblogic.kubernetes.utils.CommonMiiTestUtils.createMiiDomainAndVerify;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.checkPodReadyAndServiceExists;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.getNextFreePort;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.testUntil;
import static oracle.weblogic.kubernetes.utils.DomainUtils.createDomainAndVerify;
import static oracle.weblogic.kubernetes.utils.ImageUtils.createMiiImageAndVerify;
import static oracle.weblogic.kubernetes.utils.ImageUtils.createTestRepoSecret;
import static oracle.weblogic.kubernetes.utils.ImageUtils.imageRepoLoginAndPushImageToRegistry;
import static oracle.weblogic.kubernetes.utils.K8sEvents.DOMAIN_FAILED;
import static oracle.weblogic.kubernetes.utils.K8sEvents.checkDomainEventContainsExpectedMsg;
import static oracle.weblogic.kubernetes.utils.OperatorUtils.installAndVerifyOperator;
import static oracle.weblogic.kubernetes.utils.PodUtils.checkPodDeleted;
import static oracle.weblogic.kubernetes.utils.PodUtils.checkPodDoesNotExist;
import static oracle.weblogic.kubernetes.utils.PodUtils.setPodAntiAffinity;
import static oracle.weblogic.kubernetes.utils.SecretUtils.createSecretWithUsernamePassword;
import static oracle.weblogic.kubernetes.utils.ThreadSafeLogger.getLogger;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

// Test to verify the validating webhook for domain or cluster resource replicas count
@DisplayName("Test to verify the validating webhook for domain or cluster resource replicas count")
@IntegrationTest
@Tag("olcne")
@Tag("oke-parallel")
@Tag("kind-parallel")
@Tag("okd-wls-mrg")
class ItValidateWebhookReplicas {
  private static String opNamespace = null;
  private static String domainNamespace = null;
  private static String domainNamespace2 = null;
  private static String domainUid = "valwebrepdomain1";
  private static String domainUid2 = "valwebrepdomain2";

  private static String adminServerPodName = String.format("%s-%s", domainUid, ADMIN_SERVER_NAME_BASE);
  private static String managedServerPrefix = String.format("%s-%s", domainUid, MANAGED_SERVER_NAME_BASE);
  private static int replicaCount = 2;
  private static LoggingFacade logger = null;
  private static int domain2NumCluster = 2;
  private static int replicaCountToPatch = 10;
  private static String opServiceAccount = null;
  private static int externalRestHttpsPort = 0;

  private String clusterName = "cluster-1";

  /**
   * Perform initialization for all the tests in this class.
   * Set up the necessary namespaces, install the operator in the first namespace, and
   * create a domain in the second namespace using the pre-created basic MII image,
   * create a domain with multiple clusters in the third namespace using the pre-created basic MII image.
   * Max cluster size set in the model file is 5.
   *
   * @param namespaces list of namespaces created by the IntegrationTestWatcher by the
   *           JUnit engine parameter resolution mechanism
   */
  @BeforeAll
  public static void initAll(@Namespaces(3) List<String> namespaces) {
    logger = getLogger();

    // get a namespace for operator
    assertNotNull(namespaces.get(0), "Namespace namespaces.get(0) is null");
    opNamespace = namespaces.get(0);

    // get a namespace for domain1
    assertNotNull(namespaces.get(1), "Namespace namespaces.get(1) is null");
    domainNamespace = namespaces.get(1);

    // get a namespace for domain2
    assertNotNull(namespaces.get(2), "Namespace namespaces.get(2) is null");
    domainNamespace2 = namespaces.get(2);

    opServiceAccount = opNamespace + "-sa";
    // install and verify operator with REST API
    logger.info("Install an operator in namespace {0}, managing namespace {1} and {2}",
        opNamespace, domainNamespace, domainNamespace2);
    installAndVerifyOperator(opNamespace, opServiceAccount, true, 0,
        domainNamespace, domainNamespace2);
    externalRestHttpsPort = getServiceNodePort(opNamespace, "external-weblogic-operator-svc");

    // create a mii domain resource with one cluster
    logger.info("Create model-in-image domain {0} in namespace {1}, and wait until it comes up",
        domainUid, domainNamespace);
    createMiiDomainAndVerify(
        domainNamespace,
        domainUid,
        MII_BASIC_IMAGE_NAME + ":" + MII_BASIC_IMAGE_TAG,
        adminServerPodName,
        managedServerPrefix,
        replicaCount);

    // create a mii domain with two clusters in domainNamespace2
    logger.info("Create a model-in-image domain {0} with multiple clusters in namespace {1}",
        domainUid2, domainNamespace2);
    createMiiDomainWithMultiClusters(domainNamespace2);
  }

  @BeforeEach
  public void beforeEach() {
    // check admin server is up and running for domain1
    checkPodReadyAndServiceExists(adminServerPodName, domainUid, domainNamespace);
    // check admin server is up and running for domain2
    checkPodReadyAndServiceExists(domainUid2 + "-" + ADMIN_SERVER_NAME_BASE, domainUid2, domainNamespace2);

    // check managed servers are up and running for domain1
    for (int i = 1; i <= replicaCount; i++) {
      checkPodReadyAndServiceExists(managedServerPrefix + i, domainUid, domainNamespace);
    }

    // check managed servers are up and running for domain2
    for (int i = 1; i <= replicaCount; i++) {
      for (int j = 1; j <= domain2NumCluster; j++) {
        checkPodReadyAndServiceExists(domainUid2 + "-cluster-" + j + "-" + MANAGED_SERVER_NAME_BASE + i,
            domainUid2, domainNamespace2);
      }
    }
  }

  /**
   * Verify that when a domain and its clusters are running, a kubectl command that attempts to increase
   * the cluster level replicas to a value more than the max WebLogic cluster size will be rejected
   * with a clear error message.
   */
  @Test
  @DisplayName("Verify increasing the replicas of a cluster beyond configured WebLogic cluster size will be rejected")
  void testClusterReplicasTooHigh() {
    // patch the cluster with replicas value more than max cluster size
    String patchStr
        = "["
        + "{\"op\": \"replace\", \"path\": \"/spec/replicas\", \"value\": " + replicaCountToPatch + "}"
        + "]";
    V1Patch patch = new V1Patch(patchStr);
    logger.info("Patching cluster resource using patch string {0} ", patchStr);
    String responseMsg =
        patchClusterCustomResourceReturnResponse(clusterName, domainNamespace, patch, V1Patch.PATCH_FORMAT_JSON_PATCH);
    String expectedErrorMsg =
        "admission webhook \"weblogic.validating.webhook\" denied the request: Change request to cluster resource '"
            + clusterName
            + "' cannot be honored because the replica count would exceed the cluster size '5' when patching "
            + clusterName
            + " in namespace "
            + domainNamespace;
    assertTrue(responseMsg.contains(expectedErrorMsg),
        "Patching cluster replicas did not return the expected error msg: " + expectedErrorMsg);
  }

  /**
   * Verify that when a domain and its clusters are running, use the operator's REST call that attempts to increase
   * the cluster level replicas to a value more than the max WebLogic cluster size will be rejected
   * with a clear error message.
   */
  @Test
  @DisplayName("Verify increasing the replicas of a cluster beyond configured WebLogic cluster size will be rejected")
  void testScaleClusterReplicasTooHighUsingRest() {
    // scale the cluster with replicas value more than max cluster size using REST
    ExecResult execResult = scaleClusterWithRestApiAndReturnResult(domainUid, clusterName, replicaCountToPatch,
        externalRestHttpsPort, opNamespace, opServiceAccount);
    logger.info("execResult.exitValue={0}", execResult.exitValue());
    logger.info("execResult.stdout={0}", execResult.stdout());
    logger.info("execResult.stderr={0}", execResult.stderr());
    String expectedErrorMsg = "HTTP/1.1 400 Requested scaling count of " + replicaCountToPatch
        + " is greater than configured cluster size of 5 for WebLogic cluster cluster-1.";
    assertTrue(execResult.stderr().contains(expectedErrorMsg),
        "scale cluster with replicas more than max cluster size did not return the expected error msg: "
            + expectedErrorMsg);
  }

  /**
   * Verify that when a domain and its clusters are running, a kubectl command that attempts to increase
   * the domain level replicas to a value more than the max WebLogic cluster size will be rejected
   * with a clear error message.
   */
  @Test
  @DisplayName("Verify increasing the replicas of a domain beyond configured WebLogic cluster size will be rejected")
  void testDomainReplicasTooHigh() {
    // We need to patch the cluster resource to remove the replicas first. Otherwise, the domain level replicas will
    // be ignored.
    patchClusterResourceRemoveReplicas("cluster-1", domainUid, domainNamespace,
        managedServerPrefix + "2");

    // patch the domain with replicas value more than max cluster size
    String patchStr
        = "["
        + "{\"op\": \"replace\", \"path\": \"/spec/replicas\", \"value\": " + replicaCountToPatch + "}"
        + "]";
    V1Patch patch = new V1Patch(patchStr);
    logger.info("Patching domain resource using patch string {0} ", patchStr);
    String response =
        patchDomainCustomResourceReturnResponse(domainUid, domainNamespace, patch, V1Patch.PATCH_FORMAT_JSON_PATCH);
    String expectedErrorMsg =
        "admission webhook \"weblogic.validating.webhook\" denied the request: Change request to domain resource '"
            + domainUid
            + "' cannot be honored because the replica count of cluster '"
            + clusterName
            + "' would exceed the cluster size '5' when patching "
            + domainUid
            + " in namespace "
            + domainNamespace;

    assertTrue(response.contains(expectedErrorMsg),
        String.format("Patching domain replicas did not return the expected error msg: %s, got: %s",
            expectedErrorMsg, response));

    // restore the cluster resource with replicas
    restoreClusterResourceWithReplicas("cluster-1", domainUid, domainNamespace);
  }

  /**
   * All cluster resources in a domain have replicas set, changing domain's replicas to a number that exceeds
   * all cluster resources' size does not fail and the clusters are not impacted.
   * Max cluster size is 5. Change the domain replicas to 10.
   */
  @Test
  @DisplayName("All cluster resources in a domain have replicas set, changing domain's replicas to a number that "
      + "exceeds all cluster resources' size does not fail and the clusters are not impacted.")
  void testDomainReplicasTooHighWithClusterReplicas() {
    // patch the domain with replicas value more than max cluster size
    String patchStr
        = "["
        + "{\"op\": \"replace\", \"path\": \"/spec/replicas\", \"value\": " + replicaCountToPatch + "}"
        + "]";
    V1Patch patch = new V1Patch(patchStr);
    logger.info("Patching domain resource using patch string {0} ", patchStr);
    String response =
        patchDomainCustomResourceReturnResponse(domainUid, domainNamespace, patch, V1Patch.PATCH_FORMAT_JSON_PATCH);

    assertTrue(response.contains("Succeeded with response code: 200"),
        String.format("patching domain %s in namespace %s failed with error msg: %s",
            domainUid, domainNamespace, response));

    // check that the clusters are not impacted
    for (int i = 1; i <= replicaCount; i++) {
      checkPodReadyAndServiceExists(managedServerPrefix + i, domainUid, domainNamespace);
    }
    for (int i = replicaCount + 1; i <= replicaCountToPatch; i++) {
      checkPodDoesNotExist(managedServerPrefix + i, domainUid, domainNamespace);
    }

    // verify the domain status cluster replicas is not changed
    int domainStatusClusterReplicas = assertDoesNotThrow(() -> getDomainCustomResource(domainUid, domainNamespace))
        .getStatus().clusters().get(0).getReplicas().intValue();
    assertEquals(domainStatusClusterReplicas, replicaCount,
        String.format("domain status cluster replica is changed, expect: %s, got: %s",
            domainStatusClusterReplicas, replicaCount));

    // patch the domain with original replicas value
    patchStr
        = "["
        + "{\"op\": \"replace\", \"path\": \"/spec/replicas\", \"value\": 1}"
        + "]";
    patch = new V1Patch(patchStr);
    logger.info("Patching domain resource using patch string {0} ", patchStr);
    patchDomainCustomResourceReturnResponse(domainUid, domainNamespace, patch, V1Patch.PATCH_FORMAT_JSON_PATCH);
  }

  /**
   * Verify that when a domain and its clusters are running, a kubectl command that attempts to change the image,
   * as well as to increase the domain level replicas to a value that exceeds the WebLogic cluster size will NOT
   * be rejected, but the domain will get into a Failed:true condition with a clear error message.
   */
  @Test
  @DisplayName("Verify changing the image and increasing the replicas of a domain beyond configured WebLogic cluster "
      + "size will not be rejected but the domain will get into a Failed:true condition with a clear error message")
  void testDomainChangeImageReplicasTooHigh() {
    // We need to patch the cluster resource to remove the replicas first. Otherwise, the domain level replicas will
    // be ignored.
    patchClusterResourceRemoveReplicas("cluster-1", domainUid, domainNamespace, managedServerPrefix + "2");

    String originalImage = MII_BASIC_IMAGE_NAME + ":" + MII_BASIC_IMAGE_TAG;
    String newImage = MII_BASIC_IMAGE_NAME + ":newtag";

    testUntil(
        tagImageAndPushIfNeeded(originalImage, newImage),
        logger,
        "tagImageAndPushIfNeeded for image {0} to be successful",
        newImage);

    OffsetDateTime timestamp = now();

    // patch the domain with replicas value more than max cluster size
    String patchStr
        = "["
        + "{\"op\": \"replace\", \"path\": \"/spec/image\", \"value\": " + "\"" + newImage + "\"}, "
        + "{\"op\": \"replace\", \"path\": \"/spec/replicas\", \"value\": " + replicaCountToPatch + "}"
        + "]";
    V1Patch patch = new V1Patch(patchStr);
    logger.info("Patching domain resource using patch string {0} ", patchStr);
    String response =
        patchDomainCustomResourceReturnResponse(domainUid, domainNamespace, patch, V1Patch.PATCH_FORMAT_JSON_PATCH);

    // verify patching domain succeeds
    assertTrue(response.contains("Succeeded with response code: 200"),
        String.format("patching domain should succeed but failed with response msg: %s", response));

    // check the domain event contains the expected error msg
    String expectedErrorMsg = "Domain "
        + domainUid
        + " failed due to 'Replicas too high': "
        + replicaCountToPatch
        + " replicas specified for cluster '"
        + clusterName
        + "' which has a maximum cluster size of 5.";
    checkDomainEventContainsExpectedMsg(opNamespace, domainNamespace, domainUid, DOMAIN_FAILED, "Warning",
        timestamp, expectedErrorMsg);

    // verify up to 5 (max cluster size) pods are up and running
    for (int i = 1; i <= DEFAULT_MAX_CLUSTER_SIZE; i++) {
      checkPodReadyAndServiceExists(managedServerPrefix + i, domainUid, domainNamespace);
    }

    // restore the domain image and replicas
    restoreDomainImageAndReplicas(domainUid, domainNamespace, originalImage);

    // restore the cluster resource with replicas
    restoreClusterResourceWithReplicas("cluster-1", domainUid, domainNamespace);
  }

  /**
   * Verify that when a domain and its clusters are running, call
   * 'kubectl scale --replicas=10 clusters/cluster-1 -n ns-xxx' to increase the
   * domain level replicas to a value that exceeds the WebLogic cluster size will be rejected.
   */
  @Test
  @DisplayName("Verify call 'kubectl scale' to increase the replicas of a cluster beyond configured WebLogic cluster "
      + "size will be rejected")
  void testScaleClusterReplicasTooHigh() {

    // use 'kubectl scale' to scale the cluster
    CommandParams params = new CommandParams().defaults();
    String command = KUBERNETES_CLI + " scale --replicas=" + replicaCountToPatch
        + " clusters/" + clusterName
        + " -n " + domainNamespace;
    params.command(command);
    ExecResult result = Command.withParams(params).executeAndReturnResult();
    String errorMsg =
        "admission webhook \"weblogic.validating.webhook\" denied the request: Scale request to cluster resource '"
            + clusterName
            + "' cannot be honored because the replica count would exceed the cluster size '5'";
    assertTrue(result.stderr().contains(errorMsg),
        String.format("scale cluster beyond max cluster size did not throw error, got: %s; expect: %s",
            result.stderr(), errorMsg));
  }

  /**
   * The domain contains two cluster resources, one has replicas set, and the other does not, changing domain's replicas
   * to a number that exceeds all cluster resources' size fails and the message only contains the name of the second
   * cluster.
   */
  @Test
  @DisplayName("domain contains two cluster resources, one has replicas set, and the other does not, "
      + "changing domain's replicas to a number that exceeds all cluster resources' size fails and the message only "
      + "contains the name of the second cluster.")
  void testDomainReplicasTooHighTwoClustersDiffReplica() {
    // patch the domain2 to remove the cluster-2 replicas
    patchClusterResourceRemoveReplicas(domainUid2 + "-cluster-2", domainUid2, domainNamespace2,
        domainUid2 + "-cluster-2-" + MANAGED_SERVER_NAME_BASE + "2");

    // patch the domain with replicas value more than max cluster size
    String patchStr
        = "["
        + "{\"op\": \"replace\", \"path\": \"/spec/replicas\", \"value\": " + replicaCountToPatch + "}"
        + "]";
    V1Patch patch = new V1Patch(patchStr);
    logger.info("Patching domain resource using patch string {0} ", patchStr);
    String response =
        patchDomainCustomResourceReturnResponse(domainUid2, domainNamespace2, patch, V1Patch.PATCH_FORMAT_JSON_PATCH);
    String expectedErrorMsg =
        "admission webhook \"weblogic.validating.webhook\" denied the request: Change request to domain resource '"
            + domainUid2
            + "' cannot be honored because the replica count of cluster 'cluster-2' "
            + "would exceed the cluster size '5' when patching "
            + domainUid2
            + " in namespace "
            + domainNamespace2;

    assertTrue(response.contains(expectedErrorMsg),
        String.format("Patching domain replicas did not return the expected error msg: %s, got: %s",
            expectedErrorMsg, response));
    assertFalse(response.contains(clusterName),
        String.format("Patching domain replicas response should not contain cluster name %s", clusterName));

    // restore the domain2 cluster2
    restoreClusterResourceWithReplicas(domainUid2 + "-cluster-2", domainUid2, domainNamespace2);
  }

  /**
   * The domain contains two cluster resources and both have no replicas set, changing domain's replicas to a number
   * that exceeds all cluster resources' size fails and the message contains the name of both clusters.
   */
  @Test
  @DisplayName("The domain contains two cluster resources and both have replicas set, changing domain's replicas to a "
      + "number that exceeds all cluster resources' size fails and the message contains the name of both clusters.")
  void testDomainReplicasTooHighTwoClustersNoReplica() {

    // patch the domain2 to remove the cluster-1 replicas
    patchClusterResourceRemoveReplicas(domainUid2 + "-cluster-1", domainUid2, domainNamespace2,
        domainUid2 + "-cluster-1-" + MANAGED_SERVER_NAME_BASE + "2");
    // patch the domain2 to remove the cluster-2 replicas
    patchClusterResourceRemoveReplicas(domainUid2 + "-cluster-2", domainUid2, domainNamespace2,
        domainUid2 + "-cluster-2-" + MANAGED_SERVER_NAME_BASE + "2");

    // patch the domain with replicas value more than max cluster size
    String patchStr
        = "["
        + "{\"op\": \"replace\", \"path\": \"/spec/replicas\", \"value\": " + replicaCountToPatch + "}"
        + "]";
    V1Patch patch = new V1Patch(patchStr);
    logger.info("Patching domain resource using patch string {0} ", patchStr);
    String response =
        patchDomainCustomResourceReturnResponse(domainUid2, domainNamespace2, patch, V1Patch.PATCH_FORMAT_JSON_PATCH);
    String expectedErrorMsg =
        "admission webhook \"weblogic.validating.webhook\" denied the request: Change request to domain resource '"
            + domainUid2
            + "' cannot be honored because the replica count of each cluster in 'cluster-1, cluster-2' "
            + "would exceed its cluster size '5, 5' respectively when patching "
            + domainUid2
            + " in namespace "
            + domainNamespace2;

    assertTrue(response.contains(expectedErrorMsg),
        String.format("Patching domain replicas did not return the expected error msg: %s, got: %s",
            expectedErrorMsg, response));

    // restore domain2-cluster-1 and domain2-cluster-2
    restoreClusterResourceWithReplicas(domainUid2 + "-cluster-1", domainUid2, domainNamespace2);
    restoreClusterResourceWithReplicas(domainUid2 + "-cluster-2", domainUid2, domainNamespace2);
  }

  private void patchClusterResourceRemoveReplicas(String clusterResName,
                                                  String domainUid,
                                                  String domainNamespace,
                                                  String managedServerToDelete) {
    String patchStr
        = "["
        + "{\"op\": \"remove\", \"path\": \"/spec/replicas\"}"
        + "]";
    V1Patch patch = new V1Patch(patchStr);
    logger.info("Patching the cluster resource using patching string {0}", patchStr);
    String response = patchClusterCustomResourceReturnResponse(clusterResName, domainNamespace, patch,
            V1Patch.PATCH_FORMAT_JSON_PATCH);
    assertTrue(response.contains("Succeeded with response code: 200"),
        String.format("patching cluster %s in namespace %s failed with error msg: %s",
            clusterResName, domainNamespace, response));
    // verify managed server 2 is deleted
    checkPodDeleted(managedServerToDelete, domainUid, domainNamespace);
  }

  private void restoreClusterResourceWithReplicas(String clusterResName,
                                                  String domainUid,
                                                  String domainNamespace) {
    String patchStr
        = "["
        + "{\"op\": \"add\", \"path\": \"/spec/replicas\", \"value\": " + replicaCount + "}"
        + "]";
    V1Patch patch = new V1Patch(patchStr);
    logger.info("Patching the cluster resource using patching string {0}", patchStr);
    String response = patchClusterCustomResourceReturnResponse(clusterResName, domainNamespace, patch,
            V1Patch.PATCH_FORMAT_JSON_PATCH);
    assertTrue(response.contains("Succeeded with response code: 200"),
        String.format("patching cluster %s in namespace %s failed with error msg: %s",
            clusterResName, domainNamespace, response));

    // check the managed server pods are up and running
    int numClusters =
        assertDoesNotThrow(() -> getDomainCustomResource(domainUid, domainNamespace)).getSpec().getClusters().size();

    for (int i = 1; i <= replicaCount; i++) {
      String serverNamePrefix;
      if (numClusters > 1) {
        serverNamePrefix = clusterResName + "-" + MANAGED_SERVER_NAME_BASE;
      } else {
        serverNamePrefix = domainUid + "-" + MANAGED_SERVER_NAME_BASE;
      }
      checkPodReadyAndServiceExists(serverNamePrefix + i, domainUid, domainNamespace);
    }
  }

  /**
   * Create model in image domain with multiple clusters.
   *
   * @param domainNamespace namespace in which the domain will be created
   * @return oracle.weblogic.domain.Domain objects
   */
  private static DomainResource createMiiDomainWithMultiClusters(String domainNamespace) {

    // admin/managed server name here should match with WDT model yaml file
    String adminServerPodName = domainUid2 + "-" + ADMIN_SERVER_NAME_BASE;
    String encryptionSecretName = "encryptionsecret";
    String wdtModelFileForMiiDomain = "model-multiclusterdomain-singlesampleapp-wls.yaml";

    // create registry secret to pull the image from registry
    // this secret is used only for non-kind cluster
    logger.info("Creating registry secret in namespace {0}", domainNamespace);
    createTestRepoSecret(domainNamespace);

    String adminSecretName = "weblogic-credentials";
    // create secret for admin credentials
    logger.info("Creating secret for admin credentials");
    createSecretWithUsernamePassword(adminSecretName, domainNamespace,
        ADMIN_USERNAME_DEFAULT, ADMIN_PASSWORD_DEFAULT);

    // create encryption secret
    logger.info("Creating encryption secret");
    createSecretWithUsernamePassword(encryptionSecretName, domainNamespace,
        "weblogicenc", "weblogicenc");

    String miiImage = createAndPushMiiImage(wdtModelFileForMiiDomain);

    // create the domain CR
    DomainResource domain = new DomainResource()
        .apiVersion(DOMAIN_API_VERSION)
        .kind("Domain")
        .metadata(new V1ObjectMeta()
            .name(domainUid2)
            .namespace(domainNamespace))
        .spec(new DomainSpec()
            .domainUid(domainUid2)
            .domainHome("/u01/" + domainNamespace + "/domains/" + domainUid2)
            .domainHomeSourceType("FromModel")
            .image(miiImage)
            .imagePullPolicy(IMAGE_PULL_POLICY)
            .addImagePullSecretsItem(new V1LocalObjectReference()
                .name(TEST_IMAGES_REPO_SECRET_NAME))
            .webLogicCredentialsSecret(new V1LocalObjectReference()
                .name(adminSecretName))
            .includeServerOutInPodLog(true)
            .serverStartPolicy("IfNeeded")
            .serverPod(new ServerPod()
                .addEnvItem(new V1EnvVar()
                    .name("JAVA_OPTIONS")
                    .value("-Dweblogic.security.SSL.ignoreHostnameVerification=true "
                        + "-Dweblogic.StdoutDebugEnabled=false"))
                .addEnvItem(new V1EnvVar()
                    .name("USER_MEM_ARGS")
                    .value("-Djava.security.egd=file:/dev/./urandom ")))
            .adminServer(new AdminServer()
                .adminChannelPortForwardingEnabled(true)
                .adminService(new AdminService()
                    .addChannelsItem(new Channel()
                        .channelName("default-secure")
                        .nodePort(getNextFreePort()))
                    .addChannelsItem(new Channel()
                        .channelName("default")
                        .nodePort(getNextFreePort()))))
            .configuration(new Configuration()
                .introspectorJobActiveDeadlineSeconds(300L)
                .model(new Model()
                    .domainType(WLS_DOMAIN_TYPE)
                    .runtimeEncryptionSecret(encryptionSecretName))));

    // create cluster resource in mii domain
    for (int i = 1; i <= domain2NumCluster; i++) {
      if (!Cluster.doesClusterExist(domainUid2 + "-cluster-" + i, CLUSTER_VERSION, domainNamespace)) {
        ClusterResource cluster =
            createClusterResource(domainUid2 + "-cluster-" + i, domainNamespace,
                new ClusterSpec().withClusterName("cluster-" + i).replicas(replicaCount));
        createClusterAndVerify(cluster);
      }
      domain.getSpec().withCluster(new V1LocalObjectReference().name(domainUid2 + "-cluster-" + i));
    }
    setPodAntiAffinity(domain);

    // create model in image domain
    logger.info("Creating model in image domain {0} in namespace {1} using image {2}",
        domainUid2, domainNamespace, miiImage);
    createDomainAndVerify(domain, domainNamespace);

    // check that admin server pod is ready and service exists in domain namespace
    logger.info("Checking that admin server pod {0} is ready and service exists in namespace {1}",
        adminServerPodName, domainNamespace);
    checkPodReadyAndServiceExists(adminServerPodName, domainUid2, domainNamespace);

    // check the readiness for the managed servers in each cluster
    // for domain2-cluster-1, there are two pods running
    for (int i = 1; i <= domain2NumCluster; i++) {
      for (int j = 1; j <= replicaCount; j++) {
        String managedServerPodName =
            domainUid2 + "-cluster-" + i + "-" + MANAGED_SERVER_NAME_BASE + j;

        // check managed server pod is ready and service exists in the namespace
        logger.info("Checking that managed server pod {0} is ready and service exists in namespace {1}",
            managedServerPodName, domainNamespace);
        checkPodReadyAndServiceExists(managedServerPodName, domainUid2, domainNamespace);
      }
    }

    return domain;
  }

  /**
   * Create mii image and push it to the registry.
   *
   * @return mii image created
   */
  private static String createAndPushMiiImage(String wdtModelFileForMiiDomain) {
    // create image with model files
    logger.info("Creating image with model file {0} and verify", wdtModelFileForMiiDomain);
    List<String> appSrcDirList = new ArrayList<>();
    appSrcDirList.add(MII_BASIC_APP_NAME);

    String miiImage =
        createMiiImageAndVerify("mii-image", Collections.singletonList(MODEL_DIR + "/" + wdtModelFileForMiiDomain),
            appSrcDirList, WEBLOGIC_IMAGE_NAME, WEBLOGIC_IMAGE_TAG, WLS_DOMAIN_TYPE, false);

    // repo login and push image to registry if necessary
    imageRepoLoginAndPushImageToRegistry(miiImage);

    return miiImage;
  }

  private void restoreDomainImageAndReplicas(String domainUid, String domainNamespace, String originalImage) {
    String patchStr
        = "["
        + "{\"op\": \"replace\", \"path\": \"/spec/image\", \"value\": " + "\"" + originalImage + "\"}, "
        + "{\"op\": \"replace\", \"path\": \"/spec/replicas\", \"value\": 1}"
        + "]";
    V1Patch patch = new V1Patch(patchStr);
    logger.info("Patching domain resource using patch string {0} ", patchStr);
    String response =
        patchDomainCustomResourceReturnResponse(domainUid, domainNamespace, patch, V1Patch.PATCH_FORMAT_JSON_PATCH);
    // verify patching domain succeeds
    assertTrue(response.contains("Succeeded with response code: 200"),
        String.format("patching domain should succeed but failed with response msg: %s", response));

    // check only managed server1 pod exists, all other managed server pods are deleted
    String managedServerPrefix = domainUid + "-" + MANAGED_SERVER_NAME_BASE;
    for (int i = DEFAULT_MAX_CLUSTER_SIZE; i > 1; i--) {
      checkPodDeleted(managedServerPrefix + i, domainUid, domainNamespace);
    }
    checkPodReadyAndServiceExists(managedServerPrefix + "1", domainUid, domainNamespace);
  }

  private Callable<Boolean> tagImageAndPushIfNeeded(String originalImage, String taggedImage) {
    return (() -> {
      boolean result = true;
      result = result && imageTag(originalImage, taggedImage);
      // push the image to a registry to make the test work in multi node cluster
      logger.info("image repo login to registry {0}", TEST_IMAGES_REPO);
      result = result && imageRepoLogin(TEST_IMAGES_REPO, TEST_IMAGES_REPO_USERNAME,
          TEST_IMAGES_REPO_PASSWORD);
      // push image
      if (!DOMAIN_IMAGES_REPO.isEmpty()) {
        logger.info("push image {0} to registry", taggedImage);
        result = result && imagePush(taggedImage);
      }
      return result;
    });
  }
}
