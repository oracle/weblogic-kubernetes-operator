// Copyright (c) 2022, 2023, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.weblogic.domain.model;

import io.kubernetes.client.openapi.models.V1ObjectMeta;
import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;

class ClusterResourceTest {

  private final ClusterResource resource = new ClusterResource().spec(new ClusterSpec());

  @Test
  void whenResourceInitialized_hasCorrectApiVersionAndKind() {
    assertThat(resource.getApiVersion(), equalTo("weblogic.oracle/v1"));
    assertThat(resource.getKind(), equalTo("Cluster"));
  }

  @Test
  void canReadReplicaCount() {
    resource.spec(new ClusterSpec().withReplicas(2));

    assertThat(resource.getReplicas(), equalTo(2));
  }

  @Test
  void canSetReplicaCount() {
    resource.setReplicas(5);

    assertThat(resource.getSpec().getReplicas(), equalTo(5));
  }

  @Test
  void canReadClusterNameFromSpec() {
    resource.spec(new ClusterSpec().withClusterName("cluster-1"));

    assertThat(resource.getClusterName(), equalTo("cluster-1"));
  }

  @Test
  void canReadClusterResourceNameFromMetadata() {
    resource.setMetadata(new V1ObjectMeta().name("cluster-2"));

    assertThat(resource.getClusterResourceName(), equalTo("cluster-2"));
  }
}
