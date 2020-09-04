// Copyright (c) 2017, 2020, Oracle Corporation and/or its affiliates.
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

import oracle.kubernetes.operator.DomainStatusUpdater;
import oracle.kubernetes.operator.ProcessingConstants;
import oracle.kubernetes.operator.helpers.DomainPresenceInfo;
import oracle.kubernetes.operator.helpers.DomainPresenceInfo.ServerStartupInfo;
import oracle.kubernetes.operator.helpers.PodHelper;
import oracle.kubernetes.operator.helpers.ServiceHelper;
import oracle.kubernetes.operator.logging.LoggingFacade;
import oracle.kubernetes.operator.logging.LoggingFactory;
import oracle.kubernetes.operator.work.NextAction;
import oracle.kubernetes.operator.work.Packet;
import oracle.kubernetes.operator.work.Step;
import oracle.kubernetes.weblogic.domain.model.Domain;

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

  private final Collection<ServerStartupInfo> startupInfos;

  public ManagedServerUpIteratorStep(Collection<ServerStartupInfo> startupInfos, Step next) {
    super(next);
    this.startupInfos = startupInfos;
  }

  @Override
  protected String getDetail() {
    List<String> serversToStart = new ArrayList<>();
    for (ServerStartupInfo ssi : startupInfos) {
      serversToStart.add(ssi.getName());
    }
    return String.join(",", serversToStart);
  }

  @Override
  public NextAction apply(Packet packet) {
    if (startupInfos.isEmpty()) {
      return doNext(packet);
    }

    if (LOGGER.isFineEnabled()) {
      LOGGER.fine(String.format("Starting or validating servers for domain with UID: %s, server list: %s",
          getDomainUid(packet), getServerNames(startupInfos)));
    }

    packet.put(ProcessingConstants.SERVERS_TO_ROLL, new ConcurrentHashMap<String, StepAndPacket>());
    Collection<StepAndPacket> startDetails =
        startupInfos.stream()
            .filter(ssi -> !isServerInCluster(ssi))
            .map(ssi -> createManagedServerUpDetails(packet, ssi)).collect(Collectors.toList());

    Collection<StepAndPacket> work = new ArrayList<>();
    if (!startDetails.isEmpty()) {
      work.add(
              new StepAndPacket(
                      new StartManagedServersStep(null, 0, startDetails, null), packet));
    }

    for (Map.Entry<String, StartClusteredServersStepFactory> entry
            : getStartClusteredServersStepFactories(startupInfos, packet).entrySet()) {
      work.add(
              new StepAndPacket(
                      new StartManagedServersStep(entry.getKey(), entry.getValue().getMaxConcurrency(),
                              entry.getValue().getServerStartsStepAndPackets(), null), packet.clone()));
    }

    if (!work.isEmpty()) {
      return doForkJoin(DomainStatusUpdater.createStatusUpdateStep(getNext()), packet, work);
    }

    return doNext(DomainStatusUpdater.createStatusUpdateStep(getNext()), packet);
  }


  private String getDomainUid(Packet packet) {
    return packet.getSpi(DomainPresenceInfo.class).getDomain().getDomainUid();
  }

  private List<String> getServerNames(Collection<ServerStartupInfo> startupInfos) {
    return startupInfos.stream().map(ServerStartupInfo::getName).collect(Collectors.toList());
  }

  private StepAndPacket createManagedServerUpDetails(Packet packet, ServerStartupInfo ssi) {
    return new StepAndPacket(ServiceHelper.createForServerStep(PodHelper.createManagedPodStep(null)),
            createPacketForServer(packet, ssi));
  }

  private Packet createPacketForServer(Packet packet, ServerStartupInfo ssi) {
    Packet p = packet.clone();
    p.put(ProcessingConstants.CLUSTER_NAME, ssi.getClusterName());
    p.put(ProcessingConstants.SERVER_NAME, ssi.getName());
    p.put(ProcessingConstants.SERVER_SCAN, ssi.serverConfig);
    p.put(ProcessingConstants.ENVVARS, ssi.getEnvironment());
    return p;
  }

  private Map<String, StartClusteredServersStepFactory> getStartClusteredServersStepFactories(
      Collection<ServerStartupInfo> startupInfos,
      Packet packet) {
    DomainPresenceInfo info = packet.getSpi(DomainPresenceInfo.class);
    Domain domain = info.getDomain();

    Map<String, StartClusteredServersStepFactory> factories = new HashMap<>();
    startupInfos.stream()
        .filter(this::isServerInCluster)
        .forEach(ssi ->
            factories.computeIfAbsent(ssi.getClusterName(),
                k -> new StartClusteredServersStepFactory(getMaxConcurrentStartup(domain, ssi)))
                .add(createManagedServerUpDetails(packet, ssi)));

    return factories;
  }

  private boolean isServerInCluster(ServerStartupInfo ssi) {
    return ssi.getClusterName() != null;
  }

  static class StartManagedServersStep extends Step {
    final Collection<StepAndPacket> startDetails;
    final Queue<StepAndPacket> startDetailsQueue = new ConcurrentLinkedQueue<>();
    final String clusterName;
    final int maxConcurrency;
    final AtomicInteger numStarted = new AtomicInteger(0);

    StartManagedServersStep(String clusterName, int maxConcurrency, Collection<StepAndPacket> startDetails, Step next) {
      super(next);
      this.clusterName = clusterName;
      this.startDetails = startDetails;
      this.maxConcurrency = maxConcurrency;
      startDetails.forEach(this::add);
    }

    void add(StepAndPacket serverToStart) {
      startDetailsQueue.add(new StepAndPacket(serverToStart.step, serverToStart.packet));
    }

    @Override
    public NextAction apply(Packet packet) {

      if (startDetailsQueue.isEmpty()) {
        return doNext(new ManagedServerUpAfterStep(getNext()), packet);
      } else if (serverAvailableToStart(packet.getSpi(DomainPresenceInfo.class))) {
        numStarted.getAndIncrement();
        return doForkJoin(this, packet, Collections.singletonList(startDetailsQueue.poll()));
      } else {
        return doDelay(this, packet, 100, TimeUnit.MILLISECONDS);
      }
    }

    private boolean serverAvailableToStart(DomainPresenceInfo info) {
      return ((numStarted.get() < PodHelper.getScheduledPods(info, clusterName).size())
              && (canStartConcurrently(PodHelper.getReadyPods(info, clusterName).size())));
    }

    private boolean canStartConcurrently(int numReady) {
      return ((this.maxConcurrency > 0) && (numStarted.get() < (this.maxConcurrency + numReady - 1)))
          || (this.maxConcurrency == 0);
    }
  }

  private int getMaxConcurrentStartup(Domain domain, ServerStartupInfo ssi) {
    return domain.getMaxConcurrentStartup(ssi.getClusterName());
  }

  private static class StartClusteredServersStepFactory {

    private final Queue<StepAndPacket> serversToStart = new ConcurrentLinkedQueue<>();
    private final int maxConcurrency;

    StartClusteredServersStepFactory(int maxConcurrency) {
      this.maxConcurrency = maxConcurrency;
    }

    public int getMaxConcurrency() {
      return this.maxConcurrency;
    }

    void add(StepAndPacket serverToStart) {
      serversToStart.add(serverToStart);
    }

    Collection<StepAndPacket> getServerStartsStepAndPackets() {
      return serversToStart;
    }
  }

}
