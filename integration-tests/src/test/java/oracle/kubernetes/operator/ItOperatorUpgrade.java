// Copyright (c) 2019, 2020, Oracle Corporation and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;

import oracle.kubernetes.operator.utils.Domain;
import oracle.kubernetes.operator.utils.ExecResult;
import oracle.kubernetes.operator.utils.LoggerHelper;
import oracle.kubernetes.operator.utils.Operator;
import oracle.kubernetes.operator.utils.TestUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.MethodOrderer.Alphanumeric;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

/**
 * Operator upgrade JUnit test file testing the operator upgrade from older releases to develop.
 */
@TestMethodOrder(Alphanumeric.class)
public class ItOperatorUpgrade extends BaseTest {

  private static final String OP_BASE_REL = "2.0";
  private static String OP_TARGET_RELEASE = "weblogic-kubernetes-operator:latest";
  private static String OP_NS = "";
  private static String OP_DEP_NAME = "";
  private static String OP_SA = "";
  private static String DOM_NS = "";
  private static String DUID = "";
  private static String opUpgradeTmpDir;
  private Domain domain = null;
  private static Operator operator;
  boolean testCompletedSuccessfully = false;
  static String testClassName = null;

  /**
   * This method gets called only once before any of the test methods are executed.
   *
   * @throws Exception exception
   */
  @BeforeAll
  public static void staticPrepare() throws Exception {
    testClassName = new Object() {
    }.getClass().getEnclosingClass().getSimpleName();
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
    if (System.getenv("IMAGE_NAME_OPERATOR") != null
        && System.getenv("IMAGE_TAG_OPERATOR") != null) {
      OP_TARGET_RELEASE = System.getenv("IMAGE_NAME_OPERATOR") + ":"
          + System.getenv("IMAGE_TAG_OPERATOR");
    }
  }

  /**
   * cleanup the domain and operator after every test.
   *
   * @throws Exception when domain and operator cleanup fails
   */
  @AfterEach
  public void cleanupOperatorAndDomain() throws Exception {
    if (testCompletedSuccessfully) {
      LoggerHelper.getLocal().log(Level.INFO, "+++++++++++++++Beginning AfterTest cleanup+++++++++++++++++++++");
      if (domain != null) {
        //domain.destroy();
        TestUtils.deleteWeblogicDomainResources(domain.getDomainUid());
        TestUtils.verifyAfterDeletion(domain);
        domain.deleteImage();
      }
      if (operator != null) {
        operator.destroy();
      }
      TestUtils.exec("rm -rf " + Paths.get(opUpgradeTmpDir).toString());
      //ExecResult result = cleanup();
      LoggerHelper.getLocal().log(Level.INFO, "+++++++++++++++Done AfterTest cleanup+++++++++++++++++++++");
    }
  }

  /**
   * Test for upgrading Operator from release 2.0 to develop branch.
   *
   * @throws Exception when upgrade fails
   */
  @Test
  public void testOperatorUpgradeFrom2_0() throws Exception {
    Assumptions.assumeTrue(FULLTEST);
    testCompletedSuccessfully = false;
    String testMethod = new Object() {
    }.getClass().getEnclosingMethod().getName();
    logTestBegin(testMethod);
    OP_NS = "weblogic-operator20";
    OP_DEP_NAME = "operator-upgrade20";
    OP_SA = "operator-sa20";
    DOM_NS = "weblogic-domain20";
    DUID = "operatordomain20";
    setupOperatorAndDomain("2.0", "2.0");
    upgradeOperator(true);
    testCompletedSuccessfully = true;
    LoggerHelper.getLocal().log(Level.INFO, "SUCCESS - " + testMethod);
  }

  /**
   * Test for upgrading Operator from release 2.0.1 to develop branch.
   *
   * @throws Exception when upgrade fails
   */
  @Test
  public void testOperatorUpgradeFrom2_0_1() throws Exception {
    Assumptions.assumeTrue(FULLTEST);
    testCompletedSuccessfully = false;
    String testMethod = new Object() {
    }.getClass().getEnclosingMethod().getName();
    logTestBegin(testMethod);
    OP_NS = "weblogic-operator201";
    OP_DEP_NAME = "operator-upgrade201";
    OP_SA = "operator-sa201";
    DOM_NS = "weblogic-domain201";
    DUID = "operatordomain201";
    setupOperatorAndDomain("release/2.0.1", "2.0.1");
    upgradeOperator(true);
    testCompletedSuccessfully = true;
    LoggerHelper.getLocal().log(Level.INFO, "SUCCESS - " + testMethod);
  }

  /**
   * Test for upgrading Operator from release 2.1 to develop branch.
   *
   * @throws Exception when upgrade fails
   */
  @Test
  public void testOperatorUpgradeFrom2_1() throws Exception {
    Assumptions.assumeTrue(FULLTEST);
    testCompletedSuccessfully = false;
    String testMethod = new Object() {
    }.getClass().getEnclosingMethod().getName();
    logTestBegin(testMethod);
    OP_NS = "weblogic-operator21";
    OP_DEP_NAME = "operator-upgrade21";
    OP_SA = "operator-sa21";
    DOM_NS = "weblogic-domain21";
    DUID = "operatordomain21";
    setupOperatorAndDomain("release/2.1", "2.1");
    upgradeOperator(false);
    testCompletedSuccessfully = true;
    LoggerHelper.getLocal().log(Level.INFO, "SUCCESS - " + testMethod);
  }

  /**
   * Test for upgrading Operator from release 2.2.0 to develop branch.
   *
   * @throws Exception when upgrade fails
   */
  @Test
  public void testOperatorUpgradeFrom2_2_0() throws Exception {
    Assumptions.assumeTrue(FULLTEST);
    testCompletedSuccessfully = false;
    String testMethod = new Object() {
    }.getClass().getEnclosingMethod().getName();
    logTestBegin(testMethod);
    OP_NS = "weblogic-operator220";
    OP_DEP_NAME = "operator-upgrade220";
    OP_SA = "operator-sa220";
    DOM_NS = "weblogic-domain220";
    DUID = "operatordomain220";
    setupOperatorAndDomain("release/2.2", "2.2.0");
    upgradeOperator(false);
    testCompletedSuccessfully = true;
    LoggerHelper.getLocal().log(Level.INFO, "SUCCESS - " + testMethod);
  }

  /**
   * Test for upgrading Operator from release 2.2.1 to develop branch.
   *
   * @throws Exception when upgrade fails
   */
  @Test
  public void testOperatorUpgradeFrom2_2_1() throws Exception {
    Assumptions.assumeTrue(FULLTEST);
    testCompletedSuccessfully = false;
    String testMethod = new Object() {
    }.getClass().getEnclosingMethod().getName();
    logTestBegin(testMethod);
    OP_NS = "weblogic-operator221";
    OP_DEP_NAME = "operator-upgrade221";
    OP_SA = "operator-sa221";
    DOM_NS = "weblogic-domain221";
    DUID = "operatordomain221";
    setupOperatorAndDomain("release/2.2.1", "2.2.1");
    upgradeOperator(false);
    testCompletedSuccessfully = true;
    LoggerHelper.getLocal().log(Level.INFO, "SUCCESS - " + testMethod);
  }

  /**
   * Test for upgrading Operator from release 2.3.0 to develop branch.
   *
   * @throws Exception when upgrade fails
   */
  @Test
  public void testOperatorUpgradeFrom2_3_0() throws Exception {
    Assumptions.assumeTrue(QUICKTEST);
    testCompletedSuccessfully = false;
    String testMethod = new Object() {
    }.getClass().getEnclosingMethod().getName();
    logTestBegin(testMethod);
    OP_NS = "weblogic-operator230";
    OP_DEP_NAME = "operator-upgrade230";
    OP_SA = "operator-sa230";
    DOM_NS = "weblogic-domain230";
    DUID = "operatordomain230";
    setupOperatorAndDomain("release/2.3.0", "2.3.0");
    upgradeOperator(false);
    testCompletedSuccessfully = true;
    LoggerHelper.getLocal().log(Level.INFO, "SUCCESS - " + testMethod);
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
    testClusterScaling(operator, domain, false);
  }

  /**
   * checks the expected version of the upgraded operator in a loop. In Jenkins it takes nearly 8
   * minutes to show the updated value of the domain CRD.
   *
   * @throws Exception when version does not match
   */
  private void checkOperatorVersion() throws Exception {
    boolean result = false;
    LoggerHelper.getLocal().log(
        Level.INFO,
        "Checking for the domain apiVersion "
            + getDomainApiVersion()
            + " in a loop for up to 15 minutes");
    for (int i = 0; i < 900; i = i + 10) {
      ExecResult exec =
          TestUtils.exec(
              "kubectl get domain -n " + DOM_NS + "  " + DUID + " -o jsonpath={.apiVersion}", true);
      if (exec.stdout().contains(getDomainApiVersion())) {
        LoggerHelper.getLocal().log(Level.INFO, "Got the expected apiVersion");
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
      LoggerHelper.getLocal().log(Level.INFO,
          "Checking if managed server pod(" + DUID + "--managed-server" + i + ") is restarted");
      TestUtils.checkPodTerminating(DUID + "-managed-server" + i, DOM_NS);
      TestUtils.checkPodCreated(DUID + "-managed-server" + i, DOM_NS);
      TestUtils.checkPodReady(DUID + "-managed-server" + i, DOM_NS);
    }
  }


  /**
   * Creates operator based on operatorRelease passed to it and then creates a WebLogic domain
   * controlled by that operator.
   *
   * @param operatorGitRelease Git branch name of the operator release version
   * @param operatorRelease    Operator release version from the
   *                           https://hub.docker.com/r/oracle/weblogic-kubernetes-operator/tags
   * @throws Exception when operator or domain creation fails
   */
  private void setupOperatorAndDomain(String operatorGitRelease, String operatorRelease)
      throws Exception {
    LoggerHelper.getLocal().log(Level.INFO, "+++++++++++++++Beginning Test Setup+++++++++++++++++++++");
    opUpgradeTmpDir = getResultDir() + "/operatorupgrade";
    TestUtils.exec("rm -rf " + Paths.get(opUpgradeTmpDir).toString());
    Files.createDirectories(Paths.get(opUpgradeTmpDir));
    Map<String, Object> operatorMap = createOperatorMap(getNewSuffixCount(), true, "");
    operatorMap.put("operatorImageName", "oracle/weblogic-kubernetes-operator");
    operatorMap.put("operatorImageTag", operatorRelease);
    operatorMap.put("operatorGitVersion", operatorGitRelease);
    operatorMap.put("operatorGitVersionDir", opUpgradeTmpDir);
    operatorMap.put("namespace", OP_NS);
    operatorMap.put("releaseName", OP_DEP_NAME);
    operatorMap.put("serviceAccount", OP_SA);
    operatorMap.put("externalRestEnabled", true);
    List<String> domNs = new ArrayList<String>();
    domNs.add(DOM_NS);
    operatorMap.put("domainNamespaces", domNs);
    operator = TestUtils.createOperator(operatorMap, Operator.RestCertType.LEGACY);
    // TestUtils.exec("kubectl get all --all-namespaces", true);

    // Map<String, Object> wlstDomainMap = TestUtils.loadYaml(DOMAININIMAGE_WLST_YAML);
    Map<String, Object> wlstDomainMap = createDomainInImageMap(getNewSuffixCount(), false, testClassName);
    wlstDomainMap.put("domainUID", DUID);
    wlstDomainMap.put("namespace", DOM_NS);
    wlstDomainMap.put("projectRoot", opUpgradeTmpDir + "/weblogic-kubernetes-operator");
    domain = TestUtils.createDomain(wlstDomainMap);
    // TestUtils.exec("kubectl get all --all-namespaces", true);
    domain.verifyPodsCreated();
    domain.verifyServicesCreated();
    domain.verifyServersReady();
    LoggerHelper.getLocal().log(Level.INFO, "+++++++++++++++Ending Test Setup+++++++++++++++++++++");
  }

}
