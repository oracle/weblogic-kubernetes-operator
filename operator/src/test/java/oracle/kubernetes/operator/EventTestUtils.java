// Copyright (c) 2021, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import javax.validation.constraints.NotNull;

import io.kubernetes.client.openapi.models.V1Event;
import io.kubernetes.client.openapi.models.V1ObjectMeta;
import io.kubernetes.client.openapi.models.V1ObjectReference;
import oracle.kubernetes.operator.helpers.EventHelper.EventItem;
import oracle.kubernetes.operator.helpers.KubernetesTestSupport;

import static oracle.kubernetes.operator.EventConstants.WEBLOGIC_OPERATOR_COMPONENT;

public class EventTestUtils {
  private static List<V1Event> getEventsWithReason(@NotNull List<V1Event> events, String reason) {
    return events.stream().filter(event -> reasonMatches(event, reason)).collect(Collectors.toList());
  }

  /**
   * Whether there is an event that matches the given reason and namespace.
   *
   * @param events list of events to check
   * @param reason reason to match
   * @param namespace namespace to match
   * @return true if there is a matching event
   */
  public static boolean containsEventWithNamespace(@NotNull List<V1Event> events, String reason, String namespace) {
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
      @NotNull List<V1Event> events, String reason, Map<String, String> labels) {
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
  public static boolean containsEventWithMessage(@NotNull List<V1Event> events, String reason, String message) {
    return getEventsWithReason(events, reason).stream().anyMatch(e -> messageMatches(e, message));
  }

  /**
   * Whether there is an event that matches the given reason and reporting component is WebLogic Operator.
   *
   * @param events list of events to check
   * @param reason reason to match
   * @return true if there is a matching event
   */
  public static boolean containsEventWithComponent(@NotNull List<V1Event> events, String reason) {
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
  public static boolean containsEventWithInstance(@NotNull List<V1Event> events, String reason, String opName) {
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
  public static boolean containsEventWithInvolvedObject(
      @NotNull List<V1Event> events, String reason, String name, String namespace) {
    return getEventsWithReason(events, reason).stream().anyMatch(e -> involvedObjectMatches(e, name, namespace));
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
  public static boolean containsEventWithMessageForNamespaces(
      List<V1Event> events, EventItem eventItem, List<String> namespaces) {
    for (String ns : namespaces) {
      if (!EventTestUtils.containsEventWithMessage(events, eventItem.getReason(),
          eventItem.getMessage(ns, null))) {
        return false;
      }
    }
    return true;
  }

  public static List<V1Event> getEvents(KubernetesTestSupport testSupport) {
    return testSupport.getResources(KubernetesTestSupport.EVENT);
  }

  public static boolean containsEvent(List<V1Event> events, String reason) {
    return getEventsWithReason(events, reason).size() != 0;
  }

  private static boolean reasonMatches(V1Event event, String eventReason) {
    return eventReason.equals(event.getReason());
  }

  private static boolean namespaceMatches(V1Event event, String namespace) {
    return namespace.equals(event.getMetadata().getNamespace());
  }

  private static boolean labelsMatches(V1Event e, Map<String, String> labels) {
    return labels.equals(e.getMetadata().getLabels());
  }

  private static boolean reportingInstanceMatches(V1Event event, String instance) {
    return instance.equals(event.getReportingInstance());
  }

  private static boolean reportingComponentMatches(V1Event event) {
    return WEBLOGIC_OPERATOR_COMPONENT.equals(event.getReportingComponent());
  }

  private static boolean messageMatches(V1Event event, String message) {
    return message.equals(event.getMessage());
  }

  private static boolean involvedObjectMatches(@NotNull V1Event event, String name, String namespace) {
    return getInvolvedObjectName(event).equals(name)
        && getInvolvedObjectNamespace(event).equals(namespace)
        && getNamespace(event).equals(getInvolvedObjectNamespace(event));
  }

  private static String getNamespace(@NotNull V1Event event) {
    return Optional.ofNullable(event.getMetadata()).map(V1ObjectMeta::getNamespace).orElse("");
  }

  private static String getInvolvedObjectNamespace(@NotNull V1Event event) {
    return Optional.ofNullable(event.getInvolvedObject()).map(V1ObjectReference::getNamespace).orElse("");
  }

  private static String getInvolvedObjectName(@NotNull V1Event event) {
    return Optional.ofNullable(event.getInvolvedObject()).map(V1ObjectReference::getName).orElse("");
  }

}
