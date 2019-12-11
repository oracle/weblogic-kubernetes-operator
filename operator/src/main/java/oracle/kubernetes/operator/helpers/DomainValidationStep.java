// Copyright (c) 2019, Oracle Corporation and/or its affiliates.  All rights reserved.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.helpers;

import java.util.List;

import oracle.kubernetes.operator.DomainStatusUpdater;
import oracle.kubernetes.operator.logging.LoggingFacade;
import oracle.kubernetes.operator.logging.LoggingFactory;
import oracle.kubernetes.operator.work.NextAction;
import oracle.kubernetes.operator.work.Packet;
import oracle.kubernetes.operator.work.Step;
import oracle.kubernetes.weblogic.domain.model.Domain;

import static java.lang.System.lineSeparator;
import static oracle.kubernetes.operator.helpers.DomainStatusPatch.BAD_DOMAIN;
import static oracle.kubernetes.operator.logging.MessageKeys.DOMAIN_VALIDATION_FAILED;

public class DomainValidationStep extends Step {

  private static final LoggingFacade LOGGER = LoggingFactory.getLogger("Operator", "Operator");

  public DomainValidationStep(Step next) {
    super(next);
  }

  @Override
  public NextAction apply(Packet packet) {
    DomainPresenceInfo info = packet.getSpi(DomainPresenceInfo.class);
    Domain domain = info.getDomain();
    List<String> validationFailures = domain.getValidationFailures();

    if (validationFailures.isEmpty()) return doNext(packet);

    LOGGER.severe(DOMAIN_VALIDATION_FAILED, domain.getDomainUid(), perLine(validationFailures));
    Step step = DomainStatusUpdater.createFailedStep(BAD_DOMAIN, perLine(validationFailures), null);
    return doNext(step, packet);
  }

  private String perLine(List<String> validationFailures) {
    return String.join(lineSeparator(), validationFailures);
  }
}
