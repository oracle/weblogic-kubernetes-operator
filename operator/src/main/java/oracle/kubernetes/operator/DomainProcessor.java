// Copyright (c) 2018, 2022, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator;

import java.util.Set;
import java.util.stream.Stream;

import io.kubernetes.client.openapi.models.CoreV1Event;
import io.kubernetes.client.openapi.models.V1ConfigMap;
import io.kubernetes.client.openapi.models.V1Pod;
import io.kubernetes.client.openapi.models.V1PodDisruptionBudget;
import io.kubernetes.client.openapi.models.V1Service;
import io.kubernetes.client.util.Watch;
import io.kubernetes.client.util.Watch.Response;
import oracle.kubernetes.operator.helpers.DomainPresenceInfo;
import oracle.kubernetes.operator.helpers.EventHelper.EventItem;
import oracle.kubernetes.weblogic.domain.model.ClusterResource;
import oracle.kubernetes.weblogic.domain.model.DomainResource;

/**
 * An abstraction for processing a domain and a cluster.
 */
public interface DomainProcessor {

  /**
   * Ensures that the domain is up-to-date. This may involve validation and introspection of the domain itself,
   * changes to Kubernetes resources such as pods and services.
   * @param liveInfo an info object that tracks what is known about the domain
   * @return Make-right operation
   */
  MakeRightDomainOperation createMakeRightOperation(DomainPresenceInfo liveInfo);

  /**
   * Ensures that a cluster event is generated for a cluster resource no matter whether it is referenced by a domain
   * or not.
   *
   * @param clusterEvent the event that needs to be generated
   * @param cluster the cluster resource that the event is associated with
   * @return Make-right operation
   */
  MakeRightClusterOperation createMakeRightOperationForClusterEvent(
      EventItem clusterEvent, ClusterResource cluster);

  /**
   * Handles a watch event for clusters in the managed namespaces.
   * @param item a Kubernetes watch event
   */
  void dispatchClusterWatch(Response<ClusterResource> item);

  /**
   * Handles a watch event for domains in the managed namespaces.
   * @param item a Kubernetes watch event
   */
  void dispatchDomainWatch(Watch.Response<DomainResource> item);

  /**
   * Handles a watch event for pods in the managed namespaces.
   * @param item a Kubernetes watch event
   */
  void dispatchPodWatch(Watch.Response<V1Pod> item);

  /**
   * Handles a watch event for services in the managed namespaces.
   * @param item a Kubernetes watch event
   */
  void dispatchServiceWatch(Watch.Response<V1Service> item);

  /**
   * Handles a watch event for pod disruption budget in the managed namespaces.
   * @param item a Kubernetes watch event
   */
  void dispatchPodDisruptionBudgetWatch(Watch.Response<V1PodDisruptionBudget> item);

  /**
   * Handles a watch event for config maps in the managed namespaces.
   * @param item a Kubernetes watch event
   */
  void dispatchConfigMapWatch(Watch.Response<V1ConfigMap> item);

  /**
   * Handles a watch event for events in the managed namespaces.
   * @param item a Kubernetes watch event
   */
  void dispatchEventWatch(Watch.Response<CoreV1Event> item);

  /**
   * If the logging level is high enough, reports on any fibers which may currently be suspended.
   */
  default void reportSuspendedFibers() {
    // no-op
  }

  /**
   * Finds stranded cached domain presence infos that are not identified by the key set.
   * @param namespace namespace
   * @param domainUids domain UID key set
   * @return stream of cached domain presence infos.
   */
  default Stream<DomainPresenceInfo> findStrandedDomainPresenceInfos(String namespace, Set<String> domainUids) {
    return Stream.empty();
  }
}
