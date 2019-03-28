// Copyright 2019, Oracle Corporation and/or its affiliates.  All rights reserved.
// Licensed under the Universal Permissive License v 1.0 as shown at
// http://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator;

import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import oracle.kubernetes.operator.helpers.KubernetesVersion;
import oracle.kubernetes.operator.work.FiberGate;
import oracle.kubernetes.operator.work.Step;

/** A set of underlying services required during domain processing. */
public interface DomainProcessorDelegate {
  /**
   * Returns the namespace associated with the operator itself.
   *
   * @return a namespace string
   */
  String getOperatorNamespace();

  /**
   * Returns a factory that creates a step to wait for a pod in the specified namespace to be ready.
   *
   * @param namespace the namespace for the pod
   * @return a step-creating factory
   */
  PodAwaiterStepFactory getPodAwaiterStepFactory(String namespace);

  /**
   * Returns true if the namespace is running.
   *
   * @param namespace the namespace to check
   * @return the 'running' state of the namespace
   */
  boolean isNamespaceRunning(String namespace);

  /**
   * Returns the version of the Kubernetes environment in which the operator is running
   *
   * @return an object that represents the Kubernetes version
   */
  KubernetesVersion getVersion();

  /**
   * Creates a new FiberGate.
   *
   * @return the created instance
   */
  FiberGate createFiberGate();

  /**
   * Runs a chain of steps
   *
   * @param firstStep the first step to run
   */
  void runSteps(Step firstStep);

  /**
   * Schedules the specified command to run periodically.
   *
   * @param command the command to run
   * @param initialDelay the number of time units to wait before running the command the first time
   * @param delay the number of time units to delay between successive runs
   * @param unit the time unit for the above delays
   * @return a future which indicates completion of the command
   */
  ScheduledFuture<?> scheduleWithFixedDelay(
      Runnable command, long initialDelay, long delay, TimeUnit unit);
}
