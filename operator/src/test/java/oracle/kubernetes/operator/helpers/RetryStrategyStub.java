// Copyright (c) 2018, 2022, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.helpers;

import oracle.kubernetes.operator.calls.RetryStrategy;
import oracle.kubernetes.operator.work.NextAction;
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
  public NextAction doPotentialRetry(Step conflictStep, Packet packet, int statusCode) {
    this.conflictStep = conflictStep;
    if (conflictStep == null || numRetriesLeft-- <= 0) {
      return null;
    } else {
      NextAction next = new NextAction();
      next.invoke(conflictStep, packet);
      return next;
    }
  }
}
