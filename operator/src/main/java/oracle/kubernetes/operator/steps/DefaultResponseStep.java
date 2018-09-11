// Copyright 2018, Oracle Corporation and/or its affiliates.  All rights reserved.
// Licensed under the Universal Permissive License v 1.0 as shown at
// http://oss.oracle.com/licenses/upl.

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
  DefaultResponseStep() {}

  public DefaultResponseStep(Step nextStep) {
    super(nextStep);
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
