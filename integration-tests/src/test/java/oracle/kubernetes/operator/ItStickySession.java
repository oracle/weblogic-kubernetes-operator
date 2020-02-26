// Copyright (c) 2019, 2020, Oracle Corporation and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import oracle.kubernetes.operator.utils.Domain;
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
 * <p>This test is used for testing the affinity between a web client and a Weblogic server for the
 * duration of a HTTP session created by Voyager load balancer.
 */
@TestMethodOrder(Alphanumeric.class)
public class ItStickySession extends BaseTest {
  private static final String testAppName = "stickysessionapp";
  private static final String testAppPath = testAppName + "/StickySessionCounterServlet";
  private static final String scriptName = "buildDeployAppInPod.sh";

  private static Map<String, String> httpAttrMap;

  private static String httpHeaderFile;

  private static Operator operator;
  private static Domain domain;
  private static String domainNS;
  private static String testClassName;
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
   * for the test. It creates an operator, a Weblogic domain if not running
   * and deploy a web application with persistent-store-type configured to replicated_if_clustered
   *
   * @throws Exception exception if result/pv/operator/domain creation fails
   */

  @BeforeEach
  public void prepare() throws Exception {
    if (FULLTEST) {
      createResultAndPvDirs(testClassName);
      String testClassNameShort = "itstickysesn";
      
      // Create operator1
      if (operator == null) {
        LoggerHelper.getLocal().log(Level.INFO,
            "Creating Operator & waiting for the script to complete execution");
        Map<String, Object> operatorMap = createOperatorMap(getNewSuffixCount(), true, testClassNameShort);
        //operator = TestUtils.createOperator(OPERATOR1_YAML);
        operator = TestUtils.createOperator(operatorMap, Operator.RestCertType.SELF_SIGNED);
        Assertions.assertNotNull(operator);
        domainNS = ((ArrayList<String>) operatorMap.get("domainNamespaces")).get(0);
        namespaceList.append((String)operatorMap.get("namespace"));
        namespaceList.append(" ").append(domainNS);
      }
      
      // create domain
      if (domain == null) {
        LoggerHelper.getLocal().log(Level.INFO, "Creating WLS Domain & waiting for the script to complete execution");
        int number = getNewSuffixCount();
        Map<String, Object> domainMap = createDomainMap(number, testClassNameShort);
        domainMap.put("namespace", domainNS);
        // Treafik doesn't work due to the bug 28050300. Use Voyager instead
        domainMap.put("loadBalancer", "VOYAGER");
        domainMap.put("voyagerWebPort", 30944 + number);
        LoggerHelper.getLocal().log(Level.INFO, "For this domain voyagerWebPort is set to: 30944 + " + number);
        domain = TestUtils.createDomain(domainMap);
        domain.verifyDomainCreated();
      }

      httpHeaderFile = getResultDir() + "/headers";

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
   * Releases k8s cluster lease, archives result, pv directories.
   *
   * @throws Exception exception
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
   * Use a web application deployed on Weblogic cluster to track HTTP session.
   * In-memory replication persistence method is configured to implement session persistence.
   * server-affinity is achieved by Voyager load balancer based on HTTP session information.
   * This test sends two HTTP requests
   * to Weblogic and verify that all requests are directed to same Weblogic server.
   *
   * @throws Exception exception
   */
  @Test
  public void testSameSessionStickiness() throws Exception {
    Assumptions.assumeTrue(FULLTEST);
    String testMethodName = new Object() {
    }.getClass().getEnclosingMethod().getName();
    logTestBegin(testMethodName);

    int counterNum = 4;
    String webServiceSetUrl = testAppPath + "?setCounter=" + counterNum;
    final String webServiceGetUrl = testAppPath + "?getCounter";

    String serverNameAttr = "servername";
    String sessionIdAttr = "sessionid";
    final String countAttr = "count";

    // Send the first HTTP request to the cluster to establish a connection
    ExecResult result = getHttpResponse(webServiceSetUrl, " -D ");

    // Retrieve the session id and connected server name from the first HTTP response
    String serverName1 = getHttpResponseAttribute(result.stdout(), serverNameAttr);
    String serverID1 = getHttpResponseAttribute(result.stdout(), sessionIdAttr);

    Assertions.assertNotNull(serverName1);
    Assertions.assertNotNull(serverID1);

    LoggerHelper.getLocal().log(Level.INFO,
        "The first HTTP request established a connection with server <"
            + serverName1
            + ">, HTTP session id is <"
            + serverID1
            + ">");

    // Send the second HTTP request to the cluster to get the count number
    result = getHttpResponse(webServiceGetUrl, " -b ");

    // Retrieve the session id and connected server name from the second HTTP response
    String serverName2 = getHttpResponseAttribute(result.stdout(), serverNameAttr);
    String serverID2 = getHttpResponseAttribute(result.stdout(), sessionIdAttr);
    String count = getHttpResponseAttribute(result.stdout(), countAttr);

    Assertions.assertNotNull(serverName2);
    Assertions.assertNotNull(serverID2);
    Assertions.assertNotNull(count);

    LoggerHelper.getLocal().log(Level.INFO,
        "The second HTTP request connected to server <"
            + serverName2
            + "> with HTTP session id <"
            + serverID2
            + ">");

    // Verify that the same session info is used
    Assumptions.assumeTrue(serverID1.equals(serverID2), "HTTP session should NOT change!");
    LoggerHelper.getLocal().log(Level.INFO, "Same HTTP session id <" + serverID1 + "> is used");

    // Verify server-affinity
    Assumptions.assumeTrue(serverName1.equals(serverName2), "Weblogic server name should NOT change!");
    LoggerHelper.getLocal().log(
        Level.INFO, "Two HTTP requests are directed to same Weblogic server <"
            + serverName1 + ">");

    // Verify that count numbers from two HTTP responses match
    Assumptions.assumeTrue(Integer.parseInt(count) == counterNum, "Count number does not match");
    LoggerHelper.getLocal().log(Level.INFO,
        "Count number <"
            + count
            + "> got from HTTP response matches "
            + "original setting <"
            + counterNum
            + ">");

    LoggerHelper.getLocal().log(Level.INFO, "SUCCESS - " + testMethodName);
  }

  /**
   * Use a web application deployed on Weblogic cluster to track HTTP session. In-memory replication
   * persistence method is configured to implement session persistence. server-affinity is achieved
   * by Voyager load balancer based on HTTP session information. This test sends two HTTP requests
   * from two different clients to Weblogic and verify that HTTP sessions are isolated.
   *
   * @throws Exception exception
   */
  @Test
  public void testDiffSessionsNoSharing() throws Exception {
    Assumptions.assumeTrue(FULLTEST);
    String testMethodName = new Object() {
    }.getClass().getEnclosingMethod().getName();
    logTestBegin(testMethodName);

    Map<String, Object> domainMap = domain.getDomainMap();
    String domainNS = domainMap.get("namespace").toString();
    String domainUid = domain.getDomainUid();

    int counterNum = 4;
    String webServiceSetUrl = testAppPath + "?setCounter=" + counterNum;
    String webServiceGetUrl = testAppPath + "?getCounter";

    String sessionIdAttr = "sessionid";
    final String countAttr = "count";

    // Client1 sends a HTTP request to set a count number
    ExecResult result = getHttpResponse(webServiceSetUrl);

    // Retrieve the session id from HTTP response
    String serverIdClient1 = getHttpResponseAttribute(result.stdout(), sessionIdAttr);
    Assertions.assertNotNull(serverIdClient1);

    LoggerHelper.getLocal().log(Level.INFO,
        "Client1 created a connection with HTTP session id <"
            + serverIdClient1
            + "> and set a count number to <"
            + counterNum
            + ">");

    // Client2 sends a HTTP request to get a count number
    result = getHttpResponse(webServiceGetUrl);

    // Retrieve the session id and count number from HTTP response
    String serverIdClient2 = getHttpResponseAttribute(result.stdout(), sessionIdAttr);
    String count = getHttpResponseAttribute(result.stdout(), countAttr);

    Assertions.assertNotNull(serverIdClient2);
    Assertions.assertNotNull(count);

    LoggerHelper.getLocal().log(Level.INFO,
        "Client2 created a connection with HTTP session id <"
            + serverIdClient2
            + "> and retrieved a count number <"
            + count
            + ">");

    // Verify that each client session has its own session ID
    Assumptions.assumeFalse(serverIdClient1.equals(serverIdClient2), "HTTP session should NOT be same!");

    // Verify that count number retrieved from session state is not shared between two clients
    Assumptions.assumeTrue(
        count.equals("0"),
        "Count number <" + counterNum + "> set by client1 should be invisible to client2");

    LoggerHelper.getLocal().log(Level.INFO, "SUCCESS - " + testMethodName);
  }

  private String getHttpResponseAttribute(String httpResponseString, String attribute)
      throws Exception {
    String attrPatn = httpAttrMap.get(attribute);

    Assertions.assertNotNull(attrPatn);

    String httpAttribute = null;
    Pattern pattern = Pattern.compile(attrPatn);
    Matcher matcher = pattern.matcher(httpResponseString);

    if (matcher.find()) {
      httpAttribute = matcher.group(2);
    }

    return httpAttribute;
  }

  private ExecResult getHttpResponse(String webServiceUrl, String... args) throws Exception {
    String headerOption = (args.length == 0) ? "" : args[0] + httpHeaderFile;

    // Send a HTTP request
    String curlCmd = buildWebServiceUrl(webServiceUrl, headerOption);
    LoggerHelper.getLocal().log(Level.INFO, "Send a HTTP request: " + curlCmd);
    ExecResult result = TestUtils.exec(curlCmd);

    return result;
  }

  private String buildWebServiceUrl(String curlUrlPath, String paramToAppend) throws Exception {
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
        .append(curlUrlPath)
        .append(paramToAppend);

    return webServiceUrl.toString();
  }
}
