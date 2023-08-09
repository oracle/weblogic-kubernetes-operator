// Copyright (c) 2020, 2023, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.helpers;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.LogRecord;

import com.meterware.simplestub.Memento;
import com.meterware.simplestub.StaticStubSupport;
import io.kubernetes.client.openapi.models.CoreV1Event;
import io.kubernetes.client.openapi.models.V1ObjectMeta;
import io.kubernetes.client.openapi.models.V1ObjectReference;
import io.kubernetes.client.openapi.models.V1Pod;
import io.kubernetes.client.util.Watch;
import oracle.kubernetes.operator.DomainNamespaces;
import oracle.kubernetes.operator.DomainProcessorDelegateStub;
import oracle.kubernetes.operator.DomainProcessorImpl;
import oracle.kubernetes.operator.DomainProcessorTestSetup;
import oracle.kubernetes.operator.EventConstants;
import oracle.kubernetes.operator.EventTestUtils;
import oracle.kubernetes.operator.LabelConstants;
import oracle.kubernetes.operator.MakeRightDomainOperation;
import oracle.kubernetes.operator.builders.WatchEvent;
import oracle.kubernetes.operator.helpers.EventHelper.EventData;
import oracle.kubernetes.operator.tuning.TuningParametersStub;
import oracle.kubernetes.operator.work.Step;
import oracle.kubernetes.utils.TestUtils;
import oracle.kubernetes.weblogic.domain.model.DomainFailureReason;
import oracle.kubernetes.weblogic.domain.model.DomainResource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static com.meterware.simplestub.Stub.createStrictStub;
import static java.net.HttpURLConnection.HTTP_CONFLICT;
import static java.net.HttpURLConnection.HTTP_FORBIDDEN;
import static java.net.HttpURLConnection.HTTP_NOT_FOUND;
import static java.net.HttpURLConnection.HTTP_UNAVAILABLE;
import static oracle.kubernetes.common.logging.MessageKeys.ABORTED_ERROR_EVENT_SUGGESTION;
import static oracle.kubernetes.common.logging.MessageKeys.ABORTED_EVENT_ERROR;
import static oracle.kubernetes.common.logging.MessageKeys.CLUSTER_DELETED_EVENT_PATTERN;
import static oracle.kubernetes.common.logging.MessageKeys.CREATING_EVENT_FORBIDDEN;
import static oracle.kubernetes.common.logging.MessageKeys.DOMAIN_AVAILABLE_EVENT_PATTERN;
import static oracle.kubernetes.common.logging.MessageKeys.DOMAIN_CHANGED_EVENT_PATTERN;
import static oracle.kubernetes.common.logging.MessageKeys.DOMAIN_COMPLETED_EVENT_PATTERN;
import static oracle.kubernetes.common.logging.MessageKeys.DOMAIN_CREATED_EVENT_PATTERN;
import static oracle.kubernetes.common.logging.MessageKeys.DOMAIN_DELETED_EVENT_PATTERN;
import static oracle.kubernetes.common.logging.MessageKeys.DOMAIN_FAILED_EVENT_PATTERN;
import static oracle.kubernetes.common.logging.MessageKeys.DOMAIN_FAILURE_RESOLVED_EVENT_PATTERN;
import static oracle.kubernetes.common.logging.MessageKeys.DOMAIN_INCOMPLETE_EVENT_PATTERN;
import static oracle.kubernetes.common.logging.MessageKeys.DOMAIN_INVALID_ERROR_EVENT_SUGGESTION;
import static oracle.kubernetes.common.logging.MessageKeys.DOMAIN_INVALID_EVENT_ERROR;
import static oracle.kubernetes.common.logging.MessageKeys.DOMAIN_ROLL_COMPLETED_EVENT_PATTERN;
import static oracle.kubernetes.common.logging.MessageKeys.DOMAIN_ROLL_STARTING_EVENT_PATTERN;
import static oracle.kubernetes.common.logging.MessageKeys.DOMAIN_UNAVAILABLE_EVENT_PATTERN;
import static oracle.kubernetes.common.logging.MessageKeys.INTERNAL_EVENT_ERROR;
import static oracle.kubernetes.common.logging.MessageKeys.INTROSPECTION_EVENT_ERROR;
import static oracle.kubernetes.common.logging.MessageKeys.NAMESPACE_WATCHING_STARTED_EVENT_PATTERN;
import static oracle.kubernetes.common.logging.MessageKeys.NAMESPACE_WATCHING_STOPPED_EVENT_PATTERN;
import static oracle.kubernetes.common.logging.MessageKeys.POD_CYCLE_STARTING_EVENT_PATTERN;
import static oracle.kubernetes.common.logging.MessageKeys.REPLICAS_TOO_HIGH_ERROR_EVENT_SUGGESTION;
import static oracle.kubernetes.common.logging.MessageKeys.REPLICAS_TOO_HIGH_EVENT_ERROR;
import static oracle.kubernetes.common.logging.MessageKeys.START_MANAGING_NAMESPACE_EVENT_PATTERN;
import static oracle.kubernetes.common.logging.MessageKeys.START_MANAGING_NAMESPACE_FAILED_EVENT_PATTERN;
import static oracle.kubernetes.common.logging.MessageKeys.STOP_MANAGING_NAMESPACE_EVENT_PATTERN;
import static oracle.kubernetes.common.logging.MessageKeys.TOPOLOGY_MISMATCH_ERROR_EVENT_SUGGESTION;
import static oracle.kubernetes.common.logging.MessageKeys.TOPOLOGY_MISMATCH_EVENT_ERROR;
import static oracle.kubernetes.common.logging.MessageKeys.WILL_RETRY_EVENT_SUGGESTION;
import static oracle.kubernetes.common.utils.LogMatcher.containsInfo;
import static oracle.kubernetes.common.utils.LogMatcher.containsWarning;
import static oracle.kubernetes.operator.DomainProcessorTestSetup.CLUSTER_1_NAME;
import static oracle.kubernetes.operator.DomainProcessorTestSetup.NS;
import static oracle.kubernetes.operator.DomainProcessorTestSetup.UID;
import static oracle.kubernetes.operator.DomainProcessorTestSetup.cluster1;
import static oracle.kubernetes.operator.DomainStatusUpdater.createDomainInvalidFailureSteps;
import static oracle.kubernetes.operator.DomainStatusUpdater.createTopologyMismatchFailureSteps;
import static oracle.kubernetes.operator.EventConstants.CLUSTER_AVAILABLE_EVENT;
import static oracle.kubernetes.operator.EventConstants.CLUSTER_DELETED_EVENT;
import static oracle.kubernetes.operator.EventConstants.DOMAIN_AVAILABLE_EVENT;
import static oracle.kubernetes.operator.EventConstants.DOMAIN_COMPLETED_EVENT;
import static oracle.kubernetes.operator.EventConstants.DOMAIN_CREATED_EVENT;
import static oracle.kubernetes.operator.EventConstants.DOMAIN_FAILED_EVENT;
import static oracle.kubernetes.operator.EventConstants.DOMAIN_FAILURE_RESOLVED_EVENT;
import static oracle.kubernetes.operator.EventConstants.DOMAIN_INCOMPLETE_EVENT;
import static oracle.kubernetes.operator.EventConstants.DOMAIN_ROLL_COMPLETED_EVENT;
import static oracle.kubernetes.operator.EventConstants.DOMAIN_ROLL_STARTING_EVENT;
import static oracle.kubernetes.operator.EventConstants.DOMAIN_UNAVAILABLE_EVENT;
import static oracle.kubernetes.operator.EventConstants.NAMESPACE_WATCHING_STARTED_EVENT;
import static oracle.kubernetes.operator.EventConstants.NAMESPACE_WATCHING_STOPPED_EVENT;
import static oracle.kubernetes.operator.EventConstants.POD_CYCLE_STARTING_EVENT;
import static oracle.kubernetes.operator.EventConstants.START_MANAGING_NAMESPACE_FAILED_EVENT;
import static oracle.kubernetes.operator.EventConstants.STOP_MANAGING_NAMESPACE_EVENT;
import static oracle.kubernetes.operator.EventMatcher.hasEvent;
import static oracle.kubernetes.operator.EventTestUtils.containsEvent;
import static oracle.kubernetes.operator.EventTestUtils.containsEventWithInvolvedObject;
import static oracle.kubernetes.operator.EventTestUtils.containsEventWithLabels;
import static oracle.kubernetes.operator.EventTestUtils.containsEventWithMessage;
import static oracle.kubernetes.operator.EventTestUtils.containsEventWithNamespace;
import static oracle.kubernetes.operator.EventTestUtils.containsEventsWithCountOne;
import static oracle.kubernetes.operator.EventTestUtils.containsOneEventWithCount;
import static oracle.kubernetes.operator.EventTestUtils.getEvents;
import static oracle.kubernetes.operator.EventTestUtils.getFormattedMessage;
import static oracle.kubernetes.operator.EventTestUtils.getLocalizedString;
import static oracle.kubernetes.operator.EventTestUtils.getNumberOfEvents;
import static oracle.kubernetes.operator.KubernetesConstants.OPERATOR_NAMESPACE_ENV;
import static oracle.kubernetes.operator.KubernetesConstants.OPERATOR_POD_NAME_ENV;
import static oracle.kubernetes.operator.NamespaceTest.createDomainNamespaces;
import static oracle.kubernetes.operator.ProcessingConstants.JOB_POD;
import static oracle.kubernetes.operator.helpers.EventHelper.EventItem.CLUSTER_AVAILABLE;
import static oracle.kubernetes.operator.helpers.EventHelper.EventItem.DOMAIN_AVAILABLE;
import static oracle.kubernetes.operator.helpers.EventHelper.EventItem.DOMAIN_CHANGED;
import static oracle.kubernetes.operator.helpers.EventHelper.EventItem.DOMAIN_COMPLETE;
import static oracle.kubernetes.operator.helpers.EventHelper.EventItem.DOMAIN_CREATED;
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
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;

class EventHelperTest {
  private static final String OPERATOR_POD_NAME = "my-weblogic-operator-1234";
  private static final String OP_NS = "operator-namespace";

  private final List<Memento> mementos = new ArrayList<>();
  private final KubernetesTestSupport testSupport = new KubernetesTestSupport();
  private final DomainNamespaces domainNamespaces = createDomainNamespaces();
  private final DomainProcessorDelegateStub processorDelegate =
      DomainProcessorDelegateStub.createDelegate(testSupport, domainNamespaces);
  private final DomainProcessorImpl processor = new DomainProcessorImpl(processorDelegate);
  private final DomainResource domain = DomainProcessorTestSetup.createTestDomain();
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
  private static final String WILL_NOT_RETRY =
      "The reported problem should be corrected, and the domain will not be retried "
          + "until the domain resource is updated.";

  @BeforeEach
  void setUp() throws Exception {
    mementos.add(loggerControl = TestUtils.silenceOperatorLogger());
    mementos.add(testSupport.install());
    mementos.add(StaticStubSupport.install(DomainProcessorImpl.class, "domains", presenceInfoMap));
    mementos.add(StaticStubSupport.install(DomainProcessorImpl.class, "domainEventK8SObjects", domainEventObjects));
    mementos.add(StaticStubSupport.install(DomainProcessorImpl.class, "namespaceEventK8SObjects", nsEventObjects));
    mementos.add(TuningParametersStub.install());
    mementos.add(HelmAccessStub.install());

    testSupport.addToPacket(JOB_POD, new V1Pod().metadata(new V1ObjectMeta().name(jobPodName)));
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
    testSupport.runSteps(createTopologyMismatchFailureSteps("Test failure", null));

    assertThat("Found DOMAIN_FAILED event",
        containsEvent(getEvents(testSupport), DOMAIN_FAILED_EVENT), is(true));
  }

  @Test
  void whenCreateEventStepCalledWithFailedReasonTopologyMismatch_domainFailedEventCreatedWithExpectedMessage() {
    testSupport.runSteps(createTopologyMismatchFailureSteps("Test this failure", null));

    assertThat("Found DOMAIN_FAILED event with expected message",
        containsEventWithMessage(getEvents(testSupport),
            DOMAIN_FAILED_EVENT,
            getFormattedMessage(DOMAIN_FAILED_EVENT_PATTERN, UID,
                getLocalizedString(TOPOLOGY_MISMATCH_EVENT_ERROR), "Test this failure",
                getLocalizedString(TOPOLOGY_MISMATCH_ERROR_EVENT_SUGGESTION))), is(true));
  }

  @Test
  void whenCreateEventStepCalledWithFailedReasonDTopologyMismatchTwice_domainFailedEventCreatedOnceWithExpectedCount() {
    testSupport.runSteps(createTopologyMismatchFailureSteps("Test failure", null));
    dispatchAddedEventWatches();
    testSupport.runSteps(createTopologyMismatchFailureSteps("Test failure", null));

    assertThat(testSupport, hasEvent(DOMAIN_FAILED_EVENT).inNamespace(NS).withCount(2));
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
            getFormattedMessage(DOMAIN_FAILED_EVENT_PATTERN, UID,
                getLocalizedString(DOMAIN_INVALID_EVENT_ERROR), "Test this failure",
                getLocalizedString(DOMAIN_INVALID_ERROR_EVENT_SUGGESTION))), is(true));
  }

  @Test
  void whenCreateEventStepCalledWithFailedReasonDomainInvalidTwice_domainFailedEventCreatedOnceWithExpectedCount() {
    testSupport.runSteps(createDomainInvalidFailureSteps("Test failure"));
    dispatchAddedEventWatches();
    testSupport.runSteps(createDomainInvalidFailureSteps("Test failure"));

    assertThat(testSupport, hasEvent(DOMAIN_FAILED_EVENT).inNamespace(NS).withCount(2));
  }

  @Test
  void whenCreateEventStepCalledWithFailedReasonReplicasTooHigh_domainFailedEventCreatedWithExpectedMessage() {
    testSupport.runSteps(Step.chain(
        createEventStep(new EventData(EventHelper.EventItem.DOMAIN_FAILED)
            .message("Test failure message")
            .failureReason(DomainFailureReason.REPLICAS_TOO_HIGH))));

    assertThat("Found DOMAIN_FAILED event with expected message",
        containsEventWithMessage(getEvents(testSupport),
            DOMAIN_FAILED_EVENT,
            getFormattedMessage(DOMAIN_FAILED_EVENT_PATTERN, UID,
                getLocalizedString(REPLICAS_TOO_HIGH_EVENT_ERROR), "Test failure message",
                getLocalizedString(REPLICAS_TOO_HIGH_ERROR_EVENT_SUGGESTION))), is(true));
  }

  @Test
  void whenCreateEventStepCalledWithFailedReasonAborted_domainFailedEventCreatedWithExpectedMessage() {
    testSupport.runSteps(Step.chain(
        createEventStep(new EventData(EventHelper.EventItem.DOMAIN_FAILED)
            .message("Test failure message")
            .failureReason(DomainFailureReason.ABORTED))));

    assertThat("Found DOMAIN_FAILED event with expected message",
        containsEventWithMessage(getEvents(testSupport),
            DOMAIN_FAILED_EVENT,
            getFormattedMessage(DOMAIN_FAILED_EVENT_PATTERN, UID,
                getLocalizedString(ABORTED_EVENT_ERROR), "Test failure message",
                getFormattedMessage(ABORTED_ERROR_EVENT_SUGGESTION))), is(true));
  }

  @Test
  void whenCreateEventStepCalledWithFailedReasonInternal_domainFailedEventCreatedWithExpectedMessage() {
    domain.getStatus().setMessage("message from domain status");

    testSupport.runSteps(Step.chain(
        createEventStep(new EventData(EventHelper.EventItem.DOMAIN_FAILED)
            .message("Test failure message")
            .failureReason(DomainFailureReason.INTERNAL))));

    assertThat("Found DOMAIN_FAILED event with expected message",
        containsEventWithMessage(getEvents(testSupport),
            DOMAIN_FAILED_EVENT,
            getFormattedMessage(DOMAIN_FAILED_EVENT_PATTERN, UID,
                getLocalizedString(INTERNAL_EVENT_ERROR), "Test failure message",
                getFormattedMessage(WILL_RETRY_EVENT_SUGGESTION, "message from domain status"))), is(true));
  }

  @Test
  void whenCreateEventStepCalledWithFailedReasonIntrospection_domainFailedEventCreatedWithExpectedMessage() {
    domain.getStatus().setMessage("message from domain status");

    testSupport.runSteps(Step.chain(
        createEventStep(new EventData(EventHelper.EventItem.DOMAIN_FAILED)
            .message("Test failure message")
            .failureReason(DomainFailureReason.INTROSPECTION))));

    assertThat("Found DOMAIN_FAILED event with expected message",
        containsEventWithMessage(getEvents(testSupport),
            DOMAIN_FAILED_EVENT,
            getFormattedMessage(DOMAIN_FAILED_EVENT_PATTERN, UID,
                getLocalizedString(INTROSPECTION_EVENT_ERROR), "Test failure message",
                getFormattedMessage(WILL_RETRY_EVENT_SUGGESTION, "message from domain status"))), is(true));
  }

  @Test
  void whenMakeRightCalled_withCreatedEventData_domainCreatedEventCreated() {
    processor.dispatchDomainWatch(new Watch.Response<>("ADDED", domain));

    assertThat("Found DOMAIN_CREATED event",
        containsEvent(getEvents(testSupport), DOMAIN_CREATED_EVENT), is(true));
  }

  @Test
  void whenMakeRightCalled_withCreatedEventData_domainCreatedEventCreatedWithExpectedMessage() {
    processor.dispatchDomainWatch(new Watch.Response<>("ADDED", domain));

    assertThat("Found DOMAIN_CREATED event with expected message",
        containsEventWithMessage(getEvents(testSupport),
            DOMAIN_CREATED_EVENT,
            getFormattedMessage(DOMAIN_CREATED_EVENT_PATTERN, UID)), is(true));
  }

  @Test
  void whenMakeRightCalled_withChangedEventData_domainChangedEventCreated() {
    processor.dispatchDomainWatch(new Watch.Response<>("MODIFIED", domain));

    assertThat("Found DOMAIN_CHANGED event",
        containsEvent(getEvents(testSupport), EventConstants.DOMAIN_CHANGED_EVENT), is(true));
  }

  @Test
  void whenMakeRightCalled_withChangedEventData_domainChangedEventCreatedWithExpectedMessage() {
    processor.dispatchDomainWatch(new Watch.Response<>("MODIFIED", domain));

    assertThat("Found DOMAIN_CHANGED event with expected message",
        containsEventWithMessage(getEvents(testSupport),
            EventConstants.DOMAIN_CHANGED_EVENT,
            getFormattedMessage(DOMAIN_CHANGED_EVENT_PATTERN, UID)), is(true));
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
    processor.dispatchDomainWatch(new Watch.Response<>("DELETED", domain));

    assertThat("Found DOMAIN_DELETED event",
        containsEvent(getEvents(testSupport), EventConstants.DOMAIN_DELETED_EVENT), is(true));
  }

  @Test
  void whenMakeRightCalled_withDeletedEventData_domainDeletedEventCreatedWithExpectedMessage() {
    processor.dispatchDomainWatch(new Watch.Response<>("DELETED", domain));

    assertThat("Found DOMAIN_DELETED event with expected message",
        containsEventWithMessage(getEvents(testSupport),
            EventConstants.DOMAIN_DELETED_EVENT,
            getFormattedMessage(DOMAIN_DELETED_EVENT_PATTERN, UID)), is(true));
  }

  @Test
  void whenMakeRightCalled_withCompletedEventData_domainCompletedEventCreatedWithExpectedMessage() {
    createMakeRightWithEvent(DOMAIN_COMPLETE).execute();

    assertThat("Found DOMAIN_COMPLETED event with expected message",
        containsEventWithMessage(getEvents(testSupport),
            DOMAIN_COMPLETED_EVENT,
            getFormattedMessage(DOMAIN_COMPLETED_EVENT_PATTERN, UID)), is(true));
  }

  private MakeRightDomainOperation createMakeRightWithEvent(EventHelper.EventItem eventItem) {
    return makeRightOperation.withEventData(new EventData(eventItem));
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
            .failureReason(DomainFailureReason.ABORTED)
            .additionalMessage(WILL_NOT_RETRY)))
    );

    assertThat("Found DOMAIN_FAILED event with expected message",
        containsEventWithMessage(getEvents(testSupport),
            EventConstants.DOMAIN_FAILED_EVENT,
            getFormattedMessage(DOMAIN_FAILED_EVENT_PATTERN, UID,
                getLocalizedString(ABORTED_EVENT_ERROR), "Test this failure", WILL_NOT_RETRY)),
        is(true));
  }

  @Test
  void whenMakeRightCalled_withAvailableEventData_domainAvailableEventCreated() {
    createMakeRightWithEvent(DOMAIN_AVAILABLE).execute();

    assertThat("Found DOMAIN_AVAILABLE event",
        containsEvent(getEvents(testSupport), DOMAIN_AVAILABLE_EVENT), is(true));
  }

  @Test
  void whenMakeRightCalled_withAvailableEventData_domainAvailableEventCreatedWithExpectedMessage() {
    createMakeRightWithEvent(DOMAIN_AVAILABLE).execute();

    assertThat("Found DOMAIN_AVAILABLE event with expected message",
        containsEventWithMessage(getEvents(testSupport),
            DOMAIN_AVAILABLE_EVENT,
            getFormattedMessage(DOMAIN_AVAILABLE_EVENT_PATTERN, UID)), is(true));
  }

  @Test
  void whenMakeRightCalled_withUnavailableEventData_domainUnavailableEventCreated() {
    createMakeRightWithEvent(DOMAIN_UNAVAILABLE).execute();

    assertThat("Found DOMAIN_UNAVAILABLE event",
        containsEvent(getEvents(testSupport), DOMAIN_UNAVAILABLE_EVENT), is(true));
  }

  @Test
  void whenMakeRightCalled_withUnavailableEventData_domainUnavailableEventCreatedWithExpectedMessage() {
    createMakeRightWithEvent(DOMAIN_UNAVAILABLE).execute();

    assertThat("Found DOMAIN_UNAVAILABLE event with expected message",
        containsEventWithMessage(getEvents(testSupport),
            DOMAIN_UNAVAILABLE_EVENT,
            getFormattedMessage(DOMAIN_UNAVAILABLE_EVENT_PATTERN, UID)), is(true));
  }

  @Test
  void whenMakeRightCalled_withIncompleteEventData_domainIncompleteEventCreated() {
    createMakeRightWithEvent(DOMAIN_INCOMPLETE).execute();

    assertThat("Found DOMAIN_INCOMPLETE event",
        containsEvent(getEvents(testSupport), DOMAIN_INCOMPLETE_EVENT), is(true));
  }

  @Test
  void whenMakeRightCalled_withIncompleteEventData_domainIncompleteEventCreatedWithExpectedMessage() {
    createMakeRightWithEvent(DOMAIN_INCOMPLETE).execute();

    assertThat("Found DOMAIN_INCOMPLETE event with expected message",
        containsEventWithMessage(getEvents(testSupport),
            DOMAIN_INCOMPLETE_EVENT,
            getFormattedMessage(DOMAIN_INCOMPLETE_EVENT_PATTERN, UID)), is(true));
  }

  @Test
  void whenMakeRightCalled_withFailureResolvedEventData_domainFailureResolvedEventCreated() {
    createMakeRightWithEvent(DOMAIN_FAILURE_RESOLVED).execute();

    assertThat("Found DOMAIN_FAILURE_RESOLVED event",
        containsEvent(getEvents(testSupport), DOMAIN_FAILURE_RESOLVED_EVENT), is(true));
  }

  @Test
  void whenMakeRightCalled_withFailureResolvedEventData_domainFailureResolvedEventCreatedWithExpectedMessage() {
    createMakeRightWithEvent(DOMAIN_FAILURE_RESOLVED).execute();

    assertThat("Found DOMAIN_FAILURE_RESOLVED event with expected message",
        containsEventWithMessage(getEvents(testSupport),
            DOMAIN_FAILURE_RESOLVED_EVENT,
            getFormattedMessage(DOMAIN_FAILURE_RESOLVED_EVENT_PATTERN, UID)), is(true));
  }

  @Test
  void whenCreateEventStepCalledForStartManagingNamespace_eventCreatedWithExpectedMessage() {
    testSupport.runSteps(createEventStep(new EventData(START_MANAGING_NAMESPACE).namespace(OP_NS).resourceName(NS)));
    assertThat("Found START_MANAGING_NAMESPACE event with expected message",
        containsEventWithMessage(getEvents(testSupport),
            EventConstants.START_MANAGING_NAMESPACE_EVENT,
            getFormattedMessage(START_MANAGING_NAMESPACE_EVENT_PATTERN, NS)), is(true));
  }

  @Test
  void whenCreateEventStepCalledForStOPManagingNamespace_eventCreatedWithExpectedMessage() {
    testSupport.runSteps(createEventStep(new EventData(STOP_MANAGING_NAMESPACE).namespace(OP_NS).resourceName(NS)));
    assertThat("Found STOP_MANAGING_NAMESPACE event with expected message",
        containsEventWithMessage(getEvents(testSupport),
            EventConstants.STOP_MANAGING_NAMESPACE_EVENT,
            getFormattedMessage(STOP_MANAGING_NAMESPACE_EVENT_PATTERN, NS)), is(true));
  }

  @Test
  void whenCreateEventStepCalledWithNSWatchStartedEvent_eventCreatedWithExpectedNamespace() {
    runCreateNSWatchingStartedEventStep();
    assertThat("Found NAMESPACE_WATCHING_STARTED event with expected namespace",
        containsEventWithNamespace(getEvents(testSupport),
            NAMESPACE_WATCHING_STARTED_EVENT, NS), is(true));
  }

  @Test
  void whenCreateEventStepCalledWithNSWatchStartedEvent_eventCreatedWithExpectedLabels() {
    runCreateNSWatchingStartedEventStep();

    Map<String, String> expectedLabels = new HashMap<>();
    expectedLabels.put(LabelConstants.CREATEDBYOPERATOR_LABEL, "true");
    assertThat("Found NAMESPACE_WATCHING_STARTED event with expected labels",
        containsEventWithLabels(getEvents(testSupport),
            NAMESPACE_WATCHING_STARTED_EVENT, expectedLabels), is(true));
  }

  private void runCreateNSWatchingStartedEventStep() {
    testSupport.runSteps(createEventStep(domainNamespaces,
        new EventData(NAMESPACE_WATCHING_STARTED).namespace(NS).resourceName(NS), null));
  }

  @Test
  void whenNSWatchStartedEventCreatedTwice_eventCreatedOnceWithExpectedCount() {
    runCreateNSWatchingStartedEventStep();
    dispatchAddedEventWatches();
    runCreateNSWatchingStartedEventStep();

    assertThat("Found 1 NAMESPACE_WATCHING_STARTED event with expected count",
        containsOneEventWithCount(getEvents(testSupport),
            NAMESPACE_WATCHING_STARTED_EVENT, 2), is(true));
  }

  @Test
  void whenNSWatchStartedEventCreated_thenDelete_eventCreatedTwice() {
    runCreateNSWatchingStartedEventStep();
    dispatchAddedEventWatches();
    dispatchDeletedEventWatches();
    runCreateNSWatchingStartedEventStep();


    assertThat("Found 2 NAMESPACE_WATCHING_STARTED events",
        containsEventsWithCountOne(getEvents(testSupport),
            NAMESPACE_WATCHING_STARTED_EVENT, 2), is(true));
  }

  @Test
  void whenNSWatchStartedEventCreated_fail403OnCreate_foundExpectedLogMessage() {
    loggerControl.collectLogMessages(logRecords, CREATING_EVENT_FORBIDDEN);
    testSupport.failOnCreate(EVENT, NS, HTTP_FORBIDDEN);

    runCreateNSWatchingStartedEventStep();

    assertThat(logRecords, containsWarning(CREATING_EVENT_FORBIDDEN));
  }

  @Test
  void whenNSWatchStartedEventCreated_fail403OnCreate_startManagingNSFailedEventGenerated() {
    testSupport.failOnCreate(EVENT, NS, HTTP_FORBIDDEN);

    runCreateNSWatchingStartedEventStep();

    assertThat("Found 1 NAMESPACE_WATCHING_STARTED_FAILED event",
        containsEventsWithCountOne(getEvents(testSupport),
            START_MANAGING_NAMESPACE_FAILED_EVENT, 1), is(true));
  }

  @Test
  void whenNSWatchStartedEventCreated_fail403OnCreate_startManagingNSFailedEventGeneratedWithExpectedMessage() {
    testSupport.failOnCreate(EVENT, NS, HTTP_FORBIDDEN);

    runCreateNSWatchingStartedEventStep();

    assertThat("Found 1 NAMESPACE_WATCHING_STARTED_FAILED event with expected message",
        containsEventWithMessage(getEvents(testSupport),
            EventConstants.START_MANAGING_NAMESPACE_FAILED_EVENT,
            getFormattedMessage(START_MANAGING_NAMESPACE_FAILED_EVENT_PATTERN, NS)), is(true));
  }

  @Test
  void whenNSWatchStartedEventCreated_fail403OnCreate_startManagingNSFailedEventGeneratedWithExpectedLabel() {
    testSupport.failOnCreate(EVENT, NS, HTTP_FORBIDDEN);

    runCreateNSWatchingStartedEventStep();
    Map<String, String> expectedLabels = new HashMap<>();
    expectedLabels.put(LabelConstants.CREATEDBYOPERATOR_LABEL, "true");

    assertThat("Found 1 NAMESPACE_WATCHING_STARTED_FAILED event with expected label",
        containsEventWithLabels(getEvents(testSupport),
            START_MANAGING_NAMESPACE_FAILED_EVENT, expectedLabels), is(true));
  }

  @Test
  void whenNSWatchStartedEventCreated_fail403OnCreate_startManagingNSFailedEventGeneratedWithExpectedNS() {
    testSupport.failOnCreate(EVENT, NS, HTTP_FORBIDDEN);

    runCreateNSWatchingStartedEventStep();

    assertThat("Found 1 NAMESPACE_WATCHING_STARTED_FAILED event with expected namespace",
        containsEventWithNamespace(getEvents(testSupport),
            EventConstants.START_MANAGING_NAMESPACE_FAILED_EVENT, OP_NS), is(true));
  }

  @Test
  void whenNSWatchStartedEventCreatedWithNotificationsHavingInvolvedObject_eventCreatedOnceWithOneCount() {
    runCreateNSWatchingStartedEventStep();
    dispatchAddedEventWatches();

    assertThat("Found 1 NAMESPACE_WATCHING_STARTED event with count of 1",
        getCachedNSEvents(NS,NAMESPACE_WATCHING_STARTED_EVENT), notNullValue());
  }

  @Test
  void whenNSWatchStartedEventCreatedWithNotificationsWithoutInvolvedObject_eventCreatedOnceWithOneCount() {
    runCreateNSWatchingStartedEventStep();
    dispatchAddedEventWatchesWithoutInvolvedObject();

    assertThat("Found 1 NAMESPACE_WATCHING_STARTED event with expected count",
        getCachedNSEvents(NS,NAMESPACE_WATCHING_STARTED_EVENT), equalTo(null));
  }

  @Test
  void whenNSWatchStartedEventCreatedWithNotificationsWithoutInvolvedObjectName_eventCreatedOnceWithOneCount() {
    runCreateNSWatchingStartedEventStep();
    dispatchAddedEventWatchesWithoutInvolvedObjectName();

    assertThat("Found 1 NAMESPACE_WATCHING_STARTED event with expected count",
        getCachedNSEvents(NS,NAMESPACE_WATCHING_STARTED_EVENT), equalTo(null));
  }

  @Test
  void whenNSWatchStartedEventCreatedWithNotificationsWithoutInvolvedObjectKind_eventCreatedOnceWithOneCount() {
    runCreateNSWatchingStartedEventStep();
    dispatchAddedEventWatchesWithoutInvolvedObjectKind();

    assertThat("Found 1 NAMESPACE_WATCHING_STARTED event with expected count",
        getCachedNSEvents(NS,NAMESPACE_WATCHING_STARTED_EVENT), equalTo(null));
  }

  public CoreV1Event getCachedNSEvents(String namespace, String reason) {
    return nsEventObjects.get(namespace).getExistingEvent(createReferenceEvent(namespace, reason));
  }

  private CoreV1Event createReferenceEvent(String namespace, String reason) {
    String msg = "Started watching namespace " + namespace + ".";
    return new CoreV1Event().reason(reason).message(msg)
        .involvedObject(new V1ObjectReference().kind("Namespace").name(namespace).namespace(namespace));
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

    assertThat(logRecords, containsInfo(CREATING_EVENT_FORBIDDEN).withParams(NAMESPACE_WATCHING_STOPPED_EVENT, NS));
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

    assertThat(logRecords, containsInfo(CREATING_EVENT_FORBIDDEN).withParams(NAMESPACE_WATCHING_STOPPED_EVENT, NS));
  }

  @Test
  void whenCreateEventStepCalledForNSWatchStartedEvent_eventCreatedWithExpectedMessage() {
    runCreateNSWatchingStartedEventStep();

    assertThat("Found START_MANAGING_NAMESPACE event with expected message",
        containsEventWithMessage(getEvents(testSupport),
            NAMESPACE_WATCHING_STARTED_EVENT,
            getFormattedMessage(NAMESPACE_WATCHING_STARTED_EVENT_PATTERN, NS)), is(true));
  }

  @Test
  void whenCreateEventStepCalledForNSWatchStoppedEvent_eventCreatedWithExpectedMessage() {
    testSupport.runSteps(createEventStep(new EventData(NAMESPACE_WATCHING_STOPPED).namespace(NS).resourceName(NS)));
    assertThat("Found STOP_MANAGING_NAMESPACE event with expected message",
        containsEventWithMessage(getEvents(testSupport),
            NAMESPACE_WATCHING_STOPPED_EVENT,
            getFormattedMessage(NAMESPACE_WATCHING_STOPPED_EVENT_PATTERN, NS)), is(true));
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
            getFormattedMessage(DOMAIN_ROLL_STARTING_EVENT_PATTERN, UID, "abcde")), is(true));
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
            getFormattedMessage(DOMAIN_ROLL_COMPLETED_EVENT_PATTERN, UID)), is(true));
  }

  @Test
  void whenMakeRightCalled_withClusterDeletedEventData_clusterDeletedEventCreated() {
    ClusterPresenceInfo info = new ClusterPresenceInfo(cluster1);
    processor.registerClusterPresenceInfo(info);

    processor.dispatchClusterWatch(new Watch.Response<>("DELETED", cluster1));
    processor.unregisterClusterPresenceInfo(info);

    assertThat("Found CLUSTER_DELETED event",
        containsEvent(getEvents(testSupport), CLUSTER_DELETED_EVENT), is(true));
  }

  @Test
  void whenMakeRightCalled_withClusterDeletedEventData_clusterDeletedEventCreatedWithExpectedMessage() {
    ClusterPresenceInfo info = new ClusterPresenceInfo(cluster1);
    processor.registerClusterPresenceInfo(info);

    processor.dispatchClusterWatch(new Watch.Response<>("DELETED", cluster1));
    processor.unregisterClusterPresenceInfo(info);

    assertThat("Found CLUSTER_DELETED event with expected message",
        containsEventWithMessage(getEvents(testSupport),
            CLUSTER_DELETED_EVENT,
            getFormattedMessage(CLUSTER_DELETED_EVENT_PATTERN, CLUSTER_1_NAME)), is(true));
  }

  @Test
  void whenMakeRightCalled_withClusterDeletedEventData_clusterDeletedEventCreatedWithExpectedNamespace() {
    ClusterPresenceInfo info = new ClusterPresenceInfo(cluster1);
    processor.registerClusterPresenceInfo(info);

    processor.dispatchClusterWatch(new Watch.Response<>("DELETED", cluster1));
    processor.unregisterClusterPresenceInfo(info);

    assertThat("Found CLUSTER_DELETED event with expected namespace",
        containsEventWithNamespace(getEvents(testSupport),
            CLUSTER_DELETED_EVENT, NS), is(true));
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
            getFormattedMessage(POD_CYCLE_STARTING_EVENT_PATTERN, "12345", "abcde")), is(true));
  }

  @Test
  void whenClusterAvailableEventCreatedTwice_verifyEventReplaced() {
    testSupport.runSteps(EventHelper.createClusterResourceEventStep(new EventData(CLUSTER_AVAILABLE)));
    dispatchAddedEventWatches();
    testSupport.runSteps(EventHelper.createClusterResourceEventStep(new EventData(CLUSTER_AVAILABLE)));

    assertThat("Found CLUSTER_AVAILABLE event with unexpected count",
        containsOneEventWithCount(getEvents(testSupport), CLUSTER_AVAILABLE_EVENT, 2), is(true));
  }

  @Test
  void whenClusterAvailableEventCreatedTwice_fail409OnReplace_eventCreatedOnceWithExpectedCount() {
    testSupport.addRetryStrategy(retryStrategy);
    Step eventStep = EventHelper.createClusterResourceEventStep(new EventData(CLUSTER_AVAILABLE)
        .namespace(NS).resourceName(NS));
    testSupport.runSteps(eventStep);
    dispatchAddedEventWatches();

    CoreV1Event event = EventTestUtils.getEventWithReason(getEvents(testSupport), CLUSTER_AVAILABLE_EVENT);
    testSupport.failOnReplace(EVENT, EventTestUtils.getName(event), NS, HTTP_CONFLICT);

    testSupport.runSteps(eventStep);

    assertThat("Found 2 CLUSTER_AVAILABLE event with expected count 1",
        containsEventsWithCountOne(getEvents(testSupport),
            CLUSTER_AVAILABLE_EVENT, 2), is(true));
  }

  private void dispatchAddedEventWatches() {
    List<CoreV1Event> events = getEvents(testSupport);
    for (CoreV1Event event : events) {
      dispatchAddedEventWatch(event);
    }
  }

  private void dispatchAddedEventWatchesWithoutInvolvedObject() {
    List<CoreV1Event> events = getEvents(testSupport);
    for (CoreV1Event event : events) {
      event.setInvolvedObject(null);
      dispatchAddedEventWatch(event);
    }
  }

  private void dispatchAddedEventWatchesWithoutInvolvedObjectName() {
    List<CoreV1Event> events = getEvents(testSupport);
    for (CoreV1Event event : events) {
      Optional.ofNullable(event.getInvolvedObject()).ifPresent(r -> r.setName(null));
      dispatchAddedEventWatch(event);
    }
  }

  private void dispatchAddedEventWatchesWithoutInvolvedObjectKind() {
    List<CoreV1Event> events = getEvents(testSupport);
    for (CoreV1Event event : events) {
      Optional.ofNullable(event.getInvolvedObject()).ifPresent(r -> r.setKind(null));
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
