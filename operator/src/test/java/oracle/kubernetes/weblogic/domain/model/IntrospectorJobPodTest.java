// Copyright (c) 2023, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.weblogic.domain.model;

import oracle.kubernetes.operator.logging.LoggingFacade;
import oracle.kubernetes.operator.logging.LoggingFactory;

class IntrospectorJobPodTest extends BaseIntrospectorJobPodConfigurationTestBase {

  private static final LoggingFacade LOGGER = LoggingFactory.getLogger("Operator", "Operator");

  private final Introspector introspector1;
  private final Introspector introspector2;

  /**
   * Introspector server tests.
   */
  public IntrospectorJobPodTest() {
    super(new Introspector(), new Introspector());
    introspector1 = getInstance1();
    introspector2 = getInstance2();
  }
}
