// Copyright 2018, Oracle Corporation and/or its affiliates.  All rights reserved.
// Licensed under the Universal Permissive License v 1.0 as shown at
// http://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator;

import java.util.Map;
import oracle.kubernetes.operator.utils.Domain;
import oracle.kubernetes.operator.utils.Operator;
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
 * <p>This test is used for testing pods being restarted by some properties change.
 */
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
public class ITPodsRestart extends BaseTest {

  // property file used to customize operator properties for operator inputs yaml
	private static String operator1File = "operator1.yaml";

  // file used to customize domain properties for domain, PV and LB inputs yaml
  private static Domain domain = null;
  private static String domainonpvwlstFile = "domainonpvwlst.yaml";

  // property file used to configure constants for integration tests
  private static String appPropsFile = "OperatorIT.properties";

  private static Operator operator1;

  private static boolean QUICKTEST;
  private static boolean SMOKETEST;
  private static boolean JENKINS;
  private static boolean INGRESSPERDOMAIN = true;

  // Set QUICKTEST env var to true to run a small subset of tests.
  // Set SMOKETEST env var to true to run an even smaller subset
  // of tests, plus leave domain1 up and running when the test completes.
  // set INGRESSPERDOMAIN to false to create LB's ingress by kubectl yaml file
  static {
    QUICKTEST =
        System.getenv("QUICKTEST") != null && System.getenv("QUICKTEST").equalsIgnoreCase("true");
    SMOKETEST =
        System.getenv("SMOKETEST") != null && System.getenv("SMOKETEST").equalsIgnoreCase("true");
    if (SMOKETEST) QUICKTEST = true;
    if (System.getenv("JENKINS") != null) {
      JENKINS = new Boolean(System.getenv("JENKINS")).booleanValue();
    }
    if (System.getenv("INGRESSPERDOMAIN") != null) {
      INGRESSPERDOMAIN = new Boolean(System.getenv("INGRESSPERDOMAIN")).booleanValue();
    }
  }

  /**
   * This method gets called only once before any of the test methods are executed. It does the
   * initialization of the integration test properties defined in OperatorIT.properties and setting
   * the resultRoot, pvRoot and projectRoot attributes. Create Operator1 and domainOnPVUsingWLST
   * with admin server and 1 managed server if they are not running
   *
   * @throws Exception
   */
  @BeforeClass
  public static void staticPrepare() throws Exception {
    // initialize test properties and create the directories
    if (!QUICKTEST) {
      initialize(appPropsFile);

      logger.info("Checking if operator1 and domain are running, if not creating");
      if (operator1 == null) {
        operator1 = TestUtils.createOperator(operator1File);
      }

      domain = createPodsRestartdomain();
      Assert.assertNotNull(domain);
    }
  }

  /**
   * Releases k8s cluster lease, archives result, pv directories
   *
   * @throws Exception
   */
  @AfterClass
  public static void staticUnPrepare() throws Exception {
    if (!QUICKTEST) {
      logger.info("+++++++++++++++++++++++++++++++++---------------------------------+");
      logger.info("BEGIN");
      logger.info("Run once, release cluster lease");

      // destroyPodsRestartdomain();
      // tearDown();

      if (!"".equals(getLeaseId())) {
        logger.info("Release the k8s cluster lease");
        TestUtils.releaseLease(getProjectRoot(), getLeaseId());
      }

      logger.info("SUCCESS");
    }
  }

  /**
   * The properties tested is: env: "-Dweblogic.StdoutDebugEnabled=false"-->
   * "-Dweblogic.StdoutDebugEnabled=true"
   *
   * @throws Exception
   */
  @Test
  public void testServerRestartByChangingEnvProperty() throws Exception {
    Assume.assumeFalse(QUICKTEST);
    String testMethodName = new Object() {}.getClass().getEnclosingMethod().getName();
    logTestBegin(testMethodName);

    boolean testCompletedSuccessfully = false;
    logger.info(
        "About to testDomainServerRestart for Domain: "
            + domain.getDomainUid()
            + "  env property: StdoutDebugEnabled=false to StdoutDebugEnabled=true");
    domain.testDomainServerRestart(
        "\"-Dweblogic.StdoutDebugEnabled=false\"", "\"-Dweblogic.StdoutDebugEnabled=true\"");

    logger.info("SUCCESS - " + testMethodName);
  }

  /**
   * The properties tested is: logHomeEnabled: true --> logHomeEnabled: false
   *
   * @throws Exception
   */
  @Test
  public void testServerRestartByChangingLogHomeEnabled() throws Exception {
    Assume.assumeFalse(QUICKTEST);
    String testMethodName = new Object() {}.getClass().getEnclosingMethod().getName();
    logTestBegin(testMethodName);

    boolean testCompletedSuccessfully = false;

    logger.info(
        "About to testDomainServerRestart for Domain: "
            + domain.getDomainUid()
            + "  logHomeEnabled: true -->  logHomeEnabled: false");
    domain.testDomainServerRestart("logHomeEnabled: true", "logHomeEnabled: false");

    logger.info("SUCCESS - " + testMethodName);
  }

  /**
   * The properties tested is: imagePullPolicy: IfNotPresent --> imagePullPolicy: Never
   *
   * @throws Exception
   */
  @Test
  public void testServerRestartByChangingImagePullPolicy() throws Exception {
    Assume.assumeFalse(QUICKTEST);
    String testMethodName = new Object() {}.getClass().getEnclosingMethod().getName();
    logTestBegin(testMethodName);

    boolean testCompletedSuccessfully = false;

    logger.info(
        "About to testDomainServerRestart for Domain: "
            + domain.getDomainUid()
            + " imagePullPolicy: IfNotPresent -->  imagePullPolicy: Never ");
    domain.testDomainServerRestart(
        "imagePullPolicy: \"IfNotPresent\"", "imagePullPolicy: \"Never\" ");

    logger.info("SUCCESS - " + testMethodName);
  }

  /**
   * The properties tested is: includeServerOutInPodLog: true --> includeServerOutInPodLog: false
   *
   * @throws Exception
   */
  @Test
  public void testServerRestartByChangingIncludeServerOutInPodLog() throws Exception {
    Assume.assumeFalse(QUICKTEST);
    String testMethodName = new Object() {}.getClass().getEnclosingMethod().getName();
    logTestBegin(testMethodName);

    boolean testCompletedSuccessfully = false;

    logger.info(
        "About to testDomainServerRestart for Domain: "
            + domain.getDomainUid()
            + "  includeServerOutInPodLog: true -->  includeServerOutInPodLog: false");
    domain.testDomainServerRestart(
        "includeServerOutInPodLog: true", "includeServerOutInPodLog: false");

    logger.info("SUCCESS - " + testMethodName);
  }

  /**
   * The properties tested is: image: "store/oracle/weblogic:12.2.1.3" --> image:
   * "store/oracle/weblogic:duplicate"
   *
   * @throws Exception
   */
  @Test
  public void testServerRestartByChangingImage() throws Exception {
    Assume.assumeFalse(QUICKTEST);
    String testMethodName = new Object() {}.getClass().getEnclosingMethod().getName();
    logTestBegin(testMethodName);

    boolean testCompletedSuccessfully = false;

    logger.info(
        "About to testDomainServerRestart for Domain: "
            + domain.getDomainUid()
            + "  Image property: store/oracle/weblogic:12.2.1.3 to store/oracle/weblogic:duplicate");
    TestUtils.dockerTagImage("store/oracle/weblogic:12.2.1.3", "store/oracle/weblogic:duplicate");
    domain.testDomainServerRestart(
        "\"store/oracle/weblogic:12.2.1.3\"", "\"store/oracle/weblogic:duplicate\"");
    TestUtils.dockerRemoveImage("store/oracle/weblogic:duplicate");

    logger.info("SUCCESS - " + testMethodName);
  }

  private static Domain createPodsRestartdomain() throws Exception {

    Map<String, Object> domainMap = TestUtils.loadYaml(domainonpvwlstFile);
    domainMap.put("domainUID", "domainpodsrestart");
    domainMap.put("adminNodePort", new Integer("30707"));
    domainMap.put("t3ChannelPort", new Integer("30081"));
    domainMap.put("initialManagedServerReplicas", new Integer("1"));

    logger.info("Creating Domain domain& verifing the domain creation");
    domain = TestUtils.createDomain(domainMap);
    domain.verifyDomainCreated();

    return domain;
  }

  private static void destroyPodsRestartdomain() throws Exception {
    if (domain != null) {
      domain.destroy();
    }
  }
}
