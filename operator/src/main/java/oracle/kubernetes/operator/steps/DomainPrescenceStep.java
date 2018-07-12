// Copyright 2017, 2018, Oracle Corporation and/or its affiliates.  All rights reserved.
// Licensed under the Universal Permissive License v 1.0 as shown at
// http://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.steps;

import oracle.kubernetes.operator.StartupControlConstants;
import oracle.kubernetes.operator.helpers.DomainPresenceInfo;
import oracle.kubernetes.operator.logging.LoggingFacade;
import oracle.kubernetes.operator.logging.LoggingFactory;
import oracle.kubernetes.operator.work.NextAction;
import oracle.kubernetes.operator.work.Packet;
import oracle.kubernetes.operator.work.Step;
import oracle.kubernetes.weblogic.domain.v1.Domain;
import oracle.kubernetes.weblogic.domain.v1.DomainSpec;

public class DomainPrescenceStep extends Step {
  private static final LoggingFacade LOGGER = LoggingFactory.getLogger("Operator", "Operator");

  private final Step managedServerStep;

  public DomainPrescenceStep(Step adminStep, Step managedServerStep) {
    super(adminStep);
    this.managedServerStep = managedServerStep;
  }

  @Override
  public NextAction apply(Packet packet) {
    LOGGER.entering();
    DomainPresenceInfo info = packet.getSPI(DomainPresenceInfo.class);

    Domain dom = info.getDomain();
    DomainSpec spec = dom.getSpec();

    String sc = spec.getStartupControl();
    if (sc == null || !StartupControlConstants.NONE_STARTUPCONTROL.equals(sc.toUpperCase())) {
      LOGGER.exiting();
      return doNext(packet);
    }

    LOGGER.exiting();
    // admin server will be stopped as part of scale down flow
    return doNext(managedServerStep, packet);
  }
}
