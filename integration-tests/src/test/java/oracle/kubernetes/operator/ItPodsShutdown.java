// Copyright (c) 2019, Oracle Corporation and/or its affiliates.  All rights reserved.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;

import oracle.kubernetes.operator.utils.Domain;
import oracle.kubernetes.operator.utils.DomainCrd;
import oracle.kubernetes.operator.utils.ExecCommand;
import oracle.kubernetes.operator.utils.ExecResult;
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
 * <p>This test is used for testing pods being shutdowned by some properties change.
 */
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
public class ItPodsShutdown extends BaseTest {

  private static final String testAppName = "httpsessionreptestapp";
  private static final String scriptName = "buildDeployAppInPod.sh";
  public static String domainUid = "";
  public static String domainNS = "";
  public static long terminationTime = 0;
  private static Domain domain = null;
  private static Operator operator1 = null;
  private static String shutdownTmpDir = "";
  private static String originalYaml;
  private static long terminationDefaultOptionsTime = 0;
  private static String modifiedYaml = null;
  private static int podVer = 1;

  /**
   * This method gets called only once before any of the test methods are executed. It does the
   * initialization of the integration test properties defined in OperatorIT.properties and setting
   * the resultRoot, pvRoot and projectRoot attributes. Create Operator1 and domainOnPVUsingWLST
   * with admin server and 1 managed server if they are not running
   *
   * @throws Exception exception
   */
  @BeforeClass
  public static void staticPrepare() throws Exception {
    // initialize test properties and create the directories
    if (FULLTEST) {
      initialize(APP_PROPS_FILE);

      logger.info("Checking if operator1 and domain are running, if not creating");
      if (operator1 == null) {
        operator1 = TestUtils.createOperator(OPERATOR1_YAML);
      }
      shutdownTmpDir = BaseTest.getResultDir() + "/shutdowntemp";
      Files.createDirectories(Paths.get(shutdownTmpDir));

      domain = createDomain();
      originalYaml =
          BaseTest.getUserProjectsDir()
              + "/weblogic-domains/"
              + domain.getDomainUid()
              + "/domain.yaml";
      Assert.assertNotNull(domain);
      domainUid = domain.getDomainUid();
      domainNS = domain.getDomainNs();
      BaseTest.setWaitTimePod(5);
      BaseTest.setMaxIterationsPod(50);
    }
  }

  /**
   * Releases k8s cluster lease, archives result, pv directories.
   *
   * @throws Exception exception
   */
  @AfterClass
  public static void staticUnPrepare() throws Exception {
    if (FULLTEST) {
      logger.info("+++++++++++++++++++++++++++++++++---------------------------------+");
      logger.info("BEGIN");
      logger.info("Run once, release cluster lease");

      destroyDomain();
      tearDown(new Object() {}.getClass().getEnclosingClass().getSimpleName());

      logger.info("SUCCESS");
    }
  }

  private static Domain createDomain() throws Exception {

    Map<String, Object> domainMap = TestUtils.loadYaml(DOMAINONPV_WLST_YAML);
    domainMap.put("domainUID", "domainpodsshutdown");
    domainMap.put("initialManagedServerReplicas", new Integer("1"));

    domainUid = (String) domainMap.get("domainUID");
    logger.info("Creating and verifying the domain creation with domainUid: " + domainUid);

    domain = TestUtils.createDomain(domainMap);
    domain.verifyDomainCreated();

    return domain;
  }

  private static void getDefaultShutdownTime() throws Exception {
    terminationDefaultOptionsTime = shutdownServer("managed-server1");
    logger.info(
        " termination pod's time with default shutdown options is: "
            + terminationDefaultOptionsTime);
  }

  private static void resetDomainCrd() throws Exception {

    // reset the domain crd
    domain.shutdown();
    logger.log(Level.INFO, "kubectl apply -f ", originalYaml);
    ExecResult exec = TestUtils.exec("kubectl apply -f " + originalYaml);
    logger.info(exec.stdout());
    logger.info("Verifying if the domain is restarted");
    Thread.sleep(10 * 1000);
    // should restart domain
    TestUtils.checkPodReady(domainUid + "-admin-server", domainNS);
    TestUtils.checkPodReady(domainUid + "-managed-server1", domainNS);

    Assert.assertTrue(
        "Property value was not found in the updated domain crd ",
        checkShutdownUpdatedProp(domainUid + "-admin-server", "30", "false", "Graceful"));
    Assert.assertTrue(
        "Property value was not found in the updated domain crd ",
        checkShutdownUpdatedProp(domainUid + "-managed-server1", "30", "false", "Graceful"));
  }

  private static void destroyDomain() throws Exception {
    if (domain != null) {
      domain.destroy();
    }
  }

  /**
   * send request to web app deployed on wls.
   *
   * @param testAppPath - URL path for webapp
   * @param domain - Domain where webapp deployed
   * @param deployApp - option to build and deployApp
   * @throws Exception exception
   */
  public static void callWebApp(String testAppPath, Domain domain, boolean deployApp)
      throws Exception {
    // String testAppPath =  "httpsessionreptestapp/CounterServlet?delayTime=" + delayTime;
    if (deployApp) {
      domain.buildDeployJavaAppInPod(
          testAppName, scriptName, BaseTest.getUsername(), BaseTest.getPassword());
      domain.callWebAppAndVerifyLoadBalancing(testAppName + "/CounterServlet?", false);
    }
    String nodePortHost = domain.getHostNameForCurl();
    int nodePort = domain.getLoadBalancerWebPort();

    StringBuffer webServiceUrl = new StringBuffer("curl --silent --noproxy '*' ");
    webServiceUrl
        .append(" -H 'host: ")
        .append(domainUid)
        .append(".org' ")
        .append(" http://")
        .append(nodePortHost)
        .append(":")
        .append(nodePort)
        .append("/")
        .append(testAppPath);

    // Send a HTTP request to keep open session
    String curlCmd = webServiceUrl.toString();
    logger.info("Send a HTTP request: " + curlCmd);

    ExecResult result = ExecCommand.exec(curlCmd);
    if (result.exitValue() != 0) {
      throw new Exception("FAILURE: command " + curlCmd + " failed, returned " + result.stderr());
    }
    logger.info(result.stdout());
  }

  /**
   * shutdown managed server.
   *
   * @throws Exception exception
   */
  private static long shutdownServer(String serverName) throws Exception {
    long startTime = System.currentTimeMillis();
    String cmd = "kubectl delete pod " + domainUid + "-" + serverName + " -n " + domainNS;
    logger.info("command to shutdown server <" + serverName + "> is: " + cmd);
    ExecResult result = ExecCommand.exec(cmd);
    if (result.exitValue() != 0) {
      terminationTime = 0;
      throw new Exception("FAILURE: command " + cmd + " failed, returned " + result.stderr());
    }
    long endTime = System.currentTimeMillis();
    terminationTime = endTime - startTime;
    return terminationTime;
  }

  private static boolean checkShutdownUpdatedProp(String podName, String... props)
      throws Exception {
    // kubectl get pod domainonpvwlst-managed-server1 | grep SHUTDOWN
    HashMap<String, Boolean> propFound = new HashMap<String, Boolean>();
    StringBuffer cmd = new StringBuffer("kubectl get pod ");
    cmd.append(podName);
    cmd.append(" -o yaml ");
    cmd.append(" -n ").append(domainNS);
    cmd.append(" | grep SHUTDOWN -A 1 ");

    logger.info(
        " Get SHUTDOWN props for " + podName + " in namespace " + " with command: '" + cmd + "'");

    ExecResult result = ExecCommand.exec(cmd.toString());
    String stdout = result.stdout();
    logger.info("Output " + stdout);
    boolean found = false;
    for (String prop : props) {
      if (stdout.contains(prop)) {
        logger.info("Property with value " + prop + " has found");
        propFound.put(prop, new Boolean(true));
      }
    }
    if (props.length == propFound.size()) {
      found = true;
    }
    return found;
  }

  /**
   * Add shutdown options at managed server level and verify the managed server pod are Terminated
   * and recreated with specified shutdown options.
   *
   * @throws Exception when domain.yaml cannot be read or modified
   */
  @Test
  public void testAddShutdownOptionsToMS() throws Exception {
    Assume.assumeTrue(FULLTEST);
    String testMethodName = new Object() {}.getClass().getEnclosingMethod().getName();
    logTestBegin(testMethodName);
    String podName = domainUid + "-managed-server1";
    Files.createDirectories(Paths.get(shutdownTmpDir));
    // Modify the original domain yaml to include shutdown options in managed server-1 node
    final DomainCrd crd = new DomainCrd(originalYaml);

    Map<String, Object> shutdownProps = new HashMap();
    shutdownProps.put("timeoutSeconds", 160);
    shutdownProps.put("shutdownType", "Forced");
    shutdownProps.put("ignoreSessions", true);
    crd.addShutDownOptionToMS("managed-server1", shutdownProps);
    try {
      updateCrdYamlVerifyShutdown(crd, 0);
      Assert.assertTrue(checkShutdownUpdatedProp(domainUid + "-admin-server", "Graceful"));
      Assert.assertTrue(
          checkShutdownUpdatedProp(domainUid + "-managed-server1", "Forced", "160", "true"));
    } finally {
      logger.log(
          Level.INFO, "Reverting back the domain to old crd\n kubectl apply -f {0}", originalYaml);
      resetDomainCrd();
    }
  }

  /**
   * Add shutdown options to Cluster level and verify the managed server pods in the cluster are
   * Terminated and recreated with specified shutdown options.
   *
   * @throws Exception when domain.yaml cannot be read or modified
   */
  @Test
  public void testAddShutdownOptionToCluster() throws Exception {
    Assume.assumeTrue(FULLTEST);
    String testMethodName = new Object() {}.getClass().getEnclosingMethod().getName();
    logTestBegin(testMethodName);

    Files.createDirectories(Paths.get(shutdownTmpDir));

    // Modify the original domain yaml to include shutdown options in cluster-1 node
    final DomainCrd crd = new DomainCrd(originalYaml);

    Map<String, Object> shutdownProps = new HashMap();
    shutdownProps.put("timeoutSeconds", 60);
    shutdownProps.put("shutdownType", "Forced");
    shutdownProps.put("ignoreSessions", true);

    crd.addShutdownOptionsToCluster(domain.getClusterName(), shutdownProps);
    try {
      updateCrdYamlVerifyShutdown(crd, 0);
      Assert.assertTrue(checkShutdownUpdatedProp(domainUid + "-admin-server", "Graceful"));
      Assert.assertTrue(checkShutdownUpdatedProp(domainUid + "-managed-server1", "Forced"));
    } finally {
      logger.log(
          Level.INFO, "Reverting back the domain to old crd\n kubectl apply -f {0}", originalYaml);
      resetDomainCrd();
    }
    logger.log(Level.INFO, "SUCCESS - {0}", testMethodName);
  }

  /**
   * Add shutdown options at domain level and verify all pods are Terminated and recreated.
   *
   * @throws Exception when domain.yaml cannot be read or modified
   */
  @Test
  public void testAddShutdownOptionsToDomain() throws Exception {

    Assume.assumeTrue(FULLTEST);
    String testMethodName = new Object() {}.getClass().getEnclosingMethod().getName();
    logTestBegin(testMethodName);

    Files.createDirectories(Paths.get(shutdownTmpDir));
    // Modify the original domain yaml to include shutdown options in domain spec node
    DomainCrd crd = new DomainCrd(originalYaml);

    Map<String, Object> shutdownProps = new HashMap();
    shutdownProps.put("timeoutSeconds", 160);
    crd.addShutdownOptionToDomain(shutdownProps);
    try {
      updateCrdYamlVerifyShutdown(crd, 0);
      Assert.assertTrue(checkShutdownUpdatedProp(domainUid + "-admin-server", "160"));
      Assert.assertTrue(checkShutdownUpdatedProp(domainUid + "-managed-server1", "160"));
    } finally {
      logger.log(
          Level.INFO, "Reverting back the domain to old crd\n kubectl apply -f {0}", originalYaml);
      resetDomainCrd();
    }
    logger.log(Level.INFO, "SUCCESS - {0}", testMethodName);
  }

  /**
   * Add shutdown option IgnoreSessions at managed server level and verify all pods are Terminated
   * according to the setting.
   *
   * @throws Exception when domain.yaml cannot be read or modified
   */
  // commenting out due OWLS-75023
  // @Test
  public void testAddShutdownOptionsToMsIgnoreSessions() throws Exception {

    Assume.assumeTrue(FULLTEST);
    String testMethodName = new Object() {}.getClass().getEnclosingMethod().getName();
    logTestBegin(testMethodName);

    Files.createDirectories(Paths.get(shutdownTmpDir));
    // Modify the original domain yaml to include shutdown options in domain spec node
    DomainCrd crd = new DomainCrd(originalYaml);

    Map<String, Object> shutdownProps = new HashMap();
    shutdownProps.put("timeoutSeconds", 160);
    shutdownProps.put("ignoreSessions", false);
    crd.addShutDownOptionToMS("managed-server1", shutdownProps);
    long delayTime = 50 * 1000;
    updateCrdYamlVerifyShutdown(crd, delayTime);

    Assert.assertTrue(
        checkShutdownUpdatedProp(domainUid + "-managed-server1", "160", "false", "Graceful"));
    if (terminationTime < delayTime) {
      logger.info("FAILURE: ignored opened session during shutdown");
      throw new Exception("FAILURE: ignored opened session during shutdown");
    }
    long terminationTimeWithIgnoreSessionFalse = terminationTime;
    logger.info(
        " Termination time with ignoreSession=false :" + terminationTimeWithIgnoreSessionFalse);

    shutdownProps = new HashMap();
    shutdownProps.put("timeoutSeconds", 160);
    shutdownProps.put("ignoreSessions", true);

    crd.addShutDownOptionToMS("managed-server1", shutdownProps);
    try {
      updateCrdYamlVerifyShutdown(crd, delayTime);
      Assert.assertTrue(
          checkShutdownUpdatedProp(domainUid + "-managed-server1", "160", "true", "Graceful"));

      long terminationTimeWithIgnoreSessionTrue = terminationTime;
      logger.info(
          " Termination time with ignoreSessions=true :" + terminationTimeWithIgnoreSessionTrue);

      if (terminationTimeWithIgnoreSessionFalse - (50 * 1000)
          < terminationTimeWithIgnoreSessionTrue) {
        logger.info("FAILURE: did not ignore opened sessions during shutdown");
        throw new Exception("FAILURE: did not ignore opened sessions during shutdown");
      }
    } finally {
      logger.log(
          Level.INFO, "Reverting back the domain to old crd\n kubectl apply -f {0}", originalYaml);
      resetDomainCrd();
    }
    logger.log(Level.INFO, "SUCCESS - {0}", testMethodName);
  }

  /**
   * Add shutdown option Timeout at managed server level and verify all pods are Terminated
   * according to the setting.
   *
   * @throws Exception when domain.yaml cannot be read or modified
   */
  @Test
  public void testAddShutdownOptionsToMsTimeout() throws Exception {

    Assume.assumeTrue(FULLTEST);
    String testMethodName = new Object() {}.getClass().getEnclosingMethod().getName();
    logTestBegin(testMethodName);

    Files.createDirectories(Paths.get(shutdownTmpDir));
    // Modify the original domain yaml to include shutdown options in domain spec node
    DomainCrd crd = new DomainCrd(originalYaml);

    long delayTime = 50 * 1000;
    // testing timeout
    Map<String, Object> shutdownProps = new HashMap();
    shutdownProps.put("timeoutSeconds", 20);
    shutdownProps.put("ignoreSessions", false);
    crd.addShutDownOptionToMS("managed-server1", shutdownProps);
    try {
      updateCrdYamlVerifyShutdown(crd, delayTime);
      Assert.assertTrue(
          checkShutdownUpdatedProp(domainUid + "-managed-server1", "20", "false", "Graceful"));
      if (terminationTime > (3 * 20 * 1000)) {
        logger.info("\"FAILURE: ignored timeoutValue during shutdown");
        throw new Exception("FAILURE: ignored timeoutValue during shutdown");
      }
    } finally {
      logger.log(
          Level.INFO, "Reverting back the domain to old crd\n kubectl apply -f {0}", originalYaml);
      resetDomainCrd();
    }
    logger.log(Level.INFO, "SUCCESS - {0}", testMethodName);
  }

  /**
   * Add shutdown option Forced at managed server level and verify all pods are Terminated according
   * to the setting.
   *
   * @throws Exception when domain.yaml cannot be read or modified
   */
  @Test
  public void testAddShutdownOptionsToMsForced() throws Exception {

    Assume.assumeTrue(FULLTEST);
    String testMethodName = new Object() {}.getClass().getEnclosingMethod().getName();
    logTestBegin(testMethodName);

    if (terminationDefaultOptionsTime == 0) {
      getDefaultShutdownTime();
    }
    Files.createDirectories(Paths.get(shutdownTmpDir));
    // Modify the original domain yaml to include shutdown options in domain spec node
    DomainCrd crd = new DomainCrd(originalYaml);

    long delayTime = 50 * 1000;
    // testing timeout
    Map<String, Object> shutdownProps = new HashMap();
    shutdownProps.put("shutdownType", "Forced");
    crd.addShutDownOptionToMS("managed-server1", shutdownProps);
    try {
      updateCrdYamlVerifyShutdown(crd, delayTime);

      Assert.assertTrue(checkShutdownUpdatedProp(domainUid + "-managed-server1", "Forced"));
      if ((2 * terminationDefaultOptionsTime < terminationTime)) {
        logger.info("\"FAILURE: ignored timeout Forced value during shutdown");
        throw new Exception("FAILURE: ignored timeout Forced during shutdown");
      }
    } finally {
      logger.log(
          Level.INFO, "Reverting back the domain to old crd\n kubectl apply -f {0}", originalYaml);
      resetDomainCrd();
    }
    logger.log(Level.INFO, "SUCCESS - {0}", testMethodName);
  }

  /**
   * Add shutdown env vars at domain spec level and verify the pod are Terminated and recreated.
   *
   * @throws Exception when domain.yaml cannot be read or modified to include the
   */
  @Test
  public void testAddEnvShutdownOptions() throws Exception {
    Assume.assumeTrue(FULLTEST);
    String testMethodName = new Object() {}.getClass().getEnclosingMethod().getName();
    logTestBegin(testMethodName);
    Files.createDirectories(Paths.get(shutdownTmpDir));
    // Modify the original domain yaml to include shutdown env vars options in domain spec node
    final DomainCrd crd = new DomainCrd(originalYaml);

    Map<String, String> envOpt = new HashMap();
    envOpt.put("SHUTDOWN_TYPE", "Forced");
    envOpt.put("SHUTDOWN_TIMEOUT", "60");
    envOpt.put("SHUTDOWN_IGNORE_SESSIONS", "false");
    crd.addEnvOption(envOpt);
    try {
      updateCrdYamlVerifyShutdown(crd, 0);
      checkShutdownUpdatedProp(domainUid + "-managed-server1", "Forced", "60", "false");
      checkShutdownUpdatedProp(domainUid + "-admin-server", "Forced", "60", "false");
    } finally {
      logger.log(
          Level.INFO, "Reverting back the domain to old crd\n kubectl apply -f {0}", originalYaml);
      resetDomainCrd();
    }
    logger.log(Level.INFO, "SUCCESS - {0}", testMethodName);
  }

  /**
   * Add shutdown env vars at domain spec level and managed server level,verify managed server
   * override domain level.
   *
   * @throws Exception when domain.yaml cannot be read or modified
   */
  @Test
  public void testShutdownOptionsOverrideViaEnv() throws Exception {
    Assume.assumeTrue(FULLTEST);
    String testMethodName = new Object() {}.getClass().getEnclosingMethod().getName();
    logTestBegin(testMethodName);

    Files.createDirectories(Paths.get(shutdownTmpDir));
    // Modify the original domain yaml to include shutdown options in domain spec node
    DomainCrd crd = new DomainCrd(originalYaml);

    Map<String, String> envOpt = new HashMap();
    envOpt.put("SHUTDOWN_TYPE", "Forced");
    envOpt.put("SHUTDOWN_TIMEOUT", "60");
    crd.addEnvOption(envOpt);

    Map<String, Object> shutdownProps = new HashMap();
    shutdownProps.put("timeoutSeconds", 20);
    shutdownProps.put("shutdownType", "Graceful");
    shutdownProps.put("ignoreSessions", false);

    crd.addShutDownOptionToMS("managed-server1", shutdownProps);
    try {
      updateCrdYamlVerifyShutdown(crd, 0);
      checkShutdownUpdatedProp(domainUid + "-managed-server1", "Graceful", "20");
      checkShutdownUpdatedProp(domainUid + "-admin-server", "Forced", "60");

    } finally {
      logger.log(
          Level.INFO, "Reverting back the domain to old crd\n kubectl apply -f {0}", originalYaml);
      resetDomainCrd();
    }

    logger.log(Level.INFO, "SUCCESS - {0}", testMethodName);
  }

  /**
   * Add shutdown options at cluster spec level and the managed server1 level,verify managed server
   * override cluster level.
   *
   * @throws Exception when domain.yaml cannot be read or modified
   */
  @Test
  public void testShutdownOptionsOverrideClusterLevel() throws Exception {
    Assume.assumeTrue(FULLTEST);
    String testMethodName = new Object() {}.getClass().getEnclosingMethod().getName();
    logTestBegin(testMethodName);
    Files.createDirectories(Paths.get(shutdownTmpDir));
    // Modify the original domain yaml to include shutdown env vars options in domain spec node
    final DomainCrd crd = new DomainCrd(originalYaml);

    Map<String, Object> shutdownProps = new HashMap();
    shutdownProps.put("timeoutSeconds", 20);
    shutdownProps.put("shutdownType", "Forced");
    shutdownProps.put("ignoreSessions", false);
    crd.addShutdownOptionsToCluster(domain.getClusterName(), shutdownProps);

    shutdownProps.put("shutdownType", "Graceful");

    crd.addShutDownOptionToMS("managed-server1", shutdownProps);
    try {
      updateCrdYamlVerifyShutdown(crd, 0);
      // scale up to 2 replicas to check both managed servers in the cluster
      scaleCluster(2);
      checkShutdownUpdatedProp(domainUid + "-managed-server1", "Graceful");
      checkShutdownUpdatedProp(domainUid + "-managed-server2", "Forced");
    } finally {
      logger.log(
          Level.INFO, "Reverting back the domain to old crd\n kubectl apply -f {0}", originalYaml);
      resetDomainCrd();
    }
    logger.log(Level.INFO, "SUCCESS - {0}", testMethodName);
  }

  private void updateCrdYamlVerifyShutdown(DomainCrd crd, long delayTime) throws Exception {
    String modYaml = crd.getYamlTree();
    logger.info(modYaml);
    terminationTime = 0;
    // change version to restart domain
    Map<String, String> domain = new HashMap();
    domain.put("restartVersion", "v1." + podVer);
    podVer++;
    crd.addObjectNodeToDomain(domain);
    // Write the modified yaml to a new file
    Path path = Paths.get(shutdownTmpDir, "shutdown.managed.yaml");
    logger.log(Level.INFO, "Path of the modified domain.yaml :{0}", path.toString());
    Charset charset = StandardCharsets.UTF_8;
    Files.write(path, modYaml.getBytes(charset));
    modifiedYaml = path.toString();
    // Apply the new yaml to update the domain crd
    this.domain.shutdown();
    logger.log(Level.INFO, "kubectl apply -f {0}", path.toString());
    ExecResult exec = TestUtils.exec("kubectl apply -f " + path.toString());
    logger.info(exec.stdout());

    logger.info("Verifying if the domain is restarted");
    TestUtils.checkPodReady(domainUid + "-admin-server", domainNS);
    TestUtils.checkPodReady(domainUid + "-managed-server1", domainNS);

    // invoke servlet to keep sessions opened, terminate pod and check shutdown time
    if (delayTime > 0) {
      String testAppPath = "httpsessionreptestapp/CounterServlet?invalidate";
      callWebApp(testAppPath, this.domain, true);
      SessionDelayThread sessionDelay = new SessionDelayThread(delayTime, this.domain);
      new Thread(sessionDelay).start();
      // sleep 5 secs before shutdown
      Thread.sleep(5 * 1000);
    }
    terminationTime = shutdownServer("managed-server1");
    logger.info(" termination time: " + terminationTime);
    TestUtils.checkPodCreated(domainUid + "-admin-server", domainNS);
    TestUtils.checkPodCreated(domainUid + "-managed-server1", domainNS);
  }

  /**
   * call operator to scale to specified number of replicas.
   *
   * @param replicas - number of managed servers
   * @throws Exception exception
   */
  private void scaleCluster(int replicas) throws Exception {
    logger.info("Scale up/down to " + replicas + " managed servers");
    operator1.scale(domain.getDomainUid(), domain.getClusterName(), replicas);
  }
}

