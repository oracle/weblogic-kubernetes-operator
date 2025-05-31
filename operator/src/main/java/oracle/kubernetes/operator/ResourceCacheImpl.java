// Copyright (c) 2025, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Function;

import io.kubernetes.client.common.KubernetesListObject;
import io.kubernetes.client.common.KubernetesObject;
import io.kubernetes.client.openapi.models.V1ConfigMap;
import io.kubernetes.client.openapi.models.V1ConfigMapList;
import io.kubernetes.client.openapi.models.V1Job;
import io.kubernetes.client.openapi.models.V1JobList;
import io.kubernetes.client.openapi.models.V1PersistentVolume;
import io.kubernetes.client.openapi.models.V1PersistentVolumeClaim;
import io.kubernetes.client.openapi.models.V1PersistentVolumeClaimList;
import io.kubernetes.client.openapi.models.V1PersistentVolumeList;
import io.kubernetes.client.openapi.models.V1Pod;
import io.kubernetes.client.openapi.models.V1PodDisruptionBudget;
import io.kubernetes.client.openapi.models.V1PodDisruptionBudgetList;
import io.kubernetes.client.openapi.models.V1PodList;
import io.kubernetes.client.openapi.models.V1Secret;
import io.kubernetes.client.openapi.models.V1SecretList;
import io.kubernetes.client.openapi.models.V1Service;
import io.kubernetes.client.openapi.models.V1ServiceList;
import oracle.kubernetes.weblogic.domain.model.ClusterList;
import oracle.kubernetes.weblogic.domain.model.ClusterResource;
import oracle.kubernetes.weblogic.domain.model.DomainList;
import oracle.kubernetes.weblogic.domain.model.DomainResource;

public class ResourceCacheImpl implements ResourceCache {
  private static final Map<Class<? extends KubernetesObject>,
      Function<ResourceCacheImpl, ConcurrentMap<String, ?>>> RESOURCE_MAP = new HashMap<>();
  private static final Map<Class<? extends KubernetesListObject>,
      Function<ResourceCacheImpl, ConcurrentMap<String, ?>>> RESOURCE_LIST_MAP = new HashMap<>();

  static {
    RESOURCE_MAP.put(DomainResource.class, ResourceCache::getConfigMapResources);
    RESOURCE_MAP.put(ClusterResource.class, ResourceCache::getConfigMapResources);
    RESOURCE_MAP.put(V1Pod.class, ResourceCache::getConfigMapResources);
    RESOURCE_MAP.put(V1Service.class, ResourceCache::getConfigMapResources);
    RESOURCE_MAP.put(V1ConfigMap.class, ResourceCache::getConfigMapResources);
    RESOURCE_MAP.put(V1Secret.class, ResourceCache::getConfigMapResources);
    RESOURCE_MAP.put(V1Job.class, ResourceCache::getConfigMapResources);
    RESOURCE_MAP.put(V1PersistentVolume.class, ResourceCache::getConfigMapResources);
    RESOURCE_MAP.put(V1PersistentVolumeClaim.class, ResourceCache::getConfigMapResources);
    RESOURCE_MAP.put(V1PodDisruptionBudget.class, ResourceCache::getConfigMapResources);

    RESOURCE_LIST_MAP.put(DomainList.class, ResourceCache::getConfigMapResources);
    RESOURCE_LIST_MAP.put(ClusterList.class, ResourceCache::getConfigMapResources);
    RESOURCE_LIST_MAP.put(V1PodList.class, ResourceCache::getConfigMapResources);
    RESOURCE_LIST_MAP.put(V1ServiceList.class, ResourceCache::getConfigMapResources);
    RESOURCE_LIST_MAP.put(V1ConfigMapList.class, ResourceCache::getConfigMapResources);
    RESOURCE_LIST_MAP.put(V1SecretList.class, ResourceCache::getConfigMapResources);
    RESOURCE_LIST_MAP.put(V1JobList.class, ResourceCache::getConfigMapResources);
    RESOURCE_LIST_MAP.put(V1PersistentVolumeList.class, ResourceCache::getConfigMapResources);
    RESOURCE_LIST_MAP.put(V1PersistentVolumeClaimList.class, ResourceCache::getConfigMapResources);
    RESOURCE_LIST_MAP.put(V1PodDisruptionBudgetList.class, ResourceCache::getConfigMapResources);
  }

  /**
   * Lookup resource map based on class type.
   * @param <X> Kubernetes resource class type
   * @param type Type
   * @return Resource map
   */
  @SuppressWarnings("unchecked")
  public <X extends KubernetesObject> ConcurrentMap<String, X> lookupByType(Class<X> type) {
    Function<ResourceCacheImpl, ConcurrentMap<String, ?>> func = RESOURCE_MAP.get(type);
    if (func != null) {
      return (ConcurrentMap<String, X>) func.apply(this);
    }
    return null;
  }

  /**
   * Lookup resource map based on list class type.
   * @param <X> Kubernetes resource list class type
   * @param <Y> Kubernetes resource class type
   * @param type Type
   * @return Resource map
   */
  @SuppressWarnings("unchecked")
  public <X extends KubernetesListObject, Y extends KubernetesObject> ConcurrentMap<String, Y>
      lookupByListType(Class<X> type) {
    Function<ResourceCacheImpl, ConcurrentMap<String, ?>> func = RESOURCE_LIST_MAP.get(type);
    if (func != null) {
      return (ConcurrentMap<String, Y>) func.apply(this);
    }
    return null;
  }

  private final String namespace;
  private final ConcurrentMap<String, NamespacedResourceCache> namespacesResources = new ConcurrentHashMap<>();
  private final ConcurrentMap<String, DomainResource> domainResources = new ConcurrentHashMap<>();
  private final ConcurrentMap<String, ClusterResource> clusterResources = new ConcurrentHashMap<>();
  private final ConcurrentMap<String, V1Pod> podResources = new ConcurrentHashMap<>();
  private final ConcurrentMap<String, V1Service> serviceResources = new ConcurrentHashMap<>();
  private final ConcurrentMap<String, V1ConfigMap> configMapResources = new ConcurrentHashMap<>();
  private final ConcurrentMap<String, V1Secret> secretResources = new ConcurrentHashMap<>();
  private final ConcurrentMap<String, V1Job> jobResources = new ConcurrentHashMap<>();
  private final ConcurrentMap<String, V1PersistentVolume> pvResources = new ConcurrentHashMap<>();
  private final ConcurrentMap<String, V1PersistentVolumeClaim> pvcResources = new ConcurrentHashMap<>();
  private final ConcurrentMap<String, V1PodDisruptionBudget> pdbResources = new ConcurrentHashMap<>();

  ResourceCacheImpl() {
    this(null);
  }

  ResourceCacheImpl(String namespace) {
    this.namespace = namespace;
  }

  @Override
  public ConcurrentMap<String, NamespacedResourceCache> getNamespaces() {
    return namespacesResources;
  }

  @Override
  public NamespacedResourceCache findNamespace(String namespace) {
    return namespacesResources.computeIfAbsent(namespace, ResourceCacheImpl::new);
  }

  @Override
  public String getNamespace() {
    return namespace;
  }

  @Override
  public ConcurrentMap<String, DomainResource> getDomainResources() {
    return domainResources;
  }

  @Override
  public ConcurrentMap<String, ClusterResource> getClusterResources() {
    return clusterResources;
  }

  @Override
  public ConcurrentMap<String, V1Pod> getPodResources() {
    return podResources;
  }

  @Override
  public ConcurrentMap<String, V1Service> getServiceResources() {
    return serviceResources;
  }

  @Override
  public ConcurrentMap<String, V1ConfigMap> getConfigMapResources() {
    return configMapResources;
  }

  @Override
  public ConcurrentMap<String, V1Secret> getSecretResources() {
    return secretResources;
  }

  @Override
  public ConcurrentMap<String, V1Job> getJobResources() {
    return jobResources;
  }

  @Override
  public ConcurrentMap<String, V1PersistentVolume> getPersistentVolumeResources() {
    return pvResources;
  }

  @Override
  public ConcurrentMap<String, V1PersistentVolumeClaim> getPersistentVolumeClaimResources() {
    return pvcResources;
  }

  @Override
  public ConcurrentMap<String, V1PodDisruptionBudget> getPodDistributionBudgetResources() {
    return pdbResources;
  }
}
