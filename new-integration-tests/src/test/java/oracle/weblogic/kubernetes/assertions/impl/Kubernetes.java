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
import io.kubernetes.client.openapi.models.V1Pod;
import io.kubernetes.client.openapi.models.V1PodList;
import io.kubernetes.client.openapi.models.V1Service;
import io.kubernetes.client.openapi.models.V1ServiceList;
import io.kubernetes.client.util.ClientBuilder;

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
   * Checks if a Operator pod running in a given namespace.
   * The method assumes the operator name to starts with weblogic-operator-
   * and decorated with label weblogic.operatorName:namespace
   * @param namespace in which to check for the pod existence
   * @return true if pod exists and running otherwise false
   * @throws ApiException when there is error in querying the cluster
   */
  public static boolean isOperatorPodRunning(String namespace) throws ApiException {
    boolean status = false;
    String labelSelector = String.format("weblogic.operatorName in (%s)", namespace);
    V1Pod pod = getPod(namespace, labelSelector, "weblogic-operator-");
    if (pod != null) {
      status = pod.getStatus().getPhase().equals(RUNNING);
    } else {
      logger.info("Pod doesn't exist");
    }
    return status;
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
      if (item.getMetadata().getName().startsWith(podName.trim())) {
        logger.info("Pod Name :" + item.getMetadata().getName());
        logger.info("Pod Namespace :" + item.getMetadata().getNamespace());
        logger.info("Pod UID :" + item.getMetadata().getUid());
        logger.info("Pod Status :" + item.getStatus().getPhase());
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

}
