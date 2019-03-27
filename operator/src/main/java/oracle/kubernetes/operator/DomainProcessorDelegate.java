// Copyright 2019, Oracle Corporation and/or its affiliates.  All rights reserved.
// Licensed under the Universal Permissive License v 1.0 as shown at
// http://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator;

import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import oracle.kubernetes.operator.helpers.KubernetesVersion;
import oracle.kubernetes.operator.work.FiberGate;
import oracle.kubernetes.operator.work.Step;

public interface DomainProcessorDelegate {
  String getOperatorNamespace();

  PodAwaiterStepFactory getPodAwaiterStepFactory(String namespace);

  boolean isNamespaceRunning(String namespace);

  KubernetesVersion getVersion();

  FiberGate createFiberGate();

  void runSteps(Step firstStep);

  ScheduledFuture<?> scheduleWithFixedDelay(
      Runnable command, long initialDelay, long delay, TimeUnit unit);
}
