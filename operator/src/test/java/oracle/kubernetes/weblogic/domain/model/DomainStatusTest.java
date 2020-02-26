// Copyright (c) 2019, 2020, Oracle Corporation and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.weblogic.domain.model;

import java.util.ArrayList;
import java.util.List;

import com.meterware.simplestub.Memento;
import oracle.kubernetes.utils.SystemClockTestSupport;
import org.hamcrest.Description;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import static oracle.kubernetes.weblogic.domain.model.DomainConditionMatcher.hasCondition;
import static oracle.kubernetes.weblogic.domain.model.DomainConditionType.Available;
import static oracle.kubernetes.weblogic.domain.model.DomainConditionType.Failed;
import static oracle.kubernetes.weblogic.domain.model.DomainConditionType.Progressing;
import static oracle.kubernetes.weblogic.domain.model.DomainStatusTest.ClusterStatusMatcher.clusterStatus;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.Matchers.sameInstance;
import static org.hamcrest.junit.MatcherAssert.assertThat;

public class DomainStatusTest {

  private DomainStatus domainStatus;
  private List<Memento> mementos = new ArrayList<>();

  /**
   * Setup test.
   * @throws Exception on failure
   */
  @Before
  public void setUp() throws Exception {
    mementos.add(SystemClockTestSupport.installClock());

    domainStatus = new DomainStatus();
  }

  /**
   * Tear down test.
   */
  @After
  public void tearDown() {
    for (Memento memento : mementos) {
      memento.revert();
    }
  }

  @Test
  public void whenCreated_statusHasCreationTime() {
    assertThat(domainStatus.getStartTime(), SystemClockTestSupport.isDuringTest());
  }

  @Test
  public void whenAddedConditionEqualsPresentCondition_ignoreIt() {
    DomainCondition originalCondition = new DomainCondition(Failed).withStatus("True");
    domainStatus.addCondition(originalCondition);

    SystemClockTestSupport.increment();
    domainStatus.addCondition(new DomainCondition(Failed).withStatus("True"));

    assertThat(domainStatus.getConditions().get(0), sameInstance(originalCondition));
  }

  @Test
  public void whenAddedConditionIsFailed_replaceOldFailedCondition() {
    domainStatus.addCondition(new DomainCondition(Failed).withStatus("False"));

    domainStatus.addCondition(new DomainCondition(Failed).withStatus("True"));

    assertThat(domainStatus, hasCondition(Failed).withStatus("True"));
    assertThat(domainStatus, not(hasCondition(Failed).withStatus("False")));
  }

  @Test
  public void whenAddedConditionIsFailed_removeProgressingCondition() {
    domainStatus.addCondition(new DomainCondition(Progressing).withStatus("False"));

    domainStatus.addCondition(new DomainCondition(Failed).withStatus("True"));

    assertThat(domainStatus, not(hasCondition(Progressing)));
    assertThat(domainStatus, hasCondition(Failed).withStatus("True"));
  }

  @Test
  public void whenAddedConditionIsFailed_removeExistingAvailableCondition() {
    domainStatus.addCondition(new DomainCondition(Available).withStatus("False"));

    domainStatus.addCondition(new DomainCondition(Failed).withStatus("True"));

    assertThat(domainStatus, not(hasCondition(Available)));
    assertThat(domainStatus, hasCondition(Failed).withStatus("True"));
  }

  @Test
  public void whenAddedConditionIsAvailable_replaceOldAvailableCondition() {
    domainStatus.addCondition(new DomainCondition(Available).withStatus("False"));

    domainStatus.addCondition(new DomainCondition(Available).withStatus("True"));

    assertThat(domainStatus, hasCondition(Available).withStatus("True"));
    assertThat(domainStatus, not(hasCondition(Available).withStatus("False")));
  }

  @Test
  public void whenAddedConditionIsAvailable_removeExistedProgressingCondition() {
    domainStatus.addCondition(new DomainCondition(Progressing).withStatus("False"));

    domainStatus.addCondition(new DomainCondition(Available).withStatus("True"));

    assertThat(domainStatus, not(hasCondition(Progressing)));
    assertThat(domainStatus, hasCondition(Available).withStatus("True"));
  }

  @Test
  public void whenAddedConditionIsAvailable_removeExistedFailedCondition() {
    domainStatus.addCondition(new DomainCondition(Failed).withStatus("False"));

    domainStatus.addCondition(new DomainCondition(Available).withStatus("True"));

    assertThat(domainStatus, not(hasCondition(Failed)));
    assertThat(domainStatus, hasCondition(Available).withStatus("True"));
  }

  @Test
  public void whenAddedConditionIsProgressing_replaceOldProgressingCondition() {
    domainStatus.addCondition(new DomainCondition(Progressing).withStatus("False"));

    domainStatus.addCondition(new DomainCondition(Progressing).withStatus("True"));

    assertThat(domainStatus, hasCondition(Progressing).withStatus("True"));
    assertThat(domainStatus, not(hasCondition(Progressing).withStatus("False")));
  }

  @Test
  public void whenAddedConditionIsProgressing_leaveExistingAvailableCondition() {
    domainStatus.addCondition(new DomainCondition(Available).withStatus("False"));

    domainStatus.addCondition(new DomainCondition(Progressing).withStatus("True"));

    assertThat(domainStatus, hasCondition(Progressing).withStatus("True"));
    assertThat(domainStatus, hasCondition(Available).withStatus("False"));
  }

  @Test
  public void whenAddedConditionIsProgress_removeExistedFailedCondition() {
    domainStatus.addCondition(new DomainCondition(Failed).withStatus("False"));

    domainStatus.addCondition(new DomainCondition(Progressing).withStatus("True"));

    assertThat(domainStatus, not(hasCondition(Failed)));
    assertThat(domainStatus, hasCondition(Progressing).withStatus("True"));
  }

  @Test
  public void beforeConditionAdded_statusFailsPredicate() {
    assertThat(domainStatus.hasConditionWith(c -> c.hasType(Available)), is(false));
  }

  @Test
  public void afterConditionAdded_statusPassesPredicate() {
    domainStatus.addCondition(new DomainCondition(Available));

    assertThat(domainStatus.hasConditionWith(c -> c.hasType(Available)), is(true));
  }

  @Test
  public void afterFailedConditionAdded_copyMessageAndReasonToStatus() {
    domainStatus.addCondition(new DomainCondition(Failed).withMessage("message").withReason("reason"));

    assertThat(domainStatus.getMessage(), equalTo("message"));
    assertThat(domainStatus.getReason(), equalTo("reason"));
  }

  @Test
  public void afterNonFailedConditionAdded_clearStatusMessageAndReason() {
    domainStatus.setMessage("old message");
    domainStatus.setReason("old reason");

    domainStatus.addCondition(new DomainCondition(Progressing).withMessage("message").withReason("reason"));

    assertThat(domainStatus.getMessage(), nullValue());
    assertThat(domainStatus.getReason(), nullValue());
  }

  @Test
  public void whenClusterStatusAdded_statusHasClusterStatus() {
    domainStatus.addCluster(new ClusterStatus().withClusterName("cluster1").withReplicas(3));

    assertThat(domainStatus.getClusters(), hasItem(clusterStatus("cluster1").withReplicas(3)));
  }

  @Test
  public void whenClusterStatusAdded_remainingClusterStatusesUnaffected() {
    domainStatus.addCluster(new ClusterStatus().withClusterName("cluster1").withReplicas(3));

    domainStatus.addCluster(new ClusterStatus().withClusterName("cluster2").withMaximumReplicas(10));

    assertThat(domainStatus.getClusters(), hasItem(clusterStatus("cluster1").withReplicas(3)));
  }

  @Test
  public void whenClusterStatusAdded_matchingClusterStatusesReplaced() {
    domainStatus.addCluster(new ClusterStatus().withClusterName("cluster1").withReplicas(3));

    domainStatus.addCluster(new ClusterStatus().withClusterName("cluster1").withMaximumReplicas(10));

    assertThat(domainStatus.getClusters(), hasItem(clusterStatus("cluster1").withMaximumReplicas(10)));
    assertThat(domainStatus.getClusters(), not(hasItem(clusterStatus("cluster1").withReplicas(3))));
  }

  @Test
  public void whenHasCondition_cloneIsEqual() {
    domainStatus.addCondition(new DomainCondition(Progressing).withStatus("False"));

    DomainStatus clone = new DomainStatus(this.domainStatus);

    assertThat(clone, equalTo(domainStatus));
  }

  @Test
  public void whenHasServerStatusWithHealth_cloneIsEqual() {
    domainStatus.addServer(new ServerStatus().withHealth(new ServerHealth().withOverallHealth("peachy")));

    DomainStatus clone = new DomainStatus(this.domainStatus);

    assertThat(clone, equalTo(domainStatus));
  }

  @Test
  public void whenHasServerStatusWithoutHealth_cloneIsEqual() {
    domainStatus.addServer(new ServerStatus().withServerName("myserver"));

    DomainStatus clone = new DomainStatus(this.domainStatus);

    assertThat(clone, equalTo(domainStatus));
  }

  static class ClusterStatusMatcher extends org.hamcrest.TypeSafeDiagnosingMatcher<ClusterStatus> {
    private String name;
    private Integer replicas;
    private Integer maximumReplicas;
    private Integer readyReplicas;

    private ClusterStatusMatcher(String name) {
      this.name = name;
    }

    static ClusterStatusMatcher clusterStatus(String name) {
      return new ClusterStatusMatcher(name);
    }

    ClusterStatusMatcher withReplicas(int replicas) {
      this.replicas = replicas;
      return this;
    }

    ClusterStatusMatcher withMaximumReplicas(int maximumReplicas) {
      this.maximumReplicas = maximumReplicas;
      return this;
    }

    ClusterStatusMatcher withReadyReplicas(int readyReplicas) {
      this.readyReplicas = readyReplicas;
      return this;
    }

    @Override
    protected boolean matchesSafely(ClusterStatus clusterStatus, Description description) {
      OptionalFieldMatcher matcher = new OptionalFieldMatcher(description);
      matcher.check("clusterName", name, clusterStatus.getClusterName());
      matcher.check("replicas", replicas, clusterStatus.getReplicas());
      matcher.check("maximumReplicas", maximumReplicas, clusterStatus.getMaximumReplicas());
      matcher.check("readyReplicas", readyReplicas, clusterStatus.getReadyReplicas());

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
      if (readyReplicas != null) {
        description.appendText(", with " + readyReplicas + " ready replicas");
      }
    }
  }

  static class OptionalFieldMatcher {
    private Description description;
    private boolean matches = true;

    OptionalFieldMatcher(Description description) {
      this.description = description;
    }

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
