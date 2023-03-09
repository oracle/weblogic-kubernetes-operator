// Copyright (c) 2021, 2023, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.weblogic.kubernetes.utils;

import java.util.Map;

import oracle.weblogic.kubernetes.actions.TestActions;
import oracle.weblogic.kubernetes.actions.impl.LoggingExporterParams;
import oracle.weblogic.kubernetes.logging.LoggingFacade;

import static oracle.weblogic.kubernetes.TestConstants.ELASTICSEARCH_HTTPS_PORT;
import static oracle.weblogic.kubernetes.TestConstants.ELASTICSEARCH_HTTP_PORT;
import static oracle.weblogic.kubernetes.TestConstants.ELASTICSEARCH_IMAGE;
import static oracle.weblogic.kubernetes.TestConstants.ELASTICSEARCH_NAME;
import static oracle.weblogic.kubernetes.TestConstants.KIBANA_IMAGE;
import static oracle.weblogic.kubernetes.TestConstants.KIBANA_NAME;
import static oracle.weblogic.kubernetes.TestConstants.KIBANA_PORT;
import static oracle.weblogic.kubernetes.TestConstants.KIBANA_TYPE;
import static oracle.weblogic.kubernetes.actions.TestActions.installElasticsearch;
import static oracle.weblogic.kubernetes.actions.TestActions.installKibana;
import static oracle.weblogic.kubernetes.actions.TestActions.uninstallElasticsearch;
import static oracle.weblogic.kubernetes.actions.TestActions.uninstallKibana;
import static oracle.weblogic.kubernetes.assertions.TestAssertions.isElkStackPodReady;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.testUntil;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.withLongRetryPolicy;
import static oracle.weblogic.kubernetes.utils.ThreadSafeLogger.getLogger;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

public class LoggingExporterUtils {
  /**
   * Uninstall Elasticsearch.
   *
   * @param params logging exporter parameters to uninstall Elasticsearch
   *
   * @return true if the command to uninstall Elasticsearch succeeds, false otherwise
   */
  public static boolean uninstallAndVerifyElasticsearch(LoggingExporterParams params) {
    // uninstall Elasticsearch
    assertThat(uninstallElasticsearch(params))
        .as("Elasticsearch uninstallation succeeds")
        .withFailMessage("Elasticsearch uninstallation is failed")
        .isTrue();

    return true;
  }

  /**
   * Uninstall Kibana.
   *
   * @param params logging exporter parameters to uninstall Kibana
   *
   * @return true if the command to uninstall Kibana succeeds, false otherwise
   */
  public static boolean uninstallAndVerifyKibana(LoggingExporterParams params) {
    // uninstall Kibana
    assertThat(uninstallKibana(params))
        .as("Elasticsearch uninstallation succeeds")
        .withFailMessage("Elasticsearch uninstallation is failed")
        .isTrue();

    return true;
  }

  /**
   * Install Elasticsearch and wait up to five minutes until Elasticsearch pod is ready.
   *
   * @param namespace elastic search namespace
   * @return Elasticsearch installation parameters
   */
  public static LoggingExporterParams installAndVerifyElasticsearch(String namespace) {
    LoggingFacade logger = getLogger();
    final String elasticsearchPodNamePrefix = ELASTICSEARCH_NAME;

    // parameters to install Elasticsearch
    LoggingExporterParams elasticsearchParams = new LoggingExporterParams()
        .elasticsearchName(ELASTICSEARCH_NAME)
        .elasticsearchImage(ELASTICSEARCH_IMAGE)
        .elasticsearchHttpPort(ELASTICSEARCH_HTTP_PORT)
        .elasticsearchHttpsPort(ELASTICSEARCH_HTTPS_PORT)
        .loggingExporterNamespace(namespace);
    logger.info("ES namespace:{0}}", elasticsearchParams.getLoggingExporterNamespace());

    // install Elasticsearch
    assertThat(installElasticsearch(elasticsearchParams))
        .as("Elasticsearch installation succeeds")
        .withFailMessage("Elasticsearch installation is failed")
        .isTrue();

    // wait until the Elasticsearch pod is ready.
    testUntil(
        withLongRetryPolicy,
        assertDoesNotThrow(() -> isElkStackPodReady(namespace, elasticsearchPodNamePrefix),
          "isElkStackPodReady failed with ApiException"),
        logger,
        "Elasticsearch to be ready in namespace {0}",
        namespace);

    return elasticsearchParams;
  }

  /**
   * Install Kibana and wait up to five minutes until Kibana pod is ready.
   *
   * @return Kibana installation parameters
   */
  public static LoggingExporterParams installAndVerifyKibana(String namespace) {
    LoggingFacade logger = getLogger();
    final String kibanaPodNamePrefix = ELASTICSEARCH_NAME;

    // parameters to install Kibana
    LoggingExporterParams kibanaParams = new LoggingExporterParams()
        .kibanaName(KIBANA_NAME)
        .kibanaImage(KIBANA_IMAGE)
        .kibanaType(KIBANA_TYPE)
        .loggingExporterNamespace(namespace)
        .kibanaContainerPort(KIBANA_PORT);

    logger.info("Choosen KIBANA_IMAGE {0}", KIBANA_IMAGE);
    // install Kibana
    assertThat(installKibana(kibanaParams))
        .as("Kibana installation succeeds")
        .withFailMessage("Kibana installation is failed")
        .isTrue();

    // wait until the Kibana pod is ready.
    testUntil(
        assertDoesNotThrow(() -> isElkStackPodReady(namespace, kibanaPodNamePrefix),
          "isElkStackPodReady failed with ApiException"),
        logger,
        "Kibana to be ready in namespace {0}",
        namespace);

    return kibanaParams;
  }

  /**
   * Verify that the logging exporter is ready to use in Operator pod or WebLogic server pod.
   *
   * @param opNamespace namespace of Operator pod (for ELK Stack) or
   *                  WebLogic server pod (for WebLogic logging exporter)
   * @param esNamespace namespace of Elastic search component
   * @param labelSelector string containing the labels the Operator or WebLogic server is decorated with
   * @param index key word used to search the index status of the logging exporter
   * @return a map containing key and value pair of logging exporter index
   */
  public static Map<String, String> verifyLoggingExporterReady(String opNamespace,
                                                               String esNamespace,
                                                               String labelSelector,
                                                               String index) {
    return TestActions.verifyLoggingExporterReady(opNamespace, esNamespace, labelSelector, index);
  }
}
