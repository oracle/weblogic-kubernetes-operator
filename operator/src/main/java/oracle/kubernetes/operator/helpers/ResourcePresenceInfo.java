// Copyright (c) 2022, 2023, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.helpers;


import java.util.Optional;

import io.kubernetes.client.common.KubernetesObject;
import io.kubernetes.client.openapi.models.V1ObjectMeta;
import oracle.kubernetes.operator.work.PacketComponent;

/**
 * Base class for DomainPresenceInfo and ClusterPresenceInfo.
 */
public abstract class ResourcePresenceInfo implements PacketComponent {

  final String namespace;

  /**
   * Create presence for a domain or cluster.
   * @param namespace Namespace
   *
   */
  protected ResourcePresenceInfo(String namespace) {
    this.namespace = namespace;
  }

  /**
   * Gets the namespace.
   *
   * @return Namespace
   */
  public String getNamespace() {
    return namespace;
  }

  public abstract String getResourceName();

  Long getGeneration(KubernetesObject resource) {
    return Optional.ofNullable(resource).map(KubernetesObject::getMetadata).map(V1ObjectMeta::getGeneration).orElse(0L);
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder("ResourcePresenceInfo{");
    sb.append(", namespace=").append(namespace);
    sb.append(", resourceName=").append(getResourceName());
    sb.append("}");

    return sb.toString();
  }
}
