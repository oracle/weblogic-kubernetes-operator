// Copyright (c) 2018, 2021, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator;

import java.util.Set;
import java.util.stream.Stream;

import io.kubernetes.client.openapi.models.CoreV1Event;
import io.kubernetes.client.openapi.models.V1ConfigMap;
import io.kubernetes.client.openapi.models.V1Pod;
import io.kubernetes.client.openapi.models.V1Service;
import io.kubernetes.client.openapi.models.V1beta1PodDisruptionBudget;
import io.kubernetes.client.util.Watch;
import oracle.kubernetes.operator.helpers.DomainPresenceInfo;
import oracle.kubernetes.weblogic.domain.model.Domain;

/**
 * An abstraction for processing a domain.
 */
public interface DomainProcessor {

  /**
   * Ensures that the domain is up-to-date. This may involve validation and introspection of the domain itself,
   * changes to Kubernetes resources such as pods and services.
   * @param liveInfo an info object that tracks what is know about the domain
   * @return Make-right operation
   */
  MakeRightDomainOperation createMakeRightOperation(DomainPresenceInfo liveInfo);

  /**
   * Handles a watch event for domains in the managed namespaces.
   * @param item a Kubernetes watch even
   */
  void dispatchDomainWatch(Watch.Response<Domain> item);

  /**
   * Handles a watch event for pods in the managed namespaces.
   * @param item a Kubernetes watch even
   */
  void dispatchPodWatch(Watch.Response<V1Pod> item);

  /**
   * Handles a watch event for services in the managed namespaces.
   * @param item a Kubernetes watch even
   */
  void dispatchServiceWatch(Watch.Response<V1Service> item);

  /**
   * Handles a watch event for pod disruption budget in the managed namespaces.
   * @param item a Kubernetes watch event
   */
  void dispatchPodDisruptionBudgetWatch(Watch.Response<V1beta1PodDisruptionBudget> item);

  /**
   * Handles a watch event for config maps in the managed namespaces.
   * @param item a Kubernetes watch even
   */
  void dispatchConfigMapWatch(Watch.Response<V1ConfigMap> item);

  /**
   * Handles a watch event for events in the managed namespaces.
   * @param item a Kubernetes watch even
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
