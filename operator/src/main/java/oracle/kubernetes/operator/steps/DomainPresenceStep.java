// Copyright 2017, 2018, Oracle Corporation and/or its affiliates.  All rights reserved.
// Licensed under the Universal Permissive License v 1.0 as shown at
// http://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.steps;

import oracle.kubernetes.operator.work.NextAction;
import oracle.kubernetes.operator.work.Packet;
import oracle.kubernetes.operator.work.Step;
import oracle.kubernetes.weblogic.domain.model.Domain;

public class DomainPresenceStep extends Step {

  private DomainPresenceStep(Step adminStep) {
    super(adminStep);
  }

  public static DomainPresenceStep createDomainPresenceStep(
      Domain dom, Step adminStep, Step managedServerStep) {
    return new DomainPresenceStep(dom.isShuttingDown() ? managedServerStep : adminStep);
  }

  @Override
  public NextAction apply(Packet packet) {
    return doNext(packet);
  }
}
