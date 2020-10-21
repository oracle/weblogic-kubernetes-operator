// Copyright (c) 2018, 2020, Oracle Corporation and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator;

import io.kubernetes.client.openapi.models.V1ConfigMap;
import io.kubernetes.client.openapi.models.V1Event;
import io.kubernetes.client.openapi.models.V1Pod;
import io.kubernetes.client.openapi.models.V1Service;
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
   * Handles a watch event for config maps in the managed namespaces.
   * @param item a Kubernetes watch even
   */
  void dispatchConfigMapWatch(Watch.Response<V1ConfigMap> item);

  /**
   * Handles a watch event for events in the managed namespaces.
   * @param item a Kubernetes watch even
   */
  void dispatchEventWatch(Watch.Response<V1Event> item);

  /**
   * If the logging level is high enough, reports on any fibers which may currently be suspended.
   */
  void reportSuspendedFibers();
}
