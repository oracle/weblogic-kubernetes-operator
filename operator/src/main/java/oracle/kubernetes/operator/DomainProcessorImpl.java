// Copyright (c) 2018, 2025, Oracle and/or its affiliates.
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
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import javax.annotation.Nonnull;

import io.kubernetes.client.extended.controller.reconciler.Result;
import io.kubernetes.client.openapi.models.CoreV1Event;
import io.kubernetes.client.openapi.models.V1ConfigMap;
import io.kubernetes.client.openapi.models.V1Job;
import io.kubernetes.client.openapi.models.V1ObjectMeta;
import io.kubernetes.client.openapi.models.V1ObjectReference;
import io.kubernetes.client.openapi.models.V1PersistentVolumeClaim;
import io.kubernetes.client.openapi.models.V1PersistentVolumeClaimStatus;
import io.kubernetes.client.openapi.models.V1Pod;
import io.kubernetes.client.openapi.models.V1PodCondition;
import io.kubernetes.client.openapi.models.V1PodDisruptionBudget;
import io.kubernetes.client.openapi.models.V1PodStatus;
import io.kubernetes.client.openapi.models.V1Service;
import io.kubernetes.client.util.Watch;
import oracle.kubernetes.common.logging.LoggingFilter;
import oracle.kubernetes.common.logging.MessageKeys;
import oracle.kubernetes.common.logging.OncePerMessageLoggingFilter;
import oracle.kubernetes.operator.helpers.ClusterPresenceInfo;
import oracle.kubernetes.operator.helpers.ConfigMapHelper;
import oracle.kubernetes.operator.helpers.DomainPresenceInfo;
import oracle.kubernetes.operator.helpers.EventHelper;
import oracle.kubernetes.operator.helpers.EventHelper.EventData;
import oracle.kubernetes.operator.helpers.EventHelper.EventItem;
import oracle.kubernetes.operator.helpers.KubernetesEventObjects;
import oracle.kubernetes.operator.helpers.KubernetesUtils;
import oracle.kubernetes.operator.helpers.NamespaceHelper;
import oracle.kubernetes.operator.helpers.PodDisruptionBudgetHelper;
import oracle.kubernetes.operator.helpers.PodHelper;
import oracle.kubernetes.operator.helpers.ResourcePresenceInfo;
import oracle.kubernetes.operator.helpers.SemanticVersion;
import oracle.kubernetes.operator.helpers.ServiceHelper;
import oracle.kubernetes.operator.logging.LoggingFacade;
import oracle.kubernetes.operator.logging.LoggingFactory;
import oracle.kubernetes.operator.logging.ThreadLoggingContext;
import oracle.kubernetes.operator.steps.BeforeAdminServiceStep;
import oracle.kubernetes.operator.tuning.TuningParameters;
import oracle.kubernetes.operator.watcher.JobWatcher;
import oracle.kubernetes.operator.work.Cancellable;
import oracle.kubernetes.operator.work.Fiber;
import oracle.kubernetes.operator.work.Fiber.CompletionCallback;
import oracle.kubernetes.operator.work.FiberGate;
import oracle.kubernetes.operator.work.Packet;
import oracle.kubernetes.operator.work.Step;
import oracle.kubernetes.utils.SystemClock;
import oracle.kubernetes.weblogic.domain.model.ClusterResource;
import oracle.kubernetes.weblogic.domain.model.DomainResource;
import oracle.kubernetes.weblogic.domain.model.DomainSpec;
import oracle.kubernetes.weblogic.domain.model.DomainStatus;
import oracle.kubernetes.weblogic.domain.model.ServerHealth;
import oracle.kubernetes.weblogic.domain.model.ServerStatus;
import org.jetbrains.annotations.NotNull;

import static oracle.kubernetes.common.logging.MessageKeys.POD_UNSCHEDULABLE;
import static oracle.kubernetes.common.logging.MessageKeys.PVC_NOT_BOUND_ERROR;
import static oracle.kubernetes.operator.DomainStatusUpdater.createInternalFailureSteps;
import static oracle.kubernetes.operator.DomainStatusUpdater.createIntrospectionFailureSteps;
import static oracle.kubernetes.operator.KubernetesConstants.POD_SCHEDULED;
import static oracle.kubernetes.operator.KubernetesConstants.UNSCHEDULABLE_REASON;
import static oracle.kubernetes.operator.ProcessingConstants.SERVER_HEALTH_MAP;
import static oracle.kubernetes.operator.ProcessingConstants.SERVER_STATE_MAP;
import static oracle.kubernetes.operator.helpers.EventHelper.EventItem.CLUSTER_CHANGED;
import static oracle.kubernetes.operator.helpers.EventHelper.EventItem.CLUSTER_CREATED;
import static oracle.kubernetes.operator.helpers.EventHelper.EventItem.DOMAIN_CHANGED;
import static oracle.kubernetes.operator.helpers.EventHelper.EventItem.DOMAIN_CREATED;
import static oracle.kubernetes.operator.helpers.EventHelper.EventItem.PERSISTENT_VOLUME_CLAIM_BOUND;
import static oracle.kubernetes.operator.helpers.EventHelper.createClusterResourceEventData;
import static oracle.kubernetes.operator.helpers.PodHelper.getPodDomainUid;
import static oracle.kubernetes.operator.helpers.PodHelper.getPodName;
import static oracle.kubernetes.operator.helpers.PodHelper.getPodNamespace;
import static oracle.kubernetes.operator.helpers.PodHelper.getPodStatusMessage;
import static oracle.kubernetes.operator.logging.ThreadLoggingContext.setThreadContext;
import static oracle.kubernetes.weblogic.domain.model.DomainFailureReason.PERSISTENT_VOLUME_CLAIM;

public class DomainProcessorImpl implements DomainProcessor, MakeRightExecutor {

  private static final LoggingFacade LOGGER = LoggingFactory.getLogger("Operator", "Operator");

  private static final String ADDED = "ADDED";
  private static final String MODIFIED = "MODIFIED";
  private static final String DELETED = "DELETED";
  private static final String ERROR = "ERROR";

  /** A map that holds at most one FiberGate per namespace to run make-right steps. */
  @SuppressWarnings("FieldMayBeFinal")
  private static Map<String, FiberGate> makeRightFiberGates = new ConcurrentHashMap<>();

  /** A map that holds at most one FiberGate per namespace to run status update steps. */
  @SuppressWarnings("FieldMayBeFinal")
  private static Map<String, FiberGate> statusFiberGates = new ConcurrentHashMap<>();

  // map namespace to map of uid to processing.
  @SuppressWarnings("FieldMayBeFinal")
  private static Map<String, Map<String, Cancellable>> statusUpdaters = new ConcurrentHashMap<>();

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

  @Override
  public Map<String, FiberGate> getMakeRightFiberGateMap() {
    return makeRightFiberGates;
  }

  static void cleanupNamespace(String namespace) {
    clusterEventK8SObjects.remove(namespace);
    domainEventK8SObjects.remove(namespace);
    namespaceEventK8SObjects.remove(namespace);
    statusUpdaters.remove((namespace));
  }

  private static void registerStatusUpdater(
        String ns, String domainUid, Cancellable future) {
    Cancellable existing =
          statusUpdaters.computeIfAbsent(ns, k -> new ConcurrentHashMap<>()).put(domainUid, future);
    if (existing != null) {
      existing.cancel();
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

  private static void onCreateModifyEvent(@Nonnull String kind, @Nonnull String name, CoreV1Event event) {
    switch (kind) {
      case EventConstants.EVENT_KIND_POD:
        processPodEvent(name, event);
        break;
      case EventConstants.EVENT_KIND_DOMAIN, EventConstants.EVENT_KIND_NAMESPACE:
        updateEventK8SObjects(event);
        break;
      case EventConstants.EVENT_KIND_CLUSTER:
        updateClusterEventK8SObjects(event);
        break;
      default:
        break;
    }
  }

  private static void processPodEvent(@Nonnull String name, CoreV1Event event) {
    if (name.equals(NamespaceHelper.getOperatorPodName())) {
      updateEventK8SObjects(event);
    } else {
      processServerEvent(event);
    }
  }

  private static void processServerEvent(CoreV1Event event) {
    // no-op
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
    if (isNotDeleting(info)) {
      list.add(info.getDomain());
    }
  }

  private void onDeleteEvent(@Nonnull String kind, @Nonnull String name, CoreV1Event event) {
    switch (kind) {
      case EventConstants.EVENT_KIND_DOMAIN, EventConstants.EVENT_KIND_NAMESPACE:
        deleteEventK8SObjects(event);
        break;
      case EventConstants.EVENT_KIND_POD:
        if (name.equals(NamespaceHelper.getOperatorPodName())) {
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

  // pre-conditions: DomainPresenceInfo SPI
  // "principal"
  public static Step bringAdminServerUp(DomainPresenceInfo info) {
    return bringAdminServerUpSteps(info);
  }

  @Override
  @SuppressWarnings("try")
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

  @Override
  @SuppressWarnings("try")
  public void runMakeRight(MakeRightClusterOperation operation) {
    final ClusterPresenceInfo liveInfo = operation.getPresenceInfo();
    if (delegate.isNamespaceRunning(liveInfo.getNamespace())) {
      try (ThreadLoggingContext ignored = setThreadContext().presenceInfo(liveInfo)) {
        if (shouldContinue(operation, liveInfo)) {
          new ClusterPlan(operation, delegate).execute();
        }
      }
    }
  }

  private boolean shouldContinue(MakeRightDomainOperation operation, DomainPresenceInfo liveInfo) {
    // HERE, FIXME: there won't be a difference between cached and live info
    final DomainPresenceInfo cachedInfo = getExistingDomainPresenceInfo(liveInfo);
    if (isNewDomain(cachedInfo)) {
      return true;
    } else if (liveInfo.isFromOutOfDateEvent(operation, cachedInfo)) {
      return false;
    } else if (isDeleting(operation)) {
      return true;
    } else if (liveInfo.isDomainProcessingHalted(cachedInfo)) {
      return liveInfo.isMustDomainProcessingRestart();
    } else if (isExplicitRecheckWithoutRetriableFailure(operation, liveInfo)
        || liveInfo.isDomainGenerationChanged(cachedInfo)) {
      return true;
    } else {
      return false;
    }
  }

  private boolean shouldContinue(MakeRightClusterOperation operation, ClusterPresenceInfo liveInfo) {
    final ClusterPresenceInfo cachedInfo = getExistingClusterPresenceInfo(liveInfo);
    if (isDeleting(operation)) {
      return findClusterPresenceInfo(liveInfo.getNamespace(), liveInfo.getResourceName());
    } else if (isNewCluster(cachedInfo)) {
      return true;
    } else if (liveInfo.isFromOutOfDateEvent(operation, cachedInfo)) {
      return false;
    } else if (operation.isExplicitRecheck() || liveInfo.isClusterGenerationChanged(cachedInfo)) {
      return true;
    } else {
      cachedInfo.setCluster(liveInfo.getCluster());
      return false;
    }
  }

  private boolean isExplicitRecheckWithoutRetriableFailure(
      MakeRightDomainOperation operation, DomainPresenceInfo info) {
    return operation.isExplicitRecheck() && !hasRetriableFailureNonRetryingOperation(operation, info);
  }

  private boolean hasRetriableFailureNonRetryingOperation(MakeRightDomainOperation operation, DomainPresenceInfo info) {
    return info.hasRetryableFailure() && !operation.isRetryOnFailure();
  }

  private boolean isNewDomain(DomainPresenceInfo cachedInfo) {
    return Optional.ofNullable(cachedInfo).map(DomainPresenceInfo::getDomain).orElse(null) == null;
  }

  private boolean isNewCluster(ClusterPresenceInfo cachedInfo) {
    return Optional.ofNullable(cachedInfo).map(ClusterPresenceInfo::getCluster).orElse(null) == null;
  }

  private boolean findClusterPresenceInfo(String namespace, String clusterName) {
    return Optional.ofNullable(clusters.get(namespace)).orElse(Collections.emptyMap()).get(clusterName) != null;
  }

  private boolean isDeleting(MakeRightClusterOperation operation) {
    return EventItem.CLUSTER_DELETED == getEventItem(operation);
  }

  private boolean isDeleting(MakeRightDomainOperation operation) {
    return operation.isDeleting() || EventItem.DOMAIN_DELETED == getEventItem(operation);
  }

  @SuppressWarnings("rawtypes")
  private EventItem getEventItem(MakeRightOperation operation) {
    return Optional.ofNullable(operation.getEventData()).map(EventData::getItem).orElse(null);
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
  public void registerClusterPresenceInfo(ClusterPresenceInfo info) {
    clusters
        .computeIfAbsent(info.getNamespace(), k -> new ConcurrentHashMap<>())
        .put(info.getResourceName(), info);
  }

  @Override
  public void unregisterDomainPresenceInfo(DomainPresenceInfo info) {
    unregisterPresenceInfo(info.getNamespace(), info.getDomainUid());
  }

  @Override
  public void unregisterDomainEventK8SObjects(DomainPresenceInfo info) {
    unregisterEventK8SObject(info.getNamespace(), info.getDomainUid());
  }

  @Override
  public void unregisterClusterPresenceInfo(ClusterPresenceInfo info) {
    unregisterPresenceInfoForCluster(info.getNamespace(), info.getResourceName());
  }

  private static void unregisterEventK8SObject(String ns, String domainUid) {
    Optional.ofNullable(domainEventK8SObjects.get(ns)).ifPresent(m -> m.remove(domainUid));
  }

  private static void unregisterPresenceInfo(String ns, String domainUid) {
    Optional.ofNullable(domains.get(ns)).ifPresent(m -> m.remove(domainUid));
  }

  private static void unregisterPresenceInfoForCluster(String ns, String clusterName) {
    Optional.ofNullable(clusters.get(ns)).ifPresent(m -> m.remove(clusterName));
  }

  @Override
  public void endScheduledDomainStatusUpdates(DomainPresenceInfo info) {
    Map<String, Cancellable> map = statusUpdaters.get(info.getNamespace());
    if (map != null) {
      Cancellable existing = map.remove(info.getDomainUid());
      if (existing != null) {
        existing.cancel();
      }
    }
  }

  private static Step bringAdminServerUpSteps(DomainPresenceInfo info) {
    List<Step> steps = new ArrayList<>();
    steps.add(new BeforeAdminServiceStep(null));
    steps.add(PodHelper.createAdminPodStep(null));
    steps.add(ServiceHelper.createForExternalServiceStep(null));
    steps.add(ServiceHelper.createForServerStep(null));
    steps.add(PodHelper.createAdminReadyStep(null));
    return Step.chain(steps.toArray(new Step[0]));
  }

  /**
   * Dispatch job watch event.
   * @param item watch event
   */
  public void dispatchJobWatch(Watch.Response<V1Job> item) {
    V1Job job = item.object;
    String domainUid = getJobDomainUid(job);
    String namespace = Optional.ofNullable(job.getMetadata()).map(V1ObjectMeta::getNamespace).orElse(null);
    if (domainUid == null || namespace == null) {
      return;
    }

    DomainPresenceInfo info = getExistingDomainPresenceInfo(namespace, domainUid);
    if (info == null) {
      return;
    }

    switch (item.type) {
      case MODIFIED:
        if (JobWatcher.isComplete(job) || JobWatcher.isFailed(job)) {
          createMakeRightOperation(info).interrupt().withExplicitRecheck().execute();
        }
        break;
      default:
    }
  }

  private static String getJobDomainUid(V1Job job) {
    return Optional.ofNullable(job)
            .map(V1Job::getMetadata)
            .map(V1ObjectMeta::getLabels)
            .orElse(Collections.emptyMap())
            .get(LabelConstants.DOMAINUID_LABEL);
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

  @SuppressWarnings("fallthrough")
  private void processServerPodWatch(V1Pod pod, String watchType) {
    String domainUid = getPodLabel(pod, LabelConstants.DOMAINUID_LABEL);
    DomainPresenceInfo info = getExistingDomainPresenceInfo(getPodNamespace(pod), domainUid);
    if (info == null) {
      return;
    }

    String serverName = getPodLabel(pod, LabelConstants.SERVERNAME_LABEL);
    switch (watchType) {
      case ADDED:
        info.setServerPodFromEvent(serverName, pod);
        break;
      case MODIFIED:
        boolean podPreviouslyEvicted = info.setServerPodFromEvent(serverName, pod, PodHelper::isEvicted);
        boolean isEvicted = PodHelper.isEvicted(pod);
        if (isEvicted && !podPreviouslyEvicted) {
          if (PodHelper.shouldRestartEvictedPod(pod)) {
            LOGGER.info(MessageKeys.POD_EVICTED, getPodName(pod), getPodStatusMessage(pod));
          } else {
            LOGGER.info(MessageKeys.POD_EVICTED_NO_RESTART, getPodName(pod), getPodStatusMessage(pod));
          }
        }
        boolean isReady = PodHelper.isReady(pod);
        boolean isLabeledForShutdown = PodHelper.isPodAlreadyAnnotatedForShutdown(pod);
        if ((isEvicted || isReady != isLabeledForShutdown || PodHelper.isFailed(pod)) && !PodHelper.isDeleting(pod)) {
          createMakeRightOperation(info).interrupt().withExplicitRecheck().execute();
        }
        boolean isUnschedulable = PodHelper.hasUnSchedulableCondition(pod);
        if (isUnschedulable) {
          LOGGER.info(POD_UNSCHEDULABLE,  getPodName(pod), getUnSchedulableConditionMessage(pod));
        }
        break;
      case DELETED:
        boolean removed = info.deleteServerPodFromEvent(serverName, pod);
        if (removed && isNotDeleting(info) && Boolean.FALSE.equals(info.isServerPodBeingDeleted(serverName))) {
          LOGGER.info(MessageKeys.POD_DELETED, domainUid, getPodNamespace(pod), serverName);
        }
        createMakeRightOperation(info).interrupt().withExplicitRecheck().execute();
        break;

      case ERROR:
      default:
    }
  }

  /**
   * If a pod is unschedulable, return the condition's message.
   * @param pod Kubernetes V1Pod
   * @return message for the unschedulable pod condition
   */
  public static String getUnSchedulableConditionMessage(V1Pod pod) {
    return Optional.ofNullable(pod)
            .filter(PodHelper::isPending)
            .map(V1Pod::getStatus)
            .map(V1PodStatus::getConditions)
            .orElse(Collections.emptyList())
            .stream()
            .filter(condition -> POD_SCHEDULED.equals(condition.getType())
                    && UNSCHEDULABLE_REASON.equals(condition.getReason()))
            .map(V1PodCondition::getMessage)
            .findFirst().orElse(null);
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
      case ADDED, MODIFIED:
        updateDomainStatus(pod, info);
        break;
      case DELETED:
        LOGGER.fine("Introspector Pod " + getPodName(pod) + " for domain " + domainUid + " is deleted.");
        break;
      default:
    }
  }

  @Override
  public void updateDomainStatus(@Nonnull V1Pod pod, DomainPresenceInfo info) {
    Packet packet = new Packet();
    packet.put(ProcessingConstants.DOMAIN_PRESENCE_INFO, info);
    packet.put(ProcessingConstants.DELEGATE_COMPONENT_NAME, delegate);
    Optional.ofNullable(IntrospectionStatus.createStatusUpdateSteps(pod))
          .ifPresent(steps -> delegate.runSteps(packet, steps, null));
  }

  @Override
  public void updateDomainStatus(@Nonnull V1PersistentVolumeClaim pvc, DomainPresenceInfo info) {
    Packet packet = new Packet();
    packet.put(ProcessingConstants.DOMAIN_PRESENCE_INFO, info);
    packet.put(ProcessingConstants.DELEGATE_COMPONENT_NAME, delegate);
    if (!ProcessingConstants.BOUND.equals(getPhase(pvc))) {
      delegate.runSteps(packet, DomainStatusUpdater
              .createPersistentVolumeClaimFailureSteps(getMessage(pvc)), null);
    } else {
      delegate.runSteps(packet, DomainStatusUpdater
          .createRemoveSelectedFailuresStep(EventHelper.createEventStep(
              new EventData(PERSISTENT_VOLUME_CLAIM_BOUND)), PERSISTENT_VOLUME_CLAIM), null);
    }
  }

  private String getPhase(@Nonnull V1PersistentVolumeClaim pvc) {
    return Optional.of(pvc).map(V1PersistentVolumeClaim::getStatus)
        .map(V1PersistentVolumeClaimStatus::getPhase).orElse(null);
  }

  private String getMessage(V1PersistentVolumeClaim pvc) {
    return LOGGER.formatMessage(PVC_NOT_BOUND_ERROR, pvc.getMetadata().getName(), getPhase(pvc));
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
      case ADDED, MODIFIED:
        ServiceHelper.updatePresenceFromEvent(info, item.object);
        break;
      case DELETED:
        boolean removed = ServiceHelper.deleteFromEvent(info, item.object);
        if (removed && isNotDeleting(info)) {
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
      case ADDED, MODIFIED:
        PodDisruptionBudgetHelper.updatePDBFromEvent(info, item.object);
        break;
      case DELETED:
        boolean removed = PodDisruptionBudgetHelper.deleteFromEvent(info, item.object);
        if (removed && isNotDeleting(info)) {
          createMakeRightOperation(info).interrupt().withExplicitRecheck().execute();
        }
        break;
      default:
    }
  }

  private static boolean isNotDeleting(DomainPresenceInfo info) {
    return info.isNotDeleting() && info.getDomain() != null;
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
    if (c.getMetadata() != null) {
      switch (item.type) {
        case MODIFIED, DELETED:
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
    V1ObjectReference ref = e.getInvolvedObject();

    if (ref == null || ref.getName() == null || ref.getKind() == null) {
      return;
    }

    switch (item.type) {
      case ADDED, MODIFIED:
        onCreateModifyEvent(ref.getKind(), ref.getName(), e);
        break;
      case DELETED:
        onDeleteEvent(ref.getKind(), ref.getName(), e);
        break;
      case ERROR:
      default:
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
    List<DomainPresenceInfo> hostingDomains =
        getExistingDomainPresenceInfoForCluster(cluster.getNamespace(), cluster.getMetadata().getName());
    if (hostingDomains.isEmpty()) {
      LOGGER.info(MessageKeys.WATCH_CLUSTER_WITHOUT_DOMAIN, cluster.getMetadata().getName());
      createMakeRightOperationForClusterEvent(CLUSTER_CREATED, cluster, null).execute();
    } else {
      hostingDomains.forEach(info -> {
        LOGGER.info(MessageKeys.WATCH_CLUSTER, cluster.getMetadata().getName(), info.getDomainUid());
        info.addClusterResource(cluster);
        createMakeRightOperationForClusterEvent(CLUSTER_CREATED, cluster, info.getDomainUid())
            .andThen(createMakeRightOperation(info)
                .interrupt()
                .withExplicitRecheck())
            .execute();
      });
    }
  }

  private void handleModifiedCluster(ClusterResource cluster) {
    List<DomainPresenceInfo> hostingDomains =
        getExistingDomainPresenceInfoForCluster(cluster.getNamespace(), cluster.getMetadata().getName());
    if (hostingDomains.isEmpty()) {
      LOGGER.info(MessageKeys.WATCH_CLUSTER_WITHOUT_DOMAIN, cluster.getMetadata().getName());
      createMakeRightOperationForClusterEvent(CLUSTER_CHANGED, cluster, null).execute();
    } else {
      hostingDomains.forEach(info -> {
        if (!cluster.isGenerationLaterThanObservedGeneration()) {
          return;
        }

        LOGGER.fine(MessageKeys.WATCH_CLUSTER, cluster.getMetadata().getName(), info.getDomainUid());
        createMakeRightOperationForClusterEvent(CLUSTER_CHANGED, cluster, info.getDomainUid())
            .andThen(createMakeRightOperation(info)
                .interrupt()
                .withExplicitRecheck())
            .execute();
      });
    }
  }

  private void handleDeletedCluster(ClusterResource cluster) {
    List<DomainPresenceInfo> hostingDomains =
        getExistingDomainPresenceInfoForCluster(cluster.getNamespace(), cluster.getMetadata().getName());
    if (hostingDomains.isEmpty()) {
      LOGGER.info(MessageKeys.WATCH_CLUSTER_WITHOUT_DOMAIN, cluster.getMetadata().getName());
      createMakeRightOperationForClusterEvent(EventItem.CLUSTER_DELETED, cluster, null).execute();
    } else {
      hostingDomains.forEach(info -> {
        LOGGER.info(MessageKeys.WATCH_CLUSTER_DELETED, cluster.getMetadata().getName(), info.getDomainUid());
        info.removeClusterResource(cluster.getClusterName());
        createMakeRightOperationForClusterEvent(EventItem.CLUSTER_DELETED, cluster, info.getDomainUid())
            .andThen(createMakeRightOperation(info)
                .interrupt()
                .withExplicitRecheck())
            .execute();
      });
    }
  }

  @Override
  public MakeRightClusterOperation createMakeRightOperationForClusterEvent(
      EventItem clusterEvent, ClusterResource cluster, String domainUid) {
    return delegate.createMakeRightOperation(this, createInfoForClusterEventOnly(cluster))
        .interrupt()
        .withEventData(createClusterResourceEventData(clusterEvent, cluster, domainUid));
  }

  @Override
  public MakeRightClusterOperation createMakeRightOperationForClusterEvent(
      EventItem clusterEvent, ClusterResource cluster) {
    return createMakeRightOperationForClusterEvent(clusterEvent, cluster, null);
  }

  @NotNull
  private ClusterPresenceInfo createInfoForClusterEventOnly(ClusterResource cluster) {
    return new ClusterPresenceInfo(delegate.getResourceCache(), cluster);
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
    DomainPresenceInfo info = new DomainPresenceInfo(delegate.getResourceCache(), domain);
    Optional.ofNullable(domain.getSpec()).map(DomainSpec::getClusters).ifPresent(list -> list.forEach(clusterName -> {
      ClusterPresenceInfo c = getExistingClusterPresenceInfo(domain.getNamespace(), clusterName.getName());
      if (c != null) {
        info.addClusterResource(c.getCluster());
      }
    }));
    createMakeRightOperation(info)
        .interrupt()
        .withExplicitRecheck()
        .withEventData(new EventData(DOMAIN_CREATED))
        .execute();
  }

  private void handleModifiedDomain(DomainResource domain) {
    if (!domain.isGenerationLaterThanObservedGeneration()) {
      return;
    }
    LOGGER.fine(MessageKeys.WATCH_DOMAIN, domain.getDomainUid());
    createMakeRightOperation(new DomainPresenceInfo(delegate.getResourceCache(), domain))
        .interrupt()
        .withEventData(new EventData(DOMAIN_CHANGED))
        .execute();
  }

  private void handleDeletedDomain(DomainResource domain) {
    LOGGER.info(MessageKeys.WATCH_DOMAIN_DELETED, domain.getDomainUid());
    createMakeRightOperation(new DomainPresenceInfo(delegate.getResourceCache(), domain))
        .interrupt().forDeletion().withExplicitRecheck()
        .withEventData(new EventData(EventItem.DOMAIN_DELETED))
        .execute();
  }

  private static void logThrowable(Throwable throwable) {
    if (throwable instanceof Fiber.MultiThrowable multiThrowable) {
      for (Throwable t : multiThrowable.getThrowables()) {
        logThrowable(t);
      }
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
    public @Nonnull Result apply(Packet packet) {
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

  private static class DomainPlan extends Plan<MakeRightDomainOperation> {

    public DomainPlan(MakeRightDomainOperation operation, DomainProcessorDelegate delegate) {
      super(operation, delegate);
    }

    @Override
    public CompletionCallback createCompletionCallback() {
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
        gate.startFiber(
            ((DomainPresenceInfo)presenceInfo).getDomainUid(),
            () -> getFailureSteps(throwable),
                operation::createPacket,
            new FailureReportCompletionCallback());
      }

      private Step getFailureSteps(Throwable throwable) {
        if (throwable instanceof IntrospectionJobHolder ijh) {
          return createIntrospectionFailureSteps(throwable, ijh.getIntrospectionJob());
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
      delegate.schedule(retry::execute, delayUntilNextRetry(domainPresenceInfo), TimeUnit.SECONDS);
    }
    
    private long delayUntilNextRetry(@Nonnull DomainPresenceInfo domainPresenceInfo) {
      final OffsetDateTime nextRetryTime = domainPresenceInfo.getDomain().getNextRetryTime();
      final Duration interval = Duration.between(SystemClock.now(), nextRetryTime);
      return interval.getSeconds();

    }
  }

  private static class ClusterPlan extends Plan<MakeRightClusterOperation> {

    public ClusterPlan(MakeRightClusterOperation operation, DomainProcessorDelegate delegate) {
      super(operation, delegate);
    }

    @Override
    public CompletionCallback createCompletionCallback() {
      return new ClusterPlanCompletionCallback(operation.getAndThen());
    }

    static class ClusterPlanCompletionCallback implements CompletionCallback {

      private final MakeRightDomainOperation domainOperation;

      public ClusterPlanCompletionCallback(MakeRightDomainOperation domainOperation) {
        this.domainOperation = domainOperation;
      }

      @Override
      public void onCompletion(Packet packet) {
        if (domainOperation != null) {
          domainOperation.execute();
        }
      }

      @Override
      public void onThrowable(Packet packet, Throwable throwable) {
        reportFailure(throwable);
      }

      private void reportFailure(Throwable throwable) {
        logThrowable(throwable);
      }
    }
  }

  @SuppressWarnings("rawtypes")
  private abstract static class Plan<T extends MakeRightOperation> {

    protected final T operation;
    protected final ResourcePresenceInfo presenceInfo;
    protected final FiberGate gate;
    protected final DomainProcessorDelegate delegate;

    public Plan(T operation, DomainProcessorDelegate delegate) {
      this.operation = operation;
      this.presenceInfo = operation.getPresenceInfo();
      this.gate = getMakeRightFiberGate(delegate, this.presenceInfo.getNamespace());
      this.delegate = delegate;
    }

    private FiberGate getMakeRightFiberGate(DomainProcessorDelegate delegate, String ns) {
      return makeRightFiberGates.computeIfAbsent(ns, k -> delegate.createFiberGate());
    }

    void execute() {
      gate.startFiber(presenceInfo.getResourceName(), operation::createSteps, operation::createPacket,
          createCompletionCallback());
    }

    abstract CompletionCallback createCompletionCallback();
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

    @SuppressWarnings("try")
    private void updateStatus() {
      try {
        Step strategy = Step.chain(new DomainPresenceInfoStep(), ServerStatusReader.createStatusStep(timeoutSeconds));
        getStatusFiberGate(getNamespace())
            .startFiber(getDomainUid(), () -> strategy, this::createPacket, new CompletionCallbackImpl());
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

    @Nonnull
    private Packet createPacket() {
      Packet packet = new Packet();
      packet.put(ProcessingConstants.DOMAIN_COMPONENT_NAME, delegate.getKubernetesVersion());
      packet.put(LoggingFilter.LOGGING_FILTER_PACKET_KEY, loggingFilter);
      packet.put(ProcessingConstants.DELEGATE_COMPONENT_NAME, delegate);
      return packet;
    }

    private class DomainPresenceInfoStep extends Step {
      @Override
      public @Nonnull Result apply(Packet packet) {
        Optional.ofNullable(domains.get(getNamespace()))
            .map(n -> n.get(getDomainUid()))
            .ifPresent(i -> packet.put(ProcessingConstants.DOMAIN_PRESENCE_INFO, i));

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
