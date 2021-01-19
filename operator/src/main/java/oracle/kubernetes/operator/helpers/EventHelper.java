// Copyright (c) 2020, 2021, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.helpers;

import java.util.Optional;
import java.util.Random;
import javax.validation.constraints.NotNull;

import io.kubernetes.client.openapi.models.V1Event;
import io.kubernetes.client.openapi.models.V1ObjectMeta;
import io.kubernetes.client.openapi.models.V1ObjectReference;
import oracle.kubernetes.operator.EventConstants;
import oracle.kubernetes.operator.KubernetesConstants;
import oracle.kubernetes.operator.LabelConstants;
import oracle.kubernetes.operator.calls.CallResponse;
import oracle.kubernetes.operator.calls.UnrecoverableErrorBuilder;
import oracle.kubernetes.operator.logging.LoggingFacade;
import oracle.kubernetes.operator.logging.LoggingFactory;
import oracle.kubernetes.operator.logging.MessageKeys;
import oracle.kubernetes.operator.work.NextAction;
import oracle.kubernetes.operator.work.Packet;
import oracle.kubernetes.operator.work.Step;
import oracle.kubernetes.weblogic.domain.model.Domain;
import org.joda.time.DateTime;

import static oracle.kubernetes.operator.DomainProcessorImpl.getEventK8SObjects;
import static oracle.kubernetes.operator.EventConstants.DOMAIN_CHANGED_EVENT;
import static oracle.kubernetes.operator.EventConstants.DOMAIN_CHANGED_PATTERN;
import static oracle.kubernetes.operator.EventConstants.DOMAIN_CREATED_EVENT;
import static oracle.kubernetes.operator.EventConstants.DOMAIN_CREATED_PATTERN;
import static oracle.kubernetes.operator.EventConstants.DOMAIN_DELETED_EVENT;
import static oracle.kubernetes.operator.EventConstants.DOMAIN_DELETED_PATTERN;
import static oracle.kubernetes.operator.EventConstants.DOMAIN_PROCESSING_ABORTED_EVENT;
import static oracle.kubernetes.operator.EventConstants.DOMAIN_PROCESSING_ABORTED_PATTERN;
import static oracle.kubernetes.operator.EventConstants.DOMAIN_PROCESSING_COMPLETED_EVENT;
import static oracle.kubernetes.operator.EventConstants.DOMAIN_PROCESSING_COMPLETED_PATTERN;
import static oracle.kubernetes.operator.EventConstants.DOMAIN_PROCESSING_FAILED_EVENT;
import static oracle.kubernetes.operator.EventConstants.DOMAIN_PROCESSING_FAILED_PATTERN;
import static oracle.kubernetes.operator.EventConstants.DOMAIN_PROCESSING_RETRYING_EVENT;
import static oracle.kubernetes.operator.EventConstants.DOMAIN_PROCESSING_RETRYING_PATTERN;
import static oracle.kubernetes.operator.EventConstants.DOMAIN_PROCESSING_STARTING_EVENT;
import static oracle.kubernetes.operator.EventConstants.DOMAIN_PROCESSING_STARTING_PATTERN;
import static oracle.kubernetes.operator.EventConstants.DOMAIN_VALIDATION_ERROR_EVENT;
import static oracle.kubernetes.operator.EventConstants.DOMAIN_VALIDATION_ERROR_PATTERN;
import static oracle.kubernetes.operator.EventConstants.EVENT_NORMAL;
import static oracle.kubernetes.operator.EventConstants.EVENT_WARNING;
import static oracle.kubernetes.operator.EventConstants.NAMESPACE_WATCHING_STARTED_PATTERN;
import static oracle.kubernetes.operator.EventConstants.WEBLOGIC_OPERATOR_COMPONENT;
import static oracle.kubernetes.operator.helpers.EventHelper.EventItem.DOMAIN_PROCESSING_ABORTED;
import static oracle.kubernetes.operator.helpers.EventHelper.EventItem.DOMAIN_PROCESSING_COMPLETED;
import static oracle.kubernetes.operator.helpers.EventHelper.EventItem.DOMAIN_PROCESSING_STARTING;
import static oracle.kubernetes.operator.helpers.NamespaceHelper.getOperatorPodName;

/** A Helper Class for the operator to create Kubernetes Events at the key points in the operator's workflow. */
public class EventHelper {
  private static final LoggingFacade LOGGER = LoggingFactory.getLogger("Operator", "Operator");

  /**
   * Factory for {@link Step} that asynchronously create an event.
   *
   * @param eventData event item
   * @return Step for creating an event
   */
  public static Step createEventStep(
      EventData eventData) {
    return new CreateEventStep(eventData);
  }

  /**
   * Factory for {@link Step} that asynchronously create an event.
   *
   * @param eventData event item
   * @param next next step
   * @return Step for creating an event
   */
  public static Step createEventStep(
      EventData eventData, Step next) {
    return new CreateEventStep(eventData, next);
  }

  public static class CreateEventStep extends Step {
    private final EventData eventData;

    CreateEventStep(EventData eventData) {
      this(eventData, null);
    }

    CreateEventStep(EventData eventData, Step next) {
      super(next);
      this.eventData = eventData;
    }

    @Override
    public NextAction apply(Packet packet) {
      DomainPresenceInfo info = packet.getSpi(DomainPresenceInfo.class);
      if (hasProcessingNotStarted(info) && (eventData.eventItem == DOMAIN_PROCESSING_COMPLETED)) {
        return doNext(packet);
      }

      if (isDuplicatedStartedEvent(info)) {
        return doNext(packet);
      }

      return doNext(createEventAPICall(createEventModel(packet, eventData)), packet);
    }

    private Step createEventAPICall(V1Event event) {
      V1Event existingEvent = getExistingEvent(event);
      return existingEvent != null ? createReplaceEventCall(event, existingEvent) : createCreateEventCall(event);
    }

    private Step createCreateEventCall(V1Event event) {
      LOGGER.fine(MessageKeys.CREATING_EVENT, eventData.eventItem);
      event.firstTimestamp(event.getLastTimestamp());
      return new CallBuilder()
          .createEventAsync(
              event.getMetadata().getNamespace(),
              event,
              new CreateEventResponseStep(getNext()));
    }

    private Step createReplaceEventCall(V1Event event, @NotNull V1Event existingEvent) {
      LOGGER.fine(MessageKeys.REPLACING_EVENT, eventData.eventItem);
      existingEvent.count(Optional.ofNullable(existingEvent.getCount()).map(c -> c + 1).orElse(1));
      existingEvent.lastTimestamp(event.getLastTimestamp());
      return new CallBuilder()
          .replaceEventAsync(
              existingEvent.getMetadata().getName(),
              existingEvent.getMetadata().getNamespace(),
              existingEvent,
              new ReplaceEventResponseStep(getNext()));
    }

    private V1Event getExistingEvent(V1Event event) {
      return Optional.ofNullable(getEventK8SObjects(event))
          .map(o -> o.getExistingEvent(event)).orElse(null);
    }

    private boolean isDuplicatedStartedEvent(DomainPresenceInfo info) {
      return eventData.eventItem == EventItem.DOMAIN_PROCESSING_STARTING
          && Optional.ofNullable(info).map(dpi -> dpi.getLastEventItem() == DOMAIN_PROCESSING_STARTING).orElse(false);
    }

    private boolean hasProcessingNotStarted(DomainPresenceInfo info) {
      return Optional.ofNullable(info).map(dpi -> dpi.getLastEventItem() != DOMAIN_PROCESSING_STARTING).orElse(false);
    }

    private class CreateEventResponseStep extends ResponseStep<V1Event> {
      CreateEventResponseStep(Step next) {
        super(next);
      }

      @Override
      public NextAction onSuccess(Packet packet, CallResponse<V1Event> callResponse) {
        Optional.ofNullable(packet.getSpi(DomainPresenceInfo.class))
            .ifPresent(dpi -> dpi.setLastEventItem(eventData.eventItem));
        return doNext(packet);
      }
    }

    private class ReplaceEventResponseStep extends ResponseStep<V1Event> {

      ReplaceEventResponseStep(Step next) {
        super(next);
      }

      @Override
      public NextAction onSuccess(Packet packet, CallResponse<V1Event> callResponse) {
        Optional.ofNullable(packet.getSpi(DomainPresenceInfo.class))
            .ifPresent(dpi -> dpi.setLastEventItem(eventData.eventItem));
        return doNext(packet);
      }

      @Override
      public NextAction onFailure(Packet packet, CallResponse<V1Event> callResponse) {
        return UnrecoverableErrorBuilder.isAsyncCallNotFoundFailure(callResponse)
            ? doNext(Step.chain(createCreateEventCall(createEventModel(packet, eventData)), getNext()), packet)
            : super.onFailure(packet, callResponse);
      }
    }
  }

  private static V1Event createEventModel(
      Packet packet,
      EventData eventData) {
    DomainPresenceInfo info = packet.getSpi(DomainPresenceInfo.class);
    EventItem eventItem = eventData.eventItem;

    String namespace = getNamespaceFromInfo(eventData, info);
    eventData.namespace(namespace);
    eventData.resourceName(eventItem.calculateResourceName(info, namespace));
    eventData.domainPresenceInfo(info);

    return new V1Event()
        .metadata(createMetadata(eventData))
        .reportingComponent(WEBLOGIC_OPERATOR_COMPONENT)
        .reportingInstance(getOperatorPodName())
        .lastTimestamp(eventItem.getLastTimestamp())
        .type(eventItem.getType())
        .reason(eventItem.getReason())
        .message(eventItem.getMessage(eventData.getResourceName(), eventData))
        .involvedObject(eventItem.createInvolvedObject(eventData))
        .count(1);
  }

  private static String getNamespaceFromInfo(EventData eventData, DomainPresenceInfo info) {
    return Optional.ofNullable(info)
        .map(DomainPresenceInfo::getNamespace).orElse(eventData.namespace);
  }

  private static V1ObjectMeta createMetadata(
      EventData eventData) {
    final V1ObjectMeta metadata =
        new V1ObjectMeta().name(generateEventName(eventData)).namespace(eventData.getNamespace());

    eventData.eventItem.addLabels(metadata, eventData);

    return metadata;
  }

  private static String generateEventName(EventData eventData) {
    return String.format("%s.%s.%s",
        eventData.getResourceName(), eventData.eventItem.getReason(), generateRandomLong());
  }

  private static long generateRandomLong() {
    Random r = new Random();
    return r.nextLong();
  }

  public enum EventItem {
    DOMAIN_CREATED {
      @Override
      public String getReason() {
        return DOMAIN_CREATED_EVENT;
      }

      @Override
      public String getPattern() {
        return DOMAIN_CREATED_PATTERN;
      }
    },
    DOMAIN_CHANGED {
      @Override
      public String getReason() {
        return DOMAIN_CHANGED_EVENT;
      }

      @Override
      public String getPattern() {
        return DOMAIN_CHANGED_PATTERN;
      }

    },
    DOMAIN_DELETED {
      @Override
      public String getReason() {
        return DOMAIN_DELETED_EVENT;
      }

      @Override
      public String getPattern() {
        return DOMAIN_DELETED_PATTERN;
      }

    },
    DOMAIN_PROCESSING_STARTING {
      @Override
      public String getReason() {
        return DOMAIN_PROCESSING_STARTING_EVENT;
      }

      @Override
      public String getPattern() {
        return DOMAIN_PROCESSING_STARTING_PATTERN;
      }
    },
    DOMAIN_PROCESSING_COMPLETED {
      @Override
      public String getReason() {
        return DOMAIN_PROCESSING_COMPLETED_EVENT;
      }

      @Override
      public String getPattern() {
        return DOMAIN_PROCESSING_COMPLETED_PATTERN;
      }
    },
    DOMAIN_PROCESSING_FAILED {
      @Override
      public String getType() {
        return EVENT_WARNING;
      }

      @Override
      public String getReason() {
        return DOMAIN_PROCESSING_FAILED_EVENT;
      }

      @Override
      public String getPattern() {
        return DOMAIN_PROCESSING_FAILED_PATTERN;
      }

      @Override
      public String getMessage(String resourceName, EventData eventData) {
        return String.format(DOMAIN_PROCESSING_FAILED_PATTERN,
            resourceName, Optional.ofNullable(eventData.message).orElse(""));
      }

    },
    DOMAIN_PROCESSING_RETRYING {
      @Override
      public String getReason() {
        return DOMAIN_PROCESSING_RETRYING_EVENT;
      }

      @Override
      public String getPattern() {
        return DOMAIN_PROCESSING_RETRYING_PATTERN;
      }
    },
    DOMAIN_PROCESSING_ABORTED {
      @Override
      public String getType() {
        return EVENT_WARNING;
      }

      @Override
      public String getReason() {
        return DOMAIN_PROCESSING_ABORTED_EVENT;
      }

      @Override
      public String getPattern() {
        return DOMAIN_PROCESSING_ABORTED_PATTERN;
      }

      @Override
      public String getMessage(String resourceName, EventData eventData) {
        return String.format(DOMAIN_PROCESSING_ABORTED_PATTERN, resourceName,
            Optional.ofNullable(eventData.message).orElse(""));
      }

    },
    DOMAIN_VALIDATION_ERROR {
      @Override
      public String getType() {
        return EVENT_WARNING;
      }

      @Override
      public String getReason() {
        return DOMAIN_VALIDATION_ERROR_EVENT;
      }

      @Override
      public String getPattern() {
        return DOMAIN_VALIDATION_ERROR_PATTERN;
      }

      @Override
      public String getMessage(String resourceName, EventData eventData) {
        return String.format(DOMAIN_VALIDATION_ERROR_PATTERN,
            resourceName, Optional.ofNullable(eventData.message).orElse(""));
      }
    },
    NAMESPACE_WATCHING_STARTED {
      @Override
      public String getReason() {
        return EventConstants.NAMESPACE_WATCHING_STARTED_EVENT;
      }

      @Override
      protected String getPattern() {
        return EventConstants.NAMESPACE_WATCHING_STARTED_PATTERN;
      }

      @Override
      public String getMessage(String resourceName, EventData eventData) {
        return String.format(NAMESPACE_WATCHING_STARTED_PATTERN, resourceName);
      }

      @Override
      public void addLabels(V1ObjectMeta metadata, EventData eventData) {
        metadata
            .putLabelsItem(LabelConstants.CREATEDBYOPERATOR_LABEL, "true");
      }

      @Override
      public V1ObjectReference createInvolvedObject(EventData eventData) {
        return new V1ObjectReference()
            .name(eventData.getResourceName())
            .namespace(eventData.getNamespace())
            .kind(KubernetesConstants.NAMESPACE);
      }

      @Override
      public String calculateResourceName(DomainPresenceInfo info, String namespace) {
        return namespace;
      }
    },
    NAMESPACE_WATCHING_STOPPED {
      @Override
      public String getReason() {
        return EventConstants.NAMESPACE_WATCHING_STOPPED_EVENT;
      }

      @Override
      protected String getPattern() {
        return EventConstants.NAMESPACE_WATCHING_STOPPED_PATTERN;
      }

      @Override
      public String getMessage(String resourceName, EventData eventData) {
        return String.format(EventConstants.NAMESPACE_WATCHING_STOPPED_PATTERN, resourceName);
      }

      @Override
      public void addLabels(V1ObjectMeta metadata, EventData eventData) {
        metadata
            .putLabelsItem(LabelConstants.CREATEDBYOPERATOR_LABEL, "true");
      }

      @Override
      public V1ObjectReference createInvolvedObject(EventData eventData) {
        return new V1ObjectReference()
            .name(eventData.getResourceName())
            .namespace(eventData.getNamespace())
            .kind(KubernetesConstants.NAMESPACE);
      }

      @Override
      public String calculateResourceName(DomainPresenceInfo info, String namespace) {
        return namespace;
      }
    };

    public String getMessage(String resourceName, EventData eventData) {
      return String.format(getPattern(), resourceName);
    }

    DateTime getLastTimestamp() {
      return DateTime.now();
    }

    void addLabels(V1ObjectMeta metadata, EventData eventData) {
      metadata
          .putLabelsItem(LabelConstants.DOMAINUID_LABEL, eventData.getResourceName())
          .putLabelsItem(LabelConstants.CREATEDBYOPERATOR_LABEL, "true");
    }

    V1ObjectReference createInvolvedObject(EventData eventData) {
      return new V1ObjectReference()
          .name(eventData.getResourceName())
          .namespace(eventData.getNamespace())
          .kind(KubernetesConstants.DOMAIN)
          .apiVersion(KubernetesConstants.API_VERSION_WEBLOGIC_ORACLE)
          .uid(eventData.getUID());
    }

    String calculateResourceName(DomainPresenceInfo info, String namespace) {
      return Optional.ofNullable(info).map(DomainPresenceInfo::getDomainUid).orElse("");
    }

    String getType() {
      return EVENT_NORMAL;
    }

    abstract String getPattern();

    public abstract String getReason();
  }

  public static class EventData {
    private final EventItem eventItem;
    private String message;
    private String namespace;
    private String resourceName;
    private DomainPresenceInfo info;

    public EventData(EventItem eventItem) {
      this(eventItem, "");
    }

    public EventData(EventItem eventItem, String message) {
      this.eventItem = eventItem;
      this.message = message;
    }

    public EventData message(String message) {
      this.message = message;
      return this;
    }

    public EventData namespace(String namespace) {
      this.namespace = namespace;
      return this;
    }

    public EventData resourceName(String resourceName) {
      this.resourceName = resourceName;
      return this;
    }

    public EventData domainPresenceInfo(DomainPresenceInfo info) {
      this.info = info;
      return this;
    }

    public EventItem getItem() {
      return eventItem;
    }

    public String getNamespace() {
      return namespace;
    }

    String getResourceName() {
      return resourceName;
    }

    @Override
    public String toString() {
      return "EventData: " + eventItem;
    }

    public static boolean isProcessingAbortedEvent(@NotNull EventData eventData) {
      return eventData.eventItem == DOMAIN_PROCESSING_ABORTED;
    }

    /**
     * Get the UID from the domain metadata.
     * @return domain resource's UID
     */
    public String getUID() {
      return Optional.ofNullable(info)
          .map(DomainPresenceInfo::getDomain)
          .map(Domain::getMetadata)
          .map(V1ObjectMeta::getUid)
          .orElse("");
    }
  }
}
