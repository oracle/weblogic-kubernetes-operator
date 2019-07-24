// Copyright 2019, Oracle Corporation and/or its affiliates.  All rights reserved.
// Licensed under the Universal Permissive License v 1.0 as shown at
// http://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Map;
import oracle.kubernetes.operator.utils.CoherenceUtils;
import oracle.kubernetes.operator.utils.Domain;
import oracle.kubernetes.operator.utils.ExecResult;
import oracle.kubernetes.operator.utils.K8sTestUtils;
import oracle.kubernetes.operator.utils.Operator;
import oracle.kubernetes.operator.utils.TestUtils;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.Assume;
import org.junit.BeforeClass;
import org.junit.FixMethodOrder;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runners.MethodSorters;
/**
 * Simple JUnit test file used for testing Operator.
 *
 * <p>This test is used for testing pods being restarted by some properties change.
 */
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
public class ITCoherenceTests extends BaseTest {

  private static Domain domain = null;
  private static Operator operator1;
  private static String domainUid = "";
  private static String restartTmpDir = "";
  private static String originalYaml;

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
      initialize(APP_PROPS_FILE);

      if (operator1 == null) {
        operator1 = TestUtils.createOperator(OPERATOR1_YAML);
      }
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
      tearDown(new Object() {}.getClass().getEnclosingClass().getSimpleName());
      logger.info("SUCCESS");
    }
  }

  @Test
  public void testRollingRestart() throws Exception {

    CoherenceUtils utils = new CoherenceUtils();

    domain = createDomain();
    Assert.assertNotNull(domain);

    //  public static void exposePod(String podName, String domainNS, String serviceName,  int port,
    // int targetPort)  throws Exception {


//    String nodePortName = "coh-nodeport";
//    String podName = "dd";
//    int port = 9000;
//    TestUtils.exposePod(podName, domain.getDomainNS(), nodePortName, port, port);
//
    utils.loadCache();
    utils.validateCache();

    destroyDomain();
  }


  /**
   * Modify the domain scope env property on the domain resource using kubectl apply -f domain.yaml
   * Verify that all the server pods in the domain got re-started. The property tested is: env:
   * "-Dweblogic.StdoutDebugEnabled=false"--> "-Dweblogic.StdoutDebugEnabled=true"
   *
   * @throws Exception
   */
  @Ignore
  @Test
  public void testServerPodsRestartByChangingEnvProperty() throws Exception {


    Assume.assumeFalse(QUICKTEST);
    String testMethodName = new Object() {}.getClass().getEnclosingMethod().getName();
    logTestBegin(testMethodName);

    logger.info(
        "About to verifyDomainServerPodRestart for Domain: "
            + domain.getDomainUid()
            + "  env property: StdoutDebugEnabled=false to StdoutDebugEnabled=true");
    domain.verifyDomainServerPodRestart(
        "\"-Dweblogic.StdoutDebugEnabled=false\"", "\"-Dweblogic.StdoutDebugEnabled=true\"");

    logger.info("SUCCESS - " + testMethodName);
  }

  private static Domain createDomain() throws Exception {

    Map<String, Object> domainMap = TestUtils.loadYaml(DOMAININIMAGE_WLST_YAML);
    domainMap.put("namespace","test1");
    domainMap.put("domainUID", "coh");
    domain = TestUtils.createDomain(domainMap);
    domain.verifyDomainCreated();
    return domain;
  }

  private static void destroyDomain() throws Exception {
    if (domain != null) {
      domain.destroy();
    }
  }

  /**
   * Utility method to check if a pod is in Terminating or Running status
   *
   * @param podName - String name of the pod to check the status for
   * @param podStatusExpected - String the expected status of Terminating || RUnning
   * @throws InterruptedException when thread is interrupted
   */
  private void verifyPodStatus(String podName, String podStatusExpected)
      throws InterruptedException {
    K8sTestUtils testUtil = new K8sTestUtils();
    String domain1LabelSelector = String.format("weblogic.domainUID in (%s)", domainUid);
    String namespace = domain.getDomainNS();
    boolean gotExpected = false;
    for (int i = 0; i < BaseTest.getMaxIterationsPod(); i++) {
      if (podStatusExpected.equals("Terminating")) {
        if (testUtil.isPodTerminating(namespace, domain1LabelSelector, podName)) {
          gotExpected = true;
          break;
        }
      } else if (podStatusExpected.equals("Running")) {
        if (testUtil.isPodRunning(namespace, domain1LabelSelector, podName)) {
          gotExpected = true;
          break;
        }
      }

      Thread.sleep(BaseTest.getWaitTimePod() * 1000);
    }
    Assert.assertTrue("Didn't get the expected pod status", gotExpected);
  }
}
