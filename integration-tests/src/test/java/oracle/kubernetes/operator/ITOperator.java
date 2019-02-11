// Copyright 2018, Oracle Corporation and/or its affiliates.  All rights reserved.
// Licensed under the Universal Permissive License v 1.0 as shown at
// http://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.Map;
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
  private static String opManagingdefaultAndtest1NSYamlFile =
      "OperatorManagingdefaultAndtest1NS.yaml";
  private static String opManagingtest2NSYamlFile = "OperatorManagingtest2NS.yaml";
  private static final String opForBackwardCompatibility = "OperatorForBackwardCompatibility.yaml";

  // file used to customize domain properties for domain, PV and LB inputs yaml
  private static String domainOnPVUsingWLSTYamlFile = "DomainOnPVUsingWLST.yaml";
  private static String domainOnPVUsingWDTYamlFile = "DomainOnPVUsingWDT.yaml";
  private static String domainWithServerStartPolicyAsAdminOnlyYamlFile =
      "DomainWithServerStartPolicyAsAdminOnly.yaml";
  private static String domainWithStorageReclaimPolicyRecycleYamlFile =
      "DomainWithStorageReclaimPolicyRecycle.yaml";

  private static String domain4YamlFile = "domain4.yaml";
  private static String domain5YamlFile = "domain5.yaml";
  private static final String domain1ForDelValueYamlFile = "domain_del_1.yaml";
  private static final String domain2ForDelValueYamlFile = "domain_del_2.yaml";
  private static final String domain3ForDelValueYamlFile = "domain_del_3.yaml";
  private static String domainWithApacheLB = "DomainWithApacheLB.yaml";
  private static String domain10YamlFile = "domain10.yaml";

  // property file used to configure constants for integration tests
  private static String appPropsFile = "OperatorIT.properties";

  private static Operator operator1, operator2;

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
  public void testDomainOnPVUsingWLST() throws Exception {
    String testMethodName = new Object() {}.getClass().getEnclosingMethod().getName();
    logTestBegin(testMethodName);
    logger.info("Creating Operator & waiting for the script to complete execution");
    // create operator1
    operator1 = TestUtils.createOperator(opManagingdefaultAndtest1NSYamlFile);
    Domain domain = null;
    boolean testCompletedSuccessfully = false;
    try {
      domain = TestUtils.createDomain(domainOnPVUsingWLSTYamlFile);
      domain.verifyDomainCreated();
      testBasicUseCases(domain);
      testAdvancedUseCasesForADomain(operator1, domain);

      if (!SMOKETEST) domain.testWlsLivenessProbe();

      testCompletedSuccessfully = true;
    } finally {
      if (domain != null && !SMOKETEST && (JENKINS || testCompletedSuccessfully))
        domain.shutdownUsingServerStartPolicy();
    }

    logger.info("SUCCESS - " + testMethodName);
  }

  /**
   * Create operator if its not running. Create domain with dynamic cluster using WDT and verify the
   * domain is started successfully. Verify cluster scaling by doing scale up for domain3 using WLDF
   * scaling shutdown by deleting domain CRD using yaml
   *
   * <p>TODO: Create domain using APACHE load balancer and verify domain is started successfully and
   * access admin console via LB port
   *
   * @throws Exception
   */
  @Test
  public void testDomainOnPVUsingWDT() throws Exception {
    Assume.assumeFalse(QUICKTEST);
    String testMethodName = new Object() {}.getClass().getEnclosingMethod().getName();
    logTestBegin(testMethodName);
    logger.info("Creating Domain using DomainOnPVUsingWDT & verifing the domain creation");

    if (operator2 == null) {
      operator2 = TestUtils.createOperator(opManagingtest2NSYamlFile);
    }
    Domain domain = null;
    boolean testCompletedSuccessfully = false;
    try {
      // create domain
      domain = TestUtils.createDomain(domainOnPVUsingWDTYamlFile);
      domain.verifyDomainCreated();
      testBasicUseCases(domain);
      testWLDFScaling(operator2, domain);
      // TODO: Test Apache LB
      // domain.verifyAdminConsoleViaLB();
      testCompletedSuccessfully = true;
    } finally {
      if (domain != null && (JENKINS || testCompletedSuccessfully)) {
        // domain.destroy();
        // TestUtils.verifyBeforeDeletion(domain);
        logger.info("About to delete domain: " + domain.getDomainUid());
        TestUtils.deleteWeblogicDomainResources(domain.getDomainUid());
        TestUtils.verifyAfterDeletion(domain);
      }
    }

    logger.info("SUCCESS - " + testMethodName);
  }

  /**
   * Create two operators if they are not running. Create domain domain4 with dynamic cluster in
   * default namespace, managed by operator1. Create domain domain5 with Configured cluster using
   * WDT in test2 namespace, managed by operator2 Verify scaling for domain5 cluster from 2 to 3
   * servers and back to 2, plus verify no impact on domain4 Cycle domain4 down and back up, plus
   * verify no impact on domain5 shutdown by deleting both domain4 and domain5 CRD's
   *
   * <p>ToDo: configured cluster support is removed from samples, modify the test to create
   *
   * @throws Exception
   */
  @Test
  public void testTwoDomainsManagedByTwoOperators() throws Exception {
    Assume.assumeFalse(QUICKTEST);
    String testMethodName = new Object() {}.getClass().getEnclosingMethod().getName();
    logTestBegin(testMethodName);
    logger.info("Creating Domain domain4 & verifing the domain creation");

    logger.info("Checking if operator1 and domain1 are running, if not creating");
    if (operator1 == null) {
      operator1 = TestUtils.createOperator(opManagingdefaultAndtest1NSYamlFile);
    }

    Domain domain1 = null, domain2 = null;
    boolean testCompletedSuccessfully = false;
    try {
      // load input yaml to map and add configOverrides
      Map<String, Object> wlstDomainMap = TestUtils.loadYaml(domainOnPVUsingWLSTYamlFile);
      wlstDomainMap.put("domainUID", "domain1onpvwlst");
      domain1 = TestUtils.createDomain(wlstDomainMap);
      domain1.verifyDomainCreated();
      testBasicUseCases(domain1);
      logger.info("Checking if operator2 is running, if not creating");
      if (operator2 == null) {
        operator2 = TestUtils.createOperator(opManagingtest2NSYamlFile);
      }
      // create domain5 with configured cluster
      // ToDo: configured cluster support is removed from samples, modify the test to create
      // configured cluster
      Map<String, Object> wdtDomainMap = TestUtils.loadYaml(domainOnPVUsingWDTYamlFile);
      wdtDomainMap.put("domainUID", "domain2onpvwdt");
      // wdtDomainMap.put("clusterType", "Configured");
      domain2 = TestUtils.createDomain(wdtDomainMap);
      domain2.verifyDomainCreated();
      testBasicUseCases(domain2);
      logger.info("Verify the only remaining running domain domain1 is unaffected");
      domain1.verifyDomainCreated();

      testClusterScaling(operator2, domain2);

      logger.info("Verify the only remaining running domain domain1 is unaffected");
      domain1.verifyDomainCreated();

      logger.info("Destroy and create domain4 and verify no impact on domain2");
      domain1.destroy();
      domain1.create();

      logger.info("Verify no impact on domain2");
      domain2.verifyDomainCreated();
      testCompletedSuccessfully = true;

    } finally {
      String domainUidsToBeDeleted = "";

      if (domain1 != null && (JENKINS || testCompletedSuccessfully)) {
        // domain1.destroy();
        // TestUtils.verifyBeforeDeletion(domain1);
        domainUidsToBeDeleted = domain1.getDomainUid();
      }
      if (domain2 != null && (JENKINS || testCompletedSuccessfully)) {
        // domain2.destroy();
        domainUidsToBeDeleted = domainUidsToBeDeleted + "," + domain2.getDomainUid();
      }
      if (!domainUidsToBeDeleted.equals("")) {
        logger.info("About to delete domains: " + domainUidsToBeDeleted);
        TestUtils.deleteWeblogicDomainResources(domainUidsToBeDeleted);

        TestUtils.verifyAfterDeletion(domain1);
        TestUtils.verifyAfterDeletion(domain2);
      }
    }
    logger.info("SUCCESS - " + testMethodName);
  }
  /**
   * Create operator if its not running and create domain with serverStartPolicy="ADMIN_ONLY".
   * Verify only admin server is created. shutdown by deleting domain CRD. Create domain on existing
   * PV dir, pv is already populated by a shutdown domain.
   *
   * @throws Exception
   */
  @Test
  public void testCreateDomainWithStartPolicyAdminOnly() throws Exception {
    Assume.assumeFalse(QUICKTEST);
    String testMethodName = new Object() {}.getClass().getEnclosingMethod().getName();
    logTestBegin(testMethodName);
    logger.info("Checking if operator1 is running, if not creating");
    if (operator1 == null) {
      operator1 = TestUtils.createOperator(opManagingdefaultAndtest1NSYamlFile);
    }
    logger.info("Creating Domain domain6 & verifing the domain creation");
    // create domain6
    Domain domain = null;
    boolean testCompletedSuccessfully = false;
    try {
      domain = TestUtils.createDomain(domainWithServerStartPolicyAsAdminOnlyYamlFile);
      domain.verifyDomainCreated();

    } finally {
      if (domain != null) {
        // create domain on existing dir
        domain.destroy();
      }
    }

    domain.createDomainOnExistingDirectory();

    logger.info("SUCCESS - " + testMethodName);
  }
  /**
   * Create operator and create domain with pvReclaimPolicy="Recycle" Verify that the PV is deleted
   * once the domain and PVC are deleted
   *
   * @throws Exception
   */
  @Test
  public void testCreateDomainPVReclaimPolicyRecycle() throws Exception {
    Assume.assumeFalse(QUICKTEST);
    String testMethodName = new Object() {}.getClass().getEnclosingMethod().getName();
    logTestBegin(testMethodName);
    logger.info("Checking if operator1 is running, if not creating");
    if (operator1 == null) {
      operator1 = TestUtils.createOperator(opManagingdefaultAndtest1NSYamlFile);
    }
    logger.info("Creating Domain domain7 & verifing the domain creation");
    // create domain7
    Domain domain = null;

    try {
      domain = TestUtils.createDomain(domainWithStorageReclaimPolicyRecycleYamlFile);
      domain.verifyDomainCreated();
    } finally {
      if (domain != null) domain.shutdown();
    }
    domain.deletePVCAndCheckPVReleased();
    logger.info("SUCCESS - " + testMethodName);
  }

  /**
   * Create operator and create domain with mostly default values from sample domain inputs, mainly
   * exposeAdminT3Channel and exposeAdminNodePort which are false by default and verify domain
   * startup and cluster scaling using operator rest endpoint works.
   *
   * <p>Also test samples/scripts/delete-domain/delete-weblogic-domain-resources.sh to delete domain
   * resources
   *
   * @throws Exception
   */
  @Test
  public void testCreateDomainWithDefaultValuesInSampleInputs() throws Exception {
    Assume.assumeFalse(QUICKTEST);
    String testMethodName = new Object() {}.getClass().getEnclosingMethod().getName();
    logTestBegin(testMethodName);
    logger.info("Creating Domain domain10 & verifing the domain creation");
    if (operator1 == null) {
      operator1 = TestUtils.createOperator(opManagingdefaultAndtest1NSYamlFile);
    }

    // create domain10
    Domain domain = null;
    boolean testCompletedSuccessfully = false;
    try {
      domain = TestUtils.createDomain(domain10YamlFile);
      domain.verifyDomainCreated();
      testBasicUseCases(domain);
      // testAdvancedUseCasesForADomain(operator1, domain10);
      testCompletedSuccessfully = true;
    } finally {
      if (domain != null && (JENKINS || testCompletedSuccessfully)) {
        domain.destroy();
      }
    }

    logger.info("SUCCESS - " + testMethodName);
  }

  /**
   * This test covers both auto and custom situational configuration use cases for config.xml.
   * Create Operator and create domain with listen address not set for admin server and t3
   * channel/NAP and incorrect file for admin server log location. Introspector should override
   * these with sit-config automatically. Also, with some junk value for t3 channel public address
   * and using custom situational config override replace with valid public address using secret.
   * Verify the domain is started successfully and web application can be deployed and accessed.
   * Verify that the JMS client can actually use the overridden values. Use NFS storage on Jenkins
   *
   * @throws Exception
   */
  @Test
  public void testAutoAndCustomSitConfigOverrides() throws Exception {
    Assume.assumeFalse(QUICKTEST);
    String testMethod = new Object() {}.getClass().getEnclosingMethod().getName();
    logTestBegin(testMethod);

    if (operator1 == null) {
      operator1 = TestUtils.createOperator(opManagingdefaultAndtest1NSYamlFile);
    }
    Domain domain11 = null;
    boolean testCompletedSuccessfully = false;
    String createDomainScriptDir =
        BaseTest.getProjectRoot() + "/integration-tests/src/test/resources/domain-home-on-pv";
    try {
      // load input yaml to map and add configOverrides
      Map<String, Object> domainMap = TestUtils.loadYaml(domainOnPVUsingWLSTYamlFile);
      domainMap.put("configOverrides", "sitconfigcm");
      domainMap.put("domainUID", "customsitdomain");

      // use NFS for this domain on Jenkins, defaultis HOST_PATH
      if (System.getenv("JENKINS") != null && System.getenv("JENKINS").equalsIgnoreCase("true")) {
        domainMap.put("weblogicDomainStorageType", "NFS");
      }

      // cp py
      Files.copy(
          new File(createDomainScriptDir + "/create-domain.py").toPath(),
          new File(createDomainScriptDir + "/create-domain.py.bak").toPath(),
          StandardCopyOption.REPLACE_EXISTING);
      Files.copy(
          new File(createDomainScriptDir + "/create-domain-auto-custom-sit-config.py").toPath(),
          new File(createDomainScriptDir + "/create-domain.py").toPath(),
          StandardCopyOption.REPLACE_EXISTING);

      domain11 = TestUtils.createDomain(domainMap);
      domain11.verifyDomainCreated();
      testBasicUseCases(domain11);
      testAdminT3ChannelWithJMS(domain11);
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
    logger.info("SUCCESS - " + testMethod);
  }

  /**
   * Create operator and enable external rest endpoint using the externalOperatorCert and
   * externalOperatorKey defined in the helm chart values instead of the tls secret. This test is
   * for backward compatibility
   *
   * @throws Exception
   */
  @Test
  public void testOperatorRESTIdentityBackwardCompatibility() throws Exception {
    Assume.assumeFalse(QUICKTEST);
    String testMethodName = new Object() {}.getClass().getEnclosingMethod().getName();
    logTestBegin(testMethodName);
    logger.info("Checking if operatorForBackwardCompatibility is running, if not creating");
    if (operatorForBackwardCompatibility == null) {
      operatorForBackwardCompatibility = TestUtils.createOperator(opForBackwardCompatibility, true);
    }
    logger.info("Operator using legacy REST identity created successfully");
    operatorForBackwardCompatibility.destroy();
    logger.info("SUCCESS - " + testMethodName);
  }

  private Domain testAdvancedUseCasesForADomain(Operator operator, Domain domain) throws Exception {
    if (!SMOKETEST) {
      testClusterScaling(operator, domain);
      testDomainLifecyle(operator, domain);
      testOperatorLifecycle(operator, domain);
    }
    return domain;
  }

  private void testBasicUseCases(Domain domain) throws Exception {
    testAdminT3Channel(domain);
    testAdminServerExternalService(domain);
  }
}
