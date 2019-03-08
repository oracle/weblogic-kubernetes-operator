// Copyright 2018, Oracle Corporation and/or its affiliates.  All rights reserved.
// Licensed under the Universal Permissive License v 1.0 as shown at
// http://oss.oracle.com/licenses/upl.

package oracle.kubernetes.weblogic.domain;

import oracle.kubernetes.weblogic.domain.model.Domain;
import oracle.kubernetes.weblogic.domain.model.DomainCommonConfigurator;

public class DomainConfiguratorFactory {

  private static DomainConfigurator exemplar = new DomainCommonConfigurator();

  public static DomainConfigurator forDomain(Domain domain) {
    return exemplar.createFor(domain);
  }

  public static void selectCommonDomainModel() {
    exemplar = new DomainCommonConfigurator();
  }
}
