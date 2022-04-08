// Copyright (c) 2019, 2022, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.common.logging;

import java.util.logging.Level;
import java.util.logging.Logger;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;

class CommonLoggingFacadeTest {

  MockLogger mockLogger;
  CommonLoggingFacade loggingFacade;

  @BeforeEach
  public void setup() {
    mockLogger = new MockLogger();
    loggingFacade = new CommonLoggingFacade(mockLogger);
  }

  @Test
  void verifyEnteringLogged() {
    mockLogger.setLevel(Level.FINER);
    loggingFacade.entering();

    assertThat(mockLogger.isLogpCalled(), is(true));
    assertThat(mockLogger.messageLevel, is(Level.FINER));
  }

  @Test
  void verifyEnteringWithParamsLogged() {
    mockLogger.setLevel(Level.FINER);
    loggingFacade.entering("params");

    assertThat(mockLogger.isLogpCalled(), is(true));
    assertThat(mockLogger.messageLevel, is(Level.FINER));
    assertThat(mockLogger.messageParams, is(new Object[] { "params" }));
  }

  @Test
  void verifyExitingLogged() {
    mockLogger.setLevel(Level.FINER);
    loggingFacade.exiting();

    assertThat(mockLogger.isLogpCalled(), is(true));
    assertThat(mockLogger.messageLevel, is(Level.FINER));
  }

  @Test
  void verifyExitingWithParamsLogged() {
    mockLogger.setLevel(Level.FINER);
    loggingFacade.exiting("params");

    assertThat(mockLogger.isLogpCalled(), is(true));
    assertThat(mockLogger.messageLevel, is(Level.FINER));
    assertThat(mockLogger.messageParams, is(new Object[] { "params" }));
  }

  @Test
  void verifyLogMessageLogged() {
    mockLogger.setLevel(Level.FINEST);
    loggingFacade.log(Level.FINEST, "msg");

    assertThat(mockLogger.isLogpCalled(), is(true));
    assertThat(mockLogger.messageLevel, is(Level.FINEST));
  }

  @Test
  void verifyLogMessageWithParamsLogged() {
    mockLogger.setLevel(Level.FINEST);
    loggingFacade.log(Level.FINEST, "msg", "params");

    assertThat(mockLogger.isLogpCalled(), is(true));
    assertThat(mockLogger.messageLevel, is(Level.FINEST));
    assertThat(mockLogger.messageParams, is(new Object[] { "params" }));
  }

  @Test
  void verifyThrowingLogged() {
    mockLogger.setLevel(Level.FINEST);
    loggingFacade.log(Level.FINEST, "msg", new Throwable());

    assertThat(mockLogger.isLogpCalled(), is(true));
    assertThat(mockLogger.messageLevel, is(Level.FINEST));
    assertThat(mockLogger.messageThrowable, notNullValue());
  }

  @Test
  void verifyFinestMessageLogged() {
    mockLogger.setLevel(Level.FINEST);
    loggingFacade.finest("msg");

    assertThat(mockLogger.isLogpCalled(), is(true));
    assertThat(mockLogger.messageLevel, is(Level.FINEST));
  }

  @Test
  void verifyFinestMessageWithParamsLogged() {
    mockLogger.setLevel(Level.FINEST);
    loggingFacade.finest("msg", "params");

    assertThat(mockLogger.isLogpCalled(), is(true));
    assertThat(mockLogger.messageLevel, is(Level.FINEST));
    assertThat(mockLogger.messageParams, is(new Object[] { "params" }));
  }

  @Test
  void verifyFinestMessageWithThrowableLogged() {
    mockLogger.setLevel(Level.FINEST);
    loggingFacade.finest("msg", new Throwable());

    assertThat(mockLogger.isLogpCalled(), is(true));
    assertThat(mockLogger.messageLevel, is(Level.FINEST));
    assertThat(mockLogger.messageThrowable, notNullValue());
  }

  @Test
  void verifyFinerMessageLogged() {
    mockLogger.setLevel(Level.FINER);
    loggingFacade.finer("msg");

    assertThat(mockLogger.isLogpCalled(), is(true));
    assertThat(mockLogger.messageLevel, is(Level.FINER));
  }

  @Test
  void verifyFinerMessageWithParamsLogged() {
    mockLogger.setLevel(Level.FINER);
    loggingFacade.finer("msg", "params");

    assertThat(mockLogger.isLogpCalled(), is(true));
    assertThat(mockLogger.messageLevel, is(Level.FINER));
    assertThat(mockLogger.messageParams, is(new Object[] { "params" }));
  }

  @Test
  void verifyFinerMessageWithThrowableLogged() {
    mockLogger.setLevel(Level.FINER);
    loggingFacade.finer("msg", new Throwable());

    assertThat(mockLogger.isLogpCalled(), is(true));
    assertThat(mockLogger.messageLevel, is(Level.FINER));
    assertThat(mockLogger.messageThrowable, notNullValue());
  }

  @Test
  void verifyFineMessageLogged() {
    mockLogger.setLevel(Level.FINE);
    loggingFacade.fine("msg");

    assertThat(mockLogger.isLogpCalled(), is(true));
    assertThat(mockLogger.messageLevel, is(Level.FINE));
  }

  @Test
  void verifyFineMessageWithParamsLogged() {
    mockLogger.setLevel(Level.FINE);
    loggingFacade.fine("msg", "params");

    assertThat(mockLogger.isLogpCalled(), is(true));
    assertThat(mockLogger.messageLevel, is(Level.FINE));
    assertThat(mockLogger.messageParams, is(new Object[] { "params" }));
  }

  @Test
  void verifyFineMessageWithThrowableLogged() {
    mockLogger.setLevel(Level.FINE);
    loggingFacade.fine("msg", new Throwable());

    assertThat(mockLogger.isLogpCalled(), is(true));
    assertThat(mockLogger.messageLevel, is(Level.FINE));
    assertThat(mockLogger.messageThrowable, notNullValue());
  }

  @Test
  void verifyConfigMessageLogged() {
    mockLogger.setLevel(Level.CONFIG);
    loggingFacade.config("msg");

    assertThat(mockLogger.isLogpCalled(), is(true));
    assertThat(mockLogger.messageLevel, is(Level.CONFIG));
  }

  @Test
  void verifyConfigMessageWithParamsLogged() {
    mockLogger.setLevel(Level.CONFIG);
    loggingFacade.config("msg", "params");

    assertThat(mockLogger.isLogpCalled(), is(true));
    assertThat(mockLogger.messageLevel, is(Level.CONFIG));
    assertThat(mockLogger.messageParams, is(new Object[] { "params" }));
  }

  @Test
  void verifyConfigMessageWithThrowableLogged() {
    mockLogger.setLevel(Level.CONFIG);
    loggingFacade.config("msg", new Throwable());

    assertThat(mockLogger.isLogpCalled(), is(true));
    assertThat(mockLogger.messageLevel, is(Level.CONFIG));
    assertThat(mockLogger.messageThrowable, notNullValue());
  }

  @Test
  void verifyInfoMessageLogged() {
    loggingFacade.info("msg");

    assertThat(mockLogger.isLogpCalled(), is(true));
    assertThat(mockLogger.messageLevel, is(Level.INFO));
  }

  @Test
  void verifyInfoMessageWithParamsLogged() {
    loggingFacade.info("msg", "params");

    assertThat(mockLogger.isLogpCalled(), is(true));
    assertThat(mockLogger.messageLevel, is(Level.INFO));
    assertThat(mockLogger.messageParams, is(new Object[] { "params" }));
  }

  @Test
  void verifyInfoMessageWithThrowableLogged() {
    loggingFacade.info("msg", new Throwable());

    assertThat(mockLogger.isLogpCalled(), is(true));
    assertThat(mockLogger.messageLevel, is(Level.INFO));
    assertThat(mockLogger.messageThrowable, notNullValue());
  }

  @Test
  void verifyInfoMessageLoggedIfLoggingFilterIsNull() {
    loggingFacade.info((LoggingFilter) null, "msg");

    assertThat(mockLogger.isLogpCalled(), is(true));
  }

  @Test
  void verifyInfoMessageLoggedIfLoggingFilterAllows() {
    final String message = "info message";
    loggingFacade.info(MockLoggingFilter.createWithReturnValue(true), message);

    assertThat(mockLogger.isLogpCalled(), is(true));
    assertThat(mockLogger.getMessageLevel(), is(Level.INFO));
    assertThat(mockLogger.getMessage(), is(message));
  }

  @Test
  void verifyInfoMessageNotLoggedIfLoggingFilterDenies() {
    loggingFacade.info(MockLoggingFilter.createWithReturnValue(false), "msg");

    assertThat(mockLogger.isLogpCalled(), is(false));
  }

  @Test
  void verifyWarningMessageLogged() {
    mockLogger.setLevel(Level.WARNING);
    loggingFacade.warning("msg");

    assertThat(mockLogger.isLogpCalled(), is(true));
    assertThat(mockLogger.messageLevel, is(Level.WARNING));
  }

  @Test
  void verifyWarningMessageWithParamsLogged() {
    mockLogger.setLevel(Level.WARNING);
    loggingFacade.warning("msg", "params");

    assertThat(mockLogger.isLogpCalled(), is(true));
    assertThat(mockLogger.messageLevel, is(Level.WARNING));
    assertThat(mockLogger.messageParams, is(new Object[] { "params" }));
  }

  @Test
  void verifyWarningMessageWithThrowableLogged() {
    mockLogger.setLevel(Level.WARNING);
    loggingFacade.warning("msg", new Throwable());

    assertThat(mockLogger.isLogpCalled(), is(true));
    assertThat(mockLogger.messageLevel, is(Level.WARNING));
    assertThat(mockLogger.messageThrowable, notNullValue());
  }

  @Test
  void verifyWarningMessageLoggedIfLoggingFilterIsNull() {
    loggingFacade.warning((LoggingFilter) null, "msg");

    assertThat(mockLogger.isLogpCalled(), is(true));
  }

  @Test
  void verifyWarningMessageLoggedIfLoggingFilterAllows() {
    final String message = "warning message";
    loggingFacade.warning(MockLoggingFilter.createWithReturnValue(true), message);

    assertThat(mockLogger.isLogpCalled(), is(true));
    assertThat(mockLogger.getMessageLevel(), is(Level.WARNING));
    assertThat(mockLogger.getMessage(), is(message));
  }

  @Test
  void verifyWarningMessageNotLoggedIfLoggingFilterDenies() {
    loggingFacade.warning(MockLoggingFilter.createWithReturnValue(false), "msg");

    assertThat(mockLogger.isLogpCalled(), is(false));
  }

  @Test
  void verifySevereMessageLogged() {
    mockLogger.setLevel(Level.SEVERE);
    loggingFacade.severe("msg");

    assertThat(mockLogger.isLogpCalled(), is(true));
    assertThat(mockLogger.messageLevel, is(Level.SEVERE));
  }

  @Test
  void verifySevereMessageWithParamsLogged() {
    mockLogger.setLevel(Level.SEVERE);
    loggingFacade.severe("msg", "params");

    assertThat(mockLogger.isLogpCalled(), is(true));
    assertThat(mockLogger.messageLevel, is(Level.SEVERE));
    assertThat(mockLogger.messageParams, is(new Object[] { "params" }));
  }

  @Test
  void verifySevereMessageWithThrowableLogged() {
    mockLogger.setLevel(Level.SEVERE);
    loggingFacade.severe("msg", new Throwable());

    assertThat(mockLogger.isLogpCalled(), is(true));
    assertThat(mockLogger.messageLevel, is(Level.SEVERE));
    assertThat(mockLogger.messageThrowable, notNullValue());
  }

  @Test
  void verifySevereMessageLoggedIfLoggingFilterIsNull() {
    loggingFacade.severe((LoggingFilter) null, "msg");

    assertThat(mockLogger.isLogpCalled(), is(true));
  }

  @Test
  void verifySevereMessageLoggedIfLoggingFilterAllows() {
    final String message = "severe message";
    loggingFacade.severe(MockLoggingFilter.createWithReturnValue(true), message);

    assertThat(mockLogger.isLogpCalled(), is(true));
    assertThat(mockLogger.getMessageLevel(), is(Level.SEVERE));
    assertThat(mockLogger.getMessage(), is(message));
  }

  @Test
  void verifySevereMessageNotLoggedIfLoggingFilterDenies() {
    loggingFacade.severe(MockLoggingFilter.createWithReturnValue(false), "msg");

    assertThat(mockLogger.isLogpCalled(), is(false));
  }

  @Test
  void verifySevereMessageWithThrowableLoggedIfLoggingFilterIsNull() {
    loggingFacade.severe(null, "msg", new Throwable());

    assertThat(mockLogger.isLogpCalled(), is(true));
  }

  @Test
  void verifySevereMessageWithThrowableLoggedIfLoggingFilterAllows() {
    final String message = "severe message";
    final Throwable throwable = new Throwable("throwable");
    loggingFacade.severe(MockLoggingFilter.createWithReturnValue(true), message, throwable);

    assertThat(mockLogger.isLogpCalled(), is(true));
    assertThat(mockLogger.getMessageLevel(), is(Level.SEVERE));
    assertThat(mockLogger.getMessage(), is(message));
    assertThat(mockLogger.getMessageThrowable(), is(throwable));
  }

  @Test
  void verifySevereMessageWithThrowableNotLoggedIfLoggingFilterDenies() {
    loggingFacade.severe(MockLoggingFilter.createWithReturnValue(false), "msg", new Throwable());

    assertThat(mockLogger.isLogpCalled(), is(false));
  }

  @Test
  void verifyGetFormattedMessage_withArgs_returnsFormattedMessage() {
    assertThat(loggingFacade.formatMessage(MessageKeys.CYCLING_SERVERS, "domain1", "list1"),
        is("Cycling of servers for Domain with UID domain1 in the list list1 now"));
  }

  @Test
  void verifyGetFormattedMessage_withNoArgs_returnsFormattedMessage() {
    assertThat(loggingFacade.formatMessage(MessageKeys.RESOURCE_BUNDLE_NOT_FOUND),
        is("Could not find the resource bundle"));
  }

  static class MockLogger extends Logger {

    Level level = Level.INFO;

    boolean logpCalled;
    Level messageLevel;
    String message;
    Throwable messageThrowable;
    Object[] messageParams;

    public MockLogger() {
      super("MockLogger", "Operator");
    }

    public void setLevel(Level level) {
      this.level = level;
    }

    public boolean isLoggable(Level level) {
      int levelValue = this.level.intValue();
      if (level.intValue() < levelValue || levelValue == Level.OFF.intValue()) {
        return false;
      }
      return true;
    }

    @Override
    public void logp(
        Level level, String sourceClass, String sourceMethod, String msg, Object[] params) {
      logpCalled = true;
      message = msg;
      messageLevel = level;
      messageParams = params;
    }

    public void logp(
        Level level, String sourceClass, String sourceMethod, String msg, Throwable thrown) {
      logpCalled = true;
      message = msg;
      messageThrowable = thrown;
      messageLevel = level;
    }

    public void logp(Level level, String sourceClass, String sourceMethod, String msg) {
      logpCalled = true;
      message = msg;
      messageLevel = level;
    }

    public void logp(Level level, String sourceClass, String sourceMethod,
                     String msg, Object param1) {
      logpCalled = true;
      message = msg;
      messageLevel = level;
      messageParams = new Object[] { param1 };
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
