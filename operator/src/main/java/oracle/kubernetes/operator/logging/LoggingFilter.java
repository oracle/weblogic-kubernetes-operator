// Copyright (c) 2019, 2020, Oracle Corporation and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.logging;

/** A filter to control whether a log message should be logged. */
public interface LoggingFilter {

  // Constant for key for storing LoggingFilter object in Packet map
  String LOGGING_FILTER_PACKET_KEY = "loggingFilter";

  /**
   * Checks if the message should be logged according to an optional LoggingFilter.
   *
   * @param loggingFilter LoggingFilter that decides whether the log message should be logged, can
   *     be null
   * @param msg The log message to be loggd
   * @return the canLog() return value from the provided loggingFilter, message, and parameters, or
   *     true if loggingFilter is null
   */
  static boolean canLog(LoggingFilter loggingFilter, String msg) {
    return loggingFilter == null || loggingFilter.canLog(msg);
  }

  /**
   * Checks if the message should be logged.
   *
   * @param msg the message to be logged
   * @return true, if can log
   */
  boolean canLog(String msg);
}
