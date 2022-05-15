// Copyright (c) 2018, 2022, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator;

import oracle.kubernetes.operator.tuning.TuningParameters;

public class DomainPresence {

  private DomainPresence() {
    // no-op
  }

  static int getDomainPresenceFailureRetrySeconds() {
    return TuningParameters.getInstance().getDomainPresenceFailureRetrySeconds();
  }

  /**
   * Returns the maximum number of failures permitted before a retryable operation aborts make-right.
   * This is derived from the "domainPresenceFailureRetryMaxCount" tuning parameter.
   */
  public static int getFailureRetryMaxCount() {
    return TuningParameters.getInstance().getDomainPresenceFailureRetryMaxCount();
  }
}
