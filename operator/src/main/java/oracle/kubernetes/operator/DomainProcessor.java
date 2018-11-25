// Copyright 2018, Oracle Corporation and/or its affiliates.  All rights reserved.
// Licensed under the Universal Permissive License v 1.0 as shown at
// http://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator;

import io.kubernetes.client.models.V1ConfigMap;
import io.kubernetes.client.models.V1Event;
import io.kubernetes.client.models.V1ObjectMeta;
import io.kubernetes.client.models.V1ObjectReference;
import io.kubernetes.client.models.V1Pod;
import io.kubernetes.client.models.V1Service;
import io.kubernetes.client.models.V1beta1Ingress;
import io.kubernetes.client.util.Watch;
import java.util.Objects;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Predicate;
import javax.annotation.Nullable;
import oracle.kubernetes.operator.TuningParameters.MainTuning;
import oracle.kubernetes.operator.helpers.ConfigMapHelper;
import oracle.kubernetes.operator.helpers.DomainPresenceInfo;
import oracle.kubernetes.operator.helpers.DomainPresenceInfoManager;
import oracle.kubernetes.operator.helpers.PodHelper;
import oracle.kubernetes.operator.helpers.ServerKubernetesObjects;
import oracle.kubernetes.operator.helpers.ServerKubernetesObjectsManager;
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
import oracle.kubernetes.operator.wlsconfig.WlsRetriever;
import oracle.kubernetes.operator.work.Component;
import oracle.kubernetes.operator.work.Fiber;
import oracle.kubernetes.operator.work.Fiber.CompletionCallback;
import oracle.kubernetes.operator.work.FiberGate;
import oracle.kubernetes.operator.work.NextAction;
import oracle.kubernetes.operator.work.Packet;
import oracle.kubernetes.operator.work.Step;
import oracle.kubernetes.weblogic.domain.v2.Domain;
import oracle.kubernetes.weblogic.domain.v2.DomainSpec;
import org.joda.time.DateTime;

public class DomainProcessor {
  private static final LoggingFacade LOGGER = LoggingFactory.getLogger("Operator", "Operator");

  private static final FiberGate FIBER_GATE = new FiberGate(Main.engine);

  private DomainProcessor() {}

  static void dispatchPodWatch(Watch.Response<V1Pod> item) {
    V1Pod p = item.object;
    if (p != null) {
      V1ObjectMeta metadata = p.getMetadata();
      String domainUID = metadata.getLabels().get(LabelConstants.DOMAINUID_LABEL);
      String serverName = metadata.getLabels().get(LabelConstants.SERVERNAME_LABEL);
      DomainPresenceInfo info;
      ServerKubernetesObjects sko;
      if (domainUID != null) {
        if (serverName != null) {
          switch (item.type) {
            case "ADDED":
              info = DomainPresenceInfoManager.getOrCreate(metadata.getNamespace(), domainUID);
              sko = ServerKubernetesObjectsManager.getOrCreate(info, domainUID, serverName);
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
              info = DomainPresenceInfoManager.getOrCreate(metadata.getNamespace(), domainUID);
              sko = ServerKubernetesObjectsManager.getOrCreate(info, domainUID, serverName);
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
              info = DomainPresenceInfoManager.lookup(metadata.getNamespace(), domainUID);
              if (info != null) {
                sko =
                    ServerKubernetesObjectsManager.lookup(
                        metadata.getNamespace(), metadata.getName());
                if (sko != null) {
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
                  if (oldPod != null && !info.isDeleting()) {
                    // Pod was deleted, but sko still contained a non-null entry
                    LOGGER.info(
                        MessageKeys.POD_DELETED, domainUID, metadata.getNamespace(), serverName);
                    makeRightDomainPresence(info, domainUID, info.getDomain(), true, false, true);
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

  static void dispatchServiceWatch(Watch.Response<V1Service> item) {
    V1Service s = item.object;
    if (s != null) {
      V1ObjectMeta metadata = s.getMetadata();
      String domainUID = metadata.getLabels().get(LabelConstants.DOMAINUID_LABEL);
      String serverName = metadata.getLabels().get(LabelConstants.SERVERNAME_LABEL);
      String channelName = metadata.getLabels().get(LabelConstants.CHANNELNAME_LABEL);
      String clusterName = metadata.getLabels().get(LabelConstants.CLUSTERNAME_LABEL);
      DomainPresenceInfo info;
      ServerKubernetesObjects sko;
      if (domainUID != null) {
        switch (item.type) {
          case "ADDED":
            info = DomainPresenceInfoManager.getOrCreate(metadata.getNamespace(), domainUID);
            if (clusterName != null) {
              info.getClusters()
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
              sko = ServerKubernetesObjectsManager.getOrCreate(info, domainUID, serverName);
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
            info = DomainPresenceInfoManager.getOrCreate(metadata.getNamespace(), domainUID);
            if (clusterName != null) {
              info.getClusters()
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
              sko = ServerKubernetesObjectsManager.getOrCreate(info, domainUID, serverName);
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
            info = DomainPresenceInfoManager.lookup(metadata.getNamespace(), domainUID);
            if (info != null) {
              if (clusterName != null) {
                boolean removed =
                    removeIfPresentAnd(
                        info.getClusters(),
                        clusterName,
                        (current) -> {
                          return isOutdated(current.getMetadata(), metadata);
                        });
                if (removed && !info.isDeleting()) {
                  // Service was deleted, but clusters still contained a non-null entry
                  LOGGER.info(
                      MessageKeys.CLUSTER_SERVICE_DELETED,
                      domainUID,
                      metadata.getNamespace(),
                      clusterName);
                  makeRightDomainPresence(info, domainUID, info.getDomain(), true, false, true);
                }
              } else if (serverName != null) {
                sko =
                    ServerKubernetesObjectsManager.lookup(
                        metadata.getNamespace(), metadata.getName());
                if (sko != null) {
                  if (channelName != null) {
                    boolean removed =
                        removeIfPresentAnd(
                            sko.getChannels(),
                            channelName,
                            (current) -> {
                              return isOutdated(current.getMetadata(), metadata);
                            });
                    if (removed && !info.isDeleting()) {
                      // Service was deleted, but sko still contained a non-null entry
                      LOGGER.info(
                          MessageKeys.SERVER_SERVICE_DELETED,
                          domainUID,
                          metadata.getNamespace(),
                          serverName);
                      makeRightDomainPresence(info, domainUID, info.getDomain(), true, false, true);
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
                    if (oldService != null && !info.isDeleting()) {
                      // Service was deleted, but sko still contained a non-null entry
                      LOGGER.info(
                          MessageKeys.SERVER_SERVICE_DELETED,
                          domainUID,
                          metadata.getNamespace(),
                          serverName);
                      makeRightDomainPresence(info, domainUID, info.getDomain(), true, false, true);
                    }
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

  static void dispatchIngressWatch(Watch.Response<V1beta1Ingress> item) {
    V1beta1Ingress i = item.object;
    if (i != null) {
      V1ObjectMeta metadata = i.getMetadata();
      String domainUID = metadata.getLabels().get(LabelConstants.DOMAINUID_LABEL);
      String clusterName = metadata.getLabels().get(LabelConstants.CLUSTERNAME_LABEL);
      DomainPresenceInfo info;
      if (domainUID != null) {
        if (clusterName != null) {
          switch (item.type) {
            case "ADDED":
              info = DomainPresenceInfoManager.getOrCreate(metadata.getNamespace(), domainUID);
              info.getIngresses()
                  .compute(
                      clusterName,
                      (key, current) -> {
                        if (current != null) {
                          if (isOutdated(current.getMetadata(), metadata)) {
                            return current;
                          }
                        }
                        return i;
                      });
              break;
            case "MODIFIED":
              info = DomainPresenceInfoManager.getOrCreate(metadata.getNamespace(), domainUID);
              info.getIngresses()
                  .compute(
                      clusterName,
                      (key, current) -> {
                        if (current != null) {
                          if (!isOutdated(current.getMetadata(), metadata)) {
                            return i;
                          }
                        }
                        return current;
                      });
              break;
            case "DELETED":
              info = DomainPresenceInfoManager.lookup(metadata.getNamespace(), domainUID);
              if (info != null) {
                boolean removed =
                    removeIfPresentAnd(
                        info.getIngresses(),
                        clusterName,
                        (current) -> {
                          return isOutdated(current.getMetadata(), metadata);
                        });
                if (removed && !info.isDeleting()) {
                  // Ingress was deleted, but sko still contained a non-null entry
                  LOGGER.info(
                      MessageKeys.INGRESS_DELETED, domainUID, metadata.getNamespace(), clusterName);
                  makeRightDomainPresence(info, domainUID, info.getDomain(), true, false, true);
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

  static void dispatchConfigMapWatch(Watch.Response<V1ConfigMap> item) {
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

  static void dispatchEventWatch(Watch.Response<V1Event> item) {
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
          ServerKubernetesObjects sko =
              ServerKubernetesObjectsManager.lookup(event.getMetadata().getNamespace(), name);
          if (sko != null) {
            int idx = message.lastIndexOf(':');
            sko.getLastKnownStatus().set(message.substring(idx + 1).trim());
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
  static void dispatchDomainWatch(Watch.Response<Domain> item) {
    Domain d;
    String domainUID;
    DomainPresenceInfo existing;
    boolean added = false;
    switch (item.type) {
      case "ADDED":
        added = true;
      case "MODIFIED":
        d = item.object;
        domainUID = d.getSpec().getDomainUID();
        LOGGER.info(MessageKeys.WATCH_DOMAIN, domainUID);
        existing = DomainPresenceInfoManager.lookup(d.getMetadata().getNamespace(), domainUID);
        makeRightDomainPresence(existing, domainUID, d, added, false, true);
        break;

      case "DELETED":
        d = item.object;
        domainUID = d.getSpec().getDomainUID();
        LOGGER.info(MessageKeys.WATCH_DOMAIN_DELETED, domainUID);
        existing = DomainPresenceInfoManager.lookup(d.getMetadata().getNamespace(), domainUID);
        if (existing != null) {
          makeRightDomainPresence(existing, domainUID, d, true, true, true);
        }
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

  static void makeRightDomainPresence(
      DomainPresenceInfo existing,
      String domainUID,
      Domain dom,
      boolean explicitRecheck,
      boolean isDeleting,
      boolean isWillInterrupt) {
    LOGGER.entering();

    DomainSpec spec = null;
    String ns;
    if (dom != null) {
      spec = dom.getSpec();
      DomainPresenceControl.normalizeDomainSpec(spec);
      ns = dom.getMetadata().getNamespace();
    } else {
      ns = existing.getNamespace();
    }

    if (existing != null) {
      Domain current = existing.getDomain();
      if (current != null) {
        // Is this an outdated watch event?
        if (isOutdated(current.getMetadata(), dom.getMetadata())) {
          LOGGER.fine(MessageKeys.NOT_STARTING_DOMAINUID_THREAD, domainUID);
          return;
        }
        // Has the spec actually changed? We will get watch events for status updates
        if (!explicitRecheck && spec != null && spec.equals(current.getSpec())) {
          // nothing in the spec has changed, but status likely did; update current
          existing.setDomain(dom);
          LOGGER.fine(MessageKeys.NOT_STARTING_DOMAINUID_THREAD, domainUID);
          return;
        }
      }
    }

    internalMakeRightDomainPresence(existing, dom, domainUID, ns, isDeleting, isWillInterrupt);
  }

  private static void internalMakeRightDomainPresence(
      @Nullable DomainPresenceInfo existing,
      Domain dom,
      String domainUID,
      String ns,
      boolean isDeleting,
      boolean isWillInterrupt) {
    if (isDeleting || !Main.isNamespaceStopping(ns).get()) {
      LOGGER.info(MessageKeys.PROCESSING_DOMAIN, domainUID);
      Step.StepAndPacket plan =
          isDeleting ? createDomainDownPlan(existing, ns, domainUID) : createDomainUpPlan(dom, ns);

      runDomainPlan(dom, domainUID, ns, plan, isDeleting, isWillInterrupt);
    }
  }

  static void runDomainPlan(
      Domain dom,
      String domainUID,
      String ns,
      Step.StepAndPacket plan,
      boolean isDeleting,
      boolean isWillInterrupt) {
    CompletionCallback cc =
        new CompletionCallback() {
          @Override
          public void onCompletion(Packet packet) {
            // no-op
          }

          @Override
          public void onThrowable(Packet packet, Throwable throwable) {
            LOGGER.severe(MessageKeys.EXCEPTION, throwable);

            FIBER_GATE.startFiberIfLastFiberMatches(
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

            FIBER_GATE
                .getExecutor()
                .schedule(
                    () -> {
                      DomainPresenceInfo existing = DomainPresenceInfoManager.lookup(ns, domainUID);
                      if (existing != null) {
                        internalMakeRightDomainPresence(
                            existing, dom, domainUID, existing.getNamespace(), isDeleting, false);
                      }
                    },
                    DomainPresence.getDomainPresenceFailureRetrySeconds(),
                    TimeUnit.SECONDS);
          }
        };

    if (isWillInterrupt) {
      FIBER_GATE.startFiber(domainUID, plan.step, plan.packet, cc);
    } else {
      FIBER_GATE.startFiberIfNoCurrentFiber(domainUID, plan.step, plan.packet, cc);
    }
  }

  static Step.StepAndPacket createDomainUpPlan(Domain dom, String ns) {
    Step managedServerStrategy =
        bringManagedServersUp(DomainStatusUpdater.createEndProgressingStep(new TailStep()));
    Step adminServerStrategy = bringAdminServerUp(dom, managedServerStrategy);

    Step strategy =
        new UpHeadStep(
            dom,
            ns,
            DomainStatusUpdater.createProgressingStep(
                DomainStatusUpdater.INSPECTING_DOMAIN_PROGRESS_REASON,
                true,
                DomainPresenceStep.createDomainPresenceStep(
                    dom, adminServerStrategy, managedServerStrategy)));

    Packet p = new Packet();

    return new Step.StepAndPacket(strategy, p);
  }

  static Step.StepAndPacket createDomainDownPlan(
      DomainPresenceInfo info, String ns, String domainUID) {
    Step deleteStep = new DeleteDomainStep(info, ns, domainUID);
    Step strategy = new DownHeadStep(info, ns, deleteStep);

    Packet p = new Packet();

    return new Step.StepAndPacket(strategy, p);
  }

  private static class UpHeadStep extends Step {
    private final Domain dom;
    private final String ns;

    public UpHeadStep(Domain dom, String ns, Step next) {
      super(next);
      this.dom = dom;
      this.ns = ns;
    }

    @Override
    public NextAction apply(Packet packet) {
      PodWatcher pw = Main.podWatchers.get(ns);
      DomainPresenceInfo info = DomainPresenceInfoManager.getOrCreate(dom);
      info.setDeleting(false);
      info.setDomain(dom);
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
  private static Step bringAdminServerUp(Domain dom, Step next) {
    return StorageHelper.insertStorageSteps(
        dom,
        Step.chain(
            new ListPersistentVolumeClaimStep(null),
            PodHelper.createAdminPodStep(null),
            new BeforeAdminServiceStep(null),
            ServiceHelper.createForServerStep(null),
            new WatchPodReadyAdminStep(Main.podWatchers, null),
            WlsRetriever.readConfigStep(null),
            new ExternalAdminChannelsStep(next)));
  }

  private static Step bringManagedServersUp(Step next) {
    return new ManagedServersUpStep(next);
  }
}
