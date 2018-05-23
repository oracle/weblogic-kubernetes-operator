// Copyright 2018, Oracle Corporation and/or its affiliates.  All rights reserved.
// Licensed under the Universal Permissive License v 1.0 as shown at
// http://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.utils;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import oracle.kubernetes.operator.logging.LoggingFacade;

public class LoggingFacadeStub {

  static Field loggerField = null; // logger field of LoggingFacade class

  Logger originalLogger = null;
  LoggingFacade loggingFacade = null;

  List<LogRecord> logRecordList = new ArrayList<>();

  public static LoggingFacadeStub install(Class classWithLoggingFacade) throws Exception {
    return install(classWithLoggingFacade, "LOGGER");
  }

  public static LoggingFacadeStub install(Class classWithLoggingFacade, String loggerFieldName)
      throws Exception {
    if (loggerField == null) {
      loggerField = LoggingFacade.class.getDeclaredField("logger");
      loggerField.setAccessible(true);
    }
    Field loggingFacadeField = classWithLoggingFacade.getDeclaredField(loggerFieldName);
    loggingFacadeField.setAccessible(true);
    LoggingFacade loggingFacade = (LoggingFacade) loggingFacadeField.get(classWithLoggingFacade);
    return new LoggingFacadeStub(loggingFacade);
  }

  public LoggingFacadeStub(LoggingFacade loggingFacade) throws Exception {
    this.loggingFacade = loggingFacade;
    originalLogger = (Logger) loggerField.get(loggingFacade);
    if (!(originalLogger instanceof LoggerStub)) {
      // only if not already mocked
      LoggerStub loggerStub = new LoggerStub();
      loggerField.set(loggingFacade, loggerStub);
    }
  }

  public void uninstall() throws IllegalAccessException {
    loggerField.set(loggingFacade, originalLogger);
  }

  public int getNumMessagesLogged() {
    return logRecordList.size();
  }

  public int getNumMessagesLogged(Level level) {
    int numLogged = 0;
    for (LogRecord logRecord : logRecordList) {
      if (logRecord.getLevel().equals(level)) {
        numLogged++;
      }
    }
    return numLogged;
  }

  public boolean containsLogRecord(Level logLevel, String message, Object... parameters) {
    for (LogRecord logRecord : logRecordList) {
      if (parameters != null && parameters.length == 0) {
        parameters = null;
      }
      if (matches(logRecord, logLevel, message, parameters)) {
        return true;
      }
    }
    return false;
  }

  public boolean containsLogRecord(Level logLevel, String message, Throwable thrown) {
    for (LogRecord logRecord : logRecordList) {
      if (matches(logRecord, logLevel, message, thrown)) {
        return true;
      }
    }
    return false;
  }

  public boolean matches(LogRecord logRecord, Level logLevel, String message, Throwable thrown) {
    if ((!logRecord.getLevel().equals(logLevel)) || !logRecord.getMessage().equals(message)) {
      return false;
    }
    if (logRecord.getThrown() == null) {
      return thrown == null;
    }
    if (thrown == null || !thrown.toString().equals(logRecord.getThrown().toString())) {
      return false;
    }
    return true;
  }

  public boolean matches(LogRecord logRecord, Level logLevel, String message, Object[] parameters) {
    if ((!logRecord.getLevel().equals(logLevel)) || !logRecord.getMessage().equals(message)) {
      return false;
    }
    Object[] logRecordParams = logRecord.getParameters();
    if (parameters == null) {
      return logRecordParams == null;
    }
    if (logRecordParams == null || parameters.length != logRecordParams.length) {

      return false;
    }
    for (int i = 0; i < parameters.length; i++) {
      if (parameters[i] == null && logRecordParams[i] != null
          || parameters[i] != null && logRecordParams[i] == null) {
        return false;
      }
      if (!parameters[i].toString().equals(logRecordParams[i].toString())) {
        return false;
      }
    }
    return true;
  }

  public void assertContains(Level logLevel, String message, Object... parameters) {
    assertTrue(
        "No log message found with level: "
            + logLevel
            + ", Message: "
            + message
            + ", Parameters: "
            + parametersAsString(parameters)
            + "\n"
            + toString(),
        containsLogRecord(logLevel, message, parameters));
  }

  public void assertContains(Level logLevel, String message, Throwable thrown) {
    assertTrue(
        "No log message found with level: "
            + logLevel
            + ", Message: "
            + message
            + ", Thrown: "
            + thrown
            + "\n"
            + toString(),
        containsLogRecord(logLevel, message, thrown));
  }

  public void assertNoMessagesLogged() {
    assertNumMessageLogged(0);
  }

  public void assertNoMessagesLogged(Level level) {
    assertNumMessageLogged(0, level);
  }

  public void assertNumMessageLogged(int expectedNumMessages, Level level) {
    assertEquals(
        "Messages logged: " + toString(), expectedNumMessages, getNumMessagesLogged(level));
  }

  public void assertNumMessageLogged(int expectedNumMessages) {
    assertEquals("Messages logged: " + toString(), expectedNumMessages, getNumMessagesLogged());
  }

  public String toString() {
    StringBuffer sb = new StringBuffer("List of all LogRecord logged: \n");
    for (LogRecord logRecord : logRecordList) {
      sb.append("Level: " + logRecord.getLevel());
      sb.append(", Message: " + logRecord.getMessage());
      sb.append(", Thrown: " + logRecord.getThrown());
      sb.append(", Parameters: ").append(parametersAsString(logRecord.getParameters()));
    }
    return sb.toString();
  }

  private String parametersAsString(Object[] parameters) {
    StringBuffer sb = new StringBuffer();
    if (parameters == null) {
      sb.append("null");
    } else {
      for (Object parameter : parameters) {
        sb.append("\t" + parameter);
      }
    }
    sb.append('\n');
    return sb.toString();
  }

  public class LoggerStub extends Logger {

    public LoggerStub() {
      super("LoggerStub", null);
      setLevel(Level.FINEST);
    }

    @Override
    public void log(LogRecord record) {
      logRecordList.add(record);
    }
  }
}
