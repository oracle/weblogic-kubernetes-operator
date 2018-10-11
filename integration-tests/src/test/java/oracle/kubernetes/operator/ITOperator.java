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
  private static String op1YamlFile = "operator1.yaml";
  private static String op2YamlFile = "operator2.yaml";

  // property file used to customize domain properties for domain inputs yaml
  private static String domain1YamlFile = "domain1.yaml";
  private static String domain2YamlFile = "domain2.yaml";
  private static String domain3YamlFile = "domain3.yaml";
  private static String domain4YamlFile = "domain4.yaml";
  private static String domain5YamlFile = "domain5.yaml";
  private static String domain6YamlFile = "domain6.yaml";
  private static String domain7YamlFile = "domain7.yaml";

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

    StringBuffer cmd =
        new StringBuffer("export RESULT_ROOT=$RESULT_ROOT && export PV_ROOT=$PV_ROOT && ");
    cmd.append(BaseTest.getProjectRoot())
        .append("/integration-tests/src/test/resources/statedump.sh");
    logger.info("Running " + cmd);

    ExecResult result = ExecCommand.exec(cmd.toString());
    if (result.exitValue() == 0) logger.info("Executed statedump.sh " + result.stdout());
    else
      logger.info("Execution of statedump.sh failed, " + result.stderr() + "\n" + result.stdout());

    if (getLeaseId() != "") {
      logger.info("Release the k8s cluster lease");
      TestUtils.releaseLease(getProjectRoot(), getLeaseId());
    }

    logger.info("SUCCESS");
  }

  @Test
  public void test1CreateFirstOperatorAndDomain() throws Exception {

    logTestBegin("test1CreateFirstOperatorAndDomain");
    testCreateOperatorManagingDefaultAndTest1NS();
    testCreateDomainInDefaultNS();
    logger.info("SUCCESS - test1CreateFirstOperatorAndDomain");
  }

  @Test
  public void test2CreateAnotherDomainInDefaultNS() throws Exception {
    Assume.assumeFalse(
        System.getenv("QUICKTEST") != null && System.getenv("QUICKTEST").equalsIgnoreCase("true"));

    logTestBegin("test2CreateAnotherDomainInDefaultNS");
    logger.info("Creating Domain domain2 & verifing the domain creation");
    if (operator1 == null) {
      operator1 = TestUtils.createOperator(op1YamlFile);
    }
    // create domain2
    Domain domain2 = testDomainCreation(domain2YamlFile);

    logger.info("Destroy domain2");
    domain2.destroy();
    logger.info("SUCCESS - test2CreateAnotherDomainInDefaultNS");
  }

  @Test
  public void test3CreateDomainInTest1NS() throws Exception {
    Assume.assumeFalse(
        System.getenv("QUICKTEST") != null && System.getenv("QUICKTEST").equalsIgnoreCase("true"));

    logTestBegin("test3CreateDomainInTest1NS");
    logger.info("Creating Domain domain3 & verifing the domain creation");
    if (operator1 == null) {
      operator1 = TestUtils.createOperator(op1YamlFile);
    }
    // create domain3
    testDomainCreation(domain3YamlFile);
    logger.info("SUCCESS - test3CreateDomainInTest1NS");
  }

  @Test
  public void test4CreateAnotherOperatorManagingTest2NS() throws Exception {
    Assume.assumeFalse(
        System.getenv("QUICKTEST") != null && System.getenv("QUICKTEST").equalsIgnoreCase("true"));

    logTestBegin("test4CreateAnotherOperatorManagingTest2NS");
    logger.info("Creating Operator & waiting for the script to complete execution");
    // create operator2
    operator2 = TestUtils.createOperator(op2YamlFile);
    logger.info("SUCCESS - test4CreateAnotherOperatorManagingTest2NS");
  }

  @Test
  public void test5CreateConfiguredDomainInTest2NS() throws Exception {
    Assume.assumeFalse(
        System.getenv("QUICKTEST") != null && System.getenv("QUICKTEST").equalsIgnoreCase("true"));

    logTestBegin("test5CreateConfiguredDomainInTest2NS");
    logger.info("Creating Domain domain4 & verifing the domain creation");

    logger.info("Checking if operator1 and domain1 are running, if not creating");
    if (operator1 == null) {
      operator1 = TestUtils.createOperator(op1YamlFile);
    }
    if (domain1 == null) {
      domain1 = TestUtils.createDomain(domain1YamlFile);
    }
    logger.info("Checking if operator2 is running, if not creating");
    if (operator2 == null) {
      operator2 = TestUtils.createOperator(op2YamlFile);
    }
    // create domain4
    Domain domain4 = testDomainCreation(domain4YamlFile);

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
    logger.info("SUCCESS - test5CreateConfiguredDomainInTest2NS");
  }

  @Test
  public void test6CreateDomainWithStartupControlAdmin() throws Exception {
    Assume.assumeFalse(
        System.getenv("QUICKTEST") != null && System.getenv("QUICKTEST").equalsIgnoreCase("true"));

    logTestBegin("test6CreateDomainWithStartupControlAdmin");
    logger.info("Checking if operator1 is running, if not creating");
    if (operator1 == null) {
      operator1 = TestUtils.createOperator(op1YamlFile);
    }
    logger.info("Creating Domain domain5 & verifing the domain creation");
    // create domain5
    TestUtils.createDomain(domain5YamlFile);
    logger.info("SUCCESS - test6CreateDomainWithStartupControlAdmin");
  }

  @Test
  public void test7CreateDomainPVReclaimPolicyRecycle() throws Exception {
    Assume.assumeFalse(
        System.getenv("QUICKTEST") != null && System.getenv("QUICKTEST").equalsIgnoreCase("true"));

    logTestBegin("test7CreateDomainPVReclaimPolicyRecycle");
    logger.info("Checking if operator1 is running, if not creating");
    if (operator1 == null) {
      operator1 = TestUtils.createOperator(op1YamlFile);
    }
    logger.info("Creating Domain domain6 & verifing the domain creation");
    // create domain6
    Domain domain6 = TestUtils.createDomain(domain6YamlFile);
    domain6.shutdown();
    domain6.deletePVCAndCheckPVReleased();
    logger.info("SUCCESS - test7CreateDomainPVReclaimPolicyRecycle");
  }

  @Test
  public void test8WlsLivenessProbe() throws Exception {
    Assume.assumeFalse(
        System.getenv("QUICKTEST") != null && System.getenv("QUICKTEST").equalsIgnoreCase("true"));

    logTestBegin("test8WlsLivenessProbe");
    logger.info("Checking if operator1 is running, if not creating");
    if (operator1 == null) {
      operator1 = TestUtils.createOperator(op1YamlFile);
    }
    if (domain1 == null) {
      domain1 = TestUtils.createDomain(domain1YamlFile);
    }
    // test managed server1 pod auto restart
    String domain = domain1.getDomainUid();
    String namespace = domain1.getDomainMap().get("namespace").toString();
    String serverName = domain1.getDomainMap().get("managedServerNameBase").toString() + "1";
    TestUtils.testWlsLivenessProbe(domain, serverName, namespace);
    logger.info("SUCCESS - test8WlsLivenessProbe");
  }

  @Test
  public void test9CreateDomainOnExistingDir() throws Exception {
    Assume.assumeFalse(
        System.getenv("QUICKTEST") != null && System.getenv("QUICKTEST").equalsIgnoreCase("true"));

    logTestBegin("test9CreateDomainOnExistingDir");
    if (operator1 == null) {
      operator1 = TestUtils.createOperator(op1YamlFile);
    }
    if (domain1 == null) {
      domain1 = TestUtils.createDomain(domain1YamlFile);
    }
    logger.info("domain1 " + domain1);
    // create domain on existing dir
    domain1.destroy();
    domain1.createDomainOnExistingDirectory();
    logger.info("SUCCESS - test9CreateDomainOnExistingDir");
  }

  @Test
  public void testACreateDomainApacheLB() throws Exception {
    Assume.assumeFalse(
        System.getenv("QUICKTEST") != null && System.getenv("QUICKTEST").equalsIgnoreCase("true"));
    logTestBegin("testACreateDomainApacheLB");
    logger.info("Creating Domain domain7 & verifing the domain creation");
    if (operator1 == null) {
      operator1 = TestUtils.createOperator(op1YamlFile);
    }

    // create domain7
    Domain domain7 = TestUtils.createDomain(domain7YamlFile);
    domain7.verifyAdminConsoleViaLB();
    logger.info("SUCCESS - testACreateDomainApacheLB");
  }

  private void testCreateOperatorManagingDefaultAndTest1NS() throws Exception {
    logger.info("Creating Operator & waiting for the script to complete execution");
    // create operator1
    operator1 = TestUtils.createOperator(op1YamlFile);
  }

  private void testCreateDomainInDefaultNS() throws Exception {
    logger.info("Creating Domain domain1 & verifing the domain creation");
    // create domain1
    domain1 = testDomainCreation(domain1YamlFile);
    testDomainLifecyle(operator1, domain1);
    testClusterScaling(operator1, domain1);
    testOperatorLifecycle(operator1, domain1);
  }

  private Domain testDomainCreation(String domainYamlFile) throws Exception {
    Domain domain = TestUtils.createDomain(domainYamlFile);
    testAdminT3Channel(domain);
    testAdminServerExternalService(domain);
    return domain;
  }
}
