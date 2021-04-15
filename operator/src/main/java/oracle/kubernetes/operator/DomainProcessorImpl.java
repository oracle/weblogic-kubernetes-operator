// Copyright (c) 2018, 2021, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collection;
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

import io.kubernetes.client.openapi.models.CoreV1Event;
import io.kubernetes.client.openapi.models.V1ConfigMap;
import io.kubernetes.client.openapi.models.V1ContainerState;
import io.kubernetes.client.openapi.models.V1ContainerStatus;
import io.kubernetes.client.openapi.models.V1ObjectMeta;
import io.kubernetes.client.openapi.models.V1ObjectReference;
import io.kubernetes.client.openapi.models.V1Pod;
import io.kubernetes.client.openapi.models.V1PodCondition;
import io.kubernetes.client.openapi.models.V1PodList;
import io.kubernetes.client.openapi.models.V1PodStatus;
import io.kubernetes.client.openapi.models.V1Service;
import io.kubernetes.client.openapi.models.V1ServiceList;
import io.kubernetes.client.openapi.models.V1beta1PodDisruptionBudget;
import io.kubernetes.client.openapi.models.V1beta1PodDisruptionBudgetList;
import io.kubernetes.client.util.Watch;
import oracle.kubernetes.operator.TuningParameters.MainTuning;
import oracle.kubernetes.operator.calls.FailureStatusSourceException;
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
import oracle.kubernetes.operator.helpers.ServiceHelper;
import oracle.kubernetes.operator.logging.LoggingContext;
import oracle.kubernetes.operator.logging.LoggingFacade;
import oracle.kubernetes.operator.logging.LoggingFactory;
import oracle.kubernetes.operator.logging.LoggingFilter;
import oracle.kubernetes.operator.logging.MessageKeys;
import oracle.kubernetes.operator.logging.OncePerMessageLoggingFilter;
import oracle.kubernetes.operator.steps.BeforeAdminServiceStep;
import oracle.kubernetes.operator.steps.DeleteDomainStep;
import oracle.kubernetes.operator.steps.DomainPresenceStep;
import oracle.kubernetes.operator.steps.ManagedServersUpStep;
import oracle.kubernetes.operator.steps.MonitorExporterSteps;
import oracle.kubernetes.operator.steps.WatchPodReadyAdminStep;
import oracle.kubernetes.operator.work.Component;
import oracle.kubernetes.operator.work.Fiber;
import oracle.kubernetes.operator.work.Fiber.CompletionCallback;
import oracle.kubernetes.operator.work.FiberGate;
import oracle.kubernetes.operator.work.NextAction;
import oracle.kubernetes.operator.work.Packet;
import oracle.kubernetes.operator.work.Step;
import oracle.kubernetes.operator.work.Step.StepAndPacket;
import oracle.kubernetes.weblogic.domain.model.Domain;
import oracle.kubernetes.weblogic.domain.model.DomainSpec;
import oracle.kubernetes.weblogic.domain.model.DomainStatus;
import oracle.kubernetes.weblogic.domain.model.ServerHealth;
import oracle.kubernetes.weblogic.domain.model.ServerStatus;

import static oracle.kubernetes.operator.LabelConstants.INTROSPECTION_STATE_LABEL;
import static oracle.kubernetes.operator.ProcessingConstants.DOMAIN_INTROSPECT_REQUESTED;
import static oracle.kubernetes.operator.ProcessingConstants.FATAL_INTROSPECTOR_ERROR;
import static oracle.kubernetes.operator.ProcessingConstants.MAKE_RIGHT_DOMAIN_OPERATION;
import static oracle.kubernetes.operator.ProcessingConstants.SERVER_HEALTH_MAP;
import static oracle.kubernetes.operator.ProcessingConstants.SERVER_STATE_MAP;
import static oracle.kubernetes.operator.helpers.EventHelper.EventItem.DOMAIN_PROCESSING_ABORTED;
import static oracle.kubernetes.operator.helpers.EventHelper.EventItem.DOMAIN_PROCESSING_RETRYING;
import static oracle.kubernetes.operator.helpers.LegalNames.toJobIntrospectorName;

public class DomainProcessorImpl implements DomainProcessor {

  private static final LoggingFacade LOGGER = LoggingFactory.getLogger("Operator", "Operator");

  private static final Map<String, FiberGate> makeRightFiberGates = new ConcurrentHashMap<>();
  private static final Map<String, FiberGate> statusFiberGates = new ConcurrentHashMap<>();

  // Map namespace to map of domainUID to Domain; tests may replace this value.
  @SuppressWarnings({"FieldMayBeFinal", "CanBeFinal"})
  private static Map<String, Map<String, DomainPresenceInfo>> DOMAINS = new ConcurrentHashMap<>();
  private static final Map<String, Map<String, ScheduledFuture<?>>> statusUpdaters = new ConcurrentHashMap<>();
  private final DomainProcessorDelegate delegate;

  // Map namespace to map of domainUID to KubernetesEventObjects; tests may replace this value.
  @SuppressWarnings({"FieldMayBeFinal", "CanBeFinal"})
  private static Map<String, Map<String, KubernetesEventObjects>> domainEventK8SObjects = new ConcurrentHashMap<>();

  // Map namespace to KubernetesEventObjects; tests may replace this value.
  @SuppressWarnings({"FieldMayBeFinal", "CanBeFinal"})
  private static Map<String, KubernetesEventObjects> namespaceEventK8SObjects = new ConcurrentHashMap<>();

  public DomainProcessorImpl(DomainProcessorDelegate delegate) {
    this.delegate = delegate;
  }

  private static DomainPresenceInfo getExistingDomainPresenceInfo(String ns, String domainUid) {
    return DOMAINS.computeIfAbsent(ns, k -> new ConcurrentHashMap<>()).get(domainUid);
  }

  static void registerDomainPresenceInfo(DomainPresenceInfo info) {
    DOMAINS
          .computeIfAbsent(info.getNamespace(), k -> new ConcurrentHashMap<>())
          .put(info.getDomainUid(), info);
  }

  private static void unregisterPresenceInfo(String ns, String domainUid) {
    Map<String, DomainPresenceInfo> map = DOMAINS.get(ns);
    if (map != null) {
      map.remove(domainUid);
    }
  }

  private static void registerStatusUpdater(
        String ns, String domainUid, ScheduledFuture<?> future) {
    ScheduledFuture<?> existing =
          statusUpdaters.computeIfAbsent(ns, k -> new ConcurrentHashMap<>()).put(domainUid, future);
    if (existing != null) {
      existing.cancel(false);
    }
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

    Optional.ofNullable(DOMAINS.get(event.getMetadata().getNamespace()))
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
          JobHelper.deleteDomainIntrospectorJobStep(null),
          JobHelper.createDomainIntrospectorJobStep(null));
  }

  private static class IntrospectionRequestStep extends Step {

    private final String requestedIntrospectVersion;

    IntrospectionRequestStep(DomainPresenceInfo info) {
      this.requestedIntrospectVersion = info.getDomain().getIntrospectVersion();
    }

    @Override
    public NextAction apply(Packet packet) {
      if (!Objects.equals(requestedIntrospectVersion, packet.get(INTROSPECTION_STATE_LABEL))) {
        packet.put(DOMAIN_INTROSPECT_REQUESTED, Optional.ofNullable(requestedIntrospectVersion).orElse("0"));
      }
      return doNext(packet);
    }
  }

  private static Step bringAdminServerUpSteps(
        DomainPresenceInfo info, PodAwaiterStepFactory podAwaiterStepFactory) {
    List<Step> steps = new ArrayList<>();
    steps.add(new BeforeAdminServiceStep(null));
    steps.add(PodHelper.createAdminPodStep(null));

    if (Domain.isExternalServiceConfigured(info.getDomain().getSpec())) {
      steps.add(ServiceHelper.createForExternalServiceStep(null));
    }
    steps.add(ServiceHelper.createForServerStep(null));
    steps.add(new WatchPodReadyAdminStep(podAwaiterStepFactory, null));
    return Step.chain(steps.toArray(new Step[0]));
  }

  private static Step bringManagedServersUp(Step next) {
    return new ManagedServersUpStep(next);
  }

  private FiberGate getMakeRightFiberGate(String ns) {
    return makeRightFiberGates.computeIfAbsent(ns, k -> delegate.createFiberGate());
  }

  private FiberGate getStatusFiberGate(String ns) {
    return statusFiberGates.computeIfAbsent(ns, k -> delegate.createFiberGate());
  }

  /**
   * Report on currently suspended fibers. This is the first step toward diagnosing if we need special handling
   * to kill or kick these fibers.
   */
  public void reportSuspendedFibers() {
    if (LOGGER.isFineEnabled()) {
      BiConsumer<String, FiberGate> consumer =
          (namespace, gate) -> gate.getCurrentFibers().forEach(
            (key, fiber) -> Optional.ofNullable(fiber.getSuspendedStep()).ifPresent(suspendedStep -> {
              try (LoggingContext ignored
                  = LoggingContext.setThreadContext().namespace(namespace).domainUid(getDomainUid(fiber))) {
                LOGGER.fine("Fiber is SUSPENDED at " + suspendedStep.getName());
              }
            }));
      makeRightFiberGates.forEach(consumer);
      statusFiberGates.forEach(consumer);
    }
  }

  @Override
  public Stream<DomainPresenceInfo> findStrandedDomainPresenceInfos(String namespace, Set<String> domainUids) {
    return Optional.ofNullable(DOMAINS.get(namespace)).orElse(Collections.emptyMap())
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
    DomainPresenceInfo info = getExistingDomainPresenceInfo(getNamespace(pod), domainUid);
    if (info == null) {
      return;
    }

    String serverName = getPodLabel(pod, LabelConstants.SERVERNAME_LABEL);
    switch (watchType) {
      case "ADDED":
        info.setServerPodBeingDeleted(serverName, Boolean.FALSE);
        // fall through
      case "MODIFIED":
        info.setServerPodFromEvent(serverName, pod);
        break;
      case "DELETED":
        boolean removed = info.deleteServerPodFromEvent(serverName, pod);
        if (removed && info.isNotDeleting() && !info.isServerPodBeingDeleted(serverName)) {
          LOGGER.info(MessageKeys.POD_DELETED, domainUid, getNamespace(pod), serverName);
          createMakeRightOperation(info).interrupt().withExplicitRecheck().execute();
        }
        break;

      case "ERROR":
      default:
    }
  }

  private String getNamespace(V1Pod pod) {
    return Optional.ofNullable(pod)
        .map(V1Pod::getMetadata)
        .map(V1ObjectMeta::getNamespace)
        .orElse(null);
  }

  private String getPodLabel(V1Pod pod, String labelName) {
    return Optional.ofNullable(pod)
        .map(V1Pod::getMetadata)
        .map(V1ObjectMeta::getLabels)
        .map(m -> m.get(labelName))
        .orElse(null);
  }

  private void processIntrospectorJobPodWatch(V1Pod introspectorPod, String watchType) {
    String domainUid = getPodLabel(introspectorPod, LabelConstants.DOMAINUID_LABEL);
    DomainPresenceInfo info = getExistingDomainPresenceInfo(getNamespace(introspectorPod), domainUid);
    if (info == null) {
      return;
    }

    switch (watchType) {
      case "ADDED":
      case "MODIFIED":
        PodWatcher.PodStatus podStatus = PodWatcher.getPodStatus(introspectorPod);
        new DomainStatusUpdate(introspectorPod, domainUid, delegate, info, podStatus).invoke();
        break;
      case "DELETED":
        LOGGER.fine("Introspector Pod " + introspectorPod.getMetadata().getName()
            + " for domain " + domainUid + " is deleted.");
        break;
      default:
    }
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
      case "ADDED":
      case "MODIFIED":
        ServiceHelper.updatePresenceFromEvent(info, item.object);
        break;
      case "DELETED":
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
  public void dispatchPodDisruptionBudgetWatch(Watch.Response<V1beta1PodDisruptionBudget> item) {
    V1beta1PodDisruptionBudget pdb = item.object;
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
      case "ADDED":
      case "MODIFIED":
        PodDisruptionBudgetHelper.updatePDBFromEvent(info, item.object);
        break;
      case "DELETED":
        boolean removed = PodDisruptionBudgetHelper.deleteFromEvent(info, item.object);
        if (removed && info.isNotDeleting()) {
          createMakeRightOperation(info).interrupt().withExplicitRecheck().execute();
        }
        break;
      default:
    }
  }

  private String getPDBNamespace(V1beta1PodDisruptionBudget pdb) {
    return Optional.ofNullable(pdb).map(V1beta1PodDisruptionBudget::getMetadata)
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
        case "MODIFIED":
        case "DELETED":
          delegate.runSteps(
              ConfigMapHelper.createScriptConfigMapStep(
                    c.getMetadata().getNamespace()));
          break;

        case "ERROR":
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
        case "ADDED":
        case "MODIFIED":
          onCreateModifyEvent(e);
          break;
        case "DELETED":
          onDeleteEvent(e);
          break;
        case "ERROR":
        default:
      }
    }
  }

  /**
   * Dispatch the Domain event to the appropriate handler.
   *
   * @param item An item received from a Watch response.
   */
  public void dispatchDomainWatch(Watch.Response<Domain> item) {
    switch (item.type) {
      case "ADDED":
        handleAddedDomain(item.object);
        break;
      case "MODIFIED":
        handleModifiedDomain(item.object);
        break;
      case "DELETED":
        handleDeletedDomain(item.object);
        break;

      case "ERROR":
      default:
    }
  }

  private void handleAddedDomain(Domain domain) {
    LOGGER.info(MessageKeys.WATCH_DOMAIN, domain.getDomainUid());
    createMakeRightOperation(new DomainPresenceInfo(domain))
        .interrupt()
        .withExplicitRecheck()
        .withEventData(EventItem.DOMAIN_CREATED, null)
        .execute();
  }

  private void handleModifiedDomain(Domain domain) {
    LOGGER.fine(MessageKeys.WATCH_DOMAIN, domain.getDomainUid());
    createMakeRightOperation(new DomainPresenceInfo(domain))
        .interrupt()
        .withEventData(EventItem.DOMAIN_CHANGED, null)
        .execute();
  }

  private void handleDeletedDomain(Domain domain) {
    LOGGER.info(MessageKeys.WATCH_DOMAIN_DELETED, domain.getDomainUid());
    createMakeRightOperation(new DomainPresenceInfo(domain)).interrupt().forDeletion().withExplicitRecheck()
        .withEventData(EventItem.DOMAIN_DELETED, null)
        .execute();
  }

  private void scheduleDomainStatusUpdating(DomainPresenceInfo info) {
    final OncePerMessageLoggingFilter loggingFilter = new OncePerMessageLoggingFilter();

    MainTuning main = TuningParameters.getInstance().getMainTuning();
    registerStatusUpdater(
        info.getNamespace(),
        info.getDomainUid(),
        delegate.scheduleWithFixedDelay(
            () -> {
              try {
                Packet packet = new Packet();
                packet
                    .getComponents()
                    .put(
                        ProcessingConstants.DOMAIN_COMPONENT_NAME,
                        Component.createFor(
                            info, delegate.getKubernetesVersion()));
                packet.put(LoggingFilter.LOGGING_FILTER_PACKET_KEY, loggingFilter);
                Step strategy =
                    ServerStatusReader.createStatusStep(main.statusUpdateTimeoutSeconds, null);

                getStatusFiberGate(info.getNamespace())
                    .startFiberIfNoCurrentFiber(
                        info.getDomainUid(),
                        strategy,
                        packet,
                        new CompletionCallback() {
                          @Override
                          public void onCompletion(Packet packet) {
                            AtomicInteger serverHealthRead =
                                packet.getValue(
                                    ProcessingConstants.REMAINING_SERVERS_HEALTH_TO_READ);
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
                        });
              } catch (Throwable t) {
                try (LoggingContext ignored
                         = LoggingContext.setThreadContext()
                    .namespace(info.getNamespace()).domainUid(info.getDomainUid())) {
                  LOGGER.severe(MessageKeys.EXCEPTION, t);
                }
              }
            },
            main.initialShortDelay,
            main.initialShortDelay,
            TimeUnit.SECONDS));
  }

  private void logThrowable(Throwable throwable) {
    if (throwable instanceof Step.MultiThrowable) {
      for (Throwable t : ((Step.MultiThrowable) throwable).getThrowables()) {
        logThrowable(t);
      }
    } else if (throwable instanceof FailureStatusSourceException) {
      ((FailureStatusSourceException) throwable).log();
    } else {
      LOGGER.severe(MessageKeys.EXCEPTION, throwable);
    }
  }

  @Override
  public MakeRightDomainOperationImpl createMakeRightOperation(DomainPresenceInfo liveInfo) {
    return new MakeRightDomainOperationImpl(liveInfo);
  }

  Step createPopulatePacketServerMapsStep() {
    return new PopulatePacketServerMapsStep();
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
      Optional.ofNullable(packet.getSpi(DomainPresenceInfo.class))
          .map(DomainPresenceInfo::getDomain)
          .map(Domain::getStatus)
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

  /**
   * A factory which creates and executes steps to align the cached domain status with the value read from Kubernetes.
   */
  class MakeRightDomainOperationImpl implements MakeRightDomainOperation {

    private final DomainPresenceInfo liveInfo;
    private boolean explicitRecheck;
    private boolean deleting;
    private boolean willInterrupt;
    private boolean inspectionRun;
    private EventData eventData;

    /**
     * Create the operation.
     * @param liveInfo domain presence info read from Kubernetes
     */
    MakeRightDomainOperationImpl(DomainPresenceInfo liveInfo) {
      this.liveInfo = liveInfo;
      DomainPresenceInfo cachedInfo = getExistingDomainPresenceInfo(getNamespace(), getDomainUid());
      if (!isNewDomain(cachedInfo)
          && isAfter(getCreationTimestamp(liveInfo), getCreationTimestamp(cachedInfo))) {
        willInterrupt = true;
      }
    }

    private OffsetDateTime getCreationTimestamp(DomainPresenceInfo dpi) {
      return Optional.ofNullable(dpi.getDomain())
          .map(Domain::getMetadata).map(V1ObjectMeta::getCreationTimestamp).orElse(null);
    }

    private boolean isAfter(OffsetDateTime one, OffsetDateTime two) {
      if (two == null) {
        return true;
      }
      if (one == null) {
        return false;
      }
      return one.isAfter(two);
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
    public void execute() {
      try (LoggingContext ignored = LoggingContext.setThreadContext().presenceInfo(liveInfo)) {
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
    public boolean wasInspectionRun() {
      return inspectionRun;
    }

    @Override
    public boolean isExplicitRecheck() {
      return explicitRecheck;
    }

    private boolean shouldContinue() {
      DomainPresenceInfo cachedInfo = getExistingDomainPresenceInfo(getNamespace(), getDomainUid());

      if (isNewDomain(cachedInfo)) {
        return true;
      } else if (shouldReportAbortedEvent()) {
        return true;
      } else if (hasExceededRetryCount() && !isImgRestartIntrospectVerChanged(liveInfo, cachedInfo)) {
        LOGGER.severe(ProcessingConstants.EXCEEDED_INTROSPECTOR_MAX_RETRY_COUNT_ERROR_MSG);
        return false;
      } else if (isFatalIntrospectorError()) {
        LOGGER.fine(ProcessingConstants.FATAL_INTROSPECTOR_ERROR_MSG);
        return false;
      } else if (!liveInfo.isPopulated() && isCachedInfoNewer(liveInfo, cachedInfo)) {
        LOGGER.fine("Cached domain info is newer than the live info from the watch event .");
        return false;  // we have already cached this
      } else if (shouldRecheck(cachedInfo)) {

        if (hasExceededRetryCount()) {
          resetIntrospectorJobFailureCount();
        }
        if (getCurrentIntrospectFailureRetryCount() > 0) {
          logRetryCount(cachedInfo);
          ensureRetryingEventPresent();
        }
        LOGGER.fine("Continue the make-right domain presence, explicitRecheck -> " + explicitRecheck);
        return true;
      }
      cachedInfo.setDomain(getDomain());
      return false;
    }

    private boolean shouldReportAbortedEvent() {
      return Optional.ofNullable(eventData).map(EventData::getItem).orElse(null) == DOMAIN_PROCESSING_ABORTED;
    }

    private void ensureRetryingEventPresent() {
      if (eventData == null) {
        eventData = new EventData(DOMAIN_PROCESSING_RETRYING);
      }
    }

    private void resetIntrospectorJobFailureCount() {
      Optional.ofNullable(liveInfo)
          .map(DomainPresenceInfo::getDomain)
          .map(Domain::getStatus)
          .map(DomainStatus::resetIntrospectJobFailureCount);
    }

    private boolean hasExceededRetryCount() {
      return getCurrentIntrospectFailureRetryCount()
          >= DomainPresence.getDomainPresenceFailureRetryMaxCount();
    }

    private Integer getCurrentIntrospectFailureRetryCount() {
      return Optional.ofNullable(liveInfo)
          .map(DomainPresenceInfo::getDomain)
          .map(Domain::getStatus)
          .map(DomainStatus::getIntrospectJobFailureCount)
          .orElse(0);
    }

    private void logRetryCount(DomainPresenceInfo cachedInfo) {
      LOGGER.info(MessageKeys.INTROSPECT_JOB_FAILED_RETRY_COUNT, cachedInfo.getDomain().getDomainUid(),
          getCurrentIntrospectFailureRetryCount(),
          DomainPresence.getDomainPresenceFailureRetryMaxCount());
    }

    private boolean shouldRecheck(DomainPresenceInfo cachedInfo) {
      return explicitRecheck || isSpecChanged(liveInfo, cachedInfo);
    }

    private boolean isFatalIntrospectorError() {
      String existingError = Optional.ofNullable(liveInfo)
          .map(DomainPresenceInfo::getDomain)
          .map(Domain::getStatus)
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

      Packet packet = new Packet();
      packet.put(MAKE_RIGHT_DOMAIN_OPERATION, this);
      packet
          .getComponents()
          .put(
              ProcessingConstants.DOMAIN_COMPONENT_NAME,
              Component.createFor(liveInfo, delegate.getKubernetesVersion(),
                  PodAwaiterStepFactory.class, delegate.getPodAwaiterStepFactory(getNamespace()),
                  JobAwaiterStepFactory.class, delegate.getJobAwaiterStepFactory(getNamespace())));
      runDomainPlan(
            getDomain(),
            getDomainUid(),
            getNamespace(),
            createDomainPlanSteps(packet),
            deleting,
            willInterrupt);
    }

    private StepAndPacket createDomainPlanSteps(Packet packet) {
      if (containsAbortedEventData()) {
        return new StepAndPacket(Step.chain(createEventStep(eventData), new TailStep()), packet);
      }

      return new StepAndPacket(
          getEventStep(Step.chain(createPopulatePacketServerMapsStep(),  createSteps())), packet);
    }

    private Step getEventStep(Step next) {
      return Optional.ofNullable(eventData).map(ed -> Step.chain(createEventStep(ed), next)).orElse(next);
    }

    private boolean containsAbortedEventData() {
      return Optional.ofNullable(eventData).map(EventData::isProcessingAbortedEvent).orElse(false);
    }

    private Domain getDomain() {
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
      Step strategy =
            new StartPlanStep(liveInfo, deleting ? createDomainDownPlan(liveInfo) : createDomainUpPlan(liveInfo));
      if (deleting || getDomain() == null) {
        return strategy;
      } else {
        return DomainValidationSteps.createDomainValidationSteps(getNamespace(), strategy);
      }
    }
  }

  private static boolean isSpecChanged(DomainPresenceInfo liveInfo, DomainPresenceInfo cachedInfo) {
    // TODO, RJE: now that we are switching to updating domain status using the separate
    // status-specific endpoint, Kubernetes guarantees that changes to the main endpoint
    // will only be for metadata and spec, so we can know that we have an important
    // change just by looking at metadata.generation.
    return Optional.ofNullable(liveInfo.getDomain())
          .map(Domain::getSpec)
          .map(spec -> !spec.equals(cachedInfo.getDomain().getSpec()))
          .orElse(true);
  }


  private static boolean isImgRestartIntrospectVerChanged(DomainPresenceInfo liveInfo, DomainPresenceInfo cachedInfo) {
    return !Objects.equals(getIntrospectVersion(liveInfo), getIntrospectVersion(cachedInfo))
        || !Objects.equals(getRestartVersion(liveInfo), getRestartVersion(cachedInfo))
        || !Objects.equals(getIntrospectImage(liveInfo), getIntrospectImage(cachedInfo));
  }

  private static String getIntrospectImage(DomainPresenceInfo info) {
    return Optional.ofNullable(info)
        .map(DomainPresenceInfo::getDomain)
        .map(Domain::getSpec)
        .map(DomainSpec::getImage)
        .orElse(null);
  }

  private static String getRestartVersion(DomainPresenceInfo info) {
    return Optional.ofNullable(info)
        .map(DomainPresenceInfo::getDomain)
        .map(Domain::getRestartVersion)
        .orElse(null);
  }

  private static String getIntrospectVersion(DomainPresenceInfo info) {
    return Optional.ofNullable(info)
        .map(DomainPresenceInfo::getDomain)
        .map(Domain::getSpec)
        .map(DomainSpec::getIntrospectVersion)
        .orElse(null);
  }

  private static boolean isCachedInfoNewer(DomainPresenceInfo liveInfo, DomainPresenceInfo cachedInfo) {
    return liveInfo.getDomain() != null
        && KubernetesUtils.isFirstNewer(cachedInfo.getDomain().getMetadata(), liveInfo.getDomain().getMetadata());
  }

  @SuppressWarnings("unused")
  private void runDomainPlan(
      Domain dom,
      String domainUid,
      String ns,
      Step.StepAndPacket plan,
      boolean isDeleting,
      boolean isWillInterrupt) {
    FiberGate gate = getMakeRightFiberGate(ns);
    CompletionCallback cc =
        new CompletionCallback() {
          @Override
          public void onCompletion(Packet packet) {
            // no-op
          }

          @Override
          public void onThrowable(Packet packet, Throwable throwable) {
            logThrowable(throwable);

            gate.startFiberIfLastFiberMatches(
                domainUid,
                Fiber.getCurrentIfSet(),
                DomainStatusUpdater.createFailureRelatedSteps(throwable, null),
                plan.packet,
                new CompletionCallback() {
                  @Override
                  public void onCompletion(Packet packet) {
                    // no-op
                  }

                  @Override
                  public void onThrowable(Packet packet, Throwable throwable) {
                    logThrowable(throwable);
                  }
                });

            gate.getExecutor()
                .schedule(
                    () -> {
                      DomainPresenceInfo existing = getExistingDomainPresenceInfo(ns, domainUid);
                      if (existing != null) {
                        try (LoggingContext ignored =
                                 LoggingContext.setThreadContext().namespace(ns).domainUid(domainUid)) {
                          existing.setPopulated(false);
                          // proceed only if we have not already retried max number of times
                          int retryCount = existing.incrementAndGetFailureCount();
                          LOGGER.fine(
                              "Failure count for DomainPresenceInfo: "
                                  + existing
                                  + " is now: "
                                  + retryCount);
                          if (retryCount <= DomainPresence.getDomainPresenceFailureRetryMaxCount()) {
                            createMakeRightOperation(existing)
                                .withDeleting(isDeleting)
                                .withExplicitRecheck()
                                .withEventData(EventHelper.EventItem.DOMAIN_PROCESSING_RETRYING, null)
                                .execute();
                          } else {
                            LOGGER.severe(
                                MessageKeys.CANNOT_START_DOMAIN_AFTER_MAX_RETRIES,
                                domainUid,
                                ns,
                                DomainPresence.getDomainPresenceFailureRetryMaxCount(),
                                throwable);
                            createMakeRightOperation(existing)
                                .withEventData(DOMAIN_PROCESSING_ABORTED,
                                    String.format(
                                        "Unable to start domain %s after %s attempts due to exception: %s",
                                        domainUid,
                                        DomainPresence.getDomainPresenceFailureRetryMaxCount(),
                                        throwable))
                                .execute();
                          }
                        }
                      }
                    },
                    DomainPresence.getDomainPresenceFailureRetrySeconds(),
                    TimeUnit.SECONDS);
          }
        };

    LOGGER.fine("Starting fiber for domainUid -> " + domainUid + ", isWillInterrupt -> " + isWillInterrupt);
    if (isWillInterrupt) {
      gate.startFiber(domainUid, plan.step, plan.packet, cc);
    } else {
      gate.startFiberIfNoCurrentFiber(domainUid, plan.step, plan.packet, cc);
    }
  }

  Step createDomainUpPlan(DomainPresenceInfo info) {
    Step managedServerStrategy = Step.chain(
        bringManagedServersUp(null),
        DomainStatusUpdater.createEndProgressingStep(null),
        EventHelper.createEventStep(EventItem.DOMAIN_PROCESSING_COMPLETED),
        MonitorExporterSteps.updateExporterSidecars(),
        new TailStep());

    Step domainUpStrategy =
        Step.chain(
            domainIntrospectionSteps(info),
            DomainValidationSteps.createAfterIntrospectValidationSteps(),
            new DomainStatusStep(info, null),
            bringAdminServerUp(info, delegate.getPodAwaiterStepFactory(info.getNamespace())),
            managedServerStrategy);

    return Step.chain(
          createDomainUpInitialStep(info),
          ConfigMapHelper.readExistingIntrospectorConfigMap(info.getNamespace(), info.getDomainUid()),
          DomainPresenceStep.createDomainPresenceStep(info.getDomain(), domainUpStrategy, managedServerStrategy));
  }

  private Step createEventStep(EventData eventData) {
    return EventHelper.createEventStep(eventData);
  }

  private Step createDomainUpInitialStep(DomainPresenceInfo info) {
    return new UpHeadStep(info);
  }

  private Step createDomainDownPlan(DomainPresenceInfo info) {
    String ns = info.getNamespace();
    String domainUid = info.getDomainUid();
    return Step.chain(
        new DownHeadStep(info, ns),
        new DeleteDomainStep(info, ns, domainUid),
        new UnregisterStep(info),
        EventHelper.createEventStep(EventItem.DOMAIN_PROCESSING_COMPLETED));
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
      unregisterPresenceInfo(info.getNamespace(), info.getDomainUid());
      return doNext(packet);
    }
  }

  private static class TailStep extends Step {

    @Override
    public NextAction apply(Packet packet) {
      packet.getSpi(DomainPresenceInfo.class).complete();
      return doNext(packet);
    }
  }

  private static class StartPlanStep extends Step {
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
        Consumer<V1beta1PodDisruptionBudgetList> getPodDisruptionBudgetListProcessing() {
          return list -> list.getItems().forEach(this::addPodDisruptionBudget);
        }

        private void addPodDisruptionBudget(V1beta1PodDisruptionBudget pdb) {
          PodDisruptionBudgetHelper.addToPresence(info,pdb);
        }
      });

      return resources.createListSteps();
    }

  }

  private static class UpHeadStep extends Step {
    private final DomainPresenceInfo info;

    UpHeadStep(DomainPresenceInfo info) {
      this(info, null);
    }

    UpHeadStep(DomainPresenceInfo info, Step next) {
      super(next);
      this.info = info;
    }

    @Override
    public NextAction apply(Packet packet) {
      info.setDeleting(false);
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
  }

  private static class DomainStatusUpdate {
    private final V1Pod introspectorPod;
    private final String domainUid;
    private final DomainProcessorDelegate delegate;
    private final DomainPresenceInfo info;
    private final PodWatcher.PodStatus podStatus;

    DomainStatusUpdate(V1Pod introspectorPod, String domainUid, DomainProcessorDelegate delegate,
                       DomainPresenceInfo info, PodWatcher.PodStatus podStatus) {
      this.introspectorPod = introspectorPod;
      this.domainUid = domainUid;
      this.delegate = delegate;
      this.info = info;
      this.podStatus = podStatus;
    }

    private void invoke() {
      switch (podStatus) {
        case PHASE_FAILED:
          if (isNotTerminatedByOperator()) {
            delegate.runSteps(
                DomainStatusUpdater.createFailureRelatedSteps(
                    info, getPodStatusReason(), getPodStatusMessage(), null));
          }
          break;
        case WAITING_NON_NULL_MESSAGE:
          Optional.ofNullable(getMatchingContainerStatus())
                  .map(V1ContainerStatus::getState)
                  .map(V1ContainerState::getWaiting)
                  .ifPresent(waiting ->
                    delegate.runSteps(
                            DomainStatusUpdater.createFailureRelatedSteps(
                                    info, waiting.getReason(), waiting.getMessage(), null)));
          break;
        case TERMINATED_ERROR_REASON:
          Optional.ofNullable(getMatchingContainerStatus())
                  .map(V1ContainerStatus::getState)
                  .map(V1ContainerState::getTerminated)
                  .ifPresent(terminated -> delegate.runSteps(
                          DomainStatusUpdater.createFailureRelatedSteps(
                                  info, terminated.getReason(), terminated.getMessage(), null)));
          break;
        case UNSCHEDULABLE:
          Optional.ofNullable(getMatchingPodCondition())
                  .ifPresent(condition ->
                          delegate.runSteps(
                                  DomainStatusUpdater.createFailureRelatedSteps(
                                          info, condition.getReason(), condition.getMessage(), null)));
          break;
        case SUCCESS:
          Optional.ofNullable(getMatchingContainerStatus())
                  .map(V1ContainerStatus::getState)
                  .map(V1ContainerState::getWaiting)
                  .ifPresent(waiting ->
                          delegate.runSteps(
                                  DomainStatusUpdater.createProgressingStep(
                                          info, waiting.getReason(), false, null)));
          break;
        default:
      }
    }

    private boolean isNotTerminatedByOperator() {
      return notNullOrEmpty(getPodStatusReason()) || notNullOrEmpty(getPodStatusMessage()) || !isJobPodTerminated();
    }

    private boolean notNullOrEmpty(String value) {
      return value != null && value.length() > 0;
    }

    private boolean isJobPodTerminated() {
      return PodWatcher.getContainerStateTerminatedReason(getMatchingContainerStatus()).contains("Error");
    }

    private String getPodStatusReason() {
      return Optional.ofNullable(introspectorPod).map(V1Pod::getStatus).map(V1PodStatus::getReason).orElse(null);
    }

    private String getPodStatusMessage() {
      return Optional.ofNullable(introspectorPod).map(V1Pod::getStatus).map(V1PodStatus::getMessage).orElse(null);
    }

    private V1ContainerStatus getMatchingContainerStatus() {
      return Optional.ofNullable(introspectorPod.getStatus())
              .map(V1PodStatus::getContainerStatuses)
              .flatMap(this::getMatchingContainerStatus)
              .orElse(null);
    }

    private Optional<V1ContainerStatus> getMatchingContainerStatus(Collection<V1ContainerStatus> statuses) {
      return statuses.stream().filter(this::hasIntrospectorJobName).findFirst();
    }

    private V1PodCondition getMatchingPodCondition() {
      return Optional.ofNullable(introspectorPod.getStatus())
              .map(V1PodStatus::getConditions)
              .flatMap(this::getPodCondition)
              .orElse(null);
    }

    private Optional<V1PodCondition> getPodCondition(Collection<V1PodCondition> conditions) {
      return conditions.stream().findFirst();
    }

    private boolean hasIntrospectorJobName(V1ContainerStatus s) {
      return toJobIntrospectorName(domainUid).equals(s.getName());
    }
  }
}
