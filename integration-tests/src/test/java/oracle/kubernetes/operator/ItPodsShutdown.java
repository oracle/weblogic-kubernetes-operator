// Copyright (c) 2019, 2020, Oracle Corporation and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;

import oracle.kubernetes.operator.utils.Domain;
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
  private static Operator operator1 = null;
  private static String testClassName;
  private static String domainNSShutOpCluster = "domainns-sopcluster";
  private static String domainNSShutOpMS = "domainns-sopms";
  private static String domainNSShutOpDomain = "domainns-sopdomain";
  private static String domainNSShutOpMSIgnoreSessions = "domainns-sopmsignores";
  private static String domainNSShutOpMSTimeout = "domainns-sopmstimeout";
  private static String domainNSShutOpMSForced = "domainns-sopmsforced";
  private static String domainNSShutOpEnv = "domainns-sopenv";
  private static String domainNSShutOpOverrideViaEnv = "domainns-sopoverenv";
  private static String domainNSShutOpOverrideViaCluster = "domainns-sopovercluster";
  private static StringBuffer namespaceList;

  /**
   * This method gets called only once before any of the test methods are executed. It does the
   * initialization of the integration test properties defined in OperatorIT.properties and setting
   * the resultRoot, pvRoot and projectRoot attributes.
   *
   * @throws Exception If initializing of properties failed.
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
   * @throws Exception If result/pv/operator/domain creation fails
   */
  @BeforeEach
  public void prepare() throws Exception {
    // initialize test properties and create the directories
    if (FULLTEST) {
      LoggerHelper.getLocal().log(Level.INFO, "Checking if operator1 and domain are running, if not creating");
      createResultAndPvDirs(testClassName);
      // create operator1
      if (operator1 == null) {
        ArrayList<String> domainNamespaces = new ArrayList<String>();
        domainNamespaces.add(domainNSShutOpCluster);
        domainNamespaces.add(domainNSShutOpDomain);
        domainNamespaces.add(domainNSShutOpEnv);
        domainNamespaces.add(domainNSShutOpMS);
        domainNamespaces.add(domainNSShutOpMSForced);
        domainNamespaces.add(domainNSShutOpMSIgnoreSessions);
        domainNamespaces.add(domainNSShutOpMSTimeout);
        domainNamespaces.add(domainNSShutOpOverrideViaCluster);
        domainNamespaces.add(domainNSShutOpOverrideViaEnv);
        Map<String, Object> operatorMap = createOperatorMap(getNewSuffixCount(), true, testClassName);
        operatorMap.put("domainNamespaces", domainNamespaces);
        operator1 = TestUtils.createOperator(operatorMap, Operator.RestCertType.SELF_SIGNED);
        Assertions.assertNotNull(operator1);
        namespaceList.append((String)operatorMap.get("namespace"));
        namespaceList.append(" ")
            .append(domainNSShutOpOverrideViaCluster)
            .append(" ")
            .append(domainNSShutOpMS)
            .append(" ")
            .append(domainNSShutOpMSForced)
            .append(" ")
            .append(domainNSShutOpMSIgnoreSessions)
            .append(" ")
            .append(domainNSShutOpMSTimeout)
            .append(" ")
            .append(domainNSShutOpEnv)
            .append(" ")
            .append(domainNSShutOpDomain)
            .append(" ")
            .append(domainNSShutOpOverrideViaEnv)
            .append(" ")
            .append(domainNSShutOpCluster);
      }
    }
  }

  /**
   * Releases k8s cluster lease, archives result, pv directories.
   *
   * @throws Exception If failed to delete the created objects or archive results
   */
  @AfterAll
  public static void staticUnPrepare() throws Exception {
    if (FULLTEST) {
      tearDown(new Object() {
      }.getClass().getEnclosingClass().getSimpleName(), namespaceList.toString());

      LoggerHelper.getLocal().log(Level.INFO, "SUCCESS");
    }
  }

  private Domain createDomain(String domainNS, Map<String, Object> shutdownProps) throws Exception {

    Map<String, Object> domainMap = createDomainMap(getNewSuffixCount(), testClassName);
    domainMap.put("namespace", domainNS);
    domainMap.put("domainUID", domainNS);
    domainMap.put("initialManagedServerReplicas", new Integer("1"));
    domainMap.put("shutdownOptionsOverrides",shutdownProps);
    LoggerHelper.getLocal().log(Level.INFO, "Creating and verifying the domain creation with domainUid: " + domainNS);
    Domain domain = TestUtils.createDomain(domainMap);
    domain.verifyDomainCreated();
    return domain;
  }

  /**
   * Send request to web app deployed on wls.
   *
   * @param testAppPath - URL path for webapp
   * @param domain      - Domain where webapp deployed
   * @throws Exception  - Fails if can't find expected output from app
   */
  public static void callWebApp(String testAppPath, Domain domain)
      throws Exception {

    String nodePortHost = domain.getHostNameForCurl();
    int nodePort = domain.getLoadBalancerWebPort();

    StringBuffer webServiceUrl = new StringBuffer("curl --silent --noproxy '*' ");
    webServiceUrl
        .append(" -H 'host: ")
        .append(domain.getDomainUid())
        .append(".org' ")
        .append(" http://")
        .append(nodePortHost)
        .append(":")
        .append(nodePort)
        .append("/")
        .append(testAppPath);

    // Send a HTTP request to keep open session
    String curlCmd = webServiceUrl.toString();
    ExecCommand.exec(curlCmd);
    //TestUtils.checkAnyCmdInLoop(curlCmd, "Ending to sleep");
  }

  /**
   * Shutdown managed server and returns spent shutdown time.
   *
   * @throws Exception If failed to shutdown the server.
   */
  private static long shutdownServer(String serverName, String domainNS, String domainUid) throws Exception {
    long startTime;
    startTime = System.currentTimeMillis();
    String cmd = "kubectl delete pod " + domainUid + "-" + serverName + " -n " + domainNS;
    LoggerHelper.getLocal().log(Level.INFO, "command to shutdown server <" + serverName + "> is: " + cmd);
    ExecResult result = ExecCommand.exec(cmd);
    long terminationTime = 0;
    if (result.exitValue() != 0) {
      throw new Exception("FAILURE: command " + cmd + " failed, returned " + result.stderr());
    }
    TestUtils.checkPodCreated(domainUid + "-" + serverName, domainNS);
    long endTime = System.currentTimeMillis();
    terminationTime = endTime - startTime;
    return terminationTime;
  }

  private static boolean checkShutdownUpdatedProp(String podName, String domainNS, String... props)
      throws Exception {

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
   * Start domain with added shutdown options at the domain level
   * and verify values are propagated to server level.
   *
   * @throws Exception If domain cannot be started or failed to verify shutdown options
   */
  @Test
  public void testAddShutdownOptionsToDomain() throws Exception {

    Assumptions.assumeTrue(FULLTEST);
    String testMethodName = new Object() {
    }.getClass().getEnclosingMethod().getName();
    logTestBegin(testMethodName);
    Map<String, Object> shutdownProps = new HashMap<>();
    Map<String, Object> shutdownDomainProps = new HashMap();
    shutdownProps.put("timeoutSeconds", 160);
    shutdownDomainProps.put("domain",shutdownProps);
    Domain domain = null;
    try {
      domain = createDomain(domainNSShutOpDomain, shutdownDomainProps);
      Assertions.assertNotNull(domain, "Domain "
          + domainNSShutOpDomain
          + "failed to create");
      Assertions.assertTrue(checkShutdownUpdatedProp(domain.getDomainUid()
          + "-admin-server", domain.getDomainNs(),"160"),
          domain.getDomainUid()
              + "-admin-server"
              + "shutdown property for timeoutseconds does not match the expected value 160");
      Assertions.assertTrue(checkShutdownUpdatedProp(domain.getDomainUid()
          + "-managed-server1",domain.getDomainNs(), "160"),
          domain.getDomainUid()
              + "-managed-server1: "
              + "shutdown property for timeoutseconds does not match the expected value 160");
    } finally {
      if (domain != null) {
        TestUtils.deleteWeblogicDomainResources(domain.getDomainUid());
      }
    }
    LoggerHelper.getLocal().log(Level.INFO, "SUCCESS - {0}", testMethodName);
  }

  /**
   * Start domain with added shutdown options at the managed server level
   * and verify values are propagated to specified server level but effecting admin setting.
   *
   * @throws Exception If domain cannot be started or failed to verify shutdown options
   */
  @Test
  public void testAddShutdownOptionsToMS() throws Exception {
    Assumptions.assumeTrue(FULLTEST);
    String testMethodName = new Object() {
    }.getClass().getEnclosingMethod().getName();
    logTestBegin(testMethodName);

    Map<String, Object> shutdownProps = new HashMap();
    shutdownProps.put("timeoutSeconds", 160);
    shutdownProps.put("shutdownType", "Forced");
    shutdownProps.put("ignoreSessions", true);
    List<Map<String,Object>> shutdownPropsMSs = new ArrayList<>();
    Map<String, Object> shutdownPropsMyMS = new HashMap();
    shutdownPropsMyMS.put("managed-server1", shutdownProps);
    shutdownPropsMSs.add(shutdownPropsMyMS);
    Map<String, Object> shutdownPropOpt = new HashMap();
    shutdownPropOpt.put("server",shutdownPropsMSs);
    Domain domain = null;
    try {
      domain = createDomain(domainNSShutOpMS, shutdownPropOpt);
      Assertions.assertNotNull(domain, "domain "
          + domainNSShutOpMS
          + " failed to create, returns null");
      Assertions.assertTrue(checkShutdownUpdatedProp(domain.getDomainUid()
          + "-admin-server", "Graceful"),
          domain.getDomainUid()
              + "-admin-server: "
          + " shutdown property does not match the expected : shutdownType=Graceful"
      );
      Assertions.assertTrue(
          checkShutdownUpdatedProp(domain.getDomainUid()
              + "-managed-server1", domain.getDomainNs(),"Forced", "160", "true"),
          domain.getDomainUid()
              + "-managed-server1 :"
              + " shutdown properties don't not match the expected : "
              + "shutdownType=Forced, timeoutSeconds=160, ignoreSessions=true");
    } finally {
      LoggerHelper.getLocal().log(
          Level.INFO, " Deleting domain " + domain.getDomainUid());
      if (domain != null) {
        TestUtils.deleteWeblogicDomainResources(domain.getDomainUid());
      }
    }
    LoggerHelper.getLocal().log(Level.INFO, "SUCCESS - {0}", testMethodName);
  }

  /**
   * Start domain with added shutdown options at the cluster level
   * and verify values are propagated to all managed servers in the cluster.
   *
   * @throws Exception If domain cannot be started or failed to verify shutdown options
   */
  @Test
  public void testAddShutdownOptionToCluster() throws Exception {
    Assumptions.assumeTrue(FULLTEST);
    String testMethodName = new Object() {
    }.getClass().getEnclosingMethod().getName();
    logTestBegin(testMethodName);

    Map<String, Object> shutdownProps = new HashMap();
    shutdownProps.put("timeoutSeconds", 60);
    shutdownProps.put("shutdownType", "Forced");
    shutdownProps.put("ignoreSessions", true);
    List<Map<String,Object>> shutdownPropsClusters = new ArrayList<>();
    Map<String, Object> shutdownPropsMyCluster = new HashMap();
    shutdownPropsMyCluster.put("cluster-1", shutdownProps);
    shutdownPropsClusters.add(shutdownPropsMyCluster);
    Map<String, Object> shutdownPropOpt = new HashMap();
    shutdownPropOpt.put("cluster",shutdownPropsClusters);
    Domain domain = null;
    try {
      domain = createDomain(domainNSShutOpCluster,shutdownPropOpt);
      Assertions.assertNotNull(domain,
          domainNSShutOpCluster
              + " failed to create, returns null");
      Assertions.assertTrue(checkShutdownUpdatedProp(domain.getDomainUid()
          + "-admin-server", domain.getDomainNs(),"Graceful"),
          domain.getDomainUid()
              + "-admin-server: "
              + " shutdown property does not match the expected : shutdownType=Graceful");
      Assertions.assertTrue(checkShutdownUpdatedProp(domain.getDomainUid()
          + "-managed-server1", domain.getDomainNs(),"Forced"),
          domain.getDomainUid()
              + "-managed-server1: "
              + " shutdown property does not match the expected : shutdownType=Forced");
    } finally {
      LoggerHelper.getLocal().log(
          Level.INFO, "Deleting domain");
      if (domain != null) {
        TestUtils.deleteWeblogicDomainResources(domain.getDomainUid());
      }
    }
    LoggerHelper.getLocal().log(Level.INFO, "SUCCESS - {0}", testMethodName);
  }

  /**
   * Start domain with added shutdown options at the managed server level with IgnoreSessions=false
   * and verify values are propagated to specified server level. Server shuts down only after sessions are ended.
   *
   * @throws Exception If domain cannot be started or failed to verify shutdown options
   */
  @Test
  public void testAddShutdownOptionsToMsIgnoreSessions() throws Exception {

    Assumptions.assumeTrue(FULLTEST);
    String testMethodName = new Object() {
    }.getClass().getEnclosingMethod().getName();
    logTestBegin(testMethodName);

    Map<String, Object> shutdownProps = new HashMap();
    shutdownProps.put("timeoutSeconds", 160);
    shutdownProps.put("ignoreSessions", false);
    List<Map<String,Object>> shutdownPropsMSs = new ArrayList<>();
    Map<String, Object> shutdownPropsMyMS = new HashMap();
    shutdownPropsMyMS.put("managed-server1", shutdownProps);
    shutdownPropsMSs.add(shutdownPropsMyMS);
    Map<String, Object> shutdownPropOpt = new HashMap();
    shutdownPropOpt.put("server",shutdownPropsMSs);
    Domain domain = null;
    try {
      domain = createDomain(domainNSShutOpMSIgnoreSessions, shutdownPropOpt);
      Assertions.assertNotNull(domain,
          domainNSShutOpMSIgnoreSessions
              + " failed to create, returns null");
      Assertions.assertTrue(
          checkShutdownUpdatedProp(domain.getDomainUid()
              + "-managed-server1", domain.getDomainNs(),"160", "false"),
          domain.getDomainUid()
              + "-managed-server1: "
              + " shutdown property does not match the expected :"
              + " timeoutSeconds=160, ignoreSessions=false");
      domain.buildDeployJavaAppInPod(
          testAppName, scriptName, BaseTest.getUsername(), BaseTest.getPassword());
      domain.callWebAppAndVerifyLoadBalancing(testAppName + "/CounterServlet?", false);

      long delayTime = 80 * 1000;
      long terminationTimeWithIgnoreSessionFalse = verifyShutdown(delayTime, domain);

      if (terminationTimeWithIgnoreSessionFalse < delayTime) {
        LoggerHelper.getLocal().log(Level.INFO, "FAILURE: ignored opened session during shutdown");
        throw new Exception("FAILURE: ignored opened session during shutdown");
      }
      LoggerHelper.getLocal().log(Level.INFO,
          " Termination time with ignoreSession=false :" + terminationTimeWithIgnoreSessionFalse);
    } finally {
      if (domain != null) {
        TestUtils.deleteWeblogicDomainResources(domain.getDomainUid());
      }
    }
    LoggerHelper.getLocal().log(Level.INFO, "SUCCESS - {0}", testMethodName);
  }

  /**
   * Add shutdown option Timeout at managed server level and verify all pods are Terminated
   * according to the setting.
   *
   * @throws Exception If domain cannot be started or failed to verify shutdown options
   */
  @Test
  public void testAddShutdownOptionsToMsTimeout() throws Exception {

    Assumptions.assumeTrue(FULLTEST);
    String testMethodName = new Object() {
    }.getClass().getEnclosingMethod().getName();
    logTestBegin(testMethodName);
    Map<String, Object> shutdownProps = new HashMap();
    shutdownProps.put("timeoutSeconds", 10);
    shutdownProps.put("ignoreSessions", false);
    List<Map<String,Object>> shutdownPropsMSs = new ArrayList<>();
    Map<String, Object> shutdownPropsMyMS = new HashMap();
    shutdownPropsMyMS.put("managed-server1", shutdownProps);
    shutdownPropsMSs.add(shutdownPropsMyMS);
    Map<String, Object> shutdownPropOpt = new HashMap();
    shutdownPropOpt.put("server",shutdownPropsMSs);
    Domain domain = null;
    try {
      domain = createDomain(domainNSShutOpMSTimeout, shutdownPropOpt);
      Assertions.assertNotNull(domain,
          domainNSShutOpMSTimeout
              + " failed to create, returns null");
      Assertions.assertTrue(
          checkShutdownUpdatedProp(domain.getDomainUid() + "-managed-server1", domain.getDomainNs(),"10", "false"),
          domain.getDomainUid()
              + "-managed-server1: "
              + " shutdown property does not match the expected :"
              + " timeoutSeconds=10, ignoreSessions=false");
      domain.buildDeployJavaAppInPod(
          testAppName, scriptName, BaseTest.getUsername(), BaseTest.getPassword());
      domain.callWebAppAndVerifyLoadBalancing(testAppName + "/CounterServlet?", false);
      long delayTime = 80 * 1000;
      // testing timeout
      long terminationTime = verifyShutdown(delayTime, domain);

      if (terminationTime > delayTime) {
        LoggerHelper.getLocal().log(Level.INFO, "\"FAILURE: ignored timeoutValue during shutdown");
        throw new Exception("FAILURE: ignored timeoutValue during shutdown");
      }
    } finally {
      if (domain != null) {
        TestUtils.deleteWeblogicDomainResources(domain.getDomainUid());
      }
    }
    LoggerHelper.getLocal().log(Level.INFO, "SUCCESS - {0}", testMethodName);
  }

  /**
   * Add shutdown option Forced at managed server level and verify all pods are Terminated according
   * to the setting.
   *
   * @throws Exception If domain cannot be started or failed to verify shutdown options
   */
  @Test
  public void testAddShutdownOptionsToMsForced() throws Exception {

    Assumptions.assumeTrue(FULLTEST);
    String testMethodName = new Object() {
    }.getClass().getEnclosingMethod().getName();
    logTestBegin(testMethodName);
    Map<String, Object> shutdownProps = new HashMap();
    shutdownProps.put("shutdownType", "Forced");
    List<Map<String,Object>> shutdownPropsMSs = new ArrayList<>();
    Map<String, Object> shutdownPropsMyMS = new HashMap();
    shutdownPropsMyMS.put("managed-server1", shutdownProps);
    shutdownPropsMSs.add(shutdownPropsMyMS);
    Map<String, Object> shutdownPropOpt = new HashMap();
    shutdownPropOpt.put("server",shutdownPropsMSs);
    Domain domain = null;
    try {
      domain = createDomain(domainNSShutOpMSForced, shutdownPropOpt);
      Assertions.assertNotNull(domain,
          domainNSShutOpMSForced
              + " failed to create, returns null");
      Assertions.assertTrue(checkShutdownUpdatedProp(domain.getDomainUid()
          + "-managed-server1",domain.getDomainNs(), "Forced"),
          domain.getDomainUid()
              + "-managed-server1 :"
              + " shutdown properties don't not match the expected : "
              + "shutdownType=Forced");
      domain.buildDeployJavaAppInPod(
          testAppName, scriptName, BaseTest.getUsername(), BaseTest.getPassword());
      domain.callWebAppAndVerifyLoadBalancing(testAppName + "/CounterServlet?", false);
      long delayTime = 80 * 1000;
      // testing Forced
      long terminationTime = verifyShutdown(delayTime, domain);

      if ((delayTime < terminationTime)) {
        LoggerHelper.getLocal().log(Level.INFO, "\"FAILURE: ignored timeout Forced value during shutdown");
        throw new Exception("FAILURE: ignored timeout Forced during shutdown");
      }
    } finally {
      if (domain != null) {
        TestUtils.deleteWeblogicDomainResources(domain.getDomainUid());
      }
    }
    LoggerHelper.getLocal().log(Level.INFO, "SUCCESS - {0}", testMethodName);
  }

  /**
   * Add shutdown env vars at domain spec level and verify the pod are Terminated and recreated.
   *
   * @throws Exception If domain cannot be started or failed to verify shutdown options
   */
  @Test
  public void testAddShutdownOptionsEnv() throws Exception {
    Assumptions.assumeTrue(FULLTEST);
    String testMethodName = new Object() {
    }.getClass().getEnclosingMethod().getName();
    logTestBegin(testMethodName);

    Map<String, String> shutdownEnvProps = new HashMap();
    shutdownEnvProps.put("SHUTDOWN_TYPE", "Forced");
    shutdownEnvProps.put("SHUTDOWN_TIMEOUT", "60");
    shutdownEnvProps.put("SHUTDOWN_IGNORE_SESSIONS", "false");
    Map<String, Object> shutdownProps = new HashMap<>();
    shutdownProps.put("env",shutdownEnvProps);
    Domain domain = null;
    try {
      domain = createDomain(domainNSShutOpEnv,shutdownProps);
      Assertions.assertNotNull(domain,
          domainNSShutOpEnv
              + " failed to create, returns null");
      Assertions.assertTrue(checkShutdownUpdatedProp(domain.getDomainUid()
          + "-managed-server1", domain.getDomainNs(), "Forced", "60", "false"),
          domain.getDomainUid()
              + "-managed-server1 :"
              + " shutdown properties don't not match the expected : "
              + "shutdownType=Forced, timeoutSeconds=60, ignoreSessions=false");
      Assertions.assertTrue(checkShutdownUpdatedProp(domain.getDomainUid()
          + "-admin-server", domain.getDomainNs(),"Forced", "60", "false"),
          domain.getDomainUid()
              + "-admin-server :"
              + " shutdown properties don't not match the expected : "
              + "shutdownType=Forced, timeoutSeconds=60, ignoreSessions=false");
    } finally {
      if (domain != null) {
        TestUtils.deleteWeblogicDomainResources(domain.getDomainUid());
      }
    }
    LoggerHelper.getLocal().log(Level.INFO, "SUCCESS - {0}", testMethodName);
  }

  /**
   * Add shutdown env vars at domain spec level and managed server level,verify env override server level.
   *
   * @throws Exception If domain cannot be started or failed to verify shutdown options
   */
  @Test
  public void testShutdownOptionsOverrideViaEnv() throws Exception {
    Assumptions.assumeTrue(FULLTEST);
    String testMethodName = new Object() {
    }.getClass().getEnclosingMethod().getName();
    logTestBegin(testMethodName);
    Map<String, String> shutdownEnvProps = new HashMap();
    shutdownEnvProps.put("SHUTDOWN_TYPE", "Forced");
    shutdownEnvProps.put("SHUTDOWN_TIMEOUT", "60");
    Map<String, Object> shutdownPropsOpt = new HashMap<>();
    shutdownPropsOpt.put("env",shutdownEnvProps);


    Map<String, Object> shutdownProps = new HashMap();
    shutdownProps.put("timeoutSeconds", 20);
    shutdownProps.put("shutdownType", "Graceful");
    shutdownProps.put("ignoreSessions", false);
    List<Map<String,Object>> shutdownPropsMSs = new ArrayList<>();
    Map<String, Object> shutdownPropsMyMS = new HashMap();
    shutdownPropsMyMS.put("managed-server1", shutdownProps);
    shutdownPropsMSs.add(shutdownPropsMyMS);
    shutdownPropsOpt.put("server",shutdownPropsMSs);

    Domain domain = null;
    try {
      domain = createDomain(domainNSShutOpOverrideViaEnv,shutdownPropsOpt);
      Assertions.assertNotNull(domain,
          domainNSShutOpOverrideViaEnv
              + " failed to create, returns null");
      Assertions.assertTrue(checkShutdownUpdatedProp(domain.getDomainUid()
              + "-managed-server1", domain.getDomainNs(), "Forced", "60"),
          domain.getDomainUid()
              + "-managed-server1 :"
              + " shutdown properties don't not match the expected : "
              + "shutdownType=Forced, timeoutSeconds=60");;
      Assertions.assertTrue(checkShutdownUpdatedProp(domain.getDomainUid()
              + "-admin-server", domain.getDomainNs(),"Forced", "60"),
          domain.getDomainUid()
              + "-admin-server :"
              + " shutdown properties don't not match the expected : "
              + "shutdownType=Forced, timeoutSeconds=60");

    } finally {
      if (domain != null) {
        TestUtils.deleteWeblogicDomainResources(domain.getDomainUid());
      }
    }
    LoggerHelper.getLocal().log(Level.INFO, "SUCCESS - {0}", testMethodName);
  }

  /**
   * Add shutdown options at cluster spec level and the managed server1 level,verify managed server
   * override cluster level.
   *
   * @throws Exception If domain fails to start or can't verify the expected behavior
   */
  @Test
  public void testShutdownOptionsOverrideClusterLevel() throws Exception {
    Assumptions.assumeTrue(FULLTEST);
    String testMethodName = new Object() {
    }.getClass().getEnclosingMethod().getName();
    logTestBegin(testMethodName);

    Map<String, Object> shutdownProps = new HashMap();
    shutdownProps.put("timeoutSeconds", 20);
    shutdownProps.put("shutdownType", "Forced");
    shutdownProps.put("ignoreSessions", false);
    List<Map<String,Object>> shutdownPropsClusters = new ArrayList<>();
    Map<String, Object> shutdownPropsMyCluster = new HashMap();
    shutdownPropsMyCluster.put("cluster-1", shutdownProps);
    shutdownPropsClusters.add(shutdownPropsMyCluster);
    Map<String, Object> shutdownPropOpt = new HashMap();
    shutdownPropOpt.put("cluster",shutdownPropsClusters);

    Map<String, Object> shutdownProps1 = new HashMap();
    shutdownProps1.put("shutdownType", "Graceful");
    List<Map<String,Object>> shutdownPropsMSs = new ArrayList<>();
    Map<String, Object> shutdownPropsMyMS = new HashMap();
    shutdownPropsMyMS.put("managed-server1", shutdownProps1);
    shutdownPropsMSs.add(shutdownPropsMyMS);
    shutdownPropOpt.put("server",shutdownPropsMSs);

    Domain domain = null;
    try {
      domain = createDomain(domainNSShutOpOverrideViaCluster, shutdownPropOpt);
      Assertions.assertNotNull(domain,
          domainNSShutOpOverrideViaCluster
              + " failed to create, returns null");
      // scale up to 2 replicas to check both managed servers in the cluster
      scaleCluster(2, domain);
      Assertions.assertTrue(checkShutdownUpdatedProp(domain.getDomainUid()
              + "-managed-server1", domain.getDomainNs(),"Graceful"),
          domain.getDomainUid()
              + "-managed-server1 :"
              + " shutdown properties don't not match the expected : "
              + "shutdownType=Graceful");
      Assertions.assertTrue(checkShutdownUpdatedProp(domain.getDomainUid()
              + "-managed-server2", domain.getDomainNs(),"Forced"),
          domain.getDomainUid()
              + "-managed-server2 :"
              + " shutdown properties don't not match the expected : "
              + "shutdownType=Forced");
    } finally {
      if (domain != null) {
        TestUtils.deleteWeblogicDomainResources(domain.getDomainUid());
      }
    }
    LoggerHelper.getLocal().log(Level.INFO, "SUCCESS - {0}", testMethodName);
  }

  private long verifyShutdown(long delayTime, Domain domain) throws Exception {

    // invoke servlet to keep sessions opened, terminate pod and check shutdown time
    if (delayTime > 0) {
      SessionDelayThread sessionDelay = new SessionDelayThread(delayTime, domain);
      new Thread(sessionDelay).start();
      // sleep 5 secs before shutdown
      Thread.sleep(5 * 1000);
    }
    long terminationTime = shutdownServer("managed-server1", domain.getDomainNs(), domain.getDomainUid());
    LoggerHelper.getLocal().log(Level.INFO, " termination time: " + terminationTime);
    return terminationTime;
  }

  /**
   * Call operator to scale to specified number of replicas.
   *
   * @param replicas - number of managed servers
   * @throws Exception If fails to scale
   */
  private void scaleCluster(int replicas, Domain domain) throws Exception {
    LoggerHelper.getLocal().log(Level.INFO, "Scale up/down to " + replicas + " managed servers");
    operator1.scale(domain.getDomainUid(), domain.getClusterName(), replicas);
  }
}

