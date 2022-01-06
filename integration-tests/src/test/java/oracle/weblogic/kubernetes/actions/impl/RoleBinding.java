// Copyright (c) 2021, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.weblogic.kubernetes.actions.impl;

import io.kubernetes.client.openapi.ApiException;
import io.kubernetes.client.openapi.models.V1RoleBinding;
import oracle.weblogic.kubernetes.actions.impl.primitive.Kubernetes;

public class RoleBinding {

  /**
   * Create a Role Binding.
   *
   * @param namespace name of the namespace
   * @param roleBinding V1ClusterRoleBinding object containing role binding configuration data
   * @return true if successful
   * @throws ApiException if Kubernetes client API call fails
   */
  public static boolean createNamespacedRoleBinding(String namespace, V1RoleBinding roleBinding) throws ApiException {
    return Kubernetes.createNamespacedRoleBinding(namespace, roleBinding);
  }

  /**
   * Delete Role Binding.
   *
   * @param namespace name of the namespace
   * @param name name of cluster role binding
   * @return true if successful, false otherwise
   */
  public static boolean deleteNamespacedRoleBinding(String namespace, String name) throws ApiException {
    return Kubernetes.deleteNamespacedRoleBinding(namespace, name);
  }

}
