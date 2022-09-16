// Copyright (c) 2022, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator;

import java.util.function.Consumer;

import io.kubernetes.client.openapi.models.CoreV1EventList;
import io.kubernetes.client.openapi.models.V1ConfigMapList;
import io.kubernetes.client.openapi.models.V1JobList;
import io.kubernetes.client.openapi.models.V1PodDisruptionBudgetList;
import io.kubernetes.client.openapi.models.V1PodList;
import io.kubernetes.client.openapi.models.V1ServiceList;
import oracle.kubernetes.operator.work.Packet;
import oracle.kubernetes.weblogic.domain.model.ClusterList;
import oracle.kubernetes.weblogic.domain.model.DomainList;

/**
 * A class which describes some processing to be performed on namespaced resources as they are read.
 */
public interface Processors {

  /**
   * Return the processing to be performed on a list of config maps found in Kubernetes. May be null.
   */
  default Consumer<V1ConfigMapList> getConfigMapListProcessing() {
    return null;
  }

  /**
   * Return the processing to be performed on a list of events found in Kubernetes. May be null.
   */
  default Consumer<CoreV1EventList> getEventListProcessing() {
    return null;
  }

  /**
   * Return the processing to be performed on a list of domain events found in Kubernetes. May be null.
   */
  default Consumer<CoreV1EventList> getOperatorEventListProcessing() {
    return null;
  }

  /**
   * Return the processing to be performed on a list of jobs found in Kubernetes. May be null.
   */
  default Consumer<V1JobList> getJobListProcessing() {
    return null;
  }

  /**
   * Return the processing to be performed on a list of pods found in Kubernetes. May be null.
   */
  default Consumer<V1PodList> getPodListProcessing() {
    return null;
  }

  /**
   * Return the processing to be performed on a list of services found in Kubernetes. May be null.
   */
  default Consumer<V1ServiceList> getServiceListProcessing() {
    return null;
  }

  /**
   * Return the processing to be performed on a list of services found in Kubernetes. May be null.
   */
  default Consumer<V1PodDisruptionBudgetList> getPodDisruptionBudgetListProcessing() {
    return null;
  }

  /**
   * Return the processing to be performed on a list of clusters found in Kubernetes. May be null.
   */
  default Consumer<ClusterList> getClusterListProcessing() {
    return null;
  }

  /**
   * Return the processing to be performed on a list of domains found in Kubernetes. May be null.
   */
  default Consumer<DomainList> getDomainListProcessing() {
    return null;
  }

  /**
   * Do any post-processing of intermediate results.
   *
   * @param packet the packet in the fiber
   */
  default void completeProcessing(Packet packet) {
    
  }
}
