// Copyright 2019 Oracle Corporation and/or its affiliates.  All rights reserved.
// Licensed under the Universal Permissive License v 1.0 as shown at
// http://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.helpers;

import static org.hamcrest.Matchers.both;
import static org.hamcrest.Matchers.containsString;
import static org.junit.Assert.*;

import io.kubernetes.client.custom.Quantity;
import io.kubernetes.client.models.V1Container;
import io.kubernetes.client.models.V1ContainerPort;
import io.kubernetes.client.models.V1Pod;
import io.kubernetes.client.models.V1PodSpec;
import io.kubernetes.client.models.V1Probe;
import io.kubernetes.client.models.V1ResourceRequirements;
import java.util.Collections;
import org.junit.Test;

public class PodCompatibilityTest {
  @Test
  public void whenImagesDontMatch_createErrorMessage() {
    PodCompatibility.ContainerCompatibility compatibility =
        new PodCompatibility.ContainerCompatibility(
            new V1Container().image("abcde"),
            new V1Container().image("cdefg"),
            Collections.emptyList());

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
                    new V1Probe().initialDelaySeconds(1).timeoutSeconds(2).periodSeconds(3)),
            Collections.emptyList());

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
            new V1Container(),
            Collections.emptyList());

    assertThat(
        compatibility.getIncompatibility(),
        both(containsString("time")).and(containsString("limits")));
  }

  @Test
  public void whenPortsDontMatch_createErrorMessage() {
    PodCompatibility.ContainerCompatibility compatibility =
        new PodCompatibility.ContainerCompatibility(
            new V1Container().name("test").addPortsItem(new V1ContainerPort().containerPort(1100)),
            new V1Container().addPortsItem(new V1ContainerPort().containerPort(1234)),
            Collections.emptyList());

    assertThat(
        compatibility.getIncompatibility(),
        both(containsString("1100")).and(containsString("1234")));
  }

  private V1Pod podWithContainer(V1Container container) {
    return new V1Pod().spec(new V1PodSpec().addContainersItem(container));
  }
}
