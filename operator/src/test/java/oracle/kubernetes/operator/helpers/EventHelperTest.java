// Copyright (c) 2020, 2022, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.helpers;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.LogRecord;

import com.meterware.simplestub.Memento;
import com.meterware.simplestub.StaticStubSupport;
import io.kubernetes.client.openapi.models.CoreV1Event;
import oracle.kubernetes.operator.DomainFailureReason;
import oracle.kubernetes.operator.DomainProcessorDelegateStub;
import oracle.kubernetes.operator.DomainProcessorImpl;
import oracle.kubernetes.operator.DomainProcessorTestSetup;
import oracle.kubernetes.operator.EventConstants;
import oracle.kubernetes.operator.EventTestUtils;
import oracle.kubernetes.operator.LabelConstants;
import oracle.kubernetes.operator.MakeRightDomainOperation;
import oracle.kubernetes.operator.builders.WatchEvent;
import oracle.kubernetes.operator.helpers.EventHelper.EventData;
import oracle.kubernetes.operator.work.Step;
import oracle.kubernetes.utils.TestUtils;
import oracle.kubernetes.weblogic.domain.model.Domain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static com.meterware.simplestub.Stub.createStrictStub;
import static java.net.HttpURLConnection.HTTP_CONFLICT;
import static java.net.HttpURLConnection.HTTP_FORBIDDEN;
import static java.net.HttpURLConnection.HTTP_NOT_FOUND;
import static java.net.HttpURLConnection.HTTP_UNAVAILABLE;
import static oracle.kubernetes.operator.DomainProcessorTestSetup.NS;
import static oracle.kubernetes.operator.DomainProcessorTestSetup.UID;
import static oracle.kubernetes.operator.DomainStatusUpdater.createDomainInvalidFailureSteps;
import static oracle.kubernetes.operator.DomainStatusUpdater.createTopologyMismatchFailureSteps;
import static oracle.kubernetes.operator.EventConstants.DOMAIN_AVAILABLE_EVENT;
import static oracle.kubernetes.operator.EventConstants.DOMAIN_AVAILABLE_PATTERN;
import static oracle.kubernetes.operator.EventConstants.DOMAIN_CHANGED_PATTERN;
import static oracle.kubernetes.operator.EventConstants.DOMAIN_CREATED_EVENT;
import static oracle.kubernetes.operator.EventConstants.DOMAIN_CREATED_PATTERN;
import static oracle.kubernetes.operator.EventConstants.DOMAIN_DELETED_PATTERN;
import static oracle.kubernetes.operator.EventConstants.DOMAIN_FAILED_EVENT;
import static oracle.kubernetes.operator.EventConstants.DOMAIN_FAILED_PATTERN;
import static oracle.kubernetes.operator.EventConstants.DOMAIN_FAILURE_RESOLVED_EVENT;
import static oracle.kubernetes.operator.EventConstants.DOMAIN_FAILURE_RESOLVED_PATTERN;
import static oracle.kubernetes.operator.EventConstants.DOMAIN_INCOMPLETE_EVENT;
import static oracle.kubernetes.operator.EventConstants.DOMAIN_INCOMPLETE_PATTERN;
import static oracle.kubernetes.operator.EventConstants.DOMAIN_ROLL_COMPLETED_EVENT;
import static oracle.kubernetes.operator.EventConstants.DOMAIN_ROLL_STARTING_EVENT;
import static oracle.kubernetes.operator.EventConstants.DOMAIN_UNAVAILABLE_EVENT;
import static oracle.kubernetes.operator.EventConstants.DOMAIN_UNAVAILABLE_PATTERN;
import static oracle.kubernetes.operator.EventConstants.NAMESPACE_WATCHING_STARTED_EVENT;
import static oracle.kubernetes.operator.EventConstants.NAMESPACE_WATCHING_STOPPED_EVENT;
import static oracle.kubernetes.operator.EventConstants.POD_CYCLE_STARTING_EVENT;
import static oracle.kubernetes.operator.EventConstants.START_MANAGING_NAMESPACE_FAILED_EVENT;
import static oracle.kubernetes.operator.EventConstants.STOP_MANAGING_NAMESPACE_EVENT;
import static oracle.kubernetes.operator.EventConstants.TOPOLOGY_MISMATCH_ERROR;
import static oracle.kubernetes.operator.EventConstants.WILL_NOT_RETRY;
import static oracle.kubernetes.operator.EventTestUtils.containsEvent;
import static oracle.kubernetes.operator.EventTestUtils.containsEventWithInvolvedObject;
import static oracle.kubernetes.operator.EventTestUtils.containsEventWithLabels;
import static oracle.kubernetes.operator.EventTestUtils.containsEventWithMessage;
import static oracle.kubernetes.operator.EventTestUtils.containsEventWithNamespace;
import static oracle.kubernetes.operator.EventTestUtils.containsEventsWithCountOne;
import static oracle.kubernetes.operator.EventTestUtils.containsOneEventWithCount;
import static oracle.kubernetes.operator.EventTestUtils.getEvents;
import static oracle.kubernetes.operator.EventTestUtils.getNumberOfEvents;
import static oracle.kubernetes.operator.KubernetesConstants.OPERATOR_NAMESPACE_ENV;
import static oracle.kubernetes.operator.KubernetesConstants.OPERATOR_POD_NAME_ENV;
import static oracle.kubernetes.operator.ProcessingConstants.JOB_POD_NAME;
import static oracle.kubernetes.operator.helpers.EventHelper.EventItem.DOMAIN_AVAILABLE;
import static oracle.kubernetes.operator.helpers.EventHelper.EventItem.DOMAIN_CHANGED;
import static oracle.kubernetes.operator.helpers.EventHelper.EventItem.DOMAIN_CREATED;
import static oracle.kubernetes.operator.helpers.EventHelper.EventItem.DOMAIN_DELETED;
import static oracle.kubernetes.operator.helpers.EventHelper.EventItem.DOMAIN_FAILED;
import static oracle.kubernetes.operator.helpers.EventHelper.EventItem.DOMAIN_FAILURE_RESOLVED;
import static oracle.kubernetes.operator.helpers.EventHelper.EventItem.DOMAIN_INCOMPLETE;
import static oracle.kubernetes.operator.helpers.EventHelper.EventItem.DOMAIN_ROLL_COMPLETED;
import static oracle.kubernetes.operator.helpers.EventHelper.EventItem.DOMAIN_ROLL_STARTING;
import static oracle.kubernetes.operator.helpers.EventHelper.EventItem.DOMAIN_UNAVAILABLE;
import static oracle.kubernetes.operator.helpers.EventHelper.EventItem.NAMESPACE_WATCHING_STARTED;
import static oracle.kubernetes.operator.helpers.EventHelper.EventItem.NAMESPACE_WATCHING_STOPPED;
import static oracle.kubernetes.operator.helpers.EventHelper.EventItem.POD_CYCLE_STARTING;
import static oracle.kubernetes.operator.helpers.EventHelper.EventItem.START_MANAGING_NAMESPACE;
import static oracle.kubernetes.operator.helpers.EventHelper.EventItem.STOP_MANAGING_NAMESPACE;
import static oracle.kubernetes.operator.helpers.EventHelper.createEventStep;
import static oracle.kubernetes.operator.helpers.KubernetesTestSupport.EVENT;
import static oracle.kubernetes.operator.logging.MessageKeys.CREATING_EVENT_FORBIDDEN;
import static oracle.kubernetes.utils.LogMatcher.containsInfo;
import static oracle.kubernetes.utils.LogMatcher.containsWarning;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

class EventHelperTest {
  private static final String OPERATOR_POD_NAME = "my-weblogic-operator-1234";
  private static final String OP_NS = "operator-namespace";

  private final List<Memento> mementos = new ArrayList<>();
  private final KubernetesTestSupport testSupport = new KubernetesTestSupport();
  private final DomainProcessorDelegateStub processorDelegate = DomainProcessorDelegateStub.createDelegate(testSupport);
  private final DomainProcessorImpl processor = new DomainProcessorImpl(processorDelegate);
  private final Domain domain = DomainProcessorTestSetup.createTestDomain();
  private final Map<String, Map<String, DomainPresenceInfo>> presenceInfoMap = new HashMap<>();
  private final Map<String, Map<String, KubernetesEventObjects>> domainEventObjects = new ConcurrentHashMap<>();
  private final Map<String, KubernetesEventObjects> nsEventObjects = new ConcurrentHashMap<>();
  private final DomainPresenceInfo info = new DomainPresenceInfo(domain);
  private final MakeRightDomainOperation makeRightOperation
      = processor.createMakeRightOperation(info);
  private final String jobPodName = LegalNames.toJobIntrospectorName(UID);
  private final Collection<LogRecord> logRecords = new ArrayList<>();
  private final OnConflictRetryStrategyStub retryStrategy = createStrictStub(OnConflictRetryStrategyStub.class);
  private TestUtils.ConsoleHandlerMemento loggerControl;

  @BeforeEach
  void setUp() throws Exception {
    mementos.add(loggerControl = TestUtils.silenceOperatorLogger());
    mementos.add(testSupport.install());
    mementos.add(StaticStubSupport.install(DomainProcessorImpl.class, "DOMAINS", presenceInfoMap));
    mementos.add(StaticStubSupport.install(DomainProcessorImpl.class, "domainEventK8SObjects", domainEventObjects));
    mementos.add(StaticStubSupport.install(DomainProcessorImpl.class, "namespaceEventK8SObjects", nsEventObjects));
    mementos.add(TuningParametersStub.install());
    mementos.add(HelmAccessStub.install());

    testSupport.addToPacket(JOB_POD_NAME, jobPodName);
    testSupport.addDomainPresenceInfo(info);
    testSupport.defineResources(domain);
    DomainProcessorTestSetup.defineRequiredResources(testSupport);
    HelmAccessStub.defineVariable(OPERATOR_NAMESPACE_ENV, OP_NS);
    HelmAccessStub.defineVariable(OPERATOR_POD_NAME_ENV, OPERATOR_POD_NAME);
  }

  @AfterEach
  void tearDown() {
    mementos.forEach(Memento::revert);
  }

  @Test
  void whenCreateEventCalledTwice_thenDeleteEvent_domainCreatedEventCreatedTwice() {
    testSupport.runSteps(createEventStep(new EventData(DOMAIN_CREATED)));

    dispatchAddedEventWatches();
    dispatchDeletedEventWatches();

    testSupport.runSteps(Step.chain(
        createEventStep(new EventData(DOMAIN_CREATED))));

    assertThat("Found 2 DOMAIN_CREATED events with expected count 1",
        containsEventsWithCountOne(getEvents(testSupport),
            DOMAIN_CREATED_EVENT, 2), is(true));
  }

  @Test
  void whenCreateEventStepCalledWithFailedReasonTopologyMismatch_domainFailedEventCreated() {
    testSupport.runSteps(createTopologyMismatchFailureSteps("Test failure"));

    assertThat("Found DOMAIN_FAILED event",
        containsEvent(getEvents(testSupport), DOMAIN_FAILED_EVENT), is(true));
  }

  @Test
  void whenCreateEventStepCalledWithFailedReasonTopologyMismatch_domainFailedEventCreatedWithExpectedMessage() {
    testSupport.runSteps(createTopologyMismatchFailureSteps("Test this failure"));

    assertThat("Found DOMAIN_FAILED event with expected message",
        containsEventWithMessage(getEvents(testSupport),
            DOMAIN_FAILED_EVENT,
            String.format(DOMAIN_FAILED_PATTERN, UID,
                TOPOLOGY_MISMATCH_ERROR, "Test this failure",
                EventConstants.TOPOLOGY_MISMATCH_ERROR_SUGGESTION)), is(true));
  }

  @Test
  void whenCreateEventStepCalledWithFailedReasonDTopologyMismatchTwice_domainFailedEventCreatedOnceWithExpectedCount() {
    testSupport.runSteps(createTopologyMismatchFailureSteps("Test failure"));
    dispatchAddedEventWatches();
    testSupport.runSteps(createTopologyMismatchFailureSteps("Test failure"));

    assertThat("Found DOMAIN_FAILED event",
        containsOneEventWithCount(getEvents(testSupport), DOMAIN_FAILED_EVENT, 2), is(true));
  }

  @Test
  void whenCreateEventStepCalledWithFailedReasonDomainInvalid_domainFailedEventCreated() {
    testSupport.runSteps(createDomainInvalidFailureSteps("Test failure"));

    assertThat("Found DOMAIN_FAILED event",
        containsEvent(getEvents(testSupport), DOMAIN_FAILED_EVENT), is(true));
  }

  @Test
  void whenCreateEventStepCalledWithFailedReasonDomainInvalid_domainFailedEventCreatedWithExpectedMessage() {
    testSupport.runSteps(createDomainInvalidFailureSteps("Test this failure"));

    assertThat("Found DOMAIN_FAILED event with expected message",
        containsEventWithMessage(getEvents(testSupport),
            DOMAIN_FAILED_EVENT,
            String.format(DOMAIN_FAILED_PATTERN, UID,
                EventConstants.DOMAIN_INVALID_ERROR, "Test this failure",
                EventConstants.DOMAIN_INVALID_ERROR_SUGGESTION)), is(true));
  }

  @Test
  void whenCreateEventStepCalledWithFailedReasonDomainInvalidTwice_domainFailedEventCreatedOnceWithExpectedCount() {
    testSupport.runSteps(createDomainInvalidFailureSteps("Test failure"));
    dispatchAddedEventWatches();
    testSupport.runSteps(createDomainInvalidFailureSteps("Test failure"));

    assertThat("Found DOMAIN_FAILED event",
        containsOneEventWithCount(getEvents(testSupport), DOMAIN_FAILED_EVENT, 2), is(true));
  }

  @Test
  void whenMakeRightCalled_withCreatedEventData_domainCreatedEventCreated() {
    makeRightOperation.withEventData(DOMAIN_CREATED, null).execute();

    assertThat("Found DOMAIN_CREATED event",
        containsEvent(getEvents(testSupport), DOMAIN_CREATED_EVENT), is(true));
  }

  @Test
  void whenMakeRightCalled_withCreatedEventData_domainCreatedEventCreatedWithExpectedMessage() {
    makeRightOperation.withEventData(DOMAIN_CREATED, null).execute();

    assertThat("Found DOMAIN_CREATED event with expected message",
        containsEventWithMessage(getEvents(testSupport),
            DOMAIN_CREATED_EVENT,
            String.format(DOMAIN_CREATED_PATTERN, UID)), is(true));
  }

  @Test
  void whenMakeRightCalled_withChangedEventData_domainChangedEventCreated() {
    makeRightOperation.withEventData(DOMAIN_CHANGED, null).execute();

    assertThat("Found DOMAIN_CHANGED event",
        containsEvent(getEvents(testSupport), EventConstants.DOMAIN_CHANGED_EVENT), is(true));
  }

  @Test
  void whenMakeRightCalled_withChangedEventData_domainChangedEventCreatedWithExpectedMessage() {
    makeRightOperation.withEventData(DOMAIN_CHANGED, null).execute();

    assertThat("Found DOMAIN_CHANGED event with expected message",
        containsEventWithMessage(getEvents(testSupport),
            EventConstants.DOMAIN_CHANGED_EVENT,
            String.format(DOMAIN_CHANGED_PATTERN, UID)), is(true));
  }

  @Test
  void whenDomainChangedEventCreateCalledTwice_domainChangedEventCreatedOnceWithExpectedCount() {
    presenceInfoMap.put(NS, Map.of(UID, info));
    testSupport.runSteps(Step.chain(createEventStep(new EventData(DOMAIN_CHANGED))));
    dispatchAddedEventWatches();

    testSupport.runSteps(Step.chain(createEventStep(new EventData(DOMAIN_CHANGED))));

    presenceInfoMap.remove(NS);
    assertThat("Found DOMAIN_CHANGED event with expected count",
        containsOneEventWithCount(getEvents(testSupport), EventConstants.DOMAIN_CHANGED_EVENT, 2), is(true));
  }

  @Test
  void whenMakeRightCalled_withDeletedEventData_domainDeletedEventCreated() {
    makeRightOperation.withEventData(DOMAIN_DELETED, null).execute();

    assertThat("Found DOMAIN_DELETED event",
        containsEvent(getEvents(testSupport), EventConstants.DOMAIN_DELETED_EVENT), is(true));
  }

  @Test
  void whenMakeRightCalled_withDeletedEventData_domainDeletedEventCreatedWithExpectedMessage() {
    makeRightOperation.withEventData(DOMAIN_DELETED, null).execute();

    assertThat("Found DOMAIN_DELETED event with expected message",
        containsEventWithMessage(getEvents(testSupport),
            EventConstants.DOMAIN_DELETED_EVENT,
            String.format(DOMAIN_DELETED_PATTERN, UID)), is(true));
  }

  @Test
  void whenCreateEventStepCalledWithFailedEventNoRetry_domainFailedEventCreated() {
    testSupport.runSteps(Step.chain(
        createEventStep(new EventData(DOMAIN_FAILED)),
        createEventStep(new EventData(EventHelper.EventItem.DOMAIN_FAILED)
            .message("Test this failure")))
    );

    assertThat("Found DOMAIN_FAILED event",
        containsEvent(getEvents(testSupport), EventConstants.DOMAIN_FAILED_EVENT), is(true));
  }

  @Test
  void whenCreateEventStepCalledWithFailedEventNoRetry_domainFailedEventCreatedWithExpectedMessage() {
    testSupport.runSteps(Step.chain(
        createEventStep(new EventData(DOMAIN_FAILED)),
        createEventStep(new EventData(EventHelper.EventItem.DOMAIN_FAILED)
            .message("Test this failure")
            .failureReason(DomainFailureReason.Aborted)
            .additionalMessage(WILL_NOT_RETRY)))
    );

    assertThat("Found DOMAIN_FAILED event with expected message",
        containsEventWithMessage(getEvents(testSupport),
            EventConstants.DOMAIN_FAILED_EVENT,
            String.format(EventConstants.DOMAIN_FAILED_PATTERN, UID,
                EventConstants.ABORTED_ERROR, "Test this failure", WILL_NOT_RETRY)),
        is(true));
  }

  @Test
  void whenMakeRightCalled_withAvailableEventData_domainAvailableEventCreated() {
    makeRightOperation.withEventData(DOMAIN_AVAILABLE, null).execute();

    assertThat("Found DOMAIN_AVAILABLE event",
        containsEvent(getEvents(testSupport), DOMAIN_AVAILABLE_EVENT), is(true));
  }

  @Test
  void whenMakeRightCalled_withAvailableEventData_domainAvailableEventCreatedWithExpectedMessage() {
    makeRightOperation.withEventData(DOMAIN_AVAILABLE, null).execute();

    assertThat("Found DOMAIN_AVAILABLE event with expected message",
        containsEventWithMessage(getEvents(testSupport),
            DOMAIN_AVAILABLE_EVENT,
            String.format(DOMAIN_AVAILABLE_PATTERN, UID)), is(true));
  }

  @Test
  void whenMakeRightCalled_withUnavailableEventData_domainUnavailableEventCreated() {
    makeRightOperation.withEventData(DOMAIN_UNAVAILABLE, null).execute();

    assertThat("Found DOMAIN_UNAVAILABLE event",
        containsEvent(getEvents(testSupport), DOMAIN_UNAVAILABLE_EVENT), is(true));
  }

  @Test
  void whenMakeRightCalled_withUnavailableEventData_domainUnavailableEventCreatedWithExpectedMessage() {
    makeRightOperation.withEventData(DOMAIN_UNAVAILABLE, null).execute();

    assertThat("Found DOMAIN_UNAVAILABLE event with expected message",
        containsEventWithMessage(getEvents(testSupport),
            DOMAIN_UNAVAILABLE_EVENT,
            String.format(DOMAIN_UNAVAILABLE_PATTERN, UID)), is(true));
  }

  @Test
  void whenMakeRightCalled_withIncompleteEventData_domainIncompleteEventCreated() {
    makeRightOperation.withEventData(DOMAIN_INCOMPLETE, null).execute();

    assertThat("Found DOMAIN_INCOMPLETE event",
        containsEvent(getEvents(testSupport), DOMAIN_INCOMPLETE_EVENT), is(true));
  }

  @Test
  void whenMakeRightCalled_withIncompleteEventData_domainIncompleteEventCreatedWithExpectedMessage() {
    makeRightOperation.withEventData(DOMAIN_INCOMPLETE, null).execute();

    assertThat("Found DOMAIN_INCOMPLETE event with expected message",
        containsEventWithMessage(getEvents(testSupport),
            DOMAIN_INCOMPLETE_EVENT,
            String.format(DOMAIN_INCOMPLETE_PATTERN, UID)), is(true));
  }

  @Test
  void whenMakeRightCalled_withFailureResolvedEventData_domainFailureResolvedEventCreated() {
    makeRightOperation.withEventData(DOMAIN_FAILURE_RESOLVED, null).execute();

    assertThat("Found DOMAIN_FAILURE_RESOLVED event",
        containsEvent(getEvents(testSupport), DOMAIN_FAILURE_RESOLVED_EVENT), is(true));
  }

  @Test
  void whenMakeRightCalled_withFailureResolvedEventData_domainFailureResolvedEventCreatedWithExpectedMessage() {
    makeRightOperation.withEventData(DOMAIN_FAILURE_RESOLVED, null).execute();

    assertThat("Found DOMAIN_FAILURE_RESOLVED event with expected message",
        containsEventWithMessage(getEvents(testSupport),
            DOMAIN_FAILURE_RESOLVED_EVENT,
            String.format(DOMAIN_FAILURE_RESOLVED_PATTERN, UID)), is(true));
  }

  @Test
  void whenCreateEventStepCalledForStartManagingNamespace_eventCreatedWithExpectedMessage() {
    testSupport.runSteps(createEventStep(new EventData(START_MANAGING_NAMESPACE).namespace(OP_NS).resourceName(NS)));
    assertThat("Found NAMESPACE_WATCHING_STARTED event with expected message",
        containsEventWithMessage(getEvents(testSupport),
            EventConstants.START_MANAGING_NAMESPACE_EVENT,
            String.format(EventConstants.START_MANAGING_NAMESPACE_PATTERN, NS)), is(true));
  }

  @Test
  void whenCreateEventStepCalledWithNSWatchStartedEvent_eventCreatedWithExpectedNamespace() {
    testSupport.runSteps(createEventStep(new EventData(NAMESPACE_WATCHING_STARTED).namespace(NS).resourceName(NS)));
    assertThat("Found NAMESPACE_WATCHING_STARTED event with expected namespace",
        containsEventWithNamespace(getEvents(testSupport),
            NAMESPACE_WATCHING_STARTED_EVENT, NS), is(true));
  }

  @Test
  void whenCreateEventStepCalledWithNSWatchStartedEvent_eventCreatedWithExpectedLabels() {
    testSupport.runSteps(createEventStep(new EventData(NAMESPACE_WATCHING_STARTED).namespace(NS).resourceName(NS)));

    Map<String, String> expectedLabels = new HashMap<>();
    expectedLabels.put(LabelConstants.CREATEDBYOPERATOR_LABEL, "true");
    assertThat("Found NAMESPACE_WATCHING_STARTED event with expected labels",
        containsEventWithLabels(getEvents(testSupport),
            NAMESPACE_WATCHING_STARTED_EVENT, expectedLabels), is(true));
  }

  @Test
  void whenNSWatchStartedEventCreatedTwice_eventCreatedOnceWithExpectedCount() {
    testSupport.runSteps(createEventStep(new EventData(NAMESPACE_WATCHING_STARTED).namespace(NS).resourceName(NS)));
    dispatchAddedEventWatches();
    testSupport.runSteps(createEventStep(new EventData(NAMESPACE_WATCHING_STARTED).namespace(NS).resourceName(NS)));

    assertThat("Found 1 NAMESPACE_WATCHING_STARTED event with expected count",
        containsOneEventWithCount(getEvents(testSupport),
            NAMESPACE_WATCHING_STARTED_EVENT, 2), is(true));
  }

  @Test
  void whenNSWatchStartedEventCreated_thenDelete_eventCreatedTwice() {
    testSupport.runSteps(createEventStep(new EventData(NAMESPACE_WATCHING_STARTED).namespace(NS).resourceName(NS)));
    dispatchAddedEventWatches();
    dispatchDeletedEventWatches();
    testSupport.runSteps(createEventStep(new EventData(NAMESPACE_WATCHING_STARTED).namespace(NS).resourceName(NS)));

    assertThat("Found 2 NAMESPACE_WATCHING_STARTED events",
        containsEventsWithCountOne(getEvents(testSupport),
            NAMESPACE_WATCHING_STARTED_EVENT, 2), is(true));
  }

  @Test
  void whenNSWatchStartedEventCreated_fail403OnCreate_foundExpectedLogMessage() {
    loggerControl.collectLogMessages(logRecords, CREATING_EVENT_FORBIDDEN);
    testSupport.failOnCreate(EVENT, NS, HTTP_FORBIDDEN);

    testSupport.runSteps(createEventStep(new EventData(NAMESPACE_WATCHING_STARTED).namespace(NS).resourceName(NS)));

    assertThat(logRecords, containsWarning(CREATING_EVENT_FORBIDDEN));
  }

  @Test
  void whenNSWatchStartedEventCreated_fail403OnCreate_startManagingNSFailedEventGenerated() {
    testSupport.failOnCreate(EVENT, NS, HTTP_FORBIDDEN);

    testSupport.runSteps(createEventStep(new EventData(NAMESPACE_WATCHING_STARTED).namespace(NS).resourceName(NS)));

    assertThat("Found 1 NAMESPACE_WATCHING_STARTED_FAILED event",
        containsEventsWithCountOne(getEvents(testSupport),
            START_MANAGING_NAMESPACE_FAILED_EVENT, 1), is(true));
  }

  @Test
  void whenNSWatchStartedEventCreated_fail403OnCreate_startManagingNSFailedEventGeneratedWithExpectedMessage() {
    testSupport.failOnCreate(EVENT, NS, HTTP_FORBIDDEN);

    testSupport.runSteps(createEventStep(new EventData(NAMESPACE_WATCHING_STARTED).namespace(NS).resourceName(NS)));

    assertThat("Found 1 NAMESPACE_WATCHING_STARTED_FAILED event with expected message",
        containsEventWithMessage(getEvents(testSupport),
            EventConstants.START_MANAGING_NAMESPACE_FAILED_EVENT,
            String.format(EventConstants.START_MANAGING_NAMESPACE_FAILED_PATTERN, NS)), is(true));
  }

  @Test
  void whenNSWatchStartedEventCreated_fail403OnCreate_startManagingNSFailedEventGeneratedWithExpectedLabel() {
    testSupport.failOnCreate(EVENT, NS, HTTP_FORBIDDEN);

    testSupport.runSteps(createEventStep(new EventData(NAMESPACE_WATCHING_STARTED).namespace(NS).resourceName(NS)));
    Map<String, String> expectedLabels = new HashMap<>();
    expectedLabels.put(LabelConstants.CREATEDBYOPERATOR_LABEL, "true");

    assertThat("Found 1 NAMESPACE_WATCHING_STARTED_FAILED event with expected label",
        containsEventWithLabels(getEvents(testSupport),
            START_MANAGING_NAMESPACE_FAILED_EVENT, expectedLabels), is(true));
  }

  @Test
  void whenNSWatchStartedEventCreated_fail403OnCreate_startManagingNSFailedEventGeneratedWithExpectedNS() {
    testSupport.failOnCreate(EVENT, NS, HTTP_FORBIDDEN);

    testSupport.runSteps(createEventStep(new EventData(NAMESPACE_WATCHING_STARTED).namespace(NS).resourceName(NS)));

    assertThat("Found 1 NAMESPACE_WATCHING_STARTED_FAILED event with expected namespace",
        containsEventWithNamespace(getEvents(testSupport),
            EventConstants.START_MANAGING_NAMESPACE_FAILED_EVENT, OP_NS), is(true));
  }

  @Test
  void whenCreateEventStepCalledWithNSWatchStoppedEvent_eventCreatedWithExpectedLabels() {
    testSupport.runSteps(createEventStep(new EventData(NAMESPACE_WATCHING_STOPPED).namespace(NS).resourceName(NS)));

    Map<String, String> expectedLabels = new HashMap<>();
    expectedLabels.put(LabelConstants.CREATEDBYOPERATOR_LABEL, "true");
    assertThat("Found NAMESPACE_WATCHING_STOPPED event with expected labels",
        containsEventWithLabels(getEvents(testSupport),
            NAMESPACE_WATCHING_STOPPED_EVENT, expectedLabels), is(true));
  }

  @Test
  void whenNSWatchStoppedEventCreated_eventCreatedWithExpectedInvolvedObject() {
    testSupport.runSteps(createEventStep(new EventData(NAMESPACE_WATCHING_STOPPED).namespace(NS).resourceName(NS)));

    assertThat("Found NAMESPACE_WATCHING_STOPPED event with expected involvedObject",
        containsEventWithInvolvedObject(getEvents(testSupport),
            NAMESPACE_WATCHING_STOPPED_EVENT, NS, NS),
        is(true));
  }

  @Test
  void whenNSWatchStoppedEventCreated_fail404OnReplace_eventCreatedTwice() {
    testSupport.runSteps(createEventStep(new EventData(NAMESPACE_WATCHING_STOPPED).namespace(NS).resourceName(NS)));
    dispatchAddedEventWatches();

    CoreV1Event event = EventTestUtils.getEventWithReason(getEvents(testSupport), NAMESPACE_WATCHING_STOPPED_EVENT);
    testSupport.failOnReplace(EVENT, EventTestUtils.getName(event), NS, HTTP_NOT_FOUND);

    testSupport.runSteps(createEventStep(
        new EventData(NAMESPACE_WATCHING_STOPPED).namespace(NS).resourceName(NS)));

    assertThat("Found 2 NAMESPACE_WATCHING_STOPPED events",
        containsEventsWithCountOne(getEvents(testSupport), NAMESPACE_WATCHING_STOPPED_EVENT, 2));
  }

  @Test
  void whenNSWatchStoppedEventCreated_fail403OnCreate_foundExpectedLogMessage() {
    loggerControl.withLogLevel(Level.INFO).collectLogMessages(logRecords, CREATING_EVENT_FORBIDDEN);
    testSupport.failOnCreate(EVENT, NS, HTTP_FORBIDDEN);

    testSupport.runSteps(createEventStep(new EventData(NAMESPACE_WATCHING_STOPPED).namespace(NS).resourceName(NS)));

    assertThat(logRecords, containsInfo(CREATING_EVENT_FORBIDDEN, NAMESPACE_WATCHING_STOPPED_EVENT, NS));
  }

  @Test
  void whenNSWatchStoppedEventCreatedTwice_fail403OnReplace_eventCreatedOnce() {
    testSupport.runSteps(Step.chain(createEventStep(new EventData(NAMESPACE_WATCHING_STOPPED))));

    CoreV1Event event = EventTestUtils.getEventWithReason(getEvents(testSupport), NAMESPACE_WATCHING_STOPPED_EVENT);
    dispatchAddedEventWatches();
    testSupport.failOnReplace(EVENT, EventTestUtils.getName(event), NS, HTTP_FORBIDDEN);

    testSupport.runSteps(Step.chain(createEventStep(new EventData(NAMESPACE_WATCHING_STOPPED))));

    assertThat("Found 1 NAMESPACE_WATCHING_STOPPED event with expected count 1",
        containsOneEventWithCount(getEvents(testSupport), NAMESPACE_WATCHING_STOPPED_EVENT, 1), is(true));
  }

  @Test
  void whenNSWatchStoppedEventCreatedTwice_fail403OnReplace_foundExpectedLogMessage() {
    loggerControl.withLogLevel(Level.INFO).collectLogMessages(logRecords, CREATING_EVENT_FORBIDDEN);
    testSupport.runSteps(Step.chain(createEventStep(new EventData(NAMESPACE_WATCHING_STOPPED))));

    CoreV1Event event = EventTestUtils.getEventWithReason(getEvents(testSupport), NAMESPACE_WATCHING_STOPPED_EVENT);
    dispatchAddedEventWatches();
    testSupport.failOnReplace(EVENT, EventTestUtils.getName(event), NS, HTTP_FORBIDDEN);

    testSupport.runSteps(Step.chain(createEventStep(new EventData(NAMESPACE_WATCHING_STOPPED))));

    assertThat(logRecords, containsInfo(CREATING_EVENT_FORBIDDEN, NAMESPACE_WATCHING_STOPPED_EVENT, NS));
  }

  @Test
  void whenCreateEventStepCalledForNSWatchStartedEvent_eventCreatedWithExpectedMessage() {
    testSupport.runSteps(createEventStep(new EventData(NAMESPACE_WATCHING_STARTED).namespace(NS).resourceName(NS)));
    assertThat("Found START_MANAGING_NAMESPACE event with expected message",
        containsEventWithMessage(getEvents(testSupport),
            NAMESPACE_WATCHING_STARTED_EVENT,
            String.format(EventConstants.NAMESPACE_WATCHING_STARTED_PATTERN, NS)), is(true));
  }

  @Test
  void whenCreateEventStepCalledForStartManagingNS_eventCreatedWithExpectedNamespace() {
    testSupport.runSteps(createEventStep(new EventData(START_MANAGING_NAMESPACE).namespace(OP_NS).resourceName(NS)));
    assertThat("Found START_MANAGING_NAMESPACE event with expected namespace",
        containsEventWithNamespace(getEvents(testSupport),
            EventConstants.START_MANAGING_NAMESPACE_EVENT, OP_NS), is(true));
  }

  @Test
  void whenCreateEventStepCalledForStartManagingNS_eventCreatedWithExpectedLabels() {
    testSupport.runSteps(createEventStep(new EventData(START_MANAGING_NAMESPACE).namespace(OP_NS).resourceName(NS)));

    Map<String, String> expectedLabels = new HashMap<>();
    expectedLabels.put(LabelConstants.CREATEDBYOPERATOR_LABEL, "true");
    assertThat("Found START_MANAGING_NAMESPACE event with expected labels",
        containsEventWithLabels(getEvents(testSupport),
            EventConstants.START_MANAGING_NAMESPACE_EVENT, expectedLabels), is(true));
  }

  @Test
  void whenStartManagingNSEventCreatedTwice_eventCreatedOnceWithExpectedCount() {
    Step step = createEventStep(new EventData(START_MANAGING_NAMESPACE).namespace(OP_NS).resourceName(NS));
    testSupport.runSteps(step);
    dispatchAddedEventWatches();
    testSupport.runSteps(step);

    assertThat("Found 1 START_MANAGING_NAMESPACE event with expected count",
        containsOneEventWithCount(getEvents(testSupport),
            EventConstants.START_MANAGING_NAMESPACE_EVENT, 2), is(true));
  }

  @Test
  void whenStartManagingNSEventCreated_thenDelete_eventCreatedTwice() {
    Step step = createEventStep(new EventData(START_MANAGING_NAMESPACE).namespace(OP_NS).resourceName(NS));
    testSupport.runSteps(step);
    dispatchAddedEventWatches();
    dispatchDeletedEventWatches();
    testSupport.runSteps(step);

    assertThat("Found 2 START_MANAGING_NAMESPACE events",
        containsEventsWithCountOne(getEvents(testSupport),
            EventConstants.START_MANAGING_NAMESPACE_EVENT, 2), is(true));
  }

  @Test
  void whenCreateEventStepCalledForStopManagingNS_eventCreatedWithExpectedLabels() {
    testSupport.runSteps(createEventStep(new EventData(STOP_MANAGING_NAMESPACE).namespace(OP_NS).resourceName(NS)));

    Map<String, String> expectedLabels = new HashMap<>();
    expectedLabels.put(LabelConstants.CREATEDBYOPERATOR_LABEL, "true");
    assertThat("Found STOP_MANAGING_NAMESPACE event with expected labels",
        containsEventWithLabels(getEvents(testSupport),
            STOP_MANAGING_NAMESPACE_EVENT, expectedLabels), is(true));
  }

  @Test
  void whenCreateEventStepCalledForStopManagingNS_eventCreatedWithExpectedInvolvedObject() {
    testSupport.runSteps(createEventStep(new EventData(STOP_MANAGING_NAMESPACE).namespace(OP_NS).resourceName(NS)));

    assertThat("Found STOP_MANAGING_NAMESPACE event with expected involvedObject",
        containsEventWithInvolvedObject(getEvents(testSupport),
            STOP_MANAGING_NAMESPACE_EVENT, OPERATOR_POD_NAME, OP_NS),
        is(true));
  }

  @Test
  void whenStopManagingNSEventCreated_fail404OnReplace_eventCreatedWithExpectedCount() {
    Step step = createEventStep(new EventData(STOP_MANAGING_NAMESPACE).namespace(OP_NS).resourceName(NS));
    testSupport.runSteps(step);
    dispatchAddedEventWatches();

    CoreV1Event event = EventTestUtils.getEventWithReason(getEvents(testSupport), STOP_MANAGING_NAMESPACE_EVENT);
    testSupport.failOnReplace(EVENT, EventTestUtils.getName(event), OP_NS, HTTP_NOT_FOUND);

    testSupport.runSteps(step);

    assertThat("Found 2 STOP_MANAGING_NAMESPACE events",
        getNumberOfEvents(getEvents(testSupport), STOP_MANAGING_NAMESPACE_EVENT), equalTo(2));
  }

  @Test
  void whenStopManagingNSEventCreatedTwice_fail403OnReplace_eventCreatedOnce() {
    Step eventStep = createEventStep(new EventData(STOP_MANAGING_NAMESPACE).namespace(OP_NS).resourceName(NS));

    testSupport.runSteps(eventStep);
    dispatchAddedEventWatches();

    CoreV1Event event = EventTestUtils.getEventWithReason(getEvents(testSupport), STOP_MANAGING_NAMESPACE_EVENT);
    testSupport.failOnReplace(EVENT, EventTestUtils.getName(event), NS, HTTP_FORBIDDEN);

    testSupport.runSteps(eventStep);

    assertThat("Found 1 STOP_MANAGING_NAMESPACE events",
        getNumberOfEvents(getEvents(testSupport), STOP_MANAGING_NAMESPACE_EVENT), equalTo(1));
  }

  @Test
  void whenNSWatchStoppedEventCreatedTwice_fail409OnReplace_eventCreatedOnceWithExpectedCount() {
    testSupport.addRetryStrategy(retryStrategy);
    Step eventStep = createEventStep(new EventData(NAMESPACE_WATCHING_STOPPED).namespace(NS).resourceName(NS));

    testSupport.runSteps(eventStep);
    dispatchAddedEventWatches();

    CoreV1Event event = EventTestUtils.getEventWithReason(getEvents(testSupport), NAMESPACE_WATCHING_STOPPED_EVENT);
    testSupport.failOnReplace(EVENT, EventTestUtils.getName(event), NS, HTTP_CONFLICT);

    testSupport.runSteps(eventStep);

    assertThat("Found 2 NAMESPACE_WATCHING_STOPPED event with expected count 1",
            containsEventsWithCountOne(getEvents(testSupport),
            NAMESPACE_WATCHING_STOPPED_EVENT, 2), is(true));
  }

  @Test
  void whenNSWatchStoppedEventCreatedTwice_fail503OnReplace_eventCreatedOnceWithExpectedCount() {
    testSupport.addRetryStrategy(retryStrategy);
    Step eventStep = createEventStep(new EventData(NAMESPACE_WATCHING_STOPPED).namespace(NS).resourceName(NS));

    testSupport.runSteps(eventStep);
    dispatchAddedEventWatches();

    CoreV1Event event = EventTestUtils.getEventWithReason(getEvents(testSupport), NAMESPACE_WATCHING_STOPPED_EVENT);
    testSupport.failOnReplace(EVENT, EventTestUtils.getName(event), NS, HTTP_UNAVAILABLE);

    testSupport.runSteps(eventStep);

    assertThat("Found 1 NAMESPACE_WATCHING_STOPPED event with expected count 2",
        containsOneEventWithCount(getEvents(testSupport),
            NAMESPACE_WATCHING_STOPPED_EVENT, 2), is(true));
  }

  @Test
  void whenDomainRollStartingEventCreateCalled_domainRollStartingEventCreatedWithExpectedCount() {
    testSupport.runSteps(createEventStep(new EventData(DOMAIN_ROLL_STARTING)));

    assertThat("Found DOMAIN_ROLL_STARTING event with expected count",
        containsOneEventWithCount(getEvents(testSupport), DOMAIN_ROLL_STARTING_EVENT, 1), is(true));
  }

  @Test
  void whenDomainRollStartingEventCreateCalled_domainRollStartingEventCreatedWithExpectedMessage() {
    testSupport.runSteps(createEventStep(new EventData(DOMAIN_ROLL_STARTING).message("abcde")));

    assertThat("Found DOMAIN_ROLL_STARTING event with expected message",
        containsEventWithMessage(getEvents(testSupport),
            DOMAIN_ROLL_STARTING_EVENT,
            String.format(EventConstants.DOMAIN_ROLL_STARTING_PATTERN, UID, "abcde")), is(true));
  }

  @Test
  void whenDomainRollCompletedEventCreateCalled_domainRollCompletedEventCreatedWithExpectedCount() {
    testSupport.runSteps(createEventStep(new EventData(DOMAIN_ROLL_COMPLETED)));

    assertThat("Found DOMAIN_ROLL_COMPLETED event with expected count",
        containsOneEventWithCount(getEvents(testSupport), DOMAIN_ROLL_COMPLETED_EVENT, 1), is(true));
  }

  @Test
  void whenDomainRollCompletedEventCreateCalled_domainRollCompletedEventCreatedWithExpectedMessage() {
    testSupport.runSteps(createEventStep(new EventData(DOMAIN_ROLL_COMPLETED)));

    assertThat("Found DOMAIN_ROLL_COMPLETED event with expected message",
        containsEventWithMessage(getEvents(testSupport),
            DOMAIN_ROLL_COMPLETED_EVENT,
            String.format(EventConstants.DOMAIN_ROLL_COMPLETED_PATTERN, UID)), is(true));
  }

  @Test
  void whenPodCycleStartingEventCreateCalled_podCycleStartingEventCreatedWithExpectedCount() {
    testSupport.runSteps(createEventStep(new EventData(POD_CYCLE_STARTING)));

    assertThat("Found POD_CYCLE_STARTING event with expected count",
        containsOneEventWithCount(getEvents(testSupport), POD_CYCLE_STARTING_EVENT, 1), is(true));
  }

  @Test
  void whenPodCycleStartingEventCreateCalled_podCycleStartingEventCreatedWithExpectedMessage() {
    testSupport.runSteps(createEventStep(new EventData(POD_CYCLE_STARTING).podName("12345").message("abcde")));

    assertThat("Found POD_CYCLE_STARTING event with expected message",
        containsEventWithMessage(getEvents(testSupport),
            POD_CYCLE_STARTING_EVENT,
            String.format(EventConstants.POD_CYCLE_STARTING_PATTERN, "12345", "abcde")), is(true));
  }

  private void dispatchAddedEventWatches() {
    List<CoreV1Event> events = getEvents(testSupport);
    for (CoreV1Event event : events) {
      dispatchAddedEventWatch(event);
    }
  }

  private void dispatchAddedEventWatch(CoreV1Event event) {
    processor.dispatchEventWatch(WatchEvent.createAddedEvent(event).toWatchResponse());
  }

  private void dispatchDeletedEventWatches() {
    List<CoreV1Event> events = getEvents(testSupport);
    for (CoreV1Event event : events) {
      dispatchDeletedEventWatch(event);
    }
  }

  private void dispatchDeletedEventWatch(CoreV1Event event) {
    processor.dispatchEventWatch(WatchEvent.createDeletedEvent(event).toWatchResponse());
  }
}
