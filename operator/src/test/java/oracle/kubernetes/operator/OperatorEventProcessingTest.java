// Copyright (c) 2021, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import com.meterware.simplestub.Memento;
import com.meterware.simplestub.StaticStubSupport;
import io.kubernetes.client.openapi.models.CoreV1Event;
import io.kubernetes.client.openapi.models.V1ObjectMeta;
import io.kubernetes.client.openapi.models.V1ObjectReference;
import oracle.kubernetes.operator.builders.WatchEvent;
import oracle.kubernetes.operator.helpers.EventHelper;
import oracle.kubernetes.operator.helpers.EventHelper.EventItem;
import oracle.kubernetes.operator.helpers.HelmAccessStub;
import oracle.kubernetes.operator.helpers.KubernetesEventObjects;
import oracle.kubernetes.operator.helpers.KubernetesTestSupport;
import oracle.kubernetes.utils.TestUtils;
import org.jetbrains.annotations.NotNull;
import org.joda.time.DateTime;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static com.meterware.simplestub.Stub.createStrictStub;
import static oracle.kubernetes.operator.DomainProcessorImpl.getEventK8SObjects;
import static oracle.kubernetes.operator.EventConstants.WEBLOGIC_OPERATOR_COMPONENT;
import static oracle.kubernetes.operator.KubernetesConstants.OPERATOR_NAMESPACE_ENV;
import static oracle.kubernetes.operator.KubernetesConstants.OPERATOR_POD_NAME_ENV;
import static oracle.kubernetes.operator.KubernetesConstants.OPERATOR_POD_UID_ENV;
import static oracle.kubernetes.operator.helpers.EventHelper.EventItem.DOMAIN_CREATED;
import static oracle.kubernetes.operator.helpers.EventHelper.EventItem.DOMAIN_PROCESSING_FAILED;
import static oracle.kubernetes.operator.helpers.EventHelper.EventItem.NAMESPACE_WATCHING_STOPPED;
import static oracle.kubernetes.operator.helpers.EventHelper.EventItem.START_MANAGING_NAMESPACE;
import static oracle.kubernetes.operator.helpers.EventHelper.EventItem.STOP_MANAGING_NAMESPACE;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.junit.MatcherAssert.assertThat;

public class OperatorEventProcessingTest {
  private static final String OPERATOR_POD_NAME = "my-weblogic-operator-1234";
  private static final String OP_NS = "operator-namespace";
  private static final String OPERATOR_UID = "1234-5678-101112";
  private static final String NS = "namespace";
  private static final String UID = "uid";
  private final V1ObjectReference domainReference =
      new V1ObjectReference().name(UID).kind("Domain").namespace(NS);

  private final V1ObjectReference nsReference =
      new V1ObjectReference().name(NS).kind("Namespace").namespace(NS);

  private final V1ObjectReference opReference =
      new V1ObjectReference().name(OPERATOR_POD_NAME).kind("Pod").namespace(OP_NS).uid(OPERATOR_UID);

  private final List<Memento> mementos = new ArrayList<>();
  private final Map<String, Map<String, KubernetesEventObjects>> domainEventObjects = new ConcurrentHashMap<>();
  private final Map<String, KubernetesEventObjects> nsEventObjects = new ConcurrentHashMap<>();
  private final DomainProcessorImpl processor =
      new DomainProcessorImpl(createStrictStub(DomainProcessorDelegate.class));
  private final KubernetesTestSupport testSupport = new KubernetesTestSupport();

  /**
   * Setup test.
   * @throws Exception on failure
   */
  @BeforeEach
  public void setUp() throws Exception {
    mementos.add(TestUtils.silenceOperatorLogger());
    mementos.add(testSupport.install());
    mementos.add(StaticStubSupport.install(DomainProcessorImpl.class, "domainEventK8SObjects", domainEventObjects));
    mementos.add(StaticStubSupport.install(DomainProcessorImpl.class, "namespaceEventK8SObjects", nsEventObjects));
    mementos.add(HelmAccessStub.install());
    HelmAccessStub.defineVariable(OPERATOR_NAMESPACE_ENV, OP_NS);
    HelmAccessStub.defineVariable(OPERATOR_POD_NAME_ENV, OPERATOR_POD_NAME);
    HelmAccessStub.defineVariable(OPERATOR_POD_UID_ENV, OPERATOR_UID);
  }

  /**
   * Tear down test.
   */
  @AfterEach
  public void tearDown() {
    for (Memento memento : mementos) {
      memento.revert();
    }
  }

  @Test
  public void onNewDomainCreatedEventWithNoInvolvedObject_doNothing() {
    CoreV1Event event = createDomainEvent(".acbd1", DOMAIN_CREATED, "", null);
    dispatchAddedEventWatch(event);
    assertThat("Found NO DOMAIN_CREATED event in the map", getMatchingEvent(event), nullValue());
  }

  @Test
  public void onNewDomainCreatedEvent_updateKubernetesEventObjectsMap() {
    CoreV1Event event = createDomainEvent(".acbd2", DOMAIN_CREATED, "", domainReference);
    dispatchAddedEventWatch(event);
    assertThat("Found DOMAIN_CREATED event in the map", getMatchingEvent(event), notNullValue());
  }

  @Test
  public void afterOnAddDomainCreatedEvent_onDeleteDomainCreatedEventWithNoInvolvedObject_doNothing() {
    CoreV1Event event1 = createDomainEvent(".acbd3", DOMAIN_CREATED, "", domainReference);
    dispatchAddedEventWatch(event1);
    CoreV1Event event2 = createDomainEvent(".acbd3", DOMAIN_CREATED, "", null);
    dispatchDeletedEventWatch(event2);
    assertThat("Found DOMAIN_CREATED event in the map", getMatchingEvent(event1), notNullValue());
  }

  @Test
  public void afterOnAddDomainCreatedEvent_onDeleteDomainCreatedEvent_updateKubernetesEventObjectsMap() {
    CoreV1Event event = createDomainEvent(".acbd4", DOMAIN_CREATED, "", domainReference);
    dispatchAddedEventWatch(event);
    dispatchDeletedEventWatch(event);
    assertThat("Found NO DOMAIN_CREATED event in the map", getMatchingEvent(event), nullValue());
  }

  @Test
  public void afterOnAddDomainCreatedEvent_onModifyDomainCreatedEventWithNoInvolvedObject_doNothing() {
    CoreV1Event event1 = createDomainEvent(".acbd5", DOMAIN_CREATED, "", domainReference);
    dispatchAddedEventWatch(event1);
    CoreV1Event event2 = createDomainEvent(".acbd5", DOMAIN_CREATED, "", null);
    dispatchModifiedEventWatch(event2);
    assertThat(getMatchingEventCount(event1), equalTo(1));
  }

  @Test
  public void afterOnAddDomainCreatedEvent_onModifyDomainDeletedEvent_updateKubernetesEventObjectsMap() {
    CoreV1Event event = createDomainEvent(".acbd6", DOMAIN_CREATED, "", domainReference);
    dispatchAddedEventWatch(event);
    event = createDomainEvent(".acbd6", DOMAIN_CREATED, "", domainReference, 2);
    dispatchModifiedEventWatch(event);
    assertThat(getMatchingEventCount(event), equalTo(2));
  }

  @Test
  public void onNewDomainProcessingFailedEventWithNoInvolvedObject_doNothing() {
    CoreV1Event event = createDomainEvent(".acbd7", DOMAIN_PROCESSING_FAILED, "failure", null);
    dispatchAddedEventWatch(event);
    assertThat(getMatchingEvent(event), nullValue());
  }

  @Test
  public void onNewProcessingFailedEvent_updateKubernetesEventObjectsMap() {
    CoreV1Event event = createDomainEvent(".acbd8", DOMAIN_PROCESSING_FAILED, "failure2", domainReference);
    dispatchAddedEventWatch(event);
    assertThat(getMatchingEvent(event), notNullValue());
  }

  @Test
  public void afterAddProcessingFailedEvent_onDeleteDomainProcessingFailedEventWithNoInvolvedObject_doNothing() {
    CoreV1Event event1 = createDomainEvent(".acbd9", DOMAIN_PROCESSING_FAILED, "failureOnDelete1", domainReference);
    dispatchAddedEventWatch(event1);
    CoreV1Event event2 = createDomainEvent(".acbd9", DOMAIN_PROCESSING_FAILED, "failureOnDelete1", null);
    dispatchDeletedEventWatch(event2);
    assertThat(getMatchingEvent(event1), notNullValue());
  }

  @Test
  public void afterAddProcessingFailedEvent_onDeleteDomainProcessingFailedEvent_updateKubernetesEventObjectsMap() {
    CoreV1Event event = createDomainEvent(".acbd10", DOMAIN_PROCESSING_FAILED, "failureOnDelete2", domainReference);
    dispatchAddedEventWatch(event);
    dispatchDeletedEventWatch(event);
    assertThat(getMatchingEvent(event), nullValue());
  }

  @Test
  public void afterAddProcessingFailedEvent_onModifyDomainProcessingFailedEventWithNoInvolvedObject_doNothing() {
    CoreV1Event event1 = createDomainEvent(".acbd11", DOMAIN_PROCESSING_FAILED, "failureOnModify1", domainReference);
    dispatchAddedEventWatch(event1);
    CoreV1Event event2 = createDomainEvent(".acbd11", DOMAIN_PROCESSING_FAILED, "failureOnModify1", null);
    dispatchModifiedEventWatch(event2);
    assertThat(getMatchingEventCount(event1), equalTo(1));
  }

  @Test
  public void afterAddProcessingFailedEvent_onModifyDomainProcessingFailedEvent_updateKubernetesEventObjectsMap() {
    CoreV1Event event1 = createDomainEvent(".acbd12", DOMAIN_PROCESSING_FAILED, "failureOnModify2", domainReference);
    dispatchAddedEventWatch(event1);
    CoreV1Event event2 = createDomainEvent(".acbd12", DOMAIN_PROCESSING_FAILED, "failureOnModify2", domainReference, 2);
    dispatchModifiedEventWatch(event2);
    assertThat(getMatchingEventCount(event1), equalTo(2));
  }

  @Test
  public void afterAddDProcessingFailedEvent_onNewNamespaceWatchingStoppedEventWithNoInvolvedObject_doNothing() {
    CoreV1Event event = createDomainEvent(".1234a", NAMESPACE_WATCHING_STOPPED, "", null);
    dispatchAddedEventWatch(event);
    assertThat(getMatchingEvent(event), nullValue());
  }

  @Test
  public void onNewNamespaceWatchingStoppedEvent_updateKubernetesEventObjectsMap() {
    CoreV1Event event = createNamespaceEvent(".1234b", NAMESPACE_WATCHING_STOPPED, nsReference);
    dispatchAddedEventWatch(event);
    assertThat(getMatchingEvent(event), notNullValue());
  }

  @Test
  public void afterAddNSWatchingStoppedEvent_onDeleteNamespaceWatchingStoppedEventWithNoInvolvedObject_doNothing() {
    CoreV1Event event1 = createNamespaceEvent(".1234c", NAMESPACE_WATCHING_STOPPED, nsReference);
    dispatchAddedEventWatch(event1);
    CoreV1Event event2 = createNamespaceEvent(".1234c", NAMESPACE_WATCHING_STOPPED,null);
    dispatchDeletedEventWatch(event2);
    assertThat(getMatchingEvent(event1), notNullValue());
  }

  @Test
  public void afterAddNSWatchingStoppedEvent_onDeleteNamespaceWatchingStoppedEvent_updateKubernetesEventObjectsMap() {
    CoreV1Event event1 = createNamespaceEvent(".1234d", NAMESPACE_WATCHING_STOPPED, nsReference);
    dispatchAddedEventWatch(event1);
    CoreV1Event event2 = createNamespaceEvent(".1234d", NAMESPACE_WATCHING_STOPPED, nsReference);
    dispatchDeletedEventWatch(event2);
    assertThat(getMatchingEvent(event1), nullValue());
  }

  @Test
  public void afterAddNSWatchingStoppedEvent_onModifyNamespaceWatchingStoppedEventWithNoInvolvedObject_doNothing() {
    CoreV1Event event1 = createNamespaceEvent(".1234e", NAMESPACE_WATCHING_STOPPED, nsReference);
    dispatchAddedEventWatch(event1);
    CoreV1Event event2 = createNamespaceEvent(".1234e", NAMESPACE_WATCHING_STOPPED,null);
    dispatchModifiedEventWatch(event2);
    assertThat(getMatchingEventCount(event1), equalTo(1));
  }

  @Test
  public void afterAddNSWatchingStoppedEvent_onModifyNamespaceWatchingStoppedEvent_updateKubernetesEventObjectsMap() {
    CoreV1Event event1 = createNamespaceEvent(".1234f", NAMESPACE_WATCHING_STOPPED, nsReference);
    dispatchAddedEventWatch(event1);
    CoreV1Event event2 = createNamespaceEvent(".1234f", NAMESPACE_WATCHING_STOPPED, nsReference, 2);
    dispatchModifiedEventWatch(event2);
    assertThat(getMatchingEventCount(event1), equalTo(2));
  }

  @Test
  public void onCreateStartManagingNSEvent_updateKubernetesEventObjectsMap() {
    CoreV1Event event = createNamespaceEvent(".aaaa1", START_MANAGING_NAMESPACE, opReference);
    dispatchAddedEventWatch(event);
    assertThat(getMatchingEventCount(event), equalTo(1));
  }

  @Test
  public void afterAddStopManagingNSEvent_onModifyStopManagingNamespace_updateKubernetesEventObjectsMap() {
    CoreV1Event event1 = createNamespaceEvent(".aaaa2", STOP_MANAGING_NAMESPACE, opReference);
    dispatchAddedEventWatch(event1);
    CoreV1Event event2 = createNamespaceEvent(".aaaa2", STOP_MANAGING_NAMESPACE, opReference, 2);
    dispatchModifiedEventWatch(event2);
    assertThat(getMatchingEventCount(event1), equalTo(2));
  }

  @Test
  public void afterAddStartManagingNSEvent_onDeleteTheEventWithNoInvolvedObject_doNothing() {
    CoreV1Event event1 = createNamespaceEvent(".aaaa3", START_MANAGING_NAMESPACE, opReference);
    dispatchAddedEventWatch(event1);
    CoreV1Event event2 = createNamespaceEvent(".aaaa3", START_MANAGING_NAMESPACE, null);
    dispatchDeletedEventWatch(event2);
    assertThat(getMatchingEvent(event1), notNullValue());
  }

  @Test
  public void afterAddStopManaingNSEvent_onDeleteEvent_updateKubernetesEventObjectsMap() {
    CoreV1Event event1 = createNamespaceEvent(".1234d", STOP_MANAGING_NAMESPACE, opReference);
    dispatchAddedEventWatch(event1);
    CoreV1Event event2 = createNamespaceEvent(".1234d", STOP_MANAGING_NAMESPACE, opReference);
    dispatchDeletedEventWatch(event2);
    assertThat(getMatchingEvent(event1), nullValue());
  }

  private int getMatchingEventCount(CoreV1Event event) {
    return Optional.ofNullable(getMatchingEvent(event)).map(CoreV1Event::getCount).orElse(0);
  }

  private CoreV1Event getMatchingEvent(CoreV1Event event) {
    CoreV1Event found = Optional.ofNullable(getEventK8SObjects(event)).map(k -> k.getExistingEvent(event)).orElse(null);
    return getEventName(found).equals(getEventName(event)) ? found : null;
  }

  @NotNull
  private String getEventName(CoreV1Event event) {
    return Optional.ofNullable(event).map(CoreV1Event::getMetadata).map(V1ObjectMeta::getName).orElse("");
  }

  private void dispatchAddedEventWatch(CoreV1Event event) {
    processor.dispatchEventWatch(WatchEvent.createAddedEvent(event).toWatchResponse());
  }

  private void dispatchModifiedEventWatch(CoreV1Event event) {
    processor.dispatchEventWatch(WatchEvent.createModifiedEvent(event).toWatchResponse());
  }

  private void dispatchDeletedEventWatch(CoreV1Event event) {
    processor.dispatchEventWatch(WatchEvent.createDeletedEvent(event).toWatchResponse());
  }

  private static V1ObjectMeta createMetadata(String name, String namespace, boolean domainEvent) {
    final V1ObjectMeta metadata =
        new V1ObjectMeta().name(name).namespace(namespace);
    if (domainEvent) {
      metadata.putLabelsItem(LabelConstants.DOMAINUID_LABEL, UID);
    }

    metadata.putLabelsItem(LabelConstants.CREATEDBYOPERATOR_LABEL, "true");
    return metadata;
  }

  private CoreV1Event createDomainEvent(
      String nameAppendix, EventItem item, String message, V1ObjectReference involvedObj) {

    return new CoreV1Event()
        .metadata(createMetadata(createDomainEventName(nameAppendix, item), NS, true))
        .reportingComponent(WEBLOGIC_OPERATOR_COMPONENT)
        .reportingInstance(OPERATOR_POD_NAME)
        .lastTimestamp(DateTime.now())
        .type(EventConstants.EVENT_NORMAL)
        .reason(item.getReason())
        .message(item.getMessage(new EventHelper.EventData(item, message)))
        .involvedObject(involvedObj)
        .count(1);
  }

  private CoreV1Event createDomainEvent(
      String name, EventItem item, String message, V1ObjectReference involvedObj, int count) {
    return createDomainEvent(name, item, message, involvedObj).count(count);
  }

  private CoreV1Event createNamespaceEvent(String nameAppendix, EventItem item, V1ObjectReference involvedObj) {

    return new CoreV1Event()
        .metadata(createMetadata(createNSEventName(nameAppendix, item), OP_NS, false))
        .reportingComponent(WEBLOGIC_OPERATOR_COMPONENT)
        .reportingInstance(OPERATOR_POD_NAME)
        .lastTimestamp(DateTime.now())
        .type(EventConstants.EVENT_NORMAL)
        .reason(item.getReason())
        .message(item.getMessage(new EventHelper.EventData(item, "")))
        .involvedObject(involvedObj)
        .count(1);
  }

  private CoreV1Event createNamespaceEvent(String name, EventItem item, V1ObjectReference involvedObj, int count) {
    return createNamespaceEvent(name, item, involvedObj).count(count);
  }

  private String createDomainEventName(String nameAppendix, EventItem item) {
    return UID + item.getReason() + nameAppendix;
  }

  private String createNSEventName(String nameAppendix, EventItem item) {
    return NS + item.getReason() + nameAppendix;
  }
}
