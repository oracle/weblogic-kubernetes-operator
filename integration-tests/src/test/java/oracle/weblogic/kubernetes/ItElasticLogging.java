// Copyright (c) 2020, 2021, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.weblogic.kubernetes;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import oracle.weblogic.kubernetes.actions.impl.LoggingExporterParams;
import oracle.weblogic.kubernetes.annotations.IntegrationTest;
import oracle.weblogic.kubernetes.annotations.Namespaces;
import oracle.weblogic.kubernetes.logging.LoggingFacade;
import oracle.weblogic.kubernetes.utils.ExecResult;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIfEnvironmentVariable;

import static oracle.weblogic.kubernetes.TestConstants.COPY_WLS_LOGGING_EXPORTER_FILE_NAME;
import static oracle.weblogic.kubernetes.TestConstants.ELASTICSEARCH_HOST;
import static oracle.weblogic.kubernetes.TestConstants.ELASTICSEARCH_HTTPS_PORT;
import static oracle.weblogic.kubernetes.TestConstants.ELASTICSEARCH_HTTP_PORT;
import static oracle.weblogic.kubernetes.TestConstants.ELASTICSEARCH_IMAGE;
import static oracle.weblogic.kubernetes.TestConstants.ELASTICSEARCH_NAME;
import static oracle.weblogic.kubernetes.TestConstants.ELKSTACK_NAMESPACE;
import static oracle.weblogic.kubernetes.TestConstants.KIBANA_IMAGE;
import static oracle.weblogic.kubernetes.TestConstants.KIBANA_INDEX_KEY;
import static oracle.weblogic.kubernetes.TestConstants.KIBANA_NAME;
import static oracle.weblogic.kubernetes.TestConstants.KIBANA_PORT;
import static oracle.weblogic.kubernetes.TestConstants.KIBANA_TYPE;
import static oracle.weblogic.kubernetes.TestConstants.LOGSTASH_INDEX_KEY;
import static oracle.weblogic.kubernetes.TestConstants.MII_BASIC_APP_NAME;
import static oracle.weblogic.kubernetes.TestConstants.OKD;
import static oracle.weblogic.kubernetes.TestConstants.OPERATOR_RELEASE_NAME;
import static oracle.weblogic.kubernetes.TestConstants.WEBLOGIC_INDEX_KEY;
import static oracle.weblogic.kubernetes.TestConstants.WLS_LOGGING_EXPORTER_YAML_FILE_NAME;
import static oracle.weblogic.kubernetes.actions.ActionConstants.DOWNLOAD_DIR;
import static oracle.weblogic.kubernetes.actions.ActionConstants.MODEL_DIR;
import static oracle.weblogic.kubernetes.actions.ActionConstants.RESOURCE_DIR;
import static oracle.weblogic.kubernetes.actions.ActionConstants.SNAKE_DOWNLOADED_FILENAME;
import static oracle.weblogic.kubernetes.actions.ActionConstants.WLE_DOWNLOAD_FILENAME_DEFAULT;
import static oracle.weblogic.kubernetes.actions.ActionConstants.WORK_DIR;
import static oracle.weblogic.kubernetes.actions.TestActions.execCommand;
import static oracle.weblogic.kubernetes.actions.TestActions.getOperatorPodName;
import static oracle.weblogic.kubernetes.utils.CommonMiiTestUtils.createMiiDomainAndVerify;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.withStandardRetryPolicy;
import static oracle.weblogic.kubernetes.utils.ImageUtils.createMiiImageAndVerify;
import static oracle.weblogic.kubernetes.utils.ImageUtils.createOcirRepoSecret;
import static oracle.weblogic.kubernetes.utils.ImageUtils.dockerLoginAndPushImageToRegistry;
import static oracle.weblogic.kubernetes.utils.LoggingExporterUtils.installAndVerifyElasticsearch;
import static oracle.weblogic.kubernetes.utils.LoggingExporterUtils.installAndVerifyKibana;
import static oracle.weblogic.kubernetes.utils.LoggingExporterUtils.installAndVerifyWlsLoggingExporter;
import static oracle.weblogic.kubernetes.utils.LoggingExporterUtils.uninstallAndVerifyElasticsearch;
import static oracle.weblogic.kubernetes.utils.LoggingExporterUtils.uninstallAndVerifyKibana;
import static oracle.weblogic.kubernetes.utils.LoggingExporterUtils.verifyLoggingExporterReady;
import static oracle.weblogic.kubernetes.utils.OperatorUtils.installAndVerifyOperator;
import static oracle.weblogic.kubernetes.utils.ThreadSafeLogger.getLogger;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * To test ELK Stack used in Operator env, this Elasticsearch test does
 * 1. Install Kibana/Elasticsearch.
 * 2. Install and start Operator with ELK Stack enabled.
 * 3. Verify that ELK Stack is ready to use by checking the index status of
 *    Kibana and Logstash created in the Operator pod successfully.
 * 4. Install WebLogic Logging Exporter in all WebLogic server pods by
 *    adding WebLogic Logging Exporter binary to the image builder process
 *    so that it will be available in the domain image via
 *    --additionalBuildCommands and --additionalBuildFiles.
 * 5. Create and start the WebLogic domain.
 * 6. Verify that
 *    1) Elasticsearch collects data from WebLogic logs and
 *       stores them in its repository correctly.
 *    2) Using WebLogic Logging Exporter, WebLogic server Logs can be integrated to
 *       ELK Stack in the same pod that the domain is running on.
 */
@DisplayName("Test to use Elasticsearch API to query WebLogic logs")
@IntegrationTest
class ItElasticLogging {

  // constants for creating domain image using model in image
  private static final String WLS_LOGGING_MODEL_FILE = "model.wlslogging.yaml";
  private static final String WLS_LOGGING_IMAGE_NAME = "wls-logging-image";

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
  private static String domainNamespace = null;

  private static LoggingExporterParams elasticsearchParams = null;
  private static LoggingExporterParams kibanaParams = null;
  private static LoggingFacade logger = null;

  private static String k8sExecCmdPrefix;
  private static Map<String, String> testVarMap;

  /**
   * Install Elasticsearch, Kibana and Operator.
   * Install WebLogic Logging Exporter in all WebLogic server pods to collect WebLogic logs.
   * Create domain.
   *
   * @param namespaces list of namespaces created by the IntegrationTestWatcher by the
   *                   JUnit engine parameter resolution mechanism.
   */
  @BeforeAll
  public static void init(@Namespaces(2) List<String> namespaces) {
    logger = getLogger();

    // get a new unique opNamespace
    logger.info("Assigning a unique namespace for Operator");
    assertNotNull(namespaces.get(0), "Namespace list is null");
    opNamespace = namespaces.get(0);

    // get a new unique domainNamespace
    logger.info("Assigning a unique namespace for Domain");
    assertNotNull(namespaces.get(1), "Namespace list is null");
    domainNamespace = namespaces.get(1);

    // install and verify Elasticsearch
    logger.info("install and verify Elasticsearch");
    elasticsearchParams = assertDoesNotThrow(() -> installAndVerifyElasticsearch(),
            String.format("Failed to install Elasticsearch"));
    assertTrue(elasticsearchParams != null, "Failed to install Elasticsearch");

    // install and verify Kibana
    logger.info("install and verify Kibana");
    kibanaParams = assertDoesNotThrow(() -> installAndVerifyKibana(),
        String.format("Failed to install Kibana"));
    assertTrue(kibanaParams != null, "Failed to install Kibana");

    // install and verify Operator
    installAndVerifyOperator(opNamespace, opNamespace + "-sa",
        false, 0, true, domainNamespace);

    // install WebLogic Logging Exporter
    if (!OKD) {
      installAndVerifyWlsLoggingExporter(managedServerFilter, wlsLoggingExporterYamlFileLoc);
    }

    // create and verify WebLogic domain image using model in image with model files
    String imageName = createAndVerifyDomainImage();

    // create and verify one cluster domain
    logger.info("Create domain and verify that it's running");
    createAndVerifyDomain(imageName);

    testVarMap = new HashMap<String, String>();

    StringBuffer elasticsearchUrlBuff =
        new StringBuffer("curl http://")
            .append(ELASTICSEARCH_HOST)
            .append(":")
            .append(ELASTICSEARCH_HTTP_PORT);
    k8sExecCmdPrefix = elasticsearchUrlBuff.toString();
    logger.info("Elasticsearch URL {0}", k8sExecCmdPrefix);

    // Verify that ELK Stack is ready to use
    testVarMap = verifyLoggingExporterReady(opNamespace, null, LOGSTASH_INDEX_KEY);
    Map<String, String> kibanaMap = verifyLoggingExporterReady(opNamespace, null, KIBANA_INDEX_KEY);

    // merge testVarMap and kibanaMap
    testVarMap.putAll(kibanaMap);
  }

  /**
   * Uninstall ELK Stack and delete domain custom resource.
   */
  @AfterAll
  void tearDown() {
    if (System.getenv("SKIP_CLEANUP") == null
        || (System.getenv("SKIP_CLEANUP") != null
        && System.getenv("SKIP_CLEANUP").equalsIgnoreCase("false"))) {

      // uninstall ELK Stack
      elasticsearchParams = new LoggingExporterParams()
          .elasticsearchName(ELASTICSEARCH_NAME)
          .elasticsearchImage(ELASTICSEARCH_IMAGE)
          .elasticsearchHttpPort(ELASTICSEARCH_HTTP_PORT)
          .elasticsearchHttpsPort(ELASTICSEARCH_HTTPS_PORT)
          .loggingExporterNamespace(ELKSTACK_NAMESPACE);

      kibanaParams = new LoggingExporterParams()
          .kibanaName(KIBANA_NAME)
          .kibanaImage(KIBANA_IMAGE)
          .kibanaType(KIBANA_TYPE)
          .loggingExporterNamespace(ELKSTACK_NAMESPACE)
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

    verifyCountsHitsInSearchResults(queryCriteria, regex, LOGSTASH_INDEX_KEY, true);

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

    verifyCountsHitsInSearchResults(queryCriteria, regex, LOGSTASH_INDEX_KEY, false);

    logger.info("Query Operator log info succeeded");
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
   * Use Elasticsearch Search APIs to query WebLogic log info pushed to Elasticsearch repository
   * by WebLogic Logging Exporter. Verify that log occurrence for WebLogic servers are not empty.
   */
  @Test
  @DisplayName("Use Elasticsearch Search APIs to query WebLogic log info in WLS server pod and verify")
  @DisabledIfEnvironmentVariable(named = "OKD", matches = "true")
  void testWlsLoggingExporter() throws Exception {
    Map<String, String> wlsMap = verifyLoggingExporterReady(opNamespace, null, WEBLOGIC_INDEX_KEY);
    // merge testVarMap and wlsMap
    testVarMap.putAll(wlsMap);

    // Verify that occurrence of log level = Notice are not empty
    String regex = ".*took\":(\\d+),.*hits\":\\{(.+)\\}";
    String queryCriteria = "/_search?q=level:Notice";
    verifyCountsHitsInSearchResults(queryCriteria, regex, WEBLOGIC_INDEX_KEY, false);

    // Verify that occurrence of loggerName = WebLogicServer are not empty
    queryCriteria = "/_search?q=loggerName:WebLogicServer";
    verifyCountsHitsInSearchResults(queryCriteria, regex, WEBLOGIC_INDEX_KEY, false);

    // Verify that occurrence of _type:doc are not empty
    queryCriteria = "/_search?q=_type:doc";
    verifyCountsHitsInSearchResults(queryCriteria, regex, WEBLOGIC_INDEX_KEY, false);

    // Verify that serverName:managed-server1 is filtered out
    // by checking the count of logs from serverName:managed-server1 is zero and no failures
    regex = "(?m).*\\s*.*count\"\\s*:\\s*(\\d+),.*failed\"\\s*:\\s*(\\d+)";
    StringBuffer queryCriteriaBuff = new StringBuffer("/doc/_count?pretty")
        .append(" -H 'Content-Type: application/json'")
        .append(" -d'{\"query\":{\"query_string\":{\"query\":\"")
        .append(managedServerFilter)
        .append("\",\"fields\":[\"serverName\"],\"default_operator\": \"AND\"}}}'");

    queryCriteria = queryCriteriaBuff.toString();
    verifyCountsHitsInSearchResults(queryCriteria, regex, WEBLOGIC_INDEX_KEY, true, "notExist");

    logger.info("Query WebLogic log info succeeded");
  }

  private static String createAndVerifyDomainImage() {
    String miiImage = null;

    // create image with model files
    if (!OKD) {
      String additionalBuildCommands = WORK_DIR + "/" + COPY_WLS_LOGGING_EXPORTER_FILE_NAME;
      StringBuffer additionalBuildFilesVarargsBuff = new StringBuffer()
          .append(WORK_DIR)
          .append("/")
          .append(WLS_LOGGING_EXPORTER_YAML_FILE_NAME)
          .append(",")
          .append(DOWNLOAD_DIR)
          .append("/")
          .append(WLE_DOWNLOAD_FILENAME_DEFAULT)
          .append(",")
          .append(DOWNLOAD_DIR)
          .append("/")
          .append(SNAKE_DOWNLOADED_FILENAME);

      logger.info("Create image with model file and verify");
      miiImage = createMiiImageAndVerify(WLS_LOGGING_IMAGE_NAME, WLS_LOGGING_MODEL_FILE,
          MII_BASIC_APP_NAME, additionalBuildCommands, additionalBuildFilesVarargsBuff.toString());
    } else {
      List<String> appList = new ArrayList();
      appList.add(MII_BASIC_APP_NAME);

      // build the model file list
      final List<String> modelList = Collections.singletonList(MODEL_DIR + "/" + WLS_LOGGING_MODEL_FILE);

      // create image with model files
      logger.info("Create image with model file and verify");
      miiImage = createMiiImageAndVerify(WLS_LOGGING_IMAGE_NAME, modelList, appList);
      /*
      miiImage = createMiiImageAndVerify(WLS_LOGGING_IMAGE_NAME,
          MODEL_DIR + "/" + WLS_LOGGING_MODEL_FILE, MII_BASIC_APP_NAME);*/
    }

    // docker login and push image to docker registry if necessary
    dockerLoginAndPushImageToRegistry(miiImage);

    // create docker registry secret to pull the image from registry
    // this secret is used only for non-kind cluster
    logger.info("Create docker registry secret in namespace {0}", domainNamespace);
    createOcirRepoSecret(domainNamespace);

    return miiImage;
  }

  private static  void createAndVerifyDomain(String miiImage) {
    // create a domain resource
    logger.info("Create model-in-image domain {0} in namespace {1}, and wait until it comes up",
        domainUid, domainNamespace);
    createMiiDomainAndVerify(
        domainNamespace,
        domainUid,
        miiImage,
        adminServerPodName,
        managedServerPodPrefix,
        replicaCount);
  }

  private void verifyServerRunningInSearchResults(String serverName) {
    String queryCriteria = "/_search?q=log:" + serverName;
    withStandardRetryPolicy.untilAsserted(
        () -> assertTrue(execSearchQuery(queryCriteria, LOGSTASH_INDEX_KEY).contains("RUNNING"),
          String.format("serverName %s is not RUNNING", serverName)));

    String queryResult = execSearchQuery(queryCriteria, LOGSTASH_INDEX_KEY);
    logger.info("query result is {0}", queryResult);
  }

  private void verifyCountsHitsInSearchResults(String queryCriteria, String regex,
                                   String index, boolean checkCount, String... args) {
    String checkExist = (args.length == 0) ? "" : args[0];
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
      assertTrue(kibanaParams != null, "Failed to install Kibana");
      assertTrue(count > 0, "Total count of logs should be more than 0!");
      if (checkCount) {
        assertTrue(failedCount == 0, "Total failed count should be 0!");
        logger.info("Total failed count: " + failedCount);
      } else {
        assertFalse(hits.isEmpty(), "Total hits of search is empty!");
      }
    } else {
      assertTrue(count == 0, "Total count of logs should be zero!");
    }
  }

  private String execSearchQuery(String queryCriteria, String index) {
    String operatorPodName = assertDoesNotThrow(
        () -> getOperatorPodName(OPERATOR_RELEASE_NAME, opNamespace));
    assertTrue(operatorPodName != null && !operatorPodName.isEmpty(), "Failed to get Operator pad name");
    logger.info("Operator pod name " + operatorPodName);

    int waittime = 5;
    String indexName = (String) testVarMap.get(index);
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
}
