// Copyright (c) 2020, 2021, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.weblogic.domain.model;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

import com.meterware.simplestub.Memento;
import oracle.kubernetes.utils.SystemClockTestSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.lessThan;
import static org.hamcrest.junit.MatcherAssert.assertThat;

class ClusterStatusTest {

  static final ClusterStatus cluster1 = new ClusterStatus().withClusterName("cluster1");
  static final ClusterStatus cluster2 = new ClusterStatus().withClusterName("cluster2");
  static final ClusterStatus cluster10 = new ClusterStatus().withClusterName("cluster10");
  static final ClusterStatus nullCluster = new ClusterStatus();
  private final List<Memento> mementos = new ArrayList<>();

  @BeforeEach
  void setUp() throws Exception {
    mementos.add(SystemClockTestSupport.installClock());
  }

  @AfterEach
  void tearDown() {
    mementos.forEach(Memento::revert);
  }

  @Test
  void verify_Equal_compareTo() {
    assertThat("compareTo should return 0 if both Cluster have null cluster name",
        nullCluster.compareTo(new ClusterStatus()), equalTo(0));
    assertThat("compareTo should return 0 if both ClusterStatus have same cluster name",
        cluster1.compareTo(new ClusterStatus().withClusterName("cluster1")), equalTo(0));
  }

  @Test
  void verifyThat_cluster1_before_cluster2() {
    assertThat("ClusterStatus for cluster1 should be ordered before ClusterStatus for cluster2",
        cluster1.compareTo(cluster2), lessThan(0));
    assertThat("ClusterStatus for cluster2 should be ordered after ClusterStatus for cluster1",
        cluster2.compareTo(cluster1), greaterThan(0));
  }

  @Test
  void verifyThat_cluster1_before_cluster10() {
    assertThat("ClusterStatus for cluster1 should be ordered before ClusterStatus for cluster10",
        cluster1.compareTo(cluster10), lessThan(0));
    assertThat("ClusterStatus for cluster10 should be ordered after ClusterStatus for cluster1",
        cluster10.compareTo(cluster1), greaterThan(0));
  }

  @Test
  void verifyThat_cluster2_before_cluster10() {
    assertThat("ClusterStatus for cluster2 should be ordered before ClusterStatus for cluster10",
        cluster2.compareTo(cluster10), lessThan(0));
    assertThat("ClusterStatus for cluster10 should be ordered after ClusterStatus for cluster2",
        cluster10.compareTo(cluster2), greaterThan(0));
  }

  @Test
  void verifyThat_nullCluster_before_cluster1() {
    assertThat("ClusterStatus without cluster name should be ordered before ClusterStatus for cluster1",
        nullCluster.compareTo(cluster1), lessThan(0));
    assertThat("ClusterStatus for cluster1 should be ordered after ClusterStatus without cluster name",
        cluster1.compareTo(nullCluster), greaterThan(0));
  }

  @Test
  void verifyAdd_duplicateCondition_hasOneEntry() {
    ClusterCondition condition = new ClusterCondition(ClusterConditionType.AVAILABLE)
        .withStatus(ClusterCondition.FALSE);
    final OffsetDateTime initialTime = condition.getLastTransitionTime();
    SystemClockTestSupport.increment();
    ClusterCondition newCondition = new ClusterCondition(ClusterConditionType.AVAILABLE)
        .withStatus(ClusterCondition.FALSE);
    cluster1.addCondition(condition);
    cluster1.addCondition(newCondition);

    assertThat(cluster1.getConditions().size(), equalTo(1));
    assertThat(cluster1.getConditions().get(0), equalTo(condition));
    assertThat(cluster1.getConditions().get(0).getLastTransitionTime(), equalTo(initialTime));
  }

  @Test
  void verifyAdd_updatedCondition_hasUpdatedEntry() {
    ClusterCondition condition = new ClusterCondition(ClusterConditionType.AVAILABLE)
        .withStatus(ClusterCondition.FALSE);
    ClusterCondition newCondition = new ClusterCondition(ClusterConditionType.AVAILABLE)
        .withStatus(ClusterCondition.TRUE);
    cluster1.addCondition(condition);
    cluster1.addCondition(newCondition);

    assertThat(cluster1.getConditions().size(), equalTo(1));
    assertThat(cluster1.getConditions().get(0), equalTo(newCondition));
  }

  @Test
  void verifyAdd_multipleConditions_areSorted() {
    ClusterCondition availableCondition = new ClusterCondition(ClusterConditionType.AVAILABLE)
        .withStatus(ClusterCondition.TRUE);
    ClusterCondition completedCondition = new ClusterCondition(ClusterConditionType.COMPLETED)
        .withStatus(ClusterCondition.FALSE);
    List<ClusterCondition> list = new ArrayList<>();
    list.add(availableCondition);
    list.add(completedCondition);
    cluster1.addCondition(completedCondition);
    cluster1.addCondition(availableCondition);

    assertThat(cluster1.getConditions().size(), equalTo(2));
    assertThat(cluster1.getConditions(), equalTo(list));
  }
}
