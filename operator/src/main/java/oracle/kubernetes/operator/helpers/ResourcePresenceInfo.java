// Copyright (c) 2022, 2025, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.helpers;


import java.util.Optional;

import io.kubernetes.client.common.KubernetesObject;
import io.kubernetes.client.openapi.models.V1ObjectMeta;
import oracle.kubernetes.operator.NamespacedResourceCache;

/**
 * Base class for DomainPresenceInfo and ClusterPresenceInfo.
 */
public abstract class ResourcePresenceInfo {

  final NamespacedResourceCache resourceCache;

  /**
   * Create presence for a domain or cluster.
   * @param resourceCache Resource cache
   *
   */
  protected ResourcePresenceInfo(NamespacedResourceCache resourceCache) {
    this.resourceCache = resourceCache;
  }

  /**
   * Gets the namespace.
   *
   * @return Namespace
   */
  public String getNamespace() {
    return resourceCache.getNamespace();
  }

  public abstract String getResourceName();

  Long getGeneration(KubernetesObject resource) {
    return Optional.ofNullable(resource).map(KubernetesObject::getMetadata).map(V1ObjectMeta::getGeneration).orElse(0L);
  }

  @Override
  public String toString() {
    return "ResourcePresenceInfo{"
        + ", namespace="
        + getNamespace()
        + ", resourceName="
        + getResourceName()
        + "}";
  }
}
