// Copyright 2018, Oracle Corporation and/or its affiliates.  All rights reserved.
// Licensed under the Universal Permissive License v 1.0 as shown at
// http://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator;

import java.util.Map;
import oracle.kubernetes.operator.utils.Domain;
import oracle.kubernetes.operator.utils.Operator;
import oracle.kubernetes.operator.utils.Operator.RESTCertType;
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

  private static Operator operator1, operator2;

  private static Operator operatorForBackwardCompatibility;
  private static Operator operatorForRESTCertChain;

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
    initialize(APP_PROPS_FILE);
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

    tearDown();

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
    if (operator1 == null) {
      operator1 = TestUtils.createOperator(OPERATOR1_YAML);
    }
    Domain domain = null;
    boolean testCompletedSuccessfully = false;
    try {
      domain = TestUtils.createDomain(DOMAINONPV_WLST_YAML);
      domain.verifyDomainCreated();
      testBasicUseCases(domain);
      TestUtils.renewK8sClusterLease(getProjectRoot(), getLeaseId());
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
      operator2 = TestUtils.createOperator(OPERATOR2_YAML);
    }
    Domain domain = null;
    boolean testCompletedSuccessfully = false;
    try {
      // create domain
      domain = TestUtils.createDomain(DOMAINONPV_WDT_YAML);
      domain.verifyDomainCreated();
      testBasicUseCases(domain);
      testWLDFScaling(operator2, domain);
      // TODO: Test Apache LB
      // domain.verifyAdminConsoleViaLB();
      testCompletedSuccessfully = true;
    } finally {
      if (domain != null && (JENKINS || testCompletedSuccessfully)) {
        logger.info("About to delete domain: " + domain.getDomainUid());
        TestUtils.deleteWeblogicDomainResources(domain.getDomainUid());
        TestUtils.verifyAfterDeletion(domain);
      }
    }

    logger.info("SUCCESS - " + testMethodName);
  }

  /**
   * Create two operators if they are not running. Create domain domain1 with dynamic cluster in
   * default namespace, managed by operator1. Create domain domain2 with Configured cluster using
   * WDT in test2 namespace, managed by operator2. Verify scaling for domain2 cluster from 2 to 3
   * servers and back to 2, plus verify no impact on domain1. Cycle domain1 down and back up, plus
   * verify no impact on domain2. shutdown by the domains using the delete resource script from
   * samples.
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
    logger.info("Creating Domain domain1 & verifing the domain creation");

    logger.info("Checking if operator1 and domain1 are running, if not creating");
    if (operator1 == null) {
      operator1 = TestUtils.createOperator(OPERATOR1_YAML);
    }

    Domain domain1 = null, domain2 = null;
    boolean testCompletedSuccessfully = false;
    try {
      // load input yaml to map and add configOverrides
      Map<String, Object> wlstDomainMap = TestUtils.loadYaml(DOMAINONPV_WLST_YAML);
      wlstDomainMap.put("domainUID", "domain1onpvwlst");
      wlstDomainMap.put("adminNodePort", new Integer("30702"));
      wlstDomainMap.put("t3ChannelPort", new Integer("30031"));
      wlstDomainMap.put("voyagerWebPort", new Integer("30307"));
      domain1 = TestUtils.createDomain(wlstDomainMap);
      domain1.verifyDomainCreated();
      testBasicUseCases(domain1);
      logger.info("Checking if operator2 is running, if not creating");
      if (operator2 == null) {
        operator2 = TestUtils.createOperator(OPERATOR2_YAML);
      }
      // create domain2 with configured cluster
      // ToDo: configured cluster support is removed from samples, modify the test to create
      // configured cluster
      Map<String, Object> wdtDomainMap = TestUtils.loadYaml(DOMAINONPV_WDT_YAML);
      wdtDomainMap.put("domainUID", "domain2onpvwdt");
      wdtDomainMap.put("adminNodePort", new Integer("30703"));
      wdtDomainMap.put("t3ChannelPort", new Integer("30041"));
      // wdtDomainMap.put("clusterType", "Configured");
      wdtDomainMap.put("voyagerWebPort", new Integer("30308"));
      domain2 = TestUtils.createDomain(wdtDomainMap);
      domain2.verifyDomainCreated();
      testBasicUseCases(domain2);
      logger.info("Verify the only remaining running domain domain1 is unaffected");
      domain1.verifyDomainCreated();

      testClusterScaling(operator2, domain2);

      logger.info("Verify the only remaining running domain domain1 is unaffected");
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
      operator1 = TestUtils.createOperator(OPERATOR1_YAML);
    }
    logger.info("Creating Domain domain6 & verifing the domain creation");
    // create domain
    Domain domain = null;
    boolean testCompletedSuccessfully = false;
    try {
      domain = TestUtils.createDomain(DOMAIN_ADMINONLY_YAML);
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
      operator1 = TestUtils.createOperator(OPERATOR1_YAML);
    }
    logger.info("Creating Domain domain & verifing the domain creation");
    // create domain
    Domain domain = null;

    try {
      domain = TestUtils.createDomain(DOMAIN_RECYCLEPOLICY_YAML);
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
      operator1 = TestUtils.createOperator(OPERATOR1_YAML);
    }

    // create domain10
    Domain domain = null;
    boolean testCompletedSuccessfully = false;
    try {
      domain = TestUtils.createDomain(DOMAIN_SAMPLE_DEFAULTS_YAML);
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
      operator1 = TestUtils.createOperator(OPERATOR1_YAML);
    }
    Domain domain11 = null;
    boolean testCompletedSuccessfully = false;
    try {
      // load input yaml to map and add configOverrides
      Map<String, Object> domainMap = TestUtils.loadYaml(DOMAINONPV_WLST_YAML);
      domainMap.put("configOverrides", "sitconfigcm");
      domainMap.put(
          "configOverridesFile",
          BaseTest.getProjectRoot()
              + "/integration-tests/src/test/resources/domain-home-on-pv/customsitconfig");
      domainMap.put("domainUID", "customsitdomain");
      domainMap.put("adminNodePort", new Integer("30704"));
      domainMap.put("t3ChannelPort", new Integer("30051"));
      domainMap.put(
          "createDomainPyScript",
          "integration-tests/src/test/resources/domain-home-on-pv/create-domain-auto-custom-sit-config.py");
      domainMap.put("voyagerWebPort", new Integer("30312"));

      // use NFS for this domain on Jenkins, defaultis HOST_PATH
      if (System.getenv("JENKINS") != null && System.getenv("JENKINS").equalsIgnoreCase("true")) {
        domainMap.put("weblogicDomainStorageType", "NFS");
      }

      domain11 = TestUtils.createDomain(domainMap);
      domain11.verifyDomainCreated();
      testBasicUseCases(domain11);
      testAdminT3ChannelWithJMS(domain11);
      testCompletedSuccessfully = true;

    } finally {
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
      operatorForBackwardCompatibility =
          TestUtils.createOperator(OPERATORBC_YAML, RESTCertType.LEGACY);
    }
    operatorForBackwardCompatibility.verifyOperatorExternalRESTEndpoint();
    logger.info("Operator using legacy REST identity created successfully");
    operatorForBackwardCompatibility.destroy();
    logger.info("SUCCESS - " + testMethodName);
  }

  /**
   * Create operator and enable external rest endpoint using a certificate chain. This test uses the
   * operator backward compatibility operator because that operator is destroyed.
   *
   * @throws Exception
   */
  @Test
  public void testOperatorRESTUsingCertificateChain() throws Exception {
    Assume.assumeFalse(QUICKTEST);

    logTestBegin("testOperatorRESTUsingCertificateChain");
    logger.info("Checking if operatorForBackwardCompatibility is running, if not creating");
    if (operatorForRESTCertChain == null) {
      operatorForRESTCertChain = TestUtils.createOperator(OPERATOR_CHAIN_YAML, RESTCertType.CHAIN);
    }
    operatorForRESTCertChain.verifyOperatorExternalRESTEndpoint();
    logger.info("Operator using legacy REST identity created successfully");
    logger.info("SUCCESS - testOperatorRESTUsingCertificateChain");
  }

  /**
   * Create Operator and create domain using domain-in-image option. Verify the domain is started
   * successfully and web application can be deployed and accessed.
   *
   * @throws Exception
   */
  @Test
  public void testDomainInImageUsingWLST() throws Exception {
    Assume.assumeFalse(QUICKTEST);
    String testMethodName = new Object() {}.getClass().getEnclosingMethod().getName();
    logTestBegin(testMethodName);

    logger.info("Checking if operator1 is running, if not creating");
    if (operator1 == null) {
      operator1 = TestUtils.createOperator(OPERATOR1_YAML);
    }
    logger.info("Creating Domain & verifing the domain creation");
    // create domain
    Domain domain = null;
    boolean testCompletedSuccessfully = false;
    try {
      domain = TestUtils.createDomain(DOMAININIMAGE_WLST_YAML);
      domain.verifyDomainCreated();

      testBasicUseCases(domain);
      testClusterScaling(operator1, domain);
      testCompletedSuccessfully = true;
    } finally {
      if (domain != null && (JENKINS || testCompletedSuccessfully)) domain.destroy();
    }
    logger.info("SUCCESS - " + testMethodName);
  }
  /**
   * Create Operator and create domain using domain-in-image option. Verify the domain is started
   * successfully and web application can be deployed and accessed.
   *
   * @throws Exception
   */
  @Test
  public void testDomainInImageUsingWDT() throws Exception {
    Assume.assumeFalse(QUICKTEST);
    String testMethodName = new Object() {}.getClass().getEnclosingMethod().getName();
    logTestBegin(testMethodName);

    logger.info("Checking if operator1 is running, if not creating");
    if (operator1 == null) {
      operator1 = TestUtils.createOperator(OPERATOR1_YAML);
    }
    logger.info("Creating Domain & verifing the domain creation");
    // create domain
    Domain domain = null;
    boolean testCompletedSuccessfully = false;
    try {
      domain = TestUtils.createDomain(DOMAININIMAGE_WDT_YAML);
      domain.verifyDomainCreated();

      testBasicUseCases(domain);
      testClusterScaling(operator1, domain);
      testCompletedSuccessfully = true;
    } finally {
      if (domain != null && (JENKINS || testCompletedSuccessfully)) domain.destroy();
    }
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
