// Copyright 2018, Oracle Corporation and/or its affiliates.  All rights reserved.
// Licensed under the Universal Permissive License v 1.0 as shown at
// http://oss.oracle.com/licenses/upl.

package oracle.kubernetes.weblogic.domain.model;

import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.junit.MatcherAssert.assertThat;

import org.junit.Test;

public class ClusterTest extends BaseConfigurationTestBase {
  private final Cluster cluster1;
  private final Cluster cluster2;

  public ClusterTest() {
    super(new Cluster(), new Cluster());
    cluster1 = getInstance1();
    cluster2 = getInstance2();
  }

  @Test
  public void whenNamesAreTheSame_objectsAreEqual() {
    cluster1.setClusterName("one");
    cluster2.setClusterName("one");

    assertThat(cluster1, equalTo(cluster2));
  }

  @Test
  public void whenNamesDiffer_objectsAreNotEqual() {
    cluster1.setClusterName("one");
    cluster2.setClusterName("two");

    assertThat(cluster1, not(equalTo(cluster2)));
  }

  @Test
  public void whenReplicasAreTheSame_objectsAreEqual() {
    cluster1.setReplicas(3);
    cluster2.setReplicas(3);

    assertThat(cluster1, equalTo(cluster2));
  }

  @Test
  public void whenReplicasDiffer_objectsAreNotEqual() {
    cluster1.setReplicas(3);

    assertThat(cluster1, not(equalTo(cluster2)));
  }
}
