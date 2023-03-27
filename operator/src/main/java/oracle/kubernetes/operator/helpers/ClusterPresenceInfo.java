// Copyright (c) 2022, 2023, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.helpers;

import java.util.Optional;

import io.kubernetes.client.openapi.models.V1ObjectMeta;
import oracle.kubernetes.operator.MakeRightClusterOperation;
import oracle.kubernetes.operator.work.Component;
import oracle.kubernetes.operator.work.Packet;
import oracle.kubernetes.weblogic.domain.model.ClusterResource;

/**
 * Operator's mapping between custom resource Cluster and runtime details about that cluster.
 */
public class ClusterPresenceInfo extends ResourcePresenceInfo {

  private static final String COMPONENT_KEY = "cpi";
  private ClusterResource cluster;

  /**
   * Create presence for a cluster resource.
   *
   * @param cluster the cluster resource that the to be created presence info contains
   */
  public ClusterPresenceInfo(ClusterResource cluster) {
    super(cluster.getNamespace());
    this.cluster = cluster;
  }

  @Override
  public String getResourceName() {
    return Optional.of(cluster).map(ClusterResource::getMetadata).map(V1ObjectMeta::getName).orElse(null);
  }

  public ClusterResource getCluster() {
    return cluster;
  }

  public void setCluster(ClusterResource cluster) {
    this.cluster = cluster;
  }

  @Override
  public void addToPacket(Packet packet) {
    packet.getComponents().put(COMPONENT_KEY, Component.createFor(this));
  }

  /**
   * Returns true if the cluster make-right operation was triggered by a domain event and the reported domain
   * is older than the value already cached. That indicates that the event is old and should be ignored.
   * @param operation the make-right operation.
   * @param cachedInfo the cached domain presence info.
   */
  public boolean isFromOutOfDateEvent(MakeRightClusterOperation operation, ClusterPresenceInfo cachedInfo) {
    return operation.hasEventData() && !isNewerThan(cachedInfo);
  }

  private boolean isNewerThan(ClusterPresenceInfo cachedInfo) {
    return getCluster() == null
        || !KubernetesUtils.isFirstNewer(cachedInfo.getCluster().getMetadata(), getCluster().getMetadata());
  }

  /**
   * Returns true if the cluster in this presence info has a later generation
   * than the passed-in cached info.
   * @param cachedInfo another presence info against which to compare this one.
   */
  public boolean isClusterGenerationChanged(ClusterPresenceInfo cachedInfo) {
    return getGeneration(getCluster()).compareTo(getGeneration(cachedInfo.getCluster())) > 0;
  }

}
