// Copyright (c) 2017, 2021, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.steps;

import oracle.kubernetes.operator.helpers.DomainPresenceInfo;
import oracle.kubernetes.operator.work.NextAction;
import oracle.kubernetes.operator.work.Packet;
import oracle.kubernetes.operator.work.Step;

public class DomainPresenceStep extends Step {

  private DomainPresenceStep(Step domainUpSteps) {
    super(domainUpSteps);
  }

  public static DomainPresenceStep createDomainPresenceStep(
      DomainPresenceInfo info, Step domainUpSteps, Step managedServerStep) {
    return new DomainPresenceStep(info.isShuttingDown() ? managedServerStep : domainUpSteps);
  }

  @Override
  public NextAction apply(Packet packet) {
    return doNext(packet);
  }
}
