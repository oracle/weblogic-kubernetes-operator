// Copyright (c) 2019, 2021, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import com.meterware.simplestub.Memento;
import io.kubernetes.client.openapi.ApiException;
import io.kubernetes.client.openapi.models.CoreV1Event;
import io.kubernetes.client.openapi.models.V1Job;
import io.kubernetes.client.openapi.models.V1JobStatus;
import io.kubernetes.client.openapi.models.V1ObjectMeta;
import oracle.kubernetes.operator.helpers.DomainPresenceInfo;
import oracle.kubernetes.operator.helpers.KubernetesTestSupport;
import oracle.kubernetes.operator.helpers.LegalNames;
import oracle.kubernetes.operator.utils.RandomStringGenerator;
import oracle.kubernetes.utils.SystemClock;
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
import static oracle.kubernetes.operator.DomainProcessorTestSetup.NS;
import static oracle.kubernetes.operator.DomainProcessorTestSetup.UID;
import static oracle.kubernetes.operator.DomainStatusUpdater.createInternalFailureSteps;
import static oracle.kubernetes.operator.EventConstants.ABORTED_ERROR;
import static oracle.kubernetes.operator.EventConstants.DOMAIN_FAILED_EVENT;
import static oracle.kubernetes.operator.EventConstants.INTERNAL_ERROR;
import static oracle.kubernetes.operator.ProcessingConstants.DOMAIN_INTROSPECTOR_JOB;
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
  private final V1Job job = createIntrospectorJob("JOB");

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

    testSupport.defineResources(job);
    testSupport.addToPacket(DOMAIN_INTROSPECTOR_JOB, job);
  }

  @AfterEach
  void tearDown() throws Exception {
    mementos.forEach(Memento::revert);

    testSupport.throwOnCompletionFailure();
  }

  @Test
  void failedStepWithFailureMessage_andNoJobName_doesNotContainValidationWarnings() {
    info.addValidationWarning(validationWarning);

    testSupport.runSteps(createInternalFailureSteps(failure, null));

    assertThat(getRecordedDomain().getStatus().getMessage(), not(containsString(validationWarning)));
  }

  @Test
  void whenDomainLacksStatus_andNoJobName_failedStepUpdatesDomainWithFailedTrueAndException() {
    domain.setStatus(null);

    testSupport.runSteps(createInternalFailureSteps(failure, null));

    assertThat(
        getRecordedDomain(),
        hasCondition(Failed).withStatus(TRUE).withReason(Internal).withMessageContaining(message));
  }

  @Test
  void whenDomainLacksStatus_andNoJobName_generateFailedEvent() {
    domain.setStatus(null);

    testSupport.runSteps(createInternalFailureSteps(failure, null));

    assertThat(getEvents().stream().anyMatch(this::isInternalFailedEvent), is(true));
  }

  @Test
  void failedStepWithFailureMessage_doesNotContainValidationWarnings() {
    info.addValidationWarning(validationWarning);

    testSupport.runSteps(createInternalFailureSteps(failure,
        testSupport.getPacket().getValue(DOMAIN_INTROSPECTOR_JOB)));

    assertThat(getRecordedDomain().getStatus().getMessage(), not(containsString(validationWarning)));
  }

  @Test
  void whenDomainLacksStatus_failedStepUpdatesDomainWithFailedTrueAndException() {
    domain.setStatus(null);

    testSupport.runSteps(createInternalFailureSteps(failure,
        testSupport.getPacket().getValue(DOMAIN_INTROSPECTOR_JOB)));

    assertThat(
        getRecordedDomain(),
        hasCondition(Failed).withStatus(TRUE).withReason(Internal).withMessageContaining(message));
  }

  @Test
  void whenDomainLacksStatus_generateFailedEvent() {
    domain.setStatus(null);

    testSupport.runSteps(createInternalFailureSteps(failure,
        testSupport.getPacket().getValue(DOMAIN_INTROSPECTOR_JOB)));

    assertThat(getEvents().stream().anyMatch(this::isInternalFailedEvent), is(true));
  }

  private boolean isInternalFailedEvent(CoreV1Event e) {
    return DOMAIN_FAILED_EVENT.equals(e.getReason()) && getMessage(e).contains(INTERNAL_ERROR);
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
    testSupport.runSteps(createInternalFailureSteps(failure,
        testSupport.getPacket().getValue(DOMAIN_INTROSPECTOR_JOB)));

    assertThat(getEvents().stream().anyMatch(this::isInternalFailedEvent), is(true));
  }

  @Test
  void afterIntrospectionFailure_generateDomainAbortedEvent() {
    testSupport.runSteps(DomainStatusUpdater.createIntrospectionFailureSteps(FATAL_INTROSPECTOR_ERROR,
        testSupport.getPacket().getValue(DOMAIN_INTROSPECTOR_JOB)));

    assertThat(getEvents().stream().anyMatch(this::isDomainAbortedEvent), is(true));
  }

  private V1Job createIntrospectorJob(String uid) {
    return new V1Job().metadata(createJobMetadata(uid)).status(new V1JobStatus());
  }

  private V1ObjectMeta createJobMetadata(String uid) {
    return new V1ObjectMeta().name(getJobName()).namespace(NS).creationTimestamp(SystemClock.now()).uid(uid);
  }

  private static String getJobName() {
    return LegalNames.toJobIntrospectorName(UID);
  }

  private List<CoreV1Event> getEvents() {
    return testSupport.getResources(EVENT);
  }

  private boolean isDomainAbortedEvent(CoreV1Event e) {
    return DOMAIN_FAILED_EVENT.equals(e.getReason()) && getMessage(e).contains(ABORTED_ERROR);
  }

  private String getMessage(CoreV1Event e) {
    return Optional.ofNullable(e.getMessage()).orElse("");
  }

  private Domain getRecordedDomain() {
    return testSupport.getResourceWithName(KubernetesTestSupport.DOMAIN, NAME);
  }

  // todo when new failures match old ones, leave the old matches


}
