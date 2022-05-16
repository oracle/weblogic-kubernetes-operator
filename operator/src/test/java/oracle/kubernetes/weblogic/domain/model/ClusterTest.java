// Copyright (c) 2018, 2021, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.weblogic.domain.model;

import org.junit.jupiter.api.Test;

import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.junit.MatcherAssert.assertThat;

class ClusterTest extends BaseConfigurationTestBase {
  private final ClusterSpec cluster1;
  private final ClusterSpec cluster2;

  /**
   * Construct cluster test.
   */
  public ClusterTest() {
    super(new ClusterSpec(), new ClusterSpec());
    cluster1 = getInstance1();
    cluster2 = getInstance2();
  }

  @Test
  void whenNamesAreTheSame_objectsAreEqual() {
    cluster1.setClusterName("one");
    cluster2.setClusterName("one");

    assertThat(cluster1, equalTo(cluster2));
  }

  @Test
  void whenNamesDiffer_objectsAreNotEqual() {
    cluster1.setClusterName("one");
    cluster2.setClusterName("two");

    assertThat(cluster1, not(equalTo(cluster2)));
  }

  @Test
  void whenReplicasAreTheSame_objectsAreEqual() {
    cluster1.setReplicas(3);
    cluster2.setReplicas(3);

    assertThat(cluster1, equalTo(cluster2));
  }

  @Test
  void whenReplicasDiffer_objectsAreNotEqual() {
    cluster1.setReplicas(3);

    assertThat(cluster1, not(equalTo(cluster2)));
  }
}
