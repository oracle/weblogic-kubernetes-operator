// Copyright (c) 2020, 2021, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.weblogic.kubernetes.actions.impl;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Callable;

import io.kubernetes.client.custom.IntOrString;
import io.kubernetes.client.openapi.models.V1Container;
import io.kubernetes.client.openapi.models.V1ContainerPort;
import io.kubernetes.client.openapi.models.V1Deployment;
import io.kubernetes.client.openapi.models.V1DeploymentSpec;
import io.kubernetes.client.openapi.models.V1EnvVar;
import io.kubernetes.client.openapi.models.V1LabelSelector;
import io.kubernetes.client.openapi.models.V1ObjectMeta;
import io.kubernetes.client.openapi.models.V1PodSpec;
import io.kubernetes.client.openapi.models.V1PodTemplateSpec;
import io.kubernetes.client.openapi.models.V1SecurityContext;
import io.kubernetes.client.openapi.models.V1Service;
import io.kubernetes.client.openapi.models.V1ServicePort;
import io.kubernetes.client.openapi.models.V1ServiceSpec;
import oracle.weblogic.kubernetes.actions.impl.primitive.Installer;
import oracle.weblogic.kubernetes.actions.impl.primitive.Kubernetes;
import oracle.weblogic.kubernetes.assertions.impl.Deployment;
import oracle.weblogic.kubernetes.logging.LoggingFacade;
import oracle.weblogic.kubernetes.utils.ExecResult;
import oracle.weblogic.kubernetes.utils.FileUtils;
import org.awaitility.core.ConditionFactory;

import static java.util.concurrent.TimeUnit.MINUTES;
import static java.util.concurrent.TimeUnit.SECONDS;
import static oracle.weblogic.kubernetes.TestConstants.COPY_WLS_LOGGING_EXPORTER_FILE_NAME;
import static oracle.weblogic.kubernetes.TestConstants.ELASTICSEARCH_HOST;
import static oracle.weblogic.kubernetes.TestConstants.ELASTICSEARCH_HTTP_PORT;
import static oracle.weblogic.kubernetes.TestConstants.KIBANA_INDEX_KEY;
import static oracle.weblogic.kubernetes.TestConstants.OPERATOR_RELEASE_NAME;
import static oracle.weblogic.kubernetes.TestConstants.WLS_LOGGING_EXPORTER_YAML_FILE_NAME;
import static oracle.weblogic.kubernetes.actions.ActionConstants.WORK_DIR;
import static oracle.weblogic.kubernetes.actions.TestActions.execCommand;
import static oracle.weblogic.kubernetes.actions.TestActions.getOperatorPodName;
import static oracle.weblogic.kubernetes.actions.impl.primitive.Installer.defaultInstallSnakeParams;
import static oracle.weblogic.kubernetes.actions.impl.primitive.Installer.defaultInstallWleParams;
import static oracle.weblogic.kubernetes.assertions.impl.Kubernetes.isPodReady;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.checkServiceExists;
import static oracle.weblogic.kubernetes.utils.ThreadSafeLogger.getLogger;
import static org.awaitility.Awaitility.with;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Utility class for ELK Stack and WebLogic Logging Exporter.
 */
public class LoggingExporter {

  private static LoggingFacade logger = getLogger();
  private static ConditionFactory withStandardRetryPolicy =
      with().pollDelay(2, SECONDS)
          .and().with().pollInterval(10, SECONDS)
          .atMost(5, MINUTES).await();

  private static final int maxIterationsPod = 10;

  /**
   * Install Elasticsearch.
   *
   * @param params parameters to install Elasticsearch
   * @return true if Elasticsearch is successfully installed, false otherwise.
   */
  public static boolean installElasticsearch(LoggingExporterParams params) {
    String elasticsearchName = params.getElasticsearchName();
    String namespace = params.getLoggingExporterNamespace();
    Map labels = new HashMap<String, String>();
    labels.put("app", elasticsearchName);

    // create Elasticsearch deployment CR
    logger.info("Create Elasticsearch deployment CR for {0} in namespace {1}",
        elasticsearchName, namespace);
    V1Deployment elasticsearchDeployment = createElasticsearchDeploymentCr(params);

    // create Elasticsearch deployment
    logger.info("Create Elasticsearch deployment {0} in namespace {1}", elasticsearchName, namespace);
    boolean deploymentCreated = assertDoesNotThrow(() -> Kubernetes.createDeployment(elasticsearchDeployment),
        "createDeployment of Elasticsearch failed with ApiException");
    assertTrue(deploymentCreated,
        String.format("Failed to create Elasticsearch deployment for %s in namespace %s",
            elasticsearchName, namespace));
    logger.info("Check if Elasticsearch deployment {0} is ready in namespace {1}",
        elasticsearchName, namespace);
    withStandardRetryPolicy
        .conditionEvaluationListener(
            condition -> logger.info("Waiting for Elasticsearch deployment {0}"
                + "to be completed in {1} namespace (elapsed time {2}ms, remaining time {3}ms)",
                elasticsearchName,
                namespace,
                condition.getElapsedTimeInMS(),
                condition.getRemainingTimeInMS()))
        .until(Deployment.isReady(elasticsearchName, labels, namespace));

    // create Elasticsearch service CR
    logger.info("Create Elasticsearch service CR for {0} in namespace {1}",
        elasticsearchName, namespace);
    V1Service elasticsearchService = createElasticsearchServiceCr(params);

    // create Elasticsearch service
    logger.info("Create Elasticsearch service {0} in namespace {1}", elasticsearchName, namespace);
    boolean serviceCreated = assertDoesNotThrow(() -> Kubernetes.createService(elasticsearchService),
        "createService of Elasticsearch failed with ApiException");
    assertTrue(serviceCreated, String.format(
        "Failed to create Elasticsearch service for %s in namespace %s",
            elasticsearchName, namespace));

    // check that Elasticsearch service exists in its namespace
    logger.info("Check that Elasticsearch service {0} exists in namespace {1}",
        elasticsearchName, namespace);
    checkServiceExists(elasticsearchName, namespace);

    return true;
  }

  /**
   * Install Kibana.
   *
   * @param params parameters to install Kibana
   * @return true if Kibana is successfully installed, false otherwise.
   */
  public static boolean installKibana(LoggingExporterParams params) {
    String kibanaName = params.getKibanaName();
    String namespace = params.getLoggingExporterNamespace();
    Map labels = new HashMap<String, String>();
    labels.put("app", kibanaName);

    // create Kibana deployment CR
    logger.info("Create Kibana deployment CR for {0} in namespace {1}", kibanaName, namespace);
    V1Deployment kibanaDeployment = createKibanaDeploymentCr(params);

    // create Kibana deployment
    logger.info("Create Kibana deployment {0} in namespace {1}", kibanaName, namespace);
    boolean deploymentCreated = assertDoesNotThrow(() -> Kubernetes.createDeployment(kibanaDeployment),
        "createDeployment of Kibana failed with ApiException");
    assertTrue(deploymentCreated,
        String.format("Failed to create Kibana deployment for %s in %s namespace",
            kibanaName, namespace));
    logger.info("Checking if Kibana deployment is ready {0} completed in namespace {1}",
        kibanaName, namespace);
    withStandardRetryPolicy
        .conditionEvaluationListener(
            condition -> logger.info("Waiting for Kibana deployment {0} to be completed"
                + " in namespace {1} (elapsed time {2}ms, remaining time {3}ms)",
                kibanaName,
                namespace,
                condition.getElapsedTimeInMS(),
                condition.getRemainingTimeInMS()))
        .until(Deployment.isReady(kibanaName, labels, namespace));

    // create Kibana service CR
    logger.info("Create Kibana service CR for {0} in namespace {1}", kibanaName, namespace);
    V1Service kibanaService = createKibanaServiceCr(params);

    // create Kibana service
    logger.info("Create Kibana service for {0} in namespace {1}", kibanaName, namespace);
    boolean serviceCreated = assertDoesNotThrow(() -> Kubernetes.createService(kibanaService),
        "createService of Kibana failed with ApiException");
    assertTrue(serviceCreated, String.format(
        "Failed to create Kibana service %s in namespace %s", kibanaName, namespace));

    // check that Kibana service exists in its namespace
    logger.info("Checking that Kibana service {0} exists in namespace {1}",
        kibanaName, namespace);
    checkServiceExists(kibanaName, namespace);

    return true;
  }

  /**
   * Uninstall Elasticsearch.
   *
   * @param params parameters to uninstall Elasticsearch
   * @return true if Elasticsearch is successfully uninstalled, false otherwise.
   */
  public static boolean uninstallElasticsearch(LoggingExporterParams params) {
    String elasticsearchName = params.getElasticsearchName();
    String namespace = params.getLoggingExporterNamespace();

    // Delete Elasticsearch deployment
    try {
      Kubernetes.deleteDeployment(namespace, elasticsearchName);
    } catch (Exception ex) {
      logger.warning("Failed to delete deployment {0} ", elasticsearchName);
    }

    logger.info("Delete Service {0} in namespace {1}", elasticsearchName, namespace);
    boolean serviceDeleted =
        assertDoesNotThrow(() -> Kubernetes.deleteService(elasticsearchName, namespace),
            "deleteService of Elasticsearch failed with ApiException for %s in %s namespace");
    assertTrue(serviceDeleted, String.format(
        "Failed to delete Elasticsearch service %s in %s namespace ", elasticsearchName, namespace));

    return true;
  }

  /**
   * Uninstall Kibana.
   *
   * @param params parameters to uninstall Kibana
   * @return true if Kibana is successfully uninstalled, false otherwise.
   */
  public static boolean uninstallKibana(LoggingExporterParams params) {
    String kibanaName = params.getKibanaName();
    String namespace = params.getLoggingExporterNamespace();

    // Delete Kibana deployment
    try {
      Kubernetes.deleteDeployment(namespace, kibanaName);
    } catch (Exception ex) {
      logger.warning("Failed to delete deployment {0} ", kibanaName);
    }

    logger.info("Delete Service {0} in namespace {1}", kibanaName, namespace);
    boolean serviceDeleted =
        assertDoesNotThrow(() -> Kubernetes.deleteService(kibanaName, namespace),
            "deleteService of Kibana failed with ApiException");
    assertTrue(serviceDeleted, String.format(
        "Failed to delete Kibana service %s in %s namespace ", kibanaName, namespace));

    return true;
  }

  /**
   * Check if ELK Stack pod is ready in a given namespace.
   *
   * @param namespace in which to check ELK Stack pod is ready
   * @param podName name of ELK Stack pod
   * @return true if ELK Stack pod is in the ready state, false otherwise
   */
  public static Callable<Boolean> isReady(String namespace, String podName) {
    String labelSelector = null;
    return () -> isPodReady(namespace, labelSelector, podName);
  }

  /**
   * Verify that the Logging Exporter is ready to use in Operator pod or WebLogic server pod.
   *
   * @param namespace namespace of Operator pod (for ELK Stack) or
   *                  WebLogic server pod (for WebLogic Logging Exporter)
   * @param labelSelector string containing the labels the Operator or WebLogic server is decorated with
   * @param index key word used to search the index status of the logging exporter
   * @return a map containing key and value pair of logging exporter index
   */
  public static Map<String, String> verifyLoggingExporterReady(String namespace,
                                                               String labelSelector,
                                                               String index) {
    // Get index status info
    String statusLine =
        execLoggingExpStatusCheck(namespace, labelSelector, "*" + index + "*");
    assertNotNull(statusLine);

    String [] parseString = statusLine.split("\\s+");
    assertTrue(parseString.length >= 3, index + " does not exist!");
    String healthStatus = parseString[0];
    String indexStatus = parseString[1];
    String indexName = parseString[2];

    Map<String, String> testVarMap = new HashMap<String, String>();

    if (!index.equalsIgnoreCase(KIBANA_INDEX_KEY)) {
      // Add the logstash and wls index name to a Map
      testVarMap.put(index, indexName);
    }

    //There are multiple indexes from Kibana 6.8.0
    String[] healthStatusArr = healthStatus.split(System.getProperty("line.separator"));
    String[] indexStatusArr = indexStatus.split(System.getProperty("line.separator"));
    String[] indexNameArr = indexName.split(System.getProperty("line.separator"));

    for (int i = 0; i < indexStatusArr.length; i++) {
      logger.info("Health status of {0} is {1}", indexNameArr[i],  healthStatusArr[i]);
      logger.info("Index status of {0} is {1}", indexNameArr[i], indexStatusArr[i]);
      // Verify that the health status of index
      assertTrue(healthStatusArr[i].trim().equalsIgnoreCase("yellow")
          || healthStatusArr[i].trim().equalsIgnoreCase("green"), index + " is not ready!");
      // Verify that the index is open for use
      assertTrue(indexStatusArr[i].trim().equalsIgnoreCase("open"), index + " is not open!");
    }

    logger.info("logging exporter {0} is up and running and ready to use!", indexName);

    return testVarMap;
  }

  private static boolean downloadWle() {
    // install WDT if needed
    return Installer.withParams(
        defaultInstallWleParams())
        .download();
  }

  private static boolean downloadSnake() {
    // install SnakeYAML if needed
    return Installer.withParams(
        defaultInstallSnakeParams())
        .download();
  }

  /**
   * Install WebLogic Logging Exporter.
   *
   * @param filter the value of weblogicLoggingExporterFilters to be added to WebLogic Logging Exporter YAML file
   * @param wlsLoggingExporterYamlFileLoc the directory where WebLogic Logging Exporter YAML file stores
   * @return true if WebLogic Logging Exporter is successfully installed, false otherwise.
   */
  public static boolean installWlsLoggingExporter(String filter,
                                                  String wlsLoggingExporterYamlFileLoc) {
    // Copy WebLogic Logging Exporter files to WORK_DIR
    String[] loggingExporterFiles =
        {WLS_LOGGING_EXPORTER_YAML_FILE_NAME, COPY_WLS_LOGGING_EXPORTER_FILE_NAME};

    for (String loggingFile : loggingExporterFiles) {
      Path srcPath = Paths.get(wlsLoggingExporterYamlFileLoc, loggingFile);
      Path destPath = Paths.get(WORK_DIR, loggingFile);
      assertDoesNotThrow(() -> FileUtils.copy(srcPath, destPath),
          String.format("Failed to copy %s to %s", srcPath, destPath));
      logger.info("Copied {0} to {1}}", srcPath, destPath);
    }

    // Add filter to weblogicLoggingExporterFilters in WebLogic Logging Exporter YAML file
    assertDoesNotThrow(() -> addFilterToElkFile(filter),
        "Failed to add WebLogic Logging Exporter filter");

    // Download WebLogic Logging Exporter jar file
    if (!downloadWle()) {
      return false;
    }

    // Download the YAML parser, SnakeYAML
    if (!downloadSnake()) {
      return false;
    }

    return true;
  }

  private static V1Deployment createElasticsearchDeploymentCr(LoggingExporterParams params) {

    String elasticsearchName = params.getElasticsearchName();
    String elasticsearchImage = params.getElasticsearchImage();
    String namespace = params.getLoggingExporterNamespace();
    int elasticsearchHttpPort = params.getElasticsearchHttpPort();
    int elasticsearchHttpsPort = params.getElasticsearchHttpsPort();
    Map labels = new HashMap<String, String>();
    labels.put("app", elasticsearchName);

    // create Elasticsearch deployment CR object
    V1Deployment elasticsearchDeployment = new V1Deployment()
        .apiVersion("apps/v1")
        .kind("Deployment")
        .metadata(new V1ObjectMeta()
            .name(elasticsearchName)
            .namespace(namespace)
            .labels(labels))
        .spec(new V1DeploymentSpec()
            .replicas(1)
            .selector(new V1LabelSelector()
                .matchLabels(labels))
            .template(new V1PodTemplateSpec()
                .metadata(new V1ObjectMeta()
                    .labels(labels))
                .spec(new V1PodSpec()
                    .initContainers(Arrays.asList(new V1Container()
                        .name("set-vm-max-map-count")
                        .image("busybox")
                        .imagePullPolicy("IfNotPresent")
                        .command(Arrays.asList("sysctl", "-w", "vm.max_map_count=262144"))
                        .securityContext(new V1SecurityContext().privileged(true))))
                    .containers(Arrays.asList(new V1Container()
                        .name(elasticsearchName)
                        .image(elasticsearchImage)
                        .addPortsItem(new V1ContainerPort()
                            .containerPort(Integer.valueOf(elasticsearchHttpPort)))
                        .addPortsItem(new V1ContainerPort()
                            .containerPort(Integer.valueOf(elasticsearchHttpsPort)))
                        .addEnvItem(new V1EnvVar()
                            .name("ES_JAVA_OPTS")
                            .value("-Xms1024m -Xmx1024m"))
                        .addEnvItem(new V1EnvVar()
                            .name("discovery.type")
                            .value("single-node")))))));

    return elasticsearchDeployment;
  }

  private static V1Deployment createKibanaDeploymentCr(LoggingExporterParams params) {

    String kibanaName = params.getKibanaName();
    String kibanaImage = params.getKibanaImage();
    String namespace = params.getLoggingExporterNamespace();
    int kibanaContainerPort = params.getKibanaContainerPort();
    Map labels = new HashMap<String, String>();
    labels.put("app", kibanaName);

    // create Kibana deployment CR object
    V1Deployment kibanaDeployment = new V1Deployment()
        .apiVersion("apps/v1")
        .kind("Deployment")
        .metadata(new V1ObjectMeta()
            .name(kibanaName)
            .namespace(namespace)
            .labels(labels))
        .spec(new V1DeploymentSpec()
            .replicas(1)
            .selector(new V1LabelSelector()
                .matchLabels(labels))
            .template(new V1PodTemplateSpec()
                .metadata(new V1ObjectMeta()
                    .labels(labels))
                .spec(new V1PodSpec()
                    .containers(Arrays.asList(new V1Container()
                        .name(kibanaName)
                        .image(kibanaImage)
                        .ports(Arrays.asList(new V1ContainerPort()
                            .containerPort(Integer.valueOf(kibanaContainerPort)))))))));

    return kibanaDeployment;
  }

  private static V1Service createElasticsearchServiceCr(LoggingExporterParams params) {

    String elasticsearchName = params.getElasticsearchName();
    String namespace = params.getLoggingExporterNamespace();
    int elasticsearchHttpPort = params.getElasticsearchHttpPort();
    int elasticsearchHttpsPort = params.getElasticsearchHttpsPort();
    Map labels = new HashMap<String, String>();
    labels.put("app", elasticsearchName);

    // create Elasticsearch service CR object
    V1Service elasticsearchService = new V1Service()
        .apiVersion("v1")
        .metadata(new V1ObjectMeta()
            .name(elasticsearchName)
            .namespace(namespace))
        .spec(new V1ServiceSpec()
            .addPortsItem(new V1ServicePort()
                .name("http")
                .protocol("TCP")
                .port(elasticsearchHttpPort)
                .targetPort(new IntOrString(elasticsearchHttpPort)))
            .addPortsItem(new V1ServicePort()
                .name("https")
                .protocol("TCP")
                .port(elasticsearchHttpsPort)
                .targetPort(new IntOrString(elasticsearchHttpsPort)))
            .selector(labels));

    return elasticsearchService;
  }

  private static V1Service createKibanaServiceCr(LoggingExporterParams params) {

    String kibanaName = params.getKibanaName();
    String namespace = params.getLoggingExporterNamespace();
    String kibanaType = params.getKibanaType();
    int kibanaContainerPort = params.getKibanaContainerPort();
    Map labels = new HashMap<String, String>();
    labels.put("app", kibanaName);

    // create Kibana service CR object
    V1Service elasticsearchService = new V1Service()
        .apiVersion("v1")
        .metadata(new V1ObjectMeta()
            .name(kibanaName)
            .namespace(namespace))
        .spec(new V1ServiceSpec()
            .type(kibanaType)
            .ports(Arrays.asList(new V1ServicePort()
                .port(kibanaContainerPort)))
            .selector(labels));

    return elasticsearchService;
  }

  private static String execLoggingExpStatusCheck(String namespace, String labelSelector, String indexRegex) {
    StringBuffer k8sExecCmdPrefixBuff = new StringBuffer("curl http://");
    String cmd = k8sExecCmdPrefixBuff
        .append(ELASTICSEARCH_HOST)
        .append(":")
        .append(ELASTICSEARCH_HTTP_PORT)
        .append("/_cat/indices/")
        .append(indexRegex).toString();
    logger.info("Command to get logging exporter status line {0}", cmd);

    // get Operator pod name
    String operatorPodName = assertDoesNotThrow(
        () -> getOperatorPodName(OPERATOR_RELEASE_NAME, namespace));
    assertTrue(operatorPodName != null && !operatorPodName.isEmpty(), "Failed to get Operator pad name");

    int i = 0;
    ExecResult statusLine = null;
    while (i < maxIterationsPod) {
      statusLine = assertDoesNotThrow(
          () -> execCommand(namespace, operatorPodName, null, true,
              "/bin/sh", "-c", cmd));
      assertNotNull(statusLine, "curl command returns null");

      logger.info("Status {0} for index {1} ", statusLine.stdout(), indexRegex);
      if (null != statusLine.stdout() && !statusLine.stdout().isEmpty()) {
        break;
      }

      logger.info("logging exporter is not ready Ite ["
          + i
          + "/"
          + maxIterationsPod
          + "], sleeping "
          + maxIterationsPod
          + " seconds more");

      try {
        Thread.sleep(maxIterationsPod * 1000);
      } catch (InterruptedException ex) {
        //ignore
      }

      i++;
    }

    return statusLine.stdout();
  }

  private static void addFilterToElkFile(String filter) throws Exception {
    String filterStr = new StringBuffer()
        .append(System.lineSeparator())
        .append("weblogicLoggingExporterFilters:")
        .append(System.lineSeparator())
        .append("- FilterExpression: NOT(SERVER = '")
        .append(filter)
        .append("')")
        .toString();
    logger.info("Command to add filter {0}", filterStr);

    Files.write(Paths.get(WORK_DIR, WLS_LOGGING_EXPORTER_YAML_FILE_NAME),
        filterStr.getBytes(), StandardOpenOption.APPEND);
  }
}
