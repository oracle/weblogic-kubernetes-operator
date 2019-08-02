// Copyright 2019, Oracle Corporation and/or its affiliates.  All rights reserved.
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
public class ITCoherenceTests extends BaseTest {

  private static Domain domain = null;
  private static Operator operator1;

  private static final String testAppName = "coherence-proxy-client";

  private static final String PROXY_CLIENT_SCRIPT = "buildRunProxyClient.sh";
  private static final String PROXY_CLIENT_APP_NAME = "coherence-proxy-client";
  private static final String OP_CACHE_LOAD = "load";
  private static final String OP_CACHE_VALIDATE = "validate";
  private static final String PROXY_PORT = "9000";

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
    }
  }

  @Test
  public void testRollingRestart() throws Exception {
    String testMethodName = new Object() {}.getClass().getEnclosingMethod().getName();
    logTestBegin(testMethodName);

    domain = createDomain();
    Assert.assertNotNull(domain);

    try {
      copyAndExecuteProxyClientInPod(OP_CACHE_LOAD);

      // Do the rolling restart
      restartDomainByChangingEnvProperty();

      copyAndExecuteProxyClientInPod(OP_CACHE_VALIDATE);
    } finally {
      destroyDomain();
    }
    logger.info("SUCCESS - " + testMethodName);
  }

  /**
   * Copy the shell script file and all App files over to the admin pod the run the script to build
   * the proxy client and run the proxy test.
   *
   * @param cacheOp - cache operation
   * @throws Exception exception
   */
  private static void copyAndExecuteProxyClientInPod(String cacheOp) {
    try {
      final String adminServerPod = domain.getDomainUid() + "-" + domain.getAdminServerName();

      final String domainNS = domain.getDomainNs();

      // Use the proxy running on Managed Server 1, get the internal POD IP
      String podName = domain.getManagedSeverPodName(1);
      String ProxyIP = TestUtils.getPodIP(domainNS, "", podName);

      String cohAppLocationOnHost = BaseTest.getAppLocationOnHost() + "/" + PROXY_CLIENT_APP_NAME;
      String cohAppLocationInPod = BaseTest.getAppLocationInPod() + "/" + PROXY_CLIENT_APP_NAME;
      final String cohScriptPathOnHost = cohAppLocationOnHost + "/" + PROXY_CLIENT_SCRIPT;
      final String cohScriptPathInPod = cohAppLocationInPod + "/" + PROXY_CLIENT_SCRIPT;
      final String successMarker = "CACHE-SUCCESS";

      logger.info("Copying files to admin pod for App " + PROXY_CLIENT_APP_NAME);

      // Create app dir in the admin pod
      StringBuffer mkdirCmd = new StringBuffer(" -- bash -c 'mkdir -p ");
      mkdirCmd.append(cohAppLocationInPod).append("'");
      TestUtils.kubectlexec(adminServerPod, domainNS, mkdirCmd.toString());

      // Copy shell script to the pod
      TestUtils.copyFileViaCat(cohScriptPathOnHost, cohScriptPathInPod, adminServerPod, domainNS);

      // Copy all App files to the admin pod
      TestUtils.copyAppFilesToPod(
          cohAppLocationOnHost, cohAppLocationInPod, adminServerPod, domainNS);

      logger.info(
          "Executing script "
              + PROXY_CLIENT_SCRIPT
              + " for App "
              + PROXY_CLIENT_APP_NAME
              + " in the admin pod");

      // Run the script to on the admin pod (note first arg is app directory is applocation in pod)
      domain.callShellScriptInAdminPod(
          successMarker, cohScriptPathInPod, cohAppLocationInPod, cacheOp, ProxyIP, PROXY_PORT);
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  /**
   * Modify the domain scope env property on the domain resource using kubectl apply -f domain.yaml
   * Verify that all the server pods in the domain got re-started. The property tested is: env:
   * "-Dweblogic.StdoutDebugEnabled=false"--> "-Dweblogic.StdoutDebugEnabled=true"
   *
   * @throws Exception
   */
  private void restartDomainByChangingEnvProperty() throws Exception {

    // The default cmd loop sleep is too long and we could miss states like terminating. Change
    // the
    // sleep and iterations
    //
    setWaitTimePod(2);
    setMaxIterationsPod(125);

    domain.verifyDomainServerPodRestart(
        "\"-Dweblogic.StdoutDebugEnabled=false\"", "\"-Dweblogic.StdoutDebugEnabled=true\"");
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
