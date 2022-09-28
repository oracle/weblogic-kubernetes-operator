// Copyright (c) 2022, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.weblogic.kubernetes;

import java.util.Base64;
import java.util.List;

import oracle.weblogic.domain.DomainResource;
import oracle.weblogic.kubernetes.actions.impl.Secret;
import oracle.weblogic.kubernetes.actions.impl.primitive.Command;
import oracle.weblogic.kubernetes.actions.impl.primitive.CommandParams;
import oracle.weblogic.kubernetes.actions.impl.primitive.HelmParams;
import oracle.weblogic.kubernetes.annotations.IntegrationTest;
import oracle.weblogic.kubernetes.annotations.Namespaces;
import oracle.weblogic.kubernetes.logging.LoggingFacade;
import oracle.weblogic.kubernetes.utils.ExecResult;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static oracle.weblogic.kubernetes.TestConstants.ADMIN_SERVER_NAME_BASE;
import static oracle.weblogic.kubernetes.TestConstants.MANAGED_SERVER_NAME_BASE;
import static oracle.weblogic.kubernetes.TestConstants.MII_BASIC_IMAGE_NAME;
import static oracle.weblogic.kubernetes.TestConstants.MII_BASIC_IMAGE_TAG;
import static oracle.weblogic.kubernetes.TestConstants.OPERATOR_CHART_DIR;
import static oracle.weblogic.kubernetes.TestConstants.OPERATOR_RELEASE_NAME;
import static oracle.weblogic.kubernetes.actions.TestActions.getServiceNodePort;
import static oracle.weblogic.kubernetes.utils.CommonMiiTestUtils.createMiiDomainAndVerify;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.getHostAndPort;
import static oracle.weblogic.kubernetes.utils.ImageUtils.createTestRepoSecret;
import static oracle.weblogic.kubernetes.utils.OKDUtils.getRouteHost;
import static oracle.weblogic.kubernetes.utils.OperatorUtils.installAndVerifyOperator;
import static oracle.weblogic.kubernetes.utils.PodUtils.checkPodExists;
import static oracle.weblogic.kubernetes.utils.ThreadSafeLogger.getLogger;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Simple JUnit test file used for testing operator usability.
 * Use Helm chart to install operator(s)
 */
@DisplayName("Test scaling usability ")
@IntegrationTest
@Tag("olcne")
@Tag("oke-parallel")
@Tag("kind-parallel")
@Tag("okd-wls-mrg")
class ItClusterResourceScaling {
  private static String opNamespace = null;
  private static String domainNamespace = null;

  // domain constants
  private final String domainUid = "usabdomain";

  private final String clusterName = "cluster-1";
  private final int replicaCount = 2;
  private final String adminServerPrefix = "-" + ADMIN_SERVER_NAME_BASE;
  private final String managedServerPrefix = domainUid + "-" + MANAGED_SERVER_NAME_BASE;
  private boolean isDomainRunning = false;
  private String adminSvcExtRouteHost = null;

  private static LoggingFacade logger = null;

  /**
   * Get namespaces for operator, domain.
   *
   * @param namespaces list of namespaces created by the IntegrationTestWatcher by the
   *                   JUnit engine parameter resolution mechanism
   */
  @BeforeAll
  public static void initAll(@Namespaces(2) List<String> namespaces) {
    logger = getLogger();
    // get a unique operator namespace
    logger.info("Getting a unique namespace for operator");
    assertNotNull(namespaces.get(0), "Namespace list is null");
    opNamespace = namespaces.get(0);

    // get a unique domain namespace
    logger.info("Getting a unique namespace for WebLogic domain");
    assertNotNull(namespaces.get(1), "Namespace list is null");
    domainNamespace = namespaces.get(1);
    createTestRepoSecret(domainNamespace);
  }

  /**
   * Install the Operator successfully.
   * Create domain and verify the domain is started
   * Verify cluster can be scale via Rest Api
   * Verify expected http responce in case of :
   * 1. Provided invalid auth token
   * 2. Provided invalid domainuid
   * 3. Provided invalid clustername
   * 4. Provided above max replica
   * 5. Missed header
   * 6 Missed auth header
   */
  @Test
  @DisplayName("Create domain, managed by operator, perfom scaling operation via REST  "
      + "verify scaling operation generate expected code in case of negative scenarios: bad auth "
      + "invalid domainuid, invalid clustername, invalid replica count, invalid request header, invalid auth header")
  void testScaleClusterViaRestApi() {
    HelmParams opHelmParams = null;
    HelmParams op1HelmParams = new HelmParams().releaseName(OPERATOR_RELEASE_NAME)
        .namespace(opNamespace)
        .chartDir(OPERATOR_CHART_DIR);
    String opServiceAccount = opNamespace + "-sa";
    boolean badAuthFailed = false;
    boolean invalidDomainUidFailed = false;
    boolean invalidClusterNameFailed = false;
    boolean invalidReplicaNumberFailed = false;
    boolean invalidHeaderFailed = false;
    boolean invalidAuthHeaderFailed = false;

    StringBuffer failedNegativeTestCases = new StringBuffer();
    String negativeTestCase1 = "Bad authentication";
    String negativeTestCase2 = "Invalid domainUid";
    String negativeTestCase3 = "Invalid clusterName";
    String negativeTestCase4 = "Invalid replicaNumber";
    String negativeTestCase5 = "Invalid request header";
    String negativeTestCase6 = "Invalid auth header";
    try {
      // install operator
      opHelmParams = installAndVerifyOperator(opNamespace, opServiceAccount, true,
          0, op1HelmParams, domainNamespace).getHelmParams();
      assertNotNull(opHelmParams, "Can't install operator");

      int externalRestHttpsPort = getServiceNodePort(opNamespace, "external-weblogic-operator-svc");
      assertNotEquals(-1, externalRestHttpsPort,
          "Could not get the Operator external service node port");
      logger.info("externalRestHttpsPort {0}", externalRestHttpsPort);
      if (!isDomainRunning) {
        logger.info("Installing and verifying domain");
        // get the pre-built image created by IntegrationTestWatcher
        String miiImage = MII_BASIC_IMAGE_NAME + ":" + MII_BASIC_IMAGE_TAG;
        String adminServerPodName = domainUid + adminServerPrefix;
        DomainResource domain = createMiiDomainAndVerify(domainNamespace, domainUid,
            miiImage, adminServerPodName, managedServerPrefix, replicaCount);
        assertNotNull(domain, "Can't create and verify domain");
        isDomainRunning = true;
      }

      // scale domain
      int replicaCountCluster = replicaCount;
      String secretToken = getServerToken(opServiceAccount);
      // decode the secret encoded token
      String decodedToken = new String(Base64.getDecoder().decode(secretToken));
      assertTrue(scaleClusterWithRestApi(domainUid, clusterName, replicaCountCluster + 1,
              externalRestHttpsPort, opNamespace, decodedToken,"",
              true, true),
          "domain in namespace " + domainNamespace + " scaling operation failed");

      String managedServerPodName = managedServerPrefix + (replicaCountCluster + 1);
      logger.info("Checking that the managed server pod {0} exists in namespace {1}",
          managedServerPodName, domainNamespace);
      assertDoesNotThrow(() -> checkPodExists(managedServerPodName, domainUid, domainNamespace),
          "operator failed to manage domain, scaling was not succeeded");
      ++replicaCountCluster;
      logger.info("domain scaled to " + replicaCountCluster + " servers");

      // decode the secret encoded token
      String decodedTokenBad = new String(Base64.getDecoder().decode(secretToken)) + "badbad";
      logger.info("Testing {0}", negativeTestCase1);
      badAuthFailed = scaleClusterWithRestApi(domainUid, clusterName, replicaCountCluster + 2,
              externalRestHttpsPort, opNamespace, decodedTokenBad, "401 Unauthorized", true,
              true);
      if (!badAuthFailed) {
        failedNegativeTestCases.append(negativeTestCase1);
      }
      logger.info("Testing {0}", negativeTestCase2);
      invalidDomainUidFailed = scaleClusterWithRestApi(domainUid + "invalid", clusterName, replicaCountCluster + 2,
              externalRestHttpsPort, opNamespace, decodedToken,  "404 Not Found", true,
              true);
      if (!invalidDomainUidFailed) {
        failedNegativeTestCases
            .append(", ")
            .append(negativeTestCase2);
      }
      logger.info("Testing {0}", negativeTestCase3);
      invalidClusterNameFailed = scaleClusterWithRestApi(domainUid, clusterName + "invalid", replicaCountCluster + 2,
              externalRestHttpsPort, opNamespace, decodedToken, "404 Not Found", true,
              true);
      if (!invalidClusterNameFailed) {
        failedNegativeTestCases
            .append(", ")
            .append(negativeTestCase3);
      }
      logger.info("Testing {0}", negativeTestCase4);
      invalidReplicaNumberFailed = scaleClusterWithRestApi(domainUid, clusterName, replicaCountCluster + 12,
          externalRestHttpsPort, opNamespace, decodedToken, "400 Requested scaling count of 15"
              + " is greater than configured cluster size of 5", true,
          true);
      if (!invalidReplicaNumberFailed) {
        failedNegativeTestCases
            .append(", ")
            .append(negativeTestCase4);
      }
      logger.info("Testing {0}", negativeTestCase5);
      invalidHeaderFailed = scaleClusterWithRestApi(domainUid, clusterName, replicaCountCluster + 2,
              externalRestHttpsPort,opNamespace, decodedToken, "400 Bad Request", false,
              true);
      if (!invalidHeaderFailed) {
        failedNegativeTestCases
            .append(", ")
            .append(negativeTestCase5);
      }
      logger.info("Testing {0}", negativeTestCase6);
      invalidAuthHeaderFailed = scaleClusterWithRestApi(domainUid, clusterName, replicaCountCluster + 2,
              externalRestHttpsPort, opNamespace, decodedToken, "401 Unauthorized", true,
              false);
      if (!invalidAuthHeaderFailed) {
        failedNegativeTestCases
            .append(", ")
            .append(negativeTestCase6);
      }
    } finally {
      assertTrue(failedNegativeTestCases.toString().equals(""),
          "Test failed to generate expected error message for negative testcases " + failedNegativeTestCases);
    }
  }

  /**
   * Scale the cluster of the domain in the specified namespace with REST API.
   *
   * @param domainUid domainUid of the domain to be scaled
   * @param clusterName name of the WebLogic cluster to be scaled in the domain
   * @param numOfServers number of servers to be scaled to
   * @param externalRestHttpsPort node port allocated for the external operator REST HTTPS interface
   * @param opNamespace namespace of WebLogic operator
   * @return true if REST call succeeds, false otherwise
   */
  public static boolean scaleClusterWithRestApi(String domainUid,
                                                String clusterName,
                                                int numOfServers,
                                                int externalRestHttpsPort,
                                                String opNamespace,
                                                String decodedToken,
                                                String expectedMsg,
                                                boolean hasHeader,
                                                boolean hasAuthHeader) {
    LoggingFacade logger = getLogger();

    String opExternalSvc = getRouteHost(opNamespace, "external-weblogic-operator-svc");


    // build the curl command to scale the cluster
    StringBuffer command = new StringBuffer()
        .append("curl --noproxy '*' -v -k ");
    if (hasAuthHeader) {
      command.append("-H \"Authorization:Bearer ")
          .append(decodedToken)
          .append("\" ");
    }
    command.append("-H Accept:application/json ")
        .append("-H Content-Type:application/json ");
    if (hasHeader) {
      command.append("-H X-Requested-By:MyClient ");
    }
    command.append("-d '{\"spec\": {\"replicas\": ")
    .append(numOfServers)
    .append("}}' ")
    .append("-X POST https://")
    .append(getHostAndPort(opExternalSvc, externalRestHttpsPort))
    .append("/operator/latest/domains/")
    .append(domainUid)
    .append("/clusters/")
    .append(clusterName)
    .append("/scale").toString();

    CommandParams params = Command
        .defaultCommandParams()
        .command(command.toString())
        .saveResults(true)
        .redirect(true);

    logger.info("Calling curl to scale the cluster");
    ExecResult result = Command.withParams(params).executeAndReturnResult();
    logger.info("Return values {0}, errors {1}", result.stdout(), result.stderr());
    if (result != null) {
      logger.info("Return values {0}, errors {1}", result.stdout(), result.stderr());
      if (result.stdout().contains(expectedMsg) || result.stderr().contains(expectedMsg)) {
        return true;
      }
    }
    return false;
  }


  private String getServerToken(String opServiceAccount) {
    logger.info("Getting the secret of service account {0} in namespace {1}", opServiceAccount, opNamespace);
    String secretName = Secret.getSecretOfServiceAccount(opNamespace, opServiceAccount);
    if (secretName.isEmpty()) {
      logger.info("Did not find secret of service account {0} in namespace {1}", opServiceAccount, opNamespace);
      return null;
    }
    logger.info("Got secret {0} of service account {1} in namespace {2}",
        secretName, opServiceAccount, opNamespace);

    logger.info("Getting service account token stored in secret {0} to authenticate as service account {1}"
        + " in namespace {2}", secretName, opServiceAccount, opNamespace);
    String secretToken = Secret.getSecretEncodedToken(opNamespace, secretName);
    if (secretToken.isEmpty()) {
      logger.info("Did not get encoded token for secret {0} associated with service account {1} in namespace {2}",
          secretName, opServiceAccount, opNamespace);
      return null;
    }
    logger.info("Got encoded token for secret {0} associated with service account {1} in namespace {2}: {3}",
        secretName, opServiceAccount, opNamespace, secretToken);
    return secretToken;
  }
}
