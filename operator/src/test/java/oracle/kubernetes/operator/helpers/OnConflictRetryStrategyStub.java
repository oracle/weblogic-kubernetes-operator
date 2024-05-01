// Copyright (c) 2021, 2024, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.helpers;

import io.kubernetes.client.extended.controller.reconciler.Result;
import io.kubernetes.client.util.generic.KubernetesApiResponse;
import oracle.kubernetes.operator.calls.RetryStrategy;
import oracle.kubernetes.operator.work.Packet;
import oracle.kubernetes.operator.work.Step;

public abstract class OnConflictRetryStrategyStub implements RetryStrategy {
  @Override
  public Result doPotentialRetry(Step conflictStep, Packet packet, KubernetesApiResponse<?> callResponse) {
    return conflictStep.doStepNext(packet);
  }
}
