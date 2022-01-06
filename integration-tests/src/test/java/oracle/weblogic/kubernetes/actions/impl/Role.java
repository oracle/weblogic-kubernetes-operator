// Copyright (c) 2021, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.weblogic.kubernetes.actions.impl;

import io.kubernetes.client.openapi.ApiException;
import io.kubernetes.client.openapi.models.V1Role;
import oracle.weblogic.kubernetes.actions.impl.primitive.Kubernetes;

public class Role {

  /**
   * Create a Cluster Role.
   *
   * @param namespace name of the namespace
   * @param role V1Role object containing cluster role configuration data
   * @return true if creation is successful, false otherwise
   * @throws ApiException if Kubernetes client API call fails
   */
  public static boolean createNamespacedRole(String namespace, V1Role role) throws ApiException {
    return Kubernetes.createNamespacedRole(namespace, role);
  }

  /**
   * Delete Role.
   *
   * @param namespace name of the namespace
   * @param name name of role
   * @return true if deletion is successful, false otherwise
   * @throws ApiException if Kubernetes client API call fails
   */
  public static boolean deleteNamespacedRole(String namespace, String name) throws ApiException {
    return Kubernetes.deleteNamespacedRole(namespace, name);
  }

}
