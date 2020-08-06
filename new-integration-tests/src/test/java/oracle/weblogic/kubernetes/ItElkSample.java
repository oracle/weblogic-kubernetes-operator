// Copyright (c) 2020, Oracle Corporation and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.weblogic.kubernetes;

import java.util.List;

import oracle.weblogic.kubernetes.actions.impl.LoggingExporterParams;
import oracle.weblogic.kubernetes.annotations.IntegrationTest;
import oracle.weblogic.kubernetes.annotations.Namespaces;
import oracle.weblogic.kubernetes.logging.LoggingFacade;
import org.awaitility.core.ConditionFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static java.util.concurrent.TimeUnit.MINUTES;
import static java.util.concurrent.TimeUnit.SECONDS;
import static oracle.weblogic.kubernetes.TestConstants.KIBANA_INDEX_KEY;
import static oracle.weblogic.kubernetes.TestConstants.LOGSTASH_INDEX_KEY;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.installAndVerifyElasticsearch;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.installAndVerifyKibana;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.installAndVerifyOperator;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.uninstallAndVerifyElasticsearch;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.uninstallAndVerifyKibana;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.verifyLoggingExporterReady;
import static oracle.weblogic.kubernetes.utils.ThreadSafeLogger.getLogger;
import static org.awaitility.Awaitility.with;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verify that Elasticsearch collects data from WebLogic logs and
 * stores them in its repository correctly.
 */
@DisplayName("Test to use Elasticsearch API to query WebLogic logs")
@IntegrationTest
class ItElkSample {

  // constants for Operator
  private static String opNamespace = null;
  private static String domainNamespace = null;
  private static ConditionFactory withStandardRetryPolicy = null;

  // constants for ELK Stack
  private static LoggingExporterParams elasticsearchParams = null;
  private static LoggingExporterParams kibanaParams = null;
  private static LoggingFacade logger = null;

  /**
   * Install Elasticsearch, Kibana and Operator, create a one cluster domain.
   *
   * @param namespaces list of namespaces created by the IntegrationTestWatcher by the
   *                   JUnit engine parameter resolution mechanism
   */
  @BeforeAll
  public static void init(@Namespaces(2) List<String> namespaces) {
    logger = getLogger();
    // create standard, reusable retry/backoff policy
    withStandardRetryPolicy = with().pollDelay(2, SECONDS)
      .and().with().pollInterval(10, SECONDS)
      .atMost(5, MINUTES).await();

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
    installAndVerifyOperator(opNamespace, opNamespace + "-sa", false, 0, true, domainNamespace);
  }

  /**
   * Uninstall ELK Stack and delete domain custom resource.
   */
  @AfterAll
  void tearDown() {
    // uninstall ELK Stack
    if (elasticsearchParams != null) {
      logger.info("Uninstall Elasticsearch pod");
      assertDoesNotThrow(() -> uninstallAndVerifyElasticsearch(elasticsearchParams),
          "uninstallAndVerifyElasticsearch failed with ApiException");
    }

    if (kibanaParams != null) {
      logger.info("Uninstall Elasticsearch pod");
      assertDoesNotThrow(() -> uninstallAndVerifyKibana(kibanaParams),
          "uninstallAndVerifyKibana failed with ApiException");
    }
  }

  /**
   * Verify that ELK Stack is ready to use by checking the index status of
   * Kibana and Logstash created in the Operator pod successfully.
   */
  @Test
  @DisplayName("Verify that ELK Stack is ready to use")
  public void testLogLevelSearch() throws Exception {
    // Verify that Elastic Stack is ready to use
    verifyLoggingExporterReady(opNamespace, null, LOGSTASH_INDEX_KEY);
    verifyLoggingExporterReady(opNamespace, null, KIBANA_INDEX_KEY);
  }
}
