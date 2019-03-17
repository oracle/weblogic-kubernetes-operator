// Copyright 2018, Oracle Corporation and/or its affiliates.  All rights reserved.
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
import org.junit.Ignore;
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

  // property file used to customize operator properties for operator inputs yaml
  private static String operatorYmlFile = "operator1.yaml";

  // file used to customize domain properties for domain, PV and LB inputs yaml
  private static String domainonpvwlstFile = "domainonpvwlst.yaml";

  // property file used to configure constants for integration tests
  private static String appPropsFile = "OperatorIT.properties";

  private static final String TESTAPPNAME = "httpsessionreplication";
  private static Map<String, String> httpAttrMap;

  private static String httpHeaderFile;

  private static Operator operator;
  private static Domain domain;

  private static boolean QUICKTEST;
  private static boolean SMOKETEST;
  private static boolean JENKINS;
  private static boolean INGRESSPERDOMAIN = true;

  // Set QUICKTEST env var to true to run a small subset of tests.
  // Set SMOKETEST env var to true to run an even smaller subset
  // of tests, plus leave domain1 up and running when the test completes.
  // set INGRESSPERDOMAIN to false to create LB's ingress by kubectl yaml file
  static {
    QUICKTEST =
        System.getenv("QUICKTEST") != null && System.getenv("QUICKTEST").equalsIgnoreCase("true");
    SMOKETEST =
        System.getenv("SMOKETEST") != null && System.getenv("SMOKETEST").equalsIgnoreCase("true");
    if (SMOKETEST) QUICKTEST = true;
    if (System.getenv("JENKINS") != null) {
      JENKINS = new Boolean(System.getenv("JENKINS")).booleanValue();
    }
    if (System.getenv("INGRESSPERDOMAIN") != null) {
      INGRESSPERDOMAIN = new Boolean(System.getenv("INGRESSPERDOMAIN")).booleanValue();
    }
  }

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
      initialize(appPropsFile);

      // create operator1
      if (operator == null) {
        logger.info("Creating Operator & waiting for the script to complete execution");
        operator = TestUtils.createOperator(operatorYmlFile);
      }

      // create domain
      if (domain == null) {
        logger.info("Creating WLS Domain & waiting for the script to complete execution");
        domain = TestUtils.createDomain(domainonpvwlstFile);
        domain.verifyDomainCreated();

        domain.deployWebAppViaREST(
            TESTAPPNAME,
            BaseTest.getProjectRoot() + "/src/integration-tests/apps/httpsessionreplication.war",
            BaseTest.getUsername(),
            BaseTest.getPassword());

        // wait sometime for deployment gets ready
        Thread.sleep(30 * 1000);

        httpHeaderFile = BaseTest.getResultDir() + "/headers";

        httpAttrMap = new HashMap<String, String>();
        httpAttrMap.put("sessioncreatetime", "(.*)sessioncreatetime>(.*)</sessioncreatetime(.*)");
        httpAttrMap.put("sessionid", "(.*)sessionid>(.*)</sessionid(.*)");
        httpAttrMap.put("primary", "(.*)primary>(.*)</primary(.*)");
        httpAttrMap.put("secondary", "(.*)secondary>(.*)</secondary(.*)");
        httpAttrMap.put("count", "(.*)countattribute>(.*)</countattribute(.*)");
      }
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
   * verify that after the primary server shutdown, a new and running managed server is picked up as
   * the primary server
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

    String testAppPath = TESTAPPNAME + "/CounterServlet";
    String sessCreateTime = "sessioncreatetime";
    String primaryServ = "primary";

    // Send the 1st HTTP request and save http header/sessionID info
    logger.info("Send the first HTTP request to query Primary server name.");
    String curlSaveHttpHeader = " -D " + httpHeaderFile;
    String curlCmd = buildWebServiceUrl(testAppPath, curlSaveHttpHeader);
    ExecResult result = execCurlCmd(curlCmd);
    // logger.info("HTTP resp bf promary server is stopped:\n " + result.stdout());

    // get primary server name & session create time bf primaryServName1 is stopped
    String primaryServName1 = getHttpResponseAttribute(result.stdout(), primaryServ);
    String sessCreateTime1 = getHttpResponseAttribute(result.stdout(), sessCreateTime);
    logger.info("Primary server before is: " + primaryServName1);
    logger.info("Session created time before is: " + sessCreateTime1);

    // stop primary server
    // TODO this doesn't work. need to file a bug
    // domain.shutdownManagedServerUsingServerStartPolicy(primaryServName1);
    String msPodName = domainUid + "-" + primaryServName1;
    String cmd = "kubectl delete po/" + msPodName + " -n " + domainNS;
    logger.info("Stop managed server <" + primaryServName1 + "> using command:\n" + cmd);
    result = ExecCommand.exec(cmd);
    logger.info(result.stdout());

    // Send the 2nd HTTP request by use http header/sessionID info save before
    logger.info("Send the second HTTP request to query Primary server name.");
    String curlUseHttpHeader = " -b " + httpHeaderFile;
    curlCmd = buildWebServiceUrl(testAppPath, curlUseHttpHeader);
    result = execCurlCmd(curlCmd);
    // logger.info("HTTP resp af promary server is stopped:\n " + result.stdout());

    // get primary server name & session create time af primaryServName1 is stopped
    String primaryServName2 = getHttpResponseAttribute(result.stdout(), primaryServ);
    String sessCreateTime2 = getHttpResponseAttribute(result.stdout(), sessCreateTime);
    logger.info("Primary server after is: " + primaryServName2);
    logger.info("Session created time after is: " + sessCreateTime2);

    // verify that the same session info is used
    Assume.assumeTrue("HTTP Session should NOT change!", sessCreateTime1.equals(sessCreateTime2));

    // verify that a new primary server is picked
    Assume.assumeFalse(
        "A new primary server should be picked!",
        primaryServName1.trim().equals(primaryServName2.trim()));

    // restore test env
    // TODO will use this when it works
    // domain.restartManagedServerUsingServerStartPolicy(primaryServName1);
    // wait sometime for ms pod gets ready
    Thread.sleep(30 * 1000);
    TestUtils.checkPodCreated(domainUid + "-" + primaryServName1, domainNS);
    TestUtils.checkPodReady(domainUid + "-" + primaryServName1, domainNS);

    logger.info("SUCCESS - " + testMethodName + ". ms <" + primaryServName2 + "> now is primary.");
  }

  /**
   * verify that after the primary server shutdown, HTTP session state is migrated to the new
   * primary server
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
    String testAppPath = TESTAPPNAME + "/CounterServlet";
    String webServiceSetUrl = testAppPath + "?setCounter=" + counterNum;
    String webServiceGetUrl = testAppPath + "?getCounter";

    String primaryServ = "primary";
    String count = "count";

    // Send the 1st HTTP request and save http header/sessionID info
    logger.info("Send the first HTTP request to set a count number.");
    String curlSaveHttpHeader = " -D " + httpHeaderFile;
    String curlCmd = buildWebServiceUrl(webServiceSetUrl, curlSaveHttpHeader);
    ExecResult result = execCurlCmd(curlCmd);
    // logger.info("HTTP resp bf promary server is stopped:\n " + result.stdout());

    // get primary server name & count number bf primaryServName1 is stopped
    String primaryServName1 = getHttpResponseAttribute(result.stdout(), primaryServ);
    String countattribute1 = getHttpResponseAttribute(result.stdout(), count);
    logger.info("Primary server before is: " + primaryServName1);
    logger.info("count is set to: " + countattribute1);

    // stop primary server
    // TODO this doesn't work. need to file a bug
    // domain.shutdownManagedServerUsingServerStartPolicy(primaryServName1);
    String msPodName = domainUid + "-" + primaryServName1;
    String cmd = "kubectl delete po/" + msPodName + " -n " + domainNS;
    logger.info("Stop managed server <" + msPodName + "> using command:\n" + cmd);
    result = ExecCommand.exec(cmd);
    logger.info(result.stdout());

    // Send the 2nd HTTP request by use http header/sessionID info save before
    logger.info("Send the second HTTP request to get a count number.");
    String curlUseHttpHeader = " -b " + httpHeaderFile;
    curlCmd = buildWebServiceUrl(webServiceGetUrl, curlUseHttpHeader);
    result = execCurlCmd(curlCmd);
    // logger.info("HTTP response af promary server is stopped: \n" + result.stdout());

    // get primary server name & count number af primaryServName1 is stopped
    String primaryServName2 = getHttpResponseAttribute(result.stdout(), primaryServ);
    String countattribute2 = getHttpResponseAttribute(result.stdout(), count);

    logger.info("Primary server after is: " + primaryServName2);
    logger.info("Get count: " + countattribute2);

    // verify that the count number is from a new primary server
    Assume.assumeFalse(
        "A new primary server should be picked!",
        primaryServName1.trim().equals(primaryServName2.trim()));

    // verify that HTTP session state is migrated by checking the count number same
    // bf and af primaryServName1 stopped
    Assume.assumeTrue(
        "HTTP session state is NOT migrated!", countattribute1.equals(countattribute2));

    // restore test env
    // TODO will use this when it works
    // domain.restartManagedServerUsingServerStartPolicy(primaryServName1);
    // wait sometime for ms pod gets ready
    Thread.sleep(30 * 1000);
    TestUtils.checkPodCreated(domainUid + "-" + primaryServName1, domainNS);
    TestUtils.checkPodReady(domainUid + "-" + primaryServName1, domainNS);

    logger.info("SUCCESS - " + testMethodName + ". HTTP session state is migrated!");
  }

  @Ignore
  @Test
  public void myTest1() throws Exception {
    Assume.assumeFalse(QUICKTEST);
    Map<String, Object> domainMap = domain.getDomainMap();
    String domainNS = domainMap.get("namespace").toString();

    domain.shutdownManagedServerUsingServerStartPolicy("managed-server2");
    TestUtils.checkPodDeleted("domainonpvwlst-managed-server2", domainNS);
  }

  @Ignore
  @Test
  public void myTest2() throws Exception {
    Assume.assumeFalse(QUICKTEST);
    Map<String, Object> domainMap = domain.getDomainMap();
    String domainNS = domainMap.get("namespace").toString();

    domain.shutdownManagedServerUsingServerStartPolicy("domainonpvwlst-managed-server1");
    TestUtils.checkPodDeleted("domainonpvwlst-managed-server1", domainNS);
  }

  /**
   * build
   *
   * @throws Exception
   */
  private String getHttpResponseAttribute(String httpResponseString, String attribute)
      throws Exception {
    // logger.info("Looking for <" + attribute + "> in\n " + httpResponseString);
    String attrPatn = httpAttrMap.get(attribute);

    Assume.assumeNotNull(attrPatn);

    String httpAttribute = null;

    Pattern pattern = Pattern.compile(attrPatn);
    Matcher matcher = pattern.matcher(httpResponseString);
    if (matcher.find()) {
      httpAttribute = matcher.group(2);
    }

    // logger.info("Value for HTTP attribute found.: " + attribute + " = " + httpAttribute);

    return httpAttribute;
  }

  /**
   * build
   *
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

    logger.info("Built web service url: " + webServiceUrl);

    return webServiceUrl.toString();
  }

  /**
   * build
   *
   * @throws Exception
   */
  private ExecResult execCurlCmd(String curlCmd) throws Exception {
    logger.info("curl command to exec is:\n" + curlCmd);

    ExecResult result = ExecCommand.exec(curlCmd);

    if (result.exitValue() != 0) {
      throw new RuntimeException(
          "FAILURE: command "
              + curlCmd
              + " failed, returned "
              + result.stderr()
              + "\n "
              + result.stdout());
    }

    // logger.info("HTTP response is:\n " + result.stdout());

    return result;
  }
}
