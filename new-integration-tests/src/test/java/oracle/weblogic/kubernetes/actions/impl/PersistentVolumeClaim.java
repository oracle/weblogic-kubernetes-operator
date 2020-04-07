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

    // test code
    oracle.weblogic.domain.PersistentVolumeClaim pvc = new oracle.weblogic.domain.PersistentVolumeClaim();
    pvc.labels().put("weblogic.resourceVersion", "domain-v2");
    pvc.labels().put("weblogic.domainUID", "mydomain");
    pvc.accessMode().add("ReadWriteMany");
    pvc
      .capacity("10Gi")
      .persistentVolumeReclaimPolicy("Recycle")
      .storageClassName("itoperator-domain-2-weblogic-sample-storage-class")
      .name("mypvc")
        .namespace("mypvc-ns");

    // PersistentVolumeClaim TestAction create()
    V1PersistentVolumeClaim v1pvc = new V1PersistentVolumeClaim();

    // build metadata object
    V1ObjectMeta metadata = new V1ObjectMetaBuilder()
        .withName(pvc.name()) // set PVC name
        .withNamespace(pvc.namespace()) // set PVC namespace
        .build();
    // set PVC labels
    metadata.setLabels(pvc.labels());

    // build spec object
    V1PersistentVolumeClaimSpec spec = new V1PersistentVolumeClaimSpec();
    // set spec storageclassname and accessModes
    spec.setStorageClassName(pvc.storageClassName());
    spec.setAccessModes(pvc.accessMode());

    V1ResourceRequirements resources = new V1ResourceRequirements();
    Map<String, Quantity> requests = new HashMap<>();
    resources.setRequests(requests);
    Quantity maxClaims = Quantity.fromString(pvc.capacity());
    requests.put("storage", maxClaims);
    resources.setLimits(requests);

    v1pvc.setMetadata(metadata);
    v1pvc.setSpec(spec);

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
