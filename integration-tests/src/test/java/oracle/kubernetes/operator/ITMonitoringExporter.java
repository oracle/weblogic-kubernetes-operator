// Copyright 2019, Oracle Corporation and/or its affiliates.  All rights reserved.
// Licensed under the Universal Permissive License v 1.0 as shown at
// http://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import com.gargoylesoftware.htmlunit.FailingHttpStatusCodeException;
import com.gargoylesoftware.htmlunit.WebClient;
import com.gargoylesoftware.htmlunit.html.HtmlPage;
import com.gargoylesoftware.htmlunit.html.HtmlForm;
import com.gargoylesoftware.htmlunit.html.HtmlSubmitInput;
import com.gargoylesoftware.htmlunit.html.HtmlFileInput;
import com.gargoylesoftware.htmlunit.html.HtmlRadioButtonInput;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.Authenticator;
import java.net.PasswordAuthentication;
import java.net.URL;
import java.util.List;
import java.util.Map;
import javax.xml.bind.DatatypeConverter;
import oracle.kubernetes.operator.utils.Domain;
import oracle.kubernetes.operator.utils.Operator;
import oracle.kubernetes.operator.utils.TestUtils;
import org.junit.Assert;
import org.junit.Assume;
import org.junit.FixMethodOrder;
import org.junit.Test;
import org.junit.AfterClass;
import org.junit.BeforeClass;
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
  String oprelease = "op" + number;
  private int waitTime = 5;
  private int maxIterations = 30;

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

      DeployRunMonitoringExporter();
      upgradeTraefikHostName();
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
   * clone, build , deploy monitoring exporter
   *
   * @throws Exception
   */
  private static void DeployRunMonitoringExporter() throws Exception {

    TestUtils.gitCloneBuildMonitoringExporter();
    logger.info("Creating Operator & waiting for the script to complete execution");
    boolean testCompletedSuccessfully = false;
    startExporterPrometheusGrafana(domain, operator);
    testCompletedSuccessfully = true;
    logger.info("SUCCESS - DeployRunMonitoringExporter");
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
        logger.info("found metric value for " + line + " :" + result.toString());
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
  public void test01_ReplaceConfiguration() throws Exception {
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

    logger.info("Checking configuration for " + searchKey1);
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
}

class MyTestAuthenticator extends Authenticator {
  public PasswordAuthentication getPasswordAuthentication() {
    String username = BaseTest.getUsername();
    String password = BaseTest.getPassword();
    return (new PasswordAuthentication(username, password.toCharArray()));
  }
}
