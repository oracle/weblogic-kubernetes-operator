// Copyright (c) 2019, Oracle Corporation and/or its affiliates.  All rights reserved.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.weblogic.domain.model;

import java.util.ArrayList;
import java.util.List;

import com.meterware.simplestub.Memento;
import oracle.kubernetes.utils.SystemClockTestSupport;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import static oracle.kubernetes.weblogic.domain.model.DomainConditionMatcher.hasCondition;
import static oracle.kubernetes.weblogic.domain.model.DomainConditionType.Available;
import static oracle.kubernetes.weblogic.domain.model.DomainConditionType.Progressing;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.junit.MatcherAssert.assertThat;

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
    assertThat(domainStatus.getStartTime(), SystemClockTestSupport.isDuringTest());
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
  public void beforeConditionAdded_statusFailsPredicate() {
    assertThat(domainStatus.hasConditionWith(c -> c.hasType(Available)), is(false));
  }

  @Test
  public void afterConditionAdded_statusPassesPredicate() {
    domainStatus.addCondition(new DomainCondition(Available));

    assertThat(domainStatus.hasConditionWith(c -> c.hasType(Available)), is(true));
  }
}
