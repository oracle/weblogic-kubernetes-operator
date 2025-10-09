// Copyright (c) 2021, 2025, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

import io.kubernetes.client.openapi.models.EventsV1Event;
import io.kubernetes.client.openapi.models.EventsV1EventSeries;
import io.kubernetes.client.openapi.models.V1ObjectMeta;
import io.kubernetes.client.openapi.models.V1ObjectReference;
import jakarta.validation.constraints.NotNull;
import oracle.kubernetes.operator.helpers.EventHelper;
import oracle.kubernetes.operator.helpers.EventHelper.EventItem;
import oracle.kubernetes.operator.helpers.KubernetesTestSupport;
import oracle.kubernetes.operator.logging.LoggingFacade;
import oracle.kubernetes.operator.logging.LoggingFactory;

import static oracle.kubernetes.common.logging.MessageKeys.ABORTED_EVENT_ERROR;
import static oracle.kubernetes.operator.EventConstants.DOMAIN_FAILED_EVENT;
import static oracle.kubernetes.operator.EventConstants.WEBLOGIC_OPERATOR_COMPONENT;

public class EventTestUtils {

  private static final LoggingFacade LOGGER = LoggingFactory.getLogger("Operator", "Operator");

  public static List<EventsV1Event> getEventsWithReason(@NotNull List<EventsV1Event> events, String reason) {
    return events.stream().filter(event -> reasonMatches(event, reason))
        .collect(Collectors.toCollection(ArrayList::new));
  }

  /**
   * Whether there is an event that matches the given reason and namespace.
   *
   * @param events list of events to check
   * @param reason reason to match
   * @param namespace namespace to match
   * @return true if there is a matching event
   */
  public static boolean containsEventWithNamespace(
      @NotNull List<EventsV1Event> events, String reason, String namespace) {
    return getEventsWithReason(events, reason).stream().anyMatch(e -> namespaceMatches(e, namespace));
  }

  /**
   * Whether there is an event that matches the given set of labels.
   *
   * @param events list of events to check
   * @param reason reason to match
   * @param labels set of labels to match
   * @return true if there is a matching event
   */
  public static boolean containsEventWithLabels(
      @NotNull List<EventsV1Event> events, String reason, Map<String, String> labels) {
    return getEventsWithReason(events, reason).stream().anyMatch(e -> labelsMatches(e, labels));
  }

  /**
   * Whether there is an event that matches the given reason and message.
   *
   * @param events list of events to check
   * @param reason reason to match
   * @param message message to match
   * @return true if there is a matching event
   */
  public static boolean containsEventWithMessage(@NotNull List<EventsV1Event> events, String reason, String message) {
    return getEventsWithReason(events, reason).stream().anyMatch(e -> messageMatches(e, message));
  }

  /**
   * Whether there is an event that matches the given reason and reporting component is WebLogic Operator.
   *
   * @param events list of events to check
   * @param reason reason to match
   * @return true if there is a matching event
   */
  public static boolean containsEventWithComponent(@NotNull List<EventsV1Event> events, String reason) {
    return getEventsWithReason(events, reason).stream().anyMatch(EventTestUtils::reportingComponentMatches);
  }

  /**
   * Whether there is an event that matches the given reason and operator pod name.
   *
   * @param events list of events to check
   * @param reason reason to match
   * @param opName pod name of the operator to match
   * @return true if there is a matching event
   */
  public static boolean containsEventWithInstance(@NotNull List<EventsV1Event> events, String reason, String opName) {
    return getEventsWithReason(events, reason).stream().anyMatch(e -> reportingInstanceMatches(e, opName));
  }

  /**
   * Whether there is an event that matches the given reason and involved object.
   *
   * @param events list of events to check
   * @param reason reason to match
   * @param name name of the involved object to match
   * @param namespace namespace to match
   * @return true if there is a matching event
   */
  public static boolean containsEventWithRegarding(
      @NotNull List<EventsV1Event> events,
      String reason,
      String name,
      String namespace) {
    return getEventsWithReason(events, reason)
        .stream().anyMatch(e -> regardingMatches(e, name, namespace));
  }

  /**
   * Whether there is an event that matches the given reason and involved object.
   *
   * @param events list of events to check
   * @param reason reason to match
   * @param name name of the involved object to match
   * @param namespace namespace to match
   * @param k8sUID Kubernetes UID to match
   * @return true if there is a matching event
   */
  public static boolean containsEventWithRegarding(
      @NotNull List<EventsV1Event> events,
      String reason,
      String name,
      String namespace,
      String k8sUID) {
    return getEventsWithReason(events, reason)
        .stream().anyMatch(e -> regardingMatches(e, name, namespace, k8sUID));
  }

  /**
   * Whether there is an event that matches the reason and message of the given event item for each of the namespaces
   * in the list.
   *
   * @param events list of events to check
   * @param eventItem event item to match
   * @param namespaces list of namespaces
   * @return true if there is a matching event for each namespace
   */
  static boolean containsEventWithMessageForNamespaces(
      List<EventsV1Event> events, EventItem eventItem, List<String> namespaces) {
    for (String ns : namespaces) {
      if (!EventTestUtils.containsEventWithMessage(events, eventItem.getReason(),
          eventItem.getNote(new EventHelper.EventData(eventItem).resourceName(ns)))) {
        return false;
      }
    }
    return true;
  }

  /**
   * Whether there is an event that matches the given reason and count.
   *
   * @param events list of events to check
   * @param reason reason to match
   * @param count count to match
   * @return true if there is a matching event
   */
  public static boolean containsOneEventWithCount(List<EventsV1Event> events, String reason, int count) {
    List<EventsV1Event> eventsMatchReason = getEventsWithReason(events, reason);
    return eventsMatchReason.size() == 1 && eventsMatchReason.stream().anyMatch(e -> countMatches(e, count));
  }

  /**
   * Whether there are expected number of events that match the given reason and have count of 1.
   *
   * @param events list of events to check
   * @param reason reason to match
   * @param eventsCount number of events that was expected to match
   * @return true if the expected condition met
   */
  public static boolean containsEventsWithCountOne(List<EventsV1Event> events, String reason, int eventsCount) {
    List<EventsV1Event> eventsMatchReason = getEventsWithReason(events, reason);
    return eventsMatchReason.stream().allMatch(e -> countMatches(e, 1)) && eventsMatchReason.size() == eventsCount;
  }

  /**
   * Get the message of an expected event.
   *
   * @param testSupport the instance of KubernetesTestSupport
   * @param event  expected event
   * @return message 
   */
  public static String getExpectedEventMessage(KubernetesTestSupport testSupport, EventHelper.EventItem event) {
    List<EventsV1Event> events = getEventsWithReason(getEvents(testSupport), event.getReason());
    return Optional.ofNullable(events)
        .filter(list -> !list.isEmpty())
        .map(n -> n.get(0))
        .map(EventsV1Event::getNote)
        .orElse("Event not found");
  }

  public static List<EventsV1Event> getEvents(KubernetesTestSupport testSupport) {
    return testSupport.getResources(KubernetesTestSupport.EVENT);
  }

  public static boolean containsEvent(List<EventsV1Event> events, String reason) {
    return !getEventsWithReason(events, reason).isEmpty();
  }

  private static boolean reasonMatches(EventsV1Event event, String eventReason) {
    return eventReason.equals(event.getReason());
  }

  private static boolean namespaceMatches(EventsV1Event event, String namespace) {
    return namespace.equals(getNamespace(event));
  }

  private static boolean labelsMatches(EventsV1Event e, Map<String, String> labels) {
    return labels.equals(e.getMetadata().getLabels());
  }

  private static boolean reportingInstanceMatches(EventsV1Event event, String instance) {
    return instance.equals(event.getReportingInstance());
  }

  private static boolean reportingComponentMatches(EventsV1Event event) {
    return WEBLOGIC_OPERATOR_COMPONENT.equals(event.getReportingController());
  }

  private static boolean messageMatches(EventsV1Event event, String message) {
    return message.equals(event.getNote());
  }

  private static boolean regardingMatches(
      @NotNull EventsV1Event event, String name, String namespace, String k8sUID) {
    return regardingNameMatches(event, name)
        && regardingApiVersionMatches(event)
        && regardingNamespaceMatches(event, namespace)
        && regardingUIDMatches(event, k8sUID);
  }

  private static boolean regardingMatches(
      @NotNull EventsV1Event event, String name, String namespace) {
    return regardingNameMatches(event, name)
        && regardingNamespaceMatches(event, namespace);
  }

  private static boolean regardingUIDMatches(@NotNull EventsV1Event event, String k8sUID) {
    return getInvolvedObjectK8SUID(event).equals(k8sUID);
  }

  private static boolean regardingApiVersionMatches(@NotNull EventsV1Event event) {
    return getInvolvedObjectApiVersion(event).equals(KubernetesConstants.API_VERSION_WEBLOGIC_ORACLE);
  }

  private static boolean regardingNameMatches(@NotNull EventsV1Event event, String name) {
    return getInvolvedObjectName(event).equals(name);
  }

  private static boolean regardingNamespaceMatches(@NotNull EventsV1Event event, String namespace) {
    return getInvolvedObjectNamespace(event).equals(namespace)
        && getNamespace(event).equals(getInvolvedObjectNamespace(event));
  }

  private static boolean countMatches(@NotNull EventsV1Event event, int count) {
    return getCount(event) == count;
  }

  private static int getCount(@NotNull EventsV1Event event) {
    return Optional.of(event).map(EventsV1Event::getSeries).map(EventsV1EventSeries::getCount).orElse(1);
  }

  private static String getInvolvedObjectK8SUID(EventsV1Event event) {
    return Optional.ofNullable(event.getRegarding()).map(V1ObjectReference::getUid).orElse("");
  }

  private static String getInvolvedObjectApiVersion(EventsV1Event event) {
    return Optional.ofNullable(event.getRegarding()).map(V1ObjectReference::getApiVersion).orElse("");
  }

  public static String getName(EventsV1Event event) {
    return Optional.ofNullable(event).map(EventsV1Event::getMetadata).map(V1ObjectMeta::getName).orElse("");
  }

  private static String getNamespace(@NotNull EventsV1Event event) {
    return Optional.ofNullable(event.getMetadata()).map(V1ObjectMeta::getNamespace).orElse("");
  }

  private static String getInvolvedObjectNamespace(@NotNull EventsV1Event event) {
    return Optional.ofNullable(event.getRegarding()).map(V1ObjectReference::getNamespace).orElse("");
  }

  private static String getInvolvedObjectName(@NotNull EventsV1Event event) {
    return Optional.ofNullable(event.getRegarding()).map(V1ObjectReference::getName).orElse("");
  }

  public static EventsV1Event getEventWithReason(List<EventsV1Event> events, String reason) {
    return !getEventsWithReason(events, reason).isEmpty() ? getEventsWithReason(events, reason).get(0) : null;
  }

  public static int getNumberOfEvents(List<EventsV1Event> events, String reason) {
    return getEventsWithReason(events, reason).size();
  }

  public static boolean isDomainFailedAbortedEvent(EventsV1Event e) {
    return DOMAIN_FAILED_EVENT.equals(e.getReason())
        && Objects.requireNonNull(e.getNote()).contains(getLocalizedString(ABORTED_EVENT_ERROR));
  }

  public static String getLocalizedString(String msgId) {
    return LOGGER.formatMessage(msgId);
  }

  public static String getFormattedMessage(String msgId, Object... params) {
    return LOGGER.formatMessage(msgId, params);
  }
}
