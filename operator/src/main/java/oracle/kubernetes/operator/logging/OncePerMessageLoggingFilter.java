// Copyright (c) 2019, 2020, Oracle Corporation and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.logging;

import java.util.HashSet;
import java.util.Set;

/** A LoggingFilter that logs each log message, which are typically message keys, at most once. */
public class OncePerMessageLoggingFilter implements LoggingFilter {

  // allow all messages to be logged when filtering is off
  boolean filtering = false;

  final Set messagesLogged = new HashSet();

  /**
   * Turn on or off the filtering of log messages and skip logging of messages that have already
   * been logged.
   *
   * @param value true if filtering should be on, false if filtering should be off
   * @return logging filter
   */
  public synchronized OncePerMessageLoggingFilter setFiltering(boolean value) {
    filtering = value;
    return this;
  }

  /**
   * Clears the list of history of messages logged and turn off filtering.
   *
   * @return logging filter
   */
  public synchronized OncePerMessageLoggingFilter resetLogHistory() {
    messagesLogged.clear();
    return this;
  }

  @Override
  public synchronized boolean canLog(String msg) {
    // Do not log if filtering is on and message has already been logged
    if (filtering && messagesLogged.contains(msg)) {
      return false;
    }
    messagesLogged.add(msg);
    return true;
  }
}
