// Copyright (c) 2019, 2022, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator;

import javax.annotation.Nonnull;

import oracle.kubernetes.operator.helpers.DomainPresenceInfo;
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

  /**
   * Returns true if a retry of the last make-right on the specified domain presence info may be scheduled.
   * @param domainPresenceInfo a domain presence info
   */
  boolean mayRetry(@Nonnull DomainPresenceInfo domainPresenceInfo);
}
