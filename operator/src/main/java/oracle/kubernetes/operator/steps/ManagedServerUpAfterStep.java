// Copyright (c) 2017, 2026, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.steps;

import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import javax.annotation.Nonnull;

import io.kubernetes.client.extended.controller.reconciler.Result;
import jakarta.validation.constraints.NotNull;
import oracle.kubernetes.operator.MakeRightDomainOperation;
import oracle.kubernetes.operator.ProcessingConstants;
import oracle.kubernetes.operator.helpers.DomainPresenceInfo;
import oracle.kubernetes.operator.helpers.RollingHelper;
import oracle.kubernetes.operator.logging.LoggingFacade;
import oracle.kubernetes.operator.logging.LoggingFactory;
import oracle.kubernetes.operator.work.Fiber;
import oracle.kubernetes.operator.work.Packet;
import oracle.kubernetes.operator.work.Step;

public class ManagedServerUpAfterStep extends Step {
  private static final LoggingFacade LOGGER = LoggingFactory.getLogger("Operator", "Operator");

  public ManagedServerUpAfterStep(Step next) {
    super(next);
  }

  @Override
  public @Nonnull Result apply(Packet packet) {
    Map<String, Fiber.StepAndPacket> serversToRoll = getServersToRoll(packet);
    if (serversToRoll.isEmpty()) {
      return doNext(packet);
    }

    LOGGER.info("Debug: deferred roll decision for domain with UID: {0}; packetId={1}; "
            + "inspectionRequired={2}; serversToRoll={3}",
        getDomainUid(packet),
        System.identityHashCode(packet),
        MakeRightDomainOperation.isInspectionRequired(packet),
        serversToRoll.keySet());

    if (MakeRightDomainOperation.isInspectionRequired(packet)) {
      LOGGER.info("Debug: rerunning introspection before consuming deferred roll queue for domain with UID: {0}; "
              + "packetId={1}; serversToRoll={2}",
          getDomainUid(packet),
          System.identityHashCode(packet),
          serversToRoll.keySet());
      return doNext(MakeRightDomainOperation.createStepsToRerunWithIntrospection(packet), packet);
    } else {
      Map<String, Fiber.StepAndPacket> serversToRollSnapshot = consumeServersToRoll(packet);
      if (serversToRollSnapshot.isEmpty()) {
        return doNext(packet);
      }

      logServersToRoll(packet, serversToRollSnapshot);
      return doNext(RollingHelper.rollServers(serversToRollSnapshot, getNext()), packet);
    }
  }

  @SuppressWarnings("unchecked")
  @NotNull Map<String, Fiber.StepAndPacket> getServersToRoll(Packet packet) {
    return DomainPresenceInfo.fromPacket(packet)
        .map(info -> getServersToRoll(packet, info))
        .orElseGet(() -> Optional.ofNullable((Map<String, Fiber.StepAndPacket>) packet.get(
            ProcessingConstants.SERVERS_TO_ROLL)).orElseGet(Collections::emptyMap));
  }

  @SuppressWarnings("unchecked")
  private Map<String, Fiber.StepAndPacket> getServersToRoll(Packet packet, DomainPresenceInfo info) {
    synchronized (info) {
      Map<String, Fiber.StepAndPacket> serversToRoll = info.getServersToRoll();
      Map<String, Fiber.StepAndPacket> packetServersToRoll = Optional.ofNullable(
          (Map<String, Fiber.StepAndPacket>) packet.get(ProcessingConstants.SERVERS_TO_ROLL))
          .orElse(Collections.emptyMap());
      if (serversToRoll.isEmpty() && !packetServersToRoll.isEmpty()) {
        serversToRoll = packetServersToRoll;
        info.setServersToRoll(serversToRoll);
      }

      packet.put(ProcessingConstants.SERVERS_TO_ROLL, serversToRoll);
      return serversToRoll;
    }
  }

  Map<String, Fiber.StepAndPacket> consumeServersToRoll(Packet packet) {
    return DomainPresenceInfo.fromPacket(packet)
        .map(info -> consumeServersToRoll(packet, info))
        .orElseGet(() -> new ConcurrentHashMap<>(getServersToRoll(packet)));
  }

  private Map<String, Fiber.StepAndPacket> consumeServersToRoll(Packet packet, DomainPresenceInfo info) {
    synchronized (info) {
      Map<String, Fiber.StepAndPacket> serversToRoll = getServersToRoll(packet, info);
      if (serversToRoll.isEmpty()) {
        return Collections.emptyMap();
      }

      Map<String, Fiber.StepAndPacket> serversToRollSnapshot = new ConcurrentHashMap<>(serversToRoll);
      Map<String, Fiber.StepAndPacket> nextServersToRoll = new ConcurrentHashMap<>();
      info.setServersToRoll(nextServersToRoll);
      packet.put(ProcessingConstants.SERVERS_TO_ROLL, nextServersToRoll);
      return serversToRollSnapshot;
    }
  }

  void logServersToRoll(Packet packet, Map<String, Fiber.StepAndPacket> serversToRoll) {
    if (LOGGER.isFineEnabled()) {
      LOGGER.fine("Rolling servers for domain with UID: {0}: {1}",
            getDomainUid(packet),
            getRollingServerNames(serversToRoll));
    }
  }

  private String getDomainUid(Packet packet) {
    DomainPresenceInfo info = (DomainPresenceInfo) packet.get(ProcessingConstants.DOMAIN_PRESENCE_INFO);
    return info.getDomainUid();
  }

  private Set<String> getRollingServerNames(Map<String, Fiber.StepAndPacket> serversToRoll) {
    return serversToRoll.keySet();
  }
}
