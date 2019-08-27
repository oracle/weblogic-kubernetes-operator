// Copyright 2019, Oracle Corporation and/or its affiliates.  All rights reserved.
// Licensed under the Universal Permissive License v 1.0 as shown at
// http://oss.oracle.com/licenses/upl.

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
  private static Operator operator1 = null;
  private static Domain domain = null;
  private static String myhost = "";
  private static String metricsUrl = "";
  private static String monitoringExporterDir = "";
  private static String monitoringExporterEndToEndDir = "";
  private static String resourceExporterDir = "";
  private static String monitoringExporterScriptDir = "";
  private static String exporterUrl = "";
  private static String configPath = "";
  private static String prometheusPort = "32000";
  private static String wlsUser = "";
  private static String wlsPassword = "";
  // "heap_free_current{name="managed-server1"}[15s]" search for results for last 15secs
  private static String prometheusSearchKey1 =
      "heap_free_current%7Bname%3D%22managed-server1%22%7D%5B15s%5D";
  private static String prometheusSearchKey2 =
      "heap_free_current%7Bname%3D%22managed-server2%22%7D%5B15s%5D";
  private static String testwsappPrometheusSearchKey =
      "weblogic_servlet_invocation_total_count%7Bapp%3D%22testwsapp%22%7D%5B15s%5D";
  String oprelease = "op" + number;
  private int waitTime = 5;

  /**
   * This method gets called only once before any of the test methods are executed. It does the
   * initialization of the integration test properties defined in OperatorIT.properties and setting
   * the resultRoot, pvRoot and projectRoot attributes.
   *
   * @throws Exception exception
   */
  @BeforeClass
  public static void staticPrepare() throws Exception {
    if (!QUICKTEST) {
      initialize(APP_PROPS_FILE);
      logger.info("Checking if operator and domain are running, if not creating");
      if (operator == null) {
        Map<String, Object> operatorMap = TestUtils.createOperatorMap(number, true);
        operator = new Operator(operatorMap, Operator.RestCertType.SELF_SIGNED);
        Assert.assertNotNull(operator);
        operator.callHelmInstall();
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
      buildDeployWebServiceApp(domain, TESTWSAPP, TESTWSSERVICE);
    }
  }

  /**
   * Releases k8s cluster lease, archives result, pv directories.
   *
   * @throws Exception exception
   */
  @AfterClass
  public static void staticUnPrepare() throws Exception {
    if (!QUICKTEST) {
      logger.info("+++++++++++++++++++++++++++++++++---------------------------------+");
      logger.info("BEGIN");
      logger.info("Run once, release cluster lease");
      if (domain != null) {
        domain.destroy();
        TestUtils.deleteWeblogicDomainResources("test5");
      }
      if (operator != null) {
        operator.destroy();
      }
      if (operator1 != null) {
        operator1.destroy();
      }
      deletePvDir();
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

    logger.info("installing monitoring exporter ");
    executeShelScript(
        resourceExporterDir,
        monitoringExporterScriptDir,
        "buildMonitoringExporter.sh",
        monitoringExporterDir + " " + resourceExporterDir);
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
  private static void deployMonitoringExporterPrometethusGrafana(
      String exporterAppPath, Domain domain, Operator operator) throws Exception {

    String samplesDir = monitoringExporterDir + "/src/samples/kubernetes/deployments/";
    replaceStringInFile(
        samplesDir + "prometheus-deployment.yaml",
        "webapp=\"OpenSessionApp\"",
        "app=\"httpsessionreptestapp\"");

    String domainNS = domain.getDomainNs();
    String operatorNS = operator.getOperatorNamespace();
    createCrossNsRbacFile(domainNS, operatorNS);
    // create coordinator
    createCoordinatorFile(domainNS);
    executeShelScript(
        resourceExporterDir,
        monitoringExporterScriptDir,
        "deployPromGrafana.sh",
        monitoringExporterDir + " " + domainNS + " " + operatorNS);
    // deploy webhook
    createWebHookForScale();
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

    // scale cluster to only one replica
    scaleCluster(1);
    // invoke the app to increase number of the opened sessions
    String testAppName = "httpsessionreptestapp";
    String scriptName = "buildDeployAppInPod.sh";
    domain.buildDeployJavaAppInPod(
        testAppName, scriptName, BaseTest.getUsername(), BaseTest.getPassword());
    domain.callWebAppAndVerifyLoadBalancing(testAppName + "/CounterServlet?", false);

    String webhookPod = getPodName("name=webhook", "monitoring");
    String command = "kubectl -n monitoring logs " + webhookPod;
    TestUtils.checkAnyCmdInLoop(command, "scaleup hook triggered successfully");

    TestUtils.checkPodCreated(domain.getDomainUid() + "-managed-server2", domain.getDomainNs());
  }

  /**
   * Redeploy Monitoring Exporter webapp with original configuration.
   *
   * @param domain - domain where monitoring exporter will be deployed
   * @throws Exception if could not run the command successfully
   */
  private static void redeployMonitoringExporter(Domain domain) throws Exception {
    String exporterAppPath = monitoringExporterDir + "/apps/monitoringexporter/wls-exporter.war";
    domain.undeployWebAppViaWlst("wls-exporter", appLocationInPod, true);
    domain.deployWebAppViaWlst(
        "wls-exporter", exporterAppPath, appLocationInPod, getUsername(), getPassword(), true);
    // check if exporter is up
    domain.callWebAppAndVerifyLoadBalancing("wls-exporter", false);
  }

  private static void resetMonitoringExporterToPreBuiltConfig() throws Exception {
    redeployMonitoringExporter(domain);
  }

  /**
   * Delete Prometheus, Grafana, Coordinator.
   *
   * @throws Exception if could not run the command successfully
   */
  private static void deletePrometheusGrafana() throws Exception {

    final String samplesDir = monitoringExporterDir + "/src/samples/kubernetes/deployments/";
    String prompodName = getPodName("app=prometheus", "monitoring");
    String grafanapodName = getPodName("name=grafana", "monitoring");
    executeShelScript(
        resourceExporterDir,
        monitoringExporterDir,
        "deletePromGrafana.sh",
        monitoringExporterDir + " " + domain.getDomainNs());
    TestUtils.checkPodDeleted(prompodName, "monitoring");
    TestUtils.checkPodDeleted(grafanapodName, "monitoring");
    String crdCmd = " kubectl delete -f " + samplesDir + "monitoring-namespace.yaml";
    TestUtils.exec(crdCmd);
    logger.info("Deleted Prometheus, Grafana, Coordinator");
  }

  /**
   * Delete Webhook and AlertManager.
   *
   * @throws Exception if could not run the command successfully
   */
  private static void deleteWebHookAlertManager() throws Exception {

    String samplesDir = monitoringExporterDir + "/src/samples/kubernetes/deployments/";
    String ampodName = getPodName("name=alertmanager", "monitoring");
    String webhookpodName = getPodName("name=webhook", "monitoring");
    executeShelScript(
        resourceExporterDir,
        monitoringExporterDir,
        "deleteWebHookAlertManager.sh",
        monitoringExporterDir + " " + domain.getDomainNs());
    TestUtils.checkPodDeleted(ampodName, "monitoring");
    TestUtils.checkPodDeleted(webhookpodName, "monitoring");
    logger.info("Deleted  Webhook, Alert Manager");
  }

  /**
   * A utility method to add desired domain namespace to coordinator yaml template file replacing
   * the DOMAIN_NS.
   *
   * @throws IOException when copying files from source location to staging area fails
   */
  private static void createCoordinatorFile(String domainNS) throws IOException {
    String samplesDir = monitoringExporterDir + "/src/samples/kubernetes/deployments/";
    Path src = Paths.get(resourceExporterDir + "/coordinator.yml");
    Path dst = Paths.get(samplesDir + "/coordinator_" + domainNS + ".yaml");
    if (!dst.toFile().exists()) {
      logger.log(Level.INFO, "Copying {0}", src.toString());
      Charset charset = StandardCharsets.UTF_8;
      String content = new String(Files.readAllBytes(src), charset);
      content = content.replaceAll("default", domainNS);
      logger.log(Level.INFO, "to {0}", dst.toString());
      Files.write(dst, content.getBytes(charset));
    }
  }

  /**
   * A utility method to copy Cross Namespaces RBAC yaml template file replacing the DOMAIN_NS,
   * OPERATOR_NS.
   *
   * @throws IOException when copying files from source location to staging area fails
   */
  private static void createCrossNsRbacFile(String domainNS, String operatorNS) throws IOException {
    String samplesDir = monitoringExporterDir + "/src/samples/kubernetes/deployments/";
    Path src = Paths.get(samplesDir + "/crossnsrbac.yaml");
    Path dst = Paths.get(samplesDir + "/crossnsrbac_" + domainNS + "_" + operatorNS + ".yaml");
    if (!dst.toFile().exists()) {
      logger.log(Level.INFO, "Copying {0}", src.toString());
      Charset charset = StandardCharsets.UTF_8;
      String content = new String(Files.readAllBytes(src), charset);
      content = content.replaceAll("weblogic-domain", domainNS);
      content = content.replaceAll("weblogic-operator", operatorNS);
      logger.log(Level.INFO, "to {0}", dst.toString());
      Files.write(dst, content.getBytes(charset));
    }
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
    startExporterPrometheusGrafana(domain, operator);
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

  private static void startExporterPrometheusGrafana(Domain domain, Operator operator)
      throws Exception {
    logger.info("deploy exporter, prometheus, grafana ");
    deployMonitoringExporterPrometethusGrafana(
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
    Assume.assumeFalse(QUICKTEST);
    String testMethodName = new Object() {}.getClass().getEnclosingMethod().getName();
    logTestBegin(testMethodName);
    boolean testCompletedSuccessfully = false;
    assertTrue(checkMetricsViaPrometheus(testwsappPrometheusSearchKey, "testwsapp"));
    testCompletedSuccessfully = true;
    logger.info("SUCCESS - " + testMethodName);
  }

  /**
   * Check that configuration can be reviewed via Prometheus.
   *
   * @throws Exception if test fails
   */
  @Test
  public void test01_1_VerifyScalingViaPrometheus() throws Exception {
    Assume.assumeFalse(QUICKTEST);
    String testMethodName = new Object() {}.getClass().getEnclosingMethod().getName();
    logTestBegin(testMethodName);
    boolean testCompletedSuccessfully = false;
    try {
      verifyScalingViaPrometheus(domain, TESTWSSERVICE);
    } finally {
      deleteWebHookAlertManager();
      scaleCluster(2);
    }
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
    Assume.assumeFalse(QUICKTEST);
    String testMethodName = new Object() {}.getClass().getEnclosingMethod().getName();
    logTestBegin(testMethodName);
    HtmlPage page = submitConfigureForm(exporterUrl, "replace", configPath + "/rest_jvm.yml");

    boolean isFoundNewKey1 = false;
    boolean isFoundNewKey2 = false;
    isFoundNewKey1 = checkMetricsViaPrometheus(prometheusSearchKey1, "managed-server1");
    isFoundNewKey2 = checkMetricsViaPrometheus(prometheusSearchKey2, "managed-server2");

    boolean isFoundOldKey1 = true;
    boolean isFoundOldKey2 = true;

    isFoundOldKey1 = checkMetricsViaPrometheus(testwsappPrometheusSearchKey, "managed-server1");
    isFoundOldKey2 = checkMetricsViaPrometheus(testwsappPrometheusSearchKey, "managed-server2");
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
    logger.info("SUCCESS - " + testMethodName);
  }

  /**
   * Add additional monitoring exporter configuration and verify it was applied.
   *
   * @throws Exception if test fails
   */
  @Test
  public void test03_AppendConfiguration() throws Exception {
    Assume.assumeFalse(QUICKTEST);
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
    assertTrue(
        checkMetricsViaPrometheus(
                prometheusSearchKey1, "\"weblogic_serverName\":\"managed-server1\"")
            || checkMetricsViaPrometheus(
                prometheusSearchKey2, "\"weblogic_serverName\":\"managed-server2\""));
    assertTrue(checkMetricsViaPrometheus(testwsappPrometheusSearchKey, "testwsapp"));
    logger.info("SUCCESS - " + testMethodName);
  }

  /**
   * Replace monitoring exporter configuration with only one attribute and verify it was applied.
   *
   * @throws Exception if test fails
   */
  @Test
  public void test04_ReplaceOneAttributeValueAsArrayConfiguration() throws Exception {
    Assume.assumeFalse(QUICKTEST);
    String testMethodName = new Object() {}.getClass().getEnclosingMethod().getName();
    logTestBegin(testMethodName);

    resetMonitoringExporterToPreBuiltConfig();

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
    Assume.assumeFalse(QUICKTEST);
    String testMethodName = new Object() {}.getClass().getEnclosingMethod().getName();
    logTestBegin(testMethodName);
    resetMonitoringExporterToPreBuiltConfig();
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
    Assume.assumeFalse(QUICKTEST);
    String testMethodName = new Object() {}.getClass().getEnclosingMethod().getName();
    logTestBegin(testMethodName);
    resetMonitoringExporterToPreBuiltConfig();
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
    Assume.assumeFalse(QUICKTEST);
    String testMethodName = new Object() {}.getClass().getEnclosingMethod().getName();
    logTestBegin(testMethodName);
    resetMonitoringExporterToPreBuiltConfig();
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
    Assume.assumeFalse(QUICKTEST);
    String testMethodName = new Object() {}.getClass().getEnclosingMethod().getName();
    logTestBegin(testMethodName);
    resetMonitoringExporterToPreBuiltConfig();
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
    Assume.assumeFalse(QUICKTEST);
    String testMethodName = new Object() {}.getClass().getEnclosingMethod().getName();
    logTestBegin(testMethodName);
    resetMonitoringExporterToPreBuiltConfig();
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
    Assume.assumeFalse(QUICKTEST);
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
    Assume.assumeFalse(QUICKTEST);
    String testMethodName = new Object() {}.getClass().getEnclosingMethod().getName();
    logTestBegin(testMethodName);
    resetMonitoringExporterToPreBuiltConfig();
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
    Assume.assumeFalse(QUICKTEST);
    String testMethodName = new Object() {}.getClass().getEnclosingMethod().getName();
    logTestBegin(testMethodName);
    resetMonitoringExporterToPreBuiltConfig();
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
    Assume.assumeFalse(QUICKTEST);
    String testMethodName = new Object() {}.getClass().getEnclosingMethod().getName();
    logTestBegin(testMethodName);
    resetMonitoringExporterToPreBuiltConfig();
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
    Assume.assumeFalse(QUICKTEST);
    String testMethodName = new Object() {}.getClass().getEnclosingMethod().getName();
    logTestBegin(testMethodName);
    resetMonitoringExporterToPreBuiltConfig();
    final WebClient webClient = new WebClient();
    HtmlPage originalPage = webClient.getPage(exporterUrl);
    assertNotNull(originalPage);
    HtmlPage page =
        submitConfigureForm(exporterUrl, "replace", configPath + "/rest_snakecasefalse.yml");
    assertNotNull(page);
    assertFalse(page.asText().contains("metricsNameSnakeCase"));
    String searchKey = "weblogic_servlet_executionTimeAverage%7Bapp%3D%22testwsapp%22%7D%5B15s%5D";
    assertTrue(checkMetricsViaPrometheus(searchKey, "testwsap"));
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
    Assume.assumeFalse(QUICKTEST);
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
    Assume.assumeFalse(QUICKTEST);
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
    Assume.assumeFalse(QUICKTEST);
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
    Assume.assumeFalse(QUICKTEST);
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
    Assume.assumeFalse(QUICKTEST);
    String testMethodName = new Object() {}.getClass().getEnclosingMethod().getName();
    logTestBegin(testMethodName);
    changeConfigNegativeAuth(
        "replace",
        configPath + "/rest_snakecasetrue.yml",
        "401 Unauthorized for " + exporterUrl,
        wlsUser,
        "");
    resetMonitoringExporterToPreBuiltConfig();
    logger.info("SUCCESS - " + testMethodName);
  }

  /**
   * Test End to End example from MonitoringExporter github project.
   *
   * @throws Exception if test fails
   */
  @Test
  public void test19_EndToEndViaChart() throws Exception {
    Assume.assumeFalse(QUICKTEST);
    String testMethodName = new Object() {}.getClass().getEnclosingMethod().getName();
    logTestBegin(testMethodName);
    boolean testCompletedSuccessfully = false;
    try {

      setupPvMysql();
      createWlsImageAndDeploy();
      installWebHook();
      installPrometheusGrafanaViaChart();

      fireAlert();
      addMonitoringToExistedDomain();
    } finally {
      String crdCmd =
          " kubectl delete -f " + monitoringExporterEndToEndDir + "/demo-domains/domain1.yaml";
      ExecCommand.exec(crdCmd);
      crdCmd = "kubectl delete secret domain1-weblogic-credentials";
      ExecCommand.exec(crdCmd);
      uninstallWebHookPrometheusGrafanaViaChart();
      uninstallMySql();
      deletePvDir();
    }
    testCompletedSuccessfully = true;
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

  private void addMonitoringToExistedDomain() throws Exception {
    logger.info("Add monitoring to the running domain");
    resetMonitoringExporterToPreBuiltConfig();
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
  private static void setupPvMysql() throws Exception {
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
    // deploy PV and PVC
    // clean mysql processes
    String crdCmd = "sudo pkill mysql";
    ExecCommand.exec("crdCmd");
    crdCmd = "sudo pkill mysqlp";
    ExecCommand.exec("crdCmd");
    crdCmd = " kubectl apply -f " + monitoringExporterEndToEndDir + "/mysql/persistence.yaml";
    TestUtils.exec(crdCmd);
    crdCmd = " kubectl apply -f " + monitoringExporterEndToEndDir + "/mysql/mysql.yaml";
    TestUtils.exec(crdCmd);

    logger.fine("getSQL pod name ");
    String sqlPod = getPodName("app=mysql", "default");
    TestUtils.checkPodReady(sqlPod, "default");
    Thread.sleep(15000);
    ExecResult result =
        TestUtils.kubectlexecNoCheck(
            sqlPod, "default", " -- mysql -p123456 -e \"CREATE DATABASE domain1;\"");
    if (result.exitValue() != 0) {
      throw new RuntimeException(
          "FAILURE: command to create database domain1 "
              + sqlPod
              + " in the pod failed, returned "
              + result.stderr()
              + " "
              + result.stdout());
    }
    result =
        TestUtils.kubectlexecNoCheck(
            sqlPod,
            "default",
            " -- mysql -p123456 -e \"CREATE USER 'wluser1' IDENTIFIED BY 'wlpwd123';\"");
    if (result.exitValue() != 0) {
      throw new RuntimeException(
          "FAILURE: command to create user wluser1 "
              + sqlPod
              + " in the pod failed, returned "
              + result.stderr()
              + " "
              + result.stdout());
    }
    result =
        TestUtils.kubectlexecNoCheck(
            sqlPod, "default", " -- mysql -p123456 -e \"GRANT ALL ON domain1.* TO 'wluser1';\"");
    if (result.exitValue() != 0) {
      throw new RuntimeException(
          "FAILURE: command to grant all to user wluser1 "
              + sqlPod
              + " in the pod failed, returned "
              + result.stderr()
              + " "
              + result.stdout());
    }
    // verify all
    result =
        TestUtils.kubectlexecNoCheck(
            sqlPod, "default", " -- mysql -u wluser1 -pwlpwd123 -D domain1 -e \"show tables;\"");
    if (result.exitValue() != 0) {
      throw new RuntimeException(
          "FAILURE: failed to setup user and database "
              + " in the pod failed, returned "
              + result.stderr()
              + " "
              + result.stdout());
    }
  }

  /**
   * Install wls image tool and update wls pods.
   *
   * @throws Exception if could not run the command successfully to create WLSImage and deploy
   */
  private static void createWlsImageAndDeploy() throws Exception {
    operator1 = TestUtils.createOperator(OPERATOR1_YAML);

    String command =
        "cd "
            + monitoringExporterEndToEndDir
            + "/demo-domains/domainBuilder/ && ./build.sh domain1 "
            + wlsUser
            + " "
            + wlsPassword
            + " wluser1 wlpwd123";
    TestUtils.exec(command);
    String newImage = "domain1-image:1.0";
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
   * Install Prometheus and Grafana using helm chart.
   *
   * @throws Exception if could not run the command successfully to install Prometheus and Grafana
   */
  private static void installPrometheusGrafanaViaChart() throws Exception {
    // delete any running pods
    deletePrometheusGrafana();
    prometheusPort = "30500";
    String crdCmd = "kubectl create ns monitoring";
    TestUtils.exec(crdCmd);
    crdCmd = "kubectl apply -f " + monitoringExporterEndToEndDir + "/prometheus/persistence.yaml";
    TestUtils.exec(crdCmd);
    replaceStringInFile(
        monitoringExporterEndToEndDir + "/prometheus/values.yaml", "30000", "30500");

    crdCmd =
        "kubectl apply -f " + monitoringExporterEndToEndDir + "/prometheus/alert-persistence.yaml";
    TestUtils.exec(crdCmd);
    // install prometheus
    crdCmd =
        "helm install --wait --name prometheus --namespace monitoring --values  "
            + monitoringExporterEndToEndDir
            + "/prometheus/values.yaml stable/prometheus --version 8.14.3";
    ExecResult result = ExecCommand.exec(crdCmd);
    logger.info(" Result from helm install " + result.stdout() + " erros : " + result.stderr());
    String podName = getPodName("app=prometheus", "monitoring");
    TestUtils.checkPodReady(podName, "monitoring", "2/2");

    crdCmd = "kubectl -n monitoring get pods -l app=prometheus";
    ExecResult resultStatus = ExecCommand.exec(crdCmd);
    logger.info("Status of the pods " + resultStatus.stdout());
    result = ExecCommand.exec(crdCmd + "| grep prometheus-service");
    podName = result.stdout().trim();
    crdCmd = "kubectl -n monitoring describe pod " + podName;
    result = ExecCommand.exec(crdCmd);
    logger.info("Status of the prometheus service pod " + result.stdout());

    crdCmd = "kubectl -n monitoring -c prometheus-server-configmap-reload logs  " + podName;
    result = ExecCommand.exec(crdCmd);
    logger.info(
        "Log for prometheus service pod , container prometheus-server-configmap-reload"
            + result.stdout());

    crdCmd = "kubectl -n monitoring -c prometheus-server logs  " + podName;
    result = ExecCommand.exec(crdCmd);
    logger.info("Log for prometheus service pod , container prometheus-server" + result.stdout());

    assertFalse(
        "Can't create prometheus pods",
        resultStatus.stdout().contains("CrashLoopBackOff")
            || resultStatus.stdout().contains("Error"));

    crdCmd = "kubectl -n monitoring get svc -l app=prometheus";
    result = ExecCommand.exec(crdCmd);
    assertTrue(
        "Can't find prometheus-alertmanager service running",
        result.stdout().contains("prometheus-alertmanager"));
    assertTrue(
        "Can't find prometheus-kube-state-metrics service running",
        result.stdout().contains("prometheus-kube-state-metrics"));
    assertTrue(
        "Can't find prometheus-node-exporter service running",
        result.stdout().contains("prometheus-node-exporter"));
    assertTrue(
        "Can't find prometheus-server service running",
        result.stdout().contains("prometheus-server"));

    // install grafana
    crdCmd = "kubectl apply -f " + monitoringExporterEndToEndDir + "/grafana/persistence.yaml";
    TestUtils.exec(crdCmd);

    crdCmd =
        "kubectl --namespace monitoring create secret generic grafana-secret"
            + " --from-literal=username=admin --from-literal=password=12345678";
    TestUtils.exec(crdCmd);
    logger.info("calling helm install for grafana");
    crdCmd =
        "helm install --wait --name grafana --namespace monitoring --values  "
            + monitoringExporterEndToEndDir
            + "/grafana/values.yaml stable/grafana";
    TestUtils.exec(crdCmd);

    podName = getPodName("app=grafana", "monitoring");
    TestUtils.checkPodReady(podName, "monitoring");
    Thread.sleep(10000);

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
    assertTrue(
        "Can't find expected metrics",
        checkMetricsViaPrometheus("wls_servlet_execution_time_average", "test-webapp"));
  }

  /**
   * Install WebHook.
   *
   * @throws Exception if could not run the command successfully to install webhook and alert
   *     manager
   */
  private static void installWebHook() throws Exception {

    logger.info("building webhook image");
    String crdCmd =
        "cd " + monitoringExporterEndToEndDir + " && docker build ./webhook -t webhook-log:1.0";
    TestUtils.exec(crdCmd);

    // install webhook
    logger.info("installing webhook ");
    crdCmd = "kubectl create ns webhook ";
    ExecCommand.exec(crdCmd);

    crdCmd = "kubectl apply -f " + monitoringExporterEndToEndDir + "/webhook/server.yaml";
    TestUtils.exec(crdCmd);
    String webhookPod = getPodName("app=webhook", "webhook");
    TestUtils.checkPodReady(webhookPod, "webhook");
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
   * Uninstall deployments in specified namespace and with list of commands to execute.
   *
   * @param depName - name of deployment to uninstall
   * @param namespace - namespace where deployment is installed
   * @param cmdLines - list of command lines to execute
   * @throws Exception if could not run the command successfully to uninstall deployments
   */
  private static boolean uninstallDeployments(String depName, String namespace, String... cmdLines)
      throws Exception {
    String podName = "";
    boolean depUninstall = false;
    ExecResult result;
    logger.info("Uninstalling " + depName);
    try {
      podName = getPodName("app=" + depName, namespace);
    } catch (AssertionError assertionError) {
      // ignore, pod may not be created
      if (cmdLines.length > 1) {
        for (int i = 1; i < cmdLines.length; i++) {
          logger.info(" Executing command: " + cmdLines[i]);
          result = ExecCommand.exec(cmdLines[i]);
          logger.info(" Command output : " + result.stdout() + " errors out: " + result.stderr());
        }
      }
      return true;
    }

    try {
      logger.info(" Executing command: " + cmdLines[0]);
      result = ExecCommand.exec(cmdLines[0]);
      logger.info(" Command output : " + result.stdout() + " errors out: " + result.stderr());
      TestUtils.checkPodDeleted(podName, namespace);
      logger.info("Pod " + podName + "was deleted");
    } catch (Exception ex) {
      // pod was not deleted
      depUninstall = false;
    }
    if (cmdLines.length > 1) {
      for (int i = 1; i < cmdLines.length; i++) {
        logger.info(" Executing command: " + cmdLines[i]);
        result = ExecCommand.exec(cmdLines[i]);
        logger.info(" Command output : " + result.stdout() + " errors out: " + result.stderr());
      }
    }
    return depUninstall;
  }

  /**
   * Uninstall Prometheus and Grafana using helm chart.
   *
   * @throws Exception if could not run the command successfully to uninstall deployments
   */
  private static void uninstallWebHookPrometheusGrafanaViaChart() throws Exception {
    uninstallDeployments(
        "webhook",
        "webhook",
        "kubectl delete -f "
            + monitoringExporterEndToEndDir
            + "/webhook/server.yaml --ignore-not-found",
        "kubectl delete ns webhook");
    uninstallDeployments(
        "grafana",
        "monitoring",
        "helm delete --purge grafana",
        "kubectl -n monitoring delete secret grafana-secret --ignore-not-found",
        "kubectl delete -f "
            + monitoringExporterEndToEndDir
            + "/grafana/persistence.yaml --ignore-not-found");
    uninstallDeployments(
        "prometheus",
        "monitoring",
        "helm delete --purge prometheus",
        "kubectl delete -f "
            + monitoringExporterEndToEndDir
            + "/prometheus/persistence.yaml --ignore-not-found",
        "kubectl delete -f "
            + monitoringExporterEndToEndDir
            + "/prometheus/alert-persistence.yaml --ignore-not-found");
  }

  /**
   * Uninstall MYSQL.
   *
   * @throws Exception if could not run the command successfully to uninstall MySQL
   */
  private static void uninstallMySql() throws Exception {
    String monitoringExporterEndToEndDir =
        monitoringExporterDir + "/src/samples/kubernetes/end2end/";
    // unnstall mysql
    logger.info("Uninstalling mysql");
    uninstallDeployments(
        "mysql",
        "default",
        "kubectl delete -f "
            + monitoringExporterEndToEndDir
            + "mysql/mysql.yaml --ignore-not-found",
        "kubectl delete -f "
            + monitoringExporterEndToEndDir
            + "/mysql/persistence.yaml --ignore-not-found");
    /*
    String podName = "";
    String crdCmd = "";
    try {
        podName = getPodName("app=mysql", "default");
        crdCmd = " kubectl delete -f " + monitoringExporterEndToEndDir + "mysql/mysql.yaml --ignore-not-found";
        TestUtils.exec(crdCmd);
        BaseTest.setWaitTimePod(10);
        TestUtils.checkPodDeleted(podName, "default");
    } catch (AssertionError assertError) {
        //ignore, the pod may not be existed
    } catch (Exception ex) {
        //it take more time to terminate mysql
        BaseTest.setWaitTimePod(10);
        TestUtils.checkPodDeleted(podName, "default");
    }


    crdCmd = " kubectl delete -f " + monitoringExporterEndToEndDir + "mysql/persistence.yaml --ignore-not-found";
    ExecCommand.exec(crdCmd);

     */
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
}
