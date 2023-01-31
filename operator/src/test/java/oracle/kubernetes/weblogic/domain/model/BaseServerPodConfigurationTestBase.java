// Copyright (c) 2023, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.weblogic.domain.model;

import java.util.Arrays;

import io.kubernetes.client.openapi.models.V1EnvVar;
import org.junit.jupiter.api.Test;

import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.junit.MatcherAssert.assertThat;

public abstract class BaseServerPodConfigurationTestBase {
  private final BaseServerPodConfiguration instance1;
  private final BaseServerPodConfiguration instance2;

  BaseServerPodConfigurationTestBase(BaseServerPodConfiguration instance1, BaseServerPodConfiguration instance2) {
    this.instance1 = instance1;
    this.instance2 = instance2;
  }

  @SuppressWarnings("unchecked")
  <T extends BaseServerPodConfiguration> T getInstance1() {
    return (T) instance1;
  }

  @SuppressWarnings("unchecked")
  <T extends BaseServerPodConfiguration> T getInstance2() {
    return (T) instance2;
  }

  @Test
  void whenEnvironmentsAreTheSame_objectsAreEqual() {
    instance1.setEnv(Arrays.asList(env("a", "b"), env("c", "d")));
    instance2.addEnvironmentVariable("a", "b");
    instance2.addEnvironmentVariable("c", "d");

    assertThat(instance1, equalTo(instance2));
  }

  @Test
  void whenEnvironmentsDiffer_objectsAreNotEqual() {
    instance1.setEnv(Arrays.asList(env("a", "b"), env("c", "d")));
    instance2.addEnvironmentVariable("a", "b");

    assertThat(instance1, not(equalTo(instance2)));
  }

  private V1EnvVar env(String name, String value) {
    return new V1EnvVar().name(name).value(value);
  }

  @Test
  void whenResourcesAreTheSame_objectsAreEqual() {
    instance1.addRequestRequirement("cpu", "100m");
    instance2.addRequestRequirement("cpu", "100m");
    instance1.addLimitRequirement("cpu", "500m");
    instance2.addLimitRequirement("cpu", "500m");

    assertThat(instance1, equalTo(instance2));
  }

  @Test
  void whenResourcesDiffer_objectsAreNotEqual() {
    instance1.addRequestRequirement("cpu", "100m");
    instance2.addRequestRequirement("cpu", "200m");
    instance1.addLimitRequirement("cpu", "500m");
    instance2.addLimitRequirement("cpu", "1000m");

    assertThat(instance1, not(equalTo(instance2)));
  }
}
