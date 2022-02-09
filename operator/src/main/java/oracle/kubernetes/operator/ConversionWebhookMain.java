// Copyright (c) 2022, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator;

import java.io.File;
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

import oracle.kubernetes.operator.calls.CallResponse;
import oracle.kubernetes.operator.calls.UnrecoverableCallException;
import oracle.kubernetes.operator.helpers.CallBuilder;
import oracle.kubernetes.operator.helpers.ClientPool;
import oracle.kubernetes.operator.helpers.CrdHelper;
import oracle.kubernetes.operator.helpers.HealthCheckHelper;
import oracle.kubernetes.operator.helpers.KubernetesVersion;
import oracle.kubernetes.operator.helpers.PodHelper;
import oracle.kubernetes.operator.helpers.SemanticVersion;
import oracle.kubernetes.operator.logging.LoggingFacade;
import oracle.kubernetes.operator.logging.LoggingFactory;
import oracle.kubernetes.operator.logging.MessageKeys;
import oracle.kubernetes.operator.rest.WebhookRestConfigImpl;
import oracle.kubernetes.operator.rest.WebhookRestServer;
import oracle.kubernetes.operator.steps.DefaultResponseStep;
import oracle.kubernetes.operator.steps.InitializeIdentityStep;
import oracle.kubernetes.operator.work.Component;
import oracle.kubernetes.operator.work.Container;
import oracle.kubernetes.operator.work.ContainerResolver;
import oracle.kubernetes.operator.work.Engine;
import oracle.kubernetes.operator.work.Fiber;
import oracle.kubernetes.operator.work.Fiber.CompletionCallback;
import oracle.kubernetes.operator.work.NextAction;
import oracle.kubernetes.operator.work.Packet;
import oracle.kubernetes.operator.work.Step;
import oracle.kubernetes.operator.work.ThreadFactorySingleton;
import oracle.kubernetes.weblogic.domain.model.DomainList;

import static oracle.kubernetes.operator.helpers.NamespaceHelper.getOperatorNamespace;
import static oracle.kubernetes.operator.utils.Certificates.OPERATOR_DIR;
import static oracle.kubernetes.operator.utils.Certificates.WEBHOOK_CERTIFICATE;
import static oracle.kubernetes.operator.utils.Certificates.WEBHOOK_CERTIFICATE_KEY;

/** A Conversion Webhook for WebLogic Kubernetes Operator. */
public class ConversionWebhookMain {
  private static final LoggingFacade LOGGER = LoggingFactory.getLogger("Operator", "Operator");
  static final String GIT_BUILD_VERSION_KEY = "git.build.version";
  static final String GIT_BRANCH_KEY = "git.branch";
  static final String GIT_COMMIT_KEY = "git.commit.id.abbrev";
  static final String GIT_BUILD_TIME_KEY = "git.build.time";

  private static final Container container = new Container();
  private static final ThreadFactory threadFactory = new WrappedThreadFactory();
  private static final ScheduledExecutorService wrappedExecutorService =
      Engine.wrappedExecutorService("webhook", container);
  private static final Semaphore shutdownSignal = new Semaphore(0);

  private final ConversionWebhookMainDelegate delegate;
  private boolean warnedOfCrdAbsence;
  private static final NextStepFactory NEXT_STEP_FACTORY = ConversionWebhookMain::createInitializeWebhookIdentityStep;

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

  static class ConversionWebhookMainDelegateImpl implements ConversionWebhookMainDelegate {

    private final String buildVersion;
    private final String conversionWebhookImpl;
    private final String conversionWebhookBuildTime;
    private final SemanticVersion productVersion;
    private final KubernetesVersion kubernetesVersion;
    private final Engine engine;

    public ConversionWebhookMainDelegateImpl(Properties buildProps, ScheduledExecutorService scheduledExecutorService) {
      buildVersion = getBuildVersion(buildProps);
      conversionWebhookImpl = getBranch(buildProps) + "." + getCommit(buildProps);
      conversionWebhookBuildTime = getBuildTime(buildProps);

      productVersion = new SemanticVersion(buildVersion);
      kubernetesVersion = HealthCheckHelper.performK8sVersionCheck();

      engine = new Engine(scheduledExecutorService);

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
      ConversionWebhookMain.LOGGER.info(MessageKeys.CONVERSION_WEBHOOK_STARTED, buildVersion,
              conversionWebhookImpl, conversionWebhookBuildTime);
      ConversionWebhookMain.LOGGER.info(MessageKeys.WEBHOOK_CONFIG_NAMESPACE, getOperatorNamespace());
    }

    @Override
    public @Nonnull SemanticVersion getProductVersion() {
      return productVersion;
    }

    @Override
    public void runSteps(Step firstStep) {
      runSteps(new Packet(), firstStep, null);
    }

    @Override
    public void runSteps(Packet packet, Step firstStep, Runnable completionAction) {
      Fiber f = engine.createFiber();
      f.start(firstStep, packet, andThenDo(completionAction));
    }

    @Override
    public KubernetesVersion getKubernetesVersion() {
      return kubernetesVersion;
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
      main.startConversionWebhook(main::completeBegin);

      // now we just wait until the pod is terminated
      main.waitForDeath();

      // stop the REST server
      stopWebhookRestServer();
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
    final ConversionWebhookMainDelegateImpl delegate =
            new ConversionWebhookMainDelegateImpl(buildProps, wrappedExecutorService);

    delegate.logStartup();
    return new ConversionWebhookMain(delegate);
  }

  ConversionWebhookMain(ConversionWebhookMainDelegate delegate) {
    this.delegate = delegate;
  }

  void startConversionWebhook(Runnable completionAction) {
    try {
      delegate.runSteps(new Packet(), createStartupSteps(), completionAction);
    } catch (Throwable e) {
      LOGGER.warning(MessageKeys.EXCEPTION, e);
    }
  }

  private Step createStartupSteps() {
    return NEXT_STEP_FACTORY.createInitializationStep(
            CrdHelper.createDomainCrdStep(delegate.getKubernetesVersion(), delegate.getProductVersion()));
  }

  private static Step createInitializeWebhookIdentityStep(Step next) {
    return new InitializeWebhookIdentityStep(next);
  }

  private void completeBegin() {
    try {
      // start the REST server
      startRestWebhookServer();

      // start periodic retry and recheck
      int recheckInterval = TuningParameters.getInstance().getMainTuning().domainNamespaceRecheckIntervalSeconds;
      delegate.scheduleWithFixedDelay(recheckCrd(), recheckInterval, recheckInterval, TimeUnit.SECONDS);

      markReadyAndStartLivenessThread();

    } catch (Throwable e) {
      LOGGER.warning(MessageKeys.EXCEPTION, e);
    }
  }

  Runnable recheckCrd() {
    return () -> delegate.runSteps(createCRDRecheckSteps());
  }

  Step createCRDRecheckSteps() {
    return Step.chain(CrdHelper.createDomainCrdStep(delegate.getKubernetesVersion(), delegate.getProductVersion()),
            createCRDPresenceCheck());
  }

  // Returns a step that verifies the presence of an installed domain CRD. It does this by attempting to list the
  // domains in the operator's namespace. That should succeed (although usually returning an empty list)
  // if the CRD is present.
  private Step createCRDPresenceCheck() {
    return new CallBuilder().listDomainAsync(getOperatorNamespace(), new CrdPresenceResponseStep());
  }

  // on failure, aborts the processing.
  private class CrdPresenceResponseStep extends DefaultResponseStep<DomainList> {

    @Override
    public NextAction onSuccess(Packet packet, CallResponse<DomainList> callResponse) {
      warnedOfCrdAbsence = false;
      return super.onSuccess(packet, callResponse);
    }

    @Override
    public NextAction onFailure(Packet packet, CallResponse<DomainList> callResponse) {
      if (!warnedOfCrdAbsence) {
        LOGGER.severe(MessageKeys.CRD_NOT_INSTALLED);
        warnedOfCrdAbsence = true;
      }
      return doNext(null, packet);
    }
  }

  private static NullCompletionCallback andThenDo(Runnable completionAction) {
    return new NullCompletionCallback(completionAction);
  }

  private void startRestWebhookServer()
          throws Exception {
    WebhookRestServer.create(new WebhookRestConfigImpl());
    WebhookRestServer.getInstance().start(container);
  }

  // -----------------------------------------------------------------------------
  //
  // Below this point are methods that are called primarily from watch handlers,
  // after watch events are received.
  //
  // -----------------------------------------------------------------------------

  private static void stopWebhookRestServer() {
    WebhookRestServer.getInstance().stop();
    WebhookRestServer.destroy();
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
    Step createInitializationStep(Step next);
  }

  private static class InitializeWebhookIdentityStep extends InitializeIdentityStep {

    private static final File webhookCertFile = new File(OPERATOR_DIR + "/config/webhookCert");
    private static final File webhookKeyFile = new File(OPERATOR_DIR + "/secrets/webhookKey");

    public InitializeWebhookIdentityStep(Step next) {
      super(next, webhookCertFile, webhookKeyFile, WEBHOOK_CERTIFICATE, WEBHOOK_CERTIFICATE_KEY);
    }
  }
}