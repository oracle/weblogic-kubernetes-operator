// Copyright (c) 2020, Oracle Corporation and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.weblogic.domain.model;

import org.junit.Test;

import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.lessThan;
import static org.hamcrest.junit.MatcherAssert.assertThat;

public class ServerStatusTest {

  static ServerStatus cluster1Server1 = new ServerStatus().withClusterName("cluster-1").withServerName("server1");
  static ServerStatus cluster1Server2 = new ServerStatus().withClusterName("cluster-1").withServerName("server2");
  static ServerStatus cluster1Server10 = new ServerStatus().withClusterName("cluster-1").withServerName("server10");

  static ServerStatus cluster2Server1 = new ServerStatus().withClusterName("cluster-2").withServerName("server1");
  static ServerStatus cluster10Server1 = new ServerStatus().withClusterName("cluster-10").withServerName("server1");

  static ServerStatus standAloneServer1 = new ServerStatus().withServerName("server1");
  static ServerStatus standAloneServer2 = new ServerStatus().withServerName("server2");
  static ServerStatus standAloneServer10 = new ServerStatus().withServerName("server10");

  static ServerStatus nullClusterNullServer = new ServerStatus();

  @Test
  public void verify_Equal_compareTo() {
    assertThat(nullClusterNullServer.compareTo(nullClusterNullServer), equalTo(0));
    assertThat(cluster1Server1.compareTo(cluster1Server1), equalTo(0));
    assertThat(standAloneServer1.compareTo(standAloneServer1), equalTo(0));
  }

  @Test
  public void verifyThat_statusWithoutCluster_before_statusWithCluster() {
    assertThat(standAloneServer1.compareTo(cluster1Server1), lessThan(0));
    assertThat(cluster1Server1.compareTo(standAloneServer2), greaterThan(0));
  }

  @Test
  public void verifyThat_cluster1_before_cluster2() {
    assertThat(cluster1Server1.compareTo(cluster2Server1), lessThan(0));
    assertThat(cluster2Server1.compareTo(cluster1Server1), greaterThan(0));
  }

  @Test
  public void verifyThat_cluster1_before_cluster10() {
    assertThat(cluster1Server1.compareTo(cluster10Server1), lessThan(0));
    assertThat(cluster10Server1.compareTo(cluster1Server1), greaterThan(0));
  }

  @Test
  public void verifyThat_cluster2_before_cluster10() {
    assertThat(cluster2Server1.compareTo(cluster10Server1), lessThan(0));
    assertThat(cluster10Server1.compareTo(cluster2Server1), greaterThan(0));
  }

  @Test
  public void verifyThat_server1_before_server2() {
    assertThat(standAloneServer1.compareTo(standAloneServer2), lessThan(0));
    assertThat(standAloneServer2.compareTo(standAloneServer1), greaterThan(0));

    assertThat(cluster1Server1.compareTo(cluster1Server2), lessThan(0));
    assertThat(cluster1Server2.compareTo(cluster1Server1), greaterThan(0));
  }

  @Test
  public void verifyThat_server1_before_server10() {
    assertThat(standAloneServer1.compareTo(standAloneServer10), lessThan(0));
    assertThat(standAloneServer10.compareTo(standAloneServer1), greaterThan(0));

    assertThat(cluster1Server1.compareTo(cluster1Server10), lessThan(0));
    assertThat(cluster1Server10.compareTo(cluster1Server1), greaterThan(0));
  }

  @Test
  public void verifyThat_server2_before_server10() {
    assertThat(standAloneServer2.compareTo(standAloneServer10), lessThan(0));
    assertThat(standAloneServer10.compareTo(standAloneServer2), greaterThan(0));

    assertThat(cluster1Server2.compareTo(cluster1Server10), lessThan(0));
    assertThat(cluster1Server10.compareTo(cluster1Server2), greaterThan(0));
  }

}
