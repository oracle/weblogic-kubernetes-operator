// Copyright 2018, Oracle Corporation and/or its affiliates.  All rights reserved.
// Licensed under the Universal Permissive License v 1.0 as shown at
// http://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
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
  private static final String opForDelYamlFile1 = "operator_del1.yaml";
  private static final String opForDelYamlFile2 = "operator_del2.yaml";
  private static final String opForBackwardCompatibility = "operator_bc.yaml";

  // property file used to customize domain properties for domain inputs yaml
  private static String domain1YamlFile = "domain1.yaml";
  private static String domain2YamlFile = "domain2.yaml";
  private static String domain3YamlFile = "domain3.yaml";
  private static String domain4YamlFile = "domain4.yaml";
  private static String domain5YamlFile = "domain5.yaml";
  private static String domain6YamlFile = "domain6.yaml";
  private static String domain7YamlFile = "domain7.yaml";
  private static String domain8YamlFile = "domain8.yaml";
  private static final String domain1ForDelValueYamlFile = "domain_del_1.yaml";
  private static final String domain2ForDelValueYamlFile = "domain_del_2.yaml";
  private static final String domain3ForDelValueYamlFile = "domain_del_3.yaml";
  private static String domain9YamlFile = "domain9.yaml";
  private static String domain10YamlFile = "domain10.yaml";
  private static String domain11YamlFile = "domain11.yaml";
  private static String domain12YamlFile = "domain12.yaml";

  // property file used to configure constants for integration tests
  private static String appPropsFile = "OperatorIT.properties";

  private static Operator operator1, operator2;

  private static Operator operatorForDel1;
  private static Operator operatorForDel2;

  private static Operator operatorForBackwardCompatibility;

  private static boolean QUICKTEST;
  private static boolean SMOKETEST;
  private static boolean JENKINS;

  // Set QUICKTEST env var to true to run a small subset of tests.
  // Set SMOKETEST env var to true to run an even smaller subset
  // of tests, plus leave domain1 up and running when the test completes.
  static {
    QUICKTEST =
        System.getenv("QUICKTEST") != null && System.getenv("QUICKTEST").equalsIgnoreCase("true");
    SMOKETEST =
        System.getenv("SMOKETEST") != null && System.getenv("SMOKETEST").equalsIgnoreCase("true");
    if (SMOKETEST) QUICKTEST = true;
    if (System.getenv("JENKINS") != null) {
      JENKINS = new Boolean(System.getenv("JENKINS")).booleanValue();
    }
  }

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
   * Releases k8s cluster lease, archives result, pv directories
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

    if (JENKINS) {
      cleanup();
    }

    if (getLeaseId() != "") {
      logger.info("Release the k8s cluster lease");
      TestUtils.releaseLease(getProjectRoot(), getLeaseId());
    }

    logger.info("SUCCESS");
  }

  /**
   * Create operator and verify its deployed successfully. Create domain and verify domain is
   * started. Verify admin external service by accessing admin REST endpoint with nodeport in URL
   * Verify admin t3 channel port by exec into the admin pod and deploying webapp using the channel
   * port for WLST Verify web app load balancing by accessing the webapp using loadBalancerWebPort
   * Verify domain life cycle(destroy and create) should not any impact on Operator Cluster scale
   * up/down using Operator REST endpoint, webapp load balancing should adjust accordingly. Operator
   * life cycle(destroy and create) should not impact the running domain Verify liveness probe by
   * killing managed server 1 process 3 times to kick pod auto-restart shutdown the domain by
   * changing domain serverStartPolicy to NEVER
   *
   * @throws Exception
   */
  @Test
  public void test1CreateFirstOperatorAndDomain() throws Exception {

    logTestBegin("test1CreateFirstOperatorAndDomain");
    logger.info("Creating Operator & waiting for the script to complete execution");
    // create operator1
    operator1 = TestUtils.createOperator(op1YamlFile);
    Domain domain1 = null;
    boolean testCompletedSuccessfully = false;
    try {
      domain1 = testDomainCreation(domain1YamlFile);
      domain1.verifyDomainCreated();
      testBasicUseCases(domain1);
      testAdvancedUseCasesForADomain(operator1, domain1);

      if (!SMOKETEST) domain1.testWlsLivenessProbe();
      testCompletedSuccessfully = true;
    } finally {
      if (domain1 != null && !SMOKETEST && (JENKINS || testCompletedSuccessfully))
        domain1.shutdownUsingServerStartPolicy();
    }

    logger.info("SUCCESS - test1CreateFirstOperatorAndDomain");
  }
  /**
   * Create operator if its not running and create domain and verify domain in started successfully.
   * Verify admin external service by accessing admin REST endpoint with nodeport in URL Verify
   * admin t3 channel port by exec into the admin pod and deploying webapp using the channel port
   * for WLST Verify web app load balancing by accessing the webapp using loadBalancerWebPort
   * shutdown by deleting domain CRD using yaml
   *
   * @throws Exception
   */
  @Test
  public void test2CreateAnotherDomainInDefaultNS() throws Exception {
    Assume.assumeFalse(QUICKTEST);

    logTestBegin("test2CreateAnotherDomainInDefaultNS");
    logger.info("Creating Domain domain2 & verifing the domain creation");
    Domain domain2 = null;
    boolean testCompletedSuccessfully = false;
    if (operator1 == null) {
      operator1 = TestUtils.createOperator(op1YamlFile);
    }
    try {
      // create domain2
      domain2 = testDomainCreation(domain2YamlFile);
      domain2.verifyDomainCreated();
      testBasicUseCases(domain2);
      testCompletedSuccessfully = true;
    } finally {
      if (domain2 != null && (JENKINS || testCompletedSuccessfully)) {
        logger.info("Destroy domain2");
        domain2.destroy();
      }
    }
    logger.info("SUCCESS - test2CreateAnotherDomainInDefaultNS");
  }
  /**
   * Create operator if its not running. Create domain with dynamic cluster using WDT and verify the
   * domain is started successfully. Verify cluster scaling by doing scale up for domain3 using WLDF
   * scaling shutdown by deleting domain CRD using yaml
   *
   * @throws Exception
   */
  @Test
  public void test3CreateDomainInTest1NS() throws Exception {
    Assume.assumeFalse(QUICKTEST);
    logTestBegin("test3CreateDomainInTest1NS");
    logger.info("Creating Domain domain3 & verifing the domain creation");

    if (operator1 == null) {
      operator1 = TestUtils.createOperator(op1YamlFile);
    }
    Domain domain3 = null;
    boolean testCompletedSuccessfully = false;
    try {
      // create domain3
      domain3 = testDomainCreation(domain3YamlFile);
      domain3.verifyDomainCreated();
      testBasicUseCases(domain3);
      testWLDFScaling(operator1, domain3);
      testCompletedSuccessfully = true;
    } finally {
      if (domain3 != null && (JENKINS || testCompletedSuccessfully)) {
        domain3.destroy();
      }
    }

    logger.info("SUCCESS - test3CreateDomainInTest1NS");
  }
  /**
   * Create operator managing test2 namespace
   *
   * @throws Exception
   */
  @Test
  public void test4CreateAnotherOperatorManagingTest2NS() throws Exception {
    Assume.assumeFalse(QUICKTEST);

    logTestBegin("test4CreateAnotherOperatorManagingTest2NS");
    logger.info("Creating Operator & waiting for the script to complete execution");
    // create operator2
    operator2 = TestUtils.createOperator(op2YamlFile);
    logger.info("SUCCESS - test4CreateAnotherOperatorManagingTest2NS");
  }

  /**
   * Create two operators if they are not running. Create domain domain4 with dynamic cluster in
   * default namespace, managed by operator1 Create domain domain5 with Configured cluster using WDT
   * in test2 namespace, managed by operator2 Verify scaling for domain5 cluster from 2 to 3 servers
   * and back to 2, plus verify no impact on domain4 Cycle domain4 down and back up, plus verify no
   * impact on domain5 shutdown by deleting both domain4 and domain5 CRD's
   *
   * @throws Exception
   */
  @Test
  public void test5CreateConfiguredDomainInTest2NS() throws Exception {
    Assume.assumeFalse(QUICKTEST);

    logTestBegin("test5CreateConfiguredDomainInTest2NS");
    logger.info("Creating Domain domain4 & verifing the domain creation");

    logger.info("Checking if operator1 and domain1 are running, if not creating");
    if (operator1 == null) {
      operator1 = TestUtils.createOperator(op1YamlFile);
    }

    Domain domain4 = null, domain5 = null;
    boolean testCompletedSuccessfully = false;
    try {
      domain4 = testDomainCreation(domain4YamlFile);
      domain4.verifyDomainCreated();
      testBasicUseCases(domain4);
      logger.info("Checking if operator2 is running, if not creating");
      if (operator2 == null) {
        operator2 = TestUtils.createOperator(op2YamlFile);
      }
      // create domain5 with configured cluster
      domain5 = testDomainCreation(domain5YamlFile);
      domain5.verifyDomainCreated();
      testBasicUseCases(domain5);
      logger.info("Verify the only remaining running domain domain4 is unaffected");
      domain4.verifyDomainCreated();

      testClusterScaling(operator2, domain5);

      logger.info("Verify the only remaining running domain domain4 is unaffected");
      domain4.verifyDomainCreated();

      logger.info("Destroy and create domain4 and verify no impact on domain5");
      domain4.destroy();
      domain4.create();

      logger.info("Verify no impact on domain5");
      domain5.verifyDomainCreated();
      testCompletedSuccessfully = true;

    } finally {
      if (domain4 != null && (JENKINS || testCompletedSuccessfully)) {
        domain4.destroy();
      }
      if (domain5 != null && (JENKINS || testCompletedSuccessfully)) {
        domain5.destroy();
      }
    }
    logger.info("SUCCESS - test5CreateConfiguredDomainInTest2NS");
  }
  /**
   * Create operator if its not running and create domain with serverStartPolicy="ADMIN_ONLY" Verify
   * only admin server is created. on Jenkins, this domain will also test NFS instead of HOSTPATH PV
   * storage shutdown by deleting domain CRD
   *
   * @throws Exception
   */
  @Test
  public void test6CreateDomainWithStartPolicyAdminOnly() throws Exception {
    Assume.assumeFalse(QUICKTEST);

    logTestBegin("test6CreateDomainWithStartPolicyAdminOnly");
    logger.info("Checking if operator1 is running, if not creating");
    if (operator1 == null) {
      operator1 = TestUtils.createOperator(op1YamlFile);
    }
    logger.info("Creating Domain domain6 & verifing the domain creation");
    // create domain6
    Domain domain6 = null;
    boolean testCompletedSuccessfully = false;
    try {
      domain6 = testDomainCreation(domain6YamlFile);
      domain6.verifyDomainCreated();
      testCompletedSuccessfully = true;
    } finally {
      if (domain6 != null && (JENKINS || testCompletedSuccessfully)) domain6.destroy();
    }

    logger.info("SUCCESS - test6CreateDomainWithStartPolicyAdminOnly");
  }
  /**
   * Create operator and create domain with pvReclaimPolicy="Recycle" Verify that the PV is deleted
   * once the domain and PVC are deleted
   *
   * @throws Exception
   */
  @Test
  public void test7CreateDomainPVReclaimPolicyRecycle() throws Exception {
    Assume.assumeFalse(QUICKTEST);

    logTestBegin("test7CreateDomainPVReclaimPolicyRecycle");
    logger.info("Checking if operator1 is running, if not creating");
    if (operator1 == null) {
      operator1 = TestUtils.createOperator(op1YamlFile);
    }
    logger.info("Creating Domain domain7 & verifing the domain creation");
    // create domain7
    Domain domain7 = null;

    try {
      domain7 = testDomainCreation(domain7YamlFile);
      domain7.verifyDomainCreated();
    } finally {
      if (domain7 != null) domain7.shutdown();
    }
    domain7.deletePVCAndCheckPVReleased();
    logger.info("SUCCESS - test7CreateDomainPVReclaimPolicyRecycle");
  }
  /**
   * Create operator and create domain and verify that create domain fails when its pv is already
   * populated by a shutdown domain
   *
   * @throws Exception
   */
  @Test
  public void test8CreateDomainOnExistingDir() throws Exception {
    Assume.assumeFalse(QUICKTEST);

    logTestBegin("test8CreateDomainOnExistingDir");
    if (operator1 == null) {
      operator1 = TestUtils.createOperator(op1YamlFile);
    }

    Domain domain8 = null;

    try {
      domain8 = testDomainCreation(domain8YamlFile);
      domain8.verifyDomainCreated();
    } finally {
      if (domain8 != null) {
        // create domain on existing dir
        domain8.destroy();
      }
    }

    domain8.createDomainOnExistingDirectory();
    logger.info("SUCCESS - test8CreateDomainOnExistingDir");
  }

  /**
   * Create operator and create domain with APACHE load balancer and verify domain is started
   * successfully and access admin console via LB port. shutdown domain.
   *
   * @throws Exception
   */
  // @Test
  public void testACreateDomainApacheLB() throws Exception {
    Assume.assumeFalse(QUICKTEST);

    logTestBegin("testACreateDomainApacheLB");
    logger.info("Creating Domain domain9 & verifing the domain creation");
    if (operator1 == null) {
      operator1 = TestUtils.createOperator(op1YamlFile);
    }
    boolean testCompletedSuccessfully = false;
    // create domain9
    Domain domain9 = null;
    try {
      domain9 = testDomainCreation(domain9YamlFile);
      domain9.verifyDomainCreated();
      domain9.verifyAdminConsoleViaLB();
      testCompletedSuccessfully = true;
    } finally {
      if (domain9 != null && (JENKINS || testCompletedSuccessfully)) domain9.destroy();
    }
    logger.info("SUCCESS - testACreateDomainApacheLB");
  }
  /**
   * Create operator and create domain with mostly default values from sample domain inputs, mainly
   * exposeAdminT3Channel and exposeAdminNodePort which are false by default and verify domain
   * startup and cluster scaling using operator rest endpoint works.
   *
   * @throws Exception
   */
  @Test
  public void testBCreateDomainWithDefaultValuesInSampleInputs() throws Exception {
    Assume.assumeFalse(QUICKTEST);

    logTestBegin("testBCreateDomainWithDefaultValuesInSampleInputs");
    logger.info("Creating Domain domain10 & verifing the domain creation");
    if (operator1 == null) {
      operator1 = TestUtils.createOperator(op1YamlFile);
    }

    // create domain10
    Domain domain10 = null;
    boolean testCompletedSuccessfully = false;
    try {
      domain10 = testDomainCreation(domain10YamlFile);
      domain10.verifyDomainCreated();
      testBasicUseCases(domain10);
      testAdvancedUseCasesForADomain(operator1, domain10);
      testCompletedSuccessfully = true;
    } finally {
      if (domain10 != null && (JENKINS || testCompletedSuccessfully)) domain10.destroy();
    }

    logger.info("SUCCESS - testBCreateDomainWithDefaultValuesInSampleInputs");
  }

  @Test
  public void testDeleteOneDomain() throws Exception {
    Assume.assumeFalse(QUICKTEST);
    logTestBegin("Deleting one domain.");

    if (operatorForDel1 == null) {
      logger.info("About to create operator");
      operatorForDel1 = TestUtils.createOperator(opForDelYamlFile1);
    }
    Domain domain = null;
    try {
      domain = testDomainCreation(domain1ForDelValueYamlFile);
      domain.verifyDomainCreated();
      TestUtils.verifyBeforeDeletion(domain);
    } catch (Exception ex) {
      if (domain != null && JENKINS) {
        try {
          domain.destroy();
        } catch (Exception ignore) {
        }
      }
      throw ex;
    }

    logger.info("About to delete domain: " + domain.getDomainUid());
    TestUtils.deleteWeblogicDomainResources(domain.getDomainUid());

    TestUtils.verifyAfterDeletion(domain);
    logger.info("SUCCESS - testDeleteOneDomain");
  }

  @Test
  public void testDeleteTwoDomains() throws Exception {
    Assume.assumeFalse(QUICKTEST);
    logTestBegin("Deleting two domains.");

    if (operatorForDel2 == null) {
      logger.info("About to create operator");
      operatorForDel2 = TestUtils.createOperator(opForDelYamlFile2);
    }
    Domain domainDel1 = null, domainDel2 = null;

    try {
      domainDel1 = testDomainCreation(domain2ForDelValueYamlFile);
      domainDel1.verifyDomainCreated();
      domainDel2 = testDomainCreation(domain3ForDelValueYamlFile);
      domainDel2.verifyDomainCreated();

      TestUtils.verifyBeforeDeletion(domainDel1);
      TestUtils.verifyBeforeDeletion(domainDel2);
    } catch (Exception ex) {

      if (domainDel1 != null && JENKINS) {
        try {
          domainDel1.destroy();
        } catch (Exception ignore) {
        }
      }
      if (domainDel2 != null && JENKINS) {
        try {
          domainDel2.destroy();
        } catch (Exception ignore) {
        }
      }
      throw ex;
    }
    final String domainUidsToBeDeleted =
        domainDel1.getDomainUid() + "," + domainDel2.getDomainUid();
    logger.info("About to delete domains: " + domainUidsToBeDeleted);
    TestUtils.deleteWeblogicDomainResources(domainUidsToBeDeleted);

    TestUtils.verifyAfterDeletion(domainDel1);
    TestUtils.verifyAfterDeletion(domainDel2);
    logger.info("SUCCESS - testDeleteTwoDomains");
  }
  /**
   * Create Operator and create domain with listen address not set for admin server, t3 channel and
   * incorrect file for admin server log location Verify automatic situational config override works
   * by bringing up the domain and by verifying domain is started successfully and web application
   * can be deployed and accessed.
   *
   * @throws Exception
   */
  @Test
  public void testAutoSitConfigOverrides() throws Exception {
    Assume.assumeFalse(QUICKTEST);
    logTestBegin("testAutoSitConfigOverrides");

    if (operator1 == null) {
      operator1 = TestUtils.createOperator(op1YamlFile);
    }
    Domain domain11 = null;
    boolean testCompletedSuccessfully = false;
    String createDomainScriptDir =
        BaseTest.getProjectRoot() + "/integration-tests/src/test/resources/domain-home-on-pv";
    try {

      // cp py
      Files.copy(
          new File(createDomainScriptDir + "/create-domain.py").toPath(),
          new File(createDomainScriptDir + "/create-domain.py.bak").toPath(),
          StandardCopyOption.REPLACE_EXISTING);
      Files.copy(
          new File(createDomainScriptDir + "/create-domain-auto-sit-config.py").toPath(),
          new File(createDomainScriptDir + "/create-domain.py").toPath(),
          StandardCopyOption.REPLACE_EXISTING);

      domain11 = testDomainCreation(domain11YamlFile);
      domain11.verifyDomainCreated();
      testBasicUseCases(domain11);
      // testAdvancedUseCasesForADomain(operator1, domain11);
      testCompletedSuccessfully = true;

    } finally {
      Files.copy(
          new File(createDomainScriptDir + "/create-domain.py.bak").toPath(),
          new File(createDomainScriptDir + "/create-domain.py").toPath(),
          StandardCopyOption.REPLACE_EXISTING);
      if (domain11 != null && (JENKINS || testCompletedSuccessfully)) {
        domain11.destroy();
      }
    }
    logger.info("SUCCESS - testAutoSitConfigOverrides");
  }

  /**
   * Create operator and enable external rest endpoint using the externalOperatorCert and
   * externalOperatorKey defined in the helm chart values instead of the tls secret. This test is
   * for backward compatibility
   *
   * @throws Exception
   */
  @Test
  public void testRESTIdentityBackwardCompatibility() throws Exception {
    Assume.assumeFalse(QUICKTEST);

    logTestBegin("testRESTIdentityBackwardCompatibility");
    logger.info("Checking if operatorForBackwardCompatibility is running, if not creating");
    if (operatorForBackwardCompatibility == null) {
      operatorForBackwardCompatibility = TestUtils.createOperator(opForBackwardCompatibility, true);
    }
    logger.info("Operator using legacy REST identity created successfully");
    operatorForBackwardCompatibility.destroy();
    logger.info("SUCCESS - testRESTIdentityBackwardCompatibility");
  }

  /**
   * Create Operator and create domain with some junk value for t3 channel public address and using
   * custom situational config override replace with valid public address using secret Verify the
   * domain is started successfully and web application can be deployed and accessed.
   *
   * @throws Exception
   */
  @Test
  public void testCustomSitConfigOverrides() throws Exception {
    Assume.assumeFalse(QUICKTEST);
    logTestBegin("testCustomSitConfigOverrides");

    if (operator1 == null) {
      operator1 = TestUtils.createOperator(op1YamlFile);
    }
    Domain domain12 = null;
    boolean testCompletedSuccessfully = false;
    String createDomainScriptDir =
        BaseTest.getProjectRoot() + "/integration-tests/src/test/resources/domain-home-on-pv";
    try {

      // cp py
      Files.copy(
          new File(createDomainScriptDir + "/create-domain.py").toPath(),
          new File(createDomainScriptDir + "/create-domain.py.bak").toPath(),
          StandardCopyOption.REPLACE_EXISTING);
      Files.copy(
          new File(createDomainScriptDir + "/create-domain-custom-sit-config.py").toPath(),
          new File(createDomainScriptDir + "/create-domain.py").toPath(),
          StandardCopyOption.REPLACE_EXISTING);

      domain12 = testDomainCreation(domain12YamlFile);
      domain12.verifyDomainCreated();
      testBasicUseCases(domain12);
      // testAdvancedUseCasesForADomain(operator1, domain11);
      testCompletedSuccessfully = true;

    } finally {
      Files.copy(
          new File(createDomainScriptDir + "/create-domain.py.bak").toPath(),
          new File(createDomainScriptDir + "/create-domain.py").toPath(),
          StandardCopyOption.REPLACE_EXISTING);
      if (domain12 != null && (JENKINS || testCompletedSuccessfully)) {
        domain12.destroy();
      }
    }
    logger.info("SUCCESS - testCustomSitConfigOverrides");
  }

  private Domain testAdvancedUseCasesForADomain(Operator operator, Domain domain) throws Exception {
    if (!SMOKETEST) {
      testClusterScaling(operator, domain);
      testDomainLifecyle(operator, domain);
      testOperatorLifecycle(operator, domain);
    }
    return domain;
  }

  private Domain testDomainCreation(String domainYamlFile) throws Exception {
    return TestUtils.createDomain(domainYamlFile);
  }

  private void testBasicUseCases(Domain domain) throws Exception {
    testAdminT3Channel(domain);
    testAdminServerExternalService(domain);
  }
}
