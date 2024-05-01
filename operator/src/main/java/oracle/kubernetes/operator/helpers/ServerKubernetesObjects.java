// Copyright (c) 2018, 2024, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.helpers;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import io.kubernetes.client.openapi.models.V1Pod;
import io.kubernetes.client.openapi.models.V1Service;

/** Kubernetes pods and services associated with a single WebLogic server. */
class ServerKubernetesObjects {
  private final AtomicReference<V1Pod> pod = new AtomicReference<>(null);
  private final AtomicInteger httpRequestFailureCount = new AtomicInteger(0);
  private final AtomicReference<LastKnownStatus> lastKnownStatus = new AtomicReference<>(null);
  private final AtomicReference<V1Service> service = new AtomicReference<>(null);
  private final AtomicReference<V1Service> externalService = new AtomicReference<>();

  ServerKubernetesObjects() {
  }

  /**
   * The Pod.
   *
   * @return Pod
   */
  AtomicReference<V1Pod> getPod() {
    return pod;
  }

  AtomicInteger getHttpRequestFailureCount() {
    return httpRequestFailureCount;
  }

  /**
   * Managed server status.
   *
   * @return Status
   */
  AtomicReference<LastKnownStatus> getLastKnownStatus() {
    return lastKnownStatus;
  }

  /**
   * The Service.
   *
   * @return Service
   */
  AtomicReference<V1Service> getService() {
    return service;
  }

  AtomicReference<V1Service> getExternalService() {
    return externalService;
  }
}
