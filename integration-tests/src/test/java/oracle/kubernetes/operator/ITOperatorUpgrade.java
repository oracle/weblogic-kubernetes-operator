// Copyright 2019, Oracle Corporation and/or its affiliates.  All rights reserved.
// Licensed under the Universal Permissive License v 1.0 as shown at
// http://oss.oracle.com/licenses/upl.
package oracle.kubernetes.operator;

import static oracle.kubernetes.operator.BaseTest.DOMAININIMAGE_WLST_YAML;
import static oracle.kubernetes.operator.BaseTest.OPERATOR1_YAML;
import static oracle.kubernetes.operator.BaseTest.QUICKTEST;
import static oracle.kubernetes.operator.BaseTest.logger;

import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import oracle.kubernetes.operator.utils.Domain;
import oracle.kubernetes.operator.utils.ExecCommand;
import oracle.kubernetes.operator.utils.ExecResult;
import oracle.kubernetes.operator.utils.Operator;
import oracle.kubernetes.operator.utils.TestUtils;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.BeforeClass;
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
  private static final String OP_TARGET_RELEASE_VERSION = "apiVersion: weblogic.oracle/v4";
  private static final String OP_TARGET_RELEASE = "weblogic-kubernetes-operator:latest";
  private static final String OP_NS = "weblogic-operator";
  private static final String OP_DEP_NAME = "operator-upgrade";
  private static final String OP_SA = "operator-sa";
  private static final String DOM_NS = "weblogic-domain";
  private static final String DUID = "operator20domain";
  private static String opUpgradeTmpDir;
  private Domain domain = null;
  private static Operator operator20;

  /**
   * This method gets called only once before any of the test methods are executed. It does the
   * initialization of the integration test properties defined in OperatorIT.properties and setting
   * the resultRoot, pvRoot and projectRoot attributes.
   *
   * @throws Exception
   */
  @BeforeClass
  public static void staticPrepare() throws Exception {
    if (!QUICKTEST) {
      initialize(APP_PROPS_FILE);
      pullImages();
      opUpgradeTmpDir = BaseTest.getResultDir() + "/operatorupgrade";
    }
  }

  private void setupOperatorAndDomain(String operatorGitRelease, String operatorRelease)
      throws Exception {
    logger.log(Level.INFO, "+++++++++++++++Beginning Test Setup+++++++++++++++++++++");
    TestUtils.exec("rm -rf " + Paths.get(opUpgradeTmpDir).toString());
    Files.createDirectories(Paths.get(opUpgradeTmpDir));
    setEnv("IMAGE_NAME_OPERATOR", "oracle/weblogic-kubernetes-operator");
    setEnv("IMAGE_TAG_OPERATOR", operatorRelease);

    Map<String, Object> operatorMap = TestUtils.loadYaml(OPERATOR1_YAML);
    operatorMap.put("operatorVersion", operatorGitRelease);
    operatorMap.put("operatorVersionDir", opUpgradeTmpDir);
    operatorMap.put("namespace", OP_NS);
    operatorMap.put("releaseName", OP_DEP_NAME);
    operatorMap.put("serviceAccount", OP_SA);
    List<String> dom_ns = new ArrayList<String>();
    dom_ns.add(DOM_NS);
    operatorMap.put("domainNamespaces", dom_ns);
    operator20 = TestUtils.createOperator(operatorMap, Operator.RESTCertType.LEGACY);

    Map<String, Object> wlstDomainMap = TestUtils.loadYaml(DOMAININIMAGE_WLST_YAML);
    wlstDomainMap.put("domainUID", DUID);
    wlstDomainMap.put("namespace", DOM_NS);
    wlstDomainMap.put("projectRoot", opUpgradeTmpDir + "/weblogic-kubernetes-operator");
    domain = TestUtils.createDomain(wlstDomainMap);
    domain.verifyDomainCreated();
    testBasicUseCases(domain);
    testClusterScaling(operator20, domain);
    TestUtils.ExecAndPrintLog("kubectl get domain " + DUID + " -o yaml -n " + DOM_NS);
    TestUtils.ExecAndPrintLog("docker imges");
    logger.log(Level.INFO, "+++++++++++++++Ending Test Setup+++++++++++++++++++++");
  }

  @After
  public void cleanupOperatorAndDomain() throws Exception {
    logger.log(Level.INFO, "+++++++++++++++Beginning AfterTest cleanup+++++++++++++++++++++");
    TestUtils.ExecAndPrintLog("docker imges");
    if (domain != null) {
      domain.destroy();
    }
    if (operator20 != null) {
      operator20.destroy();
      operator20 = null;
    }
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
    TestUtils.ExecAndPrintLog(BaseTest.getProjectRoot() + "/src/integration-tests/bash/cleanup.sh");
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
  public void testOperatorUpgradeFrom2_0ToDevelop() throws Exception {
    String testMethod = new Object() {}.getClass().getEnclosingMethod().getName();
    logTestBegin(testMethod);
    // checkout weblogic operator image 2.0
    // pull traefik , wls and operator images
    // create service account, etc.,
    // create traefik loadbalancer
    // create operator
    // create domain

    // pull operator 2.1 image
    // helm upgrade to operator 2.1
    // verify the domain is not restarted but the operator image running is 2.1
    // createOperator();
    // verifyDomainCreated();
    setupOperatorAndDomain("2.0", "2.0");
    setEnv("IMAGE_NAME_OPERATOR", "weblogic-kubernetes-operator");
    setEnv("IMAGE_TAG_OPERATOR", "latest");
    upgradeOperator(true);
    logger.info("SUCCESS - " + testMethod);
  }

  @Test
  public void testOperatorUpgradeFrom2_0_1ToDevelop() throws Exception {
    String testMethod = new Object() {}.getClass().getEnclosingMethod().getName();
    logTestBegin(testMethod);
    setupOperatorAndDomain("release/2.0.1", "2.0.1");
    upgradeOperator(true);
    logger.info("SUCCESS - " + testMethod);
  }

  @Test
  public void testOperatorUpgradeFrom2_1ToDevelop() throws Exception {
    String testMethod = new Object() {}.getClass().getEnclosingMethod().getName();
    logTestBegin(testMethod);
    setupOperatorAndDomain("release/2.1", "2.1");
    upgradeOperator(false);
    logger.info("SUCCESS - " + testMethod);
  }

  @Test
  public void testOperatorUpgradeFrom2_2_0ToDevelop() throws Exception {
    String testMethod = new Object() {}.getClass().getEnclosingMethod().getName();
    logTestBegin(testMethod);
    setupOperatorAndDomain("release/2.2", "2.2.0");
    upgradeOperator(false);
    logger.info("SUCCESS - " + testMethod);
  }

  @Test
  public void testOperatorUpgradeFrom2_2_1ToDevelop() throws Exception {
    String testMethod = new Object() {}.getClass().getEnclosingMethod().getName();
    logTestBegin(testMethod);
    setupOperatorAndDomain("release/2.2.1", "2.2.1");
    upgradeOperator(false);
    logger.info("SUCCESS - " + testMethod);
  }

  private void upgradeOperator(boolean restart) throws Exception {
    upgradeOperatorHelm(OP_TARGET_RELEASE);
    checkOperatorVersion(OP_TARGET_RELEASE_VERSION);
    if (restart) checkDomainRollingRestarted();
    testBasicUseCases(domain);
    testClusterScaling(operator20, domain);
  }

  private static void pullImages() throws Exception {
    TestUtils.ExecAndPrintLog("docker pull oracle/weblogic-kubernetes-operator:" + OP_BASE_REL);
    TestUtils.ExecAndPrintLog("docker images");
  }

  private void upgradeOperatorHelm(String upgradeRelease) throws Exception {
    TestUtils.ExecAndPrintLog(
        "cd "
            + BaseTest.getProjectRoot()
            + " && helm upgrade --reuse-values --set 'image="
            + upgradeRelease
            + "' --wait --timeout 60 "
            + OP_DEP_NAME
            + " kubernetes/charts/weblogic-operator");
  }

  private void checkOperatorVersion(String version) throws Exception {
    TestUtils.ExecAndPrintLog("kubectl get domain " + DUID + " -o yaml -n " + DOM_NS);
    ExecResult result = ExecCommand.exec("kubectl get domain " + DUID + " -o yaml -n " + DOM_NS);
    if (!result.stdout().contains(version)) {
      logger.log(Level.INFO, result.stdout());
      throw new RuntimeException("FAILURE: Didn't get the expected operator version");
    }
  }

  public static void setEnv(String key, String value) {
    try {
      Map<String, String> env = System.getenv();
      Class<?> cl = env.getClass();
      Field field = cl.getDeclaredField("m");
      field.setAccessible(true);
      Map<String, String> writableEnv = (Map<String, String>) field.get(env);
      writableEnv.put(key, value);
    } catch (Exception e) {
      throw new IllegalStateException("Failed to set environment variable", e);
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
}
