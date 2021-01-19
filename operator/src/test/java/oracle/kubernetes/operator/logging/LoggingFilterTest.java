// Copyright (c) 2019, 2021, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.logging;

import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

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
