// Copyright (c) 2021, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.weblogic.kubernetes;

import java.io.IOException;
import java.util.List;

import io.kubernetes.client.openapi.ApiException;
import oracle.weblogic.kubernetes.actions.impl.primitive.Slammer;
import oracle.weblogic.kubernetes.actions.impl.primitive.SlammerParams;
import oracle.weblogic.kubernetes.annotations.IntegrationTest;
import oracle.weblogic.kubernetes.annotations.Namespaces;
import oracle.weblogic.kubernetes.logging.LoggingFacade;
import oracle.weblogic.kubernetes.utils.PodUtils;
import oracle.weblogic.kubernetes.utils.SlammerUtils;
import org.awaitility.core.ConditionFactory;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.opentest4j.AssertionFailedError;

import static java.util.concurrent.TimeUnit.MINUTES;
import static java.util.concurrent.TimeUnit.SECONDS;
import static oracle.weblogic.kubernetes.TestConstants.ADMIN_PASSWORD_DEFAULT;
import static oracle.weblogic.kubernetes.TestConstants.ADMIN_SERVER_NAME_BASE;
import static oracle.weblogic.kubernetes.TestConstants.ADMIN_USERNAME_DEFAULT;
import static oracle.weblogic.kubernetes.TestConstants.MANAGED_SERVER_NAME_BASE;
import static oracle.weblogic.kubernetes.TestConstants.MII_BASIC_IMAGE_NAME;
import static oracle.weblogic.kubernetes.TestConstants.MII_BASIC_IMAGE_TAG;
import static oracle.weblogic.kubernetes.TestConstants.WEBLOGIC_SLIM;
import static oracle.weblogic.kubernetes.actions.TestActions.getServiceNodePort;
import static oracle.weblogic.kubernetes.actions.TestActions.scaleCluster;
import static oracle.weblogic.kubernetes.assertions.TestAssertions.adminNodePortAccessible;
import static oracle.weblogic.kubernetes.utils.CommonMiiTestUtils.createMiiDomainAndVerify;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.checkPodReadyAndServiceExists;
import static oracle.weblogic.kubernetes.utils.OperatorUtils.installAndVerifyOperator;
import static oracle.weblogic.kubernetes.utils.PodUtils.checkPodDoesNotExist;
import static oracle.weblogic.kubernetes.utils.PodUtils.getExternalServicePodName;
import static oracle.weblogic.kubernetes.utils.SlammerUtils.changeTraffic;
import static oracle.weblogic.kubernetes.utils.SlammerUtils.generateSlammerInPodPropertiesFile;
import static oracle.weblogic.kubernetes.utils.SlammerUtils.setupSlammerInPod;
import static oracle.weblogic.kubernetes.utils.ThreadSafeLogger.getLogger;
import static org.awaitility.Awaitility.with;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeFalse;


// Test resilience using slammer
@DisplayName("Test resilience using slammer")
@IntegrationTest
class ItResilience {
  private static String opNamespace = null;
  private static String domainNamespace = null;
  private static String domainUid = "domain1";
  private static ConditionFactory withStandardRetryPolicy = null;

  private static String adminServerPodName = String.format("%s-%s", domainUid, ADMIN_SERVER_NAME_BASE);
  private static String managedServerPrefix = String.format("%s-%s", domainUid, MANAGED_SERVER_NAME_BASE);
  private static int replicaCount = 2;
  private static LoggingFacade logger = null;
  private static String ingressHost = null; //only used for OKD
  private static String adminServerContainerID = null;

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
    // create standard, reusable retry/backoff policy
    withStandardRetryPolicy = with().pollDelay(2, SECONDS)
        .and().with().pollInterval(10, SECONDS)
        .atMost(6, MINUTES).await();

    //install slammer
    Slammer.installSlammer();

    //check if slammer is up
    assertTrue(Slammer.list("network"), "Can't reach slammer");
    // get namespaces
    assertNotNull(namespaces.get(0), "Namespace namespaces.get(0) is null");
    opNamespace = namespaces.get(0);

    assertNotNull(namespaces.get(1), "Namespace namespaces.get(1) is null");
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
   * verify the cluster is scaled up.
   */
  @Test
  @DisplayName("increase replica count for the domain, and verify cluster is scaled up")
  void testNetworkDelayVerifyScaling() {

    try {
      //check if slammer is up
      assertTrue(Slammer.list("network"), "Can't reach slammer");

      // check new server is started and existing servers are running
      logger.info("Check admin service and pod {0} is created in namespace {1}",
          adminServerPodName, domainNamespace);
      checkPodReadyAndServiceExists(adminServerPodName, domainUid, domainNamespace);

      // check managed server services and pods are ready
      for (int i = 1; i <= replicaCount; i++) {
        logger.info("Wait for managed server pod {0} to be ready in namespace {1}",
            managedServerPrefix + i, domainNamespace);
        checkPodReadyAndServiceExists(managedServerPrefix + i, domainUid, domainNamespace);
      }

      Thread t2 = new ScalingUpThread(5);
      SlammerParams params = new SlammerParams().delay("9");
      Thread t1 = new SlammerThread(params);
      t1.start();
      t2.start();

      assertDoesNotThrow(() -> t2.join(100 * 1000), "failed to join thread");
      assertDoesNotThrow(() -> t1.join(100 * 1000), "failed to join thread");
      // check managed server services and pods are ready
      for (int i = 1; i <= 5; i++) {
        logger.info("Wait for managed server pod {0} to be ready in namespace {1}",
            managedServerPrefix + i, domainNamespace);
        checkPodReadyAndServiceExists(managedServerPrefix + i, domainUid, domainNamespace);
      }
    } finally {
      SlammerUtils.deleteNetworkLatencyDelay(null);
    }
  }

  /**
   * verify that admin console is not accessible if port 7001 is blocked
   * verify the operator can't scale domain if admin port is blocked.
   */
  @Test
  @DisplayName("execute slammer inside the admin pod and block and release port 7001")
  void testBlockPortInsideThePod() {
    String slammerPodPropertyFile = null;
    try {

      if (adminServerContainerID == null) {
        try {
          adminServerContainerID = PodUtils.getDockerContainerID(
              domainNamespace, "weblogic-server", adminServerPodName);
          logger.info("AdminServer Container ID " + adminServerContainerID);
        } catch (ApiException ex) {
          logger.info("Got exception, command failed with errors " + ex.getMessage());
        }
      }
      assertNotNull(adminServerContainerID,"Failed to retrieve admin server pod container id");

      try {
        logger.info("Generating slammer property file for AdminServer pod ");
        slammerPodPropertyFile = generateSlammerInPodPropertiesFile("localhost",
            "test@oracle.com", adminServerContainerID, "adminpod.props");
      } catch (Exception ex) {
        logger.info("Got exception during slammer property file generation for AdminServer pod, "
            + "command failed with errors " + ex.getMessage());
      }
      assertNotNull(slammerPodPropertyFile, "Failed to generate slammer property file for pod");
      logger.info("Running slammer setup inside the admin server pod");
      setupSlammerInPod(slammerPodPropertyFile);
      logger.info("Running slammer command to block incoming traffic to port 7001 inside the admin server pod");
      changeTraffic("incoming", "7001", "block", null, slammerPodPropertyFile);
      assertFalse(testAdminConsoleLogin(), "Access to console did not fail, port was not blocked");
      logger.info("Running slammer command to block incoming traffic to port 8001 inside the admin server pod");
      changeTraffic("incoming", "8001", "block", null, slammerPodPropertyFile);
      logger.info("Attempt to scale domain to 1 replica");
      runScaleOperation(1);
      logger.info("Check that the managed server2 still running, since operator "
          + "can't connect to 7001,8001 and perfome scale operation");
      checkPodReadyAndServiceExists(managedServerPrefix + 2, domainUid, domainNamespace);

    } finally {
      logger.info("Running slammer command to unblock incoming traffic to port 7001 inside the admin server pod");
      changeTraffic("incoming", "7001", "delete", null, slammerPodPropertyFile);
      changeTraffic("incoming", "8001", "delete", null, slammerPodPropertyFile);
      logger.info("check that the managed server2 is not running, since operator now can perfom scaledown operation");
      checkPodDoesNotExist(managedServerPrefix + 2, domainUid, domainNamespace);
      runScaleOperation(2);
    }
  }

  private boolean testAdminConsoleLogin() {

    assumeFalse(WEBLOGIC_SLIM, "Skipping the Console Test for slim image");

    String adminServerPodName = domainUid + "-" + ADMIN_SERVER_NAME_BASE;
    logger.info("Getting node port for default channel");
    int serviceNodePort = assertDoesNotThrow(() -> getServiceNodePort(
        domainNamespace, getExternalServicePodName(adminServerPodName), "default"),
        "Getting admin server node port failed");

    logger.info("Validating WebLogic admin server access by login to console");
    try {
      adminNodePortAccessible(serviceNodePort, ADMIN_USERNAME_DEFAULT, ADMIN_PASSWORD_DEFAULT);
      return true;
    } catch (AssertionFailedError ex) {
      logger.info("Access to admin server node port failed", ex.getMessage());
      return false;
    } catch (IOException e) {
      logger.info("Failed to check to access to admin server node port ", e.getMessage());
      return false;
    }
  }

  private void runScaleOperation(int replicaCount) {
    // scale up the domain by increasing replica count
    boolean scalingSuccess = assertDoesNotThrow(() ->
            scaleCluster(domainUid, domainNamespace, "cluster-1", replicaCount),
        String.format("Scaling the cluster cluster-1 of domain %s in namespace %s failed", domainUid, domainNamespace));
    assertTrue(scalingSuccess,
        String.format("Cluster scaling failed for domain %s in namespace %s", domainUid, domainNamespace));
  }

  class ScalingUpThread extends Thread {
    int replicaCount;

    ScalingUpThread(int replicaCount) {
      this.replicaCount = replicaCount;
    }

    public void run() {
      logger.info("Started Scaling thread");
      runScaleOperation(replicaCount);
      logger.info("Finished Scaling thread");
    }
  }

  class SlammerThread extends Thread {
    SlammerParams params;

    SlammerThread(SlammerParams params) {
      this.params = params;

    }

    public void run() {
      logger.info("Started Slammer thread");
      logger.info("Adding Network delay for " +  params.getDelay());
      Slammer.list("network");
      assertTrue(SlammerUtils.addNetworkLatencyDelay(params.getDelay(), null), "addNetworkLatencyDelay failed");
      Slammer.list("network");
      logger.info("Finished Slammer thread");
    }
  }


}
