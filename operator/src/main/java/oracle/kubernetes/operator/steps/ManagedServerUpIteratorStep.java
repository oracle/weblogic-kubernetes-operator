// Copyright 2017, 2018, Oracle Corporation and/or its affiliates.  All rights reserved.
// Licensed under the Universal Permissive License v 1.0 as shown at
// http://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.steps;

import java.util.ArrayList;
import java.util.Collection;
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
import oracle.kubernetes.weblogic.domain.v1.Domain;
import oracle.kubernetes.weblogic.domain.v1.DomainSpec;
import oracle.kubernetes.weblogic.domain.v1.ServerStartup;

public class ManagedServerUpIteratorStep extends Step {
  private static final LoggingFacade LOGGER = LoggingFactory.getLogger("Operator", "Operator");

  private final Collection<ServerStartupInfo> c;

  public ManagedServerUpIteratorStep(Collection<ServerStartupInfo> c, Step next) {
    super(next);
    this.c = c;
  }

  @Override
  public NextAction apply(Packet packet) {
    Collection<StepAndPacket> startDetails = new ArrayList<>();
    Map<String, StepAndPacket> rolling = new ConcurrentHashMap<>();
    packet.put(ProcessingConstants.SERVERS_TO_ROLL, rolling);

    for (ServerStartupInfo ssi : c) {
      Packet p = packet.clone();
      p.put(ProcessingConstants.SERVER_SCAN, ssi.serverConfig);
      p.put(ProcessingConstants.CLUSTER_SCAN, ssi.clusterConfig);
      p.put(ProcessingConstants.ENVVARS, ssi.envVars);

      p.put(ProcessingConstants.SERVER_NAME, ssi.serverConfig.getName());
      p.put(ProcessingConstants.PORT, ssi.serverConfig.getListenPort());
      ServerStartup ss = ssi.serverStartup;
      p.put(ProcessingConstants.NODE_PORT, ss != null ? ss.getNodePort() : null);

      startDetails.add(new StepAndPacket(bringManagedServerUp(ssi, null), p));
    }

    if (LOGGER.isFineEnabled()) {
      DomainPresenceInfo info = packet.getSPI(DomainPresenceInfo.class);

      Domain dom = info.getDomain();
      DomainSpec spec = dom.getSpec();

      Collection<String> serverList = new ArrayList<>();
      for (ServerStartupInfo ssi : c) {
        serverList.add(ssi.serverConfig.getName());
      }
      LOGGER.fine(
          "Starting or validating servers for domain with UID: "
              + spec.getDomainUID()
              + ", server list: "
              + serverList);
    }

    if (startDetails.isEmpty()) {
      return doNext(packet);
    }
    return doForkJoin(new ManagedServerUpAfterStep(next), packet, startDetails);
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
