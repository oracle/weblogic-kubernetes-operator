// Copyright (c) 2020, 2021, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.weblogic.kubernetes.assertions.impl;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;

import io.kubernetes.client.openapi.ApiException;
import io.kubernetes.client.openapi.models.V1PersistentVolumeClaim;
import io.kubernetes.client.openapi.models.V1PersistentVolumeClaimList;

public class PersistentVolumeClaim {

  /**
   * Check whether persistent volume claims with pvcName exists in the specified namespace.
   *
   * @param pvcName persistent volume claim to check
   * @param namespace the namespace in which the persistent volume claim to be checked
   * @return true if the persistent volume claim exists in the namespace, false otherwise
   */
  public static Callable<Boolean> pvcExists(String pvcName, String namespace) {
    return () -> doesPVCExist(pvcName, namespace);
  }

  /**
   * Check whether persistent volume claims with pvcName does NOT exist in the specified namespace.
   *
   * @param pvcName persistent volume claim to check
   * @param namespace the namespace in which the persistent volume claim to be checked
   * @return true if the persistent volume claim exists in the namespace, false otherwise
   */
  public static Callable<Boolean> pvcNotExist(String pvcName, String namespace) {
    return () -> !doesPVCExist(pvcName, namespace);
  }

  /**
   * Check whether persistent volume claims with pvcName exists in the specified namespace.
   *
   * @param pvcName persistent volume claim to check
   * @param namespace the namespace in which the persistent volume claim to be checked
   * @return true if the persistent volume claim exists in the namespace, false otherwise
   * @throws ApiException throws API exception if Kubernetes api call fails
   */
  public static boolean doesPVCExist(String pvcName, String namespace) throws ApiException {
    List<V1PersistentVolumeClaim> v1PersistentVolumeClaims = new ArrayList<>();

    V1PersistentVolumeClaimList v1PersistentVolumeClaimList = Kubernetes.listPersistentVolumeClaims(namespace);
    if (v1PersistentVolumeClaimList != null) {
      v1PersistentVolumeClaims = v1PersistentVolumeClaimList.getItems();
    }

    for (V1PersistentVolumeClaim v1PersistentVolumeClaim : v1PersistentVolumeClaims) {
      if (v1PersistentVolumeClaim.getMetadata() != null
          && v1PersistentVolumeClaim.getMetadata().getName() != null
          && v1PersistentVolumeClaim.getMetadata().getName().equals(pvcName)) {
        return true;
      }
    }

    return false;
  }
}