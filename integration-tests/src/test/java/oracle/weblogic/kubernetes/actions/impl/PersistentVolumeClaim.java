// Copyright (c) 2020, 2021, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.weblogic.kubernetes.actions.impl;

import io.kubernetes.client.openapi.ApiException;
import io.kubernetes.client.openapi.models.V1PersistentVolumeClaim;
import oracle.weblogic.kubernetes.actions.impl.primitive.Kubernetes;

public class PersistentVolumeClaim {

  /**
   * Create a Kubernetes Persistent Volume Claim.
   *
   * @param persistentVolumeClaim V1PersistentVolumeClaim object containing Kubernetes
   *     persistent volume claim configuration data
   * @return true if successful
   * @throws ApiException if Kubernetes client API call fails
   */
  public static boolean create(V1PersistentVolumeClaim persistentVolumeClaim) throws ApiException {
    return Kubernetes.createPvc(persistentVolumeClaim);
  }

  /**
   * Delete the Kubernetes Persistent Volume Claim.
   *
   * @param name name of the Persistent Volume Claim
   * @param namespace name of the namespace
   * @return true if successful, false otherwise
   */
  public static boolean delete(String name, String namespace) {
    return Kubernetes.deletePvc(name, namespace);
  }
}
