// Copyright (c) 2019, Oracle Corporation and/or its affiliates.  All rights reserved.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator;

import java.util.Map;

import oracle.kubernetes.operator.utils.DbUtils;
import oracle.kubernetes.operator.utils.JrfDomain;
import oracle.kubernetes.operator.utils.Operator;
import oracle.kubernetes.operator.utils.TestUtils;
import org.junit.AfterClass;
import org.junit.Assume;
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
  private static final String JRF_DOMAIN_ON_PV_WLST_FILE_2 = "jrfdomainonpvwlst2.yaml";
  private static final String JRF_DOMAIN_ADMINONLY_YAML = "jrfdomainadminonly.yaml";
  private static final String JRF_DOMAIN_RECYCLEPOLICY_YAML = "jrfdomainrecyclepolicy.yaml";
  private static final String JRF_DOMAIN_SAMPLE_DEFAULTS_YAML = "jrfdomainsampledefaults.yaml";
  // property file for oracle db information
  private static final String DB_PROP_FILE = "oracledb.properties";
  private static Operator operator1;
  private static Operator operator2;
  private static String rcuPodName;

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
    if (FULLTEST) {
      // initialize test properties and create the directories
      initialize(APP_PROPS_FILE);
  
      // create DB used for jrf domain
      DbUtils.createOracleDB(DB_PROP_FILE);
  
      // run RCU first
      DbUtils.deleteNamespace(DbUtils.DEFAULT_RCU_NAMESPACE);
      DbUtils.createNamespace(DbUtils.DEFAULT_RCU_NAMESPACE);
      rcuPodName = DbUtils.createRcuPod(DbUtils.DEFAULT_RCU_NAMESPACE);
  
      // TODO: reconsider the logic to check the db readiness
      // The jrfdomain can not find the db pod even the db pod shows ready, sleep more time
      logger.info("waiting for the db to be visible to rcu script ...");
      Thread.sleep(20000);
    }
  }

  /**
   * This method will run once after all test methods are finished. It Releases k8s cluster lease,
   * archives result, pv directories.
   *
   * @throws Exception - if any error occurs
   */
  @AfterClass
  public static void staticUnPrepare() throws Exception {
    if (FULLTEST) {
      logger.info("+++++++++++++++++++++++++++++++++---------------------------------+");
      logger.info("BEGIN");
      logger.info("Run once, release cluster lease");
  
      tearDown(new Object() {}.getClass().getEnclosingClass().getSimpleName());
  
      logger.info("SUCCESS");
    }
  }

  /**
   * Create operator and verify it's deployed successfully. Create jrf domain and verify domain is
   * started. Verify liveness probe by killing managed server 1 process 3 times to kick pod
   * auto-restart. Shutdown the domain by changing domain serverStartPolicy to NEVER.
   *
   * @throws Exception - if any error occurs when create operator and jrf domains
   */
  @Test
  public void testJrfDomainOnPvUsingWlst() throws Exception {
    Assume.assumeTrue(FULLTEST);
    String testMethodName = new Object() {}.getClass().getEnclosingMethod().getName();
    logTestBegin(testMethodName);
    logger.info("Creating Operator & waiting for the script to complete execution");
    // create operator1
    if (operator1 == null) {
      operator1 = TestUtils.createOperator(JRF_OPERATOR_FILE_1);
    }

    JrfDomain jrfdomain = null;
    boolean testCompletedSuccessfully = false;

    try {
      // run RCU script to load db schema
      DbUtils.runRcu(rcuPodName, JRF_DOMAIN_ON_PV_WLST_FILE);
      // create JRF domain
      jrfdomain = new JrfDomain(JRF_DOMAIN_ON_PV_WLST_FILE);
      // verify JRF domain created, servers up and running
      jrfdomain.verifyDomainCreated();
      // basic test cases
      testBasicUseCases(jrfdomain);
      // more advanced use cases
      if (FULLTEST) {
        testAdvancedUseCasesForADomain(operator1, jrfdomain);
      }
      logger.info("testing WlsLivenessProbe ...");
      jrfdomain.testWlsLivenessProbe();
      testCompletedSuccessfully = true;
    } finally {
      if (jrfdomain != null && (JENKINS || testCompletedSuccessfully)) {
        jrfdomain.shutdownUsingServerStartPolicy();
      }
    }

    logger.info("SUCCESS - " + testMethodName);
  }

  /**
   * Create two operators if they are not running. Create domain domain1 in the default namespace,
   * managed by operator1. Create domain domain2 in test2 namespace, managed by operator2. Verify
   * scaling for domain2 cluster from 2 to 3 servers and back to 2, plus verify no impact on
   * domain1. Cycle domain1 down and back up, plus verify no impact on domain2. shutdown by the
   * domains using the delete resource script from samples.
   *
   * @throws Exception - if any error occurs
   */
  @Test
  public void testTwoJrfDomainsManagedByTwoOperators() throws Exception {
    Assume.assumeFalse(QUICKTEST);
    String testMethodName = new Object() {}.getClass().getEnclosingMethod().getName();
    logTestBegin(testMethodName);

    logger.info("Checking if operator1 and domain1 are running, if not creating");
    if (operator1 == null) {
      operator1 = TestUtils.createOperator(JRF_OPERATOR_FILE_1);
    }

    JrfDomain domain1 = null;
    JrfDomain domain2 = null;
    boolean testCompletedSuccessfully = false;
    try {
      // load input yaml to map and add configOverrides
      Map<String, Object> domain1Map = TestUtils.loadYaml(JRF_DOMAIN_ON_PV_WLST_FILE);
      domain1Map.put("domainUID", "jrfdomain1");
      domain1Map.put("adminNodePort", 30702);
      domain1Map.put("t3ChannelPort", 30023);
      domain1Map.put("voyagerWebPort", 30307);
      domain1Map.put("rcuSchemaPrefix", "jrfdomain1");
      domain1Map.put("namespace", "default");

      // run RCU script to load db schema
      DbUtils.runRcu(rcuPodName, domain1Map);

      Map<String, Object> domain2Map = TestUtils.loadYaml(JRF_DOMAIN_ON_PV_WLST_FILE_2);
      domain2Map.put("domainUID", "jrfdomain2");
      domain2Map.put("adminNodePort", 30703);
      domain2Map.put("t3ChannelPort", 30024);
      domain2Map.put("voyagerWebPort", 30308);

      // run RCU script to load db schema
      DbUtils.runRcu(rcuPodName, domain2Map);

      // create domain1
      logger.info("Creating Domain domain1 & verifying the domain creation");
      domain1 = new JrfDomain(domain1Map);
      domain1.verifyDomainCreated();

      testBasicUseCases(domain1);

      logger.info("Checking if operator2 is running, if not creating");
      if (operator2 == null) {
        operator2 = TestUtils.createOperator(OPERATOR2_YAML);
      }

      // create domain2
      domain2 = new JrfDomain(domain2Map);
      domain2.verifyDomainCreated();

      testBasicUseCases(domain2);

      logger.info("Verify the running domain domain1 is unaffected");
      domain1.verifyDomainCreated();

      testClusterScaling(operator2, domain2);

      logger.info("Verify the running domain domain1 is unaffected");
      domain1.verifyDomainCreated();

      logger.info("Destroy and create domain1 and verify no impact on domain2");
      domain1.destroy();
      domain1.create();

      logger.info("Verify no impact on domain2");
      domain2.verifyDomainCreated();
      testCompletedSuccessfully = true;

    } finally {
      String domainUidsToBeDeleted = "";

      if (domain1 != null && (JENKINS || testCompletedSuccessfully)) {
        domainUidsToBeDeleted = domain1.getDomainUid();
      }
      if (domain2 != null && (JENKINS || testCompletedSuccessfully)) {
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
   * create two JRF domains in the same namespace and managed by one operator. Verify scaling up and
   * down for domain1 cluster will have no impact on domain2. Cycle domain2 down and back up, verify
   * there is no impact on domain1. shutdown by the domains using the delete resource script from
   * samples.
   *
   * @throws Exception - if any error occurs
   */
  @Test
  public void testTwoJrfDomainsManagedByOneOperatorInSameNS() throws Exception {
    Assume.assumeFalse(QUICKTEST);
    String testMethodName = new Object() {}.getClass().getEnclosingMethod().getName();
    logTestBegin(testMethodName);

    logger.info("Checking if operator1 and domain1 are running, if not creating");
    if (operator1 == null) {
      operator1 = TestUtils.createOperator(JRF_OPERATOR_FILE_1);
    }

    JrfDomain domain1 = null;
    JrfDomain domain2 = null;
    boolean testCompletedSuccessfully = false;
    try {
      // load input yaml to map and add configOverrides
      Map<String, Object> domain1Map = TestUtils.loadYaml(JRF_DOMAIN_ON_PV_WLST_FILE);
      domain1Map.put("domainUID", "jrfd1");
      domain1Map.put("adminNodePort", 30705);
      domain1Map.put("t3ChannelPort", 30025);
      domain1Map.put("voyagerWebPort", 30309);
      domain1Map.put("rcuSchemaPrefix", "jrfd1");

      // run RCU script to load db schema
      DbUtils.runRcu(rcuPodName, domain1Map);

      Map<String, Object> domain2Map = TestUtils.loadYaml(JRF_DOMAIN_ON_PV_WLST_FILE);
      domain2Map.put("domainUID", "jrfd2");
      domain2Map.put("adminNodePort", 30706);
      domain2Map.put("t3ChannelPort", 30026);
      domain2Map.put("voyagerWebPort", 30310);
      domain2Map.put("rcuSchemaPrefix", "jrfd2");

      // run RCU script to load db schema
      DbUtils.runRcu(rcuPodName, domain2Map);

      // create domain1
      logger.info("Creating Domain domain1 & verifying the domain creation");
      domain1 = new JrfDomain(domain1Map);
      domain1.verifyDomainCreated();

      testBasicUseCases(domain1);

      // create domain2
      domain2 = new JrfDomain(domain2Map);
      domain2.verifyDomainCreated();

      testBasicUseCases(domain2);

      logger.info("Verify the running domain domain1 is unaffected");
      domain1.verifyDomainCreated();

      testClusterScaling(operator1, domain1);

      logger.info("Verify the running domain domain2 is unaffected");
      domain2.verifyDomainCreated();

      logger.info("Destroy and create domain2 and verify no impact on domain1");
      domain2.destroy();
      domain2.create();

      logger.info("Verify no impact on domain1");
      domain1.verifyDomainCreated();
      testCompletedSuccessfully = true;

    } finally {
      String domainUidsToBeDeleted = "";

      if (domain1 != null && (JENKINS || testCompletedSuccessfully)) {
        domainUidsToBeDeleted = domain1.getDomainUid();
      }
      if (domain2 != null && (JENKINS || testCompletedSuccessfully)) {
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
   * create two JRF domains in the different namespaces and managed by one operator. Domain1 uses
   * Voyager load balancer, Domain2 uses Traefik load balancer. Verify scaling up and down for
   * domain1 cluster will have no impact on domain2. Cycle domain2 down and back up, verify there is
   * no impact on domain1. shutdown by the domains using the delete resource script from samples.
   *
   * @throws Exception - if any error occurs
   */
  @Test
  public void testTwoJrfDomainsManagedByOneOperatorInDifferentNS() throws Exception {
    Assume.assumeFalse(QUICKTEST);
    String testMethodName = new Object() {}.getClass().getEnclosingMethod().getName();
    logTestBegin(testMethodName);

    logger.info("Checking if operator1 and domain1 are running, if not creating");
    if (operator1 == null) {
      operator1 = TestUtils.createOperator(JRF_OPERATOR_FILE_1);
    }

    JrfDomain domain1 = null;
    JrfDomain domain2 = null;
    boolean testCompletedSuccessfully = false;
    try {
      // load input yaml to map and add configOverrides
      Map<String, Object> domain1Map = TestUtils.loadYaml(JRF_DOMAIN_ON_PV_WLST_FILE);
      domain1Map.put("domainUID", "jrfd11");
      domain1Map.put("adminNodePort", 30707);
      domain1Map.put("t3ChannelPort", 30027);
      domain1Map.put("voyagerWebPort", 30311);
      domain1Map.put("rcuSchemaPrefix", "jrfd11");
      domain1Map.put("namespace", "default");
      domain1Map.put("loadBalancer", "VOYAGER");

      // run RCU script to load db schema
      DbUtils.runRcu(rcuPodName, domain1Map);

      Map<String, Object> domain2Map = TestUtils.loadYaml(JRF_DOMAIN_ON_PV_WLST_FILE);
      domain2Map.put("domainUID", "jrfd21");
      domain2Map.put("adminNodePort", 30708);
      domain2Map.put("t3ChannelPort", 30028);
      domain2Map.put("voyagerWebPort", 30312);
      domain2Map.put("rcuSchemaPrefix", "jrfd21");

      // run RCU script to load db schema
      DbUtils.runRcu(rcuPodName, domain2Map);

      // create domain1
      logger.info("Creating Domain domain1 & verifying the domain creation");
      domain1 = new JrfDomain(domain1Map);
      domain1.verifyDomainCreated();

      testBasicUseCases(domain1);

      // create domain2
      domain2 = new JrfDomain(domain2Map);
      domain2.verifyDomainCreated();

      testBasicUseCases(domain2);

      logger.info("Verify the running domain domain1 is unaffected");
      domain1.verifyDomainCreated();

      testClusterScaling(operator1, domain1);

      logger.info("Verify the running domain domain2 is unaffected");
      domain2.verifyDomainCreated();

      logger.info("Destroy and create domain2 and verify no impact on domain1");
      domain2.destroy();
      domain2.create();

      logger.info("Verify no impact on domain1");
      domain1.verifyDomainCreated();
      testCompletedSuccessfully = true;

    } finally {
      String domainUidsToBeDeleted = "";

      if (domain1 != null && (JENKINS || testCompletedSuccessfully)) {
        domainUidsToBeDeleted = domain1.getDomainUid();
      }
      if (domain2 != null && (JENKINS || testCompletedSuccessfully)) {
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
   * @throws Exception - if any error occurs
   */
  @Test
  public void testCreateJrfDomainWithStartPolicyAdminOnly() throws Exception {
    Assume.assumeFalse(QUICKTEST);
    String testMethodName = new Object() {}.getClass().getEnclosingMethod().getName();
    logTestBegin(testMethodName);
    logger.info("Checking if operator1 is running, if not creating");
    if (operator1 == null) {
      operator1 = TestUtils.createOperator(JRF_OPERATOR_FILE_1);
    }
    logger.info("Creating Domain & verifying the domain creation");
    // create domain
    JrfDomain domain = null;
    try {
      // run RCU script to load db schema
      DbUtils.runRcu(rcuPodName, JRF_DOMAIN_ADMINONLY_YAML);

      domain = new JrfDomain(JRF_DOMAIN_ADMINONLY_YAML);
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
   * Create operator and create jrf domain with pvReclaimPolicy="Recycle" Verify that the PV is
   * deleted once the domain and PVC are deleted.
   *
   * @throws Exception - if any error occurs
   */
  @Test
  public void testCreateJrfDomainPvReclaimPolicyRecycle() throws Exception {
    Assume.assumeFalse(QUICKTEST);
    String testMethodName = new Object() {}.getClass().getEnclosingMethod().getName();
    logTestBegin(testMethodName);
    logger.info("Checking if operator1 is running, if not creating");
    if (operator1 == null) {
      operator1 = TestUtils.createOperator(JRF_OPERATOR_FILE_1);
    }
    logger.info("Creating Domain domain & verifying the domain creation");
    // create domain
    JrfDomain domain = null;

    try {
      // run RCU script to load db schema
      DbUtils.runRcu(rcuPodName, JRF_DOMAIN_RECYCLEPOLICY_YAML);

      domain = new JrfDomain(JRF_DOMAIN_RECYCLEPOLICY_YAML);
      domain.verifyDomainCreated();
    } finally {
      if (domain != null) domain.shutdown();
    }
    domain.deletePvcAndCheckPvReleased("create-fmw-infra-sample-domain-job");
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
   * @throws Exception - if any error occurs
   */
  @Test
  public void testCreateJrfDomainWithDefaultValuesInSampleInputs() throws Exception {
    Assume.assumeFalse(QUICKTEST);
    String testMethodName = new Object() {}.getClass().getEnclosingMethod().getName();
    logTestBegin(testMethodName);
    logger.info("Creating Domain domain10 & verifing the domain creation");
    if (operator1 == null) {
      operator1 = TestUtils.createOperator(JRF_OPERATOR_FILE_1);
    }

    // create domain10
    JrfDomain domain = null;
    boolean testCompletedSuccessfully = false;
    try {
      // run RCU script to load db schema
      DbUtils.runRcu(rcuPodName, JRF_DOMAIN_SAMPLE_DEFAULTS_YAML);

      domain = new JrfDomain(JRF_DOMAIN_SAMPLE_DEFAULTS_YAML);
      domain.verifyDomainCreated();
      testBasicUseCases(domain);
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
   * @throws Exception - if any error occurs
   */
  @Test
  public void testAutoAndCustomSitConfigOverrides() throws Exception {
    Assume.assumeFalse(QUICKTEST);
    String testMethod = new Object() {}.getClass().getEnclosingMethod().getName();
    logTestBegin(testMethod);

    if (operator1 == null) {
      operator1 = TestUtils.createOperator(JRF_OPERATOR_FILE_1);
    }

    JrfDomain domain11 = null;
    boolean testCompletedSuccessfully = false;
    try {
      // load input yaml to map and add configOverrides
      Map<String, Object> domainMap = TestUtils.loadYaml(JRF_DOMAIN_ON_PV_WLST_FILE);
      domainMap.put("configOverrides", "sitconfigcm");
      domainMap.put(
          "configOverridesFile",
          getProjectRoot()
              + "/integration-tests/src/test/resources/domain-home-on-pv/customsitconfig");
      domainMap.put("domainUID", "customsitdomain");
      domainMap.put("adminNodePort", 30704);
      domainMap.put("t3ChannelPort", 30051);
      domainMap.put(
          "createDomainPyScript",
          "integration-tests/src/test/resources/domain-home-on-pv/create-jrfdomain-auto-custom-sit-config.py");
      domainMap.put("voyagerWebPort", 30312);
      domainMap.put("rcuSchemaPrefix", "customsit");

      // use NFS for this domain on Jenkins, defaultis HOST_PATH
      if (System.getenv("JENKINS") != null && System.getenv("JENKINS").equalsIgnoreCase("true")) {
        domainMap.put("weblogicDomainStorageType", "NFS");
      }

      // run RCU script to load db schema
      DbUtils.runRcu(rcuPodName, domainMap);

      domain11 = new JrfDomain(domainMap);
      domain11.verifyDomainCreated();

      testAdminT3Channel(domain11);
      testAdminServerExternalService(domain11);

      testCompletedSuccessfully = true;

    } finally {
      if (domain11 != null && (JENKINS || testCompletedSuccessfully)) {
        domain11.destroy();
      }
    }
    logger.info("SUCCESS - " + testMethod);
  }

  /**
   * deploy testwebapp using admin port.
   *
   * @param domain - jrfdomain
   * @throws Exception - if any error occurs
   */
  private void testDeployAppUsingAdminPort(JrfDomain domain) throws Exception {
    Map<String, Object> domainMap = domain.getDomainMap();
    // create the app directory in admin pod
    TestUtils.kubectlexec(
        domain.getDomainUid() + ("-") + domainMap.get("adminServerName"),
        "" + domainMap.get("namespace"),
        " -- mkdir -p " + appLocationInPod);

    domain.deployWebAppViaWlst(
        TESTWEBAPP,
        getProjectRoot() + "/src/integration-tests/apps/testwebapp.war",
        appLocationInPod,
        getUsername(),
        getPassword(),
        true);

    domain.verifyWebAppLoadBalancing(TESTWEBAPP);
  }

  /**
   * basic test cases.
   *
   * @param domain - jrfdomain
   * @throws Exception - if any error occurs
   */
  private void testBasicUseCases(JrfDomain domain) throws Exception {
    // Bug 29591809
    // TODO: re-enable the test once the bug is fixed
    // testAdminT3Channel(domain);
    testDeployAppUsingAdminPort(domain);
    testAdminServerExternalService(domain);
  }

  /**
   * advanced test cases.
   *
   * @param operator - weblogic operator
   * @param domain - jrfdomain
   * @throws Exception - if any error occurs
   */
  private void testAdvancedUseCasesForADomain(Operator operator, JrfDomain domain)
      throws Exception {
    testClusterScaling(operator, domain);
    int port = (Integer) domain.getDomainMap().get("managedServerPort");
    testDomainLifecyle(operator, domain, port);
    testOperatorLifecycle(operator, domain);
    
  }
}
