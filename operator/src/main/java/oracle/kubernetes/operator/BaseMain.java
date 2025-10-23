// Copyright (c) 2022, 2025, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintStream;
import java.nio.file.Files;
import java.security.KeyManagementException;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.UnrecoverableKeyException;
import java.security.cert.CertificateException;
import java.security.spec.InvalidKeySpecException;
import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.Semaphore;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.ConsoleHandler;
import java.util.logging.FileHandler;
import java.util.logging.Level;
import java.util.logging.Logger;

import oracle.kubernetes.common.logging.MessageKeys;
import oracle.kubernetes.operator.helpers.HelmAccess;
import oracle.kubernetes.operator.http.BaseServer;
import oracle.kubernetes.operator.http.metrics.MetricsServer;
import oracle.kubernetes.operator.logging.LoggingContext;
import oracle.kubernetes.operator.logging.LoggingFacade;
import oracle.kubernetes.operator.logging.LoggingFactory;
import oracle.kubernetes.operator.tuning.TuningParameters;
import oracle.kubernetes.operator.utils.PathSupport;
import oracle.kubernetes.operator.work.Cancellable;
import oracle.kubernetes.operator.work.Fiber.CompletionCallback;
import oracle.kubernetes.operator.work.Packet;
import oracle.kubernetes.operator.work.Step;
import oracle.kubernetes.operator.work.VirtualScheduledExecutorService;
import oracle.kubernetes.utils.SystemClock;

/** An abstract base main class for the operator and the webhook. */
public abstract class BaseMain {

  static {
    try {
      Map<String, String> env  = System.getenv();
      String loggingLevel = env.get("JAVA_LOGGING_LEVEL");
      if (loggingLevel != null) {
        Level level = Level.parse(loggingLevel);

        Logger rootLogger = Logger.getLogger("");
        rootLogger.setLevel(Level.WARNING);

        // Console Handler
        ConsoleHandler consoleHandler = new ConsoleHandler();
        consoleHandler.setLevel(level);
        consoleHandler.setFormatter(new oracle.kubernetes.operator.logging.OperatorLoggingFormatter());
        rootLogger.addHandler(consoleHandler);

        String logDir = env.get("OPERATOR_LOGDIR");
        if (logDir != null) {
          Files.createDirectories(PathSupport.getPath(new File(logDir)));

          // File handler
          String pattern = logDir + "/operator%g.log";
          int limit = Integer.parseInt(env.getOrDefault("JAVA_LOGGING_MAXSIZE", "20000000"));
          int count = Integer.parseInt(env.getOrDefault("JAVA_LOGGING_COUNT", "10"));
          FileHandler fileHandler = new FileHandler(pattern, limit, count);
          fileHandler.setLevel(level);
          fileHandler.setFormatter(new oracle.kubernetes.operator.logging.OperatorLoggingFormatter());
          rootLogger.addHandler(fileHandler);
        }

        Logger logger = Logger.getLogger("Operator", "Operator");
        logger.setLevel(level);
      }

    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  static final LoggingFacade LOGGER = LoggingFactory.getLogger("Operator", "Operator");

  static final String GIT_BUILD_VERSION_KEY = "git.build.version";
  static final String GIT_BRANCH_KEY = "git.branch";
  static final String GIT_COMMIT_KEY = "git.commit.id.abbrev";
  static final String GIT_BUILD_TIME_KEY = "git.build.time";

  static final ThreadFactory threadFactory = Thread.ofVirtual().factory();
  static final ScheduledExecutorService executor = new VirtualScheduledExecutorService();
  static final AtomicReference<OffsetDateTime> lastFullRecheck =
      new AtomicReference<>(SystemClock.now());
  static final Semaphore shutdownSignal = new Semaphore(0);

  static final File deploymentHome;
  final CoreDelegate delegate;

  private final AtomicReference<BaseServer> metricsServer = new AtomicReference<>();

  static {
    try {
      // suppress System.err since we catch all necessary output with Logger
      OutputStream output = new FileOutputStream("/dev/null");
      PrintStream nullOut = new PrintStream(output);
      System.setErr(nullOut);

      // Simplify debugging the operator by allowing the setting of the operator
      // top-level directory using either an env variable or a property. In the normal,
      // container-based use case these values won't be set and the operator will with the
      // /operator directory.
      String deploymentHomeLoc = HelmAccess.getHelmVariable("DEPLOYMENT_HOME");
      if (deploymentHomeLoc == null) {
        deploymentHomeLoc = System.getProperty("deploymentHome", "/deployment");
      }
      deploymentHome = new File(deploymentHomeLoc);

      TuningParameters.initializeInstance(executor, new File(deploymentHome, "config"));
    } catch (IOException e) {
      LOGGER.warning(MessageKeys.EXCEPTION, e);
      throw new RuntimeException(e);
    }
  }

  /**
   * Return the build properties generated by the git-commit-id-plugin.
   */
  static Properties getBuildProperties() {
    try (final InputStream stream = BaseMain.class.getResourceAsStream("/version.properties")) {
      Properties buildProps = new Properties();
      buildProps.load(stream);
      return buildProps;
    } catch (IOException e) {
      LOGGER.warning(MessageKeys.EXCEPTION, e);
      return null;
    }
  }

  BaseMain(CoreDelegate delegate) {
    this.delegate = delegate;
  }

  void startDeployment(Runnable completionAction) {
    try {
      delegate.runSteps(new Packet(), createStartupSteps(), completionAction);
    } catch (Throwable e) {
      LOGGER.warning(MessageKeys.EXCEPTION, e);
    }
  }

  void stopDeployment(Runnable completionAction) {
    Step shutdownSteps = createShutdownSteps();
    if (shutdownSteps != null) {
      try {
        delegate.runSteps(new Packet(), shutdownSteps, new ReleaseShutdownSignalRunnable(completionAction));
        acquireShutdownSignal();
      } catch (Throwable e) {
        LOGGER.warning(MessageKeys.EXCEPTION, e);
      }
    } else if (completionAction != null) {
      completionAction.run();
    }
  }

  private class ReleaseShutdownSignalRunnable implements Runnable {
    final Runnable inner;

    ReleaseShutdownSignalRunnable(Runnable inner) {
      this.inner = inner;
    }

    @Override
    public void run() {
      if (inner != null) {
        inner.run();
      }
      releaseShutdownSignal();
    }
  }

  void markReadyAndStartLivenessThread(Collection<Cancellable> futures) {
    try {
      new DeploymentReady(delegate).create();

      logStartingLivenessMessage();
      // every five seconds we need to update the last modified time on the liveness file
      delegate.scheduleWithFixedDelay(new DeploymentLiveness(futures, delegate), 5, 5, TimeUnit.SECONDS);
    } catch (IOException io) {
      LOGGER.severe(MessageKeys.EXCEPTION, io);
    }
  }

  void startMetricsServer() throws UnrecoverableKeyException, CertificateException, IOException,
      NoSuchAlgorithmException, KeyStoreException, InvalidKeySpecException, KeyManagementException {
    startMetricsServer(delegate.getMetricsPort());
  }

  // for test
  void startMetricsServer(int port) throws UnrecoverableKeyException, CertificateException,
      IOException, NoSuchAlgorithmException, KeyStoreException, InvalidKeySpecException, KeyManagementException {
    BaseServer value = new MetricsServer(port);
    metricsServer.set(value);
    value.start();
  }

  // for test
  BaseServer getMetricsServer() {
    return metricsServer.get();
  }

  void stopMetricsServer() {
    Optional.ofNullable(metricsServer.getAndSet(null)).ifPresent(BaseServer::stop);
  }

  abstract Step createStartupSteps();

  Step createShutdownSteps() {
    return null;
  }

  abstract void logStartingLivenessMessage();

  void stopAllWatchers() {
    // no-op
  }

  private void acquireShutdownSignal() {
    try {
      shutdownSignal.acquire();
    } catch (InterruptedException ignore) {
      Thread.currentThread().interrupt();
    }
  }

  private void releaseShutdownSignal() {
    shutdownSignal.release();
  }

  // For test
  int getShutdownSignalAvailablePermits() {
    return shutdownSignal.availablePermits();
  }

  void waitForDeath() {
    Runtime.getRuntime().addShutdownHook(new Thread(this::releaseShutdownSignal));
    scheduleCheckForShutdownMarker();

    acquireShutdownSignal();

    executor.shutdown();
    stopAllWatchers();
  }

  void scheduleCheckForShutdownMarker() {
    delegate.scheduleWithFixedDelay(
        () -> {
          File marker = new File(delegate.getDeploymentHome(), CoreDelegate.SHUTDOWN_MARKER_NAME);
          if (isFileExists(marker)) {
            releaseShutdownSignal();
          }
        }, 5, 2, TimeUnit.SECONDS);
  }

  private static boolean isFileExists(File file) {
    return Files.isRegularFile(PathSupport.getPath(file));
  }

  static Packet createPacketWithLoggingContext(String ns) {
    Packet packet = new Packet();
    packet.put(LoggingContext.LOGGING_CONTEXT_KEY, new LoggingContext().namespace(ns));
    return packet;
  }

  static class NullCompletionCallback implements CompletionCallback {
    private final Runnable completionAction;

    NullCompletionCallback(Runnable completionAction) {
      this.completionAction = completionAction;
    }

    @Override
    public void onCompletion(Packet packet) {
      if (completionAction != null) {
        completionAction.run();
      }
    }

    @Override
    public void onThrowable(Packet packet, Throwable throwable) {
      LOGGER.severe(MessageKeys.EXCEPTION, throwable);
    }
  }
}
