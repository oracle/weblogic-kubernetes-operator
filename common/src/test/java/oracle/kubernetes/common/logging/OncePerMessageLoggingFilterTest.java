// Copyright (c) 2019, 2022, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.common.logging;

import oracle.kubernetes.common.logging.OncePerMessageLoggingFilter;
import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

class OncePerMessageLoggingFilterTest {

  @Test
  void verifyCanLogReturnsFalseForRepeatedMessage() {
    final String message = "some log message";
    OncePerMessageLoggingFilter loggingFilter = new OncePerMessageLoggingFilter();
    loggingFilter.setFiltering(true);

    loggingFilter.canLog(message);
    assertThat(loggingFilter.canLog(message), is(false));
  }

  @Test
  void verifyCanLogReturnsFalseForRepeatedNullMessage() {
    final String message = null;
    OncePerMessageLoggingFilter loggingFilter = new OncePerMessageLoggingFilter();
    loggingFilter.setFiltering(true);

    loggingFilter.canLog(message);
    assertThat(loggingFilter.canLog(message), is(false));
  }

  @Test
  void verifyCanLogReturnsTrueForRepeatedMessageWithoutFiltering() {
    final String message = "some log message";
    OncePerMessageLoggingFilter loggingFilter = new OncePerMessageLoggingFilter();

    assertThat(loggingFilter.canLog(message), is(true));
    assertThat(loggingFilter.canLog(message), is(true));
  }

  @Test
  void verifyCanLogReturnsTrueForDifferentMessage() {
    final String message = "some log message";
    final String message2 = "another log message";
    OncePerMessageLoggingFilter loggingFilter = new OncePerMessageLoggingFilter();
    loggingFilter.setFiltering(true);

    loggingFilter.canLog(message);
    assertThat(loggingFilter.canLog(message2), is(true));
  }

  @Test
  void verifyMessageHistoryKeptBeforeFilteringIsOn() {
    final String message = "some log message";
    OncePerMessageLoggingFilter loggingFilter = new OncePerMessageLoggingFilter();

    loggingFilter.canLog(message);

    loggingFilter.setFiltering(true);

    assertThat(loggingFilter.canLog(message), is(false));
  }

  @Test
  void verifyResetHistoryClearsLogHistory() {
    final String message = "some log message";
    OncePerMessageLoggingFilter loggingFilter = new OncePerMessageLoggingFilter();

    loggingFilter.canLog(message);

    loggingFilter.resetLogHistory();

    assertThat(loggingFilter.messagesLogged.contains(message), is(false));
  }

  @Test
  void verifyCanLogReturnsTrueOnceAfterReenablingFilteringAfterOffAndReset() {
    final String message = "some log message";

    OncePerMessageLoggingFilter loggingFilter = new OncePerMessageLoggingFilter();
    loggingFilter.setFiltering(true);

    loggingFilter.canLog(message);

    loggingFilter.setFiltering(false).resetLogHistory();

    loggingFilter.setFiltering(true);
    assertThat(loggingFilter.canLog(message), is(true));
    assertThat(loggingFilter.canLog(message), is(false));
  }

  @Test
  void verifyCanLogReturnsFalseAfterReenablingFilteringAfterOffWithoutReset() {
    final String message = "some log message";

    OncePerMessageLoggingFilter loggingFilter = new OncePerMessageLoggingFilter();
    loggingFilter.setFiltering(true);

    loggingFilter.canLog(message);

    loggingFilter.setFiltering(false);

    loggingFilter.setFiltering(true);
    assertThat(loggingFilter.canLog(message), is(false));
  }
}
