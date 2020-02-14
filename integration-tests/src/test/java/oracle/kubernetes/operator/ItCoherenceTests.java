// Copyright (c) 2019, Oracle Corporation and/or its affiliates.  All rights reserved.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator;

import java.util.HashMap;
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
 * This class contains Coherence related integration tests.
 */
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
public class ItCoherenceTests extends BaseTest {

  private static Domain domain = null;
  private static Operator operator1 = null;

  private static final String PROXY_CLIENT_SCRIPT = "buildRunProxyClient.sh";
  private static final String PROXY_CLIENT_APP_NAME = "coherence-proxy-client";
  private static final String PROXY_SERVER_APP_NAME = "coherence-proxy-server";
  private static final String OP_CACHE_LOAD = "load";
  private static final String OP_CACHE_VALIDATE = "validate";
  private static final String PROXY_PORT = "9000";

  /**
   * This method gets called only once before any of the test methods are executed. It does the
   * initialization of the integration test properties defined in OperatorIT.properties and setting
   * the resultRoot, pvRoot and projectRoot attributes. Also, create the operator.
   *
   * @throws Exception exception
   */
  @BeforeClass
  public static void staticPrepare() throws Exception {
    // initialize test properties and create the directories
    if (FULLTEST) {
      initialize(APP_PROPS_FILE);
      operator1 = TestUtils.createOperator(OPERATOR1_YAML);
    }
  }

  /**
   * Releases k8s cluster lease, archives result, pv directories and destroy the operator.
   *
   * @throws Exception exception
   */
  @AfterClass
  public static void staticUnPrepare() throws Exception {
    if (FULLTEST) {
      operator1.destroy();
      tearDown(new Object() {}.getClass().getEnclosingClass().getSimpleName());
    }
  }

  @Test
  public void testRollingRestart() throws Exception {
    Assume.assumeTrue(FULLTEST);

    String testMethodName = new Object() {}.getClass().getEnclosingMethod().getName();
    logTestBegin(testMethodName);

    domain = createDomain();
    Assert.assertNotNull(domain);

    try {
      // Build and run the proxy client on the admin VM to load the cache
      copyAndExecuteProxyClientInPod(OP_CACHE_LOAD);

      // Do the rolling restart
      restartDomainByChangingEnvProperty();

      // Build and run the proxy client on the admin VM to validate the cache
      copyAndExecuteProxyClientInPod(OP_CACHE_VALIDATE);
    } finally {
      destroyDomain();
    }
    logger.info("SUCCESS - " + testMethodName);
  }

  /**
   * Since the coherence.jar is not open source, we need to build the proxy client on the admin VM, which has the
   * coherence.jar.  Copy the shell script file and all coherence app files over to the admin pod.
   * Then run the script to build the proxy client and run the proxy test.
   *
   * @param cacheOp - cache operation
   */
  private static void copyAndExecuteProxyClientInPod(String cacheOp) {
    try {
      final String adminServerPod = domain.getDomainUid() + "-" + domain.getAdminServerName();

      final String domainNS = domain.getDomainNs();

      // Use the proxy running on Managed Server 1, get the internal POD IP
      final String podName = domain.getManagedSeverPodName(1);
      final String ProxyIP = TestUtils.getPodIP(domainNS, "", podName);

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
   * Create the domain.
   *
   * @return domain
   * @throws Exception exception
   */
  private Domain createDomain() throws Exception {

    Map<String, String> envMap = new HashMap<>();

    // Set this ENV var with the WDT archive so that it is included in the image build.
    envMap.put("CUSTOM_WDT_ARCHIVE", buildProxyServerWdtZip());

    // create domain
    Map<String, Object> domainMap = TestUtils.loadYaml(DOMAININIMAGE_WDT_YAML);
    domainMap.put("namespace", "test1");
    domainMap.put("domainUID", "coh");
    domainMap.put("additionalEnvMap", envMap);

    domainMap.put(
        "customWdtTemplate",
        BaseTest.getProjectRoot()
            + "/integration-tests/src/test/resources/wdt/coh-wdt-config.yaml");
    Domain domain = TestUtils.createDomain(domainMap);
    domain.verifyDomainCreated();
    return domain;
  }

  /**
   * Destroy the domain.
   *
   * @throws Exception exception
   */
  private static void destroyDomain() throws Exception {
    if (domain != null) {
      domain.destroy();
    }
  }

  /**
   * Modify the domain scope env property on the domain resource using kubectl apply -f domain.yaml
   * Verify that all the server pods in the domain got re-started. The property tested is: env:
   * "-Dweblogic.StdoutDebugEnabled=false"--> "-Dweblogic.StdoutDebugEnabled=true"
   *
   * @throws Exception exception
   */
  private void restartDomainByChangingEnvProperty() throws Exception {

    // The default cmd loop sleep is too long and we could miss states like terminating. Change
    // the
    // sleep and iterations
    //
    setWaitTimePod(2);
    setMaxIterationsPod(150);

    domain.verifyDomainServerPodRestart(
        "\"-Dweblogic.StdoutDebugEnabled=false\"", "\"-Dweblogic.StdoutDebugEnabled=true\"");
  }

  /**
   * Build the WDT zip that contains the Coherence proxy server.
   *
   * @return the WDT zip path
   */
  private static String buildProxyServerWdtZip() {

    // Build the proxy server gar file
    String garPath = getResultDir() + "/coh-proxy-server.gar";
    String cohAppLocationOnHost = BaseTest.getAppLocationOnHost() + "/" + PROXY_SERVER_APP_NAME;
    TestUtils.buildJarArchive(garPath, cohAppLocationOnHost);

    // Build the WDT zip
    String wdtArchivePath = getResultDir() + "/coh-wdt-archive.zip";
    TestUtils.buildWdtZip(wdtArchivePath, new String[] {garPath}, getResultDir());
    return wdtArchivePath;
  }
}
