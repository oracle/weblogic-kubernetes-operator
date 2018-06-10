// Copyright 2018, Oracle Corporation and/or its affiliates.  All rights reserved.
// Licensed under the Universal Permissive License v 1.0 as shown at
// http://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator;

import java.util.Properties;
import oracle.kubernetes.operator.utils.Domain;
import oracle.kubernetes.operator.utils.ExecCommand;
import oracle.kubernetes.operator.utils.ExecResult;
import oracle.kubernetes.operator.utils.Operator;
import oracle.kubernetes.operator.utils.TestUtils;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

/**
 * Simple JUnit test file used for testing Operator.
 *
 * <p>This test is used for creating Operator and a single domain which the Operator manages and
 * verifies both.
 */
public class ITSingleDomain extends BaseTest {
  public static final String TESTWEBAPP = "testwebapp";

  // property file used to customize operator properties for operator inputs yaml
  private static String opPropsFile = "ITSingleDomain_op.properties";

  // property file used to customize domain properties for domain inputs yaml
  private static String domainPropsFile = "ITSingleDomain_domain.properties";

  // property file used to configure constants for integration tests
  private static String appPropsFile = "OperatorIT.properties";

  private static Operator operator;
  private static Domain domain;

  // properties of operator/domain to use in the test methods
  private static Properties operatorProps;
  private static Properties domainProps;

  private static String domainUid, domainNS;
  /**
   * This method gets called only once before any of the test methods are executed. It does the
   * initialization of the integration test properties defined in OperatorIT.properties and setting
   * the resultRoot, pvRoot and projectRoot attributes. It also creates the Operator and domain.
   *
   * @throws Exception
   */
  @BeforeClass
  public static void staticPrepare() throws Exception {
    logger.info("+++++++++++++++++++++++++++++++++---------------------------------+");
    logger.info("BEGIN");

    // initialize test properties and create the directories
    initialize(appPropsFile);
    // renew lease at the begining for every test method, leaseId is set only for Wercker
    TestUtils.renewK8sClusterLease(getProjectRoot(), getLeaseId());

    logger.info("Run once, Creating Operator & " + "waiting for the script to complete execution");
    // create operator
    operator = TestUtils.createOperator(opPropsFile);
    operatorProps = operator.getOperatorProps();

    // create domain
    domain = TestUtils.createDomain(domainPropsFile);
    domainProps = domain.getDomainProps();

    // initialize attributes to use in the tests
    domainUid = domainProps.getProperty("domainUID");
    domainNS = domainProps.getProperty("namespace");
    // logger.info("Domain props "+domainProps);
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
    // shutdown operator, domain and cleanup all artifacts and pv dir
    try {
      if (domain != null) domain.destroy();
      if (operator != null) operator.destroy();
    } finally {
      String cmd =
          "export RESULT_ROOT="
              + getResultRoot()
              + " export PV_ROOT="
              + getPvRoot()
              + " && "
              + getProjectRoot()
              + "/src/integration-tests/bash/cleanup.sh";
      ExecResult result = ExecCommand.exec(cmd);
      if (result.exitValue() != 0) {
        logger.info("FAILED: command to call cleanup script failed " + cmd + result.stderr());
      }
    }
    logger.info("SUCCESS");
  }

  /**
   * Access Operator REST endpoint using admin node host and node port
   *
   * @throws Exception
   */
  @Test
  public void testAdminServerExternalService() throws Exception {
    logTestBegin("testAdminServerExternalService");
    domain.verifyAdminServerExternalService(getUsername(), getPassword());
    logger.info("SUCCESS");
  }

  /**
   * Verify t3channel port by deploying webapp using the port
   *
   * @throws Exception
   */
  @Test
  public void testAdminT3Channel() throws Exception {
    logTestBegin("testAdminT3Channel");
    // check if the property is set to true
    Boolean exposeAdmint3Channel = new Boolean(domainProps.getProperty("exposeAdminT3Channel"));

    if (exposeAdmint3Channel != null && exposeAdmint3Channel.booleanValue()) {
      domain.deployWebAppViaWLST(
          TESTWEBAPP,
          getProjectRoot() + "/src/integration-tests/apps/testwebapp.war",
          getUsername(),
          getPassword());
    } else {
      throw new RuntimeException("FAILURE: exposeAdminT3Channel is not set or false");
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
    logTestBegin("testDomainLifecyle");
    domain.destroy();
    domain.create();
    operator.verifyExternalRESTService();
    operator.verifyDomainExists(domainUid);
    domain.verifyDomainCreated();
    domain.verifyWebAppLoadBalancing(TESTWEBAPP);
    domain.verifyAdminServerExternalService(BaseTest.getUsername(), BaseTest.getPassword());
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
    logTestBegin("testClusterScaling");
    String managedServerNameBase = domainProps.getProperty("managedServerNameBase");
    int replicas = 3;
    String podName = domainUid + "-" + managedServerNameBase + replicas;
    String clusterName = domainProps.getProperty("clusterName");

    logger.info("Scale domain " + domainUid + " Up to " + replicas + " managed servers");
    operator.scale(domainUid, domainProps.getProperty("clusterName"), replicas);

    logger.info("Checking if managed pod(" + podName + ") is Running");
    TestUtils.checkPodCreated(podName, domainNS);

    logger.info("Checking if managed server (" + podName + ") is Running");
    TestUtils.checkPodReady(podName, domainNS);

    logger.info("Checking if managed service(" + podName + ") is created");
    TestUtils.checkServiceCreated(podName, domainNS);

    int replicaCnt = TestUtils.getClusterReplicas(domainUid, clusterName, domainNS);
    if (replicaCnt != replicas) {
      throw new RuntimeException(
          "FAILURE: Cluster replica doesn't match with scaled up size "
              + replicaCnt
              + "/"
              + replicas);
    }

    domain.verifyWebAppLoadBalancing(TESTWEBAPP);

    replicas = 2;
    podName = domainUid + "-" + managedServerNameBase + (replicas + 1);
    logger.info("Scale down to " + replicas + " managed servers");
    operator.scale(domainUid, clusterName, replicas);

    logger.info("Checking if managed pod(" + podName + ") is deleted");
    TestUtils.checkPodDeleted(podName, domainNS);

    replicaCnt = TestUtils.getClusterReplicas(domainUid, clusterName, domainNS);
    if (replicaCnt != replicas) {
      throw new RuntimeException(
          "FAILURE: Cluster replica doesn't match with scaled down size "
              + replicaCnt
              + "/"
              + replicas);
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
    logTestBegin("testOperatorLifecycle");
    operator.destroy();
    operator.create();
    operator.verifyExternalRESTService();
    operator.verifyDomainExists(domainUid);
    domain.verifyDomainCreated();
    logger.info("SUCCESS");
  }
}
