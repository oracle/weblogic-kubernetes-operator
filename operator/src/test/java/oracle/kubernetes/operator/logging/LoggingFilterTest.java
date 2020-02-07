// Copyright (c) 2019, 2020, Oracle Corporation and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.logging;

import org.junit.Test;

import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThat;

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
