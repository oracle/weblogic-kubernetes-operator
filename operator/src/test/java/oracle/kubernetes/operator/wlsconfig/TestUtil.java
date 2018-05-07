// Copyright 2018, Oracle Corporation and/or its affiliates.  All rights reserved.
// Licensed under the Universal Permissive License v 1.0 as shown at
// http://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.wlsconfig;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import java.util.logging.SimpleFormatter;
import oracle.kubernetes.operator.logging.LoggingFacade;

/** Copyright (c) 2017, Oracle and/or its affiliates. All rights reserved. */
public class TestUtil {
  static Field wlsClusterConfigLoggerFacadeField = null;
  static Field wlsClusterConfigLoggerField = null;

  static Field wlsDomainConfigLoggerFacadeField = null;
  static Field wlsDomainConfigLoggerField = null;

  static Logger getLogger(WlsClusterConfig wlsClusterConfig) throws Exception {
    if (wlsClusterConfigLoggerFacadeField == null) {
      wlsClusterConfigLoggerFacadeField = wlsClusterConfig.getClass().getDeclaredField("LOGGER");
      wlsClusterConfigLoggerFacadeField.setAccessible(true);
    }
    LoggingFacade loggingFacade =
        (LoggingFacade) wlsClusterConfigLoggerFacadeField.get(wlsClusterConfig);
    if (wlsClusterConfigLoggerField == null) {
      wlsClusterConfigLoggerField = loggingFacade.getClass().getDeclaredField("logger");
      wlsClusterConfigLoggerField.setAccessible(true);
    }
    return (Logger) wlsClusterConfigLoggerField.get(loggingFacade);
  }

  static Logger getLogger(WlsDomainConfig wlsDomainConfig) throws Exception {
    if (wlsDomainConfigLoggerFacadeField == null) {
      wlsDomainConfigLoggerFacadeField = wlsDomainConfig.getClass().getDeclaredField("LOGGER");
      wlsDomainConfigLoggerFacadeField.setAccessible(true);
    }
    LoggingFacade loggingFacade =
        (LoggingFacade) wlsDomainConfigLoggerFacadeField.get(wlsDomainConfig);
    if (wlsDomainConfigLoggerField == null) {
      wlsDomainConfigLoggerField = loggingFacade.getClass().getDeclaredField("logger");
      wlsDomainConfigLoggerField.setAccessible(true);
    }
    return (Logger) wlsDomainConfigLoggerField.get(loggingFacade);
  }

  static LogHandlerImpl setupLogHandler(WlsClusterConfig wlsClusterConfig) throws Exception {
    Logger logger = getLogger(wlsClusterConfig);
    LogHandlerImpl handler = new LogHandlerImpl();
    logger.addHandler(handler);
    return handler;
  }

  static LogHandlerImpl setupLogHandler(WlsDomainConfig wlsDomainConfig) throws Exception {
    Logger logger = getLogger(wlsDomainConfig);
    LogHandlerImpl handler = new LogHandlerImpl();
    logger.addHandler(handler);
    return handler;
  }

  static void removeLogHandler(WlsClusterConfig wlsClusterConfig, Handler logHandler)
      throws Exception {
    Logger logger = getLogger(wlsClusterConfig);
    logger.removeHandler(logHandler);
  }

  static void removeLogHandler(WlsDomainConfig wlsDomainConfig, Handler logHandler)
      throws Exception {
    Logger logger = getLogger(wlsDomainConfig);
    logger.removeHandler(logHandler);
  }

  static class LogHandlerImpl extends Handler {

    ArrayList<LogRecord> warningLogRecords = new ArrayList<>();
    ArrayList<LogRecord> infoLogRecords = new ArrayList<>();

    LogHandlerImpl() {
      setFormatter(new SimpleFormatter());
    }

    @Override
    public void publish(LogRecord record) {
      if (Level.WARNING == record.getLevel()) {
        warningLogRecords.add(record);
      } else if (Level.INFO == record.getLevel()) {
        infoLogRecords.add(record);
      }
    }

    @Override
    public void flush() {}

    @Override
    public void close() throws SecurityException {}

    public String formatMessage(LogRecord logRecord) {
      return getFormatter().format(logRecord);
    }

    public boolean hasWarningMessageLogged() {
      return warningLogRecords.size() > 0;
    }

    public String getAllFormattedMessage() {
      if (warningLogRecords.isEmpty() && infoLogRecords.isEmpty()) {
        return "No message logged";
      }
      StringBuilder sb = new StringBuilder();
      for (LogRecord logRecord : warningLogRecords) {
        sb.append(formatMessage(logRecord)).append("\n");
      }
      for (LogRecord logRecord : infoLogRecords) {
        sb.append(formatMessage(logRecord)).append("\n");
      }
      return sb.toString();
    }

    public boolean hasWarningMessageWithSubString(String searchString) {
      if (warningLogRecords != null) {
        for (LogRecord logRecord : warningLogRecords) {
          if (formatMessage(logRecord).contains(searchString)) {
            return true;
          }
        }
      }
      return false;
    }

    public boolean hasInfoMessageWithSubString(String searchString) {
      if (infoLogRecords != null) {
        for (LogRecord logRecord : infoLogRecords) {
          if (formatMessage(logRecord).contains(searchString)) {
            return true;
          }
        }
      }
      return false;
    }
  }
}
