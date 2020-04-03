// Copyright (c) 2020, Oracle Corporation and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.weblogic.kubernetes.actions.impl;

import io.kubernetes.client.openapi.ApiException;
import io.kubernetes.client.openapi.models.V1PersistentVolume;
import oracle.weblogic.kubernetes.actions.impl.primitive.Kubernetes;

public class PersistentVolume {

  public static boolean create(String pvYaml) {
    return Kubernetes.create(pvYaml);
  }

  /**
   *
   * @param persistentVolume - V1PersistentVolume object containing Kubernetes persistent volume
   *     configuration data
   * @return true if successful
   * @throws ApiException - missing required configuration data, if Kubernetes request fails or
   *     unsuccessful
   */
  public static boolean create(V1PersistentVolume persistentVolume) throws ApiException {
    return Kubernetes.createPv(persistentVolume);
  }

  /**
   * Delete the Kubernetes Persistent Volume
   *
   * @param persistentVolume - V1PersistentVolume object containing PV configuration data
   * @return true if successful
   * @throws ApiException - missing required configuration data, if Kubernetes request fails or
   *     unsuccessful
   */
  public static boolean delete(V1PersistentVolume persistentVolume) throws ApiException {
    return Kubernetes.deletePv(persistentVolume);
  }
}
