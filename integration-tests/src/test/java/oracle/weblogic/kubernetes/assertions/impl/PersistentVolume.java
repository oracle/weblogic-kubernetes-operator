// Copyright (c) 2020, 2021, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.weblogic.kubernetes.assertions.impl;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;

import io.kubernetes.client.openapi.models.V1PersistentVolume;
import io.kubernetes.client.openapi.models.V1PersistentVolumeList;

public class PersistentVolume {

  /**
   * Check whether persistent volume with pvName exists.
   *
   * @param pvName persistent volume to check
   * @param labelSelector String containing the labels the PV is decorated with
   * @return true if the persistent volume exists, false otherwise
   */
  public static Callable<Boolean> pvExists(String pvName, String labelSelector) {
    return () -> {

      List<V1PersistentVolume> v1PersistentVolumes = new ArrayList<>();

      V1PersistentVolumeList v1PersistentVolumeList = Kubernetes.listPersistentVolumes(labelSelector);
      if (v1PersistentVolumeList != null) {
        v1PersistentVolumes = v1PersistentVolumeList.getItems();
      }

      for (V1PersistentVolume v1PersistentVolume : v1PersistentVolumes) {
        if (v1PersistentVolume.getMetadata() != null) {
          if (v1PersistentVolume.getMetadata().getName() != null) {
            if (v1PersistentVolume.getMetadata().getName().equals(pvName)) {
              return true;
            }
          }
        }
      }

      return false;
    };
  }
}
