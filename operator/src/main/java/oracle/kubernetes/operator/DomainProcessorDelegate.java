// Copyright (c) 2019, 2023, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator;

import javax.annotation.Nonnull;

import io.kubernetes.client.openapi.models.V1Pod;
import oracle.kubernetes.operator.helpers.ClusterPresenceInfo;
import oracle.kubernetes.operator.helpers.DomainPresenceInfo;
import oracle.kubernetes.operator.makeright.MakeRightClusterOperationImpl;
import oracle.kubernetes.operator.makeright.MakeRightDomainOperationImpl;
import oracle.kubernetes.operator.work.FiberGate;

/** A set of underlying services required during domain processing. */
public interface DomainProcessorDelegate extends CoreDelegate {

  /**
   * Returns a factory that creates a step to wait for a pod in the specified namespace to be ready.
   *
   * @param namespace the namespace for the pod
   * @return a step-creating factory
   */
  PodAwaiterStepFactory getPodAwaiterStepFactory(String namespace);

  /**
   * Returns a factory that creates a step to wait for a pod in the specified namespace to be ready.
   *
   * @param namespace the namespace for the pod
   * @return a step-creating factory
   */
  JobAwaiterStepFactory getJobAwaiterStepFactory(String namespace);

  /**
   * Returns a factory that creates a step to wait for a pvc in the specified namespace to be bound.
   *
   * @return a step-creating factory
   */
  PvcAwaiterStepFactory getPvcAwaiterStepFactory();

  /**
   * Returns true if the namespace is running.
   *
   * @param namespace the namespace to check
   * @return the 'running' state of the namespace
   */
  boolean isNamespaceRunning(String namespace);

  /**
   * Creates a new FiberGate.
   *
   * @return the created instance
   */
  FiberGate createFiberGate();

  @Nonnull
  default MakeRightDomainOperation createMakeRightOperation(MakeRightExecutor executor, DomainPresenceInfo info) {
    return new MakeRightDomainOperationImpl(executor, this, info);
  }

  @Nonnull
  default MakeRightClusterOperation createMakeRightOperation(MakeRightExecutor executor, ClusterPresenceInfo info) {
    return new MakeRightClusterOperationImpl(executor, this, info);
  }

  DomainNamespaces getDomainNamespaces();

  void updateDomainStatus(V1Pod pod, DomainPresenceInfo info);
}
