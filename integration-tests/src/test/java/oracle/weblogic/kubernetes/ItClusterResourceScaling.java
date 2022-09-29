// Copyright (c) 2022, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.weblogic.kubernetes;

import java.util.Base64;
import java.util.List;

import oracle.weblogic.domain.DomainResource;
import oracle.weblogic.kubernetes.actions.impl.primitive.HelmParams;
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
import static oracle.weblogic.kubernetes.TestConstants.OPERATOR_CHART_DIR;
import static oracle.weblogic.kubernetes.TestConstants.OPERATOR_RELEASE_NAME;
import static oracle.weblogic.kubernetes.actions.TestActions.getServiceNodePort;
import static oracle.weblogic.kubernetes.utils.ClusterUtils.scaleClusterWithRestApi;
import static oracle.weblogic.kubernetes.utils.CommonMiiTestUtils.createMiiDomainAndVerify;
import static oracle.weblogic.kubernetes.utils.ImageUtils.createTestRepoSecret;
import static oracle.weblogic.kubernetes.utils.OperatorUtils.installAndVerifyOperator;
import static oracle.weblogic.kubernetes.utils.PodUtils.checkPodExists;
import static oracle.weblogic.kubernetes.utils.SecretUtils.getServiceAccountToken;
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
  // domain constants
  private static String domainUid = "usabdomain";
  private static String adminServerPrefix = "-" + ADMIN_SERVER_NAME_BASE;
  private static String adminServerPodName = domainUid + adminServerPrefix;
  private static String managedServerPrefix = domainUid + "-" + MANAGED_SERVER_NAME_BASE;
  private static String opServiceAccount = opNamespace + "-sa";
  private static int replicaCount = 2;
  private static String domainNamespace = null;
  private static String clusterName = "cluster-1";
  private static int externalRestHttpsPort = 0;
  private static String secretToken;
  private static String decodedToken;
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
    HelmParams opHelmParams = null;
    HelmParams op1HelmParams = new HelmParams().releaseName(OPERATOR_RELEASE_NAME)
        .namespace(opNamespace)
        .chartDir(OPERATOR_CHART_DIR);
    // install operator
    opHelmParams = installAndVerifyOperator(opNamespace, opServiceAccount, true,
        0, op1HelmParams, domainNamespace).getHelmParams();
    assertNotNull(opHelmParams, "Can't install operator");
    logger.info("Installing and verifying domain");
    // get the pre-built image created by IntegrationTestWatcher
    String miiImage = MII_BASIC_IMAGE_NAME + ":" + MII_BASIC_IMAGE_TAG;
    DomainResource domain = createMiiDomainAndVerify(domainNamespace, domainUid,
        miiImage, adminServerPodName, managedServerPrefix, replicaCount);
    assertNotNull(domain, "Can't create and verify domain");
    externalRestHttpsPort = getServiceNodePort(opNamespace, "external-weblogic-operator-svc");
    assertNotEquals(-1, externalRestHttpsPort,
        "Could not get the Operator external service node port");
    logger.info("externalRestHttpsPort {0}", externalRestHttpsPort);
    secretToken = getServiceAccountToken(opServiceAccount, opNamespace);
    assertNotNull(secretToken, "Can't retrieve secret token");
    // decode the secret encoded token
    decodedToken = new String(Base64.getDecoder().decode(secretToken));
    assertNotNull(decodedToken, "Can't decode token");
  }

  /**
   * Verify cluster can be scale via Rest Api.
   */
  @Test
  @DisplayName("Verify scaling operation via REST.  ")
  void testScaleClusterViaRestApi() {

    int replicaCountCluster = replicaCount;
    // scale domain
    assertTrue(scaleClusterWithRestApi(domainUid, clusterName, replicaCountCluster + 1,
            externalRestHttpsPort, opNamespace, decodedToken, "",
            true, true),
        "domain in namespace " + domainNamespace + " scaling operation failed");

    String managedServerPodName = managedServerPrefix + (replicaCountCluster + 1);
    logger.info("Checking that the managed server pod {0} exists in namespace {1}",
        managedServerPodName, domainNamespace);
    assertDoesNotThrow(() -> checkPodExists(managedServerPodName, domainUid, domainNamespace),
        "operator failed to manage domain, scaling was not succeeded");
    ++replicaCountCluster;
    logger.info("domain scaled to " + replicaCountCluster + " servers");
  }

  /**
   * Verify scaling via REST operation generates expected code in case of negative scenario :
   * Provided invalid domainUid.
   */
  @Test
  @DisplayName("Perform scaling operation via REST  "
      + "verify scaling operation generates expected code in case of negative scenario: invalid domainUid. ")
  void testScaleClusterViaRestApiInvalidDomainUid() {

    String negativeTestCase = "Invalid domainUid";
    logger.info("Testing {0}", negativeTestCase);
    assertTrue(scaleClusterWithRestApi(domainUid + "invalid", clusterName, replicaCount + 2,
        externalRestHttpsPort, opNamespace, decodedToken,  "404 Not Found", true,
        true), "Did not received expected message for  " + negativeTestCase);
  }

  /**
   * Verify scaling via REST operation generates expected code in case of negative scenario :
   * Provided invalid request header.
   */
  @Test
  @DisplayName("Perform scaling operation via REST  "
      + "verify scaling operation generates expected code in case of negative scenario: invalid request header ")
  void testScaleClusterViaRestApiInvalidRequestHeader() {

    String negativeTestCase = "Invalid request header";
    logger.info("Testing {0}", negativeTestCase);
    assertTrue(scaleClusterWithRestApi(domainUid, clusterName, replicaCount + 2,
        externalRestHttpsPort,opNamespace, decodedToken, "400 Bad Request", false,
        true), "Did not received expected message for  " + negativeTestCase);
  }

  /**
   * Verify scaling via REST operation generates expected code in case of negative scenario :
   * Provided invalid request header.
   */
  @Test
  @DisplayName("Perform scaling operation via REST  "
      + "verify scaling operation generates expected code in case of negative scenario: missing auth header ")
  void testScaleClusterViaRestApiInvalidMissingAuthenticationHeader() {

    String negativeTestCase = "Invalid request header";
    logger.info("Testing {0}", negativeTestCase);
    assertTrue(scaleClusterWithRestApi(domainUid, clusterName, replicaCount + 2,
        externalRestHttpsPort, opNamespace, decodedToken, "401 Unauthorized", true,
        false), "Did not received expected message for  " + negativeTestCase);
  }

  /**
   * Verify scaling via REST operation generates expected code in case of negative scenario :
   * Provided invalid cluster name.
   */
  @Test
  @DisplayName("Perform scaling operation via REST  "
      + "verify scaling operation generates expected code in case of negative scenario: invalid cluster name ")
  void testScaleClusterViaRestApiInvalidClusterName() {

    String negativeTestCase = "Invalid cluster name";
    logger.info("Testing {0}", negativeTestCase);
    assertTrue(scaleClusterWithRestApi(domainUid + "invalid", clusterName + "invalid", replicaCount + 2,
        externalRestHttpsPort, opNamespace, decodedToken,  "404 Not Found", true,
        true), "Did not received expected message for  " + negativeTestCase);
  }


  /**
   * Verify scaling via REST operation generates expected code in case of negative scenario :
   * Provided invalid auth token.
   */
  @Test
  @DisplayName("Perform scaling operation via REST  "
      + "verify scaling operation generates expected code in case of negative scenario: bad auth ")
  void testScaleClusterViaRestApiBadAuthentication() {

    String negativeTestCase = "Bad authentication";
    // decode the secret encoded token
    String decodedTokenBad = decodedToken + "badbad";
    logger.info("Testing {0}", negativeTestCase);
    assertTrue(scaleClusterWithRestApi(domainUid, clusterName, replicaCount + 2,
        externalRestHttpsPort, opNamespace, decodedTokenBad, "401 Unauthorized", true,
        true), "Did not received expected message for  " + negativeTestCase);
  }

  /**
   * Verify scaling via REST operation generates expected code in case of negative scenario :
   * Provided invalid replica number.
   */
  @Test
  @DisplayName("Perform scaling operation via REST  "
      + "verify scaling operation generates expected code in case of negative scenario: invalid replica number ")
  void testScaleClusterViaRestApiInvalidReplicaNumber() {

    String negativeTestCase = "Invalid replica number";
    // decode the secret encoded token
    logger.info("Testing {0}", negativeTestCase);
    assertTrue(scaleClusterWithRestApi(domainUid, clusterName, 15,
        externalRestHttpsPort, opNamespace, decodedToken, "400 Requested scaling count of 15"
            + " is greater than configured cluster size of 5", true,
        true), "Did not received expected message for  " + negativeTestCase);
  }
}
