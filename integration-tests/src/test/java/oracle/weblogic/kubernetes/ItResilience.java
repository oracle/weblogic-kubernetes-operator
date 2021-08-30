// Copyright (c) 2020, 2021, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.weblogic.kubernetes;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;


import oracle.weblogic.kubernetes.actions.impl.primitive.Slammer;
import oracle.weblogic.kubernetes.actions.impl.primitive.SlammerParams;
import oracle.weblogic.kubernetes.annotations.IntegrationTest;
import oracle.weblogic.kubernetes.annotations.Namespaces;
import oracle.weblogic.kubernetes.logging.LoggingFacade;
import oracle.weblogic.kubernetes.utils.SlammerUtils;
import org.awaitility.core.ConditionFactory;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static java.util.concurrent.TimeUnit.MINUTES;
import static java.util.concurrent.TimeUnit.SECONDS;
import static oracle.weblogic.kubernetes.TestConstants.ADMIN_PASSWORD_DEFAULT;
import static oracle.weblogic.kubernetes.TestConstants.ADMIN_PASSWORD_PATCH;
import static oracle.weblogic.kubernetes.TestConstants.ADMIN_SERVER_NAME_BASE;
import static oracle.weblogic.kubernetes.TestConstants.ADMIN_USERNAME_DEFAULT;
import static oracle.weblogic.kubernetes.TestConstants.ADMIN_USERNAME_PATCH;
import static oracle.weblogic.kubernetes.TestConstants.MANAGED_SERVER_NAME_BASE;
import static oracle.weblogic.kubernetes.TestConstants.MII_BASIC_IMAGE_NAME;
import static oracle.weblogic.kubernetes.TestConstants.MII_BASIC_IMAGE_TAG;
import static oracle.weblogic.kubernetes.TestConstants.OKD;
import static oracle.weblogic.kubernetes.TestConstants.OPERATOR_RELEASE_NAME;
import static oracle.weblogic.kubernetes.actions.TestActions.deletePod;
import static oracle.weblogic.kubernetes.actions.TestActions.getOperatorPodName;
import static oracle.weblogic.kubernetes.actions.TestActions.getPodCreationTimestamp;
import static oracle.weblogic.kubernetes.actions.TestActions.scaleCluster;
import static oracle.weblogic.kubernetes.actions.TestActions.startOperator;
import static oracle.weblogic.kubernetes.actions.TestActions.stopOperator;
import static oracle.weblogic.kubernetes.assertions.TestAssertions.isOperatorPodRestarted;
import static oracle.weblogic.kubernetes.assertions.TestAssertions.operatorIsReady;
import static oracle.weblogic.kubernetes.assertions.TestAssertions.verifyRollingRestartOccurred;
import static oracle.weblogic.kubernetes.utils.CommonMiiTestUtils.createMiiDomainAndVerify;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.checkPodReadyAndServiceExists;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.verifyCredentials;
import static oracle.weblogic.kubernetes.utils.OKDUtils.createRouteForOKD;
import static oracle.weblogic.kubernetes.utils.OperatorUtils.installAndVerifyOperator;
import static oracle.weblogic.kubernetes.utils.PatchDomainUtils.checkPodRestartVersionUpdated;
import static oracle.weblogic.kubernetes.utils.PatchDomainUtils.patchDomainWithNewSecretAndVerify;
import static oracle.weblogic.kubernetes.utils.PodUtils.checkPodDoesNotExist;
import static oracle.weblogic.kubernetes.utils.PodUtils.checkPodRestarted;
import static oracle.weblogic.kubernetes.utils.SecretUtils.createSecretWithUsernamePassword;
import static oracle.weblogic.kubernetes.utils.ThreadSafeLogger.getLogger;
import static org.awaitility.Awaitility.with;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;


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

    //runScaleOperation(5);
    Slammer.installSlammer();
    //check if slammer is up

    assertTrue(Slammer.list("network"), "Can't reach slammer");
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
   * Stop Operator and delete the admin and managed server pods.
   * Restart Operator and verify admin and managed servers are started.
   */
  //@Test
  @DisplayName("Stop operator, delete all the server pods and restart operator, verify servers are started")
  void testRestartOperatorAndVerifyDomainUp() {

    // get operator pod name
    String operatorPodName = assertDoesNotThrow(
        () -> getOperatorPodName(OPERATOR_RELEASE_NAME, opNamespace));
    assertNotNull(operatorPodName, "Operator pod name returned is null");
    logger.info("Operator pod name {0}", operatorPodName);

    // stop operator by changing replica to 0 in operator deployment
    assertTrue(stopOperator(opNamespace), "Couldn't stop the Operator");

    // check operator pod is not running
    checkPodDoesNotExist(operatorPodName, null, opNamespace);

    // delete server pods
    for (int i = 1; i <= replicaCount; i++) {
      final String managedServerPodName = managedServerPrefix + i;
      logger.info("Deleting managed server {0} in namespace {1}", managedServerPodName, domainNamespace);
      assertDoesNotThrow(() -> deletePod(managedServerPodName, domainNamespace),
          "Got exception while deleting server " + managedServerPodName);
      checkPodDoesNotExist(managedServerPodName, domainUid, domainNamespace);
    }

    logger.info("deleting admin server pod");
    assertDoesNotThrow(() -> deletePod(adminServerPodName, domainNamespace),
        "Got exception while deleting admin server pod");
    checkPodDoesNotExist(adminServerPodName, domainUid, domainNamespace);

    // start operator by changing replica to 1 in operator deployment
    assertTrue(startOperator(opNamespace), "Couldn't start the Operator");

    // check operator is running
    logger.info("Check Operator pod is running in namespace {0}", opNamespace);
    withStandardRetryPolicy
        .conditionEvaluationListener(
            condition -> logger.info("Waiting for operator to be running in namespace {0} "
                    + "(elapsed time {1}ms, remaining time {2}ms)",
                opNamespace,
                condition.getElapsedTimeInMS(),
                condition.getRemainingTimeInMS()))
        .until(operatorIsReady(opNamespace));

    logger.info("Check admin service and pod {0} is created in namespace {1}",
        adminServerPodName, domainNamespace);
    checkPodReadyAndServiceExists(adminServerPodName, domainUid, domainNamespace);

    // check managed server services and pods are ready
    for (int i = 1; i <= replicaCount; i++) {
      logger.info("Wait for managed server services and pods are created in namespace {0}",
          domainNamespace);
      checkPodReadyAndServiceExists(managedServerPrefix + i, domainUid, domainNamespace);
    }
  }

  /**
   * verify the cluster is scaled up.
   */
  @Test
  @DisplayName("increase replica count for the domain, and verify cluster is scaled up")
  void testNetworkDelayVerifyScaling() {

    //runScaleOperation(5);
    //Slammer.installSlammer();
    //check if slammer is up
  try {
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
    SlammerUtils.deleteNetworkLatencyDelay();
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
      logger.info("Started Scaling thread" );
      runScaleOperation(replicaCount);
      logger.info("Finished Scaling thread" );
    }
  }

  class SlammerThread extends Thread {
    SlammerParams params;

    SlammerThread(SlammerParams params) {
      this.params = params;

    }

    public void run() {
      logger.info("Started Slammer thread" );
      logger.info("Adding Network delay for " +  params.getDelay());
      Slammer.list("network");
      assertTrue(SlammerUtils.addNetworkLatencyDelay(params.getDelay()), "addNetworkLatencyDelay failed");
      Slammer.list("network");
      logger.info("Finished Slammer thread" );
    }
  }

}
