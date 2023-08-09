// Copyright (c) 2019, 2022, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.introspection;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.logging.LogRecord;

import com.meterware.simplestub.Memento;
import io.kubernetes.client.openapi.models.CoreV1Event;
import io.kubernetes.client.openapi.models.V1Job;
import io.kubernetes.client.openapi.models.V1ObjectMeta;
import io.kubernetes.client.openapi.models.V1Pod;
import oracle.kubernetes.operator.DomainProcessorTestSetup;
import oracle.kubernetes.operator.helpers.DomainPresenceInfo;
import oracle.kubernetes.operator.helpers.EventHelper;
import oracle.kubernetes.operator.helpers.JobHelper;
import oracle.kubernetes.operator.helpers.KubernetesTestSupport;
import oracle.kubernetes.operator.helpers.LegalNames;
import oracle.kubernetes.operator.tuning.TuningParametersStub;
import oracle.kubernetes.operator.work.TerminalStep;
import oracle.kubernetes.utils.TestUtils;
import oracle.kubernetes.weblogic.domain.model.DomainResource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static oracle.kubernetes.common.logging.MessageKeys.INTROSPECTION_EVENT_ERROR;
import static oracle.kubernetes.common.utils.LogMatcher.containsInfo;
import static oracle.kubernetes.common.utils.LogMatcher.containsSevere;
import static oracle.kubernetes.common.utils.LogMatcher.containsWarning;
import static oracle.kubernetes.operator.DomainProcessorTestSetup.UID;
import static oracle.kubernetes.operator.EventTestUtils.getEventsWithReason;
import static oracle.kubernetes.operator.EventTestUtils.getLocalizedString;
import static oracle.kubernetes.operator.ProcessingConstants.DOMAIN_INTROSPECTION_COMPLETE;
import static oracle.kubernetes.operator.ProcessingConstants.DOMAIN_INTROSPECTOR_JOB;
import static oracle.kubernetes.operator.ProcessingConstants.JOB_POD;
import static oracle.kubernetes.operator.helpers.EventHelper.EventItem.DOMAIN_FAILED;
import static oracle.kubernetes.operator.helpers.JobHelper.INTROSPECTOR_LOG_PREFIX;
import static oracle.kubernetes.operator.helpers.KubernetesTestSupport.DOMAIN;
import static oracle.kubernetes.utils.OperatorUtils.onSeparateLines;
import static oracle.kubernetes.weblogic.domain.model.DomainFailureReason.INTROSPECTION;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.stringContainsInOrder;
import static org.hamcrest.junit.MatcherAssert.assertThat;

class IntrospectionLoggingTest {
  private final DomainResource domain = DomainProcessorTestSetup.createTestDomain();
  private final DomainPresenceInfo info = new DomainPresenceInfo(domain);
  private final KubernetesTestSupport testSupport = new KubernetesTestSupport();
  private final List<Memento> mementos = new ArrayList<>();
  private final List<LogRecord> logRecords = new ArrayList<>();
  private final String jobPodName = LegalNames.toJobIntrospectorName(UID);
  private final TerminalStep terminalStep = new TerminalStep();
  private final V1Job introspectorJob
      = new V1Job().metadata(new V1ObjectMeta().uid(UID)).status(IntrospectionTestUtils.createCompletedStatus());

  @BeforeEach
  public void setUp() throws Exception {
    mementos.add(TestUtils.silenceOperatorLogger().collectAllLogMessages(logRecords));
    mementos.add(TuningParametersStub.install());
    mementos.add(testSupport.install());

    testSupport.addDomainPresenceInfo(info);
    testSupport.addToPacket(JOB_POD, new V1Pod().metadata(new V1ObjectMeta().name(jobPodName)));
    testSupport.addToPacket(DOMAIN_INTROSPECTOR_JOB, introspectorJob);
    testSupport.defineResources(domain);
  }

  @AfterEach
  public void tearDown() {
    mementos.forEach(Memento::revert);
  }

  private static final String SEVERE_PROBLEM_1 = "really bad";
  private static final String SEVERE_PROBLEM_2 = "better fix it";
  private static final String SEVERE_MESSAGE_1 = "@[SEVERE] " + SEVERE_PROBLEM_1;
  private static final String SEVERE_MESSAGE_2 = "@[SEVERE] " + SEVERE_PROBLEM_2;
  private static final String WARNING_MESSAGE = "@[WARNING] could be bad";
  private static final String INFO_MESSAGE = "@[INFO] just letting you know. " + DOMAIN_INTROSPECTION_COMPLETE;
  private static final String INFO_EXTRA1 = "more stuff";
  private static final String INFO_EXTRA_2 = "still more";

  @Test
  void logIntrospectorMessages() {
    IntrospectionTestUtils.defineIntrospectionPodLog(testSupport,
          onSeparateLines(SEVERE_MESSAGE_1, WARNING_MESSAGE, INFO_MESSAGE));

    testSupport.runSteps(JobHelper.readDomainIntrospectorPodLog(terminalStep));

    assertThat(logRecords, containsSevere(INTROSPECTOR_LOG_PREFIX + SEVERE_MESSAGE_1));
    assertThat(logRecords, containsWarning(INTROSPECTOR_LOG_PREFIX + WARNING_MESSAGE));
    assertThat(logRecords, containsInfo(INTROSPECTOR_LOG_PREFIX + INFO_MESSAGE));
    logRecords.clear();
  }

  @Test
  void whenIntrospectorMessageContainsAdditionalLines_logThem() {
    introspectorJob.status(IntrospectionTestUtils.createCompletedStatus());
    String extendedInfoMessage = onSeparateLines(INFO_MESSAGE, INFO_EXTRA1, INFO_EXTRA_2);
    IntrospectionTestUtils.defineIntrospectionPodLog(testSupport, extendedInfoMessage);

    testSupport.runSteps(JobHelper.readDomainIntrospectorPodLog(terminalStep));

    assertThat(logRecords, containsInfo(INTROSPECTOR_LOG_PREFIX + extendedInfoMessage));
    logRecords.clear();
  }

  @Test
  void whenJobLogContainsSevereError_copyToDomainStatus() {
    IntrospectionTestUtils.defineIntrospectionPodLog(testSupport, SEVERE_MESSAGE_1);

    testSupport.runSteps(JobHelper.readDomainIntrospectorPodLog(terminalStep));
    logRecords.clear();

    DomainResource updatedDomain = testSupport.getResourceWithName(DOMAIN, UID);
    assertThat(updatedDomain.getStatus().getReason(), equalTo(INTROSPECTION.toString()));
    assertThat(updatedDomain.getStatus().getMessage(), containsString(SEVERE_PROBLEM_1));
  }

  @Test
  void whenJobLogContainsSevereError_createDomainFailedIntrospectionEvent() {
    IntrospectionTestUtils.defineIntrospectionPodLog(testSupport, SEVERE_MESSAGE_1);

    testSupport.runSteps(JobHelper.readDomainIntrospectorPodLog(terminalStep));
    logRecords.clear();

    assertThat(
        "Expected Event " + DOMAIN_FAILED + " expected with message not found",
        getExpectedEventMessage(DOMAIN_FAILED),
        stringContainsInOrder("Domain", UID, "failed due to", getLocalizedString(INTROSPECTION_EVENT_ERROR)));
  }

  @SuppressWarnings("SameParameterValue")
  protected String getExpectedEventMessage(EventHelper.EventItem event) {
    List<CoreV1Event> events = getEventsWithReason(getEvents(), event.getReason());
    return Optional.ofNullable(events)
        .filter(list -> list.size() != 0)
        .map(n -> n.get(0))
        .map(CoreV1Event::getMessage)
        .orElse("Event not found");
  }

  private List<CoreV1Event> getEvents() {
    return testSupport.getResources(KubernetesTestSupport.EVENT);
  }

  @Test
  void whenJobLogContainsMultipleSevereErrors_copyToDomainStatus() {
    IntrospectionTestUtils.defineIntrospectionPodLog(testSupport,
            onSeparateLines(SEVERE_MESSAGE_1, INFO_MESSAGE, INFO_EXTRA1, SEVERE_MESSAGE_2));

    testSupport.runSteps(JobHelper.readDomainIntrospectorPodLog(terminalStep));
    logRecords.clear();

    DomainResource updatedDomain = testSupport.getResourceWithName(DOMAIN, UID);
    assertThat(updatedDomain.getStatus().getReason(), equalTo(INTROSPECTION.toString()));
    assertThat(
        updatedDomain.getStatus().getMessage(),
        containsString(onSeparateLines(SEVERE_PROBLEM_1, SEVERE_PROBLEM_2)));
  }
}
