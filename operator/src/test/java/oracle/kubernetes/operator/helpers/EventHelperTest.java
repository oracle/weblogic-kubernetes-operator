// Copyright (c) 2020, Oracle Corporation and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.helpers;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.meterware.simplestub.Memento;
import com.meterware.simplestub.StaticStubSupport;
import io.kubernetes.client.openapi.models.V1Event;
import oracle.kubernetes.operator.DomainProcessorDelegateStub;
import oracle.kubernetes.operator.DomainProcessorImpl;
import oracle.kubernetes.operator.DomainProcessorTestSetup;
import oracle.kubernetes.operator.EventConstants;
import oracle.kubernetes.operator.MakeRightDomainOperation;
import oracle.kubernetes.operator.helpers.EventHelper.EventData;
import oracle.kubernetes.operator.work.TerminalStep;
import oracle.kubernetes.utils.TestUtils;
import oracle.kubernetes.weblogic.domain.DomainConfigurator;
import oracle.kubernetes.weblogic.domain.DomainConfiguratorFactory;
import oracle.kubernetes.weblogic.domain.model.Domain;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import static oracle.kubernetes.operator.DomainProcessorTestSetup.UID;
import static oracle.kubernetes.operator.DomainStatusUpdater.createFailedStep;
import static oracle.kubernetes.operator.EventConstants.DOMAIN_CHANGED_PATTERN;
import static oracle.kubernetes.operator.EventConstants.DOMAIN_CREATED_PATTERN;
import static oracle.kubernetes.operator.EventConstants.DOMAIN_DELETED_PATTERN;
import static oracle.kubernetes.operator.EventConstants.DOMAIN_PROCESSING_ABORTED_PATTERN;
import static oracle.kubernetes.operator.EventConstants.DOMAIN_PROCESSING_FAILED_EVENT;
import static oracle.kubernetes.operator.EventConstants.DOMAIN_PROCESSING_FAILED_PATTERN;
import static oracle.kubernetes.operator.EventConstants.DOMAIN_PROCESSING_RETRYING_PATTERN;
import static oracle.kubernetes.operator.EventConstants.DOMAIN_PROCESSING_STARTED_EVENT;
import static oracle.kubernetes.operator.EventConstants.DOMAIN_PROCESSING_STARTED_PATTERN;
import static oracle.kubernetes.operator.EventConstants.DOMAIN_PROCESSING_SUCCEEDED_PATTERN;
import static oracle.kubernetes.operator.EventConstants.WEBLOGIC_OPERATOR_COMPONENT;
import static oracle.kubernetes.operator.ProcessingConstants.JOB_POD_NAME;
import static oracle.kubernetes.operator.helpers.EventHelper.EventItem.DOMAIN_CHANGED;
import static oracle.kubernetes.operator.helpers.EventHelper.EventItem.DOMAIN_CREATED;
import static oracle.kubernetes.operator.helpers.EventHelper.EventItem.DOMAIN_DELETED;
import static oracle.kubernetes.operator.helpers.EventHelper.EventItem.DOMAIN_PROCESSING_ABORTED;
import static oracle.kubernetes.operator.helpers.EventHelper.EventItem.DOMAIN_PROCESSING_RETRYING;
import static oracle.kubernetes.operator.helpers.EventHelper.EventItem.DOMAIN_PROCESSING_STARTED;
import static oracle.kubernetes.operator.helpers.EventHelper.EventItem.DOMAIN_PROCESSING_SUCCEEDED;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

public class EventHelperTest {
  private static final String WEBLOGIC_OPERATOR_POD_NAME = "my-weblogic-operator-1234";
  private static final String OP_NS = "operator-namespace";
  private static final String POD_NAME_ENV = "MY_POD_NAME";

  private final List<Memento> mementos = new ArrayList<>();
  private final KubernetesTestSupport testSupport = new KubernetesTestSupport();
  private final DomainProcessorDelegateStub processorDelegate = DomainProcessorDelegateStub.createDelegate(testSupport);
  private final DomainProcessorImpl processor = new DomainProcessorImpl(processorDelegate);
  private final Domain domain = DomainProcessorTestSetup.createTestDomain();
  private final Map<String, Map<String, DomainPresenceInfo>> presenceInfoMap = new HashMap<>();
  private final DomainPresenceInfo info = new DomainPresenceInfo(domain);
  private final MakeRightDomainOperation makeRightOperation
      = processor.createMakeRightOperation(info);
  private final String jobPodName = LegalNames.toJobIntrospectorName(UID);

  @Before
  public void setUp() throws Exception {
    mementos.add(TestUtils.silenceOperatorLogger());
    mementos.add(testSupport.install());
    mementos.add(StaticStubSupport.install(DomainProcessorImpl.class, "DOMAINS", presenceInfoMap));
    mementos.add(TuningParametersStub.install());
    mementos.add(HelmAccessStub.install());

    testSupport.addToPacket(JOB_POD_NAME, jobPodName);
    testSupport.addDomainPresenceInfo(info);
    testSupport.defineResources(domain);
    DomainProcessorTestSetup.defineRequiredResources(testSupport);
    HelmAccessStub.defineVariable("OPERATOR_NAMESPACE", OP_NS);
    System.setProperty(POD_NAME_ENV, WEBLOGIC_OPERATOR_POD_NAME);
  }

  @After
  public void tearDown() {
    mementos.forEach(Memento::revert);
  }

  @Test
  public void whenDomainMakeRightCalled_domainProcessingStartedEventCreated() {
    makeRightOperation.execute();

    assertThat("Event DOMAIN_PROCESSING_STARTED",
        containsEvent(getEvents(), DOMAIN_PROCESSING_STARTED_EVENT), is(Boolean.TRUE));
  }

  @Test
  public void whenDomainMakeRightCalled_domainProcessingStartedEventCreatedWithExpectedMessage() {
    makeRightOperation.execute();

    assertThat("Event DOMAIN_PROCESSING_STARTED message",
        containsEventWithMessage(getEvents(),
            DOMAIN_PROCESSING_STARTED_EVENT,
            String.format(DOMAIN_PROCESSING_STARTED_PATTERN, UID)),
            is(Boolean.TRUE));
  }

  @Test
  public void whenDomainMakeRightCalled_domainProcessingStartedEventCreatedWithReportingComponent()
      throws Exception {
    makeRightOperation.execute();

    assertThat("Event reporting component",
        containsEventWithComponent(getEvents(), DOMAIN_PROCESSING_STARTED_EVENT),
        is(Boolean.TRUE));
  }


  @Test
  public void whenDomainMakeRightCalled_domainProcessingStartedEventCreatedWithReportingInstance()
      throws Exception {
    String namespaceFromHelm = NamespaceHelper.getOperatorNamespace();
    //testSupport.addToPacket(OPERATOR_POD_NAME, WEBLOGIC_OPERATOR_POD_NAME);
    testSupport.runSteps(
        new EventHelper().createEventStep(
            new EventData(DOMAIN_PROCESSING_STARTED)
        ));

    assertThat("Operator namespace ",
        namespaceFromHelm, equalTo(OP_NS));

    assertThat("Event reporting instance",
        containsEventWithInstance(getEvents(), DOMAIN_PROCESSING_STARTED_EVENT, WEBLOGIC_OPERATOR_POD_NAME),
        is(Boolean.TRUE));
  }

  @Test
  public void whenCreateEventStepCalled_domainProcessingSucceededEventCreated() {
    testSupport.runSteps(
        new EventHelper().createEventStep(
            new EventData(DOMAIN_PROCESSING_SUCCEEDED)
        ));

    assertThat("Event DOMAIN_PROCESSING_SUCCEEDED",
        containsEvent(getEvents(), EventConstants.DOMAIN_PROCESSING_SUCCEEDED_EVENT), is(Boolean.TRUE));
  }

  @Test
  public void whenCreateEventStepCalled_domainProcessingSucceededEventCreatedWithExpectedMessage() {
    testSupport.runSteps(
        new EventHelper().createEventStep(new EventData(DOMAIN_PROCESSING_SUCCEEDED)));

    assertThat("Event DOMAIN_PROCESSING_SUCCEEDED message",
        containsEventWithMessage(getEvents(),
            EventConstants.DOMAIN_PROCESSING_SUCCEEDED_EVENT,
            String.format(DOMAIN_PROCESSING_SUCCEEDED_PATTERN, UID)),
        is(Boolean.TRUE));
  }


  @Test
  public void whenMakeRightCalled_withFailedEventData_domainProcessingFailedEventCreated() {
    testSupport.runSteps(createFailedStep("FAILED", "Test failure", new TerminalStep()));

    assertThat("Event DOMAIN_PROCESSING_FAILED",
        containsEvent(getEvents(), DOMAIN_PROCESSING_FAILED_EVENT), is(Boolean.TRUE));
  }

  @Test
  public void whenMakeRightCalled_withFailedEventData_domainProcessingFailedEventCreatedWithExpectedMessage() {
    testSupport.runSteps(createFailedStep("FAILED", "Test this failure", new TerminalStep()));

    assertThat("Event DOMAIN_PROCESSING_FAILED message",
        containsEventWithMessage(getEvents(),
            DOMAIN_PROCESSING_FAILED_EVENT,
            String.format(DOMAIN_PROCESSING_FAILED_PATTERN, UID, "Test this failure")),
        is(Boolean.TRUE));
  }

  @Test
  public void whenMakeRightCalled_withRetryingEventData_domainProcessingRetryingEventCreated() {
    makeRightOperation.withEventData(new EventData(DOMAIN_PROCESSING_RETRYING)).execute();

    assertThat("Event DOMAIN_PROCESSING_RETRYING",
        containsEvent(getEvents(), EventConstants.DOMAIN_PROCESSING_RETRYING_EVENT), is(Boolean.TRUE));
  }

  @Test
  public void whenMakeRightCalled_withRetryingEventData_domainProcessingRetryingEventCreatedWithExpectedMessage() {
    makeRightOperation.withEventData(new EventData(DOMAIN_PROCESSING_RETRYING)).execute();

    assertThat("Event DOMAIN_PROCESSING_RETRYING message",
        containsEventWithMessage(getEvents(),
            EventConstants.DOMAIN_PROCESSING_RETRYING_EVENT,
            String.format(DOMAIN_PROCESSING_RETRYING_PATTERN, UID)),
        is(Boolean.TRUE));
  }

  @Test
  public void whenMakeRightCalled_withCreatedEventData_domainCreatedEventCreated() {
    makeRightOperation.withEventData(new EventData(DOMAIN_CREATED)).execute();

    assertThat("Event DOMAIN_CREATED",
        containsEvent(getEvents(), EventConstants.DOMAIN_CREATED_EVENT), is(Boolean.TRUE));
  }

  @Test
  public void whenMakeRightCalled_withCreatedEventData_domainCreatedEventCreatedWithExpectedMessage() {
    makeRightOperation.withEventData(new EventData(DOMAIN_CREATED)).execute();

    assertThat("Event DOMAIN_CREATED message",
        containsEventWithMessage(getEvents(),
            EventConstants.DOMAIN_CREATED_EVENT,
            String.format(DOMAIN_CREATED_PATTERN, UID)),
        is(Boolean.TRUE));
  }

  @Test
  public void whenMakeRightCalled_withChangedEventData_domainChangedEventCreated() {
    makeRightOperation.withEventData(new EventData(DOMAIN_CHANGED)).execute();

    assertThat("Event DOMAIN_CHANGED",
        containsEvent(getEvents(), EventConstants.DOMAIN_CHANGED_EVENT), is(Boolean.TRUE));
  }

  @Test
  public void whenMakeRightCalled_withChangedEventData_domainChangedEventCreatedWithExpectedMessage() {
    makeRightOperation.withEventData(new EventData(DOMAIN_CHANGED)).execute();

    assertThat("Event DOMAIN_CHANGED message",
        containsEventWithMessage(getEvents(),
            EventConstants.DOMAIN_CHANGED_EVENT,
            String.format(DOMAIN_CHANGED_PATTERN, UID)),
        is(Boolean.TRUE));
  }


  @Test
  public void whenMakeRightCalled_withDeletedEventData_domainDeletedEventCreated() {
    makeRightOperation.withEventData(new EventData(DOMAIN_DELETED)).execute();

    assertThat("Event DOMAIN_DELETED",
        containsEvent(getEvents(), EventConstants.DOMAIN_DELETED_EVENT), is(Boolean.TRUE));
  }

  @Test
  public void whenMakeRightCalled_withDeletedEventData_domainDeletedEventCreatedWithExpectedMessage() {
    makeRightOperation.withEventData(new EventData(DOMAIN_DELETED)).execute();

    assertThat("Event DOMAIN_DELETED message",
        containsEventWithMessage(getEvents(),
            EventConstants.DOMAIN_DELETED_EVENT,
            String.format(DOMAIN_DELETED_PATTERN, UID)),
        is(Boolean.TRUE));
  }

  @Test
  public void whenMakeRightCalled_withAbortedEventData_domainProcessingFailedEventCreated() {
    makeRightOperation
        .withEventData(new EventData(DOMAIN_PROCESSING_ABORTED, "Test this failure"))
        .execute();

    assertThat("Event DOMAIN_PROCESSING_ABORTED",
        containsEvent(getEvents(), EventConstants.DOMAIN_PROCESSING_ABORTED_EVENT), is(Boolean.TRUE));
  }

  @Test
  public void whenMakeRightCalled_withAbortedEventData_domainProcessingFailedEventCreatedWithExpectedMessage() {
    makeRightOperation
        .withEventData(new EventData(DOMAIN_PROCESSING_ABORTED, "Test this failure"))
        .execute();

    assertThat("Event DOMAIN_PROCESSING_ABORTED message",
        containsEventWithMessage(getEvents(),
            EventConstants.DOMAIN_PROCESSING_ABORTED_EVENT,
            String.format(DOMAIN_PROCESSING_ABORTED_PATTERN, UID, "Test this failure")),
        is(Boolean.TRUE));
  }

  private V1Event getEventMatchesReason(List<V1Event> events, String reason) {
    return (V1Event) Optional.ofNullable(events).get()
        .stream()
        .filter(e -> EventHelperTest.reasonMatches(e, reason)).findFirst().orElse(null);
  }

  private Object containsEventWithMessage(List<V1Event> events, String reason, String message) {
    return Optional.ofNullable(getEventMatchesReason(events, reason))
        .map(V1Event::getMessage)
        .orElse("")
        .equals(message);
  }

  private Object containsEventWithComponent(List<V1Event> events, String reason) {
    return Optional.ofNullable(getEventMatchesReason(events, reason))
        .map(V1Event::getReportingComponent)
        .orElse("")
        .equals(WEBLOGIC_OPERATOR_COMPONENT);
  }

  private Object containsEventWithInstance(List<V1Event> events, String reason, String opName) {
    String instance = Optional.ofNullable(getEventMatchesReason(events, reason))
        .map(V1Event::getReportingInstance)
        .orElse("");
    return instance.equals(opName);
  }

  private List<V1Event> getEvents() {
    return testSupport.getResources(KubernetesTestSupport.EVENT);
  }

  private DomainConfigurator configureDomain(Domain domain) {
    return DomainConfiguratorFactory.forDomain(domain);
  }

  private boolean containsEvent(List<V1Event> events, String reason) {
    return getEventMatchesReason(events, reason) != null;
  }

  static boolean reasonMatches(V1Event event, String eventReason) {
    return eventReason.equals(event.getReason());
  }
}