// Copyright (c) 2020, Oracle Corporation and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.weblogic.kubernetes.actions.impl;

import java.util.ArrayList;
import java.util.List;

import io.kubernetes.client.openapi.ApiException;
import io.kubernetes.client.openapi.models.V1PersistentVolume;
import io.kubernetes.client.openapi.models.V1PersistentVolumeList;
import oracle.weblogic.kubernetes.actions.impl.primitive.Kubernetes;

public class PersistentVolume {

  /**
   * Create a Kubernetes Persistent Volume.
   *
   * @param persistentVolume V1PersistentVolume object containing persistent volume
   *     configuration data
   * @return true if successful, false otherwise
   * @throws ApiException if Kubernetes client API call fails
   */
  public static boolean create(V1PersistentVolume persistentVolume) throws ApiException {
    return Kubernetes.createPv(persistentVolume);
  }

  /**
   * Delete the Kubernetes Persistent Volume.
   *
   * @param name name of the Persistent Volume
   * @return true if successful
   */
  public static boolean delete(String name) {
    return Kubernetes.deletePv(name);
  }

  /**
   * List all persistent volumes.
   *
   * @return list of persistent volume names
   */
  public static List<String> listPersistentVolumes() {
    List<String> pvNames = new ArrayList<>();
    List<V1PersistentVolume> v1PersistentVolumes = new ArrayList<>();

    V1PersistentVolumeList v1PersistentVolumeList =  Kubernetes.listPersistentVolumes();
    if (v1PersistentVolumeList != null) {
      v1PersistentVolumes = v1PersistentVolumeList.getItems();
    }

    for (V1PersistentVolume v1PersistentVolume : v1PersistentVolumes) {
      if (v1PersistentVolume.getMetadata() != null) {
        pvNames.add(v1PersistentVolume.getMetadata().getName());
      }
    }

    return pvNames;
  }
}
