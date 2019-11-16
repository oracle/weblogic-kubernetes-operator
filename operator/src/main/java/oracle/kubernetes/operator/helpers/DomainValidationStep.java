// Copyright (c) 2019, Oracle Corporation and/or its affiliates.  All rights reserved.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.helpers;

import java.util.List;

import oracle.kubernetes.operator.work.NextAction;
import oracle.kubernetes.operator.work.Packet;
import oracle.kubernetes.operator.work.Step;
import oracle.kubernetes.weblogic.domain.model.Domain;

import static java.lang.System.lineSeparator;
import static oracle.kubernetes.operator.helpers.DomainStatusPatch.BAD_DOMAIN;

public class DomainValidationStep extends Step {
  private Domain domain;

  public DomainValidationStep(Domain domain, Step next) {
    super(next);
    this.domain = domain;
  }

  @Override
  public NextAction apply(Packet packet) {
    List<String> validationFailures = domain.getValidationFailures();

    if (validationFailures.isEmpty()) return doNext(packet);

    Step step = DomainStatusPatch.createStep(domain, BAD_DOMAIN, perLine(validationFailures));
    return doNext(step, packet);
  }

  private String perLine(List<String> validationFailures) {
    return String.join(lineSeparator(), validationFailures);
  }
}
