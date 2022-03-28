// Copyright (c) 2019, 2022, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.weblogic.domain.model;

import java.util.ArrayList;
import java.util.List;

import com.meterware.simplestub.Memento;
import oracle.kubernetes.utils.SystemClockTestSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static oracle.kubernetes.weblogic.domain.model.DomainConditionType.AVAILABLE;
import static oracle.kubernetes.weblogic.domain.model.DomainConditionType.COMPLETED;
import static oracle.kubernetes.weblogic.domain.model.DomainConditionType.CONFIG_CHANGES_PENDING_RESTART;
import static oracle.kubernetes.weblogic.domain.model.DomainConditionType.FAILED;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.junit.MatcherAssert.assertThat;

class DomainConditionTest {

  private final List<Memento> mementos = new ArrayList<>();

  @BeforeEach
  public void setUp() throws Exception {
    mementos.add(SystemClockTestSupport.installClock());
  }

  @AfterEach
  public void tearDown() {
    mementos.forEach(Memento::revert);
  }

  @Test
  void whenCreated_conditionHasLastTransitionTime() {
    assertThat(new DomainCondition(AVAILABLE).getLastTransitionTime(), SystemClockTestSupport.isDuringTest());
  }

  @Test
  void predicateDetectsType() {
    assertThat(new DomainCondition(FAILED).hasType(FAILED), is(true));
    assertThat(new DomainCondition(COMPLETED).hasType(AVAILABLE), is(false));
    assertThat(new DomainCondition(CONFIG_CHANGES_PENDING_RESTART).hasType(CONFIG_CHANGES_PENDING_RESTART), is(true));
  }

  @Test
  void equalsIgnoresLastTransitionTime() {
    DomainCondition oldCondition = new DomainCondition(AVAILABLE).withStatus("True");
    SystemClockTestSupport.increment();

    assertThat(oldCondition.equals(new DomainCondition(AVAILABLE).withStatus("True")), is(true));
  }

  @Test
  void mayNotPatchObjects() {
    DomainCondition oldCondition = new DomainCondition(AVAILABLE).withStatus("False");
    DomainCondition newCondition = new DomainCondition(AVAILABLE).withStatus("True");

    assertThat(newCondition.isPatchableFrom(oldCondition), is(false));

  }
}
