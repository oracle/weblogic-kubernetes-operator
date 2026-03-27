// Copyright (c) 2020, 2026, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Collections;
import java.util.Optional;
import javax.annotation.Nonnull;

import oracle.kubernetes.operator.helpers.DomainPresenceInfo;
import oracle.kubernetes.operator.helpers.EventHelper.EventData;
import oracle.kubernetes.operator.helpers.HelmAccess;
import oracle.kubernetes.operator.logging.LoggingFacade;
import oracle.kubernetes.operator.logging.LoggingFactory;
import oracle.kubernetes.operator.work.Packet;
import oracle.kubernetes.operator.work.Step;
import oracle.kubernetes.weblogic.domain.model.DomainCondition;
import oracle.kubernetes.weblogic.domain.model.DomainResource;
import oracle.kubernetes.weblogic.domain.model.DomainStatus;

import static oracle.kubernetes.operator.KubernetesConstants.OPERATOR_INTROSPECTJOB_RECHECK_SKEW;
import static oracle.kubernetes.operator.LabelConstants.INTROSPECTION_TIME;
import static oracle.kubernetes.operator.ProcessingConstants.DOMAIN_PRESENCE_INFO;
import static oracle.kubernetes.operator.ProcessingConstants.MAKE_RIGHT_DOMAIN_OPERATION;
import static oracle.kubernetes.weblogic.domain.model.DomainCondition.FALSE;
import static oracle.kubernetes.weblogic.domain.model.DomainCondition.TRUE;
import static oracle.kubernetes.weblogic.domain.model.DomainConditionType.COMPLETED;
import static oracle.kubernetes.weblogic.domain.model.DomainConditionType.ROLLING;

/**
 * Defines the operation to bring a running domain into compliance with its domain resource and introspection result.
 */
public interface MakeRightDomainOperation extends MakeRightOperation<DomainPresenceInfo> {
  final LoggingFacade LOGGER = LoggingFactory.getLogger("Operator", "Operator");

  /**
   * Defines the operation as pertaining to the deletion of a domain.
   * @return The make right domain operation for deletion
   */
  MakeRightDomainOperation forDeletion();

  MakeRightDomainOperation createRetry(@Nonnull DomainPresenceInfo info);

  MakeRightDomainOperation withExplicitRecheck();

  /**
   * Specifies the event that started this operation.
   * @param eventData a description of the event, containing at least the event type.
   *
   * @return the updated factory
   */
  MakeRightDomainOperation withEventData(EventData eventData);

  /**
   * Modifies the factory to indicate that it should interrupt any current make-right thread.
   +
   + @return the updated factory
   */
  MakeRightDomainOperation interrupt();

  /**
   * Modifies the factory to indicate that this is a retry operation on a retryable failure.
   +
   + @return the updated factory
   */
  MakeRightDomainOperation retryOnFailure();

  boolean isDeleting();

  boolean isRetryOnFailure();

  void setInspectionRun();

  void setLiveInfo(@Nonnull DomainPresenceInfo info);

  void clear();

  boolean wasInspectionRun();

  private static boolean wasInspectionRun(Packet packet) {
    // check if introspection has run since Completed=False set
    DomainStatus status = Optional.ofNullable((DomainPresenceInfo) packet.get(DOMAIN_PRESENCE_INFO))
            .map(DomainPresenceInfo::getDomain)
            .map(DomainResource::getStatus)
            .orElse(null);

    OffsetDateTime lastTransitionTime = Optional.ofNullable(status)
            .map(DomainStatus::getConditions)
            .orElse(Collections.emptyList())
            .stream()
            .filter(condition -> COMPLETED.equals(condition.getType()) && FALSE.equals(condition.getStatus()))
            .findFirst()
            .map(DomainCondition::getLastTransitionTime)
            .orElse(null);

    if (lastTransitionTime != null) {
      String time = packet.getValue(INTROSPECTION_TIME);
      if (time != null) {
        OffsetDateTime lastIntrospectionTime = OffsetDateTime.parse(time);
        Duration d = Duration.between(lastTransitionTime.toInstant(), lastIntrospectionTime.toInstant());
        int skew = getIntEnvOrDefault(OPERATOR_INTROSPECTJOB_RECHECK_SKEW,3, 3, 10);
        LOGGER.finest("skew=" + skew + " differences " + d.toMillis() + " last introspection time="
                + lastIntrospectionTime);
        if (d.compareTo(Duration.ofSeconds(10)) > 0) {
          // check condition where domain is rolling, do not run introspection again.
          boolean isRolling = Optional.ofNullable(status)
                  .map(DomainStatus::getConditions)
                  .orElse(Collections.emptyList())
                  .stream()
                  .anyMatch(condition -> ROLLING.equals(condition.getType())
                          && TRUE.equals(condition.getStatus()));

          LOGGER.finest("Large transition timing detected: MakeRightDomainOperation.wasInspectionRun skew="
                  + skew + " introspectionPodCreation - completedFalse = " + d.toMillis() + "ms"
                  + " isRolling=" + isRolling);

          return true;
        }
        if (lastIntrospectionTime.isAfter(lastTransitionTime.minusSeconds(skew))) {
          return true;
        }
      }
    }

    return fromPacket(packet).map(MakeRightDomainOperation::wasInspectionRun).orElse(false);
  }

  private static int getIntEnvOrDefault(String key, int def, int min, int max) {
    String raw = HelmAccess.getHelmVariable(key);
    if (raw == null) {
      return def;
    }

    String s = raw.trim();
    if (s.isEmpty()) {
      return def;
    }

    try {
      int v = Integer.parseInt(s);
      if (v < min) {
        return min;
      }
      if (v > max) {
        return max;
      }
      return v;
    } catch (NumberFormatException e) {
      LOGGER.warning("Invalid integer for " + key + "='" + raw + "', using default " + def);
      return def;
    }
  }

  static void recordInspection(Packet packet) {
    fromPacket(packet).ifPresent(MakeRightDomainOperation::setInspectionRun);
  }

  static boolean isInspectionRequired(Packet packet) {
    return domainRequiresIntrospectionInCurrentMakeRight(packet) && !wasInspectionRun(packet);
  }

  static boolean isMakeRight(Packet packet) {
    return fromPacket(packet).isPresent();
  }

  /**
   * Returns true if the packet contains info about a domain that requires introspection in a sequences of steps
   * before server pods are created or modified.
   * @return true, if the domain requires introspection
   */
  private static boolean domainRequiresIntrospectionInCurrentMakeRight(Packet packet) {
    return Optional.ofNullable((DomainPresenceInfo) packet.get(ProcessingConstants.DOMAIN_PRESENCE_INFO))
        .map(DomainPresenceInfo::getDomain)
        .map(DomainResource::isNewIntrospectionRequiredForNewServers)
        .orElse(false);
  }

  static Step createStepsToRerunWithIntrospection(Packet packet) {
    packet.put(ProcessingConstants.DOMAIN_INTROSPECT_REQUESTED, "MII");
    return fromPacket(packet).map(MakeRightDomainOperation::createSteps).orElse(null);
  }

  /**
   * Returns an optional containing the make-right-domain-operation in the packet.
   * @param packet a packet which may contain a make-right operation
   */
  static Optional<MakeRightDomainOperation> fromPacket(Packet packet) {
    return Optional.ofNullable(packet.getValue(MAKE_RIGHT_DOMAIN_OPERATION));
  }
}
