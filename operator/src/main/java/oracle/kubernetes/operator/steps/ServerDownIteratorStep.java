// Copyright (c) 2017, 2021, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.steps;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import oracle.kubernetes.operator.ProcessingConstants;
import oracle.kubernetes.operator.helpers.DomainPresenceInfo;
import oracle.kubernetes.operator.helpers.ServiceHelper;
import oracle.kubernetes.operator.work.NextAction;
import oracle.kubernetes.operator.work.Packet;
import oracle.kubernetes.operator.work.Step;
import oracle.kubernetes.weblogic.domain.model.Domain;

public class ServerDownIteratorStep extends Step {
  private final Collection<DomainPresenceInfo.ServerShutdownInfo> serverShutdownInfos;

  ServerDownIteratorStep(List<DomainPresenceInfo.ServerShutdownInfo> serverShutdownInfos, Step next) {
    super(next);
    Collections.reverse(serverShutdownInfos);
    this.serverShutdownInfos = serverShutdownInfos;
  }

  List<String> getServersToStop() {
    List<String> serverNames = new ArrayList<>();
    serverShutdownInfos.forEach(s -> serverNames.add(s.getServerName()));
    return serverNames;
  }

  Collection<DomainPresenceInfo.ServerShutdownInfo> getServerShutdownInfos() {
    return serverShutdownInfos;
  }

  @Override
  public NextAction apply(Packet packet) {
    DomainPresenceInfo info = packet.getSpi(DomainPresenceInfo.class);
    List<StepAndPacket> shutdownDetails =
            getServerShutdownInfos().stream()
                    .filter(ssi -> !isServerInCluster(ssi))
                    .map(ssi -> createManagedServerDownDetails(packet, ssi)).collect(Collectors.toList());

    getShutdownClusteredServersStepFactories(getServerShutdownInfos(), packet).values()
            .forEach(factory -> shutdownDetails.addAll(factory.getServerShutdownStepAndPackets(info)));

    return doNext((new ShutdownManagedServersStep(shutdownDetails, getNext())), packet);

  }

  // pre-conditions: DomainPresenceInfo SPI
  // "principal"
  // "serverScan"
  // "clusterScan"
  // "envVars"
  private static Step createServiceStep(DomainPresenceInfo.ServerShutdownInfo ssi) {
    return ServiceHelper.createForServerStep(
            true, new ServerDownStep(ssi.getServerName(), true, null));
  }

  private boolean isServerInCluster(DomainPresenceInfo.ServerShutdownInfo ssi) {
    return ssi.getClusterName() != null;
  }

  private int getMaxConcurrentShutdown(Domain domain, DomainPresenceInfo.ServerShutdownInfo ssi) {
    return domain.getMaxConcurrentShutdown(ssi.getClusterName());
  }

  private int getReplicaCount(Domain domain, DomainPresenceInfo.ServerShutdownInfo ssi) {
    return domain.getReplicaCount(ssi.getClusterName());
  }

  private StepAndPacket createManagedServerDownDetails(Packet packet, DomainPresenceInfo.ServerShutdownInfo ssi) {
    if (ssi.isServiceOnly()) {
      return new StepAndPacket(createServiceStep(ssi),
              createPacketForServer(packet, ssi));
    } else {
      return new StepAndPacket(new ServerDownStep(ssi.getName(), null), packet.copy());
    }
  }

  private Packet createPacketForServer(Packet packet, DomainPresenceInfo.ServerShutdownInfo ssi) {
    Packet p = packet.copy();
    p.put(ProcessingConstants.CLUSTER_NAME, ssi.getClusterName());
    p.put(ProcessingConstants.SERVER_NAME, ssi.getName());
    p.put(ProcessingConstants.SERVER_SCAN, ssi.serverConfig);
    p.put(ProcessingConstants.ENVVARS, ssi.getEnvironment());
    return p;
  }

  private Map<String, ShutdownClusteredServersStepFactory> getShutdownClusteredServersStepFactories(
          Collection<DomainPresenceInfo.ServerShutdownInfo> shutdownInfos, Packet packet) {
    DomainPresenceInfo info = packet.getSpi(DomainPresenceInfo.class);
    Domain domain = info.getDomain();

    Map<String, ShutdownClusteredServersStepFactory> factories = new HashMap<>();
    shutdownInfos.stream()
            .filter(this::isServerInCluster)
            .forEach(ssi ->
                    factories.computeIfAbsent(ssi.getClusterName(),
                        k -> new ShutdownClusteredServersStepFactory(getMaxConcurrentShutdown(domain, ssi),
                                getReplicaCount(domain, ssi)))
                        .add(createManagedServerDownDetails(packet, ssi)));
    return factories;
  }

  private static class ShutdownClusteredServersStepFactory {

    private final Queue<StepAndPacket> serversToShutdown = new ConcurrentLinkedQueue<>();
    private final int maxConcurrency;
    private final int replicaCount;

    ShutdownClusteredServersStepFactory(int maxConcurrency, int replicaCount) {
      this.maxConcurrency = maxConcurrency;
      this.replicaCount = replicaCount;
    }

    void add(StepAndPacket serverToShutdown) {
      serversToShutdown.add(serverToShutdown);
    }

    Collection<StepAndPacket> getServerShutdownStepAndPackets(DomainPresenceInfo info) {
      if ((maxConcurrency == 0) || (replicaCount == 0) || info.getDomain().isShuttingDown()) {
        return serversToShutdown;
      }
      ArrayList<StepAndPacket> steps = new ArrayList<>(maxConcurrency);
      IntStream.range(0, maxConcurrency)
              .forEach(i -> steps.add(ShutdownClusteredServersStep.createStepAndPacket(serversToShutdown)));
      return steps;
    }
  }

  static class ShutdownManagedServersStep extends Step {
    final Collection<StepAndPacket> shutdownDetails;

    ShutdownManagedServersStep(Collection<StepAndPacket> shutdownDetails, Step next) {
      super(next);
      this.shutdownDetails = shutdownDetails;
    }

    @Override
    public NextAction apply(Packet packet) {
      if (shutdownDetails.isEmpty()) {
        return doNext(getNext(), packet);
      } else {
        return doForkJoin(getNext(), packet, shutdownDetails);
      }
    }
  }

  static class ShutdownClusteredServersStep extends Step {

    private final Queue<StepAndPacket> serversToShutdown;

    static StepAndPacket createStepAndPacket(Queue<StepAndPacket> serversToShutdown) {
      return new StepAndPacket(new ShutdownClusteredServersStep(serversToShutdown), null);
    }

    ShutdownClusteredServersStep(Queue<StepAndPacket> serversToShutdown) {
      super(null);
      this.serversToShutdown = serversToShutdown;
    }

    @Override
    public NextAction apply(Packet packet) {

      if (serversToShutdown.isEmpty()) {
        return doNext(packet);
      } else {
        Collection<StepAndPacket> servers = Collections.singletonList(serversToShutdown.poll());
        return doForkJoin(this, packet, servers);
      }
    }
  }
}