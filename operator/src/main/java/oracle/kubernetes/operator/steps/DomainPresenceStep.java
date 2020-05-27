// Copyright (c) 2017, 2020, Oracle Corporation and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.steps;

import oracle.kubernetes.operator.work.NextAction;
import oracle.kubernetes.operator.work.Packet;
import oracle.kubernetes.operator.work.Step;
import oracle.kubernetes.weblogic.domain.model.Domain;

public class DomainPresenceStep extends Step {

  private DomainPresenceStep(Step domainUpSteps) {
    super(domainUpSteps);
  }

  public static DomainPresenceStep createDomainPresenceStep(
      Domain dom, Step domainUpSteps, Step managedServerStep) {
    return new DomainPresenceStep(dom.isShuttingDown() ? managedServerStep : domainUpSteps);
  }

  @Override
  public NextAction apply(Packet packet) {
    return doNext(packet);
  }
}
