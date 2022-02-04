// Copyright (c) 2017, 2022, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintStream;
import java.util.Optional;
import java.util.Properties;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.Semaphore;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import javax.annotation.Nonnull;

import oracle.kubernetes.operator.calls.UnrecoverableCallException;
import oracle.kubernetes.operator.helpers.ClientPool;
import oracle.kubernetes.operator.helpers.HealthCheckHelper;
import oracle.kubernetes.operator.helpers.KubernetesVersion;
import oracle.kubernetes.operator.helpers.PodHelper;
import oracle.kubernetes.operator.helpers.SemanticVersion;
import oracle.kubernetes.operator.logging.LoggingFacade;
import oracle.kubernetes.operator.logging.LoggingFactory;
import oracle.kubernetes.operator.logging.MessageKeys;
import oracle.kubernetes.operator.rest.RestWebhookConfigImpl;
import oracle.kubernetes.operator.rest.RestWebhookServer;
import oracle.kubernetes.operator.steps.InitializeInternalIdentityStep;
import oracle.kubernetes.operator.work.Component;
import oracle.kubernetes.operator.work.Container;
import oracle.kubernetes.operator.work.ContainerResolver;
import oracle.kubernetes.operator.work.Engine;
import oracle.kubernetes.operator.work.Fiber;
import oracle.kubernetes.operator.work.Fiber.CompletionCallback;
import oracle.kubernetes.operator.work.FiberGate;
import oracle.kubernetes.operator.work.Packet;
import oracle.kubernetes.operator.work.Step;
import oracle.kubernetes.operator.work.ThreadFactorySingleton;

import static oracle.kubernetes.operator.helpers.NamespaceHelper.getOperatorNamespace;

/** A Kubernetes Operator for WebLogic. */
public class ConversionWebhookMain {
  private static final LoggingFacade LOGGER = LoggingFactory.getLogger("Operator", "Operator");
  static final String GIT_BUILD_VERSION_KEY = "git.build.version";
  static final String GIT_BRANCH_KEY = "git.branch";
  static final String GIT_COMMIT_KEY = "git.commit.id.abbrev";
  static final String GIT_BUILD_TIME_KEY = "git.build.time";

  private static final Container container = new Container();
  private static final ThreadFactory threadFactory = new WrappedThreadFactory();
  private static final ScheduledExecutorService wrappedExecutorService =
      Engine.wrappedExecutorService("operator", container);
  private static final Semaphore shutdownSignal = new Semaphore(0);

  private final MainDelegate delegate;
  private boolean warnedOfCrdAbsence;
  private static final NextStepFactory NEXT_STEP_FACTORY = ConversionWebhookMain::createInitializeInternalIdentityStep;

  private static String getConfiguredServiceAccount() {
    return TuningParameters.getInstance().get("serviceaccount");
  }

  static {
    try {
      // suppress System.err since we catch all necessary output with Logger
      OutputStream output = new FileOutputStream("/dev/null");
      PrintStream nullOut = new PrintStream(output);
      System.setErr(nullOut);

      ClientPool.initialize(threadFactory);

      TuningParameters.initializeInstance(wrappedExecutorService, "/operator/config");
    } catch (IOException e) {
      LOGGER.warning(MessageKeys.EXCEPTION, e);
      throw new RuntimeException(e);
    }
  }

  static {
    container
        .getComponents()
        .put(
            ProcessingConstants.WEBHOOK_COMPONENT_NAME,
            Component.createFor(
                ScheduledExecutorService.class,
                wrappedExecutorService,
                TuningParameters.class,
                  TuningParameters.getInstance(),
                ThreadFactory.class,
                threadFactory));
  }

  static class MainDelegateImpl implements MainDelegate, DomainProcessorDelegate {

    private final String serviceAccountName = Optional.ofNullable(getConfiguredServiceAccount()).orElse("default");
    private final String principal = "system:serviceaccount:" + getOperatorNamespace() + ":" + serviceAccountName;

    private final String buildVersion;
    private final String operatorImpl;
    private final String operatorBuildTime;
    private final SemanticVersion productVersion;
    private final KubernetesVersion kubernetesVersion;
    private final Engine engine;
    private final DomainProcessor domainProcessor;
    private final DomainNamespaces domainNamespaces;

    public MainDelegateImpl(Properties buildProps, ScheduledExecutorService scheduledExecutorService) {
      buildVersion = getBuildVersion(buildProps);
      operatorImpl = getBranch(buildProps) + "." + getCommit(buildProps);
      operatorBuildTime = getBuildTime(buildProps);

      productVersion = new SemanticVersion(buildVersion);
      kubernetesVersion = HealthCheckHelper.performK8sVersionCheck();

      engine = new Engine(scheduledExecutorService);
      domainProcessor = new DomainProcessorImpl(this, productVersion);

      domainNamespaces = new DomainNamespaces(productVersion);

      PodHelper.setProductVersion(productVersion.toString());
    }

    private static String getBuildVersion(Properties buildProps) {
      return Optional.ofNullable(buildProps.getProperty(GIT_BUILD_VERSION_KEY)).orElse("1.0");
    }

    private static String getBranch(Properties buildProps) {
      return getBuildProperty(buildProps, GIT_BRANCH_KEY);
    }

    private static String getCommit(Properties buildProps) {
      return getBuildProperty(buildProps, GIT_COMMIT_KEY);
    }

    private static String getBuildTime(Properties buildProps) {
      return getBuildProperty(buildProps, GIT_BUILD_TIME_KEY);
    }

    private static String getBuildProperty(Properties buildProps, String key) {
      return Optional.ofNullable(buildProps.getProperty(key)).orElse("unknown");
    }

    private void logStartup() {
      ConversionWebhookMain.LOGGER.info(MessageKeys.OPERATOR_STARTED, buildVersion, operatorImpl, operatorBuildTime);
      Optional.ofNullable(TuningParameters.getInstance().getFeatureGates().getEnabledFeatures())
          .ifPresent(ef -> ConversionWebhookMain.LOGGER.info(MessageKeys.ENABLED_FEATURES, ef));
      ConversionWebhookMain.LOGGER.info(MessageKeys.OP_CONFIG_NAMESPACE, getOperatorNamespace());
      ConversionWebhookMain.LOGGER.info(MessageKeys.OP_CONFIG_SERVICE_ACCOUNT, serviceAccountName);
    }

    @Override
    public @Nonnull SemanticVersion getProductVersion() {
      return productVersion;
    }

    @Override
    public String getPrincipal() {
      return principal;
    }

    @Override
    public void runSteps(Step firstStep) {
      runSteps(new Packet(), firstStep, null);
    }

    @Override
    public void runSteps(Packet packet, Step firstStep) {
      runSteps(packet, firstStep, null);
    }

    @Override
    public void runSteps(Packet packet, Step firstStep, Runnable completionAction) {
      Fiber f = engine.createFiber();
      f.start(firstStep, packet, andThenDo(completionAction));
    }

    @Override
    public DomainProcessor getDomainProcessor() {
      return domainProcessor;
    }

    @Override
    public DomainNamespaces getDomainNamespaces() {
      return domainNamespaces;
    }

    @Override
    public KubernetesVersion getKubernetesVersion() {
      return kubernetesVersion;
    }

    @Override
    public PodAwaiterStepFactory getPodAwaiterStepFactory(String namespace) {
      return domainNamespaces.getPodWatcher(namespace);
    }

    @Override
    public JobAwaiterStepFactory getJobAwaiterStepFactory(String namespace) {
      return domainNamespaces.getJobWatcher(namespace);
    }

    @Override
    public boolean isNamespaceRunning(String namespace) {
      return !domainNamespaces.isStopping(namespace).get();
    }

    @Override
    public FiberGate createFiberGate() {
      return new FiberGate(engine);
    }

    @Override
    public ScheduledFuture<?> scheduleWithFixedDelay(Runnable command, long initialDelay, long delay, TimeUnit unit) {
      return engine.getExecutor().scheduleWithFixedDelay(command, initialDelay, delay, unit);
    }
  }

  /**
   * Entry point.
   *
   * @param args none, ignored
   */
  public static void main(String[] args) {
    ConversionWebhookMain main = createMain(getBuildProperties());

    try {
      main.startOperator(main::completeBegin);

      // now we just wait until the pod is terminated
      main.waitForDeath();

      // stop the REST server
      stopRestServer();
    } finally {
      LOGGER.info(MessageKeys.OPERATOR_SHUTTING_DOWN);
    }
  }

  /**
   * Return the build properties generated by the git-commit-id-plugin.
   */
  static Properties getBuildProperties() {
    try (final InputStream stream = ConversionWebhookMain.class.getResourceAsStream("/version.properties")) {
      Properties buildProps = new Properties();
      buildProps.load(stream);
      return buildProps;
    } catch (IOException e) {
      LOGGER.warning(MessageKeys.EXCEPTION, e);
      return null;
    }
  }

  static ConversionWebhookMain createMain(Properties buildProps) {
    final MainDelegateImpl delegate = new MainDelegateImpl(buildProps, wrappedExecutorService);

    delegate.logStartup();
    return new ConversionWebhookMain(delegate);
  }

  ConversionWebhookMain(MainDelegate delegate) {
    this.delegate = delegate;
  }

  void startOperator(Runnable completionAction) {
    try {
      delegate.runSteps(new Packet(), createStartupSteps(), completionAction);
    } catch (Throwable e) {
      LOGGER.warning(MessageKeys.EXCEPTION, e);
    }
  }

  private Step createStartupSteps() {

    //return NEXT_STEP_FACTORY.createInternalInitializationStep(createDomainCrdRecheckSteps());
    return NEXT_STEP_FACTORY.createInternalInitializationStep(null);
  }

  private static Step createInitializeInternalIdentityStep(Step next) {
    return new InitializeInternalIdentityStep(next);
  }

  private void completeBegin() {
    try {
      // start the REST server
      //startRestServer(delegate.getPrincipal());
      startRestWebhookServer();

      // start periodic retry and recheck
      //int recheckInterval = TuningParameters.getInstance().getMainTuning().domainNamespaceRecheckIntervalSeconds;
      //delegate.scheduleWithFixedDelay(recheckCrd(), recheckInterval, recheckInterval, TimeUnit.SECONDS);

      markReadyAndStartLivenessThread();

    } catch (Throwable e) {
      LOGGER.warning(MessageKeys.EXCEPTION, e);
    }
  }

  private static NullCompletionCallback andThenDo(Runnable completionAction) {
    return new NullCompletionCallback(completionAction);
  }

  private void startRestWebhookServer()
          throws Exception {
    RestWebhookServer.create(new RestWebhookConfigImpl());
    RestWebhookServer.getInstance().start(container);
  }

  // -----------------------------------------------------------------------------
  //
  // Below this point are methods that are called primarily from watch handlers,
  // after watch events are received.
  //
  // -----------------------------------------------------------------------------

  private static void stopRestServer() {
    RestWebhookServer.getInstance().stop();
    RestWebhookServer.destroy();
  }

  private static void markReadyAndStartLivenessThread() {
    try {
      WebhookReady.create();

      LOGGER.info(MessageKeys.STARTING_LIVENESS_THREAD);
      // every five seconds we need to update the last modified time on the liveness file
      wrappedExecutorService.scheduleWithFixedDelay(new WebhookLiveness(), 5, 5, TimeUnit.SECONDS);
    } catch (IOException io) {
      LOGGER.severe(MessageKeys.EXCEPTION, io);
    }
  }

  private void waitForDeath() {
    Runtime.getRuntime().addShutdownHook(new Thread(shutdownSignal::release));

    try {
      shutdownSignal.acquire();
    } catch (InterruptedException ignore) {
      Thread.currentThread().interrupt();
    }

  }

  private static class WrappedThreadFactory implements ThreadFactory {
    private final ThreadFactory delegate = ThreadFactorySingleton.getInstance();

    @Override
    public Thread newThread(@Nonnull Runnable r) {
      return delegate.newThread(
          () -> {
            ContainerResolver.getDefault().enterContainer(container);
            r.run();
          });
    }
  }

  private static class NullCompletionCallback implements CompletionCallback {
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
      if (throwable instanceof UnrecoverableCallException) {
        ((UnrecoverableCallException) throwable).log();
      } else {
        LOGGER.severe(MessageKeys.EXCEPTION, throwable);
      }
    }
  }

  // an interface to provide a hook for unit testing.
  interface NextStepFactory {
    Step createInternalInitializationStep(Step next);
  }

}
