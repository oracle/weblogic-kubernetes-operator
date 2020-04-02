// Copyright (c) 2020, Oracle Corporation and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.weblogic.kubernetes.assertions.impl;

import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
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
import oracle.weblogic.kubernetes.extensions.LoggedTest;

public class Kubernetes implements LoggedTest {

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

  public static boolean isPodExists(String namespace, String domainUid, String podName) throws ApiException {
    logger.info("Checking if the pod exists in namespace");
    String labelSelector = null;
    if (domainUid != null) {
      labelSelector = String.format("weblogic.domainUID in (%s)", domainUid);
    }
    V1Pod pod = getPod(namespace, labelSelector, podName);
    return pod != null;
  }

  public static boolean isPodRunning(String namespace, String domainUid, String podName) throws ApiException {
    logger.info("Checking if the pod running in namespace");
    String labelSelector = null;
    if (domainUid != null) {
      labelSelector = String.format("weblogic.domainUID in (%s)", domainUid);
    }
    V1Pod pod = getPod(namespace, labelSelector, podName);
    return pod.getStatus().getPhase().equals(RUNNING);
  }

  public static boolean isPodTerminating(String namespace, String domainUid, String podName) throws ApiException {
    logger.info("Checking if the pod terminating in namespace");
    String labelSelector = null;
    if (domainUid != null) {
      labelSelector = String.format("weblogic.domainUID in (%s)", domainUid);
    }
    V1Pod pod = getPod(namespace, labelSelector, podName);
    return pod.getStatus().getPhase().equals(TERMINATING);
  }

  public static boolean isOperatorPodRunning(String namespace) throws ApiException {
    String labelSelector = String.format("weblogic.operatorName in (%s)", namespace);
    V1Pod pod = getPod(namespace, labelSelector, "weblogic-operator-");
    return pod.getStatus().getPhase().equals(RUNNING);
  }

  public static V1Pod getPod(String namespace, String labelSelector, String podName) throws ApiException {
    V1PodList v1PodList =
        coreV1Api.listNamespacedPod(
            namespace,
            Boolean.FALSE.toString(),
            Boolean.FALSE,
            null,
            null,
            labelSelector,
            null,
            null,
            null,
            Boolean.FALSE);
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

  public static boolean isServiceCreated(String serviceName, HashMap label, String namespace) throws ApiException {
    return getService(serviceName, label, namespace) != null;
  }

  public static V1Service getService(String serviceName, HashMap label, String namespace) throws ApiException {
    String labelSelector = null;
    if (label != null) {
      String key = label.keySet().iterator().next().toString();
      String value = label.get(key).toString();
      labelSelector = String.format("(%s) in (%s)", key, value);
    }
    V1ServiceList v1ServiceList
        = coreV1Api.listServiceForAllNamespaces(
        Boolean.FALSE,
        null,
        null,
        labelSelector,
        null,
        Boolean.FALSE.toString(),
        null,
        null,
        Boolean.FALSE);
    for (V1Service service : v1ServiceList.getItems()) {
      if (service.getMetadata().getName().equals(serviceName.trim())
          && service.getMetadata().getNamespace().equals(namespace.trim())) {
        logger.info("Service Name :" + service.getMetadata().getName());
        logger.info("Service Namespace :" + service.getMetadata().getNamespace());
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





  public static void listPods(String namespace, String labelSelectors) throws ApiException {
    V1PodList v1PodList =
        coreV1Api.listNamespacedPod(
            namespace,
            Boolean.FALSE.toString(),
            Boolean.FALSE,
            null,
            null,
            labelSelectors,
            null,
            null,
            null,
            Boolean.FALSE);
    List<V1Pod> items = v1PodList.getItems();
    logger.info(Arrays.toString(items.toArray()));
  }

  public static void listServices(String namespace, String labelSelectors) throws ApiException {
    V1ServiceList v1ServiceList
        = coreV1Api.listServiceForAllNamespaces(
        Boolean.FALSE,
        null,
        null,
        labelSelectors,
        null,
        Boolean.FALSE.toString(),
        null,
        null,
        Boolean.FALSE);
    List<V1Service> items = v1ServiceList.getItems();
    logger.info(Arrays.toString(items.toArray()));
    for (V1Service service : items) {
      logger.info("Service Name :" + service.getMetadata().getName());
      logger.info("Service Namespace :" + service.getMetadata().getNamespace());
      logger.info("Service ResourceVersion :" + service.getMetadata().getResourceVersion());
      logger.info("Service SelfLink :" + service.getMetadata().getSelfLink());
      logger.info("Service Uid :" + service.getMetadata().getUid());
      logger.info("Service Spec Cluster IP :" + service.getSpec().getClusterIP());
      logger.info("Service Spec getExternalIPs :" + service.getSpec().getExternalIPs());
      logger.info("Service Spec getExternalName :" + service.getSpec().getExternalName());
      logger.info("Service Spec getPorts :" + service.getSpec().getPorts());
      logger.info("Service Spec getType :" + service.getSpec().getType());
      Map<String, String> labels = service.getMetadata().getLabels();
      if (labels != null) {
        for (Map.Entry<String, String> entry : labels.entrySet()) {
          logger.log(Level.INFO, "LABEL KEY: {0} LABEL VALUE: {1}",
              new Object[]{entry.getKey(), entry.getValue()});
        }
      }
    }
  }

  public static void verifyServices() throws ApiException {
    // Verify services
    String labelSelector = String.format("weblogic.operatorName in (%s)",
        "itmodelinimageconfigupdate-opns-1");
    logger.log(Level.INFO, labelSelector);
    V1ServiceList v1ServiceList =
        coreV1Api.listServiceForAllNamespaces(
            Boolean.FALSE,
            null,
            null,
            labelSelector,
            null,
            Boolean.FALSE.toString(),
            null,
            null,
            Boolean.FALSE);
    List<V1Service> items = v1ServiceList.getItems();
    logger.info(Arrays.toString(items.toArray()));
    for (V1Service service : v1ServiceList.getItems()) {
      logger.info("Service getApiVersion :" + service.getApiVersion());
      logger.info("Service getApiVersion :" + service.getApiVersion());
      logger.info("Service getKind :" + service.getKind());
      logger.info("Service getMetadata().getCreationTimestamp :"
          + service.getMetadata().getCreationTimestamp());
      Map<String, String> labels = service.getMetadata().getLabels();
      if (labels != null) {
        for (Map.Entry<String, String> entry : labels.entrySet()) {
          logger.log(Level.INFO, "LABEL KEY: {0} LABEL VALUE: {1}",
              new Object[]{entry.getKey(), entry.getValue()});
        }
      }
      logger.info("Service getMetadata().getName :" + service.getMetadata().getName());
      logger.info("Service getMetadata().getNamespace :" + service.getMetadata().getNamespace());
      logger.info("Service getMetadata().getResourceVersion :" + service.getMetadata().getResourceVersion());
      logger.info("Service getMetadata().getSelfLink :" + service.getMetadata().getSelfLink());
      logger.info("Service getMetadata().getUid :" + service.getMetadata().getUid());
      logger.info("Service Spec Cluster IP :" + service.getSpec().getClusterIP());
      logger.info("Service Spec getExternalIPs :" + service.getSpec().getExternalIPs());
      logger.info("Service Spec getExternalName :" + service.getSpec().getExternalName());
      logger.info("Service Spec getType :" + service.getSpec().getType());

    }
  }

  public static boolean loadBalancerReady(String domainUID) {
    return true;
  }

  public static boolean adminServerReady(String domainUID, String namespace) {
    return true;
  }

}
