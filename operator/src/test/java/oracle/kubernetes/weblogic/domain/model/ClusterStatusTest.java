// Copyright (c) 2020, 2021, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.weblogic.domain.model;

import org.junit.jupiter.api.Test;

import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.lessThan;
import static org.hamcrest.junit.MatcherAssert.assertThat;

public class ClusterStatusTest {

  static final ClusterStatus cluster1 = new ClusterStatus().withClusterName("cluster1");
  static final ClusterStatus cluster2 = new ClusterStatus().withClusterName("cluster2");
  static final ClusterStatus cluster10 = new ClusterStatus().withClusterName("cluster10");
  static final ClusterStatus nullCluster = new ClusterStatus();

  @Test
  public void verify_Equal_compareTo() {
    assertThat("compareTo should return 0 if both Cluster have null cluster name",
        nullCluster.compareTo(new ClusterStatus()), equalTo(0));
    assertThat("compareTo should return 0 if both ClusterStatus have same cluster name",
        cluster1.compareTo(new ClusterStatus().withClusterName("cluster1")), equalTo(0));
  }

  @Test
  public void verifyThat_cluster1_before_cluster2() {
    assertThat("ClusterStatus for cluster1 should be ordered before ClusterStatus for cluster2",
        cluster1.compareTo(cluster2), lessThan(0));
    assertThat("ClusterStatus for cluster2 should be ordered after ClusterStatus for cluster1",
        cluster2.compareTo(cluster1), greaterThan(0));
  }

  @Test
  public void verifyThat_cluster1_before_cluster10() {
    assertThat("ClusterStatus for cluster1 should be ordered before ClusterStatus for cluster10",
        cluster1.compareTo(cluster10), lessThan(0));
    assertThat("ClusterStatus for cluster10 should be ordered after ClusterStatus for cluster1",
        cluster10.compareTo(cluster1), greaterThan(0));
  }

  @Test
  public void verifyThat_cluster2_before_cluster10() {
    assertThat("ClusterStatus for cluster2 should be ordered before ClusterStatus for cluster10",
        cluster2.compareTo(cluster10), lessThan(0));
    assertThat("ClusterStatus for cluster10 should be ordered after ClusterStatus for cluster2",
        cluster10.compareTo(cluster2), greaterThan(0));
  }

  @Test
  public void verifyThat_nullCluster_before_cluster1() {
    assertThat("ClusterStatus without cluster name should be ordered before ClusterStatus for cluster1",
        nullCluster.compareTo(cluster1), lessThan(0));
    assertThat("ClusterStatus for cluster1 should be ordered after ClusterStatus without cluster name",
        cluster1.compareTo(nullCluster), greaterThan(0));
  }
}
