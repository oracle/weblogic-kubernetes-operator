// Copyright (c) 2020, 2022, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.calls;

import oracle.kubernetes.common.logging.MessageKeys;
import oracle.kubernetes.operator.logging.LoggingFacade;
import oracle.kubernetes.operator.logging.LoggingFactory;

public class UnrecoverableCallException extends Exception {
  private static final LoggingFacade LOGGER = LoggingFactory.getLogger("Operator", "Operator");

  private final FailureStatusSource failureStatusSource;

  public UnrecoverableCallException(FailureStatusSource failureStatusSource) {
    this(failureStatusSource, null);
  }

  public UnrecoverableCallException(FailureStatusSource failureStatusSource, Throwable cause) {
    super(cause);
    this.failureStatusSource = failureStatusSource;
  }

  /**
   * Log the exception.
   */
  public void log() {
    LOGGER.severe(MessageKeys.CALL_FAILED, failureStatusSource.getMessage(), failureStatusSource.getReason());
    LOGGER.fine(MessageKeys.EXCEPTION, getCause());
  }
}
