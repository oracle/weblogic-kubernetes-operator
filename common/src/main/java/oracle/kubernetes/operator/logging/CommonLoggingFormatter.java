// Copyright (c) 2017, 2022, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.logging;

/** Custom log formatter to format log messages in JSON format. */
public class CommonLoggingFormatter extends LoggingFormatter {

  @Override
  Object getCurrentFiberIfSet() {
    return null;
  }

  @Override
  String getFiber() {
    return "";
  }

  /**
   * Get the domain UID associated with the current log message.
   * Check the fiber that is currently being used to execute the step that initiates the log.
   * If there is no fiber associated with this log, check the ThreadLocal.
   *
   * @return the domain UID or empty string
   */
  @Override
  protected String getDomainUid(Object fiber) {
    return "";
  }

  /**
   * Get the namespace associated with the current log message.
   * Check the fiber that is currently being used to execute the step that initiate the log.
   * If there is no fiber associated with this log, check the ThreadLocal.
   *
   * @return the namespace or empty string
   */
  @Override
  String getNamespace(Object fiber) {
    return "";
  }
}
