// Copyright (c) 2020, 2021, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.weblogic.domain.model;

import org.junit.jupiter.api.Test;

import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.lessThan;
import static org.hamcrest.junit.MatcherAssert.assertThat;

public class ServerStatusTest {

  static final ServerStatus cluster1Server1
      = new ServerStatus().withClusterName("cluster-1").withServerName("server1");
  static final ServerStatus cluster1Server2 = new ServerStatus().withClusterName("cluster-1").withServerName("server2");
  static final ServerStatus cluster1Server10
      = new ServerStatus().withClusterName("cluster-1").withServerName("server10");

  static final ServerStatus cluster2Server1 = new ServerStatus().withClusterName("cluster-2").withServerName("server1");
  static final ServerStatus cluster10Server1
      = new ServerStatus().withClusterName("cluster-10").withServerName("server1");

  static final ServerStatus standAloneServer1 = new ServerStatus().withServerName("server1");
  static final ServerStatus standAloneServer2 = new ServerStatus().withServerName("server2");
  static final ServerStatus standAloneServer10 = new ServerStatus().withServerName("server10");

  static final ServerStatus adminServer = new ServerStatus().withServerName("admin-server").withIsAdminServer(true);
  static final ServerStatus cluster1ServerA = new ServerStatus().withClusterName("cluster-1").withServerName("a");
  static final ServerStatus standAloneServerA = new ServerStatus().withServerName("a");

  static final ServerStatus nullClusterNullServer = new ServerStatus();

  @Test
  public void verify_Equal_compareTo() {
    assertThat("compareTo should return 0 if both ServerStatus have null cluster and server names",
        nullClusterNullServer.compareTo(new ServerStatus()), equalTo(0));
    assertThat("compareTo should return 0 if both ServerStatus have same cluster and server names",
        cluster1Server1.compareTo(new ServerStatus().withClusterName("cluster-1").withServerName("server1")),
        equalTo(0));
    assertThat("compareTo should return 0 if both ServerStatus have null cluster name, and same server name",
        standAloneServer1.compareTo(new ServerStatus().withServerName("server1")), equalTo(0));
    assertThat("compareTo should return 0 if both ServerStatus are for admin server with same server name",
        adminServer.compareTo(new ServerStatus().withServerName("admin-server").withIsAdminServer(true)),
        equalTo(0));
  }

  @Test
  public void verifyThat_adminServer_before_notAdminServerWithSameServerName() {
    assertThat("ServerStatus for admin server should be ordered before ServerStatus for non admin server",
        adminServer.compareTo(new ServerStatus().withServerName("admin-server")), equalTo(-1));
    assertThat("ServerStatus for non admin server should be ordered after ServerStatus for admin server",
        new ServerStatus().withServerName("admin-server").compareTo(adminServer), equalTo(1));
  }

  @Test
  public void verifyThat_statusWithoutCluster_before_statusWithCluster() {
    assertThat("ServerStatus without cluster name should be ordered before ServerStatus with cluster name",
        standAloneServer1.compareTo(cluster1Server1), lessThan(0));
    assertThat("ServerStatus with cluster name should be ordered after ServerStatus without cluster name",
        cluster1Server1.compareTo(standAloneServer2), greaterThan(0));
  }

  @Test
  public void verifyThat_cluster1_before_cluster2() {
    assertThat("ServerStatus for cluster-1 should be ordered before ServerStatus for cluster-2",
        cluster1Server1.compareTo(cluster2Server1), lessThan(0));
    assertThat("ServerStatus for cluster-2 should be ordered after ServerStatus for cluster-1",
        cluster2Server1.compareTo(cluster1Server1), greaterThan(0));
  }

  @Test
  public void verifyThat_cluster1_before_cluster10() {
    assertThat("ServerStatus for cluster-1 should be ordered before ServerStatus for cluster-10",
        cluster1Server1.compareTo(cluster10Server1), lessThan(0));
    assertThat("ServerStatus for cluster-10 should be ordered after ServerStatus for cluster-1",
        cluster10Server1.compareTo(cluster1Server1), greaterThan(0));
  }

  @Test
  public void verifyThat_cluster2_before_cluster10() {
    assertThat("ServerStatus for cluster-2 should be ordered before ServerStatus for cluster-10",
        cluster2Server1.compareTo(cluster10Server1), lessThan(0));
    assertThat("ServerStatus for cluster-10 should be ordered after ServerStatus for cluster-2",
        cluster10Server1.compareTo(cluster2Server1), greaterThan(0));
  }

  @Test
  public void verifyThat_server1_before_server2() {
    assertThat("ServerStatus for server1 should be ordered before ServerStatus for server2",
        standAloneServer1.compareTo(standAloneServer2), lessThan(0));
    assertThat("ServerStatus for server2 should be ordered after ServerStatus for server1",
        standAloneServer2.compareTo(standAloneServer1), greaterThan(0));

    assertThat("ServerStatus for server1 should be ordered before ServerStatus for server2 in same cluster",
        cluster1Server1.compareTo(cluster1Server2), lessThan(0));
    assertThat("ServerStatus for server2 should be ordered after ServerStatus for server1 in same cluster",
        cluster1Server2.compareTo(cluster1Server1), greaterThan(0));
  }

  @Test
  public void verifyThat_server1_before_server10() {
    assertThat("ServerStatus for server1 should be ordered before ServerStatus for server10",
        standAloneServer1.compareTo(standAloneServer10), lessThan(0));
    assertThat("ServerStatus for server10 should be ordered after ServerStatus for server1",
        standAloneServer10.compareTo(standAloneServer1), greaterThan(0));

    assertThat("ServerStatus for server1 should be ordered before ServerStatus for server10 in same cluster",
        cluster1Server1.compareTo(cluster1Server10), lessThan(0));
    assertThat("ServerStatus for server10 should be ordered after ServerStatus for server1 in same cluster",
        cluster1Server10.compareTo(cluster1Server1), greaterThan(0));
  }

  @Test
  public void verifyThat_server2_before_server10() {
    assertThat("ServerStatus for server2 should be ordered before ServerStatus for server10",
        standAloneServer2.compareTo(standAloneServer10), lessThan(0));
    assertThat("ServerStatus for server10 should be ordered after ServerStatus for server2",
        standAloneServer10.compareTo(standAloneServer2), greaterThan(0));

    assertThat("ServerStatus for server2 should be ordered before ServerStatus for server10 in same cluster",
        cluster1Server2.compareTo(cluster1Server10), lessThan(0));
    assertThat("ServerStatus for server10 should be ordered after ServerStatus for server2 in same cluster",
        cluster1Server10.compareTo(cluster1Server2), greaterThan(0));
  }

  @Test
  public void verifyThat_adminServer_before_serverA() {
    assertThat("ServerStatus for admin server should be ordered before ServerStatus for non admin server",
        adminServer.compareTo(standAloneServerA), lessThan(0));
    assertThat("ServerStatus for non admin server should be ordered after ServerStatus for admin server",
        standAloneServerA.compareTo(adminServer), greaterThan(0));

    assertThat("ServerStatus for admin server should be ordered before ServerStatus for non admin server in a cluster",
        adminServer.compareTo(cluster1ServerA), lessThan(0));
    assertThat("ServerStatus for non admin server in a cluster should be ordered after ServerStatus for admin server",
        cluster1ServerA.compareTo(adminServer), greaterThan(0));
  }

  // We use the volatile adminServer flag to control sorting, but it is not part of the JSON schema of the status,
  // therefore it cannot figure in the equals() test, which is used to decide whether we need to update the status.
  @Test
  public void equalsMethodsIgnoresIsAdminServer() {
    assertThat(
          new ServerStatus().withClusterName("1").withServerName("1"),
          equalTo(new ServerStatus().withClusterName("1").withServerName("1").withIsAdminServer(true)));
  }
}
