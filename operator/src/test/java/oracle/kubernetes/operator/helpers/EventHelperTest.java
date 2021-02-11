// Copyright (c) 2020, 2021, Oracle and/or its affiliates.
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
import io.kubernetes.client.openapi.models.V1ObjectMeta;
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
import oracle.kubernetes.operator.work.TerminalStep;
import oracle.kubernetes.utils.TestUtils;
import oracle.kubernetes.weblogic.domain.model.Domain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static oracle.kubernetes.operator.DomainProcessorTestSetup.NS;
import static oracle.kubernetes.operator.DomainProcessorTestSetup.UID;
import static oracle.kubernetes.operator.DomainStatusUpdater.createFailureRelatedSteps;
import static oracle.kubernetes.operator.EventConstants.DOMAIN_CHANGED_PATTERN;
import static oracle.kubernetes.operator.EventConstants.DOMAIN_CREATED_EVENT;
import static oracle.kubernetes.operator.EventConstants.DOMAIN_CREATED_PATTERN;
import static oracle.kubernetes.operator.EventConstants.DOMAIN_DELETED_PATTERN;
import static oracle.kubernetes.operator.EventConstants.DOMAIN_PROCESSING_ABORTED_PATTERN;
import static oracle.kubernetes.operator.EventConstants.DOMAIN_PROCESSING_COMPLETED_EVENT;
import static oracle.kubernetes.operator.EventConstants.DOMAIN_PROCESSING_COMPLETED_PATTERN;
import static oracle.kubernetes.operator.EventConstants.DOMAIN_PROCESSING_FAILED_EVENT;
import static oracle.kubernetes.operator.EventConstants.DOMAIN_PROCESSING_FAILED_PATTERN;
import static oracle.kubernetes.operator.EventConstants.DOMAIN_PROCESSING_RETRYING_PATTERN;
import static oracle.kubernetes.operator.EventConstants.DOMAIN_PROCESSING_STARTING_EVENT;
import static oracle.kubernetes.operator.EventConstants.DOMAIN_PROCESSING_STARTING_PATTERN;
import static oracle.kubernetes.operator.EventConstants.NAMESPACE_WATCHING_STOPPED_EVENT;
import static oracle.kubernetes.operator.EventConstants.STOP_MANAGING_NAMESPACE_EVENT;
import static oracle.kubernetes.operator.EventTestUtils.containsEvent;
import static oracle.kubernetes.operator.EventTestUtils.containsEventWithComponent;
import static oracle.kubernetes.operator.EventTestUtils.containsEventWithInstance;
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
import static oracle.kubernetes.operator.helpers.EventHelper.EventItem.DOMAIN_CHANGED;
import static oracle.kubernetes.operator.helpers.EventHelper.EventItem.DOMAIN_CREATED;
import static oracle.kubernetes.operator.helpers.EventHelper.EventItem.DOMAIN_DELETED;
import static oracle.kubernetes.operator.helpers.EventHelper.EventItem.DOMAIN_PROCESSING_ABORTED;
import static oracle.kubernetes.operator.helpers.EventHelper.EventItem.DOMAIN_PROCESSING_COMPLETED;
import static oracle.kubernetes.operator.helpers.EventHelper.EventItem.DOMAIN_PROCESSING_FAILED;
import static oracle.kubernetes.operator.helpers.EventHelper.EventItem.DOMAIN_PROCESSING_RETRYING;
import static oracle.kubernetes.operator.helpers.EventHelper.EventItem.DOMAIN_PROCESSING_STARTING;
import static oracle.kubernetes.operator.helpers.EventHelper.EventItem.NAMESPACE_WATCHING_STARTED;
import static oracle.kubernetes.operator.helpers.EventHelper.EventItem.NAMESPACE_WATCHING_STOPPED;
import static oracle.kubernetes.operator.helpers.EventHelper.EventItem.START_MANAGING_NAMESPACE;
import static oracle.kubernetes.operator.helpers.EventHelper.EventItem.STOP_MANAGING_NAMESPACE;
import static oracle.kubernetes.operator.helpers.EventHelper.createEventStep;
import static oracle.kubernetes.operator.logging.MessageKeys.CREATING_EVENT_FORBIDDEN;
import static oracle.kubernetes.utils.LogMatcher.containsInfo;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

public class EventHelperTest {
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
  private final TestUtils.ConsoleHandlerMemento loggerControl = TestUtils.silenceOperatorLogger();
  private final Collection<LogRecord> logRecords = new ArrayList<>();

  @BeforeEach
  public void setUp() throws Exception {
    mementos.add(TestUtils.silenceOperatorLogger());
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
  public void tearDown() {
    mementos.forEach(Memento::revert);
  }

  @Test
  public void whenDomainMakeRightCalled_domainProcessingStartingEventCreated() {
    makeRightOperation.execute();

    assertThat("Found DOMAIN_PROCESSING_STARTING event",
        containsEvent(getEvents(testSupport), DOMAIN_PROCESSING_STARTING_EVENT), is(true));
  }

  @Test
  public void whenDomainMakeRightCalled_domainProcessingStartingEventCreatedWithExpectedLabels() {
    makeRightOperation.execute();

    Map<String, String> expectedLabels = new HashMap<>();
    expectedLabels.put(LabelConstants.DOMAINUID_LABEL, UID);
    expectedLabels.put(LabelConstants.CREATEDBYOPERATOR_LABEL, "true");
    assertThat("Found DOMAIN_PROCESSING_STARTING event with expected labels",
        containsEventWithLabels(getEvents(testSupport),
            DOMAIN_PROCESSING_STARTING_EVENT, expectedLabels), is(true));
  }

  @Test
  public void whenDomainMakeRightCalled_domainProcessingStartingEventCreatedWithExpectedNamespace() {
    makeRightOperation.execute();

    assertThat("Found DOMAIN_PROCESSING_STARTING event with expected namespace",
        containsEventWithNamespace(getEvents(testSupport),
            DOMAIN_PROCESSING_STARTING_EVENT, NS), is(true));
  }

  @Test
  public void whenDomainMakeRightCalled_domainProcessingStartingEventCreatedWithExpectedMessage() {
    makeRightOperation.execute();

    assertThat("Found DOMAIN_PROCESSING_STARTING event with expected message",
        containsEventWithMessage(getEvents(testSupport),
            DOMAIN_PROCESSING_STARTING_EVENT,
            String.format(DOMAIN_PROCESSING_STARTING_PATTERN, UID)), is(true));
  }

  @Test
  public void whenDomainMakeRightCalled_domainProcessingStartingEventCreatedWithInvolvedObject() {
    V1ObjectMeta metadata = domain.getMetadata();
    String k8sUID = metadata.getUid();

    makeRightOperation.execute();
    assertThat("Found DOMAIN_PROCESSING_STARTING event with expected involved object",
        containsEventWithInvolvedObject(
            getEvents(testSupport),
            DOMAIN_PROCESSING_STARTING_EVENT, UID, NS, k8sUID),
        is(true));
  }

  @Test
  public void whenDomainMakeRightCalled_domainProcessingStartingEventCreatedWithReportingComponent() {
    makeRightOperation.execute();

    assertThat("Found DOMAIN_PROCESSING_STARTING event with expected reporting component",
        containsEventWithComponent(getEvents(testSupport), DOMAIN_PROCESSING_STARTING_EVENT), is(true));
  }

  @Test
  public void whenCreateEventStepCalledForStarting_domainProcessingStartingEventCreatedWithReportingInstance() {
    String namespaceFromHelm = NamespaceHelper.getOperatorNamespace();

    testSupport.runSteps(createEventStep(new EventData(DOMAIN_PROCESSING_STARTING)));

    assertThat("Operator namespace is correct",
        namespaceFromHelm, equalTo(OP_NS));

    assertThat("Found DOMAIN_PROCESSING_STARTING event with expected reporting instance",
        containsEventWithInstance(getEvents(testSupport),
            DOMAIN_PROCESSING_STARTING_EVENT, OPERATOR_POD_NAME), is(true));
  }

  @Test
  public void whenCreateEventStepCalledForStartingAndCompleted_domainProcessingCompletedEventCreated() {
    testSupport.runSteps(Step.chain(
        createEventStep(new EventData(DOMAIN_PROCESSING_STARTING)),
        createEventStep(new EventData(DOMAIN_PROCESSING_COMPLETED))));

    assertThat("Found DOMAIN_PROCESSING_COMPLETED event",
        containsEvent(getEvents(testSupport), DOMAIN_PROCESSING_COMPLETED_EVENT), is(true));
  }

  @Test
  public void whenCreateEventStepCalled4StartingCompleted_domainProcessingCompletedEventCreatedWithExpectedMessage() {
    testSupport.runSteps(Step.chain(
        createEventStep(new EventData(DOMAIN_PROCESSING_STARTING)),
        createEventStep(new EventData(DOMAIN_PROCESSING_COMPLETED)))
    );

    assertThat("Found DOMAIN_PROCESSING_COMPLETED event with expected message",
        containsEventWithMessage(getEvents(testSupport),
            DOMAIN_PROCESSING_COMPLETED_EVENT,
            String.format(DOMAIN_PROCESSING_COMPLETED_PATTERN, UID)), is(true));
  }

  @Test
  public void whenCreateEventStepCalled_domainProcessingStartingEventCreatedWithExpectedCount() {
    testSupport.runSteps(Step.chain(createEventStep(new EventData(DOMAIN_PROCESSING_STARTING))));

    assertThat("Found DOMAIN_PROCESSING_STARTING event with expected count",
        containsOneEventWithCount(getEvents(testSupport), DOMAIN_PROCESSING_STARTING_EVENT, 1), is(true));
  }

  @Test
  public void whenCreateEventCalledTwice_domainProcessingStartingEventCreatedOnceWithExpectedCount() {
    testSupport.runSteps(Step.chain(
        createEventStep(new EventData(DOMAIN_PROCESSING_STARTING)),
        createEventStep(new EventData(DOMAIN_PROCESSING_COMPLETED))));
    dispatchAddedEventWatches();

    testSupport.runSteps(Step.chain(
        createEventStep(new EventData(DOMAIN_PROCESSING_STARTING)))
    );

    assertThat("Found DOMAIN_PROCESSING_STARTING event with expected count",
        containsOneEventWithCount(getEvents(testSupport), DOMAIN_PROCESSING_STARTING_EVENT, 2), is(true));
  }

  @Test
  public void whenCreateEventTwice_fail404OnReplaceEvent_domainProcessingStartingEventCreatedTwice() {
    testSupport.runSteps(Step.chain(
        createEventStep(new EventData(DOMAIN_PROCESSING_STARTING)),
        createEventStep(new EventData(DOMAIN_PROCESSING_COMPLETED))));

    dispatchAddedEventWatches();
    CoreV1Event event = EventTestUtils.getEventWithReason(getEvents(testSupport), DOMAIN_PROCESSING_STARTING_EVENT);
    testSupport.failOnReplace(KubernetesTestSupport.EVENT, EventTestUtils.getName(event), NS, 404);

    testSupport.runSteps(Step.chain(createEventStep(new EventData(DOMAIN_PROCESSING_STARTING))));

    assertThat("Found 2 DOMAIN_PROCESSING_STARTING events",
        EventTestUtils.getNumberOfEvents(getEvents(testSupport),
            DOMAIN_PROCESSING_STARTING_EVENT), equalTo(2));
  }

  @Test
  public void whenCreateEventTwice_fail410OnReplaceEvent_domainProcessingStartingEventCreatedTwice() {
    testSupport.runSteps(Step.chain(
        createEventStep(new EventData(DOMAIN_PROCESSING_STARTING)),
        createEventStep(new EventData(DOMAIN_PROCESSING_COMPLETED))));

    CoreV1Event event = EventTestUtils.getEventWithReason(getEvents(testSupport), DOMAIN_PROCESSING_STARTING_EVENT);
    dispatchAddedEventWatches();
    testSupport.failOnReplace(KubernetesTestSupport.EVENT, EventTestUtils.getName(event), NS, 410);

    testSupport.runSteps(Step.chain(createEventStep(new EventData(DOMAIN_PROCESSING_STARTING))));

    assertThat("Found 2 DOMAIN_PROCESSING_STARTING events",
        EventTestUtils.getNumberOfEvents(getEvents(testSupport),
            DOMAIN_PROCESSING_STARTING_EVENT), equalTo(2));
  }

  @Test
  public void whenCreateEventTwice_fail403OnReplaceEvent_domainProcessingStartingEventCreatedOnce() {
    testSupport.runSteps(Step.chain(
        createEventStep(new EventData(DOMAIN_PROCESSING_STARTING)),
        createEventStep(new EventData(DOMAIN_PROCESSING_COMPLETED))));

    CoreV1Event event = EventTestUtils.getEventWithReason(getEvents(testSupport), DOMAIN_PROCESSING_STARTING_EVENT);
    dispatchAddedEventWatches();
    testSupport.failOnReplace(KubernetesTestSupport.EVENT, EventTestUtils.getName(event), NS, 403);

    testSupport.runSteps(Step.chain(createEventStep(new EventData(DOMAIN_PROCESSING_STARTING))));

    assertThat("Found 1 DOMAIN_PROCESSING_STARTING event",
        EventTestUtils.getNumberOfEvents(getEvents(testSupport),
            DOMAIN_PROCESSING_STARTING_EVENT), equalTo(1));
  }

  @Test
  public void whenCreateEventStepCalledWithOutStartingEvent_domainProcessingCompletedEventNotCreated() {
    testSupport.runSteps(createEventStep(new EventData(DOMAIN_PROCESSING_COMPLETED)));

    assertThat("Found DOMAIN_PROCESSING_COMPLETED event",
        containsEvent(getEvents(testSupport), DOMAIN_PROCESSING_COMPLETED_EVENT), is(false));
  }

  @Test
  public void whenCreateEventStepCalledWithRetryingAndEvent_domainProcessingCompletedEventCreated() {
    testSupport.runSteps(Step.chain(
        createEventStep(new EventData(DOMAIN_PROCESSING_RETRYING)),
        createEventStep(new EventData(DOMAIN_PROCESSING_STARTING)),
        createEventStep(new EventData(DOMAIN_PROCESSING_COMPLETED)))
    );

    assertThat("Found DOMAIN_PROCESSING_COMPLETED event",
        containsEvent(getEvents(testSupport), DOMAIN_PROCESSING_COMPLETED_EVENT), is(true));
  }

  @Test
  public void whenCreateEventCalledTwice_domainProcessingCompletedEventCreatedOnceWithExpectedCount() {
    testSupport.runSteps(Step.chain(
        createEventStep(new EventData(DOMAIN_PROCESSING_STARTING)),
        createEventStep(new EventData(DOMAIN_PROCESSING_COMPLETED))));

    dispatchAddedEventWatches();

    testSupport.runSteps(Step.chain(
        createEventStep(new EventData(DOMAIN_PROCESSING_STARTING)),
        createEventStep(new EventData(DOMAIN_PROCESSING_COMPLETED))));

    assertThat("Found DOMAIN_PROCESSING_COMPLETED event with expected count",
        containsOneEventWithCount(getEvents(testSupport), DOMAIN_PROCESSING_COMPLETED_EVENT, 2), is(true));
  }

  @Test
  public void whenCreateEventCalledTwice_thenDeleteEvent_domainProcessingStartingEventCreatedTwice() {
    testSupport.runSteps(Step.chain(
        createEventStep(new EventData(DOMAIN_CREATED))));

    dispatchAddedEventWatches();
    dispatchDeletedEventWatches();

    testSupport.runSteps(Step.chain(
        createEventStep(new EventData(DOMAIN_CREATED))));

    assertThat("Found 2 DOMAIN_CREATED events with expected count 1",
        containsEventsWithCountOne(getEvents(testSupport),
            DOMAIN_CREATED_EVENT, 2), is(true));
  }

  @Test
  public void whenCreateEventCalledTwice_thenDeleteCompletedEvent_domainProcessingCompletedEventCreatedTwice() {
    testSupport.runSteps(Step.chain(
        createEventStep(new EventData(DOMAIN_PROCESSING_STARTING)),
        createEventStep(new EventData(DOMAIN_PROCESSING_COMPLETED))));

    dispatchAddedEventWatches();
    dispatchDeletedEventWatches();

    testSupport.runSteps(Step.chain(
        createEventStep(new EventData(DOMAIN_PROCESSING_STARTING)),
        createEventStep(new EventData(DOMAIN_PROCESSING_COMPLETED))));

    assertThat("Found 2 DOMAIN_PROCESSING_COMPLETED events with expected count 1",
        containsEventsWithCountOne(getEvents(testSupport),
            DOMAIN_PROCESSING_COMPLETED_EVENT, 2), is(true));
  }

  @Test
  public void whenCreateEventStepCalledWithFailedEvent_domainProcessingFailedEventCreated() {
    testSupport.runSteps(createFailureRelatedSteps("FAILED", "Test failure", new TerminalStep()));

    assertThat("Found DOMAIN_PROCESSING_FAILED event",
        containsEvent(getEvents(testSupport), DOMAIN_PROCESSING_FAILED_EVENT), is(true));
  }

  @Test
  public void whenCreateEventStepCalledWithFailedEvent_domainProcessingFailedEventCreatedWithExpectedMessage() {
    testSupport.runSteps(createFailureRelatedSteps("FAILED", "Test this failure", new TerminalStep()));

    assertThat("Found DOMAIN_PROCESSING_FAILED event with expected message",
        containsEventWithMessage(getEvents(testSupport),
            DOMAIN_PROCESSING_FAILED_EVENT,
            String.format(DOMAIN_PROCESSING_FAILED_PATTERN, UID, "Test this failure")), is(true));
  }

  @Test
  public void whenCreateEventStepCalledWithFailedEventTwice_domainProcessingFailedEventCreatedOnceWithExpectedCount() {
    testSupport.runSteps(createFailureRelatedSteps("FAILED", "Test failure", new TerminalStep()));
    dispatchAddedEventWatches();
    testSupport.runSteps(createFailureRelatedSteps("FAILED", "Test failure", new TerminalStep()));

    assertThat("Found DOMAIN_PROCESSING_FAILED event",
        containsOneEventWithCount(getEvents(testSupport), DOMAIN_PROCESSING_FAILED_EVENT, 2), is(true));
  }

  @Test
  public void whenMakeRightCalled_withRetryingEventData_domainProcessingRetryingEventCreated() {
    makeRightOperation.withEventData(DOMAIN_PROCESSING_RETRYING, null).execute();

    assertThat("Found DOMAIN_PROCESSING_RETRYING event",
        containsEvent(getEvents(testSupport), EventConstants.DOMAIN_PROCESSING_RETRYING_EVENT), is(true));
  }

  @Test
  public void whenMakeRightCalled_withRetryingEventData_domainProcessingRetryingEventCreatedWithExpectedMessage() {
    makeRightOperation.withEventData(DOMAIN_PROCESSING_RETRYING, null).execute();

    assertThat("Found DOMAIN_PROCESSING_RETRYING event with expected message",
        containsEventWithMessage(getEvents(testSupport),
            EventConstants.DOMAIN_PROCESSING_RETRYING_EVENT,
            String.format(DOMAIN_PROCESSING_RETRYING_PATTERN, UID)), is(true));
  }

  @Test
  public void whenMakeRightCalled_withCreatedEventData_domainCreatedEventCreated() {
    makeRightOperation.withEventData(DOMAIN_CREATED, null).execute();

    assertThat("Found DOMAIN_CREATED event",
        containsEvent(getEvents(testSupport), DOMAIN_CREATED_EVENT), is(true));
  }

  @Test
  public void whenMakeRightCalled_withCreatedEventData_domainCreatedEventCreatedWithExpectedMessage() {
    makeRightOperation.withEventData(DOMAIN_CREATED, null).execute();

    assertThat("Found DOMAIN_CREATED event with expected message",
        containsEventWithMessage(getEvents(testSupport),
            DOMAIN_CREATED_EVENT,
            String.format(DOMAIN_CREATED_PATTERN, UID)), is(true));
  }

  @Test
  public void whenMakeRightCalled_withChangedEventData_domainChangedEventCreated() {
    makeRightOperation.withEventData(DOMAIN_CHANGED, null).execute();

    assertThat("Found DOMAIN_CHANGED event",
        containsEvent(getEvents(testSupport), EventConstants.DOMAIN_CHANGED_EVENT), is(true));
  }

  @Test
  public void whenMakeRightCalled_withChangedEventData_domainChangedEventCreatedWithExpectedMessage() {
    makeRightOperation.withEventData(DOMAIN_CHANGED, null).execute();

    assertThat("Found DOMAIN_CHANGED event with expected message",
        containsEventWithMessage(getEvents(testSupport),
            EventConstants.DOMAIN_CHANGED_EVENT,
            String.format(DOMAIN_CHANGED_PATTERN, UID)), is(true));
  }

  @Test
  public void whenDomainChangedEventCreateCalledTwice_domainChangedEventCreatedOnceWithExpectedCount() {
    presenceInfoMap.put(NS, Map.of(UID, info));
    testSupport.runSteps(Step.chain(createEventStep(new EventData(DOMAIN_CHANGED))));
    dispatchAddedEventWatches();

    testSupport.runSteps(Step.chain(createEventStep(new EventData(DOMAIN_CHANGED))));

    presenceInfoMap.remove(NS);
    assertThat("Found DOMAIN_CHANGED event with expected count",
        containsOneEventWithCount(getEvents(testSupport), EventConstants.DOMAIN_CHANGED_EVENT, 2), is(true));
  }

  @Test
  public void whenMakeRightCalled_withDeletedEventData_domainDeletedEventCreated() {
    makeRightOperation.withEventData(DOMAIN_DELETED, null).execute();

    assertThat("Found DOMAIN_DELETED event",
        containsEvent(getEvents(testSupport), EventConstants.DOMAIN_DELETED_EVENT), is(true));
  }

  @Test
  public void whenMakeRightCalled_withDeletedEventData_domainDeletedEventCreatedWithExpectedMessage() {
    makeRightOperation.withEventData(DOMAIN_DELETED, null).execute();

    assertThat("Found DOMAIN_DELETED event with expected message",
        containsEventWithMessage(getEvents(testSupport),
            EventConstants.DOMAIN_DELETED_EVENT,
            String.format(DOMAIN_DELETED_PATTERN, UID)), is(true));
  }

  @Test
  public void whenCreateEventStepCalledWithAbortedEvent_domainProcessingAbortedEventCreated() {
    testSupport.runSteps(Step.chain(
        createEventStep(new EventData(DOMAIN_PROCESSING_FAILED)),
        createEventStep(new EventData(DOMAIN_PROCESSING_ABORTED).message("Test this failure")))
    );

    assertThat("Found DOMAIN_PROCESSING_ABORTED event",
        containsEvent(getEvents(testSupport), EventConstants.DOMAIN_PROCESSING_ABORTED_EVENT), is(true));
  }

  @Test
  public void whenCreateEventStepCalledWithAbortedEvent_domainProcessingAbortedEventCreatedWithExpectedMessage() {
    testSupport.runSteps(Step.chain(
        createEventStep(new EventData(DOMAIN_PROCESSING_FAILED)),
        createEventStep(new EventData(DOMAIN_PROCESSING_ABORTED).message("Test this failure")))
    );

    assertThat("Found DOMAIN_PROCESSING_ABORTED event with expected message",
        containsEventWithMessage(getEvents(testSupport),
            EventConstants.DOMAIN_PROCESSING_ABORTED_EVENT,
            String.format(DOMAIN_PROCESSING_ABORTED_PATTERN, UID, "Test this failure")), is(true));
  }

  @Test
  public void whenCreateEventStepCalledForStartManagingNamespace_eventCreatedWithExpectedMessage() {
    testSupport.runSteps(createEventStep(new EventData(START_MANAGING_NAMESPACE).namespace(OP_NS).resourceName(NS)));
    assertThat("Found NAMESPACE_WATCHING_STARTED event with expected message",
        containsEventWithMessage(getEvents(testSupport),
            EventConstants.START_MANAGING_NAMESPACE_EVENT,
            String.format(EventConstants.START_MANAGING_NAMESPACE_PATTERN, NS)), is(true));
  }

  @Test
  public void whenCreateEventStepCalledWithNSWatchStartedEvent_eventCreatedWithExpectedNamespace() {
    testSupport.runSteps(createEventStep(new EventData(NAMESPACE_WATCHING_STARTED).namespace(NS).resourceName(NS)));
    assertThat("Found NAMESPACE_WATCHING_STARTED event with expected namespace",
        containsEventWithNamespace(getEvents(testSupport),
            EventConstants.NAMESPACE_WATCHING_STARTED_EVENT, NS), is(true));
  }

  @Test
  public void whenCreateEventStepCalledWithNSWatchStartedEvent_eventCreatedWithExpectedLabels() {
    testSupport.runSteps(createEventStep(new EventData(NAMESPACE_WATCHING_STARTED).namespace(NS).resourceName(NS)));

    Map<String, String> expectedLabels = new HashMap<>();
    expectedLabels.put(LabelConstants.CREATEDBYOPERATOR_LABEL, "true");
    assertThat("Found NAMESPACE_WATCHING_STARTED event with expected labels",
        containsEventWithLabels(getEvents(testSupport),
            EventConstants.NAMESPACE_WATCHING_STARTED_EVENT, expectedLabels), is(true));
  }

  @Test
  public void whenNSWatchStartedEventCreatedTwice_eventCreatedOnceWithExpectedCount() {
    testSupport.runSteps(createEventStep(new EventData(NAMESPACE_WATCHING_STARTED).namespace(NS).resourceName(NS)));
    dispatchAddedEventWatches();
    testSupport.runSteps(createEventStep(new EventData(NAMESPACE_WATCHING_STARTED).namespace(NS).resourceName(NS)));

    assertThat("Found 1 NAMESPACE_WATCHING_STARTED event with expected count",
        containsOneEventWithCount(getEvents(testSupport),
            EventConstants.NAMESPACE_WATCHING_STARTED_EVENT, 2), is(true));
  }

  @Test
  public void whenNSWatchStartedEventCreated_thenDelete_eventCreatedTwice() {
    testSupport.runSteps(createEventStep(new EventData(NAMESPACE_WATCHING_STARTED).namespace(NS).resourceName(NS)));
    dispatchAddedEventWatches();
    dispatchDeletedEventWatches();
    testSupport.runSteps(createEventStep(new EventData(NAMESPACE_WATCHING_STARTED).namespace(NS).resourceName(NS)));

    assertThat("Found 2 NAMESPACE_WATCHING_STARTED events",
        containsEventsWithCountOne(getEvents(testSupport),
            EventConstants.NAMESPACE_WATCHING_STARTED_EVENT, 2), is(true));
  }

  @Test
  public void whenCreateEventStepCalledWithNSWatchStoppedEvent_eventCreatedWithExpectedLabels() {
    testSupport.runSteps(createEventStep(new EventData(NAMESPACE_WATCHING_STOPPED).namespace(NS).resourceName(NS)));

    Map<String, String> expectedLabels = new HashMap<>();
    expectedLabels.put(LabelConstants.CREATEDBYOPERATOR_LABEL, "true");
    assertThat("Found NAMESPACE_WATCHING_STOPPED event with expected labels",
        containsEventWithLabels(getEvents(testSupport),
            NAMESPACE_WATCHING_STOPPED_EVENT, expectedLabels), is(true));
  }

  @Test
  public void whenNSWatchStoppedEventCreated_eventCreatedWithExpectedInvolvedObject() {
    testSupport.runSteps(createEventStep(new EventData(NAMESPACE_WATCHING_STOPPED).namespace(NS).resourceName(NS)));

    assertThat("Found NAMESPACE_WATCHING_STOPPED event with expected involvedObject",
        containsEventWithInvolvedObject(getEvents(testSupport),
            NAMESPACE_WATCHING_STOPPED_EVENT, NS, NS),
        is(true));
  }

  @Test
  public void whenNSWatchStoppedEventCreated_fail404OnReplace_eventCreatedWithExpectedCount() {
    testSupport.runSteps(createEventStep(new EventData(NAMESPACE_WATCHING_STOPPED).namespace(NS).resourceName(NS)));
    dispatchAddedEventWatches();

    CoreV1Event event = EventTestUtils.getEventWithReason(getEvents(testSupport), NAMESPACE_WATCHING_STOPPED_EVENT);
    testSupport.failOnReplace(KubernetesTestSupport.EVENT, EventTestUtils.getName(event), NS, 404);

    testSupport.runSteps(createEventStep(
        new EventData(NAMESPACE_WATCHING_STOPPED).namespace(NS).resourceName(NS)));

    assertThat("Found 2 NAMESPACE_WATCHING_STOPPED events",
        getNumberOfEvents(getEvents(testSupport),
            NAMESPACE_WATCHING_STOPPED_EVENT), equalTo(2));
  }

  @Test
  public void whenNSWatchStoppedEventCreatedTwice_fail403OnReplace_eventCreatedOnce() {
    Step eventStep = createEventStep(new EventData(NAMESPACE_WATCHING_STOPPED).namespace(NS).resourceName(NS));

    testSupport.runSteps(eventStep);
    dispatchAddedEventWatches();

    CoreV1Event event = EventTestUtils.getEventWithReason(getEvents(testSupport), NAMESPACE_WATCHING_STOPPED_EVENT);
    testSupport.failOnReplace(KubernetesTestSupport.EVENT, EventTestUtils.getName(event), NS, 403);

    testSupport.runSteps(eventStep);

    assertThat("Found 1 NAMESPACE_WATCHING_STOPPED events",
        getNumberOfEvents(getEvents(testSupport),
            NAMESPACE_WATCHING_STOPPED_EVENT), equalTo(1));
  }

  @Test
  public void whenCreateEventStepCalledForNSWatchStartedEvent_eventCreatedWithExpectedMessage() {
    testSupport.runSteps(createEventStep(new EventData(NAMESPACE_WATCHING_STARTED).namespace(NS).resourceName(NS)));
    assertThat("Found START_MANAGING_NAMESPACE event with expected message",
        containsEventWithMessage(getEvents(testSupport),
            EventConstants.NAMESPACE_WATCHING_STARTED_EVENT,
            String.format(EventConstants.NAMESPACE_WATCHING_STARTED_PATTERN, NS)), is(true));
  }

  @Test
  public void whenCreateEventStepCalledForStartManagingNS_eventCreatedWithExpectedNamespace() {
    testSupport.runSteps(createEventStep(new EventData(START_MANAGING_NAMESPACE).namespace(OP_NS).resourceName(NS)));
    assertThat("Found START_MANAGING_NAMESPACE event with expected namespace",
        containsEventWithNamespace(getEvents(testSupport),
            EventConstants.START_MANAGING_NAMESPACE_EVENT, OP_NS), is(true));
  }

  @Test
  public void whenCreateEventStepCalledForStartManagingNS_eventCreatedWithExpectedLabels() {
    testSupport.runSteps(createEventStep(new EventData(START_MANAGING_NAMESPACE).namespace(OP_NS).resourceName(NS)));

    Map<String, String> expectedLabels = new HashMap<>();
    expectedLabels.put(LabelConstants.CREATEDBYOPERATOR_LABEL, "true");
    assertThat("Found START_MANAGING_NAMESPACE event with expected labels",
        containsEventWithLabels(getEvents(testSupport),
            EventConstants.START_MANAGING_NAMESPACE_EVENT, expectedLabels), is(true));
  }

  @Test
  public void whenStartManagingNSEventCreatedTwice_eventCreatedOnceWithExpectedCount() {
    Step step = createEventStep(new EventData(START_MANAGING_NAMESPACE).namespace(OP_NS).resourceName(NS));
    testSupport.runSteps(step);
    dispatchAddedEventWatches();
    testSupport.runSteps(step);

    assertThat("Found 1 START_MANAGING_NAMESPACE event with expected count",
        containsOneEventWithCount(getEvents(testSupport),
            EventConstants.START_MANAGING_NAMESPACE_EVENT, 2), is(true));
  }

  @Test
  public void whenStartManagingNSEventCreated_thenDelete_eventCreatedTwice() {
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
  public void whenCreateEventStepCalledForStopManagingNS_eventCreatedWithExpectedLabels() {
    testSupport.runSteps(createEventStep(new EventData(STOP_MANAGING_NAMESPACE).namespace(OP_NS).resourceName(NS)));

    Map<String, String> expectedLabels = new HashMap<>();
    expectedLabels.put(LabelConstants.CREATEDBYOPERATOR_LABEL, "true");
    assertThat("Found STOP_MANAGING_NAMESPACE event with expected labels",
        containsEventWithLabels(getEvents(testSupport),
            STOP_MANAGING_NAMESPACE_EVENT, expectedLabels), is(true));
  }

  @Test
  public void whenCreateEventStepCalledForStopManagingNS_eventCreatedWithExpectedInvolvedObject() {
    testSupport.runSteps(createEventStep(new EventData(STOP_MANAGING_NAMESPACE).namespace(OP_NS).resourceName(NS)));

    assertThat("Found STOP_MANAGING_NAMESPACE event with expected involvedObject",
        containsEventWithInvolvedObject(getEvents(testSupport),
            STOP_MANAGING_NAMESPACE_EVENT, OPERATOR_POD_NAME, OP_NS),
        is(true));
  }

  @Test
  public void whenStopManagingNSEventCreated_fail404OnReplace_eventCreatedWithExpectedCount() {
    Step step = createEventStep(new EventData(STOP_MANAGING_NAMESPACE).namespace(OP_NS).resourceName(NS));
    testSupport.runSteps(step);
    dispatchAddedEventWatches();

    CoreV1Event event = EventTestUtils.getEventWithReason(getEvents(testSupport), STOP_MANAGING_NAMESPACE_EVENT);
    testSupport.failOnReplace(KubernetesTestSupport.EVENT, EventTestUtils.getName(event), OP_NS, 404);

    testSupport.runSteps(step);

    assertThat("Found 2 STOP_MANAGING_NAMESPACE events",
        getNumberOfEvents(getEvents(testSupport), STOP_MANAGING_NAMESPACE_EVENT), equalTo(2));
  }

  @Test
  public void whenStopManagingNSEventCreatedTwice_fail403OnReplace_eventCreatedOnce() {
    Step eventStep = createEventStep(new EventData(STOP_MANAGING_NAMESPACE).namespace(OP_NS).resourceName(NS));

    testSupport.runSteps(eventStep);
    dispatchAddedEventWatches();

    CoreV1Event event = EventTestUtils.getEventWithReason(getEvents(testSupport), STOP_MANAGING_NAMESPACE_EVENT);
    testSupport.failOnReplace(KubernetesTestSupport.EVENT, EventTestUtils.getName(event), NS, 403);

    testSupport.runSteps(eventStep);

    assertThat("Found 1 STOP_MANAGING_NAMESPACE events",
        getNumberOfEvents(getEvents(testSupport), STOP_MANAGING_NAMESPACE_EVENT), equalTo(1));
  }

  @Test
  public void whenNSWatchStoppedEventCreated_fail403OnCreate_foundExpectedLogMessage() {
    loggerControl.withLogLevel(Level.INFO).collectLogMessages(logRecords, CREATING_EVENT_FORBIDDEN);
    testSupport.failOnCreate(KubernetesTestSupport.EVENT, null, NS, 403);

    testSupport.runSteps(createEventStep(new EventData(NAMESPACE_WATCHING_STOPPED).namespace(NS).resourceName(NS)));

    assertThat(logRecords, containsInfo(CREATING_EVENT_FORBIDDEN, NAMESPACE_WATCHING_STOPPED_EVENT, NS));
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
