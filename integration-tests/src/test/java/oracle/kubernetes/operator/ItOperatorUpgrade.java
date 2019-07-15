// Copyright 2019, Oracle Corporation and/or its affiliates.  All rights reserved.
// Licensed under the Universal Permissive License v 1.0 as shown at
// http://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;

import oracle.kubernetes.operator.utils.Domain;
import oracle.kubernetes.operator.utils.ExecResult;
import oracle.kubernetes.operator.utils.Operator;
import oracle.kubernetes.operator.utils.TestUtils;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Assume;
import org.junit.FixMethodOrder;
import org.junit.Test;
import org.junit.runners.MethodSorters;

/** Operator upgrade JUnit test file testing the operator upgrade from older releases to develop. */
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
public class ItOperatorUpgrade extends BaseTest {

  private static final String OP_BASE_REL = "2.0";
  private static final String OP_TARGET_RELEASE = "weblogic-kubernetes-operator:latest";
  private static String OP_NS = "";
  private static String OP_DEP_NAME = "";
  private static String OP_SA = "";
  private static String DOM_NS = "";
  private static String DUID = "";
  private static String opUpgradeTmpDir;
  private Domain domain = null;
  private static Operator operator;

  /**
   * Creates operator based on operatorRelease passed to it and then creates a WebLogic domain
   * controlled by that operator.
   *
   * @param operatorGitRelease Git branch name of the operator release version
   * @param operatorRelease Operator release version from the
   *     https://hub.docker.com/r/oracle/weblogic-kubernetes-operator/tags
   * @throws Exception when operator or domain creation fails
   */
  private void setupOperatorAndDomain(String operatorGitRelease, String operatorRelease)
      throws Exception {
    logger.log(Level.INFO, "+++++++++++++++Beginning Test Setup+++++++++++++++++++++");
    initialize(APP_PROPS_FILE);
    opUpgradeTmpDir = BaseTest.getResultDir() + "/operatorupgrade";
    TestUtils.exec("rm -rf " + Paths.get(opUpgradeTmpDir).toString());
    Files.createDirectories(Paths.get(opUpgradeTmpDir));
    Map<String, Object> operatorMap = TestUtils.loadYaml(OPERATOR1_YAML);
    operatorMap.put("operatorImageName", "oracle/weblogic-kubernetes-operator");
    operatorMap.put("operatorImageTag", operatorRelease);
    operatorMap.put("operatorGitVersion", operatorGitRelease);
    operatorMap.put("operatorGitVersionDir", opUpgradeTmpDir);
    operatorMap.put("namespace", OP_NS);
    operatorMap.put("releaseName", OP_DEP_NAME);
    operatorMap.put("serviceAccount", OP_SA);
    List<String> domNs = new ArrayList<String>();
    domNs.add(DOM_NS);
    operatorMap.put("domainNamespaces", domNs);
    operator = TestUtils.createOperator(operatorMap, Operator.RestCertType.LEGACY);
    TestUtils.exec("kubectl get all --all-namespaces", true);

    Map<String, Object> wlstDomainMap = TestUtils.loadYaml(DOMAININIMAGE_WLST_YAML);
    wlstDomainMap.put("domainUID", DUID);
    wlstDomainMap.put("namespace", DOM_NS);
    wlstDomainMap.put("projectRoot", opUpgradeTmpDir + "/weblogic-kubernetes-operator");
    domain = TestUtils.createDomain(wlstDomainMap);
    TestUtils.exec("kubectl get all --all-namespaces", true);
    domain.verifyPodsCreated();
    domain.verifyServicesCreated();
    domain.verifyServersReady();
    logger.log(Level.INFO, "+++++++++++++++Ending Test Setup+++++++++++++++++++++");
  }

  /**
   * cleanup the domain and operator after every test.
   *
   * @throws Exception when domain and operator cleanup fails
   */
  @After
  public void cleanupOperatorAndDomain() throws Exception {
    if (!QUICKTEST) {
      logger.log(Level.INFO, "+++++++++++++++Beginning AfterTest cleanup+++++++++++++++++++++");
      if (domain != null) {
        domain.destroy();
      }
      if (operator != null) {
        operator.destroy();
      }
      ExecResult result = cleanup();
      logger.log(Level.INFO, "+++++++++++++++Done AfterTest cleanup+++++++++++++++++++++");
    }
  }

  /**
   * Releases k8s cluster lease, archives result, pv directories.
   *
   * @throws Exception when deleting pv directories or other tearDown tasks fail.
   */
  @AfterClass
  public static void staticUnPrepare() throws Exception {
    if (!QUICKTEST) {
      logger.info("+++++++++++++++++++++++++++++++++---------------------------------+");
      logger.info("BEGIN");
      logger.info("Run once, release cluster lease");
      tearDown(new Object() {}.getClass().getEnclosingClass().getSimpleName());
      logger.info("SUCCESS");
    }
  }

  /**
   * Test for upgrading Operator from release 2.0 to develop branch.
   *
   * @throws Exception when upgrade fails
   */
  @Test
  public void testOperatorUpgradeFrom2_0() throws Exception {
    Assume.assumeFalse(QUICKTEST);
    String testMethod = new Object() {}.getClass().getEnclosingMethod().getName();
    logTestBegin(testMethod);
    OP_NS = "weblogic-operator20";
    OP_DEP_NAME = "operator-upgrade20";
    OP_SA = "operator-sa20";
    DOM_NS = "weblogic-domain20";
    DUID = "operatordomain20";
    setupOperatorAndDomain("2.0", "2.0");
    upgradeOperator(true);
    logger.info("SUCCESS - " + testMethod);
  }

  /**
   * Test for upgrading Operator from release 2.0.1 to develop branch.
   *
   * @throws Exception when upgrade fails
   */
  @Test
  public void testOperatorUpgradeFrom2_0_1() throws Exception {
    Assume.assumeFalse(QUICKTEST);
    String testMethod = new Object() {}.getClass().getEnclosingMethod().getName();
    logTestBegin(testMethod);
    OP_NS = "weblogic-operator201";
    OP_DEP_NAME = "operator-upgrade201";
    OP_SA = "operator-sa201";
    DOM_NS = "weblogic-domain201";
    DUID = "operatordomain201";
    setupOperatorAndDomain("release/2.0.1", "2.0.1");
    upgradeOperator(true);
    logger.info("SUCCESS - " + testMethod);
  }

  /**
   * Test for upgrading Operator from release 2.1 to develop branch.
   *
   * @throws Exception when upgrade fails
   */
  @Test
  public void testOperatorUpgradeFrom2_1() throws Exception {
    Assume.assumeFalse(QUICKTEST);
    String testMethod = new Object() {}.getClass().getEnclosingMethod().getName();
    logTestBegin(testMethod);
    OP_NS = "weblogic-operator21";
    OP_DEP_NAME = "operator-upgrade21";
    OP_SA = "operator-sa21";
    DOM_NS = "weblogic-domain21";
    DUID = "operatordomain21";
    setupOperatorAndDomain("release/2.1", "2.1");
    upgradeOperator(false);
    logger.info("SUCCESS - " + testMethod);
  }

  /**
   * Test for upgrading Operator from release 2.2.0 to develop branch.
   *
   * @throws Exception when upgrade fails
   */
  @Test
  public void testOperatorUpgradeFrom2_2_0() throws Exception {
    Assume.assumeFalse(QUICKTEST);
    String testMethod = new Object() {}.getClass().getEnclosingMethod().getName();
    logTestBegin(testMethod);
    OP_NS = "weblogic-operator220";
    OP_DEP_NAME = "operator-upgrade220";
    OP_SA = "operator-sa220";
    DOM_NS = "weblogic-domain220";
    DUID = "operatordomain220";
    setupOperatorAndDomain("release/2.2", "2.2.0");
    upgradeOperator(false);
    logger.info("SUCCESS - " + testMethod);
  }

  /**
   * Test for upgrading Operator from release 2.2.1 to develop branch.
   *
   * @throws Exception when upgrade fails
   */
  @Test
  public void testOperatorUpgradeFrom2_2_1() throws Exception {
    Assume.assumeFalse(QUICKTEST);
    String testMethod = new Object() {}.getClass().getEnclosingMethod().getName();
    logTestBegin(testMethod);
    OP_NS = "weblogic-operator221";
    OP_DEP_NAME = "operator-upgrade221";
    OP_SA = "operator-sa221";
    DOM_NS = "weblogic-domain221";
    DUID = "operatordomain221";
    setupOperatorAndDomain("release/2.2.1", "2.2.1");
    upgradeOperator(false);
    logger.info("SUCCESS - " + testMethod);
  }

  /**
   * Upgrades operator to develop branch by using the helm upgrade.
   *
   * @param restart boolean parameter used to determine if a restart of domain is checked
   * @throws Exception when upgrade fails or basic usecase testing or scaling fails.
   */
  private void upgradeOperator(boolean restart) throws Exception {
    operator.callHelmUpgrade("image=" + OP_TARGET_RELEASE);
    if (restart) {
      checkDomainRollingRestarted();
    }
    checkOperatorVersion();
    testBasicUseCases(domain);
    testClusterScaling(operator, domain);
  }

  /**
   * checks the expected version of the upgraded operator in a loop. In Jenkins it takes nearly 8
   * minutes to show the updated value of the domain CRD.
   *
   * @throws Exception when version does not match
   */
  private void checkOperatorVersion() throws Exception {
    boolean result = false;
    logger.log(
        Level.INFO,
        "Checking for the domain apiVersion "
            + getDomainApiVersion()
            + " in a loop for up to 15 minutes");
    for (int i = 0; i < 900; i = i + 10) {
      ExecResult exec =
          TestUtils.exec(
              "kubectl get domain -n " + DOM_NS + "  " + DUID + " -o jsonpath={.apiVersion}", true);
      if (exec.stdout().contains(getDomainApiVersion())) {
        logger.log(Level.INFO, "Got the expected apiVersion");
        result = true;
        break;
      }
      Thread.sleep(1000 * 10);
    }
    if (!result) {
      throw new RuntimeException("FAILURE: Didn't get the expected operator version");
    }
  }

  /**
   * Check whether the weblogic server instances are rolling restarted.
   *
   * @throws Exception If restart fails or not restarted
   */
  private void checkDomainRollingRestarted() throws Exception {
    domain.verifyAdminServerRestarted();
    TestUtils.checkPodReady(DUID + "-" + domain.getAdminServerName(), DOM_NS);
    for (int i = 2; i >= 1; i--) {
      logger.info(
          "Checking if managed server pod(" + DUID + "--managed-server" + i + ") is restarted");
      TestUtils.checkPodTerminating(DUID + "-managed-server" + i, DOM_NS);
      TestUtils.checkPodCreated(DUID + "-managed-server" + i, DOM_NS);
      TestUtils.checkPodReady(DUID + "-managed-server" + i, DOM_NS);
    }
  }
}
