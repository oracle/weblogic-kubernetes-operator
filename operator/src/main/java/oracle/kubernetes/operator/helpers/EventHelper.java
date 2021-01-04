// Copyright (c) 2020, 2021, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.helpers;

import java.util.Optional;
import javax.validation.constraints.NotNull;

import io.kubernetes.client.openapi.models.V1Event;
import io.kubernetes.client.openapi.models.V1ObjectMeta;
import io.kubernetes.client.openapi.models.V1ObjectReference;
import oracle.kubernetes.operator.KubernetesConstants;
import oracle.kubernetes.operator.LabelConstants;
import oracle.kubernetes.operator.ProcessingConstants;
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
      if (hasProcessingNotStarted(packet) && (eventData.eventItem == DOMAIN_PROCESSING_COMPLETED)) {
        return doNext(packet);
      }

      if (isDuplicatedStartedEvent(packet)) {
        return doNext(packet);
      }

      LOGGER.fine(MessageKeys.CREATING_EVENT, eventData.eventItem);

      packet.put(ProcessingConstants.EVENT_TYPE, eventData.eventItem);

      V1Event event = createEvent(packet, eventData);
      return doNext(new CallBuilder()
              .createEventAsync(
                  event.getMetadata().getNamespace(),
                  event,
                  new DefaultResponseStep<>(getNext())),
          packet);
    }

    private boolean isDuplicatedStartedEvent(Packet packet) {
      return eventData.eventItem == EventItem.DOMAIN_PROCESSING_STARTING
          && packet.get(ProcessingConstants.EVENT_TYPE) == EventItem.DOMAIN_PROCESSING_STARTING;
    }

    private boolean hasProcessingNotStarted(Packet packet) {
      return packet.get(ProcessingConstants.EVENT_TYPE) != DOMAIN_PROCESSING_STARTING;
    }

  }

  private static V1Event createEvent(
      Packet packet,
      EventData eventData) {
    DomainPresenceInfo info = packet.getSpi(DomainPresenceInfo.class);
    return new V1Event()
        .metadata(createMetadata(info, eventData.eventItem.getReason()))
        .reportingComponent(WEBLOGIC_OPERATOR_COMPONENT)
        .reportingInstance(getOperatorPodName())
        .lastTimestamp(eventData.eventItem.getLastTimestamp())
        .type(eventData.eventItem.getType())
        .reason(eventData.eventItem.getReason())
        .message(eventData.eventItem.getMessage(info, eventData))
        .involvedObject(createInvolvedObject(info));
  }

  private static V1ObjectReference createInvolvedObject(DomainPresenceInfo info) {
    return new V1ObjectReference()
        .name(info.getDomainUid())
        .namespace(info.getNamespace())
        .kind(KubernetesConstants.DOMAIN)
        .apiVersion(KubernetesConstants.API_VERSION_WEBLOGIC_ORACLE);
  }

  private static V1ObjectMeta createMetadata(
      DomainPresenceInfo info,
      String reason) {
    final V1ObjectMeta metadata =
        new V1ObjectMeta()
            .name(String.format("%s.%s.%s", info.getDomainUid(), reason, System.currentTimeMillis()))
            .namespace(info.getNamespace());

    metadata
        .putLabelsItem(LabelConstants.DOMAINUID_LABEL, info.getDomainUid())
        .putLabelsItem(LabelConstants.CREATEDBYOPERATOR_LABEL, "true");

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
      public String getMessage(DomainPresenceInfo info, EventData eventData) {
        return String.format(DOMAIN_PROCESSING_FAILED_PATTERN,
            info.getDomainUid(), Optional.ofNullable(eventData.message).orElse(""));
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
      public String getMessage(DomainPresenceInfo info, EventData eventData) {
        return String.format(DOMAIN_PROCESSING_ABORTED_PATTERN, info.getDomainUid(),
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
      public String getMessage(DomainPresenceInfo info, EventData eventData) {
        return String.format(DOMAIN_VALIDATION_ERROR_PATTERN,
            info.getDomainUid(), Optional.ofNullable(eventData.message).orElse(""));
      }
    },
    EMPTY {
      @Override
      protected String getPattern() {
        return null;
      }

      @Override
      public String getReason() {
        return "";
      }
    };

    public String getMessage(DomainPresenceInfo info, EventData eventData) {
      return String.format(getPattern(), info.getDomainUid());
    }

    public DateTime getLastTimestamp() {
      return DateTime.now();
    }

    protected abstract String getPattern();

    public abstract String getReason();

    String getType() {
      return EVENT_NORMAL;
    }

  }

  public static class EventData {
    private EventItem eventItem;
    private String message;

    public EventData(EventItem eventItem) {
      this(eventItem, "");
    }

    public EventData(EventItem eventItem, String message) {
      this.eventItem = eventItem;
      this.message = message;
    }

    public EventData eventItem(EventItem item) {
      this.eventItem = item;
      return this;
    }

    public EventData message(String message) {
      this.message = message;
      return this;
    }

    public EventItem getItem() {
      return eventItem;
    }

    public String getMessage() {
      return message;
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
