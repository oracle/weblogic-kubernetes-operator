// Copyright (c) 2019, 2022, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.logging;

import oracle.kubernetes.common.logging.LoggingFilter;
import oracle.kubernetes.common.logging.MockLoggingFilter;
import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

class LoggingFilterTest {

  @Test
  void verifyCanLogWithoutLoggingFilterReturnsTrue() {

    LoggingFilter loggingFilter = null;
    assertThat(LoggingFilter.canLog(loggingFilter, "message"), is(true));
  }

  @Test
  void verifyCanLogReturnsTrueValueFromLoggingFilter() {
    LoggingFilter returnTrueLoggingFilter = MockLoggingFilter.createWithReturnValue(true);
    assertThat(LoggingFilter.canLog(returnTrueLoggingFilter, "message"), is(true));
  }

  @Test
  void verifyCanLogReturnsFalseValueFromLoggingFilter() {
    LoggingFilter returnFalseLoggingFilter = MockLoggingFilter.createWithReturnValue(false);
    assertThat(LoggingFilter.canLog(returnFalseLoggingFilter, "message"), is(false));
  }
}
