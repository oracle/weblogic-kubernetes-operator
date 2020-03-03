// Copyright (c) 2019, 2020, Oracle Corporation and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.logging;

import org.junit.Test;

import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThat;

public class OncePerMessageLoggingFilterTest {

  @Test
  public void verifyCanLogReturnsFalseForRepeatedMessage() {
    final String message = "some log message";
    OncePerMessageLoggingFilter loggingFilter = new OncePerMessageLoggingFilter();
    loggingFilter.setFiltering(true);

    loggingFilter.canLog(message);
    assertThat(loggingFilter.canLog(message), is(false));
  }

  @Test
  public void verifyCanLogReturnsFalseForRepeatedNullMessage() {
    final String message = null;
    OncePerMessageLoggingFilter loggingFilter = new OncePerMessageLoggingFilter();
    loggingFilter.setFiltering(true);

    loggingFilter.canLog(message);
    assertThat(loggingFilter.canLog(message), is(false));
  }

  @Test
  public void verifyCanLogReturnsTrueForRepeatedMessageWithoutFiltering() {
    final String message = "some log message";
    OncePerMessageLoggingFilter loggingFilter = new OncePerMessageLoggingFilter();

    assertThat(loggingFilter.canLog(message), is(true));
    assertThat(loggingFilter.canLog(message), is(true));
  }

  @Test
  public void verifyCanLogReturnsTrueForDifferentMessage() {
    final String message = "some log message";
    final String message2 = "another log message";
    OncePerMessageLoggingFilter loggingFilter = new OncePerMessageLoggingFilter();
    loggingFilter.setFiltering(true);

    loggingFilter.canLog(message);
    assertThat(loggingFilter.canLog(message2), is(true));
  }

  @Test
  public void verifyMessageHistoryKeptBeforeFilteringIsOn() {
    final String message = "some log message";
    OncePerMessageLoggingFilter loggingFilter = new OncePerMessageLoggingFilter();

    loggingFilter.canLog(message);

    loggingFilter.setFiltering(true);

    assertThat(loggingFilter.canLog(message), is(false));
  }

  @Test
  public void verifyResetHistoryClearsLogHistory() {
    final String message = "some log message";
    OncePerMessageLoggingFilter loggingFilter = new OncePerMessageLoggingFilter();

    loggingFilter.canLog(message);

    loggingFilter.resetLogHistory();

    assertThat(loggingFilter.messagesLogged.contains(message), is(false));
  }

  @Test
  public void verifyCanLogReturnsTrueOnceAfterReenablingFilteringAfterOffAndReset() {
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
  public void verifyCanLogReturnsFalseAfterReenablingFilteringAfterOffWithoutReset() {
    final String message = "some log message";

    OncePerMessageLoggingFilter loggingFilter = new OncePerMessageLoggingFilter();
    loggingFilter.setFiltering(true);

    loggingFilter.canLog(message);

    loggingFilter.setFiltering(false);

    loggingFilter.setFiltering(true);
    assertThat(loggingFilter.canLog(message), is(false));
  }
}
