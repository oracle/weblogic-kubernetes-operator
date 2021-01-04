// Copyright (c) 2018, 2021, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.steps;

import oracle.kubernetes.operator.calls.CallResponse;
import oracle.kubernetes.operator.helpers.CallBuilder;
import oracle.kubernetes.operator.helpers.ResponseStep;
import oracle.kubernetes.operator.work.NextAction;
import oracle.kubernetes.operator.work.Packet;
import oracle.kubernetes.operator.work.Step;

/**
 * A response step which treats a NOT_FOUND status as success with a null result. By default, does
 * nothing on success. Subclasses must override #doSuccess to take action.
 */
public class DefaultResponseStep<T> extends ResponseStep<T> {
  public DefaultResponseStep() {
  }

  public DefaultResponseStep(Step nextStep) {
    super(nextStep);
  }

  public DefaultResponseStep(Step conflictStep, Step nextStep) {
    super(conflictStep, nextStep);
  }

  @Override
  public NextAction onFailure(Packet packet, CallResponse<T> callResponse) {
    return callResponse.getStatusCode() == CallBuilder.NOT_FOUND
        ? onSuccess(packet, callResponse)
        : super.onFailure(packet, callResponse);
  }

  @Override
  public NextAction onSuccess(Packet packet, CallResponse<T> callResponse) {
    return doNext(packet);
  }
}
