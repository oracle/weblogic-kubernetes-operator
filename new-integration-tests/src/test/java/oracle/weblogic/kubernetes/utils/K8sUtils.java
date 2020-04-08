// Copyright (c) 2020, Oracle Corporation and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.weblogic.kubernetes.utils;

import java.util.HashMap;
import java.util.Map;

import io.kubernetes.client.custom.Quantity;
import io.kubernetes.client.openapi.ApiException;
import io.kubernetes.client.openapi.models.V1HostPathVolumeSource;
import io.kubernetes.client.openapi.models.V1ObjectMeta;
import io.kubernetes.client.openapi.models.V1ObjectMetaBuilder;
import io.kubernetes.client.openapi.models.V1PersistentVolume;
import io.kubernetes.client.openapi.models.V1PersistentVolumeClaim;
import io.kubernetes.client.openapi.models.V1PersistentVolumeClaimSpec;
import io.kubernetes.client.openapi.models.V1PersistentVolumeSpec;
import io.kubernetes.client.openapi.models.V1ResourceRequirements;
import oracle.weblogic.domain.PersistentVolume;
import oracle.weblogic.domain.PersistentVolumeClaim;

public class K8sUtils {

  public static V1PersistentVolume createPVObject(PersistentVolume persistentVolume) throws ApiException {
    // create a Kubernetes V1PersistentVolume object
    V1PersistentVolume v1pv = new V1PersistentVolume();

    // build metadata object
    V1ObjectMeta metadata = new V1ObjectMetaBuilder()
        .withName(persistentVolume.name()) // set PVC name
        .withNamespace(persistentVolume.namespace()) // set PVC namespace
        .build();
    // set PVC labels
    metadata.setLabels(persistentVolume.labels());

    // build spec object
    V1PersistentVolumeSpec spec = new V1PersistentVolumeSpec();
    // set spec storageclassname and accessModes
    spec.setStorageClassName(persistentVolume.storageClassName());
    spec.setAccessModes(persistentVolume.accessMode());

    v1pv.setMetadata(metadata);
    v1pv.setSpec(spec);
    v1pv.getSpec().setPersistentVolumeReclaimPolicy(persistentVolume.persistentVolumeReclaimPolicy());
    v1pv.getSpec().setVolumeMode(persistentVolume.volumeMode());

    // build resource requirements object
    Map<String, Quantity> requests = new HashMap<>();
    Quantity maxClaims = Quantity.fromString(persistentVolume.capacity());
    requests.put("storage", maxClaims);
    spec.setCapacity(requests);
    V1HostPathVolumeSource hostPath = new V1HostPathVolumeSource();
    hostPath.setPath(persistentVolume.path());
    v1pv.getSpec().setHostPath(hostPath);

    return v1pv;
  }

  public static V1PersistentVolumeClaim createPVCObject(
      PersistentVolumeClaim persistentVolumeClaim) throws ApiException {

    // PersistentVolumeClaim TestAction create()
    V1PersistentVolumeClaim v1pvc = new V1PersistentVolumeClaim();

    // build metadata object
    V1ObjectMeta metadata = new V1ObjectMetaBuilder()
        .withName(persistentVolumeClaim.name()) // set PVC name
        .withNamespace(persistentVolumeClaim.namespace()) // set PVC namespace
        .build();
    // set PVC labels
    metadata.setLabels(persistentVolumeClaim.labels());

    // build spec object
    V1PersistentVolumeClaimSpec spec = new V1PersistentVolumeClaimSpec();
    // set spec storageclassname and accessModes
    spec.setStorageClassName(persistentVolumeClaim.storageClassName());
    spec.setAccessModes(persistentVolumeClaim.accessMode());

    // build resource requirements object
    Map<String, Quantity> requests = new HashMap<>();
    Quantity maxClaims = Quantity.fromString(persistentVolumeClaim.capacity());
    requests.put("storage", maxClaims);
    V1ResourceRequirements resources = new V1ResourceRequirements();
    resources.setRequests(requests);

    v1pvc.setMetadata(metadata);
    v1pvc.setSpec(spec);
    v1pvc.getSpec().setResources(resources);

    return v1pvc;
  }
}
