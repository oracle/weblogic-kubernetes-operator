// Copyright (c) 2020, 2021, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.helpers;

import java.util.Optional;
import javax.validation.constraints.NotNull;

import io.kubernetes.client.openapi.models.V1Event;
import io.kubernetes.client.openapi.models.V1ObjectMeta;
import io.kubernetes.client.openapi.models.V1ObjectReference;
import oracle.kubernetes.operator.EventConstants;
import oracle.kubernetes.operator.KubernetesConstants;
import oracle.kubernetes.operator.LabelConstants;
import oracle.kubernetes.operator.logging.LoggingFacade;
import oracle.kubernetes.operator.logging.LoggingFactory;
import oracle.kubernetes.operator.logging.MessageKeys;
import oracle.kubernetes.operator.steps.DefaultResponseStep;
import oracle.kubernetes.operator.work.NextAction;
import oracle.kubernetes.operator.work.Packet;
import oracle.kubernetes.operator.work.Step;
import org.joda.time.DateTime;

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

      LOGGER.fine(MessageKeys.CREATING_EVENT, eventData.eventItem);

      Optional.ofNullable(info).ifPresent(dpi -> dpi.setLastEventItem(eventData.eventItem));

      V1Event event = createEvent(packet, eventData);
      return doNext(new CallBuilder()
              .createEventAsync(
                  event.getMetadata().getNamespace(),
                  event,
                  new DefaultResponseStep<>(getNext())),
          packet);
    }

    private boolean isDuplicatedStartedEvent(DomainPresenceInfo info) {
      return eventData.eventItem == EventItem.DOMAIN_PROCESSING_STARTING
          && Optional.ofNullable(info).map(dpi -> dpi.getLastEventItem() == DOMAIN_PROCESSING_STARTING).orElse(false);
    }

    private boolean hasProcessingNotStarted(DomainPresenceInfo info) {
      return Optional.ofNullable(info).map(dpi -> dpi.getLastEventItem() != DOMAIN_PROCESSING_STARTING).orElse(false);
    }
  }

  private static V1Event createEvent(
      Packet packet,
      EventData eventData) {
    DomainPresenceInfo info = packet.getSpi(DomainPresenceInfo.class);
    eventData.namespace(Optional.ofNullable(info)
        .map(DomainPresenceInfo::getNamespace).orElse(eventData.namespace));
    eventData.resourceName(eventData.eventItem.calculateResourceName(info, eventData.namespace));
    return new V1Event()
        .metadata(createMetadata(eventData))
        .reportingComponent(WEBLOGIC_OPERATOR_COMPONENT)
        .reportingInstance(getOperatorPodName())
        .lastTimestamp(eventData.eventItem.getLastTimestamp())
        .type(eventData.eventItem.getType())
        .reason(eventData.eventItem.getReason())
        .message(eventData.eventItem.getMessage(eventData.getResourceName(), eventData))
        .involvedObject(eventData.eventItem.createInvolvedObject(eventData));
  }

  private static V1ObjectMeta createMetadata(
      EventData eventData) {
    final V1ObjectMeta metadata =
        new V1ObjectMeta()
            .name(String.format("%s.%s.%s",
                eventData.getResourceName(), eventData.eventItem.getReason(), System.currentTimeMillis()))
            .namespace(eventData.getNamespace());

    eventData.eventItem.addLabels(metadata, eventData);

    return metadata;
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
          .apiVersion(KubernetesConstants.API_VERSION_WEBLOGIC_ORACLE);
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
  }
}
