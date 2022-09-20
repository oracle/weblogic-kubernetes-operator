// Copyright (c) 2022, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.weblogic.kubernetes;

import java.util.List;

import io.kubernetes.client.custom.V1Patch;
import oracle.weblogic.kubernetes.annotations.IntegrationTest;
import oracle.weblogic.kubernetes.annotations.Namespaces;
import oracle.weblogic.kubernetes.logging.LoggingFacade;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static oracle.weblogic.kubernetes.TestConstants.ADMIN_SERVER_NAME_BASE;
import static oracle.weblogic.kubernetes.TestConstants.MANAGED_SERVER_NAME_BASE;
import static oracle.weblogic.kubernetes.TestConstants.MII_BASIC_IMAGE_NAME;
import static oracle.weblogic.kubernetes.TestConstants.MII_BASIC_IMAGE_TAG;
import static oracle.weblogic.kubernetes.actions.TestActions.patchClusterCustomResourceReturnResponse;
import static oracle.weblogic.kubernetes.actions.TestActions.patchDomainCustomResourceReturnResponse;
import static oracle.weblogic.kubernetes.utils.CommonMiiTestUtils.createMiiDomainAndVerify;
import static oracle.weblogic.kubernetes.utils.OperatorUtils.installAndVerifyOperator;
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

    // get namespaces
    assertNotNull(namespaces.get(0), String.format("Namespace namespaces.get(0) is null"));
    opNamespace = namespaces.get(0);

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

  /**
   * Verify that when a domain and its clusters are running, a kubectl command that attempts to increase
   * the domain level or cluster level replicas to a value that exceeds the WebLogic cluster size will be rejected
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
   * the domain level or cluster level replicas to a value that exceeds the WebLogic cluster size will be rejected
   * with a clear error message.
   */
  @Test
  @DisplayName("Verify increasing the replicas of a domain too high will be rejected")
  void testDomainReplicasTooHigh() {
    // We need to patch the cluster resource to remove the replicas first. Otherwise, the domain level replicas will
    // be ignored.
    String patchStr
        = "["
        + "{\"op\": \"remove\", \"path\": \"/spec/replicas\"}"
        + "]";
    V1Patch patch = new V1Patch(patchStr);
    logger.info("Patching the cluster resource using patching string {0}", patchStr);
    patchClusterCustomResourceReturnResponse(clusterName, domainNamespace, patch, V1Patch.PATCH_FORMAT_JSON_PATCH);

    // patch the domain with replicas value more than max cluster size
    /*
    patchStr
        = "["
        + "{\"op\": \"replace\", \"path\": \"/spec/replicas\", \"value\": 10}"
        + "]";
    patch = new V1Patch(patchStr);
    logger.info("Patching domain resource using patch string {0} ", patchStr);
    String responseMsg =
        patchDomainCustomResourceReturnResponse(domainUid, domainNamespace, patch, V1Patch.PATCH_FORMAT_JSON_PATCH);
    String expectedErrorMsg =
        "admission webhook \"weblogic.validating.webhook\" denied the request: Change request to domain resource '"
            + domainUid
            + "' cannot be honored because the replica count would exceed the cluster size '5' when patching "
            + domainUid
            + " in namespace "
            + domainNamespace;

    assertTrue(responseMsg.contains(expectedErrorMsg),
        "Patching cluster replicas did not return the expected error msg: " + expectedErrorMsg);
    */
  }

}
