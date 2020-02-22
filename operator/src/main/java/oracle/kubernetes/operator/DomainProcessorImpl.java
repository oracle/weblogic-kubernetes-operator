// Copyright (c) 2018, 2020, Oracle Corporation and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import javax.annotation.Nullable;

import io.kubernetes.client.openapi.models.V1ConfigMap;
import io.kubernetes.client.openapi.models.V1ContainerState;
import io.kubernetes.client.openapi.models.V1ContainerStatus;
import io.kubernetes.client.openapi.models.V1Event;
import io.kubernetes.client.openapi.models.V1ObjectMeta;
import io.kubernetes.client.openapi.models.V1ObjectReference;
import io.kubernetes.client.openapi.models.V1Pod;
import io.kubernetes.client.openapi.models.V1PodList;
import io.kubernetes.client.openapi.models.V1PodStatus;
import io.kubernetes.client.openapi.models.V1Service;
import io.kubernetes.client.openapi.models.V1ServiceList;
import io.kubernetes.client.openapi.models.V1SubjectRulesReviewStatus;
import io.kubernetes.client.util.Watch;
import oracle.kubernetes.operator.TuningParameters.MainTuning;
import oracle.kubernetes.operator.calls.CallResponse;
import oracle.kubernetes.operator.helpers.CallBuilder;
import oracle.kubernetes.operator.helpers.ConfigMapHelper;
import oracle.kubernetes.operator.helpers.DomainPresenceInfo;
import oracle.kubernetes.operator.helpers.DomainStatusPatch;
import oracle.kubernetes.operator.helpers.DomainValidationSteps;
import oracle.kubernetes.operator.helpers.JobHelper;
import oracle.kubernetes.operator.helpers.KubernetesUtils;
import oracle.kubernetes.operator.helpers.PodHelper;
import oracle.kubernetes.operator.helpers.ResponseStep;
import oracle.kubernetes.operator.helpers.ServiceHelper;
import oracle.kubernetes.operator.logging.LoggingFacade;
import oracle.kubernetes.operator.logging.LoggingFactory;
import oracle.kubernetes.operator.logging.LoggingFilter;
import oracle.kubernetes.operator.logging.MessageKeys;
import oracle.kubernetes.operator.logging.OncePerMessageLoggingFilter;
import oracle.kubernetes.operator.steps.BeforeAdminServiceStep;
import oracle.kubernetes.operator.steps.DeleteDomainStep;
import oracle.kubernetes.operator.steps.DomainPresenceStep;
import oracle.kubernetes.operator.steps.ManagedServersUpStep;
import oracle.kubernetes.operator.steps.WatchPodReadyAdminStep;
import oracle.kubernetes.operator.work.Component;
import oracle.kubernetes.operator.work.Fiber;
import oracle.kubernetes.operator.work.Fiber.CompletionCallback;
import oracle.kubernetes.operator.work.FiberGate;
import oracle.kubernetes.operator.work.NextAction;
import oracle.kubernetes.operator.work.Packet;
import oracle.kubernetes.operator.work.Step;
import oracle.kubernetes.operator.work.Step.StepAndPacket;
import oracle.kubernetes.weblogic.domain.model.AdminServer;
import oracle.kubernetes.weblogic.domain.model.AdminService;
import oracle.kubernetes.weblogic.domain.model.Channel;
import oracle.kubernetes.weblogic.domain.model.Domain;
import oracle.kubernetes.weblogic.domain.model.DomainSpec;

import static oracle.kubernetes.operator.helpers.LegalNames.toJobIntrospectorName;

public class DomainProcessorImpl implements DomainProcessor {

  private static final LoggingFacade LOGGER = LoggingFactory.getLogger("Operator", "Operator");

  private static final Map<String, FiberGate> makeRightFiberGates = new ConcurrentHashMap<>();
  private static final Map<String, FiberGate> statusFiberGates = new ConcurrentHashMap<>();
  // Map from namespace to map of domainUID to Domain
  private static Map<String, Map<String, DomainPresenceInfo>> DOMAINS =
        new ConcurrentHashMap<>();
  private static final ConcurrentMap<String, ConcurrentMap<String, ScheduledFuture<?>>>
        statusUpdaters = new ConcurrentHashMap<>();
  private final DomainProcessorDelegate delegate;

  public DomainProcessorImpl(DomainProcessorDelegate delegate) {
    this.delegate = delegate;
  }

  private static DomainPresenceInfo getExistingDomainPresenceInfo(String ns, String domainUid) {
    return DOMAINS.computeIfAbsent(ns, k -> new ConcurrentHashMap<>()).get(domainUid);
  }

  private static void registerDomainPresenceInfo(DomainPresenceInfo info) {
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
    ConcurrentMap<String, ScheduledFuture<?>> map = statusUpdaters.get(ns);
    if (map != null) {
      ScheduledFuture<?> existing = map.remove(domainUid);
      if (existing != null) {
        existing.cancel(true);
      }
    }
  }

  private static void onEvent(V1Event event) {
    V1ObjectReference ref = event.getInvolvedObject();
    if (ref == null) {
      return;
    }

    String[] domainAndServer = ref.getName().split("-");
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

  private static String getReadinessStatus(V1Event event) {
    return Optional.ofNullable(event.getMessage())
          .filter(m -> m.contains(WebLogicConstants.READINESS_PROBE_NOT_READY_STATE))
          .map(m -> m.substring(m.lastIndexOf(':') + 1).trim())
          .orElse(null);
  }

  private static Step readExistingPods(DomainPresenceInfo info) {
    return new CallBuilder()
          .withLabelSelectors(
                LabelConstants.forDomainUidSelector(info.getDomainUid()),
                LabelConstants.CREATEDBYOPERATOR_LABEL)
          .listPodAsync(info.getNamespace(), new PodListStep(info));
  }

  // pre-conditions: DomainPresenceInfo SPI
  // "principal"
  static Step bringAdminServerUp(
        DomainPresenceInfo info, PodAwaiterStepFactory podAwaiterStepFactory, Step next) {
    return Step.chain(bringAdminServerUpSteps(info, podAwaiterStepFactory, next));
  }

  private static Step[] domainIntrospectionSteps(Step next) {
    List<Step> resources = new ArrayList<>();
    resources.add(JobHelper.deleteDomainIntrospectorJobStep(null));
    resources.add(JobHelper.createDomainIntrospectorJobStep(next));
    return resources.toArray(new Step[0]);
  }

  private static Step[] bringAdminServerUpSteps(
        DomainPresenceInfo info, PodAwaiterStepFactory podAwaiterStepFactory, Step next) {
    List<Step> resources = new ArrayList<>();
    resources.add(new BeforeAdminServiceStep(null));
    resources.add(PodHelper.createAdminPodStep(null));

    Domain dom = info.getDomain();
    AdminServer adminServer = dom.getSpec().getAdminServer();
    AdminService adminService = adminServer != null ? adminServer.getAdminService() : null;
    List<Channel> channels = adminService != null ? adminService.getChannels() : null;
    if (channels != null && !channels.isEmpty()) {
      resources.add(ServiceHelper.createForExternalServiceStep(null));
    }

    resources.add(ServiceHelper.createForServerStep(null));
    resources.add(new WatchPodReadyAdminStep(podAwaiterStepFactory, next));
    return resources.toArray(new Step[0]);
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
   * Stop namespace.
   * @param ns namespace
   */
  public void stopNamespace(String ns) {
    Map<String, DomainPresenceInfo> map = DOMAINS.get(ns);
    if (map != null) {
      for (DomainPresenceInfo dpi : map.values()) {
        Domain dom = dpi.getDomain();
        DomainPresenceInfo value =
            (dom != null)
                ? new DomainPresenceInfo(dom)
                : new DomainPresenceInfo(dpi.getNamespace(), dpi.getDomainUid());
        value.setDeleting(true);
        value.setPopulated(true);
        makeRightDomainPresence(value, true, true, false);
      }
    }
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
          makeRightDomainPresence(info, true, false, true);
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

  private void processIntrospectorJobPodWatch(V1Pod pod, String watchType) {
    String domainUid = getPodLabel(pod, LabelConstants.DOMAINUID_LABEL);
    DomainPresenceInfo info = getExistingDomainPresenceInfo(getNamespace(pod), domainUid);
    if (info == null) {
      return;
    }

    switch (watchType) {
      case "ADDED":
      case "MODIFIED":
        new DomainStatusUpdate(info.getDomain(), pod, domainUid).invoke();
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
    if (domainUid == null) {
      return;
    }

    DomainPresenceInfo info =
        getExistingDomainPresenceInfo(service.getMetadata().getNamespace(), domainUid);
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
          makeRightDomainPresence(info, true, false, true);
        }
        break;
      default:
    }
  }

  /**
   * Dispatch config map watch event.
   * @param item watch event
   */
  public void dispatchConfigMapWatch(Watch.Response<V1ConfigMap> item) {
    V1ConfigMap c = item.object;
    if (c != null) {
      switch (item.type) {
        case "MODIFIED":
        case "DELETED":
          delegate.runSteps(
              ConfigMapHelper.createScriptConfigMapStep(
                  delegate.getOperatorNamespace(), c.getMetadata().getNamespace()));
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
  public void dispatchEventWatch(Watch.Response<V1Event> item) {
    V1Event e = item.object;
    if (e != null) {
      switch (item.type) {
        case "ADDED":
        case "MODIFIED":
          onEvent(e);
          break;
        case "DELETED":
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
    Domain d;
    String domainUid;
    switch (item.type) {
      case "ADDED":
        d = item.object;
        domainUid = d.getDomainUid();
        LOGGER.info(MessageKeys.WATCH_DOMAIN, domainUid);
        makeRightDomainPresence(new DomainPresenceInfo(d), true, false, true);
        break;
      case "MODIFIED":
        d = item.object;
        domainUid = d.getDomainUid();
        LOGGER.info(MessageKeys.WATCH_DOMAIN, domainUid);
        makeRightDomainPresence(new DomainPresenceInfo(d), false, false, true);
        break;
      case "DELETED":
        d = item.object;
        domainUid = d.getDomainUid();
        LOGGER.info(MessageKeys.WATCH_DOMAIN_DELETED, domainUid);
        makeRightDomainPresence(new DomainPresenceInfo(d), true, true, true);
        break;

      case "ERROR":
      default:
    }
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
                V1SubjectRulesReviewStatus srrs = delegate.getSubjectRulesReviewStatus(info.getNamespace());
                Packet packet = new Packet();
                packet
                    .getComponents()
                    .put(
                        ProcessingConstants.DOMAIN_COMPONENT_NAME,
                        Component.createFor(info, delegate.getVersion(),
                            V1SubjectRulesReviewStatus.class, srrs));
                packet.put(LoggingFilter.LOGGING_FILTER_PACKET_KEY, loggingFilter);
                Step strategy =
                    ServerStatusReader.createStatusStep(main.statusUpdateTimeoutSeconds, null);
                FiberGate gate = getStatusFiberGate(info.getNamespace());

                Fiber f =
                    gate.startFiberIfNoCurrentFiber(
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
                            LOGGER.severe(MessageKeys.EXCEPTION, throwable);
                            loggingFilter.setFiltering(true);
                          }
                        });
              } catch (Throwable t) {
                LOGGER.severe(MessageKeys.EXCEPTION, t);
              }
            },
            main.initialShortDelay,
            main.initialShortDelay,
            TimeUnit.SECONDS));
  }

  /**
   * Begin activity to align domain status with domain resource.
   * @param info domain presence info
   * @param explicitRecheck if explicit recheck
   * @param isDeleting if is deleting domain
   * @param isWillInterrupt if will interrupt already running activities
   */
  public void makeRightDomainPresence(
      DomainPresenceInfo info,
      boolean explicitRecheck,
      boolean isDeleting,
      boolean isWillInterrupt) {
    Domain domain = info.getDomain();
    DomainSpec spec = null;
    if (domain != null) {
      spec = domain.getSpec();
      DomainPresenceControl.normalizeDomainSpec(spec);
    }
    String ns = info.getNamespace();
    String domainUid = info.getDomainUid();

    if (delegate.isNamespaceRunning(ns)) {
      DomainPresenceInfo existing = getExistingDomainPresenceInfo(ns, domainUid);
      if (existing != null) {
        Domain current = existing.getDomain();
        if (current != null) {
          // Is this an outdated watch event?
          if (domain != null
              && KubernetesUtils.isFirstNewer(current.getMetadata(), domain.getMetadata())) {
            LOGGER.fine(MessageKeys.NOT_STARTING_DOMAINUID_THREAD, domainUid);
            return;
          }
          // Has the spec actually changed? We will get watch events for status updates
          if (!explicitRecheck && spec != null && spec.equals(current.getSpec())) {
            // nothing in the spec has changed, but status likely did; update current
            existing.setDomain(domain);
            LOGGER.fine(MessageKeys.NOT_STARTING_DOMAINUID_THREAD, domainUid);
            return;
          }
        }
      }

      internalMakeRightDomainPresence(info, isDeleting, isWillInterrupt);
    }
  }

  private void internalMakeRightDomainPresence(
      @Nullable DomainPresenceInfo info, boolean isDeleting, boolean isWillInterrupt) {
    if (info == null) {
      return;
    }

    String ns = info.getNamespace();
    String domainUid = info.getDomainUid();
    Domain dom = info.getDomain();
    if (isDeleting || delegate.isNamespaceRunning(ns)) {
      LOGGER.info(MessageKeys.PROCESSING_DOMAIN, domainUid);
      Step strategy =
          new StartPlanStep(
              info, isDeleting ? createDomainDownPlan(info) : createDomainUpPlan(info));
      if (!isDeleting && dom != null) {
        strategy = DomainValidationSteps.createDomainValidationSteps(ns, strategy);
      }

      PodAwaiterStepFactory pw = delegate.getPodAwaiterStepFactory(info.getNamespace());
      V1SubjectRulesReviewStatus srrs = delegate.getSubjectRulesReviewStatus(info.getNamespace());
      Packet packet = new Packet();
      packet
          .getComponents()
          .put(
              ProcessingConstants.DOMAIN_COMPONENT_NAME,
              Component.createFor(info, delegate.getVersion(),
                  PodAwaiterStepFactory.class, pw,
                  V1SubjectRulesReviewStatus.class, srrs));
      runDomainPlan(
          dom,
          domainUid,
          ns,
          new StepAndPacket(strategy, packet),
          isDeleting,
          isWillInterrupt);
    }
  }

  private Step readExistingServices(DomainPresenceInfo info) {
    return new CallBuilder()
        .withLabelSelectors(
            LabelConstants.forDomainUidSelector(info.getDomainUid()),
            LabelConstants.CREATEDBYOPERATOR_LABEL)
        .listServiceAsync(info.getNamespace(), new ServiceListStep(info));
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
            LOGGER.severe(MessageKeys.EXCEPTION, throwable);

            gate.startFiberIfLastFiberMatches(
                domainUid,
                Fiber.getCurrentIfSet(),
                DomainStatusUpdater.createFailedStep(throwable, null),
                plan.packet,
                new CompletionCallback() {
                  @Override
                  public void onCompletion(Packet packet) {
                    // no-op
                  }

                  @Override
                  public void onThrowable(Packet packet, Throwable throwable) {
                    LOGGER.severe(MessageKeys.EXCEPTION, throwable);
                  }
                });

            gate.getExecutor()
                .schedule(
                    () -> {
                      DomainPresenceInfo existing = getExistingDomainPresenceInfo(ns, domainUid);
                      if (existing != null) {
                        existing.setPopulated(false);
                        // proceed only if we have not already retried max number of times
                        int retryCount = existing.incrementAndGetFailureCount();
                        LOGGER.fine(
                            "Failure count for DomainPresenceInfo: "
                                + existing
                                + " is now: "
                                + retryCount);
                        if (retryCount <= DomainPresence.getDomainPresenceFailureRetryMaxCount()) {
                          makeRightDomainPresence(existing, true, isDeleting, false);
                        } else {
                          LOGGER.severe(
                              MessageKeys.CANNOT_START_DOMAIN_AFTER_MAX_RETRIES,
                              domainUid,
                              ns,
                              DomainPresence.getDomainPresenceFailureRetryMaxCount(),
                              throwable);
                        }
                      }
                    },
                    DomainPresence.getDomainPresenceFailureRetrySeconds(),
                    TimeUnit.SECONDS);
          }
        };

    if (isWillInterrupt) {
      gate.startFiber(domainUid, plan.step, plan.packet, cc);
    } else {
      gate.startFiberIfNoCurrentFiber(domainUid, plan.step, plan.packet, cc);
    }
  }

  Step createDomainUpPlan(DomainPresenceInfo info) {
    Domain dom = info.getDomain();
    Step managedServerStrategy =
        bringManagedServersUp(DomainStatusUpdater.createEndProgressingStep(new TailStep()));

    Step strategy =
        Step.chain(
            domainIntrospectionSteps(
                new DomainStatusStep(
                    info,
                    bringAdminServerUp(
                        info,
                        delegate.getPodAwaiterStepFactory(info.getNamespace()),
                        managedServerStrategy))));

    strategy =
        DomainStatusUpdater.createProgressingStep(
            DomainStatusUpdater.INSPECTING_DOMAIN_PROGRESS_REASON,
            true,
            DomainPresenceStep.createDomainPresenceStep(dom, strategy, managedServerStrategy));

    return Step.chain(
        new UpHeadStep(info),
        ConfigMapHelper.readExistingSituConfigMap(info.getNamespace(), info.getDomainUid()),
        strategy);
  }

  private Step createDomainDownPlan(DomainPresenceInfo info) {
    String ns = info.getNamespace();
    String domainUid = info.getDomainUid();
    return Step.chain(
        new DownHeadStep(info, ns),
        new DeleteDomainStep(info, ns, domainUid),
        new UnregisterStep(info));
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

  private static class PodListStep extends ResponseStep<V1PodList> {
    private final DomainPresenceInfo info;

    PodListStep(DomainPresenceInfo info) {
      this.info = info;
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
      if (result != null) {
        for (V1Pod pod : result.getItems()) {
          String serverName = PodHelper.getPodServerName(pod);
          if (serverName != null) {
            info.setServerPod(serverName, pod);
          }
        }
      }
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

  private class StartPlanStep extends Step {
    private final DomainPresenceInfo info;

    StartPlanStep(DomainPresenceInfo info, Step next) {
      super(next);
      this.info = info;
    }

    @Override
    public NextAction apply(Packet packet) {
      registerDomainPresenceInfo(info);
      Step strategy = getNext();
      if (!info.isPopulated() && info.isNotDeleting()) {
        strategy = Step.chain(readExistingPods(info), readExistingServices(info), strategy);
      }
      return doNext(strategy, packet);
    }
  }

  private class ServiceListStep extends ResponseStep<V1ServiceList> {
    private final DomainPresenceInfo info;

    ServiceListStep(DomainPresenceInfo info) {
      this.info = info;
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

      if (result != null) {
        for (V1Service service : result.getItems()) {
          ServiceHelper.addToPresence(info, service);
        }
      }

      return doNext(packet);
    }
  }

  private class UpHeadStep extends Step {
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

  private class DownHeadStep extends Step {
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

  private class DomainStatusUpdate {
    private final Domain domain;
    private final V1Pod pod;
    private final String domainUid;

    DomainStatusUpdate(Domain domain, V1Pod pod, String domainUid) {
      this.domain = domain;
      this.pod = pod;
      this.domainUid = domainUid;
    }

    public void invoke() {
      Optional.ofNullable(getMatchingContainerStatus())
            .map(V1ContainerStatus::getState)
            .map(V1ContainerState::getWaiting)
            .ifPresent(waiting -> updateStatus(waiting.getReason(), waiting.getMessage()));
    }

    private void updateStatus(String reason, String message) {
      if (reason == null || message == null) {
        return;
      }
      
      DomainStatusPatch.updateSynchronously(domain, reason, message);
    }

    private V1ContainerStatus getMatchingContainerStatus() {
      return Optional.ofNullable(pod.getStatus())
            .map(V1PodStatus::getContainerStatuses)
            .flatMap(this::getMatchingContainerStatus)
            .orElse(null);
    }

    private Optional<V1ContainerStatus> getMatchingContainerStatus(Collection<V1ContainerStatus> statuses) {
      return statuses.stream().filter(this::hasInstrospectorJobName).findFirst();
    }

    private boolean hasInstrospectorJobName(V1ContainerStatus s) {
      return toJobIntrospectorName(domainUid).equals(s.getName());
    }
  }
}
