// Copyright (c) 2018, 2021, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.json;

import io.kubernetes.client.openapi.models.V1EnvVar;
import io.kubernetes.client.openapi.models.V1EnvVarSource;
import io.kubernetes.client.proto.V1;
import org.junit.jupiter.api.Test;

import static org.hamcrest.Matchers.is;
import static org.hamcrest.junit.MatcherAssert.assertThat;

public class KubernetesApiNamesTest {

  @Test
  public void matchTopLevelClass() {
    assertThat(KubernetesApiNames.matches("io.k8s.api.core.v1.EnvVar", V1EnvVar.class), is(true));
  }

  @Test
  public void matchNestedClass() {
    assertThat(KubernetesApiNames.matches("io.k8s.api.core.v1.EnvVar", V1.EnvVar.class), is(true));
  }

  @Test
  public void dontMatchOthers() {
    assertThat(KubernetesApiNames.matches("abc", V1EnvVarSource.class), is(false));
    assertThat(
        KubernetesApiNames.matches("io.k8s.api.core.v1.EnvVar", V1EnvVarSource.class), is(false));
  }
}
