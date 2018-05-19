// Copyright 2018, Oracle Corporation and/or its affiliates.  All rights reserved.
// Licensed under the Universal Permissive License v 1.0 as shown at
// http://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator;

import oracle.kubernetes.operator.utils.TestUtils;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

/**
 * Simple JUnit test file used for testing Operator.
 *
 * <p>This test is used for creating Operator and a single domain which the Operator manages and
 * verifies both.
 *
 */
public class ITSingleDomain extends BaseTest {

  /**
   * Create Operator and domain
   *
   * @throws Exception
   */
  @BeforeClass
  public static void staticPrepare() throws Exception {
    logger.info("+++++++++++++++++++++++++++++++++---------------------------------+");
    logger.info("BEGIN");
    logger.info("Run once, Creating Operator & " + "waiting for the script to complete execution");

    opPropsFile = "ITSingleDomain_op.properties";
    domainPropsFile = "ITSingleDomain_domain.properties";
    appPropsFile = "OperatorIT.properties";
    //setup creates operator and domain and verifies they are up
    setup();

    logger.info("SUCCESS");
  }

  /**
   * Shutdown operator and domain
   *
   * @throws Exception
   */
  @AfterClass
  public static void staticUnPrepare() throws Exception {
    logger.info("+++++++++++++++++++++++++++++++++---------------------------------+");
    logger.info("BEGIN");
    logger.info("Run once, shutdown/deleting operator, domain, pv, etc");
    //shutdown operator, domain and cleanup all artifacts and pv dir
    shutdownAndCleanup();
    logger.info("SUCCESS");
  }

  /** Access Operator REST endpoint using admin node host and node port */
  @Test
  public void testAdminServerExternalService() {
    logger.info("+++++++++++++++++++++++++++++++++---------------------------------+");
    logger.info("BEGIN");
    domain.verifyAdminServerExternalService(username, password);
    logger.info("SUCCESS");
  }

  /** Verify t3channel port by deploying webapp using the port */
  @Test
  public void testAdminT3Channel() {
    logger.info("+++++++++++++++++++++++++++++++++---------------------------------+");
    logger.info("BEGIN");
    if (domainProps.getProperty("exposeAdminT3Channel") == null
        || !(new Boolean(domainProps.getProperty("exposeAdminT3Channel")).booleanValue())) {
      throw new RuntimeException("FAILURE: exposeAdminT3Channel is not set or false");
    } else {
      if (new Boolean(domainProps.getProperty("exposeAdminT3Channel")).booleanValue()) {
        domain.deployWebAppViaWLST(
            TESTWEBAPP, "../src/integration-tests/apps/testwebapp.war", username, password);
      }
    }
    domain.verifyWebAppLoadBalancing(TESTWEBAPP);
    logger.info("SUCCESS");
  }

  /**
   * Restarting the domain should not have any impact on Operator managing the domain, web app load
   * balancing and node port service
   *
   * @throws Exception
   */
  @Test
  public void testDomainLifecyle() throws Exception {
    logger.info("+++++++++++++++++++++++++++++++++---------------------------------+");
    logger.info("BEGIN");
    domain.shutdown();
    domain.startup();
    operator.verifyExternalRESTService();
    operator.verifyDomainExists(domainUid);
    domain.verifyDomainCreated();
    domain.verifyWebAppLoadBalancing(TESTWEBAPP);
    domain.verifyAdminServerExternalService(username, password);
    logger.info("SUCCESS");
  }

  /**
   * Scale the cluster up/down using Operator REST endpoint, load balancing should adjust
   * accordingly.
   *
   * @throws Exception
   */
  @Test
  public void testClusterScaling() throws Exception {
    logger.info("+++++++++++++++++++++++++++++++++---------------------------------+");
    logger.info("BEGIN");
    String managedServerNameBase = domainProps.getProperty("managedServerNameBase");
    int scaleNum = 3;
    String podName = domainUid + "-" + managedServerNameBase + scaleNum;
    String clusterName = domainProps.getProperty("clusterName");

    logger.info("Scale Up to " + scaleNum + " managed servers");
    operator.scale(domainUid, domainProps.getProperty("clusterName"), scaleNum);

    logger.info("Checking if managed pod(" + podName + ") is Running");
    TestUtils.checkPodCreated(podName, domainNS);

    logger.info("Checking if managed server (" + podName + ") is Running");
    TestUtils.checkPodReady(podName, domainNS);

    logger.info("Checking if managed service(" + podName + ") is created");
    TestUtils.checkServiceCreated(podName, domainNS);

    int replicaCnt = TestUtils.getClusterReplicas(domainUid, clusterName, domainNS);
    if (replicaCnt != scaleNum) {
      throw new RuntimeException(
          "FAILURE: Cluster replica doesn't match with scaled up size "
              + replicaCnt
              + "/"
              + scaleNum);
    }

    domain.verifyWebAppLoadBalancing(TESTWEBAPP);

    scaleNum = 2;
    podName = domainUid + "-" + managedServerNameBase + (scaleNum + 1);
    logger.info("Scale down to " + scaleNum + " managed servers");
    operator.scale(domainUid, clusterName, scaleNum);

    logger.info("Checking if managed pod(" + podName + ") is deleted");
    TestUtils.checkPodDeleted(podName, domainNS);

    replicaCnt = TestUtils.getClusterReplicas(domainUid, clusterName, domainNS);
    if (replicaCnt != scaleNum) {
      throw new RuntimeException(
          "FAILURE: Cluster replica doesn't match with scaled down size "
              + replicaCnt
              + "/"
              + scaleNum);
    }

    domain.verifyWebAppLoadBalancing(TESTWEBAPP);
    logger.info("SUCCESS");
  }

  /**
   * Restarting Operator should not impact the running domain
   *
   * @throws Exception
   */
  @Test
  public void testOperatorLifecycle() throws Exception {
    logger.info("+++++++++++++++++++++++++++++++++---------------------------------+");
    logger.info("BEGIN");
    operator.shutdown();
    operator.startup();
    operator.verifyExternalRESTService();
    operator.verifyDomainExists(domainUid);
    domain.verifyDomainCreated();
    logger.info("SUCCESS");
  }


}
