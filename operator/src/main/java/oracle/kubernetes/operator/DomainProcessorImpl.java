// Copyright 2018, 2019, Oracle Corporation and/or its affiliates.  All rights reserved.
// Licensed under the Universal Permissive License v 1.0 as shown at
// http://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator;

import io.kubernetes.client.models.V1ConfigMap;
import io.kubernetes.client.models.V1Event;
import io.kubernetes.client.models.V1ObjectMeta;
import io.kubernetes.client.models.V1ObjectReference;
import io.kubernetes.client.models.V1Pod;
import io.kubernetes.client.models.V1PodList;
import io.kubernetes.client.models.V1Service;
import io.kubernetes.client.models.V1ServiceList;
import io.kubernetes.client.util.Watch;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import javax.annotation.Nullable;
import oracle.kubernetes.operator.TuningParameters.MainTuning;
import oracle.kubernetes.operator.calls.CallResponse;
import oracle.kubernetes.operator.helpers.CallBuilder;
import oracle.kubernetes.operator.helpers.ConfigMapHelper;
import oracle.kubernetes.operator.helpers.DomainPresenceInfo;
import oracle.kubernetes.operator.helpers.JobHelper;
import oracle.kubernetes.operator.helpers.KubernetesUtils;
import oracle.kubernetes.operator.helpers.PodHelper;
import oracle.kubernetes.operator.helpers.ResponseStep;
import oracle.kubernetes.operator.helpers.ServerKubernetesObjects;
import oracle.kubernetes.operator.helpers.ServiceHelper;
import oracle.kubernetes.operator.logging.LoggingFacade;
import oracle.kubernetes.operator.logging.LoggingFactory;
import oracle.kubernetes.operator.logging.MessageKeys;
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

public class DomainProcessorImpl implements DomainProcessor {

  private static final LoggingFacade LOGGER = LoggingFactory.getLogger("Operator", "Operator");

  private static final Map<String, FiberGate> makeRightFiberGates = new ConcurrentHashMap<>();
  private static final Map<String, FiberGate> statusFiberGates = new ConcurrentHashMap<>();
  private DomainProcessorDelegate delegate;

  public DomainProcessorImpl(DomainProcessorDelegate delegate) {
    this.delegate = delegate;
  }

  private FiberGate getMakeRightFiberGate(String ns) {
    return makeRightFiberGates.computeIfAbsent(ns, k -> delegate.createFiberGate());
  }

  private FiberGate getStatusFiberGate(String ns) {
    return statusFiberGates.computeIfAbsent(ns, k -> delegate.createFiberGate());
  }

  // Map from namespace to map of domainUID to Domain
  private static final Map<String, Map<String, DomainPresenceInfo>> DOMAINS =
      new ConcurrentHashMap<>();

  private static DomainPresenceInfo getExistingDomainPresenceInfo(String ns, String domainUID) {
    return DOMAINS.computeIfAbsent(ns, k -> new ConcurrentHashMap<>()).get(domainUID);
  }

  private static void registerDomainPresenceInfo(DomainPresenceInfo info) {
    DOMAINS
        .computeIfAbsent(info.getNamespace(), k -> new ConcurrentHashMap<>())
        .put(info.getDomainUID(), info);
  }

  private static void unregisterPresenceInfo(String ns, String domainUID) {
    Map<String, DomainPresenceInfo> map = DOMAINS.get(ns);
    if (map != null) {
      map.remove(domainUID);
    }
  }

  private static final ConcurrentMap<String, ConcurrentMap<String, ScheduledFuture<?>>>
      statusUpdaters = new ConcurrentHashMap<>();

  private static void registerStatusUpdater(
      String ns, String domainUID, ScheduledFuture<?> future) {
    ScheduledFuture<?> existing =
        statusUpdaters.computeIfAbsent(ns, k -> new ConcurrentHashMap<>()).put(domainUID, future);
    if (existing != null) {
      existing.cancel(false);
    }
  }

  private static void unregisterStatusUpdater(String ns, String domainUID) {
    ConcurrentMap<String, ScheduledFuture<?>> map = statusUpdaters.get(ns);
    if (map != null) {
      ScheduledFuture<?> existing = map.remove(domainUID);
      if (existing != null) {
        existing.cancel(true);
      }
    }
  }

  public void stopNamespace(String ns) {
    Map<String, DomainPresenceInfo> map = DOMAINS.get(ns);
    if (map != null) {
      for (DomainPresenceInfo dpi : map.values()) {
        Domain dom = dpi.getDomain();
        DomainPresenceInfo value =
            (dom != null)
                ? new DomainPresenceInfo(dom)
                : new DomainPresenceInfo(dpi.getNamespace(), dpi.getDomainUID());
        value.setDeleting(true);
        value.setPopulated(true);
        makeRightDomainPresence(value, true, true, false);
      }
    }
  }

  public void dispatchPodWatch(Watch.Response<V1Pod> item) {
    V1Pod p = item.object;
    if (p != null) {
      V1ObjectMeta metadata = p.getMetadata();
      String domainUID = metadata.getLabels().get(LabelConstants.DOMAINUID_LABEL);
      String serverName = metadata.getLabels().get(LabelConstants.SERVERNAME_LABEL);
      if (domainUID != null && serverName != null) {
        DomainPresenceInfo info = getExistingDomainPresenceInfo(metadata.getNamespace(), domainUID);
        if (info != null) {
          ServerKubernetesObjects sko =
              info.getServers().computeIfAbsent(serverName, k -> new ServerKubernetesObjects());
          switch (item.type) {
            case "ADDED":
              sko.getPod()
                  .accumulateAndGet(
                      p,
                      (current, ob) -> {
                        if (current != null) {
                          if (KubernetesUtils.isFirstNewer(current.getMetadata(), metadata)) {
                            return current;
                          }
                        }
                        return ob;
                      });
              break;
            case "MODIFIED":
              if (PodWatcher.isReady(p)) {
                sko.getLastKnownStatus().set(WebLogicConstants.RUNNING_STATE);
              } else {
                sko.getLastKnownStatus().compareAndSet(WebLogicConstants.RUNNING_STATE, null);
              }
              sko.getPod()
                  .accumulateAndGet(
                      p,
                      (current, ob) -> {
                        if (current != null) {
                          if (!KubernetesUtils.isFirstNewer(current.getMetadata(), metadata)) {
                            return ob;
                          }
                        }
                        // If the skoPod is null then the operator deleted this pod
                        // and modifications are to the terminating pod
                        return current;
                      });
              break;
            case "DELETED":
              V1Pod oldPod =
                  sko.getPod()
                      .getAndAccumulate(
                          p,
                          (current, ob) -> {
                            if (current != null) {
                              if (!KubernetesUtils.isFirstNewer(current.getMetadata(), metadata)) {
                                sko.getLastKnownStatus().set(WebLogicConstants.SHUTDOWN_STATE);
                                return null;
                              }
                            }
                            return current;
                          });
              if (oldPod != null && info.isNotDeleting()) {
                // Pod was deleted, but sko still contained a non-null entry
                LOGGER.info(
                    MessageKeys.POD_DELETED, domainUID, metadata.getNamespace(), serverName);
                makeRightDomainPresence(info, true, false, true);
              }
              break;

            case "ERROR":
            default:
          }
        }
      }
    }
  }

  public void dispatchServiceWatch(Watch.Response<V1Service> item) {
    V1Service service = item.object;
    String domainUID = ServiceHelper.getServiceDomainUID(service);
    if (domainUID == null) return;

    DomainPresenceInfo info =
        getExistingDomainPresenceInfo(service.getMetadata().getNamespace(), domainUID);
    if (info == null) return;

    switch (item.type) {
      case "ADDED":
      case "MODIFIED":
        ServiceHelper.updatePresenceFromEvent(info, item.object);
        break;
      case "DELETED":
        boolean removed = ServiceHelper.deleteFromEvent(info, item.object);
        if (removed && info.isNotDeleting()) makeRightDomainPresence(info, true, false, true);
        break;
      default:
    }
  }

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

  private static void onEvent(V1Event event) {
    V1ObjectReference ref = event.getInvolvedObject();
    if (ref != null) {
      String name = ref.getName();
      String message = event.getMessage();
      if (message != null) {
        if (message.contains(WebLogicConstants.READINESS_PROBE_NOT_READY_STATE)) {
          String ns = event.getMetadata().getNamespace();
          Map<String, DomainPresenceInfo> map = DOMAINS.get(ns);
          if (map != null) {
            for (DomainPresenceInfo d : map.values()) {
              String domainUIDPlusDash = d.getDomainUID() + "-";
              if (name.startsWith(domainUIDPlusDash)) {
                String serverName = name.substring(domainUIDPlusDash.length());
                ServerKubernetesObjects sko = d.getServers().get(serverName);
                if (sko != null) {
                  int idx = message.lastIndexOf(':');
                  sko.getLastKnownStatus().set(message.substring(idx + 1).trim());
                  break;
                }
              }
            }
          }
        }
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
    String domainUID;
    switch (item.type) {
      case "ADDED":
        d = item.object;
        domainUID = d.getDomainUID();
        LOGGER.info(MessageKeys.WATCH_DOMAIN, domainUID);
        makeRightDomainPresence(new DomainPresenceInfo(d), true, false, true);
        break;
      case "MODIFIED":
        d = item.object;
        domainUID = d.getDomainUID();
        LOGGER.info(MessageKeys.WATCH_DOMAIN, domainUID);
        makeRightDomainPresence(new DomainPresenceInfo(d), false, false, true);
        break;
      case "DELETED":
        d = item.object;
        domainUID = d.getDomainUID();
        LOGGER.info(MessageKeys.WATCH_DOMAIN_DELETED, domainUID);
        makeRightDomainPresence(new DomainPresenceInfo(d), true, true, true);
        break;

      case "ERROR":
      default:
    }
  }

  /* Recently, we've seen a number of intermittent bugs where K8s reports
   * outdated watch events.  There seem to be two main cases: 1) a DELETED
   * event for a resource that was deleted, but has since been recreated, and 2)
   * a MODIFIED event for an object that has already had subsequent modifications.
   */

  private void scheduleDomainStatusUpdating(DomainPresenceInfo info) {
    AtomicInteger unchangedCount = new AtomicInteger(0);
    Runnable command =
        new Runnable() {
          public void run() {
            try {
              Runnable r = this; // resolve visibility
              Packet packet = new Packet();
              packet
                  .getComponents()
                  .put(
                      ProcessingConstants.DOMAIN_COMPONENT_NAME,
                      Component.createFor(info, delegate.getVersion()));
              MainTuning main = TuningParameters.getInstance().getMainTuning();
              Step strategy =
                  DomainStatusUpdater.createStatusStep(main.statusUpdateTimeoutSeconds, null);
              FiberGate gate = getStatusFiberGate(info.getNamespace());
              gate.startFiberIfNoCurrentFiber(
                  info.getDomainUID(),
                  strategy,
                  packet,
                  new CompletionCallback() {
                    @Override
                    public void onCompletion(Packet packet) {
                      Boolean isStatusUnchanged =
                          (Boolean) packet.get(ProcessingConstants.STATUS_UNCHANGED);
                      if (Boolean.TRUE.equals(isStatusUnchanged)) {
                        if (unchangedCount.incrementAndGet()
                            == main.unchangedCountToDelayStatusRecheck) {
                          // slow down retries because of sufficient unchanged statuses
                          registerStatusUpdater(
                              info.getNamespace(),
                              info.getDomainUID(),
                              delegate.scheduleWithFixedDelay(
                                  r,
                                  main.eventualLongDelay,
                                  main.eventualLongDelay,
                                  TimeUnit.SECONDS));
                        }
                      } else {
                        // reset to trying after shorter delay because of changed status
                        unchangedCount.set(0);
                        registerStatusUpdater(
                            info.getNamespace(),
                            info.getDomainUID(),
                            delegate.scheduleWithFixedDelay(
                                r,
                                main.initialShortDelay,
                                main.initialShortDelay,
                                TimeUnit.SECONDS));
                      }
                    }

                    @Override
                    public void onThrowable(Packet packet, Throwable throwable) {
                      LOGGER.severe(MessageKeys.EXCEPTION, throwable);
                      // retry to trying after shorter delay because of exception
                      unchangedCount.set(0);
                      registerStatusUpdater(
                          info.getNamespace(),
                          info.getDomainUID(),
                          delegate.scheduleWithFixedDelay(
                              r, main.initialShortDelay, main.initialShortDelay, TimeUnit.SECONDS));
                    }
                  });
            } catch (Throwable t) {
              LOGGER.severe(MessageKeys.EXCEPTION, t);
            }
          }
        };

    MainTuning main = TuningParameters.getInstance().getMainTuning();
    registerStatusUpdater(
        info.getNamespace(),
        info.getDomainUID(),
        delegate.scheduleWithFixedDelay(
            command, main.initialShortDelay, main.initialShortDelay, TimeUnit.SECONDS));
  }

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
    String domainUID = info.getDomainUID();

    if (delegate.isNamespaceRunning(ns)) {
      DomainPresenceInfo existing = getExistingDomainPresenceInfo(ns, domainUID);
      if (existing != null) {
        Domain current = existing.getDomain();
        if (current != null) {
          // Is this an outdated watch event?
          if (domain != null
              && KubernetesUtils.isFirstNewer(current.getMetadata(), domain.getMetadata())) {
            LOGGER.fine(MessageKeys.NOT_STARTING_DOMAINUID_THREAD, domainUID);
            return;
          }
          // Has the spec actually changed? We will get watch events for status updates
          if (!explicitRecheck && spec != null && spec.equals(current.getSpec())) {
            // nothing in the spec has changed, but status likely did; update current
            existing.setDomain(domain);
            LOGGER.fine(MessageKeys.NOT_STARTING_DOMAINUID_THREAD, domainUID);
            return;
          }
        }
      }

      internalMakeRightDomainPresence(info, isDeleting, isWillInterrupt);
    }
  }

  private void internalMakeRightDomainPresence(
      @Nullable DomainPresenceInfo info, boolean isDeleting, boolean isWillInterrupt) {
    String ns = info.getNamespace();
    String domainUID = info.getDomainUID();
    Domain dom = info.getDomain();
    if (isDeleting || delegate.isNamespaceRunning(ns)) {
      LOGGER.info(MessageKeys.PROCESSING_DOMAIN, domainUID);
      Step strategy =
          new StartPlanStep(
              info, isDeleting ? createDomainDownPlan(info) : createDomainUpPlan(info));

      runDomainPlan(
          dom,
          domainUID,
          ns,
          new StepAndPacket(strategy, new Packet()),
          isDeleting,
          isWillInterrupt);
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
      unregisterPresenceInfo(info.getNamespace(), info.getDomainUID());
      return doNext(packet);
    }
  }

  private static Step readExistingPods(DomainPresenceInfo info) {
    return new CallBuilder()
        .withLabelSelectors(
            LabelConstants.forDomainUidSelector(info.getDomainUID()),
            LabelConstants.CREATEDBYOPERATOR_LABEL)
        .listPodAsync(info.getNamespace(), new PodListStep(info));
  }

  private Step readExistingServices(DomainPresenceInfo info) {
    return new CallBuilder()
        .withLabelSelectors(
            LabelConstants.forDomainUidSelector(info.getDomainUID()),
            LabelConstants.CREATEDBYOPERATOR_LABEL)
        .listServiceAsync(info.getNamespace(), new ServiceListStep(info));
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
          String serverName = PodWatcher.getPodServerName(pod);
          if (serverName != null) {
            ServerKubernetesObjects sko =
                info.getServers().computeIfAbsent(serverName, k -> new ServerKubernetesObjects());
            sko.getPod().set(pod);
          }
        }
      }
      return doNext(packet);
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

  private void runDomainPlan(
      Domain dom,
      String domainUID,
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
                domainUID,
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
                      DomainPresenceInfo existing = getExistingDomainPresenceInfo(ns, domainUID);
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
                              domainUID,
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
      gate.startFiber(domainUID, plan.step, plan.packet, cc);
    } else {
      gate.startFiberIfNoCurrentFiber(domainUID, plan.step, plan.packet, cc);
    }
  }

  Step createDomainUpPlan(DomainPresenceInfo info) {
    Domain dom = info.getDomain();
    Step managedServerStrategy =
        bringManagedServersUp(DomainStatusUpdater.createEndProgressingStep(new TailStep()));

    Step strategy =
        Step.chain(
            domainIntrospectionSteps(
                info,
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
        ConfigMapHelper.readExistingSituConfigMap(info.getNamespace(), info.getDomainUID()),
        strategy);
  }

  private Step createDomainDownPlan(DomainPresenceInfo info) {
    String ns = info.getNamespace();
    String domainUID = info.getDomainUID();
    return Step.chain(
        new DownHeadStep(info, ns),
        new DeleteDomainStep(info, ns, domainUID),
        new UnregisterStep(info));
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
      PodAwaiterStepFactory pw = delegate.getPodAwaiterStepFactory(info.getNamespace());
      info.setDeleting(false);
      packet
          .getComponents()
          .put(
              ProcessingConstants.DOMAIN_COMPONENT_NAME,
              Component.createFor(info, delegate.getVersion(), PodAwaiterStepFactory.class, pw));
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
      unregisterStatusUpdater(ns, info.getDomainUID());
      PodAwaiterStepFactory pw = delegate.getPodAwaiterStepFactory(ns);
      packet
          .getComponents()
          .put(
              ProcessingConstants.DOMAIN_COMPONENT_NAME,
              Component.createFor(info, delegate.getVersion(), PodAwaiterStepFactory.class, pw));
      return doNext(packet);
    }
  }

  private static class TailStep extends Step {

    @Override
    public NextAction apply(Packet packet) {
      packet.getSPI(DomainPresenceInfo.class).complete();
      return doNext(packet);
    }
  }

  // pre-conditions: DomainPresenceInfo SPI
  // "principal"
  static Step bringAdminServerUp(
      DomainPresenceInfo info, PodAwaiterStepFactory podAwaiterStepFactory, Step next) {
    return Step.chain(bringAdminServerUpSteps(info, podAwaiterStepFactory, next));
  }

  private static Step[] domainIntrospectionSteps(DomainPresenceInfo info, Step next) {
    Domain dom = info.getDomain();
    List<Step> resources = new ArrayList<>();
    resources.add(
        JobHelper.deleteDomainIntrospectorJobStep(
            dom.getDomainUID(), dom.getMetadata().getNamespace(), null));
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
}
