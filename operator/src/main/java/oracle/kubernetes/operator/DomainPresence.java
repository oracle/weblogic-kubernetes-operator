// Copyright 2018, Oracle Corporation and/or its affiliates.  All rights reserved.
// Licensed under the Universal Permissive License v 1.0 as shown at
// http://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator;

import java.util.Optional;

public class DomainPresence {
  private static final int DEFAULT_TIMEOUT_SECONDS = 5;
  private static final int DEFAULT_RETRY_MAX_COUNT = 5;

  static int getDomainPresenceFailureRetrySeconds() {
    return Optional.ofNullable(TuningParameters.getInstance())
        .map(parameters -> parameters.getMainTuning().domainPresenceFailureRetrySeconds)
        .orElse(DEFAULT_TIMEOUT_SECONDS);
  }

  static int getDomainPresenceFailureRetryMaxCount() {
    return Optional.ofNullable(TuningParameters.getInstance())
        .map(parameters -> parameters.getMainTuning().domainPresenceFailureRetryMaxCount)
        .orElse(DEFAULT_RETRY_MAX_COUNT);
  }
}
