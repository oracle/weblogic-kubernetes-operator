// Copyright (c) 2019, 2020, Oracle Corporation and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator;

import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;

import oracle.kubernetes.operator.utils.Domain;
import oracle.kubernetes.operator.utils.ExecCommand;
import oracle.kubernetes.operator.utils.ExecResult;
import oracle.kubernetes.operator.utils.LoggerHelper;
import oracle.kubernetes.operator.utils.TestUtils;


/**
 * Simple JUnit test file used for testing Operator.
 *
 * <p>This test is used for testing pods being shutdowned by some properties change.
 */

public class ShutdownOptionsBase extends BaseTest {

  protected static final String testAppName = "httpsessionreptestapp";
  protected static final String scriptName = "buildDeployAppInPod.sh";
  protected static String domainNSShutOpCluster = "domainns-sopcluster";
  protected static String domainNSShutOpMS = "domainns-sopms";
  protected static String domainNSShutOpDomain = "domainns-sopdomain";
  protected static String domainNSShutOpMSIgnoreSessions = "domainns-sopmsignores";
  protected static String domainNSShutOpMSTimeout = "domainns-sopmstimeout";
  protected static String domainNSShutOpMSForced = "domainns-sopmsforced";
  protected static String domainNSShutOpEnv = "domainns-sopenv";
  protected static String domainNSShutOpOverrideViaEnv = "domainns-sopoverenv";
  protected static String domainNSShutOpOverrideViaCluster = "domainns-sopovercluster";
  

  protected Domain createDomain(String domainNS, Map<String, Object> shutdownProps) throws Exception {

    Map<String, Object> domainMap = createDomainMap(getNewSuffixCount(), "itpodsshutdown");
    domainMap.put("namespace", domainNS);
    domainMap.put("domainUID", domainNS + getNewSuffixCount());
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
  protected static void callWebApp(String testAppPath, Domain domain)
      throws Exception {

    if (!domain.getLoadBalancerName().equalsIgnoreCase("NONE")) {
      // url
      StringBuffer testAppUrl = new StringBuffer("http://");
      testAppUrl.append(domain.getHostNameForCurl());
      /*
      if (!OKE_CLUSTER) {
        testAppUrl.append(":").append(domain.getLoadBalancerWebPort());
      }
      */
      testAppUrl.append("/");
      if (domain.getLoadBalancerName().equals("APACHE")) {
        testAppUrl.append("weblogic/");
      }
      testAppUrl.append(testAppPath);
      // curl cmd to call webapp

      StringBuffer webServiceUrl = new StringBuffer("curl --silent --noproxy '*' ");
      webServiceUrl
          .append(" -H 'host: ")
          .append(domain.getDomainNs())
          .append(".org' ")
          .append(testAppUrl.toString());

      // Send a HTTP request to keep open session
      String curlCmd = webServiceUrl.toString();
      ExecCommand.exec(curlCmd);
    }
  }

  /**
   * Shutdown managed server and returns spent shutdown time.
   *
   * @throws Exception If failed to shutdown the server.
   */
  protected static long shutdownServer(String serverName, String domainNS, String domainUid) throws Exception {
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

  protected static boolean checkShutdownUpdatedProp(String podName, String domainNS, String... props)
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

  protected long verifyShutdown(long delayTime, Domain domain) throws Exception {

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
}

