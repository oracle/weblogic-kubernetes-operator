// Copyright (c) 2019, 2020, Oracle Corporation and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;

import oracle.kubernetes.operator.utils.Domain;
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
public class ItPodsShutdownMS extends ShutdownOptionsBase {

  private static Operator operator1 = null;
  private static String testClassName;
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
        ArrayList<String> targetDomainsNS = new ArrayList<String>();
        targetDomainsNS.add(domainNSShutOpMS);
        targetDomainsNS.add(domainNSShutOpMSForced);
        targetDomainsNS.add(domainNSShutOpMSIgnoreSessions);
        targetDomainsNS.add(domainNSShutOpMSTimeout);
        targetDomainsNS.add(domainNSShutOpOverrideViaCluster);
        Map<String, Object> operatorMap = createOperatorMap(getNewSuffixCount(), true, testClassName);
        operatorMap.put("domainNamespaces",targetDomainsNS);
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
            .append(domainNSShutOpMSTimeout);
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

