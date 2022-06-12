// Copyright (c) 2018, 2022, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.stream.Stream;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import io.kubernetes.client.openapi.models.CoreV1Event;
import io.kubernetes.client.openapi.models.V1ConfigMap;
import io.kubernetes.client.openapi.models.V1ObjectMeta;
import io.kubernetes.client.openapi.models.V1ObjectReference;
import io.kubernetes.client.openapi.models.V1Pod;
import io.kubernetes.client.openapi.models.V1PodDisruptionBudget;
import io.kubernetes.client.openapi.models.V1PodDisruptionBudgetList;
import io.kubernetes.client.openapi.models.V1PodList;
import io.kubernetes.client.openapi.models.V1Service;
import io.kubernetes.client.openapi.models.V1ServiceList;
import io.kubernetes.client.util.Watch;
import oracle.kubernetes.common.logging.LoggingFilter;
import oracle.kubernetes.common.logging.MessageKeys;
import oracle.kubernetes.common.logging.OncePerMessageLoggingFilter;
import oracle.kubernetes.operator.calls.UnrecoverableCallException;
import oracle.kubernetes.operator.helpers.ConfigMapHelper;
import oracle.kubernetes.operator.helpers.DomainPresenceInfo;
import oracle.kubernetes.operator.helpers.DomainValidationSteps;
import oracle.kubernetes.operator.helpers.EventHelper;
import oracle.kubernetes.operator.helpers.EventHelper.EventData;
import oracle.kubernetes.operator.helpers.EventHelper.EventItem;
import oracle.kubernetes.operator.helpers.JobHelper;
import oracle.kubernetes.operator.helpers.KubernetesEventObjects;
import oracle.kubernetes.operator.helpers.KubernetesUtils;
import oracle.kubernetes.operator.helpers.NamespaceHelper;
import oracle.kubernetes.operator.helpers.PodDisruptionBudgetHelper;
import oracle.kubernetes.operator.helpers.PodHelper;
import oracle.kubernetes.operator.helpers.SemanticVersion;
import oracle.kubernetes.operator.helpers.ServiceHelper;
import oracle.kubernetes.operator.logging.LoggingFacade;
import oracle.kubernetes.operator.logging.LoggingFactory;
import oracle.kubernetes.operator.logging.ThreadLoggingContext;
import oracle.kubernetes.operator.steps.BeforeAdminServiceStep;
import oracle.kubernetes.operator.steps.DeleteDomainStep;
import oracle.kubernetes.operator.steps.DomainPresenceStep;
import oracle.kubernetes.operator.steps.ManagedServersUpStep;
import oracle.kubernetes.operator.steps.MonitoringExporterSteps;
import oracle.kubernetes.operator.steps.WatchPodReadyAdminStep;
import oracle.kubernetes.operator.tuning.TuningParameters;
import oracle.kubernetes.operator.work.Component;
import oracle.kubernetes.operator.work.Fiber;
import oracle.kubernetes.operator.work.Fiber.CompletionCallback;
import oracle.kubernetes.operator.work.FiberGate;
import oracle.kubernetes.operator.work.NextAction;
import oracle.kubernetes.operator.work.Packet;
import oracle.kubernetes.operator.work.Step;
import oracle.kubernetes.weblogic.domain.model.DomainResource;
import oracle.kubernetes.weblogic.domain.model.DomainSpec;
import oracle.kubernetes.weblogic.domain.model.DomainStatus;
import oracle.kubernetes.weblogic.domain.model.ServerHealth;
import oracle.kubernetes.weblogic.domain.model.ServerStatus;
import org.jetbrains.annotations.NotNull;

import static oracle.kubernetes.operator.DomainPresence.getDomainPresenceFailureRetrySeconds;
import static oracle.kubernetes.operator.DomainStatusUpdater.createInternalFailureSteps;
import static oracle.kubernetes.operator.DomainStatusUpdater.createIntrospectionFailureSteps;
import static oracle.kubernetes.operator.DomainStatusUpdater.createStatusInitializationStep;
import static oracle.kubernetes.operator.DomainStatusUpdater.createStatusUpdateStep;
import static oracle.kubernetes.operator.LabelConstants.INTROSPECTION_STATE_LABEL;
import static oracle.kubernetes.operator.ProcessingConstants.DOMAIN_INTROSPECT_REQUESTED;
import static oracle.kubernetes.operator.ProcessingConstants.FATAL_INTROSPECTOR_ERROR;
import static oracle.kubernetes.operator.ProcessingConstants.MAKE_RIGHT_DOMAIN_OPERATION;
import static oracle.kubernetes.operator.ProcessingConstants.SERVER_HEALTH_MAP;
import static oracle.kubernetes.operator.ProcessingConstants.SERVER_STATE_MAP;
import static oracle.kubernetes.operator.helpers.PodHelper.getPodDomainUid;
import static oracle.kubernetes.operator.helpers.PodHelper.getPodName;
import static oracle.kubernetes.operator.helpers.PodHelper.getPodNamespace;
import static oracle.kubernetes.operator.helpers.PodHelper.getPodStatusMessage;
import static oracle.kubernetes.operator.logging.ThreadLoggingContext.setThreadContext;

public class DomainProcessorImpl implements DomainProcessor {

  private static final LoggingFacade LOGGER = LoggingFactory.getLogger("Operator", "Operator");

  private static final String ADDED = "ADDED";
  private static final String MODIFIED = "MODIFIED";
  private static final String DELETED = "DELETED";
  private static final String ERROR = "ERROR";

  /** A map that holds at most one FiberGate per namespace to run make-right steps. */
  private static final Map<String, FiberGate> makeRightFiberGates = new ConcurrentHashMap<>();

  /** A map that holds at most one FiberGate per namespace to run status update steps. */
  private static final Map<String, FiberGate> statusFiberGates = new ConcurrentHashMap<>();

  // Map namespace to map of domainUID to Domain; tests may replace this value.
  @SuppressWarnings({"FieldMayBeFinal", "CanBeFinal"})
  private static Map<String, Map<String, DomainPresenceInfo>> domains = new ConcurrentHashMap<>();

  // map namespace to map of uid to processing.
  private static final Map<String, Map<String, ScheduledFuture<?>>> statusUpdaters = new ConcurrentHashMap<>();
  private final DomainProcessorDelegate delegate;
  private final SemanticVersion productVersion;

  // Map namespace to map of domainUID to KubernetesEventObjects; tests may replace this value.
  @SuppressWarnings({"FieldMayBeFinal", "CanBeFinal"})
  private static Map<String, Map<String, KubernetesEventObjects>> domainEventK8SObjects = new ConcurrentHashMap<>();

  // Map namespace to KubernetesEventObjects; tests may replace this value.
  @SuppressWarnings({"FieldMayBeFinal", "CanBeFinal"})
  private static Map<String, KubernetesEventObjects> namespaceEventK8SObjects = new ConcurrentHashMap<>();

  public DomainProcessorImpl(DomainProcessorDelegate delegate) {
    this(delegate, null);
  }

  public DomainProcessorImpl(DomainProcessorDelegate delegate, SemanticVersion productVersion) {
    this.delegate = delegate;
    this.productVersion = productVersion;
  }

  private static DomainPresenceInfo getExistingDomainPresenceInfo(String ns, String domainUid) {
    return domains.computeIfAbsent(ns, k -> new ConcurrentHashMap<>()).get(domainUid);
  }

  private static DomainPresenceInfo getExistingDomainPresenceInfo(DomainPresenceInfo newPresence) {
    return getExistingDomainPresenceInfo(newPresence.getNamespace(), newPresence.getDomainUid());
  }

  static void cleanupNamespace(String namespace) {
    domains.remove(namespace);
    domainEventK8SObjects.remove(namespace);
    namespaceEventK8SObjects.remove(namespace);
    statusUpdaters.remove((namespace));
  }

  static void registerDomainPresenceInfo(DomainPresenceInfo info) {
    domains
          .computeIfAbsent(info.getNamespace(), k -> new ConcurrentHashMap<>())
          .put(info.getDomainUid(), info);
  }

  private static void registerStatusUpdater(
        String ns, String domainUid, ScheduledFuture<?> future) {
    ScheduledFuture<?> existing =
          statusUpdaters.computeIfAbsent(ns, k -> new ConcurrentHashMap<>()).put(domainUid, future);
    if (existing != null) {
      existing.cancel(false);
    }
  }

  public static void updateEventK8SObjects(CoreV1Event event) {
    getEventK8SObjects(event).update(event);
  }

  private static String getEventNamespace(CoreV1Event event) {
    return Optional.ofNullable(event).map(CoreV1Event::getMetadata).map(V1ObjectMeta::getNamespace).orElse(null);
  }

  private static String getEventDomainUid(CoreV1Event event) {
    return Optional.ofNullable(event)
        .map(CoreV1Event::getMetadata)
        .map(V1ObjectMeta::getLabels)
        .orElse(Collections.emptyMap())
        .get(LabelConstants.DOMAINUID_LABEL);
  }

  public static KubernetesEventObjects getEventK8SObjects(CoreV1Event event) {
    return getEventK8SObjects(getEventNamespace(event), getEventDomainUid(event));
  }

  private static KubernetesEventObjects getEventK8SObjects(String ns, String domainUid) {
    return Optional.ofNullable(domainUid)
        .map(d -> getDomainEventK8SObjects(ns, d))
        .orElse(getNamespaceEventK8SObjects(ns));
  }

  private static KubernetesEventObjects getNamespaceEventK8SObjects(String ns) {
    return namespaceEventK8SObjects.computeIfAbsent(ns, d -> new KubernetesEventObjects());
  }

  private static KubernetesEventObjects getDomainEventK8SObjects(String ns, String domainUid) {
    return domainEventK8SObjects.computeIfAbsent(ns, k -> new ConcurrentHashMap<>())
        .computeIfAbsent(domainUid, d -> new KubernetesEventObjects());
  }

  private static void deleteEventK8SObjects(CoreV1Event event) {
    getEventK8SObjects(event).remove(event);
  }

  private static void onCreateModifyEvent(CoreV1Event event) {
    V1ObjectReference ref = event.getInvolvedObject();

    if (ref == null || ref.getName() == null) {
      return;
    }

    String kind = ref.getKind();
    if (kind == null) {
      return;
    }

    switch (kind) {
      case EventConstants.EVENT_KIND_POD:
        processPodEvent(event);
        break;
      case EventConstants.EVENT_KIND_DOMAIN:
      case EventConstants.EVENT_KIND_NAMESPACE:
        updateEventK8SObjects(event);
        break;
      default:
        break;
    }
  }

  private static void processPodEvent(CoreV1Event event) {
    V1ObjectReference ref = event.getInvolvedObject();

    if (ref == null || ref.getName() == null) {
      return;
    }
    if (ref.getName().equals(NamespaceHelper.getOperatorPodName())) {
      updateEventK8SObjects(event);
    } else {
      processServerEvent(event);
    }
  }

  private static void processServerEvent(CoreV1Event event) {
    String[] domainAndServer = Objects.requireNonNull(event.getInvolvedObject().getName()).split("-");
    String domainUid = domainAndServer[0];
    String serverName = domainAndServer[1];
    String status = getReadinessStatus(event);
    if (status == null) {
      return;
    }

    Optional.ofNullable(domains.get(event.getMetadata().getNamespace()))
          .map(m -> m.get(domainUid))
          .ifPresent(info -> info.updateLastKnownServerStatus(serverName, status));
  }

  private void onDeleteEvent(CoreV1Event event) {
    V1ObjectReference ref = event.getInvolvedObject();

    if (ref == null || ref.getName() == null) {
      return;
    }

    String kind = ref.getKind();
    if (kind == null) {
      return;
    }

    switch (kind) {
      case EventConstants.EVENT_KIND_DOMAIN:
      case EventConstants.EVENT_KIND_NAMESPACE:
        deleteEventK8SObjects(event);
        break;
      case EventConstants.EVENT_KIND_POD:
        if (ref.getName().equals(NamespaceHelper.getOperatorPodName())) {
          deleteEventK8SObjects(event);
        }
        break;
      default:
        break;
    }
  }

  private static String getReadinessStatus(CoreV1Event event) {
    return Optional.ofNullable(event.getMessage())
          .filter(m -> m.contains(WebLogicConstants.READINESS_PROBE_NOT_READY_STATE))
          .map(m -> m.substring(m.lastIndexOf(':') + 1).trim())
          .orElse(null);
  }

  // pre-conditions: DomainPresenceInfo SPI
  // "principal"
  static Step bringAdminServerUp(
        DomainPresenceInfo info, PodAwaiterStepFactory podAwaiterStepFactory) {
    return bringAdminServerUpSteps(info, podAwaiterStepFactory);
  }

  private static Step domainIntrospectionSteps(DomainPresenceInfo info) {
    return Step.chain(
          ConfigMapHelper.readIntrospectionVersionStep(info.getNamespace(), info.getDomainUid()),
          new IntrospectionRequestStep(info),
          JobHelper.createIntrospectionStartStep(null));
  }

  private static Step createOrReplaceFluentdConfigMapStep(DomainPresenceInfo info) {
    return ConfigMapHelper.createOrReplaceFluentdConfigMapStep();
  }

  @Override
  public void runMakeRight(MakeRightDomainOperation operation) {
    final DomainPresenceInfo presenceInfo = operation.getPresenceInfo();
    try (ThreadLoggingContext ignored = setThreadContext().presenceInfo(presenceInfo)) {
      if (!this.delegate.isNamespaceRunning(presenceInfo.getNamespace())) {
        return;
      }

      if (shouldContinue(operation)) {
        new DomainPlan(operation, this.delegate).execute();
        scheduleDomainStatusUpdating(presenceInfo);
      } else {
        logNotStartingDomain(presenceInfo.getDomainUid());
      }
    }
  }

  /**
   * Compares the domain introspection version to current introspection state label and request introspection
   * if they don't match.
   */
  private static class IntrospectionRequestStep extends Step {

    private final String requestedIntrospectVersion;

    public IntrospectionRequestStep(DomainPresenceInfo info) {
      this.requestedIntrospectVersion = info.getDomain().getIntrospectVersion();
    }

    @Override
    public NextAction apply(Packet packet) {
      if (!Objects.equals(requestedIntrospectVersion, packet.get(INTROSPECTION_STATE_LABEL))) {
        packet.put(DOMAIN_INTROSPECT_REQUESTED, Optional.ofNullable(requestedIntrospectVersion).orElse("0"));
      }
      return doNext(packet);
    }

    @Nonnull
    private String getRequestedIntrospectVersion(Packet packet) {
      return DomainPresenceInfo.fromPacket(packet)
          .map(DomainPresenceInfo::getDomain)
          .map(DomainResource::getIntrospectVersion)
          .orElse("0");
    }
  }

  private static Step bringAdminServerUpSteps(
        DomainPresenceInfo info, PodAwaiterStepFactory podAwaiterStepFactory) {
    List<Step> steps = new ArrayList<>();
    steps.add(new BeforeAdminServiceStep(null));
    steps.add(PodHelper.createAdminPodStep(null));

    if (info.getDomain().isExternalServiceConfigured()) {
      steps.add(ServiceHelper.createForExternalServiceStep(null));
    }
    steps.add(ServiceHelper.createForServerStep(null));
    steps.add(new WatchPodReadyAdminStep(podAwaiterStepFactory, null));
    return Step.chain(steps.toArray(new Step[0]));
  }

  private static Step bringManagedServersUp() {
    return new ManagedServersUpStep(null);
  }

  private static FiberGate getMakeRightFiberGate(DomainProcessorDelegate delegate, String ns) {
    return makeRightFiberGates.computeIfAbsent(ns, k -> delegate.createFiberGate());
  }

  /**
   * Report on currently suspended fibers. This is the first step toward diagnosing if we need special handling
   * to kill or kick these fibers.
   */
  @Override
  public void reportSuspendedFibers() {
    if (LOGGER.isFineEnabled()) {
      BiConsumer<String, FiberGate> consumer =
          (namespace, gate) -> gate.getCurrentFibers().forEach(
            (key, fiber) -> Optional.ofNullable(fiber.getSuspendedStep()).ifPresent(suspendedStep -> {
              try (ThreadLoggingContext ignored
                  = setThreadContext().namespace(namespace).domainUid(getDomainUid(fiber))) {
                LOGGER.fine("Fiber is SUSPENDED at " + suspendedStep.getResourceName());
              }
            }));
      makeRightFiberGates.forEach(consumer);
      statusFiberGates.forEach(consumer);
    }
  }

  @Override
  public Stream<DomainPresenceInfo> findStrandedDomainPresenceInfos(String namespace, Set<String> domainUids) {
    return Optional.ofNullable(domains.get(namespace)).orElse(Collections.emptyMap())
        .entrySet().stream().filter(e -> !domainUids.contains(e.getKey())).map(Map.Entry::getValue);
  }

  private String getDomainUid(Fiber fiber) {
    return Optional.ofNullable(fiber)
          .map(Fiber::getPacket)
          .map(p -> p.getSpi(DomainPresenceInfo.class))
          .map(DomainPresenceInfo::getDomainUid).orElse("");
  }

  /**
   * Dispatch pod watch event.
   * @param item watch event
   */
  public void dispatchPodWatch(Watch.Response<V1Pod> item) {
    if (getPodLabel(item.object, LabelConstants.DOMAINUID_LABEL) == null) {
      return;
    }

    if (getPodLabel(item.object, LabelConstants.SERVERNAME_LABEL) != null) {
      processServerPodWatch(item.object, item.type);
    } else if (getPodLabel(item.object, LabelConstants.JOBNAME_LABEL) != null) {
      processIntrospectorJobPodWatch(item.object, item.type);
    }
  }

  private void processServerPodWatch(V1Pod pod, String watchType) {
    String domainUid = getPodLabel(pod, LabelConstants.DOMAINUID_LABEL);
    DomainPresenceInfo info = getExistingDomainPresenceInfo(getPodNamespace(pod), domainUid);
    if (info == null) {
      return;
    }

    String serverName = getPodLabel(pod, LabelConstants.SERVERNAME_LABEL);
    switch (watchType) {
      case ADDED:
        info.setServerPodBeingDeleted(serverName, Boolean.FALSE);
        // fall through
      case MODIFIED:
        boolean podPreviouslyEvicted = info.setServerPodFromEvent(serverName, pod, PodHelper::isEvicted);
        if (PodHelper.isEvicted(pod) && !podPreviouslyEvicted) {
          if (PodHelper.shouldRestartEvictedPod(pod)) {
            LOGGER.info(MessageKeys.POD_EVICTED, getPodName(pod), getPodStatusMessage(pod));
            createMakeRightOperation(info).interrupt().withExplicitRecheck().execute();
          } else {
            LOGGER.info(MessageKeys.POD_EVICTED_NO_RESTART, getPodName(pod), getPodStatusMessage(pod));
          }
        }
        break;
      case DELETED:
        boolean removed = info.deleteServerPodFromEvent(serverName, pod);
        if (removed && info.isNotDeleting() && Boolean.FALSE.equals(info.isServerPodBeingDeleted(serverName))) {
          LOGGER.info(MessageKeys.POD_DELETED, domainUid, getPodNamespace(pod), serverName);
          createMakeRightOperation(info).interrupt().withExplicitRecheck().execute();
        }
        break;

      case ERROR:
      default:
    }
  }

  private String getPodLabel(V1Pod pod, String labelName) {
    return Optional.ofNullable(pod)
        .map(V1Pod::getMetadata)
        .map(V1ObjectMeta::getLabels)
        .map(m -> m.get(labelName))
        .orElse(null);
  }

  private void processIntrospectorJobPodWatch(@Nonnull V1Pod pod, String watchType) {
    String domainUid = getPodDomainUid(pod);
    DomainPresenceInfo info = getExistingDomainPresenceInfo(getPodNamespace(pod), domainUid);
    if (info == null) {
      return;
    }

    switch (watchType) {
      case ADDED:
      case MODIFIED:
        updateDomainStatus(pod, info, delegate);
        break;
      case DELETED:
        LOGGER.fine("Introspector Pod " + getPodName(pod) + " for domain " + domainUid + " is deleted.");
        break;
      default:
    }
  }

  private void updateDomainStatus(@Nonnull V1Pod pod, DomainPresenceInfo info, DomainProcessorDelegate delegate) {
    Optional.ofNullable(IntrospectionStatus.createStatusUpdateSteps(pod))
          .ifPresent(steps -> delegate.runSteps(new Packet().with(info), steps, null));
  }


  /* Recently, we've seen a number of intermittent bugs where K8s reports
   * outdated watch events.  There seem to be two main cases: 1) a DELETED
   * event for a resource that was deleted, but has since been recreated, and 2)
   * a MODIFIED event for an object that has already had subsequent modifications.
   */

  /**
   * Dispatch service watch event.
   * @param item watch event
   */
  public void dispatchServiceWatch(Watch.Response<V1Service> item) {
    V1Service service = item.object;
    String domainUid = ServiceHelper.getServiceDomainUid(service);
    String namespace = Optional.ofNullable(service.getMetadata()).map(V1ObjectMeta::getNamespace).orElse(null);
    if (domainUid == null || namespace == null) {
      return;
    }

    DomainPresenceInfo info =
        getExistingDomainPresenceInfo(namespace, domainUid);
    if (info == null) {
      return;
    }

    switch (item.type) {
      case ADDED:
      case MODIFIED:
        ServiceHelper.updatePresenceFromEvent(info, item.object);
        break;
      case DELETED:
        boolean removed = ServiceHelper.deleteFromEvent(info, item.object);
        if (removed && info.isNotDeleting()) {
          createMakeRightOperation(info).interrupt().withExplicitRecheck().execute();
        }
        break;
      default:
    }
  }

  /**
   * Dispatch PodDisruptionBudget watch event.
   * @param item watch event
   */
  public void dispatchPodDisruptionBudgetWatch(Watch.Response<V1PodDisruptionBudget> item) {
    V1PodDisruptionBudget pdb = item.object;
    String domainUid = PodDisruptionBudgetHelper.getDomainUid(pdb);
    if (domainUid == null) {
      return;
    }

    DomainPresenceInfo info =
            getExistingDomainPresenceInfo(getPDBNamespace(pdb), domainUid);
    if (info == null) {
      return;
    }

    switch (item.type) {
      case ADDED:
      case MODIFIED:
        PodDisruptionBudgetHelper.updatePDBFromEvent(info, item.object);
        break;
      case DELETED:
        boolean removed = PodDisruptionBudgetHelper.deleteFromEvent(info, item.object);
        if (removed && info.isNotDeleting()) {
          createMakeRightOperation(info).interrupt().withExplicitRecheck().execute();
        }
        break;
      default:
    }
  }

  private String getPDBNamespace(V1PodDisruptionBudget pdb) {
    return Optional.ofNullable(pdb).map(V1PodDisruptionBudget::getMetadata)
        .map(V1ObjectMeta::getNamespace).orElse(null);
  }

  /**
   * Dispatch config map watch event.
   * @param item watch event
   */
  public void dispatchConfigMapWatch(Watch.Response<V1ConfigMap> item) {
    V1ConfigMap c = item.object;
    if (c != null && c.getMetadata() != null) {
      switch (item.type) {
        case MODIFIED:
        case DELETED:
          delegate.runSteps(
              ConfigMapHelper.createScriptConfigMapStep(
                    c.getMetadata().getNamespace(), productVersion));
          break;

        case ERROR:
        default:
      }
    }
  }

  /**
   * Dispatch event watch event.
   * @param item watch event
   */
  public void dispatchEventWatch(Watch.Response<CoreV1Event> item) {
    CoreV1Event e = item.object;
    if (e != null) {
      switch (item.type) {
        case ADDED:
        case MODIFIED:
          onCreateModifyEvent(e);
          break;
        case DELETED:
          onDeleteEvent(e);
          break;
        case ERROR:
        default:
      }
    }
  }

  /**
   * Dispatch the Domain event to the appropriate handler.
   *
   * @param item An item received from a Watch response.
   */
  public void dispatchDomainWatch(Watch.Response<DomainResource> item) {
    switch (item.type) {
      case ADDED:
        handleAddedDomain(item.object);
        break;
      case MODIFIED:
        handleModifiedDomain(item.object);
        break;
      case DELETED:
        handleDeletedDomain(item.object);
        break;

      case ERROR:
      default:
    }
  }

  private void handleAddedDomain(DomainResource domain) {
    LOGGER.info(MessageKeys.WATCH_DOMAIN, domain.getDomainUid());
    createMakeRightOperation(new DomainPresenceInfo(domain))
        .interrupt()
        .withExplicitRecheck()
        .withEventData(EventItem.DOMAIN_CREATED, null)
        .execute();
  }

  private void handleModifiedDomain(DomainResource domain) {
    LOGGER.fine(MessageKeys.WATCH_DOMAIN, domain.getDomainUid());
    createMakeRightOperation(new DomainPresenceInfo(domain))
        .interrupt()
        .withEventData(EventItem.DOMAIN_CHANGED, null)
        .execute();
  }

  private void handleDeletedDomain(DomainResource domain) {
    LOGGER.info(MessageKeys.WATCH_DOMAIN_DELETED, domain.getDomainUid());
    createMakeRightOperation(new DomainPresenceInfo(domain)).interrupt().forDeletion().withExplicitRecheck()
        .withEventData(EventItem.DOMAIN_DELETED, null)
        .execute();
  }

  private static void logThrowable(Throwable throwable) {
    if (throwable instanceof Step.MultiThrowable) {
      for (Throwable t : ((Step.MultiThrowable) throwable).getThrowables()) {
        logThrowable(t);
      }
    } else if (throwable instanceof UnrecoverableCallException) {
      ((UnrecoverableCallException) throwable).log();
    } else {
      LOGGER.severe(MessageKeys.EXCEPTION, throwable);
    }
  }

  @Override
  public MakeRightDomainOperationImpl createMakeRightOperation(DomainPresenceInfo liveInfo) {
    final MakeRightDomainOperationImpl operation = new MakeRightDomainOperationImpl(delegate, liveInfo);

    if (isFirstDomainNewer(liveInfo, getExistingDomainPresenceInfo(liveInfo))) {
      operation.interrupt();
    }

    return operation;
  }

  private boolean isFirstDomainNewer(DomainPresenceInfo liveInfo, DomainPresenceInfo cachedInfo) {
    return KubernetesUtils.isFirstNewer(getDomainMeta(liveInfo), getDomainMeta(cachedInfo));
  }

  private V1ObjectMeta getDomainMeta(DomainPresenceInfo info) {
    return Optional.ofNullable(info).map(DomainPresenceInfo::getDomain).map(DomainResource::getMetadata).orElse(null);
  }

  public static class PopulatePacketServerMapsStep extends Step {

    @Override
    public NextAction apply(Packet packet) {
      populatePacketServerMapsFromDomain(packet);
      return doNext(packet);
    }

    private void populatePacketServerMapsFromDomain(Packet packet) {
      Map<String, ServerHealth> serverHealth = new ConcurrentHashMap<>();
      Map<String, String> serverState = new ConcurrentHashMap<>();
      DomainPresenceInfo.fromPacket(packet)
          .map(DomainPresenceInfo::getDomain)
          .map(DomainResource::getStatus)
          .map(DomainStatus::getServers)
          .ifPresent(servers -> servers.forEach(item -> addServerToMaps(serverHealth, serverState, item)));
      if (!serverState.isEmpty()) {
        packet.put(SERVER_STATE_MAP, serverState);
      }
      if (!serverHealth.isEmpty()) {
        packet.put(SERVER_HEALTH_MAP, serverHealth);
      }
    }

    private void addServerToMaps(Map<String, ServerHealth> serverHealthMap,
                                 Map<String, String> serverStateMap, ServerStatus item) {
      if (item.getHealth() != null) {
        serverHealthMap.put(item.getServerName(), item.getHealth());
      }
      if (item.getState() != null) {
        serverStateMap.put(item.getServerName(), item.getState());
      }
    }

  }

  private boolean isNewDomain(DomainPresenceInfo cachedInfo) {
    return cachedInfo == null || cachedInfo.getDomain() == null;
  }

  private void logNotStartingDomain(String domainUid) {
    LOGGER.fine(MessageKeys.NOT_STARTING_DOMAINUID_THREAD, domainUid);
  }

  /**
   * A factory which creates and executes steps to align the cached domain status with the value read from Kubernetes.
   */
  class MakeRightDomainOperationImpl implements MakeRightDomainOperation {

    private final DomainProcessorDelegate delegate;
    private DomainPresenceInfo liveInfo;
    private boolean explicitRecheck;
    private boolean deleting;
    private boolean willInterrupt;
    private boolean inspectionRun;
    private EventData eventData;
    private boolean willThrow;

    /**
     * Create the operation.
     * @param delegate a class which handles scheduling and other types of processing
     * @param liveInfo domain presence info read from Kubernetes
     */
    MakeRightDomainOperationImpl(DomainProcessorDelegate delegate, DomainPresenceInfo liveInfo) {
      this.delegate = delegate;
      this.liveInfo = liveInfo;
    }

    @Override
    public MakeRightDomainOperation createRetry(DomainPresenceInfo presenceInfo) {
      presenceInfo.setPopulated(false);
      return new MakeRightDomainOperationImpl(delegate, presenceInfo).withDeleting(deleting).withExplicitRecheck();
    }

    /**
     * Modifies the factory to run even if the domain spec is unchanged.
     * @return the updated factory
     */
    @Override
    public MakeRightDomainOperation withExplicitRecheck() {
      explicitRecheck = true;
      return this;
    }

    /**
     * Set the event data that is associated with this operation.
     * @param eventItem event data
     * @param message event message
     * @return the updated factory
     */
    public MakeRightDomainOperation withEventData(EventItem eventItem, String message) {
      this.eventData = new EventData(eventItem, message);
      return this;
    }

    /**
     * Modifies the factory to handle shutting down the domain.
     * @return the updated factory
     */
    @Override
    public MakeRightDomainOperation forDeletion() {
      deleting = true;
      return this;
    }

    /**
     * Modifies the factory to handle shutting down the domain if the 'deleting' flag is set.
     * @param deleting if true, indicates that the domain is being shut down
     * @return the updated factory
     */
    private MakeRightDomainOperation withDeleting(boolean deleting) {
      this.deleting = deleting;
      return this;
    }

    /**
     * Modifies the factory to indicate that it should interrupt any current make-right thread.
     * @return the updated factory
     */
    public MakeRightDomainOperation interrupt() {
      willInterrupt = true;
      return this;
    }

    @Override
    public boolean isDeleting() {
      return deleting;
    }

    @Override
    public boolean isWillInterrupt() {
      return willInterrupt;
    }

    @Override
    public boolean isExplicitRecheck() {
      return explicitRecheck;
    }

    /**
     * Modifies the factory to indicate that it should throw.
     * For unit testing only.
     *
     * @return the updated factory
     */
    public MakeRightDomainOperation throwNPE() {
      willThrow = true;
      return this;
    }

    @Override
    public void execute() {
      try (ThreadLoggingContext ignored = setThreadContext().presenceInfo(liveInfo)) {
        if (!delegate.isNamespaceRunning(getNamespace())) {
          return;
        }

        if (shouldContinue()) {
          internalMakeRightDomainPresence();
        } else {
          logNotStartingDomain();
        }
      }
    }

    @Override
    public void setInspectionRun() {
      inspectionRun = true;
    }

    @Override
    public DomainPresenceInfo getPresenceInfo() {
      return liveInfo;
    }

    @Override
    public void setLiveInfo(DomainPresenceInfo info) {
      this.liveInfo = info;
    }

    @Override
    public void clear() {
      this.liveInfo = null;
      this.eventData = null;
      this.explicitRecheck = false;
      this.deleting = false;
      this.willInterrupt = false;
      this.inspectionRun = false;
    }


    @Override
    public boolean wasInspectionRun() {
      return inspectionRun;
    }

    private boolean shouldContinue() {
      DomainPresenceInfo cachedInfo = getExistingDomainPresenceInfo(getNamespace(), getDomainUid());

      if (isNewDomain(cachedInfo)) {
        return true;
      } else if (isDomainProcessingAborted(liveInfo) && !isImgRestartIntrospectVerChanged(liveInfo, cachedInfo)) {
        return false;
      } else if (isFatalIntrospectorError()) {
        LOGGER.fine(ProcessingConstants.FATAL_INTROSPECTOR_ERROR_MSG);
        return false;
      } else if (!liveInfo.isPopulated() && isCachedInfoNewer(liveInfo, cachedInfo)) {
        LOGGER.fine("Cached domain info is newer than the live info from the watch event .");
        return false;  // we have already cached this
      } else if (shouldRecheck(cachedInfo)) {
        LOGGER.fine("Continue the make-right domain presence, explicitRecheck -> " + explicitRecheck);
        return true;
      }
      cachedInfo.setDomain(getDomain());
      return false;
    }

    private boolean isDomainProcessingAborted(DomainPresenceInfo info) {
      return Optional.ofNullable(info)
              .map(DomainPresenceInfo::getDomain)
              .map(DomainResource::getStatus)
              .map(DomainStatus::isAborted)
              .orElse(false);
    }

    private boolean shouldRecheck(DomainPresenceInfo cachedInfo) {
      return explicitRecheck || isGenerationChanged(liveInfo, cachedInfo);
    }

    private boolean isFatalIntrospectorError() {
      String existingError = Optional.ofNullable(liveInfo)
          .map(DomainPresenceInfo::getDomain)
          .map(DomainResource::getStatus)
          .map(DomainStatus::getMessage)
          .orElse(null);
      return existingError != null && existingError.contains(FATAL_INTROSPECTOR_ERROR);
    }

    private boolean isNewDomain(DomainPresenceInfo cachedInfo) {
      return cachedInfo == null || cachedInfo.getDomain() == null;
    }

    private void logNotStartingDomain() {
      LOGGER.fine(MessageKeys.NOT_STARTING_DOMAINUID_THREAD, getDomainUid());
    }

    private void internalMakeRightDomainPresence() {
      LOGGER.fine(MessageKeys.PROCESSING_DOMAIN, getDomainUid());

      new DomainPlan(this, delegate).execute();
    }

    @NotNull
    @Override
    public Packet createPacket() {
      Packet packet = new Packet().with(delegate).with(liveInfo);
      packet.put(MAKE_RIGHT_DOMAIN_OPERATION, this);
      packet
          .getComponents()
          .put(
              ProcessingConstants.DOMAIN_COMPONENT_NAME,
              Component.createFor(delegate.getKubernetesVersion(),
                  PodAwaiterStepFactory.class, delegate.getPodAwaiterStepFactory(getNamespace()),
                  JobAwaiterStepFactory.class, delegate.getJobAwaiterStepFactory(getNamespace())));
      return packet;
    }

    private DomainResource getDomain() {
      return liveInfo.getDomain();
    }

    private String getDomainUid() {
      return liveInfo.getDomainUid();
    }

    private String getNamespace() {
      return liveInfo.getNamespace();
    }

    @Override
    public Step createSteps() {
      final List<Step> result = new ArrayList<>();

      result.add(willThrow ? createThrowStep() : null);
      result.add(Optional.ofNullable(eventData).map(EventHelper::createEventStep).orElse(null));
      result.add(new PopulatePacketServerMapsStep());
      result.add(createStatusInitializationStep());
      if (deleting) {
        result.add(new StartPlanStep(liveInfo, createDomainDownPlan(liveInfo)));
      } else {
        result.add(createDomainValidationStep(getDomain()));
        result.add(new StartPlanStep(liveInfo, createDomainUpPlan(liveInfo)));
      }

      return Step.chain(result);
    }

    private Step createDomainDownPlan(DomainPresenceInfo info) {
      String ns = info.getNamespace();
      return Step.chain(
          new DownHeadStep(info, ns),
          new DeleteDomainStep(),
          new UnregisterStep(info));
    }

    private Step createDomainValidationStep(@Nullable DomainResource domain) {
      return domain == null ? null : DomainValidationSteps.createDomainValidationSteps(getNamespace());
    }

    // for unit testing only
    private Step createThrowStep() {
      return new ThrowStep();
    }

    // for unit testing only
    private class ThrowStep extends Step {

      @Override
      public NextAction apply(Packet packet) {
        throw new NullPointerException("Force unit test to handle NPE");
      }
    }
  }

  private boolean shouldContinue(MakeRightDomainOperation operation) {
    DomainPresenceInfo info = operation.getPresenceInfo();
    DomainPresenceInfo cachedInfo = getExistingDomainPresenceInfo(info);

    if (isNewDomain(cachedInfo)) {
      return true;
    } else if (isDomainProcessingAborted(info) && !isImgRestartIntrospectVerChanged(info, cachedInfo)) {
      return false;
    } else if (isFatalIntrospectorError(info)) {
      LOGGER.fine(ProcessingConstants.FATAL_INTROSPECTOR_ERROR_MSG);
      return false;
    } else if (!info.isPopulated() && isCachedInfoNewer(info, cachedInfo)) {
      LOGGER.fine("Cached domain info is newer than the live info from the watch event .");
      return false;  // we have already cached this
    } else if (operation.isExplicitRecheck() || isGenerationChanged(info, cachedInfo)) {
      LOGGER.fine("Continue the make-right domain presence, explicitRecheck -> " + operation.isExplicitRecheck());
      return true;
    }
    cachedInfo.setDomain(info.getDomain());
    return false;
  }

  private boolean isDomainProcessingAborted(DomainPresenceInfo info) {
    return Optional.ofNullable(info)
            .map(DomainPresenceInfo::getDomain)
            .map(DomainResource::getStatus)
            .map(DomainStatus::isAborted)
            .orElse(false);
  }

  private boolean isFatalIntrospectorError(DomainPresenceInfo liveInfo) {
    String existingError = Optional.ofNullable(liveInfo)
        .map(DomainPresenceInfo::getDomain)
        .map(DomainResource::getStatus)
        .map(DomainStatus::getMessage)
        .orElse(null);
    return existingError != null && existingError.contains(FATAL_INTROSPECTOR_ERROR);
  }

  private static boolean isGenerationChanged(DomainPresenceInfo liveInfo, DomainPresenceInfo cachedInfo) {
    return getGeneration(liveInfo)
        .map(gen -> (gen.compareTo(getGeneration(cachedInfo).orElse(0L)) > 0))
        .orElse(true);
  }

  private static Optional<Long> getGeneration(DomainPresenceInfo dpi) {
    return Optional.ofNullable(dpi)
        .map(DomainPresenceInfo::getDomain)
        .map(DomainResource::getMetadata)
        .map(V1ObjectMeta::getGeneration);
  }

  private static boolean isImgRestartIntrospectVerChanged(DomainPresenceInfo liveInfo, DomainPresenceInfo cachedInfo) {
    return !Objects.equals(getIntrospectVersion(liveInfo), getIntrospectVersion(cachedInfo))
        || !Objects.equals(getRestartVersion(liveInfo), getRestartVersion(cachedInfo))
        || !Objects.equals(getIntrospectImage(liveInfo), getIntrospectImage(cachedInfo));
  }

  private static String getIntrospectImage(DomainPresenceInfo info) {
    return Optional.ofNullable(info)
        .map(DomainPresenceInfo::getDomain)
        .map(DomainResource::getSpec)
        .map(DomainSpec::getImage)
        .orElse(null);
  }

  private static String getRestartVersion(DomainPresenceInfo info) {
    return Optional.ofNullable(info)
        .map(DomainPresenceInfo::getDomain)
        .map(DomainResource::getRestartVersion)
        .orElse(null);
  }

  private static String getIntrospectVersion(DomainPresenceInfo info) {
    return Optional.ofNullable(info)
        .map(DomainPresenceInfo::getDomain)
        .map(DomainResource::getSpec)
        .map(DomainSpec::getIntrospectVersion)
        .orElse(null);
  }

  private static boolean isCachedInfoNewer(DomainPresenceInfo liveInfo, DomainPresenceInfo cachedInfo) {
    return liveInfo.getDomain() != null
        && KubernetesUtils.isFirstNewer(cachedInfo.getDomain().getMetadata(), liveInfo.getDomain().getMetadata());
  }

  abstract static class ThrowableCallback implements CompletionCallback {
    @Override
    public final void onCompletion(Packet packet) {
      // no-op
    }
  }

  private static class DomainPlan {

    private final MakeRightDomainOperation operation;
    private final DomainProcessorDelegate delegate;
    private final DomainPresenceInfo presenceInfo;
    private final FiberGate gate;

    private final Step firstStep;
    private final Packet packet;

    public DomainPlan(MakeRightDomainOperation operation, DomainProcessorDelegate delegate) {
      this.operation = operation;
      this.delegate = delegate;
      this.presenceInfo = operation.getPresenceInfo();
      this.firstStep = operation.createSteps();
      this.packet = operation.createPacket();
      this.gate = getMakeRightFiberGate(delegate, this.presenceInfo.getNamespace());
    }

    private void execute() {
      LOGGER.fine(MessageKeys.PROCESSING_DOMAIN, operation.getPresenceInfo().getDomainUid());

      if (operation.isWillInterrupt()) {
        gate.startFiber(presenceInfo.getDomainUid(), firstStep, packet, createCompletionCallback());
      } else {
        gate.startFiberIfNoCurrentFiber(presenceInfo.getDomainUid(), firstStep, packet, createCompletionCallback());
      }
    }

    private CompletionCallback createCompletionCallback() {
      return new DomainPlanCompletionCallback();
    }

    class DomainPlanCompletionCallback extends ThrowableCallback {
      @Override
      public void onThrowable(Packet packet, Throwable throwable) {
        reportFailure(throwable);
        scheduleRetry();
      }

      private void reportFailure(Throwable throwable) {
        logThrowable(throwable);
        runFailureSteps(throwable);
      }
  
      private void runFailureSteps(Throwable throwable) {
        gate.startNewFiberIfCurrentFiberMatches(
            presenceInfo.getDomainUid(),
            getFailureSteps(throwable),
            packet,
            new FailureReportCompletionCallback());
      }

      private Step getFailureSteps(Throwable throwable) {
        if (throwable instanceof IntrospectionJobHolder) {
          return createIntrospectionFailureSteps(throwable, ((IntrospectionJobHolder) throwable).getIntrospectionJob());
        } else {
          return createInternalFailureSteps(throwable);
        }
      }
    }

    class FailureReportCompletionCallback extends ThrowableCallback {
      @Override
      public void onThrowable(Packet packet, Throwable throwable) {
        logThrowable(throwable);
      }
    }

    public void scheduleRetry() {
      Optional.ofNullable(getExistingDomainPresenceInfo()).ifPresent(this::scheduleRetry);
    }

    private void scheduleRetry(@Nonnull DomainPresenceInfo domainPresenceInfo) {
      if (delegate.mayRetry(domainPresenceInfo)) {
        final MakeRightDomainOperation retry = operation.createRetry(domainPresenceInfo);
        gate.getExecutor().schedule(retry::execute, getDomainPresenceFailureRetrySeconds(), TimeUnit.SECONDS);
      }
    }

    private DomainPresenceInfo getExistingDomainPresenceInfo() {
      return DomainProcessorImpl
          .getExistingDomainPresenceInfo(presenceInfo.getNamespace(), presenceInfo.getDomainUid());
    }

  }

  Step createDomainUpPlan(DomainPresenceInfo info) {
    Step managedServerStrategy = Step.chain(
        bringManagedServersUp(),
        MonitoringExporterSteps.updateExporterSidecars(),
        createStatusUpdateStep(new TailStep()));

    Step domainUpStrategy =
        Step.chain(
            createOrReplaceFluentdConfigMapStep(info),
            domainIntrospectionSteps(info),
            DomainValidationSteps.createAfterIntrospectValidationSteps(),
            new DomainStatusStep(info, null),
            bringAdminServerUp(info, delegate.getPodAwaiterStepFactory(info.getNamespace())),
            managedServerStrategy);

    return Step.chain(
          createDomainUpInitialStep(),
          ConfigMapHelper.readExistingIntrospectorConfigMap(info.getNamespace(), info.getDomainUid()),
          DomainPresenceStep.createDomainPresenceStep(info.getDomain(), domainUpStrategy, managedServerStrategy));
  }

  private Step createDomainUpInitialStep() {
    return new UpHeadStep();
  }

  private static class UnregisterStep extends Step {
    private final DomainPresenceInfo info;

    UnregisterStep(DomainPresenceInfo info) {
      this(info, null);
    }

    UnregisterStep(DomainPresenceInfo info, Step next) {
      super(next);
      this.info = info;
    }

    @Override
    public NextAction apply(Packet packet) {
      unregisterDomain(info.getNamespace(), info.getDomainUid());
      return doNext(packet);
    }

    private static void unregisterDomain(String ns, String domainUid) {
      unregisterPresenceInfo(ns, domainUid);
      unregisterEventK8SObject(ns, domainUid);
    }

    private static void unregisterEventK8SObject(String ns, String domainUid) {
      Optional.ofNullable(domainEventK8SObjects.get(ns)).map(m -> m.remove(domainUid));
    }

    private static void unregisterPresenceInfo(String ns, String domainUid) {
      Optional.ofNullable(domains.get(ns)).map(m -> m.remove(domainUid));
    }
  }

  private static class TailStep extends Step {

    @Override
    public NextAction apply(Packet packet) {
      return doNext(packet);
    }
  }

  static class StartPlanStep extends Step {
    private final DomainPresenceInfo info;

    StartPlanStep(DomainPresenceInfo info, Step next) {
      super(next);
      this.info = info;
    }

    @Override
    public NextAction apply(Packet packet) {
      registerDomainPresenceInfo(info);

      return doNext(getNextSteps(), packet);
    }

    private Step getNextSteps() {
      if (lookForPodsAndServices()) {
        return Step.chain(getRecordExistingResourcesSteps(), getNext());
      } else {
        return getNext();
      }
    }

    private boolean lookForPodsAndServices() {
      return !info.isPopulated() && info.isNotDeleting();
    }

    private Step getRecordExistingResourcesSteps() {
      NamespacedResources resources = new NamespacedResources(info.getNamespace(), info.getDomainUid());

      resources.addProcessing(new NamespacedResources.Processors() {
        @Override
        Consumer<V1PodList> getPodListProcessing() {
          return list -> list.getItems().forEach(this::addPod);
        }

        private void addPod(V1Pod pod) {
          Optional.ofNullable(PodHelper.getPodServerName(pod)).ifPresent(name -> info.setServerPod(name, pod));
        }

        @Override
        Consumer<V1ServiceList> getServiceListProcessing() {
          return list -> list.getItems().forEach(this::addService);
        }

        private void addService(V1Service service) {
          ServiceHelper.addToPresence(info, service);
        }

        @Override
        Consumer<V1PodDisruptionBudgetList> getPodDisruptionBudgetListProcessing() {
          return list -> list.getItems().forEach(this::addPodDisruptionBudget);
        }

        private void addPodDisruptionBudget(V1PodDisruptionBudget pdb) {
          PodDisruptionBudgetHelper.addToPresence(info,pdb);
        }
      });

      return resources.createListSteps();
    }

  }

  private static class UpHeadStep extends Step {

    @Override
    public NextAction apply(Packet packet) {
      DomainPresenceInfo.fromPacket(packet).ifPresent(dpi -> dpi.setDeleting(false));
      return doNext(packet);
    }
  }

  private class DomainStatusStep extends Step {
    private final DomainPresenceInfo info;

    DomainStatusStep(DomainPresenceInfo info, Step next) {
      super(next);
      this.info = info;
    }

    @Override
    public NextAction apply(Packet packet) {
      scheduleDomainStatusUpdating(info);
      return doNext(packet);
    }
  }

  private void scheduleDomainStatusUpdating(DomainPresenceInfo info) {
    final int statusUpdateTimeoutSeconds = TuningParameters.getInstance().getStatusUpdateTimeoutSeconds();
    final int initialShortDelay = TuningParameters.getInstance().getInitialShortDelay();
    final OncePerMessageLoggingFilter loggingFilter = new OncePerMessageLoggingFilter();

    registerStatusUpdater(
        info.getNamespace(),
        info.getDomainUid(),
        delegate.scheduleWithFixedDelay(
            () -> new ScheduledStatusUpdater(info.getNamespace(), info.getDomainUid(), loggingFilter)
                .withTimeoutSeconds(statusUpdateTimeoutSeconds).updateStatus(),
            initialShortDelay,
            initialShortDelay,
            TimeUnit.SECONDS));
  }

  private static class DownHeadStep extends Step {
    private final DomainPresenceInfo info;
    private final String ns;

    DownHeadStep(DomainPresenceInfo info, String ns) {
      this(info, ns, null);
    }

    DownHeadStep(DomainPresenceInfo info, String ns, Step next) {
      super(next);
      this.info = info;
      this.ns = ns;
    }

    @Override
    public NextAction apply(Packet packet) {
      info.setDeleting(true);
      unregisterStatusUpdater(ns, info.getDomainUid());
      return doNext(packet);
    }

    private static void unregisterStatusUpdater(String ns, String domainUid) {
      Map<String, ScheduledFuture<?>> map = statusUpdaters.get(ns);
      if (map != null) {
        ScheduledFuture<?> existing = map.remove(domainUid);
        if (existing != null) {
          existing.cancel(true);
        }
      }
    }
  }


  private class ScheduledStatusUpdater {
    private final String namespace;
    private final String domainUid;
    private final OncePerMessageLoggingFilter loggingFilter;
    private int timeoutSeconds;

    ScheduledStatusUpdater withTimeoutSeconds(int timeoutSeconds) {
      this.timeoutSeconds = timeoutSeconds;
      return this;
    }

    public ScheduledStatusUpdater(String namespace, String domainUid, OncePerMessageLoggingFilter loggingFilter) {
      this.namespace = namespace;
      this.domainUid = domainUid;
      this.loggingFilter = loggingFilter;
    }

    private void updateStatus() {
      try {
        Step strategy = Step.chain(new DomainPresenceInfoStep(), ServerStatusReader.createStatusStep(timeoutSeconds));

        getStatusFiberGate(getNamespace())
              .startFiberIfNoCurrentFiber(getDomainUid(), strategy, createPacket(), new CompletionCallbackImpl());
      } catch (Throwable t) {
        try (ThreadLoggingContext ignored
                   = setThreadContext().namespace(getNamespace()).domainUid(getDomainUid())) {
          LOGGER.severe(MessageKeys.EXCEPTION, t);
        }
      }
    }

    private FiberGate getStatusFiberGate(String ns) {
      return statusFiberGates.computeIfAbsent(ns, k -> delegate.createFiberGate());
    }

    private String getNamespace() {
      return namespace;
    }

    private String getDomainUid() {
      return domainUid;
    }

    @NotNull
    private Packet createPacket() {
      Packet packet = new Packet();
      packet
          .getComponents()
          .put(
              ProcessingConstants.DOMAIN_COMPONENT_NAME,
              Component.createFor(delegate.getKubernetesVersion()));
      packet.put(LoggingFilter.LOGGING_FILTER_PACKET_KEY, loggingFilter);
      return packet;
    }

    private class DomainPresenceInfoStep extends Step {
      @Override
      public NextAction apply(Packet packet) {
        Optional.ofNullable(domains.get(getNamespace()))
            .map(n -> n.get(getDomainUid()))
            .ifPresent(i -> i.addToPacket(packet));

        return doNext(packet);
      }
    }

    private class CompletionCallbackImpl implements CompletionCallback {

      @Override
      public void onCompletion(Packet packet) {
        AtomicInteger serverHealthRead = packet.getValue(ProcessingConstants.REMAINING_SERVERS_HEALTH_TO_READ);
        if (serverHealthRead == null || serverHealthRead.get() == 0) {
          loggingFilter.setFiltering(false).resetLogHistory();
        } else {
          loggingFilter.setFiltering(true);
        }
      }

      @Override
      public void onThrowable(Packet packet, Throwable throwable) {
        logThrowable(throwable);
        loggingFilter.setFiltering(true);
      }
    }
  }
}
