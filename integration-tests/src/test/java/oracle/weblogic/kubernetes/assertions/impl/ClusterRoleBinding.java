// Copyright (c) 2020, 2021, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.weblogic.kubernetes.assertions.impl;

import java.util.List;

import io.kubernetes.client.openapi.ApiException;
import io.kubernetes.client.openapi.models.V1ClusterRoleBinding;
import io.kubernetes.client.openapi.models.V1ClusterRoleBindingList;

import static oracle.weblogic.kubernetes.actions.impl.primitive.Kubernetes.listClusterRoleBindings;

public class ClusterRoleBinding {

  /**
   * Check whether the specified cluster role binding exists.
   *
   * @param name the name of cluster role binding to check
   * @return true if the cluster role binding exists, false otherwise
   */
  public static boolean clusterRoleBindingExists(String name) throws ApiException {
    V1ClusterRoleBindingList clusterRoleBindingList = listClusterRoleBindings("");
    List<V1ClusterRoleBinding> clusterRoleBindings = clusterRoleBindingList.getItems();

    for (V1ClusterRoleBinding clusterRoleBinding : clusterRoleBindings) {
      if (clusterRoleBinding.getMetadata() != null && clusterRoleBinding.getMetadata().getName() != null) {
        if (clusterRoleBinding.getMetadata().getName().equals(name)) {
          return true;
        }
      }
    }

    return false;
  }
}