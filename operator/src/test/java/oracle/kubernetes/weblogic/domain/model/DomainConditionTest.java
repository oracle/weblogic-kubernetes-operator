// Copyright (c) 2019, Oracle Corporation and/or its affiliates.  All rights reserved.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.weblogic.domain.model;

import java.util.ArrayList;
import java.util.List;

import com.meterware.simplestub.Memento;
import oracle.kubernetes.utils.SystemClockTestSupport;
import oracle.kubernetes.weblogic.domain.model.DomainCondition;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import static oracle.kubernetes.weblogic.domain.model.DomainConditionType.Available;
import static oracle.kubernetes.weblogic.domain.model.DomainConditionType.Failed;
import static oracle.kubernetes.weblogic.domain.model.DomainConditionType.Progressing;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.junit.MatcherAssert.assertThat;

public class DomainConditionTest {

  private List<Memento> mementos = new ArrayList<>();

  @Before
  public void setUp() throws Exception {
    mementos.add(SystemClockTestSupport.installClock());
  }

  @After
  public void tearDown() {
    for (Memento memento : mementos) memento.revert();
  }

  @Test
  public void whenCreated_conditionHasLastTransitionTime() {
    assertThat(new DomainCondition(Available).getLastTransitionTime(), SystemClockTestSupport.isDuringTest());
  }

  @Test
  public void predicateDetectsType() {
    assertThat(new DomainCondition(Failed).hasType(Failed), is(true));
    assertThat(new DomainCondition(Progressing).hasType(Available), is(false));
  }

  @Test
  public void equalsIgnoresLastTransitionTime() {
    DomainCondition oldCondition = new DomainCondition(Available).withStatus("True");
    SystemClockTestSupport.increment();

    assertThat(oldCondition.equals(new DomainCondition(Available).withStatus("True")), is(true));
  }
}
