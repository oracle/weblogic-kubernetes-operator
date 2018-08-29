// Copyright 2018, Oracle Corporation and/or its affiliates.  All rights reserved.
// Licensed under the Universal Permissive License v 1.0 as shown at
// http://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator;

import oracle.kubernetes.operator.utils.Domain;
import oracle.kubernetes.operator.utils.ExecCommand;
import oracle.kubernetes.operator.utils.ExecResult;
import oracle.kubernetes.operator.utils.Operator;
import oracle.kubernetes.operator.utils.TestUtils;
import org.junit.AfterClass;
import org.junit.Assume;
import org.junit.BeforeClass;
import org.junit.FixMethodOrder;
import org.junit.Test;
import org.junit.runners.MethodSorters;

/**
 * Simple JUnit test file used for testing Operator.
 *
 * <p>This test is used for creating Operator(s) and multiple domains which are managed by the
 * Operator(s).
 */
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
public class ITOperator extends BaseTest {

  // property file used to customize operator properties for operator inputs yaml
  private static String op1PropsFile = "ITFirstOperator.properties";
  private static String op2PropsFile = "ITSecondOperator.properties";

  // property file used to customize domain properties for domain inputs yaml
  private static String domain1PropsFile = "ITFirstDomain.properties";
  private static String domain2PropsFile = "ITSecondDomain.properties";
  private static String domain3PropsFile = "ITThirdDomain.properties";
  private static String domain4PropsFile = "ITFourthDomain.properties";
  private static String domain5PropsFile = "ITFifthDomain.properties";
  private static String domain6PropsFile = "ITSixthDomain.properties";
  private static String domain7PropsFile = "ITSeventhDomain.properties";

  // property file used to configure constants for integration tests
  private static String appPropsFile = "OperatorIT.properties";

  private static Operator operator1, operator2;
  private static Domain domain1;

  /**
   * This method gets called only once before any of the test methods are executed. It does the
   * initialization of the integration test properties defined in OperatorIT.properties and setting
   * the resultRoot, pvRoot and projectRoot attributes.
   *
   * @throws Exception
   */
  @BeforeClass
  public static void staticPrepare() throws Exception {
    // initialize test properties and create the directories
    initialize(appPropsFile);
  }

  /**
   * Releases k8s cluster lease
   *
   * @throws Exception
   */
  @AfterClass
  public static void staticUnPrepare() throws Exception {
    logger.info("+++++++++++++++++++++++++++++++++---------------------------------+");
    logger.info("BEGIN");
    logger.info("Run once, release cluster lease");

    if (getLeaseId() != "") {
      logger.info("Release the k8s cluster lease");
      TestUtils.releaseLease(getProjectRoot(), getLeaseId());
    }

    StringBuffer cmd =
        new StringBuffer("export RESULT_ROOT=$RESULT_ROOT && export PV_ROOT=$PV_ROOT && ");
    cmd.append(BaseTest.getProjectRoot())
        .append("/integration-tests/src/test/resources/statedump.sh");

    logger.info("Running " + cmd);
    ExecResult result = ExecCommand.exec(cmd.toString());
    if (result.exitValue() == 0) logger.info("Executed statedump.sh " + result.stdout());
    else
      logger.info("Execution of statedump.sh failed, " + result.stderr() + "\n" + result.stdout());

    logger.info("SUCCESS");
  }

  @Test
  public void test1CreateOperatorManagingDefaultAndTest1NS() throws Exception {
    logTestBegin("test1CreateOperatorManagingDefaultAndTest1NS");
    logger.info("Creating Operator & waiting for the script to complete execution");
    // create operator1
    operator1 = TestUtils.createOperator(op1PropsFile, true);
    logger.info("SUCCESS");
  }

  @Test
  public void test2CreateDomainInDefaultNS() throws Exception {
    logTestBegin("test2CreateDomainInDefaultNS");
    logger.info("Creating Domain domain1 & verifing the domain creation");
    // create domain1
    domain1 = testDomainCreation(domain1PropsFile);
    testDomainLifecyle(operator1, domain1);
    testClusterScaling(operator1, domain1);
    testOperatorLifecycle(operator1, domain1);

    logger.info("SUCCESS");
  }

  @Test
  public void test3CreateAnotherDomainInDefaultNS() throws Exception {
    Assume.assumeFalse(
        System.getenv("QUICKTEST") != null && System.getenv("QUICKTEST").equalsIgnoreCase("true"));

    logTestBegin("test3CreateAnotherDomainInDefaultNS");
    logger.info("Creating Domain domain2 & verifing the domain creation");
    // create domain2
    Domain domain2 = testDomainCreation(domain2PropsFile);

    logger.info("Destroy domain2");
    domain2.destroy();
    logger.info("SUCCESS");
  }

  @Test
  public void test4CreateDomainInTest1NS() throws Exception {
    Assume.assumeFalse(
        System.getenv("QUICKTEST") != null && System.getenv("QUICKTEST").equalsIgnoreCase("true"));

    logTestBegin("test4CreateDomainInTest1NS");
    logger.info("Creating Domain domain3 & verifing the domain creation");
    // create domain3
    testDomainCreation(domain3PropsFile);
    logger.info("SUCCESS");
  }

  @Test
  public void test5CreateAnotherOperatorManagingTest2NS() throws Exception {
    Assume.assumeFalse(
        System.getenv("QUICKTEST") != null && System.getenv("QUICKTEST").equalsIgnoreCase("true"));

    logTestBegin("test5CreateAnotherOperatorManagingTest2NS");
    logger.info("Creating Operator & waiting for the script to complete execution");
    // create operator2
    operator2 = TestUtils.createOperator(op2PropsFile, false);
    logger.info("SUCCESS");
  }

  @Test
  public void test6CreateConfiguredDomainInTest2NS() throws Exception {
    Assume.assumeFalse(
        System.getenv("QUICKTEST") != null && System.getenv("QUICKTEST").equalsIgnoreCase("true"));

    logTestBegin("test6CreateDomainInTest2NS");
    logger.info("Creating Domain domain4 & verifing the domain creation");
    // create domain4
    Domain domain4 = testDomainCreation(domain4PropsFile);

    logger.info("Verify the only remaining running domain domain1 is unaffected");
    domain1.verifyDomainCreated();

    testClusterScaling(operator2, domain4);

    logger.info("Verify the only remaining running domain domain1 is unaffected");
    domain1.verifyDomainCreated();

    logger.info("Destroy and create domain1 and verify no impact on domain4");
    domain1.destroy();
    domain1.create();

    logger.info("Verify no impact on domain4");
    domain4.verifyDomainCreated();
    logger.info("SUCCESS");
  }

  @Test
  public void test7CreateDomainWithStartupControlAdmin() throws Exception {
    Assume.assumeFalse(
        System.getenv("QUICKTEST") != null && System.getenv("QUICKTEST").equalsIgnoreCase("true"));

    logTestBegin("test7CreateDomainWithStartupControlAdmin");
    logger.info("Creating Domain domain5 & verifing the domain creation");
    // create domain5
    TestUtils.createDomain(domain5PropsFile);
    logger.info("SUCCESS");
  }

  @Test
  public void test8CreateDomainPVReclaimPolicyRecycle() throws Exception {
    Assume.assumeFalse(
        System.getenv("QUICKTEST") != null && System.getenv("QUICKTEST").equalsIgnoreCase("true"));

    logTestBegin("test8CreateDomainPVReclaimPolicyRecycle");
    logger.info("Creating Domain domain6 & verifing the domain creation");
    // create domain6
    Domain domain6 = TestUtils.createDomain(domain6PropsFile);
    domain6.shutdown();
    domain6.deletePVCAndCheckPVReleased();
    logger.info("SUCCESS");
  }

  @Test
  public void test9WlsLivenessProbe() throws Exception {
    Assume.assumeFalse(
        System.getenv("QUICKTEST") != null && System.getenv("QUICKTEST").equalsIgnoreCase("true"));

    logTestBegin("test9WlsLivenessProbe");
    // test managed server1 pod auto restart
    String domain = domain1.getDomainUid();
    String namespace = domain1.getDomainProps().getProperty("namespace");
    String serverName = domain1.getDomainProps().getProperty("managedServerNameBase") + "1";
    TestUtils.testWlsLivenessProbe(domain, serverName, namespace);
    logger.info("SUCCESS");
  }

  @Test
  public void testACreateDomainOnExistingDir() throws Exception {
    Assume.assumeFalse(
        System.getenv("QUICKTEST") != null && System.getenv("QUICKTEST").equalsIgnoreCase("true"));

    logTestBegin("test10CreateDomainOnExistingDir");
    logger.info("domain1 " + domain1);
    // create domain on existing dir
    domain1.destroy();
    domain1.createDomainOnExistingDirectory();
    logger.info("SUCCESS");
  }

  @Test
  public void testBCreateDomainApacheLB() throws Exception {
    Assume.assumeFalse(
        System.getenv("QUICKTEST") != null && System.getenv("QUICKTEST").equalsIgnoreCase("true"));
    logTestBegin("test11CreateDomainApacheLB");
    logger.info("Creating Domain domain7 & verifing the domain creation");
    // create domain7
    Domain domain7 = TestUtils.createDomain(domain7PropsFile);
    domain7.verifyAdminConsoleViaLB();
    logger.info("SUCCESS");
  }

  private Domain testDomainCreation(String domainPropsFile) throws Exception {
    Domain domain = TestUtils.createDomain(domainPropsFile);
    testAdminT3Channel(domain);
    testAdminServerExternalService(domain);
    return domain;
  }
}
