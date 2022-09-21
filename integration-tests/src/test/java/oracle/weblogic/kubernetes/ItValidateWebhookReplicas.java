// Copyright (c) 2022, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.weblogic.kubernetes;

import java.time.OffsetDateTime;
import java.util.List;

import io.kubernetes.client.custom.V1Patch;
import oracle.weblogic.kubernetes.annotations.IntegrationTest;
import oracle.weblogic.kubernetes.annotations.Namespaces;
import oracle.weblogic.kubernetes.logging.LoggingFacade;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static oracle.weblogic.kubernetes.TestConstants.ADMIN_SERVER_NAME_BASE;
import static oracle.weblogic.kubernetes.TestConstants.DEFAULT_MAX_CLUSTER_SIZE;
import static oracle.weblogic.kubernetes.TestConstants.KIND_REPO;
import static oracle.weblogic.kubernetes.TestConstants.MANAGED_SERVER_NAME_BASE;
import static oracle.weblogic.kubernetes.TestConstants.MII_BASIC_IMAGE_NAME;
import static oracle.weblogic.kubernetes.TestConstants.MII_BASIC_IMAGE_TAG;
import static oracle.weblogic.kubernetes.actions.TestActions.dockerTag;
import static oracle.weblogic.kubernetes.actions.TestActions.now;
import static oracle.weblogic.kubernetes.actions.TestActions.patchClusterCustomResourceReturnResponse;
import static oracle.weblogic.kubernetes.actions.TestActions.patchDomainCustomResourceReturnResponse;
import static oracle.weblogic.kubernetes.actions.TestActions.tagAndPushToKind;
import static oracle.weblogic.kubernetes.utils.CommonMiiTestUtils.createMiiDomainAndVerify;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.checkPodReadyAndServiceExists;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.testUntil;
import static oracle.weblogic.kubernetes.utils.K8sEvents.DOMAIN_FAILED;
import static oracle.weblogic.kubernetes.utils.K8sEvents.checkDomainEventContainsExpectedMsg;
import static oracle.weblogic.kubernetes.utils.OperatorUtils.installAndVerifyOperator;
import static oracle.weblogic.kubernetes.utils.PodUtils.checkPodDeleted;
import static oracle.weblogic.kubernetes.utils.ThreadSafeLogger.getLogger;
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
  private static String domainUid = "domain1";

  private static String adminServerPodName = String.format("%s-%s", domainUid, ADMIN_SERVER_NAME_BASE);
  private static String managedServerPrefix = String.format("%s-%s", domainUid, MANAGED_SERVER_NAME_BASE);
  private static int replicaCount = 2;
  private static LoggingFacade logger = null;

  private String clusterName = "cluster-1";

  /**
   * Perform initialization for all the tests in this class.
   * Set up the necessary namespaces, install the operator in the first namespace, and
   * create a domain in the second namespace using the pre-created basic MII image.
   *
   * @param namespaces list of namespaces created by the IntegrationTestWatcher by the
   *           JUnit engine parameter resolution mechanism
   */
  @BeforeAll
  public static void initAll(@Namespaces(2) List<String> namespaces) {
    logger = getLogger();

    // get a namespace for operator
    assertNotNull(namespaces.get(0), String.format("Namespace namespaces.get(0) is null"));
    opNamespace = namespaces.get(0);

    // get a namespace for domain1
    assertNotNull(namespaces.get(1), String.format("Namespace namespaces.get(1) is null"));
    domainNamespace = namespaces.get(1);

    // install the operator
    logger.info("Install an operator in namespace {0}, managing namespace {1}",
        opNamespace, domainNamespace);
    installAndVerifyOperator(opNamespace, domainNamespace);

    // create a domain resource
    logger.info("Create model-in-image domain {0} in namespace {1}, and wait until it comes up",
        domainUid, domainNamespace);
    createMiiDomainAndVerify(
        domainNamespace,
        domainUid,
        MII_BASIC_IMAGE_NAME + ":" + MII_BASIC_IMAGE_TAG,
        adminServerPodName,
        managedServerPrefix,
        replicaCount);
  }

  @BeforeEach
  public void beforeEach() {
    // check admin server is up and running
    checkPodReadyAndServiceExists(adminServerPodName, domainUid, domainNamespace);
    // check managed servers are up and running
    for (int i = 1; i <= replicaCount; i++) {
      checkPodReadyAndServiceExists(managedServerPrefix + i, domainUid, domainNamespace);
    }
  }

  /**
   * Verify that when a domain and its clusters are running, a kubectl command that attempts to increase
   * the cluster level replicas to a value more than the max WebLogic cluster size will be rejected
   * with a clear error message.
   */
  @Test
  @DisplayName("Verify increasing the replicas of a cluster too high will be rejected")
  void testClusterReplicasTooHigh() {
    // patch the cluster with replicas value more than max cluster size
    String patchStr
        = "["
        + "{\"op\": \"replace\", \"path\": \"/spec/replicas\", \"value\": 10}"
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
   * Verify that when a domain and its clusters are running, a kubectl command that attempts to increase
   * the domain level replicas to a value more than the max WebLogic cluster size will be rejected
   * with a clear error message.
   */
  @Test
  @DisplayName("Verify increasing the replicas of a domain too high will be rejected")
  void testDomainReplicasTooHigh() {
    // We need to patch the cluster resource to remove the replicas first. Otherwise, the domain level replicas will
    // be ignored.
    patchClusterResourceRemoveReplicas();

    // patch the domain with replicas value more than max cluster size
    String patchStr
        = "["
        + "{\"op\": \"replace\", \"path\": \"/spec/replicas\", \"value\": 10}"
        + "]";
    V1Patch patch = new V1Patch(patchStr);
    logger.info("Patching domain resource using patch string {0} ", patchStr);
    String response =
        patchDomainCustomResourceReturnResponse(domainUid, domainNamespace, patch, V1Patch.PATCH_FORMAT_JSON_PATCH);
    String expectedErrorMsg =
        "admission webhook \"weblogic.validating.webhook\" denied the request: Change request to domain resource '"
            + domainUid
            + "' cannot be honored because the replica count for cluster '"
            + clusterName
            + "' would exceed the cluster size '5' when patching "
            + domainUid
            + " in namespace "
            + domainNamespace;

    assertTrue(response.contains(expectedErrorMsg),
        String.format("Patching domain replicas did not return the expected error msg: %s, got: %s",
            expectedErrorMsg, response));

    // restore the cluster resource with replicas
    restoreClusterResourceWithReplicas();
  }

  /**
   * Verify that when a domain and its clusters are running, a kubectl command that attempts to change the image,
   * as well as to increase the domain level replicas to a value that exceeds the WebLogic cluster size will NOT
   * be rejected, but the domain will get into a Failed:true condition with a clear error message.
   */
  @Test
  @DisplayName("Verify changing the image and increasing the replicas of a domain too high will not be rejected but "
      + "the domain will get into a Failed:true condition with a clear error message")
  void testDomainChangeImageReplicasTooHigh() {
    // We need to patch the cluster resource to remove the replicas first. Otherwise, the domain level replicas will
    // be ignored.
    patchClusterResourceRemoveReplicas();

    String originalImage = MII_BASIC_IMAGE_NAME + ":" + MII_BASIC_IMAGE_TAG;
    String newImage = MII_BASIC_IMAGE_NAME + ":newtag";
    if (KIND_REPO != null) {
      testUntil(
          tagAndPushToKind(originalImage, newImage),
          logger,
          "tagAndPushToKind for image {0} to be successful",
          newImage);
    } else {
      dockerTag(originalImage, newImage);
    }

    OffsetDateTime timestamp = now();

    // patch the domain with replicas value more than max cluster size
    String patchStr
        = "["
        + "{\"op\": \"replace\", \"path\": \"/spec/image\", \"value\": " + "\"" + newImage + "\"}, "
        + "{\"op\": \"replace\", \"path\": \"/spec/replicas\", \"value\": 10}"
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
        + " failed due to 'Replicas too high': 10 replicas specified for cluster '"
        + clusterName
        + "' which has a maximum cluster size of 5.";
    checkDomainEventContainsExpectedMsg(opNamespace, domainNamespace, domainUid, DOMAIN_FAILED, "Warning",
        timestamp, expectedErrorMsg);

    // verify up to 5 (max cluster size) pods are up and running
    for (int i = 1; i <= DEFAULT_MAX_CLUSTER_SIZE; i++) {
      checkPodReadyAndServiceExists(managedServerPrefix + i, domainUid, domainNamespace);
    }

    // restore the domain image and replicas
    patchStr
        = "["
        + "{\"op\": \"replace\", \"path\": \"/spec/image\", \"value\": " + "\"" + originalImage + "\"}, "
        + "{\"op\": \"replace\", \"path\": \"/spec/replicas\", \"value\": 1}"
        + "]";
    patch = new V1Patch(patchStr);
    logger.info("Patching domain resource using patch string {0} ", patchStr);
    response =
        patchDomainCustomResourceReturnResponse(domainUid, domainNamespace, patch, V1Patch.PATCH_FORMAT_JSON_PATCH);
    // verify patching domain succeeds
    assertTrue(response.contains("Succeeded with response code: 200"),
        String.format("patching domain should succeed but failed with response msg: %s", response));

    // check only managed server1 pod exists, all other managed server pods are deleted
    for (int i = DEFAULT_MAX_CLUSTER_SIZE; i > 1; i--) {
      checkPodDeleted(managedServerPrefix + i, domainUid, domainNamespace);
    }
    checkPodReadyAndServiceExists(managedServerPrefix + "1", domainUid, domainNamespace);

    // restore the cluster resource with replicas
    restoreClusterResourceWithReplicas();
  }

  private void patchClusterResourceRemoveReplicas() {
    String patchStr
        = "["
        + "{\"op\": \"remove\", \"path\": \"/spec/replicas\"}"
        + "]";
    V1Patch patch = new V1Patch(patchStr);
    logger.info("Patching the cluster resource using patching string {0}", patchStr);
    String response =
        patchClusterCustomResourceReturnResponse(clusterName, domainNamespace, patch, V1Patch.PATCH_FORMAT_JSON_PATCH);
    assertTrue(response.contains("Succeeded with response code: 200"),
        String.format("patching cluster %s in namespace %s failed with error msg: %s",
            clusterName, response, domainNamespace));
    // verify managed server 2 is deleted
    checkPodDeleted(managedServerPrefix + "2", domainUid, domainNamespace);
  }

  private void restoreClusterResourceWithReplicas() {
    String patchStr
        = "["
        + "{\"op\": \"add\", \"path\": \"/spec/replicas\", \"value\": " + replicaCount + "}"
        + "]";
    V1Patch patch = new V1Patch(patchStr);
    logger.info("Patching the cluster resource using patching string {0}", patchStr);
    String response =
        patchClusterCustomResourceReturnResponse(clusterName, domainNamespace, patch, V1Patch.PATCH_FORMAT_JSON_PATCH);
    assertTrue(response.contains("Succeeded with response code: 200"),
        String.format("patching cluster %s in namespace %s failed with error msg: %s",
            clusterName, response, domainNamespace));

    // check the managed server pods are up and running
    for (int i = 1; i <= replicaCount; i++) {
      checkPodReadyAndServiceExists(managedServerPrefix + i, domainUid, domainNamespace);
    }
  }
}
