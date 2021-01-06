// Copyright (c) 2017, 2021, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.steps;

import oracle.kubernetes.operator.calls.CallResponse;
import oracle.kubernetes.operator.work.NextAction;
import oracle.kubernetes.operator.work.Packet;
import oracle.kubernetes.operator.work.Step;

/**
 * A response step which treats a NOT_FOUND status as success with a null result. On success with
 * a non-null response, runs a specified new step before continuing the step chain.
 */
public abstract class ActionResponseStep<T> extends DefaultResponseStep<T> {
  protected ActionResponseStep() {
  }

  protected ActionResponseStep(Step next) {
    super(next);
  }

  public abstract Step createSuccessStep(T result, Step next);

  @Override
  public NextAction onSuccess(Packet packet, CallResponse<T> callResponse) {
    return callResponse.getResult() == null
        ? doNext(packet)
        : doNext(createSuccessStep(callResponse.getResult(),
            new ContinueOrNextStep(callResponse, getNext())), packet);
  }

  private class ContinueOrNextStep extends Step {
    private final CallResponse<T> callResponse;

    public ContinueOrNextStep(CallResponse<T> callResponse, Step next) {
      super(next);
      this.callResponse = callResponse;
    }

    @Override
    public NextAction apply(Packet packet) {
      return ActionResponseStep.this.doContinueListOrNext(callResponse, packet);
    }
  }
}
