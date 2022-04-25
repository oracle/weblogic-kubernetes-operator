// Copyright (c) 2018, 2022, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator;

import java.util.Optional;

public class DomainPresence {

  private DomainPresence() {
    // no-op
  }

  private static final int DEFAULT_TIMEOUT_SECONDS = 5;
  private static final int DEFAULT_RETRY_MAX_COUNT = 5;

  static int getDomainPresenceFailureRetrySeconds() {
    return Optional.ofNullable(TuningParameters.getInstance())
        .map(TuningParameters::getMainTuning)
        .map(t -> t.domainPresenceFailureRetrySeconds)
        .orElse(DEFAULT_TIMEOUT_SECONDS);
  }

  /**
   * Returns the maximum number of failures permitted before a retryable operation aborts make-right.
   * This is derived from the "domainPresenceFailureRetryMaxCount" tuning parameter.
   */
  public static int getFailureRetryMaxCount() {
    return Optional.ofNullable(TuningParameters.getInstance())
        .map(TuningParameters::getMainTuning)
        .map(t -> t.domainPresenceFailureRetryMaxCount)
        .orElse(DEFAULT_RETRY_MAX_COUNT);
  }
}
