// Copyright 2017, Oracle Corporation and/or its affiliates.  All rights reserved.
// Licensed under the Universal Permissive License v 1.0 as shown at
// http://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.logging;

public class LoggingFilter {

  volatile boolean loggingAllowed;

  public void setCanLog(boolean canLog) {
    loggingAllowed = canLog;
  }

  public boolean canLog() {
    return loggingAllowed;
  }

  public static boolean canLog(LoggingFilter loggingFilter) {
    if (loggingFilter == null) {
      return true;
    }
    return loggingFilter.canLog();
  }
}
