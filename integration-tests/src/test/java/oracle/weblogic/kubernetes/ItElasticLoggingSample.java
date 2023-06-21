// Copyright (c) 2023, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.weblogic.kubernetes;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import oracle.weblogic.kubernetes.actions.impl.OperatorParams;
import oracle.weblogic.kubernetes.actions.impl.primitive.Command;
import oracle.weblogic.kubernetes.actions.impl.primitive.CommandParams;
import oracle.weblogic.kubernetes.actions.impl.primitive.HelmParams;
import oracle.weblogic.kubernetes.annotations.IntegrationTest;
import oracle.weblogic.kubernetes.annotations.Namespaces;
import oracle.weblogic.kubernetes.logging.LoggingFacade;
import oracle.weblogic.kubernetes.utils.ExecResult;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static oracle.weblogic.kubernetes.TestConstants.BUSYBOX_IMAGE;
import static oracle.weblogic.kubernetes.TestConstants.BUSYBOX_TAG;
import static oracle.weblogic.kubernetes.TestConstants.ELASTICSEARCH_HTTP_PORT;
import static oracle.weblogic.kubernetes.TestConstants.ELASTICSEARCH_IMAGE;
import static oracle.weblogic.kubernetes.TestConstants.KIBANA_IMAGE;
import static oracle.weblogic.kubernetes.TestConstants.KIBANA_INDEX_KEY;
import static oracle.weblogic.kubernetes.TestConstants.KUBERNETES_CLI;
import static oracle.weblogic.kubernetes.TestConstants.LOGSTASH_INDEX_KEY;
import static oracle.weblogic.kubernetes.TestConstants.OPERATOR_CHART_DIR;
import static oracle.weblogic.kubernetes.TestConstants.OPERATOR_RELEASE_NAME;
import static oracle.weblogic.kubernetes.actions.ActionConstants.ITTESTS_DIR;
import static oracle.weblogic.kubernetes.actions.ActionConstants.WORK_DIR;
import static oracle.weblogic.kubernetes.actions.TestActions.execCommand;
import static oracle.weblogic.kubernetes.actions.TestActions.getOperatorPodName;
import static oracle.weblogic.kubernetes.assertions.TestAssertions.operatorIsReady;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.testUntil;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.withLongRetryPolicy;
import static oracle.weblogic.kubernetes.utils.FileUtils.replaceStringInFile;
import static oracle.weblogic.kubernetes.utils.LoggingExporterUtils.verifyLoggingExporterReady;
import static oracle.weblogic.kubernetes.utils.OperatorUtils.installAndVerifyOperator;
import static oracle.weblogic.kubernetes.utils.OperatorUtils.upgradeAndVerifyOperator;
import static oracle.weblogic.kubernetes.utils.ThreadSafeLogger.getLogger;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * To add integration tests for ELK Stack sample on Operator, this test does
 * 1. Install and start Elasticsearch/Kibana using sample script
 *    kubernetes/samples/scripts/elasticsearch-and-kibana/elasticsearch_and_kibana.yaml.
 * 2. Install and start Operators with ELK Stack enabled,
 * 3. Verify that ELK Stack is ready to use by checking the index status of
 *    Kibana and Logstash created in the Operator pod successfully.
 * 4. Verify that Elasticsearch collects data from Operator logs and
 *       stores them in its repository correctly.
 */
@DisplayName("ELK Stack sample to test to use Elasticsearch API to query Operator logs")
@IntegrationTest
@Tag("kind-parallel")
class ItElasticLoggingSample {
  // constants for namespaces
  private static String domainNamespace = null;
  private static String opNamespace = null;

  // constants for ELK stack
  static String elasticSearchHost;
  static String elasticSearchNs = "default";
  private static String sourceELKConfigFile =
      ITTESTS_DIR + "/../kubernetes/samples/scripts/elasticsearch-and-kibana/elasticsearch_and_kibana.yaml";
  private static String destELKConfigFile = WORK_DIR + "elasticsearch_and_kibana.yaml";

  // constants for test
  private static String k8sExecCmdPrefix;
  private static Map<String, String> testVarMap;

  private static LoggingFacade logger = null;

  /**
   * Install Elasticsearch, Kibana and Operator.
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

    // copy original ELK config file to work dir
    Path sourceELKConfigFilePath = Paths.get(sourceELKConfigFile);
    Path destELKConfigFilePath = Paths.get(destELKConfigFile);
    assertDoesNotThrow(() -> Files.copy(sourceELKConfigFilePath, destELKConfigFilePath,
        StandardCopyOption.REPLACE_EXISTING)," Failed to copy files");

    // reploce the location for busybox image
    assertDoesNotThrow(() -> replaceStringInFile(destELKConfigFilePath.toString(),
        "busybox", BUSYBOX_IMAGE + ":" + BUSYBOX_TAG),
            "Failed to replace String: " + BUSYBOX_IMAGE + ":" + BUSYBOX_TAG);

    // reploce the location for ELK stack image
    assertDoesNotThrow(() -> replaceStringInFile(destELKConfigFilePath.toString(),
        "elasticsearch:7.8.1", ELASTICSEARCH_IMAGE),"Failed to replace String: " + ELASTICSEARCH_IMAGE);
    assertDoesNotThrow(() -> replaceStringInFile(destELKConfigFilePath.toString(),
        "kibana:7.8.1", KIBANA_IMAGE),"Failed to replace String: " + KIBANA_IMAGE);

    // install and verify Elasticsearch and Kibana;
    elasticSearchHost = "elasticsearch." + elasticSearchNs + ".svc.cluster.local";
    logger.info("install and verify Elasticsearch and Kibana");

    CommandParams params = new CommandParams().defaults();
    params.command(KUBERNETES_CLI + " apply -f " + destELKConfigFilePath);
    boolean result = Command.withParams(params).execute();
    assertTrue(result, "Failed to install Elasticsearch and Kibana");

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
        .elasticSearchHost(elasticSearchHost)
        .elasticSearchPort(9200)
        .createLogStashConfigMap(true);

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
   * Uninstall ELK Stack.
   */
  @AfterAll
  void tearDown() {
    // uninstall ELK Stack
    logger.info("uninstall and verify Elasticsearch and Kibana");

    Path destELKConfigFilePath = Paths.get(destELKConfigFile);
    CommandParams params = new CommandParams().defaults();
    params.command(KUBERNETES_CLI + " delete -f " + destELKConfigFilePath);
    boolean result = Command.withParams(params).execute();
    assertTrue(result, "Failed to uninstall Elasticsearch and Kibana");
  }

  /**
   * Use Elasticsearch Count API to query Operator logs of level=INFO.
   * Verify that total number of logs for level=INFO is not zero and failed count is zero.
   */
  @Test
  @DisplayName("Use Elasticsearch Count API to query Operator logs of level=INFO and verify")
  void testOpLogLevelSearch() {
    // Verify that number of logs is not zero and failed count is zero
    String regex = ".*count\":(\\d+),.*failed\":(\\d+)";
    String queryCriteria = "/_count?q=level:INFO";

    // verify log level query results
    withLongRetryPolicy.untilAsserted(
        () -> assertTrue(verifyCountsHitsInSearchResults(queryCriteria, regex, LOGSTASH_INDEX_KEY),
            "Query logs of level=INFO failed"));

    logger.info("Query logs of level=INFO succeeded");
  }

  private boolean verifyCountsHitsInSearchResults(String queryCriteria, String regex, String index) {
    boolean testResult = false;
    int count = -1;
    int failedCount = -1;

    ExecResult results = execSearchQuery(queryCriteria, index);
    if (results.exitValue() == 0) {
      Pattern pattern = Pattern.compile(regex, Pattern.DOTALL | Pattern.MULTILINE);
      Matcher matcher = pattern.matcher(results.stdout());
      if (matcher.find()) {
        count = Integer.parseInt(matcher.group(1));
        failedCount = Integer.parseInt(matcher.group(2));
      }

      // verify that total count > 0 and failed count = 0 in Operator log
      logger.info("Total count of logs: " + count);
      assertTrue(count > 0, "Total count of logs should be more than 0!");
      logger.info("Total failed count: " + failedCount);
      assertEquals(0, failedCount, "Total failed count should be 0!");
      testResult = true;
    } else {
      fail("Failed to verify total count and failed count in Operator log" + results.stderr());
    }

    return testResult;
  }

  private ExecResult execSearchQuery(String queryCriteria, String index) {
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

    return execResult;
  }
}
