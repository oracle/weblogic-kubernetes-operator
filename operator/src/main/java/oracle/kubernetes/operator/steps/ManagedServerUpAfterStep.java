// Copyright (c) 2017, 2021, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.steps;

import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import javax.validation.constraints.NotNull;

import oracle.kubernetes.operator.MakeRightDomainOperation;
import oracle.kubernetes.operator.ProcessingConstants;
import oracle.kubernetes.operator.helpers.DomainPresenceInfo;
import oracle.kubernetes.operator.helpers.RollingHelper;
import oracle.kubernetes.operator.logging.LoggingFacade;
import oracle.kubernetes.operator.logging.LoggingFactory;
import oracle.kubernetes.operator.work.NextAction;
import oracle.kubernetes.operator.work.Packet;
import oracle.kubernetes.operator.work.Step;

public class ManagedServerUpAfterStep extends Step {
  private static final LoggingFacade LOGGER = LoggingFactory.getLogger("Operator", "Operator");

  public ManagedServerUpAfterStep(Step next) {
    super(next);
  }

  @Override
  public NextAction apply(Packet packet) {
    if (getServersToRoll(packet).isEmpty()) {
      return doNext(packet);
    } else if (MakeRightDomainOperation.isInspectionRequired(packet)) {
      return doNext(MakeRightDomainOperation.createStepsToRerunWithIntrospection(packet), packet);
    } else {
      logServersToRoll(packet);
      return doNext(RollingHelper.rollServers(getServersToRoll(packet), getNext()), packet);
    }
  }

  @SuppressWarnings("unchecked")
  @NotNull Map<String, StepAndPacket> getServersToRoll(Packet packet) {
    return Optional.ofNullable((Map<String, StepAndPacket>) packet.get(ProcessingConstants.SERVERS_TO_ROLL))
          .orElseGet(Collections::emptyMap);
  }

  void logServersToRoll(Packet packet) {
    if (LOGGER.isFineEnabled()) {
      LOGGER.fine("Rolling servers for domain with UID: {0}: {1}",
            getDomainUid(packet),
            getRollingServerNames(packet));
    }
  }

  private String getDomainUid(Packet packet) {
    return packet.getSpi(DomainPresenceInfo.class).getDomainUid();
  }

  private Set<String> getRollingServerNames(Packet packet) {
    return getServersToRoll(packet).keySet();
  }
}
