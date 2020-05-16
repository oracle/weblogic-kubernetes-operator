// Copyright (c) 2020, Oracle Corporation and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.weblogic.kubernetes.assertions.impl;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;

import io.kubernetes.client.openapi.ApiClient;
import io.kubernetes.client.openapi.ApiException;
import io.kubernetes.client.openapi.Configuration;
import io.kubernetes.client.openapi.apis.CoreV1Api;
import io.kubernetes.client.openapi.apis.CustomObjectsApi;
import io.kubernetes.client.openapi.models.V1Container;
import io.kubernetes.client.openapi.models.V1Pod;
import io.kubernetes.client.openapi.models.V1PodCondition;
import io.kubernetes.client.openapi.models.V1PodList;
import io.kubernetes.client.openapi.models.V1Service;
import io.kubernetes.client.openapi.models.V1ServiceList;
import io.kubernetes.client.util.ClientBuilder;
import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;

import static oracle.weblogic.kubernetes.extensions.LoggedTest.logger;

public class Kubernetes {

  private static final String OPERATOR_NAME = "weblogic-operator-";

  private static ApiClient apiClient = null;
  private static CoreV1Api coreV1Api = null;
  private static CustomObjectsApi customObjectsApi = null;
  private static final String RUNNING = "Running";
  private static final String TERMINATING = "Terminating";

  static {
    try {
      Configuration.setDefaultApiClient(ClientBuilder.defaultClient());
      apiClient = Configuration.getDefaultApiClient();
      coreV1Api = new CoreV1Api();
      customObjectsApi = new CustomObjectsApi();
    } catch (IOException ioex) {
      throw new ExceptionInInitializerError(ioex);
    }
  }

  /**
   * Checks if a pod exists in a given namespace in any state.
   * @param namespace in which to check for the pod existence
   * @param domainUid the label the pod is decorated with
   * @param podName name of the pod to check for
   * @return true if pod exists otherwise false
   * @throws ApiException when there is error in querying the cluster
   */
  public static boolean doesPodExist(String namespace, String domainUid, String podName) throws ApiException {
    boolean podExist = false;
    String labelSelector = null;
    if (domainUid != null) {
      labelSelector = String.format("weblogic.domainUID in (%s)", domainUid);
    }
    V1Pod pod = getPod(namespace, labelSelector, podName);
    if (pod != null) {
      podExist = true;
    }
    return podExist;
  }

  /**
   * Checks if a pod does not exist in a given namespace in any state.
   * @param namespace in which to check for the pod existence
   * @param domainUid the label the pod is decorated with
   * @param podName name of the pod to check for
   * @return true if pod does not exists otherwise false
   * @throws ApiException when there is error in querying the cluster
   */
  public static boolean doesPodNotExist(String namespace, String domainUid, String podName) throws ApiException {
    boolean podDeleted = false;
    String labelSelector = null;
    if (domainUid != null) {
      labelSelector = String.format("weblogic.domainUID in (%s)", domainUid);
    }
    V1Pod pod = getPod(namespace, labelSelector, podName);
    if (pod == null) {
      podDeleted = true;
    } else {
      logger.info("[" + pod.getMetadata().getName() + "] still exist");
    }
    return podDeleted;
  }

  /**
   * Checks if a pod exists in a given namespace and in Running state.
   * @param namespace in which to check for the pod running
   * @param domainUid the label the pod is decorated with
   * @param podName name of the pod to check for
   * @return true if pod exists and running otherwise false
   * @throws ApiException when there is error in querying the cluster
   */
  public static boolean isPodRunning(String namespace, String domainUid, String podName) throws ApiException {
    boolean status = false;
    String labelSelector = null;
    if (domainUid != null) {
      labelSelector = String.format("weblogic.domainUID in (%s)", domainUid);
    }
    V1Pod pod = getPod(namespace, labelSelector, podName);
    if (pod != null) {
      status = pod.getStatus().getPhase().equals(RUNNING);
    } else {
      logger.info("Pod doesn't exist");
    }
    return status;
  }

  /**
   * Checks if a pod is ready in a given namespace.
   *
   * @param namespace in which to check if the pod is ready
   * @param domainUid the label the pod is decorated with
   * @param podName name of the pod to check for
   * @return true if the pod is in the ready condition, false otherwise
   * @throws ApiException if Kubernetes client API call fails
   */
  public static boolean isPodReady(String namespace, String domainUid, String podName) throws ApiException {
    boolean status = false;
    String labelSelector = null;
    if (domainUid != null) {
      labelSelector = String.format("weblogic.domainUID in (%s)", domainUid);
    }

    V1Pod pod = getPod(namespace, labelSelector, podName);
    if (pod != null) {

      // get the podCondition with the 'Ready' type field
      V1PodCondition v1PodReadyCondition = pod.getStatus().getConditions().stream()
          .filter(v1PodCondition -> "Ready".equals(v1PodCondition.getType()))
          .findAny()
          .orElse(null);

      if (v1PodReadyCondition != null) {
        status = v1PodReadyCondition.getStatus().equalsIgnoreCase("true");
      }
    } else {
      logger.info("Pod doesn't exist");
    }
    return status;
  }

  /**
   * Checks if a pod exists in a given namespace and in Terminating state.
   * @param namespace in which to check for the pod
   * @param domainUid the label the pod is decorated with
   * @param podName name of the pod to check for
   * @return true if pod is in Terminating state otherwise false
   * @throws ApiException when there is error in querying the cluster
   */
  public static boolean isPodTerminating(String namespace, String domainUid, String podName) throws ApiException {
    boolean status = false;
    logger.info("Checking if the pod terminating in namespace");
    String labelSelector = null;
    if (domainUid != null) {
      labelSelector = String.format("weblogic.domainUID in (%s)", domainUid);
    }
    V1Pod pod = getPod(namespace, labelSelector, podName);
    if (pod != null) {
      status = pod.getStatus().getPhase().equals(TERMINATING);
    } else {
      logger.info("Pod doesn't exist");
    }
    return status;
  }

  /**
   * Checks if a WebLogic server pod has been patched with an expected image.
   *
   * @param namespace Kubernetes namespace in which the pod is running
   * @param domainUid label that the pod is decorated with
   * @param podName name of the WebLogic server pod
   * @param containerName name of the container
   * @param image name of the image to check for
   * @return true if pod's image has been patched
   * @throws ApiException when there is an error in querying the Kubernetes cluster
   */
  public static boolean podImagePatched(
      String namespace,
      String domainUid,
      String podName,
      String containerName,
      String image
  ) throws ApiException {
    boolean podPatched = false;
    String labelSelector = null;
    if (domainUid != null) {
      labelSelector = String.format("weblogic.domainUID in (%s)", domainUid);
    }
    V1Pod pod = getPod(namespace, labelSelector, podName);
    if (pod != null && pod.getSpec() != null) {
      List<V1Container> containers = pod.getSpec().getContainers();
      for (V1Container container : containers) {
        // look for the container
        if (container.getName().equals(containerName)
            && (container.getImage().equals(image))) {
          podPatched = true;
        }
      }
    }
    return podPatched;
  }

  /**
   * Checks if an operator pod is running in a given namespace.
   * The method assumes the operator name to starts with weblogic-operator-
   * and decorated with label weblogic.operatorName:namespace
   * @param namespace in which to check for the pod existence
   * @return true if pod exists and running otherwise false
   * @throws ApiException when there is error in querying the cluster
   */
  public static boolean isOperatorPodReady(String namespace) throws ApiException {
    boolean status = false;
    String labelSelector = String.format("weblogic.operatorName in (%s)", namespace);
    V1Pod pod = getPod(namespace, labelSelector, "weblogic-operator-");
    if (pod != null) {
      // get the podCondition with the 'Ready' type field
      V1PodCondition v1PodReadyCondition = pod.getStatus().getConditions().stream()
          .filter(v1PodCondition -> "Ready".equals(v1PodCondition.getType()))
          .findAny()
          .orElse(null);

      if (v1PodReadyCondition != null) {
        status = v1PodReadyCondition.getStatus().equalsIgnoreCase("true");
      }
    } else {
      logger.info("Pod doesn't exist");
    }
    return status;
  }

  /**
   * Checks if a NGINX pod is running in the specified namespace.
   * The method assumes that the NGINX pod name contains "nginx-ingress-controller".
   *
   * @param namespace in which to check if the NGINX pod is running
   * @return true if the pod is running, otherwise false
   * @throws ApiException if Kubernetes client API call fails
   */
  public static boolean isNginxPodRunning(String namespace) throws ApiException {

    return isPodRunning(namespace, null, "nginx-ingress-controller");
  }

  /**
   * Check whether the NGINX pod is ready in the specified namespace.
   * The method assumes that the NGINX pod name starts with "nginx-ingress-controller".
   *
   * @param namespace in which to check if the NGINX pod is ready
   * @return true if the pod is in the ready state, false otherwise
   * @throws ApiException if Kubernetes client API call fails
   */
  public static boolean isNginxPodReady(String namespace) throws ApiException {

    return isPodReady(namespace, null, "nginx-ingress-controller");
  }

  /**
   * Returns the V1Pod object given the following parameters.
   * @param namespace in which to check for the pod existence
   * @param labelSelector in the format "weblogic.domainUID in (%s)"
   * @param podName name of the pod to return
   * @return V1Pod object if found otherwise null
   * @throws ApiException when there is error in querying the cluster
   */
  public static V1Pod getPod(String namespace, String labelSelector, String podName) throws ApiException {
    V1PodList v1PodList =
        coreV1Api.listNamespacedPod(
            namespace, // namespace in which to look for the pods.
            Boolean.FALSE.toString(), // // pretty print output.
            Boolean.FALSE, // allowWatchBookmarks requests watch events with type "BOOKMARK".
            null, // continue to query when there is more results to return.
            null, // selector to restrict the list of returned objects by their fields
            labelSelector, // selector to restrict the list of returned objects by their labels.
            null, // maximum number of responses to return for a list call.
            null, // shows changes that occur after that particular version of a resource.
            null, // Timeout for the list/watch call.
            Boolean.FALSE // Watch for changes to the described resources.
        );
    for (V1Pod item : v1PodList.getItems()) {
      if (item.getMetadata().getName().contains(podName.trim())) {
        logger.info("Pod Name: " + item.getMetadata().getName());
        logger.info("Pod Namespace: " + item.getMetadata().getNamespace());
        logger.info("Pod UID: " + item.getMetadata().getUid());
        logger.info("Pod Status: " + item.getStatus().getPhase());
        return item;
      }
    }
    return null;
  }

  /**
   * Checks if a Kubernetes service object exists in a given namespace.
   * @param serviceName name of the service to check for
   * @param label the key value pair with which the service is decorated with
   * @param namespace the namespace in which to check for the service
   * @return true if the service is found otherwise false
   * @throws ApiException when there is error in querying the cluster
   */
  public static boolean doesServiceExist(
      String serviceName, Map<String, String> label, String namespace)
      throws ApiException {
    boolean exist = false;
    V1Service service = getService(serviceName, label, namespace);
    if (service != null) {
      exist = true;
    }
    return exist;
  }

  /**
   * Get V1Service object for the given service name, label and namespace.
   * @param serviceName name of the service to look for
   * @param label the key value pair with which the service is decorated with
   * @param namespace the namespace in which to check for the service
   * @return V1Service object if found otherwise null
   * @throws ApiException when there is error in querying the cluster
   */
  public static V1Service getService(
      String serviceName, Map<String, String> label, String namespace)
      throws ApiException {
    String labelSelector = null;
    if (label != null) {
      String key = label.keySet().iterator().next().toString();
      String value = label.get(key).toString();
      labelSelector = String.format("%s in (%s)", key, value);
      logger.info(labelSelector);
    }
    V1ServiceList v1ServiceList
        = coreV1Api.listServiceForAllNamespaces(
        Boolean.FALSE, // allowWatchBookmarks requests watch events with type "BOOKMARK".
        null, // continue to query when there is more results to return.
        null, // selector to restrict the list of returned objects by their fields
        labelSelector, // selector to restrict the list of returned objects by their labels.
        null, // maximum number of responses to return for a list call.
        Boolean.FALSE.toString(), // pretty print output.
        null, // shows changes that occur after that particular version of a resource.
        null, // Timeout for the list/watch call.
        Boolean.FALSE // Watch for changes to the described resources.
    );
    for (V1Service service : v1ServiceList.getItems()) {
      if (service.getMetadata().getName().equals(serviceName.trim())
          && service.getMetadata().getNamespace().equals(namespace.trim())) {
        logger.info("Service Name : " + service.getMetadata().getName());
        logger.info("Service Namespace : " + service.getMetadata().getNamespace());
        Map<String, String> labels = service.getMetadata().getLabels();
        if (labels != null) {
          for (Map.Entry<String, String> entry : labels.entrySet()) {
            logger.log(Level.INFO, "Label Key: {0} Label Value: {1}",
                new Object[]{entry.getKey(), entry.getValue()});
          }
        }
        return service;
      }
    }
    return null;
  }

  /**
   * A utility method to list all pods in given namespace and a label
   * This method can be used as diagnostic tool to get the details of pods.
   * @param namespace in which to list all pods
   * @param labelSelectors with which the pods are decorated
   * @throws ApiException when there is error in querying the cluster
   */
  public static void listPods(String namespace, String labelSelectors) throws ApiException {
    V1PodList v1PodList =
        coreV1Api.listNamespacedPod(
            namespace, // namespace in which to look for the pods.
            Boolean.FALSE.toString(), // pretty print output.
            Boolean.FALSE, // allowWatchBookmarks requests watch events with type "BOOKMARK".
            null, // continue to query when there is more results to return.
            null, // selector to restrict the list of returned objects by their fields
            labelSelectors, // selector to restrict the list of returned objects by their labels.
            null, // maximum number of responses to return for a list call.
            null, // shows changes that occur after that particular version of a resource.
            null, // Timeout for the list/watch call.
            Boolean.FALSE // Watch for changes to the described resources.
        );
    List<V1Pod> items = v1PodList.getItems();
    logger.info(Arrays.toString(items.toArray()));
  }

  /**
   * A utillity method to list all services in a given namespace.
   * This method can be used as diagnostic tool to get the details of services.
   * @param namespace in which to list all services
   * @param labelSelectors  with which the services are decorated
   * @throws ApiException when there is error in querying the cluster
   */
  public static void listServices(String namespace, String labelSelectors) throws ApiException {
    V1ServiceList v1ServiceList
        = coreV1Api.listServiceForAllNamespaces(
        Boolean.FALSE, // allowWatchBookmarks requests watch events with type "BOOKMARK".
        null, // continue to query when there is more results to return.
        null, // selector to restrict the list of returned objects by their fields
        labelSelectors, // selector to restrict the list of returned objects by their labels.
        null, // maximum number of responses to return for a list call.
        Boolean.FALSE.toString(), // pretty print output.
        null, // shows changes that occur after that particular version of a resource.
        null, // Timeout for the list/watch call.
        Boolean.FALSE // Watch for changes to the described resources.
        );
    List<V1Service> items = v1ServiceList.getItems();
    logger.info(Arrays.toString(items.toArray()));
    for (V1Service service : items) {
      logger.info("Service Name : " + service.getMetadata().getName());
      logger.info("Service Namespace : " + service.getMetadata().getNamespace());
      logger.info("Service ResourceVersion : " + service.getMetadata().getResourceVersion());
      logger.info("Service SelfLink : " + service.getMetadata().getSelfLink());
      logger.info("Service Uid :" + service.getMetadata().getUid());
      logger.info("Service Spec Cluster IP : " + service.getSpec().getClusterIP());
      logger.info("Service Spec getExternalIPs : " + service.getSpec().getExternalIPs());
      logger.info("Service Spec getExternalName : " + service.getSpec().getExternalName());
      logger.info("Service Spec getPorts : " + service.getSpec().getPorts());
      logger.info("Service Spec getType : " + service.getSpec().getType());
      Map<String, String> labels = service.getMetadata().getLabels();
      if (labels != null) {
        for (Map.Entry<String, String> entry : labels.entrySet()) {
          logger.log(Level.INFO, "LABEL KEY: {0} LABEL VALUE: {1}",
              new Object[]{entry.getKey(), entry.getValue()});
        }
      }
    }
  }

  public static boolean loadBalancerReady(String domainUid) {
    return true;
  }

  public static boolean adminServerReady(String domainUid, String namespace) {
    return true;
  }

  /**
   * Check if a pod is restarted based on podCreationTimestamp.
   *
   * @param podName the name of the pod to check for
   * @param domainUid the label the pod is decorated with
   * @param namespace in which the pod is running
   * @param timestamp the initial podCreationTimestamp
   * @return true if the pod new timestamp is not equal to initial PodCreationTimestamp otherwise false
   * @throws ApiException when query fails
   */
  public static boolean isPodRestarted(
      String podName, String domainUid,
      String namespace, String timestamp) throws ApiException {
    boolean podRestarted = false;
    String labelSelector = null;
    if (domainUid != null) {
      labelSelector = String.format("weblogic.domainUID in (%s)", domainUid);
    }
    V1Pod pod = getPod(namespace, labelSelector, podName);
    if (pod == null) {
      podRestarted = false;
    } else {
      DateTimeFormatter dtf = DateTimeFormat.forPattern("HHmmss");
      String newTimestamp = dtf.print(pod.getMetadata().getCreationTimestamp());
      if (newTimestamp == null) {
        logger.info("getCreationTimestamp() returns NULL");
        return false;
      }
      logger.info("OldPodCreationTimestamp [{0}]", timestamp);
      logger.info("NewPodCreationTimestamp returns [{0}]", newTimestamp);
      if (Long.parseLong(newTimestamp) == Long.parseLong(timestamp)) {
        podRestarted = false;
      } else {
        podRestarted = true;
      }
    }
    return podRestarted;
  }
}
