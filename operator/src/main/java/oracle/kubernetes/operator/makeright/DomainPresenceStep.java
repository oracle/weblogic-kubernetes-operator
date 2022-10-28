// Copyright (c) 2017, 2022, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.makeright;

import javax.annotation.Nonnull;

import oracle.kubernetes.operator.helpers.DomainPresenceInfo;
import oracle.kubernetes.operator.work.NextAction;
import oracle.kubernetes.operator.work.Packet;
import oracle.kubernetes.operator.work.Step;
import oracle.kubernetes.weblogic.domain.model.DomainResource;

public class DomainPresenceStep extends Step {

  private final Step domainDownSteps;
  private final Step domainUpSteps;

  DomainPresenceStep(Step domainDownSteps, Step domainUpSteps) {
    this.domainDownSteps = domainDownSteps;
    this.domainUpSteps = domainUpSteps;
  }

  public static DomainPresenceStep createDomainPresenceStep(Step domainUpSteps, Step domainDownSteps) {
    return new DomainPresenceStep(domainDownSteps, domainUpSteps);
  }

  @Override
  public NextAction apply(Packet packet) {
    final Step step = getNextSteps(packet);
    return doNext(step, packet);
  }

  private Step getNextSteps(Packet packet) {
    final boolean isShuttingDown = isDomainShuttingDown(packet);
    final Step next = isShuttingDown ? domainDownSteps : domainUpSteps;
    return Step.chain(next, getNext());
  }

  @Nonnull
  private Boolean isDomainShuttingDown(Packet packet) {
    return DomainPresenceInfo.fromPacket(packet)
        .map(DomainPresenceInfo::getDomain)
        .map(DomainResource::isShuttingDown)
        .orElse(false);
  }
}
