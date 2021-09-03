// Copyright (c) 2021, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.weblogic.kubernetes.utils;

public class TimeoutException extends RuntimeException {

  /**
   * <p>Constructor for TimeoutException.</p>
   *
   * @param message A description of why the timeout occurred.
   * @param throwable The cause
   */
  public TimeoutException(String message, Throwable throwable) {
    super(message, throwable);
  }

}
