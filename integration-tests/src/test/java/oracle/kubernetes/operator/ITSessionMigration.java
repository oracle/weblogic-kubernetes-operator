// Copyright 2019, Oracle Corporation and/or its affiliates.  All rights reserved.
// Licensed under the Universal Permissive License v 1.0 as shown at
// http://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import oracle.kubernetes.operator.utils.Domain;
import oracle.kubernetes.operator.utils.ExecCommand;
import oracle.kubernetes.operator.utils.ExecResult;
import oracle.kubernetes.operator.utils.Operator;
import oracle.kubernetes.operator.utils.TestUtils;
import org.junit.AfterClass;
import org.junit.Assume;
import org.junit.BeforeClass;
import org.junit.FixMethodOrder;
import org.junit.Test;
import org.junit.runners.MethodSorters;

/**
 * Simple JUnit test file used for testing Operator.
 *
 * <p>This test is used for creating Operator(s) and domain(s) which are managed by the Operator(s).
 * And to test WLS Session Migration feature
 */
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
public class ITSessionMigration extends BaseTest {
  private static final String testAppName = "httpsessionreptestapp";
  private static final String scriptName = "buildDeployAppInPod.sh";

  private static Map<String, String> httpAttrMap;

  private static String httpHeaderFile;

  private static Operator operator;
  private static Domain domain;

  /**
   * This method gets called only once before any of the test methods are executed. It does the
   * initialization of the integration test properties defined in OperatorIT.properties and setting
   * the resultRoot, pvRoot and projectRoot attributes.
   *
   * <p>It also create operator and verify its deployed successfully. Create domain and verify
   * domain is created.
   *
   * @throws Exception
   */
  @BeforeClass
  public static void staticPrepare() throws Exception {
    if (!QUICKTEST) {
      // initialize test properties and create the directories
      initialize(APP_PROPS_FILE);

      // Create operator1
      if (operator == null) {
        logger.info("Creating Operator & waiting for the script to complete execution");
        operator = TestUtils.createOperator(OPERATOR1_YAML);
      }

      // create domain
      if (domain == null) {
        logger.info("Creating WLS Domain & waiting for the script to complete execution");
        domain = TestUtils.createDomain(DOMAINONPV_WLST_YAML);
        domain.verifyDomainCreated();
      }

      httpHeaderFile = BaseTest.getResultDir() + "/headers";

      httpAttrMap = new HashMap<String, String>();
      httpAttrMap.put("sessioncreatetime", "(.*)sessioncreatetime>(.*)</sessioncreatetime(.*)");
      httpAttrMap.put("sessionid", "(.*)sessionid>(.*)</sessionid(.*)");
      httpAttrMap.put("primary", "(.*)primary>(.*)</primary(.*)");
      httpAttrMap.put("secondary", "(.*)secondary>(.*)</secondary(.*)");
      httpAttrMap.put("count", "(.*)countattribute>(.*)</countattribute(.*)");

      // Build WAR in the admin pod and deploy it from the admin pod to a weblogic target
      domain.buildDeployJavaAppInPod(
          testAppName, scriptName, BaseTest.getUsername(), BaseTest.getPassword());

      // Wait some time for deployment gets ready
      Thread.sleep(10 * 1000);
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
      logger.info("++++++++++++++++++++++++++++++++++");
      logger.info("BEGIN");
      logger.info("Run once, release cluster lease");

      tearDown();

      logger.info("SUCCESS");
    }
  }

  /**
   * Verify that a new and running managed server is picked up as the primary server after the
   * primary server shut down
   *
   * @throws Exception
   */
  @Test
  public void testRepickPrimary() throws Exception {
    Assume.assumeFalse(QUICKTEST);
    String testMethodName = new Object() {}.getClass().getEnclosingMethod().getName();
    logTestBegin(testMethodName);

    Map<String, Object> domainMap = domain.getDomainMap();
    String domainNS = domainMap.get("namespace").toString();
    String domainUid = domain.getDomainUid();

    String testAppPath = testAppName + "/CounterServlet";
    String sessCreateTime = "sessioncreatetime";
    String primaryServ = "primary";

    // Send the first HTTP request and save HTTP header/sessionID info
    ExecResult result = getHTTPResponse(testAppPath, " -D ");

    // Get primary server name & session create time bf primaryServName1 is stopped
    String primaryServName1 = getHttpResponseAttribute(result.stdout(), primaryServ);
    String sessCreateTime1 = getHttpResponseAttribute(result.stdout(), sessCreateTime);

    // Stop primary server
    stopPrimaryServer(primaryServName1);

    // Send the second HTTP request using HTTP header/sessionID info save before
    result = getHTTPResponse(testAppPath, " -b ");

    // Get primary server name & session create time af primaryServName1 is stopped
    String primaryServName2 = getHttpResponseAttribute(result.stdout(), primaryServ);
    String sessCreateTime2 = getHttpResponseAttribute(result.stdout(), sessCreateTime);

    // Verify that the same session info is used
    Assume.assumeTrue("HTTP Session should NOT change!", sessCreateTime1.equals(sessCreateTime2));

    // Verify that a new primary server is picked
    Assume.assumeFalse(
        "A new primary server should be picked!",
        primaryServName1.trim().equals(primaryServName2.trim()));

    // Restore test env
    TestUtils.checkPodReady(domainUid + "-" + primaryServName1, domainNS);

    logger.info(
        "SUCCESS - " + testMethodName + ". ms <" + primaryServName2 + "> is new primary server.");
  }

  /**
   * Verify that HTTP session state is migrated to the new primary server after the primary server
   * shut down
   *
   * @throws Exception
   */
  @Test
  public void testHttpSessionMigr() throws Exception {
    Assume.assumeFalse(QUICKTEST);
    String testMethodName = new Object() {}.getClass().getEnclosingMethod().getName();
    logTestBegin(testMethodName);

    Map<String, Object> domainMap = domain.getDomainMap();
    String domainNS = domainMap.get("namespace").toString();
    String domainUid = domain.getDomainUid();

    int counterNum = 4;
    String testAppPath = testAppName + "/CounterServlet";
    String webServiceSetUrl = testAppPath + "?setCounter=" + counterNum;
    String webServiceGetUrl = testAppPath + "?getCounter";

    String primaryServ = "primary";
    String count = "count";

    // Send the first HTTP request and save HTTP header/sessionID info
    ExecResult result = getHTTPResponse(webServiceSetUrl, " -D ");

    // Get primary server name & count number bf primaryServName1 is stopped
    String primaryServName1 = getHttpResponseAttribute(result.stdout(), primaryServ);
    String countattribute1 = getHttpResponseAttribute(result.stdout(), count);

    // Stop primary server
    stopPrimaryServer(primaryServName1);

    // Send the second HTTP request using HTTP header/sessionID info save before
    result = getHTTPResponse(webServiceGetUrl, " -b ");

    // Get primary server name & count number af primaryServName1 is stopped
    String primaryServName2 = getHttpResponseAttribute(result.stdout(), primaryServ);
    String countattribute2 = getHttpResponseAttribute(result.stdout(), count);

    // Verify that the count number is from a new primary server
    Assume.assumeFalse(
        "A new primary server should be picked!",
        primaryServName1.trim().equals(primaryServName2.trim()));

    // Verify that HTTP session state is migrated by checking the count number same
    // bf and af primaryServName1 stopped
    Assume.assumeTrue(
        "HTTP session state is NOT migrated!", countattribute1.equals(countattribute2));

    // Restore test env
    TestUtils.checkPodReady(domainUid + "-" + primaryServName1, domainNS);

    logger.info("SUCCESS - " + testMethodName + ". HTTP session state is migrated!");
  }

  /**
   * Get HTTP response from the web app deployed on wls
   *
   * @param webServiceURL - web server URL
   * @param headerOption - option to save HTTP header info or use it
   * @throws Exception
   */
  private ExecResult getHTTPResponse(String webServiceURL, String headerOption) throws Exception {
    // Send a HTTP request
    String curlCmd = buildWebServiceUrl(webServiceURL, headerOption + httpHeaderFile);
    logger.info("Send a HTTP request: " + curlCmd);

    ExecResult result = ExecCommand.exec(curlCmd);

    if (result.exitValue() != 0) {
      throw new Exception(
          "FAILURE: command "
              + curlCmd
              + " failed, returned "
              + result.stderr()
              + "\n "
              + result.stdout());
    }

    return result;
  }

  /**
   * Stop the primary server
   *
   * @param primaryServerName - weblogic primary server name
   * @throws Exception
   */
  private void stopPrimaryServer(String primaryServerName) throws Exception {
    Map<String, Object> domainMap = domain.getDomainMap();
    String domainNS = domainMap.get("namespace").toString();
    String domainUid = domain.getDomainUid();

    // stop primary server
    String msPodName = domainUid + "-" + primaryServerName;
    String cmd = "kubectl delete po/" + msPodName + " -n " + domainNS;
    logger.info("Stop managed server <" + msPodName + "> using command:\n" + cmd);

    ExecResult result = ExecCommand.exec(cmd);
    if (result.exitValue() != 0) {
      throw new Exception("FAILURE: command " + cmd + " failed, returned " + result.stderr());
    }

    logger.info(result.stdout());
  }

  /**
   * Get the value of a HTTP attribute
   *
   * @param httpResponseString - HTTP response
   * @param attribute - attrubute name to find in the HTTP response
   * @throws Exception
   */
  private String getHttpResponseAttribute(String httpResponseString, String attribute)
      throws Exception {

    String attrPatn = httpAttrMap.get(attribute);

    Assume.assumeNotNull(attrPatn);

    String httpAttribute = null;

    Pattern pattern = Pattern.compile(attrPatn);
    Matcher matcher = pattern.matcher(httpResponseString);
    if (matcher.find()) {
      httpAttribute = matcher.group(2);
    }

    return httpAttribute;
  }

  /**
   * Build web server url
   *
   * @param curlURLPath - URL path sent by curl
   * @param paramToAppend - params need to be appended to the URL path
   * @throws Exception
   */
  private String buildWebServiceUrl(String curlURLPath, String paramToAppend) throws Exception {
    String nodePortHost = domain.getHostNameForCurl();
    int nodePort = domain.getLoadBalancerWebPort();

    StringBuffer webServiceUrl = new StringBuffer("curl --silent ");
    webServiceUrl
        .append(" -H 'host: ")
        .append(domain.getDomainUid())
        .append(".org' ")
        .append(" http://")
        .append(nodePortHost)
        .append(":")
        .append(nodePort)
        .append("/")
        .append(curlURLPath)
        .append(paramToAppend);

    return webServiceUrl.toString();
  }

  /**
   * Execute a given curl command and verify the results
   *
   * @param curlCmd - a curl command to execute
   * @throws Exception
   */
  private ExecResult execCurlCmd(String curlCmd) throws Exception {
    logger.info("curl command to exec is:\n" + curlCmd);

    ExecResult result = ExecCommand.exec(curlCmd);

    if (result.exitValue() != 0) {
      throw new Exception(
          "FAILURE: command "
              + curlCmd
              + " failed, returned "
              + result.stderr()
              + "\n "
              + result.stdout());
    }

    return result;
  }
}
