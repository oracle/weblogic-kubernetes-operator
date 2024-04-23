// Copyright (c) 2017, 2024, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.calls;

import io.kubernetes.client.extended.controller.reconciler.Result;
import io.kubernetes.client.util.generic.KubernetesApiResponse;
import oracle.kubernetes.operator.work.Packet;
import oracle.kubernetes.operator.work.Step;

/** Failed or timed-out call retry strategy. */
public interface RetryStrategy {

  /**
   * Called during {@link ResponseStep#onFailure} to decide if
   * another retry attempt will occur.
   *
   * @param conflictStep Conflict step, or null
   * @param packet       Packet
   * @param callResponse Call response
   * @return Desired next action which should specify retryStep. Return null when call will not be retried.
   */
  Result doPotentialRetry(Step conflictStep, Packet packet, KubernetesApiResponse<?> callResponse);

  /**
   * Called when retry count, or other statistics, should be reset, such as when partial list was
   * returned and new request for next portion of list (continue) is invoked.
   */
  default void reset() {
    // no-op
  }
}
