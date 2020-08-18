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
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.Set;
import java.util.StringTokenizer;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.Semaphore;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import java.util.stream.Collectors;
import javax.annotation.Nonnull;

import io.kubernetes.client.openapi.models.V1EventList;
import io.kubernetes.client.openapi.models.V1Namespace;
import io.kubernetes.client.openapi.models.V1NamespaceList;
import io.kubernetes.client.openapi.models.V1Pod;
import io.kubernetes.client.openapi.models.V1PodList;
import io.kubernetes.client.openapi.models.V1Service;
import io.kubernetes.client.openapi.models.V1ServiceList;
import io.kubernetes.client.openapi.models.V1SubjectRulesReviewStatus;
import io.kubernetes.client.util.Watch;
import oracle.kubernetes.operator.calls.CallResponse;
import oracle.kubernetes.operator.calls.FailureStatusSourceException;
import oracle.kubernetes.operator.helpers.CallBuilder;
import oracle.kubernetes.operator.helpers.CallBuilderFactory;
import oracle.kubernetes.operator.helpers.ClientPool;
import oracle.kubernetes.operator.helpers.ConfigMapHelper;
import oracle.kubernetes.operator.helpers.CrdHelper;
import oracle.kubernetes.operator.helpers.DomainPresenceInfo;
import oracle.kubernetes.operator.helpers.HealthCheckHelper;
import oracle.kubernetes.operator.helpers.KubernetesVersion;
import oracle.kubernetes.operator.helpers.PodHelper;
import oracle.kubernetes.operator.helpers.ResponseStep;
import oracle.kubernetes.operator.helpers.SemanticVersion;
import oracle.kubernetes.operator.helpers.ServiceHelper;
import oracle.kubernetes.operator.logging.LoggingContext;
import oracle.kubernetes.operator.logging.LoggingFacade;
import oracle.kubernetes.operator.logging.LoggingFactory;
import oracle.kubernetes.operator.logging.MessageKeys;
import oracle.kubernetes.operator.rest.RestConfigImpl;
import oracle.kubernetes.operator.rest.RestServer;
import oracle.kubernetes.operator.steps.ConfigMapAfterStep;
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
import oracle.kubernetes.weblogic.domain.model.Domain;
import oracle.kubernetes.weblogic.domain.model.DomainList;
import org.apache.commons.lang.StringUtils;
import org.joda.time.DateTime;

/** A Kubernetes Operator for WebLogic. */
public class Main {
  private static final LoggingFacade LOGGER = LoggingFactory.getLogger("Operator", "Operator");

  private static final String DPI_MAP = "DPI_MAP";

  private static final Container container = new Container();
  private static final ThreadFactory threadFactory = new WrappedThreadFactory();
  private static final ScheduledExecutorService wrappedExecutorService =
      Engine.wrappedExecutorService("operator", container);
  private static final CallBuilderFactory callBuilderFactory = new CallBuilderFactory();
  private static Map<String, NamespaceStatus> namespaceStatuses = new ConcurrentHashMap<>();
  private static Map<String, AtomicBoolean> namespaceStoppingMap = new ConcurrentHashMap<>();
  private static final Map<String, ConfigMapWatcher> configMapWatchers = new ConcurrentHashMap<>();
  private static final Map<String, DomainWatcher> domainWatchers = new ConcurrentHashMap<>();
  private static final Map<String, EventWatcher> eventWatchers = new ConcurrentHashMap<>();
  private static final Map<String, ServiceWatcher> serviceWatchers = new ConcurrentHashMap<>();
  private static final Map<String, PodWatcher> podWatchers = new ConcurrentHashMap<>();
  private static NamespaceWatcher namespaceWatcher = null;
  private static Function<String,String> getHelmVariable = System::getenv;
  private static final String operatorNamespace = computeOperatorNamespace();
  private static final AtomicReference<DateTime> lastFullRecheck =
      new AtomicReference<>(DateTime.now());
  private static final DomainProcessorDelegateImpl delegate = new DomainProcessorDelegateImpl();
  private static final DomainProcessor processor = new DomainProcessorImpl(delegate);
  private static final String READINESS_PROBE_FAILURE_EVENT_FILTER =
      "reason=Unhealthy,type=Warning,involvedObject.fieldPath=spec.containers{weblogic-server}";
  private static final Semaphore shutdownSignal = new Semaphore(0);
  private static final Engine engine = new Engine(wrappedExecutorService);
  private static String principal;
  private static KubernetesVersion version = null;
  private static SemanticVersion productVersion = null;

  private static TuningParameters tuningAndConfig() {
    return TuningParameters.getInstance();
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
                tuningAndConfig(),
                ThreadFactory.class,
                threadFactory,
                callBuilderFactory));
  }

  /**
   * Entry point.
   *
   * @param args none, ignored
   */
  public static void main(String[] args) {
    try (final InputStream stream = Main.class.getResourceAsStream("/version.properties")) {
      Properties buildProps = new Properties();
      buildProps.load(stream);

      String operatorVersion = buildProps.getProperty("git.build.version");
      if (operatorVersion != null) {
        productVersion = new SemanticVersion(operatorVersion);
      }
      String operatorImpl =
          buildProps.getProperty("git.branch")
              + "."
              + buildProps.getProperty("git.commit.id.abbrev");
      String operatorBuildTime = buildProps.getProperty("git.build.time");

      // print startup log message
      LOGGER.info(MessageKeys.OPERATOR_STARTED, operatorVersion, operatorImpl, operatorBuildTime);
    } catch (IOException e) {
      LOGGER.warning(MessageKeys.EXCEPTION, e);
    }

    try {
      engine.getExecutor().execute(Main::begin);

      // now we just wait until the pod is terminated
      waitForDeath();

      // stop the REST server
      stopRestServer();
    } finally {
      LOGGER.info(MessageKeys.OPERATOR_SHUTTING_DOWN);
    }
  }

  private static void begin() {
    String serviceAccountName =
        Optional.ofNullable(tuningAndConfig().get("serviceaccount")).orElse("default");
    principal = "system:serviceaccount:" + operatorNamespace + ":" + serviceAccountName;

    LOGGER.info(MessageKeys.OP_CONFIG_NAMESPACE, operatorNamespace);
    JobWatcher.defineFactory(
        threadFactory, tuningAndConfig().getWatchTuning(), Main::isNamespaceStopping);

    DomainNamespaceSelectionStrategy selectionStrategy = getDomainNamespaceSelectionStrategy();
    Collection<String> configuredDomainNamespaces = selectionStrategy.getConfiguredList();
    if (configuredDomainNamespaces != null) {
      LOGGER.info(MessageKeys.OP_CONFIG_DOMAIN_NAMESPACES, StringUtils.join(configuredDomainNamespaces, ", "));
    }
    LOGGER.info(MessageKeys.OP_CONFIG_SERVICE_ACCOUNT, serviceAccountName);

    try {
      version = HealthCheckHelper.performK8sVersionCheck();

      Step strategy = Step.chain(
          new InitializeNamespacesSecurityStep(configuredDomainNamespaces),
          new NamespaceRulesReviewStep());
      if (!DomainNamespaceSelectionStrategy.Dedicated.equals(selectionStrategy)) {
        strategy = Step.chain(strategy, readExistingNamespaces(selectionStrategy, configuredDomainNamespaces, false));
      } else {
        strategy = Step.chain(strategy, CrdHelper.createDomainCrdStep(
                version, productVersion,
                new StartNamespacesStep(configuredDomainNamespaces, false)));
      }
      runSteps(
          strategy,
          Main::completeBegin);
    } catch (Throwable e) {
      LOGGER.warning(MessageKeys.EXCEPTION, e);
    }
  }

  private static void completeBegin() {
    try {
      // start the REST server
      startRestServer(principal, namespaceStoppingMap.keySet());

      // start periodic retry and recheck
      int recheckInterval = tuningAndConfig().getMainTuning().domainNamespaceRecheckIntervalSeconds;
      engine
          .getExecutor()
          .scheduleWithFixedDelay(
              recheckDomains(), recheckInterval, recheckInterval, TimeUnit.SECONDS);

      // Wait until all other initialization is done before marking ready and
      // starting liveness thread

      // mark ready and start liveness thread
      markReadyAndStartLivenessThread();

    } catch (Throwable e) {
      LOGGER.warning(MessageKeys.EXCEPTION, e);
    }
  }

  private static void stopNamespace(String ns, boolean inDomainNamespaceList) {
    AtomicBoolean isNamespaceStopping = isNamespaceStopping(ns);

    // Remove if namespace not in domainNamespace list
    if (!inDomainNamespaceList) {
      namespaceStoppingMap.remove(ns);
    }

    // stop all Domains for namespace being stopped (not active)
    if (isNamespaceStopping.get()) {
      processor.stopNamespace(ns);
    }

    // set flag to indicate namespace is stopping.
    isNamespaceStopping.set(true);

    // unsubscribe from resource events for given namespace
    namespaceStatuses.remove(ns);
    domainWatchers.remove(ns);
    eventWatchers.remove(ns);
    podWatchers.remove(ns);
    serviceWatchers.remove(ns);
    configMapWatchers.remove(ns);
    JobWatcher.removeNamespace(ns);
  }

  private static void stopNamespaces(Collection<String> domainNamespaces,
                                     Collection<String> namespacesToStop) {
    for (String ns : namespacesToStop) {
      stopNamespace(ns, domainNamespaces.contains(ns));
    }
  }

  private static AtomicBoolean isNamespaceStopping(String ns) {
    return namespaceStoppingMap.computeIfAbsent(ns, (key) -> new AtomicBoolean(false));
  }

  private static void runSteps(Step firstStep, Packet packet) {
    runSteps(firstStep, packet, null);
  }

  private static void runSteps(Step firstStep) {
    runSteps(firstStep, new Packet(), null);
  }

  private static void runSteps(Step firstStep, Runnable completionAction) {
    runSteps(firstStep, new Packet(), completionAction);
  }

  private static void runSteps(Step firstStep, Packet packet, Runnable completionAction) {
    Fiber f = engine.createFiber();
    f.start(firstStep, packet, andThenDo(completionAction));
  }

  private static NullCompletionCallback andThenDo(Runnable completionAction) {
    return new NullCompletionCallback(completionAction);
  }

  static Runnable recheckDomains() {
    return () -> runSteps(createDomainRecheckSteps(DateTime.now()));
  }

  static Step createDomainRecheckSteps(DateTime now) {
    DomainNamespaceSelectionStrategy selectionStrategy = getDomainNamespaceSelectionStrategy();
    Collection<String> configuredDomainNamespaces = selectionStrategy.getConfiguredList();

    int recheckInterval = tuningAndConfig().getMainTuning().domainPresenceRecheckIntervalSeconds;
    boolean isFullRecheck = false;
    if (lastFullRecheck.get().plusSeconds(recheckInterval).isBefore(now)) {
      processor.reportSuspendedFibers();
      isFullRecheck = true;
      lastFullRecheck.set(now);
    }

    Step strategy = Step.chain(
        new InitializeNamespacesSecurityStep(configuredDomainNamespaces),
        new NamespaceRulesReviewStep());
    if (!DomainNamespaceSelectionStrategy.Dedicated.equals(selectionStrategy)) {
      strategy = Step.chain(strategy, readExistingNamespaces(
              selectionStrategy, configuredDomainNamespaces, isFullRecheck));
    } else {
      strategy = Step.chain(strategy, CrdHelper.createDomainCrdStep(
          version, productVersion,
          new StartNamespacesStep(configuredDomainNamespaces, isFullRecheck)));
    }
    return strategy;
  }

  static Step readExistingResources(String operatorNamespace, String ns) {
    return Step.chain(
        new ReadExistingResourcesBeforeStep(),
        ConfigMapHelper.createScriptConfigMapStep(operatorNamespace, ns),
        createConfigMapStep(ns),
        readExistingPods(ns),
        readExistingEvents(ns),
        readExistingServices(ns),
        readExistingDomains(ns));
  }

  private static Step readExistingDomains(String ns) {
    LOGGER.fine(MessageKeys.LISTING_DOMAINS);
    return callBuilderFactory.create().listDomainAsync(ns, new DomainListStep(ns));
  }

  private static Step readExistingServices(String ns) {
    return new CallBuilder()
        .withLabelSelectors(LabelConstants.DOMAINUID_LABEL, LabelConstants.CREATEDBYOPERATOR_LABEL)
        .listServiceAsync(ns, new ServiceListStep(ns));
  }

  private static Step readExistingEvents(String ns) {
    return new CallBuilder()
        .withFieldSelector(Main.READINESS_PROBE_FAILURE_EVENT_FILTER)
        .listEventAsync(ns, new EventListStep(ns));
  }

  private static Step readExistingPods(String ns) {
    return new CallBuilder()
        .withLabelSelectors(LabelConstants.DOMAINUID_LABEL, LabelConstants.CREATEDBYOPERATOR_LABEL)
        .listPodAsync(ns, new PodListStep(ns));
  }

  static Step readExistingNamespaces(DomainNamespaceSelectionStrategy selectionStrategy,
                                             Collection<String> domainNamespaces,
                                             boolean isFullRecheck) {
    CallBuilder builder = new CallBuilder();
    String selector = selectionStrategy.getLabelSelector();
    if (selector != null) {
      builder.withLabelSelectors(selector);
    }
    return builder.listNamespaceAsync(new NamespaceListStep(selectionStrategy, domainNamespaces, isFullRecheck));
  }

  private static ConfigMapAfterStep createConfigMapStep(String ns) {
    return new ConfigMapAfterStep(
        ns,
        configMapWatchers,
        tuningAndConfig().getWatchTuning(),
        isNamespaceStopping(ns),
        processor::dispatchConfigMapWatch);
  }

  /**
   * Obtain the list of domain namespaces.
   *
   * @return the collection of domain namespace names
   */
  @SuppressWarnings("SameParameterValue")
  private static Collection<String> getDomainNamespacesList(String tnValue, String namespace) {
    Collection<String> domainNamespaces = new ArrayList<>();

    if (tnValue != null) {
      StringTokenizer st = new StringTokenizer(tnValue, ",");
      while (st.hasMoreTokens()) {
        domainNamespaces.add(st.nextToken().trim());
      }
    }

    // If no namespaces were found, default to the namespace of the operator
    if (domainNamespaces.isEmpty()) {
      domainNamespaces.add(namespace);
    }

    return domainNamespaces;
  }

  enum DomainNamespaceSelectionStrategy {
    List {
      @Override
      public Collection<String> getConfiguredList() {
        return getDomainNamespacesList(Optional.ofNullable(getHelmVariable.apply("OPERATOR_DOMAIN_NAMESPACES"))
            .orElse(Optional.ofNullable(tuningAndConfig().get("domainNamespaces"))
                .orElse(tuningAndConfig().get("targetNamespaces"))), operatorNamespace);
      }
    },
    LabelSelector {
      @Override
      public boolean isRequireList() {
        return true;
      }

      @Override
      public String getLabelSelector() {
        return tuningAndConfig().get("domainNamespaceLabelSelector");
      }
    },
    RegExp {
      @Override
      public boolean isRequireList() {
        return true;
      }

      @Override
      public String getRegExp() {
        return tuningAndConfig().get("domainNamespaceRegExp");
      }
    },
    Dedicated {
      @Override
      public Collection<String> getConfiguredList() {
        return Collections.singleton(operatorNamespace);
      }
    };

    public boolean isRequireList() {
      return false;
    }

    public String getLabelSelector() {
      return null;
    }

    public String getRegExp() {
      return null;
    }

    public Collection<String> getConfiguredList() {
      return null;
    }
  }

  /**
   * Gets the domain namespace selection strategy.
   * @return Selection strategy
   */
  public static DomainNamespaceSelectionStrategy getDomainNamespaceSelectionStrategy() {
    DomainNamespaceSelectionStrategy strategy =
        Optional.ofNullable(tuningAndConfig().get("domainNamespaceSelectionStrategy"))
        .map(DomainNamespaceSelectionStrategy::valueOf).orElse(DomainNamespaceSelectionStrategy.List);
    if (DomainNamespaceSelectionStrategy.List.equals(strategy) && isDeprecatedDedicated()) {
      return DomainNamespaceSelectionStrategy.Dedicated;
    }
    return strategy;
  }

  public static boolean isDedicated() {
    return DomainNamespaceSelectionStrategy.Dedicated.equals(getDomainNamespaceSelectionStrategy());
  }

  private static boolean isDeprecatedDedicated() {
    return "true".equalsIgnoreCase(Optional.ofNullable(getHelmVariable.apply("OPERATOR_DEDICATED"))
        .orElse(Optional.ofNullable(tuningAndConfig().get("dedicated")).orElse("false")));
  }

  private static void startRestServer(String principal, Collection<String> domainNamespaces)
      throws Exception {
    RestServer.create(new RestConfigImpl(principal, domainNamespaces));
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

    namespaceStoppingMap.forEach((key, value) -> value.set(true));
  }

  private static EventWatcher createEventWatcher(String ns, String initialResourceVersion) {
    return EventWatcher.create(
        threadFactory,
        ns,
        READINESS_PROBE_FAILURE_EVENT_FILTER,
        initialResourceVersion,
        tuningAndConfig().getWatchTuning(),
        processor::dispatchEventWatch,
        isNamespaceStopping(ns));
  }

  private static PodWatcher createPodWatcher(String ns, String initialResourceVersion) {
    return PodWatcher.create(
        threadFactory,
        ns,
        initialResourceVersion,
        tuningAndConfig().getWatchTuning(),
        processor::dispatchPodWatch,
        isNamespaceStopping(ns));
  }

  private static ServiceWatcher createServiceWatcher(String ns, String initialResourceVersion) {
    return ServiceWatcher.create(
        threadFactory,
        ns,
        initialResourceVersion,
        tuningAndConfig().getWatchTuning(),
        processor::dispatchServiceWatch,
        isNamespaceStopping(ns));
  }

  private static DomainWatcher createDomainWatcher(String ns, String initialResourceVersion) {
    return DomainWatcher.create(
        threadFactory,
        ns,
        initialResourceVersion,
        tuningAndConfig().getWatchTuning(),
        processor::dispatchDomainWatch,
        isNamespaceStopping(ns));
  }

  private static NamespaceWatcher createNamespaceWatcher(DomainNamespaceSelectionStrategy selectionStrategy,
                                                         String initialResourceVersion) {
    return NamespaceWatcher.create(
        threadFactory,
        initialResourceVersion,
        selectionStrategy.getLabelSelector(),
        tuningAndConfig().getWatchTuning(),
        Main::dispatchNamespaceWatch,
        new AtomicBoolean(false));
  }

  private static String computeOperatorNamespace() {
    return Optional.ofNullable(getHelmVariable.apply("OPERATOR_NAMESPACE")).orElse("default");
  }

  private static void dispatchNamespaceWatch(Watch.Response<V1Namespace> item) {
    V1Namespace c = item.object;
    if (c != null) {
      String ns = c.getMetadata().getName();

      switch (item.type) {
        case "ADDED":
          DomainNamespaceSelectionStrategy selectionStrategy = getDomainNamespaceSelectionStrategy();
          Collection<String> configuredDomainNamespaces = selectionStrategy.getConfiguredList();

          // For selection strategies with a configured list, we only care about namespaces that are in that list
          if (configuredDomainNamespaces != null && !configuredDomainNamespaces.contains(ns)) {
            return;
          }

          // For regexp strategy, we only care about namespaces that match the pattern
          String regexp = selectionStrategy.getRegExp();
          if (regexp != null) {
            try {
              if (!Pattern.compile(regexp).asPredicate().test(ns)) {
                return;
              }
            } catch (PatternSyntaxException pse) {
              LOGGER.severe(MessageKeys.EXCEPTION, pse);
              return;
            }
          }

          // For label strategy, we will only get watch events for namespaces that match the selector so
          // no additional check is needed

          Step strategy = new StartNamespacesStep(Collections.singletonList(ns), true);
          if (!isNamespaceStopping(ns).getAndSet(false)) {
            strategy = Step.chain(getScriptCreationSteps(ns), strategy);
          }
          runSteps(strategy, createPacketWithLoggingContext(ns));
          break;

        case "DELETED":
          // Mark the namespace as isStopping, which will cause the namespace be stopped
          // the next time when recheckDomains is triggered
          isNamespaceStopping(ns).set(true);

          break;

        case "MODIFIED":
        case "ERROR":
        default:
      }
    }
  }

  private static Packet createPacketWithLoggingContext(String ns) {
    Packet packet = new Packet();
    packet.getComponents().put(
        LoggingContext.LOGGING_CONTEXT_KEY,
        Component.createFor(new LoggingContext().namespace(ns)));
    return packet;
  }

  private static Step getScriptCreationSteps(String ns) {
    try (LoggingContext stack = LoggingContext.setThreadContext().namespace(ns)) {
      return Step.chain(
          ConfigMapHelper.createScriptConfigMapStep(operatorNamespace, ns), createConfigMapStep(ns));
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

  private abstract static class ForEachNamespaceStep extends Step {
    private final Collection<String> domainNamespaces;

    ForEachNamespaceStep(Collection<String> domainNamespaces) {
      this(domainNamespaces, null);
    }

    ForEachNamespaceStep(Collection<String> domainNamespaces, Step next) {
      super(next);
      this.domainNamespaces = domainNamespaces;
    }

    @Override
    protected String getDetail() {
      return String.join(",", domainNamespaces);
    }

    protected abstract Step action(String ns);

    @Override
    public NextAction apply(Packet packet) {
      // check for any existing resources and add the watches on them
      // this would happen when the Domain was running BEFORE the Operator starts up
      if (domainNamespaces != null) {
        Collection<StepAndPacket> startDetails = new ArrayList<>();

        for (String ns : domainNamespaces) {
          try (LoggingContext stack = LoggingContext.setThreadContext().namespace(ns)) {
            startDetails.add(new StepAndPacket(action(ns), packet.clone()));
          }
        }
        return doForkJoin(getNext(), packet, startDetails);
      }
      return doNext(packet);
    }
  }

  private static class StartNamespacesStep extends ForEachNamespaceStep {
    private final boolean isFullRecheck;

    StartNamespacesStep(Collection<String> domainNamespaces, boolean isFullRecheck) {
      super(domainNamespaces);
      this.isFullRecheck = isFullRecheck;
    }

    @Override
    protected Step action(String ns) {
      return Step.chain(
          new NamespaceRulesReviewStep(ns),
          new StartNamespaceBeforeStep(ns, isFullRecheck),
          readExistingResources(operatorNamespace, ns));
    }
  }

  private static class StartNamespaceBeforeStep extends Step {
    private final String ns;
    private final boolean isFullRecheck;

    StartNamespaceBeforeStep(String ns, boolean isFullRecheck) {
      this.ns = ns;
      this.isFullRecheck = isFullRecheck;
    }

    @Override
    public NextAction apply(Packet packet) {
      NamespaceStatus nss = namespaceStatuses.computeIfAbsent(ns, (key) -> new NamespaceStatus());
      if (isFullRecheck || !nss.isNamespaceStarting().getAndSet(true)) {
        return doNext(packet);
      }
      return doEnd(packet);
    }
  }

  private static class InitializeNamespacesSecurityStep extends ForEachNamespaceStep {
    InitializeNamespacesSecurityStep(Collection<String> domainNamespaces) {
      this(domainNamespaces, null);
    }

    InitializeNamespacesSecurityStep(Collection<String> domainNamespaces, Step next) {
      super(domainNamespaces, next);
    }

    @Override
    protected Step action(String ns) {
      return new NamespaceRulesReviewStep(ns);
    }
  }

  private static class NamespaceRulesReviewStep extends Step {
    private final String ns;

    NamespaceRulesReviewStep() {
      this(null);
    }

    NamespaceRulesReviewStep(String ns) {
      this.ns = ns;
    }

    @Override
    public NextAction apply(Packet packet) {
      // Looking up namespace status. If ns is null, then this step will check the status of the
      // operator's own namespace. If the namespace status is missing, then generate it with
      // the health check helper.
      NamespaceStatus nss = namespaceStatuses.computeIfAbsent(
          ns != null ? ns : operatorNamespace, (key) -> new NamespaceStatus());

      // we don't have the domain presence information yet
      // we add a logging context to pass the namespace information to the LoggingFormatter
      if (ns != null) {
        packet.getComponents().put(
            LoggingContext.LOGGING_CONTEXT_KEY,
            Component.createFor(new LoggingContext().namespace(ns)));
      }

      V1SubjectRulesReviewStatus srrs = nss.getRulesReviewStatus().updateAndGet(prev -> {
        if (prev != null) {
          return prev;
        }

        try {
          return HealthCheckHelper.performSecurityChecks(version, operatorNamespace, ns);
        } catch (Throwable e) {
          LOGGER.warning(MessageKeys.EXCEPTION, e);
        }
        return null;
      });

      packet.getComponents().put(
          NamespaceRulesReviewStep.class.getName(),
          Component.createFor(V1SubjectRulesReviewStatus.class, srrs));

      return doNext(packet);
    }
  }

  private static class ReadExistingResourcesBeforeStep extends Step {
    @SuppressWarnings("rawtypes")
    @Override
    public NextAction apply(Packet packet) {
      packet.put(DPI_MAP, new ConcurrentHashMap());
      return doNext(packet);
    }
  }

  private static class DomainListStep extends ResponseStep<DomainList> {
    private final String ns;

    DomainListStep(String ns) {
      this.ns = ns;
    }

    @Override
    public NextAction onFailure(Packet packet, CallResponse<DomainList> callResponse) {
      return callResponse.getStatusCode() == CallBuilder.NOT_FOUND
          ? onSuccess(packet, callResponse)
          : super.onFailure(packet, callResponse);
    }

    @Override
    public NextAction onSuccess(Packet packet, CallResponse<DomainList> callResponse) {
      @SuppressWarnings("unchecked")
      Map<String, DomainPresenceInfo> dpis = (Map<String, DomainPresenceInfo>) packet.get(DPI_MAP);

      DomainProcessor x = packet.getSpi(DomainProcessor.class);
      DomainProcessor dp = x != null ? x : processor;

      Set<String> domainUids = new HashSet<>();
      if (callResponse.getResult() != null) {
        for (Domain dom : callResponse.getResult().getItems()) {
          String domainUid = dom.getDomainUid();
          domainUids.add(domainUid);
          DomainPresenceInfo info =
              dpis.compute(
                  domainUid,
                  (k, v) -> {
                    if (v == null) {
                      return new DomainPresenceInfo(dom);
                    }
                    v.setDomain(dom);
                    return v;
                  });
          info.setPopulated(true);
          try (LoggingContext stack = LoggingContext.setThreadContext().namespace(ns).domainUid(domainUid)) {
            dp.createMakeRightOperation(info).withExplicitRecheck().execute();
          }
        }
      }

      dpis.forEach(
          (uid, info) -> {
            if (!domainUids.contains(uid)) {
              // This is a stranded DomainPresenceInfo.
              info.setDeleting(true);
              info.setPopulated(true);
              try (LoggingContext stack = LoggingContext.setThreadContext().namespace(ns).domainUid(uid)) {
                dp.createMakeRightOperation(info).withExplicitRecheck().forDeletion().execute();
              }
            }
          });

      if (!domainWatchers.containsKey(ns)) {
        domainWatchers.put(
            ns, createDomainWatcher(ns, getResourceVersion(callResponse.getResult())));
      }
      return doNext(packet);
    }

    String getResourceVersion(DomainList result) {
      return result != null ? result.getMetadata().getResourceVersion() : "";
    }
  }

  private static class ServiceListStep extends ResponseStep<V1ServiceList> {
    private final String ns;

    ServiceListStep(String ns) {
      this.ns = ns;
    }

    @Override
    public NextAction onFailure(Packet packet, CallResponse<V1ServiceList> callResponse) {
      return callResponse.getStatusCode() == CallBuilder.NOT_FOUND
          ? onSuccess(packet, callResponse)
          : super.onFailure(packet, callResponse);
    }

    @Override
    public NextAction onSuccess(Packet packet, CallResponse<V1ServiceList> callResponse) {
      V1ServiceList result = callResponse.getResult();

      @SuppressWarnings("unchecked")
      Map<String, DomainPresenceInfo> dpis = (Map<String, DomainPresenceInfo>) packet.get(DPI_MAP);

      if (result != null) {
        for (V1Service service : result.getItems()) {
          String domainUid = ServiceHelper.getServiceDomainUid(service);
          if (domainUid != null) {
            DomainPresenceInfo info =
                dpis.computeIfAbsent(domainUid, k -> new DomainPresenceInfo(ns, domainUid));
            ServiceHelper.addToPresence(info, service);
          }
        }
      }

      if (!serviceWatchers.containsKey(ns)) {
        serviceWatchers.put(ns, createServiceWatcher(ns, getInitialResourceVersion(result)));
      }
      return doNext(packet);
    }

    private String getInitialResourceVersion(V1ServiceList result) {
      return result != null ? result.getMetadata().getResourceVersion() : "";
    }
  }

  private static class EventListStep extends ResponseStep<V1EventList> {
    private final String ns;

    EventListStep(String ns) {
      this.ns = ns;
    }

    @Override
    public NextAction onFailure(Packet packet, CallResponse<V1EventList> callResponse) {
      return callResponse.getStatusCode() == CallBuilder.NOT_FOUND
          ? onSuccess(packet, callResponse)
          : super.onFailure(packet, callResponse);
    }

    @Override
    public NextAction onSuccess(Packet packet, CallResponse<V1EventList> callResponse) {
      V1EventList result = callResponse.getResult();
      // don't bother processing pre-existing events

      if (!eventWatchers.containsKey(ns)) {
        eventWatchers.put(ns, createEventWatcher(ns, getInitialResourceVersion(result)));
      }
      return doNext(packet);
    }

    private String getInitialResourceVersion(V1EventList result) {
      return result != null ? result.getMetadata().getResourceVersion() : "";
    }
  }

  private static class PodListStep extends ResponseStep<V1PodList> {
    private final String ns;

    PodListStep(String ns) {
      this.ns = ns;
    }

    @Override
    public NextAction onFailure(Packet packet, CallResponse<V1PodList> callResponse) {
      return callResponse.getStatusCode() == CallBuilder.NOT_FOUND
          ? onSuccess(packet, callResponse)
          : super.onFailure(packet, callResponse);
    }

    @Override
    public NextAction onSuccess(Packet packet, CallResponse<V1PodList> callResponse) {
      V1PodList result = callResponse.getResult();

      @SuppressWarnings("unchecked")
      Map<String, DomainPresenceInfo> dpis = (Map<String, DomainPresenceInfo>) packet.get(DPI_MAP);

      if (result != null) {
        for (V1Pod pod : result.getItems()) {
          String domainUid = PodHelper.getPodDomainUid(pod);
          String serverName = PodHelper.getPodServerName(pod);
          if (domainUid != null && serverName != null) {
            DomainPresenceInfo info =
                dpis.computeIfAbsent(domainUid, k -> new DomainPresenceInfo(ns, domainUid));
            info.setServerPod(serverName, pod);
          }
        }
      }

      if (!podWatchers.containsKey(ns)) {
        podWatchers.put(ns, createPodWatcher(ns, getInitialResourceVersion(result)));
      }
      return doNext(packet);
    }

    private String getInitialResourceVersion(V1PodList result) {
      return result != null ? result.getMetadata().getResourceVersion() : "";
    }
  }

  private static class NamespaceListStep extends ResponseStep<V1NamespaceList> {
    private final DomainNamespaceSelectionStrategy selectionStrategy;
    private final Collection<String> configuredDomainNamespaces;
    private final boolean isFullRecheck;

    NamespaceListStep(DomainNamespaceSelectionStrategy selectionStrategy,
                      Collection<String> configuredDomainNamespaces,
                      boolean isFullRecheck) {
      this.selectionStrategy = selectionStrategy;
      this.configuredDomainNamespaces = configuredDomainNamespaces;
      this.isFullRecheck = isFullRecheck;
    }

    @Override
    public NextAction onFailure(Packet packet, CallResponse<V1NamespaceList> callResponse) {
      return callResponse.getStatusCode() == CallBuilder.NOT_FOUND
          ? onSuccess(packet, callResponse)
          : super.onFailure(packet, callResponse);
    }

    @Override
    protected NextAction onFailureNoRetry(Packet packet, CallResponse<V1NamespaceList> callResponse) {
      return !selectionStrategy.isRequireList() && isNotAuthorizedOrForbidden(callResponse)
          ? doNext(createDomainCrdAndStartNamespaces(configuredDomainNamespaces, isFullRecheck), packet) :
            super.onFailureNoRetry(packet, callResponse);
    }

    @Override
    public NextAction onSuccess(Packet packet, CallResponse<V1NamespaceList> callResponse) {
      V1NamespaceList result = callResponse.getResult();
      // don't bother processing pre-existing events
      String intialResourceVersion = getInitialResourceVersion(result);
      List<String> nsList = getExistingNamespaces(result);
      
      Set<String> namespacesToStart;
      if (selectionStrategy.isRequireList()) {
        namespacesToStart = new TreeSet<>(nsList);
        String regexp = selectionStrategy.getRegExp();
        if (regexp != null) {
          try {
            namespacesToStart = namespacesToStart.stream().filter(
                    Pattern.compile(regexp).asPredicate()).collect(Collectors.toSet());
          } catch (PatternSyntaxException pse) {
            LOGGER.severe(MessageKeys.EXCEPTION, pse);
          }
        }
      } else {
        namespacesToStart = new TreeSet<>(configuredDomainNamespaces);
        for (String ns : configuredDomainNamespaces) {
          if (!nsList.contains(ns)) {
            try (LoggingContext stack = LoggingContext.setThreadContext().namespace(ns)) {
              LOGGER.warning(MessageKeys.NAMESPACE_IS_MISSING, ns);
            }
            namespacesToStart.remove(ns);
          }
        }
      }
      Step strategy = null;
      if (!namespacesToStart.isEmpty()) {
        strategy = Step.chain(createDomainCrdAndStartNamespaces(namespacesToStart, isFullRecheck),
          new CreateNamespaceWatcherStep(selectionStrategy, intialResourceVersion));

        if (configuredDomainNamespaces == null) {
          strategy = new InitializeNamespacesSecurityStep(namespacesToStart, strategy);
        }
      } else {
        strategy = CrdHelper.createDomainCrdStep(
          version, productVersion,
            new CreateNamespaceWatcherStep(selectionStrategy, intialResourceVersion));
      }

      // Check for namespaces that are removed from the operator's
      // domainNamespaces list, or that are deleted from the Kubernetes cluster.
      Set<String> namespacesToStop = new TreeSet<>(namespaceStoppingMap.keySet());
      for (String ns : namespacesToStart) {
        // the active namespaces are the ones that will not be stopped
        if (delegate.isNamespaceRunning(ns)) {
          namespacesToStop.remove(ns);
        }
      }
      stopNamespaces(namespacesToStart, namespacesToStop);

      return doNext(strategy, packet);
    }
    
    private Step createDomainCrdAndStartNamespaces(Collection<String> namespacesToStart, boolean isFullRecheck) {
      return CrdHelper.createDomainCrdStep(
          version, productVersion,
            new StartNamespacesStep(namespacesToStart, isFullRecheck));
    }

    private String getInitialResourceVersion(V1NamespaceList result) {
      return result != null ? result.getMetadata().getResourceVersion() : "";
    }
    
    private List<String> getExistingNamespaces(V1NamespaceList result) {
      List<String> namespaces = new ArrayList<>();
      if (result != null) {
        for (V1Namespace ns:result.getItems()) {
          namespaces.add(ns.getMetadata().getName());
        }
      }
      return namespaces;
    }
  }
  
  private static class CreateNamespaceWatcherStep extends Step {
    private final DomainNamespaceSelectionStrategy selectionStrategy;
    private final String initialResourceVersion;

    CreateNamespaceWatcherStep(DomainNamespaceSelectionStrategy selectionStrategy, String initialResourceVersion) {
      this.selectionStrategy = selectionStrategy;
      this.initialResourceVersion = initialResourceVersion;
    }

    @Override
    public NextAction apply(Packet packet) {
      if (namespaceWatcher == null) {
        namespaceWatcher = createNamespaceWatcher(selectionStrategy, initialResourceVersion);
      }
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
    public String getOperatorNamespace() {
      return operatorNamespace;
    }

    @Override
    public PodAwaiterStepFactory getPodAwaiterStepFactory(String namespace) {
      return podWatchers.get(namespace);
    }

    @Override
    public V1SubjectRulesReviewStatus getSubjectRulesReviewStatus(String namespace) {
      NamespaceStatus namespaceStatus = namespaceStatuses.get(namespace);
      return namespaceStatus != null ? namespaceStatus.getRulesReviewStatus().get() : null;
    }

    @Override
    public boolean isNamespaceRunning(String namespace) {
      // make sure the map entry is initialized the value to "false" if absent
      return !isNamespaceStopping(namespace).get();
    }

    @Override
    public KubernetesVersion getVersion() {
      return version;
    }

    @Override
    public SemanticVersion getProductVersion() {
      return productVersion;
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
