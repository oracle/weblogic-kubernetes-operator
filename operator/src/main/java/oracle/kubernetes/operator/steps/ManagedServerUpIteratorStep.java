// Copyright (c) 2017, 2020, Oracle Corporation and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.steps;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Queue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.TimeUnit;
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

  private static NextStepFactory NEXT_STEP_FACTORY =
      (next, packet, servers) -> StartClusteredServersStep.createStartClusteredServersStep(next, packet, servers);

  public ManagedServerUpIteratorStep(Collection<ServerStartupInfo> startupInfos, Step next) {
    super(next);
    this.startupInfos = startupInfos;
  }

  // pre-conditions: DomainPresenceInfo SPI
  // "principal"
  // "serverScan"
  // "clusterScan"
  // "envVars"
  private static Step bringManagedServerUp(ServerStartupInfo ssi) {
    return ssi.isServiceOnly()
        ? ServiceHelper.createForServerStep(
            true, new ServerDownStep(ssi.getServerName(), true, null))
        : ServiceHelper.createForServerStep(PodHelper.createManagedPodStep(null));
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

    Map<String, StartClusteredServersStepFactory> stepFactories =
            getStartClusteredServersStepFactories(startupInfos, packet);
    Collection<StepAndPacket> work = new ArrayList<>();
    if (!startDetails.isEmpty()) {
      work.add(
              new StepAndPacket(
                      new StartManagedServersStep(null, startDetails, null), packet));
    }
    for (Map.Entry<String, StartClusteredServersStepFactory> entry : stepFactories.entrySet()) {
      work.add(
              new StepAndPacket(
                      new StartManagedServersStep(entry.getKey(), entry.getValue().getServerStartsStepAndPackets(),
                              null), packet));
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
    return new StepAndPacket(bringManagedServerUp(ssi), createPacketForServer(packet, ssi));
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
    final Queue<StepAndPacket> startDetailsQueue = new ConcurrentLinkedQueue<StepAndPacket>();
    final String clusterName;
    int countScheduled = 0;
    int maxConcurrency = 1;

    StartManagedServersStep(String clusterName, Collection<StepAndPacket> startDetails, Step next) {
      super(next);
      this.clusterName = clusterName;
      this.startDetails = startDetails;
      startDetails.forEach(sp -> add(sp));
    }

    void add(StepAndPacket serverToStart) {
      startDetailsQueue.add(new StepAndPacket(serverToStart.step, serverToStart.packet));
      this.maxConcurrency = Optional.ofNullable(
              (Integer) serverToStart.packet.get(ProcessingConstants.MAX_CONCURRENCY)).orElse(0);
    }

    String getClusterName() {
      return clusterName;
    }

    Collection<StepAndPacket> getStartDetails() {
      return startDetailsQueue;
    }

    Collection<StepAndPacket> getServersToStart() {
      return startDetailsQueue;
    }

    @Override
    public NextAction apply(Packet packet) {
      DomainPresenceInfo info = packet.getSpi(DomainPresenceInfo.class);
      if (this.countScheduled < PodHelper.getScheduledPods(info).size()) {
        if (canScheduleConcurrently(PodHelper.getReadyPods(info).size())) {
          Collection<StepAndPacket> servers = Arrays.asList(startDetailsQueue.poll());
          this.countScheduled++;
          return doNext(NEXT_STEP_FACTORY.startClusteredServersStep(this, packet, servers), packet);
        }
      }
      if (startDetailsQueue.size() > 0) {
        return doDelay(this, packet, 100, TimeUnit.MILLISECONDS);
      } else {
        return doNext(new ManagedServerUpAfterStep(getNext()), packet);
      }
    }

    private boolean canScheduleConcurrently(int readyManagedPods) {
      return ((maxConcurrency > 0) && (this.countScheduled < (maxConcurrency + readyManagedPods - 1)))
          || (maxConcurrency == 0);
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

    void add(StepAndPacket serverToStart) {
      serverToStart.packet.put(ProcessingConstants.MAX_CONCURRENCY, maxConcurrency);
      serversToStart.add(serverToStart);
    }

    Collection<StepAndPacket> getServerStartsStepAndPackets() {
      return serversToStart;
    }
  }

  static class StartClusteredServersStep extends Step {

    private final Collection<StepAndPacket> serversToStart;
    private final Step step;
    private final Packet packet;

    static Step createStartClusteredServersStep(Step step, Packet packet, Collection<StepAndPacket> serversToStart) {
      return new StartClusteredServersStep(step, packet, serversToStart);
    }

    StartClusteredServersStep(Step step, Packet packet, Collection<StepAndPacket> serversToStart) {
      super(null);
      this.packet = packet;
      this.step = step;
      this.serversToStart = serversToStart;
    }

    @Override
    public NextAction apply(Packet packet) {
      return doForkJoin(step, packet, serversToStart);
    }
  }


  // an interface to provide a hook for unit testing.
  interface NextStepFactory {
    Step startClusteredServersStep(Step next, Packet packet, Collection<StepAndPacket> servers);
  }

}
