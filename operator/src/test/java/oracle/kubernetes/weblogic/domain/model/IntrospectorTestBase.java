// Copyright (c) 2023, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.weblogic.domain.model;

import oracle.kubernetes.operator.logging.LoggingFacade;
import oracle.kubernetes.operator.logging.LoggingFactory;
import oracle.kubernetes.weblogic.domain.DomainConfiguratorFactory;
import oracle.kubernetes.weblogic.domain.IntrospectorConfigurator;
import org.junit.jupiter.api.Test;

import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.junit.MatcherAssert.assertThat;

class IntrospectorTestBase extends BaseServerPodConfigurationTestBase {

  private static final LoggingFacade LOGGER = LoggingFactory.getLogger("Operator", "Operator");

  private final Introspector introspector1;
  private final Introspector introspector2;
  private final DomainResource domain = new DomainResource();

  /**
   * Introspector server tests.
   */
  public IntrospectorTestBase() {
    super(new Introspector(), new Introspector());
    introspector1 = getInstance1();
    introspector2 = getInstance2();
  }

  private IntrospectorConfigurator configureIntrospector() {
    return DomainConfiguratorFactory.forDomain(domain).configureIntrospector();
  }

  @Test
  void whenDifferByPodLabel_objectsAreNotEqual() {
    introspector1.addPodLabel("a", "b");
    introspector2.addPodLabel("a", "c");

    assertThat(introspector1, not(equalTo(introspector2)));
  }

  @Test
  void whenDifferByPodAnnotation_objectsAreNotEqual() {
    introspector1.addPodAnnotation("a", "b");
    introspector2.addPodAnnotation("a", "c");

    assertThat(introspector1, not(equalTo(introspector2)));
  }

  @Test
  void whenServiceAnnotationsDiffer_hashCodesAreNotEqual() {
    introspector1.addPodAnnotation("key", "value");
    assertThat(introspector1.hashCode(), not(equalTo(introspector2.hashCode())));
  }

  @Test
  void whenServiceAnnotationsDoNotDiffer_hashCodesAreEqual() {

    introspector1.addPodAnnotation("key", "value");
    introspector2.addPodAnnotation("key", "value");
    LOGGER.fine("Verify that " + introspector1 + " and " + introspector2 + " are same.");

    assertThat(introspector1.hashCode(), equalTo(introspector2.hashCode()));
  }
}
