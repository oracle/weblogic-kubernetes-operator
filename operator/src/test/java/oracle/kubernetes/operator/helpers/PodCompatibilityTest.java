// Copyright 2019 Oracle Corporation and/or its affiliates.  All rights reserved.
// Licensed under the Universal Permissive License v 1.0 as shown at
// http://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.helpers;

import static org.hamcrest.Matchers.both;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.junit.Assert.assertThat;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import io.kubernetes.client.custom.Quantity;
import io.kubernetes.client.models.V1Container;
import io.kubernetes.client.models.V1ContainerPort;
import io.kubernetes.client.models.V1Probe;
import io.kubernetes.client.models.V1ResourceRequirements;
import org.junit.Test;

public class PodCompatibilityTest {
  @Test
  public void whenImagesDontMatch_createErrorMessage() {
    PodCompatibility.ContainerCompatibility compatibility =
        new PodCompatibility.ContainerCompatibility(
            new V1Container().image("abcde"), new V1Container().image("cdefg"));

    assertThat(
        compatibility.getIncompatibility(),
        both(containsString("abcde")).and(containsString("cdefg")));
  }

  @Test
  public void whenLivenessProbesDontMatch_createErrorMessage() {
    PodCompatibility.ContainerCompatibility compatibility =
        new PodCompatibility.ContainerCompatibility(
            new V1Container()
                .livenessProbe(
                    new V1Probe().initialDelaySeconds(1).timeoutSeconds(5).periodSeconds(3)),
            new V1Container()
                .livenessProbe(
                    new V1Probe().initialDelaySeconds(1).timeoutSeconds(2).periodSeconds(3)));

    assertThat(
        compatibility.getIncompatibility(),
        both(containsString("timeout")).and(containsString("2")));
  }

  @Test
  public void whenResourcesDontMatch_createErrorMessage() {
    PodCompatibility.ContainerCompatibility compatibility =
        new PodCompatibility.ContainerCompatibility(
            new V1Container()
                .resources(new V1ResourceRequirements().putLimitsItem("time", new Quantity("20"))),
            new V1Container());

    assertThat(
        compatibility.getIncompatibility(),
        both(containsString("time")).and(containsString("limits")));
  }

  @Test
  public void whenPortsDontMatch_createErrorMessage() {
    PodCompatibility.ContainerCompatibility compatibility =
        new PodCompatibility.ContainerCompatibility(
            new V1Container().name("test").addPortsItem(new V1ContainerPort().containerPort(1100)),
            new V1Container().addPortsItem(new V1ContainerPort().containerPort(1234)));

    assertThat(
        compatibility.getIncompatibility(),
        both(containsString("1100")).and(not(containsString("1234"))));
  }

  @Test
  public void whenExpectedSubsetOfActual_reportCompatible() {
    CompatibilityCheck check =
        new CompatibleSets<>("letters", ImmutableSet.of("a", "b"), ImmutableSet.of("b", "c", "a"));

    assertThat(check.isCompatible(), is(true));
  }

  @Test
  public void whenExpectedNotSubsetOfActual_reportNotCompatible() {
    CompatibilityCheck check =
        new CompatibleSets<>(
            "letters", ImmutableSet.of("a", "b", "d"), ImmutableSet.of("b", "c", "a"));

    assertThat(check.isCompatible(), is(false));
  }

  @Test
  public void whenExpectedNotSubsetOfActual_reportMissingElements() {
    CompatibilityCheck check =
        new CompatibleSets<>(
            "letters",
            ImmutableSet.of("alpha", "beta", "delta"),
            ImmutableSet.of("beta", "gamma", "alpha"));

    assertThat(check.getIncompatibility(), containsString("delta"));
    assertThat(check.getIncompatibility(), not(containsString("alpha")));
    assertThat(check.getIncompatibility(), not(containsString("gamma")));
  }

  @Test
  public void whenExpectedSubmapOfActual_reportCompatible() {
    CompatibilityCheck check =
        new CompatibleMaps<>(
            "letters", ImmutableMap.of("a", 1, "b", 2), ImmutableMap.of("b", 2, "c", 3, "a", 1));

    assertThat(check.isCompatible(), is(true));
  }

  @Test
  public void whenExpectedNotSubmapOfActual_reportNotCompatible() {
    CompatibilityCheck check =
        new CompatibleMaps<>(
            "letters",
            ImmutableMap.of("a", 1, "b", 2, "d", 4),
            ImmutableMap.of("b", 2, "c", 3, "a", 1));

    assertThat(check.isCompatible(), is(false));
  }

  @Test
  public void whenExpectedNotSubmapOfActual_reportMissingElements() {
    CompatibilityCheck check =
        new CompatibleMaps<>(
            "letters",
            ImmutableMap.of("alpha", 1, "beta", 2, "delta", 4),
            ImmutableMap.of("beta", 2, "gamma", 3, "alpha", 1));

    assertThat(check.getIncompatibility(), containsString("delta"));
    assertThat(check.getIncompatibility(), not(containsString("alpha")));
    assertThat(check.getIncompatibility(), not(containsString("gamma")));
  }

  @Test
  public void whenActualKeysHaveDifferentValues_reportNotCompatible() {
    CompatibilityCheck check =
        new CompatibleMaps<>(
            "letters", ImmutableMap.of("a", 1, "b", 2), ImmutableMap.of("b", 5, "c", 3, "a", 1));

    assertThat(check.isCompatible(), is(false));
  }

  @Test
  public void whenActualKeysHaveDifferentValues_reportMissingElements() {
    CompatibilityCheck check =
        new CompatibleMaps<>(
            "letters",
            ImmutableMap.of("alpha", 1, "beta", 2),
            ImmutableMap.of("beta", 5, "gamma", 3, "alpha", 1));

    assertThat(check.getIncompatibility(), both(containsString("beta")).and(containsString("5")));
    assertThat(check.getIncompatibility(), not(containsString("alpha")));
    assertThat(check.getIncompatibility(), not(containsString("gamma")));
  }
}
