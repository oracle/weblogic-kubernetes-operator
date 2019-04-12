// Copyright 2019, Oracle Corporation and/or its affiliates.  All rights reserved.
// Licensed under the Universal Permissive License v 1.0 as shown at
// http://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.logging;

import java.util.ArrayList;
import java.util.List;

/** A LoggingFilter that logs each log message, which are typically message keys, at most once */
public class OncePerMessageLoggingFilter implements LoggingFilter {

  // allow all messages to be logged when filtering is off
  volatile boolean filtering = false;

  List messagesLogged = new ArrayList();

  /**
   * Turn on filtering of log messages and skip logging of messages that have already been logged
   */
  public void setFilteringOn() {
    filtering = true;
  }

  /**
   * Turn off filtering of log messages and allow all log messages to be logged
   *
   * @param resetLogHistory Whether to also clear the history of messages logged so those messages
   *     will be logged again next time filtering is turned on
   */
  public void setFilteringOff(boolean resetLogHistory) {
    filtering = false;
    if (resetLogHistory) {
      resetLogHistory();
    }
  }

  /** Clears the list of history of messages logged and turn off filtering */
  public void resetLogHistory() {
    synchronized (messagesLogged) {
      messagesLogged.clear();
    }
  }

  @Override
  public boolean canLog(String msg) {
    synchronized (messagesLogged) {
      // Do not log if filtering is on and message has already been logged
      if (filtering && messagesLogged.contains(msg)) {
        return false;
      }
      messagesLogged.add(msg);
      return true;
    }
  }
}
