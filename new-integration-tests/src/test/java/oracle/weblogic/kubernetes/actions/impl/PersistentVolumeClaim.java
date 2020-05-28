// Copyright (c) 2020, Oracle Corporation and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.weblogic.kubernetes.actions.impl;

import java.util.ArrayList;
import java.util.List;

import io.kubernetes.client.openapi.ApiException;
import io.kubernetes.client.openapi.models.V1PersistentVolumeClaim;
import io.kubernetes.client.openapi.models.V1PersistentVolumeClaimList;
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

  /**
   * List all persistent volume claims in the specified namespace.
   *
   * @param namespace the namespace in which the persistent volume claims exist
   * @return list of persistent volume claim names
   */
  public static List<String> listPersistentVolumeClaims(String namespace) {
    List<String> pvcNames = new ArrayList<>();
    List<V1PersistentVolumeClaim> v1PersistentVolumeClaims = new ArrayList<>();

    V1PersistentVolumeClaimList v1PersistentVolumeClaimList = Kubernetes.listPersistentVolumeClaims(namespace);
    if (v1PersistentVolumeClaimList != null) {
      v1PersistentVolumeClaims = v1PersistentVolumeClaimList.getItems();
    }

    for (V1PersistentVolumeClaim v1PersistentVolumeClaim : v1PersistentVolumeClaims) {
      if (v1PersistentVolumeClaim.getMetadata() != null) {
        pvcNames.add(v1PersistentVolumeClaim.getMetadata().getName());
      }
    }

    return pvcNames;
  }
}
