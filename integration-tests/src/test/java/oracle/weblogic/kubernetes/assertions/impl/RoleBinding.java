// Copyright (c) 2020, 2021, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.weblogic.kubernetes.assertions.impl;

import java.util.List;

import io.kubernetes.client.openapi.ApiException;
import io.kubernetes.client.openapi.models.V1RoleBinding;
import io.kubernetes.client.openapi.models.V1RoleBindingList;

import static oracle.weblogic.kubernetes.actions.impl.primitive.Kubernetes.listNamespacedRoleBinding;

public class RoleBinding {

  /**
   * Check whether the role binding exists in the specified namespace.
   *
   * @param name name of the role binding to check
   * @param namespace the namespace in which to check the role binding existence
   * @return true if the role binding exists, false otherwise
   * @throws ApiException if Kubernetes client API call fails
   */
  public static boolean roleBindingExists(String name, String namespace) throws ApiException {
    V1RoleBindingList roleBindingList = listNamespacedRoleBinding(namespace);
    List<V1RoleBinding> roleBindings = roleBindingList.getItems();

    for (V1RoleBinding roleBinding : roleBindings) {
      if (roleBinding.getMetadata() != null && roleBinding.getMetadata().getName() != null) {
        if (roleBinding.getMetadata().getName().equals(name)) {
          return true;
        }
      }
    }

    return false;
  }

}
