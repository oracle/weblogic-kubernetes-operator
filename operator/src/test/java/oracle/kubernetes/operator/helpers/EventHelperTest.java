// Copyright (c) 2019, 2020, Oracle Corporation and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.helpers;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.meterware.simplestub.Memento;
import com.meterware.simplestub.StaticStubSupport;
import io.kubernetes.client.openapi.models.V1Event;
import io.kubernetes.client.openapi.models.V1ObjectMeta;
import io.kubernetes.client.openapi.models.V1Pod;
import oracle.kubernetes.operator.DomainProcessorDelegateStub;
import oracle.kubernetes.operator.DomainProcessorImpl;
import oracle.kubernetes.operator.DomainProcessorTestSetup;
import oracle.kubernetes.operator.EventConstants;
import oracle.kubernetes.operator.JobAwaiterStepFactory;
import oracle.kubernetes.operator.MakeRightDomainOperation;
import oracle.kubernetes.operator.NoopWatcherStarter;
import oracle.kubernetes.operator.builders.StubWatchFactory;
import oracle.kubernetes.operator.helpers.EventHelper.EventData;
import oracle.kubernetes.operator.rest.ScanCacheStub;
import oracle.kubernetes.operator.utils.InMemoryCertificates;
import oracle.kubernetes.operator.work.TerminalStep;
import oracle.kubernetes.utils.TestUtils;
import oracle.kubernetes.weblogic.domain.DomainConfigurator;
import oracle.kubernetes.weblogic.domain.DomainConfiguratorFactory;
import oracle.kubernetes.weblogic.domain.model.Domain;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import static com.meterware.simplestub.Stub.createNiceStub;
import static oracle.kubernetes.operator.DomainProcessorTestSetup.UID;
import static oracle.kubernetes.operator.DomainStatusUpdater.createFailedStep;
import static oracle.kubernetes.operator.ProcessingConstants.JOBWATCHER_COMPONENT_NAME;
import static oracle.kubernetes.operator.ProcessingConstants.JOB_POD_NAME;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

public class EventHelperTest {
  public static final String WEBLOGIC_OPERATOR_POD_NAME = "my-weblogic-operator";
  private static final String OP_NS = "operator-namespace";

  private final List<Memento> mementos = new ArrayList<>();
  private final KubernetesTestSupport testSupport = new KubernetesTestSupport();
  private final DomainProcessorDelegateStub processorDelegate = DomainProcessorDelegateStub.createDelegate(testSupport);
  private final DomainProcessorImpl processor = new DomainProcessorImpl(processorDelegate);
  private final Domain domain = DomainProcessorTestSetup.createTestDomain();
  private final DomainConfigurator domainConfigurator = configureDomain(domain);
  private final Map<String, Map<String, DomainPresenceInfo>> presenceInfoMap = new HashMap<>();
  private final DomainPresenceInfo info = new DomainPresenceInfo(domain);
  private final MakeRightDomainOperation makeRightOperation
      = processor.createMakeRightOperation(info);
  private final String jobPodName = LegalNames.toJobIntrospectorName(UID);
  private String namespaceFromHelm;

  @Before
  public void setUp() throws Exception {
    mementos.add(TestUtils.silenceOperatorLogger());
    mementos.add(testSupport.install());
    mementos.add(StaticStubSupport.install(DomainProcessorImpl.class, "DOMAINS", presenceInfoMap));
    mementos.add(TuningParametersStub.install());
    mementos.add(InMemoryCertificates.install());
    mementos.add(UnitTestHash.install());
    mementos.add(ScanCacheStub.install());
    mementos.add(StubWatchFactory.install());
    mementos.add(NoopWatcherStarter.install());
    mementos.add(HelmAccessStub.install());
    HelmAccessStub.defineVariable("OPERATOR_NAMESPACE", OP_NS);
    testSupport.defineResources(domain);
    DomainProcessorTestSetup.defineRequiredResources(testSupport);
    testSupport.addToPacket(JOB_POD_NAME, jobPodName);
    testSupport.addDomainPresenceInfo(info);
    testSupport.addComponent(JOBWATCHER_COMPONENT_NAME,
        JobAwaiterStepFactory.class,
        createNiceStub(JobAwaiterStepFactory.class));
    testSupport.defineResources(createOperatorPod(WEBLOGIC_OPERATOR_POD_NAME, OP_NS));

  }

  @After
  public void tearDown() {
    mementos.forEach(Memento::revert);
  }

  @Test
  public void whenDomainMakeRightCalled_domainProcessingStartedEventCreated() {
    makeRightOperation.execute();

    assertThat("Event reason", containsEvent(getEvents(), EventConstants.DOMAIN_PROCESSING_STARTED), is(Boolean.TRUE));
  }

  @Test
  public void whenDomainMakeRightCalled_domainProcessingStartedEventCreatedWithExpectedMessage() {
    makeRightOperation.execute();

    assertThat("Event message",
        containsEventWithMessage(getEvents(),
            EventConstants.DOMAIN_PROCESSING_STARTED,
            String.format(EventConstants.DOMAIN_PROCESSING_STARTED_PATTERN, UID)),
            is(Boolean.TRUE));
  }

  @Test
  public void whenDomainMakeRightCalled_domainProcessingStartedEventCreatedWithReportingComponent()
      throws Exception {
    makeRightOperation.execute();

    assertThat("Event reporting component",
        containsEventWithComponent(getEvents(), EventConstants.DOMAIN_PROCESSING_STARTED),
        is(Boolean.TRUE));
  }

  @Test
  public void whenDomainMakeRightCalled_domainProcessingStartedEventCreatedWithReportingInstance()
      throws Exception {
    namespaceFromHelm = NamespaceHelper.getOperatorNamespace();

    makeRightOperation.execute();

    assertThat("Operator namespace ",
        namespaceFromHelm, equalTo(OP_NS));

    assertThat("Event reporting instance",
        containsEventWithInstance(getEvents(), EventConstants.DOMAIN_PROCESSING_STARTED, WEBLOGIC_OPERATOR_POD_NAME),
        is(Boolean.TRUE));
  }

  @Test
  public void whenCreateEventStepCalled_domainProcessingSucceededEventCreated() {
    testSupport.runSteps(
        new EventHelper().createEventStep(
            new EventData(EventHelper.EventItem.DOMAIN_PROCESSING_SUCCEEDED)
        ));

    assertThat("Event reason",
        containsEvent(getEvents(), EventConstants.DOMAIN_PROCESSING_SUCCEEDED), is(Boolean.TRUE));
  }

  @Test
  public void whenCreateEventStepCalled_domainProcessingSucceededEventCreatedWithExpectedMessage() {
    testSupport.runSteps(
        new EventHelper().createEventStep(new EventData(EventHelper.EventItem.DOMAIN_PROCESSING_SUCCEEDED)));

    assertThat("Event message",
        containsEventWithMessage(getEvents(),
            EventConstants.DOMAIN_PROCESSING_SUCCEEDED,
            String.format(EventConstants.DOMAIN_PROCESSING_SUCCEEDED_PATTERN, UID)),
        is(Boolean.TRUE));
  }


  @Test
  public void whenMakeRightCalled_withFailedEventData_domainProcessingFailedEventCreated() {
    testSupport.runSteps(createFailedStep("FAILED", "Test failure", new TerminalStep()));

    assertThat("Event DOMAIN_PROCESSING_FAILED",
        containsEvent(getEvents(), EventConstants.DOMAIN_PROCESSING_FAILED), is(Boolean.TRUE));
  }

  @Test
  public void whenMakeRightCalled_withFailedEventData_domainProcessingFailedEventCreatedWithExpectedMessage() {
    testSupport.runSteps(createFailedStep("FAILED", "Test this failure", new TerminalStep()));

    assertThat("Event message",
        containsEventWithMessage(getEvents(),
            EventConstants.DOMAIN_PROCESSING_FAILED,
            String.format(EventConstants.DOMAIN_PROCESSING_FAILED_PATTERN, UID, "Test this failure")),
        is(Boolean.TRUE));
  }

  @Test
  public void whenMakeRightCalled_withRetryingEventData_domainProcessingRetryingEventCreated() {
    makeRightOperation.withEventData(new EventData(EventHelper.EventItem.DOMAIN_PROCESSING_RETRYING)).execute();

    assertThat("Event DOMAIN_CREATED",
        containsEvent(getEvents(), EventConstants.DOMAIN_PROCESSING_RETRYING), is(Boolean.TRUE));
  }

  @Test
  public void whenMakeRightCalled_withRetryingEventData_domainProcessingRetryingEventCreatedWithExpectedMessage() {
    makeRightOperation.withEventData(new EventData(EventHelper.EventItem.DOMAIN_PROCESSING_RETRYING)).execute();

    assertThat("Event message",
        containsEventWithMessage(getEvents(),
            EventConstants.DOMAIN_PROCESSING_RETRYING,
            String.format(EventConstants.DOMAIN_PROCESSING_RETRYING_PATTERN, UID)),
        is(Boolean.TRUE));
  }

  @Test
  public void whenMakeRightCalled_withCreatedEventData_domainCreatedEventCreated() {
    makeRightOperation.withEventData(new EventData(EventHelper.EventItem.DOMAIN_CREATED)).execute();

    assertThat("Event DOMAIN_CREATED",
        containsEvent(getEvents(), EventConstants.DOMAIN_CREATED), is(Boolean.TRUE));
  }

  @Test
  public void whenMakeRightCalled_withCreatedEventData_domainCreatedEventCreatedWithExpectedMessage() {
    makeRightOperation.withEventData(new EventData(EventHelper.EventItem.DOMAIN_CREATED)).execute();

    assertThat("Event message",
        containsEventWithMessage(getEvents(),
            EventConstants.DOMAIN_CREATED,
            String.format(EventConstants.DOMAIN_CREATED_PATTERN, UID)),
        is(Boolean.TRUE));
  }

  @Test
  public void whenMakeRightCalled_withChangedEventData_domainChangedEventCreated() {
    makeRightOperation.withEventData(new EventData(EventHelper.EventItem.DOMAIN_CHANGED)).execute();

    assertThat("Event DOMAIN_CHANGED",
        containsEvent(getEvents(), EventConstants.DOMAIN_CHANGED), is(Boolean.TRUE));
  }

  @Test
  public void whenMakeRightCalled_withChangedEventData_domainChangedEventCreatedWithExpectedMessage() {
    makeRightOperation.withEventData(new EventData(EventHelper.EventItem.DOMAIN_CHANGED)).execute();

    assertThat("Event message",
        containsEventWithMessage(getEvents(),
            EventConstants.DOMAIN_CHANGED,
            String.format(EventConstants.DOMAIN_CHANGED_PATTERN, UID)),
        is(Boolean.TRUE));
  }


  @Test
  public void whenMakeRightCalled_withDeletedEventData_domainDeletedEventCreated() {
    makeRightOperation.withEventData(new EventData(EventHelper.EventItem.DOMAIN_DELETED)).execute();

    assertThat("Event DOMAIN_DELETED",
        containsEvent(getEvents(), EventConstants.DOMAIN_DELETED), is(Boolean.TRUE));
  }

  @Test
  public void whenMakeRightCalled_withDeletedEventData_domainDeletedEventCreatedWithExpectedMessage() {
    makeRightOperation.withEventData(new EventData(EventHelper.EventItem.DOMAIN_DELETED)).execute();

    assertThat("Event message",
        containsEventWithMessage(getEvents(),
            EventConstants.DOMAIN_DELETED,
            String.format(EventConstants.DOMAIN_DELETED_PATTERN, UID)),
        is(Boolean.TRUE));
  }

  @Test
  public void whenMakeRightCalled_withAbortedEventData_domainProcessingFailedEventCreated() {
    makeRightOperation
        .withEventData(new EventData(EventHelper.EventItem.DOMAIN_PROCESSING_ABORTED, "Test this failure"))
        .execute();

    assertThat("Event DOMAIN_PROCESSING_FAILED",
        containsEvent(getEvents(), EventConstants.DOMAIN_PROCESSING_ABORTED), is(Boolean.TRUE));
  }

  @Test
  public void whenMakeRightCalled_withAbortedEventData_domainProcessingFailedEventCreatedWithExpectedMessage() {
    makeRightOperation
        .withEventData(new EventData(EventHelper.EventItem.DOMAIN_PROCESSING_ABORTED, "Test this failure"))
        .execute();

    assertThat("Event message",
        containsEventWithMessage(getEvents(),
            EventConstants.DOMAIN_PROCESSING_ABORTED,
            String.format(EventConstants.DOMAIN_PROCESSING_ABORTED_PATTERN, UID, "Test this failure")),
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
        .equals(EventConstants.WEBLOGIC_OPERATOR_COMPONENT);
  }

  private Object containsEventWithInstance(List<V1Event> events, String reason, String opName) {
    String instance = Optional.ofNullable(getEventMatchesReason(events, reason))
        .map(V1Event::getReportingInstance)
        .orElse("");
    System.out.println("XXXX instance = " + instance + " opName=" + opName);
    return instance.equals(opName);
  }

  private Object createOperatorPod(String name, String namespace) throws JsonProcessingException {
    return new V1Pod()
          .metadata(createMetadata(name, namespace));
  }

  private V1ObjectMeta createMetadata(
      String name,
      String namespace) {
    final V1ObjectMeta metadata =
        new V1ObjectMeta()
            .name(name)
            .namespace(namespace);
    metadata.putLabelsItem("app", "weblogic-operator");
    return metadata;
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