// Copyright 2019, Oracle Corporation and/or its affiliates.  All rights reserved.
// Licensed under the Universal Permissive License v 1.0 as shown at
// http://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.logging;

import static org.hamcrest.Matchers.is;
import static org.junit.Assert.*;

import org.junit.Test;

public class LoggingFilterTest {

  @Test
  public void verifyCanLogWithoutLoggingFilterReturnsTrue() {

    LoggingFilter loggingFilter = null;
    assertThat(LoggingFilter.canLog(loggingFilter, "message"), is(true));
  }

  @Test
  public void verifyCanLogReturnsTrueValueFromLoggingFilter() {
    LoggingFilter returnTrueLoggingFilter = MockLoggingFilter.createWithReturnValue(true);
    assertThat(LoggingFilter.canLog(returnTrueLoggingFilter, "message"), is(true));
  }

  @Test
  public void verifyCanLogReturnsFalseValueFromLoggingFilter() {
    LoggingFilter returnFalseLoggingFilter = MockLoggingFilter.createWithReturnValue(false);
    assertThat(LoggingFilter.canLog(returnFalseLoggingFilter, "message"), is(false));
  }
}
