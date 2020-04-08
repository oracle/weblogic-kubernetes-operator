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

import static oracle.weblogic.kubernetes.extensions.LoggedTest.logger;

/**
 * Helper class to build Kubernetes objects.
 */
public class K8sUtils {

  /**
   * Creates a V1PersistentVolume object given a PersistentVolume POJO object
   *
   * @param persistentVolume POJO object of PersistentVolume containing PV configuration data
   * @return V1PersistentVolume object
   * @throws ApiException when Kubernetes objects fails to be created
   */
  public static V1PersistentVolume createPVObject(PersistentVolume persistentVolume) throws ApiException {
    logger.info("creating a persistent volume Kubernetes object");
    // create a Kubernetes V1PersistentVolume object
    V1PersistentVolume v1pv = new V1PersistentVolume();

    // build metadata object
    V1ObjectMeta metadata = new V1ObjectMetaBuilder()
        .withName(persistentVolume.name()) // set PV name
        .withLabels(persistentVolume.labels()) // set PV labels
        .build();

    // build spec object
    V1PersistentVolumeSpec spec = new V1PersistentVolumeSpec();
    // set spec storageclassname and accessModes
    spec.setAccessModes(persistentVolume.accessMode());
    spec.setStorageClassName(persistentVolume.storageClassName());

    v1pv.setMetadata(metadata);
    v1pv.setSpec(spec);
    v1pv.getSpec().setPersistentVolumeReclaimPolicy(persistentVolume.persistentVolumeReclaimPolicy());
    v1pv.getSpec().setVolumeMode(persistentVolume.volumeMode());

    // set storage and path
    Map<String, Quantity> capacity = new HashMap<>();
    Quantity maxClaims = Quantity.fromString(persistentVolume.storage());
    capacity.put("storage", maxClaims);
    spec.setCapacity(capacity);

    V1HostPathVolumeSource hostPath = new V1HostPathVolumeSource();
    hostPath.setPath(persistentVolume.path());
    v1pv.getSpec().setHostPath(hostPath);

    return v1pv;
  }

  /**
   * Creates a V1PersistentVolumeClaim object given a PersistentVolumeClaim POJO object
   *
   * @param persistentVolumeClaim POJO object of PersistentVolumeClaim containing PVC configuration
    data
   * @return V1PersistentVolume object
   * @throws ApiException when Kubernetes objects fails to be created
   */
  public static V1PersistentVolumeClaim createPVCObject(
      PersistentVolumeClaim persistentVolumeClaim) throws ApiException {

    logger.info("creating a persistent volume claim Kubernetes object");

    // PersistentVolumeClaim TestAction create()
    V1PersistentVolumeClaim v1pvc = new V1PersistentVolumeClaim();

    // build metadata object
    V1ObjectMeta metadata = new V1ObjectMetaBuilder()
        .withName(persistentVolumeClaim.name()) // set PVC name
        .withNamespace(persistentVolumeClaim.namespace()) // set PVC namespace
        .withLabels(persistentVolumeClaim.labels()) // set PVC labels
        .build();

    // build spec object
    V1PersistentVolumeClaimSpec spec = new V1PersistentVolumeClaimSpec();
    // set spec storageclassname and accessModes
    spec.setAccessModes(persistentVolumeClaim.accessMode());
    spec.setStorageClassName(persistentVolumeClaim.storageClassName());

    // set the matadata and spec objects
    v1pvc.setMetadata(metadata);
    v1pvc.setSpec(spec);

    // build resource requirements object
    Quantity maxClaims = Quantity.fromString(persistentVolumeClaim.storage());
    Map<String, Quantity> capacity = new HashMap<>();
    capacity.put("storage", maxClaims);
    V1ResourceRequirements resources = new V1ResourceRequirements();
    resources.setRequests(capacity);
    v1pvc.getSpec().setResources(resources);

    return v1pvc;
  }
}
