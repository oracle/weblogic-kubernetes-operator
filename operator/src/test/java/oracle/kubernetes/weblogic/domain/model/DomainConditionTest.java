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

import static oracle.kubernetes.operator.ProcessingConstants.FATAL_DOMAIN_INVALID_ERROR;
import static oracle.kubernetes.operator.ProcessingConstants.FATAL_INTROSPECTOR_ERROR;
import static oracle.kubernetes.weblogic.domain.model.DomainConditionType.AVAILABLE;
import static oracle.kubernetes.weblogic.domain.model.DomainConditionType.COMPLETED;
import static oracle.kubernetes.weblogic.domain.model.DomainConditionType.CONFIG_CHANGES_PENDING_RESTART;
import static oracle.kubernetes.weblogic.domain.model.DomainConditionType.FAILED;
import static oracle.kubernetes.weblogic.domain.model.DomainFailureReason.DOMAIN_INVALID;
import static oracle.kubernetes.weblogic.domain.model.DomainFailureReason.INTROSPECTION;
import static oracle.kubernetes.weblogic.domain.model.DomainFailureReason.REPLICAS_TOO_HIGH;
import static oracle.kubernetes.weblogic.domain.model.DomainFailureSeverity.FATAL;
import static oracle.kubernetes.weblogic.domain.model.DomainFailureSeverity.SEVERE;
import static oracle.kubernetes.weblogic.domain.model.DomainFailureSeverity.WARNING;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.lessThan;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.sameInstance;
import static org.hamcrest.junit.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

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
  void mayNotSetMessageOnNonFailures() {
    final DomainCondition domainCondition = new DomainCondition(FAILED).withMessage("bad order");
    assertThrows(IllegalStateException.class, () -> domainCondition.withReason(INTROSPECTION));
  }

  @Test
  void comparisonToNonCondition_returnsFalse() {
    final DomainCondition domainCondition = new DomainCondition(FAILED).withMessage("bad order");

    assertThat(domainCondition, not(equalTo("bad order")));
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

  @Test
  void whenFailedConditionCreated_setSeverityToSevereByDefault() {
    DomainCondition severeFailure = new DomainCondition(FAILED).withReason(DOMAIN_INVALID);

    assertThat(severeFailure.getSeverity(), sameInstance(SEVERE));
  }

  @Test
  void whenConditionTypeIsNotFailed_mayNotSetSeverity() {
    DomainCondition condition = new DomainCondition(AVAILABLE).withStatus("False");

    assertThrows(IllegalStateException.class, () -> condition.setSeverity(WARNING));
  }

  @Test
  void whenSeveritySetOnFailedCondition_canRetrieveIt() {
    DomainCondition condition = new DomainCondition(FAILED).withReason(DOMAIN_INVALID);
    condition.setSeverity(WARNING);

    assertThat(condition.getSeverity(), sameInstance(WARNING));
  }

  @Test
  void replicasTooHighFailures_defaultToWarning() {
    DomainCondition replicasTooHigh = new DomainCondition(FAILED).withReason(REPLICAS_TOO_HIGH);

    assertThat(replicasTooHigh.getSeverity(), sameInstance(WARNING));
  }

  @Test
  void whenIntrospectionFailuresIncludeFatalErrors_defaultToFatalSeverity() {
    DomainCondition failure = new DomainCondition(FAILED).withReason(INTROSPECTION)
          .withMessage("one error\n" + FATAL_INTROSPECTOR_ERROR + " another");

    assertThat(failure.getSeverity(), sameInstance(FATAL));

  }

  @Test
  void whenDomainInvalidFailuresIncludeFatalErrors_defaultToFatalSeverity() {
    DomainCondition failure = new DomainCondition(FAILED).withReason(DOMAIN_INVALID)
        .withMessage("one error\n" + FATAL_DOMAIN_INVALID_ERROR + " another");

    assertThat(failure.getSeverity(), sameInstance(FATAL));
  }

  @Test
  void sortSevereFailuresBeforeAvailable() {
    DomainCondition availableCondition = new DomainCondition(AVAILABLE).withStatus("False");
    DomainCondition failureCondition = new DomainCondition(FAILED).withReason(DOMAIN_INVALID);

    assertThat(failureCondition, lessThan(availableCondition));
  }

  @Test
  void sortSevereFailuresBeforeWarnings() {
    DomainCondition warningFailure = new DomainCondition(FAILED).withReason(REPLICAS_TOO_HIGH);
    DomainCondition severeFailure = new DomainCondition(FAILED).withReason(DOMAIN_INVALID);

    assertThat(severeFailure, lessThan(warningFailure));
  }

  @Test
  void sortFatalFailuresBeforeSevereFailures() {
    DomainCondition severeFailure = new DomainCondition(FAILED).withReason(DOMAIN_INVALID);
    DomainCondition fatalFailure
          = new DomainCondition(FAILED).withReason(INTROSPECTION).withMessage(FATAL_INTROSPECTOR_ERROR);

    assertThat(fatalFailure, lessThan(severeFailure));
  }
}
