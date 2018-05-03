// Copyright 2018, Oracle Corporation and/or its affiliates.  All rights reserved.
// Licensed under the Universal Permissive License v 1.0 as shown at http://oss.oracle.com/licenses/upl.

package oracle.kubernetes;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.ConsoleHandler;
import java.util.logging.Handler;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import com.meterware.simplestub.Memento;

import oracle.kubernetes.operator.logging.LoggingFactory;

import org.apache.commons.exec.CommandLine;
import org.apache.commons.exec.DefaultExecutor;

import static com.meterware.simplestub.Stub.createStub;

public class TestUtils {
  private static Boolean kubernetesStatus;

  /**
   * Returns true if Kubernetes-dependent tests should run
   */
  public static boolean isKubernetesAvailable() { // assume it is available when running on Linux
    if (kubernetesStatus == null)
      kubernetesStatus = checkKubernetes();
    return kubernetesStatus;
  }

  private static Boolean checkKubernetes() {
    PrintStream savedOut = System.out;
    System.setOut(new PrintStream(new ByteArrayOutputStream()));
    try {
      CommandLine cmdLine = CommandLine.parse("kubectl cluster-info dump");
      DefaultExecutor executor = new DefaultExecutor();
      executor.execute(cmdLine);
      return true;
    } catch (IOException e) {
      return false;
    } finally {
      System.setOut(savedOut);
    }
  }

  /**
   * Returns true if the current system is running Linux
   * @return a boolean indicating the operating system match
   */
  public static boolean isLinux() {
    return System.getProperty("os.name").toLowerCase().contains("linux");
  }

  /**
   * Removes the console handlers from the specified logger, in order to silence them during a test.
   *
   * @return a collection of the removed handlers
   */
  public static ExceptionFilteringMemento silenceOperatorLogger() {
    Logger logger = LoggingFactory.getLogger("Operator", "Operator").getUnderlyingLogger();
    List<Handler> savedHandlers = new ArrayList<>();
    for (Handler handler : logger.getHandlers()) {
      if (handler instanceof ConsoleHandler) {
        savedHandlers.add(handler);
      }
    }

    for (Handler handler : savedHandlers)
      logger.removeHandler(handler);

    TestLogHandler testHandler = createStub(TestLogHandler.class);
    logger.addHandler(testHandler);

    return new ConsoleHandlerMemento(logger, testHandler, savedHandlers);
  }

  abstract static class TestLogHandler extends Handler {
    private Throwable throwable;
    private List<Throwable> ignoredExceptions = new ArrayList<>();

    @Override
    public void publish(LogRecord record) {
      if (record.getThrown() != null && !ignoredExceptions.contains(record.getThrown()))
        throwable = record.getThrown();
    }

    void throwLoggedThrowable() {
      if (throwable == null) return;

      if (throwable instanceof Error) throw (Error) throwable;
      if (throwable instanceof RuntimeException) throw (RuntimeException) throwable;
      throw new RuntimeException(throwable);
    }

    void ignoreLoggedException(Throwable t) {
      ignoredExceptions.add(t);
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
    for (Handler handler : savedHandlers)
      logger.removeHandler(handler);
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

  public interface ExceptionFilteringMemento extends Memento {
    ExceptionFilteringMemento ignoringLoggedExceptions(Throwable... throwables);
  }

  private static class ConsoleHandlerMemento implements ExceptionFilteringMemento {
    private Logger logger;
    private TestLogHandler testHandler;
    private List<Handler> savedHandlers;

    ConsoleHandlerMemento(Logger logger, TestLogHandler testHandler, List<Handler> savedHandlers) {
      this.logger = logger;
      this.testHandler = testHandler;
      this.savedHandlers = savedHandlers;
    }

    @Override
    public ExceptionFilteringMemento ignoringLoggedExceptions(Throwable... throwables) {
      for (Throwable throwable : throwables)
        testHandler.ignoreLoggedException(throwable);
      return this;
    }

    @Override
    public void revert() {
      logger.removeHandler(testHandler);
      restoreConsoleHandlers(logger, savedHandlers);

      testHandler.throwLoggedThrowable();
    }

    @Override
    public <T> T getOriginalValue() {
      throw new UnsupportedOperationException();
    }
  }
}
