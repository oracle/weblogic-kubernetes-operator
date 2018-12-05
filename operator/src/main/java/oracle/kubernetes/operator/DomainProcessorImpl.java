// Copyright 2018, Oracle Corporation and/or its affiliates.  All rights reserved.
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
import java.util.concurrent.atomic.AtomicReference;
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
import oracle.kubernetes.operator.helpers.Scan;
import oracle.kubernetes.operator.helpers.ScanCache;
import oracle.kubernetes.operator.helpers.ServerKubernetesObjects;
import oracle.kubernetes.operator.helpers.ServiceHelper;
import oracle.kubernetes.operator.helpers.StorageHelper;
import oracle.kubernetes.operator.logging.LoggingFacade;
import oracle.kubernetes.operator.logging.LoggingFactory;
import oracle.kubernetes.operator.logging.MessageKeys;
import oracle.kubernetes.operator.steps.BeforeAdminServiceStep;
import oracle.kubernetes.operator.steps.DeleteDomainStep;
import oracle.kubernetes.operator.steps.DomainPresenceStep;
import oracle.kubernetes.operator.steps.ExternalAdminChannelsStep;
import oracle.kubernetes.operator.steps.ListPersistentVolumeClaimStep;
import oracle.kubernetes.operator.steps.ManagedServersUpStep;
import oracle.kubernetes.operator.steps.WatchPodReadyAdminStep;
import oracle.kubernetes.operator.wlsconfig.WlsDomainConfig;
import oracle.kubernetes.operator.work.Component;
import oracle.kubernetes.operator.work.Fiber;
import oracle.kubernetes.operator.work.Fiber.CompletionCallback;
import oracle.kubernetes.operator.work.FiberGate;
import oracle.kubernetes.operator.work.FiberGateFactory;
import oracle.kubernetes.operator.work.NextAction;
import oracle.kubernetes.operator.work.Packet;
import oracle.kubernetes.operator.work.Step;
import oracle.kubernetes.operator.work.Step.StepAndPacket;
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
  private static final ConcurrentMap<String, FiberGate> fiberGates = new ConcurrentHashMap<>();

  private static FiberGate getFiberGate(String ns) {
    return fiberGates.computeIfAbsent(ns, k -> FACTORY.get());
  }

  // Map from namespace to map of domainUID to Domain
  private static final ConcurrentMap<String, ConcurrentMap<String, DomainPresenceInfo>> domains =
      new ConcurrentHashMap<>();

  private DomainProcessorImpl() {}

  private static DomainPresenceInfo getExisting(String ns, String domainUID) {
    return domains.computeIfAbsent(ns, k -> new ConcurrentHashMap<>()).get(domainUID);
  }

  private static void register(DomainPresenceInfo info) {
    domains
        .computeIfAbsent(info.getNamespace(), k -> new ConcurrentHashMap<>())
        .put(info.getDomainUID(), info);
  }

  public void dispatchPodWatch(Watch.Response<V1Pod> item) {
    V1Pod p = item.object;
    if (p != null) {
      V1ObjectMeta metadata = p.getMetadata();
      String domainUID = metadata.getLabels().get(LabelConstants.DOMAINUID_LABEL);
      String serverName = metadata.getLabels().get(LabelConstants.SERVERNAME_LABEL);
      if (domainUID != null && serverName != null) {
        DomainPresenceInfo existing = getExisting(metadata.getNamespace(), domainUID);
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
        DomainPresenceInfo existing = getExisting(metadata.getNamespace(), domainUID);
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
        domainUID = d.getSpec().getDomainUID();
        LOGGER.info(MessageKeys.WATCH_DOMAIN, domainUID);
        makeRightDomainPresence(new DomainPresenceInfo(d), true, false, true);
        break;
      case "MODIFIED":
        d = item.object;
        domainUID = d.getSpec().getDomainUID();
        LOGGER.info(MessageKeys.WATCH_DOMAIN, domainUID);
        makeRightDomainPresence(new DomainPresenceInfo(d), false, false, true);
        break;
      case "DELETED":
        d = item.object;
        domainUID = d.getSpec().getDomainUID();
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
    AtomicReference<ScheduledFuture<?>> statusUpdater = info.getStatusUpdater();
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
              Main.engine
                  .createFiber()
                  .start(
                      strategy,
                      packet,
                      new CompletionCallback() {
                        @Override
                        public void onCompletion(Packet packet) {
                          Boolean isStatusUnchanged =
                              (Boolean) packet.get(ProcessingConstants.STATUS_UNCHANGED);
                          ScheduledFuture<?> existing = null;
                          if (Boolean.TRUE.equals(isStatusUnchanged)) {
                            if (unchangedCount.incrementAndGet()
                                == main.unchangedCountToDelayStatusRecheck) {
                              // slow down retries because of sufficient unchanged statuses
                              existing =
                                  statusUpdater.getAndSet(
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
                            existing =
                                statusUpdater.getAndSet(
                                    Main.engine
                                        .getExecutor()
                                        .scheduleWithFixedDelay(
                                            r,
                                            main.initialShortDelay,
                                            main.initialShortDelay,
                                            TimeUnit.SECONDS));
                            if (existing != null) {
                              existing.cancel(false);
                            }
                          }
                          if (existing != null) {
                            existing.cancel(false);
                          }
                        }

                        @Override
                        public void onThrowable(Packet packet, Throwable throwable) {
                          LOGGER.severe(MessageKeys.EXCEPTION, throwable);
                          // retry to trying after shorter delay because of exception
                          unchangedCount.set(0);
                          ScheduledFuture<?> existing =
                              statusUpdater.getAndSet(
                                  Main.engine
                                      .getExecutor()
                                      .scheduleWithFixedDelay(
                                          r,
                                          main.initialShortDelay,
                                          main.initialShortDelay,
                                          TimeUnit.SECONDS));
                          if (existing != null) {
                            existing.cancel(false);
                          }
                        }
                      });
            } catch (Throwable t) {
              LOGGER.severe(MessageKeys.EXCEPTION, t);
            }
          }
        };

    MainTuning main = Main.tuningAndConfig.getMainTuning();
    ScheduledFuture<?> existing =
        statusUpdater.getAndSet(
            Main.engine
                .getExecutor()
                .scheduleWithFixedDelay(
                    command, main.initialShortDelay, main.initialShortDelay, TimeUnit.SECONDS));

    if (existing != null) {
      existing.cancel(false);
    }
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
      DomainPresenceInfo existing = getExisting(ns, domainUID);
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
      Step strategy =
          Step.chain(readExistingSituConfigMap(info), new RegisterStep(info, getNext()));
      if (!info.isPopulated()) {
        strategy = Step.chain(readExistingPods(info), readExistingServices(info), strategy);
      }
      return doNext(strategy, packet);
    }
  }

  private static class RegisterStep extends Step {
    private final DomainPresenceInfo info;

    public RegisterStep(DomainPresenceInfo info, Step next) {
      super(next);
      this.info = info;
    }

    @Override
    public NextAction apply(Packet packet) {
      register(info);
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

  private static Step readExistingSituConfigMap(DomainPresenceInfo info) {
    String situConfigMapName =
        ConfigMapHelper.SitConfigMapContext.getConfigMapName(info.getDomainUID());
    return new CallBuilder()
        .readConfigMapAsync(
            situConfigMapName, info.getNamespace(), new ReadSituConfigMapStep(info));
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
    FiberGate gate = getFiberGate(ns);
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
                      DomainPresenceInfo existing = getExisting(ns, domainUID);
                      if (existing != null) {
                        existing.setPopulated(false);
                        makeRightDomainPresence(existing, true, isDeleting, false);
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
    Step adminServerStrategy = bringAdminServerUp(info, managedServerStrategy);

    return new UpHeadStep(
        info,
        DomainStatusUpdater.createProgressingStep(
            DomainStatusUpdater.INSPECTING_DOMAIN_PROGRESS_REASON,
            true,
            DomainPresenceStep.createDomainPresenceStep(
                dom, adminServerStrategy, managedServerStrategy)));
  }

  static Step createDomainDownPlan(DomainPresenceInfo info) {
    String ns = info.getNamespace();
    String domainUID = info.getDomainUID();
    Step deleteStep = new DeleteDomainStep(info, ns, domainUID);
    return new DownHeadStep(info, ns, deleteStep);
  }

  private static class UpHeadStep extends Step {
    private final DomainPresenceInfo info;

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
      scheduleDomainStatusUpdating(info);
      return doNext(packet);
    }
  }

  private static class DownHeadStep extends Step {
    private final DomainPresenceInfo info;
    private final String ns;

    public DownHeadStep(DomainPresenceInfo info, String ns, Step next) {
      super(next);
      this.info = info;
      this.ns = ns;
    }

    @Override
    public NextAction apply(Packet packet) {
      info.setDeleting(true);
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
    return StorageHelper.insertStorageSteps(
        info.getDomain(), Step.chain(bringAdminServerUpSteps(info, next)));
  }

  private static class ReadSituConfigMapStep extends ResponseStep<V1ConfigMap> {
    private final DomainPresenceInfo info;

    ReadSituConfigMapStep(DomainPresenceInfo info) {
      this.info = info;
    }

    @Override
    public NextAction onFailure(Packet packet, CallResponse<V1ConfigMap> callResponse) {
      System.out.println(
          "ReadSituConfigMapStep.onFailure callResponse.getStatusCode: "
              + callResponse.getStatusCode());
      return callResponse.getStatusCode() == CallBuilder.NOT_FOUND
          ? onSuccess(packet, callResponse)
          : super.onFailure(packet, callResponse);
    }

    @Override
    public NextAction onSuccess(Packet packet, CallResponse<V1ConfigMap> callResponse) {
      V1ConfigMap result = callResponse.getResult();
      System.out.println("ReadSituConfigMapStep.onSuccess result: " + result);
      if (result != null) {
        Map<String, String> data = result.getData();
        String topologyYaml = data.get("topology.yaml");
        if (topologyYaml != null) {
          System.out.println("topology.yaml: " + topologyYaml);
          ConfigMapHelper.DomainTopology domainTopology =
              ConfigMapHelper.parseDomainTopologyYaml(topologyYaml);
          WlsDomainConfig wlsDomainConfig = domainTopology.getDomain();
          ScanCache.INSTANCE.registerScan(
              info.getNamespace(), info.getDomainUID(), new Scan(wlsDomainConfig, new DateTime()));
        }
      }

      return doNext(packet);
    }
  }

  private static Step[] bringAdminServerUpSteps(DomainPresenceInfo info, Step next) {
    Domain dom = info.getDomain();
    List<Step> resources = new ArrayList<>();
    resources.add(new ListPersistentVolumeClaimStep(null));
    resources.add(
        JobHelper.deleteDomainIntrospectorJobStep(
            dom.getDomainUID(), dom.getMetadata().getNamespace(), null));
    resources.add(JobHelper.createDomainIntrospectorJobStep(null));
    resources.add(PodHelper.createAdminPodStep(null));
    resources.add(new BeforeAdminServiceStep(null));
    resources.add(ServiceHelper.createForServerStep(null));
    resources.add(new WatchPodReadyAdminStep(Main.podWatchers, null));
    resources.add(new ExternalAdminChannelsStep(next));
    return resources.toArray(new Step[0]);
  }

  private static Step bringManagedServersUp(Step next) {
    return new ManagedServersUpStep(next);
  }
}
