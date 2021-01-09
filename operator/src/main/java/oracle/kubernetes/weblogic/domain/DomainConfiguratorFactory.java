// Copyright (c) 2018, 2021, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.weblogic.domain;

import oracle.kubernetes.weblogic.domain.model.Domain;
import oracle.kubernetes.weblogic.domain.model.DomainCommonConfigurator;

public class DomainConfiguratorFactory {

  private static final DomainConfigurator exemplar = new DomainCommonConfigurator();

  public static DomainConfigurator forDomain(Domain domain) {
    return exemplar.createFor(domain);
  }

}
