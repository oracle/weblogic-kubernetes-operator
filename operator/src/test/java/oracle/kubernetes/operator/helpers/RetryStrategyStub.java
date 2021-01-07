// Copyright (c) 2018, 2021, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.helpers;

import oracle.kubernetes.operator.calls.RetryStrategy;
import oracle.kubernetes.operator.work.NextAction;
import oracle.kubernetes.operator.work.Packet;
import oracle.kubernetes.operator.work.Step;

abstract class RetryStrategyStub implements RetryStrategy {
  private Step conflictStep;

  Step getConflictStep() {
    return conflictStep;
  }

  @Override
  public NextAction doPotentialRetry(Step conflictStep, Packet packet, int statusCode) {
    this.conflictStep = conflictStep;
    return null;
  }
}
