// Copyright (c) 2024, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.calls;

import oracle.kubernetes.operator.work.Step;

public interface RetryStrategyFactory {

  /**
   * Create a retry strategy.
   *
   * @param maxRetryCount Max retry count
   * @param retryStep Retry step
   * @return Retry strategy instance
   */
  RetryStrategy create(int maxRetryCount, Step retryStep);
}
