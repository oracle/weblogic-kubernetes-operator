// Copyright (c) 2019, 2022, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.helpers;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.logging.LogRecord;

import com.meterware.simplestub.Memento;
import io.kubernetes.client.openapi.models.CoreV1Event;
import io.kubernetes.client.openapi.models.V1Job;
import io.kubernetes.client.openapi.models.V1ObjectMeta;
import oracle.kubernetes.operator.DomainProcessorTestSetup;
import oracle.kubernetes.operator.logging.LoggingFacade;
import oracle.kubernetes.operator.logging.LoggingFactory;
import oracle.kubernetes.operator.work.TerminalStep;
import oracle.kubernetes.utils.TestUtils;
import oracle.kubernetes.weblogic.domain.model.Domain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static oracle.kubernetes.common.logging.MessageKeys.NON_FATAL_INTROSPECTOR_ERROR;
import static oracle.kubernetes.common.utils.LogMatcher.containsInfo;
import static oracle.kubernetes.common.utils.LogMatcher.containsSevere;
import static oracle.kubernetes.common.utils.LogMatcher.containsWarning;
import static oracle.kubernetes.operator.DomainProcessorTestSetup.UID;
import static oracle.kubernetes.operator.EventConstants.INTROSPECTION_ERROR;
import static oracle.kubernetes.operator.EventTestUtils.getEventsWithReason;
import static oracle.kubernetes.operator.ProcessingConstants.DOMAIN_INTROSPECTOR_JOB;
import static oracle.kubernetes.operator.ProcessingConstants.JOB_POD_NAME;
import static oracle.kubernetes.operator.helpers.EventHelper.EventItem.DOMAIN_FAILED;
import static oracle.kubernetes.operator.helpers.JobHelper.INTROSPECTOR_LOG_PREFIX;
import static oracle.kubernetes.operator.helpers.KubernetesTestSupport.DOMAIN;
import static oracle.kubernetes.operator.helpers.TuningParametersStub.MAX_RETRY_COUNT;
import static oracle.kubernetes.utils.OperatorUtils.onSeparateLines;
import static oracle.kubernetes.weblogic.domain.model.DomainFailureReason.INTROSPECTION;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.stringContainsInOrder;
import static org.hamcrest.junit.MatcherAssert.assertThat;

class IntrospectionLoggingTest {
  private static final LoggingFacade LOGGER = LoggingFactory.getLogger("Operator", "Operator");

  private final Domain domain = DomainProcessorTestSetup.createTestDomain();
  private final DomainPresenceInfo info = new DomainPresenceInfo(domain);
  private final KubernetesTestSupport testSupport = new KubernetesTestSupport();
  private final List<Memento> mementos = new ArrayList<>();
  private final List<LogRecord> logRecords = new ArrayList<>();
  private final String jobPodName = LegalNames.toJobIntrospectorName(UID);
  private final TerminalStep terminalStep = new TerminalStep();

  @BeforeEach
  public void setUp() throws Exception {
    mementos.add(TestUtils.silenceOperatorLogger().collectAllLogMessages(logRecords));
    mementos.add(TuningParametersStub.install());
    mementos.add(testSupport.install());

    testSupport.addDomainPresenceInfo(info);
    testSupport.addToPacket(JOB_POD_NAME, jobPodName);
    testSupport.addToPacket(DOMAIN_INTROSPECTOR_JOB, new V1Job().metadata(new V1ObjectMeta().uid("123")));
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
  private static final String INFO_MESSAGE = "@[INFO] just letting you know";
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

    Domain updatedDomain = testSupport.getResourceWithName(DOMAIN, UID);
    assertThat(updatedDomain.getStatus().getReason(), equalTo(INTROSPECTION.toString()));
    assertThat(updatedDomain.getStatus().getMessage(), containsString(SEVERE_PROBLEM_1));
  }

  private String formatIntrospectionError(String problem) {
    return LOGGER.formatMessage(NON_FATAL_INTROSPECTOR_ERROR, problem, 1, MAX_RETRY_COUNT);
  }

  @Test
  void whenJobLogContainsSevereError_createDomainFailedIntrospectionEvent() {
    IntrospectionTestUtils.defineIntrospectionPodLog(testSupport, SEVERE_MESSAGE_1);

    testSupport.runSteps(JobHelper.readDomainIntrospectorPodLog(terminalStep));
    logRecords.clear();

    assertThat(
        "Expected Event " + DOMAIN_FAILED + " expected with message not found",
        getExpectedEventMessage(DOMAIN_FAILED),
        stringContainsInOrder("Domain", UID, "failed due to", INTROSPECTION_ERROR));
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

    Domain updatedDomain = testSupport.getResourceWithName(DOMAIN, UID);
    assertThat(updatedDomain.getStatus().getReason(), equalTo(INTROSPECTION.toString()));
    assertThat(
        updatedDomain.getStatus().getMessage(),
        containsString(onSeparateLines(SEVERE_PROBLEM_1, SEVERE_PROBLEM_2)));
  }
}
