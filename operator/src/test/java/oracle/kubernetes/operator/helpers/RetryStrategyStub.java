// Copyright 2018, Oracle Corporation and/or its affiliates.  All rights reserved.
// Licensed under the Universal Permissive License v 1.0 as shown at
// http://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.helpers;

import io.kubernetes.client.ApiException;
import java.util.List;
import java.util.Map;
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
  public NextAction doPotentialRetry(
      Step conflictStep,
      Packet packet,
      ApiException e,
      int statusCode,
      Map<String, List<String>> responseHeaders) {
    this.conflictStep = conflictStep;
    return null;
  }
}
