// Copyright (c) 2025, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator;

import java.util.concurrent.ConcurrentMap;

import io.kubernetes.client.common.KubernetesListObject;
import io.kubernetes.client.common.KubernetesObject;
import io.kubernetes.client.openapi.models.V1ConfigMap;
import io.kubernetes.client.openapi.models.V1Job;
import io.kubernetes.client.openapi.models.V1Namespace;
import io.kubernetes.client.openapi.models.V1PersistentVolume;
import io.kubernetes.client.openapi.models.V1PersistentVolumeClaim;
import io.kubernetes.client.openapi.models.V1Pod;
import io.kubernetes.client.openapi.models.V1PodDisruptionBudget;
import io.kubernetes.client.openapi.models.V1Secret;
import io.kubernetes.client.openapi.models.V1Service;
import oracle.kubernetes.weblogic.domain.model.ClusterResource;
import oracle.kubernetes.weblogic.domain.model.DomainResource;

public interface NamespacedResourceCache {
    <X extends KubernetesObject> ConcurrentMap<String, X> lookupByType(Class<X> type);

    <X extends KubernetesListObject, Y extends KubernetesObject> ConcurrentMap<String, Y> lookupByListType(Class<X> type);

    V1Namespace getNamespace();

    ConcurrentMap<String, DomainResource> getDomainResources();

    ConcurrentMap<String, ClusterResource> getClusterResources();

    ConcurrentMap<String, V1Pod> getPodResources();

    ConcurrentMap<String, V1Service> getServiceResources();

    ConcurrentMap<String, V1ConfigMap> getConfigMapResources();

    ConcurrentMap<String, V1Secret> getSecretResources();

    ConcurrentMap<String, V1Job> getJobResources();

    ConcurrentMap<String, V1PersistentVolume> getPersistentVolumeResources();

    ConcurrentMap<String, V1PersistentVolumeClaim> getPersistentVolumeClaimResources();

    ConcurrentMap<String, V1PodDisruptionBudget> getPodDistributionBudgetResources();
}
