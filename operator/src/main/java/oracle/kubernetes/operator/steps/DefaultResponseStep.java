// Copyright (c) 2018, 2024, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.steps;

import io.kubernetes.client.common.KubernetesType;
import io.kubernetes.client.extended.controller.reconciler.Result;
import io.kubernetes.client.util.generic.KubernetesApiResponse;
import oracle.kubernetes.operator.calls.ResponseStep;
import oracle.kubernetes.operator.work.Packet;
import oracle.kubernetes.operator.work.Step;

import static oracle.kubernetes.operator.KubernetesConstants.HTTP_NOT_FOUND;

/**
 * A response step which treats a NOT_FOUND status as success with a null result. By default, does
 * nothing on success. Subclasses must override #doSuccess to take action.
 */
public class DefaultResponseStep<T extends KubernetesType> extends ResponseStep<T> {
  public DefaultResponseStep() {
  }

  public DefaultResponseStep(Step nextStep) {
    super(nextStep);
  }

  public DefaultResponseStep(Step conflictStep, Step nextStep) {
    super(conflictStep, nextStep);
  }

  @Override
  public Result onFailure(Packet packet, KubernetesApiResponse<T> callResponse) {
    return callResponse.getHttpStatusCode() == HTTP_NOT_FOUND
        ? onSuccess(packet, callResponse)
        : super.onFailure(packet, callResponse);
  }

  @Override
  public Result onSuccess(Packet packet, KubernetesApiResponse<T> callResponse) {
    return doNext(packet);
  }
}
