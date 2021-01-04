// Copyright (c) 2020, 2021, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.weblogic.kubernetes.assertions.impl;

import java.util.List;

import io.kubernetes.client.openapi.ApiException;
import io.kubernetes.client.openapi.models.V1ClusterRole;
import io.kubernetes.client.openapi.models.V1ClusterRoleList;

import static oracle.weblogic.kubernetes.actions.impl.primitive.Kubernetes.listClusterRoles;

public class ClusterRole {

  /**
   * Check whether the specified cluster role exists.
   *
   * @param name name of the cluster role to check
   * @return true if the cluster role exists, false otherwise
   * @throws ApiException if Kubernetes client API call fails
   */
  public static boolean clusterRoleExists(String name) throws ApiException {

    V1ClusterRoleList clusterRoleList = listClusterRoles("");
    List<V1ClusterRole> clusterRoles = clusterRoleList.getItems();
    for (V1ClusterRole clusterRole : clusterRoles) {
      if (clusterRole.getMetadata() != null && clusterRole.getMetadata().getName() != null) {
        if (clusterRole.getMetadata().getName().equals(name)) {
          return true;
        }
      }
    }

    return false;
  }
}
