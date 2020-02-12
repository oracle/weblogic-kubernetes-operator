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

public class ItDomainInImage extends BaseTest {
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
      operator1 = TestUtils.createOperator(operatorMap, RestCertType.SELF_SIGNED);
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
   * Create Operator and create domain using domain-in-image option. Verify the domain is started
   * successfully and web application can be deployed and accessed.
   *
   * @throws Exception exception
   */
  @Test
  public void testDomainInImageUsingWlst() throws Exception {
    Assumptions.assumeTrue(FULLTEST);
    String testMethodName = new Object() {
    }.getClass().getEnclosingMethod().getName();
    logTestBegin(testMethodName);

    LoggerHelper.getLocal().log(Level.INFO, "Creating Domain & verifing the domain creation");
    // create domain
    Domain domain = null;
    boolean testCompletedSuccessfully = false;
    try {
      Map<String, Object> domainMap = createDomainInImageMap(
                getNewSuffixCount(), false, testClassName);
      domainMap.put("namespace", domainNS1);
      domainMap.remove("clusterType");
      domain = TestUtils.createDomain(domainMap);
      domain.verifyDomainCreated();
      testBasicUseCases(domain, true);
      testClusterScaling(operator1, domain, false);
      testCompletedSuccessfully = true;
    } finally {
      if (domain != null && (JENKINS || testCompletedSuccessfully)) {
        TestUtils.deleteWeblogicDomainResources(domain.getDomainUid());
      }
      if (domain != null) {
        domain.deleteImage();
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
    Assumptions.assumeTrue(QUICKTEST);

    String testMethodName = new Object() {
    }.getClass().getEnclosingMethod().getName();
    logTestBegin(testMethodName);

    LoggerHelper.getLocal().log(Level.INFO, "Creating Domain & verifing the domain creation");
    // create domain
    Domain domain = null;
    boolean testCompletedSuccessfully = false;
    try {
      Map<String, Object> domainMap = createDomainInImageMap(
                  getNewSuffixCount(), true, testClassName);
      domainMap.put("namespace", domainNS1);
      domainMap.put(
          "customWdtTemplate",
          BaseTest.getProjectRoot()
              + "/integration-tests/src/test/resources/wdt/config.cluster.topology.yaml");
      domainMap.put("createDomainFilesDir", "wdt");
      domain = TestUtils.createDomain(domainMap);
      domain.verifyDomainCreated();

      testBasicUseCases(domain, false);
      testClusterScaling(operator1, domain, true);
      testCompletedSuccessfully = true;
    } finally {
      // if (domain != null && (JENKINS || testCompletedSuccessfully)) {
      if (domain != null && testCompletedSuccessfully) {
        TestUtils.deleteWeblogicDomainResources(domain.getDomainUid());
        TestUtils.verifyAfterDeletion(domain);
      }
      if (domain != null) {
        domain.deleteImage();
      }
    }
    LoggerHelper.getLocal().log(Level.INFO, "SUCCESS - " + testMethodName);
  }

}
