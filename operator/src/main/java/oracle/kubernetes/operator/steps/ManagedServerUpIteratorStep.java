// Copyright (c) 2017, 2026, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.steps;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import javax.annotation.Nonnull;

import io.kubernetes.client.extended.controller.reconciler.Result;
import io.kubernetes.client.openapi.models.V1Pod;
import oracle.kubernetes.operator.DomainStatusUpdater;
import oracle.kubernetes.operator.ProcessingConstants;
import oracle.kubernetes.operator.helpers.DomainPresenceInfo;
import oracle.kubernetes.operator.helpers.DomainPresenceInfo.ServerStartupInfo;
import oracle.kubernetes.operator.helpers.PodHelper;
import oracle.kubernetes.operator.helpers.ServiceHelper;
import oracle.kubernetes.operator.logging.LoggingFacade;
import oracle.kubernetes.operator.logging.LoggingFactory;
import oracle.kubernetes.operator.wlsconfig.WlsDomainConfig;
import oracle.kubernetes.operator.work.Fiber;
import oracle.kubernetes.operator.work.Packet;
import oracle.kubernetes.operator.work.Step;

import static oracle.kubernetes.operator.ProcessingConstants.DOMAIN_TOPOLOGY;
import static oracle.kubernetes.operator.ProcessingConstants.SERVER_NAME;

/**
 * A step which will bring up the specified managed servers in parallel.
 * Adds to packet:
 *    SERVERS_TO_ROLL    a collection of servers to be rolled, updated in parallel by the server-up steps.
 * and for each server:
 *    SERVER_NAME        the name of the server to bring up
 *    CLUSTER_NAME       the name of the cluster of which it is a member, if any
 *    ENVVARS            the associated environment variables
 *    SERVER_SCAN        the configuration for the server
 */
public class ManagedServerUpIteratorStep extends Step {
  private static final LoggingFacade LOGGER = LoggingFactory.getLogger("Operator", "Operator");

  /** The interval in msec that the operator will wait to ensure that started pods have been scheduled on a node. */
  static final int SCHEDULING_DETECTION_DELAY = 100;

  private final Collection<ServerStartupInfo> startupInfos;

  ManagedServerUpIteratorStep(Collection<ServerStartupInfo> startupInfos, Step next) {
    super(next);
    this.startupInfos = startupInfos;
  }

  @Override
  protected String getDetail() {
    return startupInfos.stream().map(ServerStartupInfo::getName).collect(Collectors.joining(","));
  }

  @Override
  public @Nonnull Result apply(Packet packet) {
    if (startupInfos.isEmpty()) {
      return doNext(packet);
    }

    if (LOGGER.isFineEnabled()) {
      LOGGER.fine(String.format("Starting or validating servers for domain with UID: %s, server list: %s",
          getDomainUid(packet), getServerNames(startupInfos)));
    }

    initialServersToRoll(packet);
    Collection<Fiber.StepAndPacket> startDetails =
        startupInfos.stream()
            .filter(ssi -> !isServerInCluster(ssi))
            .map(ssi -> createManagedServerUpDetails(packet, ssi)).toList();

    Collection<Fiber.StepAndPacket> work = new ArrayList<>();
    if (!startDetails.isEmpty()) {
      work.add(
              new Fiber.StepAndPacket(
                      new StartManagedServersStep(null, 0, startDetails, null), packet));
    }

    for (Map.Entry<String, StartClusteredServersStepFactory> entry
            : getStartClusteredServersStepFactories(startupInfos, packet).entrySet()) {
      work.add(
              new Fiber.StepAndPacket(
                      new StartManagedServersStep(entry.getKey(), entry.getValue().getMaxConcurrency(),
                              entry.getValue().getServerStartsStepAndPackets(), null), packet.copy()));
    }

    Collection<Fiber.StepAndPacket> startupWaiters =
            startupInfos.stream()
                    .map(ssi -> createManagedServerUpWaiters(packet, ssi)).toList();
    work.addAll(startupWaiters);

    if (!work.isEmpty()) {
      return doForkJoin(DomainStatusUpdater.createStatusUpdateStep(
              new ManagedServerUpAfterStep(getNext())), packet, work);
    }

    return doNext(DomainStatusUpdater.createStatusUpdateStep(new ManagedServerUpAfterStep(getNext())), packet);
  }

  // Adds an empty map to both the packet and the domain presence info to track servers that need to be rolled
  private void initialServersToRoll(Packet packet) {
    final Map<String, Fiber.StepAndPacket> serversToRoll = new ConcurrentHashMap<>();
    packet.put(ProcessingConstants.SERVERS_TO_ROLL, serversToRoll);
    DomainPresenceInfo.fromPacket(packet).ifPresent(dpi -> dpi.setServersToRoll(serversToRoll));
  }


  private String getDomainUid(Packet packet) {
    DomainPresenceInfo info = (DomainPresenceInfo) packet.get(ProcessingConstants.DOMAIN_PRESENCE_INFO);
    return info.getDomain().getDomainUid();
  }

  private List<String> getServerNames(Collection<ServerStartupInfo> startupInfos) {
    return startupInfos.stream().map(ServerStartupInfo::getName).toList();
  }

  private Fiber.StepAndPacket createManagedServerUpDetails(Packet packet, ServerStartupInfo ssi) {
    return new Fiber.StepAndPacket(ServiceHelper.createForServerStep(PodHelper.createManagedPodStep(null)),
            createPacketForServer(packet, ssi));
  }

  private Fiber.StepAndPacket createManagedServerUpWaiters(Packet packet, ServerStartupInfo ssi) {
    return new Fiber.StepAndPacket(new ManagedPodReadyStep(ssi.getServerName(), null),
            createPacketForServer(packet, ssi));
  }

  static class ManagedPodReadyStep extends Step {
    private final String serverName;

    ManagedPodReadyStep(String serverName, Step next) {
      super(next);
      this.serverName = serverName;
    }

    @Override
    public @Nonnull Result apply(Packet packet) {
      DomainPresenceInfo info = (DomainPresenceInfo) packet.get(ProcessingConstants.DOMAIN_PRESENCE_INFO);
      V1Pod managedPod = info.getServerPod(serverName);
      boolean isWaitingToRoll = PodHelper.isWaitingToRoll(managedPod);
      if (PodHelper.isSchedulingGated(managedPod)) {
        LOGGER.fine(
            "WKO-POD-STARTUP-TRACE component=ready-wait phase=decision decision=continue-scheduling-gated "
                + "domainUid={0} namespace={1} server={2} pod={3} node={4} ready={5} dpi={6} fiber={7}",
            info.getDomainUid(),
            info.getNamespace(),
            serverName,
            getPodName(managedPod),
            getNodeName(managedPod),
            PodHelper.isReady(managedPod),
            getIdentity(info),
            packet.getFiber());
        return doNext(packet);
      }
      if (managedPod == null || (!isPodReady(managedPod) && !isPodMarkedForShutdown(managedPod)
              && !isWaitingToRoll)) {
        if (info.hasRetryableFailure()) {
          LOGGER.fine(
              "WKO-POD-STARTUP-TRACE component=ready-wait phase=decision decision=continue-retryable-failure "
                  + "domainUid={0} namespace={1} server={2} pod={3} node={4} ready={5} dpi={6} fiber={7}",
              info.getDomainUid(),
              info.getNamespace(),
              serverName,
              getPodName(managedPod),
              getNodeName(managedPod),
              PodHelper.isReady(managedPod),
              getIdentity(info),
              packet.getFiber());
          return doNext(packet);
        }
        // requeue to wait for managed pod to be ready
        LOGGER.fine(
            "WKO-POD-STARTUP-TRACE component=ready-wait phase=decision decision=requeue "
                + "domainUid={0} namespace={1} server={2} pod={3} node={4} ready={5} "
                + "deletingOrShutdown={6} waitingToRoll={7} dpi={8} fiber={9}",
            info.getDomainUid(),
            info.getNamespace(),
            serverName,
            getPodName(managedPod),
            getNodeName(managedPod),
            PodHelper.isReady(managedPod),
            managedPod != null && isPodMarkedForShutdown(managedPod),
            isWaitingToRoll,
            getIdentity(info),
            packet.getFiber());
        return doRequeue();
      }

      LOGGER.fine(
          "WKO-POD-STARTUP-TRACE component=ready-wait phase=decision decision=continue "
              + "domainUid={0} namespace={1} server={2} pod={3} node={4} ready={5} "
              + "deletingOrShutdown={6} waitingToRoll={7} dpi={8} fiber={9}",
          info.getDomainUid(),
          info.getNamespace(),
          serverName,
          getPodName(managedPod),
          getNodeName(managedPod),
          PodHelper.isReady(managedPod),
          isPodMarkedForShutdown(managedPod),
          isWaitingToRoll,
          getIdentity(info),
          packet.getFiber());
      return doNext(packet);
    }

    private String getPodName(V1Pod pod) {
      return pod == null ? null : PodHelper.getPodName(pod);
    }

    private String getNodeName(V1Pod pod) {
      return pod == null || pod.getSpec() == null ? null : pod.getSpec().getNodeName();
    }

    private String getIdentity(Object object) {
      return Integer.toHexString(System.identityHashCode(object));
    }

    protected boolean isPodReady(V1Pod result) {
      return PodHelper.isReady(result);
    }

    protected boolean isPodMarkedForShutdown(V1Pod result) {
      return PodHelper.isDeleting(result) || PodHelper.isPodAlreadyAnnotatedForShutdown(result);
    }
  }

  private Packet createPacketForServer(Packet packet, ServerStartupInfo ssi) {
    return ssi.createPacket(packet);
  }

  private Map<String, StartClusteredServersStepFactory> getStartClusteredServersStepFactories(
      Collection<ServerStartupInfo> startupInfos,
      Packet packet) {
    DomainPresenceInfo info = (DomainPresenceInfo) packet.get(ProcessingConstants.DOMAIN_PRESENCE_INFO);

    Map<String, StartClusteredServersStepFactory> factories = new HashMap<>();
    startupInfos.stream()
        .filter(this::isServerInCluster)
        .forEach(ssi ->
            factories.computeIfAbsent(ssi.getClusterName(),
                k -> new StartClusteredServersStepFactory(getMaxConcurrentStartup(info, ssi)))
                .add(createManagedServerUpDetails(packet, ssi)));

    return factories;
  }

  private boolean isServerInCluster(ServerStartupInfo ssi) {
    return ssi.getClusterName() != null;
  }

  static class StartManagedServersStep extends Step {
    private static final long TRACE_HEARTBEAT_NANOS = TimeUnit.SECONDS.toNanos(10);

    final Queue<Fiber.StepAndPacket> startDetailsQueue = new ConcurrentLinkedQueue<>();
    final String clusterName;
    final int maxConcurrency;
    final AtomicInteger numStarted = new AtomicInteger(0);
    private String lastTraceState;
    private long lastTraceNanos;
    private long traceStateStartNanos;

    StartManagedServersStep(String clusterName, int maxConcurrency,
                            Collection<Fiber.StepAndPacket> startDetails, Step next) {
      super(next);
      this.clusterName = clusterName;
      this.maxConcurrency = maxConcurrency;
      startDetails.forEach(this::add);
    }

    void add(Fiber.StepAndPacket serverToStart) {
      startDetailsQueue.add(new Fiber.StepAndPacket(serverToStart.step(), serverToStart.packet()));
    }

    @Override
    public @Nonnull Result apply(Packet packet) {
      if (startDetailsQueue.isEmpty()) {
        traceGateDecision(packet, "complete", null, true);
        return doNext(packet);
      } else if (hasServerAvailableToStart(packet)) {
        traceGateDecision(packet, "start", getNextServerName(), true);
        numStarted.getAndIncrement();
        return doForkJoin(this, packet, Collections.singletonList(startDetailsQueue.poll()));
      } else if (hasSchedulingGatedManagedServer(packet)) {
        traceGateDecision(packet, "complete-scheduling-gated", getNextServerName(), true);
        return doNext(packet);
      } else {
        traceGateWait(packet);
        return doDelay(this, packet, SCHEDULING_DETECTION_DELAY, TimeUnit.MILLISECONDS);
      }
    }

    private boolean hasServerAvailableToStart(Packet packet) {
      DomainPresenceInfo info = (DomainPresenceInfo) packet.get(ProcessingConstants.DOMAIN_PRESENCE_INFO);
      String adminServerName = ((WlsDomainConfig) packet.get(DOMAIN_TOPOLOGY)).getAdminServerName();
      return (getNumServersStarted() <= getNumStartedOrSchedulingGatedManagedServers(info, adminServerName)
              && (canStartConcurrently(info.getNumReadyManagedServers(clusterName, adminServerName))));
    }

    private long getNumStartedOrSchedulingGatedManagedServers(DomainPresenceInfo info, String adminServerName) {
      return info.getNumScheduledManagedServers(clusterName, adminServerName)
          + info.getNumSchedulingGatedManagedServers(clusterName, adminServerName);
    }

    private boolean hasSchedulingGatedManagedServer(Packet packet) {
      DomainPresenceInfo info = (DomainPresenceInfo) packet.get(ProcessingConstants.DOMAIN_PRESENCE_INFO);
      String adminServerName = ((WlsDomainConfig) packet.get(DOMAIN_TOPOLOGY)).getAdminServerName();
      return info.getNumSchedulingGatedManagedServers(clusterName, adminServerName) > 0;
    }

    private StartupGateState getStartupGateState(Packet packet) {
      DomainPresenceInfo info = (DomainPresenceInfo) packet.get(ProcessingConstants.DOMAIN_PRESENCE_INFO);
      String adminServerName = ((WlsDomainConfig) packet.get(DOMAIN_TOPOLOGY)).getAdminServerName();
      return new StartupGateState(
          info,
          getNumServersStarted(),
          info.getNumScheduledManagedServers(clusterName, adminServerName),
          info.getNumSchedulingGatedManagedServers(clusterName, adminServerName),
          info.getNumReadyManagedServers(clusterName, adminServerName));
    }

    private String getNextServerName() {
      return java.util.Optional.ofNullable(startDetailsQueue.peek())
          .map(Fiber.StepAndPacket::packet)
          .map(p -> (String) p.get(SERVER_NAME))
          .orElse(null);
    }

    private void traceGateWait(Packet packet) {
      if (!LOGGER.isFineEnabled()) {
        return;
      }
      StartupGateState state = getStartupGateState(packet);
      traceGateDecision(
          packet,
          state,
          state.isWaitingForScheduling() ? "wait-scheduled" : "wait-ready",
          getNextServerName(),
          false);
    }

    private void traceGateDecision(Packet packet, String decision, String nextServer, boolean force) {
      if (!LOGGER.isFineEnabled()) {
        return;
      }
      traceGateDecision(packet, getStartupGateState(packet), decision, nextServer, force);
    }

    private void traceGateDecision(
        Packet packet, StartupGateState state, String decision, String nextServer, boolean force) {
      long now = System.nanoTime();
      String traceState = decision + ":" + state.numStarted() + ":" + state.numScheduled() + ":"
          + state.numSchedulingGated() + ":" + state.numReady() + ":" + startDetailsQueue.size();
      boolean stateChanged = !traceState.equals(lastTraceState);
      if (stateChanged) {
        lastTraceState = traceState;
        traceStateStartNanos = now;
      }
      if (!force && !stateChanged && now - lastTraceNanos < TRACE_HEARTBEAT_NANOS) {
        return;
      }

      lastTraceNanos = now;
      LOGGER.fine(
          "WKO-POD-STARTUP-TRACE component=start-gate phase=decision decision={0} "
              + "domainUid={1} namespace={2} cluster={3} nextServer={4} dpi={5} fiber={6} "
              + "numStarted={7} numScheduled={8} numSchedulingGated={9} numReady={10} "
              + "numNotReady={11} maxConcurrentStartup={12} queueRemaining={13} stateAgeMs={14}",
          decision,
          state.info().getDomainUid(),
          state.info().getNamespace(),
          clusterName,
          nextServer,
          Integer.toHexString(System.identityHashCode(state.info())),
          packet.getFiber(),
          state.numStarted(),
          state.numScheduled(),
          state.numSchedulingGated(),
          state.numReady(),
          state.numStarted() - state.numReady(),
          maxConcurrency,
          startDetailsQueue.size(),
          TimeUnit.NANOSECONDS.toMillis(now - traceStateStartNanos));
    }

    private int getNumServersStarted() {
      return numStarted.get();
    }

    private boolean canStartConcurrently(long numReady) {
      return (ignoreConcurrencyLimits() || numNotReady(numReady) < this.maxConcurrency);
    }

    private long numNotReady(long numReady) {
      return getNumServersStarted() - numReady;
    }

    private boolean ignoreConcurrencyLimits() {
      return this.maxConcurrency == 0;
    }

    private record StartupGateState(
        DomainPresenceInfo info,
        int numStarted,
        long numScheduled,
        long numSchedulingGated,
        long numReady) {

      private boolean isWaitingForScheduling() {
        return numStarted > numScheduled + numSchedulingGated;
      }
    }
  }

  private int getMaxConcurrentStartup(DomainPresenceInfo info, ServerStartupInfo ssi) {
    return info.getMaxConcurrentStartup(ssi.getClusterName());
  }

  private static class StartClusteredServersStepFactory {

    private final Queue<Fiber.StepAndPacket> serversToStart = new ConcurrentLinkedQueue<>();
    private final int maxConcurrency;

    StartClusteredServersStepFactory(int maxConcurrency) {
      this.maxConcurrency = maxConcurrency;
    }

    int getMaxConcurrency() {
      return this.maxConcurrency;
    }

    void add(Fiber.StepAndPacket serverToStart) {
      serversToStart.add(serverToStart);
    }

    Collection<Fiber.StepAndPacket> getServerStartsStepAndPackets() {
      return serversToStart;
    }
  }

}
