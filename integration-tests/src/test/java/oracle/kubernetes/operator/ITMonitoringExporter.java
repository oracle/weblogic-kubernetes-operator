// Copyright 2019, Oracle Corporation and/or its affiliates.  All rights reserved.
// Licensed under the Universal Permissive License v 1.0 as shown at
// http://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import com.gargoylesoftware.htmlunit.FailingHttpStatusCodeException;
import com.gargoylesoftware.htmlunit.WebClient;
import com.gargoylesoftware.htmlunit.html.HtmlFileInput;
import com.gargoylesoftware.htmlunit.html.HtmlForm;
import com.gargoylesoftware.htmlunit.html.HtmlPage;
import com.gargoylesoftware.htmlunit.html.HtmlRadioButtonInput;
import com.gargoylesoftware.htmlunit.html.HtmlSubmitInput;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.Authenticator;
import java.net.PasswordAuthentication;
import java.net.URL;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import javax.xml.bind.DatatypeConverter;
import oracle.kubernetes.operator.utils.Domain;
import oracle.kubernetes.operator.utils.ExecCommand;
import oracle.kubernetes.operator.utils.ExecResult;
import oracle.kubernetes.operator.utils.Operator;
import oracle.kubernetes.operator.utils.TestUtils;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.Assume;
import org.junit.BeforeClass;
import org.junit.FixMethodOrder;
import org.junit.Test;
import org.junit.runners.MethodSorters;

/** This test is used for testing Monitoring Exporter with Operator(s) */
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
public class ITMonitoringExporter extends BaseTest {

  private static int number = 3;
  private static Operator operator = null;
  private static Domain domain = null;
  private static String myhost = "";
  private static String metricsUrl = "";
  private static String exporterUrl = "";
  private static String serverName = "managed-server";
  private static String configPath = "";
  private static String appName = TESTWSAPP;
  private static String appNameUC = "TestWSApp";
  private static String testWSAppTotalServletInvokesSearchKey1 =
      "weblogic_servlet_invocation_total_count{app=\"testwsapp\",name=\"managed-server1_/TestWSApp\",servletName=\"TestWSAppServlethttp\"}";
  private static String testWSAppTotalServletInvokesSearchKey2 =
      "weblogic_servlet_invocation_total_count{app=\"testwsapp\",name=\"managed-server2_/TestWSApp\",servletName=\"TestWSAppServlethttp\"}";
  private static String[] testWSAppTotalServletInvokesSearchKey = {
    "weblogic_servlet_invocation_total_count{app=\"testwsapp\",name=\"managed-server",
    "_/TestWSApp\",servletName=\"TestWSAppServlethttp\"}"
  };
  String oprelease = "op" + number;
  private int waitTime = 5;
  private int maxIterations = 30;
  private static String loadBalancer = "TRAEFIK";
  // private static String prometheusSearchKey1 = "curl --noproxy '*' -X GET
  // http://slc13kef.us.oracle.com:32000/api/v1/query?query=heap_free_current%7Bname%3D%22managed-server2%22%7D";
  private static String prometheusSearchKey1 =
      "heap_free_current%7Bname%3D%22managed-server1%22%7D";
  private static String prometheusSearchKey2 =
      "heap_free_current%7Bname%3D%22managed-server2%22%7D";
  private static String testwsappPrometheusSearchKey =
      "weblogic_servlet_invocation_total_count%7Bapp%3D%22testwsapp%22%7D";

  /**
   * This method gets called only once before any of the test methods are executed. It does the
   * initialization of the integration test properties defined in OperatorIT.properties and setting
   * the resultRoot, pvRoot and projectRoot attributes.
   *
   * @throws Exception
   */
  @BeforeClass
  public static void staticPrepare() throws Exception {
    if (!QUICKTEST) {
      initialize(APP_PROPS_FILE);

      logger.info("Checking if operator and domain are running, if not creating");
      if (operator == null) {
        Map<String, Object> operatorMap = TestUtils.createOperatorMap(number, true);
        operator = new Operator(operatorMap, Operator.RESTCertType.SELF_SIGNED);
        Assert.assertNotNull(operator);
        operator.callHelmInstall();
      }
      if (domain == null) {
        domain = createVerifyDomain(number, operator);
        Assert.assertNotNull(domain);
      }

      myhost = domain.getHostNameForCurl();
      exporterUrl = "http://" + myhost + ":" + domain.getLoadBalancerWebPort() + "/wls-exporter/";
      metricsUrl = exporterUrl + "metrics";
      configPath = BaseTest.getProjectRoot() + "/integration-tests/src/test/resources/exporter/";
      upgradeTraefikHostName();
      deployRunMonitoringExporter(domain, operator);
      buildDeployWebServiceApp(domain, TESTWSAPP, TESTWSSERVICE);
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
      logger.info("+++++++++++++++++++++++++++++++++---------------------------------+");
      logger.info("BEGIN");
      logger.info("Run once, release cluster lease");
      if (domain != null) {
        domain.destroy();
      }
      if (operator != null) {
        operator.destroy();
      }

      tearDown(new Object() {}.getClass().getEnclosingClass().getSimpleName());

      logger.info("SUCCESS");
    }
  }

  /**
   * clone, build , deploy monitoring exporter on specified domain, operator
   *
   * @throws Exception
   */
  private static void deployRunMonitoringExporter(Domain domain, Operator operator)
      throws Exception {

    TestUtils.gitCloneBuildMonitoringExporter();
    logger.info("Creating Operator & waiting for the script to complete execution");
    boolean testCompletedSuccessfully = false;
    startExporterPrometheusGrafana(domain, operator);
    // check if exporter is up
    domain.callWebAppAndVerifyLoadBalancing("wls-exporter", false);
    testCompletedSuccessfully = true;
    logger.info("SUCCESS - deployRunMonitoringExporter");
  }

  /**
   * create operator, domain, run some verification tests to check domain runtime
   *
   * @throws Exception
   */
  private static Domain createVerifyDomain(int number, Operator operator) throws Exception {
    logger.info("create domain with UID : test" + number);
    Domain domain = TestUtils.createDomain(TestUtils.createDomainMap(number));
    domain.verifyDomainCreated();
    TestUtils.renewK8sClusterLease(getProjectRoot(), getLeaseId());
    logger.info("verify that domain is managed by operator");
    operator.verifyDomainExists(domain.getDomainUid());
    return domain;
  }

  /**
   * call webapp and verify load balancing by checking server name in the response
   *
   * @param searchKey - metric query expression
   * @param expectedVal - expected metrics to search
   * @throws Exception
   */
  public boolean checkMetricsViaPrometheus(String searchKey, String expectedVal) throws Exception {

    // sleep 15 secs to scrap metrics
    Thread.sleep(15 * 1000);
    // url
    StringBuffer testAppUrl = new StringBuffer("http://");
    testAppUrl.append(myhost).append(":").append("32000").append("/api/v1/query?query=");

    testAppUrl.append(searchKey);
    // curl cmd to call webapp
    StringBuffer curlCmd = new StringBuffer("curl  --noproxy '*' ");
    curlCmd.append(testAppUrl.toString());
    logger.info("Curl cmd " + curlCmd);
    ExecResult result = ExecCommand.exec(curlCmd.toString());
    logger.info("Prometheus application invoked successfully with curlCmd:" + curlCmd);

    String checkPrometheus = result.stdout().trim();
    logger.info("Result :" + checkPrometheus);
    return checkPrometheus.contains(expectedVal);
  }

  private static void startExporterPrometheusGrafana(Domain domain, Operator operator)
      throws Exception {

    logger.info("deploy exporter, prometheus, grafana ");
    TestUtils.deployMonitoringExporterPrometethusGrafana(
        TestUtils.monitoringDir + "/apps/monitoringexporter/wls-exporter.war", domain, operator);
  }

  private static Object getMetricsValue(BufferedReader contents, String metricKey)
      throws Exception {
    boolean found = false;
    String line;
    String result = null;
    while ((line = contents.readLine()) != null) {
      if (line.contains(metricKey)) {
        found = true;
        result = line.substring(line.lastIndexOf(" ") + 1);
      }
    }
    return result;
  }

  public static boolean containsWordsIndexOf(String inputString, String[] words) {
    boolean found = true;
    for (String word : words) {
      logger.info(" Checking inputString" + inputString + " word " + word);
      if (inputString.indexOf(word) == -1) {
        found = false;
        break;
      }
    }
    return found;
  }

  private static Object getMetricsValue(BufferedReader contents, String... metricExp)
      throws Exception {
    boolean found = false;
    String line;
    String result = null;
    while ((line = contents.readLine()) != null) {
      if (containsWordsIndexOf(line, metricExp)) {
        logger.info("found metric value for " + line + " :");
        result = line.substring(line.lastIndexOf(" ") + 1);
      }
    }
    return result;
  }

  private static void setCredentials(WebClient webClient) {
    String base64encodedUsernameAndPassword =
        base64Encode(BaseTest.getUsername() + ":" + BaseTest.getPassword());
    webClient.addRequestHeader("Authorization", "Basic " + base64encodedUsernameAndPassword);
  }

  private static void setCredentials(WebClient webClient, String username, String password) {
    String base64encodedUsernameAndPassword = base64Encode(username + ":" + password);
    webClient.addRequestHeader("Authorization", "Basic " + base64encodedUsernameAndPassword);
  }

  private static String base64Encode(String stringToEncode) {
    return DatatypeConverter.printBase64Binary(stringToEncode.getBytes());
  }

  private static void upgradeTraefikHostName() throws Exception {
    String chartDir =
        BaseTest.getProjectRoot()
            + "/integration-tests/src/test/resources/charts/ingress-per-domain";
    StringBuffer cmd = new StringBuffer("helm upgrade ");
    cmd.append("--reuse-values ")
        .append("--set ")
        .append("\"")
        .append("traefik.hostname=")
        .append("\"")
        .append(" traefik-ingress-test" + number + " " + chartDir);

    logger.info(" upgradeTraefikNamespace() Running " + cmd.toString());
    TestUtils.executeCmd(cmd.toString());
  }

  /**
   * Replace monitoring exporter configuration and verify it was applied to both managed servers
   *
   * @throws Exception
   */
  @Test
  public void test01_CheckMetricsViaPrometheus() throws Exception {
    Assume.assumeFalse(QUICKTEST);
    String testMethodName = new Object() {}.getClass().getEnclosingMethod().getName();
    logTestBegin(testMethodName);
    boolean testCompletedSuccessfully = false;
    assertTrue(checkMetricsViaPrometheus(testwsappPrometheusSearchKey, "testwsapp"));
    testCompletedSuccessfully = true;
    logger.info("SUCCESS - " + testMethodName);
  }

  /**
   * Replace monitoring exporter configuration and verify it was applied to both managed servers
   *
   * @throws Exception
   */
  // commenting out due bug OWLS-74163
  // @Test
  public void test02_ReplaceConfiguration() throws Exception {
    Assume.assumeFalse(QUICKTEST);
    String testMethodName = new Object() {}.getClass().getEnclosingMethod().getName();
    logTestBegin(testMethodName);
    boolean testCompletedSuccessfully = false;

    /*
        exporterUrl = "http://slc13kef.us.oracle.com:" + "30305" + "/wls-exporter/";
        metricsUrl = exporterUrl + "metrics";
        configPath =
            "/scratch/mkogan/weblogic-kubernetes-operator/integration-tests/src/test/resources/exporter";
        String testWSAppTotalServletInvokesSearchKey1 =
            "weblogic_servlet_invocation_total_count{app=\"testwsapp\",name=\"managed-server1_/TestWSApp\",servletName=\"TestWSAppServlethttp\"}";
        String testWSAppTotalServletInvokesSearchKey2 =
            "weblogic_servlet_invocation_total_count{app=\"testwsapp\",name=\"managed-server2_/TestWSApp\",servletName=\"TestWSAppServlethttp\"}";
    */
    HtmlPage page = submitConfigureForm(exporterUrl, "replace", configPath + "/rest_jvm.yml");

    // check for updated metrics
    String searchKey1 = "heap_free_current{name=\"managed-server1\"}";
    String searchKey2 = "heap_free_current{name=\"managed-server2\"}";
    boolean isFoundNewKey1 = false;
    boolean isFoundNewKey2 = false;
    boolean isFoundOldKey1 = true;
    boolean isFoundOldKey2 = true;

    isFoundNewKey1 = checkMetrics(searchKey1);
    isFoundNewKey2 = checkMetrics(searchKey2);
    isFoundOldKey1 = checkMetrics(testWSAppTotalServletInvokesSearchKey1);
    isFoundOldKey2 = checkMetrics(testWSAppTotalServletInvokesSearchKey2);
    String foundResults =
        " server1: ( newMetrics:"
            + isFoundNewKey1
            + " oldMetrics: "
            + isFoundOldKey1
            + ") server2: ( newMetrics:"
            + isFoundNewKey2
            + " oldMetrics: "
            + isFoundOldKey2
            + ")";
    if (isFoundNewKey1 && isFoundNewKey2) {
      logger.info("Updated Metrics for both managed servers are found");
      assertFalse(
          "Old configuration still presented " + foundResults, isFoundOldKey1 && isFoundOldKey2);
    } else {
      if ((isFoundNewKey1 || isFoundNewKey2) || (!isFoundOldKey1 || !isFoundOldKey2)) {
        throw new RuntimeException(
            "FAILURE: coordinator does not update config for one of the managed-server:"
                + foundResults);
      }
      throw new RuntimeException("FAILURE: configuration has not updated - " + foundResults);
    }
    testCompletedSuccessfully = true;
    logger.info("SUCCESS - " + testMethodName);
  }

  /**
   * Add additional monitoring exporter configuration and verify it was applied
   *
   * @throws Exception
   */
  @Test
  public void test03_AppendConfiguration() throws Exception {

    Assume.assumeFalse(QUICKTEST);
    String testMethodName = new Object() {}.getClass().getEnclosingMethod().getName();
    logTestBegin(testMethodName);
    boolean testCompletedSuccessfully = false;
    // make sure some config is there
    HtmlPage page = submitConfigureForm(exporterUrl, "replace", configPath + "/rest_jvm.yml");
    assertTrue(page.asText().contains("JVMRuntime"));
    assertFalse(page.asText().contains("WebAppComponentRuntime"));
    // run append more
    page = submitConfigureForm(exporterUrl, "append", configPath + "/rest_webapp.yml");
    assertTrue(page.asText().contains("WebAppComponentRuntime"));
    // check previous config is there
    assertTrue(page.asText().contains("JVMRuntime"));
    // due coordinator bug just checking one of servers
    assertTrue(
        checkMetricsViaPrometheus(
                prometheusSearchKey1, "\"weblogic_serverName\":\"managed-server1\"")
            || checkMetricsViaPrometheus(
                prometheusSearchKey2, "\"weblogic_serverName\":\"managed-server2\""));
    assertTrue(checkMetricsViaPrometheus(testwsappPrometheusSearchKey, "testwsapp"));
    testCompletedSuccessfully = true;
    logger.info("SUCCESS - " + testMethodName);
  }

  /**
   * Replace monitoring exporter configuration with only one attribute and verify it was applied
   *
   * @throws Exception
   */
  @Test
  public void test04_ReplaceOneAttributeValueAsArrayConfiguration() throws Exception {

    Assume.assumeFalse(QUICKTEST);
    String testMethodName = new Object() {}.getClass().getEnclosingMethod().getName();
    logTestBegin(testMethodName);
    boolean testCompletedSuccessfully = false;

    HtmlPage page =
        submitConfigureForm(exporterUrl, "replace", configPath + "/rest_oneattribval.yml");
    assertTrue(page.asText().contains("values: invocationTotalCount"));
    assertFalse(page.asText().contains("reloadTotal"));
    testCompletedSuccessfully = true;
    logger.info("SUCCESS - " + testMethodName);
  }

  /**
   * Append monitoring exporter configuration with one more attribute and verify it was applied
   * append to [a] new config [a,b]
   *
   * @throws Exception
   */
  @Test
  public void test05_AppendArrayWithOneExistedAndOneDifferentAttributeValueAsArrayConfiguration()
      throws Exception {

    Assume.assumeFalse(QUICKTEST);
    String testMethodName = new Object() {}.getClass().getEnclosingMethod().getName();
    logTestBegin(testMethodName);
    boolean testCompletedSuccessfully = false;
    HtmlPage page =
        submitConfigureForm(exporterUrl, "replace", configPath + "/rest_oneattribval.yml");
    assertTrue(page.asText().contains("values: invocationTotalCount"));
    page = submitConfigureForm(exporterUrl, "append", configPath + "/rest_twoattribs.yml");
    assertTrue(page.asText().contains("values: [invocationTotalCount, executionTimeAverage]"));
    testCompletedSuccessfully = true;
    logger.info("SUCCESS - " + testMethodName);
  }

  /**
   * Replace monitoring exporter configuration with empty configuration
   *
   * @throws Exception
   */
  @Test
  public void test06_ReplaceWithEmptyConfiguration() throws Exception {
    Assume.assumeFalse(QUICKTEST);
    String testMethodName = new Object() {}.getClass().getEnclosingMethod().getName();
    logTestBegin(testMethodName);
    boolean testCompletedSuccessfully = false;
    HtmlPage page = submitConfigureForm(exporterUrl, "replace", configPath + "/rest_empty.yml");
    assertTrue(page.asText().contains("queries:") && !page.asText().contains("values"));
    testCompletedSuccessfully = true;
    logger.info("SUCCESS - " + testMethodName);
  }

  /**
   * Replace monitoring exporter configuration with not existed config file
   *
   * @throws Exception
   */
  @Test
  public void test06_1ReplaceWithNotExistedFileConfiguration() throws Exception {
    Assume.assumeFalse(QUICKTEST);
    String testMethodName = new Object() {}.getClass().getEnclosingMethod().getName();
    logTestBegin(testMethodName);
    boolean testCompletedSuccessfully = false;
    HtmlPage page = submitConfigureForm(exporterUrl, "replace", "/scratch/m/test.yml");
    assertTrue(page.asText().contains("queries:") && !page.asText().contains("values"));
    testCompletedSuccessfully = true;
    logger.info("SUCCESS - " + testMethodName);
  }

  /**
   * Replace monitoring exporter configuration with no file name for configuration
   *
   * @throws Exception
   */
  @Test
  public void test06_2ReplaceWithNoFileNameConfiguration() throws Exception {
    Assume.assumeFalse(QUICKTEST);
    String testMethodName = new Object() {}.getClass().getEnclosingMethod().getName();
    logTestBegin(testMethodName);
    boolean testCompletedSuccessfully = false;
    HtmlPage page = submitConfigureForm(exporterUrl, "replace", "");
    assertTrue(page.asText().contains("queries:") && !page.asText().contains("values"));
    testCompletedSuccessfully = true;
    logger.info("SUCCESS - " + testMethodName);
  }

  /**
   * Try to append monitoring exporter configuration with empty configuration
   *
   * @throws Exception
   */
  @Test
  public void test07_AppendWithEmptyConfiguration() throws Exception {
    Assume.assumeFalse(QUICKTEST);
    String testMethodName = new Object() {}.getClass().getEnclosingMethod().getName();
    logTestBegin(testMethodName);
    boolean testCompletedSuccessfully = false;
    final WebClient webClient = new WebClient();
    HtmlPage originalPage = webClient.getPage(exporterUrl);
    assertNotNull(originalPage);
    HtmlPage page = submitConfigureForm(exporterUrl, "append", configPath + "/rest_empty.yml");
    assertTrue(originalPage.asText().equals(page.asText()));
    testCompletedSuccessfully = true;
    logger.info("SUCCESS - " + testMethodName);
  }

  /**
   * Try to append monitoring exporter configuration with not existed configuration file
   *
   * @throws Exception
   */
  @Test
  public void test07_1AppendWithNotExistedFileConfiguration() throws Exception {
    Assume.assumeFalse(QUICKTEST);
    String testMethodName = new Object() {}.getClass().getEnclosingMethod().getName();
    logTestBegin(testMethodName);
    boolean testCompletedSuccessfully = false;
    final WebClient webClient = new WebClient();
    HtmlPage originalPage = webClient.getPage(exporterUrl);
    assertNotNull(originalPage);
    HtmlPage page = submitConfigureForm(exporterUrl, "append", "/scratch/m/test.yml");
    assertTrue(originalPage.asText().equals(page.asText()));
    testCompletedSuccessfully = true;
    logger.info("SUCCESS - " + testMethodName);
  }

  /**
   * Try to append monitoring exporter configuration with null name for configuration file
   *
   * @throws Exception
   */
  @Test
  public void test07_2AppendWithNoFileNameConfiguration() throws Exception {
    Assume.assumeFalse(QUICKTEST);
    String testMethodName = new Object() {}.getClass().getEnclosingMethod().getName();
    logTestBegin(testMethodName);
    boolean testCompletedSuccessfully = false;
    final WebClient webClient = new WebClient();
    HtmlPage originalPage = webClient.getPage(exporterUrl);
    assertNotNull(originalPage);
    HtmlPage page = submitConfigureForm(exporterUrl, "append", "");
    assertTrue(originalPage.asText().equals(page.asText()));
    testCompletedSuccessfully = true;
    logger.info("SUCCESS - " + testMethodName);
  }

  /**
   * Try to append monitoring exporter configuration with configuration file not in the yaml format
   *
   * @throws Exception
   */
  @Test
  public void test08_1AppendWithNotYmlConfiguration() throws Exception {
    Assume.assumeFalse(QUICKTEST);
    String testMethodName = new Object() {}.getClass().getEnclosingMethod().getName();
    logTestBegin(testMethodName);
    boolean testCompletedSuccessfully = false;
    changeConfigNegative(
        "append", configPath + "/rest_notymlformat.yml", "Configuration is not in YAML format");
    testCompletedSuccessfully = true;
    logger.info("SUCCESS - " + testMethodName);
  }

  /**
   * Try to replace monitoring exporter configuration with configuration file not in the yaml format
   *
   * @throws Exception
   */
  @Test
  public void test08_2ReplaceWithNotYmlConfiguration() throws Exception {
    Assume.assumeFalse(QUICKTEST);
    String testMethodName = new Object() {}.getClass().getEnclosingMethod().getName();
    logTestBegin(testMethodName);
    boolean testCompletedSuccessfully = false;
    changeConfigNegative(
        "replace", configPath + "/rest_notymlformat.yml", "Configuration is not in YAML format");
  }

  /**
   * Try to append monitoring exporter configuration with configuration file in the corrupted yaml
   * format
   *
   * @throws Exception
   */
  public void test09_AppendWithCorruptedYmlConfiguration() throws Exception {
    Assume.assumeFalse(QUICKTEST);
    String testMethodName = new Object() {}.getClass().getEnclosingMethod().getName();
    logTestBegin(testMethodName);
    boolean testCompletedSuccessfully = false;
    changeConfigNegative(
        "append",
        configPath + "/rest_notyml.yml",
        "Configuration YAML format has errors while scanning a simple key");
    testCompletedSuccessfully = true;
    logger.info("SUCCESS - " + testMethodName);
  }

  /**
   * Try to replace monitoring exporter configuration with configuration file in the corrupted yaml
   * format
   *
   * @throws Exception
   */
  @Test
  public void test10_ReplaceWithCorruptedYmlConfiguration() throws Exception {
    Assume.assumeFalse(QUICKTEST);
    String testMethodName = new Object() {}.getClass().getEnclosingMethod().getName();
    logTestBegin(testMethodName);
    boolean testCompletedSuccessfully = false;
    changeConfigNegative(
        "replace",
        configPath + "/rest_notyml.yml",
        "Configuration YAML format has errors while scanning a simple key");
    testCompletedSuccessfully = true;
    logger.info("SUCCESS - " + testMethodName);
  }

  /**
   * Try to replace monitoring exporter configuration with configuration file with dublicated values
   *
   * @throws Exception
   */
  @Test
  public void test11_ReplaceWithDublicatedValuesConfiguration() throws Exception {
    Assume.assumeFalse(QUICKTEST);
    String testMethodName = new Object() {}.getClass().getEnclosingMethod().getName();
    logTestBegin(testMethodName);
    boolean testCompletedSuccessfully = false;
    changeConfigNegative(
        "replace",
        configPath + "/rest_dublicatedval.yml",
        "Duplicate values for [deploymentState] at applicationRuntimes.componentRuntimes");
    testCompletedSuccessfully = true;
    logger.info("SUCCESS - " + testMethodName);
  }

  /**
   * Try to append monitoring exporter configuration with configuration file with dublicated values
   *
   * @throws Exception
   */
  @Test
  public void test12_AppendWithDublicatedValuesConfiguration() throws Exception {
    Assume.assumeFalse(QUICKTEST);
    String testMethodName = new Object() {}.getClass().getEnclosingMethod().getName();
    logTestBegin(testMethodName);
    boolean testCompletedSuccessfully = false;
    changeConfigNegative(
        "append",
        configPath + "/rest_dublicatedval.yml",
        "Duplicate values for [deploymentState] at applicationRuntimes.componentRuntimes");
    testCompletedSuccessfully = true;
    logger.info("SUCCESS - " + testMethodName);
  }

  /**
   * Try to replace monitoring exporter configuration with configuration file with
   * NameSnakeCase=false
   *
   * @throws Exception
   */
  @Test
  public void test13_ReplaceMetricsNameSnakeCaseFalseConfiguration() throws Exception {
    Assume.assumeFalse(QUICKTEST);
    String testMethodName = new Object() {}.getClass().getEnclosingMethod().getName();
    logTestBegin(testMethodName);
    boolean testCompletedSuccessfully = false;
    final WebClient webClient = new WebClient();
    HtmlPage originalPage = webClient.getPage(exporterUrl);
    assertNotNull(originalPage);
    HtmlPage page =
        submitConfigureForm(exporterUrl, "replace", configPath + "/rest_snakecasefalse.yml");
    assertNotNull(page);
    assertFalse(page.asText().contains("metricsNameSnakeCase"));
    String searchKey = "weblogic_servlet_executionTimeAverage%7Bapp%3D%22testwsapp%22%7D";
    assertTrue(checkMetricsViaPrometheus(searchKey, "testwsapp"));
    testCompletedSuccessfully = true;
    logger.info("SUCCESS - " + testMethodName);
  }

  /**
   * Try to append monitoring exporter configuration with NameSnakeCase=true to the original
   * configuration where NameSnakeCase=false will change NameSnakeCase=true
   *
   * @throws Exception
   */
  @Test
  public void test14_AppendMetricsNameSnakeCaseTrueToSnakeCaseFalseConfiguration()
      throws Exception {

    Assume.assumeFalse(QUICKTEST);
    String testMethodName = new Object() {}.getClass().getEnclosingMethod().getName();
    logTestBegin(testMethodName);
    boolean testCompletedSuccessfully = false;
    HtmlPage page =
        submitConfigureForm(exporterUrl, "append", configPath + "/rest_snakecasetrue.yml");
    assertNotNull(page);
    assertTrue(page.asText().contains("metricsNameSnakeCase"));
    String searchKey = "weblogic_servlet_executionTimeAverage%7Bapp%3D%22testwsapp%22%7D";
    assertFalse(checkMetricsViaPrometheus(searchKey, "testwsapp"));
    testCompletedSuccessfully = true;
    logger.info("SUCCESS - " + testMethodName);
  }

  /**
   * Try to change monitoring exporter configuration without authentication
   *
   * @throws Exception
   */
  // verify that change configuration fails without authentication
  @Test
  public void test15_ChangeConfigNoCredentials() throws Exception {
    Assume.assumeFalse(QUICKTEST);
    String testMethodName = new Object() {}.getClass().getEnclosingMethod().getName();
    logTestBegin(testMethodName);
    boolean testCompletedSuccessfully = false;
    WebClient webClient = new WebClient();
    String expectedErrorMsg = "401 Unauthorized for " + exporterUrl;
    try {
      HtmlPage page =
          submitConfigureForm(
              exporterUrl, "append", configPath + "/rest_snakecasetrue.yml", webClient);
      throw new RuntimeException("Form was submitted successfully with no credentials");
    } catch (FailingHttpStatusCodeException ex) {
      assertTrue((ex.getMessage()).contains(expectedErrorMsg));
    }
    testCompletedSuccessfully = true;
    logger.info("SUCCESS - " + testMethodName);
  }

  /**
   * Try to change monitoring exporter configuration with invalid username
   *
   * @throws Exception
   */
  @Test
  public void test16_ChangeConfigInvalidUser() throws Exception {
    Assume.assumeFalse(QUICKTEST);
    String testMethodName = new Object() {}.getClass().getEnclosingMethod().getName();
    logTestBegin(testMethodName);
    boolean testCompletedSuccessfully = false;
    changeConfigNegativeAuth(
        "replace",
        configPath + "/rest_snakecasetrue.yml",
        "401 Unauthorized for " + exporterUrl,
        "invaliduser",
        "welcome1");
    testCompletedSuccessfully = true;
    logger.info("SUCCESS - " + testMethodName);
  }

  /**
   * Try to change monitoring exporter configuration with invalid password
   *
   * @throws Exception
   */
  @Test
  public void test17_ChangeConfigInvalidPass() throws Exception {
    Assume.assumeFalse(QUICKTEST);
    String testMethodName = new Object() {}.getClass().getEnclosingMethod().getName();
    logTestBegin(testMethodName);
    boolean testCompletedSuccessfully = false;
    changeConfigNegativeAuth(
        "replace",
        configPath + "/rest_snakecasetrue.yml",
        "401 Unauthorized for " + exporterUrl,
        "weblogic",
        "invalidpass");
  }

  /**
   * Try to change monitoring exporter configuration with empty username
   *
   * @throws Exception
   */
  @Test
  public void test18_ChangeConfigEmptyUser() throws Exception {
    Assume.assumeFalse(QUICKTEST);
    String testMethodName = new Object() {}.getClass().getEnclosingMethod().getName();
    logTestBegin(testMethodName);
    boolean testCompletedSuccessfully = false;
    changeConfigNegativeAuth(
        "replace",
        configPath + "/rest_snakecasetrue.yml",
        "401 Unauthorized for " + exporterUrl,
        "",
        "welcome1");
    testCompletedSuccessfully = true;
    logger.info("SUCCESS - " + testMethodName);
  }

  /**
   * Try to change monitoring exporter configuration with empty pass
   *
   * @throws Exception
   */
  @Test
  public void test19_ChangeConfigEmptyPass() throws Exception {
    Assume.assumeFalse(QUICKTEST);
    String testMethodName = new Object() {}.getClass().getEnclosingMethod().getName();
    logTestBegin(testMethodName);
    boolean testCompletedSuccessfully = false;
    changeConfigNegativeAuth(
        "replace",
        configPath + "/rest_snakecasetrue.yml",
        "401 Unauthorized for " + exporterUrl,
        "weblogic",
        "");
    testCompletedSuccessfully = true;
    logger.info("SUCCESS - " + testMethodName);
  }

  private void changeConfigNegative(String effect, String configFile, String expectedErrorMsg)
      throws Exception {
    final WebClient webClient = new WebClient();
    HtmlPage originalPage = webClient.getPage(exporterUrl);
    assertNotNull(originalPage);
    HtmlPage page = submitConfigureForm(exporterUrl, effect, configFile);
    assertTrue((page.asText()).contains(expectedErrorMsg));
    assertTrue(!(page.asText()).contains("Error 500--Internal Server Error"));
  }

  private void changeConfigNegativeAuth(
      String effect, String configFile, String expectedErrorMsg, String username, String password)
      throws Exception {
    try {
      HtmlPage page = submitConfigureForm(exporterUrl, effect, configFile, username, password);
      throw new RuntimeException("Expected exception was not thrown ");
    } catch (FailingHttpStatusCodeException ex) {
      assertTrue((ex.getMessage()).contains(expectedErrorMsg));
    }
  }

  private Object getMetricsFromPage(String testUrl, String... args) throws Exception {
    Authenticator.setDefault(new MyTestAuthenticator());
    InputStream stream = new URL(testUrl).openStream();
    BufferedReader contents = new BufferedReader(new InputStreamReader(stream));
    assertNotNull(contents);
    return getMetricsValue(contents, args);
  }

  private Object getMetricsFromPage(String testUrl, String searchKey) throws Exception {
    Authenticator.setDefault(new MyTestAuthenticator());
    InputStream stream = new URL(testUrl).openStream();
    BufferedReader contents = new BufferedReader(new InputStreamReader(stream));
    assertNotNull(contents);

    return getMetricsValue(contents, searchKey);
  }

  private HtmlPage submitConfigureForm(
      String exporterUrl, String effect, String configFile, String username, String password)
      throws Exception {
    final WebClient webClient = new WebClient();
    setCredentials(webClient, username, password);
    return submitConfigureForm(exporterUrl, effect, configFile, webClient);
  }

  private HtmlPage submitConfigureForm(String exporterUrl, String effect, String configFile)
      throws Exception {
    final WebClient webClient = new WebClient();
    setCredentials(webClient);
    return submitConfigureForm(exporterUrl, effect, configFile, webClient);
  }

  private HtmlPage submitConfigureForm(
      String exporterUrl, String effect, String configFile, WebClient webClient) throws Exception {

    // Get the first page
    final HtmlPage page1 = webClient.getPage(exporterUrl);
    assertNotNull(page1);
    assertTrue((page1.asText()).contains("This is the WebLogic Monitoring Exporter."));

    // Get the form that we are dealing with and within that form,
    // find the submit button and the field that we want to change.Generated form for cluster had
    // extra path for wls-exporter
    HtmlForm form = page1.getFirstByXPath("//form[@action='configure']");
    if (form == null) form = page1.getFirstByXPath("//form[@action='/wls-exporter/configure']");
    assertNotNull(form);
    List<HtmlRadioButtonInput> radioButtons = form.getRadioButtonsByName("effect");
    assertNotNull(radioButtons);
    for (HtmlRadioButtonInput radioButton : radioButtons) {
      if (radioButton.getValueAttribute().equalsIgnoreCase(effect)) {
        radioButton.setChecked(true);
      }
    }

    HtmlSubmitInput button =
        (HtmlSubmitInput) page1.getFirstByXPath("//form//input[@type='submit']");
    assertNotNull(button);
    final HtmlFileInput fileField = form.getInputByName("configuration");
    assertNotNull(fileField);

    // Change the value of the text field
    fileField.setValueAttribute(configFile);
    fileField.setContentType("multipart/form-data");

    // Now submit the form by clicking the button and get back the second page.
    HtmlPage page2 = button.click();
    assertNotNull(page2);
    return page2;
  }

  private boolean checkMetrics(String searchKey) throws Exception {
    boolean result = false;
    int i = 0;
    while (i < maxIterations) {
      Object searchResult = getMetricsFromPage(metricsUrl, searchKey);
      if (searchResult == null) {
        // check for last iteration
        if (i == (BaseTest.getMaxIterationsPod() - 1)) {
          return false;
        }
        i++;
      } else {
        logger.info("Found value for metric " + searchKey + " : " + searchResult);
        result = true;
        break;
      }
    }
    return result;
  }

  private boolean checkMetrics(String... searchKeys) throws Exception {
    boolean result = false;
    int i = 0;
    while (i < maxIterations) {
      Object searchResult = getMetricsFromPage(metricsUrl, searchKeys);
      if (searchResult == null) {
        // check for last iteration
        if (i == (BaseTest.getMaxIterationsPod() - 1)) {
          return false;
        }
        i++;
      } else {
        logger.info("Found value for metric " + Arrays.toString(searchKeys) + " : " + searchResult);
        result = true;
        break;
      }
    }
    return result;
  }
}

class MyTestAuthenticator extends Authenticator {
  public PasswordAuthentication getPasswordAuthentication() {
    String username = BaseTest.getUsername();
    String password = BaseTest.getPassword();
    return (new PasswordAuthentication(username, password.toCharArray()));
  }
}
