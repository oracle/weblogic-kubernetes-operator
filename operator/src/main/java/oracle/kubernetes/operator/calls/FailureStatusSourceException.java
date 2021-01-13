// Copyright (c) 2020, 2021, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.calls;

import oracle.kubernetes.operator.logging.LoggingFacade;
import oracle.kubernetes.operator.logging.LoggingFactory;
import oracle.kubernetes.operator.logging.MessageKeys;

public class FailureStatusSourceException extends Exception {
  private static final LoggingFacade LOGGER = LoggingFactory.getLogger("Operator", "Operator");

  private final FailureStatusSource failureStatusSource;

  public FailureStatusSourceException(FailureStatusSource failureStatusSource) {
    this(failureStatusSource, null);
  }

  public FailureStatusSourceException(FailureStatusSource failureStatusSource, Throwable cause) {
    super(cause);
    this.failureStatusSource = failureStatusSource;
  }

  /**
   * Log the exception.
   */
  public void log() {
    Throwable cause = getCause();
    LOGGER.severe(MessageKeys.CALL_FAILED, failureStatusSource.getMessage(), failureStatusSource.getReason());
    LOGGER.fine(MessageKeys.EXCEPTION, getCause());
  }
}
