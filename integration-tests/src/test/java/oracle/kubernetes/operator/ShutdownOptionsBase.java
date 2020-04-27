// Copyright (c) 2020, Oracle Corporation and/or its affiliates.
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
 * Base class defining the common methods for testing Shutdown Properties, supplied at domain, cluster, ms, env level.
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
  protected static String domainNSShutOpOverrideViaCluster = "domainns-sopovercl";
  

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
   * Send request to web app deployed on WebLogic.
   *
   * @param testAppPath - URL path for webapp
   * @param domain      - Domain where webapp is deployed
   * @throws Exception  - Fails if can't find expected output from app.
   */
  protected static void callWebApp(String testAppPath, Domain domain)
      throws Exception {

    if (!domain.getLoadBalancerName().equalsIgnoreCase("NONE")) {
      // url
      StringBuffer testAppUrl = new StringBuffer("http://");
      testAppUrl.append(domain.getHostNameForCurl());
      testAppUrl.append(":" + domain.getLoadBalancerWebPort());
      testAppUrl.append("/");
      if (domain.getLoadBalancerName().equals("APACHE")) {
        testAppUrl.append("weblogic/");
      }
      testAppUrl.append(testAppPath);

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
   * Check the shutdown properties values in the pod.
   *
   * @param podName - name of the pod
   * @param podNS - pod namespace
   * @param props - set of properties to check
   * @throws Exception if failed to verify the shutdown properties values.
   */
  protected static boolean checkShutdownProp(String podName, String podNS, String... props)
      throws Exception {

    HashMap<String, Boolean> propFound = new HashMap<String, Boolean>();
    StringBuffer cmd = new StringBuffer("kubectl get pod ");
    cmd.append(podName);
    cmd.append(" -o yaml ");
    cmd.append(" -n ").append(podNS);
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
}

