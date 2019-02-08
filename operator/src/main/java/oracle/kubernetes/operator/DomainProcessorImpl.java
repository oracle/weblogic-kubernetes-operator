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
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Predicate;
import javax.annotation.Nullable;
import oracle.kubernetes.operator.TuningParameters.MainTuning;
import oracle.kubernetes.operator.calls.CallResponse;
import oracle.kubernetes.operator.helpers.CallBuilder;
import oracle.kubernetes.operator.helpers.ConfigMapHelper;
import oracle.kubernetes.operator.helpers.DomainPresenceInfo;
import oracle.kubernetes.operator.helpers.JobHelper;
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
import oracle.kubernetes.operator.work.FiberGateFactory;
import oracle.kubernetes.operator.work.NextAction;
import oracle.kubernetes.operator.work.Packet;
import oracle.kubernetes.operator.work.Step;
import oracle.kubernetes.operator.work.Step.StepAndPacket;
import oracle.kubernetes.weblogic.domain.v2.AdminServer;
import oracle.kubernetes.weblogic.domain.v2.AdminService;
import oracle.kubernetes.weblogic.domain.v2.Channel;
import oracle.kubernetes.weblogic.domain.v2.Domain;
import oracle.kubernetes.weblogic.domain.v2.DomainSpec;
import org.joda.time.DateTime;

public class DomainProcessorImpl implements DomainProcessor {
  static final DomainProcessor INSTANCE = new DomainProcessorImpl();

  private static final LoggingFacade LOGGER = LoggingFactory.getLogger("Operator", "Operator");

  private static final FiberGateFactory FACTORY =
      () -> {
        return new FiberGate(Main.engine);
      };
  private static final ConcurrentMap<String, FiberGate> makeRightFiberGates =
      new ConcurrentHashMap<>();
  private static final ConcurrentMap<String, FiberGate> statusFiberGates =
      new ConcurrentHashMap<>();

  private static FiberGate getMakeRightFiberGate(String ns) {
    return makeRightFiberGates.computeIfAbsent(ns, k -> FACTORY.get());
  }

  private static FiberGate getStatusFiberGate(String ns) {
    return statusFiberGates.computeIfAbsent(ns, k -> FACTORY.get());
  }

  // Map from namespace to map of domainUID to Domain
  private static final ConcurrentMap<String, ConcurrentMap<String, DomainPresenceInfo>> domains =
      new ConcurrentHashMap<>();

  private static DomainPresenceInfo getExistingDomainPresenceInfo(String ns, String domainUID) {
    return domains.computeIfAbsent(ns, k -> new ConcurrentHashMap<>()).get(domainUID);
  }

  private static void registerDomainPresenceInfo(DomainPresenceInfo info) {
    domains
        .computeIfAbsent(info.getNamespace(), k -> new ConcurrentHashMap<>())
        .put(info.getDomainUID(), info);
  }

  private static void unregisterPresenceInfo(String ns, String domainUID) {
    ConcurrentMap<String, DomainPresenceInfo> map = domains.get(ns);
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

  private DomainProcessorImpl() {}

  public void stopNamespace(String ns) {
    ConcurrentMap<String, DomainPresenceInfo> map = domains.get(ns);
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
        DomainPresenceInfo existing =
            getExistingDomainPresenceInfo(metadata.getNamespace(), domainUID);
        if (existing != null) {
          ServerKubernetesObjects sko =
              existing.getServers().computeIfAbsent(serverName, k -> new ServerKubernetesObjects());
          if (sko != null) {
            switch (item.type) {
              case "ADDED":
                sko.getPod()
                    .accumulateAndGet(
                        p,
                        (current, ob) -> {
                          if (current != null) {
                            if (isOutdated(current.getMetadata(), metadata)) {
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
                            if (!isOutdated(current.getMetadata(), metadata)) {
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
                                if (!isOutdated(current.getMetadata(), metadata)) {
                                  sko.getLastKnownStatus().set(WebLogicConstants.SHUTDOWN_STATE);
                                  return null;
                                }
                              }
                              return current;
                            });
                if (oldPod != null && !existing.isDeleting()) {
                  // Pod was deleted, but sko still contained a non-null entry
                  LOGGER.info(
                      MessageKeys.POD_DELETED, domainUID, metadata.getNamespace(), serverName);
                  makeRightDomainPresence(existing, true, false, true);
                }
                break;

              case "ERROR":
              default:
            }
          }
        }
      }
    }
  }

  public void dispatchServiceWatch(Watch.Response<V1Service> item) {
    V1Service s = item.object;
    if (s != null) {
      V1ObjectMeta metadata = s.getMetadata();
      String domainUID = metadata.getLabels().get(LabelConstants.DOMAINUID_LABEL);
      String serverName = metadata.getLabels().get(LabelConstants.SERVERNAME_LABEL);
      String channelName = metadata.getLabels().get(LabelConstants.CHANNELNAME_LABEL);
      String clusterName = metadata.getLabels().get(LabelConstants.CLUSTERNAME_LABEL);
      if (domainUID != null) {
        DomainPresenceInfo existing =
            getExistingDomainPresenceInfo(metadata.getNamespace(), domainUID);
        if (existing != null) {
          switch (item.type) {
            case "ADDED":
              if (clusterName != null) {
                existing
                    .getClusters()
                    .compute(
                        clusterName,
                        (key, current) -> {
                          if (current != null) {
                            if (isOutdated(current.getMetadata(), metadata)) {
                              return current;
                            }
                          }
                          return s;
                        });
              } else if (serverName != null) {
                ServerKubernetesObjects sko =
                    existing
                        .getServers()
                        .computeIfAbsent(serverName, k -> new ServerKubernetesObjects());
                if (channelName != null) {
                  sko.getChannels()
                      .compute(
                          channelName,
                          (key, current) -> {
                            if (current != null) {
                              if (isOutdated(current.getMetadata(), metadata)) {
                                return current;
                              }
                            }
                            return s;
                          });
                } else {
                  sko.getService()
                      .accumulateAndGet(
                          s,
                          (current, ob) -> {
                            if (current != null) {
                              if (isOutdated(current.getMetadata(), metadata)) {
                                return current;
                              }
                            }
                            return ob;
                          });
                }
              }
              break;
            case "MODIFIED":
              if (clusterName != null) {
                existing
                    .getClusters()
                    .compute(
                        clusterName,
                        (key, current) -> {
                          if (current != null) {
                            if (!isOutdated(current.getMetadata(), metadata)) {
                              return s;
                            }
                          }
                          return current;
                        });
              } else if (serverName != null) {
                ServerKubernetesObjects sko =
                    existing
                        .getServers()
                        .computeIfAbsent(serverName, k -> new ServerKubernetesObjects());
                if (channelName != null) {
                  sko.getChannels()
                      .compute(
                          channelName,
                          (key, current) -> {
                            if (current != null) {
                              if (!isOutdated(current.getMetadata(), metadata)) {
                                return s;
                              }
                            }
                            return current;
                          });
                } else {
                  sko.getService()
                      .accumulateAndGet(
                          s,
                          (current, ob) -> {
                            if (current != null) {
                              if (!isOutdated(current.getMetadata(), metadata)) {
                                return ob;
                              }
                            }
                            return current;
                          });
                }
              }
              break;
            case "DELETED":
              if (clusterName != null) {
                boolean removed =
                    removeIfPresentAnd(
                        existing.getClusters(),
                        clusterName,
                        (current) -> {
                          return isOutdated(current.getMetadata(), metadata);
                        });
                if (removed && !existing.isDeleting()) {
                  // Service was deleted, but clusters still contained a non-null entry
                  LOGGER.info(
                      MessageKeys.CLUSTER_SERVICE_DELETED,
                      domainUID,
                      metadata.getNamespace(),
                      clusterName);
                  makeRightDomainPresence(existing, true, false, true);
                }
              } else if (serverName != null) {
                ServerKubernetesObjects sko = existing.getServers().get(serverName);
                if (sko != null) {
                  if (channelName != null) {
                    boolean removed =
                        removeIfPresentAnd(
                            sko.getChannels(),
                            channelName,
                            (current) -> {
                              return isOutdated(current.getMetadata(), metadata);
                            });
                    if (removed && !existing.isDeleting()) {
                      // Service was deleted, but sko still contained a non-null entry
                      LOGGER.info(
                          MessageKeys.SERVER_SERVICE_DELETED,
                          domainUID,
                          metadata.getNamespace(),
                          serverName);
                      makeRightDomainPresence(existing, true, false, true);
                    }
                  } else {
                    V1Service oldService =
                        sko.getService()
                            .getAndAccumulate(
                                s,
                                (current, ob) -> {
                                  if (current != null) {
                                    if (!isOutdated(current.getMetadata(), metadata)) {
                                      return null;
                                    }
                                  }
                                  return current;
                                });
                    if (oldService != null && !existing.isDeleting()) {
                      // Service was deleted, but sko still contained a non-null entry
                      LOGGER.info(
                          MessageKeys.SERVER_SERVICE_DELETED,
                          domainUID,
                          metadata.getNamespace(),
                          serverName);
                      makeRightDomainPresence(existing, true, false, true);
                    }
                  }
                }
              }
              break;

            case "ERROR":
            default:
          }
        }
      }
    }
  }

  private static <K, V> boolean removeIfPresentAnd(
      ConcurrentMap<K, V> map, K key, Predicate<? super V> predicateFunction) {
    Objects.requireNonNull(predicateFunction);
    for (V oldValue; (oldValue = map.get(key)) != null; ) {
      if (predicateFunction.test(oldValue)) {
        if (map.remove(key, oldValue)) {
          return true;
        }
      } else {
        return false;
      }
    }
    return false;
  }

  public void dispatchConfigMapWatch(Watch.Response<V1ConfigMap> item) {
    V1ConfigMap c = item.object;
    if (c != null) {
      switch (item.type) {
        case "MODIFIED":
        case "DELETED":
          Main.runSteps(
              ConfigMapHelper.createScriptConfigMapStep(
                  Main.getOperatorNamespace(), c.getMetadata().getNamespace()));
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
          ConcurrentMap<String, DomainPresenceInfo> map = domains.get(ns);
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
  static boolean isOutdated(V1ObjectMeta current, V1ObjectMeta ob) {
    if (ob == null) {
      return true;
    }
    if (current == null) {
      return false;
    }

    DateTime obTime = ob.getCreationTimestamp();
    DateTime cuTime = current.getCreationTimestamp();

    if (obTime.isAfter(cuTime)) {
      return false;
    }
    return Integer.parseInt(ob.getResourceVersion())
            < Integer.parseInt(current.getResourceVersion())
        || obTime.isBefore(cuTime);
  }

  private static void scheduleDomainStatusUpdating(DomainPresenceInfo info) {
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
                      Component.createFor(info, Main.getVersion()));
              MainTuning main = Main.tuningAndConfig.getMainTuning();
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
                              Main.engine
                                  .getExecutor()
                                  .scheduleWithFixedDelay(
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
                            Main.engine
                                .getExecutor()
                                .scheduleWithFixedDelay(
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
                          Main.engine
                              .getExecutor()
                              .scheduleWithFixedDelay(
                                  r,
                                  main.initialShortDelay,
                                  main.initialShortDelay,
                                  TimeUnit.SECONDS));
                    }
                  });
            } catch (Throwable t) {
              LOGGER.severe(MessageKeys.EXCEPTION, t);
            }
          }
        };

    MainTuning main = Main.tuningAndConfig.getMainTuning();
    registerStatusUpdater(
        info.getNamespace(),
        info.getDomainUID(),
        Main.engine
            .getExecutor()
            .scheduleWithFixedDelay(
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

    if (!Main.isNamespaceStopping(ns).get()) {
      DomainPresenceInfo existing = getExistingDomainPresenceInfo(ns, domainUID);
      if (existing != null) {
        Domain current = existing.getDomain();
        if (current != null) {
          // Is this an outdated watch event?
          if (domain != null && isOutdated(current.getMetadata(), domain.getMetadata())) {
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

  private static final Map<String, JobWatcher> jws = new ConcurrentHashMap<>();

  private void internalMakeRightDomainPresence(
      @Nullable DomainPresenceInfo info, boolean isDeleting, boolean isWillInterrupt) {
    String ns = info.getNamespace();
    String domainUID = info.getDomainUID();
    Domain dom = info.getDomain();
    if (isDeleting || !Main.isNamespaceStopping(ns).get()) {
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

  private static class StartPlanStep extends Step {
    private final DomainPresenceInfo info;

    public StartPlanStep(DomainPresenceInfo info, Step next) {
      super(next);
      this.info = info;
    }

    @Override
    public NextAction apply(Packet packet) {
      registerDomainPresenceInfo(info);
      Step strategy = getNext();
      if (!info.isPopulated() && !info.isDeleting()) {
        strategy = Step.chain(readExistingPods(info), readExistingServices(info), strategy);
      }
      return doNext(strategy, packet);
    }
  }

  private static class UnregisterStep extends Step {
    private final DomainPresenceInfo info;

    public UnregisterStep(DomainPresenceInfo info) {
      this(info, null);
    }

    public UnregisterStep(DomainPresenceInfo info, Step next) {
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
            LabelConstants.forDomainUid(info.getDomainUID()),
            LabelConstants.CREATEDBYOPERATOR_LABEL)
        .listPodAsync(info.getNamespace(), new PodListStep(info));
  }

  private static Step readExistingServices(DomainPresenceInfo info) {
    return new CallBuilder()
        .withLabelSelectors(
            LabelConstants.forDomainUid(info.getDomainUID()),
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

  private static class ServiceListStep extends ResponseStep<V1ServiceList> {
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
          String serverName = ServiceWatcher.getServiceServerName(service);
          String channelName = ServiceWatcher.getServiceChannelName(service);
          String clusterName = ServiceWatcher.getServiceClusterName(service);
          if (clusterName != null) {
            info.getClusters().put(clusterName, service);
          } else if (serverName != null) {
            ServerKubernetesObjects sko =
                info.getServers().computeIfAbsent(serverName, k -> new ServerKubernetesObjects());
            if (channelName != null) {
              sko.getChannels().put(channelName, service);
            } else {
              sko.getService().set(service);
            }
          }
        }
      }

      return doNext(packet);
    }
  }

  void runDomainPlan(
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

  static Step createDomainUpPlan(DomainPresenceInfo info) {
    Domain dom = info.getDomain();
    Step managedServerStrategy =
        bringManagedServersUp(DomainStatusUpdater.createEndProgressingStep(new TailStep()));

    Step strategy =
        Step.chain(
            domainIntrospectionSteps(
                info, new DomainStatusStep(info, bringAdminServerUp(info, managedServerStrategy))));

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

  static Step createDomainDownPlan(DomainPresenceInfo info) {
    String ns = info.getNamespace();
    String domainUID = info.getDomainUID();
    return Step.chain(
        new DownHeadStep(info, ns),
        new DeleteDomainStep(info, ns, domainUID),
        new UnregisterStep(info));
  }

  private static class UpHeadStep extends Step {
    private final DomainPresenceInfo info;

    public UpHeadStep(DomainPresenceInfo info) {
      this(info, null);
    }

    public UpHeadStep(DomainPresenceInfo info, Step next) {
      super(next);
      this.info = info;
    }

    @Override
    public NextAction apply(Packet packet) {
      PodWatcher pw = Main.podWatchers.get(info.getNamespace());
      info.setDeleting(false);
      packet
          .getComponents()
          .put(
              ProcessingConstants.DOMAIN_COMPONENT_NAME,
              Component.createFor(info, Main.getVersion(), PodAwaiterStepFactory.class, pw));
      packet.put(ProcessingConstants.PRINCIPAL, Main.getPrincipal());
      return doNext(packet);
    }
  }

  private static class DomainStatusStep extends Step {
    private final DomainPresenceInfo info;

    public DomainStatusStep(DomainPresenceInfo info, Step next) {
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

    public DownHeadStep(DomainPresenceInfo info, String ns) {
      this(info, ns, null);
    }

    public DownHeadStep(DomainPresenceInfo info, String ns, Step next) {
      super(next);
      this.info = info;
      this.ns = ns;
    }

    @Override
    public NextAction apply(Packet packet) {
      info.setDeleting(true);
      unregisterStatusUpdater(ns, info.getDomainUID());
      PodWatcher pw = Main.podWatchers.get(ns);
      packet
          .getComponents()
          .put(
              ProcessingConstants.DOMAIN_COMPONENT_NAME,
              Component.createFor(info, Main.getVersion(), PodAwaiterStepFactory.class, pw));
      packet.put(ProcessingConstants.PRINCIPAL, Main.getPrincipal());
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
  private static Step bringAdminServerUp(DomainPresenceInfo info, Step next) {
    return Step.chain(bringAdminServerUpSteps(info, next));
  }

  private static Step[] domainIntrospectionSteps(DomainPresenceInfo info, Step next) {
    Domain dom = info.getDomain();
    List<Step> resources = new ArrayList<>();
    resources.add(
        JobHelper.deleteDomainIntrospectorJobStep(
            dom.getDomainUID(), dom.getMetadata().getNamespace(), null));
    resources.add(
        JobHelper.createDomainIntrospectorJobStep(
            Main.tuningAndConfig.getWatchTuning(),
            next,
            jws,
            Main.isNamespaceStopping(dom.getMetadata().getNamespace())));
    return resources.toArray(new Step[resources.size()]);
  }

  private static Step[] bringAdminServerUpSteps(DomainPresenceInfo info, Step next) {
    List<Step> resources = new ArrayList<>();
    resources.add(PodHelper.createAdminPodStep(null));
    resources.add(new BeforeAdminServiceStep(null));

    Domain dom = info.getDomain();
    AdminServer adminServer = dom.getSpec().getAdminServer();
    AdminService adminService = adminServer != null ? adminServer.getAdminService() : null;
    List<Channel> channels = adminService != null ? adminService.getChannels() : null;
    if (channels != null && !channels.isEmpty()) {
      resources.add(ServiceHelper.createForExternalServiceStep(null));
    }

    resources.add(ServiceHelper.createForServerStep(null));
    resources.add(new WatchPodReadyAdminStep(Main.podWatchers, next));
    return resources.toArray(new Step[resources.size()]);
  }

  private static Step bringManagedServersUp(Step next) {
    return new ManagedServersUpStep(next);
  }
}
