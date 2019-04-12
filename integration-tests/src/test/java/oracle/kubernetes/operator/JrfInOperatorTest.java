// Copyright 2019, Oracle Corporation and/or its affiliates.  All rights reserved.
// Licensed under the Universal Permissive License v 1.0 as shown at
// http://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator;

import oracle.kubernetes.operator.utils.DBUtils;
import oracle.kubernetes.operator.utils.JRFDomain;
import oracle.kubernetes.operator.utils.Operator;
import oracle.kubernetes.operator.utils.OracleDB;
import oracle.kubernetes.operator.utils.TestUtils;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.FixMethodOrder;
import org.junit.Test;
import org.junit.runners.MethodSorters;

/**
 * Simple JUnit test file used for testing Operator for JRF domains.
 *
 * <p>This test is used for creating Operator(s) and multiple JRF domains which are managed by the
 * Operator(s).
 */
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
public class JrfInOperatorTest extends BaseTest {

  // property file used to customize operator properties for operator inputs yaml
  private static final String JRF_OPERATOR_FILE_1 = "jrfoperator1.yaml";
  // file used to customize domain properties for domain, PV and LB inputs yaml
  private static final String JRF_DOMAIN_ON_PV_WLST_FILE = "jrfdomainonpvwlst.yaml";
  // property file for oracle db information
  private static final String DB_PROP_FILE = "oracledb.properties";
  private static Operator operator1;

  /**
   * This method gets called only once before any of the test methods are executed. It does the
   * initialization of the integration test properties defined in OperatorIT.properties and setting
   * the resultRoot, pvRoot and projectRoot attributes. It also creates Oracle DB pod which used for
   * RCU.
   *
   * @throws Exception - if an error occurs when load property file or create DB pod
   */
  @BeforeClass
  public static void staticPrepare() throws Exception {
    // initialize test properties and create the directories
    initialize(APP_PROPS_FILE);

    // create DB used for jrf domain
    OracleDB db = DBUtils.createOracleDB(DB_PROP_FILE);

    // populate the jrf/create-domain-script.sh
    // copy the integration-tests/src/test/resources/domain-home-on-pv/jrf to
    // BaseTest.getResultDir()
    TestUtils.exec(
        "cp -rf "
            + BaseTest.getProjectRoot()
            + "/integration-tests/src/test/resources/domain-home-on-pv/jrf "
            + BaseTest.getResultDir());
    // replace the db connection string with true value
    String dbConnectString =
        db.getName()
            + "."
            + db.getNamespace()
            + ".svc.cluster.local:"
            + db.getPort()
            + "/"
            + db.getDBPdb()
            + "."
            + db.getDBDomain();
    TestUtils.replaceStringInFile(
        BaseTest.getResultDir() + "/jrf/create-domain-script.sh",
        "%CONNECTION_STRING%",
        dbConnectString);
  }

  /**
   * This method will run once after all test methods are finished. It Releases k8s cluster lease,
   * archives result, pv directories.
   *
   * @throws Exception - if any error occurs
   */
  @AfterClass
  public static void staticUnPrepare() throws Exception {
    logger.info("+++++++++++++++++++++++++++++++++---------------------------------+");
    logger.info("BEGIN");
    logger.info("Run once, release cluster lease");

    tearDown(new Object() {}.getClass().getEnclosingClass().getSimpleName());

    logger.info("SUCCESS");
  }

  /**
   * Create operator and verify it's deployed successfully. Create jrf domain and verify domain is
   * started. Verify liveness probe by killing managed server 1 process 3 times to kick pod
   * auto-restart. Shutdown the domain by changing domain serverStartPolicy to NEVER.
   *
   * @throws Exception - if any error occurs when create operator and jrf domains
   */
  @Test
  public void testJRFDomainOnPVUsingWLST() throws Exception {
    String testMethodName = new Object() {}.getClass().getEnclosingMethod().getName();
    logTestBegin(testMethodName);
    logger.info("Creating Operator & waiting for the script to complete execution");
    // create operator1
    if (operator1 == null) {
      operator1 = TestUtils.createOperator(JRF_OPERATOR_FILE_1);
    }

    // TODO: reconsider the logic to check the db readiness
    // The jrfdomain can not find the db pod even the db pod shows ready, sleep more time
    logger.info("waiting for the db to be visible to rcu script ...");
    Thread.sleep(30000);

    JRFDomain jrfdomain = null;
    boolean testCompletedSuccessfully = false;

    try {
      jrfdomain = new JRFDomain(JRF_DOMAIN_ON_PV_WLST_FILE);
      jrfdomain.verifyDomainCreated();

      if (!SMOKETEST) {
        jrfdomain.testWlsLivenessProbe();
      }

      testCompletedSuccessfully = true;
    } finally {
      if (jrfdomain != null && !SMOKETEST && (JENKINS || testCompletedSuccessfully)) {
        jrfdomain.shutdownUsingServerStartPolicy();
      }
    }

    logger.info("SUCCESS - " + testMethodName);
  }
}
