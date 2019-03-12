// Copyright 2019, Oracle Corporation and/or its affiliates.  All rights reserved.
// Licensed under the Universal Permissive License v 1.0 as shown at
// http://oss.oracle.com/licenses/upl.

package oracle.kubernetes.weblogic.domain.model;

import static oracle.kubernetes.utils.SystemClockTestSupport.isDuringTest;
import static oracle.kubernetes.weblogic.domain.model.DomainConditionMatcher.hasCondition;
import static oracle.kubernetes.weblogic.domain.model.DomainConditionType.Available;
import static oracle.kubernetes.weblogic.domain.model.DomainConditionType.Progressing;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.junit.MatcherAssert.assertThat;

import com.meterware.simplestub.Memento;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import oracle.kubernetes.utils.SystemClockTestSupport;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class DomainStatusTest {

  private DomainStatus domainStatus;
  private List<Memento> mementos = new ArrayList<>();

  @Before
  public void setUp() throws Exception {
    mementos.add(SystemClockTestSupport.installClock());

    domainStatus = new DomainStatus();
  }

  @After
  public void tearDown() {
    for (Memento memento : mementos) memento.revert();
  }

  @Test
  public void whenCreated_statusHasCreationTime() {
    assertThat(domainStatus.getStartTime(), isDuringTest());
  }

  @Test
  public void whenDomainCreated_isNotModified() {
    assertThat(domainStatus.isModified(), is(false));
  }

  @Test
  public void whenFirstConditionAdded_statusIsModified() {
    domainStatus.addCondition(new DomainCondition(Available));

    assertThat(domainStatus.isModified(), is(true));
  }

  @Test
  public void whenAddedConditionIsSameTypeAsExisting_oldConditionIsReplaced() {
    domainStatus.addCondition(new DomainCondition(Available).withStatus("False"));

    domainStatus.addCondition(new DomainCondition(Available).withStatus("True"));

    assertThat(domainStatus, hasCondition(Available).withStatus("True"));
    assertThat(domainStatus, not(hasCondition(Available).withStatus("False")));
  }

  @Test
  public void whenAddedConditionIsDifferentTypeThenExisting_oldConditionIsNotReplaced() {
    domainStatus.addCondition(new DomainCondition(Available).withStatus("True"));

    domainStatus.addCondition(new DomainCondition(Progressing).withStatus("False"));

    assertThat(domainStatus, hasCondition(Available).withStatus("True"));
    assertThat(domainStatus, hasCondition(Progressing).withStatus("False"));
  }

  @Test
  public void whenConditionIsReplaced_statusIsModifiedOnAdd() {
    domainStatus.addCondition(new DomainCondition(Available).withStatus("False"));
    domainStatus.clearModified();

    domainStatus.addCondition(new DomainCondition(Available).withStatus("True"));

    assertThat(domainStatus.isModified(), is(true));
  }

  @Test
  public void whenNewConditionEqualsExisting_statusIsNotModifiedOnAdd() {
    domainStatus.addCondition(new DomainCondition(Available).withStatus("True"));
    domainStatus.clearModified();

    domainStatus.addCondition(new DomainCondition(Available).withStatus("True"));

    assertThat(domainStatus.isModified(), is(false));
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
  public void whenSetServersEqualIgnoringOrder_originalStatusIsNotModified() {
    ServerStatus server1 = new ServerStatus().withServerName("aa").withState("RUNNING");
    ServerStatus server2 = new ServerStatus().withServerName("bb").withState("RUNNING");
    ServerStatus server3 = new ServerStatus().withServerName("cc").withState("RUNNING");
    domainStatus.setServers(Arrays.asList(server1, server2, server3));
    domainStatus.clearModified();

    domainStatus.setServers(Arrays.asList(server2, server3, server1));

    assertThat(domainStatus.isModified(), is(false));
  }

  @Test
  public void whenSetServersNotEquals_originalStatusIsModified() {
    ServerStatus server1 = new ServerStatus().withServerName("aa").withState("RUNNING");
    ServerStatus server2 = new ServerStatus().withServerName("bb").withState("RUNNING");
    ServerStatus server3 = new ServerStatus().withServerName("cc").withState("RUNNING");
    domainStatus.setServers(Arrays.asList(server1, server2, server3));
    domainStatus.clearModified();

    domainStatus.setServers(
        Arrays.asList(server2, server3, new ServerStatus().withServerName("aa").withState("STOP")));

    assertThat(domainStatus.isModified(), is(true));
  }
}
