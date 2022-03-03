// Copyright (c) 2021, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.weblogic.kubernetes.utils;

import java.nio.file.Files;
import java.nio.file.Paths;
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
import static oracle.weblogic.kubernetes.TestConstants.WLS_LOGGING_EXPORTER_YAML_FILE_DIR;
import static oracle.weblogic.kubernetes.TestConstants.WLS_LOGGING_EXPORTER_YAML_FILE_NAME;
import static oracle.weblogic.kubernetes.actions.ActionConstants.RESOURCE_DIR;
import static oracle.weblogic.kubernetes.actions.TestActions.installElasticsearch;
import static oracle.weblogic.kubernetes.actions.TestActions.installKibana;
import static oracle.weblogic.kubernetes.actions.TestActions.uninstallElasticsearch;
import static oracle.weblogic.kubernetes.actions.TestActions.uninstallKibana;
import static oracle.weblogic.kubernetes.assertions.TestAssertions.isElkStackPodReady;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.testUntil;
import static oracle.weblogic.kubernetes.utils.FileUtils.copyFolder;
import static oracle.weblogic.kubernetes.utils.FileUtils.replaceStringInFile;
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
   * @return Elasticsearch installation parameters
   */
  public static LoggingExporterParams installAndVerifyElasticsearch(String elkStackNamespace) {
    LoggingFacade logger = getLogger();
    final String elasticsearchPodNamePrefix = ELASTICSEARCH_NAME;

    // parameters to install Elasticsearch
    LoggingExporterParams elasticsearchParams = new LoggingExporterParams()
        .elasticsearchName(ELASTICSEARCH_NAME)
        .elasticsearchImage(ELASTICSEARCH_IMAGE)
        .elasticsearchHttpPort(ELASTICSEARCH_HTTP_PORT)
        .elasticsearchHttpsPort(ELASTICSEARCH_HTTPS_PORT)
        .loggingExporterNamespace(elkStackNamespace);

    // install Elasticsearch
    assertThat(installElasticsearch(elasticsearchParams))
        .as("Elasticsearch installation succeeds")
        .withFailMessage("Elasticsearch installation is failed")
        .isTrue();

    // wait until the Elasticsearch pod is ready.
    testUntil(
        assertDoesNotThrow(() -> isElkStackPodReady(elkStackNamespace, elasticsearchPodNamePrefix),
          "isElkStackPodReady failed with ApiException"),
        logger,
        "Elasticsearch to be ready in namespace {0}",
        elkStackNamespace);

    return elasticsearchParams;
  }

  /**
   * Install Kibana and wait up to five minutes until Kibana pod is ready.
   *
   * @return Kibana installation parameters
   */
  public static LoggingExporterParams installAndVerifyKibana(String elkStackNamespace) {
    LoggingFacade logger = getLogger();
    final String kibanaPodNamePrefix = ELASTICSEARCH_NAME;

    // parameters to install Kibana
    LoggingExporterParams kibanaParams = new LoggingExporterParams()
        .kibanaName(KIBANA_NAME)
        .kibanaImage(KIBANA_IMAGE)
        .kibanaType(KIBANA_TYPE)
        .loggingExporterNamespace(elkStackNamespace)
        .kibanaContainerPort(KIBANA_PORT);

    // install Kibana
    assertThat(installKibana(kibanaParams))
        .as("Kibana installation succeeds")
        .withFailMessage("Kibana installation is failed")
        .isTrue();

    // wait until the Kibana pod is ready.
    testUntil(
        assertDoesNotThrow(() -> isElkStackPodReady(elkStackNamespace, kibanaPodNamePrefix),
          "isElkStackPodReady failed with ApiException"),
        logger,
        "Kibana to be ready in namespace {0}",
        elkStackNamespace);

    return kibanaParams;
  }

  /**
   * Install WebLogic Logging Exporter.
   *
   * @param filter the value of weblogicLoggingExporterFilters to be added to WebLogic Logging Exporter YAML file
   * @param wlsLoggingExporterYamlFileLoc the directory where WebLogic Logging Exporter YAML file stores
   * @return true if WebLogic Logging Exporter is successfully installed, false otherwise.
   */
  public static boolean installAndVerifyWlsLoggingExporter(String filter,
                                                           String wlsLoggingExporterYamlFileLoc) {
    // Install WebLogic Logging Exporter
    assertThat(TestActions.installWlsLoggingExporter(filter,
        wlsLoggingExporterYamlFileLoc))
        .as("WebLogic Logging Exporter installation succeeds")
        .withFailMessage("WebLogic Logging Exporter installation failed")
        .isTrue();

    return true;
  }

  /**
   * Verify that the logging exporter is ready to use in Operator pod or WebLogic server pod.
   *
   * @param namespace namespace of Operator pod (for ELK Stack) or
   *                  WebLogic server pod (for WebLogic Logging Exporter)
   * @param index key word used to search the index status of the logging exporter
   * @return a map containing key and value pair of logging exporter index
   */
  public static Map<String, String> verifyLoggingExporterReady(String namespace,
                                                               String elkStackNamespace,
                                                               String index) {
    return TestActions.verifyLoggingExporterReady(namespace, elkStackNamespace, index);
  }

  /**
   * Generate WeblogicLoggingExporter.yaml for a given test class
   *
   * @param namespace the namespace in which the pod exists and used to replace existing default value
   * @param destYamlFileDir destination dir of yaml file
   */
  public static void generateNewWlsLoggingExporterYamlFile(String namespace,
                                                           String destYamlFileDir) {
    LoggingFacade logger = getLogger();
    final String srcYamlFileDir = RESOURCE_DIR + "/" + WLS_LOGGING_EXPORTER_YAML_FILE_DIR;
    final String destYamlFile = destYamlFileDir + "/" + WLS_LOGGING_EXPORTER_YAML_FILE_NAME;

    // create dest dir
    assertDoesNotThrow(() -> Files.createDirectories(
        Paths.get(destYamlFileDir)),
        String.format("Could not create directory under %s", destYamlFileDir));

    // copy WeblogicLoggingExporter.yaml to results dir
    logger.info("Copy "  + srcYamlFileDir + " to " + destYamlFileDir);
    assertDoesNotThrow(() -> copyFolder(srcYamlFileDir, destYamlFileDir),
        "Failed to copy " + srcYamlFileDir + " to " + destYamlFileDir);

    // replace default with namespace in WeblogicLoggingExporter.yaml
    assertDoesNotThrow(() -> replaceStringInFile(
        destYamlFile.toString(), "default", namespace),
        "Could not modify namespace in " + destYamlFile);
  }
}
