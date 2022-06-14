// Copyright (c) 2018, 2021, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.weblogic.domain.model;

import org.junit.jupiter.api.Test;

import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.junit.MatcherAssert.assertThat;

class ClusterSpecTest extends BaseConfigurationTestBase {
  private final ClusterSpec clusterSpec1;
  private final ClusterSpec clusterSpec2;

  /**
   * Construct cluster test.
   */
  public ClusterSpecTest() {
    super(new ClusterSpec(), new ClusterSpec());
    clusterSpec1 = getInstance1();
    clusterSpec2 = getInstance2();
  }

  @Test
  void whenNamesAreTheSame_objectsAreEqual() {
    clusterSpec1.setClusterName("one");
    clusterSpec2.setClusterName("one");

    assertThat(clusterSpec1, equalTo(clusterSpec2));
  }

  @Test
  void whenNamesDiffer_objectsAreNotEqual() {
    clusterSpec1.setClusterName("one");
    clusterSpec2.setClusterName("two");

    assertThat(clusterSpec1, not(equalTo(clusterSpec2)));
  }

  @Test
  void whenReplicasAreTheSame_objectsAreEqual() {
    clusterSpec1.setReplicas(3);
    clusterSpec2.setReplicas(3);

    assertThat(clusterSpec1, equalTo(clusterSpec2));
  }

  @Test
  void whenReplicasDiffer_objectsAreNotEqual() {
    clusterSpec1.setReplicas(3);

    assertThat(clusterSpec1, not(equalTo(clusterSpec2)));
  }
}
