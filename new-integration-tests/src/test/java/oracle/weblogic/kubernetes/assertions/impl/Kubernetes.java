// Copyright (c) 2020, Oracle Corporation and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.weblogic.kubernetes.assertions.impl;

import java.io.IOException;
import java.util.Map;
import java.util.logging.Level;

import io.kubernetes.client.openapi.ApiClient;
import io.kubernetes.client.openapi.ApiException;
import io.kubernetes.client.openapi.Configuration;
import io.kubernetes.client.openapi.apis.CoreV1Api;
import io.kubernetes.client.openapi.apis.CustomObjectsApi;
import io.kubernetes.client.openapi.models.V1Pod;
import io.kubernetes.client.openapi.models.V1PodList;
import io.kubernetes.client.openapi.models.V1ServiceAccount;
import io.kubernetes.client.openapi.models.V1ServiceAccountList;
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

  public static boolean podExists(String podName, String namespace) throws ApiException {
    V1PodList list = coreV1Api.listPodForAllNamespaces(null, null, null, null, null, null, null, null, null);
    for (V1Pod item : list.getItems()) {
      if (item.getMetadata().getName().startsWith(podName.trim())
          && item.getMetadata().getNamespace().equals(namespace.trim())) {
        logPodStatus(item);
        return true;
      }
    }
    return false;
  }

  public static boolean podRunning(String podName, String namespace) throws ApiException {
    V1PodList list = coreV1Api.listPodForAllNamespaces(null, null, null, null, null, null, null, null, null);
    for (V1Pod item : list.getItems()) {
      if (item.getMetadata().getNamespace().equals(namespace.trim())
          && item.getMetadata().getName().equals(podName)
          && item.getStatus().getPhase().equals(RUNNING)) {
        logPodStatus(item);
        return true;
      }
    }
    return false;
  }

  public static boolean operatorPodRunning(String namespace) throws ApiException {
    V1PodList list = coreV1Api.listPodForAllNamespaces(null, null, null, null, null, null, null, null, null);
    for (V1Pod item : list.getItems()) {
      if (item.getMetadata().getNamespace().equals(namespace.trim())
          && item.getMetadata().getName().startsWith(OPERATOR_NAME)
          && item.getStatus().getPhase().equals(RUNNING)) {
        logPodStatus(item);
        return true;
      }
    }
    return false;
  }

  private static void logPodStatus(V1Pod pod) {
    logger.log(Level.INFO, "NAME:{0}", pod.getMetadata().getName());
    logger.log(Level.INFO, "LABELS:{0}", pod.getMetadata().getLabels());
    logger.log(Level.INFO, "NAMESPACE:{0}", pod.getMetadata().getNamespace());
    logger.log(Level.INFO, "PODSTATUS:{0}", pod.getStatus().getMessage());
    logger.log(Level.INFO, "REASON:{0}", pod.getStatus().getReason());
    logger.log(Level.INFO, "PHASE:{0}", pod.getStatus().getPhase());
    logger.log(Level.INFO, "STARTTME:{0}", pod.getStatus().getStartTime());
  }

  public static boolean podTerminating(String podName, String namespace) throws ApiException {
    V1PodList list = coreV1Api.listPodForAllNamespaces(null, null, null, null, null, null, null, null, null);
    for (V1Pod item : list.getItems()) {
      if (item.getMetadata().getNamespace().equals(namespace.trim())
          && item.getMetadata().getName().startsWith(OPERATOR_NAME)
          && item.getStatus().getPhase().equals(TERMINATING)) {
        logPodStatus(item);
        return true;
      }
    }
    return false;
  }

  public static boolean serviceCreated(String domainUID, String namespace) throws ApiException {
    V1ServiceAccountList listServiceAccountForAllNamespaces =
        coreV1Api.listServiceAccountForAllNamespaces(null, null, null, null, null, null, null, null, null);
    for (V1ServiceAccount sa : listServiceAccountForAllNamespaces.getItems()) {
      if (sa.getMetadata().getNamespace().equals(namespace)) {
        Map<String, String> labels = sa.getMetadata().getLabels();
        for (Map.Entry<String, String> entry : labels.entrySet()) {
          logger.log(Level.INFO, "LABEL KEY: {0} LABEL VALUE: {1}", new Object[]{entry.getKey(), entry.getValue()});
        }
        logger.log(Level.INFO, "Service Account Name :", sa.getMetadata().getName());
        logger.log(Level.INFO, "Service Account Namespace :", sa.getMetadata().getNamespace());
        logger.log(Level.INFO, "Service Account Uid :", sa.getMetadata().getUid());
      }
    }
    return true;
  }

  public static boolean loadBalancerReady(String domainUID) {
    return true;
  }

  public static boolean adminServerReady(String domainUID, String namespace) {
    return true;
  }
}
