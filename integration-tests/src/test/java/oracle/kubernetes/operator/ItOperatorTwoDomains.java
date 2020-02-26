// Copyright (c) 2018, 2020, Oracle Corporation and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator;

import java.util.ArrayList;
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
import org.junit.jupiter.api.Test;


/**
 * Simple JUnit test file used for testing Operator.
 *
 * <p>This test is used for creating Operator(s) and multiple domains which are managed by the
 * Operator(s).
 */

public class ItOperatorTwoDomains extends BaseTest {
  private static Operator operator1;
  private static String domainNS1;
  private static String testClassName;
  private static String testClassNameShort;
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
    if (FULLTEST) {
      namespaceList = new StringBuffer();
      testClassName = new Object() {
      }.getClass().getEnclosingClass().getSimpleName();
      // initialize test properties and create the directories
      initialize(APP_PROPS_FILE, testClassName);
    }
  }

  /**
   * This method gets called before every test. It creates the result/pv root directories
   * for the test. Creates the operator and domain if its not running.
   *
   * @throws Exception exception if result/pv/operator/domain creation fails
   */
  @BeforeEach
  public void prepare() throws Exception {
    if (FULLTEST) {
      createResultAndPvDirs(testClassName);
      testClassNameShort = "twooptwodomain";
      // create operator1
      if (operator1 == null) {
        Map<String, Object> operatorMap = createOperatorMap(getNewSuffixCount(),
            true, testClassNameShort);
        operator1 = TestUtils.createOperator(operatorMap, RestCertType.SELF_SIGNED);
        Assertions.assertNotNull(operator1);
        domainNS1 = ((ArrayList<String>) operatorMap.get("domainNamespaces")).get(0);
        namespaceList.append((String) operatorMap.get("namespace"));
        namespaceList.append(" ").append(domainNS1);
      }
    }
  }

  /**
   * Releases k8s cluster lease, archives result, pv directories.
   *
   * @throws Exception exception
   */
  @AfterAll
  public static void staticUnPrepare() throws Exception {
    if (FULLTEST) {
      tearDown(new Object() {
      }.getClass().getEnclosingClass().getSimpleName(), namespaceList.toString());

      LoggerHelper.getLocal().info("SUCCESS");
    }
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
    Assumptions.assumeTrue(FULLTEST);
    String testMethodName = new Object() {
    }.getClass().getEnclosingMethod().getName();
    logTestBegin(testMethodName);
    LoggerHelper.getLocal().log(Level.INFO,
        "Creating Domain domain1 & verifing the domain creation");

    Domain domain1 = null;
    Domain domain2 = null;
    boolean testCompletedSuccessfully = false;
    try {
      // load input yaml to map and add configOverrides
      Map<String, Object> wlstDomainMap =
          createDomainMap(getNewSuffixCount(), testClassNameShort);
      wlstDomainMap.put("namespace", domainNS1);
      wlstDomainMap.put("createDomainPyScript",
          "integration-tests/src/test/resources/domain-home-on-pv/create-domain-custom-nap.py");
      domain1 = TestUtils.createDomain(wlstDomainMap);
      domain1.verifyDomainCreated();
      testBasicUseCases(domain1, false);
      Map<String, Object> operatorMap = createOperatorMap(getNewSuffixCount(),
                          true, testClassNameShort);
      final Operator operator2 =
          TestUtils.createOperator(operatorMap, RestCertType.SELF_SIGNED);
      String domainNS2 = ((ArrayList<String>) operatorMap.get("domainNamespaces")).get(0);
      namespaceList.append(" ").append(operatorMap.get("namespace"));
      // create domain2 with configured cluster
      // ToDo: configured cluster support is removed from samples, modify the test to create
      // configured cluster
      Map<String, Object> wdtDomainMap =
          createDomainMap(getNewSuffixCount(), testClassNameShort);
      wdtDomainMap.put("namespace", domainNS2);
      wdtDomainMap.put("createDomainFilesDir", "wdt");
      domain2 = TestUtils.createDomain(wdtDomainMap);
      namespaceList.append(" ").append(wdtDomainMap.get("namespace"));
      domain2.verifyDomainCreated();
      testBasicUseCases(domain2, false);
      LoggerHelper.getLocal().log(Level.INFO,
          "Verify the only remaining running domain domain1 is unaffected");
      domain1.verifyDomainCreated();

      testClusterScaling(operator2, domain2, false);

      LoggerHelper.getLocal().log(Level.INFO,
          "Verify the only remaining running domain domain1 is unaffected");
      domain1.verifyDomainCreated();

      LoggerHelper.getLocal().log(Level.INFO,
          "Destroy and create domain1 and verify no impact on domain2");
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
        LoggerHelper.getLocal().log(Level.INFO,
            "About to delete domains: " + domainUidsToBeDeleted);
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
    Assumptions.assumeTrue(FULLTEST);
    String testMethodName = new Object() {
    }.getClass().getEnclosingMethod().getName();
    logTestBegin(testMethodName);
    LoggerHelper.getLocal().log(Level.INFO,
        "Creating Domain domain1 & verifing the domain creation");


    Domain domain1 = null;
    Domain domain2 = null;
    boolean testCompletedSuccessfully = false;
    try {
      // load input yaml to map and add configOverrides
      Map<String, Object> domain1Map =
          createDomainMap(getNewSuffixCount(), testClassNameShort);
      domain1Map.put("domainUID", "d1onpv");
      domain1Map.put("namespace", domainNS1);
      domain1Map.put("createDomainPyScript",
          "integration-tests/src/test/resources/domain-home-on-pv/create-domain-custom-nap.py");
      domain1Map.put("pvSharing", new Boolean("true"));
      domain1 = TestUtils.createDomain(domain1Map);
      domain1.verifyDomainCreated();
      testBasicUseCases(domain1, false);

      Map<String, Object> domain2Map = createDomainMap(
          getNewSuffixCount(), testClassNameShort);
      domain2Map.put("domainUID", "d2onpv");
      domain2Map.put("namespace", domainNS1);
      domain2Map.put("createDomainPyScript",
          "integration-tests/src/test/resources/domain-home-on-pv/create-domain-custom-nap.py");
      domain2Map.put("pvSharing", new Boolean("true"));
      domain2 = TestUtils.createDomain(domain2Map);
      domain2.verifyDomainCreated();
      testBasicUseCases(domain2, false);
      LoggerHelper.getLocal().log(Level.INFO,
          "Verify the only remaining running domain domain1 is unaffected");
      domain1.verifyDomainCreated();

      testClusterScaling(operator1, domain2, false);

      LoggerHelper.getLocal().log(Level.INFO,
          "Verify the only remaining running domain domain1 is unaffected");
      domain1.verifyDomainCreated();

      LoggerHelper.getLocal().log(Level.INFO,
          "Destroy and create domain1 and verify no impact on domain2");
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
        LoggerHelper.getLocal().log(Level.INFO,
            "About to delete domains: " + domainUidsToBeDeleted);
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
}
