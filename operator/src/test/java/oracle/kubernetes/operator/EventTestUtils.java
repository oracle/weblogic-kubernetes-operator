// Copyright (c) 2020, Oracle Corporation and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import javax.validation.constraints.NotNull;

import io.kubernetes.client.openapi.models.V1Event;
import io.kubernetes.client.openapi.models.V1ObjectMeta;
import io.kubernetes.client.openapi.models.V1ObjectReference;
import oracle.kubernetes.operator.helpers.KubernetesTestSupport;

import static oracle.kubernetes.operator.EventConstants.WEBLOGIC_OPERATOR_COMPONENT;

public class EventTestUtils extends ThreadFactoryTestBase {
  private List<V1Event> getEventsMatchesReason(@NotNull List<V1Event> events, String reason) {
    return events.stream().filter(event -> reasonMatches(event, reason)).collect(Collectors.toList());
  }

  protected boolean containsEventWithNamespace(@NotNull List<V1Event> events, String reason, String namespace) {
    return namespace.equals(getEventsMatchesReason(events, reason).stream()
        .filter(e -> namespaceMatches(e, namespace))
        .findFirst()
        .map(V1Event::getMetadata)
        .map(V1ObjectMeta::getNamespace)
        .orElse(""));
  }

  protected boolean containsEventWithLabels(@NotNull List<V1Event> events, String reason, Map<String, String> labels) {
    return !getEventsMatchesReason(events, reason)
        .stream()
        .filter(e -> labelsMatches(e, labels))
        .findFirst()
        .map(V1Event::getMetadata)
        .map(V1ObjectMeta::getLabels)
        .orElse(Collections.emptyMap()).isEmpty();
  }

  protected boolean containsEventWithMessage(@NotNull List<V1Event> events, String reason, String message) {
    return message.equals(getEventsMatchesReason(events, reason).stream()
        .filter(e -> messageMatches(e, message)).findFirst().map(V1Event::getMessage).orElse(""));
  }

  protected boolean containsEventWithComponent(@NotNull List<V1Event> events, String reason) {
    return WEBLOGIC_OPERATOR_COMPONENT.equals(getEventsMatchesReason(events, reason).stream()
        .filter(e -> reportingComponentMatches(e, WEBLOGIC_OPERATOR_COMPONENT))
        .findFirst().map(V1Event::getReportingComponent)
        .orElse(""));
  }

  protected boolean containsEventWithInstance(@NotNull List<V1Event> events, String reason, String opName) {
    return opName.equals(getEventsMatchesReason(events, reason).stream()
        .filter(e -> reportingInstanceMatches(e, opName)).findFirst().map(V1Event::getReportingInstance)
        .orElse(""));
  }

  protected boolean containsEventWithInvolvedObject(
      @NotNull List<V1Event> events, String reason, String name, String namespace) {
    return referenceMatches(getEventsMatchesReason(events, reason).stream()
        .filter(e -> involvedObjectMatches(e, name, namespace)).findFirst().map(V1Event::getInvolvedObject)
        .orElse(null), name, namespace);
  }

  private boolean referenceMatches(V1ObjectReference reference, String name, String namespace) {
    return reference != null && name.equals(reference.getName()) && namespace.equals(reference.getNamespace());
  }

  protected List<V1Event> getEvents(KubernetesTestSupport testSupport) {
    return testSupport.getResources(KubernetesTestSupport.EVENT);
  }

  protected boolean containsEvent(List<V1Event> events, String reason) {
    return getEventsMatchesReason(events, reason).size() != 0;
  }

  static boolean reasonMatches(V1Event event, String eventReason) {
    return eventReason.equals(event.getReason());
  }

  static boolean namespaceMatches(V1Event event, String namespace) {
    return namespace.equals(event.getMetadata().getNamespace());
  }

  private boolean labelsMatches(V1Event e, Map<String, String> labels) {
    return labels.equals(e.getMetadata().getLabels());
  }

  static boolean reportingInstanceMatches(V1Event event, String instance) {
    return instance.equals(event.getReportingInstance());
  }

  static boolean reportingComponentMatches(V1Event event, String component) {
    return component.equals(event.getReportingComponent());
  }

  static boolean messageMatches(V1Event event, String message) {
    return message.equals(event.getMessage());
  }

  static boolean involvedObjectMatches(@NotNull V1Event event, String name, String namespace) {
    return getInvolvedObjectName(event).equals(name)
        && getInvolvedObjectNamespace(event).equals(namespace)
        && getNamespace(event).equals(getInvolvedObjectNamespace(event));
  }

  static String getNamespace(@NotNull V1Event event) {
    return Optional.ofNullable(event.getMetadata()).map(V1ObjectMeta::getNamespace).orElse("");
  }

  static String getInvolvedObjectNamespace(@NotNull V1Event event) {
    return Optional.ofNullable(event.getInvolvedObject()).map(V1ObjectReference::getNamespace).orElse("");
  }

  static String getInvolvedObjectName(@NotNull V1Event event) {
    return Optional.ofNullable(event.getInvolvedObject()).map(V1ObjectReference::getName).orElse("");
  }
}
