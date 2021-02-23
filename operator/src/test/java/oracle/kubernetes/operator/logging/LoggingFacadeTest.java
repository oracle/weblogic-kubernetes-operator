// Copyright (c) 2019, 2021, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.logging;

import java.util.logging.Level;
import java.util.logging.Logger;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

public class LoggingFacadeTest {

  MockLogger mockLogger;
  LoggingFacade loggingFacade;

  @BeforeEach
  public void setup() {
    mockLogger = new MockLogger();
    loggingFacade = new LoggingFacade(mockLogger);
  }

  @Test
  public void verifyInfoMessageLoggedIfLoggingFilterIsNull() {
    loggingFacade.info((LoggingFilter) null, "msg");

    assertThat(mockLogger.isLogpCalled(), is(true));
  }

  @Test
  public void verifyInfoMessageLoggedIfLoggingFilterAllows() {
    final String message = "info message";
    loggingFacade.info(MockLoggingFilter.createWithReturnValue(true), message);

    assertThat(mockLogger.isLogpCalled(), is(true));
    assertThat(mockLogger.getMessageLevel(), is(Level.INFO));
    assertThat(mockLogger.getMessage(), is(message));
  }

  @Test
  public void verifyInfoMessageNotLoggedIfLoggingFilterDenies() {
    loggingFacade.info(MockLoggingFilter.createWithReturnValue(false), "msg");

    assertThat(mockLogger.isLogpCalled(), is(false));
  }

  @Test
  public void verifyWarningMessageLoggedIfLoggingFilterIsNull() {
    loggingFacade.warning((LoggingFilter) null, "msg");

    assertThat(mockLogger.isLogpCalled(), is(true));
  }

  @Test
  public void verifyWarningMessageLoggedIfLoggingFilterAllows() {
    final String message = "warning message";
    loggingFacade.warning(MockLoggingFilter.createWithReturnValue(true), message);

    assertThat(mockLogger.isLogpCalled(), is(true));
    assertThat(mockLogger.getMessageLevel(), is(Level.WARNING));
    assertThat(mockLogger.getMessage(), is(message));
  }

  @Test
  public void verifyWarningMessageNotLoggedIfLoggingFilterDenies() {
    loggingFacade.warning(MockLoggingFilter.createWithReturnValue(false), "msg");

    assertThat(mockLogger.isLogpCalled(), is(false));
  }

  @Test
  public void verifySevereMessageLoggedIfLoggingFilterIsNull() {
    loggingFacade.severe((LoggingFilter) null, "msg");

    assertThat(mockLogger.isLogpCalled(), is(true));
  }

  @Test
  public void verifySevereMessageLoggedIfLoggingFilterAllows() {
    final String message = "severe message";
    loggingFacade.severe(MockLoggingFilter.createWithReturnValue(true), message);

    assertThat(mockLogger.isLogpCalled(), is(true));
    assertThat(mockLogger.getMessageLevel(), is(Level.SEVERE));
    assertThat(mockLogger.getMessage(), is(message));
  }

  @Test
  public void verifySevereMessageNotLoggedIfLoggingFilterDenies() {
    loggingFacade.severe(MockLoggingFilter.createWithReturnValue(false), "msg");

    assertThat(mockLogger.isLogpCalled(), is(false));
  }

  @Test
  public void verifySevereMessageWithThrowableLoggedIfLoggingFilterIsNull() {
    loggingFacade.severe(null, "msg", new Throwable());

    assertThat(mockLogger.isLogpCalled(), is(true));
  }

  @Test
  public void verifySevereMessageWithThrowableLoggedIfLoggingFilterAllows() {
    final String message = "severe message";
    final Throwable throwable = new Throwable("throwable");
    loggingFacade.severe(MockLoggingFilter.createWithReturnValue(true), message, throwable);

    assertThat(mockLogger.isLogpCalled(), is(true));
    assertThat(mockLogger.getMessageLevel(), is(Level.SEVERE));
    assertThat(mockLogger.getMessage(), is(message));
    assertThat(mockLogger.getMessageThrowable(), is(throwable));
  }

  @Test
  public void verifySevereMessageWithThrowableNotLoggedIfLoggingFilterDenies() {
    loggingFacade.severe(MockLoggingFilter.createWithReturnValue(false), "msg", new Throwable());

    assertThat(mockLogger.isLogpCalled(), is(false));
  }

  @Test
  public void verifyGetFormattedMessage_withArgs_returnsFormattedMessage() {
    assertThat(loggingFacade.formatMessage(MessageKeys.CYCLING_SERVERS, "domain1", "list1"),
        is("Cycling of servers for Domain with UID domain1 in the list list1 now"));
  }

  @Test
  public void verifyGetFormattedMessage_withNoArgs_returnsFormattedMessage() {
    assertThat(loggingFacade.formatMessage(MessageKeys.RESOURCE_BUNDLE_NOT_FOUND),
        is("Could not find the resource bundle"));
  }

  static class MockLogger extends Logger {

    boolean logpCalled;
    Level messageLevel;
    String message;
    Throwable messageThrowable;

    public MockLogger() {
      super("MockLogger", "Operator");
    }

    @Override
    public void logp(
        Level level, String sourceClass, String sourceMethod, String msg, Object[] params) {
      logpCalled = true;
      message = msg;
      messageLevel = level;
    }

    public void logp(
        Level level, String sourceClass, String sourceMethod, String msg, Throwable thrown) {
      logpCalled = true;
      message = msg;
      messageThrowable = thrown;
      messageLevel = level;
    }

    boolean isLogpCalled() {
      return logpCalled;
    }

    String getMessage() {
      return message;
    }

    Level getMessageLevel() {
      return messageLevel;
    }

    Throwable getMessageThrowable() {
      return messageThrowable;
    }
  }
}
