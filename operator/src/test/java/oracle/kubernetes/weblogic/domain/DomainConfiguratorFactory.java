// Copyright (c) 2018, 2022, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.weblogic.domain;

import oracle.kubernetes.weblogic.domain.model.DomainCommonConfigurator;
import oracle.kubernetes.weblogic.domain.model.DomainResource;

public class DomainConfiguratorFactory {

  private DomainConfiguratorFactory() {
    // no-op
  }

  private static final DomainConfigurator exemplar = new DomainCommonConfigurator();

  public static DomainConfigurator forDomain(DomainResource domain) {
    return exemplar.createFor(domain);
  }

}
