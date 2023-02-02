// Copyright (c) 2023, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.weblogic.domain.model;

import oracle.kubernetes.operator.logging.LoggingFacade;
import oracle.kubernetes.operator.logging.LoggingFactory;

class IntrospectorJobPodTest extends BaseIntrospectorJobPodConfigurationTestBase {

  private static final LoggingFacade LOGGER = LoggingFactory.getLogger("Operator", "Operator");

  private final IntrospectorJob introspector1;
  private final IntrospectorJob introspector2;
  private final DomainResource domain = new DomainResource();

  /**
   * Introspector server tests.
   */
  public IntrospectorJobPodTest() {
    super(new IntrospectorJob(), new IntrospectorJob());
    introspector1 = getInstance1();
    introspector2 = getInstance2();
  }
}
