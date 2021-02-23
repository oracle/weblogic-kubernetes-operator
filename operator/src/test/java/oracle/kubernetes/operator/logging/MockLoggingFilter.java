// Copyright (c) 2019, 2021, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.logging;

public class MockLoggingFilter implements LoggingFilter {

  final boolean returnValue;

  public MockLoggingFilter(boolean returnValue) {
    this.returnValue = returnValue;
  }

  public static MockLoggingFilter createWithReturnValue(boolean returnValue) {
    return new MockLoggingFilter(returnValue);
  }

  @Override
  public boolean canLog(String msg) {
    return returnValue;
  }
}
