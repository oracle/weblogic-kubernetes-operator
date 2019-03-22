// Copyright 2017, 2018, Oracle Corporation and/or its affiliates.  All rights reserved.
// Licensed under the Universal Permissive License v 1.0 as shown at
// http://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.calls;

import io.kubernetes.client.ApiException;
import java.util.List;
import java.util.Map;
import oracle.kubernetes.operator.helpers.ResponseStep;
import oracle.kubernetes.operator.work.NextAction;
import oracle.kubernetes.operator.work.Packet;
import oracle.kubernetes.operator.work.Step;

/** Failed or timed-out call retry strategy. */
public interface RetryStrategy {
  /**
   * Initialization that provides reference to step that should be invoked on a retry attempt.
   *
   * @param retryStep Retry step
   */
  void setRetryStep(Step retryStep);

  /**
   * Called during {@link ResponseStep#onFailure(Packet, ApiException, int, Map)} to decide if
   * another retry attempt will occur.
   *
   * @param conflictStep Conflict step, or null
   * @param packet Packet
   * @param e ApiException thrown by Kubernetes client; will be null for simple timeout
   * @param statusCode HTTP response status code; will be 0 for simple timeout
   * @param responseHeaders HTTP response headers; will be null for simple timeout
   * @return Desired next action which should specify retryStep. Return null when call will not be
   *     retried.
   */
  NextAction doPotentialRetry(
      Step conflictStep,
      Packet packet,
      ApiException e,
      int statusCode,
      Map<String, List<String>> responseHeaders);

  /**
   * Called when retry count, or other statistics, should be reset, such as when partial list was
   * returned and new request for next portion of list (continue) is invoked.
   */
  void reset();
}
