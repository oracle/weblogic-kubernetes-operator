// Copyright 2019, Oracle Corporation and/or its affiliates.  All rights reserved.
// Licensed under the Universal Permissive License v 1.0 as shown at
// http://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.logging;

import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThat;

import org.junit.Test;

public class OncePerMessageLoggingFilterTest {

  @Test
  public void verifyCanLogReturnsFalseForRepeatedMessage() {
    final String MESSAGE = "some log message";
    OncePerMessageLoggingFilter loggingFilter = new OncePerMessageLoggingFilter();
    loggingFilter.setFiltering(true);

    loggingFilter.canLog(MESSAGE);
    assertThat(loggingFilter.canLog(MESSAGE), is(false));
  }

  @Test
  public void verifyCanLogReturnsFalseForRepeatedNullMessage() {
    final String MESSAGE = null;
    OncePerMessageLoggingFilter loggingFilter = new OncePerMessageLoggingFilter();
    loggingFilter.setFiltering(true);

    loggingFilter.canLog(MESSAGE);
    assertThat(loggingFilter.canLog(MESSAGE), is(false));
  }

  @Test
  public void verifyCanLogReturnsTrueForRepeatedMessageWithoutFiltering() {
    final String MESSAGE = "some log message";
    OncePerMessageLoggingFilter loggingFilter = new OncePerMessageLoggingFilter();

    assertThat(loggingFilter.canLog(MESSAGE), is(true));
    assertThat(loggingFilter.canLog(MESSAGE), is(true));
  }

  @Test
  public void verifyCanLogReturnsTrueForDifferentMessage() {
    final String MESSAGE = "some log message";
    final String MESSAGE2 = "another log message";
    OncePerMessageLoggingFilter loggingFilter = new OncePerMessageLoggingFilter();
    loggingFilter.setFiltering(true);

    loggingFilter.canLog(MESSAGE);
    assertThat(loggingFilter.canLog(MESSAGE2), is(true));
  }

  @Test
  public void verifyMessageHistoryKeptBeforeFilteringIsOn() {
    final String MESSAGE = "some log message";
    OncePerMessageLoggingFilter loggingFilter = new OncePerMessageLoggingFilter();

    loggingFilter.canLog(MESSAGE);

    loggingFilter.setFiltering(true);

    assertThat(loggingFilter.canLog(MESSAGE), is(false));
  }

  @Test
  public void verifyResetHistoryClearsLogHistory() {
    final String MESSAGE = "some log message";
    OncePerMessageLoggingFilter loggingFilter = new OncePerMessageLoggingFilter();

    loggingFilter.canLog(MESSAGE);

    loggingFilter.resetLogHistory();

    assertThat(loggingFilter.messagesLogged.contains(MESSAGE), is(false));
  }

  @Test
  public void verifyCanLogReturnsTrueOnceAfterReenablingFilteringAfterOffAndReset() {
    final String MESSAGE = "some log message";

    OncePerMessageLoggingFilter loggingFilter = new OncePerMessageLoggingFilter();
    loggingFilter.setFiltering(true);

    loggingFilter.canLog(MESSAGE);

    loggingFilter.setFiltering(false).resetLogHistory();

    loggingFilter.setFiltering(true);
    assertThat(loggingFilter.canLog(MESSAGE), is(true));
    assertThat(loggingFilter.canLog(MESSAGE), is(false));
  }

  @Test
  public void verifyCanLogReturnsFalseAfterReenablingFilteringAfterOffWithoutReset() {
    final String MESSAGE = "some log message";

    OncePerMessageLoggingFilter loggingFilter = new OncePerMessageLoggingFilter();
    loggingFilter.setFiltering(true);

    loggingFilter.canLog(MESSAGE);

    loggingFilter.setFiltering(false);

    loggingFilter.setFiltering(true);
    assertThat(loggingFilter.canLog(MESSAGE), is(false));
  }
}
