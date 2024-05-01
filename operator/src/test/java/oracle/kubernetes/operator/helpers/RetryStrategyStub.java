// Copyright (c) 2018, 2024, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.helpers;

import io.kubernetes.client.extended.controller.reconciler.Result;
import io.kubernetes.client.util.generic.KubernetesApiResponse;
import oracle.kubernetes.operator.calls.RetryStrategy;
import oracle.kubernetes.operator.work.Packet;
import oracle.kubernetes.operator.work.Step;

public abstract class RetryStrategyStub implements RetryStrategy {
  private Step conflictStep;
  private int numRetriesLeft = 0;

  Step getConflictStep() {
    return conflictStep;
  }

  @SuppressWarnings("SameParameterValue")
  public void setNumRetriesLeft(int retriesLeft) {
    this.numRetriesLeft = retriesLeft;
  }

  @Override
  public Result doPotentialRetry(Step conflictStep, Packet packet, KubernetesApiResponse<?> callResponse) {
    this.conflictStep = conflictStep;
    if (conflictStep == null || numRetriesLeft-- <= 0) {
      return null;
    }
    return conflictStep.doStepNext(packet);
  }
}
