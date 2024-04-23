// Copyright (c) 2017, 2024, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.steps;

import javax.annotation.Nonnull;

import io.kubernetes.client.common.KubernetesType;
import io.kubernetes.client.extended.controller.reconciler.Result;
import io.kubernetes.client.util.generic.KubernetesApiResponse;
import oracle.kubernetes.operator.work.Packet;
import oracle.kubernetes.operator.work.Step;

/**
 * A response step which treats a NOT_FOUND status as success with a null result. On success with
 * a non-null response, runs a specified new step before continuing the step chain.
 */
public abstract class ActionResponseStep<T extends KubernetesType> extends DefaultResponseStep<T> {
  protected ActionResponseStep() {
  }

  protected ActionResponseStep(Step next) {
    super(next);
  }

  public abstract Step createSuccessStep(T result, Step next);

  @Override
  public Result onSuccess(Packet packet, KubernetesApiResponse<T> callResponse) {
    return callResponse.getObject() == null
        ? doNext(packet)
        : doNext(createSuccessStep(callResponse.getObject(),
            new ContinueOrNextStep(callResponse, getNext())), packet);
  }

  private class ContinueOrNextStep extends Step {
    private final KubernetesApiResponse<T> callResponse;

    public ContinueOrNextStep(KubernetesApiResponse<T> callResponse, Step next) {
      super(next);
      this.callResponse = callResponse;
    }

    @Override
    public @Nonnull Result apply(Packet packet) {
      return ActionResponseStep.this.doContinueListOrNext(callResponse, packet);
    }
  }
}
