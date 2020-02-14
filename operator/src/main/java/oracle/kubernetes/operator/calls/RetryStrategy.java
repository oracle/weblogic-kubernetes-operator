// Copyright (c) 2017, 2020, Oracle Corporation and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.calls;

import oracle.kubernetes.operator.helpers.ResponseStep;
import oracle.kubernetes.operator.work.NextAction;
import oracle.kubernetes.operator.work.Packet;
import oracle.kubernetes.operator.work.Step;

/** Failed or timed-out call retry strategy. */
public interface RetryStrategy {

  /**
   * Called during {@link ResponseStep#onFailure(Packet, CallResponse)} to decide if
   * another retry attempt will occur.
   *
   * @param conflictStep Conflict step, or null
   * @param packet Packet
   * @param statusCode HTTP response status code; will be 0 for simple timeout
   * @return Desired next action which should specify retryStep. Return null when call will not be
   *     retried.
   */
  NextAction doPotentialRetry(
      Step conflictStep,
      Packet packet,
      int statusCode);

  /**
   * Called when retry count, or other statistics, should be reset, such as when partial list was
   * returned and new request for next portion of list (continue) is invoked.
   */
  void reset();
}
