// Copyright (c) 2021, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.helpers;

import oracle.kubernetes.operator.calls.RetryStrategy;
import oracle.kubernetes.operator.work.NextAction;
import oracle.kubernetes.operator.work.Packet;
import oracle.kubernetes.operator.work.Step;

public abstract class EventRetryStrategyStub implements RetryStrategy {
  @Override
  public NextAction doPotentialRetry(Step conflictStep, Packet packet, int statusCode) {
    NextAction na = new NextAction();
    na.invoke(conflictStep, packet);
    return na;
  }
}
