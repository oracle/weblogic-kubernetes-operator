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
 * <p>This test is used for creating Operator(s) and domain(s) which are managed by the Operator(s).
 * And to test WLS Session Migration feature
 */
@TestMethodOrder(Alphanumeric.class)
public class ItSessionMigration extends BaseTest {
  private static final String testAppName = "httpsessionreptestapp";
  private static final String scriptName = "buildDeployAppInPod.sh";
  private static Map<String, String> httpAttrMap;
  private static String httpHeaderFile;
  private static Operator operator;
  private static Domain domain;
  private static String domainNS1;
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
   * for the test. Creates the operator and domain if its not running.
   *
   * @throws Exception exception if result/pv/operator/domain creation fails
   */
  @BeforeEach
  public void prepare() throws Exception {
    if (FULLTEST) {
      createResultAndPvDirs(testClassName);
      String testClassNameShort = "sessmig";
      // create operator1
      if (operator == null) {
        Map<String, Object> operatorMap =
            createOperatorMap(getNewSuffixCount(), true, testClassNameShort);
        operator = TestUtils.createOperator(operatorMap, Operator.RestCertType.SELF_SIGNED);
        Assertions.assertNotNull(operator);
        domainNS1 = ((ArrayList<String>) operatorMap.get("domainNamespaces")).get(0);
        namespaceList.append((String)operatorMap.get("namespace"));
        namespaceList.append(" ").append(domainNS1);
      }

      // create domain
      if (domain == null) {
        LoggerHelper.getLocal().log(Level.INFO,
            "Creating WLS Domain & waiting for the script to complete execution");
        Map<String, Object> wlstDomainMap =
            createDomainMap(getNewSuffixCount(), testClassNameShort);
        wlstDomainMap.put("namespace", domainNS1);
        // wlstDomainMap.put("domainUID", "sessmigdomainonpvwlst");
        domain = TestUtils.createDomain(wlstDomainMap);
        domain.verifyDomainCreated();
      }

      httpHeaderFile = getResultDir() + "/headers";
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
   * Releases k8s cluster lease, archives result, pv directories.
   *
   * @throws Exception exception
   */
  @AfterAll
  public static void staticUnPrepare() throws Exception {
    if (FULLTEST) {
      tearDown(new Object() {}.getClass()
          .getEnclosingClass().getSimpleName(), namespaceList.toString());

      LoggerHelper.getLocal().log(Level.INFO, "SUCCESS");
    }
  }

  /**
   * Verify that a new and running managed server is picked up as the primary server after the
   * primary server shut down.
   *
   * @throws Exception exception
   */
  @Test
  public void testRepickPrimary() throws Exception {
    Assumptions.assumeTrue(FULLTEST);
    String testMethodName = new Object() {
    }.getClass().getEnclosingMethod().getName();
    logTestBegin(testMethodName);

    Map<String, Object> domainMap = domain.getDomainMap();
    final String domainNS = domainMap.get("namespace").toString();
    final String domainUid = domain.getDomainUid();

    String testAppPath = testAppName + "/CounterServlet";
    String sessCreateTime = "sessioncreatetime";
    String primaryServ = "primary";

    // Send the first HTTP request and save HTTP header/sessionID info
    ExecResult result = getHttpResponse(testAppPath, " -D ");

    // Get primary server name & session create time bf primaryServName1 is stopped
    String primaryServName1 = getHttpResponseAttribute(result.stdout(), primaryServ);
    String sessCreateTime1 = getHttpResponseAttribute(result.stdout(), sessCreateTime);

    // Stop primary server
    domain.shutdownManagedServerUsingServerStartPolicy(primaryServName1);

    // Send the second HTTP request using HTTP header/sessionID info save before
    result = getHttpResponse(testAppPath, " -b ");

    // Get primary server name & session create time af primaryServName1 is stopped
    String primaryServName2 = getHttpResponseAttribute(result.stdout(), primaryServ);
    String sessCreateTime2 = getHttpResponseAttribute(result.stdout(), sessCreateTime);

    // Verify that the same session info is used
    Assumptions.assumeTrue(sessCreateTime1.equals(sessCreateTime2), "HTTP Session should NOT change!");

    // Verify that a new primary server is picked
    Assumptions.assumeFalse(
        primaryServName1.trim().equals(primaryServName2.trim()),
        "A new primary server should be picked!");

    // Restore test env
    domain.restartManagedServerUsingServerStartPolicy(primaryServName1);
    TestUtils.checkPodReady(domainUid + "-" + primaryServName1, domainNS);

    LoggerHelper.getLocal().log(Level.INFO,
        "SUCCESS - " + testMethodName + ". ms <" + primaryServName2 + "> is new primary server.");
  }

  /**
   * Verify that HTTP session state is migrated to the new primary server after the primary server
   * shut down.
   *
   * @throws Exception exception
   */
  @Test
  public void testHttpSessionMigr() throws Exception {
    Assumptions.assumeTrue(FULLTEST);
    String testMethodName = new Object() {
    }.getClass().getEnclosingMethod().getName();
    logTestBegin(testMethodName);

    Map<String, Object> domainMap = domain.getDomainMap();
    final String domainNS = domainMap.get("namespace").toString();
    final String domainUid = domain.getDomainUid();

    int counterNum = 4;
    String testAppPath = testAppName + "/CounterServlet";
    String webServiceSetUrl = testAppPath + "?setCounter=" + counterNum;
    String webServiceGetUrl = testAppPath + "?getCounter";

    String primaryServ = "primary";
    String count = "count";

    // Send the first HTTP request and save HTTP header/sessionID info
    ExecResult result = getHttpResponse(webServiceSetUrl, " -D ");

    // Get primary server name & count number bf primaryServName1 is stopped
    String primaryServName1 = getHttpResponseAttribute(result.stdout(), primaryServ);
    final String countattribute1 = getHttpResponseAttribute(result.stdout(), count);

    // Stop primary server
    domain.shutdownManagedServerUsingServerStartPolicy(primaryServName1);

    // Send the second HTTP request using HTTP header/sessionID info save before
    result = getHttpResponse(webServiceGetUrl, " -b ");

    // Get primary server name & count number af primaryServName1 is stopped
    String primaryServName2 = getHttpResponseAttribute(result.stdout(), primaryServ);
    String countattribute2 = getHttpResponseAttribute(result.stdout(), count);

    // Verify that the count number is from a new primary server
    Assumptions.assumeFalse(
        primaryServName1.trim().equals(primaryServName2.trim()),
        "A new primary server should be picked!");

    // Verify that HTTP session state is migrated by checking the count number same
    // bf and af primaryServName1 stopped
    Assumptions.assumeTrue(
        countattribute1.equals(countattribute2), "HTTP session state is NOT migrated!");

    // Restore test env
    domain.restartManagedServerUsingServerStartPolicy(primaryServName1);
    TestUtils.checkPodReady(domainUid + "-" + primaryServName1, domainNS);

    LoggerHelper.getLocal().log(Level.INFO, "SUCCESS - "
        + testMethodName + ". HTTP session state is migrated!");
  }

  /**
   * Get HTTP response from the web app deployed on wls.
   *
   * @param webServiceUrl - web server URL
   * @param headerOption  - option to save HTTP header info or use it
   * @throws Exception exception
   */
  private ExecResult getHttpResponse(String webServiceUrl, String headerOption) throws Exception {
    // Send a HTTP request
    String curlCmd = buildWebServiceUrl(webServiceUrl, headerOption + httpHeaderFile);
    LoggerHelper.getLocal().log(Level.INFO, "Send a HTTP request: " + curlCmd);

    ExecResult result = TestUtils.exec(curlCmd);

    return result;
  }

  /**
   * Get the value of a HTTP attribute.
   *
   * @param httpResponseString - HTTP response
   * @param attribute          - attribute name to find in the HTTP response
   * @throws Exception exception
   */
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

  /**
   * Build web server url.
   *
   * @param curlUrlPath   - URL path sent by curl
   * @param paramToAppend - params need to be appended to the URL path
   * @throws Exception exception
   */
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
