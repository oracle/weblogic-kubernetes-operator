// Copyright (c) 2020, 2023, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.weblogic.kubernetes;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import oracle.weblogic.kubernetes.actions.impl.LoggingExporterParams;
import oracle.weblogic.kubernetes.actions.impl.OperatorParams;
import oracle.weblogic.kubernetes.actions.impl.primitive.HelmParams;
import oracle.weblogic.kubernetes.annotations.IntegrationTest;
import oracle.weblogic.kubernetes.annotations.Namespaces;
import oracle.weblogic.kubernetes.logging.LoggingFacade;
import oracle.weblogic.kubernetes.utils.ExecResult;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static oracle.weblogic.kubernetes.TestConstants.ELASTICSEARCH_HTTPS_PORT;
import static oracle.weblogic.kubernetes.TestConstants.ELASTICSEARCH_HTTP_PORT;
import static oracle.weblogic.kubernetes.TestConstants.ELASTICSEARCH_IMAGE;
import static oracle.weblogic.kubernetes.TestConstants.ELASTICSEARCH_NAME;
import static oracle.weblogic.kubernetes.TestConstants.KIBANA_IMAGE;
import static oracle.weblogic.kubernetes.TestConstants.KIBANA_INDEX_KEY;
import static oracle.weblogic.kubernetes.TestConstants.KIBANA_NAME;
import static oracle.weblogic.kubernetes.TestConstants.KIBANA_PORT;
import static oracle.weblogic.kubernetes.TestConstants.KIBANA_TYPE;
import static oracle.weblogic.kubernetes.TestConstants.KUBERNETES_CLI;
import static oracle.weblogic.kubernetes.TestConstants.LOGSTASH_INDEX_KEY;
import static oracle.weblogic.kubernetes.TestConstants.MII_BASIC_APP_NAME;
import static oracle.weblogic.kubernetes.TestConstants.OKD;
import static oracle.weblogic.kubernetes.TestConstants.OPERATOR_CHART_DIR;
import static oracle.weblogic.kubernetes.TestConstants.OPERATOR_RELEASE_NAME;
import static oracle.weblogic.kubernetes.TestConstants.SKIP_CLEANUP;
import static oracle.weblogic.kubernetes.actions.ActionConstants.ITTESTS_DIR;
import static oracle.weblogic.kubernetes.actions.ActionConstants.MODEL_DIR;
import static oracle.weblogic.kubernetes.actions.ActionConstants.RESOURCE_DIR;
import static oracle.weblogic.kubernetes.actions.ActionConstants.WORK_DIR;
import static oracle.weblogic.kubernetes.actions.TestActions.execCommand;
import static oracle.weblogic.kubernetes.actions.TestActions.getOperatorPodName;
import static oracle.weblogic.kubernetes.assertions.TestAssertions.operatorIsReady;
import static oracle.weblogic.kubernetes.utils.CommonMiiTestUtils.createMiiDomainAndVerify;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.testUntil;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.withLongRetryPolicy;
import static oracle.weblogic.kubernetes.utils.ConfigMapUtils.configMapExist;
import static oracle.weblogic.kubernetes.utils.ConfigMapUtils.createConfigMapFromFiles;
import static oracle.weblogic.kubernetes.utils.ConfigMapUtils.replaceConfigMap;
import static oracle.weblogic.kubernetes.utils.ExecCommand.exec;
import static oracle.weblogic.kubernetes.utils.FileUtils.copy;
import static oracle.weblogic.kubernetes.utils.FileUtils.copyFileFromPodUsingK8sExec;
import static oracle.weblogic.kubernetes.utils.FileUtils.replaceStringInFile;
import static oracle.weblogic.kubernetes.utils.FileUtils.searchStringInFile;
import static oracle.weblogic.kubernetes.utils.ImageUtils.createMiiImageAndVerify;
import static oracle.weblogic.kubernetes.utils.ImageUtils.createTestRepoSecret;
import static oracle.weblogic.kubernetes.utils.ImageUtils.imageRepoLoginAndPushImageToRegistry;
import static oracle.weblogic.kubernetes.utils.LoggingExporterUtils.installAndVerifyElasticsearch;
import static oracle.weblogic.kubernetes.utils.LoggingExporterUtils.installAndVerifyKibana;
import static oracle.weblogic.kubernetes.utils.LoggingExporterUtils.uninstallAndVerifyElasticsearch;
import static oracle.weblogic.kubernetes.utils.LoggingExporterUtils.uninstallAndVerifyKibana;
import static oracle.weblogic.kubernetes.utils.LoggingExporterUtils.verifyLoggingExporterReady;
import static oracle.weblogic.kubernetes.utils.OKDUtils.addSccToNsSvcAccount;
import static oracle.weblogic.kubernetes.utils.OperatorUtils.installAndVerifyOperator;
import static oracle.weblogic.kubernetes.utils.OperatorUtils.upgradeAndVerifyOperator;
import static oracle.weblogic.kubernetes.utils.ThreadSafeLogger.getLogger;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * To test ELK Stack used in Operator env, this Elasticsearch test does
 * 1. Install Kibana/Elasticsearch.
 * 2. Install and start Operators with ELK Stack enabled,
 *    one used to test createLogStashConfigMap = true and another one is used to
 *    test createLogStashConfigMap = false.
 * 3. Verify that ELK Stack is ready to use by checking the index status of
 *    Kibana and Logstash created in the Operator pod successfully.
 * 4. Create and start the WebLogic domain.
 * 5. Verify that
 *    1) Elasticsearch collects data from WebLogic logs and
 *       stores them in its repository correctly.
 *    2) Users can update logstash configuration by updating the configmap
 *       weblogic-operator-logstash-cm instead of rebuilding operator image
 */
@DisplayName("Test to use Elasticsearch API to query WebLogic logs")
@IntegrationTest
@Tag("olcne")
@Tag("oke-parallel")
@Tag("kind-parallel")
@Tag("okd-wls-mrg")
class ItElasticLogging {

  // constants for creating domain image using model in image
  private static final String WLS_ELK_LOGGING_MODEL_FILE = "model.wlslogging.yaml";
  private static final String WLS_ELK_LOGGING_IMAGE_NAME = "wls-logging-image";

  // constants for testing WebLogic Logging Exporter
  private static final String wlsLoggingExporterYamlFileLoc = RESOURCE_DIR + "/loggingexporter";

  // constants for Domain
  private static String domainUid = "elk-domain1";
  private static String clusterName = "cluster-1";
  private static String adminServerName = "admin-server";
  private static String adminServerPodName = domainUid + "-" + adminServerName;
  private static String managedServerPrefix = "managed-server";
  private static String managedServerPodPrefix = domainUid + "-" + managedServerPrefix;
  private static String managedServerFilter = managedServerPrefix + "1";
  private static int replicaCount = 2;

  private static String opNamespace = null;
  private static String opNamespace2 = null;
  private static String domainNamespace = null;

  private static LoggingExporterParams elasticsearchParams = null;
  private static LoggingExporterParams kibanaParams = null;
  private static LoggingFacade logger = null;
  static String elasticSearchHost;
  static String elasticSearchNs;

  private static String k8sExecCmdPrefix;
  private static Map<String, String> testVarMap;

  private static String sourceConfigFile = ITTESTS_DIR + "/../kubernetes/charts/weblogic-operator/logstash.conf";
  private static String destConfigFile = WORK_DIR + "/logstash.conf";
  private static String sourceSettingsFile = ITTESTS_DIR + "/../kubernetes/charts/weblogic-operator/logstash.yml";
  private static String destSettingsFile = WORK_DIR + "/logstash.yml";

  /**
   * Install Elasticsearch, Kibana and Operator.
   * Install WebLogic Logging Exporter in all WebLogic server pods to collect WebLogic logs.
   * Create domain.
   *
   * @param namespaces list of namespaces created by the IntegrationTestWatcher by the
   *                   JUnit engine parameter resolution mechanism.
   */
  @BeforeAll
  public static void init(@Namespaces(4) List<String> namespaces) {
    logger = getLogger();

    // get a new unique opNamespace
    logger.info("Assigning a unique namespace for Operator");
    assertNotNull(namespaces.get(0), "Namespace list is null");
    opNamespace = namespaces.get(0);

    // get a new unique opNamespace2
    logger.info("Assigning a unique namespace for Operator");
    assertNotNull(namespaces.get(1), "Namespace list is null");
    opNamespace2 = namespaces.get(1);

    // get a new unique domainNamespace
    logger.info("Assigning a unique namespace for Domain");
    assertNotNull(namespaces.get(2), "Namespace list is null");
    domainNamespace = namespaces.get(2);

    // install and verify Elasticsearch
    elasticSearchNs = namespaces.get(3);
    elasticSearchHost = "elasticsearch." + elasticSearchNs + ".svc.cluster.local";

    if (OKD) {
      addSccToNsSvcAccount("default", elasticSearchNs);
    }

    logger.info("install and verify Elasticsearch");
    elasticsearchParams = assertDoesNotThrow(() -> installAndVerifyElasticsearch(elasticSearchNs),
            "Failed to install Elasticsearch");
    assertNotNull(elasticsearchParams, "Failed to install Elasticsearch");

    // install and verify Kibana
    logger.info("install and verify Kibana");
    kibanaParams = assertDoesNotThrow(() -> installAndVerifyKibana(elasticSearchNs), "Failed to install Kibana");
    assertNotNull(kibanaParams, "Failed to install Kibana");

    // install and verify Operator
    installAndVerifyOperator(opNamespace, opNamespace + "-sa",
        false, 0, true, domainNamespace);

    // upgrade to latest operator
    HelmParams upgradeHelmParams = new HelmParams()
        .releaseName(OPERATOR_RELEASE_NAME)
        .namespace(opNamespace)
        .chartDir(OPERATOR_CHART_DIR);

    // build operator chart values
    OperatorParams opParams = new OperatorParams()
        .helmParams(upgradeHelmParams)
        .elkIntegrationEnabled(true)
        .elasticSearchHost(elasticSearchHost);

    assertTrue(upgradeAndVerifyOperator(opNamespace, opParams),
        String.format("Failed to upgrade operator in namespace %s", opNamespace));

    // wait for the operator to be ready
    logger.info("Wait for the operator pod is ready in namespace {0}", opNamespace);
    testUntil(
        assertDoesNotThrow(() -> operatorIsReady(opNamespace),
            "operatorIsReady failed with ApiException"),
        logger,
        "operator to be running in namespace {0}",
        opNamespace);

    // create and verify WebLogic domain image using model in image with model files
    String imageName = createAndVerifyDomainImage();

    // create and verify one cluster domain
    logger.info("Create domain and verify that it's running");
    createAndVerifyDomain(imageName);

    StringBuffer elasticsearchUrlBuff =
        new StringBuffer("curl http://")
            .append(elasticSearchHost)
            .append(":")
            .append(ELASTICSEARCH_HTTP_PORT);
    k8sExecCmdPrefix = elasticsearchUrlBuff.toString();
    logger.info("Elasticsearch URL {0}", k8sExecCmdPrefix);

    // Verify that ELK Stack is ready to use
    testVarMap = new HashMap<String, String>();
    testVarMap = verifyLoggingExporterReady(opNamespace, elasticSearchNs, null, LOGSTASH_INDEX_KEY);
    Map<String, String> kibanaMap = verifyLoggingExporterReady(opNamespace, elasticSearchNs, null, KIBANA_INDEX_KEY);

    // merge testVarMap and kibanaMap
    testVarMap.putAll(kibanaMap);
  }

  /**
   * Uninstall ELK Stack and delete domain custom resource.
   */
  @AfterAll
  void tearDown() {
    if (!SKIP_CLEANUP) {
      // uninstall ELK Stack
      elasticsearchParams = new LoggingExporterParams()
          .elasticsearchName(ELASTICSEARCH_NAME)
          .elasticsearchImage(ELASTICSEARCH_IMAGE)
          .elasticsearchHttpPort(ELASTICSEARCH_HTTP_PORT)
          .elasticsearchHttpsPort(ELASTICSEARCH_HTTPS_PORT)
          .loggingExporterNamespace(elasticSearchNs);

      kibanaParams = new LoggingExporterParams()
          .kibanaName(KIBANA_NAME)
          .kibanaImage(KIBANA_IMAGE)
          .kibanaType(KIBANA_TYPE)
          .loggingExporterNamespace(elasticSearchNs)
          .kibanaContainerPort(KIBANA_PORT);

      logger.info("Uninstall Elasticsearch pod");
      assertDoesNotThrow(() -> uninstallAndVerifyElasticsearch(elasticsearchParams),
          "uninstallAndVerifyElasticsearch failed with ApiException");

      logger.info("Uninstall Kibana pod");
      assertDoesNotThrow(() -> uninstallAndVerifyKibana(kibanaParams),
          "uninstallAndVerifyKibana failed with ApiException");
    }
  }

  /**
   * Use Elasticsearch Count API to query logs of level=INFO. Verify that total number of logs
   * for level=INFO is not zero and failed count is zero.
   */
  @Test
  @DisplayName("Use Elasticsearch Count API to query logs of level=INFO and verify")
  void testLogLevelSearch() {
    // Verify that number of logs is not zero and failed count is zero
    String regex = ".*count\":(\\d+),.*failed\":(\\d+)";
    String queryCriteria = "/_count?q=level:INFO";

    // verify log level query results
    withLongRetryPolicy.untilAsserted(
        () -> assertTrue(verifyCountsHitsInSearchResults(queryCriteria, regex, LOGSTASH_INDEX_KEY, true),
            "Query logs of level=INFO failed"));

    logger.info("Query logs of level=INFO succeeded");
  }

  /**
   * Use Elasticsearch Search APIs to query Operator log info. Verify that log occurrence for
   * type=weblogic-operator are not empty.
   */
  @Test
  @DisplayName("Use Elasticsearch Search APIs to query Operator log info and verify")
  void testOperatorLogSearch() {
    // Verify that log occurrence for Operator are not empty
    String regex = ".*took\":(\\d+),.*hits\":\\{(.+)\\}";
    String queryCriteria = "/_search?q=type:weblogic-operator";

    // verify results of query of type:weblogic-operator in Operator log
    withLongRetryPolicy.untilAsserted(
        () -> assertTrue(verifyCountsHitsInSearchResults(queryCriteria, regex, LOGSTASH_INDEX_KEY, false),
            "Query Operator log info q=type:weblogic-operator failed"));

    logger.info("Query Operator log info q=type:weblogic-operator succeeded");
  }

  /**
   * Use Elasticsearch Search APIs to query WebLogic log info.
   * Verify that WebLogic server status of "RUNNING" is found.
   */
  @Disabled("Disabled the test due to JIRA OWLS-83899")
  @Test
  @DisplayName("Use Elasticsearch Search APIs to query Operator log info and verify")
  void testWebLogicLogSearch() {
    // Verify that the admin status of "RUNNING" is found in query return from Elasticsearch repository
    verifyServerRunningInSearchResults(adminServerPodName);

    // Verify that the ms status of "RUNNING" is found in query return from Elasticsearch repos
    verifyServerRunningInSearchResults(managedServerPodPrefix + "1");

    logger.info("Query Operator log for WebLogic server status info succeeded");
  }

  /**
   * Test when variable createLogStashConfigMap sets to true, a configMap named weblogic-operator-logstash-cm
   * is created and users can update logstash configuration by updating the configmap
   * instead of rebuilding operator image.
   */
  @Test
  @DisplayName("Test that configMap weblogic-operator-logstash-cm is created and able to be modified")
  void testCreateLogStashConfigMapModify() throws Exception {
    String configMapName = "weblogic-operator-logstash-cm";
    List<Path> logstashConfigFiles = new ArrayList<>();

    // verify that configMap weblogic-operator-logstash-cm is created when createLogStashConfigMap= = true
    Callable<Boolean> configMapExist = assertDoesNotThrow(() -> configMapExist(opNamespace, configMapName),
        "copy logstash.conf failed");
    assertTrue(configMapExist.call().booleanValue(),
        String.format("configMap %s in namespace %s DO NOT exist", configMapName, opNamespace));

    // copy the logstash config file to workdir
    assertDoesNotThrow(() -> copy(Paths.get(sourceConfigFile), Paths.get(destConfigFile)),
        "copy logstash.conf failed");
    logstashConfigFiles.add(Paths.get(destConfigFile));

    // modify the configMape with cusotm logstash config file
    String replaceStr = "msg";
    logger.info("edit ConfigMap {0} to change 'message' to {1}", configMapName, replaceStr);
    assertDoesNotThrow(() -> replaceStringInFile(destConfigFile, "message", replaceStr),
        "replaceStringInFile failed");
    assertTrue(replaceConfigMap(opNamespace, configMapName, destConfigFile),
        String.format("Failed to replace configMap %s in namespace %s", configMapName, opNamespace));

    verifyLogstashConfigMapModifyResult(replaceStr);
  }

  /**
   * Test that users are able to use their own configMap weblogic-operator-logstash-cm
   * to create the Operator when variable createLogStashConfigMap sets to false.
   */
  @Test
  @DisplayName("Test that users can config and create their own configMap "
      + "when configMapcreateLogStashConfigMap = false")
  void testCreateLogStashConfigMapFalse() throws Exception {
    boolean elkIntegrationEnabled = true;
    boolean createLogStashConfigMap = false;
    String defaultNamespace = "default";
    String configMapName = "weblogic-operator-logstash-cm";

    assertDoesNotThrow(() -> copy(Paths.get(sourceConfigFile), Paths.get(destConfigFile)),
        "copy logstash.conf failed");
    assertDoesNotThrow(() -> copy(Paths.get(sourceSettingsFile), Paths.get(destSettingsFile)),
        "copy logstash.yml failed");

    List<Path> logstashConfigFiles = new ArrayList<>();
    logstashConfigFiles.add(Paths.get(destConfigFile));
    logstashConfigFiles.add(Paths.get(destSettingsFile));

    //create config map for logstash config
    createConfigMapFromFiles(configMapName, logstashConfigFiles, opNamespace2);

    // install and verify Operator2 up and running with createLogStashConfigMap = false
    installAndVerifyOperator(opNamespace2, opNamespace2 + "-sa",
        false, 0, elasticSearchHost, elkIntegrationEnabled,
        createLogStashConfigMap, defaultNamespace);
  }

  private static String createAndVerifyDomainImage() {
    String miiImage = null;

    // create image with model files
    if (!OKD) {
      logger.info("Create image with model file and verify");
      miiImage = createMiiImageAndVerify(WLS_ELK_LOGGING_IMAGE_NAME, WLS_ELK_LOGGING_MODEL_FILE,MII_BASIC_APP_NAME);
    } else {
      List<String> appList = new ArrayList<>();
      appList.add(MII_BASIC_APP_NAME);

      // build the model file list
      final List<String> modelList = Collections.singletonList(MODEL_DIR + "/" + WLS_ELK_LOGGING_MODEL_FILE);

      // create image with model files
      logger.info("Create image with model file and verify");
      miiImage = createMiiImageAndVerify(WLS_ELK_LOGGING_IMAGE_NAME, modelList, appList);
    }

    // repo login and push image to registry if necessary
    imageRepoLoginAndPushImageToRegistry(miiImage);

    // create registry secret to pull the image from registry
    // this secret is used only for non-kind cluster
    logger.info("Create registry secret in namespace {0}", domainNamespace);
    createTestRepoSecret(domainNamespace);

    return miiImage;
  }

  private static  void createAndVerifyDomain(String miiImage) {
    // create a domain resource
    logger.info("Create model-in-image domain {0} in namespace {1}, and wait until it comes up",
        domainUid, domainNamespace);
    List<String> clusterNames = new ArrayList();
    clusterNames.add("cluster-1");
    createMiiDomainAndVerify(
        domainNamespace,
        domainUid,
        miiImage,
        adminServerPodName,
        managedServerPodPrefix,
        replicaCount,
        clusterNames);
  }

  private void verifyServerRunningInSearchResults(String serverName) {
    String queryCriteria = "/_search?q=log:" + serverName;
    withLongRetryPolicy.untilAsserted(
        () -> assertTrue(execSearchQuery(queryCriteria, LOGSTASH_INDEX_KEY).contains("RUNNING"),
          String.format("serverName %s is not RUNNING", serverName)));

    String queryResult = execSearchQuery(queryCriteria, LOGSTASH_INDEX_KEY);
    logger.info("query result is {0}", queryResult);
  }

  private boolean verifyCountsHitsInSearchResults(String queryCriteria, String regex,
                                                  String index, boolean checkCount, String... args) {
    String checkExist = (args.length == 0) ? "" : args[0];
    boolean testResult = false;
    int count = -1;
    int failedCount = -1;
    String hits = "";

    String results = execSearchQuery(queryCriteria, index);
    Pattern pattern = Pattern.compile(regex, Pattern.DOTALL | Pattern.MULTILINE);
    Matcher matcher = pattern.matcher(results);
    if (matcher.find()) {
      count = Integer.parseInt(matcher.group(1));
      if (checkCount) {
        failedCount = Integer.parseInt(matcher.group(2));
      } else {
        hits = matcher.group(2);
      }
    }

    logger.info("Total count of logs: " + count);
    if (!checkExist.equalsIgnoreCase("notExist")) {
      assertNotNull(kibanaParams, "Failed to install Kibana");
      assertTrue(count > 0, "Total count of logs should be more than 0!");
      if (checkCount) {
        logger.info("Total failed count: " + failedCount);
        assertEquals(0, failedCount, "Total failed count should be 0!");
      } else {
        assertFalse(hits.isEmpty(), "Total hits of search is empty!");
      }
      testResult = true;
    } else {
      assertEquals(0, count, "Total count of logs should be zero!");
      testResult = true;
    }

    return testResult;
  }

  private String execSearchQuery(String queryCriteria, String index) {
    String operatorPodName = assertDoesNotThrow(
        () -> getOperatorPodName(OPERATOR_RELEASE_NAME, opNamespace));
    assertTrue(operatorPodName != null && !operatorPodName.isEmpty(), "Failed to get Operator pad name");
    logger.info("Operator pod name " + operatorPodName);

    int waittime = 5;
    String indexName = testVarMap.get(index);
    StringBuffer curlOptions = new StringBuffer(" --connect-timeout " + waittime)
        .append(" --max-time " + waittime)
        .append(" -X GET ");
    StringBuffer k8sExecCmdPrefixBuff = new StringBuffer(k8sExecCmdPrefix);
    int offset = k8sExecCmdPrefixBuff.indexOf("http");
    k8sExecCmdPrefixBuff.insert(offset, curlOptions);
    String cmd = k8sExecCmdPrefixBuff
        .append("/")
        .append(indexName)
        .append(queryCriteria)
        .toString();
    logger.info("Exec command {0} in Operator pod {1}", cmd, operatorPodName);

    ExecResult execResult = assertDoesNotThrow(
        () -> execCommand(opNamespace, operatorPodName, null, true,
            "/bin/sh", "-c", cmd));
    assertNotNull(execResult, "curl command returns null");
    logger.info("Search query returns " + execResult.stdout());

    return execResult.stdout();
  }

  private void verifyLogstashConfigMapModifyResult(String replaceStr) {
    String containerName = "logstash";
    String operatorPodName = assertDoesNotThrow(
        () -> getOperatorPodName(OPERATOR_RELEASE_NAME, opNamespace));
    assertTrue(operatorPodName != null && !operatorPodName.isEmpty(),
        "Failed to get Operator pad name");
    logger.info("Operator pod name " + operatorPodName);

    // command to restart logstash container
    StringBuffer restartLogstashCmd = new StringBuffer(KUBERNETES_CLI + " exec ");
    restartLogstashCmd.append(" -n ");
    restartLogstashCmd.append(opNamespace);
    restartLogstashCmd.append(" pod/");
    restartLogstashCmd.append(operatorPodName);
    restartLogstashCmd.append(" -c ");
    restartLogstashCmd.append(containerName);
    restartLogstashCmd.append(" -- kill -SIGHUP 1");
    logger.info("Command to restart logstash is {0}", restartLogstashCmd.toString());

    // restart logstash container
    ExecResult execResult = assertDoesNotThrow(() -> exec(restartLogstashCmd.toString(), true));
    logger.info("command to restart logstash returned {0}", execResult.toString());

    assertNotNull(execResult, "command returns null");
    if (execResult.exitValue() == 0) {
      logger.info("command {0} returns {1}", restartLogstashCmd, execResult.toString());
    } else {
      logger.info("Failed to exec the command command {0}. Error is {1} ", restartLogstashCmd, execResult.stderr());
    }

    // wait for logstash config modified and verify
    withLongRetryPolicy.untilAsserted(
        () -> assertTrue(copyConfigFromPodAndSearchForString(containerName, replaceStr),
            String.format("Failed to find search string %s", replaceStr)));
  }

  private boolean copyConfigFromPodAndSearchForString(String containerName, String replaceStr) {
    String sourceConfigFileInPod = "/usr/share/logstash/pipeline/logstash.conf";
    String newDestConfigFile = WORK_DIR + "/logstash_new.conf";
    String operatorPodName = assertDoesNotThrow(
        () -> getOperatorPodName(OPERATOR_RELEASE_NAME, opNamespace));
    assertTrue(operatorPodName != null && !operatorPodName.isEmpty(),
        "Failed to get Operator pad name");
    logger.info("Operator pod name " + operatorPodName);

    assertDoesNotThrow(
        () -> Files.deleteIfExists(Paths.get(newDestConfigFile)));

    // copy logstash config file from logstash container to the local
    assertDoesNotThrow(
        () -> copyFileFromPodUsingK8sExec(opNamespace, operatorPodName,
            containerName, sourceConfigFileInPod, Paths.get(newDestConfigFile)));

    // verify that the logstash config is modified successfully
    boolean configFileModified =
        assertDoesNotThrow(() -> searchStringInFile(newDestConfigFile, replaceStr), "searchStringInFile failed");

    return configFileModified;
  }
}
