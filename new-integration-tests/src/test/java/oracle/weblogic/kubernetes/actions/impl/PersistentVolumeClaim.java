// Copyright (c) 2020, Oracle Corporation and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.weblogic.kubernetes.actions.impl;

import java.util.HashMap;
import java.util.Map;

import io.kubernetes.client.custom.Quantity;
import io.kubernetes.client.openapi.ApiException;
import io.kubernetes.client.openapi.models.V1ObjectMeta;
import io.kubernetes.client.openapi.models.V1ObjectMetaBuilder;
import io.kubernetes.client.openapi.models.V1PersistentVolumeClaim;
import io.kubernetes.client.openapi.models.V1PersistentVolumeClaimSpec;
import io.kubernetes.client.openapi.models.V1ResourceRequirements;
import oracle.weblogic.kubernetes.actions.impl.primitive.Kubernetes;

/**
 * TestAction class PersistentVolumeClaim to create and delete a Kubernetes Persistent Volume
 * It takes a oracle.weblogic.domain.PersistentVolumeClaim persistentVolumeClaim POJO object
 * to pass the PersistentVolumeClaim details to the create method
 */
public class PersistentVolumeClaim {

  /**
   * Create a Kubernetes Persistent Volume Claim.
   *
   * @param oracle.weblogic.domain.PersistentVolumeClaim persistentVolumeClaim object containing
   *  persistent volume claim configuration data
   * @return true if successful false otherwise
   * @throws ApiException if Kubernetes client API call fails
   */
  public static boolean create(oracle.weblogic.domain.PersistentVolumeClaim persistentVolumeClaim) throws ApiException {

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

    return Kubernetes.createPvc(v1pvc);
  }

  /**
   * Delete the Kubernetes Persistent Volume Claim.
   *
   * @param name name of the Persistent Volume Claim
   * @param namespace name of the namespace
   * @return true if successful, false otherwise
   * @throws ApiException if Kubernetes client API call fails
   */
  public static boolean delete(String name, String namespace) throws ApiException {
    return Kubernetes.deletePvc(name, namespace);
  }
}
