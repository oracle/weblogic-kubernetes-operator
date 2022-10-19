// Copyright (c) 2022, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.weblogic.domain.model;

import java.io.IOException;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

import com.meterware.simplestub.Memento;
import io.kubernetes.client.common.KubernetesObject;
import oracle.kubernetes.utils.SystemClock;
import oracle.kubernetes.utils.SystemClockTestSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static java.time.temporal.ChronoUnit.SECONDS;
import static oracle.kubernetes.weblogic.domain.model.DomainConditionType.FAILED;
import static oracle.kubernetes.weblogic.domain.model.DomainFailureReason.ABORTED;
import static oracle.kubernetes.weblogic.domain.model.DomainFailureReason.DOMAIN_INVALID;
import static oracle.kubernetes.weblogic.domain.model.DomainFailureReason.INTERNAL;
import static oracle.kubernetes.weblogic.domain.model.DomainFailureReason.REPLICAS_TOO_HIGH;
import static oracle.kubernetes.weblogic.domain.model.DomainFailureReason.SERVER_POD;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.core.Is.is;
import static org.hamcrest.junit.MatcherAssert.assertThat;

class DomainResourceRetryTest extends DomainTestBase {
  private final List<Memento> mementos = new ArrayList<>();

  @BeforeEach
  void setUp() throws NoSuchFieldException {
    configureDomain(domain);

    mementos.add(SystemClockTestSupport.installClock());
  }

  @AfterEach
  void tearDown() {
    mementos.forEach(Memento::revert);
  }

  @Test
  void failureRetryParameters_haveDefaultValues() {
    assertThat(domain.getFailureRetryIntervalSeconds(), equalTo(120L));
    assertThat(domain.getFailureRetryLimitMinutes(), equalTo(1440L));
  }

  @Test
  void failureRetryParameters_mayBeConfigured() {
    configureDomain(domain)
          .withFailureRetryIntervalSeconds(73)
          .withFailureRetryLimitMinutes(103);

    assertThat(domain.getFailureRetryIntervalSeconds(), equalTo(73L));
    assertThat(domain.getFailureRetryLimitMinutes(), equalTo(103L));
  }

  @Test
  void readFailureRetryParametersFromYaml() throws IOException {
    List<KubernetesObject> resources = readFromYaml(DOMAIN_V2_SAMPLE_YAML_3);
    DomainResource domain = (DomainResource) resources.get(0);

    assertThat(domain.getFailureRetryIntervalSeconds(), equalTo(90L));
    assertThat(domain.getFailureRetryLimitMinutes(), equalTo(1000L));
  }

  @Test
  void whenNoErrors_noRetryNeeded() {
    assertThat(domain.shouldRetry(), is(false));
  }

  @Test
  void whenDomainHasSevereFailure_retryIsNeeded() {
    addFailureCondition(INTERNAL);

    assertThat(domain.shouldRetry(), is(true));
  }

  @Test
  void whenDomainHasAbortedFailure_noRetryNeeded() {
    addFailureCondition(ABORTED);

    assertThat(domain.shouldRetry(), is(false));
  }

  private void addFailureCondition(DomainFailureReason reason) {
    domain.getOrCreateStatus().addCondition(new DomainCondition(FAILED).withReason(reason).withMessage("oops"));
  }

  @Test
  void whenDomainHasSevereFailure_nextRetryTimeIsLastFailureTimePlusRetryInterval() {
    final OffsetDateTime lastFailureTime = SystemClock.now();
    addFailureCondition(SERVER_POD);

    assertThat(domain.getNextRetryTime(),
               equalTo(lastFailureTime.plus(domain.getFailureRetryIntervalSeconds(), SECONDS)));
  }

  @Test
  void whenDomainHasOnlyWarningFailure_noRetryNeeded() {
    addFailureCondition(REPLICAS_TOO_HIGH);

    assertThat(domain.shouldRetry(), is(false));
  }

  @Test
  void whenDomainHasFatalFailure_noRetryNeeded() {
    addFailureCondition(ABORTED);

    assertThat(domain.shouldRetry(), is(false));
  }

  @Test
  void retryMessage_contains_retryLimitCalculatedFromInitialFailureTime() {
    final int RETRY_LIMIT_MINUTES = 60;
    configureDomain(domain)
        .withFailureRetryLimitMinutes(RETRY_LIMIT_MINUTES);

    OffsetDateTime initialFailureTime = SystemClock.now();
    addFailureCondition(DOMAIN_INVALID);

    SystemClockTestSupport.increment();
    DomainCondition secondLaterCondition = new DomainCondition(FAILED).withReason(DOMAIN_INVALID).withMessage("oops");

    String retryMessage = domain.createRetryMessage(domain.getStatus(), secondLaterCondition);
    assertThat(retryMessage,
        containsString(initialFailureTime.plus(RETRY_LIMIT_MINUTES, ChronoUnit.MINUTES).toString()));
  }
}
