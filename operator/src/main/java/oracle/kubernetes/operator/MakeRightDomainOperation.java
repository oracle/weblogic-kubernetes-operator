// Copyright (c) 2020, 2023, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator;

import java.util.Optional;
import javax.annotation.Nonnull;

import oracle.kubernetes.operator.helpers.DomainPresenceInfo;
import oracle.kubernetes.operator.helpers.EventHelper.EventData;
import oracle.kubernetes.operator.work.Packet;
import oracle.kubernetes.operator.work.Step;
import oracle.kubernetes.weblogic.domain.model.DomainResource;

import static oracle.kubernetes.operator.ProcessingConstants.MAKE_RIGHT_DOMAIN_OPERATION;

/**
 * Defines the operation to bring a running domain into compliance with its domain resource and introspection result.
 */
public interface MakeRightDomainOperation extends MakeRightOperation<DomainPresenceInfo> {

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
   * Modifies the factory to indicate that this is a retry operation on a retriable failure.
   +
   + @return the updated factory
   */
  MakeRightDomainOperation retryOnFailure();

  boolean isDeleting();

  boolean isRetryOnFailure();

  void setInspectionRun();

  void setLiveInfo(@Nonnull DomainPresenceInfo info);

  void clear();

  @Override
  default void addToPacket(Packet packet) {
    packet.put(MAKE_RIGHT_DOMAIN_OPERATION, this);
  }

  boolean wasInspectionRun();

  private static boolean wasInspectionRun(Packet packet) {
    return fromPacket(packet).map(MakeRightDomainOperation::wasInspectionRun).orElse(false);
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
    return Optional.ofNullable(packet.getSpi(DomainPresenceInfo.class))
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
