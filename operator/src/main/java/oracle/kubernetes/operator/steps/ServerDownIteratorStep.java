// Copyright (c) 2017, 2025, Oracle and/or its affiliates.
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
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import io.kubernetes.client.extended.controller.reconciler.Result;
import oracle.kubernetes.operator.ProcessingConstants;
import oracle.kubernetes.operator.helpers.DomainPresenceInfo;
import oracle.kubernetes.operator.helpers.DomainPresenceInfo.ServerShutdownInfo;
import oracle.kubernetes.operator.helpers.ServiceHelper;
import oracle.kubernetes.operator.work.Fiber;
import oracle.kubernetes.operator.work.Packet;
import oracle.kubernetes.operator.work.Step;
import org.jetbrains.annotations.NotNull;

public class ServerDownIteratorStep extends Step {
  private final List<ServerShutdownInfo> serverShutdownInfos;

  ServerDownIteratorStep(List<ServerShutdownInfo> serverShutdownInfos, Step next) {
    super(next);
    this.serverShutdownInfos = serverShutdownInfos.reversed();
  }

  List<String> getServersToStop() {
    List<String> serverNames = new ArrayList<>();
    serverShutdownInfos.forEach(s -> serverNames.add(s.getServerName()));
    return serverNames;
  }

  @Override
  public @Nonnull Result apply(Packet packet) {
    return doNext(new IteratorContext(packet, serverShutdownInfos).createNextSteps(), packet);
  }

  class IteratorContext {
    private final Packet packet;
    private final List<ServerShutdownInfo> serverShutdownInfos;
    private final DomainPresenceInfo info;

    public IteratorContext(Packet packet, List<ServerShutdownInfo> serverShutdownInfos) {
      this.packet = packet;
      this.serverShutdownInfos = Collections.unmodifiableList(serverShutdownInfos);
      this.info = (DomainPresenceInfo) packet.get(ProcessingConstants.DOMAIN_PRESENCE_INFO);
    }

    private Step createNextSteps() {
      return Step.chain(createShutDownServersStep(), createWaitForServersDownStep(), getNext());
    }

    @Nonnull
    private Step createShutDownServersStep() {
      return new RunInParallelStep(createShutdownDetails());
    }

    @Nonnull
    private List<Fiber.StepAndPacket> createShutdownDetails() {
      List<Fiber.StepAndPacket> shutdownDetails =
              this.serverShutdownInfos.stream()
                      .filter(ssi -> !isServerInCluster(ssi))
                      .map(ssi -> createManagedServerDownDetails(packet, ssi)).collect(Collectors.toList());

      getShutdownClusteredServersStepFactories(serverShutdownInfos, packet).values()
              .forEach(factory -> shutdownDetails.addAll(factory.getServerShutdownStepAndPackets(info)));
      return shutdownDetails;
    }

    private boolean isServerInCluster(ServerShutdownInfo ssi) {
      return ssi.getClusterName() != null;
    }

    private Fiber.StepAndPacket createManagedServerDownDetails(Packet packet, ServerShutdownInfo ssi) {
      if (ssi.isServiceOnly()) {
        return new Fiber.StepAndPacket(createServiceStep(ssi), createPacketForServer(packet, ssi));
      } else {
        return new Fiber.StepAndPacket(new ServerDownStep(ssi.getName(), null), createPacketForServer(packet, ssi));
      }
    }

    private Packet createPacketForServer(Packet packet, ServerShutdownInfo ssi) {
      return ssi.createPacket(packet);
    }

    private Map<String, ShutdownClusteredServersStepFactory> getShutdownClusteredServersStepFactories(
        Collection<ServerShutdownInfo> shutdownInfos, Packet packet) {

      Map<String, ShutdownClusteredServersStepFactory> factories = new HashMap<>();
      shutdownInfos.stream()
              .filter(this::isServerInCluster)
              .forEach(ssi ->
                      factories.computeIfAbsent(ssi.getClusterName(),
                          k -> new ShutdownClusteredServersStepFactory(getMaxConcurrentShutdown(ssi),
                                  getReplicaCount(ssi)))
                          .add(createManagedServerDownDetails(packet, ssi)));
      return factories;
    }

    private int getMaxConcurrentShutdown(ServerShutdownInfo ssi) {
      return info.getMaxConcurrentShutdown(ssi.getClusterName());
    }

    private int getReplicaCount(ServerShutdownInfo ssi) {
      return info.getReplicaCount(ssi.getClusterName());
    }

    @Nonnull
    private Step createWaitForServersDownStep() {
      return new RunInParallelStep(createShutdownWaiters());
    }

    @Nonnull
    private List<Fiber.StepAndPacket> createShutdownWaiters() {
      return serverShutdownInfos.stream().map(this::createServerDownWaiter).toList();
    }

    @Nonnull
    private Fiber.StepAndPacket createServerDownWaiter(ServerShutdownInfo ssi) {
      return new Fiber.StepAndPacket(createWaitSteps(ssi), createPacketForServer(packet, ssi));
    }

    @Nullable
    private Step createWaitSteps(ServerShutdownInfo ssi) {
      return waitForDelete(ssi);
    }

    private Step waitForDelete(ServerShutdownInfo ssi) {
      return new PodDownStep(ssi);
    }

    private class PodDownStep extends Step {
      private final ServerShutdownInfo ssi;

      PodDownStep(ServerShutdownInfo ssi) {
        this.ssi = ssi;
      }

      @NotNull
      @Override
      public Result apply(Packet packet) {
        if (info.getServerPod(ssi.getServerName()) != null) {
          // requeue to wait for pod to be deleted
          return doRequeue();
        }
        return doEnd();
      }
    }
  }

  // pre-conditions: DomainPresenceInfo SPI
  // "principal"
  // "serverScan"
  // "clusterScan"
  // "envVars"
  private static Step createServiceStep(ServerShutdownInfo ssi) {
    return ServiceHelper.createForServerStep(
            true, new ServerDownStep(ssi.getServerName(), true, null));
  }

  private static class ShutdownClusteredServersStepFactory {

    private final Queue<Fiber.StepAndPacket> serversToShutdown = new ConcurrentLinkedQueue<>();
    private final int maxConcurrency;
    private final int replicaCount;

    ShutdownClusteredServersStepFactory(int maxConcurrency, int replicaCount) {
      this.maxConcurrency = maxConcurrency;
      this.replicaCount = replicaCount;
    }

    void add(Fiber.StepAndPacket serverToShutdown) {
      serversToShutdown.add(serverToShutdown);
    }

    Collection<Fiber.StepAndPacket> getServerShutdownStepAndPackets(DomainPresenceInfo info) {
      if ((maxConcurrency == 0) || (replicaCount == 0) || info.getDomain().isShuttingDown()) {
        return serversToShutdown;
      }
      ArrayList<Fiber.StepAndPacket> steps = new ArrayList<>(maxConcurrency);
      IntStream.range(0, maxConcurrency)
              .forEach(i -> steps.add(ShutdownClusteredServersStep.createStepAndPacket(serversToShutdown)));
      return steps;
    }
  }

  static class RunInParallelStep extends Step {
    final Collection<Fiber.StepAndPacket> shutdownDetails;

    RunInParallelStep(Collection<Fiber.StepAndPacket> shutdownDetails) {
      this.shutdownDetails = shutdownDetails;
    }

    @Override
    public @Nonnull Result apply(Packet packet) {
      if (shutdownDetails.isEmpty()) {
        return doNext(getNext(), packet);
      } else {
        return doForkJoin(getNext(), packet, shutdownDetails);
      }
    }
  }

  static class ShutdownClusteredServersStep extends Step {

    private final Queue<Fiber.StepAndPacket> serversToShutdown;

    static Fiber.StepAndPacket createStepAndPacket(Queue<Fiber.StepAndPacket> serversToShutdown) {
      return new Fiber.StepAndPacket(new ShutdownClusteredServersStep(serversToShutdown), null);
    }

    ShutdownClusteredServersStep(Queue<Fiber.StepAndPacket> serversToShutdown) {
      super(null);
      this.serversToShutdown = serversToShutdown;
    }

    @Override
    public @Nonnull Result apply(Packet packet) {

      if (serversToShutdown.isEmpty()) {
        return doNext(packet);
      } else {
        Collection<Fiber.StepAndPacket> servers = Collections.singletonList(serversToShutdown.poll());
        return doForkJoin(this, packet, servers);
      }
    }
  }

}
