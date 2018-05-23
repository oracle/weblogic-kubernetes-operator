// Copyright 2018, Oracle Corporation and/or its affiliates.  All rights reserved.
// Licensed under the Universal Permissive License v 1.0 as shown at
// http://oss.oracle.com/licenses/upl.

package oracle.kubernetes;

import static com.meterware.simplestub.Stub.createStub;

import com.meterware.simplestub.Memento;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.logging.ConsoleHandler;
import java.util.logging.Handler;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import java.util.logging.SimpleFormatter;
import oracle.kubernetes.operator.logging.LoggingFactory;

public class TestUtils {

  /**
   * Removes the console handlers from the specified logger, in order to silence them during a test.
   *
   * @return a collection of the removed handlers
   */
  public static ConsoleHandlerMemento silenceOperatorLogger() {
    Logger logger = LoggingFactory.getLogger("Operator", "Operator").getUnderlyingLogger();
    List<Handler> savedHandlers = new ArrayList<>();
    for (Handler handler : logger.getHandlers()) {
      if (handler instanceof ConsoleHandler) {
        savedHandlers.add(handler);
      }
    }

    for (Handler handler : savedHandlers) logger.removeHandler(handler);

    TestLogHandler testHandler = createStub(TestLogHandler.class);
    logger.addHandler(testHandler);

    return new ConsoleHandlerMemento(logger, testHandler, savedHandlers);
  }

  abstract static class TestLogHandler extends Handler {
    private Throwable throwable;
    private List<Throwable> ignoredExceptions = new ArrayList<>();
    private Collection<LogRecord> logRecords = new ArrayList<>();
    private List<String> messagesToTrack = new ArrayList<>();

    @Override
    public void publish(LogRecord record) {
      if (record.getThrown() != null && !ignoredExceptions.contains(record.getThrown()))
        throwable = record.getThrown();
      if (messagesToTrack.contains(record.getMessage())) logRecords.add(record);
    }

    void throwLoggedThrowable() {
      if (throwable == null) return;

      throwable.printStackTrace();
      if (throwable instanceof Error) throw (Error) throwable;
      if (throwable instanceof RuntimeException) throw (RuntimeException) throwable;
      throw new RuntimeException(throwable);
    }

    void ignoreLoggedException(Throwable t) {
      ignoredExceptions.add(t);
    }

    void collectLogMessages(Collection<LogRecord> collection, String[] messages) {
      this.logRecords = collection;
      this.messagesToTrack = new ArrayList<>();
      this.messagesToTrack.addAll(Arrays.asList(messages));
    }

    void throwUncheckedLogMessages() {
      if (logRecords.isEmpty()) return;

      SimpleFormatter formatter = new SimpleFormatter();
      List<String> messageKeys = new ArrayList<>();
      for (LogRecord record : logRecords) messageKeys.add(formatter.format(record));

      throw new AssertionError("Unexpected log messages " + messageKeys);
    }
  }

  /**
   * Removes the console handlers from the specified logger, in order to silence them during a test.
   *
   * @param logger a logger to silence
   * @return a collection of the removed handlers
   */
  public static List<Handler> removeConsoleHandlers(Logger logger) {
    List<Handler> savedHandlers = new ArrayList<>();
    for (Handler handler : logger.getHandlers()) {
      if (handler instanceof ConsoleHandler) {
        savedHandlers.add(handler);
      }
    }
    for (Handler handler : savedHandlers) logger.removeHandler(handler);
    return savedHandlers;
  }

  /**
   * Restores the silenced logger handlers.
   *
   * @param logger a logger to restore
   * @param savedHandlers the handlers to restore
   */
  public static void restoreConsoleHandlers(Logger logger, List<Handler> savedHandlers) {
    for (Handler handler : savedHandlers) {
      logger.addHandler(handler);
    }
  }

  public static class ConsoleHandlerMemento implements Memento {
    private Logger logger;
    private TestLogHandler testHandler;
    private List<Handler> savedHandlers;

    ConsoleHandlerMemento(Logger logger, TestLogHandler testHandler, List<Handler> savedHandlers) {
      this.logger = logger;
      this.testHandler = testHandler;
      this.savedHandlers = savedHandlers;
    }

    public ConsoleHandlerMemento ignoringLoggedExceptions(Throwable... throwables) {
      for (Throwable throwable : throwables) testHandler.ignoreLoggedException(throwable);
      return this;
    }

    public ConsoleHandlerMemento collectLogMessages(
        Collection<LogRecord> collection, String... messages) {
      testHandler.collectLogMessages(collection, messages);
      return this;
    }

    public void ignoreMessage(String message) {
      testHandler.messagesToTrack.remove(message);
    }

    @Override
    public void revert() {
      logger.removeHandler(testHandler);
      restoreConsoleHandlers(logger, savedHandlers);

      testHandler.throwLoggedThrowable();
      testHandler.throwUncheckedLogMessages();
    }

    @Override
    public <T> T getOriginalValue() {
      throw new UnsupportedOperationException();
    }
  }
}
