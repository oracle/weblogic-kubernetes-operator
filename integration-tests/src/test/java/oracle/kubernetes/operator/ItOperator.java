// Copyright 2018, 2019, Oracle Corporation and/or its affiliates.  All rights reserved.
// Licensed under the Universal Permissive License v 1.0 as shown at
// http://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator;

import java.util.ArrayList;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;


import oracle.kubernetes.operator.utils.Domain;
import oracle.kubernetes.operator.utils.LoggerHelper;
import oracle.kubernetes.operator.utils.Operator;
import oracle.kubernetes.operator.utils.Operator.RestCertType;
import oracle.kubernetes.operator.utils.TestUtils;
import org.junit.AfterClass;
import org.junit.Assert;
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
public class ItOperator extends BaseTest {
  private static Operator operator1;
  private static String domainNS1 ;
  private static String testClassName ;
  /**
   * This method gets called only once before any of the test methods are executed. It does the
   * initialization of the integration test properties defined in OperatorIT.properties and setting
   * the resultRoot, pvRoot and projectRoot attributes.
   *
   * @throws Exception exception
   */
  @BeforeClass
  public static void staticPrepare() throws Exception {
    testClassName = new Object() {}.getClass().getEnclosingClass().getSimpleName();
    
    // initialize test properties and create the directories
    initialize(APP_PROPS_FILE, testClassName);
    // create operator1
    if(operator1 == null ) {
      Map<String, Object> operatorMap = TestUtils.createOperatorMap(getNewNumber(), true, testClassName);
      operator1 = TestUtils.createOperator(operatorMap, Operator.RestCertType.SELF_SIGNED);
      Assert.assertNotNull(operator1);
      domainNS1 = ((ArrayList<String>)operatorMap.get("domainNamespaces")).get(0);
    }
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
   * @throws Exception exception
   */
  @Test
  public void testDomainOnPvUsingWlst() throws Exception {
    Assume.assumeTrue(QUICKTEST);
    String testMethodName = new Object() {}.getClass().getEnclosingMethod().getName();
    logTestBegin(testMethodName);
    LoggerHelper.getLocal().log(Level.INFO, "Creating Operator & waiting for the script to complete execution");
    Domain domain = null;
    boolean testCompletedSuccessfully = false;
    try {
      Map<String, Object> domainMap = TestUtils.createDomainMap(getNewNumber(), testClassName);
      // domainMap.put("domainUID", "domainonpvwlst");
      domainMap.put("namespace", domainNS1);
      domainMap.put("createDomainPyScript","integration-tests/src/test/resources/domain-home-on-pv/create-domain-custom-nap.py");
      domain = TestUtils.createDomain(domainMap);
      domain.verifyDomainCreated();
      testBasicUseCases(domain);
      TestUtils.renewK8sClusterLease(getProjectRoot(), getLeaseId());
      testAdvancedUseCasesForADomain(operator1, domain);
      domain.testWlsLivenessProbe();
      testCompletedSuccessfully = true;
    } finally {
      if (domain != null && (JENKINS || testCompletedSuccessfully))
        // domain.shutdownUsingServerStartPolicy();
        TestUtils.deleteWeblogicDomainResources(domain.getDomainUid());
        /* if(domain != null) {
          TestUtils.verifyAfterDeletion(domain);
        } */
    } 

    LoggerHelper.getLocal().log(Level.INFO, "SUCCESS - " + testMethodName);
  }

  /**
   * Create operator if its not running. Create domain with dynamic cluster using WDT and verify the
   * domain is started successfully. Verify cluster scaling by doing scale up for domain3 using WLDF
   * scaling shutdown by deleting domain CRD using yaml
   *
   * <p>TODO: Create domain using APACHE load balancer and verify domain is started successfully and
   * access admin console via LB port
   *
   * @throws Exception exception
   */
  @Test
  public void testDomainOnPvUsingWdt() throws Exception {
    Assume.assumeTrue(FULLTEST);
    String testMethodName = new Object() {}.getClass().getEnclosingMethod().getName();
    logTestBegin(testMethodName);
    LoggerHelper.getLocal().log(Level.INFO, "Creating Domain using DomainOnPVUsingWDT & verifing the domain creation");

    Domain domain = null;
    boolean testCompletedSuccessfully = false;
    try {
      // create domain
      Map<String, Object> domainMap = TestUtils.createDomainMap(getNewNumber(), testClassName);
      domainMap.put("namespace", domainNS1);
      domainMap.put("createDomainFilesDir", "wdt");
      domain = TestUtils.createDomain(domainMap);
      domain.verifyDomainCreated();
      testBasicUseCases(domain);
      testWldfScaling(operator1, domain);
      // TODO: Test Apache LB
      // domain.verifyAdminConsoleViaLB();
      testCompletedSuccessfully = true;
    } finally {
      if (domain != null && (JENKINS || testCompletedSuccessfully)) {
        LoggerHelper.getLocal().log(Level.INFO, "About to delete domain: " + domain.getDomainUid());
        TestUtils.deleteWeblogicDomainResources(domain.getDomainUid());
        TestUtils.verifyAfterDeletion(domain);
      }
    } 

    LoggerHelper.getLocal().log(Level.INFO, "SUCCESS - " + testMethodName);
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
   * @throws Exception exception
   */
  @Test
  public void testTwoDomainsManagedByTwoOperators() throws Exception {
    Assume.assumeTrue(FULLTEST);
    String testMethodName = new Object() {}.getClass().getEnclosingMethod().getName();
    logTestBegin(testMethodName);
    LoggerHelper.getLocal().log(Level.INFO, "Creating Domain domain1 & verifing the domain creation");

    Domain domain1 = null;
    Domain domain2 = null;
    boolean testCompletedSuccessfully = false;
    try {
      // load input yaml to map and add configOverrides
      Map<String, Object> wlstDomainMap = TestUtils.createDomainMap(getNewNumber(), testClassName);
      wlstDomainMap.put("namespace", domainNS1);
      wlstDomainMap.put("createDomainPyScript","integration-tests/src/test/resources/domain-home-on-pv/create-domain-custom-nap.py");
      domain1 = TestUtils.createDomain(wlstDomainMap);
      domain1.verifyDomainCreated();
      testBasicUseCases(domain1);
      /* LoggerHelper.getLocal().log(Level.INFO, "Checking if operator2 is running, if not creating");
      if (operator2 == null) {
        operator2 = TestUtils.createOperator(OPERATOR2_YAML);
      } */
      Map<String, Object> operatorMap = TestUtils.createOperatorMap(getNewNumber(), true, testClassName);
      Operator operator2 = TestUtils.createOperator(operatorMap, Operator.RestCertType.SELF_SIGNED);
      String domainNS2 = ((ArrayList<String>)operatorMap.get("domainNamespaces")).get(0);
      
      // create domain2 with configured cluster
      // ToDo: configured cluster support is removed from samples, modify the test to create
      // configured cluster
      Map<String, Object> wdtDomainMap = TestUtils.createDomainMap(getNewNumber(), testClassName);
      wdtDomainMap.put("namespace", domainNS2);
      wdtDomainMap.put("createDomainFilesDir", "wdt");
      domain2 = TestUtils.createDomain(wdtDomainMap);
      domain2.verifyDomainCreated();
      testBasicUseCases(domain2);
      LoggerHelper.getLocal().log(Level.INFO, "Verify the only remaining running domain domain1 is unaffected");
      domain1.verifyDomainCreated();

      testClusterScaling(operator2, domain2, false);

      LoggerHelper.getLocal().log(Level.INFO, "Verify the only remaining running domain domain1 is unaffected");
      domain1.verifyDomainCreated();

      LoggerHelper.getLocal().log(Level.INFO, "Destroy and create domain1 and verify no impact on domain2");
      domain1.destroy();
      domain1.create();

      LoggerHelper.getLocal().log(Level.INFO, "Verify no impact on domain2");
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
        LoggerHelper.getLocal().log(Level.INFO, "About to delete domains: " + domainUidsToBeDeleted);
        TestUtils.deleteWeblogicDomainResources(domainUidsToBeDeleted);
        if (domain1 != null) {
          TestUtils.verifyAfterDeletion(domain1);
        }
        if (domain2 != null) {
          TestUtils.verifyAfterDeletion(domain2);
        }
      }
    } 
    LoggerHelper.getLocal().log(Level.INFO, "SUCCESS - " + testMethodName);
  }


  /**
   * Create one operator if it is not running. Create domain domain1 and domain2 dynamic cluster in
   * default namespace, managed by operator1. Both domains share one PV. Verify scaling for domain2
   * cluster from 2 to 3 servers and back to 2, plus verify no impact on domain1. Cycle domain1 down
   * and back up, plus verify no impact on domain2. shutdown by the domains using the delete
   * resource script from samples.
   *
   * <p>ToDo: configured cluster support is removed from samples, modify the test to create
   *
   * @throws Exception exception
   */
  @Test
  public void testTwoDomainsManagedByOneOperatorSharingPV() throws Exception {
    Assume.assumeTrue(FULLTEST);
    String testMethodName = new Object() {}.getClass().getEnclosingMethod().getName();
    logTestBegin(testMethodName);
    LoggerHelper.getLocal().log(Level.INFO, "Creating Domain domain1 & verifing the domain creation");


    Domain domain1 = null;
    Domain domain2 = null;
    boolean testCompletedSuccessfully = false;
    try {
      // load input yaml to map and add configOverrides
      Map<String, Object> domain1Map = TestUtils.createDomainMap(getNewNumber(), testClassName);
      domain1Map.put("domainUID", "d1onpv");
      domain1Map.put("namespace", domainNS1);
      domain1Map.put("createDomainPyScript","integration-tests/src/test/resources/domain-home-on-pv/create-domain-custom-nap.py");
      domain1Map.put("pvSharing", new Boolean("true"));
      domain1 = TestUtils.createDomain(domain1Map);
      domain1.verifyDomainCreated();
      testBasicUseCases(domain1);

      Map<String, Object> domain2Map = TestUtils.createDomainMap(getNewNumber(), testClassName);
      domain2Map.put("domainUID", "d2onpv");
      domain1Map.put("namespace", domainNS1);
      domain1Map.put("createDomainPyScript","integration-tests/src/test/resources/domain-home-on-pv/create-domain-custom-nap.py");
      domain1Map.put("pvSharing", new Boolean("true"));
      domain2 = TestUtils.createDomain(domain2Map);
      domain2.verifyDomainCreated();
      testBasicUseCases(domain2);
      LoggerHelper.getLocal().log(Level.INFO, "Verify the only remaining running domain domain1 is unaffected");
      domain1.verifyDomainCreated();

      testClusterScaling(operator1, domain2, false);

      LoggerHelper.getLocal().log(Level.INFO, "Verify the only remaining running domain domain1 is unaffected");
      domain1.verifyDomainCreated();

      LoggerHelper.getLocal().log(Level.INFO, "Destroy and create domain1 and verify no impact on domain2");
      domain1.destroy();
      domain1.create();

      LoggerHelper.getLocal().log(Level.INFO, "Verify no impact on domain2");
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
        LoggerHelper.getLocal().log(Level.INFO, "About to delete domains: " + domainUidsToBeDeleted);
        TestUtils.deleteWeblogicDomainResources(domainUidsToBeDeleted);
        if (domain1 != null) {
          TestUtils.verifyAfterDeletion(domain1);
        }
        if (domain2 != null) {
          TestUtils.verifyAfterDeletion(domain2);
        }
      }
    } 
    LoggerHelper.getLocal().log(Level.INFO, "SUCCESS - " + testMethodName);
  }


  /**
   * Create operator if its not running and create domain with serverStartPolicy="ADMIN_ONLY".
   * Verify only admin server is created. Make domain configuration change and restart the domain.
   * shutdown by deleting domain CRD. Create domain on existing PV dir, pv is already populated by a
   * shutdown domain.
   *
   * @throws Exception exception
   */
  @Test
  public void testCreateDomainWithStartPolicyAdminOnly() throws Exception {
    Assume.assumeTrue(FULLTEST);
    String testMethodName = new Object() {}.getClass().getEnclosingMethod().getName();
    logTestBegin(testMethodName);
    LoggerHelper.getLocal().log(Level.INFO, "Creating Domain domain & verifing the domain creation");
    // create domain
    Domain domain = null;
    try {
      Map<String, Object> domainMap = TestUtils.createDomainMap(getNewNumber(), testClassName);
      domainMap.put("serverStartPolicy", "ADMIN_ONLY");
      domainMap.put("namespace", domainNS1);
      domain = TestUtils.createDomain(domainMap, false);
      domain.verifyDomainCreated();
      // change domain config by modifying accept backlog on adminserver tuning
      modifyDomainConfig(domain);
      domain.shutdownUsingServerStartPolicy();
      domain.restartUsingServerStartPolicy();
    } finally {
      if (domain != null) {
        // create domain on existing dir
        domain.destroy();
      }
    }

    domain.createDomainOnExistingDirectory(); 

    LoggerHelper.getLocal().log(Level.INFO, "SUCCESS - " + testMethodName);
  }

  /**
   * Create operator and create domain with pvReclaimPolicy="Recycle" Verify that the PV is deleted
   * once the domain and PVC are deleted.
   *
   * @throws Exception exception
   */
  @Test
  public void testCreateDomainPvReclaimPolicyRecycle() throws Exception {
    Assume.assumeTrue(FULLTEST);
    String testMethodName = new Object() {}.getClass().getEnclosingMethod().getName();
    logTestBegin(testMethodName);

    LoggerHelper.getLocal().log(Level.INFO, "Creating Domain domain & verifing the domain creation");
    // create domain
    Domain domain = null;

    try {
      Map<String, Object> domainMap = TestUtils.createDomainMap(getNewNumber(), testClassName);
      domainMap.put("weblogicDomainStorageReclaimPolicy", "Recycle");
      domainMap.put("clusterType", "CONFIGURED");
      domainMap.put("namespace", domainNS1);
      domain = TestUtils.createDomain(domainMap, false);

      domain.verifyDomainCreated();
    } finally {
      if (domain != null) domain.shutdown();
    }
    domain.deletePvcAndCheckPvReleased(); 
    LoggerHelper.getLocal().log(Level.INFO, "SUCCESS - " + testMethodName);
  }

  /**
   * Create operator and create domain with mostly default values from sample domain inputs, mainly
   * exposeAdminT3Channel and exposeAdminNodePort which are false by default and verify domain
   * startup and cluster scaling using operator rest endpoint works.
   *
   * <p>Also test samples/scripts/delete-domain/delete-weblogic-domain-resources.sh to delete domain
   * resources
   *
   * @throws Exception exception
   */
  @Test
  public void testCreateDomainWithDefaultValuesInSampleInputs() throws Exception {
    Assume.assumeTrue(FULLTEST);
    String testMethodName = new Object() {}.getClass().getEnclosingMethod().getName();
    logTestBegin(testMethodName);
    LoggerHelper.getLocal().log(Level.INFO, "Creating Domain domain10 & verifing the domain creation");

    // create domain10
    Domain domain = null;
    boolean testCompletedSuccessfully = false;
    try {
      Map<String, Object> domainMap = new HashMap<String, Object>();
      domainMap.put("domainUID", "domainsampledefaults");
      domainMap.put("configuredManagedServerCount", "4");
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

    LoggerHelper.getLocal().log(Level.INFO, "SUCCESS - " + testMethodName);
  }

  /**
   * Create operator and enable external rest endpoint using the externalOperatorCert and
   * externalOperatorKey defined in the helm chart values instead of the tls secret. This test is
   * for backward compatibility
   *
   * @throws Exception exception
   */
  @Test
  public void testOperatorRestIdentityBackwardCompatibility() throws Exception {
    Assume.assumeTrue(FULLTEST);
    String testMethodName = new Object() {}.getClass().getEnclosingMethod().getName();
    logTestBegin(testMethodName);
    LoggerHelper.getLocal().log(Level.INFO, "Creating operatorForBackwardCompatibility ");
    Map<String, Object> operatorMap = TestUtils.createOperatorMap(getNewNumber(), true, testClassName);
    Operator operatorForBackwardCompatibility = TestUtils.createOperator(operatorMap, Operator.RestCertType.LEGACY);
    operatorForBackwardCompatibility.verifyOperatorExternalRestEndpoint();
    LoggerHelper.getLocal().log(Level.INFO, "Operator using legacy REST identity created successfully");
    operatorForBackwardCompatibility.destroy(); 
    LoggerHelper.getLocal().log(Level.INFO, "SUCCESS - " + testMethodName);
  }

  /**
   * Create operator and enable external rest endpoint using a certificate chain. This test uses the
   * operator backward compatibility operator because that operator is destroyed.
   *
   * @throws Exception exception
   */
  @Test
  public void testOperatorRestUsingCertificateChain() throws Exception {
    Assume.assumeTrue(FULLTEST);

    logTestBegin("testOperatorRestUsingCertificateChain");
    LoggerHelper.getLocal().log(Level.INFO, "Creating operatorForBackwardCompatibility");
    Map<String, Object> operatorMap = TestUtils.createOperatorMap(getNewNumber(), true, testClassName);
    Operator  operatorForRESTCertChain = TestUtils.createOperator(operatorMap, RestCertType.CHAIN);
    operatorForRESTCertChain.verifyOperatorExternalRestEndpoint();
    operatorForRESTCertChain.destroy();
    LoggerHelper.getLocal().log(Level.INFO, "Operator using legacy REST identity created successfully"); 
    LoggerHelper.getLocal().log(Level.INFO, "SUCCESS - testOperatorRestUsingCertificateChain");
  }

  /**
   * Create Operator and create domain using domain-in-image option. Verify the domain is started
   * successfully and web application can be deployed and accessed.
   *
   * @throws Exception exception
   */
  @Test
  public void testDomainInImageUsingWlst() throws Exception {
    Assume.assumeTrue(FULLTEST);
    String testMethodName = new Object() {}.getClass().getEnclosingMethod().getName();
    logTestBegin(testMethodName);

    LoggerHelper.getLocal().log(Level.INFO, "Creating Domain & verifing the domain creation");
    // create domain
    Domain domain = null;
    boolean testCompletedSuccessfully = false;
    try {
      Map<String, Object> domainMap = TestUtils.createDomainInImageMap(getNewNumber(), false, testClassName);
      domainMap.put("namespace", domainNS1);
      domainMap.remove("clusterType");
      domain = TestUtils.createDomain(domainMap);
      domain.verifyDomainCreated();
      testBasicUseCases(domain);
      testClusterScaling(operator1, domain, false);
      testCompletedSuccessfully = true;
    } finally {
      if (domain != null && (JENKINS || testCompletedSuccessfully)) {
        TestUtils.deleteWeblogicDomainResources(domain.getDomainUid());
      }
    } 
    LoggerHelper.getLocal().log(Level.INFO, "SUCCESS - " + testMethodName);
  }

  /**
   * Create Operator and create domain using domain-in-image option. Verify the domain is started
   * successfully and web application can be deployed and accessed.
   *
   * @throws Exception exception
   */
  @Test
  public void testDomainInImageUsingWdt() throws Exception {
    Assume.assumeTrue(QUICKTEST);

    String testMethodName = new Object() {}.getClass().getEnclosingMethod().getName();
    logTestBegin(testMethodName);

    LoggerHelper.getLocal().log(Level.INFO, "Creating Domain & verifing the domain creation");
    // create domain
    Domain domain = null;
    boolean testCompletedSuccessfully = false;
    try {
      Map<String, Object> domainMap = TestUtils.createDomainInImageMap(getNewNumber(), true, testClassName);
      domainMap.put("namespace", domainNS1);
      domainMap.put(
          "customWdtTemplate",
          BaseTest.getProjectRoot()
              + "/integration-tests/src/test/resources/wdt/config.cluster.topology.yaml");
      domainMap.put("createDomainFilesDir", "wdt");
      domain = TestUtils.createDomain(domainMap);
      domain.verifyDomainCreated();

      testBasicUseCases(domain);
      testClusterScaling(operator1, domain, true);
      testCompletedSuccessfully = true;
    } finally {
      if (domain != null && (JENKINS || testCompletedSuccessfully)) {
        TestUtils.deleteWeblogicDomainResources(domain.getDomainUid());
        TestUtils.verifyAfterDeletion(domain);
      }
    } 
    LoggerHelper.getLocal().log(Level.INFO, "SUCCESS - " + testMethodName);
  }

  private Domain testAdvancedUseCasesForADomain(Operator operator, Domain domain) throws Exception {
    domain.enablePrecreateService();
    testClusterScaling(operator, domain, true);
    domain.verifyServicesCreated(true);
    if (FULLTEST) {
      testDomainLifecyle(operator, domain);
      testOperatorLifecycle(operator, domain);
    }    
    return domain;
  }

  private void modifyDomainConfig(Domain domain) throws Exception {
    String adminPod = domain.getDomainUid() + "-" + domain.getAdminServerName();
    String scriptsLocInPod = "/u01/oracle";
    TestUtils.copyFileViaCat(
        BaseTest.getProjectRoot() + "/integration-tests/src/test/resources/modifyAcceptBacklog.py",
        scriptsLocInPod + "/modifyAcceptBacklog.py",
        adminPod,
        domain.getDomainNs());

    TestUtils.copyFileViaCat(
        BaseTest.getProjectRoot() + "/integration-tests/src/test/resources/callpyscript.sh",
        scriptsLocInPod + "/callpyscript.sh",
        adminPod,
        domain.getDomainNs());
    String[] args = {
      scriptsLocInPod + "/modifyAcceptBacklog.py",
      BaseTest.getUsername(),
      BaseTest.getPassword(),
      "t3://" + adminPod + ":" + domain.getDomainMap().get("t3ChannelPort")
    };
    TestUtils.callShellScriptByExecToPod(
        adminPod, domain.getDomainNs(), scriptsLocInPod, "callpyscript.sh", args);
  }
  
}
