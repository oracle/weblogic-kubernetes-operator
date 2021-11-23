// Copyright (c) 2019, 2021, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.weblogic.domain.model;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

import com.meterware.simplestub.Memento;
import oracle.kubernetes.utils.SystemClock;
import oracle.kubernetes.utils.SystemClockTestSupport;
import org.hamcrest.Description;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static oracle.kubernetes.operator.DomainFailureReason.Internal;
import static oracle.kubernetes.operator.DomainFailureReason.Kubernetes;
import static oracle.kubernetes.operator.WebLogicConstants.SHUTDOWN_STATE;
import static oracle.kubernetes.weblogic.domain.model.DomainConditionMatcher.hasCondition;
import static oracle.kubernetes.weblogic.domain.model.DomainConditionType.Available;
import static oracle.kubernetes.weblogic.domain.model.DomainConditionType.Completed;
import static oracle.kubernetes.weblogic.domain.model.DomainConditionType.ConfigChangesPendingRestart;
import static oracle.kubernetes.weblogic.domain.model.DomainConditionType.Failed;
import static oracle.kubernetes.weblogic.domain.model.DomainConditionType.Progressing;
import static oracle.kubernetes.weblogic.domain.model.DomainStatusTest.ClusterStatusMatcher.clusterStatus;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.Matchers.sameInstance;
import static org.hamcrest.junit.MatcherAssert.assertThat;

class DomainStatusTest {

  private DomainStatus domainStatus;
  private final List<Memento> mementos = new ArrayList<>();

  @BeforeEach
  void setUp() throws Exception {
    mementos.add(SystemClockTestSupport.installClock());

    domainStatus = new DomainStatus();
  }

  @AfterEach
  void tearDown() {
    mementos.forEach(Memento::revert);
  }

  @Test
  void whenCreated_statusHasCreationTime() {
    assertThat(domainStatus.getStartTime(), SystemClockTestSupport.isDuringTest());
  }

  @Test
  void whenConditionAdded_removeAnyObsoleteConditions() {
    domainStatus.addCondition(new DomainCondition(Progressing));

    domainStatus.addCondition(new DomainCondition(ConfigChangesPendingRestart));

    assertThat(domainStatus, not(hasCondition(Progressing)));
  }

  @Test
  void whenAddedConditionEqualsPresentCondition_ignoreIt() {
    DomainCondition originalCondition = new DomainCondition(Failed).withStatus("True");
    domainStatus.addCondition(originalCondition);

    SystemClockTestSupport.increment();
    domainStatus.addCondition(new DomainCondition(Failed).withStatus("True"));

    assertThat(domainStatus.getConditions().get(0), sameInstance(originalCondition));
  }

  @Test
  void whenAddedConditionIsFailed_retainOldFailedCondition() {
    domainStatus.addCondition(new DomainCondition(Failed).withStatus("True").withMessage("problem 1"));
    domainStatus.addCondition(new DomainCondition(Failed).withStatus("True").withMessage("problem 2"));

    assertThat(domainStatus, hasCondition(Failed).withMessage("problem 1"));
    assertThat(domainStatus, hasCondition(Failed).withMessage("problem 2"));
  }

  @Test
  void whenAddedConditionIsAvailable_replaceOldAvailableCondition() {
    domainStatus.addCondition(new DomainCondition(Available).withStatus("False"));

    domainStatus.addCondition(new DomainCondition(Available).withStatus("True"));

    assertThat(domainStatus, hasCondition(Available).withStatus("True"));
    assertThat(domainStatus, not(hasCondition(Available).withStatus("False")));
  }

  @Test
  void whenAddedConditionIsConfigChangesPending_doNotRemoveExistingFailedCondition() {
    domainStatus.addCondition(new DomainCondition(Failed).withStatus("True"));

    domainStatus.addCondition(new DomainCondition(ConfigChangesPendingRestart).withStatus("True"));

    assertThat(domainStatus, hasCondition(Failed));
    assertThat(domainStatus, hasCondition(ConfigChangesPendingRestart));
  }

  @Test
  void whenAddedConditionIsConfigChangesPending_doNotRemoveExistingAvailableCondition() {
    domainStatus.addCondition(new DomainCondition(Available));

    domainStatus.addCondition(new DomainCondition(ConfigChangesPendingRestart).withStatus("True"));

    assertThat(domainStatus, hasCondition(Available));
    assertThat(domainStatus, hasCondition(ConfigChangesPendingRestart));
  }

  @Test
  void beforeConditionAdded_statusFailsPredicate() {
    assertThat(domainStatus.hasConditionWithType(Available), is(false));
  }

  @Test
  void afterConditionAdded_statusPassesPredicate() {
    domainStatus.addCondition(new DomainCondition(Available));

    assertThat(domainStatus.hasConditionWithType(Available), is(true));
  }

  @Test
  void afterFailedConditionAdded_copyMessageAndReasonToStatus() {
    domainStatus.addCondition(new DomainCondition(Failed).withStatus("True").withMessage("msg").withReason(Internal));

    assertThat(domainStatus.getMessage(), equalTo("msg"));
    assertThat(domainStatus.getReason(), equalTo("Internal"));
  }

  @Test
  void mayHaveMultipleFailedConditions_withDifferentReasonsOrMessages() {
    domainStatus.addCondition(new DomainCondition(Failed).withMessage("message1").withReason(Internal));
    domainStatus.addCondition(new DomainCondition(Failed).withMessage("message2").withReason(Kubernetes));

    assertThat(domainStatus.getConditions(), hasSize(2));
  }

  @Test
  void duplicateFailuresAreIgnored() {
    final OffsetDateTime initialTime = SystemClock.now();
    domainStatus.addCondition(new DomainCondition(Failed).withMessage("message").withReason(Internal));

    SystemClockTestSupport.increment();
    domainStatus.addCondition(new DomainCondition(Failed).withMessage("message").withReason(Internal));

    assertThat(domainStatus.getConditions(), hasSize(1));
    assertThat(domainStatus.getConditions().get(0).getLastTransitionTime(), equalTo(initialTime));
  }

  @Test
  void failedConditionsAreListedBeforeNoneFailures() {
    domainStatus.addCondition(new DomainCondition(Failed).withMessage("message1").withReason(Internal));
    domainStatus.addCondition(new DomainCondition(Available).withMessage("message2"));
    domainStatus.addCondition(new DomainCondition(Failed).withMessage("message3").withReason(Internal));

    assertThat(domainStatus.getConditions().get(0).getType(), equalTo(Failed));
    assertThat(domainStatus.getConditions().get(1).getType(), equalTo(Failed));
    assertThat(domainStatus.getConditions().get(2).getType(), equalTo(Available));
  }

  @Test
  void conditionsAddedLater_areListedBeforeEarlierConditions() {
    domainStatus.addCondition(new DomainCondition(Failed).withMessage("message1").withReason(Internal));
    SystemClockTestSupport.increment();
    domainStatus.addCondition(new DomainCondition(Failed).withMessage("message2").withReason(Kubernetes));

    assertThat(domainStatus.getConditions().get(0).getReason(), equalTo("Kubernetes"));
  }

  @Test
  void whenMultipleConditionsHaveReason_domainStatusReasonIsTakeFromTheMostRecentlyAdded() {
    domainStatus.addCondition(new DomainCondition(Failed).withStatus("True").withMessage("m1").withReason(Internal));
    SystemClockTestSupport.increment();
    domainStatus.addCondition(new DomainCondition(Failed).withStatus("True").withMessage("m2").withReason(Kubernetes));

    assertThat(domainStatus.getReason(), equalTo("Kubernetes"));
    assertThat(domainStatus.getMessage(), equalTo("m2"));
  }

  @Test
  void whenEarlierConditionsLackReasonOrMessage_domainStatusMessageIsTakenFromFirstNonNullMessage() {
    domainStatus.addCondition(new DomainCondition(Completed).withStatus("True").withMessage("Got 'em all"));
    domainStatus.addCondition(new DomainCondition(Available).withStatus("True"));

    assertThat(domainStatus.getMessage(), equalTo("Got 'em all"));
    assertThat(domainStatus.getReason(), nullValue());
  }

  @Test
  void whenEarlierConditionsLackStatusTrue_domainStatusMessageIsTakenFromFirstWithStatusTrue() {
    domainStatus.addCondition(new DomainCondition(Completed).withStatus("False").withMessage("Got 'em all"));
    domainStatus.addCondition(new DomainCondition(Available).withStatus("True").withMessage("Got enough"));

    assertThat(domainStatus.getReason(), nullValue());
    assertThat(domainStatus.getMessage(), equalTo("Got enough"));
  }

  @Test
  void whenConditionRemoved_setDomainStatusMessageFromFirstValidRemaining() {
    domainStatus.addCondition(new DomainCondition(Failed).withStatus("True").withMessage("m1").withReason(Internal));
    domainStatus.addCondition(new DomainCondition(Completed).withStatus("True").withMessage("Got 'em all"));
    domainStatus.addCondition(new DomainCondition(Available).withStatus("True"));

    domainStatus.removeConditionWithType(Failed);

    assertThat(domainStatus.getMessage(), equalTo("Got 'em all"));
    assertThat(domainStatus.getReason(), nullValue());
  }

  @Test
  void whenConditionRemovedAndNoOtherHasMessage_setDomainStatusMessageNull() {
    domainStatus.addCondition(new DomainCondition(Failed).withStatus("True").withMessage("m1").withReason(Internal));
    domainStatus.addCondition(new DomainCondition(Completed).withStatus("True"));
    domainStatus.addCondition(new DomainCondition(Available).withStatus("True"));

    domainStatus.removeConditionWithType(Failed);

    assertThat(domainStatus.getMessage(), nullValue());
    assertThat(domainStatus.getReason(), nullValue());
  }

  @Test
  void whenClusterStatusAdded_statusHasClusterStatus() {
    domainStatus.addCluster(new ClusterStatus().withClusterName("cluster1").withReplicas(3));

    assertThat(domainStatus.getClusters(), hasItem(clusterStatus("cluster1").withReplicas(3)));
  }

  @Test
  void whenClusterStatusAdded_remainingClusterStatusesUnaffected() {
    domainStatus.addCluster(new ClusterStatus().withClusterName("cluster1").withReplicas(3));

    domainStatus.addCluster(new ClusterStatus().withClusterName("cluster2").withMaximumReplicas(10));

    assertThat(domainStatus.getClusters(), hasItem(clusterStatus("cluster1").withReplicas(3)));
  }

  @Test
  void whenClusterStatusAdded_matchingClusterStatusesReplaced() {
    domainStatus.addCluster(new ClusterStatus().withClusterName("cluster1").withReplicas(3).withReplicasGoal(5));

    domainStatus.addCluster(new ClusterStatus().withClusterName("cluster1").withMaximumReplicas(10)
        .withMinimumReplicas(2).withReplicasGoal(6));

    assertThat(domainStatus.getClusters(), hasItem(clusterStatus("cluster1").withMaximumReplicas(10)));
    assertThat(domainStatus.getClusters(), hasItem(clusterStatus("cluster1").withReplicasGoal(6)));
    assertThat(domainStatus.getClusters(), hasItem(clusterStatus("cluster1").withMinimumReplicas(2)));
    assertThat(domainStatus.getClusters(), not(hasItem(clusterStatus("cluster1").withReplicas(3))));
    assertThat(domainStatus.getClusters(), not(hasItem(clusterStatus("cluster1").withReplicasGoal(5))));
  }

  @Test
  void whenHasCondition_cloneIsEqual() {
    domainStatus.addCondition(new DomainCondition(Available).withStatus("False"));

    DomainStatus clone = new DomainStatus(this.domainStatus);

    assertThat(clone, equalTo(domainStatus));
  }

  @Test
  void whenHasServerStatusWithHealth_cloneIsEqual() {
    domainStatus.addServer(new ServerStatus().withHealth(new ServerHealth().withOverallHealth("peachy")));

    DomainStatus clone = new DomainStatus(this.domainStatus);

    assertThat(clone, equalTo(domainStatus));
  }

  @Test
  void whenHasServerStatusWithoutHealth_cloneIsEqual() {
    domainStatus.addServer(new ServerStatus().withServerName("myserver"));

    DomainStatus clone = new DomainStatus(this.domainStatus);

    assertThat(clone, equalTo(domainStatus));
  }

  @Test
  void verifyThat_addServers_serverSortedInExpectedOrdering() {
    ServerStatus cluster1Server1 = new ServerStatus().withClusterName("cluster-1").withServerName("cluster1-server1");
    ServerStatus cluster1Server2 = new ServerStatus().withClusterName("cluster-1").withServerName("cluster1-server2");
    ServerStatus cluster2Server1 = new ServerStatus().withClusterName("cluster-2").withServerName("cluster2-server1");
    ServerStatus adminServer = new ServerStatus().withServerName("admin-server").withIsAdminServer(true);
    ServerStatus standAloneServerA = new ServerStatus().withServerName("a");

    domainStatus.addServer(cluster1Server1).addServer(cluster2Server1)
        .addServer(cluster1Server2).addServer(standAloneServerA).addServer(adminServer);

    assertThat(domainStatus.getServers(),
        contains(adminServer, standAloneServerA, cluster1Server1, cluster1Server2, cluster2Server1));
  }

  @Test
  void verifyThat_setServers_serverSortedInExpectedOrdering() {
    ServerStatus cluster1Server1 = createStatus().withClusterName("cluster-1").withServerName("cluster1-server1");
    ServerStatus cluster1Server2 = createStatus().withClusterName("cluster-1").withServerName("cluster1-server2");
    ServerStatus cluster2Server1 = createStatus().withClusterName("cluster-2").withServerName("cluster2-server1");
    ServerStatus adminServer = createStatus().withServerName("admin-server").withIsAdminServer(true);
    ServerStatus standAloneServerA = createStatus().withServerName("a");

    domainStatus.setServers(Arrays.asList(cluster1Server1,
        cluster2Server1, cluster1Server2, standAloneServerA, adminServer));

    assertThat(domainStatus.getServers(),
        contains(adminServer, standAloneServerA, cluster1Server1, cluster1Server2, cluster2Server1));
  }

  private ServerStatus createStatus() {
    return new ServerStatus().withState("a");
  }

  @Test
  void whenMatchingServersExist_setServersUpdatesState() {
    domainStatus.addServer(new ServerStatus().withClusterName("1").withServerName("1").withState("state1"));
    domainStatus.addServer(new ServerStatus().withClusterName("1").withServerName("2").withState("state1"));
    domainStatus.addServer(new ServerStatus().withClusterName("1").withServerName("3").withState("state1"));
    
    domainStatus.setServers(Arrays.asList(
          new ServerStatus().withClusterName("1").withServerName("1").withState("state1"),
          new ServerStatus().withClusterName("1").withServerName("2").withState("state1"),
          new ServerStatus().withServerName("admin").withIsAdminServer(true).withState("state2")
    ));

    assertThat(getServer("1", "1").getState(), equalTo("state1"));
    assertThat(getServer("1", "2").getState(), equalTo("state1"));
    assertThat(getServer(null, "admin").getState(), equalTo("state2"));
  }

  @Test
  void whenSetServerIncludesServerWithoutStateAndNoExistingState_defaultToSHUTDOWN() {
    domainStatus.addServer(new ServerStatus().withClusterName("1").withServerName("1").withState("state1"));
    domainStatus.addServer(new ServerStatus().withClusterName("1").withServerName("2").withState("state1"));
    domainStatus.addServer(new ServerStatus().withClusterName("1").withServerName("3").withState("state1"));

    domainStatus.setServers(Arrays.asList(
          new ServerStatus().withClusterName("1").withServerName("1").withState("state1"),
          new ServerStatus().withClusterName("1").withServerName("2").withState("state1"),
          new ServerStatus().withClusterName("1").withServerName("3").withState("state2"),
          new ServerStatus().withClusterName("2").withServerName("1")
    ));

    assertThat(getServer("2", "1").getState(), equalTo(SHUTDOWN_STATE));
  }

  @Test
  void whenSetServerIncludesServerWithoutStateAndHasExistingState_preserveIt() {
    domainStatus.addServer(new ServerStatus().withClusterName("1").withServerName("1").withState("state1")
        .withHealth(new ServerHealth().withOverallHealth("ok")));
    domainStatus.addServer(new ServerStatus().withClusterName("1").withServerName("2").withState("state1")
        .withHealth(new ServerHealth().withOverallHealth("ok")));
    domainStatus.addServer(new ServerStatus().withClusterName("1").withServerName("3").withState("state1")
        .withHealth(new ServerHealth().withOverallHealth("ok")));

    domainStatus.setServers(Arrays.asList(
        new ServerStatus().withClusterName("1").withServerName("1").withState("state1"),
        new ServerStatus().withClusterName("1").withServerName("2").withState("state1"),
        new ServerStatus().withClusterName("1").withServerName("3")
        ));

    assertThat(getServer("1", "3").getState(), equalTo("state1"));
  }

  private ServerStatus getServer(String clusterName, String serverName) {
    return domainStatus.getServers()
          .stream()
          .filter(s -> Objects.equals(clusterName, s.getClusterName()))
          .filter(s -> Objects.equals(serverName, s.getServerName()))
          .findFirst()
          .orElse(null);
  }

  @Test
  void verifyThat_getServers_serverInExpectedOrdering() {
    ServerStatus cluster1Server1 = new ServerStatus().withClusterName("cluster-1").withServerName("cluster1-server1");
    ServerStatus cluster1Server2 = new ServerStatus().withClusterName("cluster-1").withServerName("cluster1-server2");
    ServerStatus cluster2Server1 = new ServerStatus().withClusterName("cluster-2").withServerName("cluster2-server1");
    ServerStatus adminServer = new ServerStatus().withServerName("admin-server").withIsAdminServer(true);
    ServerStatus standAloneServerA = new ServerStatus().withServerName("a");

    domainStatus.addServer(cluster1Server1).addServer(cluster2Server1)
        .addServer(cluster1Server2).addServer(standAloneServerA).addServer(adminServer);

    List<ServerStatus> serverStatuses = domainStatus.getServers();

    assertThat(serverStatuses,
        contains(adminServer, standAloneServerA, cluster1Server1, cluster1Server2, cluster2Server1));
  }

  @Test
  void verifyThat_addClusters_clustersSortedInExpectedOrdering() {
    ClusterStatus cluster1 = new ClusterStatus().withClusterName("cluster-1");
    ClusterStatus cluster2 = new ClusterStatus().withClusterName("cluster-2");
    ClusterStatus cluster10 = new ClusterStatus().withClusterName("cluster-10");

    domainStatus.addCluster(cluster10).addCluster(cluster1).addCluster(cluster2);

    assertThat(domainStatus.getClusters(), contains(cluster1, cluster2, cluster10));
  }

  @Test
  void verifyThat_setClusters_clustersSortedInExpectedOrdering() {
    ClusterStatus cluster1 = new ClusterStatus().withClusterName("cluster-1");
    ClusterStatus cluster2 = new ClusterStatus().withClusterName("cluster-2");
    ClusterStatus cluster10 = new ClusterStatus().withClusterName("cluster-10");

    domainStatus.setClusters(Arrays.asList(cluster10, cluster1, cluster2));

    assertThat(domainStatus.getClusters(), contains(cluster1, cluster2, cluster10));
  }

  @Test
  void verifyThat_getClusters_clustersInExpectedOrdering() {
    ClusterStatus cluster1 = new ClusterStatus().withClusterName("cluster-1");
    ClusterStatus cluster2 = new ClusterStatus().withClusterName("cluster-2");
    ClusterStatus cluster10 = new ClusterStatus().withClusterName("cluster-10");

    domainStatus.addCluster(cluster10).addCluster(cluster1).addCluster(cluster2);

    List<ClusterStatus> clusterStatuses = domainStatus.getClusters();

    assertThat(clusterStatuses, contains(cluster1, cluster2, cluster10));
  }

  @Test
  void verifyThat_getServers_returnCopyOfServersList() {
    ServerStatus server1 = new ServerStatus().withServerName("server1");
    ServerStatus server2 = new ServerStatus().withServerName("server2");

    domainStatus.addServer(server1);

    List<ServerStatus> serverStatuses = domainStatus.getServers();

    domainStatus.addServer(server2);

    assertThat(serverStatuses.size(), is(equalTo(1)));
  }

  @Test
  void verifyThat_getClusters_returnCopyOfClustersList() {
    ClusterStatus cluster1 = new ClusterStatus().withClusterName("cluster1");
    ClusterStatus cluster2 = new ClusterStatus().withClusterName("cluster2");

    domainStatus.addCluster(cluster1);

    List<ClusterStatus> clusterStatuses = domainStatus.getClusters();

    domainStatus.addCluster(cluster2);

    assertThat(clusterStatuses.size(), is(equalTo(1)));
  }

  @SuppressWarnings("unused")
  static class ClusterStatusMatcher extends org.hamcrest.TypeSafeDiagnosingMatcher<ClusterStatus> {
    private final String name;
    private Integer replicas;
    private Integer maximumReplicas;
    private Integer minimumReplicas;
    private Integer readyReplicas;
    private Integer replicasGoal;

    private ClusterStatusMatcher(String name) {
      this.name = name;
    }

    static ClusterStatusMatcher clusterStatus(String name) {
      return new ClusterStatusMatcher(name);
    }

    @SuppressWarnings("SameParameterValue")
    ClusterStatusMatcher withReplicas(int replicas) {
      this.replicas = replicas;
      return this;
    }

    @SuppressWarnings("SameParameterValue")
    ClusterStatusMatcher withMaximumReplicas(int maximumReplicas) {
      this.maximumReplicas = maximumReplicas;
      return this;
    }

    @SuppressWarnings("SameParameterValue")
    ClusterStatusMatcher withMinimumReplicas(int minimumReplicas) {
      this.minimumReplicas = minimumReplicas;
      return this;
    }

    ClusterStatusMatcher withReplicasGoal(int replicasGoal) {
      this.replicasGoal = replicasGoal;
      return this;
    }

    @Override
    protected boolean matchesSafely(ClusterStatus clusterStatus, Description description) {
      OptionalFieldMatcher matcher = new OptionalFieldMatcher(description);
      matcher.check("clusterName", name, clusterStatus.getClusterName());
      matcher.check("replicas", replicas, clusterStatus.getReplicas());
      matcher.check("maximumReplicas", maximumReplicas, clusterStatus.getMaximumReplicas());
      matcher.check("minimumReplicas", minimumReplicas, clusterStatus.getMinimumReplicas());
      matcher.check("readyReplicas", readyReplicas, clusterStatus.getReadyReplicas());
      matcher.check("replicasGoal", replicasGoal, clusterStatus.getReplicasGoal());

      return matcher.matches;
    }

    @Override
    public void describeTo(Description description) {
      description.appendText("cluster status for ").appendValue(name);
      if (replicas != null) {
        description.appendText(", with " + replicas + " replicas");
      }
      if (maximumReplicas != null) {
        description.appendText(", with " + maximumReplicas + " maximum replicas");
      }
      if (minimumReplicas != null) {
        description.appendText(", with " + minimumReplicas + " minimum replicas");
      }
      if (readyReplicas != null) {
        description.appendText(", with " + readyReplicas + " ready replicas");
      }
      if (replicasGoal != null) {
        description.appendText(", with " + replicasGoal + " requested replicas");
      }
    }
  }

  static class OptionalFieldMatcher {
    private final Description description;
    private boolean matches = true;

    OptionalFieldMatcher(Description description) {
      this.description = description;
    }

    @SuppressWarnings("SameParameterValue")
    void check(String fieldName, String expected, String actual) {
      if (expected == null || expected.equals(actual)) {
        return;
      }

      matches = false;
      description.appendText(fieldName).appendValue(actual);
    }

    void check(String fieldName, Number expected, Number actual) {
      if (expected == null || expected.equals(actual)) {
        return;
      }

      matches = false;
      description.appendText(fieldName).appendValue(actual);
    }


  }

}
