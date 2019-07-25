// Copyright 2019, Oracle Corporation and/or its affiliates.  All rights reserved.
// Licensed under the Universal Permissive License v 1.0 as shown at
// http://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator;

import java.util.Map;
import oracle.kubernetes.operator.utils.CoherenceUtils;
import oracle.kubernetes.operator.utils.Domain;
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

      // The default cmd loop sleep is too long and we could miss states like terminating. Change the
      // sleep and iterations
      //
      setWaitTimePod(2);
      setMaxIterationsPod(125);

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

    //    String nodePortName = "coh-nodeport";
    //    String podName = "dd";
    //    int port = 9000;
    //    TestUtils.exposePod(podName, domain.getDomainNS(), nodePortName, port, port);
    //
    utils.loadCache();

    // Do the rolling restart
    testServerPodsRestartByChangingEnvProperty();

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

  private static void destroyDomain() throws Exception {
    if (domain != null) {
      domain.destroy();
    }
  }

  private Domain createDomain() throws Exception {

    //    System.getenv().put("CUSTOM_WDT_ARCHIVE", "/Users/pmackin/archive-proxy.zip");

    // create domain
    Domain domain = null;
    Map<String, Object> domainMap = TestUtils.loadYaml(DOMAININIMAGE_WDT_YAML);
    domainMap.put("namespace", "test1");
    domainMap.put("domainUID", "coh");
    domainMap.put(
        "customWdtTemplate",
        BaseTest.getProjectRoot()
            + "/integration-tests/src/test/resources/wdt/coh-wdt-config.yaml");
    domain = TestUtils.createDomain(domainMap);
    domain.verifyDomainCreated();
    return domain;
  }
}