// Copyright (c) 2020, Oracle Corporation and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.helpers;

import java.util.Optional;

import io.kubernetes.client.openapi.models.V1Event;
import io.kubernetes.client.openapi.models.V1ObjectMeta;
import oracle.kubernetes.operator.EventConstants;
import oracle.kubernetes.operator.LabelConstants;
import oracle.kubernetes.operator.logging.LoggingFacade;
import oracle.kubernetes.operator.logging.LoggingFactory;
import oracle.kubernetes.operator.logging.MessageKeys;
import oracle.kubernetes.operator.steps.DefaultResponseStep;
import oracle.kubernetes.operator.work.NextAction;
import oracle.kubernetes.operator.work.Packet;
import oracle.kubernetes.operator.work.Step;

import static oracle.kubernetes.operator.EventConstants.DOMAIN_PROCESSING_ABORTED_PATTERN;
import static oracle.kubernetes.operator.EventConstants.DOMAIN_PROCESSING_FAILED_PATTERN;
import static oracle.kubernetes.operator.EventConstants.DOMAIN_PROCESSING_RETRYING_PATTERN;
import static oracle.kubernetes.operator.EventConstants.DOMAIN_PROCESSING_SUCCEEDED_PATTERN;
import static oracle.kubernetes.operator.EventConstants.EVENT_NORMAL;
import static oracle.kubernetes.operator.EventConstants.EVENT_WARNING;
import static oracle.kubernetes.operator.EventConstants.WEBLOGIC_OPERATOR_COMPONENT;

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

  private static class CreateEventStep extends Step {
    private final EventData eventData;

    CreateEventStep(EventData eventData) {
      this.eventData = eventData;
    }

    @Override
    public NextAction apply(Packet packet) {
      V1Event event = createEvent(packet, eventData);

      LOGGER.fine(MessageKeys.CREATING_EVENT, event.getMessage());

      return doNext(new CallBuilder()
              .createEventAsync(
                  packet.getSpi(DomainPresenceInfo.class).getNamespace(),
                  event,
                  new DefaultResponseStep<>(getNext())),
          packet);

    }
  }

  private static V1Event createEvent(
      Packet packet,
      EventData eventData) {
    DomainPresenceInfo info = packet.getSpi(DomainPresenceInfo.class);
    return createCommonElements(info, eventData.eventItem.getReason())
        .type(eventData.eventItem.getType())
        .reason(eventData.eventItem.getReason())
        .message(eventData.eventItem.getMessage(info, eventData))
        .action(eventData.eventItem.getAction());
  }

  private static V1Event createCommonElements(DomainPresenceInfo info, String eventReason) {
    return new V1Event()
        .metadata(createMetadata(info, eventReason))
        .reportingComponent(WEBLOGIC_OPERATOR_COMPONENT)
        .reportingInstance(System.getProperty("MY_POD_NAME"));
  }

  private static V1ObjectMeta createMetadata(
      DomainPresenceInfo info,
      String reason) {
    final V1ObjectMeta metadata =
        new V1ObjectMeta()
            .name(info.getDomainUid() + reason + System.currentTimeMillis())
            .namespace(info.getNamespace());

    LOGGER.finest("EventHelper.createMetaData");

    metadata
        .putLabelsItem(LabelConstants.DOMAINUID_LABEL, info.getDomainUid())
        .putLabelsItem(LabelConstants.CREATEDBYOPERATOR_LABEL, "true");

    return metadata;
  }

  public enum EventItem {
    DOMAIN_CREATED {
      @Override
      public String getReason() {
        return EventConstants.DOMAIN_CREATED_EVENT;
      }

      @Override
      public String getMessage(DomainPresenceInfo info, EventData eventData) {
        return String.format(EventConstants.DOMAIN_CREATED_PATTERN, info.getDomainUid());
      }
    },
    DOMAIN_CHANGED {
      @Override
      public String getReason() {
        return EventConstants.DOMAIN_CHANGED_EVENT;
      }

      @Override
      public String getMessage(DomainPresenceInfo info, EventData eventData) {
        return String.format(EventConstants.DOMAIN_CHANGED_PATTERN, info.getDomainUid());
      }

    },
    DOMAIN_DELETED {
      @Override
      public String getReason() {
        return EventConstants.DOMAIN_DELETED_EVENT;
      }

      @Override
      public String getMessage(DomainPresenceInfo info, EventData eventData) {
        return String.format(EventConstants.DOMAIN_DELETED_PATTERN, info.getDomainUid());
      }

    },
    DOMAIN_PROCESSING_STARTED {
      @Override
      public String getReason() {
        return EventConstants.DOMAIN_PROCESSING_STARTED_EVENT;
      }

      @Override
      public String getMessage(DomainPresenceInfo info, EventData eventData) {
        return String.format(EventConstants.DOMAIN_PROCESSING_STARTED_PATTERN, info.getDomainUid());
      }
    },
    DOMAIN_PROCESSING_SUCCEEDED {
      @Override
      public String getReason() {
        return EventConstants.DOMAIN_PROCESSING_SUCCEEDED_EVENT;
      }

      @Override
      public String getMessage(DomainPresenceInfo info, EventData eventData) {
        return String.format(DOMAIN_PROCESSING_SUCCEEDED_PATTERN, info.getDomainUid());
      }
    },
    DOMAIN_PROCESSING_FAILED {
      @Override
      public String getType() {
        return EVENT_WARNING;
      }

      @Override
      public String getReason() {
        return EventConstants.DOMAIN_PROCESSING_FAILED_EVENT;
      }

      @Override
      public String getMessage(DomainPresenceInfo info, EventData eventData) {
        return String.format(DOMAIN_PROCESSING_FAILED_PATTERN,
            info.getDomainUid(), Optional.ofNullable(eventData.message).orElse(""));
      }

      @Override
      public String getAction() {
        return EventConstants.DOMAIN_PROCESSING_FAILED_ACTION;
      }
    },
    DOMAIN_PROCESSING_RETRYING {
      @Override
      public String getReason() {
        return EventConstants.DOMAIN_PROCESSING_RETRYING_EVENT;
      }

      @Override
      public String getMessage(DomainPresenceInfo info, EventData eventData) {
        return String.format(DOMAIN_PROCESSING_RETRYING_PATTERN, info.getDomainUid());
      }
    },
    DOMAIN_PROCESSING_ABORTED {
      @Override
      public String getType() {
        return EVENT_WARNING;
      }

      @Override
      public String getReason() {
        return EventConstants.DOMAIN_PROCESSING_ABORTED_EVENT;
      }

      @Override
      public String getMessage(DomainPresenceInfo info, EventData eventData) {
        return String.format(DOMAIN_PROCESSING_ABORTED_PATTERN, info.getDomainUid(),
            Optional.ofNullable(eventData.message).orElse(""));
      }

      @Override
      public String getAction() {
        return EventConstants.DOMAIN_PROCESSING_ABORTED_ACTION;
      }
    };

    public EventItem message(String message) {
      this.message = message;
      return this;
    }

    String message;

    public abstract String getReason();

    public abstract String getMessage(DomainPresenceInfo info, EventData eventData);

    String getAction() {
      return "";
    }

    String getType() {
      return EVENT_NORMAL;
    }
  }

  public static class EventData {
    public EventItem eventItem;
    public String message;

    public EventData(EventItem eventItem) {
      this(eventItem, "");
    }

    public EventData(EventItem eventItem, String message) {
      this.eventItem = eventItem;
      this.message = message;
    }

    @Override
    public String toString() {
      return "EventData: " + eventItem;
    }
  }
}