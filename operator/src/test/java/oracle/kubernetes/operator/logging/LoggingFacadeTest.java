// Copyright 2019, Oracle Corporation and/or its affiliates.  All rights reserved.
// Licensed under the Universal Permissive License v 1.0 as shown at
// http://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.logging;

import static org.hamcrest.Matchers.is;
import static org.junit.Assert.*;

import java.util.logging.Level;
import java.util.logging.Logger;
import org.junit.Test;

public class LoggingFacadeTest {

  @Test
  public void verifyInfoMessageLoggedIfLoggingFilterIsNull() {
    MockLogger mockLogger = new MockLogger();
    LoggingFacade loggingFacade = new LoggingFacade(mockLogger);

    loggingFacade.info((LoggingFilter) null, "msg");

    assertThat(mockLogger.isLogpCalled(), is(true));
  }

  @Test
  public void verifyInfoMessageLoggedIfLoggingFilterAllows() {
    final String MESSAGE = "info message";
    MockLogger mockLogger = new MockLogger();
    LoggingFacade loggingFacade = new LoggingFacade(mockLogger);

    loggingFacade.info(MockLoggingFilter.createWithReturnValue(true), MESSAGE);

    assertThat(mockLogger.isLogpCalled(), is(true));
    assertThat(mockLogger.getMessageLevel(), is(Level.INFO));
    assertThat(mockLogger.getMessage(), is(MESSAGE));
  }

  @Test
  public void verifyInfoMessageNotLoggedIfLoggingFilterDenies() {
    MockLogger mockLogger = new MockLogger();
    LoggingFacade loggingFacade = new LoggingFacade(mockLogger);

    loggingFacade.info(MockLoggingFilter.createWithReturnValue(false), "msg");

    assertThat(mockLogger.isLogpCalled(), is(false));
  }

  @Test
  public void verifyWarningMessageLoggedIfLoggingFilterIsNull() {
    MockLogger mockLogger = new MockLogger();
    LoggingFacade loggingFacade = new LoggingFacade(mockLogger);

    loggingFacade.warning((LoggingFilter) null, "msg");

    assertThat(mockLogger.isLogpCalled(), is(true));
  }

  @Test
  public void verifyWarningMessageLoggedIfLoggingFilterAllows() {
    final String MESSAGE = "warning message";
    MockLogger mockLogger = new MockLogger();
    LoggingFacade loggingFacade = new LoggingFacade(mockLogger);

    loggingFacade.warning(MockLoggingFilter.createWithReturnValue(true), MESSAGE);

    assertThat(mockLogger.isLogpCalled(), is(true));
    assertThat(mockLogger.getMessageLevel(), is(Level.WARNING));
    assertThat(mockLogger.getMessage(), is(MESSAGE));
  }

  @Test
  public void verifyWarningMessageNotLoggedIfLoggingFilterDenies() {
    MockLogger mockLogger = new MockLogger();
    LoggingFacade loggingFacade = new LoggingFacade(mockLogger);

    loggingFacade.warning(MockLoggingFilter.createWithReturnValue(false), "msg");

    assertThat(mockLogger.isLogpCalled(), is(false));
  }

  @Test
  public void verifySevereMessageLoggedIfLoggingFilterIsNull() {
    MockLogger mockLogger = new MockLogger();
    LoggingFacade loggingFacade = new LoggingFacade(mockLogger);

    loggingFacade.severe((LoggingFilter) null, "msg");

    assertThat(mockLogger.isLogpCalled(), is(true));
  }

  @Test
  public void verifySevereMessageLoggedIfLoggingFilterAllows() {
    final String MESSAGE = "severe message";
    MockLogger mockLogger = new MockLogger();
    LoggingFacade loggingFacade = new LoggingFacade(mockLogger);

    loggingFacade.severe(MockLoggingFilter.createWithReturnValue(true), MESSAGE);

    assertThat(mockLogger.isLogpCalled(), is(true));
    assertThat(mockLogger.getMessageLevel(), is(Level.SEVERE));
    assertThat(mockLogger.getMessage(), is(MESSAGE));
  }

  @Test
  public void verifySevereMessageNotLoggedIfLoggingFilterDenies() {
    MockLogger mockLogger = new MockLogger();
    LoggingFacade loggingFacade = new LoggingFacade(mockLogger);

    loggingFacade.severe(MockLoggingFilter.createWithReturnValue(false), "msg");

    assertThat(mockLogger.isLogpCalled(), is(false));
  }

  @Test
  public void verifySevereMessageWithThrowableLoggedIfLoggingFilterIsNull() {
    MockLogger mockLogger = new MockLogger();
    LoggingFacade loggingFacade = new LoggingFacade(mockLogger);

    loggingFacade.severe((LoggingFilter) null, "msg", new Throwable());

    assertThat(mockLogger.isLogpCalled(), is(true));
  }

  @Test
  public void verifySevereMessageWithThrowableLoggedIfLoggingFilterAllows() {
    final String MESSAGE = "severe message";
    final Throwable THROWABLE = new Throwable("throwable");
    MockLogger mockLogger = new MockLogger();
    LoggingFacade loggingFacade = new LoggingFacade(mockLogger);

    loggingFacade.severe(MockLoggingFilter.createWithReturnValue(true), MESSAGE, THROWABLE);

    assertThat(mockLogger.isLogpCalled(), is(true));
    assertThat(mockLogger.getMessageLevel(), is(Level.SEVERE));
    assertThat(mockLogger.getMessage(), is(MESSAGE));
    assertThat(mockLogger.getMessageThrowable(), is(THROWABLE));
  }

  @Test
  public void verifySevereMessageWithThrowableNotLoggedIfLoggingFilterDenies() {
    MockLogger mockLogger = new MockLogger();
    LoggingFacade loggingFacade = new LoggingFacade(mockLogger);

    loggingFacade.severe(MockLoggingFilter.createWithReturnValue(false), "msg", new Throwable());

    assertThat(mockLogger.isLogpCalled(), is(false));
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
        Level level, String sourceClass, String sourceMethod, String msg, Object params[]) {
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
