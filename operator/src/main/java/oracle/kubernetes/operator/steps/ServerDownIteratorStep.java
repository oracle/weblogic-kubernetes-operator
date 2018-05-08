// Copyright 2017, 2018, Oracle Corporation and/or its affiliates.  All rights reserved.
// Licensed under the Universal Permissive License v 1.0 as shown at
// http://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.steps;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Map;
import oracle.kubernetes.operator.helpers.DomainPresenceInfo;
import oracle.kubernetes.operator.helpers.ServerKubernetesObjects;
import oracle.kubernetes.operator.logging.LoggingFacade;
import oracle.kubernetes.operator.logging.LoggingFactory;
import oracle.kubernetes.operator.work.NextAction;
import oracle.kubernetes.operator.work.Packet;
import oracle.kubernetes.operator.work.Step;
import oracle.kubernetes.weblogic.domain.v1.Domain;
import oracle.kubernetes.weblogic.domain.v1.DomainSpec;

public class ServerDownIteratorStep extends Step {
  private static final LoggingFacade LOGGER = LoggingFactory.getLogger("Operator", "Operator");

  private final Collection<Map.Entry<String, ServerKubernetesObjects>> c;

  public ServerDownIteratorStep(
      Collection<Map.Entry<String, ServerKubernetesObjects>> serversToStop, Step next) {
    super(next);
    this.c = serversToStop;
  }

  @Override
  public NextAction apply(Packet packet) {
    Collection<StepAndPacket> startDetails = new ArrayList<>();

    for (Map.Entry<String, ServerKubernetesObjects> entry : c) {
      startDetails.add(
          new StepAndPacket(
              new ServerDownStep(entry.getKey(), entry.getValue(), null), packet.clone()));
    }

    if (LOGGER.isFineEnabled()) {
      DomainPresenceInfo info = packet.getSPI(DomainPresenceInfo.class);

      Domain dom = info.getDomain();
      DomainSpec spec = dom.getSpec();

      Collection<String> stopList = new ArrayList<>();
      for (Map.Entry<String, ServerKubernetesObjects> entry : c) {
        stopList.add(entry.getKey());
      }
      LOGGER.fine(
          "Stopping servers for domain with UID: "
              + spec.getDomainUID()
              + ", stop list: "
              + stopList);
    }

    if (startDetails.isEmpty()) {
      return doNext(packet);
    }
    return doForkJoin(next, packet, startDetails);
  }
}
