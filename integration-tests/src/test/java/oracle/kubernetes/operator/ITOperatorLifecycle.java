// Copyright 2018, Oracle Corporation and/or its affiliates.  All rights reserved.
// Licensed under the Universal Permissive License v 1.0 as shown at
// http://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator;

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
public class ITOperatorLifecycle extends BaseTest {

  // property file used to customize operator properties for operator inputs yaml

  private static String operator1File = "operator1.yaml";
  private static String operator2File = "operator2.yaml";
  private static final String operator_bcFile = "operator_bc.yaml";
  private static final String operator_chainFile = "operator_chain.yaml";

  // file used to customize domain properties for domain, PV and LB inputs yaml
  private static String domainonpvwlstFile = "domainonpvwlst.yaml";
  private static String domainonpvwdtFile = "domainonpvwdt.yaml";
  private static String domainadminonlyFile = "domainadminonly.yaml";
  private static String domainrecyclepolicyFile = "domainrecyclepolicy.yaml";
  private static String domainsampledefaultsFile = "domainsampledefaults.yaml";
  private static String domaininimagewlstFile = "domaininimagewlst.yaml";
  private static String domaininimagewdtFile = "domaininimagewdt.yaml";

  // property file used to configure constants for integration tests
  private static String appPropsFile = "OperatorIT.properties";

  private static Operator operator1, operator2;

  private static Operator operatorForBackwardCompatibility;
  private static Operator operatorForRESTCertChain;

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
   * Create Operator1 and domainOnPVUsingWLST with admin server and 1 managed server if they are not
   * running. After verifying the domain is created properly. Change some properties on domain
   * resources that would cause servers to be restarted and verify that server are indeed restarted.
   * The properties tested here are: env: "-Dweblogic.StdoutDebugEnabled=false"-->
   * "-Dweblogic.StdoutDebugEnabled=false" logHomeEnabled: true --> logHomeEnabled: false
   * includeServerOutInPodLog: true --> includeServerOutInPodLog: false
   * imagePullPolicy: IfNotPresent -->  imagePullPolicy: Never
   *
   * @throws Exception
   */
  @Test
  public void testServerRestartByDomainOnPVUsingWLST() throws Exception {
    Assume.assumeFalse(QUICKTEST);
    String testMethodName = new Object() {}.getClass().getEnclosingMethod().getName();
    logTestBegin(testMethodName);

    logger.info("Checking if operator1 and domain are running, if not creating");
    if (operator1 == null) {
      operator1 = TestUtils.createOperator(operator1File);
    }

    Domain domain = null;
    boolean testCompletedSuccessfully = false;
    try {
      // load input yaml to map and add configOverrides
      Map<String, Object> domainMap = TestUtils.loadYaml(domainonpvwlstFile);
      domainMap.put("domainUID", "domainlifecycle");
      domainMap.put("adminNodePort", new Integer("30707"));
      domainMap.put("t3ChannelPort", new Integer("30081"));
      domainMap.put("initialManagedServerReplicas", new Integer("1"));

      logger.info("Creating Domain domain& verifing the domain creation");
      domain = TestUtils.createDomain(domainMap);
      domain.verifyDomainCreated();
      // TODO add some comments
      logger.info(
          "About to testDomainServerRestart for Domain: "
              + domainMap.get("domainUID")
              + "  env property: StdoutDebugEnabled=false to StdoutDebugEnabled=true");
      domain.testDomainServerRestart(
          "\"-Dweblogic.StdoutDebugEnabled=false\"", "\"-Dweblogic.StdoutDebugEnabled=true\"");
      logger.info(
          "About to testDomainServerRestart for Domain: "
              + domainMap.get("domainUID")
              + "  logHomeEnabled: true -->  logHomeEnabled: false");
      domain.testDomainServerRestart("logHomeEnabled: true", "logHomeEnabled: false");
      logger.info(
          "About to testDomainServerRestart for Domain: "
              + domainMap.get("domainUID")
              + " imagePullPolicy: IfNotPresent -->  imagePullPolicy: Never ");
      domain.testDomainServerRestart(" imagePullPolicy: IfNotPresent", "imagePullPolicy: Never ");
      logger.info(
          "About to testDomainServerRestart for Domain: "
              + domainMap.get("domainUID")
              + "  includeServerOutInPodLog: true -->  includeServerOutInPodLog: false");
      domain.testDomainServerRestart(
          "includeServerOutInPodLog: true", "includeServerOutInPodLog: false");
    } finally {
      String domainUidsToBeDeleted = "";

      if (domain != null && (JENKINS || testCompletedSuccessfully)) {
        domainUidsToBeDeleted = domain.getDomainUid();
      }

       if (!domainUidsToBeDeleted.equals("")) {
        logger.info("About to delete domains: " + domainUidsToBeDeleted);
        TestUtils.deleteWeblogicDomainResources(domainUidsToBeDeleted);
        TestUtils.verifyAfterDeletion(domain);
      }
    }
    logger.info("SUCCESS - " + testMethodName);
  }
}
