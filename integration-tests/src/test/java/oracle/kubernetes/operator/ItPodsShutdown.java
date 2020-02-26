// Copyright (c) 2019, 2020, Oracle Corporation and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;

import oracle.kubernetes.operator.utils.Domain;
import oracle.kubernetes.operator.utils.DomainCrd;
import oracle.kubernetes.operator.utils.ExecCommand;
import oracle.kubernetes.operator.utils.ExecResult;
import oracle.kubernetes.operator.utils.LoggerHelper;
import oracle.kubernetes.operator.utils.Operator;
import oracle.kubernetes.operator.utils.TestUtils;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.MethodOrderer.Alphanumeric;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

/**
 * Simple JUnit test file used for testing Operator.
 *
 * <p>This test is used for testing pods being shutdowned by some properties change.
 */
@TestMethodOrder(Alphanumeric.class)
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
  private static String testClassName;
  private static String domainNS1;
  private static StringBuffer namespaceList;

  /**
   * This method gets called only once before any of the test methods are executed. It does the
   * initialization of the integration test properties defined in OperatorIT.properties and setting
   * the resultRoot, pvRoot and projectRoot attributes.
   *
   * @throws Exception exception
   */
  @BeforeAll
  public static void staticPrepare() throws Exception {
    if (FULLTEST) {
      namespaceList = new StringBuffer();
      testClassName = new Object() {
      }.getClass().getEnclosingClass().getSimpleName();
      initialize(APP_PROPS_FILE, testClassName);
    }
  }

  /**
   * This method gets called before every test. It creates the result/pv root directories
   * for the test. Creates the operator and domain if its not running.
   *
   * @throws Exception exception if result/pv/operator/domain creation fails
   */
  @BeforeEach
  public void prepare() throws Exception {
    // initialize test properties and create the directories
    if (FULLTEST) {
      LoggerHelper.getLocal().log(Level.INFO, "Checking if operator1 and domain are running, if not creating");
      // create operator1
      if (operator1 == null) {
        createResultAndPvDirs(testClassName);
        Map<String, Object> operatorMap = createOperatorMap(getNewSuffixCount(), true, testClassName);
        operator1 = TestUtils.createOperator(operatorMap, Operator.RestCertType.SELF_SIGNED);
        Assertions.assertNotNull(operator1);
        domainNS1 = ((ArrayList<String>) operatorMap.get("domainNamespaces")).get(0);
        namespaceList.append((String)operatorMap.get("namespace"));
        namespaceList.append(" ").append(domainNS1);
        shutdownTmpDir = getResultDir() + "/shutdowntemp";
        Files.createDirectories(Paths.get(shutdownTmpDir));
      }

      if (domain == null) {
        domain = createDomain();
        originalYaml =
            getUserProjectsDir()
                + "/weblogic-domains/"
                + domain.getDomainUid()
                + "/domain.yaml";
        Assertions.assertNotNull(domain);
      }
      domainUid = domain.getDomainUid();
      domainNS = domain.getDomainNs();
    }
  }

  /**
   * Releases k8s cluster lease, archives result, pv directories.
   *
   * @throws Exception exception
   */
  @AfterAll
  public static void staticUnPrepare() throws Exception {
    if (FULLTEST) {
      destroyDomain();
      tearDown(new Object() {
      }.getClass().getEnclosingClass().getSimpleName(), namespaceList.toString());

      LoggerHelper.getLocal().log(Level.INFO, "SUCCESS");
    }
  }

  private Domain createDomain() throws Exception {

    Map<String, Object> domainMap = createDomainMap(getNewSuffixCount(), testClassName);
    domainMap.put("namespace", domainNS1);
    domainMap.put("domainUID", "domainpodsshutdown");
    domainMap.put("initialManagedServerReplicas", new Integer("1"));

    domainUid = (String) domainMap.get("domainUID");
    LoggerHelper.getLocal().log(Level.INFO, "Creating and verifying the domain creation with domainUid: " + domainUid);

    domain = TestUtils.createDomain(domainMap);
    domain.verifyDomainCreated();

    return domain;
  }

  private static void getDefaultShutdownTime() throws Exception {
    terminationDefaultOptionsTime = shutdownServer("managed-server1");
    LoggerHelper.getLocal().log(Level.INFO,
        " termination pod's time with default shutdown options is: "
            + terminationDefaultOptionsTime);
  }

  private static void resetDomainCrd() throws Exception {

    // reset the domain crd
    domain.shutdown();
    LoggerHelper.getLocal().log(Level.INFO, "kubectl apply -f ", originalYaml);
    ExecResult exec = TestUtils.exec("kubectl apply -f " + originalYaml);
    LoggerHelper.getLocal().log(Level.INFO, exec.stdout());
    LoggerHelper.getLocal().log(Level.INFO, "Verifying if the domain is restarted");
    Thread.sleep(10 * 1000);
    // should restart domain
    domain.verifyDomainCreated();
    Assertions.assertTrue(
        checkShutdownUpdatedProp(domainUid + "-admin-server", "30", "false", "Graceful"),
        "Property value was not found in the updated domain crd ");
    Assertions.assertTrue(
        checkShutdownUpdatedProp(domainUid + "-managed-server1", "30", "false", "Graceful"),
        "Property value was not found in the updated domain crd ");
  }

  private static void destroyDomain() throws Exception {
    if (domain != null) {
      TestUtils.deleteWeblogicDomainResources(domain.getDomainUid());
    }
  }

  /**
   * send request to web app deployed on wls.
   *
   * @param testAppPath - URL path for webapp
   * @param domain      - Domain where webapp deployed
   * @param deployApp   - option to build and deployApp
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
    // LoggerHelper.getLocal().log(Level.INFO, "Send a HTTP request: " + curlCmd);
    TestUtils.checkAnyCmdInLoop(curlCmd, "Ending to sleep");
  }

  /**
   * shutdown managed server.
   *
   * @throws Exception exception
   */
  private static long shutdownServer(String serverName) throws Exception {
    long startTime;
    startTime = System.currentTimeMillis();
    String cmd = "kubectl delete pod " + domainUid + "-" + serverName + " -n " + domainNS;
    LoggerHelper.getLocal().log(Level.INFO, "command to shutdown server <" + serverName + "> is: " + cmd);
    ExecResult result = ExecCommand.exec(cmd);
    if (result.exitValue() != 0) {
      terminationTime = 0;
      throw new Exception("FAILURE: command " + cmd + " failed, returned " + result.stderr());
    }
    TestUtils.checkPodCreated(domainUid + "-" + serverName, domainNS);
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

    LoggerHelper.getLocal().log(Level.INFO,
        " Get SHUTDOWN props for " + podName + " in namespace " + " with command: '" + cmd + "'");

    ExecResult result = ExecCommand.exec(cmd.toString());
    String stdout = result.stdout();
    LoggerHelper.getLocal().log(Level.INFO, "Output " + stdout);
    boolean found = false;
    for (String prop : props) {
      if (stdout.contains(prop)) {
        LoggerHelper.getLocal().log(Level.INFO, "Property with value " + prop + " has found");
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
    Assumptions.assumeTrue(FULLTEST);
    String testMethodName = new Object() {
    }.getClass().getEnclosingMethod().getName();
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
      Assertions.assertTrue(checkShutdownUpdatedProp(domainUid + "-admin-server", "Graceful"));
      Assertions.assertTrue(
          checkShutdownUpdatedProp(domainUid + "-managed-server1", "Forced", "160", "true"));
    } finally {
      LoggerHelper.getLocal().log(
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
    Assumptions.assumeTrue(FULLTEST);
    String testMethodName = new Object() {
    }.getClass().getEnclosingMethod().getName();
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
      Assertions.assertTrue(checkShutdownUpdatedProp(domainUid + "-admin-server", "Graceful"));
      Assertions.assertTrue(checkShutdownUpdatedProp(domainUid + "-managed-server1", "Forced"));
    } finally {
      LoggerHelper.getLocal().log(
          Level.INFO, "Reverting back the domain to old crd\n kubectl apply -f {0}", originalYaml);
      resetDomainCrd();
    }
    LoggerHelper.getLocal().log(Level.INFO, "SUCCESS - {0}", testMethodName);
  }

  /**
   * Add shutdown options at domain level and verify all pods are Terminated and recreated.
   *
   * @throws Exception when domain.yaml cannot be read or modified
   */
  @Test
  public void testAddShutdownOptionsToDomain() throws Exception {

    Assumptions.assumeTrue(FULLTEST);
    String testMethodName = new Object() {
    }.getClass().getEnclosingMethod().getName();
    logTestBegin(testMethodName);

    Files.createDirectories(Paths.get(shutdownTmpDir));
    // Modify the original domain yaml to include shutdown options in domain spec node
    DomainCrd crd = new DomainCrd(originalYaml);

    Map<String, Object> shutdownProps = new HashMap();
    shutdownProps.put("timeoutSeconds", 160);
    crd.addShutdownOptionToDomain(shutdownProps);
    try {
      updateCrdYamlVerifyShutdown(crd, 0);
      Assertions.assertTrue(checkShutdownUpdatedProp(domainUid + "-admin-server", "160"));
      Assertions.assertTrue(checkShutdownUpdatedProp(domainUid + "-managed-server1", "160"));
    } finally {
      LoggerHelper.getLocal().log(
          Level.INFO, "Reverting back the domain to old crd\n kubectl apply -f {0}", originalYaml);
      resetDomainCrd();
    }
    LoggerHelper.getLocal().log(Level.INFO, "SUCCESS - {0}", testMethodName);
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

    Assumptions.assumeTrue(FULLTEST);
    String testMethodName = new Object() {
    }.getClass().getEnclosingMethod().getName();
    logTestBegin(testMethodName);

    Files.createDirectories(Paths.get(shutdownTmpDir));
    // Modify the original domain yaml to include shutdown options in domain spec node
    DomainCrd crd = new DomainCrd(originalYaml);

    Map<String, Object> shutdownProps = new HashMap();
    shutdownProps.put("timeoutSeconds", 160);
    shutdownProps.put("ignoreSessions", false);
    crd.addShutDownOptionToMS("managed-server1", shutdownProps);
    long delayTime = 30 * 1000;
    updateCrdYamlVerifyShutdown(crd, delayTime);

    Assertions.assertTrue(
        checkShutdownUpdatedProp(domainUid + "-managed-server1", "160", "false", "Graceful"));
    if (terminationTime < delayTime) {
      LoggerHelper.getLocal().log(Level.INFO, "FAILURE: ignored opened session during shutdown");
      throw new Exception("FAILURE: ignored opened session during shutdown");
    }
    long terminationTimeWithIgnoreSessionFalse = terminationTime;
    LoggerHelper.getLocal().log(Level.INFO,
        " Termination time with ignoreSession=false :" + terminationTimeWithIgnoreSessionFalse);

    shutdownProps = new HashMap();
    shutdownProps.put("timeoutSeconds", 160);
    shutdownProps.put("ignoreSessions", true);

    crd.addShutDownOptionToMS("managed-server1", shutdownProps);
    try {
      updateCrdYamlVerifyShutdown(crd, delayTime);
      Assertions.assertTrue(
          checkShutdownUpdatedProp(domainUid + "-managed-server1", "160", "true", "Graceful"));

      long terminationTimeWithIgnoreSessionTrue = terminationTime;
      LoggerHelper.getLocal().log(Level.INFO,
          " Termination time with ignoreSessions=true :" + terminationTimeWithIgnoreSessionTrue);

      if (terminationTimeWithIgnoreSessionFalse - delayTime
          < terminationTimeWithIgnoreSessionTrue) {
        LoggerHelper.getLocal().log(Level.INFO, "FAILURE: did not ignore opened sessions during shutdown");
        throw new Exception("FAILURE: did not ignore opened sessions during shutdown");
      }
    } finally {
      LoggerHelper.getLocal().log(
          Level.INFO, "Reverting back the domain to old crd\n kubectl apply -f {0}", originalYaml);
      resetDomainCrd();
    }
    LoggerHelper.getLocal().log(Level.INFO, "SUCCESS - {0}", testMethodName);
  }

  /**
   * Add shutdown option Timeout at managed server level and verify all pods are Terminated
   * according to the setting.
   *
   * @throws Exception when domain.yaml cannot be read or modified
   */
  @Test
  public void testAddShutdownOptionsToMsTimeout() throws Exception {

    Assumptions.assumeTrue(FULLTEST);
    String testMethodName = new Object() {
    }.getClass().getEnclosingMethod().getName();
    logTestBegin(testMethodName);

    Files.createDirectories(Paths.get(shutdownTmpDir));
    // Modify the original domain yaml to include shutdown options in domain spec node
    DomainCrd crd = new DomainCrd(originalYaml);

    long delayTime = 30 * 1000;
    // testing timeout
    Map<String, Object> shutdownProps = new HashMap();
    shutdownProps.put("timeoutSeconds", 20);
    shutdownProps.put("ignoreSessions", false);
    crd.addShutDownOptionToMS("managed-server1", shutdownProps);
    try {
      updateCrdYamlVerifyShutdown(crd, delayTime);
      Assertions.assertTrue(
          checkShutdownUpdatedProp(domainUid + "-managed-server1", "20", "false", "Graceful"));
      if (terminationTime > (3 * 20 * 1000)) {
        LoggerHelper.getLocal().log(Level.INFO, "\"FAILURE: ignored timeoutValue during shutdown");
        throw new Exception("FAILURE: ignored timeoutValue during shutdown");
      }
    } finally {
      LoggerHelper.getLocal().log(
          Level.INFO, "Reverting back the domain to old crd\n kubectl apply -f {0}", originalYaml);
      resetDomainCrd();
    }
    LoggerHelper.getLocal().log(Level.INFO, "SUCCESS - {0}", testMethodName);
  }

  /**
   * Add shutdown option Forced at managed server level and verify all pods are Terminated according
   * to the setting.
   *
   * @throws Exception when domain.yaml cannot be read or modified
   */
  @Test
  public void testAddShutdownOptionsToMsForced() throws Exception {

    Assumptions.assumeTrue(FULLTEST);
    String testMethodName = new Object() {
    }.getClass().getEnclosingMethod().getName();
    logTestBegin(testMethodName);

    if (terminationDefaultOptionsTime == 0) {
      getDefaultShutdownTime();
    }
    Files.createDirectories(Paths.get(shutdownTmpDir));
    // Modify the original domain yaml to include shutdown options in domain spec node
    DomainCrd crd = new DomainCrd(originalYaml);

    long delayTime = 30 * 1000;
    // testing timeout
    Map<String, Object> shutdownProps = new HashMap();
    shutdownProps.put("shutdownType", "Forced");
    crd.addShutDownOptionToMS("managed-server1", shutdownProps);
    try {
      updateCrdYamlVerifyShutdown(crd, delayTime);

      Assertions.assertTrue(checkShutdownUpdatedProp(domainUid + "-managed-server1", "Forced"));
      if ((2 * terminationDefaultOptionsTime < terminationTime)) {
        LoggerHelper.getLocal().log(Level.INFO, "\"FAILURE: ignored timeout Forced value during shutdown");
        throw new Exception("FAILURE: ignored timeout Forced during shutdown");
      }
    } finally {
      LoggerHelper.getLocal().log(
          Level.INFO, "Reverting back the domain to old crd\n kubectl apply -f {0}", originalYaml);
      resetDomainCrd();
    }
    LoggerHelper.getLocal().log(Level.INFO, "SUCCESS - {0}", testMethodName);
  }

  /**
   * Add shutdown env vars at domain spec level and verify the pod are Terminated and recreated.
   *
   * @throws Exception when domain.yaml cannot be read or modified to include the
   */
  @Test
  public void testAddEnvShutdownOptions() throws Exception {
    Assumptions.assumeTrue(FULLTEST);
    String testMethodName = new Object() {
    }.getClass().getEnclosingMethod().getName();
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
      LoggerHelper.getLocal().log(
          Level.INFO, "Reverting back the domain to old crd\n kubectl apply -f {0}", originalYaml);
      resetDomainCrd();
    }
    LoggerHelper.getLocal().log(Level.INFO, "SUCCESS - {0}", testMethodName);
  }

  /**
   * Add shutdown env vars at domain spec level and managed server level,verify managed server
   * override domain level.
   *
   * @throws Exception when domain.yaml cannot be read or modified
   */
  @Test
  public void testShutdownOptionsOverrideViaEnv() throws Exception {
    Assumptions.assumeTrue(FULLTEST);
    String testMethodName = new Object() {
    }.getClass().getEnclosingMethod().getName();
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
      LoggerHelper.getLocal().log(
          Level.INFO, "Reverting back the domain to old crd\n kubectl apply -f {0}", originalYaml);
      resetDomainCrd();
    }

    LoggerHelper.getLocal().log(Level.INFO, "SUCCESS - {0}", testMethodName);
  }

  /**
   * Add shutdown options at cluster spec level and the managed server1 level,verify managed server
   * override cluster level.
   *
   * @throws Exception when domain.yaml cannot be read or modified
   */
  @Test
  public void testShutdownOptionsOverrideClusterLevel() throws Exception {
    Assumptions.assumeTrue(FULLTEST);
    String testMethodName = new Object() {
    }.getClass().getEnclosingMethod().getName();
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
      LoggerHelper.getLocal().log(
          Level.INFO, "Reverting back the domain to old crd\n kubectl apply -f {0}", originalYaml);
      resetDomainCrd();
    }
    LoggerHelper.getLocal().log(Level.INFO, "SUCCESS - {0}", testMethodName);
  }

  private void updateCrdYamlVerifyShutdown(DomainCrd crd, long delayTime) throws Exception {
    String modYaml = crd.getYamlTree();
    LoggerHelper.getLocal().log(Level.INFO, modYaml);
    terminationTime = 0;
    // change version to restart domain
    Map<String, String> domain = new HashMap();
    domain.put("restartVersion", "v1." + podVer);
    podVer++;
    crd.addObjectNodeToDomain(domain);
    // Write the modified yaml to a new file
    Path path = Paths.get(shutdownTmpDir, "shutdown.managed.yaml");
    LoggerHelper.getLocal().log(Level.INFO, "Path of the modified domain.yaml :{0}", path.toString());
    Charset charset = StandardCharsets.UTF_8;
    Files.write(path, modYaml.getBytes(charset));
    modifiedYaml = path.toString();
    // Apply the new yaml to update the domain crd
    this.domain.shutdown();
    LoggerHelper.getLocal().log(Level.INFO, "kubectl apply -f {0}", path.toString());
    ExecResult exec = TestUtils.exec("kubectl apply -f " + path.toString());
    LoggerHelper.getLocal().log(Level.INFO, exec.stdout());

    LoggerHelper.getLocal().log(Level.INFO, "Verifying if the domain is restarted");
    this.domain.verifyDomainCreated();
    // invoke servlet to keep sessions opened, terminate pod and check shutdown time
    if (delayTime > 0) {
      String testAppPath = "httpsessionreptestapp/CounterServlet?delayTime=" + delayTime;
      callWebApp(testAppPath, this.domain, true);
      SessionDelayThread sessionDelay = new SessionDelayThread(delayTime, this.domain);
      new Thread(sessionDelay).start();
      // sleep 5 secs before shutdown
      Thread.sleep(5 * 1000);
    }
    terminationTime = shutdownServer("managed-server1");
    LoggerHelper.getLocal().log(Level.INFO, " termination time: " + terminationTime);
  }

  /**
   * call operator to scale to specified number of replicas.
   *
   * @param replicas - number of managed servers
   * @throws Exception exception
   */
  private void scaleCluster(int replicas) throws Exception {
    LoggerHelper.getLocal().log(Level.INFO, "Scale up/down to " + replicas + " managed servers");
    operator1.scale(domain.getDomainUid(), domain.getClusterName(), replicas);
  }
}

