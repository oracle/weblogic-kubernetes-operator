// Copyright (c) 2019, 2022, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.logging.LogRecord;

import com.meterware.simplestub.Memento;
import io.kubernetes.client.openapi.ApiException;
import io.kubernetes.client.openapi.models.V1Job;
import io.kubernetes.client.openapi.models.V1JobStatus;
import io.kubernetes.client.openapi.models.V1ObjectMeta;
import oracle.kubernetes.operator.helpers.DomainPresenceInfo;
import oracle.kubernetes.operator.helpers.KubernetesTestSupport;
import oracle.kubernetes.operator.helpers.LegalNames;
import oracle.kubernetes.operator.helpers.TuningParametersStub;
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

import static oracle.kubernetes.common.logging.MessageKeys.DOMAIN_ROLL_START;
import static oracle.kubernetes.common.utils.LogMatcher.containsInfo;
import static oracle.kubernetes.operator.DomainFailureReason.INTERNAL;
import static oracle.kubernetes.operator.DomainFailureReason.KUBERNETES;
import static oracle.kubernetes.operator.DomainProcessorTestSetup.NS;
import static oracle.kubernetes.operator.DomainProcessorTestSetup.UID;
import static oracle.kubernetes.operator.DomainStatusUpdater.createInternalFailureSteps;
import static oracle.kubernetes.operator.EventConstants.ABORTED_ERROR;
import static oracle.kubernetes.operator.EventConstants.DOMAIN_FAILED_EVENT;
import static oracle.kubernetes.operator.EventConstants.DOMAIN_ROLL_STARTING_EVENT;
import static oracle.kubernetes.operator.EventConstants.INTERNAL_ERROR;
import static oracle.kubernetes.operator.EventMatcher.hasEvent;
import static oracle.kubernetes.operator.ProcessingConstants.DOMAIN_INTROSPECTOR_JOB;
import static oracle.kubernetes.operator.ProcessingConstants.FATAL_INTROSPECTOR_ERROR;
import static oracle.kubernetes.weblogic.domain.model.DomainCondition.TRUE;
import static oracle.kubernetes.weblogic.domain.model.DomainConditionMatcher.hasCondition;
import static oracle.kubernetes.weblogic.domain.model.DomainConditionType.FAILED;
import static oracle.kubernetes.weblogic.domain.model.DomainConditionType.ROLLING;
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
  private final Collection<LogRecord> logRecords = new ArrayList<>();
  private TestUtils.ConsoleHandlerMemento consoleHandlerMemento;

  @BeforeEach
  void setUp() throws NoSuchFieldException {
    mementos.add(consoleHandlerMemento = TestUtils.silenceOperatorLogger()
          .collectLogMessages(logRecords).ignoringLoggedExceptions(ApiException.class));
    mementos.add(testSupport.install());
    mementos.add(ClientFactoryStub.install());
    mementos.add(SystemClockTestSupport.installClock());
    mementos.add(TuningParametersStub.install());

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
  void whenNeedToReplacePodAndNotRolling_setRollingStatus() {
    testSupport.runSteps(DomainStatusUpdater.createStartRollStep());

    assertThat(getRecordedDomain().getStatus().isRolling(), is(true));
  }

  @Test
  void whenNeedToReplacePodAndRolling_dontGenerateRollingStartedEvent() {
    domain.getStatus().addCondition(new DomainCondition(ROLLING));

    testSupport.runSteps(DomainStatusUpdater.createStartRollStep());

    assertThat(testSupport, not(hasEvent(DOMAIN_ROLL_STARTING_EVENT)));
  }

  @Test
  void whenNeedToReplacePodAndRolling_dontLogDomainStarted() {
    domain.getStatus().addCondition(new DomainCondition(ROLLING));
    consoleHandlerMemento.trackMessage(DOMAIN_ROLL_START);

    testSupport.runSteps(DomainStatusUpdater.createStartRollStep());

    assertThat(logRecords, not(containsInfo(DOMAIN_ROLL_START)));
  }

  @Test
  void whenNeedToReplacePodAndNotRolling_generateRollingStartedEvent() {
    testSupport.runSteps(DomainStatusUpdater.createStartRollStep());

    assertThat(testSupport, hasEvent(DOMAIN_ROLL_STARTING_EVENT).inNamespace(NS).withMessageContaining(UID));
  }

  @Test
  void whenNeedToReplacePodAndRolling_logDomainStarted() {
    consoleHandlerMemento.trackMessage(DOMAIN_ROLL_START);

    testSupport.runSteps(DomainStatusUpdater.createStartRollStep());

    assertThat(logRecords, containsInfo(DOMAIN_ROLL_START));
  }

  @Test
  void failedStepWithFailureMessage_andNoJob_doesNotContainValidationWarnings() {
    info.addValidationWarning(validationWarning);

    testSupport.runSteps(createInternalFailureSteps(failure));

    assertThat(getRecordedDomain().getStatus().getMessage(), not(containsString(validationWarning)));
  }

  @Test
  void whenDomainLacksStatus_andNoJob_failedStepUpdatesDomainWithFailedTrueAndException() {
    domain.setStatus(null);

    testSupport.runSteps(createInternalFailureSteps(failure));

    assertThat(
        getRecordedDomain(),
        hasCondition(FAILED).withStatus(TRUE).withReason(INTERNAL).withMessageContaining(message));
  }

  @Test
  void whenDomainLacksStatus_andNoJob_generateFailedEvent() {
    domain.setStatus(null);

    testSupport.runSteps(createInternalFailureSteps(failure));

    assertThat(testSupport, hasEvent(DOMAIN_FAILED_EVENT).withMessageContaining(INTERNAL_ERROR));
  }

  @Test
  void failedStepWithFailureMessage_doesNotContainValidationWarnings() {
    info.addValidationWarning(validationWarning);

    testSupport.runSteps(createInternalFailureSteps(failure));

    assertThat(getRecordedDomain().getStatus().getMessage(), not(containsString(validationWarning)));
  }

  @Test
  void whenDomainLacksStatus_failedStepUpdatesDomainWithFailedTrueAndException() {
    domain.setStatus(null);

    testSupport.runSteps(createInternalFailureSteps(failure));

    assertThat(
        getRecordedDomain(),
        hasCondition(FAILED).withStatus(TRUE).withReason(INTERNAL).withMessageContaining(message));
  }

  @Test
  void whenDomainLacksStatus_generateFailedEvent() {
    domain.setStatus(null);

    testSupport.runSteps(createInternalFailureSteps(failure));

    assertThat(testSupport, hasEvent(DOMAIN_FAILED_EVENT).withMessageContaining(INTERNAL_ERROR));
  }

  @Test
  void whenDomainStatusIsNull_removeFailuresStepDoesNothing() {
    domain.setStatus(null);

    testSupport.runSteps(DomainStatusUpdater.createRemoveFailuresStep());

    assertThat(getRecordedDomain(), not(hasCondition(FAILED)));
  }

  @Test
  void whenDomainHasFailedCondition_removeFailureStepRemovesIt() {
    domain.getStatus().addCondition(new DomainCondition(FAILED).withReason(KUBERNETES));

    testSupport.runSteps(DomainStatusUpdater.createRemoveFailuresStep());

    assertThat(getRecordedDomain(), not(hasCondition(FAILED)));
  }

  @Test
  void whenDomainLacksFailedCondition_failedStepUpdatesDomainWithFailedTrueAndException() {
    testSupport.runSteps(createInternalFailureSteps(failure));

    assertThat(testSupport, hasEvent(DOMAIN_FAILED_EVENT).withMessageContaining(INTERNAL_ERROR));
  }

  @Test
  void afterIntrospectionFailure_generateDomainAbortedEvent() {
    testSupport.runSteps(DomainStatusUpdater.createIntrospectionFailureSteps(FATAL_INTROSPECTOR_ERROR, job));

    assertThat(testSupport, hasEvent(DOMAIN_FAILED_EVENT).withMessageContaining(ABORTED_ERROR));
  }

  @SuppressWarnings("SameParameterValue")
  private V1Job createIntrospectorJob(String uid) {
    return new V1Job().metadata(createJobMetadata(uid)).status(new V1JobStatus());
  }

  private V1ObjectMeta createJobMetadata(String uid) {
    return new V1ObjectMeta().name(getJobName()).namespace(NS).creationTimestamp(SystemClock.now()).uid(uid);
  }

  private static String getJobName() {
    return LegalNames.toJobIntrospectorName(UID);
  }

  private Domain getRecordedDomain() {
    return testSupport.getResourceWithName(KubernetesTestSupport.DOMAIN, NAME);
  }

  // todo when new failures match old ones, leave the old matches

}
