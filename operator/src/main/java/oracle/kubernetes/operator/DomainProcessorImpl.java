// Copyright (c) 2018, 2022, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator;

import java.time.Duration;
import java.time.OffsetDateTime;
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
import java.util.stream.Stream;
import javax.annotation.Nonnull;

import io.kubernetes.client.openapi.models.CoreV1Event;
import io.kubernetes.client.openapi.models.V1ConfigMap;
import io.kubernetes.client.openapi.models.V1ObjectMeta;
import io.kubernetes.client.openapi.models.V1ObjectReference;
import io.kubernetes.client.openapi.models.V1Pod;
import io.kubernetes.client.openapi.models.V1PodDisruptionBudget;
import io.kubernetes.client.openapi.models.V1Service;
import io.kubernetes.client.util.Watch;
import oracle.kubernetes.common.logging.LoggingFilter;
import oracle.kubernetes.common.logging.MessageKeys;
import oracle.kubernetes.common.logging.OncePerMessageLoggingFilter;
import oracle.kubernetes.operator.calls.UnrecoverableCallException;
import oracle.kubernetes.operator.helpers.ConfigMapHelper;
import oracle.kubernetes.operator.helpers.DomainPresenceInfo;
import oracle.kubernetes.operator.helpers.EventHelper.EventData;
import oracle.kubernetes.operator.helpers.EventHelper.EventItem;
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
import oracle.kubernetes.operator.steps.WatchPodReadyAdminStep;
import oracle.kubernetes.operator.tuning.TuningParameters;
import oracle.kubernetes.operator.work.Component;
import oracle.kubernetes.operator.work.Fiber;
import oracle.kubernetes.operator.work.Fiber.CompletionCallback;
import oracle.kubernetes.operator.work.FiberGate;
import oracle.kubernetes.operator.work.NextAction;
import oracle.kubernetes.operator.work.Packet;
import oracle.kubernetes.operator.work.Step;
import oracle.kubernetes.utils.SystemClock;
import oracle.kubernetes.weblogic.domain.model.ClusterResource;
import oracle.kubernetes.weblogic.domain.model.DomainResource;
import oracle.kubernetes.weblogic.domain.model.DomainStatus;
import oracle.kubernetes.weblogic.domain.model.ServerHealth;
import oracle.kubernetes.weblogic.domain.model.ServerStatus;
import org.jetbrains.annotations.NotNull;

import static oracle.kubernetes.operator.DomainStatusUpdater.createInternalFailureSteps;
import static oracle.kubernetes.operator.DomainStatusUpdater.createIntrospectionFailureSteps;
import static oracle.kubernetes.operator.ProcessingConstants.SERVER_HEALTH_MAP;
import static oracle.kubernetes.operator.ProcessingConstants.SERVER_STATE_MAP;
import static oracle.kubernetes.operator.helpers.PodHelper.getPodDomainUid;
import static oracle.kubernetes.operator.helpers.PodHelper.getPodName;
import static oracle.kubernetes.operator.helpers.PodHelper.getPodNamespace;
import static oracle.kubernetes.operator.helpers.PodHelper.getPodStatusMessage;
import static oracle.kubernetes.operator.logging.ThreadLoggingContext.setThreadContext;

public class DomainProcessorImpl implements DomainProcessor, MakeRightExecutor {

  private static final LoggingFacade LOGGER = LoggingFactory.getLogger("Operator", "Operator");

  private static final String ADDED = "ADDED";
  private static final String MODIFIED = "MODIFIED";
  private static final String DELETED = "DELETED";
  private static final String ERROR = "ERROR";

  @SuppressWarnings({"FieldCanBeLocal", "FieldMayBeFinal"})
  private static String debugPrefix = null;  // Debugging: set this to a non-null value to dump the make-right steps

  /** A map that holds at most one FiberGate per namespace to run make-right steps. */
  @SuppressWarnings("FieldMayBeFinal")
  private static Map<String, FiberGate> makeRightFiberGates = new ConcurrentHashMap<>();

  /** A map that holds at most one FiberGate per namespace to run status update steps. */
  @SuppressWarnings("FieldMayBeFinal")
  private static Map<String, FiberGate> statusFiberGates = new ConcurrentHashMap<>();

  // Map namespace to map of domainUID to Domain; tests may replace this value.
  @SuppressWarnings({"FieldMayBeFinal", "CanBeFinal"})
  private static Map<String, Map<String, DomainPresenceInfo>> domains = new ConcurrentHashMap<>();


  // map namespace to map of uid to processing.
  @SuppressWarnings("FieldMayBeFinal")
  private static Map<String, Map<String, ScheduledFuture<?>>> statusUpdaters = new ConcurrentHashMap<>();
  private final DomainProcessorDelegate delegate;
  private final SemanticVersion productVersion;

  // Map namespace to map of domainUID to KubernetesEventObjects; tests may replace this value.
  @SuppressWarnings({"FieldMayBeFinal", "CanBeFinal"})
  private static Map<String, Map<String, KubernetesEventObjects>> domainEventK8SObjects = new ConcurrentHashMap<>();

  // Map namespace to KubernetesEventObjects; tests may replace this value.
  @SuppressWarnings({"FieldMayBeFinal", "CanBeFinal"})
  private static Map<String, KubernetesEventObjects> namespaceEventK8SObjects = new ConcurrentHashMap<>();

  // Map namespace to map of cluster resource name to KubernetesEventObjects; tests may replace this value.
  @SuppressWarnings({"FieldMayBeFinal", "CanBeFinal"})
  private static Map<String, Map<String, KubernetesEventObjects>> clusterEventK8SObjects = new ConcurrentHashMap<>();

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

  private static List<DomainPresenceInfo> getExistingDomainPresenceInfoForCluster(String ns, String cluster) {
    List<DomainPresenceInfo> referencingDomains = new ArrayList<>();
    Optional.ofNullable(domains.get(ns)).ifPresent(d -> d.values().stream()
        .filter(info -> info.doesReferenceCluster(cluster)).forEach(referencingDomains::add));
    return referencingDomains;
  }

  static void cleanupNamespace(String namespace) {
    clusterEventK8SObjects.remove(namespace);
    domains.remove(namespace);
    domainEventK8SObjects.remove(namespace);
    namespaceEventK8SObjects.remove(namespace);
    statusUpdaters.remove((namespace));
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

  private static void updateClusterEventK8SObjects(CoreV1Event event) {
    getClusterEventK8SObjects(event).update(event);
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

  public static KubernetesEventObjects getClusterEventK8SObjects(CoreV1Event event) {
    return getClusterEventK8SObjects(getEventNamespace(event), getClusterName(event));
  }

  private static KubernetesEventObjects getClusterEventK8SObjects(String ns, String clusterResourceName) {
    return clusterEventK8SObjects.computeIfAbsent(ns, k -> new ConcurrentHashMap<>())
        .computeIfAbsent(clusterResourceName, c -> new KubernetesEventObjects());
  }

  private static String getClusterName(CoreV1Event event) {
    return Optional.ofNullable(event.getInvolvedObject())
        .map(V1ObjectReference::getName)
        .orElse("");
  }

  private static void deleteClusterEventK8SObjects(CoreV1Event event) {
    getClusterEventK8SObjects(event).remove(event);
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
      case EventConstants.EVENT_KIND_CLUSTER:
        updateClusterEventK8SObjects(event);
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

  /**
   * Get all the domain resources in the given namespace.
   *
   * @param ns the namespace
   * @return list of the domain resources
   */
  public static List<DomainResource> getDomains(String ns) {
    List<DomainResource> domains = new ArrayList<>();
    Optional.ofNullable(DomainProcessorImpl.domains.get(ns)).ifPresent(d -> d.values()
        .forEach(domain -> addToList(domains, domain)));
    return domains;
  }

  private static void addToList(List<DomainResource> list, DomainPresenceInfo info) {
    list.add(info.getDomain());
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
      case EventConstants.EVENT_KIND_CLUSTER:
        deleteClusterEventK8SObjects(event);
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
  public static Step bringAdminServerUp(DomainPresenceInfo info, PodAwaiterStepFactory podAwaiterStepFactory) {
    return bringAdminServerUpSteps(info, podAwaiterStepFactory);
  }

  @Override
  public void runMakeRight(MakeRightDomainOperation operation) {
    final DomainPresenceInfo liveInfo = operation.getPresenceInfo();
    if (delegate.isNamespaceRunning(liveInfo.getNamespace())) {
      try (ThreadLoggingContext ignored = setThreadContext().presenceInfo(liveInfo)) {
        if (shouldContinue(operation, liveInfo)) {
          logStartingDomain(liveInfo);
          new DomainPlan(operation, delegate).execute();
        } else {
          logNotStartingDomain(liveInfo);
        }
      }
    }
  }

  private boolean shouldContinue(MakeRightDomainOperation operation, DomainPresenceInfo liveInfo) {
    final DomainPresenceInfo cachedInfo = getExistingDomainPresenceInfo(liveInfo);
    if (isNewDomain(cachedInfo)) {
      return true;
    } else if (liveInfo.isFromOutOfDateEvent(operation, cachedInfo) || liveInfo.isDomainProcessingHalted(cachedInfo)) {
      return false;
    } else if (operation.isExplicitRecheck() || liveInfo.isDomainGenerationChanged(cachedInfo)) {
      return true;
    } else {
      cachedInfo.setDomain(liveInfo.getDomain());
      return false;
    }
  }

  private boolean isNewDomain(DomainPresenceInfo cachedInfo) {
    return Optional.ofNullable(cachedInfo).map(DomainPresenceInfo::getDomain).orElse(null) == null;
  }

  private void logStartingDomain(DomainPresenceInfo presenceInfo) {
    LOGGER.fine(MessageKeys.PROCESSING_DOMAIN, presenceInfo.getDomainUid());
  }

  private void logNotStartingDomain(DomainPresenceInfo info) {
    LOGGER.fine(MessageKeys.NOT_STARTING_DOMAINUID_THREAD, info.getDomainUid());
  }

  @Override
  public void scheduleDomainStatusUpdates(DomainPresenceInfo info) {
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

  @Override
  public void registerDomainPresenceInfo(DomainPresenceInfo info) {
    domains
          .computeIfAbsent(info.getNamespace(), k -> new ConcurrentHashMap<>())
          .put(info.getDomainUid(), info);
  }

  @Override
  public void unregisterDomainPresenceInfo(DomainPresenceInfo info) {
    unregisterPresenceInfo(info.getNamespace(), info.getDomainUid());
    unregisterEventK8SObject(info.getNamespace(), info.getDomainUid());
  }

  private static void unregisterEventK8SObject(String ns, String domainUid) {
    Optional.ofNullable(domainEventK8SObjects.get(ns)).ifPresent(m -> m.remove(domainUid));
  }

  private static void unregisterPresenceInfo(String ns, String domainUid) {
    Optional.ofNullable(domains.get(ns)).ifPresent(m -> m.remove(domainUid));
  }

  @Override
  public void endScheduledDomainStatusUpdates(DomainPresenceInfo info) {
    Map<String, ScheduledFuture<?>> map = statusUpdaters.get(info.getNamespace());
    if (map != null) {
      ScheduledFuture<?> existing = map.remove(info.getDomainUid());
      if (existing != null) {
        existing.cancel(true);
      }
    }
  }

  private static Step bringAdminServerUpSteps(DomainPresenceInfo info, PodAwaiterStepFactory podAwaiterStepFactory) {
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
   * Dispatch the Cluster event to the appropriate handler.
   *
   * @param item An item received from a Watch response.
   */
  public void dispatchClusterWatch(Watch.Response<ClusterResource> item) {
    switch (item.type) {
      case ADDED:
        handleAddedCluster(item.object);
        break;
      case MODIFIED:
        handleModifiedCluster(item.object);
        break;
      case DELETED:
        handleDeletedCluster(item.object);
        break;

      case ERROR:
      default:
    }
  }

  private void handleAddedCluster(ClusterResource cluster) {
    getExistingDomainPresenceInfoForCluster(cluster.getNamespace(), cluster.getMetadata().getName()).forEach(info -> {
      LOGGER.info(MessageKeys.WATCH_CLUSTER, cluster.getClusterName(), info.getDomainUid());
      createMakeRightOperation(info)
          .interrupt()
          .withExplicitRecheck()
          .withEventData(new EventData(EventItem.CLUSTER_CREATED).resourceName(cluster.getClusterName()))
          .execute();
    });
  }

  private void handleModifiedCluster(ClusterResource cluster) {
    getExistingDomainPresenceInfoForCluster(cluster.getNamespace(), cluster.getMetadata().getName()).forEach(info -> {
      ClusterResource cachedResource = info.getClusterResource(cluster.getClusterName());
      if (cachedResource == null || !cluster.isGenerationChanged(cachedResource)) {
        return;
      }

      LOGGER.fine(MessageKeys.WATCH_CLUSTER, cluster.getClusterName(), info.getDomainUid());
      createMakeRightOperation(info)
          .interrupt()
          .withExplicitRecheck()
          .withEventData(new EventData(EventItem.CLUSTER_CHANGED).resourceName(cluster.getClusterName()))
          .execute();
    });
  }

  private void handleDeletedCluster(ClusterResource cluster) {
    getExistingDomainPresenceInfoForCluster(cluster.getNamespace(), cluster.getMetadata().getName()).forEach(info -> {
      LOGGER.info(MessageKeys.WATCH_CLUSTER_DELETED, cluster.getClusterName(), info.getDomainUid());
      info.removeClusterResource(cluster.getClusterName());
      createMakeRightOperation(info)
          .interrupt()
          .withExplicitRecheck()
          .withEventData(new EventData(EventItem.CLUSTER_DELETED).resourceName(cluster.getClusterName()))
          .execute();
    });
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
        .withEventData(new EventData(EventItem.DOMAIN_CREATED))
        .execute();
  }

  private void handleModifiedDomain(DomainResource domain) {
    LOGGER.fine(MessageKeys.WATCH_DOMAIN, domain.getDomainUid());
    createMakeRightOperation(new DomainPresenceInfo(domain))
        .interrupt()
        .withEventData(new EventData(EventItem.DOMAIN_CHANGED))
        .execute();
  }

  private void handleDeletedDomain(DomainResource domain) {
    LOGGER.info(MessageKeys.WATCH_DOMAIN_DELETED, domain.getDomainUid());
    createMakeRightOperation(new DomainPresenceInfo(domain)).interrupt().forDeletion().withExplicitRecheck()
        .withEventData(new EventData(EventItem.DOMAIN_DELETED))
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
  public MakeRightDomainOperation createMakeRightOperation(DomainPresenceInfo liveInfo) {
    final MakeRightDomainOperation operation = delegate.createMakeRightOperation(this, liveInfo);

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

  private static class DomainPlan {

    private final MakeRightDomainOperation operation;
    private final DomainPresenceInfo presenceInfo;
    private final FiberGate gate;

    private final Step firstStep;
    private final Packet packet;

    public DomainPlan(MakeRightDomainOperation operation, DomainProcessorDelegate delegate) {
      this.operation = operation;
      this.presenceInfo = operation.getPresenceInfo();
      this.firstStep = operation.createSteps();
      this.packet = operation.createPacket();
      this.gate = getMakeRightFiberGate(delegate, this.presenceInfo.getNamespace());
    }

    private static FiberGate getMakeRightFiberGate(DomainProcessorDelegate delegate, String ns) {
      return makeRightFiberGates.computeIfAbsent(ns, k -> delegate.createFiberGate());
    }

    private void execute() {
      LOGGER.fine(MessageKeys.PROCESSING_DOMAIN, operation.getPresenceInfo().getDomainUid());
      Optional.ofNullable(debugPrefix).ifPresent(prefix -> packet.put(Fiber.DEBUG_FIBER, prefix));

      if (operation.isWillInterrupt()) {
        gate.startFiber(presenceInfo.getDomainUid(), firstStep, packet, createCompletionCallback());
      } else {
        gate.startFiberIfNoCurrentFiber(presenceInfo.getDomainUid(), firstStep, packet, createCompletionCallback());
      }
    }

    private CompletionCallback createCompletionCallback() {
      return new DomainPlanCompletionCallback();
    }

    class DomainPlanCompletionCallback implements CompletionCallback {

      @Override
      public void onCompletion(Packet packet) {
        retryIfNeeded(packet);
      }

      @Override
      public void onThrowable(Packet packet, Throwable throwable) {
        reportFailure(throwable);
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

    class FailureReportCompletionCallback implements CompletionCallback {

      @Override
      public void onCompletion(Packet packet) {
        retryIfNeeded(packet);
      }

      @Override
      public void onThrowable(Packet packet, Throwable throwable) {
        logThrowable(throwable);
      }
    }

    public void retryIfNeeded(Packet packet) {
      if (shouldRetry(packet)) {
        DomainPresenceInfo.fromPacket(packet).ifPresent(this::scheduleRetry);
      }
    }

    @Nonnull
    private Boolean shouldRetry(Packet packet) {
      return DomainPresenceInfo.fromPacket(packet)
          .map(DomainPresenceInfo::getDomain)
          .map(DomainResource::shouldRetry)
          .orElse(false);
    }

    private void scheduleRetry(@Nonnull DomainPresenceInfo domainPresenceInfo) {
      final MakeRightDomainOperation retry = operation.createRetry(domainPresenceInfo);
      gate.getExecutor().schedule(retry::execute, delayUntilNextRetry(domainPresenceInfo), TimeUnit.SECONDS);
    }
    
    private long delayUntilNextRetry(@Nonnull DomainPresenceInfo domainPresenceInfo) {
      final OffsetDateTime nextRetryTime = domainPresenceInfo.getDomain().getNextRetryTime();
      final Duration interval = Duration.between(SystemClock.now(), nextRetryTime);
      return interval.getSeconds();

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
      } catch (Exception t) {
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
      packet.put(ProcessingConstants.SCHEDULED_STATUS_UPDATER, Boolean.TRUE);
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
