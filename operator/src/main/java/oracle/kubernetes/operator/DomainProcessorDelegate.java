// Copyright (c) 2019, 2022, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator;

import oracle.kubernetes.operator.helpers.DomainPresenceInfo;
import oracle.kubernetes.operator.makeright.MakeRightDomainOperationImpl;
import oracle.kubernetes.operator.work.FiberGate;
import org.jetbrains.annotations.NotNull;

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

  @NotNull
  default MakeRightDomainOperation createMakeRightOperation(MakeRightExecutor executor, DomainPresenceInfo info) {
    return new MakeRightDomainOperationImpl(executor, this, info);
  }
}
