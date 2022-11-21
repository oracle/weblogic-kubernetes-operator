// Copyright (c) 2022, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.helpers;

import java.util.Optional;

import io.kubernetes.client.openapi.models.V1ObjectMeta;
import oracle.kubernetes.operator.work.Component;
import oracle.kubernetes.operator.work.Packet;
import oracle.kubernetes.weblogic.domain.model.ClusterResource;

/**
 * Operator's mapping between custom resource Domain and runtime details about that domain,
 * including the scan and the Pods and Services for servers.
 */
public class ClusterPresenceInfo extends ResourcePresenceInfo {

  private static final String COMPONENT_KEY = "cpi";
  private final ClusterResource cluster;

  /**
   * Create presence for a domain.
   * @param namespace Namespace
   *
   */
  public ClusterPresenceInfo(String namespace, ClusterResource cluster) {
    super(namespace);
    this.cluster = cluster;
  }

  @Override
  public String getResourceName() {
    return Optional.of(cluster).map(ClusterResource::getMetadata).map(V1ObjectMeta::getName).orElse(null);
  }

  @Override
  public void addToPacket(Packet packet) {
    packet.getComponents().put(COMPONENT_KEY, Component.createFor(this));
  }
}
