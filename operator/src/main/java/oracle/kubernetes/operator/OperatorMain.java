// Copyright (c) 2017, 2025, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator;

import java.io.IOException;
import java.security.KeyManagementException;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.UnrecoverableKeyException;
import java.security.cert.CertificateException;
import java.security.spec.InvalidKeySpecException;
import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Properties;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import javax.annotation.Nonnull;

import io.kubernetes.client.extended.controller.reconciler.Result;
import io.kubernetes.client.openapi.models.CoreV1EventList;
import io.kubernetes.client.openapi.models.V1CustomResourceDefinition;
import io.kubernetes.client.openapi.models.V1CustomResourceDefinitionSpec;
import io.kubernetes.client.openapi.models.V1Namespace;
import io.kubernetes.client.openapi.models.V1NamespaceList;
import io.kubernetes.client.openapi.models.V1ObjectMeta;
import io.kubernetes.client.openapi.models.V1Pod;
import io.kubernetes.client.util.Watch;
import io.kubernetes.client.util.generic.KubernetesApiResponse;
import io.kubernetes.client.util.generic.options.ListOptions;
import oracle.kubernetes.common.logging.MessageKeys;
import oracle.kubernetes.operator.calls.RequestBuilder;
import oracle.kubernetes.operator.calls.ResponseStep;
import oracle.kubernetes.operator.helpers.DomainPresenceInfo;
import oracle.kubernetes.operator.helpers.HelmAccess;
import oracle.kubernetes.operator.helpers.KubernetesUtils;
import oracle.kubernetes.operator.helpers.PodHelper;
import oracle.kubernetes.operator.http.rest.BaseRestServer;
import oracle.kubernetes.operator.http.rest.OperatorRestServer;
import oracle.kubernetes.operator.http.rest.RestConfigImpl;
import oracle.kubernetes.operator.logging.LoggingFacade;
import oracle.kubernetes.operator.steps.DefaultResponseStep;
import oracle.kubernetes.operator.steps.InitializeInternalIdentityStep;
import oracle.kubernetes.operator.tuning.TuningParameters;
import oracle.kubernetes.operator.utils.Certificates;
import oracle.kubernetes.operator.watcher.NamespaceWatcher;
import oracle.kubernetes.operator.watcher.OperatorEventWatcher;
import oracle.kubernetes.operator.work.Cancellable;
import oracle.kubernetes.operator.work.FiberGate;
import oracle.kubernetes.operator.work.Packet;
import oracle.kubernetes.operator.work.Step;
import oracle.kubernetes.weblogic.domain.model.DomainList;

import static oracle.kubernetes.operator.KubernetesConstants.OPERATOR_ENABLE_REST_ENDPOINT_ENV;
import static oracle.kubernetes.operator.ProcessingConstants.WEBHOOK;
import static oracle.kubernetes.operator.helpers.NamespaceHelper.getOperatorNamespace;

/** A Kubernetes Operator for WebLogic. */
public class OperatorMain extends BaseMain {

  private final MainDelegate mainDelegate;
  private final StuckPodProcessing stuckPodProcessing;
  private NamespaceWatcher namespaceWatcher;
  protected OperatorEventWatcher operatorNamespaceEventWatcher;
  @SuppressWarnings({"FieldMayBeFinal", "CanBeFinal"})
  private static NextStepFactory nextStepFactory = OperatorMain::createInitializeInternalIdentityStep;

  /**
   * The interval in sec that the operator will check the CRD presence and log a message if CRD not installed.
   */
  private static final long CRD_DETECTION_DELAY = 10;

  Object getOperatorNamespaceEventWatcher() {
    return operatorNamespaceEventWatcher;
  }

  static class MainDelegateImpl extends CoreDelegateImpl implements MainDelegate, DomainProcessorDelegate {

    private static String getConfiguredServiceAccount() {
      return TuningParameters.getInstance().getServiceAccountName();
    }

    private final String serviceAccountName = Optional.ofNullable(getConfiguredServiceAccount()).orElse("default");
    private final String principal = "system:serviceaccount:" + getOperatorNamespace() + ":" + serviceAccountName;

    private final DomainProcessor domainProcessor;
    private final DomainNamespaces domainNamespaces;
    private final AtomicReference<V1CustomResourceDefinition> crdReference;

    public MainDelegateImpl(Properties buildProps, ScheduledExecutorService executor) {
      super(buildProps, executor);

      domainProcessor = new DomainProcessorImpl(this, productVersion);

      domainNamespaces = new DomainNamespaces(productVersion);

      PodHelper.setProductVersion(productVersion.toString());

      crdReference = new AtomicReference<>();
    }


    @SuppressWarnings("SameParameterValue")
    private void logStartup(LoggingFacade loggingFacade) {
      loggingFacade.info(MessageKeys.OPERATOR_STARTED, buildVersion, deploymentImpl, deploymentBuildTime);
      Optional.ofNullable(TuningParameters.getInstance().getFeatureGates().getEnabledFeatures())
          .ifPresent(ef -> loggingFacade.info(MessageKeys.ENABLED_FEATURES, ef));
      loggingFacade.info(MessageKeys.OP_CONFIG_NAMESPACE, getOperatorNamespace());
      loggingFacade.info(MessageKeys.OP_CONFIG_SERVICE_ACCOUNT, serviceAccountName);
      Optional.ofNullable(Namespaces.getConfiguredDomainNamespaces())
          .ifPresent(strings -> logConfiguredNamespaces(loggingFacade, strings));
    }

    private void logConfiguredNamespaces(LoggingFacade loggingFacade, Collection<String> configuredDomainNamespaces) {
      loggingFacade.info(MessageKeys.OP_CONFIG_DOMAIN_NAMESPACES, String.join(", ", configuredDomainNamespaces));
    }

    @Override
    public String getPrincipal() {
      return principal;
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
    public void updateDomainStatus(V1Pod pod, DomainPresenceInfo info) {
      getDomainProcessor().updateDomainStatus(pod, info);
    }

    @Override
    public boolean isNamespaceRunning(String namespace) {
      return !domainNamespaces.isStopping(namespace).get();
    }

    @Override
    public FiberGate createFiberGate() {
      return new FiberGate(scheduledExecutorService);
    }

    @Override
    public AtomicReference<V1CustomResourceDefinition> getCrdReference() {
      return crdReference;
    }
  }

  /**
   * Entry point.
   *
   * @param args none, ignored
   */
  public static void main(String[] args) {
    createMain(getBuildProperties()).doMain();
  }

  void doMain() {
    try {
      startDeployment(this::completeBegin);

      // now we just wait until the pod is terminated
      waitForDeath();

      stopDeployment(this::completeStop);
    } finally {
      LOGGER.info(MessageKeys.OPERATOR_SHUTTING_DOWN);
    }
  }

  static @Nonnull OperatorMain createMain(Properties buildProps) {
    final MainDelegateImpl delegate = new MainDelegateImpl(buildProps, executor);

    delegate.logStartup(LOGGER);
    return new OperatorMain(delegate);
  }

  DomainNamespaces getDomainNamespaces() {
    return mainDelegate.getDomainNamespaces();
  }

  OperatorMain(MainDelegate mainDelegate) {
    super(mainDelegate);
    this.mainDelegate = mainDelegate;
    stuckPodProcessing = new StuckPodProcessing(mainDelegate);
  }

  @Override
  Step createStartupSteps() {

    return nextStepFactory.createInternalInitializationStep(
        mainDelegate, Namespaces.getSelection(new StartupStepsVisitor()));
  }

  private static Step createInitializeInternalIdentityStep(MainDelegate delegate, Step next) {
    return new InitializeInternalIdentityStep(delegate, next);
  }

  private class EventListResponseStep extends ResponseStep<CoreV1EventList> {
    DomainProcessor processor;

    EventListResponseStep(DomainProcessor processor) {
      this.processor = processor;
    }

    @Override
    public Result onSuccess(Packet packet, KubernetesApiResponse<CoreV1EventList> callResponse) {
      CoreV1EventList list = callResponse.getObject();
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

    private Step createOperatorNamespaceEventListStep() {
      return RequestBuilder.EVENT.list(getOperatorNamespace(),
          new ListOptions().labelSelector(ProcessingConstants.OPERATOR_EVENT_LABEL_FILTER),
          new EventListResponseStep(mainDelegate.getDomainProcessor()));
    }

    @Override
    public Step getDefaultSelection() {
      return Step.chain(
          RequestBuilder.NAMESPACE.list(new StartNamespaceWatcherStep()),
          createOperatorNamespaceEventListStep(),
          createDomainRecheckSteps());
    }
  }

  private class StartNamespaceWatcherStep extends DefaultResponseStep<V1NamespaceList> {

    private NamespaceWatcher createNamespaceWatcher(String initialResourceVersion) {
      return NamespaceWatcher.create(
          threadFactory,
          initialResourceVersion,
          TuningParameters.getInstance().getWatchTuning(),
          OperatorMain.this::dispatchNamespaceWatch,
          new AtomicBoolean(false));
    }

    @Override
    public Result onSuccess(Packet packet, KubernetesApiResponse<V1NamespaceList> callResponse) {
      namespaceWatcher = createNamespaceWatcher(KubernetesUtils.getResourceVersion(callResponse.getObject()));
      return doNext(packet);
    }
  }

  void completeBegin() {
    try {
      // Register metrics collectors
      new NamespaceCollector(mainDelegate).register();
      startMetricsServer();
      startRestServer();

      // start periodic retry and recheck
      int recheckInterval = TuningParameters.getInstance().getDomainNamespaceRecheckIntervalSeconds();
      int stuckPodInterval = TuningParameters.getInstance().getStuckPodRecheckSeconds();
      Collection<Cancellable> futures = List.of(
          mainDelegate.scheduleWithFixedDelay(recheckDomains(), recheckInterval, recheckInterval, TimeUnit.SECONDS),
          mainDelegate.scheduleWithFixedDelay(checkStuckPods(), stuckPodInterval, stuckPodInterval, TimeUnit.SECONDS));

      markReadyAndStartLivenessThread(futures);

    } catch (Throwable e) {
      LOGGER.warning(MessageKeys.EXCEPTION, e);
    }
  }

  @Override
  void startRestServer()
      throws UnrecoverableKeyException, CertificateException, IOException, NoSuchAlgorithmException,
      KeyStoreException, InvalidKeySpecException, KeyManagementException {
    if (Optional.ofNullable(HelmAccess.getHelmVariable(OPERATOR_ENABLE_REST_ENDPOINT_ENV))
        .map(Boolean::valueOf).orElse(Boolean.FALSE)) {
      super.startRestServer();
    }
  }

  void completeStop() {
    stopRestServer();
    stopMetricsServer();
  }

  NamespaceWatcher getNamespaceWatcher() {
    return namespaceWatcher;
  }

  Runnable recheckDomains() {
    return () -> mainDelegate.runSteps(createDomainRecheckSteps());
  }

  Runnable checkStuckPods() {
    return () -> getDomainNamespaces().getNamespaces().forEach(stuckPodProcessing::checkStuckPods);
  }

  Step createDomainRecheckSteps() {
    return createDomainRecheckSteps(OffsetDateTime.now());
  }

  private Step createDomainRecheckSteps(OffsetDateTime now) {
    int recheckInterval = TuningParameters.getInstance().getDomainPresenceRecheckIntervalSeconds();
    boolean isFullRecheck = false;
    if (lastFullRecheck.get().plusSeconds(recheckInterval).isBefore(now)) {
      mainDelegate.getDomainProcessor().reportSuspendedFibers();
      isFullRecheck = true;
      lastFullRecheck.set(now);
    }

    final DomainRecheck domainRecheck = new DomainRecheck(mainDelegate, isFullRecheck);
    return Step.chain(
        domainRecheck.createOperatorNamespaceReview(),
        createCRDPresenceCheck(),
        domainRecheck.createReadNamespacesStep());
  }

  // Returns a step that verifies the presence of an installed domain CRD. It does this by attempting to list the
  // domains in the operator's namespace. That should succeed (although usually returning an empty list)
  // if the CRD is present.
  Step createCRDPresenceCheck() {
    return new CrdPresenceStep();
  }

  class CrdPresenceStep extends Step {

    @Override
    public @Nonnull Result apply(Packet packet) {
      return doNext(
          RequestBuilder.CRD.get(KubernetesConstants.DOMAIN_CRD_NAME, createReadResponseStep(getNext())), packet);
    }
  }

  // If CRD read succeeds, wait until the CRD has webhook.
  // Otherwise, if CRD read fails due to permissions error,
  // list the domains to indirectly check if the CRD is installed.
  ResponseStep<V1CustomResourceDefinition> createReadResponseStep(Step next) {
    return new ReadResponseStep(next);
  }

  class ReadResponseStep extends DefaultResponseStep<V1CustomResourceDefinition> {
    ReadResponseStep(Step next) {
      super(next);
    }

    @Override
    public Result onSuccess(
            Packet packet, KubernetesApiResponse<V1CustomResourceDefinition> callResponse) {
      V1CustomResourceDefinition existingCrd = callResponse.getObject();

      if (!existingCrdContainsConversionWebhook(existingCrd)) {
        LOGGER.info(MessageKeys.WAIT_FOR_CRD_INSTALLATION, CRD_DETECTION_DELAY);
        return doDelay(createCRDPresenceCheck(), packet, CRD_DETECTION_DELAY, TimeUnit.SECONDS);
      } else {
        return doNext(packet);
      }
    }

    private boolean existingCrdContainsConversionWebhook(V1CustomResourceDefinition existingCrd) {
      return Optional.ofNullable(existingCrd)
            .map(V1CustomResourceDefinition::getSpec)
            .map(V1CustomResourceDefinitionSpec::getConversion)
            .map(c -> c.getStrategy().equalsIgnoreCase(WEBHOOK))
            .orElse(false);
    }

    @Override
    protected Result onFailureNoRetry(Packet packet,
                                          KubernetesApiResponse<V1CustomResourceDefinition> callResponse) {
      return isNotAuthorizedOrForbidden(callResponse)
          ? doNext(RequestBuilder.DOMAIN.list(getOperatorNamespace(), new CrdPresenceResponseStep(getNext())), packet)
          : super.onFailureNoRetry(packet, callResponse);
    }
  }

  // on failure, waits for the CRD to be installed.
  private class CrdPresenceResponseStep extends DefaultResponseStep<DomainList> {

    CrdPresenceResponseStep(Step next) {
      super(next);
    }

    @Override
    public Result onFailure(Packet packet, KubernetesApiResponse<DomainList> callResponse) {
      LOGGER.info(MessageKeys.WAIT_FOR_CRD_INSTALLATION, CRD_DETECTION_DELAY);
      return doDelay(createCRDPresenceCheck(), packet, CRD_DETECTION_DELAY, TimeUnit.SECONDS);
    }
  }

  /**
   * Returns true if the operator is configured to use a single dedicated namespace for both itself and any domains.
   * @return true, if selection strategy is dedicated mode.
   */
  public static boolean isDedicated() {
    return Namespaces.SelectionStrategy.DEDICATED.equals(Namespaces.getSelectionStrategy());
  }

  @Override
  protected BaseRestServer createRestServer() {
    return OperatorRestServer.create(
        new RestConfigImpl(mainDelegate.getPrincipal(), mainDelegate.getDomainNamespaces()::getNamespaces,
                new Certificates(mainDelegate)));
  }

  // -----------------------------------------------------------------------------
  //
  // Below this point are methods that are called primarily from watch handlers,
  // after watch events are received.
  //
  // -----------------------------------------------------------------------------

  @Override
  protected void logStartingLivenessMessage() {
    LOGGER.info(MessageKeys.STARTING_LIVENESS_THREAD);
  }

  @Override
  protected void stopAllWatchers() {
    mainDelegate.getDomainNamespaces().stopAllWatchers();
  }

  void dispatchNamespaceWatch(Watch.Response<V1Namespace> item) {
    String ns = Optional.ofNullable(item.object).map(V1Namespace::getMetadata).map(V1ObjectMeta::getName).orElse(null);

    if (ns == null) {
      return;
    }

    switch (item.type) {
      case "ADDED":
        if (!Namespaces.isDomainNamespace(item.object)) {
          return;
        }

        mainDelegate.runSteps(createPacketWithLoggingContext(ns),
              new DomainRecheck(mainDelegate, true).createStartNamespacesStep(Collections.singletonList(ns)),
              null);
        break;

      case "DELETED":
        // Mark the namespace as isStopping, which will cause the namespace be stopped
        // the next time when recheckDomains is triggered
        Optional.ofNullable(mainDelegate.getDomainNamespaces().getStopping(ns)).ifPresent(n -> n.set(true));

        break;

      case "MODIFIED", "ERROR":
      default:
    }
  }

  // an interface to provide a hook for unit testing.
  interface NextStepFactory {
    Step createInternalInitializationStep(MainDelegate delegate, Step next);
  }
}
