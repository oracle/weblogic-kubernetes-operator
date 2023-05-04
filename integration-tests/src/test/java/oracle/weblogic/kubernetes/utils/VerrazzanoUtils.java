// Copyright (c) 2023, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.weblogic.kubernetes.utils;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import io.kubernetes.client.openapi.ApiException;
import io.kubernetes.client.openapi.models.V1Namespace;
import io.kubernetes.client.openapi.models.V1ObjectMeta;
import io.kubernetes.client.util.Yaml;
import oracle.verrazzano.weblogic.Component;
import oracle.verrazzano.weblogic.ComponentSpec;
import oracle.verrazzano.weblogic.Workload;
import oracle.weblogic.kubernetes.actions.impl.primitive.Kubernetes;
import oracle.weblogic.kubernetes.logging.LoggingFacade;

import static oracle.weblogic.kubernetes.TestConstants.KUBERNETES_CLI;
import static oracle.weblogic.kubernetes.actions.impl.primitive.Kubernetes.createComponent;
import static oracle.weblogic.kubernetes.actions.impl.primitive.Kubernetes.deleteComponent;
import static oracle.weblogic.kubernetes.actions.impl.primitive.Kubernetes.replaceNamespace;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.testUntil;
import static oracle.weblogic.kubernetes.utils.ExecCommand.exec;
import static oracle.weblogic.kubernetes.utils.ThreadSafeLogger.getLogger;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A utility class for verrazzano tests.
 */
public class VerrazzanoUtils {

  static LoggingFacade logger = getLogger();

  /**
   * set labels to domain namespace for the WKO to manage it through namespace label selection strategy.
   *
   * @param namespaces list of domain namespace to label
   * @throws ApiException throws exception when label cannot be set
   */
  public static void setLabelToNamespace(List<String> namespaces) throws ApiException {
    //add label to domain namespace
    assertDoesNotThrow(() -> TimeUnit.MINUTES.sleep(1));
    Map<String, String> labels = new java.util.HashMap<>();
    labels.put("verrazzano-managed", "true");
    labels.put("istio-injection", "enabled");
    for (String namespace : namespaces) {
      V1Namespace namespaceObject = assertDoesNotThrow(() -> Kubernetes.getNamespace(namespace));
      assertNotNull(namespaceObject, "Can't find namespace with name " + namespace);
      namespaceObject.getMetadata().setLabels(labels);
      assertDoesNotThrow(() -> replaceNamespace(namespaceObject));
    }
  }

  /**
   * Get istio gateway host name for accessing the deployed applications in verrazzano.
   *
   * @param namespace namespace in which gateway is deployed
   * @return istio host name
   */
  public static String getIstioHost(String namespace) {
    String curlCmd = KUBERNETES_CLI + " get gateways.networking.istio.io -n "
        + namespace + " -o jsonpath='{.items[0].spec.servers[0].hosts[0]}'";
    ExecResult result = null;
    logger.info("curl command {0}", curlCmd);
    result = assertDoesNotThrow(() -> exec(curlCmd, true));
    logger.info(String.valueOf(result.exitValue()));
    logger.info(result.stdout());
    logger.info(result.stderr());
    assertEquals(0, result.exitValue(), "Failed to get istio host details");
    return result.stdout();
  }

  /**
   * Get istio loadbalancer address for accessing the deployed applications in verrazzano.
   *
   * @return loadbalancer address
   */
  public static String getLoadbalancerAddress() {
    String curlCmd = KUBERNETES_CLI + " get service -n istio-system "
        + "istio-ingressgateway -o jsonpath='{.status.loadBalancer.ingress[0].ip}'";
    logger.info("curl command {0}", curlCmd);
    ExecResult result = assertDoesNotThrow(() -> exec(curlCmd, true));
    logger.info(String.valueOf(result.exitValue()));
    logger.info(result.stdout());
    logger.info(result.stderr());
    assertEquals(0, result.exitValue(), "Failed to get loadbalancer address");
    return result.stdout();
  }

  /**
   * Access applications runnign in WebLogic pods and verify the returned page has expected message.
   *
   * @param url url to access
   * @param message message to be present in returned page
   * @return true if expected message is in returned page, otherwise false
   */
  public static boolean verifyVzApplicationAccess(String url, String message) {
    String curlCmd = "curl -sk " + url;
    logger.info("curl command {0}", curlCmd);
    ExecResult result = assertDoesNotThrow(() -> exec(curlCmd, true));
    logger.info(String.valueOf(result.exitValue()));
    logger.info(result.stdout());
    logger.info(result.stderr());
    return result.stdout().contains(message);
  }

  /**
   * Create a configmap component in verrazzano.
   *
   * @param componentName name of the component referred in verrazzano application configuration
   * @param configmapName name of the configmap referred inside the WebLogic workload
   * @param namespace namespace in which to create the verrazzano component
   * @param domainUid Uid of the WebLogic domain referring this configmap
   * @param modelFiles list of model files for the configmap
   */
  public static void createVzConfigmapComponent(String componentName, String configmapName,
      String namespace, String domainUid, List<String> modelFiles) {

    Map<String, String> labels = new HashMap<>();
    labels.put("weblogic.domainUID", domainUid);
    assertNotNull(componentName, "ConfigMap component name cannot be null");
    logger.info("Create ConfigMap component {0} that contains model files {1}",
        componentName, modelFiles);
    Map<String, String> data = new HashMap<>();
    for (String modelFile : modelFiles) {
      ConfigMapUtils.addModelFile(data, modelFile);
    }

    Component component = new Component()
        .apiVersion("core.oam.dev/v1alpha2")
        .kind("Component")
        .metadata(new V1ObjectMeta()
            .name(componentName)
            .namespace(namespace))
        .spec(new ComponentSpec()
            .workLoad(new Workload()
                .apiVersion("v1")
                .kind("ConfigMap")
                .metadata(new V1ObjectMeta()
                    .labels(labels)
                    .name(configmapName))
                .data(data)));
    logger.info("Deploying configmap component");
    logger.info(Yaml.dump(component));
    assertDoesNotThrow(() -> createComponent(component));

    testUntil(() -> {
      try {
        return Kubernetes.listComponents(namespace).getItems().stream()
            .anyMatch(comp -> comp.getMetadata().getName().equals(componentName));
      } catch (ApiException ex) {
        logger.warning(ex.getResponseBody());
      }
      return false;
    },
        logger,
        "Checking for " + configmapName + " in namespace " + namespace + " exists");
    assertDoesNotThrow(() -> logger.info(Yaml.dump(Kubernetes.listComponents(namespace))));
  }

  /**
   * Delete a configmap component in verrazzano.
   *
   * @param componentName name of the component referred in verrazzano application configuration
   * @param namespace namespace in which to create the verrazzano component
   * @throws ApiException throws when delete fails
   */
  public static void deleteVzConfigmapComponent(String componentName, String namespace) throws ApiException {
    assertTrue(deleteComponent(componentName, namespace));
    // check configuration for JMS
    testUntil(() -> {
      try {
        return !Kubernetes.listComponents(namespace).getItems().stream()
            .anyMatch(component -> component.getMetadata().getName().equals(componentName));
      } catch (ApiException ex) {
        logger.warning(ex.getResponseBody());
      }
      return false;
    },
        logger,
        "Checking for " + componentName + " in namespace " + namespace + " exists");
    logger.info("Component " + componentName + " deleted");
  }
  
}