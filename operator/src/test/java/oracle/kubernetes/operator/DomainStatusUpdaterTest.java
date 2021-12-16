// Copyright (c) 2019, 2021, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator;

import java.util.ArrayList;
import java.util.List;

import com.meterware.simplestub.Memento;
import io.kubernetes.client.openapi.ApiException;
import io.kubernetes.client.openapi.models.CoreV1Event;
import oracle.kubernetes.operator.helpers.DomainPresenceInfo;
import oracle.kubernetes.operator.helpers.KubernetesTestSupport;
import oracle.kubernetes.operator.utils.RandomStringGenerator;
import oracle.kubernetes.utils.SystemClockTestSupport;
import oracle.kubernetes.utils.TestUtils;
import oracle.kubernetes.weblogic.domain.model.Domain;
import oracle.kubernetes.weblogic.domain.model.DomainCondition;
import oracle.kubernetes.weblogic.domain.model.DomainStatus;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static oracle.kubernetes.operator.DomainConditionMatcher.hasCondition;
import static oracle.kubernetes.operator.DomainFailureReason.Internal;
import static oracle.kubernetes.operator.DomainFailureReason.Introspection;
import static oracle.kubernetes.operator.DomainProcessorTestSetup.UID;
import static oracle.kubernetes.operator.EventConstants.DOMAIN_PROCESSING_ABORTED_EVENT;
import static oracle.kubernetes.operator.ProcessingConstants.FATAL_INTROSPECTOR_ERROR;
import static oracle.kubernetes.operator.helpers.KubernetesTestSupport.EVENT;
import static oracle.kubernetes.weblogic.domain.model.DomainCondition.TRUE;
import static oracle.kubernetes.weblogic.domain.model.DomainConditionType.Failed;
import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.junit.MatcherAssert.assertThat;

/**
 * Tests for most methods in the DomainStatusUpdater class. The #createStatusUpdateStep method is
 * tested by DomainStatusUpdateTestBase and its subclasses.
 */
class DomainStatusUpdaterTest {
  private static final String NAME = UID;
  private static final String ADMIN = "admin";
  public static final String CLUSTER = "cluster1";
  private final KubernetesTestSupport testSupport = new KubernetesTestSupport();
  private final List<Memento> mementos = new ArrayList<>();
  private final Domain domain = DomainProcessorTestSetup.createTestDomain();
  private final DomainPresenceInfo info = new DomainPresenceInfo(domain);
  private final RandomStringGenerator generator = new RandomStringGenerator();
  private final String message = generator.getUniqueString();
  private final RuntimeException failure = new RuntimeException(message);
  private final String validationWarning = generator.getUniqueString();

  @BeforeEach
  void setUp() throws NoSuchFieldException {
    mementos.add(TestUtils.silenceOperatorLogger().ignoringLoggedExceptions(ApiException.class));
    mementos.add(testSupport.install());
    mementos.add(ClientFactoryStub.install());
    mementos.add(SystemClockTestSupport.installClock());

    domain.setStatus(new DomainStatus());
    info.setAdminServerName(ADMIN);

    testSupport.addDomainPresenceInfo(info);
    testSupport.defineResources(domain);
  }

  @AfterEach
  void tearDown() throws Exception {
    mementos.forEach(Memento::revert);

    testSupport.throwOnCompletionFailure();
  }

  @Test
  void failedStepWithFailureMessage_doesNotContainValidationWarnings() {
    info.addValidationWarning(validationWarning);

    testSupport.runSteps(DomainStatusUpdater.createFailureRelatedSteps(failure));

    assertThat(getRecordedDomain().getStatus().getMessage(), not(containsString(validationWarning)));
  }

  @Test
  void whenDomainLacksStatus_failedStepUpdatesDomainWithFailedTrueAndException() {
    domain.setStatus(null);

    testSupport.runSteps(DomainStatusUpdater.createFailureRelatedSteps(failure));

    assertThat(
        getRecordedDomain(),
        hasCondition(Failed).withStatus(TRUE).withReason(Internal).withMessageContaining(message));
  }

  @Test
  void whenDomainStatusIsNull_removeFailuresStepDoesNothing() {
    domain.setStatus(null);

    testSupport.runSteps(DomainStatusUpdater.createRemoveFailuresStep());

    assertThat(getRecordedDomain(), not(hasCondition(Failed)));
  }

  @Test
  void whenDomainHasFailedCondition_removeFailureStepRemovesIt() {
    domain.getStatus().addCondition(new DomainCondition(Failed));

    testSupport.runSteps(DomainStatusUpdater.createRemoveFailuresStep());

    assertThat(getRecordedDomain(), not(hasCondition(Failed)));
  }

  @Test
  void whenDomainLacksFailedCondition_failedStepUpdatesDomainWithFailedTrueAndException() {
    testSupport.runSteps(DomainStatusUpdater.createFailureRelatedSteps(failure));

    assertThat(
          getRecordedDomain(),
        hasCondition(Failed).withStatus(TRUE).withReason(Internal).withMessageContaining(message));
  }

  @Test
  void afterIntrospectionFailure_generateDomainProcessingAbortedEvent() {
    testSupport.runSteps(DomainStatusUpdater.createFailureRelatedSteps(Introspection, FATAL_INTROSPECTOR_ERROR));

    assertThat(getEvents().stream().anyMatch(this::isDomainProcessingAbortedEvent), is(true));
  }

  private List<CoreV1Event> getEvents() {
    return testSupport.getResources(EVENT);
  }

  private boolean isDomainProcessingAbortedEvent(CoreV1Event e) {
    return DOMAIN_PROCESSING_ABORTED_EVENT.equals(e.getReason());
  }

  private Domain getRecordedDomain() {
    return testSupport.getResourceWithName(KubernetesTestSupport.DOMAIN, NAME);
  }

  // todo when new failures match old ones, leave the old matches

}
