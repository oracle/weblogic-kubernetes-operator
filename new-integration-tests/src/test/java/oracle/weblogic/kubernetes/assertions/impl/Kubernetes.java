// Copyright (c) 2020, Oracle Corporation and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.weblogic.kubernetes.assertions.impl;

import java.util.concurrent.Callable;

import io.kubernetes.client.openapi.ApiClient;
import io.kubernetes.client.openapi.ApiException;
import io.kubernetes.client.openapi.Configuration;
import io.kubernetes.client.openapi.apis.CoreV1Api;
import io.kubernetes.client.openapi.apis.CustomObjectsApi;
import io.kubernetes.client.openapi.models.V1Pod;
import io.kubernetes.client.openapi.models.V1PodList;
import io.kubernetes.client.util.ClientBuilder;
import java.io.IOException;

public class Kubernetes {

  private static ApiClient apiClient = null;
  private static CoreV1Api coreV1Api = null;
  private static CustomObjectsApi customObjectsApi = null;

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

  public static Callable<Boolean> podExists(String podName, String domainUID, String namespace) {
    return () -> {
      return true;
    };
  }

  public static Callable<Boolean> podRunning(String podName, String domainUID, String namespace) throws ApiException {
    V1PodList list = coreV1Api.listPodForAllNamespaces(null, null, null, null, null, null, null, null, null);
    for (V1Pod item : list.getItems()) {
      System.out.println(item.getMetadata().getName());
    }
    return () -> {
      return true;
    };
  }

  public static Callable<Boolean> podTerminating(String podName, String domainUID, String namespace) {
    return () -> {
      return true;
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
