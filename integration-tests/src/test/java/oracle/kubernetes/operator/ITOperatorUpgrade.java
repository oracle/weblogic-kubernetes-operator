// Copyright 2019, Oracle Corporation and/or its affiliates.  All rights reserved.
// Licensed under the Universal Permissive License v 1.0 as shown at
// http://oss.oracle.com/licenses/upl.
package oracle.kubernetes.operator;

import static oracle.kubernetes.operator.BaseTest.DOMAININIMAGE_WLST_YAML;
import static oracle.kubernetes.operator.BaseTest.OPERATOR1_YAML;
import static oracle.kubernetes.operator.BaseTest.QUICKTEST;
import static oracle.kubernetes.operator.BaseTest.logger;

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
import org.junit.FixMethodOrder;
import org.junit.Test;
import org.junit.runners.MethodSorters;

/**
 * Simple JUnit test file used for testing Operator.
 *
 * <p>This test is used for testing Helm install for Operator(s)
 */
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
public class ITOperatorUpgrade extends BaseTest {

  private static final String OP_BASE_REL = "2.0";
  private static final String OP_TARGET_RELEASE = "weblogic-kubernetes-operator:latest";
  private static final String DOM_TARGET_RELEASE_VERSION = "weblogic.oracle/v4";
  private static final String OP_NS = "weblogic-operator";
  private static final String OP_DEP_NAME = "operator-upgrade";
  private static final String OP_SA = "operator-sa";
  private static final String DOM_NS = "weblogic-domain";
  private static final String DUID = "operator20domain";
  private static String opUpgradeTmpDir;
  private Domain domain = null;
  private static Operator operator20;

  private void setupOperatorAndDomain(String operatorGitRelease, String operatorRelease)
      throws Exception {
    logger.log(Level.INFO, "+++++++++++++++Beginning Test Setup+++++++++++++++++++++");
    if (!QUICKTEST) {
      initialize(APP_PROPS_FILE);
      opUpgradeTmpDir = BaseTest.getResultDir() + "/operatorupgrade";
    }
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
    List<String> dom_ns = new ArrayList<String>();
    dom_ns.add(DOM_NS);
    operatorMap.put("domainNamespaces", dom_ns);
    operator20 = TestUtils.createOperator(operatorMap, Operator.RESTCertType.LEGACY);
    TestUtils.ExecAndPrintLog("kubectl get all --all-namespaces");

    Map<String, Object> wlstDomainMap = TestUtils.loadYaml(DOMAININIMAGE_WLST_YAML);
    wlstDomainMap.put("domainUID", DUID);
    wlstDomainMap.put("namespace", DOM_NS);
    wlstDomainMap.put("projectRoot", opUpgradeTmpDir + "/weblogic-kubernetes-operator");
    domain = TestUtils.createDomain(wlstDomainMap);
    Thread.sleep(1000 * 60);
    TestUtils.ExecAndPrintLog("kubectl get all --all-namespaces");
    domain.verifyDomainCreated();
    // testBasicUseCases(domain);
    // testClusterScaling(operator20, domain);
    printCompVersions();
    logger.log(Level.INFO, "+++++++++++++++Ending Test Setup+++++++++++++++++++++");
  }

  @After
  public void cleanupOperatorAndDomain() throws Exception {
    logger.log(Level.INFO, "+++++++++++++++Beginning AfterTest cleanup+++++++++++++++++++++");
    TestUtils.ExecAndPrintLog("docker images");
    if (domain != null) {
      domain.destroy();
    }
    if (operator20 != null) {
      operator20.destroy();
    }
    ExecResult result = cleanup();
    logger.log(Level.INFO, "cleanup stdout\n" + result.stdout());
    logger.log(Level.INFO, "cleanup stderr\n" + result.stderr());
    TestUtils.ExecAndPrintLog("helm del --purge operator-upgrade");
    TestUtils.ExecAndPrintLog(
        "kubectl delete pods,services,deployments,replicasets,configmaps,services --all  --grace-period=0 --force --ignore-not-found -n "
            + OP_NS);
    TestUtils.ExecAndPrintLog(
        "kubectl delete pods,services,deployments,replicasets,configmaps,services --all  --grace-period=0 --force --ignore-not-found -n "
            + DOM_NS);
    TestUtils.ExecAndPrintLog(
        "kubectl delete crd --all --grace-period=0 --ignore-not-found  --force");
    TestUtils.ExecAndPrintLog("kubectl delete ns weblogic-operator --ignore-not-found  --force");
    TestUtils.ExecAndPrintLog("kubectl delete ns weblogic-domain --ignore-not-found  --force");
    TestUtils.ExecAndPrintLog("kubectl get all --all-namespaces");
    logger.log(Level.INFO, "+++++++++++++++Done AfterTest cleanup+++++++++++++++++++++");
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
      tearDown(new Object() {}.getClass().getEnclosingClass().getSimpleName());
      logger.info("SUCCESS");
    }
  }

  @Test
  public void test5OperatorUpgradeFrom2_0() throws Exception {
    String testMethod = new Object() {}.getClass().getEnclosingMethod().getName();
    logTestBegin(testMethod);
    setupOperatorAndDomain("2.0", "2.0");
    upgradeOperator(true);
    logger.info("SUCCESS - " + testMethod);
  }

  @Test
  public void test4OperatorUpgradeFrom2_0_1() throws Exception {
    String testMethod = new Object() {}.getClass().getEnclosingMethod().getName();
    logTestBegin(testMethod);
    setupOperatorAndDomain("release/2.0.1", "2.0.1");
    upgradeOperator(true);
    logger.info("SUCCESS - " + testMethod);
  }

  @Test
  public void test3OperatorUpgradeFrom2_1() throws Exception {
    String testMethod = new Object() {}.getClass().getEnclosingMethod().getName();
    logTestBegin(testMethod);
    setupOperatorAndDomain("release/2.1", "2.1");
    upgradeOperator(false);
    logger.info("SUCCESS - " + testMethod);
  }

  @Test
  public void test2OperatorUpgradeFrom2_2_0() throws Exception {
    String testMethod = new Object() {}.getClass().getEnclosingMethod().getName();
    logTestBegin(testMethod);
    setupOperatorAndDomain("release/2.2", "2.2.0");
    upgradeOperator(false);
    logger.info("SUCCESS - " + testMethod);
  }

  @Test
  public void test1OperatorUpgradeFrom2_2_1() throws Exception {
    String testMethod = new Object() {}.getClass().getEnclosingMethod().getName();
    logTestBegin(testMethod);
    setupOperatorAndDomain("release/2.2.1", "2.2.1");
    upgradeOperator(false);
    logger.info("SUCCESS - " + testMethod);
  }

  private void upgradeOperator(boolean restart) throws Exception {
    operator20.callHelmUpgrade("image=" + OP_TARGET_RELEASE);
    //    if (restart) {
    //      checkDomainRollingRestarted();
    //    }
    checkOperatorVersion(DOM_TARGET_RELEASE_VERSION);
    // testBasicUseCases(domain);
    // testClusterScaling(operator20, domain);
  }

  private void checkOperatorVersion(String version) throws Exception {
    boolean result = false;
    logger.log(Level.INFO, "Checking for the domain apiVersion in a loop for up to 15 minutes");
    for (int i = 0; i < 900; i = i + 10) {
      TestUtils.ExecAndPrintLog(
          "kubectl get domain -n " + DOM_NS + "  " + DUID + " -o jsonpath={.apiVersion}");
      ExecResult exec =
          TestUtils.exec(
              "kubectl get domain -n " + DOM_NS + "  " + DUID + " -o jsonpath={.apiVersion}");
      if (exec.stdout().contains(DOM_TARGET_RELEASE_VERSION)) {
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

  private void printCompVersions() throws Exception {
    TestUtils.ExecAndPrintLog("docker images");
    TestUtils.ExecAndPrintLog("kubectl get pods -n " + OP_NS + " -o yaml");
    TestUtils.ExecAndPrintLog("kubectl get domain " + DUID + " -o yaml -n " + DOM_NS);
  }
}
