// Copyright (c) 2019, Oracle Corporation and/or its affiliates.  All rights reserved.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator;

import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import javax.xml.bind.DatatypeConverter;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;
import com.gargoylesoftware.htmlunit.FailingHttpStatusCodeException;
import com.gargoylesoftware.htmlunit.WebClient;
import com.gargoylesoftware.htmlunit.html.HtmlFileInput;
import com.gargoylesoftware.htmlunit.html.HtmlForm;
import com.gargoylesoftware.htmlunit.html.HtmlPage;
import com.gargoylesoftware.htmlunit.html.HtmlRadioButtonInput;
import com.gargoylesoftware.htmlunit.html.HtmlSubmitInput;
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

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/** This test is used for testing Monitoring Exporter with Operator(s) . */
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
public class ItMonitoringExporter extends BaseTest {

  private static int number = 5;
  private static Operator operator = null;

  private static Domain domain = null;
  private static String myhost = "";
  private static String metricsUrl = "";
  private static String monitoringExporterDir = "";
  private static String monitoringExporterEndToEndDir = "";
  private static String resourceExporterDir = "";
  private static String monitoringExporterScriptDir = "";
  private static String exporterUrl = "";
  private static String configPath = "";
  private static String prometheusPort = "30000";
  private static String wlsUser = "";
  private static String wlsPassword = "";
  // "heap_free_current{name="managed-server1"}[15s]" search for results for last 15secs
  private static String prometheusSearchKey1 =
      "heap_free_current%7Bname%3D%22managed-server1%22%7D%5B15s%5D";
  private static String prometheusSearchKey2 =
      "heap_free_current%7Bname%3D%22managed-server2%22%7D%5B15s%5D";
  private static String testappPrometheusSearchKey =
      "weblogic_servlet_invocation_total_count%7Bapp%3D%22httpsessionreptestapp%22%7D%5B15s%5D";
  String oprelease = "op" + number;
  private int waitTime = 5;
  //update with specific branch name if not master
  private static String monitoringExporterBranchVer = "master";

  /**
   * This method gets called only once before any of the test methods are executed. It does the
   * initialization of the integration test properties defined in OperatorIT.properties and setting
   * the resultRoot, pvRoot and projectRoot attributes.
   *
   * @throws Exception exception
   */
  @BeforeClass
  public static void staticPrepare() throws Exception {
    if (FULLTEST) {
      initialize(APP_PROPS_FILE);
      logger.info("Checking if operator and domain are running, if not creating");
      if (operator == null) {
        operator = TestUtils.createOperator("operatorexp.yaml");
      }
      if (domain == null) {
        domain = createVerifyDomain(number, operator);
        Assert.assertNotNull(domain);
      }
      wlsUser = BaseTest.getUsername();
      wlsPassword = BaseTest.getPassword();
      myhost = domain.getHostNameForCurl();
      exporterUrl = "http://" + myhost + ":" + domain.getLoadBalancerWebPort() + "/wls-exporter/";
      metricsUrl = exporterUrl + "metrics";
      monitoringExporterDir = BaseTest.getResultDir() + "/monitoring";
      monitoringExporterScriptDir = BaseTest.getResultDir() + "/scripts";
      resourceExporterDir =
          BaseTest.getProjectRoot() + "/integration-tests/src/test/resources/exporter";
      configPath = resourceExporterDir;
      monitoringExporterEndToEndDir = monitoringExporterDir + "/src/samples/kubernetes/end2end/";
      BaseTest.setWaitTimePod(10);
      upgradeTraefikHostName();
      deployRunMonitoringExporter(domain, operator);

      String testAppName = "httpsessionreptestapp";
      String scriptName = "buildDeployAppInPod.sh";
      domain.buildDeployJavaAppInPod(
              testAppName, scriptName, BaseTest.getUsername(), BaseTest.getPassword());
    }
  }

  /**
   * Releases k8s cluster lease, archives result, pv directories.
   *
   * @throws Exception exception
   */
  @AfterClass
  public static void staticUnPrepare() throws Exception {
    if (FULLTEST) {
      logger.info("+++++++++++++++++++++++++++++++++---------------------------------+");
      logger.info("BEGIN");
      logger.info("Run once, release cluster lease");
      if (domain != null) {
        domain.destroy();
        TestUtils.deleteWeblogicDomainResources("test5");
      }

      String crdCmd =
              " kubectl delete -f " + monitoringExporterEndToEndDir + "/demo-domains/domain1.yaml";
      ExecCommand.exec(crdCmd);
      crdCmd = "kubectl delete secret domain1-weblogic-credentials";
      ExecCommand.exec(crdCmd);
      if (operator != null) {
        operator.destroy();
      }
      uninstallWebHookPrometheusGrafanaMySql();
      tearDown(new Object() {}.getClass().getEnclosingClass().getSimpleName());
      logger.info("SUCCESS");
    }
  }

  /**
   * Remove monitoring exporter directory if exists and clone latest from github for monitoring
   * exporter code.
   *
   * @throws Exception if could not run the command successfully to clone from github
   */
  private static void gitCloneBuildMonitoringExporter() throws Exception {

    logger.info("installing monitoring exporter version: " + MONITORING_EXPORTER_VERSION);
    executeShelScript(
        resourceExporterDir,
        monitoringExporterScriptDir,
        "buildMonitoringExporter.sh",
        monitoringExporterDir + " " + resourceExporterDir + " " + monitoringExporterBranchVer + " "
            + MONITORING_EXPORTER_VERSION);
  }

  /**
   * Utility to execute any shell scripts.
   *
   * @param srcLoc - path to the shell script
   * @param destLoc - destination path there the shell script will be executed
   * @param fileName - name of the shell script
   * @param args - args to pass to the shell script
   * @throws Exception if could not run the command successfully to clone from github
   */
  private static void executeShelScript(String srcLoc, String destLoc, String fileName, String args)
      throws Exception {
    if (!new File(destLoc).exists()) {
      logger.info(" creating script dir ");
      Files.createDirectories(Paths.get(destLoc));
    }
    String crdCmd = " cp " + srcLoc + "/" + fileName + " " + destLoc;
    TestUtils.exec(crdCmd);
    crdCmd =
        "cd "
            + destLoc
            + " && chmod 777 ./"
            + fileName
            + " && . ./"
            + fileName
            + " "
            + args
            + " | tee script.log";
    TestUtils.exec(crdCmd);
    crdCmd = " cat " + destLoc + "/script.log";
    ExecResult result = ExecCommand.exec(crdCmd);
    assertFalse(
        "Shel script failed: " + result.stdout(), result.stdout().contains("BUILD FAILURE"));
    logger.info("Result output from  the command " + crdCmd + " : " + result.stdout());
  }

  /**
   * Deploy Monitoring Exporter webapp, Prometheus and Grafana.
   *
   * @param exporterAppPath path to exporter webapp
   * @param domain - domain where monitoring exporter will be deployed
   * @param operator operator object managing the domain
   * @throws Exception if could not run the command successfully to clone from github
   */
  private static void deployMonitoringExporter(
      String exporterAppPath, Domain domain, Operator operator) throws Exception {

    Map<String, Object> domainMap = domain.getDomainMap();
    // create the app directory in admin pod
    TestUtils.kubectlexec(
        domain.getDomainUid() + ("-") + domainMap.get("adminServerName"),
        "" + domainMap.get("namespace"),
        " -- mkdir -p " + appLocationInPod);
    domain.deployWebAppViaWlst(
        "wls-exporter", exporterAppPath, appLocationInPod, getUsername(), getPassword(), true);
  }

  /**
   * Deploy Monitoring Exporter webapp, Prometheus and Grafana.
   *
   * @param webappName webapp name used to collect metrics for scaling
   * @param domain - domain where monitoring exporter will be deployed
   * @throws Exception if could not run the command successfully
   */
  private static void verifyScalingViaPrometheus(Domain domain, String webappName)
      throws Exception {

    createWebHookForScale();
    // scale cluster to only one replica
    scaleCluster(1);
    // invoke the app to increase number of the opened sessions
    String testAppName = "httpsessionreptestapp";


    domain.callWebAppAndVerifyLoadBalancing(testAppName + "/CounterServlet?", false);

    String webhookPod = getPodName("name=webhook", "monitoring");
    String command = "kubectl -n monitoring logs " + webhookPod;
    TestUtils.checkAnyCmdInLoop(command, "scaleup hook triggered successfully");

    TestUtils.checkPodCreated(domain.getDomainUid() + "-managed-server2", domain.getDomainNs());
  }

  /**
   * clone, build , deploy monitoring exporter on specified domain, operator.
   *
   * @throws Exception exception
   */
  private static void deployRunMonitoringExporter(Domain domain, Operator operator)
      throws Exception {
    gitCloneBuildMonitoringExporter();
    logger.info("Creating Operator & waiting for the script to complete execution");
    boolean testCompletedSuccessfully = false;
    startExporter(domain, operator);
    // check if exporter is up
    domain.callWebAppAndVerifyLoadBalancing("wls-exporter", false);
    testCompletedSuccessfully = true;
    logger.info("SUCCESS - deployRunMonitoringExporter");
  }

  /**
   * create operator, domain, run some verification tests to check domain runtime.
   *
   * @throws Exception exception
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

  private static void startExporter(Domain domain, Operator operator)
      throws Exception {
    logger.info("deploy exporter ");
    deployMonitoringExporter(
        monitoringExporterDir + "/apps/monitoringexporter/wls-exporter.war", domain, operator);
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
    TestUtils.exec(cmd.toString());
  }

  /**
   * Check that configuration can be reviewed via Prometheus.
   *
   * @throws Exception if test fails
   */
  @Test
  public void test01_CheckMetricsViaPrometheus() throws Exception {
    Assume.assumeTrue(FULLTEST);
    String testMethodName = new Object() {}.getClass().getEnclosingMethod().getName();
    logTestBegin(testMethodName);
    boolean testCompletedSuccessfully = false;
    assertTrue(checkMetricsViaPrometheus(testappPrometheusSearchKey, "httpsessionreptestapp"));
    testCompletedSuccessfully = true;
    logger.info("SUCCESS - " + testMethodName);
  }

  /**
   * Replace monitoring exporter configuration and verify it was applied to both managed servers.
   *
   * @throws Exception if test fails
   */
  @Test
  public void test02_ReplaceConfiguration() throws Exception {
    Assume.assumeTrue(FULLTEST);
    String testMethodName = new Object() {}.getClass().getEnclosingMethod().getName();
    logTestBegin(testMethodName);
    HtmlPage page = submitConfigureForm(exporterUrl, "replace", configPath + "/rest_jvm.yml");
    assertTrue(checkMetricsViaPrometheus(prometheusSearchKey1, "managed-server1"));
    assertTrue(checkMetricsViaPrometheus(prometheusSearchKey2, "managed-server2"));
    logger.info("SUCCESS - " + testMethodName);
  }

  /**
   * Add additional monitoring exporter configuration and verify it was applied.
   *
   * @throws Exception if test fails
   */
  @Test
  public void test03_AppendConfiguration() throws Exception {
    Assume.assumeTrue(FULLTEST);
    String testMethodName = new Object() {}.getClass().getEnclosingMethod().getName();
    logTestBegin(testMethodName);
    // scale cluster to 1 managed server only to test functionality of the exporter without
    // coordinator layer
    scaleCluster(1);

    // make sure some config is there
    HtmlPage page = submitConfigureForm(exporterUrl, "replace", configPath + "/rest_jvm.yml");

    assertTrue(page.asText().contains("JVMRuntime"));
    assertFalse(page.asText().contains("WebAppComponentRuntime"));
    // run append
    page = submitConfigureForm(exporterUrl, "append", configPath + "/rest_webapp.yml");
    assertTrue(page.asText().contains("WebAppComponentRuntime"));
    // check previous config is there
    assertTrue(page.asText().contains("JVMRuntime"));

    assertTrue(checkMetricsViaPrometheus(testappPrometheusSearchKey, "httpsessionreptestapp"));
    logger.info("SUCCESS - " + testMethodName);
  }

  /**
   * Replace monitoring exporter configuration with only one attribute and verify it was applied.
   *
   * @throws Exception if test fails
   */
  @Test
  public void test04_ReplaceOneAttributeValueAsArrayConfiguration() throws Exception {
    Assume.assumeTrue(FULLTEST);
    String testMethodName = new Object() {}.getClass().getEnclosingMethod().getName();
    logTestBegin(testMethodName);
    HtmlPage page =
        submitConfigureForm(exporterUrl, "replace", configPath + "/rest_oneattribval.yml");
    assertTrue(page.asText().contains("values: invocationTotalCount"));
    assertFalse(page.asText().contains("reloadTotal"));
    logger.info("SUCCESS - " + testMethodName);
  }

  /**
   * Append monitoring exporter configuration with one more attribute and verify it was applied
   * append to [a] new config [a,b].
   *
   * @throws Exception if test fails
   */
  @Test
  public void test05_AppendArrayWithOneExistedAndOneDifferentAttributeValueAsArrayConfiguration()
      throws Exception {
    Assume.assumeTrue(FULLTEST);
    String testMethodName = new Object() {}.getClass().getEnclosingMethod().getName();
    logTestBegin(testMethodName);
    HtmlPage page =
        submitConfigureForm(exporterUrl, "replace", configPath + "/rest_oneattribval.yml");
    assertTrue(page.asText().contains("values: invocationTotalCount"));
    page = submitConfigureForm(exporterUrl, "append", configPath + "/rest_twoattribs.yml");
    assertTrue(page.asText().contains("values: [invocationTotalCount, executionTimeAverage]"));
    logger.info("SUCCESS - " + testMethodName);
  }

  /**
   * Replace monitoring exporter configuration with empty configuration.
   *
   * @throws Exception if test fails
   */
  @Test
  public void test06_ReplaceWithEmptyConfiguration() throws Exception {
    Assume.assumeTrue(FULLTEST);
    String testMethodName = new Object() {}.getClass().getEnclosingMethod().getName();
    logTestBegin(testMethodName);
    HtmlPage page = submitConfigureForm(exporterUrl, "replace", configPath + "/rest_empty.yml");
    assertTrue(page.asText().contains("queries:") && !page.asText().contains("values"));
    logger.info("SUCCESS - " + testMethodName);
  }

  /**
   * Try to append monitoring exporter configuration with empty configuration.
   *
   * @throws Exception exception
   */
  @Test
  public void test07_AppendWithEmptyConfiguration() throws Exception {
    Assume.assumeTrue(FULLTEST);
    String testMethodName = new Object() {}.getClass().getEnclosingMethod().getName();
    logTestBegin(testMethodName);
    final WebClient webClient = new WebClient();
    HtmlPage originalPage = webClient.getPage(exporterUrl);
    assertNotNull(originalPage);
    HtmlPage page = submitConfigureForm(exporterUrl, "append", configPath + "/rest_empty.yml");
    assertTrue(originalPage.asText().equals(page.asText()));
    logger.info("SUCCESS - " + testMethodName);
  }

  /**
   * Try to append monitoring exporter configuration with configuration file not in the yaml format.
   *
   * @throws Exception if test fails
   */
  @Test
  public void test08_1AppendWithNotYmlConfiguration() throws Exception {
    Assume.assumeTrue(FULLTEST);
    String testMethodName = new Object() {}.getClass().getEnclosingMethod().getName();
    logTestBegin(testMethodName);
    changeConfigNegative(
        "append", configPath + "/rest_notymlformat.yml", "Configuration is not in YAML format");
    logger.info("SUCCESS - " + testMethodName);
  }

  /**
   * Try to replace monitoring exporter configuration with configuration file not in the yaml
   * format.
   *
   * @throws Exception exception
   */
  @Test
  public void test08_2ReplaceWithNotYmlConfiguration() throws Exception {
    Assume.assumeTrue(FULLTEST);
    String testMethodName = new Object() {}.getClass().getEnclosingMethod().getName();
    logTestBegin(testMethodName);
    changeConfigNegative(
        "replace", configPath + "/rest_notymlformat.yml", "Configuration is not in YAML format");
  }

  /**
   * Try to append monitoring exporter configuration with configuration file in the corrupted yaml
   * format.
   *
   * @throws Exception if test fails
   */
  public void test09_AppendWithCorruptedYmlConfiguration() throws Exception {
    Assume.assumeTrue(FULLTEST);
    String testMethodName = new Object() {}.getClass().getEnclosingMethod().getName();
    logTestBegin(testMethodName);
    changeConfigNegative(
        "append",
        configPath + "/rest_notyml.yml",
        "Configuration YAML format has errors while scanning a simple key");
    logger.info("SUCCESS - " + testMethodName);
  }

  /**
   * Try to replace monitoring exporter configuration with configuration file in the corrupted yaml
   * format.
   *
   * @throws Exception exception
   */
  @Test
  public void test10_ReplaceWithCorruptedYmlConfiguration() throws Exception {
    Assume.assumeTrue(FULLTEST);
    String testMethodName = new Object() {}.getClass().getEnclosingMethod().getName();
    logTestBegin(testMethodName);
    changeConfigNegative(
        "replace",
        configPath + "/rest_notyml.yml",
        "Configuration YAML format has errors while scanning a simple key");
    logger.info("SUCCESS - " + testMethodName);
  }

  /**
   * Try to replace monitoring exporter configuration with configuration file with dublicated
   * values.
   *
   * @throws Exception if test fails
   */
  @Test
  public void test11_ReplaceWithDublicatedValuesConfiguration() throws Exception {
    Assume.assumeTrue(FULLTEST);
    String testMethodName = new Object() {}.getClass().getEnclosingMethod().getName();
    logTestBegin(testMethodName);
    changeConfigNegative(
        "replace",
        configPath + "/rest_dublicatedval.yml",
        "Duplicate values for [deploymentState] at applicationRuntimes.componentRuntimes");
    logger.info("SUCCESS - " + testMethodName);
  }

  /**
   * Try to append monitoring exporter configuration with configuration file with duplicated values.
   *
   * @throws Exception if test fails
   */
  @Test
  public void test12_AppendWithDuplicatedValuesConfiguration() throws Exception {
    Assume.assumeTrue(FULLTEST);
    String testMethodName = new Object() {}.getClass().getEnclosingMethod().getName();
    logTestBegin(testMethodName);
    changeConfigNegative(
        "append",
        configPath + "/rest_dublicatedval.yml",
        "Duplicate values for [deploymentState] at applicationRuntimes.componentRuntimes");
    logger.info("SUCCESS - " + testMethodName);
  }

  /**
   * Try to replace monitoring exporter configuration with configuration file with
   * NameSnakeCase=false.
   *
   * @throws Exception exception
   */
  @Test
  public void test13_ReplaceMetricsNameSnakeCaseFalseConfiguration() throws Exception {
    Assume.assumeTrue(FULLTEST);
    String testMethodName = new Object() {}.getClass().getEnclosingMethod().getName();
    logTestBegin(testMethodName);
    final WebClient webClient = new WebClient();
    HtmlPage originalPage = webClient.getPage(exporterUrl);
    assertNotNull(originalPage);
    HtmlPage page =
        submitConfigureForm(exporterUrl, "replace", configPath + "/rest_snakecasefalse.yml");
    assertNotNull(page);
    assertFalse(page.asText().contains("metricsNameSnakeCase"));
    String searchKey = "weblogic_servlet_executionTimeAverage%7Bapp%3D%22httpsessionreptestapp%22%7D%5B15s%5D";
    assertTrue(checkMetricsViaPrometheus(searchKey, "httpsessionreptestapp"));
    logger.info("SUCCESS - " + testMethodName);
  }

  /**
   * Try to change monitoring exporter configuration without authentication.
   *
   * @throws Exception exception
   */
  // verify that change configuration fails without authentication
  @Test
  public void test14_ChangeConfigNoCredentials() throws Exception {
    Assume.assumeTrue(FULLTEST);
    String testMethodName = new Object() {}.getClass().getEnclosingMethod().getName();
    logTestBegin(testMethodName);
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
    logger.info("SUCCESS - " + testMethodName);
  }

  /**
   * Try to change monitoring exporter configuration with invalid username.
   *
   * @throws Exception exception
   */
  @Test
  public void test15_ChangeConfigInvalidUser() throws Exception {
    Assume.assumeTrue(FULLTEST);
    String testMethodName = new Object() {}.getClass().getEnclosingMethod().getName();
    logTestBegin(testMethodName);
    changeConfigNegativeAuth(
        "replace",
        configPath + "/rest_snakecasetrue.yml",
        "401 Unauthorized for " + exporterUrl,
        "invaliduser",
        wlsPassword);
    logger.info("SUCCESS - " + testMethodName);
  }

  /**
   * Try to change monitoring exporter configuration with invalid password.
   *
   * @throws Exception exception
   */
  @Test
  public void test16_ChangeConfigInvalidPass() throws Exception {
    Assume.assumeTrue(FULLTEST);
    String testMethodName = new Object() {}.getClass().getEnclosingMethod().getName();
    logTestBegin(testMethodName);
    changeConfigNegativeAuth(
        "replace",
        configPath + "/rest_snakecasetrue.yml",
        "401 Unauthorized for " + exporterUrl,
        wlsUser,
        "invalidpass");
  }

  /**
   * Try to change monitoring exporter configuration with empty username.
   *
   * @throws Exception if test fails
   */
  @Test
  public void test17_ChangeConfigEmptyUser() throws Exception {
    Assume.assumeTrue(FULLTEST);
    String testMethodName = new Object() {}.getClass().getEnclosingMethod().getName();
    logTestBegin(testMethodName);
    changeConfigNegativeAuth(
        "replace",
        configPath + "/rest_snakecasetrue.yml",
        "401 Unauthorized for " + exporterUrl,
        "",
        wlsPassword);
    logger.info("SUCCESS - " + testMethodName);
  }

  /**
   * Try to change monitoring exporter configuration with empty pass.
   *
   * @throws Exception if test fails
   */
  @Test
  public void test18_ChangeConfigEmptyPass() throws Exception {
    Assume.assumeTrue(FULLTEST);
    String testMethodName = new Object() {}.getClass().getEnclosingMethod().getName();
    logTestBegin(testMethodName);
    changeConfigNegativeAuth(
        "replace",
        configPath + "/rest_snakecasetrue.yml",
        "401 Unauthorized for " + exporterUrl,
        wlsUser,
        "");
    logger.info("SUCCESS - " + testMethodName);
  }

  /**
   * Install prometheus and grafana with latest version of chart.
   *
   * @throws Exception if test fails
   */
  @Test
  public void test19_CheckPromGrafanaLatestVersion() throws Exception {
    Assume.assumeTrue(FULLTEST);
    String testMethodName = new Object() {}.getClass().getEnclosingMethod().getName();
    logTestBegin(testMethodName);
    executeShelScript(
            resourceExporterDir,
            monitoringExporterScriptDir,
            "redeployPromGrafanaLatestChart.sh",
            monitoringExporterDir + " " + resourceExporterDir);
    checkPromGrafana();

    logger.info("SUCCESS - " + testMethodName);
  }

  /**
   * Test End to End example from MonitoringExporter github project.
   *
   * @throws Exception if test fails
   */
  @Test
  public void test01_1_EndToEndViaChart() throws Exception {
    Assume.assumeTrue(FULLTEST);
    String testMethodName = new Object() {}.getClass().getEnclosingMethod().getName();
    logTestBegin(testMethodName);

    setupPv();
    installPrometheusGrafanaWebHookMySqlCoordinatorWlsImage();
    fireAlert();
    addMonitoringToExistedDomain();

    logger.info("SUCCESS - " + testMethodName);
  }

  private void fireAlert() throws Exception {
    logger.info("Fire Alert by changing replca count");
    replaceStringInFile(
        monitoringExporterEndToEndDir + "/demo-domains/domain1.yaml", "replicas: 2", "replicas: 1");
    // apply new domain yaml and verify pod restart
    String crdCmd =
        " kubectl apply -f " + monitoringExporterEndToEndDir + "/demo-domains/domain1.yaml";
    TestUtils.exec(crdCmd);

    TestUtils.checkPodReady("domain1-admin-server", "default");
    TestUtils.checkPodReady("domain1-managed-server-1", "default");

    String webhookPod = getPodName("app=webhook", "webhook");
    String command = "kubectl -n webhook logs " + webhookPod;
    TestUtils.checkAnyCmdInLoop(
        command, "Some WLS cluster has only one running server for more than 1 minutes");
  }

  private static void addMonitoringToExistedDomain() throws Exception {
    logger.info("Add monitoring to the running domain");
    String exporterAppPath = monitoringExporterDir + "/apps/monitoringexporter/wls-exporter.war";
    domain.deployWebAppViaWlst(
            "wls-exporter", exporterAppPath, appLocationInPod, getUsername(), getPassword(), true);
    // check if exporter is up
    domain.callWebAppAndVerifyLoadBalancing("wls-exporter", false);
    // apply new domain yaml and verify pod restart
    String crdCmd =
        " kubectl -n monitoring get cm prometheus-server -oyaml > "
            + monitoringExporterEndToEndDir
            + "/cm.yaml";
    TestUtils.exec(crdCmd);
    ExecResult result = ExecCommand.exec("cat " + monitoringExporterEndToEndDir + "/cm.yaml");
    logger.info(" output for cm " + result.stdout());
    replaceStringInFile(
        monitoringExporterEndToEndDir + "/cm.yaml",
        "default;domain1;cluster-1",
        "test5;test5;cluster-1");
    crdCmd = " kubectl -n monitoring apply -f " + monitoringExporterEndToEndDir + "/cm.yaml";
    TestUtils.exec(crdCmd);
    BaseTest.setWaitTimePod(10);
    BaseTest.setMaxIterationsPod(50);
    assertTrue(
        "Can't find expected metrics",
        checkMetricsViaPrometheus("webapp_config_open_sessions_current_count", "test5"));
  }

  private static String getPodName(String labelExp, String namespace) throws Exception {
    StringBuffer cmd = new StringBuffer();
    cmd.append(
        "kubectl get pod -l "
            + labelExp
            + " -n "
            + namespace
            + " -o jsonpath=\"{.items[0].metadata.name}\"");
    logger.info(" pod name cmd =" + cmd);
    ExecResult result = null;
    String podName = null;
    int i = 0;
    while (i < 4) {
      result = ExecCommand.exec(cmd.toString());
      logger.info(" Result output" + result.stdout());
      if (result.exitValue() == 0) {
        logger.info(result.stdout());
        podName = result.stdout().trim();
        break;
      } else {
        Thread.sleep(10000);
        i++;
      }
    }
    assertNotNull(labelExp + " was not created, can't find running pod ", podName);
    return podName;
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
    assertFalse((page2.asText()).contains("Error 500--Internal Server Error"));
    // wait time for coordinator to update both managed configuration
    Thread.sleep(15 * 1000);
    return page2;
  }

  /**
   * Remove monitoring exporter directory if exists and clone latest from github for monitoring
   * exporter code.
   *
   * @throws Exception if could not run the command successfully to install database
   */
  private static void setupPv() throws Exception {
    String pvDir = monitoringExporterEndToEndDir + "pvDir";
    if (new File(pvDir).exists()) {
      logger.info(" PV dir already exists , cleaning ");
      if (!pvDir.isEmpty()) {
        deletePvDir();
      }
    } else {
      Files.createDirectories(Paths.get(pvDir));
    }
    replaceStringInFile(
            monitoringExporterEndToEndDir + "/mysql/persistence.yaml", "%PV_ROOT%", pvDir);
    replaceStringInFile(
            monitoringExporterEndToEndDir + "/prometheus/persistence.yaml", "%PV_ROOT%", pvDir);
    replaceStringInFile(
            monitoringExporterEndToEndDir + "/prometheus/alert-persistence.yaml", "%PV_ROOT%", pvDir);
    replaceStringInFile(
            monitoringExporterEndToEndDir + "/grafana/persistence.yaml", "%PV_ROOT%", pvDir);

  }

  /**
   * Install wls image tool and update wls pods.
   *
   * @throws Exception if could not run the command successfully to create WLSImage and deploy
   */
  private static void createWlsImageAndDeploy() throws Exception {
    logger.info(" Starting to create WLS Image");

    String command =
        "cd "
            + monitoringExporterEndToEndDir
            + "/demo-domains/domainBuilder/ && ./build.sh domain1 "
            + wlsUser
            + " "
            + wlsPassword
            + " wluser1 wlpwd123";
    TestUtils.exec(command);

    logger.info(" Starting to create WLS secret");
    command =
        "kubectl -n default create secret generic domain1-weblogic-credentials "
            + "  --from-literal=username="
            + wlsUser
            + "  --from-literal=password="
            + wlsPassword;
    TestUtils.exec(command);
    // apply new domain yaml and verify pod restart
    String crdCmd =
        " kubectl apply -f " + monitoringExporterEndToEndDir + "/demo-domains/domain1.yaml";
    TestUtils.exec(crdCmd);

    TestUtils.checkPodReady("domain1-admin-server", "default");
    TestUtils.checkPodReady("domain1-managed-server-1", "default");
    TestUtils.checkPodReady("domain1-managed-server-2", "default");

    // apply curl to the pod
    crdCmd = " kubectl apply -f " + monitoringExporterEndToEndDir + "/util/curl.yaml";
    TestUtils.exec(crdCmd);

    TestUtils.checkPodReady("curl", "default");
    // access metrics
    crdCmd =
        "kubectl exec curl -- curl http://"
            + wlsUser
            + ":"
            + wlsPassword
            + "@domain1-managed-server-1:8001/wls-exporter/metrics";
    ExecResult result = TestUtils.exec(crdCmd);
    assertTrue((result.stdout().contains("wls_servlet_execution_time_average")));
    crdCmd =
        "kubectl exec curl -- curl http://"
            + wlsUser
            + ":"
            + wlsPassword
            + "@domain1-managed-server-2:8001/wls-exporter/metrics";
    result = TestUtils.exec(crdCmd);
    assertTrue((result.stdout().contains("wls_servlet_execution_time_average")));
  }

  /**
   * Install Prometheus and Grafana using helm chart, MySql, webhook, coordinator, create WLS image and deploy.
   *
   * @throws Exception if could not run the command successfully to install Prometheus and Grafana
   */
  private static void installPrometheusGrafanaWebHookMySqlCoordinatorWlsImage() throws Exception {
    prometheusPort = "30000";

    executeShelScript(
            resourceExporterDir,
            monitoringExporterScriptDir,
            "createPromGrafanaMySqlCoordWebhook.sh",
            monitoringExporterDir + " " + resourceExporterDir + " " + PROMETHEUS_CHART_VERSION + " "
                + GRAFANA_CHART_VERSION);

    String webhookPod = getPodName("app=webhook", "webhook");
    TestUtils.checkPodReady(webhookPod, "webhook");

    //update with current WDT version
    replaceStringInFile(monitoringExporterEndToEndDir + "/demo-domains/domainBuilder/build.sh", "0.24", WDT_VERSION);
    createWlsImageAndDeploy();
    checkPromGrafana();
  }

  static void checkPromGrafana() throws Exception {
    String podName = getPodName("app=prometheus", "monitoring");
    TestUtils.checkPodReady(podName, "monitoring", "2/2");

    String crdCmd = "kubectl -n monitoring get pods -l app=prometheus";
    ExecResult resultStatus = ExecCommand.exec(crdCmd);
    logger.info("Status of the pods " + resultStatus.stdout());


    assertFalse(
        "Can't create prometheus pods",
        resultStatus.stdout().contains("CrashLoopBackOff")
            || resultStatus.stdout().contains("Error"));

    podName = getPodName("app=grafana", "monitoring");
    TestUtils.checkPodReady(podName, "monitoring");


    logger.info("installing grafana dashboard");

    crdCmd =
        " cd "
            + monitoringExporterEndToEndDir
            + " && curl -v -H 'Content-Type: application/json' -H \"Content-Type: application/json\""
            + "  -X POST http://admin:12345678@$HOSTNAME:31000/api/datasources/"
            + "  --data-binary @grafana/datasource.json";
    TestUtils.exec(crdCmd);

    crdCmd =
        " cd "
            + monitoringExporterEndToEndDir
            + " && curl -v -H 'Content-Type: application/json' -H \"Content-Type: application/json\""
            + "  -X POST http://admin:12345678@$HOSTNAME:31000/api/dashboards/db/"
            + "  --data-binary @grafana/dashboard.json";
    TestUtils.exec(crdCmd);
    crdCmd = " cd "
        + monitoringExporterEndToEndDir
        + " && "
        + "curl -v  -H 'Content-Type: application/json' -X GET http://admin:12345678@$HOSTNAME:31000/api/dashboards/db/weblogic-server-dashboard";
    ExecResult result = ExecCommand.exec(crdCmd);
    assertTrue(result.stdout().contains("wls_jvm_uptime"));
    assertTrue(
        "Can't find expected metrics",
        checkMetricsViaPrometheus("wls_servlet_execution_time_average", "test-webapp"));
  }


  /**
   * Install WebHook for performing scaling via prometheus.
   *
   * @throws Exception if could not run the command successfully to install webhook and alert
   *     manager
   */
  private static void createWebHookForScale() throws Exception {

    String webhookResourceDir = resourceExporterDir + "/../webhook";
    String webhookDir = monitoringExporterDir + "/webhook";
    // install webhook
    logger.info("installing webhook ");
    executeShelScript(
        webhookResourceDir,
        monitoringExporterScriptDir,
        "setupWebHook.sh",
        webhookDir + " " + webhookResourceDir + " " + operator.getOperatorNamespace());
    String webhookPod = getPodName("name=webhook", "monitoring");
    TestUtils.checkPodReady(webhookPod, "monitoring");
  }

  /**
   * Uninstall Prometheus and Grafana using helm chart, uninstall Webhook, MySql.
   *
   * @throws Exception if could not run the command successfully to uninstall deployments
   */
  private static void uninstallWebHookPrometheusGrafanaMySql() throws Exception {

    executeShelScript(
            resourceExporterDir,
            monitoringExporterScriptDir,
            "deletePromGrafanaMySqlCoordWebhook.sh",
            monitoringExporterDir + " " + resourceExporterDir);

    deletePvDir();
  }

  /**
   * Delete PvDir via docker.
   *
   * @throws Exception if could not run the command successfully to delete PV
   */
  private static void deletePvDir() throws Exception {
    String pvDir = monitoringExporterEndToEndDir + "/pvDir";
    String crdCmd =
        "cd "
            + monitoringExporterEndToEndDir
            + " && docker run --rm -v "
            + monitoringExporterEndToEndDir
            + "pvDir:/tt -v $PWD/util:/util  nginx  /util/clean-pv.sh";
    try {
      if (new File(pvDir).exists()) {
        ExecCommand.exec(crdCmd);
        StringBuffer removeDir = new StringBuffer();
        logger.info("Cleaning PV dir " + pvDir);
        removeDir.append("rm -rf ").append(pvDir);
        ExecCommand.exec(removeDir.toString());
      }
    } finally {
      if (JENKINS) {
        if (new File(pvDir).exists()) {

          logger.info("Deleting pv created dir " + pvDir);
          TestUtils.exec("/usr/local/packages/aime/ias/run_as_root \"rm -rf " + pvDir);
        }
      }
    }
  }

  /**
   * A utility method to sed files.
   *
   * @throws IOException when copying files from source location to staging area fails
   */
  private static void replaceStringInFile(String filePath, String oldValue, String newValue)
      throws IOException {
    Path src = Paths.get(filePath);
    logger.log(Level.INFO, "Copying {0}", src.toString());
    Charset charset = StandardCharsets.UTF_8;
    String content = new String(Files.readAllBytes(src), charset);
    content = content.replaceAll(oldValue, newValue);
    logger.log(Level.INFO, "to {0}", src.toString());
    Files.write(src, content.getBytes(charset));
  }

  /**
   * call operator to scale to specified number of replicas.
   *
   * @param replicas - number of managed servers
   * @throws Exception if scaling fails
   */
  private static void scaleCluster(int replicas) throws Exception {
    logger.info("Scale up/down to " + replicas + " managed servers");
    operator.scale(domain.getDomainUid(), domain.getClusterName(), replicas);
  }

  /**
   * Check metrics using Prometheus.
   * @param searchKey - metric query expression
   * @param expectedVal - expected metrics to search
   * @throws Exception if command to check metrics fails
   */
  private static boolean checkMetricsViaPrometheus(String searchKey, String expectedVal)
      throws Exception {

    // url
    StringBuffer testAppUrl = new StringBuffer("http://");
    // testAppUrl.append(myhost).append(":").append(prometheusPort).append("/api/v1/query?query=");
    testAppUrl
        .append(myhost)
        .append(":")
        .append(prometheusPort)
        .append("/api/v1/query?query=")
        .append(searchKey);
    // curl cmd to call webapp
    StringBuffer curlCmd = new StringBuffer("curl --noproxy '*' ");
    curlCmd.append(testAppUrl.toString());
    logger.info("Curl cmd " + curlCmd);
    logger.info("searchKey:" + searchKey);
    logger.info("expected Value " + expectedVal);
    boolean result = false;
    try {
      TestUtils.checkAnyCmdInLoop(curlCmd.toString(), expectedVal);
      logger.info("Prometheus application invoked successfully with curlCmd:" + curlCmd);
      result = true;
    } catch (Exception ex) {
      new RuntimeException("FAILURE: can't check metrics" + ex.getMessage());
    }
    return result;
  }

  /**
   * Method to read the yaml file and add extra properties to the root.
   *
   * @param yamlFile - Name of the yaml file to make changes.
   * @throws IOException exception
   */
  private static void addRestOptToYaml(String yamlFile, String prop, int restPort) throws IOException {
    ObjectMapper objectMapper = new ObjectMapper();

    ObjectMapper yamlReader = new ObjectMapper(new YAMLFactory());
    Object obj = yamlReader.readValue(new File(yamlFile), Object.class);

    ObjectMapper jsonWriter = new ObjectMapper();
    String writeValueAsString = jsonWriter.writeValueAsString(obj);
    JsonNode root = objectMapper.readTree(writeValueAsString);
    ((ObjectNode) root).put(prop, restPort);
    String jsonAsYaml = new YAMLMapper().writerWithDefaultPrettyPrinter().writeValueAsString(root);

    // Write the modified yaml to the file
    Path path = Paths.get(yamlFile);
    Charset charset = StandardCharsets.UTF_8;
    Files.write(path, jsonAsYaml.getBytes(charset));

  }
}
