// Copyright (c) 2020, Oracle Corporation and/or its affiliates.
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
 * This class is used to test Shutdown Properties at Domain, Cluster, Env level.
 */
@TestMethodOrder(Alphanumeric.class)
public class ItPodsShutdownOptionsEnvDomainCluster extends ShutdownOptionsBase {

  private static Operator operator1 = null;
  private static String testClassName;
  private static StringBuffer namespaceList;

  /**
   * This method gets called only once before any of the test methods are executed. It does the
   * initialization of the integration test properties defined in OperatorIT.properties and setting
   * the resultRoot, pvRoot and projectRoot attributes.
   *
   * @throws Exception if initialization of properties failed.
   */
  @BeforeAll
  public static void staticPrepare() throws Exception {
    Assumptions.assumeTrue(FULLTEST);
    namespaceList = new StringBuffer();
    testClassName = new Object() {
    }.getClass().getEnclosingClass().getSimpleName();
    initialize(APP_PROPS_FILE, testClassName);
  }

  /**
   * This method gets called before every test. It creates the result/pv root directories
   * for the test. Creates the operator and domain if its not running.
   *
   * @throws Exception if result/pv/operator/domain creation fails
   */
  @BeforeEach
  public void prepare() throws Exception {
    // initialize test properties and create the directories
    Assumptions.assumeTrue(FULLTEST);
    LoggerHelper.getLocal().log(Level.INFO, "Checking if operator1 and domain are running, if not creating");
    createResultAndPvDirs(testClassName);
    // create operator1
    if (operator1 == null) {
      ArrayList<String> targetDomainsNS = new ArrayList<String>();
      targetDomainsNS.add(domainNSShutOpCluster);
      targetDomainsNS.add(domainNSShutOpDomain);
      targetDomainsNS.add(domainNSShutOpEnv);
      targetDomainsNS.add(domainNSShutOpOverrideViaEnv);
      Map<String, Object> operatorMap = createOperatorMap(getNewSuffixCount(), true, testClassName);
      operatorMap.put("domainNamespaces",targetDomainsNS);
      operator1 = TestUtils.createOperator(operatorMap, Operator.RestCertType.SELF_SIGNED);
      Assertions.assertNotNull(operator1);
      namespaceList.append((String)operatorMap.get("namespace"));
      namespaceList.append(" ")
          .append(domainNSShutOpEnv)
          .append(" ")
          .append(domainNSShutOpDomain)
          .append(" ")
          .append(domainNSShutOpOverrideViaEnv)
          .append(" ")
          .append(domainNSShutOpCluster);
    }
  }

  /**
   * Releases k8s cluster lease, archives result, pv directories.
   *
   * @throws Exception if failed to delete the created objects or archive results.
   */
  @AfterAll
  public static void staticUnPrepare() throws Exception {
    Assumptions.assumeTrue(FULLTEST);
    tearDown(new Object() {
    }.getClass().getEnclosingClass().getSimpleName(), namespaceList.toString());
    LoggerHelper.getLocal().log(Level.INFO, "SUCCESS - tearDown");
  }

  /**
   * Start domain with added shutdown options at the domain level
   * and verify values are propagated to server level.
   *
   * @throws Exception if domain cannot be started or failed to verify shutdown options.
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
          + "failed to create domain " + domainNSShutOpDomain);
      Assertions.assertTrue(checkShutdownProp(domain.getDomainUid()
              + "-admin-server", domain.getDomainNs(),"160"),
          domain.getDomainUid()
              + "-admin-server"
              + "shutdown property for timeoutseconds does not match the expected value 160");
      Assertions.assertTrue(checkShutdownProp(domain.getDomainUid()
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
   * Start domain with added shutdown options at the cluster level
   * and verify values are propagated to all managed servers in the cluster.
   *
   * @throws Exception if domain cannot be started or failed to verify shutdown options.
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
              + " failed to create domain " + domainNSShutOpCluster);
      Assertions.assertTrue(checkShutdownProp(domain.getDomainUid()
              + "-admin-server", domain.getDomainNs(),"Graceful"),
          domain.getDomainUid()
              + "-admin-server: "
              + " shutdown property does not match the expected : shutdownType=Graceful");
      Assertions.assertTrue(checkShutdownProp(domain.getDomainUid()
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
   * Add shutdown env vars at domain spec level and verify the pod are Terminated and recreated.
   *
   * @throws Exception if domain cannot be started or failed to verify shutdown options.
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
              + " failed to create domain " + domainNSShutOpEnv);
      Assertions.assertTrue(checkShutdownProp(domain.getDomainUid()
              + "-managed-server1", domain.getDomainNs(), "Forced", "60", "false"),
          domain.getDomainUid()
              + "-managed-server1 :"
              + " shutdown properties don't not match the expected : "
              + "shutdownType=Forced, timeoutSeconds=60, ignoreSessions=false");
      Assertions.assertTrue(checkShutdownProp(domain.getDomainUid()
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
   * @throws Exception if domain cannot be started or failed to verify shutdown options.
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
              + " failed to create domain " + domainNSShutOpOverrideViaEnv);
      Assertions.assertTrue(checkShutdownProp(domain.getDomainUid()
              + "-managed-server1", domain.getDomainNs(), "Forced", "60"),
          domain.getDomainUid()
              + "-managed-server1 :"
              + " shutdown properties don't not match the expected : "
              + "shutdownType=Forced, timeoutSeconds=60");;
      Assertions.assertTrue(checkShutdownProp(domain.getDomainUid()
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
}

