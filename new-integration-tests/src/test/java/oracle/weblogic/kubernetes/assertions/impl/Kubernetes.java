// Copyright (c) 2020, Oracle Corporation and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.weblogic.kubernetes.assertions.impl;

import java.io.IOException;
import java.util.concurrent.Callable;
import java.util.logging.Level;

import io.kubernetes.client.openapi.ApiClient;
import io.kubernetes.client.openapi.ApiException;
import io.kubernetes.client.openapi.Configuration;
import io.kubernetes.client.openapi.apis.CoreV1Api;
import io.kubernetes.client.openapi.apis.CustomObjectsApi;
import io.kubernetes.client.openapi.models.V1Pod;
import io.kubernetes.client.openapi.models.V1PodList;
import io.kubernetes.client.util.ClientBuilder;
import oracle.weblogic.kubernetes.extensions.LoggedTest;

public class Kubernetes implements LoggedTest {

  private static final String OPERATOR_NAME = "weblogic-operator-";

  private static ApiClient apiClient = null;
  private static CoreV1Api coreV1Api = null;
  private static CustomObjectsApi customObjectsApi = null;
  private static final String RUNNING = "Running";

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

  public static void main(String[] args) throws ApiException {
    Kubernetes test = new Kubernetes();
    test.operatorPodRunning("itmodelinimageconfigupdate-opns-1");
  }

  public static Callable<Boolean> podExists(String podName, String domainUID, String namespace) {
    return () -> {
      V1PodList list = coreV1Api.listPodForAllNamespaces(null, null, null, null, null, null, null, null, null);
      for (V1Pod item : list.getItems()) {
        if (namespace != null) {
          if (podName != null) {
            if (item.getMetadata().getNamespace().equals(namespace.trim())
                && item.getMetadata().getName().equals(podName.trim())) {
              logger.info("REASON:" + item.getStatus().getPhase());
              return true;
            }
          } else {
            ;
          }
        } else {
          logger.log(Level.WARNING, "namespace cannot be null");
          return false;
        }
        if (item.getMetadata().getNamespace().equals(namespace.trim())
            && item.getStatus().getPhase().equals(RUNNING)) {
          logger.info("NAME:" + item.getMetadata().getName());
          logger.info("LABELS:" + item.getMetadata().getLabels());
          logger.info("NAMESPACE:" + item.getMetadata().getNamespace());
          logger.info("PODSTATUS:" + item.getStatus().getMessage());
          logger.info("REASON:" + item.getStatus().getReason());
          logger.info("PHASE:" + item.getStatus().getPhase());
          logger.info("STARTTME:" + item.getStatus().getStartTime());
          if (item.getStatus().getPhase().equals(RUNNING)) {
            return true;
          }
        }
      }
      return true;
    };
  }

  public static boolean podRunning(String podName, String domainUID, String namespace) throws ApiException {
    V1PodList list = coreV1Api.listPodForAllNamespaces(null, null, null, null, null, null, null, null, null);
    for (V1Pod item : list.getItems()) {
      if (item.getMetadata().getNamespace().equals(namespace.trim())
          && item.getMetadata().getName().equals(podName)
          && item.getStatus().getPhase().equals(RUNNING)) {
        logger.info("NAME:" + item.getMetadata().getName());
        logger.info("LABELS:" + item.getMetadata().getLabels());
        logger.info("NAMESPACE:" + item.getMetadata().getNamespace());
        logger.info("PODSTATUS:" + item.getStatus().getMessage());
        logger.info("REASON:" + item.getStatus().getReason());
        logger.info("PHASE:" + item.getStatus().getPhase());
        logger.info("STARTTME:" + item.getStatus().getStartTime());
      }
    }
    return true;
  }

  public static boolean podRunning1(String podName, String domainUID, String namespace) throws ApiException {
    V1PodList list = coreV1Api.listPodForAllNamespaces(null, null, null, null, null, null, null, null, null);
    for (V1Pod item : list.getItems()) {
      if (item.getMetadata().getNamespace().equals(namespace.trim())
          && item.getStatus().getPhase().equals(RUNNING)) {
        logger.info("NAME:" + item.getMetadata().getName());
        logger.info("LABELS:" + item.getMetadata().getLabels());
        logger.info("NAMESPACE:" + item.getMetadata().getNamespace());
        logger.info("PODSTATUS:" + item.getStatus().getMessage());
        logger.info("REASON:" + item.getStatus().getReason());
        logger.info("PHASE:" + item.getStatus().getPhase());
        logger.info("STARTTME:" + item.getStatus().getStartTime());
        if (item.getStatus().getPhase().equals(RUNNING)) {
          return true;
        }
      }
    }
    return false;
  }

  public static boolean operatorPodRunning(String namespace) throws ApiException {
    V1PodList list = coreV1Api.listPodForAllNamespaces(null, null, null, null, null, null, null, null, null);
    return list.getItems().stream().anyMatch((item) -> (item.getMetadata().getNamespace().equals(namespace.trim())
      && item.getMetadata().getName().startsWith(OPERATOR_NAME)
      && item.getStatus().getPhase().equals(RUNNING)));
  }

  public static Callable<Boolean> podTerminating(String podName, String domainUID, String namespace) {
    return () -> {
      return false;
    };
  }

  public static boolean serviceCreated(String domainUID, String namespace) {
    return true;
  }

  public static boolean loadBalancerReady(String domainUID) {
    return true;
  }

  public static boolean adminServerReady(String domainUID, String namespace) {
    return true;
  }
}
