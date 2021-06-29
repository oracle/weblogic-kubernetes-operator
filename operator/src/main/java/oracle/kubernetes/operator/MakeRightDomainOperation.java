// Copyright (c) 2020, 2021, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator;

import java.util.Optional;

import oracle.kubernetes.operator.helpers.DomainPresenceInfo;
import oracle.kubernetes.operator.helpers.EventHelper.EventItem;
import oracle.kubernetes.operator.work.Packet;
import oracle.kubernetes.operator.work.Step;
import oracle.kubernetes.weblogic.domain.model.Domain;

import static oracle.kubernetes.operator.ProcessingConstants.MAKE_RIGHT_DOMAIN_OPERATION;

/**
 * Defines the operation to bring a running domain into compliance with its domain resource and introspection result.
 */
public interface MakeRightDomainOperation {

  /**
   * Defines the operation as pertaining to the deletion of a domain.
   * @return The make right domain operation for deletion
   */
  MakeRightDomainOperation forDeletion();

  MakeRightDomainOperation withExplicitRecheck();

  MakeRightDomainOperation withEventData(EventItem eventItem, String message);

  MakeRightDomainOperation interrupt();

  void execute();

  Step createSteps();

  void setInspectionRun();

  void setLiveInfo(DomainPresenceInfo info);

  void clear();

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

  boolean isExplicitRecheck();

  static boolean isExplicitRecheck(Packet packet) {
    return fromPacket(packet).map(MakeRightDomainOperation::isExplicitRecheck).orElse(false);
  }

  /**
   * Returns true if the packet contains info about a domain that requires introspection in a sequences of steps
   * before server pods are created or modified.
   * @return true, if the domain requires introspection
   */
  private static boolean domainRequiresIntrospectionInCurrentMakeRight(Packet packet) {
    return Optional.ofNullable(packet.getSpi(DomainPresenceInfo.class))
          .map(DomainPresenceInfo::getDomain)
          .map(Domain::isNewIntrospectionRequiredForNewServers)
          .orElse(false);
  }

  static Step createStepsToRerunWithIntrospection(Packet packet) {
    packet.put(ProcessingConstants.DOMAIN_INTROSPECT_REQUESTED, "MII");
    return fromPacket(packet).map(MakeRightDomainOperation::createSteps).orElse(null);
  }

  private static Optional<MakeRightDomainOperation> fromPacket(Packet packet) {
    return Optional.ofNullable((MakeRightDomainOperation) packet.get(MAKE_RIGHT_DOMAIN_OPERATION));
  }
}
