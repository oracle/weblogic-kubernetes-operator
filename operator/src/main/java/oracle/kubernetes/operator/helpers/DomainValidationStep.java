// Copyright 2019, Oracle Corporation and/or its affiliates.  All rights reserved.
// Licensed under the Universal Permissive License v 1.0 as shown at
// http://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.helpers;

import java.util.List;

import oracle.kubernetes.operator.work.NextAction;
import oracle.kubernetes.operator.work.Packet;
import oracle.kubernetes.operator.work.Step;
import oracle.kubernetes.weblogic.domain.model.Domain;

import static java.lang.System.lineSeparator;

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

    DomainStatusPatch.updateDomainStatus(domain, "ErrBadDomain", String.join(lineSeparator(), validationFailures));
    return doEnd(packet);
  }
}
