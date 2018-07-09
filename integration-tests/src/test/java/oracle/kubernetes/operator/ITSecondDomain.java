// Copyright 2018, Oracle Corporation and/or its affiliates.  All rights reserved.
// Licensed under the Universal Permissive License v 1.0 as shown at
// http://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator;

import java.util.Properties;
import oracle.kubernetes.operator.utils.Domain;
import oracle.kubernetes.operator.utils.TestUtils;
import org.junit.AfterClass;
import org.junit.Assume;
import org.junit.BeforeClass;
import org.junit.Test;

/**
 * Simple JUnit test file used for testing Operator.
 *
 * <p>This test is used for creating a single domain which the Operator manages and verifies both.
 */
public class ITSecondDomain extends BaseTest {
  public static final String TESTWEBAPP = "testwebapp";

  // property file used to customize domain properties for domain inputs yaml
  private static String domainPropsFile = "ITSecondDomain.properties";

  // property file used to configure constants for integration tests
  private static String appPropsFile = "OperatorIT.properties";

  private static Domain domain;

  // properties of operator/domain to use in the test methods
  private static Properties domainProps;

  private static String domainUid, domainNS;
  /**
   * This method gets called only once before any of the test methods are executed. It creates the
   * domain.
   *
   * @throws Exception
   */
  @BeforeClass
  public static void staticPrepare() throws Exception {
    Assume.assumeTrue(
        System.getenv("QUICKTEST") == null || System.getenv("QUICKTEST").equalsIgnoreCase("false"));
    logger.info("+++++++++++++++++++++++++++++++++---------------------------------+");
    logger.info("BEGIN");

    // renew lease at the begining for every test method, leaseId is set only for Wercker
    TestUtils.renewK8sClusterLease(getProjectRoot(), getLeaseId());

    logger.info("Run once, Creating Domain & " + "waiting for the script to complete execution");

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
    if (domain != null) domain.destroy();

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
}
