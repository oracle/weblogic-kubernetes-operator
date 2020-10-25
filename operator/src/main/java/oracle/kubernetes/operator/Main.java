// Copyright (c) 2017, 2020, Oracle Corporation and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.Semaphore;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.stream.Collectors;
import javax.annotation.Nonnull;

import io.kubernetes.client.openapi.ApiException;
import io.kubernetes.client.openapi.models.V1Namespace;
import io.kubernetes.client.openapi.models.V1NamespaceList;
import io.kubernetes.client.openapi.models.V1ObjectMeta;
import io.kubernetes.client.util.Watch;
import oracle.kubernetes.operator.calls.CallResponse;
import oracle.kubernetes.operator.calls.FailureStatusSourceException;
import oracle.kubernetes.operator.helpers.CallBuilder;
import oracle.kubernetes.operator.helpers.ClientPool;
import oracle.kubernetes.operator.helpers.CrdHelper;
import oracle.kubernetes.operator.helpers.HealthCheckHelper;
import oracle.kubernetes.operator.helpers.KubernetesUtils;
import oracle.kubernetes.operator.helpers.KubernetesVersion;
import oracle.kubernetes.operator.helpers.SemanticVersion;
import oracle.kubernetes.operator.logging.LoggingContext;
import oracle.kubernetes.operator.logging.LoggingFacade;
import oracle.kubernetes.operator.logging.LoggingFactory;
import oracle.kubernetes.operator.logging.MessageKeys;
import oracle.kubernetes.operator.rest.RestConfigImpl;
import oracle.kubernetes.operator.rest.RestServer;
import oracle.kubernetes.operator.steps.DefaultResponseStep;
import oracle.kubernetes.operator.work.Component;
import oracle.kubernetes.operator.work.Container;
import oracle.kubernetes.operator.work.ContainerResolver;
import oracle.kubernetes.operator.work.Engine;
import oracle.kubernetes.operator.work.Fiber;
import oracle.kubernetes.operator.work.Fiber.CompletionCallback;
import oracle.kubernetes.operator.work.FiberGate;
import oracle.kubernetes.operator.work.NextAction;
import oracle.kubernetes.operator.work.Packet;
import oracle.kubernetes.operator.work.Step;
import oracle.kubernetes.operator.work.ThreadFactorySingleton;
import org.apache.commons.lang.StringUtils;
import org.joda.time.DateTime;

import static oracle.kubernetes.operator.helpers.NamespaceHelper.getOperatorNamespace;

/** A Kubernetes Operator for WebLogic. */
public class Main {
  private static final LoggingFacade LOGGER = LoggingFactory.getLogger("Operator", "Operator");

  private static final Container container = new Container();
  private static final ThreadFactory threadFactory = new WrappedThreadFactory();
  private static final ScheduledExecutorService wrappedExecutorService =
      Engine.wrappedExecutorService("operator", container);
  private static final AtomicReference<DateTime> lastFullRecheck =
      new AtomicReference<>(DateTime.now());
  private static final DomainProcessor processor = new DomainProcessorImpl(new DomainProcessorDelegateImpl());
  private static final Semaphore shutdownSignal = new Semaphore(0);
  private static final Engine engine = new Engine(wrappedExecutorService);

  private static KubernetesVersion version = null;

  private final MainDelegate delegate;
  private NamespaceWatcher namespaceWatcher;

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
            ProcessingConstants.MAIN_COMPONENT_NAME,
            Component.createFor(
                ScheduledExecutorService.class,
                wrappedExecutorService,
                TuningParameters.class,
                  TuningParameters.getInstance(),
                ThreadFactory.class,
                threadFactory));
  }

  static class MainDelegateImpl implements MainDelegate {

    private final String serviceAccountName = Optional.ofNullable(getConfiguredServiceAccount()).orElse("default");
    private final String principal = "system:serviceaccount:" + getOperatorNamespace() + ":" + serviceAccountName;

    private final String buildVersion;
    private final String operatorImpl;
    private final String operatorBuildTime;
    private final SemanticVersion productVersion;
    private final KubernetesVersion kubernetesVersion;
    private final Engine engine;

    public MainDelegateImpl(Properties buildProps, ScheduledExecutorService scheduledExecutorService) {
      buildVersion = buildProps.getProperty("git.build.version");
      operatorImpl = buildProps.getProperty("git.branch") + "." + buildProps.getProperty("git.commit.id.abbrev");
      operatorBuildTime = buildProps.getProperty("git.build.time");

      productVersion = (buildVersion == null) ? null : new SemanticVersion(buildVersion);
      kubernetesVersion = HealthCheckHelper.performK8sVersionCheck();

      engine = new Engine(scheduledExecutorService);
    }

    @Override
    public void logStartup(LoggingFacade loggingFacade) {
      loggingFacade.info(MessageKeys.OPERATOR_STARTED, buildVersion, operatorImpl, operatorBuildTime);
      loggingFacade.info(MessageKeys.OP_CONFIG_NAMESPACE, getOperatorNamespace());
      loggingFacade.info(MessageKeys.OP_CONFIG_SERVICE_ACCOUNT, serviceAccountName);
      Optional.ofNullable(Namespaces.getConfiguredDomainNamespaces())
            .ifPresent(strings -> logConfiguredNamespaces(loggingFacade, strings));
    }

    private void logConfiguredNamespaces(LoggingFacade loggingFacade, Collection<String> configuredDomainNamespaces) {
      loggingFacade.info(MessageKeys.OP_CONFIG_DOMAIN_NAMESPACES, StringUtils.join(configuredDomainNamespaces, ", "));
    }

    @Override
    public SemanticVersion getProductVersion() {
      return productVersion;
    }

    @Override
    public String getServiceAccountName() {
      return serviceAccountName;
    }

    @Override
    public String getPrincipal() {
      return principal;
    }

    @Override
    public Engine getEngine() {
      return engine;
    }

    @Override
    public void runSteps(Packet packet, Step firstStep, Runnable completionAction) {
      Fiber f = engine.createFiber();
      f.start(firstStep, packet, andThenDo(completionAction));
    }

    @Override
    public DomainProcessor getProcessor() {
      return processor;
    }

    @Override
    public KubernetesVersion getKubernetesVersion() {
      return kubernetesVersion;
    }
  }

  static class StartupProblemVisitor implements NamespaceStrategyVisitor<Boolean> {

    @Override
    public Boolean getDedicatedStrategySelection() {
      try {
        new CallBuilder().listDomain(getOperatorNamespace());
        return false;
      } catch (ApiException e) {
        LOGGER.severe(MessageKeys.CRD_NOT_INSTALLED);
        return true;
      }
    }

    @Override
    public Boolean getDefaultSelection() {
      return false;
    }
  }

  /**
   * Entry point.
   *
   * @param args none, ignored
   */
  public static void main(String[] args) {
    Main main = createMain();
    if (main == null) {
      System.exit(0);
    }

    try {
      main.startOperator(main::completeBegin);

      // now we just wait until the pod is terminated
      waitForDeath();

      // stop the REST server
      stopRestServer();
    } finally {
      LOGGER.info(MessageKeys.OPERATOR_SHUTTING_DOWN);
    }
  }

  private static Main createMain() {
    try (final InputStream stream = Main.class.getResourceAsStream("/version.properties")) {
      Properties buildProps = new Properties();
      buildProps.load(stream);

      return createMain(buildProps);
    } catch (IOException e) {
      LOGGER.warning(MessageKeys.EXCEPTION, e);
      return null;
    }
  }

  static Main createMain(Properties buildProps) {
    final MainDelegateImpl delegate = new MainDelegateImpl(buildProps, wrappedExecutorService);

    if (delegate.getProductVersion() == null) {
      return null;
    } else if (Namespaces.getSelection(new StartupProblemVisitor())) {
      return null;
    } else {
      delegate.logStartup(LOGGER);
      return new Main(delegate);
    }
  }

  Main(MainDelegate delegate) {
    this.delegate = delegate;
  }

  void startOperator(Runnable completionAction) {
    try {
      version = delegate.getKubernetesVersion();
      delegate.runSteps(new Packet(), createStartupSteps(), completionAction);
    } catch (Throwable e) {
      LOGGER.warning(MessageKeys.EXCEPTION, e);
    }
  }

  private Step createStartupSteps() {
    return Step.chain(
          new CallBuilder().listNamespaceAsync(new StartNamespaceWatcherStep()),
          createDomainRecheckSteps(DateTime.now()));
  }

  private class StartNamespaceWatcherStep extends DefaultResponseStep<V1NamespaceList> {

    @Override
    public NextAction onSuccess(Packet packet, CallResponse<V1NamespaceList> callResponse) {
      namespaceWatcher = createNamespaceWatcher(KubernetesUtils.getResourceVersion(callResponse.getResult()));
      return doNext(packet);
    }
  }

  private void completeBegin() {
    try {
      // start the REST server
      startRestServer(delegate.getPrincipal());

      // start periodic retry and recheck
      int recheckInterval = TuningParameters.getInstance().getMainTuning().domainNamespaceRecheckIntervalSeconds;
      delegate.getEngine()
          .getExecutor()
          .scheduleWithFixedDelay(
              recheckDomains(), recheckInterval, recheckInterval, TimeUnit.SECONDS);

      markReadyAndStartLivenessThread();

    } catch (Throwable e) {
      LOGGER.warning(MessageKeys.EXCEPTION, e);
    }
  }

  NamespaceWatcher getNamespaceWatcher() {
    return namespaceWatcher;
  }

  private static void runSteps(Step firstStep) {
    runSteps(new Packet(), firstStep, null);
  }

  @SuppressWarnings("SameParameterValue") // TODO reg - start here for the next refactoring
  private static void runSteps(Packet packet, Step firstStep, Runnable completionAction) {
    Fiber f = engine.createFiber();
    f.start(firstStep, packet, andThenDo(completionAction));
  }

  private static NullCompletionCallback andThenDo(Runnable completionAction) {
    return new NullCompletionCallback(completionAction);
  }

  Runnable recheckDomains() {
    return () -> delegate.runSteps(createDomainRecheckSteps(DateTime.now()));
  }

  Step createDomainRecheckSteps(DateTime now) {

    int recheckInterval = TuningParameters.getInstance().getMainTuning().domainPresenceRecheckIntervalSeconds;
    boolean isFullRecheck = false;
    if (lastFullRecheck.get().plusSeconds(recheckInterval).isBefore(now)) {
      delegate.getProcessor().reportSuspendedFibers();
      isFullRecheck = true;
      lastFullRecheck.set(now);
    }

    return Step.chain(
        NamespaceRulesReviewStep.forOperatorNamespace(),
        RunInParallel.perNamespace(Namespaces.getConfiguredDomainNamespaces(), NamespaceRulesReviewStep::forNamespace),
        CrdHelper.createDomainCrdStep(delegate.getKubernetesVersion(), delegate.getProductVersion()),
        new DomainRecheck(isFullRecheck).createReadNamespacesStep());
  }

  static class DomainRecheck {
    private final boolean fullRecheck;

    DomainRecheck() {
      this(false);
    }

    DomainRecheck(boolean fullRecheck) {
      this.fullRecheck = fullRecheck;
    }

    private Step createReadNamespacesStep() {
      return Namespaces.getSelection(new ReadNamespacesStepsVisitor());
    }

    class ReadNamespacesStepsVisitor implements NamespaceStrategyVisitor<Step> {

      @Override
      public Step getDedicatedStrategySelection() {
        return createStartNamespacesStep(Collections.singletonList(getOperatorNamespace()));
      }

      @Override
      public Step getDefaultSelection() {
        return readExistingNamespaces();
      }
    }

    /**
     * Reads the existing namespaces from Kubernetes and performs appropriate processing on those
     * identified as domain namespaces.
      */
    Step readExistingNamespaces() {
      return new CallBuilder()
            .withLabelSelectors(Namespaces.getLabelSelectors())
            .listNamespaceAsync(new NamespaceListResponseStep());
    }

    private class NamespaceListResponseStep extends DefaultResponseStep<V1NamespaceList> {

      private NamespaceListResponseStep() {
        super(new Namespaces.NamespaceListAfterStep());
      }

      // If unable to list the namespaces, we may still be able to start them if we are using
      // a strategy that specifies them explicitly.
      @Override
      protected NextAction onFailureNoRetry(Packet packet, CallResponse<V1NamespaceList> callResponse) {
        return useBackupStrategy(callResponse)
                ? doNext(createStartNamespacesStep(Namespaces.getConfiguredDomainNamespaces()), packet)
                : super.onFailureNoRetry(packet, callResponse);
      }

      // Returns true if the failure wasn't due to authorization, and we have a list of namespaces to manage.
      private boolean useBackupStrategy(CallResponse<V1NamespaceList> callResponse) {
        return Namespaces.getConfiguredDomainNamespaces() != null && isNotAuthorizedOrForbidden(callResponse);
      }

      @Override
      public NextAction onSuccess(Packet packet, CallResponse<V1NamespaceList> callResponse) {
        final Set<String> domainNamespaces = getNamespacesToStart(getNames(callResponse.getResult()));
        Namespaces.getFoundDomainNamespaces(packet).addAll(domainNamespaces);

        return doContinueListOrNext(callResponse, packet, createNextSteps(domainNamespaces));
      }

      private Step createNextSteps(Set<String> namespacesToStartNow) {
        List<Step> nextSteps = new ArrayList<>();
        if (!namespacesToStartNow.isEmpty()) {
          nextSteps.add(createStartNamespacesStep(namespacesToStartNow));
          if (Namespaces.getConfiguredDomainNamespaces() == null) {
            nextSteps.add(RunInParallel.perNamespace(namespacesToStartNow, NamespaceRulesReviewStep::forNamespace));
          }
        }
        nextSteps.add(getNext());
        return Step.chain(nextSteps.toArray(new Step[0]));
      }

      private Set<String> getNamespacesToStart(List<String> namespaceNames) {
        return namespaceNames.stream().filter(Namespaces::isDomainNamespace).collect(Collectors.toSet());
      }

      private List<String> getNames(V1NamespaceList result) {
        return result.getItems().stream()
              .map(V1Namespace::getMetadata)
              .filter(Objects::nonNull)
              .map(V1ObjectMeta::getName)
              .collect(Collectors.toList());
      }
    }

    Step createStartNamespacesStep(Collection<String> domainNamespaces) {
      return RunInParallel.perNamespace(domainNamespaces, this::startNamespaceSteps);
    }

    Step startNamespaceSteps(String ns) {
      return Step.chain(
          NamespaceRulesReviewStep.forNamespace(ns),
          new StartNamespaceBeforeStep(ns),
          DomainNamespaces.readExistingResources(ns, processor));
    }

    private class StartNamespaceBeforeStep extends Step {
      private final String ns;

      StartNamespaceBeforeStep(String ns) {
        this.ns = ns;
      }

      @Override
      public NextAction apply(Packet packet) {
        NamespaceStatus nss = DomainNamespaces.getNamespaceStatus(ns);
        if (fullRecheck || !nss.isNamespaceStarting().getAndSet(true)) {
          return doNext(packet);
        } else {
          return doEnd(packet);
        }
      }
    }
  }


  /**
   * Returns true if the operator is configured to use a single dedicated namespace for both itself any any domains.
   */
  public static boolean isDedicated() {
    return Namespaces.SelectionStrategy.Dedicated.equals(Namespaces.getSelectionStrategy());
  }

  private static void startRestServer(String principal)
      throws Exception {
    RestServer.create(new RestConfigImpl(principal, DomainNamespaces::getNamespaces));
    RestServer.getInstance().start(container);
  }

  // -----------------------------------------------------------------------------
  //
  // Below this point are methods that are called primarily from watch handlers,
  // after watch events are received.
  //
  // -----------------------------------------------------------------------------

  private static void stopRestServer() {
    RestServer.getInstance().stop();
    RestServer.destroy();
  }

  private static void markReadyAndStartLivenessThread() {
    try {
      OperatorReady.create();

      LOGGER.info(MessageKeys.STARTING_LIVENESS_THREAD);
      // every five seconds we need to update the last modified time on the liveness file
      wrappedExecutorService.scheduleWithFixedDelay(new OperatorLiveness(), 5, 5, TimeUnit.SECONDS);
    } catch (IOException io) {
      LOGGER.severe(MessageKeys.EXCEPTION, io);
    }
  }

  private static void waitForDeath() {
    Runtime.getRuntime().addShutdownHook(new Thread(shutdownSignal::release));

    try {
      shutdownSignal.acquire();
    } catch (InterruptedException ignore) {
      Thread.currentThread().interrupt();
    }

    DomainNamespaces.stopAllWatchers();
  }

  private NamespaceWatcher createNamespaceWatcher(String initialResourceVersion) {
    return NamespaceWatcher.create(
        threadFactory,
        initialResourceVersion,
        Namespaces.getLabelSelectors(),
        TuningParameters.getInstance().getWatchTuning(),
        this::dispatchNamespaceWatch,
        new AtomicBoolean(false));
  }

  void dispatchNamespaceWatch(Watch.Response<V1Namespace> item) {
    String ns = Optional.ofNullable(item.object).map(V1Namespace::getMetadata).map(V1ObjectMeta::getName).orElse(null);
    if (ns == null) {
      return;
    }

    switch (item.type) {
      case "ADDED":
        if (!Namespaces.isDomainNamespace(ns)) {
          return;
        }

        delegate.runSteps(createPacketWithLoggingContext(ns),
              new DomainRecheck(true).createStartNamespacesStep(Collections.singletonList(ns)),
              null);
        break;

      case "DELETED":
        // Mark the namespace as isStopping, which will cause the namespace be stopped
        // the next time when recheckDomains is triggered
        DomainNamespaces.isStopping(ns).set(true);

        break;

      case "MODIFIED":
      case "ERROR":
      default:
    }
  }

  private static Packet createPacketWithLoggingContext(String ns) {
    Packet packet = new Packet();
    packet.getComponents().put(
        LoggingContext.LOGGING_CONTEXT_KEY,
        Component.createFor(new LoggingContext().namespace(ns)));
    return packet;
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

  /**
   * Given a list of namespace names and a method that creates steps for the namespace,
   * will create the appropriate steps and run them in parallel, waiting for all to complete
   * before proceeding.
   */
  private static class RunInParallel extends Step {

    protected final Function<String, Step> stepFactory;
    private final Collection<String> domainNamespaces;

    RunInParallel(Collection<String> domainNamespaces, Function<String, Step> stepFactory) {
      this.domainNamespaces = domainNamespaces;
      this.stepFactory = stepFactory;
    }

    public static Step perNamespace(Collection<String> domainNamespaces, Function<String, Step> stepFactory) {
      return new RunInParallel(domainNamespaces, stepFactory);
    }

    @Override
    protected String getDetail() {
      return Optional.ofNullable(domainNamespaces).map(d -> String.join(",", d)).orElse(null);
    }

    @Override
    public NextAction apply(Packet packet) {
      if (domainNamespaces == null) {
        return doNext(packet);
      } else {
        Collection<StepAndPacket> startDetails = new ArrayList<>();

        for (String ns : domainNamespaces) {
          try (LoggingContext ignored = LoggingContext.setThreadContext().namespace(ns)) {
            startDetails.add(new StepAndPacket(stepFactory.apply(ns), packet.clone()));
          }
        }
        return doForkJoin(getNext(), packet, startDetails);
      }
    }
  }

  /**
   * This step logs warnings to the operator console if the specified domain namespace lacks the required privileges.
   */
  private static class NamespaceRulesReviewStep extends Step {
    private final String ns;

    static NamespaceRulesReviewStep forOperatorNamespace() {
      return new NamespaceRulesReviewStep(getOperatorNamespace());
    }

    static NamespaceRulesReviewStep forNamespace(@Nonnull String ns) {
      return new NamespaceRulesReviewStep(ns);
    }

    private NamespaceRulesReviewStep(@Nonnull String ns) {
      this.ns = ns;
    }

    @Override
    public NextAction apply(Packet packet) {
      NamespaceStatus nss = DomainNamespaces.getNamespaceStatus(ns);

      // we don't have the domain presence information yet
      // we add a logging context to pass the namespace information to the LoggingFormatter
      packet.getComponents().put(
          LoggingContext.LOGGING_CONTEXT_KEY,
          Component.createFor(new LoggingContext().namespace(ns)));

      nss.getRulesReviewStatus().updateAndGet(prev -> {
        if (prev != null) {
          return prev;
        }

        try {
          return HealthCheckHelper.getAccessAuthorizations(ns);
        } catch (Throwable e) {
          LOGGER.warning(MessageKeys.EXCEPTION, e);
        }
        return null;
      });

      return doNext(packet);
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
      if (throwable instanceof FailureStatusSourceException) {
        ((FailureStatusSourceException) throwable).log();
      } else {
        LOGGER.severe(MessageKeys.EXCEPTION, throwable);
      }
    }
  }

  private static class DomainProcessorDelegateImpl implements DomainProcessorDelegate {

    @Override
    public PodAwaiterStepFactory getPodAwaiterStepFactory(String namespace) {
      return DomainNamespaces.getPodWatcher(namespace);
    }

    @Override
    public JobAwaiterStepFactory getJobAwaiterStepFactory(String namespace) {
      return DomainNamespaces.getJobWatcher(namespace);
    }

    @Override
    public boolean isNamespaceRunning(String namespace) {
      return !DomainNamespaces.isStopping(namespace).get();
    }

    @Override
    public KubernetesVersion getVersion() {
      return version;
    }

    @Override
    public FiberGate createFiberGate() {
      return new FiberGate(Main.engine);
    }

    @Override
    public void runSteps(Step firstStep) {
      Main.runSteps(firstStep);
    }

    @Override
    public ScheduledFuture<?> scheduleWithFixedDelay(
        Runnable command, long initialDelay, long delay, TimeUnit unit) {
      return Main.engine.getExecutor().scheduleWithFixedDelay(command, initialDelay, delay, unit);
    }
  }

}
