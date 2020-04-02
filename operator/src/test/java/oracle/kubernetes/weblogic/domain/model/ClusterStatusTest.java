// Copyright (c) 2020, Oracle Corporation and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.weblogic.domain.model;

import org.junit.Test;

import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.lessThan;
import static org.hamcrest.junit.MatcherAssert.assertThat;

public class ClusterStatusTest {

  static ClusterStatus cluster1 = new ClusterStatus().withClusterName("cluster1");
  static ClusterStatus cluster2 = new ClusterStatus().withClusterName("cluster2");
  static ClusterStatus cluster10 = new ClusterStatus().withClusterName("cluster10");
  static ClusterStatus nullCluster = new ClusterStatus();

  @Test
  public void verify_Equal_compareTo() {
    assertThat(nullCluster.compareTo(nullCluster), equalTo(0));
    assertThat(cluster1.compareTo(cluster1), equalTo(0));
  }

  @Test
  public void verifyThat_cluster1_before_cluster2() {
    assertThat(cluster1.compareTo(cluster2), lessThan(0));
    assertThat(cluster2.compareTo(cluster1), greaterThan(0));
  }

  @Test
  public void verifyThat_cluster1_before_cluster10() {
    assertThat(cluster1.compareTo(cluster10), lessThan(0));
    assertThat(cluster10.compareTo(cluster1), greaterThan(0));
  }

  @Test
  public void verifyThat_cluster2_before_cluster10() {
    assertThat(cluster2.compareTo(cluster10), lessThan(0));
    assertThat(cluster10.compareTo(cluster2), greaterThan(0));
  }

  @Test
  public void verifyThat_nullCluster_before_cluster1() {
    assertThat(nullCluster.compareTo(cluster1), lessThan(0));
    assertThat(cluster1.compareTo(nullCluster), greaterThan(0));
  }
}
