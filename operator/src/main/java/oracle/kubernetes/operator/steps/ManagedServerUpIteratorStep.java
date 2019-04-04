// Copyright 2017, 2019, Oracle Corporation and/or its affiliates.  All rights reserved.
// Licensed under the Universal Permissive License v 1.0 as shown at
// http://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.steps;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
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

  private final Collection<ServerStartupInfo> c;

  public ManagedServerUpIteratorStep(Collection<ServerStartupInfo> c, Step next) {
    super(next);
    this.c = c;
  }

  @Override
  protected String getDetail() {
    List<String> serversToStart = new ArrayList<>();
    for (ServerStartupInfo ssi : c) {
      serversToStart.add(ssi.serverConfig.getName());
    }
    return String.join(",", serversToStart);
  }

  @Override
  public NextAction apply(Packet packet) {
    Collection<StepAndPacket> startDetails = new ArrayList<>();
    Map<String, StepAndPacket> rolling = new ConcurrentHashMap<>();
    packet.put(ProcessingConstants.SERVERS_TO_ROLL, rolling);

    for (ServerStartupInfo ssi : c) {
      Packet p = packet.clone();
      p.put(ProcessingConstants.SERVER_SCAN, ssi.serverConfig);
      p.put(ProcessingConstants.CLUSTER_NAME, ssi.getClusterName());
      p.put(ProcessingConstants.ENVVARS, ssi.getEnvironment());

      p.put(ProcessingConstants.SERVER_NAME, ssi.serverConfig.getName());

      startDetails.add(new StepAndPacket(bringManagedServerUp(ssi, null), p));
    }

    if (LOGGER.isFineEnabled()) {
      DomainPresenceInfo info = packet.getSPI(DomainPresenceInfo.class);

      Domain dom = info.getDomain();

      Collection<String> serverList = new ArrayList<>();
      for (ServerStartupInfo ssi : c) {
        serverList.add(ssi.serverConfig.getName());
      }
      LOGGER.fine(
          "Starting or validating servers for domain with UID: "
              + dom.getDomainUID()
              + ", server list: "
              + serverList);
    }

    if (startDetails.isEmpty()) {
      return doNext(packet);
    }
    return doForkJoin(new ManagedServerUpAfterStep(getNext()), packet, startDetails);
  }

  // pre-conditions: DomainPresenceInfo SPI
  // "principal"
  // "serverScan"
  // "clusterScan"
  // "envVars"
  private static Step bringManagedServerUp(ServerStartupInfo ssi, Step next) {
    return PodHelper.createManagedPodStep(ServiceHelper.createForServerStep(next));
  }
}
