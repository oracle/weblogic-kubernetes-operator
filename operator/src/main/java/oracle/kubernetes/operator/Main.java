// Copyright (c) 2017, 2021, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintStream;
import java.security.KeyPair;
import java.security.PrivateKey;
import java.security.cert.CertificateEncodingException;
import java.security.cert.X509Certificate;
import java.time.OffsetDateTime;
import java.util.Base64;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.Semaphore;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import javax.annotation.Nonnull;

import io.kubernetes.client.custom.V1Patch;
import io.kubernetes.client.openapi.models.CoreV1EventList;
import io.kubernetes.client.openapi.models.V1Namespace;
import io.kubernetes.client.openapi.models.V1NamespaceList;
import io.kubernetes.client.openapi.models.V1ObjectMeta;
import io.kubernetes.client.openapi.models.V1Secret;
import io.kubernetes.client.util.Watch;
import jakarta.json.Json;
import jakarta.json.JsonPatchBuilder;
import oracle.kubernetes.operator.calls.CallResponse;
import oracle.kubernetes.operator.calls.FailureStatusSourceException;
import oracle.kubernetes.operator.helpers.CallBuilder;
import oracle.kubernetes.operator.helpers.ClientPool;
import oracle.kubernetes.operator.helpers.CrdHelper;
import oracle.kubernetes.operator.helpers.HealthCheckHelper;
import oracle.kubernetes.operator.helpers.KubernetesUtils;
import oracle.kubernetes.operator.helpers.KubernetesVersion;
import oracle.kubernetes.operator.helpers.PodHelper;
import oracle.kubernetes.operator.helpers.ResponseStep;
import oracle.kubernetes.operator.helpers.SemanticVersion;
import oracle.kubernetes.operator.logging.LoggingContext;
import oracle.kubernetes.operator.logging.LoggingFacade;
import oracle.kubernetes.operator.logging.LoggingFactory;
import oracle.kubernetes.operator.logging.MessageKeys;
import oracle.kubernetes.operator.rest.RestConfigImpl;
import oracle.kubernetes.operator.rest.RestServer;
import oracle.kubernetes.operator.steps.DefaultResponseStep;
import oracle.kubernetes.operator.utils.SelfSignedCertGenerator;
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
import oracle.kubernetes.utils.SystemClock;
import oracle.kubernetes.weblogic.domain.model.DomainList;

import static oracle.kubernetes.operator.helpers.NamespaceHelper.getOperatorNamespace;
import static oracle.kubernetes.operator.logging.MessageKeys.CERTIFICATE_ENCODING_FAILED;
import static oracle.kubernetes.operator.logging.MessageKeys.INTERNAL_CERTIFICATE_GENERATION_FAILED;
import static oracle.kubernetes.operator.utils.Certificates.INTERNAL_CERTIFICATE;
import static oracle.kubernetes.operator.utils.Certificates.INTERNAL_CERTIFICATE_KEY;
import static oracle.kubernetes.operator.utils.SelfSignedCertGenerator.createKeyPair;
import static oracle.kubernetes.operator.utils.SelfSignedCertGenerator.writePem;
import static oracle.kubernetes.operator.utils.SelfSignedCertGenerator.writeStringToFile;

/** A Kubernetes Operator for WebLogic. */
public class Main {
  private static final LoggingFacade LOGGER = LoggingFactory.getLogger("Operator", "Operator");
  static final String GIT_BUILD_VERSION_KEY = "git.build.version";
  static final String GIT_BRANCH_KEY = "git.branch";
  static final String GIT_COMMIT_KEY = "git.commit.id.abbrev";
  static final String GIT_BUILD_TIME_KEY = "git.build.time";

  private static final Container container = new Container();
  private static final ThreadFactory threadFactory = new WrappedThreadFactory();
  private static final ScheduledExecutorService wrappedExecutorService =
      Engine.wrappedExecutorService("operator", container);
  private static final AtomicReference<OffsetDateTime> lastFullRecheck =
      new AtomicReference<>(SystemClock.now());
  private static final Semaphore shutdownSignal = new Semaphore(0);
  private static final int DEFAULT_STUCK_POD_RECHECK_SECONDS = 30;
  public static final String OPERATOR_CM = "weblogic-operator-cm";
  public static final String OPERATOR_SECRETS = "weblogic-operator-secrets";

  private final MainDelegate delegate;
  private final StuckPodProcessing stuckPodProcessing;
  private NamespaceWatcher namespaceWatcher;
  protected OperatorEventWatcher operatorNamespaceEventWatcher;
  private boolean warnedOfCrdAbsence;

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

  Object getOperatorNamespaceEventWatcher() {
    return operatorNamespaceEventWatcher;
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

    private void logStartup(LoggingFacade loggingFacade) {
      loggingFacade.info(MessageKeys.OPERATOR_STARTED, buildVersion, operatorImpl, operatorBuildTime);
      Optional.ofNullable(TuningParameters.getInstance().getFeatureGates().getEnabledFeatures())
          .ifPresent(ef -> loggingFacade.info(MessageKeys.ENABLED_FEATURES, ef));
      loggingFacade.info(MessageKeys.OP_CONFIG_NAMESPACE, getOperatorNamespace());
      loggingFacade.info(MessageKeys.OP_CONFIG_SERVICE_ACCOUNT, serviceAccountName);
      Optional.ofNullable(Namespaces.getConfiguredDomainNamespaces())
            .ifPresent(strings -> logConfiguredNamespaces(loggingFacade, strings));
    }

    private void logConfiguredNamespaces(LoggingFacade loggingFacade, Collection<String> configuredDomainNamespaces) {
      loggingFacade.info(MessageKeys.OP_CONFIG_DOMAIN_NAMESPACES,
          configuredDomainNamespaces.stream().collect(Collectors.joining(", ")));
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
    Main main = createMain(getBuildProperties());

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
    try (final InputStream stream = Main.class.getResourceAsStream("/version.properties")) {
      Properties buildProps = new Properties();
      buildProps.load(stream);
      return buildProps;
    } catch (IOException e) {
      LOGGER.warning(MessageKeys.EXCEPTION, e);
      return null;
    }
  }

  static @Nonnull Main createMain(Properties buildProps) {
    final MainDelegateImpl delegate = new MainDelegateImpl(buildProps, wrappedExecutorService);

    delegate.logStartup(LOGGER);
    return new Main(delegate);
  }

  DomainNamespaces getDomainNamespaces() {
    return delegate.getDomainNamespaces();
  }

  Main(MainDelegate delegate) {
    this.delegate = delegate;
    stuckPodProcessing = new StuckPodProcessing(delegate);
  }

  void startOperator(Runnable completionAction) {
    try {
      delegate.runSteps(new Packet(), createStartupSteps(), completionAction);
    } catch (Throwable e) {
      LOGGER.warning(MessageKeys.EXCEPTION, e);
    }
  }

  private Step createStartupSteps() {
    return createInternalCertStep(Namespaces.getSelection(new StartupStepsVisitor()));
  }

  private Step createInternalCertStep(Step next) {
    return new InternalCertStep(next);
  }

  private Step createOperatorNamespaceEventListStep() {
    return new CallBuilder()
        .withLabelSelectors(ProcessingConstants.OPERATOR_EVENT_LABEL_FILTER)
        .listEventAsync(getOperatorNamespace(), new EventListResponseStep(delegate.getDomainProcessor()));
  }

  private class EventListResponseStep extends ResponseStep<CoreV1EventList> {
    DomainProcessor processor;

    EventListResponseStep(DomainProcessor processor) {
      this.processor = processor;
    }

    @Override
    public NextAction onSuccess(Packet packet, CallResponse<CoreV1EventList> callResponse) {
      CoreV1EventList list = callResponse.getResult();
      operatorNamespaceEventWatcher = startWatcher(getOperatorNamespace(), KubernetesUtils.getResourceVersion(list));
      list.getItems().forEach(DomainProcessorImpl::updateEventK8SObjects);
      return doContinueListOrNext(callResponse, packet);
    }

    OperatorEventWatcher startWatcher(String ns, String resourceVersion) {
      return OperatorEventWatcher.create(DomainNamespaces.getThreadFactory(), ns,
          resourceVersion, DomainNamespaces.getWatchTuning(), processor::dispatchEventWatch, null);
    }
  }

  private class StartupStepsVisitor implements NamespaceStrategyVisitor<Step> {

    @Override
    public Step getDedicatedStrategySelection() {
      return createDomainRecheckSteps();
    }

    @Override
    public Step getDefaultSelection() {
      return Step.chain(
            new CallBuilder().listNamespaceAsync(new StartNamespaceWatcherStep()),
            createOperatorNamespaceEventListStep(),
            createDomainRecheckSteps());
    }
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
      int stuckPodInterval = getStuckPodInterval();
      delegate.scheduleWithFixedDelay(recheckDomains(), recheckInterval, recheckInterval, TimeUnit.SECONDS);
      delegate.scheduleWithFixedDelay(checkStuckPods(), stuckPodInterval, stuckPodInterval, TimeUnit.SECONDS);

      markReadyAndStartLivenessThread();

    } catch (Throwable e) {
      LOGGER.warning(MessageKeys.EXCEPTION, e);
    }
  }

  private int getStuckPodInterval() {
    return Optional.ofNullable(TuningParameters.getInstance())
          .map(TuningParameters::getMainTuning)
          .map(t -> t.stuckPodRecheckSeconds)
          .orElse(DEFAULT_STUCK_POD_RECHECK_SECONDS);
  }

  NamespaceWatcher getNamespaceWatcher() {
    return namespaceWatcher;
  }

  private static NullCompletionCallback andThenDo(Runnable completionAction) {
    return new NullCompletionCallback(completionAction);
  }

  Runnable recheckDomains() {
    return () -> delegate.runSteps(createDomainRecheckSteps());
  }

  Runnable checkStuckPods() {
    return () -> getDomainNamespaces().getNamespaces().forEach(stuckPodProcessing::checkStuckPods);
  }


  Step createDomainRecheckSteps() {
    return createDomainRecheckSteps(OffsetDateTime.now());
  }

  private Step createDomainRecheckSteps(OffsetDateTime now) {
    int recheckInterval = TuningParameters.getInstance().getMainTuning().domainPresenceRecheckIntervalSeconds;
    boolean isFullRecheck = false;
    if (lastFullRecheck.get().plusSeconds(recheckInterval).isBefore(now)) {
      delegate.getDomainProcessor().reportSuspendedFibers();
      isFullRecheck = true;
      lastFullRecheck.set(now);
    }

    final DomainRecheck domainRecheck = new DomainRecheck(delegate, isFullRecheck);
    return Step.chain(
        domainRecheck.createOperatorNamespaceReview(),
        CrdHelper.createDomainCrdStep(delegate.getKubernetesVersion(), delegate.getProductVersion()),
        createCRDPresenceCheck(),
        domainRecheck.createReadNamespacesStep());
  }

  // Returns a step that verifies the presence of an installed domain CRD. It does this by attempting to list the
  // domains in the operator's namespace. That should succeed (although usually returning an empty list)
  // if the CRD is present.
  Step createCRDPresenceCheck() {
    return new CallBuilder().listDomainAsync(getOperatorNamespace(), new CrdPresenceResponseStep());
  }

  // on failure, aborts the processing.
  class CrdPresenceResponseStep extends DefaultResponseStep<DomainList> {

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


  /**
   * Returns true if the operator is configured to use a single dedicated namespace for both itself any any domains.
   * @return true, if selection strategy is dedicated mode.
   */
  public static boolean isDedicated() {
    return Namespaces.SelectionStrategy.Dedicated.equals(Namespaces.getSelectionStrategy());
  }

  private void startRestServer(String principal)
      throws Exception {
    RestServer.create(new RestConfigImpl(principal, delegate.getDomainNamespaces()::getNamespaces));
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

  private void waitForDeath() {
    Runtime.getRuntime().addShutdownHook(new Thread(shutdownSignal::release));

    try {
      shutdownSignal.acquire();
    } catch (InterruptedException ignore) {
      Thread.currentThread().interrupt();
    }

    stopAllWatchers();
  }

  private void stopAllWatchers() {
    delegate.getDomainNamespaces().stopAllWatchers();
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
              new DomainRecheck(delegate, true).createStartNamespacesStep(Collections.singletonList(ns)),
              null);
        break;

      case "DELETED":
        // Mark the namespace as isStopping, which will cause the namespace be stopped
        // the next time when recheckDomains is triggered
        delegate.getDomainNamespaces().isStopping(ns).set(true);

        break;

      case "MODIFIED":
      case "ERROR":
      default:
    }
  }

  static Packet createPacketWithLoggingContext(String ns) {
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

  public static class InternalCertStep extends Step {

    public static final String SHA_256_WITH_RSA = "SHA256withRSA";
    public static final String INTERNAL_WEBLOGIC_OPERATOR_SVC = "internal-weblogic-operator-svc";

    public InternalCertStep(Step next) {
      super(next);
    }

    @Override
    public NextAction apply(Packet packet) {
      X509Certificate cert = null;
      KeyPair keyPair = null;
      try {
        keyPair = createKeyPair();
        writePem(keyPair.getPrivate(), new File(INTERNAL_CERTIFICATE_KEY));
        cert = SelfSignedCertGenerator.generate(keyPair, SHA_256_WITH_RSA, INTERNAL_WEBLOGIC_OPERATOR_SVC, 3650);
        writeStringToFile(getBase64Encoded(cert), new File(INTERNAL_CERTIFICATE));
      } catch (Exception e) {
        LOGGER.severe(INTERNAL_CERTIFICATE_GENERATION_FAILED, e.getMessage());
      }

      return doNext(recordInternalOperatorCert(cert, recordInternalOperatorKey(keyPair, getNext())), packet);
    }
  }

  private static Step recordInternalOperatorCert(X509Certificate cert, Step next) {
    JsonPatchBuilder patchBuilder = Json.createPatchBuilder();

    try {
      patchBuilder.add("/data/internalOperatorCert", getBase64Encoded(cert));
    } catch (CertificateEncodingException cce) {
      LOGGER.severe(CERTIFICATE_ENCODING_FAILED, cce.getMessage());
    }

    return new CallBuilder()
            .patchConfigMapAsync(OPERATOR_CM, getOperatorNamespace(),
                    null,
                    new V1Patch(patchBuilder.build().toString()), new DefaultResponseStep<>(next));
  }

  private static String getBase64Encoded(X509Certificate cert) throws CertificateEncodingException {
    return cert != null ? Base64.getEncoder().encodeToString(cert.getEncoded()) : "";
  }

  private static Step recordInternalOperatorKey(KeyPair keyPair, Step next) {
    return new CallBuilder().readSecretAsync(OPERATOR_SECRETS,
            getOperatorNamespace(), readSecretResponseStep(next, keyPair.getPrivate()));
  }

  private static ResponseStep<V1Secret> readSecretResponseStep(Step next, PrivateKey internalOperatorKey) {
    return new ReadSecretResponseStep(next, internalOperatorKey);
  }

  protected static final V1Secret createModel(V1Secret secret, PrivateKey internalOperatorKey) {
    byte[] encodedKey = Base64.getEncoder().encode(internalOperatorKey.getEncoded());
    if (secret == null) {
      Map<String, byte[]> data = new HashMap<>();
      data.put("internalOperatorKey", encodedKey);
      return new V1Secret().kind("Secret").apiVersion("v1").metadata(createMetadata()).data(data);
    } else {
      Map data = Optional.ofNullable(secret.getData()).orElse(new HashMap<>());
      data.put("internalOperatorKey", encodedKey);
      return new V1Secret().kind("Secret").apiVersion("v1").metadata(secret.getMetadata()).data(data);
    }
  }

  private static V1ObjectMeta createMetadata() {
    Map labels = new HashMap<>();
    labels.put("weblogic.operatorName", getOperatorNamespace());
    return new V1ObjectMeta().name(OPERATOR_SECRETS).namespace(getOperatorNamespace())
            .labels(labels);
  }

  private static class ReadSecretResponseStep extends DefaultResponseStep<V1Secret> {
    final PrivateKey internalOperatorKey;

    ReadSecretResponseStep(Step next, PrivateKey internalOperatorKey) {
      super(next);
      this.internalOperatorKey = internalOperatorKey;
    }

    @Override
    public NextAction onSuccess(Packet packet, CallResponse<V1Secret> callResponse) {
      V1Secret existingSecret = callResponse.getResult();
      if (existingSecret == null) {
        return doNext(createSecret(getNext(), internalOperatorKey), packet);
      } else {
        return doNext(replaceSecret(getNext(), existingSecret, internalOperatorKey), packet);
      }
    }

  }

  private static Step createSecret(Step next, PrivateKey internalOperatorKey) {
    return new CallBuilder()
            .createSecretAsync(getOperatorNamespace(),
                    createModel(null, internalOperatorKey), new DefaultResponseStep<>(next));
  }

  private static Step replaceSecret(Step next, V1Secret secret, PrivateKey internalOperatorKey) {
    return new CallBuilder()
            .replaceSecretAsync(OPERATOR_SECRETS, getOperatorNamespace(), createModel(secret, internalOperatorKey),
                    new DefaultResponseStep<>(next));
  }
}
