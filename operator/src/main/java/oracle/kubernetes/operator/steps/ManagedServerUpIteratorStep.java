// Copyright (c) 2017, 2020, Oracle Corporation and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.steps;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
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

public class ManagedServerUpIteratorStep extends Step {
  private static final LoggingFacade LOGGER = LoggingFactory.getLogger("Operator", "Operator");

  private final Collection<ServerStartupInfo> startupInfos;

  public ManagedServerUpIteratorStep(Collection<ServerStartupInfo> startupInfos, Step next) {
    super(next);
    this.startupInfos = startupInfos;
  }

  // pre-conditions: DomainPresenceInfo SPI
  // "principal"
  // "serverScan"
  // "clusterScan"
  // "envVars"
  private static Step bringManagedServerUp(ServerStartupInfo ssi, Step next) {
    return ssi.isServiceOnly()
        ? ServiceHelper.createForServerStep(
            true, new ServerDownStep(ssi.getServerName(), true, next))
        : ServiceHelper.createForServerStep(PodHelper.createManagedPodStep(next));
  }

  @Override
  protected String getDetail() {
    List<String> serversToStart = new ArrayList<>();
    for (ServerStartupInfo ssi : startupInfos) {
      serversToStart.add(ssi.serverConfig.getName());
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

    DomainPresenceInfo info = packet.getSpi(DomainPresenceInfo.class);
    Domain domain = info.getDomain();

    packet.put(ProcessingConstants.SERVERS_TO_ROLL, new ConcurrentHashMap<String, StepAndPacket>());
    Collection<StepAndPacket> startDetails =
        startupInfos.stream()
            .filter(ssi -> canServerStartsConcurrently(ssi, domain))
            .map(ssi -> createManagedServerUpDetails(packet, ssi)).collect(Collectors.toList());

    getSequentialServerStarts(startupInfos, packet, domain).values()
        .forEach(servers -> startDetails.add(new StepAndPacket(new StartServersSequentially(servers), packet)));

    return doNext(
        DomainStatusUpdater.createStatusUpdateStep(new StartManagedServersStep(startDetails, getNext())),
        packet);
  }

  private Map<String, List<StepAndPacket>> getSequentialServerStarts(Collection<ServerStartupInfo> startupInfos,
      Packet packet, Domain domain) {
    Map<String, List<StepAndPacket>> clusterStartupInfos = new HashMap<>();
    startupInfos.stream()
        .filter(ssi -> !canServerStartsConcurrently(ssi, domain))
        .forEach(ssi ->
            clusterStartupInfos.computeIfAbsent(ssi.getClusterName(), l -> new ArrayList())
                .add(createManagedServerUpDetails(packet, ssi)));

    return clusterStartupInfos;
  }

  private String getDomainUid(Packet packet) {
    return packet.getSpi(DomainPresenceInfo.class).getDomain().getDomainUid();
  }

  private List<String> getServerNames(Collection<ServerStartupInfo> startupInfos) {
    return startupInfos.stream().map(ServerStartupInfo::getServerName).collect(Collectors.toList());
  }

  private boolean canServerStartsConcurrently(ServerStartupInfo ssi, Domain dom) {
    return ssi.getClusterName() == null || dom.isAllowConcurrentScaleUp(ssi.getClusterName());
  }

  private StepAndPacket createManagedServerUpDetails(Packet packet, ServerStartupInfo ssi) {
    return new StepAndPacket(bringManagedServerUp(ssi, null), createPacketForServer(packet, ssi));
  }

  private Packet createPacketForServer(Packet packet, ServerStartupInfo ssi) {
    Packet p = packet.clone();
    p.put(ProcessingConstants.CLUSTER_NAME, ssi.getClusterName());
    p.put(ProcessingConstants.SERVER_NAME, ssi.getServerName());
    p.put(ProcessingConstants.SERVER_SCAN, ssi.serverConfig);
    p.put(ProcessingConstants.ENVVARS, ssi.getEnvironment());
    return p;
  }

  static class StartManagedServersStep extends Step {
    final Collection<StepAndPacket> startDetails;

    StartManagedServersStep(Collection<StepAndPacket> startDetails, Step next) {
      super(next);
      this.startDetails = startDetails;
    }

    @Override
    public NextAction apply(Packet packet) {
      return doForkJoin(new ManagedServerUpAfterStep(getNext()), packet, startDetails);
    }
  }

  static class StartServersSequentially extends Step {

    final List<StepAndPacket> serversToStart;

    public StartServersSequentially(List<StepAndPacket> serversToStart) {
      super(null);
      this.serversToStart = serversToStart;
    }

    @Override
    public NextAction apply(Packet packet) {
      Collection<StepAndPacket> servers = Arrays.asList(serversToStart.remove(0));
      if (servers.isEmpty()) {
        return doNext(packet);
      } else {
        return doForkJoin(this, packet, servers);
      }
    }
  }
}
