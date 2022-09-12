// Copyright (c) 2022, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.weblogic.kubernetes.assertions.impl;

import java.io.IOException;

import io.kubernetes.client.openapi.ApiException;
import io.kubernetes.client.openapi.Configuration;
import io.kubernetes.client.openapi.apis.CustomObjectsApi;
import io.kubernetes.client.util.ClientBuilder;

import static oracle.weblogic.kubernetes.utils.ThreadSafeLogger.getLogger;

public class Cluster {
  static {
    try {
      Configuration.setDefaultApiClient(ClientBuilder.defaultClient());
    } catch (IOException ioex) {
      throw new ExceptionInInitializerError(ioex);
    }
  }

  private static final CustomObjectsApi customObjectsApi = new CustomObjectsApi();

  /**
   * Checks if weblogic.oracle CRD cluster object exists.
   *
   * @param clusterResName cluster resource name
   * @param clusterVersion version value for Kind Cluster
   * @param namespace in which the cluster object exists
   * @return true if cluster object exists otherwise false
   */
  public static boolean doesClusterExist(String clusterResName, String clusterVersion, String namespace) {

    Object clusterObject = null;
    try {
      clusterObject
          = customObjectsApi.getNamespacedCustomObject(
          "weblogic.oracle", clusterVersion, namespace, "clusters", clusterResName);
    } catch (ApiException apex) {
      getLogger().info(apex.getMessage());
    }
    boolean cluster = (clusterObject != null);
    getLogger().info("Cluster Object exists : " + cluster);
    return cluster;
  }
}
