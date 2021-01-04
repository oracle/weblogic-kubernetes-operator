// Copyright (c) 2020, 2021, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.weblogic.kubernetes.actions.impl;

import io.kubernetes.client.openapi.ApiException;
import io.kubernetes.client.openapi.models.V1ClusterRole;
import oracle.weblogic.kubernetes.actions.impl.primitive.Kubernetes;

public class ClusterRole {

  /**
   * Create a Cluster Role.
   *
   * @param clusterRole V1ClusterRole object containing cluster role configuration data
   * @return true if creation is successful, false otherwise
   * @throws ApiException if Kubernetes client API call fails
   */
  public static boolean createClusterRole(V1ClusterRole clusterRole) throws ApiException {
    return Kubernetes.createClusterRole(clusterRole);
  }

  /**
   * Delete Cluster Role.
   *
   * @param name name of cluster role
   * @return true if deletion is successful, false otherwise
   * @throws ApiException if Kubernetes client API call fails
   */
  public static boolean deleteClusterRole(String name) throws ApiException {
    return Kubernetes.deleteClusterRole(name);
  }

}
