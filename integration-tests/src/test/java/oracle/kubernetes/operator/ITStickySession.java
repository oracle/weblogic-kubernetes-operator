// Copyright 2019, Oracle Corporation and/or its affiliates.  All rights reserved.
// Licensed under the Universal Permissive License v 1.0 as shown at
// http://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import oracle.kubernetes.operator.utils.Domain;
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
 * <p>This test is used for testing the affinity between a web client and a Weblogic server for the
 * duration of a HTTP session created by Voyager load balancer.
 */
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
public class ITStickySession extends BaseTest {
  private static final String testAppName = "stickysessionapp";
  private static final String testAppPath = testAppName + "/StickySessionCounterServlet";
  private static final String scriptName = "buildDeployAppInPod.sh";

  private static Map<String, String> httpAttrMap;

  private static String httpHeaderFile;

  private static Operator operator;
  private static Domain domain;

  /**
   * This method gets called only once before any of the test methods are executed. It does the
   * initialization of the integration test properties defined in OperatorIT.properties and setting
   * the resultRoot, pvRoot and projectRoot attributes. It creates an operator, a Weblogic domain
   * and deploy a web application with persistent-store-type configured to replicated_if_clustered.
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
        System.setProperty("LB_TYPE", "VOYAGER");
        domain = TestUtils.createDomain(DOMAINONPV_WLST_YAML);
        domain.verifyDomainCreated();
      }

      httpHeaderFile = BaseTest.getResultDir() + "/headers";

      httpAttrMap = new HashMap<String, String>();
      httpAttrMap.put("sessioncreatetime", "(.*)sessioncreatetime>(.*)</sessioncreatetime(.*)");
      httpAttrMap.put("sessionid", "(.*)sessionid>(.*)</sessionid(.*)");
      httpAttrMap.put("servername", "(.*)connectedservername>(.*)</connectedservername(.*)");
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
    logger.info("+++++++++++++++++++++++++++++++++---------------------------------+");
    logger.info("BEGIN");
    logger.info("Run once, release cluster lease");

    tearDown(new Object() {}.getClass().getEnclosingClass().getSimpleName());

    logger.info("SUCCESS");
  }

  /**
   * Use a web application deployed on Weblogic cluster to track HTTP session. In-memory replication
   * persistence method is configured to implement session persistence. server-affinity is achieved
   * by Voyager load balancer based on HTTP session information. This test sends two HTTP requests
   * to Weblogic and verify that all requests are directed to same Weblogic server.
   *
   * @throws Exception
   */
  @Test
  public void testSameSessionStickiness() throws Exception {
    Assume.assumeFalse(QUICKTEST);
    String testMethodName = new Object() {}.getClass().getEnclosingMethod().getName();
    logTestBegin(testMethodName);

    Map<String, Object> domainMap = domain.getDomainMap();
    String domainNS = domainMap.get("namespace").toString();
    String domainUid = domain.getDomainUid();

    int counterNum = 4;
    String webServiceSetUrl = testAppPath + "?setCounter=" + counterNum;
    String webServiceGetUrl = testAppPath + "?getCounter";

    String serverNameAttr = "servername";
    String sessionIDAttr = "sessionid";
    String countAttr = "count";

    // Send the first HTTP request to the cluster to establish a connection
    ExecResult result = getHTTPResponse(webServiceSetUrl, " -D ");

    // Retrieve the session id and connected server name from the first HTTP response
    String serverName1 = getHttpResponseAttribute(result.stdout(), serverNameAttr);
    String serverID1 = getHttpResponseAttribute(result.stdout(), sessionIDAttr);

    Assume.assumeNotNull(serverName1);
    Assume.assumeNotNull(serverID1);

    logger.info(
        "The first HTTP request established a connection with server <"
            + serverName1
            + ">, HTTP session id is <"
            + serverID1
            + ">");

    // Send the second HTTP request to the cluster to get the count number
    result = getHTTPResponse(webServiceGetUrl, " -b ");

    // Retrieve the session id and connected server name from the second HTTP response
    String serverName2 = getHttpResponseAttribute(result.stdout(), serverNameAttr);
    String serverID2 = getHttpResponseAttribute(result.stdout(), sessionIDAttr);
    String count = getHttpResponseAttribute(result.stdout(), countAttr);

    Assume.assumeNotNull(serverName2);
    Assume.assumeNotNull(serverID2);
    Assume.assumeNotNull(count);

    logger.info(
        "The second HTTP request connected to server <"
            + serverName2
            + "> with HTTP session id <"
            + serverID2
            + ">");

    // Verify that the same session info is used
    Assume.assumeTrue("HTTP session should NOT change!", serverID1.equals(serverID2));
    logger.info("Same HTTP session id <" + serverID1 + "> is used");

    // Verify server-affinity
    Assume.assumeTrue("Weblogic server name should NOT change!", serverName1.equals(serverName2));
    logger.info("Two HTTP requests are directed to same Weblogic server <" + serverName1 + ">");

    // Verify that count numbers from two HTTP responses match
    Assume.assumeTrue("Count number does not match", Integer.parseInt(count) == counterNum);
    logger.info(
        "Count number <"
            + count
            + "> got from HTTP response matches "
            + "original setting <"
            + counterNum
            + ">");

    logger.info("SUCCESS - " + testMethodName);
  }

  /**
   * Use a web application deployed on Weblogic cluster to track HTTP session. In-memory replication
   * persistence method is configured to implement session persistence. server-affinity is achieved
   * by Voyager load balancer based on HTTP session information. This test sends two HTTP requests
   * from two different clients to Weblogic and verify that HTTP sessions are isolated.
   *
   * @throws Exception
   */
  @Test
  public void testDiffSessionsNoSharing() throws Exception {
    Assume.assumeFalse(QUICKTEST);
    String testMethodName = new Object() {}.getClass().getEnclosingMethod().getName();
    logTestBegin(testMethodName);

    Map<String, Object> domainMap = domain.getDomainMap();
    String domainNS = domainMap.get("namespace").toString();
    String domainUid = domain.getDomainUid();

    int counterNum = 4;
    String webServiceSetUrl = testAppPath + "?setCounter=" + counterNum;
    String webServiceGetUrl = testAppPath + "?getCounter";

    String sessionIDAttr = "sessionid";
    String countAttr = "count";

    // Client1 sends a HTTP request to set a count number
    ExecResult result = getHTTPResponse(webServiceSetUrl);

    // Retrieve the session id from HTTP response
    String serverIDClient1 = getHttpResponseAttribute(result.stdout(), sessionIDAttr);
    Assume.assumeNotNull(serverIDClient1);

    logger.info(
        "Client1 created a connection with HTTP session id <"
            + serverIDClient1
            + "> and set a count number to <"
            + counterNum
            + ">");

    // Client2 sends a HTTP request to get a count number
    result = getHTTPResponse(webServiceGetUrl);

    // Retrieve the session id and count number from HTTP response
    String serverIDClient2 = getHttpResponseAttribute(result.stdout(), sessionIDAttr);
    String count = getHttpResponseAttribute(result.stdout(), countAttr);

    Assume.assumeNotNull(serverIDClient2);
    Assume.assumeNotNull(count);

    logger.info(
        "Client2 created a connection with HTTP session id <"
            + serverIDClient2
            + "> and retrieved a count number <"
            + count
            + ">");

    // Verify that each client session has its own session ID
    Assume.assumeFalse("HTTP session should NOT be same!", serverIDClient1.equals(serverIDClient2));

    // Verify that count number retrieved from session state is not shared between two clients
    Assume.assumeTrue(
        "Count number <" + counterNum + "> set by client1 should be invisible to client2",
        count.equals("0"));

    logger.info("SUCCESS - " + testMethodName);
  }

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

  private ExecResult getHTTPResponse(String webServiceURL, String... args) throws Exception {
    String headerOption = (args.length == 0) ? "" : args[0] + httpHeaderFile;

    // Send a HTTP request
    String curlCmd = buildWebServiceUrl(webServiceURL, headerOption);
    logger.info("Send a HTTP request: " + curlCmd);

    ExecResult result = TestUtils.exec(curlCmd);

    return result;
  }

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
}
