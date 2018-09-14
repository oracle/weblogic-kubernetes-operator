// Copyright 2018, Oracle Corporation and/or its affiliates.  All rights reserved.
// Licensed under the Universal Permissive License v 1.0 as shown at
// http://oss.oracle.com/licenses/upl.

package oracle.kubernetes.weblogic.domain;

import oracle.kubernetes.weblogic.domain.v1.Domain;
import oracle.kubernetes.weblogic.domain.v1.DomainV1Configurator;
import oracle.kubernetes.weblogic.domain.v2.DomainV2Configurator;

public class DomainConfiguratorFactory {

  private static DomainConfigurator examplar = new DomainV1Configurator(null);

  public static DomainConfigurator forDomain(Domain domain) {
    return examplar.createFor(domain);
  }

  public static void selectV1DomainModel() {
    examplar = new DomainV1Configurator(null);
  }

  public static void selectV2DomainModel() {
    examplar = new DomainV2Configurator(null);
  }
}
