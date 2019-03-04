// Copyright 2019, Oracle Corporation and/or its affiliates.  All rights reserved.
// Licensed under the Universal Permissive License v 1.0 as shown at
// http://oss.oracle.com/licenses/upl.

package oracle.kubernetes.utils;

import org.joda.time.DateTime;

/** A wrapper for the system clock that facilitates unit testing of time. */
public abstract class SystemClock {

  private static SystemClock DELEGATE =
      new SystemClock() {
        @Override
        public DateTime getCurrentTime() {
          return DateTime.now();
        }
      };

  /**
   * Returns the current time.
   *
   * @return a time instance
   */
  public static DateTime now() {
    return DELEGATE.getCurrentTime();
  }

  /**
   * Returns the delegate's current time.
   *
   * @return a time instance
   */
  public abstract DateTime getCurrentTime();
}
