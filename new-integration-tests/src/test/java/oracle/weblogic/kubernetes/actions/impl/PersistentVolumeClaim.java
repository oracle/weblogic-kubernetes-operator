// Copyright (c) 2020, Oracle Corporation and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.weblogic.kubernetes.actions.impl;

import io.kubernetes.client.openapi.ApiException;
import io.kubernetes.client.openapi.models.V1PersistentVolumeClaim;
import oracle.weblogic.kubernetes.actions.impl.primitive.Kubernetes;

public class PersistentVolumeClaim {

  /**
   * Create a Kubernetes Persistent Volume Claim using the specified path to a file in yaml format
   *
   * @param pvcYaml the persistent volume claim yaml file
   * @return true on success, false otherwise
   */
  public static boolean create(String pvcYaml) {
    return Kubernetes.create(pvcYaml);
  }

  /**
   * @param persistentVolumeClaim - V1PersistentVolumeClaim object containing Kubernetes
   *     persistent volume claim configuration data
   * @return true if successful
   * @throws ApiException - missing required configuration data, if Kubernetes request fails or
   *     unsuccessful
   */
  public static boolean create(V1PersistentVolumeClaim persistentVolumeClaim) throws ApiException {
    return Kubernetes.createPvc(persistentVolumeClaim);
  }

  /**
   * Delete the Kubernetes Persistent Volume Claim
   *
   * @param persistentVolumeClaim - V1PersistentVolumeClaim object containing PVC configuration
   *     data
   * @return true if successful
   * @throws ApiException - missing required configuration data, if Kubernetes request fails or
   *     unsuccessful
   */
  public static boolean delete(V1PersistentVolumeClaim persistentVolumeClaim)
      throws ApiException {
    return Kubernetes.deletePvc(persistentVolumeClaim);
  }
}
