// Copyright (c) 2020, 2024, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.weblogic.kubernetes.actions.impl;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.logging.Level;

import io.kubernetes.client.custom.IntOrString;
import io.kubernetes.client.openapi.models.V1Capabilities;
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
import oracle.weblogic.kubernetes.TestConstants;
import oracle.weblogic.kubernetes.actions.impl.primitive.Installer;
import oracle.weblogic.kubernetes.actions.impl.primitive.Kubernetes;
import oracle.weblogic.kubernetes.assertions.impl.Deployment;
import oracle.weblogic.kubernetes.logging.LoggingFacade;
import oracle.weblogic.kubernetes.utils.ExecResult;

import static oracle.weblogic.kubernetes.TestConstants.BUSYBOX_IMAGE;
import static oracle.weblogic.kubernetes.TestConstants.BUSYBOX_TAG;
import static oracle.weblogic.kubernetes.TestConstants.ELASTICSEARCH_HTTP_PORT;
import static oracle.weblogic.kubernetes.TestConstants.IMAGE_PULL_POLICY;
import static oracle.weblogic.kubernetes.TestConstants.KIBANA_INDEX_KEY;
import static oracle.weblogic.kubernetes.TestConstants.OPERATOR_RELEASE_NAME;
import static oracle.weblogic.kubernetes.actions.TestActions.execCommand;
import static oracle.weblogic.kubernetes.actions.TestActions.getOperatorPodName;
import static oracle.weblogic.kubernetes.actions.impl.primitive.Installer.defaultInstallSnakeParams;
import static oracle.weblogic.kubernetes.actions.impl.primitive.Installer.defaultInstallWleParams;
import static oracle.weblogic.kubernetes.assertions.impl.Kubernetes.isPodReady;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.checkServiceExists;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.testUntil;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.withLongRetryPolicy;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.withStandardRetryPolicy;
import static oracle.weblogic.kubernetes.utils.ThreadSafeLogger.getLogger;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Utility class for ELK Stack and WebLogic Logging Exporter.
 */
public class LoggingExporter {

  private static LoggingFacade logger = getLogger();
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
    Map<String, String> labels = new HashMap<>();
    labels.put("app", elasticsearchName);
    boolean deploymentCreated = false;

    // create Elasticsearch deployment CR
    logger.info("Create Elasticsearch deployment CR for {0} in namespace {1}",
        elasticsearchName, namespace);
    V1Deployment elasticsearchDeployment = createElasticsearchDeploymentCr(params);

    // create Elasticsearch deployment
    logger.info("Create Elasticsearch deployment {0} in namespace {1}", elasticsearchName, namespace);
    try {
      // create Elasticsearch deployment
      logger.info("Create Elasticsearch deployment {0} in namespace {1}", elasticsearchName, namespace);
      withStandardRetryPolicy.untilAsserted(
          () -> assertTrue(Kubernetes.createDeployment(elasticsearchDeployment),
              String.format("Waiting for Elasticsearch %s in namespace %s to be deployed",
                  elasticsearchName, namespace)));
      deploymentCreated = true;
    } catch (Exception ex) {
      ex.printStackTrace();
      if (ex.getMessage().contains("AlreadyExists")) {
        getLogger().log(Level.WARNING, ex.getMessage());;
      } else {
        getLogger().log(Level.SEVERE, ex.getMessage());
        fail("Elastic search deployment failed with unknown reason, see above");
      }
    }
    logger.info("Elasticsearch deploymentCreated: {0}", deploymentCreated);

    logger.info("Check if Elasticsearch deployment {0} is ready in namespace {1}",
        elasticsearchName, namespace);
    testUntil(
        withLongRetryPolicy,
        Deployment.isReady(elasticsearchName, labels, namespace),
        logger,
        "Elasticsearch deployment {0} to be completed in {1} namespace",
        elasticsearchName,
        namespace);

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
    logger.info("Elasticsearch service created {0}:",serviceCreated);

    // check that Elasticsearch service exists in its namespace
    logger.info("Check that Elasticsearch service {0} exists in namespace {1}",
        elasticsearchName, namespace);
    checkServiceExists(elasticsearchName, namespace);

    return deploymentCreated && serviceCreated;
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
    Map<String, String> labels = new HashMap<>();
    labels.put("app", kibanaName);
    boolean deploymentCreated = false;

    // create Kibana deployment CR
    logger.info("Create Kibana deployment CR for {0} in namespace {1}", kibanaName, namespace);
    V1Deployment kibanaDeployment = createKibanaDeploymentCr(params);

    try {
      // create Kibana deployment
      logger.info("Create Kibana deployment {0} in namespace {1}", kibanaName, namespace);
      withStandardRetryPolicy.untilAsserted(
          () -> assertTrue(Kubernetes.createDeployment(kibanaDeployment),
              String.format("Waiting for Kibana %s in namespace %s to be deployed",
                  kibanaName, namespace)));
      deploymentCreated = true;
    } catch (Exception ex) {
      ex.printStackTrace();
      if (ex.getMessage().contains("AlreadyExists")) {
        getLogger().log(Level.WARNING, ex.getMessage());
      } else {
        getLogger().log(Level.SEVERE, ex.getMessage());
        fail("Kibana deployment failed with unknown reason, see above");
      }
    }
    logger.info("Kibana deploymentCreated: {0}", deploymentCreated);

    logger.info("Checking if Kibana deployment is ready {0} completed in namespace {1}",
        kibanaName, namespace);
    testUntil(
        withLongRetryPolicy,
        Deployment.isReady(kibanaName, labels, namespace),
        logger,
        "Kibana deployment {0} to be completed in namespace {1}",
        kibanaName,
        namespace);

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

    return deploymentCreated && serviceCreated;
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
    // Get index status info
    String statusLine =
        execLoggingExpStatusCheck(opNamespace, esNamespace, labelSelector, "*" + index + "*");
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

  private static V1Deployment createElasticsearchDeploymentCr(LoggingExporterParams params) {

    String elasticsearchName = params.getElasticsearchName();
    String elasticsearchImage = params.getElasticsearchImage();
    String namespace = params.getLoggingExporterNamespace();
    int elasticsearchHttpPort = params.getElasticsearchHttpPort();
    int elasticsearchHttpsPort = params.getElasticsearchHttpsPort();
    Map<String, String> labels = new HashMap<>();
    labels.put("app", elasticsearchName);

    List<V1Container> initcontainers = new ArrayList<>();
    if (TestConstants.WLSIMG_BUILDER.equals(TestConstants.WLSIMG_BUILDER_DEFAULT)) {
      V1Container setmaxmap = new V1Container()
          .name("set-vm-max-map-count")
          .image(BUSYBOX_IMAGE + ":" + BUSYBOX_TAG)
          .imagePullPolicy(IMAGE_PULL_POLICY)
          .command(Arrays.asList("sysctl", "-w", "vm.max_map_count=262144"))
          .securityContext(new V1SecurityContext().privileged(true));
      initcontainers.add(setmaxmap);
    }
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
                    .initContainers(initcontainers)
                    .containers(List.of(new V1Container()
                        .name(elasticsearchName)
                        .image(elasticsearchImage)
                        .securityContext(new V1SecurityContext()
                            .capabilities(new V1Capabilities()
                                .add(List.of("SYS_CHROOT"))))
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
    Map<String, String> labels = new HashMap<>();
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
                    .containers(List.of(new V1Container()
                        .name(kibanaName)
                        .image(kibanaImage)
                        .ports(List.of(new V1ContainerPort()
                            .containerPort(Integer.valueOf(kibanaContainerPort)))))))));

    return kibanaDeployment;
  }

  private static V1Service createElasticsearchServiceCr(LoggingExporterParams params) {

    String elasticsearchName = params.getElasticsearchName();
    String namespace = params.getLoggingExporterNamespace();
    int elasticsearchHttpPort = params.getElasticsearchHttpPort();
    int elasticsearchHttpsPort = params.getElasticsearchHttpsPort();
    Map<String, String> labels = new HashMap<>();
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
    Map<String, String> labels = new HashMap<>();
    labels.put("app", kibanaName);

    // create Kibana service CR object
    V1Service elasticsearchService = new V1Service()
        .apiVersion("v1")
        .metadata(new V1ObjectMeta()
            .name(kibanaName)
            .namespace(namespace))
        .spec(new V1ServiceSpec()
            .type(kibanaType)
            .ports(List.of(new V1ServicePort()
                .port(kibanaContainerPort)))
            .selector(labels));

    return elasticsearchService;
  }

  private static String execLoggingExpStatusCheck(String opNamespace, String esNamespace,
      String labelSelector, String indexRegex) {
    String elasticSearchHost = "elasticsearch." + esNamespace + ".svc";
    StringBuffer k8sExecCmdPrefixBuff = new StringBuffer("curl http://");
    String cmd = k8sExecCmdPrefixBuff
        .append(elasticSearchHost)
        .append(":")
        .append(ELASTICSEARCH_HTTP_PORT)
        .append("/_cat/indices/")
        .append(indexRegex).toString();
    logger.info("Command to get logging exporter status line {0}", cmd);

    // get Operator pod name
    String operatorPodName = assertDoesNotThrow(() -> getOperatorPodName(OPERATOR_RELEASE_NAME, opNamespace));
    assertTrue(operatorPodName != null && !operatorPodName.isEmpty(), "Failed to get Operator pad name");

    int i = 0;
    ExecResult statusLine = null;
    while (i < maxIterationsPod) {
      logger.info("Command to exec execLoggingExpStatusCheck: opNamespace: {0}, operatorPodName: {1}, cmd {2} {3}",
          opNamespace, operatorPodName, "/bin/sh -c ", cmd);
      statusLine = assertDoesNotThrow(() -> execCommand(opNamespace, operatorPodName, null, true,
              "/bin/sh", "-c", cmd));
      assertNotNull(statusLine, "curl command returns null");

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
}
