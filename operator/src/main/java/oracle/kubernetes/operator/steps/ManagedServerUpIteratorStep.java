// Copyright (c) 2017, 2020, Oracle Corporation and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.steps;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
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
          startupInfos.stream().map(ssi -> createManagedServerUpDetails(packet, ssi)).collect(Collectors.toList());
    return doNext(
          DomainStatusUpdater.createStatusUpdateStep(new StartManagedServersStep(startDetails, getNext())),
          packet);
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

}
