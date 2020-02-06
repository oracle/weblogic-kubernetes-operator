// Copyright (c) 2018, 2020, Oracle Corporation and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;

import oracle.kubernetes.operator.utils.Domain;
import oracle.kubernetes.operator.utils.LoggerHelper;
import oracle.kubernetes.operator.utils.Operator;
import oracle.kubernetes.operator.utils.Operator.RestCertType;
import oracle.kubernetes.operator.utils.TestUtils;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.MethodOrderer.Alphanumeric;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

/**
 * Simple JUnit test file used for testing Operator.
 *
 * <p>This test is used for creating Operator(s) and multiple domains which are managed by the
 * Operator(s).
 */
@TestMethodOrder(Alphanumeric.class)
public class ItOperator extends BaseTest {
  private static Operator operator1;
  private static String domainNS1;
  private static String testClassName;
  private static StringBuffer namespaceList;

  /**
   * This method gets called only once before any of the test methods are executed. It does the
   * initialization of the integration test properties defined in OperatorIT.properties and setting
   * the resultRoot, pvRoot and projectRoot attributes.
   *
   * @throws Exception exception
   */
  @BeforeAll
  public static void staticPrepare() throws Exception {
    namespaceList = new StringBuffer();
    testClassName = new Object() {
    }.getClass().getEnclosingClass().getSimpleName();
    // initialize test properties and create the directories
    initialize(APP_PROPS_FILE, testClassName);
  }

  /**
   * This method gets called before every test. It creates the result/pv root directories
   * for the test. Creates the operator and domain if its not running.
   *
   * @throws Exception exception if result/pv/operator/domain creation fails
   */
  @BeforeEach
  public void prepare() throws Exception {

    createResultAndPvDirs(testClassName);

    // create operator1
    if (operator1 == null) {
      Map<String, Object> operatorMap = createOperatorMap(getNewSuffixCount(),
          true, testClassName);
      operator1 = TestUtils.createOperator(operatorMap, Operator.RestCertType.SELF_SIGNED);
      Assertions.assertNotNull(operator1);
      domainNS1 = ((ArrayList<String>) operatorMap.get("domainNamespaces")).get(0);
      namespaceList.append((String)operatorMap.get("namespace"));
      namespaceList.append(" ").append(domainNS1);
    }
  }

  /**
   * Releases k8s cluster lease, archives result, pv directories.
   *
   * @throws Exception exception
   */
  @AfterAll
  public static void staticUnPrepare() throws Exception {
    tearDown(new Object() {
    }.getClass().getEnclosingClass().getSimpleName(), namespaceList.toString());

    LoggerHelper.getLocal().info("SUCCESS");
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
    Assumptions.assumeTrue(QUICKTEST);
    String testMethodName = new Object() {
    }.getClass().getEnclosingMethod().getName();
    logTestBegin(testMethodName);
    LoggerHelper.getLocal().log(Level.INFO,
        "Creating Operator & waiting for the script to complete execution");
    Domain domain = null;
    boolean testCompletedSuccessfully = false;
    try {
      Map<String, Object> domainMap =
          createDomainMap(getNewSuffixCount(), testClassName);
      domainMap.put("namespace", domainNS1);
      domainMap.put("createDomainPyScript",
          "integration-tests/src/test/resources/domain-home-on-pv/create-domain-custom-nap.py");
      domain = TestUtils.createDomain(domainMap);
      domain.verifyDomainCreated();
      testBasicUseCases(domain, true);
      TestUtils.renewK8sClusterLease(getProjectRoot(), getLeaseId());
      testAdvancedUseCasesForADomain(operator1, domain);
      domain.testWlsLivenessProbe();
      testCompletedSuccessfully = true;
    } finally {
      if (domain != null && (JENKINS || testCompletedSuccessfully)) {
        TestUtils.deleteWeblogicDomainResources(domain.getDomainUid());
      }
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
    Assumptions.assumeTrue(FULLTEST);
    String testMethodName = new Object() {
    }.getClass().getEnclosingMethod().getName();
    logTestBegin(testMethodName);
    LoggerHelper.getLocal().log(Level.INFO,
        "Creating Domain using DomainOnPVUsingWDT & verifing the domain creation");

    Domain domain = null;
    Operator operator = null;
    boolean testCompletedSuccessfully = false;
    try {
      //create operator just for this test to match the namespaces with wldf-policy.yaml
      Map<String, Object> operatorMap = createOperatorMap(getNewSuffixCount(),
          true, testClassName);
      ArrayList<String> targetDomainsNS = new ArrayList<String>();
      targetDomainsNS.add("test2");
      operatorMap.put("domainNamespaces", targetDomainsNS);
      operatorMap.put("namespace", "weblogic-operator2");
      operator = TestUtils.createOperator(operatorMap, Operator.RestCertType.SELF_SIGNED);
      namespaceList.append(" ").append((String)operatorMap.get("namespace"));

      // create domain
      Map<String, Object> domainMap = createDomainMap(getNewSuffixCount(), testClassName);
      domainMap.put("namespace", "test2");
      domainMap.put("createDomainFilesDir", "wdt");
      domainMap.put("domainUID", "domainonpvwdt");
      domain = TestUtils.createDomain(domainMap);
      domain.verifyDomainCreated();
      testBasicUseCases(domain, false);
      testWldfScaling(operator, domain);
      namespaceList.append(" ").append(domainMap.get("namespace"));
      // TODO: Test Apache LB
      // domain.verifyAdminConsoleViaLB();
      testCompletedSuccessfully = true;
    } finally {
      // if (domain != null && (JENKINS || testCompletedSuccessfully)) {
      if (domain != null && testCompletedSuccessfully) {
        LoggerHelper.getLocal().log(Level.INFO, "About to delete domain: " + domain.getDomainUid());
        TestUtils.deleteWeblogicDomainResources(domain.getDomainUid());
        TestUtils.verifyAfterDeletion(domain);
      }
      // if (operator != null && (JENKINS || testCompletedSuccessfully)) {
      if (operator != null && testCompletedSuccessfully) {
        operator.destroy();
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
    Assumptions.assumeTrue(FULLTEST);
    String testMethodName = new Object() {
    }.getClass().getEnclosingMethod().getName();
    logTestBegin(testMethodName);
    LoggerHelper.getLocal().log(Level.INFO,
        "Creating Domain domain & verifing the domain creation");
    // create domain
    Domain domain = null;
    try {
      Map<String, Object> domainMap = createDomainMap(
                        getNewSuffixCount(), testClassName);
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
    Assumptions.assumeTrue(FULLTEST);
    String testMethodName = new Object() {
    }.getClass().getEnclosingMethod().getName();
    logTestBegin(testMethodName);

    LoggerHelper.getLocal().log(Level.INFO,
        "Creating Domain domain & verifing the domain creation");
    // create domain
    Domain domain = null;

    try {
      Map<String, Object> domainMap = createDomainMap(
                  getNewSuffixCount(), testClassName);
      domainMap.put("weblogicDomainStorageReclaimPolicy", "Recycle");
      domainMap.put("clusterType", "CONFIGURED");
      domainMap.put("namespace", domainNS1);

      domain = TestUtils.createDomain(domainMap, false);

      domain.verifyDomainCreated();
    } finally {
      if (domain != null) {
        domain.shutdown();
      }
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
    Assumptions.assumeTrue(FULLTEST);
    String testMethodName = new Object() {
    }.getClass().getEnclosingMethod().getName();
    logTestBegin(testMethodName);
    LoggerHelper.getLocal().log(Level.INFO,
        "Creating Domain domain10 & verifing the domain creation");

    // create domain10
    Domain domain = null;
    boolean testCompletedSuccessfully = false;
    try {
      Map<String, Object> domainMap = new HashMap<String, Object>();
      domainMap.put("domainUID", "domainsampledefaults");
      domainMap.put("namespace", domainNS1);
      domainMap.put("resultDir", getResultDir());
      domainMap.put("userProjectsDir", getUserProjectsDir());
      domainMap.put("pvRoot", getPvRoot());

      domain = TestUtils.createDomain(domainMap);
      domain.verifyDomainCreated();
      testBasicUseCases(domain, false);
      // testAdvancedUseCasesForADomain(operator1, domain10);
      testCompletedSuccessfully = true;
    } finally {
      if (domain != null && (JENKINS || testCompletedSuccessfully)) {
        TestUtils.deleteWeblogicDomainResources(domain.getDomainUid());
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
    Assumptions.assumeTrue(FULLTEST);
    String testMethodName = new Object() {
    }.getClass().getEnclosingMethod().getName();
    logTestBegin(testMethodName);
    LoggerHelper.getLocal().log(Level.INFO, "Creating operatorForBackwardCompatibility ");
    Map<String, Object> operatorMap = createOperatorMap(getNewSuffixCount(),
                  true, testClassName);
    Operator operatorForBackwardCompatibility =
        TestUtils.createOperator(operatorMap, Operator.RestCertType.LEGACY);
    operatorForBackwardCompatibility.verifyOperatorExternalRestEndpoint();
    LoggerHelper.getLocal().log(Level.INFO,
        "Operator using legacy REST identity created successfully");
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
    Assumptions.assumeTrue(FULLTEST);

    logTestBegin("testOperatorRestUsingCertificateChain");
    LoggerHelper.getLocal().log(Level.INFO, "Creating operatorForBackwardCompatibility");
    Map<String, Object> operatorMap = createOperatorMap(getNewSuffixCount(),
              true, testClassName);
    Operator operatorForRestCertChain =
        TestUtils.createOperator(operatorMap, RestCertType.CHAIN);
    operatorForRestCertChain.verifyOperatorExternalRestEndpoint();
    operatorForRestCertChain.destroy();
    LoggerHelper.getLocal().log(Level.INFO,
        "Operator using legacy REST identity created successfully");
    LoggerHelper.getLocal().log(Level.INFO, "SUCCESS - testOperatorRestUsingCertificateChain");
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
