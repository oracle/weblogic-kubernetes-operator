// Copyright 2018, 2019, Oracle Corporation and/or its affiliates.  All rights reserved.
// Licensed under the Universal Permissive License v 1.0 as shown at
// http://oss.oracle.com/licenses/upl.

package oracle.kubernetes.weblogic.domain.model;

import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.junit.MatcherAssert.assertThat;

import io.kubernetes.client.models.V1EnvVar;
import java.util.Arrays;
import org.junit.Test;

public abstract class BaseConfigurationTestBase {
  private BaseConfiguration instance1;
  private BaseConfiguration instance2;

  BaseConfigurationTestBase(BaseConfiguration instance1, BaseConfiguration instance2) {
    this.instance1 = instance1;
    this.instance2 = instance2;
  }

  @SuppressWarnings("unchecked")
  <T extends BaseConfiguration> T getInstance1() {
    return (T) instance1;
  }

  @SuppressWarnings("unchecked")
  <T extends BaseConfiguration> T getInstance2() {
    return (T) instance2;
  }

  @Test
  public void whenEnvironmentsAreTheSame_objectsAreEqual() {
    instance1.setEnv(Arrays.asList(env("a", "b"), env("c", "d")));
    instance2.addEnvironmentVariable("a", "b");
    instance2.addEnvironmentVariable("c", "d");

    assertThat(instance1, equalTo(instance2));
  }

  @Test
  public void whenEnvironmentsDiffer_objectsAreNotEqual() {
    instance1.setEnv(Arrays.asList(env("a", "b"), env("c", "d")));
    instance2.addEnvironmentVariable("a", "b");

    assertThat(instance1, not(equalTo(instance2)));
  }

  @Test
  public void whenServerStartStatesAreSame_objectsAreEqual() {
    instance1.setServerStartState("ADMIN");
    instance2.setServerStartState("ADMIN");

    assertThat(instance1, equalTo(instance2));
  }

  @Test
  public void whenServerStartStatesDiffer_objectsAreNotEqual() {
    instance1.setServerStartState("ADMIN");

    assertThat(instance1, not(equalTo(instance2)));
  }

  @Test
  public void whenServerStartPolicyAreSame_objectsAreEqual() {
    instance1.setServerStartPolicy("IF_NEEDED");
    instance2.setServerStartPolicy("IF_NEEDED");

    assertThat(instance1, equalTo(instance2));
  }

  @Test
  public void whenServerStartPoliciesDiffer_objectsAreNotEqual() {
    instance1.setServerStartPolicy("NEVER");

    assertThat(instance1, not(equalTo(instance2)));
  }

  private V1EnvVar env(String name, String value) {
    return new V1EnvVar().name(name).value(value);
  }

  @Test
  public void whenServiceLabelsDiffer_objectsAreNotEqual() {
    instance1.addServiceLabel("key", "value");
    assertThat(instance1, not(equalTo(instance2)));
  }

  @Test
  public void whenServiceLabelsDoNotDiffer_objectsAreEqual() {
    instance1.addServiceLabel("key", "value");
    instance2.addServiceLabel("key", "value");
    assertThat(instance1, equalTo(instance2));
  }

  @Test
  public void whenserviceAnnotationsDiffer_hashCodesAreNotEqual() {
    instance1.addServiceAnnotation("key", "value");
    assertThat(instance1.hashCode(), not(equalTo(instance2.hashCode())));
  }

  @Test
  public void whenserviceAnnotationsDoNotDiffer_hashCodesAreEqual() {
    instance1.addServiceAnnotation("key", "value");
    instance2.addServiceAnnotation("key", "value");
    assertThat(instance1.hashCode(), equalTo(instance2.hashCode()));
  }
}
