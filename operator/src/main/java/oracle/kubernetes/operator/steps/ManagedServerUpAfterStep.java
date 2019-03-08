// Copyright 2017, 2018, Oracle Corporation and/or its affiliates.  All rights reserved.
// Licensed under the Universal Permissive License v 1.0 as shown at
// http://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.steps;

import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import oracle.kubernetes.operator.ProcessingConstants;
import oracle.kubernetes.operator.helpers.DomainPresenceInfo;
import oracle.kubernetes.operator.helpers.RollingHelper;
import oracle.kubernetes.operator.logging.LoggingFacade;
import oracle.kubernetes.operator.logging.LoggingFactory;
import oracle.kubernetes.operator.work.NextAction;
import oracle.kubernetes.operator.work.Packet;
import oracle.kubernetes.operator.work.Step;
import oracle.kubernetes.weblogic.domain.model.Domain;

public class ManagedServerUpAfterStep extends Step {
  private static final LoggingFacade LOGGER = LoggingFactory.getLogger("Operator", "Operator");

  public ManagedServerUpAfterStep(Step next) {
    super(next);
  }

  @Override
  public NextAction apply(Packet packet) {
    @SuppressWarnings("unchecked")
    Map<String, StepAndPacket> rolling =
        (Map<String, StepAndPacket>) packet.get(ProcessingConstants.SERVERS_TO_ROLL);

    if (LOGGER.isFineEnabled()) {
      DomainPresenceInfo info = packet.getSPI(DomainPresenceInfo.class);

      Domain dom = info.getDomain();

      Collection<String> rollingList = Collections.emptyList();
      if (rolling != null) {
        rollingList = rolling.keySet();
      }
      LOGGER.fine(
          "Rolling servers for domain with UID: "
              + dom.getDomainUID()
              + ", rolling list: "
              + rollingList);
    }

    if (rolling == null || rolling.isEmpty()) {
      return doNext(packet);
    }

    return doNext(RollingHelper.rollServers(rolling, getNext()), packet);
  }
}
