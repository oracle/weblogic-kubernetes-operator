// Copyright 2018, Oracle Corporation and/or its affiliates.  All rights reserved.
// Licensed under the Universal Permissive License v 1.0 as shown at
// http://oss.oracle.com/licenses/upl.

package oracle.kubernetes.json;

import static org.hamcrest.Matchers.is;
import static org.hamcrest.junit.MatcherAssert.assertThat;

import io.kubernetes.client.models.V1EnvVar;
import io.kubernetes.client.models.V1EnvVarSource;
import io.kubernetes.client.models.V1beta1Ingress;
import io.kubernetes.client.proto.V1;
import org.junit.Test;

public class KubernetesApiNamesTest {

  @Test
  public void matchTopLevelClass() {
    assertThat(KubernetesApiNames.matches("io.k8s.api.core.v1.EnvVar", V1EnvVar.class), is(true));
    assertThat(
        KubernetesApiNames.matches("io.k8s.api.extensions.v1beta1.Ingress", V1beta1Ingress.class),
        is(true));
  }

  @Test
  public void matchNestedClass() {
    assertThat(KubernetesApiNames.matches("io.k8s.api.core.v1.EnvVar", V1.EnvVar.class), is(true));
    assertThat(
        KubernetesApiNames.matches("io.k8s.api.extensions.v1beta1.Ingress", V1beta1Ingress.class),
        is(true));
  }

  @Test
  public void dontMatchOthers() {
    assertThat(KubernetesApiNames.matches("abc", V1EnvVarSource.class), is(false));
    assertThat(
        KubernetesApiNames.matches("io.k8s.api.core.v1.EnvVar", V1EnvVarSource.class), is(false));
  }
}
